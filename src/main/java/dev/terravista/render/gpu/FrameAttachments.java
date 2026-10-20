package dev.terravista.render.gpu;

import com.mojang.renderpearl.api.textures.GpuTextureView;

/** 携带当前帧的宿主附件、尺寸及远景视口。 */
public record FrameAttachments(VulkanViewTargets view, GpuTextureView colour, GpuTextureView depth,
                               int width, int height) {}
