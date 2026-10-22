package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;

final class TemplateGroupScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private EditBox name;
    private String draft = "", error = "";
    private CompletableFuture<String> pending;
    TemplateGroupScreen(Screen parent) { super(QuestTemplateEditorScreen.text("add_group")); this.parent = parent; }
    protected void init() {
        clearWidgets(); int w = Math.min(320, width - 40), x = (width - w) / 2, y = height / 2 - 30;
        name = new EditBox(font, x, y, w, 22, QuestTemplateEditorScreen.text("group"));
        name.setMaxLength(128); name.setValue(draft); name.setResponder(value -> draft = value);
        name.setEditable(pending == null); addRenderableWidget(name);
        var save = new StyledButton(x, y + 36, (w - 8) / 2, 22, QuestTemplateEditorScreen.text("save"),
                ignored -> { if (!draft.isBlank()) { pending = ClientQuestProgress.changeTemplateLibrary("add_group", draft.strip(), "", false); init(); } }, styles, "template-group-save");
        save.active = pending == null; addRenderableWidget(save);
        addRenderableWidget(new StyledButton(x + (w + 8) / 2, y + 36, (w - 8) / 2, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "template-group-cancel"));
        addRenderableWidget(new EditorIconButton(width - 36, 8, Component.translatable("gui.cancel"), this::onClose, styles, "template-group-close", "close"));
    }
    public void tick() {
        if (pending != null && pending.isDone()) {
            try { error = pending.join(); } catch (Exception failure) { error = "save"; }
            pending = null;
            if (error.isEmpty()) minecraft.setScreen(parent); else init();
        }
    }
    protected void renderContents(GuiGraphics g, int x, int y, float dt) {
        backdrop(g, JOURNAL_BACKDROP); super.renderContents(g, x, y, dt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFF2D29A);
        if (!error.isEmpty()) g.drawCenteredString(font, QuestTemplateEditorScreen.text("error." + error), width / 2, height / 2 + 40, 0xFFFF9999);
    }
    public void onClose() { closeTo(parent); }
}
