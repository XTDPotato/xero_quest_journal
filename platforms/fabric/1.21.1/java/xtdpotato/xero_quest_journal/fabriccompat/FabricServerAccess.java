package xtdpotato.xero_quest_journal.fabriccompat;

import net.minecraft.server.MinecraftServer;

public final class FabricServerAccess {
    private FabricServerAccess() {}
    public static Object resources(MinecraftServer server) { return server.resources.managers(); }
}
