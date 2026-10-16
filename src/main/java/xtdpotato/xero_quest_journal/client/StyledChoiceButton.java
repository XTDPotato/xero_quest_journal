package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
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
    private boolean searchable;
    private net.minecraft.client.gui.components.EditBox search;
    private String query = "";
    private List<Integer> filtered;
    private final java.util.Map<Integer, String> searchIndexes = new java.util.HashMap<>();
    private Set<T> distinctOptions = Set.of();
    private Map<T, String> optionGroups = Map.of();

    public StyledChoiceButton<T> distinctOptions(java.util.Collection<T> values) {
        distinctOptions = Set.copyOf(values);
        return this;
    }

    public StyledChoiceButton<T> groupedOptions(Map<T, String> groups) {
        optionGroups = Map.copyOf(groups);
        return this;
    }

    public StyledChoiceButton<T> searchable() {
        searchable = true;
        return this;
    }
    private List<Integer> options() {
        if (filtered == null) filtered = java.util.stream.IntStream.range(0, values.size()).boxed().toList();
        return filtered;
    }
    private int selectedOption() { return options().indexOf(index); }
    private int searchHeight() { return searchable ? 24 : 0; }
    private void filter(String value) {
        query = value;
        String normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
        String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        filtered = java.util.stream.IntStream.range(0, values.size()).filter(candidate -> {
            String index = searchIndexes.computeIfAbsent(candidate,
                    i -> QuestIcons.searchIndex(String.valueOf(values.get(i)), label.apply(values.get(i)).getString()));
            for (String term : terms) if (!index.contains(term)) return false;
            return true;
        }).boxed().toList();
        highlighted = 0;
        scroll = targetScroll = 0;
    }

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
        if (searchable && open) {
            query = "";
            filtered = null;
            search = new net.minecraft.client.gui.components.EditBox(Minecraft.getInstance().font,
                    0, 0, 100, 18, Component.translatable("screen.xero_quest_journal.choice.search"));
            search.setMaxLength(128);
            search.setHint(Component.translatable("screen.xero_quest_journal.choice.search"));
            search.setResponder(this::filter);
            search.setFocused(true);
        }
        highlighted = Math.max(0, selectedOption());
        ensureHighlightedVisible();
    }

    public boolean isOpen() {
        return open;
    }
    void restoreSearchFocus() { if (searchable && search != null && open) search.setFocused(true); }

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
        return Math.max(1, Math.min(options().size(), Math.max(1,
                (screenHeight() - SCREEN_MARGIN * 2 - searchHeight()) / rowHeight())));
    }

    private int popupWidth() {
        var font = Minecraft.getInstance().font;
        int content = values.stream().map(label).mapToInt(font::width).max().orElse(1) + 40;
        return Math.min(screenWidth() - SCREEN_MARGIN * 2, Math.max(getWidth(), content));
    }

    private int popupX() {
        return Math.clamp(getX(), SCREEN_MARGIN, Math.max(SCREEN_MARGIN,
                screenWidth() - popupWidth() - SCREEN_MARGIN));
    }

    private int popupY() {
        int height = visibleRows() * rowHeight() + searchHeight();
        if (getBottom() + height <= screenHeight() - SCREEN_MARGIN) {
            return getBottom() + searchHeight();
        }
        if (getY() - height >= SCREEN_MARGIN) {
            return getY() - height + searchHeight();
        }
        return Math.clamp(getBottom(), SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenHeight() - height - SCREEN_MARGIN)) + searchHeight();
    }

    boolean popupContains(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        int left = popupX();
        int top = popupY();
        return mouseX >= left && mouseX < left + popupWidth()
                && mouseY >= top - searchHeight() && mouseY < top + visibleRows() * rowHeight();
    }

    private int optionAt(double mouseX, double mouseY) {
        if (!popupContains(mouseX, mouseY) || mouseY < popupY()) {
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
        return new AnimatedQuestScreen.Viewport(popupX() - 1, popupY() - searchHeight() - 1,
                popupX() + popupWidth() + 1, popupY() + popupHeight() + 1);
    }
    private int maxScroll() { return Math.max(0, options().size() * rowHeight() - popupHeight()); }
    private int thumbHeight() { return Math.max(8, (popupHeight() - 4) * visibleRows() / Math.max(1, options().size())); }
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
        if (hovered >= 0 && hovered < options().size()) {
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
        graphics.fill(left - 1, top - searchHeight() - 1, left + width + 1, top + rows * rowHeight + 1, 0xFF9DB4D2);
        if (searchable && search != null) {
            search.setX(left + 3);
            search.setY(top - searchHeight() + 3);
            search.setWidth(width - 6);
            search.render(graphics, mouseX, mouseY, 0);
        }
        graphics.enableScissor(left, top, left + width, top + popupHeight());
        if (options().isEmpty()) {
            graphics.fill(left, top, left + width, top + popupHeight(), 0xFF202832);
            graphics.drawString(font, Component.translatable("screen.xero_quest_journal.choice.empty"),
                    left + 6, top + 5, 0xFF9CAEBB, false);
        }
        int first = (int) scroll / rowHeight;
        for (int option = first; option < Math.min(options().size(), first + rows + 1); option++) {
            int actual = options().get(option);
            boolean isHovered = option == hovered;
            boolean selected = actual == index;
            ButtonStyle style = styles.resolve("context", Set.of("choice-option"),
                    isHovered, false, false, option == highlighted);
            if (selected) {
                style = styles.resolveSelectedFrom(style, "context", Set.of("choice-option"),
                        isHovered, false, false, option == highlighted);
            }
            int y = top + option * rowHeight - (int) Math.round(scroll);
            boolean distinct = distinctOptions.contains(values.get(actual));
            boolean distinctStart = distinct && (option == 0
                    || !distinctOptions.contains(values.get(options().get(option - 1))));
            String group = optionGroup(values.get(actual));
            boolean groupStart = group != null && (option == 0
                    || !group.equals(optionGroup(values.get(options().get(option - 1)))));
            int background = distinct
                    ? isHovered ? 0xFF7798C1 : selected ? 0xFF607FA6 : 0xFF4B5D79
                    : style.backgroundColor();
            graphics.fill(left, y, left + width, y + rowHeight, background);
            if (distinctStart) graphics.fill(left, y, left + width, y + 2, 0xFFB9D6F2);
            int border = selected ? 0xFFF3D48B : distinct ? 0xFF8EABC9 : style.borderColor();
            graphics.fill(left, y + rowHeight - 1, left + width, y + rowHeight, border);
            if (group != null) {
                int groupColor = optionGroupColor(group);
                graphics.fill(left + 2, y, left + 4, y + rowHeight, groupColor);
                if (groupStart) graphics.fill(left + 4, y, left + width - 2, y + 1, groupColor);
            }
            int iconWidth = 20;
            String value = font.plainSubstrByWidth(label.apply(values.get(actual)).getString(),
                    Math.max(1, width - 14 - iconWidth));
            int iconX = left + 7, iconY = y + (rowHeight - 14) / 2;
            String texture = icon == null ? null : icon.apply(values.get(actual));
            if (texture != null && !texture.isBlank())
                QuestIcons.render(graphics, texture, iconX, y + (rowHeight - 16) / 2, 16);
            else drawOptionGlyph(graphics, values.get(actual), iconX, iconY, optionGroupColor(group));
            graphics.drawString(font, value, left + 6 + iconWidth, y + (rowHeight - font.lineHeight) / 2,
                    style.textColor(), style.textShadow());
        }
        graphics.disableScissor();
        if (options().size() > rows) {
            int trackTop = top + 2;
            int trackHeight = rows * rowHeight - 4;
            int thumbHeight = thumbHeight();
            int thumbY = (int) thumbY();
            graphics.fill(left + width - 4, trackTop, left + width - 2, trackTop + trackHeight, 0xFF303741);
            graphics.fill(left + width - 4, thumbY, left + width - 2, thumbY + thumbHeight, 0xFFF3D48B);
        }
        graphics.pose().popPose();
    }

    private String optionGroup(T option) {
        String group = optionGroups.get(option);
        if (group != null && !group.isBlank()) return group;
        return distinctOptions.contains(option) ? "logic" : "options";
    }

    private static int optionGroupColor(String group) {
        return switch (group) {
            case "manual", "quest" -> 0xFF83C6B3;
            case "inventory" -> 0xFF9FCB86;
            case "combat" -> 0xFFE17C72;
            case "world" -> 0xFF77BBD0;
            case "events" -> 0xFFE7BF70;
            case "logic", "distinct" -> 0xFFB99AE0;
            default -> 0xFF9FB5D2;
        };
    }

    private static void drawOptionGlyph(GuiGraphics graphics, Object option, int x, int y, int color) {
        String key = String.valueOf(option).toLowerCase(java.util.Locale.ROOT);
        if (key.startsWith("item_") || key.contains("inventory") || key.equals("item")) {
            graphics.fill(x + 2, y + 2, x + 12, y + 5, color);
            graphics.fill(x + 1, y + 5, x + 13, y + 12, color);
            graphics.fill(x + 4, y + 4, x + 10, y + 6, 0xFF252B33);
            graphics.fill(x + 4, y + 7, x + 5, y + 10, 0xFF252B33);
            graphics.fill(x + 9, y + 7, x + 10, y + 10, 0xFF252B33);
        } else if (key.equals("confirm") || key.contains("enable") || key.equals("true")) {
            graphics.fill(x + 1, y + 7, x + 3, y + 9, color);
            graphics.fill(x + 3, y + 9, x + 5, y + 11, color);
            graphics.fill(x + 5, y + 7, x + 7, y + 9, color);
            graphics.fill(x + 7, y + 5, x + 9, y + 7, color);
            graphics.fill(x + 9, y + 3, x + 11, y + 5, color);
            graphics.fill(x + 11, y + 1, x + 13, y + 3, color);
        } else if (key.contains("health")) {
            graphics.fill(x + 2, y + 3, x + 6, y + 7, color);
            graphics.fill(x + 9, y + 3, x + 13, y + 7, color);
            graphics.fill(x + 2, y + 6, x + 13, y + 9, color);
            graphics.fill(x + 4, y + 9, x + 11, y + 11, color);
            graphics.fill(x + 6, y + 11, x + 9, y + 13, color);
        } else if (key.contains("food") || key.contains("hunger")) {
            graphics.fill(x + 5, y + 1, x + 9, y + 3, color);
            graphics.fill(x + 3, y + 3, x + 11, y + 8, color);
            graphics.fill(x + 4, y + 8, x + 10, y + 11, color);
            graphics.fill(x + 5, y + 11, x + 9, y + 13, color);
            graphics.fill(x + 11, y + 2, x + 13, y + 5, color);
        } else if (key.contains("experience") || key.contains("level")) {
            graphics.fill(x + 6, y + 1, x + 9, y + 3, color);
            graphics.fill(x + 4, y + 3, x + 11, y + 6, color);
            graphics.fill(x + 7, y + 5, x + 9, y + 10, color);
            graphics.fill(x + 3, y + 9, x + 7, y + 12, color);
            graphics.fill(x + 9, y + 9, x + 13, y + 12, color);
        } else if (key.equals("x") || key.equals("y") || key.equals("z")
                || key.contains("position") || key.contains("coordinate")) {
            graphics.renderOutline(x + 2, y + 2, 11, 11, color);
            graphics.fill(x + 6, y + 1, x + 8, y + 13, color);
            graphics.fill(x + 1, y + 6, x + 13, y + 8, color);
            graphics.fill(x + 6, y + 6, x + 8, y + 8, 0xFF252B33);
        } else if (key.contains("time") || key.contains("day")) {
            graphics.renderOutline(x + 2, y + 2, 11, 11, color);
            graphics.fill(x + 7, y + 4, x + 8, y + 8, color);
            graphics.fill(x + 7, y + 7, x + 11, y + 8, color);
            graphics.fill(x + 6, y, x + 9, y + 2, color);
        } else if (key.contains("rain") || key.contains("thunder") || key.contains("weather")) {
            graphics.fill(x + 4, y + 3, x + 7, y + 6, color);
            graphics.fill(x + 7, y + 1, x + 11, y + 7, color);
            graphics.fill(x + 4, y + 5, x + 12, y + 8, color);
            graphics.fill(x + 7, y + 8, x + 9, y + 10, color);
            graphics.fill(x + 4, y + 10, x + 6, y + 12, color);
            if (key.contains("thunder")) {
                graphics.fill(x + 9, y + 8, x + 12, y + 10, 0xFF252B33);
                graphics.fill(x + 7, y + 10, x + 10, y + 12, 0xFF252B33);
            }
        } else if (key.contains("sneak") || key.contains("sprint") || key.contains("player")) {
            graphics.fill(x + 6, y + 1, x + 9, y + 4, color);
            graphics.fill(x + 5, y + 5, x + 10, y + 9, color);
            graphics.fill(x + 3, y + 6, x + 5, y + 8, color);
            graphics.fill(x + 10, y + 6, x + 12, y + 8, color);
            graphics.fill(x + 4, y + 9, x + 6, y + 13, color);
            graphics.fill(x + 9, y + 9, x + 11, y + 13, color);
        } else if (key.equals("kill") || key.contains("combat") || key.contains("entity")) {
            graphics.fill(x + 2, y + 11, x + 4, y + 13, color);
            graphics.fill(x + 4, y + 9, x + 6, y + 11, color);
            graphics.fill(x + 6, y + 7, x + 8, y + 9, color);
            graphics.fill(x + 8, y + 5, x + 10, y + 7, color);
            graphics.fill(x + 10, y + 3, x + 12, y + 5, color);
            graphics.fill(x + 10, y + 1, x + 14, y + 3, color);
            graphics.fill(x + 1, y + 10, x + 3, y + 12, color);
            graphics.fill(x + 4, y + 7, x + 6, y + 9, 0xFF252B33);
        } else if (key.contains("block")) {
            graphics.renderOutline(x + 2, y + 2, 11, 11, color);
            graphics.fill(x + 7, y + 2, x + 8, y + 12, color);
            graphics.fill(x + 2, y + 7, x + 12, y + 8, color);
        } else if (key.contains("dimension") || key.contains("location") || key.contains("region")
                || key.contains("biome") || key.contains("anchor") || key.contains("world")) {
            graphics.fill(x + 6, y + 1, x + 9, y + 3, color);
            graphics.fill(x + 3, y + 3, x + 12, y + 7, color);
            graphics.fill(x + 4, y + 7, x + 11, y + 10, color);
            graphics.fill(x + 6, y + 10, x + 9, y + 13, color);
            graphics.fill(x + 6, y + 4, x + 9, y + 7, 0xFF252B33);
        } else if (key.contains("advancement") || key.contains("achievement")) {
            graphics.fill(x + 6, y, x + 9, y + 4, color);
            graphics.fill(x + 4, y + 4, x + 11, y + 7, color);
            graphics.fill(x + 7, y + 6, x + 9, y + 12, color);
            graphics.fill(x + 3, y + 11, x + 7, y + 13, color);
            graphics.fill(x + 9, y + 11, x + 13, y + 13, color);
            graphics.fill(x + 1, y + 3, x + 4, y + 5, color);
            graphics.fill(x + 11, y + 3, x + 14, y + 5, color);
        } else if (key.contains("script") || key.contains("function") || key.contains("code")) {
            graphics.fill(x + 1, y + 3, x + 3, y + 5, color);
            graphics.fill(x + 3, y + 5, x + 5, y + 7, color);
            graphics.fill(x + 1, y + 9, x + 3, y + 11, color);
            graphics.fill(x + 11, y + 3, x + 13, y + 5, color);
            graphics.fill(x + 9, y + 5, x + 11, y + 7, color);
            graphics.fill(x + 11, y + 9, x + 13, y + 11, color);
            graphics.fill(x + 7, y + 2, x + 9, y + 3, color);
            graphics.fill(x + 5, y + 11, x + 7, y + 12, color);
        } else if (key.contains("event") || key.contains("stage")) {
            graphics.fill(x + 8, y, x + 11, y + 5, color);
            graphics.fill(x + 5, y + 5, x + 10, y + 8, color);
            graphics.fill(x + 4, y + 8, x + 7, y + 13, color);
            graphics.fill(x + 7, y + 6, x + 9, y + 8, 0xFF252B33);
        } else if (key.equals("quest") || key.equals("unlock") || key.equals("lock")
                || key.contains("task")) {
            graphics.fill(x + 2, y + 2, x + 7, y + 12, color);
            graphics.fill(x + 8, y + 2, x + 13, y + 12, color);
            graphics.fill(x + 4, y + 4, x + 6, y + 5, 0xFF252B33);
            graphics.fill(x + 9, y + 4, x + 11, y + 5, 0xFF252B33);
            if (key.equals("lock")) {
                graphics.fill(x + 6, y + 7, x + 10, y + 11, 0xFF252B33);
                graphics.renderOutline(x + 7, y + 5, 4, 4, 0xFF252B33);
            } else if (key.equals("unlock")) {
                graphics.renderOutline(x + 7, y + 5, 4, 4, 0xFF252B33);
                graphics.fill(x + 9, y + 5, x + 11, y + 7, color);
            }
        } else if (key.equals("all") || key.equals("any") || key.equals("count") || key.equals("ratio")) {
            graphics.fill(x + 2, y + 2, x + 5, y + 5, color);
            graphics.fill(x + 10, y + 2, x + 13, y + 5, color);
            graphics.fill(x + 6, y + 9, x + 9, y + 12, color);
            graphics.fill(x + 3, y + 5, x + 4, y + 8, color);
            graphics.fill(x + 11, y + 5, x + 12, y + 8, color);
            graphics.fill(x + 3, y + 7, x + 12, y + 8, color);
        } else if (key.equals("false") || key.contains("disable")) {
            graphics.fill(x + 2, y + 2, x + 12, y + 12, color);
            graphics.fill(x + 4, y + 4, x + 10, y + 10, 0xFF252B33);
            graphics.fill(x + 6, y + 6, x + 8, y + 8, color);
        } else {
            graphics.fill(x + 6, y + 1, x + 9, y + 4, color);
            graphics.fill(x + 3, y + 4, x + 12, y + 7, color);
            graphics.fill(x + 5, y + 7, x + 10, y + 10, color);
            graphics.fill(x + 6, y + 10, x + 9, y + 13, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0) {
            return false;
        }
        if (open && popupContains(mouseX, mouseY)) {
            if (searchable && mouseY < popupY()) {
                if (search != null) {
                    search.setFocused(true);
                    search.mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }
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
                    && pressedOption == optionAt(x, y) && pressedOption >= 0 && pressedOption < options().size();
            dragging = thumbDragging = false;
            if (select) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                choose(options().get(pressedOption));
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
            if (searchable && search != null && search.isFocused()
                    && search.keyPressed(key, scan, modifiers)) return true;
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                if (options().isEmpty()) return true;
                highlighted = Math.floorMod(highlighted + (key == GLFW.GLFW_KEY_UP ? -1 : 1), options().size());
                ensureHighlightedVisible();
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                highlighted = key == GLFW.GLFW_KEY_HOME ? 0 : Math.max(0, options().size() - 1);
                ensureHighlightedVisible();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE && !searchable) {
                if (!options().isEmpty()) choose(options().get(highlighted));
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (open && searchable && search != null) return search.charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            closeMenu();
        } else if (searchable && open) restoreSearchFocus();
    }

    @Override
    protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.button", caption.copy().append(": ").append(getMessage()));
    }
}
