package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Local keyframe editor for the shared popup-menu transition curve. */
final class PopupAnimationEditorScreen extends AnimatedQuestScreen {
    private static final int KEYFRAME_GAP = 4;
    private final Screen parent;
    private final boolean taskToast;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final List<PopupAnimationProfile.Keyframe> frames;
    private int selected;
    private double playhead;
    private boolean timelineDragging, playing, repeatPreview;
    private int draggingFrame = -1;
    private long playbackStarted;
    private String timeValue, scaleValue, xValue, yValue, rotationValue, opacityValue;
    private String message = "";

    PopupAnimationEditorScreen(Screen parent) {
        this(parent, false);
    }

    PopupAnimationEditorScreen(Screen parent, boolean taskToast) {
        super(label(taskToast ? "task_toast_animation.title" : "animation.title"));
        this.parent = parent;
        this.taskToast = taskToast;
        var preferences = ClientPreferences.get();
        frames = new ArrayList<>((taskToast ? preferences.taskToastAnimation
                : preferences.popupAnimation).keyframes());
        loadFields();
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.config." + key, args);
    }
    private int trackingGutter() { return width >= 800 ? Math.min(320, Math.max(280, width * 30 / 100)) : 0; }
    private int panelWidth() { return Math.max(1, Math.min(920, width - trackingGutter() - 16)); }
    private int panelHeight() { return Math.max(1, Math.min(620, height - 24)); }
    private int left() { return trackingGutter() == 0 ? (width - panelWidth()) / 2 : trackingGutter() + 8; }
    private int top() { return (height - panelHeight()) / 2; }
    private int inset() { return Math.min(20, Math.max(8, (panelWidth() - 1) / 2)); }
    private int trackLeft() { return left() + inset(); }
    private int trackRight() { return left() + panelWidth() - inset(); }
    private int trackY() { return top() + 82; }
    private int previewTop() { return top() + 128; }
    private int controlsTop() { return top() + panelHeight() - 190; }
    private int footerY() { return top() + panelHeight() - 31; }
    private int fieldWidth() { return Math.max(1, (panelWidth() - inset() * 2 - 16) / 3); }
    private int fieldX(int column) { return left() + inset() + column * (fieldWidth() + 8); }

    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() { return viewport(left(), top(), left() + panelWidth(), top() + panelHeight()); }
    @Override protected Viewport modalScrimBounds() { return viewport(left(), 0, width, height); }
    @Override protected boolean preserveTrackingHudBehind() { return true; }
    @Override protected void renderModalBackground(GuiGraphics g, float delta) {
        if (parent instanceof AnimatedQuestScreen animated) animated.renderContents(g, -10000, -10000, delta);
        else if (parent != null) parent.render(g, -10000, -10000, delta);
        QuestHudRenderer.render(g);
    }

    @Override protected void init() {
        clearWidgets();
        int fieldW = fieldWidth();
        addField(0, timeValue, "animation.time", fieldX(0), controlsTop() + 12, fieldW);
        addField(1, scaleValue, "animation.scale", fieldX(1), controlsTop() + 12, fieldW);
        addField(5, opacityValue, "animation.opacity", fieldX(2), controlsTop() + 12, fieldW);
        addField(2, xValue, "animation.move_x", fieldX(0), controlsTop() + 52, fieldW);
        addField(3, yValue, "animation.move_y", fieldX(1), controlsTop() + 52, fieldW);
        addField(4, rotationValue, "animation.rotation", fieldX(2), controlsTop() + 52, fieldW);

        int actionY = controlsTop() + 88;
        int gap = 6, cellW = Math.max(1, (panelWidth() - inset() * 2 - gap * 2) / 3);
        int x = left() + inset();
        addRenderableWidget(new StyledChoiceButton<>(x, actionY, cellW, 22,
                label("animation.easing"), List.of("smooth", "linear", "ease_in", "ease_out"),
                selectedFrame().easing(), value -> label("animation.easing." + value), value -> {
                    commitFields();
                    if (!message.isEmpty()) return;
                    replace(selectedFrame().easing(value));
                    loadFields();
                }, styles, "popup-easing"));
        addRenderableWidget(new StyledButton(x + cellW + gap, actionY, cellW, 22,
                label("animation.add_keyframe"), ignored -> addKeyframe(), styles, "popup-add-keyframe"));
        var remove = addRenderableWidget(new StyledButton(x + (cellW + gap) * 2, actionY, cellW, 22,
                label("animation.remove_keyframe"), ignored -> removeKeyframe(), styles, "popup-remove-keyframe"));
        remove.active = selected > 0 && selected < frames.size() - 1;
        int footerW = Math.max(1, (panelWidth() - inset() * 2 - 12) / 3);
        int footerX = left() + inset();
        addRenderableWidget(new StyledButton(footerX, footerY(), footerW, 22,
                label(playing ? "animation.pause_preview" : "animation.preview"),
                ignored -> togglePreview(), styles, "popup-animation-preview"));
        addRenderableWidget(Checkbox.builder(label("animation.repeat"), font).pos(footerX, footerY() - 24)
                .selected(repeatPreview).onValueChange((box, value) -> repeatPreview = value).build());
        addRenderableWidget(new StyledButton(footerX + footerW + 6, footerY(), footerW, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "popup-animation-cancel"));
        addRenderableWidget(new StyledButton(footerX + (footerW + 6) * 2, footerY(), footerW, 22,
                label("animation.save"), ignored -> save(), styles, "popup-animation-save"));
    }

