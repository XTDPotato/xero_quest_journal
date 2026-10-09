package xtdpotato.xero_quest_journal.client;

import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestUnlockLayout;

import java.util.*;

/** Bounded initial layout for the prerequisite editor. */
final class QuestUnlockGraphLayout {
    private static final int CARD_WIDTH = 190;
    private static final int X_STEP = 250;
    private static final int Y_STEP = 122;
    private static final int GRID_COLUMNS = 400;

    private QuestUnlockGraphLayout() {}

    static Map<String, QuestUnlockLayout.Position> initial(List<QuestDefinition> tasks) {
        var parents = new HashMap<String, List<String>>();
        tasks.forEach(task -> parents.put(task.id(), task.parents()));
        return initial(tasks.stream().map(QuestDefinition::id).toList(), parents);
    }

    static Map<String, QuestUnlockLayout.Position> initial(List<String> ids, Map<String, List<String>> parents) {
        var depths = new HashMap<String, Integer>();
        ids.forEach(id -> depth(id, parents, depths, new HashSet<>()));
        var rows = new HashMap<Integer, Integer>();
        var ordered = ids.stream().distinct().sorted(Comparator.comparingInt((String id) -> depths.get(id))
                .thenComparing(id -> id)).toList();
        var positions = new LinkedHashMap<String, QuestUnlockLayout.Position>();
        boolean layeredFits = true;
        for (var id : ordered) {
            int column = depths.get(id);
            int row = rows.merge(column, 1, Integer::sum) - 1;
            long x = 32L + (long) column * X_STEP;
            long y = 24L + (long) row * Y_STEP;
            if (x + CARD_WIDTH > 100_000 || y + 122 > 100_000) layeredFits = false;
            positions.put(id, new QuestUnlockLayout.Position((int) Math.min(100_000, x),
                    (int) Math.min(100_000, y)));
        }
        if (layeredFits) return Map.copyOf(positions);

        // Preserve the topological ordering while wrapping wide/large graphs to bounded rows.
        positions.clear();
        for (int index = 0; index < ordered.size(); index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int x = 32 + column * X_STEP;
            int y = 24 + row * Y_STEP;
            positions.put(ordered.get(index), new QuestUnlockLayout.Position(x, y));
        }
        return Map.copyOf(positions);
    }

    private static int depth(String id, Map<String, List<String>> parentsById,
                             Map<String, Integer> depths, Set<String> visiting) {
        Integer known = depths.get(id);
        if (known != null) return known;
        if (!visiting.add(id)) return 0;
        int result = 0;
        for (String parent : parentsById.getOrDefault(id, List.of()))
            if (parentsById.containsKey(parent))
                result = Math.max(result, 1 + depth(parent, parentsById, depths, visiting));
        visiting.remove(id);
        depths.put(id, result);
        return result;
    }

    static Map<String, QuestUnlockLayout.Position> persisted(
            Map<String, QuestUnlockLayout.Position> positions,
            Map<String, QuestUnlockLayout.Position> defaults) {
        var changed = new LinkedHashMap<String, QuestUnlockLayout.Position>();
        positions.forEach((id, position) -> {
            if (!position.equals(defaults.get(id))) changed.put(id, position);
        });
        if (changed.size() > 4096) throw new IllegalArgumentException("Too many custom unlock positions");
        return Map.copyOf(changed);
    }
}
