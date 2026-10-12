package xtdpotato.xero_quest_journal;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import xtdpotato.xero_quest_journal.core.QuestCommand;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.platform.neoforge.NeoForgePlatform;

@Mod(XeroQuestJournal.MOD_ID)
public final class XeroQuestJournalNeoForge {
    public XeroQuestJournalNeoForge(IEventBus modBus) {
        PlatformServices.install(new NeoForgePlatform());
        xtdpotato.xero_quest_journal.core.runtime.QuestSceneBlocks.register(modBus);
        QuestConfig.ensureDefaults();
        QuestRepository.reload();
        modBus.addListener(QuestNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        QuestCommand.register(event.getDispatcher());
    }
}
