package dev.terravista.config;

import dev.terravista.task.ProcessorTopology;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.option.SteppedValidator;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import static dev.terravista.config.SodiumOptionsComposer.*;

/** 按渲染和存档页面注册分组配置。 */
public final class OptionsPages {
    public static final Identifier DISTANCE = Identifier.parse("terravista:view_distance");
    private static final String FOG_STYLE = "terravista:fog_style";
    private OptionsPages() {}

    public static void register(ConfigBuilder builder, ModOptionsBuilder target, ClientOptions options) {
        var actions = new OptionsActions(options);
        String reload = OptionFlag.REQUIRES_RENDERER_RELOAD.getId().toString();
        var threads = binding("worker_count", "workerCount", () -> options.serviceThreads, value -> options.serviceThreads = value)
                .flags("terravista:update_threads");
        var detail = binding("terrain_detail", "terrainDetail", () -> options.terrainDetail, value -> options.terrainDetail = value)
                .impact(OptionImpact.HIGH);
        var distance = binding("view_distance", "viewDistance", () -> options.renderDistanceChunks(vanillaDistance()),
                value -> options.viewDistanceChunks = value).afterApply(actions::distanceApplied, reload).impact(OptionImpact.MEDIUM);
        var fog = binding("fog_style", "fogStyle", () -> options.fog().ordinal(), value -> options.fog(ClientOptions.FogMode.values()[value]))
                .flags(reload);
        var strength = binding("fog_strength", "fogStrength", () -> options.fogStrength, value -> options.fogStrength = value)
                .enabledWhen(state -> state.readIntOption(Identifier.parse(FOG_STYLE)) == ClientOptions.FogMode.CUSTOM.ordinal(), FOG_STYLE);
        var reach = binding("fog_reach", "fogReach", () -> options.fogReachChunks, value -> options.fogReachChunks = value)
                .enabledWhen(state -> state.readIntOption(Identifier.parse(FOG_STYLE)) == ClientOptions.FogMode.CUSTOM.ordinal(), FOG_STYLE);
        var ssao = binding("ambient_occlusion", "ambientOcclusion", () -> options.ssao().ordinal(),
                value -> options.ssao(ClientOptions.SSAOMode.values()[value])).impact(OptionImpact.MEDIUM).flags(reload);
        var foliage = binding("foliage_style", "foliageStyle", () -> options.foliage().ordinal(),
                value -> options.foliage(ClientOptions.FoliageStyle.values()[value])).impact(OptionImpact.MEDIUM).flags(reload);
        var blendRadius = binding("biome_blend_radius", "biomeBlendRadius", () -> options.biomeBlendRadius,
                value -> options.biomeBlendRadius = value).impact(OptionImpact.MEDIUM).flags(reload);
        var blendScope = binding("biome_blend_scope", "biomeBlendScope", () -> options.biomeBlendAllBlocks
                        ? ClientOptions.BlendScope.TINTED_BLOCKS : ClientOptions.BlendScope.FLUIDS,
                value -> options.biomeBlendAllBlocks = value == ClientOptions.BlendScope.TINTED_BLOCKS).flags(reload);
        var pressure = binding("build_pressure", "buildPressure", () -> options.buildPressure, value -> options.buildPressure = value)
                .impact(OptionImpact.HIGH);
        var curve = binding("world_curvature", "worldCurvature", () -> options.worldCurvature, value -> options.worldCurvature = value)
                .impact(OptionImpact.LOW).flags(OptionFlag.REQUIRES_ASSET_RELOAD.getId().toString());
        var rendering = new Page(Component.translatable("terravista.config.rendering"),
                new Group(Component.translatable("terravista.config.group.distance"), new Entry(context -> {
                    var control = OptionsControls.integer(context.builder(), distance, context.changes(), context.storage(),
                            new Range(ClientOptions.MIN_DISTANCE, ClientOptions.MAX_DISTANCE, 8));
                    control.setValidator(new DistanceRange());
                    control.setValueFormatter(value -> Component.translatable("terravista.config.chunks", value));
                    return control;
                }), distanceInput(builder), integer(detail, new Range(1, 7, 1),
                        value -> label("terrainDetail." + value)),
                        integer(ssao, new Range(0, ClientOptions.SSAOMode.values().length - 1, 1),
                                value -> label("ambientOcclusion." + ClientOptions.SSAOMode.values()[value].name().toLowerCase(Locale.ROOT))),
                        integer(foliage, new Range(0, ClientOptions.FoliageStyle.values().length - 1, 1),
                                value -> label("foliageStyle." + ClientOptions.FoliageStyle.values()[value].name().toLowerCase(Locale.ROOT))),
                        integer(curve, new Range(-10000, 10000, 50), value -> value == 0 ? Component.translatable("options.off")
                                : Component.literal(Integer.toString(value)))),
                new Group(Component.translatable("terravista.config.group.performance"),
                        integer(threads, new Range(1, ProcessorTopology.coreCount(), 1), value -> Component.literal(Integer.toString(value))),
                        integer(pressure, new Range(1, 3, 1), value -> label("buildPressure." + value))),
                new Group(Component.translatable("terravista.config.group.fog"), integer(fog, new Range(0, ClientOptions.FogMode.values().length - 1, 1),
                                value -> label("fogStyle." + ClientOptions.FogMode.values()[value].name().toLowerCase(Locale.ROOT))),
                        integer(strength, new Range(0, 100, 1), value -> Component.literal(value + "%")),
                        integer(reach, new Range(8, 8192, 8), value -> Component.translatable("terravista.config.chunks", value))),
                new Group(Component.translatable("terravista.config.group.biomes"),
                        integer(blendRadius, new Range(0, 7, 1), value -> Component.literal(Integer.toString(value))),
                        new Entry(context -> OptionsControls.enumeration(context.builder(), blendScope, context.changes(), context.storage(),
                                ClientOptions.BlendScope.class, value -> label("biomeBlendScope." + value.ordinal())))));
        var saves = new Page(Component.translatable("terravista.config.saves"),
                new Group(Component.translatable("terravista.config.group.import"),
                        importButton(builder, "import_current", "importCurrent", ImportOptions::current),
                        importButton(builder, "import_save", "importSave", parent -> ImportOptions.chooseSave()),
                        new Entry(context -> builder.createExternalButtonOption(Identifier.parse("terravista:open_lod_folder"))
                                .setName(label("openLodFolder")).setTooltip(label("openLodFolder.tooltip"))
                                .setScreenConsumer(parent -> ImportOptions.openLodFolder()))));
        SodiumOptionsComposer.register(builder, target, () -> ClientOptionsFile.DEFAULT.save(options),
                changes -> changes.on("terravista:update_threads", OptionsActions::threadsApplied), rendering, saves);
    }

