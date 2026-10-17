package xtdpotato.xero_quest_journal.core.model;

import java.util.*;

/** Authoring presets; stable runtime types keep existing graphs compatible. */
public final class TriggerNodeLibrary {
    public static final List<String> CATEGORIES = List.of("chapter_event", "player_event", "player_value", "world_event",
            "world_value", "control_event", "control_value", "complete", "advance_step", "activate", "unlock", "lock",
            "set_property",
            "reset_property", "set_task_property", "discover_anecdote", "complete_anecdote",
            "set_theme", "set_overview_background", "set_hot_event_background", "reward", "condition", "script_condition",
            "kubejs_call", "kubejs_script", "delay", "set_context",
            "variable", "set_variable", "calculate_variable", "command", "mail_send", "mail_check", "mail_delete", "mail_set_property",
            "mail_lock_task", "mail_unlock_task",
            "locate_structure", "locate_biome", "teleport", "enter_structure", "end");
    private static final Map<String, List<String>> GROUPS = Map.of(
            "task_actions", List.of("complete", "advance_step", "activate", "unlock", "lock",
                    "set_task_property", "discover_anecdote", "complete_anecdote",
                    "command"),
            "control_properties", List.of("set_property", "reset_property"),
            "logic", List.of("condition", "script_condition", "variable", "set_variable", "calculate_variable"),
            "kubejs", List.of("kubejs_call", "kubejs_script"),
            "mail", List.of("mail_send", "mail_check", "mail_delete", "mail_set_property",
                    "mail_lock_task", "mail_unlock_task"),
            "location", List.of("locate_structure", "locate_biome", "teleport", "enter_structure"));
    public static final List<String> PALETTE_CATEGORIES = List.of("chapter_event", "player_event", "player_value",
            "world_event", "world_value", "control_event", "control_value", "task_actions", "delay", "set_context",
            "control_properties", "set_theme", "set_overview_background", "set_hot_event_background", "reward", "logic", "kubejs", "mail", "location", "end");
    public static final List<String> PLAYER_EVENTS = List.of("jump", "join", "leave", "dimension",
            "death", "respawn", "kill", "block_break", "block_interact", "item_pickup", "craft",
            "smelt", "sleep", "wake", "advancement", "tick");
    public static final List<String> WORLD_EVENTS = List.of("world_tick", "day_start", "night_start",
            "rain_start", "rain_stop", "thunder_start", "thunder_stop");
    public static final List<String> PLAYER_VALUES = List.of("playerName", "playerId", "health", "maxHealth",
            "foodLevel", "experienceLevel", "x", "y", "z", "sneaking", "sprinting", "eventType", "eventTarget");
    public static final List<String> WORLD_VALUES = List.of("dimension", "dayTime", "gameTime", "raining",
            "thundering", "difficulty", "locatedX", "locatedY", "locatedZ", "locatedDimension", "locationFound");
    public static final List<String> CONTROL_VALUES = List.of("questId", "controlId", "controlKind",
            "controlValue", "controlX", "controlY", "controlWidth", "controlHeight",
            "mouseX", "mouseY", "mouseWidth", "mouseHeight", "mouseHeld", "mouseInside");
    public record Entry(String category, String value, String type, Map<String, String> data) {
        public String labelKey() {
            return category.endsWith("_event") ? "event." + value
                    : category.endsWith("_value") ? "field." + value : "category." + category;
        }
    }
    private TriggerNodeLibrary() {}
    public static List<Entry> paletteEntries(String category) {
        return GROUPS.getOrDefault(category, List.of(category)).stream()
                .flatMap(member -> entries(member).stream()).toList();
    }
    public static String requiredMod(String category) { return category.startsWith("kubejs_") ? "kubejs" : ""; }
    public static List<String> events(String category) {
        return switch (category) {
            case "chapter_event" -> List.of("chapter_switch_after", "chapter_switch_before");
            case "player_event" -> PLAYER_EVENTS;
            case "world_event" -> WORLD_EVENTS;
            case "control_event" -> TriggerGraph.CONTROL_EVENTS;
            default -> TriggerGraph.EVENTS;
        };
    }
    public static List<String> fields(String category) {
        return switch (category) {
            case "player_value" -> PLAYER_VALUES;
            case "world_value" -> WORLD_VALUES;
            case "control_value" -> CONTROL_VALUES;
            default -> TriggerGraph.FIELDS;
        };
    }
    public static List<Entry> entries(String category) {
        if (category.endsWith("_event")) return events(category).stream().map(event ->
                new Entry(category, event, "event", Map.of("event", event, "category", category,
                        "cooldown", "20", "once", "false"))).toList();
        if (category.endsWith("_value")) return fields(category).stream().map(field ->
                new Entry(category, field, "context", Map.of("field", field, "category", category))).toList();
        return List.of(new Entry(category, category, switch (category) {
            case "condition", "script_condition" -> "js";
            default -> category;
        }, switch (category) {
            case "condition" -> Map.of("mode", "visual", "field", "eventType", "operator", "eq", "expected", "");
            case "script_condition" -> Map.of("mode", "script", "script", "eventType === 'jump'");
            case "set_property" -> Map.of("property", "visibility", "value", "visible");
            case "set_task_property" -> Map.of("property", "hidden", "value", "true");
            case "discover_anecdote" -> Map.of("target", "");
            case "complete_anecdote" -> Map.of("entry", "", "record", "");
            case "reset_property" -> Map.of("property", "visibility", "all", "false");
            case "set_theme" -> Map.of("key", "accent", "value", "#F2D29A", "once", "true");
            case "set_overview_background" -> Map.of("source", "", "scope", "player", "once", "true");
            case "set_hot_event_background" -> Map.of("source", "", "type", "image", "mode", "cover",
                    "scale", "1", "offsetX", "0", "offsetY", "0", "music", "", "once", "true");
            case "reward" -> Map.of("rewards", "[]", "once", "true");
            case "command" -> Map.of("command", "");
            case "delay" -> Map.of("ticks", "20");
            case "set_context" -> Map.of("scope", "player", "field", "experienceLevel",
                    "player", "@s", "dimension", "", "value", "0");
            case "calculate_variable" -> Map.of("operation", "add", "operand", "1", "operandVariable", "");
            case "mail_send" -> Map.of("mailId", "", "sender", "", "subject", "", "body", "",
                    "rewards", "[]", "expiresIn", "0", "once", "true");
            case "mail_check" -> Map.of("mailId", "", "state", "exists");
            case "mail_delete" -> Map.of("mailId", "", "discardPending", "false");
            case "mail_set_property" -> Map.of("mailId", "", "property", "subject", "value", "");
            case "mail_lock_task", "mail_unlock_task" -> Map.of("target", "", "player", "");
            case "locate_structure" -> Map.of("target", "", "dimension", "", "radius", "16",
                    "storeVariable", "globe.located_structure");
            case "locate_biome" -> Map.of("target", "", "dimension", "", "radius", "256");
            case "teleport" -> Map.of("dimension", "", "x", "", "y", "", "z", "");
            case "enter_structure" -> Map.of("structure", "", "x", "", "y", "", "z", "");
            case "kubejs_call" -> Map.of("function", "", "once", "true");
            case "kubejs_script" -> Map.of("script", "// event contains the trigger context\n", "once", "true");
            default -> Map.of();
        }));
    }
}
