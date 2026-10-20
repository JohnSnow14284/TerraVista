package dev.terravista.model;

/** 持有等待渲染线程批量上传的 CPU 结果。 */
sealed interface ModelUpload permits BakedModelUpload, BiomeColourUpload {
    void upload(ModelStore store);
    void free();
}
