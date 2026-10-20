package dev.terravista.render.gpu;

import java.util.List;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 记录整幅图像屏障后更新布局状态。 */
public final class ImageTransitions {
    public record Change(DeviceImage image, int layout, int sourceAccess, int destinationAccess) {}

    public static void transition(DeviceImage image, int layout, int sourceStage, int sourceAccess,
                                  int destinationStage, int destinationAccess) {
        if (image.layout == VK_IMAGE_LAYOUT_UNDEFINED) {
            sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            sourceAccess = 0;
        }
        try (var stack = stackPush()) {
            var barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default()
                    .srcAccessMask(sourceAccess).dstAccessMask(destinationAccess)
                    .oldLayout(image.layout).newLayout(layout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image.image);
            barrier.subresourceRange().aspectMask(image.aspect).levelCount(image.mipLevels).layerCount(1);
            vkCmdPipelineBarrier(image.commands.command(), sourceStage, destinationStage, 0, null, null, barrier);
            image.layout = layout;
        }
    }
    public static void batch(List<Change> changes, int sourceStages, int destinationStages) {
        if (changes.isEmpty()) return;
        boolean undefined = false;
        try (var stack = stackPush()) {
            var barriers = VkImageMemoryBarrier.calloc(changes.size(), stack);
            for (int index = 0; index < changes.size(); index++) {
                var change = changes.get(index);
                boolean discard = change.image.layout == VK_IMAGE_LAYOUT_UNDEFINED;
                if (discard) undefined = true;
                barriers.get(index).sType$Default().srcAccessMask(discard ? 0 : change.sourceAccess)
                        .dstAccessMask(change.destinationAccess).oldLayout(change.image.layout).newLayout(change.layout)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(change.image.image).subresourceRange().aspectMask(change.image.aspect)
                        .levelCount(change.image.mipLevels).layerCount(1);
            }
            var command = changes.get(0).image.commands.command();
            int actualSource = undefined ? sourceStages | VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : sourceStages;
            vkCmdPipelineBarrier(command, actualSource, destinationStages, 0, null, null, barriers);
            for (var change : changes) change.image.layout = change.layout;
        }
    }
}
