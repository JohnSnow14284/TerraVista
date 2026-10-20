package dev.terravista.config;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.importer.CurrentWorldImport;
import dev.terravista.world.WorldSession;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 展示当前世界导入进度与完成结果。 */
public final class CurrentWorldImportScreen extends Screen {
    private enum State { READY, RUNNING, SUCCEEDED, FAILED }

    private final Screen parent;
    private State state = State.READY;
    private volatile int percent;
    private Button start;
    private Button close;
    private WorldSession session;
    private ClientLevel level;

    public CurrentWorldImportScreen(Screen parent) {
        super(Component.translatable("terravista.config.general.importCurrent"));
        this.parent = parent;
    }

    @Override protected void init() {
        start = addRenderableWidget(Button.builder(Component.translatable("terravista.import.start"), button -> begin())
                .bounds(width / 2 - 100, height / 2 + 32, 96, 20).build());
        close = addRenderableWidget(Button.builder(Component.translatable("terravista.import.close"), button -> onClose())
                .bounds(width / 2 + 4, height / 2 + 32, 96, 20).build());
        updateButtons();
    }

    private void begin() {
        if (state != State.READY) return;
        session = SessionLifecycle.current();
        level = minecraft.level;
        state = State.RUNNING;
        updateButtons();
        try {
            CurrentWorldImport.run((done, total) -> {
                percent = total <= 0 ? 0 : (int) Math.min(99, Math.max(0, 100L * done / total));
            }, success -> minecraft.execute(() -> finish(success)), message -> {
                ModLog.error("Import:", message.getString());
                finish(false);
            });
        } catch (Exception exception) {
            ModLog.error("Import: Current world could not be imported", exception);
            finish(false);
        }
    }

    private void finish(boolean success) {
        if (state != State.RUNNING) return;
        state = success && sameWorld() ? State.SUCCEEDED : State.FAILED;
        if (state == State.SUCCEEDED) percent = 100;
        updateButtons();
    }

    private boolean sameWorld() { return SessionLifecycle.current() == session && minecraft.level == level; }

    private void updateButtons() {
        start.active = state == State.READY;
        close.active = state != State.RUNNING;
    }

    @Override public void tick() {
        if (state == State.RUNNING && !sameWorld()) finish(false);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return state != State.RUNNING; }
    @Override public void onClose() { if (state != State.RUNNING) minecraft.gui.setScreen(parent); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = width / 2 - 100;
        int top = height / 2 - 10;
        graphics.centeredText(font, title, width / 2, top - 40, 0xffffffff);
        Component status = switch (state) {
            case READY -> Component.translatable("terravista.import.ready");
            case RUNNING -> Component.translatable("terravista.import.progress", percent);
            case SUCCEEDED -> Component.translatable("terravista.message.import_succeeded");
            case FAILED -> Component.translatable("terravista.message.import_failed");
        };
        graphics.centeredText(font, status, width / 2, top - 18, state == State.FAILED ? 0xffff7777 : 0xffffffff);
        graphics.fill(left, top, left + 200, top + 12, 0xff777777);
        graphics.fill(left + 1, top + 1, left + 199, top + 11, 0xff202020);
        graphics.fill(left + 1, top + 1, left + 1 + 198 * percent / 100, top + 11, 0xff55aa77);
    }
}
