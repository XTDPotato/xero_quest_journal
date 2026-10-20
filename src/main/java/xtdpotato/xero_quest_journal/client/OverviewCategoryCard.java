package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Raised category cards: release inside, settle, then let the screen begin its exit. */
final class OverviewCategoryCard extends JournalNavButton {
    private final String icon;
    private UiMotion lift = new UiMotion(false, System.nanoTime());
    private boolean pointerHeld;
    private boolean activationPending;

    OverviewCategoryCard(int x, int y, int width, int height, Component title, OnPress action,
                           ButtonStyleSheet styles, String id, String icon) {
        this(x, y, width, height, title, action, styles, id, icon, null);
    }

    OverviewCategoryCard(int listX, int listY, int x, int y, int width, int height, Component title,
                         OnPress action, ButtonStyleSheet styles, String id, String icon) {
        this(x, y, width, height, title, action, styles, id, icon, new int[]{listX, listY});
    }

    private OverviewCategoryCard(int x, int y, int width, int height, Component title, OnPress action,
                                 ButtonStyleSheet styles, String id, String icon, int[] clipping) {
        super(x, y, width, height, title, action, styles, id, Kind.ACTION, icon);
        if (clipping != null) clipTo(clipping[0], clipping[1], clipping[0] + width, clipping[1] + height);
        this.icon = icon;
        extraCss("""
                .journal-action { background-color:#715b49b8; border-color:#dcc59888; border-width:1px; color:#ffffff; }
                .journal-action:hover { background-color:#907759dc; border-color:#f1d5a0; }
                .journal-action:active { background-color:#534a40ee; }
                """);
    }
    private int duration() { return AnimatedQuestScreen.animationMillis() == 0 ? 0 : 160; }
    boolean activationPending() { return activationPending; }
    @Override protected boolean pressed() { return pointerHeld || activationPending || super.pressed(); }

