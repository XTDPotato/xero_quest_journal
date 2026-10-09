package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Multi-step tasks: only the current step's conditions are evaluated. Each step counts its own
 * events through a step-scoped identity, so two steps that both count "kill zombie" never share
 * progress. The step index lives with the task's counters and is cleared by a task reset.
 */
public final class QuestSteps {
    private QuestSteps() {}

    public static boolean has(QuestDefinition quest) {
        return quest != null && !quest.steps().isEmpty();
    }

    static String indexKey(QuestDefinition quest) {
        return quest.progressKey() + "/#step";
    }

    private static String scopeBase(QuestDefinition quest) {
        return quest.uid().isBlank() ? "id:" + quest.id() : quest.uid();
    }

    /** Prefix shared by every step-scoped counter key of the task. */
    static String scopePrefix(QuestDefinition quest) {
        return "uid:" + scopeBase(quest) + "/#s";
    }

    public static int index(QuestProgressData data, UUID player, QuestDefinition quest) {
        if (!has(quest)) return 0;
        return Math.clamp(data.counter(player, indexKey(quest)), 0, quest.steps().size() - 1);
    }

    /** Condition view of one step; tasks without steps are returned unchanged. */
    public static QuestDefinition view(QuestDefinition quest, int index) {
        if (!has(quest)) return quest;
        int bounded = Math.clamp(index, 0, quest.steps().size() - 1);
        List<QuestDefinition.Trigger> conditions = new ArrayList<>(quest.steps().get(bounded).conditions());
        return quest.stepView(conditions, scopeBase(quest) + "/#s" + bounded);
    }

    public static QuestDefinition current(QuestProgressData data, UUID player, QuestDefinition quest) {
        return has(quest) ? view(quest, index(data, player, quest)) : quest;
    }

    public static QuestDefinition.Step currentStep(QuestProgressData data, UUID player, QuestDefinition quest) {
        return has(quest) ? quest.steps().get(index(data, player, quest)) : null;
    }

    public static boolean last(QuestProgressData data, UUID player, QuestDefinition quest) {
        return !has(quest) || index(data, player, quest) >= quest.steps().size() - 1;
    }

    /** Moves to the next step; returns false when the current step is the final one. */
    static boolean advance(QuestProgressData data, ServerPlayer player, QuestDefinition quest) {
        if (last(data, player.getUUID(), quest)) return false;
        data.setCounter(player.getUUID(), indexKey(quest), index(data, player.getUUID(), quest) + 1);
        return true;
    }

    /** Task conditions must be valid and every step needs at least one evaluable condition. */
    public static boolean valid(QuestDefinition quest) {
        if (!QuestConditions.valid(quest.triggers())) return false;
        if (!has(quest)) return true;
        for (var step : quest.steps()) {
            if (step.conditions().isEmpty() || !QuestConditions.valid(step.conditions())) return false;
            if (step.conditions().stream().allMatch(t -> t.type().equals("unlock") || t.type().equals("lock")))
                return false;
        }
        return true;
    }
}
