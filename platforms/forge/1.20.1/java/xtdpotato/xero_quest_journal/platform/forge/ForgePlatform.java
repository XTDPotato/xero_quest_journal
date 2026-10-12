package xtdpotato.xero_quest_journal.platform.forge;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import xtdpotato.xero_quest_journal.platform.XeroPlatform;

import java.nio.file.Path;

public final class ForgePlatform implements XeroPlatform {
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
