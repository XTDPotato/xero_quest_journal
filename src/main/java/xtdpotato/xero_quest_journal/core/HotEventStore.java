package xtdpotato.xero_quest_journal.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

public final class HotEventStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("xero_quest/hot_events.json");
    private static HotEventCanvas current = HotEventCanvas.empty();
    private static boolean loaded;
    private static long reconciledRevision = -1;

    private HotEventStore() {}

    public static HotEventCanvas get() {
        synchronized (QuestRepository.class) {
            return getWithRepositoryLock();
        }
    }

    private static synchronized HotEventCanvas getWithRepositoryLock() {
        if (!loaded) reload();
        if (reconciledRevision != QuestRepository.revision()) {
            reconcileCards();
            reconciledRevision = QuestRepository.revision();
        }
        return current;
    }

    private static void reconcileCards() {
        List<String> ids = new ArrayList<>();
        Set<String> hotGroups = new HashSet<>();
        Map<String, String> representatives = new HashMap<>();
        QuestRepository.groups().stream().filter(group -> group.hotEvent().enabled())
                .forEach(group -> { ids.add("group:" + group.id()); hotGroups.add("group:" + group.id()); });
        QuestRepository.all().stream().filter(quest -> quest.hotEvent().enabled())
                .forEach(quest -> {
                    String group = "group:" + QuestRepository.groupOf(quest.id());
                    if (hotGroups.contains(group)) representatives.put("quest:" + quest.id(), group);
                    else ids.add("quest:" + quest.id());
                });
        ids.sort(Comparator.naturalOrder());
        Set<String> visibleIds = new HashSet<>(ids);
        Set<String> known = current.knownCardIds() == null ? Set.of() : current.knownCardIds();
        Set<String> valid = new HashSet<>();
        QuestRepository.groups().forEach(group -> valid.add("group:" + group.id()));
        QuestRepository.all().forEach(quest -> valid.add("quest:" + quest.id()));
        var edges = new java.util.LinkedHashSet<HotEventCanvas.Connection>();
        for (var edge : current.connections()) {
            String from = representatives.getOrDefault(edge.from(), edge.from());
            String to = representatives.getOrDefault(edge.to(), edge.to());
            if (visibleIds.contains(from) && visibleIds.contains(to) && !from.equals(to)) {
                var visible = new HotEventCanvas.Connection(from, to);
                edges.add(visible);
            }
        }
        Map<String, HotEventCanvas.Position> positions = new LinkedHashMap<>(current.positions());
        positions.keySet().retainAll(valid);
        String start = valid.contains(current.startId()) ? current.startId() : "";
        if (known.equals(visibleIds) && List.copyOf(edges).equals(current.connections())
                && positions.equals(current.positions()) && start.equals(current.startId())) return;
        // An old customized canvas is already authored; do not invent links among its old cards.
        if (!known.isEmpty() || !current.customized()) {
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                if (!known.contains(id)) {
                    String previous = i > 0 ? ids.get(i - 1) : "";
                    String next = i + 1 < ids.size() ? ids.get(i + 1) : "";
                    if (!previous.isBlank() || !next.isBlank()) {
                        if (!current.customized() && !previous.isBlank() && !next.isBlank()) {
                            edges.remove(new HotEventCanvas.Connection(previous, next));
                        }
                        var edge = previous.isBlank() ? new HotEventCanvas.Connection(id, next)
                                : new HotEventCanvas.Connection(previous, id);
                        if (edges.size() < 2048) edges.add(edge);
                        if (!current.customized() && !previous.isBlank() && !next.isBlank()) {
                            var following = new HotEventCanvas.Connection(id, next);
                            if (edges.size() < 2048) edges.add(following);
                        }
                    }
                }
            }
        }
        current = new HotEventCanvas(current.backgroundImage(), start, List.copyOf(edges),
                current.customized(), positions, current.backgroundType(), current.backgroundScale(),
                current.backgroundOffsetX(), current.backgroundOffsetY(), new HashSet<>(ids));
        try {
            AtomicFiles.write(FILE, GSON.toJson(current), true);
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to persist automatic hot-event connections", failure);
        }
    }

    public static synchronized void reload() {
        try {
            current = Files.exists(FILE) ? GSON.fromJson(Files.readString(FILE), HotEventCanvas.class)
                    : HotEventCanvas.empty();
            if (current == null) current = HotEventCanvas.empty();
        } catch (Exception invalid) {
            XeroQuestJournal.LOGGER.warn("Unable to read hot-event canvas settings", invalid);
            current = HotEventCanvas.empty();
        }
        loaded = true;
        reconciledRevision = -1;
    }

    public static boolean update(ServerPlayer author, HotEventCanvas next) {
        synchronized (QuestRepository.class) {
            return updateWithRepositoryLock(author, next);
        }
    }

    private static synchronized boolean updateWithRepositoryLock(ServerPlayer author, HotEventCanvas next) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2) || next == null) return false;
        if (!loaded) reload();
        reconcileCards();
        if (!validReferences(next)) return false;
        next = new HotEventCanvas(next.backgroundImage(), next.startId(), next.connections(),
                next.customized(), next.positions(), next.backgroundType(), next.backgroundScale(),
                next.backgroundOffsetX(), next.backgroundOffsetY(),
                current.knownCardIds() == null ? Set.of() : current.knownCardIds());
        try {
            AtomicFiles.write(FILE, GSON.toJson(next), true);
            current = next;
            loaded = true;
            QuestRepository.hotEventsChanged();
            reconciledRevision = QuestRepository.revision();
            return true;
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to save hot-event canvas settings", failure);
            return false;
        }
    }

    private static boolean validReferences(HotEventCanvas canvas) {
        Set<String> ids = new HashSet<>();
        QuestRepository.groups().forEach(group -> ids.add("group:" + group.id()));
        QuestRepository.all().forEach(quest -> ids.add("quest:" + quest.id()));
        if (!canvas.startId().isBlank() && !ids.contains(canvas.startId())) return false;
        if (canvas.positions().keySet().stream().anyMatch(id -> !ids.contains(id))) return false;
        Set<String> unique = new HashSet<>();
        for (var edge : canvas.connections()) {
            if (!ids.contains(edge.from()) || !ids.contains(edge.to())
                    || !unique.add(edge.from() + "\u0000" + edge.to())) return false;
        }
        return true;
    }
}
