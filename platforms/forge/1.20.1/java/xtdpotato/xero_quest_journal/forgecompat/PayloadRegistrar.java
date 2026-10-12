package xtdpotato.xero_quest_journal.forgecompat;

import java.util.function.BiConsumer;

/**
 * Temporary registration facade. The actual SimpleChannel transport is wired
 * after all payloads compile against the 1.20.1 API.
 */
public final class PayloadRegistrar {
    public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
                                                              StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                              BiConsumer<T, IPayloadContext> handler) {
        ForgeNetworkBridge.registerServer(type, codec, handler);
    }

    public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
                                                              StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                              BiConsumer<T, IPayloadContext> handler) {
        ForgeNetworkBridge.registerClient(type, codec, handler);
    }
}
