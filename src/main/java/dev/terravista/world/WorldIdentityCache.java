package dev.terravista.world;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/** 保存世界构造参数生成的身份。 */
public final class WorldIdentityCache {
    private WorldIdentity identity;

    public void initialize(ResourceKey<Level> key, long seed, Holder<DimensionType> dimension) {
        identity = key == null ? null : new WorldIdentity(key, seed,
                dimension == null ? null : dimension.unwrapKey().orElse(null));
    }

    public WorldIdentity current() { return identity; }
}
