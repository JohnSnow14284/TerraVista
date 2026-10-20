package dev.terravista.model;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 提供六面投影所需的旋转、翻转与取整变换。 */
final class ModelViews {
    static final int COUNT = 6;
    private static final Matrix4f[] VIEWS = new Matrix4f[COUNT];
    static {
        create(0, -90, 0, 0, 0);
        create(1, 90, 0, 0, 0b100);
        create(2, 0, 180, 0, 0b001);
        create(3, 0, 0, 0, 0);
        create(4, 0, 90, 270, 0b100);
        create(5, 0, 270, 270, 0);
    }
    private ModelViews() {}
    static Matrix4f view(int face) { return VIEWS[face]; }
    private static void create(int face, float pitch, float yaw, float rotation, int flip) {
        var stack = new PoseStack();
        stack.translate(0.5f, 0.5f, 0.5f);
        stack.rotate(quaternion(new Vector3f(0, 0, 1), rotation));
        stack.rotate(quaternion(new Vector3f(1, 0, 0), pitch));
        stack.rotate(quaternion(new Vector3f(0, 1, 0), yaw));
        stack.mulPose(new Matrix4f().scale(1 - 2 * (flip & 1), 1 - (flip & 2), 1 - ((flip >> 1) & 2)));
        stack.translate(-0.5f, -0.5f, -0.5f);
        var matrix = new Matrix4f(stack.last().pose());
        matrix = new Matrix4f().set(
                2, 0, 0, 0,
                0, 2, 0, 0,
                0, 0, -2, 0,
                -1, -1, 1, 1).mul(matrix);
        VIEWS[face] = matrix;
    }
    private static Quaternionf quaternion(Vector3f axis, float angle) {
        angle = (float) Math.toRadians(angle);
        float half = angle / 2.0f;
        float sine = (float) Math.sin(half);
        float inverseLength = (float) (1 / Math.sqrt(axis.lengthSquared()));
        return new Quaternionf(axis.x * inverseLength * sine,
                axis.y * inverseLength * sine, axis.z * inverseLength * sine, Math.cos(half));
    }
}
