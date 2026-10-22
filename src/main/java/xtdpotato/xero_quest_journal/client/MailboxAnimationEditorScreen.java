package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Checkbox;
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
    private MailboxActionButton previewAction;
    private boolean playing, repeatPreview;
    private long playbackStarted;
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
                    source = null;
                    playing = false;
                    event = value;
                    undo.clear(); redo.clear();
                    init();
                }, styles, "mail-animation-event"));
        int tabX = 12 + eventWidth + 6;
        for (String tab : List.of("html", "css", "js")) {
            var button = new StyledButton(tabX, toolsY, 29, 22, Component.literal(tab.toUpperCase()),
                    ignored -> {
                        storeSource();
                        source = null;
                        playing = false;
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
        var play = new StyledButton(76, 67, 112, 22,
                text(playing ? "animation.replay" : "animation.play"),
                ignored -> playPreview(), styles, "mail-animation-play", "toolbar-button");
        addRenderableWidget(play);
        addRenderableWidget(Checkbox.builder(text("animation.repeat"), font).pos(194, 69)
                .selected(repeatPreview).onValueChange((box, value) -> repeatPreview = value).build());
        addRenderableWidget(new StyledButton(Math.max(74, width - 152), buttonY, 68, 22,
                text("animation.save"), ignored -> save(), styles, "mail-animation-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 78, buttonY, 66, 22,
                text("animation.cancel"), ignored -> onClose(), styles, "mail-animation-cancel", "toolbar-button"));
        previewStar = new MailFavoriteButton((previewLeft + previewRight) / 2 - 14,
                previewTop + 54, 28, event.equals("favorite"), ignored -> previewStar.trigger(!previewStar.targetStarred()),
                styles);
        addRenderableWidget(previewStar);
        previewStar.visible = isFavoriteEvent(event);
        previewAction = null;
        if (!event.startsWith("row_") && (event.endsWith("_hover") || event.endsWith("_click"))) {
            String actionKey = event.substring(0, event.lastIndexOf('_'));
            previewAction = new MailboxActionButton((previewLeft + previewRight) / 2 - 80,
                    previewTop + 52, 160, 26, text("animation.event." + event), ignored -> {},
                    styles, actionKey.equals("edit") ? "mail-edit-animations" : "mail-" + actionKey,
                    JournalNavButton.Kind.ACTION,
                    "mail_envelope", actionKey);
            previewAction.visible = true;
            addRenderableWidget(previewAction);
        }
        setInitialFocus(source);
        MailboxAnimationStore.preview(draft);
    }

    private static boolean isFavoriteEvent(String value) {
        return List.of("favorite", "unfavorite", "hover", "press").contains(value);
    }

    private void playPreview() {
        storeSource();
        playing = true;
        playbackStarted = System.nanoTime();
        if (previewStar != null && isFavoriteEvent(event)) {
            boolean target = event.equals("favorite") || event.equals("hover") || event.equals("press");
            previewStar.trigger(target);
        }
        if (previewAction != null && event.endsWith("_click")) previewAction.onPress();
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
        double progress = previewProgress();
        backdrop(graphics, 0xFF161A20);
        graphics.drawString(font, title, 12, 12, 0xFFF0D59B, false);
        graphics.drawString(font, text("animation.preview"), previewLeft, previewTop - 15, 0xFFD8E1EC, false);
        graphics.fill(previewLeft, previewTop, previewRight, previewBottom, 0xFF242C36);
        graphics.renderOutline(previewLeft, previewTop, previewRight - previewLeft,
                previewBottom - previewTop, 0xFF637589);
        int previewMouseX = mouseX, previewMouseY = mouseY;
        if (playing && event.equals("row_hover")) {
            previewMouseX = (previewLeft + previewRight) / 2;
            previewMouseY = previewTop + 128;
        } else if (playing && previewStar != null && isFavoriteEvent(event)) {
            previewMouseX = previewStar.getX() + previewStar.getWidth() / 2;
            previewMouseY = previewStar.getY() + previewStar.getHeight() / 2;
        } else if (playing && previewAction != null && event.endsWith("_hover")) {
            previewMouseX = previewAction.getX() + previewAction.getWidth() / 2;
            previewMouseY = previewAction.getY() + previewAction.getHeight() / 2;
        }
        if (event.startsWith("row_")) {
            int rowWidth = Math.min(220, previewRight - previewLeft - 20);
            int rowX = (previewLeft + previewRight - rowWidth) / 2;
            int rowY = previewTop + 108;
            MailboxRuntime.View sample = new MailboxRuntime.View("preview", "Quest", "Sample mail",
                    "", 0, 0, false, false, List.of());
            MailRowButton row = new MailRowButton(rowX, rowY, rowWidth, 42, sample,
                    ignored -> {}, styles);
            row.setSelectionHighlighted(event.equals("row_selected"));
            row.render(graphics, previewMouseX, previewMouseY, partialTick);
        }
        if (event.equals("notice")) {
            int width = Math.min(previewRight - previewLeft - 16,
                    Math.round(MailboxAnimationStore.scalar("notice", "width", 250, 80, 1600)));
            int height = Math.round(MailboxAnimationStore.scalar("notice", "height", 30, 18, 100));
            int x = (previewLeft + previewRight - width) / 2;
            int y = (previewTop + previewBottom - height) / 2;
            MailArrivalToast.drawBanner(graphics, x, y, width, height,
                    playing ? (float) Math.min(1, progress * 4) : 1);
        }
        if (event.equals("new_badge")) {
            graphics.pose().pushPose();
            float scale = playing ? .85F + .15F * (float) Math.min(1, progress * 4) : 1;
            int centerX = (previewLeft + previewRight) / 2, centerY = previewTop + 60;
            graphics.pose().translate(centerX, centerY, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.pose().translate(-centerX, -centerY, 0);
            MailRowButton.newBadge(graphics, centerX - 20, centerY - 20, 40);
            graphics.pose().popPose();
        }
        if (message != null) graphics.drawString(font, text(message), 12, height - 43, 0xFFFF8C8C, false);
        super.renderContents(graphics, previewMouseX, previewMouseY, partialTick);
    }

    private double previewProgress() {
        if (!playing) return 0;
        double duration = Math.max(100, MailboxAnimationStore.style(event).durationMillis());
        return Math.clamp((System.nanoTime() - playbackStarted) / (duration * 1_000_000.0), 0, 1);
    }

    @Override public void tick() {
        if (!playing || previewProgress() < 1) return;
        if (!repeatPreview) {
            playing = false;
            return;
        }
        playbackStarted = System.nanoTime();
        if (previewStar != null && isFavoriteEvent(event)) {
            boolean target = event.equals("favorite") || event.equals("hover") || event.equals("press");
            previewStar.trigger(target);
        }
        if (previewAction != null && event.endsWith("_click")) previewAction.onPress();
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
        playing = false;
        MailboxAnimationStore.clearPreview();
        closeTo(parent);
    }

    @Override protected int animationHeaderBottom() { return 96; }
    @Override protected int animationFooterTop() { return height - 40; }
}
