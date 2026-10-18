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
    private static final String LANGUAGE = System.getProperty("xero.docsLanguage", "zh_cn");
    private static final boolean ENGLISH = LANGUAGE.equals("en_us");
    private static final boolean STANDALONE = Boolean.getBoolean("xero.docsGalleryOnly");
    private static final Path OUTPUT = STANDALONE
            ? Path.of("..", "build", "verification", "documentation", LANGUAGE)
            : Path.of("..", "build", "verification");
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
    private static CompletableFuture<Void> setup, language;
    private static net.minecraft.nbt.CompoundTag structure;
    private static QuestDefinition.Reward frozenChoice;
    private static QuestDefinition.Reward largeChoice;
    private static final List<Map<String, Object>> photographs = new ArrayList<>();
    private static List<Scene> scenes;
    private static int index, wait;
    private static boolean opened;
    private static long pickerReadyFrame;
    private static boolean pickerControlsVerified;
    private static QuestScreen journal;
    private static MailboxScreen mailbox;
    private interface Open { Screen screen() throws Exception; }
    private record Scene(String name, Open open) {}

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        if (setup == null) {
            game.options.pauseOnLostFocus = false;
            game.options.guiScale().set(2);
            game.getWindow().setWindowed(1920, 1080);
            game.getWindow().setWidth(1920); game.getWindow().setHeight(1080); game.resizeDisplay();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(game.getWindow().getWindow(), 0, 0);
            ClientEditMode.set(true);
            if (STANDALONE) ClientPreferences.get().reducedMotion = true;
            if (STANDALONE) {
                if (new ClientPreferences().journeyTabUnderlineHeight != 3)
                    throw new AssertionError("Default underline height is not 3px");
                ClientPreferences.get().journeyTabUnderlineHeight = 3;
            }
            game.getLanguageManager().setSelected(LANGUAGE);
            game.options.languageCode = LANGUAGE;
            language = game.reloadResourcePacks();
            setup = CompletableFuture.runAsync(() -> {
                try { fixtures(game); } catch (Exception failure) { throw new RuntimeException(failure); }
            }, game.getSingleplayerServer());
            return false;
        }
        if (!setup.isDone() || !language.isDone() || game.getOverlay() != null) return false;
        setup.join(); language.join();
        if (scenes == null) {
            if (ClientQuestDefinitions.find(TASK) == null) return false;
            ClientMailbox.request();
            journal = QuestScreen.task(null, TASK);
            mailbox = new MailboxScreen(journal);
            scenes = scenes();
            wait = 12;
            return false;
        }
        if (index == scenes.size()) {
            Files.writeString(OUTPUT.resolve("manifest.json"), new com.google.gson.GsonBuilder()
                    .setPrettyPrinting().create().toJson(photographs));
            Files.writeString(OUTPUT.resolve("result.txt"), "PASS: " + photographs.size()
                    + " real client screens; locale=" + LANGUAGE + "; native 1920x1080; settled UI capture.\n");
            return true;
        }
        MailArrivalToast.clear(); QuestUnlockToast.clear(); XeroToast.clear(); game.getToasts().clear();
        if (!opened) {
            ClientEditMode.set(!List.of("journal-current", "overview-current", "journey-current",
                    "mailbox-current", "legendary-current", "hot-events-current",
                    "anecdotes-current", "anecdotes-rewards").contains(scenes.get(index).name()));
            game.setScreen(scenes.get(index).open().screen());
            if (scenes.get(index).name().equals("task-context-menu")) {
                set(game.screen, "editMode", true);
                invoke(game.screen, "init", new Class[0]);
                invoke(game.screen, "contextMenu", new Class[]{int.class, int.class, String.class},
                        170, 110, "quest:" + TASK);
            } else if (scenes.get(index).name().equals("task-properties")) {
                invoke(game.screen, "editProperties", new Class[]{String.class, String.class}, "task", TASK);
            } else if (scenes.get(index).name().equals("journey-editor")) {
                button(game.screen, "journey-edit-layout").onPress();
            } else if (scenes.get(index).name().equals("journey-appearance")) {
                invoke(game.screen, "editAppearance", new Class[0]);
            } else if (scenes.get(index).name().equals("js-editor-examples")) {
                button(game.screen, "script-reference-examples").onPress();
            } else if (scenes.get(index).name().equals("event-graph-current")) {
                set(game.screen, "selected", "check");
                @SuppressWarnings("unchecked") var selected = (Set<String>) get(game.screen, "selectedIds");
                selected.add("check");
                invoke(game.screen, "init", new Class[0]);
            } else if (STANDALONE && scenes.get(index).name().equals("settings-appearance")) {
                verifyUnderlineSetting(game);
            } else if (scenes.get(index).name().equals("item-picker")) {
                button(game.screen, "icon-source-items").onPress();
            }
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(game.getWindow().getWindow(), 0, 0);
            for (String coordinate : List.of("xpos", "ypos")) {
                var field = game.mouseHandler.getClass().getDeclaredField(coordinate);
                field.setAccessible(true); field.setDouble(game.mouseHandler, 0);
            }
            opened = true; pickerReadyFrame = 0; pickerControlsVerified = false; wait = 12;
            return false;
        }
        if (game.screen == null) throw new AssertionError("Gallery screen closed: " + scenes.get(index).name());
        if (game.screen instanceof AnimatedQuestScreen animated && animated.inputBlocked()) return false;
        if (scenes.get(index).name().equals("item-picker") || scenes.get(index).name().equals("texture-picker")) {
            var picker = get(game.screen, "iconPicker");
            if (get(picker, "loadingChoices") != null || ((List<?>) get(picker, "filteredChoices")).isEmpty()) return false;
            if (pickerReadyFrame == 0) pickerReadyFrame = ClientSmokeTest.renderedFrames;
            if (ClientSmokeTest.renderedFrames - pickerReadyFrame < 3) return false;
            if (!pickerControlsVerified) {
                verifyPickerControls(game);
                pickerControlsVerified = true;
                pickerReadyFrame = ClientSmokeTest.renderedFrames;
                return false;
            }
            @SuppressWarnings("unchecked") var fields = (Map<String, net.minecraft.client.gui.components.EditBox>) get(game.screen, "formFields");
            var manual = fields.get("icon_picker.manual");
            if (manual != null && game.screen.getChildAt(manual.getX() + 5, manual.getY() + 5).orElse(null) != manual)
                throw new AssertionError("Resource grid intercepted its manual input");
        }
        if (!game.options.languageCode.equals(LANGUAGE)
                || !game.getLanguageManager().getSelected().equals(LANGUAGE))
            throw new AssertionError("Gallery locale does not match " + LANGUAGE);
        Files.createDirectories(OUTPUT);
        String file = scenes.get(index).name() + (ENGLISH ? "-en-1920.png" : "-zh-1920.png");
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            if (image.getWidth() != 1920 || image.getHeight() != 1080)
                throw new AssertionError("Gallery is not native 1920x1080");
            if (STANDALONE && scenes.get(index).name().startsWith("journey-current"))
                verifyUnderlinePixels(game, image);
            image.writeToFile(OUTPUT.resolve(file));
        }
        photographs.add(Map.of("scene", scenes.get(index).name(), "file", file, "locale", LANGUAGE,
                "width", 1920, "height", 1080, "screen", game.screen.getClass().getSimpleName(),
                "capturedAt", java.time.Instant.now().toString(), "sha256", HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(OUTPUT.resolve(file))))));
        index++; opened = false;
        return false;
    }
    private static List<Scene> scenes() {
        var result = new ArrayList<>(List.of(
                new Scene("journal-current", () -> journal),
                new Scene("task-context-menu", () -> QuestScreen.task(null, TASK)),
                new Scene("task-properties", () -> QuestScreen.task(null, TASK)),
                new Scene("search-scope", () -> new QuestSearchSettingsScreen(journal)),
                new Scene("search-filters", () -> new QuestSearchFilterScreen(journal)),
                new Scene("conditions-editor", () -> new QuestConditionsScreen(journal, TASK, CONDITIONS, ignored -> {})),
                new Scene("rewards-editor", () -> new QuestRewardsScreen(journal, REWARDS, "manual", (a, b) -> {})),
                new Scene("reward-receipt", () -> new QuestRewardPopupScreen(journal, tr("探索者的补给", "Explorer supplies"), REWARDS)),
                new Scene("loot-editor", () -> new QuestLootScreen(journal, LOOT, null)),
                new Scene("mailbox-current", () -> {
                    var selected = MailboxScreen.class.getDeclaredField("selectedId"); selected.setAccessible(true);
                    selected.set(mailbox, "docs_explorer_letter");
                    return mailbox;
                }),
                new Scene("mail-compose", () -> {
                    Screen screen = construct("MailComposeScreen", new Class[]{MailboxScreen.class}, mailbox);
                    set(screen, "recipientValue", "@s"); set(screen, "senderValue", tr("探索协会", "Explorers Guild"));
                    set(screen, "subjectValue", tr("旅途中的补给", "Supplies for your journey"));
                    set(screen, "bodyValue", letter());
                    set(screen, "iconValue", "minecraft:paper"); set(screen, "rewards", ATTACHMENTS);
                    return screen;
                }),
                new Scene("mail-attachments", () -> QuestRewardsScreen.mail(mailbox, ATTACHMENTS, ignored -> {})),
                new Scene("rich-text-editor", () -> new QuestTextEditorScreen(journal,
                        tr("<h2>探索者的旅程</h2><p>收集 <b>16 个原木</b>和 <i>4 个苹果</i>。</p>"
                                + "<p><color=#7BD6B0>完成后返回领取补给。</color></p>",
                                "<h2>An explorer's journey</h2><p>Collect <b>16 logs</b> and <i>4 apples</i>.</p>"
                                        + "<p><color=#7BD6B0>Return for your supplies when ready.</color></p>"), ignored -> {})),
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
                new Scene("value-picker-current", () -> new QuestValuePickerScreen(journal, tr("选择任务分类", "Choose a quest category"), "type",
                        () -> List.of(new QuestValuePickerScreen.Option("world", tr("世界任务", "World quests")),
                                new QuestValuePickerScreen.Option("guide", tr("引导任务", "Guides")),
                                new QuestValuePickerScreen.Option("daily", tr("每日任务", "Daily quests"))), "world", ignored -> {})),
                new Scene("suggestion-recycle-bin", () -> {
                    var picker = new QuestValuePickerScreen(journal, tr("选择任务分类", "Choose a quest category"), "type", List::of, "", ignored -> {});
                    return construct("QuestSuggestionTrashScreen",
                            new Class[]{QuestValuePickerScreen.class, String.class, String.class, List.class, Runnable.class},
                            picker, tr("任务分类", "Quest categories"), "type", List.of("world", "guide", "daily"), (Runnable) () -> {});
                })));
        if (STANDALONE) {
            result.add(new Scene("overview-current", () -> QuestScreen.overview(null)));
            for (String tab : List.of("general", "motion", "notifications", "appearance", "tasks", "tools")) {
                result.add(new Scene("settings-" + tab, () -> {
                    var screen = new QuestConfigScreen(journal); set(screen, "tab", tab); return screen;
                }));
            }
            result.add(new Scene("inventory-placement", () -> new InventoryPlacementScreen(journal)));
            result.add(new Scene("journey-current", () -> new JourneyScreen(journal, TASK)));
            result.add(new Scene("journey-current-1px", () -> {
                ClientPreferences.get().journeyTabUnderlineHeight = 1;
                return new JourneyScreen(journal, TASK);
            }));
            result.add(new Scene("journey-current-5px", () -> {
                ClientPreferences.get().journeyTabUnderlineHeight = 5;
                return new JourneyScreen(journal, TASK);
            }));
            result.add(new Scene("journey-editor", () -> new JourneyScreen(journal, TASK)));
            result.add(new Scene("journey-appearance", () -> new JourneyScreen(journal, TASK)));
            result.add(new Scene("journey-task-details-popup", () -> {
                var parent = new JourneyScreen(journal, TASK);
                parent.init(net.minecraft.client.Minecraft.getInstance(), journal.width, journal.height);
                return construct("JourneyQuestPopupScreen", new Class[]{Screen.class, String.class}, parent, TASK);
            }));
            result.add(new Scene("js-editor-reference", DocumentationGalleryRegression::scriptEditor));
            result.add(new Scene("js-editor-examples", DocumentationGalleryRegression::scriptEditor));
            result.add(new Scene("event-graph-current", () -> new QuestTriggerGraphScreen(journal,
                    exampleGraph(), TASK, "button", Map.of(), ignored -> {})));
            result.add(new Scene("anecdotes-current", () -> new AnecdoteScreen(journal)));
            result.add(new Scene("anecdotes-rewards", () -> new AnecdoteRewardsScreen(journal)));
            result.add(new Scene("experience-reward-details", () -> new QuestRewardPopupScreen(journal,
                    new QuestDefinition.Reward("experience", "", 120))));
            result.add(new Scene("levels-reward-details", () -> new QuestRewardPopupScreen(journal,
                    new QuestDefinition.Reward("levels", "", 2))));
            result.add(new Scene("loot-reward-contents", () -> LootTablePopupScreen.inspect(journal,
                    new QuestDefinition.Reward("loot", LOOT, 1))));
            result.add(new Scene("choice-loot-contents", () -> LootTablePopupScreen.inspect(journal, frozenChoice)));
            result.add(new Scene("choice-loot-selection", () -> new QuestLootChoiceScreen(journal, TASK,
                    tr("选择探索补给", "Choose expedition supplies"), List.of(frozenChoice), false)));
            result.add(new Scene("loot-search", () -> LootTablePopupScreen.inspect(journal, largeChoice)));
            result.add(new Scene("choice-loot-search", () -> new QuestLootChoiceScreen(journal, TASK,
                    tr("搜索探索补给", "Search expedition supplies"), List.of(largeChoice), false)));
            result.add(new Scene("structure-template", () -> structurePreview(false, false)));
            result.add(new Scene("jigsaw-inspector", () -> structurePreview(true, false)));
            result.add(new Scene("jigsaw-guide", () -> structurePreview(true, true)));
        }
        return List.copyOf(result);
    }
    private static String tr(String zh, String en) { return ENGLISH ? en : zh; }
    private static void verifyUnderlineSetting(Minecraft game) throws Exception {
        button(game.screen, "journey-tab-underline-more").onPress();
        if (ClientPreferences.get().journeyTabUnderlineHeight != 4) throw new AssertionError("Underline stepper did not apply");
        var input = game.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable(
                        "screen.xero_quest_journal.config.journey_tab_underline").getString())).findFirst().orElseThrow();
        input.setValue("2");
        if (ClientPreferences.get().journeyTabUnderlineHeight != 2) throw new AssertionError("Underline numeric input did not apply");
        ClientPreferences.get().journeyTabUnderlineHeight = 3;
        if (!ClientPreferences.get().save()) throw new AssertionError("Preference save failed");
        Path file = xtdpotato.xero_quest_journal.platform.PlatformServices.configDir().resolve("xero_quest/ui/client.json");
        var saved = new Gson().fromJson(Files.readString(file), ClientPreferences.class);
        if (saved.journeyTabUnderlineHeight != 3) throw new AssertionError("Underline setting was not persisted");
        var legacy = new Gson().fromJson("{}", ClientPreferences.class);
        if (legacy.journeyTabUnderlineHeight != 3) throw new AssertionError("Old preferences did not inherit 3px");
        invoke(game.screen, "init", new Class[0]);
    }
    private static void verifyUnderlinePixels(Minecraft game, com.mojang.blaze3d.platform.NativeImage image) throws Exception {
        var bounds = (int[]) call(game.screen, "timelineProgressBounds");
        int left = Math.max(bounds[0], (int) call(game.screen, "tabsLeft"));
        int right = Math.min(bounds[2], (int) call(game.screen, "tabsRight"));
        int x = (left + right) / 2;
        int pixels = ClientPreferences.get().journeyTabUnderlineHeight;
        double sx = image.getWidth() / (double) game.screen.width, sy = image.getHeight() / (double) game.screen.height;
        int found = 0;
        for (int y = bounds[3] - 8; y < bounds[3] + 1; y++)
            for (int py = (int) Math.round(y * sy); py < (int) Math.round((y + 1) * sy); py++) {
                int color = image.getPixelRGBA((int) Math.round(x * sx), py);
                int rgb = (color & 255) << 16 | (color >>> 8 & 255) << 8 | (color >>> 16 & 255);
                if (rgb == 0xF2D29A || rgb == 0xFFE6AE) found++;
            }
        if (found != Math.round(pixels * sy))
            throw new AssertionError("Underline raster differs: " + found + " expected " + Math.round(pixels * sy));
        if (pixels == 5) ClientPreferences.get().journeyTabUnderlineHeight = 3;
    }
    private static String letter() {
        return tr("<h2>致探索者</h2><p>感谢你完成本次委托。</p><p>补给已随信附上，请在下次出发前领取。</p>",
                "<h2>Dear explorer</h2><p>Thank you for completing the expedition.</p>"
                        + "<p>Your supplies are attached. Claim them before setting out.</p>");
    }
    private static Screen scriptEditor() throws Exception {
        return construct("QuestScriptEditorScreen", new Class[]{Screen.class, String.class, Consumer.class},
                journal, "eventType === \"click\" && Number(experienceLevel) >= 10;", (Consumer<String>) ignored -> {});
    }
    private static TriggerGraph exampleGraph() {
        return new TriggerGraph(List.of(
                new TriggerGraph.Node("event", "event", 30, 40, Map.of("event", "click")),
                new TriggerGraph.Node("check", "js", 240, 40, Map.of("mode", "script",
                        "script", "Number(experienceLevel) >= 10;")),
                new TriggerGraph.Node("done", "complete", 480, 40, Map.of())),
                List.of(new TriggerGraph.Edge("event", "check", "next"), new TriggerGraph.Edge("check", "done", "true")));
    }
    private static Screen structurePreview(boolean inspector, boolean guide) throws Exception {
        ClientStructures.cachePreview("docs:village_fountain", structure);
        var screen = new QuestStructurePreviewScreen(journal, "docs:village_fountain");
        set(screen, "inspector", inspector); set(screen, "guide", guide);
        return screen;
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void verifyPickerControls(Minecraft game) throws Exception {
        Screen screen = game.screen;
        int originalWidth = screen.width, originalHeight = screen.height;
        Object picker = get(screen, "iconPicker"), scroll = get(picker, "scroll");
        String selected = (String) get(picker, "manual");
        try {
            for (int[] size : List.of(new int[]{originalWidth, originalHeight},
                    new int[]{480, 270}, new int[]{320, 240})) {
                screen.resize(game, size[0], size[1]);
                var apply = button(screen, "icon-apply");
                var cancel = button(screen, "icon-cancel");
                var preview = button(screen, "icon-preview");
                if (apply.getWidth() != cancel.getWidth() || apply.getY() != cancel.getY()
                        || cancel.getX() - apply.getRight() != 8 || cancel.getRight() > screen.width - 16
                        || preview.getRight() + 8 > apply.getX())
                    throw new AssertionError("Picker footer buttons overlap or are not equal adjacent actions");
                for (var action : List.of(apply, cancel))
                    if (action.getWidth() < game.font.width(action.getMessage()) + 24 || action.getWidth() > 100)
                        throw new AssertionError("Picker action width lacks padding or is unnecessarily wide");
                @SuppressWarnings("unchecked")
                var fields = (Map<String, net.minecraft.client.gui.components.EditBox>) get(screen, "formFields");
                if (fields.get("icon_picker.manual").getRight() + 8 > button(screen, "icon-none").getX())
                    throw new AssertionError("Picker blank button overlaps the manual ID input");
                var rail = screen.children().stream()
                        .filter(child -> child.getClass().getSimpleName().equals("ScrollRail"))
                        .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).findFirst().orElseThrow();
                double maximum = (double) call(scroll, "maximum");
                if (maximum <= 0) throw new AssertionError("Resource fixture cannot exercise scrollbar overflow");
                invoke(scroll, "jump", new Class[]{double.class}, 0.);
                int thumbHeight = (int) call(rail, "thumbHeight");
                double railX = rail.getX() + rail.getWidth() / 2.;
                double grabY = rail.getY() + thumbHeight / 2.;
                double endY = rail.getBottom() - thumbHeight / 2.;
                screen.mouseClicked(railX, grabY, 0);
                screen.mouseDragged(railX, endY, 0, 0, endY - grabY);
                screen.mouseReleased(railX, endY, 0);
                if (Math.abs((double) call(scroll, "position") - maximum) > 1)
                    throw new AssertionError("Resource scrollbar cannot drag to the last row");
                invoke(scroll, "jump", new Class[]{double.class}, 0.);
                screen.mouseClicked(railX, rail.getY() + rail.getHeight() * .65, 0);
                screen.mouseReleased(railX, rail.getY() + rail.getHeight() * .65, 0);
                double target = (double) call(scroll, "target");
                if (target <= 0 || (double) call(scroll, "position") != 0)
                    throw new AssertionError("Scrollbar track click did not set a smooth scroll target");
                invoke(scroll, "advance", new Class[]{double.class}, 1. / 60);
                double position = (double) call(scroll, "position");
                if (position <= 0 || position >= target || !selected.equals(get(picker, "manual")))
                    throw new AssertionError("Scrollbar easing changed the selection or jumped immediately");
            }
        } finally {
            invoke(scroll, "jump", new Class[]{double.class}, 0.);
            screen.resize(game, originalWidth, originalHeight);
        }
    }
    private static void fixtures(Minecraft game) throws Exception {
        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
        player.server.getCommands().getDispatcher().execute(
                "xero_quest edit_mode " + player.getGameProfile().getName() + " true",
                player.server.createCommandSourceStack());
        Path group = QuestRepository.root().resolve("docs_explorers");
        Files.createDirectories(group);
        Files.writeString(group.resolve("group_manifest.json"), new Gson().toJson(Map.of(
                "id", "docs_explorers", "name", tr("探索协会", "Explorers Guild"),
                "description", tr("为下一次旅程做好准备。", "Prepare for the next expedition."),
                "type", "world", "icon", "minecraft:oak_sapling", "focusAvailable", true)));
        Files.deleteIfExists(QuestRepository.root().resolve(TASK + ".json"));
        Files.deleteIfExists(QuestRepository.root().resolve("docs_legendary_path.json"));
        for (var type : xtdpotato.xero_quest_journal.core.QuestTypeStore.all()) {
            String name = switch (type.id()) {
                case "main" -> tr("主线任务", "Main quests");
                case "legendary" -> tr("传说任务", "Legendary quests");
                case "commission" -> tr("委托任务", "Commissions");
                case "world" -> tr("世界任务", "World quests");
                default -> type.name();
            };
            xtdpotato.xero_quest_journal.core.QuestTypeStore.save(player, new QuestType(type.id(), name, type.icon()), true);
        }
        var values = new LinkedHashMap<String, Object>();
        values.put("id", TASK); values.put("title", tr("探索者的补给", "Explorer supplies")); values.put("type", "world");
        values.put("icon", "minecraft:oak_log"); values.put("trackable", true); values.put("recommended", true);
        values.put("subtitle", tr("采集与发现", "Gathering and discovery"));
        values.put("description", tr("收集 16 个原木和 4 个苹果，为下一次探索准备补给。",
                "Collect 16 logs and 4 apples to prepare for the next expedition."));
        values.put("hotEvent", new HotEventData(true, "minecraft:textures/gui/title/background/panorama_0.png",
                tr("探索者的补给", "Explorer supplies"), 40, 24, true));
        values.put("triggers", CONDITIONS); values.put("rewards", REWARDS); values.put("rewardMode", "manual");
        Files.writeString(group.resolve(TASK + ".json"), new Gson().toJson(values));
        var legend = new LinkedHashMap<>(values);
        legend.put("id", "docs_legendary_path"); legend.put("title", tr("远行者的约定", "A wanderer's promise"));
        legend.put("type", "legendary"); legend.put("icon", "minecraft:compass");
        legend.put("legendary", new LegendaryData("minecraft:textures/gui/title/background/panorama_0.png",
                tr("第一章 · 旅途启程", "Chapter one: setting out"),
                Map.of(tr("篇章", "Chapter"), List.of(tr("第一章", "One")), tr("地区", "Region"), List.of(tr("主世界", "Overworld")))));
        legend.put("hotEvent", new HotEventData(true, "minecraft:textures/gui/title/background/panorama_0.png",
                tr("远行者的约定", "A wanderer's promise"), 310, 24, true));
        Files.writeString(group.resolve("docs_legendary_path.json"), new Gson().toJson(legend));
        var chapter = new LinkedHashMap<String, Object>();
        chapter.put("id", "docs_exploration"); chapter.put("name", tr("采集与发现", "Gathering and discovery"));
        chapter.put("group", "docs_explorers");
        chapter.put("icon", "minecraft:oak_sapling"); chapter.put("quests", List.of(TASK, "docs_legendary_path"));
        Files.writeString(QuestRepository.root().resolve("docs_exploration.chapter.json"), new Gson().toJson(chapter));
        QuestRepository.reload();
        if (QuestRepository.find(TASK) == null) throw new AssertionError("Gallery task was not loaded");
        QuestLootStore.save(player, new LootDefinition(LOOT, tr("探索者补给箱", "Explorer supply crate"), LootDefinition.Mode.RANDOM, 2,
                List.of(new LootDefinition.Entry("diamond", "item", "minecraft:diamond", 2, 10, "always", true, "rare", ""),
                        new LootDefinition.Entry("emerald", "item", "minecraft:emerald", 6, 30, "always", true, "epic", ""),
                        new LootDefinition.Entry("apple", "item", "minecraft:golden_apple", 1, 5, "always", true, "legendary", ""))));
        MailboxRuntime.sendIfAbsent(player, "docs_explorer_letter", tr("探索协会", "Explorers Guild"),
                tr("旅途中的补给", "Supplies for your journey"), letter(),
                30 * 86400L, "minecraft:paper", false, ATTACHMENTS);
        if (STANDALONE) {
            var entries = List.of(new LootDefinition.Entry("diamond", "item", "minecraft:diamond", 2, 10, "always", true, "rare", ""),
                    new LootDefinition.Entry("xp", "experience", "", 120, 10, "always", true, "", "",
                            tr("探索经验补给，可用于积累经验点数。", "Expedition experience points to support your journey.")));
            QuestLootStore.save(player, new LootDefinition(LOOT + "_choice", tr("自选探索补给", "Choose expedition supplies"),
                    LootDefinition.Mode.CHOICE, 1, tr("选择一份奖励", "Choose one reward"), entries, "minecraft:emerald"));
            frozenChoice = xtdpotato.xero_quest_journal.core.runtime.QuestRewards.freeze(player,
                    List.of(new QuestDefinition.Reward("loot", LOOT + "_choice", 1))).getFirst();
            var many = java.util.stream.IntStream.range(0, 11).mapToObj(index ->
                    new LootDefinition.Entry("supply_" + index, "item",
                            List.of("minecraft:diamond", "minecraft:emerald", "minecraft:golden_apple",
                                    "minecraft:bread", "minecraft:iron_ingot", "minecraft:gold_ingot",
                                    "minecraft:apple", "minecraft:oak_log", "minecraft:torch",
                                    "minecraft:compass", "minecraft:book").get(index), index + 1, 10, "always", true,
                            "rare", "", tr("补给项目 " + (index + 1) + "，选择适合本次旅行的物品。",
                                    "Supply " + (index + 1) + ": choose what fits this expedition."))).toList();
            QuestLootStore.save(player, new LootDefinition(LOOT + "_many", tr("探索补给列表", "Expedition supplies"),
                    LootDefinition.Mode.CHOICE, 1, "", many, "minecraft:chest"));
            largeChoice = xtdpotato.xero_quest_journal.core.runtime.QuestRewards.freeze(player,
                    List.of(new QuestDefinition.Reward("loot", LOOT + "_many", 1))).getFirst();
            var anecdote = new AnecdoteDefinition("docs_mountain_letter", tr("山路来信", "A mountain letter"),
                    tr("山路来信", "Letters"), "minecraft:book", tr("来自远方的问候", "Greetings from afar"),
                    List.of(new AnecdoteDefinition.Record("meeting", tr("初次相遇", "First meeting"),
                                    tr("在山路上遇到了一位旅人。", "You met a traveler on the mountain path."), "minecraft:compass"),
                            new AnecdoteDefinition.Record("reply", tr("远方的回信", "A reply from afar"),
                                    tr("旅人寄来了新的冒险见闻。", "The traveler sent news of another adventure."), "minecraft:emerald")),
                    true, false, 0);
            AnecdoteStore.save(anecdote);
            AnecdoteStore.save(new AnecdoteDefinition("docs_unknown_tale", tr("未发现的故事", "An undiscovered tale"),
                    tr("未知旅途", "Unknown"), "minecraft:map", "", List.of(), false, false, 1));
            xtdpotato.xero_quest_journal.core.runtime.AnecdoteRuntime.discover(player, anecdote.id());
            xtdpotato.xero_quest_journal.core.runtime.AnecdoteRuntime.completeRecord(player, anecdote.id(), "meeting");
            xtdpotato.xero_quest_journal.core.runtime.AnecdoteRuntime.completeRecord(player, anecdote.id(), "reply");
            structure = xtdpotato.xero_quest_journal.core.runtime.QuestStructureLibrary.previewTemplate(player,
                    "minecraft:village/plains/town_centers/plains_fountain_01");
        }
    }
    private static Screen construct(String name, Class<?>[] types, Object... args) throws Exception {
        var constructor = Class.forName("xtdpotato.xero_quest_journal.client." + name).getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return (Screen) constructor.newInstance(args);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object get(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object call(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
    private static void invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        var method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(object, args);
    }
}
