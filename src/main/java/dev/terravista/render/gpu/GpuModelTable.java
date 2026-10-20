package dev.terravista.render.gpu;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.model.ModelStore;
import dev.terravista.model.BlendedTints;
import dev.terravista.model.TextureMipmaps;
import dev.terravista.render.DeviceBuffer;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 持有模型、生物群系设备表与图集，纹理上传单独管理。 */
public final class GpuModelTable implements ModelStore {
    final FrameCommands commands;
    final VulkanUploads uploads;
    public final GpuBuffer models;
    public final GpuBuffer colours;
    public final DeviceImage atlas;
    public final long sampler;
    private final ModelTextureUploads textures;

    public GpuModelTable(FrameCommands commands, VulkanUploads uploads) {
        this.commands = commands;
        this.uploads = uploads;
        textures = new ModelTextureUploads(this);
        GpuBuffer modelData = null, colourData = null;
        DeviceImage image = null;
        long createdSampler = 0;
        try {
            modelData = new GpuBuffer(commands, ModelStore.MODEL_BYTES * (1L << 16));
            modelData.zero();
            colourData = new GpuBuffer(commands, 4L * (BlendedTints.BASE + BlendedTints.CAPACITY));
            colourData.zero();
            image = new DeviceImage(commands, TextureMipmaps.FACE_SIZE * 3 * 256,
                    TextureMipmaps.FACE_SIZE * 2 * 256, TextureMipmaps.LAYERS,
                    VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT, false);
            ImageTransitions.transition(image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, 0,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
            try (var stack = stackPush()) {
                var create = VkSamplerCreateInfo.calloc(stack).sType$Default()
                        .magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST)
                        .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                        .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .minLod(0).maxLod(TextureMipmaps.LAYERS - 1);
                var handle = stack.mallocLong(1);
                check(vkCreateSampler(commands.device().device, create, null, handle), "vkCreateSampler(modelAtlas)");
                createdSampler = handle.get(0);
            }
        } catch (RuntimeException | Error failure) {
            if (createdSampler != 0) vkDestroySampler(commands.device().device, createdSampler, null);
            if (image != null) image.free();
            if (colourData != null) colourData.free();
            if (modelData != null) modelData.free();
            throw failure;
        }
        models = modelData;
        colours = colourData;
        atlas = image;
        sampler = createdSampler;
    }
    @Override public DeviceBuffer modelBuffer() { return models; }
    @Override public DeviceBuffer colourBuffer() { return colours; }
    @Override public void beginTextures() { textures.begin(); }
    @Override public void uploadTexture(int modelId, NativeBuffer texture) { textures.write(modelId, texture); }
    @Override public void endTextures() { textures.end(); }
    @Override public void free() {
        vkDestroySampler(commands.device().device, sampler, null);
        models.free();
        colours.free();
        atlas.free();
    }
}
