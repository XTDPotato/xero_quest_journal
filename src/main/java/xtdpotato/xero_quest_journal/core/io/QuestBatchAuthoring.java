package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A batch is validated as a whole and backed up before changing any definition. */
public final class QuestBatchAuthoring {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public record Result(Map<String, String> copies, String error) {
        public boolean successful() { return error == null; }
    }
    private QuestBatchAuthoring() {}

    public static Result paste(ServerPlayer author, List<QuestClipboard.Task> tasks, String groupId,
                               String chapterId, String suffix) {
        if (!allowed(author)) return failed("permission");
        synchronized (QuestRepository.class) {
            try {
                tasks = QuestClipboard.decode(QuestClipboard.encode(tasks));
                QuestRepository.reload();
                if (!QuestRepository.errors().isEmpty()) return failed("definitions");
                var group = QuestRepository.groups().stream().filter(g -> g.id().equals(groupId)).findFirst().orElse(null);
                if (group == null) return failed("group");
                var chapter = QuestRepository.chapters().stream().filter(c -> c.id().equals(chapterId)
                        && c.groupId().equals(groupId)).findFirst().orElse(null);
                if (!chapterId.isEmpty() && chapter == null) return failed("chapter");
                Map<String, String> ids = new LinkedHashMap<>();
                for (var task : tasks) {
                    String id;
                    do { id = QuestAuthoring.newId(); } while (QuestRepository.idExists(id) || ids.containsValue(id));
                    ids.put(task.id(), id);
                }
                Map<Path, String> writes = new LinkedHashMap<>();
                for (var task : tasks) {
                    var definition = QuestRepository.parseDefinition(Path.of("task" + task.extension()), task.source());
                    String name = definition.title() + suffix;
                    String source;
                    if (task.extension().equals(".json")) {
                        JsonObject object = JsonParser.parseString(task.source()).getAsJsonObject();
                        object.addProperty("id", ids.get(task.id()));
                        object.addProperty("uid", UUID.randomUUID().toString());
                        object.addProperty("title", name);
                        object.remove("titleKey");
                        JsonArray parents = new JsonArray();
                        definition.parents().forEach(id -> parents.add(ids.getOrDefault(id, id)));
                        object.add("parents", parents);
                        source = GSON.toJson(object);
                    } else {
                        source = QuestDocuments.identity(task.source(), ids.get(task.id()), name, UUID.randomUUID().toString());
                        source = QuestDocuments.remapParents(source, ids);
                    }
                    if (definition.presentation().authored())
                        source = QuestPresentationDocuments.write(source, definition.presentation().remapTasks(ids));
                    writes.put(safe(group.directory().resolve("quest_" + ids.get(task.id()) + task.extension())), source);
                }
                if (chapter != null && (!chapter.quests().isEmpty() || chapter.timeline().explicitTasks())) {
                    JsonObject object = JsonParser.parseString(QuestRepository.source(chapter.id())).getAsJsonObject();
                    JsonArray refs = object.has("quests") ? object.getAsJsonArray("quests") : new JsonArray();
                    ids.values().forEach(refs::add);
                    object.add("quests", refs);
                    writes.put(safe(chapter.file()), GSON.toJson(object));
                }
                apply(author, ids.values().iterator().next(), "paste", writes, Set.of());
                return new Result(Map.copyOf(ids), null);
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to paste quest batch", exception);
                return failed("paste");
            }
        }
    }

    public static Result delete(ServerPlayer author, Set<String> taskIds, Set<String> groupIds) {
        if (!allowed(author)) return failed("permission");
        return deleteDefinitions(author, taskIds, groupIds, Set.of());
    }

    static Result deleteGenerated(net.minecraft.server.MinecraftServer server, Set<String> taskIds,
                                  Set<String> groupIds, Set<String> chapterIds) {
        if (server == null || !server.isSameThread()) return failed("permission");
        return deleteDefinitions(null, taskIds, groupIds, chapterIds);
    }

