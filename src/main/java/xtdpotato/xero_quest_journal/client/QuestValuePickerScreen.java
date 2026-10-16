package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Searchable registry/value menu used by condition and reward editors. */
public final class QuestValuePickerScreen extends AnimatedQuestScreen {
    public static final class Option {
        private final String value;
        private final java.util.function.Supplier<String> labelSupplier;
        private String label;
        private final String searchIndex;
        public Option(String value, String label) {
            this(value, label, (value + "\n" + label).toLowerCase(Locale.ROOT));
        }
        public Option(String value, String label, String searchIndex) {
            this(value, () -> label, searchIndex);
        }
        public Option(String value, java.util.function.Supplier<String> label) {
            this(value, label, value.toLowerCase(Locale.ROOT));
        }
        private Option(String value, java.util.function.Supplier<String> label, String searchIndex) {
            this.value = value;
            this.labelSupplier = label;
            this.searchIndex = searchIndex;
        }
        public String value() { return value; }
        public String label() {
            if (label == null) label = labelSupplier.get();
            return label;
        }
        public String searchIndex() { return searchIndex; }
    }

    private static final int ROW_HEIGHT = 22;
    private final Screen parent;
    private final String caption;
    private final String context;
    private final Supplier<List<Option>> options;
    private List<Option> candidates;
    private final Consumer<String> selected;
    private String query = "";
    private final String selectedValue;
    private List<Option> filtered;
    private int scroll;
    private double smoothScroll;
    private double targetScroll;
    private boolean draggingScrollbar;
    private double thumbOffset;
    private boolean draggingList;
    private double dragStartY;
    private double dragStartScroll;
    private final java.util.Map<Option, String> searchIndexes = new java.util.HashMap<>();
    private EditBox search;
    private EditBox newCandidate;
    private String candidate = "";
    private String error = "";
    private ButtonStyleSheet styles;

    public QuestValuePickerScreen(Screen parent, String caption, String context, Supplier<List<Option>> options,
                                  String current, Consumer<String> selected) {
        super(Component.literal(caption));
        this.parent = parent;
        this.caption = caption;
        this.context = context;
        this.options = options;
        this.selected = selected;
        this.selectedValue = current == null ? "" : current;
        updateFilter();
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }

