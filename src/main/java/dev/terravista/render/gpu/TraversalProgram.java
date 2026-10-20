package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.render.DepthConvention;
import java.util.List;

/** 管理遍历着色器变体及子组失败回退。 */
final class TraversalProgram {
    static final int WORKGROUP_BITS = 6;
    static final int HIZ = 0, SCENE = 1, REQUESTS = 2, RENDERS = 3, NODES = 4,
            METADATA = 5, SOURCE = 6, SINK = 7, VISIBILITY = 8;
    private final FrameCommands commands;
    private final DepthConvention convention;
    private final DevicePipeline ordinary;
    private DevicePipeline subgroup;
    private boolean subgroupFailed;

    TraversalProgram(FrameCommands commands, DepthConvention convention) {
        this.commands = commands;
        this.convention = convention;
        ordinary = create(false);
    }

    private DevicePipeline create(boolean useSubgroups) {
        String source = ShaderVariants.load(ShaderProgram.TREE_VISIT, ShaderVariants.defines()
                .depth(convention).when("SUBGROUP_QUEUE", useSubgroups)
                .define("MAX_ITERATIONS", TraversalQueues.ITERATIONS).define("LOCAL_SIZE_BITS", WORKGROUP_BITS)
                .define("MAX_REQUEST_QUEUE_SIZE", TraversalQueues.REQUEST_CAPACITY)
                .define("HIZ_BINDING", HIZ).define("SCENE_UNIFORM_BINDING", SCENE)
                .define("REQUEST_QUEUE_BINDING", REQUESTS).define("RENDER_QUEUE_BINDING", RENDERS)
                .define("NODE_DATA_BINDING", NODES).define("NODE_QUEUE_INDEX_BINDING", 0)
                .define("NODE_QUEUE_META_BINDING", METADATA).define("NODE_QUEUE_SOURCE_BINDING", SOURCE)
                .define("NODE_QUEUE_SINK_BINDING", SINK).define("RENDER_TRACKER_BINDING", VISIBILITY).build());
        if (useSubgroups) {
            source = source.replace("#version 460 core", "#version 460 core\n"
                    + "#extension GL_KHR_shader_subgroup_basic : require\n"
                    + "#extension GL_KHR_shader_subgroup_arithmetic : require\n"
                    + "#extension GL_KHR_shader_subgroup_ballot : require\n");
        }
        return ComputePipeline.create(commands, ShaderProgram.TREE_VISIT.path, source, 4,
                List.of(PipelineBindings.sampler(HIZ), PipelineBindings.uniform(SCENE),
                        PipelineBindings.storage(REQUESTS), PipelineBindings.storage(RENDERS),
                        PipelineBindings.storage(NODES), PipelineBindings.storage(METADATA),
                        PipelineBindings.storage(SOURCE), PipelineBindings.storage(SINK), PipelineBindings.storage(VISIBILITY)));
    }

    DevicePipeline current() {
        DevicePipeline selected = ordinary;
        if (commands.device().capabilities.queueOperations && !subgroupFailed) {
            if (subgroup == null) {
                try { subgroup = create(true); }
                catch (RuntimeException failure) {
                    subgroupFailed = true;
                    ModLog.warn("Render: Subgroup traversal unavailable; using ordinary queues", failure);
                }
            }
            if (subgroup != null) selected = subgroup;
        }
        return selected;
    }

    void close() {
        ordinary.free();
        if (subgroup != null) subgroup.free();
    }
}