    private static Result deleteDefinitions(ServerPlayer author, Set<String> taskIds, Set<String> groupIds, Set<String> chapterIds) {
        synchronized (QuestRepository.class) {
            try {
                QuestRepository.reload();
                if (!QuestRepository.errors().isEmpty()) return failed("definitions");
                Set<Path> directories = new LinkedHashSet<>();
                for (String id : groupIds) {
                    var group = QuestRepository.groups().stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
                    directories.add(safe(group.directory()));
                }
                Set<Path> deletes = new LinkedHashSet<>();
                for (var group : QuestRepository.groups()) {
                    if (directories.stream().anyMatch(group.directory()::startsWith)) {
                        deletes.add(safe(QuestRepository.fileOf(group.id())));
                    }
                }
                Set<String> removedTasks = new HashSet<>(taskIds);
                for (var quest : QuestRepository.all()) {
                    Path file = QuestRepository.fileOf(quest.id());
                    if (file != null && directories.stream().anyMatch(file::startsWith)) removedTasks.add(quest.id());
                }
                for (String id : removedTasks) {
                    if (QuestRepository.find(id) == null) return failed("no_source");
                    deletes.add(safe(QuestRepository.fileOf(id)));
                }
                Map<Path, String> writes = new LinkedHashMap<>();
                for (var quest : QuestRepository.all()) {
                    if (removedTasks.contains(quest.id()) || Collections.disjoint(quest.parents(), removedTasks)) continue;
                    Path file = safe(QuestRepository.fileOf(quest.id()));
                    String source = QuestRepository.source(quest.id());
                    if (file.toString().endsWith(".json")) {
                        JsonObject data = JsonParser.parseString(source).getAsJsonObject();
                        JsonArray parents = new JsonArray();
                        quest.parents().stream().filter(id -> !removedTasks.contains(id)).forEach(parents::add);
                        data.add("parents", parents);
                        source = GSON.toJson(data);
                    } else {
                        for (String id : removedTasks) source = QuestDocuments.removeParent(source, id);
                    }
                    writes.put(file, source);
                }
                for (var chapter : QuestRepository.chapters()) {
                    Path file = safe(chapter.file());
                    if (chapterIds.contains(chapter.id()) || directories.stream().anyMatch(file::startsWith)
                            || deletes.contains(QuestRepository.fileOf(chapter.groupId()))) {
                        deletes.add(file);
                    } else if (!Collections.disjoint(chapter.quests(), removedTasks)) {
                        JsonObject data = JsonParser.parseString(QuestRepository.source(chapter.id())).getAsJsonObject();
                        JsonArray refs = new JsonArray();
                        chapter.quests().stream().filter(id -> !removedTasks.contains(id)).forEach(refs::add);
                        data.add("quests", refs);
                        writes.put(file, GSON.toJson(data));
                    }
                }
                if (deletes.isEmpty()) return failed("no_source");
                apply(author, removedTasks.stream().sorted().findFirst().orElse(""), "delete", writes, deletes);
                return new Result(Map.of(), null);
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to delete quest batch", exception);
                return failed("save");
            }
        }
    }

    private static boolean allowed(ServerPlayer player) {
        return player != null && player.getServer() != null && player.getServer().isSameThread() && player.hasPermissions(2);
    }
    private static Result failed(String key) {
        return new Result(Map.of(), "create.error." + key);
    }
    private static Path safe(Path path) throws IOException {
        if (path == null) throw new IOException("Missing source");
        Path root = QuestRepository.root();
        Path resolved = path.toAbsolutePath().normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) throw new IOException("Invalid definition path");
        for (Path parent = resolved; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) throw new IOException("Symbolic link in definition path");
        }
        return resolved;
    }
    private static void apply(ServerPlayer author, String id, String kind,
                              Map<Path, String> writes, Set<Path> deletes) throws IOException {
        Map<Path, String> originals = new LinkedHashMap<>();
        Set<Path> affected = new LinkedHashSet<>(writes.keySet());
        affected.addAll(deletes);
        for (Path file : affected) {
            safe(file);
            originals.put(file, Files.exists(file) ? Files.readString(file) : null);
        }
        Path backup = QuestRepository.root().getParent().resolve("history")
                .resolve("batch-" + System.currentTimeMillis() + "-" + UUID.randomUUID());
        for (Path parent = backup; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) throw new IOException("Symbolic link in history path");
        }
        JsonObject journal = new JsonObject();
        originals.forEach((file, source) -> journal.add(QuestRepository.root().relativize(file).toString(),
                source == null ? JsonNull.INSTANCE : new JsonPrimitive(source)));
        AtomicFiles.write(backup.resolve("before.json"), GSON.toJson(journal), false);
        try {
            for (var entry : writes.entrySet()) AtomicFiles.write(entry.getKey(), entry.getValue(), originals.get(entry.getKey()) != null);
            for (Path file : deletes) Files.delete(file);
            QuestRepository.reload();
            if (!QuestRepository.errors().isEmpty()) throw new IOException("Invalid batch; restoring definitions");
            QuestAuthoringHistory.remember(author, id, kind, originals);
        } catch (Exception failure) {
            for (var entry : originals.entrySet()) {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                else AtomicFiles.write(entry.getKey(), entry.getValue(), Files.exists(entry.getKey()));
            }
            QuestRepository.reload();
            throw new IOException("Batch rejected", failure);
        }
    }
}
