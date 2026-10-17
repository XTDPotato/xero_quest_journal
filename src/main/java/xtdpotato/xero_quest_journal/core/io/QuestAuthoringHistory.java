package xtdpotato.xero_quest_journal.core.io;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Per-author definition transactions. Player progress and delivered rewards are never rewound. */
public final class QuestAuthoringHistory {
    private static final int LIMIT = 64;
    private static final long BYTE_LIMIT = 32L * 1024 * 1024;
    private static final Map<MinecraftServer, Map<UUID, History>> SERVERS = new WeakHashMap<>();
    private static long revision;
    private record Change(String id, String kind, Map<Path, String> before, Map<Path, String> after, long bytes) {}
    private static final class History {
        final Deque<Change> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
        long bytes;
    }
    public record State(long revision, int undoCount, int redoCount, String undoKind, String redoKind) {}
    public record Result(String id, String error) {
        public boolean successful() { return error == null; }
    }
    private QuestAuthoringHistory() {}

    private static History history(ServerPlayer author) {
        return SERVERS.computeIfAbsent(author.getServer(), ignored -> new HashMap<>())
                .computeIfAbsent(author.getUUID(), ignored -> new History());
    }
    public static State state(ServerPlayer author) {
        synchronized (QuestRepository.class) {
            if (author == null || author.getServer() == null || !author.hasPermissions(2))
                return new State(revision, 0, 0, "", "");
            var history = history(author);
            return new State(revision, history.undo.size(), history.redo.size(),
                    history.undo.isEmpty() ? "" : history.undo.peek().kind(),
                    history.redo.isEmpty() ? "" : history.redo.peek().kind());
        }
    }
    /** Capture only affected files, including a null value for a file that did not exist. */
    public static Map<Path, String> capture(Collection<Path> files) throws IOException {
        var values = new LinkedHashMap<Path, String>();
        for (Path file : files) {
            Path path = safe(file);
            values.put(path, Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? Files.readString(path) : null);
        }
        return values;
    }
    private static Path safe(Path file) throws IOException {
        Path path = file.toAbsolutePath().normalize(), root = QuestRepository.root();
        if (!(path.startsWith(root) && !path.equals(root))
                && !(path.startsWith(root.getParent().resolve("templates"))
                && !path.equals(root.getParent().resolve("templates")))
                && !path.equals(root.getParent().resolve("unlock_layout.json"))
                && !path.equals(QuestJournalOrderStore.file()))
            throw new IOException("History file is outside definition storage");
        for (Path cursor = path; cursor != null; cursor = cursor.getParent())
            if (Files.isSymbolicLink(cursor)) throw new IOException("Symbolic link in history path");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("History target is not a regular file");
        return path;
    }
    /** Record after the caller has committed and validated its entire operation. */
    public static void remember(ServerPlayer author, String id, String kind, Map<Path, String> before) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()) return;
        synchronized (QuestRepository.class) {
            try {
                var after = capture(before.keySet());
                var previous = new LinkedHashMap<Path, String>();
                var next = new LinkedHashMap<Path, String>();
                long bytes = 0;
                for (var entry : before.entrySet()) {
                    Path file = safe(entry.getKey());
                    if (Objects.equals(entry.getValue(), after.get(file))) continue;
                    previous.put(file, entry.getValue()); next.put(file, after.get(file));
                    bytes += (entry.getValue() == null ? 0 : entry.getValue().length() * 2L)
                            + (after.get(file) == null ? 0 : after.get(file).length() * 2L);
                }
                if (previous.isEmpty()) return;
                var history = history(author);
                for (var change : history.redo) history.bytes -= change.bytes();
                history.redo.clear();
                history.undo.push(new Change(id, kind, Collections.unmodifiableMap(previous),
                        Collections.unmodifiableMap(next), bytes));
                history.bytes += bytes;
                while (history.undo.size() > LIMIT || history.bytes > BYTE_LIMIT && history.undo.size() > 1)
                    history.bytes -= history.undo.removeLast().bytes();
                revision++;
            } catch (IOException failure) {
                // Never leave a stale stack able to overwrite an operation that could not be recorded.
                var history = history(author);
                history.undo.clear(); history.redo.clear(); history.bytes = 0; revision++;
                XeroQuestJournal.LOGGER.error("Unable to record authoring history", failure);
            }
        }
    }
    public static void write(ServerPlayer author, String id, String kind, Path file, String source) throws IOException {
        synchronized (QuestRepository.class) {
            var before = capture(List.of(file));
            try {
                AtomicFiles.write(safe(file), source, true);
                QuestRepository.reload();
                if (!QuestRepository.errors().isEmpty()) throw new IOException("Invalid edited definitions");
            } catch (Exception failure) {
                restore(before); QuestRepository.reload();
                throw new IOException("Definition edit rejected", failure);
            }
            remember(author, id, kind, before);
        }
    }
    public static Result undo(ServerPlayer author) { return restore(author, false); }
    public static Result redo(ServerPlayer author) { return restore(author, true); }
    private static Result restore(ServerPlayer author, boolean redo) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime.canEdit(author))
            return new Result("", "permission");
        synchronized (QuestRepository.class) {
            var history = history(author);
            var from = redo ? history.redo : history.undo;
            var to = redo ? history.undo : history.redo;
            if (from.isEmpty()) return new Result("", "empty");
            var change = from.peek();
            var expected = redo ? change.before() : change.after();
            var replacement = redo ? change.after() : change.before();
            try {
                var current = capture(expected.keySet());
                if (!current.equals(expected)) return new Result(change.id(), "conflict");
                try {
                    restore(replacement);
                    QuestRepository.reload();
                    if (!QuestRepository.errors().isEmpty()) throw new IOException("Restored definitions are invalid");
                } catch (Exception failure) {
                    restore(current); QuestRepository.reload();
                    throw failure;
                }
                from.pop(); to.push(change); revision++;
                return new Result(change.id(), null);
            } catch (Exception failure) {
                XeroQuestJournal.LOGGER.warn("Unable to restore authoring transaction", failure);
                return new Result(change.id(), "save");
            }
        }
    }
    private static void restore(Map<Path, String> values) throws IOException {
        // Validate every path before changing any file.
        for (Path file : values.keySet()) safe(file);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
            else AtomicFiles.write(entry.getKey(), entry.getValue(),
                    Files.exists(entry.getKey(), LinkOption.NOFOLLOW_LINKS));
        }
    }
}
