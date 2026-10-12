package xtdpotato.xero_quest_journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared mod identity and logger. Loader entry points live under their
 * platform source sets.
 */
public final class XeroQuestJournal {
    public static final String MOD_ID = "xero_quest_journal";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private XeroQuestJournal() {
    }
}
