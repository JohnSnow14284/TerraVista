package dev.terravista.model;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/** 以六个捕获面、流体模型与固定着色构成去重键。 */
final class ModelDeduplication {
    record Key(ModelFacePixels down, ModelFacePixels up, ModelFacePixels north, ModelFacePixels south,
               ModelFacePixels west, ModelFacePixels east, int fluidModel, int tintColour, int foliage, boolean waterDatum, boolean crossPlant) {
        Key(ModelFacePixels[] faces, int fluidModel, int tintColour, int foliage, boolean waterDatum, boolean crossPlant) {
            this(faces[0], faces[1], faces[2], faces[3], faces[4], faces[5], fluidModel, tintColour, foliage, waterDatum, crossPlant);
        }
    }
    private final Object2IntOpenHashMap<Key> entries = new Object2IntOpenHashMap<>();
    ModelDeduplication() { entries.defaultReturnValue(-1); }
    int find(Key key) { return entries.getInt(key); }
    int insert(Key key) {
        int id = entries.size();
        entries.put(key, id);
        return id;
    }
    int size() { return entries.size(); }
}
