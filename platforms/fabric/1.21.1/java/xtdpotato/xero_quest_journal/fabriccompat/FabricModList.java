package xtdpotato.xero_quest_journal.fabriccompat;

import net.fabricmc.loader.api.FabricLoader;

public final class FabricModList {
    private static final FabricModList INSTANCE = new FabricModList();
    public static FabricModList get() { return INSTANCE; }
    public boolean isLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    public java.util.Optional<net.fabricmc.loader.api.ModContainer> getModContainerById(String id) {
        return FabricLoader.getInstance().getModContainer(id);
    }
}
