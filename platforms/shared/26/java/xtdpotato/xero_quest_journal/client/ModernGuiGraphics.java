package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Optional;

/** Small source-compatibility facade over the 26.x render-state extractor. */
public final class ModernGuiGraphics {
    private final GuiGraphicsExtractor extractor;

    public ModernGuiGraphics(GuiGraphicsExtractor extractor) {
        this.extractor = extractor;
    }

    public GuiGraphicsExtractor raw() { return extractor; }
    public Pose pose() { return new Pose(extractor.pose()); }
    public void fill(int x0, int y0, int x1, int y1, int color) { extractor.fill(x0, y0, x1, y1, color); }
    public void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        extractor.fillGradient(x0, y0, x1, y1, top, bottom);
    }
    public void renderOutline(int x, int y, int width, int height, int color) {
        extractor.outline(x, y, width, height, color);
    }
    public void enableScissor(int left, int top, int right, int bottom) {
        extractor.enableScissor(left, top, right, bottom);
    }
    public void disableScissor() { extractor.disableScissor(); }
    public void flush() {}

    public void drawString(Font font, String value, int x, int y, int color) {
        extractor.text(font, value, x, y, color);
    }
    public void drawString(Font font, String value, int x, int y, int color, boolean shadow) {
        extractor.text(font, value, x, y, color, shadow);
    }
    public void drawString(Font font, Component value, int x, int y, int color, boolean shadow) {
        extractor.text(font, value, x, y, color, shadow);
    }
    public void drawString(Font font, Component value, int x, int y, int color) {
        extractor.text(font, value, x, y, color);
    }
    public void drawString(Font font, FormattedCharSequence value, int x, int y, int color, boolean shadow) {
        extractor.text(font, value, x, y, color, shadow);
    }
    public void drawString(Font font, FormattedCharSequence value, int x, int y, int color) {
        extractor.text(font, value, x, y, color);
    }
    public int drawCenteredString(Font font, Component value, int centerX, int y, int color) {
        extractor.centeredText(font, value, centerX, y, color);
        return centerX - font.width(value) / 2;
    }
    public int drawCenteredString(Font font, String value, int centerX, int y, int color) {
        extractor.centeredText(font, value, centerX, y, color);
        return centerX - font.width(value) / 2;
    }
    public int drawCenteredString(Font font, FormattedCharSequence value, int centerX, int y, int color) {
        extractor.centeredText(font, value, centerX, y, color);
        return centerX - font.width(value) / 2;
    }
    public void drawWordWrap(Font font, Component value, int x, int y, int width, int color) {
        extractor.textWithWordWrap(font, value, x, y, width, color);
    }
    public void drawManaged(Runnable draw) { draw.run(); }
    public static void render(ModernGuiGraphics graphics, Renderable renderable,
                              int mouseX, int mouseY, float delta) {
        renderable.extractRenderState(graphics.raw(), mouseX, mouseY, delta);
    }
    public void hLine(int x1, int x2, int y, int color) { extractor.horizontalLine(x1, x2, y, color); }
    public void vLine(int x, int y1, int y2, int color) { extractor.verticalLine(x, y1, y2, color); }
    public int guiWidth() { return extractor.guiWidth(); }
    public int guiHeight() { return extractor.guiHeight(); }

    public void renderItem(ItemStack stack, int x, int y) { extractor.item(stack, x, y); }
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        extractor.itemDecorations(font, stack, x, y);
    }
    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        extractor.setTooltipForNextFrame(font, stack, x, y);
    }
    public void renderTooltip(Font font, Component text, int x, int y) {
        extractor.setTooltipForNextFrame(font, text, x, y);
    }
    public void renderTooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        extractor.setTooltipForNextFrame(font, lines, x, y);
    }
    public void renderTooltip(Font font, List<Component> lines, Optional<?> image, int x, int y) {
        @SuppressWarnings("unchecked")
        Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> component =
                (Optional<net.minecraft.world.inventory.tooltip.TooltipComponent>) image;
        extractor.setTooltipForNextFrame(font, lines, component, x, y);
    }
    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        extractor.setComponentTooltipForNextFrame(font, lines, x, y);
    }

    public void blit(Identifier texture, int x, int y, int width, int height,
                     int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        extractor.blit(texture, x, y, width, height, u / (float) textureWidth, v / (float) textureHeight,
                (u + regionWidth) / (float) textureWidth, (v + regionHeight) / (float) textureHeight);
    }
    public void blit(Identifier texture, int x, int y, int u, int v, int width, int height) {
        extractor.blit(texture, x, y, width, height, u / 256.0F, v / 256.0F,
                (u + width) / 256.0F, (v + height) / 256.0F);
    }
    public void blit(Identifier texture, int x, int y, int width, int height,
                     float u, float v, float regionWidth, float regionHeight,
                     int textureWidth, int textureHeight) {
        extractor.blit(texture, x, y, width, height, u / textureWidth, v / textureHeight,
                (u + regionWidth) / textureWidth, (v + regionHeight) / textureHeight);
    }

    public static final class Pose {
        private final Matrix3x2fStack stack;
        private Pose(Matrix3x2fStack stack) { this.stack = stack; }
        public void pushPose() { stack.pushMatrix(); }
        public void popPose() { stack.popMatrix(); }
        public void translate(double x, double y, double ignoredZ) {
            stack.translate((float) x, (float) y);
        }
        public void scale(float x, float y, float ignoredZ) { stack.scale(x, y); }
    }

    public void setColor(float red, float green, float blue, float alpha) {
        // The extraction API has no mutable global tint state. Alpha is applied by
        // the caller's color/pipeline where supported instead of reaching into GL.
    }
}
