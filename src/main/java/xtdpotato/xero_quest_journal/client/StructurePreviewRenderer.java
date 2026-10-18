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
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import xtdpotato.xero_quest_journal.core.model.QuestJigsawInfo;
import java.util.*;

public final class StructurePreviewRenderer {
    private record Block(int x, int y, int z, BlockState state) {}
    private record Model(int x, int y, int z, List<Block> blocks, List<QuestJigsawInfo> jigsaws) {}
    public record JigsawMarker(QuestJigsawInfo info, float x, float y, float depth) {}
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
        return new Model(size.getInt(0), size.getInt(1), size.getInt(2), blocks, QuestJigsawInfo.read(tag));
    }
    public static void render(GuiGraphics g, String reference, int x, int y, int w, int h) {
        render(g, reference, x, y, w, h, (float) ((System.nanoTime() / 1_000_000_000.0 * 8) % 360), 1);
    }
    public static void render(GuiGraphics g, String reference, int x, int y, int w, int h, float angle, float zoom) {
        render(g, reference, x, y, w, h, angle, zoom, 0, 0);
    }
    public static void render(GuiGraphics g, String reference, int x, int y, int w, int h,
                              float angle, float zoom, float panX, float panY) {
        render(g, reference, x, y, w, h, angle, zoom, panX, panY, null);
    }
    public static void render(GuiGraphics g, String reference, int x, int y, int w, int h,
                              float angle, float zoom, float panX, float panY, BlockPos selected) {
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
        if (selected != null) {
            var transform = transform(model, x, y, w, h, angle, zoom, panX, panY);
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            for (var point : model.jigsaws) {
                var pos = point.position();
                var centre = transform.transformPosition(new Vector3f(pos.getX() + .5f, pos.getY() + .5f, pos.getZ() + .5f));
                boolean active = pos.equals(selected);
                int mx = Math.round(centre.x), my = Math.round(centre.y);
                g.fill(mx - 3, my - 3, mx + 4, my + 4, active ? 0xFFFFD77F : 0xFF74DDDE);
                g.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF20272B);
                if (!active) continue;
                for (int corner = 0; corner < 8; corner++)
                    for (int axis = 0; axis < 3; axis++) if ((corner & (1 << axis)) == 0) {
                        var a = transform.transformPosition(new Vector3f(pos.getX() + (corner & 1),
                                pos.getY() + ((corner >> 1) & 1), pos.getZ() + ((corner >> 2) & 1)));
                        int other = corner | (1 << axis);
                        var b = transform.transformPosition(new Vector3f(pos.getX() + (other & 1),
                                pos.getY() + ((other >> 1) & 1), pos.getZ() + ((other >> 2) & 1)));
                        line(g, a, b, 0xFFFFD77F);
                    }
                var front = transform.transformPosition(new Vector3f(pos.getX() + .5f + point.front().getStepX() * 1.6f,
                        pos.getY() + .5f + point.front().getStepY() * 1.6f,
                        pos.getZ() + .5f + point.front().getStepZ() * 1.6f));
                var top = transform.transformPosition(new Vector3f(pos.getX() + .5f + point.top().getStepX(),
                        pos.getY() + .5f + point.top().getStepY(), pos.getZ() + .5f + point.top().getStepZ()));
                line(g, centre, front, 0xFFFFD77F);
                line(g, centre, top, 0xFF74DDDE);
                g.fill(Math.round(front.x) - 2, Math.round(front.y) - 2,
                        Math.round(front.x) + 3, Math.round(front.y) + 3, 0xFFFFD77F);
            }
            g.pose().popPose();
        }
        g.disableScissor();
        Lighting.setupFor3DItems();
    }

    public static List<JigsawMarker> jigsawMarkers(String reference, int x, int y, int w, int h,
                                                   float angle, float zoom, float panX, float panY) {
        var data = ClientStructures.model(reference);
        if (data == null) return List.of();
        var model = cache.computeIfAbsent(reference, ignored -> load(data));
        var transform = transform(model, x, y, w, h, angle, zoom, panX, panY);
        return model.jigsaws.stream().map(point -> {
            var pos = point.position();
            var centre = transform.transformPosition(new Vector3f(pos.getX() + .5f, pos.getY() + .5f, pos.getZ() + .5f));
            return new JigsawMarker(point, centre.x, centre.y, centre.z);
        }).toList();
    }

    private static Matrix4f transform(Model model, int x, int y, int w, int h,
                                      float angle, float zoom, float panX, float panY) {
        float scale = Math.min(w, h) * .7f / Math.max(model.y, Math.max(model.x, model.z)) * zoom;
        return new Matrix4f().translate(x + w / 2f + panX, y + h / 2f + panY, 150)
                .scale(scale, -scale, scale).rotateX((float) Math.toRadians(25))
                .rotateY((float) Math.toRadians(angle)).translate(-model.x / 2f, -model.y / 2f, -model.z / 2f);
    }

    private static void line(GuiGraphics g, Vector3f from, Vector3f to, int color) {
        int steps = Math.min(512, Math.max(1, (int) Math.ceil(Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y)))));
        for (int step = 0; step <= steps; step++) {
            int x = Math.round(from.x + (to.x - from.x) * step / steps);
            int y = Math.round(from.y + (to.y - from.y) * step / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
