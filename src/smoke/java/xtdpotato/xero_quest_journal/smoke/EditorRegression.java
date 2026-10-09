package xtdpotato.xero_quest_journal.smoke;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class EditorRegression {
    private static int phase;
    private static int ticks;
    private static int originalTaskCount;
    private static CompletableFuture<?> pending;
    private static String guideId;
    private static double initialScroll;
    private static String clipboard;
    private static String standaloneId;
    private static String groupedId;
    private static int movedTitleX;
    private static int desktopTitleWidth;

    public static void serverChecks(ServerPlayer player, String group, String chapter, String guide, String child) throws Exception {
        var server = player.getServer();
        var tasks = QuestClipboard.capture(List.of(guide, child));
        require(QuestClipboard.decode(QuestClipboard.encode(tasks)).equals(tasks), "Clipboard roundtrip");
        require(!QuestBatchAuthoring.paste(null, tasks, group, chapter, " Copy").successful(), "Unauthorized paste");
        require(!QuestBatchAuthoring.delete(null, Set.of(guide), Set.of()).successful(), "Unauthorized delete");
        var stranger = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "NoPermission"),
                ClientInformation.createDefault());
        require(QuestRuntime.reset(stranger, guide).equals("permission"), "Unauthorized reset");

        var definition = QuestRepository.find(guide);
        var data = QuestProgressData.get(server);
        require(data.entry(player.getUUID(), definition).claimed(), "Guide must have been claimed");
        require(QuestRuntime.reset(player, guide).equals("reset"), "Reset failed");
        require(data.entry(player.getUUID(), definition) == null, "Reset still complete");
        var restored = QuestProgressData.load(data.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
        require(restored.entry(player.getUUID(), definition) == null, "Reset not persisted");
        restored.complete(player.getUUID(), definition);
        require(!restored.entry(player.getUUID(), definition).claimed(), "Reset retained old claim record");
        require(QuestRuntime.activate(player, guide).equals("completed"), "Reset guide cannot complete again");
        require(QuestRuntime.claim(player, guide).equals("claimed"), "Reset guide cannot claim reward again");
        require(QuestRuntime.claim(player, guide).equals("already_claimed"), "Repeat claim after reset succeeded");
        require(data.entry(player.getUUID(), QuestRepository.find(child)).claimed(), "Reset erased successor");

        var copies = QuestBatchAuthoring.paste(player, tasks, group, chapter, " Copy");
        require(copies.successful(), "Batch copy rejected: " + copies.error());
        String parentCopy = copies.copies().get(guide);
        String childCopy = copies.copies().get(child);
        require(!QuestRepository.find(parentCopy).uid().equals(definition.uid()), "Copied UID was reused");
        require(QuestRepository.find(childCopy).parents().equals(List.of(parentCopy)), "Copied parents not remapped");
        require(QuestRepository.find(parentCopy).title().endsWith(" Copy"), "Copy name missing suffix");
        require(QuestRepository.chapters().stream().anyMatch(c -> c.id().equals(chapter) && c.quests().contains(childCopy)),
                "Copy chapter membership missing");

        String source = QuestRepository.source(parentCopy);
        String edited = source + "\n";
        require(QuestAuthoring.editSource(player, parentCopy, QuestRepository.fingerprint(source), edited).successful(),
                "Source save failed");
        require(!QuestAuthoring.editSource(player, parentCopy, QuestRepository.fingerprint(source), source).successful(),
                "Stale source overwrote file");
        String invalid = QuestDocuments.replaceSection(edited, "parents", "<parents><parent id=\"missing_regression\"/></parents>");
        require(!QuestAuthoring.editSource(player, parentCopy, QuestRepository.fingerprint(edited), invalid).successful(),
                "Invalid source was published");
        require(QuestRepository.source(parentCopy).equals(edited), "Invalid source not rolled back");

        List<QuestClipboard.Task> brokenBatch = List.of(tasks.getFirst(),
                new QuestClipboard.Task("broken", ".html",
                        "<quest id=\"broken\"><parents><parent id=\"missing_regression\"/></parents></quest>"));
        int before = QuestRepository.all().size();
        require(!QuestBatchAuthoring.paste(player, brokenBatch, group, chapter, " Copy").successful(), "Bad batch published");
        require(QuestRepository.all().size() == before && QuestRepository.errors().isEmpty(), "Batch rollback lost definitions");

        require(QuestBatchAuthoring.delete(player, Set.of(parentCopy), Set.of()).successful(), "Delete failed");
        require(QuestRepository.find(parentCopy) == null && QuestRepository.find(childCopy).parents().isEmpty(),
                "Delete left dangling prerequisite");
        require(QuestRepository.chapters().stream().noneMatch(c -> c.quests().contains(parentCopy)), "Dangling chapter reference");
        require(QuestBatchAuthoring.delete(player, Set.of(childCopy), Set.of()).successful(), "Second delete failed");

        String disposable = QuestAuthoring.newId();
        require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP, disposable,
                "Disposable", "", group, "", "normal", "", 1)).successful(), "Nested group creation");
        require(QuestBatchAuthoring.paste(player, tasks, disposable, "", " Copy").successful(), "Nested task paste");
        require(QuestBatchAuthoring.delete(player, Set.of(), Set.of(disposable)).successful(), "Group deletion");
        require(!QuestRepository.idExists(disposable) && QuestRepository.errors().isEmpty(), "Group deletion invalid");

        List<QuestClipboard.Task> scrollTasks = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String id = "scroll_" + i;
            scrollTasks.add(new QuestClipboard.Task(id, ".html",
                    "<quest id=\"" + id + "\" name=\"Scroll " + i + "\" type=\"guide\"><trigger type=\"confirm\"/>"
                            + "<content><b>Original markup</b></content><script>const a = 'keep';</script></quest>"));
        }
        require(QuestBatchAuthoring.paste(player, scrollTasks, group, chapter, " Copy").successful(), "Scroll fixture");
        standaloneId = "standalone_" + QuestAuthoring.newId();
        AtomicFiles.write(QuestRepository.root().resolve(standaloneId + ".html"),
                "<quest id=\"" + standaloneId + "\" name=\"Standalone task\"><trigger type=\"confirm\"/></quest>", false);
        QuestRepository.reload();
        require(QuestRepository.groupOf(standaloneId).isEmpty(), "Standalone fixture acquired a group");
        groupedId = group;
        checkListModel(guide, child);
    }

    private static void checkListModel(String guide, String child) {
        var groups = List.of(
                new xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group("a", "Group A", "Subtitle", ""),
                new xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group("b", "Empty group", "", ""));
        var tasks = List.of(QuestRepository.find(standaloneId), QuestRepository.find(child), QuestRepository.find(guide));
        java.util.function.Function<String, String> membership = id -> id.equals(standaloneId) ? "" : "a";
        var expanded = JournalListModel.build(tasks, groups, membership, Set.of(), true);
        require(expanded.rows().stream().map(JournalListModel.Row::key).toList().equals(List.of(
                "group:a", "quest:" + child, "quest:" + guide, "group:b", "quest:" + standaloneId)),
                "Group-first/standalone-last ordering");
        require(expanded.rows().get(0).height() > expanded.rows().get(3).height(), "Description has no reserved height");
        require(expanded.rows().get(1).indent() == 12 && expanded.rows().getLast().indent() == 0, "Task indentation");
        var collapsed = JournalListModel.build(tasks, groups, membership, Set.of("a"), true);
        require(collapsed.rows().size() == 3 && collapsed.contentHeight() < expanded.contentHeight(),
                "Collapsed tasks still occupy rows");
        var filtered = JournalListModel.build(tasks, groups, membership, Set.of(), false);
        require(filtered.rows().stream().noneMatch(row -> row.key().equals("group:b")), "Empty chapter group visible");
        require(JournalListModel.build(List.of(), List.of(), membership, Set.of(), true).contentHeight() == 0,
                "Empty list has scroll extent");
        for (int i = 1; i < expanded.rows().size(); i++) {
            var before = expanded.rows().get(i - 1);
            require(expanded.rows().get(i).offset() >= before.offset() + before.height(), "Rows overlap");
        }
    }

    public static boolean tick(Minecraft game, String guide) throws Exception {
        if (++ticks < 8) return false;
        ticks = 0;
        switch (phase) {
            case 0 -> {
                if (ClientQuestProgress.catalogRevision() != QuestRepository.revision()) return false;
                guideId = guide;
                clipboard = game.keyboardHandler.getClipboard();
                require(game.screen instanceof QuestScreen, "Expected journal");
                set(game.screen, "editMode", true);
                set(game.screen, "category", "main");
                set(game.screen, "chapterId", "");
                set(game.screen, "selectedId", guideId);
                set(game.screen, "revealSelectedPage", true);
                set(game.screen, "listScroll", 0.0);
                set(game.screen, "listTargetScroll", 0.0);
                @SuppressWarnings("unchecked")
                Set<String> collapsed = (Set<String>) field(game.screen, "collapsedGroupIds");
                collapsed.clear();
                ClientQuestDefinitions.groups().stream().map(group -> group.id())
                        .filter(id -> !id.equals(groupedId)).forEach(collapsed::add);
                game.screen.init(game, game.screen.width, game.screen.height);
                var model = (JournalListModel) field(game.screen, "journalList");
                revealRow(game, model.rows().stream().filter(row -> row.quest() != null)
                        .findFirst().orElseThrow().key());
                originalTaskCount = QuestRepository.all().size();
                String active = (String) field(game.screen, "selectedId");
                var rows = visibleRows(game);
                require(rows.size() >= 2, "Multi-select fixture needs two visible tasks");
                var search = (EditBox) field(game.screen, "taskSearch");
                game.keyboardHandler.setClipboard("search-paste-probe");
                search.setFocused(true);
                game.screen.setFocused(search);
                game.screen.keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
                require(search.getValue().equals("search-paste-probe"),
                        "Ctrl+V did not paste into the focused search field");
                click(game, rows.get(0), 0);
                require(!search.isFocused() && game.screen.getFocused() != search,
                        "Task-list click did not release search focus");
                game.keyboardHandler.setClipboard("task-paste-probe");
                game.screen.keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
                require(search.getValue().equals("search-paste-probe"),
                        "Task Ctrl+V pasted into the search field");
                require("create.error.clipboard".equals(field(game.screen, "error")),
                        "Ctrl+V without search focus was not handled as task paste");
                set(game.screen, "error", null);
                game.screen.keyPressed(GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_MOD_CONTROL);
                click(game, rows.get(0), 0);
                click(game, rows.get(1), 0);
                game.screen.keyReleased(GLFW.GLFW_KEY_LEFT_CONTROL, 0, 0);
                require(((Set<?>) field(game.screen, "selectedTaskIds")).size() >= 2, "Ctrl click did not multi-select");
                require(field(game.screen, "selectedId").equals(active), "Ctrl click activated task");
                game.screen.keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL);
                require(QuestClipboard.decode(game.keyboardHandler.getClipboard()).size() >= 2,
                        "Ctrl+C did not copy the selected tasks");
                click(game, rows.get(0), 1);
                menu(game, "copy_tasks");
                require(QuestClipboard.decode(game.keyboardHandler.getClipboard()).size() >= 2, "Clipboard selection lost");
                click(game, rows.get(0), 1);
                menu(game, "duplicate_tasks");
            }
            case 1 -> {
                if (field(game.screen, "batchPending") != null) return false;
                require(QuestRepository.all().size() >= originalTaskCount + 2, "Duplicate menu did not create tasks");
                originalTaskCount = QuestRepository.all().size();
                var rows = visibleRows(game);
                if (rows.isEmpty()) {
                    var model = (JournalListModel) field(game.screen, "journalList");
                    revealRow(game, model.rows().stream().filter(row -> row.quest() != null)
                            .findFirst().orElseThrow().key());
                    rows = visibleRows(game);
                }
                click(game, rows.get(0), 2);
            }
            case 2 -> {
                if (field(game.screen, "batchPending") != null) return false;
                require(QuestRepository.all().size() > originalTaskCount, "Middle click did not duplicate");
                var rows = visibleRows(game);
                if (rows.isEmpty()) {
                    var model = (JournalListModel) field(game.screen, "journalList");
                    revealRow(game, model.rows().stream().filter(row -> row.quest() != null)
                            .findFirst().orElseThrow().key());
                    rows = visibleRows(game);
                }
                click(game, rows.get(0), 1);
                menu(game, "delete_tasks");
                require(QuestRepository.all().size() > originalTaskCount, "Delete executed before confirmation");
                menu(game, "cancel");
                originalTaskCount = QuestRepository.all().size();
                click(game, rows.get(0), 1);
                menu(game, "delete_tasks");
                menu(game, "delete_confirm");
            }
            case 3 -> {
                if (field(game.screen, "batchPending") != null) return false;
                require(QuestRepository.all().size() < originalTaskCount, "Confirmed delete did not execute");
                set(game.screen, "groupId", "");
                set(game.screen, "chapterId", "");
                set(game.screen, "category", "main");
                set(game.screen, "listScroll", 0.0);
                set(game.screen, "listTargetScroll", 0.0);
                game.screen.init(game, game.screen.width, game.screen.height);
                initialScroll = (double) field(game.screen, "listScroll");
                game.screen.mouseScrolled(30, 130, 0, -3);
                require((double) field(game.screen, "listScroll") == initialScroll, "Wheel jumped instead of interpolating");
                require((double) field(game.screen, "listTargetScroll") > initialScroll, "Wheel target unchanged");
            }
            case 4 -> {
                require((double) field(game.screen, "listScroll") > initialScroll, "Smooth animation did not advance");
                int right = 16 + (int) field(game.screen, "listWidth");
                game.screen.mouseClicked(right - 2, 108, 0);
                game.screen.mouseDragged(right - 2, game.screen.height - 62, 0, 0, game.screen.height - 170);
                game.screen.mouseReleased(right - 2, game.screen.height - 62, 0);
                require((double) field(game.screen, "listScroll") > 100, "Scrollbar drag failed");
                String selected = (String) field(game.screen, "selectedId");
                StyledButton swipeRow = visibleRows(game).getFirst();
                double swipeX = Math.min(swipeRow.getRight() - 6, swipeRow.getX() + 36);
                double swipeY = swipeRow.getY() + swipeRow.getHeight() / 2.0;
                game.screen.mouseClicked(swipeX, swipeY, 0);
                game.screen.mouseDragged(swipeX, swipeY + 60, 0, 0, 60);
                game.screen.mouseReleased(swipeX, swipeY + 60, 0);
                require(field(game.screen, "selectedId").equals(selected), "Swipe triggered task click");
                double listBefore = (double) field(game.screen, "listScroll");
                double targetBefore = (double) field(game.screen, "listTargetScroll");
                game.screen.mouseScrolled(game.screen.width - 40, 130, 0, -1);
                require((double) field(game.screen, "listScroll") == listBefore
                        && (double) field(game.screen, "listTargetScroll") == targetBefore, "Details scrolled task list");
                capture(game, "19-smooth-scroll");
                // Return to the known guide for a reset through the actual payload and context menu.
                set(game.screen, "groupId", "");
                set(game.screen, "chapterId", "");
                set(game.screen, "selectedId", guideId);
                game.screen.init(game, game.screen.width, game.screen.height);
                reveal(game, guideId);
                click(game, find(game, "quest-" + guideId), 1);
                menu(game, "reset_progress");
                menu(game, "reset_confirm");
            }
            case 5 -> {
                if (field(game.screen, "actionPending") != null) return false;
                require(ClientQuestProgress.view(guideId).status().equals("active"), "Network reset not synchronized");
                require(!ClientQuestProgress.isTracked(guideId), "Reset kept tracking");
                click(game, find(game, "source"), 0);
                require(game.screen instanceof QuestSourceScreen, "Source editor did not open");
                var editor = game.screen.children().stream().filter(MultiLineEditBox.class::isInstance)
                        .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                editor.setValue(editor.getValue() + "\n<!-- smoke source edit -->");
            }
            case 6 -> {
                capture(game, "20-source-editor");
                click(game, find(game, "source-save"), 0);
            }
            case 7 -> {
                if (!(game.screen instanceof QuestScreen)) return false;
                require(QuestRepository.source(guideId).contains("smoke source edit"), "Source editor did not persist");
                context(game, 20, 80, "system:add-group");
                require(menuEntries(game).stream().anyMatch(b -> b.styleId().equals("context-paste_tasks")),
                        "Add context menu lacks paste");
                game.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            case 8 -> {
                capture(game, "21-group-menu");
                game.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                game.setScreen(QuestScreen.overview((QuestScreen) game.screen));
                set(game.screen, "editMode", true);
                set(game.screen, "groupId", groupedId);
                set(game.screen, "chapterId", "");
                set(game.screen, "selectedId", guideId);
                set(game.screen, "listScroll", 0.0);
                set(game.screen, "listTargetScroll", 0.0);
                @SuppressWarnings("unchecked")
                Set<String> collapsed = (Set<String>) field(game.screen, "collapsedGroupIds");
                collapsed.clear();
                ClientQuestDefinitions.groups().stream().map(group -> group.id())
                        .filter(id -> !id.equals(groupedId)).forEach(collapsed::add);
                game.screen.init(game, game.screen.width, game.screen.height);
            }
            case 9 -> {
                capture(game, "22-group-expanded");
                var header = find(game, "group-" + groupedId);
                require(visibleRows(game).getFirst().getX() > header.getX(), "Tasks not indented");
                ((Set<?>) field(game.screen, "selectedGroupIds")).clear();
                game.screen.keyPressed(GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_MOD_CONTROL);
                click(game, header, 0);
                game.screen.keyReleased(GLFW.GLFW_KEY_LEFT_CONTROL, 0, 0);
                require(((Set<?>) field(game.screen, "selectedGroupIds")).contains(groupedId), "Header Ctrl selection");
                require(!((Set<?>) field(game.screen, "collapsedGroupIds")).contains(groupedId), "Ctrl folded group");
                click(game, header, 0);
                require(field(game.screen, "selectedId").equals(guideId), "Folding changed selected task");
                require(game.screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).noneMatch(b -> b.styleId().startsWith("quest-")),
                        "Folded tasks still receive input");
                require((double) field(game.screen, "listScroll") == 0, "Folded list scroll not clamped");
            }
            case 10 -> {
                capture(game, "23-group-collapsed");
                game.screen.init(game, game.screen.width, game.screen.height);
                require(((Set<?>) field(game.screen, "collapsedGroupIds")).contains(groupedId), "Rebuild lost fold state");
                // Header properties must not use the current filter as the target.
                set(game.screen, "groupId", "");
                set(game.screen, "editMode", true);
                game.screen.init(game, game.screen.width, game.screen.height);
                revealRow(game, "group:" + groupedId);
                StyledButton groupButton = find(game, "group-" + groupedId);
                set(game.screen, "editMode", true);
                context(game, groupButton.getX(), groupButton.getY(), "group:" + groupedId);
                menu(game, "properties");
                require(game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .findFirst().orElseThrow().getValue().equals(groupedId), "Header edited another group");
                click(game, find(game, "cancel"), 0);
                revealRow(game, "group:" + groupedId);
                click(game, find(game, "group-" + groupedId), 0);
                require(!((Set<?>) field(game.screen, "collapsedGroupIds")).contains(groupedId), "Header cannot expand twice");
                var model = (JournalListModel) field(game.screen, "journalList");
                boolean standaloneSeen = false;
                for (var row : model.rows()) {
                    if (row.quest() != null && QuestRepository.groupOf(row.quest().id()).isEmpty()) standaloneSeen = true;
                    else require(!standaloneSeen, "Grouped row after standalone task");
                }
                require(standaloneSeen, "Standalone tasks missing");
                reveal(game, standaloneId);
            }
            case 11 -> {
                capture(game, "24-standalone-last");
                set(game.screen, "category", "active");
                set(game.screen, "groupId", groupedId);
                set(game.screen, "listScroll", 0.0);
                set(game.screen, "listTargetScroll", 0.0);
                game.screen.init(game, game.screen.width, game.screen.height);
                var header = find(game, "group-" + groupedId);
                int y = header.getY() + header.getHeight() / 2;
                int swipeX = header.getX() + 32;
                game.screen.mouseClicked(swipeX, y, 0);
                game.screen.mouseDragged(swipeX, y + 30, 0, 0, 30);
                game.screen.mouseReleased(swipeX, y + 30, 0);
                require(!((Set<?>) field(game.screen, "collapsedGroupIds")).contains(groupedId), "Swipe folded header");
                set(game.screen, "editMode", false);
                game.screen.init(game, game.screen.width, game.screen.height);
                require(game.screen.children().stream().filter(StyledButton.class::isInstance)
                                .map(StyledButton.class::cast).noneMatch(b -> b.styleId().equals("category-add")),
                        "Preview still shows the task-type add button");
                boolean hasVisibleGroup = ((JournalListModel) field(game.screen, "journalList")).rows().stream()
                        .anyMatch(row -> row.key().equals("group:" + groupedId));
                if (hasVisibleGroup) {
                    revealRow(game, "group:" + groupedId);
                    var previewHeader = game.screen.children().stream().filter(StyledButton.class::isInstance)
                            .map(StyledButton.class::cast)
                            .filter(button -> button.styleId().equals("group-" + groupedId) && button.visible)
                            .findFirst();
                    if (previewHeader.isPresent()) {
                        click(game, previewHeader.get(), 1);
                        require(menuEntries(game).isEmpty(), "Preview opened a right-click menu");
                        click(game, previewHeader.get(), 0);
                        require(((Set<?>) field(game.screen, "collapsedGroupIds")).contains(groupedId), "Preview cannot fold");
                        click(game, previewHeader.get(), 0);
                        require(!((Set<?>) field(game.screen, "collapsedGroupIds")).contains(groupedId), "Preview cannot expand");
                    }
                }
                set(game.screen, "editMode", true);
                set(game.screen, "listPage", 0);
                game.screen.init(game, game.screen.width, game.screen.height);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                GLFW.glfwSetCursorPos(game.getWindow().getWindow(), 1000, 750);
                game.resizeDisplay();
            }
            case 12 -> {
                revealRow(game, "group:" + groupedId);
                require(find(game, "group-" + groupedId).visible, "Resize lost group header");
                var layout = (LayoutEditorState) field(game.screen, "layout");
                require(layout.position("builtin:" + guideId + ":title", 0, 0).x() >= 0,
                        "Task coordinates are not relative to the canvas");
                desktopTitleWidth = displayWidth(game, layout.elements().get("builtin:" + guideId + ":title"));
                require(desktopTitleWidth > 0, "Desktop task element has no display width");
                capture(game, "25-group-desktop");
                var position = layout.position("builtin:" + guideId + ":title", 0, 0);
                movedTitleX = position.x() + 8;
                layout.setPosition("builtin:" + guideId + ":title", movedTitleX, position.y());
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 854, 480);
                game.resizeDisplay();
            }
            case 13 -> {
                var layout = (LayoutEditorState) field(game.screen, "layout");
                require(layout.position("builtin:" + guideId + ":title", 0, 0).x() == movedTitleX,
                        "Resize overwrote custom title position");
                require(displayWidth(game, layout.elements().get("builtin:" + guideId + ":title"))
                                <= desktopTitleWidth,
                        "Task element grew when the canvas narrowed");
                require(layout.elements().get("builtin:" + guideId + ":title").attribute("journal_anchor", "").isEmpty(),
                        "Manual move did not relinquish automatic positioning");
                game.keyboardHandler.setClipboard(clipboard);
                return true;
            }
            default -> throw new IllegalStateException("Unknown editor phase " + phase);
        }
        phase++;
        return false;
    }

    @SuppressWarnings("unchecked")
    public static List<StyledButton> menuEntries(Minecraft game) throws Exception {
        return (List<StyledButton>) field(game.screen, "menu");
    }
    public static void menu(Minecraft game, String name) throws Exception {
        StyledButton button = menuEntries(game).stream().filter(b -> b.styleId().equals("context-" + name))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing menu " + name));
        click(game, button, 0);
    }
    private static void context(Minecraft game, int x, int y, String target) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod("contextMenu", int.class, int.class, String.class);
        method.setAccessible(true);
        method.invoke(game.screen, x, y, target);
    }
    private static List<StyledButton> visibleRows(Minecraft game) throws Exception {
        var topMethod = game.screen.getClass().getDeclaredMethod("listTop");
        topMethod.setAccessible(true);
        int top = (int) topMethod.invoke(game.screen);
        var bottomMethod = game.screen.getClass().getDeclaredMethod("listBottom");
        bottomMethod.setAccessible(true);
        int bottom = (int) bottomMethod.invoke(game.screen);
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().startsWith("quest-") && b.visible && b.getY() >= top
                        && b.getBottom() <= bottom).toList();
    }
    private static StyledButton find(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void click(Minecraft game, StyledButton button, int mouseButton) {
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        var screen = game.screen;
        require(screen.mouseClicked(x, y, mouseButton), "Click not handled " + button.styleId());
        screen.mouseReleased(x, y, mouseButton);
    }
    public static void reveal(Minecraft game, String questId) throws Exception {
        @SuppressWarnings("unchecked")
        var collapsed = (Set<String>) field(game.screen, "collapsedGroupIds");
        collapsed.remove(QuestRepository.groupOf(questId));
        showPageContaining(game, questId);
        game.screen.init(game, game.screen.width, game.screen.height);
        revealRow(game, "quest:" + questId);
    }
    public static void revealRow(Minecraft game, String key) throws Exception {
        var model = (JournalListModel) field(game.screen, "journalList");
        if (model.rows().stream().noneMatch(row -> row.key().equals(key))) {
            if (key.startsWith("quest:")) {
                showPageContaining(game, key.substring("quest:".length()));
            } else if (key.startsWith("group:")) {
                String group = key.substring("group:".length());
                @SuppressWarnings("unchecked")
                List<xtdpotato.xero_quest_journal.core.model.QuestDefinition> quests =
                        (List<xtdpotato.xero_quest_journal.core.model.QuestDefinition>) field(game.screen, "quests");
                int index = -1;
                for (int i = 0; i < quests.size(); i++) {
                    if (ClientQuestDefinitions.groupOf(quests.get(i).id()).equals(group)) {
                        index = i;
                        break;
                    }
                }
                set(game.screen, "listPage", index < 0 ? 0 : index / 50);
            }
            game.screen.init(game, game.screen.width, game.screen.height);
            model = (JournalListModel) field(game.screen, "journalList");
        }
        int offset = model.rows().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow().offset();
        var topMethod = game.screen.getClass().getDeclaredMethod("listTop");
        topMethod.setAccessible(true);
        int top = (int) topMethod.invoke(game.screen);
        var bottomMethod = game.screen.getClass().getDeclaredMethod("listBottom");
        bottomMethod.setAccessible(true);
        int bottom = (int) bottomMethod.invoke(game.screen);
        double scroll = Math.max(0, Math.min(offset, model.contentHeight() - (bottom - top)));
        set(game.screen, "listScroll", scroll);
        set(game.screen, "listTargetScroll", scroll);
        game.screen.init(game, game.screen.width, game.screen.height);
    }
    public static void checkPagination(Minecraft game) throws Exception {
        @SuppressWarnings("unchecked")
        List<xtdpotato.xero_quest_journal.core.model.QuestDefinition> quests =
                (List<xtdpotato.xero_quest_journal.core.model.QuestDefinition>) field(game.screen, "quests");
        int pages = (int) field(game.screen, "listPages");
        var model = (JournalListModel) field(game.screen, "journalList");
        require(model.rows().stream().filter(row -> row.quest() != null).count() <= 50,
                "Task page contains more than 50 tasks");
        require(pages == Math.max(1, (quests.size() + 49) / 50), "Task page count is incorrect");
        if (pages > 1) {
            StyledButton next = find(game, "list-page-next");
            require(next.active, "Next task page is disabled");
            click(game, next, 0);
            require((int) field(game.screen, "listPage") == 1, "Next task page did not open");
            require(((JournalListModel) field(game.screen, "journalList")).rows().stream()
                    .filter(row -> row.quest() != null).count() <= 50,
                    "Second task page contains more than 50 tasks");
            StyledButton previous = find(game, "list-page-previous");
            require(previous.active, "Previous task page is disabled");
            click(game, previous, 0);
            require((int) field(game.screen, "listPage") == 0, "Previous task page did not open");
        }
    }
    private static void showPageContaining(Minecraft game, String questId) throws Exception {
        @SuppressWarnings("unchecked")
        List<xtdpotato.xero_quest_journal.core.model.QuestDefinition> quests =
                (List<xtdpotato.xero_quest_journal.core.model.QuestDefinition>) field(game.screen, "quests");
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id().equals(questId)) {
                set(game.screen, "listPage", i / 50);
                return;
            }
        }
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static int displayWidth(Minecraft game, LayoutEditorState.Element element) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod("displayWidth", LayoutEditorState.Element.class);
        method.setAccessible(true);
        return (int) method.invoke(game.screen, element);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void require(boolean test, String message) {
        if (!test) throw new IllegalStateException(message);
    }
}
