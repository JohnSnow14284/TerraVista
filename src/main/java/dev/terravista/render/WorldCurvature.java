package dev.terravista.render;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.terravista.config.ClientOptions;
import net.minecraft.resources.Identifier;
import org.joml.FrustumIntersection;

/** 统一近景和远景的曲率参数与保守包围盒。 */
public final class WorldCurvature {
    private WorldCurvature() {}
    public static float coefficient() { return ClientOptions.CONFIG.worldCurvature / 12742000f; }

    public static String shader(Identifier id, ShaderType type, String source) {
        if (source == null || type != ShaderType.VERTEX || coefficient() == 0) return source;
        String coordinate;
        if (id.equals(Identifier.parse("minecraft:core/terrain"))) coordinate = "pos";
        else if (id.equals(Identifier.parse("sodium:blocks/block_layer_opaque"))) coordinate = "position";
        else return source;
        String bend = coordinate + ".y -= dot(" + coordinate + ".xz, " + coordinate + ".xz) * "
                + Float.toString(coefficient()) + ";\n    ";
        return source.replace("gl_Position =", bend + "gl_Position =");
    }

    public static int intersect(FrustumIntersection frustum, float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ) {
        float curve = coefficient();
        if (curve != 0) {
            float nearX = Math.max(Math.max(minX, -maxX), 0), nearZ = Math.max(Math.max(minZ, -maxZ), 0);
            float farX = Math.max(Math.abs(minX), Math.abs(maxX)), farZ = Math.max(Math.abs(minZ), Math.abs(maxZ));
            float a = -curve * (nearX * nearX + nearZ * nearZ), b = -curve * (farX * farX + farZ * farZ);
            float margin = 0.001f + Math.max(Math.abs(a), Math.abs(b)) * 0.000002f;
            minY += Math.min(a, b) - margin;
            maxY += Math.max(a, b) + margin;
        }
        return frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
