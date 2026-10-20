package dev.terravista.render.gpu;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import net.minecraft.client.Minecraft;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 访问原版当前帧图像并记录借用图像的布局转换。 */
public final class MinecraftFrameAccess {
    private MinecraftFrameAccess() {}

    public static long lightmap() {
        return view(Minecraft.getInstance().gameRenderer.levelLightmap());
    }

    public static long view(GpuTextureView texture) {
        return ((VulkanGpuTextureView) texture).vkImageView();
    }

    public static int format(GpuTextureView texture) {
        return VulkanConst.toVk(texture.texture().getFormat());
    }

    public static void transition(VkCommandBuffer command, GpuTextureView texture,
                                  boolean depth, int previous, int next) {
        try (var stack = stackPush()) {
            long image = ((VulkanGpuTexture) texture.texture()).vkImage();
            boolean sampling = next == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            int attachmentStage = depth
                    ? VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT
                    : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            int shaderStages = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                    | (depth ? VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT : 0);
            int attachmentWrite = depth
                    ? VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            int attachmentRead = depth
                    ? VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT : VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
            int aspects = VulkanConst.formatAspectMask(texture.texture().getFormat());
            var barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default()
                    .srcAccessMask(sampling ? attachmentWrite : VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(sampling ? VK_ACCESS_SHADER_READ_BIT : attachmentRead | attachmentWrite)
                    .oldLayout(previous).newLayout(next)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.subresourceRange().aspectMask(aspects)
                    .levelCount(VK_REMAINING_MIP_LEVELS).layerCount(VK_REMAINING_ARRAY_LAYERS);
            vkCmdPipelineBarrier(command, sampling ? attachmentStage : shaderStages,
                    sampling ? shaderStages : attachmentStage, 0, null, null, barrier);
        }
    }
}
