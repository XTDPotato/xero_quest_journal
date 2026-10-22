package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

/** Transparent upper-left completion banner shown only while the HUD is active. */
public final class QuestCompletionToast {
    private static final long DURATION_NANOS = 4_200_000_000L;

    private QuestCompletionToast() {}

    public static void enqueue(String questId) {
        QuestHudToastQueue.enqueue(QuestHudToastQueue.Type.COMPLETION, questId);
    }

    public static boolean visible() {
        return !QuestHudToastQueue.current(QuestHudToastQueue.Type.COMPLETION).isBlank();
    }

    public static void clear() {
        QuestHudToastQueue.clear();
    }

    public static float opacity(long elapsedNanos) {
        if (elapsedNanos < 0 || elapsedNanos >= DURATION_NANOS) return 0;
        long animationNanos = QuestUnlockToast.animationNanos();
        long fadeStart = DURATION_NANOS - animationNanos;
        if (elapsedNanos < animationNanos) return smooth((float) elapsedNanos / animationNanos);
        if (elapsedNanos < fadeStart) return 1;
        return 1 - Math.clamp((float) (elapsedNanos - fadeStart) / animationNanos, 0, 1);
    }

    private static float smooth(float value) {
        float bounded = Math.clamp(value, 0, 1);
        return bounded * bounded * (3 - 2 * bounded);
    }

