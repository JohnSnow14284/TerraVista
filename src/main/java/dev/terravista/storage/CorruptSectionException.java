package dev.terravista.storage;

/** 标识无法安全解码的区段字节。 */
public final class CorruptSectionException extends RuntimeException {
    public CorruptSectionException(String message) { super(message); }
    public CorruptSectionException(String message, Throwable cause) { super(message, cause); }
}
