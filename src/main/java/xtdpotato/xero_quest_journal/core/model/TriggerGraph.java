package xtdpotato.xero_quest_journal.core.model;

import java.util.*;

/** Bounded event -> predicate -> action DAG. Unconnected nodes remain editable drafts. */
public record TriggerGraph(List<Node> nodes, List<Edge> edges) {
    public static final int MAX_NODE_SIZE = 4096;
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    public static final List<String> EVENTS = List.of("click", "jump", "join", "leave", "dimension",
            "death", "respawn", "kill", "block_break", "block_interact", "item_pickup", "craft",
            "smelt", "sleep", "wake", "advancement", "tick", "world_tick", "day_start", "night_start",
            "rain_start", "rain_stop", "thunder_start", "thunder_stop", "hover", "press", "release", "hold");
    public static final List<String> CONTROL_EVENTS = List.of("click", "hover", "press", "release", "hold");
    public static final List<String> ACTIONS = List.of("complete", "advance_step", "activate", "unlock", "lock",
            "set_task_property", "command",
            "set_property", "reset_property",
            "set_theme", "set_overview_background", "reward", "kubejs_call", "kubejs_script", "set_variable",
            "set_context", "end", "context");
    public static final List<String> SETTABLE_PLAYER_FIELDS = List.of("experienceLevel", "health", "foodLevel");
    public static final List<String> SETTABLE_WORLD_FIELDS = List.of("dayTime", "raining", "thundering");
    public static final List<String> PROPERTIES = ControlProperties.all();
    public static final List<String> FIELDS = List.of("eventType", "eventTarget", "dimension",
            "playerName", "playerId", "experienceLevel", "x", "y", "z", "health", "maxHealth",
            "foodLevel", "sneaking", "sprinting", "dayTime", "gameTime", "raining", "thundering", "difficulty",
            "questId", "controlId", "controlKind", "controlValue", "controlX", "controlY", "controlWidth", "controlHeight",
            "mouseX", "mouseY", "mouseWidth", "mouseHeight", "mouseHeld", "mouseInside",
            "locatedX", "locatedY", "locatedZ", "locatedDimension", "locationFound");
    public static final List<String> OPERATORS = List.of("eq", "ne", "gt", "gte", "lt", "lte",
            "contains", "starts", "ends");
    public static final List<String> LOGICS = List.of("single", "not", "and", "or");
    public record Node(String id, String type, int x, int y, Map<String, String> data) {
        public Node {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")
                    || !List.of("event", "js", "variable", "context", "complete", "advance_step", "activate",
                    "unlock", "lock", "reset_property",
                    "set_theme", "set_overview_background", "reward", "kubejs_call", "kubejs_script", "set_property",
                    "set_task_property", "set_variable", "command", "end",
                    "set_context", "delay",
                    "mail_send", "mail_check", "mail_delete", "mail_set_property", "mail_lock_task", "mail_unlock_task",
                    "locate_structure", "locate_biome", "teleport", "enter_structure").contains(type))
                throw new IllegalArgumentException("Invalid trigger node");
            data = data == null ? Map.of() : Map.copyOf(data);
            MailNodeSettings.validate(type, data);
            if (data.size() > 32 || data.entrySet().stream().anyMatch(e -> e.getKey().length() > 64
                    || e.getValue().length() > 16384) || Math.abs((long) x) > 100000 || Math.abs((long) y) > 100000)
                throw new IllegalArgumentException("Trigger node exceeds limits");
            for (String key : List.of("layoutWidth", "layoutHeight")) {
                int size = Integer.parseInt(data.getOrDefault(key, "0"));
                if (size < 0 || size > MAX_NODE_SIZE)
                    throw new IllegalArgumentException("Invalid trigger node size");
            }
            if (type.equals("event") && !EVENTS.contains(data.getOrDefault("event", "click")))
                throw new IllegalArgumentException("Unknown event");
            if (List.of("set_property", "reset_property").contains(type)
                    && !PROPERTIES.contains(data.getOrDefault("property", "visibility")))
                throw new IllegalArgumentException("Unknown control property");
            if (List.of("set_property", "reset_property").contains(type)) {
                QuestControlTarget.decode(data.getOrDefault("controls", ""));
                String quest = data.getOrDefault("controlQuest", "");
                if (!quest.isBlank() && !quest.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}"))
                    throw new IllegalArgumentException("Invalid target task");
            }
            if (type.equals("set_task_property")) {
                if (!List.of("unlocked", "hideWhenLocked", "hidden", "triggersEnabled")
                        .contains(data.getOrDefault("property", "hidden"))
                        || !List.of("true", "false").contains(data.getOrDefault("value", "true"))
                        || data.getOrDefault("target", "").length() > 256)
                    throw new IllegalArgumentException("Invalid task property");
            }
            if (type.equals("command")) {
                String command = data.getOrDefault("command", "");
                if (command.length() > 4096 || command.indexOf('\n') >= 0
                        || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0)
                    throw new IllegalArgumentException("Invalid command");
            }
            if (type.equals("delay")) {
                int ticks = Integer.parseInt(data.getOrDefault("ticks", "20"));
                if (ticks < 1 || ticks > 72000) throw new IllegalArgumentException("Invalid delay");
            }
            if (type.equals("set_context")) {
                String scope = data.getOrDefault("scope", "player");
                String field = data.getOrDefault("field", "experienceLevel");
                String value = data.getOrDefault("value", "");
                if (value.length() > 128) throw new IllegalArgumentException("Invalid game variable value");
                if (scope.equals("player")) {
                    if (!SETTABLE_PLAYER_FIELDS.contains(field)) throw new IllegalArgumentException("Invalid player variable");
                    MailNodeSettings.validatePlayer(data.getOrDefault("player", ""));
                    if (data.getOrDefault("player", "").isBlank())
                        throw new IllegalArgumentException("Player selector is required");
                } else if (scope.equals("world")) {
                    if (!SETTABLE_WORLD_FIELDS.contains(field)) throw new IllegalArgumentException("Invalid world variable");
                    String dimension = data.getOrDefault("dimension", "");
                    if (!dimension.isBlank() && net.minecraft.resources.ResourceLocation.tryParse(dimension) == null)
                        throw new IllegalArgumentException("Invalid world dimension");
                } else throw new IllegalArgumentException("Invalid game variable scope");
                validateSetContextValue(field, value);
            }
            if (type.equals("set_theme")) {
                String key = data.getOrDefault("key", "accent");
                String value = data.getOrDefault("value", "");
                QuestUiTheme.validate(key, value);
            }
            if (type.equals("set_overview_background")) {
                QuestUiTheme.validateOverviewImage(data.getOrDefault("source", ""));
                if (!List.of("player", "server").contains(data.getOrDefault("scope", "player")))
                    throw new IllegalArgumentException("Invalid overview background scope");
            }
            if (type.equals("context") && !FIELDS.contains(data.getOrDefault("field", "")))
                throw new IllegalArgumentException("Unknown event variable");
            for (String key : List.of("expectedField", "expectedField2", "sourceField"))
                if (!data.getOrDefault(key, "").isBlank() && !FIELDS.contains(data.get(key)))
                    throw new IllegalArgumentException("Unknown comparison event variable");
            for (String key : List.of("xField", "yField", "zField", "dimensionField"))
                if (!data.getOrDefault(key, "").isBlank() && !FIELDS.contains(data.get(key))
                        && !data.get(key).startsWith("var:")) throw new IllegalArgumentException("Unknown coordinate source");
            if (type.equals("js") && !List.of("script", "visual").contains(data.getOrDefault("mode", "script")))
                throw new IllegalArgumentException("Invalid predicate mode");
            if (type.equals("js") && data.getOrDefault("mode", "script").equals("visual")
                    && (!(FIELDS.contains(data.getOrDefault("field", "eventType"))
                    || data.getOrDefault("field", "").startsWith("var:"))
                    || !(FIELDS.contains(data.getOrDefault("field2", "eventTarget"))
                    || data.getOrDefault("field2", "").startsWith("var:"))
                    || !OPERATORS.contains(data.getOrDefault("operator", "eq"))
                    || !OPERATORS.contains(data.getOrDefault("operator2", "eq"))
                    || !LOGICS.contains(data.getOrDefault("logic", "single"))))
                throw new IllegalArgumentException("Invalid visual predicate");
            if ((type.equals("variable") || type.equals("set_variable"))
                    && (data.getOrDefault("variable", "").isBlank()
                    || !OPERATORS.contains(data.getOrDefault("operator", "eq"))))
                throw new IllegalArgumentException("Invalid variable node");
            int cooldown = Integer.parseInt(data.getOrDefault("cooldown", "20"));
            if (cooldown < 1 || cooldown > 72000) throw new IllegalArgumentException("Invalid cooldown");
        }
        public String get(String key, String fallback) { return data.getOrDefault(key, fallback); }
    }
    public record Edge(String from, String to, String port) {
        public Edge {
            port = port == null ? "next" : port;
            if (!List.of("next", "true", "false").contains(port)) throw new IllegalArgumentException("Invalid port");
        }
    }
    public TriggerGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        if (nodes.size() > 128 || edges.size() > 256) throw new IllegalArgumentException("Trigger graph too large");
        Map<String, Node> index = new HashMap<>();
        for (Node node : nodes) if (index.put(node.id(), node) != null) throw new IllegalArgumentException("Duplicate node ID");
        Set<Edge> unique = new HashSet<>();
        Map<String, Integer> incoming = new HashMap<>();
        nodes.forEach(node -> incoming.put(node.id(), 0));
        for (Edge edge : edges) {
            Node from = index.get(edge.from()), to = index.get(edge.to());
            if (from == null || to == null || List.of("event", "context").contains(to.type()) || !unique.add(edge)
                    || ACTIONS.contains(from.type())
                    || !branching(from.type()) && !edge.port().equals("next")
                    || branching(from.type()) && edge.port().equals("next"))
                throw new IllegalArgumentException("Invalid trigger connection");
            incoming.compute(edge.to(), (id, value) -> value + 1);
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        incoming.forEach((id, count) -> { if (count == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.remove();
            visited++;
            for (Edge edge : edges) if (edge.from().equals(id) && incoming.compute(edge.to(), (key, n) -> n - 1) == 0)
                ready.add(edge.to());
        }
        if (visited != nodes.size()) throw new IllegalArgumentException("Cyclic trigger graph");
    }
    public static TriggerGraph empty() { return new TriggerGraph(List.of(), List.of()); }
    public static boolean branching(String type) {
        return List.of("js", "variable", "locate_structure", "locate_biome", "teleport", "enter_structure").contains(type)
                || MailNodeSettings.TYPES.contains(type);
    }
    private static void validateSetContextValue(String field, String value) {
        try {
            switch (field) {
                case "experienceLevel" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0 || parsed > 21863) throw new IllegalArgumentException("Invalid experience level");
                }
                case "health" -> {
                    float parsed = Float.parseFloat(value);
                    if (!Float.isFinite(parsed) || parsed < 0 || parsed > 2048)
                        throw new IllegalArgumentException("Invalid health");
                }
                case "foodLevel" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0 || parsed > 20) throw new IllegalArgumentException("Invalid food level");
                }
                case "dayTime" -> {
                    long parsed = Long.parseLong(value);
                    if (parsed < 0) throw new IllegalArgumentException("Invalid day time");
                }
                case "raining", "thundering" -> {
                    if (!List.of("true", "false").contains(value))
                        throw new IllegalArgumentException("Invalid weather state");
                }
                default -> throw new IllegalArgumentException("Unknown game variable");
            }
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid game variable value", invalid);
        }
    }
    public static TriggerGraph buttonEvents() {
        return new TriggerGraph(List.of(
                new Node("click", "event", 20, 20, Map.of("event", "click", "category", "control_event", "cooldown", "1")),
                new Node("hover", "event", 20, 110, Map.of("event", "hover", "category", "control_event", "cooldown", "4")),
                new Node("press", "event", 20, 200, Map.of("event", "press", "category", "control_event", "cooldown", "1")),
                new Node("release", "event", 20, 290, Map.of("event", "release", "category", "control_event", "cooldown", "1"))),
                List.of());
    }
    public TriggerGraph display() {
        return new TriggerGraph(nodes.stream().map(node -> {
            var data = new LinkedHashMap<>(node.data());
            data.remove("script");
            data.remove("function");
            if (node.type().equals("reward") || node.type().equals("mail_send")) {
                try {
                    var rewards = GSON.fromJson(data.getOrDefault("rewards", "[]"), QuestDefinition.Reward[].class);
                    data.put("rewards", rewards == null ? "[]" : GSON.toJson(Arrays.stream(rewards)
                            .map(QuestDefinition.Reward::display).toList()));
                } catch (RuntimeException invalidDraft) {
                    // An unfinished reward draft must not leak executable data into the public catalog.
                    data.put("rewards", "[]");
                }
            }
            return new Node(node.id(), node.type(), node.x(), node.y(), data);
        }).toList(), edges);
    }
}
