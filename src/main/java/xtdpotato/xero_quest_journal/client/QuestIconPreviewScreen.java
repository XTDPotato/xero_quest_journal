package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Full-screen, animated inspection surface for an item or texture before applying it. */
final class QuestIconPreviewScreen extends AnimatedQuestScreen {
    private static final long DOUBLE_CLICK_NANOS = 320_000_000L;
    private final Screen parent;
    private final String value;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private double zoom = 1, targetZoom = 1;
    private double panX, panY, startPanX, startPanY, pressX, pressY;
    private boolean dragging;
    private long lastClickAt;
    private long lastFrame;

    QuestIconPreviewScreen(Screen parent, String value) {
        super(Component.literal(QuestIcons.displayName(value)));
        this.parent = parent;
        this.value = value;
    }

    private int left() { return 24; }
    private int right() { return width - 24; }
    private int top() { return 52; }
    private int bottom() { return height - 58; }
    private double fitSize() { return Math.max(16, Math.min(right() - left() - 32, bottom() - top() - 32)); }
    private double imageSize() { return fitSize() * zoom; }

    @Override protected void init() {
        clearWidgets();
        int y = height - 38;
        addRenderableWidget(new StyledButton(16, y, 94, 24, text("icon_picker.preview_fit"),
                ignored -> setZoom(1), styles, "icon-preview-fit", "toolbar-button"));
        addRenderableWidget(new StyledButton(118, y, 94, 24, text("icon_picker.preview_reset"),
                ignored -> resetView(), styles, "icon-preview-reset", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 108, y, 92, 24, text("icon_picker.preview_back"),
                ignored -> closeTo(parent), styles, "icon-preview-back", "toolbar-button"));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal." + key, args);
    }

    private void setZoom(double next) {
        targetZoom = Math.clamp(next, .25, 8);
    }

    private void resetView() {
        setZoom(1);
        panX = panY = 0;
    }

    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 1. / 60 : Math.min(.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double blend = 1 - Math.exp(-14 * dt);
        zoom += (targetZoom - zoom) * blend;
        if (Math.abs(targetZoom - zoom) < .001) zoom = targetZoom;
        backdrop(g, 0xFF15191F);
        g.fill(0, 0, width, 40, 0xFF252C34);
        g.drawString(font, font.plainSubstrByWidth(QuestIcons.displayName(value), width - 250),
                18, 15, 0xFFF1D49A, false);
        g.drawString(font, text("icon_picker.preview.zoom", Math.round(zoom * 100)),
                width - 142, 15, 0xFFB6C0CD, false);
        g.fill(left(), top(), right(), bottom(), 0xFF0D1116);
        g.renderOutline(left(), top(), right() - left(), bottom() - top(), 0xFF657385);
        g.enableScissor(left() + 1, top() + 1, right() - 1, bottom() - 1);
        double size = imageSize();
        int drawSize = Math.max(1, (int) Math.round(size));
        int x = (int) Math.round((width - size) / 2 + panX);
        int y = (int) Math.round((top() + bottom() - size) / 2 + panY);
        if (QuestIcons.stack(value).isEmpty() && ResourceLocation.tryParse(value) == null
                && !ClientMediaManager.isNetworkSource(value))
            g.fill(x, y, x + drawSize, y + drawSize, 0xFF343B45);
        QuestIcons.render(g, value, x, y, drawSize, drawSize, 1);
        g.disableScissor();
        int captionHeight = font.lineHeight + 12;
        g.fill(left() + 1, bottom() - captionHeight - 1, right() - 1, bottom() - 1, 0xDD15191F);
        g.drawString(font, font.plainSubstrByWidth(text("icon_picker.preview.hint").getString(),
                        Math.max(1, right() - left() - 20)),
                left() + 10, bottom() - font.lineHeight - 7, 0xFFB6C0CD, false);
        g.fill(0, height - 48, width, height, 0xFF252C34);
        super.renderContents(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && x >= left() && x < right() && y >= top() && y < bottom()) {
            long now = System.nanoTime();
            if (now - lastClickAt <= DOUBLE_CLICK_NANOS) {
                if (targetZoom > 1.01) resetView();
                else setZoom(2);
                lastClickAt = 0;
                return true;
            }
            lastClickAt = now;
            dragging = true;
            pressX = x;
            pressY = y;
            startPanX = panX;
            startPanY = panY;
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging) {
            panX = startPanX + x - pressX;
            panY = startPanY + y - pressY;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (x >= left() && x < right() && y >= top() && y < bottom()) {
            setZoom(targetZoom * Math.pow(1.15, vertical));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { closeTo(parent); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { resetView(); return true; }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            setZoom(targetZoom * 1.2);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            setZoom(targetZoom / 1.2);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
