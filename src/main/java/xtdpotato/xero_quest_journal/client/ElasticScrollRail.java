package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;

/** A gallery rail sharing the wheel/pan state instead of maintaining a second scroll offset. */
final class ElasticScrollRail extends AbstractWidget {
    private final ElasticScroll scroll;
    private final boolean vertical;
    private double viewport, grab;
    private boolean held;

    ElasticScrollRail(int x, int y, int length, boolean vertical, ElasticScroll scroll, double viewport) {
        super(x, y, vertical ? 8 : length, vertical ? length : 8,
                Component.translatable("screen.xero_quest_journal.journey.element.scroll_thumb"));
        this.vertical = vertical;
        this.scroll = scroll;
        this.viewport = Math.max(1, viewport);
    }
    void viewport(double value) { viewport = Math.max(1, value); }
    private int length() { return vertical ? height : width; }
    private int origin() { return vertical ? getY() : getX(); }
    private int thumbLength() {
        return Math.min(length(), Math.max(16, (int) (length() * viewport / (viewport + scroll.maximum()))));
    }
    private int thumbStart() {
        return origin() + (int) Math.round(Math.clamp(scroll.position(), 0, scroll.maximum())
                / Math.max(1, scroll.maximum()) * (length() - thumbLength()));
    }
    private double valueAt(double point) {
        return (point - origin() - grab) * scroll.maximum() / Math.max(1, length() - thumbLength());
    }
    @Override protected void renderWidget(GuiGraphics g, int x, int y, float delta) {
        int thumb = thumbStart(), size = thumbLength();
        int color = scroll.maximum() <= 0 ? 0xFF59616B : held || isHoveredOrFocused() ? 0xFFF3D48B : 0xFFABB8C8;
        if (vertical) {
            g.fill(getX() + 2, getY(), getRight() - 2, getBottom(), 0xFF303741);
            g.fill(getX() + 2, thumb, getRight() - 2, thumb + size, color);
        } else {
            g.fill(getX(), getY() + 2, getRight(), getBottom() - 2, 0xFF303741);
            g.fill(thumb, getY() + 2, thumb + size, getBottom() - 2, color);
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!visible || !active || button != 0 || !isMouseOver(x, y)) return false;
        double point = vertical ? y : x;
        held = scroll.maximum() > 0 && point >= thumbStart() && point < thumbStart() + thumbLength();
        grab = held ? point - thumbStart() : thumbLength() / 2.;
        if (!held) scroll.to(valueAt(point));
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !held) return false;
        scroll.jump(valueAt(vertical ? y : x));
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !held) return false;
        held = false;
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
