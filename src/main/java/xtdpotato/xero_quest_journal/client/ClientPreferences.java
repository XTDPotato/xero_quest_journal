package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;

import java.nio.file.*;

public final class ClientPreferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientPreferences current;
    public boolean inventoryButton = true;
    public boolean trackingHud = true;
    public boolean reducedMotion;
    public boolean questRewardSources;
    public int popupAnimationMillis = 100;
    public int taskToastScalePercent = 33;
    public int taskToastIconScalePercent = 33;
    public PopupAnimationProfile popupAnimation = PopupAnimationProfile.defaults();
    public String defaultView = "journal";
    public String legendaryBackground = "";
    public String lastTaskTemplate = "";
    public java.util.List<String> collapsedJournalGroups = java.util.List.of();
    public int inventoryX = 6;
    public int inventoryY = 6;
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
        if (lastTaskTemplate == null || !lastTaskTemplate.matches("[A-Za-z0-9_.-]{0,64}")) lastTaskTemplate = "";
        collapsedJournalGroups = collapsedJournalGroups == null ? java.util.List.of()
                : collapsedJournalGroups.stream().filter(java.util.Objects::nonNull)
                .filter(id -> !id.isBlank() && id.length() <= 128).distinct().limit(65536).toList();
        inventoryX = Math.clamp(inventoryX, 0, 16384);
        inventoryY = Math.clamp(inventoryY, 0, 16384);
        popupAnimationMillis = Math.clamp(popupAnimationMillis <= 0 ? 100 : popupAnimationMillis, 100, 2000);
        taskToastScalePercent = Math.clamp(taskToastScalePercent <= 0 ? 33 : taskToastScalePercent, 10, 200);
        taskToastIconScalePercent = Math.clamp(
                taskToastIconScalePercent <= 0 ? 33 : taskToastIconScalePercent, 10, 200);
        try {
            popupAnimation = popupAnimation == null ? PopupAnimationProfile.defaults()
                    : new PopupAnimationProfile(popupAnimation.keyframes());
        } catch (RuntimeException invalid) {
            popupAnimation = PopupAnimationProfile.defaults();
        }
    }

    public static net.minecraft.client.gui.screens.Screen openDefault(net.minecraft.client.gui.screens.Screen parent) {
        return "overview".equals(get().defaultView) ? QuestScreen.overview(parent) : new QuestScreen(parent);
    }

    private static Path file() {
        return PlatformServices.configDir().resolve("xero_quest/ui/client.json");
    }
}
