package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Photographs the current screens at native 1080p after their opening transitions settle. */
final class DocumentationGalleryRegression {
    private static final String TASK = "docs_collect_oak";
    private static final String LOOT = "docs_explorer_supplies";
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final List<QuestDefinition.Reward> REWARDS = List.of(
            new QuestDefinition.Reward("item", "minecraft:diamond", 8, "rare", ""),
            new QuestDefinition.Reward("item", "minecraft:emerald", 16, "epic", ""),
            new QuestDefinition.Reward("experience", "", 120));
    private static final List<MailboxRuntime.Attachment> ATTACHMENTS = REWARDS.stream()
            .map(r -> new MailboxRuntime.Attachment(r.type(), r.item(), r.count(), false, false, false,
                    r.quality(), r.background())).toList();
    private static final List<QuestDefinition.Trigger> CONDITIONS = List.of(
            new QuestDefinition.Trigger("item_hold", "minecraft:oak_log", 16),
            new QuestDefinition.Trigger("item_hold", "minecraft:apple", 4));
    private static CompletableFuture<Void> setup;
    private static List<Scene> scenes;
    private static int index, wait;
    private static boolean opened;
    private static QuestScreen journal;
    private static MailboxScreen mailbox;
    private interface Open { Screen screen() throws Exception; }
    private record Scene(String name, Open open) {}

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        if (setup == null) {
            game.options.guiScale().set(2);
            game.getWindow().setWindowed(1920, 1080);
            game.getWindow().setWidth(1920); game.getWindow().setHeight(1080); game.resizeDisplay();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(game.getWindow().getWindow(), 0, 0);
            ClientEditMode.set(true);
            setup = CompletableFuture.runAsync(() -> {
                try { fixtures(game); } catch (Exception failure) { throw new RuntimeException(failure); }
            }, game.getSingleplayerServer());
            return false;
        }
        if (!setup.isDone()) return false;
        setup.join();
        if (scenes == null) {
            ClientMailbox.request();
            journal = QuestScreen.task(null, TASK);
            mailbox = new MailboxScreen(journal);
            scenes = scenes();
            wait = 12;
            return false;
        }
        if (index == scenes.size()) return true;
        MailArrivalToast.clear(); QuestUnlockToast.clear(); XeroToast.clear(); game.getToasts().clear();
        if (!opened) {
            game.setScreen(scenes.get(index).open().screen());
            if (scenes.get(index).name().equals("task-context-menu")) {
                set(game.screen, "editMode", true);
                invoke(game.screen, "init", new Class[0]);
                invoke(game.screen, "contextMenu", new Class[]{int.class, int.class, String.class},
                        170, 110, "quest:" + TASK);
            } else if (scenes.get(index).name().equals("task-properties")) {
                invoke(game.screen, "editProperties", new Class[]{String.class, String.class}, "task", TASK);
            }
            opened = true; wait = 12;
            return false;
        }
        if (game.screen == null) throw new AssertionError("Gallery screen closed: " + scenes.get(index).name());
        if (game.screen instanceof AnimatedQuestScreen animated && animated.inputBlocked()) return false;
        if (!game.options.languageCode.equals("zh_cn")) throw new AssertionError("Gallery language is not Chinese");
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            if (image.getWidth() != 1920 || image.getHeight() != 1080)
                throw new AssertionError("Gallery is not native 1920x1080");
            image.writeToFile(OUTPUT.resolve(scenes.get(index).name() + "-zh-1920.png"));
        }
        index++; opened = false;
        return false;
    }
    private static List<Scene> scenes() {
        return List.of(
                new Scene("journal-current", () -> journal),
                new Scene("task-context-menu", () -> QuestScreen.task(null, TASK)),
                new Scene("task-properties", () -> QuestScreen.task(null, TASK)),
                new Scene("search-scope", () -> new QuestSearchSettingsScreen(journal)),
                new Scene("search-filters", () -> new QuestSearchFilterScreen(journal)),
                new Scene("conditions-editor", () -> new QuestConditionsScreen(journal, TASK, CONDITIONS, ignored -> {})),
                new Scene("rewards-editor", () -> new QuestRewardsScreen(journal, REWARDS, "manual", (a, b) -> {})),
                new Scene("reward-receipt", () -> new QuestRewardPopupScreen(journal, "探索者的补给", REWARDS)),
                new Scene("loot-editor", () -> new QuestLootScreen(journal, LOOT, null)),
                new Scene("mailbox-current", () -> {
                    var selected = MailboxScreen.class.getDeclaredField("selectedId"); selected.setAccessible(true);
                    selected.set(mailbox, "docs_explorer_letter");
                    return mailbox;
                }),
                new Scene("mail-compose", () -> {
                    Screen screen = construct("MailComposeScreen", new Class[]{MailboxScreen.class}, mailbox);
                    set(screen, "recipientValue", "@s"); set(screen, "senderValue", "探索协会");
                    set(screen, "subjectValue", "旅途中的补给");
                    set(screen, "bodyValue", "<h2>致探索者</h2><p>感谢你完成本次委托。</p>"
                            + "<p>补给已随信附上，请在下次出发前领取。</p>");
                    set(screen, "iconValue", "minecraft:paper"); set(screen, "rewards", ATTACHMENTS);
                    return screen;
                }),
                new Scene("mail-attachments", () -> QuestRewardsScreen.mail(mailbox, ATTACHMENTS, ignored -> {})),
                new Scene("rich-text-editor", () -> new QuestTextEditorScreen(journal,
                        "<h2>探索者的旅程</h2><p>收集 <b>16 个原木</b>和 <i>4 个苹果</i>。</p>"
                                + "<p><color=#7BD6B0>完成后返回领取补给。</color></p>", ignored -> {})),
                new Scene("item-picker", () -> QuestScreen.pickResource(journal, "minecraft:diamond", true, ignored -> {})),
                new Scene("texture-picker", () -> QuestScreen.pickResource(journal,
                        "minecraft:textures/gui/title/background/panorama_0.png", false, ignored -> {})),
                new Scene("icon-fullscreen-preview", () -> construct("QuestIconPreviewScreen",
                        new Class[]{Screen.class, String.class}, journal, "minecraft:diamond")),
                new Scene("node-library-current", () -> new QuestNodeLibraryScreen(journal, ignored -> {})),
                new Scene("variable-catalog", () -> construct("QuestVariableListScreen",
                        new Class[]{Screen.class, String.class, Consumer.class, BiConsumer.class},
                        journal, TASK, (Consumer<String>) ignored -> {}, (BiConsumer<String, String>) (a, b) -> {})),
                new Scene("variable-editor", () -> construct("QuestVariableEditScreen",
                        new Class[]{Screen.class, String.class, String.class, QuestVariable.class, BiConsumer.class},
                        journal, TASK, "globe", new QuestVariable("globe.expedition_count", "4", 1, "number", "team"),
                        (BiConsumer<String, String>) (a, b) -> {})),
                new Scene("animation-editor-current", () -> {
                    Screen screen = construct("PopupAnimationEditorScreen", new Class[]{Screen.class}, journal);
                    set(screen, "playhead", .75);
                    return screen;
                }),
                new Scene("unlock-graph-current", () -> new QuestUnlockGraphScreen(journal)),
                new Scene("task-templates", () -> new QuestTemplateScreen(journal, "")),
                new Scene("location-editor", () -> new QuestLocationScreen(journal,
                        "coords|minecraft:overworld|0|64|0|16", ignored -> {})),
                new Scene("legendary-current", () -> new LegendaryScreen(journal)),
                new Scene("hot-events-current", () -> new HotEventsScreen(journal)),
                new Scene("value-picker-current", () -> new QuestValuePickerScreen(journal, "选择任务分类", "type",
                        () -> List.of(new QuestValuePickerScreen.Option("world", "世界任务"),
                                new QuestValuePickerScreen.Option("guide", "引导任务"),
                                new QuestValuePickerScreen.Option("daily", "每日任务")), "world", ignored -> {})),
                new Scene("suggestion-recycle-bin", () -> {
                    var picker = new QuestValuePickerScreen(journal, "选择任务分类", "type", List::of, "", ignored -> {});
                    return construct("QuestSuggestionTrashScreen",
                            new Class[]{QuestValuePickerScreen.class, String.class, String.class, List.class, Runnable.class},
                            picker, "任务分类", "type", List.of("world", "guide", "daily"), (Runnable) () -> {});
                }));
    }
    private static void fixtures(Minecraft game) throws Exception {
        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
        var values = new LinkedHashMap<String, Object>();
        values.put("id", TASK); values.put("title", "探索者的补给"); values.put("type", "world");
        values.put("icon", "minecraft:oak_log"); values.put("trackable", true); values.put("recommended", true);
        values.put("subtitle", "采集与发现"); values.put("description", "收集 16 个原木和 4 个苹果，为下一次探索准备补给。");
        values.put("hotEvent", new HotEventData(true, "minecraft:textures/gui/title/background/panorama_0.png",
                "探索者的补给", 40, 24, true));
        values.put("triggers", CONDITIONS); values.put("rewards", REWARDS); values.put("rewardMode", "manual");
        Files.writeString(QuestRepository.root().resolve(TASK + ".json"), new Gson().toJson(values));
        var legend = new LinkedHashMap<>(values);
        legend.put("id", "docs_legendary_path"); legend.put("title", "远行者的约定");
        legend.put("type", "legendary"); legend.put("icon", "minecraft:compass");
        legend.put("legendary", new LegendaryData("minecraft:textures/gui/title/background/panorama_0.png",
                "第一章 · 旅途启程", Map.of("篇章", List.of("第一章"), "地区", List.of("主世界"))));
        legend.put("hotEvent", new HotEventData(true, "minecraft:textures/gui/title/background/panorama_0.png",
                "远行者的约定", 310, 24, true));
        Files.writeString(QuestRepository.root().resolve("docs_legendary_path.json"), new Gson().toJson(legend));
        QuestRepository.reload();
        if (QuestRepository.find(TASK) == null) throw new AssertionError("Gallery task was not loaded");
        QuestLootStore.save(player, new LootDefinition(LOOT, "探索者补给箱", LootDefinition.Mode.RANDOM, 2,
                List.of(new LootDefinition.Entry("diamond", "item", "minecraft:diamond", 2, 10, "always", true, "rare", ""),
                        new LootDefinition.Entry("emerald", "item", "minecraft:emerald", 6, 30, "always", true, "epic", ""),
                        new LootDefinition.Entry("apple", "item", "minecraft:golden_apple", 1, 5, "always", true, "legendary", ""))));
        MailboxRuntime.sendIfAbsent(player, "docs_explorer_letter", "探索协会", "旅途中的补给",
                "<h2>致探索者</h2><p>感谢你完成本次委托。</p><p>补给已随信附上，请在下次出发前领取。</p>",
                30 * 86400L, "minecraft:paper", false, ATTACHMENTS);
    }
    private static Screen construct(String name, Class<?>[] types, Object... args) throws Exception {
        var constructor = Class.forName("xtdpotato.xero_quest_journal.client." + name).getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return (Screen) constructor.newInstance(args);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        var method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(object, args);
    }
}
