package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.mailbox.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class RewardInspectionRegression {
    private static final String TASK = "inspect_claimed_task", MAIL = "inspect_claimed_mail", LOOT = "inspect_table",
            OBJECTIVE = "inspect_objective_task";
    private static CompletableFuture<Void> setup, language;
    private static QuestScreen journal;
    private static MailboxScreen mailbox;
    private static int stage, wait, inventory;
    private static boolean itemTooltipHovered;
    private static boolean experienceTooltipHovered;
    private static List<QuestDefinition.Reward> choices;
    private static QuestDefinition.Reward frozenChoice;
    private static QuestDefinition.Reward largeChoice;

    static boolean tick(Minecraft game) throws Exception {
        MailArrivalToast.clear(); QuestUnlockToast.clear(); XeroToast.clear(); game.getToasts().clear();
        if (--wait > 0) return false;
        wait = 6;
        if (game.screen instanceof AnimatedQuestScreen screen && screen.inputBlocked()) return false;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                ClientEditMode.set(false);
                game.options.guiScale().set(2);
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920); game.getWindow().setHeight(1080); game.resizeDisplay();
                game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                language = game.reloadResourcePacks();
                setup = CompletableFuture.runAsync(() -> {
                    try {
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        var rewards = List.of(new QuestDefinition.Reward("item", "minecraft:diamond", 2, "rare", ""),
                                new QuestDefinition.Reward("experience", "", 120),
                                new QuestDefinition.Reward("levels", "", 2));
                        Files.writeString(QuestRepository.root().resolve(TASK + ".json"), new Gson().toJson(Map.of(
                                "id", TASK, "title", "已领取奖励检查", "type", "world", "rewardMode", "manual",
                                "triggers", List.of(new QuestDefinition.Trigger("confirm", "", 1)), "rewards", rewards)));
                        Files.writeString(QuestRepository.root().resolve(OBJECTIVE + ".json"), new Gson().toJson(Map.of(
                                "id", OBJECTIVE, "title", "查看完成方式", "description", "在背包中持有木棍，点击物品卡查看来源。",
                                "type", "world", "rewardMode", "manual",
                                "triggers", List.of(new QuestDefinition.Trigger("item_hold", "minecraft:stick", 64)),
                                "rewards", rewards)));
                        QuestRepository.reload();
                        QuestRuntime.activate(player, TASK);
                        if (!QuestRuntime.claim(player, TASK).equals("claimed")) throw new AssertionError("Fixture task claim failed");
                        MailboxData.get(player.server).update(player.getUUID(), MAIL, ignored -> null);
                        MailboxRuntime.sendIfAbsent(player, MAIL, "探索协会", "已领取附件检查", "点击底部奖励查看详情。", 0,
                                rewards.stream().map(r -> new MailboxRuntime.Attachment(r.type(), r.item(), r.count(), false)).toList());
                        if (!MailboxRuntime.claim(player, MAIL).equals("claimed")) throw new AssertionError("Fixture mail claim failed");
                        var entries = List.of(new LootDefinition.Entry("diamond", "item", "minecraft:diamond", 2, 30, "always", true),
                                new LootDefinition.Entry("xp", "experience", "", 120, 70, "always", true));
                        QuestLootStore.save(player, new LootDefinition(LOOT, "探索补给", LootDefinition.Mode.RANDOM, 2, entries));
                        QuestLootStore.save(player, new LootDefinition(LOOT + "_choice", "自选补给", LootDefinition.Mode.CHOICE, 1,
                                "选择一份奖励", entries, "minecraft:emerald"));
                        frozenChoice = QuestRewards.freeze(player, List.of(new QuestDefinition.Reward("loot", LOOT + "_choice", 1))).getFirst();
                        var large = java.util.stream.IntStream.range(0, 11).mapToObj(index ->
                                new LootDefinition.Entry("reward_" + index, "item", index == 10 ? "minecraft:emerald" : "minecraft:diamond",
                                        index + 1, 10, "always", true, "", "", index == 10 ? "搜索特有说明" : "补给详情 " + index)).toList();
                        QuestLootStore.save(player, new LootDefinition(LOOT + "_large", "大量奖励", LootDefinition.Mode.CHOICE, 1, large));
                        QuestLootStore.save(player, new LootDefinition(LOOT + "_ten", "十个奖励", LootDefinition.Mode.RANDOM, 1, large.subList(0, 10)));
                        QuestLootStore.reload();
                        require(QuestLootStore.find(LOOT + "_large").entries().get(10).detailText().equals("搜索特有说明"),
                                "Custom details did not survive save/reload");
                        largeChoice = QuestRewards.freeze(player, List.of(new QuestDefinition.Reward("loot", LOOT + "_large", 1))).getFirst();
                        require(QuestRewards.choiceDetail(largeChoice, 10).equals("搜索特有说明"), "Frozen choice lost custom details");
                        var legacy = new Gson().fromJson("{\"id\":\"legacy\",\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":1,\"weight\":1}",
                                LootDefinition.Entry.class);
                        require(legacy.detailText().isEmpty(), "Legacy loot entry did not default its details");
                        choices = rewards;
                        inventory = player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND);
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!setup.isDone() || !language.isDone()) return false;
                setup.join(); language.join();
                ClientMailbox.request();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new QuestNetwork.ProgressRequestPayload(-1));
            }
            case 2 -> {
                if (ClientQuestProgress.view(TASK) == null || ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals(MAIL)))
                    return false;
                if (ClientQuestDefinitions.find(TASK) == null || ClientQuestProgress.view(TASK).rewards().isEmpty()) return false;
                verifyInlineLootTooltip();
                journal = QuestScreen.task(null, TASK); game.setScreen(journal);
            }
            case 3 -> {
                require(ClientQuestProgress.view(TASK).status().equals("claimed"), "Task is not claimed");
                if (field(journal, "selected") == null) {
                    journal.init(game, journal.width, journal.height);
                    return false;
                }
                journal = QuestScreen.task(null, TASK);
                game.setScreen(journal);
                if (field(journal, "selected") == null) return false;
                if ((int) call(journal, "rewardFooterHeight") == 0) {
                    journal.init(game, journal.width, journal.height);
                    return false;
                }
                if (!itemTooltipHovered) {
                    double x = (int) field(journal, "detailsLeft") + 20;
                    double y = (int) call(journal, "detailContentBottom") + 30;
                    moveCursor(game, x, y);
                    itemTooltipHovered = true;
                    wait = 8;
                    return false;
                }
                capture(game, "claimed-task-item-tooltip-zh-1920");
                taskClick(game, 0);
                require(game.screen instanceof QuestRewardPopupScreen, "Claimed task item did not open");
                require(!((net.minecraft.world.item.ItemStack) field(game.screen, "inspectedStack")).isEmpty(),
                        "Claimed task item lost its real icon/identity");
            }
            case 4 -> {
                if (!experienceTooltipHovered) {
                    capture(game, "claimed-task-item-details-zh-1920");
                    game.screen.onClose();
                    require(game.screen == journal, "Task detail return failed");
                    double x = (int) field(journal, "detailsLeft") + 10 + 38 + 10;
                    double y = (int) call(journal, "detailContentBottom") + 30;
                    moveCursor(game, x, y);
                    experienceTooltipHovered = true;
                    wait = 8;
                    return false;
                }
                require(game.screen == journal, "Experience hover left the selected journal");
                require(QuestText.rewardTooltip(new QuestDefinition.Reward("experience", "", 120))
                                .getString().equals("经验点数 ×120")
                                && QuestText.rewardTooltip(new QuestDefinition.Reward("levels", "", 2))
                                .getString().equals("经验等级 ×2"),
                        "Experience reward tooltips omitted their localized type or amount");
                require(QuestRewardsScreen.icon("experience", "").equals("minecraft:experience_bottle")
                                && QuestRewardsScreen.icon("levels", "").equals("minecraft:experience_bottle"),
                        "Experience points/levels reward icon mapping is missing");
                capture(game, "experience-reward-tooltip-zh-1920");
                taskClick(game, 1);
                require(game.screen instanceof QuestRewardPopupScreen, "Task XP did not open");
            }
            case 5 -> {
                capture(game, "experience-reward-details-zh-1920");
                game.screen.onClose();
                mailbox = new MailboxScreen(null, MAIL); game.setScreen(mailbox);
            }
            case 6 -> {
                require(field(mailbox, "selected") != null, "Claimed mail not selected");
                mailClick(0);
                require(game.screen instanceof QuestRewardPopupScreen, "Claimed mail item did not open");
                require(!((net.minecraft.world.item.ItemStack) field(game.screen, "inspectedStack")).isEmpty(),
                        "Claimed mail item lost icon/identity");
            }
            case 7 -> {
                capture(game, "claimed-mail-item-details-zh-1920");
                game.screen.onClose(); mailClick(1);
                require(game.screen instanceof QuestRewardPopupScreen, "Mail XP did not open");
            }
            case 8 -> {
                game.screen.onClose(); mailClick(2);
                require(game.screen instanceof QuestRewardPopupScreen, "Mail levels did not open");
            }
            case 9 -> {
                capture(game, "levels-reward-details-zh-1920");
                game.screen.onClose();
                game.setScreen(LootTablePopupScreen.inspect(mailbox, new QuestDefinition.Reward("loot", LOOT, 1)));
            }
            case 10 -> {
                require(game.screen instanceof LootTablePopupScreen && ((List<?>) field(game.screen, "entries")).size() == 2,
                        "Loot table contents omitted rows");
                capture(game, "loot-reward-contents-zh-1920");
                var popup = game.screen;
                double x = (int) call(popup, "left") + 35, y = (int) call(popup, "rowsTop") + 20;
                popup.mouseClicked(x, y, 0); popup.mouseReleased(x, y, 0);
                require(game.screen instanceof QuestRewardPopupScreen, "Loot item row did not open details");
                game.screen.onClose(); popup.onClose();
                game.setScreen(LootTablePopupScreen.inspect(mailbox, frozenChoice));
            }
            case 11 -> {
                require(game.screen instanceof LootTablePopupScreen
                                && field(game.screen, "displayIcon").equals("minecraft:emerald")
                                && ((List<?>) field(game.screen, "entries")).size() == 2,
                        "Frozen choice contents lost icon or candidates");
                capture(game, "choice-loot-contents-zh-1920");
                game.screen.onClose();
                game.setScreen(new QuestLootChoiceScreen(mailbox, TASK, "选择补给", List.of(frozenChoice), false));
            }
            case 12 -> {
                capture(game, "choice-loot-selection-zh-1920");
                var selector = game.screen;
                var button = selector.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .filter(value -> value.styleId().equals("loot-choice-inspect-0-0")).findFirst().orElseThrow();
                button.onPress();
                require(game.screen instanceof QuestRewardPopupScreen, "Choice candidate inspect button did not open");
                game.screen.onClose();
                require(game.screen == selector && ((Map<?, ?>) field(selector, "selected")).isEmpty(),
                        "Inspecting a choice implicitly selected it");
                selector.onClose();
                setup = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == inventory,
                            "Inspection repeated reward delivery");
                }, game.getSingleplayerServer());
            }
            case 13 -> {
                if (!setup.isDone()) return false; setup.join();
                game.setScreen(LootTablePopupScreen.inspect(mailbox, new QuestDefinition.Reward("loot", LOOT + "_ten", 1)));
            }
            case 14 -> {
                require(game.screen.children().stream().noneMatch(net.minecraft.client.gui.components.EditBox.class::isInstance),
                        "Ten rewards incorrectly showed search");
                game.screen.onClose();
                game.setScreen(LootTablePopupScreen.inspect(mailbox, largeChoice));
            }
            case 15 -> {
                var popup = game.screen;
                var search = popup.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("特有");
                var filtered = (List<?>) field(popup, "filtered");
                require(filtered.size() == 1 && ((LootDefinition.DisplayEntry) filtered.getFirst()).count() == 11,
                        "Detail-text search selected the wrong reward");
                search.setValue("does_not_exist");
                require(((List<?>) field(popup, "filtered")).isEmpty(), "Empty search did not produce no results");
                search.setValue("");
                require(((List<?>) field(popup, "filtered")).size() == 11, "Clearing search did not restore rewards");
                capture(game, "loot-search-zh-1920");
                game.screen.onClose();
                game.setScreen(new QuestLootChoiceScreen(mailbox, TASK, "搜索自选补给", List.of(largeChoice), false));
            }
            case 16 -> {
                var selector = game.screen;
                var search = selector.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("特有");
                var options = selector.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .filter(button -> button.styleId().equals("loot-choice-option")).toList();
                require(options.size() == 1, "Choice search did not filter details");
                options.getFirst().onPress();
                require(((Map<?, ?>) field(selector, "selected")).get(0).equals(10),
                        "Filtered selection lost its original candidate index");
                search = selector.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("");
                require(((Map<?, ?>) field(selector, "selected")).get(0).equals(10), "Search cleared the existing choice");
                journal = QuestScreen.task(null, OBJECTIVE);
                game.setScreen(journal);
            }
            case 17 -> {
                require(field(journal, "selected") != null, "Objective fixture was not selected");
                if (game.screen instanceof AnimatedQuestScreen animated && animated.inputBlocked()) return false;
                boolean opened = false;
                int left = (int) field(journal, "detailsLeft") + 24;
                int top = (int) call(journal, "detailsTop");
                int bottom = (int) call(journal, "detailContentBottom");
                for (int y = top; y < Math.min(bottom, top + 180); y++) {
                    journal.mouseClicked(left, y, 0);
                    journal.mouseReleased(left, y, 0);
                    if (game.screen != journal) { opened = true; break; }
                }
                require(opened && game.screen.getClass().getSimpleName().equals("JourneyQuestPopupScreen"),
                        "Journal objective did not open journey-style task details");
            }
            case 18 -> {
                capture(game, "journal-objective-details-zh-1920");
                var popup = game.screen;
                var hits = (List<?>) field(popup, "conditionHits");
                require(!hits.isEmpty(), "Task details omitted visual completion conditions");
                Object hit = hits.getFirst();
                int x = (int) call(hit, "cardLeft") + 8;
                int y = (int) call(hit, "top") + 10;
                popup.mouseClicked(x, y, 0);
                popup.mouseReleased(x, y, 0);
                require(game.screen instanceof QuestRewardPopupScreen, "Objective item card did not open item sources");
                require(((QuestDefinition.Reward) field(game.screen, "selectedReward")).item().equals("minecraft:stick"),
                        "Objective details inspected the wrong item");
            }
            case 19 -> {
                game.screen.onClose();
            }
            case 20 -> {
                require(game.screen.getClass().getSimpleName().equals("JourneyQuestPopupScreen"),
                        "Item inspection did not return to task details");
                game.screen.onClose();
            }
            case 21 -> {
                require(game.screen == journal && ((String) field(journal, "selectedId")).equals(OBJECTIVE),
                        "Task details did not retain the journal selection");
                var layout = (LayoutEditorState) field(journal, "layout");
                String title = (String) invoke(journal, "builtInId", new Class[]{String.class}, "title");
                layout.elements().put(title, new LayoutEditorState.Element(title, OBJECTIVE, "quest_title", "", 220, 20, Map.of()));
                layout.setPosition(title, 10, 10);
                layout.elements().put("inspect_objective", new LayoutEditorState.Element("inspect_objective", OBJECTIVE,
                        "quest_objective", "", 220, 24, Map.of()));
                layout.setPosition("inspect_objective", 10, 56);
            }
            case 22 -> {
                var layout = (LayoutEditorState) field(journal, "layout");
                var objective = layout.elements().get("inspect_objective");
                var position = (int[]) invoke(journal, "elementDisplayPosition",
                        new Class[]{LayoutEditorState.Element.class}, objective);
                journal.mouseClicked(position[0] + 22, position[1] + 10, 0);
                journal.mouseReleased(position[0] + 22, position[1] + 10, 0);
                require(game.screen.getClass().getSimpleName().equals("JourneyQuestPopupScreen"),
                        "Authored objective control did not open the shared task popup");
            }
            case 23 -> {
                game.screen.onClose();
            }
            case 24 -> {
                require(game.screen == journal, "Authored objective popup lost its parent journal");
                Files.writeString(Path.of("..", "build", "verification", "journal-objective-details-result.txt"),
                        "PASS: journal objective -> journey task popup -> matching item/source details -> popup -> same journal selection;\n"
                                + "authored objective control opens the same task popup and returns to its journal.\n");
                return true;
            }
            default -> throw new AssertionError("Unknown reward inspection stage");
        }
        stage++;
        return false;
    }

    private static void taskClick(Minecraft game, int index) throws Exception {
        int footer = (int) call(journal, "rewardFooterHeight");
        double x, y;
        if (footer > 0) {
            x = (int) field(journal, "detailsLeft") + 10 + index * 38 + 10;
            y = (int) call(journal, "detailContentBottom") + 30;
        } else {
            var layout = (LayoutEditorState) field(journal, "layout");
            var element = layout.elements().values().stream().filter(e -> e.quest().equals(TASK)
                    && e.kind().equals("quest_rewards")).findFirst().orElseThrow();
            var position = (int[]) invoke(journal, "elementDisplayPosition", new Class[]{LayoutEditorState.Element.class}, element);
            double scale = (double) call(journal, "canvasRenderScale");
            x = position[0] + (index * 38 + 10) * scale; y = position[1] + 25 * scale;
        }
        require(journal.mouseClicked(x, y, 0), "Task reward press ignored");
        journal.mouseReleased(x, y, 0);
    }
    private static void mailClick(int index) throws Exception {
        var viewport = call(mailbox, "attachmentViewport");
        double x = (int) call(viewport, "left") + index * 38 + 10;
        double y = (int) call(viewport, "top") + 20;
        require(mailbox.mouseClicked(x, y, 0), "Mail attachment press ignored");
        mailbox.mouseReleased(x, y, 0);
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Object call(Object owner, String name) throws Exception { return invoke(owner, name, new Class[0]); }
    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(owner, args);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            require(image.getWidth() == 1920 && image.getHeight() == 1080,
                    "Reward-inspection screenshot is not 1920x1080: " + name);
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void moveCursor(Minecraft game, double x, double y) throws Exception {
        double scale = game.getWindow().getGuiScale();
        double windowX = x * scale;
        double windowY = y * scale;
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), windowX, windowY);
        var mouse = game.mouseHandler;
        for (String name : List.of("xpos", "ypos")) {
            var field = mouse.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setDouble(mouse, name.equals("xpos") ? windowX : windowY);
        }
    }
    private static void verifyInlineLootTooltip() {
        var table = new LootDefinition("tooltip_inline", "自动生成难度 1 奖励",
                LootDefinition.Mode.RANDOM_WITH_REPLACEMENT, 1, "", List.of(
                new LootDefinition.Entry("tier-1-1", "item", "minecraft:raw_iron", 1, 1,
                        "always", true),
                new LootDefinition.Entry("tier-1-3", "item", "minecraft:iron_nugget", 4, 1,
                        "always", true)));
        var task = new QuestDefinition("inline_loot_tooltip", "战利品提示检查", "",
                "world", "", true, false, 0, null, "", "", "", "inline-loot-tooltip",
                List.of(), List.of(new QuestDefinition.Trigger("confirm", "", 1)),
                List.of(new QuestDefinition.Reward("loot", "inline:" + new Gson().toJson(table), 1)),
                "default");
        String tooltip = QuestText.tooltip(task).getString();
        require(!tooltip.contains("inline:") && !tooltip.contains("\"weight\"")
                        && !tooltip.contains("\"displayIcon\""),
                "Inline loot tooltip leaked serialized JSON");
        require(tooltip.contains(QuestIcons.localizedName("minecraft:iron_nugget"))
                        && tooltip.contains("×4")
                        && tooltip.contains(QuestIcons.localizedName("minecraft:raw_iron")),
                "Inline loot tooltip omitted localized item names or counts");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError("Reward inspection stage " + stage + ": " + message);
    }
}
