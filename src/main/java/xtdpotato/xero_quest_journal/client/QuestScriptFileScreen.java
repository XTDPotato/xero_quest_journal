package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.runtime.QuestScripts;

import java.util.concurrent.CompletableFuture;

/** Edits an existing script reference without changing the condition's file identity. */
final class QuestScriptFileScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final String reference;
    private final String expectedHash;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String draft;
    private ScriptBox editor;
    private CompletableFuture<Boolean> pending;
    private boolean failed;

    QuestScriptFileScreen(Screen parent, String reference) throws Exception {
        super(Component.literal(reference));
        this.parent = parent;
        this.reference = reference;
        draft = QuestScripts.source(reference);
        expectedHash = QuestRepository.fingerprint(draft);
    }

    @Override protected void init() {
        if (editor != null) draft = editor.getValue();
        clearWidgets();
        editor = new ScriptBox(font, 42, 42, Math.max(80, width - 54),
                Math.max(36, height - 80), title, Component.empty());
        editor.setCharacterLimit(65535);
        editor.setValue(draft);
        addRenderableWidget(editor);
        addRenderableWidget(new StyledButton(width - 126, 9, 62, 20,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "script-file-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 58, 9, 48, 20,
                Component.translatable("gui.cancel"), ignored -> onClose(),
                styles, "script-file-cancel", "toolbar-button"));
        setInitialFocus(editor);
    }

    private void save() {
        if (pending != null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        String value = editor.getValue();
        var server = minecraft.getSingleplayerServer();
        var uuid = minecraft.player.getUUID();
        pending = CompletableFuture.supplyAsync(() -> QuestScripts.editSource(
                server.getPlayerList().getPlayer(uuid), reference, expectedHash, value), server);
        failed = false;
    }

    @Override public void tick() {
        if (pending != null && pending.isDone()) {
            boolean success;
            try { success = pending.join(); } catch (RuntimeException ignored) { success = false; }
            pending = null;
            if (success) minecraft.setScreen(parent);
            else failed = true;
        }
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xFF101216);
        g.fill(0, 40, 40, height - 36, 0xFF090A0C);
        g.drawString(font, title, 12, 14, 0xFFF2CF83);
        int row = 47 - (int) editor.scroll();
        int number = 1;
        for (String line : editor.getValue().split("\\R", -1)) {
            if (row >= 42 && row < height - 38)
                g.drawString(font, Integer.toString(number), 8, row, 0xFF81909E);
            row += Math.max(1, font.split(Component.literal(line), Math.max(1, editor.getWidth() - 8)).size()) * font.lineHeight;
            number++;
        }
        if (failed) g.drawString(font, Component.translatable("screen.xero_quest_journal.graph.script_save_failed"),
                12, height - 24, 0xFFFF7777);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { save(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { if (pending == null) closeTo(parent); }

    private static final class ScriptBox extends SmoothTextBox {
        private ScriptBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height,
                          Component message, Component placeholder) {
            super(font, x, y, width, height, message, placeholder);
        }
        private double scroll() { return scrollAmount(); }
    }
}
