package dev.terravista.render;

import dev.terravista.memory.NativeBuffer;
import java.util.function.Consumer;

/** 在渲染线程记录读回，借用结果内存在回调期间有效。 */
public abstract class ReadbackStream {
    public interface ResultConsumer { void consume(long pointer, long bytes); }
    public void download(DeviceBuffer buffer, ResultConsumer consumer) { download(buffer, 0, buffer.sizeBytes(), consumer); }
    public void download(DeviceBuffer buffer, Consumer<NativeBuffer> consumer) { download(buffer, 0, buffer.sizeBytes(), consumer); }
    public void download(DeviceBuffer buffer, long offset, long bytes, Consumer<NativeBuffer> consumer) {
        download(buffer, offset, bytes, (pointer, returnedBytes) -> consumer.accept(NativeBuffer.borrow(pointer, bytes)));
    }
    public abstract void download(DeviceBuffer buffer, long offset, long bytes, ResultConsumer consumer);
    public abstract void commit();
    public abstract void tick();
    public abstract void waitDiscard();
    public abstract void flushWaitClear();
    public abstract void free();

    private static ReadbackStream current;
    public static ReadbackStream current() {
        var stream = current;
        if (stream == null) throw new IllegalStateException("TerraVista Render: Vulkan stream not initialized");
        return stream;
    }
    public static void register(ReadbackStream stream) {
        if (current != null) throw new IllegalStateException("TerraVista Render: Download stream already initialized");
        current = stream;
    }
    public static void clear() { current = null; }
}
