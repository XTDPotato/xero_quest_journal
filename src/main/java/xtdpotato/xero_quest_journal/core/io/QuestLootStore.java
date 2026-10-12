package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.model.LootDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import xtdpotato.xero_quest_journal.core.runtime.QuestLootConditions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Filesystem-backed reusable loot tables. All writes are permission-checked by callers. */
public final class QuestLootStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = PlatformServices.configDir().resolve("xero_quest/loot").toAbsolutePath().normalize();
    private static final Map<String, LootDefinition> TABLES = new LinkedHashMap<>();
    private static long revision;

    private QuestLootStore() {}

    public static synchronized void reload() {
        Map<String, LootDefinition> next = new LinkedHashMap<>();
        try {
            Files.createDirectories(ROOT);
            try (var paths = Files.list(ROOT)) {
                for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    try {
                        LootDefinition table = GSON.fromJson(JsonParser.parseString(Files.readString(path)), LootDefinition.class);
                        validate(table);
                        if (next.putIfAbsent(table.id(), table) != null) throw new IllegalArgumentException("Duplicate loot ID");
                    } catch (Exception invalid) {
                        XeroQuestJournal.LOGGER.warn("Invalid loot table {}", path, invalid);
                    }
                }
            }
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to load loot tables", failure);
        }
        TABLES.clear();
        TABLES.putAll(next);
        revision++;
    }

    public static synchronized List<LootDefinition> all() {
        return List.copyOf(TABLES.values());
    }

    public static synchronized LootDefinition find(String id) {
        return id == null ? null : TABLES.get(id);
    }

    public static synchronized long revision() {
        return revision;
    }

    public static synchronized void save(ServerPlayer author, LootDefinition table) {
        if (author == null || !author.hasPermissions(2)) throw new IllegalArgumentException("Permission required");
        validate(table);
        try {
            Files.createDirectories(ROOT);
            Path path = safe(table.id() + ".json");
            Files.writeString(path, GSON.toJson(table));
            TABLES.put(table.id(), table);
            revision++;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Unable to save loot table", failure);
        }
    }

    public static synchronized void delete(ServerPlayer author, String id) {
        if (author == null || !author.hasPermissions(2)) throw new IllegalArgumentException("Permission required");
        if (id == null || id.isBlank()) return;
        try {
            Files.deleteIfExists(safe(id + ".json"));
            TABLES.remove(id);
            revision++;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Unable to delete loot table", failure);
        }
    }

    public static void validate(LootDefinition table) {
        if (table == null || table.entries().isEmpty()) throw new IllegalArgumentException("Loot table is empty");
        Set<String> ids = new HashSet<>();
        boolean usable = false;
        for (LootDefinition.Entry entry : table.entries()) {
            if (!ids.add(entry.id()) || !List.of("item", "command", "experience", "levels").contains(entry.type())
                    || !QuestRewards.valid(entry.reward()) || !QuestLootConditions.valid(entry.condition())) {
                throw new IllegalArgumentException("Invalid loot entry");
            }
            usable |= entry.enabled() && (table.mode() == LootDefinition.Mode.CHOICE || entry.weight() > 0);
        }
        if (!usable) throw new IllegalArgumentException("Loot table has no usable entries");
        if (table.mode() == LootDefinition.Mode.CHOICE && table.entries().stream()
                .filter(entry -> entry.enabled()).count() < 2)
            throw new IllegalArgumentException("Choice loot needs at least two entries");
    }

    private static Path safe(String name) {
        if (!name.matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalArgumentException("Invalid loot ID");
        Path path = ROOT.resolve(name).normalize();
        if (!path.startsWith(ROOT)) throw new IllegalArgumentException("Invalid loot path");
        return path;
    }
}
