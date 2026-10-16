package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.client.AnimatedQuestScreen;
import xtdpotato.xero_quest_journal.core.QuestConfig;

final class PopupAnimationRegression {
    private static int stage;
    private static int wait;
    private static PopupScreen screen;
    private static double openingPosition;
    private static double beforeClosePosition;
    private static int closingBackgroundColor;
    private static int originalPopupDuration;
    private static MenuScreen menus;
    private static double menuOpeningPosition;
    private static xtdpotato.xero_quest_journal.client.QuestScreen journal;
    private static xtdpotato.xero_quest_journal.client.MailComposeScreen composer;
    private static FastModal fastModal;
    private static long contextStartFrame;
    private static boolean editorPreviewDone;
    private static Screen animationEditor;

    private PopupAnimationRegression() {}

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        if (stage == 0 && !editorPreviewDone) {
            if (animationEditor == null) {
                var type = Class.forName("xtdpotato.xero_quest_journal.client.PopupAnimationEditorScreen");
                var constructor = type.getDeclaredConstructor(Screen.class);
                constructor.setAccessible(true);
                animationEditor = (Screen) constructor.newInstance((Screen) null);
                game.setScreen(animationEditor);
                wait = 8;
                return false;
            }
            verifyEditorLayout(animationEditor);
            capture(game, "popup-animation-editor-redesigned");
            game.setScreen(null);
            animationEditor = null;
            editorPreviewDone = true;
            wait = 2;
            return false;
        }
        switch (stage) {
            case 0 -> {
                ClientPreferencesReset.reset();
                originalPopupDuration = xtdpotato.xero_quest_journal.client.ClientPreferences.get()
                        .popupAnimationMillis;
                var preferences = xtdpotato.xero_quest_journal.client.ClientPreferences.get();
                var defaultCurve = xtdpotato.xero_quest_journal.client.PopupAnimationProfile.defaults();
                var firstFrame = defaultCurve.sample(0);
                var lastFrame = defaultCurve.sample(1);
                require(firstFrame.scale() < 1 && firstFrame.opacity() == 0
                                && firstFrame.x() == 0 && firstFrame.y() == 0 && firstFrame.rotation() == 0
                                && lastFrame.scale() == 1 && lastFrame.opacity() == 1
                                && lastFrame.x() == 0 && lastFrame.y() == 0 && lastFrame.rotation() == 0,
                        "Default popup curve must use only scale and opacity");
                var oldDefault = new xtdpotato.xero_quest_journal.client.PopupAnimationProfile(java.util.List.of(
                        new xtdpotato.xero_quest_journal.client.PopupAnimationProfile.Keyframe(
                                0, .88, 0, 8, -2, 0, "smooth"),
                        new xtdpotato.xero_quest_journal.client.PopupAnimationProfile.Keyframe(
                                1, 1, 0, 0, 0, 1, "smooth")));
                var migrated = new xtdpotato.xero_quest_journal.client.ClientPreferences();
                migrated.popupAnimation = oldDefault;
                var validate = xtdpotato.xero_quest_journal.client.ClientPreferences.class
                        .getDeclaredMethod("validate");
                validate.setAccessible(true);
                validate.invoke(migrated);
                require(migrated.popupAnimation.equals(defaultCurve),
                        "Legacy default popup curve was not migrated");
                require(new xtdpotato.xero_quest_journal.client.ClientPreferences().popupAnimationMillis == 100,
                        "Popup default is not 100ms");
                preferences.popupAnimationMillis = 100;
                QuestConfig.defaults().uiAnimationMillis = 600;
                require(AnimatedQuestScreen.popupAnimationMillis() == 100,
                        "Slow page duration leaked into popup duration");
                preferences.reducedMotion = true;
                require(AnimatedQuestScreen.popupAnimationMillis() == 0, "Reduced motion animates popups");
                preferences.reducedMotion = false;
                xtdpotato.xero_quest_journal.client.ClientPreferences.get().popupAnimationMillis = 500;
                QuestConfig.defaults().uiAnimationMillis = 600;
                screen = new PopupScreen();
                game.setScreen(screen);
                wait = 20;
            }
            case 1 -> {
                require(screen.animationPosition() == 1, "Base screen entrance did not settle");
                screen.showPopup(true);
                require(screen.inputBlocked(), "Popup accepted input while opening");
                wait = 6;
            }
            case 2 -> {
                openingPosition = screen.popupAnimationPosition();
                require(openingPosition > 0 && openingPosition < 1, "Popup opening had no intermediate frame");
                capture(game, "popup-animation-open");
                wait = 5;
            }
            case 3 -> {
                beforeClosePosition = screen.popupAnimationPosition();
                require(beforeClosePosition > openingPosition,
                        "Popup opacity/scale progress did not advance");
                screen.showPopup(false);
                wait = 5;
            }
            case 4 -> {
                double closingPosition = screen.popupAnimationPosition();
                require(closingPosition > 0 && closingPosition < beforeClosePosition,
                        "Popup did not reverse from its current state");
                capture(game, "popup-animation-close");
                closingBackgroundColor = backgroundPixel(game);
                wait = 35;
            }
            case 5 -> {
                require(game.screen == screen && screen.popupAnimationPosition() == 0,
                        "Popup did not finish closing in place");
                require(!screen.inputBlocked(), "Popup kept blocking input after closing");
                int settledBackgroundColor = backgroundPixel(game);
                for (int shift : new int[] { 0, 8, 16 }) {
                    require(Math.abs((closingBackgroundColor >> shift & 255)
                            - (settledBackgroundColor >> shift & 255)) <= 5,
                            "Popup close dimmed the underlying background");
                }
                menus = new MenuScreen();
                game.setScreen(menus);
                wait = 20;
            }
            case 6 -> {
                menus.context = true;
                wait = 5;
            }
            case 7 -> {
                menuOpeningPosition = menus.menuAnimationPosition("context");
                require(menuOpeningPosition > 0 && menuOpeningPosition < 1,
                        "Context menu skipped scale/fade entry frames");
                require(menus.inputBlocked(), "Context menu was interactive during entry");
                capture(game, "context-menu-opening");
                wait = 20;
            }
            case 8 -> {
                menuOpeningPosition = menus.menuAnimationPosition("context");
                require(menuOpeningPosition == 1 && !menus.inputBlocked(),
                        "Context menu entry did not settle");
                menus.context = false;
                wait = 4;
            }
            case 9 -> {
                require(menus.menuAnimationPosition("context") > 0
                                && menus.menuAnimationPosition("context") < menuOpeningPosition,
                        "Context menu had no exit animation: " + menus.menuAnimationPosition("context"));
                capture(game, "context-menu-closing");
                closingBackgroundColor = backgroundPixel(game);
                wait = 20;
            }
            case 10 -> {
                require(menus.menuAnimationPosition("context") == 0 && !menus.inputBlocked(),
                        "Closed context menu left a blocking layer");
                require(backgroundPixel(game) == closingBackgroundColor,
                        "Context menu animation changed the background");
                menus.choice.onPress();
                wait = 5;
            }
            case 11 -> {
                require(menus.menuAnimationPosition("choice") > 0
                        && menus.menuAnimationPosition("choice") < 1, "Dropdown had no entry animation");
                capture(game, "choice-menu-opening");
                wait = 20;
            }
            case 12 -> {
                require(!menus.inputBlocked(), "Settled dropdown still blocks input");
                menus.mouseClicked(menus.width - 10, menus.height - 10, 0);
                require(!menus.choice.isOpen(), "Clicking outside did not dismiss dropdown");
                wait = 4;
            }
            case 13 -> {
                require(menus.menuAnimationPosition("choice") > 0
                        && menus.menuAnimationPosition("choice") < 1, "Dropdown had no exit animation");
                capture(game, "choice-menu-closing");
                wait = 20;
            }
            case 14 -> {
                require(!menus.inputBlocked(), "Dropdown dismissal left input blocked");
                menus.context = true;
                wait = 18;
            }
            case 15 -> {
                menus.context = false;
                menus.earlyReturn = true;
                wait = 4;
            }
            case 16 -> {
                require(menus.menuAnimationPosition("context") > 0,
                        "Changing menu owner discarded the close animation");
                wait = 20;
            }
            case 17 -> {
                require(menus.menuAnimationPosition("context") == 0 && !menus.inputBlocked(),
                        "Early-return owner left a stuck menu animation");
                xtdpotato.xero_quest_journal.client.ClientPreferences.get().reducedMotion = true;
                var task = xtdpotato.xero_quest_journal.client.ClientQuestDefinitions.all().stream()
                        .findFirst().orElseThrow();
                journal = xtdpotato.xero_quest_journal.client.QuestScreen.task(null, task.id());
                var edit = journal.getClass().getDeclaredField("editMode");
                edit.setAccessible(true);
                edit.setBoolean(journal, true);
                game.setScreen(journal);
                wait = 5;
            }
            case 18 -> {
                require(hierarchyIcon().equals("visual"), "Collapsed hierarchy has the wrong icon");
                capture(game, "hierarchy-collapsed");
                hierarchyButton().onPress();
                require(hierarchyIcon().equals("back"), "Expanded hierarchy icon did not change");
                wait = 5;
            }
            case 19 -> {
                capture(game, "hierarchy-expanded");
                hierarchyButton().onPress();
                require(hierarchyIcon().equals("visual"), "Hierarchy icon did not restore when collapsed");
                xtdpotato.xero_quest_journal.client.ClientPreferences.get().reducedMotion = false;
                var context = journal.getClass().getDeclaredMethod("contextMenu", int.class, int.class, String.class);
                context.setAccessible(true);
                var task = xtdpotato.xero_quest_journal.client.ClientQuestDefinitions.all().stream()
                        .findFirst().orElseThrow();
                context.invoke(journal, 60, 80, "quest:" + task.id());
                contextStartFrame = ClientSmokeTest.renderedFrames;
                wait = 5;
            }
            case 20 -> {
                if (ClientSmokeTest.renderedFrames < contextStartFrame + 2) return false;
                require(journal.menuAnimationPosition("context") > 0
                                && journal.menuAnimationPosition("context") < 1,
                        "Journal context menu skipped entry animation: progress="
                                + journal.menuAnimationPosition("context") + ", duration="
                                + AnimatedQuestScreen.popupAnimationMillis() + ", page="
                                + journal.animationPosition());
                capture(game, "journal-context-opening");
                wait = 20;
            }
            case 21 -> {
                require(!journal.inputBlocked(), "Journal context menu did not settle");
                journal.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                contextStartFrame = ClientSmokeTest.renderedFrames;
                wait = 4;
            }
            case 22 -> {
                if (ClientSmokeTest.renderedFrames < contextStartFrame + 2) return false;
                require(journal.menuAnimationPosition("context") > 0
                                && journal.menuAnimationPosition("context") < 1,
                        "Journal context menu skipped exit animation");
                capture(game, "journal-context-closing");
                wait = 20;
            }
            case 23 -> {
                require(!journal.inputBlocked() && journal.menuAnimationPosition("context") == 0,
                        "Journal context menu left input blocked");
                var constructor = xtdpotato.xero_quest_journal.client.MailComposeScreen.class.getDeclaredConstructor(
                        xtdpotato.xero_quest_journal.client.MailboxScreen.class);
                constructor.setAccessible(true);
                composer = constructor.newInstance(new xtdpotato.xero_quest_journal.client.MailboxScreen());
                game.setScreen(composer);
                var subject = composer.getClass().getDeclaredField("subject");
                subject.setAccessible(true);
                ((net.minecraft.client.gui.components.EditBox) subject.get(composer)).setValue("Draft");
                wait = 20;
            }
            case 24 -> {
                require(!composer.inputBlocked(), "Composer entry did not settle");
                composer.onClose();
                wait = 5;
            }
            case 25 -> {
                require(composer.menuAnimationPosition("confirm") > 0
                                && composer.menuAnimationPosition("confirm") < 1,
                        "Save draft confirmation skipped scale/fade entry");
                capture(game, "mail-draft-confirm-opening");
                wait = 20;
            }
            case 26 -> {
                require(composer.menuAnimationPosition("confirm") == 1 && !composer.inputBlocked(),
                        "Save draft confirmation entry did not settle");
                int w = Math.max(120, composer.width / 2), h = Math.max(112, composer.height / 2);
                int part = (w - 24) / 3;
                double x = (composer.width - w) / 2.0 + 8 + 2 * (part + 4) + part / 2.0;
                double y = (composer.height - h) / 2.0 + h - 22;
                composer.mouseClicked(x, y, 0);
                composer.mouseReleased(x, y, 0);
                wait = 4;
            }
            case 27 -> {
                require(composer.menuAnimationPosition("confirm") > 0
                                && composer.menuAnimationPosition("confirm") < 1,
                        "Save draft confirmation skipped shrink/fade exit");
                capture(game, "mail-draft-confirm-closing");
                wait = 20;
            }
            case 28 -> {
                require(composer.menuAnimationPosition("confirm") == 0 && !composer.inputBlocked()
                                && game.screen == composer,
                        "Save draft cancel left the editor blocked or closed it");
                QuestConfig.defaults().uiAnimationMillis = 2000;
                xtdpotato.xero_quest_journal.client.ClientPreferences.get().popupAnimationMillis = 100;
                fastModal = new FastModal();
                game.setScreen(fastModal);
                wait = 5;
            }
            case 29 -> {
                require(fastModal.animationPosition() == 1 && !fastModal.inputBlocked(),
                        "100ms dialog opening inherited slow page timing");
                fastModal.onClose();
                wait = 5;
            }
            case 30 -> {
                require(game.screen == null, "100ms dialog closing inherited slow page timing");
                QuestConfig.defaults().uiAnimationMillis = 220;
                xtdpotato.xero_quest_journal.client.ClientPreferences.get().popupAnimationMillis =
                        originalPopupDuration;
                return true;
            }
            default -> throw new AssertionError("Unknown popup animation stage " + stage);
        }
        stage++;
        return false;
    }

    private static xtdpotato.xero_quest_journal.client.StyledButton hierarchyButton() {
        return journal.children().stream().filter(child ->
                        child instanceof xtdpotato.xero_quest_journal.client.StyledButton b
                                && b.styleId().equals("hierarchy"))
                .map(child -> (xtdpotato.xero_quest_journal.client.StyledButton) child)
                .findFirst().orElseThrow();
    }

    private static String hierarchyIcon() throws Exception {
        var field = xtdpotato.xero_quest_journal.client.EditorIconButton.class.getDeclaredField("icon");
        field.setAccessible(true);
        return (String) field.get(hierarchyButton());
    }

    private static final class MenuScreen extends AnimatedQuestScreen {
        boolean context, earlyReturn;
        xtdpotato.xero_quest_journal.client.StyledChoiceButton<String> choice;

        MenuScreen() { super(Component.literal("Menu animation test")); }

        @Override protected void init() {
            clearWidgets();
            choice = addRenderableWidget(new xtdpotato.xero_quest_journal.client.StyledChoiceButton<>(
                    width / 2 - 80, 40, 160, 20, Component.literal("Selector"),
                    java.util.List.of("First", "Second", "Third"), "First",
                    Component::literal, value -> {}, xtdpotato.xero_quest_journal.client.ButtonStyleSheet.load(),
                    "animation-choice"));
        }

        @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
            g.fill(0, 0, width, height, 0xFF203038);
            super.renderContents(g, x, y, delta);
            if (earlyReturn) return;
            renderAnimatedMenu(g, "context", context ? "context" : null,
                    viewport(width / 2 - 90, height / 2 - 48, width / 2 + 90, height / 2 + 48),
                    () -> {
                        g.fill(width / 2 - 90, height / 2 - 48, width / 2 + 90, height / 2 + 48, 0xFF80B89C);
                        g.drawCenteredString(font, "Context menu", width / 2, height / 2, 0xFFFFFFFF);
                    });
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class FastModal extends AnimatedQuestScreen {
        FastModal() { super(Component.literal("100ms dialog")); }
        @Override protected boolean isModalScreen() { return true; }
        @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
            g.fill(width / 2 - 80, height / 2 - 40, width / 2 + 80, height / 2 + 40, 0xFF58657A);
        }
        @Override public void onClose() { closeTo(null); }
        @Override public boolean isPauseScreen() { return false; }
    }

    private static void capture(Minecraft game, String name) throws Exception {
        java.nio.file.Path path = java.nio.file.Path.of("..", "build", "verification", name + ".png");
        java.nio.file.Files.createDirectories(path.getParent());
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(path);
        }
    }

    private static void verifyEditorLayout(Screen editor) throws Exception {
        require(editor.getTitle().getString().startsWith("Popup Menu")
                        || editor.getTitle().getString().startsWith("弹出菜单"),
                "Animation editor title is untranslated: " + editor.getTitle().getString());
        require(editor instanceof AnimatedQuestScreen animated && animated.showsTrackingHudBehind(),
                "Animation editor does not preserve the tracking HUD");
        var scrimMethod = AnimatedQuestScreen.class.getDeclaredMethod("modalScrimBounds");
        scrimMethod.setAccessible(true);
        Object scrim = scrimMethod.invoke(editor);
        var left = scrim.getClass().getDeclaredMethod("left");
        left.setAccessible(true);
        require((int) left.invoke(scrim) > 0, "Animation editor scrim covers the tracking column");

        var widgets = editor.children().stream()
                .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).toList();
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            require(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= editor.width
                            && a.getBottom() <= editor.height,
                    "Animation editor control exceeds the viewport: " + i);
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                boolean intersects = a.getX() < b.getRight() && a.getRight() > b.getX()
                        && a.getY() < b.getBottom() && a.getBottom() > b.getY();
                require(!intersects, "Animation editor controls overlap: " + i + " / " + j);
            }
        }
    }

    private static int backgroundPixel(Minecraft game) {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            return image.getPixelRGBA(10, 10);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class PopupScreen extends AnimatedQuestScreen {
        private boolean popup;

        private PopupScreen() {
            super(Component.literal("Popup animation test"));
        }

        private void showPopup(boolean value) {
            popup = value;
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            backdrop(graphics, 0xE0202630);
            graphics.fill(0, 0, width, height, 0xE0202630);
            graphics.drawCenteredString(font, "Underlying screen", width / 2, height / 2 + 82, 0xFFFFFFFF);
            if (popup) {
                int left = width / 2 - 90;
                int top = height / 2 - 48;
                graphics.fill(left, top, left + 180, top + 96, 0xFF58657A);
                graphics.drawCenteredString(font, "Popup", width / 2, height / 2 - 4, 0xFFFFFFFF);
            }
        }

        @Override protected Object popupKey() { return popup ? Boolean.TRUE : null; }
        @Override protected Viewport popupBounds() {
            return viewport(width / 2 - 90, height / 2 - 48,
                    width / 2 + 90, height / 2 + 48);
        }
        @Override public void renderBackground(GuiGraphics graphics, int x, int y, float delta) {}
        @Override public void onClose() { showPopup(false); }
        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class ClientPreferencesReset {
        private static void reset() {
            xtdpotato.xero_quest_journal.client.ClientPreferences.get().reducedMotion = false;
        }
    }
}
