package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.vulkan.*;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 借用宿主设备并持有命令池与设备范围缓存。 */
public final class VulkanDevice {
    public final VkInstance instance;
    public final VkPhysicalDevice physicalDevice;
    public final VkDevice device;
    public final VkQueue queue;
    public final int queueFamily;
    public final boolean indirectCount;
    public final DeviceCapabilities capabilities;
    public final String name;
    public final boolean sampleMaskDiscard;
    public final long commandPool;
    public final DeviceMemoryTypes memory;
    final Map<PipelineBindings.LayoutKey, Long> descriptorLayouts = new HashMap<>();
    final Map<Integer, Long> samplers = new HashMap<>();
    private long pipelineCache;
    private long storageAlignment = -1;
    private long uniformAlignment = -1;

    public static VulkanDevice adopt(MinecraftDeviceAccess host) { return new VulkanDevice(host); }
    private VulkanDevice(MinecraftDeviceAccess host) {
        instance = host.instance();
        physicalDevice = host.physicalDevice();
        device = host.device();
        queue = host.graphicsQueue();
        queueFamily = host.graphicsQueueFamily();
        indirectCount = host.drawIndirectCountEnabled();
        capabilities = DeviceCapabilities.query(physicalDevice);
        String deviceName;
        int vendor;
        try (var stack = stackPush()) {
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, properties);
            deviceName = properties.deviceNameString();
            vendor = properties.vendorID();
            var create = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT).queueFamilyIndex(queueFamily);
            var handle = stack.mallocLong(1);
            check(vkCreateCommandPool(device, create, null, handle), "vkCreateCommandPool(adopted)");
            commandPool = handle.get(0);
        }
        sampleMaskDiscard = DeviceCapabilities.sampleMaskDiscard(vendor);
        name = deviceName + " (MC host)";
        memory = new DeviceMemoryTypes(physicalDevice);
        ModLog.info("Render: TerraVista Vulkan context adopted Minecraft device: " + name
                + " (drawIndirectCount=" + indirectCount + ", sampleMaskDiscard=" + sampleMaskDiscard
                + ", subgroupArithmetic=" + capabilities.arithmetic
                + " (deviceCapable=" + capabilities.arithmeticSupported + ", gate=false)"
                + ", subgroupSize=" + capabilities.subgroupSize + ")");
    }
    public long pipelineCache() {
        if (pipelineCache == VK_NULL_HANDLE) {
            try (var stack = stackPush()) {
                var create = VkPipelineCacheCreateInfo.calloc(stack).sType$Default();
                var handle = stack.mallocLong(1);
                check(vkCreatePipelineCache(device, create, null, handle), "vkCreatePipelineCache");
                pipelineCache = handle.get(0);
            }
        }
        return pipelineCache;
    }
    public long storageAlignment() {
        if (storageAlignment == -1) {
            try (var stack = stackPush()) {
                var properties = VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(physicalDevice, properties);
                storageAlignment = properties.limits().minStorageBufferOffsetAlignment();
            }
        }
        return storageAlignment;
    }
    public long uniformAlignment() {
        if (uniformAlignment == -1) {
            try (var stack = stackPush()) {
                var properties = VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(physicalDevice, properties);
                uniformAlignment = properties.limits().minUniformBufferOffsetAlignment();
            }
        }
        return uniformAlignment;
    }
    public void close() {
        vkDeviceWaitIdle(device);
        if (pipelineCache != VK_NULL_HANDLE) {
            vkDestroyPipelineCache(device, pipelineCache, null);
            pipelineCache = VK_NULL_HANDLE;
        }
        descriptorLayouts.values().forEach(layout -> vkDestroyDescriptorSetLayout(device, layout, null));
        descriptorLayouts.clear();
        samplers.values().forEach(sampler -> vkDestroySampler(device, sampler, null));
        samplers.clear();
        vkDestroyCommandPool(device, commandPool, null);
        capabilities.close();
        memory.close();
    }
}
