package xtdpotato.xero_quest_journal.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

/** Local-host source editor. Source writes remain server-threaded, atomic and hash checked. */
public final class QuestSourceScreen extends AnimatedQuestScreen {
    private static final int SOURCE_LIMIT = 1_048_576;
    @Override protected int animationHeaderBottom() { return editorTop - 2; }
    @Override protected int animationFooterTop() { return height - 34; }

    private final Screen parent;
    private final String questId;
    private final Path file;
    private final String original;
    private final String expectedHash;
    private SourceBox editor;
    private ButtonStyleSheet styles;
    private CompletableFuture<QuestAuthoring.Result> pending;
    private String message;
    private QuestSourceFormatter.Diagnostic diagnostic;
    private StyledButton repairButton;
    private StyledButton copyProblemButton;
    private int editorTop;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private String lastDraft;
    private boolean restoring;

    public QuestSourceScreen(Screen parent, String questId) throws Exception {
        super(Component.translatable("screen.xero_quest_journal.source"));
        this.parent = parent;
        this.questId = questId;
        this.file = QuestRepository.fileOf(questId);
        if (file == null) throw new IllegalArgumentException("Quest has no source file");
        this.original = QuestRepository.source(questId);
        this.expectedHash = QuestRepository.fingerprint(original);
    }

    @Override
    protected void init() {
        String draft = editor == null ? original : editor.getValue();
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int buttonY = 14 + font.split(Component.literal(file.getFileName().toString()), Math.max(1, width - 24)).size() * font.lineHeight;
        int toolsY = buttonY + 28;
        editorTop = toolsY + 30;
        editor = new SourceBox(font, 48, editorTop, Math.max(80, width - 60), Math.max(24, height - editorTop - 36),
                Component.translatable("screen.xero_quest_journal.source.editor"),
                Component.translatable("screen.xero_quest_journal.source.placeholder"));
        editor.setCharacterLimit(SOURCE_LIMIT);
        editor.setValue(draft);
        lastDraft = draft;
        editor.setValueListener(this::draftChanged);
        addRenderableWidget(editor);
        tool(12, toolsY, 24, "undo", "source.undo", () -> restore(undo, redo));
        tool(40, toolsY, 24, "redo", "source.redo", () -> restore(redo, undo));
        tool(68, toolsY, 76, null, "source.format", this::format);
        repairButton = new StyledButton(148, toolsY, 86, 22,
                Component.translatable("screen.xero_quest_journal.source.repair"),
                ignored -> repair(), styles, "source-repair", "toolbar-button");
        repairButton.active = diagnostic != null && diagnostic.repair() != null;
        addRenderableWidget(repairButton);
        copyProblemButton = new StyledButton(238, toolsY, 86, 22,
                Component.translatable("screen.xero_quest_journal.source.copy_problem"),
                ignored -> copyProblem(), styles, "source-copy-problem", "toolbar-button");
        copyProblemButton.active = diagnostic != null;
        addRenderableWidget(copyProblemButton);
        addRenderableWidget(new StyledButton(width - 220, buttonY, 66, 22,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "source-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 148, buttonY, 92, 22,
                Component.translatable("screen.xero_quest_journal.open_folder"), ignored ->
                Util.getPlatform().openFile(file.getParent().toFile()),
                styles, "source-folder", "toolbar-button"));
        addRenderableWidget(new EditorIconButton(width - 36, buttonY - 1,
                Component.translatable("gui.back"), this::onClose, styles, "source-close", "close"));
        setInitialFocus(editor);
    }

    private void tool(int x, int y, int width, String symbol, String key, Runnable action) {
        Component label = Component.translatable("screen.xero_quest_journal." + key);
        StyledButton button = symbol == null ? new StyledButton(x, y, width, 22, label,
                ignored -> action.run(), styles, key, "toolbar-button")
                : new EditorIconButton(x, y, label, action, styles, key, symbol);
        button.setTooltip(Tooltip.create(label));
        addRenderableWidget(button);
    }

    private void draftChanged(String value) {
        if ("source.format_error".equals(message)) message = null;
        diagnostic = null;
        if (repairButton != null) repairButton.active = false;
        if (copyProblemButton != null) copyProblemButton.active = false;
        if (!restoring && !value.equals(lastDraft)) {
            remember(undo, lastDraft);
            redo.clear();
        }
        lastDraft = value;
    }

    private static void remember(Deque<String> history, String value) {
        history.push(value);
        long characters = 0;
        int count = 0;
        var iterator = history.iterator();
        while (iterator.hasNext()) {
            characters += iterator.next().length();
            if (++count > 128 || (characters > 8_388_608 && count > 1)) iterator.remove();
        }
    }

    private void restore(Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return;
        remember(to, editor.getValue());
        double scroll = editor.scroll();
        restoring = true;
        try {
            editor.setValue(from.pop());
            editor.restoreScroll(scroll);
        } finally {
            restoring = false;
        }
        setFocused(editor);
    }

    private void format() {
        try {
            String formatted = QuestSourceFormatter.format(editor.getValue());
            if (formatted.length() > SOURCE_LIMIT) throw new IllegalArgumentException("Formatted source is too long");
            if (!formatted.equals(editor.getValue())) editor.setValue(formatted);
            message = null;
        } catch (RuntimeException invalid) {
            message = "source.format_error";
            showDiagnostic(editor.getValue(), invalid);
        }
        setFocused(editor);
    }

