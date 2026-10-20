package dev.terravista.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** 校验手动距离并提交到设置页的待应用值。 */
public final class DistanceInputScreen extends Screen {
    private final Screen parent;
    private final IntegerOption option;
    private EditBox input;
    private Button confirm;
    private String draft;

    public DistanceInputScreen(Screen parent, IntegerOption option) {
        super(Component.translatable("terravista.config.general.distanceInput"));
        this.parent = parent;
        this.option = option;
        draft = Integer.toString(option.getValidatedValue());
    }

    @Override protected void init() {
        input = addRenderableWidget(new EditBox(font, width / 2 - 100, height / 2 - 12, 200, 20, title));
        input.setMaxLength(10);
        confirm = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> submit())
                .bounds(width / 2 - 100, height / 2 + 44, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 4, height / 2 + 44, 96, 20).build());
        input.setResponder(text -> { draft = text; confirm.active = parsedDistance() != null; });
        input.setValue(draft);
        setInitialFocus(input);
    }

    private Integer parsedDistance() {
        try {
            if (!draft.matches("[0-9]+")) return null;
            int distance = Integer.parseInt(draft);
            int minimum = Math.max(ClientOptions.MIN_DISTANCE, OptionsPages.vanillaDistance());
            return distance >= minimum && distance <= ClientOptions.MAX_DISTANCE ? distance : null;
        } catch (NumberFormatException failure) { return null; }
    }

    private void submit() {
        Integer distance = parsedDistance();
        if (distance == null) return;
        option.modifyValue(distance);
        minecraft.gui.setScreen(parent);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 54, 0xffffffff);
        var hint = Component.translatable("terravista.config.distanceRange",
                Math.max(ClientOptions.MIN_DISTANCE, OptionsPages.vanillaDistance()), ClientOptions.MAX_DISTANCE);
        graphics.centeredText(font, hint, width / 2, height / 2 + 16, confirm.active ? 0xffaaaaaa : 0xffff7777);
        graphics.centeredText(font, Component.translatable("terravista.config.distanceApplyHint"), width / 2, height / 2 + 29, 0xffaaaaaa);
    }
}
