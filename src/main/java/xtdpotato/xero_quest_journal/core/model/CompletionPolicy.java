package xtdpotato.xero_quest_journal.core.model;

/** Author-defined completion and structure-runtime permissions. */
public record CompletionPolicy(boolean events, boolean reenterStructure, boolean exportAcquiredItems,
                               boolean structureProgress, boolean automaticRewardPopup, String prerequisiteMode) {
    public CompletionPolicy {
        prerequisiteMode = "any".equals(prerequisiteMode) ? "any" : "all";
    }
    public CompletionPolicy(boolean events, boolean reenterStructure, boolean exportAcquiredItems,
                            boolean structureProgress, boolean automaticRewardPopup) {
        this(events, reenterStructure, exportAcquiredItems, structureProgress, automaticRewardPopup, "all");
    }
    public CompletionPolicy withPrerequisiteMode(String mode) {
        return new CompletionPolicy(events, reenterStructure, exportAcquiredItems, structureProgress, automaticRewardPopup, mode);
    }
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
