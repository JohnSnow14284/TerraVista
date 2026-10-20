package dev.terravista.render.gpu;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.vulkan.VK10.*;

/** 生成地形绘制命令并维护异步计数预算。 */
final class TerrainCommands {
    private final FrameCommands commands;
    private final VulkanReadbacks readbacks;
    private final GpuGeometryTable geometry;
    private final TerrainScene scene;
    private final TerrainBuffers buffers;
    private final TerrainPipelines pipelines;
    private IndirectCommandClear clear;
    private int opaque, translucent, temporal;
    private boolean received, enabled;
    private long generation;

    TerrainCommands(FrameCommands commands, VulkanReadbacks readbacks, GpuGeometryTable geometry,
                    TerrainScene scene, TerrainBuffers buffers, TerrainPipelines pipelines) {
        this.commands = commands;
        this.readbacks = readbacks;
        this.geometry = geometry;
        this.scene = scene;
        this.buffers = buffers;
        this.pipelines = pipelines;
    }

    void record(VulkanViewTargets view) {
        boolean needsCounts = !commands.device().indirectCount;
        if (needsCounts != enabled) {
            enabled = needsCounts;
            generation++;
            received = false;
        }
        if (geometry.size() == 0) return;
        var command = commands.command();
        commands.barrier(VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
        vkCmdFillBuffer(command, buffers.distances.buffer, 0, 1024L * 4, 0);
        if (!commands.device().indirectCount) {
            if (clear == null) clear = new IndirectCommandClear(commands);
            clear.record(view.drawCalls, view.drawCounts);
        }

        pipelines.prepare.bind(command);
        try (var bindings = pipelines.prepare.descriptors()) {
            scene.bind(bindings).storage(1, view.drawCounts).storage(2, view.lookup).push(command);
        }
        vkCmdDispatch(command, 1, 1, 1);
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_INDIRECT_COMMAND_READ_BIT | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);

        pipelines.generate.bind(command);
        try (var bindings = pipelines.generate.descriptors()) {
            scene.bind(bindings).storage(1, view.drawCalls).storage(2, view.drawCounts)
                    .storage(3, geometry.metadataBuffer()).storage(4, view.visibility)
                    .storage(5, view.lookup).storage(6, view.positionScratch).storage(7, buffers.distances)
                    .sampler(8, view.depthPyramid.view(), view.depthPyramid.sampler(), VK_IMAGE_LAYOUT_GENERAL)
                    .push(command);
        }
        try (var stack = stackPush()) {
            pipelines.generate.pushConstants(command, stack.malloc(4).putInt(0, 1));
        }
        vkCmdDispatchIndirect(command, view.drawCounts.buffer, 0);
        commands.computeToCompute();

        pipelines.prefix.bind(command);
        try (var bindings = pipelines.prefix.descriptors()) {
            bindings.storage(0, buffers.distances).storage(1, view.drawCounts).push(command);
        }
        vkCmdDispatch(command, 1, 1, 1);
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
        pipelines.translucent.bind(command);
        try (var bindings = pipelines.translucent.descriptors()) {
            scene.bind(bindings).storage(1, view.drawCalls).storage(2, view.drawCounts)
                    .storage(3, geometry.metadataBuffer()).storage(4, view.lookup)
                    .storage(5, buffers.distances).push(command);
        }
        vkCmdDispatchIndirect(command, view.drawCounts.buffer, TerrainPipelines.TRANSLUCENT_DISPATCH_OFFSET);

        if (enabled) {
            long recordedGeneration = generation;
            readbacks.download(view.drawCounts, 12, 12, (pointer, bytes) -> {
                if (recordedGeneration != generation || !enabled) return;
                opaque = clamp(memGetInt(pointer), VulkanViewTargets.OPAQUE_CAPACITY);
                translucent = clamp(memGetInt(pointer + 4), VulkanViewTargets.TRANSLUCENT_CAPACITY);
                temporal = clamp(memGetInt(pointer + 8), VulkanViewTargets.TEMPORAL_CAPACITY);
                received = true;
            });
        }
    }

    private static int clamp(int value, int capacity) {
        return value < 0 ? 0 : Math.min(value, capacity);
    }

    private int budget(int count, int headroom, int capacity) {
        if (commands.device().indirectCount) return capacity;
        if (!enabled || !received) return capacity;
        int requested = (int) (count * 1.5f) + headroom;
        return Math.min(capacity, Math.max(0, requested));
    }

    int opaqueBudget(int capacity) { return budget(opaque, 1024, capacity); }
    int temporalBudget(int capacity) { return budget(temporal, 256, capacity); }
    int translucentBudget(int capacity) { return budget(translucent, 256, capacity); }

    void close() {
        if (clear != null) clear.close();
    }
}
