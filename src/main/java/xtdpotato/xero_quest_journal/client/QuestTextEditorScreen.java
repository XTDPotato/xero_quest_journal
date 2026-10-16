package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.Consumer;

/** Edits a property draft, not the quest source file. */
public final class QuestTextEditorScreen extends AnimatedQuestScreen {
    private static final int LIMIT = 16384;
    // Vanilla exposes no selection API, so locate its private model by type instead of mapped names.
    private static final Field TEXT_FIELD = textFieldAccessor();
    private final Screen parent;
    private final Consumer<String> saved;
    private final int characterLimit;
    private MultiLineEditBox editor;
    private EditBox color;
    private String draft;
    private ButtonStyleSheet styles;
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private boolean restoring, previewDragging;
    private double previewZoom = 1, previewPanX, previewPanY;
    private int previewLeft, previewTop, previewRight, previewBottom;

    public QuestTextEditorScreen(Screen parent, String initial, Consumer<String> saved) {
        this(parent, initial, saved, LIMIT);
    }

    public QuestTextEditorScreen(Screen parent, String initial, Consumer<String> saved, int characterLimit) {
        super(Component.translatable("screen.xero_quest_journal.layout.rich_editor"));
        if (characterLimit <= 0) throw new IllegalArgumentException("Text limit must be positive");
        this.parent = parent;
        this.draft = initial;
        this.saved = saved;
        this.characterLimit = characterLimit;
    }

    @Override protected int animationHeaderBottom() { return 38; }
    @Override protected int animationFooterTop() { return height - 34; }

    @Override
    protected void init() {
        if (editor != null) draft = editor.getValue();
        clearWidgets();
        styles = ButtonStyleSheet.load();
        boolean columns = width >= 520;
        int split = columns ? Math.max(316, width * 55 / 100) : width;
        int toolsY = 38;
        int editorTop = split < 356 ? 96 : 68;
        int editorBottom = columns ? height - 38 : Math.max(editorTop + 50, height * 62 / 100);
        previewLeft = columns ? split + 8 : 12;
        previewTop = columns ? 68 : editorBottom + 32;
        previewRight = width - 12;
        previewBottom = height - 38;
        editor = new SmoothTextBox(font, 12, editorTop, Math.max(80, split - 24),
                Math.max(36, editorBottom - editorTop), title,
                Component.translatable("screen.xero_quest_journal.layout.rich_hint"));
        editor.setCharacterLimit(characterLimit);
        editor.setValue(draft);
        editor.setValueListener(this::changed);
        addRenderableWidget(editor);
        int x = 12;
        x = tool(x, toolsY, 25, "\u21b6", "layout.rich_undo", () -> restore(undo, redo));
        x = tool(x, toolsY, 25, "\u21b7", "layout.rich_redo", () -> restore(redo, undo));
        x = tool(x, toolsY, 25, "B", "layout.rich_bold", () -> insert("<b>", "</b>"));
        x = tool(x, toolsY, 25, "I", "layout.rich_italic", () -> insert("<i>", "</i>"));
        x = tool(x, toolsY, 25, "U", "layout.rich_underline", () -> insert("<u>", "</u>"));
        x = tool(x, toolsY, 25, "S", "layout.rich_strike", () -> insert("<s>", "</s>"));
        x = tool(x, toolsY, 32, "\u21b5", "layout.rich_break", () -> insert("<br>", ""));
        int colorY = split < 356 ? toolsY + 28 : toolsY;
        if (colorY != toolsY) x = 12;
        int colorWidth = Math.max(60, Math.min(96, split - x - 52));
        color = new EditBox(font, x + 4, colorY, colorWidth, 20,
                Component.translatable("screen.xero_quest_journal.layout.rich_color"));
        color.setMaxLength(7);
        color.setValue("#ffffff");
        addRenderableWidget(color);
        tool(x + colorWidth + 8, colorY, 32, "\u25a0", "layout.rich_color",
                () -> {
                    String hex = color.getValue();
                    if (hex.matches("(?i)#[0-9a-f]{6}"))
                        insert("<span style=\"color:" + hex + "\">", "</span>");
                });
        addRenderableWidget(new StyledButton(width - 140, height - 29, 62, 22,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "rich-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 72, height - 29, 60, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(),
                styles, "rich-cancel", "toolbar-button"));
        int resetX = columns ? previewLeft : 12;
        var reset = new EditorIconButton(resetX, previewTop - 28,
                Component.translatable("screen.xero_quest_journal.layout.rich_preview_reset"),
                this::resetPreview, styles, "rich-preview-reset", "refresh");
        reset.setTooltip(Tooltip.create(Component.translatable("screen.xero_quest_journal.layout.rich_preview_reset")));
        addRenderableWidget(reset);
        setInitialFocus(editor);
    }

    private void changed(String value) {
        if (!restoring && !value.equals(draft)) {
            undo.push(draft);
            while (undo.size() > 128) undo.removeLast();
            redo.clear();
        }
        draft = value;
    }

    private void restore(Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return;
        to.push(editor.getValue());
        restoring = true;
        try { editor.setValue(from.pop()); }
        finally { restoring = false; }
        setFocused(editor);
    }

    private void resetPreview() {
        previewZoom = 1;
        previewPanX = previewPanY = 0;
    }

    private boolean overPreview(double x, double y) {
        return x >= previewLeft && x < previewRight && y >= previewTop && y < previewBottom;
    }

