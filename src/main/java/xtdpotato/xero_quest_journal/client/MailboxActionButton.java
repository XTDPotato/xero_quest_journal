package xtdpotato.xero_quest_journal.client;

import net.minecraft.network.chat.Component;

/** Mailbox command button with independently editable hover and click motion. */
public final class MailboxActionButton extends JournalNavButton {
    private final String mailGlyph;
    private final Kind mailKind;
    public MailboxActionButton(int x, int y, int width, int height, Component label, OnPress action,
                               ButtonStyleSheet styles, String id, Kind kind, String glyph, String actionKey) {
        super(x, y, width, height, label, action, styles, id, kind, glyph);
        mailGlyph = glyph;
        mailKind = kind;
        extraCss("""
                .journal-action { background-color:#ffffff; border-width:0px; color:#505a70; opacity:1; }
                .journal-action:hover { background-color:#fff1da; }
                .journal-icon { background-color:transparent; border-width:0px; }
                """ + MailboxAnimationStore.animation(actionKey + "_hover").css + "\n"
                + MailboxAnimationStore.animation(actionKey + "_click").css);
        actionMotion(actionKey);
    }

    public void copyAnimationFrom(MailboxActionButton previous) {
        if (previous == null) return;
        copyMotionFrom(previous);
    }

    @Override protected void renderSurface(net.minecraft.client.gui.GuiGraphics g, ButtonStyle style, int background, int border) {
        if (mailKind == Kind.ICON && (mailGlyph.startsWith("mail_") || mailGlyph.equals("delete"))) {
            if (mailGlyph.equals("mail_close")) {
                MailDecor.circle(g, getX(), getY(), getWidth(), 0xFF97958F);
                MailDecor.circle(g, getX() + 3, getY() + 3, getWidth() - 6, 0xFFFFFFFF);
            } else MailDecor.circle(g, getX(), getY(), getWidth(), isHovered() ? 0xFFFFEAC9 : 0xFFFFFFFF);
        } else super.renderSurface(g, style, background, border);
    }

    @Override protected void renderContent(net.minecraft.client.gui.GuiGraphics g, ButtonStyle style, int text) {
        if (mailKind == Kind.ICON && (mailGlyph.startsWith("mail_") || mailGlyph.equals("delete"))) {
            if (mailGlyph.equals("mail_help")) {
                drawIcon(g, mailGlyph, getX() - 1, getY() + 2, getWidth(), 0xFF505A6B);
            } else {
                int inset = Math.min(5, getWidth() / 4);
                drawIcon(g, mailGlyph, getX() + inset, getY() + inset,
                        getWidth() - inset * 2, 0xFF505A6B);
            }
        } else if (mailKind == Kind.ACTION && (mailGlyph.equals("delete") || mailGlyph.equals("mail_claim")
                || mailGlyph.equals("cancel_choice") || mailGlyph.equals("confirm_choice"))) {
            MailDecor.circle(g, getX() + 5, getY() + 3, getHeight() - 6, 0xFF34373A);
            if (mailGlyph.endsWith("_choice")) {
                if (mailGlyph.equals("confirm_choice")) {
                    MailDecor.circle(g, getX() + 10, getY() + 8, getHeight() - 16, 0xFFFFD900);
                    MailDecor.circle(g, getX() + 12, getY() + 10, getHeight() - 20, 0xFF34373A);
                } else drawIcon(g, "close", getX() + 9, getY() + 7, getHeight() - 14, 0xFF00B9E8);
            } else drawIcon(g, mailGlyph, getX() + 8, getY() + 6, getHeight() - 12,
                    mailGlyph.equals("delete") ? 0xFFF05D70 : 0xFFFFD900);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - getHeight() - 10));
            g.drawString(font, label, getX() + getHeight() + (getWidth() - getHeight() - font.width(label)) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2, text, false);
        } else super.renderContent(g, style, text);
    }
}
