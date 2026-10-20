package dev.terravista.mixin.minecraft;

import dev.terravista.bootstrap.StartupFailurePolicy;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 转交事件循环的任务异常判定。 */
@Mixin(BlockableEventLoop.class)
public abstract class TaskFailureBridge {
    @Redirect(method = "doRunTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/BlockableEventLoop;isNonRecoverable(Ljava/lang/Throwable;)Z"))
    private boolean terravista$taskFailure(Throwable failure) {
        return StartupFailurePolicy.nonRecoverable(failure);
    }
}
