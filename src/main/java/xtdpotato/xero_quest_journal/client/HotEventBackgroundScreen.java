package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;

import java.util.*;
import java.util.function.Consumer;

/** Shared background draft editor with a live, draggable preview and a scrollable inspector. */
class HotEventBackgroundScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final HotEventCanvas original;
    private final Consumer<HotEventCanvas> saved;
    private final String mediaOwner = "backdrop-editor-" + UUID.randomUUID();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final SmoothScroll scroll = new SmoothScroll();
    private final Set<AbstractWidget> body = new HashSet<>();
    private final Map<String, EditBox> inputs = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private String type, source, scale, offsetX, offsetY, mode, music;
    private String error = "";
    private int lastScroll;
    private boolean panning;
    private double dragX, dragY, startX, startY;
    private record Label(Component text, int y) {}

    HotEventBackgroundScreen(Screen parent, HotEventCanvas canvas, Consumer<HotEventCanvas> saved) {
        this(parent, canvas, saved, text("menu.background"));
    }
    HotEventBackgroundScreen(Screen parent, HotEventCanvas canvas, Consumer<HotEventCanvas> saved, Component title) {
        super(title);
        this.parent = parent; this.original = canvas; this.saved = saved;
        reset(canvas);
    }
    private void reset(HotEventCanvas canvas) {
        type = canvas.backgroundType(); source = canvas.backgroundImage();
        scale = Double.toString(canvas.backgroundScale());
        offsetX = Integer.toString(canvas.backgroundOffsetX()); offsetY = Integer.toString(canvas.backgroundOffsetY());
        mode = canvas.backgroundMode(); music = canvas.backgroundMusic();
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.hot_events." + key);
    }
    private int inspectorWidth() { return Math.min(278, Math.max(172, width / 3)); }
    private int inspectorX() { return width - inspectorWidth(); }
    private int bodyBottom() { return height - 44; }
    private int previewWidth() { return Math.max(1, inspectorX() - 24); }
    private int previewHeight() { return Math.max(1, height - 110); }
    @Override protected int animationHeaderBottom() { return 38; }
    @Override protected int animationFooterTop() { return height - 38; }

    @Override protected void init() {
        clearWidgets(); body.clear(); labels.clear(); inputs.clear();
        scroll.limit(348 - Math.max(1, bodyBottom() - 48));
        lastScroll = scroll.pixels();
        int x = inspectorX() + 12, w = inspectorWidth() - 32, y = 52 - lastScroll;
        label("background_type", y);
        addBody(new StyledChoiceButton<>(x, y + 13, w, 20, text("background_type"), List.of("image", "video"),
                type, value -> text("background_type." + value), value -> {
                    type = value; ClientMediaManager.release(mediaOwner); init();
                }, styles, "hot-background-type"));
        y += 48;
        field("background_source", source, x, y, w - 28, 4096, value -> {
            if (!source.equals(value)) ClientMediaManager.release(mediaOwner);
            source = value;
        });
        addBody(new EditorIconButton(x + w - 24, y + 11, text("choose_background"), () -> {
            if (type.equals("video")) QuestMediaPicker.choose(this, true, value -> source = value);
            else minecraft.setScreen(QuestScreen.pickResource(this, source, false, value -> source = value));
        }, styles, "hot-background-choose", "choose"));
        y += 48;
        label("background_mode", y);
        addBody(new StyledChoiceButton<>(x, y + 13, w, 20, text("background_mode"),
                List.of("cover", "contain", "stretch", "adaptive"), mode,
                value -> text("background_mode." + value), value -> mode = value, styles, "hot-background-mode"));
        y += 48;
        field("background_scale", scale, x, y, w - 56, 16, value -> scale = value);
        addBody(new EditorIconButton(x + w - 52, y + 11, text("scale_down"),
                () -> stepScale(-.1), styles, "hot-background-smaller", "back"));
        addBody(new EditorIconButton(x + w - 24, y + 11, text("scale_up"),
                () -> stepScale(.1), styles, "hot-background-larger", "add"));
        y += 48;
        field("background_offset_x", offsetX, x, y, w, 16, value -> offsetX = value);
        y += 48;
        field("background_offset_y", offsetY, x, y, w, 16, value -> offsetY = value);
        y += 48;
        field("background_music", music, x, y, w - 28, 4096, value -> {
            if (!music.equals(value)) ClientMediaManager.release(mediaOwner);
            music = value;
        });
        addBody(new EditorIconButton(x + w - 24, y + 11, text("background_music"),
                () -> QuestMediaPicker.choose(this, false, value -> music = value),
                styles, "hot-background-music", "choose"));
        addRenderableWidget(new ScrollRail(width - 9, 48, Math.max(1, bodyBottom() - 48), scroll));
        addRenderableWidget(new EditorIconButton(12, height - 30, text("reset_background"), () -> {
            reset(HotEventCanvas.empty()); error = ""; ClientMediaManager.release(mediaOwner); init();
        }, styles, "hot-background-reset", "refresh"));
        addRenderableWidget(new StyledButton(width - 154, height - 30, 68, 22,
                Component.translatable("gui.done"), ignored -> save(), styles, "hot-background-save"));
        addRenderableWidget(new StyledButton(width - 80, height - 30, 68, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "hot-background-cancel"));
    }
    private void stepScale(double delta) {
        try {
            scale = String.format(Locale.ROOT, "%.2f", Math.clamp(Double.parseDouble(scale) + delta, .1, 10));
            inputs.get("background_scale").setValue(scale);
        } catch (NumberFormatException invalid) { error = text("invalid_background").getString(); }
    }
    private void label(String key, int y) { labels.add(new Label(text(key), y)); }
    private void addBody(AbstractWidget widget) { body.add(widget); addRenderableWidget(widget); }
    private void field(String key, String value, int x, int y, int w, int limit, Consumer<String> changed) {
        label(key, y);
        var input = new EditBox(font, x, y + 13, Math.max(16, w), 20, text(key));
        input.setMaxLength(limit); input.setValue(value); input.setResponder(next -> {
            changed.accept(next); error = "";
        });
        inputs.put(key, input); addBody(input);
    }
    private HotEventCanvas draft() {
        return new HotEventCanvas(source.strip(), original.startId(), original.connections(), true,
                original.positions(), type, Double.parseDouble(scale.strip()),
                Integer.parseInt(offsetX.strip()), Integer.parseInt(offsetY.strip()), mode, music.strip(),
                original.knownCardIds());
    }
    private void save() {
        try {
            var next = draft();
            if (!next.backgroundImage().isBlank() && (type.equals("video")
                    ? !ClientMediaManager.validVideoSource(source)
                    : !ClientMediaManager.validImageSource(source) && !QuestIcons.valid(source)))
                throw new IllegalArgumentException("Invalid image/video");
            if (!music.isBlank() && !ClientMediaManager.validAudioSource(music))
                throw new IllegalArgumentException("Invalid music");
            saved.accept(next);
            closeTo(parent);
        } catch (IllegalArgumentException invalid) { error = text("invalid_background").getString(); }
    }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return body.contains(widget) ? viewport(inspectorX(), 48, width - 12, bodyBottom()) : null;
    }
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        backdrop(g, 0xFF191D23);
        scroll.advance();
        int change = scroll.pixels() - lastScroll;
        if (change != 0) {
            body.forEach(widget -> widget.setY(widget.getY() - change));
            labels.replaceAll(label -> new Label(label.text(), label.y() - change));
            lastScroll = scroll.pixels();
        }
        g.drawString(font, title, 14, 15, 0xFFF1D49B, false);
        g.fill(inspectorX(), 38, width, height - 38, 0xFF252B33);
        int pw = previewWidth(), ph = previewHeight();
        g.fill(12, 54, 12 + pw, 54 + ph, 0xFF303B40);
        g.enableScissor(12, 54, 12 + pw, 54 + ph);
        try {
            var next = draft();
            int w = Math.max(1, (int) Math.round(pw * next.backgroundScale()));
            int h = Math.max(1, (int) Math.round(ph * next.backgroundScale()));
            int x = 12 + (pw - w) / 2 + Math.round(next.backgroundOffsetX() * pw / (float) width);
            int y = 54 + (ph - h) / 2 + Math.round(next.backgroundOffsetY() * ph / (float) height);
            if (type.equals("video")) ClientMediaManager.renderVideo(g, mediaOwner, "background", source,
                    x, y, w, h, 1, true, true, true, 0, mode, "");
            else ClientMediaManager.renderImageScaled(g, source, x, y, w, h, 1, mode);
            if (!music.isBlank()) ClientMediaManager.renderAudio(g, mediaOwner, "music", music,
                    0, 0, 1, 1, 1, true, true, false, 1, false, "");
        } catch (IllegalArgumentException invalid) {
            g.drawCenteredString(font, text("invalid_background"), 12 + pw / 2, 64, 0xFFFF9C90);
        }
        g.disableScissor();
        g.renderOutline(12, 54, pw, ph, 0xFF72817F);
        g.enableScissor(inspectorX(), 48, width - 12, bodyBottom());
        for (var label : labels) g.drawString(font, label.text(), inspectorX() + 12, label.y(), 0xFFB6C4CB, false);
        g.disableScissor();
        if (!error.isBlank()) g.drawString(font, font.plainSubstrByWidth(error, previewWidth()), 12, height - 50, 0xFFFF9C90);
        super.renderContents(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && x >= 12 && x < 12 + previewWidth() && y >= 54 && y < 54 + previewHeight()) {
            try { startX = Integer.parseInt(offsetX); startY = Integer.parseInt(offsetY); }
            catch (NumberFormatException invalid) { return true; }
            panning = true; dragX = x; dragY = y; setFocused(null); return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!panning) return super.mouseDragged(x, y, button, dx, dy);
        inputs.get("background_offset_x").setValue(Integer.toString((int) Math.clamp(
                Math.round(startX + (x - dragX) * width / previewWidth()), -10000, 10000)));
        inputs.get("background_offset_y").setValue(Integer.toString((int) Math.clamp(
                Math.round(startY + (y - dragY) * height / previewHeight()), -10000, 10000)));
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (panning) { panning = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inputBlocked() || scrollChoicePopup(x, y, h, v)) return true;
        if (x >= inspectorX() && y >= 48 && y < bodyBottom()) { scroll.wheel(v * 30); return true; }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override public void removed() { panning = false; ClientMediaManager.release(mediaOwner); super.removed(); }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
