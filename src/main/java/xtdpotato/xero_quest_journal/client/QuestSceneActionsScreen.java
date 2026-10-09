package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.util.concurrent.CompletableFuture;

public final class QuestSceneActionsScreen extends AnimatedQuestScreen {
    private record State(boolean editing, String reference, String result) {}
    private final Screen parent;
    private CompletableFuture<State> pending;
    private State state;
    private String message = "";
    private ButtonStyleSheet styles;
    public QuestSceneActionsScreen(Screen parent) { super(text("settings")); this.parent = parent; }
    private static Component text(String key) { return Component.translatable("screen.xero_quest_journal.structure." + key); }
    @Override protected void init() {
        clearWidgets(); styles = ButtonStyleSheet.load();
        int x = width / 2 - 110, y = Math.max(36, height / 2 - 74);
        button(x, y, "leave", () -> { ClientStructures.leave(); minecraft.setScreen(null); });
        if (state == null && pending == null && minecraft.getSingleplayerServer() != null && minecraft.player != null) {
            var server = minecraft.getSingleplayerServer(); var id = minecraft.player.getUUID();
            pending = CompletableFuture.supplyAsync(() -> {
                var p = server.getPlayerList().getPlayer(id);
                return new State(p != null && QuestStructures.editing(p), p == null ? "" : QuestStructures.reference(p), "");
            }, server);
        }
        if (state != null && state.editing) {
            button(x, y + 26, "settings", () -> minecraft.setScreen(new QuestSceneSettingsScreen(this, state.reference, true)));
            button(x, y + 52, "draft", () -> save(true));
            button(x, y + 78, "version", () -> save(false));
        } else button(x, y + 26, "reset", () -> minecraft.setScreen(new QuestConfirmScreen(this,
                text("reset"), Component.translatable("screen.xero_quest_journal.dialog.confirm"),
                () -> { ClientStructures.reset(); minecraft.setScreen(null); })));
        addRenderableWidget(new StyledButton(width - 42, 10, 28, 20, Component.literal("x"),
                b -> onClose(), styles, "scene-close"));
    }
    private void button(int x, int y, String key, Runnable run) {
        addRenderableWidget(new StyledButton(x, y, 220, 22, text(key), b -> run.run(), styles, "scene-" + key));
    }
    private void save(boolean draft) {
        if (pending != null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        var server = minecraft.getSingleplayerServer(); var id = minecraft.player.getUUID();
        pending = CompletableFuture.supplyAsync(() -> {
            try { return new State(true, state.reference, QuestStructureLibrary.saveEdit(server.getPlayerList().getPlayer(id), draft)); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }, server);
    }
    @Override public void tick() {
        if (pending == null || !pending.isDone()) return;
        try {
            state = pending.join();
            if (!state.result.isEmpty()) message = Component.translatable(
                    "screen.xero_quest_journal.structure.saved_copy", state.result).getString();
        } catch (Exception failure) { message = text("invalid").getString(); }
        pending = null; init();
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xD922282C);
        g.drawString(font, title, 16, 14, 0xFFFFFFFF, false);
        if (!message.isBlank()) g.drawWordWrap(font, Component.literal(message), 16, height - 48, Math.max(40, width - 32), 0xFFBDE7CA);
        super.renderContents(g, x, y, delta);
    }
    @Override public void onClose() { closeTo(parent); }
}
