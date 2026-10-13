package xtdpotato.xero_quest_journal.fabriccompat;

import java.util.function.BiConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import xtdpotato.xero_quest_journal.forgecompat.*;

@Environment(EnvType.CLIENT)
final class FabricClientNetwork {
    private FabricClientNetwork() {}

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload.type().id(), FabricNetwork.encode(payload));
    }

    static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, FabricPayloadContext> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (game, connection, buffer, sender) -> {
            var payload = codec.decode(new RegistryFriendlyByteBuf(buffer));
            game.execute(() -> handler.accept(payload,
                    new FabricPayloadContext(game.player, FabricNetwork::sendToServer)));
        });
    }
}
