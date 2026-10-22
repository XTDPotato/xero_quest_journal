package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import java.util.*;

/** Explicit tracking queues bounded server-thread searches; passive progress evaluation never locates. */
public final class QuestStructureGuidance {
    private static final Map<ServerPlayer, String> pending = new LinkedHashMap<>();
    private static final Map<ServerPlayer, Long> cooldown = new WeakHashMap<>();
    private static final Map<ServerPlayer, Map<String, String>> sent = new WeakHashMap<>();
    private static QuestLocation first(List<QuestDefinition.Trigger> conditions) {
        for (var condition : conditions) {
            if (condition.type().equals("location")) {
                var target = QuestLocation.parse(condition.item());
                if (target != null && target.kind().equals("structure")) return target;
            }
            var nested = first(condition.children()); if (nested != null) return nested;
        }
        return null;
    }
    public static String request(ServerPlayer player, String id) {
        var quest = QuestRepository.find(id);
        if (quest == null || !QuestRuntime.tracked(player).contains(id)) return "locked";
        var target = first(QuestSteps.current(QuestProgressData.get(player.server), player.getUUID(), quest).triggers());
        if (target == null) return "unavailable";
        if (!target.dimension().isBlank() && !target.dimension().equals(player.level().dimension().location().toString())) return "guidance_dimension";
        String cached = QuestControlData.get(player).guidance(player, quest, target.encode());
        if (!cached.isBlank()) { send(player, quest.id(), cached); return "guidance_ready"; }
        pending.put(player, id);
        if (player.server.getTickCount() < cooldown.getOrDefault(player, Long.MIN_VALUE)) return "guidance_cooldown";
        return "guidance_searching";
    }
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || pending.isEmpty()) return;
        pending.keySet().removeIf(p -> p.server == server && !server.getPlayerList().getPlayers().contains(p));
        var entry = pending.entrySet().stream().filter(e -> e.getKey().server == server
                && server.getTickCount() >= cooldown.getOrDefault(e.getKey(), Long.MIN_VALUE)).findFirst().orElse(null);
        if (entry == null) return;
        var player = entry.getKey(); String id = entry.getValue();
        pending.remove(player);
        cooldown.put(player, (long) server.getTickCount() + 200);
        if (player.server != server || !server.getPlayerList().getPlayers().contains(player) || !QuestRuntime.tracked(player).contains(id)) return;
        var quest = QuestRepository.find(id);
        var target = quest == null ? null : first(QuestSteps.current(QuestProgressData.get(server), player.getUUID(), quest).triggers());
        if (target == null) return;
        try (var context = new QuestGraphContext(Map.of())) {
            boolean found = QuestTravelActions.execute(player, quest, "guidance", new TriggerGraph.Node("locate", "locate_structure", 0, 0,
                    Map.of("target", target.target(), "dimension", target.dimension(), "radius", "100", "storeVariable", "globe.located_structure")), "track", "");
            player.sendSystemMessage(Component.translatable("screen.xero_quest_journal.action." + (found ? "guidance_ready" : "guidance_not_found")));
        } catch (Exception failure) {
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn("Unable to locate tracked structure {}", id, failure);
            player.sendSystemMessage(Component.translatable("screen.xero_quest_journal.action.guidance_not_found"));
        }
    }
    public static void located(ServerPlayer player, QuestDefinition quest, String structure, QuestLocation location) {
        var target = first(QuestSteps.current(QuestProgressData.get(player.server), player.getUUID(), quest).triggers());
        if (target == null || !target.target().equals(structure)) return;
        QuestControlData.get(player).guidance(player, quest, target.encode(), location.encode());
        String team = QuestControlData.teamKey(player);
        for (var member : player.server.getPlayerList().getPlayers())
            if (QuestControlData.teamKey(member).equals(team)) send(member, quest.id(), location.encode());
    }
    public static void sync(ServerPlayer player) {
        for (String id : QuestRuntime.tracked(player)) {
            var quest = QuestRepository.find(id); if (quest == null) continue;
            var target = first(QuestSteps.current(QuestProgressData.get(player.server), player.getUUID(), quest).triggers());
            if (target == null) continue;
            String cached = QuestControlData.get(player).guidance(player, quest, target.encode());
            if (!cached.isBlank() && !cached.equals(sent.getOrDefault(player, Map.of()).get(id))) send(player, id, cached);
        }
    }
    private static void send(ServerPlayer player, String id, String location) {
        PacketDistributor.sendToPlayer(player, new QuestNetwork.LocatedQuestPayload(id, location));
        sent.computeIfAbsent(player, ignored -> new HashMap<>()).put(id, location);
    }
}
