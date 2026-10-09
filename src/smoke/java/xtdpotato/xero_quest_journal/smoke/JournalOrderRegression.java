package xtdpotato.xero_quest_journal.smoke;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.QuestTypeStore;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class JournalOrderRegression {
    private static final String A = "order_a", B = "order_b", C = "order_c";
    private static final String D = "order_d", U = "order_u", V = "order_v";
    private static int stage, wait;
    private static CompletableFuture<Void> work;
    private static QuestScreen screen;
    private static List<String> runtimeOrder;
    private static String sourceA;
    private static int openHeight, followingY, firstChildY;
    private static boolean scaleChecks;
    private static long readyFrame;

    static boolean tick(Minecraft game) throws Exception {
        if (scaleChecks) return JournalScaleRegression.tick(game);
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                QuestConfig.defaults().taskPagination = false;
                resize(game, 1920, 1080);
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    try { fixtures(server.getPlayerList().getPlayer(game.player.getUUID())); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, server);
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                ClientEditMode.set(true);
                open(game);
                require(categories().getLast().equals("completed"), "Completed tab is not last by default");
                checkRows();
                checkCodec(game);
                var row = button("quest-" + A);
                require(screen.mouseClicked(row.getX() + 42, row.getY() + 10, 1), "Task menu did not open");
                @SuppressWarnings("unchecked")
                var menu = (List<StyledButton>) field(screen, "menu");
                menu.stream().filter(b -> b.styleId().equals("context-order.move_task"))
                        .findFirst().orElseThrow(() -> new AssertionError("Move task entry missing")).onPress();
                require(field(screen, "movingTask").equals(A), "Task menu did not start movement");
                require(!button("order-previous").active, "First group task could move out of its group");
                screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                require(field(screen, "orderPending") != null, "Down key did not send movement request");
            }
            case 2 -> {
                if (!settled()) return false;
                require(groupTasks("order_group_main").equals(List.of(B, A, C)), "Task did not move down");
                require(field(screen, "selectedId").equals(A), "Move lost task selection");
                require(field(screen, "movingTask").equals(A), "Task mode cleared after one move");
                require(button("order-previous").active && button("order-next").active, "Move controls not refreshed");
                checkRows();
                capture(game, "journal-order-task");
                clickButton("order-next");
            }
            case 3 -> {
                if (!settled()) return false;
                require(groupTasks("order_group_main").equals(List.of(B, C, A)), "Direction button did not move");
                require(!button("order-next").active, "Last group task could move into another group");
                screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                require(field(screen, "orderPending") == null, "Boundary key dispatched an invalid move");
                screen.mouseClicked(8, 8, 1);
                require(field(screen, "movingTask").equals(""), "Right click did not cancel task movement");
                open(game);
                require(groupTasks("order_group_main").equals(List.of(B, C, A)), "Reopen lost saved task order");
                invoke(screen, "startTaskMove", new Class<?>[]{String.class}, U);
                screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            }
            case 4 -> {
                if (!settled()) return false;
                require(standaloneTasks().equals(List.of(V, U)), "Standalone tasks did not reorder");
                require(!button("order-next").active, "Standalone task crossed its footer boundary");
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(game.screen == screen && field(screen, "movingTask").equals(""), "Escape closed journal");
                invoke(screen, "ensureCategoryVisible", new Class<?>[]{int.class, int.class},
                        categories().indexOf("main"), categories().size() + 1);
                invoke(screen, "animateCategories", new Class<?>[]{});
                rightTab("main");
                require(field(screen, "movingTab").equals("main"), "Tab right-click did not start movement");
                require(field(screen, "category").equals("active"), "Right click changed the active tab");
                screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            }
            case 5 -> {
                if (!settled()) return false;
                var tabs = categories();
                require(tabs.indexOf("main") > tabs.indexOf("legendary"), "Right key did not move tab");
                require(tabs.getLast().equals("completed"), "Moving another tab displaced completed");
                require(field(screen, "movingTab").equals("main"), "Tab mode did not persist");
                require(field(screen, "category").equals("active"), "Tab reorder changed the active filter");
                capture(game, "journal-order-tabs");
                rightTab("main");
                require(field(screen, "movingTab").equals(""), "Second right-click did not cancel tab mode");
                open(game);
                require(categories().equals(tabs), "Reopen lost tab order");
                invoke(screen, "startTabMove", new Class<?>[]{String.class}, "completed");
                require(!button("order-next").active, "Last tab could move beyond the add control");
                clickButton("order-previous");
            }
            case 6 -> {
                if (!settled()) return false;
                require(categories().get(categories().size() - 2).equals("completed"),
                        "Explicit completed-tab movement failed");
                clickButton("order-next");
            }
            case 7 -> {
                if (!settled()) return false;
                require(categories().getLast().equals("completed"), "Completed could not return to the last slot");
                clickButton("order-done");
                require(field(screen, "movingTab").equals(""), "Done button did not finish moving");
                invoke(screen, "startTaskMove", new Class<?>[]{String.class}, B);
                ClientEditMode.set(false);
                screen.tick();
                require(field(screen, "movingTask").equals("") && !(boolean) field(screen, "editMode"),
                        "Edit-mode revocation left movement enabled");
                invoke(screen, "startTaskMove", new Class<?>[]{String.class}, B);
                require(field(screen, "movingTask").equals(""), "Disabled editing accepted movement");
                ClientEditMode.set(true);
                open(game);
                resize(game, 640, 480);
                invoke(screen, "startTaskMove", new Class<?>[]{String.class}, B);
            }
            case 8 -> {
                for (String id : List.of("order-previous", "order-next", "order-done")) {
                    var control = button(id);
                    require(control.getX() >= 0 && control.getY() >= 0 && control.getRight() <= screen.width
                            && control.getBottom() <= screen.height, "Compact movement control outside viewport");
                }
                capture(game, "journal-order-compact");
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    try { serverChecks(server.getPlayerList().getPlayer(game.player.getUUID())); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, server);
            }
            case 9 -> {
                if (!work.isDone()) return false;
                work.join();
                resize(game, 1920, 1080);
                open(game);
                checkSlideGeometry();
                readyFrame = ClientSmokeTest.renderedFrames;
                wait = 6;
            }
            case 10 -> {
                if (ClientSmokeTest.renderedFrames <= readyFrame) return false;
                ClientPreferences.get().reducedMotion = false;
                openHeight = ((JournalListModel) field(screen, "journalList")).contentHeight();
                followingY = button("group-order_group_world").getY();
                firstChildY = button("quest-" + A).getY();
                clickButton("group-order_group_main");
                require(slide().position() > 0 && !slide().visible(),
                        "Closing removed group children before the slide started");
                require(button("quest-" + A).visible, "Closing task disappeared immediately");
                wait = 1;
            }
            case 11 -> {
                require(slide().position() > 0 && slide().position() < 1, "Group has no close animation");
                require(button("quest-" + A).getY() < firstChildY, "Closing child did not move up");
                require(button("group-order_group_world").getY() < followingY, "Following rows did not move up");
                checkChildClip();
                capture(game, "journal-group-closing");
                invoke(screen, "animateGroups", new Class<?>[]{long.class}, System.nanoTime());
                invoke(screen, "updateListButtons", new Class<?>[]{});
                double position = slide().position();
                clickButton("group-order_group_main");
                require(slide().visible() && Math.abs(slide().position() - position) < .15,
                        "Rapid reversal snapped group progress: " + position + " -> " + slide().position()
                                + ", opening=" + slide().visible() + ", blocked=" + screen.inputBlocked());
                wait = 3;
            }
            case 12 -> {
                require(slide().visible() && slide().position() > 0, "Reversal did not reopen the group");
                wait = 14;
            }
            case 13 -> {
                require(slide().settled() && slide().position() == 1, "Reopened slide did not settle");
                require(((JournalListModel) field(screen, "journalList")).contentHeight() == openHeight,
                        "Reopening changed the final content height");
                clickButton("group-order_group_main");
                wait = 14;
            }
            case 14 -> {
                require(slide().settled() && slide().position() == 0, "Close did not finish within the configured duration");
                require(screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).noneMatch(b -> b.styleId().equals("quest-" + A)),
                        "Closed child widgets were not removed");
                capture(game, "journal-group-collapsed");
                clickButton("group-order_group_main");
                wait = 3;
            }
            case 15 -> {
                require(slide().position() > 0 && slide().position() < 1, "Group has no open animation");
                require(button("quest-" + A).getY() < firstChildY, "Opening child did not slide from the group");
                checkChildClip();
                capture(game, "journal-group-opening");
                wait = 14;
            }
            case 16 -> {
                require(slide().settled() && slide().position() == 1, "Open did not finish within the configured duration");
                require(button("quest-" + A).getY() == firstChildY, "Expanded child ended at the wrong position");
                require(button("group-order_group_world").getY() == followingY, "Following rows did not settle");
                capture(game, "journal-group-expanded");
                ClientPreferences.get().reducedMotion = true;
                clickButton("group-order_group_main");
                require(slide().position() == 0, "Reduced motion did not collapse immediately");
                clickButton("group-order_group_main");
                require(slide().position() == 1, "Reduced motion did not expand immediately");
                scaleChecks = true;
                return false;
            }
            default -> throw new AssertionError("Unknown journal order stage " + stage);
        }
        stage++;
        if (wait <= 0) wait = 5;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static UiMotion slide() throws Exception {
        return ((Map<String, UiMotion>) field(screen, "groupSlides")).get("order_group_main");
    }

    private static void checkChildClip() throws Exception {
        var header = button("group-order_group_main");
        var child = button("quest-" + A);
        require(!child.isMouseOver(child.getX() + 30, header.getY() + header.getHeight() / 2.),
                "A child behind its header remained clickable");
        int top = (int) invoke(screen, "groupClipTop", new Class<?>[]{String.class}, "quest:" + A);
        int bottom = (int) invoke(screen, "groupClipBottom", new Class<?>[]{String.class}, "quest:" + A);
        require(top >= header.getBottom() && bottom > top, "Task slide clip escaped the group body");
        require(!child.isMouseOver(child.getX() + 30, bottom), "Clipped task remained clickable below its group");
    }

    private static void checkSlideGeometry() {
        var tasks = List.of(QuestRepository.find(A), QuestRepository.find(B), QuestRepository.find(C),
                QuestRepository.find(D), QuestRepository.find(U));
        var groups = ClientQuestDefinitions.groups().stream().filter(g -> g.id().startsWith("order_group")).toList();
        var source = JournalListModel.build(tasks, groups, QuestRepository::groupOf, Set.of(), false);
        var expanded = JournalGroupSlide.apply(source, Map.of("order_group_main", 1.));
        require(expanded.model().equals(source), "Fully open group geometry differs from its source");
        var closed = JournalGroupSlide.apply(source, Map.of("order_group_main", 0.));
        var target = JournalListModel.build(tasks, groups, QuestRepository::groupOf, Set.of("order_group_main"), false);
        require(closed.model().contentHeight() == target.contentHeight(), "Collapsed content height is incorrect");
        for (var row : target.rows()) {
            var actual = closed.model().rows().stream().filter(r -> r.key().equals(row.key())).findFirst().orElseThrow();
            require(actual.offset() == row.offset(), "Collapse moved a neighboring row incorrectly");
        }
        var half = JournalGroupSlide.apply(source, Map.of("order_group_main", .5));
        require(source.contentHeight() - half.model().contentHeight()
                        == (source.contentHeight() - target.contentHeight()) / 2,
                "Half-open group height is incorrect");
        require(half.model().rows().stream().filter(r -> r.key().equals("quest:" + A)).findFirst()
                        .orElseThrow().offset() < source.rows().stream().filter(r -> r.key().equals("quest:" + A))
                        .findFirst().orElseThrow().offset(),
                "Half-open children were resized rather than translated");
        var motion = new UiMotion(false, 0);
        motion.target(true, 0, 100);
        require(motion.advance(50_000_000L, 100) == .5, "Group timing is not 100 ms");
        motion.target(false, 50_000_000L, 100);
        require(motion.position() == .5 && motion.advance(100_000_000L, 100) == 0,
                "Group reversal discontinuity");
    }

    private static void fixtures(ServerPlayer player) throws Exception {
        JournalScaleRegression.cleanup();
        Files.createDirectories(QuestRepository.root());
        Files.deleteIfExists(QuestJournalOrderStore.file());
        var first = QuestRepository.root().resolve("order_group_main");
        var second = QuestRepository.root().resolve("order_group_world");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.writeString(first.resolve("group_manifest.json"),
                "{\"id\":\"order_group_main\",\"name\":\"Order Main Group\",\"type\":\"main\"}");
        Files.writeString(second.resolve("group_manifest.json"),
                "{\"id\":\"order_group_world\",\"name\":\"Order World Group\",\"type\":\"world\"}");
        for (String id : List.of(A, B, C)) writeTask(first, id, id.equals(C) ? "world" : "main");
        writeTask(second, D, "world");
        writeTask(QuestRepository.root(), U, "main");
        writeTask(QuestRepository.root(), V, "world");
        QuestRepository.reload();
        require(QuestRepository.errors().isEmpty(), "Order fixtures invalid: " + QuestRepository.errors());
        runtimeOrder = QuestRepository.all().stream().map(QuestDefinition::id).toList();
        sourceA = QuestRepository.source(A);
        player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, true);
        var defaultTabs = QuestJournalOrder.defaultTabs(QuestTypeStore.all());
        require(QuestIcons.matchesSearch("order", null, "Order title")
                        && !QuestIcons.matchesSearch("order", (String) null),
                "Search mishandled missing optional translation fields");
        require(defaultTabs.getFirst().equals("active") && defaultTabs.getLast().equals("completed"),
                "Default tab endpoints are wrong");
        require(QuestJournalOrder.apply(List.of("a", "b", "new"), List.of("removed", "b", "a"))
                .equals(List.of("b", "a", "new")), "Saved order mishandled deleted/new tasks");
        var model = JournalListModel.build(List.of(QuestRepository.find(A), QuestRepository.find(U)),
                List.of(), QuestRepository::groupOf, Set.of(), false);
        require(model.rows().size() == 1 && model.rows().getFirst().quest().id().equals(U),
                "A grouped task became standalone when its group was filtered out");
    }

    private static void writeTask(Path folder, String id, String type) throws Exception {
        String source = "{\"id\":\"" + id + "\",\"title\":\"Order "
                + id + "\",\"type\":\"" + type + "\",\"unlocked\":true,"
                + "\"triggers\":[{\"type\":\"confirm\",\"item\":\"\",\"count\":1}]}";
        var presentation = new QuestPresentation(true, 395, 275, List.of(
                new QuestPresentation.Control("title", "quest_title", "", 12, 12, 260, 20, "",
                        Map.of("textColor", "#ffffff"), TriggerGraph.empty()),
                new QuestPresentation.Control("status", "quest_status", "", 12, 42, 180, 16, "",
                        Map.of("textColor", "#97d4c5"), TriggerGraph.empty())));
        Files.writeString(folder.resolve(id + ".json"), QuestPresentationDocuments.write(source, presentation));
    }

    private static void serverChecks(ServerPlayer player) throws Exception {
        require(QuestRepository.source(A).equals(sourceA), "Reordering modified task source");
        require(QuestRepository.all().stream().map(QuestDefinition::id).toList().equals(runtimeOrder),
                "Display ordering changed the runtime task enumeration");
        var saved = QuestRepository.journalOrder();
        long revision = QuestRepository.revision();
        require(QuestJournalOrderStore.move(player, revision - 1, "task", A, B, -1)
                .equals("definition_changed"), "Stale order revision was accepted");
        require(QuestJournalOrderStore.move(player, revision, "task", A, D, 1)
                .equals("invalid_action"), "Movement crossed group membership");
        require(QuestJournalOrderStore.move(player, revision, "task", A, U, 1)
                .equals("invalid_action"), "Grouped task could move into standalone tasks");
        player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, false);
        require(QuestJournalOrderStore.move(player, revision, "tab", "active", "main", 1)
                .equals("permission"), "Server accepted ordering with editing disabled");
        player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, true);
        require(QuestRepository.revision() == revision && QuestRepository.journalOrder().equals(saved),
                "Rejected request changed saved order");
        QuestRepository.reload();
        require(QuestRepository.errors().isEmpty() && QuestRepository.journalOrder().equals(saved),
                "Disk reload lost display order");
        var catalog = QuestCatalog.decode(QuestCatalog.serverJson());
        require(catalog.journalOrder().equals(saved), "Remote catalog lost display order");
        require(QuestJournalOrderStore.move(player, QuestRepository.revision(), "task", A, B, -1)
                .equals("journal_order_saved"), "Filtered-neighbor move failed");
        var taskOrder = QuestRepository.journalOrder().tasks();
        require(taskOrder.indexOf(A) + 1 == taskOrder.indexOf(B), "Filtered neighbor was not respected");
        require(QuestRepository.groupOf(A).equals("order_group_main"), "Order changed group membership");
        require(QuestRepository.source(A).equals(sourceA), "Filtered move mutated source");
    }

    private static void checkCodec(Minecraft game) {
        var move = new QuestJournalOrderNetwork.Move(10, ClientQuestDefinitions.revision(), "task", A, B, 1);
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), game.player.registryAccess());
        try {
            QuestJournalOrderNetwork.Move.CODEC.encode(b, move);
            require(QuestJournalOrderNetwork.Move.CODEC.decode(b).equals(move), "Order codec did not round-trip");
        } finally { b.release(); }
        try {
            new QuestJournalOrderNetwork.Move(10, 0, "task", A, A, 1);
            throw new AssertionError("Self-neighbor accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void open(Minecraft game) throws Exception {
        screen = QuestScreen.task(null, A);
        game.setScreen(screen);
        set(screen, "editMode", true);
        set(screen, "searchQuery", "order_");
        screen.init(game, screen.width, screen.height);
    }

    private static boolean settled() throws Exception {
        boolean hadRequest = field(screen, "orderPending") != null;
        screen.tick();
        if (hadRequest && field(screen, "orderPending") == null) {
            wait = 3;
            return false;
        }
        return field(screen, "orderPending") == null;
    }

    private static void clickButton(String id) {
        var button = button(id);
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(screen.mouseClicked(x, y, 0), "Mouse press missed order control " + id);
        screen.mouseReleased(x, y, 0);
    }

    private static void rightTab(String id) throws Exception {
        var tab = button("category-" + id);
        require(tab.visible, "Moved tab is not visible");
        screen.mouseClicked(tab.getX() + tab.getWidth() / 2., tab.getY() + tab.getHeight() / 2., 1);
    }

    private static void checkRows() throws Exception {
        boolean standalone = false;
        String group = "";
        for (var row : rows()) {
            if (row.section()) continue;
            if (row.group() != null) {
                require(!standalone, "Group header appeared after standalone task");
                group = row.group().id();
            } else if (ClientQuestDefinitions.groupOf(row.quest().id()).isBlank()) {
                standalone = true;
                require(row.indent() == 0, "Standalone task is indented inside a group");
            } else {
                require(!standalone && ClientQuestDefinitions.groupOf(row.quest().id()).equals(group)
                        && row.indent() > 0, "Task is outside its own group");
            }
        }
    }

    private static List<JournalListModel.Row> rows() throws Exception {
        return ((JournalListModel) field(screen, "journalList")).rows();
    }
    private static List<String> groupTasks(String group) throws Exception {
        return rows().stream().filter(r -> r.quest() != null
                && ClientQuestDefinitions.groupOf(r.quest().id()).equals(group)).map(r -> r.quest().id()).toList();
    }
    private static List<String> standaloneTasks() throws Exception {
        return rows().stream().filter(r -> r.quest() != null
                && ClientQuestDefinitions.groupOf(r.quest().id()).isBlank()).map(r -> r.quest().id()).toList();
    }
    @SuppressWarnings("unchecked")
    private static List<String> categories() throws Exception {
        return (List<String>) invoke(screen, "categories", new Class<?>[]{});
    }
    private static StyledButton button(String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing order button " + id));
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(owner, args);
    }
    private static void resize(Minecraft game, int width, int height) {
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), width, height);
        game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        var root = Path.of("..", "build", "verification");
        Files.createDirectories(root);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(root.resolve(name + ".png"));
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
