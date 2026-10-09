package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A labeled binary switch with a stable hit area and keyboard button semantics. */
final class QuestFocusButton extends StyledButton {
    private final boolean enabled;

    QuestFocusButton(int x, int y, int width, Component label, boolean enabled,
                     OnPress action, ButtonStyleSheet styles) {
        super(x, y, width, 24, label, action, styles, "focus", "quest-focus");
        this.enabled = enabled;
        defaultCss("""
                .quest-focus { background-color:transparent; border-width:0px; color:#ffffff; opacity:1; }
                .quest-focus:disabled { color:#b8bbc2; opacity:0.65; }
                """);
    }

    @Override protected void renderSurface(GuiGraphics g, ButtonStyle style, int background, int border) {
        if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFF2D29A);
    }

    @Override protected void renderContent(GuiGraphics g, ButtonStyle style, int text) {
        var font = Minecraft.getInstance().font;
        int x = getRight() - 34, y = getY() + 4;
        int border = active ? isHovered() ? 0xFFFFFFFF : 0xFFECE6D8 : 0xFF96999E;
        pill(g, x, y, 34, 16, border);
        pill(g, x + 1, y + 1, 32, 14, enabled ? 0xFF698D7A : 0xFF343D4D);
        int knob = x + (enabled ? 19 : 3);
        MailDecor.circle(g, knob, y + 2, 12, 0xFFF2ECDE);
        JournalNavButton.drawIcon(g, enabled ? "track" : "close", knob + 2, y + 4, 8, 0xFFAD925B);
        String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - 40));
        g.drawString(font, label, x - 6 - font.width(label),
                getY() + (getHeight() - font.lineHeight) / 2, text, false);
    }

    private static void pill(GuiGraphics g, int x, int y, int w, int h, int color) {
        MailDecor.circle(g, x, y, h, color);
        MailDecor.circle(g, x + w - h, y, h, color);
        g.fill(x + h / 2, y, x + w - h / 2, y + h, color);
    }
}
