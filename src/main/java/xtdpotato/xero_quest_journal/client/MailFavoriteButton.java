package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/** Circular mailbox favorite control with a native renderer for the editable animation source. */
public final class MailFavoriteButton extends StyledButton {
    private static final float LOADING_END = .72F;
    private boolean starred;
    private boolean targetStarred;
    private boolean animationFromStarred;
    private long animationStart;

    public MailFavoriteButton(int x, int y, int size, boolean starred, OnPress action,
                              ButtonStyleSheet styles) {
        super(x, y, size, size, Component.empty(), action, styles, "mail-star", "mail-favorite");
        this.starred = starred;
        this.targetStarred = starred;
        this.animationFromStarred = starred;
        defaultCss("""
                .mail-favorite { background-color:transparent; border-width:0px; color:#ffffff; opacity:1; }
                """);
        hoverMotion();
    }

    @Override protected String motionShape() { return "circle"; }

    public void copyAnimationFrom(MailFavoriteButton previous) {
        if (previous == null) return;
        copyMotionFrom(previous);
        starred = previous.starred;
        targetStarred = previous.targetStarred;
        animationFromStarred = previous.animationFromStarred;
        animationStart = previous.animationStart;
    }

    public void setStarred(boolean value) {
        starred = value;
        targetStarred = value;
        animationFromStarred = value;
    }

    public boolean targetStarred() { return targetStarred; }

    public boolean animationPlaying() {
        if (animationStart <= 0) return false;
        return progress(MailboxAnimationStore.style(targetStarred ? "favorite" : "unfavorite")) < 1;
    }

    public void trigger(boolean value) {
        animationFromStarred = starred;
        starred = value;
        targetStarred = value;
        animationStart = System.nanoTime();
    }

    @Override
    protected void renderSurface(GuiGraphics graphics, ButtonStyle style, int background, int border) {
        MailboxAnimationStore.Style animation = MailboxAnimationStore.style(targetStarred ? "favorite" : "unfavorite");
        int color = active && pressed()
                ? MailboxAnimationStore.style("press").buttonPressedColor()
                : active && isHovered() ? MailboxAnimationStore.style("hover").buttonHoverColor() : animation.buttonColor();
        circle(graphics, getX(), getY(), getWidth(), color);
        if (style.borderWidth() > 0) {
            ring(graphics, getX() + 1, getY() + 1, getWidth() - 2, animation.buttonBorderColor(),
                    Math.min(2, style.borderWidth()));
        }
        float progress = progress(animation);
        if (progress <= 0 || progress >= 1) return;
        if (progress < LOADING_END
                && MailboxAnimationStore.hasPart(targetStarred ? "favorite" : "unfavorite", "dots"))
            renderDots(graphics, animation, progress);
        if (rippleActive(progress, targetStarred)
                && MailboxAnimationStore.hasPart("favorite", "ripple"))
            renderRipple(graphics, animation, progress);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, ButtonStyle style, int text) {
        MailboxAnimationStore.Style animation = MailboxAnimationStore.style(targetStarred ? "favorite" : "unfavorite");
        if (!MailboxAnimationStore.hasPart(targetStarred ? "favorite" : "unfavorite", "star")) return;
        float progress = progress(animation);
        if (progress > 0 && progress < LOADING_END) return;
        float transition = transitionProgress(progress);
        float pulse = transition > 0 && transition < 1 ? (float) Math.sin(transition * Math.PI) : 0;
        float scale = 1.0F + (animation.starScale() - 1.0F) * pulse;
        int size = Math.max(6, Math.round(Math.min(getWidth(), getHeight()) * .48F * scale));
        int centerX = getX() + getWidth() / 2;
        int centerY = getY() + getHeight() / 2;
        boolean filled = transition < .5F ? animationFromStarred : targetStarred;
        int oldColor = animationFromStarred ? animation.starColor() : animation.starEmptyColor();
        int newColor = targetStarred ? animation.starColor() : animation.starEmptyColor();
        int color = progress > 0 && progress < 1
                ? blend(oldColor, newColor, easeInOut(transition)) : newColor;
        drawStar(graphics, centerX, centerY, size / 2.0F, size * .21F, color, filled);
    }

    private float progress(MailboxAnimationStore.Style animation) {
        if (animationStart <= 0) return 0;
        if (animation.durationMillis() <= 0) return 1;
        float value = Math.clamp((System.nanoTime() - animationStart) / (animation.durationMillis() * 1_000_000.0F),
                0.0F, 1.0F);
        return value;
    }

    private void renderDots(GuiGraphics graphics, MailboxAnimationStore.Style animation, float progress) {
        float loading = Math.clamp(progress / LOADING_END, 0, 1);
        int count = Math.clamp(animation.dotCount(), 0, 3);
        int dotSize = Math.max(2, getWidth() / 7);
        int spacing = dotSize + Math.max(1, getWidth() / 14);
        int firstX = getX() + getWidth() / 2 - spacing;
        int centerY = getY() + getHeight() / 2;
        for (int index = 0; index < count; index++) {
            float pulse = loadingDotProgress(progress, index);
            float brightness = pulse > 0 ? .16F + .84F * pulse : .16F;
            int alpha = Math.round(255 * brightness);
            int x = firstX + index * spacing;
            circle(graphics, x - dotSize / 2, centerY - dotSize / 2, dotSize,
                    withAlpha(animation.dotColor(), alpha));
        }
    }

