package xtdpotato.xero_quest_journal.client;

import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared multi-select state and matching rules for the journal's search filters. */
public final class QuestSearchFilters {
    static final List<String> KEYS = List.of("type", "group", "status", "template",
            "reward_type", "reward_item", "condition_type", "condition_target");

    private QuestSearchFilters() {}

    static Set<String> selected(Map<String, Set<String>> filters, String key) {
        return filters == null ? Set.of() : filters.getOrDefault(key, Set.of());
    }

    static Map<String, Set<String>> toggle(Map<String, Set<String>> filters, String key, String value) {
        var result = new LinkedHashMap<String, Set<String>>();
        if (filters != null) filters.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) result.put(name, new LinkedHashSet<>(values));
        });
        var values = new LinkedHashSet<>(result.getOrDefault(key, Set.of()));
        if (!values.remove(value)) values.add(value);
        if (values.isEmpty()) result.remove(key);
        else result.put(key, values);
        return sanitize(result);
    }

    static Map<String, Set<String>> without(Map<String, Set<String>> filters, String key, String value) {
        var result = new LinkedHashMap<String, Set<String>>();
        if (filters != null) filters.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) result.put(name, new LinkedHashSet<>(values));
        });
        var values = new LinkedHashSet<>(result.getOrDefault(key, Set.of()));
        values.remove(value);
        if (values.isEmpty()) result.remove(key);
        else result.put(key, values);
        return sanitize(result);
    }

    static Map<String, Set<String>> sanitize(Map<String, Set<String>> filters) {
        var result = new LinkedHashMap<String, Set<String>>();
        if (filters != null) filters.forEach((key, values) -> {
            if (KEYS.contains(key) && values != null) {
                var accepted = values.stream().filter(java.util.Objects::nonNull)
                        .filter(value -> !value.isBlank() && value.length() <= 8192)
                        .limit(10000).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (!accepted.isEmpty()) result.put(key, Set.copyOf(accepted));
            }
        });
        return Map.copyOf(result);
    }

    static int count(Map<String, Set<String>> filters) {
        return selectedValues(filters).values().stream().mapToInt(Set::size).sum();
    }

    static Map<String, Set<String>> selectedValues(Map<String, Set<String>> filters) {
        return sanitize(filters);
    }

    public static boolean matchesContent(QuestDefinition quest, Map<String, Set<String>> filters) {
        Set<String> values = selected(filters, "type");
        if (!values.isEmpty() && !values.contains(quest.type())) return false;
        values = selected(filters, "template");
        if (!values.isEmpty() && !values.contains(quest.templateId().isBlank() ? "none" : quest.templateId()))
            return false;

        Set<String> rewardTypes = selected(filters, "reward_type");
        Set<String> rewardItems = selected(filters, "reward_item");
        if (!rewardTypes.isEmpty() || !rewardItems.isEmpty()) {
            boolean matched = quest.rewards().stream().anyMatch(reward ->
                    (rewardTypes.isEmpty() || rewardTypes.contains(reward.type()))
                            && (rewardItems.isEmpty() || reward.type().equals("item")
                            && rewardItems.contains(reward.item())));
            if (!matched) return false;
        }

        Set<String> conditionTypes = selected(filters, "condition_type");
        Set<String> conditionTargets = selected(filters, "condition_target");
        if (!conditionTypes.isEmpty() || !conditionTargets.isEmpty()) {
            boolean matched = quest.triggers().stream().anyMatch(trigger ->
                    matchesTrigger(trigger, conditionTypes, conditionTargets))
                    || quest.steps().stream().flatMap(step -> step.conditions().stream()).anyMatch(trigger ->
                    matchesTrigger(trigger, conditionTypes, conditionTargets));
            if (!matched) return false;
        }
        return true;
    }

    static boolean matchesStatus(QuestDefinition quest, Map<String, Set<String>> filters) {
        Set<String> statuses = selected(filters, "status");
        if (statuses.isEmpty()) return true;
        var view = ClientQuestProgress.view(quest.id());
        return statuses.stream().anyMatch(status -> switch (status) {
            case "active" -> view != null && view.status().equals("active");
            case "completed" -> view != null && view.completed();
            case "claimed" -> view != null && view.status().equals("claimed");
            case "locked" -> view != null && !view.unlocked();
            case "unlocked" -> view != null && view.unlocked();
            case "tracked" -> ClientQuestProgress.isTracked(quest.id());
            case "untracked" -> !ClientQuestProgress.isTracked(quest.id());
            case "unread" -> view != null && view.newTask();
            default -> false;
        });
    }

    private static boolean matchesTrigger(QuestDefinition.Trigger trigger, Set<String> types,
                                          Set<String> targets) {
        if ((types.isEmpty() || types.contains(trigger.type()))
                && (targets.isEmpty() || targets.contains(trigger.item()))) return true;
        return trigger.children().stream().anyMatch(child -> matchesTrigger(child, types, targets));
    }

    static List<String> sorted(Set<String> values) {
        return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
