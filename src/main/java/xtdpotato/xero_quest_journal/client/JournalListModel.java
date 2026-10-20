package xtdpotato.xero_quest_journal.client;

import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.util.*;
import java.util.function.Function;

/** Visible rows are the shared source of truth for scrolling, drawing and hit testing. */
public record JournalListModel(List<Row> rows, int contentHeight) {
    /** Settled rows are ordered and disjoint, so viewport lookup is logarithmic. */
    public List<Row> visibleRows(double top, double bottom) {
        int low = 0, high = rows.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            var row = rows.get(middle);
            if (row.offset() + row.height() <= top) low = middle + 1;
            else high = middle;
        }
        int first = low;
        while (low < rows.size() && rows.get(low).offset() < bottom) low++;
        return rows.subList(first, low);
    }

    public record Row(String key, QuestDefinition quest, QuestCatalog.Group group,
                      int offset, int height, int indent, String category) {
        public Row(String key, QuestDefinition quest, QuestCatalog.Group group,
                   int offset, int height, int indent) {
            this(key, quest, group, offset, height, indent, "");
        }
        public boolean section() { return !category.isBlank(); }
    }

    /** Compact single-line rows; subtitle rows add only the height needed for a second line. */
    public static final int QUEST_ROW = 22, TALL_ROW = 28;

    public JournalListModel withSection(String category) {
        if (rows.isEmpty() || category.isBlank()) return this;
        var headed = new ArrayList<Row>();
        headed.add(new Row("section:" + category, null, null, 0, 25, 0, category));
        for (var row : rows)
            headed.add(new Row(row.key(), row.quest(), row.group(), row.offset() + 29,
                    row.height(), row.indent(), row.category()));
        return new JournalListModel(List.copyOf(headed), contentHeight + 29);
    }

    public static JournalListModel categorized(List<QuestDefinition> quests, List<QuestCatalog.Group> groups,
                                               Function<String, String> membership, Set<String> collapsed,
                                               boolean showEmptyGroups, List<String> categories) {
        return categorized(quests, groups, membership, collapsed, showEmptyGroups, categories, quest -> QUEST_ROW);
    }

    public static JournalListModel categorized(List<QuestDefinition> quests, List<QuestCatalog.Group> groups,
                                               Function<String, String> membership, Set<String> collapsed,
                                               boolean showEmptyGroups, List<String> categories,
                                               java.util.function.ToIntFunction<QuestDefinition> questHeight) {
        var order = new LinkedHashSet<>(List.of("main", "legendary", "commission", "world"));
        categories.stream().filter(type -> !List.of("active", "normal", "other").contains(type)).forEach(order::add);
        var fallbackTypes = new HashMap<String, String>();
        for (var quest : quests)
            if (order.contains(quest.type())) fallbackTypes.putIfAbsent(membership.apply(quest.id()), quest.type());
        Map<String, String> groupTypes = new HashMap<>();
        for (var group : groups) {
            String type = group.type();
            if (!order.contains(type)) type = fallbackTypes.getOrDefault(group.id(), "");
            groupTypes.put(group.id(), type);
        }
        var tasksBySection = new HashMap<String, List<QuestDefinition>>();
        var standaloneTasks = new ArrayList<QuestDefinition>();
        for (var quest : quests) {
            String group = membership.apply(quest.id());
            if (group.isBlank()) standaloneTasks.add(quest);
            else tasksBySection.computeIfAbsent(groupTypes.getOrDefault(group,
                    order.contains(quest.type()) ? quest.type() : ""), ignored -> new ArrayList<>()).add(quest);
        }
        var groupsBySection = new HashMap<String, List<QuestCatalog.Group>>();
        for (var group : groups)
            groupsBySection.computeIfAbsent(groupTypes.get(group.id()), ignored -> new ArrayList<>()).add(group);
        List<Row> rows = new ArrayList<>();
        int offset = 0;
        List<String> sections = new ArrayList<>(order);
        sections.add("");
        for (String type : sections) {
            var tasks = tasksBySection.getOrDefault(type, List.of());
            var groupList = groupsBySection.getOrDefault(type, List.of());
            var model = build(tasks, groupList, membership, collapsed, showEmptyGroups, questHeight);
            if (model.rows().isEmpty()) continue;
            if (!type.isBlank()) {
                rows.add(new Row("section:" + type, null, null, offset, 25, 0, type));
                offset += 29;
            }
            for (var row : model.rows())
                rows.add(new Row(row.key(), row.quest(), row.group(), offset + row.offset(),
                        row.height(), row.indent()));
            offset += model.contentHeight() + 10;
        }
        var standalone = build(standaloneTasks, List.of(), membership, Set.of(), false, questHeight);
        for (var row : standalone.rows())
            rows.add(new Row(row.key(), row.quest(), null, offset + row.offset(), row.height(), 0));
        return new JournalListModel(List.copyOf(rows),
                rows.isEmpty() ? 0 : rows.getLast().offset() + rows.getLast().height());
    }

    public static JournalListModel build(List<QuestDefinition> quests, List<QuestCatalog.Group> groups,
                                         Function<String, String> membership, Set<String> collapsed,
                                         boolean showEmptyGroups) {
        return build(quests, groups, membership, collapsed, showEmptyGroups, quest -> QUEST_ROW);
    }

    public static JournalListModel build(List<QuestDefinition> quests, List<QuestCatalog.Group> groups,
                                         Function<String, String> membership, Set<String> collapsed,
                                         boolean showEmptyGroups,
                                         java.util.function.ToIntFunction<QuestDefinition> questHeight) {
        Map<String, List<QuestDefinition>> grouped = new LinkedHashMap<>();
        for (var group : groups) grouped.put(group.id(), new ArrayList<>());
        List<QuestDefinition> standalone = new ArrayList<>();
        for (var quest : quests) {
            String group = membership.apply(quest.id());
            var children = grouped.get(group);
            if (children != null) children.add(quest);
            else if (group.isBlank()) standalone.add(quest);
        }
        List<Row> rows = new ArrayList<>();
        int offset = 0;
        for (var group : groups) {
            var children = grouped.get(group.id());
            if (children.isEmpty() && !showEmptyGroups) continue;
            int height = group.description().isBlank() ? 26 : 38;
            rows.add(new Row("group:" + group.id(), null, group, offset, height, 0));
            offset += height + 4;
            if (!collapsed.contains(group.id())) {
                for (var quest : children) {
                    int rowHeight = Math.max(QUEST_ROW, questHeight.applyAsInt(quest));
                    rows.add(new Row("quest:" + quest.id(), quest, null, offset, rowHeight, 12));
                    offset += rowHeight + 4;
                }
            }
            offset += 4;
        }
        for (var quest : standalone) {
            int height = Math.max(QUEST_ROW, questHeight.applyAsInt(quest));
            rows.add(new Row("quest:" + quest.id(), quest, null, offset, height, 0));
            offset += height + 4;
        }
        int contentHeight = rows.isEmpty() ? 0 : rows.getLast().offset() + rows.getLast().height();
        return new JournalListModel(List.copyOf(rows), contentHeight);
    }
}
