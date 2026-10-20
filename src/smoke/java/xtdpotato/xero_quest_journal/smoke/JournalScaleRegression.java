package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Large real-client fixture: controls, hit testing and retained state scale with the viewport. */
@EventBusSubscriber(modid = "xero_quest_journal", value = Dist.CLIENT)
final class JournalScaleRegression {
    private static final int COUNT = 1200;
    private static final String GROUP = "scale_group";
    private static int stage, wait;
    private static QuestScreen screen;
    private static JourneyScreen journey;
    private static CompletableFuture<Void> work;
    private static long layoutMillis;
    private static int widgetCount, journeyNodes;
    private static long searchDeadline;
    private static long searchInputNanos;
    private static String pendingCapture = "";
    private static net.minecraft.client.gui.screens.Screen captureScreen;

    static void cleanup() throws Exception {
        Path group = QuestRepository.root().resolve(GROUP);
        for (int i = 0; i < COUNT; i++) Files.deleteIfExists(group.resolve(id(i) + ".json"));
        for (int c = 0; c < 2; c++) Files.deleteIfExists(group.resolve("scale_chapter_" + c + ".chapter.json"));
        Files.deleteIfExists(group.resolve("group_manifest.json"));
        if (Files.isDirectory(group)) {
            try (var entries = Files.list(group)) {
                if (entries.findAny().isEmpty()) Files.delete(group);
            }
        }
    }

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) throws Exception {
        if (pendingCapture.isBlank() || event.getScreen() != captureScreen) return;
        var game = Minecraft.getInstance();
        Path root = Path.of("..", "build", "verification");
        Files.createDirectories(root);
        event.getGuiGraphics().flush();
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(root.resolve(pendingCapture + ".png"));
        }
        pendingCapture = "";
        captureScreen = null;
    }

    static boolean tick(Minecraft game) throws Exception {
        if (!pendingCapture.isBlank()) return false;
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                QuestConfig.defaults().taskPagination = false;
                work = CompletableFuture.runAsync(() -> {
                    try {
                        Path group = QuestRepository.root().resolve(GROUP);
                        Files.createDirectories(group);
                        Files.writeString(group.resolve("group_manifest.json"),
                                "{\"id\":\"scale_group\",\"name\":\"Large scale tasks\",\"type\":\"main\"}");
                        for (int i = 0; i < COUNT; i++) {
                            Files.writeString(group.resolve(id(i) + ".json"), "{\"id\":\"" + id(i)
                                    + "\",\"title\":\"Scale task " + i + "\",\"type\":\"main\",\"icon\":\"minecraft:diamond\","
                                    + "\"rewardMode\":\"manual\",\"triggers\":[{\"type\":\"confirm\",\"count\":1}],"
                                    + "\"rewards\":[{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":1}]}");
                        }
                        for (int c = 0; c < 2; c++) {
                            var ids = new ArrayList<String>();
                            for (int i = c * COUNT / 2; i < (c + 1) * COUNT / 2; i++) ids.add(id(i));
                            Files.writeString(group.resolve("scale_chapter_" + c + ".chapter.json"),
                                    "{\"id\":\"scale_chapter_" + c + "\",\"name\":\"Scale " + c
                                            + "\",\"group\":\"scale_group\",\"quests\":"
                                            + new com.google.gson.Gson().toJson(ids) + "}");
                        }
                        QuestRepository.reload();
                        require(QuestRepository.errors().isEmpty(), "Scale fixtures invalid");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                long start = System.nanoTime();
                open(game, true);
                layoutMillis = (System.nanoTime() - start) / 1_000_000;
                int listWidth = (int) field(screen, "listWidth");
                int detailsWidth = screen.width - 16 - (int) field(screen, "detailsLeft");
                require(listWidth == Math.max(100, Math.min(420, (screen.width - 46) * 36 / 100)),
                        "Journal list width did not follow the responsive title-width rule");
                require(detailsWidth > listWidth, "Expanded task list crowded out task details");
                if (screen.width >= 900)
                    require(listWidth >= game.font.width("测".repeat(25)) + 56,
                            "Task list cannot fit a 25-character title beside its status icon");
                assertVirtualized();
                set(screen, "searchQuery", "Scale task");
                screen.init(game, screen.width, screen.height);
                var group = button("group-" + GROUP);
                screen.mouseClicked(group.getX() + 40, group.getY() + 10, 1);
                buttonInMenu("context-select_group_tasks").onPress();
                require(selection().size() == COUNT, "Group select omitted filtered/off-screen tasks");
                screen.mouseClicked(group.getX() + 40, group.getY() + 10, 1);
                buttonInMenu("context-invert_group_tasks").onPress();
                require(selection().isEmpty(), "Group invert did not invert every task");
                set(screen, "searchQuery", "Scale task");
                screen.init(game, screen.width, screen.height);
                capture(game, "journal-large-top");
            }
            case 2 -> {
                selection().clear();
                controlClick(id(0));
                controlClick(id(1));
                require(selection().equals(Set.of(id(0), id(1))), "Ctrl multi-selection failed");
                var source = button("quest-" + id(0));
                screen.mouseClicked(source.getX() + 42, source.getY() + 10, 0);
                set(screen, "taskReorderPressedAt", System.nanoTime() - 450_000_000L);
                invoke(screen, "advanceTaskReorder", new Class<?>[]{long.class, double.class}, System.nanoTime(), 0.);
                require((boolean) field(screen, "taskReorderDragging"), "Long press did not lift task block");
                var target = button("quest-" + id(3));
                double x = target.getX() + 42, y = target.getBottom() - 3;
                screen.mouseDragged(x, y, 0, 0, y - source.getY() - 10);
                require(field(screen, "taskDropTarget").equals(id(3)), "Drop target did not resolve");
                capture(game, "journal-large-multi-drag");
                wait = 3;
            }
            case 3 -> {
                var target = button("quest-" + id(3));
                screen.mouseReleased(target.getX() + 42, target.getBottom() - 3, 0);
                require(field(screen, "orderPending") != null, "Multi-drag did not send atomic request");
            }
            case 4 -> {
                if (field(screen, "orderPending") != null) return false;
                var order = ClientQuestDefinitions.all().stream()
                        .filter(q -> ClientQuestDefinitions.groupOf(q.id()).equals(GROUP))
                        .map(q -> q.id()).limit(4).toList();
                require(order.equals(List.of(id(2), id(3), id(0), id(1))), "Block drag changed internal order");
                screen.init(game, screen.width, screen.height);
                double maximum = (double) invoke(screen, "maxListScroll", new Class<?>[]{});
                set(screen, "listScroll", maximum);
                set(screen, "listTargetScroll", maximum);
                invoke(screen, "updateListButtons", new Class<?>[]{});
                assertVirtualized();
                require(selection().equals(Set.of(id(0), id(1))), "Virtualization discarded selection");
                require(button("quest-" + id(COUNT - 1)).visible, "Last task did not materialize");
                require(screen.children().stream().filter(JournalListButton.class::isInstance)
                        .noneMatch(b -> ((StyledButton) b).styleId().equals("quest-" + id(2))),
                        "Top task widget survived off-screen");
                set(screen, "listScroll", 0.);
                set(screen, "listTargetScroll", 0.);
                invoke(screen, "updateListButtons", new Class<?>[]{});
                button("group-" + GROUP).onPress();
                require(ClientPreferences.get().collapsedJournalGroups.contains(GROUP), "Collapse not saved");
                var current = ClientPreferences.class.getDeclaredField("current");
                current.setAccessible(true); current.set(null, null);
                require(ClientPreferences.get().collapsedJournalGroups.contains(GROUP), "Fold lost on disk reload");
                open(game, true);
                require(screen.children().stream().filter(JournalListButton.class::isInstance)
                        .noneMatch(b -> ((StyledButton) b).styleId().startsWith("quest-scale_task")),
                        "Re-entry automatically expanded a collapsed group");
                button("group-" + GROUP).onPress();
                capture(game, "journal-large-reopened");
            }
            case 5 -> {
                double maximum = (double) invoke(screen, "maxListScroll", new Class<?>[]{});
                set(screen, "listScroll", maximum);
                set(screen, "listTargetScroll", maximum);
                invoke(screen, "updateListButtons", new Class<?>[]{});
                capture(game, "journal-large-bottom");
            }
            case 6 -> {
                long start = System.nanoTime();
                journey = new JourneyScreen(screen, id(100));
                game.setScreen(journey);
                require((System.nanoTime() - start) / 1_000_000 < 15_000, "Journey opening took over 15 seconds");
            }
            case 7 -> {
                assertJourneyVisibility();
                capture(game, "journey-large");
                var saved = field(journey, "slotIndex");
                invoke(journey, "advanceMotion", new Class<?>[]{});
                require(field(journey, "slotIndex") == saved, "Journey rebuilt index while panning");
            }
            case 8 -> {
                var slots = (List<?>) field(journey, "slots");
                var far = slots.stream().filter(slot -> {
                    try { return ((xtdpotato.xero_quest_journal.core.model.QuestDefinition)
                            invoke(slot, "quest", new Class<?>[]{})).id().equals(id(1199)); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }).findFirst().orElseThrow();
                int x = (int) invoke(journey, "slotX", new Class<?>[]{far.getClass()}, far);
                invoke(field(journey, "timelineX"), "jump", new Class<?>[]{double.class}, x - 100.);
                invoke(journey, "advanceMotion", new Class<?>[]{});
                assertJourneyVisibility();
                capture(game, "journey-large-end");
                work = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    player.getInventory().clearContent();
                    require(QuestRuntime.activate(player, id(0)).equals("completed"), "Fixture completion failed");
                    require(QuestRuntime.activate(player, id(1)).equals("completed"), "Fixture completion failed");
                    player.containerMenu.broadcastChanges();
                }, game.getSingleplayerServer());
            }
            case 9 -> {
                if (!work.isDone() || ClientQuestProgress.view(id(0)) == null
                        || !ClientQuestProgress.view(id(0)).completed()) return false;
                work.join();
                open(game, false);
                var claim = button("claim_all");
                require(claim.active, "Claim-all header icon absent for completed tasks");
                capture(game, "journal-large-claim-ready");
            }
            case 10 -> {
                button("claim_all").onPress();
            }
            case 11 -> {
                if (!(game.screen instanceof QuestRewardPopupScreen)) return false;
                require(ClientQuestProgress.view(id(0)).status().equals("claimed")
                        && ClientQuestProgress.view(id(1)).status().equals("claimed"), "Bulk claim skipped completion");
                require(game.player.getInventory().countItem(Items.DIAMOND) == 2, "Bulk claim delivery count");
                capture(game, "journal-large-claim-all");
            }
            case 12 -> {
                game.screen.onClose();
            }
            case 13 -> {
                require(screen.children().stream().filter(StyledButton.class::isInstance)
                        .noneMatch(b -> ((StyledButton) b).styleId().equals("claim_all")),
                        "Claim button remained after receipt completed");
                Files.writeString(Path.of("..", "build", "verification", "large-quest-viewports-result.txt"),
                        "PASS: 1200 tasks; journal visible widget maximum=" + widgetCount
                                + "; indexed journey visible nodes=" + journeyNodes
                                + "; journal opening=" + layoutMillis + " ms.\n"
                                + "Scroll, disk folds, group selection/inversion under filters, multi-task drag,\n"
                                + "stable ordering, journey visibility/hit testing and claim-all header verified.\n");
                open(game, true);
                var search = (net.minecraft.client.gui.components.EditBox) field(screen, "taskSearch");
                long started = System.nanoTime();
                search.setValue("Scale task 1199");
                searchInputNanos = System.nanoTime() - started;
                require(searchInputNanos < 100_000_000L, "Search input blocked the client for over 100 ms");
                searchDeadline = System.nanoTime() + 15_000_000_000L;
            }
            case 14 -> {
                require(System.nanoTime() < searchDeadline, "Asynchronous journal search timed out");
                @SuppressWarnings("unchecked")
                var result = (List<xtdpotato.xero_quest_journal.core.model.QuestDefinition>) field(screen, "quests");
                if (result.size() != 1 || !result.getFirst().id().equals(id(1199))) return false;
                assertVirtualizedSearch();
                var search = (net.minecraft.client.gui.components.EditBox) field(screen, "taskSearch");
                search.setValue("Scale task 1");
                search.setValue("no_matches_expected");
                search.setValue("Scale task 1199");
                searchDeadline = System.nanoTime() + 15_000_000_000L;
            }
            case 15 -> {
                Object searchState = field(screen, "journalSearch");
                require(System.nanoTime() < searchDeadline, "Latest-query journal search timed out: query="
                        + field(screen, "searchQuery") + ", pending=" + invoke(searchState, "pending", new Class<?>[]{})
                        + ", cursor=" + field(searchState, "cursor") + "/"
                        + ((List<?>) field(searchState, "source")).size()
                        + ", submitted=" + field(searchState, "submittedQuery")
                        + ", applied=" + field(searchState, "applied"));
                @SuppressWarnings("unchecked")
                var result = (List<xtdpotato.xero_quest_journal.core.model.QuestDefinition>) field(screen, "quests");
                if (result.size() != 1 || !result.getFirst().id().equals(id(1199))) return false;
                capture(game, "journal-async-search");
            }
            case 16 -> {
                ((net.minecraft.client.gui.components.EditBox) field(screen, "taskSearch")).setValue("");
            }
            case 17 -> {
                require(((List<?>) field(screen, "quests")).size() >= COUNT, "Clearing search did not restore tasks");
                Files.writeString(Path.of("..", "build", "verification", "journal-search-result.txt"),
                        "PASS: 1200 real-client tasks, nonblocking search input ("
                                + searchInputNanos / 1_000_000.0 + " ms), latest-query wins and empty-query restore;\n"
                                + "viewport-only row materialization retained during search.\n");
                work = CompletableFuture.runAsync(() -> {
                    try { cleanup(); QuestRepository.reload(); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 18 -> {
                if (!work.isDone()) return false;
                work.join();
                return true;
            }
            default -> throw new AssertionError("Scale stage " + stage);
        }
        stage++;
        if (wait <= 0) wait = 5;
        return false;
    }

    private static String id(int i) { return String.format(Locale.ROOT, "scale_task_%04d", i); }
    private static void open(Minecraft game, boolean edit) throws Exception {
        screen = new QuestScreen(null);
        game.setScreen(screen);
        set(screen, "editMode", edit);
        set(screen, "searchQuery", "Scale task");
        screen.init(game, screen.width, screen.height);
    }
    private static void assertVirtualized() throws Exception {
        int rows = ((JournalListModel) field(screen, "journalList")).rows().size();
        require(rows >= COUNT, "Large task model missing: rows=" + rows
                + ", quests=" + ((List<?>) field(screen, "quests")).size()
                + ", query=" + field(screen, "searchQuery")
                + ", category=" + field(screen, "category")
                + ", scroll=" + field(screen, "listScroll"));
        long count = screen.children().stream().filter(JournalListButton.class::isInstance).count();
        widgetCount = Math.max(widgetCount, (int) count);
        require(count > 0 && count < 80, "Off-screen journal controls materialized: " + count);
    }
    private static void assertVirtualizedSearch() {
        long count = screen.children().stream().filter(JournalListButton.class::isInstance).count();
        require(count > 0 && count < 10, "Filtered search materialized unrelated task rows");
    }
    private static void assertJourneyVisibility() throws Exception {
        double left = scroll("timelineX"), top = scroll("timelineY");
        double right = left + (double) invoke(journey, "viewWidth", new Class<?>[]{});
        double bottom = top + (double) invoke(journey, "viewHeight", new Class<?>[]{});
        var actual = (List<?>) invoke(journey, "visibleSlots",
                new Class<?>[]{double.class, double.class, double.class, double.class}, left, top, right, bottom);
        var expected = new ArrayList<Object>();
        for (var slot : (List<?>) field(journey, "slots")) {
            int x = (int) invoke(journey, "slotX", new Class<?>[]{slot.getClass()}, slot);
            int y = (int) invoke(journey, "slotY", new Class<?>[]{slot.getClass()}, slot);
            int h = (int) invoke(journey, "slotHeight", new Class<?>[]{slot.getClass()}, slot);
            if (x + 168 + 24 >= left && x - 24 <= right && y + h + 48 >= top && y - 16 <= bottom) expected.add(slot);
        }
        require(actual.equals(expected), "Journey index differs from full geometry scan");
        require(actual.size() > 0 && actual.size() < 50, "Journey visits off-screen nodes: " + actual.size());
        journeyNodes = Math.max(journeyNodes, actual.size());
        double scale = (double) invoke(journey, "scale", new Class<?>[]{});
        int header = (int) invoke(journey, "top", new Class<?>[]{});
        for (var slot : actual) {
            int x = (int) invoke(journey, "slotX", new Class<?>[]{slot.getClass()}, slot);
            int y = (int) invoke(journey, "slotY", new Class<?>[]{slot.getClass()}, slot);
            double sx = (x + 40 - left) * scale, sy = header + (y + 12 - top) * scale;
            if (sx < 0 || sx >= journey.width || sy < header || sy >= journey.height - 23) continue;
            var quest = (xtdpotato.xero_quest_journal.core.model.QuestDefinition)
                    invoke(slot, "quest", new Class<?>[]{});
            require(invoke(journey, "questAt", new Class<?>[]{double.class, double.class}, sx, sy).equals(quest.id()),
                    "Visible journey card cannot be hit");
        }
    }
    private static double scroll(String name) throws Exception {
        return (double) invoke(field(journey, name), "position", new Class<?>[]{});
    }
    @SuppressWarnings("unchecked") private static Set<String> selection() throws Exception {
        return (Set<String>) field(screen, "selectedTaskIds");
    }
    private static void controlClick(String id) throws Exception {
        set(screen, "selectionControlDown", true);
        var button = button("quest-" + id);
        screen.mouseClicked(button.getX() + 42, button.getY() + 10, 0);
        screen.mouseReleased(button.getX() + 42, button.getY() + 10, 0);
        set(screen, "selectionControlDown", false);
    }
    private static StyledButton button(String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing " + id));
    }
    @SuppressWarnings("unchecked") private static StyledButton buttonInMenu(String id) throws Exception {
        return ((List<StyledButton>) field(screen, "menu")).stream().filter(b -> b.styleId().equals(id))
                .findFirst().orElseThrow();
    }
    private static void set(Object value, String name, Object next) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); field.set(value, next);
    }
    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static Object invoke(Object value, String name, Class<?>[] types, Object... args) throws Exception {
        var method = value.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(value, args);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        pendingCapture = name;
        captureScreen = game.screen;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (scale stage " + stage + ")");
    }
}