    private int tool(int x, int y, int w, String glyph, String key, Runnable action) {
        String icon = key.substring("layout.rich_".length());
        if (icon.equals("break")) icon = "linebreak";
        var button = new EditorIconButton(x, y, Component.translatable("screen.xero_quest_journal." + key),
                action, styles, key, icon);
        button.setTooltip(Tooltip.create(Component.translatable("screen.xero_quest_journal." + key)));
        addRenderableWidget(button);
        return x + EditorIconButton.SIZE + 4;
    }

    private void insert(String open, String close) {
        String value = editor.getValue();
        MultilineTextField input = textField();
        if (input.hasSelection()) {
            int[] range = selectedRange(input, value);
            int start = range[0];
            int end = range[1];
            String selected = value.substring(start, end);
            String replacement = open + selected + close;
            if (value.length() + replacement.length() - selected.length() > characterLimit) return;
            String formatted = value.substring(0, start) + replacement + value.substring(end);
            editor.setValue(formatted);
            selectRange(input, start + open.length(), start + open.length() + selected.length());
        } else {
            if (value.length() + open.length() + close.length() > characterLimit) return;
            int cursor = value.length() + open.length();
            editor.setValue(value + open + close);
            input.seekCursor(Whence.ABSOLUTE, cursor);
        }
        setFocused(editor);
    }

    private static Field textFieldAccessor() {
        Field field = Arrays.stream(MultiLineEditBox.class.getDeclaredFields())
                .filter(candidate -> candidate.getType() == MultilineTextField.class)
                .findFirst().orElseThrow(() -> new ExceptionInInitializerError(
                        "Cannot find the text model used by MultiLineEditBox"));
        field.setAccessible(true);
        return field;
    }

    private MultilineTextField textField() {
        try {
            return (MultilineTextField) TEXT_FIELD.get(editor);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access the rich-text editor selection", exception);
        }
    }

    private static int[] selectedRange(MultilineTextField input, String value) {
        String selected = input.getSelectedText();
        for (Method method : MultilineTextField.class.getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || !method.getReturnType().isRecord()) continue;
            try {
                method.setAccessible(true);
                Object range = method.invoke(input);
                var components = method.getReturnType().getRecordComponents();
                if (components.length != 2) continue;
                Method begin = components[0].getAccessor();
                Method finish = components[1].getAccessor();
                begin.setAccessible(true);
                finish.setAccessible(true);
                int start = (int) begin.invoke(range);
                int end = (int) finish.invoke(range);
                if (start >= 0 && end <= value.length() && end - start == selected.length()
                        && value.regionMatches(start, selected, 0, selected.length())) {
                    return new int[]{start, end};
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot read the rich-text editor selection", exception);
            }
        }
        throw new IllegalStateException("Cannot locate the rich-text editor selection range");
    }

    private static void selectRange(MultilineTextField input, int start, int end) {
        input.setSelecting(false);
        input.seekCursor(Whence.ABSOLUTE, start);
        input.setSelecting(true);
        input.seekCursor(Whence.ABSOLUTE, end);
        input.setSelecting(false);
    }

    private void save() {
        saved.accept(editor.getValue());
        minecraft.setScreen(parent);
    }

    @Override
    protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xFF161A20);
        g.drawString(font, title, 12, 12, 0xFFF0D59B, false);
        g.drawString(font, Component.translatable("screen.xero_quest_journal.layout.rich_preview"),
                previewLeft + 30, previewTop - 19, 0xFF9FB4BC, false);
        g.fill(previewLeft, previewTop, previewRight, previewBottom, 0xFF242A30);
        g.enableScissor(previewLeft, previewTop, previewRight, previewBottom);
        var preview = QuestRichText.parse(editor.getValue(), Style.EMPTY);
        g.pose().pushPose();
        g.pose().translate(previewLeft + 10 + previewPanX, previewTop + 10 + previewPanY, 0);
        g.pose().scale((float) previewZoom, (float) previewZoom, 1);
        int lineY = 0;
        for (var line : font.split(preview, Math.max(1, (int) ((previewRight - previewLeft - 20) / previewZoom)))) {
            if (previewTop + 10 + previewPanY + lineY * previewZoom > previewBottom + 12) break;
            if (previewTop + 10 + previewPanY + lineY * previewZoom >= previewTop - 12)
                g.drawString(font, line, 0, lineY, 0xFFE4EAF2, false);
            lineY += font.lineHeight + 2;
        }
        g.pose().popPose();
        g.disableScissor();
        g.renderOutline(previewLeft, previewTop, previewRight - previewLeft,
                previewBottom - previewTop, 0xFF596773);
        super.renderContents(g, x, y, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (overPreview(x, y) && (button == 0 || button == 2)) {
            previewDragging = true;
            setFocused(null);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (previewDragging) {
            previewPanX += dx;
            previewPanY += dy;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (previewDragging) { previewDragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!overPreview(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        double old = previewZoom;
        previewZoom = Math.clamp(previewZoom * Math.pow(1.12, vertical), .4, 4);
        previewPanX = x - previewLeft - 10 - (x - previewLeft - 10 - previewPanX) / old * previewZoom;
        previewPanY = y - previewTop - 10 - (y - previewTop - 10 - previewPanY) / old * previewZoom;
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_Z) { restore(undo, redo); return true; }
            if (key == GLFW.GLFW_KEY_Y) { restore(redo, undo); return true; }
        }
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { closeTo(parent); }
}
