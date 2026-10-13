package xtdpotato.xero_quest_journal.fabriccompat;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.function.BiConsumer;

public final class FabricPayloadRegistrar {
    public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, FabricPayloadContext> handler) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                handler.accept(payload, new FabricPayloadContext(context.player(), context.responseSender()::sendPacket)));
    }

    public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, FabricPayloadContext> handler) {
        PayloadTypeRegistry.playS2C().register(type, codec);
        FabricNetwork.clientReceiver(type, handler);
    }
}
