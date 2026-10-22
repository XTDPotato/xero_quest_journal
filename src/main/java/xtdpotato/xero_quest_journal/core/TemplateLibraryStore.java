package xtdpotato.xero_quest_journal.core;

import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoringHistory;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestJournalOrder;
import java.nio.file.Files;
import java.util.*;

/** Template folders and order are independent of task groups and progression. */
public final class TemplateLibraryStore {
    public record Library(List<String> groups, List<String> order, Map<String, String> membership) {
        public Library {
            groups = groups == null ? List.of() : List.copyOf(groups);
            order = order == null ? List.of() : List.copyOf(order);
            membership = membership == null ? Map.of() : Map.copyOf(membership);
            if (groups.size() > 256 || order.size() > 256 || membership.size() > 256
                    || groups.stream().anyMatch(s -> s.isBlank() || s.length() > 128)
                    || new HashSet<>(groups).size() != groups.size())
                throw new IllegalArgumentException("Invalid template library");
        }
        public static Library empty() { return new Library(List.of(), List.of(), Map.of()); }
        public String group(xtdpotato.xero_quest_journal.core.model.QuestTemplate t) {
            return membership.getOrDefault(t.id(), t.group());
        }
    }
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Library snapshot = Library.empty();
    public static Library get() { return snapshot; }
    public static java.nio.file.Path file() { return QuestTemplateStore.root().resolveSibling("template_library.json").toAbsolutePath().normalize(); }
    public static void refresh() {
        synchronized (QuestRepository.class) {
            try {
                var path = file();
                if (Files.isSymbolicLink(path) || Files.exists(path) && Files.size(path) > 262144) return;
                Library next = Files.exists(path) ? GSON.fromJson(Files.readString(path), Library.class) : Library.empty();
                if (next != null && !next.equals(snapshot)) { snapshot = next; QuestRepository.templatesChanged(); }
            } catch (Exception failure) {
                xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn("Unable to read template library", failure);
            }
        }
    }
    public static String change(ServerPlayer player, String kind, String id, String target, boolean after) {
        if (player == null || !player.hasPermissions(2) || !player.server.isSameThread()) return "permission";
        synchronized (QuestRepository.class) {
            try {
                refresh();
                var groups = new ArrayList<>(snapshot.groups());
                for (String group : List.of("main", "legendary", "commission", "world"))
                    if (!groups.contains(group)) groups.add(group);
                var templates = QuestTemplateStore.all().stream().filter(t -> !t.archived()).toList();
                for (var t : templates) if (!groups.contains(snapshot.group(t))) groups.add(snapshot.group(t));
                var membership = new LinkedHashMap<>(snapshot.membership());
                var order = QuestJournalOrder.apply(templates.stream().map(t -> t.id()).toList(), snapshot.order());
                if (kind.equals("add_group")) {
                    id = id.strip();
                    if (id.isBlank() || id.length() > 128 || groups.contains(id)) return "duplicate";
                    groups.add(id);
                } else if (kind.equals("group")) {
                    groups = new ArrayList<>(QuestJournalOrder.move(groups, id, target, after ? 1 : -1));
                } else {
                    if (!order.contains(id)) return "invalid";
                    if (kind.equals("into_group")) {
                        if (!groups.contains(target)) return "invalid";
                        membership.put(id, target);
                    } else if (kind.equals("template")) {
                        var destination = templates.stream().filter(t -> t.id().equals(target)).findFirst().orElseThrow();
                        membership.put(id, snapshot.group(destination));
                        order = QuestJournalOrder.move(order, id, target, after ? 1 : -1);
                    } else return "invalid";
                }
                var path = file();
                if (Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent())) return "save";
                var next = new Library(groups, order, membership);
                var before = QuestAuthoringHistory.capture(List.of(path));
                AtomicFiles.write(path, GSON.toJson(next), Files.exists(path));
                snapshot = next;
                QuestRepository.templatesChanged();
                QuestAuthoringHistory.remember(player, "", "template", before);
                return "";
            } catch (Exception failure) {
                xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn("Unable to update template library", failure);
                return "save";
            }
        }
    }
}
