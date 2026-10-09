package xtdpotato.xero_quest_journal.core.model;

/** Author-defined completion and structure-runtime permissions. */
public record CompletionPolicy(boolean events, boolean reenterStructure, boolean exportAcquiredItems,
                               boolean structureProgress, boolean automaticRewardPopup) {
    public CompletionPolicy(boolean events, boolean reenterStructure, boolean exportAcquiredItems) {
        this(events, reenterStructure, exportAcquiredItems, false, true);
    }

    public CompletionPolicy(boolean events, boolean reenterStructure, boolean exportAcquiredItems,
                            boolean structureProgress) {
        this(events, reenterStructure, exportAcquiredItems, structureProgress, true);
    }

    public static CompletionPolicy defaults() {
        return new CompletionPolicy(false, false, false, false, true);
    }
}
