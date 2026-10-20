package dev.terravista.memory;

import java.lang.ref.Cleaner;

/** 先释放再分配线程暂存内存，并预留 1024 字节。 */
public final class ResizableNativeScratch {
    private final ThreadLocal<ScratchBinding> buffers;
    public ResizableNativeScratch(long bytes) { buffers = ThreadLocal.withInitial(() -> create(bytes)); }
    private static ScratchBinding create(long bytes) {
        var allocation = new NativeBuffer(bytes);
        var view = NativeBuffer.borrow(allocation.address, allocation.bytes);
        return new ScratchBinding(NativeCleaner.INSTANCE.register(view, allocation::free), view);
    }
    public NativeBuffer get() { return buffers.get().view(); }
    public NativeBuffer get(long minimum) {
        var binding = buffers.get();
        if (binding.view().bytes < minimum) {
            binding.release().clean();
            binding = create(minimum + 1024);
            buffers.set(binding);
        }
        return binding.view();
    }
}

record ScratchBinding(Cleaner.Cleanable release, NativeBuffer view) {}
