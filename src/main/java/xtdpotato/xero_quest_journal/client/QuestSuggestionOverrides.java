package xtdpotato.xero_quest_journal.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local authoring preferences, never edits game registries or quest definitions. */
final class QuestSuggestionOverrides {
    private static final Path FILE = QuestConfig.root().resolve("ui/suggestions.json");
    private static final Map<String, Set<String>> ADDED = new LinkedHashMap<>();
    private static final Map<String, Set<String>> HIDDEN = new LinkedHashMap<>();
    private static boolean loaded;

    private QuestSuggestionOverrides() {}

    static synchronized List<QuestValuePickerScreen.Option> apply(String context,
                                                                    List<QuestValuePickerScreen.Option> base) {
        load();
        List<QuestValuePickerScreen.Option> result = new ArrayList<>();
        Set<String> hidden = HIDDEN.getOrDefault(context, Set.of());
        for (var option : base) if (option.value().isEmpty() || !hidden.contains(option.value())) result.add(option);
        for (String value : ADDED.getOrDefault(context, Set.of())) {
            if (result.stream().noneMatch(option -> option.value().equals(value)))
                result.add(new QuestValuePickerScreen.Option(value, value));
        }
        return List.copyOf(result);
    }

    static synchronized boolean add(String context, String value) {
        load();
        if (context == null || value == null) return false;
        value = value.strip();
        if (context.isBlank() || value.isEmpty() || value.length() > 8192
                || value.contains("\n") || value.contains("\r")) return false;
        Set<String> previousAdded = new LinkedHashSet<>(ADDED.getOrDefault(context, Set.of()));
        Set<String> previousHidden = new LinkedHashSet<>(HIDDEN.getOrDefault(context, Set.of()));
        HIDDEN.computeIfAbsent(context, ignored -> new LinkedHashSet<>()).remove(value);
        ADDED.computeIfAbsent(context, ignored -> new LinkedHashSet<>()).add(value);
        if (save()) return true;
        if (previousAdded.isEmpty()) ADDED.remove(context);
        else ADDED.put(context, previousAdded);
        if (previousHidden.isEmpty()) HIDDEN.remove(context);
        else HIDDEN.put(context, previousHidden);
        return false;
    }

    static synchronized boolean remove(String context, String value) {
        return removeAll(context, List.of(value));
    }

    static synchronized boolean removeAll(String context, Collection<String> values) {
        load();
        if (context == null || context.isBlank() || values == null || values.isEmpty()) return false;
        Set<String> previousAdded = new LinkedHashSet<>(ADDED.getOrDefault(context, Set.of()));
        Set<String> previousHidden = new LinkedHashSet<>(HIDDEN.getOrDefault(context, Set.of()));
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            ADDED.computeIfAbsent(context, ignored -> new LinkedHashSet<>()).remove(value);
            HIDDEN.computeIfAbsent(context, ignored -> new LinkedHashSet<>()).add(value);
        }
        if (save()) return true;
        if (previousAdded.isEmpty()) ADDED.remove(context);
        else ADDED.put(context, previousAdded);
        if (previousHidden.isEmpty()) HIDDEN.remove(context);
        else HIDDEN.put(context, previousHidden);
        return false;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        try {
            JsonObject data = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            read(data, "added", ADDED);
            read(data, "hidden", HIDDEN);
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to read quest suggestion preferences", failure);
        }
    }

    private static void read(JsonObject data, String name, Map<String, Set<String>> target) {
        if (!data.has(name) || !data.get(name).isJsonObject()) return;
        data.getAsJsonObject(name).entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonArray()) return;
            Set<String> values = new LinkedHashSet<>();
            entry.getValue().getAsJsonArray().forEach(element -> {
                if (element.isJsonPrimitive() && element.getAsString().length() <= 8192)
                    values.add(element.getAsString());
            });
            target.put(entry.getKey(), values);
        });
    }

    private static boolean save() {
        try {
            JsonObject data = new JsonObject();
            write(data, "added", ADDED);
            write(data, "hidden", HIDDEN);
            AtomicFiles.write(FILE, data.toString(), true);
            return true;
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to save quest suggestion preferences", failure);
            return false;
        }
    }

    private static void write(JsonObject data, String name, Map<String, Set<String>> values) {
        JsonObject object = new JsonObject();
        values.forEach((context, entries) -> {
            if (entries.isEmpty()) return;
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            entries.forEach(array::add);
            object.add(context, array);
        });
        data.add(name, object);
    }
}
