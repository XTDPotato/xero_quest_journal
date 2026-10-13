package xtdpotato.xero_quest_journal.core.model;

import java.util.List;

/** Source examples shared by the in-game reference and executable documentation checks. */
public final class QuestScriptReference {
    public enum Kind { GRAPH_PREDICATE, CONDITION_FILE, REWARD_FILE, KUBEJS }
    public record Example(String id, String source) {}
    public record Field(String name, String type) {}

    private QuestScriptReference() {}

    public static List<Example> examples(Kind kind) {
        return switch (kind) {
            case GRAPH_PREDICATE -> List.of(
                    new Example("click", "eventType === \"click\";"),
                    new Example("level_graph", "Number(experienceLevel) >= 10;"),
                    new Example("region_graph", "dimension === \"minecraft:overworld\" && Number(y) >= 64;"),
                    new Example("sneaking", "sneaking === \"true\" && Number(health) < Number(maxHealth);"),
                    new Example("function_graph", "(function () {\n    const level = Number(experienceLevel);\n    return eventType === \"click\" && level >= 10;\n})();"),
                    new Example("pointer", "eventType === \"hold\" && mouseInside === \"true\";"),
                    new Example("located", "locationFound === \"true\" && locatedDimension === dimension;"));
            case CONDITION_FILE -> List.of(
                    new Example("level", "experienceLevel >= 10;"),
                    new Example("dimension", "dimension === \"minecraft:overworld\";"),
                    new Example("region", "dimension === \"minecraft:overworld\" && x >= 0 && x <= 100 && z >= 0 && z <= 100;"),
                    new Example("function", "(function () {\n    const correctWorld = dimension === \"minecraft:overworld\";\n    return correctWorld && experienceLevel >= 10;\n})();"));
            case REWARD_FILE -> List.of(
                    new Example("message", "message(\"Congratulations, \" + playerName + \"!\");"),
                    new Example("command", "if (!command(\"say Quest reward claimed\")) {\n    throw new Error(\"Reward command failed\");\n}"),
                    new Example("reward_branch", "if (experienceLevel >= 10) {\n    message(\"Level requirement met\");\n} else {\n    message(\"Reward received\");\n}"));
            case KUBEJS -> List.of(
                    new Example("kubejs", "if (typeof global.onXeroQuestAction !== \"function\") {\n    throw new Error(\"Define global.onXeroQuestAction in kubejs/server_scripts first\");\n}\nglobal.onXeroQuestAction(event);"));
        };
    }

    public static List<Field> fields(Kind kind) {
        if (kind == Kind.GRAPH_PREDICATE || kind == Kind.KUBEJS)
            return TriggerGraph.FIELDS.stream().map(name -> new Field(
                    kind == Kind.KUBEJS ? "event." + name : name, "string")).toList();
        return List.of(new Field("playerName", "string"), new Field("playerId", "string"),
                new Field("dimension", "string"), new Field("x", "number"), new Field("y", "number"),
                new Field("z", "number"), new Field("experienceLevel", "number"));
    }

    public static int characterLimit(Kind kind) {
        return kind == Kind.CONDITION_FILE || kind == Kind.REWARD_FILE ? 65535 : 16384;
    }
}
