package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;

import java.nio.file.Path;
import java.util.Map;

/** Reproduces partially visible vanilla inputs crossing a fixed property-form header. */
final class ScrollViewportRegression {
    private static int stage;
    private static int ticks;
    private static boolean reducedMotion;
    private static EditBox first;

    static boolean tick(Minecraft game) throws Exception {
        if (++ticks < 8) return false;
        ticks = 0;
        switch (stage) {
            case 0 -> {
                reducedMotion = ClientPreferences.get().reducedMotion;
                ClientPreferences.get().reducedMotion = true;
                setup(game, 1280, 600);
            }
            case 1 -> {
                verify(game, "scroll-form-wide");
                setup(game, 640, 480);
            }
            case 2 -> {
                verify(game, "scroll-form-small");
                ClientPreferences.get().reducedMotion = reducedMotion;
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                return true;
            }
            default -> throw new IllegalStateException("Viewport phase " + stage);
        }
        stage++;
        return false;
    }

    private static void setup(Minecraft game, int width, int height) throws Exception {
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), width, height);
        game.resizeDisplay();
        var screen = new QuestScreen();
        game.setScreen(screen);
        invoke(screen, "openForm", new Class<?>[]{String.class, String.class}, "text", "normal");
        var spacing = screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(b -> b.styleId().equals("layout-page-spacing"))
                .findFirst().orElseThrow();
        spacing.onPress();
        @SuppressWarnings("unchecked") var widgets = (Map<net.minecraft.client.gui.components.AbstractWidget, Integer>)
                field(screen, "formWidgets");
        first = widgets.keySet().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
        int top = (int) invoke(screen, "formBodyTop", new Class<?>[0]);
        screen.mouseScrolled(first.getX() + 4, top + 4, 0, -0.5);
        settleForm(screen);
        require(first.getY() < top && first.getBottom() > top, "Fixture did not straddle viewport");
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), 0, 0);
    }

    private static void verify(Minecraft game, String name) throws Exception {
        var screen = game.screen;
        int top = (int) invoke(screen, "formBodyTop", new Class<?>[0]);
        int bottom = (int) invoke(screen, "formBodyBottom", new Class<?>[0]);
        // SuggestedEditBox reserves the rightmost 18 pixels for its menu button.
        int x = Math.max(first.getX() + 6, first.getRight() - 24);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            // Both samples are in the blank fixed padding, where input backgrounds used to leak.
            require(rgb(image, screen.width, screen.height, x, top - 2) == 0x292E36,
                    "Input background escaped above property viewport");
            require(rgb(image, screen.width, screen.height, x, bottom + 2) == 0x292E36,
                    "Input background escaped below property viewport");
            require(rgb(image, screen.width, screen.height, x, top + 2) == 0,
                    "Partially visible input was hidden instead of clipped");
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
        screen.mouseClicked(x, top - 2, 0);
        require(screen.getFocused() != first, "Clipped input accepted a header click");
        screen.mouseClicked(x, top + 2, 0);
        require(screen.getFocused() == first, "Visible input portion could not be focused");
        screen.mouseReleased(x, top + 2, 0);
        Object form = field(screen, "form");
        double before = (double) field(form, "scroll");
        screen.mouseScrolled(x, top - 2, 0, -1);
        require((double) field(form, "scroll") == before, "Header wheel scrolled form contents");
        screen.mouseScrolled(x, top + 2, 0, -100);
        settleForm(screen);
        require(!first.visible && screen.getFocused() != first, "Offscreen input retained focus");
    }

    private static void settleForm(Object screen) throws Exception {
        Object form = field(screen, "form"), motion = field(form, "motion");
        for (int i = 0; i < 20; i++) invoke(motion, "advance", new Class<?>[]{double.class}, .1);
        var scroll = form.getClass().getDeclaredField("scroll");
        scroll.setAccessible(true);
        scroll.setDouble(form, (double) invoke(motion, "position", new Class<?>[0]));
        invoke(screen, "updateFormViewport", new Class<?>[0]);
    }
    private static int rgb(com.mojang.blaze3d.platform.NativeImage image, int width, int height, int x, int y) {
        int pixel = image.getPixelRGBA((int) ((x + .5) * image.getWidth() / width),
                (int) ((y + .5) * image.getHeight() / height));
        return (pixel & 255) << 16 | pixel & 0xFF00 | pixel >>> 16 & 255;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
