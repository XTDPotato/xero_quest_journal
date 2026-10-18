package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Count real tooltip passes so manual and deferred rendering cannot silently duplicate one another. */
final class JourneyToolbarRegression {
    private static int stage, wait;
    private static JourneyScreen journey;
    private static final Map<Long, Integer> tooltips = new HashMap<>();
    private static final Consumer<RenderTooltipEvent.Pre> listener = event -> {
        if (Minecraft.getInstance().screen == journey)
            tooltips.merge(ClientSmokeTest.renderedFrames, 1, Integer::sum);
    };

    static boolean tick(Minecraft game, JourneyScreen screen) throws Exception {
        if (--wait > 0) return false;
        wait = 8;
        switch (stage) {
            case 0 -> {
                journey = screen;
                game.options.guiScale().set(2);
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920); game.getWindow().setHeight(1080);
                game.setScreen(journey);
                game.resizeDisplay();
                if ((boolean) field(journey, "editing")) button("journey-edit-layout").onPress();
                NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Pre.class, listener);
                icon(game, "journey-edit-layout", "edit");
                hover(game, "journey-edit-layout");
            }
            case 1 -> {
                oneTooltip();
                capture(game, "journey-toolbar-edit-tooltip-zh-1920");
                click("journey-edit-layout");
                require(game.screen == journey && (boolean) field(journey, "editing"), "Edit toggle closed the screen");
                icon(game, "journey-edit-layout", "edit_exit");
                require(button("journey-edit-layout").getMessage().getString().equals(
                        net.minecraft.network.chat.Component.translatable(
                                "screen.xero_quest_journal.journey.finish_edit_layout").getString()),
                        "Editing tooltip still says enter editing");
                hover(game, "journey-edit-layout");
            }
            case 2 -> {
                oneTooltip();
                capture(game, "journey-toolbar-exit-tooltip-zh-1920");
                icon(game, "journey-tasks", "select_tasks");
                hover(game, "journey-tasks");
            }
            case 3 -> {
                oneTooltip();
                capture(game, "journey-toolbar-select-tooltip-zh-1920");
                click("journey-tasks");
                require(game.screen instanceof QuestTaskPickerScreen && (boolean) field(game.screen, "multiSelect"),
                        "Selection icon did not open the chapter multi-selector");
                game.screen.onClose();
            }
            case 4 -> {
                require(game.screen == journey, "Cancelling selection did not return to the journey");
                click("journey-edit-layout");
                require(game.screen == journey && !(boolean) field(journey, "editing"),
                        "Exit-edit icon closed the window instead of leaving editing");
                icon(game, "journey-edit-layout", "edit");
                NeoForge.EVENT_BUS.unregister(listener);
                tooltips.clear();
                return true;
            }
            default -> throw new AssertionError("Unknown journey toolbar stage");
        }
        stage++;
        return false;
    }

    private static void oneTooltip() {
        require(tooltips.size() >= 3 && tooltips.values().stream().allMatch(count -> count == 1),
                "Expected exactly one tooltip per hovered frame, got " + tooltips);
    }
    private static void hover(Minecraft game, String id) throws Exception {
        var button = button(id);
        double x = (button.getX() + button.getWidth() / 2.) * game.getWindow().getScreenWidth() / journey.width;
        double y = (button.getY() + button.getHeight() / 2.) * game.getWindow().getScreenHeight() / journey.height;
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), x, y);
        for (var entry : Map.of("xpos", x, "ypos", y).entrySet()) {
            var field = game.mouseHandler.getClass().getDeclaredField(entry.getKey());
            field.setAccessible(true); field.setDouble(game.mouseHandler, entry.getValue());
        }
        tooltips.clear();
    }
    private static void icon(Minecraft game, String id, String expected) throws Exception {
        require(field(button(id), "icon").equals(expected), "Incorrect toolbar glyph: " + id);
        var resource = game.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "xero_quest_journal", "textures/gui/navigation/" + expected + ".png")).orElseThrow();
        try (var stream = resource.open(); var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
            require(image.getWidth() == 32 && image.getHeight() == 32, "Toolbar glyph resolution differs");
        }
    }
    private static void click(String id) {
        var button = button(id);
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(journey.mouseClicked(x, y, 0), "Toolbar press was ignored: " + id);
        journey.mouseReleased(x, y, 0);
    }
    private static StyledButton button(String id) {
        return journey.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
