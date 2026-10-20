package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.HotEventStore;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Real client coverage for the background inspector, chapter menus, canvas handles and task popup. */
final class PresentationInteractionRegression {
    private static final String IMAGE = "minecraft:textures/gui/title/background/panorama_0.png";
    private static int stage, wait;
    private static CompletableFuture<Void> work, language;
    private static Screen background;
    private static JourneyAppearanceScreen appearance;
    private static QuestTriggerGraphScreen graphEditor;
    private static HotEventCanvas previousLegendary;
    private static Map<String, JourneyAppearance> otherChaptersBefore;
    private static final AtomicReference<HotEventCanvas> savedBackground = new AtomicReference<>();
    private static final AtomicReference<JourneyAppearance> savedAppearance = new AtomicReference<>();

    static boolean tick(Minecraft game, JourneyScreen journey, String chapterId, String taskId, String music) throws Exception {
        if (--wait > 0) return false;
        wait = 7;
        switch (stage) {
            case 0 -> {
                game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                language = game.reloadResourcePacks();
                work = CompletableFuture.runAsync(() -> {
                    try { serverChecks(game, chapterId, music); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!work.isDone() || !language.isDone()) return false;
                work.join(); language.join();
                game.setScreen(journey);
                var chapters = (List<?>) field(journey, "chapters");
                int current = (int) field(journey, "chapterIndex");
                call(journey, "selectChapter", new Class[]{int.class}, (current + 1) % chapters.size());
            }
            case 2 -> {
                var chapters = ClientQuestDefinitions.chapters();
                int index = java.util.stream.IntStream.range(0, chapters.size())
                        .filter(i -> chapters.get(i).id().equals(chapterId)).findFirst().orElseThrow();
                call(journey, "selectChapter", new Class[]{int.class}, index);
            }
            case 3 -> {
                var serverPlayer = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                require(serverPlayer.getTags().containsAll(Set.of("presentation_before", "presentation_after")),
                        "Chapter before/after graph events did not execute");
                if (!(boolean) field(journey, "editing")) button("journey-edit-layout").onPress();
                call(journey, "openMenu", new Class[]{double.class, double.class}, 30., 160.);
                @SuppressWarnings("unchecked") var menu = (List<StyledButton>) field(journey, "menu");
                require(menu.stream().anyMatch(b -> b.styleId().equals("journey-menu-edit_chapter_icon"))
                                && menu.stream().anyMatch(b -> b.styleId().equals("journey-menu-edit_chapter_name")),
                        "Chapter menu did not expose independent icon/name entries");
                menu.stream().filter(b -> b.styleId().equals("journey-menu-edit_chapter_name"))
                        .findFirst().orElseThrow().onPress();
                require(game.screen.getClass().getSimpleName().equals("JourneyChapterEditorScreen"),
                        "Chapter menu did not open the identity editor");
                var edits = game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                edits.get(0).setValue("采集与发现"); edits.get(1).setValue("minecraft:diamond");
            }
            case 4 -> {
                require(game.screen.getClass().getSimpleName().equals("JourneyChapterEditorScreen"),
                        "Chapter editor unexpectedly changed to " + game.screen.getClass().getSimpleName());
                capture(game, "journey-chapter-identity-zh-1920");
                button("journey-chapter-save").onPress();
            }
            case 5 -> {
                if (game.screen != journey || field(journey, "pending") != null) return false;
                var chapter = ClientQuestDefinitions.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst().orElseThrow();
                require(chapter.name().equals("采集与发现") && chapter.icon().equals("minecraft:diamond"),
                        "Chapter identity did not survive save/catalog refresh");
                appearance = new JourneyAppearanceScreen(journey, chapter.journeyAppearance(), savedAppearance::set)
                        .preview("chapter", chapter.icon(), chapter.name());
                game.setScreen(appearance);
            }
            case 6 -> {
                dragHandle("width", 24, 0);
                var draft = (JourneyAppearance.Element) call(appearance, "previewValue", new Class[0]);
                require(draft.iconWidth() == 52 && draft.iconHeight() == 28, "Right handle did not resize width only");
                dragHandle("height", 0, 18);
                draft = (JourneyAppearance.Element) call(appearance, "previewValue", new Class[0]);
                require(draft.iconWidth() == 52 && draft.iconHeight() == 46, "Bottom handle did not resize height only");
                dragHandle("ratio", 52, 46);
                draft = (JourneyAppearance.Element) call(appearance, "previewValue", new Class[0]);
                require(draft.iconWidth() == 104 && draft.iconHeight() == 92, "Corner handle lost aspect ratio");
                int[] bounds = (int[]) call(appearance, "componentBounds", new Class[]{String.class}, "icon");
                double x = bounds[0] + 8, y = bounds[1] + 8;
                int beforeX = draft.iconOffsetX();
                appearance.mouseClicked(x, y, 0); appearance.mouseDragged(x + 12, y + 8, 0, 12, 8);
                appearance.mouseReleased(x + 12, y + 8, 0);
                draft = (JourneyAppearance.Element) call(appearance, "previewValue", new Class[0]);
                require(draft.iconOffsetX() == beforeX + 12, "Dragging preview did not update numeric offsets");
            }
            case 7 -> {
                capture(game, "journey-visual-handles-zh-1920");
                button("journey-appearance-save").onPress();
                require(savedAppearance.get().get("chapter").iconWidth() == 104,
                        "Saving preview dimensions discarded the draft");
                var value = new Gson().fromJson(new Gson().toJson(savedAppearance.get()), JourneyAppearance.class);
                require(value.equals(savedAppearance.get()), "New element dimensions did not round-trip");
                var constructor = Class.forName("xtdpotato.xero_quest_journal.client.HotEventBackgroundScreen")
                        .getDeclaredConstructor(Screen.class, HotEventCanvas.class, Consumer.class);
                constructor.setAccessible(true);
                background = (Screen) constructor.newInstance(journey,
                        new HotEventCanvas(IMAGE, "", List.of(), true, Map.of(), "image", 1, 0, 0, "contain", music),
                        (Consumer<HotEventCanvas>) savedBackground::set);
                game.setScreen(background);
            }
            case 8 -> {
                @SuppressWarnings("unchecked") var fields = (Map<String, EditBox>) field(background, "inputs");
                fields.get("background_scale").setValue("1.5");
                background.mouseClicked(70, 90, 0); background.mouseDragged(100, 110, 0, 30, 20);
                background.mouseReleased(100, 110, 0);
                require(!fields.get("background_offset_x").getValue().equals("0")
                                && !fields.get("background_offset_y").getValue().equals("0"),
                        "Dragging background preview did not update offsets");
                fields.get("background_scale").setValue("invalid");
                button("hot-background-save").onPress();
                require(game.screen == background && savedBackground.get() == null,
                        "Invalid background values were silently accepted");
                fields.get("background_scale").setValue("1.5");
            }
            case 9 -> {
                capture(game, "hot-background-inspector-zh-1920");
                button("hot-background-save").onPress();
                require(savedBackground.get() != null && savedBackground.get().backgroundMode().equals("contain")
                                && savedBackground.get().backgroundMusic().equals(music),
                        "Background mode/music were not included in the saved draft");
                game.setScreen(new LegendaryScreen(journey));
                button("legendary-background-edit").onPress();
                require(game.screen.getClass().getSimpleName().equals("LegendaryBackgroundScreen")
                                && button("hot-background-mode") != null && button("hot-background-music") != null,
                        "Legendary gallery did not reuse the full background inspector");
            }
            case 10 -> {
                capture(game, "legendary-background-inspector-zh-1920");
                var config = ClientPreferences.get();
                previousLegendary = new HotEventCanvas(config.legendaryBackground, "", List.of(), true, Map.of(),
                        config.legendaryBackgroundType, config.legendaryBackgroundScale,
                        config.legendaryBackgroundOffsetX, config.legendaryBackgroundOffsetY,
                        config.legendaryBackgroundMode, config.legendaryBackgroundMusic);
                @SuppressWarnings("unchecked") var fields = (Map<String, EditBox>) field(game.screen, "inputs");
                fields.get("background_source").setValue(IMAGE);
                fields.get("background_music").setValue(music);
                fields.get("background_offset_x").setValue("24");
                ((StyledChoiceButton<String>) button("hot-background-mode")).select("stretch");
                button("hot-background-save").onPress();
                require(config.legendaryBackgroundMode.equals("stretch") && config.legendaryBackgroundOffsetX == 24
                                && config.legendaryBackgroundMusic.equals(music),
                        "Legendary background inspector did not save all fields");
                restoreLegendary();
                game.setScreen(journey);
                if ((boolean) field(journey, "editing")) button("journey-edit-layout").onPress();
                call(journey, "focusRequestedQuest", new Class[0]);
                var focus = JourneyScreen.class.getDeclaredField("focusQuest"); focus.setAccessible(true); focus.set(journey, taskId);
                call(journey, "focusRequestedQuest", new Class[0]);
                Object slot = ((List<?>) field(journey, "slots")).stream().filter(s -> {
                    try { return ((QuestDefinition) call(s, "quest", new Class[0])).id().equals(taskId); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }).findFirst().orElseThrow();
                double scale = (double) call(journey, "scale", new Class[0]);
                double sx = (double) call(field(journey, "timelineX"), "position", new Class[0]);
                double sy = (double) call(field(journey, "timelineY"), "position", new Class[0]);
                double x = ((int) call(journey, "slotX", new Class[]{slot.getClass()}, slot) + 50 - sx) * scale;
                double y = (int) call(journey, "top", new Class[0])
                        + ((int) call(journey, "slotY", new Class[]{slot.getClass()}, slot) + 30 - sy) * scale;
                journey.mouseClicked(x, y, 0); journey.mouseReleased(x, y, 0);
                require(game.screen.getClass().getSimpleName().equals("JourneyQuestPopupScreen"),
                        "Journey task click skipped the details popup");
            }
            case 11 -> {
                capture(game, "journey-task-details-popup-zh-1920");
                int popupWidth = (int) call(game.screen, "panelWidth", new Class[0]);
                int popupHeight = (int) call(game.screen, "panelHeight", new Class[0]);
                require(Math.abs(popupWidth - Math.round(game.screen.width * .75F)) <= 1
                                && Math.abs(popupHeight - Math.round(game.screen.height * .75F)) <= 1,
                        "Journey task details popup does not occupy 75% of the viewport");
                require(game.screen.children().stream().filter(StyledButton.class::isInstance)
                                .map(StyledButton.class::cast)
                                .noneMatch(button -> button.styleId().equals("journey-quest-popup-open")),
                        "Journey task popup still contains a redundant open-task button");
                button("journey-quest-popup-close").onPress();
                require(game.screen == journey, "Closing the task popup did not return to the same journey");
                graphEditor = new QuestTriggerGraphScreen(journey, new TriggerGraph(List.of(
                        new TriggerGraph.Node("backdrop", "set_hot_event_background", 100, 100,
                                TriggerNodeLibrary.entries("set_hot_event_background").getFirst().data())), List.of()), ignored -> {});
                var selected = QuestTriggerGraphScreen.class.getDeclaredField("selected");
                selected.setAccessible(true); selected.set(graphEditor, "backdrop");
                game.setScreen(graphEditor);
                require(button("graph-hot-background-mode") != null && button("graph-hot-background-type") != null,
                        "Hot-event background graph inspector is missing its choices");
                require(((int) field(graphEditor, "inspectorMaxScroll")) > 0,
                        "Long background node inspector is not scrollable");
            }
            case 12 -> {
                capture(game, "hot-background-node-zh-1920");
                ((StyledChoiceButton<String>) button("graph-hot-background-mode")).select("contain");
                @SuppressWarnings("unchecked") var nodes = (List<TriggerGraph.Node>) field(graphEditor, "nodes");
                require(nodes.getFirst().get("mode", "").equals("contain"),
                        "Choosing a background scale mode did not update the node");
                var preview = button("graph-hot_events.visual_background");
                var once = button("graph-action-once");
                require(preview.getY() >= once.getY() + once.getHeight(),
                        "Graph visual preview overlaps execution settings");
                preview.onPress();
                require(game.screen.getClass().getSimpleName().equals("HotEventBackgroundScreen"),
                        "Background node did not open its visual editor");
            }
            case 13 -> {
                @SuppressWarnings("unchecked") var fields = (Map<String, EditBox>) field(game.screen, "inputs");
                fields.get("background_source").setValue(IMAGE);
                fields.get("background_offset_x").setValue("37");
                fields.get("background_scale").setValue("1.25");
                ((StyledChoiceButton<String>) button("hot-background-mode")).select("stretch");
                button("hot-background-save").onPress();
            }
            case 14 -> {
                require(game.screen == graphEditor, "Background preview did not return to its node");
                @SuppressWarnings("unchecked") var nodes = (List<TriggerGraph.Node>) field(graphEditor, "nodes");
                var data = nodes.getFirst();
                require(data.get("source", "").equals(IMAGE) && data.get("offsetX", "").equals("37")
                                && data.get("scale", "").equals("1.25") && data.get("mode", "").equals("stretch"),
                        "Visual background changes did not return to the node draft");
                game.setScreen(journey);
                if (!(boolean) field(journey, "editing")) button("journey-edit-layout").onPress();
                otherChaptersBefore = ClientQuestDefinitions.chapters().stream()
                        .filter(chapter -> !chapter.id().equals(chapterId))
                        .collect(java.util.stream.Collectors.toMap(
                                xtdpotato.xero_quest_journal.core.network.QuestCatalog.Chapter::id,
                                xtdpotato.xero_quest_journal.core.network.QuestCatalog.Chapter::journeyAppearance));
                call(journey, "editAppearance", new Class[0]);
                appearance = (JourneyAppearanceScreen) game.screen;
                require(field(appearance, "saved") != null && field(appearance, "chapterContext") != null,
                        "Journey appearance did not bind a chapter-local save target");
            }
            case 15 -> {
                verifyMultipleSelection();
            }
            case 16 -> {
                capture(game, "journey-chapter-multiselect-zh-1920");
                button("journey-appearance-save").onPress();
            }
            case 17 -> {
                if (game.screen != journey || field(journey, "pending") != null) return false;
                var chapter = ClientQuestDefinitions.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst().orElseThrow();
                require(chapter.journeyAppearance().get("chapter").iconOffsetX() == 15
                                && chapter.journeyAppearance().get("marker").iconOffsetX() == 15,
                        "Multi-selection changes did not persist on the current chapter");
                require(ClientQuestDefinitions.chapters().stream().filter(c -> !c.id().equals(chapterId))
                                .allMatch(c -> c.journeyAppearance().equals(otherChaptersBefore.get(c.id()))),
                        "Chapter-local edit changed another chapter");
                capture(game, "journey-chapter-marker-zh-1920");
                return true;
            }
            default -> throw new AssertionError("Unknown presentation stage");
        }
        stage++;
        return false;
    }
    private static void verifyMultipleSelection() throws Exception {
        var checkbox = appearance.children().stream().filter(net.minecraft.client.gui.components.Checkbox.class::isInstance)
                .map(net.minecraft.client.gui.components.Checkbox.class::cast)
                .filter(c -> c.getMessage().getString().equals("多选")).findFirst().orElseThrow();
        checkbox.onPress();
        var selected = appearance.getClass().getDeclaredField("selected");
        var kind = appearance.getClass().getDeclaredField("selectedPart");
        selected.setAccessible(true); kind.setAccessible(true);
        selected.set(appearance, "marker"); kind.set(appearance, "icon");
        int[] marker = (int[]) call(appearance, "componentBounds", new Class[]{String.class}, "icon");
        selected.set(appearance, "chapter"); kind.set(appearance, "icon");
        double mx = marker[0] + marker[2] / 2., my = marker[1] + marker[3] / 2.;
        appearance.mouseClicked(mx, my, 0); appearance.mouseReleased(mx, my, 0);
        require(((Set<?>) field(appearance, "selections")).size() == 2,
                "Preview click did not add a second component to the selection");
        @SuppressWarnings("unchecked") var fields = (Map<String, EditBox>) field(appearance, "fields");
        fields.get("icon_x").setValue("15");
        @SuppressWarnings("unchecked") var drafts = (Map<String, Object>) field(appearance, "drafts");
        require(((JourneyAppearance.Element) call(drafts.get("chapter"), "preview", new Class[0])).iconOffsetX() == 15
                        && ((JourneyAppearance.Element) call(drafts.get("marker"), "preview", new Class[0])).iconOffsetX() == 15,
                "Inspector change did not update only the selected components");
        require(((JourneyAppearance.Element) call(drafts.get("tab"), "preview", new Class[0])).iconOffsetX() == 0,
                "Inspector change modified a component outside the selection");
        int[] primary = (int[]) call(appearance, "componentBounds", new Class[]{String.class}, "icon");
        double x = primary[0] + primary[2] / 2., y = primary[1] + primary[3] / 2.;
        appearance.mouseClicked(x, y, 0);
        double scale = (double) call(appearance, "previewScale", new Class[0]);
        appearance.mouseDragged(x + 8 * scale, y, 0, 8 * scale, 0);
        appearance.mouseReleased(x + 8 * scale, y, 0);
        require(((Set<?>) field(appearance, "selections")).size() == 2
                        && ((JourneyAppearance.Element) call(drafts.get("chapter"), "preview", new Class[0])).iconOffsetX() == 23
                        && ((JourneyAppearance.Element) call(drafts.get("marker"), "preview", new Class[0])).iconOffsetX() == 23,
                "Dragging a selected component did not move the multiple selection together");
        fields = (Map<String, EditBox>) field(appearance, "fields");
        fields.get("icon_x").setValue("15");
    }
    private static void restoreLegendary() {
        var config = ClientPreferences.get();
        config.legendaryBackground = previousLegendary.backgroundImage();
        config.legendaryBackgroundType = previousLegendary.backgroundType();
        config.legendaryBackgroundMode = previousLegendary.backgroundMode();
        config.legendaryBackgroundScale = previousLegendary.backgroundScale();
        config.legendaryBackgroundOffsetX = previousLegendary.backgroundOffsetX();
        config.legendaryBackgroundOffsetY = previousLegendary.backgroundOffsetY();
        config.legendaryBackgroundMusic = previousLegendary.backgroundMusic();
        require(config.save(), "Could not restore local gallery settings");
    }
    private static void serverChecks(Minecraft game, String chapter, String music) throws Exception {
        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
        var previous = HotEventStore.get();
        var graph = new TriggerGraph(List.of(
                new TriggerGraph.Node("event", "event", 0, 0, Map.of("event", "click")),
                new TriggerGraph.Node("backdrop", "set_hot_event_background", 160, 0, Map.of(
                        "source", IMAGE, "type", "image", "mode", "contain", "scale", "1.5",
                        "offsetX", "11", "offsetY", "-9", "music", music, "once", "false"))),
                List.of(new TriggerGraph.Edge("event", "backdrop", "next")));
        var controls = new ArrayList<QuestPresentation.Control>();
        controls.add(new QuestPresentation.Control("background", "button", "", 0, 0, 80, 20, "", Map.of(), graph));
        for (String phase : List.of("before", "after")) controls.add(new QuestPresentation.Control(
                phase, "button", "", 0, 0, 80, 20, "", Map.of(), new TriggerGraph(List.of(
                new TriggerGraph.Node("event", "event", 0, 0, Map.of(
                        "event", "chapter_switch_" + phase, "target", chapter, "cooldown", "1")),
                new TriggerGraph.Node("tag", "command", 160, 0, Map.of("command", "tag @s add presentation_" + phase))),
                List.of(new TriggerGraph.Edge("event", "tag", "next")))));
        String id = "presentation_regression_" + System.currentTimeMillis();
        Files.writeString(QuestRepository.root().resolve(id + ".json"),
                QuestPresentationDocuments.write("{\"id\":\"" + id + "\",\"title\":\"Presentation regression\",\"type\":\"guide\"}",
                        new QuestPresentation(true, 395, 275, controls)));
        QuestRepository.reload();
        try {
            QuestEventGraphs.click(player, id, "background");
            var value = HotEventStore.get();
            require(value.backgroundMode().equals("contain") && value.backgroundMusic().equals(music)
                            && value.backgroundOffsetX() == 11 && value.backgroundOffsetY() == -9,
                    "Hot-event background node did not execute");
            require(value.connections().equals(previous.connections()) && value.positions().equals(previous.positions()),
                    "Background node changed card layout/connections");
            HotEventStore.reload();
            require(HotEventStore.get().backgroundMode().equals("contain"), "Background mode did not survive reload");
            var legacy = new Gson().fromJson("{\"backgroundImage\":\"\"}", HotEventCanvas.class);
            require(legacy.backgroundMode().equals("cover") && legacy.backgroundMusic().isBlank(), "Legacy background defaults lost");
            require(TriggerNodeLibrary.events("chapter_event").getFirst().equals("chapter_switch_after"),
                    "Chapter events do not default to after-switch");
        } finally { require(HotEventStore.update(player, previous), "Could not restore background fixture"); }
    }
    private static void dragHandle(String handle, int dx, int dy) throws Exception {
        int[] b = (int[]) call(appearance, "componentBounds", new Class[]{String.class}, "icon");
        double x = b[0] + (handle.equals("height") ? b[2] / 2. : b[2]);
        double y = b[1] + (handle.equals("width") ? b[3] / 2. : b[3]);
        require(appearance.mouseClicked(x, y, 0), "Could not grab resize handle");
        appearance.mouseDragged(x + dx, y + dy, 0, dx, dy); appearance.mouseReleased(x + dx, y + dy, 0);
    }
    private static StyledButton button(String id) {
        return Minecraft.getInstance().screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(b -> b.styleId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + id + " at stage " + stage));
    }
    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
            catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("../build/verification", name + ".png"); Files.createDirectories(output.getParent());
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(output); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (presentation stage " + stage + ")");
    }
}
