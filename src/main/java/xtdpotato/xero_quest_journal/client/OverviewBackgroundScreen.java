package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestUiTheme;

import java.util.concurrent.CompletableFuture;

/** Background authoring uses the same server-owned defaults as the journal theme. */
public final class OverviewBackgroundScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String source = ClientQuestProgress.uiTheme().overviewImage();
    private EditBox field;
    private CompletableFuture<String> pending;
    private String message = "";

    public OverviewBackgroundScreen(Screen parent) {
        super(text("edit"));
        this.parent = parent;
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.overview_background." + key);
    }

    static void renderImage(GuiGraphics graphics, String source, int width, int height) {
        graphics.fill(0, 0, width, height, 0xFF292C32);
        ClientMediaManager.renderImageCover(graphics, QuestUiTheme.DEFAULT_OVERVIEW_IMAGE, 0, 0, width, height, 1);
        if (!source.isBlank() && !source.equals(QuestUiTheme.DEFAULT_OVERVIEW_IMAGE))
            ClientMediaManager.renderImageCover(graphics, source, 0, 0, width, height, 1);
    }

    @Override protected boolean blurWorldBackground() { return true; }
    @Override protected void renderBeforeBlur(GuiGraphics graphics, float delta) {
        renderImage(graphics, source, width, height);
    }

    @Override protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets();
        int w = Math.min(460, width - 32), x = (width - w) / 2;
        field = new EditBox(font, x, height - 88, w - 30, 20, text("source"));
        field.setMaxLength(2048);
        field.setValue(source);
        field.setResponder(value -> source = value.strip());
        addRenderableWidget(field);
        addRenderableWidget(new EditorIconButton(x + w - 24, height - 90, text("choose"), () ->
                minecraft.setScreen(QuestScreen.pickResource(this, source, false, value -> source = value)),
                styles, "overview-background-pick", "minecraft:painting"));
        int part = (w - 12) / 3;
        addRenderableWidget(new StyledButton(x, height - 42, part, 22, text("reset"),
                ignored -> { source = ""; field.setValue(""); }, styles, "overview-background-reset"));
        addRenderableWidget(new StyledButton(x + part + 6, height - 42, part, 22,
                Component.translatable("gui.done"), ignored -> save(), styles, "overview-background-save"));
        addRenderableWidget(new StyledButton(x + (part + 6) * 2, height - 42, part, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "overview-background-cancel"));
        if (pending != null) children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).forEach(widget -> widget.active = false);
    }

    private void save() {
        if (pending != null) return;
        try {
            QuestUiTheme.validateOverviewImage(source);
            if (!source.isBlank() && !QuestIcons.valid(source)) throw new IllegalArgumentException("Missing image");
            pending = ClientQuestProgress.setUiTheme(ClientQuestProgress.uiTheme().withOverviewImage(source));
            message = "";
            init();
        } catch (IllegalArgumentException invalid) {
            message = text("invalid").getString();
        }
    }

    @Override public void tick() {
        if (pending == null || !pending.isDone()) return;
        String result = pending.join();
        pending = null;
        if (result.equals("config_saved")) closeTo(parent);
        else {
            message = Component.translatable("screen.xero_quest_journal.action." + result).getString();
            init();
        }
    }

    @Override protected void renderContents(GuiGraphics graphics, int x, int y, float delta) {
        backdrop(graphics, 0x55181B23);
        graphics.fill(0, height - 116, width, height, 0xE521252D);
        graphics.drawString(font, title, 18, 16, 0xFFF4DCB1, false);
        int left = (width - Math.min(460, width - 32)) / 2;
        graphics.drawString(font, text("source"), left, height - 103, 0xFFFFFFFF, false);
        if (!message.isBlank()) graphics.drawString(font, font.plainSubstrByWidth(message, width - left * 2),
                left, height - 61, 0xFFFFA6A6, false);
        super.renderContents(graphics, x, y, delta);
    }
    @Override public void onClose() { if (pending == null) closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
