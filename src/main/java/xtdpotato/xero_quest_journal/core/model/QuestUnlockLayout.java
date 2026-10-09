package xtdpotato.xero_quest_journal.core.model;

import java.util.Map;

/** Editor positions only; prerequisite edges remain authoritative in task sources. */
public record QuestUnlockLayout(Map<String, Position> positions) {
    public record Position(int x, int y) {
        public Position {
            if (Math.abs((long) x) > 100000 || Math.abs((long) y) > 100000)
                throw new IllegalArgumentException("Unlock node position is out of bounds");
        }
    }

    public QuestUnlockLayout {
        positions = positions == null ? Map.of() : Map.copyOf(positions);
        if (positions.size() > 4096 || positions.entrySet().stream().anyMatch(
                entry -> entry.getKey() == null || entry.getKey().length() > 128 || entry.getValue() == null
                        || Math.abs((long) entry.getValue().x()) > 100000
                        || Math.abs((long) entry.getValue().y()) > 100000))
            throw new IllegalArgumentException("Invalid unlock layout");
    }

    public static QuestUnlockLayout empty() { return new QuestUnlockLayout(Map.of()); }
}
