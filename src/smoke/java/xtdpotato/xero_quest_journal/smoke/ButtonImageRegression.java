package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class ButtonImageRegression {
    private static final String TASK = "button_images_" + System.currentTimeMillis();
    private static final String ID = TASK + "_button";
    private static final String RED = "minecraft:textures/block/redstone_block.png";
    private static final String GREEN = "minecraft:textures/block/emerald_block.png";
    private static final String BLUE = "minecraft:textures/block/diamond_block.png";
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static QuestScreen journal;
    private static CompletableFuture<Void> pending;
    private static int stage, wait;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        wait = 12;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                game.reloadResourcePacks();
                game.options.guiScale().set(2);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            TASK, "Button images", "", "", "", "guide", "", 1)).successful(), "Task creation");
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone() || ClientQuestProgress.view(TASK) == null) return false;
                pending.join();
                journal = QuestScreen.task(null, TASK);
                set(journal, "editMode", true);
                set(journal, "searchQuery", TASK);
                game.setScreen(journal);
                layout().elements().put(ID, new LayoutEditorState.Element(ID, TASK, "button", "Button text", 190, 44,
                        Map.of("background", "#33443C", "shape", "rounded", "cornerRadius", "6",
                                "borderColor", "#809D8B", "borderWidth", "1", "paddingLeft", "5", "paddingRight", "5")));
                layout().setPosition(ID, 30, 100);
                call(journal, "editElement", new Class<?>[]{String.class}, ID);
                set(form(), "propertyPage", "content");
                call(journal, "init", new Class<?>[0]);
                button("button-image-choose").onPress();
                require(field(journal, "iconPicker") != null, "Image picker not opened");
                call(journal, "applyPickedResource", new Class<?>[]{String.class}, RED);
                require(content().get("buttonImage").equals(RED), "Picker did not update form");
                content().put("buttonImageWidth", "22");
                content().put("buttonImageHeight", "22");
                content().put("buttonImageGap", "8");
                content().put("buttonImageHover", GREEN);
                content().put("buttonImagePressed", BLUE);
                content().put("textColorHover", "#B6FFD2");
                content().put("textColorPressed", "#B4ECFF");
                set(form(), "propertyPage", "appearance");
                set(form(), "appearanceState", "hover");
                call(journal, "init", new Class<?>[0]);
            }
            case 2 -> {
                capture(game, "button-image-appearance");
                button("button-image-choose").onPress();
                call(journal, "applyPickedResource", new Class<?>[]{String.class}, "");
                require(content().get("buttonImageHover").equals("none"), "Clear must hide state image");
                button("button-image-inherit").onPress();
                require(content().get("buttonImageHover").equals("inherit"), "Inheritance reset");
                content().put("buttonImageHover", GREEN);
                content().put("buttonImageWidth", "0");
                call(journal, "saveElementProperties", new Class<?>[0]);
                require(field(journal, "form") != null, "Accepted invalid width");
                content().put("buttonImageWidth", "22");
                call(journal, "saveElementProperties", new Class<?>[0]);
                require(field(journal, "form") == null, "Form failed to save: " + field(journal, "error"));
                require(control().attribute("buttonImagePressed", "").equals(BLUE), "Pressed image lost");
                codecs(control());
                geometry(game);
                for (String key : List.of("buttonImage", "buttonImageHover", "buttonImagePressed", "buttonImageWidth",
                        "buttonImageHeight", "buttonImageGap", "buttonImagePosition", "textColorHover", "textColorPressed")) {
                    require(ControlProperties.forKind("button").contains(key), "Missing trigger property " + key);
                    require(!ControlProperties.forKind("image").contains(key), "Button property leaked to image");
                    String language = "screen.xero_quest_journal.graph.property." + key;
                    require(!Component.translatable(language).getString().equals(language), "Missing language " + key);
                }
            }
            case 3 -> {
                Object publisher = field(journal, "presentationWriter");
                if ((boolean) call(publisher, "busy", new Class<?>[0])) return false;
                require(QuestRepository.source(TASK).contains("buttonImagePressed"), "Source publication lost image properties");
                call(journal, "editElement", new Class<?>[]{String.class}, ID);
                require(content().get("buttonImageHover").equals(GREEN)
                        && content().get("buttonImageWidth").equals("22"), "Reopened form lost properties");
                call(journal, "closeForm", new Class<?>[0]);
                set(journal, "editMode", false);
                call(journal, "init", new Class<?>[0]);
                move(game, 4, 4);
            }
            case 4 -> {
                assertImage(game, "normal", 0);
                int[] xy = position();
                move(game, xy[0] + 10, xy[1] + 10);
            }
            case 5 -> {
                assertImage(game, "hover", 1);
                int[] xy = position();
                journal.mouseClicked(xy[0] + 10, xy[1] + 10, 0);
                require(ID.equals(field(journal, "pressedControlId")), "Button press was not captured");
            }
            case 6 -> {
                assertImage(game, "pressed", 2);
                journal.mouseReleased(4, 4, 0);
                require(field(journal, "pressedControlId") == null, "Release outside left button pressed");
            }
            case 7 -> {
                assertImage(game, "released-hover", 1);
                move(game, 4, 4);
            }
            case 8 -> {
                assertImage(game, "released-normal", 0);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 720, 540);
                game.resizeDisplay();
                move(game, 4, 4);
            }
            case 9 -> {
                assertImage(game, "small-normal", 0);
                set(journal, "editMode", true);
                call(journal, "editElement", new Class<?>[]{String.class}, ID);
                set(form(), "propertyPage", "content");
                set(form(), "scroll", 290.0);
                call(journal, "init", new Class<?>[0]);
            }
            case 10 -> {
                capture(game, "button-image-content-small");
                return true;
            }
        }
        stage++;
        return false;
    }

    private static void geometry(Minecraft game) {
        var font = game.font;
        for (String position : List.of("left", "right", "top", "bottom", "only")) {
            for (double scale : List.of(.35, .75, 1.0, 1.25, 2.0)) {
                for (int w : List.of(0, 1, 12, 40, 180)) for (int h : List.of(0, 1, 12, 40, 80)) {
                    var attributes = new LinkedHashMap<>(Map.of("buttonImageHover", GREEN, "buttonImagePosition", position));
                    var e = new LayoutEditorState.Element("test", TASK, "button", "Long button text", w, h, attributes);
                    var content = ButtonControlContent.layout(e, font, Component.literal(e.value()), (float) scale, scale, w, h);
                    for (var box : List.of(content.image(), content.text()))
                        require(box.x() >= 0 && box.y() >= 0 && box.width() >= 0 && box.height() >= 0
                                && box.x() + box.width() <= w && box.y() + box.height() <= h,
                                "Out of bounds " + position + ": " + content);
                    if (content.text().width() > 0 && content.text().height() > 0) {
                        var a = content.image(); var b = content.text();
                        require(a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x()
                                || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y(), "Image overlaps text");
                    }
                }
            }
        }
        var e = new LayoutEditorState.Element("test", TASK, "button", "Label", -2, -2,
                Map.of("buttonImage", RED, "buttonImageHover", "inherit", "buttonImagePressed", "none"));
        require(ButtonControlContent.image(e, "Hover").equals(RED), "Hover inheritance");
        require(ButtonControlContent.image(e, "Pressed").equals("none"), "Explicit hidden image");
        require(ButtonControlContent.hasImage(e), "Stable image slot missing");
        var size = ButtonControlContent.naturalSize(e, font, Component.literal("Label"), 1, 10000);
        require(size.width() == 20 + font.width("Label") && size.height() == 16, "Natural image + label size");
        QuestEventGraphs.validateProperty("buttonImage", RED);
        QuestEventGraphs.validateProperty("buttonImagePressed", "none");
        QuestEventGraphs.validateProperty("textColorHover", "inherit");
    }

    private static void codecs(LayoutEditorState.Element e) {
        var presentation = new QuestPresentation(true, 395, 275, List.of(new QuestPresentation.Control(
                ID, "button", e.value(), 30, 100, e.width(), e.height(), "", e.attributes(), TriggerGraph.empty())));
        for (String source : List.of("<quest id=\"test\"><rules/></quest>", "<html><body><quest id=\"test\"/></body></html>")) {
            String result = QuestPresentationDocuments.write(source, presentation);
            var visual = org.jsoup.Jsoup.parse(result, "", org.jsoup.parser.Parser.xmlParser()).selectFirst("visual");
            require(QuestPresentationDocuments.read(visual).equals(presentation), "Markup round-trip lost button state");
        }
        var result = com.google.gson.JsonParser.parseString(QuestPresentationDocuments.write("{\"id\":\"test\"}", presentation));
        require(new Gson().fromJson(result.getAsJsonObject().get("presentation"), QuestPresentation.class).equals(presentation),
                "JSON round-trip lost button state");
    }

    private static void assertImage(Minecraft game, String name, int channel) throws Exception {
        int[] xy = position();
        var e = control();
        double scale = (double) call(journal, "canvasRenderScale", new Class<?>[0]);
        int w = (int) call(journal, "displayWidth", new Class<?>[]{LayoutEditorState.Element.class}, e);
        int h = (int) call(journal, "displayHeight", new Class<?>[]{LayoutEditorState.Element.class}, e);
        int padding = (int) Math.round(5 * scale);
        var box = ButtonControlContent.layout(e, game.font, Component.literal(e.value()), (float) scale, scale, w - padding * 2, h).image();
        double pixelScale = game.getWindow().getGuiScale();
        int px = (int) ((xy[0] + padding + box.x() + box.width() / 2.0) * pixelScale);
        int py = (int) ((xy[1] + box.y() + box.height() / 2.0) * pixelScale);
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve("button-image-" + name + ".png"));
            int color = image.getPixelRGBA(px, py);
            int r = color & 255, g = color >>> 8 & 255, b = color >>> 16 & 255;
            require(channel == 0 ? r > g + 30 && r > b + 30
                    : channel == 1 ? g > r + 30 && g > b + 20 : b > r + 30 && g > r + 30,
                    "Wrong image pixels for " + name + ": " + r + "," + g + "," + b + " at " + px + "," + py);
        }
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(OUTPUT.resolve(name + ".png")); }
    }
    private static void move(Minecraft game, double x, double y) {
        // The smoke-test window can be unfocused, so GLFW cursor callbacks are
        // not guaranteed to update Minecraft's accumulated mouse position.
        try {
            set(game.mouseHandler, "xpos",
                    x * game.getWindow().getScreenWidth() / (double) game.screen.width);
            set(game.mouseHandler, "ypos",
                    y * game.getWindow().getScreenHeight() / (double) game.screen.height);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to move the smoke-test cursor", error);
        }
    }
    private static int[] position() throws Exception {
        return (int[]) call(journal, "elementDisplayPosition", new Class<?>[]{LayoutEditorState.Element.class}, control());
    }
    private static LayoutEditorState layout() throws Exception { return (LayoutEditorState) field(journal, "layout"); }
    private static LayoutEditorState.Element control() throws Exception { return layout().elements().get(ID); }
    private static Object form() throws Exception { return field(journal, "form"); }
    @SuppressWarnings("unchecked")
    private static Map<String, String> content() throws Exception { return (Map<String, String>) field(form(), "buttonContent"); }
    private static StyledButton button(String id) {
        return journal.children().stream().filter(e -> e instanceof StyledButton b && b.styleId().equals(id))
                .map(e -> (StyledButton) e).findFirst().orElseThrow(() -> new AssertionError("Missing button " + id));
    }
    private static Object field(Object object, String key) throws Exception {
        var field = object.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String key, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(key); field.setAccessible(true); field.set(object, value);
    }
    private static Object call(Object object, String key, Class<?>[] types, Object... args) throws Exception {
        var method = object.getClass().getDeclaredMethod(key, types); method.setAccessible(true); return method.invoke(object, args);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Button image stage " + stage + ": " + message);
    }
}
