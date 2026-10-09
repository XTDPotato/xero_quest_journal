package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.ClientPreferences;
import xtdpotato.xero_quest_journal.client.ClientQuestProgress;
import xtdpotato.xero_quest_journal.client.JournalListButton;
import xtdpotato.xero_quest_journal.client.QuestHudRenderer;
import xtdpotato.xero_quest_journal.client.QuestIcons;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Uses real server tracking, rendered HUD pixels, and the corresponding journal row. */
final class TrackingHudRegression {
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final String IMAGE_FILE = "tracking_hud_probe.png";
    private static final String IMAGE = QuestIcons.LOCAL_NAMESPACE + ":"
            + HexFormat.of().formatHex(IMAGE_FILE.getBytes(StandardCharsets.UTF_8));
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("main", "main", "auto", "", ""),
            new Fixture("commission", "commission", "auto", "minecraft:book", ""),
            new Fixture("legendary", "legendary", "auto", "", ""),
            new Fixture("world", "world", "auto", "minecraft:diamond", ""),
            new Fixture("override_gold", "world", "main", "", ""),
            new Fixture("override_world", "main", "world", "", ""),
            new Fixture("custom_image", "world", "auto", "minecraft:book", IMAGE));
    private static int stage, wait, index;
    private static CompletableFuture<Void> work;
    private static boolean originalMotion, originalHud;

    private record Fixture(String name, String type, String style, String cover, String trackedImage) {
        String id() { return "tracking_hud_" + name; }
        String resolvedStyle() { return style.equals("auto") ? type : style; }
        int color() {
            if (!trackedImage.isBlank()) return 0xF13DA7;
            return switch (resolvedStyle()) {
                case "main" -> 0xFFEB30;
                case "commission" -> 0xD6B7FF;
                default -> 0x9DF2FF;
            };
        }
    }

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        Fixture fixture = FIXTURES.get(index);
        switch (stage) {
            case 0 -> {
                originalMotion = ClientPreferences.get().reducedMotion;
                originalHud = ClientPreferences.get().trackingHud;
                ClientPreferences.get().reducedMotion = true;
                ClientPreferences.get().trackingHud = true;
                game.options.pauseOnLostFocus = false;
                game.options.guiScale().set(2);
                resize(game, 1920, 1080);
                createImage();
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    try {
                        var gson = new Gson();
                        for (var entry : FIXTURES) {
                            Files.writeString(QuestRepository.root().resolve(entry.id() + ".json"), gson.toJson(Map.of(
                                    "id", entry.id(), "title", "Tracked " + entry.name(),
                                    "description", "Tracking marker reference", "type", entry.type(),
                                    "icon", entry.cover(), "defaultUnlocked", true, "trackable", true,
                                    "triggers", List.of(Map.of("type", "event", "item", "hud_probe", "count", 4)),
                                    "trailingIcon", new ListIconConfig(
                                            entry.trackedImage().isBlank() ? "" : "minecraft:compass",
                                            entry.trackedImage(), true, !entry.trackedImage().isBlank(),
                                            1.0F, 14, 14, 0, 0, entry.style()))));
                        }
                        QuestRepository.reload();
                        require(QuestRepository.errors().isEmpty(), "Invalid HUD fixtures: " + QuestRepository.errors());
                    } catch (Exception failure) { throw new IllegalStateException(failure); }
                }, server);
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
            }
            case 2 -> {
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    QuestProgressData.get(server).trackOnly(player.getUUID(), fixture.id());
                }, server);
            }
            case 3 -> {
                if (!work.isDone()) return false;
                work.join();
                PacketDistributor.sendToServer(new QuestNetwork.ProgressRequestPayload(-1));
            }
            case 4 -> {
                if (!ClientQuestProgress.isTracked(fixture.id())
                        || ClientQuestProgress.trackedIds().size() != 1
                        || ClientQuestProgress.view(fixture.id()) == null) return false;
                game.setScreen(null);
            }
            case 5 -> {
                require(game.getWindow().getWidth() >= 1600 && game.getWindow().getHeight() >= 900,
                        "HUD test did not use a large window");
                checkHud(game, fixture, true);
                capture(game, "tracking-hud-" + fixture.name());
                game.setScreen(QuestScreen.task(null, fixture.id()));
            }
            case 6 -> {
                var row = game.screen.children().stream().filter(JournalListButton.class::isInstance)
                        .map(JournalListButton.class::cast)
                        .filter(button -> button.styleId().equals("quest-" + fixture.id())).findFirst().orElseThrow();
                require(row.visible && row.trackingStyle().equals(fixture.resolvedStyle()),
                        "List/HUD tracking configuration mismatch");
                if (fixture.trackedImage().isBlank()) TrackingIndicatorRegression.check(game, fixture.id(), true);
                else {
                    int top = row.getY() + (row.getHeight() - 14) / 2;
                    require(countColor(game, row.getRight() - 20, top, 16, 14, fixture.color()) >= 2,
                            "Custom tracked image missing from list");
                }
                capture(game, "tracking-list-" + fixture.name());
                index++;
                if (index < FIXTURES.size()) {
                    stage = 2;
                    wait = 12;
                    return false;
                }
                index = 0;
                stage = 7;
                game.setScreen(null);
                resize(game, 854, 480);
                wait = 12;
                return false;
            }
            case 7 -> {
                checkHud(game, FIXTURES.getLast(), true);
                capture(game, "tracking-hud-compact");
                ClientPreferences.get().trackingHud = false;
            }
            case 8 -> {
                checkHud(game, FIXTURES.getLast(), false);
                resize(game, 1920, 1080);
                ClientPreferences.get().trackingHud = true;
                ClientPreferences.get().reducedMotion = false;
            }
            case 9 -> {
                checkHud(game, FIXTURES.getLast(), true);
            }
            case 10 -> {
                if (!MailBannerTrackingRegression.tick(game)) return false;
                ClientPreferences.get().trackingHud = originalHud;
                ClientPreferences.get().reducedMotion = originalMotion;
                return true;
            }
            default -> throw new AssertionError("Unknown tracking HUD stage " + stage);
        }
        stage++;
        wait = 15;
        return false;
    }

    private static void createImage() throws Exception {
        Files.createDirectories(QuestIcons.localRoot());
        try (var image = new NativeImage(16, 16, false)) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                image.setPixelRGBA(x, y, Math.abs(x - 7.5) + Math.abs(y - 7.5) < 6 ? 0xFFA73DF1 : 0);
            image.writeToFile(QuestIcons.localRoot().resolve(IMAGE_FILE));
        }
    }

    private static void checkHud(Minecraft game, Fixture fixture, boolean visible) throws Exception {
        int matches = countColor(game, 18, 42, 16, 16, fixture.color());
        require(visible ? matches >= 2 : matches == 0,
                "HUD marker pixels mismatch for " + fixture.name() + ": " + matches);
        if (!visible) return;
        var field = QuestHudRenderer.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        var lines = (List<?>) field.get(null);
        require(lines.size() == 1, "Unexpected HUD lines");
        var line = lines.getFirst();
        require(recordValue(line, "trackingStyle").equals(fixture.resolvedStyle()),
                "HUD ignored tracking style override");
        require(recordValue(line, "trackingImage").equals(fixture.trackedImage()),
                "HUD used the cover or non-tracked image");
    }

    private static Object recordValue(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static int countColor(Minecraft game, int x, int y, int width, int height, int color) {
        int matches = 0;
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            double sx = image.getWidth() / (double) game.getWindow().getGuiScaledWidth();
            double sy = image.getHeight() / (double) game.getWindow().getGuiScaledHeight();
            for (int yy = y; yy < y + height; yy++) for (int xx = x; xx < x + width; xx++) {
                int abgr = image.getPixelRGBA((int) ((xx + 0.5) * sx), (int) ((yy + 0.5) * sy));
                int rgb = (abgr & 255) << 16 | abgr & 0xFF00 | abgr >>> 16 & 255;
                if (Math.abs((rgb >>> 16 & 255) - (color >>> 16 & 255)) <= 2
                        && Math.abs((rgb >>> 8 & 255) - (color >>> 8 & 255)) <= 2
                        && Math.abs((rgb & 255) - (color & 255)) <= 2) matches++;
            }
        }
        return matches;
    }

    private static void resize(Minecraft game, int width, int height) {
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), width, height);
        game.resizeDisplay();
    }

    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
