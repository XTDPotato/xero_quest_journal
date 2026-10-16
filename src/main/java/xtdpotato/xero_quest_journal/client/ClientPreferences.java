package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;

import java.nio.file.*;
import java.util.Set;
import java.util.Map;

public final class ClientPreferences {
    public static final int DEFAULT_POPUP_ANIMATION_MILLIS = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientPreferences current;
    public boolean inventoryButton = true;
    public Boolean mailboxInventoryButton;
    public InventoryEntryPosition taskInventoryPosition, mailInventoryPosition;
    public boolean trackingHud = true;
    public int trackingMarkerScalePercent = 50;
    public boolean reducedMotion;
    public boolean questRewardSources;
    public int popupAnimationMillis = DEFAULT_POPUP_ANIMATION_MILLIS;
    public int taskToastScalePercent = 33;
    public int taskToastIconScalePercent = 33;
    public PopupAnimationProfile popupAnimation = PopupAnimationProfile.defaults();
    public String defaultView = "journal";
    public String legendaryBackground = "";
    public String legendaryBackgroundType = "image";
    public String legendaryBackgroundMode = "cover";
    public double legendaryBackgroundScale = 1.0;
    public int legendaryBackgroundOffsetX, legendaryBackgroundOffsetY;
    public String legendaryBackgroundMusic = "";
    public String lastTaskTemplate = "";
    public String lastSelectedQuestId = "";
    public String lastSelectedQuestCategory = "active";
    public java.util.List<String> collapsedJournalGroups = java.util.List.of();
    public int inventoryX = 6;
    public int inventoryY = 6;
    public boolean fixedQuestContextMenuPosition;
    public int questContextMenuXPercent = 50;
    public int questContextMenuYPercent = 25;
    public Set<String> questSearchScopes = Set.of("title", "description");
    public Map<String, Set<String>> questSearchFilters = Map.of();
    public MailComposeScreen.DraftState mailDraft;

    public static ClientPreferences get() {
        if (current == null) {
            try {
                current = Files.exists(file()) ? GSON.fromJson(Files.readString(file()), ClientPreferences.class) : null;
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to load client preferences", exception);
            }
            if (current == null) {
                current = new ClientPreferences();
            }
            current.validate();
        }
        return current;
    }

    public boolean save() {
        try {
            validate();
            AtomicFiles.write(file(), GSON.toJson(this), true);
            return true;
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to save client preferences", exception);
            return false;
        }
    }

    private void validate() {
        if (!"overview".equals(defaultView)) defaultView = "journal";
        if (legendaryBackground == null || legendaryBackground.length() > 4096) legendaryBackground = "";
        if (legendaryBackgroundType == null || !Set.of("image", "video").contains(legendaryBackgroundType))
            legendaryBackgroundType = "image";
        if (legendaryBackgroundMode == null || !Set.of("cover", "contain", "stretch", "adaptive").contains(legendaryBackgroundMode))
            legendaryBackgroundMode = "cover";
        if (!Double.isFinite(legendaryBackgroundScale)) legendaryBackgroundScale = 1;
        legendaryBackgroundScale = Math.clamp(legendaryBackgroundScale, .1, 10);
        legendaryBackgroundOffsetX = Math.clamp(legendaryBackgroundOffsetX, -10000, 10000);
        legendaryBackgroundOffsetY = Math.clamp(legendaryBackgroundOffsetY, -10000, 10000);
        if (legendaryBackgroundMusic == null || legendaryBackgroundMusic.length() > 4096)
            legendaryBackgroundMusic = "";
        if (lastTaskTemplate == null || !lastTaskTemplate.matches("[A-Za-z0-9_.-]{0,64}")) lastTaskTemplate = "";
        if (lastSelectedQuestId == null || lastSelectedQuestId.length() > 128
                || !lastSelectedQuestId.matches("[A-Za-z0-9_.-]*")) lastSelectedQuestId = "";
        if (lastSelectedQuestCategory == null || lastSelectedQuestCategory.length() > 64
                || !lastSelectedQuestCategory.matches("[A-Za-z0-9_.-]+"))
            lastSelectedQuestCategory = "active";
        collapsedJournalGroups = collapsedJournalGroups == null ? java.util.List.of()
                : collapsedJournalGroups.stream().filter(java.util.Objects::nonNull)
                .filter(id -> !id.isBlank() && id.length() <= 128).distinct().limit(65536).toList();
        inventoryX = Math.clamp(inventoryX, 0, 16384);
        inventoryY = Math.clamp(inventoryY, 0, 16384);
        questContextMenuXPercent = Math.clamp(questContextMenuXPercent, 0, 100);
        questContextMenuYPercent = Math.clamp(questContextMenuYPercent, 0, 100);
        Set<String> validSearchScopes = Set.of("title", "description", "subtitle", "internal");
        questSearchScopes = questSearchScopes == null ? Set.of("title", "description")
                : questSearchScopes.stream().filter(validSearchScopes::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        questSearchFilters = QuestSearchFilters.sanitize(questSearchFilters);
        if (mailboxInventoryButton == null) mailboxInventoryButton = inventoryButton;
        taskInventoryPosition = taskInventoryPosition == null ? new InventoryEntryPosition("top_left", inventoryX, inventoryY)
                : new InventoryEntryPosition(taskInventoryPosition.anchor(), taskInventoryPosition.x(), taskInventoryPosition.y());
        mailInventoryPosition = mailInventoryPosition == null ? new InventoryEntryPosition("top_left", inventoryX + 24, inventoryY)
                : new InventoryEntryPosition(mailInventoryPosition.anchor(), mailInventoryPosition.x(), mailInventoryPosition.y());
        popupAnimationMillis = Math.clamp(popupAnimationMillis <= 0 ? DEFAULT_POPUP_ANIMATION_MILLIS
                : popupAnimationMillis, DEFAULT_POPUP_ANIMATION_MILLIS, 2000);
        taskToastScalePercent = Math.clamp(taskToastScalePercent <= 0 ? 33 : taskToastScalePercent, 10, 200);
        taskToastIconScalePercent = Math.clamp(
                taskToastIconScalePercent <= 0 ? 33 : taskToastIconScalePercent, 10, 200);
        trackingMarkerScalePercent = Math.clamp(
                trackingMarkerScalePercent <= 0 ? 50 : trackingMarkerScalePercent, 25, 200);
        try {
            popupAnimation = popupAnimation == null ? PopupAnimationProfile.defaults()
                    : new PopupAnimationProfile(popupAnimation.keyframes());
            var oldDefault = new PopupAnimationProfile(java.util.List.of(
                    new PopupAnimationProfile.Keyframe(0, .88, 0, 8, -2, 0, "smooth"),
                    new PopupAnimationProfile.Keyframe(1, 1, 0, 0, 0, 1, "smooth")));
            if (popupAnimation.keyframes().equals(oldDefault.keyframes()))
                popupAnimation = PopupAnimationProfile.defaults();
        } catch (RuntimeException invalid) {
            popupAnimation = PopupAnimationProfile.defaults();
        }
    }

    public static net.minecraft.client.gui.screens.Screen openDefault(net.minecraft.client.gui.screens.Screen parent) {
        return "overview".equals(get().defaultView) ? QuestScreen.overview(parent) : QuestScreen.resume(parent);
    }

    private static Path file() {
        return PlatformServices.configDir().resolve("xero_quest/ui/client.json");
    }
}
