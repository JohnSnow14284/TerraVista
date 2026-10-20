package dev.terravista.compat;

import dev.terravista.render.RenderSession;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.minecraft.core.SectionPos;

/** 收集 Sodium 已构建可见区块并协调原生地形淡入。 */
public final class SodiumVisibility {
    public static RenderRegion region(RenderRegionManager regions, int x, int y, int z) {
        var region = regions.getForChunk(x, y, z);
        int index = LocalSectionIndex.pack(x, y, z);
        if (built(region, index)) {
            var session = RenderSessionAccess.current();
            if (session != null) append(session, x, y, z);
        }
        return region;
    }

    public static RenderSection section(SectionStorage sections, int x, int y, int z) {
        var section = sections.getCurrent(x, y, z);
        var session = RenderSessionAccess.current();
        if (session != null && built(section, LocalSectionIndex.pack(x, y, z))) {
            append(session, x, y, z);
        }
        return section;
    }

    private static boolean built(RenderRegion region, int index) {
        return region != null && (region.getSectionFlags(index) & RenderSectionFlags.MASK_IS_BUILT) != 0;
    }

    private static boolean built(RenderSection section, int index) {
        return section != null && (section.getRegion().getSectionFlags(index) & RenderSectionFlags.MASK_IS_BUILT) != 0;
    }

    private static void append(RenderSession session, int x, int y, int z) {
        if (session.visibleBounds() != null) session.visibleBounds().append(SectionPos.asLong(x, y, z));
    }

    public static int fadeOrigin(int original) {
        if (original == -1) return original;
        return RenderSessionAccess.current() == null ? original : -999999;
    }

    private SodiumVisibility() {}
}
