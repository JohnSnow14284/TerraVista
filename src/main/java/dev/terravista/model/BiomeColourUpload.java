package dev.terravista.model;

import dev.terravista.memory.NativeBuffer;

/** 持有生物群系颜色矩阵与模型索引，等待排队上传。 */
final class BiomeColourUpload implements ModelUpload {
    final NativeBuffer colours;
    final NativeBuffer modelIndices;
    BiomeColourUpload(int biomes, int models) {
        colours = new NativeBuffer(biomes * models * 4);
        modelIndices = new NativeBuffer(models * 8);
    }
    @Override public void upload(ModelStore store) { store.uploadBiomes(colours, modelIndices); }
    @Override public void free() {
        if (!colours.isReleased()) {
            colours.free();
            modelIndices.free();
        }
    }
}
