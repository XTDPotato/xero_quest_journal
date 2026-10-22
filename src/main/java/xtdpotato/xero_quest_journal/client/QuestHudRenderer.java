package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestTrackingStyle;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;
import java.util.List;

/**
 * Genshin-style tracking HUD: type icon, the current objective with a fading gold underline and a
 * yellow distance / progress line. No background panel; text keeps a shadow for legibility.
 */
public final class QuestHudRenderer {
    private static final UiMotion motion = new UiMotion(false, System.nanoTime());
    private static final UiCompositor compositor = new UiCompositor();
    private static final CoordinateTrackAnimation markerAnimation = new CoordinateTrackAnimation();
    private static final java.util.Map<String, QuestLocation> locatedLocations = new java.util.LinkedHashMap<>();
    private static final double BEACON_MIN_DISTANCE = 20;
    private static final double BEACON_MAX_DISTANCE = 80;
    private static final long STEP_ENTER_NANOS = 650_000_000L;
    private static final long STEP_SWEEP_NANOS = 1_300_000_000L;
    private static java.util.List<HudLine> snapshot = java.util.List.of();
    private static java.util.List<MapMarker> markerSnapshot = java.util.List.of();
    private record HudLine(String trackingStyle, String trackingImage, float markerAlpha,
                           Component title, Component detail, Component bottom,
                           boolean completed, String distance, long stepStarted) {}
    private record MapMarker(String questId, String trackingStyle, String image, float alpha,
                             QuestLocation location) {}
    private QuestHudRenderer() {
    }

    public static void track(String id) {
        ClientQuestProgress.toggleTracking(id).thenAccept(result -> {
            if (result.equals("tracked")) playCoordinateTrackAnimation(id);
        });
    }

    public static void clear() {
        CoordinateGuidance.clear();
        for (String id : ClientQuestProgress.trackedIds()) {
            ClientQuestProgress.toggleTracking(id);
        }
    }

    public static String trackedId() {
        return ClientQuestProgress.trackedIds().stream().findFirst().orElse(null);
    }

    public static boolean playCoordinateTrackAnimation(String questId) {
        QuestDefinition quest = ClientQuestDefinitions.find(questId);
        QuestLocation target = quest == null ? null : location(quest);
        if (target == null || !target.kind().equals("coords") || !target.resolved()) return false;
        CoordinateGuidance.start(questId);
        return markerAnimation.play(questId, System.nanoTime());
    }

    public static boolean replayTrackedCoordinateAnimation() {
        String id = trackedId();
        return id != null && playCoordinateTrackAnimation(id);
    }

    public static void clearCoordinateGuidance() {
        CoordinateGuidance.clear();
        locatedLocations.clear();
    }

    public static void acceptLocatedQuest(
            xtdpotato.xero_quest_journal.core.network.QuestNetwork.LocatedQuestPayload payload) {
        QuestLocation location = QuestLocation.parse(payload.location());
        if (location != null && location.kind().equals("coords") && location.resolved())
            locatedLocations.put(payload.questId(), location);
        if (ClientQuestProgress.isTracked(payload.questId())) playCoordinateTrackAnimation(payload.questId());
    }

    public static QuestLocation locatedLocation(String questId) {
        return locatedLocations.get(questId);
    }

    public static float coordinateTrackProgress(String questId, long now) {
        return markerAnimation.progress(questId, now);
    }

    public static boolean beaconVisible(double distance) {
        return distance > BEACON_MIN_DISTANCE && distance <= BEACON_MAX_DISTANCE;
    }

