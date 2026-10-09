package xtdpotato.xero_quest_journal.core.model;

public record QuestType(String id, String name, String icon) {
    public QuestType {
        if (id == null || !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid quest type ID");
        }
        name = name == null || name.isBlank() ? id : name.strip();
        icon = icon == null ? "" : icon.strip();
        if (name.length() > 128 || icon.length() > 8192) {
            throw new IllegalArgumentException("Invalid quest type");
        }
    }
}
