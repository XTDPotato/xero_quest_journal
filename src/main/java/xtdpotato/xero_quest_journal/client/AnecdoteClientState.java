package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.core.model.AnecdoteSnapshot;
import xtdpotato.xero_quest_journal.core.network.AnecdoteNetwork;
import xtdpotato.xero_quest_journal.core.network.QuestClientInbox;
import xtdpotato.xero_quest_journal.core.io.AnecdoteStore;

/** Remote-safe chronicle cache; all edits and discovery changes are validated on the server. */
public final class AnecdoteClientState {
    private static AnecdoteSnapshot snapshot = AnecdoteSnapshot.empty();
    private static Object connection;
    private static String lastResult = "";

    private AnecdoteClientState() {}

    public static void tick() {
        var game = Minecraft.getInstance();
        if (connection != game.getConnection()) {
            connection = game.getConnection();
            snapshot = AnecdoteSnapshot.empty();
            lastResult = "";
        }
        if (game.getSingleplayerServer() != null && snapshot.entries().isEmpty()) {
            var definitions = AnecdoteStore.all();
            if (!definitions.isEmpty()) {
                snapshot = new AnecdoteSnapshot(definitions,
                        definitions.stream().filter(AnecdoteDefinition::defaultDiscovered)
                                .map(AnecdoteDefinition::id).toList(), java.util.List.of());
            }
        }
        AnecdoteNetwork.Snapshot update;
        while ((update = QuestClientInbox.takeAnecdoteSnapshot()) != null) {
            try {
                snapshot = new com.google.gson.Gson().fromJson(update.json(), AnecdoteSnapshot.class);
                if (snapshot == null) snapshot = AnecdoteSnapshot.empty();
            } catch (RuntimeException invalid) {
                snapshot = AnecdoteSnapshot.empty();
            }
        }
        var result = QuestClientInbox.takeAnecdoteResult();
        if (result != null) {
            lastResult = result.value();
            if (lastResult.equals("saved") || lastResult.equals("deleted")) request();
        }
    }

    public static AnecdoteSnapshot snapshot() { return snapshot; }
    public static java.util.Optional<AnecdoteDefinition> find(String id) {
        return snapshot.entries().stream().filter(entry -> entry.id().equals(id)).findFirst();
    }
    public static String lastResult() { return lastResult; }
    public static void clearResult() { lastResult = ""; }

    public static boolean discovered(String id) { return snapshot.discovered().contains(id); }
    public static boolean unread(String id) { return snapshot.unread().contains(id); }
    public static String nextId() {
        for (int index = 1; index <= 100_000; index++) {
            String id = "anecdote_" + index;
            if (snapshot.entries().stream().noneMatch(entry -> entry.id().equals(id))) return id;
        }
        throw new IllegalStateException("No anecdote IDs available");
    }

    public static void request() {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new AnecdoteNetwork.Request());
    }

    public static void save(AnecdoteDefinition definition) {
        if (definition == null) return;
        PacketDistributor.sendToServer(new AnecdoteNetwork.Save(
                new com.google.gson.Gson().toJson(definition)));
    }

    public static void delete(String id) {
        PacketDistributor.sendToServer(new AnecdoteNetwork.Delete(id));
    }

    public static void markRead(String id) {
        if (unread(id)) PacketDistributor.sendToServer(new AnecdoteNetwork.MarkRead(id));
    }

    public static void markRewardsRead() {
        if (snapshot.newCompletions().isEmpty()) return;
        PacketDistributor.sendToServer(new AnecdoteNetwork.MarkRewardsRead());
    }
    public static void claimReward(String batch) {
        PacketDistributor.sendToServer(new AnecdoteNetwork.ClaimReward(batch));
    }
    public static void markRewardRecordsRead(java.util.List<String> keys) {
        if (!keys.isEmpty()) PacketDistributor.sendToServer(new AnecdoteNetwork.MarkRecordsRead(keys));
    }
}
