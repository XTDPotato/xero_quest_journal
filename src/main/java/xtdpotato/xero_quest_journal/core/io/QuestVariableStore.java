package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.QuestControlData;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-authoritative variable definitions shared by all graphs in their scope. */
public final class QuestVariableStore {
    private static final Gson GSON = new Gson();
    private static final Path FILE = QuestConfig.root().resolve("variables.json");
    private static List<QuestVariable> shared = List.of();
    private static List<QuestVariable> snapshot = List.of();

    private QuestVariableStore() {}

    public static synchronized List<QuestVariable> all() { return snapshot; }
    public static synchronized QuestVariable find(String id) {
        QuestVariable existing = snapshot.stream().filter(variable -> variable.id().equals(id))
                .findFirst().orElse(null);
        if (existing != null) return existing;
        if (id == null) return null;
        for (String suffix : List.of("progress_current", "progress_required", "progress_ratio")) {
            String ending = "." + suffix;
            if (!id.endsWith(ending)) continue;
            String taskId = id.substring(0, id.length() - ending.length());
            if (xtdpotato.xero_quest_journal.core.io.QuestRepository.find(taskId) != null)
                return new QuestVariable(id, "0", 0, "number", "player");
        }
        return null;
    }
    public static boolean builtInLocation(String id) {
        return builtInLocationVariables().stream().anyMatch(variable -> variable.id().equals(id));
    }
    public static List<QuestVariable> progressVariables(String taskId) {
        return List.of("progress_current", "progress_required", "progress_ratio").stream()
                .map(name -> find(taskId + "." + name)).filter(Objects::nonNull).toList();
    }
    public static synchronized void refresh() {
        try {
            if (!Files.exists(FILE, LinkOption.NOFOLLOW_LINKS)) {
                shared = List.of();
                return;
            }
            if (!Files.isRegularFile(FILE, LinkOption.NOFOLLOW_LINKS) || Files.size(FILE) > 1048576)
                throw new IllegalArgumentException("Invalid variables file");
            List<QuestVariable> parsed = GSON.fromJson(Files.readString(FILE),
                    new TypeToken<List<QuestVariable>>() {}.getType());
            if (parsed == null || parsed.size() > 1024) throw new IllegalArgumentException("Too many variables");
            var ids = new HashSet<String>();
            for (QuestVariable variable : parsed) {
                new QuestVariable(variable.id(), variable.initial(), variable.created(),
                        variable.type(), variable.sharing());
                if (!ids.add(variable.id())) throw new IllegalArgumentException("Duplicate variable ID");
            }
            shared = List.copyOf(parsed);
        } catch (Exception invalid) {
            XeroQuestJournal.LOGGER.warn("Invalid variable definitions; keeping last valid shared definitions", invalid);
        }
    }
    public static synchronized void setTasks(List<QuestDefinition> tasks) {
        Map<String, QuestVariable> combined = new LinkedHashMap<>();
        Set<String> taskIds = new HashSet<>();
        for (QuestDefinition task : tasks) taskIds.add(task.id());
        for (QuestVariable variable : shared) {
            if (variable.scope().equals("globe") || taskIds.contains(variable.scope()))
                combined.put(variable.id(), variable);
        }
        for (QuestVariable variable : builtInLocationVariables()) combined.put(variable.id(), variable);
        for (QuestVariable variable : builtInLocationVariables()) combined.put(variable.id(), variable);
        for (QuestDefinition task : tasks) {
            for (QuestVariable variable : task.presentation().variables()) {
                if (!variable.scope().equals(task.id()))
                    throw new IllegalArgumentException("Variable belongs to another task: " + variable.id());
                combined.put(variable.id(), variable); // Authored task definitions supersede legacy shared definitions.
            }
        }
        if (combined.size() > 1024) throw new IllegalArgumentException("Too many variables");
        snapshot = List.copyOf(combined.values());
    }
    private static List<QuestVariable> builtInLocationVariables() {
        return List.of(
                new QuestVariable("globe.located_structure", "", 0, "string", "team"),
                new QuestVariable("globe.located_structure_x", "0", 0, "number", "team"),
                new QuestVariable("globe.located_structure_y", "0", 0, "number", "team"),
                new QuestVariable("globe.located_structure_z", "0", 0, "number", "team"),
                new QuestVariable("globe.located_structure_dimension", "", 0, "string", "team"),
                new QuestVariable("globe.located_structure_id", "", 0, "string", "team"));
    }
    public record Result(String id, String error) {
        public boolean successful() { return error == null; }
    }
    /**
     * A global edit can update every task reference, or fork into the current task.
     * Task-scoped edits always affect only their owning task.
     */
    public static Result save(ServerPlayer author, String taskId, String oldId, String id,
                              String initial, boolean currentOnly) {
        QuestVariable old = oldId == null || oldId.isBlank() ? null : find(oldId);
        return save(author, taskId, oldId, id, initial,
                old == null ? null : old.type(), old == null ? "player" : old.sharing(), currentOnly);
    }

