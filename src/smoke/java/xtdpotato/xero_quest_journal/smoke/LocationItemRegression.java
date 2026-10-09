package xtdpotato.xero_quest_journal.smoke;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class LocationItemRegression {
    private static final String ID = "location_item_" + System.currentTimeMillis();
    private static int stage, wait;
    private static CompletableFuture<Void> pending;
    private static QuestScreen journal;
    private static QuestLocationScreen location;
    private static String saved, picked, variant;
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
                pending = CompletableFuture.runAsync(() -> runtime(player(game)), game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone()) return false;
                journal = new QuestScreen();
                location = new QuestLocationScreen(journal, "", value -> saved = value);
                game.setScreen(location);
                require(QuestSuggestions.options("dimension", "").stream().map(QuestValuePickerScreen.Option::value)
                        .collect(java.util.stream.Collectors.toSet()).containsAll(game.getSingleplayerServer().levelKeys()
                                .stream().map(key -> key.location().toString()).toList()), "Missing registered dimensions");
                require(QuestSuggestions.options("kill", "").stream()
                        .anyMatch(option -> option.value().equals("minecraft:zombie")), "Missing entity suggestions");
                require(QuestSuggestions.options("effect", "").stream()
                        .anyMatch(option -> option.value().equals("minecraft:speed")), "Missing effect suggestions");
            }
            case 2 -> {
                capture(game, "location-coordinates");
                var dimension = boxes(location).stream().filter(box -> box.getY() == 44).findFirst().orElseThrow();
                require(dimension instanceof SuggestedEditBox, "Dimension has no menu");
                location.mouseClicked(dimension.getRight() - 5, dimension.getY() + 8, 0);
                require(game.screen instanceof QuestValuePickerScreen, "Right-side dimension menu did not open");
            }
            case 3 -> {
                capture(game, "location-dimension-menu");
                boxes(game.screen).getFirst().setValue("minecraft:the_nether");
                game.screen.mouseClicked(game.screen.width / 2.0, 85, 0);
                game.screen.mouseReleased(game.screen.width / 2.0, 85, 0);
            }
            case 4 -> {
                if (game.screen != location) return false;
                require(field(location, "dimension").equals("minecraft:the_nether"), "Menu selection lost after return");
                set(location, "dimension", "minecraft:overworld");
                set(location, "mode", "biome");
                set(location, "target", "minecraft:plains");
                game.setScreen(location);
                @SuppressWarnings("unchecked") var options = (List<QuestValuePickerScreen.Option>) call(location, "targetOptions");
                require(options.stream().anyMatch(option -> option.value().equals("minecraft:plains")),
                        "Biome registry suggestions missing");
                require(location.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .noneMatch(button -> button.styleId().equals("location-locate")), "Place still requires locate");
                require(boxes(location).size() == 2, "Place still asks for coordinates or radius");
                @SuppressWarnings("unchecked") var dimensions = (List<QuestValuePickerScreen.Option>) call(location, "dimensionOptions");
                require(dimensions.stream().anyMatch(option -> option.value().isEmpty()), "No unrestricted dimension option");
                set(location, "dimension", "");
                game.setScreen(location);
            }
            case 5 -> {
                capture(game, "location-biome-portable");
                button(location, "location-apply").onPress();
            }
            case 6 -> {
                require("biome||minecraft:plains".equals(saved), "Optional dimension was replaced or place saved world coordinates");
                location = new QuestLocationScreen(journal, saved, value -> saved = value);
                game.setScreen(location);
                require(field(location, "mode").equals("biome") && field(location, "target").equals("minecraft:plains")
                                && field(location, "dimension").equals(""),
                        "Place lost on reopen");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 480);
                game.resizeDisplay();
                widgets(location);
            }
            case 7 -> {
                capture(game, "location-small");
                set(location, "mode", "structure");
                game.setScreen(location);
                @SuppressWarnings("unchecked") var options = (List<QuestValuePickerScreen.Option>) call(location, "targetOptions");
                require(options.stream().anyMatch(option -> option.value().equals("#minecraft:village")),
                        "Structure tags missing");
                var choices = QuestIcons.itemChoices();
                require(choices.stream().anyMatch(choice -> QuestItemStackValue.encoded(choice.value())
                        && QuestItemStackValue.itemId(choice.value()).equals("minecraft:potion")),
                        "Creative-tab component variants missing");
                require(QuestIcons.choice(variant).matches("variant_a"), "Variant data not searchable");
                game.setScreen(QuestScreen.pickResource(journal, variant, true, true, value -> picked = value));
                button(game.screen, "icon-source-inventory").onPress();
                boxes(game.screen).getFirst().setValue("variant_a");
            }
            case 8 -> {
                capture(game, "item-variants-small");
                button(game.screen, "icon-entry").onPress();
                require(picked != null && QuestItemStackValue.matches(stack("variant_a"), picked, game.player.registryAccess()),
                        "Inventory picker lost component data");
                game.setScreen(QuestScreen.pickResource(journal, "#minecraft:logs", true, true, value -> picked = value));
                boxes(game.screen).getFirst().setValue("#minecraft:logs");
            }
            case 9 -> {
                capture(game, "item-tag-picker");
                button(game.screen, "icon-entry").onPress();
                require(picked.startsWith("#minecraft:logs"), "Item tag picker returned a concrete item");
                var picker = QuestScreen.pickResource(journal, variant, true, value -> picked = value);
                game.setScreen(picker);
                require(picker.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .noneMatch(button -> button.styleId().equals("icon-source-tags")), "Rewards incorrectly allow item tags");
                require(boxes(picker).stream().anyMatch(box -> box.getValue().equals(variant)),
                        "Long item identity truncated in picker");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                var conditions = new QuestConditionsScreen(journal, ID,
                        List.of(new QuestDefinition.Trigger("dimension", "minecraft:overworld", 1),
                                new QuestDefinition.Trigger("item_hold", variant, 2)), values -> {});
                game.setScreen(conditions);
                widgets(conditions);
            }
            case 10 -> {
                capture(game, "location-conditions-wide");
                game.setScreen(new QuestRewardsScreen(journal, ID));
                widgets(game.screen);
            }
            case 11 -> {
                capture(game, "component-rewards-wide");
                require(ClientQuestProgress.view(ID) != null && ClientQuestProgress.view(ID).rewards().getFirst().item().equals(variant),
                        "Long component reward did not synchronize to client");
                if (ClientQuestProgress.view(ID + "_confirm") == null) return false;
                journal = QuestScreen.task(null, ID + "_confirm");
                game.setScreen(journal);
                var layout = (LayoutEditorState) field(journal, "layout");
                layout.setPosition("system:complete", 5, 5);
                game.setScreen(journal);
                footer(journal);
                require((double) call(journal, "maxDetailScroll") > 0, "Long content cannot scroll");
            }
            case 12 -> {
                capture(game, "completion-footer-wide");
                set(journal, "editMode", true);
                game.setScreen(journal);
                require(journal.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .noneMatch(button -> button.styleId().equals("complete")), "Completion action remains in editor canvas");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 480);
                game.resizeDisplay();
                journal = QuestScreen.task(null, ID + "_submit");
                game.setScreen(journal);
                footer(journal);
            }
            case 13 -> {
                capture(game, "submission-footer-small");
                game.setScreen(null);
                return true;
            }
            default -> throw new AssertionError("Unknown stage");
        }
        stage++;
        return false;
    }

    private static void runtime(ServerPlayer player) {
        try {
            var registries = player.registryAccess();
            String dimension = player.level().dimension().location().toString();
            var here = new QuestLocation("coords", dimension, "", player.getX(), player.getY(), player.getZ(), 8, true);
            require(QuestLocation.parse(here.encode()).equals(here), "Coordinates do not round-trip");
            require(here.reached(player), "Current coordinates not reached");
            require(!new QuestLocation("coords", "minecraft:the_nether", "", here.x(), here.y(), here.z(), 8, true)
                    .reached(player), "Wrong dimension completed");
            require(!new QuestLocation("coords", dimension, "", here.x() + 9, here.y(), here.z(), 8, true)
                    .reached(player), "Outside radius completed");
            require(new QuestLocation("coords", dimension, "", here.x() + 8, here.y(), here.z(), 8, true)
                    .reached(player), "Radius boundary excluded");
            require(QuestLocation.parse("coords|minecraft:overworld|NaN|2|3|8") == null
                    && QuestLocation.parse("coords|minecraft:overworld|30000001|2|3|8") == null
                    && QuestLocation.parse("coords|minecraft:overworld|1|2|3|0") == null, "Invalid location accepted");
            var biome = QuestLocation.parse("biome|" + dimension + "|minecraft:plains");
            require(biome != null && !biome.resolved() && biome.reached(player), "Portable biome target failed");
            require(QuestLocation.parse(biome.encode()).equals(biome), "Biome does not round-trip");
            require(QuestLocation.parse("biome|" + dimension + "|minecraft:plains|8").equals(biome),
                    "Legacy biome target not normalized");
            require(QuestLocation.parse("biome|" + dimension + "|minecraft:plains|20000|80|-10000|8").reached(player),
                    "Old author-world coordinates still affect biome membership");
            require(QuestLocation.parse("biome|" + dimension + "|#minecraft:is_overworld").reached(player),
                    "Biome tag failed");
            require(!QuestLocation.parse("biome|" + dimension + "|minecraft:desert").reached(player),
                    "Wrong biome completed");
            var anyBiome = QuestLocation.parse("biome||minecraft:plains");
            require(anyBiome != null && anyBiome.reached(player) && anyBiome.encode().equals("biome||minecraft:plains"),
                    "Unrestricted biome failed");
            require(QuestLocation.parse("coords||0|64|0|8") == null, "Coordinate target allowed empty dimension");
            structureMembership(player);

            var a = stack("variant_a");
            var b = stack("variant_b");
            variant = QuestItemStackValue.encode(a, registries);
            require(variant.length() > 256, "Fixture does not cover long component values");
            require(QuestItemStackValue.matches(a, variant, registries)
                    && !QuestItemStackValue.matches(b, variant, registries), "Same-ID variants collapsed");
            require(QuestConditions.matches(a, "minecraft:stick", registries), "Broad item ID stopped matching");
            require(QuestConditions.matches(new ItemStack(Items.OAK_LOG), "#minecraft:logs", registries)
                    && !QuestConditions.matches(a, "#minecraft:logs", registries), "Item tag matching failed");
            var huge = stack("x".repeat(5000));
            require(QuestItemStackValue.encode(huge, registries).isEmpty(), "Un-decodable stack was encoded");
            require(QuestItemStackValue.decode("xqj-stack:broken", registries).isEmpty(), "Malformed stack decoded");

            player.getInventory().clearContent();
            player.getInventory().setItem(0, a.copyWithCount(2));
            player.getInventory().setItem(1, b.copyWithCount(9));
            var hold = quest("hold", List.of(new QuestDefinition.Trigger("item_hold", variant, 3)));
            require(QuestConditions.progress(player, hold).current() == 2, "Hold counted wrong variant");
            var submit = quest("submit", List.of(new QuestDefinition.Trigger("item_submit", variant, 2)));
            var plan = QuestConditions.plan(player, submit, true);
            require(plan.satisfied() && plan.inventory().getFirst().isEmpty() && plan.inventory().get(1).getCount() == 9,
                    "Submission consumed another variant");
            require(player.getInventory().getItem(0).getCount() == 2, "Submission plan mutated inventory");
            var request = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, ID, "Variant reward", "",
                    "", "", "guide", "", 1, variant, "", variant, 2, "manual", "", null, null,
                    List.of(new QuestDefinition.Trigger("item_acquire", variant, 2),
                            new QuestDefinition.Trigger("confirm", "", 1)), List.of());
            var progressData = QuestProgressData.get(player.getServer());
            progressData.observeTaskCatalog(player.getUUID(),
                    QuestRepository.all().stream().map(QuestDefinition::id).toList());
            require(QuestAuthoring.create(player, request).successful(), "Compact authoring rejected components");
            String woodId = ID + "_logs";
            var woodRequest = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, woodId, "Wood collector", "",
                    "", "", "guide", "", 1, "", "", "", 1, "manual", "", null, null,
                    List.of(new QuestDefinition.Trigger("item_acquire", "#minecraft:logs", 3)), List.of());
            require(QuestAuthoring.create(player, woodRequest).successful(), "Tag acquisition task creation failed");
            require(QuestRuntime.trigger(player, woodId, "unlock", false).equals("unlocked"),
                    "Tag acquisition task could not unlock");
            require(QuestRuntime.snapshot(player).get(woodId).newTask(), "New task did not become unread");
            QuestConditions.recordItemAcquired(player, new ItemStack(Items.OAK_LOG), 2);
            QuestConditions.recordItemAcquired(player, new ItemStack(Items.BIRCH_LOG), 1);
            require(QuestConditions.progress(player, QuestRepository.find(woodId)).current() == 3,
                    "Acquisition tag did not aggregate different wood types");
            require(QuestRuntime.markTaskRead(player, woodId).equals("read")
                            && !QuestRuntime.snapshot(player).get(woodId).newTask(),
                    "Opening a new task did not clear its unread badge state");
            for (String kind : List.of("confirm", "submit")) {
                var footerRequest = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, ID + "_" + kind,
                        "Footer " + kind, "Scrollable task content. ".repeat(120), "", "", "guide", "", 1,
                        "", "", "", 1, "manual", "", null, null,
                        List.of(new QuestDefinition.Trigger(kind.equals("confirm") ? "confirm" : "item_submit",
                                kind.equals("confirm") ? "" : "minecraft:diamond", 1)), List.of());
                require(QuestAuthoring.create(player, footerRequest).successful(), "Footer fixture creation failed");
            }
            require(QuestAuthoring.edit(player, request, hash(ID), false, true).successful(), "Component icon/reward edit failed");
            var definition = QuestRepository.find(ID);
            QuestConditions.recordItemAcquired(player, b, 10);
            require(QuestConditions.progress(player, definition).current() == 0, "Wrong acquired variant counted");
            String alternate = "xqj-stack:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("  " + a.copyWithCount(1).save(registries)).getBytes(StandardCharsets.UTF_8));
            require(!alternate.equals(variant), "Semantic equivalence fixture identical");
            QuestConditions.record(player, "item_acquire", alternate, 1);
            QuestConditions.recordItemAcquired(player, a, 1);
            require(QuestConditions.progress(player, definition).current() == 2, "Equivalent component payload not counted");
            require(QuestRuntime.activate(player, ID).equals("completed"), "Variant acquire task not complete");
            require(QuestRuntime.claim(player, ID).equals("claimed"), "Component reward not claimed");
            require(player.getInventory().getItem(0).getCount() == 4
                    && QuestItemStackValue.matches(player.getInventory().getItem(0), variant, registries)
                    && player.getInventory().getItem(1).getCount() == 9, "Reward lost data or merged wrong variant");
            var snapshot = new QuestNetwork.ProgressSnapshotPayload(QuestRuntime.snapshot(player), List.of());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
                var decoded = QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.decode(buffer);
                require(decoded.views().get(ID).rewards().getFirst().item().equals(variant),
                        "Network truncated component reward");
            } finally { buffer.release(); }
            var encoded = QuestDocuments.conditions(QuestRepository.source(ID),
                    List.of(new QuestDefinition.Trigger("location", biome.encode(), 1)));
            var parsed = QuestRepository.parseDefinition(QuestRepository.fileOf(ID), encoded);
            require(parsed.triggers().getFirst().item().equals(biome.encode()), "Location lost in task source");
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static void structureMembership(ServerPlayer player) {
        var level = player.serverLevel();
        var original = player.position();
        var chunk = level.getChunkAt(player.blockPosition());
        var type = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(net.minecraft.resources.ResourceLocation.parse("minecraft:village_plains"));
        var piece = new net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece(
                net.minecraft.util.RandomSource.create(42), chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
        piece.move(0, player.blockPosition().getY() - piece.getBoundingBox().minY(), 0);
        var start = new net.minecraft.world.level.levelgen.structure.StructureStart(type, chunk.getPos(), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece)));
        chunk.setStartForStructure(type, start);
        var target = QuestLocation.parse("structure|minecraft:overworld|#minecraft:village");
        int loaded = level.getChunkSource().getLoadedChunksCount();
        try {
            var box = piece.getBoundingBox();
            player.setPos(box.minX() + 1, box.minY() + 1, box.minZ() + 1);
            var current = level.getChunkAt(player.blockPosition());
            current.addReferenceForStructure(type, chunk.getPos().toLong());
            require(target.reached(player), "Inside structure tag not reached");
            require(QuestLocation.parse("structure||#minecraft:village").reached(player),
                    "Unrestricted structure failed");
            require(QuestLocation.parse("structure|minecraft:overworld|minecraft:village_plains").reached(player),
                    "Inside exact structure not reached");
            require(QuestLocation.parse("structure|minecraft:overworld|#minecraft:village|20000|80|20000|8")
                    .reached(player), "Legacy coordinates still bind structure to author world");
            require(!QuestLocation.parse("structure|minecraft:the_nether|#minecraft:village").reached(player),
                    "Structure ignored dimension");
            require(!QuestLocation.parse("structure|minecraft:overworld|minecraft:desert_pyramid").reached(player),
                    "Wrong structure completed");
            player.setPos(player.getX(), box.maxY() + 20, player.getZ());
            require(!target.reached(player), "Outside structure bounds completed");
            require(level.getChunkSource().getLoadedChunksCount() == loaded, "Membership generated chunks");
            current.getReferencesForStructure(type).remove(chunk.getPos().toLong());
        } finally {
            chunk.setStartForStructure(type, net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START);
            player.setPos(original.x, original.y, original.z);
        }
    }

    private static ItemStack stack(String id) {
        var stack = new ItemStack(Items.STICK);
        var data = new CompoundTag();
        data.putString("variant", id);
        data.putString("description", "component round-trip fixture ".repeat(14));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
    private static QuestDefinition quest(String id, List<QuestDefinition.Trigger> conditions) {
        return new QuestDefinition(id, id, "", "commission", "", true, false, 0,
                null, "", "", "", "", List.of(), conditions, List.of(), "manual");
    }
    private static String hash(String id) throws Exception { return QuestRepository.fingerprint(QuestRepository.source(id)); }
    private static ServerPlayer player(Minecraft game) { return game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID()); }
    private static List<EditBox> boxes(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }
    private static void footer(QuestScreen screen) throws Exception {
        var action = button(screen, "complete");
        int bottom = (int) call(screen, "canvasBottom");
        var track = screen.children().stream().filter(child -> child instanceof StyledButton button
                && button.styleId().equals("track")).map(StyledButton.class::cast).findFirst().orElse(null);
        // Wide footers keep "track" rightmost with submit/confirm beside it; narrow ones show the action alone.
        int expectedX = track == null ? screen.width - 136 : screen.width - 264;
        require(action.getY() == screen.height - 34 && action.getX() == expectedX
                        && action.getY() >= bottom + 4 && action.getBottom() <= screen.height - 10,
                "Task action overlaps content or navigation");
        require(track == null || track.getX() == screen.width - 136 && track.getY() == action.getY()
                        && action.getRight() <= track.getX() - 8,
                "Track and completion buttons overlap in the footer");
        require((int) call(screen, "detailContentBottom") == bottom, "Content clipping disagrees with canvas bounds");
        var layout = (LayoutEditorState) field(screen, "layout");
        require(!layout.positions().containsKey("system:complete"), "Old draggable action position survived");
        var method = QuestScreen.class.getDeclaredMethod("inDetails", double.class, double.class);
        method.setAccessible(true);
        require(!(boolean) method.invoke(screen, action.getX() + 2.0, action.getY() + 2.0),
                "Completion footer participates in canvas hit testing");
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget other) || other == action || !other.visible) continue;
            require(action.getRight() <= other.getX() || other.getRight() <= action.getX()
                    || action.getBottom() <= other.getY() || other.getBottom() <= action.getY(),
                    "Task action overlaps another widget");
        }
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.visible && button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object call(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
    private static void widgets(Screen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
        for (var widget : widgets) {
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width
                    && widget.getBottom() <= screen.height, "Widget outside screen");
            for (var other : widgets) {
                if (widget == other) continue;
                require(widget.getRight() <= other.getX() || other.getRight() <= widget.getX()
                        || widget.getBottom() <= other.getY() || other.getBottom() <= widget.getY(), "Widgets overlap");
            }
        }
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(OUTPUT.resolve(name + ".png")); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (location/item stage " + stage + ")");
    }
}
