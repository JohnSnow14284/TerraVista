package dev.terravista.mesh;

import dev.terravista.model.ModelCatalog;
import dev.terravista.model.ModelMetadata;
import dev.terravista.world.ResidentSection;
import dev.terravista.world.VoxelBits;
import dev.terravista.world.VoxelWorld;
import java.util.Arrays;

/** 准备材质采样、行掩码与六个相邻区块的原始面。 */
final class SectionSamples {
    private final VoxelWorld world;
    private final ModelCatalog models;
    final long[] cells = new long[32 * 32 * 32 * 2];
    final long[] neighbors = new long[32 * 32 * 6];
    final int[] opaque = new int[32 * 32];
    final int[] nonOpaque = new int[32 * 32];
    final int[] fluid = new int[32 * 32];
    final int[] recessed = new int[32 * 32];

    SectionSamples(VoxelWorld world, ModelCatalog models) { this.world = world; this.models = models; }
    void resetMasks() {
        Arrays.fill(opaque, 0);
        Arrays.fill(nonOpaque, 0);
        Arrays.fill(fluid, 0);
        Arrays.fill(recessed, 0);
    }
    int prepare(long[] raw) {
        if (cells.length != 32 * 32 * 32 * 2) throw new IllegalStateException();
        if (raw.length != 32 * 32 * 32) throw new IllegalStateException();
        int[] mappings = models.rawMappings();
        int flags = 0;
        for (int base = 0; base < 32 * 32 * 32; base += 64) {
            long occupied = 0, solid = 0, pureFluid = 0, containedFluid = 0, lowered = 0;
            for (int bit = 0; bit < 64; bit++) {
                int index = base + bit;
                long state = raw[index];
                int model = 0;
                if (!VoxelBits.isAir(state)) {
                    model = mappings[VoxelBits.block(state)];
                    if (model == -1) return VoxelBits.block(state) | (1 << 31);
                }
                if (model == 0) {
                    cells[index * 2] = (state & (0xFFL << 56)) >>> 1;
                    cells[index * 2 + 1] = 0;
                } else {
                    long metadata = models.metadata(model);
                    cells[index * 2] = FaceEmission.attributes(model, state, metadata);
                    cells[index * 2 + 1] = metadata;
                    occupied |= 1L << bit;
                    solid |= ModelMetadata.fullyOpaqueBit(metadata) << bit;
                    pureFluid |= ModelMetadata.fluidBit(metadata) << bit;
                    containedFluid |= ModelMetadata.containsFluidBit(metadata) << bit;
                    int height = VoxelBits.surfaceHeight(state);
                    if (height != 0 && height < 256) lowered |= 1L << bit;
                }
            }
            if (occupied != 0) {
                flags |= boundaryMask(occupied, base + 64);
                flags |= solid != 0 ? 1 << 6 : 0;
                int row = base >> 5;
                opaque[row] = (int) solid;
                opaque[row + 1] = (int) (solid >>> 32);
                long nonSolid = (occupied ^ solid) & ~pureFluid;
                nonOpaque[row] = (int) nonSolid;
                nonOpaque[row + 1] = (int) (nonSolid >>> 32);
                long fluids = pureFluid | containedFluid;
                fluid[row] = (int) fluids;
                fluid[row + 1] = (int) (fluids >>> 32);
                recessed[row] = (int) lowered;
                recessed[row + 1] = (int) (lowered >>> 32);
            }
        }
        return flags;
    }
    void closeCoveredSurfaces(long[] raw) {
        // 连续实体内部保持完整高度，仅压低暴露表面。
        for (int row = 0; row < 1024; row++) {
            int lowered = recessed[row];
            while (lowered != 0) {
                int x = Integer.numberOfTrailingZeros(lowered);
                lowered &= lowered - 1;
                int cell = row * 32 + x;
                long metadata = cells[cell * 2 + 1];
                if (ModelMetadata.translucent(metadata) || ModelMetadata.fluid(metadata)
                        || ModelMetadata.containsFluid(metadata) || ModelMetadata.doubleSided(metadata)) continue;
                long above = row < 992 ? raw[cell + 1024] : neighbors[3 * 1024 + (row & 31) * 32 + x];
                if (VoxelBits.isAir(above)) continue;
                int model = models.modelId(VoxelBits.block(above));
                long upper = models.metadata(model);
                if (!ModelMetadata.faceOccludes(upper, 0)
                        && !(ModelMetadata.cullsSame(metadata) && model == ((cells[cell * 2] >>> 26) & 65535))) continue;
                cells[cell * 2] &= ~(511L << 3);
                if (!ModelMetadata.fullyOpaque(metadata)) cells[cell * 2] |= 256L << 3;
                recessed[row] &= ~(1 << x);
            }
        }
    }
    private static int boundaryMask(long occupied, int end) {
        int packed = (int) ((occupied >>> 32) | occupied);
        int mask = packed & 1;
        mask += (packed >>> 30) & 0b10;
        mask += (((end - 1) >> 10) == 0 ? 0b100 : 0) * (packed != 0 ? 1 : 0);
        mask += (((end - 1) >> 10) == 31 ? 0b1000 : 0) * (packed != 0 ? 1 : 0);
        mask += ((((end - 33) >> 5) & 31) == 0 ? 0b10000 : 0) * ((int) occupied != 0 ? 1 : 0);
        mask += ((((end - 1) >> 5) & 31) == 31 ? 0b100000 : 0) * ((occupied >>> 32) != 0 ? 1 : 0);
        return mask;
    }
    void acquireFaces(ResidentSection section, int mask) {
        for (int face = 0; face < 6; face++) {
            if ((mask & (1 << face)) == 0) continue;
            var origin = section.contents();
            var neighbor = world.acquire(origin.level,
                    origin.x + (face == 0 ? -1 : face == 1 ? 1 : 0),
                    origin.y + (face == 2 ? -1 : face == 3 ? 1 : 0),
                    origin.z + (face == 4 ? -1 : face == 5 ? 1 : 0));
            try {
                long[] raw = neighbor.contents().blocks();
                for (int index = 0; index < 32 * 32; index++) {
                    int source = switch (face) {
                        case 0 -> (index << 5) + 31;
                        case 1 -> index << 5;
                        case 2 -> index | (31 << 10);
                        case 3 -> index;
                        case 4 -> Integer.expand(index, 0b11111_00000_11111) | (31 << 5);
                        default -> Integer.expand(index, 0b11111_00000_11111);
                    };
                    neighbors[face * 1024 + index] = raw[source];
                }
            } finally { neighbor.release(ResidentSection.POSSIBLE_REUSE); }
        }
    }
    int occupancyBarrier(int index) {
        int mask = opaque[index], boundary = 0;
        boundary |= mask ^ (mask >> 1);
        boundary |= mask ^ (mask << 1);
        boundary |= index < 32 * 31 ? mask ^ opaque[index + 32] : 0;
        boundary |= 31 < index ? mask ^ opaque[index - 32] : 0;
        boundary |= (index & 31) < 31 ? mask ^ opaque[index + 1] : 0;
        boundary |= 0 < (index & 31) ? mask ^ opaque[index - 1] : 0;
        return boundary;
    }
    void collectOccupancy(OccupancyWords target) {
        for (int row = 0; row < 32 * 32; row++) {
            int boundary = occupancyBarrier(row);
            for (; boundary != 0; boundary &= ~Integer.lowestOneBit(boundary))
                target.set(row * 32 + Integer.numberOfTrailingZeros(boundary));
        }
    }
    void collectOccupancy16(OccupancyWords target) {
        for (int row = 0; row < 16 * 16; row++) {
            int x = (row & 15) * 2, y = (row >> 4) * 2;
            int a = occupancyBarrier(y * 32 + x); a = (a | (a >> 16)) & 0xFFFF;
            int b = occupancyBarrier(y * 32 + x + 1); b = (b | (b >> 16)) & 0xFFFF;
            int c = occupancyBarrier((y + 1) * 32 + x); c = (c | (c >> 16)) & 0xFFFF;
            int d = occupancyBarrier((y + 1) * 32 + x + 1); d = (d | (d >> 16)) & 0xFFFF;
            int boundary = a | b | c | d;
            for (; boundary != 0; boundary &= ~Integer.lowestOneBit(boundary))
                target.set(row * 16 + Integer.numberOfTrailingZeros(boundary));
        }
    }
}
