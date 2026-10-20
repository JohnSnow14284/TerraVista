package dev.terravista.bootstrap;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

/** 按注入包职责和已发现模组选择注入。 */
public final class InjectionSelection implements IMixinConfigPlugin {
    private String prefix;

    @Override
    public void onLoad(String mixinPackage) {
        prefix = mixinPackage + ".";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(prefix)) return true;
        String relativeName = mixinClassName.substring(prefix.length());
        int separator = relativeName.indexOf('.');
        if (separator < 0) return true;
        String owner = relativeName.substring(0, separator);
        return owner.equals("vulkan") || RuntimeEnvironment.modLoaded(owner);
    }
}
