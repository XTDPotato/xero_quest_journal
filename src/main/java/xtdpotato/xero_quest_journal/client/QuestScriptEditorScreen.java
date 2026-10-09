package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Full-size editor for a single JS predicate draft. */
final class QuestScriptEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final Consumer<String> saved;
    private String draft;
    private MultiLineEditBox editor;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();

    QuestScriptEditorScreen(Screen parent, String initial, Consumer<String> saved) {
        this(parent, Component.translatable("screen.xero_quest_journal.graph.node.js"), initial, saved);
    }
    QuestScriptEditorScreen(Screen parent, Component title, String initial, Consumer<String> saved) {
        super(title);
        this.parent = parent;
        this.draft = initial;
        this.saved = saved;
    }

    @Override protected void init() {
        if (editor != null) draft = editor.getValue();
        clearWidgets();
        editor = new SmoothTextBox(font, 40, 42, width - 52, height - 78, title, Component.empty());
        editor.setCharacterLimit(16384);
        editor.setValue(draft);
        addRenderableWidget(editor);
        addRenderableWidget(new StyledButton(width - 124, 8, 58, 22,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "script-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 60, 8, 48, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(),
                styles, "script-cancel", "toolbar-button"));
        setInitialFocus(editor);
    }

    private void save() {
        saved.accept(editor.getValue());
        minecraft.setScreen(parent);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop(graphics, 0xFF101216);
        graphics.fill(0, 40, 38, height - 34, 0xFF090A0C);
        int lineY = 46;
        for (int i = 0; i < editor.getValue().split("\\R", -1).length && lineY < height - 40; i++) {
            graphics.drawString(font, Integer.toString(i + 1), 8, lineY, 0xFF81909E);
            lineY += font.lineHeight;
        }
        graphics.drawString(font, title, 12, 14, 0xFFF2CF83);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { save(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { closeTo(parent); }
}
