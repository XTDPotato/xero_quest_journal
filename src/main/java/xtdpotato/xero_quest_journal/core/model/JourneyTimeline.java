package xtdpotato.xero_quest_journal.core.model;

import java.util.List;
import java.util.Map;

/** Display-only chapter layout; connections never alter quest prerequisites. */
public record JourneyTimeline(Map<String, Position> positions, List<Connection> connections,
                              boolean customConnections, boolean explicitTasks) {
    /** Reserved endpoint that targets the current chapter's timeline diamond. */
    public static final String CHAPTER_POINT = "@chapter";

    public static boolean chapterPoint(String endpoint) {
        return CHAPTER_POINT.equals(endpoint);
    }
    public JourneyTimeline(Map<String, Position> positions, List<Connection> connections, boolean customConnections) {
        this(positions, connections, customConnections, false);
    }
    public record Position(int x, int y) {
        public Position {
            if (x < 100 || x > 100000 || Math.abs((long) y) > 100000)
                throw new IllegalArgumentException("Invalid journey position");
        }
    }
    public record Connection(String from, String to) {
        public Connection {
            if (from == null || to == null || from.isBlank() || to.isBlank()
                    || from.equals(to) || from.length() > 128 || to.length() > 128
                    || chapterPoint(from) && chapterPoint(to))
                throw new IllegalArgumentException("Invalid journey connection");
        }
    }
    public JourneyTimeline {
        positions = positions == null ? Map.of() : Map.copyOf(positions);
        connections = connections == null ? List.of() : List.copyOf(connections);
        if (positions.size() > 4096 || connections.size() > 8192
                || positions.keySet().stream().anyMatch(id -> id.isBlank() || id.length() > 128)
                || connections.stream().distinct().count() != connections.size())
            throw new IllegalArgumentException("Journey layout too large");
    }
    public static JourneyTimeline empty() { return new JourneyTimeline(Map.of(), List.of(), false); }
}
