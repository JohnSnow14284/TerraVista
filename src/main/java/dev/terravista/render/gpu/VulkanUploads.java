package dev.terravista.render.gpu;

import dev.terravista.render.DeviceBuffer;
import dev.terravista.render.UploadStream;

/** 连接上传暂存范围、显式拷贝发布与帧所有权。 */
public class VulkanUploads extends UploadStream {
    private final FrameCommands commands;
    private final UploadCopies copies = new UploadCopies();
    private final UploadStaging staging;

    public VulkanUploads(FrameCommands commands, long bytes) {
        this.commands = commands;
        staging = new UploadStaging(commands, bytes);
        commands.retirement().addListener(this::retired);
    }
    public long stagingBuffer() { return staging.handle(); }
    @Override public long upload(DeviceBuffer target, long destinationOffset, long bytes) {
        long address = rawUploadAddress((int) bytes);
        copies.add((GpuBuffer) target, address, destinationOffset, bytes);
        return staging.pointer() + address;
    }
    @Override public long rawUploadAddress(int bytes) {
        if (bytes < 0) throw new IllegalStateException("TerraVista Render: Negative size");
        return staging.allocateAligned(alignAllocation(bytes), this::tick);
    }
    @Override public void commit() {
        if (copies.isEmpty()) { staging.resetRange(); return; }
        copies.record(commands, staging.handle());
        staging.resetRange();
    }
    @Override public void tick() { commit(); staging.publishAllocations(); }
    private void retired(long inclusiveFrame) { staging.retire(inclusiveFrame); }
    @Override public long baseAddress() { return staging.pointer(); }
    @Override public int baseAlignment() { return staging.alignment; }
    @Override public void free() { staging.free(); }
}
