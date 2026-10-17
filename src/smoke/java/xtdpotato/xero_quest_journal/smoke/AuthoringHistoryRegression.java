package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Saves, deletes and restores actual definitions, then drives the journal toolbar and shortcuts. */
final class AuthoringHistoryRegression {
    private static final String ID = "history_" + System.currentTimeMillis();
    private static int stage, wait;
    private static CompletableFuture<Void> work;
    private static QuestScreen journal;

    static boolean tick(Minecraft game) throws Exception {
        if (game.screen instanceof AnimatedQuestScreen screen && screen.inputBlocked()) return false;
        if (--wait > 0) return false;
        wait = 6;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                game.options.guiScale().set(2);
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920); game.getWindow().setHeight(1080); game.resizeDisplay();
                work = CompletableFuture.runAsync(() -> {
                    try { serverChecks(game); } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                journal = editJournal(game);
                require(button("undo").active, "Saved task edit did not enable Undo");
                button("undo").onPress();
            }
            case 2 -> {
                if (field(journal, "historyPending") != null) return false;
                require(QuestRepository.find(ID).title().equals("撤销测试：修改前"), "Toolbar did not undo the task file");
                require(journal.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL), "Redo shortcut was ignored");
            }
            case 3 -> {
                if (field(journal, "historyPending") != null) return false;
                require(QuestRepository.find(ID).title().equals("撤销测试：修改后"), "Ctrl+Y did not redo the saved edit");
                journal.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
            }
            case 4 -> {
                if (field(journal, "historyPending") != null) return false;
                require(QuestRepository.find(ID).title().equals("撤销测试：修改前"), "Ctrl+Z did not undo");
                journal.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
            }
            case 5 -> {
                if (field(journal, "historyPending") != null) return false;
                require(QuestRepository.find(ID).title().equals("撤销测试：修改后"), "Ctrl+Shift+Z did not redo");
                var method = QuestScreen.class.getDeclaredMethod("confirmDelete", Set.class, Set.class);
                method.setAccessible(true); method.invoke(journal, Set.of(ID), Set.of());
                @SuppressWarnings("unchecked") var menu = (List<StyledButton>) field(journal, "menu");
                menu.stream().filter(b -> b.styleId().equals("context-delete_confirm")).findFirst().orElseThrow().onPress();
            }
            case 6 -> {
                if (field(journal, "batchPending") != null) return false;
                require(QuestRepository.find(ID) == null, "Journal task deletion did not finish");
                // Reopen a fresh screen: the file history must outlive the screen's local layout stacks.
                journal = editJournal(game);
                require(button("undo").active, "Reopening the journal lost deletion history");
                button("undo").onPress();
            }
            case 7 -> {
                if (field(journal, "historyPending") != null) return false;
                require(QuestRepository.find(ID) != null && field(journal, "selectedId").equals(ID),
                        "Restoring deletion did not restore/select its task");
                require(button("redo").active, "Restored deletion did not enable Redo");
                Path output = Path.of("..", "build", "verification");
                Files.createDirectories(output);
                try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("task-authoring-undo-zh-1920.png"));
                }
                button("redo").onPress();
            }
            case 8 -> {
                if (field(journal, "historyPending") != null) return false;
                require(QuestRepository.find(ID) == null, "Redo did not delete the task again");
                button("undo").onPress();
            }
            case 9 -> {
                if (field(journal, "historyPending") != null) return false;
                require(QuestRepository.find(ID) != null, "Second deletion undo failed");
                return true;
            }
            default -> throw new AssertionError("Unknown history test stage");
        }
        stage++;
        return false;
    }
    private static QuestScreen editJournal(Minecraft game) throws Exception {
        ClientEditMode.set(true);
        var screen = QuestScreen.task(null, ID);
        var mode = QuestScreen.class.getDeclaredField("editMode"); mode.setAccessible(true); mode.set(screen, true);
        game.setScreen(screen);
        return screen;
    }
    private static void serverChecks(Minecraft game) throws Exception {
        var server = game.getSingleplayerServer();
        var player = server.getPlayerList().getPlayer(game.player.getUUID());
        player.getPersistentData().putBoolean("xero_quest_edit_mode", true);
        Path root = QuestRepository.root();
        Path task = root.resolve(ID + ".json"), child = root.resolve(ID + "_child.json");
        Path chapter = root.resolve(ID + ".chapter.json");
        String group = ID + "_group";
        require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP,
                group, "历史恢复测试", "", "", "", "normal", "", 1)).successful(), "Cannot create history fixture group");
        Files.writeString(task, "{\"id\":\"" + ID + "\",\"uid\":\"" + ID + "\",\"title\":\"撤销测试：修改前\","
                + "\"type\":\"world\",\"triggers\":[{\"type\":\"item_hold\",\"item\":\"minecraft:diamond\",\"count\":16}]}");
        Files.writeString(child, "{\"id\":\"" + ID + "_child\",\"title\":\"后续任务\",\"type\":\"world\",\"parents\":[\"" + ID + "\"]}");
        Files.writeString(chapter, "{\"id\":\"" + ID + "_chapter\",\"name\":\"章节引用\",\"group\":\""
                + group + "\",\"quests\":[\"" + ID + "\",\"" + ID + "_child\"]}");
        QuestRepository.reload();
        require(QuestRepository.errors().isEmpty(), "Invalid history fixture");
        var progress = QuestProgressData.get(server);
        progress.complete(player.getUUID(), QuestRepository.find(ID));
        CompoundTag progressBefore = progress.save(new CompoundTag(), server.registryAccess()).copy();

        String original = Files.readString(task);
        require(QuestAuthoring.edit(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, ID,
                "撤销测试：属性编辑", "属性保存测试", "", "", "world", "", 1, "minecraft:diamond"),
                hash(ID), true, false).successful(), "Task property edit failed");
        String edited = Files.readString(task);
        require(QuestAuthoringHistory.undo(player).successful() && Files.readString(task).equals(original), "Source undo failed");
        require(QuestAuthoringHistory.redo(player).successful() && Files.readString(task).equals(edited), "Source redo failed");
        var rewards = List.of(new QuestDefinition.Reward("item", "minecraft:emerald", 3, "epic", ""));
        require(QuestAuthoring.editRewards(player, ID, hash(ID), rewards, "manual").successful(), "Reward edit failed");
        require(QuestAuthoringHistory.undo(player).successful() && Files.readString(task).equals(edited), "Reward undo failed");
        require(QuestAuthoringHistory.redo(player).successful()
                && QuestRepository.find(ID).rewards().equals(rewards), "Reward redo failed");
        String beforeConditions = Files.readString(task);
        require(QuestAuthoring.editConditions(player, ID, hash(ID),
                List.of(new QuestDefinition.Trigger("item_hold", "minecraft:apple", 4))).successful(), "Condition edit failed");
        require(QuestAuthoringHistory.undo(player).successful() && Files.readString(task).equals(beforeConditions),
                "Condition undo did not preserve rewards");
        var count = QuestAuthoringHistory.state(player);
        require(!QuestAuthoring.editSource(player, ID, "stale", "{}").successful(), "Invalid source edit accepted");
        require(QuestAuthoringHistory.state(player).redoCount() == count.redoCount(), "Rejected edit cleared redo");
        edit(player, ID, Files.readString(task).replace("属性编辑", "新修改"));
        require(QuestAuthoringHistory.state(player).redoCount() == 0, "New edit did not invalidate redo");

        var beforeDelete = QuestAuthoringHistory.capture(List.of(task, child, chapter));
        require(QuestBatchAuthoring.delete(player, Set.of(ID), Set.of()).successful(), "Batch delete failed");
        require(!Files.exists(task) && QuestRepository.find(ID + "_child").parents().isEmpty(), "Delete references not updated");
        var afterDelete = QuestAuthoringHistory.capture(List.of(task, child, chapter));
        Files.writeString(child, Files.readString(child).replace("后续任务", "外部修改"));
        var conflictState = QuestAuthoringHistory.capture(List.of(task, child, chapter));
        require("conflict".equals(QuestAuthoringHistory.undo(player).error()), "External file overwrite was allowed");
        require(conflictState.equals(QuestAuthoringHistory.capture(List.of(task, child, chapter))),
                "Conflict changed part of the batch");
        AtomicFiles.write(child, afterDelete.get(child), true); QuestRepository.reload();
        require(QuestAuthoringHistory.undo(player).successful(), "Batch delete undo failed");
        require(beforeDelete.equals(QuestAuthoringHistory.capture(List.of(task, child, chapter))),
                "Batch undo did not restore exact sources and references");
        require(QuestAuthoringHistory.redo(player).successful() && !Files.exists(task), "Batch redo failed");
        require(QuestAuthoringHistory.undo(player).successful(), "Batch second undo failed");

        Path manifest = QuestRepository.fileOf(group);
        String groupSource = Files.readString(manifest);
        require(QuestBatchAuthoring.delete(player, Set.of(), Set.of(group)).successful(), "Group delete failed");
        require(QuestAuthoringHistory.undo(player).successful() && Files.readString(manifest).equals(groupSource)
                && Files.exists(chapter), "Group and chapter restore failed");
        String taskBeforeVisual = Files.readString(task);
        var visual = new QuestPresentation(true, 395, 275, List.of(
                new QuestPresentation.Control("heading", "text", "任务控件修改", 12, 16, 180, 20,
                        "", Map.of(), TriggerGraph.empty())));
        edit(player, ID, QuestPresentationDocuments.write(taskBeforeVisual, visual));
        require(QuestAuthoringHistory.undo(player).successful() && Files.readString(task).equals(taskBeforeVisual),
                "Visual control undo failed");
        require(QuestAuthoringHistory.redo(player).successful()
                && QuestRepository.find(ID).presentation().equals(visual), "Visual control redo failed");
        require(QuestAuthoringHistory.undo(player).successful(), "Cannot restore pre-visual task");
        var template = xtdpotato.xero_quest_journal.core.QuestTemplateStore.all().stream()
                .filter(value -> !value.archived() && !value.elements().isEmpty()).findFirst().orElseThrow();
        String templateHash = xtdpotato.xero_quest_journal.core.QuestTemplateStore.fingerprint(template.id());
        var changedTemplate = template.withElements(List.of());
        require(xtdpotato.xero_quest_journal.core.QuestTemplateStore.save(player, changedTemplate, templateHash).isEmpty(),
                "Template save failed");
        require(QuestAuthoringHistory.undo(player).successful()
                && xtdpotato.xero_quest_journal.core.QuestTemplateStore.all().contains(template), "Template undo failed");
        require(QuestAuthoringHistory.redo(player).successful()
                && xtdpotato.xero_quest_journal.core.QuestTemplateStore.all().contains(changedTemplate), "Template redo failed");
        require(QuestAuthoringHistory.undo(player).successful(), "Cannot restore template fixture");
        require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                ID + "_new", "新建撤销", "", "", "", "world", "", 1)).successful(), "Task creation failed");
        require(QuestAuthoringHistory.undo(player).successful() && QuestRepository.find(ID + "_new") == null,
                "Creation undo failed");
        require(QuestAuthoringHistory.redo(player).successful() && QuestRepository.find(ID + "_new") != null,
                "Creation redo failed");
        require(progressBefore.equals(progress.save(new CompoundTag(), server.registryAccess())),
                "History rewound player completion/frozen rewards");

        player.getPersistentData().putBoolean("xero_quest_edit_mode", false);
        require("permission".equals(QuestAuthoringHistory.undo(player).error()), "Undo bypassed edit permission");
        player.getPersistentData().putBoolean("xero_quest_edit_mode", true);
        String source = Files.readString(task);
        var json = JsonParser.parseString(source).getAsJsonObject();
        json.addProperty("title", "撤销测试：修改前");
        Files.writeString(task, json.toString()); QuestRepository.reload();
        edit(player, ID, Files.readString(task).replace("修改前", "修改后"));
    }
    private static String hash(String id) throws Exception { return QuestRepository.fingerprint(QuestRepository.source(id)); }
    private static void edit(ServerPlayer player, String id, String source) throws Exception {
        require(QuestAuthoring.editSource(player, id, hash(id), source).successful(), "Cannot save source " + id);
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static StyledButton button(String id) {
        return Minecraft.getInstance().screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
