package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.ClientPreferences;
import xtdpotato.xero_quest_journal.client.ClientQuestProgress;
import xtdpotato.xero_quest_journal.client.ClientControlState;
import xtdpotato.xero_quest_journal.client.CoordinateTrackAnimation;
import xtdpotato.xero_quest_journal.client.CoordinateRoute;
import xtdpotato.xero_quest_journal.client.CoordinateGuidance;
import xtdpotato.xero_quest_journal.client.JournalListButton;
import xtdpotato.xero_quest_journal.client.QuestHudRenderer;
import xtdpotato.xero_quest_journal.client.QuestIcons;
import xtdpotato.xero_quest_journal.client.QuestUnlockToast;
import xtdpotato.xero_quest_journal.client.QuestMarkerProjection;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.ItemPickupToast;
import xtdpotato.xero_quest_journal.client.HudOverlayLayout;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.QuestGraphContext;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;
import xtdpotato.xero_quest_journal.core.runtime.QuestTravelActions;
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
    private static int originalMarkerScale;
    private static volatile String locatedQuestId = "";
    private static volatile String locatedPosition = "";
    private static boolean locatedMarkerVerified;

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
                verifyCoordinateAnimationCooldown();
                verifyHudOverlayCollisionLayout();
                require(game.level != null
                                && !(game.getSingleplayerServer().overworld().getChunkSource().getGenerator()
                                instanceof net.minecraft.world.level.levelgen.FlatLevelSource),
                        "Coordinate locate experiment did not create a normal terrain world");
                originalMotion = ClientPreferences.get().reducedMotion;
                originalHud = ClientPreferences.get().trackingHud;
                originalMarkerScale = ClientPreferences.get().trackingMarkerScalePercent;
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
                    if (index == 0 && !locatedMarkerVerified) {
                        var quest = QuestRepository.find(fixture.id());
                        require(quest != null, "Locate experiment quest is missing");
                        var locate = new TriggerGraph.Node("locate", "locate_structure", 0, 0,
                                Map.of("dimension", player.level().dimension().location().toString(),
                                        "target", "minecraft:village_plains", "radius", "100",
                                        "storeVariable", "globe.located_structure"));
                        try (var context = new QuestGraphContext(Map.of())) {
                            require(QuestTravelActions.execute(player, quest, "hud_locate_button", locate,
                                            "button", "activate"),
                                    "Real village locate failed in the new normal terrain world");
                            locatedQuestId = quest.id();
                            locatedPosition = String.join("|",
                                    QuestGraphContext.get("locatedX"), QuestGraphContext.get("locatedY"),
                                    QuestGraphContext.get("locatedZ"), QuestGraphContext.get("locatedDimension"));
                            var stored = new Gson().fromJson(
                                    xtdpotato.xero_quest_journal.core.runtime.QuestControlData.get(player)
                                            .variable(player, "globe.located_structure"),
                                    com.google.gson.JsonObject.class);
                            require(stored.get("kind").getAsString().equals("structure")
                                            && stored.get("target").getAsString().equals("minecraft:village_plains")
                                            && stored.get("dimension").getAsString().equals(partsafe(locatedPosition, 3))
                                            && Math.abs(stored.get("x").getAsDouble()
                                            - Double.parseDouble(partsafe(locatedPosition, 0))) < .001
                                            && Math.abs(stored.get("y").getAsDouble()
                                            - Double.parseDouble(partsafe(locatedPosition, 1))) < .001
                                            && Math.abs(stored.get("z").getAsDouble()
                                            - Double.parseDouble(partsafe(locatedPosition, 2))) < .001,
                                    "Shared locate variable did not preserve structure and coordinates");
                            var sharedState = xtdpotato.xero_quest_journal.core.runtime.QuestControlData.get(player);
                            require(sharedState.variable(player, "globe.located_structure_dimension")
                                            .equals(partsafe(locatedPosition, 3))
                                            && sharedState.variable(player, "globe.located_structure_id")
                                            .equals("minecraft:village_plains")
                                            && Math.abs(Double.parseDouble(sharedState.variable(player,
                                            "globe.located_structure_x")) - Double.parseDouble(partsafe(locatedPosition, 0))) < .001
                                            && Math.abs(Double.parseDouble(sharedState.variable(player,
                                            "globe.located_structure_y")) - Double.parseDouble(partsafe(locatedPosition, 1))) < .001
                                            && Math.abs(Double.parseDouble(sharedState.variable(player,
                                            "globe.located_structure_z")) - Double.parseDouble(partsafe(locatedPosition, 2))) < .001,
                                    "Dedicated shared structure or coordinate fields were not saved");
                        } catch (Exception failure) {
                            throw new IllegalStateException("Real structure locate experiment failed", failure);
                        }
                    }
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
                if (index == 0 && !locatedMarkerVerified) {
                    QuestLocation location = QuestHudRenderer.locatedLocation(fixture.id());
                    if (location == null) return false;
                    String sharedOnClient = ClientControlState.variable("globe.located_structure");
                    if (sharedOnClient == null || sharedOnClient.isBlank()) return false;
                    var clientStructure = new Gson().fromJson(sharedOnClient, com.google.gson.JsonObject.class);
                    require(clientStructure.get("target").getAsString().equals("minecraft:village_plains")
                                    && clientStructure.get("dimension").getAsString()
                                    .equals(game.level.dimension().location().toString()),
                            "Team-shared locate value did not reach the client variable catalog");
                    String[] parts = locatedPosition.split("\\|", -1);
                    require(locatedQuestId.equals(fixture.id()) && parts.length == 4,
                            "Locate result did not belong to the tracked task");
                    require(location.kind().equals("coords") && location.resolved()
                                    && location.dimension().equals(parts[3])
                                    && location.dimension().equals(game.level.dimension().location().toString())
                                    && Math.abs(location.x() - Double.parseDouble(parts[0])) < .001
                                    && Math.abs(location.y() - Double.parseDouble(parts[1])) < .001
                                    && Math.abs(location.z() - Double.parseDouble(parts[2])) < .001,
                            "Client map marker does not match the real locate coordinates: " + location);
                    QuestUnlockToast.clear();
                    xtdpotato.xero_quest_journal.client.QuestCompletionToast.clear();
                    game.setScreen(null);
                    var markersField = QuestHudRenderer.class.getDeclaredField("markerSnapshot");
                    markersField.setAccessible(true);
                    boolean markerRendered = ((List<?>) markersField.get(null)).stream().anyMatch(marker -> {
                        try {
                            var questField = marker.getClass().getDeclaredMethod("questId");
                            questField.setAccessible(true);
                            return questField.invoke(marker).equals(fixture.id());
                        } catch (ReflectiveOperationException failure) {
                            throw new IllegalStateException(failure);
                        }
                    });
                    if (!markerRendered) return false;
                    require(markerRendered, "Located coordinates were not projected to a tracked HUD marker");
                    QuestUnlockToast.trackCurrent();
                    long animationNow = System.nanoTime();
                    float animationProgress = QuestHudRenderer.coordinateTrackProgress(fixture.id(), animationNow);
                    require(animationProgress > 0 && animationProgress < 1,
                            "Pressing V did not start the located marker animation");
                    CoordinateGuidance.tick(game);
                    var guidanceRoute = CoordinateGuidance.route();
                    boolean alreadyArrived = game.player.position().distanceToSqr(
                            location.x(), location.y(), location.z())
                            <= Math.pow(Math.min(1.75, location.radius()), 2);
                    require(alreadyArrived || guidanceRoute.size() > 1,
                            "Pressing V did not build a walkable coordinate guidance route");
                    for (int routeIndex = 1; routeIndex < guidanceRoute.size(); routeIndex++) {
                        var previous = guidanceRoute.get(routeIndex - 1);
                        var next = guidanceRoute.get(routeIndex);
                        require(Math.abs(previous.x() - next.x()) + Math.abs(previous.z() - next.z()) == 1
                                        && Math.abs(previous.y() - next.y()) <= 3,
                                "Coordinate guidance route stepped through a wall or skipped terrain");
                    }
                    QuestUnlockToast.trackCurrent();
                    require(QuestHudRenderer.coordinateTrackProgress(fixture.id(), System.nanoTime()) < 1,
                            "Repeated V press interrupted/restarted the active marker animation");
                    capture(game, "tracking-hud-real-locate-normal-world");
                    locatedMarkerVerified = true;
                }
                xtdpotato.xero_quest_journal.client.QuestUnlockToast.clear();
                xtdpotato.xero_quest_journal.client.QuestCompletionToast.clear();
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
                game.setScreen(null);
                checkHud(game, FIXTURES.getLast(), true);
                capture(game, "tracking-hud-compact");
                ClientPreferences.get().trackingHud = false;
            }
            case 8 -> {
                game.setScreen(null);
                checkHud(game, FIXTURES.getLast(), false);
                resize(game, 1920, 1080);
                ClientPreferences.get().trackingHud = true;
                ClientPreferences.get().reducedMotion = false;
            }
            case 9 -> {
                game.setScreen(null);
                checkHud(game, FIXTURES.getLast(), true);
            }
            case 10 -> {
                if (!MailBannerTrackingRegression.tick(game)) return false;
                ItemPickupToast.clear();
                for (int picked = 1; picked <= 5; picked++)
                    ItemPickupToast.enqueue(new QuestNetwork.ItemPickupNoticePayload(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND),
                            picked, 70 + picked, 105));
                require(ItemPickupToast.visibleCount() == 4,
                        "Item pickup HUD did not enforce its four-row limit");
                require(ItemPickupToast.newest() != null && ItemPickupToast.newest().pickedCount() == 5
                                && ItemPickupToast.newest().current() == 75,
                        "Item pickup HUD did not prioritize the most recent acquisition");
                game.setScreen(null);
                stage = 11;
                wait = 15;
                return false;
            }
            case 11 -> {
                capture(game, "tracking-hud-item-pickup");
                ItemPickupToast.clear();
                ClientPreferences.get().trackingHud = originalHud;
                ClientPreferences.get().reducedMotion = originalMotion;
                ClientPreferences.get().trackingMarkerScalePercent = originalMarkerScale;
                return true;
            }
            default -> throw new AssertionError("Unknown tracking HUD stage " + stage);
        }
        stage++;
        wait = 15;
        return false;
    }

    private static String partsafe(String encoded, int index) {
        String[] values = encoded.split("\\|", -1);
        if (index < 0 || index >= values.length) throw new AssertionError("Invalid located coordinate tuple");
        return values[index];
    }

    private static void verifyCoordinateAnimationCooldown() {
        var destination = new QuestLocation("coords", "minecraft:overworld", "",
                4.5, 64, .5, .5, true);
        var route = CoordinateRoute.find(new CoordinateRoute.Cell(0, 64, 0), destination, cell -> {
            var next = new java.util.ArrayList<CoordinateRoute.Cell>();
            for (int[] direction : CoordinateRoute.directions()) {
                int x = cell.x() + direction[0], z = cell.z() + direction[1];
                if (x < 0 || x > 4 || z < -2 || z > 2 || x == 2 && z == 0) continue;
                next.add(new CoordinateRoute.Cell(x, 64, z));
            }
            return next;
        }, 200);
        require(route.reached() && route.cells().size() > 5
                        && route.cells().stream().noneMatch(cell -> cell.x() == 2 && cell.z() == 0),
                "Coordinate guidance path cut through a wall instead of routing around it");
        for (int index = 1; index < route.cells().size(); index++) {
            var previous = route.cells().get(index - 1);
            var next = route.cells().get(index);
            require(Math.abs(previous.x() - next.x()) + Math.abs(previous.z() - next.z()) == 1
                            && Math.abs(previous.y() - next.y()) <= 1,
                    "Coordinate guidance route contains a non-walkable jump");
        }
        CoordinateTrackAnimation animation = new CoordinateTrackAnimation();
        require(new ClientPreferences().trackingMarkerScalePercent == 50,
                "Coordinate marker default scale is not 50 percent");
        require(!QuestHudRenderer.beaconVisible(20) && QuestHudRenderer.beaconVisible(20.01)
                        && QuestHudRenderer.beaconVisible(80) && !QuestHudRenderer.beaconVisible(80.01),
                "Beacon marker distance bounds are incorrect");
        ClientPreferences.get().trackingMarkerScalePercent = 125;
        require(ClientPreferences.get().trackingMarkerScalePercent == 125,
                "Coordinate marker scale cannot be customized");
        ClientPreferences.get().trackingMarkerScalePercent = originalMarkerScale;
        long started = 2_000_000_000L;
        require(animation.play("coordinate_task", started), "Coordinate marker animation did not start");
        require(animation.isPlaying(started)
                        && animation.progress("coordinate_task", started) == 0,
                "Coordinate marker animation did not begin at zero");
        require(animation.progress("coordinate_task",
                        started + CoordinateTrackAnimation.DURATION_NANOS / 2) > .49F,
                "Coordinate marker animation did not advance");
        require(!animation.play("another_task",
                        started + CoordinateTrackAnimation.DURATION_NANOS - 1),
                "Coordinate marker animation replayed before the current animation ended");
        require(!animation.isPlaying(started + CoordinateTrackAnimation.DURATION_NANOS)
                        && animation.play("coordinate_task",
                        started + CoordinateTrackAnimation.DURATION_NANOS),
                "Coordinate marker replay did not unlock at the animation end");
    }

    private static void verifyHudOverlayCollisionLayout() {
        for (var mapCorner : HudOverlayLayout.Corner.values()) {
            HudOverlayLayout.clearReservations();
            HudOverlayLayout.reserveMinimapArea(1920, 1080, mapCorner);
            var mapTest = HudOverlayLayout.place(1920, 1080,
                    new HudOverlayLayout.Rect(8, 8, 360, 70), mapCorner, 8);
            var toast = HudOverlayLayout.place(1920, 1080,
                    new HudOverlayLayout.Rect(8, 8, 430, 90), mapCorner, 8);
            var pickups = HudOverlayLayout.place(1920, 1080,
                    new HudOverlayLayout.Rect(8, 8, 760, 265), mapCorner, 8);
            require(HudOverlayLayout.avoids(mapTest, toast) && HudOverlayLayout.avoids(mapTest, pickups)
                            && HudOverlayLayout.avoids(toast, pickups),
                    "HUD overlays did not relocate around minimap corner " + mapCorner);
        }
        HudOverlayLayout.clearReservations();
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
        int screenWidth = game.getWindow().getGuiScaledWidth();
        int screenHeight = game.getWindow().getGuiScaledHeight();
        var preferences = ClientPreferences.get();
        var expected = HudOverlayLayout.atPercent(screenWidth, screenHeight,
                Math.min(360, Math.max(120, screenWidth - 16)), 34,
                preferences.taskToastXPercent, preferences.taskToastYPercent, 8);
        int matches = countColor(game, expected.x() + 18, expected.y(), 16, 16, fixture.color());
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
