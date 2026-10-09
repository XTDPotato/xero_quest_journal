package xtdpotato.xero_quest_journal.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestListAccent;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

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

    public static void dismissCurrent() {
        QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.UNLOCK);
    }

    public static boolean hasTask(String questId) {
        return QuestHudToastQueue.has(QuestHudToastQueue.Type.UNLOCK, questId);
    }

    public static boolean dismissTask(String questId) {
        return QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.UNLOCK, questId);
    }

    /** Dismisses only the displayed unlock notice; queued notices remain intact. */
    public static boolean dismissAt(double mouseX, double mouseY) {
        String current = currentQuestId();
        if (current.isBlank()) return false;
        var bounds = closeBounds();
        if (mouseX < bounds[0] || mouseX >= bounds[2] || mouseY < bounds[1] || mouseY >= bounds[3])
            return false;
        QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.UNLOCK, current);
        return true;
    }

    private static double[] closeBounds() {
        Minecraft game = Minecraft.getInstance();
        int sw = game.getWindow().getGuiScaledWidth(), sh = game.getWindow().getGuiScaledHeight();
        float scale = ClientPreferences.get().taskToastScalePercent / 100F;
        int y = Math.max(74, Math.min(sh - 94, sh / 5));
        double screenRight = 22 + (Math.min(sw, 430) - 22) * scale;
        double centerX = Math.min(sw - 12, screenRight - 12 * scale);
        double centerY = y - 3 * scale;
        return new double[]{centerX - 9 * scale, centerY - 9 * scale,
                centerX + 9 * scale, centerY + 9 * scale};
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

    public static void trackCurrent() {
        String id = currentQuestId();
        if (id.isBlank()) return;
        QuestDefinition quest = ClientQuestDefinitions.find(id);
        if (quest == null) {
            dismissCurrent();
            return;
        }
        if (quest.trackable() && location(quest) != null) {
            ClientQuestProgress.toggleTracking(id);
            dismissCurrent();
        } else {
            Minecraft game = Minecraft.getInstance();
            dismissCurrent();
            game.setScreen(QuestScreen.task(game.screen, id));
        }
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static QuestLocation location(QuestDefinition quest) {
        for (var trigger : quest.triggers()) {
            QuestLocation result = location(trigger);
            if (result != null) return result;
        }
        return null;
    }

    private static QuestLocation location(QuestDefinition.Trigger trigger) {
        if (trigger.type().equals("location")) {
            QuestLocation target = QuestLocation.parse(trigger.item());
            if (target != null) return target;
        }
        for (var child : trigger.children()) {
            QuestLocation result = location(child);
            if (result != null) return result;
        }
        return null;
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
        int y = Math.max(74, Math.min(screenHeight - 94, screenHeight / 5));
        int accent = accent(quest);
        float pulse = reduced ? 0 : (float) ((Math.sin(elapsed / 60_000_000.0) + 1) * .5);
        float toastScale = preferences.taskToastScalePercent / 100F;
        float iconScale = preferences.taskToastIconScalePercent / 100F;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1010);
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(toastScale, toastScale, 1);
        graphics.pose().translate(-x, -y, 0);
        backing(graphics, 0, y - 12, Math.min(screenWidth, 430), y + 78, alpha * .5F);

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
        drawDiamond(graphics, centerX, centerY, outer + 14, fade(accent, alpha * .38F));
        drawDiamond(graphics, centerX, centerY, outer + 6, fade(accent, alpha * .75F));
        drawDiamond(graphics, centerX, centerY, outer, fade(darken(accent), alpha));
        drawDiamond(graphics, centerX, centerY, Math.round(outer * .70F), fade(accent, alpha));
        drawDiamond(graphics, centerX, centerY, Math.max(6, Math.round(outer * .24F)),
                fade(0xFFFFE879, alpha));
        int markColor = fade(0xFF5A3712, alpha);
        graphics.fill(centerX - 2, centerY - 7, centerX + 2, centerY + 4, markColor);
        graphics.fill(centerX - 2, centerY + 7, centerX + 2, centerY + 11, markColor);
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

        int iconSlot = Math.max(42, Math.round(42 * iconScale / toastScale));
        int textX = x + 16 + iconSlot;
        int maxTextWidth = Math.max(1, Math.min(330, screenWidth - textX - 16));
        int trailY = y + 40;
        int trailEnd = Math.min(screenWidth - 14, textX + maxTextWidth);
        float grow = reduced ? 1 : easeOutCubic(segment(enter, .2F, .75F)) * (1 - exit * .6F);
        int trailWidth = Math.round(Math.max(0, trailEnd - centerX - 14) * grow);
        for (int i = 0; i < 24 && trailWidth > 0; i++) {
            int left = centerX + 14 + trailWidth * i / 24;
            int right = centerX + 14 + trailWidth * (i + 1) / 24;
            float strength = (1 - i / 24F) * alpha;
            int color = fade(accent, strength * .72F);
            graphics.fill(left, trailY, Math.max(left + 1, right), trailY + 2, color);
            if (i % 2 == 0) graphics.fill(left, trailY - 1, Math.max(left + 1, right), trailY,
                    fade(0xFFFFE29A, strength * .32F));
        }
        if (!reduced && grow > 0 && grow < .98F) {
            // Bright head gliding at the growing end of the trail.
            int head = centerX + 14 + trailWidth;
            graphics.fill(head - 6, trailY - 1, head, trailY + 3, fade(0xFFFFF4D6, alpha * .5F));
            drawDiamond(graphics, head, trailY + 1, 6, fade(0xFFFFFFFF, alpha * .9F));
        }

        Component label = Component.translatable("screen.xero_quest_journal.unlock_toast.label");
        Component title = QuestText.title(quest);
        String labelText = game.font.plainSubstrByWidth(label.getString(), maxTextWidth);
        String titleText = game.font.plainSubstrByWidth(title.getString(), maxTextWidth);
        float labelIn = reduced ? 1 : easeOutCubic(segment(enter, .14F, .4F));
        float titleIn = reduced ? 1 : easeOutCubic(segment(enter, .24F, .42F));
        float hintIn = reduced ? 1 : easeOutCubic(segment(enter, .36F, .42F));
        if (labelIn > 0) graphics.drawString(game.font, labelText, textX - Math.round((1 - labelIn) * 18), y + 4,
                fade(accent, alpha * labelIn), false);
        if (titleIn > 0) {
            // The title reads larger, as the quest name does in the reference notice.
            graphics.pose().pushPose();
            graphics.pose().translate(textX - (1 - titleIn) * 24, y + 24, 0);
            float titleScale = Math.min(1.35F, maxTextWidth / (float) Math.max(1, game.font.width(titleText)));
            graphics.pose().scale(Math.max(1, titleScale), Math.max(1, titleScale), 1);
            graphics.drawString(game.font, titleText, 0, 0, fade(0xFFF7F8FC, alpha * titleIn), true);
            graphics.pose().popPose();
        }
        String key = QuestKeyMappings.TRACK_TASK.getTranslatedKeyMessage().getString();
        Component hint = Component.translatable(quest.trackable() && location(quest) != null
                ? "screen.xero_quest_journal.unlock_toast.track_hint"
                : "screen.xero_quest_journal.unlock_toast.open_hint", key);
        if (hintIn > 0) graphics.drawString(game.font, game.font.plainSubstrByWidth(hint.getString(), maxTextWidth),
                textX - Math.round((1 - hintIn) * 18), y + 53, fade(accent, alpha * .92F * hintIn), false);
        int screenMouseX = (int) Math.round(game.mouseHandler.xpos() * screenWidth
                / (double) Math.max(1, game.getWindow().getScreenWidth()));
        int screenMouseY = (int) Math.round(game.mouseHandler.ypos() * screenHeight
                / (double) Math.max(1, game.getWindow().getScreenHeight()));
        double[] close = closeBounds();
        boolean hoveredClose = screenMouseX >= close[0] && screenMouseX < close[2]
                && screenMouseY >= close[1] && screenMouseY < close[3];
        float toastScaleForClose = preferences.taskToastScalePercent / 100F;
        double closeX = x + (close[0] + close[2]) / 2.0 / toastScaleForClose - x;
        double closeY = y + (close[1] + close[3]) / 2.0 / toastScaleForClose - y;
        int closeCenterX = (int) Math.round(closeX), closeCenterY = (int) Math.round(closeY);
        MailDecor.circle(graphics, closeCenterX - 9, closeCenterY - 9, 18,
                fade(hoveredClose ? 0xFF4E5662 : 0xFF29313B, alpha * .9F));
        JournalNavButton.drawIcon(graphics, "close", closeCenterX - 6, closeCenterY - 6, 12,
                fade(0xFFF5F1E9, alpha));
        if (hoveredClose) graphics.renderTooltip(game.font,
                Component.translatable("screen.xero_quest_journal.unlock_toast.dismiss"),
                screenMouseX, screenMouseY);
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
