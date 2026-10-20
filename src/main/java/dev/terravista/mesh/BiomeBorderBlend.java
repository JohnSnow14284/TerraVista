package dev.terravista.mesh;

import dev.terravista.config.ClientOptions;
import dev.terravista.model.ModelCatalog;
import dev.terravista.model.ModelMetadata;
import dev.terravista.model.PendingModel;
import dev.terravista.world.ResidentSection;
import dev.terravista.world.VoxelBits;
import dev.terravista.world.VoxelWorld;

/** 在网格重建时混合水平邻域的群系颜色。 */
final class BiomeBorderBlend {
    private final VoxelWorld world;
    private final ModelCatalog models;
    private final int radius = Math.clamp(ClientOptions.CONFIG.biomeBlendRadius, 0, 7);
    private final boolean allBlocks = ClientOptions.CONFIG.biomeBlendAllBlocks;

    BiomeBorderBlend(VoxelWorld world, ModelCatalog models) { this.world = world; this.models = models; }

    void apply(ResidentSection section, SectionSamples samples) {
        if (radius == 0) return;
        if (!allBlocks) {
            boolean hasFluid = false;
            for (int mask : samples.fluid) hasFluid |= mask != 0;
            if (!hasFluid) return;
        }
        var origin = section.contents();
        var neighbors = new ResidentSection[9];
        var blocks = new long[9][];
        blocks[4] = origin.blocks();
        var biomes = new int[(radius * 2 + 1) * (radius * 2 + 1)];
        try {
            for (int cell = 0; cell < 32768; cell++) {
                long metadata = samples.cells[cell * 2 + 1];
                if (!ModelMetadata.biomeColoured(metadata) || ModelMetadata.containsFluid(metadata)
                        || (!allBlocks && !ModelMetadata.fluid(metadata))) continue;
                int x = cell & 31, z = (cell >>> 5) & 31, y = cell >>> 10;
                int center = VoxelBits.biome(blocks[4][cell]), count = 0;
                boolean mixed = false;
                for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                    int nx = x + dx, nz = z + dz;
                    int sx = Math.floorDiv(nx, 32), sz = Math.floorDiv(nz, 32);
                    int slot = (sz + 1) * 3 + sx + 1;
                    if (slot != 4 && blocks[slot] == null && neighbors[slot] == null) {
                        neighbors[slot] = world.acquire(origin.level, origin.x + sx, origin.y, origin.z + sz);
                        blocks[slot] = neighbors[slot].contents().blocks();
                    }
                    long raw = blocks[slot][(nx & 31) | ((nz & 31) << 5) | (y << 10)];
                    int biome = VoxelBits.isAir(raw) ? center : VoxelBits.biome(raw);
                    biomes[count++] = biome;
                    mixed |= biome != center;
                }
                if (!mixed) continue;
                long data = samples.cells[cell * 2];
                int model = (int) ((data >>> 26) & 0xFFFF);
                int[] colours = models.blendedTints.row(model);
                if (colours == null) throw new PendingModel(model, false);
                int red = 0, green = 0, blue = 0;
                for (int biome : biomes) {
                    if (biome >= colours.length) throw new PendingModel(model, false);
                    int colour = colours[biome];
                    red += colour & 255; green += (colour >>> 8) & 255; blue += (colour >>> 16) & 255;
                }
                int colour = (red / count) | ((green / count) << 8) | ((blue / count) << 16);
                int index = models.blendedTints.index(colour);
                samples.cells[cell * 2] = (data & ~(0x1FFL << 46)) | ((long) index << 46) | Long.MIN_VALUE;
            }
        } finally {
            for (var neighbor : neighbors) if (neighbor != null) neighbor.release(ResidentSection.POSSIBLE_REUSE);
        }
    }
}
