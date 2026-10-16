package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestTrackingStyle;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

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
        return markerAnimation.play(questId, System.nanoTime());
    }

    public static boolean replayTrackedCoordinateAnimation() {
        String id = trackedId();
        return id != null && playCoordinateTrackAnimation(id);
    }

    public static void acceptLocatedQuest(
            xtdpotato.xero_quest_journal.core.network.QuestNetwork.LocatedQuestPayload payload) {
        QuestLocation location = QuestLocation.parse(payload.location());
        if (location != null && location.kind().equals("coords") && location.resolved())
            locatedLocations.put(payload.questId(), location);
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
            long now = System.nanoTime();
            boolean reduced = ClientPreferences.get().reducedMotion;
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
                graphics.pose().translate(markerX - size / 2.0 - 9, markerY + size / 2.0 - 7, 0);
                graphics.pose().translate(7, 7, 0);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
                graphics.pose().translate(-7, -7, 0);
                JournalNavButton.drawIcon(graphics, "back", 0, 0, 14, 0xFFF5F6F8);
                graphics.pose().popPose();
            }
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
