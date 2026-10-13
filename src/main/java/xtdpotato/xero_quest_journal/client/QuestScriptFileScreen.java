package xtdpotato.xero_quest_journal.client;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind;
import xtdpotato.xero_quest_journal.core.runtime.QuestScripts;

/** Existing file editing retains the original reference and optimistic conflict check. */
final class QuestScriptFileScreen extends ScriptEditorScreenBase {
    private final String reference;
    private final String expectedHash;
    private CompletableFuture<Boolean> pending;

    QuestScriptFileScreen(Screen parent, String reference) throws Exception {
        this(parent, reference, Kind.CONDITION_FILE);
    }
    QuestScriptFileScreen(Screen parent, String reference, Kind kind) throws Exception {
        this(parent, reference, kind, QuestScripts.source(reference));
    }
    private QuestScriptFileScreen(Screen parent, String reference, Kind kind, String source) {
        super(parent, Component.literal(reference), source, kind, "script-file-save");
        this.reference = reference;
        expectedHash = QuestRepository.fingerprint(source);
    }
    @Override protected boolean canSave() {
        return pending == null && minecraft != null && minecraft.player != null
                && minecraft.getSingleplayerServer() != null && minecraft.player.hasPermissions(2);
    }
    @Override protected void saveDraft(String source) {
        var server = minecraft.getSingleplayerServer();
        var uuid = minecraft.player.getUUID();
        pending = CompletableFuture.supplyAsync(() -> QuestScripts.editSource(
                server.getPlayerList().getPlayer(uuid), reference, expectedHash, source), server);
    }
    @Override public void tick() {
        if (pending == null || !pending.isDone()) return;
        boolean success;
        try { success = pending.join(); } catch (RuntimeException failure) { success = false; }
        pending = null;
        if (success) minecraft.setScreen(parent);
        else showSaveError();
    }
    @Override public void onClose() { if (pending == null) super.onClose(); }
}
