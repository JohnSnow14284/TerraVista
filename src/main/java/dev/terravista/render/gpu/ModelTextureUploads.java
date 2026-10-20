package dev.terravista.render.gpu;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.model.TextureMipmaps;
import org.lwjgl.vulkan.VkBufferImageCopy;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

/** 管理渲染线程纹理批次、图集布局与四级 mip 拷贝区域。 */
final class ModelTextureUploads {
    private final GpuModelTable table;
    private boolean active;
    private boolean wroteTextures;

    ModelTextureUploads(GpuModelTable table) { this.table = table; }
    void begin() { active = true; wroteTextures = false; }
    void write(int modelId, NativeBuffer texture) {
        if (!active) throw new IllegalStateException("TerraVista Render: Texture upload outside batch");
        if (!wroteTextures) {
            ImageTransitions.transition(table.atlas, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
            wroteTextures = true;
        }
        final int size = TextureMipmaps.FACE_SIZE;
        int x = (modelId & 0xFF) * size * 3;
        int y = ((modelId >> 8) & 0xFF) * size * 2;
        int bytes = 0;
        for (int mip = 0; mip < TextureMipmaps.LAYERS; mip++) bytes += (size * size * 3 * 2 * 4) >> (mip << 1);
        long offset = table.uploads.rawUploadAddress(bytes);
        memCopy(texture.address, table.uploads.baseAddress() + offset, bytes);
        var command = table.commands.command();
        try (var stack = stackPush()) {
            var regions = VkBufferImageCopy.calloc(TextureMipmaps.LAYERS, stack);
            long source = offset;
            for (int mip = 0; mip < TextureMipmaps.LAYERS; mip++) {
                var region = regions.get(mip);
                region.bufferOffset(source).bufferRowLength(0).bufferImageHeight(0);
                region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(mip).baseArrayLayer(0).layerCount(1);
                region.imageOffset().set(x >> mip, y >> mip, 0);
                region.imageExtent().set((size * 3) >> mip, (size * 2) >> mip, 1);
                source += (size * size * 3 * 2 * 4) >> (mip << 1);
            }
            vkCmdCopyBufferToImage(command, table.uploads.stagingBuffer(), table.atlas.image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, regions);
        }
    }
    void end() {
        if (wroteTextures) ImageTransitions.transition(table.atlas, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
        active = false;
    }
}
