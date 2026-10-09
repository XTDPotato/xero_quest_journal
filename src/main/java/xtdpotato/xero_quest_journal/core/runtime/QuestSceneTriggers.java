package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

/** Attachment evaluation does not replace the block or interfere with its normal ticking. */
public final class QuestSceneTriggers {
    private QuestSceneTriggers() {}
    public static void tick(ServerPlayer player) {
        evaluate(player, null);
    }
    public static void interact(ServerPlayer player, BlockPos pos) {
        evaluate(player, pos);
    }
    private static void evaluate(ServerPlayer player, BlockPos interaction) {
        if (!QuestStructures.active(player) || QuestStructures.editing(player) || !QuestStructures.current(player)
                || !player.level().dimension().equals(QuestStructures.DIMENSION)) return;
        var quest = QuestRepository.find(QuestStructures.task(player));
        if (quest != null && !QuestRuntime.allowsCompletedEvents(player, quest)) return;
        var session = QuestStructures.snapshot(player);
        var states = session.getCompound("triggerState");
        long now = player.level().getGameTime();
        for (var trigger : QuestStructures.settings(player).triggers()) {
            if (trigger.type().equals("interaction") != (interaction != null)) continue;
            var state = states.getCompound(trigger.id());
            if (!states.contains(trigger.id())) { states.put(trigger.id(), state); state.putLong("since", -1); }
            if (trigger.once() && state.getBoolean("fired") || now < state.getLong("errorUntil")) continue;
            try {
                BlockPos pos = QuestStructures.origin(player).offset(trigger.x(), trigger.y(), trigger.z());
                boolean matches = matches(player, trigger, pos, interaction, now - session.getLong("started"));
                boolean old = state.getBoolean("active");
                if (matches != old) state.putLong("since", now);
                boolean initialized = state.contains("initialized");
                if (!initialized) {
                    state.putBoolean("initialized", true);
                    state.putLong("since", now);
                }
                state.putBoolean("active", matches);
                if (matches != old) {
                    state.putBoolean("pendingRising", matches && (initialized || interaction != null));
                    state.putBoolean("pendingFalling", !matches && initialized);
                }
                boolean eligible = switch (trigger.mode()) {
                    case "falling" -> !matches && state.getBoolean("pendingFalling") && state.getBoolean("seenTrue");
                    case "rising" -> matches && state.getBoolean("pendingRising");
                    default -> matches;
                };
                if (matches) state.putBoolean("seenTrue", true);
                if (matches != old) state.putBoolean("edgeFired", false);
                if (!eligible || now - state.getLong("since") < trigger.duration()
                        || now < state.getLong("next")
                        || !trigger.mode().equals("current") && state.getBoolean("edgeFired")) continue;
                switch (trigger.action()) {
                    case "complete" -> {
                        String result = QuestStructures.complete(player);
                        if (!result.equals("completed") && !result.equals("already_complete")) continue;
                    }
                    case "unlock" -> {
                        var target = QuestRepository.find(trigger.target());
                        if (target != null) {
                            QuestProgressData.get(player.server).unlock(player.getUUID(), target);
                        } else {
                            String result = QuestRuntime.trigger(player, trigger.target(), "unlock", false);
                            if (!result.equals("unlocked")) throw new IllegalArgumentException(result);
                        }
                    }
                    case "lock" -> {
                        String result = QuestRuntime.trigger(player, trigger.target(), "lock", false);
                        if (!result.equals("locked")) throw new IllegalArgumentException(result);
                    }
                    case "message" -> player.sendSystemMessage(Component.literal(trigger.target()));
                    case "checkpoint" -> QuestStructures.checkpoint(player);
                }
                state.putBoolean("fired", true);
                state.putBoolean("edgeFired", true);
                state.putBoolean("pendingRising", false);
                state.putBoolean("pendingFalling", false);
                state.putLong("next", now + trigger.cooldown());
                if (trigger.type().equals("interaction")) state.putBoolean("active", false);
            } catch (Exception failure) {
                state.putLong("errorUntil", now + 200);
                XeroQuestJournal.LOGGER.warn("Scene trigger {} failed for {}", trigger.id(), player.getUUID(), failure);
            }
        }
        QuestStructures.dirty(player);
    }
    private static boolean matches(ServerPlayer player, QuestSceneSettings.Trigger trigger, BlockPos pos,
                                   BlockPos interaction, long elapsed) throws Exception {
        var level = player.serverLevel();
        return switch (trigger.type()) {
            case "signal" -> level.getBestNeighborSignal(pos) >= Math.max(1, trigger.count());
            case "block" -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()
                    .equals(trigger.value());
            case "container" -> {
                int count = 0;
                if (level.getBlockEntity(pos) instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        var item = container.getItem(i);
                        if (QuestConditions.matches(item, trigger.value(), player.registryAccess())) count += item.getCount();
                    }
                }
                yield count >= Math.max(1, trigger.count());
            }
            case "region" -> new AABB(pos).inflate(trigger.radius()).contains(player.position());
            case "entity" -> level.getEntities((net.minecraft.world.entity.Entity) null,
                            new AABB(pos).inflate(trigger.radius()),
                            entity -> !(entity instanceof net.minecraft.world.entity.player.Player)
                                    && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(trigger.value()))
                    .size() >= Math.max(1, trigger.count());
            case "timer" -> elapsed >= trigger.count();
            case "interaction" -> pos.equals(interaction);
            case "js" -> Boolean.TRUE.equals(QuestScripts.run(player, trigger.value().endsWith(".js")
                    ? QuestScripts.source(trigger.value()) : trigger.value(), true,
                    java.util.Map.of("sceneTask", QuestStructures.task(player), "sceneElapsed", Long.toString(elapsed),
                            "sceneX", Double.toString(player.getX() - QuestStructures.origin(player).getX()),
                            "sceneY", Double.toString(player.getY() - QuestStructures.origin(player).getY()),
                            "sceneZ", Double.toString(player.getZ() - QuestStructures.origin(player).getZ()))));
            default -> false;
        };
    }
}
