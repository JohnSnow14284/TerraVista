package dev.terravista.bootstrap;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;

/** 识别加载环境与已加载模组。 */
public final class RuntimeEnvironment {
    public static final boolean IN_MINECRAFT;
    public static final boolean DEDICATED_SERVER;
    static {
        var loader = FMLLoader.getCurrentOrNull();
        IN_MINECRAFT = loader != null;
        DEDICATED_SERVER = loader != null && loader.getDist() == Dist.DEDICATED_SERVER;
    }
    public static boolean modLoaded(String id) {
        var loader = FMLLoader.getCurrentOrNull();
        return loader != null && loader.getLoadingModList().getModFileById(id) != null;
    }
}