    private void save() {
        if (pending != null || minecraft == null || minecraft.player == null
                || minecraft.getSingleplayerServer() == null) {
            return;
        }
        String value = editor.getValue();
        var issue = QuestSourceFormatter.diagnose(value);
        if (issue != null) {
            diagnostic = issue;
            message = "source.format_error";
            showDiagnostic(value, null);
            return;
        }
        var server = minecraft.getSingleplayerServer();
        var uuid = minecraft.player.getUUID();
        message = "saving";
        pending = CompletableFuture.supplyAsync(() -> QuestAuthoring.editSource(
                server.getPlayerList().getPlayer(uuid), questId, expectedHash, value), server);
    }

    @Override
    public void tick() {
        styles.refreshIfChanged();
        if (pending != null && pending.isDone()) {
            QuestAuthoring.Result result;
            try {
                result = pending.join();
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.error("Source save failed for {}", questId, exception);
                result = new QuestAuthoring.Result("", "screen.xero_quest_journal.create.error.save");
            }
            pending = null;
            if (result.successful()) {
                message = "source.saved";
                minecraft.setScreen(parent);
            } else {
                message = result.error().replace("screen.xero_quest_journal.", "");
                if (message.equals("create.error.definitions")) showDiagnostic(editor.getValue(), null);
            }
        }
    }

    private void showDiagnostic(String value, RuntimeException failure) {
        if (diagnostic == null) diagnostic = QuestSourceFormatter.diagnose(value);
        if (diagnostic == null && failure != null)
            diagnostic = new QuestSourceFormatter.Diagnostic(1, 1, failure.getMessage(), null);
        if (diagnostic == null) return;
        if (repairButton != null) repairButton.active = diagnostic.repair() != null;
        if (copyProblemButton != null) copyProblemButton.active = true;
        int offset = 0;
        String[] lines = value.split("\\R", -1);
        for (int i = 0; i < Math.min(diagnostic.line() - 1, lines.length); i++)
            offset += Math.max(1, font.split(Component.literal(lines[i]), Math.max(1, editor.getWidth() - 8)).size());
        editor.restoreScroll(Math.max(0, offset * font.lineHeight - editor.getHeight() / 3.0));
        setFocused(editor);
    }

    private void repair() {
        if (diagnostic == null || diagnostic.repair() == null) return;
        editor.setValue(diagnostic.repair());
        format();
    }

    private void copyProblem() {
        if (diagnostic == null) return;
        minecraft.keyboardHandler.setClipboard(Component.translatable("screen.xero_quest_journal.source.diagnostic",
                diagnostic.line(), diagnostic.column(), diagnostic.reason()).getString());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop(graphics, 0xFF101216);
        graphics.fill(0, editorTop - 2, 46, height - 34, 0xFF090A0C);
        graphics.fill(47, editorTop - 2, width, height - 34, 0xFF050607);
        int titleY = 8;
        for (var line : font.split(Component.literal(file.getFileName().toString()), Math.max(1, width - 24))) {
            graphics.drawString(font, line, 12, titleY, 0xFFE4EAF2, false);
            titleY += font.lineHeight;
        }
        int lineHeight = font.lineHeight;
        int lineY = editorTop + 4 - (int) editor.scroll();
        String[] lines = editor.getValue().split("\\R", -1);
        graphics.enableScissor(0, editorTop, 46, height - 36);
        for (int line = 0; line < lines.length && lineY < height - 38; line++) {
            String number = Integer.toString(line + 1);
            if (lineY >= editorTop - font.lineHeight) graphics.drawString(font, number, 40 - font.width(number), lineY,
                    diagnostic != null && diagnostic.line() == line + 1 ? 0xFFFF6666 : 0xFF68717D, false);
            lineY += Math.max(1, font.split(Component.literal(lines[line]), Math.max(1, editor.getWidth() - 8)).size()) * lineHeight;
        }
        graphics.disableScissor();
        if (message != null) {
            Component detail = diagnostic == null ? Component.translatable("screen.xero_quest_journal." + message)
                    : Component.translatable("screen.xero_quest_journal.source.diagnostic",
                    diagnostic.line(), diagnostic.column(), diagnostic.reason());
            var messageLines = font.split(detail,
                    Math.max(1, width - 24));
            for (int line = 0; line < Math.min(2, messageLines.size()); line++) {
                graphics.drawString(font, messageLines.get(line), 12, height - 23 + line * font.lineHeight,
                        message.equals("source.saved") ? 0xFF97D4C5 : 0xFFF2CF83, false);
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y) {
                boolean forward = key == GLFW.GLFW_KEY_Y || (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
                restore(forward ? redo : undo, forward ? undo : redo);
                return true;
            }
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (pending == null) closeTo(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class SourceBox extends SmoothTextBox {
        private SourceBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height,
                          Component message, Component placeholder) {
            super(font, x, y, width, height, message, placeholder);
        }

        private double scroll() {
            return scrollAmount();
        }

        private void restoreScroll(double value) {
            setScrollAmount(value);
        }

        @Override
        protected void renderBackground(GuiGraphics graphics) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF050607);
        }
    }
}
