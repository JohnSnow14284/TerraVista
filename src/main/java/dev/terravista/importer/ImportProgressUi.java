package dev.terravista.importer;

import dev.terravista.bootstrap.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 将导入完成结果提交至客户端界面。 */
public final class ImportProgressUi extends ImportCoordinator {
    protected final class ProgressRun extends Run {
        protected ProgressRun(ImportJob job) { super(job); }

        @Override protected void completed(int total) {
            super.completed(total);
            Minecraft.getInstance().execute(() -> {
                var message = Component.translatable(job.successful() ? "terravista.message.import_succeeded" : "terravista.message.import_failed");
                Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
                ModLog.info(message.getString());
            });
        }
    }

    @Override protected synchronized Run create(ImportJob job) { return new ProgressRun(job); }
}
