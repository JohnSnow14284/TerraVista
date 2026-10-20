package dev.terravista.render.gpu;

import org.lwjgl.vulkan.*;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 管理渲染线程记录目标与私有命令的同步即时提交。 */
public final class FrameCommands {
    private final VulkanDevice device;
    private final FrameRetirement retirement;
    private VkCommandBuffer borrowed;
    private VkCommandBuffer immediate;
    private VkCommandBuffer reusable;
    private VkCommandBuffer submitted;
    private long fence;
    private boolean work;

    public FrameCommands(VulkanDevice device) {
        this.device = device;
        retirement = new FrameRetirement(device);
    }
    public VulkanDevice device() { return device; }
    public FrameRetirement retirement() { return retirement; }

    public void beginFrame(VkCommandBuffer command) {
        if (borrowed != null) throw new IllegalStateException("TerraVista Render: Frame already begun");
        borrowed = command;
    }
    public void endFrame() {
        if (borrowed == null) throw new IllegalStateException("TerraVista Render: No frame begun");
        if (work) {
            retirement.stamp(borrowed);
            work = false;
        }
        borrowed = null;
    }

    public VkCommandBuffer command() {
        work = true;
        if (borrowed != null) return borrowed;
        if (immediate == null) {
            finishImmediate();
            try (var stack = stackPush()) {
                var command = reusable;
                reusable = null;
                if (command == null) {
                    var allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                            .commandPool(device.commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
                    var pointer = stack.mallocPointer(1);
                    check(vkAllocateCommandBuffers(device.device, allocate, pointer), "vkAllocateCommandBuffers(immediate)");
                    command = new VkCommandBuffer(pointer.get(0), device.device);
                }
                try {
                    // 使用命令池的重置能力复用已完成的私有缓冲。
                    var begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                    check(vkBeginCommandBuffer(command, begin), "vkBeginCommandBuffer(immediate)");
                    immediate = command;
                } finally {
                    if (immediate == null) vkFreeCommandBuffers(device.device, device.commandPool, command);
                }
            }
        }
        return immediate;
    }

    public void flushImmediate() {
        finishImmediate();
        if (immediate == null) return;
        var command = immediate;
        immediate = null;
        try (var stack = stackPush()) {
            check(vkEndCommandBuffer(command), "vkEndCommandBuffer(immediate)");
            if (fence == VK_NULL_HANDLE) {
                var create = VkFenceCreateInfo.calloc(stack).sType$Default();
                var handle = stack.mallocLong(1);
                check(vkCreateFence(device.device, create, null, handle), "vkCreateFence(immediate)");
                fence = handle.get(0);
            } else check(vkResetFences(device.device, fence), "vkResetFences(immediate)");
            var submit = VkSubmitInfo.calloc(stack).sType$Default().pCommandBuffers(stack.pointers(command));
            check(vkQueueSubmit(device.queue, submit, fence), "vkQueueSubmit(immediate)");
            submitted = command;
        } finally {
            if (submitted == null) vkFreeCommandBuffers(device.device, device.commandPool, command);
        }
        finishImmediate();
    }
    private void finishImmediate() {
        if (submitted == null) return;
        // 等待失败时保留两个句柄，等待后续完成确认。
        check(vkWaitForFences(device.device, fence, true, Long.MAX_VALUE), "vkWaitForFences(immediate)");
        reusable = submitted;
        submitted = null;
    }

    public void waitIdleAndRetire() {
        flushImmediate();
        check(vkDeviceWaitIdle(device.device), "vkDeviceWaitIdle");
        retirement.poll();
        if (borrowed == null && !retirement.hasFlights()) retirement.retireIdle();
    }
    public void fillBuffer(long buffer, long offset, long size, int value) {
        vkCmdFillBuffer(command(), buffer, offset, size, value);
        barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
    }
    public void barrier(int sourceStage, int sourceAccess, int destinationStage, int destinationAccess) {
        try (var stack = stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                    .srcAccessMask(sourceAccess).dstAccessMask(destinationAccess);
            vkCmdPipelineBarrier(command(), sourceStage, destinationStage, 0, barrier, null, null);
        }
    }
    public void computeToCompute() {
        barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
    }
    public void computeToDraw() {
        barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                VK_ACCESS_INDIRECT_COMMAND_READ_BIT | VK_ACCESS_SHADER_READ_BIT);
    }
    public void computeToTransfer() {
        barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_READ_BIT);
    }
    public void close() {
        waitIdleAndRetire();
        if (reusable != null) {
            vkFreeCommandBuffers(device.device, device.commandPool, reusable);
            reusable = null;
        }
        if (fence != VK_NULL_HANDLE) {
            vkDestroyFence(device.device, fence, null);
            fence = VK_NULL_HANDLE;
        }
        retirement.closePool();
    }
}
