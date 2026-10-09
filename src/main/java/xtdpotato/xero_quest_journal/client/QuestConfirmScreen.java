package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class QuestConfirmScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final Component message;
    private final Component confirmLabel;
    private final Runnable confirm;
    private boolean decided;

    QuestConfirmScreen(Screen parent, Component message, Runnable confirm) {
        this(parent, message, Component.translatable("screen.xero_quest_journal.dialog.confirm"), confirm);
    }
    QuestConfirmScreen(Screen parent, Component message, Component confirmLabel, Runnable confirm) {
        super(confirmLabel);
        this.parent = parent;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.confirm = confirm;
    }
    @Override protected void init() {
        int w = HalfScreenDialog.width(width), x = (width - w) / 2 + 12;
        int y = (height + HalfScreenDialog.height(height)) / 2 - 34;
        var styles = ButtonStyleSheet.load();
        addRenderableWidget(new StyledButton(x, y, (w - 32) / 2, 22,
                Component.translatable("screen.xero_quest_journal.cancel"), ignored -> onClose(), styles, "overflow-cancel"));
        addRenderableWidget(new StyledButton(x + (w - 16) / 2, y, (w - 32) / 2, 22,
                confirmLabel, ignored -> {
                    if (decided) return;
                    decided = true;
                    minecraft.setScreen(parent);
                    confirm.run();
                }, styles, "overflow-confirm"));
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float tick) {
        int w = HalfScreenDialog.width(width), h = HalfScreenDialog.height(height);
        int left = (width - w) / 2, top = (height - h) / 2;
        MailDecor.frame(g, left, top, w, h, true);
        HalfScreenDialog.drawBody(g, font, java.util.List.of(message), width / 2,
                top + 12, w - 24, h - 58, 0xFF505A70);
        super.renderContents(g, x, y, tick);
    }
    @Override protected boolean isModalScreen() { return true; }
    @Override protected void renderModalBackground(GuiGraphics g, float tick) {
        if (parent != null) parent.render(g, -10000, -10000, tick);
    }
    @Override protected Viewport popupBounds() {
        int w = HalfScreenDialog.width(width), h = HalfScreenDialog.height(height);
        int left = (width - w) / 2, top = (height - h) / 2;
        return new Viewport(Math.max(0, left - 14), Math.max(0, top - 14),
                Math.min(width, left + w + 14), Math.min(height, top + h + 14));
    }
    @Override public void onClose() { if (!decided) { decided = true; closeTo(parent); } }
    @Override public boolean isPauseScreen() { return false; }
}
