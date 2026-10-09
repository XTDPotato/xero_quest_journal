package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/** Keeps vanilla cursor/selection semantics while easing wheel and cursor-driven scrolling. */
public class SmoothTextBox extends MultiLineEditBox {
    private final SmoothScroll motion = new SmoothScroll();
    private boolean draggingThumb;
    private double grab;

    public SmoothTextBox(Font font, int x, int y, int width, int height,
                         Component message, Component placeholder) {
        super(font, x, y, width, height, message, placeholder);
    }

    public final void advanceScroll() {
        motion.limit(getMaxScrollAmount());
        motion.advance();
        super.setScrollAmount(motion.position());
    }
    @Override protected void setScrollAmount(double value) {
        if (motion == null) { super.setScrollAmount(value); return; }
        motion.limit(getMaxScrollAmount());
        motion.to(value);
    }
    @Override public void renderWidget(GuiGraphics g, int x, int y, float delta) {
        advanceScroll();
        super.renderWidget(g, x, y, delta);
    }
    private int thumbHeight() {
        return Math.min(height, Math.max(12, (int) (height * height / (double) (height + getMaxScrollAmount()))));
    }
    private int thumbY() {
        return getY() + (int) Math.round(scrollAmount() / Math.max(1, getMaxScrollAmount())
                * (height - thumbHeight()));
    }
    @Override protected void renderDecorations(GuiGraphics g) {
        super.renderDecorations(g);
        // Reserve the rail inside the field so viewport clipping cannot hide it.
        g.fill(getRight() - 4, getY() + 1, getRight() - 1, getBottom() - 1, 0xFF303741);
        g.fill(getRight() - 4, thumbY() + 1, getRight() - 1,
                Math.min(getBottom() - 1, thumbY() + thumbHeight()),
                getMaxScrollAmount() > 0 ? 0xFFABB8C8 : 0xFF59616B);
    }
    @Override protected boolean scrollbarVisible() { return false; }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (!visible || !active || !isMouseOver(x, y)) return false;
        motion.limit(getMaxScrollAmount());
        motion.wheel(v * 27);
        return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (visible && active && button == 0 && isMouseOver(x, y) && x >= getRight() - 8) {
            motion.limit(getMaxScrollAmount());
            draggingThumb = y >= thumbY() && y < thumbY() + thumbHeight();
            grab = draggingThumb ? y - thumbY() : thumbHeight() / 2.;
            if (!draggingThumb) motion.to(valueAt(y));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    private double valueAt(double y) {
        return (y - getY() - grab) * getMaxScrollAmount() / Math.max(1, height - thumbHeight());
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingThumb) {
            motion.jump(valueAt(y));
            super.setScrollAmount(motion.position());
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingThumb) { draggingThumb = false; return true; }
        return super.mouseReleased(x, y, button);
    }
}
