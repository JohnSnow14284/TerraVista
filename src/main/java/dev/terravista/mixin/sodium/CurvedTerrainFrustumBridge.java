package dev.terravista.mixin.sodium;

import dev.terravista.render.WorldCurvature;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.SimpleFrustum;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 将曲面区块的包围盒交给现有视锥判断。 */
@Mixin(value = SimpleFrustum.class, remap = false)
public class CurvedTerrainFrustumBridge {
    @Shadow @Final private FrustumIntersection frustum;
    @Inject(method = "testSection", at = @At("HEAD"), cancellable = true)
    private void terravista$section(float x, float y, float z, CallbackInfoReturnable<Boolean> result) {
        terravista$expanded(x, y, z, Viewport.CHUNK_SECTION_PADDED_RADIUS, result);
    }
    @Inject(method = "testSectionExpanded", at = @At("HEAD"), cancellable = true)
    private void terravista$expanded(float x, float y, float z, float radius, CallbackInfoReturnable<Boolean> result) {
        if (WorldCurvature.coefficient() != 0) result.setReturnValue(WorldCurvature.intersect(frustum,
                x - radius, y - radius, z - radius, x + radius, y + radius, z + radius) != FrustumIntersection.OUTSIDE);
    }
    @Inject(method = "testAab", at = @At("HEAD"), cancellable = true)
    private void terravista$box(float x, float y, float z, float a, float b, float c, CallbackInfoReturnable<Boolean> result) {
        if (WorldCurvature.coefficient() != 0)
            result.setReturnValue(WorldCurvature.intersect(frustum, x, y, z, a, b, c) != FrustumIntersection.OUTSIDE);
    }
    @Inject(method = "intersectAab", at = @At("HEAD"), cancellable = true)
    private void terravista$intersection(float x, float y, float z, float a, float b, float c, CallbackInfoReturnable<Integer> result) {
        if (WorldCurvature.coefficient() != 0)
            result.setReturnValue(WorldCurvature.intersect(frustum, x, y, z, a, b, c));
    }
}
