package xtdpotato.xero_quest_journal.client;

import xtdpotato.xero_quest_journal.core.model.QuestTemplate;

import java.util.*;

/** Shared geometry, per-task values; instance IDs must never alias another task. */
public final class TemplateLayout {
    private TemplateLayout() {}

    public static String id(String quest, String template, String key) {
        return "template:" + quest + ":" + template + ":" + key;
    }

    public static void apply(LayoutEditorState layout, String quest, QuestTemplate template, int x, int y) {
        apply(layout, quest, template, x, y, false);
    }

    static void apply(LayoutEditorState layout, String quest, QuestTemplate template, int x, int y, boolean preserveInstance) {
        Map<String, String> values = new HashMap<>();
        Map<String, LayoutEditorState.Element> existing = new HashMap<>();
        Map<String, int[]> previousPositions = new HashMap<>();
        Set<String> removed = new HashSet<>();
        for (var element : layout.elements().values()) {
            if (!element.quest().equals(quest)) continue;
            if (element.attribute("template_owner", "").equals(template.id())) {
                values.put(element.attribute("template_key", ""), element.value());
                existing.put(element.id(), element);
                var position = layout.position(element.id(), 0, 0);
                previousPositions.put(element.id(), new int[]{position.x(), position.y()});
                removed.add(element.id());
            } else if (element.id().startsWith("builtin:" + quest + ":")) {
                removed.add(element.id());
            }
        }
        // Rebuild links in one pass, so valid parent inversions never see the old graph.
        removed.forEach(key -> {
            layout.elements().remove(key);
            layout.positions().remove(key);
            layout.parents().remove(key);
        });
        Set<String> live = new HashSet<>();
        for (var control : template.elements()) {
            String id = id(quest, template.id(), control.id());
            if (layout.isDeleted(id)) {
                continue;
            }
            live.add(id);
            if (preserveInstance && existing.containsKey(id)) {
                layout.elements().put(id, existing.get(id));
                int[] position = previousPositions.get(id);
                layout.setPosition(id, position[0], position[1]);
                continue;
            }
            var attrs = new LinkedHashMap<>(control.attributes());
            if (attrs.containsKey("eventGraph")) {
                var gson = new com.google.gson.Gson();
                attrs.put("eventGraph", gson.toJson(template.qualifyGraph(gson.fromJson(
                        attrs.get("eventGraph"), xtdpotato.xero_quest_journal.core.model.TriggerGraph.class), quest)));
            }
            attrs.put("template_owner", template.id());
            attrs.put("template_key", control.id());
            layout.elements().put(id, new LayoutEditorState.Element(id, quest, control.kind(),
                    values.getOrDefault(control.id(), control.value()), control.width(), control.height(), attrs));
            layout.setPosition(id, x + control.x(), y + control.y());
            layout.position(id, 0, 0).size(control.width(), control.height());
        }
        layout.parents().entrySet().removeIf(entry -> removed.contains(entry.getValue()) && !live.contains(entry.getValue()));
        for (var control : template.elements()) {
            if (!control.parent().isBlank()) {
                String child = id(quest, template.id(), control.id());
                String parent = id(quest, template.id(), control.parent());
                if (layout.elements().containsKey(child) && layout.elements().containsKey(parent)) {
                    layout.setParent(child, parent);
                }
            }
        }
    }
}
