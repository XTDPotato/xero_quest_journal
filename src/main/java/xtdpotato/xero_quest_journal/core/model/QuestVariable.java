package xtdpotato.xero_quest_journal.core.model;

/** Authored initial value; live values are stored in world state, never in task source. */
public record QuestVariable(String id, String initial, long created, String type, String sharing) {
    public QuestVariable {
        if (id == null || !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}\\.[A-Za-z_][A-Za-z0-9_]{0,63}")
                || initial == null || initial.length() > 4096 || created < 0)
            throw new IllegalArgumentException("Invalid quest variable");
        type = type == null || type.isBlank() ? inferType(initial) : type;
        sharing = sharing == null || sharing.isBlank() ? "player" : sharing;
        if (!java.util.List.of("number", "string").contains(type)
                || !java.util.List.of("player", "team").contains(sharing)
                || type.equals("number") && !isNumber(initial))
            throw new IllegalArgumentException("Invalid quest variable type or initial value");
    }
    public QuestVariable(String id, String initial, long created) {
        this(id, initial, created, null, "player");
    }
    public QuestVariable(String id, String initial, long created, String type) {
        this(id, initial, created, type, "player");
    }
    public String scope() { return id.substring(0, id.lastIndexOf('.')); }
    public String name() { return id.substring(id.lastIndexOf('.') + 1); }
    public boolean numeric() { return type.equals("number"); }
    public boolean teamShared() { return sharing.equals("team"); }
    public boolean readOnly() {
        String name = name();
        return java.util.List.of("progress_current", "progress_required", "progress_ratio").contains(name);
    }

    private static String inferType(String value) { return isNumber(value) ? "number" : "string"; }
    private static boolean isNumber(String value) {
        try { return Double.isFinite(Double.parseDouble(value)); }
        catch (RuntimeException invalid) { return false; }
    }
}