    private void addField(int index, String value, String key, int x, int y, int width) {
        EditBox box = new EditBox(font, x, y, width, 20, label(key));
        box.setMaxLength(12);
        box.setValue(value == null ? "" : value);
        box.setResponder(next -> setField(index, next));
        addRenderableWidget(box);
    }

    private PopupAnimationProfile.Keyframe selectedFrame() { return frames.get(selected); }
    private void loadFields() {
        var key = selectedFrame();
        timeValue = Long.toString(Math.round(key.time() * 100));
        scaleValue = Double.toString(key.scale());
        xValue = Double.toString(key.x());
        yValue = Double.toString(key.y());
        rotationValue = Double.toString(key.rotation());
        opacityValue = Double.toString(key.opacity());
        playhead = key.time();
    }

    private void setField(int index, String value) {
        switch (index) {
            case 0 -> timeValue = value;
            case 1 -> scaleValue = value;
            case 2 -> xValue = value;
            case 3 -> yValue = value;
            case 4 -> rotationValue = value;
            case 5 -> opacityValue = value;
        }
        if (index == 0) {
            try {
                double time = Math.clamp(Double.parseDouble(value.strip()) / 100, 0, 1);
                playhead = time;
                if (selected > 0 && selected + 1 < frames.size()
                        && time > frames.get(selected - 1).time()
                        && time < frames.get(selected + 1).time()) {
                    replace(selectedFrame().at(time));
                }
            } catch (RuntimeException ignored) {}
        }
    }

    private void commitFields() {
        try {
            double time = Double.parseDouble(timeValue.strip()) / 100;
            var key = new PopupAnimationProfile.Keyframe(time, Double.parseDouble(scaleValue.strip()),
                    Double.parseDouble(xValue.strip()), Double.parseDouble(yValue.strip()),
                    Double.parseDouble(rotationValue.strip()), Double.parseDouble(opacityValue.strip()),
                    selectedFrame().easing());
            if (selected == 0 && time != 0 || selected == frames.size() - 1 && time != 1)
                throw new IllegalArgumentException();
            if (selected > 0 && time <= frames.get(selected - 1).time()
                    || selected + 1 < frames.size() && time >= frames.get(selected + 1).time())
                throw new IllegalArgumentException();
            replace(key);
            message = "";
        } catch (RuntimeException invalid) {
            message = label("animation.invalid_keyframe").getString();
        }
    }

    private void replace(PopupAnimationProfile.Keyframe key) {
        frames.set(selected, key);
        new PopupAnimationProfile(frames);
    }

    private PopupAnimationProfile currentProfile() { return new PopupAnimationProfile(frames); }

    private void addKeyframe() {
        commitFields();
        if (!message.isEmpty() || frames.size() >= PopupAnimationProfile.MAX_KEYFRAMES) return;
        int interval = selected == frames.size() - 1 ? selected - 1 : selected;
        double left = frames.get(interval).time(), right = frames.get(interval + 1).time();
        if (right - left <= KEYFRAME_GAP / 1000.0) {
            message = label("animation.no_timeline_room").getString();
            return;
        }
        double at = playhead <= left + KEYFRAME_GAP / 1000.0
                || playhead >= right - KEYFRAME_GAP / 1000.0
                ? (left + right) / 2
                : playhead;
        var transform = currentProfile().sample(at);
        frames.add(interval + 1, new PopupAnimationProfile.Keyframe(at, transform.scale(), transform.x(),
                transform.y(), transform.rotation(), transform.opacity(), "smooth"));
        selected = interval + 1;
        loadFields();
        init();
    }

