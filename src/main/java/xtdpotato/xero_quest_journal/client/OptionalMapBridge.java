package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.util.Locale;

/** Opens an installed map using its configured keybinding after the journal closes. */
final class OptionalMapBridge {
    private static Screen waitingForClose;
    private static boolean openPending;
    private static String journeySignature = "";

    private OptionalMapBridge() {}

    static boolean hasSupportedMap() {
        return ModList.get().isLoaded("xaeroworldmap") || ModList.get().isLoaded("journeymap");
    }

    static void openAfterClose(Screen closingScreen) {
        waitingForClose = closingScreen;
        openPending = true;
    }

    static void refreshWaypoints() {
        if (ModList.get().isLoaded("xaeroworldmap") || ModList.get().isLoaded("xaerominimap"))
            XaeroWaypointBridge.refresh();
        if (ModList.get().isLoaded("journeymap")) refreshJourneyMap();
    }

    static void tick() {
        if (ModList.get().isLoaded("journeymap")) {
            String signature = ClientQuestDefinitions.revision() + "|" + ClientQuestProgress.revision()
                    + "|" + String.join(",", ClientQuestProgress.trackedIds());
            if (!signature.equals(journeySignature)) {
                journeySignature = signature;
                refreshJourneyMap();
            }
        }
        if (!openPending) return;
        Minecraft game = Minecraft.getInstance();
        if (game.screen == waitingForClose) return;
        openPending = false;
        waitingForClose = null;
        if (game.player == null || game.level == null) return;

        String modId = ModList.get().isLoaded("xaeroworldmap") ? "xaero" : "journeymap";
        KeyMapping mapping = findMapBinding(game, modId);
        if (mapping == null) {
            XeroQuestJournal.LOGGER.debug("No map-opening key binding found for {}", modId);
            return;
        }
        try {
            KeyMapping.click(mapping.getKey());
        } catch (RuntimeException failure) {
            XeroQuestJournal.LOGGER.debug("Unable to open optional map {}", modId, failure);
        }
    }

    private static KeyMapping findMapBinding(Minecraft game, String modId) {
        KeyMapping best = null;
        int bestScore = Integer.MIN_VALUE;
        for (KeyMapping mapping : game.options.keyMappings) {
            String name = mapping.getName().toLowerCase(Locale.ROOT);
            boolean mapAction = name.contains("map") || name.contains("fullscreen")
                    || name.contains("full_screen");
            if (!name.contains(modId) || !mapAction) continue;
            int score = name.contains("open") ? 4 : 0;
            if (name.contains("world")) score += 3;
            if (name.contains("fullscreen") || name.contains("full_screen")) score += 2;
            if (name.contains("toggle")) score++;
            if (score > bestScore) {
                best = mapping;
                bestScore = score;
            }
        }
        return best;
    }

    private static void refreshJourneyMap() {
        try {
            Class<?> bridge = Class.forName("xtdpotato.xero_quest_journal.client.JourneyMapWaypointBridge");
            bridge.getDeclaredMethod("refresh").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            XeroQuestJournal.LOGGER.debug("JourneyMap waypoint bridge unavailable", failure);
            journeySignature = "";
        }
    }
}
