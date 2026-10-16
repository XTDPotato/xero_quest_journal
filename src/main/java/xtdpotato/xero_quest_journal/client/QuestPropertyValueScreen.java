package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Enlarged plain-value editor for graph properties (no rich-text markup insertion). */
final class QuestPropertyValueScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final Consumer<String> saved;
    private String draft;
    private MultiLineEditBox editor;
    private final int limit;

    QuestPropertyValueScreen(Screen parent, Component title, String initial, Consumer<String> saved) {
        this(parent, title, initial, saved, 4096);
    }
    QuestPropertyValueScreen(Screen parent, Component title, String initial, Consumer<String> saved, int limit) {
        super(title);
        this.parent = parent;
        this.saved = saved;
        this.draft = initial;
        this.limit = limit;
    }
    @Override protected void init() {
        if (editor != null) draft = editor.getValue();
        clearWidgets();
        editor = new SmoothTextBox(font, 12, 35, width - 24, height - 74, title, Component.empty());
        editor.setCharacterLimit(Math.max(limit, draft.length()));
        editor.setValue(draft);
        addRenderableWidget(editor);
        var styles = ButtonStyleSheet.load();
        addRenderableWidget(new StyledButton(width - 140, height - 29, 62, 22,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> {
                    saved.accept(editor.getValue());
                    minecraft.setScreen(parent);
                }, styles, "property-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 72, height - 29, 60, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(),
                styles, "property-cancel", "toolbar-button"));
        setInitialFocus(editor);
    }
    @Override protected void renderContents(GuiGraphics graphics, int x, int y, float delta) {
        backdrop(graphics, 0xFF161A20);
        graphics.drawString(font, title, 12, 12, 0xFFF0D59B, false);
        super.renderContents(graphics, x, y, delta);
    }
    @Override public void onClose() { closeTo(parent); }
}
