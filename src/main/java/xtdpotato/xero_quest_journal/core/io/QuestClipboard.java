package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.*;

import java.nio.file.Path;
import java.util.*;

/** Portable source snapshots, never filesystem paths or executable clipboard commands. */
public final class QuestClipboard {
    public static final int LIMIT = 4 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().create();
    public record Task(String id, String extension, String source) {}

    private QuestClipboard() {}

    public static List<Task> capture(Collection<String> ids) throws java.io.IOException {
        List<Task> tasks = new ArrayList<>();
        for (String id : new LinkedHashSet<>(ids)) {
            Path file = QuestRepository.fileOf(id);
            if (file == null || QuestRepository.find(id) == null) throw new IllegalArgumentException("Missing task");
            String name = file.getFileName().toString();
            tasks.add(new Task(id, name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT),
                    QuestRepository.source(id)));
        }
        return decode(encode(tasks));
    }

    public static String encode(List<Task> tasks) {
        JsonObject data = new JsonObject();
        data.addProperty("format", "xero-quest-clipboard");
        data.addProperty("version", 1);
        data.add("tasks", GSON.toJsonTree(tasks));
        String result = GSON.toJson(data);
        if (result.length() > LIMIT) throw new IllegalArgumentException("Clipboard too large");
        return result;
    }

    public static List<Task> decode(String json) {
        if (json == null || json.length() > LIMIT) throw new IllegalArgumentException("Invalid clipboard size");
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        if (!"xero-quest-clipboard".equals(data.get("format").getAsString())
                || data.get("version").getAsInt() != 1) throw new IllegalArgumentException("Clipboard format");
        JsonArray array = data.getAsJsonArray("tasks");
        if (array.isEmpty() || array.size() > 128) throw new IllegalArgumentException("Clipboard task count");
        List<Task> tasks = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement value : array) {
            Task task = GSON.fromJson(value, Task.class);
            if (task.id() == null || !ids.add(task.id()) || task.source() == null
                    || task.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1_048_576
                    || !Set.of(".html", ".htm", ".xml", ".json").contains(task.extension())) {
                throw new IllegalArgumentException("Invalid clipboard task");
            }
            if (!QuestRepository.parseDefinition(Path.of("task" + task.extension()), task.source()).id().equals(task.id())) {
                throw new IllegalArgumentException("Clipboard ID mismatch");
            }
            tasks.add(task);
        }
        return List.copyOf(tasks);
    }
}
