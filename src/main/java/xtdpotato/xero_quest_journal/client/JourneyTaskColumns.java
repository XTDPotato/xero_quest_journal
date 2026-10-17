package xtdpotato.xero_quest_journal.client;

import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Category-first vertical lanes for tasks generated into automatic groups. */
public final class JourneyTaskColumns {
    public record Cell(QuestDefinition quest, int column, int row, String category) {}

    private JourneyTaskColumns() {}

    public static List<Cell> layout(String groupId, List<QuestDefinition> quests) {
        if (groupId == null || !groupId.startsWith("auto_")
                || quests.stream().noneMatch(QuestDefinition::automaticallyGenerated)) return List.of();
        Map<String, List<QuestDefinition>> lanes = new LinkedHashMap<>();
        quests.stream().sorted(Comparator.comparingInt(quest -> priority(category(quest))))
                .forEach(quest -> lanes.computeIfAbsent(category(quest), ignored -> new ArrayList<>()).add(quest));
        var result = new ArrayList<Cell>();
        int column = 0;
        for (var lane : lanes.entrySet()) {
            int row = 0;
            for (var quest : lane.getValue()) result.add(new Cell(quest, column, row++, lane.getKey()));
            column++;
        }
        return List.copyOf(result);
    }

    private static String category(QuestDefinition quest) {
        String trigger = triggerCategory(quest.triggers());
        if (trigger.equals("structure")) return "structure";
        if (quest.id().startsWith("auto_item_"))
            return quest.titleKey().endsWith(".craft") ? "crafting" : "acquisition";
        if (quest.id().startsWith("auto_adv_")) return "advancement";
        if (quest.id().startsWith("auto_kill_")) return "combat";
        if (!trigger.isBlank()) return trigger;
        return "other";
    }

    private static String triggerCategory(List<QuestDefinition.Trigger> triggers) {
        boolean item = false, advancement = false, combat = false, structure = false;
        for (var trigger : triggers) {
            String type = trigger.type().toLowerCase(java.util.Locale.ROOT);
            String target = trigger.item().toLowerCase(java.util.Locale.ROOT);
            structure |= type.contains("structure") || type.contains("locate")
                    || target.startsWith("structure|");
            advancement |= type.contains("advancement");
            combat |= type.equals("kill") || type.contains("combat");
            item |= type.startsWith("item_") || type.equals("item");
            String nested = triggerCategory(trigger.children());
            structure |= nested.equals("structure");
            advancement |= nested.equals("advancement");
            combat |= nested.equals("combat");
            item |= nested.equals("acquisition");
        }
        if (structure) return "structure";
        if (advancement) return "advancement";
        if (combat) return "combat";
        return item ? "acquisition" : "";
    }

    private static int priority(String category) {
        return switch (category) {
            case "acquisition" -> 0;
            case "structure" -> 1;
            case "crafting" -> 2;
            case "advancement" -> 3;
            case "combat" -> 4;
            default -> 5;
        };
    }
}
