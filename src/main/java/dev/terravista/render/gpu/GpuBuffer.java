package dev.terravista.render.gpu;

import dev.terravista.memory.TrackedResource;
import dev.terravista.render.TerrainDrawList;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 管理 Vulkan 独立缓冲分配并延迟原生销毁至帧完成。 */
public class GpuBuffer extends TrackedResource implements dev.terravista.render.DeviceBuffer, TerrainDrawList {
    public static final int DEFAULT_USAGE = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
            | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
            | VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    private final FrameCommands commands;
    public final long buffer;
    public final long memory;
    private final long bytes;
    private static int count;
    private static long totalBytes;

    public GpuBuffer(FrameCommands commands, long bytes) { this(commands, bytes, DEFAULT_USAGE, false); }
    public GpuBuffer(FrameCommands commands, long bytes, int usage, boolean hostVisible) {
        this.commands = commands;
        this.bytes = bytes;
        var device = commands.device();
        long created = 0;
        long allocated = 0;
        try (var stack = stackPush()) {
            var create = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(bytes).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            var handle = stack.mallocLong(1);
            check(vkCreateBuffer(device.device, create, null, handle), "vkCreateBuffer");
            created = handle.get(0);
            var requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device.device, created, requirements);
            allocated = BufferMemory.allocate(device, requirements, usage, hostVisible);
            check(vkBindBufferMemory(device.device, created, allocated, 0), "vkBindBufferMemory");
        } catch (RuntimeException | Error failure) {
            if (created != 0) vkDestroyBuffer(device.device, created, null);
            if (allocated != 0) vkFreeMemory(device.device, allocated, null);
            markReleased();
            throw failure;
        }
        buffer = created;
        memory = allocated;
        count++;
        totalBytes += bytes;
    }
    public long map() {
        try (var stack = stackPush()) {
            var pointer = stack.mallocPointer(1);
            check(vkMapMemory(commands.device().device, memory, 0, bytes, 0, pointer), "vkMapMemory");
            return pointer.get(0);
        }
    }
    @Override public long sizeBytes() { return bytes; }
    public long size() { return bytes; }
    public GpuBuffer zeroRange(long offset, long length) {
        commands.fillBuffer(buffer, offset, length, 0);
        return this;
    }
    public GpuBuffer zero() { return zeroRange(0, VK_WHOLE_SIZE); }
    public GpuBuffer fill(int value) {
        commands.fillBuffer(buffer, 0, VK_WHOLE_SIZE, value);
        return this;
    }
    @Override public void free() {
        markReleased();
        count--;
        totalBytes -= bytes;
        commands.retirement().deferBuffer(buffer, memory);
    }
    public static int count() { return count; }
    public static long totalBytes() { return totalBytes; }
}
