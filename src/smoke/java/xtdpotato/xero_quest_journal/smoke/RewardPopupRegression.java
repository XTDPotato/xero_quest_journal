package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class RewardPopupRegression {
    private static final String GROUP = "popup_" + System.currentTimeMillis();
    private static final String ID = GROUP + "_task";
    private static final String SOURCE_ID = GROUP + "_source";
    private static final String DEFERRED_ID = GROUP + "_deferred";
    private static final String EMPTY_GROUP = GROUP + "_empty";
    private static final Reward REWARD = new Reward("item", "minecraft:diamond", 2, "epic", "#7650A6");
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static int stage;
    private static int wait;
    private static CompletableFuture<Void> pending;
    private static CompletableFuture<Void> languageReload;
    private static QuestScreen journal;
    private static QuestRewardPopupScreen popup;
    private static QuestRewardPopupScreen receipt;
    private static DepthParent depthParent;
    private static int[] rewardPoint;
    private static boolean sourceHoverPending;
    private static LayoutEditorState.CanvasSize originalCanvas;
    private static int originalBlur;
    private static int sharpContrast;
    private static BlurProbeScreen blurProbe;

    static boolean tick(Minecraft game) throws Exception {
        if (stage == 5) moveMouse(game, rewardPoint[0] + 1, rewardPoint[1] + 1);
        if (--wait > 0) return false;
        wait = 16;
        switch (stage) {
            case 0 -> {
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                languageReload = game.reloadResourcePacks();
                ClientPreferences.get().reducedMotion = false;
                QuestConfig.defaults().uiAnimationMillis = 220;
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP,
                            GROUP, "Popup group", "", "", "", "active", "", 1)).successful(), "Create group");
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP,
                            EMPTY_GROUP, "Empty group", "", "", "", "main", "", 1)).successful(), "Create empty group");
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            ID, "Popup task", "", GROUP, "", "guide", "", 1, "", "",
                            "minecraft:diamond", 2, "manual")).successful(), "Create task");
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            SOURCE_ID, "Source task", "", GROUP, "", "guide", "", 1, "", "",
                            "minecraft:diamond", 1, "manual")).successful(), "Create source task");
                    player.getInventory().clearContent();
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone() || !languageReload.isDone() || ClientQuestProgress.view(ID) == null) return false;
                pending.join();
                languageReload.join();
                verifyLootTranslations(game);
                journal = QuestScreen.task(null, ID);
                game.setScreen(journal);
            }
            case 2 -> {
                require(hasGroup(journal), "Active group missing before completion");
                checkEmptyGroups(game);
                require(ClientQuestProgress.view(ID).status().equals("active"), "Manual task started completed");
                button(journal, "quest-" + ID).onPress();
                require(field(journal, "actionPending") == null,
                        "Selecting a task sent a completion request");
                require(ClientQuestProgress.view(ID).status().equals("active"),
                        "Selecting a task completed its confirmation condition");
                var q = button(journal, "category-previous");
                var e = button(journal, "category-next");
                require(q.getWidth() == (journal.width < 400 ? 9 : 10)
                                && e.getHeight() == ((int) call(journal, "categoryHeight") - 2) / 2,
                        "Q/E keycaps were not reduced by half");
                try (NativeImage ignored = capture(game, "journal-small-keycaps")) {}
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestRuntime.activate(player, ID).equals("completed"), "Complete fixture task");
                }, game.getSingleplayerServer());
            }
            case 3 -> {
                if (!pending.isDone() || !ClientQuestProgress.view(ID).completed()) return false;
                pending.join();
                journal.tick();
                require(QuestScreen.matchesCategory(ClientQuestDefinitions.find(ID), "active"),
                        "Completed unlocked task disappeared from in-progress category");
                require(hasGroup(journal), "Unclaimed reward group disappeared from active list");
                var overview = QuestScreen.overview(journal);
                game.setScreen(overview);
                require(hasGroup(overview), "Empty group was removed from overview too");
                set(journal, "category", ClientQuestDefinitions.find(ID).type());
                set(journal, "selectedId", ID);
                set(journal, "searchQuery", ID);
                game.setScreen(journal);
                prepareRewardControl();
            }
            case 4 -> {
                rewardPoint = findRewardPoint();
                moveMouse(game, rewardPoint[0] + 1, rewardPoint[1] + 1);
            }
            case 5 -> {
                require(field(journal, "selectedId").equals(ID), "Wrong task selected");
                var hit = QuestScreen.class.getDeclaredMethod("hit", double.class, double.class);
                hit.setAccessible(true);
                require("popup-reward".equals(hit.invoke(journal,
                        (double) rewardPoint[0] + 1, (double) rewardPoint[1] + 1)), "Reward point hit another control");
                try (NativeImage image = capture(game, "reward-hover-border")) {
                    int color = rgb(image, journal, rewardPoint[0], rewardPoint[1]);
                    require(color == 0x9DB4D2,
                            "Reward hover outline color " + Integer.toHexString(color)
                                    + " at " + rewardPoint[0] + "," + rewardPoint[1]);
                }
                require(journal.mouseClicked(rewardPoint[0] + 1, rewardPoint[1] + 1, 0),
                        "Reward item click was not handled");
                require(game.screen instanceof QuestRewardPopupScreen, "Item click did not open source");
                popup = (QuestRewardPopupScreen) game.screen;
                require(field(popup, "selectedReward") != null, "Item click opened a receipt");
                require(field(journal, "actionPending") == null, "Item inspection sent a task action");
                moveMouse(game, 1, 1);
            }
            case 6 -> {
                require(ClientQuestProgress.view(ID).status().equals("complete"), "Source inspection claimed reward");
                require(game.player.getInventory().countItem(Items.DIAMOND) == 0, "Inspection granted an item");
                require((int) call(popup, "panelWidth") <= 250, "Source popup is too wide");
                var source = button(popup, "reward-source-recipes");
                require(source.active == !System.getProperty("xero.recipeViewerTest", "").isEmpty(),
                        "Recipe source action did not reflect optional viewer availability");
                var sourceMethod = popup.getClass().getDeclaredMethod("findSources", ItemStack.class);
                sourceMethod.setAccessible(true);
                ClientPreferences.get().questRewardSources = false;
                var hidden = (java.util.List<?>) sourceMethod.invoke(popup, new ItemStack(Items.DIAMOND));
                var idMethod = Class.forName("xtdpotato.xero_quest_journal.client.QuestRewardPopupScreen$Source")
                        .getDeclaredMethod("questId");
                idMethod.setAccessible(true);
                require(hidden.stream().noneMatch(sourceEntry -> {
                    try { return !((String) idMethod.invoke(sourceEntry)).isEmpty(); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                }), "Quest reward sources should be hidden by default");
                ClientPreferences.get().questRewardSources = true;
                var enabled = (java.util.List<?>) sourceMethod.invoke(popup, new ItemStack(Items.DIAMOND));
                require(enabled.stream().anyMatch(sourceEntry -> {
                    try { return SOURCE_ID.equals(idMethod.invoke(sourceEntry)); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                }), "Available quest reward source missing after opt-in");
                require(enabled.stream().noneMatch(sourceEntry -> {
                    try { return ID.equals(idMethod.invoke(sourceEntry)); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                }), "Completed quest leaked into item sources");
                ClientPreferences.get().questRewardSources = false;
                int left = (int) call(popup, "left");
                int top = (int) call(popup, "top");
                var rows = (java.util.List<?>) field(popup, "sourceRows");
                require(!rows.isEmpty(), "Source fixture has no informational rows");
                var row = rows.getFirst();
                var rowHeight = row.getClass().getDeclaredMethod("height");
                var rowLines = row.getClass().getDeclaredMethod("lines");
                rowHeight.setAccessible(true);
                rowLines.setAccessible(true);
                if (((java.util.List<?>) rowLines.invoke(row)).size() == 1)
                    require((int) rowHeight.invoke(row) == 22, "Source row height was not reduced by 4px");
                int firstRowY = top + 94 + (int) call(popup, "tooltipHeight") + 40;
                if (!sourceHoverPending) {
                    try (NativeImage ignored = capture(game, "reward-source-only")) {}
                    moveMouse(game, left + 24, firstRowY + 8);
                    sourceHoverPending = true;
                    wait = 3;
                    return false;
                }
                try (NativeImage image = capture(game, "reward-source-hover")) {
                    require(rgb(image, popup, left + 16, firstRowY + 8) == (ButtonStyle.FRAME_COLOR & 0xFFFFFF),
                            "Informational source row has no hover border");
                }
                popup.mouseClicked(left + 24, firstRowY + 8, 0);
                require(game.screen == popup && !popup.isClosing(), "Informational source row became actionable");
                popup.mouseClicked(left + 6, top + 60, 0);
                require(game.screen == popup && !popup.isClosing(), "Inside blank area dismissed source");
                ClientPreferences.get().questRewardSources = true;
                var load = popup.getClass().getDeclaredMethod("loadDetails", Reward.class);
                load.setAccessible(true);
                load.invoke(popup, REWARD);
                call(popup, "init");
                var questRows = (java.util.List<?>) field(popup, "sourceRows");
                int questY = (int) call(popup, "top") + 94 + (int) call(popup, "tooltipHeight") + 40;
                boolean jumped = false;
                for (var candidate : questRows) {
                    var rowId = candidate.getClass().getDeclaredMethod("questId");
                    var rowSize = candidate.getClass().getDeclaredMethod("height");
                    rowId.setAccessible(true);
                    rowSize.setAccessible(true);
                    int size = (int) rowSize.invoke(candidate);
                    if (SOURCE_ID.equals(rowId.invoke(candidate))) {
                        int bottom = (int) call(popup, "top") + (int) call(popup, "panelHeight") - 10;
                        int offset = Math.max(0, questY + size - bottom + 2);
                        set(popup, "detailScroll", offset);
                        popup.mouseClicked((int) call(popup, "left") + 24, questY - offset + 8, 0);
                        require(game.screen instanceof QuestScreen
                                        && SOURCE_ID.equals(field(game.screen, "selectedId")),
                                "Quest source did not navigate to the matching task");
                        jumped = true;
                        break;
                    }
                    questY += size + 4;
                }
                require(jumped, "Quest source row not present after opt-in");
                ClientPreferences.get().questRewardSources = false;
                game.setScreen(popup);
                popup.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(popup.isClosing(), "Source did not animate closing");
            }
            case 7 -> {
                require(game.screen == journal, "Source Esc revealed a fake rewards receipt");
                journal.mouseClicked(rewardPoint[0] + 1, rewardPoint[1] + 1, 0);
                require(game.screen instanceof QuestRewardPopupScreen, "Could not reopen source");
                popup = (QuestRewardPopupScreen) game.screen;
            }
            case 8 -> {
                popup.mouseClicked(1, 1, 0);
                require(popup.isClosing(), "Outside click did not close source");
            }
            case 9 -> {
                require(game.screen == journal, "Outside click did not return to journal");
                MailArrivalToast.clear();
                var claim = journal.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals("claim"))
                        .findFirst().orElseThrow();
                require(claim.getX() == journal.width - 136 && claim.getY() == journal.height - 34,
                        "Claim action is not in the bottom-right tracking slot");
                require(claim.getY() > (int) call(journal, "canvasBottom"),
                        "Claim action overlaps the task canvas");
                require(journal.children().stream().noneMatch(child -> child instanceof StyledButton button
                                && button.styleId().equals("track")),
                        "Completed task still shows tracking beside its claim action");
                require(journal.children().stream().noneMatch(child -> child instanceof StyledButton button
                                && button.styleId().equals("claim-all")),
                        "Duplicate claim-all button remains");
                try (NativeImage ignored = capture(game, "claim-footer")) {}
                claim.onPress();
            }
            case 10 -> {
                if (!(game.screen instanceof QuestRewardPopupScreen)) return false;
                receipt = (QuestRewardPopupScreen) game.screen;
                require(field(receipt, "selectedReward") == null, "Claim did not open receipt");
                require(ClientQuestProgress.view(ID).status().equals("claimed"), "Claim not synchronized");
                require(QuestScreen.matchesCategory(ClientQuestDefinitions.find(ID), "active"),
                        "Claimed unlocked task disappeared from in-progress category");
                require(game.player.getInventory().countItem(Items.DIAMOND) == 2, "Incorrect claim delivery");
            }
            case 11 -> {
                try (NativeImage ignored = capture(game, "reward-receipt")) {}
                require(Math.abs((double) call(receipt, "contentLeft") + 32 - receipt.width / 2.0) <= .5,
                        "Single reward is not centered");
                int itemY = (int) call(receipt, "cardTop") + 20;
                receipt.mouseClicked(receipt.width / 2.0, itemY, 0);
                receipt.mouseReleased(receipt.width / 2.0, itemY, 0);
                require(game.screen instanceof QuestRewardPopupScreen && game.screen != receipt,
                        "Receipt item did not open separate source");
                popup = (QuestRewardPopupScreen) game.screen;
            }
            case 12 -> {
                popup.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            case 13 -> {
                require(game.screen == receipt, "Nested source did not return to actual receipt");
                receipt.mouseClicked((int) call(receipt, "left") + 6, (int) call(receipt, "top") + 60, 0);
                require(receipt.isClosing(), "Blank area inside receipt bounds did not dismiss it");
            }
            case 14 -> {
                require(game.screen == journal, "Receipt outside click did not return to journal");
                require(journal.children().stream().noneMatch(child -> child instanceof StyledButton button
                                && (button.styleId().equals("track") || button.styleId().equals("claim")
                                || button.styleId().equals("complete"))),
                        "Claimed task still shows tracking or completion actions");
                QuestConfig.defaults().uiAnimationMillis = 800;
                depthParent = new DepthParent();
                game.setScreen(depthParent);
                popup = new QuestRewardPopupScreen(depthParent, REWARD);
                game.setScreen(popup);
                wait = 5;
            }
            case 15 -> {
                require(popup.animationPosition() > 0 && popup.animationPosition() < 1,
                        "Modal entrance skipped intermediate frames");
                require(popup.inputBlocked(), "Modal accepted input during entrance");
                try (NativeImage ignored = capture(game, "reward-popup-opening")) {}
                wait = 24;
            }
            case 16 -> {
                require(!popup.inputBlocked(), "Modal entrance never settled");
                try (NativeImage image = capture(game, "reward-popup-layering")) {
                    require(rgb(image, popup, (int) call(popup, "left") + 10, popup.height / 2 + 50) == 0x2A2E38,
                            "High-depth parent reward leaked through opaque modal");
                    require(rgb(image, popup, (int) call(popup, "left") - 1, popup.height / 2) == 0x9DB4D2,
                            "Modal frame color differs from hover outline");
                }
                require(popup.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).anyMatch(b -> b.styleId().equals("reward-popup-close")),
                        "Close control missing");
                popup.mouseClicked(1, 1, 0);
                wait = 5;
            }
            case 17 -> {
                require(game.screen == popup && popup.isClosing() && popup.animationPosition() > 0
                        && popup.animationPosition() < 1, "Modal exit skipped intermediate frames");
                try (NativeImage ignored = capture(game, "reward-popup-closing")) {}
                wait = 24;
            }
            case 18 -> {
                require(game.screen == depthParent, "Animated close lost parent");
                require(depthParent.clicks == 0, "Outside click passed through to parent");
                QuestConfig.defaults().uiAnimationMillis = 220;
                var stack = new ItemStack(Items.DIAMOND);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        Component.literal("Detailed diamond"));
                stack.set(net.minecraft.core.component.DataComponents.LORE,
                        new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                                Component.literal("Tooltip remains visible even when recipe sources exist"),
                                Component.literal("Second tooltip line"))));
                String encoded = xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue
                        .encode(stack, game.player.registryAccess());
                popup = new QuestRewardPopupScreen(journal, new Reward("item", encoded, 1));
                game.setScreen(popup);
            }
            case 19 -> {
                require(((java.util.List<?>) field(popup, "tooltip")).stream().map(Component.class::cast)
                                .anyMatch(c -> c.getString().contains("Second tooltip line")),
                        "Component-specific tooltip was discarded");
                try (NativeImage ignored = capture(game, "reward-source-tooltip")) {}
                int oldWidth = popup.width, oldHeight = popup.height;
                popup.resize(game, 320, 240);
                require((int) call(popup, "left") >= 0 && (int) call(popup, "top") >= 0
                                && (int) call(popup, "panelWidth") <= 288
                                && (int) call(popup, "panelHeight") <= 208, "Small viewport bounds");
                popup.resize(game, 320, 180);
                require((int) call(popup, "panelHeight") <= 148, "Short viewport bounds");
                popup.mouseScrolled(160, 90, 0, -20);
                require((int) field(popup, "detailScroll") > 0, "Long tooltip/source list cannot scroll");
                popup.resize(game, oldWidth, oldHeight);
                popup = new QuestRewardPopupScreen(journal, REWARD);
                game.setScreen(popup);
            }
            case 20 -> {
                if (!System.getProperty("xero.recipeViewerTest", "").isEmpty()) {
                    button(popup, "reward-source-recipes").onPress();
                    require(game.screen != popup, "Recipe viewer did not open for the current item");
                    require(!game.screen.getClass().getName().startsWith("net.minecraft."),
                            "Recipe action opened vanilla inventory instead of recipes");
                }
            }
            case 21 -> {
                if (!System.getProperty("xero.recipeViewerTest", "").isEmpty()) {
                    try (NativeImage ignored = capture(game, "reward-source-" + System.getProperty("xero.recipeViewerTest"))) {}
                }
                game.setScreen(journal);
                prepareRewardControl();
                rewardPoint = findRewardPoint();
                moveMouse(game, 1, 1);
            }
            case 22 -> {
                RewardCardAssertions.claimedBadge(game, journal, rewardPoint[0], rewardPoint[1],
                        ((Double) call(journal, "canvasRenderScale")).floatValue(), "task-claimed-reward");
                journal.mouseClicked(rewardPoint[0] + 1, rewardPoint[1] + 1, 0);
                require(game.screen instanceof QuestRewardPopupScreen, "Claimed icon did not open inspector");
                popup = (QuestRewardPopupScreen) game.screen;
                require(field(popup, "selectedReward") != null && field(popup, "parent") == journal,
                        "Claimed icon inserted a fake receipt in the screen stack");
                require(field(journal, "actionPending") == null, "Claimed icon sent another claim");
            }
            case 23 -> {
                popup.mouseClicked(1, 1, 0);
            }
            case 24 -> {
                require(game.screen == journal, "Claimed inspector revealed a second window");
                var action = QuestScreen.class.getDeclaredMethod("runAction", String.class, boolean.class);
                action.setAccessible(true);
                action.invoke(journal, ID, true);
                require(field(journal, "actionPending") == null, "Already claimed task allowed another request");
                set(journal, "actionPending", CompletableFuture.completedFuture("claimed"));
                journal.tick();
                require(game.screen == journal, "Unrelated/stale success opened receipt without a claim request");
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            DEFERRED_ID, "Deferred claim", "", GROUP, "", "guide", "", 1, "", "",
                            "minecraft:diamond", 2, "manual")).successful(), "Create deferred claim fixture");
                    QuestRuntime.activate(player, DEFERRED_ID);
                    player.getInventory().clearContent();
                }, game.getSingleplayerServer());
            }
            case 25 -> {
                if (!pending.isDone() || ClientQuestProgress.view(DEFERRED_ID) == null
                        || !ClientQuestProgress.view(DEFERRED_ID).status().equals("complete")) return false;
                pending.join();
                var action = QuestScreen.class.getDeclaredMethod("runAction", String.class, boolean.class);
                action.setAccessible(true);
                action.invoke(journal, DEFERRED_ID, true);
                popup = new QuestRewardPopupScreen(journal, REWARD);
                game.setScreen(popup);
            }
            case 26 -> {
                if (!ClientQuestProgress.view(DEFERRED_ID).status().equals("claimed")) return false;
                journal.tick();
                require(game.screen == popup, "Claim completion replaced an open source inspector");
                popup.onClose();
            }
            case 27 -> {
                if (game.screen == journal) return false;
                require(game.screen instanceof QuestRewardPopupScreen && game.screen != popup,
                        "Deferred successful claim lost its receipt");
                receipt = (QuestRewardPopupScreen) game.screen;
                require(field(receipt, "selectedReward") == null && field(receipt, "parent") == journal,
                        "Deferred receipt was nested below an inspector");
                require(game.player.getInventory().countItem(Items.DIAMOND) == 2, "Deferred claim delivery changed");
                receipt.onClose();
            }
            case 28 -> {
                require(game.screen == journal, "Deferred receipt did not close");
                journal.tick();
                journal.tick();
                require(game.screen == journal && field(journal, "pendingClaim") == null,
                        "Successful request produced multiple receipts");
                depthParent = new DepthParent();
                game.setScreen(depthParent);
                QuestConfig.defaults().uiAnimationMillis = 800;
                receipt = new QuestRewardPopupScreen(depthParent, "Carousel",
                        java.util.stream.IntStream.range(0, 12)
                                .mapToObj(i -> new Reward("item", i % 2 == 0 ? "minecraft:diamond" : "minecraft:emerald", i + 1))
                                .toList());
                game.setScreen(receipt);
                wait = 5;
            }
            case 29 -> {
                require(receipt.animationPosition() > 0 && receipt.animationPosition() < 1,
                        "Receipt entrance skipped scale/fade transition");
                try (NativeImage ignored = capture(game, "reward-receipt-opening")) {}
                wait = 24;
            }
            case 30 -> {
                require(!receipt.inputBlocked(), "Receipt animation did not settle");
                try (NativeImage image = capture(game, "reward-receipt-carousel")) {
                    int near = rgb(image, receipt, 39, 60);
                    int far = rgb(image, receipt, 34, 60);
                    require(near != far, "Receipt backdrop edge remained sharp instead of blurred");
                }
                require((double) call(receipt, "maxScroll") > 0, "Long receipt has no scrollable overflow");
                receipt.mouseScrolled(receipt.width / 2.0, receipt.height / 2.0, 0, -1);
                require((double) field(receipt, "scroll") == 0 && (double) field(receipt, "targetScroll") > 0,
                        "Wheel scrolling jumped instead of animating");
                wait = 2;
            }
            case 31 -> {
                double scroll = (double) field(receipt, "scroll");
                require(scroll > 0 && scroll < (double) field(receipt, "targetScroll"),
                        "Smooth scrolling has no intermediate frame");
                wait = 16;
            }
            case 32 -> {
                int itemY = (int) call(receipt, "cardTop") + 20;
                double itemX = (double) call(receipt, "contentLeft") + 80 + 32;
                receipt.mouseClicked(itemX, itemY, 0);
                receipt.mouseDragged(itemX - 80, itemY, 0, -80, 0);
                receipt.mouseReleased(itemX - 80, itemY, 0);
                require(game.screen == receipt && (double) field(receipt, "targetScroll") > 100,
                        "Dragging reward row opened source or failed to scroll");
                receipt.mouseClicked((int) call(receipt, "viewportRight") - 2, (int) call(receipt, "trackY"), 0);
                receipt.mouseReleased(0, 0, 0);
                require((double) field(receipt, "targetScroll") == (double) call(receipt, "maxScroll"),
                        "Scrollbar could not reach the final reward");
            }
            case 33 -> {
                try (NativeImage ignored = capture(game, "reward-receipt-scrolled")) {}
                int oldWidth = receipt.width, oldHeight = receipt.height;
                receipt.resize(game, 320, 240);
                require((int) call(receipt, "left") >= 0 && (int) call(receipt, "top") >= 0,
                        "Receipt does not fit small viewport");
                receipt.resize(game, oldWidth, oldHeight);
                receipt.mouseClicked(receipt.width / 2.0, (int) call(receipt, "top") + 44, 0);
                require(receipt.isClosing(), "Receipt blank click did not animate closing");
                wait = 5;
            }
            case 34 -> {
                require(receipt.animationPosition() > 0 && receipt.animationPosition() < 1,
                        "Receipt exit skipped scale/fade transition");
                try (NativeImage ignored = capture(game, "reward-receipt-closing")) {}
                wait = 24;
            }
            case 35 -> {
                require(game.screen == depthParent && depthParent.clicks == 0,
                        "Receipt dismissal lost parent or clicked through");
                QuestConfig.defaults().uiAnimationMillis = 220;
                game.setScreen(null);
            }
            case 36 -> {
                if (!ClaimReceiptRegression.tick(game)) return false;
            }
            case 37 -> {
                if (!ConditionsFormRegression.tick(game)) return false;
            }
            case 38 -> {
                if (!AutomaticClaimRegression.tick(game)) return false;
                ClientPreferences.get().reducedMotion = true;
                game.getToasts().clear();
                game.options.guiScale().set(2);
                GLFW.glfwRestoreWindow(game.getWindow().getWindow());
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 720);
                game.resizeDisplay();
                journal = QuestScreen.task(null, SOURCE_ID);
                game.setScreen(journal);
                moveMouse(game, 1, 1);
            }
            case 39 -> {
                checkTaskScale(game, "task-proportional-desktop");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 854, 480);
                game.resizeDisplay();
            }
            case 40 -> {
                checkTaskScale(game, "task-proportional-small");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1920, 1080);
                game.resizeDisplay();
            }
            case 41 -> {
                checkTaskScale(game, "task-proportional-wide");
                var layout = (LayoutEditorState) field(journal, "layout");
                originalCanvas = layout.taskCanvas(SOURCE_ID);
                layout.setTaskCanvas(SOURCE_ID, 1200, 700);
            }
            case 42 -> {
                checkTaskScale(game, "task-proportional-authored-wide");
                ((LayoutEditorState) field(journal, "layout")).setTaskCanvas(SOURCE_ID, 395, 1200);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 854, 480);
                game.resizeDisplay();
            }
            case 43 -> {
                checkTaskScale(game, "task-proportional-authored-tall");
                ((LayoutEditorState) field(journal, "layout")).setTaskCanvas(SOURCE_ID,
                        originalCanvas.width(), originalCanvas.height());
                originalBlur = game.options.menuBackgroundBlurriness().get();
                game.options.menuBackgroundBlurriness().set(0);
                blurProbe = new BlurProbeScreen(journal);
                game.setScreen(blurProbe);
            }
            case 44 -> {
                sharpContrast = backdropContrast(game, "task-backdrop-sharp-probe");
                require(sharpContrast > 100, "Task backdrop is opaque");
                game.options.menuBackgroundBlurriness().set(10);
            }
            case 45 -> {
                require(backdropContrast(game, "task-backdrop-blurred-probe") < sharpContrast / 2,
                        "Task background was not blurred before drawing the interface");
                blurProbe = new BlurProbeScreen(new MailboxScreen(null));
                game.options.menuBackgroundBlurriness().set(0);
                game.setScreen(blurProbe);
            }
            case 46 -> {
                sharpContrast = backdropContrast(game, "mail-backdrop-sharp-probe");
                require(sharpContrast > 100, "Mailbox backdrop is opaque");
                game.options.menuBackgroundBlurriness().set(10);
            }
            case 47 -> {
                require(backdropContrast(game, "mail-backdrop-blurred-probe") < sharpContrast / 2,
                        "Mailbox background was not blurred before drawing the interface");
                game.options.menuBackgroundBlurriness().set(originalBlur);
                game.setScreen(null);
                return true;
            }
            default -> throw new AssertionError("Unknown stage " + stage);
        }
        stage++;
        return false;
    }

    private static void checkTaskScale(Minecraft game, String name) throws Exception {
        double x = (double) call(journal, "canvasScaleX");
        double y = (double) call(journal, "canvasScaleY");
        require(Math.abs(x - y) < .00001 && x > 0 && x <= 1,
                "Task content stretched or enlarged: " + x + "," + y);
        require((int) call(journal, "taskWidth") * x <= (int) call(journal, "canvasWidth") + .01
                        && (int) call(journal, "taskHeight") * y <= (int) call(journal, "canvasHeight") + .01,
                "Authored task canvas does not fit the viewport");
        try (NativeImage image = capture(game, name)) {
            var close = button(journal, "close");
            int background = rgb(image, journal, close.getX() + close.getWidth() / 2, close.getY() + 4);
            int left = close.getWidth(), right = -1, top = close.getHeight(), bottom = -1;
            for (int dy = 0; dy < close.getHeight(); dy++) {
                for (int dx = 0; dx < close.getWidth(); dx++) {
                    int color = rgb(image, journal, close.getX() + dx, close.getY() + dy);
                    if (color != background) continue;
                    left = Math.min(left, dx);
                    right = Math.max(right, dx);
                    top = Math.min(top, dy);
                    bottom = Math.max(bottom, dy);
                }
            }
            require(right >= left && right - left == bottom - top, "Close icon background is not circular");
        }
    }

    private static int backdropContrast(Minecraft game, String name) throws Exception {
        try (NativeImage image = capture(game, name)) {
            int a = rgb(image, blurProbe, 7, blurProbe.height / 2);
            int b = rgb(image, blurProbe, 8, blurProbe.height / 2);
            return Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                    + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
        }
    }

    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }

    private static void checkEmptyGroups(Minecraft game) throws Exception {
        var overview = QuestScreen.overview(journal);
        for (QuestScreen screen : java.util.List.of(journal, overview)) {
            for (String category : java.util.List.of("active", "main")) {
                set(screen, "category", category);
                set(screen, "editMode", false);
                screen.init(game, journal.width, journal.height);
                require(!containsGroup(screen, EMPTY_GROUP), "Empty group leaked into normal view");
                set(screen, "editMode", true);
                screen.init(game, journal.width, journal.height);
                require(containsGroup(screen, EMPTY_GROUP), "Empty group unavailable in editor");
                set(screen, "editMode", false);
                set(screen, "searchQuery", "Empty group");
                screen.init(game, journal.width, journal.height);
                require(!containsGroup(screen, EMPTY_GROUP), "Search restored empty group");
                set(screen, "searchQuery", "");
            }
        }
        set(journal, "category", "active");
        game.setScreen(journal);
    }

    private static boolean containsGroup(QuestScreen screen, String id) throws Exception {
        return ((JournalListModel) field(screen, "journalList")).rows().stream()
                .anyMatch(row -> row.key().equals("group:" + id));
    }

    private static void prepareRewardControl() throws Exception {
        var layout = (LayoutEditorState) field(journal, "layout");
        // Isolate the test card from nested template controls that may cover its hit target.
        var ids = layout.elements().values().stream().filter(element -> element.quest().equals(ID))
                .map(LayoutEditorState.Element::id).toList();
        ids.forEach(layout::removeTree);
        layout.elements().put("popup-reward", new LayoutEditorState.Element("popup-reward", ID,
                "quest_rewards", "", 96, 60, Map.of()));
        layout.setPosition("popup-reward", 10, 70);
    }

    private static int[] findRewardPoint() throws Exception {
        var layout = (LayoutEditorState) field(journal, "layout");
        var element = layout.elements().get("popup-reward");
        var method = QuestScreen.class.getDeclaredMethod("rewardIndexAt", LayoutEditorState.Element.class,
                double.class, double.class);
        method.setAccessible(true);
        for (int y = 0; y < journal.height - 82; y++) {
            for (int x = (int) field(journal, "detailsLeft"); x < journal.width - 16; x++) {
                if ((int) method.invoke(journal, element, (double) x, (double) y) == 0) return new int[]{x, y};
            }
        }
        throw new AssertionError("No visible reward card");
    }

    private static boolean hasGroup(QuestScreen screen) throws Exception {
        return ((JournalListModel) field(screen, "journalList")).rows().stream()
                .anyMatch(row -> row.key().equals("group:" + GROUP));
    }

    private static void verifyLootTranslations(Minecraft game) throws Exception {
        List<String> suffixes = List.of("choose", "group", "claim", "title", "new", "id",
                "name", "mode", "mode.random", "mode.choice", "rolls", "entries",
                "save", "use", "delete", "type", "type.item", "type.command",
                "type.experience", "type.levels", "target", "pick_item", "count",
                "weight", "condition", "enabled", "remove", "invalid", "load_error",
                "delete_error");
        for (String locale : List.of("en_us", "zh_cn")) {
            var resource = ResourceLocation.fromNamespaceAndPath("xero_quest_journal",
                    "lang/" + locale + ".json");
            try (var reader = game.getResourceManager().openAsReader(resource)) {
                var translations = JsonParser.parseReader(reader).getAsJsonObject();
                for (String suffix : suffixes) {
                    String key = "screen.xero_quest_journal.loot." + suffix;
                    require(translations.has(key) && !translations.get(key).getAsString().isBlank(),
                            locale + " translation missing: " + key);
                    require(!Component.translatable(key).getString().equals(key),
                            "Unresolved in-game translation: " + key);
                }
            }
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void moveMouse(Minecraft game, int x, int y) throws Exception {
        // GLFW does not dispatch cursor moves reliably while a smoke-test window is unfocused.
        set(game.mouseHandler, "xpos", x * game.getWindow().getScreenWidth() / (double) game.screen.width);
        set(game.mouseHandler, "ypos", y * game.getWindow().getScreenHeight() / (double) game.screen.height);
    }

    private static NativeImage capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        NativeImage image = Screenshot.takeScreenshot(game.getMainRenderTarget());
        image.writeToFile(OUTPUT.resolve(name + ".png"));
        return image;
    }

    private static int rgb(NativeImage image, Screen screen, int x, int y) {
        int pixel = image.getPixelRGBA((int) ((x + .5) * image.getWidth() / screen.width),
                (int) ((y + .5) * image.getHeight() / screen.height));
        return (pixel & 255) << 16 | pixel & 0xFF00 | pixel >>> 16 & 255;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (reward popup stage " + stage + ")");
    }

    private static final class DepthParent extends Screen {
        private int clicks;
        DepthParent() { super(Component.literal("Depth regression")); }
        @Override public void render(GuiGraphics g, int x, int y, float delta) {
            g.fill(0, 0, width, height, 0xFF234932);
            g.fill(40, 40, 60, 80, 0xFFFFFFFF);
            g.pose().pushPose();
            g.pose().translate(0, 0, 1600);
            g.fill(width / 2 - 110, height / 2 + 40, width / 2 - 70, height / 2 + 80, 0xFFFF00FF);
            g.renderItem(new ItemStack(Items.CHEST), width / 2 - 102, height / 2 + 48);
            g.pose().popPose();
        }
        @Override public boolean mouseClicked(double x, double y, int button) { clicks++; return true; }
        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class BlurProbeScreen extends Screen {
        private final Screen content;
        BlurProbeScreen(Screen content) {
            super(Component.literal("Background blur regression"));
            this.content = content;
        }
        @Override protected void init() { content.init(minecraft, width, height); }
        @Override public void render(GuiGraphics g, int x, int y, float delta) {
            for (int left = 0; left < width; left += 8)
                g.fill(left, 0, left + 8, height, left % 16 == 0 ? 0xFF000000 : 0xFFFFFFFF);
            content.render(g, -10000, -10000, delta);
        }
        @Override public void removed() { content.removed(); }
        @Override public boolean isPauseScreen() { return false; }
    }
}
