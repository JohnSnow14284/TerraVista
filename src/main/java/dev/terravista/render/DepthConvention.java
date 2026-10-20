package dev.terravista.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;

/** 保存设备裁剪深度范围与 Minecraft 默认深度比较规则。 */
public record DepthConvention(boolean zeroToOne, boolean reverseZ, boolean blockAtlasUvs) {
    public float clearDepth() { return reverseZ ? 0.0f : 1.0f; }
    public float inverseClearDepth() { return reverseZ ? 1.0f : 0.0f; }
    public static DepthConvention snapshot() {
        return new DepthConvention(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(),
                DepthStencilState.DEFAULT.depthTest().equals(CompareOp.GREATER_THAN_OR_EQUAL), false);
    }
}
