package dev.terravista.render.gpu;

import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import dev.terravista.render.DepthConvention;
import net.minecraft.client.Minecraft;
import org.lwjgl.vulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.vkCmdDrawIndexedIndirectCount;

/** 连接地形命令生成、三类绘制及资源生命周期。 */
public final class TerrainDraws {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final GpuGeometryTable geometry;
    private final GpuModelTable models;
    private final TerrainScene scene = new TerrainScene();
    private final TerrainBuffers buffers;
    private final TerrainPipelines pipelines;
    private final TerrainCommands generation;
    private final TerrainMaterials materials;

    public TerrainDraws(FrameCommands commands, VulkanUploads uploads, VulkanReadbacks readbacks,
                        DepthConvention convention, GpuGeometryTable geometry, GpuModelTable models) {
        this.commands = commands;
        this.uploads = uploads;
        this.geometry = geometry;
        this.models = models;
        buffers = new TerrainBuffers(commands, uploads);
        try {
            pipelines = new TerrainPipelines(commands, convention);
        } catch (RuntimeException | Error failure) {
            buffers.close();
            throw failure;
        }
        generation = new TerrainCommands(commands, readbacks, geometry, scene, buffers, pipelines);
        materials = new TerrainMaterials(commands, convention);
    }

    public void publish(VulkanViewTargets view) { scene.publish(uploads, view); }
    public void build(VulkanViewTargets view) { generation.record(view); }

    public void opaque(VulkanViewTargets view, boolean clearTargets) {
        materials.ensure(view);
        if (geometry.size() == 0) return;
        int capacity = Math.min(geometry.size() * 7, VulkanViewTargets.OPAQUE_CAPACITY);
        draw(view, view.attachments.colour.view, materials.opaque, 0, 12, generation.opaqueBudget(capacity));
    }

    public void temporal(VulkanViewTargets view) {
        materials.ensure(view);
        if (geometry.size() == 0) return;
        int capacity = Math.min(geometry.size() * 7, VulkanViewTargets.TEMPORAL_CAPACITY);
        draw(view, view.attachments.colour.view, materials.opaque,
                TerrainPipelines.TEMPORAL_OFFSET * 20L, 20, generation.temporalBudget(capacity));
    }

    public void translucent(VulkanViewTargets view) {
        materials.ensure(view);
        if (geometry.size() == 0) return;
        int capacity = Math.min(geometry.size(), VulkanViewTargets.TRANSLUCENT_CAPACITY);
        draw(view, view.attachments.shadedColour.view, materials.translucent,
                TerrainPipelines.TRANSLUCENT_OFFSET * 20L, 16, generation.translucentBudget(capacity));
    }

    private void draw(VulkanViewTargets view, long colour, DevicePipeline pipeline,
                      long indirectOffset, long countOffset, int maximum) {
        var command = commands.command();
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_VERTEX_SHADER_BIT
                        | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                VK_ACCESS_INDIRECT_COMMAND_READ_BIT | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_INDEX_READ_BIT);
        begin(command, view, colour);
        pipeline.bind(command);
        RenderCommands.viewport(command, view.state.width, view.state.height);
        long lightmap = ((VulkanGpuTextureView) Minecraft.getInstance().gameRenderer.levelLightmap()).vkImageView();
        try (var bindings = pipeline.descriptors()) {
            scene.bind(bindings).storage(1, geometry.geometryBuffer()).storage(3, models.models)
                    .storage(4, models.colours).storage(5, view.positionScratch)
                    .sampler(8, models.atlas.view, models.sampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .sampler(9, lightmap, buffers.lightSampler, VK_IMAGE_LAYOUT_GENERAL)
                    .sampler(10, view.attachments.depthBoundSampleView, buffers.depthSampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .push(command);
        }
        vkCmdBindIndexBuffer(command, buffers.indices.buffer, 0, VK_INDEX_TYPE_UINT32);
        if (commands.device().indirectCount) {
            vkCmdDrawIndexedIndirectCount(command, view.drawCalls.buffer, indirectOffset,
                    view.drawCounts.buffer, countOffset, maximum, 20);
        } else {
            vkCmdDrawIndexedIndirect(command, view.drawCalls.buffer, indirectOffset, maximum, 20);
        }
        vkCmdEndRenderingKHR(command);
    }

    private static void begin(VkCommandBuffer command, VulkanViewTargets view, long colour) {
        try (var stack = stackPush()) {
            var depth = VkRenderingAttachmentInfoKHR.calloc(stack).sType$Default()
                    .imageView(view.attachments.depthStencil.view).imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            var stencil = VkRenderingAttachmentInfoKHR.calloc(stack).sType$Default()
                    .imageView(view.attachments.depthStencil.view).imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            var area = VkRect2D.calloc(stack);
            area.extent().set(view.state.width, view.state.height);
            var info = VkRenderingInfoKHR.calloc(stack).sType$Default().renderArea(area).layerCount(1)
                    .pDepthAttachment(depth).pStencilAttachment(stencil);
            if (colour != 0) {
                var attachment = VkRenderingAttachmentInfoKHR.calloc(1, stack).sType$Default()
                        .imageView(colour).imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                info.pColorAttachments(attachment);
            }
            vkCmdBeginRenderingKHR(command, info);
        }
    }

    public void close() {
        generation.close();
        pipelines.close();
        materials.close();
        buffers.close();
    }
}
