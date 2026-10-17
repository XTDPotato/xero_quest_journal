package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.io.AnecdoteStore;
import xtdpotato.xero_quest_journal.core.model.AnecdoteSnapshot;
import xtdpotato.xero_quest_journal.core.network.AnecdoteNetwork;

public final class AnecdoteRuntime {
    private AnecdoteRuntime() {}

    public static AnecdoteSnapshot snapshot(ServerPlayer player) {
        requireServer(player);
        var definitions = AnecdoteStore.all();
        var data = QuestProgressData.get(player.getServer());
        data.observeAnecdoteCatalog(player.getUUID(), definitions);
        return new AnecdoteSnapshot(definitions,
                definitions.stream().filter(entry -> data.anecdoteDiscovered(player.getUUID(), entry.id()))
                        .map(entry -> entry.id()).toList(),
                definitions.stream().filter(entry -> data.anecdoteNew(player.getUUID(), entry.id()))
                        .map(entry -> entry.id()).toList(),
                data.completedAnecdotes(player.getUUID()), data.newAnecdoteCompletions(player.getUUID()));
    }

    public static boolean discover(ServerPlayer player, String id) {
        requireServer(player);
        if (AnecdoteStore.find(id) == null) return false;
        boolean changed = QuestProgressData.get(player.getServer()).discoverAnecdote(player.getUUID(), id);
        if (changed) AnecdoteNetwork.sendSnapshot(player);
        return true;
    }

    public static void markRead(ServerPlayer player, String id) {
        requireServer(player);
        QuestProgressData.get(player.getServer()).markAnecdoteRead(player.getUUID(), id);
        AnecdoteNetwork.sendSnapshot(player);
    }

    public static boolean completeRecord(ServerPlayer player, String entryId, String recordId) {
        requireServer(player);
        var entry = AnecdoteStore.find(entryId);
        if (entry == null || entry.records().stream().noneMatch(record -> record.id().equals(recordId))) return false;
        var data = QuestProgressData.get(player.getServer());
        data.observeAnecdoteCatalog(player.getUUID(), AnecdoteStore.all());
        if (!data.anecdoteDiscovered(player.getUUID(), entryId)) return false;
        boolean changed = data.completeAnecdoteRecord(player.getUUID(), entryId, recordId);
        if (changed) AnecdoteNetwork.sendSnapshot(player);
        return true;
    }

    public static void markRewardsRead(ServerPlayer player) {
        requireServer(player);
        var data = QuestProgressData.get(player.getServer());
        data.markAnecdoteCompletionsRead(player.getUUID(), data.newAnecdoteCompletions(player.getUUID()));
        AnecdoteNetwork.sendSnapshot(player);
    }

    private static void requireServer(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("Anecdotes require a connected server player on the server thread");
    }
}
