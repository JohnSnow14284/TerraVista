package dev.terravista.config;

import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.StatefulOptionBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Predicate;
import net.caffeinemc.mods.sodium.api.config.ConfigState;

/** 将选项值、提示、启用条件和保存回调绑定到控件。 */
public final class OptionsBinding<T> {
    private final String id;
    private final Component label;
    private final Component description;
    private final Supplier<T> read;
    private final Consumer<T> write;
    private Predicate<ConfigState> enabled;
    private Identifier[] enabledDependencies;
    private OptionImpact impact;
    private Consumer<T> applied;
    private Identifier[] excluded;
    private Identifier[] flags;

    public OptionsBinding(String id, Component label, Component description, Supplier<T> read, Consumer<T> write) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.read = read;
        this.write = write;
    }

    public OptionsBinding(String id, Component label, Supplier<T> read, Consumer<T> write) {
        this(id, label, label.getContents() instanceof TranslatableContents translation
                ? Component.translatable(translation.getKey() + ".tooltip") : label, read, write);
    }

    public OptionsBinding<T> enabledWhen(Predicate<ConfigState> condition, String... dependencies) {
        enabled = condition;
        enabledDependencies = identifiers(dependencies);
        return this;
    }
    Identifier identifier() { return Identifier.parse(id); }
    public OptionsBinding<T> impact(OptionImpact value) { impact = value; return this; }

    public OptionsBinding<T> afterApply(Consumer<T> action, String... conflicts) {
        applied = action;
        excluded = identifiers(conflicts);
        return this;
    }

    public OptionsBinding<T> flags(String... names) {
        flags = identifiers(names);
        return this;
    }

    public void attach(StatefulOptionBuilder<T> control, OptionsChanges changes, StorageEventHandler storage) {
        control.setName(label);
        control.setTooltip(description);
        var triggers = new LinkedHashSet<Identifier>();
        if (applied != null) {
            var trigger = Identifier.parse(id);
            var action = applied;
            var reader = read;
            changes.on(trigger, () -> action.accept(reader.get()), excluded);
            triggers.add(trigger);
        }
        if (flags != null) triggers.addAll(List.of(flags));
        if (!triggers.isEmpty()) control.setFlags(triggers.toArray(Identifier[]::new));
        control.setBinding(write, read);
        if (enabled != null) {
            control.setEnabledProvider(state -> enabled.test(state), enabledDependencies);
        }
        control.setStorageHandler(storage);
        control.setDefaultValue(read.get());
        if (impact != null) control.setImpact(impact);
    }

    private static Identifier[] identifiers(String[] names) {
        var ids = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) ids[i] = Identifier.parse(names[i]);
        return ids;
    }
}
