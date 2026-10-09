package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ActiveListRegression {
    private static final String PREFIX = "active_list_";
    private static int stage, wait;
    private static QuestScreen screen;
    private static boolean pagination;

    static void fixtures() throws Exception {
        for (String type : List.of("main", "legendary", "commission", "world", "other")) {
            Files.writeString(QuestRepository.root().resolve(PREFIX + type + ".json"),
                    "{\"id\":\"" + PREFIX + type + "\",\"title\":\"Active " + type
                            + "\",\"type\":\"" + type + "\",\"hidden\":" + type.equals("main")
                            + ",\"triggers\":[{\"type\":\"confirm\",\"item\":\"\",\"count\":1}]}");
        }
        Files.writeString(QuestRepository.root().resolve(PREFIX + "locked.json"), """
                {"id":"active_list_locked","title":"Locked task","type":"world",
                 "defaultUnlocked":false,"hideWhenLocked":true,
                 "triggers":[{"type":"confirm","item":"","count":1}]}
                """);
        QuestRepository.reload();
    }

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                if (ClientQuestProgress.view(PREFIX + "locked") == null) return false;
                pagination = QuestConfig.defaults().taskPagination;
                QuestConfig.defaults().taskPagination = false;
                screen = new QuestScreen();
                game.setScreen(screen);
                set(screen, "initialTaskResolved", true);
                set(screen, "category", "active");
                screen.init(game, screen.width, screen.height);
                checkRows(false);
                wait = 6;
            }
            case 1 -> {
                capture(game, "journal-active-categories");
                checkTools(game);
                set(screen, "editMode", true);
                screen.init(game, screen.width, screen.height);
                checkRows(true);
                var row = screen.children().stream().filter(JournalListButton.class::isInstance)
                        .map(JournalListButton.class::cast)
                        .filter(b -> b.styleId().equals("quest-" + PREFIX + "locked")).findFirst().orElseThrow();
                require(row.locked(), "Locked editor task has no lock status");
                var model = (JournalListModel) field(screen, "journalList");
                int offset = model.rows().stream().filter(r -> r.key().equals("quest:" + PREFIX + "locked"))
                        .findFirst().orElseThrow().offset();
                set(screen, "listScroll", (double) offset);
                set(screen, "listTargetScroll", (double) offset);
                wait = 6;
            }
            case 2 -> {
                capture(game, "journal-editor-lock");
                checkTools(game);
                game.getWindow().setWindowed(640, 480);
                game.getWindow().setWidth(640);
                game.getWindow().setHeight(480);
                game.resizeDisplay();
                wait = 6;
            }
            case 3 -> {
                checkTools(game);
                capture(game, "journal-circular-tools-small");
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.resizeDisplay();
                QuestConfig.defaults().taskPagination = pagination;
                return true;
            }
            default -> throw new AssertionError("Active list stage " + stage);
        }
        stage++;
        return false;
    }

    private static void checkRows(boolean editing) throws Exception {
        var model = (JournalListModel) field(screen, "journalList");
        Set<String> actual = model.rows().stream().filter(r -> r.quest() != null)
                .map(r -> r.quest().id()).collect(Collectors.toSet());
        Set<String> expected = ClientQuestDefinitions.all().stream().filter(q -> editing
                        || ClientQuestProgress.view(q.id()) != null && ClientQuestProgress.view(q.id()).unlocked()
                        && !ClientQuestProgress.view(q.id()).completed())
                .map(q -> q.id()).collect(Collectors.toSet());
        require(actual.equals(expected), "Active/editor task set differs: " + actual + " != " + expected);
        require(actual.contains(PREFIX + "main"), "Unlocked hidden task missing from active");
        require(actual.contains(PREFIX + "locked") == editing, "Locked task visibility differs by mode");
        List<String> sections = model.rows().stream().filter(JournalListModel.Row::section)
                .map(JournalListModel.Row::category).toList();
        List<String> builtinSections = sections.stream()
                .filter(List.of("main", "legendary", "commission", "world")::contains).toList();
        require(builtinSections.equals(List.of("main", "legendary", "commission", "world").stream()
                        .filter(builtinSections::contains).toList()),
                "Grouped category order changed: " + sections);
        boolean standaloneStarted = false;
        for (var row : model.rows()) {
            if (row.quest() != null && ClientQuestDefinitions.groupOf(row.quest().id()).isBlank())
                standaloneStarted = true;
            else require(!standaloneStarted, "A group or grouped task followed the standalone footer");
        }
        int other = -1, world = -1;
        for (int i = 0; i < model.rows().size(); i++) {
            var row = model.rows().get(i);
            if (row.category().equals("world")) world = i;
            if (row.key().equals("quest:" + PREFIX + "other")) other = i;
        }
        require(other > world, "Uncategorized task is not in the footer");
        int bottom = -1;
        for (var row : model.rows()) {
            require(row.offset() > bottom, "Rows overlap");
            bottom = row.offset() + row.height() - 1;
        }
        require(model.contentHeight() == bottom + 1, "Scroll content height differs from rows");
    }

    private static void checkTools(Minecraft game) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            double sx = image.getWidth() / (double) screen.width, sy = image.getHeight() / (double) screen.height;
            for (String id : List.of("settings", "edit", "close")) {
                var button = screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
                require(button.getWidth() <= 24 && button.getHeight() <= 24, "Oversized header tool " + id);
                int left = button.getX(), top = button.getY() + (button.getHeight() - button.getWidth()) / 2;
                int corner = image.getPixelRGBA((int) ((left + .5) * sx), (int) ((top + .5) * sy)) & 0xFFFFFF;
                int rim = image.getPixelRGBA((int) ((left + button.getWidth() / 2.) * sx),
                        (int) ((top + 1.5) * sy)) & 0xFFFFFF;
                require((corner & 255) < 230 && (rim & 255) >= 240 && (rim >>> 8 & 255) >= 240
                        && (rim >>> 16 & 255) >= 240, "Tool is not a small white circle: " + id);
            }
        }
    }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
