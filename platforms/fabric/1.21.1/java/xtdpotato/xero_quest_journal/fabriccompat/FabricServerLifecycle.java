package xtdpotato.xero_quest_journal.fabriccompat;

import net.minecraft.server.MinecraftServer;

public final class FabricServerLifecycle {
    private static volatile MinecraftServer server;
    private FabricServerLifecycle() {}
    public static MinecraftServer getCurrentServer() { return server; }
    public static void setServer(MinecraftServer value) { server = value; }
}