    private void renderRipple(GuiGraphics graphics, MailboxAnimationStore.Style animation, float progress) {
        float rippleProgress = Math.clamp((progress - LOADING_END) / (1 - LOADING_END), 0, 1);
        float eased = easeOut(rippleProgress);
        int diameter = Math.max(2, Math.round(getWidth() * (0.18F + animation.rippleScale() * .82F * eased)));
        int centerX = getX() + getWidth() / 2;
        int centerY = getBottom() - 2;
        int alpha = Math.round(180 * (1 - eased));
        ring(graphics, centerX - diameter / 2, centerY - diameter / 2, diameter,
                withAlpha(animation.rippleColor(), alpha), Math.max(1, Math.round(2 * (1 - eased))));
    }

    static float transitionProgress(float progress) {
        return Math.clamp((progress - LOADING_END) / (1 - LOADING_END), 0, 1);
    }

    static float loadingDotProgress(float progress, int dotIndex) {
        return Math.clamp(progress / LOADING_END * 3 - dotIndex, 0, 1);
    }

    static boolean rippleActive(float progress, boolean targetStarred) {
        return targetStarred && progress > LOADING_END && progress < 1;
    }

    private static float easeOut(float value) {
        float inverse = 1 - value;
        return 1 - inverse * inverse * inverse;
    }

    private static float easeInOut(float value) {
        return value * value * (3 - 2 * value);
    }

    private static void circle(GuiGraphics graphics, int x, int y, int diameter, int color) {
        if (diameter <= 0 || (color >>> 24) == 0) return;
        MailDecor.circle(graphics, x, y, diameter, color);
    }

    private static void ring(GuiGraphics graphics, int x, int y, int diameter, int color, int width) {
        if (diameter <= 0 || (color >>> 24) == 0) return;
        for (int edge = 0; edge < width; edge++) {
            int current = diameter - edge * 2;
            if (current <= 0) return;
            drawCircleOutline(graphics, x + edge, y + edge, current, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics graphics, int x, int y, int diameter, int color) {
        double radius = diameter / 2.0;
        int centerX = x + diameter / 2;
        int centerY = y + diameter / 2;
        int previousX = centerX + diameter / 2;
        int previousY = centerY;
        for (int step = 1; step <= 72; step++) {
            double angle = step * Math.PI * 2 / 72.0;
            int currentX = centerX + (int) Math.round(Math.cos(angle) * radius);
            int currentY = centerY + (int) Math.round(Math.sin(angle) * radius);
            line(graphics, previousX, previousY, currentX, currentY, color);
            previousX = currentX;
            previousY = currentY;
        }
    }

    private static void drawStar(GuiGraphics graphics, int centerX, int centerY, float outer,
                                 float inner, int color, boolean filled) {
        float[] xs = new float[10], ys = new float[10];
        for (int index = 0; index < 10; index++) {
            double angle = -Math.PI / 2 + index * Math.PI / 5;
            float radius = index % 2 == 0 ? outer : inner;
            xs[index] = centerX + (float) Math.cos(angle) * radius;
            ys[index] = centerY + (float) Math.sin(angle) * radius;
        }
        if (filled) {
            int top = Math.max(0, (int) Math.floor(centerY - outer));
            int bottom = (int) Math.ceil(centerY + outer);
            for (int y = top; y <= bottom; y++) {
                ArrayList<Float> intersections = new ArrayList<>();
                for (int index = 0; index < 10; index++) {
                    int next = (index + 1) % 10;
                    if ((ys[index] <= y && ys[next] > y) || (ys[next] <= y && ys[index] > y)) {
                        float x = xs[index] + (y - ys[index]) * (xs[next] - xs[index])
                                / (ys[next] - ys[index]);
                        intersections.add(x);
                    }
                }
                intersections.sort(Float::compare);
                for (int index = 0; index + 1 < intersections.size(); index += 2) {
                    graphics.fill(Math.round(intersections.get(index)), y,
                            Math.round(intersections.get(index + 1)) + 1, y + 1, color);
                }
            }
        } else {
            for (int index = 0; index < 10; index++) {
                int next = (index + 1) % 10;
                line(graphics, Math.round(xs[index]), Math.round(ys[index]),
                        Math.round(xs[next]), Math.round(ys[next]), color);
            }
        }
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static int blend(int from, int to, float amount) {
        int a = Math.round(((from >>> 24) & 255) * (1 - amount) + ((to >>> 24) & 255) * amount);
        int r = Math.round(((from >>> 16) & 255) * (1 - amount) + ((to >>> 16) & 255) * amount);
        int g = Math.round(((from >>> 8) & 255) * (1 - amount) + ((to >>> 8) & 255) * amount);
        int b = Math.round((from & 255) * (1 - amount) + (to & 255) * amount);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }
}
