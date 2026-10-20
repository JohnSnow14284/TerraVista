package dev.terravista.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 为 TerraVista 操作按钮接入左键点击。 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.options.control.ExternalButtonControl$ExternalButtonControlElement", remap = false)
public class OptionsButtonBridge {
    @Shadow @Final private ExternalButtonOption option;

    @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/input/MouseButtonEvent;button()I"))
    private int terravista$button(int button) {
        return button == 0 && ((OptionIdentityAccess) option).terravista$id().getNamespace().equals("terravista") ? 1 : button;
    }
}
