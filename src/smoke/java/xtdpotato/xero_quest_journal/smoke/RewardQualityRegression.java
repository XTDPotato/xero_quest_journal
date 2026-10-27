package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "xero_quest_journal", value = Dist.CLIENT)
final class RewardQualityRegression {
    private static final String ID = "quality_test";
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final Reward PURPLE = new Reward("item", "minecraft:experience_bottle", 20, "epic", "");
    private static final Reward RED = new Reward("item", "minecraft:diamond", 16, "mythic", "");
    private static final Reward CUSTOM = new Reward("item", "minecraft:emerald", 8, "epic", "#327A65");
    private static final List<Reward> REWARDS = List.of(PURPLE, RED, CUSTOM);
    private static final String BULK_A = ID + "_bulk_a", BULK_B = ID + "_bulk_b";
    private static final UUID OTHER_PLAYER = UUID.fromString("843b5306-524b-4ede-a239-fc03ea86c34e");
    private static int stage, wait;
    private static CompletableFuture<Void> work;
    private static QuestScreen journal;
    private static Screen captureScreen;
    private static String captureName = "";
    private static boolean inspectDot, inspectCards;
    private static boolean bulkSnapshotRequested;

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) throws Exception {
        if (captureName.isBlank() || event.getScreen() != captureScreen) return;
        if (event.getScreen() instanceof AnimatedQuestScreen screen && screen.inputBlocked()) return;
        var game = Minecraft.getInstance();
        event.getGuiGraphics().flush();
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(captureName + ".png"));
            if (inspectDot) {
                var row = button(journal, "quest-" + ID);
                int redPixels = 0;
                for (int x = row.getRight() - 10; x < row.getRight(); x++)
                    for (int y = row.getY() + 2; y < row.getY() + 10; y++) {
                        int color = pixel(image, journal, x, y);
                        if ((color >>> 16 & 255) > 200 && (color >>> 8 & 255) < 110
                                && (color & 255) < 150) redPixels++;
                    }
                require(redPixels >= 8,
                        "New-task red dot did not render at the row's top-right");
            }
            if (inspectCards) {
                var colors = new HashSet<Integer>();
                for (int i = 0; i < 7; i++) colors.add(pixel(image, event.getScreen(), 22 + i * 50, 65));
                require(colors.size() == 7, "Six qualities and custom background did not have distinct palettes");
                int red = pixel(image, event.getScreen(), 272, 65);
                require((red >>> 16 & 255) > (red >>> 8 & 255) * 1.5,
                        "Red quality rendered green");
                require(pixel(image, event.getScreen(), 322, 65) == 0x327A65,
                        "Explicit background override did not preserve its exact color");
                require(pixel(image, event.getScreen(), 22, 88) == pixel(image, event.getScreen(), 22, 92),
                        "Count band did not occupy the lower card region");
            }
        }
        captureName = "";
        inspectDot = inspectCards = false;
    }

    static boolean tick(Minecraft game) throws Exception {
        if (!captureName.isBlank() || --wait > 0) return false;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                ClientEditMode.set(false);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                work = CompletableFuture.runAsync(() -> {
                    try {
                        Files.deleteIfExists(QuestRepository.root().resolve(ID + ".json"));
                        Files.deleteIfExists(QuestRepository.root().resolve(BULK_A + ".json"));
                        Files.deleteIfExists(QuestRepository.root().resolve(BULK_B + ".json"));
                        Files.writeString(QuestRepository.root().resolve("quality_baseline.json"),
                                "{\"id\":\"quality_baseline\",\"title\":\"Already known task\",\"type\":\"world\","
                                        + "\"defaultUnlocked\":true,\"triggers\":[{\"type\":\"confirm\",\"count\":1}]}");
                        QuestRepository.reload();
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        QuestRuntime.snapshot(player);
                        var json = new com.google.gson.JsonObject();
                        json.addProperty("id", ID);
                        json.addProperty("title", "New quality task");
                        json.addProperty("type", "world");
                        json.addProperty("defaultUnlocked", true);
                        json.addProperty("rewardMode", "manual");
                        json.add("triggers", new com.google.gson.Gson().toJsonTree(
                                List.of(new QuestDefinition.Trigger("confirm", "", 1))));
                        json.add("rewards", new com.google.gson.Gson().toJsonTree(REWARDS));
                        Files.writeString(QuestRepository.root().resolve(ID + ".json"), json.toString());
                        QuestRepository.reload();
                        var data = QuestProgressData.get(player.server);
                        data.reset(player.getUUID(), QuestRepository.find(ID));
                        var snapshot = QuestRuntime.snapshot(player);
                        require(snapshot.get(ID).newTask(), "New task did not have server unread state");
                        var saved = QuestProgressData.load(data.save(new net.minecraft.nbt.CompoundTag(),
                                player.registryAccess()), player.registryAccess());
                        require(saved.isNewTask(player.getUUID(), ID), "Unread state was lost on world reload");
                        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(
                                io.netty.buffer.Unpooled.buffer(), player.registryAccess());
                        try {
                            var packet = new QuestNetwork.ProgressSnapshotPayload(snapshot, List.of());
                            QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.encode(buf, packet);
                            require(QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.decode(buf).views().get(ID)
                                    .rewards().equals(REWARDS), "Reward style snapshot codec lost fields");
                        } finally { buf.release(); }
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!work.isDone() || ClientQuestProgress.view(ID) == null
                        || !ClientQuestProgress.view(ID).newTask()) return false;
                work.join();
                journal = QuestScreen.task(null, "quality_baseline");
                game.setScreen(journal);
                set(journal, "searchQuery", "quality_");
                journal.init(game, journal.width, journal.height);
                capture(game, "new-task-red-dot");
                inspectDot = true;
            }
            case 2 -> {
                require(ClientQuestProgress.view(ID).newTask(), "Browsing the list marked a task read");
                button(journal, "quest-" + ID).onPress();
            }
            case 3 -> {
                if (ClientQuestProgress.view(ID).newTask()) return false;
                capture(game, "new-task-read");
                work = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestRuntime.activate(player, ID).equals("completed"), "Styled task could not complete");
                    var data = QuestProgressData.get(player.server);
                    var saved = QuestProgressData.load(data.save(new net.minecraft.nbt.CompoundTag(),
                            player.registryAccess()), player.registryAccess());
                    require(saved.entry(player.getUUID(), QuestRepository.find(ID)).rewards().equals(REWARDS),
                            "Frozen reward save/load lost styles");
                }, game.getSingleplayerServer());
            }
            case 4 -> {
                if (!work.isDone()) return false;
                work.join();
                game.setScreen(new QuestRewardsScreen(null, ID));
                require(button(game.screen, "rewards-quality-0") != null, "Reward quality selector absent");
                var draftsField = QuestRewardsScreen.class.getDeclaredField("rewards");
                draftsField.setAccessible(true);
                var drafts = (List<?>) draftsField.get(game.screen);
                Object diamond = drafts.get(1);
                set(diamond, "background", "#327A65");
                @SuppressWarnings("unchecked")
                var quality = (StyledChoiceButton<String>) button(game.screen, "rewards-quality-1");
                quality.select("mythic");
                var value = diamond.getClass().getDeclaredMethod("value");
                value.setAccessible(true);
                require(value.invoke(diamond).equals(RED), "Choosing red quality retained the old green override");
                verifyRewardEditorLayouts(game);
                capture(game, "reward-quality-editor");
            }
            case 5 -> {
                game.setScreen(new PaletteScreen());
                capture(game, "reward-six-quality-cards");
                inspectCards = true;
            }
            case 6 -> {
                game.setScreen(new QuestRewardPopupScreen(null, "Styled rewards", REWARDS));
                wait = 12;
                stage++;
                return false;
            }
            case 7 -> {
                capture(game, "reward-quality-receipt");
            }
            case 8 -> {
                work = CompletableFuture.runAsync(() -> {
                    try {
                        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                        var data = QuestProgressData.get(player.server);
                        List<String> knownBeforeBulk = QuestRepository.all().stream()
                                .map(QuestDefinition::id).toList();
                        data.observeTaskCatalog(player.getUUID(), knownBeforeBulk);
                        data.observeTaskCatalog(OTHER_PLAYER, knownBeforeBulk);
                        for (String id : List.of(BULK_A, BULK_B))
                            Files.writeString(QuestRepository.root().resolve(id + ".json"),
                                    "{\"id\":\"" + id + "\",\"title\":\"Unread bulk task\",\"type\":\"world\","
                                            + "\"defaultUnlocked\":true,\"triggers\":[{\"type\":\"confirm\",\"count\":1}]}");
                        QuestRepository.reload();
                        QuestRuntime.snapshot(player);
                        data.observeTaskCatalog(OTHER_PLAYER, QuestRepository.all().stream()
                                .map(QuestDefinition::id).toList());
                        var state = QuestRuntime.snapshot(player);
                        require(state.get(BULK_A).newTask() && state.get(BULK_B).newTask(),
                                "Bulk read fixtures did not become unread");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 9 -> {
                if (!work.isDone()) return false;
                work.join();
                if (!bulkSnapshotRequested) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new QuestNetwork.ProgressRequestPayload(-1));
                    bulkSnapshotRequested = true;
                    return false;
                }
                if (ClientQuestProgress.view(BULK_A) == null
                        || !ClientQuestProgress.view(BULK_A).newTask()
                        || ClientQuestProgress.view(BULK_B) == null || !ClientQuestProgress.view(BULK_B).newTask())
                    return false;
                journal = QuestScreen.task(null, "quality_baseline");
                game.setScreen(journal);
                var readAll = button(journal, "mark-all-read");
                var claimAll = button(journal, "claim_all");
                require(readAll.active && readAll.getRight() < claimAll.getX()
                        && readAll.getY() == claimAll.getY(), "Clear dots button was not left of claim all");
                capture(game, "task-mark-all-read-button");
            }
            case 10 -> {
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 800, 600);
                game.resizeDisplay();
                var readAll = button(journal, "mark-all-read");
                var claimAll = button(journal, "claim_all");
                require(readAll.getRight() < claimAll.getX() && readAll.getBottom() <= 32,
                        "Compact header buttons overlap");
                var categoryNext = button(journal, "category-next");
                require(categoryNext.getRight() < readAll.getX()
                                || categoryNext.getY() >= readAll.getBottom(),
                        "Clear dots button overlaps the category keycap");
                capture(game, "task-mark-all-read-compact");
            }
            case 11 -> button(journal, "mark-all-read").onPress();
            case 12 -> {
                if (ClientQuestProgress.hasUnreadTasks()) return false;
                require(!ClientQuestProgress.view(BULK_A).newTask() && !ClientQuestProgress.view(BULK_B).newTask(),
                        "One-click mark read left an unread task");
                require(!button(journal, "mark-all-read").active, "Read-all button did not disable after clearing");
                require(button(journal, "claim_all").active, "Clearing dots disabled reward claims");
                capture(game, "task-mark-all-read-cleared");
                work = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    var data = QuestProgressData.get(player.server);
                    require(data.entry(player.getUUID(), QuestRepository.find(ID)) != null
                                    && !data.entry(player.getUUID(), QuestRepository.find(ID)).claimed()
                                    && data.entry(player.getUUID(), QuestRepository.find(ID)).rewards().equals(REWARDS),
                            "Read-all changed the pending reward entitlement");
                    require(data.entry(player.getUUID(), QuestRepository.find(BULK_A)) == null
                                    && data.entry(player.getUUID(), QuestRepository.find(BULK_B)) == null,
                            "Read-all completed a task");
                    require(data.isNewTask(OTHER_PLAYER, "unread"), "Read-all cleared another player's red dots");
                    require(QuestRuntime.markAllTasksRead(player).equals("already_read"),
                            "Read-all was not idempotent");
                    var saved = QuestProgressData.load(data.save(new net.minecraft.nbt.CompoundTag(),
                            player.registryAccess()), player.registryAccess());
                    require(!saved.isNewTask(player.getUUID(), BULK_A)
                                    && saved.isNewTask(OTHER_PLAYER, "unread"),
                            "Bulk read state did not survive save/load");
                }, game.getSingleplayerServer());
            }
            case 13 -> {
                if (!work.isDone()) return false;
                work.join();
                Files.writeString(OUTPUT.resolve("reward-quality-result.txt"),
                        "PASS: persistent unread dot and detail-open read action; six quality palettes plus background override;\n"
                                + "38px card height, quality editing clears old overrides, styled reward JSON/NBT and progress snapshot codec;\n"
                                + "header mark-all-read, compact layout, idempotency, player isolation and unchanged rewards/completion.\n");
                Files.copy(OUTPUT.resolve("reward-quality-result.txt"), OUTPUT.resolve("result.txt"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            default -> throw new AssertionError("Quality stage " + stage);
        }
        stage++;
        wait = 4;
        return false;
    }

    private static void verifyRewardEditorLayouts(Minecraft game) throws Exception {
        Screen original = game.screen;
        String lootJson = new com.google.gson.Gson().toJson(new xtdpotato.xero_quest_journal.core.model.LootDefinition(
                "editor_inline_test", "Inline editor test", xtdpotato.xero_quest_journal.core.model.LootDefinition.Mode.RANDOM,
                1, "", List.of(new xtdpotato.xero_quest_journal.core.model.LootDefinition.Entry(
                "first", "item", "minecraft:coal", 2, 1, "always", true, "", ""),
                new xtdpotato.xero_quest_journal.core.model.LootDefinition.Entry(
                        "second", "item", "minecraft:iron_nugget", 4, 1, "always", true, "", ""))));
        var legacy = new Reward("item", "inline:" + lootJson, 2);
        var empty = new Reward("item", "", 1);
        var experience = new Reward("experience", "", 2);
        var editor = new QuestRewardsScreen(null, List.of(legacy, empty, experience), "manual", (a, b) -> {});
        game.setScreen(editor);
        var draftsField = QuestRewardsScreen.class.getDeclaredField("rewards");
        draftsField.setAccessible(true);
        List<?> drafts = (List<?>) draftsField.get(editor);
        require(drafts.size() == 2, "Empty item reward was left as a blank editor row");
        require(field(drafts.getFirst(), "type").equals("loot"),
                "Legacy inline loot-table JSON is still edited as an item ID");
        require(QuestRewardsScreen.icon("loot", "inline:" + lootJson)
                        .equals(xtdpotato.xero_quest_journal.core.model.LootDefinition.DEFAULT_DISPLAY_ICON)
                        && QuestIcons.valid(QuestRewardsScreen.icon("loot", "inline:" + lootJson)),
                "Inline loot reward has no valid display icon");

        var columns = QuestRewardsScreen.class.getDeclaredMethod("tableColumns");
        columns.setAccessible(true);
        int tableColumns = (int) columns.invoke(editor);
        require(tableColumns >= 8 && tableColumns <= 12,
                "Reward table does not render 8-12 items per row");
        var rows = QuestRewardsScreen.class.getDeclaredMethod("rows");
        rows.setAccessible(true);
        require((int) rows.invoke(editor) >= 3,
                "Reward list viewport does not display at least three entries");

        set(editor, "tableMode", true);
        var init = QuestRewardsScreen.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(editor);
        tableColumns = (int) columns.invoke(editor);
        require(tableColumns >= 8 && tableColumns <= 12,
                "Reward table does not retain its responsive column count after switching modes");
        var tile = button(editor, "rewards-tile-0");
        var panelWidth = QuestRewardsScreen.class.getDeclaredMethod("panelWidth");
        panelWidth.setAccessible(true);
        require(tile.getWidth() >= (int) panelWidth.invoke(editor) / tableColumns - 1,
                "Reward grid cells have excessive horizontal margins");

        set(editor, "tableMode", false);
        var edit = QuestRewardsScreen.class.getDeclaredMethod("editReward", int.class);
        edit.setAccessible(true);
        edit.invoke(editor, 0);
        var editDraft = QuestRewardsScreen.class.getDeclaredField("editDraft");
        editDraft.setAccessible(true);
        Object draft = editDraft.get(editor);
        require(field(draft, "type").equals("loot"), "Inline loot dialog changed the reward type");
        var target = editor.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast)
                .filter(box -> box.getValue().equals("Inline editor test")).findFirst().orElseThrow();
        require(!target.getValue().contains("inline:"),
                "Inline table JSON leaked into the reward target editor");
        button(editor, "rewards-dialog-loot-inspect").onPress();
        require(game.screen instanceof LootTablePopupScreen
                        && ((List<?>) field(game.screen, "entries")).size() == 2,
                "Clicking the inline loot reward icon did not open its table details");
        game.setScreen(original);
    }

    private static final class PaletteScreen extends Screen {
        PaletteScreen() { super(Component.literal("Reward qualities")); }
        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, 0xFF20242B);
            var qualities = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic", "epic");
            try {
                var renderer = Class.forName("xtdpotato.xero_quest_journal.client.RewardCardRenderer");
                var method = renderer.getDeclaredMethod("render", GuiGraphics.class,
                        net.minecraft.client.gui.Font.class, String.class, int.class, String.class, String.class,
                        int.class, int.class, float.class, float.class, boolean.class, boolean.class);
                method.setAccessible(true);
                for (int i = 0; i < qualities.size(); i++)
                    method.invoke(null, g, font, "minecraft:experience_bottle", 20,
                            qualities.get(i), i == 6 ? "#327A65" : "", 20 + i * 50, 60, 1F, 1F, false, false);
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private static int pixel(NativeImage image, Screen screen, int x, int y) {
        int value = image.getPixelRGBA(Math.clamp(x * image.getWidth() / screen.width, 0, image.getWidth() - 1),
                Math.clamp(y * image.getHeight() / screen.height, 0, image.getHeight() - 1));
        return (value & 255) << 16 | value & 0xFF00 | value >>> 16 & 255;
    }
    private static void capture(Minecraft game, String name) { captureName = name; captureScreen = game.screen; }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (quality stage " + stage + ")");
    }
}