    @Override protected void renderWidget(GuiGraphics graphics, int x, int y, float delta) {
        lift.target(active && isMouseOver(x, y) && !pressed(), System.nanoTime(), duration());
        float raised = lift.eased();
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + getWidth() / 2F, getY() + getHeight() / 2F - 2 * raised, 0);
        graphics.pose().scale(1 + .025F * raised, 1 + .025F * raised, 1);
        graphics.pose().translate(-getX() - getWidth() / 2F, -getY() - getHeight() / 2F, 0);
        if (raised > 0) graphics.fill(getX() + 1, getY() + 3, getRight() + 2, getBottom() + 3,
                Math.round(70 * raised) << 24);
        super.renderWidget(graphics, x, y, delta);
        graphics.pose().popPose();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !active || !visible || activationPending || !isMouseOver(x, y)) return false;
        pointerHeld = true;
        lift.target(false, System.nanoTime(), duration());
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !pointerHeld) return super.mouseReleased(x, y, button);
        pointerHeld = false;
        if (active && visible && isMouseOver(x, y)) onPress();
        return true;
    }

    @Override public void setFocused(boolean value) {
        super.setFocused(value);
        if (!value) pointerHeld = false;
    }

    @Override public void onPress() {
        if (!active || !visible || activationPending) return;
        activationPending = true;
        lift.target(false, System.nanoTime(), duration());
        if (duration() == 0) advanceActivation();
    }

    void advanceActivation() {
        lift.advance(System.nanoTime(), duration());
        if (activationPending && lift.settled()) {
            activationPending = false;
            super.onPress();
        }
    }

    @Override public void copyMotionFrom(StyledButton previous) {
        super.copyMotionFrom(previous);
        if (previous instanceof OverviewCategoryCard card) {
            lift = card.lift;
            activationPending = card.activationPending;
            pointerHeld = card.pointerHeld;
        }
    }

    @Override protected String motionShape() { return "rectangle"; }
    int iconSlotWidth() { return getWidth() < 130 ? 24 : 42; }
    int iconSlotX() { return getX() + (getWidth() < 130 ? 5 : 12); }
    int dividerX() { return iconSlotX() + iconSlotWidth() + (getWidth() < 130 ? 4 : 10); }
    int labelX() { return dividerX() + (getWidth() < 130 ? 7 : 12); }
    int forwardSize() { return Math.max(8, Math.min(14, getHeight() - 10)); }
    int categoryIconSize() {
        return Math.min(getHeight() - 8, getWidth() < 130 ? 16 : 24);
    }

    @Override protected void renderSurface(GuiGraphics g, ButtonStyle style, int background, int border) {
        g.fill(getX(), getY(), getRight(), getBottom(), background);
        // Soft top light and darker foot give the parchment card some depth.
        g.fillGradient(getX() + 1, getY() + 1, getRight() - 1, getY() + getHeight() / 2, 0x18FFFFFF, 0x00FFFFFF);
        g.fillGradient(getX() + 1, getY() + getHeight() / 2, getRight() - 1, getBottom() - 1, 0x00000000, 0x22000000);
        g.renderOutline(getX(), getY(), getWidth(), getHeight(), border);
        if (getHeight() > 24) g.renderOutline(getX() + 3, getY() + 3, getWidth() - 6, getHeight() - 6, 0x447C735F);
        // Notched light edge on the right, echoing Genshin's category plaques.
        g.fill(getRight() - 2, getY() + 2, getRight() - 1, getBottom() - 2, 0x66F1D5A0);
        for (int notch = getY() + 6; notch < getBottom() - 4; notch += 6)
            g.fill(getRight() - 3, notch, getRight() - 1, notch + 2, 0x88F1D5A0);
        if (lift.position() > 0) g.renderOutline(getX() - 2, getY() - 2, getWidth() + 4, getHeight() + 4,
                Math.round(255 * lift.eased()) << 24 | 0xF1D5A0);
    }
    @Override protected void renderContent(GuiGraphics g, ButtonStyle style, int color) {
        var font = Minecraft.getInstance().font;
        int iconSize = categoryIconSize();
        int iconX = iconSlotX() + (iconSlotWidth() - iconSize) / 2;
        int iconY = getY() + (getHeight() - iconSize) / 2;
        if (icon.equals("anecdote_map"))
            AnecdoteBookArt.mapScroll(g, iconX, iconY, iconSize, 0xFFF1D5A0);
        else JournalNavButton.drawIcon(g, icon, iconX, iconY, iconSize, 0xFFF1D5A0);
        int divider = dividerX();
        int textX = labelX();
        for (int y = getY() + 6; y < getBottom() - 5; y += 3) g.fill(divider, y, divider + 1, y + 1, 0x779F9078);
        int knob = forwardSize();
        var lines = font.split(getMessage(), Math.max(1, getRight() - textX - knob - 14));
        int count = Math.min(lines.size(), Math.max(1, (getHeight() - 6) / font.lineHeight));
        int y = getY() + (getHeight() - count * font.lineHeight) / 2;
        for (int i = 0; i < count; i++) g.drawString(font, lines.get(i), textX, y + i * font.lineHeight, color, false);
        // Keep the compact navigation mark from the original category cards.
        int knobX = getRight() - knob - 8, knobY = getY() + (getHeight() - knob) / 2;
        MailDecor.circle(g, knobX, knobY, knob, 0xCCDCC598);
        MailDecor.circle(g, knobX + 1, knobY + 1, knob - 2, 0xFF4A3B2C);
        int chevronX = knobX + knob / 2 - 1, chevronY = knobY + knob / 2;
        int arm = Math.max(2, knob / 4);
        for (int row = -arm; row <= arm; row++)
            g.fill(chevronX + (arm - Math.abs(row)) - 1, chevronY + row, chevronX + (arm - Math.abs(row)) + 1,
                    chevronY + row + 1, 0xFFF1D5A0);
        if (limitedRewardBadge()) JournalNavButton.drawLimitedRewardBadge(g, font, getX(), getY() - 4, getWidth());
    }
}
