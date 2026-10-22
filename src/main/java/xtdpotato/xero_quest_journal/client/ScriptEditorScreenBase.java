package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.QuestScriptReference;
import xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind;
import xtdpotato.xero_quest_journal.core.runtime.QuestScripts;

/** One clipped editor/reference layout for graph drafts and file-backed scripts. */
abstract class ScriptEditorScreenBase extends AnimatedQuestScreen {
    protected final Screen parent;
    protected final Kind kind;
    protected ScriptBox editor;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final String saveId;
    private final SmoothScroll referenceScroll = new SmoothScroll();
    private String draft;
    private String referenceTab = "reference";
    private String exampleId;
    private String diagnostic = "";
    private boolean showReference = true;
    private int previousWidth;
    private static final java.util.concurrent.ExecutorService COMPLETIONS =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                var thread = new Thread(task, "xero-script-completion");
                thread.setDaemon(true);
                return thread;
            });
    private final SmoothScroll completionScroll = new SmoothScroll();
    private final java.util.Deque<EditState> undo = new java.util.ArrayDeque<>(), redo = new java.util.ArrayDeque<>();
    private java.util.concurrent.CompletableFuture<ScriptCompletions.Result> pendingCompletions;
    private ScriptCompletions.Result completionResult;
    private String completionSource = "", pendingSource = "", dismissedSource;
    private int completionCursor = -1, pendingCursor, dismissedCursor, selectedCompletion, pressedCompletion = -1;
    private boolean explicitCompletion, restoring;
    private long completionChangedAt;
    private long completionRequest, pendingRequest;
    private EditState editState;
    private record EditState(String source, int cursor, int anchor) {}

    ScriptEditorScreenBase(Screen parent, Component title, String initial, Kind kind, String saveId) {
        super(title);
        this.parent = parent;
        this.kind = kind;
        this.saveId = saveId;
        draft = initial == null ? "" : initial;
        exampleId = QuestScriptReference.examples(kind).getFirst().id();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.script." + key, args);
    }
    private boolean sideBySide() { return width >= 640 && showReference; }
    private boolean referenceOnly() { return width < 640 && showReference; }
    private int bodyTop() { return 54; }
    private int bodyBottom() { return height - 34; }
    private int completionHeight() { return Math.min(88, Math.max(44, (bodyBottom() - bodyTop()) / 3)); }
    private int codeBottom() { return bodyBottom() - completionHeight() - 6; }
    private int completionTop() { return codeBottom() + 6; }
    private int completionListTop() { return completionTop() + 16; }
    private int codeRight() { return sideBySide() ? width - Math.min(320, width / 3) - 12 : width - 12; }
    private int referenceLeft() { return sideBySide() ? codeRight() + 14 : 12; }
    private int referenceRight() { return width - 12; }
    private int referenceTop() { return bodyTop() + 30; }
    private int referenceBottom() { return bodyBottom() - (referenceTab.equals("examples") ? 66 : 4); }

    @Override protected void init() {
        EditState previous = editor == null ? null : editor.state();
        if (editor != null) draft = editor.getValue();
        if (width < 640 && (previousWidth == 0 || previousWidth >= 640)) showReference = false;
        previousWidth = width;
        clearWidgets();
        editor = new ScriptBox(font, 48, bodyTop(), Math.max(80, codeRight() - 48),
                Math.max(36, codeBottom() - bodyTop()), title);
        editor.setCharacterLimit(QuestScriptReference.characterLimit(kind));
        editor.setValue(draft);
        if (previous != null) editor.restore(previous);
        editState = editor.state();
        editor.setValueListener(value -> {
            if (!restoring && editState != null && !value.equals(editState.source())) {
                undo.push(editState);
                while (undo.size() > 128) undo.removeLast();
                redo.clear();
            }
            editState = editor.state();
            diagnostic = "";
        });
        editor.visible = !referenceOnly();
        addRenderableWidget(editor);
        var undoButton = new EditorIconButton(width - 282, 10,
                Component.translatable("screen.xero_quest_journal.layout.rich_undo"),
                () -> restoreEdit(undo, redo), styles, "script-undo", "undo");
        undoButton.setTooltip(Tooltip.create(undoButton.getMessage()));
        addRenderableWidget(undoButton);
        var redoButton = new EditorIconButton(width - 254, 10,
                Component.translatable("screen.xero_quest_journal.layout.rich_redo"),
                () -> restoreEdit(redo, undo), styles, "script-redo", "redo");
        redoButton.setTooltip(Tooltip.create(redoButton.getMessage()));
        addRenderableWidget(redoButton);
        addRenderableWidget(new StyledButton(width - 226, 10, 86, 23,
                text(showReference ? "hide_reference" : "show_reference"), ignored -> {
                    showReference = !showReference;
                    init();
                }, styles, "script-help", "toolbar-button"));
        var save = new StyledButton(width - 134, 10, 62, 23,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, saveId, "toolbar-button");
        save.active = canSave();
        addRenderableWidget(save);
        addRenderableWidget(new StyledButton(width - 66, 10, 54, 23,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "script-cancel", "toolbar-button"));
        if (showReference) {
            int x = referenceLeft(), w = referenceRight() - x;
            for (int i = 0; i < 2; i++) {
                String tab = i == 0 ? "reference" : "examples";
                var button = new StyledButton(x + i * (w / 2 + 2), bodyTop(), w / 2 - 2, 22,
                        text(tab), ignored -> {
                            referenceTab = tab;
                            referenceScroll.jump(0);
                            init();
                        }, styles, "script-reference-" + tab, "toolbar-button");
                button.setSelectionHighlighted(referenceTab.equals(tab));
                addRenderableWidget(button);
            }
            if (referenceTab.equals("examples")) {
                var examples = QuestScriptReference.examples(kind);
                var selected = examples.stream().filter(value -> value.id().equals(exampleId)).findFirst().orElse(examples.getFirst());
                addRenderableWidget(new StyledChoiceButton<>(x + 8, bodyBottom() - 59, w - 16, 22,
                        text("example"), examples, selected,
                        value -> text("example." + value.id()), value -> {
                            exampleId = value.id();
                            referenceScroll.jump(0);
                        }, styles, "script-example-select"));
                addRenderableWidget(new StyledButton(x + 8, bodyBottom() - 31, w - 16, 23,
                        text("append_example"), ignored -> {
                            String current = editor.getValue();
                            String source = examples.stream().filter(value -> value.id().equals(exampleId))
                                    .findFirst().orElse(examples.getFirst()).source();
                            String combined = current.isBlank() ? source : current + "\n;\n\n" + source;
                            if (combined.length() > QuestScriptReference.characterLimit(kind)) {
                                diagnostic = text("example_too_long").getString();
                                return;
                            }
                            editor.setValue(combined);
                            draft = editor.getValue();
                            diagnostic = "";
                            if (referenceOnly()) { showReference = false; init(); }
                            setFocused(editor);
                        }, styles, "script-example-insert"));
            }
            addRenderableWidget(new ScrollRail(referenceRight() - 8, referenceTop(),
                    Math.max(8, referenceBottom() - referenceTop()), referenceScroll));
        }
        if (!referenceOnly()) setInitialFocus(editor);
    }

    protected boolean canSave() { return true; }
    protected abstract void saveDraft(String source);
    protected final void showSaveError() { diagnostic = text("save_failed").getString(); }
    protected final String draft() { return editor == null ? draft : editor.getValue(); }

    private void save() {
        if (!canSave()) return;
        draft = editor.getValue();
        diagnostic = kind == Kind.KUBEJS ? "" : QuestScripts.syntaxError(draft);
        if (!diagnostic.isEmpty()) return;
        saveDraft(draft);
    }

    private void restoreEdit(java.util.Deque<EditState> from, java.util.Deque<EditState> to) {
        if (from.isEmpty()) return;
        to.push(editor.state());
        restoring = true;
        try { editor.restore(from.pop()); }
        finally { restoring = false; }
        editState = editor.state();
        setFocused(editor);
    }

    private void updateCompletions() {
        if (editor == null) return;
        EditState current = editor.state();
        // Keep history's pre-edit caret/selection current even after pointer or keyboard navigation.
        if (editState == null || current.source().equals(editState.source())) editState = current;
        if (!current.source().equals(completionSource) || current.cursor() != completionCursor) {
            completionSource = current.source();
            completionCursor = current.cursor();
            completionResult = null;
            completionChangedAt = System.nanoTime();
            completionRequest++;
            explicitCompletion = false;
            selectedCompletion = 0;
            pressedCompletion = -1;
            completionScroll.jump(0);
        }
        if (pendingCompletions != null && pendingCompletions.isDone()) {
            try {
                var result = pendingCompletions.join();
                if (pendingRequest == completionRequest)
                    completionResult = result;
            } catch (RuntimeException ignored) {
                if (pendingRequest == completionRequest)
                    completionResult = new ScriptCompletions.Result(completionCursor, completionCursor, List.of());
            }
            pendingCompletions = null;
        }
        if (referenceOnly() || !editor.isFocused() || current.cursor() != current.anchor()
                || current.source().equals(dismissedSource) && current.cursor() == dismissedCursor) return;
        if (completionResult == null && pendingCompletions == null
                && (explicitCompletion || System.nanoTime() - completionChangedAt >= 75_000_000L)) {
            pendingSource = completionSource;
            pendingCursor = completionCursor;
            pendingRequest = completionRequest;
            String source = pendingSource;
            int cursor = pendingCursor;
            boolean explicit = explicitCompletion;
            pendingCompletions = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> ScriptCompletions.complete(source, cursor, kind, explicit), COMPLETIONS);
        }
    }

    private boolean completionVisible() {
        return !referenceOnly() && editor.isFocused() && !editor.hasSelection()
                && completionResult != null && !completionResult.candidates().isEmpty()
                && editor.getValue().equals(completionSource) && editor.cursor() == completionCursor
                && !(completionSource.equals(dismissedSource) && completionCursor == dismissedCursor);
    }

    private int completionAt(double x, double y) {
        if (!completionVisible() || x < 48 || x >= codeRight() - 6
                || y < completionListTop() || y >= bodyBottom()) return -1;
        int index = (int) ((y - completionListTop() + completionScroll.position()) / 16);
        return index >= 0 && index < completionResult.candidates().size() ? index : -1;
    }

    private void acceptCompletion(int index) {
        if (!completionVisible() || index < 0 || index >= completionResult.candidates().size()) return;
        // Validate against the live model: a worker result must never replace a newly edited word.
        if (!editor.getValue().equals(completionSource) || editor.cursor() != completionCursor) return;
        String replacement = completionResult.candidates().get(index).name();
        if (editor.getValue().length() - (completionResult.end() - completionResult.start())
                + replacement.length() > QuestScriptReference.characterLimit(kind)) return;
        editState = editor.state();
        editor.replace(completionResult.start(), completionResult.end(), replacement);
        dismissedSource = editor.getValue();
        dismissedCursor = editor.cursor();
        completionResult = null;
        setFocused(editor);
    }

    private void renderCompletions(GuiGraphics g, int mouseX, int mouseY) {
        if (referenceOnly()) return;
        g.fill(48, completionTop(), codeRight(), bodyBottom(), 0xFF19232A);
        g.fill(48, completionTop(), codeRight(), completionTop() + 1, 0xFF536976);
        g.drawString(font, text("completion"), 54, completionTop() + 4, 0xFFE4D29E, false);
        if (!completionVisible()) return;
        String count = (selectedCompletion + 1) + "/" + completionResult.candidates().size();
        g.drawString(font, count, codeRight() - 8 - font.width(count), completionTop() + 4, 0xFFABC2CC, false);
        int listHeight = bodyBottom() - completionListTop();
        completionScroll.limit(completionResult.candidates().size() * 16 - listHeight);
        completionScroll.advance();
        int first = Math.max(0, completionScroll.pixels() / 16);
        int last = Math.min(completionResult.candidates().size(), (completionScroll.pixels() + listHeight) / 16 + 2);
        g.enableScissor(48, completionListTop(), codeRight() - 5, bodyBottom());
        for (int index = first; index < last; index++) {
            int row = completionListTop() + index * 16 - completionScroll.pixels();
            boolean hover = completionAt(mouseX, mouseY) == index;
            var candidate = completionResult.candidates().get(index);
            if (hover || index == selectedCompletion)
                g.fill(49, row, codeRight() - 6, row + 16, hover ? 0xFF39505A : 0xFF293D47);
            int color = candidate.type().equals("function") ? 0xFFB7D99B
                    : candidate.type().equals("keyword") ? 0xFFE3B687 : 0xFF9ACDE2;
            int available = codeRight() - 70;
            String name = font.plainSubstrByWidth(candidate.name(), Math.max(1, available * 2 / 3));
            g.drawString(font, name, 54, row + 4, color, false);
            String detail = candidate.signature().equals(candidate.name()) ? "" : candidate.signature();
            int detailX = 64 + font.width(name);
            g.drawString(font, font.plainSubstrByWidth(detail, Math.max(0, codeRight() - detailX - 10)),
                    detailX, row + 4, 0xFF9CAEBA, false);
        }
        g.disableScissor();
        if (completionScroll.maximum() > 0) {
            int thumb = Math.max(8, listHeight * listHeight / (completionResult.candidates().size() * 16));
            int top = completionListTop() + (int) (completionScroll.position()
                    / completionScroll.maximum() * (listHeight - thumb));
            g.fill(codeRight() - 3, top, codeRight() - 1, top + thumb, 0xFFABC2CC);
        }
    }

    private List<Component> referenceLines() {
        if (referenceTab.equals("examples")) {
            var example = QuestScriptReference.examples(kind).stream()
                    .filter(value -> value.id().equals(exampleId)).findFirst().orElseThrow();
            return java.util.Arrays.stream(example.source().split("\\R", -1)).map(Component::literal).map(Component.class::cast).toList();
        }
        var result = new ArrayList<Component>();
        result.add(text("kind." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        result.add(text(kind == Kind.KUBEJS ? "kubejs_context" : kind == Kind.REWARD_FILE ? "reward_result" : "result"));
        result.add(text(kind == Kind.GRAPH_PREDICATE ? "graph_types" : kind == Kind.KUBEJS ? "kubejs_return" : "file_types"));
        if (kind != Kind.KUBEJS) {
            result.add(text("return"));
            result.add(text("no_player"));
            result.add(text("limits"));
        }
        if (kind == Kind.REWARD_FILE) result.add(text("actions"));
        if (kind == Kind.GRAPH_PREDICATE) result.add(text("error_branch"));
        result.add(Component.empty());
        result.add(text("fields"));
        for (var field : QuestScriptReference.fields(kind))
            result.add(Component.literal(field.name() + "  ").append(text("type." + field.type())));
        return result;
    }

    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        updateCompletions();
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .forEach(button -> {
                    if (button.styleId().equals(saveId)) button.active = canSave();
                    else if (button.styleId().equals("script-undo")) button.active = !undo.isEmpty();
                    else if (button.styleId().equals("script-redo")) button.active = !redo.isEmpty();
                });
        backdrop(g, 0xFF12171D);
        g.fill(0, 0, width, 42, 0xFF202832);
        g.fill(0, 41, width, 42, 0xFF3E4D5B);
        g.enableScissor(12, 6, Math.max(12, width - 294), 39);
        g.drawString(font, title, 12, 12, 0xFFF3D48B, false);
        g.drawString(font, text("kind." + kind.name().toLowerCase(java.util.Locale.ROOT)), 12, 28, 0xFF9CAEBB, false);
        g.disableScissor();
        if (!referenceOnly()) {
            editor.advanceScroll();
            g.fill(12, bodyTop(), 46, codeBottom(), 0xFF0C1117);
            g.enableScissor(12, bodyTop(), 44, codeBottom());
            int row = bodyTop() + 4 - (int) editor.scroll();
            int number = 1;
            for (String line : editor.getValue().split("\\R", -1)) {
                if (row + font.lineHeight >= bodyTop() && row < codeBottom())
                    g.drawString(font, Integer.toString(number), 18, row, 0xFF7C929E, false);
                row += Math.max(1, font.split(Component.literal(line), Math.max(1, editor.getWidth() - 8)).size()) * font.lineHeight;
                number++;
            }
            g.disableScissor();
        }
        if (showReference) {
            int x = referenceLeft(), right = referenceRight(), top = referenceTop(), bottom = referenceBottom();
            g.fill(x, bodyTop(), right, bodyBottom(), 0xFF1C252E);
            var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
            for (Component line : referenceLines()) {
                if (line.getString().isEmpty()) lines.add(Component.literal(" ").getVisualOrderText());
                else lines.addAll(font.split(line, Math.max(40, right - x - 30)));
                lines.add(Component.literal(" ").getVisualOrderText());
            }
            referenceScroll.limit(lines.size() * font.lineHeight - (bottom - top - 8));
            referenceScroll.advance();
            g.enableScissor(x + 8, top, right - 12, bottom);
            int row = top + 4 - referenceScroll.pixels();
            for (var line : lines) {
                if (row + font.lineHeight >= top && row < bottom)
                    g.drawString(font, line, x + 10, row, referenceTab.equals("examples") ? 0xFFD4EBCB : 0xFFB8CBD5, false);
                row += font.lineHeight;
            }
            g.disableScissor();
        }
        g.fill(0, height - 30, width, height, 0xFF182029);
        if (!diagnostic.isEmpty()) {
            var lines = font.split(text("syntax_error", diagnostic), Math.max(80, codeRight() - 118));
            for (int i = 0; i < Math.min(2, lines.size()); i++)
                g.drawString(font, lines.get(i), 12, height - 26 + i * font.lineHeight, 0xFFFF9B8F, false);
        } else g.drawString(font, font.plainSubstrByWidth(text(kind == Kind.KUBEJS ? "trusted"
                        : kind == Kind.REWARD_FILE ? "action_status" : "strict_boolean").getString(), width - 132),
                12, height - 21, 0xFF8FA5B3, false);
        String count = editor.getValue().length() + " / " + QuestScriptReference.characterLimit(kind);
        g.drawString(font, count, width - 12 - font.width(count), height - 21, 0xFF8FA5B3, false);
        super.renderContents(g, mouseX, mouseY, delta);
        renderCompletions(g, mouseX, mouseY);
    }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        if (widget == editor) return viewport(48, bodyTop(), codeRight(), codeBottom());
        return null;
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inputBlocked()) return true;
        if (scrollChoicePopup(x, y, h, v)) return true;
        if (completionVisible() && x >= 48 && x < codeRight() && y >= completionTop() && y < bodyBottom()) {
            completionScroll.limit(completionResult.candidates().size() * 16 - (bodyBottom() - completionListTop()));
            completionScroll.wheel(v * 24);
            return true;
        }
        if (showReference && x >= referenceLeft() && y >= referenceTop() && y < referenceBottom()) {
            referenceScroll.wheel(v * 28);
            return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        updateCompletions();
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && key == GLFW.GLFW_KEY_Z) {
            restoreEdit((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? redo : undo,
                    (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? undo : redo);
            return true;
        }
        if (control && key == GLFW.GLFW_KEY_Y) { restoreEdit(redo, undo); return true; }
        if (editor.isFocused() && control && key == GLFW.GLFW_KEY_SPACE) {
            dismissedSource = null;
            explicitCompletion = true;
            completionResult = null;
            completionRequest++;
            return true;
        }
        if (completionVisible()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                dismissedSource = completionSource;
                dismissedCursor = completionCursor;
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB && modifiers == 0) { acceptCompletion(selectedCompletion); return true; }
            if ((modifiers & GLFW.GLFW_MOD_ALT) != 0
                    && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
                selectedCompletion = Math.floorMod(selectedCompletion + (key == GLFW.GLFW_KEY_UP ? -1 : 1),
                        completionResult.candidates().size());
                completionScroll.limit(completionResult.candidates().size() * 16 - (bodyBottom() - completionListTop()));
                completionScroll.to(selectedCompletion * 16);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { save(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (inputBlocked()) return true;
        if (character == ' ' && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return true;
        updateCompletions();
        return super.charTyped(character, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        updateCompletions();
        if (button == 0 && completionAt(x, y) >= 0) {
            pressedCompletion = completionAt(x, y);
            return true;
        }
        pressedCompletion = -1;
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (pressedCompletion >= 0 && button == 0) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (pressedCompletion >= 0 && button == 0) {
            int pressed = pressedCompletion;
            pressedCompletion = -1;
            if (!inputBlocked() && completionAt(x, y) == pressed) acceptCompletion(pressed);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }

    protected static final class ScriptBox extends SmoothTextBox {
        private static final java.lang.reflect.Field MODEL = java.util.Arrays.stream(MultiLineEditBox.class.getDeclaredFields())
                .filter(field -> field.getType() == MultilineTextField.class).findFirst().map(field -> {
                    field.setAccessible(true);
                    return field;
                }).orElseThrow();
        ScriptBox(net.minecraft.client.gui.Font font, int x, int y, int w, int h, Component title) {
            super(font, x, y, w, h, title, Component.empty());
        }
        double scroll() { return scrollAmount(); }
        private MultilineTextField model() {
            try { return (MultilineTextField) MODEL.get(this); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
        int cursor() { return model().cursor(); }
        boolean hasSelection() { return model().hasSelection(); }
        EditState state() {
            int cursor = cursor();
            int length = model().getSelectedText().length();
            // The selected text ends at one side of the caret; reflection only reads the model's range.
            int anchor = cursor;
            if (length > 0) {
                try {
                    Object range = model().getSelected();
                    var components = range.getClass().getRecordComponents();
                    var begin = components[0].getAccessor();
                    var end = components[1].getAccessor();
                    begin.setAccessible(true);
                    end.setAccessible(true);
                    int start = (int) begin.invoke(range);
                    anchor = cursor == start ? (int) end.invoke(range) : start;
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
            return new EditState(getValue(), cursor, anchor);
        }
        void restore(EditState state) {
            setValue(state.source());
            select(state.anchor(), state.cursor());
        }
        void select(int start, int end) {
            model().setSelecting(false);
            model().seekCursor(Whence.ABSOLUTE, start);
            model().setSelecting(true);
            model().seekCursor(Whence.ABSOLUTE, end);
            model().setSelecting(false);
        }
        void replace(int start, int end, String value) {
            String updated = getValue().substring(0, start) + value + getValue().substring(end);
            setValue(updated);
            select(start + value.length(), start + value.length());
        }
        @Override protected void renderDecorations(GuiGraphics g) {
            int right = getX() + getWidth(), bottom = getY() + getHeight();
            int thumb = Math.min(getHeight(), Math.max(12, (int) (getHeight() * getHeight()
                    / (double) (getHeight() + getMaxScrollAmount()))));
            int top = getY() + (int) Math.round(scrollAmount() / Math.max(1, getMaxScrollAmount()) * (getHeight() - thumb));
            g.fill(right - 4, getY() + 1, right - 1, bottom - 1, 0xFF303741);
            g.fill(right - 4, top + 1, right - 1, Math.min(bottom - 1, top + thumb), 0xFFABB8C8);
        }
    }
}
