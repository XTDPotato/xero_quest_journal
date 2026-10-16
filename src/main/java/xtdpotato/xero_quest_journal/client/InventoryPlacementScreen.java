package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** A scaled screen preview preserves the same anchor coordinates as the live inventory. */
public final class InventoryPlacementScreen extends AnimatedQuestScreen {
    private static final ResourceLocation INVENTORY = java.util.Objects.requireNonNull(
            ResourceLocation.tryParse("minecraft:textures/gui/container/inventory.png"));
    private final Screen parent;
    private InventoryEntryPosition task, mail;
    private boolean showTask, showMail;
    private String selected = "task", draggingEntry = "", error = "";
    private double grabX, grabY;
    private EditBox offsetX, offsetY;
    private boolean syncing;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final SmoothScroll sidebarScroll = new SmoothScroll();
    private final java.util.Set<AbstractWidget> sidebarWidgets = new java.util.HashSet<>();
    private int sidebarPixels;

    public InventoryPlacementScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        var prefs = ClientPreferences.get();
        task = prefs.taskInventoryPosition;
        mail = prefs.mailInventoryPosition;
        showTask = prefs.inventoryButton;
        showMail = prefs.mailboxInventoryButton;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.inventory_placement." + key);
    }
    private int sideWidth() { return width < 600 ? 148 : 220; }
    private int sideX() { return width - sideWidth(); }
    private double previewScale() {
        return Math.max(.1, Math.min((sideX() - 32.0) / width, (height - 100.0) / height));
    }
    private double previewX() { return 12 + (sideX() - 24 - width * previewScale()) / 2; }
    private double previewY() { return 56 + (height - 100 - height * previewScale()) / 2; }
    private InventoryEntryPosition position(String kind) { return kind.equals("task") ? task : mail; }
    private void position(String kind, InventoryEntryPosition value) {
        if (kind.equals("task")) task = value;
        else mail = value;
    }
    private void syncOffsets() {
        syncing = true;
        offsetX.setValue(Integer.toString(position(selected).x()));
        offsetY.setValue(Integer.toString(position(selected).y()));
        syncing = false;
    }
    @Override protected void init() {
        clearWidgets();
        sidebarWidgets.clear();
        int x = sideX() + 12, w = sideWidth() - 24;
        for (int i = 0; i < 2; i++) {
            String kind = i == 0 ? "task" : "mail";
            var tab = new StyledButton(x + i * (w / 2 + 2), 56, w / 2 - 2, 22, text(kind),
                    ignored -> { selected = kind; init(); }, styles, "placement-" + kind);
            tab.setSelectionHighlighted(selected.equals(kind));
            addRenderableWidget(tab);
        }
        addRenderableWidget(Checkbox.builder(text("show_task"), font).pos(x, 92).selected(showTask)
                .onValueChange((box, value) -> showTask = value).build());
        addRenderableWidget(Checkbox.builder(text("show_mail"), font).pos(x, 118).selected(showMail)
                .onValueChange((box, value) -> showMail = value).build());
        addRenderableWidget(new StyledChoiceButton<>(x, 166, w, 23, text("anchor"),
                InventoryEntryPosition.ANCHORS, position(selected).anchor(),
                value -> text("anchor." + value), value -> {
                    position(selected, position(selected).reanchor(value, width, height));
                    init();
                }, styles, "placement-anchor"));
        offsetX = new EditBox(font, x, 218, (w - 6) / 2, 20, text("offset_x"));
        offsetY = new EditBox(font, x + (w + 6) / 2, 218, (w - 6) / 2, 20, text("offset_y"));
        offsetX.setMaxLength(7); offsetY.setMaxLength(7);
        syncOffsets();
        offsetX.setResponder(value -> changeOffset(true, value));
        offsetY.setResponder(value -> changeOffset(false, value));
        addRenderableWidget(offsetX); addRenderableWidget(offsetY);
        addRenderableWidget(new StyledButton(x, 254, w, 23, text("reset"), ignored -> {
            position(selected, new InventoryEntryPosition("top_left", selected.equals("task") ? 6 : 30, 6));
            init();
        }, styles, "placement-reset"));
        children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).forEach(sidebarWidgets::add);
        sidebarScroll.limit(280 - (height - 44));
        sidebarPixels = sidebarScroll.pixels();
        sidebarWidgets.forEach(widget -> widget.setY(widget.getY() - sidebarPixels));
        if (sidebarScroll.maximum() > 0)
            addRenderableWidget(new ScrollRail(width - 9, 54, height - 98, sidebarScroll));
        addRenderableWidget(new StyledButton(width - 148, height - 31, 64, 23,
                Component.translatable("screen.xero_quest_journal.save"), ignored -> save(),
                styles, "placement-save"));
        addRenderableWidget(new StyledButton(width - 76, height - 31, 64, 23,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "placement-cancel"));
    }
    private void changeOffset(boolean horizontal, String value) {
        if (syncing) return;
        try {
            int offset = Integer.parseInt(value);
            var current = position(selected);
            position(selected, new InventoryEntryPosition(current.anchor(),
                    horizontal ? offset : current.x(), horizontal ? current.y() : offset));
            error = "";
        } catch (NumberFormatException invalid) { error = "integer_required"; }
    }
    private void save() {
        if (!error.isEmpty()) return;
        var prefs = ClientPreferences.get();
        var oldTask = prefs.taskInventoryPosition;
        var oldMail = prefs.mailInventoryPosition;
        boolean oldShowTask = prefs.inventoryButton, oldShowMail = prefs.mailboxInventoryButton;
        prefs.taskInventoryPosition = task; prefs.mailInventoryPosition = mail;
        prefs.inventoryButton = showTask; prefs.mailboxInventoryButton = showMail;
        if (prefs.save()) minecraft.setScreen(parent);
        else {
            prefs.taskInventoryPosition = oldTask; prefs.mailInventoryPosition = oldMail;
            prefs.inventoryButton = oldShowTask; prefs.mailboxInventoryButton = oldShowMail;
            error = "save_failed";
        }
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        sidebarScroll.advance();
        int shift = sidebarScroll.pixels() - sidebarPixels;
        if (shift != 0) sidebarWidgets.forEach(widget -> widget.setY(widget.getY() - shift));
        sidebarPixels = sidebarScroll.pixels();
        backdrop(g, 0xFF171C22);
        g.fill(0, 0, width, 42, 0xFF252C34);
        g.drawString(font, title, 16, 16, 0xFFF3D48B, false);
        g.fill(sideX(), 43, width, height - 39, 0xFF202832);
        int x = sideX() + 12;
        g.enableScissor(sideX(), 50, width - 8, height - 44);
        g.drawString(font, text("anchor"), x, 150 - sidebarPixels, 0xFFB8CBCD, false);
        g.drawString(font, text("offset_x"), x, 202 - sidebarPixels, 0xFFB8CBCD, false);
        g.drawString(font, text("offset_y"), x + (sideWidth() - 18) / 2, 202 - sidebarPixels, 0xFFB8CBCD, false);
        g.disableScissor();
        double scale = previewScale();
        int left = (int) previewX(), top = (int) previewY();
        int right = (int) (previewX() + width * scale), bottom = (int) (previewY() + height * scale);
        g.fill(left, top, right, bottom, 0xFF303A43);
        g.renderOutline(left - 1, top - 1, right - left + 2, bottom - top + 2, 0xFF6B898A);
        g.enableScissor(left, top, right, bottom);
        g.pose().pushPose();
        g.pose().translate(previewX(), previewY(), 0);
        g.pose().scale((float) scale, (float) scale, 1);
        int inventoryX = (width - 176) / 2, inventoryY = (height - 166) / 2;
        g.blit(INVENTORY, inventoryX, inventoryY, 0, 0, 176, 166);
        for (int slot = 0; slot < 36; slot++) {
            var stack = minecraft.player == null ? slot == 0 ? new ItemStack(Items.BOOK) : ItemStack.EMPTY
                    : minecraft.player.getInventory().getItem(slot);
            int sx = inventoryX + 8 + slot % 9 * 18;
            int sy = inventoryY + (slot < 9 ? 142 : 84 + (slot / 9 - 1) * 18);
            if (!stack.isEmpty()) g.renderItem(stack, sx, sy);
        }
        for (String kind : java.util.List.of("task", "mail")) {
            if (kind.equals("task") ? !showTask : !showMail) continue;
            int[] point = position(kind).resolve(width, height);
            boolean current = selected.equals(kind);
            if (current) {
                for (int line = 0; line < width; line += 6) g.fill(line, point[1], line + 3, point[1] + 1, 0xFF9BD3C1);
                for (int line = 0; line < height; line += 6) g.fill(point[0], line, point[0] + 1, line + 3, 0xFF9BD3C1);
            }
            g.fill(point[0], point[1], point[0] + 20, point[1] + 20, 0xFF46586D);
            g.renderOutline(point[0], point[1], 20, 20, current ? 0xFFF3D48B : 0xFF8A9FB2);
            g.renderItem(new ItemStack(kind.equals("task") ? Items.BOOK : Items.PAPER), point[0] + 2, point[1] + 2);
        }
        g.pose().popPose();
        g.disableScissor();
        g.drawString(font, "0, 0", left, Math.max(44, top - 10), 0xFF94AEAE, false);
        g.fill(0, height - 39, width, height, 0xFF252C34);
        if (!error.isEmpty()) g.drawString(font, text(error), 12, height - 24, 0xFFFF9B8F, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0) return false;
        for (String kind : java.util.List.of(selected, selected.equals("task") ? "mail" : "task")) {
            if (kind.equals("task") ? !showTask : !showMail) continue;
            int[] point = position(kind).resolve(width, height);
            double left = previewX() + point[0] * previewScale(), top = previewY() + point[1] * previewScale();
            if (x < left || x >= left + 20 * previewScale() || y < top || y >= top + 20 * previewScale()) continue;
            selected = draggingEntry = kind;
            grabX = (x - left) / previewScale(); grabY = (y - top) / previewScale();
            setFocused(null);
            return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && !draggingEntry.isEmpty()) {
            int left = (int) Math.round((x - previewX()) / previewScale() - grabX);
            int top = (int) Math.round((y - previewY()) / previewScale() - grabY);
            position(draggingEntry, position(draggingEntry).move(left, top, width, height, !hasShiftDown()));
            syncOffsets();
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && !draggingEntry.isEmpty()) {
            draggingEntry = "";
            init();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if (!(getFocused() instanceof EditBox) && !hasChoicePopup()) {
            int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            if (dx != 0 || dy != 0) {
                var point = position(selected).resolve(width, height);
                int step = hasShiftDown() ? 10 : 1;
                position(selected, position(selected).move(point[0] + dx * step, point[1] + dy * step, width, height, false));
                syncOffsets();
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return sidebarWidgets.contains(widget) ? viewport(sideX() + 8, 50, width - 10, height - 44) : null;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (x >= sideX() && y >= 50 && y < height - 44) {
            sidebarScroll.wheel(vertical * 28);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
