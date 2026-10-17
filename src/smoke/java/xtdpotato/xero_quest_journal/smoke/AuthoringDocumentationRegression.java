package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.*;

/** Real authoring workflows, evaluated examples and documentation photographs. */
final class AuthoringDocumentationRegression {
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final String ID = "docs_js_demo";
    private static final String[] TABS = {"general", "motion", "notifications", "appearance", "tasks", "tools"};
    private static int stage, wait, tabIndex;
    private static long tabStartedAt;
    private static CompletableFuture<?> pending;
    private static CompletableFuture<Void> language;
    private static QuestConfigScreen settings;
    private static QuestScreen journal;
    private static QuestTriggerGraphScreen graphEditor;
    private static QuestRewardsScreen rewardsEditor;
    private static String savedScript;
    private static TriggerGraph graph = graph("eventType === \"click\" && Number(experienceLevel) >= 10;", false);

    static boolean tick(Minecraft game) throws Exception {
        MailArrivalToast.clear();
        QuestUnlockToast.clear();
        XeroToast.clear();
        if (game.screen instanceof AnimatedQuestScreen animated && animated.inputBlocked()) return false;
        if (game.player == null || game.level == null || --wait > 0) return false;
        if (pending != null) {
            if (!pending.isDone()) return false;
            pending.join();
            pending = null;
        }
        switch (stage) {
            case 0 -> {
                Files.createDirectories(OUTPUT);
                game.options.guiScale().set(2);
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.resizeDisplay();
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                language = game.reloadResourcePacks();
                pending = CompletableFuture.runAsync(() -> {
                    try { setupAndCheck(game); } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
                wait = 30;
            }
            case 1 -> {
                if (!language.isDone() || game.getOverlay() != null) return false;
                language.join();
                ClientPreferences.get().reducedMotion = false;
                ClientPreferences.get().popupAnimationMillis = 100;
                ClientEditMode.set(false);
                settings = new QuestConfigScreen(null);
                game.setScreen(settings);
                require(game.screen == settings, "Personal settings require author mode");
                wait = 8;
            }
            case 2 -> {
                capture(game, "settings-general-zh-1920");
                ClientEditMode.set(true);
                tabStartedAt = System.nanoTime();
                button(settings, "config-tab-motion").onPress();
                require((double) call(field(settings, "tabMotion"), "position") == 0,
                        "Settings tab transition did not start");
                require((int) call(settings, "tabMillis") == 100, "Settings tab duration is not 100ms");
                wait = 5;
            }
            case 3 -> {
                if ((double) call(field(settings, "tabMotion"), "position") != 1
                        && System.nanoTime() - tabStartedAt < 500_000_000L) return false;
                require((double) call(field(settings, "tabMotion"), "position") == 1, "100ms tab did not settle");
                capture(game, "settings-motion-zh-1920");
                tabIndex = 2;
                button(settings, "config-tab-" + TABS[tabIndex]).onPress();
                wait = 5;
            }
            case 4 -> {
                if ((double) call(field(settings, "tabMotion"), "position") != 1) return false;
                capture(game, "settings-" + TABS[tabIndex] + "-zh-1920");
                if (++tabIndex < TABS.length) {
                    button(settings, "config-tab-" + TABS[tabIndex]).onPress();
                    wait = 5;
                    return false;
                }
                game.getWindow().setWindowed(854, 480);
                game.getWindow().setWidth(854);
                game.getWindow().setHeight(480);
                game.resizeDisplay();
                button(settings, "config-tab-general").onPress();
                wait = 6;
            }
            case 5 -> {
                var rail = settings.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                        .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                        .filter(widget -> widget.getClass().getSimpleName().equals("ScrollRail")).findFirst().orElseThrow();
                rail.setFocused(true);
                rail.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0);
                wait = 10;
            }
            case 6 -> {
                var done = button(settings, "done");
                require(settings.getChildAt(done.getX() + 3, done.getY() + 3).orElse(null) == done,
                        "Settings footer intercepted by scrolled body");
                var last = button(settings, "inventory-placement");
                require(last.getY() + last.getHeight() <= (int) call(settings, "bodyBottom"),
                        "Final personal setting is unreachable");
                desktop(game);
                journal = QuestScreen.task(null, ID);
                game.setScreen(journal);
                wait = 10;
            }
            case 7 -> {
                game.getToasts().clear();
                capture(game, "task-js-example-zh-1920");
                graphEditor = new QuestTriggerGraphScreen(journal, graph, ID, "button",
                        Map.of("button", control(graph)), ignored -> {});
                game.setScreen(graphEditor);
                selectPredicate();
                wait = 10;
            }
            case 8 -> {
                var mode = button(graphEditor, "graph-mode");
                var edit = graphEditor.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(widget -> widget.styleId().endsWith("edit_script"))
                        .findFirst().orElseThrow();
                require(mode.getY() + mode.getHeight() <= edit.getY(), "JS mode control covers script editor entry");
                capture(game, "js-predicate-graph-zh-1920");
                var type = Class.forName("xtdpotato.xero_quest_journal.client.QuestScriptEditorScreen");
                var constructor = type.getDeclaredConstructor(Screen.class, String.class, Consumer.class);
                constructor.setAccessible(true);
                savedScript = null;
                game.setScreen((Screen) constructor.newInstance(graphEditor,
                        "eventType === \"click\" && Number(experienceLevel) >= 10;",
                        (Consumer<String>) value -> savedScript = value));
                wait = 8;
            }
            case 9 -> {
                capture(game, "js-editor-reference-zh-1920");
                button(game.screen, "script-reference-examples").onPress();
                ((StyledChoiceButton<QuestScriptReference.Example>) button(game.screen, "script-example-select"))
                        .select(QuestScriptReference.examples(QuestScriptReference.Kind.GRAPH_PREDICATE).stream()
                                .filter(example -> example.id().equals("function_graph")).findFirst().orElseThrow());
                wait = 6;
            }
            case 10 -> {
                capture(game, "js-editor-examples-zh-1920");
                var editor = textBox(game.screen);
                String previous = editor.getValue();
                button(game.screen, "script-example-insert").onPress();
                require(editor.getValue().startsWith(previous), "Example insertion replaced the existing draft");
                editor.setValue("//" + "x".repeat(16381));
                button(game.screen, "script-example-insert").onPress();
                require(editor.getValue().length() == 16383, "Example insertion truncated the existing draft");
                editor.setValue("return true;");
                button(game.screen, "script-save").onPress();
                require(game.screen.getClass().getSimpleName().equals("QuestScriptEditorScreen") && savedScript == null,
                        "Invalid top-level return was saved");
                wait = 6;
            }
            case 11 -> {
                capture(game, "js-editor-syntax-error-zh-1920");
                textBox(game.screen).setValue("Number(experienceLevel) >= 10;");
                String beforeResize = textBox(game.screen).getValue();
                game.getWindow().setWindowed(854, 480);
                game.getWindow().setWidth(854);
                game.getWindow().setHeight(480);
                game.resizeDisplay();
                require(textBox(game.screen).getValue().equals(beforeResize), "Script draft lost on resize");
                button(game.screen, "script-help").onPress();
                require(textBox(game.screen).getValue().equals(beforeResize), "Reference toggle lost draft");
                desktop(game);
                button(game.screen, "script-save").onPress();
                require(game.screen == graphEditor && "Number(experienceLevel) >= 10;".equals(savedScript),
                        "Valid script draft did not return to its graph");
                var nodes = (List<TriggerGraph.Node>) field(graphEditor, "nodes");
                int index = 1;
                var visual = new TriggerGraph.Node("check", "js", 230, 50,
                        Map.of("mode", "visual", "field", "experienceLevel", "operator", "gte",
                                "expected", "10", "logic", "single"));
                nodes.set(index, visual);
                call(graphEditor, "init");
                wait = 8;
            }
            case 12 -> {
                capture(game, "visual-predicate-graph-zh-1920");
                var type = Class.forName("xtdpotato.xero_quest_journal.client.QuestScriptFileScreen");
                var constructor = type.getDeclaredConstructor(Screen.class, String.class);
                constructor.setAccessible(true);
                game.setScreen((Screen) constructor.newInstance(graphEditor, "conditions/docs_level.js"));
                wait = 8;
            }
            case 13 -> {
                capture(game, "js-condition-file-zh-1920");
                textBox(game.screen).setValue("experienceLevel >= 5;");
                button(game.screen, "script-file-save").onPress();
                wait = 8;
            }
            case 14 -> {
                if (game.screen != graphEditor) return false;
                require(QuestScripts.source("conditions/docs_level.js").equals("experienceLevel >= 5;"),
                        "File-backed editor did not persist its source");
                rewardsEditor = new QuestRewardsScreen(journal, List.of(
                        new QuestDefinition.Reward("script", "rewards/docs_welcome.js", 1)),
                        "manual", (values, mode) -> {});
                game.setScreen(rewardsEditor);
                var draft = ((List<?>) field(rewardsEditor, "rewards")).getFirst();
                var method = QuestRewardsScreen.class.getDeclaredMethod("editSource", draft.getClass());
                method.setAccessible(true);
                method.invoke(rewardsEditor, draft);
                require(field(game.screen, "kind") == QuestScriptReference.Kind.REWARD_FILE,
                        "Reward script entry did not open the contextual reference editor");
                wait = 10;
            }
            case 15 -> {
                capture(game, "js-reward-file-zh-1920");
                textBox(game.screen).setValue("message(\"Saved reward example\");");
                button(game.screen, "script-file-save").onPress();
                wait = 8;
            }
            case 16 -> {
                if (game.screen != rewardsEditor) return false;
                require(QuestScripts.source("rewards/docs_welcome.js").equals("message(\"Saved reward example\");"),
                        "Reward script source did not persist");
                game.setScreen(new MailboxScreen(journal));
                wait = 10;
            }
            case 17 -> {
                capture(game, "mailbox-authoring-zh-1920");
                game.setScreen(QuestScreen.overview(null));
                wait = 10;
            }
            case 18 -> {
                capture(game, "overview-authoring-zh-1920");
            }
            case 19 -> {
                if (!AuthoringInteractionRegression.tick(game)) return false;
            }
            case 20 -> {
                if (!DocumentationGalleryRegression.tick(game)) return false;
                game.setScreen(null);
                return true;
            }
            default -> throw new AssertionError("Unknown authoring documentation stage " + stage);
        }
        stage++;
        return false;
    }

    private static void setupAndCheck(Minecraft game) throws Exception {
        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
        Files.createDirectories(QuestRepository.root());
        Files.createDirectories(QuestConfig.root().resolve("scripts/conditions"));
        Files.createDirectories(QuestConfig.root().resolve("scripts/rewards"));
        Files.writeString(QuestConfig.root().resolve("scripts/conditions/docs_level.js"), "experienceLevel >= 10;");
        Files.writeString(QuestConfig.root().resolve("scripts/rewards/docs_welcome.js"),
                "message(\"Congratulations, \" + playerName + \"!\");\ncommand(\"say Quest reward claimed\");");
        Files.writeString(QuestRepository.root().resolve(ID + ".html"),
                QuestPresentationDocuments.write("<quest id=\"" + ID + "\" name=\"学习 JavaScript 判断\" type=\"guide\" icon=\"minecraft:book\">"
                        + "<content><h2>编写第一个判断</h2><p>点击事件进入判断，满足等级条件时执行真分支。</p>"
                        + "<p>事件图中的字段都是字符串；使用 Number(experienceLevel) 进行数值比较。</p></content></quest>",
                        new QuestPresentation(true, 400, 280, List.of(control(graph)))));
        QuestRepository.reload();
        require(QuestRepository.find(ID) != null, "Documentation quest failed to load");
        player.giveExperienceLevels(15 - player.experienceLevel);
        player.setHealth(player.getMaxHealth() / 2);
        player.setShiftKeyDown(true);
        var context = new LinkedHashMap<String, String>();
        for (String name : TriggerGraph.FIELDS) context.put(name, QuestVisualPredicate.field(player, name, "click", "", ID, "button"));
        context.put("mouseInside", "true");
        context.put("locationFound", "true");
        context.put("locatedDimension", player.level().dimension().location().toString());
        require(QuestScripts.syntaxError("return true;").length() > 0, "Top-level return accepted");
        require(QuestScripts.syntaxError("while (true) {}").isEmpty(), "Syntax check executed a loop");
        boolean invalidModeRejected = false;
        try { new TriggerGraph.Node("invalid", "js", 0, 0, Map.of("mode", "javascript")); }
        catch (IllegalArgumentException expected) { invalidModeRejected = true; }
        require(invalidModeRejected, "Unknown predicate mode accepted");
        require(Boolean.TRUE.equals(QuestScripts.run(player, "experienceLevel === 15;", true)), "File numeric binding changed");
        require(Boolean.TRUE.equals(QuestScripts.run(player, "experienceLevel === \"15\";", true, context)), "Graph string binding changed");
        for (var kind : QuestScriptReference.Kind.values()) for (var example : QuestScriptReference.examples(kind)) {
            if (kind == QuestScriptReference.Kind.KUBEJS) continue;
            require(QuestScripts.syntaxError(example.source()).isEmpty(), "Invalid reference example: " + example.id());
            Object result = QuestScripts.run(player, example.source(), kind != QuestScriptReference.Kind.REWARD_FILE,
                    kind == QuestScriptReference.Kind.GRAPH_PREDICATE ? context : Map.of());
            if (kind != QuestScriptReference.Kind.REWARD_FILE)
                require(result instanceof Boolean, "Reference predicate is not Boolean: " + example.id());
        }
        for (String value : List.of("1;", "\"true\";", "undefined;"))
            require(!Boolean.TRUE.equals(QuestScripts.run(player, value, true, context)), "Truthy value accepted: " + value);
        checkGraph(player, "true;", "TRUE", false);
        checkGraph(player, "1;", "FALSE", false);
        checkGraph(player, "\"true\";", "FALSE", false);
        checkGraph(player, "missingBinding;", null, false);
        checkGraph(player, "true;", "TRUE", true);
        QuestEventGraphs.reset(player, QuestRepository.find("docs_branch"));
        QuestEventGraphs.click(player, "docs_branch", "button");
        require("FALSE".equals(QuestControlData.get(player).properties(player, QuestRepository.find("docs_branch"), "button").get("value")),
                "Successful once-only predicate did not route false on retry");
        exportInterfaces(player, context);
        AuthoringInteractionRegression.runtime(player);
        QuestProgressData.get(player.server).focused(player.getUUID(), "");
    }
    private static void checkGraph(ServerPlayer player, String source, String expected, boolean once) throws Exception {
        Files.writeString(QuestRepository.root().resolve("docs_branch.html"),
                QuestPresentationDocuments.write("<quest id=\"docs_branch\" name=\"判断分支验证\" type=\"guide\"/>",
                        new QuestPresentation(true, 400, 240, List.of(control(graph(source, once))))));
        QuestRepository.reload();
        var quest = QuestRepository.find("docs_branch");
        QuestControlData.get(player).resetTask(player, quest);
        QuestEventGraphs.click(player, quest.id(), "button");
        require(Objects.equals(expected, QuestControlData.get(player).properties(player, quest, "button").get("value")),
                "Incorrect JS branch for " + source);
    }
    private static TriggerGraph graph(String source, boolean once) {
        return new TriggerGraph(List.of(
                new TriggerGraph.Node("clicked", "event", 20, 50, Map.of("event", "click", "cooldown", "1")),
                new TriggerGraph.Node("check", "js", 230, 50, Map.of("mode", "script", "script", source, "once", Boolean.toString(once))),
                new TriggerGraph.Node("yes", "set_property", 600, 0, Map.of("control", "button", "property", "value", "value", "TRUE")),
                new TriggerGraph.Node("no", "set_property", 600, 150, Map.of("control", "button", "property", "value", "value", "FALSE"))),
                List.of(new TriggerGraph.Edge("clicked", "check", "next"),
                        new TriggerGraph.Edge("check", "yes", "true"), new TriggerGraph.Edge("check", "no", "false")));
    }
    private static QuestPresentation.Control control(TriggerGraph graph) {
        return new QuestPresentation.Control("button", "button", "检查经验等级", 20, 190, 180, 28, "", Map.of(), graph);
    }
    private static void exportInterfaces(ServerPlayer player, Map<String, String> context) throws Exception {
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        var routes = new TreeMap<String, Integer>();
        var dispatcher = player.server.getCommands().getDispatcher();
        for (String name : List.of("xero_quest", "xero_toast", "xero_structure", "xero_anecdote")) {
            var root = dispatcher.getRoot().getChild(name);
            require(root != null, "Missing command root " + name);
            for (int permission : new int[]{0, 2, 4})
                for (String usage : dispatcher.getAllUsage(root, player.createCommandSourceStack().withPermission(permission), true))
                    routes.putIfAbsent("/" + name + (usage.isBlank() ? "" : " " + usage), permission);
        }
        Files.writeString(OUTPUT.resolve("commands-runtime.json"), gson.toJson(routes));
        var examples = new ArrayList<Map<String, String>>();
        for (var kind : QuestScriptReference.Kind.values())
            for (var example : QuestScriptReference.examples(kind))
                examples.add(Map.of("kind", kind.name(), "id", example.id(), "source", example.source()));
        Files.writeString(OUTPUT.resolve("script-reference-examples.json"), gson.toJson(examples));
        Files.writeString(OUTPUT.resolve("script-graph-context.json"), gson.toJson(context));
    }
    private static void selectPredicate() throws Exception {
        set(graphEditor, "selected", "check");
        ((Set<String>) field(graphEditor, "selectedIds")).add("check");
        call(graphEditor, "init");
    }
    private static void desktop(Minecraft game) {
        game.getWindow().setWindowed(1920, 1080);
        game.getWindow().setWidth(1920);
        game.getWindow().setHeight(1080);
        game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        require(game.getLanguageManager().getSelected().equals("zh_cn"), "Screenshot language is not Chinese");
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            require(image.getWidth() == 1920 && image.getHeight() == 1080, "Screenshot is not 1920x1080");
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }
    private static MultiLineEditBox textBox(Screen screen) {
        return screen.children().stream().filter(MultiLineEditBox.class::isInstance)
                .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing button " + id));
    }
    private static Field reflected(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Object field(Object target, String name) throws Exception { return reflected(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { reflected(target, name).set(target, value); }
    private static Object call(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
