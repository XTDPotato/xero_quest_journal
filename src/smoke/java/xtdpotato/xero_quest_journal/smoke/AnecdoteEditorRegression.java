package xtdpotato.xero_quest_journal.smoke;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.AnecdoteStore;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.AnecdoteNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

final class AnecdoteEditorRegression {
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final String ID = "editor_anecdote_" + System.currentTimeMillis();
    private static int stage, wait;
    private static Screen editor, recordsScreen;
    private static AnecdoteDefinition definition, saved;
    private static CompletableFuture<?> pending;
    private static AnecdoteCardLayout afterMove;
    private static AnecdoteRewardsScreen rewards;
    private static double intermediateScroll;
    private static Object stableTooltip;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0 || game.screen instanceof AnimatedQuestScreen a && a.inputBlocked()) return false;
        if (pending != null) { if (!pending.isDone()) return false; pending.join(); pending = null; }
        switch (stage) {
            case 0 -> {
                game.options.guiScale().set(2); resize(game, 1920, 1080);
                game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                pending = game.reloadResourcePacks();
                ClientEditMode.set(true); ClientPreferences.get().reducedMotion = true;
                definition = new AnecdoteDefinition(ID, "实时编辑演示", "山路来信", "minecraft:book", "旅途记录", List.of(), true, false, 0);
                for (String asset : List.of("category_journey", "category_legendary", "category_hot_events",
                        "category_anecdotes", "refresh", "fullscreen", "sort_up", "sort_down")) {
                    var resource = game.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "xero_quest_journal", "textures/gui/navigation/" + asset + ".png")).orElseThrow();
                    try (var input = resource.open(); var image = com.mojang.blaze3d.platform.NativeImage.read(input)) {
                        int pixels = 0;
                        for (int py = 0; py < image.getHeight(); py++) for (int px = 0; px < image.getWidth(); px++)
                            if ((image.getPixelRGBA(px, py) >>> 24) > 128) pixels++;
                        require(pixels >= 150 && image.getWidth() >= 32, "Blank or undersized PNG: " + asset);
                    }
                }
                var json = new com.google.gson.Gson();
                require(json.fromJson(json.toJson(definition), AnecdoteDefinition.class).equals(definition), "Definition JSON failed");
                var legacy = json.toJsonTree(definition).getAsJsonObject(); legacy.remove("cardLayout");
                require(json.fromJson(legacy, AnecdoteDefinition.class).cardLayout().equals(AnecdoteCardLayout.defaults()), "Legacy default failed");
                verifyStampCycle();
                editor = entryEditor(null, definition); game.setScreen(editor); wait = 5;
            }
            case 1 -> {
                double scale = (double) call(editor, "previewScale");
                double x = (double) call(editor, "previewX") + 45 * scale;
                double y = (double) call(editor, "previewY") + 40 * scale;
                editor.mouseClicked(x, y, 0); editor.mouseDragged(x + 10 * scale, y + 8 * scale, 0, 10 * scale, 8 * scale);
                editor.mouseReleased(x + 10 * scale, y + 8 * scale, 0);
                afterMove = layout();
                require(Math.abs(afterMove.image().x() - 17) < .1, "Image did not move in design coordinates");
                require(Math.abs(afterMove.image().y() - 10.4) < .1, "Image Y did not move");
                button(editor, "anecdote-undo").onPress();
                require(layout().equals(definition.cardLayout()), "Drag undo failed");
                button(editor, "anecdote-redo").onPress(); require(layout().equals(afterMove), "Drag redo failed");
                dragHandle("width", 9, 0); require(layout().image().width() > afterMove.image().width(), "Width handle failed");
                dragHandle("height", 0, 10); require(layout().image().height() > afterMove.image().height(), "Height handle failed");
                double oldScale = layout().image().scale();
                dragHandle("scale", 10, 10); require(layout().image().scale() > oldScale, "Proportional scale handle failed");
                select("text"); number("font_size").setValue("18"); number("x").setValue("8");
                require(layout().fontSize() == 18 && layout().text().x() == 8, "Numeric text controls are not live");
                select("background"); number("width").setValue("94"); number("height").setValue("94");
                require(layout().background().width() == 94 && layout().background().height() == 94, "Background geometry not live");
                select("image"); wait = 3;
            }
            case 2 -> {
                capture(game, "anecdote-entry-layout-zh-1920");
                saved = (AnecdoteDefinition) call(editor, "draft");
                require(saved.cardLayout().equals(layout()), "Editor draft discarded appearance");
                pending = CompletableFuture.runAsync(() -> {
                    try {
                        AnecdoteStore.save(saved);
                        require(AnecdoteStore.find(ID).equals(saved), "File round-trip discarded layout");
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        AnecdoteNetwork.sendSnapshot(player);
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
                button(editor, "anecdote-tab-content").onPress(); button(editor, "anecdote-records").onPress();
                recordsScreen = game.screen; wait = 4;
            }
            case 3 -> {
                require(!recordsScreen.getTitle().getString().contains("screen.xero"), "Records title is not translated");
                var close = button(recordsScreen, "anecdote-records-close");
                require(close.getX() + close.getWidth() <= recordsScreen.width, "Records close offscreen");
                capture(game, "anecdote-record-list-zh-1920");
                close.onPress(); require(game.screen == editor, "Close list did not restore draft");
                require(((AnecdoteDefinition) call(editor, "draft")).cardLayout().equals(saved.cardLayout()), "Child return lost layout");
                require(AnecdoteClientState.find(ID).orElseThrow().cardLayout().equals(saved.cardLayout()), "Snapshot lost layout");
                resize(game, 854, 480); wait = 4;
            }
            case 4 -> {
                require((double) field(field(editor, "scroll"), "maximum") > 0, "Compact form cannot scroll");
                capture(game, "anecdote-entry-layout-compact");
                resize(game, 1920, 1080);
                game.setScreen(new AnecdoteScreen(null)); wait = 8;
            }
            case 5 -> {
                capture(game, "anecdote-book-toolbar-zh-1920");
                // Validate content in the empty rewards page without any selected sticker.
                rewards = new AnecdoteRewardsScreen(game.screen); game.setScreen(rewards); wait = 4;
            }
            case 6 -> {
                capture(game, "anecdote-rewards-empty-zh-1920");
                pending = CompletableFuture.runAsync(() -> {
                    try {
                        var rows = new ArrayList<AnecdoteDefinition.Record>();
                        for (int i = 0; i < 12; i++) rows.add(new AnecdoteDefinition.Record(String.format("record_%02d", i),
                                "记录 " + i, "记录正文", i % 2 == 0 ? "minecraft:emerald" : "minecraft:diamond"));
                        AnecdoteStore.save(saved.withRecords(rows));
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        AnecdoteRuntime.discover(player, ID);
                        require(AnecdoteRuntime.claimReward(player, "__xero_anecdote_00000000-0000-0000-0000-000000000000")
                                .equals("incomplete"), "Invalid batch was claimable");
                        for (var row : rows) require(AnecdoteRuntime.completeRecord(player, ID, row.id()), "Completion failed");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer()); wait = 5;
            }
            case 7 -> {
                if (AnecdoteClientState.snapshot().completed().size() < 12) return false;
                // The page is already open while new records arrive; all must enter without reopening it.
                ClientPreferences.get().reducedMotion = false; wait = 100;
            }
            case 8 -> {
                capture(game, "anecdote-rewards-four-column-zh-1920");
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    var state = AnecdoteRuntime.snapshot(player);
                    var ordered = AnecdoteRewardPage.ordered(state.completed());
                    String batch = AnecdoteRewardPage.batchId(ordered, 0);
                    int before = player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND);
                    require(AnecdoteRuntime.claimReward(player, batch).equals("claimed"), "Milestone claim failed");
                    require(player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == before + 50, "Reward amount wrong");
                    require(AnecdoteRuntime.claimReward(player, batch).equals("already_claimed"), "Milestone claimed twice");
                    var progress = QuestProgressData.get(player.server);
                    var loaded = QuestProgressData.load(progress.save(new net.minecraft.nbt.CompoundTag(), player.registryAccess()), player.registryAccess());
                    require(loaded.completedAnecdotes(player.getUUID()).size() == 12, "Clear deleted completion history");
                    require(AnecdoteRuntime.snapshot(player).claimedRewards().contains(batch), "Claim receipt missing from snapshot");
                }, game.getSingleplayerServer());
                game.setScreen(QuestScreen.pickResource(null, "minecraft:diamond", false, value -> {})); wait = 8;
            }
            case 9 -> {
                var picker = field(game.screen, "iconPicker");
                if (((List<?>) field(picker, "filteredChoices")).size() < 100) return false;
                var choice = game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .filter(button -> button.styleId().equals("icon-entry") && button.visible).findFirst().orElseThrow();
                stableTooltip = choice.getTooltip();
                call(game.screen, "updateIconPickerViewport");
                require(stableTooltip == choice.getTooltip(), "Picker rebuilt tooltip without changing its item");
                double maximum = (double) field(field(picker, "scroll"), "maximum");
                require(maximum > 0, "Picker did not enable scrolling");
                var scroll = field(picker, "scroll"); set(scroll, "position", 0.); set(scroll, "target", 0.);
                game.screen.mouseScrolled(80, 160, 0, -2);
                intermediateScroll = (double) field(scroll, "position");
                require((double) field(scroll, "target") > intermediateScroll, "Picker jumped instead of scrolling");
                wait = 1;
            }
            case 10 -> {
                var picker = field(game.screen, "iconPicker");
                double position = (double) field(field(picker, "scroll"), "position");
                require(position > intermediateScroll, "Picker did not advance between rendered frames");
                capture(game, "icon-picker-smooth-zh-1920");
                button(game.screen, "icon-preview").onPress(); wait = 5;
            }
            case 11 -> {
                capture(game, "icon-preview-contained-caption-zh-1920");
                require(game.screen.getClass().getSimpleName().equals("QuestIconPreviewScreen"), "Preview did not open");
                game.setScreen(QuestScreen.overview(null)); wait = 6;
            }
            case 12 -> {
                capture(game, "overview-default-category-icons-zh-1920");
                require(button(game.screen, "overview-main") != null && button(game.screen, "overview-anecdotes") != null,
                        "Overview entries missing");
                game.setScreen(null);
                pending = CompletableFuture.runAsync(() -> { try { AnecdoteStore.delete(ID); } catch (Exception e) { throw new RuntimeException(e); } },
                        game.getSingleplayerServer());
            }
            case 13 -> { return true; }
        }
        stage++; return false;
    }
    private static void verifyStampCycle() throws Exception {
        Class<?> type = Class.forName("xtdpotato.xero_quest_journal.client.AnecdoteRewardAnimation");
        var ctor = type.getDeclaredConstructor(int.class); ctor.setAccessible(true); Object animation = ctor.newInstance(9);
        invoke(animation, "target", new Class<?>[]{int.class}, 12);
        invoke(animation, "advance", new Class<?>[]{long.class, boolean.class}, 1L, false);
        invoke(animation, "advance", new Class<?>[]{long.class, boolean.class}, 420_000_002L, false);
        require((int) call(animation, "countOnPage") == 10, "Full stamp page was skipped");
        invoke(animation, "advance", new Class<?>[]{long.class, boolean.class}, 920_000_003L, false);
        require((int) call(animation, "page") == 1 && (int) call(animation, "countOnPage") == 0, "Full page did not reset");
        invoke(animation, "advance", new Class<?>[]{long.class, boolean.class}, 1_340_000_004L, false);
        require((int) call(animation, "displayed") == 11 && (int) call(animation, "entering") == 11, "New page did not resume stamping");
    }
    private static void dragHandle(String mode, double dx, double dy) throws Exception {
        var p = layout().image();
        double w = p.width() * p.scale(), h = p.height() * 1.25 * p.scale();
        double px = p.x() + p.width() * (1 - p.scale()) / 2;
        double py = p.y() * 1.25 + p.height() * 1.25 * (1 - p.scale()) / 2;
        double scale = (double) call(editor, "previewScale");
        double x = (double) call(editor, "previewX") + (px + (mode.equals("height") ? w / 2 : w)) * scale;
        double y = (double) call(editor, "previewY") + (py + (mode.equals("width") ? h / 2 : h)) * scale;
        editor.mouseClicked(x, y, 0); editor.mouseDragged(x + dx * scale, y + dy * scale, 0, dx * scale, dy * scale);
        editor.mouseReleased(x + dx * scale, y + dy * scale, 0);
    }
    private static Screen entryEditor(Screen parent, AnecdoteDefinition entry) throws Exception {
        var type = Class.forName("xtdpotato.xero_quest_journal.client.AnecdoteEntryEditorScreen");
        var constructor = type.getDeclaredConstructor(Screen.class, AnecdoteDefinition.class, boolean.class); constructor.setAccessible(true);
        return (Screen) constructor.newInstance(parent, entry, false);
    }
    private static void select(String value) throws Exception { set(editor, "selected", value); call(editor, "init"); }
    private static AnecdoteCardLayout layout() throws Exception { return (AnecdoteCardLayout) field(editor, "layout"); }
    private static EditBox number(String key) throws Exception { return (EditBox) ((Map<?, ?>) field(editor, "numbers")).get(key); }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing " + id));
    }
    private static Field property(Object target, String key) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(key); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(key);
    }
    private static Object field(Object target, String key) throws Exception { return property(target, key).get(target); }
    private static void set(Object target, String key, Object value) throws Exception { property(target, key).set(target, value); }
    private static Object call(Object target, String key) throws Exception { return invoke(target, key, new Class<?>[0]); }
    private static Object invoke(Object target, String key, Class<?>[] types, Object... values) throws Exception {
        var method = target.getClass().getDeclaredMethod(key, types); method.setAccessible(true); return method.invoke(target, values);
    }
    private static void resize(Minecraft game, int w, int h) {
        game.getWindow().setWindowed(w, h); game.getWindow().setWidth(w); game.getWindow().setHeight(h); game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(OUTPUT.resolve(name + ".png")); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message + " at authoring stage " + stage); }
}
