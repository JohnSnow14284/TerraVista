package dev.terravista.render;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import dev.terravista.config.ClientOptions;

/** 保留宿主附加视角变换并扩展远景投影范围。 */
public final class CameraProjection {
    public static float hostDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    public static Matrix4f extend(DepthConvention convention, Matrix4fc source) {
        float requested = ClientOptions.CONFIG.renderDistanceChunks(
                Minecraft.getInstance().options.getEffectiveRenderDistance()) * 16f;
        return extend(convention, source, Math.max(16 * 3000, (requested + 1024) * 2));
    }

    private static Matrix4f extend(DepthConvention convention, Matrix4fc source, float farPlane) {
        var raw = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix;
        var extra = raw.invert(new Matrix4f()).mul(source);
        float near = hostDistance() <= 32.0f ? 8f : 16f;
        float far = farPlane;
        if (convention.reverseZ()) {
            float previousNear = near;
            near = far;
            far = previousNear;
        }
        return extra.mulLocal(new Matrix4f(raw)
                .m22((convention.zeroToOne() ? far : far + near) / (near - far))
                .m32((convention.zeroToOne() ? far : far + far) * near / (near - far)));
    }

    private CameraProjection() {}
}
