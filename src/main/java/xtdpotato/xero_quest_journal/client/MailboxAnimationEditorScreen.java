package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Per-event HTML/CSS/JS sources with a live preview of the native mailbox rendering. */
public final class MailboxAnimationEditorScreen extends AnimatedQuestScreen {
    private static final int SOURCE_LIMIT = 16384;
    private final Screen parent;
    private final MailboxAnimationStore.Config draft;
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private ButtonStyleSheet styles;
    private MultiLineEditBox source;
    private String event = "favorite";
    private String language = "css";
    private String lastValue = "";
    private boolean restoring;
    private MailFavoriteButton previewStar;
    private String message;
    private int previewLeft, previewTop, previewRight, previewBottom;

    public MailboxAnimationEditorScreen(Screen parent) {
        super(text("animation.title"));
        this.parent = parent;
        this.draft = MailboxAnimationStore.copy(MailboxAnimationStore.get());
        MailboxAnimationStore.preview(draft);
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.mailbox." + key);
    }

    @Override protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        if (source != null) storeSource();
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int editorWidth = width >= 560 ? Math.max(290, width * 55 / 100) : width;
        int toolsY = 38;
        int sourceTop = 96;
        int sourceBottom = width >= 560 ? height - 40 : Math.max(sourceTop + 48, height * 60 / 100);
        previewLeft = width >= 560 ? editorWidth + 8 : 12;
        previewTop = width >= 560 ? 72 : sourceBottom + 6;
        previewRight = width - 12;
        previewBottom = height - 40;
        int eventWidth = Math.min(190, Math.max(112, editorWidth - 108));
        addRenderableWidget(new StyledChoiceButton<>(12, toolsY, eventWidth, 22,
                text("animation.event"), MailboxAnimationStore.EVENTS, event,
                value -> text("animation.event." + value), value -> {
                    storeSource();
                    event = value;
                    undo.clear(); redo.clear();
                    init();
                }, styles, "mail-animation-event"));
        int tabX = 12 + eventWidth + 6;
        for (String tab : List.of("html", "css", "js")) {
            var button = new StyledButton(tabX, toolsY, 29, 22, Component.literal(tab.toUpperCase()),
                    ignored -> {
                        storeSource();
                        language = tab;
                        undo.clear(); redo.clear();
                        init();
                    }, styles, "mail-animation-" + tab, "toolbar-button");
            button.active = !language.equals(tab);
            addRenderableWidget(button);
            tabX += 32;
        }
        source = new SmoothTextBox(font, 12, sourceTop, Math.max(80, editorWidth - 24),
                Math.max(32, sourceBottom - sourceTop), title, text("animation.hint"));
        source.setCharacterLimit(SOURCE_LIMIT);
        lastValue = sourceValue();
        source.setValue(lastValue);
        source.setValueListener(this::changed);
        addRenderableWidget(source);
        int buttonY = height - 30;
        var undoButton = new EditorIconButton(12, 67, text("animation.undo"), () -> restore(undo, redo),
                styles, "mail-animation-undo", "undo");
        var redoButton = new EditorIconButton(40, 67, text("animation.redo"), () -> restore(redo, undo),
                styles, "mail-animation-redo", "redo");
        addRenderableWidget(undoButton);
        addRenderableWidget(redoButton);
        addRenderableWidget(new StyledButton(Math.max(74, width - 152), buttonY, 68, 22,
                text("animation.save"), ignored -> save(), styles, "mail-animation-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 78, buttonY, 66, 22,
                text("animation.cancel"), ignored -> onClose(), styles, "mail-animation-cancel", "toolbar-button"));
        previewStar = new MailFavoriteButton((previewLeft + previewRight) / 2 - 14,
                previewTop + 54, 28, event.equals("favorite"), ignored -> previewStar.trigger(!previewStar.targetStarred()),
                styles);
        addRenderableWidget(previewStar);
        previewStar.visible = !List.of("notice", "new_badge").contains(event);
        setInitialFocus(source);
        MailboxAnimationStore.preview(draft);
    }

    private String sourceValue() {
        var animation = draft.events.get(event);
        return switch (language) {
            case "html" -> animation.html;
            case "js" -> animation.js;
            default -> animation.css;
        };
    }

    private void storeSource() {
        if (source == null) return;
        String value = source.getValue();
        var animation = draft.events.get(event);
        switch (language) {
            case "html" -> animation.html = value;
            case "js" -> animation.js = value;
            default -> animation.css = value;
        }
        MailboxAnimationStore.preview(draft);
    }

    private void changed(String value) {
        if (!restoring && !value.equals(lastValue)) {
            undo.push(lastValue);
            while (undo.size() > 128) undo.removeLast();
            redo.clear();
        }
        lastValue = value;
        storeSource();
    }

    private void restore(Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return;
        to.push(source.getValue());
        restoring = true;
        try { source.setValue(from.pop()); }
        finally { restoring = false; }
        setFocused(source);
    }

    private void save() {
        storeSource();
        if (!MailboxAnimationStore.save(draft)) {
            message = "animation.save_failed";
            return;
        }
        minecraft.setScreen(parent);
    }

    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop(graphics, 0xFF161A20);
        graphics.drawString(font, title, 12, 12, 0xFFF0D59B, false);
        graphics.drawString(font, text("animation.preview"), previewLeft, previewTop - 15, 0xFFD8E1EC, false);
        graphics.fill(previewLeft, previewTop, previewRight, previewBottom, 0xFF242C36);
        graphics.renderOutline(previewLeft, previewTop, previewRight - previewLeft,
                previewBottom - previewTop, 0xFF637589);
        if (event.startsWith("row_")) {
            int rowWidth = Math.min(220, previewRight - previewLeft - 20);
            int rowX = (previewLeft + previewRight - rowWidth) / 2;
            int rowY = previewTop + 108;
            MailboxRuntime.View sample = new MailboxRuntime.View("preview", "Quest", "Sample mail",
                    "", 0, 0, false, false, List.of());
            MailRowButton row = new MailRowButton(rowX, rowY, rowWidth, 42, sample,
                    ignored -> {}, styles);
            row.setSelectionHighlighted(event.equals("row_selected"));
            row.render(graphics, mouseX, mouseY, partialTick);
        }
        if (event.equals("notice")) {
            int width = Math.min(previewRight - previewLeft - 16,
                    Math.round(MailboxAnimationStore.scalar("notice", "width", 250, 80, 1600)));
            int height = Math.round(MailboxAnimationStore.scalar("notice", "height", 30, 18, 100));
            int x = (previewLeft + previewRight - width) / 2;
            int y = (previewTop + previewBottom - height) / 2;
            MailArrivalToast.drawBanner(graphics, x, y, width, height, 1);
        }
        if (event.equals("new_badge")) MailRowButton.newBadge(graphics,
                (previewLeft + previewRight) / 2 - 20, previewTop + 40, 40);
        if (message != null) graphics.drawString(font, text(message), 12, height - 43, 0xFFFF8C8C, false);
        super.renderContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_S) { save(); return true; }
            if (key == GLFW.GLFW_KEY_Z) { restore(undo, redo); return true; }
            if (key == GLFW.GLFW_KEY_Y) { restore(redo, undo); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() {
        MailboxAnimationStore.clearPreview();
        closeTo(parent);
    }

    @Override protected int animationHeaderBottom() { return 96; }
    @Override protected int animationFooterTop() { return height - 40; }
}
