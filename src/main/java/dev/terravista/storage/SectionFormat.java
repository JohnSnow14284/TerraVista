package dev.terravista.storage;

/** 定义持久化与压缩暂存共享的序列化区块容量。 */
public final class SectionFormat {
    public static final int MAX_SERIALIZED_BYTES = 32 * 32 * 32 * 8 * 2 + 8;
    private SectionFormat() {}
}
