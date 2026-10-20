package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;

/** Left-aligned journal row; leaves a dedicated area for status or disclosure icons. */
public final class JournalListButton extends StyledButton {
    static final String DEFAULT_CSS = """
            .quest-button {
                background-color:#414c5dcc; border-width:0px; color:#ece8df; opacity:1;
                padding:6px; text-shadow:false;
            }
            .quest-button:hover { background-color:#4b5869dd; border-color:#d1be92; border-width:1px; }
            .quest-button:active { background-color:#374353ee; }
            .group-button {
                background-color:#283545e6; border-color:#56595a; border-width:1px;
                color:#d5bf89; opacity:1; padding:8px; text-shadow:false;
            }
            .group-button:hover { background-color:#303c4de6; border-color:#d1be92; }
            .quest-button.completed { background-color:#383d45cc; color:#aeb5bf; }
            """;
    private final ListIconConfig leadingIcon;
    private final ListIconConfig trailingIcon;
    private final String description;
    private final boolean group;
    private final boolean expanded;
    private final boolean completed;
    private final boolean tracked;
    private final Runnable leadingAction;
    private boolean selected;
    private String accent = "none";
    private String trackingType = "world";
    private boolean locked;
    private boolean claimable;
    private boolean claimPressed;
    private int claimIconX, claimIconY, claimIconSize;
    private boolean newTask;
    private java.util.function.Supplier<String> subtitle;

    public JournalListButton locked(boolean value) { locked = value; return this; }
    public boolean locked() { return locked; }
    public JournalListButton claimable(boolean value) { claimable = value; return this; }
    public boolean claimable() { return claimable; }
    public JournalListButton claimPressed(boolean value) {
        claimPressed = value && claimable;
        return this;
    }
    public boolean claimPressed() { return claimPressed; }
    public JournalListButton newTask(boolean value) { newTask = value; return this; }
    public boolean notificationBadge() { return !completed && (claimable || newTask); }

    /** Second line of a task row (distance, authored subtitle or current step); evaluated each frame. */
    public JournalListButton subtitle(java.util.function.Supplier<String> value) {
        subtitle = value;
        return this;
    }

    public String subtitleText() {
        if (subtitle == null) return "";
        String value = subtitle.get();
        return value == null ? "" : value;
    }

    public JournalListButton trackingType(String value) {
        trackingType = value;
        return this;
    }

    public String trackingStyle() {
        return xtdpotato.xero_quest_journal.core.model.QuestTrackingStyle.resolve(
                trailingIcon.trackingStyle(), trackingType);
    }

    public JournalListButton accent(String value) {
        accent = xtdpotato.xero_quest_journal.core.model.QuestListAccent.normalize(value);
        return this;
    }

    public String accent() { return accent; }

    public JournalListButton(int x, int y, int width, int height, Component title, OnPress onPress,
                             ButtonStyleSheet styles, String id, String image,
                             String description, boolean group, boolean expanded, boolean completed) {
        this(x, y, width, height, title, onPress, styles, id, image, description,
                group, expanded, completed, null);
    }

    public JournalListButton(int x, int y, int width, int height, Component title, OnPress onPress,
                             ButtonStyleSheet styles, String id, String image,
                             String description, boolean group, boolean expanded, boolean completed,
                             Runnable leadingAction) {
        super(x, y, width, height, title, onPress, styles, id,
                group ? new String[]{"group-button"}
                        : completed ? new String[]{"quest-button", "completed"} : new String[]{"quest-button"});
        this.leadingIcon = ListIconConfig.leading(image);
        this.trailingIcon = ListIconConfig.trailing();
        this.description = description;
        this.group = group;
        this.expanded = expanded;
        this.completed = completed;
        this.tracked = false;
        this.leadingAction = leadingAction;
        applyCompletedDefaults();
    }

