package dev.terravista.render;

import java.lang.reflect.Field;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.util.Mth;
import org.joml.*;

/** 保存帧输入并计算相机与剔除状态。 */
public final class ViewState {
    private static final Field PLANE_STORAGE;
    static {
        try {
            PLANE_STORAGE = FrustumIntersection.class.getDeclaredField("planes");
            PLANE_STORAGE.setAccessible(true);
        } catch (NoSuchFieldException failure) { throw new RuntimeException(failure); }
    }
    public int width, height, frame;
    public Matrix4f sourceProjection = new Matrix4f();
    public Matrix4f projection = new Matrix4f();
    public Matrix4f modelView = new Matrix4f();
    public final FrustumIntersection frustum = new FrustumIntersection();
    public final Vector4f[] planes;
    public double cameraX, cameraY, cameraZ;
    public FogParameters fog;
    public final Matrix4f transform = new Matrix4f();
    public final Vector3i section = new Vector3i();
    public final Vector3f offset = new Vector3f();

    public ViewState() {
        try { planes = (Vector4f[]) PLANE_STORAGE.get(frustum); }
        catch (IllegalAccessException failure) { throw new RuntimeException(failure); }
    }
    public ViewState sourceProjection(Matrix4fc value) { sourceProjection.set(value); return this; }
    public ViewState projection(Matrix4f value) { projection = value; return this; }
    public ViewState modelView(Matrix4fc value) { modelView.set(value); return this; }
    public ViewState camera(double x, double y, double z) { cameraX = x; cameraY = y; cameraZ = z; return this; }
    public ViewState size(int width, int height) { this.width = width; this.height = height; return this; }
    public ViewState fog(FogParameters value) { fog = value; return this; }
    public ViewState refresh() {
        projection.mul(modelView, transform);
        frustum.set(transform, false);
        int x = Mth.floor(cameraX) >> 5;
        int y = Mth.floor(cameraY) >> 5;
        int z = Mth.floor(cameraZ) >> 5;
        section.set(x, y, z);
        offset.set((float) (cameraX - (x << 5)), (float) (cameraY - (y << 5)), (float) (cameraZ - (z << 5)));
        return this;
    }
}
