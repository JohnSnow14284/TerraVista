package dev.terravista.render.gpu;

/** 声明渲染程序的资源入口与着色阶段。 */
public enum ShaderProgram {
    BOUNDS_VERTEX("bounds/volume.vert", ShaderStage.VERTEX),
    BOUNDS_FRAGMENT("bounds/volume.frag", ShaderStage.FRAGMENT),
    DEPTH_REDUCE("depth/reduce.comp", ShaderStage.COMPUTE),
    DEPTH_TILED("depth/tiled.comp", ShaderStage.COMPUTE),
    VISIBILITY_MARK("visibility/mark.comp", ShaderStage.COMPUTE),
    VISIBILITY_MINIMUM("visibility/minimum.comp", ShaderStage.COMPUTE),
    VISIBILITY_EVICT("visibility/evict.comp", ShaderStage.COMPUTE),
    VISIBILITY_RANK("visibility/rank.comp", ShaderStage.COMPUTE),
    DRAW_TRANSLUCENT("draw/translucent.comp", ShaderStage.COMPUTE),
    DRAW_RESET("draw/reset.comp", ShaderStage.COMPUTE),
    DRAW_COMMANDS("draw/commands.comp", ShaderStage.COMPUTE),
    DRAW_PREPARE("draw/prepare.comp", ShaderStage.COMPUTE),
    TERRAIN_VERTEX("surface/terrain.vert", ShaderStage.VERTEX),
    TERRAIN_FRAGMENT("surface/terrain.frag", ShaderStage.FRAGMENT),
    COMPOSITE_DEPTH("composite/depth.frag", ShaderStage.FRAGMENT),
    COMPOSITE_VERTEX("composite/screen.vert", ShaderStage.VERTEX),
    COMPOSITE_RESOLVE("composite/resolve.frag", ShaderStage.FRAGMENT),
    TRANSFER_COPY("transfer/copy.comp", ShaderStage.COMPUTE),
    TRANSFER_SCATTER("transfer/scatter.comp", ShaderStage.COMPUTE),
    SCAN_SERIAL("scan/serial.comp", ShaderStage.COMPUTE),
    SCAN_SUBGROUP("scan/subgroup.comp", ShaderStage.COMPUTE),
    TREE_VISIT("tree/visit.comp", ShaderStage.COMPUTE),
    OCCLUSION_APPLY("occlusion/apply.comp", ShaderStage.COMPUTE);

    public final String path;
    public final ShaderStage stage;

    ShaderProgram(String path, ShaderStage stage) {
        this.path = path;
        this.stage = stage;
    }
}
