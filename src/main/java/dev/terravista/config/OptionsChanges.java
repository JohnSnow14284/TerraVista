package dev.terravista.config;

import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.option.FlagHook;
import net.minecraft.resources.Identifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按变更标记执行回调并跳过被重载覆盖的操作。 */
public final class OptionsChanges implements FlagHook {
    private record Action(Runnable callback, Set<Identifier> excluded) {}
    private final Map<Identifier, Action> actions = new LinkedHashMap<>();

    public OptionsChanges on(String name, Runnable callback, String... excluded) {
        var id = Identifier.parse(name);
        var identifiers = new Identifier[excluded.length];
        for (int i = 0; i < excluded.length; i++) identifiers[i] = Identifier.parse(excluded[i]);
        return on(id, callback, identifiers);
    }

    public OptionsChanges on(Identifier name, Runnable callback, Identifier... excluded) {
        actions.put(name, new Action(callback, new LinkedHashSet<>(List.of(excluded))));
        return this;
    }

    @Override public Collection<Identifier> getTriggers() { return actions.keySet(); }

    @Override public void accept(Collection<Identifier> identifiers, ConfigState state) {
        for (var id : identifiers) {
            var action = actions.get(id);
            if (action != null && Collections.disjoint(identifiers, action.excluded)) action.callback.run();
        }
    }
}
