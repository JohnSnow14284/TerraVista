package dev.terravista.render.gpu;

import java.util.Locale;
import org.lwjgl.vulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/** 保存子组属性副本并独立判断各项设备特性。 */
public final class DeviceCapabilities {
    private static final boolean ENABLE_SUBGROUP_PATHS = false;
    private VkPhysicalDeviceSubgroupProperties properties;
    public final int subgroupSize;
    public final boolean arithmeticSupported;
    public final boolean arithmetic;
    public final boolean queueOperations;

    DeviceCapabilities(VkPhysicalDeviceSubgroupProperties properties) {
        this.properties = properties;
        subgroupSize = properties != null ? properties.subgroupSize() : 1;
        int operations = properties != null ? properties.supportedOperations() : 0;
        int stages = properties != null ? properties.supportedStages() : 0;
        int arithmeticOperations = VK_SUBGROUP_FEATURE_ARITHMETIC_BIT | VK_SUBGROUP_FEATURE_BASIC_BIT | VK_SUBGROUP_FEATURE_CLUSTERED_BIT;
        arithmeticSupported = (operations & arithmeticOperations) == arithmeticOperations
                && (stages & VK_SHADER_STAGE_COMPUTE_BIT) != 0 && subgroupSize >= 16;
        arithmetic = ENABLE_SUBGROUP_PATHS && arithmeticSupported;
        int queue = VK_SUBGROUP_FEATURE_BASIC_BIT | VK_SUBGROUP_FEATURE_ARITHMETIC_BIT | VK_SUBGROUP_FEATURE_BALLOT_BIT;
        queueOperations = (operations & queue) == queue && (stages & VK_SHADER_STAGE_COMPUTE_BIT) != 0;
    }
    static DeviceCapabilities query(VkPhysicalDevice physicalDevice) {
        try (var stack = stackPush()) {
            var subgroup = VkPhysicalDeviceSubgroupProperties.calloc(stack).sType$Default();
            var properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(subgroup.address());
            VK11.vkGetPhysicalDeviceProperties2(physicalDevice, properties);
            var retained = VkPhysicalDeviceSubgroupProperties.malloc();
            retained.set(subgroup);
            return new DeviceCapabilities(retained);
        }
    }
    static boolean sampleMaskDiscard(int vendor) {
        boolean macOS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        return macOS && vendor != 0x106B;
    }
    void close() {
        if (properties != null) { properties.free(); properties = null; }
    }
}
