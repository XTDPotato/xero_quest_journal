package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.platform.PlatformServices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Each chronicle entry has its own JSON file in the server config directory. */
public final class AnecdoteStore {
    public record RecordRef(String entryId, String recordId) {}
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Path ROOT = PlatformServices.configDir().resolve("xero_quest/anecdotes")
            .toAbsolutePath().normalize();
    private static Map<String, AnecdoteDefinition> entries = Map.of();
    private static Map<String, RecordRef> generatedQuestRecords = Map.of();
    private static long revision;

    private AnecdoteStore() {}

    public static synchronized void refresh() {
        Map<String, AnecdoteDefinition> next = new LinkedHashMap<>();
        try {
            Files.createDirectories(ROOT);
            try (var paths = Files.list(ROOT)) {
                for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    try {
                        AnecdoteDefinition value = GSON.fromJson(Files.readString(path), AnecdoteDefinition.class);
                        if (value == null || !path.equals(file(value.id())))
                            throw new IllegalArgumentException("File name must match its anecdote ID");
                        if (next.putIfAbsent(value.id(), value) != null)
                            throw new IllegalArgumentException("Duplicate anecdote ID");
                    } catch (Exception invalid) {
                        XeroQuestJournal.LOGGER.warn("Ignoring invalid anecdote definition {}", path, invalid);
                    }
                }
            }
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to read anecdote definitions from {}", ROOT, failure);
        }
        var questRecords = new LinkedHashMap<String, RecordRef>();
        for (var entry : next.values()) for (var record : entry.records()) {
            if (!record.generatedQuestId().isBlank())
                questRecords.putIfAbsent(record.generatedQuestId(),
                        new RecordRef(entry.id(), record.id()));
        }
        entries = Map.copyOf(next);
        generatedQuestRecords = Map.copyOf(questRecords);
        revision++;
    }

    public static synchronized List<AnecdoteDefinition> all() {
        return entries.values().stream().sorted(Comparator.comparingInt(AnecdoteDefinition::order)
                .thenComparing(AnecdoteDefinition::id)).toList();
    }

    public static synchronized AnecdoteDefinition find(String id) {
        return entries.get(id);
    }

    public static synchronized long revision() { return revision; }

    public static synchronized RecordRef generatedRecord(String questId) {
        return generatedQuestRecords.get(questId);
    }

    public static Path root() { return ROOT; }

    public static Path fileFor(String id) { return file(id); }

    public static synchronized String nextId() {
        for (int index = 1; index <= 100_000; index++) {
            String id = "anecdote_" + index;
            if (!entries.containsKey(id)) return id;
        }
        throw new IllegalStateException("No anecdote IDs available");
    }

    public static synchronized void save(AnecdoteDefinition value) throws Exception {
        if (value == null) throw new IllegalArgumentException("Missing anecdote");
        AtomicFiles.write(file(value.id()), GSON.toJson(value), true);
        refresh();
    }

    public static synchronized boolean delete(String id) throws Exception {
        Path path = file(id);
        boolean removed = Files.deleteIfExists(path);
        if (removed) refresh();
        return removed;
    }

    private static Path file(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid anecdote ID");
        Path path = ROOT.resolve(id + ".json").normalize();
        if (!path.startsWith(ROOT)) throw new IllegalArgumentException("Invalid anecdote path");
        return path;
    }
}
