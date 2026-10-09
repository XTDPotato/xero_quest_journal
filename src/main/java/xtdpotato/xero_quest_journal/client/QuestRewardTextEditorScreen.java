package xtdpotato.xero_quest_journal.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Full-screen editor used by reward values that are easier to author as source:
 * server commands, JavaScript files and datapack functions.
 */
final class QuestRewardTextEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final Path file;
    private final Function<String, CompletableFuture<Boolean>> saver;
    private final Consumer<String> saved;
    private String draft;
    private MultiLineEditBox editor;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private CompletableFuture<Boolean> pending;
    private String error;

    QuestRewardTextEditorScreen(Screen parent, Component title, String initial, Path file,
                                Function<String, CompletableFuture<Boolean>> saver,
                                Consumer<String> saved) {
        super(title);
        this.parent = parent;
        this.draft = initial == null ? "" : initial;
        this.file = file;
        this.saver = saver;
        this.saved = saved;
    }

    @Override
    protected void init() {
        if (editor != null) draft = editor.getValue();
        clearWidgets();
        editor = new SmoothTextBox(font, 40, 42, width - 52, height - 78, title, Component.empty());
        editor.setCharacterLimit(1_048_576);
        editor.setValue(draft);
        addRenderableWidget(editor);

        addRenderableWidget(new StyledButton(width - (file == null ? 124 : 208), 8, 58, 22,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "reward-source-save", "toolbar-button"));
        if (file != null) {
            addRenderableWidget(new StyledButton(width - 144, 8, 76, 22,
                    Component.translatable("screen.xero_quest_journal.open_folder"), ignored ->
                    Util.getPlatform().openFile(file.getParent().toFile()),
                    styles, "reward-source-folder", "toolbar-button"));
        }
        addRenderableWidget(new EditorIconButton(width - 60, 7,
                Component.translatable("gui.cancel"), this::onClose, styles,
                "reward-source-close", "close"));
        setInitialFocus(editor);
    }

    private void save() {
        if (pending != null) return;
        String value = editor.getValue();
        try {
            pending = saver.apply(value);
            error = null;
        } catch (RuntimeException failure) {
            error = "create.error.save";
        }
    }

    @Override
    public void tick() {
        if (pending == null || !pending.isDone()) return;
        boolean successful;
        try {
            successful = Boolean.TRUE.equals(pending.join());
        } catch (Exception failure) {
            successful = false;
        }
        pending = null;
        if (successful) {
            saved.accept(editor.getValue());
            minecraft.setScreen(parent);
            return;
        } else {
            error = "create.error.save";
        }
        init();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop(graphics, 0xFF101216);
        graphics.fill(0, 40, 38, height - 34, 0xFF090A0C);
        int lineY = 46;
        for (int i = 0; i < editor.getValue().split("\\R", -1).length && lineY < height - 40; i++) {
            graphics.drawString(font, Integer.toString(i + 1), 8, lineY, 0xFF81909E);
            lineY += font.lineHeight;
        }
        graphics.drawString(font, title, 12, 14, 0xFFF2CF83);
        if (pending != null) {
            graphics.drawString(font, Component.translatable("screen.xero_quest_journal.saving"),
                    12, height - 22, 0xFFF2CF83);
        } else if (error != null) {
            graphics.drawString(font, Component.translatable("screen.xero_quest_journal." + error),
                    12, height - 22, 0xFFFF7777);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { if (pending == null) closeTo(parent); }
}
