package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Texture-backed tools with an accessible label rather than font-dependent symbols. */
public final class EditorIconButton extends StyledButton {
    public static final int SIZE = 24, ICON_SIZE = 16, PADDING = 4;
    private final String icon;
    private boolean dark;

    public EditorIconButton(int x, int y, Component label, Runnable action,
                            ButtonStyleSheet styles, String id, String icon) {
        super(x, y, SIZE, SIZE, label, ignored -> action.run(), styles, id, "toolbar-button");
        this.icon = icon;
        defaultCss(".toolbar-button { background-color:transparent; border-width:0px; color:#505a6b; opacity:1; }");
        setTooltip(Tooltip.create(label));
    }

    /** Light glyphs on a dark toolbar (node editors); the 24px circular hit area is unchanged. */
    public EditorIconButton dark() {
        dark = true;
        return this;
    }

    @Override protected String motionShape() { return "circle"; }
    @Override protected void renderSurface(GuiGraphics graphics, ButtonStyle style, int background, int border) {
        if (dark) ToolIconRenderer.darkSurface(graphics, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused(),
                style.opacity() * alpha);
        else ToolIconRenderer.surface(graphics, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused(),
                style.opacity() * alpha);
    }
    @Override protected void renderContent(GuiGraphics graphics, ButtonStyle style, int color) {
        if (dark) ToolIconRenderer.darkIcon(graphics, icon, getX(), getY(), getWidth(), getHeight(),
                style.opacity() * alpha, active);
        else ToolIconRenderer.icon(graphics, icon, getX(), getY(), getWidth(), getHeight(), style.opacity() * alpha);
    }
}
