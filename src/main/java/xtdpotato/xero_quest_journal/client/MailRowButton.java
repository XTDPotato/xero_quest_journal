package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

public final class MailRowButton extends StyledButton {
    private static final String CSS = """
            .mail-row { background-color:#29313d; border-color:#50555d; border-width:1px; color:#eef3fa; opacity:1; }
            .mail-row:selected { background-color:#fffffe; border-color:#f4e4cc; color:#50545d; opacity:1; }
            """;
    private final MailboxRuntime.View mail;
    private final boolean treasureBox;
    private boolean multiSelect, multiSelected;

    public void multiSelection(boolean enabled, boolean selected) {
        multiSelect = enabled;
        multiSelected = selected;
    }

    public MailRowButton(int x, int y, int width, int height, MailboxRuntime.View mail,
                         OnPress action, ButtonStyleSheet styles) {
        this(x, y, width, height, mail, false, action, styles);
    }

    public MailRowButton(int x, int y, int width, int height, MailboxRuntime.View mail, boolean treasureBox,
                         OnPress action, ButtonStyleSheet styles) {
        super(x, y, width, height, Component.literal(mail.subject()), action,
                styles, "mail-" + mail.id(), "mail-row");
        this.mail = mail;
        this.treasureBox = treasureBox;
        defaultCss(CSS);
        extraCss(MailboxAnimationStore.rowCss());
        hoverMotion();
    }

    @Override
    protected void renderSurface(GuiGraphics graphics, ButtonStyle style, int background, int border) {
        boolean subdued = !mail.claimable() && (mail.read() || !mail.attachments().isEmpty());
        super.renderSurface(graphics, style,
                subdued && !selectionHighlighted() ? 0xFF343841 : background, border);
        int opacity = Math.round(hoverProgress() * 255);
        if (opacity > 0) graphics.renderOutline(getX(), getY(), getWidth(), getHeight(),
                opacity << 24 | 0xFFFFFF);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, ButtonStyle style, int text) {
        var font = Minecraft.getInstance().font;
        int left = getX() + 46;
        int rightInset = mail.starred() ? 42 : isNew() ? 30 : mail.claimable() ? 24 : 12;
        int subjectWidth = Math.max(1, getRight() - rightInset - left);
        FittedText fittedSubject = fit(font, mail.subject(), subjectWidth, 1.3F, .85F);
        boolean subdued = !mail.claimable() && (mail.read() || !mail.attachments().isEmpty());
        int subjectColor = selectionHighlighted() ? (subdued ? 0xFF777B83 : 0xFF373D47)
                : subdued ? 0xFF9A9DA5 : 0xFFF4F3EF;
        MailDecor.text(graphics, font, fittedSubject.value(), left,
                getY() + Math.round((21 - font.lineHeight * fittedSubject.scale()) / 2F),
                fittedSubject.scale(), subjectColor);
        String expiry = expiryLabel();
        int expiryWidth = Math.min(font.width(expiry), Math.max(1, (getWidth() - 65) / 2));
        expiry = font.plainSubstrByWidth(expiry, expiryWidth);
        int senderLeft = left + 12;
        int senderRight = getRight() - 8 - (expiry.isEmpty() ? 0 : expiryWidth + 14);
        String sender = ellipsize(font, mail.sender(), Math.max(1, senderRight - senderLeft));
        JournalNavButton.drawIcon(graphics, "mail_sender", left, getY() + 22, 9, 0xFFADB0B8);
        graphics.drawString(font, sender, senderLeft, getY() + 22,
                selectionHighlighted() ? 0xFF777B83 : 0xFFADB0B8, false);
        graphics.drawString(font, expiry, getRight() - 8 - font.width(expiry), getY() + 22,
                selectionHighlighted() ? 0xFFAAA59E : 0xFFA6ABB4, false);
        graphics.fill(getX() + 5, getY() + 5, getX() + 37, getBottom() - 5,
                selectionHighlighted() ? 0x30505761 : 0x4020242B);
        if (mail.icon().isBlank()) JournalNavButton.drawIcon(graphics, "mail_envelope",
                getX() + 8, getY() + 8, 26, 0xFFFFFFFF);
        else QuestIcons.render(graphics, mail.icon(), getX() + 8, getY() + 8, 26);
        if (mail.starred()) {
            JournalNavButton.drawIcon(graphics, "mail_star", getRight() - 38, getY() + 5, 13, 0xFFFFDB00);
        }
        if (isNew()) newBadge(graphics, getRight() - 22, getY() + 1, 20);
        if (mail.claimable()) {
            JournalNavButton.drawIcon(graphics, "mail_pending",
                    getRight() - 18, getY() + 4, 13, 0xFFFFFFFF);
        }
        if (multiSelect) {
            int x = getX() + 27, y = getY() + 27;
            graphics.fill(x, y, x + 13, y + 13, 0xFF343B48);
            if (multiSelected) JournalNavButton.drawIcon(graphics, "choose", x + 1, y + 1, 11, 0xFFF3D48B);
            else graphics.renderOutline(x + 2, y + 2, 9, 9, 0xFFE1E6EE);
        }
    }

