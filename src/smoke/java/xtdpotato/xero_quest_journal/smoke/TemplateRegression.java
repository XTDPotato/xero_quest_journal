package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class TemplateRegression {
    private static final String ID = "template_smoke_" + System.currentTimeMillis();
    private static int stage;
    private static int wait;
    private static CompletableFuture<Void> pending;
    private static QuestTemplate template;
    private static QuestScreen journal;
    private static final String DELETE_A = ID + "_delete_a";
    private static final String DELETE_B = ID + "_delete_b";
    private static final String DELETE_SINGLE = ID + "_delete_single";

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                if (game.screen != null) return false;
                pending = CompletableFuture.runAsync(() -> {
                    var author = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    template = new QuestTemplate(ID, "Commission test", "Shared template", "item", "",
                            "minecraft:oak_log", 16, "minecraft:diamond", 2, "manual", "",
                            testElements());
                    require(QuestTemplateStore.save(null, template, "").equals("permission"), "Unauthorized save");
                    require(QuestTemplateStore.save(author, template, "").isEmpty(), "New template failed");
                    require(QuestTemplateStore.save(author, template, "").equals("duplicate"), "Duplicate accepted");
                    require(QuestTemplateStore.save(author, template, "stale").equals("conflict"), "Conflict accepted");
                    require(QuestTemplateStore.delete(author, List.of("../guide")).equals("invalid"),
                            "Unsafe template deletion accepted");
                    require(QuestTemplateStore.save(author, copyTemplate(DELETE_A, "Bulk delete A"), "").isEmpty(),
                            "First bulk-delete template setup failed");
                    require(QuestTemplateStore.save(author, copyTemplate(DELETE_B, "Bulk delete B"), "").isEmpty(),
                            "Second bulk-delete template setup failed");
                    require(QuestTemplateStore.save(author, copyTemplate(DELETE_SINGLE, "Single delete"), "").isEmpty(),
                            "Single-delete template setup failed");
                    require(QuestAuthoring.create(author, new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP,
                            ID, "Templates", "", "", "", "normal", "", 1)).successful(), "Group setup failed");
                    validateLayout();
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone()) return false;
                pending.join();
                journal = new QuestScreen();
                game.setScreen(journal);
                game.setScreen(new QuestTemplateScreen(journal, ID));
            }
            case 2 -> {
                require(game.screen instanceof QuestTemplateScreen, "Chooser missing");
                var search = fields(game).getFirst();
                search.setValue("Commission test");
                var row = find(game, "template-" + ID);
                int count = game.screen.children().size();
                for (int i = 0; i < 3; i++) {
                    game.screen.mouseClicked(row.getX() + 2, row.getY() + 2, 1);
                    game.screen.mouseClicked(game.screen.width - 2, game.screen.height - 2, 0);
                    require(count == game.screen.children().size(), "Dismissed menu left ghost widgets");
                }
                game.screen.mouseClicked(row.getX() + 2, row.getY() + 2, 1);
                @SuppressWarnings("unchecked") var menu = (List<StyledButton>) field(game.screen, "menu");
                click(game, menu.get(1));
                require(((Set<?>) field(game.screen, "selected")).contains(ID), "Context multi-select failed");
                click(game, row);
                capture(game, "templates-select");
                click(game, find(game, "template-use"));
                require(game.screen == journal, "Did not return to task creation");
                require(ID.equals(field(field(journal, "form"), "templateId")), "Template reference lost");
                var fields = fields(game);
                fields.get(0).setValue(ID + "_quest");
                fields.get(1).setValue("Collect wood");
                fields.get(2).setValue("Bring back sixteen logs. Each task keeps its own content.");
                click(game, find(game, "create"));
            }
            case 3 -> {
                if (field(journal, "pending") != null) return false;
                require(QuestRepository.find(ID + "_quest") != null, "GUI task creation failed");
                require(ID.equals(QuestRepository.groupOf(ID + "_quest")), "Task created in wrong group");
                require(ID.equals(QuestRepository.find(ID + "_quest").templateId()), "Task did not retain reference");
                set(journal, "selectedId", ID + "_quest");
                set(journal, "editMode", false);
                game.setScreen(journal);
            }
            case 4 -> {
                var layout = (LayoutEditorState) field(journal, "layout");
                var measure = QuestScreen.class.getDeclaredMethod("displayWidth", LayoutEditorState.Element.class);
                measure.setAccessible(true);
                var viewportWidth = QuestScreen.class.getDeclaredMethod("canvasWidth");
                viewportWidth.setAccessible(true);
                var canvasPixels = QuestScreen.class.getDeclaredMethod("canvasPixels", int.class);
                canvasPixels.setAccessible(true);
                var body = layout.elements().get(TemplateLayout.id(ID + "_quest", ID, "body"));
                var description = layout.elements().get(TemplateLayout.id(ID + "_quest", ID, "description"));
                require(body != null && description != null, "Template controls missing");
                capture(game, "templates-commission");
                // Authored task canvases may exceed the viewport; their content must remain reachable.
                require((int) measure.invoke(journal, description) <=
                                (int) canvasPixels.invoke(journal, layout.taskCanvas(ID + "_quest").width()),
                        "Description wider than task canvas: " + measure.invoke(journal, description)
                                + " viewport=" + viewportWidth.invoke(journal)
                                + " root=" + measure.invoke(journal, body) + " attrs=" + body.attributes()
                                + " parent=" + layout.parentOf(description.id()));
                if ((int) measure.invoke(journal, description) > (int) viewportWidth.invoke(journal)) {
                    var top = QuestScreen.class.getDeclaredMethod("canvasTop");
                    top.setAccessible(true);
                    journal.mouseScrolled((int) field(journal, "detailsLeft") + 10, (int) top.invoke(journal) + 10, -100, 0);
                    require((double) field(journal, "canvasTargetPanX") < 0,
                            "Wide template cannot pan to its hidden content");
                    set(journal, "canvasPanX", 0.);
                }
                game.setScreen(new QuestTemplateEditorScreen(journal, template));
                fields(game).get(1).setValue("Renamed template");
                click(game, find(game, "template-controls"));
            }
            case 5 -> {
                require(game.screen.getClass().getSimpleName().equals("QuestTemplateControlsScreen"), "Controls missing");
                var controls = game.screen;
                fields(game).getFirst().setValue("new_body");
                controls.mouseScrolled(controls.width - 20, 70, 0, -5);
                capture(game, "templates-controls");
                click(game, find(game, "template-save"));
                require(game.screen instanceof QuestTemplateEditorScreen, "Controls save failed");
                require(fields(game).get(1).getValue().equals("Renamed template"), "Returning erased metadata");
                click(game, find(game, "template-save"));
            }
            case 6 -> {
                if (game.screen instanceof QuestTemplateEditorScreen) return false;
                template = QuestTemplateStore.all().stream().filter(t -> t.id().equals(ID)).findFirst().orElseThrow();
                require(template.name().equals("Renamed template"), "Overwrite failed");
                require(template.elements().getFirst().id().equals("new_body"), "Controls did not persist");
                require(template.elements().stream().filter(e -> !e.parent().isBlank())
                        .allMatch(e -> e.parent().equals("new_body")), "Parent rename broken");
                var json = QuestCatalog.serverJson();
                require(QuestCatalog.decode(json).templates().contains(template), "Catalog omitted templates");
                var network = ClientQuestProgress.catalog().templates().stream().filter(t -> t.id().equals(ID)).findFirst();
                if (network.isEmpty() || !network.get().equals(template)) return false;
                set(journal, "selectedId", ID + "_quest");
                game.setScreen(journal);
                game.setScreen(new QuestTemplateScreen(journal, ID));
            }
            case 7 -> {
                require(game.screen instanceof QuestTemplateScreen, "Template list did not reopen");
                fields(game).getFirst().setValue("Bulk delete");
                for (String id : List.of(DELETE_A, DELETE_B)) {
                    var row = find(game, "template-" + id);
                    game.screen.mouseClicked(row.getX() + 2, row.getY() + 2, 1);
                    @SuppressWarnings("unchecked") var menu = (List<StyledButton>) field(game.screen, "menu");
                    click(game, menu.get(1));
                }
                require(((Set<?>) field(game.screen, "selected")).containsAll(List.of(DELETE_A, DELETE_B)),
                        "Could not select multiple templates for deletion");
                var delete = find(game, "template-delete-selected");
                require(delete.active, "Bulk delete action remained disabled");
                click(game, delete);
                require(game.screen.getClass().getSimpleName().equals("QuestConfirmScreen"),
                        "Bulk delete did not request confirmation");
                click(game, find(game, "overflow-confirm"));
            }
            case 8 -> {
                if (QuestTemplateStore.all().stream().anyMatch(t -> t.id().equals(DELETE_A) || t.id().equals(DELETE_B)))
                    return false;
                require(QuestTemplateStore.all().stream().anyMatch(t -> t.id().equals(ID)),
                        "Bulk delete removed an unselected template");
                game.setScreen(new QuestTemplateScreen(journal, ID));
            }
            case 9 -> {
                require(game.screen instanceof QuestTemplateScreen, "Template list did not reopen for single delete");
                fields(game).getFirst().setValue("Single delete");
                var row = find(game, "template-" + DELETE_SINGLE);
                game.screen.mouseClicked(row.getX() + 2, row.getY() + 2, 1);
                @SuppressWarnings("unchecked") var menu = (List<StyledButton>) field(game.screen, "menu");
                require(menu.size() == 3 && menu.get(2).active, "Single delete context action missing");
                click(game, menu.get(2));
                click(game, find(game, "overflow-confirm"));
            }
            case 10 -> {
                if (QuestTemplateStore.all().stream().anyMatch(t -> t.id().equals(DELETE_A)
                        || t.id().equals(DELETE_B) || t.id().equals(DELETE_SINGLE))) return false;
                require(QuestTemplateStore.all().stream().anyMatch(t -> t.id().equals(ID)),
                        "Deletion removed an unselected template");
                return TemplateInteractionRegression.tick(game);
            }
            default -> throw new AssertionError("Unknown template stage");
        }
        stage++;
        wait = 15;
        return false;
    }

    private static QuestTemplate copyTemplate(String id, String name) {
        return new QuestTemplate(id, name, "Temporary bulk-delete regression fixture", "commission", "",
                "minecraft:oak_log", 1, "", 1, "default", "",
                testElements());
    }

    private static List<QuestTemplate.Element> testElements() {
        var elements = new ArrayList<>(QuestTemplateStore.all().stream()
                .filter(t -> t.id().equals("commission_view")).findFirst().orElseThrow().elements());
        elements.add(2, new QuestTemplate.Element("location", "text", "", 0, 0, 280, 12,
                Map.of("layout_width", "-1", "layout_height", "-2"), "body"));
        return elements;
    }

    private static void validateLayout() {
        var state = new LayoutEditorState();
        TemplateLayout.apply(state, "a", template, 20, 40);
        TemplateLayout.apply(state, "b", template, 20, 40);
        String id = TemplateLayout.id("a", ID, "location");
        var value = state.elements().get(id);
        state.elements().put(id, new LayoutEditorState.Element(id, "a", value.kind(), "Only A",
                value.width(), value.height(), value.attributes()));
        List<QuestTemplate.Element> controls = new ArrayList<>(template.elements());
        var source = controls.get(2);
        controls.set(2, new QuestTemplate.Element(source.id(), source.kind(), "New default", 12, 30,
                source.width(), source.height(), Map.of("textColor", "#ff0000"), source.parent()));
        var updated = template.withElements(controls);
        TemplateLayout.apply(state, "a", updated, 20, 40);
        TemplateLayout.apply(state, "b", updated, 20, 40);
        require(state.elements().get(id).value().equals("Only A"), "Template overwrote instance text");
        require(!state.elements().get(TemplateLayout.id("b", ID, "location")).value().equals("Only A"), "Content leaked");
        require(state.elements().get(id).attribute("textColor", "").equals("#ff0000"), "Shared style did not update");
        require(state.elements().size() == controls.size() * 2, "Instance IDs collide");
        try {
            template.withElements(List.of(new QuestTemplate.Element("cycle", "linear", "", 0, 0, 1, 1, Map.of(), "cycle")));
            throw new AssertionError("Cyclic template accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static List<EditBox> fields(Minecraft game) {
        return game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }
    private static StyledButton find(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void click(Minecraft game, StyledButton button) {
        require(button.active, "Button disabled: " + button.styleId());
        var screen = game.screen;
        require(screen.mouseClicked(button.getX() + button.getWidth() / 2., button.getY() + 10, 0), "Click failed");
        screen.mouseReleased(button.getX() + button.getWidth() / 2., button.getY() + 10, 0);
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
    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        }
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
