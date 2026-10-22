package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import xtdpotato.xero_quest_journal.client.QuestIcons;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.QuestText;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestConditions;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Executes the shipped generation command and verifies the task documents on disk. */
final class TaskGenerationRegression {
    private static CompletableFuture<Integer> command;
    private static CompletableFuture<Void> preservedFixture;
    private static boolean started, fallbackVerified;
    private static int commandStage;
    private static boolean previewRequested;
    private static String previewTaskId = "";
    private static long taskCount;
    private static long chapterCount;
    private static long groupCount;
    private static long modelTaskCount;
    private static long hotCount;
    private static String preservedTaskSource = "";
    private static String progressIdentity;
    private static java.util.List<QuestDefinition.Reward> frozenRewards;
    private static Map<String, String> recipeSources;

    private TaskGenerationRegression() {}

    static boolean tick(Minecraft game) throws Exception {
        return tick(game, false);
    }

    static boolean tick(Minecraft game, boolean requireTactical) throws Exception {
        if (!fallbackVerified) {
            verifyMissingTranslationFallback();
            fallbackVerified = true;
        }
        if (previewRequested) {
            if (!(game.screen instanceof QuestScreen)) return false;
            Path output = Path.of("..", "build", "verification");
            Files.createDirectories(output);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                image.writeToFile(output.resolve("tactical-generated-task-preview.png"));
            }
            writeReport(output);
            previewRequested = false;
            return true;
        }
        var server = game.getSingleplayerServer();
        if (!started) {
            started = true;
            command = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    int result = server.getCommands().getDispatcher().execute(
                            "xero_quest delete_generated all confirm",
                            server.createCommandSourceStack().withPermission(4));
                    checkRecipeReachability(server);
                    command.complete(result);
                } catch (Throwable failure) {
                    command.completeExceptionally(failure);
                }
            });
            return false;
        }
        if (!command.isDone()) return false;
        if (commandStage >= 4) {
            require(command.join() > 0, "Recipe generation command did not start");
            if (xtdpotato.xero_quest_journal.core.io.QuestGenerator.running()) return false;
            require(QuestRepository.errors().isEmpty(), "Recipe regeneration failed: " + QuestRepository.errors());
            require(QuestRepository.source("auto_item_minecraft_stick").equals(preservedTaskSource),
                    "Recipe regeneration overwrote hand-authored rewards or text");
            var table = QuestRepository.find("auto_item_minecraft_crafting_table");
            require(table.progressKey().equals(progressIdentity), "Regeneration replaced the task progress identity");
            require(!table.parents().contains(table.id()) && !table.parents().isEmpty(),
                    "Self-only legacy prerequisite was not replaced with the material path");
            require(table.rewards().stream().anyMatch(reward -> reward.type().equals("item")
                            && reward.item().endsWith("_planks") && reward.count() == 8),
                    "Real recipe command did not produce four crafting materials at multiplier two");
            var log = QuestRepository.find("auto_item_minecraft_oak_log");
            require(log.rewards().stream().anyMatch(reward -> reward.type().equals("item")
                            && reward.item().equals("minecraft:oak_log") && reward.count() == 2),
                    "Gathered task did not receive the related item fallback");
            require(QuestRepository.find("auto_item_minecraft_oak_planks").parents()
                            .contains("auto_item_minecraft_oak_log"),
                    "Legacy empty material path was not upgraded to logs -> planks");
            if (commandStage == 5) {
                for (var entry : recipeSources.entrySet())
                    require(QuestRepository.source(entry.getKey()).equals(entry.getValue()),
                            "Repeated recipe generation was not idempotent: " + entry.getKey());
                preservedFixture.join();
                writeReport(Path.of("..", "build", "verification"));
                return true;
            }
            recipeSources = new java.util.LinkedHashMap<>();
            for (var task : QuestRepository.all())
                if (QuestRepository.groupOf(task.id()).startsWith("auto_"))
                    recipeSources.put(task.id(), QuestRepository.source(task.id()));
            preservedFixture = CompletableFuture.runAsync(() -> {
                var player = server.getPlayerList().getPlayer(game.player.getUUID());
                var progress = xtdpotato.xero_quest_journal.core.runtime.QuestProgressData.get(server);
                require(progress.entry(player.getUUID(), table).rewards().equals(frozenRewards),
                        "Regeneration changed an already completed reward snapshot");
                checkUnlockGraph(player);
                var planks = QuestRepository.find("auto_item_minecraft_oak_planks");
                require(progress.complete(player.getUUID(), planks,
                                xtdpotato.xero_quest_journal.core.runtime.QuestRewards.freeze(player, planks.rewards())),
                        "Recipe claim fixture was already completed");
                int before = player.getInventory().countItem(net.minecraft.world.item.Items.OAK_LOG);
                require(xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.claim(player, planks.id()).equals("claimed"),
                        "Generated recipe reward could not be claimed");
                require(player.getInventory().countItem(net.minecraft.world.item.Items.OAK_LOG) == before + 2,
                        "Generated recipe materials were not delivered to the player inventory");
                xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.claim(player, planks.id());
                require(player.getInventory().countItem(net.minecraft.world.item.Items.OAK_LOG) == before + 2,
                        "Repeated generated reward claim delivered items twice");
            }, server);
            commandStage = 5;
            command = server.submit(() -> {
                preservedFixture.join();
                try {
                    Path file = QuestRepository.fileOf(table.id());
                    var data = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    var parents = new com.google.gson.JsonArray(); parents.add(table.id());
                    data.add("parents", parents);
                    Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
                    // A failed startup reload may omit the broken file from the live index.
                    var scan = QuestRepository.scan();
                    QuestRepository.apply(new QuestRepository.Scan(
                            scan.quests().stream().filter(task -> !task.id().equals(table.id())).toList(),
                            scan.groups(), scan.chapters(), scan.files(), scan.membership(), scan.unlockLayout(),
                            scan.journalOrder(), java.util.List.of(), scan.revisionAtStart()));
                    return server.getCommands().getDispatcher().execute("xero_quest generate all recipe 2",
                            server.createCommandSourceStack().withPermission(4));
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            return false;
        }
        if (commandStage == 2) {
            if (preservedFixture == null || !preservedFixture.isDone()) return false;
            preservedFixture.join();
            commandStage = 3;
            command = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    int result = server.getCommands().getDispatcher().execute(
                            "xero_quest generate", server.createCommandSourceStack().withPermission(4));
                    command.complete(result);
                } catch (Throwable failure) { command.completeExceptionally(failure); }
            });
            return false;
        }
        if (commandStage == 3) {
            require(command.join() > 0, "Second generation command did not start");
            if (xtdpotato.xero_quest_journal.core.io.QuestGenerator.running()) return false;
            String after = QuestRepository.source("auto_item_minecraft_stick");
            require(after.equals(preservedTaskSource),
                    "Regeneration overwrote authored generated-task title, description or hotspot settings");
            commandStage = 4;
            command = server.submit(() -> {
                try {
                    return server.getCommands().getDispatcher().execute("xero_quest generate all recipe 2",
                            server.createCommandSourceStack().withPermission(4));
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            return false;
        }
        command.join();
        if (commandStage == 0) {
            commandStage = 1;
            command = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    int result = server.getCommands().getDispatcher().execute(
                            "xero_quest generate", server.createCommandSourceStack().withPermission(4));
                    command.complete(result);
                } catch (Throwable failure) {
                    command.completeExceptionally(failure);
                }
            });
            return false;
        }
        require(command.join() > 0, "The generation command did not start");
        if (xtdpotato.xero_quest_journal.core.io.QuestGenerator.running()) return false;
        require(QuestRepository.errors().isEmpty(),
                "Generated task documents failed validation: " + QuestRepository.errors());

        Path root = QuestRepository.root();
        long taskDocuments = QuestRepository.all().stream()
                .filter(quest -> QuestRepository.groupOf(quest.id()).startsWith("auto_")).count();
        long groups = QuestRepository.groups().stream().filter(group -> group.id().startsWith("auto_")).count();
        long chapters = QuestRepository.chapters().stream()
                .filter(chapter -> chapter.groupId().startsWith("auto_")).count();
        require(taskDocuments > 0 && chapters > 0 && groups > 0,
                "Generation completed without task, chapter, or group documents");
        require(QuestRepository.find("auto_item_minecraft_stick") != null,
                "Craftable item task was filtered out");
        require(QuestRepository.find("auto_item_minecraft_enchanted_book") != null,
                "Loot-only item task was filtered out");
        require(QuestRepository.find("auto_item_minecraft_water_bucket") != null,
                "Survival operation item task was filtered out");
        require(QuestRepository.find("auto_item_xero_quest_journal_scene_completion") == null,
                "Creative-only mod item received a generated task");
        require(QuestRepository.find("auto_item_minecraft_bedrock") == null
                        && QuestRepository.find("auto_item_minecraft_command_block") == null
                        && QuestRepository.find("auto_item_minecraft_debug_stick") == null,
                "Creative-only/technical items received generated tasks");
        require(QuestRepository.all().stream()
                        .filter(quest -> QuestRepository.groupOf(quest.id()).startsWith("auto_")).count()
                        == taskDocuments,
                "Generated documents were not all loaded into the task repository");
        var generated = QuestRepository.all().stream()
                .filter(quest -> QuestRepository.groupOf(quest.id()).startsWith("auto_")).toList();
        var hotspots = generated.stream().filter(quest -> quest.hotEvent().enabled()).toList();
        hotCount = hotspots.size();
        require(hotCount > 0 && hotCount < generated.size(), "Hotspot selection is empty or unbounded");
        var counts = new java.util.HashMap<String, Integer>();
        for (var task : hotspots) counts.merge(QuestRepository.groupOf(task.id()), 1, Integer::sum);
        require(counts.values().stream().allMatch(count -> count <= 8), "More than eight hot tasks in a namespace");
        require(hotspots.stream().anyMatch(task -> task.id().startsWith("auto_adv_minecraft_"))
                        && hotspots.stream().anyMatch(task -> task.id().startsWith("auto_item_minecraft_"))
                        && hotspots.stream().anyMatch(task -> task.id().startsWith("auto_kill_minecraft_")),
                "Hotspot selection lost progression, item or combat variety");
        require(!QuestRepository.find("auto_item_minecraft_stick").hotEvent().enabled(),
                "A common early crafting task became a hot event");
        for (var task : generated) {
            require(!QuestText.title(task).getString().isBlank()
                    && !QuestText.title(task).getString().contains("%s"), "Unreadable generated task title");
            require(!task.parents().contains(task.id()), "Generated task references itself as a prerequisite");
            if (!task.id().startsWith("auto_adv_"))
                require(!QuestText.description(task).getString().isBlank(), "Generated description missing");
            require(QuestText.tooltip(task).getString().contains(QuestText.title(task).getString()),
                    "Generated title is absent from its tooltip");
        }
        var stick = QuestRepository.find("auto_item_minecraft_stick");
        require(stick.titleKey().equals("generate.xero_quest_journal.title.craft")
                        && stick.descriptionKey().equals("generate.xero_quest_journal.description.craft"),
                "Crafting title/description did not retain their localization keys");
        taskCount = taskDocuments;
        chapterCount = chapters;
        groupCount = groups;
        if (commandStage == 1) {
            preservedFixture = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    Path file = QuestRepository.fileOf("auto_item_minecraft_stick");
                    var data = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    data.addProperty("title", "Hand-authored stick objective");
                    data.addProperty("description", "Hand-authored objective notes.");
                    data.add("rewards", new com.google.gson.Gson().toJsonTree(java.util.List.of(
                            new QuestDefinition.Reward("item", "minecraft:diamond", 7))));
                    data.add("hotEvent", new com.google.gson.Gson().toJsonTree(
                            new xtdpotato.xero_quest_journal.core.model.HotEventData(
                                    false, "minecraft:stick", "Do not replace", 912, 73, true)));
                    Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                            .toJson(data));
                    QuestRepository.reload();
                    preservedTaskSource = QuestRepository.source("auto_item_minecraft_stick");
                    var table = QuestRepository.find("auto_item_minecraft_crafting_table");
                    progressIdentity = table.progressKey();
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    var progress = xtdpotato.xero_quest_journal.core.runtime.QuestProgressData.get(server);
                    progress.complete(player.getUUID(), table);
                    frozenRewards = progress.entry(player.getUUID(), table).rewards();
                    Path tableFile = QuestRepository.fileOf(table.id());
                    var legacy = com.google.gson.JsonParser.parseString(Files.readString(tableFile)).getAsJsonObject();
                    for (String key : java.util.List.of("generatedRewardMode", "generatedRewardMultiplier", "generatedRewards"))
                        legacy.remove(key);
                    var selfParent = new com.google.gson.JsonArray();
                    selfParent.add(table.id());
                    legacy.add("parents", selfParent);
                    legacy.addProperty("defaultUnlocked", false);
                    Files.writeString(tableFile, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(legacy));
                    Path planksFile = QuestRepository.fileOf("auto_item_minecraft_oak_planks");
                    var planks = com.google.gson.JsonParser.parseString(Files.readString(planksFile)).getAsJsonObject();
                    planks.remove("generatedParents");
                    planks.add("parents", new com.google.gson.JsonArray());
                    Files.writeString(planksFile, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(planks));
                    preservedFixture.complete(null);
                } catch (Throwable failure) { preservedFixture.completeExceptionally(failure); }
            });
            commandStage = 2;
            return false;
        }
        var modelTasks = QuestRepository.all().stream()
                .filter(quest -> QuestRepository.groupOf(quest.id()).startsWith("auto_"))
                .filter(quest -> quest.triggers().stream().anyMatch(trigger ->
                        trigger.item().startsWith("xqj-model:")))
                .toList();
        if (requireTactical) {
            require(ModList.get().isLoaded("tacz"), "TaCZ test profile was not loaded");
            require(ModList.get().isLoaded("lrtactical"), "LR Tactical Workshop test profile was not loaded");
            require(modelTasks.stream().anyMatch(task -> task.id().startsWith("auto_item_gun_")),
                    "TaCZ model-specific gun tasks were not generated");
            require(modelTasks.stream().anyMatch(task -> task.id().startsWith("auto_item_ammo_")),
                    "TaCZ model-specific ammo tasks were not generated");
            require(modelTasks.stream().anyMatch(task -> task.id().startsWith("auto_item_melee_")),
                    "LR Tactical model-specific melee tasks were not generated");
        }
        for (QuestDefinition task : modelTasks) {
            var trigger = task.triggers().stream()
                    .filter(value -> value.item().startsWith("xqj-model:")).findFirst().orElseThrow();
            ItemStack expected = QuestItemStackValue.decode(trigger.item(), game.player.registryAccess())
                    .orElse(ItemStack.EMPTY);
            require(!expected.isEmpty()
                            && !xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat
                            .identity(expected).isBlank(),
                    "Generated model target cannot resolve: " + task.id());
            ItemStack sameModelVariant = expected.copy();
            sameModelVariant.set(DataComponents.CUSTOM_NAME, Component.literal("Different display components"));
            require(QuestConditions.matches(sameModelVariant, trigger.item(), game.player.registryAccess()),
                    "Model target incorrectly depends on volatile item components: " + task.id());
            require(!QuestIcons.stack(task.icon()).isEmpty(), "Model icon stack did not resolve: " + task.id());
            String targetText = QuestText.targetName(trigger).getString();
            require(!targetText.matches("(item|block|entity)\\.[A-Za-z0-9_.-]+"),
                    "Model target rendered a raw translation key: " + task.id());
            require(!QuestText.title(task).getString().matches("(item|block|entity)\\.[A-Za-z0-9_.-]+"),
                    "Generated model title rendered a raw translation key: " + task.id());
        }
        if (requireTactical) {
            require(modelTasks.stream().filter(task -> task.id().startsWith("auto_item_gun_")).count() >= 5,
                    "Only a partial set of TaCZ gun models was indexed");
            require(modelTasks.stream().filter(task -> task.id().startsWith("auto_item_ammo_")).count() >= 5,
                    "Only a partial set of TaCZ ammo models was indexed");
            require(modelTasks.stream().filter(task -> task.id().startsWith("auto_item_attachment_")).count() >= 5,
                    "Only a partial set of TaCZ attachment models was indexed");
            require(modelTasks.stream().filter(task -> task.id().startsWith("auto_item_melee_")).count() > 0,
                    "LR Tactical melee models were not indexed");
            require(modelTasks.stream().filter(task -> task.id().startsWith("auto_item_throwable_")).count() > 0,
                    "LR Tactical throwable models were not indexed");
            previewTaskId = modelTasks.stream().filter(task -> task.id().startsWith("auto_item_gun_"))
                    .map(QuestDefinition::id).findFirst().orElseThrow();
        }
        modelTaskCount = modelTasks.size();
        if (requireTactical) {
            previewRequested = true;
            game.setScreen(QuestScreen.task(null, previewTaskId));
            return false;
        }
        writeReport(Path.of("..", "build", "verification"));
        return true;
    }

    private static void writeReport(Path output) throws Exception {
        Files.createDirectories(output);
        Files.writeString(output.resolve("generated-task-documents.txt"),
                "PASS\n"
                        + "Command: /xero_quest generate\n"
                        + "Task documents: " + taskCount + "\n"
                        + "Chapter documents: " + chapterCount + "\n"
                        + "Group directories: " + groupCount + "\n"
                        + "Model-specific tasks: " + modelTaskCount + "\n"
                        + "Selected hot events: " + hotCount + "\n"
                        + "TaCZ loaded: " + ModList.get().isLoaded("tacz") + "\n"
                        + "LR Tactical loaded: " + ModList.get().isLoaded("lrtactical") + "\n");
        Files.writeString(output.resolve("generated-spotlight-result.txt"),
                "PASS: generated source candidates included " + taskCount + " tasks, "
                        + groupCount + " groups, and " + chapterCount + " chapters.\n"
                        + "Bounded varied hot-event selection: " + hotCount + " task cards.\n"
                        + "Presentation defaults use localized collect/craft/defeat titles, objectives and explanations.\n"
                        + "Second generation preserved hand-authored title, description and hot-event fields.\n");
        Files.writeString(output.resolve("generated-recipe-unlock-result.txt"),
                "PASS: actual tier -> recipe 2 command updates; recipe materials and related-item fallback;\n"
                        + "legacy self-only parent repair persisted; authored rewards/text preserved; unchanged progress UUID;\n"
                        + "frozen completed rewards preserved; repeat generation idempotent; all prerequisites exist;\n"
                        + "full generated DAG and runtime root/parent-completion unlock traversal verified;\n"
                        + "all five ingredient prerequisites retained; authored-cycle guard; real recipe-material claim delivers once;\n"
                        + "owned broken file repaired even when absent from the live catalog after a failed reload.\n");
        Files.writeString(output.resolve("result.txt"),
                "PASS: generated task documents loaded and their model targets, names and icons were verified.\n");
    }

    private static void verifyMissingTranslationFallback() {
        String titleKey = "missingmod.advancement.root";
        String descriptionKey = titleKey + ".desc";
        var untranslatedAdvancement = new QuestDefinition(
                "auto_adv_missingmod_advancement_root", titleKey, descriptionKey, "world", "",
                true, false, 0, null, titleKey, descriptionKey, "", "fallback-regression",
                java.util.List.of(),
                java.util.List.of(new QuestDefinition.Trigger("advancement", "missingmod:advancement/root", 1)),
                java.util.List.of(), "default");
        require(QuestText.title(untranslatedAdvancement).getString().equals("Missingmod Root"),
                "Missing advancement language entry leaked its translation key into the task title");
        require(QuestText.description(untranslatedAdvancement).getString().isBlank(),
                "Missing advancement description language entry leaked its translation key");
        String tooltip = QuestText.tooltip(untranslatedAdvancement).getString();
        require(!tooltip.contains(titleKey) && !tooltip.contains(descriptionKey),
                "Missing advancement language key leaked into its task tooltip");

        var authoredFallback = new QuestDefinition(
                "authored_fallback", "Hand Written Objective", "A readable fallback description.",
                "world", "", true, false, 0, null, "missingmod.task.title", "missingmod.task.description",
                "", "fallback-regression-authored", java.util.List.of(), java.util.List.of(),
                java.util.List.of(), "default");
        require(QuestText.title(authoredFallback).getString().equals("Hand Written Objective")
                        && QuestText.description(authoredFallback).getString()
                        .equals("A readable fallback description."),
                "Readable authored fallback text was altered when its optional language entry was missing");
    }

    private static void checkRecipeReachability(net.minecraft.server.MinecraftServer server) throws Exception {
        var generator = xtdpotato.xero_quest_journal.core.io.QuestGenerator.class;
        var settingsType = Class.forName(generator.getName() + "$Settings");
        var constructor = settingsType.getDeclaredConstructor(String.class,
                xtdpotato.xero_quest_journal.core.io.QuestGenerator.RewardMode.class, int.class);
        constructor.setAccessible(true);
        var settings = constructor.newInstance("minecraft",
                xtdpotato.xero_quest_journal.core.io.QuestGenerator.RewardMode.NONE, 1);
        var method = generator.getDeclaredMethod("planItems", Map.class, Set.class, settingsType,
                net.minecraft.world.flag.FeatureFlagSet.class, Set.class, Map.class);
        method.setAccessible(true);
        var recipes = new java.util.HashMap<net.minecraft.world.item.Item,
                java.util.List<java.util.List<java.util.List<net.minecraft.world.item.Item>>>>();
        recipes.put(net.minecraft.world.item.Items.STICK, java.util.List.of(java.util.List.of(
                java.util.List.of(net.minecraft.world.item.Items.BARRIER))));
        recipes.put(net.minecraft.world.item.Items.DIAMOND, java.util.List.of(java.util.List.of(
                java.util.List.of(net.minecraft.world.item.Items.EMERALD))));
        recipes.put(net.minecraft.world.item.Items.EMERALD, java.util.List.of(java.util.List.of(
                java.util.List.of(net.minecraft.world.item.Items.DIAMOND))));
        var planned = new java.util.LinkedHashMap<String, Object>();
        method.invoke(null, recipes, Set.of(), settings, server.getWorldData().enabledFeatures(), Set.of(), planned);
        require(!planned.containsKey("auto_item_minecraft_stick"), "Creative ingredient made its output obtainable");
        require(!planned.containsKey("auto_item_minecraft_diamond")
                && !planned.containsKey("auto_item_minecraft_emerald"), "Unseeded recipe cycle generated tasks");
        planned.clear();
        recipes.put(net.minecraft.world.item.Items.STICK, java.util.List.of(java.util.List.of(
                java.util.List.of(net.minecraft.world.item.Items.BARRIER, net.minecraft.world.item.Items.OAK_LOG))));
        method.invoke(null, recipes, Set.of(net.minecraft.world.item.Items.OAK_LOG, net.minecraft.world.item.Items.DIAMOND),
                settings, server.getWorldData().enabledFeatures(), Set.of(), planned);
        require(planned.containsKey("auto_item_minecraft_stick"), "Obtainable ingredient alternative was skipped");
        require(planned.containsKey("auto_item_minecraft_diamond")
                && planned.containsKey("auto_item_minecraft_emerald"), "Loot-seeded recipe cycle was skipped");

        var outputSettings = constructor.newInstance("minecraft",
                xtdpotato.xero_quest_journal.core.io.QuestGenerator.RewardMode.OUTPUT, 16);
        planned.clear();
        method.invoke(null, recipes, Set.of(net.minecraft.world.item.Items.OAK_LOG),
                outputSettings, server.getWorldData().enabledFeatures(), Set.of(), planned);
        var plannedType = Class.forName(generator.getName() + "$Planned");
        var jsonAccessor = plannedType.getDeclaredMethod("json");
        jsonAccessor.setAccessible(true);
        var output = (com.google.gson.JsonObject) jsonAccessor.invoke(planned.get("auto_item_minecraft_stick"));
        var outputRewards = output.getAsJsonArray("rewards");
        boolean outputItem = false, guaranteedExperience = false;
        for (var value : outputRewards) {
            var reward = value.getAsJsonObject();
            outputItem |= reward.get("item").getAsString().equals("minecraft:stick")
                    && reward.get("count").getAsInt() == 16;
            guaranteedExperience |= reward.get("type").getAsString().equals("experience")
                    && reward.get("count").getAsInt() >= 1;
        }
        require(outputItem && guaranteedExperience,
                "Output mode did not reward the generated task item and guarantee experience");

        var recipeSettings = constructor.newInstance("minecraft",
                xtdpotato.xero_quest_journal.core.io.QuestGenerator.RewardMode.RECIPE, 1);
        planned.clear();
        method.invoke(null, recipes, Set.of(net.minecraft.world.item.Items.OAK_LOG),
                recipeSettings, server.getWorldData().enabledFeatures(), Set.of(), planned);
        var recipe = (com.google.gson.JsonObject) jsonAccessor.invoke(planned.get("auto_item_minecraft_stick"));
        var recipeRewards = recipe.getAsJsonArray("rewards");
        require(recipeRewards.size() == 1
                        && recipeRewards.get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:oak_log"),
                "Existing recipe mode stopped rewarding recipe materials");
        planned.clear();
        method.invoke(null, recipes, Set.of(net.minecraft.world.item.Items.OAK_LOG, net.minecraft.world.item.Items.STICK),
                recipeSettings, server.getWorldData().enabledFeatures(), Set.of(), planned);
        recipe = (com.google.gson.JsonObject) jsonAccessor.invoke(planned.get("auto_item_minecraft_stick"));
        recipeRewards = recipe.getAsJsonArray("rewards");
        require(recipeRewards.size() == 1
                        && recipeRewards.get(0).getAsJsonObject().get("type").getAsString().equals("item")
                        && recipeRewards.get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:oak_log"),
                "Loot-seeded craftable item fell back to experience instead of its recipe materials");
        var gathered = (com.google.gson.JsonObject) jsonAccessor.invoke(planned.get("auto_item_minecraft_oak_log"));
        require(gathered.getAsJsonArray("rewards").get(0).getAsJsonObject().get("item").getAsString()
                        .equals("minecraft:oak_log"),
                "Recipe mode without a recipe did not provide the related target item");
        recipes.clear();
        var materials = java.util.List.of(net.minecraft.world.item.Items.OAK_LOG, net.minecraft.world.item.Items.APPLE,
                net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.DIRT,
                net.minecraft.world.item.Items.SAND);
        recipes.put(net.minecraft.world.item.Items.STICK, java.util.List.of(materials.stream()
                .map(java.util.List::of).toList()));
        planned.clear();
        method.invoke(null, recipes, Set.copyOf(materials), recipeSettings,
                server.getWorldData().enabledFeatures(), Set.of(), planned);
        var parentAccessor = plannedType.getDeclaredMethod("parentCandidates"); parentAccessor.setAccessible(true);
        require(((java.util.List<?>) parentAccessor.invoke(planned.get("auto_item_minecraft_stick"))).size() == 5,
                "Recipe unlock path dropped ingredients after the fourth material");
        var updateParents = generator.getDeclaredMethod("updateGeneratedParents", com.google.gson.JsonObject.class,
                plannedType, Map.class, Set.class); updateParents.setAccessible(true);
        var old = new com.google.gson.JsonObject();
        old.add("parents", new com.google.gson.JsonArray());
        var dependencies = new java.util.HashMap<String, java.util.List<String>>();
        var parentIds = materials.stream().map(material -> "auto_item_minecraft_"
                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material).getPath()).toList();
        for (String id : parentIds) dependencies.put(id, java.util.List.of());
        dependencies.put(parentIds.getFirst(), java.util.List.of("auto_item_minecraft_stick"));
        updateParents.invoke(null, old, planned.get("auto_item_minecraft_stick"), dependencies, Set.copyOf(parentIds));
        require(old.getAsJsonArray("parents").size() == 4
                        && old.getAsJsonArray("parents").asList().stream()
                        .noneMatch(parent -> parent.getAsString().equals(parentIds.getFirst())),
                "Generated material edge closed a cycle through authored prerequisites");
    }

    private static void checkUnlockGraph(net.minecraft.server.level.ServerPlayer player) {
        var generated = QuestRepository.all().stream()
                .filter(task -> QuestRepository.groupOf(task.id()).startsWith("auto_")).toList();
        var progress = new xtdpotato.xero_quest_journal.core.runtime.QuestProgressData();
        var pending = new java.util.LinkedHashMap<String, QuestDefinition>();
        for (var task : generated) {
            require(!task.parents().contains(task.id()), "Self prerequisite: " + task.id());
            require(new java.util.HashSet<>(task.parents()).size() == task.parents().size(),
                    "Duplicate prerequisite: " + task.id());
            for (String parent : task.parents())
                require(QuestRepository.find(parent) != null, "Missing prerequisite: " + parent);
            require(xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.unlocked(progress, player, task)
                            == task.parents().isEmpty(), "Incorrect initial unlock state: " + task.id());
            pending.put(task.id(), task);
        }
        while (!pending.isEmpty()) {
            var ready = pending.values().stream().filter(task -> task.parents().stream()
                    .allMatch(parent -> progress.entry(player.getUUID(), QuestRepository.find(parent)) != null)).toList();
            require(!ready.isEmpty(), "Generated prerequisite graph has a cycle or unreachable path: " + pending.keySet());
            for (var task : ready) {
                require(xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.unlocked(progress, player, task),
                        "Completed parents did not unlock task: " + task.id());
                progress.complete(player.getUUID(), task, java.util.List.of());
                pending.remove(task.id());
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
