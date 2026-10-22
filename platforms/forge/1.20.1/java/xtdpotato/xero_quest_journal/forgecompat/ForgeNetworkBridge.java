package xtdpotato.xero_quest_journal.forgecompat;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class ForgeNetworkBridge {
    private static final String PROTOCOL = "13";
    private static final ResourceLocation CHANNEL_ID =
            new ResourceLocation("xero_quest_journal", "main");
    private static final Map<ResourceLocation, Registration<?>> SERVERBOUND = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Registration<?>> CLIENTBOUND = new ConcurrentHashMap<>();
    private static volatile SimpleChannel channel;

    private ForgeNetworkBridge() {
    }

    public static void initialize() {
        if (channel != null) return;
        synchronized (ForgeNetworkBridge.class) {
            if (channel != null) return;
            channel = NetworkRegistry.newSimpleChannel(CHANNEL_ID, () -> PROTOCOL,
                    PROTOCOL::equals, PROTOCOL::equals);
            channel.registerMessage(0, WireMessage.class, ForgeNetworkBridge::encode,
                    ForgeNetworkBridge::decode, ForgeNetworkBridge::handle);
        }
    }

    public static <T extends CustomPacketPayload> void registerServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        initialize();
        SERVERBOUND.put(type.id(), new Registration<>(codec, handler));
    }

    public static <T extends CustomPacketPayload> void registerClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        initialize();
        CLIENTBOUND.put(type.id(), new Registration<>(codec, handler));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        initialize();
        channel.send(PacketDistributor.PLAYER.with(() -> player), wire(payload, CLIENTBOUND));
    }

    public static void sendToServer(CustomPacketPayload payload) {
        initialize();
        channel.sendToServer(wire(payload, SERVERBOUND));
    }

    private static WireMessage wire(CustomPacketPayload payload, Map<ResourceLocation, Registration<?>> registrations) {
        @SuppressWarnings("unchecked")
        Registration<CustomPacketPayload> registration = (Registration<CustomPacketPayload>)
                registrations.get(payload.type().id());
        if (registration == null) throw new IllegalStateException("Unregistered payload: " + payload.type().id());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            registration.codec.encode(buffer, payload);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new WireMessage(payload.type().id(), bytes);
        } finally {
            buffer.release();
        }
    }

    private static void encode(WireMessage message, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(message.id());
        buffer.writeByteArray(message.data());
    }

    private static WireMessage decode(FriendlyByteBuf buffer) {
        return new WireMessage(buffer.readResourceLocation(), buffer.readByteArray(1_000_000));
    }

    private static void handle(WireMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        boolean serverbound = context.getDirection().getReceptionSide().isServer();
        Registration<?> registration = (serverbound ? SERVERBOUND : CLIENTBOUND).get(message.id());
        if (registration == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(message.data()));
            try {
                IPayloadContext payloadContext = new ForgePayloadContext(context);
                registration.dispatch(buffer, payloadContext);
            } finally {
                buffer.release();
            }
        });
        context.setPacketHandled(true);
    }

    private record WireMessage(ResourceLocation id, byte[] data) {
    }

    private record Registration<T extends CustomPacketPayload>(
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        void dispatch(RegistryFriendlyByteBuf buffer, IPayloadContext context) {
            T payload = codec.decode(buffer);
            handler.accept(payload, context);
        }
    }

    private static final class ForgePayloadContext implements IPayloadContext {
        private final NetworkEvent.Context context;

        private ForgePayloadContext(NetworkEvent.Context context) {
            this.context = context;
        }

        @Override
        public ServerPlayer player() {
            return context.getSender();
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            ServerPlayer sender = context.getSender();
            if (sender != null) sendToPlayer(sender, payload);
            else sendToServer(payload);
        }
    }
}
