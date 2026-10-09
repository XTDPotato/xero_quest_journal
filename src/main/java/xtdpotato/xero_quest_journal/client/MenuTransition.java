package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** Retains only the menu surface while its owner removes the interactive menu on close. */
final class MenuTransition implements AutoCloseable {
    private final UiMotion motion = new UiMotion(false, System.nanoTime());
    private final UiCompositor compositor = new UiCompositor();
    private Object current;
    private AnimatedQuestScreen.Viewport lastBounds;
    private int surfaceWidth, surfaceHeight;
    private int scrim;

    void render(GuiGraphics graphics, int width, int height, Object key,
                AnimatedQuestScreen.Viewport bounds, int scrimColor, Runnable draw) {
        long now = System.nanoTime();
        var preferences = ClientPreferences.get();
        int duration = preferences.reducedMotion || AnimatedQuestScreen.animationMillis() == 0
                ? 0 : preferences.popupAnimationMillis;
        if (!Objects.equals(current, key)) {
            if (key != null && current != null) motion.target(false, now, 0);
            motion.target(key != null, now, duration);
            current = key;
        }
        motion.advance(now, duration);
        if (key != null) {
            lastBounds = bounds;
            surfaceWidth = width;
            surfaceHeight = height;
            scrim = scrimColor;
            compositor.capture(graphics, draw);
        }
        if (motion.position() == 0) {
            compositor.close();
            lastBounds = null;
            return;
        }
        if (lastBounds == null || width != surfaceWidth || height != surfaceHeight) {
            close();
            return;
        }
        var transform = preferences.popupAnimation.sample(motion.eased());
        if ((scrim >>> 24) != 0) {
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                    net.minecraft.client.Minecraft.ON_OSX);
            graphics.fill(0, 0, width, height,
                    (scrim & 0xFFFFFF) | Math.round((scrim >>> 24) * (float) motion.eased()) << 24);
        }
        compositor.transformed(graphics, width, height, lastBounds.left(), lastBounds.top(),
                lastBounds.right(), lastBounds.bottom(), transform.scale(), transform.x(), transform.y(),
                transform.rotation(), transform.opacity());
    }

    boolean transitioning() { return !motion.settled(); }
    double position() { return motion.position(); }

    @Override public void close() {
        compositor.close();
        motion.target(false, System.nanoTime(), 0);
        current = null;
        lastBounds = null;
    }
}
