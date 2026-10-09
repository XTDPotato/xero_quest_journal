package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import xtdpotato.xero_quest_journal.client.QuestConditionsScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.io.QuestDocuments;

import java.nio.file.Path;
import java.util.List;

final class ConditionsFormRegression {
    private static final String ID = "conditions_form_" + System.currentTimeMillis();
    private static QuestScreen journal;
    private static int stage;
    private static String original;

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> {
                String cleared = QuestDocuments.conditions(
                        "<quest id=\"legacy\" type=\"guide\"><content>Keep me</content></quest>", List.of());
                require(QuestRepository.parseDefinition(Path.of("legacy.html"), cleared).triggers().isEmpty(),
                        "Legacy confirmation condition reappeared after clearing");
                require(cleared.contains("<content>Keep me</content>"), "Clearing conditions modified task content");
                journal = new QuestScreen();
                game.setScreen(journal);
                invoke("openForm", new Class<?>[]{String.class, String.class}, "task", "normal");
                var fields = journal.children().stream().filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast).toList();
                fields.get(0).setValue(ID);
                fields.get(1).setValue("Condition draft task");
                require(fields.stream().noneMatch(box -> box.getMessage().getString()
                        .contains("create.parents")), "Legacy parent field remains");
                button(game.screen, "edit-form-conditions").onPress();
                require(game.screen instanceof QuestConditionsScreen, "Creation conditions did not open");
                button(game.screen, "conditions-add").onPress();
                button(game.screen, "conditions-save").onPress();
                require(game.screen == journal, "Condition draft did not return to the task form");
                require(QuestRepository.find(ID) == null, "Condition draft created task before main save");
                button(journal, "edit-form-conditions").onPress();
                require(button(game.screen, "condition-delete-0") != null, "Draft condition was lost on reopen");
                button(game.screen, "conditions-save").onPress();
                button(journal, "create").onPress();
            }
            case 1 -> {
                if (field(journal, "pending") != null) return false;
                require(QuestRepository.find(ID) != null, "Task creation failed");
                require(QuestRepository.find(ID).triggers().size() == 1, "Creation did not save condition draft");
                original = QuestRepository.source(ID);
                invoke("editProperties", new Class<?>[]{String.class, String.class}, "task", ID);
                button(journal, "edit-form-conditions").onPress();
                button(game.screen, "conditions-add").onPress();
                button(game.screen, "conditions-save").onPress();
                require(QuestRepository.source(ID).equals(original), "Property subeditor wrote early");
                button(journal, "cancel").onPress();
                require(QuestRepository.source(ID).equals(original), "Cancel published condition changes");
                invoke("editProperties", new Class<?>[]{String.class, String.class}, "task", ID);
                button(journal, "edit-form-conditions").onPress();
                button(game.screen, "conditions-add").onPress();
                button(game.screen, "conditions-save").onPress();
                button(journal, "create").onPress();
            }
            case 2 -> {
                if (field(journal, "pending") != null) return false;
                require(QuestRepository.find(ID).triggers().size() == 2, "Property save omitted condition draft");
                invoke("editProperties", new Class<?>[]{String.class, String.class}, "task", ID);
                button(journal, "edit-form-conditions").onPress();
                button(game.screen, "condition-delete-0").onPress();
                button(game.screen, "condition-delete-0").onPress();
                button(game.screen, "conditions-save").onPress();
                button(journal, "create").onPress();
            }
            case 3 -> {
                if (field(journal, "pending") != null) return false;
                require(QuestRepository.find(ID).triggers().equals(List.of()), "Clearing all conditions was ignored");
                invoke("editProperties", new Class<?>[]{String.class, String.class}, "task", ID);
            }
            case 4 -> {
                try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    image.writeToFile(Path.of("..", "build", "verification", "conditions-property-form.png"));
                }
                game.setScreen(null);
                return true;
            }
            default -> throw new AssertionError("Unknown condition form stage");
        }
        stage++;
        return false;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(String name, Class<?>[] types, Object... arguments) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(journal, arguments);
    }

    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (condition form stage " + stage + ")");
    }
}
