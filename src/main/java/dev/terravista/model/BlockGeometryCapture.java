package dev.terravista.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import java.util.ArrayList;
import java.util.List;

/** 选择烘焙方块部件并按顺序分发四边形。 */
final class BlockGeometryCapture {
    private BlockGeometryCapture() {}
    static boolean collect(BlockState state, QuadVertexCapture opaque, QuadVertexCapture translucent) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) return false;
        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(new SingleThreadedRandomSource(42L), parts);
        boolean centered = true;
        int families = 0, count = 0;
        for (var part : parts) {
            for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH,
                    Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
                var quads = part.getQuads(direction);
                if (direction != null && !quads.isEmpty()) centered = false;
                for (var quad : quads) {
                    if (centered) {
                        int family = CrossPlantGeometry.family(quad);
                        if (family == 0) centered = false;
                        else { families |= family; count++; }
                    }
                    (quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT ? translucent : opaque)
                            .quad(quad, state.is(BlockTags.LEAVES));
                }
            }
        }
        return centered && count >= 2 && families == 3;
    }
}
