package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.gui.components.EditBox;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class ControlGraphRegression {
    private static final String ID = "graph_" + System.currentTimeMillis();
    private static final String GLOBAL = "globe.g" + System.currentTimeMillis();
    private static final String RENAMED = GLOBAL + "_new";
    private static final String ADAPTIVE_VARIABLE = GLOBAL + "_long_variable_name_that_must_remain_fully_visible";
    private static final String TASK_VARIABLE = ID + ".target";
    private static final Gson GSON = new Gson();
    private static CompletableFuture<Void> pending;
    private static int stage, wait, issueCaptureStage;
    private static QuestScreen journal;
    private static QuestTriggerGraphScreen editor;
    private static TriggerGraph saved;
    private static QuestPresentation presentation;
    private static long previewPixels;
    private static int originX, originZ;
    private static final Path OUTPUT = Path.of("..", "build", "verification");

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        wait = 12;
        if (pending != null && pending.isDone()) pending.join();
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                game.reloadResourcePacks();
                pending = CompletableFuture.runAsync(() -> setup(player(game)), game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone() || ClientQuestProgress.view(ID) == null) return false;
                pending.join();
                var structures = new QuestStructurePickerScreen(null, ignored -> {});
                game.setScreen(structures);
                @SuppressWarnings("unchecked") var marked = (Set<String>) field(structures, "marked");
                marked.add("confirm-test.nbt");
                call(structures, "confirmDelete", new Class<?>[0]);
                require(game.screen != structures, "Structure deletion confirmation did not open");
                require(button(game.screen, "overflow-confirm").getMessage().getString().equals("确定"),
                        "Structure deletion confirmation has the wrong action label");
                button(game.screen, "overflow-cancel").onPress();
                journal = QuestScreen.task(null, ID);
                set(journal, "editMode", true);
                set(journal, "searchQuery", ID);
                game.setScreen(journal);
                var layout = layout();
                var control = control("button");
                require(layout.position(control.id(), 0, 0).x() == 16, "Source position not loaded");
                require(control.attribute("textSize", "").equals("1.25"), "Source style not loaded");
                call(journal, "contextMenu", new Class<?>[]{int.class, int.class, String.class}, 280, 120, control.id());
                @SuppressWarnings("unchecked") var menu = (List<StyledButton>) field(journal, "menu");
                require(menu.stream().anyMatch(b -> b.styleId().equals("context-graph.settings")), "Missing trigger context action");
                menu.clear();
                call(journal, "openTriggerGraph", new Class<?>[]{String.class}, control.id());
                require(game.screen instanceof QuestTriggerGraphScreen, "Context trigger editor not opened");
                editor = (QuestTriggerGraphScreen) game.screen;
            }
            case 2 -> {
                if (issueCaptureStage == 1) {
                    capture(game, "control-graph-mail-body-issue");
                    var mailNode = ((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                            .filter(node -> node.type().equals("mail_send")).findFirst().orElseThrow();
                    set(editor, "wireFrom", "complete");
                    set(editor, "wirePort", "next");
                    call(editor, "connectTo", new Class<?>[]{TriggerGraph.Node.class}, mailNode);
                    require(field(editor, "graphIssue") != null
                                    && field(editor, "invalidLinkTarget").equals(mailNode.id()),
                            "Failed connection did not highlight both endpoints");
                    require(((Component) call(editor, "graphProblem", new Class<?>[0])).getString().contains("complete"),
                            "Failed connection did not name its source node");
                    issueCaptureStage = 2;
                    return false;
                }
                if (issueCaptureStage == 2) {
                    capture(game, "control-graph-connection-issue");
                    var mailNode = ((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                            .filter(node -> node.type().equals("mail_send")).findFirst().orElseThrow();
                    @SuppressWarnings("unchecked") var edges = (List<TriggerGraph.Edge>) field(editor, "edges");
                    var invalidEdge = new TriggerGraph.Edge("complete", mailNode.id(), "next");
                    edges.add(invalidEdge);
                    button(editor, "graph-save").onPress();
                    require(game.screen == editor && field(editor, "invalidLinkTarget").equals(mailNode.id()),
                            "Saving an invalid connection did not locate its target");
                    edges.remove(invalidEdge);
                    issueCaptureStage = 3;
                    return false;
                }
                if (issueCaptureStage == 3) {
                    capture(game, "control-graph-saved-connection-issue");
                    issueCaptureStage = 0;
                    button(editor, "graph-cancel").onPress();
                    require(game.screen.getClass().getSimpleName().equals("QuestConfirmScreen"),
                            "Dirty graph did not ask before discarding changes");
                    game.setScreen(editor);
                    button(editor, "graph-source").onPress();
                    var source = (MultiLineEditBox) field(editor, "source");
                    require(source.getValue().contains("eventType"), "JS source missing");
                    source.setValue("{broken");
                    button(editor, "graph-format").onPress();
                    require(button(editor, "graph-copy_problem").active, "Format did not expose a copyable problem");
                    button(editor, "graph-copy_problem").onPress();
                    require(game.keyboardHandler.getClipboard().contains("第"), "Graph problem was not copied");
                    button(editor, "graph-undo").onPress();
                    require(!source.getValue().equals("{broken"), "Graph source undo failed");
                    button(editor, "graph-redo").onPress();
                    require(source.getValue().equals("{broken"), "Graph source redo failed");
                    button(editor, "graph-save").onPress();
                    require(game.screen == editor, "Invalid graph source saved");
                    source.setValue(GSON.toJson(presentation.controls().getFirst().graph()));
                    button(editor, "graph-save").onPress();
                    require(game.screen == journal, "Valid graph correction could not save");
                    break;
                }
                capture(game, "control-graph-editor");
                editor.mouseClicked(45, 90, 0);
                editor.mouseDragged(65, 105, 0, 20, 15);
                editor.mouseReleased(65, 105, 0);
                call(editor, "addNode", new Class<?>[]{TriggerNodeLibrary.Entry.class},
                        TriggerNodeLibrary.entries("mail_send").getFirst());
                require(((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                        .anyMatch(node -> node.type().equals("mail_send")
                                && !node.get("mailId", "").isBlank()), "New mail node has no usable ID");
                call(editor, "update", new Class<?>[]{String.class, String.class}, "subject", "Subject");
                button(editor, "graph-save").onPress();
                require(game.screen == editor, "Mail with an empty body was saved");
                require(field(editor, "graphIssue") != null, "Invalid mail node was not highlighted");
                var problem = (Component) call(editor, "graphProblem", new Class<?>[0]);
                require(problem.getString().contains("邮件正文"), "Mail body issue lacks property detail");
                editor.mouseClicked(12, editor.height - 18, 0);
                require(game.keyboardHandler.getClipboard().contains("邮件正文"),
                        "Clicking graph issue did not copy its text");
                issueCaptureStage = 1;
                return false;
            }
            case 3 -> {
                if (busy()) return false;
                pending = CompletableFuture.runAsync(() -> runtime(player(game)), game.getSingleplayerServer());
            }
            case 4 -> {
                if (!pending.isDone()) return false;
                pending.join();
                if (!ClientControlState.properties(ID, "button").containsKey("value")) return false;
                require(ClientControlState.properties(ID, "button").get("value").equals("Jumped"), "Override not synchronized");
                var current = control("button");
                layout().setPosition(current.id(), 33, 47);
                call(journal, "saveLayout", new Class<?>[0]);
            }
            case 5 -> {
                if (busy()) return false;
                require(QuestRepository.find(ID).presentation().controls().getFirst().x() == 33, "Visual save not published");
                require(QuestRepository.find(ID).presentation().controls().getFirst().value().equals("Click me"), "Runtime override leaked into source");
                var other = QuestScreen.task(null, ID + "_copy");
                set(other, "searchQuery", ID + "_copy");
                game.setScreen(other);
                var otherLayout = (LayoutEditorState) field(other, "layout");
                long count = otherLayout.elements().values().stream()
                        .filter(e -> Set.of(ID, ID + "_copy").contains(e.quest()))
                        .filter(e -> e.attribute("journal_source_id", "").equals("button")).count();
                require(count == 2, "Different tasks overwrote shared control ID: " + count);
                game.setScreen(journal);
                call(journal, "openSourceEditor", new Class<?>[0]);
            }
            case 6 -> {
                if (!(game.screen instanceof QuestSourceScreen)) return false;
                require(QuestRepository.source(ID).contains("textSize"), "Task source omitted control attributes");
                game.setScreen(journal);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                var controls = new LinkedHashMap<String, QuestPresentation.Control>();
                presentation.controls().forEach(control -> controls.put(control.id(), control));
                editor = new QuestTriggerGraphScreen(journal, presentation.controls().getFirst().graph(),
                        "button", controls, value -> saved = value);
                game.setScreen(editor);
                set(editor, "selected", "property");
                call(editor, "init", new Class<?>[0]);
                @SuppressWarnings("unchecked") var propertyChoice = (StyledChoiceButton<String>) editor.children().stream()
                        .filter(StyledChoiceButton.class::isInstance).map(StyledChoiceButton.class::cast)
                        .filter(choice -> choice.styleId().equals("graph-property")).findFirst().orElseThrow();
                require(propertyChoice.getMessage().getString().equals("内容或资源"), "Property name not localized");
                propertyChoice.select("backgroundHover");
                var propertyNode = ((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                        .filter(node -> node.id().equals("property")).findFirst().orElseThrow();
                require(propertyNode.get("value", "").equals("#49685A"),
                        "Property selection did not load current control setting");
                button(editor, "graph-expand_value").onPress();
                require(game.screen != editor, "Expanded property editor did not open");
                var valueBox = game.screen.children().stream().filter(MultiLineEditBox.class::isInstance)
                        .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                valueBox.setValue("#112233");
                button(game.screen, "property-save").onPress();
                require(game.screen == editor && ((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                        .filter(node -> node.id().equals("property")).findFirst().orElseThrow()
                        .get("value", "").equals("#112233"), "Expanded property edit not saved");
                button(editor, "graph-variables").onPress();
                button(editor, "graph-variable.list").onPress();
                require(game.screen.getClass().getSimpleName().equals("QuestVariableListScreen"),
                        "Variable catalog did not open");
                require(game.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .count() == 2, "Variable catalog is missing independent search fields");
                game.screen.mouseClicked(25, 125, 0);
                button(game.screen, "variable-edit").onPress();
                require(game.screen.getClass().getSimpleName().equals("QuestVariableEditScreen"),
                        "Selected variable could not be edited");
                button(game.screen, "variable-cancel").onPress();
                require(game.screen.getClass().getSimpleName().equals("QuestVariableListScreen"),
                        "Variable editor did not return to catalog");
                button(game.screen, "variable-insert").onPress();
                require(game.screen == editor, "Variable catalog did not return to graph");
                button(editor, "graph-variables").onPress();
                button(editor, "graph-variable.insert").onPress();
                game.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow()
                        .setValue(RENAMED);
                game.screen.mouseClicked(25, 125, 0);
                button(game.screen, "variable-insert").onPress();
                var inserted = ((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                        .filter(node -> node.type().equals("variable")).findFirst().orElse(null);
                require(inserted != null,
                        "Place variable did not insert a comparison node");
                set(editor, "selected", "predicate");
                call(editor, "update", new Class<?>[]{String.class, String.class}, "mode", "visual");
                var predicate = ((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                        .filter(node -> node.id().equals("predicate")).findFirst().orElseThrow();
                double panX = (double) field(editor, "panX"), panY = (double) field(editor, "panY");
                double zoom = (double) field(editor, "zoom");
                double fromX = 8 + panX + inserted.x() * zoom + 20, fromY = 56 + panY + inserted.y() * zoom + 15;
                double toX = 8 + panX + predicate.x() * zoom + 30, toY = 56 + panY + predicate.y() * zoom + 20;
                editor.mouseClicked(fromX, fromY, 0);
                editor.mouseDragged(toX, toY, 0, toX - fromX, toY - fromY);
                editor.mouseReleased(toX, toY, 0);
                require(((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                        .filter(node -> node.id().equals("predicate")).findFirst().orElseThrow()
                        .get("field", "").equals("var:" + RENAMED),
                        "Dragging a variable onto a visual predicate did not bind it: "
                                + ((List<TriggerGraph.Node>) field(editor, "nodes")).stream()
                                .filter(node -> node.id().equals("predicate") || node.type().equals("variable")).toList()
                                + ", selected=" + field(editor, "selected")
                                + ", drag=" + field(editor, "dragging")
                                + ", viewport=" + editor.width + "x" + editor.height
                                + ", from=" + fromX + "," + fromY + " to=" + toX + "," + toY);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 480);
                game.resizeDisplay();
                editor.mouseClicked(40, 85, 0);
                editor.mouseReleased(40, 85, 0);
                assertWidgets(editor);
                editor.mouseScrolled(editor.width - 8, editor.height / 2.0, 0, -6);
                require(editor.children().stream().anyMatch(widget -> widget instanceof StyledChoiceButton<?> choice
                        && choice.styleId().equals("graph-once")), "Small inspector cannot scroll to last field");
                assertWidgets(editor);
            }
            case 7 -> {
                capture(game, "control-graph-small");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                // Use the actual port hit regions to connect a draft graph.
                var draft = new TriggerGraph(List.of(
                        node("start", "event", 0, 0, Map.of("event", "click")),
                        node("end", "end", 155, 65, Map.of())), List.of());
                editor = new QuestTriggerGraphScreen(journal, draft, value -> saved = value);
                game.setScreen(editor);
            }
            case 8 -> {
                require(editor.mouseClicked(144, 106, 0), "Output port not clickable");
                require(editor.mouseDragged(181, 171, 0, 37, 65), "Output wire could not be dragged");
                require(editor.mouseReleased(181, 171, 0), "Input port did not accept dragged wire");
                var selection = (java.util.Set<String>) field(editor, "selectedIds");
                selection.add("start");
                selection.add("end");
                require(editor.mouseClicked(45, 90, 0), "Could not grab selected nodes");
                require(editor.mouseDragged(65, 105, 0, 20, 15), "Could not drag selected nodes");
                editor.mouseReleased(65, 105, 0);
                var moved = (java.util.List<TriggerGraph.Node>) field(editor, "nodes");
                // Graph dragging snaps to the 24-unit grid (0+20 -> 24, 155+20 -> 168).
                require(moved.get(0).x() == 24 && moved.get(1).x() == 168,
                        "Multi-selection did not snap together");
                require(editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0),
                        "Arrow nudge not handled");
                require(moved.get(0).x() == 25 && moved.get(1).x() == 169,
                        "Arrow nudge snapped unexpectedly");
                call(editor, "rename", new Class<?>[]{String.class}, "end");
                require(((String) field(editor, "message")).contains("end"),
                        "Duplicate ID did not identify the conflicting node");
                call(editor, "rename", new Class<?>[]{String.class}, "start_new");
                require(((java.util.List<TriggerGraph.Edge>) field(editor, "edges")).getFirst()
                        .from().equals("start_new"), "Renaming a node lost its connection");
                editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_C, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                require(((java.util.List<?>) field(editor, "nodes")).size() == 4,
                        "Copying a selected group did not duplicate its nodes");
                button(editor, "graph-undo").onPress();
                selection.clear();
                selection.add("start_new");
                selection.add("end");
                require(editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0), "Delete shortcut not handled");
                require(((java.util.List<?>) field(editor, "nodes")).isEmpty(), "Multi-selection was not deleted");
                button(editor, "graph-undo").onPress();
                require(((java.util.List<?>) field(editor, "nodes")).size() == 2, "Multi-delete could not be undone");
                require(editor.mouseClicked(186, 156, 0), "Connection could not be selected");
                require(field(editor, "selectedEdge") != null, "Connection selection missing");
                require(editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0), "Connection Del not handled");
                require(((java.util.List<?>) field(editor, "edges")).isEmpty(), "Del did not remove selected connection");
                button(editor, "graph-undo").onPress();
                require(editor.mouseClicked(186, 156, 1), "Connection right click not handled");
                require(field(editor, "contextEdge") != null, "Connection context menu missing");
                int menuX = (int) field(editor, "contextX");
                int menuY = (int) field(editor, "contextY");
                editor.mouseClicked(menuX + 8, menuY + 8, 0);
                require(((java.util.List<?>) field(editor, "edges")).isEmpty(), "Context menu did not remove connection");
                button(editor, "graph-undo").onPress();
                button(editor, "graph-save").onPress();
                require(saved != null && saved.edges().size() == 1, "Visual wire not saved");
                ClientStructures.request(ID, "preview", "test.nbt");
                game.setScreen(new QuestStructurePreviewScreen(journal, "test.nbt"));
            }
            case 9 -> {
                require(ClientStructures.model("test.nbt") != null, "Preview did not load");
                previewPixels = capture(game, "structure-preview");
                require(previewPixels > 30, "Structure preview has no gold block pixels");
                game.screen.mouseDragged(220, 160, 0, 110, 0);
                game.screen.mouseScrolled(180, 130, 0, 1);
            }
            case 10 -> {
                require(capture(game, "structure-preview-rotated") > 30, "Rotated structure vanished");
                game.setScreen(null);
                pending = CompletableFuture.runAsync(() -> {
                    var p = player(game);
                    p.getInventory().clearContent();
                    p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
                    p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 9));
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 12000));
                    try { QuestStructures.enter(p, "test.nbt"); } catch (Exception e) { throw new RuntimeException(e); }
                    require(p.level().dimension().equals(QuestStructures.DIMENSION), "Scene dimension not entered");
                    require(p.getInventory().isEmpty() && p.getEnderChestInventory().isEmpty(), "Inventory not isolated");
                    require(!p.hasEffect(MobEffects.NIGHT_VISION), "Effects not isolated");
                    originX = (int) Math.floor(p.getX() + .5); originZ = (int) Math.floor(p.getZ());
                    require(p.level().getBlockState(new BlockPos(originX, 80, originZ)).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK),
                            "Structure blocks were not placed");
                    reject(() -> QuestStructures.enter(p, "test.nbt"), "Nested entry accepted");
                }, game.getSingleplayerServer());
            }
            case 11 -> {
                if (!pending.isDone() || !game.level.dimension().equals(QuestStructures.DIMENSION)) return false;
                pending.join();
                capture(game, "structure-entered");
                pending = CompletableFuture.runAsync(() -> {
                    var p = player(game);
                    p.getInventory().setItem(1, new ItemStack(Items.NETHERITE_INGOT, 64));
                    p.getEnderChestInventory().setItem(1, new ItemStack(Items.GOLD_INGOT, 64));
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
                    QuestStructures.leave(p);
                    restored(p);
                    QuestStructures.leave(p);
                    restored(p);
                    try { QuestStructures.enter(p, "test.nbt"); } catch (Exception e) { throw new RuntimeException(e); }
                    var death = new LivingDeathEvent(p, p.damageSources().generic());
                    NeoForge.EVENT_BUS.post(death);
                    require(death.isCanceled(), "Scene death was not canceled");
                    restored(p);
                    try { QuestStructures.enter(p, "test.nbt"); } catch (Exception e) { throw new RuntimeException(e); }
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
                    restored(p);
                    reject(() -> QuestStructures.read("../test.nbt"), "Traversal accepted");
                    reject(() -> QuestStructures.read("wrong.mcstructure"), "Bedrock format accepted");
                    reject(() -> QuestStructures.read("oversize.nbt"), "Oversize structure accepted");
                }, game.getSingleplayerServer());
            }
            case 12 -> {
                if (!pending.isDone()) return false;
                pending.join();
                ClientControlState.click(ID, "button");
            }
            case 13 -> {
                if (!ClientControlState.properties(ID, "button").getOrDefault("alpha", "").equals("0.5")) return false;
                ClientStructures.enter(ID, "enter");
            }
            case 14 -> {
                if (!game.level.dimension().equals(QuestStructures.DIMENSION)) return false;
                ClientStructures.leave();
            }
            case 15 -> {
                if (!game.level.dimension().equals(Level.OVERWORLD)) return false;
                pending = CompletableFuture.runAsync(() -> {
                    var p = player(game);
                    restored(p);
                    try {
                        String source = QuestRepository.source(ID);
                        var controls = new ArrayList<>(QuestRepository.find(ID).presentation().controls());
                        var old = controls.getFirst();
                        controls.set(0, new QuestPresentation.Control(old.id(), old.kind(), "Source changed", 93, old.y(),
                                old.width(), old.height(), old.parent(), old.properties(), old.graph()));
                        var current = QuestRepository.find(ID).presentation();
                        String updated = QuestPresentationDocuments.write(source,
                                new QuestPresentation(true, 320, 240, controls, current.variables()));
                        require(QuestAuthoring.editSource(p, ID, QuestRepository.fingerprint(source), updated).successful(), "Reverse source edit failed");
                        require(!QuestAuthoring.editSource(p, ID, QuestRepository.fingerprint(source), source).successful(), "Stale source edit accepted");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 16 -> {
                if (!pending.isDone()) return false;
                pending.join();
                // This editor still holds the pre-external-edit draft and must not overwrite the file.
                layout().setPosition(control("button").id(), 99, 47);
                call(journal, "saveLayout", new Class<?>[0]);
            }
            case 17 -> {
                if (busy()) return false;
                require(QuestRepository.find(ID).presentation().controls().getFirst().x() == 93, "Conflict overwrote external source");
                game.setScreen(journal);
                require(layout().position(control("button").id(), 0, 0).x() == 93, "Source edit not reflected in canvas");
                editor = new QuestTriggerGraphScreen(journal, presentation.controls().getFirst().graph(),
                        List.of("button", "preview"), value -> saved = value);
                game.setScreen(editor);
                button(editor, "graph-fit").onPress();
                button(editor, "graph-grid").onPress();
            }
            case 18 -> {
                capture(game, "control-graph-fitted");
                game.setScreen(journal);
                call(journal, "openTriggerGraph", new Class<?>[]{String.class}, control("button").id());
                editor = (QuestTriggerGraphScreen) game.screen;
                button(editor, "graph-variables").onPress();
                button(editor, "graph-variable.add_task").onPress();
                require(game.screen.getClass().getSimpleName().equals("QuestVariableEditScreen"),
                        "New task variable form was not opened");
                ((net.minecraft.client.gui.components.EditBox) field(game.screen, "name")).setValue("ui_value");
                ((net.minecraft.client.gui.components.EditBox) field(game.screen, "value")).setValue("12");
            }
            case 19 -> {
                assertWidgets(game.screen);
                capture(game, "variable-edit");
                button(game.screen, "variable-save").onPress();
            }
            case 20 -> {
                if (game.screen != editor) return false;
                require(QuestVariableStore.find(ID + ".ui_value") != null, "UI creation did not save variable");
                button(editor, "graph-variables").onPress();
                button(editor, "graph-variable.list").onPress();
                ((net.minecraft.client.gui.components.EditBox) field(game.screen, "taskSearch")).setValue("ui_value");
                game.screen.mouseClicked(game.screen.width / 2.0 + 12, 125, 0);
                game.screen.mouseReleased(game.screen.width / 2.0 + 12, 125, 0);
            }
            case 21 -> {
                assertWidgets(game.screen);
                capture(game, "variable-catalog");
                int maxScroll = (int) call(game.screen, "maxScroll", new Class<?>[]{int.class}, 0);
                if (maxScroll > 0) {
                    game.screen.mouseScrolled(25, 130, 0, -2);
                    require(((double[]) field(game.screen, "targetScroll"))[0] > 0, "Variable wheel scroll ignored");
                    int track = (int) call(game.screen, "trackX", new Class<?>[]{int.class}, 0);
                    game.screen.mouseClicked(track + 1, 130, 0);
                    game.screen.mouseDragged(track + 1, game.screen.height - 44, 0, 0, 60);
                    game.screen.mouseReleased(track + 1, game.screen.height - 44, 0);
                    require(((double[]) field(game.screen, "scroll"))[0] > 0, "Variable scrollbar not draggable");
                }
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 480);
                game.resizeDisplay();
            }
            case 22 -> {
                assertWidgets(game.screen);
                capture(game, "variable-catalog-small");
                var place = game.screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals("variable-insert")
                                && b.getX() > game.screen.width / 2).findFirst().orElseThrow();
                require(place.active, "Task variable selection did not enable insertion");
                place.onPress();
                require(game.screen == editor, "Variable list insert did not return to graph");
                button(editor, "graph-save").onPress();
            }
            case 23 -> {
                if (busy()) return false;
                require(game.screen == journal, "Graph with newly created variable did not save");
                var task = QuestRepository.find(ID);
                require(task.presentation().variables().stream().anyMatch(v -> v.id().equals(ID + ".ui_value")),
                        "Graph save lost variable definition");
                require(task.presentation().controls().getFirst().graph().nodes().stream()
                        .anyMatch(n -> n.get("variable", "").equals(ID + ".ui_value")),
                        "Graph publication conflicted after variable creation");
                call(journal, "openTriggerGraph", new Class<?>[]{String.class}, control("button").id());
                editor = (QuestTriggerGraphScreen) game.screen;
                editor.mouseClicked(40, 85, 0);
                editor.mouseReleased(40, 85, 0);
                button(editor, "graph-variables").onPress();
            }
            case 24 -> {
                capture(game, "variable-menu-small");
                var action = button(editor, "graph-variable.add_task");
                editor.mouseClicked(action.getX() + 5, action.getY() + 5, 0);
                editor.mouseReleased(action.getX() + 5, action.getY() + 5, 0);
                require(game.screen.getClass().getSimpleName().equals("QuestVariableEditScreen"),
                        "Variable menu click leaked through to the inspector");
                button(game.screen, "variable-cancel").onPress();
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                editor = new QuestTriggerGraphScreen(journal, TriggerGraph.empty(), ID, "button",
                        Map.of("button", presentation.controls().getFirst()), value -> saved = value);
                game.setScreen(editor);
                button(editor, "graph-add").onPress();
                require(game.screen instanceof QuestNodeLibraryScreen, "Add button did not open categorized node library");
            }
            case 25 -> {
                capture(game, "node-library-player");
                assertWidgets(game.screen);
                require(TriggerNodeLibrary.CATEGORIES.containsAll(List.of("player_event", "player_value",
                        "world_event", "world_value", "control_event", "control_value", "reset_property",
                        "reward", "condition", "script_condition", "kubejs_call", "kubejs_script")),
                        "Missing node categories");
                for (String category : TriggerNodeLibrary.CATEGORIES) for (var entry : TriggerNodeLibrary.entries(category)) {
                    require(!net.minecraft.network.chat.Component.translatable(
                            "screen.xero_quest_journal.graph." + entry.labelKey()).getString()
                            .startsWith("screen."), "Missing node entry translation: " + entry.labelKey());
                }
                require(TriggerNodeLibrary.paletteEntries("mail").stream().map(TriggerNodeLibrary.Entry::type)
                        .toList().equals(List.of("mail_send", "mail_check", "mail_delete", "mail_set_property",
                                "mail_lock_task", "mail_unlock_task")),
                        "Mail actions were not grouped in the palette");
                var visibleTypes = TriggerNodeLibrary.PALETTE_CATEGORIES.stream()
                        .flatMap(group -> TriggerNodeLibrary.paletteEntries(group).stream())
                        .map(TriggerNodeLibrary.Entry::category).distinct().toList();
                require(visibleTypes.containsAll(TriggerNodeLibrary.CATEGORIES)
                                && visibleTypes.size() == TriggerNodeLibrary.CATEGORIES.size(),
                        "Grouped palette lost or duplicated a node category");
                set(game.screen, "category", "task_actions");
                set(game.screen, "query", "command");
                call(game.screen, "init", new Class<?>[0]);
                @SuppressWarnings("unchecked") var commandResults =
                        (List<TriggerNodeLibrary.Entry>) call(game.screen, "entries", new Class<?>[0]);
                require(commandResults.size() == 1 && commandResults.getFirst().type().equals("command"),
                        "Cross-category node search missed command action");
                require(((Component) call(game.screen, "entryLabel",
                        new Class<?>[]{TriggerNodeLibrary.Entry.class}, commandResults.getFirst()))
                        .getString().equals("任务操作 - 执行命令"),
                        "Search result did not show palette category and node name");
                set(game.screen, "query", "");
                set(game.screen, "category", "kubejs_call");
                double[] scroll = (double[]) field(game.screen, "scroll"), target = (double[]) field(game.screen, "target");
                scroll[0] = target[0] = 11 * 40;
                call(game.screen, "init", new Class<?>[0]);
                double scale = game.getWindow().getGuiScale();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(game.getWindow().getWindow(),
                        (game.screen.width - 40) * scale, 80 * scale);
            }
            case 26 -> {
                capture(game, QuestKubeJs.available() ? "node-library-kubejs-enabled" : "node-library-kubejs-disabled");
                require(QuestNodeLibraryScreen.available("kubejs_call") == QuestKubeJs.available(), "Mod gate mismatch");
                if (!QuestKubeJs.available()) {
                    var palette = game.screen;
                    game.screen.mouseClicked(game.screen.width - 40, 80, 0);
                    game.screen.mouseReleased(game.screen.width - 40, 80, 0);
                    require(game.screen == palette, "Disabled KubeJS entry was inserted");
                    choosePalette(game, "kubejs_script");
                    require(game.screen == palette, "Disabled KubeJS script editor was opened");
                    require(net.minecraft.network.chat.Component.translatable(
                            "screen.xero_quest_journal.graph.requires_mod", "KubeJS").getString()
                            .equals("安装 KubeJS Mod 后可用"), "Missing dependency tooltip not localized");
                }
                choosePalette(game, "condition");
                require(game.screen == editor, "Condition category failed");
                var nodes = (List<TriggerGraph.Node>) field(editor, "nodes");
                require(nodes.getFirst().type().equals("js") && nodes.getFirst().get("mode", "").equals("visual"),
                        "Condition preset did not create visual condition");
                nodes.set(0, node(nodes.getFirst().id(), "js", 145, 50, nodes.getFirst().data()));
                button(editor, "graph-add").onPress();
                choosePalette(game, "world_value");
                nodes = (List<TriggerGraph.Node>) field(editor, "nodes");
                var context = nodes.getLast();
                require(context.type().equals("context") && context.get("field", "").equals("dimension"),
                        "World event variable preset");
                nodes.set(nodes.size() - 1, node(context.id(), "context", 0, 50, context.data()));
            }
            case 27 -> {
                var nodes = (List<TriggerGraph.Node>) field(editor, "nodes");
                var context = nodes.getLast();
                var condition = nodes.getFirst();
                double px = (double) field(editor, "panX"), py = (double) field(editor, "panY");
                double zoom = (double) field(editor, "zoom");
                double fx = 8 + px + context.x() * zoom + 20, fy = 56 + py + context.y() * zoom + 15;
                double tx = 8 + px + (condition.x()
                        + (int) call(editor, "nodeWidth", new Class<?>[]{TriggerGraph.Node.class}, condition) * .75) * zoom;
                double ty = 56 + py + condition.y() * zoom + 20;
                editor.mouseClicked(fx, fy, 0);
                editor.mouseDragged(tx, ty, 0, tx - fx, ty - fy);
                editor.mouseReleased(tx, ty, 0);
                require(nodes.size() == 1 && nodes.getFirst().get("expectedField", "").equals("dimension"),
                        "Event variable did not snap into right condition slot");
                button(editor, "graph-add").onPress();
                choosePalette(game, "reward");
                button(editor, "graph-custom_rewards").onPress();
                require(game.screen instanceof QuestRewardsScreen, "Custom reward editor missing");
                require(game.screen.getTitle().getString().equals("自定义奖励"), "Custom reward title not differentiated");
                require(game.screen.children().stream().noneMatch(child -> child instanceof StyledButton b
                        && b.styleId().equals("rewards-mode")), "Custom rewards exposed task reward mode");
                game.setScreen(editor);
                button(editor, "graph-add").onPress();
                choosePalette(game, "reset_property");
                require(((List<TriggerGraph.Node>) field(editor, "nodes")).getLast().type().equals("reset_property"),
                        "Restore property category failed");
            }
            case 28 -> {
                capture(game, "node-library-new-actions");
                if (QuestKubeJs.available()) {
                    button(editor, "graph-add").onPress();
                    choosePalette(game, "kubejs_script");
                    require(game.screen.getClass().getSimpleName().equals("QuestScriptEditorScreen"),
                            "KubeJS script preset did not open editor");
                    var script = (MultiLineEditBox) field(game.screen, "editor");
                    script.setValue("console.info(event.questId);");
                    button(game.screen, "script-save").onPress();
                    require(game.screen == editor && ((List<TriggerGraph.Node>) field(editor, "nodes"))
                            .getLast().get("script", "").equals("console.info(event.questId);"),
                            "KubeJS script editor failed to save into node");
                    button(editor, "graph-add").onPress();
                    choosePalette(game, "kubejs_call");
                    require(game.screen == editor && ((List<TriggerGraph.Node>) field(editor, "nodes"))
                            .getLast().type().equals("kubejs_call"), "Enabled KubeJS call node not inserted");
                }
            }
            case 29 -> {
                if (QuestKubeJs.available()) capture(game, "node-library-kubejs-inspector");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                editor = new QuestTriggerGraphScreen(journal, new TriggerGraph(List.of(
                        node("condition", "js", 150, 20, Map.of("mode", "visual", "logic", "and")),
                        node("world_value", "context", 0, 20, Map.of("field", "dayTime", "category", "world_value"))),
                        List.of()), ignored -> {});
                game.setScreen(editor);
                set(editor, "selected", "condition");
                call(editor, "init", new Class<?>[0]);
                for (String id : List.of("add", "undo", "redo", "fit", "variables", "grid", "source", "save")) {
                    var tool = button(editor, "graph-" + id);
                    require(tool instanceof EditorIconButton && tool.getWidth() == 24 && tool.getHeight() == 24
                                    && EditorIconButton.ICON_SIZE == 16 && EditorIconButton.PADDING == 4,
                            "Tool is not a compact icon with padding: " + id);
                }
                var choice = (StyledChoiceButton<?>) button(editor, "graph-field");
                choice.onPress();
                editor.setFocused(choice);
                double ix = (int) call(choice, "popupX", new Class<?>[0]) + 12;
                double iy = (int) call(choice, "popupY", new Class<?>[0]) + 12;
                double inspector = (double) field(editor, "inspectorTarget");
                double zoom = (double) field(editor, "zoom");
                editor.mouseScrolled(ix, iy, 0, -4);
                require((double) field(choice, "targetScroll") > 0 && (double) field(choice, "scroll") == 0,
                        "Choice menu is not smoothly scrolling");
                editor.mouseScrolled(16, 90, 0, -4);
                require((double) field(editor, "inspectorTarget") == inspector && (double) field(editor, "zoom") == zoom,
                        "Open menu allowed scroll through to inspector/canvas");
            }
            case 30 -> {
                capture(game, "graph-choice-smooth");
                var choice = (StyledChoiceButton<?>) button(editor, "graph-field");
                require(choice.isOpen() && (double) field(choice, "scroll") > 0, "Choice failed to animate");
                int x = (int) call(choice, "popupX", new Class<?>[0]);
                int y = (int) call(choice, "popupY", new Class<?>[0]);
                double before = (double) field(choice, "scroll");
                editor.mouseClicked(x + 15, y + 65, 0);
                editor.mouseDragged(x + 15, y + 20, 0, 0, -45);
                editor.mouseDragged(x + 15, y + 65, 0, 0, 45);
                editor.mouseReleased(x + 15, y + 65, 0);
                require(choice.isOpen(), "Drag returning to origin accidentally selected a menu option");
                int barX = x + (int) call(choice, "popupWidth", new Class<?>[0]) - 3;
                editor.mouseClicked(barX, y + 2, 0);
                editor.mouseDragged(barX, y + 3, 0, 0, 1);
                editor.mouseReleased(barX, y + 3, 0);
                require((double) field(choice, "scroll") < before, "Choice scrollbar cannot drag to top");
                editor.mouseClicked(1, 1, 0);
                require(!choice.isOpen(), "Outside click did not dismiss menu");
                set(editor, "visualSecond", true);
                var nodes = (List<TriggerGraph.Node>) field(editor, "nodes");
                double px = (double) field(editor, "panX"), py = (double) field(editor, "panY");
                double fx = 8 + px + 20, fy = 56 + py + 35;
                double tx = 8 + px + 150 + (int) call(editor, "nodeWidth",
                        new Class<?>[]{TriggerGraph.Node.class}, nodes.getFirst()) * .75, ty = fy + 5;
                editor.mouseClicked(fx, fy, 0);
                editor.mouseDragged(fx + 20, fy + 10, 0, 20, 10);
                var selected = nodes.stream().filter(n -> n.id().equals("world_value")).findFirst().orElseThrow();
                require(((EditBox) field(editor, "nodeX")).getValue().equals(Integer.toString(selected.x()))
                                && ((EditBox) field(editor, "nodeY")).getValue().equals(Integer.toString(selected.y())),
                        "Inspector position did not update during drag");
                editor.mouseDragged(tx, ty, 0, tx - fx - 20, ty - fy - 10);
                editor.mouseReleased(tx, ty, 0);
                require(nodes.size() == 1 && nodes.getFirst().get("expectedField2", "").equals("dayTime")
                        && nodes.getFirst().get("expectedField", "").isEmpty(), "World variable did not bind second clause");
                button(editor, "graph-undo").onPress();
                require(((List<?>) field(editor, "nodes")).size() == 2, "Binding undo did not restore variable");
                button(editor, "graph-redo").onPress();
            }
            case 31 -> {
                capture(game, "graph-icons-and-binding");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1440, 1000);
                game.resizeDisplay();
                var node = node("adaptive", "js", 10, 5, Map.of("mode", "visual", "logic", "and",
                        "field", "experienceLevel", "operator", "gte", "expectedField", "maxHealth",
                        "field2", "var:" + ADAPTIVE_VARIABLE,
                        "operator2", "contains", "expected2", "This complete expected value must wrap across multiple lines without truncation."));
                editor = new QuestTriggerGraphScreen(journal, new TriggerGraph(List.of(node), List.of()), ignored -> {});
                game.setScreen(editor);
                require((int) call(editor, "nodeWidth", new Class<?>[]{TriggerGraph.Node.class}, node) > 118
                                && (int) call(editor, "nodeHeight", new Class<?>[]{TriggerGraph.Node.class}, node) > 54,
                        "Long predicate did not grow in both dimensions");
                Object layout = call(editor, "nodeLayout", new Class<?>[]{TriggerGraph.Node.class}, node);
                List<?> slots = (List<?>) call(layout, "slots", new Class<?>[0]);
                require(slots.size() == 4, "AND predicate does not show both complete clauses");
                for (Object slot : slots) {
                    int w = (int) call(slot, "width", new Class<?>[0]);
                    int h = (int) call(slot, "height", new Class<?>[0]);
                    String value = (String) call(slot, "value", new Class<?>[0]);
                    require(game.font.split(net.minecraft.network.chat.Component.literal(value), w - 8).size()
                            * (game.font.lineHeight + 1) + 8 <= h, "Predicate slot clips full label");
                }
            }
            case 32 -> {
                capture(game, "graph-adaptive-predicate");
                var original = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                int naturalWidth = nodeSize(original, "Width"), naturalHeight = nodeSize(original, "Height");
                resizeNode(original, 1, .75, 100, 0);
                var wider = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                require(nodeSize(wider, "Width") == naturalWidth + 100 && wider.x() == original.x(),
                        "Right border did not change width or moved the opposite border");
                require(((EditBox) field(editor, "nodeWidthField")).getValue().equals(Integer.toString(naturalWidth + 100)),
                        "Width inspector did not update during resize");
                require(wider.get("layoutHeight", "").isEmpty(), "Horizontal resize froze automatic height");
                button(editor, "graph-undo").onPress();
                require(((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst().equals(original),
                        "Resize did not undo as a single operation");
                button(editor, "graph-redo").onPress();
                require(((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst().equals(wider), "Resize redo lost geometry");
                var graph = new TriggerGraph(List.of(wider), List.of());
                require(GSON.fromJson(GSON.toJson(graph), TriggerGraph.class).equals(graph), "Node size lost in source round-trip");
                saved = null;
                editor = new QuestTriggerGraphScreen(journal, graph, value -> saved = value);
                game.setScreen(editor);
                set(editor, "zoom", .6);
                set(editor, "panX", 80.);
                set(editor, "panY", 65.);
                int originalRight = wider.x() + nodeSize(wider, "Width");
                resizeNode(wider, 0, .75, -40, 0);
                var left = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                require(left.x() == wider.x() - 40 && left.x() + nodeSize(left, "Width") == originalRight,
                        "Left resize at non-unit zoom did not anchor right edge");
                int bottom = left.y() + nodeSize(left, "Height");
                resizeNode(left, .5, 0, 0, -30);
                var top = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                require(top.y() == left.y() - 30 && top.y() + nodeSize(top, "Height") == bottom,
                        "Top resize did not anchor bottom edge");
                resizeNode(top, .5, 1, 0, 40);
                var taller = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                require(taller.y() == top.y() && nodeSize(taller, "Height") == nodeSize(top, "Height") + 40,
                        "Bottom resize did not preserve top edge");
                for (int[] corner : List.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}, new int[]{1, 1})) {
                    var before = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                    resizeNode(before, corner[0], corner[1], corner[0] == 0 ? -10 : 10, corner[1] == 0 ? -10 : 10);
                    var after = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                    require(nodeSize(after, "Width") == nodeSize(before, "Width") + 10
                                    && nodeSize(after, "Height") == nodeSize(before, "Height") + 10,
                            "Corner did not resize both axes: " + Arrays.toString(corner));
                }
                button(editor, "graph-auto_size").onPress();
                var automatic = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                require(!automatic.data().containsKey("layoutWidth") && !automatic.data().containsKey("layoutHeight")
                                && nodeSize(automatic, "Width") == naturalWidth && nodeSize(automatic, "Height") == naturalHeight,
                        "Restore automatic size retained manual geometry");
                // Keep the very tall wrapped test entirely inside the viewport at this zoom.
                resizeNode(automatic, 1, .5, -200, 0);
                var narrow = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                require(nodeSize(narrow, "Width") == naturalWidth - 200
                                && nodeSize(narrow, "Height") > naturalHeight,
                        "Narrowing node did not reflow full predicate text");
                var layout = call(editor, "nodeLayout", new Class<?>[]{TriggerGraph.Node.class}, narrow);
                for (Object slot : (List<?>) call(layout, "slots", new Class<?>[0])) {
                    int w = (int) call(slot, "width", new Class<?>[0]);
                    int h = (int) call(slot, "height", new Class<?>[0]);
                    int y = (int) call(slot, "y", new Class<?>[0]);
                    String value = (String) call(slot, "value", new Class<?>[0]);
                    require(game.font.split(net.minecraft.network.chat.Component.literal(value), w - 8).size()
                                    * (game.font.lineHeight + 1) + 8 <= h && y + h <= nodeSize(narrow, "Height"),
                            "Manually narrowed node clips a predicate slot");
                }
                resizeNode(narrow, .5, 1, 0, -100);
                var minimum = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                require(nodeSize(minimum, "Height") == nodeSize(narrow, "Height"), "Resize clipped content below minimum height");
                button(editor, "graph-save").onPress();
                require(saved != null && saved.nodes().getFirst().equals(minimum), "Save lost resized geometry");
                editor = new QuestTriggerGraphScreen(journal, saved, ignored -> {});
                game.setScreen(editor);
                button(editor, "graph-fit").onPress();
                set(editor, "selected", minimum.id());
                ((Set<String>) field(editor, "selectedIds")).add(minimum.id());
                call(editor, "init", new Class<?>[0]);
                require(nodeSize(minimum, "Width") == naturalWidth - 200, "Reload changed resized width");
                for (String bad : List.of("-1", "4097", "NaN", "2147483648")) {
                    reject(() -> node("bad_size", "event", 0, 0, Map.of("layoutWidth", bad)), "Invalid width accepted: " + bad);
                    reject(() -> node("bad_size", "event", 0, 0, Map.of("layoutHeight", bad)), "Invalid height accepted: " + bad);
                }
            }
            case 33 -> {
                capture(game, "graph-resized-predicate");
                // Port hit testing takes priority over resize handles at the same border.
                var condition = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
                double x = (double) call(editor, "sx", new Class<?>[]{TriggerGraph.Node.class}, condition);
                double y = (double) call(editor, "portY", new Class<?>[]{TriggerGraph.Node.class, String.class}, condition, "true");
                x += nodeSize(condition, "Width") * (double) field(editor, "zoom");
                editor.mouseClicked(x, y, 0);
                require((boolean) field(editor, "wiring") && field(editor, "resizeOrigin") == null,
                        "Resize border intercepted connection port");
                editor.mouseReleased(x, y, 0);
                button(editor, "graph-add").onPress();
                set(game.screen, "category", "mail");
                call(game.screen, "init", new Class<?>[0]);
            }
            case 34 -> {
                capture(game, "node-library-mail-group");
                game.screen.mouseClicked(game.screen.width - 40, 68 + 40 + 10, 0);
                game.screen.mouseReleased(game.screen.width - 40, 68 + 40 + 10, 0);
                require(game.screen == editor && ((List<TriggerGraph.Node>) field(editor, "nodes"))
                        .getLast().type().equals("mail_check"), "Mail group did not select check action");
                return true;
            }
            default -> throw new AssertionError("Unknown graph stage " + stage);
        }
        stage++;
        return false;
    }
    private static int nodeSize(TriggerGraph.Node node, String dimension) throws Exception {
        return (int) call(editor, "node" + dimension, new Class<?>[]{TriggerGraph.Node.class}, node);
    }
    private static void resizeNode(TriggerGraph.Node node, double sideX, double sideY, int dx, int dy) throws Exception {
        double zoom = (double) field(editor, "zoom");
        double x = (double) call(editor, "sx", new Class<?>[]{TriggerGraph.Node.class}, node)
                + nodeSize(node, "Width") * zoom * sideX;
        double y = (double) call(editor, "sy", new Class<?>[]{TriggerGraph.Node.class}, node)
                + nodeSize(node, "Height") * zoom * sideY;
        require(editor.mouseClicked(x, y, 0) && field(editor, "resizeOrigin") != null, "Resize handle did not start drag");
        double targetX = x + dx * zoom, targetY = y + dy * zoom;
        editor.mouseDragged(targetX, targetY, 0, dx * zoom, dy * zoom);
        var current = ((List<TriggerGraph.Node>) field(editor, "nodes")).getFirst();
        require(((EditBox) field(editor, "nodeWidthField")).getValue().equals(Integer.toString(nodeSize(current, "Width")))
                        && ((EditBox) field(editor, "nodeHeightField")).getValue().equals(Integer.toString(nodeSize(current, "Height"))),
                "Resize dimensions did not update live");
        editor.mouseReleased(targetX, targetY, 0);
        require(field(editor, "resizeOrigin") == null, "Resize gesture did not end");
    }
    private static void choosePalette(Minecraft game, String category) throws Exception {
        set(game.screen, "category", category);
        call(game.screen, "init", new Class<?>[0]);
        game.screen.mouseClicked(game.screen.width - 40, 80, 0);
        game.screen.mouseReleased(game.screen.width - 40, 80, 0);
    }
    private static void restored(ServerPlayer p) {
        require(!QuestStructures.active(p) && p.level().dimension().equals(Level.OVERWORLD), "Did not return");
        require(p.getInventory().countItem(Items.DIAMOND) == 7 && p.getInventory().countItem(Items.NETHERITE_INGOT) == 0,
                "Inventory restore failed");
        require(p.getEnderChestInventory().getItem(0).getCount() == 9 && p.getEnderChestInventory().getItem(1).isEmpty(),
                "Ender inventory leaked");
        require(p.hasEffect(MobEffects.NIGHT_VISION) && !p.hasEffect(MobEffects.POISON), "Effects leaked");
    }
    private static void setup(ServerPlayer player) {
        try {
            var graph = new TriggerGraph(List.of(
                    node("jump", "event", 0, 0, Map.of("event", "jump", "once", "true")),
                    node("predicate", "js", 150, 0, Map.of("script", "eventType === 'jump' && 1 < 2 && '<&>' !== ''")),
                    node("property", "set_property", 300, 0, Map.of("property", "value", "value", "Jumped")),
                    node("complete", "complete", 300, 70, Map.of("target", ID + "_done")),
                    node("unlock", "unlock", 300, 140, Map.of("target", ID + "_locked")),
                    node("end", "end", 300, 210, Map.of()),
                    node("click", "event", 0, 100, Map.of("event", "click")),
                    node("clicked", "set_property", 150, 100, Map.of("property", "alpha", "value", "0.5")),
                    node("dimension", "event", 0, 175, Map.of("event", "dimension", "target", "minecraft:overworld")),
                    node("world", "set_property", 150, 175, Map.of("property", "textSize", "value", "2"))),
                    List.of(new TriggerGraph.Edge("jump", "predicate", "next"),
                            new TriggerGraph.Edge("predicate", "property", "true"),
                            new TriggerGraph.Edge("predicate", "complete", "true"),
                            new TriggerGraph.Edge("predicate", "unlock", "true"),
                            new TriggerGraph.Edge("predicate", "end", "false"),
                            new TriggerGraph.Edge("click", "clicked", "next"),
                            new TriggerGraph.Edge("dimension", "world", "next")));
            presentation = new QuestPresentation(true, 320, 240, List.of(
                    new QuestPresentation.Control("button", "button", "Click me", 16, 16, 100, 24, "",
                            Map.of("textSize", "1.25", "visibility", "visible", "background", "#334C44",
                                    "backgroundHover", "#49685A", "shape", "rounded",
                                    "cornerRadius", "4", "borderEnabledPressed", "false"), graph),
                    new QuestPresentation.Control("preview", "structure_preview", "test.nbt", 16, 60, 150, 120, "", Map.of(), TriggerGraph.empty()),
                    new QuestPresentation.Control("enter", "structure_enter", "test.nbt", 16, 192, 120, 24, "", Map.of(), TriggerGraph.empty()),
                    new QuestPresentation.Control("var_set", "button", "Set variable", 16, 218, 120, 20, "", Map.of(),
                            new TriggerGraph(List.of(
                                    node("start_var", "event", 0, 0, Map.of("event", "click")),
                                    node("assign_var", "set_variable", 150, 0,
                                            Map.of("variable", GLOBAL, "value", "5"))),
                                    List.of(new TriggerGraph.Edge("start_var", "assign_var", "next")))),
                    new QuestPresentation.Control("var_check", "button", "Check variable", 145, 218, 120, 20, "", Map.of(),
                            new TriggerGraph(List.of(
                                    node("start_check", "event", 0, 0, Map.of("event", "click")),
                                    node("compare_var", "js", 150, 0, Map.of("mode", "visual",
                                            "field", "var:" + GLOBAL, "operator", "eq",
                                            "expectedVar", TASK_VARIABLE)),
                                    node("matched", "set_property", 300, 0,
                                            Map.of("control", "button", "property", "value", "value", "Variable matched"))),
                                    List.of(new TriggerGraph.Edge("start_check", "compare_var", "next"),
                                            new TriggerGraph.Edge("compare_var", "matched", "true")))),
                    new QuestPresentation.Control("task_flags", "button", "Hide target", 0, 0, 90, 20, "", Map.of(),
                            new TriggerGraph(List.of(
                                    node("flag_start", "event", 0, 0, Map.of("event", "click")),
                                    node("flag_action", "set_task_property", 150, 0,
                                            Map.of("target", ID + "_locked", "property", "hidden", "value", "true"))),
                                    List.of(new TriggerGraph.Edge("flag_start", "flag_action", "next"))))));
            String source = QuestPresentationDocuments.write("<quest id=\"" + ID + "\" name=\"Graph test\" type=\"guide\"><content>Graph test</content></quest>", presentation);
            require(QuestRepository.parseDefinition(Path.of("test.html"), source).presentation().equals(presentation), "HTML round-trip");
            String selfClosing = QuestPresentationDocuments.write("<quest id=\"self\"/>", presentation);
            require(QuestRepository.parseDefinition(Path.of("test.xml"), selfClosing).presentation().equals(presentation), "Self-closing source");
            require(QuestRepository.parseDefinition(Path.of("test.html"), QuestSourceFormatter.format(source))
                    .presentation().equals(presentation), "Formatting changed control source");
            String json = QuestPresentationDocuments.write("{\"id\":\"" + ID + "\",\"title\":\"Graph test\",\"type\":\"guide\"}", presentation);
            require(QuestRepository.parseDefinition(Path.of("test.json"), json).presentation().equals(presentation), "JSON round-trip");
            require(!GSON.toJson(presentation.display()).contains("eventType"), "Catalog leaked JS");
            reject(() -> new TriggerGraph(List.of(node("a", "js", 0, 0, Map.of()), node("b", "js", 0, 0, Map.of())),
                    List.of(new TriggerGraph.Edge("a", "b", "true"), new TriggerGraph.Edge("b", "a", "false"))), "Cycle accepted");
            reject(() -> node("invalid", "set_task_property", 0, 0,
                    Map.of("property", "invalid", "value", "true")), "Invalid task property accepted");
            reject(() -> new TriggerGraph(graph.nodes(), List.of(new TriggerGraph.Edge("jump", "missing", "next"))), "Missing target accepted");
            require(ControlProperties.forKind("button").contains("backgroundHover")
                    && ControlProperties.forKind("button").contains("textSize")
                    && !ControlProperties.forKind("button").contains("autoplay"), "Button property choices");
            require(ControlProperties.forKind("video").contains("autoplay")
                    && !ControlProperties.forKind("video").contains("textSize"), "Media property choices");
            QuestEventGraphs.validateProperty("backgroundHover", "#49685A");
            QuestEventGraphs.validateProperty("width", "-2");
            reject(() -> QuestEventGraphs.validateProperty("eventGraph", "{}"), "Internal metadata accepted");
            reject(() -> QuestEventGraphs.validateProperty("volume", "2"), "Invalid volume accepted");
            var extended = new ArrayList<>(presentation.controls());
            extended.add(new QuestPresentation.Control("custom_reward", "button", "Custom reward", 0, 0, 80, 20, "", Map.of(),
                    new TriggerGraph(List.of(node("start_reward", "event", 0, 0, Map.of("event", "click", "cooldown", "1")),
                            node("reward_node", "reward", 150, 0, Map.of("once", "true", "rewards",
                                    GSON.toJson(List.of(new QuestDefinition.Reward("item", "minecraft:emerald", 3)))))),
                            List.of(new TriggerGraph.Edge("start_reward", "reward_node", "next")))));
            extended.add(new QuestPresentation.Control("command_action", "button", "Command", 0, 0, 80, 20, "", Map.of(),
                    new TriggerGraph(List.of(node("command_start", "event", 0, 0, Map.of("event", "click")),
                            node("command_node", "command", 150, 0,
                                    Map.of("command", "tag @s add xero_graph_command"))),
                            List.of(new TriggerGraph.Edge("command_start", "command_node", "next")))));
            extended.add(new QuestPresentation.Control("restore", "button", "Restore", 0, 0, 80, 20, "", Map.of(),
                    new TriggerGraph(List.of(node("start_restore", "event", 0, 0, Map.of("event", "click")),
                            node("restore_node", "reset_property", 150, 0, Map.of("control", "button", "property", "alpha"))),
                            List.of(new TriggerGraph.Edge("start_restore", "restore_node", "next")))));
            extended.add(new QuestPresentation.Control("weather", "button", "Weather", 0, 0, 80, 20, "", Map.of(),
                    new TriggerGraph(List.of(node("weather_start", "event", 0, 0,
                                    Map.of("event", "rain_start", "category", "world_event")),
                            node("weather_action", "set_property", 150, 0,
                                    Map.of("control", "weather", "property", "value", "value", "Raining"))),
                            List.of(new TriggerGraph.Edge("weather_start", "weather_action", "next")))));
            for (String kind : List.of("call", "script")) {
                var actionData = kind.equals("call")
                        ? Map.of("function", "global.xeroQuestSmokeCall", "once", "true")
                        : Map.of("script", "if (event.controlId !== 'kube_script' || event.eventType !== 'click') "
                                + "throw new Error('Missing script context'); global.xeroQuestSmoke.scripts++;", "once", "true");
                extended.add(new QuestPresentation.Control("kube_" + kind, "button", "KubeJS " + kind,
                        0, 0, 80, 20, "", Map.of(), new TriggerGraph(List.of(
                        node("kube_start", "event", 0, 0, Map.of("event", "click")),
                        node("kube_action", "kubejs_" + kind, 150, 0, actionData)),
                        List.of(new TriggerGraph.Edge("kube_start", "kube_action", "next")))));
            }
            var confidential = new TriggerGraph(List.of(node("secret", "reward", 0, 0,
                    Map.of("rewards", GSON.toJson(List.of(
                            new QuestDefinition.Reward("command", "say confidential-command", 1),
                            new QuestDefinition.Reward("script", "confidential-script.js", 1),
                            new QuestDefinition.Reward("function", "test:confidential-function", 1),
                            new QuestDefinition.Reward("item", "minecraft:emerald", 3)))))),
                    List.of());
            String publicRewards = GSON.toJson(confidential.display());
            require(!publicRewards.contains("confidential") && publicRewards.contains("minecraft:emerald"),
                    "Public reward graph leaked executable data or lost item display");
            presentation = new QuestPresentation(true, 320, 240, extended);
            source = QuestPresentationDocuments.write(source, presentation);
            Files.createDirectories(QuestRepository.root());
            Files.writeString(QuestRepository.root().resolve(ID + ".html"), source);
            Files.writeString(QuestRepository.root().resolve(ID + "_copy.html"),
                    QuestDocuments.identity(source, ID + "_copy", "Copy", UUID.randomUUID().toString()));
            Files.writeString(QuestRepository.root().resolve(ID + "_done.html"),
                    "<quest id=\"" + ID + "_done\" type=\"normal\" name=\"Graph target\"/>");
            Files.writeString(QuestRepository.root().resolve(ID + "_locked.html"),
                    "<quest id=\"" + ID + "_locked\" type=\"normal\" name=\"Locked\"><parents><parent id=\"" + ID + "\"/></parents></quest>");
            QuestRepository.reload();
            require(QuestRepository.errors().isEmpty(), "Definition errors: " + QuestRepository.errors());
            mailTaskActions(player);
            require(QuestVariableStore.save(player, ID, "", GLOBAL, "0", false).successful(),
                    "Global variable was not created");
            require(QuestVariableStore.save(player, ID, "", ADAPTIVE_VARIABLE, "0", false).successful(),
                    "Adaptive-node variable was not created");
            require(QuestVariableStore.save(player, ID, "", TASK_VARIABLE, "5", false).successful(),
                    "Task variable was not created");
            require(QuestVariableStore.save(player, ID + "_done", "", ID + "_done.score", "7", false).successful(),
                    "Task variable could not be added to an unauthored task canvas");
            require(!QuestRepository.find(ID + "_done").presentation().authored(),
                    "Adding a variable changed the task canvas to an empty authored canvas");
            require(QuestRepository.find(ID).presentation().variables().stream()
                    .anyMatch(variable -> variable.id().equals(TASK_VARIABLE)),
                    "Task variable is not part of its task source");
            require(QuestRepository.parseDefinition(QuestRepository.fileOf(ID), QuestRepository.source(ID))
                    .presentation().variables().stream().anyMatch(variable -> variable.id().equals(TASK_VARIABLE)),
                    "Task variable was not persisted to disk");
            String sharedVariables = Files.readString(QuestConfig.root().resolve("variables.json"));
            require(!sharedVariables.contains(TASK_VARIABLE), "Task variable leaked into shared file");
            var copied = QuestRepository.find(ID).presentation().remapTasks(Map.of(ID, ID + "_duplicate"));
            require(copied.variables().stream().anyMatch(variable ->
                    variable.id().equals(ID + "_duplicate.target")), "Copied task variable was not remapped");
            require(copied.controls().stream().filter(control -> control.id().equals("var_check"))
                    .flatMap(control -> control.graph().nodes().stream())
                    .anyMatch(node -> node.get("expectedVar", "").equals(ID + "_duplicate.target")),
                    "Copied task variable reference was not remapped");
            var taskPresentation = QuestRepository.find(ID).presentation();
            for (String format : List.of("html", "xml", "json")) {
                String template = format.equals("json") ? "{\"id\":\"" + ID + "\"}" : "<quest id=\"" + ID + "\"/>";
                require(QuestRepository.parseDefinition(Path.of("variables." + format),
                        QuestPresentationDocuments.write(template, taskPresentation)).presentation().equals(taskPresentation),
                        "Variable source round-trip failed: " + format);
            }
            require(!QuestVariableStore.save(player, ID, "", ID + "_copy.forbidden", "1", false).successful(),
                    "Variable in another task scope was accepted");
            reject(() -> {
                if (QuestVariableStore.save(player, ID, "", GLOBAL, "0", false).successful())
                    throw new AssertionError("Duplicate variable accepted");
                throw new IllegalArgumentException();
            }, "Duplicate variable accepted");
            Path root = QuestConfig.root().resolve("structures");
            Files.createDirectories(root);
            CompoundTag data = new CompoundTag();
            ListTag size = new ListTag(); for (int n : new int[]{3, 3, 3}) size.add(IntTag.valueOf(n)); data.put("size", size);
            ListTag palette = new ListTag();
            for (String name : List.of("minecraft:gold_block", "minecraft:stone")) {
                CompoundTag state = new CompoundTag(); state.putString("Name", name); palette.add(state);
            }
            data.put("palette", palette);
            ListTag blocks = new ListTag();
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
                if (y > 0 && x != 1) continue;
                CompoundTag block = new CompoundTag(); ListTag pos = new ListTag();
                pos.add(IntTag.valueOf(x)); pos.add(IntTag.valueOf(y)); pos.add(IntTag.valueOf(z));
                block.put("pos", pos); block.putInt("state", y == 1 ? 1 : 0); blocks.add(block);
            }
            data.put("blocks", blocks);
            data.put("entities", new ListTag());
            NbtIo.writeCompressed(data, root.resolve("test.nbt"));
            size.set(0, IntTag.valueOf(33));
            NbtIo.writeCompressed(data, root.resolve("oversize.nbt"));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void runtime(ServerPlayer p) {
        var quest = QuestRepository.find(ID);
        var data = QuestControlData.get(p);
        p.removeTag("xero_graph_command");
        QuestEventGraphs.click(p, ID, "command_action");
        require(p.getTags().contains("xero_graph_command"),
                "Command node did not resolve @s to the current player");
        NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(p));
        var visual = node("visual", "js", 0, 0, Map.of("mode", "visual", "field", "eventType",
                "operator", "eq", "expected", "jump", "logic", "and", "field2", "dimension",
                "operator2", "eq", "expected2", "minecraft:overworld"));
        require(xtdpotato.xero_quest_journal.core.runtime.QuestVisualPredicate.evaluate(p, visual, "jump", ""),
                "Visual AND predicate failed");
        require(!xtdpotato.xero_quest_journal.core.runtime.QuestVisualPredicate.evaluate(p, visual, "death", ""),
                "Visual predicate matched the wrong event");
        require(data.properties(p, quest, "button").get("value").equals("Jumped"), "Jump/JS/property chain failed");
        require(QuestRuntime.snapshot(p).get(ID + "_done").completed(), "Complete action failed");
        require(QuestRuntime.snapshot(p).get(ID + "_locked").unlocked(), "Unlock action failed");
        QuestEventGraphs.click(p, ID, "task_flags");
        require(QuestRuntime.snapshot(p).get(ID + "_locked").hidden(), "Linked task property action did not execute");
        require(QuestRuntime.setTaskProperty(p, ID + "_locked", "hidden", false)
                && !QuestRuntime.snapshot(p).get(ID + "_locked").hidden(), "Task property override could not be cleared");
        data.set(p, quest, "button", "value", "Once retained");
        QuestRepository.reload();
        NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(p));
        require(data.properties(p, quest, "button").get("value").equals("Once retained"), "One-shot retriggered");
        data.set(p, quest, "button", "value", "Jumped");
        QuestEventGraphs.click(p, ID, "button");
        require(data.properties(p, quest, "button").get("alpha").equals("0.5"), "Click action failed");
        data.set(p, quest, "button", "alpha", "1");
        QuestEventGraphs.click(p, ID, "button");
        require(data.properties(p, quest, "button").get("alpha").equals("1"), "Cooldown ignored");
        data.set(p, quest, "button", "visibility", "gone");
        require(!QuestEventGraphs.visible(p, quest, quest.presentation().controls().getFirst()), "Hidden control visible on server");
        data.set(p, quest, "button", "visibility", "visible");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.NETHER, Level.OVERWORLD));
        require(data.properties(p, quest, "button").get("textSize").equals("2"), "Dimension event failed");
        require(data.variable(p, GLOBAL).equals("0"), "Global initial value");
        var forbidden = node("forbidden", "js", 0, 0, Map.of("mode", "visual", "logic", "not",
                "field", "var:" + TASK_VARIABLE, "expected", "5"));
        require(!QuestVisualPredicate.evaluate(p, forbidden, "click", "", ID + "_copy"),
                "NOT predicate bypassed task variable isolation");
        QuestEventGraphs.click(p, ID, "var_set");
        require(data.variable(p, GLOBAL).equals("5"), "Variable assignment node failed");
        QuestEventGraphs.click(p, ID, "var_check");
        require(data.properties(p, quest, "button").get("value").equals("Variable matched"),
                "Visual variable comparison failed");
        require(QuestVariableStore.save(p, ID, GLOBAL, ID + ".local", "6", true).successful(),
                "Current-task variable fork failed");
        require(QuestRepository.find(ID).presentation().controls().stream()
                .filter(c -> c.id().equals("var_set")).findFirst().orElseThrow().graph().nodes().stream()
                .anyMatch(n -> n.get("variable", "").equals(ID + ".local")),
                "Current task variable references were not changed");
        require(QuestRepository.find(ID + "_copy").presentation().controls().stream()
                .filter(c -> c.id().equals("var_set")).findFirst().orElseThrow().graph().nodes().stream()
                .anyMatch(n -> n.get("variable", "").equals(GLOBAL)),
                "Current-task edit changed another task");
        require(data.variable(p, ID + ".local").equals("6"), "Forked task variable initial value");
        require(QuestRepository.find(ID).presentation().variables().stream()
                .anyMatch(variable -> variable.id().equals(ID + ".local")),
                "Current-task fork was not saved in task source");
        require(QuestVariableStore.save(p, ID, GLOBAL, RENAMED, "9", false).successful(),
                "Global rename failed");
        require(data.variable(p, RENAMED).equals("5"), "Global rename lost live player state");
        require(QuestRepository.find(ID + "_copy").presentation().controls().stream()
                .filter(c -> c.id().equals("var_set")).findFirst().orElseThrow().graph().nodes().stream()
                .anyMatch(n -> n.get("variable", "").equals(RENAMED)),
                "Replace everywhere did not update other task source");
        require(QuestRepository.find(ID).presentation().variables().stream()
                .anyMatch(variable -> variable.id().equals(TASK_VARIABLE)),
                "Global rename discarded existing task variables");
        require(QuestRuntime.setTaskProperty(p, ID, "hidden", true), "Task reset fixture property failed");
        data.set(p, quest, "button", "value", "Runtime override");
        data.set(p, quest, "button", "alpha", "0.25");
        data.setVariable(p, ID + ".local", "Runtime task value");
        data.setVariable(p, RENAMED, "Runtime global value");
        require(QuestRuntime.resetTrusted(p, ID).equals("reset"), "Task reset did not report a reset");
        var resetQuest = QuestRepository.find(ID);
        require(!QuestRuntime.snapshot(p).get(ID).hidden(), "Task reset retained a task property override");
        require(data.properties(p, resetQuest, "button").isEmpty(), "Task reset retained control overrides");
        require(data.variable(p, ID + ".local").equals("6"), "Task reset retained task variable value");
        require(data.variable(p, RENAMED).equals("Runtime global value"), "Task reset removed global variable value");
        require(QuestRuntime.snapshot(p).get(ID + "_done").completed(), "Task reset changed another task");
        QuestEventGraphs.fire(p, "jump", "");
        require(data.properties(p, resetQuest, "button").get("value").equals("Jumped"),
                "Task reset did not clear one-shot node state");
        data.set(p, quest, "button", "value", "Jumped");
        data.set(p, quest, "button", "alpha", "0.25");
        data.set(p, quest, "button", "textSize", "2");
        QuestEventGraphs.click(p, ID, "restore");
        require(!data.properties(p, quest, "button").containsKey("alpha")
                        && data.properties(p, quest, "button").get("textSize").equals("2"),
                "Restore property removed wrong overrides");
        var beforeEntry = QuestProgressData.get(p.server).entry(p.getUUID(), quest);
        int beforeRewards = p.getInventory().countItem(Items.EMERALD);
        QuestEventGraphs.click(p, ID, "custom_reward");
        require(p.getInventory().countItem(Items.EMERALD) == beforeRewards + 3, "Custom reward did not reach inventory");
        QuestRewards.custom(p, quest, "custom_reward/reward_node",
                List.of(new QuestDefinition.Reward("item", "minecraft:emerald", 3)), true);
        require(p.getInventory().countItem(Items.EMERALD) == beforeRewards + 3, "Custom reward repeated despite once setting");
        require(Objects.equals(beforeEntry, QuestProgressData.get(p.server).entry(p.getUUID(), quest)),
                "Custom reward changed task completion or claim state");
        var contextCheck = node("context_check", "js", 0, 0, Map.of("mode", "visual", "field", "dimension",
                "expectedField", "dimension", "logic", "and", "field2", "controlId", "expected2", "button"));
        require(QuestVisualPredicate.evaluate(p, contextCheck, "click", "", ID, "button"), "Context values comparison failed");
        var level = p.serverLevel();
        boolean raining = level.getLevelData().isRaining(), thundering = level.getLevelData().isThundering();
        level.setWeatherParameters(0, 6000, false, false);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true, level));
        level.setWeatherParameters(0, 6000, true, false);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true, level));
        require(data.properties(p, quest, "weather").getOrDefault("value", "").equals("Raining"), "World weather event not fired");
        level.setWeatherParameters(0, 6000, raining, thundering);
        if (QuestKubeJs.available()) {
            QuestEventGraphs.click(p, ID, "kube_call");
            QuestEventGraphs.click(p, ID, "kube_script");
            String assertion = "if (global.xeroQuestSmoke.questId !== event.questId || "
                    + "global.xeroQuestSmoke.calls !== 1 || global.xeroQuestSmoke.scripts !== 1) "
                    + "throw new Error('KubeJS graph actions or once guard failed');";
            QuestKubeJs.run(p, assertion, Map.of("questId", ID));
            QuestRepository.reload(); // Clear event cooldowns: action-level once must still prevent repetition.
            QuestEventGraphs.click(p, ID, "kube_call");
            QuestEventGraphs.click(p, ID, "kube_script");
            QuestKubeJs.run(p, assertion, Map.of("questId", ID));
            reject(() -> QuestKubeJs.call(p, "global.xeroMissingFunction", Map.of()), "Missing KubeJS function accepted");
            reject(() -> QuestKubeJs.run(p, "throw new Error('expected smoke failure')", Map.of()),
                    "KubeJS script error silently ignored");
            reject(() -> QuestKubeJs.call(p, "global.xeroQuestSmokeCall; throw 1", Map.of()),
                    "KubeJS function injection accepted");
        } else reject(() -> QuestKubeJs.run(p, "throw new Error('must not run')", Map.of()), "KubeJS fallback silently ran");
        xtdpotato.xero_quest_journal.core.network.QuestControlNetwork.send(p);
    }

    private static void mailTaskActions(ServerPlayer player) {
        var quest = QuestRepository.find(ID);
        var mailLock = node("mail_lock_task", "mail_lock_task", 0, 0,
                Map.of("target", ID + "_locked", "player", ""));
        var mailUnlock = node("mail_unlock_task", "mail_unlock_task", 0, 0,
                Map.of("target", ID + "_locked", "player", ""));
        require(QuestMailActions.execute(player, quest, "button", mailLock)
                        && !QuestRuntime.snapshot(player).get(ID + "_locked").unlocked(),
                "Mail task action did not lock its selected task");
        require(QuestMailActions.execute(player, quest, "button", mailUnlock)
                        && QuestRuntime.snapshot(player).get(ID + "_locked").unlocked(),
                "Mail task action did not unlock its selected task");
    }
    private static TriggerGraph.Node node(String id, String type, int x, int y, Map<String, String> data) {
        return new TriggerGraph.Node(id, type, x, y, data);
    }
    private static ServerPlayer player(Minecraft game) { return game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID()); }
    private static LayoutEditorState layout() throws Exception { return (LayoutEditorState) field(journal, "layout"); }
    private static LayoutEditorState.Element control(String id) throws Exception {
        return layout().elements().values().stream().filter(e -> e.quest().equals(ID) && e.attribute("journal_source_id", "").equals(id)).findFirst().orElseThrow();
    }
    private static boolean busy() throws Exception { return (boolean) call(field(journal, "presentationWriter"), "busy", new Class<?>[0]); }
    private static Object field(Object object, String key) throws Exception {
        var field = object.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String key, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(key); field.setAccessible(true); field.set(object, value);
    }
    private static Object call(Object object, String key, Class<?>[] types, Object... args) throws Exception {
        var method = object.getClass().getDeclaredMethod(key, types); method.setAccessible(true); return method.invoke(object, args);
    }
    private static StyledButton button(net.minecraft.client.gui.screens.Screen screen, String id) {
        return screen.children().stream().filter(e -> e instanceof StyledButton b && b.styleId().equals(id))
                .map(e -> (StyledButton) e).findFirst().orElseThrow(() -> new AssertionError("Missing " + id));
    }
    private static void assertWidgets(net.minecraft.client.gui.screens.Screen screen) throws Exception {
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
            if (screen instanceof QuestTriggerGraphScreen
                    && ((List<?>) field(screen, "inspectorWidgets")).contains(w)) {
                require(call(screen, "widgetViewport", new Class<?>[]{net.minecraft.client.gui.components.AbstractWidget.class}, w) != null,
                        "Scrollable inspector widget has no clip bounds");
                continue;
            }
            require(w.getX() >= 0 && w.getY() >= 0 && w.getRight() <= screen.width && w.getBottom() <= screen.height,
                    "Widget out of bounds: " + w.getMessage().getString());
        }
    }
    private static long capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
            long colored = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getPixelRGBA(x, y);
                int r = color & 255, g = (color >>> 8) & 255, b = (color >>> 16) & 255;
                if (r > 130 && g > 105 && b < 90) colored++;
            }
            return colored;
        }
    }
    private interface Checked { void run() throws Exception; }
    private static void reject(Checked action, String message) {
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError(message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