    private int panelWidth() { return Math.min(560, width - 24); }
    private int left() { return (width - panelWidth()) / 2; }
    private int listTop() { return 78; }
    private int listBottom() { return Math.max(listTop() + ROW_HEIGHT, height - 72); }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT); }

    private void updateFilter() {
        if (candidates == null) candidates = QuestSuggestionOverrides.apply(context, options.get());
        String normalizedQuery = query.strip().toLowerCase(Locale.ROOT);
        String[] terms = normalizedQuery.isEmpty() ? new String[0] : normalizedQuery.split("\\s+");
        filtered = candidates.stream().filter(option -> {
            for (String term : terms) if (!option.searchIndex().contains(term)
                    && !searchIndexes.computeIfAbsent(option,
                    value -> QuestIcons.searchIndex(value.value(), value.label())).contains(term)) return false;
            return true;
        }).toList();
        targetScroll = Math.clamp(targetScroll, 0, maxScroll());
        smoothScroll = Math.clamp(smoothScroll, 0, maxScroll());
        scroll = (int) smoothScroll;
    }

    public void refreshOptions() {
        candidates = null;
        updateFilter();
    }

    private int maxScroll() { return Math.max(0, filtered.size() - visibleRows()); }
    private int trackX() { return left() + panelWidth() + 3; }
    private int thumbHeight() {
        return Math.max(12, (listBottom() - listTop()) * visibleRows() / Math.max(1, filtered.size()));
    }
    private double thumbY() {
        return listTop() + (listBottom() - listTop() - thumbHeight()) * smoothScroll / Math.max(1, maxScroll());
    }

    @Override
    protected void init() {
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int x = left(), w = panelWidth();
        search = new EditBox(font, x, 42, w, 20, text("value_picker.search"));
        search.setMaxLength(256);
        search.setValue(query);
        search.setHint(text("value_picker.search"));
        search.setResponder(value -> {
            query = value;
            scroll = 0;
            smoothScroll = targetScroll = 0;
            updateFilter();
        });
        addRenderableWidget(search);
        search.setFocused(true);
        newCandidate = new EditBox(font, x, height - 58, w - 58, 20, text("value_picker.add"));
        if (!context.isBlank()) newCandidate.setWidth(Math.max(48, w - 90));
        newCandidate.setMaxLength(4096);
        newCandidate.setValue(candidate);
        newCandidate.setHint(text("value_picker.add"));
        newCandidate.setResponder(value -> candidate = value);
        addRenderableWidget(newCandidate);
        int addWidth = 54;
        int addX = context.isBlank() ? x + w - addWidth : x + w - 84;
        var add = new StyledButton(addX, height - 58, addWidth, 20, text("value_picker.add"),
                ignored -> {
                    if (!QuestSuggestionOverrides.add(context, candidate)) {
                        error = text("value_picker.save_error").getString();
                        return;
                    }
                    error = "";
                    query = "";
                    candidate = "";
                    candidates = null;
                    updateFilter();
                    init();
                }, styles, "candidate-add", "toolbar-button");
        add.active = !context.isBlank();
        addRenderableWidget(add);
        if (!context.isBlank()) {
            var trash = new EditorIconButton(x + w - 24, height - 60, text("value_picker.trash"),
                    this::openTrash, styles, "value-picker-trash", "delete");
            trash.setTooltip(Tooltip.create(text("value_picker.trash_hint")));
            addRenderableWidget(trash);
        }
        var close = new StyledButton(width - 32, 12, 20, 20, Component.literal("\u00d7"),
                ignored -> closeTo(parent), styles, "value-picker-close");
        close.setTooltip(Tooltip.create(Component.translatable("gui.cancel")));
        addRenderableWidget(close);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderContents(graphics, mouseX, mouseY, partialTick);
        int x = left(), right = x + panelWidth(), top = listTop(), bottom = listBottom();
        if (!draggingScrollbar && !draggingList) {
            smoothScroll += (targetScroll - smoothScroll) * Math.min(1, Math.max(0.18, partialTick * 0.4));
            if (Math.abs(targetScroll - smoothScroll) < 0.01) smoothScroll = targetScroll;
        }
        int first = (int) smoothScroll;
        int offset = (int) Math.round((smoothScroll - first) * ROW_HEIGHT);
        graphics.drawCenteredString(font, caption, width / 2, 16, 0xFFFFFFFF);
        graphics.fill(x, top, right, bottom, 0xCC171B21);
        graphics.enableScissor(x, top, right, bottom);
        for (int row = 0; row <= visibleRows() && row + first < filtered.size(); row++) {
            Option option = filtered.get(row + first);
            int y = top + row * ROW_HEIGHT - offset;
            boolean hovered = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean active = option.value().equals(selectedValue);
            int background = active ? 0xFF45556A : hovered ? 0xFF394450 : 0xFF272D35;
            graphics.fill(x + 1, y, right - 1, y + ROW_HEIGHT - 1, background);
            int textX = x + 7;
            int preview = QuestScreen.safeColor(option.value(), Integer.MIN_VALUE);
            if (preview != Integer.MIN_VALUE) {
                if ((preview >>> 24) == 0) {
                    graphics.fill(x + 6, y + 3, x + 14, y + 11, 0xFF8A929E);
                    graphics.fill(x + 14, y + 11, x + 22, y + 19, 0xFF8A929E);
                } else graphics.fill(x + 6, y + 3, x + 22, y + 19, preview);
                graphics.renderOutline(x + 6, y + 3, 16, 16, 0xFFABB8C3);
                textX = x + 27;
            } else if (!option.value().isBlank() && QuestIcons.valid(option.value())) {
                QuestIcons.render(graphics, option.value(), x + 6, y + 3, 16);
                textX = x + 27;
            }
            graphics.drawString(font, font.plainSubstrByWidth(option.label(), right - textX - 8),
                    textX, y + 6, 0xFFE6E9ED, false);
        }
        graphics.disableScissor();
        if (!error.isBlank()) graphics.drawString(font, error, x, height - 70, 0xFFFF8888);
        if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, text("value_picker.empty"), width / 2, top + 12, 0xFFABB8C3);
        } else if (filtered.size() > visibleRows()) {
            graphics.fill(trackX(), top, trackX() + 5, bottom, 0xFF303741);
            graphics.fill(trackX(), (int) thumbY(), trackX() + 5,
                    (int) thumbY() + thumbHeight(), 0xFF9DB4D2);
        }
    }

    private void openTrash() {
        if (context.isBlank()) return;
        if (candidates == null) candidates = QuestSuggestionOverrides.apply(context, options.get());
        List<String> values = candidates.stream().map(Option::value).filter(value -> !value.isBlank())
                .distinct().toList();
        minecraft.setScreen(new QuestSuggestionTrashScreen(this, caption, context, values, () -> {
            candidates = null;
            updateFilter();
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && maxScroll() > 0 && mouseX >= trackX() - 2 && mouseX < trackX() + 9
                && mouseY >= listTop() && mouseY < listBottom()) {
            draggingScrollbar = true;
            thumbOffset = mouseY >= thumbY() && mouseY < thumbY() + thumbHeight()
                    ? mouseY - thumbY() : thumbHeight() / 2.0;
            dragThumb(mouseY);
            return true;
        }
        if (button == 1 && mouseX >= left() && mouseX < left() + panelWidth()
                && mouseY >= listTop() && mouseY < listBottom()) {
            int index = (int) Math.floor(smoothScroll + (mouseY - listTop()) / ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                minecraft.keyboardHandler.setClipboard(filtered.get(index).value());
                error = text("value_picker.copied").getString();
            }
            return true;
        }
        if (button == 0 && mouseX >= left() && mouseX < left() + panelWidth()
                && mouseY >= listTop() && mouseY < listBottom()) {
            int index = (int) Math.floor(smoothScroll + (mouseY - listTop()) / ROW_HEIGHT);
            if (index < filtered.size()) {
                if (maxScroll() > 0) {
                    draggingList = true;
                    dragStartY = mouseY;
                    dragStartScroll = targetScroll;
                    return true;
                }
                String value = filtered.get(index).value();
                selected.accept(value);
                closeTo(parent);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragThumb(double y) {
        targetScroll = smoothScroll = Math.clamp((y - listTop() - thumbOffset) * maxScroll()
                / Math.max(1, listBottom() - listTop() - thumbHeight()), 0, maxScroll());
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScrollbar) { dragThumb(y); return true; }
        if (button == 0 && draggingList) {
            targetScroll = smoothScroll = Math.clamp(dragStartScroll
                    + (dragStartY - y) / ROW_HEIGHT, 0, maxScroll());
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        if (button == 0 && draggingList) {
            draggingList = false;
            if (Math.abs(y - dragStartY) < 4 && x >= left() && x < left() + panelWidth()
                    && y >= listTop() && y < listBottom()) {
                int index = (int) Math.floor(smoothScroll + (y - listTop()) / ROW_HEIGHT);
                if (index >= 0 && index < filtered.size()) {
                    selected.accept(filtered.get(index).value());
                    closeTo(parent);
                }
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (mouseX >= left() && mouseX < left() + panelWidth() + 8
                && mouseY >= listTop() && mouseY < listBottom() && vertical != 0) {
            targetScroll = Math.clamp(targetScroll - vertical * 2.5, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeTo(parent);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop(graphics, 0xF01C2026);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected int animationHeaderBottom() { return 38; }
    @Override protected int animationFooterTop() { return height - 34; }
}
