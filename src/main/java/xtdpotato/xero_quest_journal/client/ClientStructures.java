package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.network.QuestStructureNetwork;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructures;
import java.util.*;

public final class ClientStructures {
    private static final Map<String, CompoundTag> models = new HashMap<>();
    private static final Map<String, String> errors = new HashMap<>();
    private static final Map<String, Long> requested = new HashMap<>();
    private static Object connection;
    private static long revision = -1;
    private ClientStructures() {}
    public static void tick() {
        var game = Minecraft.getInstance();
        if (connection != game.getConnection()) while (QuestStructureNetwork.take() != null) { }
        if (connection != game.getConnection() || revision != ClientQuestDefinitions.revision()) {
            connection = game.getConnection(); revision = ClientQuestDefinitions.revision();
            models.clear(); errors.clear(); requested.clear(); StructurePreviewRenderer.clear();
        }
        QuestStructureNetwork.Preview response;
        while ((response = QuestStructureNetwork.take()) != null) {
            if (response.error().isEmpty()) models.put(response.reference(), response.data());
            else {
                if (!response.reference().isBlank()) errors.put(response.reference(), response.error());
                XeroTips.show(net.minecraft.network.chat.Component.translatable(
                        "screen.xero_quest_journal." + response.error()));
            }
        }
    }
    public static CompoundTag model(String reference) { return models.get(reference); }
    public static void cachePreview(String reference, CompoundTag data) {
        models.put(reference, data);
        errors.remove(reference);
        StructurePreviewRenderer.clear();
    }
    public static String error(String reference) { return errors.getOrDefault(reference, "structure.loading"); }
    public static void request(String quest, String control, String reference) {
        if (models.containsKey(reference) || errors.containsKey(reference)) return;
        long now = System.nanoTime();
        if (now - requested.getOrDefault(reference, 0L) < 3_000_000_000L) return;
        requested.put(reference, now);
        var game = Minecraft.getInstance();
        // An author's unpublished draft is previewable locally, but cannot enter a server scene until published.
        if (game.getSingleplayerServer() != null && game.player != null && game.player.hasPermissions(2)) {
            try { models.put(reference, QuestStructures.preview(QuestStructures.read(reference))); }
            catch (Exception invalid) { errors.put(reference, "structure.invalid"); }
        } else PacketDistributor.sendToServer(new QuestStructureNetwork.Request(quest, control, 0, ClientQuestProgress.catalogRevision()));
    }
    public static void enter(String quest, String control) {
        PacketDistributor.sendToServer(new QuestStructureNetwork.Request(quest, control, 1, ClientQuestProgress.catalogRevision()));
    }
    public static boolean inScene() {
        var game = Minecraft.getInstance();
        return game.player != null && game.level != null && game.level.dimension().equals(QuestStructures.DIMENSION);
    }
    public static void leave() { PacketDistributor.sendToServer(new QuestStructureNetwork.Request("", "", 2, ClientQuestProgress.catalogRevision())); }
    public static void reset() { PacketDistributor.sendToServer(new QuestStructureNetwork.Request("", "", 3, ClientQuestProgress.catalogRevision())); }
}
