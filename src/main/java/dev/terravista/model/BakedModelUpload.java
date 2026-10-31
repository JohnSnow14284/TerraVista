package dev.terravista.model;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.render.UploadStream;
import org.jetbrains.annotations.Nullable;

/** 持有模型的六十四字节数据、纹理分配与可选颜色数据。 */
final class BakedModelUpload implements ModelUpload {
    final NativeBuffer model = new NativeBuffer(ModelStore.MODEL_BYTES).zero();
    final NativeBuffer texture = new NativeBuffer(TextureMipmaps.allocationBytes());
    int modelId = -1;
    int biomeIndex = -1;
    @Nullable NativeBuffer colours;
    @Override public long stagingBytes(int alignment) {
        return UploadStream.alignUp(model.bytes, alignment)
                + (biomeIndex != -1 && colours != null ? UploadStream.alignUp(colours.bytes, alignment) : 0)
                + UploadStream.alignUp(texture.bytes, alignment);
    }
    @Override public void upload(ModelStore store) {
        store.uploadModel(modelId, model, biomeIndex, colours, texture);
        modelId = -1;
    }
    @Override public void free() {
        model.free();
        texture.free();
        if (colours != null) colours.free();
    }
}