    public static void render(GuiGraphics graphics) {
        var game = Minecraft.getInstance();
        if (game.player == null) {
            snapshot = java.util.List.of();
            markerSnapshot = java.util.List.of();
            motion.target(false, System.nanoTime(), 0);
            compositor.close();
            return;
        }
        var lines = new java.util.ArrayList<HudLine>();
        var markers = new java.util.ArrayList<MapMarker>();
        for (String trackedId : ClientQuestProgress.trackedIds()) {
            QuestDefinition quest = QuestScreen.questById(trackedId);
            if (quest == null) {
                continue;
            }
            var view = ClientQuestProgress.view(trackedId);
            QuestLocation location = location(quest);
            boolean sameDimension = location != null && game.level != null
                    && (location.dimension().isBlank()
                    || location.dimension().equals(game.level.dimension().location().toString()));
            if (location != null && location.resolved() && sameDimension) {
                markers.add(new MapMarker(trackedId, QuestTrackingStyle.resolve(
                        quest.trailingIcon().trackingStyle(), quest.type()),
                        quest.trailingIcon().image(true), quest.trailingIcon().alpha(), location));
            }
            String distance = "";
            if (location != null && location.resolved() && sameDimension) {
                double dx = location.x() - game.player.getX();
                double dy = location.y() - game.player.getY();
                double dz = location.z() - game.player.getZ();
                distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
            }
            Component detail;
            if (location != null && !sameDimension && !location.dimension().isBlank()) {
                detail = Component.translatable("hud.xero_quest_journal.go_to_dimension",
                        QuestText.dimensionName(location.dimension()));
            } else if (view != null && view.required() > 1 && !view.completed()) {
                detail = Component.literal("(" + view.current() + "/" + view.required() + ")");
            } else if (location != null && !location.resolved() && distance.isEmpty()) {
                detail = QuestText.placeName(location);
            } else {
                detail = Component.empty();
            }
            if (view != null && view.stepCount() > 1 && !view.completed()) {
                Component stage = Component.translatable("hud.xero_quest_journal.step",
                        view.step() + 1, view.stepCount());
                detail = detail.getString().isEmpty() ? stage : stage.copy().append("  ").append(detail);
            }
            String trackingStyle = QuestTrackingStyle.resolve(
                    quest.trailingIcon().trackingStyle(), quest.type());
            lines.add(new HudLine(trackingStyle, quest.trailingIcon().image(true),
                    quest.trailingIcon().alpha(), QuestText.objective(quest), detail,
                    QuestText.title(quest), false, distance, ClientQuestProgress.stepNoticeStarted(trackedId)));
        }
        boolean keepBehind = game.screen instanceof AnimatedQuestScreen animated
                && animated.showsTrackingHudBehind();
        boolean visible = (!lines.isEmpty() || !markers.isEmpty())
                && ClientPreferences.get().trackingHud && (game.screen == null || keepBehind)
                && !QuestHudToastQueue.hasAny();
        if (visible) {
            snapshot = java.util.List.copyOf(lines);
            markerSnapshot = java.util.List.copyOf(markers);
        }
        motion.target(visible, System.nanoTime(), AnimatedQuestScreen.animationMillis());
        if (motion.position() == 0) {
            compositor.close();
            if (!visible) {
                snapshot = java.util.List.of();
                markerSnapshot = java.util.List.of();
            }
            return;
        }
        Runnable draw = () -> {
            int y = 42;
            int screenWidth = game.getWindow().getGuiScaledWidth();
            int screenHeight = game.getWindow().getGuiScaledHeight();
            long now = System.nanoTime();
            boolean reduced = ClientPreferences.get().reducedMotion;
            int blockHeight = Math.max(26, snapshot.stream()
                    .mapToInt(line -> line.completed() ? 24 : 34).sum());
            var block = HudOverlayLayout.place(screenWidth, screenHeight,
                    new HudOverlayLayout.Rect(0, y, Math.min(360, Math.max(120, screenWidth - 16)), blockHeight),
                    HudOverlayLayout.Corner.TOP_LEFT, 8);
            graphics.pose().pushPose();
            graphics.pose().translate(block.x(), block.y() - y, 0);
            for (var line : snapshot) {
                int textX = 40;
                int maxWidth = Math.max(1, Math.min(240, screenWidth - textX - 16));
                if (line.completed()) QuestStatusIcons.render(graphics, "claimed", 18, y, 16);
                else if (!line.trackingImage().isBlank())
                    QuestIcons.render(graphics, line.trackingImage(), 18, y, 16, 16, line.markerAlpha());
                else QuestTrackingIcons.render(graphics, line.trackingStyle(), true, 18, y, 16,
                        line.markerAlpha(), reduced);
                long sinceStep = line.stepStarted() == 0 || reduced ? Long.MAX_VALUE : now - line.stepStarted();
                float enter = sinceStep >= STEP_ENTER_NANOS ? 1 : smooth(sinceStep / (float) STEP_ENTER_NANOS);
                int shift = Math.round((1 - enter) * 12);
                var objective = Language.getInstance().getVisualOrder(
                        game.font.substrByWidth(line.title(), maxWidth));
                int textWidth = Math.min(maxWidth, game.font.width(line.title()));
                graphics.drawString(game.font, objective, textX + shift, y + 1,
                        fade(line.completed() ? 0xFF79D69A : 0xFFFFFFFF, enter), true);
                underline(graphics, textX, y + 12, textWidth + 8, enter,
                        sinceStep < STEP_SWEEP_NANOS ? sinceStep / (float) STEP_SWEEP_NANOS : -1);
                if (!line.completed()) {
                    int infoX = textX;
                    if (!line.distance().isEmpty()) {
                        graphics.drawString(game.font, line.distance(), infoX, y + 16, 0xFFFFE07A, true);
                        infoX += game.font.width(line.distance()) + 6;
                    }
                    if (!line.detail().getString().isBlank()) {
                        graphics.drawString(game.font, Language.getInstance().getVisualOrder(
                                game.font.substrByWidth(line.detail(), Math.max(1, maxWidth - (infoX - textX)))),
                                infoX, y + 16, line.distance().isEmpty() ? 0xFFFFE07A : 0xFFD8DEE8, true);
                    }
                }
                y += line.completed() ? 24 : 34;
            }
            graphics.pose().popPose();
            renderLocationMarkers(graphics, markerSnapshot, game);
        };
        if (motion.settled()) {
            compositor.close();
            draw.run();
        } else {
            compositor.capture(graphics, draw);
            compositor.band(graphics, game.getWindow().getGuiScaledWidth(), game.getWindow().getGuiScaledHeight(),
                    0, game.getWindow().getGuiScaledHeight(), -32 * (1 - motion.eased()), motion.eased());
        }
    }

