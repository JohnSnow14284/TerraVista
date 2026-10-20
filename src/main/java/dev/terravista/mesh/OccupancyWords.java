package dev.terravista.mesh;

import org.lwjgl.system.MemoryUtil;
import java.util.Arrays;

/** 保存两种网格布局的四立方分块目录与占用位。 */
public final class OccupancyWords {
    private final int tileMask;
    private final int cellMask;
    private final int wordsPerTile;
    private final long[] words;
    private long tiles;

    private OccupancyWords(int tileMask, int cellMask, int wordsPerTile) {
        this.tileMask = tileMask;
        this.cellMask = cellMask;
        this.wordsPerTile = wordsPerTile;
        words = new long[64 * wordsPerTile];
    }
    public static OccupancyWords blocks32() { return new OccupancyWords(0b11000_11000_11000, 0b00111_00111_00111, 8); }
    public static OccupancyWords blocks16() { return new OccupancyWords(0b1100_1100_1100, 0b0011_0011_0011, 1); }
    public void set(int position) {
        long tile = 1L << Integer.compress(position, tileMask);
        int cell = Integer.compress(position, cellMask);
        int base = Long.bitCount(tiles & (tile - 1)) * wordsPerTile;
        if ((tiles & tile) == 0) {
            long above = tiles & ~((tile << 1) - 1);
            if (above != 0) {
                int first = base + wordsPerTile;
                int count = Long.bitCount(above);
                for (int index = first + count * wordsPerTile - 1; first <= index; index--)
                    words[index] = words[index - wordsPerTile];
                for (int index = base; index < base + wordsPerTile; index++) words[index] = 0;
            }
            tiles |= tile;
        }
        words[base + (cell >> 6)] |= 1L << (cell & 63);
    }
    boolean contains(int position) {
        long tile = 1L << Integer.compress(position, tileMask);
        int cell = Integer.compress(position, cellMask);
        if ((tiles & tile) == 0) return false;
        int base = Long.bitCount(tiles & (tile - 1)) * wordsPerTile;
        return (words[base + (cell >> 6)] & (1L << (cell & 63))) != 0;
    }
    public void reset() {
        if (tiles != 0) Arrays.fill(words, 0);
        tiles = 0;
    }
    public int bytes() { return 8 + Long.bitCount(tiles) * wordsPerTile * 8; }
    public boolean isEmpty() { return tiles == 0; }
    public void write(long target, boolean asLongs) {
        target = writeWord(target, tiles, asLongs);
        int count = Long.bitCount(tiles) * wordsPerTile;
        for (int index = 0; index < count; index++) target = writeWord(target, words[index], asLongs);
    }
    private static long writeWord(long target, long value, boolean asLongs) {
        if (asLongs) MemoryUtil.memPutLong(target, value);
        else {
            MemoryUtil.memPutInt(target, (int) (value >>> 32));
            MemoryUtil.memPutInt(target + 4, (int) value);
        }
        return target + 8;
    }
}
