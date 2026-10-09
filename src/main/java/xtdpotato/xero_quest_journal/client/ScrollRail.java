package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A persistent rail for pixel-based lists; track clicks ease, thumb drags follow the pointer. */
final class ScrollRail extends AbstractWidget {
    private final SmoothScroll scroll;
    private boolean dragging;
    private double grab;

    ScrollRail(int x, int y, int height, SmoothScroll scroll) {
        super(x, y, 8, Math.max(8, height),
                Component.translatable("screen.xero_quest_journal.journey.element.scroll_thumb"));
        this.scroll = scroll;
    }

    private int thumbHeight() {
        return Math.min(height, Math.max(12, (int) (height * height / (height + scroll.maximum()))));
    }
    private int thumbY() {
        return getY() + (int) Math.round(scroll.position() / Math.max(1, scroll.maximum())
                * (height - thumbHeight()));
    }
    private double valueAt(double y) {
        return (y - getY() - grab) * scroll.maximum() / Math.max(1, height - thumbHeight());
    }

    @Override protected void renderWidget(GuiGraphics g, int x, int y, float delta) {
        g.fill(getX() + 2, getY(), getRight() - 2, getBottom(), 0xFF303741);
        g.fill(getX() + 2, thumbY(), getRight() - 2, thumbY() + thumbHeight(),
                scroll.maximum() <= 0 ? 0xFF59616B : dragging || isHoveredOrFocused() ? 0xFFF3D48B : 0xFFABB8C8);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!visible || !active || button != 0 || !isMouseOver(x, y)) return false;
        if (scroll.maximum() <= 0) return true;
        dragging = y >= thumbY() && y < thumbY() + thumbHeight();
        grab = dragging ? y - thumbY() : thumbHeight() / 2.;
        if (!dragging) scroll.to(valueAt(y));
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !dragging) return false;
        scroll.jump(valueAt(y));
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !dragging) return false;
        dragging = false;
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (!visible || !isMouseOver(x, y)) return false;
        scroll.wheel(v * 28);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll.wheel(28);
            case GLFW.GLFW_KEY_DOWN -> scroll.wheel(-28);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll.wheel(height * .85);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll.wheel(-height * .85);
            case GLFW.GLFW_KEY_HOME -> scroll.to(0);
            case GLFW.GLFW_KEY_END -> scroll.to(scroll.maximum());
            default -> { return false; }
        }
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