    private void removeKeyframe() {
        commitFields();
        if (selected == 0 || selected == frames.size() - 1 || !message.isEmpty()) return;
        frames.remove(selected);
        selected = Math.min(selected, frames.size() - 2);
        loadFields();
        init();
    }

    private void togglePreview() {
        commitFields();
        if (!message.isEmpty()) return;
        playing = !playing;
        playbackStarted = System.nanoTime();
        if (playing) playhead = 0;
        init();
    }

    private void save() {
        commitFields();
        if (!message.isEmpty()) return;
        var preferences = ClientPreferences.get();
        if (taskToast) preferences.taskToastAnimation = currentProfile();
        else preferences.popupAnimation = currentProfile();
        if (preferences.save()) {
            closeTo(parent);
        } else {
            message = label("animation.save_failed").getString();
        }
    }

    @Override public void tick() {
        if (!playing) return;
        double duration = Math.max(100, ClientPreferences.get().popupAnimationMillis);
        double progress = (System.nanoTime() - playbackStarted) / 1_000_000.0 / duration;
        if (!repeatPreview && progress >= 1) {
            playhead = 1;
            playing = false;
            init();
        } else playhead = repeatPreview ? progress % 1 : Math.min(1, progress);
    }

    private int timelineX(double time) {
        return trackLeft() + (int) Math.round((trackRight() - trackLeft()) * Math.clamp(time, 0, 1));
    }
    private double timelineTime(double x) {
        return Math.clamp((x - trackLeft()) / Math.max(1.0, trackRight() - trackLeft()), 0, 1);
    }

    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        int pad = inset();
        g.fill(x, y, x + w, y + h, 0xFF242A34);
        g.fill(x, y, x + w, y + 40, 0xFF333D4B);
        g.fill(x, y + 39, x + w, y + 40, 0xFF66768A);
        g.drawString(font, label(taskToast ? "task_toast_animation.title" : "animation.title"),
                x + pad, y + 13, 0xFFF3D48B, false);
        g.drawString(font, label("animation.timeline"), x + pad, y + 51, 0xFFD4DCE7, false);
        int ty = trackY();
        g.fill(trackLeft(), ty, trackRight(), ty + 3, 0xFF596576);
        for (int i = 0; i <= 4; i++) {
            int tx = timelineX(i / 4.0);
            g.fill(tx, ty - 3, tx + 1, ty + 7, 0xFF8793A4);
            String percent = Integer.toString(i * 25) + "%";
            int labelX = Math.clamp(tx - font.width(percent) / 2, trackLeft(), trackRight() - font.width(percent));
            g.drawString(font, percent, labelX, ty + 12, 0xFFADB8C8, false);
        }
        for (int i = 0; i < frames.size(); i++) {
            int tx = timelineX(frames.get(i).time());
            int color = i == selected ? 0xFFFFD071 : 0xFFE3E9F2;
            g.fill(tx - 3, ty - 5, tx + 4, ty + 6, color);
            if (i == selected) {
                String frame = label("animation.frame", i + 1, frames.size()).getString();
                int frameX = Math.clamp(tx - font.width(frame) / 2, trackLeft(), trackRight() - font.width(frame));
                g.drawString(font, frame, frameX, ty - 21, 0xFFF3D48B, false);
            }
        }
        int px = timelineX(playhead);
        g.fill(px, ty - 8, px + 1, ty + 8, 0xFFFF8276);