    public static Result save(ServerPlayer author, String taskId, String oldId, String id,
                              String initial, String type, String sharing, boolean currentOnly) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return new Result(id, "permission");
        synchronized (QuestRepository.class) {
            try {
                if (QuestRepository.find(taskId) == null) return new Result(id, "missing_task");
                QuestVariable next = new QuestVariable(id, initial, System.currentTimeMillis(), type, sharing);
                QuestVariable old = oldId == null || oldId.isBlank() ? null : find(oldId);
                if (next.readOnly() || builtInLocation(id) || old != null
                        && (old.readOnly() || builtInLocation(old.id()))) return new Result(id, "readonly");
                if (oldId != null && !oldId.isBlank() && old == null) return new Result(id, "missing_variable");
                if (!next.scope().equals("globe") && !next.scope().equals(taskId))
                    return new Result(id, "scope");
                if (old != null && !old.scope().equals("globe") && !old.scope().equals(taskId))
                    return new Result(id, "scope");
                if (old != null && old.scope().equals("globe") && currentOnly && !next.scope().equals(taskId))
                    return new Result(id, "scope");
                if (old != null && !old.scope().equals("globe") && !next.scope().equals(taskId))
                    return new Result(id, "scope");
                if (old != null && old.scope().equals("globe") && !currentOnly && !next.scope().equals("globe"))
                    return new Result(id, "scope");
                if (find(id) != null && (old == null || !old.id().equals(id) || currentOnly))
                    return new Result(id, "duplicate");
                QuestVariable saved = new QuestVariable(id, initial,
                        old == null || currentOnly ? next.created() : old.created(), next.type(), next.sharing());
                if (snapshot.size() + (old == null || currentOnly ? 1 : 0) > 1024)
                    return new Result(id, "limit");
                List<QuestVariable> updatedShared = new ArrayList<>(shared);
                if (old != null && !currentOnly) updatedShared.removeIf(variable -> variable.id().equals(old.id()));
                if (saved.scope().equals("globe")) updatedShared.add(saved);
                // A legacy task variable is migrated out of the shared file when edited.
                else updatedShared.removeIf(variable -> variable.id().equals(saved.id()));

                Map<Path, String> previous = new LinkedHashMap<>();
                Map<Path, String> replacements = new LinkedHashMap<>();
                for (var task : QuestRepository.all()) {
                    boolean ownsVariable = saved.scope().equals(task.id());
                    boolean rewritesReferences = old != null && !old.id().equals(id)
                            && (!currentOnly && old.scope().equals("globe") || task.id().equals(taskId));
                    if (!ownsVariable && !rewritesReferences) continue;
                    QuestPresentation presentation = task.presentation();
                    QuestPresentation rewritten = rewritesReferences
                            ? presentation.renameVariable(old.id(), id) : presentation;
                    if (ownsVariable) {
                        List<QuestVariable> variables = new ArrayList<>(rewritten.variables());
                        if (old != null && !currentOnly)
                            variables.removeIf(variable -> variable.id().equals(old.id()));
                        variables.removeIf(variable -> variable.id().equals(saved.id()));
                        variables.add(saved);
                        if (variables.size() > 256) return new Result(id, "limit");
                        rewritten = new QuestPresentation(rewritten.authored(), rewritten.width(),
                                rewritten.height(), rewritten.controls(), variables);
                    }
                    if (rewritten.equals(presentation)) continue;
                    Path file = QuestRepository.fileOf(task.id());
                    if (file == null) return new Result(id, "missing_task");
                    String source = Files.readString(file);
                    if (!QuestRepository.parseDefinition(file, source).presentation().equals(presentation))
                        return new Result(id, "conflict");
                    String replacement = QuestPresentationDocuments.write(source, rewritten);
                    if (replacement.getBytes(StandardCharsets.UTF_8).length > 1048576
                            || !QuestRepository.parseDefinition(file, replacement).presentation().equals(rewritten))
                        return new Result(id, "invalid_source");
                    previous.put(file, source);
                    replacements.put(file, replacement);
                }
                boolean writeShared = !updatedShared.equals(shared);
                String sharedJson = writeShared ? GSON.toJson(updatedShared) : "";
                if (sharedJson.getBytes(StandardCharsets.UTF_8).length > 1048576)
                    return new Result(id, "limit");
                boolean existed = writeShared && Files.exists(FILE, LinkOption.NOFOLLOW_LINKS);
                String original = existed ? Files.readString(FILE) : "";
                if (writeShared) {
                    List<QuestVariable> onDisk = existed ? GSON.fromJson(original,
                            new TypeToken<List<QuestVariable>>() {}.getType()) : List.of();
                    if (!shared.equals(onDisk)) return new Result(id, "conflict");
                }
                try {
                    for (var entry : replacements.entrySet()) AtomicFiles.write(entry.getKey(), entry.getValue(), true);
                    if (writeShared) AtomicFiles.write(FILE, sharedJson, true);
                    QuestRepository.reload();
                    if (!QuestRepository.errors().isEmpty() || !saved.equals(find(id)))
                        throw new IllegalStateException("Invalid variable or task source");
                    if (old != null && !currentOnly && !old.id().equals(id))
                        QuestControlData.get(author).renameVariable(old.id(), id);
                    return new Result(id, null);
                } catch (Exception failed) {
                    for (var entry : previous.entrySet()) AtomicFiles.write(entry.getKey(), entry.getValue(), true);
                    if (writeShared) {
                        if (existed) AtomicFiles.write(FILE, original, true);
                        else Files.deleteIfExists(FILE);
                    }
                    QuestRepository.reload();
                    throw failed;
                }
            } catch (Exception invalid) {
                XeroQuestJournal.LOGGER.warn("Unable to save variable {}", id, invalid);
                return new Result(id, "save");
            }
        }
    }
}
