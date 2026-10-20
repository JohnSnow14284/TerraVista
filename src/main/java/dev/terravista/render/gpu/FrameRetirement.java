package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkEventCreateInfo;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 通过事件确认帧完成并回收资源，兼容尚未提交的借用帧。 */
public final class FrameRetirement {
    public interface Listener { void retired(long inclusiveFrame); }
    private record Flight(long frame, long event) {}
    private record Resource(long frame, long buffer, long image, long view, long memory) {}
    private record Pipeline(long frame, long pipeline, long layout) {}

    private final VulkanDevice device;
    private long current;
    private long retired = -1;
    private final Deque<Flight> flights = new ArrayDeque<>();
    private final ArrayList<Long> events = new ArrayList<>();
    private final ArrayList<Resource> resources = new ArrayList<>();
    private final ArrayList<Pipeline> pipelines = new ArrayList<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();

    FrameRetirement(VulkanDevice device) { this.device = device; }
    public long currentFrame() { return current; }
    public void addListener(Listener listener) { listeners.add(listener); }

    void stamp(VkCommandBuffer command) {
        long event = obtainEvent();
        vkCmdSetEvent(command, event, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
        flights.add(new Flight(current, event));
        current++;
    }

    public void poll() {
        boolean any = false;
        while (!flights.isEmpty()) {
            var frame = flights.peek();
            int status = vkGetEventStatus(device.device, frame.event);
            if (status == VK_EVENT_RESET) break;
            if (status != VK_EVENT_SET) check(status, "vkGetEventStatus");
            flights.pop();
            check(vkResetEvent(device.device, frame.event), "vkResetEvent");
            events.add(frame.event);
            retired = frame.frame;
            any = true;
        }
        if (any) runRetirement();
    }

    boolean hasFlights() { return !flights.isEmpty(); }
    void retireIdle() {
        retired = current;
        runRetirement();
    }

    private void runRetirement() {
        for (var listener : listeners) listener.retired(retired);
        resources.removeIf(resource -> {
            if (resource.frame <= retired) {
                if (resource.buffer != VK_NULL_HANDLE) vkDestroyBuffer(device.device, resource.buffer, null);
                if (resource.view != VK_NULL_HANDLE) vkDestroyImageView(device.device, resource.view, null);
                if (resource.image != VK_NULL_HANDLE) vkDestroyImage(device.device, resource.image, null);
                if (resource.memory != VK_NULL_HANDLE) vkFreeMemory(device.device, resource.memory, null);
                return true;
            }
            return false;
        });
        var iterator = pipelines.iterator();
        while (iterator.hasNext()) {
            var pipeline = iterator.next();
            if (pipeline.frame <= retired) {
                vkDestroyPipeline(device.device, pipeline.pipeline, null);
                vkDestroyPipelineLayout(device.device, pipeline.layout, null);
                iterator.remove();
            }
        }
    }

    private long obtainEvent() {
        if (!events.isEmpty()) return events.remove(events.size() - 1);
        try (var stack = stackPush()) {
            var create = VkEventCreateInfo.calloc(stack).sType$Default();
            var handle = stack.mallocLong(1);
            check(vkCreateEvent(device.device, create, null, handle), "vkCreateEvent");
            return handle.get(0);
        }
    }

    public void deferBuffer(long buffer, long memory) {
        resources.add(new Resource(current, buffer, VK_NULL_HANDLE, VK_NULL_HANDLE, memory));
    }
    public void deferImage(long image, long view, long memory) {
        resources.add(new Resource(current, VK_NULL_HANDLE, image, view, memory));
    }
    public void deferPipeline(long pipeline, long layout) {
        pipelines.add(new Pipeline(current, pipeline, layout));
    }

    void closePool() {
        for (long event : events) vkDestroyEvent(device.device, event, null);
        events.clear();
        int pending = resources.size() + pipelines.size();
        if (pending != 0) ModLog.warn("Render: FrameCommands freed with " + pending + " pending destroys remaining");
    }
}
