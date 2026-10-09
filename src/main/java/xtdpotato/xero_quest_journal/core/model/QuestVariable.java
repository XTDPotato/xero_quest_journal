package xtdpotato.xero_quest_journal.core.model;

/** Authored initial value; live values are stored per player, never in task source. */
public record QuestVariable(String id, String initial, long created) {
    public QuestVariable {
        if (id == null || !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}\\.[A-Za-z_][A-Za-z0-9_]{0,63}")
                || initial == null || initial.length() > 4096 || created < 0)
            throw new IllegalArgumentException("Invalid quest variable");
    }
    public String scope() { return id.substring(0, id.lastIndexOf('.')); }
    public String name() { return id.substring(id.lastIndexOf('.') + 1); }
}
