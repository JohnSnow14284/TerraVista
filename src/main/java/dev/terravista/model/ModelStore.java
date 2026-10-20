package dev.terravista.model;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.render.DeviceBuffer;
import dev.terravista.render.UploadStream;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/** 定义模型与图集存储及原生上传操作。 */
public interface ModelStore {
    int MODEL_BYTES = 64;
    DeviceBuffer modelBuffer();
    DeviceBuffer colourBuffer();
    void beginTextures();
    void uploadTexture(int modelId, NativeBuffer texture);

    default void uploadModel(int modelId, NativeBuffer model, int biomeIndex,
                             @Nullable NativeBuffer biomes, NativeBuffer texture) {
        model.copyTo(UploadStream.current().upload(modelBuffer(), (long) modelId * MODEL_BYTES, MODEL_BYTES));
        if (biomeIndex != -1 && biomes != null) {
            biomes.copyTo(UploadStream.current().upload(colourBuffer(), biomeIndex * 4L, biomes.bytes));
        }
        uploadTexture(modelId, texture);
    }
    default void uploadBiomes(NativeBuffer colours, NativeBuffer modelIndexPairs) {
        colours.copyTo(UploadStream.current().upload(colourBuffer(), 0, colours.bytes));
        long pointer = modelIndexPairs.address;
        for (long offset = 0; offset < modelIndexPairs.bytes; offset += 8) {
            long value = MemoryUtil.memGetLong(pointer);
            pointer += 8;
            MemoryUtil.memPutInt(UploadStream.current().upload(modelBuffer(),
                    MODEL_BYTES * (value & 0xFFFF_FFFFL) + 4L * 6 + 4, 4), (int) (value >>> 32));
        }
    }
    default void finish() { UploadStream.current().commit(); }
    void endTextures();
    void free();
}