    public JournalListButton(int x, int y, int width, int height, Component title, OnPress onPress,
                             ButtonStyleSheet styles, String id, ListIconConfig leadingIcon,
                             ListIconConfig trailingIcon, String description, boolean group,
                             boolean expanded, boolean completed, boolean tracked, String status,
                             Runnable leadingAction) {
        super(x, y, width, height, title, onPress, styles, id,
                group ? new String[]{"group-button"}
                        : completed ? new String[]{"quest-button", "completed"} : new String[]{"quest-button"});
        this.leadingIcon = leadingIcon == null ? ListIconConfig.leading("") : leadingIcon;
        this.trailingIcon = trailingIcon == null ? ListIconConfig.trailing() : trailingIcon;
        this.description = description == null ? "" : description;
        this.group = group;
        this.expanded = expanded;
        this.completed = completed;
        this.tracked = tracked;
        this.leadingAction = leadingAction;
        applyCompletedDefaults();
    }

    private void applyCompletedDefaults() {
        defaultCss(DEFAULT_CSS);
        if (completed) {
            defaultCss(DEFAULT_CSS + """
                    .completed {
                        background-color: #383d45;
                        border-color: #5e6570;
                        color: #aeb5bf;
                        opacity: 0.92;
                    }
                    .completed:hover {
                        background-color: #454b55;
                        border-color: #858e9b;
                    }
                    .completed:selected {
                        background-color: #ffffff80;
                        border-color: #f7ece080;
                        color: #eef1f5;
                        opacity: 1.0;
                    }
                    """);
        }
    }

    @Override
    protected ButtonStyle selectedStyle(ButtonStyle style) {
        if (group) {
            return new ButtonStyle(style.backgroundColor(), 0xFFF0EBDD, 2, style.textColor(),
                    1.0F, style.padding(), style.textShadow());
        }
        if (completed) {
            return new ButtonStyle(0x80FFFFFF, 0x80F7ECE0, 1, 0xFFEEF1F5,
                    1.0F, style.padding(), false);
        }
        return new ButtonStyle(0xFFECE6DA, 0xFFF5EFE2, 1, 0xFF50586D,
                1.0F, style.padding(), false);
    }

    @Override
    protected void renderSurface(GuiGraphics g, ButtonStyle style, int background, int border) {
        super.renderSurface(g, style, background, border);
        if (!group && selected) {
            renderSelectedOrnament(g, style.opacity() * alpha);
            g.renderOutline(getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4,
                    applyAlpha(0xFFD5CFC3, style.opacity() * alpha));
        }
        if (accent.equals("gold") || accent.equals("blue")) {
            int bar = accent.equals("gold") ? selected && !group ? 0xFFC59D57 : 0xFFA5803E : 0xFF748FD6;
            int diamond = accent.equals("gold") ? 0xFFF5C46F : 0xFF9DE8FF;
            int x = getX() + (selected ? 3 : 2);
            int top = getY() + (selected ? 3 : 2);
            int bottom = getBottom() - (selected ? 3 : 2);
            int barWidth = group ? 3 : 9;
            g.fill(x, top, x + barWidth, bottom, applyAlpha(bar, style.opacity() * alpha));
            if (!group) MailDecor.diamond(g, x + barWidth / 2, getY() + getHeight() / 2,
                    2, applyAlpha(diamond, style.opacity() * alpha));
        }
    }

    /** Faint gold lattice fading in toward the right edge of the selected (ivory) task row. */
    private void renderSelectedOrnament(GuiGraphics g, float opacity) {
        int left = getX() + getWidth() * 11 / 20;
        int right = getRight() - 6;
        if (right - left < 16 || getHeight() < 14) return;
        g.enableScissor(Math.max(left, getX() + 3), getY() + 3, getRight() - 3, getBottom() - 3);
        int spacing = 8;
        for (int column = 0; left + column * spacing <= right + spacing; column++) {
            int cx = left + column * spacing;
            float strength = Math.clamp((cx - left) / (float) Math.max(1, right - left), 0, 1);
            int color = applyAlpha(0x30C9A464, opacity * (0.35F + 0.65F * strength));
            for (int cy = getY() + 4 + (column % 2) * spacing / 2; cy < getBottom(); cy += spacing)
                MailDecor.diamond(g, cx, cy, 2, color);
        }
        g.disableScissor();
    }

    public static double arrowOffset(long nanos, boolean reducedMotion) {
        return reducedMotion ? 0 : (1 - Math.cos((nanos % 2_800_000_000L) / 2_800_000_000.0 * Math.PI * 2)) * 2;
    }

