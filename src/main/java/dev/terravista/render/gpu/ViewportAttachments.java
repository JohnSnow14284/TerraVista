package dev.terravista.render.gpu;

import dev.terravista.render.ViewState;
import static org.lwjgl.vulkan.VK10.*;

/** 全部图像与视图创建成功后替换离屏附件集合。 */
public final class ViewportAttachments {
    private final FrameCommands commands;
    public DeviceImage colour, shadedColour, depthStencil, depthBound;
    public long depthSampleView, depthBoundSampleView;

    ViewportAttachments(FrameCommands commands) { this.commands = commands; }

    public boolean ensure(ViewState state) {
        if (state.width <= 0 || state.height <= 0) return false;
        if (colour != null && colour.width == state.width && colour.height == state.height) return false;
        DeviceImage nextColour = null, nextShaded = null, nextDepth = null, nextBound = null;
        long nextDepthView;
        try {
            nextColour = new DeviceImage(commands, state.width, state.height, 1, VK_FORMAT_R8G8B8A8_UNORM,
                    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT, false);
            nextShaded = new DeviceImage(commands, state.width, state.height, 1, VK_FORMAT_R8G8B8A8_UNORM,
                    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT, false);
            nextDepth = new DeviceImage(commands, state.width, state.height, 1, VK_FORMAT_D32_SFLOAT_S8_UINT,
                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT, false);
            nextDepthView = nextDepth.aspectView(VK_IMAGE_ASPECT_DEPTH_BIT);
            nextBound = new DeviceImage(commands, state.width, state.height, 1, VK_FORMAT_D32_SFLOAT,
                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT, false);
        } catch (RuntimeException | Error failure) {
            if (nextBound != null) nextBound.free();
            if (nextDepth != null) nextDepth.free();
            if (nextShaded != null) nextShaded.free();
            if (nextColour != null) nextColour.free();
            throw failure;
        }
        if (colour != null) close();
        colour = nextColour;
        shadedColour = nextShaded;
        depthStencil = nextDepth;
        depthSampleView = nextDepthView;
        depthBound = nextBound;
        depthBoundSampleView = nextBound.view;
        return true;
    }

    public void close() {
        if (colour != null) {
            colour.free();
            shadedColour.free();
            depthStencil.free();
            depthBound.free();
        }
    }
}
