package xtdpotato.xero_quest_journal.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestListAccent;
import xtdpotato.xero_quest_journal.core.model.QuestTrackingStyle;

/** Top-left, type-accented banner for a newly unlocked task. */
public final class QuestUnlockToast {
    private static final long DURATION_NANOS = 5_000_000_000L;
    private QuestUnlockToast() {}

    public static void enqueue(String questId) {
        QuestHudToastQueue.enqueue(QuestHudToastQueue.Type.UNLOCK, questId);
    }

    public static String currentQuestId() {
        return QuestHudToastQueue.current(QuestHudToastQueue.Type.UNLOCK);
    }

    public static boolean isPresenting() {
        return QuestHudToastQueue.has(QuestHudToastQueue.Type.UNLOCK);
    }

    public static void clear() {
        QuestHudToastQueue.clear();
    }

    public static void clearUnlockNotices() { QuestHudToastQueue.clear(QuestHudToastQueue.Type.UNLOCK); }

    public static void dismissCurrent() {
        QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.UNLOCK);
    }

    public static void dismiss(boolean all) {
        if (all) clearUnlockNotices();
        else dismissCurrent();
    }

    public static boolean openCurrent() {
        String id = currentQuestId();
        if (id.isBlank() || QuestScreen.questById(id) == null) return false;
        Minecraft game = Minecraft.getInstance();
        QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.UNLOCK, id);
        game.setScreen(QuestScreen.task(game.screen, id));
        return true;
    }

    public static boolean hasTask(String questId) {
        return QuestHudToastQueue.has(QuestHudToastQueue.Type.UNLOCK, questId);
    }

    public static boolean dismissTask(String questId) {
        return QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.UNLOCK, questId);
    }

    public static float opacity(long elapsedNanos) {
        if (elapsedNanos < 0 || elapsedNanos >= DURATION_NANOS) return 0;
        long animationNanos = animationNanos();
        long fadeStart = DURATION_NANOS - animationNanos;
        if (elapsedNanos < animationNanos) return smooth((float) elapsedNanos / animationNanos);
        if (elapsedNanos < fadeStart) return 1;
        return 1 - Math.clamp((float) (elapsedNanos - fadeStart) / animationNanos, 0, 1);
    }

    static long animationNanos() {
        return ClientPreferences.get().popupAnimationMillis * 1_000_000L;
    }

    static PopupAnimationProfile.Transform taskToastTransform(float enter, float exit, boolean reduced) {
        if (reduced) return new PopupAnimationProfile.Transform(1, 0, 0, 0, 1);
        return ClientPreferences.get().taskToastAnimation.sample(exit > 0 ? 1 - exit : enter);
    }

    public static void trackCurrent() {
        String id = QuestHudRenderer.trackedId();
        if (id == null || id.isBlank()) return;
        QuestDefinition quest = ClientQuestDefinitions.find(id);
        if (quest == null) return;

        var destination = QuestHudRenderer.location(quest);
        if (destination != null) {
            if (destination.kind().equals("coords") && destination.resolved())
                QuestHudRenderer.playCoordinateTrackAnimation(id);
            return;
        }
        Minecraft.getInstance().setScreen(QuestScreen.task(null, id));
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void render(GuiGraphics graphics) {
        String current = QuestHudToastQueue.current(QuestHudToastQueue.Type.UNLOCK);
        if (current.isBlank()) return;
        QuestDefinition quest = ClientQuestDefinitions.find(current);
        Minecraft game = Minecraft.getInstance();
        if (quest == null || game.player == null) {
            QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.UNLOCK);
            return;
        }

        long elapsed = QuestHudToastQueue.elapsed(QuestHudToastQueue.Type.UNLOCK);
        ClientPreferences preferences = ClientPreferences.get();
        boolean reduced = preferences.reducedMotion;
        long animationNanos = animationNanos();
        long fadeStart = DURATION_NANOS - animationNanos;
        float alpha = reduced
                ? elapsed < fadeStart ? 1
                : 1 - Math.clamp((float) (elapsed - fadeStart) / animationNanos, 0, 1)
                : opacity(elapsed);
        if (alpha <= 0) return;

        float enter = Math.clamp((float) elapsed / animationNanos, 0, 1);
        float exit = reduced ? 0 : Math.clamp((float) (elapsed - fadeStart) / animationNanos, 0, 1);
        int screenWidth = game.getWindow().getGuiScaledWidth();
        int screenHeight = game.getWindow().getGuiScaledHeight();
        int x = 22 - Math.round(exit * exit * 16);
        int y = 20;
        int accent = accent(quest);
        float pulse = reduced ? 0 : (float) ((Math.sin(elapsed / 60_000_000.0) + 1) * .5);
        var geometry = NoticeGeometry.task(screenWidth, preferences.taskToastScalePercent,
                preferences.taskToastTextScalePercent, preferences.taskToastIconScalePercent);
        float toastScale = geometry.scale();
        float iconScale = preferences.taskToastIconScalePercent / 100F;
        int panelWidth = geometry.width();
        int panelHeight = Math.round(geometry.height() * toastScale);
        int initialLeft = Math.round(x * (1 - toastScale));
        int initialTop = Math.round(y - 12 * toastScale);
        var animation = taskToastTransform(enter, exit, reduced);
        var desired = HudOverlayLayout.atPercent(screenWidth, screenHeight,
                Math.round(panelWidth * toastScale), panelHeight,
                preferences.taskToastXPercent, preferences.taskToastYPercent, 8);
        String trackedId = QuestHudRenderer.trackedId();
        boolean overlaysTrackedTask = trackedId != null && !trackedId.isBlank();
        HudOverlayLayout.Rect panel;
        if (overlaysTrackedTask && QuestHudRenderer.hudRect() != null) {
            var hud = QuestHudRenderer.hudRect();
            panelWidth = Math.max(1, Math.min(panelWidth, Math.round(hud.width() / toastScale)));
            int height = Math.min(panelHeight, Math.max(1, screenHeight - hud.y() - 8));
            panel = new HudOverlayLayout.Rect(hud.x(), hud.y(),
                    Math.round(panelWidth * toastScale), height);
        } else {
            panel = HudOverlayLayout.place(screenWidth, screenHeight, desired,
                    HudOverlayLayout.cornerFromPercent(preferences.taskToastXPercent,
                            preferences.taskToastYPercent), 8);
        }
        int offsetX = panel.x() - initialLeft;
        int offsetY = panel.y() - initialTop;
        double panelCenterX = panel.x() + panel.width() / 2.0;
        double panelCenterY = panel.y() + panel.height() / 2.0;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1010);
        graphics.pose().translate(panelCenterX + animation.x(), panelCenterY + animation.y(), 0);
        if (animation.rotation() != 0)
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) animation.rotation()));
        graphics.pose().scale((float) animation.scale(), (float) animation.scale(), 1);
        graphics.pose().translate(-panelCenterX, -panelCenterY, 0);
        graphics.pose().translate(offsetX, offsetY, 0);
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(toastScale, toastScale, 1);
        graphics.pose().translate(-x, -y, 0);
        alpha *= (float) animation.opacity();
        if (preferences.taskToastBackgroundEnabled)
            backing(graphics, 0, y - 12, panelWidth, y - 12 + geometry.height(),
                    alpha * preferences.taskToastBackgroundOpacityPercent / 100F);

        int centerX = x + 24;
        int centerY = y + 23;
        int outer = 42 + Math.round(pulse * 4);
        float stamp = reduced ? 1 : easeOutBack(segment(enter, 0, .4F));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(iconScale / toastScale, iconScale / toastScale, 1);
        graphics.pose().translate(-centerX, -centerY, 0);
        float scale = 1.6F - .6F * stamp;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-centerX, -centerY, 0);
        String questIcon = quest.icon();
        if (!questIcon.isBlank() && QuestIcons.valid(questIcon)) {
            QuestIcons.render(graphics, questIcon, centerX - outer / 2, centerY - outer / 2,
                    outer, outer, alpha);
        } else {
            QuestTrackingIcons.render(graphics,
                    QuestTrackingStyle.resolve(quest.trailingIcon().trackingStyle(), quest.type()),
                    true, centerX - outer / 2, centerY - outer / 2, outer, alpha, reduced);
        }
        // White flash over the emblem while it lands.
        float flash = reduced ? 0 : 1 - segment(enter, .05F, .35F);
        if (flash > 0) drawDiamond(graphics, centerX, centerY, outer + 4, fade(0xFFFFFFFF, alpha * flash * .55F));
        graphics.pose().popPose();
        if (!reduced) for (int wave = 0; wave < 2; wave++) {
            float progress = segment(enter, .08F + wave * .16F, .7F);
            if (progress <= 0 || progress >= 1) continue;
            int size = outer + 8 + Math.round(easeOutCubic(progress) * 34);
            diamondOutline(graphics, centerX, centerY, size, 2, fade(0xFFFFF2C8, alpha * (1 - progress) * .9F));
        }
        graphics.pose().popPose();

        float textScale = geometry.textScale();
        int iconRight = Math.round(centerX + 58 * iconScale / toastScale);
        int textX = Math.max(x + geometry.textX(), iconRight + 14);
        int maxTextWidth = Math.max(1, Math.min(330,
                Math.round((panelWidth - textX - 16) / textScale)));
        int trailY = y + geometry.titleY() + Math.round(10 * textScale) + 3;
        int trailStart = textX;
        int trailEnd = Math.min(screenWidth - 14, trailStart + maxTextWidth);
        float grow = reduced ? 1 : easeOutCubic(segment(enter, .2F, .75F)) * (1 - exit * .6F);
        int trailWidth = Math.round(Math.max(0, trailEnd - trailStart) * grow);
        for (int i = 0; i < 24 && trailWidth > 0; i++) {
            int left = trailStart + trailWidth * i / 24;
            int right = trailStart + trailWidth * (i + 1) / 24;
            float strength = (1 - i / 24F) * alpha;
            int color = fade(accent, strength * .72F);
            graphics.fill(left, trailY, Math.max(left + 1, right), trailY + 2, color);
            if (i % 2 == 0) graphics.fill(left, trailY - 1, Math.max(left + 1, right), trailY,
                    fade(0xFFFFE29A, strength * .32F));
        }
        if (!reduced && grow > 0 && grow < .98F) {
            // Bright head gliding at the growing end of the trail.
            int head = trailStart + trailWidth;
            graphics.fill(head - 6, trailY - 1, head, trailY + 3, fade(0xFFFFF4D6, alpha * .5F));
            drawDiamond(graphics, head, trailY + 1, 6, fade(0xFFFFFFFF, alpha * .9F));
        }

        Component label = Component.translatable("screen.xero_quest_journal.unlock_toast.label");
        Component title = QuestText.title(quest);
        String labelText = game.font.plainSubstrByWidth(label.getString(), maxTextWidth);
        String titleText = game.font.plainSubstrByWidth(title.getString(), maxTextWidth);
        int titleY = y + geometry.titleY();
        int hintY = y + geometry.hintY();
        float labelIn = reduced ? 1 : easeOutCubic(segment(enter, .14F, .4F));
        float titleIn = reduced ? 1 : easeOutCubic(segment(enter, .24F, .42F));
        float hintIn = reduced ? 1 : easeOutCubic(segment(enter, .36F, .42F));
        if (labelIn > 0 && !overlaysTrackedTask) {
            graphics.pose().pushPose();
            graphics.pose().translate(textX - Math.round((1 - labelIn) * 18), y + 4, 0);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(game.font, labelText, 0, 0, fade(accent, alpha * labelIn), false);
            graphics.pose().popPose();
        }
        if (titleIn > 0) {
            // The title reads larger, as the quest name does in the reference notice.
            graphics.pose().pushPose();
            graphics.pose().translate(textX - (1 - titleIn) * 24, titleY, 0);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(game.font, titleText, 0, 0, fade(0xFFF7F8FC, alpha * titleIn), true);
            graphics.pose().popPose();
        }
        String key = QuestKeyMappings.OPEN.getTranslatedKeyMessage().getString();
        String dismissKey = QuestKeyMappings.DISMISS_NEW_TASK.matches(org.lwjgl.glfw.GLFW.GLFW_KEY_GRAVE_ACCENT, 0)
                ? "~" : QuestKeyMappings.DISMISS_NEW_TASK.getTranslatedKeyMessage().getString();
        Component hint = Component.translatable("screen.xero_quest_journal.unlock_toast.open_hint", key).append("  ")
                .append(Component.translatable("screen.xero_quest_journal.unlock_toast.dismiss_hint", dismissKey));
        if (hintIn > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(textX - Math.round((1 - hintIn) * 18), hintY, 0);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(game.font, game.font.plainSubstrByWidth(hint.getString(), maxTextWidth),
                    0, 0, fade(accent, alpha * .92F * hintIn), false);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    /** Linear progress of a phase that starts at {@code start} seconds and lasts {@code length}. */
    static float segment(float seconds, float start, float length) {
        return Math.clamp((seconds - start) / length, 0, 1);
    }

    static float easeOutCubic(float t) {
        float inverse = 1 - Math.clamp(t, 0, 1);
        return 1 - inverse * inverse * inverse;
    }

    /** Overshoots slightly before settling, for "stamped" emblems. */
    static float easeOutBack(float t) {
        float value = Math.clamp(t, 0, 1) - 1;
        float overshoot = 1.70158F;
        return 1 + (overshoot + 1) * value * value * value + overshoot * value * value;
    }

    /** Dark band that fades to the right and at its top and bottom edges, keeping text legible. */
    static void backing(GuiGraphics graphics, int left, int top, int right, int bottom, float alpha) {
        if (alpha <= 0 || right <= left || bottom - top < 20) return;
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            int x0 = left + (right - left) * i / steps, x1 = left + (right - left) * (i + 1) / steps;
            float strength = alpha * (1 - i / (float) steps);
            int color = Math.clamp(Math.round(strength * 255), 0, 255) << 24 | 0x080A10;
            graphics.fillGradient(x0, top, x1, top + 10, color & 0x00FFFFFF, color);
            graphics.fill(x0, top + 10, x1, bottom - 10, color);
            graphics.fillGradient(x0, bottom - 10, x1, bottom, color, color & 0x00FFFFFF);
        }
    }

    static void diamondOutline(GuiGraphics graphics, int centerX, int centerY, int size, int thickness, int color) {
        if (size <= 0 || color >>> 24 == 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45));
        int half = size / 2;
        graphics.fill(-half, -half, half, -half + thickness, color);
        graphics.fill(-half, half - thickness, half, half, color);
        graphics.fill(-half, -half + thickness, -half + thickness, half - thickness, color);
        graphics.fill(half - thickness, -half + thickness, half, half - thickness, color);
        graphics.pose().popPose();
    }

    private static int accent(QuestDefinition quest) {
        String groupId = ClientQuestDefinitions.groupOf(quest.id());
        String groupAccent = ClientQuestDefinitions.groups().stream().filter(group -> group.id().equals(groupId))
                .map(xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group::listAccent)
                .findFirst().orElse("auto");
        return switch (QuestListAccent.resolve(quest.listAccent(), groupAccent, quest.type(), !groupId.isBlank())) {
            case "gold" -> 0xFFF2C94C;
            case "blue" -> 0xFF78B8F2;
            default -> switch (QuestDefinition.normalizeType(quest.type())) {
                case "legendary" -> 0xFFC18AFF;
                case "commission" -> 0xFF55D8C1;
                case "world" -> 0xFF79B8F2;
                default -> 0xFFD6DEE9;
            };
        };
    }

    private static int darken(int color) {
        int red = (color >>> 16 & 255) / 3;
        int green = (color >>> 8 & 255) / 3;
        int blue = (color & 255) / 3;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int fade(int color, float alpha) {
        return color & 0x00FFFFFF | Math.clamp(Math.round((color >>> 24) * alpha), 0, 255) << 24;
    }

    private static void drawDiamond(GuiGraphics graphics, int centerX, int centerY, int size, int color) {
        if (size <= 0 || color >>> 24 == 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45));
        int half = size / 2;
        graphics.fill(-half, -half, half, half, color);
        graphics.pose().popPose();
    }
}
