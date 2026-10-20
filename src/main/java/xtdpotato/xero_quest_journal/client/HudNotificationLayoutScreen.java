package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Visual placement editor for the tracked-task and item-pickup HUD notices. */
public final class HudNotificationLayoutScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String selected = "task", error = "";
    private boolean avoidMinimap, dragging, resizing, taskBackground, previewPlaying;
    private int taskX, taskY, taskScale, taskBackgroundOpacity, pickupX, pickupY, pickupScale, minimapCorner;
    private double grabX, grabY;
    private double resizeStartX, resizeStartY;
    private int resizeStartScale;
    private long previewStartedAt;
    private EditBox scaleInput, backgroundOpacityInput;
    private boolean syncing;

    public HudNotificationLayoutScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.notification_layout.title"));
        this.parent = parent;
        var prefs = ClientPreferences.get();
        taskX = prefs.taskToastXPercent;
        taskY = prefs.taskToastYPercent;
        taskScale = prefs.taskToastScalePercent;
        taskBackground = prefs.taskToastBackgroundEnabled;
        taskBackgroundOpacity = prefs.taskToastBackgroundOpacityPercent;
        pickupX = prefs.itemPickupToastXPercent;
        pickupY = prefs.itemPickupToastYPercent;
        pickupScale = prefs.itemPickupToastScalePercent;
        avoidMinimap = prefs.hudAutoAvoidMinimap;
        minimapCorner = switch (prefs.minimapCorner) {
            case "top_left" -> 0;
            case "bottom_left" -> 2;
            case "bottom_right" -> 3;
            default -> 1;
        };
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.notification_layout." + key, args);
    }

    private int sideWidth() { return Math.min(280, Math.max(218, width / 4)); }
    private int previewRight() { return width - sideWidth(); }
    private double sceneScale() {
        return Math.max(.1, Math.min((previewRight() - 36.0) / width, (height - 100.0) / height));
    }
    private double sceneX() { return 16 + (previewRight() - 28 - width * sceneScale()) / 2; }
    private double sceneY() { return 54 + (height - 86 - height * sceneScale()) / 2; }
    private int selectedX() { return selected.equals("task") ? taskX : pickupX; }
    private int selectedY() { return selected.equals("task") ? taskY : pickupY; }
    private int selectedScale() { return selected.equals("task") ? taskScale : pickupScale; }
    private void setSelectedX(int v) { if (selected.equals("task")) taskX = v; else pickupX = v; }
    private void setSelectedY(int v) { if (selected.equals("task")) taskY = v; else pickupY = v; }
    private void setSelectedScale(int v) {
        if (selected.equals("task")) taskScale = v; else pickupScale = v;
        if (scaleInput != null) {
            syncing = true;
            scaleInput.setValue(Integer.toString(v));
            syncing = false;
        }
    }

    @Override protected void init() {
        clearWidgets();
        int sx = previewRight() + 12;
        int sw = sideWidth() - 24;
        for (int i = 0; i < 2; i++) {
            String value = i == 0 ? "task" : "pickup";
            var tab = new StyledButton(sx + i * (sw / 2 + 2), 52, sw / 2 - 2, 24, text(value),
                    ignored -> { selected = value; init(); }, styles, "notification-layout-" + value);
            tab.setSelectionHighlighted(selected.equals(value));
            addRenderableWidget(tab);
        }
        addRenderableWidget(Checkbox.builder(text("avoid_minimap"), font).pos(sx, 91)
                .selected(avoidMinimap).onValueChange((box, value) -> avoidMinimap = value).build());
        addRenderableWidget(new StyledChoiceButton<>(sx, 126, sw, 24, text("minimap_corner"),
                java.util.List.of(0, 1, 2, 3), minimapCorner, value -> text("corner." + value),
                value -> minimapCorner = value, styles, "notification-minimap-corner"));
        addRenderableWidget(new StyledButton(sx, 163, 32, 24, Component.literal("-"), ignored ->
                setSelectedScale(Math.max(10, selectedScale() - 5)), styles, "notification-scale-less"));
        scaleInput = new EditBox(font, sx + 38, 165, sw - 76, 20, text("scale"));
        scaleInput.setValue(Integer.toString(selectedScale()));
        scaleInput.setMaxLength(3);
        scaleInput.setFilter(raw -> raw.isEmpty() || raw.matches("\\d{1,3}"));
        scaleInput.setResponder(raw -> {
            if (syncing || raw.isBlank()) return;
            try { setSelectedScale(Math.clamp(Integer.parseInt(raw), 10, 200)); }
            catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(scaleInput);
        addRenderableWidget(new StyledButton(sx + sw - 32, 163, 32, 24, Component.literal("+"), ignored ->
                setSelectedScale(Math.min(200, selectedScale() + 5)), styles, "notification-scale-more"));
        if (selected.equals("task")) {
            addRenderableWidget(Checkbox.builder(text("task_background"), font).pos(sx, 198)
                    .selected(taskBackground).onValueChange((box, value) -> taskBackground = value).build());
            addRenderableWidget(new StyledButton(sx, 235, 28, 23, Component.literal("-"), ignored ->
                    setBackgroundOpacity(Math.max(5, taskBackgroundOpacity - 5)),
                    styles, "notification-background-less"));
            backgroundOpacityInput = new EditBox(font, sx + 32, 237, sw - 64, 19, text("background_opacity"));
            backgroundOpacityInput.setValue(Integer.toString(taskBackgroundOpacity));
            backgroundOpacityInput.setMaxLength(3);
            backgroundOpacityInput.setFilter(raw -> raw.isEmpty() || raw.matches("\\d{1,3}"));
            backgroundOpacityInput.setResponder(raw -> {
                if (syncing || raw.isBlank()) return;
                try { setBackgroundOpacity(Math.clamp(Integer.parseInt(raw), 5, 100)); }
                catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(backgroundOpacityInput);
            addRenderableWidget(new StyledButton(sx + sw - 28, 235, 28, 23, Component.literal("+"), ignored ->
                    setBackgroundOpacity(Math.min(100, taskBackgroundOpacity + 5)),
                    styles, "notification-background-more"));
            addRenderableWidget(new StyledButton(sx, 270, sw, 23, text("edit_animation"), ignored ->
                    minecraft.setScreen(new PopupAnimationEditorScreen(this, true)),
                    styles, "notification-task-animation"));
        }
        addRenderableWidget(new StyledButton(sx, selected.equals("task") ? 304 : 198, sw, 23,
                text(previewPlaying ? "restart_preview" : "play_preview"), ignored -> playPreview(),
                styles, "notification-play-preview"));
        addRenderableWidget(new StyledButton(width - 148, height - 31, 64, 23,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "notification-layout-save"));
        addRenderableWidget(new StyledButton(width - 76, height - 31, 64, 23,
                Component.translatable("gui.cancel"), ignored -> onClose(),
                styles, "notification-layout-cancel"));
    }

    private void setBackgroundOpacity(int value) {
        taskBackgroundOpacity = value;
        if (backgroundOpacityInput != null) {
            syncing = true;
            backgroundOpacityInput.setValue(Integer.toString(value));
            syncing = false;
        }
    }

    private void playPreview() {
        previewStartedAt = System.nanoTime();
        previewPlaying = true;
    }

    @Override public void tick() {
        if (!previewPlaying) return;
        long duration = selected.equals("task")
                ? Math.max(100, ClientPreferences.get().popupAnimationMillis) * 1_000_000L
                : 900_000_000L;
        if (System.nanoTime() - previewStartedAt >= duration) previewPlaying = false;
    }

    private void save() {
        var prefs = ClientPreferences.get();
        int oldTaskX = prefs.taskToastXPercent, oldTaskY = prefs.taskToastYPercent;
        int oldTaskScale = prefs.taskToastScalePercent;
        boolean oldTaskBackground = prefs.taskToastBackgroundEnabled;
        int oldTaskBackgroundOpacity = prefs.taskToastBackgroundOpacityPercent;
        int oldPickupX = prefs.itemPickupToastXPercent, oldPickupY = prefs.itemPickupToastYPercent;
        int oldPickupScale = prefs.itemPickupToastScalePercent;
        boolean oldAvoid = prefs.hudAutoAvoidMinimap;
        String oldCorner = prefs.minimapCorner;
        prefs.taskToastXPercent = taskX;
        prefs.taskToastYPercent = taskY;
        prefs.taskToastScalePercent = taskScale;
        prefs.taskToastBackgroundEnabled = taskBackground;
        prefs.taskToastBackgroundOpacityPercent = taskBackgroundOpacity;
        prefs.itemPickupToastXPercent = pickupX;
        prefs.itemPickupToastYPercent = pickupY;
        prefs.itemPickupToastScalePercent = pickupScale;
        prefs.hudAutoAvoidMinimap = avoidMinimap;
        prefs.minimapCorner = switch (minimapCorner) {
            case 0 -> "top_left";
            case 2 -> "bottom_left";
            case 3 -> "bottom_right";
            default -> "top_right";
        };
        if (prefs.save()) minecraft.setScreen(parent);
        else {
            prefs.taskToastXPercent = oldTaskX;
            prefs.taskToastYPercent = oldTaskY;
            prefs.taskToastScalePercent = oldTaskScale;
            prefs.taskToastBackgroundEnabled = oldTaskBackground;
            prefs.taskToastBackgroundOpacityPercent = oldTaskBackgroundOpacity;
            prefs.itemPickupToastXPercent = oldPickupX;
            prefs.itemPickupToastYPercent = oldPickupY;
            prefs.itemPickupToastScalePercent = oldPickupScale;
            prefs.hudAutoAvoidMinimap = oldAvoid;
            prefs.minimapCorner = oldCorner;
            error = "save_failed";
        }
    }

    private int[] selectedRect() {
        int panelWidth = selected.equals("task") ? 430 : 760;
        int panelHeight = selected.equals("task") ? 90 : 265;
        double factor = sceneScale();
        double size = selectedScale() / 100.0;
        int scaledWidth = Math.max(1, (int) Math.round(panelWidth * size));
        if (selected.equals("pickup")) scaledWidth = Math.min(scaledWidth, Math.max(1, width - 20));
        int scaledHeight = Math.max(1, (int) Math.round(panelHeight * size));
        int maxX = Math.max(0, width - scaledWidth);
        int maxY = Math.max(0, height - scaledHeight);
        return new int[]{(int) Math.round(sceneX() + maxX * selectedX() / 100.0 * factor),
                (int) Math.round(sceneY() + maxY * selectedY() / 100.0 * factor),
                Math.max(12, (int) Math.round(scaledWidth * factor)),
                Math.max(12, (int) Math.round(scaledHeight * factor))};
    }

    private void drawPreview(GuiGraphics g, int mouseX, int mouseY) {
        double scale = sceneScale();
        int left = (int) sceneX(), top = (int) sceneY();
        int right = (int) (left + width * scale), bottom = (int) (top + height * scale);
        g.fill(left, top, right, bottom, 0xFF71869A);
        g.fill(left, top + (bottom - top) * 2 / 3, right, bottom, 0xFF384A3D);
        g.fill(left, top + (bottom - top) * 2 / 3, right, top + (bottom - top) * 2 / 3 + 2, 0xFF53634B);
        g.fill(left, bottom - 22, right, bottom, 0xAA171B1F);
        for (int i = 0; i < 9; i++) {
            int slotX = (left + right) / 2 - 81 + i * 18;
            g.fill(slotX, bottom - 21, slotX + 17, bottom - 4, 0xAA484F52);
            g.renderOutline(slotX, bottom - 21, 17, 17, 0xFF8C9696);
        }
        g.renderOutline(left, top, right - left, bottom - top, 0xFFB8C9D4);
        g.drawString(font, text("preview_hint"), left + 8, top + 8, 0xFFFFFFFF, false);

        if (avoidMinimap) {
            int mapWidth = Math.min(Math.max(112, width / 4), Math.max(1, width / 2));
            int mapHeight = Math.min(Math.max(84, height / 4), Math.max(1, height / 2));
            int mx = minimapCorner == 0 || minimapCorner == 2 ? 6 : width - mapWidth - 6;
            int my = minimapCorner == 0 || minimapCorner == 1 ? 6 : height - mapHeight - 6;
            int px = left + (int) (mx * scale), py = top + (int) (my * scale);
            int mw = Math.max(1, (int) (mapWidth * scale)), mh = Math.max(1, (int) (mapHeight * scale));
            g.fill(px, py, px + mw, py + mh, 0x66525D66);
            g.renderOutline(px, py, mw, mh, 0xFFC2CBD0);
            g.drawCenteredString(font, text("minimap"), px + mw / 2, py + mh / 2 - 4, 0xFFE2E8E9);
        }

        String activeNotice = selected;
        selected = selected.equals("task") ? "pickup" : "task";
        int[] otherBounds = selectedRect();
        selected = activeNotice;
        g.fill(otherBounds[0], otherBounds[1], otherBounds[0] + otherBounds[2],
                otherBounds[1] + otherBounds[3], 0x1828C7AE);
        g.renderOutline(otherBounds[0], otherBounds[1], otherBounds[2], otherBounds[3], 0xAA80D8C5);
        g.drawString(font, text(activeNotice.equals("task") ? "pickup_ghost" : "task_ghost"),
                otherBounds[0] + 4, otherBounds[1] + 3, 0xFFD0E5DE, false);

        int[] bounds = selectedRect();
        var animation = previewTransform();
        double noticeCenterX = bounds[0] + bounds[2] / 2.0;
        double noticeCenterY = bounds[1] + bounds[3] / 2.0;
        float previewAlpha = (float) animation.opacity();
        g.pose().pushPose();
        g.pose().translate(noticeCenterX + animation.x() * scale,
                noticeCenterY + animation.y() * scale, 0);
        if (animation.rotation() != 0)
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) animation.rotation()));
        g.pose().scale((float) animation.scale(), (float) animation.scale(), 1);
        g.pose().translate(-noticeCenterX, -noticeCenterY, 0);
        if (selected.equals("task")) {
            if (taskBackground)
                QuestUnlockToast.backing(g, bounds[0], bounds[1], bounds[0] + bounds[2],
                        bounds[1] + bounds[3], previewAlpha * taskBackgroundOpacity / 100F);
            g.fill(bounds[0] + 8, bounds[1] + 8, bounds[0] + 34, bounds[1] + 34,
                    fadeColor(0xFF8C7137, previewAlpha));
            g.drawCenteredString(font, "!", bounds[0] + 21, bounds[1] + 16,
                    fadeColor(0xFFFFE477, previewAlpha));
            g.drawString(font, text("task_label"), bounds[0] + 42, bounds[1] + 9,
                    fadeColor(0xFFE5BF69, previewAlpha), true);
            g.drawString(font, text("task_sample"), bounds[0] + 42, bounds[1] + 24,
                    fadeColor(0xFFFFFFFF, previewAlpha), true);
            g.drawString(font, text("task_hint"), bounds[0] + 42, bounds[1] + 39,
                    fadeColor(0xFFD9DDE2, previewAlpha), false);
        } else {
            g.fill(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3],
                    fadeColor(0x60385878, previewAlpha));
            float size = selectedScale() / 100F;
            int headerHeight = Math.max(10, (int) Math.round(33 * size));
            int rowHeight = Math.max(12, (int) Math.round(58 * size));
            g.drawCenteredString(font, Component.translatable("screen.xero_quest_journal.item_pickup.title"),
                    bounds[0] + bounds[2] / 2, bounds[1] + Math.max(2, (headerHeight - 9) / 2),
                    fadeColor(0xFFF4F3F0, previewAlpha));
            for (int i = 0; i < 4; i++) {
                int ry = bounds[1] + headerHeight + i * rowHeight;
                g.fill(bounds[0] + 6, ry, bounds[0] + bounds[2] - 5,
                        Math.min(bounds[1] + bounds[3], ry + rowHeight - 2), fadeColor(0x68456891, previewAlpha));
                int progressWidth = Math.max(28, Math.round(144 * size));
                g.fill(bounds[0] + 8, ry + Math.max(2, Math.round(12 * size)),
                        bounds[0] + 8 + progressWidth, Math.min(bounds[1] + bounds[3] - 2,
                                ry + Math.round(46 * size)), fadeColor(0x9B5E55BA, previewAlpha));
                g.drawCenteredString(font, Component.literal((74 + i) + "/105"),
                        bounds[0] + 8 + progressWidth / 2, ry + Math.max(3, Math.round(21 * size)),
                        fadeColor(0xFFFFFFFF, previewAlpha));
                var item = new ItemStack(i % 2 == 0 ? Items.DIAMOND : Items.IRON_INGOT);
                int iconX = bounds[0] + Math.min(bounds[2] - 48, 164);
                int iconY = ry + Math.max(2, Math.round(14 * size));
                g.renderItem(item, iconX, iconY);
                g.drawString(font, item.getHoverName(), iconX + Math.max(14, Math.round(34 * size)),
                        ry + Math.max(3, Math.round(23 * size)), fadeColor(0xFFF2F0E9, previewAlpha), true);
                g.drawString(font, "x" + (4 - i), bounds[0] + bounds[2] - Math.max(22, Math.round(52 * size)),
                        ry + Math.max(3, Math.round(23 * size)),
                        fadeColor(0xFFE4EAF0, previewAlpha), true);
            }
        }
        g.pose().popPose();
        g.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3], 0xFFE8C875);
        if (bounds[0] < left || bounds[1] < top || bounds[0] + bounds[2] > right
                || bounds[1] + bounds[3] > bottom) {
            g.drawString(font, text("out_of_preview"), left + 8, bottom - 34, 0xFFFFA49A, false);
        }
        int handleX = bounds[0] + bounds[2] - 5;
        int handleY = bounds[1] + bounds[3] - 5;
        g.fill(handleX - 2, handleY - 2, handleX + 7, handleY + 7, 0xFFE8C875);
        g.renderOutline(handleX - 2, handleY - 2, 9, 9, 0xFF352B1A);
        g.drawString(font, text("position", selectedX(), selectedY()),
                left + 8, bottom + 8, 0xFFE5D19A, false);
        g.drawString(font, text("drag_hint"), left + 8, bottom + 22, 0xFFC2CFD2, false);
    }

    private PopupAnimationProfile.Transform previewTransform() {
        if (!previewPlaying) return new PopupAnimationProfile.Transform(1, 0, 0, 0, 1);
        long elapsed = Math.max(0, System.nanoTime() - previewStartedAt);
        if (selected.equals("task")) {
            double duration = Math.max(100, ClientPreferences.get().popupAnimationMillis) * 1_000_000.0;
            return ClientPreferences.get().taskToastAnimation.sample(Math.clamp(elapsed / duration, 0, 1));
        }
        float progress = Math.clamp(elapsed / 900_000_000F, 0, 1);
        float eased = QuestUnlockToast.easeOutCubic(progress);
        return new PopupAnimationProfile.Transform(.86 + .14 * eased,
                -24 * (1 - eased), 0, 0, eased);
    }

    private static int fadeColor(int color, float opacity) {
        int alpha = Math.clamp(Math.round((color >>> 24) * opacity), 0, 255);
        return (color & 0x00FFFFFF) | alpha << 24;
    }

    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        backdrop(g, 0xFF171C22);
        g.fill(0, 0, width, 42, 0xFF252C34);
        g.drawString(font, title, 16, 16, 0xFFF3D48B, false);
        g.fill(previewRight(), 43, width, height - 39, 0xFF202832);
        g.drawString(font, text("selected_scale", selectedScale()), previewRight() + 12, 153,
                0xFFB8CBCD, false);
        g.drawString(font, text("coordinates_explain"), previewRight() + 12, 185,
                0xFFB8CBCD, false);
        if (selected.equals("task"))
            g.drawString(font, text("background_opacity_value", taskBackgroundOpacity),
                    previewRight() + 12, 222, 0xFFB8CBCD, false);
        drawPreview(g, mouseX, mouseY);
        g.fill(0, height - 39, width, height, 0xFF252C34);
        if (!error.isEmpty()) g.drawString(font, text(error), 12, height - 24, 0xFFFF9B8F, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0) return false;
        int[] bounds = selectedRect();
        if (x >= bounds[0] + bounds[2] - 10 && x <= bounds[0] + bounds[2] + 3
                && y >= bounds[1] + bounds[3] - 10 && y <= bounds[1] + bounds[3] + 3) {
            resizing = true;
            resizeStartX = x;
            resizeStartY = y;
            resizeStartScale = selectedScale();
            setFocused(null);
            return true;
        }
        if (x >= bounds[0] && x <= bounds[0] + bounds[2]
                && y >= bounds[1] && y <= bounds[1] + bounds[3]) {
            dragging = true;
            double factor = sceneScale();
            grabX = (x - bounds[0]) / factor;
            grabY = (y - bounds[1]) / factor;
            setFocused(null);
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (resizing && button == 0) {
            double factor = sceneScale();
            int reference = selected.equals("task") ? 430 : 760;
            int growth = (int) Math.round(((x - resizeStartX) + (y - resizeStartY))
                    / (2 * factor * reference) * 100);
            setSelectedScale(Math.clamp(resizeStartScale + growth, 10, 200));
            return true;
        }
        if (!dragging || button != 0) return super.mouseDragged(x, y, button, dx, dy);
        double factor = sceneScale();
        int rawX = (int) Math.round((x - sceneX()) / factor - grabX);
        int rawY = (int) Math.round((y - sceneY()) / factor - grabY);
        int w = selected.equals("task") ? 430 : 760;
        int h = selected.equals("task") ? 90 : 265;
        double scale = selectedScale() / 100.0;
        int scaledWidth = Math.round(w * (float) scale);
        if (selected.equals("pickup")) scaledWidth = Math.min(scaledWidth, Math.max(1, width - 20));
        int maxX = Math.max(1, width - scaledWidth);
        int maxY = Math.max(1, height - Math.round(h * (float) scale));
        setSelectedX(Math.clamp(Math.round(rawX * 100F / maxX), 0, 100));
        setSelectedY(Math.clamp(Math.round(rawY * 100F / maxY), 0, 100));
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && resizing) { resizing = false; return true; }
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
