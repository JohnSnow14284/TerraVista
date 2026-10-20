package dev.terravista.render;

import dev.terravista.memory.NativeBuffer;

/** 定义 CPU 暂存写入、显式提交与帧回收操作。 */
public abstract class UploadStream {
    public void upload(DeviceBuffer buffer, long offset, NativeBuffer data) {
        data.copyTo(upload(buffer, offset, data.bytes));
    }
    public long uploadTo(DeviceBuffer buffer) { return upload(buffer, 0, buffer.sizeBytes()); }
    public abstract long upload(DeviceBuffer buffer, long offset, long bytes);
    public long rawUpload(int bytes) {
        long offset = rawUploadAddress(bytes);
        return baseAddress() + offset;
    }
    public abstract long rawUploadAddress(int bytes);
    public abstract void commit();
    public abstract void tick();
    public abstract long baseAddress();
    public abstract int baseAlignment();
    public final int alignAllocation(int value) {
        int alignment = baseAlignment();
        return ((value + alignment - 1) / alignment) * alignment;
    }
    public abstract void free();
    private static UploadStream current;
    public static UploadStream current() {
        var stream = current;
        if (stream == null) throw new IllegalStateException("TerraVista Render: Vulkan stream not initialized");
        return stream;
    }
    public static void register(UploadStream stream) {
        if (current != null) throw new IllegalStateException("TerraVista Render: Upload stream already initialized");
        current = stream;
    }
    public static void clear() { current = null; }
    public static long alignUp(long value, long alignment) { return ((value + alignment - 1) / alignment) * alignment; }
    public static int alignUp(int value, int alignment) { return ((value + alignment - 1) / alignment) * alignment; }
}
