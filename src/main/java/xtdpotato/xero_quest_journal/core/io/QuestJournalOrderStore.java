package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestTypeStore;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestJournalOrder;

import java.nio.file.Files;
import java.nio.file.Path;

public final class QuestJournalOrderStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private QuestJournalOrderStore() {}

    public static Path file() { return QuestRepository.root().getParent().resolve("journal_order.json"); }

    static QuestJournalOrder read() throws java.io.IOException {
        Path path = file();
        if (!Files.exists(path)) return QuestJournalOrder.empty();
        if (Files.isSymbolicLink(path) || Files.size(path) > 16 * 1024 * 1024)
            throw new java.io.IOException("Invalid journal order file");
        var order = GSON.fromJson(Files.readString(path), QuestJournalOrder.class);
        if (order == null) throw new java.io.IOException("Missing journal order");
        return order;
    }

    public static String move(ServerPlayer player, long expectedRevision, String kind,
                              String id, String neighbor, int direction) {
        if (!MailboxRuntime.canEdit(player) || !player.server.isSameThread()) return "permission";
        synchronized (QuestRepository.class) {
            if (expectedRevision != QuestRepository.revision()) return "definition_changed";
            var old = QuestRepository.journalOrder();
            QuestJournalOrder next;
            try {
                if (kind.equals("task")) {
                    if (QuestRepository.find(id) == null || QuestRepository.find(neighbor) == null) return "missing";
                    if (!QuestRepository.groupOf(id).equals(QuestRepository.groupOf(neighbor))) return "invalid_action";
                    var current = QuestJournalOrder.apply(QuestRepository.all().stream()
                            .map(QuestDefinition::id).toList(), old.tasks());
                    next = new QuestJournalOrder(QuestJournalOrder.move(current, id, neighbor, direction),
                            old.tabs(), old.groups());
                } else if (kind.equals("tab")) {
                    next = new QuestJournalOrder(old.tasks(), QuestJournalOrder.move(
                            old.tabOrder(QuestTypeStore.all()), id, neighbor, direction), old.groups());
                } else return "invalid_action";
                var before = QuestAuthoringHistory.capture(java.util.List.of(file()));
                write(next);
                QuestAuthoringHistory.remember(player, id, "order", before);
                return "journal_order_saved";
            } catch (IllegalArgumentException invalid) {
                return "invalid_action";
            } catch (Exception failure) {
                XeroQuestJournal.LOGGER.warn("Unable to save journal display order", failure);
                return "journal_order_failed";
            }
        }
    }

    public static String moveTasks(ServerPlayer player, long expectedRevision,
                                   java.util.List<String> taskIds, String neighbor, boolean after) {
        if (!MailboxRuntime.canEdit(player) || !player.server.isSameThread()) return "permission";
        if (taskIds == null || taskIds.isEmpty() || taskIds.size() > 512
                || new java.util.LinkedHashSet<>(taskIds).size() != taskIds.size()) return "invalid_action";
        synchronized (QuestRepository.class) {
            if (expectedRevision != QuestRepository.revision()) return "definition_changed";
            if (neighbor == null || taskIds.contains(neighbor)) return "invalid_action";
            var group = QuestRepository.groupOf(neighbor);
            if (QuestRepository.find(neighbor) == null
                    || taskIds.stream().anyMatch(id -> QuestRepository.find(id) == null
                    || !group.equals(QuestRepository.groupOf(id)))) return "invalid_action";
            try {
                var old = QuestRepository.journalOrder();
                var current = QuestJournalOrder.apply(QuestRepository.all().stream()
                        .map(QuestDefinition::id).toList(), old.tasks());
                var moved = QuestJournalOrder.moveBlock(current, taskIds, neighbor, after);
                var before = QuestAuthoringHistory.capture(java.util.List.of(file()));
                write(new QuestJournalOrder(moved, old.tabs(), old.groups()));
                QuestAuthoringHistory.remember(player, taskIds.getFirst(), "order", before);
                return "journal_order_saved";
            } catch (IllegalArgumentException invalid) {
                return "invalid_action";
            } catch (Exception failure) {
                XeroQuestJournal.LOGGER.warn("Unable to save journal task block order", failure);
                return "journal_order_failed";
            }
        }
    }

    public record Placement(String kind, String group) {
        public Placement {
            if (!java.util.List.of("task_top", "task_bottom", "group_before", "group_after").contains(kind)
                    || group == null || group.isBlank() || group.length() > 128)
                throw new IllegalArgumentException("Invalid creation placement");
        }

        public boolean task() { return kind.startsWith("task_"); }
    }

    public static boolean validPlacement(QuestAuthoring.Request request, Placement placement) {
        if (placement == null) return true;
        if (QuestRepository.groups().stream().noneMatch(g -> g.id().equals(placement.group()))) return false;
        return placement.task()
                ? request.kind() == QuestAuthoring.Kind.TASK && request.group().equals(placement.group())
                : request.kind() == QuestAuthoring.Kind.GROUP && request.group().isBlank();
    }

    /** Called under the repository lock after creating and reloading the new definition. */
    static void placeNew(String id, Placement placement) throws java.io.IOException {
        var old = QuestRepository.journalOrder();
        if (placement.task()) {
            if (QuestRepository.find(id) == null
                    || !QuestRepository.groupOf(id).equals(placement.group()))
                throw new IllegalArgumentException("Creation membership changed");
            var order = new java.util.ArrayList<>(QuestJournalOrder.apply(
                    QuestRepository.all().stream().map(QuestDefinition::id).toList(), old.tasks()));
            order.remove(id);
            var peers = order.stream().filter(value -> QuestRepository.groupOf(value).equals(placement.group())).toList();
            if (peers.isEmpty()) order.add(id);
            else {
                boolean top = placement.kind().equals("task_top");
                String neighbor = top ? peers.getFirst() : peers.getLast();
                order.add(order.indexOf(neighbor) + (top ? 0 : 1), id);
            }
            write(new QuestJournalOrder(order, old.tabs(), old.groups()));
        } else {
            var order = QuestJournalOrder.apply(QuestRepository.groups().stream()
                    .map(QuestRepository.Group::id).toList(), old.groups());
            write(new QuestJournalOrder(old.tasks(), old.tabs(), QuestJournalOrder.move(order, id,
                    placement.group(), placement.kind().equals("group_before") ? -1 : 1)));
        }
    }

    private static void write(QuestJournalOrder order) throws java.io.IOException {
        Path path = file();
        if (Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent()))
            throw new java.io.IOException("Unsafe journal order file");
        AtomicFiles.write(path, GSON.toJson(order), Files.exists(path));
        QuestRepository.journalOrderChanged(order);
    }
}
