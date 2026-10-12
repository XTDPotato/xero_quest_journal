package xtdpotato.xero_quest_journal.platform;

import java.nio.file.Path;

/**
 * Small boundary for loader services used by the shared quest code.
 *
 * <p>Keep Minecraft types out of this interface so the same domain sources can
 * be compiled by NeoForge and Fabric adapters.</p>
 */
public interface XeroPlatform {
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String modId);
}
