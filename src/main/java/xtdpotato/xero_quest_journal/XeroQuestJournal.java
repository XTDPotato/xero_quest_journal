package xtdpotato.xero_quest_journal;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xtdpotato.xero_quest_journal.core.QuestCommand;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

@Mod(XeroQuestJournal.MOD_ID)
public final class XeroQuestJournal {
    public static final String MOD_ID = "xero_quest_journal";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public XeroQuestJournal(IEventBus modBus) {
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
