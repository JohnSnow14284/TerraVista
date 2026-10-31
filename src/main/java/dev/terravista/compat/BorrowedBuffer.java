package dev.terravista.compat;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import java.util.function.BooleanSupplier;

/** A host API view of an existing native buffer. Only TerraVista's owner may free the allocation. */
final class BorrowedBuffer extends VulkanGpuBuffer {
    private final BooleanSupplier released;
    private boolean closed;

    BorrowedBuffer(long handle, int usage, long bytes, BooleanSupplier released) {
        super(handle, usage, bytes);
        if (handle == 0 || bytes <= 0) throw new IllegalArgumentException("Invalid borrowed Vulkan buffer");
        this.released = released;
    }

    @Override public boolean isClosed() { return closed || released.getAsBoolean(); }

    @Override public void checkCanBeUsed() {
        if (isClosed()) throw new IllegalStateException("TerraVista: borrowed buffer owner has closed");
    }

    @Override public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        throw new UnsupportedOperationException("TerraVista: borrowed buffers are bound without mapping");
    }

    @Override public void close() { closed = true; }
    @Override public void destroy() { closed = true; }
}
