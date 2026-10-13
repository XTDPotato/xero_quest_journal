package xtdpotato.xero_quest_journal.fabriccompat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import xtdpotato.xero_quest_journal.forgecompat.*;
import java.util.function.BiConsumer;

public final class FabricPayloadRegistrar {
    public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, FabricPayloadContext> handler) {
        FabricNetwork.registerCodec(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, listener, buffer, sender) -> {
            var payload = codec.decode(new RegistryFriendlyByteBuf(buffer));
            server.execute(() -> handler.accept(payload, new FabricPayloadContext(player,
                    reply -> FabricNetwork.sendToPlayer(player, reply))));
        });
    }
    public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, FabricPayloadContext> handler) {
        FabricNetwork.registerCodec(type, codec);
        FabricNetwork.clientReceiver(type, codec, handler);
    }
}