        int previewX = x + pad, previewY = previewTop(), previewW = w - pad * 2;
        int previewH = Math.max(1, controlsTop() - previewY - 25);
        g.drawString(font, label("animation.preview"), previewX, previewY - 15, 0xFFB8C7D9, false);
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF1B2028);
        g.renderOutline(previewX, previewY, previewW, previewH, 0xFF586677);
        drawPreview(g, previewX, previewY, previewW, previewH, currentProfile().sample(playhead));
        String time = label("animation.time_percent", Math.round(playhead * 100)).getString();
        g.drawString(font, time, previewX, previewY + previewH + 7, 0xFFB8C7D9, false);
        g.fill(x + pad, controlsTop() - 4, x + w - pad, controlsTop() - 3, 0xFF465261);
        String[] fieldKeys = {"animation.time", "animation.scale", "animation.opacity",
                "animation.move_x", "animation.move_y", "animation.rotation"};
        for (int i = 0; i < fieldKeys.length; i++) {
            int column = i % 3;
            int rowY = controlsTop() + (i < 3 ? 0 : 40);
            g.drawString(font, label(fieldKeys[i]), fieldX(column), rowY,
                    i < 3 ? 0xFFE7CF9D : 0xFFB8C7D9, false);
        }
        super.renderContents(g, mouseX, mouseY, delta);
        int statusY = controlsTop() + 115;
        int statusHeight = Math.max(16, footerY() - statusY - 4);
        g.fill(x + pad, statusY, x + w - pad, statusY + statusHeight,
                message.isEmpty() ? 0xFF29323D : 0xFF482C34);
        g.renderOutline(x + pad, statusY, w - pad * 2, statusHeight,
                message.isEmpty() ? 0xFF465261 : 0xFFB96267);
        Component status = message.isEmpty()
                ? label("animation.valid") : Component.literal(message);
        g.drawString(font, font.plainSubstrByWidth(status.getString(), w - pad * 2 - 12),
                x + pad + 6, statusY + Math.max(2, (statusHeight - font.lineHeight) / 2),
                message.isEmpty() ? 0xFFB8C7D9 : 0xFFFF9A91, false);
    }

    private void drawPreview(GuiGraphics g, int x, int y, int w, int h, PopupAnimationProfile.Transform transform) {
        double scale = transform.scale();
        double cx = x + w / 2.0 + transform.x(), cy = y + h / 2.0 + transform.y();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        if (transform.rotation() != 0)
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) transform.rotation()));
        g.pose().scale((float) scale, (float) scale, 1);
        int boxW = Math.min(720, Math.max(1, w - 64)), boxH = Math.min(260, Math.max(1, h - 80));
        int alpha = (int) Math.round(transform.opacity() * 255);
        int a = alpha << 24;
        int left = -boxW / 2, top = -boxH / 2;
        g.fill(left, top, left + boxW, top + boxH, a | 0x00303A48);
        g.renderOutline(left, top, boxW, boxH, a | 0x00D8BB7A);
        g.fill(left, top, left + 4, top + boxH, a | 0x00D8BB7A);
        g.fill(left + 12, top + 12, left + boxW - 12, top + 14, a | 0x00F3D48B);
        var preview = label("animation.preview_sample");
        g.drawCenteredString(font, preview, 0, top + boxH / 2 - font.lineHeight / 2,
                a | 0x00E8EDF2);
        g.pose().popPose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && mouseY >= trackY() - 14 && mouseY <= trackY() + 24
                && mouseX >= trackLeft() && mouseX <= trackRight()) {
            int nearest = -1;
            double distance = Double.MAX_VALUE;
            for (int i = 0; i < frames.size(); i++) {
                double next = Math.abs(mouseX - timelineX(frames.get(i).time()));
                if (next < distance) { distance = next; nearest = i; }
            }
            if (distance <= 16 && nearest >= 0) {
                selected = nearest;
                loadFields();
                // The profile endpoints are fixed at 0% and 100%; they can be
                // selected for inspection but only interior frames may move.
                draggingFrame = nearest > 0 && nearest < frames.size() - 1 ? nearest : -1;
                timelineDragging = draggingFrame >= 0;
                init();
            } else {
                timelineDragging = true;
                playhead = timelineTime(mouseX);
                playing = false;
                updateTimelineField();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateTimelineField() {
        timeValue = Long.toString(Math.round(playhead * 100));
        if (children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().isPresent()) {
            var boxes = children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            if (!boxes.isEmpty()) boxes.getFirst().setValue(timeValue);
        }
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && timelineDragging) {
            double time = timelineTime(x);
            if (draggingFrame >= 0) {
                int previous = draggingFrame == 0 ? 0 : (int) Math.round(frames.get(draggingFrame - 1).time() * 100);
                int next = draggingFrame == frames.size() - 1 ? 100
                        : (int) Math.round(frames.get(draggingFrame + 1).time() * 100);
                int frame = Math.clamp((int) Math.round(time * 100), previous + 1, next - 1);
                replace(selectedFrame().at(frame / 100.0));
                loadFields();
                playhead = frame / 100.0;
            } else {
                playhead = time;
                updateTimelineField();
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && timelineDragging) {
            timelineDragging = false;
            draggingFrame = -1;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {}
    @Override public void onClose() { if (!playing) closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
