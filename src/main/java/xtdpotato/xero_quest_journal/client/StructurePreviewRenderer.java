package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class StructurePreviewRenderer {
    private record Block(int x, int y, int z, BlockState state) {}
    private record Model(int x, int y, int z, List<Block> blocks) {}
    private static final Map<String, Model> cache = new HashMap<>();
    private StructurePreviewRenderer() {}
    public static void clear() { cache.clear(); }
    private static Model load(CompoundTag tag) {
        var size = tag.getList("size", Tag.TAG_INT);
        var palette = tag.getList("palette", Tag.TAG_COMPOUND);
        var states = new ArrayList<BlockState>();
        for (int i = 0; i < palette.size(); i++) states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        var blocks = new ArrayList<Block>();
        for (var entry : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) entry;
            var pos = block.getList("pos", Tag.TAG_INT);
            blocks.add(new Block(pos.getInt(0), pos.getInt(1), pos.getInt(2), states.get(block.getInt("state"))));
        }
        return new Model(size.getInt(0), size.getInt(1), size.getInt(2), blocks);
    }
    public static void render(GuiGraphics g, String reference, int x, int y, int w, int h) {
        render(g, reference, x, y, w, h, (float) ((System.nanoTime() / 1_000_000_000.0 * 8) % 360), 1);
    }
    public static void render(GuiGraphics g, String reference, int x, int y, int w, int h, float angle, float zoom) {
        render(g, reference, x, y, w, h, angle, zoom, 0, 0);
    }
    public static void render(GuiGraphics g, String reference, int x, int y, int w, int h,
                              float angle, float zoom, float panX, float panY) {
        var game = Minecraft.getInstance();
        var tag = ClientStructures.model(reference);
        if (tag == null) {
            g.drawWordWrap(game.font, Component.translatable("screen.xero_quest_journal." + ClientStructures.error(reference)),
                    x + 4, y + 6, Math.max(1, w - 8), 0xFFBDC9CC);
            return;
        }
        Model model = cache.computeIfAbsent(reference, ignored -> load(tag));
        float scale = Math.min(w, h) * .7f / Math.max(model.y, Math.max(model.x, model.z)) * zoom;
        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f + panX, y + h / 2f + panY, 150);
        g.pose().scale(scale, -scale, scale);
        g.pose().mulPose(Axis.XP.rotationDegrees(25));
        g.pose().mulPose(Axis.YP.rotationDegrees(angle));
        g.pose().translate(-model.x / 2f, -model.y / 2f, -model.z / 2f);
        Lighting.setupFor3DItems();
        var buffer = game.renderBuffers().bufferSource();
        for (var block : model.blocks) {
            g.pose().pushPose();
            g.pose().translate(block.x, block.y, block.z);
            game.getBlockRenderer().renderSingleBlock(block.state, g.pose(), buffer,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            g.pose().popPose();
        }
        buffer.endBatch();
        g.pose().popPose();
        g.disableScissor();
        Lighting.setupFor3DItems();
    }
}
