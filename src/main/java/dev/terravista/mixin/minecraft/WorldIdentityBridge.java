package dev.terravista.mixin.minecraft;

import dev.terravista.world.WorldIdentity;
import dev.terravista.world.WorldIdentityCache;
import dev.terravista.world.WorldIdentityProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在世界构造完成后连接身份初始化与访问。 */
@Mixin(Level.class)
public abstract class WorldIdentityBridge implements WorldIdentityProvider {
    @Unique private final WorldIdentityCache terravista$identity = new WorldIdentityCache();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void terravista$initializeIdentity(WritableLevelData data, ResourceKey<Level> key,
                                              RegistryAccess registries, Holder<DimensionType> dimension,
                                              boolean client, boolean debug, long seed, int neighbors,
                                              CallbackInfo callback) {
        terravista$identity.initialize(key, seed, dimension);
    }

    @Override public WorldIdentity terraVistaIdentity() { return terravista$identity.current(); }
}
