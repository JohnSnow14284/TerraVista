package dev.terravista.mixin.minecraft;

import dev.terravista.compat.DebugRegistration;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** 将调试列表排序前的状态交给调试注册服务。 */
@Mixin(DebugScreenEntryList.class)
public abstract class DebugListBridge {
    @Shadow @Final private List<Identifier> currentlyEnabled;
    @Shadow public abstract boolean isOverlayVisible();

    @Inject(method = "rebuildCurrentList", at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
    private void terravista$beforeSort(CallbackInfo callback) {
        DebugRegistration.beforeSort(isOverlayVisible(), currentlyEnabled);
    }
}
