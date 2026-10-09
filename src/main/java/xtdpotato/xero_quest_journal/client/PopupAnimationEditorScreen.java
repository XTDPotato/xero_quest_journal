package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Local keyframe editor for the shared popup-menu transition curve. */
final class PopupAnimationEditorScreen extends AnimatedQuestScreen {
    private static final int KEYFRAME_GAP = 4;
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final List<PopupAnimationProfile.Keyframe> frames = new ArrayList<>(
            ClientPreferences.get().popupAnimation.keyframes());
    private int selected;
    private double playhead;
    private boolean timelineDragging, playing;
    private long playbackStarted;
    private String timeValue, scaleValue, xValue, yValue, rotationValue, opacityValue;
    private String message = "";

    PopupAnimationEditorScreen(Screen parent) {
        super(label("animation.editor"));
        this.parent = parent;
        loadFields();
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.config." + key, args);
    }
    private int panelWidth() { return Math.min(820, width - 24); }
    private int panelHeight() { return Math.min(480, height - 24); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int trackLeft() { return left() + 24; }
    private int trackRight() { return left() + panelWidth() - 24; }
    private int trackY() { return top() + 86; }
    private int previewTop() { return top() + 132; }
    private int controlsTop() { return top() + panelHeight() - 106; }
    private int footerY() { return top() + panelHeight() - 35; }

    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() { return viewport(left(), top(), left() + panelWidth(), top() + panelHeight()); }
    @Override protected void renderModalBackground(GuiGraphics g, float delta) {
        if (parent instanceof AnimatedQuestScreen animated) animated.renderContents(g, -10000, -10000, delta);
        else if (parent != null) parent.render(g, -10000, -10000, delta);
    }

    @Override protected void init() {
        clearWidgets();
        int x = left(), w = panelWidth(), fieldY = controlsTop() + 30;
        int gap = 6, fieldW = (w - 48 - gap * 5) / 6;
        String[] values = {timeValue, scaleValue, xValue, yValue, rotationValue, opacityValue};
        String[] keys = {"animation.time", "animation.scale", "animation.move_x", "animation.move_y",
                "animation.rotation", "animation.opacity"};
        for (int i = 0; i < values.length; i++) {
            int px = x + 24 + i * (fieldW + gap);
            EditBox box = new EditBox(font, px, fieldY, fieldW, 20, label(keys[i]));
            box.setMaxLength(12);
            box.setValue(values[i] == null ? "" : values[i]);
            final int index = i;
            box.setResponder(value -> setField(index, value));
            addRenderableWidget(box);
        }
        addRenderableWidget(new StyledChoiceButton<>(x + 24, fieldY + 28, 178, 20,
                label("animation.easing"), List.of("smooth", "linear", "ease_in", "ease_out"),
                selectedFrame().easing(), value -> label("animation.easing." + value), value -> {
                    commitFields();
                    replace(selectedFrame().easing(value));
                    loadFields();
                }, styles, "popup-easing"));
        addRenderableWidget(new StyledButton(x + 216, fieldY + 28, 106, 20, label("animation.add_keyframe"),
                ignored -> addKeyframe(), styles, "popup-add-keyframe"));
        var remove = addRenderableWidget(new StyledButton(x + 328, fieldY + 28, 120, 20,
                label("animation.remove_keyframe"), ignored -> removeKeyframe(), styles, "popup-remove-keyframe"));
        remove.active = selected > 0 && selected < frames.size() - 1;
        addRenderableWidget(new StyledButton(x + w - 218, footerY(), 92, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "popup-animation-cancel"));
        addRenderableWidget(new StyledButton(x + w - 118, footerY(), 94, 22,
                label("animation.save"), ignored -> save(), styles, "popup-animation-save"));
        addRenderableWidget(new StyledButton(x + 24, footerY(), 116, 22,
                label(playing ? "animation.pause_preview" : "animation.preview"),
                ignored -> togglePreview(), styles, "popup-animation-preview"));
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
            try { playhead = Math.clamp(Double.parseDouble(value.strip()) / 100, 0, 1); }
            catch (RuntimeException ignored) {}
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
        init();
    }

    private void save() {
        commitFields();
        if (!message.isEmpty()) return;
        var preferences = ClientPreferences.get();
        preferences.popupAnimation = currentProfile();
        if (preferences.save()) {
            closeTo(parent);
        } else {
            message = label("animation.save_failed").getString();
        }
    }

    @Override public void tick() {
        if (!playing) return;
        double duration = Math.max(100, ClientPreferences.get().popupAnimationMillis);
        playhead = ((System.nanoTime() - playbackStarted) / 1_000_000.0 % duration) / duration;
    }

    private int timelineX(double time) {
        return trackLeft() + (int) Math.round((trackRight() - trackLeft()) * Math.clamp(time, 0, 1));
    }
    private double timelineTime(double x) {
        return Math.clamp((x - trackLeft()) / Math.max(1.0, trackRight() - trackLeft()), 0, 1);
    }

    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        g.fill(x, y, x + w, y + h, 0xFF242A34);
        g.fill(x, y, x + w, y + 34, 0xFF333D4B);
        g.drawString(font, title, x + 18, y + 11, 0xFFF3D48B, false);
        g.drawString(font, label("animation.timeline"), x + 20, y + 49, 0xFFD4DCE7, false);
        int ty = trackY();
        g.fill(trackLeft(), ty, trackRight(), ty + 3, 0xFF596576);
        for (int i = 0; i <= 4; i++) {
            int tx = timelineX(i / 4.0);
            g.fill(tx, ty - 3, tx + 1, ty + 7, 0xFF8793A4);
            g.drawCenteredString(font, Integer.toString(i * 25) + "%", tx, ty + 11, 0xFFADB8C8);
        }
        for (int i = 0; i < frames.size(); i++) {
            int tx = timelineX(frames.get(i).time());
            int color = i == selected ? 0xFFFFD071 : 0xFFE3E9F2;
            g.fill(tx - 4, ty - 5, tx + 5, ty + 6, color);
            if (i == selected) g.drawCenteredString(font, Integer.toString(i + 1), tx, ty - 18, 0xFFF3D48B);
        }
        int px = timelineX(playhead);
        g.fill(px, ty - 8, px + 1, ty + 8, 0xFFFF8276);

        int previewX = x + 24, previewY = previewTop(), previewW = w - 48, previewH = controlsTop() - previewY - 8;
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF1B2028);
        g.renderOutline(previewX, previewY, previewW, previewH, 0xFF586677);
        drawPreview(g, previewX, previewY, previewW, previewH, currentProfile().sample(playhead));
        g.drawString(font, label("animation.time_percent", Math.round(playhead * 100)),
                x + 24, controlsTop() + 3, 0xFFB8C7D9, false);
        super.renderContents(g, mouseX, mouseY, delta);
        if (!message.isEmpty())
            g.drawString(font, font.plainSubstrByWidth(message, w - 48), x + 350, footerY() + 7, 0xFFFF8E83, false);
    }

    private void drawPreview(GuiGraphics g, int x, int y, int w, int h, PopupAnimationProfile.Transform transform) {
        double scale = transform.scale();
        double cx = x + w / 2.0 + transform.x(), cy = y + h / 2.0 + transform.y();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) transform.rotation()));
        g.pose().scale((float) scale, (float) scale, 1);
        int boxW = Math.min(280, w - 64), boxH = Math.min(86, h - 24);
        int alpha = (int) Math.round(transform.opacity() * 255);
        int a = alpha << 24;
        g.fill(-boxW / 2, -boxH / 2, boxW / 2, boxH / 2, a | 0xFFF7F4EC);
        g.renderOutline(-boxW / 2, -boxH / 2, boxW, boxH, a | 0x00E5C87F);
        g.fill(-boxW / 2 + 5, -boxH / 2 + 5, boxW / 2 - 5, -boxH / 2 + 7, a | 0x00FFE3AC);
        var preview = label("animation.preview_sample");
        g.drawCenteredString(font, preview, 0, -font.lineHeight / 2, a | 0x00465166);
        g.pose().popPose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && mouseY >= trackY() - 14 && mouseY <= trackY() + 24
                && mouseX >= trackLeft() && mouseX <= trackRight()) {
            commitFields();
            if (!message.isEmpty()) return true;
            int nearest = -1;
            double distance = Double.MAX_VALUE;
            for (int i = 0; i < frames.size(); i++) {
                double next = Math.abs(mouseX - timelineX(frames.get(i).time()));
                if (next < distance) { distance = next; nearest = i; }
            }
            if (distance <= 9 && nearest >= 0) {
                selected = nearest;
                loadFields();
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
            playhead = timelineTime(x);
            updateTimelineField();
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && timelineDragging) { timelineDragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {}
    @Override public void onClose() { if (!playing) closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
