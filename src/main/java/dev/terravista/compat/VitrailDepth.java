package dev.terravista.compat;

import dev.terravista.render.CameraProjection;
import org.joml.Matrix4fc;
import org.joml.Vector2f;

/** The reversed-Z projection shared by packed rasterization and Vitrail's distant-depth conversion. */
public final class VitrailDepth {
    public static Vector2f row(Vector2f target) {
        return row(CameraProjection.nearPlane(), CameraProjection.farPlane(), target);
    }

    public static Vector2f row(float near, float far, Vector2f target) {
        if (!(near > 0 && far > near) || !Float.isFinite(far)) throw new IllegalArgumentException("Invalid LOD depth planes");
        return target.set(near / (far - near), far * near / (far - near));
    }

    public static Vector2f worldPair(Matrix4fc world, Vector2f distantRow) {
        float scale = world.m32() / distantRow.y;
        return distantRow.set(scale, scale * distantRow.x - world.m22());
    }

    private VitrailDepth() {}
}
