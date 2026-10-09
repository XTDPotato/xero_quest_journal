package xtdpotato.xero_quest_journal.core.model;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;

/** Explicit task/control pairs avoid collisions between different tasks. */
public record QuestControlTarget(String quest, String control) {
    private static final Gson JSON = new Gson();
    public QuestControlTarget {
        quest = quest == null ? "" : quest;
        control = control == null ? "" : control;
        if (quest.length() > 64 || !quest.isBlank() && !quest.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")
                || control.isBlank() || control.length() > 256)
            throw new IllegalArgumentException("Invalid control target");
    }
    public static List<QuestControlTarget> decode(String source) {
        if (source == null || source.isBlank()) return List.of();
        if (source.length() > 16384) throw new IllegalArgumentException("Control targets exceed limits");
        var values = JSON.fromJson(source, QuestControlTarget[].class);
        if (values == null || values.length > 128) throw new IllegalArgumentException("Too many control targets");
        return List.of(values).stream().distinct().toList();
    }
    public static String encode(List<QuestControlTarget> targets) {
        String source = JSON.toJson(targets);
        decode(source);
        return source;
    }
    public static List<QuestControlTarget> of(Map<String, String> data, String quest, String control) {
        var targets = decode(data.getOrDefault("controls", ""));
        return targets.isEmpty() ? List.of(new QuestControlTarget(
                data.getOrDefault("controlQuest", "").isBlank() ? quest : data.get("controlQuest"),
                data.getOrDefault("control", "").isBlank() ? control : data.get("control"))) : targets;
    }
}
