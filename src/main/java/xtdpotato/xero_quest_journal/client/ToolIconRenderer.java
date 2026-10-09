package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared compact circular tool treatment, matching the mailbox controls. */
final class ToolIconRenderer {
    private ToolIconRenderer() {}
    static void surface(GuiGraphics g, int x, int y, int width, int height, boolean hovered, float alpha) {
        int size = Math.min(24, Math.min(width, height));
        int left = x + (width - size) / 2, top = y + (height - size) / 2;
        int opacity = Math.clamp(Math.round(alpha * 255), 0, 255) << 24;
        MailDecor.circle(g, left, top, size, opacity | (hovered ? 0xFFEAC9 : 0xFFFFFF));
    }
    static void icon(GuiGraphics g, String icon, int x, int y, int width, int height, float alpha) {
        int size = Math.min(24, Math.min(width, height));
        int inset = Math.max(3, size / 4), glyph = Math.max(1, size - inset * 2);
        JournalNavButton.drawIcon(g, icon, x + (width - glyph) / 2, y + (height - glyph) / 2,
                glyph, Math.clamp(Math.round(alpha * 255), 0, 255) << 24 | 0x505A6B);
    }

    /** Dark-toolbar variant: no plate at rest, a soft light disc while hovered or focused. */
    static void darkSurface(GuiGraphics g, int x, int y, int width, int height, boolean hovered, float alpha) {
        if (!hovered) return;
        int size = Math.min(24, Math.min(width, height));
        int left = x + (width - size) / 2, top = y + (height - size) / 2;
        int opacity = Math.clamp(Math.round(alpha * 0x40), 0, 255) << 24;
        MailDecor.circle(g, left, top, size, opacity | 0xFFFFFF);
    }

    /** Light glyph for dark toolbars; disabled tools are dimmed. */
    static void darkIcon(GuiGraphics g, String icon, int x, int y, int width, int height, float alpha, boolean active) {
        int size = Math.min(24, Math.min(width, height));
        int inset = Math.max(3, size / 4), glyph = Math.max(1, size - inset * 2);
        JournalNavButton.drawIcon(g, icon, x + (width - glyph) / 2, y + (height - glyph) / 2,
                glyph, Math.clamp(Math.round(alpha * 255), 0, 255) << 24 | (active ? 0xE8E8EC : 0x6E6E76));
    }
}
