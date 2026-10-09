package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;
import xtdpotato.xero_quest_journal.core.model.QuestUiTheme;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.network.QuestUiThemeNetwork;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class JourneyAppearanceRegression {
    private static int stage, wait;
    private static QuestUiTheme originalTheme;
    private static QuestScreen journal;
    private static JourneyAppearanceScreen editor;
    private static JourneyScreen journey;
    private static CompletableFuture<Void> work;
    private static JourneyAppearance savedAppearance;
    private static JourneyAppearance savedTaskAppearance;
    private static String taskId;
    private static int[] previewPixels;

    static boolean tick(Minecraft game, String chapterId) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                originalTheme = ClientQuestProgress.uiTheme();
                ClientPreferences.get().reducedMotion = true;
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.resizeDisplay();
                Gson gson = new Gson();
                var legacy = gson.fromJson("{\"elements\":{\"chapter\":{\"icon\":\"minecraft:diamond\",\"textOffsetX\":6}}}",
                        JourneyAppearance.class);
                require(legacy.get("chapter").textScale() == 1 && legacy.get("chapter").iconVisible(),
                        "Partial appearance JSON lost defaults");
                require(gson.fromJson("{\"colors\":{}}", QuestUiTheme.class).journeyAppearance().elements().isEmpty(),
                        "Legacy theme no longer loads");
                boolean rejected = false;
                try { new JourneyAppearance.Element("", 0, 0, Float.NaN, 0, 0, 1F, true, true); }
                catch (IllegalArgumentException invalid) { rejected = true; }
                require(rejected, "Non-finite appearance scale accepted");
                rejected = false;
                try { new JourneyAppearance(Map.of("invalid", JourneyAppearance.Element.defaults())); }
                catch (IllegalArgumentException invalid) { rejected = true; }
                require(rejected, "Unknown appearance element accepted");
                journal = new QuestScreen();
                game.setScreen(journal);
                invoke(journal, "editProperties", new Class<?>[]{String.class, String.class}, "chapter", chapterId);
                button(journal, "journey-appearance").onPress();
                require(game.screen instanceof JourneyAppearanceScreen, "Chapter appearance action missing");
                editor = (JourneyAppearanceScreen) game.screen;
                field("text_x").setValue("17");
                field("text_y").setValue("-9");
                field("icon_x").setValue("8");
                field("icon_y").setValue("3");
                field("icon_scale").setValue("1.25");
                field("text_scale").setValue("1.1");
                button(editor, "journey-appearance-pick").onPress();
                require(game.screen instanceof QuestScreen, "Icon resource picker did not open");
                var fields = (Map<?, ?>) member(game.screen, "formFields");
                ((EditBox) fields.get("icon_picker.manual")).setValue("minecraft:diamond");
                button(game.screen, "icon-apply").onPress();
                wait = 5;
            }
            case 1 -> {
                require(game.screen == editor && field("text_x").getValue().equals("17")
                        && field("icon").getValue().equals("minecraft:diamond"), "Picker discarded appearance draft");
                capture(game, "journey-appearance-editor");
                field("text_x").setValue("9999");
                var draftPreview = ((Map<?, ?>) member(editor, "drafts")).get("chapter");
                var preview = (JourneyAppearance.Element) invoke(draftPreview, "preview", new Class<?>[]{});
                require(preview.iconOffsetX() == 8 && preview.textOffsetX() == 256,
                        "Invalid field discarded valid preview properties");
                field("icon").setValue("minecraft:../invalid");
                preview = (JourneyAppearance.Element) invoke(draftPreview, "preview", new Class<?>[]{});
                require(preview.icon().isBlank() && preview.iconOffsetX() == 8,
                        "Invalid icon interrupted appearance preview");
                field("icon").setValue("minecraft:diamond");
                button(editor, "journey-appearance-save").onPress();
                require(game.screen == editor && !((String) member(editor, "message")).isBlank(),
                        "Out-of-range text offset accepted");
                field("text_x").setValue("17");
                button(editor, "journey-appearance-save").onPress();
                require(game.screen == journal, "Appearance save did not return to chapter form");
                var draft = member(journal, "form");
                savedAppearance = (JourneyAppearance) member(draft, "journeyAppearance");
                require(savedAppearance.get("chapter").textOffsetX() == 17
                                && savedAppearance.get("chapter").icon().equals("minecraft:diamond"),
                        "Chapter form lost appearance callback");
                require(QuestRepository.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst()
                        .orElseThrow().journeyAppearance().elements().isEmpty(), "Appearance published before chapter save");
                button(journal, "create").onPress();
                wait = 8;
            }
            case 2 -> {
                if (member(journal, "pending") != null) return false;
                var chapter = QuestRepository.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst().orElseThrow();
                require(chapter.journeyAppearance().equals(savedAppearance), "Chapter appearance not persisted");
                require(QuestCatalog.decode(QuestCatalog.serverJson()).chapters().stream()
                        .filter(c -> c.id().equals(chapterId)).findFirst().orElseThrow().journeyAppearance().equals(savedAppearance),
                        "Chapter appearance lost during catalog sync");
                invoke(journal, "editProperties", new Class<?>[]{String.class, String.class}, "chapter", chapterId);
                button(journal, "journey-appearance").onPress();
                editor = (JourneyAppearanceScreen) game.screen;
                require(field("text_y").getValue().equals("-9"), "Reopened chapter editor lost text offset");
                field("text_y").setValue("23");
                button(editor, "journey-appearance-cancel").onPress();
                require(((JourneyAppearance) member(member(journal, "form"), "journeyAppearance")).equals(savedAppearance),
                        "Cancel modified chapter draft");
                button(journal, "journey-appearance").onPress();
                editor = (JourneyAppearanceScreen) game.screen;
                button(editor, "journey-appearance-reset").onPress();
                require(field("text_y").getValue().equals("0") && field("icon").getValue().isBlank(),
                        "Reset did not restore inherited defaults");
                button(editor, "journey-appearance-cancel").onPress();
                journey = new JourneyScreen(journal);
                game.setScreen(journey);
                int index = 0;
                var chapters = ClientQuestDefinitions.chapters();
                for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).id().equals(chapterId)) index = i;
                invoke(journey, "selectChapter", new Class<?>[]{int.class}, index);
                wait = 6;
            }
            case 3 -> {
                require(((JourneyAppearance) invoke(journey, "appearance", new Class<?>[]{})).get("chapter")
                        .equals(savedAppearance.get("chapter")), "Live timeline ignored chapter appearance");
                capture(game, "journey-custom-chapter");
                int x = journey.width - 52, y = 22;
                journey.mouseClicked(x, y, 0);
                journey.mouseReleased(x, y, 0);
                require(game.screen instanceof JourneyAppearanceScreen, "Global appearance tool did not open");
                editor = (JourneyAppearanceScreen) game.screen;
                setSelected("header");
                field("icon").setValue("minecraft:compass");
                field("text_x").setValue("5");
                field("text_y").setValue("2");
                button(editor, "journey-appearance-save").onPress();
                wait = 10;
            }
            case 4 -> {
                if (game.screen == editor) return false;
                require(game.screen == journey && ClientQuestProgress.uiTheme().journeyAppearance()
                        .get("header").icon().equals("minecraft:compass"), "Server theme did not save journey appearance");
                var effective = (JourneyAppearance) invoke(journey, "appearance", new Class<?>[]{});
                require(effective.get("chapter").equals(savedAppearance.get("chapter"))
                                && effective.get("header").icon().equals("minecraft:compass"),
                        "Chapter overrides replaced unrelated global slots");
                capture(game, "journey-custom-defaults");
                var theme = ClientQuestProgress.uiTheme();
                require(theme.with("accent", "#ffffff").journeyAppearance().equals(theme.journeyAppearance())
                                && theme.withOverviewImage("").journeyAppearance().equals(theme.journeyAppearance()),
                        "Unrelated theme edits lost journey appearance");
                work = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestConfig.reload() && QuestConfig.defaults().uiTheme.journeyAppearance()
                            .get("header").icon().equals("minecraft:compass"), "Theme appearance did not survive reload");
                    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
                    try {
                        var state = new QuestUiThemeNetwork.State(new Gson().toJson(theme));
                        QuestUiThemeNetwork.State.CODEC.encode(buffer, state);
                        require(QuestUiThemeNetwork.State.CODEC.decode(buffer).equals(state), "Appearance state codec failed");
                    } finally { buffer.release(); }
                    require(QuestUiThemeNetwork.apply(player, originalTheme), "Original theme could not be restored");
                }, game.getSingleplayerServer());
            }
            case 5 -> {
                if (!work.isDone()) return false;
                work.join();
                editor = new JourneyAppearanceScreen(journey, savedAppearance, ignored -> {});
                game.setScreen(editor);
                game.getWindow().setWindowed(640, 480);
                game.getWindow().setWidth(640);
                game.getWindow().setHeight(480);
                game.resizeDisplay();
                editor.setFocused(field("icon_x"));
                editor.mouseScrolled(100, editor.height - 55, 0, -1);
                wait = 6;
            }
            case 6 -> {
                require(editor.getFocused() == field("icon_x"),
                        "Scrolling rebuilt the focused appearance field");
                editor.mouseScrolled(100, editor.height - 55, 0, -8);
                wait = 6;
            }
            case 7 -> {
                capture(game, "journey-appearance-small");
                require((double) invoke(member(editor, "scroll"), "position", new Class<?>[]{}) > 0,
                        "Compact appearance properties not scrollable");
                editor.onClose();
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.resizeDisplay();
                taskId = QuestRepository.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst()
                        .orElseThrow().quests().stream().filter(id -> QuestRepository.find(id).type().equals("legendary"))
                        .findFirst().orElseThrow();
                game.setScreen(journal);
                invoke(journal, "editProperties", new Class<?>[]{String.class, String.class}, "task", taskId);
                Object form = member(journal, "form");
                var scroll = form.getClass().getDeclaredField("scroll");
                scroll.setAccessible(true);
                scroll.setDouble(form, (double) invoke(journal, "formMaxScroll", new Class<?>[]{}));
                invoke(journal, "updateFormViewport", new Class<?>[]{});
                var action = button(journal, "journey-task-appearance");
                require(action.visible, "Task journey appearance cannot be reached by scrolling");
                action.onPress();
                editor = (JourneyAppearanceScreen) game.screen;
                field("icon").setValue("minecraft:diamond");
                field("text_override").setValue("Custom expedition");
                field("text_color").setValue("#ffffff");
                field("background").setValue("minecraft:textures/gui/title/background/panorama_1.png");
                setSelected("subtitle");
                field("text_override").setValue("Act I");
                button(editor, "journey-appearance-save").onPress();
                require(game.screen == journal && QuestRepository.find(taskId).journeyAppearance().elements().isEmpty(),
                        "Task appearance draft mismatch: screen=" + game.screen.getClass().getSimpleName()
                                + ", editorMessage=" + member(editor, "message")
                                + ", saved=" + QuestRepository.find(taskId).journeyAppearance());
                savedTaskAppearance = (JourneyAppearance) member(member(journal, "form"), "journeyAppearance");
                button(journal, "create").onPress();
                wait = 8;
            }
            case 8 -> {
                if (member(journal, "pending") != null) return false;
                var quest = QuestRepository.find(taskId);
                require(quest.journeyAppearance().equals(savedTaskAppearance), "Task appearance did not persist in JSON");
                require(QuestCatalog.decode(QuestCatalog.serverJson()).quests().stream().filter(q -> q.id().equals(taskId))
                        .findFirst().orElseThrow().journeyAppearance().equals(savedTaskAppearance),
                        "Task appearance lost in catalog display copy");
                String source = "<quest id=\"appearance_xml\" name=\"Original\"><script>const x = 1 < 2;</script></quest>";
                String updated = xtdpotato.xero_quest_journal.core.io.QuestDocuments.journeyAppearance(source, savedTaskAppearance);
                require(updated.contains("<script>const x = 1 < 2;</script>")
                        && QuestRepository.parseDefinition(Path.of("appearance.xml"), updated).journeyAppearance().equals(savedTaskAppearance),
                        "XML journey appearance lost values or modified author code");
                invoke(journal, "editProperties", new Class<?>[]{String.class, String.class}, "task", taskId);
                button(journal, "journey-task-appearance").onPress();
                editor = (JourneyAppearanceScreen) game.screen;
                require(field("text_override").getValue().equals("Custom expedition")
                        && !field("background").getValue().isBlank(), "Reopening task appearance lost text/background");
                capture(game, "journey-task-appearance-editor");
                button(editor, "journey-appearance-cancel").onPress();
                journey = new JourneyScreen(journal);
                game.setScreen(journey);
                int index = 0;
                for (int i = 0; i < ClientQuestDefinitions.chapters().size(); i++)
                    if (ClientQuestDefinitions.chapters().get(i).id().equals(chapterId)) index = i;
                invoke(journey, "selectChapter", new Class<?>[]{int.class}, index);
                Object scroll = member(journey, "timelineX");
                invoke(scroll, "by", new Class<?>[]{double.class}, 320.);
                wait = 8;
            }
            case 9 -> {
                capture(game, "journey-task-custom-background");
                editor = new JourneyAppearanceScreen(journey, JourneyAppearance.empty(), ignored -> {})
                        .preview("chapter", "minecraft:book", "Current chapter");
                game.setScreen(editor);
                field("icon").setValue("minecraft:diamond");
                wait = 6;
            }
            case 10 -> {
                previewPixels = previewPixels(game);
                require(((JourneyAppearance.Element) invoke(editor, "previewValue", new Class<?>[]{}))
                        .icon().equals("minecraft:diamond"), "Visible icon field not used by preview");
                capture(game, "journey-preview-diamond");
                field("icon").setValue("minecraft:emerald");
                wait = 6;
            }
            case 11 -> {
                int[] next = previewPixels(game);
                int changed = 0;
                for (int i = 0; i < next.length; i++) if (next[i] != previewPixels[i]) changed++;
                require(changed > next.length / 20, "Changing the selected item left the old preview pixels");
                capture(game, "journey-preview-emerald");
                setSelected("tab");
                field("icon").setValue("minecraft:compass");
                require(((JourneyAppearance.Element) invoke(editor, "previewValue", new Class<?>[]{}))
                        .icon().equals("minecraft:compass"), "Slot switch still previewed the first slot");
                setSelected("chapter");
                require(field("icon").getValue().equals("minecraft:emerald"), "Switching slots discarded icon draft");
                field("icon").setValue("");
                require(((Map<?, ?>) member(editor, "previews")).get("chapter")
                        .equals(new JourneyAppearanceScreen.Preview("minecraft:book", "Current chapter")),
                        "Inherited preview differs from the current chapter context");
                editor.onClose();
                return true;
            }
            default -> throw new AssertionError("Unknown appearance stage");
        }
        stage++;
        return false;
    }

    private static void setSelected(String value) throws Exception {
        var f = editor.getClass().getDeclaredField("selected");
        f.setAccessible(true); f.set(editor, value);
        invoke(editor, "init", new Class<?>[]{});
    }
    private static EditBox field(String suffix) {
        String key = "screen.xero_quest_journal.journey." + suffix;
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        return editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static Object member(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        var m = owner.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(owner, args);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static int[] previewPixels(Minecraft game) throws Exception {
        int x = (int) invoke(editor, "left", new Class<?>[]{}) + 15;
        int y = (61 + (int) invoke(editor, "bodyTop", new Class<?>[]{}) - 8) / 2 - 14;
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            double sx = image.getWidth() / (double) editor.width, sy = image.getHeight() / (double) editor.height;
            int[] pixels = new int[28 * 28];
            for (int row = 0; row < 28; row++) for (int col = 0; col < 28; col++)
                pixels[row * 28 + col] = image.getPixelRGBA((int) ((x + col + .5) * sx), (int) ((y + row + .5) * sy));
            return pixels;
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (journey appearance stage " + stage + ")");
    }
}
