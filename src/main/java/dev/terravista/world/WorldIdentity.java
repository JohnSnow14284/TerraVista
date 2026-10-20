package dev.terravista.world;

import java.lang.ref.WeakReference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/** 保存维度与种子身份，JSON 与目录命名分别管理。 */
public final class WorldIdentity {
    private static final ResourceKey<DimensionType> NULL_DIMENSION = ResourceKey.create(
            Registries.DIMENSION_TYPE, Identifier.parse("terravista:null_dimension_id"));
    public final ResourceKey<Level> key;
    public final long biomeSeed;
    public final ResourceKey<DimensionType> dimension;
    private final transient long hash;
    transient WeakReference<VoxelWorld> cachedWorld;

    public WorldIdentity(ResourceKey<Level> key, long biomeSeed, ResourceKey<DimensionType> dimension) {
        if (key == null) throw new IllegalStateException("TerraVista World: Key cannot be null");
        dimension = dimension == null ? NULL_DIMENSION : dimension;
        this.key = key; this.biomeSeed = biomeSeed; this.dimension = dimension;
        hash = mix(keyHash(key)) ^ mix(keyHash(dimension)) ^ mix(biomeSeed);
    }
    @Override public int hashCode() { return (int) hash; }
    public long longHash() { return hash; }
    @Override public boolean equals(Object object) {
        return object instanceof WorldIdentity other && other.hash == hash && other.biomeSeed == biomeSeed
                && same(other.key, key) && same(other.dimension, dimension);
    }
    private static boolean same(ResourceKey<?> a, ResourceKey<?> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.registry().equals(b.registry()) && a.identifier().equals(b.identifier());
    }
    public static long mix(long seed) {
        seed += 918759875987111L;
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }
    private static long keyHash(ResourceKey<?> key) {
        var registry = key.registry(); var identifier = key.identifier();
        int a = registry == null ? 0 : registry.hashCode();
        int b = identifier == null ? 0 : identifier.hashCode();
        return (Integer.toUnsignedLong(a) << 32) | Integer.toUnsignedLong(b);
    }
    @Override public String toString() {
        return "WorldIdentity[" + key.identifier().toString() + ", " + biomeSeed + ", " + dimension.identifier().toString() + ']';
    }
}
