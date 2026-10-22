package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot task generation from everything installed: every displayed advancement (chained by its
 * parent), every obtainable item (chained along its cheapest crafting recipe) and every hostile
 * mob. Each namespace (vanilla or mod) gets its own group {@code auto_<namespace>} with numbered
 * chapters. Re-running adds missing tasks and updates unchanged generator-owned rewards.
 */
public final class QuestGenerator {
    private record Planned(String id, String namespace, String category, String chapter,
                           JsonObject json, List<String> parentCandidates, int spotlightScore) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int INFINITE = Integer.MAX_VALUE / 4;
    /** Chapter keys in display order; the number keeps the file (and so the chapter) order stable. */
    private static final List<String> CHAPTERS = List.of("1_advancements", "2_gather", "3_craft_basic",
            "4_craft_advanced", "5_craft_expert", "6_combat", "7_exploration");
    private static final Set<String> TECHNICAL_ITEMS = Set.of(
            "xero_quest_journal:scene_completion",
            "minecraft:air", "minecraft:barrier", "minecraft:light", "minecraft:structure_void",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:command_block_minecart", "minecraft:debug_stick", "minecraft:knowledge_book",
            "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:reinforced_deepslate",
            "minecraft:petrified_oak_slab", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault",
            "minecraft:budding_amethyst", "minecraft:farmland", "minecraft:dirt_path", "minecraft:chorus_plant",
            "minecraft:frogspawn", "minecraft:player_head", "minecraft:infested_stone",
            "minecraft:infested_cobblestone", "minecraft:infested_stone_bricks",
            "minecraft:infested_mossy_stone_bricks", "minecraft:infested_cracked_stone_bricks",
            "minecraft:infested_chiseled_stone_bricks", "minecraft:infested_deepslate");
    private static final Set<String> EXCLUDED_MOBS = Set.of("minecraft:giant", "minecraft:illusioner");
    // Survival operations whose outputs are not represented by ordinary recipe/loot JSON.
    private static final Set<String> OPERATION_ITEMS = Set.of(
            "minecraft:water_bucket", "minecraft:lava_bucket", "minecraft:milk_bucket",
            "minecraft:powder_snow_bucket", "minecraft:cod_bucket", "minecraft:salmon_bucket",
            "minecraft:pufferfish_bucket", "minecraft:tropical_fish_bucket", "minecraft:axolotl_bucket",
            "minecraft:tadpole_bucket", "minecraft:potion", "minecraft:splash_potion",
            "minecraft:lingering_potion", "minecraft:tipped_arrow", "minecraft:dragon_breath",
            "minecraft:honey_bottle", "minecraft:filled_map", "minecraft:written_book",
            "minecraft:enchanted_book", "minecraft:turtle_scute", "minecraft:armadillo_scute");

    private QuestGenerator() {}

    /** Namespaces that have something to generate, for command suggestions. */
    public static Set<String> namespaces(MinecraftServer server) {
        Set<String> result = new TreeSet<>();
        server.getAdvancements().getAllAdvancements().forEach(holder -> {
            if (holder.value().display().isPresent()) result.add(holder.id().getNamespace());
        });
        BuiltInRegistries.ITEM.keySet().forEach(key -> result.add(key.getNamespace()));
        BuiltInRegistries.ENTITY_TYPE.keySet().forEach(key -> result.add(key.getNamespace()));
        server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().forEach(key -> result.add(key.getNamespace()));
        xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.models(server.registryAccess())
                .forEach(model -> result.add(model.id().getNamespace()));
        return result;
    }

    /** Receives progress lines; called from the worker thread. */
    @FunctionalInterface
    public interface Reporter {
        void send(Component message, boolean failure);
    }

    /** Advancement data copied on the server thread, so planning never touches live server state. */
    private record AdvancementData(ResourceLocation id, ResourceLocation parent, Component title,
                                   Component description, int tier, String icon, int depth, List<String> items) {}
    private record StructureData(ResourceLocation id, String dimension) {}

    private record TacticalTask(String id, String namespace, String identity, String target, String kind,
                                String title, String titleKey, int tier) {}
    private record TacticalMaterial(String target, int count) {}
    private record TacticalRecipe(String identity, List<TacticalMaterial> materials) {}
    private record TacticalSnapshot(List<TacticalTask> tasks, List<TacticalRecipe> recipes) {}

    private record Snapshot(List<AdvancementData> advancements, Map<Item, List<List<List<Item>>>> recipes,
                            Set<Item> lootItems, Set<String> standardModelRecipes,
                            List<TacticalTask> tacticalTasks, List<TacticalRecipe> tacticalRecipes,
                            FeatureFlagSet features, Set<String> existing, Map<String, Integer> hotCounts,
                            List<StructureData> structures) {}

    /**
     * How generated tasks are rewarded. {@code TIER}: experience (and emeralds/diamonds) by difficulty;
     * {@code RECIPE}: item tasks give recipe materials, or their target when no recipe is available;
     * other task types use difficulty rewards. {@code OUTPUT}: item tasks reward their target item.
     */
    public enum RewardMode {
        TIER, RECIPE, OUTPUT, NONE;

        public static RewardMode parse(String value) {
            for (RewardMode mode : values())
                if (mode.name().equalsIgnoreCase(value == null ? "" : value.strip())) return mode;
            return null;
        }
    }

