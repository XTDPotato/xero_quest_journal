package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.ClientQuestDefinitions;
import xtdpotato.xero_quest_journal.client.JourneyScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.client.ClientPreferences;
import xtdpotato.xero_quest_journal.client.UiMotion;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class JourneyRegression {
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final String ID = "journey_continuous_smoke";
    private static final String FIRST = ID + "_first";
    private static final String SECOND = ID + "_second";
    private static final String IMAGE_A = "minecraft:textures/gui/title/background/panorama_0.png";
    private static final String IMAGE_B = "minecraft:textures/gui/title/background/panorama_1.png";
    private static int stage;
    private static int wait;
    private static CompletableFuture<Void> fixture;
    private static JourneyScreen journey;
    private static QuestScreen overview;
    private static boolean originalReducedMotion;
    private static int originalDuration;
    private static double originalScale;
    private static StyledButton card;
    private static int cardX, cardY;
    private static long heldAt;

    private JourneyRegression() {}

    static boolean tick(Minecraft game) throws Exception {
        xtdpotato.xero_quest_journal.client.MailArrivalToast.clear();
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                originalReducedMotion = ClientPreferences.get().reducedMotion;
                originalDuration = QuestConfig.defaults().uiAnimationMillis;
                ClientPreferences.get().reducedMotion = false;
                QuestConfig.defaults().uiAnimationMillis = 1000;
                game.options.pauseOnLostFocus = false;
                game.options.guiScale().set(2);
                verifyScrollTiming();
                fixture = new CompletableFuture<>();
                game.getSingleplayerServer().execute(() -> {
                    try {
                        Path group = QuestRepository.root().resolve("group_" + ID);
                        Files.createDirectories(group);
                        Files.writeString(group.resolve("group_manifest.json"),
                                "{\"id\":\"" + ID + "\",\"name\":\"Journey Smoke\"}");
                        StringBuilder quests = new StringBuilder();
                        for (int i = 0; i < 6; i++) {
                            String questId = ID + "_task_" + i;
                            if (i > 0) quests.append(',');
                            quests.append('"').append(questId).append('"');
                            Files.writeString(group.resolve(questId + ".html"),
                                    "<quest id=\"" + questId + "\" name=\"Journey " + i + "\" type=\"main\" icon=\"" + IMAGE_A
                                            + "\"><trigger type=\"confirm\"/></quest>");
                        }
                        for (int i = 0; i < 5; i++) {
                            String questId = ID + "_branch_" + i;
                            quests.append(",\"").append(questId).append('"');
                            Files.writeString(group.resolve(questId + ".json"),
                                    "{\"id\":\"" + questId + "\",\"title\":\"Side story " + i
                                            + "\",\"type\":\"legendary\",\"parents\":[\"" + ID + "_task_1\"]}");
                        }
                        Files.writeString(group.resolve(FIRST + ".chapter.json"),
                                "{\"id\":\"" + FIRST + "\",\"name\":\"First chapter\",\"group\":\"" + ID
                                        + "\",\"quests\":[" + quests + "],\"journeyBackgrounds\":["
                                        + "{\"startX\":0,\"source\":\"" + IMAGE_A + "\",\"type\":\"image\","
                                        + "\"scale\":1.0,\"offsetX\":0,\"offsetY\":0},"
                                        + "{\"startX\":220,\"source\":\"" + IMAGE_B + "\",\"type\":\"image\","
                                        + "\"scale\":1.1,\"offsetX\":3,\"offsetY\":-2}]}");
                        Files.writeString(group.resolve(SECOND + ".chapter.json"),
                                "{\"id\":\"" + SECOND + "\",\"name\":\"Second chapter\",\"group\":\"" + ID
                                        + "\",\"quests\":[\"" + ID + "_task_0\"],\"journeyBackgrounds\":["
                                        + "{\"startX\":0,\"source\":\"" + IMAGE_A + "\",\"type\":\"image\","
                                        + "\"scale\":1.0,\"offsetX\":0,\"offsetY\":0}]}");
                        for (int i = 0; i < 6; i++)
                            Files.writeString(group.resolve(ID + "_extra_" + i + ".chapter.json"),
                                    "{\"id\":\"" + ID + "_extra_" + i + "\",\"name\":\"Chapter " + (i + 3)
                                            + "\",\"group\":\"" + ID + "\",\"quests\":[]}");
                        QuestRepository.reload();
                        fixture.complete(null);
                    } catch (Exception failure) {
                        fixture.completeExceptionally(failure);
                    }
                });
                wait = 8;
            }
            case 1 -> {
                if (!fixture.isDone()) return false;
                fixture.join();
                var chapter = ClientQuestDefinitions.chapters().stream()
                        .filter(value -> value.id().equals(FIRST)).findFirst().orElseThrow();
                require(chapter.backgrounds().size() == 2
                                && chapter.backgrounds().get(1).offsetX() == 3,
                        "Chapter backgrounds did not load");
                var network = QuestCatalog.decode(QuestCatalog.serverJson()).chapters().stream()
                        .filter(value -> value.id().equals(FIRST)).findFirst().orElseThrow();
                require(network.backgrounds().equals(chapter.backgrounds()),
                        "Backgrounds did not survive catalog serialization");
                resize(game, 1920, 1080);
                overview = QuestScreen.overview(null);
                game.setScreen(overview);
                wait = 28;
            }
            case 2 -> {
                require(!overview.inputBlocked(), "Overview did not enter");
                card = button(overview, "overview-main");
                cardX = card.getX() + card.getWidth() / 2;
                cardY = card.getY() + card.getHeight() / 2;
                hover(game, cardX, cardY);
                wait = 8;
            }
            case 3 -> {
                require(((UiMotion) field(card, "lift")).position() == 1, "Card did not lift on hover");
                capture(game, "overview-card-hover");
                overview.mouseClicked(cardX, cardY, 0);
                heldAt = System.nanoTime();
                require(game.screen == overview && !overview.isClosing(), "Card navigated on press");
                wait = 5;
            }
            case 4 -> {
                card = button(overview, "overview-main");
                if (((UiMotion) field(card, "lift")).position() > 0 && System.nanoTime() - heldAt < 1_000_000_000L)
                    return false;
                require(((UiMotion) field(card, "lift")).position() == 0, "Held card did not settle: "
                        + ((UiMotion) field(card, "lift")).position() + ", held=" + field(card, "pointerHeld")
                        + ", focused=" + card.isFocused() + ", closing=" + overview.isClosing());
                capture(game, "overview-card-held");
                overview.mouseReleased(0, 0, 0);
                require(!(boolean) field(card, "activationPending") && game.screen == overview,
                        "Releasing outside activated card");
                wait = 8;
            }
            case 5 -> {
                card = button(overview, "overview-main");
                require(((UiMotion) field(card, "lift")).position() == 1, "Canceled card lost hover");
                overview.mouseClicked(cardX, cardY, 0);
                overview.mouseReleased(cardX, cardY, 0);
                overview.tick();
                require((boolean) field(card, "activationPending") && !overview.isClosing()
                                && game.screen == overview, "Screen closed before press animation completed");
                // A second category cannot replace the pending navigation.
                var other = button(overview, "overview-world");
                overview.mouseClicked(other.getX() + 3, other.getY() + 3, 0);
                require(!(boolean) field(other, "activationPending"), "Duplicate category click accepted");
                wait = 6;
            }
            case 6 -> {
                require(overview.isClosing() && game.screen == overview, "Press/exit animation order wrong");
                require(((UiMotion) field(card, "lift")).position() == 0, "Exit began with raised card");
                capture(game, "overview-card-exit");
                wait = 30;
            }
            case 7 -> {
                require(game.screen instanceof JourneyScreen, "Travel action did not open journey");
                journey = (JourneyScreen) game.screen;
                if (journey.inputBlocked()) return false;
                QuestConfig.defaults().uiAnimationMillis = 240;
                selectTab(FIRST);
                require(journey.chapterCount() >= 8 && journey.visibleQuestCount() == 11,
                        "Journey chapters/tasks missing");
                require((int) field(journey, "columns") == 6 && (int) field(journey, "branchRows") == 5,
                        "Actual side quests were not laid out as branches");
                wait = 14;
            }
            case 8 -> {
                capture(game, "journey-first");
                verifyCoverPixels(game);
                require(((JourneyBackdrop) field(journey, "backdrop")).source().equals(IMAGE_A),
                        "Initial chapter background missing");
                long changed = (long) field(journey, "backdropChanged");
                Object selectedMotion = motions().get(FIRST);
                selectTab(FIRST);
                require((long) field(journey, "backdropChanged") == changed
                                && selectedMotion == motions().get(FIRST) && motions().get(FIRST).position() == 1,
                        "Clicking selected tab restarted animation");
                int selected = (int) field(journey, "chapterIndex");
                int tx = (int) invoke(journey, "tabsRight") - 8, ty = (int) invoke(journey, "tabsTop") + 16;
                journey.mouseClicked(tx, ty, 0);
                journey.mouseDragged(tx - 100, ty, 0, -100, 0);
                journey.mouseDragged(tx, ty, 0, 100, 0);
                journey.mouseReleased(tx, ty, 0);
                require((int) field(journey, "chapterIndex") == selected, "Tab swipe triggered click");
                invoke(field(journey, "tabs"), "jump", new Class<?>[]{double.class}, 0.);
                journey.mouseScrolled(tx, ty, 0, -2);
                require((double) field(field(journey, "tabs"), "target") > scroll("tabs"),
                        "Tabs do not smooth-scroll");
                double wheelX = journey.width / 2.0, wheelY = journey.height / 2.0;
                double worldXBeforeWheel = scroll("timelineX") + wheelX / (double) invoke(journey, "scale");
                double worldYBeforeWheel = scroll("timelineY")
                        + (wheelY - (int) invoke(journey, "top")) / (double) invoke(journey, "scale");
                require(journey.mouseScrolled(journey.width / 2, journey.height / 2, -2, 0),
                        "Journey wheel not handled");
                require((double) field(journey, "timelineZoom") < 1
                                && Math.abs(scroll("timelineX") + wheelX / (double) invoke(journey, "scale")
                                - worldXBeforeWheel) < .01
                                && Math.abs(scroll("timelineY") + (wheelY - (int) invoke(journey, "top"))
                                / (double) invoke(journey, "scale") - worldYBeforeWheel) < .01,
                        "Content wheel panned the timeline instead of zooming at the pointer");
                journey.mouseScrolled(journey.width / 2, journey.height / 2, 2, 0);
                require(Math.abs((double) field(journey, "timelineZoom") - 1) < .001,
                        "Reverse wheel did not restore timeline zoom");
                invoke(field(journey, "timelineX"), "by", new Class<?>[]{double.class}, 576.);
                wait = 20;
            }
            case 9 -> {
                require(((JourneyBackdrop) field(journey, "backdrop")).source().equals(IMAGE_B),
                        "Scroll did not switch backdrop");
                require(scroll("tabs") > 0, "Tab strip did not scroll");
                capture(game, "journey-scrolled");
                selectTab(SECOND);
                require(journey.visibleQuestCount() == 1
                                && ((JourneyBackdrop) field(journey, "backdrop")).source().equals(IMAGE_A),
                        "Chapter did not load independently configured background");
                require(motions().get(FIRST).position() == 1 && !motions().get(FIRST).visible()
                                && motions().get(SECOND).position() == 0 && motions().get(SECOND).visible(),
                        "Tab underline did not start reversible center expansion/contraction");
                require(field(field(journey, "backdropSnapshot"), "surface") != null
                                && (float) invoke(journey, "backdropAlpha") < .1,
                        "Chapter switch discarded preceding background instead of crossfading");
                wait = 1;
            }
            case 10 -> {
                require(motions().get(SECOND).position() > 0 && motions().get(SECOND).position() < 1,
                        "Incoming underline had no intermediate frames");
                require(motions().get(FIRST).position() < 1 && motions().get(FIRST).position() > 0,
                        "Outgoing underline did not contract");
                capture(game, "journey-tab-transition");
                selectTab(FIRST);
                require(journey.visibleQuestCount() == 11
                                && ((JourneyBackdrop) field(journey, "backdrop")).source().equals(IMAGE_A),
                        "Returning to the first tab did not restore its start");
                wait = 12;
            }
            case 11 -> {
                double sx = journey.width / 2., sy = journey.height / 2.;
                journey.mouseClicked(sx, sy, 0);
                journey.mouseDragged(sx - 60, sy - 35, 0, -60, -35);
                journey.mouseReleased(sx - 60, sy - 35, 0);
                require(scroll("timelineX") > 0 && scroll("timelineY") > 0
                                && game.screen == journey && !journey.isClosing(),
                        "Content swipe did not scroll both axes, or opened a quest");
                originalScale = (double) invoke(journey, "scale");
                ClientPreferences.get().reducedMotion = true;
                selectTab(SECOND);
                require(motions().get(SECOND).position() == 1 && motions().get(FIRST).position() == 0
                                && (float) invoke(journey, "backdropAlpha") == 1,
                        "Reduced motion did not remove animation delays");
                verifyContinuousNavigation();
                selectTab(SECOND);
                wait = 4;
            }
            case 12 -> {
                capture(game, "journey-second");
                resize(game, 640, 480);
                selectTab(FIRST);
                invoke(field(journey, "timelineY"), "jump", new Class<?>[]{double.class}, 0.);
                wait = 6;
            }
            case 13 -> {
                require((double) invoke(journey, "scale") < originalScale, "Small window did not scale proportionally");
                require((int) invoke(journey, "tabsLeft") > 0
                                && (int) invoke(journey, "tabsRight") < journey.width,
                        "Compact tabs outside viewport");
                capture(game, "journey-small");
                // Drag out and return onto a task must not turn a swipe into a click.
                double scale = (double) invoke(journey, "scale");
                var sections = (List<?>) field(journey, "sections");
                double start = (int) invoke(sections.get((int) field(journey, "chapterIndex")), "start");
                double savedX = scroll("timelineX");
                double x = (start + 192 + 84 - savedX) * scale;
                double y = (int) invoke(journey, "top")
                        + ((int) invoke(journey, "lineY") - 55 - scroll("timelineY")) * scale;
                journey.mouseClicked(x, y, 0);
                journey.mouseDragged(x - 25, y, 0, -25, 0);
                journey.mouseDragged(x, y, 0, 25, 0);
                journey.mouseReleased(x, y, 0);
                require(game.screen == journey, "Returning swipe fired quest click");
                invoke(field(journey, "timelineX"), "jump", new Class<?>[]{double.class}, savedX);
                journey.mouseClicked(x, y, 0);
                journey.mouseReleased(x, y, 0);
                require(game.screen instanceof QuestScreen, "Task did not activate on inside release");
                game.screen.onClose();
                require(game.screen == null, "Closing journey task details did not return to the game");
                game.setScreen(journey);
                journey.onClose();
                wait = 5;
            }
            case 14 -> {
                require(game.screen instanceof QuestScreen, "Journey close did not return to journal");
                verifyEditor(game, (QuestScreen) game.screen);
                wait = 12;
            }
            case 15 -> {
                require(game.screen instanceof QuestScreen, "Background editor did not return to parent");
                overview = QuestScreen.overview(null);
                game.setScreen(overview);
                card = button(overview, "overview-main");
                card.setFocused(true);
                card.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(game.screen == overview, "Keyboard activated card on press");
                card.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(game.screen instanceof JourneyScreen, "Reduced-motion keyboard release failed");
            }
            case 16 -> {
                if (!JourneyAppearanceRegression.tick(game, FIRST)) return false;
                ClientPreferences.get().reducedMotion = originalReducedMotion;
                QuestConfig.defaults().uiAnimationMillis = originalDuration;
                return true;
            }
            default -> throw new AssertionError("Journey test stage " + stage);
        }
        stage++;
        return false;
    }

    private static void verifyContinuousNavigation() throws Exception {
        Object horizontal = field(journey, "timelineX"), vertical = field(journey, "timelineY");
        int next = Math.min((int) field(journey, "chapterIndex"), journey.chapterCount() - 2);
        double boundary = (double) invoke(journey, "chapterPosition", new Class<?>[]{int.class}, next);
        invoke(horizontal, "jump", new Class<?>[]{double.class}, boundary - 20);
        invoke(journey, "followTimeline");
        require((int) field(journey, "chapterIndex") == next - 1, "Chapter activated before its line");
        int slotCount = ((List<?>) field(journey, "slots")).size();
        double scale = (double) invoke(journey, "scale"), x = journey.width / 2., y = journey.height / 2.;
        double savedY = scroll("timelineY");
        journey.mouseClicked(x, y, 0);
        journey.mouseDragged(x - 40 * scale, y, 0, -40 * scale, 0);
        require((int) field(journey, "chapterIndex") == next && (boolean) field(journey, "dragging")
                        && Math.abs(scroll("timelineX") - (boundary + 20)) < .01
                        && scroll("timelineY") == savedY && ((List<?>) field(journey, "slots")).size() == slotCount,
                "Crossing chapter line reset canvas: chapter=" + field(journey, "chapterIndex") + "/" + next
                        + ", dragging=" + field(journey, "dragging") + ", x=" + scroll("timelineX")
                        + "/" + (boundary + 20) + ", y=" + scroll("timelineY") + "/" + savedY
                        + ", slots=" + ((List<?>) field(journey, "slots")).size() + "/" + slotCount);
        journey.mouseDragged(x + 10 * scale, y, 0, 50 * scale, 0);
        require((int) field(journey, "chapterIndex") == next - 1, "Reverse drag did not select previous chapter");
        journey.mouseReleased(x + 10 * scale, y, 0);
        double initialZoom = (double) field(journey, "timelineZoom");
        double worldXBeforeWheel = scroll("timelineX") + x / scale;
        double worldYBeforeWheel = scroll("timelineY") + (y - (int) invoke(journey, "top")) / scale;
        require(journey.mouseScrolled(x, y, 0, 2), "Journey wheel not handled");
        double zoomedScale = (double) invoke(journey, "scale");
        require((double) field(journey, "timelineZoom") > initialZoom
                        && Math.abs(scroll("timelineX") + x / zoomedScale - worldXBeforeWheel) < .01
                        && Math.abs(scroll("timelineY") + (y - (int) invoke(journey, "top"))
                        / zoomedScale - worldYBeforeWheel) < .01,
                "Content wheel should zoom around the pointer without panning");
        journey.mouseScrolled(x, y, 0, -2);
        require(Math.abs((double) field(journey, "timelineZoom") - initialZoom) < .001,
                "Reverse wheel did not restore zoom");
        double zoomLeft = ((Number) invoke(journey, "zoomSliderLeft")).doubleValue();
        double zoomWidth = ((Number) invoke(journey, "zoomSliderWidth")).doubleValue();
        double zoomY = journey.height - 12;
        journey.mouseClicked(zoomLeft + zoomWidth - 3, zoomY, 0);
        require((double) field(journey, "timelineZoom") == 1.5, "Bottom slider did not zoom in");
        journey.mouseReleased(zoomLeft + zoomWidth - 3, zoomY, 0);
        journey.mouseClicked(zoomLeft + 3, zoomY, 0);
        require((double) field(journey, "timelineZoom") == .5, "Bottom slider did not zoom out");
        journey.mouseReleased(zoomLeft + 3, zoomY, 0);
        journey.mouseClicked(zoomLeft + zoomWidth / 2, zoomY, 0);
        require(Math.abs((double) field(journey, "timelineZoom") - 1) < .01,
                "Bottom slider did not restore default zoom");
        journey.mouseReleased(zoomLeft + zoomWidth / 2, zoomY, 0);

        invoke(vertical, "jump", new Class<?>[]{double.class}, 0.);
        double minY = (double) invoke(vertical, "minimum");
        double edgeY = (int) invoke(journey, "top") + 8;
        journey.mouseClicked(journey.width - 5, edgeY, 0);
        journey.mouseDragged(journey.width - 5, edgeY, 0, 0, 0);
        journey.mouseReleased(journey.width - 5, edgeY, 0);
        require(scroll("timelineY") == minY,
                "Invisible right-edge scroll control no longer pans to its boundary");
        invoke(horizontal, "jump", new Class<?>[]{double.class}, 1e9);
        invoke(journey, "followTimeline");
        require((int) field(journey, "chapterIndex") == journey.chapterCount() - 1, "Final chapter inaccessible");
        journey.mouseScrolled(x, y, -10000, 0);
        require((double) field(journey, "timelineZoom") == .5,
                "Horizontal wheel did not zoom the timeline");
        invoke(journey, "setTimelineZoom", new Class<?>[]{double.class, double.class, double.class},
                1.0, journey.width / 2.0, journey.height / 2.0);
        invoke(horizontal, "jump", new Class<?>[]{double.class}, -1e9);
        invoke(journey, "followTimeline");
        require(scroll("timelineX") == 0 && (int) field(journey, "chapterIndex") == 0,
                "Timeline start boundary failed: x=" + scroll("timelineX") + ", chapter="
                        + field(journey, "chapterIndex") + ", zoom=" + field(journey, "timelineZoom")
                        + ", target=" + invoke(horizontal, "target"));
        invoke(vertical, "jump", new Class<?>[]{double.class}, 0.);
    }

    @SuppressWarnings("unchecked")
    private static void verifyEditor(Minecraft game, QuestScreen parent) throws Exception {
        Class<?> type = Class.forName("xtdpotato.xero_quest_journal.client.JourneyBackgroundScreen");
        Constructor<?> constructor = type.getDeclaredConstructor(QuestScreen.class, List.class, Consumer.class);
        constructor.setAccessible(true);
        AtomicReference<List<JourneyBackdrop>> saved = new AtomicReference<>();
        var editor = (net.minecraft.client.gui.screens.Screen) constructor.newInstance(parent,
                List.of(new JourneyBackdrop(0, IMAGE_A, "image", 1, 0, 0),
                        new JourneyBackdrop(0, IMAGE_B, "image", 1, 0, 0)),
                (Consumer<List<JourneyBackdrop>>) saved::set);
        game.setScreen(editor);
        require(editor.children().stream().filter(EditBox.class::isInstance).count() == 6,
                "Background fields not initialized");
        capture(game, "journey-background-editor");
        button(editor, "journey-save").onPress();
        require(game.screen == editor && saved.get() == null, "Duplicate background position accepted");
        button(editor, "journey-segment-1").onPress();
        var start = editor.children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
        start.setValue("220");
        button(editor, "journey-save").onPress();
        require(saved.get() != null && saved.get().size() == 2 && saved.get().get(1).startX() == 220,
                "Background edit was not saved");
    }

    private static StyledButton button(net.minecraft.client.gui.screens.Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(value -> value.styleId().equals(id))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void selectTab(String id) throws Exception {
        var chapters = (List<QuestCatalog.Chapter>) field(journey, "chapters");
        int index = -1;
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).id().equals(id)) index = i;
        require(index >= 0, "Missing chapter tab " + id);
        int left = (int) invoke(journey, "tabsLeft"), w = (int) invoke(journey, "tabWidth");
        invoke(field(journey, "tabs"), "jump", new Class<?>[]{double.class}, (double) (index * w));
        double x = left + index * w - scroll("tabs") + w / 2.;
        double y = (int) invoke(journey, "tabsTop") + 16;
        journey.mouseClicked(x, y, 0);
        journey.mouseReleased(x, y, 0);
        require((int) field(journey, "chapterIndex") == index, "Tab release did not select chapter");
    }

    private static Object field(Object target, String name) throws Exception {
        var member = target.getClass().getDeclaredField(name);
        member.setAccessible(true);
        return member.get(target);
    }

    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static double scroll(String key) throws Exception { return (double) invoke(field(journey, key), "position"); }
    @SuppressWarnings("unchecked")
    private static Map<String, UiMotion> motions() throws Exception {
        return (Map<String, UiMotion>) field(journey, "tabMotions");
    }
    private static void hover(Minecraft game, int x, int y) throws Exception {
        for (var entry : Map.of("xpos", x * game.getWindow().getScreenWidth() / (double) game.screen.width,
                "ypos", y * game.getWindow().getScreenHeight() / (double) game.screen.height).entrySet()) {
            var f = game.mouseHandler.getClass().getDeclaredField(entry.getKey());
            f.setAccessible(true);
            f.setDouble(game.mouseHandler, entry.getValue());
        }
    }
    private static void resize(Minecraft game, int width, int height) {
        game.getWindow().setWindowed(width, height);
        // The unattended window may not receive native resize callbacks until it regains focus.
        game.getWindow().setWidth(width);
        game.getWindow().setHeight(height);
        game.resizeDisplay();
    }
    private static void verifyCoverPixels(Minecraft game) throws Exception {
        double scale = (double) invoke(journey, "scale");
        double sx = game.getMainRenderTarget().width / (double) journey.width;
        double sy = game.getMainRenderTarget().height / (double) journey.height;
        int top = (int) invoke(journey, "top");
        int y = (int) invoke(journey, "lineY") - 112 + 13;
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            int painted = 0;
            for (int column = 0; column < 3; column++)
                for (int dx : new int[]{12, 50, 100, 150}) {
                    int px = (int) ((192 + column * 230 + dx) * scale * sx);
                    int py = (int) ((top + (y + 34) * scale) * sy);
                    int rgba = image.getPixelRGBA(px, py);
                    if ((rgba & 0xFFFFFF) != 0x463C30) painted++;
                }
            require(painted == 12, "Scaled covers were clipped using untransformed screen coordinates");
        }
    }
    private static void verifyScrollTiming() throws Exception {
        Class<?> type = Class.forName("xtdpotato.xero_quest_journal.client.JourneyScroll");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object a = constructor.newInstance(), b = constructor.newInstance();
        for (Object scroll : List.of(a, b)) {
            invoke(scroll, "limit", new Class<?>[]{double.class}, 1000.);
            invoke(scroll, "to", new Class<?>[]{double.class}, 700.);
        }
        for (int i = 0; i < 30; i++) invoke(a, "advance", new Class<?>[]{double.class, boolean.class}, 1. / 30, false);
        for (int i = 0; i < 144; i++) invoke(b, "advance", new Class<?>[]{double.class, boolean.class}, 1. / 144, false);
        require(Math.abs((double) invoke(a, "position") - (double) invoke(b, "position")) < .03,
                "Scroll speed depended on frame rate");
        invoke(a, "by", new Class<?>[]{double.class}, 10000.);
        invoke(a, "advance", new Class<?>[]{double.class, boolean.class}, .016, true);
        require((double) invoke(a, "position") == 1000., "Scroll clamp/reduced motion failed");
    }

    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (journey stage " + stage + ")");
    }
}
