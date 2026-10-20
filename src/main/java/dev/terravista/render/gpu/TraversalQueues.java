package dev.terravista.render.gpu;

import dev.terravista.render.tree.TreePipeline;
import dev.terravista.world.VoxelWorld;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;
import org.lwjgl.vulkan.VkBufferCopy;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 持有遍历前沿、请求和场景缓冲以及紧凑 GPU 根列表。 */
final class TraversalQueues {
    static final int REQUEST_CAPACITY = 50;
    static final int ITERATIONS = VoxelWorld.MAX_LEVEL + 1;
    private final FrameCommands commands;
    final GpuBuffer requests, nodes, uniform, metadata, first, second;
    GpuBuffer roots;
    private final int maxRoots;
    private int rootCount;
    private final Int2IntOpenHashMap rootIndices = new Int2IntOpenHashMap();
    private int[] rootIds;

    TraversalQueues(FrameCommands commands, int maxNodes) {
        this.commands = commands;
        maxRoots = maxNodes;
        rootIds = new int[Math.min(VulkanViewTargets.LOOKUP_CAPACITY, maxRoots)];
        GpuBuffer[] created = new GpuBuffer[7];
        try {
            requests = created[0] = new GpuBuffer(commands, REQUEST_CAPACITY * 8L + 8);
            requests.zero();
            nodes = created[1] = new GpuBuffer(commands, maxNodes * 16L);
            nodes.fill(-1);
            uniform = created[2] = new GpuBuffer(commands, 224);
            uniform.zero();
            roots = created[3] = new GpuBuffer(commands, rootIds.length * 4L);
            roots.zero();
            metadata = created[4] = new GpuBuffer(commands, 16L * ITERATIONS);
            metadata.zero();
            // 以节点池容量限制中间遍历前沿。
            first = created[5] = new GpuBuffer(commands, maxNodes * 4L);
            first.zero();
            second = created[6] = new GpuBuffer(commands, maxNodes * 4L);
            second.zero();
        } catch (RuntimeException | Error failure) {
            for (var buffer : created) if (buffer != null) buffer.free();
            throw failure;
        }
    }

    // 在常规遍历管线创建后执行初始化。
    void publishRoots(TreePipeline tree) {
        rootIndices.defaultReturnValue(-1);
        tree.rootCallbacks(this::addRoot, this::removeRoot);
    }

    int rootCount() { return rootCount; }

    private void addRoot(int id) {
        ensureRootCapacity(rootCount + 1);
        int index = rootCount++;
        vkCmdFillBuffer(commands.command(), roots.buffer, index * 4L, 4, id);
        if (rootIndices.put(id, index) != -1) throw new IllegalStateException();
        rootIds[index] = id;
    }

    /** 按实际根节点数扩容，并延迟释放仍被帧引用的旧缓冲。 */
    private void ensureRootCapacity(int required) {
        if (required <= rootIds.length) return;
        if (required > maxRoots) throw new IllegalStateException("TerraVista Render: Root node pool exhausted");
        int capacity = Math.min(maxRoots, Math.max(required, rootIds.length * 2));
        int[] identifiers = Arrays.copyOf(rootIds, capacity);
        var replacement = new GpuBuffer(commands, capacity * 4L);
        try {
            commands.barrier(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_ACCESS_MEMORY_WRITE_BIT | VK_ACCESS_MEMORY_READ_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
            if (rootCount > 0) {
                try (var stack = stackPush()) {
                    var copy = VkBufferCopy.calloc(1, stack).size(rootCount * 4L);
                    vkCmdCopyBuffer(commands.command(), roots.buffer, replacement.buffer, copy);
                }
            }
            commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
        } catch (RuntimeException | Error failure) {
            replacement.free();
            throw failure;
        }
        roots.free();
        roots = replacement;
        rootIds = identifiers;
    }

    private void removeRoot(int id) {
        int index = rootIndices.remove(id);
        rootCount--;
        if (index == -1) throw new IllegalStateException();
        if (index == rootCount) return;
        int moved = rootIds[rootCount];
        rootIds[index] = moved;
        if (rootIndices.put(moved, index) == -1) throw new IllegalStateException();
        vkCmdFillBuffer(commands.command(), roots.buffer, index * 4L, 4, moved);
    }

    /** 按分配顺序清理构造失败的部分资源。 */
    void abort() {
        requests.free();
        nodes.free();
        uniform.free();
        roots.free();
        metadata.free();
        first.free();
        second.free();
    }

    void close() {
        requests.free();
        nodes.free();
        uniform.free();
        metadata.free();
        roots.free();
        first.free();
        second.free();
    }
}
