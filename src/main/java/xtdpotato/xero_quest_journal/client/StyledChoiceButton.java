package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** A styled selector that opens a menu instead of cycling values on each click. */
public final class StyledChoiceButton<T> extends StyledButton {
    private static final int SCREEN_MARGIN = 8;

    private final List<T> values;
    private final Function<T, Component> label;
    private final Function<T, String> icon;
    private final Consumer<T> changed;
    private final Component caption;
    private final ButtonStyleSheet styles;
    private int index;
    private int highlighted;
    private double scroll, targetScroll;
    private long lastFrame;
    private boolean dragging, thumbDragging, moved;
    private double dragY, dragScroll, thumbOffset;
    private int pressedOption = -1;
    private boolean open;

    public StyledChoiceButton(int x, int y, int width, int height, Component caption,
                              List<T> values, T initial, Function<T, Component> label, Consumer<T> changed,
                              ButtonStyleSheet styles, String id) {
        this(x, y, width, height, caption, values, initial, label, changed, styles, id, null);
    }

    public StyledChoiceButton(int x, int y, int width, int height, Component caption,
                              List<T> values, T initial, Function<T, Component> label, Consumer<T> changed,
                              ButtonStyleSheet styles, String id, Function<T, String> icon) {
        super(x, y, width, height, Component.empty(),
                button -> ((StyledChoiceButton<?>) button).toggleMenu(), styles, id);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("A selector needs at least one value");
        }
        this.values = List.copyOf(values);
        this.label = label;
        this.icon = icon;
        this.changed = changed;
        this.caption = caption;
        this.styles = styles;
        index = Math.max(0, values.indexOf(initial));
        highlighted = index;
        updateLabel();
    }

    private void updateLabel() {
        setMessage(label.apply(values.get(index)));
        setTooltip(Tooltip.create(caption.copy().append(": ").append(getMessage())));
    }

    private void toggleMenu() {
        open = !open;
        highlighted = index;
        ensureHighlightedVisible();
    }

    public boolean isOpen() {
        return open;
    }

    public T value() {
        return values.get(index);
    }

    public void select(T value) {
        int next = values.indexOf(value);
        if (next >= 0) {
            choose(next);
        }
    }

    void closeMenu() {
        open = false;
        dragging = thumbDragging = false;
    }

    private void choose(int next) {
        index = Math.clamp(next, 0, values.size() - 1);
        highlighted = index;
        closeMenu();
        updateLabel();
        changed.accept(values.get(index));
    }

    private int rowHeight() {
        return Math.max(18, getHeight());
    }

    private int screenWidth() {
        return Minecraft.getInstance().screen == null
                ? getRight() + SCREEN_MARGIN : Minecraft.getInstance().screen.width;
    }

    private int screenHeight() {
        return Minecraft.getInstance().screen == null
                ? getBottom() + SCREEN_MARGIN : Minecraft.getInstance().screen.height;
    }

    private int visibleRows() {
        return Math.min(values.size(), Math.max(1, (screenHeight() - SCREEN_MARGIN * 2) / rowHeight()));
    }

    private int popupWidth() {
        var font = Minecraft.getInstance().font;
        int content = values.stream().map(label).mapToInt(font::width).max().orElse(1) + 20
                + (icon == null ? 0 : 20);
        return Math.min(screenWidth() - SCREEN_MARGIN * 2, Math.max(getWidth(), content));
    }

    private int popupX() {
        return Math.clamp(getX(), SCREEN_MARGIN, Math.max(SCREEN_MARGIN,
                screenWidth() - popupWidth() - SCREEN_MARGIN));
    }

    private int popupY() {
        int height = visibleRows() * rowHeight();
        if (getBottom() + height <= screenHeight() - SCREEN_MARGIN) {
            return getBottom();
        }
        if (getY() - height >= SCREEN_MARGIN) {
            return getY() - height;
        }
        return Math.clamp(getBottom(), SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenHeight() - height - SCREEN_MARGIN));
    }

    boolean popupContains(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        int left = popupX();
        int top = popupY();
        return mouseX >= left && mouseX < left + popupWidth()
                && mouseY >= top && mouseY < top + visibleRows() * rowHeight();
    }

    private int optionAt(double mouseX, double mouseY) {
        if (!popupContains(mouseX, mouseY)) {
            return -1;
        }
        return (int) ((mouseY - popupY() + scroll) / rowHeight());
    }

    private void ensureHighlightedVisible() {
        double top = highlighted * rowHeight();
        if (top < targetScroll) targetScroll = top;
        else if (top + rowHeight() > targetScroll + popupHeight())
            targetScroll = top + rowHeight() - popupHeight();
        targetScroll = Math.clamp(targetScroll, 0, maxScroll());
        if (lastFrame == 0) scroll = targetScroll;
    }

    private int popupHeight() { return visibleRows() * rowHeight(); }
    AnimatedQuestScreen.Viewport popupBounds() {
        return new AnimatedQuestScreen.Viewport(popupX() - 1, popupY() - 1,
                popupX() + popupWidth() + 1, popupY() + popupHeight() + 1);
    }
    private int maxScroll() { return Math.max(0, values.size() * rowHeight() - popupHeight()); }
    private int thumbHeight() { return Math.max(8, (popupHeight() - 4) * visibleRows() / values.size()); }
    private double thumbY() {
        return popupY() + 2 + (popupHeight() - 4 - thumbHeight()) * scroll / Math.max(1, maxScroll());
    }
    private void dragThumb(double y) {
        scroll = targetScroll = Math.clamp((y - popupY() - 2 - thumbOffset) * maxScroll()
                / Math.max(1, popupHeight() - 4 - thumbHeight()), 0, maxScroll());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, ButtonStyle style, int text) {
        if ((text >>> 24) < 4) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int inset = Math.min(Math.max(2, style.padding()), Math.max(0, (getWidth() - 1) / 2));
        int arrowWidth = font.width("\u25be") + 5;
        int iconWidth = icon == null ? 0 : 19;
        int textWidth = Math.max(1, getWidth() - inset * 2 - arrowWidth - iconWidth);
        String value = font.plainSubstrByWidth(getMessage().getString(), textWidth);
        int left = getX() + inset + iconWidth + Math.max(0, (textWidth - font.width(value)) / 2);
        if (icon != null) QuestIcons.render(graphics, icon.apply(values.get(index)),
                getX() + inset, getY() + (getHeight() - 16) / 2, 16);
        graphics.drawString(font, value, left, getY() + (getHeight() - font.lineHeight) / 2,
                text, style.textShadow());
        graphics.drawString(font, open ? "\u25b4" : "\u25be", getRight() - inset - arrowWidth + 5,
                getY() + (getHeight() - font.lineHeight) / 2, text, style.textShadow());
    }

    void renderPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!open || !visible) {
            return;
        }
        long now = System.nanoTime();
        double blend = lastFrame == 0 ? 1 : 1 - Math.exp(-18 * Math.min(.1, (now - lastFrame) / 1e9));
        lastFrame = now;
        targetScroll = Math.clamp(targetScroll, 0, maxScroll());
        scroll += (targetScroll - scroll) * blend;
        if (Math.abs(targetScroll - scroll) < .05) scroll = targetScroll;
        int hovered = optionAt(mouseX, mouseY);
        if (hovered >= 0 && hovered < values.size()) {
            highlighted = hovered;
        }
        int left = popupX();
        int top = popupY();
        int width = popupWidth();
        int rows = visibleRows();
        int rowHeight = rowHeight();
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.fill(left - 1, top - 1, left + width + 1, top + rows * rowHeight + 1, 0xFF9DB4D2);
        graphics.enableScissor(left, top, left + width, top + popupHeight());
        int first = (int) scroll / rowHeight;
        for (int option = first; option < Math.min(values.size(), first + rows + 1); option++) {
            boolean isHovered = option == hovered;
            boolean selected = option == index;
            ButtonStyle style = styles.resolve("context", Set.of("choice-option"),
                    isHovered, false, false, option == highlighted);
            if (selected) {
                style = styles.resolveSelectedFrom(style, "context", Set.of("choice-option"),
                        isHovered, false, false, option == highlighted);
            }
            int y = top + option * rowHeight - (int) Math.round(scroll);
            graphics.fill(left, y, left + width, y + rowHeight, style.backgroundColor());
            int border = selected ? 0xFFF3D48B : style.borderColor();
            graphics.fill(left, y + rowHeight - 1, left + width, y + rowHeight, border);
            int iconWidth = icon == null ? 0 : 20;
            String value = font.plainSubstrByWidth(label.apply(values.get(option)).getString(),
                    Math.max(1, width - 14 - iconWidth));
            if (icon != null) QuestIcons.render(graphics, icon.apply(values.get(option)),
                    left + 4, y + (rowHeight - 16) / 2, 16);
            graphics.drawString(font, value, left + 6 + iconWidth, y + (rowHeight - font.lineHeight) / 2,
                    style.textColor(), style.textShadow());
        }
        graphics.disableScissor();
        if (values.size() > rows) {
            int trackTop = top + 2;
            int trackHeight = rows * rowHeight - 4;
            int thumbHeight = thumbHeight();
            int thumbY = (int) thumbY();
            graphics.fill(left + width - 4, trackTop, left + width - 2, trackTop + trackHeight, 0xFF303741);
            graphics.fill(left + width - 4, thumbY, left + width - 2, thumbY + thumbHeight, 0xFFF3D48B);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0) {
            return false;
        }
        if (open && popupContains(mouseX, mouseY)) {
            dragging = true;
            moved = false;
            dragY = mouseY;
            dragScroll = scroll;
            targetScroll = scroll;
            pressedOption = optionAt(mouseX, mouseY);
            thumbDragging = maxScroll() > 0 && mouseX >= popupX() + popupWidth() - 8;
            if (thumbDragging) {
                thumbOffset = mouseY >= thumbY() && mouseY < thumbY() + thumbHeight()
                        ? mouseY - thumbY() : thumbHeight() / 2.;
                dragThumb(mouseY);
            }
            return true;
        }
        if (open && !isMouseOver(mouseX, mouseY)) {
            closeMenu();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!open) return false;
        if (popupContains(mouseX, mouseY))
            targetScroll = Math.clamp(targetScroll - vertical * rowHeight() * 2.5, 0, maxScroll());
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!open) return false;
        if (button == 0 && dragging) {
            moved |= Math.abs(y - dragY) >= 4;
            if (thumbDragging) dragThumb(y);
            else if (moved) scroll = targetScroll = Math.clamp(dragScroll + dragY - y, 0, maxScroll());
        }
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (!open) return super.mouseReleased(x, y, button);
        if (button == 0 && dragging) {
            boolean select = !moved && !thumbDragging && popupContains(x, y)
                    && pressedOption == optionAt(x, y) && pressedOption >= 0 && pressedOption < values.size();
            dragging = thumbDragging = false;
            if (select) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                choose(pressedOption);
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (active && visible && isFocused() && open) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeMenu();
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                highlighted = Math.floorMod(highlighted + (key == GLFW.GLFW_KEY_UP ? -1 : 1), values.size());
                ensureHighlightedVisible();
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                highlighted = key == GLFW.GLFW_KEY_HOME ? 0 : values.size() - 1;
                ensureHighlightedVisible();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
                choose(highlighted);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            closeMenu();
        }
    }

    @Override
    protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.button", caption.copy().append(": ").append(getMessage()));
    }
}
