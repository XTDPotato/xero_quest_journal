package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.io.QuestVariableStore;
import xtdpotato.xero_quest_journal.core.model.QuestVariable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Edits definitions on the integrated server; never mutates a client-side catalog. */
final class QuestVariableEditScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final String taskId, scope, oldId;
    private final BiConsumer<String, String> changed;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private EditBox name, value;
    private String draftName, draftValue, error = "";
    private CompletableFuture<QuestVariableStore.Result> pending;

    QuestVariableEditScreen(Screen parent, String taskId, String scope, QuestVariable existing,
                            BiConsumer<String, String> changed) {
        super(Component.translatable("screen.xero_quest_journal.variable.edit"));
        this.parent = parent;
        this.taskId = taskId;
        this.scope = scope;
        this.oldId = existing == null ? "" : existing.id();
        this.draftName = existing == null ? "" : existing.name();
        this.draftValue = existing == null ? "" : existing.initial();
        this.changed = changed;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.variable." + key);
    }
    @Override protected void init() {
        if (name != null) { draftName = name.getValue(); draftValue = value.getValue(); }
        clearWidgets();
        int left = Math.max(12, (width - 310) / 2), w = Math.min(310, width - 24);
        name = new EditBox(font, left, 67, w, 20, text("name"));
        name.setMaxLength(64);
        name.setValue(draftName);
        name.setResponder(next -> draftName = next);
        addRenderableWidget(name);
        value = new EditBox(font, left, 113, w, 20, text("initial"));
        value.setMaxLength(4096);
        value.setValue(draftValue);
        value.setResponder(next -> draftValue = next);
        addRenderableWidget(value);
        boolean globalEdit = scope.equals("globe") && !oldId.isBlank();
        int bottom = height - 32;
        if (globalEdit) {
            action("replace_all", left, bottom - 25, (w - 4) / 2, () -> submit(false));
            action("current_only", left + (w + 4) / 2, bottom - 25, (w - 4) / 2, () -> submit(true));
        } else action("save", left, bottom - 25, w, () -> submit(false));
        action("cancel", left, bottom, w, this::onClose);
        setInitialFocus(name);
    }
    private void action(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(new StyledButton(x, y, w, 20, text(label),
                ignored -> action.run(), styles, "variable-" + label, "toolbar-button"));
    }
    private void submit(boolean currentOnly) {
        if (pending != null || !draftName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")
                || draftValue.length() > 4096) { error = "invalid"; return; }
        Minecraft game = Minecraft.getInstance();
        var server = game.getSingleplayerServer();
        if (server == null || game.player == null) { error = "permission"; return; }
        String id = (currentOnly ? taskId : scope) + "." + draftName;
        String initial = draftValue, previous = oldId;
        UUID author = game.player.getUUID();
        pending = CompletableFuture.supplyAsync(() -> QuestVariableStore.save(
                server.getPlayerList().getPlayer(author), taskId, previous, id, initial, currentOnly), server);
    }
    @Override public void tick() {
        super.tick();
        if (pending == null || !pending.isDone()) return;
        try {
            QuestVariableStore.Result result = pending.join();
            if (!result.successful()) error = result.error();
            else {
                changed.accept(oldId, result.id());
                minecraft.setScreen(parent);
            }
        } catch (Exception invalid) { error = "save_failed"; }
        finally { pending = null; }
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xFF1D2228);
        g.drawString(font, title, 12, 12, 0xFFF1D8A3, false);
        int left = Math.max(12, (width - 310) / 2);
        g.drawString(font, text("name"), left, 53, 0xFFE4EAF2);
        g.drawString(font, text("initial"), left, 99, 0xFFE4EAF2);
        int w = Math.min(310, width - 24);
        g.drawString(font, font.plainSubstrByWidth(scope + "." + draftName, w), left, 145, 0xFFAEC3CB);
        if (scope.equals("globe") && !oldId.isBlank() && error.isBlank() && pending == null)
            g.drawString(font, text("edit_scope"), left, 163, 0xFFB5C1C8);
        if (pending != null) g.drawString(font, text("saving"), left, height - 84, 0xFFF1D8A3);
        else if (!error.isBlank()) g.drawWordWrap(font, text("error." + error), left, height - 84, w, 0xFFFF9988);
        super.renderContents(g, x, y, delta);
    }
    @Override public void onClose() { if (pending == null) closeTo(parent); }
}
