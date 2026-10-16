package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestPresentation;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.util.Map;
import java.util.WeakHashMap;

/** Explicit, bounded locate actions. Passive biome/structure objectives never call these searches. */
public final class QuestTravelActions {
    private static final Map<ServerPlayer, Long> NEXT_SEARCH = new WeakHashMap<>();
    private QuestTravelActions() {}

    public static boolean execute(ServerPlayer player, QuestDefinition quest, String control, TriggerGraph.Node node,
                                  String event, String target) throws Exception {
        if (node.type().equals("enter_structure")) {
            if (QuestStructures.active(player)) return false;
            String reference = node.get("structure", "");
            if (reference.isBlank()) reference = xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls().stream()
                    .filter(value -> value.id().equals(control) && value.kind().equals("structure_enter"))
                    .map(QuestPresentation.Control::value).findFirst().orElse("");
            var spawn = QuestSceneSettings.read(reference).spawn();
            var position = new QuestSceneSettings.Spawn(
                    coordinate(player, quest, control, node, "x", spawn.x(), event, target),
                    coordinate(player, quest, control, node, "y", spawn.y(), event, target),
                    coordinate(player, quest, control, node, "z", spawn.z(), event, target), spawn.yaw(), spawn.pitch());
            var entrance = new QuestPresentation.Control(control, "structure_enter", reference, 0, 0, 1, 1, "", Map.of(), TriggerGraph.empty());
            QuestStructures.enter(player, quest, entrance, position);
            PacketDistributor.sendToPlayer(player, new QuestNetwork.OpenScreenPayload("structure_entered", ""));
            return true;
        }
        // Generic teleport cannot bypass the scene inventory/return transaction or reach another player's cell.
        if (QuestStructures.active(player)) return false;
        String dimension = value(player, quest, control, node, "dimension", event, target);
        ResourceLocation id = dimension.isBlank() ? player.level().dimension().location() : ResourceLocation.tryParse(dimension);
        if (id == null) return false;
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null || level.dimension().equals(QuestStructures.DIMENSION)) return false;
        if (node.type().equals("teleport")) {
            double x = coordinate(player, quest, control, node, "x", player.getX(), event, target);
            double y = coordinate(player, quest, control, node, "y", player.getY(), event, target);
            double z = coordinate(player, quest, control, node, "z", player.getZ(), event, target);
            if (Math.abs(x) > 29999984 || Math.abs(z) > 29999984 || y < level.getMinBuildHeight()
                    || y >= level.getMaxBuildHeight() - 1 || !level.getWorldBorder().isWithinBounds(BlockPos.containing(x, y, z)))
                return false;
            var box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
            if (!level.noCollision(player, box)) return false;
            player.closeContainer(); player.stopRiding(); player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
            return true;
        }
        QuestGraphContext.put("locationFound", "false");
        for (String key : java.util.List.of("locatedX", "locatedY", "locatedZ", "locatedDimension"))
            QuestGraphContext.put(key, "");
        long now = player.server.getTickCount();
        if (now < NEXT_SEARCH.getOrDefault(player, Long.MIN_VALUE)) return false;
        NEXT_SEARCH.put(player, now + 200);
        var destinationId = ResourceLocation.tryParse(node.get("target", ""));
        if (destinationId == null) return false;
        BlockPos found;
        int radius = Integer.parseInt(node.get("radius", node.type().equals("locate_biome") ? "256" : "16"));
        if (node.type().equals("locate_biome")) {
            if (radius < 1 || radius > 2048) return false;
            var biome = level.registryAccess().registryOrThrow(Registries.BIOME)
                    .getHolder(ResourceKey.create(Registries.BIOME, destinationId));
            if (biome.isEmpty()) return false;
            var result = level.findClosestBiome3d(holder -> holder.equals(biome.get()), player.blockPosition(), radius, 32, 64);
            found = result == null ? null : result.getFirst();
        } else {
            if (radius < 1 || radius > 100) return false;
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolder(ResourceKey.create(Registries.STRUCTURE, destinationId));
            if (structure.isEmpty()) return false;
            var result = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(structure.get()), player.blockPosition(), radius, false);
            found = result == null ? null : result.getFirst();
            if (found != null && found.getY() <= level.getMinBuildHeight())
                found = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found);
        }
        if (found == null) return false;
        QuestGraphContext.put("locatedX", Double.toString(found.getX() + .5));
        QuestGraphContext.put("locatedY", Integer.toString(found.getY()));
        QuestGraphContext.put("locatedZ", Double.toString(found.getZ() + .5));
        QuestGraphContext.put("locatedDimension", id.toString());
        QuestGraphContext.put("locationFound", "true");
        var location = new QuestLocation("coords", id.toString(), "",
                found.getX() + .5, found.getY(), found.getZ() + .5, 8, true);
        String storeVariable = node.get("storeVariable", "");
        if (!storeVariable.isBlank()) {
            if (!QuestVisualPredicate.allowed(storeVariable, quest.id()))
                throw new IllegalArgumentException("Locate result variable outside task scope");
            var definition = xtdpotato.xero_quest_journal.core.io.QuestVariableStore.find(storeVariable);
            if (definition == null || definition.numeric())
                throw new IllegalArgumentException("Locate result needs a string variable");
            String encoded = new com.google.gson.Gson().toJson(new StoredLocation(
                    node.type().equals("locate_structure") ? "structure" : "biome",
                    node.get("target", ""), id.toString(), found.getX() + .5, found.getY(), found.getZ() + .5));
            QuestControlData.get(player).setVariable(player, storeVariable, encoded);
            if (storeVariable.equals("globe.located_structure")) {
                var state = QuestControlData.get(player);
                state.setVariable(player, "globe.located_structure_x", Double.toString(found.getX() + .5));
                state.setVariable(player, "globe.located_structure_y", Integer.toString(found.getY()));
                state.setVariable(player, "globe.located_structure_z", Double.toString(found.getZ() + .5));
                state.setVariable(player, "globe.located_structure_dimension", id.toString());
                state.setVariable(player, "globe.located_structure_id", node.get("target", ""));
            }
            if (definition.teamShared()) xtdpotato.xero_quest_journal.core.network.QuestControlNetwork.sendTeam(player);
            else xtdpotato.xero_quest_journal.core.network.QuestControlNetwork.sendVariables(player);
        }
        PacketDistributor.sendToPlayer(player,
                new QuestNetwork.LocatedQuestPayload(quest.id(), location.encode()));
        return true;
    }

    public record StoredLocation(String kind, String target, String dimension, double x, double y, double z) {}

    private static String value(ServerPlayer player, QuestDefinition quest, String control, TriggerGraph.Node node,
                                String key, String event, String target) {
        String source = node.get(key + "Field", "");
        if (source.isEmpty()) return node.get(key, "");
        if (source.startsWith("var:") && !QuestVisualPredicate.allowed(source.substring(4), quest.id()))
            throw new IllegalArgumentException("Coordinate variable outside task scope");
        String result = QuestVisualPredicate.field(player, source, event, target, quest.id(), control);
        if (result.isBlank()) throw new IllegalArgumentException("Coordinate source has no value");
        return result;
    }
    private static double coordinate(ServerPlayer player, QuestDefinition quest, String control, TriggerGraph.Node node,
                                     String axis, double fallback, String event, String target) {
        String value = value(player, quest, control, node, axis, event, target);
        double coordinate = value.isBlank() ? fallback : Double.parseDouble(value);
        if (!Double.isFinite(coordinate)) throw new IllegalArgumentException("Non-finite coordinate");
        return coordinate;
    }
}
