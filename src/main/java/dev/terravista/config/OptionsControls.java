package dev.terravista.config;

import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.network.chat.Component;
import java.util.function.Function;
import java.util.function.IntFunction;

/** 创建类型控件并在公共绑定后设置范围与显示函数。 */
public final class OptionsControls {
    private OptionsControls() {}

    public static IntegerOptionBuilder integer(ConfigBuilder builder, OptionsBinding<Integer> binding,
                                                OptionsChanges changes, StorageEventHandler storage, Range range) {
        return integer(builder, binding, changes, storage, range,
                value -> Component.literal(Integer.toString(value)));
    }

    public static IntegerOptionBuilder integer(ConfigBuilder builder, OptionsBinding<Integer> binding,
                                                OptionsChanges changes, StorageEventHandler storage,
                                                Range range,
                                                IntFunction<Component> format) {
        var control = builder.createIntegerOption(binding.identifier());
        binding.attach(control, changes, storage);
        control.setRange(range);
        ControlValueFormatter formatter = value -> format.apply(value);
        control.setValueFormatter(formatter);
        return control;
    }

    public static <E extends Enum<E>> EnumOptionBuilder<E> enumeration(ConfigBuilder builder,
            OptionsBinding<E> binding, OptionsChanges changes, StorageEventHandler storage, Class<E> type,
            Function<E, Component> names) {
        var control = builder.createEnumOption(binding.identifier(), type);
        binding.attach(control, changes, storage);
        control.setElementNameProvider(names);
        return control;
    }
}
