package xtdpotato.xero_quest_journal.core.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared, presentation-only state for the hot-events canvas. */
public record HotEventCanvas(String backgroundImage, String startId,
                             List<Connection> connections, boolean customized,
                             Map<String, Position> positions, String backgroundType,
                             double backgroundScale, int backgroundOffsetX, int backgroundOffsetY,
                             Set<String> knownCardIds) {
    public HotEventCanvas {
        backgroundImage = backgroundImage == null ? "" : backgroundImage;
        startId = startId == null ? "" : startId;
        connections = connections == null ? List.of() : List.copyOf(connections);
        positions = positions == null ? Map.of() : Map.copyOf(positions);
        knownCardIds = knownCardIds == null ? Set.of() : Set.copyOf(knownCardIds);
        backgroundType = backgroundType == null ? "image" : backgroundType;
        backgroundScale = backgroundScale == 0 ? 1.0 : backgroundScale;
        if (backgroundImage.length() > 4096 || startId.length() > 256
                || connections.size() > 2048 || positions.size() > 4096 || knownCardIds.size() > 4096
                || !List.of("image", "video").contains(backgroundType)
                || !Double.isFinite(backgroundScale) || backgroundScale < 0.1 || backgroundScale > 10
                || Math.abs((long) backgroundOffsetX) > 10000 || Math.abs((long) backgroundOffsetY) > 10000) {
            throw new IllegalArgumentException("Invalid hot-event canvas");
        }
    }

    public HotEventCanvas(String backgroundImage, String startId, List<Connection> connections,
                          boolean customized, Map<String, Position> positions, String backgroundType,
                          double backgroundScale, int backgroundOffsetX, int backgroundOffsetY) {
        this(backgroundImage, startId, connections, customized, positions, backgroundType,
                backgroundScale, backgroundOffsetX, backgroundOffsetY, Set.of());
    }

    public HotEventCanvas(String backgroundImage, String startId,
                          List<Connection> connections, boolean customized, Map<String, Position> positions) {
        this(backgroundImage, startId, connections, customized, positions, "image", 1, 0, 0);
    }

    public HotEventCanvas(String backgroundImage, String startId,
                          List<Connection> connections, boolean customized) {
        this(backgroundImage, startId, connections, customized, Map.of(), "image", 1, 0, 0);
    }

    public static HotEventCanvas empty() {
        return new HotEventCanvas("", "", List.of(), false, Map.of());
    }

    public record Position(int x, int y) {
        public Position {
            if (Math.abs((long) x) > 1_000_000 || Math.abs((long) y) > 1_000_000) {
                throw new IllegalArgumentException("Invalid hot-event position");
            }
        }
    }

    public record Connection(String from, String to) {
        public Connection {
            from = from == null ? "" : from;
            to = to == null ? "" : to;
            if (from.length() > 256 || to.length() > 256 || from.isBlank() || to.isBlank()
                    || from.equals(to)) {
                throw new IllegalArgumentException("Invalid hot-event connection");
            }
        }
    }
}
