package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Local author UI; every read/write that accesses a live player runs on the server thread. */
public final class QuestSceneSettingsScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final String reference;
    private final boolean editingCopy;
    private String draft, expected, error = "";
    private CompletableFuture<String> pending;
    private boolean saving;
    private MultiLineEditBox editor;
    private EditBox exitDelay;
    private ButtonStyleSheet styles;

    public QuestSceneSettingsScreen(Screen parent, String reference, boolean editingCopy) {
        super(text("settings"));
        this.parent = parent; this.reference = reference; this.editingCopy = editingCopy;
    }
    private static Component text(String key) { return Component.translatable("screen.xero_quest_journal.structure." + key); }
    @Override protected void init() {
        if (editor != null) draft = editor.getValue();
        clearWidgets();
        styles = ButtonStyleSheet.load();
        if (draft == null && pending == null) {
            var server = minecraft.getSingleplayerServer();
            if (server == null || minecraft.player == null) { error = "structure.permission"; return; }
            var id = minecraft.player.getUUID();
            pending = CompletableFuture.supplyAsync(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(id);
                    QuestStructureLibrary.authorize(player);
                    return editingCopy ? QuestStructures.editSettingsSource(player) : QuestSceneSettings.source(reference);
                } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
            }, server);
        }
        if (draft != null) {
            QuestSceneSettings parsed;
            try { parsed = QuestSceneSettings.parse(draft); }
            catch (Exception invalid) { parsed = QuestSceneSettings.defaults(); }
            int w = Math.max(80, width - 32), selector = Math.max(80, Math.min(260, w / 2));
            choice(36, "inventory", List.of("empty", "player_copy", "task"), parsed.inventory(), selector);
            choice(62, "interaction", List.of("view", "interact", "build", "regions"), parsed.interaction(), selector);
            choice(88, "death", List.of("return", "spawn", "checkpoint"), parsed.death(), selector);
            exitDelay = new EditBox(font, width - 100, 108, 84, 20,
                    text("exit_delay"));
            exitDelay.setValue(Integer.toString(parsed.exitDelay()));
            exitDelay.setHint(text("exit_delay_hint"));
            addRenderableWidget(exitDelay);
            editor = new TopEditor(font, 16, 130, w, Math.max(24, height - 172), text("advanced"), Component.empty());
            editor.setCharacterLimit(16000);
            editor.setValue(draft);
            addRenderableWidget(editor);
            addRenderableWidget(new StyledButton(width - 156, height - 30, 66, 20,
                    Component.translatable("screen.xero_quest_journal.save"), b -> save(), styles, "scene-settings-save"));
        }
        addRenderableWidget(new StyledButton(width - 82, height - 30, 66, 20,
                Component.translatable("screen.xero_quest_journal.cancel"), b -> onClose(), styles, "scene-settings-close"));
    }
    private void choice(int y, String key, List<String> values, String initial, int w) {
        addRenderableWidget(new StyledChoiceButton<>(width - 16 - w, y, w, 20, text(key),
                values, initial, QuestSceneSettingsScreen::text, value -> {
                    try {
                        var json = com.google.gson.JsonParser.parseString(editor.getValue()).getAsJsonObject();
                        json.addProperty(key, value);
                        draft = QuestSceneSettings.JSON.toJson(QuestSceneSettings.parse(json.toString()));
                        editor.setValue(draft);
                    } catch (Exception invalid) { error = "structure.settings_invalid"; }
                }, styles, "scene-" + key));
    }
    private void save() {
        if (pending != null || editor == null) return;
        try {
            var json = com.google.gson.JsonParser.parseString(editor.getValue()).getAsJsonObject();
            json.addProperty("exitDelay", Integer.parseInt(exitDelay.getValue().strip()));
            draft = QuestSceneSettings.JSON.toJson(QuestSceneSettings.parse(json.toString()));
            var server = minecraft.getSingleplayerServer();
            if (server == null || minecraft.player == null) throw new IllegalArgumentException("structure.permission");
            var id = minecraft.player.getUUID();
            String savedDraft = draft, savedHash = expected;
            saving = true;
            pending = CompletableFuture.supplyAsync(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(id);
                    if (editingCopy) QuestStructures.editSettings(player, savedHash, savedDraft);
                    else QuestSceneSettings.save(player, reference, savedHash, savedDraft);
                    return savedDraft;
                } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
            }, server);
        } catch (Exception invalid) { error = "structure.settings_invalid"; }
    }
    @Override public void tick() {
        if (pending == null || !pending.isDone()) return;
        try {
            draft = pending.join();
            expected = QuestRepository.fingerprint(draft);
            if (saving) { pending = null; minecraft.setScreen(parent); return; }
        } catch (Exception e) {
            String key = e.getCause() == null ? null : e.getCause().getMessage();
            error = key != null && key.startsWith("structure.") ? key : "structure.settings_invalid";
        }
        pending = null; saving = false;
        // A failed initial load must not loop and submit another read.
        if (draft != null) init();
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xFF22282C);
        g.drawString(font, title, 16, 14, 0xFFFFFFFF, false);
        for (int i = 0; i < 3; i++)
            g.drawString(font, text(List.of("inventory", "interaction", "death").get(i)), 16, 42 + i * 26, 0xFFEEEEEE, false);
        g.drawString(font, text("exit_delay"), width - 184, 114, 0xFFBBCBC2, false);
        g.drawString(font, text("advanced"), 16, 116, 0xFFBBCBC2, false);
        if (!error.isEmpty()) g.drawString(font, font.plainSubstrByWidth(Component.translatable(
                "screen.xero_quest_journal." + error).getString(), Math.max(20, width - 185)), 16, height - 25, 0xFFFF9E91);
        super.renderContents(g, x, y, delta);
    }
    @Override public void onClose() { if (!saving) closeTo(parent); }
    private static final class TopEditor extends SmoothTextBox {
        private TopEditor(Font font, int x, int y, int width, int height, Component title, Component placeholder) {
            super(font, x, y, width, height, title, placeholder);
        }
        @Override public void setValue(String value) {
            super.setValue(value);
            setScrollAmount(0);
        }
    }
}
