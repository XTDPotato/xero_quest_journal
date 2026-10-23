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
        var completed = xtdpotato.xero_quest_journal.core.model.AnecdoteRewardPage.ordered(data.completedAnecdotes(player.getUUID()));
        var claimed = new java.util.ArrayList<String>();
        for (int page = 0; page < completed.size() / 10; page++) {
            var quest = rewardQuest(completed, page);
            var entry = data.entry(player.getUUID(), quest);
            if (entry != null && entry.claimed()) claimed.add(quest.id());
        }
        return new AnecdoteSnapshot(definitions,
                definitions.stream().filter(entry -> data.anecdoteDiscovered(player.getUUID(), entry.id()))
                        .map(entry -> entry.id()).toList(),
                definitions.stream().filter(entry -> data.anecdoteNew(player.getUUID(), entry.id()))
                        .map(entry -> entry.id()).toList(),
                completed, data.newAnecdoteCompletions(player.getUUID()), claimed);
    }

    private static xtdpotato.xero_quest_journal.core.model.QuestDefinition rewardQuest(
            java.util.List<AnecdoteSnapshot.Completed> ordered, int page) {
        String id = xtdpotato.xero_quest_journal.core.model.AnecdoteRewardPage.batchId(ordered, page);
        var source = AnecdoteStore.find(ordered.get(page * 10 + 9).entryId());
        var rewards = source == null ? java.util.List.of(new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward(
                "item", "minecraft:diamond", 50)) : source.rewards();
        return new xtdpotato.xero_quest_journal.core.model.QuestDefinition(id, "Anecdote reward", "", "world", "",
                false, false, 0, null, "", "", "", id, java.util.List.of(), java.util.List.of(), rewards, "manual");
    }

    public static String claimReward(ServerPlayer player, String batch) {
        requireServer(player);
        var data = QuestProgressData.get(player.getServer());
        var ordered = xtdpotato.xero_quest_journal.core.model.AnecdoteRewardPage.ordered(data.completedAnecdotes(player.getUUID()));
        for (int page = 0; page < ordered.size() / 10; page++) {
            if (!xtdpotato.xero_quest_journal.core.model.AnecdoteRewardPage.batchId(ordered, page).equals(batch)) continue;
            var quest = rewardQuest(ordered, page);
            if (data.entry(player.getUUID(), quest) == null)
                data.complete(player.getUUID(), quest, QuestRewards.freeze(player, quest.rewards()));
            String result = QuestRewards.claim(player, quest, false);
            AnecdoteNetwork.sendSnapshot(player);
            return result;
        }
        return "incomplete";
    }

    public static boolean discover(ServerPlayer player, String id) {
        requireServer(player);
        if (id == null || id.isBlank())
            id = AnecdoteStore.all().stream().findFirst().map(xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition::id)
                    .orElse("");
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
        if (entryId == null || entryId.isBlank())
            entryId = AnecdoteStore.all().stream().findFirst()
                    .map(xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition::id).orElse("");
        var entry = AnecdoteStore.find(entryId);
        if (entry == null) return false;
        if (recordId == null || recordId.isBlank())
            recordId = entry.records().stream().findFirst()
                    .map(xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition.Record::id).orElse("");
        String selectedRecordId = recordId;
        if (entry.records().stream().noneMatch(record -> record.id().equals(selectedRecordId))) return false;
        var data = QuestProgressData.get(player.getServer());
        data.observeAnecdoteCatalog(player.getUUID(), AnecdoteStore.all());
        boolean unlocked = !data.anecdoteDiscovered(player.getUUID(), entryId);
        if (unlocked) data.discoverAnecdote(player.getUUID(), entryId);
        boolean changed = data.completeAnecdoteRecord(player.getUUID(), entryId, selectedRecordId);
        if (unlocked || changed) AnecdoteNetwork.sendSnapshot(player);
        return true;
    }

    public static void completeGeneratedQuest(ServerPlayer player, String questId) {
        requireServer(player);
        var reference = AnecdoteStore.generatedRecord(questId);
        if (reference != null) completeRecord(player, reference.entryId(), reference.recordId());
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
