package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.ClientQuestProgress;
import xtdpotato.xero_quest_journal.client.UiMotion;
import xtdpotato.xero_quest_journal.client.ClientPreferences;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.QuestUnlockGraphScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestUnlockLayout;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class UnlockGraphRegression {
    private static final String PREFIX = "unlock_graph_" + System.currentTimeMillis();
    private static final String A = PREFIX + "_a", B = PREFIX + "_b", C = PREFIX + "_c";
    private static int stage;
    private static CompletableFuture<?> pending;
    private static QuestUnlockGraphScreen editor;
    private static QuestScreen journal;
    private static CompletableFuture<String> lockRequest;
    private static int waitTicks;
    private static long transitionStartFrame;
    private static int originalPopupMillis;

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                pending = CompletableFuture.runAsync(() -> {
                    try {
                        Files.createDirectories(QuestRepository.root());
                        for (String id : List.of(A, B, C))
                            Files.writeString(QuestRepository.root().resolve(id + ".html"),
                                    "<quest id=\"" + id + "\" name=\"" + id
                                            + "\" type=\"normal\" unlocked=\"" + id.equals(A) + "\"/>");
                        for (int i = 0; i < 32; i++) {
                            String id = PREFIX + "_scroll_" + i;
                            Files.writeString(QuestRepository.root().resolve(id + ".html"),
                                    "<quest id=\"" + id + "\" name=\"Scroll fixture " + i
                                            + "\" type=\"normal\" unlocked=\"false\"/>");
                        }
                        QuestRepository.reload();
                        require(QuestRepository.errors().isEmpty(), "Invalid test definitions");
                    } catch (Exception error) { throw new RuntimeException(error); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone()) return false;
                pending.join();
                checkLargeDefaultLayout();
                journal = QuestScreen.task(null, A);
                field(journal, "editMode").setBoolean(journal, true);
                game.setScreen(journal);
                button(journal, "unlock_graph.title").onPress();
                require(game.screen instanceof QuestUnlockGraphScreen, "Unlock graph toolbar did not open");
                editor = (QuestUnlockGraphScreen) game.screen;
                @SuppressWarnings("unchecked")
                Map<String, QuestUnlockLayout.Position> positions =
                        (Map<String, QuestUnlockLayout.Position>) field(editor, "positions").get(editor);
                positions.put(A, new QuestUnlockLayout.Position(30, 30));
                positions.put(B, new QuestUnlockLayout.Position(240, 30));
                positions.put(C, new QuestUnlockLayout.Position(450, 30));
                method(editor, "rebuildViewportIndexes").invoke(editor);
                method(editor, "fit").invoke(editor);
                @SuppressWarnings("unchecked")
                List<QuestDefinition> allTasks = (List<QuestDefinition>) field(editor, "tasks").get(editor);
                @SuppressWarnings("unchecked")
                List<QuestDefinition> visibleTasks = (List<QuestDefinition>)
                        method(editor, "visibleTasks").invoke(editor);
                require(allTasks.size() < 100 || visibleTasks.size() < allTasks.size(),
                        "Unlock graph still prepares every off-screen task for drawing");
                @SuppressWarnings("unchecked")
                Set<Object> allLinks = (Set<Object>) field(editor, "links").get(editor);
                @SuppressWarnings("unchecked")
                List<Object> visibleLinks = (List<Object>) method(editor, "visibleLinks").invoke(editor);
                require(allLinks.size() < 100 || visibleLinks.size() < allLinks.size(),
                        "Unlock graph still prepares every off-screen link for drawing");
                checkEditorInteractions(game);
                double outX = (double) method(editor, "sx", double.class).invoke(editor, 219.);
                double targetX = (double) method(editor, "sx", double.class).invoke(editor, 260.);
                double y = (double) method(editor, "sy", double.class).invoke(editor, 76.);
                String sourceHit = (String) method(editor, "nodeAt", double.class, double.class)
                        .invoke(editor, outX, y);
                String targetHit = (String) method(editor, "nodeAt", double.class, double.class)
                        .invoke(editor, targetX, y);
                editor.mouseClicked(outX, y, 0);
                String wireAfterClick = (String) field(editor, "wireFrom").get(editor);
                editor.mouseReleased(outX, y, 0);
                editor.mouseClicked(targetX, y, 0);
                editor.mouseReleased(targetX, y, 0);
                require(hasLink(editor, A, B), "Click-to-connect did not link A to B: "
                        + "source=" + sourceHit + ", target=" + targetHit + ", wire=" + wireAfterClick
                        + ", point=" + outX + "," + targetX + "," + y);
                method(editor, "connect", String.class, String.class).invoke(editor, B, C);
                method(editor, "connect", String.class, String.class).invoke(editor, A, C);
                require(hasLink(editor, B, C) && hasLink(editor, A, C),
                        "Sequential and branching links missing");
                method(editor, "connect", String.class, String.class).invoke(editor, C, A);
                require(!hasLink(editor, C, A), "Cycle accepted");
                button(editor, "unlock-save").onPress();
            }
            case 2 -> {
                if (game.screen == editor) return false;
                require(game.screen == journal, "Save failed to close graph");
                pending = CompletableFuture.runAsync(() -> {
                    require(QuestRepository.find(B).parents().equals(List.of(A)), "A -> B not saved");
                    require(Set.copyOf(QuestRepository.find(C).parents()).equals(Set.of(A, B)),
                            "Branch and chain prerequisites not saved");
                    require(QuestRepository.unlockLayout().positions().get(B).x() == 240,
                            "Node position not persisted");
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    Map<String, List<String>> cyclic = new java.util.HashMap<>();
                    for (var task : QuestRepository.all()) cyclic.put(task.id(), task.parents());
                    cyclic.put(A, List.of(C));
                    Map<String, String> hashes = new java.util.HashMap<>();
                    for (var task : QuestRepository.all()) {
                        try {
                            hashes.put(task.id(), QuestRepository.fingerprint(QuestRepository.source(task.id())));
                        } catch (Exception error) { throw new RuntimeException(error); }
                    }
                    require(!QuestAuthoring.editUnlockGraph(player, QuestRepository.revision(),
                                    cyclic, hashes, QuestRepository.unlockLayout()).successful(),
                            "Server accepted a prerequisite cycle");
                    require(!QuestAuthoring.editUnlockGraph(player, QuestRepository.revision() - 1,
                                    cyclic, hashes, QuestRepository.unlockLayout()).successful(),
                            "Server accepted stale graph revision");
                    require(QuestRepository.find(A).parents().isEmpty(), "Invalid graph mutated source");
                    var data = new QuestProgressData();
                    require(QuestRuntime.unlocked(data, player, QuestRepository.find(A)),
                            "Starting task is not available");
                    require(!QuestRuntime.unlocked(data, player, QuestRepository.find(B)),
                            "B unlocked before A completion");
                    require(!QuestRuntime.unlocked(data, player, QuestRepository.find(C)),
                            "C unlocked before both prerequisites");
                    data.complete(player.getUUID(), QuestRepository.find(A));
                    require(QuestRuntime.unlocked(data, player, QuestRepository.find(B)),
                            "B stayed locked after A completion");
                    require(!QuestRuntime.unlocked(data, player, QuestRepository.find(C)),
                            "C unlocked before B completion");
                    data.complete(player.getUUID(), QuestRepository.find(B));
                    require(QuestRuntime.unlocked(data, player, QuestRepository.find(C)),
                            "C stayed locked after both completions");
                }, game.getSingleplayerServer());
            }
            case 3 -> {
                if (!pending.isDone()) return false;
                pending.join();
                game.setScreen(new QuestUnlockGraphScreen(journal));
                editor = (QuestUnlockGraphScreen) game.screen;
                require(hasLink(editor, A, B) && hasLink(editor, A, C) && hasLink(editor, B, C),
                        "Graph did not reopen with links");
                method(editor, "removeLink", links(editor).iterator().next().getClass())
                        .invoke(editor, links(editor).iterator().next());
                editor.onClose();
                require(field(editor, "confirmExit").getBoolean(editor), "Unsaved changes not confirmed");
                button(editor, "unlock-discard").onPress();
            }
            case 4 -> {
                if (game.screen == editor) return false;
                require(QuestRepository.find(B).parents().equals(List.of(A)),
                        "Discarded changes altered stored prerequisites");
                if (ClientQuestProgress.catalogRevision() != QuestRepository.revision()) return false;
                game.setScreen(new QuestUnlockGraphScreen(journal));
                editor = (QuestUnlockGraphScreen) game.screen;
                method(editor, "toggleMultiple").invoke(editor);
                method(editor, "selectTask", String.class).invoke(editor, A);
                method(editor, "selectTask", String.class).invoke(editor, B);
                method(editor, "openContext", String.class, double.class, double.class)
                        .invoke(editor, A, 200., 90.);
                clickContext(4);
                lockRequest = (CompletableFuture<String>) field(editor, "lockPending").get(editor);
                require(lockRequest != null, "Batch lock did not start");
            }
            case 5 -> {
                if (!lockRequest.isDone()) return false;
                require(lockRequest.join().equals("locked"), "Batch lock failed: " + lockRequest.join());
                require(!ClientQuestProgress.view(A).unlocked() && !ClientQuestProgress.view(B).unlocked(),
                        "Server batch lock left a task unlocked");
                require(selection().containsAll(Set.of(A, B)), "Batch lock cleared selection");
                checkLockMessage("tasks_locked");
                waitTicks = 4;
            }
            case 6 -> {
                if (--waitTicks > 0) return false;
                capture(game, "unlock-graph-lock-success");
                method(editor, "openContext", String.class, double.class, double.class)
                        .invoke(editor, A, 200., 90.);
                clickContext(5);
                lockRequest = (CompletableFuture<String>) field(editor, "lockPending").get(editor);
            }
            case 7 -> {
                if (!lockRequest.isDone()) return false;
                require(lockRequest.join().equals("unlocked"), "Batch unlock failed: " + lockRequest.join());
                require(ClientQuestProgress.view(A).unlocked() && ClientQuestProgress.view(B).unlocked(),
                        "Server batch unlock left a task locked");
                checkLockMessage("tasks_unlocked");
                button(editor, "unlock-task-list").onPress();
                ((EditBox) field(editor, "search").get(editor)).setValue(PREFIX);
                waitTicks = 4;
            }
            case 8 -> {
                if (--waitTicks > 0) return false;
                capture(game, "unlock-graph-drawer");
                checkDrawerColors(game);
                button(editor, "unlock-task-list").onPress();
                waitTicks = 3;
            }
            case 9 -> {
                if (--waitTicks > 0) return false;
                capture(game, "unlock-graph-canvas");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 720, 540);
                game.resizeDisplay();
                button(editor, "unlock-task-list").onPress();
                ((EditBox) field(editor, "search").get(editor)).setValue("");
                waitTicks = 4;
            }
            case 10 -> {
                if (--waitTicks > 0) return false;
                require(editor.width <= 720, "Compact resize was not applied");
                var rail = (net.minecraft.client.gui.components.AbstractWidget)
                        field(editor, "drawerRail").get(editor);
                require(rail.visible, "Long task list had no scrollbar");
                var scroll = field(editor, "drawerScroll").get(editor);
                double before = (double) method(scroll, "target").invoke(scroll);
                editor.mouseScrolled(30, 100, 0, -2);
                double after = (double) method(scroll, "target").invoke(scroll);
                require(after > before, "Drawer wheel scrolled the canvas instead of the task list");
                require((double) field(editor, "zoom").get(editor) <= 2, "Drawer wheel invalidated canvas zoom");
                capture(game, "unlock-graph-compact");
                waitTicks = 4;
            }
            case 11 -> {
                if (--waitTicks > 0) return false;
                ClientPreferences.get().reducedMotion = false;
                button(editor, "unlock-task-list").onPress();
                require(!field(editor, "taskDrawer").getBoolean(editor), "Drawer target did not close");
                var motion = (UiMotion) field(editor, "drawerMotion").get(editor);
                require(motion.position() > 0, "Drawer closure snapped instead of animating");
                waitTicks = 8;
            }
            case 12 -> {
                if (--waitTicks > 0) return false;
                var motion = (UiMotion) field(editor, "drawerMotion").get(editor);
                require(motion.settled() && motion.position() == 0, "Drawer close animation failed to settle");
                xtdpotato.xero_quest_journal.core.QuestConfig.defaults().uiAnimationMillis = 600;
                originalPopupMillis = ClientPreferences.get().popupAnimationMillis;
                ClientPreferences.get().popupAnimationMillis = 600;
                field(editor, "propertiesTask").set(editor, A);
                transitionStartFrame = ClientSmokeTest.renderedFrames;
                waitTicks = 1;
            }
            case 13 -> {
                if (ClientSmokeTest.renderedFrames < transitionStartFrame + 2) return false;
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("properties") > 0
                                && editor.menuAnimationPosition("properties") < 1 && editor.inputBlocked(),
                        "Task properties skipped scale/fade entry");
                capture(game, "unlock-properties-opening");
                waitTicks = 20;
            }
            case 14 -> {
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("properties") == 1 && !editor.inputBlocked(),
                        "Task properties entry did not settle");
                editor.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(field(editor, "propertiesTask").get(editor).equals(""), "Escape did not close properties");
                transitionStartFrame = ClientSmokeTest.renderedFrames;
                waitTicks = 1;
            }
            case 15 -> {
                if (ClientSmokeTest.renderedFrames < transitionStartFrame + 2) return false;
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("properties") > 0
                                && editor.menuAnimationPosition("properties") < 1 && editor.inputBlocked(),
                        "Task properties skipped exit animation");
                capture(game, "unlock-properties-closing");
                waitTicks = 20;
            }
            case 16 -> {
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("properties") == 0 && !editor.inputBlocked(),
                        "Task properties left a blocking layer");
                method(editor, "removeLink", links(editor).iterator().next().getClass())
                        .invoke(editor, links(editor).iterator().next());
                editor.onClose();
                transitionStartFrame = ClientSmokeTest.renderedFrames;
                waitTicks = 1;
            }
            case 17 -> {
                if (ClientSmokeTest.renderedFrames < transitionStartFrame + 2) return false;
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("confirm") > 0 && editor.menuAnimationPosition("confirm") < 1,
                        "Unsaved confirmation skipped scale/fade entry");
                capture(game, "unlock-confirm-opening");
                waitTicks = 20;
            }
            case 18 -> {
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("confirm") == 1 && !editor.inputBlocked(),
                        "Unsaved confirmation entry did not settle");
                var cancel = button(editor, "unlock-continue");
                editor.mouseClicked(cancel.getX() + 10, cancel.getY() + 10, 0);
                editor.mouseReleased(cancel.getX() + 10, cancel.getY() + 10, 0);
                require(!field(editor, "confirmExit").getBoolean(editor), "Cancel did not dismiss confirmation");
                transitionStartFrame = ClientSmokeTest.renderedFrames;
                waitTicks = 1;
            }
            case 19 -> {
                if (ClientSmokeTest.renderedFrames < transitionStartFrame + 2) return false;
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("confirm") > 0 && editor.menuAnimationPosition("confirm") < 1,
                        "Unsaved confirmation skipped shrink/fade exit");
                capture(game, "unlock-confirm-closing");
                waitTicks = 20;
            }
            case 20 -> {
                if (--waitTicks > 0) return false;
                require(editor.menuAnimationPosition("confirm") == 0 && !editor.inputBlocked() && game.screen == editor,
                        "Cancel left editor input blocked or closed the editor");
                xtdpotato.xero_quest_journal.core.QuestConfig.defaults().uiAnimationMillis = 220;
                ClientPreferences.get().popupAnimationMillis = originalPopupMillis;
                return true;
            }
        }
        stage++;
        return false;
    }

    private static void checkEditorInteractions(Minecraft game) throws Exception {
        var motion = new UiMotion(false, 0);
        motion.target(true, 0, 200);
        require(motion.advance(100_000_000L, 200) == .5, "Drawer entry clock is frame-dependent");
        motion.target(false, 100_000_000L, 200);
        require(motion.advance(150_000_000L, 200) == .25, "Reversing drawer snapped its position");
        require(motion.advance(200_000_000L, 200) == 0, "Drawer exit did not settle");

        method(editor, "selectTask", String.class).invoke(editor, A);
        var x = (EditBox) field(editor, "coordinateX").get(editor);
        var y = (EditBox) field(editor, "coordinateY").get(editor);
        require(x.getValue().equals("30") && y.getValue().equals("30"), "Selected coordinates not displayed");
        x.setValue("77");
        require(position(A).x() == 77, "Coordinate X did not update node");
        button(editor, "unlock-undo").onPress();
        require(position(A).x() == 30, "Coordinate edit could not undo");
        button(editor, "unlock-redo").onPress();
        require(position(A).x() == 77, "Coordinate edit could not redo");
        button(editor, "unlock-undo").onPress();
        x = (EditBox) field(editor, "coordinateX").get(editor);
        x.setValue("100001");
        require(position(A).x() == 30, "Out-of-range coordinate mutated node");
        x.setValue("30");

        button(editor, "unlock-multi-select").onPress();
        clickNode(B);
        clickNode(C);
        require(selection().equals(Set.of(A, B, C)), "Multi-selection replaced previous tasks");
        editor.mouseClicked(editor.width - 20, editor.height - 80, 0);
        require(selection().equals(Set.of(A, B, C)), "Blank canvas cleared multi-selection");
        editor.setFocused(null);
        editor.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        Map<?, ?> byId = (Map<?, ?>) field(editor, "byId").get(editor);
        require(selection().size() == byId.size(), "Ctrl+A did not select all tasks");
        method(editor, "invertSelection").invoke(editor);
        require(selection().isEmpty(), "Invert did not invert all");
        method(editor, "invertSelection").invoke(editor);
        require(selection().size() == byId.size(), "Invert did not restore all");
        button(editor, "unlock-multi-select").onPress();
        method(editor, "selectTask", String.class).invoke(editor, A);

        button(editor, "unlock-task-list").onPress();
        require(field(editor, "taskDrawer").getBoolean(editor), "Task list did not open");
        button(editor, "unlock-task-list").onPress();
        require(!field(editor, "taskDrawer").getBoolean(editor), "Task list did not close");
        field(editor, "selected").set(editor, "");
        method(editor, "init").invoke(editor);

        var payload = new xtdpotato.xero_quest_journal.core.network.QuestNetwork.LockBatchPayload(
                21, 9, List.of(A, B), true);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                game.player.registryAccess());
        try {
            var codec = xtdpotato.xero_quest_journal.core.network.QuestNetwork.LockBatchPayload.STREAM_CODEC;
            codec.encode(buffer, payload);
            require(codec.decode(buffer).equals(payload), "Batch lock codec did not round-trip");
        } finally {
            buffer.release();
        }
        try {
            new xtdpotato.xero_quest_journal.core.network.QuestNetwork.LockBatchPayload(
                    21, 9, List.of(A, A), false);
            throw new AssertionError("Duplicate batch targets accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void checkLargeDefaultLayout() throws Exception {
        var type = Class.forName("xtdpotato.xero_quest_journal.client.QuestUnlockGraphLayout");
        var method = type.getDeclaredMethod("initial", List.class, Map.class);
        method.setAccessible(true);
        var ids = new java.util.ArrayList<String>();
        var parents = new java.util.LinkedHashMap<String, List<String>>();
        for (int i = 0; i < 1200; i++) {
            String id = "unlock_scale_" + i;
            ids.add(id);
            parents.put(id, List.of());
        }
        @SuppressWarnings("unchecked")
        Map<String, QuestUnlockLayout.Position> positions =
                (Map<String, QuestUnlockLayout.Position>) method.invoke(null, ids, parents);
        require(positions.size() == ids.size(), "Large default layout lost nodes");
        var distinct = new java.util.HashSet<String>();
        for (var entry : positions.entrySet()) {
            var point = entry.getValue();
            require(Math.abs((long) point.x()) <= 100000 && Math.abs((long) point.y()) <= 100000,
                    "Large default node exceeded the persisted coordinate range: " + entry);
            require(distinct.add(point.x() + "," + point.y()), "Large default nodes overlap");
        }
    }
    private static void checkLockMessage(String key) throws Exception {
        editor.tick();
        String translation = "screen.xero_quest_journal.unlock_graph." + key;
        require(Language.getInstance().has(translation), "Missing lock success translation: " + translation);
        String message = (String) field(editor, "lockResult").get(editor);
        require(message.equals(Component.translatable(translation).getString()),
                "Incorrect lock success message: " + message);
        require(!message.equals(Component.translatable("screen.xero_quest_journal.action.locked").getString()),
                "Lock success reused the prerequisite error message");
        require(field(editor, "error").get(editor).equals(""), "Successful lock action displayed an error");
        require(field(editor, "lockPending").get(editor) == null, "Completed lock request was not cleared");
    }
    @SuppressWarnings("unchecked")
    private static Set<String> selection() throws Exception {
        return (Set<String>) field(editor, "selectedTasks").get(editor);
    }
    private static QuestUnlockLayout.Position position(String id) throws Exception {
        return (QuestUnlockLayout.Position) ((Map<?, ?>) field(editor, "positions").get(editor)).get(id);
    }
    private static void clickContext(int row) throws Exception {
        int x = field(editor, "contextX").getInt(editor) + 12;
        int y = field(editor, "contextY").getInt(editor) + row * 22 + 8;
        editor.mouseClicked(x, y, 0);
        editor.mouseReleased(x, y, 0);
    }
    private static void clickNode(String id) throws Exception {
        var p = position(id);
        double x = (double) method(editor, "sx", double.class).invoke(editor, p.x() + 50.);
        double y = (double) method(editor, "sy", double.class).invoke(editor, p.y() + 20.);
        editor.mouseClicked(x, y, 0);
        editor.mouseReleased(x, y, 0);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        var output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        }
    }
    private static void checkDrawerColors(Minecraft game) throws Exception {
        int w = (int) method(editor, "drawerWidth").invoke(editor);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            for (int row : List.of(0, 2)) {
                int abgr = image.getPixelRGBA((w - 20) * image.getWidth() / editor.width,
                        (70 + row * 28 + 14) * image.getHeight() / editor.height);
                int rgb = (abgr & 255) << 16 | abgr & 0xFF00 | abgr >>> 16 & 255;
                require(rgb == (row == 0 ? 0x315A43 : 0x5A3438),
                        "Drawer unlock/lock status color did not render: " + Integer.toHexString(rgb));
            }
        }
    }
    private static Set<?> links(QuestUnlockGraphScreen graph) throws Exception {
        return (Set<?>) field(graph, "links").get(graph);
    }
    private static boolean hasLink(QuestUnlockGraphScreen graph, String from, String to) throws Exception {
        for (Object link : links(graph))
            if (from.equals(method(link, "from").invoke(link))
                    && to.equals(method(link, "to").invoke(link))) return true;
        return false;
    }
    private static java.lang.reflect.Field field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static java.lang.reflect.Method method(Object target, String name, Class<?>... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(child -> child instanceof StyledButton b
                        && b.styleId().equals(id)).map(child -> (StyledButton) child).findFirst()
                .orElseThrow(() -> new AssertionError("Missing button " + id));
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
