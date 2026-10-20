package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.mesh.MeshBuildQueue;
import dev.terravista.render.DepthConvention;
import dev.terravista.render.tree.TreePipeline;
import static dev.terravista.render.gpu.TraversalProgram.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

/** 记录各级 LOD 遍历并将节点请求交给树工作线程。 */
public final class LodTraversal {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final VulkanReadbacks readbacks;
    private final TreePipeline tree;
    private final GpuVisibilityCleaner cleaner;
    private final MeshBuildQueue meshes;
    private final TraversalQueues queues;
    private final TraversalProgram program;

    public LodTraversal(FrameCommands commands, VulkanUploads uploads, VulkanReadbacks readbacks,
                        DepthConvention convention, TreePipeline tree, GpuVisibilityCleaner cleaner, MeshBuildQueue meshes) {
        this.commands = commands;
        this.uploads = uploads;
        this.readbacks = readbacks;
        this.tree = tree;
        this.cleaner = cleaner;
        this.meshes = meshes;
        TraversalQueues created = null;
        try {
            queues = created = new TraversalQueues(commands, tree.maxNodes);
            program = new TraversalProgram(commands, convention);
        } catch (RuntimeException | Error failure) {
            if (created != null) created.abort();
            throw failure;
        }
        queues.publishRoots(tree);
    }

    public void record(VulkanViewTargets targets) {
        if (queues.rootCount() == 0) {
            commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
            vkCmdFillBuffer(commands.command(), targets.lookup.buffer, 0, 4, 0);
            commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
            return;
        }
        TraversalScene.upload(uploads, queues, targets, cleaner, meshes);
        var command = commands.command();
        vkCmdFillBuffer(command, targets.lookup.buffer, 0, 4, 0);
        int firstDispatch = (queues.rootCount() + (1 << WORKGROUP_BITS) - 1) >> WORKGROUP_BITS;
        long pointer = uploads.upload(queues.metadata, 0, 16L * TraversalQueues.ITERATIONS);
        memPutInt(pointer, firstDispatch);
        memPutInt(pointer + 4, 1);
        memPutInt(pointer + 8, 1);
        memPutInt(pointer + 12, queues.rootCount());
        for (int i = 1; i < TraversalQueues.ITERATIONS; i++) {
            memPutInt(pointer + i * 16L, 0);
            memPutInt(pointer + i * 16L + 4, 1);
            memPutInt(pointer + i * 16L + 8, 1);
            memPutInt(pointer + i * 16L + 12, 0);
        }
        // 同时提交此前的绘制列表计数器填充。
        uploads.commit();
        var selected = program.current();
        selected.bind(command);
        for (int level = 0; level < TraversalQueues.ITERATIONS; level++) {
            var source = level == 0 ? queues.roots : ((level & 1) == 0 ? queues.first : queues.second);
            var sink = (level & 1) == 0 ? queues.second : queues.first;
            try (var writes = selected.descriptors()) {
                if (level == 0) {
                    writes.sampler(HIZ, targets.depthPyramid.view(), targets.depthPyramid.sampler(), VK_IMAGE_LAYOUT_GENERAL)
                            .uniform(SCENE, queues.uniform).storage(REQUESTS, queues.requests).storage(RENDERS, targets.lookup)
                            .storage(NODES, queues.nodes).storage(METADATA, queues.metadata).storage(VISIBILITY, cleaner.visibility);
                }
                writes.storage(SOURCE, source).storage(SINK, sink).push(command);
            }
            try (var stack = stackPush()) { selected.pushConstants(command, stack.malloc(4).putInt(0, level)); }
            if (level == 0) {
                if (firstDispatch != 0) vkCmdDispatch(command, firstDispatch, 1, 1);
            } else vkCmdDispatchIndirect(command, queues.metadata.buffer, level * 16L);
            commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
        }
        readbacks.download(queues.requests, this::acceptRequests);
        // 等待下载拷贝读完源计数器后再复位。
        commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
        vkCmdFillBuffer(commands.command(), queues.requests.buffer, 0, 4, 0);
        commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_SHADER_READ_BIT);
    }

    private void acceptRequests(long pointer, long bytes) {
        int count = memGetInt(pointer);
        pointer += 8;
        if (count < 0 || count > 50000) {
            ModLog.error(new IllegalStateException("TerraVista Render: Traversal count exceeds the expected range: " + count + "; rendering may be incomplete"));
            return;
        }
        if (count > (queues.requests.size() >> 3) - 1) count = (int) ((queues.requests.size() >> 3) - 1);
        if (count != 0) {
            var batch = new NativeBuffer(count * 8L + 8).copyFrom(pointer - 8);
            memPutInt(batch.address, count);
            tree.request(batch);
        }
    }

    public GpuBuffer nodes() { return queues.nodes; }

    public void close() {
        program.close();
        queues.close();
    }
}
