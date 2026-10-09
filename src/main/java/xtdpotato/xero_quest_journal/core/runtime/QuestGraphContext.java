package xtdpotato.xero_quest_journal.core.runtime;

import java.util.HashMap;
import java.util.Map;

/** Values belong to one graph invocation, never to the next player or event. */
public final class QuestGraphContext implements AutoCloseable {
    private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();
    private final Map<String, String> previous;
    public QuestGraphContext(Map<String, String> values) {
        previous = CURRENT.get();
        CURRENT.set(new HashMap<>(values));
    }
    public static String get(String key) { return CURRENT.get() == null ? null : CURRENT.get().get(key); }
    public static Map<String, String> snapshot() {
        return CURRENT.get() == null ? Map.of() : Map.copyOf(CURRENT.get());
    }
    public static void put(String key, String value) {
        if (CURRENT.get() == null) throw new IllegalStateException("No active graph context");
        CURRENT.get().put(key, value);
    }
    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
