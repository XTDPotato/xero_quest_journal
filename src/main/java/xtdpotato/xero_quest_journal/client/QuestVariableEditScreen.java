package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.io.QuestVariableStore;
import xtdpotato.xero_quest_journal.core.model.QuestVariable;

import java.util.List;
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
    private String draftName, draftValue, draftType, draftSharing, error = "";
    private CompletableFuture<QuestVariableStore.Result> pending;

    QuestVariableEditScreen(Screen parent, String taskId, String scope, QuestVariable existing,
                            BiConsumer<String, String> changed) {
        super(Component.translatable("screen.xero_quest_journal.variable.edit"));
        this.parent = parent;
        this.taskId = taskId;
        this.scope = scope;
        this.oldId = existing == null ? "" : existing.id();
        this.draftName = existing == null ? "" : existing.name();
        this.draftValue = existing == null ? "0" : existing.initial();
        this.draftType = existing == null ? "number" : existing.type();
        this.draftSharing = existing == null ? "player" : existing.sharing();
        this.changed = changed;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.variable." + key);
    }
    @Override protected void init() {
        if (name != null) { draftName = name.getValue(); draftValue = value.getValue(); }
        clearWidgets();
        int left = Math.max(12, (width - 520) / 2), w = Math.min(520, width - 24);
        name = new EditBox(font, left, 70, w, 20, text("name"));
        name.setMaxLength(64);
        name.setValue(draftName);
        name.setResponder(next -> draftName = next);
        addRenderableWidget(name);
        value = new EditBox(font, left, 120, w, 20, text("initial"));
        value.setMaxLength(4096);
        value.setValue(draftValue);
        value.setResponder(next -> draftValue = next);
        addRenderableWidget(value);
        int half = (w - 8) / 2;
        choice("number", left, 174, half / 2, draftType.equals("number"),
                () -> selectType("number"));
        choice("string", left + half / 2 + 2, 174, half - half / 2 - 2, draftType.equals("string"),
                () -> selectType("string"));
        choice("player", left + half + 8, 174, half / 2, draftSharing.equals("player"),
                () -> selectSharing("player"));
        choice("team", left + half + 8 + half / 2 + 2, 174, half - half / 2 - 2,
                draftSharing.equals("team"), () -> selectSharing("team"));
        boolean globalEdit = scope.equals("globe") && !oldId.isBlank();
        int bottom = height - 32;
        if (globalEdit) {
            action("replace_all", left, bottom - 25, (w - 4) / 2, () -> submit(false));
            action("current_only", left + (w + 4) / 2, bottom - 25, (w - 4) / 2, () -> submit(true));
        } else action("save", left, bottom - 25, w, () -> submit(false));
        action("cancel", left, bottom, w, this::onClose);
        setInitialFocus(name);
    }
    private void choice(String key, int x, int y, int width, boolean selected, Runnable action) {
        Component label = List.of("number", "string").contains(key) ? text("type." + key) : text("sharing." + key);
        var button = addRenderableWidget(new StyledButton(x, y, width, 22, label,
                ignored -> action.run(), styles, "variable-choice-" + key, "toolbar-button"));
        button.setSelectionHighlighted(selected);
        button.active = oldId.isBlank() && pending == null;
        if (!oldId.isBlank()) button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                text(key.equals("number") || key.equals("string")
                        ? "error.type_immutable" : "error.sharing_immutable")));
    }
    private void selectType(String type) {
        if (!oldId.isBlank() || pending != null) return;
        draftType = type;
        if (type.equals("number") && !numeric(draftValue)) {
            draftValue = "0";
            value.setValue(draftValue);
        }
        error = "";
        init();
    }
    private void selectSharing(String sharing) {
        if (!oldId.isBlank() || pending != null) return;
        draftSharing = sharing;
        error = "";
        init();
    }
    private void action(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(new StyledButton(x, y, w, 20, text(label),
                ignored -> action.run(), styles, "variable-" + label, "toolbar-button"));
    }
    private void submit(boolean currentOnly) {
        if (pending != null || !draftName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")
                || draftValue.length() > 4096 || draftType.equals("number") && !numeric(draftValue)) {
            error = "invalid"; return;
        }
        if (!oldId.isBlank()) {
            QuestVariable old = ClientQuestDefinitions.variables().stream()
                    .filter(variable -> variable.id().equals(oldId)).findFirst().orElse(null);
            if (old != null && !old.type().equals(draftType)) { error = "type_immutable"; return; }
            if (old != null && old.teamShared() != draftSharing.equals("team")) { error = "sharing_immutable"; return; }
        }
        Minecraft game = Minecraft.getInstance();
        var server = game.getSingleplayerServer();
        if (server == null || game.player == null) { error = "permission"; return; }
        String id = (currentOnly ? taskId : scope) + "." + draftName;
        String initial = draftValue, previous = oldId;
        UUID author = game.player.getUUID();
        String type = draftType, sharing = draftSharing;
        pending = CompletableFuture.supplyAsync(() -> QuestVariableStore.save(
                server.getPlayerList().getPlayer(author), taskId, previous, id, initial, type, sharing, currentOnly), server);
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
        int left = Math.max(12, (width - 520) / 2);
        int w = Math.min(520, width - 24);
        int half = (w - 8) / 2;
        g.drawString(font, text("name"), left, 55, 0xFFE4EAF2);
        g.drawString(font, text("initial"), left, 105, 0xFFE4EAF2);
        g.drawString(font, text("type"), left, 157, 0xFFE4EAF2);
        g.drawString(font, text("sharing"), left + half + 8, 157, 0xFFE4EAF2);
        g.fill(left, 206, left + w, 254, 0xFF242C35);
        g.renderOutline(left, 206, w, 48, numericValid() ? 0xFF5B8F73 : 0xFFB85C5C);
        g.drawString(font, text("preview"), left + 8, 210, 0xFFF1D8A3, false);
        String preview = font.plainSubstrByWidth(scope + "." + draftName + "  =  " + draftValue, w - 16);
        g.drawString(font, preview, left + 8, 222, 0xFFE4EAF2, false);
        String statusKey = !numericValid() ? "number_invalid"
                : draftSharing.equals("team") ? "team_scope_hint" : "player_scope_hint";
        int statusColor = numericValid() ? draftSharing.equals("team") ? 0xFF9ED8B1 : 0xFFB8C7D9
                : 0xFFFF9988;
        g.drawString(font, font.plainSubstrByWidth(text(statusKey).getString(), w - 16),
                left + 8, 238, statusColor, false);
        if (scope.equals("globe") && !oldId.isBlank() && error.isBlank() && pending == null)
            g.drawString(font, text("edit_scope"), left, 265, 0xFFB5C1C8);
        if (pending != null) g.drawString(font, text("saving"), left, height - 84, 0xFFF1D8A3);
        else if (!error.isBlank()) g.drawWordWrap(font, text("error." + error), left, height - 84, w, 0xFFFF9988);
        super.renderContents(g, x, y, delta);
    }
    private boolean numericValid() { return !draftType.equals("number") || numeric(draftValue); }
    private static boolean numeric(String value) {
        try { return Double.isFinite(Double.parseDouble(value)); }
        catch (RuntimeException invalid) { return false; }
    }
    @Override public void onClose() { if (pending == null) closeTo(parent); }
}
