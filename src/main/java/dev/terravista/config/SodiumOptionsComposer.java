package dev.terravista.config;

import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.function.Function;

/** 按页面和分组顺序创建控件并注册应用回调。 */
public final class SodiumOptionsComposer {
    private SodiumOptionsComposer() {}

    public record Context(ConfigBuilder builder, OptionsChanges changes, StorageEventHandler storage) {}

    public record Entry(Function<Context, OptionBuilder> create) {}

    public static final class Group {
        private final Component name;
        private final Entry[] entries;

        public Group(Component name, Entry... entries) {
            this.name = name;
            this.entries = entries;
        }

        private OptionGroupBuilder create(Context context) {
            var group = context.builder.createOptionGroup();
            group.setName(name);
            for (var entry : entries) group.addOption(entry.create.apply(context));
            return group;
        }
    }

    public static final class Page {
        private final Component name;
        private final Group[] groups;

        public Page(Component name, Group... groups) {
            this.name = name;
            this.groups = groups;
        }

        private OptionPageBuilder create(Context context) {
            var page = context.builder.createOptionPage();
            page.setName(name);
            for (var group : groups) page.addOptionGroup(group.create(context));
            return page;
        }
    }

    public static void register(ConfigBuilder builder, ModOptionsBuilder options, StorageEventHandler storage,
                                Consumer<OptionsChanges> callbacks, Page... pages) {
        var changes = new OptionsChanges();
        callbacks.accept(changes);
        var context = new Context(builder, changes, storage);
        for (var page : pages) options.addPage(page.create(context));
        options.registerFlagHook(changes);
    }
}
