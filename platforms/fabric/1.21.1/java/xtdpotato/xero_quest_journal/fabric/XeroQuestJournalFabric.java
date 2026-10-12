package xtdpotato.xero_quest_journal.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.platform.fabric.FabricPlatform;

/**
 * Fabric entry point reserved for the Fabric adapter. Common quest bootstrap
 * is intentionally not called until its command/network/event registrations
 * have been ported.
 */
public final class XeroQuestJournalFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("xero_quest_journal");

    @Override
    public void onInitialize() {
        PlatformServices.install(new FabricPlatform());
        LOGGER.info("Xero Quest Journal Fabric platform detected; adapter bootstrap is pending.");
    }
}
