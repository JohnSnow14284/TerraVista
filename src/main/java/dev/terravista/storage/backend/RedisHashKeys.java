package dev.terravista.storage.backend;

/** 使用固定宽度大端序字节编码哈希字段。 */
final class RedisHashKeys {
    private RedisHashKeys() {}
    static byte[] section(long key) {
        byte[] bytes = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) { bytes[i] = (byte) (key & 0xFF); key >>= Byte.SIZE; }
        return bytes;
    }
    static long section(byte[] bytes) {
        long key = 0;
        for (int i = 0; i < Long.BYTES; i++) { key <<= Byte.SIZE; key |= bytes[i] & 0xFF; }
        return key;
    }
    static byte[] palette(int id) { return new byte[]{(byte) (id >> 24), (byte) (id >> 16), (byte) (id >> 8), (byte) id}; }
    static int palette(byte[] bytes) {
        return (Byte.toUnsignedInt(bytes[0]) << 24) | (Byte.toUnsignedInt(bytes[1]) << 16) | (Byte.toUnsignedInt(bytes[2]) << 8) | Byte.toUnsignedInt(bytes[3]);
    }
}
