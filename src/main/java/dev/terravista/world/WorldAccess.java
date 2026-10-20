package dev.terravista.world;

import dev.terravista.bootstrap.SessionLifecycle;
import net.minecraft.world.level.Level;

/** 通过当前会话查询世界身份对应的实例。 */
public final class WorldAccess {
    private WorldAccess() {}

    public static VoxelWorld getOrCreate(WorldIdentity identity) { return getOrCreate(identity, false); }
    public static VoxelWorld getOrCreate(WorldIdentity identity, boolean allowNull) {
        var session = SessionLifecycle.current();
        if (session == null) {
            identity.cachedWorld = null;
            return null;
        }
        var world = session.worlds().getOrCreate(identity);
        if (allowNull && world == null) throw new IllegalStateException("TerraVista World: Engine null on creation");
        return world;
    }
    public static VoxelWorld find(WorldIdentity identity) {
        var session = SessionLifecycle.current();
        if (session == null) {
            identity.cachedWorld = null;
            return null;
        }
        return session.worlds().find(identity);
    }
    public static WorldIdentity identity(Level level) {
        if (level == null) return null;
        return ((WorldIdentityProvider)level).terraVistaIdentity();
    }
    public static VoxelWorld forLevel(Level level) {
        var identity = identity(level);
        return identity == null ? null : getOrCreate(identity);
    }
    public static VoxelWorld findLevel(Level level) {
        var identity = identity(level);
        return identity == null ? null : find(identity);
    }
}
