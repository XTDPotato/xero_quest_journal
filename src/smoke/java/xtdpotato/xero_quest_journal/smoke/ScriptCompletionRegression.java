package xtdpotato.xero_quest_journal.smoke;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.AnimatedQuestScreen;
import xtdpotato.xero_quest_journal.client.ScriptCompletions;
import xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind;

/** Runs real text-model, pointer and resize events without evaluating author code. */
final class ScriptCompletionRegression {
    private static int stage, wait;
    private static Screen screen;
    private static String saved;
    private static final Path OUTPUT = Path.of("..", "build", "verification");

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0 || game.screen instanceof AnimatedQuestScreen animated && animated.inputBlocked()) return false;
        switch (stage) {
            case 0 -> {
                verifyAnalysis();
                game.options.guiScale().set(2);
                resize(game, 1920, 1080);
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                var reload = game.reloadResourcePacks();
                reload.thenRun(() -> {});
                wait = 20;
            }
            case 1 -> {
                open(game, "Number(experien) >= 10;", Kind.GRAPH_PREDICATE);
                seek("Number(experien".length());
                wait = 8;
            }
            case 2 -> {
                if (!ready("experienceLevel")) return false;
                require(box().getBottom() <= (int) call(screen, "completionTop"), "Candidate panel covers code");
                capture(game, "js-completion-zh-1920");
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(box().getValue().equals("Number(experienceLevel) >= 10;"), "Completion corrupted surrounding code");
                require(screen.getFocused() == box(), "Completion stole editor focus");
                screen.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(box().getValue().equals("Number(experien) >= 10;"), "Completion undo did not restore text");
                require(model().cursor() == "Number(experien".length(), "Undo did not restore caret");
                screen.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(box().getValue().equals("Number(experienceLevel) >= 10;"), "Completion redo failed");
                require(model().cursor() == "Number(experienceLevel".length(), "Redo caret moved to document end");
                box().setValue("Math.sin(0) + 1;");
                seek("Math.si".length());
                wait = 8;
            }
            case 3 -> {
                if (!ready("sin")) return false;
                select("sin");
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(box().getValue().equals("Math.sin(0) + 1;"), "Middle-of-word suffix duplicated");
                box().setValue("Math.s");
                wait = 8;
            }
            case 4 -> {
                if (!ready("sqrt")) return false;
                int before = model().cursor();
                screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
                require(model().cursor() == before - 1, "Popup consumed cursor movement");
                box().setValue("Math.s");
                screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(box().getValue().equals("Math.s\n"), "Popup consumed newline");
                box().setValue("Math.s");
                model().setSelecting(false);
                model().seekCursor(Whence.ABSOLUTE, 5);
                model().setSelecting(true);
                model().seekCursor(Whence.ABSOLUTE, 6);
                model().setSelecting(false);
                screen.charTyped('c', 0);
                require(box().getValue().equals("Math.c"), "Typing did not replace selection");
                screen.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(box().getValue().equals("Math.s") && model().getSelectedText().equals("s"),
                        "Undo did not restore the selected text");
                screen.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(box().getValue().equals("Math.c") && !model().hasSelection(), "Redo did not restore edit state");
                box().setValue("Math.s");
                wait = 8;
            }
            case 5 -> {
                if (!ready("sqrt")) return false;
                screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_ALT);
                require((int) field(screen, "selectedCompletion") == 1, "Alt+Down did not select a candidate");
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(game.screen == screen && !(boolean) call(screen, "completionVisible"), "Escape closed editor");
                screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_MOD_CONTROL);
                wait = 8;
            }
            case 6 -> {
                if (!ready("sqrt")) return false;
                double x = 62, y = (int) call(screen, "completionListTop") + 7;
                String before = box().getValue();
                screen.mouseClicked(x, y, 0);
                require(box().getValue().equals(before), "Candidate applied on press");
                screen.mouseDragged(x, y - 30, 0, 0, -30);
                screen.mouseReleased(x, y - 30, 0);
                require(box().getValue().equals(before), "Candidate applied after releasing outside");
                screen.mouseClicked(x, y, 0);
                screen.mouseReleased(x, y, 0);
                require(!box().getValue().equals(before) && box().isFocused(), "Candidate click or focus failed");
                open(game, "event.loc", Kind.KUBEJS);
                wait = 8;
            }
            case 7 -> {
                if (!ready("locationFound")) return false;
                capture(game, "kubejs-completion-zh-1920");
                resize(game, 854, 480);
                require(box().getBottom() <= (int) call(screen, "completionTop"), "Compact popup covers code");
                require((int) call(screen, "bodyBottom") < screen.height - 29, "Popup covers footer");
                require(box().getHeight() >= 36, "Compact code field collapsed");
                require(box().getValue().equals("event.loc"), "Resize lost draft");
                wait = 8;
            }
            case 8 -> {
                if (!ready("locationFound")) return false;
                capture(game, "kubejs-completion-compact");
                box().setValue("event.");
                wait = 8;
            }
            case 9 -> {
                if (!ready("questId")) return false;
                box().setValue("Math.co");
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(box().getValue().equals("Math.co"), "Stale event candidate replaced Math prefix");
                screen.setFocused(box());
                wait = 8;
            }
            case 10 -> {
                if (!ready("cos")) return false;
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(box().getValue().startsWith("Math.co") && !box().getValue().contains("event"),
                        "Stale asynchronous completion applied");
                box().setValue("/*" + "x".repeat(16373) + "*/exper");
                wait = 8;
            }
            case 11 -> {
                if (!ready("experienceLevel")) {
                    // KubeJS intentionally has no bare experienceLevel; switch to a predicate for the limit check.
                    open(game, "/*" + "x".repeat(16373) + "*/exper", Kind.GRAPH_PREDICATE);
                    wait = 8;
                    stage++;
                    return false;
                }
            }
            case 12 -> {
                if (!ready("experienceLevel")) return false;
                String previous = box().getValue();
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(box().getValue().equals(previous), "Oversized insertion truncated code");
                model().seekCursor(Whence.ABSOLUTE, 2);
                wait = 8;
            }
            case 13 -> {
                require(!(boolean) call(screen, "completionVisible"), "Comment displayed completion");
                box().setValue("experienceLevel > 1;");
                screen.keyPressed(GLFW.GLFW_KEY_S, 0, GLFW.GLFW_MOD_CONTROL);
                require("experienceLevel > 1;".equals(saved), "Ctrl+S no longer saves");
                return true;
            }
        }
        stage++;
        return false;
    }

    private static void verifyAnalysis() {
        check("exper", Kind.GRAPH_PREDICATE, "experienceLevel");
        require(candidates("exper", Kind.GRAPH_PREDICATE).get(0).type().equals("string"), "Graph type is not string");
        require(candidates("exper", Kind.CONDITION_FILE).get(0).type().equals("number"), "File type is not number");
        check("Math.si", Kind.CONDITION_FILE, "sin", "sinh", "sign");
        check("JSON.st", Kind.CONDITION_FILE, "stringify");
        check("mess", Kind.REWARD_FILE, "message");
        require(candidates("mess", Kind.CONDITION_FILE).isEmpty(), "Condition offered reward actions");
        check("event.loc", Kind.KUBEJS, "locationFound", "locatedDimension");
        require(candidates("event.", Kind.GRAPH_PREDICATE).isEmpty(), "Built-in offered KubeJS event");
        require(candidates("play", Kind.KUBEJS).isEmpty(), "KubeJS offered invalid bare player data");
        check("global.on", Kind.KUBEJS, "onXeroQuestAction");
        check("const score = 3; sco", Kind.CONDITION_FILE, "score");
        check("const state = {score: 1, title: 'x'}; state.sc", Kind.CONDITION_FILE, "score");
        check("const state = {nested: {score: 1}}; state.nested.sc", Kind.CONDITION_FILE, "score");
        check("const name = playerName; name.to", Kind.CONDITION_FILE, "toUpperCase");
        check("const ev = event; ev.que", Kind.KUBEJS, "questId");
        check("const list = [1, 2]; list.ma", Kind.CONDITION_FILE, "map");
        check("function test(level) { lev", Kind.CONDITION_FILE, "level");
        require(candidates("function hidden(){ const secret=1; } sec", Kind.CONDITION_FILE).isEmpty(),
                "Sibling function locals leaked");
        for (String source : List.of("// experien", "/* experien", "'experien", "\"experien",
                "/experien/", "`experien")) {
            require(candidates(source, Kind.GRAPH_PREDICATE).isEmpty(), "Completion in literal/comment: " + source);
        }
        check("const state = {score: 1}; state.", Kind.CONDITION_FILE, "score");
        require(ScriptCompletions.complete("", 0, Kind.REWARD_FILE, true).candidates().stream()
                .anyMatch(value -> value.name().equals("message")), "Explicit completion missing globals");
        require(ScriptCompletions.complete("x".repeat(65536), 65536, Kind.CONDITION_FILE, true)
                .candidates().isEmpty(), "Oversized source was analyzed");
        System.clearProperty("xero.completion.executed");
        candidates("java.lang.System.setProperty('xero.completion.executed','yes'); Math.s", Kind.KUBEJS);
        require(System.getProperty("xero.completion.executed") == null, "Completion executed source");
    }

    private static void check(String source, Kind kind, String... expected) {
        var names = candidates(source, kind).stream().map(ScriptCompletions.Candidate::name).toList();
        for (String value : expected) require(names.contains(value), "Missing " + value + " in " + source + ": " + names);
    }
    private static List<ScriptCompletions.Candidate> candidates(String source, Kind kind) {
        return ScriptCompletions.complete(source, source.length(), kind, false).candidates();
    }
    private static void open(Minecraft game, String source, Kind kind) throws Exception {
        var type = Class.forName("xtdpotato.xero_quest_journal.client.QuestScriptEditorScreen");
        var constructor = type.getDeclaredConstructor(Screen.class, Component.class, String.class, Consumer.class, Kind.class);
        constructor.setAccessible(true);
        saved = null;
        screen = (Screen) constructor.newInstance(null, Component.literal("JavaScript / KubeJS"), source,
                (Consumer<String>) value -> saved = value, kind);
        game.setScreen(screen);
    }
    private static boolean ready(String name) throws Exception {
        var result = (ScriptCompletions.Result) field(screen, "completionResult");
        return result != null && result.candidates().stream().anyMatch(value -> value.name().equals(name));
    }
    private static void select(String name) throws Exception {
        var result = (ScriptCompletions.Result) field(screen, "completionResult");
        int target = result.candidates().stream().map(ScriptCompletions.Candidate::name).toList().indexOf(name);
        require(target >= 0, "Candidate not found: " + name);
        while ((int) field(screen, "selectedCompletion") != target)
            screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_ALT);
    }
    private static MultiLineEditBox box() {
        return screen.children().stream().filter(MultiLineEditBox.class::isInstance)
                .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
    }
    private static MultilineTextField model() throws Exception {
        for (Field field : MultiLineEditBox.class.getDeclaredFields()) {
            if (field.getType() == MultilineTextField.class) {
                field.setAccessible(true);
                return (MultilineTextField) field.get(box());
            }
        }
        throw new AssertionError("No text model");
    }
    private static void seek(int cursor) throws Exception {
        model().setSelecting(false);
        model().seekCursor(Whence.ABSOLUTE, cursor);
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private static void resize(Minecraft game, int width, int height) {
        game.getWindow().setWindowed(width, height);
        game.getWindow().setWidth(width);
        game.getWindow().setHeight(height);
        game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(OUTPUT.resolve(name + ".png")); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
