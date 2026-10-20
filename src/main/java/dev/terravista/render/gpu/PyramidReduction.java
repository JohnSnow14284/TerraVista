package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import java.util.List;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 管理深度归约管线并记录各层计算。 */
final class PyramidReduction {
    private final FrameCommands commands;
    private final DevicePipeline single;
    private DevicePipeline tiled;
    private final DepthConvention convention;
    private final boolean tiledAllowed;
    final long sampler;

    PyramidReduction(FrameCommands commands, DepthConvention convention) {
        this.commands = commands;
        this.convention = convention;
        try (var stack = stackPush()) {
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(commands.device().physicalDevice, properties);
            tiledAllowed = properties.limits().maxPerStageDescriptorStorageImages() >= 6
                    && properties.limits().maxDescriptorSetStorageImages() >= 6;
        }
        single = ComputePipeline.create(commands, ShaderProgram.DEPTH_REDUCE.path,
                ShaderVariants.load(ShaderProgram.DEPTH_REDUCE, ShaderVariants.defines().depth(convention).build()),
                16, List.of(PipelineBindings.sampler(0), PipelineBindings.image(1)));
        try { sampler = DeviceSamplers.get(commands.device(), true, false); }
        catch (RuntimeException | Error failure) { single.free(); throw failure; }
    }
    private void level(VkCommandBuffer command, long source, int layout, long target, int w, int h, int sw, int sh) {
        try (var writes = single.descriptors()) {
            writes.sampler(0, source, sampler, layout).image(1, target).push(command);
        }
        try (var stack = stackPush()) {
            single.pushConstants(command, stack.malloc(16).putInt(0, w).putInt(4, h).putInt(8, sw).putInt(12, sh));
        }
        vkCmdDispatch(command, (w + 7) / 8, (h + 7) / 8, 1);
    }
    void record(VkCommandBuffer command, DeviceImage image, int levels, long depthView, int sourceWidth, int sourceHeight) {
        single.bind(command);
        int w = image.width, h = image.height;
        int sw = sourceWidth, sh = sourceHeight;
        level(command, depthView, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, image.mipViews[0], w, h, sw, sh);
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
        sw = w; sh = h;
        w = Math.max(w / 2, 1); h = Math.max(h / 2, 1);
        int tiledEnd = 0;
        if (tiledAllowed && levels >= 3) {
            if (tiled == null) {
                tiled = ComputePipeline.create(commands, ShaderProgram.DEPTH_TILED.path,
                        ShaderVariants.load(ShaderProgram.DEPTH_TILED, ShaderVariants.defines().depth(convention).build()),
                        4, List.of(PipelineBindings.sampler(0), PipelineBindings.image(1), PipelineBindings.image(2),
                                PipelineBindings.image(3), PipelineBindings.image(4), PipelineBindings.image(5), PipelineBindings.image(6)));
            }
            tiled.bind(command);
            while (levels - tiledEnd - 1 >= 2) {
                int count = Math.min(6, levels - tiledEnd - 1);
                try (var writes = tiled.descriptors()) {
                    writes.sampler(0, image.mipViews[tiledEnd], sampler, VK_IMAGE_LAYOUT_GENERAL);
                    for (int next = 1; next <= 6; next++) writes.image(next, image.mipViews[tiledEnd + Math.min(next, count)]);
                    writes.push(command);
                }
                try (var stack = stackPush()) { tiled.pushConstants(command, stack.malloc(4).putInt(0, count)); }
                int baseWidth = Math.max(image.width >> tiledEnd, 1);
                int baseHeight = Math.max(image.height >> tiledEnd, 1);
                vkCmdDispatch(command, (baseWidth + 63) / 64, (baseHeight + 63) / 64, 1);
                commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
                tiledEnd += count;
            }
            w = Math.max(image.width >> (tiledEnd + 1), 1);
            h = Math.max(image.height >> (tiledEnd + 1), 1);
            sw = Math.max(image.width >> tiledEnd, 1);
            sh = Math.max(image.height >> tiledEnd, 1);
        }
        if (tiledEnd != 0 && tiledEnd + 1 < levels) single.bind(command);
        for (int mip = tiledEnd + 1; mip < levels; mip++) {
            level(command, image.mipViews[mip - 1], VK_IMAGE_LAYOUT_GENERAL, image.mipViews[mip], w, h, sw, sh);
            commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
            sw = w; sh = h;
            w = Math.max(w / 2, 1); h = Math.max(h / 2, 1);
        }
    }
    void close() {
        // 共享采样器由设备管理。
        single.free();
        if (tiled != null) tiled.free();
    }
}
