package dev.terravista.render.gpu;

import static org.lwjgl.system.MemoryUtil.memPutInt;

/** 管理地形索引、透明距离桶及共享采样器引用。 */
final class TerrainBuffers {
    static final int QUAD_CAPACITY = 65_535;
    final GpuBuffer distances, indices;
    final long depthSampler, lightSampler;

    TerrainBuffers(FrameCommands commands, VulkanUploads uploads) {
        if (VulkanViewTargets.LOOKUP_CAPACITY > (1 << 22)) {
            throw new IllegalStateException("TerraVista Render: Render queue exceeds packed translucent draw ID capacity");
        }
        GpuBuffer createdDistances = null, createdIndices = null;
        try {
            distances = createdDistances = new GpuBuffer(commands, 1024L * 4 + VulkanViewTargets.TRANSLUCENT_CAPACITY * 4L);
            distances.zero();
            indices = createdIndices = new GpuBuffer(commands, QUAD_CAPACITY * 6L * Integer.BYTES);
            long pointer = uploads.upload(indices, 0, indices.sizeBytes());
            for (int quad = 0; quad < QUAD_CAPACITY; quad++) {
                int vertex = quad * 4;
                memPutInt(pointer, vertex + 1);
                memPutInt(pointer + 4, vertex + 2);
                memPutInt(pointer + 8, vertex);
                memPutInt(pointer + 12, vertex + 1);
                memPutInt(pointer + 16, vertex + 3);
                memPutInt(pointer + 20, vertex + 2);
                pointer += 24;
            }
            uploads.commit();
            depthSampler = DeviceSamplers.get(commands.device(), false, false);
            lightSampler = DeviceSamplers.get(commands.device(), false, true);
        } catch (RuntimeException | Error failure) {
            if (createdDistances != null) createdDistances.free();
            if (createdIndices != null) createdIndices.free();
            throw failure;
        }
    }

    void close() {
        distances.free();
        indices.free();
    }
}
