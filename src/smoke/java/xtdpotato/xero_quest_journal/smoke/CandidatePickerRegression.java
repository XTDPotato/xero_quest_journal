package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import xtdpotato.xero_quest_journal.client.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Exercises candidate source selection and return paths without the unrelated creation workflow. */
final class CandidatePickerRegression {
    private static int stage;
    private static int writes;
    private static QuestValuePickerScreen candidates;
    private static QuestLocationScreen location;
    private static QuestStructurePickerScreen templates;
    private static QuestStructurePickerScreen pool;
    private static QuestStructurePreviewScreen preview;
    private static int chosenPoint;
    private static boolean reducedMotion;
    private static long previewFrame;
    private static CompletableFuture<Void> language;

    static boolean tick(Minecraft game) throws Exception {
        if (game.screen instanceof AnimatedQuestScreen animated && animated.inputBlocked()) return false;
        switch (stage) {
            case 0 -> {
                if (language == null) {
                    game.getLanguageManager().setSelected("zh_cn");
                    game.options.languageCode = "zh_cn";
                    game.getWindow().setWindowed(1920, 1080);
                    game.getWindow().setWidth(1920);
                    game.getWindow().setHeight(1080);
                    game.options.guiScale().set(2);
                    game.resizeDisplay();
                    language = game.reloadResourcePacks();
                    return false;
                }
                if (!language.isDone()) return false;
                language.join();
                verifyCompletionMessage(game);
                verifyJigsawMetadata();
                reducedMotion = ClientPreferences.get().reducedMotion;
                ClientPreferences.get().reducedMotion = true;
                candidates = picker("item_acquire", List.of());
                game.setScreen(candidates);
                button(game, "candidate-add");
                require(game.screen instanceof QuestScreen, "Item source did not open the resource picker");
                manual(game).setValue("minecraft:diamond");
                button(game, "icon-apply");
                require(game.screen == candidates, "Item apply did not return to candidates");
                require(hasCandidate("minecraft:diamond"), "Selected item was not added");
                require(writes == 0, "Adding a candidate committed the original field");
                button(game, "candidate-add");
                button(game, "icon-cancel");
                require(game.screen == candidates && hasCandidate("minecraft:diamond") && writes == 0,
                        "Cancel changed the original field or lost the candidate");
            }
            case 1 -> {
                candidates = picker("block_break", List.of(
                        new QuestValuePickerScreen.Option("minecraft:stone", "Stone")));
                game.setScreen(candidates);
                button(game, "candidate-add");
                require(game.screen instanceof QuestValuePickerScreen && game.screen != candidates,
                        "Generic source did not open a list");
                selectRow(game, 0);
                require(game.screen == candidates && hasCandidate("minecraft:stone") && writes == 0,
                        "Generic source did not preserve candidate/field separation");
                selectRow(game, 0);
                require(writes == 1, "Choosing the candidate did not commit the field");
            }
            case 2 -> {
                candidates = picker("locate_structure", List.of());
                game.setScreen(candidates);
                ((EditBox) field(candidates, "newCandidate")).setValue("minecraft:village_plains");
                button(game, "candidate-add");
                require(game.screen instanceof QuestLocationScreen, "World structure did not open location selector");
                location = (QuestLocationScreen) game.screen;
                require(field(location, "mode").equals("structure") && (boolean) field(location, "targetOnly"),
                        "World structure source lost its registry-ID format");
                button(game, "location-preview");
                require(game.screen instanceof QuestStructurePickerScreen, "Structure preview browser did not open");
                templates = (QuestStructurePickerScreen) game.screen;
            }
            case 3 -> {
                if (field(templates, "pending") != null) return false;
                require(!((List<?>) field(templates, "visible")).isEmpty(),
                        "Village start-pool templates were not discovered");
                require(game.screen.children().stream().noneMatch(child -> child instanceof StyledButton button
                                && button.styleId().equals("structure-delete")),
                        "World preview browser exposed destructive library actions");
                button(game, "structure-preview-0");
            }
            case 4 -> {
                if (game.screen == templates) return false;
                require(game.screen instanceof QuestStructurePreviewScreen, "3D template preview did not open");
                if (previewFrame == 0) previewFrame = ClientSmokeTest.renderedFrames;
                if (ClientSmokeTest.renderedFrames - previewFrame < 4) return false;
                String reference = (String) field(game.screen, "reference");
                require(ClientStructures.model(reference) != null
                                && !ClientStructures.model(reference).getList("blocks", 10).isEmpty(),
                        "Structure preview model is empty");
                game.screen.mouseScrolled(game.screen.width / 2., game.screen.height / 2., 0, 1);
                require((float) field(game.screen, "zoom") > 1, "Structure preview cannot zoom");
                Path output = Path.of("..", "build", "verification");
                Files.createDirectories(output);
                try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("candidate-world-structure-preview.png"));
                }
                preview = (QuestStructurePreviewScreen) game.screen;
                button(game, "structure-jigsaws");
                var points = points();
                require(!points.isEmpty() && points.stream().allMatch(
                        xtdpotato.xero_quest_journal.core.model.QuestJigsawInfo::complete),
                        "Real template lost its jigsaw connection metadata");
                chosenPoint = java.util.stream.IntStream.range(0, points.size()).filter(index ->
                        !points.get(index).pool().isBlank() && !points.get(index).pool().equals("minecraft:empty"))
                        .findFirst().orElseThrow();
                button(game, "jigsaw-choose");
                selectRow(game, chosenPoint);
                require(game.screen == preview && (int) field(preview, "selected") == chosenPoint,
                        "Connection-point list did not select the requested point");
                previewFrame = ClientSmokeTest.renderedFrames;
            }
            case 5 -> {
                if (ClientSmokeTest.renderedFrames - previewFrame < 4) return false;
                screenshot(game, "jigsaw-inspector-zh-1920.png");
                var markers = StructurePreviewRenderer.jigsawMarkers((String) field(preview, "reference"), 16, 40,
                        method(preview, "previewWidth"), method(preview, "previewHeight"),
                        (float) field(preview, "angle"), (float) field(preview, "zoom"), 0, 0);
                var marker = markers.stream().filter(point -> !point.info().equals(points().get(chosenPoint)))
                        .filter(point -> point.x() > 24 && point.x() < methodUnchecked(preview, "previewWidth")
                                && point.y() > 48 && point.y() < methodUnchecked(preview, "previewHeight") + 32)
                        .filter(point -> markers.stream().noneMatch(other -> other != point
                                && Math.hypot(other.x() - point.x(), other.y() - point.y()) < 1))
                        .findFirst().orElseThrow();
                preview.mouseClicked(marker.x(), marker.y(), 0);
                preview.mouseReleased(marker.x(), marker.y(), 0);
                require(points().get((int) field(preview, "selected")).equals(marker.info()),
                        "Clicking the projected model marker did not select its jigsaw");
                chosenPoint = (int) field(preview, "selected");
                preview.mouseScrolled(method(preview, "panelX") + 10, method(preview, "contentTop") + 10, 0, -4);
                require((double) methodValue(field(preview, "reading"), "maximum") == 0
                                || (double) methodValue(field(preview, "reading"), "target") > 0,
                        "Connection properties cannot scroll");
                button(game, "jigsaw-guide");
                previewFrame = ClientSmokeTest.renderedFrames;
            }
            case 6 -> {
                if (ClientSmokeTest.renderedFrames - previewFrame < 4) return false;
                screenshot(game, "jigsaw-guide-zh-1920.png");
                // Any village fountain connector with a real pool can continue to its templates.
                if (points().get(chosenPoint).pool().equals("minecraft:empty")) {
                    chosenPoint = java.util.stream.IntStream.range(0, points().size())
                            .filter(index -> !points().get(index).pool().equals("minecraft:empty")).findFirst().orElseThrow();
                    button(game, "jigsaw-choose");
                    selectRow(game, chosenPoint);
                }
                button(game, "jigsaw-pool");
                require(game.screen instanceof QuestStructurePickerScreen
                                && (boolean) field(game.screen, "poolTarget"),
                        "Jigsaw pool did not open the reusable template browser");
                pool = (QuestStructurePickerScreen) game.screen;
            }
            case 7 -> {
                if (field(pool, "pending") != null) return false;
                require(!((List<?>) field(pool, "visible")).isEmpty(),
                        "Actual jigsaw pool has no discovered templates");
                button(game, "structure-preview-0");
            }
            case 8 -> {
                if (game.screen == pool) return false;
                require(game.screen instanceof QuestStructurePreviewScreen
                                && ClientStructures.model((String) field(game.screen, "reference")) != null,
                        "Pool template preview did not load");
                game.screen.onClose();
                require(game.screen == pool, "Nested preview did not return to pool");
                pool.onClose();
                require(game.screen == preview && (int) field(preview, "selected") == chosenPoint,
                        "Returning from a pool lost the selected connector");
                game.getWindow().setWindowed(960, 540);
                game.getWindow().setWidth(960);
                game.getWindow().setHeight(540);
                game.resizeDisplay();
                require(method(preview, "previewHeight") > 40
                                && method(preview, "contentBottom") > method(preview, "contentTop"),
                        "Narrow layout collapsed preview or scroll viewport");
                var footer = preview.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals("jigsaw-pool")).findFirst().orElseThrow();
                require(footer.getY() >= method(preview, "contentBottom") + 4,
                        "Narrow inspector content overlaps its footer");
                previewFrame = ClientSmokeTest.renderedFrames;
            }
            case 9 -> {
                if (ClientSmokeTest.renderedFrames - previewFrame < 4) return false;
                screenshot(game, "jigsaw-guide-small.png");
                preview.mouseScrolled(method(preview, "panelX") + 10, method(preview, "contentTop") + 10, 0, -4);
                require((double) methodValue(field(preview, "reading"), "maximum") > 0
                                && (double) methodValue(field(preview, "reading"), "target") > 0,
                        "Narrow guide does not smoothly scroll overflowing instructions");
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.resizeDisplay();
                preview.onClose();
                require(game.screen == templates, "Preview did not return to template browser");
                templates.onClose();
                require(game.screen == location && field(location, "target").equals("minecraft:village_plains"),
                        "Preview return lost the selected world structure");
                button(game, "location-apply");
                require(game.screen == candidates && hasCandidate("minecraft:village_plains") && writes == 1,
                        "World target was not saved as a raw ID or changed the source field");
            }
            case 10 -> {
                candidates = picker("structure_file", List.of());
                game.setScreen(candidates);
                button(game, "candidate-add");
                require(game.screen instanceof QuestStructurePickerScreen
                                && field(game.screen, "worldTarget") == null,
                        "NBT file source did not open the imported structure library");
                game.screen.onClose();
                require(game.screen == candidates && writes == 1, "Library cancel changed the source field");
                ClientPreferences.get().reducedMotion = reducedMotion;
                game.setScreen(null);
                return true;
            }
            default -> throw new AssertionError("Unexpected candidate test stage");
        }
        stage++;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<xtdpotato.xero_quest_journal.core.model.QuestJigsawInfo> points() {
        try { return (List<xtdpotato.xero_quest_journal.core.model.QuestJigsawInfo>) field(preview, "points"); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static void screenshot(Minecraft game, String name) throws Exception {
        Path output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name));
        }
    }

    private static Object methodValue(Object owner, String name) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
    }
    private static int method(Object owner, String name) throws Exception { return (int) methodValue(owner, name); }
    private static int methodUnchecked(Object owner, String name) {
        try { return method(owner, name); } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static void verifyJigsawMetadata() throws Exception {
        var source = net.minecraft.nbt.TagParser.parseTag("""
                {size:[3,2,1],palette:[
                  {Name:"minecraft:jigsaw",Properties:{orientation:"east_up"}},
                  {Name:"minecraft:jigsaw",Properties:{orientation:"west_up"}},
                  {Name:"minecraft:stone"}],
                 blocks:[
                  {pos:[0,0,0],state:0,nbt:{name:"demo:start",target:"demo:door",pool:"demo:rooms",
                    joint:"aligned",final_state:"minecraft:air",selection_priority:2,placement_priority:3,
                    Command:"secret",Items:[{id:"minecraft:diamond"}]}},
                  {pos:[2,0,0],state:1,nbt:{name:"demo:door",target:"minecraft:empty",pool:"minecraft:empty",
                    final_state:"minecraft:stone",Command:"secret"}},
                  {pos:[1,0,0],state:2,nbt:{Command:"secret"}}]}
                """);
        var clean = xtdpotato.xero_quest_journal.core.runtime.QuestStructures.preview(
                xtdpotato.xero_quest_journal.core.runtime.QuestStructures.validate(source));
        var blocks = clean.getList("blocks", 10);
        require(!blocks.getCompound(0).getCompound("nbt").contains("Command")
                        && !blocks.getCompound(0).getCompound("nbt").contains("Items")
                        && !blocks.getCompound(2).contains("nbt"),
                "Preview leaked arbitrary block-entity metadata");
        var points = xtdpotato.xero_quest_journal.core.model.QuestJigsawInfo.read(clean);
        require(points.size() == 2 && points.get(0).front() == net.minecraft.core.Direction.EAST
                        && points.get(0).top() == net.minecraft.core.Direction.UP
                        && points.get(0).target().equals(points.get(1).name())
                        && points.get(0).selectionPriority() == 2 && points.get(0).placementPriority() == 3
                        && points.get(0).finalState().equals("minecraft:air")
                        && points.get(1).joint().equals("aligned"),
                "Connection metadata, orientation, priorities or vanilla joint fallback are incorrect");
        var palette = clean.getList("palette", 10);
        var a = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                points.get(0).position(), net.minecraft.nbt.NbtUtils.readBlockState(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(0)),
                blocks.getCompound(0).getCompound("nbt"));
        var b = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                points.get(1).position(), net.minecraft.nbt.NbtUtils.readBlockState(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(1)),
                blocks.getCompound(1).getCompound("nbt"));
        require(net.minecraft.world.level.block.JigsawBlock.canAttach(a, b),
                "Documented opposite-face/name matching example does not match vanilla rules");
        var wrong = b.nbt().copy();
        wrong.putString("name", "demo:wrong");
        require(!net.minecraft.world.level.block.JigsawBlock.canAttach(a,
                        new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                                b.pos(), b.state(), wrong)), "Vanilla accepted the mismatched connector-name example");
    }

    private static QuestValuePickerScreen picker(String context, List<QuestValuePickerScreen.Option> options) {
        return new QuestValuePickerScreen(null, "Candidate source test", context, () -> options,
                "", ignored -> writes++);
    }

    private static void verifyCompletionMessage(Minecraft game) throws Exception {
        var quest = xtdpotato.xero_quest_journal.core.io.QuestRepository.parseDefinition(Path.of("chat-test.json"),
                """
                {"id":"completion_chat_test","title":"Defeat zombie","titleKey":"generate.xero_quest_journal.title.defeat",
                 "type":"world","triggers":[{"type":"kill","item":"minecraft:zombie","count":1}]}
                """);
        var message = xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.completionMessage(
                quest, game.player.registryAccess());
        String content = message.getString();
        require(!content.contains("%s") && content.contains(
                        net.minecraft.world.entity.EntityType.ZOMBIE.getDescription().getString()),
                "Completion message lost generated title arguments");
        var open = message.getSiblings().getLast();
        require(content.contains("\n" + open.getString()) && open.getStyle().getColor().getValue()
                        == net.minecraft.ChatFormatting.GOLD.getColor(),
                "Open-task link is not on a separate gold line");
        var click = open.getStyle().getClickEvent();
        require(click != null && click.getAction() == net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND
                        && click.getValue().equals("/xero_quest open task completion_chat_test"),
                "Completion message link targets the wrong task");
        var source = game.getSingleplayerServer().createCommandSourceStack().withPermission(0);
        require(game.getSingleplayerServer().getCommands().getDispatcher().parse(
                        click.getValue().substring(1), source).getExceptions().isEmpty(),
                "Task-open link is not available to an ordinary player");
    }

    @SuppressWarnings("unchecked")
    private static EditBox manual(Minecraft game) throws Exception {
        return (EditBox) ((Map<String, ?>) field(game.screen, "formFields")).get("icon_picker.manual");
    }

    private static boolean hasCandidate(String id) throws Exception {
        return ((List<?>) field(candidates, "filtered")).stream()
                .anyMatch(value -> ((QuestValuePickerScreen.Option) value).value().equals(id));
    }

    private static void selectRow(Minecraft game, int index) throws Exception {
        var screen = game.screen;
        var left = screen.getClass().getDeclaredMethod("left");
        var top = screen.getClass().getDeclaredMethod("listTop");
        left.setAccessible(true);
        top.setAccessible(true);
        double x = (int) left.invoke(screen) + 12, y = (int) top.invoke(screen) + index * 22 + 10;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void button(Minecraft game, String id) {
        var screen = game.screen;
        var button = screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(widget -> widget.styleId().equals(id)).findFirst().orElseThrow();
        require(button.active, "Disabled button: " + id);
        require(screen.mouseClicked(button.getX() + button.getWidth() / 2., button.getY() + 10, 0),
                "Unhandled click: " + id);
        screen.mouseReleased(button.getX() + button.getWidth() / 2., button.getY() + 10, 0);
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Candidate picker stage " + stage + ": " + message);
    }
}
