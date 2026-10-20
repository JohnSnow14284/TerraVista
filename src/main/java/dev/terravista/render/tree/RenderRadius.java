package dev.terravista.render.tree;

import dev.terravista.world.SectionCoordinates;
import java.util.function.LongConsumer;

/** 将相机距离与圆形网格操作转换为完整四级 LOD 列。 */
public final class RenderRadius {
    private final LongConsumer added, removed;
    private final int minSection, maxSection;
    private CircularResidency residency;
    private int radius;
    private double x, z;

    public RenderRadius(int minSection, int maxSection, LongConsumer added, LongConsumer removed) {
        this.added = added;
        this.removed = removed;
        radius = 2;
        residency = new CircularResidency(radius, 0, 0, true);
        this.minSection = minSection;
        this.maxSection = maxSection;
    }
    public void radius(int radius) {
        if (radius == this.radius) return;
        this.radius = radius;
        residency.unload();
        residency = new CircularResidency(residency, radius, ((int) x) >> 9, ((int) z) >> 9, true);
    }
    public boolean process(double x, double z, int processRate) {
        double dx = this.x - x, dz = this.z - z;
        if (128 * 128 < dx * dx + dz * dz) {
            this.x = x;
            this.z = z;
            residency.move(((int) x) >> 9, ((int) z) >> 9);
        }
        return residency.process(processRate, (cx, cz) -> column(cx, cz, added), (cx, cz) -> column(cx, cz, removed)) != 0;
    }
    private void column(int x, int z, LongConsumer consumer) {
        for (int y = minSection; y <= maxSection; y++) consumer.accept(SectionCoordinates.key(4, x, y, z));
    }
}
