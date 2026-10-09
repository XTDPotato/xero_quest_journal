package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Reuses one small surface so item models and remote images share the same alpha-mask tint. */
final class CategoryIconRenderer {
    private static final int SIZE = 32;
    private static TextureTarget surface;
    private static ShaderInstance shader;

    private CategoryIconRenderer() {}

    static void shader(ShaderInstance value) {
        shader = value;
        close();
    }

    static void render(GuiGraphics graphics, String icon, int x, int y, int size, int color) {
        if (shader == null) {
            JournalNavButton.drawIcon(graphics, icon, x, y, size, color);
            return;
        }
        graphics.flush();
        var game = Minecraft.getInstance();
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        if (scissor) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.disableScissor();
        try {
            if (surface == null) {
                surface = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
                surface.setClearColor(0, 0, 0, 0);
            }
            surface.clear(Minecraft.ON_OSX);
            surface.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, 1000, 21000),
                    VertexSorting.ORTHOGRAPHIC_Z);
            var offscreen = new GuiGraphics(game, game.renderBuffers().bufferSource());
            JournalNavButton.drawIcon(offscreen, icon, 0, 0, SIZE, 0xFFFFFFFF);
            offscreen.flush();
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setProjectionMatrix(projection, sorting);
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, surface.getColorTextureId());
        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x, y + size, 0).setUv(0, 0).setColor(color);
        buffer.addVertex(matrix, x + size, y + size, 0).setUv(1, 0).setColor(color);
        buffer.addVertex(matrix, x + size, y, 0).setUv(1, 1).setColor(color);
        buffer.addVertex(matrix, x, y, 0).setUv(0, 1).setColor(color);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
    }

    static void close() {
        if (surface != null) {
            int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            surface.destroyBuffers();
            surface = null;
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        }
    }
}
