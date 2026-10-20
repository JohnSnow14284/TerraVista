package dev.terravista.render.tree;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

/** 增量维护圆形网格成员，并按顺序和预算交付增删操作。 */
public final class CircularResidency {
    private final Long2ByteOpenHashMap pending = new Long2ByteOpenHashMap(1 << 13);
    private final int[] halfSpans;
    private final int radius;
    private int centerX, centerZ;

    @FunctionalInterface public interface CellConsumer { void accept(int x, int z); }

    public CircularResidency(int radius, int x, int z, boolean fill) { this(null, radius, x, z, fill); }
    public CircularResidency(CircularResidency previous, int radius, int x, int z, boolean fill) {
        centerX = x;
        centerZ = z;
        this.radius = radius;
        halfSpans = new int[radius * 2 + 1];
        for (int offset = -radius; offset <= radius; offset++)
            halfSpans[offset + radius] = (int) Math.sqrt(radius * radius - offset * offset);
        if (previous != null) {
            pending.putAll(previous.pending);
            previous.pending.clear();
        }
        if (fill) fill(true);
    }
    private static long key(int x, int z) { return Integer.toUnsignedLong(x) | (Integer.toUnsignedLong(z) << 32); }
    private void fill(boolean load) {
        for (int column = 0; column <= radius * 2; column++) {
            int x = centerX + column - radius, span = halfSpans[column];
            for (int z = centerZ - span; z <= centerZ + span; z++) adjust(x, z, load, false);
        }
    }
    private void adjust(int x, int z, boolean load, boolean describe) {
        byte previous = pending.addTo(key(x, z), (byte) (load ? 1 : -1));
        if ((load && previous > 0) || (!load && previous < 0)) {
            if (describe) throw new IllegalStateException("TerraVista Render: x: " + x + ", z: " + z + " state: " + pending.get(key(x, z)));
            throw new IllegalStateException();
        }
    }
    public void unload() { fill(false); }
    public void move(int x, int z) {
        if (radius + 1 < Math.abs(x - centerX) || radius + 1 < Math.abs(z - centerZ)) {
            fill(false);
            centerX = x;
            centerZ = z;
            fill(true);
        } else {
            if (x != centerX) shift(true, x - centerX);
            if (z != centerZ) shift(false, z - centerZ);
        }
    }
    private void adjustAxis(boolean xAxis, int fixed, int moving, boolean load, boolean describe) {
        if (xAxis) adjust(moving, fixed, load, describe);
        else adjust(fixed, moving, load, describe);
    }
    private void shift(boolean xAxis, int delta) {
        if (delta == 0) return;
        int along = xAxis ? centerX : centerZ;
        int across = xAxis ? centerZ : centerX;
        if (delta == -1 || delta == 1) {
            for (int column = 0; column <= radius * 2; column++) {
                int fixed = across + column - radius, span = halfSpans[column] * delta;
                adjustAxis(xAxis, fixed, along + span + delta, true, !xAxis);
                adjustAxis(xAxis, fixed, along - span, false, !xAxis);
            }
        } else {
            int direction = Integer.signum(delta);
            for (int column = 0; column <= radius * 2; column++) {
                int fixed = across + column - radius, span = halfSpans[column] * direction;
                int addEdge = along + span;
                for (int coordinate = addEdge + (direction < 0 ? delta : 1); coordinate <= addEdge + (direction < 0 ? -1 : delta); coordinate++)
                    adjustAxis(xAxis, fixed, coordinate, true, false);
                int removeEdge = along - span;
                for (int coordinate = removeEdge + (direction < 0 ? delta + 1 : 0); coordinate < removeEdge + (direction < 0 ? 1 : delta); coordinate++)
                    adjustAxis(xAxis, fixed, coordinate, false, false);
            }
        }
        if (xAxis) centerX += delta;
        else centerZ += delta;
    }
    public int process(int budget, CellConsumer added, CellConsumer removed) {
        if (pending.isEmpty()) return 0;
        var entries = pending.long2ByteEntrySet().fastIterator();
        int processed = 0;
        while (entries.hasNext() && budget-- != 0) {
            var entry = entries.next();
            if (entry.getByteValue() == 0) {
                entries.remove();
                budget++;
                continue;
            }
            processed++;
            byte operation = entry.getByteValue();
            if (operation != 1 && operation != -1) throw new IllegalStateException();
            long position = entry.getLongKey();
            int x = (int) (position & 0xFFFFFFFFL), z = (int) ((position >>> 32) & 0xFFFFFFFFL);
            if (operation == 1) added.accept(x, z);
            else removed.accept(x, z);
            entries.remove();
        }
        return processed;
    }
}
