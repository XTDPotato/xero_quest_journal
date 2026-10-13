package xtdpotato.xero_quest_journal.fabriccompat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.forgecompat.*;
import java.util.*;
import java.util.function.BiConsumer;

public final class FabricNetwork {
    private static final Map<ResourceLocation, StreamCodec<RegistryFriendlyByteBuf, ?>> CODECS = new HashMap<>();
    private static final List<Runnable> RECEIVERS = new ArrayList<>();
    private FabricNetwork() {}
    static <T extends CustomPacketPayload> void registerCodec(CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec) { CODECS.put(type.id(), codec); }
    @SuppressWarnings("unchecked")
    static RegistryFriendlyByteBuf encode(CustomPacketPayload payload) {
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var codec = (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) CODECS.get(payload.type().id());
        if (codec == null) throw new IllegalArgumentException("Unregistered payload " + payload.type().id());
        codec.encode(buffer, payload);
        return buffer;
    }
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload.type().id(), encode(payload));
    }
    public static void sendToServer(CustomPacketPayload payload) {
        FabricClientNetwork.send(payload);
    }
    static <T extends CustomPacketPayload> void clientReceiver(CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, FabricPayloadContext> handler) {
        RECEIVERS.add(() -> FabricClientNetwork.register(type, codec, handler));
    }
    public static void registerClientReceivers() { RECEIVERS.forEach(Runnable::run); }
}
