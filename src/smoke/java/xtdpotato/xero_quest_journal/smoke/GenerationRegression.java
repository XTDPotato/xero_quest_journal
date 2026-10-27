package xtdpotato.xero_quest_journal.smoke;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.*;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

final class GenerationRegression {
    private static final Path OUT = Path.of("..", "build", "verification");
    private static int stage, wait;
    private static CompletableFuture<?> work;
    private static QuestScreen journal;
    private static QuestTemplateScreen templates;
    private static Object stableButton;
    private static Map<String, String> sources;
    private static String village;
    private static String rewardRepairTask = "";
    private static boolean scopePrepared, scopeSelectionPrepared, scopeCaptured;
    private static boolean compactGenerationLayoutChecked;
    private static boolean tierRewardsEditorChecked;
    private static boolean dailyAnecdotesStarted, dailyAnecdotesFirstBatchChecked,
            dailyAnecdotesSecondBatchChecked;
    private static List<String> dailyAnecdoteIds = List.of();
    private static HudNotificationLayoutScreen hud;
    private static long started;
    static boolean tick(Minecraft game) throws Exception {
        if(stage==25)return GraphSearchPickupRegression.tick(game);
        if (game.player == null || game.level == null || --wait > 0) return false;
        if (game.screen instanceof AnimatedQuestScreen a && a.inputBlocked()) return false;
        if (work != null) { if (!work.isDone()) return false; work.join(); work = null; }
        var server = game.getSingleplayerServer(); var playerId = game.player.getUUID();
        switch (stage) {
            case 0 -> {
                if (!(game.screen instanceof QuestGenerateScreen)) return false;
                int[] expectedXp = {2, 3, 5, 10, 20};
                for (int tier = 1; tier <= expectedXp.length; tier++) {
                    var points = (com.google.gson.JsonObject) invoke(QuestGenerator.class, "experienceReward",
                            new Class<?>[]{int.class, int.class, String.class}, tier, 1, "points");
                    var levels = (com.google.gson.JsonObject) invoke(QuestGenerator.class, "experienceReward",
                            new Class<?>[]{int.class, int.class, String.class}, tier, 1, "levels");
                    check(points.get("type").getAsString().equals("experience")
                                    && points.get("count").getAsInt() == expectedXp[tier - 1],
                            "Difficulty XP reward mapping changed at tier " + tier);
                    check(levels.get("type").getAsString().equals("levels")
                                    && levels.get("count").getAsInt() == Math.max(1, tier - 2),
                            "Difficulty level reward mapping changed at tier " + tier);
                    var automatic = (com.google.gson.JsonObject) invoke(QuestGenerator.class,
                            "experienceReward", new Class<?>[]{int.class, int.class, String.class},
                            tier, 1, "auto");
                    check(automatic.get("type").getAsString().equals(tier >= 4 ? "levels" : "experience")
                                    && automatic.get("count").getAsInt()
                                    == (tier >= 4 ? tier - 2 : expectedXp[tier - 1]),
                            "Automatic XP unit mapping changed at tier " + tier);
                }
                var configuredRewards = QuestGenerator.GenerationOptions.defaults().tierItemRewards();
                check(configuredRewards.size() == 25
                                && configuredRewards.stream().anyMatch(r -> r.tier() == 1 && r.item().equals("minecraft:coal"))
                                && configuredRewards.stream().anyMatch(r -> r.tier() == 2 && r.item().equals("minecraft:lapis_lazuli"))
                                && configuredRewards.stream().anyMatch(r -> r.tier() == 3 && r.item().equals("minecraft:diamond") && r.weight() == 10)
                                && configuredRewards.stream().anyMatch(r -> r.tier() == 4 && r.item().equals("minecraft:netherite_scrap") && r.weight() == 10)
                                && configuredRewards.stream().anyMatch(r -> r.tier() == 5 && r.item().equals("minecraft:diamond") && r.count() == 3)
                                && configuredRewards.stream().anyMatch(r -> r.tier() == 6 && r.item().equals("minecraft:netherite_ingot") && r.weight() == 10),
                        "Default reward catalog is missing difficulty materials");
                var legacyLevelOptions = new QuestGenerator.GenerationOptions(
                        1, 1, 1, true, true, true, true, true, true);
                check(legacyLevelOptions.experienceUnit().equals("auto"),
                        "Older generation option constructor must default to difficulty-based XP units");
                check(!server.overworld().getChunkSource().getGenerator().getClass().getSimpleName().contains("Flat"), "Normal terrain required");
                check(QuestRepository.all().stream().noneMatch(q -> q.id().startsWith("auto_story")), "Opening chooser generated tasks");
                game.options.guiScale().set(2); game.getWindow().setWindowed(1920,1080); game.resizeDisplay();
                game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                work = game.reloadResourcePacks();
            }
            case 1 -> {
                capture(game,"generation-chooser-zh-1920");
                button(game.screen,"generate-cancel").onPress(); wait=5;
            }
            case 2 -> {
                check(game.screen == null, "Cancel did not close chooser");
                work = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    check(QuestProgressData.get(server).generationOffered(playerId), "First-run choice not acknowledged");
                    var saved = QuestProgressData.get(server).save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
                    check(QuestProgressData.load(saved, server.registryAccess()).generationOffered(playerId), "First-run acknowledgement did not persist");
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "xero_quest start_generate_gui");
                    return true;
                }); wait=5;
            }
            case 3 -> {
                if (!scopePrepared) {
                    if (game.screen instanceof QuestGenerateScreen) {
                        button(game.screen, "generate-scope").onPress();
                        wait = 8;
                        return false;
                    }
                    Screen scope = game.screen;
                    if (!scopeCaptured) {
                        capture(game, "generation-scope-zh-1920");
                        scopeCaptured = true;
                        wait = 6;
                        return false;
                    }
                    if (!scopeSelectionPrepared) {
                        button(scope, "generate-scope-none").onPress();
                        var search = (EditBox) field(scope, "search");
                        search.setValue("minecraft");
                        clickFirstScopeRow(scope);
                        search.setValue("story_fixture");
                        clickFirstScopeRow(scope);
                        var selected = (Set<String>) field(scope, "selected");
                        check(selected.size() == 2 && selected.containsAll(Set.of("minecraft", "story_fixture")),
                                "Generation content picker did not retain multiple selections");
                        var configuredItems = new ArrayList<>(
                                QuestGenerator.GenerationOptions.defaults().tierItemRewards());
                        configuredItems.set(0, new QuestGenerator.TierItemReward(1, "minecraft:coal", 2));
                        var options = new QuestGenerator.GenerationOptions(2, 2, 1, "points",
                                true, true, true, true, true, true, configuredItems);
                        var payload = new QuestGenerationNetwork.Choose(2, "progress",
                                List.of("minecraft", "story_fixture"), true, options);
                        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), game.player.registryAccess());
                        try {
                            QuestGenerationNetwork.Choose.CODEC.encode(buffer, payload);
                            check(QuestGenerationNetwork.Choose.CODEC.decode(buffer).namespaces()
                                            .equals(payload.namespaces()),
                                    "Generation content multi-select was lost in the network payload");
                        } finally {
                            buffer.release();
                        }
                        var unitBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), game.player.registryAccess());
                        try {
                            QuestGenerationNetwork.Choose.CODEC.encode(unitBuffer, payload);
                            check(QuestGenerationNetwork.Choose.CODEC.decode(unitBuffer).options()
                                            .experienceUnit().equals("points"),
                                    "Experience reward unit was lost in the network payload");
                            unitBuffer.clear();
                            QuestGenerationNetwork.Choose.CODEC.encode(unitBuffer, payload);
                            check(QuestGenerationNetwork.Choose.CODEC.decode(unitBuffer).options()
                                            .tierItemRewards().equals(configuredItems),
                                    "Difficulty item reward catalog was lost in the network payload");
                        } finally {
                            unitBuffer.release();
                        }
                        scopeSelectionPrepared = true;
                        wait = 8;
                        return false;
                    }
                    button(scope, "generate-scope-none").onPress();
                    var search = (EditBox) field(scope, "search");
                    search.setValue("story_fixture");
                    clickFirstScopeRow(scope);
                    button(scope, "generate-scope-apply").onPress();
                    scopePrepared = true;
                    return false;
                }
                if (game.screen instanceof QuestGenerationRewardTiersScreen editor) {
                    check(((List<?>) field(editor, "rewards")).size() == 25,
                            "Tiered item reward editor did not show its default mineral catalog");
                    capture(game, "generation-tier-rewards-zh-1920");
                    button(editor, "generation-rewards-close").onPress();
                    tierRewardsEditorChecked = true;
                    wait = 6;
                    return false;
                }
                if (!(game.screen instanceof QuestGenerateScreen)) return false;
                if (!compactGenerationLayoutChecked) {
                    button(game.screen, "generate-advanced").onPress();
                    game.options.guiScale().set(4);
                    game.resizeDisplay();
                    var scroll = field(game.screen, "contentScroll");
                    check((double) invoke(scroll, "maximum", new Class<?>[]{}) > 0,
                            "Advanced generation settings did not become scrollable in a compact window");
                    var checkbox = game.screen.children().stream()
                            .filter(Checkbox.class::isInstance).map(Checkbox.class::cast)
                            .filter(box -> box.getMessage().getString().contains("游戏进度任务"))
                            .findFirst().orElseThrow();
                    int checkboxX = checkbox.getX() + 8;
                    int checkboxY = Math.min(checkbox.getY() + checkbox.getHeight() / 2,
                            game.screen.height - 65);
                    check(game.screen.mouseClicked(checkboxX, checkboxY, 0),
                            "Compact advanced checkbox press was not handled");
                    game.screen.mouseDragged(checkboxX, checkboxY - 48, 0, 0, -48);
                    game.screen.mouseReleased(checkboxX, checkboxY - 48, 0);
                    check((boolean) field(game.screen, "advancementTasks"),
                            "Dragging the generation settings list activated a checkbox");
                    check((double) invoke(scroll, "target", new Class<?>[]{}) > 0,
                            "Dragging the settings list did not scroll it");
                    game.options.guiScale().set(2);
                    game.resizeDisplay();
                    compactGenerationLayoutChecked = true;
                    wait = 5;
                    return false;
                }
                if (!tierRewardsEditorChecked) {
                    button(game.screen, "generate-tier-item-rewards").onPress();
                    wait = 6;
                    return false;
                }
                ((EditBox) field(game.screen, "taskMultiplierField")).setValue("2");
                ((EditBox) field(game.screen, "rewardMultiplierField")).setValue("2");
                ((EditBox) field(game.screen, "experienceMultiplierField")).setValue("1");
                button(game.screen,"generate-start").onPress(); wait=10;
            }
            case 4 -> {
                if (QuestGenerator.running()) return false;
                var tasks = QuestRepository.all().stream().filter(q -> q.id().startsWith("auto_story")).toList();
                check(tasks.size()>2, "Progress mode did not add linked recipe tasks");
                var advancement = tasks.stream().filter(q -> q.triggers().stream().anyMatch(t -> t.type().equals("advancement") && t.item().equals("story_fixture:root"))).findFirst().orElseThrow();
                check(advancement.rewards().stream().anyMatch(r -> r.type().equals("item"))
                                && advancement.rewards().stream().anyMatch(r -> r.type().equals("experience")
                                && Set.of(2, 3, 5, 10, 20).contains(r.count())),
                        "Progress reward is missing recipe supplies or its guaranteed experience");
                check(tasks.stream().allMatch(q -> q.rewards().stream()
                                .anyMatch(reward -> Set.of("experience", "levels").contains(reward.type())
                                        && Set.of(2, 3, 5, 10, 20).contains(reward.count()))),
                        "Progress story task did not receive at least 1 experience");
                var generatedLoot = tasks.stream().flatMap(q -> q.rewards().stream())
                        .filter(reward -> reward.type().equals("loot"))
                        .map(QuestDefinition.Reward::item)
                        .map(QuestRewards::lootTable).filter(Objects::nonNull).toList();
                check(!generatedLoot.isEmpty()
                                && generatedLoot.stream().anyMatch(table -> table.mode()
                                        == LootDefinition.Mode.RANDOM_WITH_REPLACEMENT
                                        && table.entries().stream().anyMatch(entry ->
                                        entry.item().equals("minecraft:coal"))),
                        "Difficulty-tiered weighted loot rewards were not generated");
                check(tasks.stream().anyMatch(q -> q.triggers().stream()
                                .anyMatch(trigger -> trigger.type().equals("item_hold") && trigger.count() == 2)),
                        "Advanced task amount multiplier was not applied to generated item goals");
                for (var task : tasks) for (var reward : task.rewards()) {
                    check(!reward.type().equals("item") || !reward.item().isBlank(),
                            "Generated task contains an empty item reward: " + task.id());
                    check(!reward.type().equals("item") || !reward.item().startsWith("inline:"),
                            "Generated loot-table JSON was serialized as an item: " + task.id());
                }
                for (var task : tasks) {
                    var inlineTableIds = task.rewards().stream().filter(reward -> reward.type().equals("loot"))
                            .map(QuestDefinition.Reward::item).filter(item -> item.startsWith("inline:"))
                            .map(item -> com.google.gson.JsonParser.parseString(item.substring("inline:".length()))
                                    .getAsJsonObject().get("id").getAsString()).toList();
                    check(inlineTableIds.stream().distinct().count() == inlineTableIds.size(),
                            "Generated task repeats the same inline loot table: " + task.id());
                }
                var ids = new HashSet<String>(); tasks.forEach(q -> ids.add(q.id()));
                for (var q : tasks) check(!q.parents().contains(q.id()) && q.parents().stream().allMatch(ids::contains), "Broken story prerequisite");
                sources = new HashMap<>(); for (var q : tasks) sources.put(q.id(),QuestRepository.source(q.id()));
                rewardRepairTask = tasks.stream().filter(q -> !q.rewards().isEmpty()).map(QuestDefinition::id)
                        .findFirst().orElseThrow();
                work = server.submit(() -> {
                    check(AnecdoteGenerationData.get(server).enabled(),
                            "Progress-mode generation did not enable the daily anecdote scheduler");
                    try {
                        try (var paths = Files.walk(QuestRepository.root())) {
                            Path taskFile = paths.filter(path -> path.getFileName().toString()
                                    .equals(rewardRepairTask + ".json")).findFirst().orElseThrow();
                            var broken = com.google.gson.JsonParser.parseString(
                                    QuestRepository.source(rewardRepairTask)).getAsJsonObject();
                            broken.add("rewards", new com.google.gson.JsonArray());
                            broken.add("generatedRewards", new com.google.gson.JsonArray());
                            Files.writeString(taskFile, new com.google.gson.GsonBuilder()
                                    .setPrettyPrinting().create().toJson(broken));
                        }
                        QuestRepository.reload();
                        sources.remove(rewardRepairTask);
                        return QuestGenerator.start(server,"story_fixture",QuestGenerator.RewardMode.RECIPE,1,
                                QuestGenerator.GenerationMode.PROGRESS,true,
                                new QuestGenerator.GenerationOptions(2, 2, 1, true, true,
                                        true, true, true, true),
                                (m,f)->{ if(f) throw new AssertionError(m.getString()); });
                    } catch (Exception failure) {
                        throw new IllegalStateException("Unable to prepare story reward repair fixture", failure);
                    }
                }); wait=10;
            }
            case 5 -> {
                if (QuestGenerator.running()) return false;
                for (var e : sources.entrySet()) {
                    String current = QuestRepository.source(e.getKey());
                    check(sameExceptGeneratedRewards(e.getValue(), current),
                            "Rerun changed non-reward content for existing task " + e.getKey());
                    if (!e.getValue().equals(current)) {
                        var original = com.google.gson.JsonParser.parseString(e.getValue()).getAsJsonObject();
                        boolean containedMalformedEntry = hasMalformedGeneratedRewardArray(
                                original.has("rewards") && original.get("rewards").isJsonArray()
                                        ? original.getAsJsonArray("rewards") : null)
                                || hasMalformedGeneratedRewardArray(
                                original.has("generatedRewards") && original.get("generatedRewards").isJsonArray()
                                        ? original.getAsJsonArray("generatedRewards") : null);
                        check(containedMalformedEntry,
                                "Rerun changed reward content that did not need generated-reward repair");
                    }
                }
                check(QuestRepository.find(rewardRepairTask).rewards().stream()
                                .anyMatch(reward -> Set.of("experience", "levels").contains(reward.type())
                                        && reward.count() >= 1),
                        "Regeneration did not repair an empty generated story reward");
                game.setScreen(journal = new QuestScreen()); ClientEditMode.set(true);
                templates = new QuestTemplateScreen(journal,""); game.setScreen(templates); wait=4;
            }
            case 6 -> { button(templates,"template-add-group").onPress(); wait=4; }
            case 7 -> {
                ((EditBox)field(game.screen,"name")).setValue("自定义模板组"); button(game.screen,"template-group-save").onPress(); wait=5;
            }
            case 8 -> {
                if (game.screen != templates) return false;
                check(ClientQuestDefinitions.templateLibrary().groups().contains("自定义模板组"),"Empty template group missing");
                check(QuestCatalog.decode(QuestCatalog.serverJson()).templateLibrary().groups().contains("自定义模板组"),"Template group absent from network catalog");
                check(QuestRepository.groups().stream().noneMatch(g -> g.name().equals("自定义模板组")), "Template group polluted task groups");
                ((EditBox)field(templates,"search")).setValue("主线"); wait=3;
            }
            case 9 -> {
                var source=button(templates,"template-main_acquire");
                var target=button(templates,"template-main_submit");
                templates.mouseClicked(source.getX()+50,source.getY()+12,0);
                set(templates,"dragTemplateStartedAt",System.nanoTime()-400_000_000L);
                templates.mouseDragged(target.getX()+50,target.getBottom()-2,0,0,40);
                templates.mouseReleased(target.getX()+50,target.getBottom()-2,0); wait=6;
            }
            case 10 -> {
                if (field(templates,"pendingOrder") != null) return false;
                var order=ClientQuestDefinitions.templateLibrary().order();
                check(order.indexOf("main_acquire")>order.indexOf("main_submit"),"Long-press template reorder failed");
                capture(game,"template-acquire-order-zh-1920");
                work=server.submit(() -> {
                    try {
                        var player=server.getPlayerList().getPlayer(playerId);
                        for(int i=0;i<3;i++) {
                            String templateId=i==0?"world_acquire":"world_view";
                            var template=QuestTemplateStore.all().stream().filter(t->t.id().equals(templateId)).findFirst().orElseThrow();
                            check(QuestAuthoring.create(player,new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,"generation_move_"+i,"排序任务 "+i,"","","","world","minecraft:oak_log",16,"minecraft:oak_log","","",1,"manual",templateId,null,null,template.defaultConditions(),List.of())).successful(),"Create acquisition fixture");
                        }
                        player.getInventory().clearContent(); player.getInventory().add(new ItemStack(Items.OAK_LOG,16));
                        QuestRuntime.evaluate(player);
                        check(QuestRuntime.completed(player,QuestRepository.find("generation_move_0")),"Holding 16 logs did not auto-complete");
                        check(player.getInventory().countItem(Items.OAK_LOG)==16,"Acquisition template consumed logs");
                        return true;
                    } catch(Exception e){throw new RuntimeException(e);}
                }); wait=5;
            }
            case 11 -> {
                ClientPreferences.get().reducedMotion=false;
                journal=QuestScreen.task(null,"generation_move_1"); set(journal,"editMode",true); game.setScreen(journal); wait=5;
            }
            case 12 -> {
                invoke(journal,"startTaskMove",new Class<?>[]{String.class},"generation_move_1");
                var next = button(journal,"order-next");
                click(journal,next); stableButton = next; wait=8;
            }
            case 13 -> {
                if(field(journal,"orderPending")!=null)return false;
                stableButton=button(journal,"order-done"); wait=12;
            }
            case 14 -> {
                check(stableButton==button(journal,"order-done"),"Movement rebuild loop still present");
                check(!journal.inputBlocked(),"Movement left input blocked");
                var previous = button(journal,"order-previous");
                click(journal,previous); stableButton = button(journal,"order-done"); wait=8;
            }
            case 15 -> {
                if(field(journal,"orderPending")!=null)return false;
                click(journal,button(journal,"order-done")); check(field(journal,"movingTask").equals(""),"Move mode did not exit");
                game.setScreen(null);
                work=server.submit(()->QuestGenerator.start(server,"minecraft",QuestGenerator.RewardMode.RECIPE,1,QuestGenerator.GenerationMode.STRUCTURE,false,(m,f)->{if(f)throw new AssertionError(m.getString());})); wait=15;
            }
            case 16 -> {
                if(QuestGenerator.running())return false;
                if (!dailyAnecdotesStarted) {
                    work = server.submit(() -> {
                        QuestGenerator.tickDailyAnecdotes(server);
                        return true;
                    });
                    dailyAnecdotesStarted = true;
                    wait = 2;
                    return false;
                }
                if (!dailyAnecdotesFirstBatchChecked) {
                    dailyAnecdoteIds = AnecdoteStore.all().stream()
                            .filter(entry -> entry.id().startsWith("auto_anecdote_"))
                            .filter(entry -> entry.records().size() == 1)
                            .map(AnecdoteDefinition::id).toList();
                    check(dailyAnecdoteIds.size() >= 1 && dailyAnecdoteIds.size() <= 4,
                            "Daily anecdote generation did not add 1-4 entries; found "
                                    + dailyAnecdoteIds.size() + " under " + AnecdoteStore.root());
                    for (String anecdoteId : dailyAnecdoteIds) {
                        var entry = AnecdoteStore.find(anecdoteId);
                        check(entry != null && entry.records().size() == 1,
                                "Daily structure anecdote was not persisted with its arrival record");
                        var arrival = QuestRepository.find(entry.records().getFirst().generatedQuestId());
                        check(arrival != null && arrival.triggers().stream().anyMatch(trigger ->
                                        trigger.type().equals("location")
                                                && trigger.item().startsWith("structure|")),
                                "Daily anecdote arrival task is not a structure destination task");
                        String dimension = arrival.triggers().stream().filter(trigger ->
                                        trigger.type().equals("location"))
                                .map(trigger -> trigger.item().split("\\|", -1))
                                .filter(parts -> parts.length == 3).map(parts -> parts[1]).findFirst().orElse("");
                        if (dimension.equals("minecraft:the_nether") || dimension.equals("minecraft:the_end"))
                            check(arrival.parents().contains(QuestGenerator.dimensionGateQuestId(dimension)),
                                    "Daily anecdote exposes a destination before its dimension progression gate");
                    }
                    dailyAnecdotesFirstBatchChecked = true;
                    work = server.submit(() -> {
                        long nextDay = Math.floorDiv(server.overworld().getDayTime(), 24_000L) + 1;
                        server.overworld().setDayTime(nextDay * 24_000L);
                        QuestGenerator.tickDailyAnecdotes(server);
                        return true;
                    });
                    wait = 2;
                    return false;
                }
                if (!dailyAnecdotesSecondBatchChecked) {
                    for (String anecdoteId : dailyAnecdoteIds) {
                        var entry = AnecdoteStore.find(anecdoteId);
                        check(entry != null && entry.records().size() == 2,
                                "Second game day did not append a follow-up record to the first-day anecdote");
                        var arrival = QuestRepository.find(entry.records().getFirst().generatedQuestId());
                        var followup = QuestRepository.find(entry.records().get(1).generatedQuestId());
                        check(arrival != null && followup != null && followup.parents().contains(arrival.id()),
                                "Anecdote follow-up task is not linked after its structure task");
                        boolean villageTarget = arrival.triggers().stream().anyMatch(trigger ->
                                trigger.type().equals("location") && trigger.item().contains("village"));
                        check(villageTarget
                                        ? followup.triggers().stream().anyMatch(trigger ->
                                        trigger.type().equals("block_interact")
                                                && trigger.item().equals("minecraft:bell") && trigger.count() == 3)
                                        : followup.triggers().stream().anyMatch(trigger ->
                                        trigger.type().equals("item_acquire")),
                                "Follow-up must require three village bell interactions or acquiring an item");
                    }
                    dailyAnecdotesSecondBatchChecked = true;
                    work = server.submit(() -> {
                        QuestGenerator.tickDailyAnecdotes(server);
                        var state = AnecdoteGenerationData.get(server);
                        check(state.lastProcessedDay() == Math.floorDiv(server.overworld().getDayTime(), 24_000L),
                                "Daily anecdote scheduler did not persist the processed game day");
                        var saved = state.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
                        var restored = AnecdoteGenerationData.load(saved, server.registryAccess());
                        check(restored.enabled()
                                        && restored.namespaces().equals(state.namespaces())
                                        && restored.pendingFollowups().equals(state.pendingFollowups())
                                        && restored.lastProcessedDay() == state.lastProcessedDay(),
                                "Daily anecdote scheduler state did not survive save/load");
                        return true;
                    });
                    wait = 2;
                    return false;
                }
                var q=QuestRepository.all().stream().filter(t->t.id().startsWith("auto_story_visit") && t.triggers().stream().anyMatch(c->c.item().endsWith("minecraft:village_plains"))).findFirst().orElseThrow();
                village=q.id();
                var structureGroup = QuestRepository.groupOf(village);
                check(structureGroup.equals("auto_structures_minecraft"),
                        "Village exploration was not placed in its dedicated structure group: " + structureGroup);
                check(QuestRepository.chapters().stream().anyMatch(chapter -> chapter.groupId().equals(structureGroup)
                                && chapter.id().equals(structureGroup + "_7_exploration")
                                && chapter.quests().contains(village)),
                        "Village exploration missing from its dedicated journey row");
                for (String dimension : List.of("minecraft:the_nether", "minecraft:the_end")) {
                    String gateId = QuestGenerator.dimensionGateQuestId(dimension);
                    var dimensionStructures = QuestRepository.all().stream().filter(task ->
                            task.triggers().stream().anyMatch(trigger -> trigger.type().equals("location")
                                    && trigger.item().startsWith("structure|" + dimension + "|"))).toList();
                    if (!dimensionStructures.isEmpty()) {
                        var gate = QuestRepository.find(gateId);
                        check(gate != null && dimensionStructures.stream()
                                        .allMatch(task -> dependsOn(task, gateId, new HashSet<>())),
                                "Generated structures in " + dimension + " are not progression-gated");
                        String expectedAdvancement = dimension.equals("minecraft:the_nether")
                                ? "minecraft:story/mine_diamond" : "minecraft:nether/obtain_blaze_rod";
                        check(gate.triggers().stream().anyMatch(trigger -> trigger.type().equals("advancement")
                                        && trigger.item().equals(expectedAdvancement)),
                                "Dimension progression gate does not require the expected advancement");
                    }
                }
                check(q.presentation().controls().stream().anyMatch(c->c.graph().nodes().stream().anyMatch(n->n.type().equals("locate_structure"))),"Exploration missing locate action");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(playerId); QuestRuntime.trigger(p,village,"unlock",true); return QuestRuntime.toggleTracking(p,village);});
                started=System.nanoTime(); wait=10;
            }
            case 17 -> {
                if(QuestHudRenderer.locatedLocation(village)==null){check(System.nanoTime()-started<90_000_000_000L,"Structure tracking never received locate coordinates");return false;}
                var location=QuestHudRenderer.locatedLocation(village); check(location.resolved(),"Located target unresolved");
                QuestUnlockToast.clear(); MailArrivalToast.clear(); game.getToasts().clear(); wait=5;
            }
            case 18 -> {
                var markers=(List<?>)field(QuestHudRenderer.class,"markerSnapshot");
                if (markers.isEmpty()) {
                    check(System.nanoTime() - started < 15_000_000_000L,
                            "No tracked coordinate marker drawn");
                    wait = 2;
                    return false;
                }
                capture(game,"generated-structure-guidance-zh-1920");
                work=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(playerId); var q=QuestRepository.find(village);
                    String cached=QuestControlData.get(p).guidance(p,q,q.triggers().getFirst().item());
                    check(!cached.isBlank(),"Locate did not persist shared target");
                    check(QuestStructureGuidance.request(p,village).equals("guidance_ready"),"Cached locate was searched again");
                    var position=QuestHudRenderer.locatedLocation(village);
                    p.serverLevel().getChunk(net.minecraft.core.BlockPos.containing(position.x(),position.y(),position.z()));
                    p.teleportTo(p.serverLevel(),position.x(),position.y(),position.z(),p.getYRot(),p.getXRot());
                    QuestRuntime.evaluate(p);
                    check(QuestRuntime.completed(p,q),"Arriving at located village did not complete structure task");
                    return true;
                }); wait=3;
            }
            case 19 -> {
                ClientPreferences.get().reducedMotion=true;
                hud=new HudNotificationLayoutScreen(null); game.setScreen(hud); wait=4;
                ((EditBox)field(hud,"scaleInput")).setValue("10");
                ((EditBox)field(hud,"textScaleInput")).setValue("200");
            }
            case 20 -> {
                var geometry=NoticeGeometry.task(hud.width,10,200,ClientPreferences.get().taskToastIconScalePercent);
                check(geometry.hintY()+game.font.lineHeight*geometry.textScale()<geometry.height(),"Task hint overflows at minimum scale/maximum font");
                int[] bounds=(int[])invoke(hud,"selectedRect",new Class<?>[]{});
                check(Math.abs(bounds[3]-geometry.height()*geometry.scale()*(double)invoke(hud,"sceneScale",new Class<?>[]{}))<2,"Editor task outline differs from runtime dimensions");
                capture(game,"notice-layout-large-font-zh-1920");
                button(hud,"notification-layout-pickup").onPress();
                ((EditBox)field(hud,"scaleInput")).setValue("50");
                ((EditBox)field(hud,"textScaleInput")).setValue("200");wait=4;
            }
            case 21 -> {
                var geometry=NoticeGeometry.pickup(hud.width,50,200,game.font);
                check(geometry.rowHeight()>game.font.lineHeight*geometry.textScale()+10,"Pickup rows overlap at minimum scale/maximum font");
                capture(game,"notice-pickup-small-scale-zh-1920");
                int[] map=(int[])invoke(hud,"minimapAvoidRect",new Class<?>[]{});
                double x=map[0]+map[2]/2.,y=map[1]+map[3]/2.;
                check(hud.mouseClicked(x,y,0),"Map drag not handled");hud.mouseDragged(x-100,y+45,0,-100,45);hud.mouseReleased(x-100,y+45,0);
                check((int)field(hud,"minimapX")>=0 && (int)field(hud,"minimapY")>0,"Minimap region position cannot move");
                game.options.guiScale().set(4);game.resizeDisplay();
                button(hud,"notification-layout-task").onPress();
                hud.mouseScrolled(hud.width-30,hud.height/2.,0,-15);wait=10;
            }
            case 22 -> {
                check(button(hud,"notification-play-preview").visible,"Compact properties cannot scroll to preview control");
                button(hud,"notification-play-preview").onPress();check((boolean)field(hud,"previewPlaying"),"Animation preview did not start");
                button(hud,"notification-layout-save").onPress();wait=2;
            }
            case 23 -> {
                check(ClientPreferences.get().minimapAvoidXPercent>=0 && ClientPreferences.get().minimapAvoidYPercent>0,"Custom avoid position not saved");
                game.options.guiScale().set(2);game.resizeDisplay();
                ClientPreferences.get().taskToastScalePercent=46;ClientPreferences.get().taskToastTextScalePercent=100;
                game.setScreen(hud=new HudNotificationLayoutScreen(null));wait=3;
            }
            case 24 -> {
                capture(game,"notification-layout-editor-zh-1920");
                Files.createDirectories(OUT); Files.writeString(OUT.resolve("generation-template-result.txt"),"PASS: first-world offer and cancel; persisted acknowledgement; reopen command; advancement recipe chain and useful rewards; idempotent generation; empty template group network catalog; long-press template reorder; auto-complete 16 logs without consumption; repeated move with animations and stable toolbar; progress-mode daily anecdote scheduler enablement; dimension progression gates; daily 1-4 structure anecdotes, next-day linked follow-up records, village bell/item conditions, scheduler save/load and same-day idempotence; normal-world structure locate, arrival completion, network marker and shared cached coordinates; HUD minimum-scale/maximum-font row bounds; matching runtime/editor dimensions; movable minimap reservation; compact property scrolling, one-shot preview and saved position. All captured images are verified at 1920x1080.\n");
                wait=2;
            }
        }
        stage++;return false;
    }
    private static void clickFirstScopeRow(Screen screen) throws Exception {
        int left = (int) invoke(screen, "left", new Class<?>[]{});
        int top = (int) invoke(screen, "listTop", new Class<?>[]{});
        check(screen.mouseClicked(left + 100, top + 18, 0), "Generation scope row did not handle selection");
        screen.mouseReleased(left + 100, top + 18, 0);
    }
    private static StyledButton button(Screen s,String id){return s.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast).filter(b->b.styleId().equals(id)).findFirst().orElseThrow(()->new AssertionError("Missing "+id));}
    private static void click(Screen s,StyledButton b){check(b.active,"Inactive "+b.styleId());s.mouseClicked(b.getX()+b.getWidth()/2.,b.getY()+b.getHeight()/2.,0);s.mouseReleased(b.getX()+b.getWidth()/2.,b.getY()+b.getHeight()/2.,0);}
    private static boolean dependsOn(QuestDefinition quest,String target,Set<String> visited){
        if(quest==null||!visited.add(quest.id()))return false;
        if(quest.parents().contains(target))return true;
        return quest.parents().stream().map(QuestRepository::find)
                .anyMatch(parent->dependsOn(parent,target,visited));
    }
    private static boolean sameExceptGeneratedRewards(String first, String second) {
        var left = com.google.gson.JsonParser.parseString(first).getAsJsonObject().deepCopy();
        var right = com.google.gson.JsonParser.parseString(second).getAsJsonObject().deepCopy();
        for (String key : List.of("rewards", "generatedRewards")) {
            left.remove(key);
            right.remove(key);
        }
        return left.equals(right);
    }
    private static boolean hasMalformedGeneratedRewardArray(com.google.gson.JsonArray rewards) {
        if (rewards == null) return false;
        var inlineTableIds = new HashSet<String>();
        for (var value : rewards) {
            if (!value.isJsonObject()) continue;
            var reward = value.getAsJsonObject();
            if (!reward.has("type") || !reward.has("item")) continue;
            String type = reward.get("type").getAsString();
            String item = reward.get("item").getAsString();
            if (type.equals("item") && (item.isBlank() || item.startsWith("inline:"))) return true;
            if (!type.equals("loot") || !item.startsWith("inline:")) continue;
            try {
                String tableId = com.google.gson.JsonParser.parseString(item.substring("inline:".length()))
                        .getAsJsonObject().get("id").getAsString();
                if (!inlineTableIds.add(tableId)) return true;
            } catch (RuntimeException invalidInlineLoot) {
                return true;
            }
        }
        return false;
    }
    private static java.lang.reflect.Field reflected(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
    private static Object field(Object o,String n)throws Exception{return o instanceof Class<?> c ? staticField(c,n) : reflected(o,n).get(o);}
    private static Object staticField(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    private static void set(Object o,String n,Object v)throws Exception{reflected(o,n).set(o,v);}
    private static Object invoke(Object o,String n,Class<?>[] types,Object...args)throws Exception{Class<?> type=o instanceof Class<?> c?c:o.getClass();var m=type.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o instanceof Class<?>?null:o,args);}
    private static void capture(Minecraft g,String name)throws Exception{Files.createDirectories(OUT);try(var i=Screenshot.takeScreenshot(g.getMainRenderTarget())){check(i.getWidth()==1920&&i.getHeight()==1080,"Screenshot is not 1920x1080: "+name);i.writeToFile(OUT.resolve(name+".png"));}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message+" at generation stage "+stage);}
}