    /** Thin gold rule under the objective that fades out to the right; {@code sweep} adds a moving glint. */
    private static void underline(GuiGraphics graphics, int x, int y, int width, float alpha, float sweep) {
        int segments = Math.max(1, width / 3);
        for (int i = 0; i < segments; i++) {
            int left = x + width * i / segments;
            int right = x + width * (i + 1) / segments;
            float strength = 1 - (float) i / segments;
            strength = strength * strength;
            graphics.fill(left, y, Math.max(left + 1, right), y + 1, fade(0xFFE8C06A, alpha * (.25F + .75F * strength)));
        }
        if (sweep >= 0 && sweep <= 1) {
            int center = x + Math.round((width + 24) * sweep) - 12;
            for (int offset = -12; offset <= 12; offset++) {
                float glow = 1 - Math.abs(offset) / 12F;
                int px = center + offset;
                if (px < x || px >= x + width) continue;
                graphics.fill(px, y - 1, px + 1, y + 2, fade(0xFFFFF4C8, alpha * glow * glow));
            }
        }
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static int fade(int color, float alpha) {
        return color & 0x00FFFFFF | Math.clamp(Math.round((color >>> 24) * alpha), 0, 255) << 24;
    }

    /** Location of the task's current objective (the current step for multi-step tasks). */
    static QuestLocation location(QuestDefinition quest) {
        return locatedLocations.getOrDefault(quest.id(), QuestText.location(quest));
    }

    private static void renderLocationMarkers(GuiGraphics graphics, java.util.List<MapMarker> markers,
                                              Minecraft game) {
        if (game.player == null || game.level == null) return;
        int width = game.getWindow().getGuiScaledWidth();
        int height = game.getWindow().getGuiScaledHeight();
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        var camera = game.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return;

        for (MapMarker marker : markers) {
            QuestLocation target = marker.location();
            double dx = target.x() - game.player.getX();
            double dy = target.y() - game.player.getY();
            double dz = target.z() - game.player.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            renderCoordinateGuidance(graphics, marker.questId(), camera, width, height, game);
            var projection = QuestMarkerProjection.project(camera,
                    new net.minecraft.world.phys.Vec3(target.x(), target.y(), target.z()),
                    width, height, game.options.fov().get());
            boolean onScreen = projection.onScreen();
            int markerX = projection.x(), markerY = projection.y();
            int baseSize = onScreen ? 34 : 30;
            int size = Math.max(8, Math.round(baseSize
                    * ClientPreferences.get().trackingMarkerScalePercent / 100F));
            int x = markerX - size / 2;
            int y = markerY - size / 2;
            if (onScreen && beaconVisible(distance))
                renderBeaconBeam(graphics, markerX, markerY, height);
            long now = System.nanoTime();
            float trackProgress = markerAnimation.progress(marker.questId(), now);
            if (trackProgress > 0) renderTrackAnimation(graphics, markerX, markerY, size, trackProgress);
            float iconScale = trackProgress > 0
                    ? .18F + .82F * QuestUnlockToast.easeOutBack(
                            Math.clamp(trackProgress / .28F, 0, 1))
                    : 1;
            if (iconScale != 1) {
                graphics.pose().pushPose();
                graphics.pose().translate(markerX, markerY, 0);
                graphics.pose().scale(iconScale, iconScale, 1);
                graphics.pose().translate(-markerX, -markerY, 0);
            }
            if (!marker.image().isBlank() && QuestIcons.valid(marker.image()))
                QuestIcons.render(graphics, marker.image(), x, y, size, size, marker.alpha());
            else QuestTrackingIcons.render(graphics, marker.trackingStyle(), true, x, y, size,
                    marker.alpha(), ClientPreferences.get().reducedMotion);
            if (iconScale != 1) graphics.pose().popPose();
            if (onScreen) {
                String label = Math.round(distance) + "m";
                int labelY = Math.min(height - game.font.lineHeight - 4, markerY + size / 2 + 3);
                graphics.drawCenteredString(game.font, Component.literal(label), markerX, labelY, 0xFFFFFFFF);
            } else {
                double rayX = markerX - centerX, rayY = markerY - centerY;
                float angle = (float) Math.toDegrees(Math.atan2(rayY, rayX)) - 180;
                graphics.pose().pushPose();
                graphics.pose().translate(markerX - size / 2.0 - 2, markerY + size / 2.0, 0);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
                graphics.pose().scale(1.5F, 1.5F, 1);
                graphics.drawCenteredString(game.font, Component.literal("<"),
                        0, -game.font.lineHeight / 2, 0xFFF5F6F8);
                graphics.pose().popPose();
            }
        }
    }

    private static void renderCoordinateGuidance(GuiGraphics graphics, String questId,
                                                 net.minecraft.client.Camera camera, int width, int height,
                                                 Minecraft game) {
        if (!CoordinateGuidance.active(questId)) return;
        List<CoordinateRoute.Cell> route = CoordinateGuidance.route();
        if (route.size() < 2) return;
        int stride = Math.max(1, route.size() / 42);
        long now = System.nanoTime();
        int sample = 0;
        int offscreenSample = 0;
        QuestMarkerProjection.Marker previousOnScreen = null;
        for (int index = Math.max(1, stride); index < route.size(); index += stride) {
            var cell = route.get(index);
            var point = cell.center().add(0, .12, 0);
            var projection = QuestMarkerProjection.project(camera, point, width, height, game.options.fov().get());
            if (projection.onScreen() && previousOnScreen != null) {
                int segment = Math.abs(projection.x() - previousOnScreen.x())
                        + Math.abs(projection.y() - previousOnScreen.y());
                if (segment <= 72) drawGuidanceSegment(graphics, previousOnScreen.x(), previousOnScreen.y(),
                        projection.x(), projection.y(), fade(0xFFFFD45A, .42F));
            }
            previousOnScreen = projection.onScreen() ? projection : null;
            double distance = point.distanceTo(game.player.position());
            float depthAlpha = Math.max(.42F, 1F - (float) distance / 220F);
            float twinkle = (float) ((Math.sin(now / 180_000_000.0 + sample * 1.73) + 1) * .5);
            float pulse = .5F + twinkle * .5F;
            int radius = 3 + Math.round(twinkle * (sample % 4 == 0 ? 5 : 3));
            int screenX = projection.x(), screenY = projection.y();
            boolean major = sample % 4 == 0;
            if (!projection.onScreen()) {
                if (offscreenSample >= 7) { sample++; continue; }
                double rayX = screenX - width / 2.0, rayY = screenY - height / 2.0;
                double rayLength = Math.max(1, Math.hypot(rayX, rayY));
                double inset = 24 + offscreenSample++ * 16;
                screenX -= (int) Math.round(rayX / rayLength * inset);
                screenY -= (int) Math.round(rayY / rayLength * inset);
                if (screenX < 8 || screenX >= width - 8 || screenY < 8 || screenY >= height - 8) {
                    sample++;
                    continue;
                }
                depthAlpha = Math.max(depthAlpha, .82F);
                radius = Math.min(radius + 2, 7);
                major = offscreenSample == 1 || offscreenSample == 4;
            }
            drawGuidanceStar(graphics, screenX, screenY, radius, depthAlpha * pulse, major);
            sample++;
        }
    }

    private static void drawGuidanceSegment(GuiGraphics graphics, int fromX, int fromY,
                                            int toX, int toY, int color) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        if (steps <= 0) return;
        for (int index = 0; index <= steps; index++) {
            if ((index & 1) != 0) continue;
            int x = fromX + (toX - fromX) * index / steps;
            int y = fromY + (toY - fromY) * index / steps;
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawGuidanceStar(GuiGraphics graphics, int x, int y,
                                         int radius, float alpha, boolean major) {
        int glow = fade(0xFFFFD86B, alpha * (major ? .25F : .13F));
        int gold = fade(0xFFFFD45A, alpha * (major ? .9F : .62F));
        int core = fade(0xFFFFF4C6, alpha);
        graphics.fill(x - radius * 2, y - 1, x + radius * 2 + 1, y + 2, glow);
        graphics.fill(x - 1, y - radius * 2, x + 2, y + radius * 2 + 1, glow);
        graphics.fill(x - radius, y, x + radius + 1, y + 1, gold);
        graphics.fill(x, y - radius, x + 1, y + radius + 1, gold);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, core);
        if (major) {
            graphics.fill(x - radius * 2 - 1, y, x - radius - 1, y + 1, gold);
            graphics.fill(x + radius + 1, y, x + radius * 2 + 1, y + 1, gold);
            graphics.fill(x, y - radius * 2 - 1, x + 1, y - radius - 1, gold);
            graphics.fill(x, y + radius + 1, x + 1, y + radius * 2 + 1, gold);
        }
    }

    private static void renderBeaconBeam(GuiGraphics graphics, int x, int markerY, int screenHeight) {
        int bottom = Math.clamp(markerY, 0, screenHeight);
        if (bottom <= 0) return;
        graphics.fill(x - 3, 0, x + 4, bottom, 0x40FFD45E);
        graphics.fill(x - 1, 0, x + 1, bottom, 0x80FFE7A0);
    }

    private static void renderTrackAnimation(GuiGraphics graphics, int x, int y, int markerSize, float progress) {
        for (int wave = 0; wave < 2; wave++) {
            float phase = (progress - wave * .14F) / .86F;
            if (phase < 0 || phase >= 1) continue;
            float eased = QuestUnlockToast.easeOutCubic(phase);
            int diameter = markerSize + 10 + Math.round(eased * 46);
            float alpha = (1 - phase) * (1 - phase) * .88F;
            QuestUnlockToast.diamondOutline(graphics, x, y, diameter, 2,
                    fade(0xFFFFE9A3, alpha));
        }
    }
}
