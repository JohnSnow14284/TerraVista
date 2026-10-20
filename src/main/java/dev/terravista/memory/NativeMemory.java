package dev.terravista.memory;

import sun.misc.Unsafe;

/** 通过 Unsafe 传输原生标量与数组。 */
public final class NativeMemory {
    private static final Unsafe ACCESS = acquire();
    private static final long BYTES = ACCESS.arrayBaseOffset(byte[].class);
    private static final long SHORTS = ACCESS.arrayBaseOffset(short[].class);
    private static final long INTS = ACCESS.arrayBaseOffset(int[].class);
    private static final long LONGS = ACCESS.arrayBaseOffset(long[].class);

    private static Unsafe acquire() {
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public static void copy(long source, long target, long bytes) {
        ACCESS.copyMemory(source, target, bytes);
    }
    public static void copy(long source, byte[] target) {
        copy(source, target.length, target, 0);
    }
    public static void copy(long source, int bytes, byte[] target) {
        copy(source, bytes, target, 0);
    }
    public static void copy(long source, int bytes, byte[] target, int offset) {
        ACCESS.copyMemory(null, source, target, BYTES + offset, bytes);
    }
    public static void copy(byte[] source, long target) {
        copy(source, source.length, target);
    }
    public static void copy(byte[] source, int bytes, long target) {
        ACCESS.copyMemory(source, BYTES, null, target, bytes);
    }
    public static void copy(short[] source, long target) {
        ACCESS.copyMemory(source, SHORTS, null, target, (long) source.length << 1);
    }
    public static void copy(int[] source, long target) {
        copy(source, source.length, target);
    }
    public static void copy(int[] source, int count, long target) {
        ACCESS.copyMemory(source, INTS, null, target, (long) count << 2);
    }
    public static void copy(long[] source, long target) {
        ACCESS.copyMemory(source, LONGS, null, target, (long) source.length << 3);
    }

    public static boolean readBoolean(long address) { return ACCESS.getByte(null, address) != 0; }
    public static byte readByte(long address) { return ACCESS.getByte(null, address); }
    public static short readShort(long address) { return ACCESS.getShort(null, address); }
    public static int readInt(long address) { return ACCESS.getInt(null, address); }
    public static long readLong(long address) { return ACCESS.getLong(null, address); }
    public static float readFloat(long address) { return ACCESS.getFloat(null, address); }
    public static double readDouble(long address) { return ACCESS.getDouble(null, address); }
    public static void writeByte(long address, byte value) { ACCESS.putByte(null, address, value); }
    public static void writeShort(long address, short value) { ACCESS.putShort(null, address, value); }
    public static void writeInt(long address, int value) { ACCESS.putInt(null, address, value); }
    public static void writeLong(long address, long value) { ACCESS.putLong(null, address, value); }
    public static void writeFloat(long address, float value) { ACCESS.putFloat(null, address, value); }
    public static void writeDouble(long address, double value) { ACCESS.putDouble(null, address, value); }
}
