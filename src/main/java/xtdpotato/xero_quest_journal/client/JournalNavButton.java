package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class JournalNavButton extends StyledButton {
    public enum Kind { TAB, ACTION, ICON, KEY }
    private static final long SELECTION_ANIMATION_NANOS = 220_000_000L;
    private static final String CSS = """
            .journal-tab, .journal-icon {
                background-color: transparent; border-width: 0px; color: #c6c8cf; opacity: 1;
            }
            .journal-tab:hover, .journal-icon:hover { color: #ffffff; background-color: #ffffff18; }
            .journal-tab:selected { color: #ffffff; }
            .journal-icon:focus { border-color: #f2d29a; border-width: 1px; }
            .journal-key {
                background-color: #ffffff; border-color: #d8d8dc; border-width: 1px;
                color: #303238; opacity: 1;
            }
            .journal-key:hover { background-color: #f4f4f6; color: #17181c; }
            .journal-key:active { background-color: #dedee3; }
            .journal-key:focus { border-color: #f2d29a; border-width: 1px; }
            .journal-action { background-color: #fffffe; color: #50586d; border-width: 0px; opacity: 1; }
            .journal-action:hover { background-color: #f7ece0; color: #303849; }
            .journal-action:active { background-color: #e7d9c5; }
            .journal-action:focus { border-color: #f2d29a; border-width: 1px; }
            .journal-action:disabled { background-color: #aeb0b6; color: #50586d; opacity: 0.8; }
            button#close { background-color: #fffffe; color: #50586d; border-width: 0px; }
            button#close:hover { background-color: #f7ece0; }
            """;
    private final Kind kind;
    private final String glyph;
    private long selectionAnimationStart;
    private boolean compactContent;
    private boolean limitedRewardBadge;

    public JournalNavButton compactContent() { compactContent = true; return this; }

    public JournalNavButton setLimitedRewardBadge(boolean value) {
        limitedRewardBadge = value;
        return this;
    }

    public boolean limitedRewardBadge() {
        return limitedRewardBadge;
    }

    @Override protected String motionShape() {
        return kind == Kind.ICON || glyph.equals("close") ? "circle"
                : kind == Kind.ACTION ? "pill" : kind == Kind.KEY ? "rounded" : "rectangle";
    }

    @Override protected int motionCornerRadius() { return kind == Kind.KEY ? 3 : 0; }

    public JournalNavButton(int x, int y, int width, int height, Component label, OnPress action,
                            ButtonStyleSheet styles, String id, Kind kind, String glyph) {
        super(x, y, width, height, label, action, styles, id, "journal-" + kind.name().toLowerCase(java.util.Locale.ROOT));
        this.kind = kind;
        this.glyph = glyph;
        defaultCss(CSS);
    }

    @Override
    protected void renderSurface(GuiGraphics g, ButtonStyle style, int background, int border) {
        if (kind == Kind.TAB) {
        } else if (kind == Kind.KEY) {
            roundedRect(g, getX(), getY(), getWidth(), getHeight(), border);
            roundedRect(g, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, background);
        } else if (kind == Kind.ICON) {
            ToolIconRenderer.surface(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused(), style.opacity() * alpha);
        } else if (glyph.equals("close")) {
            int diameter = Math.min(getWidth(), getHeight());
            int x = getX() + (getWidth() - diameter) / 2;
            int y = getY() + (getHeight() - diameter) / 2;
            circle(g, x, y, diameter, background);
            if (style.borderWidth() > 0) {
                int edge = Math.min(3, style.borderWidth());
                circle(g, x, y, diameter, border);
                circle(g, x + edge, y + edge, diameter - edge * 2, background);
            }
        } else if (kind == Kind.ACTION) {
            pill(g, getX(), getY(), getWidth(), getHeight(), background);
            if (style.borderWidth() > 0) {
                int edge = Math.min(3, style.borderWidth());
                pill(g, getX(), getY(), getWidth(), getHeight(), border);
                pill(g, getX() + edge, getY() + edge, getWidth() - edge * 2, getHeight() - edge * 2, background);
            }
        } else {
            super.renderSurface(g, style, background, border);
        }
        if (kind == Kind.TAB && selectionHighlighted()) {
            float progress = selectionProgress();
            int diameter = Math.max(2, Math.min(getWidth() - 4, getHeight() - 4) & ~1);
            int circleX = tabCenterX() - diameter / 2;
            int circleY = tabCenterY() - diameter / 2;
            circle(g, circleX, circleY, diameter,
                    ((int) (progress * 255.0F) << 24) | 0x00FFFFFF);
            int lineWidth = Math.max(2, Math.round(2 + (getWidth() - 6) * progress));
            int center = getX() + getWidth() / 2;
            g.fill(center - lineWidth / 2, getBottom() - 2,
                    center + (lineWidth + 1) / 2, getBottom(), 0xFFF2D29A);
        }
        if (limitedRewardBadge) renderLimitedRewardBadge(g);
    }

    @Override
    protected void renderHoverOutline(GuiGraphics g, int color) {
        if (kind == Kind.TAB) {
            return;
        } else if (motionShape().equals("pill")) {
            roundedOutline(g, getX(), getY(), getWidth(), getHeight(), getHeight() / 2, color);
        } else if (motionShape().equals("rounded")) {
            roundedOutline(g, getX(), getY(), getWidth(), getHeight(), 3, color);
        } else {
            super.renderHoverOutline(g, color);
        }
    }

    private static void pill(GuiGraphics g, int x, int y, int width, int height, int color) {
        double radius = Math.min(width, height) / 2.0;
        for (int row = 0; row < height; row++) {
            double dy = row + 0.5 - height / 2.0;
            int inset = (int) Math.ceil(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            g.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void roundedRect(GuiGraphics g, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        int radius = Math.min(3, Math.min(width, height) / 2);
        for (int row = 0; row < height; row++) {
            int inset = roundedInset(row, height, radius);
            g.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void roundedOutline(GuiGraphics g, int x, int y, int width, int height,
                                       int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        radius = Math.min(radius, Math.min(width, height) / 2);
        for (int row = 0; row < height; row++) {
            int inset = roundedInset(row, height, radius);
            if (row == 0 || row == height - 1) {
                g.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            } else {
                g.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
                g.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
            }
        }
    }

    private static int roundedInset(int row, int height, int radius) {
        if (radius <= 0) return 0;
        double dy = row < radius ? radius - row - .5
                : row >= height - radius ? row - (height - radius) + .5 : 0;
        if (dy == 0) return 0;
        return Math.max(0, Math.min(radius,
                (int) Math.ceil(radius - Math.sqrt(Math.max(0, radius * (double) radius - dy * dy)))));
    }

    private static void circle(GuiGraphics g, int x, int y, int diameter, int color) {
        if (diameter <= 0 || (color >>> 24) == 0) return;
        MailDecor.circle(g, x, y, diameter, color);
    }

    @Override
    protected void renderContent(GuiGraphics g, ButtonStyle style, int text) {
        if (kind == Kind.ICON) {
            ToolIconRenderer.icon(g, glyph, getX(), getY(), getWidth(), getHeight(), style.opacity() * alpha);
            return;
        }
        if (kind == Kind.KEY) {
            var font = Minecraft.getInstance().font;
            g.pose().pushPose();
            g.pose().translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0);
            g.pose().scale(.5f, .5f, 1);
            g.drawString(font, getMessage(), -font.width(getMessage()) / 2, -font.lineHeight / 2, text, false);
            g.pose().popPose();
            return;
        }
        int size = kind == Kind.ACTION ? compactContent ? 16 : 18 : Math.min(20, getHeight() - (kind == Kind.TAB ? 8 : 6));
        if (kind == Kind.TAB) size = Math.max(2, size & ~1);
        int x = kind == Kind.ACTION ? getX() + (compactContent ? 8 : 12) : getX() + (getWidth() - size) / 2;
        int iconColor = text;
        if (kind == Kind.TAB) {
            float progress = selectionHighlighted() ? selectionProgress() : 0.0F;
            int channel = Math.max(0, Math.min(255, Math.round(255.0F * (1.0F - progress))));
            iconColor = 0x80000000 | channel << 16 | channel << 8 | channel;
        }
        if (kind == Kind.TAB) {
            CategoryIconRenderer.render(g, glyph, tabCenterX() - size / 2, tabCenterY() - size / 2,
                    size, iconColor);
        } else if (kind == Kind.ACTION && glyph.equals("track") && styleId().equals("track")) {
            QuestTrackingIcons.renderControl(g, x, getY() + (getHeight() - size) / 2, size, iconColor);
        } else {
            drawIcon(g, glyph, x, getY() + (getHeight() - size) / 2, size, iconColor);
        }
        if (kind == Kind.ACTION) {
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - (compactContent ? 36 : 48)));
            g.drawString(font, label, getX() + (compactContent ? 28 : 38), getY() + (getHeight() - font.lineHeight) / 2, text, false);
        }
    }

    private void renderLimitedRewardBadge(GuiGraphics g) {
        drawLimitedRewardBadge(g, Minecraft.getInstance().font, getX() - 2, getY() - 5, getWidth() + 4);
    }

    public static void drawLimitedRewardBadge(GuiGraphics g, net.minecraft.client.gui.Font font,
                                              int x, int y, int maxWidth) {
        String label = Component.translatable("screen.xero_quest_journal.limited_reward").getString();
        int labelWidth = font.width(label);
        int width = Math.min(Math.max(34, maxWidth), Math.max(38, labelWidth + 20));
        x = Math.max(0, x);
        y = Math.max(0, y);
        g.fill(x, y, x + width, y + 12, 0xFFE8893D);
        drawIcon(g, "gift", x + 3, y + 2, 8, 0xFFFFFFFF);
        String visible = font.plainSubstrByWidth(label, Math.max(1, width - 14));
        g.drawString(font, visible, x + 13, y + 2, 0xFFFFFFFF, false);
    }

    public void animateSelection(long startNanos) {
        selectionAnimationStart = startNanos;
    }

    private int tabCenterX() { return getX() + getWidth() / 2; }
    private int tabCenterY() { return getY() + (getHeight() - 4) / 2; }

    private float selectionProgress() {
        if (AnimatedQuestScreen.animationMillis() == 0) return selectionHighlighted() ? 1 : 0;
        if (!selectionHighlighted() || selectionAnimationStart <= 0) return selectionHighlighted() ? 1.0F : 0.0F;
        float linear = Math.max(0.0F, Math.min(1.0F,
                (System.nanoTime() - selectionAnimationStart) / (float) SELECTION_ANIMATION_NANOS));
        float inverse = 1.0F - linear;
        return 1.0F - inverse * inverse * inverse;
    }

    public static void drawIcon(GuiGraphics g, String name, int x, int y, int size, int color) {
        if (name == null || name.isBlank()) name = "other";
        if (name.equals("refresh")) {
            drawRefresh(g, x, y, size, color);
            return;
        }
        if (name.equals("fullscreen")) {
            drawFullscreen(g, x, y, size, color);
            return;
        }
        if (name.equals("sort_up") || name.equals("sort_down")) {
            drawSort(g, x, y, size, color, name.equals("sort_down"));
            return;
        }
        if (name.equals("forward") || name.equals("next")) {
            g.pose().pushPose();
            g.pose().translate(x + size, y, 0);
            g.pose().scale(-1, 1, 1);
            drawIcon(g, "back", 0, 0, size, color);
            g.pose().popPose();
            return;
        }
        if (name.contains(":") || name.contains("/") || name.contains("\\")) {
            QuestIcons.render(g, name, x, y, size);
            return;
        }
        g.setColor((color >>> 16 & 255) / 255F, (color >>> 8 & 255) / 255F,
                (color & 255) / 255F, (color >>> 24) / 255F);
        g.blit(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "textures/gui/navigation/" + name + ".png"),
                x, y, size, size, 0, 0, 32, 32, 32, 32);
        g.setColor(1, 1, 1, 1);
    }

    private static void drawRefresh(GuiGraphics g, int x, int y, int size, int color) {
        int stroke = Math.max(2, size / 6);
        int left = x + stroke, top = y + stroke, right = x + size - stroke, bottom = y + size - stroke;
        g.fill(left + stroke, top, right - stroke, top + stroke, color);
        g.fill(right - stroke, top + stroke, right, y + size / 2, color);
        g.fill(left, bottom - stroke, right - stroke, bottom, color);
        g.fill(left, y + size / 2, left + stroke, bottom - stroke, color);
        g.fill(right - stroke * 2, top - 1, right + 1, top + stroke * 2, color);
        g.fill(right - stroke * 2, top - 1, right + stroke, top + stroke, color);
        g.fill(left - 1, bottom - stroke * 2, left + stroke * 2, bottom + 1, color);
        g.fill(left - 1, bottom - stroke, left + stroke, bottom + stroke, color);
    }

    private static void drawFullscreen(GuiGraphics g, int x, int y, int size, int color) {
        int stroke = Math.max(2, size / 6), inset = Math.max(2, size / 5);
        g.fill(x + inset, y + inset, x + inset + stroke, y + inset + size / 3, color);
        g.fill(x + inset, y + inset, x + inset + size / 3, y + inset + stroke, color);
        g.fill(x + size - inset - stroke, y + inset, x + size - inset, y + inset + size / 3, color);
        g.fill(x + size - inset - size / 3, y + inset, x + size - inset, y + inset + stroke, color);
        g.fill(x + inset, y + size - inset - stroke, x + inset + size / 3, y + size - inset, color);
        g.fill(x + inset, y + size - inset - size / 3, x + inset + stroke, y + size - inset, color);
        g.fill(x + size - inset - size / 3, y + size - inset - stroke, x + size - inset, y + size - inset, color);
        g.fill(x + size - inset - stroke, y + size - inset - size / 3, x + size - inset, y + size - inset, color);
    }

    private static void drawSort(GuiGraphics g, int x, int y, int size, int color, boolean down) {
        int mid = x + size / 2, top = y + size / 5, bottom = y + size - size / 5;
        int shaft = Math.max(2, size / 7);
        if (!down) {
            g.fill(mid - shaft / 2, top + 3, mid + (shaft + 1) / 2, bottom, color);
            g.fill(mid - size / 4, top, mid + size / 4, top + shaft, color);
            g.fill(mid - size / 4, top, mid - size / 4 + shaft, top + size / 4, color);
            g.fill(mid + size / 4 - shaft, top, mid + size / 4, top + size / 4, color);
        } else {
            g.fill(mid - shaft / 2, top, mid + (shaft + 1) / 2, bottom - 3, color);
            g.fill(mid - size / 4, bottom - shaft, mid + size / 4, bottom, color);
            g.fill(mid - size / 4, bottom - size / 4, mid - size / 4 + shaft, bottom, color);
            g.fill(mid + size / 4 - shaft, bottom - size / 4, mid + size / 4, bottom, color);
        }
    }
}
