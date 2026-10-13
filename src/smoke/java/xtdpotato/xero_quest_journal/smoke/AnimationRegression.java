package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class AnimationRegression {
    private static int stage;
    private static int wait;
    private static QuestScreen journal;
    private static QuestTemplateEditorScreen editor;
    private static double closingPosition;
    private static final String ID = "animation_template_" + System.currentTimeMillis();

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                if (game.screen != null) return false;
                UiMotion motion = new UiMotion(false, 0);
                motion.target(true, 0, 1000);
                require(Math.abs(motion.advance(400_000_000L, 1000) - .4) < .00001, "Motion not time-based");
                motion.target(false, 400_000_000L, 1000);
                require(Math.abs(motion.position() - .4) < .00001, "Reverse jumped");
                require(Math.abs(motion.advance(500_000_000L, 1000) - .3) < .00001, "Reverse direction wrong");
                motion.target(true, 500_000_000L, 0);
                require(motion.position() == 1 && motion.settled(), "Zero-duration failed");
                ClientPreferences.get().reducedMotion = false;
                QuestConfig.defaults().uiAnimationMillis = 1000;
                journal = new QuestScreen();
                game.setScreen(journal);
                require(journal.inputBlocked(), "First unrendered frame accepts input");
                wait = 6;
            }
            case 1 -> {
                require(journal.animationPosition() > 0 && journal.animationPosition() < 1, "No visible entry frames");
                var click = new ScreenEvent.MouseButtonPressed.Pre(journal, 30, 30, 0);
                NeoForge.EVENT_BUS.post(click);
                require(click.isCanceled(), "Animated hit coordinates accepted");
                capture(game, "animation-entry");
                journal.onClose();
                require(journal.isClosing() && game.screen == journal, "Close did not retain screen");
                wait = 1;
            }
            case 2 -> {
                closingPosition = journal.animationPosition();
                journal.reopen();
                require(Math.abs(journal.animationPosition() - closingPosition) < .15, "Reopen restarted animation");
                wait = 24;
            }
            case 3 -> {
                require(!journal.isClosing() && journal.animationPosition() == 1, "Reopen never settled");
                verifyListOverscrollRelease();
                capture(game, "animation-open");
                var click = new ScreenEvent.MouseButtonPressed.Pre(journal, 30, 30, 0);
                NeoForge.EVENT_BUS.post(click);
                require(!click.isCanceled(), "Stable page input blocked");
                journal.onClose();
                wait = 5;
            }
            case 4 -> {
                require(journal.isClosing() && game.screen == journal, "Closing finished too early");
                var click = new ScreenEvent.MouseButtonPressed.Pre(journal, 30, 30, 0);
                NeoForge.EVENT_BUS.post(click);
                require(click.isCanceled(), "Closing click-through");
                var key = new ScreenEvent.KeyPressed.Pre(journal, 257, 0, 0);
                NeoForge.EVENT_BUS.post(key);
                require(key.isCanceled(), "Closing keyboard activated business action");
                capture(game, "animation-close");
                wait = 22;
            }
            case 5 -> {
                require(game.screen == null, "Closing did not detach");
                QuestConfig.defaults().uiAnimationMillis = 220;
                ClientPreferences.get().defaultView = "overview";
                require(ClientPreferences.get().save(), "Personal settings save failed");
                Screen defaultPage = ClientPreferences.openDefault(null);
                require((boolean) field(defaultPage, "overviewMode"), "Personal default ignored");
                game.setScreen(new QuestConfigScreen(journal));
                wait = 8;
            }
            case 6 -> {
                capture(game, "animation-settings");
                game.screen.onClose();
                wait = 8;
            }
            case 7 -> {
                require(game.screen == journal && journal.animationPosition() == 1, "Parent entrance restarted");
                ClientPreferences.get().reducedMotion = true;
                journal.onClose();
                require(game.screen == null, "Reduced-motion close delayed");
                ClientPreferences.get().reducedMotion = false;
                ClientPreferences.get().defaultView = "journal";
                ClientPreferences.get().save();
                XeroTips.clear();
                XeroTips.show(Component.literal("Long notification text must wrap inside the viewport. ".repeat(7)));
                XeroTips.show(Component.literal("Second queued notification"));
                require(XeroTips.queuedCount() == 1 && XeroTips.history().size() == 2, "Tips replaced instead of queued");
                wait = 12;
            }
            case 8 -> {
                capture(game, "animation-tip-wrap");
                wait = 66;
            }
            case 9 -> {
                require(XeroTips.currentMessage() != null
                        && XeroTips.currentMessage().getString().equals("Second queued notification"), "Tip queue never advanced");
                XeroTips.clear();
                editor = new QuestTemplateEditorScreen(journal, null);
                game.setScreen(editor);
                @SuppressWarnings("unchecked") var values = (Map<String, String>) field(editor, "values");
                values.put("id", ID);
                values.put("name", "Animation template");
                values.put("icon", "minecraft:book");
                var updateRules = QuestTemplateEditorScreen.class.getDeclaredMethod(
                        "updateTemplateRules", List.class, List.class);
                updateRules.setAccessible(true);
                updateRules.invoke(editor, List.of(), List.of(
                        new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward("item", "minecraft:diamond", 3)));
                values.put("reward_mode", "manual");
                values.put("css", "button { color:#abcdef; }");
                game.setScreen(editor);
                editor.mouseScrolled(editor.width / 2, 100, 0, -6);
                wait = 8;
            }
            case 10 -> {
                capture(game, "animation-template-properties");
                find(game, "template-pick-icon").onPress();
                wait = 8;
            }
            case 11 -> {
                require(game.screen instanceof QuestScreen, "Template picker not opened");
                var fields = game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                fields.getLast().setValue("minecraft:textures/item/diamond.png");
                find(game, "icon-apply").onPress();
                require(game.screen == editor, "Picker did not restore template editor");
                @SuppressWarnings("unchecked") var values = (Map<String, String>) field(editor, "values");
                require(values.get("icon").equals("minecraft:textures/item/diamond.png")
                        && ((List<?>) field(editor, "defaultRewards")).equals(List.of(
                        new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward("item", "minecraft:diamond", 3))),
                        "Picker lost template draft");
                find(game, "template-save").onPress();
                wait = 10;
            }
            case 12 -> {
                if (game.screen == editor) {
                    require(((String) field(editor, "error")).isBlank(), "Template save error");
                    return false;
                }
                QuestTemplate value = QuestTemplateStore.all().stream().filter(t -> t.id().equals(ID)).findFirst().orElseThrow();
                require(value.defaultRewards().equals(List.of(
                        new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward("item", "minecraft:diamond", 3)))
                        && value.rewardMode().equals("manual") && value.css().contains("#abcdef")
                        && value.icon().equals("minecraft:textures/item/diamond.png"), "Template fields did not persist");
                game.options.guiScale().set(3);
                game.resizeDisplay();
                game.setScreen(new QuestConfigScreen(journal));
                wait = 10;
            }
            case 13 -> {
                capture(game, "animation-settings-compact");
                for (var child : game.screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible)
                        require(w.getY() >= 0 && w.getBottom() <= game.screen.height, "Settings outside short window");
                }
                game.options.guiScale().set(2);
                game.resizeDisplay();
                game.setScreen(new MailboxScreen(journal));
                wait = 10;
            }
            case 14 -> {
                QuestConfig.defaults().uiAnimationMillis = 1000;
                find(game, "mail-delete_read").onPress();
                wait = 6;
            }
            case 15 -> {
                var page = (AnimatedQuestScreen) game.screen;
                require(page.animationPosition() == 1 && page.inputBlocked(), "Popup restarted base or lacked animation gate");
                require(page.popupAnimationPosition() > 0 && page.popupAnimationPosition() < 1,
                        "Popup did not animate from its opening state");
                capture(game, "animation-mail-popup-entry");
                wait = 20;
            }
            case 16 -> {
                var page = (AnimatedQuestScreen) game.screen;
                require(!page.inputBlocked(), "Popup opening never settled");
                find(game, "mail-cancel").onPress();
                wait = 5;
            }
            case 17 -> {
                var page = (AnimatedQuestScreen) game.screen;
                require(page.animationPosition() == 1 && page.inputBlocked(),
                        "Mailbox modal did not block input while closing");
                require(page.popupAnimationPosition() > 0 && page.popupAnimationPosition() < 1,
                        "Popup did not reverse through its closing animation");
                require(!(boolean) field(page, "confirmDelete"), "Cancel changed no modal state");
                capture(game, "animation-mail-popup-close");
                wait = 22;
            }
            case 18 -> {
                require(!((AnimatedQuestScreen) game.screen).inputBlocked(), "Popup closing did not finish");
                QuestConfig.defaults().uiAnimationMillis = 220;
                QuestTemplate value = QuestTemplateStore.all().stream().filter(t -> t.id().equals(ID)).findFirst().orElseThrow();
                editor = new QuestTemplateEditorScreen(journal, value);
                game.setScreen(editor);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 854, 480);
                game.resizeDisplay();
                wait = 8;
            }
            case 19 -> {
                int barX = (int) invoke(editor, "scrollbarX") + 1;
                require(!editor.inputBlocked(), "Template entry animation has not settled");
                require(editor.mouseClicked(barX, (int) invoke(editor, "bottom") - 2, 0),
                        "Template scrollbar track did not respond: x=" + barX + ", max="
                                + invoke(editor, "maxScroll") + ", height=" + editor.height);
                require((int) field(editor, "scroll") > 0, "Template scrollbar did not move content");
                editor.mouseDragged(barX, 32, 0, 0, -100);
                editor.mouseReleased(barX, 32, 0);
                require((int) field(editor, "scroll") == 0, "Template scrollbar could not return to top");
                editor.mouseScrolled(editor.width / 2, 100, 0, -3);
                require(find(game, "template-type").visible, "Template type selector unreachable");
                @SuppressWarnings("unchecked") var values = (Map<String, String>) field(editor, "values");
                choice(game, "template-type").onPress();
                require(choice(game, "template-type").isOpen() && values.get("type").equals("commission"),
                        "Template type selector still cycles on button press");
                wait = 8;
            }
            case 20 -> {
                capture(game, "animation-choice-menu");
                selectChoice(game, "template-type", "main");
                @SuppressWarnings("unchecked") var values = (Map<String, String>) field(editor, "values");
                require(values.get("type").equals("main"), "Template type choice not applied");
                find(game, "template-controls").onPress();
                find(game, "template-element.add").onPress();
                choice(game, "template-element-kind").select("image");
                require(find(game, "template-element-image") != null, "Image kind did not expose picker");
                wait = 8;
            }
            case 21 -> {
                capture(game, "animation-template-image-control");
                for (int i = 0; i < 20 && !find(game, "template-element-image").visible; i++) {
                    game.screen.mouseScrolled(game.screen.width - 60, 100, 0, -1);
                }
                find(game, "template-element-image").onPress();
                wait = 8;
            }
            case 22 -> {
                var fields = game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                fields.getLast().setValue("minecraft:textures/item/emerald.png");
                find(game, "icon-apply").onPress();
                @SuppressWarnings("unchecked") var draft = (Map<String, String>) field(game.screen, "draft");
                require(draft.get("value").equals("minecraft:textures/item/emerald.png"), "Template image selection lost");
                find(game, "template-save").onPress();
                require(game.screen == editor, "Template control save did not return to parent");
                find(game, "template-save").onPress();
                wait = 10;
            }
            case 23 -> {
                if (game.screen == editor) {
                    require(((String) field(editor, "error")).isBlank(), "Extended template save error");
                    return false;
                }
                QuestTemplate value = QuestTemplateStore.all().stream().filter(t -> t.id().equals(ID)).findFirst().orElseThrow();
                require(value.type().equals("main") && value.elements().stream().anyMatch(e ->
                        e.kind().equals("image") && e.value().equals("minecraft:textures/item/emerald.png")),
                        "Template kind/image did not persist");
            }
            case 24 -> {
                return ScrollViewportRegression.tick(game);
            }
            default -> throw new AssertionError("Unknown animation stage " + stage);
        }
        stage++;
        return false;
    }

    private static void verifyListOverscrollRelease() throws Exception {
        Object originalList = field(journal, "journalList");
        Object originalSlides = field(journal, "groupSlides");
        set(journal, "journalList", new JournalListModel(List.of(), 2_000));
        ((Map<?, ?>) originalSlides).clear();
        try {
            double maximum = (double) invoke(journal, "maxListScroll");
            require(maximum > 0, "Journal list fixture is not scrollable");
            verifyListOverscrollRelease(-24, maximum);
            verifyListOverscrollRelease(maximum + 24, maximum);
        } finally {
            set(journal, "journalList", originalList);
            set(journal, "listScroll", 0.0);
            set(journal, "listTargetScroll", 0.0);
            set(journal, "listReleaseVelocity", 0.0);
            journal.init(Minecraft.getInstance(), journal.width, journal.height);
        }
    }

    private static void verifyListOverscrollRelease(double position, double maximum) throws Exception {
        set(journal, "listScroll", position);
        set(journal, "listTargetScroll", Math.clamp(position, 0, maximum));
        set(journal, "listReleaseVelocity", 0.0);
        set(journal, "listLastDragNanos", System.nanoTime());
        set(journal, "listDragCandidate", true);
        set(journal, "listDragMoved", true);

        journal.mouseReleased(0, 0, 0);
        require((double) field(journal, "listScroll") == position,
                "List release snapped overscroll before the spring could run");

        for (int i = 0; i < 60; i++) {
            set(journal, "listScrollFrameNanos", System.nanoTime() - 16_000_000L);
            invoke(journal, "animateList");
        }
        double settled = (double) field(journal, "listScroll");
        double edge = Math.clamp(position, 0, maximum);
        require(Math.abs(settled - edge) < 0.02,
                "List overscroll did not spring back to its edge: position=" + position
                        + ", settled=" + settled + ", edge=" + edge);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        var types = java.util.Arrays.stream(args).map(arg -> arg instanceof Double ? double.class : arg.getClass())
                .toArray(Class<?>[]::new);
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var type = target.getClass();
        java.lang.reflect.Field field = null;
        while (type != null && field == null) {
            try { field = type.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static StyledButton find(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static StyledChoiceButton<String> choice(Minecraft game, String id) {
        return (StyledChoiceButton<String>) find(game, id);
    }

    @SuppressWarnings("unchecked")
    private static void selectChoice(Minecraft game, String id, String value) throws Exception {
        StyledChoiceButton<String> choice = choice(game, id);
        List<String> options = (List<String>) field(choice, "values");
        int index = options.indexOf(value);
        require(index >= 0, "Missing choice value: " + value);
        double scroll = (double) field(choice, "scroll");
        var popupX = StyledChoiceButton.class.getDeclaredMethod("popupX");
        var popupY = StyledChoiceButton.class.getDeclaredMethod("popupY");
        var rowHeight = StyledChoiceButton.class.getDeclaredMethod("rowHeight");
        popupX.setAccessible(true);
        popupY.setAccessible(true);
        rowHeight.setAccessible(true);
        double x = (int) popupX.invoke(choice) + 6;
        int row = (int) rowHeight.invoke(choice);
        double y = (int) popupY.invoke(choice) + index * row - Math.round(scroll) + row / 2.0;
        require(game.screen.mouseClicked(x, y, 0), "Choice list item did not handle click");
        game.screen.mouseReleased(x, y, 0);
        require(choice.value().equals(value),
                "Choice release missed value " + value + "; selected=" + choice.value()
                        + ", point=" + x + "," + y + ", open=" + choice.isOpen());
    }

    private static Object field(Object target, String name) throws Exception {
        var type = target.getClass();
        java.lang.reflect.Field field = null;
        while (type != null && field == null) {
            try { field = type.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void capture(Minecraft game, String name) throws Exception {
        Path path = Path.of("..", "build", "verification", name + ".png");
        java.nio.file.Files.createDirectories(path.getParent());
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(path); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (animation stage " + stage + ")");
    }
}