    static FittedText fit(net.minecraft.client.gui.Font font, String value, int width,
                          float preferredScale, float minimumScale) {
        int naturalWidth = font.width(value);
        float scale = naturalWidth <= 0 ? preferredScale
                : Math.min(preferredScale, width / (float) naturalWidth);
        if (scale >= minimumScale) return new FittedText(value, scale);
        scale = minimumScale;
        return new FittedText(ellipsize(font, value, Math.max(1, (int) (width / scale))), scale);
    }

    private static String ellipsize(net.minecraft.client.gui.Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String suffix = "...";
        int prefixWidth = Math.max(0, maxWidth - font.width(suffix));
        String prefix = font.plainSubstrByWidth(value, prefixWidth);
        String result = prefix + suffix;
        return font.width(result) <= maxWidth ? result : font.plainSubstrByWidth(result, maxWidth);
    }

    record FittedText(String value, float scale) {}

    public boolean isNew() { return treasureBox && mail.treasureNew(); }

    public void renderSelectionArrow(GuiGraphics graphics, long nanos, float opacity) {
        renderSelectionArrow(graphics, nanos, opacity, 0);
    }

    public void renderSelectionArrow(GuiGraphics graphics, long nanos, float opacity, int inset) {
        if (!selectionHighlighted() || !visible || opacity <= 0) return;
        int tip = getX() - 2 + inset - (int) Math.round(JournalListButton.arrowOffset(
                nanos, ClientPreferences.get().reducedMotion));
        int centerY = getY() + getHeight() / 2;
        int alpha = Math.round(255 * opacity);
        for (int row = -5; row <= 5; row++) {
            int right = tip - Math.abs(row);
            if (right >= tip - 5)
                graphics.fill(tip - 6, centerY + row, right + 1, centerY + row + 1,
                        (Math.round(alpha * .19F) << 24) | 0x00FFFFFF);
        }
        for (int row = -3; row <= 3; row++) {
            int edge = Math.max(0, Math.min(255, alpha));
            graphics.fill(tip - 4, centerY + row, tip - Math.abs(row), centerY + row + 1,
                    edge << 24 | 0x00FFFFFF);
        }
    }

    public String expiryLabel() {
        if (treasureBox) return "";
        if (mail.treasured() || mail.expiresAt() <= 0)
            return Component.translatable("screen.xero_quest_journal.mailbox.expiry.permanent").getString();
        long remaining = ClientMailbox.remainingSeconds(mail);
        long day = mail.gameTime() ? 1200 : 86400;
        String key;
        long count;
        if (remaining >= day) { key = mail.gameTime() ? "game_days" : "days"; count = (remaining + day - 1) / day; }
        else if (!mail.gameTime() && remaining >= 3600) { key = "hours"; count = (remaining + 3599) / 3600; }
        else if (remaining >= 60) { key = "minutes"; count = (remaining + 59) / 60; }
        else { key = "seconds"; count = remaining; }
        return Component.translatable("screen.xero_quest_journal.mailbox.expiry." + key, count).getString();
    }

    static void newBadge(GuiGraphics graphics, int x, int y, int size) {
        JournalNavButton.drawIcon(graphics, MailboxAnimationStore.attribute("new_badge", "background", "mail_new_bg"),
                x, y, size, MailboxAnimationStore.color("new_badge", "background", 0xFFC88008));
        JournalNavButton.drawIcon(graphics, MailboxAnimationStore.attribute("new_badge", "frame", "mail_new_frame"),
                x, y, size, MailboxAnimationStore.color("new_badge", "border", 0xFFF0C038));
        String value = MailboxAnimationStore.attribute("new_badge", "text", "");
        if (value.isEmpty()) value = Component.translatable("screen.xero_quest_journal.mailbox.new").getString();
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1.25F, (size - 7F) / Math.max(1, font.width(value)));
        MailDecor.centered(graphics, font, value, x + size / 2, y + Math.round((size - font.lineHeight * scale) / 2),
                scale, MailboxAnimationStore.color("new_badge", "text", 0xFFF8F070));
    }
}
