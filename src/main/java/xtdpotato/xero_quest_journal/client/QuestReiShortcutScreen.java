package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Add or remove the task/mail item shortcuts from REI's own favorites panel. */
final class QuestReiShortcutScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String error = "";
    QuestReiShortcutScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.rei.title"));
        this.parent = parent;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.rei." + key);
    }
    @Override protected void init() {
        clearWidgets();
        int left = Math.max(16, (width - 620) / 2);
        int right = Math.min(width - 16, left + 620);
        int row = Math.max(32, height / 4);
        addRenderableWidget(Checkbox.builder(text("task"), font).pos(left, row)
                .selected(QuestReiShortcuts.contains("task"))
                .onValueChange((box, enabled) -> change("task", enabled)).build());
        addRenderableWidget(Checkbox.builder(text("mail"), font).pos(left, row + 38)
                .selected(QuestReiShortcuts.contains("mail"))
                .onValueChange((box, enabled) -> change("mail", enabled)).build());
        addRenderableWidget(new StyledButton(right - 72, height - 31, 60, 23,
                Component.translatable("gui.done"), ignored -> onClose(),
                styles, "rei-shortcuts-done"));
    }
    private void change(String kind, boolean enabled) {
        try { QuestReiShortcuts.set(kind, enabled); error = ""; }
        catch (RuntimeException failure) { error = text("save_failed").getString(); init(); }
    }
    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, 0xFF171C22);
        graphics.fill(0, 0, width, 42, 0xFF252C34);
        graphics.drawString(font, title, 16, 16, 0xFFF3D48B, false);
        graphics.drawString(font, text("summary").getString(), Math.max(16, (width - 620) / 2),
                58, 0xFF9CAEBB, false);
        if (!error.isEmpty()) graphics.drawString(font, error, 16, height - 24, 0xFFFF9B8F, false);
        super.renderContents(graphics, mouseX, mouseY, delta);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
