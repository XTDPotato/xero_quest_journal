package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class StyledButton extends Button {
    private final ButtonStyleSheet styleSheet;
    private final String styleId;
    private final Set<String> classes;
    private boolean pressed;
    private boolean pointerPressed;
    private boolean selectionHighlighted;
    private final ButtonStyle[] cachedStyles = new ButtonStyle[32];
    private int cachedRevision = -1;
    private ItemStack icon = ItemStack.EMPTY;
    private String textureIcon;
    private String leadingIcon;
    private ButtonStyleSheet extra;
    private ButtonStyleSheet defaults;
    private Integer clipLeft;
    private Integer clipTop;
    private Integer clipRight;
    private Integer clipBottom;
    private boolean hoverMotion;
    private final ButtonActionAnimation motion = new ButtonActionAnimation();
    private String actionMotionKey;
    // Kept for compatibility with existing UI extensions and smoke tests.
    private float hoverProgress;
    private float liftProgress;

    public StyledButton hoverMotion() {
        hoverMotion = true;
        return this;
    }

    /** Share mailbox motion without replacing this button's authored appearance. */
    public StyledButton actionMotion(String actionKey) {
        actionMotionKey = actionKey;
        return hoverMotion();
    }

    protected float hoverProgress() { return motion.hoverProgress(); }
    protected float hoverScale() {
        // Hover feedback is expressed by the authored border/style now. Keeping the
        // widget at its layout bounds prevents neighboring controls from moving.
        return 0F;
    }
    protected String motionShape() { return "rectangle"; }
    protected int motionCornerRadius() { return 0; }
    protected boolean pressed() { return pressed; }

    public void copyMotionFrom(StyledButton previous) {
        if (previous == null) return;
        motion.copyFrom(previous.motion);
        hoverProgress = previous.hoverProgress;
        liftProgress = previous.liftProgress;
    }

    public StyledButton(int x, int y, int width, int height, Component message, OnPress onPress,
                        ButtonStyleSheet styleSheet, String styleId, String... classes) {
        super(x, y, width, height, message, onPress, narration -> narration.get());
        this.styleSheet = styleSheet;
        this.styleId = styleId;
        this.classes = Set.of(classes);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        styleSheet.refreshIfChanged();
        isHovered = isMouseOver(mouseX, mouseY);
        if (clipLeft != null) {
            graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        }
        if (hoverMotion) {
            motion.update(isHovered && active, pressed());
            hoverProgress = motion.hoverProgress();
            liftProgress = motion.liftProgress();
            motion.push(graphics, getX(), getY(), getWidth(), getHeight(), hoverScale());
        }
        int state = (isHovered() && active ? 1 : 0) | (pressed() && active ? 2 : 0)
                | (!active ? 4 : 0) | (isFocused() ? 8 : 0) | (selectionHighlighted ? 16 : 0);
        if (cachedRevision != styleSheet.revision()) {
            java.util.Arrays.fill(cachedStyles, null);
            cachedRevision = styleSheet.revision();
        }
        ButtonStyle style = cachedStyles[state];
        if (style == null) {
            style = styleSheet.resolveWithDefaults(defaults, styleId, classes, (state & 1) != 0, (state & 2) != 0,
                    (state & 4) != 0, (state & 8) != 0);
            if (extra != null) {
                style = extra.resolveFrom(style, styleId, classes, (state & 1) != 0, (state & 2) != 0,
                        (state & 4) != 0, (state & 8) != 0);
            }
            if ((state & 16) != 0) {
                style = selectedStyle(style);
                style = styleSheet.resolveSelectedFrom(style, defaults, styleId, classes, (state & 1) != 0, (state & 2) != 0,
                        (state & 4) != 0, (state & 8) != 0);
                if (extra != null) {
                    style = extra.resolveSelectedFrom(style, styleId, classes, (state & 1) != 0, (state & 2) != 0,
                            (state & 4) != 0, (state & 8) != 0);
                }
            }
            cachedStyles[state] = style;
        }
        int alpha = Math.max(0, Math.min(255, Math.round(style.opacity() * this.alpha * 255.0F)));
        int background = withAlpha(style.backgroundColor(), alpha);
        int border = withAlpha(style.borderColor(), alpha);
        int text = withAlpha(style.textColor(), alpha);

        renderSurface(graphics, style, background, border);
        if (isHovered() && active) {
            renderHoverOutline(graphics, withAlpha(0xFFFFE6B4, alpha));
        }
        if (actionMotionKey != null) motion.ripple(graphics, actionMotionKey, getX(), getY(),
                getWidth(), getHeight(), motionShape(), motionCornerRadius(), style.opacity() * this.alpha);
        renderContent(graphics, style, text);
        if (hoverMotion) graphics.pose().popPose();
        if (clipLeft != null) {
            graphics.disableScissor();
        }
    }

    protected void renderSurface(GuiGraphics graphics, ButtonStyle style, int background, int border) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        int borderWidth = Math.min(style.borderWidth(), Math.min(getWidth(), getHeight()) / 2);
        for (int edge = 0; edge < borderWidth; edge++) {
            graphics.fill(getX() + edge, getY() + edge, getX() + getWidth() - edge, getY() + edge + 1, border);
            graphics.fill(getX() + edge, getY() + getHeight() - edge - 1, getX() + getWidth() - edge, getY() + getHeight() - edge, border);
            graphics.fill(getX() + edge, getY() + edge, getX() + edge + 1, getY() + getHeight() - edge, border);
            graphics.fill(getX() + getWidth() - edge - 1, getY() + edge, getX() + getWidth() - edge, getY() + getHeight() - edge, border);
        }

    }

    protected void renderHoverOutline(GuiGraphics graphics, int color) {
        switch (motionShape()) {
            case "circle" -> renderCircleOutline(graphics, getX(), getY(), getWidth(), getHeight(), color);
            case "pill" -> renderRoundedOutline(graphics, getX(), getY(), getWidth(), getHeight(),
                    Math.min(getWidth(), getHeight()) / 2, color);
            case "rounded" -> renderRoundedOutline(graphics, getX(), getY(), getWidth(), getHeight(),
                    motionCornerRadius(), color);
            default -> graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), color);
        }
    }

    private static void renderCircleOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int size = Math.min(width, height);
        if (size <= 0) return;
        int left = x + (width - size) / 2, top = y + (height - size) / 2;
        double radius = size / 2.0;
        for (int row = 0; row < size; row++) {
            double dy = row + .5 - radius;
            int inset = Math.max(0, (int) Math.ceil(radius
                    - Math.sqrt(Math.max(0, radius * radius - dy * dy)) - .5));
            if (row == 0 || row == size - 1)
                graphics.fill(left + inset, top + row, left + size - inset, top + row + 1, color);
            else {
                graphics.fill(left + inset, top + row, left + inset + 1, top + row + 1, color);
                graphics.fill(left + size - inset - 1, top + row, left + size - inset, top + row + 1, color);
            }
        }
    }

    private static void renderRoundedOutline(GuiGraphics graphics, int x, int y, int width, int height,
                                             int radius, int color) {
        radius = Math.min(radius, Math.min(width, height) / 2);
        for (int row = 0; row < height; row++) {
            double dy = row < radius ? radius - row - .5
                    : row >= height - radius ? row - (height - radius) + .5 : 0;
            int inset = dy == 0 ? 0 : Math.max(0, Math.min(radius,
                    (int) Math.ceil(radius - Math.sqrt(Math.max(0, radius * (double) radius - dy * dy)))));
            if (row == 0 || row == height - 1)
                graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            else {
                graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
                graphics.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
            }
        }
    }

    public StyledButton defaultCss(String css) {
        defaults = ButtonStyleSheet.parse(css);
        cachedRevision = -1;
        return this;
    }

    protected void renderContent(GuiGraphics graphics, ButtonStyle style, int text) {
        if (textureIcon != null) {
            QuestIcons.render(graphics, textureIcon, getX() + (getWidth() - 16) / 2,
                    getY() + (getHeight() - 16) / 2, 16);
        } else if (!icon.isEmpty()) {
            graphics.renderItem(icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
        } else if ((text >>> 24) >= 4) {
            int inset = Math.min(Math.max(2, style.padding()), Math.max(0, (getWidth() - 1) / 2));
            var font = Minecraft.getInstance().font;
            int iconSpace = leadingIcon == null ? 0 : 20;
            String label = font.plainSubstrByWidth(getMessage().getString(),
                    Math.max(1, getWidth() - 2 * inset - iconSpace));
            int contentWidth = font.width(label) + iconSpace;
            int contentX = getX() + Math.max(inset, (getWidth() - contentWidth) / 2);
            if (leadingIcon != null) {
                QuestIcons.render(graphics, leadingIcon, contentX, getY() + (getHeight() - 16) / 2, 16);
            }
            graphics.drawString(font, label, contentX + iconSpace,
                    getY() + (getHeight() - font.lineHeight) / 2, text, style.textShadow());
        }
    }

    protected ButtonStyle selectedStyle(ButtonStyle style) {
        return style;
    }

    @Override
    public void onPress() {
        if (active && actionMotionKey != null) motion.click();
        super.onPress();
    }

    public void setSelectionHighlighted(boolean value) {
        selectionHighlighted = value;
    }

    public boolean selectionHighlighted() {
        return selectionHighlighted;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY)
                && (clipLeft == null || mouseX >= clipLeft && mouseX < clipRight
                && mouseY >= clipTop && mouseY < clipBottom);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        pressed = true;
        pointerPressed = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = button == 0 && pointerPressed;
        boolean activate = handled && active && visible && isMouseOver(mouseX, mouseY);
        if (button == 0) {
            pressed = false;
            pointerPressed = false;
        }
        if (activate) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onPress();
        }
        return handled;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            pressed = false;
            pointerPressed = false;
        }
    }

    public StyledButton icon(ItemStack value) {
        this.icon = value;
        this.textureIcon = null;
        return this;
    }

    public StyledButton textureIcon(String value) {
        this.textureIcon = value == null || value.isBlank() ? null : value;
        this.icon = ItemStack.EMPTY;
        return this;
    }

    public StyledButton leadingIcon(String value) {
        this.leadingIcon = value == null || value.isBlank() ? null : value;
        return this;
    }

    public StyledButton clipTo(int left, int top, int right, int bottom) {
        clipLeft = left;
        clipTop = top;
        clipRight = right;
        clipBottom = bottom;
        return this;
    }

    public StyledButton extraCss(String css) {
        try {
            extra = ButtonStyleSheet.parse(css);
        } catch (IllegalArgumentException invalid) {
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn("Invalid button CSS for {}", styleId, invalid);
        }
        cachedRevision = -1;
        return this;
    }

    public String styleId() {
        return styleId;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (active && visible && CommonInputs.selected(key)) {
            pressed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (pressed && !pointerPressed && CommonInputs.selected(key)) {
            pressed = false;
            if (active && visible) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                onPress();
            }
            return true;
        }
        return false;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (((color >>> 24) * alpha / 255) << 24);
    }
}
