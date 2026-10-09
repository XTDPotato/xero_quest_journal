package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestPresentation;
import java.nio.file.*;
import java.util.*;

/** Bounded Java NBT scenes, isolated by allocated cells in a dedicated dimension. */
public final class QuestStructures {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "structures"));
    private static final String KEY = "xero_scene_sessions";
    private static final Set<UUID> returning = new HashSet<>();
    private static final Map<ServerPlayer, Live> live = new WeakHashMap<>();
    private static final Map<Path, FileDigest> digests = new HashMap<>();
    private record FileDigest(java.nio.file.attribute.FileTime modified, long size, String hash) {}
    private static final Map<String, QuestSceneSettings> versionSettings = new HashMap<>();
    private static final class Live {
        final QuestSceneSettings settings;
        long checkedTick = Long.MIN_VALUE;
        String fingerprint = "";
        Live(QuestSceneSettings settings) { this.settings = settings; }
    }
    private QuestStructures() {}
    public static Path path(String reference) {
        Path root = QuestConfig.root().resolve("structures").toAbsolutePath().normalize();
        Path file = root.resolve(reference).normalize();
        if (reference.isBlank() || !file.startsWith(root) || file.equals(root))
            throw new IllegalArgumentException("structure.invalid");
        for (Path p = file; p != null; p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("structure.invalid");
        return file;
    }
    public static CompoundTag read(String reference) throws Exception {
        Path file = path(reference);
        if (!reference.endsWith(".nbt")) throw new IllegalArgumentException("structure.invalid");
        if (!Files.isRegularFile(file) || Files.size(file) > 2_097_152) throw new IllegalArgumentException("structure.missing");
        return validate(NbtIo.readCompressed(file, NbtAccounter.create(8_388_608)));
    }
    public static CompoundTag validate(CompoundTag data) {
        ListTag size = data.getList("size", Tag.TAG_INT);
        if (size.size() != 3 || size.getInt(0) < 1 || size.getInt(1) < 1 || size.getInt(2) < 1
                || size.getInt(0) > 32 || size.getInt(1) > 32 || size.getInt(2) > 32
                || data.getList("blocks", Tag.TAG_COMPOUND).size() > 32768
                || data.getList("entities", Tag.TAG_COMPOUND).size() > 128) throw new IllegalArgumentException("structure.large");
        ListTag palette = data.getList("palette", Tag.TAG_COMPOUND);
        if (data.contains("palettes")) throw new IllegalArgumentException("structure.palette");
        if (palette.isEmpty() || palette.size() > 1024) throw new IllegalArgumentException("structure.palette");
        for (int i = 0; i < palette.size(); i++) {
            ResourceLocation block = ResourceLocation.tryParse(palette.getCompound(i).getString("Name"));
            if (block == null || !BuiltInRegistries.BLOCK.containsKey(block)) throw new IllegalArgumentException("structure.mod_missing");
        }
        int solid = 0;
        for (var entry : data.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) entry;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size() || pos.size() != 3) throw new IllegalArgumentException("structure.invalid");
            for (int axis = 0; axis < 3; axis++) if (pos.getInt(axis) < 0 || pos.getInt(axis) >= size.getInt(axis))
                throw new IllegalArgumentException("structure.invalid");
            if (!palette.getCompound(state).getString("Name").equals("minecraft:air") && ++solid > 4096)
                throw new IllegalArgumentException("structure.large");
        }
        int[] entities = {0};
        for (var entry : data.getList("entities", Tag.TAG_COMPOUND)) {
            var entity = (CompoundTag) entry;
            var pos = entity.getList("pos", Tag.TAG_DOUBLE);
            if (pos.size() != 3) throw new IllegalArgumentException("structure.invalid");
            for (int axis = 0; axis < 3; axis++)
                if (!Double.isFinite(pos.getDouble(axis)) || pos.getDouble(axis) < 0 || pos.getDouble(axis) > size.getInt(axis))
                    throw new IllegalArgumentException("structure.invalid");
            validateEntity(entity.getCompound("nbt"), entities, 0);
        }
        return data;
    }
    private static void validateEntity(CompoundTag entity, int[] count, int depth) {
        if (++count[0] > 128 || depth > 8) throw new IllegalArgumentException("structure.large");
        ResourceLocation id = ResourceLocation.tryParse(entity.getString("id"));
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) throw new IllegalArgumentException("structure.mod_missing");
        for (var passenger : entity.getList("Passengers", Tag.TAG_COMPOUND))
            validateEntity((CompoundTag) passenger, count, depth + 1);
    }
    public static CompoundTag preview(CompoundTag source) {
        CompoundTag result = new CompoundTag();
        result.put("size", source.get("size").copy());
        result.put("palette", source.get("palette").copy());
        ListTag blocks = new ListTag();
        for (var value : source.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) value;
            String id = source.getList("palette", Tag.TAG_COMPOUND).getCompound(block.getInt("state")).getString("Name");
            if (id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")) continue;
            var clean = new CompoundTag();
            clean.put("pos", block.get("pos").copy()); clean.putInt("state", block.getInt("state"));
            blocks.add(clean);
        }
        result.put("blocks", blocks);
        return result;
    }
    private static Sessions sessions(ServerPlayer player) {
        return player.server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Sessions::new,
                (tag, registries) -> { var data = new Sessions(); data.state = tag; return data; }), KEY);
    }
    public static boolean active(ServerPlayer player) { return sessions(player).state.getCompound("players").contains(player.getUUID().toString()); }
    public static boolean returning(ServerPlayer player) { return returning.contains(player.getUUID()); }
    static CompoundTag snapshot(ServerPlayer player) {
        return sessions(player).state.getCompound("players").getCompound(player.getUUID().toString());
    }
    public static BlockPos origin(ServerPlayer player) {
        var data = snapshot(player);
        return new BlockPos(data.getInt("originX"), 80, data.getInt("originZ"));
    }
    public static boolean editing(ServerPlayer player) { return active(player) && snapshot(player).getBoolean("editing"); }
    public static String reference(ServerPlayer player) { return snapshot(player).getString("reference"); }
    public static void editSettings(ServerPlayer player, String expected, String source) {
        QuestStructureLibrary.authorize(player);
        if (!editing(player)) throw new IllegalArgumentException("structure.not_editing");
        if (!QuestRepository.fingerprint(snapshot(player).getString("settings")).equals(expected))
            throw new IllegalArgumentException("structure.settings_changed");
        var parsed = QuestSceneSettings.parse(source);
        for (var slot : parsed.equipment()) slot.stack(player);
        String encoded = QuestSceneSettings.JSON.toJson(parsed);
        QuestSceneSettings.parse(encoded);
        snapshot(player).putString("settings", encoded);
        live.put(player, new Live(parsed));
        sessions(player).setDirty();
    }
    public static String editSettingsSource(ServerPlayer player) {
        QuestStructureLibrary.authorize(player);
        if (!editing(player)) throw new IllegalArgumentException("structure.not_editing");
        return snapshot(player).getString("settings");
    }
    public static QuestSceneSettings settings(ServerPlayer player) {
        return live.computeIfAbsent(player, ignored -> new Live(snapshot(player).contains("settings")
                ? QuestSceneSettings.parse(snapshot(player).getString("settings")) : QuestSceneSettings.defaults())).settings;
    }
    public static String task(ServerPlayer player) { return snapshot(player).getString("quest"); }
    private static String fileHash(Path file, long limit) throws Exception {
        for (Path p = file; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("structure.invalid");
        var attrs = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        if (!attrs.isRegularFile() || attrs.size() > limit) throw new IllegalArgumentException("structure.large");
        var cached = digests.get(file);
        if (cached == null || !cached.modified.equals(attrs.lastModifiedTime()) || cached.size != attrs.size()) {
            cached = new FileDigest(attrs.lastModifiedTime(), attrs.size(),
                    HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            if (digests.size() > 512) digests.clear();
            digests.put(file, cached);
        }
        return cached.hash;
    }
    private static String fingerprint(String quest, String reference) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        digest.update(fileHash(path(reference), 2_097_152).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var settingsPath = path(reference + ".scene.json");
        String settingsHash = Files.exists(settingsPath) ? fileHash(settingsPath, 48000) : "default";
        digest.update(settingsHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var settings = versionSettings.get(settingsHash);
        if (settings == null) {
            settings = QuestSceneSettings.read(reference);
            if (versionSettings.size() >= 128) versionSettings.clear();
            versionSettings.put(settingsHash, settings);
        }
        if (!quest.isEmpty()) {
            if (QuestRepository.find(quest) == null || QuestRepository.fileOf(quest) == null) return "missing";
            digest.update(fileHash(QuestRepository.fileOf(quest), 1_048_576).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var definition = QuestRepository.find(quest);
            if (!definition.presentation().authored() && !definition.templateId().isBlank())
                digest.update(xtdpotato.xero_quest_journal.core.QuestTemplateStore.fingerprint(definition.templateId())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var scripts = new TreeSet<String>();
        if (!quest.isEmpty()) {
            var definition = QuestRepository.find(quest);
            for (var reward : definition.rewards()) if (reward.type().equals("script")) scripts.add(reward.item());
            for (var trigger : definition.triggers()) collectScripts(trigger, scripts);
            for (var control : xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(definition).controls())
                for (var node : control.graph().nodes()) if (node.type().equals("reward")) {
                    try {
                        for (var reward : QuestEventGraphs.rewards(node))
                            if (reward.type().equals("script")) scripts.add(reward.item());
                    } catch (Exception invalid) { return "invalid"; }
                }
        }
        for (var trigger : settings.triggers())
            if (trigger.type().equals("js") && trigger.value().endsWith(".js")) scripts.add(trigger.value());
        var scriptRoot = QuestConfig.root().resolve("scripts").toAbsolutePath().normalize();
        for (var script : scripts) {
            var file = scriptRoot.resolve(script).normalize();
            if (!file.startsWith(scriptRoot) || !script.endsWith(".js")) return "invalid";
            digest.update(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((Files.exists(file) ? fileHash(file, 65536) : "missing")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void collectScripts(QuestDefinition.Trigger trigger, Set<String> scripts) {
        if (trigger.type().equals("script")) scripts.add(trigger.item());
        for (var child : trigger.children()) collectScripts(child, scripts);
    }
    public static boolean current(ServerPlayer player) {
        if (!active(player)) return true;
        var data = snapshot(player);
        if (data.getBoolean("stale")) return false;
        if (data.getBoolean("editing") || data.getString("reference").isEmpty()) return !data.getBoolean("editing");
        settings(player);
        var state = live.get(player);
        long now = player.server.getTickCount();
        if (state.checkedTick != now) {
            state.checkedTick = now;
            try { state.fingerprint = fingerprint(data.getString("quest"), data.getString("reference")); }
            catch (Exception failure) { state.fingerprint = "missing"; }
        }
        if (data.getString("fingerprint").equals(state.fingerprint)) return true;
        data.putBoolean("stale", true);
        sessions(player).setDirty();
        player.server.overworld().getDataStorage().save();
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "screen.xero_quest_journal.structure.stale"));
        return false;
    }
    /** No unrelated quest may consume the temporary scene inventory or its events. */
    public static boolean allowsProgress(ServerPlayer player, QuestDefinition quest) {
        if (!active(player)) return true;
        if (editing(player) || !task(player).equals(quest.id()) || !current(player)) return false;
        return quest.completionPolicy().structureProgress();
    }
    public static boolean allows(ServerPlayer player, BlockPos pos, String operation) {
        if (!player.level().dimension().equals(DIMENSION)) return !active(player) || returning(player);
        if (!active(player)) return false;
        BlockPos local = pos.subtract(origin(player));
        if (local.getX() < -2 || local.getX() > 34 || local.getZ() < -2 || local.getZ() > 34
                || local.getY() < 0 || local.getY() > 33) return false;
        if (editing(player)) {
            var size = snapshot(player).getList("size", Tag.TAG_INT);
            return !operation.equals("build") || local.getX() >= 0 && local.getY() >= 0 && local.getZ() >= 0
                    && local.getX() < size.getInt(0) && local.getY() < size.getInt(1) && local.getZ() < size.getInt(2);
        }
        return settings(player).allows(local, operation);
    }
    public static boolean canReset(ServerPlayer player) {
        if (!active(player) || editing(player)) return false;
        long now = player.server.getTickCount();
        var state = snapshot(player);
        if (state.contains("lastReset") && now - state.getLong("lastReset") < 40) return false;
        state.putLong("lastReset", now);
        return true;
    }
    public static void enter(ServerPlayer player, QuestDefinition quest, QuestPresentation.Control control) throws Exception {
        checkCompletedEntry(player, quest);
        enter(player, control.value(), quest.id(), control.id(), false);
    }
    public static void enter(ServerPlayer player, QuestDefinition quest, QuestPresentation.Control control,
                             QuestSceneSettings.Spawn position) throws Exception {
        checkCompletedEntry(player, quest);
        enter(player, control.value(), quest.id(), control.id(), false, position);
    }
    private static void checkCompletedEntry(ServerPlayer player, QuestDefinition quest) {
        if (!QuestFocus.allows(player, quest))
            throw new IllegalStateException("Exit quest focus before entering another task");
        if (QuestRuntime.completed(player, quest) && !quest.completionPolicy().reenterStructure())
            throw new IllegalArgumentException("structure.completed_locked");
    }
    public static void enter(ServerPlayer player, String reference) throws Exception {
        enter(player, reference, "", "", false);
    }
    public static void edit(ServerPlayer player, String reference) throws Exception {
        QuestStructureLibrary.authorize(player);
        enter(player, reference, "", "", true);
    }
    private static void enter(ServerPlayer player, String reference, String quest, String control, boolean editing) throws Exception {
        enter(player, reference, quest, control, editing, null);
    }
    private static void enter(ServerPlayer player, String reference, String quest, String control, boolean editing,
                              QuestSceneSettings.Spawn position) throws Exception {
        if (active(player)) throw new IllegalArgumentException("structure.already_inside");
        var level = player.server.getLevel(DIMENSION);
        if (level == null) throw new IllegalArgumentException("structure.dimension_missing");
        CompoundTag nbt = read(reference);
        var settings = QuestSceneSettings.read(reference);
        if (position != null) settings = new QuestSceneSettings(settings.inventory(), settings.interaction(), settings.death(),
                position, settings.equipment(), settings.regions(), settings.triggers(), settings.exitDelay());
        QuestSceneSettings.parse(QuestSceneSettings.JSON.toJson(settings));
        // Decode components before any side effect or snapshot reservation.
        for (var slot : settings.equipment()) slot.stack(player);
        String version = fingerprint(quest, reference);
        var template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), nbt);
        var sessions = sessions(player);
        int index = sessions.state.getInt("next");
        if (index >= 65536) throw new IllegalArgumentException("structure.full");
        sessions.state.putInt("next", index + 1);
        BlockPos origin = new BlockPos((index % 256) * 256, 80, (index / 256) * 256);
        force(level, origin, true);
        try { place(level, origin, template); }
        catch (Exception failure) { cleanup(level, origin); force(level, origin, false); throw failure; }
        CompoundTag snapshot = new CompoundTag();
        player.closeContainer();
        snapshot.putString("dimension", player.level().dimension().location().toString());
        snapshot.putDouble("x", player.getX()); snapshot.putDouble("y", player.getY()); snapshot.putDouble("z", player.getZ());
        snapshot.putFloat("yaw", player.getYRot()); snapshot.putFloat("pitch", player.getXRot());
        snapshot.putInt("mode", player.gameMode.getGameModeForPlayer().getId());
        snapshot.put("inventory", player.getInventory().save(new ListTag()));
        snapshot.putInt("selected", player.getInventory().selected);
        snapshot.put("ender", player.getEnderChestInventory().createTag(player.registryAccess()));
        ListTag effects = new ListTag();
        player.getActiveEffects().forEach(effect -> effects.add(effect.save()));
        snapshot.put("effects", effects);
        CompoundTag food = new CompoundTag();
        player.getFoodData().addAdditionalSaveData(food);
        snapshot.put("foodData", food);
        snapshot.putInt("xpLevel", player.experienceLevel); snapshot.putInt("xpTotal", player.totalExperience);
        snapshot.putFloat("xpProgress", player.experienceProgress); snapshot.putFloat("health", player.getHealth());
        snapshot.putInt("food", player.getFoodData().getFoodLevel());
        snapshot.putFloat("saturation", player.getFoodData().getSaturationLevel());
        snapshot.putInt("originX", origin.getX()); snapshot.putInt("originZ", origin.getZ());
        snapshot.putString("reference", reference);
        snapshot.putString("quest", quest); snapshot.putString("control", control);
        snapshot.putString("fingerprint", version);
        snapshot.putBoolean("editing", editing);
        snapshot.putBoolean("completedBeforeEntry", !editing && !quest.isBlank()
                && QuestRepository.find(quest) != null && QuestRuntime.completed(player, QuestRepository.find(quest)));
        if (!editing && !quest.isBlank()) {
            var definition = QuestRepository.find(quest);
            snapshot.putBoolean("exportAcquired", definition != null && QuestRuntime.completed(player, definition)
                    && definition.completionPolicy().reenterStructure()
                    && definition.completionPolicy().exportAcquiredItems());
        }
        snapshot.putString("settings", QuestSceneSettings.JSON.toJson(settings));
        snapshot.put("size", nbt.get("size").copy());
        snapshot.putLong("started", level.getGameTime());
        snapshot.put("triggerState", new CompoundTag());
        snapshot.put("initialInventory", snapshot.getList("inventory", Tag.TAG_COMPOUND).copy());
        if (!sessions.state.contains("players")) sessions.state.put("players", new CompoundTag());
        sessions.state.getCompound("players").put(player.getUUID().toString(), snapshot);
        sessions.setDirty();
        // Persist the return checkpoint before replacing any player inventory slots.
        player.server.overworld().getDataStorage().save();
        live.put(player, new Live(settings));
        try {
            player.closeContainer();
            player.getInventory().clearContent();
            player.getEnderChestInventory().clearContent();
            player.removeAllEffects();
            player.setExperienceLevels(0); player.setExperiencePoints(0);
            equip(player, snapshot, settings);
            if (snapshot.getBoolean("exportAcquired")) {
                snapshot.put("sceneInitialInventory", player.getInventory().save(new ListTag()));
                sessions.setDirty();
                player.server.overworld().getDataStorage().save();
            }
            player.setGameMode(editing ? GameType.CREATIVE
                    : settings.interaction().equals("build") || settings.interaction().equals("regions")
                    ? GameType.SURVIVAL : GameType.ADVENTURE);
            respawn(player, false);
            player.containerMenu.broadcastChanges();
        } catch (Exception failure) { leave(player); throw failure; }
    }
    public static void leave(ServerPlayer player) {
        var sessions = sessions(player);
        var players = sessions.state.getCompound("players");
        String id = player.getUUID().toString();
        if (!players.contains(id)) return;
        CompoundTag snapshot = players.getCompound(id);
        returning.add(player.getUUID());
        try {
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(snapshot.getString("dimension")) == null
                    ? Level.OVERWORLD.location() : ResourceLocation.parse(snapshot.getString("dimension")));
            var level = player.server.getLevel(key);
            boolean fallback = level == null || key.equals(DIMENSION);
            if (fallback) level = player.server.overworld();
            var spawn = level.getSharedSpawnPos();
            Vec3 destination = safeReturn(level, fallback ? Vec3.atBottomCenterOf(spawn.above())
                    : new Vec3(snapshot.getDouble("x"), snapshot.getDouble("y"), snapshot.getDouble("z")));
            if (destination == null) {
                level = player.server.overworld();
                destination = safeReturn(level, Vec3.atBottomCenterOf(level.getSharedSpawnPos().above()));
            }
            if (destination == null) throw new IllegalStateException("No safe return position; checkpoint retained");
            List<ItemStack> acquired = List.of();
            if (snapshot.getBoolean("exportAcquired") && snapshot.contains("sceneInitialInventory")
                    && !snapshot.getBoolean("stale")) {
                var quest = QuestRepository.find(snapshot.getString("quest"));
                if (quest != null && QuestRuntime.completed(player, quest)
                        && quest.completionPolicy().reenterStructure()
                        && quest.completionPolicy().exportAcquiredItems() && current(player))
                    acquired = acquiredItems(player, snapshot);
            }
            player.closeContainer();
            player.stopRiding();
            player.getInventory().clearContent();
            player.getInventory().load(snapshot.getList("inventory", Tag.TAG_COMPOUND));
            player.getInventory().selected = snapshot.getInt("selected");
            player.getEnderChestInventory().clearContent();
            player.getEnderChestInventory().fromTag(snapshot.getList("ender", Tag.TAG_COMPOUND), player.registryAccess());
            player.removeAllEffects();
            for (var tag : snapshot.getList("effects", Tag.TAG_COMPOUND)) {
                var effect = net.minecraft.world.effect.MobEffectInstance.load((CompoundTag) tag);
                if (effect != null) player.addEffect(effect);
            }
            player.setGameMode(GameType.byId(snapshot.getInt("mode")));
            player.setExperienceLevels(snapshot.getInt("xpLevel"));
            player.setExperiencePoints(Math.round(snapshot.getFloat("xpProgress") * player.getXpNeededForNextLevel()));
            player.totalExperience = snapshot.getInt("xpTotal");
            player.setHealth(Math.max(1, snapshot.getFloat("health")));
            player.getFoodData().setFoodLevel(snapshot.getInt("food"));
            player.getFoodData().setSaturation(snapshot.getFloat("saturation"));
            if (snapshot.contains("foodData")) player.getFoodData().readAdditionalSaveData(snapshot.getCompound("foodData"));
            player.clearFire(); player.fallDistance = 0;
            player.teleportTo(level, destination.x, destination.y, destination.z, snapshot.getFloat("yaw"), snapshot.getFloat("pitch"));
            for (ItemStack stack : acquired) {
                ItemStack remaining = stack.copy();
                player.getInventory().add(remaining);
                if (!remaining.isEmpty()) player.drop(remaining, false);
            }
            player.containerMenu.broadcastChanges();
            // Persist restored inventory before removing its recovery checkpoint. Formal rewards run later.
            player.server.getPlayerList().saveAll();
            players.remove(id);
            live.remove(player);
            sessions.setDirty();
            var scene = player.server.getLevel(DIMENSION);
            if (scene != null) {
                var origin = new BlockPos(snapshot.getInt("originX"), 80, snapshot.getInt("originZ"));
                cleanup(scene, origin);
                force(scene, origin, false);
            }
            player.server.overworld().getDataStorage().save();
        } finally { returning.remove(player.getUUID()); }
    }
    private static List<ItemStack> acquiredItems(ServerPlayer player, CompoundTag snapshot) {
        var baseline = new Inventory(player);
        baseline.load(snapshot.getList("sceneInitialInventory", Tag.TAG_COMPOUND));
        int[] remaining = new int[baseline.getContainerSize()];
        for (int slot = 0; slot < remaining.length; slot++) remaining[slot] = baseline.getItem(slot).getCount();
        List<ItemStack> acquired = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            int count = stack.getCount();
            for (int old = 0; old < remaining.length && count > 0; old++) {
                if (remaining[old] == 0 || !ItemStack.isSameItemSameComponents(stack, baseline.getItem(old))) continue;
                int matched = Math.min(count, remaining[old]);
                remaining[old] -= matched;
                count -= matched;
            }
            if (count > 0) acquired.add(stack.copyWithCount(count));
        }
        return acquired;
    }
    public static void checkBoundary(ServerPlayer player) {
        if (!active(player)) return;
        current(player);
        var snapshot = sessions(player).state.getCompound("players").getCompound(player.getUUID().toString());
        if (!player.level().dimension().equals(DIMENSION) || Math.abs(player.getX() - snapshot.getInt("originX") - 16) > 64
                || Math.abs(player.getZ() - snapshot.getInt("originZ") - 16) > 64 || player.getY() < 60 || player.getY() > 160)
            leave(player);
    }
    private static void equip(ServerPlayer player, CompoundTag snapshot, QuestSceneSettings settings) {
        player.getInventory().clearContent();
        if (settings.inventory().equals("player_copy"))
            player.getInventory().load(snapshot.getList("initialInventory", Tag.TAG_COMPOUND));
        else if (settings.inventory().equals("task"))
            for (var slot : settings.equipment()) player.getInventory().setItem(slot.slot(), slot.stack(player));
        player.getInventory().setChanged();
    }
    public static void reset(ServerPlayer player) throws Exception {
        if (!active(player) || editing(player) || !current(player)) throw new IllegalArgumentException("structure.stale");
        var snapshot = snapshot(player);
        var level = player.server.getLevel(DIMENSION);
        var template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), read(snapshot.getString("reference")));
        player.closeContainer();
        cleanup(level, origin(player));
        place(level, origin(player), template);
        equip(player, snapshot, settings(player));
        player.getEnderChestInventory().clearContent(); player.removeAllEffects();
        player.setExperienceLevels(0); player.setExperiencePoints(0);
        snapshot.put("triggerState", new CompoundTag());
        snapshot.remove("checkpoint");
        snapshot.putLong("started", level.getGameTime());
        sessions(player).setDirty();
        respawn(player, false);
        player.containerMenu.broadcastChanges();
        // Intentionally never call QuestRuntime.reset/resetTrusted here.
    }
    public static void respawn(ServerPlayer player, boolean checkpoint) {
        var snapshot = snapshot(player);
        var spawn = settings(player).spawn();
        Vec3 pos = Vec3.atLowerCornerOf(origin(player)).add(spawn.x(), spawn.y(), spawn.z());
        if (checkpoint && snapshot.contains("checkpoint")) {
            var data = snapshot.getCompound("checkpoint");
            pos = new Vec3(data.getDouble("x"), data.getDouble("y"), data.getDouble("z"));
        }
        player.closeContainer(); player.stopRiding();
        player.clearFire(); player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
        player.teleportTo(player.server.getLevel(DIMENSION), pos.x, pos.y, pos.z, spawn.yaw(), spawn.pitch());
    }
    public static void checkpoint(ServerPlayer player) {
        if (!active(player)) return;
        var data = new CompoundTag();
        data.putDouble("x", player.getX()); data.putDouble("y", player.getY()); data.putDouble("z", player.getZ());
        snapshot(player).put("checkpoint", data); sessions(player).setDirty();
    }
    static void dirty(ServerPlayer player) { sessions(player).setDirty(); }
    public static void releaseOrphanChunks(net.minecraft.server.MinecraftServer server) {
        var scene = server.getLevel(DIMENSION);
        if (scene == null) return;
        var sessions = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Sessions::new,
                (tag, registries) -> { var data = new Sessions(); data.state = tag; return data; }), KEY);
        var players = sessions.state.getCompound("players");
        for (String id : players.getAllKeys()) {
            var entry = players.getCompound(id);
            force(scene, new BlockPos(entry.getInt("originX"), 80, entry.getInt("originZ")), false);
        }
    }
    public static String complete(ServerPlayer player) {
        if (!active(player) || editing(player) || !current(player) || task(player).isEmpty())
            return "unavailable";
        return QuestRuntime.completeFromStructure(player, task(player));
    }
    /** Executes a configured post-completion return through the normal checkpoint transaction. */
    public static void tick(ServerPlayer player) {
        if (!active(player) || editing(player)) return;
        var state = snapshot(player);
        if (!state.getBoolean("completedBeforeEntry") && !state.getBoolean("exitScheduled")
                && !task(player).isBlank()) {
            var quest = QuestRepository.find(task(player));
            if (quest != null && QuestRuntime.completed(player, quest)) {
                state.putBoolean("exitScheduled", true);
                int delay = settings(player).exitDelay();
                if (delay > 0) state.putLong("exitAt", player.serverLevel().getGameTime() + delay * 20L);
                dirty(player);
            }
        }
        long exitAt = state.getLong("exitAt");
        if (exitAt > 0 && player.serverLevel().getGameTime() >= exitAt) {
            state.remove("exitAt");
            dirty(player);
            leave(player);
        }
    }
    public static void signal(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(DIMENSION)) return;
        for (var player : level.players()) if (active(player) && !editing(player)
                && pos.closerThan(origin(player).offset(16, 16, 16), 40)) complete(player);
    }
    private static void force(ServerLevel level, BlockPos origin, boolean value) {
        for (int x = (origin.getX() - 16) >> 4; x <= (origin.getX() + 48) >> 4; x++)
            for (int z = (origin.getZ() - 16) >> 4; z <= (origin.getZ() + 48) >> 4; z++)
                level.setChunkForced(x, z, value);
    }
    private static void place(ServerLevel level, BlockPos origin, StructureTemplate template) {
        for (int x = -3; x <= 35; x++) for (int z = -3; z <= 35; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            level.setBlock(origin.offset(x, 35, z), Blocks.BARRIER.defaultBlockState(), 2);
            if (x == -3 || z == -3 || x == 35 || z == 35)
                for (int y = 0; y < 35; y++)
                    level.setBlock(origin.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), 2);
        }
        if (!template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(false),
                level.random, 3)) throw new IllegalArgumentException("structure.invalid");
    }
    private static void cleanup(ServerLevel level, BlockPos origin) {
        if (level == null) return;
        var bounds = new BoundingBox(origin.getX() - 100, -64, origin.getZ() - 100,
                origin.getX() + 132, 320, origin.getZ() + 132);
        for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, AABB.of(bounds)))
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) entity.discard();
        level.getBlockTicks().clearArea(bounds);
        level.getFluidTicks().clearArea(bounds);
        level.clearBlockEvents(bounds);
        for (var pos : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(35, 35, 35)))
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        level.getBlockTicks().clearArea(bounds); level.getFluidTicks().clearArea(bounds); level.clearBlockEvents(bounds);
        // Container removal can spawn drops during block cleanup, after the initial entity sweep.
        for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, AABB.of(bounds)))
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) entity.discard();
    }
    private static Vec3 safeReturn(ServerLevel level, Vec3 desired) {
        if (!Double.isFinite(desired.x) || !Double.isFinite(desired.y) || !Double.isFinite(desired.z)) return null;
        // Keep an unchanged, clear original position (including a flying player's position).
        if (safe(level, desired)) return desired;
        var centre = BlockPos.containing(desired);
        for (int radius = 0; radius <= 8; radius++) for (int y = 0; y <= 16; y++)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                Vec3 pos = Vec3.atBottomCenterOf(centre.offset(dx, y, dz));
                if (safe(level, pos) && level.getBlockState(BlockPos.containing(pos).below()).isSolid()) return pos;
            }
        return null;
    }
    private static boolean safe(ServerLevel level, Vec3 pos) {
        var block = BlockPos.containing(pos);
        var box = new AABB(pos.x - .3, pos.y, pos.z - .3, pos.x + .3, pos.y + 1.8, pos.z + .3);
        if (pos.y < level.getMinBuildHeight() + 1 || pos.y > level.getMaxBuildHeight() - 2
                || !level.getWorldBorder().isWithinBounds(block)) return false;
        level.getChunkAt(block);
        var below = level.getBlockState(block.below());
        return level.noCollision(box) && !level.containsAnyLiquid(box)
                && !below.is(Blocks.MAGMA_BLOCK) && !below.is(Blocks.CAMPFIRE)
                && !below.is(Blocks.SOUL_CAMPFIRE) && !below.is(Blocks.CACTUS)
                && !level.getBlockState(block).is(Blocks.FIRE) && !level.getBlockState(block).is(Blocks.SOUL_FIRE);
    }
    public static final class Sessions extends SavedData {
        private CompoundTag state = new CompoundTag();
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { return state.copy(); }
    }
}
