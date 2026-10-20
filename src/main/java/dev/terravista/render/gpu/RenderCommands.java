package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 设置完整目标的动态光栅范围与地形合成深度比较规则。 */
public final class RenderCommands {
    public static void viewport(VkCommandBuffer command, int width, int height) {
        try (var stack = stackPush()) {
            var viewport = VkViewport.calloc(1, stack).x(0).y(0).width(width).height(height).minDepth(0).maxDepth(1);
            vkCmdSetViewport(command, 0, viewport);
            var scissor = VkRect2D.calloc(1, stack);
            scissor.extent(extent -> extent.width(width).height(height));
            vkCmdSetScissor(command, 0, scissor);
        }
    }

    public static int closerEqual(DepthConvention convention) {
        return convention.reverseZ() ? VK_COMPARE_OP_GREATER_OR_EQUAL : VK_COMPARE_OP_LESS_OR_EQUAL;
    }

    private RenderCommands() {}
}
