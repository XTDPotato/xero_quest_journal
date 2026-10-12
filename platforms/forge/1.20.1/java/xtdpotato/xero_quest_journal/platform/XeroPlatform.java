package xtdpotato.xero_quest_journal.platform;

import java.nio.file.Path;

public interface XeroPlatform {
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String modId);
}
