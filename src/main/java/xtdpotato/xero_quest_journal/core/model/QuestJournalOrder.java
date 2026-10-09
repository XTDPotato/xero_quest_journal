package xtdpotato.xero_quest_journal.core.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/** Display order only; membership, graph positions, and progression are independent. */
public record QuestJournalOrder(List<String> tasks, List<String> tabs, List<String> groups) {
    public QuestJournalOrder {
        tasks = validated(tasks, 65536);
        tabs = validated(tabs, 66);
        groups = validated(groups, 65536);
    }

    public QuestJournalOrder(List<String> tasks, List<String> tabs) { this(tasks, tabs, List.of()); }

    private static List<String> validated(List<String> values, int maximum) {
        if (values == null) return List.of();
        if (values.size() > maximum || new HashSet<>(values).size() != values.size()
                || values.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 128))
            throw new IllegalArgumentException("Invalid journal order");
        return List.copyOf(values);
    }

    public static QuestJournalOrder empty() { return new QuestJournalOrder(List.of(), List.of()); }

    public static List<String> apply(List<String> available, List<String> saved) {
        var remaining = new LinkedHashSet<>(available);
        var result = new ArrayList<String>();
        for (String id : saved) if (remaining.remove(id)) result.add(id);
        result.addAll(remaining);
        return List.copyOf(result);
    }

    public static List<String> defaultTabs(List<QuestType> types) {
        var result = new LinkedHashSet<String>();
        result.add("active");
        types.stream().map(QuestType::id).filter(id -> !id.equals("completed")).forEach(result::add);
        result.add("completed");
        return List.copyOf(result);
    }

    public List<String> tabOrder(List<QuestType> types) {
        List<String> defaults = defaultTabs(types);
        if (tabs.isEmpty()) return defaults;
        var result = new ArrayList<>(apply(defaults, tabs));
        // New types are inserted before completed, without changing explicitly moved tabs.
        for (String id : defaults) {
            if (tabs.contains(id) || id.equals("completed")) continue;
            result.remove(id);
            result.add(result.indexOf("completed"), id);
        }
        return List.copyOf(result);
    }

    public static List<String> move(List<String> values, String id, String neighbor, int direction) {
        var result = new ArrayList<>(values);
        if (!result.contains(id) || !result.contains(neighbor) || id.equals(neighbor)
                || Math.abs(direction) != 1) throw new IllegalArgumentException("Invalid order move");
        result.remove(id);
        result.add(result.indexOf(neighbor) + (direction > 0 ? 1 : 0), id);
        return List.copyOf(result);
    }

    /** Moves a selected block as one stable unit, retaining its current internal order. */
    public static List<String> moveBlock(List<String> values, List<String> ids, String neighbor, boolean after) {
        if (values == null || ids == null || ids.isEmpty() || ids.size() > 512
                || new HashSet<>(ids).size() != ids.size() || neighbor == null || ids.contains(neighbor))
            throw new IllegalArgumentException("Invalid order block");
        var moving = new LinkedHashSet<>(ids);
        var block = values.stream().filter(moving::contains).toList();
        if (block.size() != moving.size() || !values.contains(neighbor))
            throw new IllegalArgumentException("Missing order task");
        var result = new ArrayList<>(values);
        result.removeIf(moving::contains);
        int destination = result.indexOf(neighbor) + (after ? 1 : 0);
        result.addAll(destination, block);
        return List.copyOf(result);
    }
}
