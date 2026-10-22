package xtdpotato.xero_quest_journal.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.io.QuestGenerator;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import java.util.*;

public final class QuestGenerationNetwork {
    private static final Set<ServerPlayer> offered = Collections.newSetFromMap(new WeakHashMap<>());
    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayer() && player.server.isSingleplayerOwner(player.getGameProfile());
    }
    public static void offerFirst(ServerPlayer player) {
        if (player.tickCount < 40 || offered.contains(player) || QuestProgressData.get(player.server).generationOffered(player.getUUID())) return;
        open(player, true); offered.add(player);
    }
    public static void open(ServerPlayer player, boolean automatic) {
        PacketDistributor.sendToPlayer(player, new Offer(automatic, allowed(player), QuestGenerator.namespaces(player.server).stream().sorted().limit(512).toList()));
    }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Offer.TYPE, Offer.CODEC, (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToServer(Choose.TYPE, Choose.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            QuestProgressData.get(player.server).markGenerationOffered(player.getUUID());
            String result = "generation_dismissed";
            if (!payload.mode().equals("dismiss")) {
                if (!allowed(player)) result = "permission";
                else {
                    boolean started = QuestGenerator.start(player.server, payload.namespace(), QuestGenerator.RewardMode.RECIPE, 1,
                            QuestGenerator.GenerationMode.valueOf(payload.mode().toUpperCase(Locale.ROOT)), payload.recipes(),
                            (message, failure) -> player.server.execute(() -> player.sendSystemMessage(message)));
                    result = started ? "generation_started" : "generation_busy";
                }
            }
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), result));
        });
    }
    public record Offer(boolean automatic, boolean allowed, List<String> namespaces) implements CustomPacketPayload {
        public Offer { namespaces = List.copyOf(namespaces); if (namespaces.size() > 512) throw new IllegalArgumentException("Too many namespaces"); }
        public static final Type<Offer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "generation_offer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = new StreamCodec<>() {
            public Offer decode(RegistryFriendlyByteBuf b) {
                boolean automatic = b.readBoolean(), allowed = b.readBoolean(); int count = b.readVarInt();
                if (count < 0 || count > 512) throw new IllegalArgumentException("Invalid namespace count");
                var ids = new ArrayList<String>(); for (int i=0; i<count; i++) ids.add(b.readUtf(128));
                return new Offer(automatic, allowed, ids);
            }
            public void encode(RegistryFriendlyByteBuf b, Offer p) { b.writeBoolean(p.automatic()); b.writeBoolean(p.allowed()); b.writeVarInt(p.namespaces().size()); p.namespaces().forEach(id -> b.writeUtf(id,128)); }
        };
        public Type<Offer> type() { return TYPE; }
    }
    public record Choose(long requestId, String mode, String namespace, boolean recipes) implements CustomPacketPayload {
        public Choose {
            if (!List.of("dismiss", "progress", "structure", "both", "catalog").contains(mode)
                    || namespace == null || !namespace.matches("[a-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid generation choice");
        }
        public static final Type<Choose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "generation_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = new StreamCodec<>() {
            public Choose decode(RegistryFriendlyByteBuf b) { return new Choose(b.readVarLong(), b.readUtf(16), b.readUtf(128), b.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf b, Choose p) { b.writeVarLong(p.requestId()); b.writeUtf(p.mode(),16); b.writeUtf(p.namespace(),128); b.writeBoolean(p.recipes()); }
        };
        public Type<Choose> type() { return TYPE; }
    }
}
