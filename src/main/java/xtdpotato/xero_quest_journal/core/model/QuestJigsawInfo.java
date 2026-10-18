package xtdpotato.xero_quest_journal.core.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** Connection metadata from a sanitized template preview, in template-local coordinates. */
public record QuestJigsawInfo(BlockPos position, Direction front, Direction top, String name, String target,
                              String pool, String joint, String finalState, int selectionPriority,
                              int placementPriority, boolean priorities, boolean complete) {
    public String coordinates() { return position.getX() + ", " + position.getY() + ", " + position.getZ(); }

    public static List<QuestJigsawInfo> read(CompoundTag data) {
        if (data == null) return List.of();
        var palette = data.getList("palette", Tag.TAG_COMPOUND);
        var result = new ArrayList<QuestJigsawInfo>();
        for (var entry : data.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) entry;
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size()) continue;
            var material = palette.getCompound(state);
            if (!material.getString("Name").equals("minecraft:jigsaw")) continue;
            var pos = block.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3) continue;
            String[] orientation = material.getCompound("Properties").getString("orientation").split("_");
            Direction front = direction(orientation, 0, Direction.NORTH), top = direction(orientation, 1, Direction.UP);
            var metadata = block.getCompound("nbt");
            String joint = metadata.getString("joint");
            if (!joint.equals("aligned") && !joint.equals("rollable"))
                joint = front.getAxis().isHorizontal() ? "aligned" : "rollable";
            boolean complete = List.of("name", "target", "pool", "final_state").stream()
                    .allMatch(key -> metadata.contains(key, Tag.TAG_STRING));
            result.add(new QuestJigsawInfo(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                    front, top, metadata.getString("name"), metadata.getString("target"),
                    metadata.getString("pool"), joint, metadata.getString("final_state"),
                    metadata.getInt("selection_priority"), metadata.getInt("placement_priority"),
                    metadata.contains("selection_priority") || metadata.contains("placement_priority"), complete));
        }
        return List.copyOf(result);
    }

    private static Direction direction(String[] values, int index, Direction fallback) {
        Direction result = index < values.length ? Direction.byName(values[index]) : null;
        return result == null ? fallback : result;
    }
}
