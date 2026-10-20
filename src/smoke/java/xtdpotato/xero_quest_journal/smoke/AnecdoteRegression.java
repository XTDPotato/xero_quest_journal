package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.AnecdoteToast;
import xtdpotato.xero_quest_journal.client.ClientPreferences;
import xtdpotato.xero_quest_journal.client.AnecdoteClientState;
import xtdpotato.xero_quest_journal.client.AnecdoteScreen;
import xtdpotato.xero_quest_journal.client.AnecdoteRewardsScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.client.ClientEditMode;
import xtdpotato.xero_quest_journal.core.io.AnecdoteStore;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import xtdpotato.xero_quest_journal.core.runtime.AnecdoteRuntime;
import xtdpotato.xero_quest_journal.core.network.QuestClientInbox;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;

final class AnecdoteRegression {
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final long DURATION = 3_200_000_000L;
    private static final AnecdoteDefinition ENTRY_A = new AnecdoteDefinition("smoke_anecdote_a",
            "风雪来信", "风雪", "", "山路上的一封信",
            List.of(new AnecdoteDefinition.Record("arrival", "雪夜相遇", "在雪夜的山路上，遇到了迷路的旅人。",
                            "minecraft:diamond"),
                    new AnecdoteDefinition.Record("after", "后来", "后来，她寄来了一封感谢信。", "minecraft:emerald"),
                    new AnecdoteDefinition.Record("farewell", "再会", "旅人终于抵达了目的地。", "minecraft:book")),
            true, false, 0);
    private static final AnecdoteDefinition ENTRY_B = new AnecdoteDefinition("smoke_anecdote_b",
            "尚未发现", "文字超出五个字符", "minecraft:paper", "被遗忘的故事", List.of(), false, false, 1);
    private static int stage, played;
    private static long lastStarted;
    private static long rewardsOpenedAt;
    private static AnecdoteScreen chronicleScreen;

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> {
                var defaults = new QuestNetwork.AnecdotePayload();
                require(defaults.scale() == .7 && defaults.position().equals("top"),
                        "Default size or anchor changed");
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                game.setScreen(new Probe());
                AnecdoteToast.clear();
                ClientPreferences.get().reducedMotion = false;
                var server = game.getSingleplayerServer();
                var source = game.player.createCommandSourceStack().withPermission(2);
                var root = server.getCommands().getDispatcher().getRoot().getChild("xero_anecdote");
                require(root != null, "Missing command");
                require(!root.canUse(source.withPermission(0)), "Unprivileged command access");
                var dispatcher = server.getCommands().getDispatcher();
                AnecdoteStore.save(ENTRY_A);
                AnecdoteStore.save(ENTRY_B);
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    require(AnecdoteRuntime.discover(player, ENTRY_A.id()), "Graph discover action failed");
                    require(AnecdoteRuntime.completeRecord(player, ENTRY_A.id(), "arrival"),
                            "Record completion action failed");
                    require(AnecdoteRuntime.completeRecord(player, ENTRY_A.id(), "after")
                                    && AnecdoteRuntime.completeRecord(player, ENTRY_A.id(), "farewell"),
                            "The other chronicle records did not complete");
                });
                require(dispatcher.parse("xero_quest open anecdotes", source).getExceptions().isEmpty(),
                        "Chronicle open command did not parse");
                String[] commands = {"xero_anecdote @s", "xero_anecdote @s 24 -12 0.8 top,left",
                        "xero_anecdote @a 0 0 0.7 bottom,right",
                        "xero_anecdote " + game.player.getGameProfile().getName() + " 0 0 0.7 center"};
                for (String command : commands) {
                    var parsed = dispatcher.parse(command, source);
                    require(parsed.getExceptions().isEmpty(),
                            "Command/selector did not parse: " + command + " " + parsed.getExceptions());
                }
                var left = new QuestNetwork.AnecdotePayload(24, -12, .8, "start,top");
                require(left.position().equals("left,top"), "Start alias was not normalized");
                try {
                    new QuestNetwork.AnecdotePayload(0, 0, .7, "top,bottom");
                    throw new AssertionError("Conflicting vertical anchors were accepted");
                } catch (IllegalArgumentException expected) {}
                for (var payload : java.util.List.of(new QuestNetwork.AnecdotePayload(), left,
                        new QuestNetwork.AnecdotePayload(0, 0, .7, "bottom,right"),
                        new QuestNetwork.AnecdotePayload(0, 0, .7, "center")))
                    QuestClientInbox.accept(payload);
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(), game.player.registryAccess());
                try {
                    var packet = new QuestNetwork.AnecdotePayload(24, -12, .8, "start,top");
                    QuestNetwork.AnecdotePayload.STREAM_CODEC.encode(buffer, packet);
                    require(QuestNetwork.AnecdotePayload.STREAM_CODEC.decode(buffer).equals(packet),
                            "Packet codec");
                } finally { buffer.release(); }
                var progress = new QuestProgressData();
                var owner = game.player.getUUID();
                progress.discoverAnecdote(owner, ENTRY_A.id());
                require(progress.completeAnecdoteRecord(owner, ENTRY_A.id(), "arrival"),
                        "First anecdote record completion was not recorded");
                require(!progress.completeAnecdoteRecord(owner, ENTRY_A.id(), "arrival"),
                        "Completed anecdote record was duplicated");
                var saved = progress.save(new net.minecraft.nbt.CompoundTag(), game.player.registryAccess());
                var restored = QuestProgressData.load(saved, game.player.registryAccess());
                require(restored.anecdoteDiscovered(owner, ENTRY_A.id())
                                && restored.anecdoteCompleted(owner, ENTRY_A.id(), "arrival")
                                && restored.newAnecdoteCompletions(owner).contains(ENTRY_A.id() + "/arrival"),
                        "Anecdote discovery/completion state did not survive save/load");
                stage = Boolean.getBoolean("xero.anecdoteUiTest") ? 5 : 1;
            }
            case 1 -> {
                if (!active()) return false;
                long current = started();
                if (current != lastStarted) {
                    if (played > 0) require(current - lastStarted >= DURATION, "Overlapping banners");
                    lastStarted = current;
                    played++;
                }
                long age = System.nanoTime() - current;
                if (played == 1 && age > 600_000_000L && age < 2_000_000_000L) {
                    capture(game, "anecdote-world-1920");
                    stage++;
                }
            }
            case 2 -> {
                long current = started();
                if (current != lastStarted) {
                    require(current - lastStarted >= DURATION, "Queue restarted a running banner");
                    lastStarted = current;
                    played++;
                }
                if (System.nanoTime() - current > 500_000_000L) {
                    try (NativeImage image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                        Files.createDirectories(OUTPUT);
                        image.writeToFile(OUTPUT.resolve("anecdote-screen-1920.png"));
                        int outside = image.getPixelRGBA(4, 4);
                        require((outside & 255) == 0x36 && (outside >> 8 & 255) == 0x53
                                && (outside >> 16 & 255) == 0x65, "Banner darkened outside world");
                        for (int y = 0; y < 8; y++)
                            require(image.getPixelRGBA(image.getWidth() / 2, y) == outside,
                                    "Top anchor is less than 4 pixels from the edge");
                        int center = image.getPixelRGBA(image.getWidth() / 2,
                                Math.round(image.getHeight() * .16F));
                        require(center != outside, "Banner did not render");
                    }
                    stage++;
                }
            }
            case 3 -> {
                long current = started();
                if (current != lastStarted) {
                    require(current - lastStarted >= DURATION, "Next notice began before full fade-out");
                    lastStarted = current;
                    played++;
                }
                if (active()) return false;
                require(played == 4, "Each configured command must play exactly once");
                lastStarted = System.nanoTime();
                stage++;
            }
            case 4 -> {
                require(!active(), "Idle notification replayed");
                if (System.nanoTime() - lastStarted < 400_000_000L) return false;
                for (int i = 0; i < 24; i++) QuestClientInbox.accept(new QuestNetwork.AnecdotePayload());
                for (int i = 0; i < 24; i++) {
                    AnecdoteToast.tick();
                    require(active(), "Dropped queued command " + i);
                    require(started() >= lastStarted, "Invalid queue start");
                    field("current").set(null, new QuestNetwork.AnecdotePayload());
                    field("started").setLong(null, System.nanoTime() - DURATION);
                }
                AnecdoteToast.tick();
                require(!active(), "Duplicate event after burst");
                QuestClientInbox.accept(new QuestNetwork.AnecdotePayload());
                AnecdoteToast.tick();
                ClientPreferences.get().reducedMotion = true;
                field("started").setLong(null, System.nanoTime() - 900_000_000L);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 360);
                game.getWindow().setWidth(640);
                game.getWindow().setHeight(360);
                game.resizeDisplay();
                stage++;
            }
            case 5 -> {
                capture(game, "anecdote-compact-reduced");
                QuestClientInbox.accept(new QuestNetwork.AnecdotePayload());
                QuestClientInbox.clear();
                AnecdoteToast.clear();
                AnecdoteToast.tick();
                require(!active() && QuestClientInbox.takeAnecdote() == null, "Connection cleanup");
                ClientEditMode.set(true);
                ClientPreferences.get().reducedMotion = false;
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                AnecdoteClientState.request();
                game.setScreen(QuestScreen.overview(null));
                stage++;
            }
            case 6 -> {
                if (!(game.screen instanceof QuestScreen overview)) return false;
                var card = overview.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast)
                        .filter(button -> button.styleId().equals("overview-anecdotes"))
                        .findFirst().orElseThrow();
                require(field(card, "icon").equals("anecdote_map"),
                        "Overview did not use the dedicated pixel map-pin icon for Anecdote Chronicle");
                require(card.getMessage().equals(Component.translatable(
                                "screen.xero_quest_journal.overview.category.anecdotes")),
                        "Anecdote Chronicle overview action uses the wrong localized title");
                require(overview.children().stream().filter(StyledButton.class::isInstance)
                                .map(StyledButton.class::cast)
                                .noneMatch(button -> button.styleId().equals("overview-world")),
                        "Duplicate World Quests action occupies the Travel Anecdotes slot");
                card.onPress();
                stage++;
                return false;
            }
            case 7 -> {
                if (game.screen instanceof QuestScreen) return false;
                if (!(game.screen instanceof AnecdoteScreen chronicle)) return false;
                {
                    var opening = field(chronicle, "pageOpen");
                    if (!(boolean) call(opening, "settled")) return false;
                    if (AnecdoteClientState.snapshot().entries().size() < 2
                            || AnecdoteClientState.snapshot().completed().size() < 3) return false;
                    if (((java.util.List<?>) field(chronicle, "cardBounds")).size() < 2) return false;
                    chronicleScreen = chronicle;
                    capture(game, "anecdote-chronicle-1920");
                    var cards = (java.util.List<?>) field(game.screen, "cardBounds");
                    var entries = (java.util.List<?>) field(game.screen, "visibleEntries");
                    require(cards.size() >= 2 && entries.size() >= 2, "Chronicle cards missing");
                    var selectedEntry = AnecdoteStore.find(ENTRY_A.id());
                    require(selectedEntry != null && selectedEntry.icon().isBlank(),
                            "Default scroll icon fixture was replaced with a custom icon");
                    require(((java.util.List<?>) field(game.screen, "recordRows")).size() == 3,
                            "Multiple records did not remain visible under one anecdote");
                    var add = field(game.screen, "addBounds");
                    int addX = (int) call(add, "x") + (int) call(add, "width") / 2;
                    int addY = (int) call(add, "y") + (int) call(add, "height") / 2;
                    require(game.screen.mouseClicked(addX, addY, 0), "Anecdote create button press was not handled");
                    game.screen.mouseReleased(addX, addY, 0);
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteEntryEditorScreen")
                                    && (boolean) field(game.screen, "creating"),
                            "Anecdote toolbar + did not open the create editor");
                    game.screen.onClose();
                    require(game.screen == chronicleScreen && !chronicleScreen.isClosing(),
                            "Closing an unrendered entry editor did not restore its chronicle");
                    var rewardAdd = field(chronicleScreen, "rewardAddBounds");
                    int rewardX = (int) call(rewardAdd, "x") + (int) call(rewardAdd, "width") / 2;
                    int rewardY = (int) call(rewardAdd, "y") + (int) call(rewardAdd, "height") / 2;
                    chronicleScreen.mouseClicked(rewardX, rewardY, 0);
                    chronicleScreen.mouseReleased(rewardX, rewardY, 0);
                    require(game.screen.getClass().getSimpleName().equals("QuestRewardsScreen"),
                            "Anecdote reward add button did not open the reward editor");
                    game.screen.onClose();
                    var recordAdd = field(chronicleScreen, "recordAddBounds");
                    int recordX = (int) call(recordAdd, "x") + (int) call(recordAdd, "width") / 2;
                    int recordY = (int) call(recordAdd, "y") + (int) call(recordAdd, "height") / 2;
                    require(chronicleScreen.mouseClicked(recordX, recordY, 0), "Record add toolbar click was not handled");
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteRecordEditorScreen"),
                            "Anecdote record + did not open its editor");
                    game.screen.onClose();
                    require(chronicleScreen.mouseClicked(recordX, recordY, 0), "Empty-page record add click was not handled");
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteRecordEditorScreen"),
                            "Record add button did not work while the page was otherwise empty");
                    game.screen.onClose();
                    var lockedIndex = entries.indexOf(ENTRY_B);
                    require(lockedIndex >= 0, "Undiscovered entry was hidden despite its visibility setting");
                    int x = (int) call(cards.get(lockedIndex), "x") + (int) call(cards.get(lockedIndex), "width") / 2;
                    int y = (int) call(cards.get(lockedIndex), "y") + (int) call(cards.get(lockedIndex), "height") / 2;
                    String previous = (String) field(chronicleScreen, "selectedId");
                    chronicleScreen.mouseClicked(x, y, 0);
                    require(previous.equals(field(chronicleScreen, "selectedId")),
                            "Chronicle selected on press instead of release");
                    chronicleScreen.mouseReleased(x, y, 0);
                    require(ENTRY_B.id().equals(field(chronicleScreen, "selectedId")),
                            "Chronicle did not select on release inside the same card");
                    chronicleScreen.onClose();
                    require(chronicleScreen.isClosing() && (boolean) field(chronicleScreen, "closingBook"),
                            "Chronicle did not use the book-close transition");
                    var rewards = new AnecdoteRewardsScreen(chronicleScreen);
                    game.setScreen(rewards);
                    require(rewards.animationPosition() < 1,
                            "Anecdote reward page skipped the popup-scale entrance animation");
                    rewardsOpenedAt = System.nanoTime();
                    stage++;
                }
            }
            case 8 -> {
                if (System.nanoTime() - rewardsOpenedAt < 650_000_000L) return false;
                require(game.screen instanceof AnecdoteRewardsScreen, "Anecdote reward sticker page did not open");
                capture(game, "anecdote-rewards-1920");
                require(!AnecdoteClientState.snapshot().completed().isEmpty(),
                        "Reward stickers removed the completed record");
                game.setScreen(null);
                AnecdoteStore.delete(ENTRY_A.id());
                AnecdoteStore.delete(ENTRY_B.id());
                return true;
            }
            default -> throw new AssertionError("Unexpected stage");
        }
        return false;
    }

    private static Field field(String name) throws Exception {
        var result = AnecdoteToast.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static Field reflected(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var value = type.getDeclaredField(name);
                value.setAccessible(true);
                return value;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object object, String name) throws Exception {
        return reflected(object, name).get(object);
    }

    private static Object call(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }

    private static boolean active() throws Exception { return field("current").get(null) != null; }
    private static long started() throws Exception { return field("started").getLong(null); }

    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (anecdote stage " + stage + ")");
    }

    private static final class Probe extends Screen {
        Probe() { super(Component.literal("Notification backdrop")); }
        @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
            graphics.fill(0, 0, width, height, 0xFF365365);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
