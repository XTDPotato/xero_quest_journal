package xtdpotato.xero_quest_journal.core.model;

import java.util.*;

/** Portable task canvas, independent of client preferences and runtime property overrides. */
public record QuestPresentation(boolean authored, int width, int height, List<Control> controls,
                                List<QuestVariable> variables) {
    public record Control(String id, String kind, String value, int x, int y, int width, int height,
                          String parent, Map<String, String> properties, TriggerGraph graph) {
        public Control {
            if (id == null || id.isBlank() || id.length() > 256 || kind == null || kind.length() > 64)
                throw new IllegalArgumentException("Invalid control identity");
            value = value == null ? "" : value;
            parent = parent == null ? "" : parent;
            properties = properties == null ? Map.of() : Map.copyOf(properties);
            graph = graph == null ? TriggerGraph.empty() : graph;
            if (value.length() > 65536 || properties.size() > 128 || Math.abs((long) x) > 100000
                    || Math.abs((long) y) > 100000 || width < -2 || width > 100000 || height < -2 || height > 100000
                    || properties.entrySet().stream().anyMatch(e -> e.getKey().length() > 128 || e.getValue().length() > 8192))
                throw new IllegalArgumentException("Control exceeds limits");
        }
        public Control display() {
            return new Control(id, kind, value, x, y, width, height, parent, properties, graph.display());
        }
    }
    public QuestPresentation {
        controls = controls == null ? List.of() : List.copyOf(controls);
        variables = variables == null ? List.of() : List.copyOf(variables);
        if (width < 1 || width > 100000 || height < 1 || height > 100000 || controls.size() > 256)
            throw new IllegalArgumentException("Invalid canvas");
        if (variables.size() > 256 || variables.stream().map(QuestVariable::id).distinct().count() != variables.size())
            throw new IllegalArgumentException("Invalid task variables");
        Map<String, Control> index = new HashMap<>();
        for (Control control : controls) if (index.put(control.id(), control) != null)
            throw new IllegalArgumentException("Duplicate control ID");
        for (Control control : controls) {
            var visited = new HashSet<String>();
            for (Control current = control; current != null && !current.parent().isBlank(); current = index.get(current.parent())) {
                if (!visited.add(current.id()) || !index.containsKey(current.parent()))
                    throw new IllegalArgumentException("Invalid control parent");
            }
        }
    }
    public QuestPresentation(boolean authored, int width, int height, List<Control> controls) {
        this(authored, width, height, controls, List.of());
    }
    public static QuestPresentation empty() { return new QuestPresentation(false, 320, 240, List.of()); }
    public QuestPresentation display() {
        return new QuestPresentation(authored, width, height, controls.stream().map(Control::display).toList(), variables);
    }
    public QuestPresentation renameVariable(String oldId, String newId) {
        if (oldId == null || oldId.isBlank() || oldId.equals(newId)) return this;
        return new QuestPresentation(authored, width, height, controls.stream().map(control -> {
            var nodes = control.graph().nodes().stream().map(node -> {
                var data = new LinkedHashMap<>(node.data());
                for (String key : List.of("field", "field2", "expectedVar", "expectedVar2",
                        "variable", "sourceVariable")) {
                    if (oldId.equals(data.get(key))) data.put(key, newId);
                    if (("field".equals(key) || "field2".equals(key)) && ("var:" + oldId).equals(data.get(key)))
                        data.put(key, "var:" + newId);
                }
                return data.equals(node.data()) ? node
                        : new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
            }).toList();
            return new Control(control.id(), control.kind(), control.value(), control.x(), control.y(),
                    control.width(), control.height(), control.parent(), control.properties(),
                    new TriggerGraph(nodes, control.graph().edges()));
        }).toList(), variables);
    }
    public QuestPresentation remapTasks(Map<String, String> ids) {
        return new QuestPresentation(authored, width, height, controls.stream().map(control -> {
            var graph = new TriggerGraph(control.graph.nodes().stream().map(node -> {
                var data = new LinkedHashMap<>(node.data());
                if (List.of("complete", "unlock", "lock").contains(node.type())
                        && ids.containsKey(node.get("target", "")))
                    data.put("target", ids.get(node.get("target", "")));
                for (String key : List.of("field", "field2", "expectedVar", "expectedVar2",
                        "variable", "sourceVariable")) {
                    String value = data.get(key);
                    if (value == null) continue;
                    boolean field = (key.equals("field") || key.equals("field2")) && value.startsWith("var:");
                    String variable = field ? value.substring(4) : value;
                    int separator = variable.lastIndexOf('.');
                    if (separator > 0 && ids.containsKey(variable.substring(0, separator)))
                        data.put(key, (field ? "var:" : "") + ids.get(variable.substring(0, separator))
                                + variable.substring(separator));
                }
                return data.equals(node.data()) ? node
                        : new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
            }).toList(), control.graph.edges());
            return new Control(control.id, control.kind, control.value, control.x, control.y,
                    control.width, control.height, control.parent, control.properties, graph);
        }).toList(), variables.stream().map(variable -> {
            String scope = variable.scope();
            return ids.containsKey(scope) ? new QuestVariable(ids.get(scope) + "." + variable.name(),
                    variable.initial(), variable.created()) : variable;
        }).toList());
    }
}