    public void renderSelectionArrow(GuiGraphics g, long nanos) {
        if (group || !selected || !visible) return;
        int tip = getX() - 2 - (int) Math.round(arrowOffset(nanos, ClientPreferences.get().reducedMotion));
        int cy = getY() + getHeight() / 2;
        for (int row = -5; row <= 5; row++) {
            int right = tip - Math.abs(row);
            if (right >= tip - 5) g.fill(tip - 6, cy + row, right + 1, cy + row + 1, 0x30FFFFFF);
        }
        for (int row = -3; row <= 3; row++)
            g.fill(tip - 4, cy + row, tip - Math.abs(row), cy + row + 1, 0xFFFFFFFF);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, ButtonStyle style, int text) {
        if ((text >>> 24) < 4) return;
        var font = Minecraft.getInstance().font;
        int inset = Math.min(Math.max(6, style.padding()), Math.max(0, (getWidth() - 24) / 2));
        int left = getX() + inset;
        if (accent.equals("gold") || accent.equals("blue")) left = Math.max(left, getX() + (group ? 9 : 16));
        if (leadingAction != null) {
            int centerX = left + 7;
            int centerY = getY() + getHeight() / 2;
            graphics.fill(centerX - 4, centerY, centerX + 5, centerY + 1, text);
            graphics.fill(centerX, centerY - 4, centerX + 1, centerY + 5, text);
            left += 18;
        }
        String leadingImage = leadingIcon.image(tracked);
        if (leadingIcon.visible() && !leadingImage.isBlank()) {
            int baseY = getY() + (getHeight() - leadingIcon.height()) / 2;
            QuestIcons.render(graphics, leadingImage, left + leadingIcon.offsetX(),
                    baseY + leadingIcon.offsetY(), leadingIcon.width(), leadingIcon.height(), leadingIcon.alpha());
            left += leadingIcon.width() + Math.abs(leadingIcon.offsetX()) + 4;
        }
        int trailingSpace = claimable
                ? Math.max(24, trailingIcon.width() + Math.abs(trailingIcon.offsetX()) + 8)
                : trailingIcon.visible()
                ? trailingIcon.width() + Math.abs(trailingIcon.offsetX()) + 8 : 4;
        if (locked) trailingSpace = Math.max(trailingSpace, 24);
        int textRight = getRight() - inset - trailingSpace;
        int available = Math.max(1, textRight - left);
        int top = getY() + (getHeight() - font.lineHeight) / 2;
        if (group && !description.isBlank()) top = getY() + 7;
        String secondLine = group ? "" : subtitleText();
        boolean twoLines = !secondLine.isBlank() && getHeight() >= font.lineHeight * 2 + 6;
        if (twoLines) top = getY() + (getHeight() - font.lineHeight * 2 - 2) / 2;
        graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), available),
                left, top, text, style.textShadow());
        if (twoLines) {
            int muted = selected ? 0x8A90A0 : completed ? 0x9AA1AB : 0xB4BCC8;
            graphics.drawString(font, font.plainSubstrByWidth(secondLine, available), left,
                    top + font.lineHeight + 2, (text & 0xFF000000) | muted, false);
        }
        if (group) {
            if (!description.isBlank()) {
                int muted = (text & 0xFF000000) | ((text >>> 16 & 255) * 4 / 5 << 16)
                        | ((text >>> 8 & 255) * 4 / 5 << 8) | ((text & 255) * 4 / 5);
                graphics.drawString(font, font.plainSubstrByWidth(description, available), left,
                        top + font.lineHeight + 4, muted, style.textShadow());
            }
        }
        renderTrailing(graphics, text, inset);
        if (notificationBadge() && !group) {
            int right = getRight() - 9, badgeTop = getY() + 3;
            MailDecor.circle(graphics, right - 1, badgeTop - 1, 8, 0xFFF7F4EE);
            MailDecor.circle(graphics, right, badgeTop, 6, 0xFFEF4052);
            MailDecor.circle(graphics, right + 1, badgeTop + 1, 2, 0xFFFFB4BC);
        }
    }

    private void renderTrailing(GuiGraphics graphics, int text, int inset) {
        if (locked) {
            QuestStatusIcons.render(graphics, "locked", getRight() - inset - 16,
                    getY() + (getHeight() - 16) / 2, 16);
            return;
        }
        if (completed && !group) {
            int size = 16;
            int x = getRight() - inset - size + (trailingIcon.width() - size) / 2 + trailingIcon.offsetX();
            int y = getY() + (getHeight() - size) / 2 + trailingIcon.offsetY();
            QuestStatusIcons.renderCheckbox(graphics, true, x, y, size, selected,
                    trailingIcon.alpha() * alpha);
            claimIconSize = 0;
            return;
        }
        if (claimable && !group) {
            int size = 20;
            claimIconX = getRight() - inset - size + trailingIcon.offsetX();
            claimIconY = getY() + (getHeight() - size) / 2 + trailingIcon.offsetY();
            claimIconSize = size;
            boolean pressed = claimPressed && isHovered();
            int outer = pressed ? 0xFFFFC1A8 : 0xFF783D39;
            int background = pressed ? 0xFFFF8065 : 0xFFB55549;
            MailDecor.circle(graphics, claimIconX - 1, claimIconY - 1, size + 2,
                    applyAlpha(outer, trailingIcon.alpha() * alpha));
            MailDecor.circle(graphics, claimIconX, claimIconY, size,
                    applyAlpha(background, trailingIcon.alpha() * alpha));
            JournalNavButton.drawIcon(graphics, "gift", claimIconX + 3, claimIconY + 3, size - 6,
                    applyAlpha(0xFFFFF8ED, trailingIcon.alpha() * alpha));
            return;
        }
        claimIconSize = 0;
        if (!trailingIcon.visible()) return;
        String image = trailingIcon.image(tracked);
        int baseX = getRight() - inset - trailingIcon.width();
        int baseY = getY() + (getHeight() - trailingIcon.height()) / 2;
        int drawX = baseX + trailingIcon.offsetX();
        int drawY = baseY + trailingIcon.offsetY();
        if (!image.isBlank()) {
            QuestIcons.render(graphics, image, drawX, drawY, trailingIcon.width(),
                    trailingIcon.height(), trailingIcon.alpha());
        } else if (group) {
            int cx = drawX + trailingIcon.width() / 2;
            int cy = drawY + trailingIcon.height() / 2;
            int color = applyAlpha(text, trailingIcon.alpha());
            for (int i = 0; i < 4; i++) {
                if (expanded) {
                    graphics.fill(cx - 3 + i, cy - 2 + i, cx + 4 - i, cy - 1 + i, color);
                } else {
                    graphics.fill(cx - 2 + i, cy - 3 + i, cx - 1 + i, cy + 4 - i, color);
                }
            }
        } else {
            int size = Math.min(trailingIcon.width(), trailingIcon.height());
            // Waves may expand to the row edges, but never bleed into adjacent rows or titles.
            graphics.enableScissor(Math.max(getX(), drawX - size / 2), getY(), getRight(), getBottom());
            QuestTrackingIcons.render(graphics, trackingStyle(), tracked,
                    drawX + (trailingIcon.width() - size) / 2, drawY + (trailingIcon.height() - size) / 2,
                    size, trailingIcon.alpha() * alpha, ClientPreferences.get().reducedMotion);
            graphics.disableScissor();
        }
    }

    public boolean claimIconContains(double mouseX, double mouseY) {
        return claimable && !group && !locked && claimIconSize > 0
                && mouseX >= claimIconX && mouseX < claimIconX + claimIconSize
                && mouseY >= claimIconY && mouseY < claimIconY + claimIconSize;
    }

    @Override
    public void setSelectionHighlighted(boolean value) {
        selected = value;
        super.setSelectionHighlighted(value);
    }

    public boolean runLeadingAction(double mouseX) {
        if (leadingAction == null || mouseX >= getX() + 24) return false;
        leadingAction.run();
        return true;
    }

    private static int applyAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24;
        return color & 0x00FFFFFF | Math.clamp(Math.round(sourceAlpha * alpha), 0, 255) << 24;
    }
}
