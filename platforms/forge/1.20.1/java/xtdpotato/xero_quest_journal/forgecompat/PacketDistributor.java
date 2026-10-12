package xtdpotato.xero_quest_journal.forgecompat;

import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgeNetworkBridge.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ForgeNetworkBridge.sendToServer(payload);
    }
}
