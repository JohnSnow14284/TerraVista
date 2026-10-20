package dev.terravista.render;

/** 定义设备缓冲的大小与资源所有权操作。 */
public interface DeviceBuffer {
    long sizeBytes();
    void free();
}
