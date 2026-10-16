package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.core.runtime.*;

/** Pointer gestures, owner-preserving bindings and live anchored inventory entries. */
final class AuthoringInteractionRegression {
    private static final String SOURCE = "(function () {\n    const level = Number(experienceLevel);\n"
            + "    const label = \"preserve all source lines\";\n    return level >= 10;\n})();";
    private static int phase, wait;
    private static QuestTriggerGraphScreen graph;
    private static Screen parent;
    private static int fullHeight;
    private static InventoryPlacementScreen placement;
    private static InventoryEntryPosition oldTask, oldMail;
    private static boolean oldShowTask, oldShowMail;

    static boolean tick(Minecraft game) throws Exception {
        if (phase == 15) {
            if (!xtdpotato.xero_quest_journal.platform.PlatformServices.isModLoaded("roughlyenoughitems"))
                return true;
            return ReiShortcutRegression.tick(game);
        }
        if (game.screen instanceof AnimatedQuestScreen animated && animated.inputBlocked()) return false;
        if (--wait > 0) return false;
        switch (phase) {
            case 0 -> {
                parent = game.screen;
                graph = new QuestTriggerGraphScreen(parent, new TriggerGraph(List.of(
                        node("script", "js", 40, 50, Map.of("mode", "script", "script", SOURCE, "layoutWidth", "220"))),
                        List.of()), ignored -> {});
                game.setScreen(graph);
                select("script");
            }
            case 1 -> {
                var script = nodes().getFirst();
                fullHeight = size(script, "Height");
                var box = textBlock(script, "script");
                require((String.valueOf(call(box, "value"))).equals(SOURCE), "Default node display flattened/truncated source");
                Object checkbox = showAllControl();
                require((boolean) call(checkbox, "selected"), "Show-all is not checked by default");
                capture(game, "graph-full-script-zh-1920");
                doubleClick(script, box);
                require(game.screen.getClass().getSimpleName().equals("QuestScriptEditorScreen"),
                        "Double-click did not open the contextual script editor: "
                                + game.screen.getClass().getSimpleName() + ", property=" + field(graph, "lastValueProperty")
                                + ", dragging=" + field(graph, "dragging") + ", resize=" + field(graph, "resizeEdges"));
            }
            case 2 -> {
                require(textBox(game.screen).getValue().equals(SOURCE), "Quick edit lost complete source");
                textBox(game.screen).setValue(SOURCE + "\n// saved by quick edit");
                button(game.screen, "script-save").onPress();
                require(game.screen == graph, "Quick script save did not return to graph");
            }
            case 3 -> {
                require(nodes().getFirst().get("script", "").endsWith("// saved by quick edit"), "Quick edit did not update node draft");
                var checkbox = showAllControl();
                call(checkbox, "onPress");
                var compact = nodes().getFirst();
                require(compact.get("showAll", "").equals("false") && size(compact, "Height") < fullHeight,
                        "Show-all checkbox did not collapse long content");
                require(compact.get("script", "").contains("\n"), "Compact display modified source");
                require(new Gson().fromJson(new Gson().toJson(compact), TriggerGraph.Node.class).equals(compact),
                        "Display flag lost on source round-trip");
                checkbox = showAllControl();
                call(checkbox, "onPress");
                require(nodes().getFirst().get("showAll", "").equals("true"), "Show-all did not restore");
                graph = new QuestTriggerGraphScreen(parent, new TriggerGraph(List.of(
                        node("condition", "js", 40, 60, Map.of("mode", "visual", "field", "experienceLevel",
                                "fieldPlayer", "@p", "operator", "gte", "expected", "10")),
                        node("setter", "set_variable", 440, 180, Map.of("variable", "temporary_draft", "value", "0"))),
                        List.of()), ignored -> {});
                game.setScreen(graph);
                select("condition");
            }
            case 4 -> {
                var condition = nodes().getFirst();
                double[] point = slotPoint(condition);
                graph.mouseClicked(point[0], point[1], 0);
                graph.mouseReleased(point[0], point[1], 0);
                require(field(graph, "inlineChoice") != null, "Clicking field slot did not open its choice menu");
            }
            case 5 -> {
                for (char c : "jingyandengji".toCharArray()) graph.charTyped(c, 0);
                var choice = field(graph, "inlineChoice");
                require(((List<?>) call(choice, "options")).size() == 1, "Pinyin search did not isolate experience level");
            }
            case 6 -> {
                capture(game, "graph-inline-pinyin-zh-1920");
                graph.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(nodes().getFirst().get("field", "").equals("experienceLevel"), "Filtered field choice selected the wrong ID");
                boolean reduced = ClientPreferences.get().reducedMotion;
                ClientPreferences.get().reducedMotion = true;
                double[] point = slotPoint(nodes().getFirst());
                graph.mouseClicked(point[0], point[1], 1);
                require(game.screen.getClass().getSimpleName().equals("QuestValuePickerScreen"),
                        "Right-click did not open the full searchable field picker");
                var search = (EditBox) field(game.screen, "search");
                search.setValue("jingyandengji");
                require(((List<?>) field(game.screen, "filtered")).size() == 1,
                        "Full field picker did not filter using pinyin");
                int x = (int) call(game.screen, "left") + 40;
                int y = (int) call(game.screen, "listTop") + 10;
                game.screen.mouseClicked(x, y, 0);
                require(game.screen == graph && nodes().getFirst().get("field", "").equals("experienceLevel"),
                        "Full field picker selection did not return the selected value");
                ClientPreferences.get().reducedMotion = reduced;
            }
            case 7 -> {
                var condition = nodes().getFirst();
                double[] point = slotPoint(condition);
                graph.mouseClicked(point[0], point[1], 0);
                graph.mouseDragged(point[0] + 20, point[1] + 5, 0, 20, 5);
                graph.mouseReleased(point[0] + 20, point[1] + 5, 0);
                require(nodes().size() == 2 && nodes().getFirst().equals(condition), "Short field drag moved or extracted the node");
                graph.mouseClicked(point[0], point[1], 0);
                set(graph, "optionPressedAt", System.nanoTime() - 400_000_000L);
                graph.mouseDragged(point[0] + 50, point[1] + 25, 0, 50, 25);
                require(nodes().size() == 3, "Hold-drag did not extract a variable");
                var extracted = nodes().getLast();
                require(extracted.type().equals("context") && extracted.get("field", "").equals("experienceLevel")
                                && extracted.get("player", "").equals("@p"), "Extraction lost the field or owner");
                require(nodes().getFirst().equals(condition), "Extracting a field moved its source node");
                var setter = nodes().get(1);
                graph.mouseReleased(sx(setter) + 30, sy(setter) + 35, 0);
                setter = nodes().get(1);
                require(nodes().size() == 2 && setter.type().equals("set_context")
                                && setter.get("field", "").equals("experienceLevel") && setter.get("player", "").equals("@p"),
                        "Dropping built-in variable into setter lost its identity or owner");
            }
            case 8 -> {
                capture(game, "graph-variable-owner-zh-1920");
                button(graph, "graph-undo").onPress();
                require(nodes().size() == 2 && nodes().get(1).type().equals("set_variable"), "Extraction/binding did not undo atomically");
                button(graph, "graph-redo").onPress();
                require(nodes().get(1).type().equals("set_context"), "Binding redo lost built-in setter");
                var condition = nodes().getFirst();
                double x = sx(condition) + 30, y = sy(condition) + 9;
                graph.mouseClicked(x, y, 0);
                graph.mouseDragged(x + 24, y + 24, 0, 24, 24);
                graph.mouseReleased(x + 24, y + 24, 0);
                require(nodes().getFirst().x() != condition.x(), "Node header no longer moves the node");
                graph = new QuestTriggerGraphScreen(parent, new TriggerGraph(List.of(
                        node("command", "command", 40, 50, Map.of("command", "say Before"))), List.of()), ignored -> {});
                game.setScreen(graph);
                select("command");
            }
            case 9 -> {
                var command = nodes().getFirst();
                doubleClick(command, textBlock(command, "command"));
                require(game.screen.getClass().getSimpleName().equals("QuestPropertyValueScreen"), "Command quick edit opened the wrong editor");
            }
            case 10 -> {
                textBox(game.screen).setValue("say After");
                button(game.screen, "property-save").onPress();
                require(nodes().getFirst().get("command", "").equals("say After"), "Command quick edit did not persist its draft");
                var prefs = ClientPreferences.get();
                oldTask = prefs.taskInventoryPosition; oldMail = prefs.mailInventoryPosition;
                oldShowTask = prefs.inventoryButton; oldShowMail = prefs.mailboxInventoryButton;
                prefs.inventoryButton = true; prefs.mailboxInventoryButton = true;
                prefs.taskInventoryPosition = new InventoryEntryPosition("top_left", 6, 6);
                prefs.mailInventoryPosition = new InventoryEntryPosition("top_left", 30, 6);
                placement = new InventoryPlacementScreen(graph);
                game.setScreen(placement);
            }
            case 11 -> {
                require(Arrays.equals(new InventoryEntryPosition("top_left", 0, 0).resolve(960, 540), new int[]{0, 0}),
                        "Top-left zero offset is not screen zero");
                require(Arrays.equals(new InventoryEntryPosition("bottom_right", 0, 0).resolve(960, 540), new int[]{940, 520}),
                        "Bottom-right zero offset is not edge aligned");
                var point = new InventoryEntryPosition("center", 0, 0).move(2, 3, 960, 540, true);
                require(point.equals(new InventoryEntryPosition("top_left", 0, 0)), "Top-left snap did not normalize anchor and offsets");
                double scale = (double) call(placement, "previewScale");
                double x = (double) call(placement, "previewX"), y = (double) call(placement, "previewY");
                placement.mouseClicked(x + 11 * scale, y + 11 * scale, 0);
                placement.mouseDragged(x + 5 * scale, y + 5 * scale, 0, -6 * scale, -6 * scale);
                placement.mouseReleased(x + 5 * scale, y + 5 * scale, 0);
                require(field(placement, "task").equals(new InventoryEntryPosition("top_left", 0, 0)),
                        "Visual drag did not snap to screen origin");
                capture(game, "inventory-placement-zh-1920");
                var checkbox = widgetByMessage(placement, "screen.xero_quest_journal.inventory_placement.show_task");
                call(checkbox, "onPress");
                button(placement, "placement-save").onPress();
            }
            case 12 -> {
                require(!ClientPreferences.get().inventoryButton && ClientPreferences.get().mailboxInventoryButton,
                        "Task visibility switch also disabled mail");
                game.setScreen(new InventoryScreen(game.player));
            }
            case 13 -> {
                require(!hasButton(game.screen, "inventory-entry") && hasButton(game.screen, "mailbox-inventory-entry"),
                        "Independent inventory visibility did not reach the real screen");
                var prefs = ClientPreferences.get();
                prefs.inventoryButton = true; prefs.mailboxInventoryButton = false;
                prefs.taskInventoryPosition = new InventoryEntryPosition("bottom_right", 0, 0);
                game.setScreen(new InventoryScreen(game.player));
            }
            case 14 -> {
                var task = button(game.screen, "inventory-entry");
                require(!hasButton(game.screen, "mailbox-inventory-entry") && task.getX() == game.screen.width - 20
                                && task.getY() == game.screen.height - 20, "Live inventory ignored anchor alignment");
                capture(game, "inventory-relative-anchor-zh-1920");
                var prefs = ClientPreferences.get();
                prefs.taskInventoryPosition = oldTask; prefs.mailInventoryPosition = oldMail;
                prefs.inventoryButton = oldShowTask; prefs.mailboxInventoryButton = oldShowMail;
                require(prefs.save(), "Could not restore isolated test preferences");
            }
            default -> throw new AssertionError("Unknown interaction phase " + phase);
        }
        phase++;
        wait = 7;
        return false;
    }
    static void runtime(net.minecraft.server.level.ServerPlayer player) throws Exception {
        require(QuestContextTargets.resolve(player, "@s") == player, "Self owner did not resolve");
        require(QuestContextTargets.resolve(player, "@p") == player, "Nearest owner did not resolve");
        boolean rejected = false;
        try { QuestContextTargets.validateRead("@a"); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Ambiguous multiple-player read owner accepted");
        var setter = QuestEventGraphs.class.getDeclaredMethod("setGameContext",
                net.minecraft.server.level.ServerPlayer.class, TriggerGraph.Node.class);
        setter.setAccessible(true);
        int previous = player.experienceLevel;
        setter.invoke(null, player, node("write", "set_context", 0, 0,
                Map.of("scope", "player", "field", "experienceLevel", "player", "@s", "value", "17")));
        require(player.experienceLevel == 17, "Built-in setter did not change the selected player's level");
        setter.invoke(null, player, node("write", "set_context", 0, 0,
                Map.of("scope", "player", "field", "experienceLevel", "player", "@a[name=__absent_docs_player]", "value", "19")));
        require(player.experienceLevel == 17, "Nonmatching selector modified the trigger player");
        player.giveExperienceLevels(previous - player.experienceLevel);
        require(QuestVisualPredicate.fieldFor(player, "experienceLevel", "click", "", "", "", "@p")
                .equals(Integer.toString(previous)), "Owned visual field returned the wrong player data");
    }
    private static TriggerGraph.Node node(String id, String type, int x, int y, Map<String, String> data) {
        return new TriggerGraph.Node(id, type, x, y, data);
    }
    @SuppressWarnings("unchecked") private static List<TriggerGraph.Node> nodes() throws Exception {
        return (List<TriggerGraph.Node>) field(graph, "nodes");
    }
    private static int size(TriggerGraph.Node node, String axis) throws Exception {
        return (int) call(graph, "node" + axis, new Class<?>[]{TriggerGraph.Node.class}, node);
    }
    private static double sx(TriggerGraph.Node node) throws Exception { return (double) call(graph, "sx", new Class<?>[]{TriggerGraph.Node.class}, node); }
    private static double sy(TriggerGraph.Node node) throws Exception { return (double) call(graph, "sy", new Class<?>[]{TriggerGraph.Node.class}, node); }
    private static Object layout(TriggerGraph.Node node) throws Exception {
        return call(graph, "nodeLayout", new Class<?>[]{TriggerGraph.Node.class}, node);
    }
    private static Object textBlock(TriggerGraph.Node node, String property) throws Exception {
        for (Object box : (List<?>) call(layout(node), "texts"))
            if (property.equals(call(box, "property"))) return box;
        throw new AssertionError("Missing editable block " + property);
    }
    private static double[] slotPoint(TriggerGraph.Node node) throws Exception {
        Object slot = ((List<?>) call(layout(node), "slots")).getFirst();
        double zoom = (double) field(graph, "zoom");
        return new double[]{sx(node) + ((int) call(slot, "x") + 8) * zoom,
                sy(node) + ((int) call(slot, "y") + 8) * zoom};
    }
    private static void doubleClick(TriggerGraph.Node node, Object box) throws Exception {
        double zoom = (double) field(graph, "zoom");
        double x = sx(node) + ((int) call(box, "x") + 12) * zoom;
        double y = sy(node) + ((int) call(box, "y") + 4) * zoom;
        for (int click = 0; click < 2; click++) {
            graph.mouseClicked(x, y, 0);
            if (Minecraft.getInstance().screen == graph) graph.mouseReleased(x, y, 0);
        }
    }
    private static void select(String id) throws Exception {
        set(graph, "selected", id);
        ((Set<String>) field(graph, "selectedIds")).add(id);
        call(graph, "init");
    }
    private static boolean hasButton(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .anyMatch(value -> value.styleId().equals(id));
    }
    private static Object widgetByMessage(Screen screen, String key) {
        String message = Component.translatable(key).getString();
        return screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(message))
                .findFirst().orElseThrow();
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(value -> value.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing " + id));
    }
    private static net.minecraft.client.gui.components.AbstractWidget showAllControl() {
        String expected = Component.translatable("screen.xero_quest_journal.graph.show_all").getString();
        return graph.children().stream()
                .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(expected))
                .findFirst().orElseThrow();
    }
    private static MultiLineEditBox textBox(Screen screen) {
        return screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            require(image.getWidth() == 1920 && image.getHeight() == 1080, "Interaction screenshot is not full HD");
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static Field reflected(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Object field(Object object, String name) throws Exception { return reflected(object, name).get(object); }
    private static void set(Object object, String name, Object value) throws Exception { reflected(object, name).set(object, value); }
    private static Object call(Object object, String name) throws Exception { return call(object, name, new Class<?>[0]); }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(object, args); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
