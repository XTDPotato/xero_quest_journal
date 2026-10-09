package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class TemplateInteractionRegression {
    private static final String PREFIX = "template_actions_" + System.currentTimeMillis();
    private static final Gson JSON = new Gson();
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static int stage, wait;
    private static CompletableFuture<Void> pending;
    private static QuestScreen journal;
    private static Screen controls;
    private static List<QuestControlTarget> picked;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                game.options.guiScale().set(2);
                resize(game, 1920, 1080);
                verifyButtons(game);
                verifyButtonShapes();
                verifyNoticeMigration();
                MailArrivalToast.clear();
                MailArrivalToast.enqueue(new QuestNetwork.MailboxNoticePayload(PREFIX + "_notice", "", ""));
                pending = CompletableFuture.runAsync(() -> verifyServer(game), game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone()) return false;
                pending.join();
                journal = new QuestScreen();
                game.setScreen(journal);
                ClientPreferences.get().lastTaskTemplate = "main_view";
                game.setScreen(new QuestTemplateScreen(journal, ""));
            }
            case 2 -> {
                var chooser = game.screen;
                require(field(chooser, "activeId").equals("main_view"), "Remembered template was not used");
                if (!MailArrivalToast.visible()) return false;
                int left = (int) staticField(MailArrivalToast.class, "left");
                int top = (int) staticField(MailArrivalToast.class, "top");
                int right = (int) staticField(MailArrivalToast.class, "right");
                int bottom = (int) staticField(MailArrivalToast.class, "bottom");
                require(right - left <= game.getWindow().getGuiScaledWidth() / 3
                                && bottom - top <= 34,
                        "Default new-mail notice does not fit the reference banner size");
                double noticeX = (left + right) / 2.0, noticeY = (top + bottom) / 2.0;
                require(MailArrivalToast.click(noticeX, noticeY), "Notice press was not handled");
                require(game.screen == chooser && MailArrivalToast.visible(), "Notice opened on press");
                require(MailArrivalToast.release(noticeX, top - 2), "Outside notice release not handled");
                require(game.screen == chooser && MailArrivalToast.visible(), "Outside release opened the notice");
                require(MailArrivalToast.click(noticeX, noticeY), "Notice second press was not handled");
                require(MailArrivalToast.release(noticeX, noticeY), "Notice in-bounds release was not handled");
                require(game.screen instanceof MailboxScreen, "Notice did not open on matching release");
                MailArrivalToast.clear();
                game.setScreen(chooser);
                capture(game, "template-default-preview-desktop");
                var header = button(chooser, "template-group-main");
                chooser.mouseClicked(header.getX() + 30, header.getY() + 12, 0);
                require(chooser.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .anyMatch(b -> b.styleId().equals("template-main_view")), "Group collapsed on press");
                chooser.mouseReleased(header.getX() + 30, header.getY() + 12, 0);
                require(chooser.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .noneMatch(b -> b.styleId().equals("template-main_view")), "Group did not collapse on release");
                ((EditBox) chooser.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow())
                        .setValue("world_submit");
                click(game, button(chooser, "template-world_submit"));
                require(ClientPreferences.get().lastTaskTemplate.equals("world_submit"), "Last choice not persisted");
            }
            case 3 -> {
                capture(game, "template-submit-preview-desktop");
                game.setScreen(new QuestTemplateScreen(journal, ""));
                require(field(game.screen, "activeId").equals("world_submit"), "Reopening reset last choice");
                game.setScreen(new QuestTemplateEditorScreen(journal, template("main_view")));
                click(game, button(game.screen, "template-controls"));
                controls = game.screen;
            }
            case 4 -> {
                require(field(controls, "preview") != null, "Shared control editor has no task canvas");
                capture(game, "template-shared-controls-desktop");
                click(game, button(controls, "template-control-action"));
                var value = controls.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.getMessage().getString().equals(QuestTemplateEditorScreenLabel("element.value")))
                        .findFirst().orElseThrow();
                value.setValue("Confirmed preview");
            }
            case 5 -> {
                var preview = field(controls, "preview");
                var state = (LayoutEditorState) field(preview, "layout");
                require(state.elements().values().stream().anyMatch(e -> e.value().equals("Confirmed preview")),
                        "Shared control preview did not update");
                capture(game, "template-shared-controls-live");
                resize(game, 854, 480);
            }
            case 6 -> {
                capture(game, "template-shared-controls-compact");
                click(game, button(game.screen, "template-properties"));
            }
            case 7 -> {
                capture(game, "template-shared-properties-compact");
                resize(game, 1920, 1080);
                var graph = new TriggerGraph(List.of(
                        new TriggerGraph.Node("action", "set_property", 41, 17,
                                Map.of("property", "textColor", "value", "#ff0000"))), List.of());
                var editor = new QuestTriggerGraphScreen(journal, graph, PREFIX + "_target_a", "",
                        Map.of("shared", targetPresentation().controls().getFirst()), ignored -> {});
                game.setScreen(editor);
                set(editor, "selected", "action");
                @SuppressWarnings("unchecked") var selected = (Set<String>) field(editor, "selectedIds");
                selected.add("action");
                call(editor, "init");
                set(editor, "showGrid", true);
                double x = (double) call(editor, "sx", TriggerGraph.Node.class, graph.nodes().getFirst()) + 50;
                double y = (double) call(editor, "sy", TriggerGraph.Node.class, graph.nodes().getFirst()) + 18;
                editor.mouseClicked(x, y, 0);
                editor.mouseDragged(x + 13, y + 17, 0, 13, 17);
                editor.mouseReleased(x + 13, y + 17, 0);
                @SuppressWarnings("unchecked") var nodes = (List<TriggerGraph.Node>) field(editor, "nodes");
                require(nodes.getFirst().x() % 24 == 0 && nodes.getFirst().y() % 24 == 0,
                        "Graph left/top edges do not align with rendered grid");
                capture(game, "template-graph-grid-desktop");
                click(game, button(editor, "graph-picker.open"));
                require(game.screen instanceof QuestControlPickerScreen, "Visual control picker did not open");
            }
            case 8 -> {
                var picker = (QuestControlPickerScreen) game.screen;
                EditBox search = (EditBox) picker.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                search.setValue(PREFIX + "_target");
                click(game, button(picker, "control-picker-task-" + PREFIX + "_target_a"));
            }
            case 9 -> {
                var picker = (QuestControlPickerScreen) game.screen;
                var preview = field(picker, "preview");
                var state = (LayoutEditorState) field(preview, "layout");
                var control = state.elements().values().stream()
                        .filter(e -> e.id().equals("shared")).findFirst().orElseThrow();
                int[] pos = (int[]) call(preview, "elementDisplayPosition", LayoutEditorState.Element.class, control);
                picker.mouseClicked(pos[0] + 10, pos[1] + 8, 0);
                require(((Set<?>) field(picker, "selected")).isEmpty(), "Control picker selected on press");
                picker.mouseReleased(pos[0] + 10, pos[1] + 8, 0);
                click(game, button(picker, "control-picker-task-" + PREFIX + "_target_b"));
            }
            case 10 -> {
                var picker = (QuestControlPickerScreen) game.screen;
                var preview = field(picker, "preview");
                var state = (LayoutEditorState) field(preview, "layout");
                var control = state.elements().values().stream().filter(e -> e.id().equals("shared")).findFirst().orElseThrow();
                int[] pos = (int[]) call(preview, "elementDisplayPosition", LayoutEditorState.Element.class, control);
                picker.mouseClicked(pos[0] + 10, pos[1] + 8, 0);
                picker.mouseReleased(pos[0] + 10, pos[1] + 8, 0);
                require(((Set<?>) field(picker, "selected")).size() == 2, "Cross-task multi-select was lost");
                capture(game, "template-control-picker-desktop");
                click(game, button(picker, "control-picker-confirm"));
                require(game.screen instanceof QuestTriggerGraphScreen, "Confirmation did not return to graph");
                @SuppressWarnings("unchecked") var nodes = (List<TriggerGraph.Node>) field(game.screen, "nodes");
                picked = QuestControlTarget.decode(nodes.getFirst().get("controls", ""));
                require(picked.size() == 2 && picked.stream().map(QuestControlTarget::quest).distinct().count() == 2,
                        "Graph did not retain explicit multi-task targets");
                journal = QuestScreen.task(null, PREFIX + "_view");
                set(journal, "editMode", true);
                game.setScreen(journal);
            }
            case 11 -> {
                var state = (LayoutEditorState) field(journal, "layout");
                var method = QuestScreen.class.getDeclaredMethod("snapElementPosition",
                        String.class, int.class, int.class, int.class, int.class);
                method.setAccessible(true);
                int[] aligned = (int[]) method.invoke(journal, "unknown", 19, 29, 101, 27);
                require(aligned[0] == state.snap(19) && aligned[1] == state.snap(29),
                        "Task left/top snapping was overridden by center guides");
                var scale = QuestScreen.class.getDeclaredMethod("canvasContentScale");
                scale.setAccessible(true);
                require((double) scale.invoke(journal) >= 1.5,
                        "Full-size desktop task canvas did not scale to its available area");
                capture(game, "task-layout-fullscreen-desktop");
                Files.writeString(OUTPUT.resolve("template-interaction-result.txt"),
                    "PASS: 20 defaults, default deletion persistence, view press/release, exact item submission,"
                                + " event graph completion, editable scene completion, explicit cross-task property targets,"
                                + " button cancellation/disabled/keyboard/choice behavior, grouped templates,"
                                + " remembered choice, live shared task canvas, multi-task visual selection,"
                                + " 24-unit graph and task left/top snapping, full-size 1920x1080 task canvas,"
                                + " reference-size mail notice press/inside-release and outside-release cancellation,"
                                + " circular/pill/rounded hover outlines, notice migration with custom sizing preserved,"
                                + " desktop and compact screenshots.\n");
                return true;
            }
            default -> throw new AssertionError("Unknown interaction stage");
        }
        stage++; wait = 12; return false;
    }

    private static void verifyButtons(Minecraft game) {
        int[] calls = {0};
        var button = new StyledButton(20, 20, 80, 24, Component.literal("Test"), ignored -> calls[0]++,
                ButtonStyleSheet.load(), "release-test");
        require(button.mouseClicked(25, 25, 0) && calls[0] == 0, "Button executes on press");
        button.mouseReleased(10, 10, 0); require(calls[0] == 0, "Outside release executed button");
        button.mouseReleased(25, 25, 0); require(calls[0] == 0, "Release without press executed button");
        button.mouseClicked(25, 25, 0); button.mouseReleased(25, 25, 0);
        require(calls[0] == 1, "Inside release did not execute exactly once");
        button.mouseClicked(25, 25, 0); button.active = false; button.mouseReleased(25, 25, 0);
        require(calls[0] == 1, "Disabled held button executed");
        button.active = true; button.setFocused(true);
        button.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
        require(calls[0] == 1, "Keyboard activation occurred on press");
        button.keyReleased(GLFW.GLFW_KEY_SPACE, 0, 0); require(calls[0] == 2, "Keyboard release failed");
        var choice = new StyledChoiceButton<>(20, 20, 80, 24, Component.literal("Choice"), List.of("a", "b"),
                "a", Component::literal, ignored -> calls[0]++, ButtonStyleSheet.load(), "choice-test");
        choice.mouseClicked(25, 25, 0); require(!choice.isOpen(), "Choice opened on press");
        choice.mouseReleased(25, 25, 0); require(choice.isOpen(), "Choice failed to open on release");
    }

    private static void verifyButtonShapes() throws Exception {
        var pill = new JournalNavButton(0, 0, 120, 24, Component.literal("Pill"), ignored -> {},
                ButtonStyleSheet.load(), "shape-pill", JournalNavButton.Kind.ACTION, "choose");
        var circle = new EditorIconButton(0, 0, Component.literal("Circle"), () -> {},
                ButtonStyleSheet.load(), "shape-circle", "settings");
        var rounded = new JournalNavButton(0, 0, 64, 24, Component.literal("Key"), ignored -> {},
                ButtonStyleSheet.load(), "shape-rounded", JournalNavButton.Kind.KEY, "other");
        require(shape(pill).equals("pill") && shape(circle).equals("circle")
                        && shape(rounded).equals("rounded"),
                "Hover outline geometry does not match pill, circle and rounded button surfaces");
    }

    private static String shape(StyledButton button) throws Exception {
        for (Class<?> type = button.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var method = type.getDeclaredMethod("motionShape");
                method.setAccessible(true);
                return (String) method.invoke(button);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("motionShape");
    }

    private static void verifyNoticeMigration() throws Exception {
        String html = "<div class=\"mail-notice\" data-icon=\"mail_envelope\" data-text=\"\" data-background=\"\"></div>";
        String css = ".mail-notice { --background:#cc161a20; --border:#64666b; --text:#e9e7e2;"
                + " --width:500; --height:58; --text-scale:1.6; --duration:6000; }";
        var legacy = new MailboxAnimationStore.Config();
        legacy.events.put("notice", new MailboxAnimationStore.Animation(html, css, ""));
        var migrated = MailboxAnimationStore.copy(legacy);
        MailboxAnimationStore.preview(migrated);
        int background = MailboxAnimationStore.color("notice", "background", 0);
        int icon = MailboxAnimationStore.color("notice", "icon", 0);
        MailboxAnimationStore.clearPreview();
        require(migrated.noticeVersion == 4 && migrated.events.get("notice").css.contains("--width:250")
                        && migrated.events.get("notice").css.contains("--height:30")
                        && migrated.events.get("notice").css.contains("--background:#1d202680")
                        && migrated.events.get("notice").css.contains("--icon:#c8c8c880")
                        && background == 0x801D2026 && icon == 0x80C8C8C8,
                "Default notice did not migrate to the half-opacity reference style");
        var custom = new MailboxAnimationStore.Config();
        custom.events.put("notice", new MailboxAnimationStore.Animation(html,
                css.replace("--width:500", "--width:333"), ""));
        var preserved = MailboxAnimationStore.copy(custom);
        require(preserved.events.get("notice").css.contains("--width:333")
                        && preserved.events.get("notice").css.contains("--background:#1d202680"),
                "Notice migration overwrote custom sizing or retained the old palette");
    }

    private static void verifyServer(Minecraft game) {
        try {
            var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
            for (String type : List.of("main", "legendary", "commission", "world"))
                for (String mode : List.of("view", "travel", "submit", "structure", "event")) {
                    var template = template(type + "_" + mode);
                    require(template.group().equals(type) && !template.elements().isEmpty(), "Default template missing");
                    require(QuestConditions.valid(template.defaultConditions()), "Invalid default conditions");
                }
            var removed = template("world_event");
            require(QuestTemplateStore.delete(player, List.of("world_event")).isEmpty(), "Default template cannot be deleted");
            QuestTemplateStore.ensureDefaults(); QuestTemplateStore.refresh();
            require(QuestTemplateStore.all().stream().noneMatch(t -> t.id().equals("world_event")), "Deleted default resurrected");
            require(QuestTemplateStore.save(player, removed, "").isEmpty(), "Restore test default failed");
            for (String mode : List.of("view", "submit", "event", "structure", "travel")) {
                var template = template("main_" + mode);
                write(PREFIX + "_" + mode, Map.of("templateId", template.id(), "triggers", template.defaultConditions()));
            }
            for (String suffix : List.of("a", "b"))
                write(PREFIX + "_target_" + suffix, Map.of("presentation", targetPresentation()));
            var targets = List.of(new QuestControlTarget(PREFIX + "_target_a", "shared"),
                    new QuestControlTarget(PREFIX + "_target_b", "shared"));
            var graph = new TriggerGraph(List.of(
                    new TriggerGraph.Node("click", "event", 0, 0, Map.of("event", "click", "cooldown", "1")),
                    new TriggerGraph.Node("set", "set_property", 168, 0, Map.of(
                            "controls", QuestControlTarget.encode(targets), "property", "textColor", "value", "#ff0000"))),
                    List.of(new TriggerGraph.Edge("click", "set", "next")));
            write(PREFIX + "_gate", Map.of("presentation", new QuestPresentation(true, 395, 275, List.of(
                    new QuestPresentation.Control("gate", "button", "Set", 8, 8, 80, 24, "", Map.of(), graph)))));
            QuestRepository.reload();
            require(QuestRepository.errors().isEmpty(), "Invalid fixtures: " + QuestRepository.errors());
            var view = QuestRepository.find(PREFIX + "_view");
            var control = QuestTemplateStore.presentation(view).controls().stream().filter(c -> c.kind().equals("button"))
                    .findFirst().orElseThrow();
            long revision = QuestRepository.revision();
            QuestPointerEvents.input(player, view.id(), control.id(), revision, "press", 1, 1, 124, 26);
            require(!QuestRuntime.completed(player, view), "View completed on press");
            QuestPointerEvents.input(player, view.id(), control.id(), revision, "release", -1, 1, 124, 26);
            require(!QuestRuntime.completed(player, view), "View completed on outside release");
            QuestPointerEvents.input(player, view.id(), control.id(), revision, "press", 1, 1, 124, 26);
            QuestPointerEvents.input(player, view.id(), control.id(), revision, "release", 1, 1, 124, 26);
            require(QuestRuntime.completed(player, view), "Template view button has no server action");
            var inventory = player.getInventory().save(new net.minecraft.nbt.ListTag());
            player.getInventory().clearContent();
            try {
                require(!QuestRuntime.activate(player, PREFIX + "_submit").equals("completed"), "Empty submission completed");
                player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 20));
                require(QuestRuntime.activate(player, PREFIX + "_submit").equals("completed"), "Valid submission failed");
                require(player.getInventory().getItem(0).getCount() == 4, "Submission did not consume exactly 16");
            } finally { player.getInventory().load(inventory); }
            QuestEventGraphs.fire(player, "jump", "");
            require(QuestRuntime.completed(player, QuestRepository.find(PREFIX + "_event")), "Template jump graph did not complete");
            QuestEventGraphs.click(player, PREFIX + "_gate", "gate");
            for (var target : targets) require(QuestControlData.get(player).properties(player,
                    QuestRepository.find(target.quest()), target.control()).get("textColor").equals("#ff0000"),
                    "Cross-task control property did not apply");
            var sceneQuest = QuestRepository.find(PREFIX + "_structure");
            var entrance = QuestTemplateStore.presentation(sceneQuest).controls().stream()
                    .filter(c -> c.kind().equals("structure_enter")).findFirst().orElseThrow();
            try {
                QuestStructures.enter(player, sceneQuest, entrance);
                require(QuestStructures.active(player), "Default structure entrance failed");
                player.server.getLevel(QuestStructures.DIMENSION).setBlock(
                        QuestStructures.origin(player).offset(3, 1, 3), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                QuestSceneTriggers.tick(player);
                require(QuestRuntime.completed(player, sceneQuest), "Default structure challenge did not complete");
            } finally { if (QuestStructures.active(player)) QuestStructures.leave(player); }
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static void write(String id, Map<String, Object> extra) throws Exception {
        var data = new LinkedHashMap<String, Object>(Map.of("id", id, "title", id,
                "description", "Task canvas test", "type", "main", "defaultUnlocked", true,
                "triggers", List.of(new QuestDefinition.Trigger("event", "never", 1))));
        data.putAll(extra);
        Files.writeString(QuestRepository.root().resolve(id + ".json"), JSON.toJson(data));
    }
    private static QuestPresentation targetPresentation() {
        return new QuestPresentation(true, 395, 275, List.of(
                new QuestPresentation.Control("shared", "text", "Target task control", 16, 24, 200, 24,
                        "", Map.of(), TriggerGraph.empty())));
    }
    private static QuestTemplate template(String id) {
        return QuestTemplateStore.all().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }
    private static String QuestTemplateEditorScreenLabel(String key) {
        return Component.translatable("screen.xero_quest_journal.template." + key).getString();
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing button " + id));
    }
    private static void click(Minecraft game, StyledButton button) {
        require(button.active, "Inactive button: " + button.styleId());
        var screen = game.screen;
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(screen.mouseClicked(x, y, 0), "Press not handled: " + button.styleId());
        screen.mouseReleased(x, y, 0);
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static Object staticField(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object call(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target);
    }
    private static Object call(Object target, String name, Class<?> type, Object value) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, type); method.setAccessible(true); return method.invoke(target, value);
    }
    private static Object callStatic(Class<?> type, String name, Class<?> parameter, Object value) throws Exception {
        var method = type.getDeclaredMethod(name, parameter); method.setAccessible(true); return method.invoke(null, value);
    }
    private static void resize(Minecraft game, int w, int h) {
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), w, h); game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
