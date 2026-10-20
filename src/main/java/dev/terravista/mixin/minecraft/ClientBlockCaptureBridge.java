package dev.terravista.mixin.minecraft;

import dev.terravista.world.ClientBlockCapture;
import dev.terravista.render.DistantBeacons;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 连接客户端世界初始化与方块变更采集。 */
@Mixin(ClientLevel.class)
public abstract class ClientBlockCaptureBridge {
    @Unique private final ClientBlockCapture terravista$blockCapture = new ClientBlockCapture();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terravista$initializeCapture(ClientPacketListener connection, ClientLevel.ClientLevelData data,
            ResourceKey<Level> dimension, Holder<DimensionType> type, int chunkRadius, int simulationDistance,
            LevelExtractor extractor, boolean debug, long seed, int seaLevel, CallbackInfo callback) {
        terravista$blockCapture.initialize((Level)(Object)this);
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void terravista$captureBlock(BlockPos position, BlockState previous, BlockState updated, CallbackInfo callback) {
        DistantBeacons.changed((Level)(Object)this, position, previous, updated);
        terravista$blockCapture.changed((Level)(Object)this, position, previous, updated);
    }
}
