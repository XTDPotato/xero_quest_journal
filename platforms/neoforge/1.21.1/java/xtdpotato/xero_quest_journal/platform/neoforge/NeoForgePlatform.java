package xtdpotato.xero_quest_journal.platform.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import xtdpotato.xero_quest_journal.platform.XeroPlatform;

import java.nio.file.Path;

public final class NeoForgePlatform implements XeroPlatform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
