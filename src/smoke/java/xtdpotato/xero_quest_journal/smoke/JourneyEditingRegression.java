package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class JourneyEditingRegression {
    private static final String GROUP = "journey_edit_" + System.currentTimeMillis();
    private static final String CHAPTER = GROUP + "_chapter", SECOND = GROUP + "_second";
    private static final String A = GROUP + "_a", B = GROUP + "_b", HIDDEN = GROUP + "_hidden";
    private static final String IMAGE_A = "minecraft:textures/gui/title/background/panorama_0.png";
    private static final String IMAGE_B = "minecraft:textures/gui/title/background/panorama_1.png";
    private static int stage, wait;
    private static CompletableFuture<Void> work;
    private static JourneyScreen journey;
    private static String music;
    private static Object oldPlayback;
    private static String originalParents;
    private static boolean visualBackgroundChecked;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        wait = 6;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                game.options.pauseOnLostFocus = false;
                resize(game, 1920, 1080);
                verifyRebound();
                Path wav = Files.createTempFile("xero-journey-", ".wav");
                try {
                    byte[] samples = new byte[22050];
                    try (var stream = new javax.sound.sampled.AudioInputStream(
                            new java.io.ByteArrayInputStream(samples),
                            new javax.sound.sampled.AudioFormat(22050, 8, 1, true, false), samples.length)) {
                        javax.sound.sampled.AudioSystem.write(stream,
                                javax.sound.sampled.AudioFileFormat.Type.WAVE, wav.toFile());
                    }
                    music = QuestMediaPicker.importFile(wav, false);
                } finally { Files.deleteIfExists(wav); }
                work = CompletableFuture.runAsync(() -> {
                    try {
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        player.server.getCommands().getDispatcher().execute(
                                "xero_quest edit_mode " + player.getGameProfile().getName() + " true",
                                player.server.createCommandSourceStack());
                        Path group = QuestRepository.root().resolve("group_" + GROUP);
                        Files.createDirectories(group);
                        Files.writeString(group.resolve("group_manifest.json"), "{\"id\":\"" + GROUP + "\",\"name\":\"Journey test\"}");
                        Files.writeString(group.resolve(A + ".html"), "<quest id=\"" + A
                                + "\" name=\"Recommended journey quest\" type=\"main\" recommended=\"true\" recommendation-level=\"99999\" icon=\"minecraft:chest\">"
                                + "<trigger type=\"confirm\"/></quest>");
                        Files.writeString(group.resolve(B + ".json"), "{\"id\":\"" + B
                                + "\",\"title\":\"Second quest\",\"type\":\"main\",\"icon\":\"minecraft:book\",\"parents\":[\"" + A + "\"]}");
                        Files.writeString(group.resolve(HIDDEN + ".html"), "<quest id=\"" + HIDDEN
                                + "\" name=\"Excluded quest\" journey-visible=\"false\" type=\"main\"/>");
                        var gson = new Gson();
                        var chapter = new com.google.gson.JsonObject();
                        chapter.addProperty("id", CHAPTER); chapter.addProperty("name", "Editable chapter");
                        chapter.addProperty("group", GROUP); chapter.add("quests", gson.toJsonTree(List.of()));
                        chapter.add("journeyBackgrounds", gson.toJsonTree(List.of(
                                new JourneyBackdrop(0, IMAGE_A, "image", 1, 0, 0, music))));
                        Files.writeString(group.resolve(CHAPTER + ".chapter.json"), chapter.toString());
                        chapter.addProperty("id", SECOND); chapter.addProperty("name", "Empty chapter");
                        chapter.add("journeyTimeline", gson.toJsonTree(new JourneyTimeline(Map.of(), List.of(), true, true)));
                        chapter.add("journeyBackgrounds", gson.toJsonTree(List.of(
                                new JourneyBackdrop(0, IMAGE_B, "image", 1, 0, 0, music))));
                        Files.writeString(group.resolve(SECOND + ".chapter.json"), chapter.toString());
                        QuestRepository.reload();
                        String added = GROUP + "_new";
                        require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                                added, "New automatic task", "", GROUP, CHAPTER, "main", "", 1,
                                "", "", "", 1, "manual")).successful(), "Could not create a chapter task");
                        require(QuestRepository.chapters().stream().filter(c -> c.id().equals(CHAPTER))
                                .findFirst().orElseThrow().quests().isEmpty(), "New task replaced automatic chapter membership");
                        String addedSource = QuestRepository.source(added);
                        require(QuestAuthoring.editSource(player, added, QuestRepository.fingerprint(addedSource),
                                QuestDocuments.journeyVisible(addedSource, false)).successful(),
                                "Could not disable journey visibility");
                        originalParents = gson.toJson(QuestRepository.find(B).parents());
                        require(QuestRepository.find(A).journeyVisible() && !QuestRepository.find(HIDDEN).journeyVisible(),
                                "Task visibility defaults/markup failed");
                        var catalog = QuestCatalog.decode(QuestCatalog.serverJson());
                        require(!catalog.quests().stream().filter(q -> q.id().equals(HIDDEN)).findFirst().orElseThrow().journeyVisible(),
                                "Task visibility lost in catalog");
                        var view = catalog.chapters().stream().filter(c -> c.id().equals(CHAPTER)).findFirst().orElseThrow();
                        var draft = new QuestJourneyNetwork.Draft(List.of(A, B), JourneyTimeline.empty(), view.backgrounds());
                        require(QuestAuthoring.editJourney(player, CHAPTER, "0".repeat(64), draft).equals("conflict"),
                                "Stale chapter edit accepted");
                        require(QuestAuthoring.editJourney(player, CHAPTER, view.fingerprint(),
                                new QuestJourneyNetwork.Draft(List.of("missing"), JourneyTimeline.empty(), List.of()))
                                .equals("invalid_config"), "Missing chapter task accepted");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                        game.player.registryAccess());
                try {
                    var value = ClientQuestDefinitions.chapters().stream().filter(c -> c.id().equals(CHAPTER))
                            .findFirst().orElseThrow();
                    var update = new QuestJourneyNetwork.Update(1, value.id(), value.fingerprint(),
                            new Gson().toJson(new QuestJourneyNetwork.Draft(List.of(A, B), value.timeline(), value.backgrounds())));
                    QuestJourneyNetwork.Update.CODEC.encode(buffer, update);
                    require(QuestJourneyNetwork.Update.CODEC.decode(buffer).equals(update), "Journey update codec failed");
                } finally { buffer.release(); }
                var overview = QuestScreen.overview(null);
                game.setScreen(overview);
                invoke(overview, "openOverviewTask", new Class<?>[]{String.class}, A);
                require(game.screen instanceof JourneyScreen, "Recommended quest opened task details");
                journey = (JourneyScreen) game.screen;
                require(chapterId().equals(CHAPTER) && journey.visibleQuestCount() == 2,
                        "Default chapter membership or focus failed");
                require(field(journey, "selectedQuest").equals(A), "Recommended quest lost focused highlight");
                require(!invoke(journey, "chapterIcon", new Class<?>[]{int.class}, index(CHAPTER)).equals(
                        invoke(journey, "chapterIcon", new Class<?>[]{int.class}, index(SECOND))),
                        "Chapter fallback icons are identical");
                require(((JourneyBackdrop) field(journey, "backdrop")).music().equals(music), "Chapter music missing");
            }
            case 2 -> {
                capture(game, "journey-recommendation-focused");
                oldPlayback = playback();
                require(oldPlayback != null, "Chapter did not start background music");
                invoke(journey, "selectChapter", new Class<?>[]{int.class}, index(SECOND));
                require(journey.visibleQuestCount() == 0 && (boolean) field(oldPlayback, "closed"),
                        "Switching chapter did not stop preceding music");
                require(((JourneyBackdrop) field(journey, "backdrop")).source().equals(IMAGE_B),
                        "Switching chapter did not switch backdrop");
            }
            case 3 -> {
                require(playback() != null && playback() != oldPlayback, "Same-source music did not restart in new chapter");
                capture(game, "journey-empty-on-timeline");
                invoke(journey, "selectChapter", new Class<?>[]{int.class}, index(CHAPTER));
                button("journey-edit-layout").onPress();
                require((boolean) field(journey, "editing"), "Timeline editor toggle failed");
                double[] point = point(A, 50, 30);
                journey.mouseClicked(point[0], point[1], 0);
                journey.mouseDragged(point[0] + 70, point[1] - 20, 0, 70, -20);
                journey.mouseReleased(point[0] + 70, point[1] - 20, 0);
            }
            case 4 -> {
                if (field(journey, "pending") != null) return false;
                require(chapter().timeline().positions().containsKey(A), "Dragged task position was not persisted");
                require(new Gson().toJson(QuestRepository.find(B).parents()).equals(originalParents),
                        "Dragging changed quest prerequisites");
                capture(game, "journey-edited-position");
                var source = slot(A);
                var target = slot(B);
                double[] from = anchor(source, true), to = anchor(target, false);
                journey.mouseClicked(from[0], from[1], 0);
                journey.mouseDragged(to[0], to[1], 0, to[0] - from[0], to[1] - from[1]);
                journey.mouseReleased(to[0], to[1], 0);
            }
            case 5 -> {
                if (field(journey, "pending") != null) return false;
                require(chapter().timeline().customConnections()
                        && chapter().timeline().connections().contains(new JourneyTimeline.Connection(A, B)),
                        "Display connection was not persisted");
                button("journey-tasks").onPress();
                require(game.screen instanceof QuestTaskPickerScreen && (boolean) field(game.screen, "multiSelect"),
                        "Journey picker did not use multi-select");
                @SuppressWarnings("unchecked") var chosen = (Set<String>) field(game.screen, "selectedIds");
                require(chosen.containsAll(Set.of(A, B, HIDDEN)), "Picker lost default chapter membership");
                chosen.clear();
                button("task-picker-confirm").onPress();
            }
            case 6 -> {
                if (game.screen != journey || field(journey, "pending") != null) return false;
                require(journey.visibleQuestCount() == 0 && chapter().timeline().explicitTasks(),
                        "Empty selection reverted to all chapter tasks");
                button("journey-tasks").onPress();
                @SuppressWarnings("unchecked") var chosen = (Set<String>) field(game.screen, "selectedIds");
                chosen.add(A); chosen.add(B);
                button("task-picker-confirm").onPress();
            }
            case 7 -> {
                if (game.screen != journey || field(journey, "pending") != null) return false;
                require(journey.visibleQuestCount() == 2, "Chapter task add failed");
                button("journey-edit-backgrounds").onPress();
                require(game.screen.getClass().getSimpleName().equals("JourneyBackgroundScreen"),
                        "Journey media editor did not open");
                require(game.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .count() == 6, "Background/music editor missing fields");
            }
            case 8 -> {
                if (!visualBackgroundChecked) {
                    button("journey-background-visual").onPress();
                    require(game.screen.getClass().getSimpleName().equals("HotEventBackgroundScreen"),
                            "Chapter background did not open the live visual editor");
                    @SuppressWarnings("unchecked") var inputs = (Map<String, net.minecraft.client.gui.components.EditBox>)
                            field(game.screen, "inputs");
                    inputs.get("background_offset_x").setValue("13");
                    ((StyledChoiceButton<String>) button("hot-background-mode")).select("contain");
                    button("hot-background-save").onPress();
                    require(game.screen.getClass().getSimpleName().equals("JourneyBackgroundScreen"),
                            "Visual backdrop draft did not return to the chapter segments");
                    visualBackgroundChecked = true;
                    return false;
                }
                capture(game, "journey-background-music-editor");
                button("journey-save").onPress();
            }
            case 9 -> {
                if (game.screen != journey || field(journey, "pending") != null) return false;
                work = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    var chapter = QuestRepository.chapters().stream().filter(c -> c.id().equals(CHAPTER)).findFirst().orElseThrow();
                    var network = QuestCatalog.decode(QuestCatalog.serverJson()).chapters().stream()
                            .filter(c -> c.id().equals(CHAPTER)).findFirst().orElseThrow();
                    require(network.timeline().equals(chapter.timeline()) && network.backgrounds().equals(chapter.backgrounds()),
                            "Journey metadata lost during catalog serialization");
                    require(chapter.backgrounds().getFirst().mode().equals("contain")
                                    && chapter.backgrounds().getFirst().offsetX() == 13,
                            "Chapter visual backdrop edit did not persist its mode/offset");
                    player.getPersistentData().putBoolean(xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime.EDIT_MODE_KEY, false);
                    require(QuestAuthoring.editJourney(player, CHAPTER, network.fingerprint(),
                            new QuestJourneyNetwork.Draft(List.of(), JourneyTimeline.empty(), List.of()))
                            .equals("permission"), "Disabled editor accepted journey changes");
                    player.getPersistentData().putBoolean(xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime.EDIT_MODE_KEY, true);
                }, game.getSingleplayerServer());
                resize(game, 640, 480);
                require(chapterId().equals(CHAPTER), "Resize changed focused chapter");
                require(button("journey-tasks").getRight() < button("journey-edit-layout").getX(),
                        "Compact timeline tools overlap");
            }
            case 10 -> {
                work.join();
                capture(game, "journey-editor-compact");
                game.setScreen(new QuestLootScreen(journey));
                verifyToolbar();
            }
            case 11 -> {
                capture(game, "loot-toolbar-compact");
                resize(game, 1920, 1080);
                verifyToolbar();
            }
            case 12 -> {
                capture(game, "loot-toolbar-desktop");
                game.setScreen(new HotEventsScreen(journey));
                game.screen.mouseClicked(game.screen.width - 51, 16, 0);
                require((boolean) field(game.screen, "editing"), "Hot-event edit toggle failed");
            }
            case 13 -> {
                capture(game, "hot-events-exit-edit-icon");
                try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    int cx = (game.screen.width - 51) * image.getWidth() / game.screen.width;
                    int cy = 16 * image.getHeight() / game.screen.height;
                    int dark = 0;
                    for (int dx = -8; dx <= 8; dx++) for (int dy = -8; dy <= 8; dy++) {
                        int pixel = image.getPixelRGBA(cx + dx, cy + dy);
                        int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                        if (r < 150 && g < 150 && b < 150 && r > 25) dark++;
                    }
                    require(dark > 8, "Exit-edit icon rendered blank");
                }
            }
            case 14 -> {
                return PresentationInteractionRegression.tick(game, journey, CHAPTER, A, music);
            }
            default -> throw new AssertionError("Journey edit stage " + stage);
        }
        stage++;
        return false;
    }

    private static QuestCatalog.Chapter chapter() throws Exception {
        @SuppressWarnings("unchecked") var chapters = (List<QuestCatalog.Chapter>) field(journey, "chapters");
        return chapters.get(index(CHAPTER));
    }
    private static String chapterId() throws Exception {
        @SuppressWarnings("unchecked") var chapters = (List<QuestCatalog.Chapter>) field(journey, "chapters");
        return chapters.get((int) field(journey, "chapterIndex")).id();
    }
    private static int index(String id) throws Exception {
        @SuppressWarnings("unchecked") var chapters = (List<QuestCatalog.Chapter>) field(journey, "chapters");
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).id().equals(id)) return i;
        throw new AssertionError("Missing chapter " + id);
    }
    private static Object slot(String id) throws Exception {
        for (var slot : (List<?>) field(journey, "slots"))
            if (((QuestDefinition) invoke(slot, "quest", new Class<?>[]{})).id().equals(id)) return slot;
        throw new AssertionError("Missing slot " + id);
    }
    private static double[] point(String id, int dx, int dy) throws Exception {
        var slot = slot(id);
        double scale = (double) invoke(journey, "scale", new Class<?>[]{});
        double px = ((int) invoke(journey, "slotX", new Class<?>[]{slot.getClass()}, slot)
                + dx - scroll("timelineX")) * scale;
        double py = (int) invoke(journey, "top", new Class<?>[]{})
                + ((int) invoke(journey, "slotY", new Class<?>[]{slot.getClass()}, slot)
                + dy - scroll("timelineY")) * scale;
        return new double[]{px, py};
    }
    private static double[] anchor(Object slot, boolean output) throws Exception {
        double scale = (double) invoke(journey, "scale", new Class<?>[]{});
        return new double[]{
                ((int) invoke(journey, "slotX", new Class<?>[]{slot.getClass()}, slot)
                        + (output ? 177 : -9) - scroll("timelineX")) * scale,
                (int) invoke(journey, "top", new Class<?>[]{})
                        + ((int) invoke(journey, "anchorY", new Class<?>[]{slot.getClass()}, slot)
                        - scroll("timelineY")) * scale};
    }
    private static double scroll(String field) throws Exception {
        return (double) invoke(field(journey, field), "position", new Class<?>[]{});
    }
    private static Object playback() throws Exception {
        var map = (Map<?, ?>) staticField(ClientMediaManager.class, "PLAYBACKS");
        return map.get(field(journey, "mediaOwner") + "\u0000music");
    }
    private static void verifyToolbar() {
        var undo = button("loot-undo");
        var redo = button("loot-redo");
        var close = button("loot-close");
        require(undo.getRight() < redo.getX() && redo.getRight() < close.getX()
                && close.getRight() <= Minecraft.getInstance().screen.width, "Loot toolbar overlaps");
    }
    private static void verifyRebound() throws Exception {
        var type = Class.forName("xtdpotato.xero_quest_journal.client.JourneyScroll");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object scroll = constructor.newInstance();
        invoke(scroll, "limit", new Class<?>[]{double.class}, 1000.);
        for (boolean end : List.of(false, true)) {
            invoke(scroll, "jump", new Class<?>[]{double.class}, end ? 1000. : 0.);
            invoke(scroll, "begin", new Class<?>[]{});
            invoke(scroll, "drag", new Class<?>[]{double.class}, end ? 1400. : -400.);
            double out = (double) invoke(scroll, "position", new Class<?>[]{});
            require(end ? out > 1000 && out < 1072 : out < 0 && out > -72, "No resisted overscroll");
            invoke(scroll, "range", new Class<?>[]{double.class, double.class}, 0., 1000.);
            require((double) invoke(scroll, "position", new Class<?>[]{}) == out, "Frame limits clipped overscroll");
            invoke(scroll, "release", new Class<?>[]{boolean.class}, true);
            invoke(scroll, "advance", new Class<?>[]{double.class, boolean.class}, .016, false);
            double intermediate = (double) invoke(scroll, "position", new Class<?>[]{});
            require(end ? intermediate < out && intermediate > 1000 : intermediate > out && intermediate < 0,
                    "Rebound skipped intermediate frames");
            for (int i = 0; i < 120; i++)
                invoke(scroll, "advance", new Class<?>[]{double.class, boolean.class}, 1. / 60, false);
            require((double) invoke(scroll, "position", new Class<?>[]{}) == (end ? 1000 : 0), "Rebound did not settle");
        }
    }
    private static StyledButton button(String id) {
        return Minecraft.getInstance().screen.children().stream().filter(child -> child instanceof StyledButton b
                && b.styleId().equals(id)).map(child -> (StyledButton) child).findFirst()
                .orElseThrow(() -> new AssertionError("Missing button " + id + " stage " + stage));
    }
    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static Object staticField(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static Object invoke(Object value, String name, Class<?>[] types, Object... args) throws Exception {
        var method = value.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(value, args);
    }
    private static void resize(Minecraft game, int w, int h) {
        game.options.guiScale().set(2);
        game.getWindow().setWindowed(w, h); game.getWindow().setWidth(w); game.getWindow().setHeight(h); game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        game.getToasts().clear();
        Path output = Path.of("..", "build", "verification", name + ".png");
        Files.createDirectories(output.getParent());
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(output); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (journey editing stage " + stage + ")");
    }
}
