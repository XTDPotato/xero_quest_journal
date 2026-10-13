package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private int codeRight() { return sideBySide() ? width - Math.min(320, width / 3) - 12 : width - 12; }
    private int referenceLeft() { return sideBySide() ? codeRight() + 14 : 12; }
    private int referenceRight() { return width - 12; }
    private int referenceTop() { return bodyTop() + 30; }
    private int referenceBottom() { return bodyBottom() - (referenceTab.equals("examples") ? 66 : 4); }

    @Override protected void init() {
        if (editor != null) draft = editor.getValue();
        if (width < 640 && (previousWidth == 0 || previousWidth >= 640)) showReference = false;
        previousWidth = width;
        clearWidgets();
        editor = new ScriptBox(font, 48, bodyTop(), Math.max(80, codeRight() - 48),
                Math.max(36, bodyBottom() - bodyTop()), title);
        editor.setCharacterLimit(QuestScriptReference.characterLimit(kind));
        editor.setValue(draft);
        editor.visible = !referenceOnly();
        addRenderableWidget(editor);
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
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(saveId)).forEach(button -> button.active = canSave());
        backdrop(g, 0xFF12171D);
        g.fill(0, 0, width, 42, 0xFF202832);
        g.fill(0, 41, width, 42, 0xFF3E4D5B);
        g.enableScissor(12, 6, Math.max(12, width - 238), 39);
        g.drawString(font, title, 12, 12, 0xFFF3D48B, false);
        g.drawString(font, text("kind." + kind.name().toLowerCase(java.util.Locale.ROOT)), 12, 28, 0xFF9CAEBB, false);
        g.disableScissor();
        if (!referenceOnly()) {
            editor.advanceScroll();
            g.fill(12, bodyTop(), 46, bodyBottom(), 0xFF0C1117);
            g.enableScissor(12, bodyTop(), 44, bodyBottom());
            int row = bodyTop() + 4 - (int) editor.scroll();
            int number = 1;
            for (String line : editor.getValue().split("\\R", -1)) {
                if (row + font.lineHeight >= bodyTop() && row < bodyBottom())
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
    }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        if (widget == editor) return viewport(48, bodyTop(), codeRight(), bodyBottom());
        return null;
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inputBlocked()) return true;
        if (scrollChoicePopup(x, y, h, v)) return true;
        if (showReference && x >= referenceLeft() && y >= referenceTop() && y < referenceBottom()) {
            referenceScroll.wheel(v * 28);
            return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { save(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }

    protected static final class ScriptBox extends SmoothTextBox {
        ScriptBox(net.minecraft.client.gui.Font font, int x, int y, int w, int h, Component title) {
            super(font, x, y, w, h, title, Component.empty());
        }
        double scroll() { return scrollAmount(); }
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
