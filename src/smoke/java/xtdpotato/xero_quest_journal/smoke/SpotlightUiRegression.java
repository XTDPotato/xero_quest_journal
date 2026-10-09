package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.HotEventStore;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "xero_quest_journal", value = Dist.CLIENT)
final class SpotlightUiRegression {
    private static final int COUNT = 1200;
    private static final String GROUP = "spotlight_ui_fixture";
    private static final String DESCRIPTION = "A survival milestone with a complete explanation. "
            + "Explore, obtain the required supplies and keep the objective in mind before claiming the reward.";
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static int stage, wait, visibleCount;
    private static CompletableFuture<Void> work;
    private static QuestScreen overview, task;
    private static HotEventsScreen hot;
    private static JourneyScreen journey;
    private static Object cardIndex;
    private static String captureName = "";
    private static Screen captureScreen;

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) throws Exception {
        if (captureName.isEmpty() || event.getScreen() != captureScreen) return;
        event.getGuiGraphics().flush();
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(captureName + ".png"));
        }
        captureName = "";
        captureScreen = null;
    }

    static boolean tick(Minecraft game) throws Exception {
        if (!captureName.isEmpty() || --wait > 0) return false;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                ClientEditMode.set(false);
                game.options.pauseOnLostFocus = false;
                game.options.guiScale().set(2);
                GLFW.glfwRestoreWindow(game.getWindow().getWindow());
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1536, 864);
                game.resizeDisplay();
                work = CompletableFuture.runAsync(() -> {
                    try {
                        cleanup();
                        Path group = QuestRepository.root().resolve(GROUP);
                        Files.createDirectories(group);
                        Files.writeString(group.resolve("group_manifest.json"), "{\"id\":\"" + GROUP
                                + "\",\"name\":\"Spotlight UI\",\"type\":\"main\"}");
                        var ids = new ArrayList<String>();
                        for (int i = 0; i < COUNT; i++) {
                            ids.add(id(i));
                            var data = new com.google.gson.JsonObject();
                            data.addProperty("id", id(i));
                            data.addProperty("title", title(i));
                            data.addProperty("description", DESCRIPTION);
                            data.addProperty("type", "main");
                            data.addProperty("icon", "minecraft:diamond");
                            data.addProperty("defaultUnlocked", true);
                            data.add("triggers", com.google.gson.JsonParser.parseString(
                                    "[{\"type\":\"confirm\",\"count\":1}]"));
                            data.add("hotEvent", com.google.gson.JsonParser.parseString(
                                    "{\"enabled\":true,\"positioned\":true,\"x\":" + (48 + i * 228) + ",\"y\":12}"));
                            Files.writeString(group.resolve(id(i) + ".json"), data.toString());
                        }
                        Files.writeString(group.resolve("spotlight_ui.chapter.json"), "{\"id\":\"spotlight_ui\","
                                + "\"name\":\"Spotlight itinerary\",\"group\":\"" + GROUP + "\",\"quests\":"
                                + new com.google.gson.Gson().toJson(ids) + "}");
                        QuestRepository.reload();
                        require(QuestRepository.errors().isEmpty(), "UI fixture definitions invalid");
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        require(HotEventStore.update(player, new HotEventCanvas("", "", List.of(
                                new HotEventCanvas.Connection("quest:" + id(0), "quest:" + id(COUNT - 1))), true)),
                                "Cross-viewport connection fixture failed");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                if (ClientQuestProgress.view(id(0)) == null) return false;
                ClientEditMode.set(false);
                overview = QuestScreen.overview(null);
                game.setScreen(overview);
                button(overview, "overview-hot_events").onPress();
                require(game.screen instanceof HotEventsScreen, "Player mode did not open hot events");
                hot = (HotEventsScreen) game.screen;
                require(!hot.isClosing() && !(boolean) field(hot, "editing"), "Player canvas closed or enabled editing");
                require(hot.visibleCardCount() == COUNT, "Hot canvas omitted tasks");
                cardIndex = field(hot, "cardIndex");
                Object card = invoke(hot, "findCard", new Class<?>[]{String.class}, "quest:" + id(0));
                ComponentChecks(invoke(hot, "cardTooltip", new Class<?>[]{card.getClass()}, card).toString());
                require(!ClientHotEvents.save(HotEventCanvas.empty()), "Disabled author mode submitted a canvas mutation");
                cursor(game, sx(100), sy(55));
                capture(game, "spotlight-player-tooltip");
            }
            case 2 -> {
                hot.mouseClicked(hot.width - 50, 10, 0);
                require(!(boolean) field(hot, "editing") && !(boolean) invoke(hot, "isAdmin", new Class<?>[]{}),
                        "Player mode enabled editing tools");
                assertVisibleCards();
                double x = hot.width / 2., y = hot.height / 2.;
                require(hot.mouseClicked(x, y, 0) && hot.mouseDragged(x - 600, y, 0, -600, 0),
                        "Large canvas drag was not handled");
                hot.mouseReleased(x - 600, y, 0);
                require(field(hot, "cardIndex") == cardIndex, "Scrolling rebuilt the card index");
                assertVisibleCards();
                @SuppressWarnings("unchecked") var values = (List<Object>) invoke(hot, "visibleCards", new Class<?>[]{});
                require(values.stream().noneMatch(card -> {
                    try { return invoke(card, "id", new Class<?>[]{}).equals("quest:" + id(0)); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }), "Off-screen first card remained in the query");
                var edge = ClientQuestDefinitions.hotEventCanvas().connections().getFirst();
                var points = (double[]) invoke(hot, "connectionPoints",
                        new Class<?>[]{HotEventCanvas.Connection.class}, edge);
                var clipped = ViewportIndex.clipSegment(points[0], points[1], points[2], points[3],
                        0, 35, hot.width, hot.height - 34);
                require(clipped != null && Math.hypot(clipped[2] - clipped[0], clipped[3] - clipped[1])
                        <= Math.hypot(hot.width, hot.height), "Long link rasterization was not bounded");
                cursor(game, 0, 0);
                capture(game, "spotlight-player-scrolled");
            }
            case 3 -> {
                hot.mouseClicked(sx(48 + 3 * 228 + 30), sy(40), 0);
                hot.mouseReleased(sx(48 + 3 * 228 + 30), sy(40), 0);
                require(game.screen instanceof QuestScreen, "Hot task did not open details");
                task = (QuestScreen) game.screen;
                require(field(task, "parent") == null && field(task, "selectedId").equals(id(3)),
                        "Hot task retained a history screen or selected another task");
                require(button(task, "quest-" + id(3)).getTooltip() != null, "Player journal row has no tooltip");
                cursor(game, button(task, "quest-" + id(3)).getX() + 45,
                        button(task, "quest-" + id(3)).getY() + 10);
                capture(game, "spotlight-task-tooltip");
            }
            case 4 -> {
                task.onClose();
                require(game.screen == null, "Closing hotspot task restored the hotspot canvas");
                journey = new JourneyScreen(new QuestScreen(null), id(0));
                game.setScreen(journey);
                double[] point = journeyPoint(id(0));
                cursor(game, point[0], point[1]);
                capture(game, "spotlight-journey-tooltip");
            }
            case 5 -> {
                var query = (List<?>) invoke(journey, "visibleSlots",
                        new Class<?>[]{double.class, double.class, double.class, double.class},
                        journeyScroll("timelineX"), journeyScroll("timelineY"),
                        journeyScroll("timelineX") + (double) invoke(journey, "viewWidth", new Class<?>[]{}),
                        journeyScroll("timelineY") + (double) invoke(journey, "viewHeight", new Class<?>[]{}));
                require(query.size() < 20, "Journey queried off-screen task cards");
                double[] point = journeyPoint(id(0));
                journey.mouseClicked(point[0], point[1], 0);
                journey.mouseReleased(point[0], point[1], 0);
                require(game.screen instanceof QuestScreen, "Journey task click did not open details");
                task = (QuestScreen) game.screen;
                require(field(task, "parent") == null && field(task, "selectedId").equals(id(0)),
                        "Journey task details retained the journey as a back target");
                capture(game, "spotlight-journey-task-details");
            }
            case 6 -> {
                task.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(game.screen == null, "Escape from journey task did not close to game");
                task = QuestScreen.task(null, id(0));
                game.setScreen(task);
                var close = button(task, "close");
                task.mouseClicked(close.getX() + 8, close.getY() + 8, 0);
                task.mouseReleased(close.getX() + 8, close.getY() + 8, 0);
                require(game.screen == null, "Close icon restored an old journey or journal");
                work = CompletableFuture.runAsync(() -> {
                    try { cleanup(); QuestRepository.reload(); HotEventStore.get(); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 7 -> {
                if (!work.isDone()) return false;
                work.join();
                Files.createDirectories(OUTPUT);
                Files.writeString(OUTPUT.resolve("spotlight-ui-result.txt"),
                        "PASS: player-mode hotspot access, author mutation denial, full title/description tooltips,\n"
                                + "1200 hot cards with at most " + visibleCount + " viewport candidates,\n"
                                + "cached scrolling, clipped crossing links, journey visible-node queries,\n"
                                + "journey-to-task Escape/close returns to game rather than a history screen.\n");
                return true;
            }
            default -> throw new AssertionError("Spotlight stage " + stage);
        }
        stage++;
        wait = 5;
        return false;
    }

    private static void ComponentChecks(String tooltip) {
        require(tooltip.contains(DESCRIPTION), "Hot tooltip discarded the description");
        require(tooltip.contains(title(0)), "Hot tooltip truncated the full title");
    }
    private static void assertVisibleCards() throws Exception {
        var visible = (List<?>) invoke(hot, "visibleCards", new Class<?>[]{});
        visibleCount = Math.max(visibleCount, visible.size());
        require(!visible.isEmpty() && visible.size() < 12, "Hot canvas queried all " + COUNT + " cards");
    }
    private static String id(int i) { return String.format(Locale.ROOT, "spotlight_ui_%04d", i); }
    private static String title(int i) {
        return "Milestone " + i + " with a long complete title that remains available when the card label is clipped";
    }
    private static double sx(double x) throws Exception {
        return (double) invoke(hot, "screenX", new Class<?>[]{double.class}, x);
    }
    private static double sy(double y) throws Exception {
        return (double) invoke(hot, "screenY", new Class<?>[]{double.class}, y);
    }
    private static double journeyScroll(String name) throws Exception {
        return (double) invoke(field(journey, name), "position", new Class<?>[]{});
    }
    private static double[] journeyPoint(String id) throws Exception {
        for (var slot : (List<?>) field(journey, "slots")) {
            if (!((QuestDefinition) invoke(slot, "quest", new Class<?>[]{})).id().equals(id)) continue;
            double scale = (double) invoke(journey, "scale", new Class<?>[]{});
            return new double[]{
                    ((int) invoke(journey, "slotX", new Class<?>[]{slot.getClass()}, slot) + 40
                            - journeyScroll("timelineX")) * scale,
                    (int) invoke(journey, "top", new Class<?>[]{})
                            + ((int) invoke(journey, "slotY", new Class<?>[]{slot.getClass()}, slot) + 30
                            - journeyScroll("timelineY")) * scale};
        }
        throw new AssertionError("Missing journey slot " + id);
    }
    private static void cleanup() throws Exception {
        Path group = QuestRepository.root().resolve(GROUP);
        for (int i = 0; i < COUNT; i++) Files.deleteIfExists(group.resolve(id(i) + ".json"));
        Files.deleteIfExists(group.resolve("spotlight_ui.chapter.json"));
        Files.deleteIfExists(group.resolve("group_manifest.json"));
        if (Files.isDirectory(group)) try (var files = Files.list(group)) {
            if (files.findAny().isEmpty()) Files.delete(group);
        }
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing " + id));
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        var method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(object, args);
    }
    private static void cursor(Minecraft game, double x, double y) throws Exception {
        double wx = x * game.getWindow().getScreenWidth() / game.screen.width;
        double wy = y * game.getWindow().getScreenHeight() / game.screen.height;
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), wx, wy);
        for (var entry : Map.of("xpos", wx, "ypos", wy).entrySet()) {
            var field = game.mouseHandler.getClass().getDeclaredField(entry.getKey());
            field.setAccessible(true); field.setDouble(game.mouseHandler, entry.getValue());
        }
    }
    private static void capture(Minecraft game, String name) { captureName = name; captureScreen = game.screen; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (spotlight stage " + stage + ")");
    }
}
