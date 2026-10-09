package xtdpotato.xero_quest_journal.core.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import java.nio.file.Files;
import java.util.*;

/** Author-owned settings next to the immutable template, never supplied by an entering client. */
public record QuestSceneSettings(String inventory, String interaction, String death,
                                 Spawn spawn, List<Slot> equipment, List<Region> regions,
                                 List<Trigger> triggers, int exitDelay) {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record Spawn(double x, double y, double z, float yaw, float pitch) {
        public Spawn {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                    || x < -1 || x > 33 || y < 0 || y > 32 || z < -1 || z > 33 || Math.abs(pitch) > 90)
                throw invalid();
        }
    }
    public record Slot(int slot, String item, int count) {
        public Slot {
            if (slot < 0 || slot > 40 || item == null || item.isBlank() || item.length() > 8192
                    || count < 1 || count > 99) throw invalid();
        }
        ItemStack stack(ServerPlayer player) {
            ItemStack result;
            if (QuestItemStackValue.encoded(item))
                result = QuestItemStackValue.decode(item, player.registryAccess()).orElse(ItemStack.EMPTY);
            else {
                var id = ResourceLocation.tryParse(item);
                result = id != null && BuiltInRegistries.ITEM.containsKey(id)
                        ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
            }
            if (result.isEmpty() || count > result.getMaxStackSize()) throw invalid();
            return result.copyWithCount(count);
        }
    }
    public record Region(int x1, int y1, int z1, int x2, int y2, int z2,
                         boolean use, boolean build, boolean attack) {
        public Region {
            if (x1 < -2 || y1 < 0 || z1 < -2 || x2 > 34 || y2 > 33 || z2 > 34
                    || x1 > x2 || y1 > y2 || z1 > z2) throw invalid();
        }
        boolean contains(BlockPos p) {
            return p.getX() >= x1 && p.getX() <= x2 && p.getY() >= y1 && p.getY() <= y2
                    && p.getZ() >= z1 && p.getZ() <= z2;
        }
    }
    public record Trigger(String id, String type, int x, int y, int z, int radius,
                          String value, int count, String mode, int duration, int cooldown,
                          boolean once, String action, String target) {
        public Trigger {
            if (id == null || !id.matches("[A-Za-z0-9_.-]{1,64}") || !List.of(
                    "signal", "block", "container", "region", "entity", "timer", "interaction", "js").contains(type)
                    || x < -2 || x > 34 || y < 0 || y > 33 || z < -2 || z > 34
                    || radius < 0 || radius > 32 || count < 0 || count > 1_000_000
                    || !List.of("current", "rising", "falling").contains(mode)
                    || duration < 0 || duration > 72000 || cooldown < 1 || cooldown > 72000
                    || !List.of("complete", "unlock", "lock", "message", "checkpoint").contains(action))
                throw invalid();
            value = value == null ? "" : value;
            target = target == null ? "" : target;
            if (value.length() > 8192 || target.length() > 1024) throw invalid();
        }
    }
    public QuestSceneSettings {
        inventory = inventory == null ? "empty" : inventory;
        interaction = interaction == null ? "interact" : interaction;
        death = death == null ? "return" : death;
        spawn = spawn == null ? new Spawn(-.5, 1, .5, 0, 0) : spawn;
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        regions = regions == null ? List.of() : List.copyOf(regions);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        if (exitDelay < 0 || exitDelay > 3600) throw invalid();
        if (!List.of("empty", "player_copy", "task").contains(inventory)
                || !List.of("view", "interact", "build", "regions").contains(interaction)
                || !List.of("return", "spawn", "checkpoint").contains(death)
                || equipment.size() > 41 || regions.size() > 64 || triggers.size() > 128
                || equipment.stream().map(Slot::slot).distinct().count() != equipment.size()
                || triggers.stream().map(Trigger::id).distinct().count() != triggers.size()) throw invalid();
    }
    /** Compatibility constructor for existing scene defaults and integrations. */
    public QuestSceneSettings(String inventory, String interaction, String death,
                              Spawn spawn, List<Slot> equipment, List<Region> regions,
                              List<Trigger> triggers) {
        this(inventory, interaction, death, spawn, equipment, regions, triggers, 0);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("structure.settings_invalid"); }
    public static QuestSceneSettings defaults() { return new QuestSceneSettings(null, null, null, null, null, null, null, 0); }
    public static QuestSceneSettings parse(String source) {
        try {
            // Settings are persisted as one NBT string in the return checkpoint.
            if (source == null || source.length() > 16000) throw invalid();
            var root = com.google.gson.JsonParser.parseString(source).getAsJsonObject();
            // Fail closed for misspelled/unimplemented engine settings rather than pretending to support them.
            if (!Set.of("inventory", "interaction", "death", "spawn", "equipment", "regions", "triggers", "exitDelay")
                    .containsAll(root.keySet())) throw invalid();
            var settings = JSON.fromJson(root, QuestSceneSettings.class);
            if (settings == null) throw invalid();
            // Reconstruct explicitly so validation also runs with alternate Gson/record adapters.
            return new QuestSceneSettings(settings.inventory, settings.interaction, settings.death,
                    settings.spawn == null ? null : new Spawn(settings.spawn.x, settings.spawn.y,
                            settings.spawn.z, settings.spawn.yaw, settings.spawn.pitch),
                    settings.equipment == null ? null : settings.equipment.stream()
                            .map(s -> new Slot(s.slot, s.item, s.count)).toList(),
                    settings.regions == null ? null : settings.regions.stream()
                            .map(r -> new Region(r.x1, r.y1, r.z1, r.x2, r.y2, r.z2, r.use, r.build, r.attack)).toList(),
                    settings.triggers == null ? null : settings.triggers.stream().map(t -> new Trigger(t.id, t.type,
                            t.x, t.y, t.z, t.radius, t.value, t.count, t.mode, t.duration, t.cooldown, t.once,
                            t.action, t.target)).toList(), settings.exitDelay);
        } catch (Exception e) { throw invalid(); }
    }
    public static String source(String reference) throws Exception {
        var path = QuestStructures.path(reference + ".scene.json");
        if (!Files.exists(path)) return JSON.toJson(defaults());
        if (Files.size(path) > 48000) throw invalid();
        return Files.readString(path);
    }
    public static QuestSceneSettings read(String reference) throws Exception { return parse(source(reference)); }
    public static void save(ServerPlayer author, String reference, String expectedHash, String source) throws Exception {
        QuestStructureLibrary.authorize(author);
        QuestStructures.read(reference);
        if (!QuestRepository.fingerprint(source(reference)).equals(expectedHash))
            throw new IllegalArgumentException("structure.settings_changed");
        var encoded = JSON.toJson(parse(source));
        if (encoded.length() > 16000) throw invalid();
        AtomicFiles.write(QuestStructures.path(reference + ".scene.json"), encoded, true);
    }
    public boolean allows(BlockPos local, String operation) {
        if (interaction.equals("view")) return false;
        if (interaction.equals("regions")) return regions.stream().anyMatch(r -> r.contains(local) && switch (operation) {
            case "build" -> r.build;
            case "attack" -> r.attack;
            default -> r.use;
        });
        return !operation.equals("build") || interaction.equals("build");
    }
}
