package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class NavigationRegression {
    private static int phase;
    private static int ticks;
    private static int permission;
    private static QuestScreen journal;

    public static boolean tick(Minecraft game, String guide) throws Exception {
        if (phase > 0 && game.screen instanceof AnimatedQuestScreen screen && screen.inputBlocked()) {
            ticks = 0;
            return false;
        }
        if (++ticks < 8) return false;
        ticks = 0;
        switch (phase) {
            case 0 -> {
                permission = game.player.getPermissionLevel();
                game.player.setPermissionLevel(0);
                journal = new QuestScreen();
                game.setScreen(journal);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                GLFW.glfwSetCursorPos(game.getWindow().getWindow(), 1100, 700);
                game.resizeDisplay();
                require(!has(game, "groups") && !has(game, "chapters"), "Legacy selectors on player journal");
                assertPlayerControls(game);
                require(find(game, "overview").getX() < 30, "Overview is not bottom-left");
                require(find(game, "track").getRight() == game.screen.width - 16, "Track is not bottom-right");
            }
            case 1 -> {
                assertPreviewBounds(game);
                assertCompactLayout(game);
                capture(game, "29-player-navigation");
                assertDisjointNavigation(game);
                click(game, "category-main");
                assertCategory(game, "main");
                game.screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                assertCategory(game, "legendary");
                game.screen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
                assertCategory(game, "main");
                click(game, "category-active");
                game.screen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
                assertCategory(game, "completed");
                game.screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                assertCategory(game, "active");
                click(game, "overview");
                require((has(game, "overview-feature") || has(game, "overview-feature-empty"))
                                && !has(game, "hot-events") && !has(game, "open-task"),
                        "Overview presentation did not open");
                assertOverviewEntries(game);
                require(!has(game, "edit") && !has(game, "add-group"), "Player can author in overview");
            }
            case 2 -> {
                assertPreviewBounds(game);
                capture(game, "30-player-overview");
                String featured = (String) field(game.screen, "selectedId");
                if (featured.isEmpty()) {
                    require(has(game, "overview-feature-empty"), "Empty recommendations did not show the empty state");
                    game.setScreen(journal);
                } else {
                    click(game, "overview-feature");
                    require(game.screen == journal, "Overview task did not return to original journal");
                    require(field(game.screen, "selectedId").equals(featured), "Overview did not select featured task");
                }
                require(ClientQuestProgress.view(guide).status().equals("active"), "Navigation completed guide");
                assertPlayerControls(game);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 480);
                game.resizeDisplay();
            }
            case 3 -> {
                assertDisjointNavigation(game);
                assertPreviewBounds(game);
                assertCompactLayout(game);
                capture(game, "31-player-navigation-small");
                game.player.setPermissionLevel(permission);
            }
            case 4 -> {
                require(has(game, "edit") && has(game, "settings"), "Admin buttons not refreshed");
                require(!has(game, "category-add"), "Preview shows the task-type add button");
                StyledButton row = rows(game).stream().filter(b -> b.styleId().startsWith("quest-"))
                        .findFirst().orElseThrow();
                double rowX = row.getX() + row.getWidth() / 2.0;
                double rowY = row.getY() + row.getHeight() / 2.0;
                require(game.screen.mouseClicked(rowX, rowY, 1), "Preview right click was not consumed");
                game.screen.mouseReleased(rowX, rowY, 1);
                require(((List<?>) field(game.screen, "menu")).isEmpty(), "Preview opened a right-click menu");
                assertDisjointNavigation(game);
                assertCompactLayout(game);
                click(game, "edit");
                require(!has(game, "groups") && has(game, "category-add") && has(game, "source"),
                        "Admin editor unavailable");
                game.player.setPermissionLevel(0);
            }
            case 5 -> {
                assertPlayerControls(game);
                require(!has(game, "groups") && !has(game, "source"), "Revoked permissions left authoring UI");
                game.player.setPermissionLevel(permission);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
            }
            case 6 -> {
                require(has(game, "edit"), "Admin controls did not return");
                require(!has(game, "category-add"), "Preview retained the task-type add button");
                assertDisjointNavigation(game);
                assertCompactLayout(game);
                assertPreviewBounds(game);
                capture(game, "32-admin-navigation");
                return true;
            }
            default -> throw new IllegalStateException("Navigation phase " + phase);
        }
        phase++;
        return false;
    }

    private static void assertPlayerControls(Minecraft game) {
        require(rows(game).stream().noneMatch(b -> Set.of("edit", "settings", "add-group", "add-task",
                "add-chapter", "category-add", "source", "undo", "redo", "hierarchy").contains(b.styleId())),
                "Admin controls leaked");
        require(rows(game).stream().filter(b -> b.styleId().startsWith("category-")).count()
                        == ClientQuestDefinitions.journalOrder().tabOrder(ClientQuestDefinitions.types()).size() + 2L,
                "Missing category tabs");
        require(has(game, "overview") && has(game, "close"), "Missing player navigation");
        if (has(game, "track")) {
            StyledButton track = find(game, "track");
            require(track.getRight() == game.screen.width - 16
                            && track.getBottom() == game.screen.height - 10,
                    "Track is not fixed to the bottom-right");
        } else {
            require(has(game, "complete") || has(game, "claim"),
                    "Task details are missing their completion/reward action");
        }
    }

    private static void assertOverviewEntries(Minecraft game) {
        Set<String> expected = Set.of("overview-main", "overview-legendary",
                "overview-hot_events", "overview-world");
        var entries = rows(game).stream()
                .filter(button -> expected.contains(button.styleId()))
                .toList();
        require(entries.size() == expected.size(), "Overview does not expose exactly four fixed entries");
        require(entries.stream().allMatch(button -> button instanceof JournalNavButton),
                "Overview entry is not a journal navigation button");
        require(rows(game).stream().noneMatch(button -> button.styleId().startsWith("overview-type-")),
                "Overview still exposes dynamic task-type entries");
        for (String id : expected) require(has(game, id), "Missing overview entry: " + id);
    }

    @SuppressWarnings("unchecked")
    private static void assertCategory(Minecraft game, String category) throws Exception {
        require(find(game, "category-" + category).selectionHighlighted(), "Category tab not selected");
        var tasks = (List<QuestDefinition>) field(game.screen, "quests");
        require(tasks.stream().allMatch(q -> QuestScreen.matchesCategory(q, category)), "Category filter ignored");
        long expected = QuestRepository.all().stream().filter(q -> QuestScreen.matchesCategory(q, category)).count();
        require(tasks.size() == expected, "Category omitted tasks");
    }

    private static void assertDisjointNavigation(Minecraft game) throws Exception {
        var nav = rows(game).stream().filter(b -> b instanceof JournalNavButton && b.visible).toList();
        for (int i = 0; i < nav.size(); i++) {
            var a = nav.get(i);
            require(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= game.screen.width
                    && a.getBottom() <= game.screen.height, "Navigation outside screen");
            for (int j = i + 1; j < nav.size(); j++) {
                var b = nav.get(j);
                require(visibleRight(game, a) <= visibleLeft(game, b)
                        || visibleRight(game, b) <= visibleLeft(game, a)
                        || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(), "Overlapping navigation");
            }
        }
    }

    private static boolean clippedCategory(StyledButton button) {
        return button.styleId().startsWith("category-")
                && !Set.of("category-previous", "category-next").contains(button.styleId());
    }

    private static int visibleLeft(Minecraft game, StyledButton button) throws Exception {
        return clippedCategory(button) ? Math.max(button.getX(), metric(game, "categoryViewportLeft")) : button.getX();
    }

    private static int visibleRight(Minecraft game, StyledButton button) throws Exception {
        return clippedCategory(button) ? Math.min(button.getRight(), metric(game, "categoryViewportRight")) : button.getRight();
    }

    private static void assertCompactLayout(Minecraft game) throws Exception {
        int width = game.screen.width;
        int headerHeight = metric(game, "headerHeight");
        require(headerHeight == (width < 400 ? 51 : 32), "Journal header was not reduced by one third");
        int listWidth = (int) field(game.screen, "listWidth");
        int detailWidth = width - 16 - (int) field(game.screen, "detailsLeft");
        require(detailWidth > listWidth, "Task details must be wider than the task list");
        require(listWidth == Math.max(100, Math.min(420, (width - 46) * 36 / 100)),
                "Task list did not use the responsive width reserved for longer quest titles");
        require(metric(game, "detailsTop") == headerHeight + 6
                        && metric(game, "listTop") == metric(game, "searchY") + 28,
                "Content did not reclaim the reduced header space");
        for (var button : rows(game)) {
            if (button.styleId().startsWith("category-")
                    || Set.of("edit", "settings", "close").contains(button.styleId())) {
                require(button.getY() >= 0 && button.getBottom() <= headerHeight,
                        "Header control extends into task content");
            }
        }
    }

    private static int metric(Minecraft game, String name) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (int) method.invoke(game.screen);
    }

    private static void assertPreviewBounds(Minecraft game) throws Exception {
        var screen = (QuestScreen) game.screen;
        var layout = (LayoutEditorState) field(screen, "layout");
        String selected = (String) field(screen, "selectedId");
        int left = metric(game, "canvasLeft");
        int top = metric(game, "canvasTop");
        double scaleX = decimalMetric(screen, "canvasScaleX");
        double scaleY = decimalMetric(screen, "canvasScaleY");
        var display = QuestScreen.class.getDeclaredMethod("elementDisplayPosition", LayoutEditorState.Element.class);
        display.setAccessible(true);
        for (var element : layout.elements().values()) {
            if (!element.quest().equals(selected)) continue;
            var saved = layout.position(element.id(), 0, 0);
            int x = saved.x(), y = saved.y();
            int[] visible = (int[]) display.invoke(screen, element);
            require(saved.x() == x && saved.y() == y, "Preview changed saved layout coordinates");
            String parent = layout.parentOf(element.id());
            if (parent == null) {
                require(visible[0] >= left && visible[1] >= top, "Task content overlaps list/header");
            } else if (layout.elements().containsKey(parent)) {
                var origin = layout.position(parent, 0, 0);
                int[] parentDisplay = (int[]) display.invoke(screen, layout.elements().get(parent));
                require(Math.abs(visible[0] - parentDisplay[0] - Math.round((x - origin.x()) * scaleX)) <= 1
                        && Math.abs(visible[1] - parentDisplay[1] - Math.round((y - origin.y()) * scaleY)) <= 1,
                        "Preview broke responsive parent-child offsets");
            }
        }
    }

    private static double decimalMetric(QuestScreen screen, String name) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (double) method.invoke(screen);
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static List<StyledButton> rows(Minecraft game) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast).toList();
    }
    private static boolean has(Minecraft game, String id) {
        return rows(game).stream().anyMatch(b -> b.styleId().equals(id));
    }
    private static StyledButton find(Minecraft game, String id) {
        return rows(game).stream().filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void click(Minecraft game, String id) {
        var button = find(game, id);
        var screen = game.screen;
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "Click not handled: " + id);
        screen.mouseReleased(x, y, 0);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
