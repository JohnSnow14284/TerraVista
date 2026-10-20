package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import static org.lwjgl.vulkan.VK10.*;

/** 持有 Hi-Z 图像、尺寸与首次使用的布局状态。 */
public final class DepthPyramid {
    private final FrameCommands commands;
    private final PyramidReduction reduction;
    private DeviceImage image;
    private int levels;
    private int width, height;
    private boolean initialized;

    public DepthPyramid(FrameCommands commands, DepthConvention convention) {
        this.commands = commands;
        reduction = new PyramidReduction(commands, convention);
    }
    private void resize(int width, int height) {
        int nextLevels = Math.max(1, (int) Math.ceil(Math.log(Math.max(width, height)) / Math.log(2)));
        var replacement = new DeviceImage(commands, width, height, nextLevels, VK_FORMAT_R32_SFLOAT,
                VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT, true);
        if (image != null) image.free();
        image = replacement;
        levels = nextLevels;
        this.width = width;
        this.height = height;
        initialized = false;
    }
    public void build(long depthView, int sourceWidth, int sourceHeight) {
        int nextWidth = Integer.highestOneBit(sourceWidth);
        int nextHeight = Integer.highestOneBit(sourceHeight);
        if (width != nextWidth || height != nextHeight || image == null) resize(nextWidth, nextHeight);
        var command = commands.command();
        if (!initialized) {
            ImageTransitions.transition(image, VK_IMAGE_LAYOUT_GENERAL, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, 0,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT);
            initialized = true;
        }
        reduction.record(command, image, levels, depthView, sourceWidth, sourceHeight);
    }
    public long view() { return image.view; }
    public long sampler() { return reduction.sampler; }
    public int packedSize() { return (width << 16) | height; }
    public void close() {
        if (image != null) image.free();
        reduction.close();
    }
}
