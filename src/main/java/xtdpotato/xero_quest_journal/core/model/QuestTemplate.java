package xtdpotato.xero_quest_journal.core.model;

import java.util.List;
import java.util.Map;

public record QuestTemplate(
        String id,
        String name,
        String description,
        String type,
        String icon,
        String item,
        int count,
        String rewardItem,
        int rewardCount,
        String rewardMode,
        String css,
        List<Element> elements,
        List<QuestDefinition.Trigger> defaultConditions,
        List<QuestDefinition.Reward> defaultRewards,
        String group,
        boolean archived
) {
    public QuestTemplate {
        if (id == null || !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid template ID");
        }
        name = name == null || name.isBlank() ? id : name;
        description = description == null ? "" : description;
        type = QuestDefinition.normalizeType(type);
        group = group == null || group.isBlank() ? type : group.strip();
        icon = icon == null ? "" : icon;
        item = item == null || item.isBlank() ? "minecraft:oak_log" : item;
        if (count < 1 || count > 1000000 || rewardCount < 1 || rewardCount > 1000000) {
            throw new IllegalArgumentException("Invalid template count");
        }
        rewardItem = rewardItem == null ? "" : rewardItem;
        rewardMode = rewardMode == null || rewardMode.isBlank() ? "default" : rewardMode;
        css = css == null ? "" : css;
        elements = elements == null ? List.of() : List.copyOf(elements);
        defaultConditions = defaultConditions == null ? List.of() : List.copyOf(defaultConditions);
        defaultRewards = defaultRewards == null
                ? rewardItem.isBlank() ? List.of()
                : List.of(new QuestDefinition.Reward("item", rewardItem, rewardCount))
                : List.copyOf(defaultRewards);
        if (!type.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")
                || !List.of("default", "manual", "automatic").contains(rewardMode)
                || icon.length() > 8192 || item.length() > 8192 || rewardItem.length() > 8192
                || name.length() > 128 || group.length() > 128 || description.length() > 4096
                || css.length() > 32768 || elements.size() > 256
                || defaultConditions.size() > 128 || defaultRewards.size() > 128) {
            throw new IllegalArgumentException("Invalid template");
        }
        var ids = new java.util.HashSet<String>();
        for (Element element : elements) {
            if (!ids.add(element.id())) throw new IllegalArgumentException("Duplicate template control ID");
        }
        var byId = new java.util.HashMap<String, Element>();
        elements.forEach(element -> byId.put(element.id(), element));
        for (Element element : elements) {
            var visited = new java.util.HashSet<String>();
            visited.add(element.id());
            for (String parent = element.parent(); !parent.isBlank(); ) {
                Element container = byId.get(parent);
                if (container == null || !List.of("linear", "relative", "scroll").contains(container.kind())
                        || !visited.add(parent)) {
                    throw new IllegalArgumentException("Invalid or cyclic template parent");
                }
                parent = container.parent();
            }
        }
    }

    public QuestTemplate(String id, String name, String description, String type, String icon,
                         String item, int count, String rewardItem, int rewardCount,
                         String rewardMode, String css, List<Element> elements,
                         List<QuestDefinition.Trigger> defaultConditions,
                         List<QuestDefinition.Reward> defaultRewards) {
        this(id, name, description, type, icon, item, count, rewardItem, rewardCount, rewardMode,
                css, elements, defaultConditions, defaultRewards, null, false);
    }

    public QuestTemplate(String id, String name, String description, String type, String icon,
                         String item, int count, String rewardItem, int rewardCount,
                         String rewardMode, String css) {
        this(id, name, description, type, icon, item, count, rewardItem, rewardCount, rewardMode, css,
                List.of(), null, null);
    }

    public QuestTemplate(String id, String name, String description, String type, String icon,
                         String item, int count, String rewardItem, int rewardCount,
                         String rewardMode, String css, List<Element> elements) {
        this(id, name, description, type, icon, item, count, rewardItem, rewardCount, rewardMode, css,
                elements, null, null);
    }

    public record Element(String id, String kind, String value, int x, int y, int width, int height,
                          Map<String, String> attributes, String parent) {
        public Element {
            if (id == null || !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")) {
                throw new IllegalArgumentException("Invalid template control ID");
            }
            kind = kind == null || kind.isBlank() ? "text" : kind;
            value = value == null ? "" : value;
            width = Math.max(1, width);
            height = Math.max(1, height);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            parent = parent == null ? "" : parent;
            if (!List.of("text", "image", "video", "audio", "linear", "relative", "quest_title", "quest_type",
                    "quest_status", "quest_progress", "quest_description", "quest_icon", "quest_rewards",
                    "quest_objective", "quest_place", "quest_entry_text", "item_display", "button",
                    "structure_preview", "structure_enter", "scroll")
                    .contains(kind) || width > 4096 || height > 4096
                    || Math.abs((long) x) > 16384 || Math.abs((long) y) > 16384
                    || value.length() > 4096 || attributes.size() > 64) {
                throw new IllegalArgumentException("Invalid template control");
            }
            if (attributes.entrySet().stream().anyMatch(entry -> entry.getKey().length() > 64
                    || entry.getValue().length() > 4096 || entry.getKey().startsWith("template_")
                    || entry.getKey().startsWith("journal_"))) {
                throw new IllegalArgumentException("Invalid template attribute");
            }
            if (attributes.containsKey("eventGraph")) {
                TriggerGraph graph = new com.google.gson.Gson().fromJson(attributes.get("eventGraph"), TriggerGraph.class);
                if (graph == null) throw new IllegalArgumentException("Invalid template event graph");
            }
        }
    }

    public QuestTemplate withElements(List<Element> controls) {
        return new QuestTemplate(id, name, description, type, icon, item, count,
                rewardItem, rewardCount, rewardMode, css, controls, defaultConditions, defaultRewards, group, archived);
    }

    public QuestTemplate withDefaults(List<QuestDefinition.Trigger> conditions,
                                     List<QuestDefinition.Reward> rewards) {
        return new QuestTemplate(id, name, description, type, icon, item, count,
                rewardItem, rewardCount, rewardMode, css, elements, conditions, rewards, group, archived);
    }

    public QuestTemplate withGroup(String value) {
        return new QuestTemplate(id, name, description, type, icon, item, count, rewardItem, rewardCount,
                rewardMode, css, elements, defaultConditions, defaultRewards, value, archived);
    }

    public QuestTemplate archive() {
        return new QuestTemplate(id, name, description, type, icon, item, count, rewardItem, rewardCount,
                rewardMode, css, elements, defaultConditions, defaultRewards, group, true);
    }

    public QuestPresentation presentation(String questId) {
        return new QuestPresentation(true, 395, 275, elements.stream().map(element -> {
            var properties = new java.util.LinkedHashMap<>(element.attributes());
            String graph = properties.remove("eventGraph");
            return new QuestPresentation.Control(controlId(questId, element.id()), element.kind(), element.value(),
                    element.x() + (element.parent().isBlank() ? 10 : 0), element.y(), element.width(), element.height(),
                    element.parent().isBlank() ? "" : controlId(questId, element.parent()), properties,
                    graph == null ? TriggerGraph.empty() : qualifyGraph(
                            new com.google.gson.Gson().fromJson(graph, TriggerGraph.class), questId));
        }).toList());
    }

    public TriggerGraph qualifyGraph(TriggerGraph graph, String questId) {
        return new TriggerGraph(graph.nodes().stream().map(node -> {
            if (!List.of("set_property", "reset_property").contains(node.type())) return node;
            var data = new java.util.LinkedHashMap<>(node.data());
            String targetQuest = data.getOrDefault("controlQuest", "");
            String key = data.getOrDefault("control", "");
            if ((targetQuest.isBlank() || targetQuest.equals(questId))
                    && elements.stream().anyMatch(e -> e.id().equals(key)))
                data.put("control", controlId(questId, key));
            var targets = QuestControlTarget.decode(data.getOrDefault("controls", ""));
            if (!targets.isEmpty()) data.put("controls", QuestControlTarget.encode(targets.stream().map(target ->
                    (target.quest().isBlank() || target.quest().equals(questId))
                            && elements.stream().anyMatch(e -> e.id().equals(target.control()))
                            ? new QuestControlTarget(questId, controlId(questId, target.control())) : target).toList()));
            return new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
        }).toList(), graph.edges());
    }
    public String controlId(String questId, String key) { return "template:" + questId + ":" + id + ":" + key; }
}
