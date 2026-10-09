package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.mailbox.MailClipboardCodec;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MailBodyEditorRegression {
    private static final String ID = "mail_body_" + System.currentTimeMillis();
    private static final String RICH = "<b>Bold</b> <i>Italic</i> <u>Underlined</u> <s>Strike</s>"
            + "<br><span style=\"color:#31af62\">Green</span>\nSecond line";
    private static int stage, wait;
    private static MailComposeScreen composer;
    private static QuestTextEditorScreen rich;
    private static String baseline, beforeRich;
    private static CompletableFuture<Void> pending;
    private static final Path OUTPUT = Path.of("..", "build", "verification");

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        wait = 8;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                ClientPreferences.get().mailDraft = null;
                game.options.guiScale().set(1);
                resize(game, 1280, 800);
                var constructor = MailComposeScreen.class.getDeclaredConstructor(MailboxScreen.class);
                constructor.setAccessible(true);
                composer = constructor.newInstance(new MailboxScreen());
                game.setScreen(composer);
                baseline = body().getValue();
                require(baseline.isEmpty() && !button("mail-body-undo").active
                        && !button("mail-body-redo").active, "Initial history should be empty");
                composer.setFocused(body());
                require(composer.charTyped('A', 0), "Body typing was not handled");
                require(body().getValue().equals("A") && button("mail-body-undo").active,
                        "Typing did not update history");
                composer.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(body().getValue().equals(baseline) && button("mail-body-redo").active,
                        "Ctrl+Z did not undo typing");
                composer.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
                require(body().getValue().equals("A"), "Ctrl+Shift+Z did not redo typing");
                game.keyboardHandler.setClipboard(" pasted");
                // Vanilla's paste shortcut polls GLFW; exercise the same insertion path without OS input.
                var textField = MultiLineEditBox.class.getDeclaredField("textField");
                textField.setAccessible(true);
                ((net.minecraft.client.gui.components.MultilineTextField) textField.get(body()))
                        .insertText(game.keyboardHandler.getClipboard());
                require(body().getValue().equals("A pasted"), "Paste did not enter the body");
                button("mail-body-undo").onPress();
                require(body().getValue().equals("A"), "Undo button did not undo paste");
                composer.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(body().getValue().equals("A pasted"), "Ctrl+Y did not redo paste");
                composer.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                composer.charTyped('B', 0);
                require(body().getValue().equals("AB") && !button("mail-body-redo").active,
                        "New edit did not invalidate redo");
                ((EditBox) field(composer, "subject")).setValue("Rich mail");
                composer.setFocused((EditBox) field(composer, "subject"));
                composer.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(body().getValue().equals("AB"), "Subject shortcut changed the body");
                resize(game, 854, 480);
            }
            case 1 -> {
                require(composer.width == 854 && composer.height == 480, "Compact viewport was not applied");
                require(body().getValue().equals("AB"), "Resize lost the body");
                button("mail-body-undo").onPress();
                require(body().getValue().equals("A"), "Resize lost undo history");
                button("mail-body-redo").onPress();
                require(body().getValue().equals("AB"), "Resize lost redo history");
                checkToolbar();
                capture(game, "mail-body-inline-compact");
                beforeRich = body().getValue();
                button("mail-body-editor").onPress();
                require(game.screen instanceof QuestTextEditorScreen, "Mail did not use the task rich editor");
                rich = (QuestTextEditorScreen) game.screen;
                require(editor().getValue().equals(beforeRich), "Rich editor did not receive the body");
                for (String id : List.of("bold", "italic", "underline", "strike", "break", "color"))
                    require(button("layout.rich_" + id).visible, "Missing shared style tool: " + id);
                editor().setValue("Cancelled");
                rich.onClose();
                require(game.screen == composer && body().getValue().equals(beforeRich),
                        "Cancelling the rich editor changed the body");
                button("mail-body-editor").onPress();
                rich = (QuestTextEditorScreen) game.screen;
                editor().setValue("Text");
                button("layout.rich_bold").onPress();
                require(editor().getValue().equals("Text<b></b>"), "Shared bold tool did not work");
                editor().setValue(RICH);
                rich.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(editor().getValue().equals("Text<b></b>"), "Rich editor undo did not work");
                rich.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(editor().getValue().equals(RICH), "Rich editor redo did not work");
                editor().setValue("x".repeat(8192));
                button("layout.rich_bold").onPress();
                require(editor().getValue().length() == 8192, "Style tool exceeded the mail limit");
                editor().setValue(RICH);
            }
            case 2 -> {
                capture(game, "mail-body-rich-compact");
                button("rich-save").onPress();
                require(game.screen == composer && body().getValue().equals(RICH),
                        "Saving rich text did not refill the mail body");
                button("mail-body-undo").onPress();
                require(body().getValue().equals(beforeRich), "Rich save was not a single inline undo step");
                button("mail-body-undo").onPress();
                require(body().getValue().equals("A"), "Returning from rich editor lost earlier history");
                button("mail-body-redo").onPress();
                button("mail-body-redo").onPress();
                require(body().getValue().equals(RICH), "Inline redo lost rich styles");
                var content = QuestRichText.parse(RICH, Style.EMPTY);
                require(content.getString().equals("Bold Italic Underlined Strike\nGreen\nSecond line"),
                        "Styled mail parsed as raw markup");
                require(content.getSiblings().getFirst().getStyle().isBold()
                                && content.getSiblings().stream().anyMatch(s -> s.getStyle().isItalic())
                                && content.getSiblings().stream().anyMatch(s -> s.getStyle().isUnderlined())
                                && content.getSiblings().stream().anyMatch(s -> s.getStyle().isStrikethrough())
                                && content.getSiblings().stream().anyMatch(s -> s.getStyle().getColor() != null
                                && s.getStyle().getColor().getValue() == 0x31af62),
                        "Mail styles were lost");
                ((EditBox) field(composer, "recipient")).setValue(game.player.getGameProfile().getName());
                button("mail-compose-copy").onPress();
                var imported = MailClipboardCodec.parse(game.keyboardHandler.getClipboard());
                require(imported.draft().body().equals(RICH), "Portable mail lost rich markup");
                var server = game.getSingleplayerServer();
                var uuid = game.player.getUUID();
                pending = CompletableFuture.runAsync(() -> {
                    MailboxRuntime.send(server.getPlayerList().getPlayer(uuid),
                            ID, "Xero", "Rich mail", RICH, 0, List.of());
                }, server);
                resize(game, 1280, 800);
            }
            case 3 -> {
                capture(game, "mail-body-inline-desktop");
                require(body().getValue().equals(RICH), "Second resize lost rich body");
                checkToolbar();
                require(pending.isDone(), "Mail delivery did not finish");
                pending.join();
                ClientMailbox.request();
            }
            case 4 -> {
                if (ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals(ID))) return false;
                var mailbox = new MailboxScreen(null, ID);
                game.setScreen(mailbox);
                require(ClientMailbox.messages().stream().filter(mail -> mail.id().equals(ID)).findFirst()
                        .orElseThrow().body().equals(RICH), "Delivery lost rich markup");
                var bottom = MailboxScreen.class.getDeclaredMethod("bodyBottom", MailboxRuntime.View.class);
                bottom.setAccessible(true);
                var viewport = MailboxScreen.class.getDeclaredMethod("detailViewport");
                viewport.setAccessible(true);
                Object bounds = viewport.invoke(mailbox);
                var left = bounds.getClass().getDeclaredMethod("left");
                var right = bounds.getClass().getDeclaredMethod("right");
                left.setAccessible(true); right.setAccessible(true);
                int available = (int) right.invoke(bounds) - (int) left.invoke(bounds);
                int lines = game.font.split(QuestRichText.parse(RICH, Style.EMPTY),
                        Math.max(40, (int) (available / 1.2F))).size();
                int expected = 122 + lines * ((int) Math.ceil(game.font.lineHeight * 1.2F) + 2);
                require((int) bottom.invoke(mailbox, field(mailbox, "selected")) == expected,
                        "Mailbox scroll height did not use styled text");
            }
            case 5 -> {
                capture(game, "mail-body-styled-reading");
                game.setScreen(composer);
                button("mail-body-editor").onPress();
                rich = (QuestTextEditorScreen) game.screen;
            }
            case 6 -> {
                capture(game, "mail-body-rich-desktop");
                return true;
            }
            default -> throw new AssertionError("Unknown mail body stage " + stage);
        }
        stage++;
        return false;
    }

    private static void checkToolbar() throws Exception {
        for (String id : List.of("mail-body-undo", "mail-body-redo", "mail-body-editor")) {
            var b = button(id);
            require(b.getX() >= body().getX() && b.getRight() <= body().getRight()
                            && b.getBottom() <= body().getY() && b.getY() >= 132,
                    "Body toolbar overlaps adjacent controls");
        }
    }
    private static void resize(Minecraft game, int width, int height) {
        game.getWindow().setWindowed(width, height);
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), width, height);
        game.getWindow().setWidth(width);
        game.getWindow().setHeight(height);
        game.resizeDisplay();
    }
    private static MultiLineEditBox body() throws Exception { return (MultiLineEditBox) field(composer, "body"); }
    private static MultiLineEditBox editor() throws Exception { return (MultiLineEditBox) field(rich, "editor"); }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static StyledButton button(String id) {
        Screen screen = Minecraft.getInstance().screen;
        return screen.children().stream().filter(child -> child instanceof StyledButton b && b.styleId().equals(id))
                .map(child -> (StyledButton) child).findFirst().orElseThrow();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        java.nio.file.Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
