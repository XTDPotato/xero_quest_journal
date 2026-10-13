package xtdpotato.xero_quest_journal.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.platform.fabric.FabricPlatform;

public final class XeroQuestJournalFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("xero_quest_journal");

    @Override
    public void onInitialize() {
        PlatformServices.install(new FabricPlatform());
        xtdpotato.xero_quest_journal.core.runtime.QuestSceneBlocks.register(
                xtdpotato.xero_quest_journal.fabriccompat.FabricEventBus.EVENT_BUS);
        xtdpotato.xero_quest_journal.core.QuestConfig.ensureDefaults();
        xtdpotato.xero_quest_journal.core.io.QuestRepository.reload();
        xtdpotato.xero_quest_journal.core.network.QuestNetwork.register(
                new xtdpotato.xero_quest_journal.fabriccompat.FabricRegisterPayloads());
        FabricGameEvents.register();
        LOGGER.info("Xero Quest Journal Fabric runtime initialized.");
    }
}
