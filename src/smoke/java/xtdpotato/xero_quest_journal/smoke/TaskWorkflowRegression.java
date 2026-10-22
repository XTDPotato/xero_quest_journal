package xtdpotato.xero_quest_journal.smoke;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.*;

final class TaskWorkflowRegression {
    private static final String PREFIX = "workflow_" + System.currentTimeMillis(), TASK = PREFIX + "_template";
    private static int stage, wait;
    private static long nextFrame;
    private static CompletableFuture<?> pending;
    private static QuestScreen journal;
    private static QuestUnlockGraphScreen graph;
    private static String templateHash;
    private static List<QuestPresentation.Control> before;
    private static long hoverAt;
    private static int hoverX, hoverY;
    private static Map<String, Set<String>> oldFilters;
    private static Set<String> oldScopes;
    private static final Path OUTPUT = Path.of("..", "build", "verification");

    static boolean tick(Minecraft game) throws Exception {
        if (stage == 14) return TaskUiCorrectionsRegression.tick(game);
        MailArrivalToast.clear(); QuestUnlockToast.clear();
        if (stage == 5) cursor(game, hoverX, hoverY);
        if (--wait > 0 || ClientSmokeTest.renderedFrames < nextFrame
                || game.screen instanceof AnimatedQuestScreen a && a.inputBlocked()) return false;
        if (pending != null) { if (!pending.isDone()) return false; pending.join(); pending = null; }
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true; ClientEditMode.set(true);
                oldFilters = ClientPreferences.get().questSearchFilters; oldScopes = ClientPreferences.get().questSearchScopes;
                ClientPreferences.get().questSearchFilters = Map.of(); ClientPreferences.get().questSearchScopes = Set.of("title","description");
                resize(game, 1920, 1080); game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                pending = game.reloadResourcePacks();
            }
            case 1 -> {
                pending = CompletableFuture.runAsync(() -> {
                    try {
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        for (String group : List.of(PREFIX + "_g1", PREFIX + "_g2")) require(QuestAuthoring.create(player,
                                new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP, group, group, "", "", "", "world", "", 1)).successful(), "Group create");
                        for (int i = 0; i < (Boolean.getBoolean("xero.unlockGraphPerf") ? 1200 : 120); i++) {
                            String id = PREFIX + String.format("_%03d", i);
                            String parent = i % 60 == 0 ? "" : "<parents><parent id='" + PREFIX + String.format("_%03d", i - 1) + "'/></parents>";
                            AtomicFiles.write(QuestRepository.root().resolve("group_" + PREFIX + (i < 60 ? "_g1" : "_g2"))
                                    .resolve(id + ".xml"), "<quest id='" + id
                                    + "' name='分组任务 " + i + "' type='world'>" + parent + "</quest>", false);
                        }
                        QuestRepository.reload();
                        require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, TASK,
                                "获得暗星", "获取目标物品。", PREFIX + "_g1", "", "world", "minecraft:stone", 1,
                                "minecraft:paper", "", "minecraft:diamond", 1, "manual", "world_view")).successful(), "Template create");
                        templateHash = QuestTemplateStore.fingerprint("world_view");
                        var quest = QuestRepository.find(TASK);
                        require(QuestTemplateStore.presentation(quest).controls().size() > 5, "Template geometry missing");
                        QuestProgressData.get(player.getServer()).complete(player.getUUID(), QuestRepository.find(PREFIX + "_119"));
                        xtdpotato.xero_quest_journal.core.network.QuestClientInbox.accept(
                                new xtdpotato.xero_quest_journal.core.network.QuestNetwork.ProgressSnapshotPayload(
                                        QuestRuntime.snapshot(player), List.of()));
                    } catch (Exception e) { throw new RuntimeException(e); }
                }, game.getSingleplayerServer()); wait = 5;
            }
            case 2 -> {
                if (ClientQuestDefinitions.find(TASK) == null) return false;
                journal = QuestScreen.task(null, TASK); set(journal, "editMode", true); game.setScreen(journal);
                var searchTool = button(journal, "search-settings");
                require(searchTool.getWidth() == 17 && searchTool.getHeight() == 15
                                && Math.abs((float) field(searchTool, "toolIconScale") - .75F) < .001F,
                        "Search/filter icon button was not reduced by 25 percent");
                var layout = (LayoutEditorState) field(journal, "layout");
                require((boolean) field(journal, "showEditorGrid"), "Editor grid should be enabled by default");
                button(journal, "editor-grid").onPress();
                require(!(boolean) field(journal, "showEditorGrid"), "Grid toolbar action did not hide the grid");
                button(journal, "editor-grid").onPress();
                require((boolean) field(journal, "showEditorGrid"), "Grid toolbar action did not restore the grid");
                require((int) call(journal, "rewardFooterHeight") == 0, "Template already has rewards but author footer duplicated");
                before = controls(layout);
                var line = layout.elements().values().stream().filter(e -> e.quest().equals(TASK)
                        && (e.kind().equals("divider") || e.kind().equals("line"))).findFirst();
                require(!before.isEmpty(), "No controls");
                call(journal, "init");
                require(controls(layout).equals(before), "Reinitializing template reset its geometry");
                call(journal, "saveLayout");
                wait = 8;
            }
            case 3 -> {
                if ((boolean) call(field(journal, "presentationWriter"), "busy")) return false;
                capture(game, "task-editor-grid-visible-zh-1920");
                var quest = QuestRepository.find(TASK);
                require(quest.presentation().authored() && !quest.presentation().controls().isEmpty(), "Saved presentation empty");
                require(QuestTemplateStore.fingerprint("world_view").equals(templateHash), "Editing task changed the shared template");
                invoke(journal, "editProperties", new Class<?>[]{String.class,String.class}, "task", TASK);
                var form = field(journal, "form");
                set(form, "name", "暗星任务已编辑"); set(form, "description", "修改的说明");
                call(journal, "saveForm"); wait = 8;
            }
            case 4 -> {
                if (!ClientQuestDefinitions.find(TASK).title().equals("暗星任务已编辑")) return false;
                journal = QuestScreen.task(null, TASK); set(journal, "editMode", false); game.setScreen(journal);
                var layout = (LayoutEditorState) field(journal, "layout");
                require(controls(layout).equals(before), "Reopening edited task lost layout controls or lines");
                require((int) call(journal, "rewardFooterHeight") == 0, "Runtime rewards duplicated");
                var objective = layout.elements().values().stream().filter(e -> e.quest().equals(TASK) && e.kind().equals("quest_objective"))
                        .findFirst().orElseThrow();
                int[] point = (int[]) invoke(journal, "elementDisplayPosition", new Class<?>[]{LayoutEditorState.Element.class}, objective);
                hoverX = point[0] + 5; hoverY = point[1] + 5;
                cursor(game, hoverX, hoverY);
                hoverAt = System.nanoTime(); wait = 4;
            }
            case 5 -> {
                var graphics = new GuiGraphics(game, game.renderBuffers().bufferSource());
                var layout = (LayoutEditorState) field(journal, "layout");
                var objective = layout.elements().values().stream().filter(e -> e.quest().equals(TASK)
                        && e.kind().equals("quest_objective")).findFirst().orElseThrow();
                int[] point = (int[]) invoke(journal, "elementDisplayPosition",
                        new Class<?>[]{LayoutEditorState.Element.class}, objective);
                hoverX = point[0] + 8; hoverY = point[1] + 8;
                capture(game, "template-objective-hover-zh-1920");
                try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    double dpi = game.getWindow().getGuiScale();
                    int white = 0;
                    for (int px = (int)(point[0] * dpi); px < Math.min(image.getWidth(), (point[0] + 100) * dpi); px++)
                        for (int py = Math.max(0, (int)(point[1] * dpi) - 1); py <= Math.min(image.getHeight() - 1, (point[1] + 1) * dpi); py++)
                            if ((image.getPixelRGBA(px, py) & 0x00FFFFFF) == 0x00FFFFFF) white++;
                    require(white >= 20, "White hover edge missing at " + point[0] + "," + point[1]
                            + "; hover " + hoverX + "," + hoverY);
                }
                graph = new QuestUnlockGraphScreen(journal, TASK); game.setScreen(graph);
                var positions = positions();
                // Each fixture group occupies its own right-to-left row.
                var a = positions.get(PREFIX + "_000");
                var b = positions.get(PREFIX + "_001");
                require(a.y() == b.y() && a.x() > b.x(), "Default graph is not right-to-left within group");
                require(((List<?>) call(graph, "visibleTasks")).size() < 40, "Offscreen graph nodes were not culled");
                checkMinimapAspect();
                double pan = (double) field(graph, "panX");
                int x = (int) call(graph, "mapX") + (int) call(graph, "mapWidth") / 2;
                int y = (int) call(graph, "mapY") + (int) call(graph, "mapHeight") / 2;
                graph.mouseClicked(x, y, 0); graph.mouseDragged(x + 8, y + 3, 0, 8, 3);
                double[] viewport = (double[]) call(graph, "minimapViewport");
                require(Math.abs(viewport[0] + viewport[2] / 2 - x - 8) < .00001
                        && Math.abs(viewport[1] + viewport[3] / 2 - y - 3) < .00001,
                        "Minimap drag did not use the rendered projection");
                graph.mouseReleased(x + 8, y + 3, 0);
                require((double) field(graph, "panX") != pan, "Minimap did not move canvas");
                checkMinimapAspect();
                invoke(graph, "selectTask", new Class<?>[]{String.class}, TASK);
                ((StyledChoiceButton<String>) button(graph, "unlock-prerequisite-mode")).select("any");
                require(((Map<?, ?>) field(graph, "modes")).get(TASK).equals("any"), "Any mode selection failed");
                button(graph, "unlock-undo").onPress();
                require(((Map<?, ?>) field(graph, "modes")).get(TASK).equals("all"), "Mode undo failed");
                button(graph, "unlock-redo").onPress();
                invoke(graph, "connect", new Class<?>[]{String.class,String.class}, PREFIX + "_000", TASK);
                invoke(graph, "connect", new Class<?>[]{String.class,String.class}, PREFIX + "_060", TASK);
                var focus = positions().get(TASK);
                set(graph, "zoom", .8);
                set(graph, "panX", graph.width / 2. - (focus.x() + 95) * .8);
                set(graph, "panY", ((int) call(graph, "top") + (int) call(graph, "bottom")) / 2. - (focus.y() + 46) * .8);
                checkMinimapAspect();
                checkMinimapTasks();
                button(graph, "unlock-minimap-overview").onPress();
                require((boolean) field(graph, "minimapOverview"), "Minimap overview button did not activate");
                Object overviewProjection = call(graph, "minimapProjection");
                invoke(graph, "rasterizeMinimap", new Class<?>[]{overviewProjection.getClass()}, overviewProjection);
                require(Arrays.stream((int[]) field(graph, "minimapOffscreen")).sum() == 0,
                        "Show-all minimap omitted tasks");
                button(graph, "unlock-minimap-overview").onPress();
                wait = 4;
            }
            case 6 -> {
                if (Boolean.getBoolean("xero.unlockGraphPerf") && !UnlockGraphPerformance.tick(game, graph)) return false;
                capture(game, "unlock-group-rows-minimap-zh-1920");
                double previousPanX = (double) field(graph, "panX"), previousPanY = (double) field(graph, "panY");
                set(graph, "panX", 0.); set(graph, "panY", 0.); call(graph, "init");
                require((double) field(graph, "panX") == 0. && (double) field(graph, "panY") == 0.,
                        "Rebuilding widgets at zero offset auto-moved the canvas");
                set(graph, "panX", previousPanX); set(graph, "panY", previousPanY);
                button(graph, "unlock-save").onPress(); wait = 8;
            }
            case 7 -> {
                if (game.screen == graph) return false;
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    var quest = QuestRepository.find(TASK); var data = new QuestProgressData();
                    require(quest.completionPolicy().prerequisiteMode().equals("any"), "Mode did not persist");
                    require(!QuestRuntime.unlocked(data, player, quest), "Any unlocked without predecessors");
                    data.complete(player.getUUID(), QuestRepository.find(PREFIX + "_000"));
                    require(QuestRuntime.unlocked(data, player, quest), "Any did not unlock after one predecessor");
                    try {
                        String source = QuestDocuments.completionPolicy(QuestRepository.source(TASK),
                                quest.completionPolicy().withPrerequisiteMode("all"));
                        var all = QuestRepository.parseDefinition(QuestRepository.fileOf(TASK), source);
                        require(!QuestRuntime.unlocked(data, player, all), "All unlocked after one predecessor");
                        data.complete(player.getUUID(), QuestRepository.find(PREFIX + "_060"));
                        require(QuestRuntime.unlocked(data, player, all), "All did not unlock after both");
                    } catch (Exception e) { throw new RuntimeException(e); }
                }, game.getSingleplayerServer());
                game.setScreen(new QuestSearchSettingsScreen(journal)); wait = 4;
            }
            case 8 -> {
                require(game.screen instanceof QuestSearchSettingsScreen && game.screen instanceof QuestSearchFilterScreen, "Search/filter not unified");
                require(game.screen.children().stream().filter(Checkbox.class::isInstance).count() == 4, "Scopes missing");
                require((double) field(field(game.screen, "scroll"), "maximum") > 0, "No smooth scroll");
                var taskSearch = (EditBox) field(game.screen, "taskSearch");
                taskSearch.setValue("暗星");
                var filter = (EditBox) field(game.screen, "search"); filter.setValue("物品");
                wait = 4;
            }
            case 9 -> {
                capture(game, "combined-search-filters-zh-1920");
                resize(game, 854, 480); wait = 4;
            }
            case 10 -> {
                capture(game, "combined-search-filters-compact");
                require(button(game.screen, "search-filter-apply").getBottom() < game.screen.height, "Footer offscreen");
                button(game.screen, "search-filter-apply").onPress(); wait = 5;
            }
            case 11 -> {
                require(game.screen == journal, "Combined close did not restore journal");
                resize(game, 1920, 1080);
                game.getLanguageManager().setSelected("en_us"); game.options.languageCode = "en_us";
                pending = game.reloadResourcePacks();
            }
            case 12 -> {
                game.setScreen(new QuestSearchSettingsScreen(journal));
                ((EditBox) field(game.screen, "search")).setValue("item"); wait = 4;
            }
            case 13 -> {
                capture(game, "combined-search-filters-en-1920");
                ClientPreferences.get().questSearchFilters = oldFilters; ClientPreferences.get().questSearchScopes = oldScopes;
                game.setScreen(null);
                wait = 4;
            }
        }
        stage++; nextFrame = ClientSmokeTest.renderedFrames + 2; return false;
    }
    private static List<QuestPresentation.Control> controls(LayoutEditorState layout) throws Exception {
        Class<?> type = Class.forName("xtdpotato.xero_quest_journal.client.QuestPresentationClient");
        var method = type.getDeclaredMethod("snapshot", String.class, LayoutEditorState.class); method.setAccessible(true);
        return ((QuestPresentation) method.invoke(null, TASK, layout)).controls();
    }
    private static Map<String, QuestUnlockLayout.Position> positions() throws Exception { return (Map<String, QuestUnlockLayout.Position>) field(graph,"positions"); }
    private static void checkMinimapAspect() throws Exception {
        double[] viewport = (double[]) call(graph, "minimapViewport");
        double expected = (graph.width - 12. - (int) call(graph, "canvasLeft"))
                / ((int) call(graph, "bottom") - (int) call(graph, "top"));
        require(Math.abs(viewport[2] / viewport[3] - expected) < .00001,
                "Minimap viewport aspect differs from visible canvas");
        if (!(boolean) field(graph, "minimapDragging")) {
            double originalZoom = (double) field(graph, "zoom");
            var beforeProjection = call(graph, "minimapProjection");
            try {
                set(graph, "zoom", originalZoom * .5);
                var afterProjection = call(graph, "minimapProjection");
                require(Math.abs((double) field(afterProjection, "scale") / (double) field(beforeProjection, "scale") - .5) < .00001,
                        "Minimap zoom does not follow canvas zoom");
            } finally { set(graph, "zoom", originalZoom); }
        }
    }
    private static void checkMinimapTasks() throws Exception {
        call(graph, "refreshMinimapStates");
        var dots = (List<?>) field(graph, "minimapDots");
        var ids = new HashSet<String>();
        for (var dot : dots) {
            String id = (String) field(dot, "id"); ids.add(id);
            var view = ClientQuestProgress.view(id);
            String expected = view != null && view.completed() ? "COMPLETE"
                    : view != null && !view.unlocked() ? "LOCKED" : "AVAILABLE";
            require(field(dot, "state").toString().equals(expected), "Minimap state differs from task " + id);
        }
        require(ids.equals(positions().keySet()) && dots.size() == positions().size(), "Minimap dropped task markers");
        int[] counts = (int[]) field(graph, "minimapCounts");
        require(counts[0] > 0 && counts[1] > 0 && counts[2] > 0, "Minimap lacks available/complete/locked states");
        var original = Map.copyOf(positions());
        double oldPanX = (double) field(graph, "panX"), oldPanY = (double) field(graph, "panY");
        try {
            var overlap = positions().get(PREFIX + "_000");
            positions().put(PREFIX + "_001", overlap); positions().put(PREFIX + "_119", overlap);
            call(graph, "rebuildViewportIndexes");
            double currentZoom = (double) field(graph, "zoom");
            set(graph, "panX", graph.width / 2. - (overlap.x() + 95) * currentZoom);
            set(graph, "panY", graph.height / 2. - (overlap.y() + 46) * currentZoom);
            Object projection = call(graph, "minimapProjection");
            invoke(graph, "rasterizeMinimap", new Class<?>[]{projection.getClass()}, projection);
            int[] masks = (int[]) field(graph, "minimapRasterMasks");
            require(Arrays.stream(masks).anyMatch(mask -> mask == 7), "Overlapping task states were discarded");
        } finally {
            set(graph, "panX", oldPanX); set(graph, "panY", oldPanY);
            positions().clear(); positions().putAll(original); call(graph, "rebuildViewportIndexes");
        }
    }
    private static Field property(Object target,String name) throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) {
            try{var field=type.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        }throw new NoSuchFieldException(name);
    }
    private static Object field(Object target,String name)throws Exception{return property(target,name).get(target);}
    private static void set(Object target,String name,Object value)throws Exception{property(target,name).set(target,value);}
    private static Object call(Object target,String name)throws Exception{return invoke(target,name,new Class<?>[0]);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()){
            try{var method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
            catch(NoSuchMethodException ignored){}
        }throw new NoSuchMethodException(name);
    }
    private static StyledButton button(net.minecraft.client.gui.screens.Screen screen,String id){
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button->button.styleId().equals(id)).findFirst().orElseThrow(()->new AssertionError("Missing "+id));
    }
    private static void resize(Minecraft game,int w,int h){
        game.options.guiScale().set(2);game.getWindow().setWindowed(w,h);game.getWindow().setWidth(w);game.getWindow().setHeight(h);game.resizeDisplay();
    }
    private static void cursor(Minecraft game, double x, double y) throws Exception {
        double windowX = x * game.getWindow().getScreenWidth() / game.screen.width;
        double windowY = y * game.getWindow().getScreenHeight() / game.screen.height;
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), windowX, windowY);
        set(game.mouseHandler, "xpos", windowX); set(game.mouseHandler, "ypos", windowY);
    }
    private static void capture(Minecraft game,String name)throws Exception{
        Files.createDirectories(OUTPUT);try(var image=Screenshot.takeScreenshot(game.getMainRenderTarget())){image.writeToFile(OUTPUT.resolve(name+".png"));}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message+" at workflow stage "+stage);}
}
