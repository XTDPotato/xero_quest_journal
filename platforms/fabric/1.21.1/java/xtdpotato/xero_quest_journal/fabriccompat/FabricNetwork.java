package xtdpotato.xero_quest_journal.fabriccompat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class FabricNetwork {
    private static final List<Runnable> CLIENT_RECEIVERS = new ArrayList<>();
    private FabricNetwork() {}
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
    public static void sendToServer(CustomPacketPayload payload) { FabricClientNetwork.send(payload); }
    static <T extends CustomPacketPayload> void clientReceiver(CustomPacketPayload.Type<T> type,
            BiConsumer<T, FabricPayloadContext> handler) {
        CLIENT_RECEIVERS.add(() -> FabricClientNetwork.register(type, handler));
    }
    public static void registerClientReceivers() { CLIENT_RECEIVERS.forEach(Runnable::run); }
}
