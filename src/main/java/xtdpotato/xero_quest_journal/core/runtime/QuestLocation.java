package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Portable biome/structure membership and explicit dimension-coordinate destinations. */
public record QuestLocation(String kind, String dimension, String target,
                            double x, double y, double z, double radius, boolean resolved) {
    public static QuestLocation parse(String value) {
        if (value == null) return null;
        String[] p = value.strip().split("\\|", -1);
        try {
            if (p.length < 3) return null;
            boolean coords = p[0].equals("coords");
            if (!coords && !p[0].equals("structure") && !p[0].equals("biome")) return null;
            if (coords && p[1].isBlank()) return null;
            if ((coords || !p[1].isBlank()) && ResourceLocation.tryParse(p[1]) == null) return null;
            if (coords ? p.length != 6 : p.length != 3 && p.length != 4 && p.length != 7) return null;
            String target = coords ? "" : p[2];
            if (!coords && targetId(target) == null) return null;
            // Old four/seven-part places keep their identity, not a world's resolved coordinates.
            if (!coords) return new QuestLocation(p[0], p[1].isBlank() ? "" : p[1], target, 0, 0, 0, 0, false);
            double radius = Double.parseDouble(p[5]);
            if (!Double.isFinite(radius) || radius < 1 || radius > 100000) return null;
            double x = Double.parseDouble(p[2]);
            double y = Double.parseDouble(p[3]);
            double z = Double.parseDouble(p[4]);
            if (!coordinate(x) || !coordinate(y) || !coordinate(z)) return null;
            return new QuestLocation(p[0], p[1], target, x, y, z, radius, true);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000;
    }

    public static ResourceLocation targetId(String target) {
        String id = target.startsWith("#") ? target.substring(1) : target;
        return id.isBlank() ? null : ResourceLocation.tryParse(id);
    }

    public String encode() {
        return kind + "|" + dimension + "|" + (kind.equals("coords")
                ? x + "|" + y + "|" + z + "|" + radius : target);
    }

    public String summary() {
        return kind.equals("coords") ? dimension + " [" + x + ", " + y + ", " + z + "] (r=" + radius + ")"
                : dimension.isBlank() ? target : dimension + " / " + target;
    }

    private static HolderSet<Structure> structures(ServerLevel level, String target) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceLocation id = targetId(target);
        if (id == null) return HolderSet.direct();
        return target.startsWith("#")
                ? registry.getTag(TagKey.create(Registries.STRUCTURE, id))
                    .<HolderSet<Structure>>map(set -> set).orElseGet(HolderSet::direct)
                : registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id))
                    .<HolderSet<Structure>>map(HolderSet::direct).orElseGet(HolderSet::direct);
    }

    public boolean reached(ServerPlayer player) {
        if (!dimension.isBlank() && !player.level().dimension().location().toString().equals(dimension)) return false;
        if (kind.equals("coords")) {
            double dx = player.getX() - x, dz = player.getZ() - z;
            double dy = player.getY() - y;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
        if (kind.equals("structure")) {
            var level = player.serverLevel();
            var position = player.blockPosition();
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) return false;
            for (var holder : structures(level, target)) {
                for (long reference : chunk.getReferencesForStructure(holder.value())) {
                    var start = level.getChunkSource().getChunk(net.minecraft.world.level.ChunkPos.getX(reference),
                            net.minecraft.world.level.ChunkPos.getZ(reference),
                            net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS, false);
                    if (start == null) continue;
                    var structure = start.getStartForStructure(holder.value());
                    if (structure != null && structure.isValid() && structure.getBoundingBox().isInside(position))
                        return true;
                }
            }
            return false;
        }
        var biome = player.level().getBiome(player.blockPosition());
        var id = targetId(target);
        return target.startsWith("#") ? biome.is(TagKey.create(Registries.BIOME, id))
                : biome.is(ResourceKey.create(Registries.BIOME, id));
    }
}
