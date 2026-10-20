package dev.terravista.render.gpu;

import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;

/** 按需读取固定设备内存属性并选择首个优先或回退类型。 */
public final class DeviceMemoryTypes {
    private final VkPhysicalDevice physicalDevice;
    private VkPhysicalDeviceMemoryProperties properties;

    DeviceMemoryTypes(VkPhysicalDevice physicalDevice) { this.physicalDevice = physicalDevice; }
    public int find(int typeBits, int required) { return find(typeBits, required, 0); }
    public int find(int typeBits, int required, int preferred) {
        if (properties == null) {
            properties = VkPhysicalDeviceMemoryProperties.malloc();
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, properties);
        }
        int fallback = -1;
        for (int index = 0; index < properties.memoryTypeCount(); index++) {
            if ((typeBits & (1 << index)) == 0) continue;
            int flags = properties.memoryTypes(index).propertyFlags();
            if ((flags & required) != required) continue;
            if (fallback == -1) fallback = index;
            if ((flags & preferred) == preferred) return index;
        }
        if (fallback != -1) return fallback;
        throw new IllegalStateException("TerraVista Render: No suitable VK memory type");
    }
    void close() {
        if (properties != null) { properties.free(); properties = null; }
    }
}