    public static void render(GuiGraphics graphics) {
        String id = QuestHudToastQueue.current(QuestHudToastQueue.Type.COMPLETION);
        if (id.isBlank()) return;
        Minecraft game = Minecraft.getInstance();
        QuestDefinition quest = QuestScreen.questById(id);
        if (quest == null) {
            QuestHudToastQueue.dismiss(QuestHudToastQueue.Type.COMPLETION);
            return;
        }
        long elapsed = QuestHudToastQueue.elapsed(QuestHudToastQueue.Type.COMPLETION);
        ClientPreferences preferences = ClientPreferences.get();
        long animationNanos = QuestUnlockToast.animationNanos();
        long fadeStart = DURATION_NANOS - animationNanos;
        float alpha = opacity(elapsed);
        if (alpha <= 0) return;

        boolean reduced = preferences.reducedMotion;
        float enter = Math.clamp((float) elapsed / animationNanos, 0, 1);
        float exit = reduced ? 0 : Math.clamp((float) (elapsed - fadeStart) / animationNanos, 0, 1);
        int screenWidth = game.getWindow().getGuiScaledWidth();
        int screenHeight = game.getWindow().getGuiScaledHeight();
        int x = 26 - Math.round(exit * exit * 16)
                - (reduced ? 0 : Math.round((1 - smooth(enter)) * 12));
        int y = 16;
        int iconSize = 42;
        var geometry = NoticeGeometry.task(screenWidth, preferences.taskToastScalePercent,
                preferences.taskToastTextScalePercent, preferences.taskToastIconScalePercent);
        float toastScale = geometry.scale();
        float iconScale = preferences.taskToastIconScalePercent / 100F;
        float textScale = preferences.taskToastTextScalePercent / 100F;
        int iconSlot = Math.max(iconSize, Math.round(iconSize * iconScale / toastScale));
        int titleX = Math.max(x + iconSlot + 16,
                x + iconSize / 2 + Math.round(38 * iconScale / toastScale) + 12);
        int panelWidth = geometry.width();
        int internalTextScale = Math.max(1, Math.round(textScale / toastScale * 100));
        int maxTextWidth = Math.max(1, Math.min(320,
                (panelWidth - titleX - 24) * 100 / internalTextScale));
        int panelHeight = Math.round(geometry.height() * toastScale);
        int desiredLeft = Math.round(8 + Math.max(0, screenWidth - panelWidth * toastScale - 16)
                * preferences.taskToastXPercent / 100F);
        int desiredTop = Math.round(8 + Math.max(0, screenHeight - panelHeight - 16)
                * preferences.taskToastYPercent / 100F);
        int initialLeft = Math.round(x * (1 - toastScale));
        int initialTop = Math.round(y - 10 * toastScale);
        var animation = QuestUnlockToast.taskToastTransform(enter, exit, reduced);
        var panel = HudOverlayLayout.place(screenWidth, screenHeight,
                new HudOverlayLayout.Rect(desiredLeft, desiredTop,
                        Math.round(panelWidth * toastScale), panelHeight),
                HudOverlayLayout.cornerFromPercent(preferences.taskToastXPercent, preferences.taskToastYPercent), 8);
        int shiftX = panel.x() - initialLeft;
        int shiftY = panel.y() - initialTop;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1010);
        double panelCenterX = panel.x() + panel.width() / 2.0;
        double panelCenterY = panel.y() + panel.height() / 2.0;
        graphics.pose().translate(panelCenterX + animation.x(), panelCenterY + animation.y(), 0);
        if (animation.rotation() != 0)
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) animation.rotation()));
        graphics.pose().scale((float) animation.scale(), (float) animation.scale(), 1);
        graphics.pose().translate(-panelCenterX, -panelCenterY, 0);
        graphics.pose().translate(shiftX, shiftY, 0);
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(toastScale, toastScale, 1);
        graphics.pose().translate(-x, -y, 0);
        alpha *= (float) animation.opacity();
        if (preferences.taskToastBackgroundEnabled)
            QuestUnlockToast.backing(graphics, 0, y - 10, panelWidth, y - 10 + geometry.height(),
                    alpha * preferences.taskToastBackgroundOpacityPercent / 100F);
        int centerX = x + iconSize / 2, centerY = y + iconSize / 2;
        float stamp = reduced ? 1 : QuestUnlockToast.easeOutBack(QuestUnlockToast.segment(enter, 0, .38F));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(iconScale / toastScale, iconScale / toastScale, 1);
        graphics.pose().translate(-centerX, -centerY, 0);
        float scale = 1.5F - .5F * stamp;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-centerX, -centerY, 0);
        QuestStatusIcons.render(graphics, "complete", x, y, iconSize, false, alpha);
        graphics.pose().popPose();
        if (!reduced) {
            float ring = QuestUnlockToast.segment(enter, .1F, .65F);
            if (ring > 0 && ring < 1)
                QuestUnlockToast.diamondOutline(graphics, centerX, centerY,
                        iconSize + Math.round(QuestUnlockToast.easeOutCubic(ring) * 30), 2,
                        fade(0xFFFFF0B8, alpha * (1 - ring) * .9F));
        }
        graphics.pose().popPose();

        String title = game.font.plainSubstrByWidth(QuestText.title(quest).getString(), maxTextWidth);
        Component completed = Component.translatable("screen.xero_quest_journal.completion_toast.title");
        String completedText = game.font.plainSubstrByWidth(completed.getString(), maxTextWidth);
        float titleIn = reduced ? 1 : QuestUnlockToast.easeOutCubic(QuestUnlockToast.segment(enter, .12F, .42F));
        float labelIn = reduced ? 1 : QuestUnlockToast.easeOutCubic(QuestUnlockToast.segment(enter, .26F, .42F));
        int titleY = y + 3;
        float localTextScale = textScale / toastScale;
        int ruleY = Math.max(y + 17, titleY + Math.round(10 * localTextScale) + 2);
        int labelY = ruleY + 4;
        if (titleIn > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(titleX - Math.round((1 - titleIn) * 20), titleY, 0);
            graphics.pose().scale(textScale / toastScale, textScale / toastScale, 1);
            graphics.drawString(game.font, title, 0, 0, fade(0xFFF5F1E9, alpha * titleIn), true);
            graphics.pose().popPose();
        }
        // Gold rule between the name and the status line.
        float grow = reduced ? 1 : QuestUnlockToast.easeOutCubic(QuestUnlockToast.segment(enter, .2F, .6F));
        int ruleWidth = Math.round(Math.min(maxTextWidth, Math.max(game.font.width(title), 80) + 24) * grow);
        for (int i = 0; i < 16 && ruleWidth > 0; i++) {
            int left = titleX + ruleWidth * i / 16, right = titleX + ruleWidth * (i + 1) / 16;
            graphics.fill(left, ruleY, Math.max(left + 1, right), ruleY + 1,
                    fade(0xFFF2D29A, alpha * (1 - i / 16F) * .85F));
        }
        if (labelIn > 0) {
            // Letters start slightly spread and close in as the line arrives.
            int spread = Math.round((1 - labelIn) * 4);
            int letterX = titleX - Math.round((1 - labelIn) * 14);
            int color = fade(0xFF68E65F, alpha * labelIn);
            for (int index = 0; index < completedText.length(); ) {
                int code = completedText.codePointAt(index);
                String letter = new String(Character.toChars(code));
                graphics.pose().pushPose();
                graphics.pose().translate(letterX, labelY, 0);
                graphics.pose().scale(textScale / toastScale, textScale / toastScale, 1);
                graphics.drawString(game.font, letter, 0, 0, color, true);
                graphics.pose().popPose();
                letterX += Math.round(game.font.width(letter) * localTextScale) + spread;
                index += Character.charCount(code);
            }
        }
        if (!reduced) {
            // A soft sheen sweeps once across the text block after it has settled.
            float sweep = QuestUnlockToast.segment(enter, .7F, .3F);
            if (sweep > 0 && sweep < 1) {
                int band = 22;
                int sweepX = titleX - band + Math.round((maxTextWidth + band) * sweep);
                graphics.enableScissor(titleX, y, titleX + maxTextWidth, y + 38);
                for (int i = 0; i < band; i++) {
                    float strength = 1 - Math.abs(i - band / 2F) / (band / 2F);
                    graphics.fill(sweepX + i, y, sweepX + i + 1, y + 38,
                            fade(0xFFFFFFFF, alpha * strength * .18F));
                }
                graphics.disableScissor();
            }
        }
        graphics.pose().popPose();
    }

    private static int fade(int color, float alpha) {
        return (color & 0x00FFFFFF)
                | Math.clamp(Math.round((color >>> 24) * alpha), 0, 255) << 24;
    }
}
