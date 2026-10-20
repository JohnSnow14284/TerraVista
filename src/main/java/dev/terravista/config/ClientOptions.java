package dev.terravista.config;

import dev.terravista.task.ProcessorTopology;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** 保存客户端配置值并校验范围。 */
public final class ClientOptions {
    public enum FogMode {
        CLEAR(true, false), ENVIRONMENT(false, true), CUSTOM(false, true);
        public final boolean removesVanillaEnvFog;
        public final boolean hasFog;
        FogMode(boolean removesVanillaEnvFog, boolean hasFog) {
            this.removesVanillaEnvFog = removesVanillaEnvFog; this.hasFog = hasFog;
        }
    }
    public enum SSAOMode { AUTO, BASIC, BETTER, BEST }
    public enum FoliageStyle { DENSE, NATURAL, DETAILED }
    public enum BlendScope { FLUIDS, TINTED_BLOCKS }
    public enum BuildPressure {
        LOW(250_000, 4, 16, 1600), BALANCED(900_000, 12, 32, 4000), HIGH(2_000_000, 40, 50, 6000);
        public final long modelNanos;
        public final int rootColumns, requests, backlog;
        BuildPressure(long modelNanos, int rootColumns, int requests, int backlog) {
            this.modelNanos = modelNanos; this.rootColumns = rootColumns;
            this.requests = requests; this.backlog = backlog;
        }
    }

    public static final int MIN_DISTANCE = 8, MAX_DISTANCE = 8192;
    private static final float[] DETAIL_PIXELS = {256, 208, 168, 136, 112, 88, 72};

    public static ClientOptions CONFIG = ClientOptionsFile.DEFAULT.loadOrCreate();
    public int viewDistanceChunks = 512;
    public int serviceThreads = (int) Math.max(ProcessorTopology.coreCount() / 1.5, 1);
    public int terrainDetail = 3;
    public String fogStyle;
    public int fogStrength = 70;
    public int fogReachChunks = 256;
    public String ssaoMode;
    public String foliageStyle;
    public int biomeBlendRadius = 2;
    public boolean biomeBlendAllBlocks;
    public int buildPressure = 2;
    public int worldCurvature;

    public BuildPressure pressure() {
        return switch (Math.clamp(buildPressure, 1, 3)) {
            case 1 -> BuildPressure.LOW;
            case 3 -> BuildPressure.HIGH;
            default -> BuildPressure.BALANCED;
        };
    }

    public FoliageStyle foliage() {
        if (foliageStyle == null) return FoliageStyle.NATURAL;
        try { return FoliageStyle.valueOf(foliageStyle.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return FoliageStyle.NATURAL; }
    }
    public void foliage(FoliageStyle style) { foliageStyle = style.name().toLowerCase(Locale.ROOT); }

    public SSAOMode ssao() {
        if (ssaoMode == null) return SSAOMode.AUTO;
        try { return SSAOMode.valueOf(ssaoMode.toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { return SSAOMode.AUTO; }
    }
    public void ssao(SSAOMode mode) { ssaoMode = mode.name().toLowerCase(Locale.ROOT); }
    public FogMode fog() {
        if (fogStyle == null) return FogMode.ENVIRONMENT;
        try { return FogMode.valueOf(fogStyle.toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { return FogMode.ENVIRONMENT; }
    }
    public void fog(FogMode mode) { fogStyle = mode.name().toLowerCase(Locale.ROOT); }
    public int renderDistanceChunks(int vanillaChunks) {
        return Math.max(Math.clamp(viewDistanceChunks, MIN_DISTANCE, MAX_DISTANCE), vanillaChunks);
    }
    public float detailPixels() { return DETAIL_PIXELS[Math.clamp(terrainDetail, 1, DETAIL_PIXELS.length) - 1]; }
    public void normalize() {
        viewDistanceChunks = Math.clamp(viewDistanceChunks, MIN_DISTANCE, MAX_DISTANCE);
        terrainDetail = Math.clamp(terrainDetail, 1, DETAIL_PIXELS.length);
        serviceThreads = Math.clamp(serviceThreads, 1, ProcessorTopology.coreCount());
        fogStrength = Math.clamp(fogStrength, 0, 100);
        fogReachChunks = Math.clamp(fogReachChunks, MIN_DISTANCE, MAX_DISTANCE);
        biomeBlendRadius = Math.clamp(biomeBlendRadius, 0, 7);
        buildPressure = Math.clamp(buildPressure, 1, 3);
        worldCurvature = Math.clamp(worldCurvature, -10000, 10000);
    }
    public boolean renderingEnabled(BooleanSupplier sessionAvailable, BooleanSupplier nativeRenderer) {
        return sessionAvailable.getAsBoolean() && nativeRenderer.getAsBoolean();
    }
}
