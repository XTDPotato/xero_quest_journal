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
    private boolean avoidMinimap, dragging, resizing, resizingMinimap, draggingMinimap, taskBackground, previewPlaying;
    private int taskX, taskY, taskScale, taskTextScale, taskBackgroundOpacity;
    private int pickupX, pickupY, pickupScale, pickupTextScale, minimapCorner, minimapWidth, minimapHeight;
    private int minimapX, minimapY;
    private double mapGrabX, mapGrabY;
    private double grabX, grabY;
    private double resizeStartX, resizeStartY;
    private int resizeStartScale, resizeStartMinimapWidth, resizeStartMinimapHeight;
    private long previewStartedAt;
    private EditBox scaleInput, textScaleInput, backgroundOpacityInput, minimapWidthInput, minimapHeightInput;
    private boolean syncing;
    private final SmoothScroll propertyScroll = new SmoothScroll();
    private final java.util.Map<net.minecraft.client.gui.components.AbstractWidget, Integer> propertyWidgets = new java.util.LinkedHashMap<>();
    private Viewport propertyViewport() { return viewport(previewRight() + 6, 34, width - 6, height - 38); }
    @Override protected Viewport widgetViewport(net.minecraft.client.gui.components.AbstractWidget widget) {
        return propertyWidgets.containsKey(widget) ? propertyViewport() : null;
    }
    private void positionProperties() {
        propertyScroll.limit(Math.max(0, (selected.equals("task") ? 405 : 300) - (height - 38)));
        propertyWidgets.forEach((widget, y) -> { widget.setY(y - propertyScroll.pixels()); widget.visible = propertyViewport().intersects(widget); });
    }

    public HudNotificationLayoutScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.notification_layout.title"));
        this.parent = parent;
        var prefs = ClientPreferences.get();
        taskX = prefs.taskToastXPercent;
        taskY = prefs.taskToastYPercent;
        taskScale = prefs.taskToastScalePercent;
        taskTextScale = prefs.taskToastTextScalePercent;
        taskBackground = prefs.taskToastBackgroundEnabled;
        taskBackgroundOpacity = prefs.taskToastBackgroundOpacityPercent;
        pickupX = prefs.itemPickupToastXPercent;
        pickupY = prefs.itemPickupToastYPercent;
        pickupScale = prefs.itemPickupToastScalePercent;
        pickupTextScale = prefs.itemPickupToastTextScalePercent;
        minimapWidth = prefs.minimapAvoidWidthPercent;
        minimapHeight = prefs.minimapAvoidHeightPercent;
        minimapX = prefs.minimapAvoidXPercent; minimapY = prefs.minimapAvoidYPercent;
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
        return Math.max(.1, Math.min((previewRight() - 36.0) / width, (height - 78.0) / height));
    }
    private double sceneX() { return 16 + (previewRight() - 28 - width * sceneScale()) / 2; }
    private double sceneY() { return 40 + (height - 64 - height * sceneScale()) / 2; }
    private int selectedX() { return selected.equals("task") ? taskX : pickupX; }
    private int selectedY() { return selected.equals("task") ? taskY : pickupY; }
    private int selectedScale() { return selected.equals("task") ? taskScale : pickupScale; }
    private int minimumSelectedScale() { return selected.equals("task") ? 10 : 50; }
    private int selectedTextScale() { return selected.equals("task") ? taskTextScale : pickupTextScale; }
    private void setSelectedX(int v) { if (selected.equals("task")) taskX = v; else pickupX = v; }
    private void setSelectedY(int v) { if (selected.equals("task")) taskY = v; else pickupY = v; }
    private void setSelectedScale(int v) {
        v = Math.clamp(v, minimumSelectedScale(), 200);
        if (selected.equals("task")) taskScale = v; else pickupScale = v;
        if (scaleInput != null && !scaleInput.isFocused()) {
            syncing = true;
            scaleInput.setValue(Integer.toString(v));
            syncing = false;
        }
    }
    private void setSelectedTextScale(int value) {
        if (selected.equals("task")) taskTextScale = value; else pickupTextScale = value;
        if (textScaleInput != null && !textScaleInput.isFocused()) {
            syncing = true;
            textScaleInput.setValue(Integer.toString(value));
            syncing = false;
        }
    }

    private EditBox percentControl(int x, int y, int width, int value, int min, int max,
                                   java.util.function.IntConsumer setter, String id) {
        addRenderableWidget(new StyledButton(x, y, 25, 22, Component.literal("-"), ignored -> {
            int current = id.equals("minimap-width") ? minimapWidth
                    : id.equals("minimap-height") ? minimapHeight : selectedTextScale();
            setter.accept(Math.max(min, current - 5));
        }, styles, "notification-" + id + "-less"));
        var input = new EditBox(font, x + 29, y + 1, width - 58, 20, text(id.replace('-', '_')));
        input.setValue(Integer.toString(value));
        input.setMaxLength(3);
        input.setFilter(raw -> raw.isEmpty() || raw.matches("\\d{1,3}"));
        input.setResponder(raw -> {
            if (syncing || raw.isBlank()) return;
            try { setter.accept(Math.clamp(Integer.parseInt(raw), min, max)); }
            catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(input);
        addRenderableWidget(new StyledButton(x + width - 25, y, 25, 22, Component.literal("+"), ignored -> {
            int current = id.equals("minimap-width") ? minimapWidth
                    : id.equals("minimap-height") ? minimapHeight : selectedTextScale();
            setter.accept(Math.min(max, current + 5));
        }, styles, "notification-" + id + "-more"));
        return input;
    }

    private int[] minimapAvoidRect() {
        var area = HudOverlayLayout.minimapArea(width, height, HudOverlayLayout.Corner.values()[minimapCorner], minimapWidth, minimapHeight, minimapX, minimapY);
        double factor = sceneScale();
        return new int[]{(int) Math.round(sceneX() + area.x() * factor), (int) Math.round(sceneY() + area.y() * factor),
                Math.max(1, (int) Math.round(area.width() * factor)), Math.max(1, (int) Math.round(area.height() * factor))};
    }

    @Override protected void init() {
        propertyWidgets.clear(); scaleInput = textScaleInput = backgroundOpacityInput = minimapWidthInput = minimapHeightInput = null;
        clearWidgets();
        int sx = previewRight() + 12;
        int sw = sideWidth() - 24;
        for (int i = 0; i < 2; i++) {
            String value = i == 0 ? "task" : "pickup";
            var tab = new StyledButton(sx + i * (sw / 2 + 2), 36, sw / 2 - 2, 24, text(value),
                    ignored -> { selected = value; init(); }, styles, "notification-layout-" + value);
            tab.setSelectionHighlighted(selected.equals(value));
            addRenderableWidget(tab);
        }
        addRenderableWidget(Checkbox.builder(text("avoid_minimap"), font).pos(sx, 66)
                .selected(avoidMinimap).onValueChange((box, value) -> avoidMinimap = value).build());
        addRenderableWidget(new StyledChoiceButton<>(sx, 98, sw, 24, text("minimap_corner"),
                java.util.List.of(0, 1, 2, 3), minimapCorner, value -> text("corner." + value),
                value -> { minimapCorner = value; minimapX = minimapY = -1; }, styles, "notification-minimap-corner"));
        minimapWidthInput = percentControl(sx, 132, sw, minimapWidth, 5, 50,
                value -> setMinimapSize(value, minimapHeight), "minimap-width");
        minimapHeightInput = percentControl(sx, 164, sw, minimapHeight, 5, 50,
                value -> setMinimapSize(minimapWidth, value), "minimap-height");
        addRenderableWidget(new StyledButton(sx, 197, 32, 24, Component.literal("-"), ignored ->
                setSelectedScale(Math.max(minimumSelectedScale(), selectedScale() - 5)), styles, "notification-scale-less"));
        scaleInput = new EditBox(font, sx + 38, 199, sw - 76, 20, text("scale"));
        scaleInput.setValue(Integer.toString(selectedScale()));
        scaleInput.setMaxLength(3);
        scaleInput.setFilter(raw -> raw.isEmpty() || raw.matches("\\d{1,3}"));
        scaleInput.setResponder(raw -> {
            if (syncing || raw.isBlank()) return;
            try { setSelectedScale(Math.clamp(Integer.parseInt(raw), minimumSelectedScale(), 200)); }
            catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(scaleInput);
        addRenderableWidget(new StyledButton(sx + sw - 32, 197, 32, 24, Component.literal("+"), ignored ->
                setSelectedScale(Math.min(200, selectedScale() + 5)), styles, "notification-scale-more"));
        textScaleInput = percentControl(sx, 238, sw, selectedTextScale(), 50, 200,
                this::setSelectedTextScale, "text-scale");
        if (selected.equals("task")) {
            addRenderableWidget(Checkbox.builder(text("task_background"), font).pos(sx, 274)
                    .selected(taskBackground).onValueChange((box, value) -> taskBackground = value).build());
            addRenderableWidget(new StyledButton(sx, 314, 28, 23, Component.literal("-"), ignored ->
                    setBackgroundOpacity(Math.max(5, taskBackgroundOpacity - 5)),
                    styles, "notification-background-less"));
            backgroundOpacityInput = new EditBox(font, sx + 32, 316, sw - 64, 19, text("background_opacity"));
            backgroundOpacityInput.setValue(Integer.toString(taskBackgroundOpacity));
            backgroundOpacityInput.setMaxLength(3);
            backgroundOpacityInput.setFilter(raw -> raw.isEmpty() || raw.matches("\\d{1,3}"));
            backgroundOpacityInput.setResponder(raw -> {
                if (syncing || raw.isBlank()) return;
                try { setBackgroundOpacity(Math.clamp(Integer.parseInt(raw), 5, 100)); }
                catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(backgroundOpacityInput);
            addRenderableWidget(new StyledButton(sx + sw - 28, 314, 28, 23, Component.literal("+"), ignored ->
                    setBackgroundOpacity(Math.min(100, taskBackgroundOpacity + 5)),
                    styles, "notification-background-more"));
            addRenderableWidget(new StyledButton(sx, 349, sw, 23, text("edit_animation"), ignored ->
                    minecraft.setScreen(new PopupAnimationEditorScreen(this, true)),
                    styles, "notification-task-animation"));
        }
        addRenderableWidget(new StyledButton(sx, selected.equals("task") ? 380 : 274, sw, 23,
                text(previewPlaying ? "restart_preview" : "play_preview"), ignored -> playPreview(),
                styles, "notification-play-preview"));
        addRenderableWidget(new StyledButton(width - 148, height - 31, 64, 23,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "notification-layout-save"));
        addRenderableWidget(new StyledButton(width - 76, height - 31, 64, 23,
                Component.translatable("gui.cancel"), ignored -> onClose(),
                styles, "notification-layout-cancel"));
        children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                .filter(widget -> !(widget instanceof StyledButton b && (b.styleId().equals("notification-layout-save") || b.styleId().equals("notification-layout-cancel"))))
                .forEach(widget -> propertyWidgets.put(widget, widget.getY()));
        positionProperties();
    }

    private void setMinimapSize(int newWidth, int newHeight) {
        minimapWidth = Math.clamp(newWidth, 5, 50);
        minimapHeight = Math.clamp(newHeight, 5, 50);
        if (minimapWidthInput != null && minimapHeightInput != null) {
            syncing = true;
            if (!minimapWidthInput.isFocused()) minimapWidthInput.setValue(Integer.toString(minimapWidth));
            if (!minimapHeightInput.isFocused()) minimapHeightInput.setValue(Integer.toString(minimapHeight));
            syncing = false;
        }
    }

    private void setBackgroundOpacity(int value) {
        taskBackgroundOpacity = value;
        if (backgroundOpacityInput != null && !backgroundOpacityInput.isFocused()) {
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
        int oldTaskTextScale = prefs.taskToastTextScalePercent;
        boolean oldTaskBackground = prefs.taskToastBackgroundEnabled;
        int oldTaskBackgroundOpacity = prefs.taskToastBackgroundOpacityPercent;
        int oldPickupX = prefs.itemPickupToastXPercent, oldPickupY = prefs.itemPickupToastYPercent;
        int oldPickupScale = prefs.itemPickupToastScalePercent;
        int oldPickupTextScale = prefs.itemPickupToastTextScalePercent;
        int oldMinimapWidth = prefs.minimapAvoidWidthPercent, oldMinimapHeight = prefs.minimapAvoidHeightPercent;
        int oldMinimapX = prefs.minimapAvoidXPercent, oldMinimapY = prefs.minimapAvoidYPercent;
        boolean oldAvoid = prefs.hudAutoAvoidMinimap;
        String oldCorner = prefs.minimapCorner;
        prefs.taskToastXPercent = taskX;
        prefs.taskToastYPercent = taskY;
        prefs.taskToastScalePercent = taskScale;
        prefs.taskToastTextScalePercent = taskTextScale;
        prefs.taskToastBackgroundEnabled = taskBackground;
        prefs.taskToastBackgroundOpacityPercent = taskBackgroundOpacity;
        prefs.itemPickupToastXPercent = pickupX;
        prefs.itemPickupToastYPercent = pickupY;
        prefs.itemPickupToastScalePercent = pickupScale;
        prefs.itemPickupToastTextScalePercent = pickupTextScale;
        prefs.minimapAvoidWidthPercent = minimapWidth;
        prefs.minimapAvoidHeightPercent = minimapHeight;
        prefs.minimapAvoidXPercent = minimapX; prefs.minimapAvoidYPercent = minimapY;
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
            prefs.taskToastTextScalePercent = oldTaskTextScale;
            prefs.taskToastBackgroundEnabled = oldTaskBackground;
            prefs.taskToastBackgroundOpacityPercent = oldTaskBackgroundOpacity;
            prefs.itemPickupToastXPercent = oldPickupX;
            prefs.itemPickupToastYPercent = oldPickupY;
            prefs.itemPickupToastScalePercent = oldPickupScale;
            prefs.itemPickupToastTextScalePercent = oldPickupTextScale;
            prefs.minimapAvoidWidthPercent = oldMinimapWidth;
            prefs.minimapAvoidHeightPercent = oldMinimapHeight;
            prefs.minimapAvoidXPercent = oldMinimapX; prefs.minimapAvoidYPercent = oldMinimapY;
            prefs.hudAutoAvoidMinimap = oldAvoid;
            prefs.minimapCorner = oldCorner;
            error = "save_failed";
        }
    }

    private int[] selectedRect() {
        int[] size = selectedSize();
        double factor = sceneScale();
        int scaledWidth = size[0], scaledHeight = size[1];
        int maxX = Math.max(0, width - scaledWidth - 16), maxY = Math.max(0, height - scaledHeight - 16);
        return new int[]{(int) Math.round(sceneX() + (8 + maxX * selectedX() / 100.0) * factor),
                (int) Math.round(sceneY() + (8 + maxY * selectedY() / 100.0) * factor),
                Math.max(1, (int) Math.round(scaledWidth * factor)), Math.max(1, (int) Math.round(scaledHeight * factor))};
    }
    private int[] selectedSize() {
        if (selected.equals("task")) {
            var g = NoticeGeometry.task(width, taskScale, taskTextScale, ClientPreferences.get().taskToastIconScalePercent);
            return new int[]{Math.round(g.width() * g.scale()), Math.round(g.height() * g.scale())};
        }
        var g = NoticeGeometry.pickup(width, pickupScale, pickupTextScale, font);
        return new int[]{Math.round(g.width() * g.scale()), Math.round(g.height() * g.scale())};
    }
    private void previewText(GuiGraphics g, String value, int x, int y, float scale, int maxWidth, int color) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, font.plainSubstrByWidth(value, Math.max(1, (int) (maxWidth / scale))), 0, 0, color, true);
        g.pose().popPose();
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
            int[] mapBounds = minimapAvoidRect();
            int px = mapBounds[0], py = mapBounds[1], mw = mapBounds[2], mh = mapBounds[3];
            g.fill(px, py, px + mw, py + mh, 0x66525D66);
            g.renderOutline(px, py, mw, mh, 0xFFC2CBD0);
            g.drawCenteredString(font, text("minimap"), px + mw / 2, py + mh / 2 - 4, 0xFFE2E8E9);
            int handleX = minimapCorner == 0 || minimapCorner == 2 ? px + mw - 5 : px - 4;
            int handleY = minimapCorner == 0 || minimapCorner == 1 ? py + mh - 5 : py - 4;
            g.fill(handleX, handleY, handleX + 9, handleY + 9, 0xFFE8C875);
            g.renderOutline(handleX, handleY, 9, 9, 0xFF352B1A);
        }

        String activeNotice = selected;
        selected = selected.equals("task") ? "pickup" : "task";
        int[] otherBounds = selectedRect();
        selected = activeNotice;
        g.renderOutline(otherBounds[0], otherBounds[1], otherBounds[2], otherBounds[3], 0xAA80D8C5);
        int[] activeBounds = selectedRect();
        int labelY = otherBounds[1] + otherBounds[3] - font.lineHeight - 4;
        if (labelY + font.lineHeight < activeBounds[1] || labelY > activeBounds[1] + activeBounds[3])
            g.drawCenteredString(font, text(activeNotice.equals("task") ? "pickup_ghost" : "task_ghost"),
                    otherBounds[0] + otherBounds[2] / 2, labelY, 0xFFD0E5DE);

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
        var taskGeometry = NoticeGeometry.task(width, taskScale, taskTextScale, ClientPreferences.get().taskToastIconScalePercent);
        var pickupGeometry = NoticeGeometry.pickup(width, pickupScale, pickupTextScale, font);
        float contentScale = (float) sceneScale() * (selected.equals("task") ? taskGeometry.scale() : pickupGeometry.scale());
        g.pose().pushPose();
        g.pose().translate(bounds[0], bounds[1], 0);
        g.pose().scale(contentScale, contentScale, 1);
        if (selected.equals("task")) {
            if (taskBackground)
                QuestUnlockToast.backing(g, 0, 0, taskGeometry.width(), taskGeometry.height(),
                        previewAlpha * taskBackgroundOpacity / 100F);
            float previewIconScale = ClientPreferences.get().taskToastIconScalePercent / (100F * taskGeometry.scale());
            g.pose().pushPose(); g.pose().translate(37, 35, 0); g.pose().scale(previewIconScale, previewIconScale, 1); g.pose().translate(-37, -35, 0);
            g.fill(16, 14, 58, 56,
                    fadeColor(0xFF8C7137, previewAlpha));
            g.drawCenteredString(font, "!", 37, 28,
                    fadeColor(0xFFFFE477, previewAlpha));
            g.pose().popPose();
            int tx = taskGeometry.textX(), available = taskGeometry.width() - tx - 16;
            previewText(g, text("task_label").getString(), tx, 4, taskGeometry.textScale(), available, fadeColor(0xFFE5BF69, previewAlpha));
            previewText(g, text("task_sample").getString(), tx, taskGeometry.titleY(), taskGeometry.textScale(), available, fadeColor(0xFFFFFFFF, previewAlpha));
            previewText(g, text("task_hint").getString(), tx, taskGeometry.hintY(), taskGeometry.textScale(), available, fadeColor(0xFFD9DDE2, previewAlpha));
        } else {
            int pw = pickupGeometry.width(), headerHeight = pickupGeometry.headerHeight(), rowHeight = pickupGeometry.rowHeight();
            float ts = pickupGeometry.textScale();
            String header = Component.translatable("screen.xero_quest_journal.item_pickup.title").getString();
            previewText(g, header, Math.max(4, (pw - Math.round(font.width(header) * ts)) / 2),
                    Math.max(2, Math.round((headerHeight - font.lineHeight * ts) / 2)), ts, pw - 8, fadeColor(0xFFF4F3F0, previewAlpha));
            for (int i = 0; i < 4; i++) {
                float rowEntry = !previewPlaying ? 1 : QuestUnlockToast.easeOutCubic(Math.clamp(
                        ((System.nanoTime() - previewStartedAt) / 1_000_000F - i * 100) / 240, 0, 1));
                g.pose().pushPose();g.pose().translate(-16 * (1-rowEntry), 0, 0);
                float rowAlpha = previewAlpha * rowEntry;
                int ry = headerHeight + i * rowHeight;
                g.fill(6, ry, pw - 5, ry + rowHeight - 2, fadeColor(0x68456891, rowAlpha));
                String progress = (74 + i) + "/105";
                int progressWidth = Math.min(pw * 2 / 5, Math.max(Math.min(76, pw / 3), Math.round(font.width(progress) * ts) + 10));
                g.fill(8, ry + 5, 8 + progressWidth, ry + rowHeight - 5, fadeColor(0x9B5E55BA, rowAlpha));
                float ps = Math.min(ts, (progressWidth - 8F) / Math.max(1, font.width(progress)));
                previewText(g, progress, 8 + Math.round((progressWidth - font.width(progress) * ps) / 2), ry + Math.round((rowHeight - font.lineHeight * ps) / 2), ps, progressWidth - 8, fadeColor(0xFFFFFFFF, rowAlpha));
                var item = new ItemStack(i % 2 == 0 ? Items.DIAMOND : Items.IRON_INGOT);
                int iconX = 14 + progressWidth;
                int iconY = ry + (rowHeight - 16) / 2;
                if (rowAlpha > .05F) g.renderItem(item, iconX, iconY);
                int countWidth = font.width("x" + (4 - i));
                int nameX = iconX + 22;
                float textScale = Math.min(ts, Math.max(.25F, (pw - nameX - 16F) / Math.max(1, countWidth + font.width("...") + 10)));
                int countX = pw - 16 - Math.round(countWidth * textScale), textY = ry + Math.round((rowHeight - font.lineHeight * textScale) / 2);
                previewText(g, Component.translatable("screen.xero_quest_journal.item_pickup.row.acquire").append(" ").append(item.getHoverName()).getString(), nameX, textY, textScale, countX - nameX - 12, fadeColor(0xFFF2F0E9, rowAlpha));
                previewText(g, "x" + (4 - i), countX, textY, textScale, pw - countX, fadeColor(0xFFE4EAF0, rowAlpha));
                g.pose().popPose();
            }
        }
        g.pose().popPose();
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
                left + 8, bottom + 5, 0xFFE5D19A, false);
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
        propertyScroll.advance(); positionProperties();
        backdrop(g, 0xFF171C22);
        g.fill(0, 0, width, 30, 0xFF252C34);
        g.drawString(font, title, 16, 10, 0xFFF3D48B, false);
        g.fill(previewRight(), 31, width, height, 0xFF202832);
        g.enableScissor(previewRight()+6, 34, width-6, height-38);
        g.pose().pushPose(); g.pose().translate(0, -propertyScroll.pixels(), 0);
        g.drawString(font, text("minimap_width_label"), previewRight() + 12, 122, 0xFFB8CBCD, false);
        g.drawString(font, text("minimap_height_label"), previewRight() + 12, 154, 0xFFB8CBCD, false);
        g.drawString(font, text("selected_scale", selectedScale()), previewRight() + 12, 187,
                0xFFB8CBCD, false);
        g.drawString(font, text("text_scale_value", selectedTextScale()), previewRight() + 12, 226,
                0xFFB8CBCD, false);
        if (selected.equals("task"))
            g.drawString(font, text("background_opacity_value", taskBackgroundOpacity),
                    previewRight() + 12, 302, 0xFFB8CBCD, false);
        g.pose().popPose(); g.disableScissor();
        if (propertyScroll.maximum() > 0) {
            int bodyHeight = Math.max(1, height-72);
            int thumb = Math.max(12, (int)(bodyHeight*bodyHeight/(bodyHeight+propertyScroll.maximum())));
            int ty=34+(int)((bodyHeight-thumb)*propertyScroll.position()/propertyScroll.maximum());
            g.fill(width-5,34,width-3,height-38,0xFF444B57);g.fill(width-5,ty,width-3,ty+thumb,0xFFE5D2AC);
        }
        drawPreview(g, mouseX, mouseY);
        if (!error.isEmpty()) g.drawString(font, text(error), 12, height - 24, 0xFFFF9B8F, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0) return false;
        if (avoidMinimap) {
            int[] map = minimapAvoidRect();
            int handleX = minimapCorner == 0 || minimapCorner == 2 ? map[0] + map[2] - 6 : map[0] - 4;
            int handleY = minimapCorner == 0 || minimapCorner == 1 ? map[1] + map[3] - 6 : map[1] - 4;
            if (x >= handleX - 2 && x <= handleX + 11 && y >= handleY - 2 && y <= handleY + 11) {
                resizingMinimap = true;
                resizeStartX = x;
                resizeStartY = y;
                resizeStartMinimapWidth = minimapWidth;
                resizeStartMinimapHeight = minimapHeight;
                setFocused(null);
                return true;
            }
            if (x >= map[0] && x < map[0]+map[2] && y >= map[1] && y < map[1]+map[3]) {
                draggingMinimap = true; mapGrabX = x-map[0]; mapGrabY = y-map[1]; setFocused(null); return true;
            }
        }
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
        if (draggingMinimap && button == 0) {
            int[] bounds=minimapAvoidRect(); double factor=sceneScale();
            minimapX = Math.clamp((int)Math.round(((x-mapGrabX-sceneX())/factor-6)*100 / Math.max(1,width-bounds[2]/factor-12)),0,100);
            minimapY = Math.clamp((int)Math.round(((y-mapGrabY-sceneY())/factor-6)*100 / Math.max(1,height-bounds[3]/factor-12)),0,100);
            return true;
        }
        if (resizingMinimap && button == 0) {
            double factor = sceneScale();
            int xDirection = minimapCorner == 0 || minimapCorner == 2 ? 1 : -1;
            int yDirection = minimapCorner == 0 || minimapCorner == 1 ? 1 : -1;
            int widthDelta = (int) Math.round((x - resizeStartX) * xDirection / (factor * width) * 100);
            int heightDelta = (int) Math.round((y - resizeStartY) * yDirection / (factor * height) * 100);
            setMinimapSize(resizeStartMinimapWidth + widthDelta,
                    resizeStartMinimapHeight + heightDelta);
            return true;
        }
        if (resizing && button == 0) {
            double factor = sceneScale();
            int reference = selected.equals("task") ? 430 : 760;
            int growth = (int) Math.round(((x - resizeStartX) + (y - resizeStartY))
                    / (2 * factor * reference) * 100);
            setSelectedScale(Math.clamp(resizeStartScale + growth, minimumSelectedScale(), 200));
            return true;
        }
        if (!dragging || button != 0) return super.mouseDragged(x, y, button, dx, dy);
        double factor = sceneScale();
        int rawX = (int) Math.round((x - sceneX()) / factor - grabX);
        int rawY = (int) Math.round((y - sceneY()) / factor - grabY);
        int[] size=selectedSize();
        int maxX = Math.max(1, width-size[0]-16), maxY = Math.max(1,height-size[1]-16);
        setSelectedX(Math.clamp(Math.round((rawX-8) * 100F / maxX), 0, 100));
        setSelectedY(Math.clamp(Math.round((rawY-8) * 100F / maxY), 0, 100));
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingMinimap) { draggingMinimap = false; return true; }
        if (button == 0 && resizingMinimap) { resizingMinimap = false; return true; }
        if (button == 0 && resizing) { resizing = false; return true; }
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x,y,horizontal,vertical)) return true;
        if (x >= previewRight() && y>=34 && y<height-38) { propertyScroll.wheel(vertical*28); return true; }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean isPauseScreen() { return false; }
}
