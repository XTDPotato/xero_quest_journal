package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class RuntimeRegression {
    private static final String PREFIX = "runtime_" + System.currentTimeMillis();
    private static int stage;
    private static int wait;
    private static CompletableFuture<Void> pending;
    private static CompletableFuture<Void> reload;
    private static QuestScreen journal;
    private static final String GUI = PREFIX + "_gui";
    private static int searchSettingsPhase;

    static boolean tick(Minecraft game) throws Exception {
        game.getToasts().clear();
        game.gui.getChat().clearMessages(true);
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                if (game.screen != null) return false;
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                reload = game.reloadResourcePacks();
                pending = CompletableFuture.runAsync(() -> {
                    try { checks(game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID())); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone() || !reload.isDone()) return false;
                pending.join();
                reload.join();
                if (ClientQuestDefinitions.find(GUI) == null) return false;
                if (journal == null) {
                    var preferences = ClientPreferences.get();
                    preferences.questSearchScopes = Set.of("title", "description");
                    preferences.questSearchFilters = Map.of();
                    preferences.lastSelectedQuestId = "";
                    preferences.lastSelectedQuestCategory = "active";
                    preferences.save();
                    journal = new QuestScreen();
                    game.setScreen(journal);
                }
                if (searchSettingsPhase == 0) {
                    button(game, "search-settings").onPress();
                    require(game.screen instanceof QuestSearchSettingsScreen,
                            "Search settings did not open from the task search field");
                    require(game.screen.children().stream().filter(Checkbox.class::isInstance).count() == 4,
                            "Search settings did not show the four searchable task fields");
                    searchSettingsPhase = 1;
                    wait = 12;
                    return false;
                }
                if (searchSettingsPhase == 1) {
                    capture(game, "runtime-search-settings");
                    button(game, "search-settings-filters").onPress();
                    require(game.screen instanceof QuestSearchFilterScreen,
                            "Advanced task filters did not open");
                    searchSettingsPhase = 2;
                    wait = 12;
                    return false;
                }
                if (searchSettingsPhase == 2) {
                    require(game.screen instanceof QuestSearchFilterScreen,
                            "Advanced task filter screen did not remain open");
                    capture(game, "runtime-search-filters");
                    require((double) field(field(game.screen, "scroll"), "maximum") > 0,
                            "Advanced task filter list did not enable smooth scrolling");
                    var selectedFilters = Map.of(
                            "reward_type", Set.of("item"),
                            "reward_item", Set.of("minecraft:emerald"),
                            "condition_type", Set.of("confirm"));
                    set(game.screen, "draft", selectedFilters);
                    button(game, "search-filter-apply").onPress();
                    require(ClientPreferences.get().questSearchFilters.equals(selectedFilters),
                            "Search filter selections were not persisted");
                    var fixture = QuestRepository.find(GUI);
                    require(QuestSearchFilters.matchesContent(fixture, selectedFilters),
                            "Combined reward and condition filters rejected a matching task");
                    require(!QuestSearchFilters.matchesContent(fixture, Map.of(
                                    "reward_type", Set.of("item"), "reward_item", Set.of("minecraft:diamond"))),
                            "Reward-item filter accepted an unrelated item");
                    ClientPreferences.get().questSearchFilters = Map.of();
                    ClientPreferences.get().save();
                    call(journal, "searchConfigurationChanged", new Class<?>[0]);
                    searchSettingsPhase = 3;
                    wait = 12;
                    return false;
                }
                if (game.screen instanceof QuestSearchSettingsScreen) {
                    button(game, "search-settings-close").onPress();
                    wait = 12;
                    return false;
                }
                if (game.screen != journal) return false;
                searchSettingsPhase = 4;
                game.setScreen(new QuestRewardsScreen(journal, GUI));
            }
            case 2 -> {
                capture(game, "runtime-rewards");
                require(game.screen.children().stream().anyMatch(w -> w instanceof StyledChoiceButton<?>),
                        "No reward type selector");
                game.setScreen(new QuestConditionsScreen(journal, GUI));
                button(game, "condition-type-0").onPress();
            }
            case 3 -> {
                capture(game, "runtime-conditions");
                var conditionType = (StyledChoiceButton<?>) button(game, "condition-type-0");
                require(conditionType.isOpen(), "Condition type popup did not open");
                @SuppressWarnings("unchecked") var groups =
                        (Map<String, String>) field(conditionType, "optionGroups");
                require(groups.values().containsAll(List.of("manual", "inventory", "combat", "world",
                                "quest", "events", "logic")),
                        "Condition type popup is missing its visual option groups");
                conditionType.onPress();
                var edits = fields(game);
                require(edits.size() >= 2, "No completion condition inputs");
                button(game, "conditions-add").onPress();
                button(game, "conditions-save").onPress();
            }
            case 4 -> {
                if (game.screen instanceof QuestConditionsScreen) {
                    require(field(game.screen, "error") == null, "Condition GUI save failed");
                    return false;
                }
                require(game.screen == journal, "Condition editor lost parent screen: "
                        + (game.screen == null ? "null" : game.screen.getClass().getSimpleName()));
                require(QuestRepository.find(GUI).triggers().size() == 2, "Condition editor did not persist draft");
                game.options.guiScale().set(1);
                game.resizeDisplay();
                game.setScreen(new QuestRewardsScreen(journal, GUI));
            }
            case 5 -> {
                capture(game, "runtime-rewards-wide");
                game.setScreen(new QuestConditionsScreen(journal, GUI));
            }
            case 6 -> {
                capture(game, "runtime-conditions-wide");
                game.options.guiScale().set(2);
                game.resizeDisplay();
                game.setScreen(journal);
            }
            case 7 -> {
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                        player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                    require(QuestRuntime.activate(player, GUI).equals("completed"), "GUI task completion failed");
                }, game.getSingleplayerServer());
            }
            case 8 -> {
                if (!pending.isDone()) return false;
                pending.join();
                var view = ClientQuestProgress.view(GUI);
                if (view == null || !view.status().equals("complete")) return false;
                if (ClientQuestProgress.completionNotices().stream()
                        .anyMatch(notice -> notice.questId().equals(GUI))) return false;
                EditorRegression.reveal(game, GUI);
                var taskRow = game.screen.children().stream().filter(JournalListButton.class::isInstance)
                        .map(JournalListButton.class::cast)
                        .filter(row -> row.styleId().equals("quest-" + GUI)).findFirst().orElseThrow();
                double selectX = taskRow.getX() + 30;
                double selectY = taskRow.getY() + taskRow.getHeight() / 2.0;
                require(game.screen.mouseClicked(selectX, selectY, 0),
                        "Could not select the completed task before closing the journal");
                game.screen.mouseReleased(selectX, selectY, 0);
                require(field(game.screen, "selectedId").equals(GUI),
                        "Selecting a task did not update the journal selection");
                ((QuestScreen) game.screen).onClose();
                journal = QuestScreen.resume(null);
                require(field(journal, "selectedId").equals(GUI)
                                && field(journal, "category").equals("active"),
                        "Closing and reopening reset the selected task: selected="
                                + field(journal, "selectedId") + ", category=" + field(journal, "category")
                                + ", savedId=" + ClientPreferences.get().lastSelectedQuestId
                                + ", savedCategory=" + ClientPreferences.get().lastSelectedQuestCategory);
                game.setScreen(journal);
            }
            case 9 -> {
                var taskRow = game.screen.children().stream().filter(JournalListButton.class::isInstance)
                        .map(JournalListButton.class::cast)
                        .filter(row -> row.styleId().equals("quest-" + GUI)).findFirst().orElseThrow();
                require(taskRow.claimable(), "Completed-unclaimed task has no claim marker");
                require(QuestScreen.matchesCategory(QuestRepository.find(GUI), "active")
                                && !QuestScreen.matchesCategory(QuestRepository.find(GUI), "completed"),
                        "Completed-unclaimed task left In Progress before it was claimed");
                double claimX = taskRow.getRight() - 10;
                double claimY = taskRow.getY() + taskRow.getHeight() / 2.0;
                require(game.screen.mouseClicked(claimX, claimY, 0),
                        "Clicking the list claim icon did not start reward claiming");
                require(game.screen == journal && taskRow.claimPressed(),
                        "Claim icon executed on press instead of showing the held highlight");
                game.screen.mouseReleased(claimX, claimY, 0);
                require(!taskRow.claimPressed(), "Claim held highlight stuck after release");
            }
            case 10 -> {
                if (!(game.screen != null && game.screen.getClass().getSimpleName().equals("QuestConfirmScreen")))
                    return false;
                capture(game, "runtime-overflow");
                button(game, "overflow-cancel").onPress();
            }
            case 11 -> {
                if (!(game.screen instanceof QuestScreen)) return false;
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(!QuestProgressData.get(player.getServer()).entry(player.getUUID(), QuestRepository.find(GUI)).claimed(),
                            "Cancel consumed entitlement");
                }, game.getSingleplayerServer());
            }
            case 12 -> {
                if (!pending.isDone()) return false;
                pending.join();
                button(game, "claim").onPress();
            }
            case 13 -> {
                if (!(game.screen != null && game.screen.getClass().getSimpleName().equals("QuestConfirmScreen")))
                    return false;
                button(game, "overflow-confirm").onPress();
            }
            case 14 -> {
                var view = ClientQuestProgress.view(GUI);
                if (view == null || !view.status().equals("claimed")) return false;
                require(!QuestScreen.matchesCategory(QuestRepository.find(GUI), "active")
                                && QuestScreen.matchesCategory(QuestRepository.find(GUI), "completed"),
                        "Claimed task did not move to Completed");
                return true;
            }
            default -> throw new AssertionError("Invalid runtime stage");
        }
        stage++;
        wait = 15;
        return false;
    }

    private static void checks(ServerPlayer player) throws Exception {
        var unauthorized = new EntityTravelToDimensionEvent(player, QuestStructures.DIMENSION);
        QuestStructureEvents.travel(unauthorized);
        require(unauthorized.isCanceled(), "Player entered another scene without a session");
        var outsideItem = new net.minecraft.world.entity.item.ItemEntity(player.level(),
                player.getX(), player.getY(), player.getZ(), new ItemStack(net.minecraft.world.item.Items.STONE));
        var incomingItem = new EntityTravelToDimensionEvent(outsideItem, QuestStructures.DIMENSION);
        QuestStructureEvents.travel(incomingItem);
        require(incomingItem.isCanceled(), "Outside entity entered the scene dimension");
        Path structureRoot = QuestConfig.root().resolve("structures");
        Files.createDirectories(structureRoot);
        CompoundTag template = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag();
        for (int i = 0; i < 3; i++) {
            size.add(IntTag.valueOf(1));
            position.add(IntTag.valueOf(0));
        }
        template.put("size", size);
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        ListTag palette = new ListTag();
        palette.add(state);
        template.put("palette", palette);
        CompoundTag block = new CompoundTag();
        block.put("pos", position);
        block.putInt("state", 0);
        ListTag blocks = new ListTag();
        blocks.add(block);
        template.put("blocks", blocks);
        template.put("entities", new ListTag());
        String reference = "runtime-travel.nbt";
        NbtIo.writeCompressed(template, structureRoot.resolve(reference));
        try {
            QuestStructures.enter(player, reference);
            require(QuestStructures.active(player) && player.level().dimension().equals(QuestStructures.DIMENSION),
                    "Authorized scene entry was blocked");
            var escape = new EntityTravelToDimensionEvent(player, Level.OVERWORLD);
            QuestStructureEvents.travel(escape);
            require(escape.isCanceled(), "Active scene player bypassed return flow");
            QuestStructures.leave(player);
            require(!QuestStructures.active(player) && player.level().dimension().equals(Level.OVERWORLD),
                    "Authorized scene return was blocked");
        } finally {
            if (QuestStructures.active(player)) QuestStructures.leave(player);
            Files.deleteIfExists(structureRoot.resolve(reference));
        }
        Files.createDirectories(QuestConfig.root().resolve("scripts"));
        AtomicFiles.write(QuestConfig.root().resolve("scripts/runtime-test.js"),
                "command('tag @s add xero_frozen_script'); true;", true);
        AtomicFiles.write(QuestConfig.root().resolve("scripts/runtime-condition.js"),
                "typeof Packages === 'undefined' && typeof java === 'undefined' && typeof command === 'undefined';", true);
        AtomicFiles.write(QuestConfig.root().resolve("scripts/runtime-fail.js"),
                "command('experience add @s 3 points'); throw new Error('expected runtime regression failure');", true);

        String unlock = create(player, "unlock", List.of(new Trigger("confirm", "", 1)), List.of());
        String completed = create(player, "completed", List.of(new Trigger("confirm", "", 1)), List.of());
        List<Reward> mixed = List.of(
                new Reward("item", "minecraft:diamond", 2), new Reward("experience", "", 10),
                new Reward("levels", "", 2), new Reward("command", "execute as @s run tag @s add xero_reward_command", 1),
                new Reward("function", "xero_quest_journal:runtime_reward", 1),
                new Reward("script", "runtime-test.js", 1), new Reward("effect", "minecraft:speed", 5),
                new Reward("unlock", unlock, 1), new Reward("complete", completed, 1),
                new Reward("message", "Runtime regression", 1));
        String id = create(player, "mixed", List.of(new Trigger("confirm", "", 1)), mixed);
        int xp = player.totalExperience;
        int levels = player.experienceLevel;
        require(QuestRuntime.activate(player, id).equals("completed"), "Mixed completion");
        AtomicFiles.write(QuestConfig.root().resolve("scripts/runtime-test.js"),
                "command('tag @s add xero_modified_script');", true);
        require(QuestRuntime.claim(player, id).equals("claimed"), "Mixed reward claim");
        require(player.totalExperience == xp + 10 && player.experienceLevel >= levels + 2, "Experience rewards");
        require(player.getTags().containsAll(Set.of("xero_reward_command", "xero_frozen_script",
                        "xero_function_reward", "xero_function_execute"))
                && !player.getTags().contains("xero_modified_script"),
                "Command or frozen script not executed; tags=" + player.getTags());
        require(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "Effect reward missing");
        var data = QuestProgressData.get(player.getServer());
        require(data.unlocked(player.getUUID(), QuestRepository.find(unlock)), "Unlock reward missing");
        require(data.entry(player.getUUID(), QuestRepository.find(completed)) != null, "Complete reward missing");
        require(QuestRuntime.claim(player, id).equals("already_claimed"), "Duplicate claim");
        player.removeTag("xero_function_reward");
        String fromCommand = create(player, "command_claim", List.of(new Trigger("confirm", "", 1)),
                List.of(new Reward("function", "xero_quest_journal:runtime_reward", 1)));
        require(QuestRuntime.activate(player, fromCommand).equals("completed"), "Command claim completion");
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),
                "xero_quest reward claim " + fromCommand);
        require(player.getTags().contains("xero_function_reward")
                && QuestProgressData.get(player.getServer()).entry(player.getUUID(),
                QuestRepository.find(fromCommand)).claimed(), "Nested command receipt completed before function");
        String invalidCommand = create(player, "invalid_command", List.of(new Trigger("confirm", "", 1)),
                List.of(new Reward("command", "function xero_quest_journal:missing_runtime_function", 1)));
        require(QuestRuntime.activate(player, invalidCommand).equals("completed"), "Invalid command fixture");
        require(QuestRuntime.claim(player, invalidCommand).equals("reward_failed")
                && QuestRuntime.claim(player, invalidCommand).equals("reward_failed"), "Missing function was not blocked");
        String resetItem = create(player, "reset_item", List.of(new Trigger("confirm", "", 1)),
                List.of(new Reward("item", "minecraft:diamond", 2)));
        var resetQuest = QuestRepository.find(resetItem);
        data.lock(player.getUUID(), resetQuest.progressKey());
        require(!QuestRuntime.unlocked(data, player, resetQuest), "Locked reset fixture remained available");
        require(QuestRuntime.reset(player, resetItem).equals("reset")
                        && QuestRuntime.unlocked(data, player, resetQuest)
                        && !data.explicitlyLocked(player.getUUID(), resetQuest.progressKey()),
                "Reset did not restore the default-unlocked state");
        int diamondsBefore = player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND);
        require(QuestRuntime.activate(player, resetItem).equals("completed")
                && QuestRuntime.claim(player, resetItem).equals("claimed"), "First item claim failed");
        require(QuestRuntime.reset(player, resetItem).equals("reset"), "Progress reset");
        require(QuestRuntime.activate(player, resetItem).equals("completed")
                && QuestRuntime.claim(player, resetItem).equals("claimed"), "Reset blocked reward");
        require(player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == diamondsBefore + 4,
                "Reset claim did not grant items again");
        require(QuestRuntime.claim(player, resetItem).equals("already_claimed"), "Reset claim was duplicated");
        require(!QuestCatalog.serverJson().contains("xero_reward_command")
                && !QuestCatalog.serverJson().contains("runtime-test.js"), "Catalog leaked executable source");
        require(QuestRuntime.snapshot(player).get(id).rewards().stream()
                .filter(r -> r.type().equals("command") || r.type().equals("script"))
                .allMatch(r -> r.item().isEmpty()), "Snapshot leaked executable source");

        require(Boolean.TRUE.equals(QuestScripts.run(player, QuestScripts.source("runtime-condition.js"), true)),
                "Script exposed Java or reward host functions");
        boolean budget = false;
        try { QuestScripts.run(player, "while (true) {}", true); }
        catch (IllegalArgumentException expected) { budget = true; }
        require(budget, "Infinite script was not interrupted");
        require(!QuestRewards.valid(new Reward("script", "../outside.js", 1)), "Script traversal allowed");
        AtomicFiles.write(QuestConfig.root().resolve("scripts/runtime-oversized.js"),
                "//" + "\uD834\uDD1E".repeat(11000), true);
        require(!QuestRewards.valid(new Reward("script", "runtime-oversized.js", 1)),
                "Accepted script that cannot be persisted in NBT");
        require(!QuestRewards.valid(new Reward("command", "say hello\nstop", 1)), "Multiline command accepted");
        require(!QuestRewards.valid(new Reward("structure", "minecraft:igloo", 1)), "Fake structure support");

        String failed = create(player, "failed", List.of(new Trigger("confirm", "", 1)),
                List.of(new Reward("experience", "", 7), new Reward("script", "runtime-fail.js", 1)));
        require(QuestRuntime.activate(player, failed).equals("completed"), "Failure fixture completion");
        int beforeFailure = player.totalExperience;
        require(QuestRuntime.claim(player, failed).equals("reward_failed"), "Script failure not recorded");
        require(player.totalExperience == beforeFailure + 10, "Partial execution mismatch");
        require(QuestRuntime.claim(player, failed).equals("reward_failed")
                && player.totalExperience == beforeFailure + 10, "Failed script was retried");
        var failedQuest = QuestRepository.find(failed);
        require(data.receipt(player.getUUID(), failedQuest, 0).equals("done")
                && data.receipt(player.getUUID(), failedQuest, 1).equals("failed"), "Missing per-reward receipts");

        var dispatcher = player.getServer().getCommands().getDispatcher();
        var op = player.createCommandSourceStack().withPermission(2);
        require(dispatcher.execute("xero_quest reward acknowledge @s " + failed + " 1", op) == 1,
                "Receipt acknowledgement failed");
        require(QuestRuntime.claim(player, failed).equals("claimed") && player.totalExperience == beforeFailure + 10,
                "Acknowledgement repeated reward");
        boolean denied = false;
        try { dispatcher.execute("xero_quest trigger complete @s " + unlock, op.withPermission(0)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        require(denied, "Unprivileged trigger allowed");
        require(dispatcher.execute("xero_quest trigger complete @s " + unlock, op) == 1, "Admin trigger failed");

        var nested = new Trigger("all", "", 1, List.of(
                new Trigger("any", "", 1, List.of(new Trigger("dimension", "minecraft:the_nether", 1),
                        new Trigger("dimension", "minecraft:overworld", 1))),
                new Trigger("ratio", "", 50, List.of(new Trigger("quest", completed, 1),
                        new Trigger("dimension", "minecraft:the_end", 1))),
                new Trigger("script", "runtime-condition.js", 1)));
        String combination = create(player, "nested", List.of(nested), List.of());
        QuestRuntime.evaluate(player);
        require(data.entry(player.getUUID(), QuestRepository.find(combination)) != null, "Nested conditions failed");

        clear(player);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
        String submit = create(player, "submit", List.of(new Trigger("all", "", 1, List.of(
                new Trigger("item_submit", "#minecraft:logs", 3),
                new Trigger("item_submit", "minecraft:oak_log", 3)))), List.of());
        require(QuestRuntime.activate(player, submit).equals("insufficient")
                && player.getInventory().getItem(0).getCount() == 4, "Overlapping submission partially consumed");
        player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 6));
        require(QuestRuntime.activate(player, submit).equals("completed") && player.getInventory().getItem(0).isEmpty(),
                "Atomic nested submission failed");

        player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 1));
        player.getInventory().setItem(1, new ItemStack(Items.BIRCH_LOG, 1));
        String alternative = create(player, "alternative", List.of(new Trigger("all", "", 1, List.of(
                new Trigger("any", "", 1, List.of(new Trigger("item_submit", "minecraft:oak_log", 1),
                        new Trigger("item_submit", "minecraft:birch_log", 1))),
                new Trigger("item_submit", "minecraft:oak_log", 1)))), List.of());
        require(QuestRuntime.activate(player, alternative).equals("completed"), "Alternative submission plan failed");

        String eventId = create(player, "event", List.of(new Trigger("event", "test_event", 3)), List.of());
        require(dispatcher.execute("xero_quest event event @s test_event 2", op) == 1, "Event command failed");
        require(data.entry(player.getUUID(), QuestRepository.find(eventId)) == null, "Event completed too early");
        require(QuestRuntime.snapshot(player).get(eventId).current() == 2, "Event progress not displayed");
        require(dispatcher.execute("xero_quest event event @s test_event 1", op) == 1
                && data.entry(player.getUUID(), QuestRepository.find(eventId)) != null, "Event threshold not completed");
        String jumpId = create(player, "jump_trigger", List.of(new Trigger("event", "jump", 2)), List.of());
        QuestEventGraphs.fire(player, "jump", "");
        require(QuestRuntime.snapshot(player).get(jumpId).current() == 1, "Jump trigger not counted");
        QuestEventGraphs.fire(player, "jump", "");
        QuestRuntime.evaluate(player);
        require(data.entry(player.getUUID(), QuestRepository.find(jumpId)) != null, "Jump trigger did not complete");
        String flagged = create(player, "flags", List.of(new Trigger("event", "jump", 2)), List.of());
        var flaggedQuest = QuestRepository.find(flagged);
        require(!flaggedQuest.hidden() && !flaggedQuest.hideWhenLocked() && flaggedQuest.triggersEnabled(),
                "Legacy task flags changed");
        require(QuestRuntime.setTaskProperty(player, flagged, "hideWhenLocked", true), "Hide-when-locked override");
        require(QuestRuntime.trigger(player, flagged, "lock", false).equals("locked")
                && QuestRuntime.snapshot(player).get(flagged).hidden(), "Locked task remained visible");
        require(QuestRuntime.trigger(player, flagged, "unlock", false).equals("unlocked")
                && !QuestRuntime.snapshot(player).get(flagged).hidden(), "Unlocked task remained hidden");
        require(QuestRuntime.toggleTracking(player, flagged).equals("tracked"), "Tracking fixture failed");
        require(QuestRuntime.setTaskProperty(player, flagged, "hidden", true)
                && QuestRuntime.snapshot(player).get(flagged).hidden()
                && !QuestRuntime.tracked(player).contains(flagged), "Hidden task remained tracked");
        require(QuestRuntime.toggleTracking(player, flagged).equals("locked"), "Hidden task can be tracked");
        require(QuestRuntime.setTaskProperty(player, flagged, "hidden", false), "Show task override");
        require(QuestRuntime.setTaskProperty(player, flagged, "triggersEnabled", false), "Disable triggers");
        QuestEventGraphs.fire(player, "jump", "");
        QuestRuntime.evaluate(player);
        require(QuestRuntime.snapshot(player).get(flagged).current() == 0
                && data.entry(player.getUUID(), flaggedQuest) == null, "Disabled task counted or completed");
        require(QuestRuntime.setTaskProperty(player, flagged, "triggersEnabled", true), "Enable triggers");
        QuestEventGraphs.fire(player, "jump", "");
        require(QuestRuntime.snapshot(player).get(flagged).current() == 1, "Re-enabled task did not count");
        require(!QuestRuntime.setTaskProperty(player, flagged, "unknown", true), "Unknown task property accepted");
        String manualFlags = create(player, "manual_flags", List.of(new Trigger("confirm", "", 1)), List.of());
        require(QuestRuntime.setTaskProperty(player, manualFlags, "triggersEnabled", false)
                && QuestRuntime.activate(player, manualFlags).equals("triggers_disabled"),
                "Disabled manual task could be completed");
        require(QuestRuntime.setTaskProperty(player, manualFlags, "triggersEnabled", true)
                && QuestRuntime.activate(player, manualFlags).equals("completed"),
                "Re-enabled manual task could not complete");
        String acquire = create(player, "acquire", List.of(new Trigger("item_acquire", "minecraft:diamond", 3)), List.of());
        QuestConditions.record(player, "item_acquire", "minecraft:diamond", 2);
        QuestRuntime.evaluate(player);
        require(data.entry(player.getUUID(), QuestRepository.find(acquire)) == null, "Acquisition completed too early");
        QuestConditions.record(player, "item_acquire", "minecraft:diamond", 1);
        QuestRuntime.evaluate(player);
        require(data.entry(player.getUUID(), QuestRepository.find(acquire)) != null, "Acquisition counter missing");
        String kills = create(player, "kills", List.of(new Trigger("kill", "minecraft:zombie", 2)), List.of());
        var zombie = new net.minecraft.world.entity.monster.Zombie(player.serverLevel());
        QuestConditionEvents.killed(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(
                zombie, player.damageSources().playerAttack(player)));
        QuestConditionEvents.killed(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(
                zombie, player.damageSources().playerAttack(player)));
        QuestRuntime.evaluate(player);
        require(data.entry(player.getUUID(), QuestRepository.find(kills)) != null, "Kill events not counted");

        List<String> baselineTasks = QuestRepository.all().stream().map(QuestDefinition::id).toList();
        data.observeTaskCatalog(player.getUUID(), baselineTasks);
        require(!baselineTasks.isEmpty() && !data.isNewTask(player.getUUID(), baselineTasks.getFirst()),
                "Initial catalog was marked new");
        var expandedTasks = new ArrayList<>(baselineTasks);
        expandedTasks.add("new_task_" + PREFIX);
        data.observeTaskCatalog(player.getUUID(), expandedTasks);
        require(data.isNewTask(player.getUUID(), expandedTasks.getLast()), "New catalog task was not marked unread");
        var saved = data.save(new CompoundTag(), player.registryAccess());
        var loaded = QuestProgressData.load(saved, player.registryAccess());
        require(loaded.isNewTask(player.getUUID(), expandedTasks.getLast()), "Unread task state persistence");
        require(loaded.markTaskRead(player.getUUID(), expandedTasks.getLast())
                && !loaded.isNewTask(player.getUUID(), expandedTasks.getLast()), "Read task state did not clear");
        data.markTaskRead(player.getUUID(), expandedTasks.getLast());
        require(loaded.receipt(player.getUUID(), failedQuest, 1).equals("done"), "Receipt persistence");
        require(loaded.counter(player.getUUID(), QuestConditions.key(QuestRepository.find(eventId),
                new Trigger("event", "test_event", 3))) == 3, "Counter persistence");
        require(loaded.unlocked(player.getUUID(), QuestRepository.find(unlock)), "Unlock persistence");
        require(loaded.taskProperty(player.getUUID(), flaggedQuest, "hideWhenLocked", false)
                && !loaded.taskProperty(player.getUUID(), flaggedQuest, "hidden", true)
                && loaded.taskProperty(player.getUUID(), flaggedQuest, "triggersEnabled", false),
                "Task property persistence");
        String flagsSource = QuestDocuments.taskFlags(QuestRepository.source(flagged), true, true, false);
        var parsedFlags = QuestRepository.parseDefinition(QuestRepository.fileOf(flagged), flagsSource);
        require(parsedFlags.hideWhenLocked() && parsedFlags.hidden() && !parsedFlags.triggersEnabled(),
                "Task flag markup round trip");
        var jsonFlags = QuestRepository.parseDefinition(Path.of("flags.json"),
                "{\"id\":\"flags\",\"title\":\"Flags\",\"triggersEnabled\":false,\"hideWhenLocked\":true,\"hidden\":true}");
        require(jsonFlags.hideWhenLocked() && jsonFlags.hidden() && !jsonFlags.triggersEnabled(),
                "Task flag JSON round trip");
        require(QuestRepository.parseDefinition(Path.of("legacy.json"),
                "{\"id\":\"legacy\",\"title\":\"Legacy\"}").triggersEnabled(),
                "Legacy JSON disabled triggers");

        clear(player);
        List<Reward> guiRewards = List.of(new Reward("item", "minecraft:emerald", 2, "epic", "#7450A6"),
                new Reward("experience", "", 5), new Reward("command", "tag @s add runtime_gui", 1));
        create(player, "gui", List.of(new Trigger("confirm", "", 1)), guiRewards);
        String before = QuestRepository.source(GUI);
        require(!QuestAuthoring.editConditions(null, GUI, QuestRepository.fingerprint(before), List.of(nested)).successful(),
                "Unauthenticated condition authoring");
        require(!QuestAuthoring.editConditions(player, GUI, "stale", List.of(nested)).successful(), "Stale conditions accepted");
        require(!QuestAuthoring.editConditions(player, GUI, QuestRepository.fingerprint(before),
                List.of(new Trigger("any", "", 1, List.of()))).successful(), "Empty condition group accepted");
        String roundtrip = QuestDocuments.conditions(before, List.of(nested));
        require(QuestRepository.parseMarkup(roundtrip, true).triggers().equals(List.of(nested)), "Condition DOM round trip");
        require(QuestRepository.parseMarkup(roundtrip, true).rewards().equals(guiRewards), "Conditions damaged rewards");
        var jsonReward = QuestRepository.parseDefinition(Path.of("styled.json"),
                "{\"id\":\"styled\",\"title\":\"Styled\",\"rewards\":[{\"type\":\"item\","
                        + "\"item\":\"minecraft:emerald\",\"count\":2,\"quality\":\"epic\","
                        + "\"background\":\"#7450A6\"}]}").rewards().getFirst();
        require(jsonReward.equals(guiRewards.getFirst()), "Styled reward JSON round trip");
        var snapshotView = new QuestRuntime.View("active", 0, 1, List.of(guiRewards.getFirst()),
                false, true, false, 0, 0, true);
        var packet = new QuestNetwork.ProgressSnapshotPayload(Map.of("styled", snapshotView), List.of());
        var payloadBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), player.registryAccess());
        try {
            QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.encode(payloadBuffer, packet);
            var decoded = QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.decode(payloadBuffer);
            require(decoded.views().get("styled").newTask()
                            && decoded.views().get("styled").rewards().getFirst().equals(guiRewards.getFirst()),
                    "Progress snapshot lost unread state or reward quality");
        } finally {
            payloadBuffer.release();
        }
        Class<?> cardRenderer = Class.forName("xtdpotato.xero_quest_journal.client.RewardCardRenderer");
        var cardHeight = cardRenderer.getDeclaredField("HEIGHT");
        cardHeight.setAccessible(true);
        require(cardHeight.getInt(null) == 38, "Reward card height did not gain four pixels");
        var rarity = cardRenderer.getDeclaredMethod("rarity", String.class, String.class);
        rarity.setAccessible(true);
        for (int tier = 1; tier <= 6; tier++) {
            String quality = List.of("", "common", "uncommon", "rare", "epic", "legendary", "mythic").get(tier);
            require((int) rarity.invoke(null, "minecraft:diamond", quality) == tier,
                    "Reward quality mapped to the wrong card tier: " + quality);
        }
        var complexRewards = List.of(new Reward("command", "tag @s add xero_properties", 1));
        String properties = create(player, "properties", List.of(nested), complexRewards);
        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, properties, "Changed properties",
                "Regression", "", "", "guide", "", 1, "", "", "tag @s add xero_properties", 1, "automatic");
        require(QuestAuthoring.edit(player, request, hash(properties), false, true).successful(),
                "Compact form rejected a command-first reward");
        var edited = QuestRepository.find(properties);
        require(edited.triggers().equals(List.of(nested)) && edited.rewards().equals(complexRewards)
                && edited.rewardMode().equals("automatic"), "Compact form damaged complex rules");
    }

    private static String create(ServerPlayer player, String suffix, List<Trigger> triggers, List<Reward> rewards) throws Exception {
        String id = PREFIX + "_" + suffix;
        require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                id, "Runtime " + suffix, "Regression", "", "", "guide", "", 1)).successful(), "Create " + suffix);
        require(QuestAuthoring.editConditions(player, id, hash(id), triggers).successful(), "Conditions " + suffix);
        require(QuestAuthoring.editRewards(player, id, hash(id), rewards, "manual").successful(), "Rewards " + suffix);
        return id;
    }
    private static String hash(String id) throws Exception { return QuestRepository.fingerprint(QuestRepository.source(id)); }
    private static void clear(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) player.getInventory().setItem(i, ItemStack.EMPTY);
    }
    private static List<EditBox> fields(Minecraft game) {
        return game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }
    private static StyledButton button(Minecraft game, String id) throws Exception {
        var found = game.screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(b -> b.styleId().equals(id)).findFirst().orElse(null);
        if (found != null) return found;
        var view = ClientQuestProgress.view(GUI);
        String selected = String.valueOf(field(game.screen, "selectedId"));
        var styles = game.screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).map(StyledButton::styleId).toList();
        String requested = String.valueOf(field(game.screen, "requestedInitialTask"));
        throw new AssertionError("Missing " + id + " on " + game.screen.getClass().getSimpleName()
                + "; selected=" + selected + ", requested=" + requested
                + ", catalog=" + (ClientQuestDefinitions.find(GUI) != null)
                + ", status=" + (view == null ? "null" : view.status())
                + ", buttons=" + styles);
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("../build/verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(output.resolve(name + ".png")); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
