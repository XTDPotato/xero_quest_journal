package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared visual-only motion for native buttons and authored canvas controls. */
final class ButtonActionAnimation {
    private float hoverProgress;
    private float liftProgress;
    private long motionTime;
    private long clickStarted;

    float hoverProgress() { return hoverProgress; }
    float liftProgress() { return liftProgress; }
    float scale(float enlargement) { return 1 + enlargement * liftProgress; }

    void update(boolean hovered, boolean pressed) {
        long now = System.nanoTime();
        float blend = AnimatedQuestScreen.animationMillis() == 0 ? 1
                : (float) -Math.expm1(-Math.clamp(motionTime == 0 ? 0 : (now - motionTime) / 1e9, 0, .05) * 18);
        motionTime = now;
        float target = hovered ? 1 : 0;
        hoverProgress += (target - hoverProgress) * blend;
        liftProgress += ((pressed ? 0 : target) - liftProgress) * blend;
    }

    void click() { clickStarted = System.nanoTime(); }

    void copyFrom(ButtonActionAnimation previous) {
        hoverProgress = previous.hoverProgress;
        liftProgress = previous.liftProgress;
        motionTime = previous.motionTime;
        clickStarted = previous.clickStarted;
    }

    void push(GuiGraphics graphics, int x, int y, int width, int height, float enlargement) {
        graphics.pose().pushPose();
        float centerX = x + width / 2f, centerY = y + height / 2f;
        graphics.pose().translate(centerX, centerY, 0);
        float scale = scale(enlargement);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-centerX, -centerY, 0);
    }

    void ripple(GuiGraphics graphics, String action, int x, int y, int width, int height,
                String shape, int cornerRadius, float opacity) {
        if (clickStarted == 0 || width <= 0 || height <= 0) return;
        MailboxAnimationStore.Style style = MailboxAnimationStore.style(action + "_click");
        float fraction = (System.nanoTime() - clickStarted) / (Math.max(1, style.durationMillis()) * 1_000_000F);
        if (fraction >= 1 || AnimatedQuestScreen.animationMillis() == 0) {
            clickStarted = 0;
            return;
        }
        double radius = Math.min(width, height) * style.rippleScale() * fraction / 2.0;
        int alpha = Math.clamp(Math.round(90 * (1 - fraction) * opacity), 0, 255);
        if (alpha < 4) return;
        int color = alpha << 24 | style.rippleColor() & 0xFFFFFF;
        // Clip scanlines to the actual shape, without changing any parent scissor.
        for (int row = 0; row < height; row++) {
            double dy = row + .5 - height / 2.0;
            if (Math.abs(dy) >= radius) continue;
            double halfWidth = Math.sqrt(radius * radius - dy * dy);
            int inset = ControlAppearance.inset(shape, cornerRadius, width, height, row);
            int left = Math.max(inset, (int) Math.ceil(width / 2.0 - halfWidth));
            int right = Math.min(width - inset, (int) Math.floor(width / 2.0 + halfWidth));
            if (right > left) graphics.fill(x + left, y + row, x + right, y + row + 1, color);
        }
    }
}
