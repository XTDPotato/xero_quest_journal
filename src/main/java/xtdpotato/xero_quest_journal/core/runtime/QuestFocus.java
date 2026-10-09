package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

/** A player may focus one group; the group's active quest is its current HUD target. */
public final class QuestFocus {
    private QuestFocus() {}

    public static String group(ServerPlayer player) {
        var data = QuestProgressData.get(player.server);
        String groupId = data.focusGroup(player.getUUID());
        if (groupId.isBlank()) {
            // Migrate a legacy task focus only when its group opted into the new group-level setting.
            QuestDefinition legacy = QuestRepository.find(data.focused(player.getUUID()));
            if (legacy != null) {
                String legacyGroup = QuestRepository.groupOf(legacy.id());
                var definition = QuestRepository.groups().stream()
                        .filter(value -> value.id().equals(legacyGroup)).findFirst().orElse(null);
                if (definition != null && definition.focusAvailable()) {
                    data.focusGroup(player.getUUID(), legacyGroup);
                    groupId = legacyGroup;
                }
            }
        }
        String validatedGroupId = groupId;
        boolean valid = QuestRepository.groups().stream()
                .anyMatch(value -> value.id().equals(validatedGroupId) && value.focusAvailable());
        if (!groupId.isBlank() && !valid) {
            data.focusGroup(player.getUUID(), "");
            data.focused(player.getUUID(), "");
            return "";
        }
        return groupId;
    }

    public static String current(ServerPlayer player) {
        var data = QuestProgressData.get(player.server);
        String groupId = group(player);
        if (groupId.isBlank()) return "";
        String id = data.focused(player.getUUID());
        QuestDefinition quest = QuestRepository.find(id);
        if (!eligible(player, groupId, quest)) {
            quest = firstEligible(player, groupId);
            id = quest == null ? "" : quest.id();
            data.focused(player.getUUID(), id);
        }
        return id;
    }

    public static boolean allows(ServerPlayer player, QuestDefinition quest) {
        String groupId = group(player);
        if (groupId.isBlank()) return true;
        return QuestRepository.groupOf(quest.id()).equals(groupId) && current(player).equals(quest.id());
    }

    public static boolean allowsReward(ServerPlayer player, QuestDefinition quest) {
        String groupId = group(player);
        return groupId.isBlank() || QuestRepository.groupOf(quest.id()).equals(groupId);
    }

    public static String toggle(ServerPlayer player, String groupId) {
        var data = QuestProgressData.get(player.server);
        String currentGroup = group(player);
        if (!currentGroup.isBlank() && currentGroup.equals(groupId)) {
            data.focusGroup(player.getUUID(), "");
            data.focused(player.getUUID(), "");
            return "focus_off";
        }
        var definition = QuestRepository.groups().stream()
                .filter(group -> group.id().equals(groupId)).findFirst().orElse(null);
        if (definition == null) return "missing";
        if (!definition.focusAvailable()) return "focus_unavailable";
        QuestDefinition first = firstEligible(player, groupId);
        if (first == null) return "locked";
        data.focusGroup(player.getUUID(), groupId);
        data.focused(player.getUUID(), first.id());
        return "focus_on";
    }

    public static String select(ServerPlayer player, QuestDefinition quest) {
        String groupId = group(player);
        if (groupId.isBlank() || !QuestRepository.groupOf(quest.id()).equals(groupId)) return "focus_active";
        if (!eligible(player, groupId, quest)) return "locked";
        var data = QuestProgressData.get(player.server);
        data.focused(player.getUUID(), quest.id());
        return "tracked";
    }

    public static void advance(ServerPlayer player, String completedId) {
        String groupId = group(player);
        if (groupId.isBlank()) return;
        QuestDefinition first = QuestRepository.find(completedId) == null ? null
                : QuestRepository.all().stream()
                .filter(quest -> quest.parents().contains(completedId))
                .filter(quest -> eligible(player, groupId, quest)).findFirst().orElse(null);
        if (first == null) first = firstEligible(player, groupId);
        var data = QuestProgressData.get(player.server);
        String id = first == null ? "" : first.id();
        data.focused(player.getUUID(), id);
    }

    private static QuestDefinition firstEligible(ServerPlayer player, String groupId) {
        return QuestRepository.all().stream()
                .filter(quest -> eligible(player, groupId, quest))
                .findFirst().orElse(null);
    }

    private static boolean eligible(ServerPlayer player, String groupId, QuestDefinition quest) {
        if (quest == null || !QuestRepository.groupOf(quest.id()).equals(groupId)) return false;
        var data = QuestProgressData.get(player.server);
        return quest.trackable() && data.entry(player.getUUID(), quest) == null
                && QuestRuntime.unlocked(data, player, quest)
                && !QuestRuntime.hidden(data, player, quest)
                && QuestRuntime.triggersEnabled(data, player, quest)
                && QuestSteps.valid(quest);
    }
}
