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
            "风雪来信", "风雪", "minecraft:diamond", "山路上的一封信",
            List.of(new AnecdoteDefinition.Record("arrival", "雪夜相遇", "在雪夜的山路上，遇到了迷路的旅人。",
                            "minecraft:diamond"),
                    new AnecdoteDefinition.Record("after", "后来", "后来，她寄来了一封感谢信。", "minecraft:emerald")),
            true, false, 0);
    private static final AnecdoteDefinition ENTRY_B = new AnecdoteDefinition("smoke_anecdote_b",
            "尚未发现", "文字超出五个字符", "minecraft:paper", "被遗忘的故事", List.of(), false, false, 1);
    private static int stage, played;
    private static long lastStarted;
    private static long rewardsOpenedAt;

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
                stage++;
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
                if (System.nanoTime() - current > 500_000_000L && played == 1) {
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
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                AnecdoteClientState.request();
                game.setScreen(new AnecdoteScreen(null));
                stage++;
            }
            case 6 -> {
                if (AnecdoteClientState.snapshot().entries().size() < 2) return false;
                if (AnecdoteClientState.snapshot().completed().stream()
                        .noneMatch(value -> value.entryId().equals(ENTRY_A.id()) && value.recordId().equals("arrival")))
                    return false;
                require(game.screen instanceof AnecdoteScreen, "Chronicle screen did not open");
                Object openingMotion = field(game.screen, "pageOpen");
                if (!(boolean) call(openingMotion, "settled")) return false;
                capture(game, "anecdote-chronicle-1920");
                var screen = game.screen;
                var cards = (java.util.List<?>) field(screen, "cardBounds");
                var entries = (java.util.List<?>) field(screen, "visibleEntries");
                require(cards.size() >= 2 && entries.size() >= 2, "Chronicle cards missing");
                int lockedIndex = -1;
                for (int index = 0; index < entries.size(); index++)
                    if (ENTRY_B.id().equals(call(entries.get(index), "id"))) lockedIndex = index;
                require(lockedIndex >= 0, "Undiscovered entry was hidden despite its visibility setting");
                require(((java.util.List<?>) field(screen, "recordRows")).size() == 2,
                        "Multiple records did not remain visible under one anecdote");
                String previous = (String) reflected(screen, "selectedId").get(screen);
                var bounds = cards.get(lockedIndex);
                int x = (int) call(bounds, "x") + (int) call(bounds, "width") / 2;
                int y = (int) call(bounds, "y") + (int) call(bounds, "height") / 2;
                screen.mouseClicked(x, y, 0);
                require(previous.equals(reflected(screen, "selectedId").get(screen)),
                        "Chronicle selected on press instead of release");
                screen.mouseReleased(x, y, 0);
                require(ENTRY_B.id().equals(reflected(screen, "selectedId").get(screen)),
                        "Chronicle did not select on release inside the same card");
                screen.mouseClicked((int) call(cards.get(0), "x") + 4,
                        (int) call(cards.get(0), "y") + 4, 0);
                screen.mouseReleased(1, 1, 0);
                require(ENTRY_B.id().equals(reflected(screen, "selectedId").get(screen)),
                        "Chronicle selection changed after release outside the card");
                int rx = (int) call(cards.get(lockedIndex), "x") + 4,
                        ry = (int) call(cards.get(lockedIndex), "y") + 4;
                screen.mouseClicked(rx, ry, 1);
                require(game.screen.getClass().getSimpleName().equals("AnecdoteEntryEditorScreen"),
                        "Right-click did not open the selected anecdote editor");
                var chronicle = new AnecdoteScreen(null);
                game.setScreen(new AnecdoteRewardsScreen(chronicle));
                rewardsOpenedAt = System.nanoTime();
                stage++;
            }
            case 7 -> {
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
        var value = object.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value;
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
