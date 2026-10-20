package dev.terravista.mesh;

/** 将三十二列行流合并为最大十六乘十六的有序矩形。 */
public abstract class RectangleMesher {
    private final long[] values = new long[32];
    private final int[] widths = new int[32];
    private final int[] heights = new int[32];
    private int pending;
    private int cursor;
    private int runWidth;
    private long runValue;

    public final void accept(long value) {
        int column = cursor++ & 31;
        if (column == 0) beginRow(value);
        if (value != runValue || runWidth == 16) {
            if (runValue != 0) {
                int end = column - 1;
                heights[end] = 1;
                widths[end] = runWidth;
                values[end] = runValue;
                pending |= 1 << end;
            }
            runValue = value;
            runWidth = 0;
        }
        runWidth++;
        boolean previous = (pending & (1 << column)) != 0;
        boolean heightLimit = false;
        if (runValue != 0 && previous && widths[column] == runWidth && values[column] == runValue) {
            int height = ++heights[column];
            runWidth = 0;
            runValue = 0;
            if (height != 16) return;
            heightLimit = true;
        }
        if (previous) {
            emit(column & 31, ((cursor - 1) >> 5) - (heightLimit ? 0 : 1),
                    widths[column], heights[column], values[column]);
            pending &= ~(1 << column);
        }
    }
    private void beginRow(long value) {
        if (runValue != 0) {
            if ((pending & (1 << 31)) != 0)
                emit(31, ((cursor - 1) >> 5) - 1, widths[31], heights[31], values[31]);
            pending |= 1 << 31;
            widths[31] = runWidth;
            heights[31] = 1;
            values[31] = runValue;
        }
        runValue = value;
        runWidth = 0;
    }
    private void emitPending(int mask) {
        int remaining = pending & mask;
        pending &= ~mask;
        while (remaining != 0) {
            int end = Integer.numberOfTrailingZeros(remaining);
            remaining &= ~Integer.lowestOneBit(remaining);
            emit(end, (cursor >> 5) - 1, widths[end], heights[end], values[end]);
        }
    }
    public final void skip(int count) {
        if (count == 0) return;
        if (runValue != 0) {
            accept(0);
            count--;
        }
        if (0 < count) {
            int mask = (int) ((1L << Math.min(32, count)) - 1) << (cursor & 31);
            emitPending(mask);
            cursor += count;
        }
    }
    public final void reset() { pending = 0; runWidth = 0; runValue = 0; cursor = 0; }
    public final void endRow() { if ((cursor & 31) != 0) skip(32 - (cursor & 31)); }
    public final void finish() {
        if (cursor != 0) {
            skip(32 - (cursor & 31));
            emitPending(-1);
        }
        reset();
    }
    /** 按输出顺序返回包含右下端点的矩形坐标。 */
    protected abstract void emit(int x, int y, int width, int height, long value);
}
