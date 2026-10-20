package dev.terravista.render.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import dev.terravista.render.AtlasReadback;
import org.lwjgl.vulkan.VkBufferImageCopy;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memIntBuffer;
import static org.lwjgl.vulkan.VK10.*;

/** 等待宿主上传完成后同步读回零级图集像素。 */
public final class VulkanAtlasPixels extends AtlasReadback {
    private final FrameCommands commands;

    public VulkanAtlasPixels(FrameCommands commands) { this.commands = commands; }

    @Override public int[] read(GpuTexture atlas, int width, int height) {
        long image = ((VulkanGpuTexture) atlas).vkImage();
        long bytes = (long) width * height * 4;
        var staging = new GpuBuffer(commands, bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT, true);
        try {
            RenderSystem.getDevice().createCommandEncoder().submit();
            var command = commands.command();
            commands.barrier(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_ACCESS_MEMORY_WRITE_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_READ_BIT);
            try (var stack = stackPush()) {
                var region = VkBufferImageCopy.calloc(1, stack)
                        .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
                region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0).baseArrayLayer(0).layerCount(1);
                region.imageOffset().set(0, 0, 0);
                region.imageExtent().set(width, height, 1);
                vkCmdCopyImageToBuffer(command, image, VK_IMAGE_LAYOUT_GENERAL, staging.buffer, region);
            }
            commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_HOST_BIT, VK_ACCESS_HOST_READ_BIT);
            commands.flushImmediate();
            var pixels = new int[width * height];
            long mapped = staging.map();
            memIntBuffer(mapped, pixels.length).get(pixels);
            return pixels;
        } finally {
            // 由帧回收销毁资源，释放内存时同时解除映射。
            staging.free();
        }
    }
}
