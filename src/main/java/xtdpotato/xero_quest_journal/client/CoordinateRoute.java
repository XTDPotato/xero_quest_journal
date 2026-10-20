package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.world.phys.Vec3;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

/** Bounded A* over caller-supplied walkable floor cells. */
public final class CoordinateRoute {
    static final int MAX_EXPANDED = 4200;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public record Cell(int x, int y, int z) {
        Vec3 center() { return new Vec3(x + .5, y, z + .5); }
    }
    public record Result(List<Cell> cells, boolean reached) {
        public Result { cells = List.copyOf(cells); }
    }
    private record Open(Cell cell, double cost, double estimate, long order) {}

    private CoordinateRoute() {}

    public static Result find(Cell start, QuestLocation target, Function<Cell, List<Cell>> neighbors, int limit) {
        if (start == null || target == null || neighbors == null) return new Result(List.of(), false);
        var frontier = new PriorityQueue<Open>(java.util.Comparator.comparingDouble(Open::estimate)
                .thenComparingLong(Open::order));
        Map<Cell, Double> costs = new HashMap<>();
        Map<Cell, Cell> previous = new HashMap<>();
        Set<Cell> visited = new HashSet<>();
        long order = 0;
        double startHeuristic = heuristic(start, target);
        frontier.add(new Open(start, 0, startHeuristic, order++));
        costs.put(start, 0.0);
        Cell best = start;
        double bestHeuristic = startHeuristic;
        int expanded = 0;

        while (!frontier.isEmpty() && expanded < Math.max(1, limit)) {
            Open next = frontier.poll();
            if (next.cost() > costs.getOrDefault(next.cell(), Double.POSITIVE_INFINITY)) continue;
            if (!visited.add(next.cell())) continue;
            expanded++;
            double h = heuristic(next.cell(), target);
            if (h < bestHeuristic) {
                best = next.cell();
                bestHeuristic = h;
            }
            if (insideTarget(next.cell(), target)) return new Result(reconstruct(previous, next.cell()), true);
            for (Cell candidate : neighbors.apply(next.cell())) {
                if (candidate == null || visited.contains(candidate)) continue;
                double edge = edgeCost(next.cell(), candidate);
                double cost = next.cost() + edge;
                if (cost >= costs.getOrDefault(candidate, Double.POSITIVE_INFINITY)) continue;
                costs.put(candidate, cost);
                previous.put(candidate, next.cell());
                frontier.add(new Open(candidate, cost, cost + heuristic(candidate, target), order++));
            }
        }
        if (best.equals(start)) return new Result(List.of(), false);
        return new Result(reconstruct(previous, best), false);
    }

    private static boolean insideTarget(Cell cell, QuestLocation target) {
        double dx = cell.x() + .5 - target.x();
        double dy = cell.y() - target.y();
        double dz = cell.z() + .5 - target.z();
        double arrivalRadius = Math.min(1.5, target.radius());
        return dx * dx + dy * dy + dz * dz <= arrivalRadius * arrivalRadius;
    }

    private static double heuristic(Cell cell, QuestLocation target) {
        double dx = Math.abs(cell.x() + .5 - target.x());
        double dz = Math.abs(cell.z() + .5 - target.z());
        double dy = Math.abs(cell.y() - target.y());
        return Math.max(0, dx + dz + dy - Math.min(1.5, target.radius()) * Math.sqrt(3));
    }

    private static double edgeCost(Cell from, Cell to) {
        return 1 + Math.abs(from.y() - to.y());
    }

    private static List<Cell> reconstruct(Map<Cell, Cell> previous, Cell last) {
        List<Cell> result = new ArrayList<>();
        for (Cell current = last; current != null; current = previous.get(current)) result.add(current);
        Collections.reverse(result);
        return result;
    }

    public static List<int[]> directions() {
        return List.of(NEIGHBORS[0], NEIGHBORS[1], NEIGHBORS[2], NEIGHBORS[3]);
    }
}
