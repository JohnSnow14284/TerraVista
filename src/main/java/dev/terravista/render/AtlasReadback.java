package dev.terravista.render;

import com.mojang.renderpearl.api.textures.GpuTexture;

/** 从当前 Vulkan 后端读回零级 RGBA8 图集。 */
public abstract class AtlasReadback {
    private static AtlasReadback current;
    public abstract int[] read(GpuTexture atlas, int width, int height);
    public static AtlasReadback current() {
        var reader = current;
        if (reader == null) throw new IllegalStateException("TerraVista Render: Vulkan atlas reader not initialized");
        return reader;
    }
    public static void register(AtlasReadback reader) {
        if (current != null) throw new IllegalStateException("TerraVista Render: Atlas texture reader already initialized");
        current = reader;
    }
    public static void clear() { current = null; }
}
