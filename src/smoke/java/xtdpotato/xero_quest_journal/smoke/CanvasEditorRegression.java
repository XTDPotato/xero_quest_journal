package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestPresentationDocuments;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructureLibrary;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructures;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class CanvasEditorRegression {
    private static final String ID = "canvas_" + System.currentTimeMillis();
    private static final String CONTROL = ID + "_text";
    private static QuestScreen journal;
    private static QuestSourceScreen source;
    private static CompletableFuture<Void> pending;
    private static CompletableFuture<Boolean> libraryCheck;
    private static String supportedTemplate;
    private static int stage;
    private static int ticks;
    private static int historyPhase;
    private static String formatted;
    private static final String MARKUP = "<quest id=\"test\"><rules><trigger type=\"confirm\"/></rules>"
            + "<content>  hello <b>world</b> \n </content><script>if (a < b) { call(\"x\"); }</script></quest>";

    static boolean tick(Minecraft game) throws Exception {
        if (++ticks < 12) return false;
        ticks = 0;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    player.getPersistentData().putBoolean("xero_quest_edit_mode", true);
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            ID, "Canvas test", "", "", "", "guide", "", 1)).successful(), "Create task");
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone() || ClientQuestProgress.view(ID) == null) return false;
                pending.join();
                journal = QuestScreen.task(null, ID);
                set(journal, "editMode", true);
                set(journal, "category", ClientQuestDefinitions.find(ID).type());
                set(journal, "searchQuery", "");
                game.setScreen(journal);
                var layout = layout();
                layout.elements().put(CONTROL, new LayoutEditorState.Element(CONTROL, ID, "text",
                        "Extended canvas target", 100, 20, Map.of()));
                layout.position(CONTROL, 20, 30).size(100, 20);
                set(journal, "selectedLayoutId", CONTROL);
                @SuppressWarnings("unchecked") var selected = (Set<String>) field(journal, "selectedLayoutIds");
                selected.add(CONTROL);
                int left = (int) invoke(journal, "canvasLeft", new Class<?>[0]);
                int top = (int) invoke(journal, "canvasTop", new Class<?>[0]);
                journal.mouseClicked(left + 120, top + 95, 1);
                @SuppressWarnings("unchecked")
                var menu = (java.util.List<StyledButton>) field(journal, "menu");
                var settings = menu.stream().filter(b -> b.styleId().equals("context-layout.canvas_settings"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Canvas settings absent from context menu"));
                settings.onPress();
                require(((String) field(field(journal, "form"), "layoutWidth")).equals("395")
                        && ((String) field(field(journal, "form"), "layoutHeight")).equals("275"),
                        "Default canvas size");
                invoke(journal, "closeForm", new Class<?>[0]);
                var hierarchy = journal.children().stream().filter(child ->
                        child instanceof StyledButton b && b.styleId().equals("hierarchy"))
                        .map(child -> (StyledButton) child).findFirst().orElseThrow();
                var iconField = EditorIconButton.class.getDeclaredField("icon");
                iconField.setAccessible(true);
                require(iconField.get(hierarchy).equals("visual"), "Collapsed hierarchy icon changed");
                hierarchy.onPress();
                hierarchy = journal.children().stream().filter(child ->
                        child instanceof StyledButton b && b.styleId().equals("hierarchy"))
                        .map(child -> (StyledButton) child).findFirst().orElseThrow();
                require(iconField.get(hierarchy).equals("back"), "Expanded hierarchy icon has no state feedback");
                hierarchy.onPress();
            }
            case 2 -> {
                checkResponsiveScale(game);
                invoke(journal, "selectLayoutElement", new Class<?>[]{String.class, boolean.class}, CONTROL, false);
                edit("x", "40");
                require(layout().position(CONTROL, 0, 0).x() == 40, "X edit failed");
                edit("y", "60");
                require(layout().position(CONTROL, 0, 0).y() == 60, "Y edit failed");
                openCoordinate("x");
                ((EditBox) field(journal, "coordinateEditor")).setValue("bad");
                journal.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(field(journal, "coordinateEditor") != null, "Invalid coordinate accepted");
                journal.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(layout().position(CONTROL, 0, 0).x() == 40, "Escape changed X");
                journal.keyPressed(GLFW.GLFW_KEY_EQUAL, 0, 0);
                require(near((double) field(journal, "canvasZoom"), 1.01), "Normal zoom step");
                journal.keyPressed(GLFW.GLFW_KEY_MINUS, 0, GLFW.GLFW_MOD_SHIFT);
                require(near((double) field(journal, "canvasZoom"), 1.009), "Fine zoom step");
                edit("zoom", "100");
                int left = (int) invoke(journal, "canvasLeft", new Class<?>[0]);
                int top = (int) invoke(journal, "canvasTop", new Class<?>[0]);
                String unchanged = LayoutStore.snapshot(layout());
                int x = (int) invoke(journal, "logicalX", new Class<?>[]{double.class}, (double) left + 100);
                int y = (int) invoke(journal, "logicalY", new Class<?>[]{double.class}, (double) top + 100);
                invoke(journal, "zoomCanvasAt", new Class<?>[]{double.class, double.class, double.class},
                        1.5, (double) left + 100, (double) top + 100);
                require(Math.abs(x - (int) invoke(journal, "logicalX", new Class<?>[]{double.class},
                        (double) left + 100)) <= 1
                        && Math.abs(y - (int) invoke(journal, "logicalY", new Class<?>[]{double.class},
                        (double) top + 100)) <= 1, "Pointer jumped during preview zoom");
                journal.mouseClicked(left + 100, top + 100, 2);
                journal.mouseDragged(left + 130, top + 120, 2, 30, 20);
                journal.mouseReleased(left + 130, top + 120, 2);
                require((double) field(journal, "canvasPanX") != 0
                        && (double) field(journal, "canvasPanY") != 0, "Middle drag did not pan preview");
                journal.mouseClicked(left + 100, top + 100, 2);
                journal.mouseReleased(left + 100, top + 100, 2);
                journal.mouseClicked(left + 100, top + 100, 2);
                require(near((double) field(journal, "canvasZoom"), 1)
                        && near((double) field(journal, "canvasPanX"), 0)
                        && near((double) field(journal, "canvasPanY"), 0), "Middle double-click did not reset preview");
                require(unchanged.equals(LayoutStore.snapshot(layout())), "Preview interaction changed saved layout");
                invoke(journal, "openCanvasSize", new Class<?>[]{String.class}, ID);
                Object form = field(journal, "form");
                set(form, "layoutHeight", "1600");
                set(form, "layoutWidth", "1200");
                invoke(journal, "saveForm", new Class<?>[0]);
                require(field(journal, "form") == null, "Canvas form did not save");
                require(layout().taskCanvas(ID).height() == 1600, "Canvas height");
                require(layout().taskCanvas("unrelated").width() == 395
                        && layout().taskCanvas("unrelated").height() == 275, "Default task canvas changed");
                var restored = LayoutStore.restore(LayoutStore.snapshot(layout()));
                require(restored.taskCanvas(ID).width() == 1200, "Canvas not persisted");
                journal.setFocused(null);
                journal.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
            }
            case 3 -> {
                if (field(journal, "historyPending") != null || field(journal, "afterPublication") != null
                        || (boolean) invoke(field(journal, "presentationWriter"), "busy", new Class<?>[0])) return false;
                if (historyPhase == 0) {
                    require(layout().taskCanvas(ID).height() != 1600, "Canvas resize undo failed: " + field(journal, "error"));
                    journal.setFocused(null);
                    journal.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                    historyPhase = 1;
                    return false;
                }
                require(layout().taskCanvas(ID).height() == 1600, "Canvas resize redo failed");
                invoke(journal, "selectLayoutElement", new Class<?>[]{String.class, boolean.class}, CONTROL, false);
                edit("y", "900");
                double scale = (double) invoke(journal, "canvasScaleY", new Class<?>[0]);
                set(journal, "detailScroll", 850 * scale);
                set(journal, "detailTargetScroll", 850 * scale);
                int[] display = (int[]) invoke(journal, "elementDisplayPosition",
                        new Class<?>[]{LayoutEditorState.Element.class}, layout().elements().get(CONTROL));
                int logicalY = (int) invoke(journal, "logicalY", new Class<?>[]{double.class}, (double) display[1]);
                require(Math.abs(logicalY - 900) <= 1, "Scrolled coordinate round-trip failed");
                require(display[1] > (int) invoke(journal, "canvasTop", new Class<?>[0])
                        && display[1] < (int) invoke(journal, "canvasBottom", new Class<?>[0]), "Extended control not visible");
                capture(game, "canvas-editor-extended");
                if (Boolean.getBoolean("xero.canvasHistoryOnly")) return true;
                invoke(journal, "openCanvasSize", new Class<?>[]{String.class}, ID);
            }
            case 4 -> {
                capture(game, "canvas-size-form");
                Object form = field(journal, "form");
                set(form, "layoutHeight", "0");
                invoke(journal, "saveForm", new Class<?>[0]);
                require(field(journal, "form") != null && layout().taskCanvas(ID).height() == 1600,
                        "Invalid canvas height was accepted");
                invoke(journal, "closeForm", new Class<?>[0]);
                set(journal, "editMode", false);
                invoke(journal, "init", new Class<?>[0]);
                require((double) invoke(journal, "maxDetailScroll", new Class<?>[0]) > 0, "Preview cannot scroll");
                require(layout().position(CONTROL, 0, 0).y() == 900, "Preview changed saved Y");
                int left = (int) invoke(journal, "canvasLeft", new Class<?>[0]);
                int top = (int) invoke(journal, "canvasTop", new Class<?>[0]);
                journal.mouseScrolled(left + 10, top + 10, -2, 0);
                require((double) field(journal, "canvasTargetPanX") < 0, "Wide preview cannot pan");
                source = new QuestSourceScreen(journal, ID);
                game.setScreen(source);
                editor().setValue(MARKUP);
                invoke(source, "format", new Class<?>[0]);
                formatted = editor().getValue();
                require(!formatted.equals(MARKUP) && formatted.contains("\n  <rules>"), "Markup not formatted");
                require(formatted.contains("<content>  hello <b>world</b> \n </content>")
                        && formatted.contains("<script>if (a < b) { call(\"x\"); }</script>"), "Formatter changed content");
                source.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(editor().getValue().equals(MARKUP), "Undo format");
                source.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
                require(editor().getValue().equals(formatted), "Redo format");
                require(QuestSourceFormatter.format(formatted).equals(formatted), "Formatter is not idempotent");
                editor().setValue("<quest><rules>");
                invoke(source, "format", new Class<?>[0]);
                require(editor().getValue().equals("<quest><rules>"), "Invalid markup lost draft");
                require(field(source, "message") != null, "Invalid markup lacked error");
                var diagnostic = (QuestSourceFormatter.Diagnostic) field(source, "diagnostic");
                require(diagnostic != null && diagnostic.line() == 1, "Invalid markup lacked a line diagnostic");
                var repairable = QuestSourceFormatter.diagnose("<quest><rules/>");
                require(repairable != null && repairable.repair() != null
                                && QuestSourceFormatter.diagnose(repairable.repair()) == null,
                        "Safe missing-end-tag repair was not available");
                source.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(editor().getValue().equals(formatted), "Failed format modified undo history");
                String json = QuestSourceFormatter.format("{\"text\":\"<b>\",\"n\":2}");
                require(json.contains("\n  \"text\": \"<b>\""), "JSON formatting failed");
                game.options.guiScale().set(2);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 720, 540);
                game.resizeDisplay();
            }
            case 5 -> {
                capture(game, "source-editor-small");
                require(editor().getValue().equals(formatted), "Resize lost source");
                source.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(editor().getValue().equals("<quest><rules>"), "Resize lost redo history");
                source.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                for (var child : source.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                        require(widget.getX() >= 0 && widget.getRight() <= source.width, "Source toolbar out of bounds");
                    }
                }
                game.options.guiScale().set(1);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
            }
            case 6 -> {
                capture(game, "source-editor-wide");
                game.setScreen(journal);
                set(journal, "editMode", true);
                invoke(journal, "editElement", new Class<?>[]{String.class}, CONTROL);
                Object form = field(journal, "form");
                set(form, "propertyPage", "content");
                invoke(journal, "init", new Class<?>[0]);
                StyledButton expand = journal.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals("rich-editor"))
                        .findFirst().orElseThrow();
                expand.onPress();
                require(game.screen instanceof QuestTextEditorScreen, "Rich editor did not open");
                var rich = (QuestTextEditorScreen) game.screen;
                ((MultiLineEditBox) field(rich, "editor")).setValue("<b>Bold</b> <i>Italic</i>");
                rich.onClose();
                require(((String) field(form, "description")).equals("Extended canvas target"),
                        "Cancel changed text draft");
                game.setScreen(journal);
                expand = journal.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals("rich-editor"))
                        .findFirst().orElseThrow();
                expand.onPress();
                rich = (QuestTextEditorScreen) game.screen;
                String markup = "<b>Bold</b> <i>Italic</i><br><span style=\"color:#31af62\">Green</span>";
                ((MultiLineEditBox) field(rich, "editor")).setValue(markup);
            }
            case 7 -> {
                capture(game, "rich-text-expanded");
                var rich = (QuestTextEditorScreen) game.screen;
                String markup = ((MultiLineEditBox) field(rich, "editor")).getValue();
                ((MultiLineEditBox) field(rich, "editor")).setValue("temporary");
                rich.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(((MultiLineEditBox) field(rich, "editor")).getValue().equals(markup),
                        "Rich text undo failed");
                rich.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(((MultiLineEditBox) field(rich, "editor")).getValue().equals("temporary"),
                        "Rich text redo failed");
                rich.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                int previewX = (int) field(rich, "previewLeft") + 40;
                int previewY = (int) field(rich, "previewTop") + 40;
                rich.mouseScrolled(previewX, previewY, 0, 2);
                require((double) field(rich, "previewZoom") > 1, "Preview zoom failed");
                rich.mouseClicked(previewX, previewY, 0);
                rich.mouseDragged(previewX + 15, previewY + 12, 0, 15, 12);
                rich.mouseReleased(previewX + 15, previewY + 12, 0);
                require((double) field(rich, "previewPanX") != 0, "Preview pan failed");
                rich.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .filter(button -> button.styleId().equals("rich-preview-reset")).findFirst().orElseThrow().onPress();
                require(near((double) field(rich, "previewZoom"), 1)
                        && near((double) field(rich, "previewPanX"), 0), "Preview reset failed");
                rich.keyPressed(GLFW.GLFW_KEY_S, 0, GLFW.GLFW_MOD_CONTROL);
                Object form = field(journal, "form");
                require(((String) field(form, "description")).equals(markup), "Rich draft not saved");
                var parsed = QuestRichText.parse(markup, net.minecraft.network.chat.Style.EMPTY);
                require(parsed.getString().equals("Bold Italic\nGreen"), "Rich text parse");
                require(parsed.getSiblings().getFirst().getStyle().isBold(), "Bold style lost");
                require(parsed.getSiblings().stream().anyMatch(s -> s.getStyle().getColor() != null
                        && s.getStyle().getColor().getValue() == 0x31af62), "Inline color lost");
                require(QuestRichText.parse("Literal <unknown>text</unknown>",
                        net.minecraft.network.chat.Style.EMPTY).getString()
                        .equals("Literal <unknown>text</unknown>"), "Legacy text changed");
                invoke(journal, "saveForm", new Class<?>[0]);
                require(layout().elements().get(CONTROL).value().equals(markup), "Rich text not persisted");
                require(LayoutStore.restore(LayoutStore.snapshot(layout())).elements().get(CONTROL)
                        .value().equals(markup), "Rich text layout round-trip");
                var published = QuestPresentationDocuments.write("<quest id=\"rich-test\"/>",
                        new xtdpotato.xero_quest_journal.core.model.QuestPresentation(true, 395, 275,
                                java.util.List.of(new xtdpotato.xero_quest_journal.core.model.QuestPresentation.Control(
                                        "rich", "text", markup, 0, 0, 100, 20, "", Map.of(),
                                        xtdpotato.xero_quest_journal.core.model.TriggerGraph.empty()))));
                var document = org.jsoup.Jsoup.parse(published, "", org.jsoup.parser.Parser.xmlParser());
                require(QuestPresentationDocuments.read(document.selectFirst("visual")).controls().getFirst()
                        .value().equals(markup), "Rich text source round-trip");
                layout().setPosition(CONTROL, 40, 40);
                set(journal, "detailScroll", 0D);
                set(journal, "detailTargetScroll", 0D);
            }
            case 8 -> {
                capture(game, "rich-text-editor");
                libraryCheck = CompletableFuture.supplyAsync(() -> {
                    try {
                        var player = game.getSingleplayerServer().getPlayerList()
                                .getPlayer(game.player.getUUID());
                        String template = QuestStructureLibrary.worldTemplates(player).stream()
                                .filter(name -> {
                                    try {
                                        return !QuestStructureLibrary.previewTemplate(player, name)
                                                .getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty();
                                    } catch (Exception unsupported) {
                                        return false;
                                    }
                                }).findFirst().orElseThrow(() -> new IllegalStateException("No bounded world template"));
                        supportedTemplate = template;
                        String reference = QuestStructureLibrary.importTemplate(player, template);
                        require(!QuestStructures.preview(QuestStructures.read(reference))
                                .getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
                                "Imported structure has no blocks");
                        require(QuestStructureLibrary.delete(player, java.util.List.of(reference)) == 1,
                                "Imported structure could not be deleted");
                        return true;
                    } catch (Exception failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                    }
                }, game.getSingleplayerServer());
                game.setScreen(new QuestStructurePickerScreen(journal, ignored -> {}));
            }
            case 9 -> {
                if (!libraryCheck.isDone()) return false;
                require(libraryCheck.join(), "Structure library round-trip failed");
                capture(game, "structure-picker-library");
                ((StyledButton) game.screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(button -> button.styleId().equals("structure-world"))
                        .findFirst().orElseThrow()).onPress();
            }
            case 10 -> {
                @SuppressWarnings("unchecked") var world = (java.util.List<String>) field(game.screen, "world");
                if (world.isEmpty()) return false;
                require(world.contains(supportedTemplate), "Supported world structure missing from picker");
                var search = game.screen.children().stream().filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue(supportedTemplate);
                require(game.screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).anyMatch(button ->
                                button.styleId().startsWith("structure-preview-")),
                        "World structure preview button missing");
                game.screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast)
                        .filter(button -> button.styleId().equals("structure-preview-0"))
                        .findFirst().orElseThrow().onPress();
            }
            case 11 -> {
                if (!(game.screen instanceof QuestStructurePreviewScreen)) return false;
                require(ClientStructures.model("world:" + supportedTemplate) != null,
                        "World preview model not cached");
                capture(game, "structure-world-preview");
                return true;
            }
            default -> throw new IllegalStateException("Unexpected stage");
        }
        stage++;
        return false;
    }

    private static MultiLineEditBox editor() throws Exception {
        return (MultiLineEditBox) field(source, "editor");
    }

    private static LayoutEditorState layout() throws Exception {
        return (LayoutEditorState) field(journal, "layout");
    }

    private static void openCoordinate(String axis) throws Exception {
        int x = (int) invoke(journal, "coordinateLeft", new Class<?>[]{String.class}, axis) + 3;
        journal.mouseClicked(x, journal.height - 44, 0);
        journal.mouseClicked(x, journal.height - 44, 0);
        require(field(journal, "coordinateEditor") != null, "Double click did not open " + axis
                + "; selected=" + field(journal, "selectedLayoutId") + "; exists=" + layout().elements().containsKey(CONTROL)
                + "; edit=" + field(journal, "editMode") + "; blocked=" + journal.inputBlocked()
                + "; form=" + field(journal, "form"));
    }

    private static void edit(String axis, String value) throws Exception {
        openCoordinate(axis);
        ((EditBox) field(journal, "coordinateEditor")).setValue("");
        for (char character : value.toCharArray()) journal.charTyped(character, 0);
        journal.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        require(field(journal, "coordinateEditor") == null, "Coordinate editor did not commit " + axis);
    }

    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.00001;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void checkResponsiveScale(Minecraft game) throws Exception {
        int oldWidth = journal.width, oldHeight = journal.height;
        try {
            for (int[] size : new int[][]{{1920, 1080}, {1280, 720}, {640, 360}, {427, 240}, {900, 240}}) {
                journal.resize(game, size[0], size[1]);
                double x = (double) invoke(journal, "canvasScaleX", new Class<?>[0]);
                double y = (double) invoke(journal, "canvasScaleY", new Class<?>[0]);
                double expected = Math.max(0.75, Math.min(
                        (int) invoke(journal, "canvasWidth", new Class<?>[0]) / 395.0,
                        (int) invoke(journal, "canvasHeight", new Class<?>[0]) / 275.0));
                require(near(x, y) && near(x, expected),
                        "Editor canvas did not retain its uniform scrollable workspace scale");
            }
        } finally {
            journal.resize(game, oldWidth, oldHeight);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
