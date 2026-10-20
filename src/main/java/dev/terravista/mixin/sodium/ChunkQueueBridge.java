package dev.terravista.mixin.sodium;

import dev.terravista.compat.SodiumQueuePermits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.Semaphore;

/** 转交任务队列的许可创建与关闭事件。 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobQueue", remap = false)
public class ChunkQueueBridge {
    @Unique private SodiumQueuePermits terravista$queuePermits;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(I)Ljava/util/concurrent/Semaphore;"))
    private Semaphore terravista$queueSemaphore(int permits) {
        if (terravista$queuePermits == null) terravista$queuePermits = new SodiumQueuePermits();
        return terravista$queuePermits.create(permits);
    }

    @Inject(method = "shutdown", at = @At("RETURN"))
    private void terravista$queueStopped(CallbackInfoReturnable<?> ci) {
        if (terravista$queuePermits != null) terravista$queuePermits.release();
    }
}
