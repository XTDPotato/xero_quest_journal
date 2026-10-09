package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** One transient surface: items, text and textures receive exactly the same final opacity. */
final class UiCompositor implements AutoCloseable {
    private TextureTarget surface;

    void capture(GuiGraphics graphics, Runnable draw) {
        var game = Minecraft.getInstance();
        var main = game.getMainRenderTarget();
        graphics.flush();
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (surface == null || surface.width != main.width || surface.height != main.height) {
            close();
            surface = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            surface.setClearColor(0, 0, 0, 0);
        }
        surface.clear(Minecraft.ON_OSX);
        surface.bindWrite(true);
        try {
            draw.run();
            graphics.flush();
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    void band(GuiGraphics graphics, int width, int height, int top, int bottom, float offset, float alpha) {
        if (surface == null || bottom <= top || alpha <= 0) return;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, surface.getColorTextureId());
        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float vTop = 1 - top / (float) height;
        float vBottom = 1 - bottom / (float) height;
        buffer.addVertex(matrix, 0, bottom + offset, 0).setUv(0, vBottom).setColor(alpha, alpha, alpha, alpha);
        buffer.addVertex(matrix, width, bottom + offset, 0).setUv(1, vBottom).setColor(alpha, alpha, alpha, alpha);
        buffer.addVertex(matrix, width, top + offset, 0).setUv(1, vTop).setColor(alpha, alpha, alpha, alpha);
        buffer.addVertex(matrix, 0, top + offset, 0).setUv(0, vTop).setColor(alpha, alpha, alpha, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    void centered(GuiGraphics graphics, int width, int height, int left, int top,
                  int right, int bottom, float scale, float alpha) {
        if (surface == null || alpha <= 0 || right <= left || bottom <= top) return;
        float boundedScale = Math.max(0.01F, Math.min(1.0F, scale));
        float regionWidth = right - left;
        float regionHeight = bottom - top;
        float drawWidth = regionWidth * boundedScale;
        float drawHeight = regionHeight * boundedScale;
        float drawLeft = (left + right - drawWidth) / 2.0F;
        float drawTop = (top + bottom - drawHeight) / 2.0F;
        float opacity = Math.max(0.0F, Math.min(1.0F, alpha));
        float uLeft = left / (float) width;
        float uRight = right / (float) width;
        float vTop = 1 - top / (float) height;
        float vBottom = 1 - bottom / (float) height;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, surface.getColorTextureId());
        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, drawLeft, drawTop + drawHeight, 0).setUv(uLeft, vBottom).setColor(opacity, opacity, opacity, opacity);
        buffer.addVertex(matrix, drawLeft + drawWidth, drawTop + drawHeight, 0).setUv(uRight, vBottom).setColor(opacity, opacity, opacity, opacity);
        buffer.addVertex(matrix, drawLeft + drawWidth, drawTop, 0).setUv(uRight, vTop).setColor(opacity, opacity, opacity, opacity);
        buffer.addVertex(matrix, drawLeft, drawTop, 0).setUv(uLeft, vTop).setColor(opacity, opacity, opacity, opacity);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    void transformed(GuiGraphics graphics, int width, int height, int left, int top,
                     int right, int bottom, double scale, double offsetX, double offsetY,
                     double rotation, double alpha) {
        if (surface == null || alpha <= 0 || right <= left || bottom <= top) return;
        double centerX = (left + right) / 2.0 + offsetX;
        double centerY = (top + bottom) / 2.0 + offsetY;
        double halfW = (right - left) * Math.clamp(scale, 0, 2.5) / 2.0;
        double halfH = (bottom - top) * Math.clamp(scale, 0, 2.5) / 2.0;
        double angle = Math.toRadians(rotation), cos = Math.cos(angle), sin = Math.sin(angle);
        double opacity = Math.clamp(alpha, 0, 1);
        double[][] points = {
                {-halfW, halfH}, {halfW, halfH}, {halfW, -halfH}, {-halfW, -halfH}
        };
        float[][] uv = {
                {left / (float) width, 1 - bottom / (float) height},
                {right / (float) width, 1 - bottom / (float) height},
                {right / (float) width, 1 - top / (float) height},
                {left / (float) width, 1 - top / (float) height}
        };
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, surface.getColorTextureId());
        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < points.length; i++) {
            double px = points[i][0], py = points[i][1];
            float x = (float) (centerX + px * cos - py * sin);
            float y = (float) (centerY + px * sin + py * cos);
            buffer.addVertex(matrix, x, y, 0).setUv(uv[i][0], uv[i][1])
                    .setColor((float) opacity, (float) opacity, (float) opacity, (float) opacity);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    @Override
    public void close() {
        if (surface != null) {
            int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if (previous == surface.frameBufferId) previous = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
            surface.destroyBuffers();
            surface = null;
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }
    }
}
