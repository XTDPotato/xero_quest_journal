package xtdpotato.xero_quest_journal.client;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapWaypointBridge implements IClientPlugin {
    private static final String MOD_ID = XeroQuestJournal.MOD_ID;
    private static IClientAPI api;
    private static String lastSignature = "";

    public JourneyMapWaypointBridge() {}

    @Override public void initialize(IClientAPI clientApi) {
        api = clientApi;
        refresh();
    }

    @Override public String getModId() { return MOD_ID; }

    static void refresh() { lastSignature = ""; tick(); }

    static void tick() {
        Minecraft game = Minecraft.getInstance();
        if (api == null || game.player == null || game.level == null) return;
        String signature = ClientQuestDefinitions.revision() + "|" + ClientQuestProgress.revision()
                + "|" + String.join(",", ClientQuestProgress.trackedIds());
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        try {
            api.removeAllWaypoints(MOD_ID);
            for (String id : ClientQuestProgress.trackedIds()) {
                QuestDefinition quest = ClientQuestDefinitions.find(id);
                QuestLocation location = quest == null ? null : QuestHudRenderer.location(quest);
                if (quest == null || location == null || !location.kind().equals("coords")
                        || !location.resolved()) continue;
                Waypoint waypoint = WaypointFactory.createWaypoint(MOD_ID,
                        BlockPos.containing(location.x(), location.y(), location.z()),
                        QuestText.title(quest).getString(), location.dimension(), false);
                waypoint.setColor(0xE5B84D);
                waypoint.setShowDeviation(true);
                waypoint.setShowOnMap(true);
                waypoint.setShowLabel(true);
                ResourceLocation icon = ResourceLocation.tryParse(quest.trailingIcon().image(true));
                if (icon != null) waypoint.setIconIdentifier(icon);
                api.addWaypoint(MOD_ID, waypoint);
            }
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.debug("JourneyMap waypoint integration unavailable", failure);
            lastSignature = "";
        }
    }
}
