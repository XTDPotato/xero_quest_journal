package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class SelectionRegression {
    private static int phase;
    private static int ticks;
    private static String group;
    private static String first;
    private static String second;
    private static String standalone;
    private static StyledButton cssTarget;

    public static boolean tick(Minecraft game, String guide) throws Exception {
        if (phase > 0 && game.screen instanceof AnimatedQuestScreen screen && screen.inputBlocked()) {
            ticks = 0;
            return false;
        }
        if (++ticks < 8) return false;
        ticks = 0;
        switch (phase) {
            case 0 -> {
                group = QuestRepository.groupOf(guide);
                var tasks = QuestRepository.all().stream().filter(q -> QuestRepository.groupOf(q.id()).equals(group)).toList();
                first = tasks.get(0).id();
                second = tasks.get(1).id();
                standalone = QuestRepository.all().stream().filter(q -> QuestRepository.groupOf(q.id()).isEmpty())
                        .findFirst().orElseThrow().id();
                game.setScreen(new QuestScreen());
                set(game.screen, "editMode", true);
                set(game.screen, "groupId", group);
                set(game.screen, "selectedId", first);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                GLFW.glfwSetCursorPos(game.getWindow().getWindow(), 1100, 750);
                game.resizeDisplay();
                game.screen.init(game, game.screen.width, game.screen.height);
                EditorRegression.reveal(game, first);
                EditorRegression.revealRow(game, "group:" + group);
                checkCss();
            }
            case 1 -> {
                checkSelected(game, first, group);
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    CategoryRegression.checkCentered(game, image);
                    assertBackground(game, image, find(game, "quest-" + first), 0xECE6DA);
                    assertBorder(game, image, find(game, "group-" + group), 0xF0EBDD);
                    assertThinGroupBorder(game, image, find(game, "group-" + group));
                    require(hasColor(game, image, find(game, "quest-" + first), 0x50586D), "Selected title not dark");
                    image.writeToFile(Path.of("..", "build", "verification", "26-selection-group-task.png"));
                }
                var task = find(game, "quest-" + first);
                GLFW.glfwSetCursorPos(game.getWindow().getWindow(),
                        (task.getX() + 30) * game.getWindow().getGuiScale(),
                        (task.getY() + 8) * game.getWindow().getGuiScale());
            }
            case 2 -> {
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    assertBackground(game, image, find(game, "quest-" + first), 0xECE6DA);
                }
                click(game, find(game, "quest-" + second));
                checkSelected(game, second, group);
                require(!find(game, "quest-" + first).selectionHighlighted(), "Old task kept white selection");
                game.screen.keyPressed(GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_MOD_CONTROL);
                click(game, find(game, "quest-" + first));
                game.screen.keyReleased(GLFW.GLFW_KEY_LEFT_CONTROL, 0, 0);
                checkSelected(game, second, group);
                require(!find(game, "quest-" + first).selectionHighlighted(), "Multi-selection replaced current task");
                click(game, find(game, "group-" + group));
                require(find(game, "group-" + group).selectionHighlighted(), "Folded active group lost highlight");
                var selectedGroup = find(game, "group-" + group);
                GLFW.glfwSetCursorPos(game.getWindow().getWindow(),
                        (selectedGroup.getX() + 30) * game.getWindow().getGuiScale(),
                        (selectedGroup.getY() + 8) * game.getWindow().getGuiScale());
            }
            case 3 -> {
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    assertBorder(game, image, find(game, "group-" + group), 0xF0EBDD);
                    assertThinGroupBorder(game, image, find(game, "group-" + group));
                    image.writeToFile(Path.of("..", "build", "verification", "27-selection-collapsed.png"));
                }
                set(game.screen, "groupId", "");
                game.screen.init(game, game.screen.width, game.screen.height);
                String other = QuestRepository.all().stream().filter(q -> !QuestRepository.groupOf(q.id()).isEmpty()
                        && !QuestRepository.groupOf(q.id()).equals(group)).findFirst().orElseThrow().id();
                EditorRegression.reveal(game, other);
                click(game, find(game, "quest-" + other));
                checkSelected(game, other, QuestRepository.groupOf(other));
                require(!find(game, "group-" + group).selectionHighlighted(), "Previous group still highlighted");
                EditorRegression.reveal(game, standalone);
                click(game, find(game, "quest-" + standalone));
                GLFW.glfwSetCursorPos(game.getWindow().getWindow(),
                        (game.screen.width - 2) * game.getWindow().getGuiScale(),
                        (game.screen.height - 2) * game.getWindow().getGuiScale());
            }
            case 4 -> {
                checkSelected(game, standalone, "");
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    assertBackground(game, image, find(game, "quest-" + standalone), 0xECE6DA);
                    image.writeToFile(Path.of("..", "build", "verification", "28-selection-standalone.png"));
                }
                cssTarget = find(game, "quest-" + standalone);
                cssTarget.extraCss(
                        ".quest-button:selected { background-color: #e8f2ff; color: #19202d; }");
            }
            case 5 -> {
                var current = find(game, "quest-" + standalone);
                if (current != cssTarget) {
                    cssTarget = current;
                    cssTarget.extraCss(
                            ".quest-button:selected { background-color: #e8f2ff; color: #19202d; }");
                    return false;
                }
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    assertBackground(game, image, find(game, "quest-" + standalone), 0xE8F2FF);
                }
                return true;
            }
            default -> throw new IllegalStateException("Selection phase " + phase);
        }
        phase++;
        return false;
    }

    private static void checkCss() {
        var css = ButtonStyleSheet.parse("""
                button { background-color: #123456; }
                .quest-button:selected { background-color: #abcdef; color: #102030; }
                .quest-button:selected:hover { border-color: #654321; }
                """);
        var normal = css.resolve("task", Set.of("quest-button"), true, false, false, false);
        require(normal.backgroundColor() == 0xFF123456, "Selected CSS leaked to normal rows");
        var selected = css.resolveSelectedFrom(normal, "task", Set.of("quest-button"), true, false, false, false);
        require(selected.backgroundColor() == 0xFFABCDEF && selected.textColor() == 0xFF102030
                && selected.borderColor() == 0xFF654321, "Selected CSS cascade failed");
        var groupStyle = css.resolveSelectedFrom(normal, "group", Set.of("group-button"), true, false, false, false);
        require(groupStyle.equals(normal), "Task selection CSS changed group");
    }

    private static void checkSelected(Minecraft game, String task, String groupId) {
        for (var button : rows(game)) {
            if (button.styleId().startsWith("quest-")) {
                require(button.selectionHighlighted() == button.styleId().equals("quest-" + task), "Task highlight mismatch");
            } else if (button.styleId().startsWith("group-")) {
                require(button.selectionHighlighted() == button.styleId().equals("group-" + groupId), "Group highlight mismatch");
            }
        }
    }

    private static void assertBackground(Minecraft game, NativeImage image, StyledButton button, int rgb) {
        int actual = colorAt(game, image, button.getX() + 18, button.getY() + 3);
        require(actual == rgb,
                "Unexpected selected background on " + button.styleId()
                        + ": expected=" + Integer.toHexString(rgb) + ", actual=" + Integer.toHexString(actual));
    }

    private static void assertBorder(Minecraft game, NativeImage image, StyledButton button, int rgb) {
        // The hover outline occupies the outer pixel; selection retains its inner border.
        require(colorAt(game, image, button.getX() + 1, button.getY() + 4) == rgb, "Active group border not highlighted");
    }

    private static void assertThinGroupBorder(Minecraft game, NativeImage image, StyledButton button) {
        require(colorAt(game, image, button.getX() + 8, button.getY() + 1) == 0xF0EBDD
                        && colorAt(game, image, button.getX() + 8, button.getY() + 2) != 0xF0EBDD,
                "Selected group border must be exactly two pixels");
    }

    private static boolean hasColor(Minecraft game, NativeImage image, StyledButton button, int rgb) {
        for (int x = button.getX() + 6; x < button.getRight() - 22; x++) {
            for (int y = button.getY() + 4; y < button.getBottom() - 2; y++) {
                int actual = colorAt(game, image, x, y);
                // Font shader multiplication can quantize each channel one level down.
                if (Math.abs((actual >>> 16 & 255) - (rgb >>> 16 & 255)) <= 1
                        && Math.abs((actual >>> 8 & 255) - (rgb >>> 8 & 255)) <= 1
                        && Math.abs((actual & 255) - (rgb & 255)) <= 1) return true;
            }
        }
        return false;
    }

    private static int colorAt(Minecraft game, NativeImage image, int x, int y) {
        int px = (int) ((x + 0.5) * image.getWidth() / game.screen.width);
        int py = (int) ((y + 0.5) * image.getHeight() / game.screen.height);
        int abgr = image.getPixelRGBA(px, py);
        return (abgr & 255) << 16 | abgr & 0xFF00 | abgr >>> 16 & 255;
    }

    private static List<StyledButton> rows(Minecraft game) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast).toList();
    }

    private static StyledButton find(Minecraft game, String id) {
        return rows(game).stream().filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }

    private static void click(Minecraft game, StyledButton button) {
        Screen screen = game.screen;
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "Selection click rejected");
        screen.mouseReleased(x, y, 0);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
