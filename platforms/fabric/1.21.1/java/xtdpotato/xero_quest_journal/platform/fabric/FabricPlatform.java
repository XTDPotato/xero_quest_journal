package xtdpotato.xero_quest_journal.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;
import xtdpotato.xero_quest_journal.platform.XeroPlatform;

import java.nio.file.Path;

public final class FabricPlatform implements XeroPlatform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
