package dev.terravista.mixin.sodium;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取配置项标识。 */
@Mixin(value = Option.class, remap = false)
public interface OptionIdentityAccess {
    @Accessor("id") Identifier terravista$id();
}
