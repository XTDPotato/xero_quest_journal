package xtdpotato.xero_quest_journal.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuestTypeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = PlatformServices.configDir().resolve("xero_quest").resolve("task_types");
    private static volatile List<QuestType> snapshot = List.of();

    private QuestTypeStore() {
    }

    public static void ensureDefaults() throws IOException {
        Files.createDirectories(ROOT);
        Path initialized = ROOT.resolve(".initialized");
        boolean empty;
        try (var files = Files.list(ROOT)) {
            empty = files.noneMatch(path -> path.getFileName().toString().endsWith(".json"));
        }
        if (empty && !Files.exists(initialized)) {
            writeIfMissing(new QuestType("main", "主线任务", "minecraft:nether_star"));
            writeIfMissing(new QuestType("legendary", "传说任务", "minecraft:totem_of_undying"));
            writeIfMissing(new QuestType("commission", "委托任务", "minecraft:paper"));
            writeIfMissing(new QuestType("world", "世界任务", "minecraft:filled_map"));
        }
        if (!Files.exists(initialized)) AtomicFiles.write(initialized, "initialized\n", false);
        refresh();
    }

    private static void writeIfMissing(QuestType type) throws IOException {
        Path file = ROOT.resolve(type.id() + ".json");
        if (!Files.exists(file)) {
            AtomicFiles.write(file, GSON.toJson(type), false);
        }
    }

    public static List<QuestType> all() {
        return snapshot;
    }

    public static void refresh() {
        synchronized (QuestRepository.class) {
            List<QuestType> next = readAll();
            if (next != null && !next.equals(snapshot)) {
                snapshot = next;
                QuestRepository.typesChanged();
            }
        }
    }

    private static List<QuestType> readAll() {
        try (var files = Files.list(ROOT)) {
            var paths = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
            if (paths.size() > 64) throw new IOException("Too many quest types");
            var values = new ArrayList<QuestType>();
            for (Path path : paths) {
                if (Files.size(path) > 16384) throw new IOException("Quest type exceeds 16 KiB");
                QuestType type = GSON.fromJson(Files.readString(path), QuestType.class);
                if (type == null || !path.getFileName().toString().equals(type.id() + ".json")) return null;
                values.add(type);
            }
            values.sort(Comparator.comparingInt(QuestTypeStore::order).thenComparing(QuestType::id));
            return List.copyOf(values);
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to read quest types", exception);
            return null;
        }
    }

    private static int order(QuestType type) {
        return switch (type.id()) {
            case "main" -> 0;
            case "legendary" -> 1;
            case "commission" -> 2;
            case "world" -> 3;
            default -> 100;
        };
    }

    public static String save(ServerPlayer author, QuestType type, boolean editing) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return "permission";
        synchronized (QuestRepository.class) {
            try {
                if (List.of("add", "previous", "next").contains(type.id())) return "invalid";
                Files.createDirectories(ROOT);
                Path file = ROOT.resolve(type.id() + ".json");
                if (Files.isSymbolicLink(ROOT) || Files.isSymbolicLink(file)) return "save";
                if (!editing && Files.exists(file)) return "duplicate";
                if (!Files.exists(file) && snapshot.size() >= 64) return "invalid";
                AtomicFiles.write(file, GSON.toJson(type), Files.exists(file));
                refresh();
                return "";
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to save quest type {}", type.id(), exception);
                return "save";
            }
        }
    }

    public static String delete(ServerPlayer author, String id) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return "permission";
        synchronized (QuestRepository.class) {
            try {
                if (id == null || !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")) return "invalid";
                if (id.equals("active")) return "in_use";
                QuestRepository.reload();
                if (!QuestRepository.errors().isEmpty()) return "save";
                if (QuestRepository.all().stream().anyMatch(quest -> quest.type().equals(id))
                        || QuestRepository.groups().stream().anyMatch(group -> group.type().equals(id))
                        || QuestTemplateStore.all().stream().anyMatch(template -> template.type().equals(id))) {
                    return "in_use";
                }
                Path file = ROOT.resolve(id + ".json").toAbsolutePath().normalize();
                if (!file.startsWith(ROOT.toAbsolutePath().normalize())
                        || Files.isSymbolicLink(ROOT) || Files.isSymbolicLink(file)) return "save";
                if (!Files.deleteIfExists(file)) return "not_found";
                refresh();
                return "";
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to delete quest type {}", id, exception);
                return "save";
            }
        }
    }
}
