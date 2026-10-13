package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class OverviewAuthoringRegression {
    private static final String P = "author_" + System.currentTimeMillis();
    private static final String GROUP = P + "_group", OTHER = P + "_other";
    private static final String FIRST = P + "_first", LAST = P + "_last", LOCKED = P + "_locked";
    private static final String TOP = P + "_top", BOTTOM = P + "_bottom";
    private static final String ABOVE = P + "_above", BELOW = P + "_below", SOLO = P + "_solo";
    private static int stage, wait;
    private static long nextFrame;
    private static CompletableFuture<Void> work;
    private static QuestScreen screen;
    private static List<String> runtimeGroups;

    static boolean tick(Minecraft game) throws Exception {
        if (ClientSmokeTest.renderedFrames < nextFrame) return false;
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                QuestConfig.defaults().taskPagination = false;
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    try { fixtures(server.getPlayerList().getPlayer(game.player.getUUID())); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, server);
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                screen = QuestScreen.overview(null);
                game.setScreen(screen);
                click(screen, "edit");
                require(!(boolean) field(screen, "editMode"), "Pen entered editor without showing its menu");
                require(menuIds().equals(List.of("context-overview_edit.all_tasks",
                        "context-overview_background.edit")), "Overview edit menu entries are incorrect");
            }
            case 2 -> {
                capture(game, "overview-authoring-edit-menu");
                menuClick("overview_edit.all_tasks");
                require((boolean) field(screen, "editMode") && (boolean) field(screen, "overviewMode"),
                        "All-task editor did not open within the overview");
                require(field(screen, "groupId").equals("") && field(screen, "chapterId").equals(""),
                        "All-task editor retained a scope filter");
                require(taskIds().equals(ClientQuestDefinitions.all().stream()
                        .map(QuestDefinition::id).collect(Collectors.toSet())), "Editor omitted hidden/locked/completed tasks");
                require(taskIds().containsAll(Set.of(FIRST, LAST, LOCKED)), "Fixture tasks missing");
                click(screen, "add-task");
                require(menuIds().equals(List.of("context-add_group", "context-add_task")), "Global add menu differs");
                menuClick("add_group");
                require(field(field(screen, "form"), "kind").equals("group")
                        && field(field(screen, "form"), "targetGroup").equals(""), "Global group was nested");
                click(screen, "cancel");
                click(screen, "add-task");
                menuClick("add_task");
                require(game.screen instanceof QuestTemplateScreen, "Global task did not open template chooser");
                click(game.screen, "template-close");
                require(game.screen == screen && field(screen, "form") == null, "Template cancellation created a form");
                groupPlus();
                require(menuIds().equals(List.of("context-group_add.task_bottom", "context-group_add.task_top",
                        "context-group_add.group_bottom", "context-group_add.group_top")),
                        "Group plus did not expose the four insertion choices");
            }
            case 3 -> {
                capture(game, "overview-authoring-group-menu");
                menuClick("group_add.task_top");
                require(game.screen instanceof QuestTemplateScreen, "Top task did not open template chooser");
                chooseTemplate(game);
                require(((QuestJournalOrderStore.Placement) field(field(screen, "form"), "placement"))
                        .kind().equals("task_top"), "Task-top insertion intent was lost");
                saveCreation(TOP, "New First Task");
            }
            case 4 -> {
                if (!creationDone()) return false;
                require(groupTasks().getFirst().equals(TOP), "New task was not at the top of its group");
                require(field(screen, "selectedId").equals(TOP), "New task was not selected");
                groupPlus();
                menuClick("group_add.task_bottom");
                chooseTemplate(game);
                saveCreation(BOTTOM, "New Last Task");
            }
            case 5 -> {
                if (!creationDone()) return false;
                require(groupTasks().getLast().equals(BOTTOM), "New task was not at the bottom of its group");
                groupPlus();
                menuClick("group_add.group_top");
                require(field(field(screen, "form"), "kind").equals("group")
                        && field(field(screen, "form"), "type").equals("main"), "Sibling group type is incorrect");
                saveCreation(ABOVE, "Group Above");
            }
            case 6 -> {
                if (!creationDone()) return false;
                require(adjacent(ABOVE, GROUP), "New group was not immediately above the current group");
                require(field(screen, "groupId").equals(""), "Group creation filtered out other groups");
                groupPlus();
                menuClick("group_add.group_bottom");
                saveCreation(BELOW, "Group Below");
            }
            case 7 -> {
                if (!creationDone()) return false;
                require(adjacent(GROUP, BELOW), "New group was not immediately below the current group");
                groupPlus();
                menuClick("group_add.task_top");
                chooseTemplate(game);
                click(screen, "cancel");
                click(screen, "add-task");
                menuClick("add_task");
                chooseTemplate(game);
                require(field(field(screen, "form"), "placement") == null,
                        "Cancelled task insertion leaked into the next global creation");
                saveCreation(SOLO, "Standalone Task");
            }
            case 8 -> {
                if (!creationDone()) return false;
                require(QuestRepository.groupOf(SOLO).isBlank(), "Global task was inserted into a prior group");
                require(groupTasks().getFirst().equals(TOP) && groupTasks().getLast().equals(BOTTOM),
                        "Unrelated task creation reset saved order");
                capture(game, "overview-authoring-all-tasks");
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    try { serverChecks(server.getPlayerList().getPlayer(game.player.getUUID())); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }, server);
            }
            case 9 -> {
                if (!work.isDone()) return false;
                work.join();
                game.setScreen(screen);
                require(groupTasks().getFirst().equals(TOP) && groupTasks().getLast().equals(BOTTOM)
                        && adjacent(ABOVE, GROUP) && adjacent(GROUP, BELOW), "Reopen lost insertion order");
                resize(game, 640, 480);
                click(screen, "add-task");
                checkMenuBounds();
            }
            case 10 -> {
                capture(game, "overview-authoring-add-small");
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                groupPlus();
                checkMenuBounds();
            }
            case 11 -> {
                capture(game, "overview-authoring-group-small");
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            case 12 -> {
                ClientPreferences.get().reducedMotion = false;
                groupPlus();
                wait = 1;
            }
            case 13 -> {
                require(screen.menuAnimationPosition("context") > 0 && screen.menuAnimationPosition("context") < 1,
                        "New menu skipped scale/fade animation");
                capture(game, "overview-authoring-menu-opening");
                wait = 14;
            }
            case 14 -> {
                require(!screen.inputBlocked(), "Opened menu did not release transition input");
                screen.mouseClicked(screen.width - 2, screen.height - 2, 0);
                require(menuIds().isEmpty(), "Outside click did not clear menu");
                wait = 1;
            }
            case 15 -> {
                require(screen.menuAnimationPosition("context") > 0 && screen.menuAnimationPosition("context") < 1,
                        "Dismissed menu skipped shrink/fade animation");
                capture(game, "overview-authoring-menu-closing");
                wait = 14;
            }
            case 16 -> {
                require(screen.menuAnimationPosition("context") == 0 && !screen.inputBlocked(),
                        "Closed menu left a blocking layer");
                ClientPreferences.get().reducedMotion = true;
                click(screen, "edit");
                require(menuIds().contains("context-overview_edit.all_tasks"), "Editor pen did not expose menu");
                menuClick("preview");
                require(!(boolean) field(screen, "editMode"), "Preview menu could not exit editing");
                game.player.setPermissionLevel(0);
                screen.tick();
                require(screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).noneMatch(b -> b.styleId().equals("edit")),
                        "Overview editing leaked to a non-author");
                return true;
            }
            default -> throw new AssertionError("Unknown overview authoring stage " + stage);
        }
        stage++;
        nextFrame = ClientSmokeTest.renderedFrames + 2;
        if (wait <= 0) wait = 5;
        return false;
    }

    private static void fixtures(ServerPlayer player) throws Exception {
        Files.createDirectories(QuestRepository.root());
        var first = QuestRepository.root().resolve(P + "_folder");
        var other = QuestRepository.root().resolve(P + "_other_folder");
        Files.createDirectories(first); Files.createDirectories(other);
        Files.writeString(first.resolve("group_manifest.json"),
                "{\"id\":\"" + GROUP + "\",\"name\":\"Insertion Group\",\"type\":\"main\"}");
        Files.writeString(other.resolve("group_manifest.json"),
                "{\"id\":\"" + OTHER + "\",\"name\":\"Other Group\",\"type\":\"world\"}");
        for (String id : List.of(FIRST, LAST, LOCKED)) {
            Files.writeString(first.resolve(id + ".json"), "{\"id\":\"" + id + "\",\"title\":\"" + id
                    + "\",\"type\":\"main\",\"hidden\":" + id.equals(LAST)
                    + ",\"defaultUnlocked\":" + !id.equals(LOCKED)
                    + ",\"triggers\":[{\"type\":\"confirm\",\"item\":\"\",\"count\":1}]}");
        }
        QuestRepository.reload();
        require(QuestRepository.errors().isEmpty(), "Invalid authoring fixtures");
        require(QuestRuntime.trigger(player, FIRST, "complete", false).equals("completed"), "Fixture completion failed");
        player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, true);
        runtimeGroups = QuestRepository.groups().stream().map(QuestRepository.Group::id).toList();
        require(new Gson().fromJson("{\"tasks\":[],\"tabs\":[]}", QuestJournalOrder.class).groups().isEmpty(),
                "Older order JSON without groups failed to load");
    }

    private static void serverChecks(ServerPlayer player) throws Exception {
        var order = QuestRepository.journalOrder();
        QuestRepository.reload();
        require(QuestRepository.errors().isEmpty() && QuestRepository.journalOrder().equals(order),
                "Disk reload did not persist insertion order");
        require(QuestCatalog.decode(QuestCatalog.serverJson()).journalOrder().equals(order), "Catalog lost group order");
        require(QuestRepository.groupOf(TOP).equals(GROUP) && QuestRepository.groupOf(BOTTOM).equals(GROUP),
                "Inserted tasks changed group membership");
        require(QuestRepository.groups().stream().filter(g -> g.id().equals(ABOVE) || g.id().equals(BELOW))
                .allMatch(g -> g.directory().getParent().equals(QuestRepository.root())),
                "Sibling insertion created nested group folders");
        require(QuestRepository.groups().stream().map(QuestRepository.Group::id).filter(runtimeGroups::contains)
                .toList().equals(runtimeGroups), "Display order changed runtime group enumeration");
        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, P + "_invalid", "Invalid", "",
                GROUP, "", "main", "minecraft:oak_log", 1);
        require(!QuestAuthoring.create(player, request,
                        new QuestJournalOrderStore.Placement("task_top", P + "_missing")).successful()
                        && QuestRepository.find(request.id()) == null, "Missing anchor created a task");
        player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, false);
        require(!QuestAuthoring.create(player, request,
                        new QuestJournalOrderStore.Placement("task_top", GROUP)).successful(),
                "Server accepted insertion while editing was disabled");
        player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, true);
    }

    private static void saveCreation(String id, String name) throws Exception {
        var form = field(screen, "form");
        set(form, "id", id); set(form, "name", name);
        click(screen, "create");
        require(field(screen, "pending") != null, "Creation did not dispatch");
    }

    private static boolean creationDone() throws Exception {
        screen.tick();
        if (field(screen, "pending") != null) return false;
        require(field(screen, "form") == null, "Creation failed: " + field(screen, "error"));
        return true;
    }

    private static void chooseTemplate(Minecraft game) {
        require(game.screen instanceof QuestTemplateScreen, "Template chooser absent");
        button(game.screen, "template-use").onPress();
        require(game.screen == screen, "Template did not return to parent");
    }

    private static void groupPlus() throws Exception {
        var row = button(screen, "group-" + GROUP);
        if (!row.visible) {
            int offset = ((JournalListModel) field(screen, "journalList")).rows().stream()
                    .filter(r -> r.key().equals("group:" + GROUP)).findFirst().orElseThrow().offset();
            set(screen, "listScroll", (double) offset);
            set(screen, "listTargetScroll", (double) offset);
            var method = QuestScreen.class.getDeclaredMethod("updateListButtons");
            method.setAccessible(true); method.invoke(screen);
        }
        double x = row.getX() + 12, y = row.getY() + row.getHeight() / 2.;
        screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
        require(!menuIds().isEmpty(), "Group plus did not open menu");
    }

    @SuppressWarnings("unchecked")
    private static List<StyledButton> menu() throws Exception { return (List<StyledButton>) field(screen, "menu"); }
    private static List<String> menuIds() throws Exception { return menu().stream().map(StyledButton::styleId).toList(); }
    private static void menuClick(String id) throws Exception {
        var item = menu().stream().filter(b -> b.styleId().equals("context-" + id)).findFirst().orElseThrow();
        click(screen, item);
    }
    private static Set<String> taskIds() throws Exception {
        return ((JournalListModel) field(screen, "journalList")).rows().stream().filter(r -> r.quest() != null)
                .map(r -> r.quest().id()).collect(Collectors.toSet());
    }
    private static List<String> groupTasks() throws Exception {
        return ((JournalListModel) field(screen, "journalList")).rows().stream().filter(r -> r.quest() != null
                && ClientQuestDefinitions.groupOf(r.quest().id()).equals(GROUP)).map(r -> r.quest().id()).toList();
    }
    private static boolean adjacent(String first, String second) {
        var groups = ClientQuestDefinitions.groups().stream().map(QuestCatalog.Group::id).toList();
        return groups.indexOf(first) + 1 == groups.indexOf(second);
    }
    private static void checkMenuBounds() throws Exception {
        for (var item : menu()) require(item.getX() >= 0 && item.getY() >= 0
                && item.getRight() <= screen.width && item.getBottom() <= screen.height, "Menu exceeds compact viewport");
    }
    private static StyledButton button(Screen target, String id) {
        return target.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing " + id));
    }
    private static void click(Screen target, String id) { click(target, button(target, id)); }
    private static void click(Screen target, StyledButton b) {
        double x = b.getX() + b.getWidth() / 2., y = b.getY() + b.getHeight() / 2.;
        target.mouseClicked(x, y, 0); target.mouseReleased(x, y, 0);
    }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static void resize(Minecraft game, int w, int h) {
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), w, h); game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        var root = Path.of("..", "build", "verification");
        Files.createDirectories(root);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            int colored = 0;
            for (int y = 0; y < image.getHeight(); y += 20)
                for (int x = 0; x < image.getWidth(); x += 20)
                    if ((image.getPixelRGBA(x, y) & 0xFFFFFF) != 0) colored++;
            require(colored > 10, "Screenshot was blank: " + name);
            image.writeToFile(root.resolve(name + ".png"));
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
