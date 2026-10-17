package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class AnecdoteRecordEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final AnecdoteDefinition entry;
    private final AnecdoteDefinition.Record initial;
    private final boolean creating;
    private final Consumer<AnecdoteDefinition> save;
    private EditBox title;
    private EditBox completedIcon;
    private SmoothTextBox body;
    private ButtonStyleSheet styles;

    AnecdoteRecordEditorScreen(Screen parent, AnecdoteDefinition entry, AnecdoteDefinition.Record initial,
                               boolean creating, Consumer<AnecdoteDefinition> save) {
        super(Component.translatable("screen.xero_quest_journal.anecdote.edit_record"));
        this.parent = parent;
        this.entry = entry;
        this.initial = initial;
        this.creating = creating;
        this.save = save;
    }

    private Component text(String key) { return Component.translatable("screen.xero_quest_journal.anecdote." + key); }

    @Override protected void init() {
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int width = Math.min(this.width - 32, 820), left = (this.width - width) / 2;
        title = new EditBox(font, left + 18, 72, width - 36, 22, text("record_title"));
        title.setMaxLength(256);
        title.setValue(initial.title());
        addRenderableWidget(title);
        completedIcon = new EditBox(font, left + 18, 104, width - 36, 20, text("completed_icon"));
        completedIcon.setMaxLength(2048);
        completedIcon.setValue(initial.completedIcon());
        addRenderableWidget(completedIcon);
        addRenderableWidget(new StyledButton(left + width - 42, 104, 24, 20, Component.literal("..."),
                ignored -> minecraft.setScreen(QuestScreen.pickResource(this, completedIcon.getValue(), false,
                        completedIcon::setValue)), styles, "anecdote-completion-icon", "toolbar-button"));
        body = new SmoothTextBox(font, left + 18, 136, width - 36, Math.max(70, height - 200),
                text("record_body"), text("record_body_hint"));
        body.setCharacterLimit(8192);
        body.setValue(initial.body());
        addRenderableWidget(body);
        addRenderableWidget(new StyledButton(left + width - 158, height - 42, 66, 24,
                text("save"), ignored -> commit(), styles, "anecdote-record-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(left + width - 82, height - 42, 64, 24,
                text("cancel"), ignored -> onClose(), styles, "anecdote-record-cancel", "toolbar-button"));
    }

    private void commit() {
        String value = title.getValue().strip();
        if (value.isBlank()) {
            XeroTips.show(text("record_title_required"));
            return;
        }
        var result = new AnecdoteDefinition.Record(initial.id(), value, body.getValue(), completedIcon.getValue());
        var records = new ArrayList<>(entry.records());
        if (creating) records.add(result);
        else {
            int index = -1;
            for (int i = 0; i < records.size(); i++) if (records.get(i).id().equals(initial.id())) index = i;
            if (index >= 0) records.set(index, result);
            else records.add(result);
        }
        save.accept(new AnecdoteDefinition(entry.id(), entry.title(), entry.cardText(), entry.icon(), entry.heading(),
                records, entry.defaultDiscovered(), entry.hideWhenLocked(), entry.order()));
        minecraft.setScreen(parent);
    }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xFF202A3B);
        int panelW = Math.min(width - 24, 860), left = (width - panelW) / 2;
        g.fill(left, 36, left + panelW, height - 28, 0xFFF2EBDC);
        g.renderOutline(left, 36, panelW, height - 64, 0xFFC6B18A);
        g.drawString(font, text(creating ? "record_create" : "record_edit"), left + 18, 48, 0xFF51586B, false);
        super.renderContents(g, mx, my, delta);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
}
