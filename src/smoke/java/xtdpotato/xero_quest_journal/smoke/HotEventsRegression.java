package xtdpotato.xero_quest_journal.smoke;

import xtdpotato.xero_quest_journal.client.JourneyScreen;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.AnimatedQuestScreen;
import xtdpotato.xero_quest_journal.client.ClientQuestDefinitions;
import xtdpotato.xero_quest_journal.client.ClientQuestProgress;
import xtdpotato.xero_quest_journal.client.HotEventsScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class HotEventsRegression {
    private static final String SUFFIX = Long.toString(System.currentTimeMillis());
    private static final String GROUP_ID = "hot_smoke_group_" + SUFFIX;
    private static final String OTHER_GROUP_ID = "hot_smoke_other_" + SUFFIX;
    private static final String QUEST_ID = "hot_smoke_task_" + SUFFIX;
    private static final String CHILD_ID = "hot_smoke_child_" + SUFFIX;
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static int stage;
    private static int wait;
    private static CompletableFuture<Void> fixture;
    private static CompletableFuture<Void> languageReload;
    private static QuestScreen overview;
    private static HotEventsScreen screen;
    private static double settledX;
    private static int animationDuration;

    private HotEventsRegression() {}

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                animationDuration = QuestConfig.defaults().uiAnimationMillis;
                QuestConfig.defaults().uiAnimationMillis = 0;
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                languageReload = game.reloadResourcePacks();
                fixture = new CompletableFuture<>();
                game.getSingleplayerServer().execute(() -> {
                    try {
                        Path root = QuestRepository.root().toAbsolutePath().normalize();
                        Files.createDirectories(root);
                        try (var entries = Files.list(root)) {
                            for (Path entry : entries.toList()) {
                                if (Files.isDirectory(entry, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                                        && !Files.isSymbolicLink(entry)
                                        && entry.getFileName().toString().startsWith("group_hot_smoke_")) {
                                    try (var walk = Files.walk(entry)) {
                                        for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                                            Files.deleteIfExists(path);
                                        }
                                    }
                                }
                            }
                        }
                        Files.deleteIfExists(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                                .resolve("xero_quest/hot_events.json"));
                        xtdpotato.xero_quest_journal.core.HotEventStore.reload();
                        Path directory = root.resolve("group_" + GROUP_ID);
                        Files.createDirectories(directory);
                        Files.writeString(directory.resolve("group_manifest.json"),
                                "{\"id\":\"" + GROUP_ID + "\",\"name\":\"Hot Smoke Group\","
                                        + "\"type\":\"active\",\"icon\":\"minecraft:diamond\","
                                        + "\"hotEvent\":{\"enabled\":true,"
                                        + "\"title\":\"\",\"image\":\"\",\"x\":40,\"y\":12,"
                                        + "\"positioned\":true,\"hideImage\":true}}");
                        Files.writeString(directory.resolve("quest_" + CHILD_ID + ".html"),
                                "<quest id=\"" + CHILD_ID + "\" name=\"Grouped hot task\" "
                                        + "type=\"normal\"><trigger type=\"confirm\"/>"
                                        + "<content>Represented by its hot group</content>"
                                        + "<hot-event enabled=\"true\" title=\"Hidden group member\"/></quest>");
                        Path other = root.resolve("group_" + OTHER_GROUP_ID);
                        Files.createDirectories(other);
                        Files.writeString(other.resolve("group_manifest.json"),
                                "{\"id\":\"" + OTHER_GROUP_ID + "\",\"name\":\"Other smoke group\","
                                        + "\"type\":\"active\"}");
                        Files.writeString(other.resolve("quest_" + QUEST_ID + ".html"),
                                "<quest id=\"" + QUEST_ID + "\" name=\"Hot Smoke Task\" "
                                        + "type=\"normal\" recommended=\"true\"><trigger type=\"confirm\"/>"
                                        + "<content>Hot event smoke fixture</content>"
                                        + "<hot-event enabled=\"true\" title=\"Hot Smoke Task\" "
                                        + "image=\"minecraft:textures/gui/title/background/panorama_0.png\" "
                                        + "x=\"300\" y=\"12\" positioned=\"true\"/></quest>");
                        QuestRepository.reload();
                        fixture.complete(null);
                    } catch (Exception failure) {
                        fixture.completeExceptionally(failure);
                    }
                });
                wait = 6;
            }
            case 1 -> {
                if (!fixture.isDone() || !languageReload.isDone()) return false;
                fixture.join();
                languageReload.join();
                verifyTranslations(game);
                require(!QuestRepository.groups().stream().filter(group -> group.id().equals(GROUP_ID))
                                .findFirst().orElseThrow().recommended()
                                && QuestRepository.find(QUEST_ID).recommended(),
                        "Hot-event and recommended flags were not loaded independently");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1920, 1080);
                game.resizeDisplay();
                overview = QuestScreen.overview(null);
                game.setScreen(overview);
                cursor(game, 0, 0);
                wait = 20;
            }
            case 2 -> {
                require(overview.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).noneMatch(button -> button.styleId().equals("open-task")),
                        "Overview still has the redundant open-task action");
                assertOverviewEntries(overview);
                StyledButton feature = find(overview, "overview-feature");
                StyledButton firstType = find(overview, "overview-main");
                StyledButton hotButton = find(overview, "overview-hot_events");
                StyledButton lastType = find(overview, "overview-world");
                require(feature.getWidth() > feature.getHeight()
                        && firstType.getX() > feature.getRight()
                                && firstType.getY() == feature.getY()
                                && Math.abs(feature.getBottom() - lastType.getBottom()) <= 2
                                && hotButton.getRight() <= overview.width - 16
                                && hotButton.getBottom() <= overview.height - 28,
                        "Overview geometry: feature " + feature.getX() + "," + feature.getY()
                                + " " + feature.getWidth() + "x" + feature.getHeight()
                                + ", first type y=" + firstType.getY()
                                + ", last bottom=" + lastType.getBottom());
                for (var child : overview.children()) if (child instanceof StyledButton row
                        && isOverviewEntry(row))
                    require(row.getHeight() == hotButton.getHeight(), "Hot-event button height differs from task types");
                capture(game, "hot-events-overview-feature");
                cursor(game, feature.getX() + feature.getWidth() / 2, feature.getY() + 12);
                stage = 17; // Exercise hover and hub navigation before entering the hot-events canvas.
                wait = 12;
            }
            case 3 -> {
                require(screen.visibleCardCount() == 2, "A hot group must represent its member tasks");
                var cards = (List<?>) field(screen, "cards");
                require(cards.stream().noneMatch(card -> card.toString().contains("quest:" + CHILD_ID))
                                && cards.stream().anyMatch(card -> card.toString().contains("group:" + GROUP_ID)
                                && card.toString().contains("hideImage=true")),
                        "Grouped tasks or image-free group were rendered incorrectly");
                require(field(screen, "parent") == overview, "Hot events lost their overview parent");
                require(Math.abs(sy(67) - screen.height / 2.0) < .001, "Quest line is not vertically centred");
                verifyZoom();
                cursor(game, sx(90), sy(67));
                wait = 4;
            }
            case 4 -> {
                capture(game, "hot-events-overview");
                require(screen.mouseClicked(screen.width - 50, 10, 0), "Could not enter edit mode");
                require(clickCanvas(70, 67, 1), "Could not open the node menu");
                chooseContext(1);
                wait = 6;
            }
            case 5 -> {
                require(ClientQuestDefinitions.hotEventCanvas().startId().equals("group:" + GROUP_ID),
                        "Starting point did not synchronize from the server");
                require(ClientQuestDefinitions.hotEventCanvas().connections().size() == 1,
                        "New hot-event card was not linked to its predecessor");
                var edge = ClientQuestDefinitions.hotEventCanvas().connections().getFirst();
                require(edge.from().equals("group:" + GROUP_ID) && edge.to().equals("quest:" + QUEST_ID),
                        "Automatic chain included a hidden group member");
                var line = HotEventsScreen.class.getDeclaredMethod("connectionPoints",
                        xtdpotato.xero_quest_journal.core.model.HotEventCanvas.Connection.class);
                line.setAccessible(true);
                double[] points = (double[]) line.invoke(screen, edge);
                require(points.length == 4 && Math.abs(points[1] - points[3]) < .001,
                        "Default hot-event connection is not a horizontal straight line");
                capture(game, "hot-events-edit-grid");
                require(clickCanvas(262, 67, 1), "Could not open default connection menu");
                chooseContext(0);
                require(clickCanvas(224, 67, 0), "Could not grab output port");
                require(screen.mouseDragged(sx(300), sy(67), 0, 76, 0), "Could not drag a connection");
                require(screen.mouseReleased(sx(300), sy(67), 0), "Could not connect input port");
                wait = 8;
            }
            case 6 -> {
                var canvas = ClientQuestDefinitions.hotEventCanvas();
                require(canvas.connections().size() == 1
                                && canvas.connections().getFirst().from().equals("group:" + GROUP_ID)
                                && canvas.connections().getFirst().to().equals("quest:" + QUEST_ID),
                        "Visual connection was not persisted");
                capture(game, "hot-events-connected");
                verifyZoom();
                verifyScaledDragging();
                verifyLongCanvasZoom();
                require(clickCanvas(262, 67, 1), "Could not open a connection context menu");
                chooseContext(0);
                wait = 8;
            }
            case 7 -> {
                require(ClientQuestDefinitions.hotEventCanvas().connections().isEmpty(),
                        "Right-click connection removal was not synchronized");
                require(clickCanvas(320, 35, 1), "Could not open task properties menu");
                chooseContext(0);
                require(game.screen instanceof QuestScreen, "Task hot-event properties did not open");
                StyledButton tab = find(game.screen, "authoring-page-hot_event");
                require(tab.getMessage().getString().equals("\u70ed\u70b9\u4e8b\u4ef6"),
                        "Hot-event tab did not use the Chinese translation");
                require(game.screen.children().stream().filter(StyledButton.class::isInstance)
                                .map(StyledButton.class::cast).anyMatch(button -> button.styleId().equals("recommended")),
                        "Task properties did not expose the recommended setting");
                tab.onPress();
                wait = 4;
            }
            case 8 -> {
                if (((QuestScreen) game.screen).inputBlocked()) return false;
                capture(game, "hot-events-properties-zh-cn");
                require(game.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Could not cancel task properties");
                wait = 4;
            }
            case 9 -> {
                if (game.screen instanceof AnimatedQuestScreen animated && animated.isClosing()) return false;
                require(game.screen == screen, "Canceling properties did not return to the same canvas");
                require(clickCanvas(70, 67, 1), "Could not open icon context menu");
                wait = 4;
            }
            case 10 -> {
                capture(game, "hot-events-context-menu");
                require(screen.mouseClicked(10, screen.height / 2, 1), "Could not open background menu");
                chooseContext(0);
                require(game.screen != screen, "Background properties did not open");
                wait = 20;
            }
            case 11 -> {
                capture(game, "hot-events-background-properties");
                game.screen.onClose();
                wait = 6;
            }
            case 12 -> {
                if (game.screen instanceof AnimatedQuestScreen animated && animated.isClosing()) return false;
                require(game.screen == screen, "Canceling background properties did not return");
                require(screen.mouseClicked(screen.width - 50, 10, 0), "Could not leave edit mode");
                game.getSingleplayerServer().execute(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    QuestRuntime.activate(player, QUEST_ID);
                });
                wait = 6;
            }
            case 13 -> {
                if (ClientQuestProgress.view(QUEST_ID) == null
                        || !ClientQuestProgress.view(QUEST_ID).completed()) return false;
                require(game.screen == screen, "Completion changed the hot-events screen");
                wait = 5;
            }
            case 14 -> {
                capture(game, "hot-events-player-view");
                settledX = position("scrollX");
                double x = screen.width / 2.0, y = screen.height - 60;
                require(screen.mouseClicked(x, y, 0), "Could not begin canvas drag");
                require(screen.mouseDragged(x + 200, y + 100, 0, 200, 100),
                        "Could not drag canvas past horizontal boundary");
                require(position("scrollX") < settledX - 1,
                        "Canvas did not provide resisted horizontal overscroll");
                wait = 4;
            }
            case 15 -> {
                capture(game, "hot-events-overscroll");
                double x = screen.width / 2.0, y = screen.height - 60;
                require(screen.mouseReleased(x + 200, y + 100, 0), "Could not release canvas");
                require(position("scrollX") < settledX - 1,
                        "Canvas snapped back instead of animating");
                wait = 36;
            }
            case 16 -> {
                require(game.screen == screen, "Screen changed during canvas rebound");
                require(Math.abs(position("scrollX") - settledX) < 0.5,
                        "Canvas did not settle back at its horizontal boundary: x=" + position("scrollX"));
                capture(game, "hot-events-rebounded");
                screen.onClose();
                wait = 20;
            }
            case 17 -> {
                require(game.screen == overview, "Hot-events canvas did not return to overview");
                require(overview.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).anyMatch(button ->
                                button.styleId().equals("overview-feature-empty")),
                        "Completed recommended task remained featured");
                require(Component.translatable("screen.xero_quest_journal.overview_feature_empty")
                                .getString().equals("当前无推荐任务"),
                        "Empty recommended card has the wrong message");
                capture(game, "hot-events-overview-empty");
                fixture = new CompletableFuture<>();
                game.getSingleplayerServer().execute(() -> {
                    try {
                        var group = QuestRepository.groups().stream().filter(value -> value.id().equals(GROUP_ID))
                                .findFirst().orElseThrow();
                        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP,
                                group.id(), group.name(), group.description(), "", "", group.type(),
                                "", 1, group.icon(), "", "", 1, "default", "",
                                group.leadingIcon(), group.trailingIcon(), List.of(), List.of(),
                                group.hotEvent(), null, group.defaultUnlocked(), true);
                        var author = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        var result = QuestAuthoring.edit(author, request,
                                QuestRepository.fingerprint(Files.readString(QuestRepository.fileOf(GROUP_ID))),
                                false, false);
                        require(result.successful(), "Could not save recommended group: " + result.error());
                        fixture.complete(null);
                    } catch (Exception failure) {
                        fixture.completeExceptionally(failure);
                    }
                });
                stage = 20;
                wait = 12;
            }
            case 21 -> {
                if (!fixture.isDone()) return false;
                fixture.join();
                require(QuestRepository.groups().stream().filter(group -> group.id().equals(GROUP_ID))
                                .findFirst().orElseThrow().recommended(),
                        "Recommended group flag did not persist");
                require(field(overview, "featuredGroup") instanceof
                                xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group group
                                && group.id().equals(GROUP_ID)
                                && field(overview, "selectedId").equals(CHILD_ID),
                        "Recommended group did not replace the completed task card");
                capture(game, "recommended-group-overview");
                find(overview, "overview-feature").onPress();
                require(game.screen instanceof JourneyScreen
                                && field(game.screen, "selectedQuest").equals(CHILD_ID),
                        "Recommended group did not locate its available task in the journey");
                game.setScreen(null);
                return true;
            }
            case 18 -> {
                var motion = (xtdpotato.xero_quest_journal.client.UiMotion) field(overview, "overviewFeatureHover");
                require(motion.eased() > .99, "Recommended card hover did not reach its zoom target");
                capture(game, "overview-feature-hover");
                cursor(game, 0, 0);
                wait = 12;
            }
            case 19 -> {
                var motion = (xtdpotato.xero_quest_journal.client.UiMotion) field(overview, "overviewFeatureHover");
                require(motion.eased() < .01, "Recommended card did not restore its original scale");
                capture(game, "overview-feature-restored");
                verifyOverviewNavigation(game);
                // Check compact windows still use equal-height, parallel columns.
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 480);
                game.resizeDisplay();
                var feature = find(overview, "overview-feature");
                var hot = find(overview, "overview-hot_events");
                var last = find(overview, "overview-world");
                for (var child : overview.children()) if (child instanceof StyledButton row
                        && isOverviewEntry(row))
                    require(row.getHeight() == hot.getHeight(), "Small overview row-height mismatch");
                require(feature.getBottom() == last.getBottom() && hot.getRight() < overview.width,
                        "Small overview columns are not aligned");
                wait = 12;
            }
            case 20 -> {
                capture(game, "overview-feature-small");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1920, 1080);
                game.resizeDisplay();
                QuestConfig.defaults().uiAnimationMillis = 0;
                find(overview, "overview-hot_events").onPress();
                require(game.screen instanceof HotEventsScreen, "Overview did not open hot events");
                screen = (HotEventsScreen) game.screen;
                stage = 2;
                wait = 20;
            }
            default -> throw new AssertionError("Unknown hot-events stage " + stage);
        }
        stage++;
        return false;
    }

    private static void verifyOverviewNavigation(Minecraft game) throws Exception {
        var button = find(overview, "overview-world");
        String category = button.styleId().substring("overview-".length());
        button.onPress();
        require(game.screen instanceof QuestScreen && !(boolean) field(game.screen, "overviewMode")
                        && field(game.screen, "parent") == null && field(game.screen, "category").equals(category),
                "Category navigation kept the overview in its parent chain");
        QuestScreen journal = (QuestScreen) game.screen;
        for (int i = 0; i < 4; i++) {
            var hub = QuestScreen.overview(journal);
            game.setScreen(hub);
            find(hub, "overview-" + category).onPress();
            require(game.screen == journal && field(journal, "parent") == null,
                    "Repeated overview/category navigation stacked journal screens");
        }
        game.setScreen(overview);
        find(overview, "overview-feature").onPress();
        require(game.screen instanceof JourneyScreen
                        && field(game.screen, "parent") instanceof QuestScreen journalParent
                        && field(journalParent, "parent") == null
                        && field(game.screen, "selectedQuest").equals(QUEST_ID)
                        && ((JourneyScreen) game.screen).visibleQuestCount() > 0,
                "Recommended task did not locate its journey task or retained overview");
        var external = new Screen(Component.literal("External parent")) {};
        var hub = QuestScreen.overview(external);
        game.setScreen(hub);
        find(hub, "overview-" + category).onPress();
        require(field(game.screen, "parent") == external, "Replacement navigation lost the external parent");
        game.setScreen(overview);
    }

    private static void verifyZoom() throws Exception {
        double centre = invokeNumber("canvasX", screen.width / 2.0);
        for (double value : new double[]{.45, 1.6, .75, 1}) {
            var setter = HotEventsScreen.class.getDeclaredMethod("setZoom", double.class);
            setter.setAccessible(true);
            setter.invoke(screen, value);
            require(Math.abs(sx(centre) - screen.width / 2.0) < .001, "Zoom moved the screen-centre canvas point");
            require(Math.abs(sy(67) - screen.height / 2.0) < .001, "Zoom moved task line away from vertical centre");
            for (double coordinate : new double[]{40, 224, 300, 484})
                require(Math.abs(invokeNumber("canvasX", sx(coordinate)) - coordinate) < .001,
                        "Zoom broke inverse pointer coordinates");
            require(Math.abs(invokeNumber("canvasY", sy(35)) - 35) < .001,
                    "Zoom broke inverse vertical coordinates");
            if (!ClientQuestDefinitions.hotEventCanvas().connections().isEmpty()) {
                var hit = HotEventsScreen.class.getDeclaredMethod("connectionAt", double.class, double.class);
                hit.setAccessible(true);
                require(hit.invoke(screen, sx(262), sy(67)) != null, "Scaled connection hit region is misaligned");
            }
        }
        int trackLeft = screen.width / 2 - Math.min(90, Math.max(12, (screen.width - 80) / 2));
        int trackRight = screen.width - trackLeft;
        require(screen.mouseClicked(trackLeft, screen.height - 20, 0), "Zoom slider did not accept click");
        require(Math.abs(sy(67) - screen.height / 2.0) < .001, "Slider zoom shifted line");
        screen.mouseDragged(trackRight, screen.height - 20, 0, trackRight - trackLeft, 0);
        require(Math.abs(sx(centre) - screen.width / 2.0) < .001, "Slider zoom lost screen-centre anchor");
        screen.mouseReleased(trackRight, screen.height - 20, 0);
        var setter = HotEventsScreen.class.getDeclaredMethod("setZoom", double.class);
        setter.setAccessible(true);
        setter.invoke(screen, 1.0);
    }

    private static void verifyScaledDragging() throws Exception {
        var setter = HotEventsScreen.class.getDeclaredMethod("setZoom", double.class);
        setter.setAccessible(true);
        setter.invoke(screen, 1.25);
        double x = sx(330), y = sy(35);
        require(screen.mouseClicked(x, y, 0), "Could not grab scaled task card");
        require(screen.mouseDragged(x + 25, y, 0, 25, 0), "Could not move scaled task card");
        @SuppressWarnings("unchecked")
        var positions = (java.util.Map<String, xtdpotato.xero_quest_journal.core.model.HotEventCanvas.Position>)
                field(screen, "positions");
        var moved = positions.get("quest:" + QUEST_ID);
        require(moved.x() == 320 && moved.y() == 12, "Scaled task drag did not convert to canvas coordinates");
        screen.mouseDragged(x, y, 0, -25, 0);
        require(positions.get("quest:" + QUEST_ID).x() == 300, "Scaled task did not drag back to its source position");
        screen.mouseReleased(x, y, 0);
        setter.invoke(screen, 1.0);
    }

    private static void verifyLongCanvasZoom() throws Exception {
        @SuppressWarnings("unchecked")
        var positions = (java.util.Map<String, xtdpotato.xero_quest_journal.core.model.HotEventCanvas.Position>)
                field(screen, "positions");
        var original = new java.util.LinkedHashMap<>(positions);
        double scroll = position("scrollX"), target = position("targetScrollX");
        var reload = HotEventsScreen.class.getDeclaredMethod("reloadCards");
        reload.setAccessible(true);
        var setter = HotEventsScreen.class.getDeclaredMethod("setZoom", double.class);
        setter.setAccessible(true);
        try {
            positions.put("quest:" + QUEST_ID,
                    new xtdpotato.xero_quest_journal.core.model.HotEventCanvas.Position(1400, 12));
            reload.invoke(screen);
            setField(screen, "scrollX", 300.0);
            setField(screen, "targetScrollX", 300.0);
            double centre = invokeNumber("canvasX", screen.width / 2.0);
            for (double zoom : new double[]{1.6, 1.2, 1}) {
                setter.invoke(screen, zoom);
                require(Math.abs(sx(centre) - screen.width / 2.0) < .001,
                        "Zoom after panning a long canvas drifted from screen centre");
                require(Math.abs(sy(67) - screen.height / 2.0) < .001,
                        "Long task line moved vertically during zoom");
            }
        } finally {
            positions.clear();
            positions.putAll(original);
            setField(screen, "scrollX", scroll);
            setField(screen, "targetScrollX", target);
            reload.invoke(screen);
            setter.invoke(screen, 1.0);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var member = target.getClass().getDeclaredField(name);
        member.setAccessible(true);
        member.set(target, value);
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static double invokeNumber(String method, double value) throws Exception {
        var member = HotEventsScreen.class.getDeclaredMethod(method, double.class);
        member.setAccessible(true);
        return (double) member.invoke(screen, value);
    }
    private static double sx(double x) throws Exception { return invokeNumber("screenX", x); }
    private static double sy(double y) throws Exception { return invokeNumber("screenY", y); }
    private static boolean clickCanvas(double x, double y, int button) throws Exception {
        return screen.mouseClicked(sx(x), sy(y), button);
    }
    private static void chooseContext(int option) throws Exception {
        var method = HotEventsScreen.class.getDeclaredMethod("contextOptions");
        method.setAccessible(true);
        int count = ((List<?>) method.invoke(screen)).size();
        int x = Math.clamp((int) field(screen, "contextX"), 4, Math.max(4, screen.width - 136));
        int y = Math.clamp((int) field(screen, "contextY"), 4, Math.max(4, screen.height - (count * 19 + 12)));
        require(screen.mouseClicked(x + 12, y + 10 + option * 19, 0), "Context option not handled");
    }
    private static void cursor(Minecraft game, double x, double y) throws Exception {
        double windowX = x * game.getWindow().getScreenWidth() / game.screen.width;
        double windowY = y * game.getWindow().getScreenHeight() / game.screen.height;
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), windowX, windowY);
        // Unfocused test windows do not reliably receive GLFW cursor callbacks.
        for (var coordinate : java.util.Map.of("xpos", windowX, "ypos", windowY).entrySet()) {
            var field = game.mouseHandler.getClass().getDeclaredField(coordinate.getKey());
            field.setAccessible(true);
            field.setDouble(game.mouseHandler, coordinate.getValue());
        }
    }

    private static void verifyTranslations(Minecraft game) throws Exception {
        List<String> suffixes = List.of("list_icon.page.hot_event", "hot_event.enabled",
                "hot_event.image", "hot_event.show_image", "hot_event.title", "hot_event.image_hint",
                "hot_event.invalid_image",
                "hot_event.positioned", "hot_event.x", "hot_event.y", "hot_event.invalid_position",
                "create.recommended", "overview_feature_empty");
        for (String locale : List.of("en_us", "zh_cn")) {
            var resource = ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "lang/" + locale + ".json");
            try (var reader = game.getResourceManager().openAsReader(resource)) {
                var translations = JsonParser.parseReader(reader).getAsJsonObject();
                for (String suffix : suffixes) {
                    String key = "screen.xero_quest_journal." + suffix;
                    require(translations.has(key), locale + " translation missing: " + key);
                    String value = translations.get(key).getAsString();
                    require(!value.isBlank() && !value.equals(key), locale + " translation invalid: " + key);
                    require(!Component.translatable(key).getString().equals(key),
                            "Unresolved in-game translation: " + key);
                }
            }
        }
    }

    private static StyledButton find(Screen screen, String style) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(style)).findFirst().orElseThrow();
    }

    private static boolean isOverviewEntry(StyledButton button) {
        return List.of("overview-main", "overview-legendary", "overview-hot_events", "overview-world")
                .contains(button.styleId());
    }

    private static void assertOverviewEntries(Screen screen) {
        List<String> expected = List.of("overview-main", "overview-legendary",
                "overview-hot_events", "overview-world");
        for (String id : expected) {
            StyledButton button = find(screen, id);
            require(button instanceof xtdpotato.xero_quest_journal.client.JournalNavButton,
                    "Overview entry is not a journal navigation button");
        }
        require(screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(HotEventsRegression::isOverviewEntry).count()
                        == expected.size(),
                "Overview does not expose exactly four fixed entries");
        require(screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast)
                        .noneMatch(button -> button.styleId().startsWith("overview-type-")),
                "Overview still exposes dynamic task-type entries");
    }

    private static double position(String fieldName) throws Exception {
        var field = HotEventsScreen.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(screen);
    }

    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (hot-events stage " + stage + ")");
    }
}
