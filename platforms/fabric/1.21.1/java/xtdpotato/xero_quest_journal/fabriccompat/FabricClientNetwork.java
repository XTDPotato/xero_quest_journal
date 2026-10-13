package xtdpotato.xero_quest_journal.fabriccompat;

import java.util.function.BiConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@Environment(EnvType.CLIENT)
final class FabricClientNetwork {
    private FabricClientNetwork() {}

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    static <T extends CustomPacketPayload> void register(
            CustomPacketPayload.Type<T> type, BiConsumer<T, FabricPayloadContext> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                handler.accept(payload, new FabricPayloadContext(context.player(), context.responseSender()::sendPacket)));
    }
}
