package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class QuestStructurePreviewScreen extends AnimatedQuestScreen {
    private static final long RESET_DOUBLE_CLICK_NANOS = 350_000_000L;
    private final Screen parent;
    private final String reference;
    private float angle = 35, zoom = 1;
    private float panX, panY;
    private double lastMiddleX, lastMiddleY;
    private long lastMiddleClick;
    private boolean middlePanning, middleMoved, rotating;
    public QuestStructurePreviewScreen(Screen parent, String reference) {
        super(Component.translatable("screen.xero_quest_journal.structure.preview"));
        this.parent = parent; this.reference = reference;
    }
    @Override protected void init() {
        addRenderableWidget(new StyledButton(width - 32, 8, 24, 20, Component.literal("\u00d7"),
                button -> onClose(), ButtonStyleSheet.load(), "structure-close", "toolbar-button"));
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xFF20272B);
        g.drawString(font, title, 12, 12, 0xFFE7D6AC, false);
        StructurePreviewRenderer.render(g, reference, 16, 40, width - 32, height - 56,
                angle, zoom, panX, panY);
        super.renderContents(g, x, y, delta);
    }

    private boolean inPreview(double x, double y) {
        return x >= 16 && x < width - 16 && y >= 40 && y < height - 16;
    }

    private void resetView() {
        angle = 35;
        zoom = 1;
        panX = panY = 0;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (inPreview(x, y) && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            long now = System.nanoTime();
            boolean doubleClick = lastMiddleClick != 0 && now - lastMiddleClick <= RESET_DOUBLE_CLICK_NANOS
                    && Math.abs(x - lastMiddleX) <= 4 && Math.abs(y - lastMiddleY) <= 4;
            if (doubleClick) {
                resetView();
                lastMiddleClick = 0;
                middlePanning = false;
                middleMoved = false;
            } else {
                middlePanning = true;
                middleMoved = false;
                lastMiddleClick = now;
                lastMiddleX = x;
                lastMiddleY = y;
            }
            return true;
        }
        if (inPreview(x, y) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            rotating = true;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (inputBlocked()) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && middlePanning) {
            if (Math.abs(x - lastMiddleX) >= 4 || Math.abs(y - lastMiddleY) >= 4) {
                middleMoved = true;
                lastMiddleClick = 0;
            }
            panX = Math.clamp(panX + (float) dx, -(width - 32) * .75F, (width - 32) * .75F);
            panY = Math.clamp(panY + (float) dy, -(height - 56) * .75F, (height - 56) * .75F);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && rotating) { angle += (float) dx; return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && middlePanning) {
            middlePanning = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && rotating) {
            rotating = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (!inPreview(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        zoom = Math.clamp(zoom + (float) vertical * .1f, .25f, 3);
        return true;
    }
    @Override public void onClose() { closeTo(parent); }
}
