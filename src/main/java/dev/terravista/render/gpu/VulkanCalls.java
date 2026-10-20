package dev.terravista.render.gpu;

import org.lwjgl.vulkan.VK10;

/** 保留 Vulkan 返回码供内存重试与调用失败处理。 */
public final class VulkanCalls {
    private VulkanCalls() {}
    public static int check(int result, String operation) {
        if (result != VK10.VK_SUCCESS) throw new Failure(result, operation);
        return result;
    }
    public static final class Failure extends IllegalStateException {
        private final int result;
        private Failure(int result, String operation) {
            super("TerraVista Vulkan call failed: " + operation + " -> " + result);
            this.result = result;
        }
        public boolean outOfMemory() {
            return result == VK10.VK_ERROR_OUT_OF_HOST_MEMORY || result == VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY;
        }
    }
}
