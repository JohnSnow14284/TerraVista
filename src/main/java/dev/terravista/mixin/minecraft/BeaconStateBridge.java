package dev.terravista.mixin.minecraft;

import dev.terravista.render.DistantBeacons;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 发布客户端信标完成更新后的光束状态。 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconStateBridge {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void terravista$beam(Level level, BlockPos position, BlockState state, BeaconBlockEntity beacon, CallbackInfo callback) {
        if (level.isClientSide()) DistantBeacons.observe(level, position, beacon);
    }
}