    /** {@code filter} is a namespace or blank; {@code multiplier} scales every reward amount. */
    public enum GenerationMode { CATALOG, PROGRESS, STRUCTURE, BOTH }
    public record GenerationOptions(int taskMultiplier, int rewardMultiplier, boolean bonusExperience,
                                    boolean itemRewards, boolean advancements, boolean itemTasks,
                                    boolean combatTasks, boolean structureTasks) {
        public GenerationOptions {
            taskMultiplier = Math.clamp(taskMultiplier, 1, 64);
            rewardMultiplier = Math.clamp(rewardMultiplier, 1, 64);
        }
        public static GenerationOptions defaults() {
            return new GenerationOptions(1, 1, true, true, true, true, true, true);
        }
    }
    private record Settings(String filter, RewardMode mode, int multiplier, GenerationMode generation,
                            boolean relatedRecipes, GenerationOptions options) {
        Settings(String filter, RewardMode mode, int multiplier) {
            this(filter, mode, multiplier, GenerationMode.CATALOG, true, GenerationOptions.defaults());
        }
        Settings(String filter, RewardMode mode, int multiplier, GenerationMode generation,
                 boolean relatedRecipes) {
            this(filter, mode, multiplier, generation, relatedRecipes, GenerationOptions.defaults());
        }
        int taskCount(int base) {
            return Math.max(1, Math.min(1_000_000, base * options.taskMultiplier()));
        }
    }

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Xero Quest Generator");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    public static boolean running() {
        return RUNNING.get();
    }

    public record DeleteResult(int tasks, String error) {}

    /** Only generator-owned tasks are removed; manual tasks inside generated groups survive. */
    public static DeleteResult deleteGenerated(MinecraftServer server, String namespace) {
        if (server == null || !server.isSameThread()) return new DeleteResult(0, "permission");
        String filter = namespace == null || namespace.equals("all") ? "" : namespace.strip();
        if (!filter.isBlank() && !filter.matches("[a-z0-9_.-]+"))
            return new DeleteResult(0, "invalid");
        if (!RUNNING.compareAndSet(false, true)) return new DeleteResult(0, "busy");
        try {
            synchronized (QuestRepository.class) {
                QuestRepository.reload();
                if (!QuestRepository.errors().isEmpty()) return new DeleteResult(0, "definitions");
                Set<String> tasks = new HashSet<>();
                for (var quest : QuestRepository.all()) {
                    String group = QuestRepository.groupOf(quest.id());
                    if (!group.startsWith("auto_")) continue;
                    String owner = group.substring(5);
                    if (!filter.isBlank() && !owner.equals(filter)) continue;
                    Path file = QuestRepository.fileOf(quest.id());
                    if (file == null || !file.getFileName().toString().equals(quest.id() + ".json")) continue;
                    var data = JsonParser.parseString(QuestRepository.source(quest.id())).getAsJsonObject();
                    boolean marked = data.has("generatedBy")
                            && data.get("generatedBy").getAsString().equals("xero_quest_journal");
                    boolean legacy = List.of("auto_adv_", "auto_item_", "auto_kill_").stream()
                            .anyMatch(prefix -> quest.id().startsWith(prefix + owner + "_"));
                    if (marked && quest.id().startsWith("auto_") || legacy) tasks.add(quest.id());
                }
                if (tasks.isEmpty()) return new DeleteResult(0, null);
                Set<String> groups = new HashSet<>();
                Set<String> chapters = new HashSet<>();
                for (var group : QuestRepository.groups()) {
                    if (!group.id().startsWith("auto_")) continue;
                    if (!filter.isBlank() && !group.id().equals("auto_" + filter)) continue;
                    boolean hasManual = QuestRepository.all().stream()
                            .anyMatch(q -> QuestRepository.groupOf(q.id()).equals(group.id()) && !tasks.contains(q.id()));
                    boolean hasManualChapter = QuestRepository.chapters().stream()
                            .anyMatch(c -> c.groupId().equals(group.id()) && !generatedChapter(c.id(), group.id()));
                    if (!hasManual && !hasManualChapter) groups.add(group.id());
                }
                for (var chapter : QuestRepository.chapters()) {
                    if (generatedChapter(chapter.id(), chapter.groupId())
                            && chapter.quests().stream().anyMatch(tasks::contains)
                            && chapter.quests().stream().allMatch(tasks::contains)) chapters.add(chapter.id());
                }
                var result = QuestBatchAuthoring.deleteGenerated(server, tasks, groups, chapters);
                return new DeleteResult(result.successful() ? tasks.size() : 0, result.error());
            }
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to remove generated tasks", failure);
            return new DeleteResult(0, "save");
        } finally {
            RUNNING.set(false);
        }
    }

    private static boolean generatedChapter(String id, String group) {
        return group.startsWith("auto_") && CHAPTERS.stream().anyMatch(key -> id.equals(group + "_" + key));
    }

    /**
     * Starts generation in the background for one namespace, or for all when blank. Only the data
     * copy (advancements, recipes) runs on the calling server thread. Namespaces are then processed
     * one after another: each one is written to disk (saved) and loaded before the next begins,
     * with file reading on the worker and only the final snapshot swap on the server thread.
     * Returns false when a generation is already running.
     */
    public static boolean start(MinecraftServer server, String namespace, RewardMode mode, int multiplier,
                                Reporter reporter) {
        return start(server, namespace, mode, multiplier, GenerationMode.CATALOG, true, reporter);
    }
    public static boolean start(MinecraftServer server, String namespace, RewardMode mode, int multiplier,
                                GenerationMode generation, boolean relatedRecipes, Reporter reporter) {
        return start(server, namespace, mode, multiplier, generation, relatedRecipes,
                new GenerationOptions(1, multiplier, true, true, true, true, true, true), reporter);
    }
    public static boolean start(MinecraftServer server, String namespace, RewardMode mode, int multiplier,
                                GenerationMode generation, boolean relatedRecipes, GenerationOptions options,
                                Reporter reporter) {
        if (server == null || !server.isSameThread()) throw new IllegalArgumentException("Generation requires server thread");
        if (!RUNNING.compareAndSet(false, true)) return false;
        String target = namespace == null ? "" : namespace.strip().toLowerCase(Locale.ROOT);
        Settings settings = new Settings(target.equals("all") ? "" : target, mode == null ? RewardMode.TIER : mode,
                options == null ? Math.max(1, Math.min(64, multiplier))
                        : options.rewardMultiplier(), generation, relatedRecipes,
                options == null ? new GenerationOptions(1, multiplier, true, true, true, true, true, true)
                        : options);
        Snapshot snapshot;
        try {
            snapshot = snapshot(server);
        } catch (RuntimeException failure) {
            RUNNING.set(false);
            throw failure;
        }
        WORKER.execute(() -> {
            try {
                run(server, settings, snapshot, reporter);
            } catch (Exception failure) {
                XeroQuestJournal.LOGGER.error("Task generation failed", failure);
                reporter.send(Component.translatable("command.xero_quest_journal.generate_failed",
                        String.valueOf(failure.getMessage())), true);
            } finally {
                RUNNING.set(false);
            }
        });
        return true;
    }

    private static Snapshot snapshot(MinecraftServer server) {
        Map<ResourceLocation, AdvancementHolder> byId = new HashMap<>();
        server.getAdvancements().getAllAdvancements().forEach(holder -> {
            if (holder.value().display().isPresent()) byId.put(holder.id(), holder);
        });
        List<AdvancementData> advancements = new ArrayList<>();
        for (var holder : byId.values()) {
            DisplayInfo display = holder.value().display().orElseThrow();
            int tier = switch (display.getType()) {
                case CHALLENGE -> 3;
                case GOAL -> 2;
                default -> 1;
            };
            ResourceLocation parent = holder.value().parent().filter(byId::containsKey).orElse(null);
            advancements.add(new AdvancementData(holder.id(), parent, display.getTitle(), display.getDescription(),
                    tier, itemId(display.getIcon()), advancementDepth(holder.id(), byId), advancementItems(server, holder.id())));
        }
        // Tree order: roots first, then each level, so chapters read like the advancement screen.
        advancements.sort(Comparator.comparingInt(AdvancementData::depth)
                .thenComparing(data -> data.id().toString()));
        Set<String> existing = new HashSet<>();
        Map<String, Integer> hotCounts = new HashMap<>();
        QuestRepository.all().forEach(quest -> {
            existing.add(quest.id());
            String group = QuestRepository.groupOf(quest.id());
            if (quest.hotEvent().enabled() && group.startsWith("auto_"))
                hotCounts.merge(group.substring(5), 1, Integer::sum);
        });
        Map<Item, List<List<List<Item>>>> recipes = recipes(server);
        Set<Item> lootItems = lootTableItems(server);
        Set<String> standardModelRecipes = modelRecipeOutputs(server);
        TacticalSnapshot tactical = tacticalSnapshot(server, recipes.keySet(), lootItems, standardModelRecipes);
        return new Snapshot(List.copyOf(advancements), recipes, Set.copyOf(lootItems), standardModelRecipes,
                tactical.tasks(), tactical.recipes(),
                server.getWorldData().enabledFeatures(), Set.copyOf(existing), Map.copyOf(hotCounts), structureSnapshot(server));
    }

    private static List<String> advancementItems(MinecraftServer server, ResourceLocation advancement) {
        var file = ResourceLocation.fromNamespaceAndPath(advancement.getNamespace(), "advancement/" + advancement.getPath() + ".json");
        var resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) return List.of();
        var result = new LinkedHashSet<String>();
        try (var reader = resource.get().openAsReader()) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            if (!json.has("criteria")) return List.of();
            for (var entry : json.getAsJsonObject("criteria").entrySet()) {
                var criterion = entry.getValue().getAsJsonObject();
                if (!criterion.has("trigger") || !criterion.get("trigger").getAsString().equals("minecraft:inventory_changed")
                        || !criterion.has("conditions")) continue;
                var conditions = criterion.getAsJsonObject("conditions");
                if (!conditions.has("items") || !conditions.get("items").isJsonArray()) continue;
                for (var predicate : conditions.getAsJsonArray("items")) {
                    if (!predicate.isJsonObject()) continue;
                    var items = predicate.getAsJsonObject().get("items");
                    if (items == null) continue;
                    var candidates = new ArrayList<JsonElement>();
                    if (items.isJsonArray()) items.getAsJsonArray().forEach(candidates::add); else candidates.add(items);
                    for (var item : candidates) if (item.isJsonPrimitive()) {
                        var id = ResourceLocation.tryParse(item.getAsString());
                        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) result.add(id.toString());
                    }
                }
            }
        } catch (Exception unsupported) { return List.of(); }
        return result.stream().limit(32).toList();
    }

    private static List<StructureData> structureSnapshot(MinecraftServer server) {
        if (!server.getWorldData().worldGenOptions().generateStructures()) return List.of();
        var result = new ArrayList<StructureData>();
        var registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var used = new HashSet<ResourceLocation>();
        server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).forEach(set ->
                set.structures().forEach(entry -> entry.structure().unwrapKey().ifPresent(key -> used.add(key.location()))));
        for (var id : registry.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            if (!used.contains(id)) continue;
            var structure = registry.get(id);
            for (var level : server.getAllLevels()) {
                if (level.dimension().location().getNamespace().equals("xero_quest_journal")) continue;
                if (level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream().anyMatch(structure.biomes()::contains)) {
                    result.add(new StructureData(id, level.dimension().location().toString())); break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static Set<String> modelRecipeOutputs(MinecraftServer server) {
        Set<String> result = new HashSet<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                String identity = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(
                        holder.value().getResultItem(server.registryAccess()));
                if (!identity.isBlank()) result.add(identity);
            } catch (RuntimeException unsupported) {
                XeroQuestJournal.LOGGER.debug("Skipping special-item recipe {} during task generation",
                        holder.id(), unsupported);
            }
        }
        return Set.copyOf(result);
    }

    private static Set<Item> lootTableItems(MinecraftServer server) {
        var resources = server.getResourceManager().listResources("loot_table",
                id -> id.getPath().endsWith(".json"));
        Map<ResourceLocation, JsonObject> tables = new HashMap<>();
        resources.forEach((resourceId, resource) -> {
            String path = resourceId.getPath();
            if (path.startsWith("loot_table/")) path = path.substring("loot_table/".length());
            if (path.endsWith(".json")) path = path.substring(0, path.length() - ".json".length());
            ResourceLocation tableId = ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path);
            try (var reader = resource.openAsReader()) {
                JsonElement source = JsonParser.parseReader(reader);
                if (source.isJsonObject()) tables.put(tableId, source.getAsJsonObject());
            } catch (Exception invalid) {
                XeroQuestJournal.LOGGER.debug("Skipping loot table {} during task generation",
                        resourceId, invalid);
            }
        });
        Set<Item> result = new HashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();
        tables.forEach((id, table) -> collectLootTable(id, table, tables, visited, result, 0));
        return Set.copyOf(result);
    }

    private static void collectLootTable(ResourceLocation id, JsonObject table,
                                         Map<ResourceLocation, JsonObject> tables,
                                         Set<ResourceLocation> visited, Set<Item> result, int depth) {
        if (depth > 48 || !visited.add(id)) return;
        if (table.has("pools") && table.get("pools").isJsonArray()) {
            for (JsonElement pool : table.getAsJsonArray("pools")) {
                if (pool.isJsonObject()) collectLootEntries(pool.getAsJsonObject().get("entries"),
                        tables, visited, result, depth + 1);
            }
        }
        collectLootFunctions(table.get("functions"), tables, visited, result, depth + 1, null);
    }

    private static void collectLootEntries(JsonElement entries, Map<ResourceLocation, JsonObject> tables,
                                           Set<ResourceLocation> visited, Set<Item> result, int depth) {
        if (entries == null || !entries.isJsonArray() || depth > 48) return;
        for (JsonElement element : entries.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String type = entry.has("type") ? entry.get("type").getAsString() : "";
            if (type.endsWith(":item") || type.equals("item")) {
                ResourceLocation id = entry.has("name")
                        ? ResourceLocation.tryParse(entry.get("name").getAsString()) : null;
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) result.add(BuiltInRegistries.ITEM.get(id));
                collectLootFunctions(entry.get("functions"), tables, visited, result, depth + 1, id);
            } else if (type.endsWith(":tag") || type.equals("tag")) {
                ResourceLocation id = entry.has("name")
                        ? ResourceLocation.tryParse(entry.get("name").getAsString()) : null;
                if (id != null) BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                        .ifPresent(values -> values.forEach(holder -> result.add(holder.value())));
                collectLootFunctions(entry.get("functions"), tables, visited, result, depth + 1, null);
            } else if (type.endsWith(":loot_table") || type.equals("loot_table")) {
                ResourceLocation referenced = entry.has("name")
                        ? ResourceLocation.tryParse(entry.get("name").getAsString()) : null;
                JsonObject table = referenced == null ? null : tables.get(referenced);
                if (table != null) collectLootTable(referenced, table, tables, visited, result, depth + 1);
                collectLootFunctions(entry.get("functions"), tables, visited, result, depth + 1, null);
            } else {
                collectLootFunctions(entry.get("functions"), tables, visited, result, depth + 1, null);
            }
            collectLootEntries(entry.get("children"), tables, visited, result, depth + 1);
        }
    }

    private static void collectLootFunctions(JsonElement functions, Map<ResourceLocation, JsonObject> tables,
                                             Set<ResourceLocation> visited, Set<Item> result, int depth,
                                             ResourceLocation sourceItem) {
        if (functions == null || !functions.isJsonArray() || depth > 48) return;
        for (JsonElement function : functions.getAsJsonArray()) {
            if (!function.isJsonObject()) continue;
            JsonObject object = function.getAsJsonObject();
            String type = object.has("function") ? object.get("function").getAsString() : "";
            if ((type.endsWith(":enchant_with_levels") || type.endsWith(":enchant_randomly"))
                    && ResourceLocation.withDefaultNamespace("book").equals(sourceItem)) {
                result.add(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("enchanted_book")));
            } else if (type.endsWith(":set_item") && object.has("item")) {
                ResourceLocation id = ResourceLocation.tryParse(object.get("item").getAsString());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) result.add(BuiltInRegistries.ITEM.get(id));
            }
            if (object.has("entries")) collectLootEntries(object.get("entries"), tables, visited, result, depth + 1);
            if (object.has("contents")) collectLootEntries(object.get("contents"), tables, visited, result, depth + 1);
            if (object.has("loot_table") && object.get("loot_table").isJsonPrimitive()) {
                ResourceLocation reference = ResourceLocation.tryParse(object.get("loot_table").getAsString());
                JsonObject table = reference == null ? null : tables.get(reference);
                if (table != null) collectLootTable(reference, table, tables, visited, result, depth + 1);
            }
        }
    }

    private static TacticalSnapshot tacticalSnapshot(MinecraftServer server, Set<Item> recipeOutputs,
                                                      Set<Item> lootItems, Set<String> recipeIdentities) {
        Map<String, TacticalTask> tasks = new LinkedHashMap<>();
        for (var model : xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.models(
                server.registryAccess())) {
            String target = xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue.encodeModel(
                    model.stack(), server.registryAccess());
            String identity = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(model.stack());
            if (target.isBlank() || identity.isBlank()) continue;
            if (!recipeIdentities.contains(identity) && !recipeOutputs.contains(model.stack().getItem())
                    && !lootItems.contains(model.stack().getItem())) continue;
            String titleKey = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat
                    .translationKey(model.name());
            String id = baseId("auto_item_" + model.kind(), model.id());
            tasks.putIfAbsent(identity, new TacticalTask(id, model.id().getNamespace(), identity, target,
                    model.kind(), xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat
                    .readableName(model.id()), titleKey, model.tier()));
        }

        List<TacticalRecipe> recipes = new ArrayList<>();
        for (var recipe : xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.recipes()) {
            String identity = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(recipe.result());
            if (identity.isBlank() || !tasks.containsKey(identity)) continue;
            List<TacticalMaterial> materials = new ArrayList<>();
            for (var material : recipe.ingredients()) {
                String target = material.alternatives().stream()
                        .map(stack -> tacticalMaterialTarget(stack, server))
                        .filter(value -> !value.isBlank()).findFirst().orElse("");
                if (!target.isBlank()) materials.add(new TacticalMaterial(target, material.count()));
            }
            if (!materials.isEmpty()) recipes.add(new TacticalRecipe(identity, List.copyOf(materials)));
        }
        return new TacticalSnapshot(List.copyOf(tasks.values()), List.copyOf(recipes));
    }

    private static String tacticalMaterialTarget(ItemStack stack, MinecraftServer server) {
        String identity = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(stack);
        if (!identity.isBlank())
            return xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue.encodeModel(
                    stack, server.registryAccess());
        if (xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.dynamicItem(stack.getItem())) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void run(MinecraftServer server, Settings settings, Snapshot snapshot, Reporter reporter)
            throws Exception {
        if (settings.generation() != GenerationMode.CATALOG) {
            runStory(server, settings, snapshot, reporter); return;
        }
        Set<String> existing = new HashSet<>(snapshot.existing());
        Map<String, Planned> planned = new LinkedHashMap<>();
        if (settings.options().advancements())
            planAdvancements(snapshot.advancements(), settings, Set.of(), planned);
        if (settings.options().itemTasks()) {
            planItems(snapshot.recipes(), snapshot.lootItems(), settings, snapshot.features(), Set.of(), planned);
            planTacticalItems(snapshot.tacticalTasks(), snapshot.tacticalRecipes(), settings, Set.of(), planned);
        }
        if (settings.options().combatTasks()) planKills(settings, snapshot.features(), Set.of(), planned);
        // Existing generator-owned documents can receive absent presentation defaults.
        // Explicit hot-event fields and edited titles/descriptions are never overwritten.
        Map<String, JsonObject> legacy = new HashMap<>();
        Map<String, String> legacySources = new HashMap<>();
        var iterator = planned.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var task = entry.getValue();
            Path path = taskFile(task);
            if (!Files.exists(path)) {
                if (existing.contains(task.id())) iterator.remove();
                continue;
            }
            if (Files.isSymbolicLink(path)) { iterator.remove(); continue; }
            String original = Files.readString(path);
            var data = JsonParser.parseString(original).getAsJsonObject();
            boolean managedTriggers = data.has("generatedTriggers")
                    && java.util.Objects.equals(data.get("triggers"), data.get("generatedTriggers"));
            if (!data.has("generatedBy") || !data.get("generatedBy").getAsString().equals("xero_quest_journal")
                    || !data.has("generatedNamespace")
                    || !data.get("generatedNamespace").getAsString().equals(task.namespace())
                    || !managedTriggers && !java.util.Objects.equals(data.get("triggers"), task.json().get("triggers"))) {
                iterator.remove();
                continue;
            }
            legacy.put(task.id(), data);
            legacySources.put(task.id(), original);
            existing.add(task.id());
        }
        var eligible = planned.values().stream().filter(task -> !legacy.containsKey(task.id())
                || !legacy.get(task.id()).has("hotEvent")).toList();
        selectHotEvents(eligible, snapshot.hotCounts());
        Map<String, List<String>> dependencies = new HashMap<>();
        QuestRepository.all().forEach(task -> dependencies.put(task.id(), task.parents()));
        legacy.forEach((id, data) -> {
            List<String> parents = new ArrayList<>();
            if (data.has("parents")) data.getAsJsonArray("parents")
                    .forEach(parent -> parents.add(parent.getAsString()));
            dependencies.put(id, List.copyOf(parents));
        });
        planned.values().stream().filter(task -> !existing.contains(task.id()))
                .forEach(task -> dependencies.put(task.id(), task.parentCandidates()));
        // Only unchanged generated reward definitions follow a newly requested reward mode.
        for (var task : List.copyOf(planned.values())) {
            var data = legacy.get(task.id());
            if (data == null) continue;
            boolean changed = updateGeneratedParents(data, task, dependencies, existing);
            changed |= updateGeneratedTriggers(data, task.json());
            changed |= updateGeneratedRewards(data, task.json());
            String oldTitleKey = data.has("titleKey") ? data.get("titleKey").getAsString() : "";
            if ((oldTitleKey.startsWith("item.") || oldTitleKey.startsWith("block.")
                    || oldTitleKey.startsWith("entity."))
                    && data.has("title") && data.get("title").getAsString()
                    .equals(Component.translatable(oldTitleKey).getString())
                    && task.json().has("titleKey") && task.json().get("titleKey").getAsString().startsWith("generate.")) {
                data.add("title", task.json().get("title").deepCopy());
                data.add("titleKey", task.json().get("titleKey").deepCopy());
                changed = true;
            }
            if ((!data.has("description") || data.get("description").getAsString().isBlank())
                    && (!data.has("descriptionKey") || data.get("descriptionKey").getAsString().isBlank())) {
                if (!task.json().get("description").getAsString().isBlank()) {
                    data.add("description", task.json().get("description").deepCopy());
                    if (task.json().has("descriptionKey"))
                        data.add("descriptionKey", task.json().get("descriptionKey").deepCopy());
                    changed = true;
                }
            }
            if (!data.has("hotEvent") && task.json().has("hotEvent")) {
                data.add("hotEvent", task.json().get("hotEvent").deepCopy());
                changed = true;
            }
            if (!changed) planned.remove(task.id());
        }
        if (planned.isEmpty()) {
            reporter.send(Component.translatable("command.xero_quest_journal.generate_none"), false);
            return;
        }
        // Vanilla first (mods often build on it), then mods alphabetically.
        Map<String, List<Planned>> byNamespace = new java.util.TreeMap<>(Comparator
                .comparing((String value) -> !value.equals("minecraft")).thenComparing(value -> value));
        planned.values().forEach(value -> byNamespace.computeIfAbsent(value.namespace(), ignored -> new ArrayList<>())
                .add(value));
        reporter.send(Component.translatable("command.xero_quest_journal.generate_started",
                planned.size(), byNamespace.size()), false);

        Set<String> available = new HashSet<>(existing);
        int advancements = 0, items = 0, kills = 0, groups = 0, index = 0, enriched = 0;
        for (var entry : byNamespace.entrySet()) {
            if (!server.isRunning()) return;
            index++;
            String groupId = "auto_" + clean(entry.getKey());
            Path directory = groupDirectory(entry.getKey());
            Set<String> batch = new HashSet<>();
            entry.getValue().forEach(quest -> batch.add(quest.id()));
            Map<String, List<String>> chapterAdditions = new LinkedHashMap<>();
            List<String> written = new ArrayList<>();
            for (Planned quest : entry.getValue()) {
                if (legacy.containsKey(quest.id())) {
                    synchronized (QuestRepository.class) {
                        Path path = taskFile(quest);
                        if (Files.isSymbolicLink(path) || !Files.readString(path).equals(legacySources.get(quest.id())))
                            continue;
                        AtomicFiles.write(path, GSON.toJson(legacy.get(quest.id())), true);
                    }
                    enriched++;
                    written.add(quest.id());
                    continue;
                }
                // Prerequisites may only point at tasks that are already loaded or saved in this batch;
                // links to namespaces processed later are dropped so every load stays valid.
                JsonArray parents = new JsonArray();
                for (String parent : quest.parentCandidates())
                    if (!parent.equals(quest.id()) && (available.contains(parent) || batch.contains(parent))
                            && !dependsOn(parent, quest.id(), dependencies))
                        parents.add(parent);
                quest.json().add("parents", parents);
                quest.json().add("generatedParents", parents.deepCopy());
                List<String> actualParents = new ArrayList<>();
                parents.forEach(parent -> actualParents.add(parent.getAsString()));
                dependencies.put(quest.id(), List.copyOf(actualParents));
                quest.json().addProperty("generatedBy", "xero_quest_journal");
                quest.json().addProperty("generatedNamespace", quest.namespace());
                AtomicFiles.write(taskFile(quest), GSON.toJson(quest.json()), false);
                written.add(quest.id());
                chapterAdditions.computeIfAbsent(quest.chapter(), ignored -> new ArrayList<>()).add(quest.id());
                switch (quest.category()) {
                    case "advancements" -> advancements++;
                    case "items" -> items++;
                    default -> kills++;
                }
            }
            String groupName = modName(entry.getKey());
            writeChapters(directory, groupId, chapterAdditions);
            Path manifest = directory.resolve("group_manifest.json");
            if (QuestRepository.groups().stream().noneMatch(group -> group.id().equals(groupId))
                    && !Files.exists(manifest)) {
                JsonObject group = new JsonObject();
                group.addProperty("id", groupId);
                group.addProperty("uid", UUID.randomUUID().toString());
                group.addProperty("name", groupName);
                group.addProperty("description", Component.translatable(
                        "generate.xero_quest_journal.group_description", groupName).getString());
                group.addProperty("icon", groupIcon(entry.getValue()));
                group.addProperty("type", "active");
                AtomicFiles.write(manifest, GSON.toJson(group), false);
            }
            groups++;
            // Saved; now load it: read files here, swap the snapshot on the server thread.
            QuestRepository.Scan scan = QuestRepository.scan();
            if (!server.isRunning()) return;
            boolean loaded = server.submit(() -> {
                if (QuestRepository.revision() != scan.revisionAtStart()) {
                    // Something else reloaded meanwhile; a full reload keeps both changes.
                    QuestRepository.reload();
                    return QuestRepository.errors().isEmpty();
                }
                return QuestRepository.apply(scan);
            }).get(2, TimeUnit.MINUTES);
            if (!loaded) {
                String error = QuestRepository.errors().isEmpty() ? "?" : QuestRepository.errors().getFirst();
                reporter.send(Component.translatable("command.xero_quest_journal.generate_failed", error), true);
                return;
            }
            available.addAll(written);
            reporter.send(Component.translatable("command.xero_quest_journal.generate_progress",
                    index, byNamespace.size(), groupName, written.size()), false);
        }
        reporter.send(Component.translatable("command.xero_quest_journal.generated",
                advancements + items + kills, advancements, items, kills, groups), false);
        if (enriched > 0) reporter.send(Component.translatable("command.xero_quest_journal.generate_enriched", enriched), false);
    }

    /** Plan progression and exploration as one graph, including cross-namespace recipe dependencies. */
    private static void runStory(MinecraftServer server, Settings settings, Snapshot snapshot, Reporter reporter) throws Exception {
        var plan = new LinkedHashMap<String, Planned>();
        var allItems = new LinkedHashMap<String, Planned>();
        var sourceSettings = new Settings("", RewardMode.RECIPE, settings.multiplier(),
                settings.generation(), settings.relatedRecipes(), settings.options());
        if (settings.generation() != GenerationMode.STRUCTURE && settings.options().itemTasks())
            planItems(snapshot.recipes(), snapshot.lootItems(), sourceSettings, snapshot.features(), Set.of(), allItems);
        var recipeRewards = new HashMap<String, JsonArray>();
        allItems.forEach((id, task) -> recipeRewards.put(id, task.json().getAsJsonArray("rewards").deepCopy()));
        var advancementTasks = new LinkedHashMap<String, Planned>();
        if (settings.generation() != GenerationMode.STRUCTURE && settings.options().advancements())
            planAdvancements(snapshot.advancements(), settings, Set.of(), advancementTasks);
        var wantedItems = new LinkedHashSet<String>();
        var originParents = new HashMap<String, String>();
        for (var advancement : snapshot.advancements()) {
            if (!matches(settings.filter(), advancement.id()) || settings.generation() == GenerationMode.STRUCTURE) continue;
            for (String item : advancement.items()) {
                String id = baseId("auto_item", ResourceLocation.parse(item));
                if (allItems.containsKey(id)) {
                    collectRelatedItems(id, allItems, wantedItems);
                    if (advancement.parent() != null) originParents.put(id, baseId("auto_adv", advancement.parent()));
                }
            }
        }
        if (settings.relatedRecipes()) for (String item : wantedItems) plan.put(item, allItems.get(item));
        plan.putAll(advancementTasks);
        // Each progression reward supplies a small part of a following milestone's actual recipe.
        for (var data : snapshot.advancements()) {
            String id = baseId("auto_adv", data.id());
            var task = plan.get(id); if (task == null) continue;
            var supplies = new LinkedHashMap<String, Integer>();
            for (var child : snapshot.advancements()) if (data.id().equals(child.parent())) {
                for (String output : child.items()) {
                    var recipeTask = allItems.get(baseId("auto_item", ResourceLocation.parse(output)));
                    if (recipeTask == null) continue;
                    for (var reward : recipeTask.json().getAsJsonArray("rewards")) {
                        var value = reward.getAsJsonObject(); String material = value.get("item").getAsString();
                        if (!value.get("type").getAsString().equals("item") || material.equals(output)
                                || child.items().contains(material)) continue;
                        supplies.merge(material, Math.max(1, value.get("count").getAsInt()), Math::max);
                    }
                }
            }
            var rewards = new JsonArray();
            for (var entry : supplies.entrySet().stream().limit(4).toList())
                rewards.add(reward("item", entry.getKey(), entry.getValue()));
            if (rewards.isEmpty()) rewards = storyFallbackRewards(task, settings, recipeRewards);
            task.json().add("rewards", rewards);
            task.json().addProperty("generatedRewardReason", supplies.isEmpty()
                    ? "Difficulty reward because no following recipe materials were found"
                    : "Materials from recipes required by following advancement criteria");
        }
        // An auxiliary recipe branch can start after its associated advancement's parent.
        for (var entry : originParents.entrySet()) {
            var task = plan.get(entry.getKey());
            if (task == null || !plan.containsKey(entry.getValue())) continue;
            var parents = new ArrayList<>(task.parentCandidates()); parents.add(entry.getValue());
            plan.put(task.id(), new Planned(task.id(), task.namespace(), task.category(), task.chapter(), task.json(), parents, task.spotlightScore()));
        }
        // Give recipe tasks support for their next operation instead of returning the task item.
        for (var task : plan.values()) if (task.category().equals("items")) {
            var rewards = new JsonArray();
            var next = plan.values().stream().filter(candidate -> candidate.parentCandidates().contains(task.id())
                    && candidate.category().equals("items")).findFirst().orElse(null);
            if (next != null) for (var element : recipeRewards.get(next.id())) {
                var value = element.getAsJsonObject();
                if (value.get("item").getAsString().equals(task.json().get("icon").getAsString())
                        || value.get("item").getAsString().equals(next.json().get("icon").getAsString())) continue;
                rewards.add(reward("item", value.get("item").getAsString(),
                        Math.max(1, value.get("count").getAsInt())));
                if (rewards.size() == 4) break;
            }
            if (rewards.isEmpty()) rewards = storyFallbackRewards(task, settings, recipeRewards);
            task.json().add("rewards", rewards);
        }
        var renamed = new HashMap<String, String>();
        plan.keySet().forEach(id -> renamed.put(id, baseId("auto_story", ResourceLocation.fromNamespaceAndPath("xero", id))));
        var story = new LinkedHashMap<String, Planned>();
        for (var task : plan.values()) {
            String id = renamed.get(task.id());
            task.json().addProperty("id", id);
            task.json().addProperty("rewardMode", "manual");
            story.put(id, new Planned(id, task.namespace(), "story", task.chapter(), task.json(),
                    task.parentCandidates().stream().filter(renamed::containsKey).map(renamed::get).distinct().toList(), 0));
        }
        if (settings.generation() != GenerationMode.PROGRESS && settings.options().structureTasks()) {
            var previous = new HashMap<String, String>();
            for (var structure : snapshot.structures()) {
                if (!matches(settings.filter(), structure.id())) continue;
                String route = structure.id().getNamespace() + "|" + structure.dimension();
                String id = baseId("auto_story_visit", structure.id());
                String name = Component.translatableWithFallback("structure." + structure.id().getNamespace() + "."
                        + structure.id().getPath().replace('/', '.'), structure.id().toString()).getString();
                var json = quest(id, Component.literal(name), Component.translatable("generate.xero_quest_journal.story.visit"), "world", "minecraft:compass");
                json.add("triggers", trigger("location", "structure|" + structure.dimension() + "|" + structure.id(), 1));
                var controls = new ArrayList<>(xtdpotato.xero_quest_journal.core.QuestTemplateStore.defaultElements());
                var locate = new xtdpotato.xero_quest_journal.core.model.TriggerGraph(List.of(
                        new xtdpotato.xero_quest_journal.core.model.TriggerGraph.Node("click", "event", 0, 0, Map.of("event", "click", "cooldown", "20")),
                        new xtdpotato.xero_quest_journal.core.model.TriggerGraph.Node("locate", "locate_structure", 180, 0,
                                Map.of("target", structure.id().toString(), "dimension", structure.dimension(), "radius", "100", "storeVariable", "globe.located_structure"))),
                        List.of(new xtdpotato.xero_quest_journal.core.model.TriggerGraph.Edge("click", "locate", "next")));
                controls.add(new xtdpotato.xero_quest_journal.core.model.QuestTemplate.Element("locate", "button",
                        Component.translatable("generate.xero_quest_journal.story.locate").getString(), 0, 0, 160, 24,
                        Map.of("eventGraph", GSON.toJson(locate)), "body"));
                var template = new xtdpotato.xero_quest_journal.core.model.QuestTemplate("story_visit", name, "", "world", "minecraft:compass",
                        "minecraft:oak_log", 1, "", 1, "manual", "", controls);
                json.add("presentation", GSON.toJsonTree(template.presentation(id)));
                json.addProperty("generatedLocate", true);
                json.addProperty("rewardMode", "manual");
                var rewards = new JsonArray();
                rewards.add(reward("item", "minecraft:bread", 4 * settings.multiplier()));
                rewards.add(reward("item", "minecraft:torch", 8 * settings.multiplier()));
                json.add("rewards", rewards);
                String parent = previous.put(route, id);
                if (parent == null && settings.generation() == GenerationMode.BOTH)
                    parent = snapshot.advancements().stream().filter(a -> a.id().getNamespace().equals(structure.id().getNamespace()))
                            .sorted(Comparator.comparingInt(AdvancementData::depth).reversed().thenComparing(a -> a.id().toString()))
                            .map(a -> renamed.get(baseId("auto_adv", a.id()))).filter(java.util.Objects::nonNull).findFirst().orElse(null);
                story.put(id, new Planned(id, structure.id().getNamespace(), "story", "7_exploration", json,
                        parent == null ? List.of() : List.of(parent), 0));
            }
        }
        if (story.isEmpty()) { reporter.send(Component.translatable("command.xero_quest_journal.generate_none"), false); return; }
        for (var task : story.values())
            applyGeneratedRewardOptions(task.json().getAsJsonArray("rewards"), settings);
        // Stage the whole cross-mod graph before publishing, so forward dependencies are never discarded.
        var writes = new LinkedHashMap<Path, String>();
        var expectedExisting = new LinkedHashMap<Path, String>();
        var dependencies = new HashMap<String, List<String>>();
        QuestRepository.all().forEach(q -> dependencies.put(q.id(), q.parents()));
        story.values().forEach(task -> { if (!snapshot.existing().contains(task.id())) dependencies.put(task.id(), task.parentCandidates()); });
        var chapters = new LinkedHashMap<String, Map<String, List<String>>>();
        for (var task : story.values()) {
            Path file = taskFile(task);
            if (snapshot.existing().contains(task.id()) || Files.exists(file)) {
                if (!Files.isSymbolicLink(file) && Files.exists(file)) {
                    String original = Files.readString(file);
                    var existing = JsonParser.parseString(original).getAsJsonObject();
                    boolean changed = updateGeneratedTriggers(existing, task.json());
                    changed |= repairEmptyStoryRewards(existing, task.json());
                    if (changed) {
                        writes.put(file, GSON.toJson(existing));
                        expectedExisting.put(file.toAbsolutePath().normalize(), original);
                    }
                }
                continue;
            }
            for (String parent : task.parentCandidates()) if (parent.equals(task.id()) || dependsOn(parent, task.id(), dependencies))
                throw new IllegalArgumentException("Cyclic story task " + task.id());
            var json = task.json(); json.add("parents", GSON.toJsonTree(task.parentCandidates()));
            json.add("generatedParents", GSON.toJsonTree(task.parentCandidates()));
            json.addProperty("generatedBy", "xero_quest_journal"); json.addProperty("generatedNamespace", task.namespace());
            json.addProperty("generatedMode", "story"); json.add("generatedRewards", json.getAsJsonArray("rewards").deepCopy());
            json.add("generatedTriggers", json.get("triggers").deepCopy());
            GSON.fromJson(json, xtdpotato.xero_quest_journal.core.model.QuestDefinition.class);
            writes.put(file, GSON.toJson(json));
            chapters.computeIfAbsent(task.namespace(), ignored -> new LinkedHashMap<>()).computeIfAbsent(task.chapter(), ignored -> new ArrayList<>()).add(task.id());
        }
        synchronized (QuestRepository.class) {
            for (String namespace : story.values().stream().map(Planned::namespace)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))) {
                String groupId = "auto_" + clean(namespace);
                Path manifest = groupDirectory(namespace).resolve("group_manifest.json");
                if (!Files.exists(manifest) && QuestRepository.groups().stream().noneMatch(g -> g.id().equals(groupId))) {
                    var group = new JsonObject();
                    group.addProperty("id", groupId);
                    group.addProperty("uid", UUID.randomUUID().toString());
                    group.addProperty("name", modName(namespace));
                    group.addProperty("icon", storyGroupIcon(namespace, story.values()));
                    group.addProperty("type", "active");
                    writes.put(manifest, GSON.toJson(group));
                } else if (Files.exists(manifest) && !Files.isSymbolicLink(manifest)) {
                    String original = Files.readString(manifest);
                    var group = JsonParser.parseString(original).getAsJsonObject();
                    if (group.has("icon") && group.get("icon").getAsString().equals("minecraft:book")) {
                        expectedExisting.put(manifest.toAbsolutePath().normalize(), original);
                        group.addProperty("icon", storyGroupIcon(namespace, story.values()));
                        writes.put(manifest, GSON.toJson(group));
                    }
                }
            }
            for (var entry : chapters.entrySet()) {
                String groupId = "auto_" + clean(entry.getKey());
                Path directory = groupDirectory(entry.getKey());
                writes.putAll(stageChapters(directory, groupId, entry.getValue(), expectedExisting));
            }
            if (writes.isEmpty()) {
                reporter.send(Component.translatable("command.xero_quest_journal.generate_none"), false);
                return;
            }
            reporter.send(Component.translatable("command.xero_quest_journal.generate_started",
                    writes.size(), chapters.size()), false);
            var before = QuestAuthoringHistory.capture(writes.keySet());
            var written = new ArrayList<Path>();
            try {
                for (var entry : writes.entrySet()) {
                    Path path = entry.getKey();
                    String expected = expectedExisting.get(path.toAbsolutePath().normalize());
                    boolean changed = Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent())
                            || (expected == null ? Files.exists(path)
                            : !Files.exists(path) || !Files.readString(path).equals(expected));
                    if (changed) throw new java.io.IOException("Task changed during generation");
                    AtomicFiles.write(entry.getKey(), entry.getValue(), before.get(entry.getKey().toAbsolutePath().normalize()) != null);
                    written.add(entry.getKey());
                }
                var validation = QuestRepository.scan();
                if (!validation.problems().isEmpty()) throw new IllegalStateException("Invalid story plan: " + validation.problems());
            } catch (Exception failure) {
                for (Path file : written) if (Files.readString(file).equals(writes.get(file))) {
                    String old = before.get(file.toAbsolutePath().normalize());
                    if (old == null) Files.deleteIfExists(file); else AtomicFiles.write(file, old, true);
                }
                throw failure;
            }
        }
        var scan = QuestRepository.scan();
        boolean loaded = server.submit(() -> {
            if (QuestRepository.revision() != scan.revisionAtStart()) {
                QuestRepository.reload(); return QuestRepository.errors().isEmpty();
            }
            return QuestRepository.apply(scan);
        }).get(2, TimeUnit.MINUTES);
        if (!loaded) throw new IllegalStateException("Generated story catalog failed validation: " + QuestRepository.errors());
        reporter.send(Component.translatable("command.xero_quest_journal.story_generated",
                chapters.values().stream().flatMap(map -> map.values().stream()).mapToInt(List::size).sum()), false);
    }

    private static void collectRelatedItems(String id, Map<String, Planned> items, Set<String> selected) {
        var pending = new java.util.ArrayDeque<String>(); pending.add(id);
        while (!pending.isEmpty()) {
            var current = pending.removeFirst(); var task = items.get(current);
            if (task != null && selected.add(current)) pending.addAll(task.parentCandidates());
            if (selected.size() > 4096) throw new IllegalArgumentException("Too many related recipe tasks; choose a smaller namespace");
        }
    }

    private static JsonArray storyFallbackRewards(Planned task, Settings settings,
                                                   Map<String, JsonArray> recipeRewards) {
        JsonArray result = new JsonArray();
        if (settings.mode() == RewardMode.NONE && !settings.options().bonusExperience()) return result;
        int scale = settings.multiplier();
        if (task.category().equals("items")) {
            if (settings.mode() == RewardMode.OUTPUT) {
                result.add(reward("item", task.json().get("icon").getAsString(), scale));
                return result;
            }
            JsonArray ingredients = recipeRewards.get(task.id());
            if (settings.mode() == RewardMode.RECIPE && ingredients != null && !ingredients.isEmpty()) {
                ingredients.forEach(value -> result.add(value.deepCopy()));
                return result;
            }
        }
        int tier = Math.max(1, Math.min(3, task.spotlightScore() >= 200 ? 3 : 1));
        return rewards(tier, new Settings(settings.filter(), RewardMode.TIER, scale,
                settings.generation(), settings.relatedRecipes(), settings.options()));
    }

    private static void applyGeneratedRewardOptions(JsonArray values, Settings settings) {
        var retained = new JsonArray();
        for (var value : values) {
            if (!value.isJsonObject() || !value.getAsJsonObject().has("type")) {
                retained.add(value.deepCopy());
                continue;
            }
            String type = value.getAsJsonObject().get("type").getAsString();
            if (!settings.options().itemRewards() && Set.of("item", "loot_choice").contains(type)
                    || !settings.options().bonusExperience() && type.equals("experience")) continue;
            retained.add(value.deepCopy());
        }
        for (int index = values.size() - 1; index >= 0; index--) values.remove(index);
        retained.forEach(value -> values.add(value.deepCopy()));
        boolean hasExperience = false;
        for (var value : values) {
            if (value.isJsonObject() && value.getAsJsonObject().has("type")
                    && value.getAsJsonObject().get("type").getAsString().equals("experience")) {
                hasExperience = true;
                break;
            }
        }
        if (!hasExperience) {
            int experience = settings.options().bonusExperience()
                    ? Math.max(1, 20 * settings.multiplier()) : 1;
            values.add(reward("experience", "", experience));
        }
    }

    private static boolean repairEmptyStoryRewards(JsonObject existing, JsonObject planned) {
        if (!existing.has("generatedBy") || !existing.get("generatedBy").getAsString().equals("xero_quest_journal")
                || !existing.has("generatedMode") || !existing.get("generatedMode").getAsString().equals("story"))
            return false;
        JsonElement current = existing.get("rewards");
        JsonElement baseline = existing.get("generatedRewards");
        JsonElement replacement = planned.get("rewards");
        if (current == null || !current.isJsonArray() || !current.getAsJsonArray().isEmpty()
                || baseline != null && (!baseline.isJsonArray() || !baseline.getAsJsonArray().isEmpty())
                || replacement == null || !replacement.isJsonArray() || replacement.getAsJsonArray().isEmpty())
            return false;
        existing.add("rewards", replacement.deepCopy());
        existing.add("generatedRewards", replacement.deepCopy());
        return true;
    }

    private static String storyGroupIcon(String namespace, java.util.Collection<Planned> tasks) {
        String matchingTaskIcon = tasks.stream().filter(task -> task.namespace().equals(namespace))
                .map(task -> task.json().get("icon").getAsString())
                .filter(icon -> namespace.equals(iconNamespace(icon)) && !icon.equals("minecraft:book"))
                .findFirst().orElse("");
        if (!matchingTaskIcon.isBlank()) return matchingTaskIcon;
        if (!namespace.equals("minecraft")) {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id.getNamespace().equals(namespace) && item != net.minecraft.world.item.Items.AIR)
                    return id.toString();
            }
        }
        return tasks.stream().filter(task -> task.namespace().equals(namespace))
                .map(task -> task.json().get("icon").getAsString())
                .filter(icon -> !icon.equals("minecraft:book"))
                .findFirst().orElse("minecraft:compass");
    }

    private static String iconNamespace(String icon) {
        try {
            return ResourceLocation.parse(icon).getNamespace();
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    /** Keep the spotlight bounded and varied rather than duplicating the entire task catalog. */
    private static void selectHotEvents(java.util.Collection<Planned> planned, Map<String, Integer> existing) {
        Map<String, List<Planned>> namespaces = new LinkedHashMap<>();
        for (var task : planned) if (task.spotlightScore() > 0)
            namespaces.computeIfAbsent(task.namespace(), ignored -> new ArrayList<>()).add(task);
        for (var entry : namespaces.entrySet()) {
            int capacity = Math.max(0, 8 - existing.getOrDefault(entry.getKey(), 0));
            var ranked = entry.getValue().stream().sorted(Comparator
                    .comparingInt(Planned::spotlightScore).reversed().thenComparing(Planned::id)).toList();
            var chosen = new LinkedHashSet<Planned>();
            for (String category : List.of("advancements", "items", "combat")) {
                if (chosen.size() >= capacity) break;
                ranked.stream().filter(task -> task.category().equals(category)).findFirst().ifPresent(chosen::add);
            }
            for (var task : ranked) {
                if (chosen.size() >= capacity) break;
                chosen.add(task);
            }
            for (var task : chosen) {
                task.json().add("hotEvent", GSON.toJsonTree(
                        new xtdpotato.xero_quest_journal.core.model.HotEventData(true, "", "", 0, 0, false)));
            }
        }
    }

    /** Repairs older generated documents that accidentally listed themselves as a prerequisite. */
    private static boolean removeSelfPrerequisite(JsonObject data, String taskId) {
        if (data == null || taskId == null || !data.has("parents") || !data.get("parents").isJsonArray())
            return false;
        JsonArray parents = data.getAsJsonArray("parents");
        JsonArray filtered = new JsonArray();
        boolean changed = false;
        for (JsonElement parent : parents) {
            if (parent.isJsonPrimitive() && taskId.equals(parent.getAsString())) {
                changed = true;
            } else {
                filtered.add(parent);
            }
        }
        if (changed) data.add("parents", filtered);
        return changed;
    }

    private static void markGeneratedRewards(JsonObject quest, Settings settings) {
        quest.addProperty("generatedRewardMode", settings.mode().name().toLowerCase(Locale.ROOT));
        quest.addProperty("generatedRewardMultiplier", settings.multiplier());
        quest.addProperty("generatedTaskMultiplier", settings.options().taskMultiplier());
        quest.addProperty("generatedBonusExperience", settings.options().bonusExperience());
        quest.addProperty("generatedItemRewards", settings.options().itemRewards());
        quest.add("generatedRewards", quest.getAsJsonArray("rewards").deepCopy());
        if (quest.has("triggers")) quest.add("generatedTriggers", quest.get("triggers").deepCopy());
    }

    private static boolean updateGeneratedTriggers(JsonObject existing, JsonObject planned) {
        JsonElement triggers = existing.get("triggers");
        JsonElement baseline = existing.get("generatedTriggers");
        JsonElement replacement = planned.get("triggers");
        if (triggers == null || baseline == null || !triggers.equals(baseline)
                || replacement == null || triggers.equals(replacement)) return false;
        existing.add("triggers", replacement.deepCopy());
        existing.add("generatedTriggers", replacement.deepCopy());
        return true;
    }

    private static boolean updateGeneratedParents(JsonObject data, Planned task,
                                                   Map<String, List<String>> dependencies, Set<String> existing) {
        JsonArray original = data.has("parents") ? data.getAsJsonArray("parents") : new JsonArray();
        boolean managed = data.has("generatedParents") ? original.equals(data.get("generatedParents"))
                : original.isEmpty() && (!data.has("defaultUnlocked") || data.get("defaultUnlocked").getAsBoolean());
        boolean repairedSelf = removeSelfPrerequisite(data, task.id());
        boolean changed = repairedSelf;
        JsonArray parents = data.has("parents") ? data.getAsJsonArray("parents") : original;
        if (managed || changed && parents.isEmpty()) {
            JsonArray next = new JsonArray();
            for (String candidate : task.parentCandidates())
                if (!candidate.equals(task.id()) && existing.contains(candidate)
                        && !dependsOn(candidate, task.id(), dependencies)) next.add(candidate);
            if (!parents.equals(next)) { data.add("parents", next); changed = true; }
            if (!java.util.Objects.equals(data.get("generatedParents"), next)) {
                data.add("generatedParents", next.deepCopy()); changed = true;
            }
            if (repairedSelf && next.isEmpty() && data.has("defaultUnlocked")
                    && !data.get("defaultUnlocked").getAsBoolean()) {
                data.addProperty("defaultUnlocked", true); changed = true;
            }
            parents = next;
        }
        List<String> actual = new ArrayList<>();
        parents.forEach(parent -> actual.add(parent.getAsString()));
        dependencies.put(task.id(), List.copyOf(actual));
        return changed;
    }

    private static boolean dependsOn(String start, String target, Map<String, List<String>> dependencies) {
        Set<String> visited = new HashSet<>();
        var pending = new java.util.ArrayDeque<String>(); pending.add(start);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (id.equals(target)) return true;
            if (visited.add(id)) pending.addAll(dependencies.getOrDefault(id, List.of()));
        }
        return false;
    }

    private static boolean updateGeneratedRewards(JsonObject existing, JsonObject planned) {
        JsonElement current = existing.get("rewards");
        if (current == null || !current.isJsonArray()) return false;
        boolean managed = existing.has("generatedRewards")
                ? current.equals(existing.get("generatedRewards")) : legacyTierRewards(current);
        if (!managed) return false;
        boolean changed = false;
        for (String key : List.of("rewards", "generatedRewards", "generatedRewardMode",
                "generatedRewardMultiplier", "generatedTaskMultiplier", "generatedBonusExperience",
                "generatedItemRewards")) {
            if (!java.util.Objects.equals(existing.get(key), planned.get(key))) {
                existing.add(key, planned.get(key).deepCopy());
                changed = true;
            }
        }
        return changed;
    }

    /** Older files have no baseline; recognize only the exact historical difficulty defaults. */
    private static boolean legacyTierRewards(JsonElement current) {
        if (!current.isJsonArray() || current.getAsJsonArray().isEmpty()) return false;
        var first = current.getAsJsonArray().get(0);
        if (!first.isJsonObject() || !first.getAsJsonObject().has("type")
                || !first.getAsJsonObject().get("type").getAsString().equals("experience")) return false;
        for (int tier = 1; tier <= 3; tier++)
            for (int scale = 1; scale <= 64; scale++)
                if (current.equals(rewards(tier, new Settings("", RewardMode.TIER, scale)))) return true;
        return false;
    }

    /** Existing group folder with the generated group ID, otherwise {@code groups/auto_<namespace>}. */
    private static Path groupDirectory(String namespace) {
        String groupId = "auto_" + clean(namespace);
        return QuestRepository.groups().stream().filter(group -> group.id().equals(groupId))
                .map(QuestRepository.Group::directory).findFirst()
                .orElse(QuestRepository.root().resolve(groupId));
    }

    private static Path taskFile(Planned quest) {
        return groupDirectory(quest.namespace()).resolve(quest.category()).resolve(quest.id() + ".json");
    }

    private static void planAdvancements(List<AdvancementData> advancements, Settings settings, Set<String> existing,
                                         Map<String, Planned> planned) {
        for (var data : advancements) {
            if (!matches(settings.filter(), data.id())) continue;
            String id = baseId("auto_adv", data.id());
            if (existing.contains(id) || planned.containsKey(id)) continue;
            JsonObject quest = quest(id, data.title(), data.description(),
                    data.id().getNamespace().equals("minecraft") ? "main" : "world", data.icon());
            quest.add("triggers", trigger("advancement", data.id().toString(), 1));
            JsonArray generatedRewards = rewards(data.tier(), settings);
            applyGeneratedRewardOptions(generatedRewards, settings);
            quest.add("rewards", generatedRewards);
            markGeneratedRewards(quest, settings);
            quest.addProperty("descriptionKey", "generate.xero_quest_journal.description.advancement");
            quest.addProperty("description", Component.translatable(
                    "generate.xero_quest_journal.description.advancement").getString());
            List<String> parents = data.parent() == null ? List.of() : List.of(baseId("auto_adv", data.parent()));
            planned.put(id, new Planned(id, data.id().getNamespace(), "advancements", "1_advancements",
                    quest, parents, data.tier() >= 2 ? 100 * data.tier() + Math.min(30, data.depth())
                            : data.depth() == 0 ? 70 : 0));
        }
    }

    private static int advancementDepth(ResourceLocation id, Map<ResourceLocation, AdvancementHolder> byId) {
        int depth = 0;
        ResourceLocation current = id;
        while (depth < 64) {
            var holder = byId.get(current);
            if (holder == null || holder.value().parent().isEmpty()) break;
            current = holder.value().parent().get();
            depth++;
        }
        return depth;
    }

    /** Every recipe as "output <- for each ingredient: any of these items" (copied on the server thread). */
    private static Map<Item, List<List<List<Item>>>> recipes(MinecraftServer server) {
        Map<Item, List<List<List<Item>>>> recipes = new HashMap<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes().stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList()) {
            try {
                ItemStack result = holder.value().getResultItem(server.registryAccess());
                if (result.isEmpty()) continue;
                List<List<Item>> ingredients = new ArrayList<>();
                for (Ingredient ingredient : holder.value().getIngredients()) {
                    if (ingredient.isEmpty()) continue;
                    List<Item> alternatives = new ArrayList<>();
                    for (ItemStack stack : ingredient.getItems())
                        if (!stack.isEmpty() && !alternatives.contains(stack.getItem())) alternatives.add(stack.getItem());
                    if (!alternatives.isEmpty()) ingredients.add(alternatives);
                }
                // Repairs, dyeing and similar self-referencing recipes say nothing about progression.
                if (ingredients.isEmpty() || ingredients.stream().anyMatch(alts -> alts.contains(result.getItem())))
                    continue;
                recipes.computeIfAbsent(result.getItem(), ignored -> new ArrayList<>()).add(ingredients);
            } catch (RuntimeException unsupported) {
                XeroQuestJournal.LOGGER.debug("Skipping recipe {} during task generation", holder.id(), unsupported);
            }
        }
        return recipes;
    }

    private static void planItems(Map<Item, List<List<List<Item>>>> recipes, Set<Item> lootItems,
                                  Settings settings, FeatureFlagSet features,
                                  Set<String> existing, Map<String, Planned> planned) {
        // Seed actual survival sources, not every non-recipe registry entry. Otherwise a
        // recipe requiring a creative-only ingredient (or an unseeded cycle) looks obtainable.
        Map<Item, Integer> depth = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            depth.put(item, obtainable(item, key, features)
                    && (lootItems.contains(item) && !recipes.containsKey(item)
                    || OPERATION_ITEMS.contains(key.toString())) ? 0 : INFINITE);
        }
        resolveRecipeDepths(recipes, depth);
        // Block self-drops must not erase a reachable crafting chain. Only seed remaining loot
        // after resolving real raw-material recipes, then propagate those additional sources.
        for (Item item : lootItems) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (depth.getOrDefault(item, INFINITE) >= INFINITE && obtainable(item, key, features))
                depth.put(item, 0);
        }
        resolveRecipeDepths(recipes, depth);
        Map<Item, String> itemQuest = new HashMap<>();
        List<Item> candidates = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (!obtainable(item, key, features)) continue;
            if (depth.getOrDefault(item, INFINITE) >= INFINITE) continue;
            itemQuest.put(item, baseId("auto_item", key));
            candidates.add(item);
        }
        candidates.sort(Comparator.comparingInt((Item item) -> effectiveDepth(depth.getOrDefault(item, 0)))
                .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        for (Item item : candidates) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (!matches(settings.filter(), key)) continue;
            String id = itemQuest.get(item);
            if (existing.contains(id) || planned.containsKey(id)) continue;
            int level = effectiveDepth(depth.getOrDefault(item, 0));
            int rarityTier = switch (new ItemStack(item).getRarity()) {
                case UNCOMMON -> 2;
                case RARE, EPIC -> 3;
                default -> 1;
            };
            int tier = Math.max(rarityTier, level >= 5 ? 3 : level >= 3 ? 2 : 1);
            String chapter = level == 0 ? "2_gather" : level <= 2 ? "3_craft_basic"
                    : level <= 4 ? "4_craft_advanced" : "5_craft_expert";
            boolean craftable = recipes.containsKey(item);
            String titleKey = "generate.xero_quest_journal.title." + (craftable ? "craft" : "collect");
            String descriptionKey = "generate.xero_quest_journal.description."
                    + (craftable ? "craft" : lootItems.contains(item) ? "loot" : "operation");
            JsonObject quest = quest(id, Component.translatable(titleKey, item.getDescription()),
                    Component.translatable(descriptionKey), "world", key.toString());
            quest.addProperty("titleKey", titleKey);
            quest.add("triggers", trigger("item_hold", key.toString(), settings.taskCount(1)));
            JsonArray itemRewards = settings.mode() == RewardMode.OUTPUT
                    ? outputRewards(key.toString(), settings.multiplier())
                    : settings.mode() == RewardMode.RECIPE
                    ? recipeRewards(item, recipes, depth, itemQuest, settings.multiplier()) : null;
            JsonArray generatedRewards = itemRewards != null ? itemRewards : rewards(tier, settings);
            applyGeneratedRewardOptions(generatedRewards, settings);
            quest.add("rewards", generatedRewards);
            markGeneratedRewards(quest, settings);
            planned.put(id, new Planned(id, key.getNamespace(), "items", chapter, quest,
                    itemParents(item, recipes, depth, itemQuest),
                    rarityTier == 3 ? 200 + Math.min(30, level) : level >= 5 ? 150 + Math.min(30, level) : 0));
        }
    }

    private static void resolveRecipeDepths(Map<Item, List<List<List<Item>>>> recipes, Map<Item, Integer> depth) {
        for (int pass = 0; pass < 64; pass++) {
            boolean changed = false;
            for (var entry : recipes.entrySet()) {
                int current = depth.getOrDefault(entry.getKey(), INFINITE);
                for (var recipe : entry.getValue()) {
                    int cost = cost(recipe, depth);
                    if (cost < INFINITE && cost + 1 < current) {
                        current = cost + 1;
                        changed = true;
                    }
                }
                depth.put(entry.getKey(), current);
            }
            if (!changed) break;
        }
    }

    private static void planTacticalItems(List<TacticalTask> tasks, List<TacticalRecipe> recipes,
                                          Settings settings, Set<String> existing,
                                          Map<String, Planned> planned) {
        Map<String, TacticalRecipe> byIdentity = new HashMap<>();
        recipes.forEach(recipe -> byIdentity.putIfAbsent(recipe.identity(), recipe));
        for (TacticalTask task : tasks) {
            if (!settings.filter().isBlank() && !task.namespace().equals(settings.filter())) continue;
            if (existing.contains(task.id()) || planned.containsKey(task.id())) continue;
            JsonObject quest = quest(task.id(), Component.literal(task.title()),
                    Component.translatable("generate.xero_quest_journal.description.special_item"),
                    "world", task.target());
            if (!task.titleKey().isBlank()) quest.addProperty("titleKey", task.titleKey());
            quest.add("triggers", trigger("item_acquire", task.target(), settings.taskCount(1)));
            JsonArray itemRewards = settings.mode() == RewardMode.OUTPUT
                    ? outputRewards(task.target(), settings.multiplier())
                    : settings.mode() == RewardMode.RECIPE
                    ? tacticalRecipeRewards(byIdentity.get(task.identity()), settings.multiplier()) : null;
            if (itemRewards == null && settings.mode() == RewardMode.RECIPE)
                itemRewards = outputRewards(task.target(), settings.multiplier());
            JsonArray generatedRewards = itemRewards == null ? rewards(task.tier(), settings) : itemRewards;
            applyGeneratedRewardOptions(generatedRewards, settings);
            quest.add("rewards", generatedRewards);
            markGeneratedRewards(quest, settings);
            planned.put(task.id(), new Planned(task.id(), task.namespace(), "items",
                    tacticalChapter(task.kind()), quest, List.of(), task.tier() >= 3 ? 220 : 0));
        }
    }

    private static JsonArray tacticalRecipeRewards(TacticalRecipe recipe, int multiplier) {
        if (recipe == null) return null;
        Map<String, Integer> combined = new LinkedHashMap<>();
        for (TacticalMaterial material : recipe.materials())
            combined.merge(material.target(), material.count(), Integer::sum);
        JsonArray result = new JsonArray();
        combined.entrySet().stream().limit(32).forEach(entry -> result.add(reward("item", entry.getKey(),
                Math.min(1_000_000, entry.getValue() * multiplier))));
        return result.isEmpty() ? null : result;
    }

    private static JsonArray outputRewards(String target, int multiplier) {
        if (target == null || target.isBlank()) return null;
        JsonArray result = new JsonArray();
        result.add(reward("item", target, Math.max(1, Math.min(1_000_000, multiplier))));
        return result;
    }

    private static String tacticalChapter(String kind) {
        return switch (kind) {
            case "ammo" -> "2_gather";
            case "gun" -> "3_craft_basic";
            case "attachment", "consumable" -> "4_craft_advanced";
            default -> "6_combat";
        };
    }

    /** The recipe that gives an item its crafting depth (its cheapest one), or null for gathered items. */
    private static List<List<Item>> bestRecipe(Item item, Map<Item, List<List<List<Item>>>> recipes,
                                               Map<Item, Integer> depth) {
        int own = depth.getOrDefault(item, 0);
        if (own <= 0 || own >= INFINITE) return null;
        for (var recipe : recipes.getOrDefault(item, List.of()))
            if (cost(recipe, depth) + 1 == own) return recipe;
        return null;
    }

    /** The cheapest alternative of one ingredient slot, preferring items that have their own task. */
    private static Item chooseAlternative(List<Item> alternatives, Map<Item, Integer> depth,
                                          Map<Item, String> itemQuest) {
        Item chosen = null;
        int chosenDepth = INFINITE;
        for (Item alternative : alternatives) {
            int value = depth.getOrDefault(alternative, INFINITE);
            boolean preferred = value < chosenDepth || value == chosenDepth && chosen != null
                    && (itemQuest.containsKey(alternative) && !itemQuest.containsKey(chosen)
                    || itemQuest.containsKey(alternative) == itemQuest.containsKey(chosen)
                    && BuiltInRegistries.ITEM.getKey(alternative).toString()
                    .compareTo(BuiltInRegistries.ITEM.getKey(chosen).toString()) < 0);
            if (preferred) {
                chosen = alternative;
                chosenDepth = value;
            }
        }
        return chosen;
    }

    /**
     * "Recipe" rewards: the materials of one craft of the item (each ingredient slot counts once),
     * times the multiplier. Loot availability affects unlock depth, not reward recipe selection.
     */
    private static JsonArray recipeRewards(Item item, Map<Item, List<List<List<Item>>>> recipes,
                                           Map<Item, Integer> depth, Map<Item, String> itemQuest, int multiplier) {
        var recipe = recipes.getOrDefault(item, List.of()).stream()
                .filter(candidate -> !candidate.isEmpty() && cost(candidate, depth) < INFINITE
                        && candidate.stream().noneMatch(alternatives -> alternatives.contains(item)))
                .min(Comparator.comparingInt(candidate -> cost(candidate, depth))).orElse(null);
        String target = BuiltInRegistries.ITEM.getKey(item).toString();
        if (recipe == null) return outputRewards(target, multiplier);
        Map<Item, Integer> amounts = new LinkedHashMap<>();
        for (var alternatives : recipe) {
            Item material = chooseAlternative(alternatives, depth, itemQuest);
            if (material != null && itemQuest.containsKey(material)) amounts.merge(material, 1, Integer::sum);
        }
        if (amounts.isEmpty()) return outputRewards(target, multiplier);
        JsonArray rewards = new JsonArray();
        amounts.entrySet().stream().limit(32).forEach(entry -> rewards.add(reward("item",
                BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(),
                Math.min(1_000_000, entry.getValue() * multiplier))));
        return rewards;
    }

    private static int effectiveDepth(int depth) {
        return Math.max(0, depth);
    }

    private static int cost(List<List<Item>> recipe, Map<Item, Integer> depth) {
        int worst = 0;
        for (var alternatives : recipe) {
            int best = INFINITE;
            for (Item alternative : alternatives) best = Math.min(best, depth.getOrDefault(alternative, INFINITE));
            if (best >= INFINITE) return INFINITE;
            worst = Math.max(worst, best);
        }
        return worst;
    }

    /** Prerequisites: the cheapest ingredients of the cheapest recipe (strictly shallower, so acyclic). */
    private static List<String> itemParents(Item item, Map<Item, List<List<List<Item>>>> recipes,
                                            Map<Item, Integer> depth, Map<Item, String> itemQuest) {
        int own = depth.getOrDefault(item, 0);
        var best = bestRecipe(item, recipes, depth);
        if (best == null) return List.of();
        Set<String> parents = new LinkedHashSet<>();
        for (var alternatives : best) {
            Item chosen = chooseAlternative(alternatives, depth, itemQuest);
            if (chosen != null && depth.getOrDefault(chosen, 0) < own && itemQuest.containsKey(chosen))
                parents.add(itemQuest.get(chosen));
            if (parents.size() >= 128) break;
        }
        return List.copyOf(parents);
    }

    private static boolean obtainable(Item item, ResourceLocation key, FeatureFlagSet features) {
        String id = key.toString();
        return !TECHNICAL_ITEMS.contains(id) && !(item instanceof SpawnEggItem) && !key.getPath().endsWith("_spawn_egg")
                && !xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.dynamicItem(item)
                && item.isEnabled(features) && !new ItemStack(item).isEmpty();
    }

    private static void planKills(Settings settings, FeatureFlagSet features, Set<String> existing,
                                  Map<String, Planned> planned) {
        List<EntityType<?>> hostile = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (type.getCategory() != MobCategory.MONSTER || !type.isEnabled(features)
                    || EXCLUDED_MOBS.contains(key.toString())) continue;
            hostile.add(type);
        }
        hostile.sort(Comparator.comparingDouble((EntityType<?> type) -> maxHealth(type))
                .thenComparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
        for (EntityType<?> type : hostile) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!matches(settings.filter(), key)) continue;
            String id = baseId("auto_kill", key);
            if (existing.contains(id) || planned.containsKey(id)) continue;
            double health = maxHealth(type);
            int tier = health >= 100 ? 3 : health >= 30 ? 2 : 1;
            int count = settings.taskCount(tier == 3 ? 1 : tier == 2 ? 3 : 5);
            SpawnEggItem egg = SpawnEggItem.byId(type);
            String icon = egg != null ? BuiltInRegistries.ITEM.getKey(egg).toString() : "minecraft:iron_sword";
            String titleKey = "generate.xero_quest_journal.title.defeat";
            JsonObject quest = quest(id, Component.translatable(titleKey, type.getDescription()),
                    Component.translatable("generate.xero_quest_journal.description."
                            + (tier == 3 ? "boss" : "combat")), "world", icon);
            quest.addProperty("titleKey", titleKey);
            quest.add("triggers", trigger("kill", key.toString(), count));
            JsonArray generatedRewards = rewards(tier, settings);
            applyGeneratedRewardOptions(generatedRewards, settings);
            quest.add("rewards", generatedRewards);
            markGeneratedRewards(quest, settings);
            planned.put(id, new Planned(id, key.getNamespace(), "combat", "6_combat", quest, List.of(),
                    tier == 3 ? 400 + (int) Math.min(100, health / 10) : 0));
        }
    }

    @SuppressWarnings("unchecked")
    private static double maxHealth(EntityType<?> type) {
        try {
            if (!DefaultAttributes.hasSupplier(type)) return 20;
            return DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type)
                    .getBaseValue(Attributes.MAX_HEALTH);
        } catch (RuntimeException unknown) {
            return 20;
        }
    }

    private static JsonObject quest(String id, Component title, Component description, String type, String icon) {
        JsonObject quest = new JsonObject();
        quest.addProperty("id", id);
        quest.addProperty("uid", UUID.randomUUID().toString());
        quest.addProperty("title", title.getString());
        String titleKey = translationKey(title);
        if (!titleKey.isBlank()) quest.addProperty("titleKey", titleKey);
        quest.addProperty("description", description.getString());
        String descriptionKey = translationKey(description);
        if (!descriptionKey.isBlank()) quest.addProperty("descriptionKey", descriptionKey);
        quest.addProperty("type", type);
        quest.addProperty("icon", icon);
        quest.addProperty("trackable", true);
        quest.addProperty("defaultUnlocked", true);
        quest.addProperty("triggersEnabled", true);
        quest.addProperty("rewardMode", "default");
        return quest;
    }

    /** Key of an argument-free translatable component, so clients show their own language. */
    private static String translationKey(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable
                && translatable.getArgs().length == 0 && component.getSiblings().isEmpty())
            return translatable.getKey();
        return "";
    }

    private static JsonArray trigger(String type, String target, int count) {
        JsonObject trigger = new JsonObject();
        trigger.addProperty("type", type);
        trigger.addProperty("item", target);
        trigger.addProperty("count", count);
        JsonArray triggers = new JsonArray();
        triggers.add(trigger);
        return triggers;
    }

    /**
     * Difficulty-scaled rewards: experience at every tier, emeralds and a diamond for harder tasks,
     * all times the multiplier. {@code NONE} gives nothing.
     */
    private static JsonArray rewards(int tier, Settings settings) {
        JsonArray rewards = new JsonArray();
        if (settings.mode() == RewardMode.NONE || settings.mode() == RewardMode.OUTPUT) return rewards;
        int scale = settings.multiplier();
        if (settings.options().bonusExperience())
            rewards.add(reward("experience", "", Math.max(1,
                    (tier == 3 ? 150 : tier == 2 ? 60 : 20) * scale)));
        if (tier >= 2) rewards.add(reward("item", "minecraft:emerald", (tier == 3 ? 4 : 2) * scale));
        if (tier == 3) rewards.add(reward("item", "minecraft:diamond", scale));
        return rewards;
    }

    private static JsonObject reward(String type, String item, int count) {
        JsonObject reward = new JsonObject();
        reward.addProperty("type", type);
        reward.addProperty("item", item);
        reward.addProperty("count", count);
        return reward;
    }

    private static void writeChapters(Path directory, String groupId, Map<String, List<String>> additions)
            throws Exception {
        for (var entry : stageChapters(directory, groupId, additions).entrySet())
            AtomicFiles.write(entry.getKey(), entry.getValue(), true);
    }
    private static Map<Path, String> stageChapters(Path directory, String groupId, Map<String, List<String>> additions) throws Exception {
        return stageChapters(directory, groupId, additions, null);
    }

    private static Map<Path, String> stageChapters(Path directory, String groupId,
                                                   Map<String, List<String>> additions,
                                                   Map<Path, String> expectedExisting) throws Exception {
        var writes = new LinkedHashMap<Path, String>();
        for (String chapterKey : CHAPTERS) {
            List<String> added = additions.get(chapterKey);
            if (added == null || added.isEmpty()) continue;
            String chapterId = groupId + "_" + chapterKey;
            Path file = QuestRepository.chapters().stream().filter(chapter -> chapter.id().equals(chapterId))
                    .map(QuestRepository.Chapter::file).findFirst()
                    .orElse(directory.resolve(chapterId + ".chapter.json"));
            JsonObject chapter;
            if (Files.exists(file)) {
                String original = Files.readString(file);
                if (expectedExisting != null) expectedExisting.put(file.toAbsolutePath().normalize(), original);
                chapter = JsonParser.parseString(original).getAsJsonObject();
            } else {
                chapter = new JsonObject();
                chapter.addProperty("id", chapterId);
                chapter.addProperty("uid", UUID.randomUUID().toString());
                chapter.addProperty("name", Component.translatable("generate.xero_quest_journal.chapter."
                        + chapterKey.substring(2)).getString());
                chapter.addProperty("description", "");
                chapter.addProperty("group", groupId);
                chapter.add("quests", new JsonArray());
            }
            JsonArray quests = chapter.has("quests") ? chapter.getAsJsonArray("quests") : new JsonArray();
            Set<String> present = new HashSet<>();
            quests.forEach(value -> present.add(value.getAsString()));
            for (String id : added) if (present.add(id)) quests.add(id);
            chapter.add("quests", quests);
            writes.put(file, GSON.toJson(chapter));
        }
        return writes;
    }

    private static String groupIcon(List<Planned> quests) {
        return quests.stream().filter(quest -> quest.category().equals("items")).findFirst()
                .or(() -> quests.stream().findFirst())
                .map(quest -> quest.json().get("icon").getAsString())
                .filter(icon -> !icon.equals("minecraft:book")).orElse("minecraft:compass");
    }

    private static String modName(String namespace) {
        if (namespace.equals("minecraft")) return "Minecraft";
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName()).orElse(namespace);
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "minecraft:book"
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean matches(String filter, ResourceLocation key) {
        return filter.isBlank() || key.getNamespace().equals(filter);
    }

    private static String clean(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    /** Stable task ID for a registry entry (within the 64-character task ID limit). */
    static String baseId(String prefix, ResourceLocation key) {
        String raw = prefix + "_" + key.getNamespace() + "_" + key.getPath();
        String id = clean(raw);
        if (id.length() <= 64) return id;
        String hash = Integer.toHexString(raw.hashCode());
        return id.substring(0, 63 - hash.length()) + "_" + hash;
    }
}
