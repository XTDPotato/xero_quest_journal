package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

/** Replans a loaded-terrain walking route in bounded work, never forcing chunk loads. */
public final class CoordinateGuidance {
    private static final long REPLAN_TICKS = 20;
    private static String questId = "";
    private static String dimension = "";
    private static QuestLocation target;
    private static List<CoordinateRoute.Cell> route = List.of();
    private static double lastX, lastY, lastZ;
    private static long lastPlanTick = Long.MIN_VALUE;
    private static boolean requested;

    private CoordinateGuidance() {}

    public static void start(String id) {
        if (id == null || id.isBlank()) return;
        if (requested && questId.equals(id)) return;
        questId = id;
        target = null;
        route = List.of();
        lastPlanTick = Long.MIN_VALUE;
        requested = true;
    }

    public static void clear() {
        questId = "";
        dimension = "";
        target = null;
        route = List.of();
        requested = false;
        lastPlanTick = Long.MIN_VALUE;
    }

    public static List<CoordinateRoute.Cell> route() { return route; }
    static boolean active() { return requested && !route.isEmpty(); }
    static boolean active(String id) { return active() && questId.equals(id); }

    public static void tick(Minecraft game) {
        if (!requested || game.player == null || game.level == null
                || !ClientQuestProgress.trackedIds().contains(questId)) {
            if (requested && !ClientQuestProgress.trackedIds().contains(questId)) clear();
            return;
        }
        QuestDefinitionRoute state = destination(questId);
        if (state == null) { route = List.of(); return; }
        String currentDimension = game.level.dimension().location().toString();
        if (!state.location().dimension().equals(currentDimension)) {
            route = List.of();
            target = state.location();
            dimension = currentDimension;
            return;
        }

        var player = game.player;
        var location = state.location();
        double arrivalRadius = Math.min(1.75, location.radius());
        if (player.distanceToSqr(location.x(), location.y(), location.z())
                <= arrivalRadius * arrivalRadius) {
            route = List.of();
            target = location;
            dimension = currentDimension;
            lastPlanTick = game.level.getGameTime();
            return;
        }
        long now = game.level.getGameTime();
        boolean moved = square(player.getX() - lastX) + square(player.getY() - lastY)
                + square(player.getZ() - lastZ) >= 9;
        if (target != null && target.equals(location) && currentDimension.equals(dimension)
                && !moved && now - lastPlanTick < REPLAN_TICKS) return;

        target = location;
        dimension = currentDimension;
        lastPlanTick = now;
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        boolean underwater = player.isEyeInFluid(FluidTags.WATER);
        var start = findStart(game.level, player, underwater);
        if (start == null) { route = List.of(); return; }
        route = CoordinateRoute.find(start, location,
                from -> neighbors(game.level, player, from, underwater), CoordinateRoute.MAX_EXPANDED).cells();
    }

    private record QuestDefinitionRoute(QuestLocation location) {}

    private static QuestDefinitionRoute destination(String id) {
        var quest = ClientQuestDefinitions.find(id);
        if (quest == null) return null;
        QuestLocation location = QuestHudRenderer.location(quest);
        return location != null && location.kind().equals("coords") && location.resolved()
                ? new QuestDefinitionRoute(location) : null;
    }

    private static double square(double value) { return value * value; }

    private static CoordinateRoute.Cell findStart(Level level, net.minecraft.world.entity.player.Player player,
                                                   boolean underwater) {
        int baseX = BlockPos.containing(player.getX(), player.getY(), player.getZ()).getX();
        int baseY = BlockPos.containing(player.getX(), player.getY(), player.getZ()).getY();
        int baseZ = BlockPos.containing(player.getX(), player.getY(), player.getZ()).getZ();
        CoordinateRoute.Cell best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                for (int dy = 1; dy >= -3; dy--) {
                    var candidate = new CoordinateRoute.Cell(baseX + dx, baseY + dy, baseZ + dz);
                    if (!walkable(level, player, candidate, underwater)) continue;
                    double distance = square(candidate.x() + .5 - player.getX())
                            + square(candidate.y() - player.getY())
                            + square(candidate.z() + .5 - player.getZ());
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static List<CoordinateRoute.Cell> neighbors(
            Level level, net.minecraft.world.entity.player.Player player, CoordinateRoute.Cell current,
            boolean underwater) {
        var result = new ArrayList<CoordinateRoute.Cell>(8);
        for (int[] direction : CoordinateRoute.directions()) {
            int x = current.x() + direction[0], z = current.z() + direction[1];
            for (int dy : new int[]{0, 1, -1, -2, -3}) {
                var candidate = new CoordinateRoute.Cell(x, current.y() + dy, z);
                if (!walkable(level, player, candidate, underwater)) continue;
                if (direction[0] != 0 && direction[1] != 0) {
                    var sideX = new CoordinateRoute.Cell(current.x() + direction[0], current.y(), current.z());
                    var sideZ = new CoordinateRoute.Cell(current.x(), current.y(), current.z() + direction[1]);
                    if (!walkable(level, player, sideX, underwater)
                            || !walkable(level, player, sideZ, underwater)) continue;
                }
                result.add(candidate);
                break;
            }
        }
        return result;
    }

    private static boolean walkable(Level level, net.minecraft.world.entity.player.Player player,
                                    CoordinateRoute.Cell cell, boolean underwater) {
        BlockPos feet = new BlockPos(cell.x(), cell.y(), cell.z());
        BlockPos floor = feet.below();
        if (!loaded(level, floor) || !loaded(level, feet) || !loaded(level, feet.above())) return false;
        if (!level.getWorldBorder().isWithinBounds(floor) || !level.getWorldBorder().isWithinBounds(feet))
            return false;
        var support = level.getBlockState(floor);
        var supportShape = support.getCollisionShape(level, floor, CollisionContext.of(player));
        var footState = level.getBlockState(feet);
        boolean solidFloor = !supportShape.isEmpty() && supportShape.max(Direction.Axis.Y) >= .875
                && support.getFluidState().isEmpty();
        boolean waterSurface = !underwater && support.getFluidState().is(FluidTags.WATER)
                && footState.getFluidState().isEmpty()
                && level.getBlockState(feet.above()).getFluidState().isEmpty();
        boolean swimming = underwater && footState.getFluidState().is(FluidTags.WATER);
        if (!solidFloor && !waterSurface && !swimming) return false;

        var dimensions = player.getDimensions(Pose.STANDING);
        double halfWidth = dimensions.width() * .5;
        AABB box = new AABB(cell.x() + .5 - halfWidth, cell.y() + .001, cell.z() + .5 - halfWidth,
                cell.x() + .5 + halfWidth, cell.y() + dimensions.height(), cell.z() + .5 + halfWidth);
        int minX = BlockPos.containing(box.minX, box.minY, box.minZ).getX();
        int minY = BlockPos.containing(box.minX, box.minY, box.minZ).getY();
        int minZ = BlockPos.containing(box.minX, box.minY, box.minZ).getZ();
        int maxX = BlockPos.containing(box.maxX, Math.nextDown(box.maxY), box.maxZ).getX();
        int maxY = BlockPos.containing(box.maxX, Math.nextDown(box.maxY), box.maxZ).getY();
        int maxZ = BlockPos.containing(box.maxX, Math.nextDown(box.maxY), box.maxZ).getZ();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++) {
                var pos = new BlockPos(x, y, z);
                if (!loaded(level, pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            }
        return level.noCollision(player, box);
    }

    private static boolean loaded(Level level, BlockPos pos) { return level.hasChunkAt(pos); }
}