    private static Entry distanceInput(ConfigBuilder builder) {
        return new Entry(context -> builder.createExternalButtonOption(Identifier.parse("terravista:distance_input"))
                .setName(label("distanceInput")).setTooltip(label("distanceInput.tooltip"))
                .setScreenConsumer(parent -> Minecraft.getInstance().gui.setScreen(new DistanceInputScreen(parent,
                        (IntegerOption) ConfigManager.CONFIG.getOption(DISTANCE)))));
    }

    private static Entry importButton(ConfigBuilder builder, String id, String key, Consumer<Screen> action) {
        return new Entry(context -> builder.createExternalButtonOption(Identifier.parse("terravista:" + id))
                .setName(label(key)).setTooltip(label(key + ".tooltip"))
                .setEnabledProvider(state -> ImportOptions.available(), net.caffeinemc.mods.sodium.api.config.ConfigState.UPDATE_ON_REBUILD)
                .setScreenConsumer(action));
    }

    private static Component label(String key) { return Component.translatable("terravista.config.general." + key); }
    private static <T> OptionsBinding<T> binding(String id, String key, Supplier<T> read, Consumer<T> write) {
        return new OptionsBinding<>("terravista:" + id, label(key), read, write);
    }
    private static Entry integer(OptionsBinding<Integer> binding, Range range, IntFunction<Component> format) {
        return new Entry(context -> OptionsControls.integer(context.builder(), binding, context.changes(), context.storage(), range, format));
    }
    static int vanillaDistance() { return Minecraft.getInstance().options.getEffectiveRenderDistance(); }

    /** 保留手动输入的整数，滑块仍按八区块移动。 */
    private record DistanceRange() implements SteppedValidator {
        public int min() { return ClientOptions.MIN_DISTANCE; }
        public int max() { return ClientOptions.MAX_DISTANCE; }
        public int step() { return 8; }
        @Override public Integer getValidatedValue(Integer value, Supplier<Integer> fallback) {
            int validated = Math.clamp(value, Math.max(min(), vanillaDistance()), max());
            return value.intValue() == validated ? value : Integer.valueOf(validated);
        }
    }
}
