package dev.terravista.mixin.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 访问宿主编码器当前记录的命令缓冲。 */
@Mixin(VulkanCommandEncoder.class)
public interface RecordingCommandAccess {
    @Accessor("currentCommandBuffer")
    VkCommandBuffer terravista$recordingCommand();
}
