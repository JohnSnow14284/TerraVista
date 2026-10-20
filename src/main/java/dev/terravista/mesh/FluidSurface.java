package dev.terravista.mesh;

import dev.terravista.world.ResidentSection;
import dev.terravista.world.SectionContents;
import dev.terravista.world.VoxelBits;
import dev.terravista.world.VoxelWorld;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import java.util.Arrays;

/** 管理流体邻域引用并缓存量化后的四角高度。 */
final class FluidSurface {
    private final VoxelWorld world;
    private ResidentSection source;
    private final ResidentSection[] neighbors = new ResidentSection[27];
    private final int[] corners = new int[32 * 32 * 32];

    FluidSurface(VoxelWorld world) { this.world = world; }
    void clearCache() { Arrays.fill(corners, -1); }
    void source(ResidentSection section) { source = section; }
    ResidentSection source() { return source; }
    int waterBlockId() { return world.palette().blockId(net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()); }
    boolean ice(long value) { return world.palette().blockState(VoxelBits.block(value)).getBlock() instanceof net.minecraft.world.level.block.IceBlock; }
    boolean iceSurface(int x, int y, int z) {
        return source.contents().level > 0
                && block(x, y, z).getBlock() instanceof net.minecraft.world.level.block.IceBlock
                && !(block(x, y + 1, z).getBlock() instanceof net.minecraft.world.level.block.IceBlock)
                && !(block(x, y - 1, z).getBlock() instanceof net.minecraft.world.level.block.IceBlock);
    }
    boolean unrecordedSide(int x, int y, int z, int axis, int side) {
        int step = side * 2 - 1;
        return !VoxelBits.sampled(voxel(x + (axis == 2 ? step : 0), y, z + (axis == 1 ? step : 0)));
    }
    int packedHeights(int x, int y, int z) {
        int index = SectionContents.index(x, y, z);
        int cached = corners[index];
        if (cached != -1) return cached;
        long raw = source.contents().blocks()[index];
        if (VoxelBits.iceWater(raw)) {
            int height = Math.max(1, VoxelBits.surfaceHeight(raw) - (256 >> source.contents().level));
            return corners[index] = height | (6 << 9);
        }
        FluidState fluid = block(x, y, z).getFluidState();
        if (fluid.isEmpty()) {
            corners[index] = 0xFFF;
            return 0xFFF;
        }
        if (source.contents().level > 0) {
            int height = VoxelBits.surfaceHeight(source.contents().blocks()[index]);
            boolean covered = fluid.getType().isSame(block(x, y + 1, z).getFluidState().getType());
            if (covered) height = 256;
            int cap = covered ? 7 : Math.max(0, Math.min(7, Math.round(fluid.getOwnHeight() * 8.0f) - 1));
            return corners[index] = height | (cap << 9);
        }
        int packed = 0;
        for (int cornerX = 0; cornerX < 2; cornerX++) for (int cornerZ = 0; cornerZ < 2; cornerZ++) {
            float height = corner(fluid, x, y, z, cornerX, cornerZ);
            int quantized = Math.max(0, Math.min(7, Math.round(height * 8.0f) - 1));
            packed |= quantized << (3 * ((cornerX << 1) | cornerZ));
        }
        corners[index] = packed;
        return packed;
    }
    float iceShade(int x, int y, int z) {
        return iceShade(x, y, z, true);
    }
    float iceShade(int x, int y, int z, boolean fluid) {
        if (!net.minecraft.client.Minecraft.getInstance().options.ambientOcclusion().get()) return 1;
        int roof = VoxelBits.iceWater(voxel(x, y, z)) ? y : y + 1;
        if (!(block(x, roof, z).getBlock() instanceof net.minecraft.world.level.block.IceBlock)) return 1;
        float shade = 0;
        // 将冰盖邻域遮蔽插值到水面的实际高度。
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            var state = block(x + dx, roof, z + dz);
            float value = state.getShadeBrightness(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
            value = ((int) (value * 4096)) / 4096f;
            shade += value * (dx == 0 ? 2 : 1) * (dz == 0 ? 2 : 1);
        }
        if (!fluid) return shade / 16;
        int packed = packedHeights(x, y, z);
        float height = 0;
        for (int corner = 0; corner < 4; corner++) {
            int cap = source.contents().level > 0 ? packed >>> 9 : (packed >>> (corner * 3)) & 7;
            height += (cap == 6 ? 8f / 9f : (cap + 1) / 8f) - .001f;
        }
        return 1 + (shade / 16 - 1) * (height / 4);
    }
    private float corner(FluidState fluid, int x, int y, int z, int cornerX, int cornerZ) {
        int dx = cornerX == 0 ? -1 : 1;
        int dz = cornerZ == 0 ? -1 : 1;
        float center = height(fluid, x, y, z);
        if (center >= 1.0f) return 1.0f;
        float alongX = height(fluid, x + dx, y, z);
        float alongZ = height(fluid, x, y, z + dz);
        if (alongX >= 1.0f || alongZ >= 1.0f) return 1.0f;
        float diagonal = -1.0f;
        if (alongX > 0.0f || alongZ > 0.0f) {
            diagonal = height(fluid, x + dx, y, z + dz);
            if (diagonal >= 1.0f) return 1.0f;
        }
        float centerWeight = weight(center);
        float xWeight = weight(alongX);
        float zWeight = weight(alongZ);
        float diagonalWeight = weight(diagonal);
        float weight = centerWeight + xWeight + zWeight + diagonalWeight;
        if (weight == 0.0f) return fluid.getOwnHeight();
        return (center * centerWeight + alongX * xWeight + alongZ * zWeight + diagonal * diagonalWeight) / weight;
    }
    private static float weight(float height) {
        if (height >= 0.8f) return 10.0f;
        return height >= 0.0f ? 1.0f : 0.0f;
    }
    private float height(FluidState fluid, int x, int y, int z) {
        long raw = voxel(x, y, z);
        if (!VoxelBits.sampled(raw)) return fluid.getOwnHeight();
        BlockState state = world.palette().blockState(VoxelBits.block(raw));
        FluidState sample = state.getFluidState();
        if (fluid.getType().isSame(sample.getType())) {
            FluidState above = block(x, y + 1, z).getFluidState();
            return fluid.getType().isSame(above.getType()) ? 1.0f : sample.getOwnHeight();
        }
        return state.isSolid() ? -1.0f : 0.0f;
    }
    boolean sameFluid(long first, long second) {
        var palette = world.palette();
        FluidState a = palette.blockState(VoxelBits.block(first)).getFluidState();
        if (a.isEmpty()) return false;
        FluidState b = palette.blockState(VoxelBits.block(second)).getFluidState();
        return !b.isEmpty() && a.getType().isSame(b.getType());
    }
    private BlockState block(int x, int y, int z) {
        return world.palette().blockState(VoxelBits.block(voxel(x, y, z)));
    }
    long voxel(int x, int y, int z) {
        int sx = Math.floorDiv(x, 32), sy = Math.floorDiv(y, 32), sz = Math.floorDiv(z, 32);
        if (Math.abs(sx) > 1 || Math.abs(sy) > 1 || Math.abs(sz) > 1)
            throw new IllegalArgumentException("TerraVista Mesh: Fluid neighbour outside one-section border");
        ResidentSection section;
        if (sx == 0 && sy == 0 && sz == 0) section = source;
        else {
            int index = ((sy + 1) * 3 + (sz + 1)) * 3 + (sx + 1);
            section = neighbors[index];
            if (section == null) {
                var position = source.contents();
                section = world.acquire(position.level, position.x + sx, position.y + sy, position.z + sz);
                neighbors[index] = section;
            }
        }
        return section.contents().blocks()[SectionContents.index(Math.floorMod(x, 32), Math.floorMod(y, 32), Math.floorMod(z, 32))];
    }
    void releaseNeighbors() {
        RuntimeException failure = null;
        for (int index = 0; index < neighbors.length; index++) {
            var section = neighbors[index];
            if (section != null) {
                neighbors[index] = null;
                try { section.release(ResidentSection.POSSIBLE_REUSE); }
                catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
        }
        source = null;
        if (failure != null) throw failure;
    }
}
