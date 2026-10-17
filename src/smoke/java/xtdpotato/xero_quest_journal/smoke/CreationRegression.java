package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real input coverage for creation destinations and field-specific resource pickers. */
final class CreationRegression {
    private static final String GROUP = "creation_" + System.currentTimeMillis();
    private static final String CHAPTER = GROUP + "_chapter";
    private static final String ROOT_TASK = GROUP + "_root";
    private static final String TASK = GROUP + "_task";
    private static final String TEXTURE = "minecraft:textures/item/diamond.png";
    private static int stage;
    private static int wait;
    private static CompletableFuture<Void> pending;
    private static CompletableFuture<Void> language;
    private static QuestScreen journal;
    private static String imageId;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                if (game.screen != null) return false;
                ClientPreferences.get().reducedMotion = true;
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                language = game.reloadResourcePacks();
                pending = CompletableFuture.runAsync(() -> {
                    var author = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestAuthoring.create(author, new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP,
                            GROUP, "Creation group", "", "", "", "normal", "", 1)).successful(), "Group fixture");
                    require(QuestAuthoring.create(author, new QuestAuthoring.Request(QuestAuthoring.Kind.CHAPTER,
                            CHAPTER, "Creation chapter", "", GROUP, "", "normal", "", 1)).successful(), "Chapter fixture");
                    require(!QuestAuthoring.create(author, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            GROUP + "_invalid", "Invalid", "", "missing", "", "guide", "", 1)).successful(),
                            "Invalid group accepted");
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone() || !language.isDone()) return false;
                pending.join();
                language.join();
                verifySuggestionTrash(game);
                require(QuestText.describe(new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger(
                                "event", "template_graph_only", 1)).getString().equals("\u5b8c\u6210\u4efb\u52a1\u76ee\u6807"),
                        "Graph-only template objective was not localized");
                require(QuestText.describe(new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger(
                                "event", "template_scene_only", 1)).getString().equals("\u5b8c\u6210\u7ed3\u6784\u6311\u6218"),
                        "Scene-only template objective was not localized");
                String customObjective = QuestDocuments.objective("<quest id=\"objective_regression\"/>",
                        "Custom task objective");
                require(customObjective.contains("objective=\"Custom task objective\""),
                        "Custom objective was not written to task properties");
                require(!QuestDocuments.objective(customObjective, "").contains("objective="),
                        "Clearing a custom objective did not restore automatic text");
                game.getToasts().clear();
                journal = new QuestScreen();
                set(journal, "editMode", true);
                game.setScreen(journal);
                context(30, 100, null);
                require(menu().stream().map(StyledButton::styleId).toList()
                        .containsAll(List.of("context-add_task", "context-add_group", "context-add_chapter")),
                        "All creation disabled");
                menu().clear();
                var groups = find(game, "group-" + GROUP);
                journal.mouseClicked(groups.getX() + 5, groups.getY() + 5, 1);
                require(menu().stream().map(StyledButton::styleId).toList().containsAll(
                        List.of("context-add_task", "context-add_group", "context-add_chapter")), "All context missing actions");
                menu().clear();
                context(30, 100, null);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-add_task")).findFirst().orElseThrow());
                require(game.screen instanceof QuestTemplateScreen, "All did not open templates");
                fields(game).getFirst().setValue("commission_view");
                click(game, find(game, "template-commission_view"));
                click(game, find(game, "template-use"));
                require(field(form(), "targetGroup").equals(""), "All forced group");
                fields(game).get(0).setValue(ROOT_TASK);
                fields(game).get(1).setValue("Ungrouped draft");
                fields(game).get(2).setValue("Keep the description");
                click(game, find(game, "choose-icon"));
                require(buttons(game, "icon-entry").size() > 0, "Empty default picker");
                var itemChoices = QuestIcons.itemChoices();
                require(itemChoices == QuestIcons.itemChoices(), "Item search index was rebuilt");
                var diamond = itemChoices.stream().filter(choice -> choice.value().equals("minecraft:diamond"))
                        .findFirst().orElseThrow();
                require(diamond.displayName().contains("\u94bb\u77f3")
                                && diamond.displayName().contains("minecraft:diamond"),
                        "Localized item label omitted name or resource ID");
                require(diamond.matches("\u94bb\u77f3") && diamond.matches("zuanshi")
                                && diamond.matches("zs") && diamond.matches("minecraft:diamond"),
                        "Localized item search aliases missing");
                var customStack = new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.DIAMOND);
                customStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("Regression Diamond"));
                String encodedStack = xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue
                        .encode(customStack, game.player.registryAccess());
                var decodedStack = xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue
                        .decode(encodedStack, game.player.registryAccess()).orElseThrow();
                require(net.minecraft.world.item.ItemStack.isSameItemSameComponents(customStack, decodedStack)
                                && !net.minecraft.world.item.ItemStack.isSameItemSameComponents(
                                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND), decodedStack),
                        "Component-specific item identity was not preserved");
                fields(game).getFirst().setValue("\u94bb\u77f3");
                click(game, find(game, "icon-filter"));
                require(buttons(game, "icon-entry").size() > 0, "Localized item name search failed");
                fields(game).getFirst().setValue("zuanshi");
                click(game, find(game, "icon-filter"));
                require(buttons(game, "icon-entry").size() > 0, "Item pinyin search failed");
                fields(game).getFirst().setValue("minecraft:diamond");
                click(game, find(game, "icon-filter"));
                require(buttons(game, "icon-entry").size() > 0, "Item ID search failed");
                require(find(game, "icon-sort") instanceof StyledChoiceButton<?>
                                && find(game, "icon-sort-order") != null,
                        "Fullscreen icon picker is missing sort controls");
                String iconBeforeBrowse = (String) field(form(), "icon");
                click(game, find(game, "icon-entry"));
                require(field(form(), "icon").equals(iconBeforeBrowse),
                        "Browsing an icon applied it before confirmation");
                require(field(field(journal, "iconPicker"), "manual").equals("minecraft:diamond"),
                        "Item picker did not stage the selected item");
                require(find(game, "icon-preview").active, "Selected icon did not enable fullscreen preview");
                click(game, find(game, "icon-preview"));
                require(game.screen.getClass().getSimpleName().equals("QuestIconPreviewScreen"),
                        "Fullscreen icon preview did not open");
                capture(game, "creation-icon-preview");
                game.screen.mouseScrolled(game.screen.width / 2.0, game.screen.height / 2.0, 0, 2);
                require((double) field(game.screen, "targetZoom") > 1,
                        "Preview wheel did not smoothly zoom the selected icon");
                click(game, find(game, "icon-preview-back"));
                require(game.screen == journal && field(field(journal, "iconPicker"), "manual")
                                .equals("minecraft:diamond"),
                        "Returning from preview lost the staged selection");
                click(game, find(game, "icon-apply"));
                require(field(form(), "icon").equals("minecraft:diamond"), "Item picker apply did not commit selection");
                click(game, find(game, "choose-icon"));
                manual(game, TEXTURE);
                require(field(form(), "icon").equals(TEXTURE), "Icon not set");
                require(field(form(), "name").equals("Ungrouped draft")
                        && field(form(), "description").equals("Keep the description"), "Picker discarded draft");
                journal = (QuestScreen) game.screen;
                game.setScreen(QuestScreen.pickResource(journal, "minecraft:oak_log", true,
                        value -> {}));
                require(buttons(game, "icon-source-textures").isEmpty(), "Item picker offered textures");
                manual(game, TEXTURE);
                require(game.screen != journal && field(game.screen, "iconPicker") != null
                        && field(game.screen, "error") != null, "Texture accepted as item");
                manual(game, "minecraft:air");
                require(game.screen != journal && field(game.screen, "iconPicker") != null, "Air accepted as item");
                manual(game, "minecraft:oak_log");
                require(game.screen == journal, "Item picker did not return to its draft");
                click(game, find(game, "choose-reward-item"));
                manual(game, "minecraft:emerald");
                require(field(form(), "rewardItem").equals("minecraft:emerald")
                        && field(form(), "icon").equals(TEXTURE), "Picker wrote wrong task field");
            }
            case 2 -> {
                capture(game, "creation-all-form");
                click(game, find(game, "create"));
            }
            case 3 -> {
                if (field(journal, "pending") != null) return false;
                require(QuestRepository.find(ROOT_TASK) != null, "Ungrouped GUI save failed: " + field(journal, "error"));
                require(QuestRepository.groupOf(ROOT_TASK).isEmpty(), "Ungrouped task in group");
                require(QuestRepository.find(ROOT_TASK).icon().equals(TEXTURE), "Icon save lost");
                context(30, 100, null);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-add_chapter")).findFirst().orElseThrow());
                fields(game).get(0).setValue(GROUP + "_newchapter");
                fields(game).get(1).setValue("New chapter");
                click(game, find(game, "create"));
            }
            case 4 -> {
                if (field(journal, "pending") != null) return false;
                require(form() != null && field(journal, "error") != null, "Chapter without group accepted");
                select(game, "create-group", "targetGroup", GROUP);
                click(game, find(game, "create"));
            }
            case 5 -> {
                if (field(journal, "pending") != null) return false;
                require(form() == null && QuestRepository.chapters().stream()
                        .anyMatch(c -> c.id().equals(GROUP + "_newchapter") && c.groupId().equals(GROUP)),
                        "Chapter destination wrong");
                game.setScreen(journal);
                set(journal, "chapterId", CHAPTER);
                context(30, 100, "group:" + GROUP);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-add_task")).findFirst().orElseThrow());
                click(game, find(game, "template-use"));
                require(field(form(), "targetChapter").equals(CHAPTER), "Template chooser lost chapter");
                click(game, find(game, "cancel"));
                set(journal, "groupId", "");
                set(journal, "chapterId", "");
                game.setScreen(journal);
                context(30, 100, null);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-add_task")).findFirst().orElseThrow());
                click(game, find(game, "template-use"));
                fields(game).get(0).setValue(TASK);
                fields(game).get(1).setValue("Destination draft");
                select(game, "create-group", "targetGroup", GROUP);
                select(game, "create-chapter", "targetChapter", CHAPTER);
                require(field(form(), "name").equals("Destination draft"), "Destination selection lost fields");
                click(game, find(game, "create"));
            }
            case 6 -> {
                if (field(journal, "pending") != null) return false;
                require(QuestRepository.find(TASK) != null && QuestRepository.groupOf(TASK).equals(GROUP), "Wrong task folder");
                require(QuestRepository.chapters().stream().filter(c -> c.id().equals(CHAPTER))
                        .anyMatch(c -> c.quests().contains(TASK)
                                || c.quests().isEmpty() && !c.timeline().explicitTasks()
                                && c.groupId().equals(QuestRepository.groupOf(TASK))),
                        "Chapter membership missing");
                context(30, 100, "quest:" + TASK);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-copy_id")).findFirst().orElseThrow());
                require(game.keyboardHandler.getClipboard().equals(TASK), "Task ID copy used wrong value");
                context(30, 100, "group:" + GROUP);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-add_chapter")).findFirst().orElseThrow());
                require(field(form(), "targetGroup").equals(GROUP), "Group context used active filter");
                click(game, find(game, "cancel"));
                set(journal, "selectedId", TASK);
                game.setScreen(journal);
                var currentLayout = (LayoutEditorState) field(journal, "layout");
                var titleControl = currentLayout.elements().values().stream()
                        .filter(element -> element.quest().equals(TASK) && element.kind().equals("quest_title"))
                        .findFirst().orElseThrow();
                context((int) field(journal, "detailsLeft") + 20, 100, titleControl.id());
                click(game, menu().stream().filter(b -> b.styleId().equals("context-copy_id")).findFirst().orElseThrow());
                require(game.keyboardHandler.getClipboard().equals("title"), "Built-in control copied internal task-prefixed ID");
                context((int) field(journal, "detailsLeft") + 20, 100, titleControl.id());
                click(game, menu().stream().filter(b -> b.styleId().equals("context-properties")).findFirst().orElseThrow());
                require(field(form(), "id").equals("title"), "Built-in control properties display internal task-prefixed ID");
                require(field(form(), "elementId").equals(titleControl.id()), "Built-in control lost its internal ID");
                click(game, find(game, "cancel"));
                context((int) field(journal, "detailsLeft") + 30, (int) call(journal, "detailsTop") + 30, null);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-add_image")).findFirst().orElseThrow());
                imageId = (String) field(form(), "id");
                click(game, find(game, "choose-image"));
                if (find(game, "icon-source-textures").active)
                    click(game, find(game, "icon-source-textures"));
                fields(game).getFirst().setValue("textures/item/diamond.png");
                click(game, find(game, "icon-filter"));
                @SuppressWarnings("unchecked")
                var matches = (List<QuestIcons.IconChoice>) field(field(journal, "iconPicker"), "filteredChoices");
                require(matches.size() == 1 && matches.getFirst().value().equals(TEXTURE),
                        "Resource-ID search failed: matches=" + matches
                                + ", search=" + field(field(journal, "iconPicker"), "search"));
            }
            case 7 -> {
                capture(game, "creation-texture-picker");
                int pageY = find(game, "icon-previous").getY();
                require(buttons(game, "icon-entry").stream().allMatch(b -> b.getBottom() <= pageY), "Grid overlaps pages");
                click(game, find(game, "icon-entry"));
                require(((String) field(form(), "description")).isBlank()
                                && field(field(journal, "iconPicker"), "manual").equals(TEXTURE),
                        "Selecting a texture committed before the Apply action");
                click(game, find(game, "icon-preview"));
                require(game.screen.getClass().getSimpleName().equals("QuestIconPreviewScreen"),
                        "Texture fullscreen preview did not open");
            }
            case 8 -> {
                capture(game, "creation-icon-preview");
                game.screen.mouseScrolled(game.screen.width / 2.0, game.screen.height / 2.0, 0, 2);
                require((double) field(game.screen, "targetZoom") > 1,
                        "Texture preview zoom transition did not start");
                click(game, find(game, "icon-preview-back"));
                require(game.screen == journal, "Preview did not return to the picker");
                click(game, find(game, "icon-apply"));
                require(field(form(), "description").equals(TEXTURE)
                        && field(form(), "icon").equals(""), "Image picker wrote icon instead of content");
                click(game, find(game, "choose-image"));
                click(game, find(game, "icon-cancel"));
                require(field(form(), "description").equals(TEXTURE), "Cancel cleared value");
                click(game, find(game, "create"));
                var layout = (LayoutEditorState) field(journal, "layout");
                require(layout.elements().get(imageId).value().equals(TEXTURE), "Image not saved");
                context((int) field(journal, "detailsLeft") + 20, 120, imageId);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-copy_id")).findFirst().orElseThrow());
                require(game.keyboardHandler.getClipboard().equals(imageId), "Control ID copy used task ID");
                context((int) field(journal, "detailsLeft") + 20, 120, imageId);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-properties")).findFirst().orElseThrow());
                require(field(form(), "id").equals(imageId), "Control properties opened with wrong ID");
                require(fields(game).getFirst().getMessage().getString().equals("控件 ID"),
                        "Control properties still use task ID label");
                set(form(), "propertyPage", "appearance");
                game.setScreen(journal);
                click(game, find(game, "choose-background"));
                manual(game, "minecraft:textures/block/stone.png");
                require(field(form(), "background").equals("minecraft:textures/block/stone.png")
                        && field(form(), "description").equals(TEXTURE), "Background changed content");
            }
            case 9 -> {
                capture(game, "creation-background");
                click(game, find(game, "create"));
                var saved = LayoutStore.load("journal").elements().get(imageId);
                require(saved != null && saved.value().equals(TEXTURE)
                        && saved.attribute("background", "").equals("minecraft:textures/block/stone.png"), "Layout round-trip failed");
                context((int) field(journal, "detailsLeft") + 20, 120, imageId);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-properties")).findFirst().orElseThrow());
                set(form(), "propertyPage", "content");
                game.setScreen(journal);
                click(game, find(game, "choose-image"));
                game.options.guiScale().set(1);
                game.resizeDisplay();
            }
            case 10 -> {
                capture(game, "creation-picker-wide");
                click(game, find(game, "icon-none"));
                require(field(form(), "description").equals(TEXTURE),
                        "Choosing Blank committed before pressing Apply");
                click(game, find(game, "icon-apply"));
                require(field(form(), "description").equals(""), "Clear not applied");
                click(game, find(game, "cancel"));
            }
            case 11 -> {
                return AuthoringHistoryRegression.tick(game);
            }
            default -> throw new AssertionError("Unknown creation stage " + stage);
        }
        stage++;
        wait = 15;
        return false;
    }

    private static void verifySuggestionTrash(Minecraft game) throws Exception {
        var picker = new QuestValuePickerScreen(null, "Regression suggestions",
                "creation_trash_" + GROUP, () -> List.of(
                new QuestValuePickerScreen.Option("candidate_a", "Candidate A"),
                new QuestValuePickerScreen.Option("candidate_b", "Candidate B")), "", ignored -> {});
        game.setScreen(picker);
        click(game, find(game, "value-picker-trash"));
        require(game.screen instanceof QuestSuggestionTrashScreen,
                "Suggestion recycle bin did not open from the picker footer");
        click(game, find(game, "trash-select-all"));
        require(((Set<?>) field(game.screen, "selected")).size() == 2,
                "Recycle bin select-all did not select every visible candidate");
        click(game, find(game, "trash-invert"));
        require(((Set<?>) field(game.screen, "selected")).isEmpty(),
                "Recycle bin invert did not clear an all-selected list");
        click(game, find(game, "trash-invert"));
        click(game, find(game, "trash-delete"));
        require(game.screen.getClass().getSimpleName().equals("QuestConfirmScreen"),
                "Recycle bin deletion skipped confirmation");
        click(game, find(game, "overflow-cancel"));
        require(((List<?>) field(picker, "filtered")).size() == 2,
                "Canceling recycle-bin confirmation removed suggestions");
        click(game, find(game, "trash-cancel"));
        require(game.screen == picker, "Closing recycle bin did not return to the picker");
        click(game, find(game, "value-picker-trash"));
        click(game, find(game, "trash-select-all"));
        click(game, find(game, "trash-delete"));
        click(game, find(game, "overflow-confirm"));
        require(((List<?>) field(picker, "filtered")).isEmpty(),
                "Confirmed recycle-bin deletion left suggestions visible");
        game.setScreen(null);
    }

    private static void select(Minecraft game, String button, String field, String value) throws Exception {
        @SuppressWarnings("unchecked")
        var choice = (StyledChoiceButton<String>) find(game, button);
        choice.select(value);
        require(field(form(), field).equals(value), "Missing selector value " + value);
    }
    private static void manual(Minecraft game, String value) throws Exception {
        fields(game).getLast().setValue(value);
        click(game, find(game, "icon-apply"));
    }
    private static Object form() throws Exception { return field(journal, "form"); }
    private static List<EditBox> fields(Minecraft game) {
        return game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }
    private static List<StyledButton> buttons(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).toList();
    }
    private static StyledButton find(Minecraft game, String id) {
        return buttons(game, id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing creation button: " + id));
    }
    private static void click(Minecraft game, StyledButton button) throws Exception {
        require(button.active, "Disabled " + button.styleId());
        if (game.screen == journal && form() != null && field(journal, "iconPicker") == null
                && !Set.of("create", "cancel").contains(button.styleId())) {
            for (int i = 0; i < 50; i++) {
                int top = (int) call(journal, "formBodyTop"), bottom = (int) call(journal, "formBodyBottom");
                if (button.getY() >= top && button.getBottom() <= bottom) break;
                journal.mouseScrolled(journal.width / 2., (top + bottom) / 2., 0, button.getY() < top ? 1 : -1);
            }
        }
        var screen = game.screen;
        require(screen.mouseClicked(button.getX() + button.getWidth() / 2., button.getY() + 10, 0),
                "Click failed " + button.styleId());
        screen.mouseReleased(button.getX() + button.getWidth() / 2., button.getY() + 10, 0);
    }
    private static void context(int x, int y, String target) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod("contextMenu", int.class, int.class, String.class);
        method.setAccessible(true);
        method.invoke(journal, x, y, target);
    }
    @SuppressWarnings("unchecked")
    private static List<StyledButton> menu() throws Exception { return (List<StyledButton>) field(journal, "menu"); }
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
    private static Object call(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Creation stage " + stage + ": " + message);
    }
}
