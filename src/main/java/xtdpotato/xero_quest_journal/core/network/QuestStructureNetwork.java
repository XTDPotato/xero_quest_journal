package xtdpotato.xero_quest_journal.core.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.util.*;

public final class QuestStructureNetwork {
    private static final Map<ServerPlayer, Long> last = new WeakHashMap<>();
    private static final Queue<Preview> incoming = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public static Preview take() { return incoming.poll(); }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (request.action == 2) { QuestStructures.leave(player); return; }
            if (request.action == 3) {
                if (!QuestStructures.canReset(player)) return;
                try { QuestStructures.reset(player); }
                catch (Exception failure) { context.reply(new Preview("", new CompoundTag(), "structure.stale")); }
                return;
            }
            if (request.action == 1 && QuestStructures.active(player)) {
                context.reply(new Preview("", new CompoundTag(), "structure.already_inside"));
                return;
            }
            if (request.action < 0 || request.action > 1 || request.revision != QuestRepository.revision()) return;
            long now = player.server.getTickCount();
            Long before = last.put(player, now);
            if (before != null && now - before < 5) return;
            String reference = "";
            try {
                var quest = QuestRepository.find(request.quest);
                if (quest == null) throw new IllegalArgumentException("structure.invalid");
                if (!QuestRuntime.unlocked(QuestProgressData.get(player.server), player, quest))
                    throw new IllegalArgumentException("structure.locked");
                var control = quest.presentation().controls().stream().filter(c -> c.id().equals(request.control))
                        .filter(c -> c.kind().equals(request.action == 1 ? "structure_enter" : "structure_preview"))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("structure.invalid"));
                if (!QuestEventGraphs.visible(player, quest, control)) throw new IllegalArgumentException("structure.locked");
                reference = control.value();
                if (request.action == 1) {
                    QuestStructures.enter(player, quest, control);
                    context.reply(new QuestNetwork.OpenScreenPayload("structure_entered", ""));
                } else {
                    context.reply(new Preview(reference, QuestStructures.preview(QuestStructures.read(reference)), ""));
                }
            } catch (Exception failure) {
                String key = failure instanceof IllegalArgumentException && failure.getMessage() != null
                        && failure.getMessage().startsWith("structure.") ? failure.getMessage() : "structure.invalid";
                context.reply(new Preview(reference, new CompoundTag(), key));
            }
        });
        registrar.playToClient(Preview.TYPE, Preview.CODEC, (payload, context) -> {
            if (incoming.size() < 32) incoming.add(payload);
        });
    }
    public record Request(String quest, String control, int action, long revision) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "structure_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            public Request decode(RegistryFriendlyByteBuf b) { return new Request(b.readUtf(256), b.readUtf(256), b.readVarInt(), b.readLong()); }
            public void encode(RegistryFriendlyByteBuf b, Request p) { b.writeUtf(p.quest, 256); b.writeUtf(p.control, 256); b.writeVarInt(p.action); b.writeLong(p.revision); }
        };
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Preview(String reference, CompoundTag data, String error) implements CustomPacketPayload {
        public static final Type<Preview> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "structure_preview"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Preview> CODEC = new StreamCodec<>() {
            public Preview decode(RegistryFriendlyByteBuf b) { return new Preview(b.readUtf(4096), b.readNbt(), b.readUtf(128)); }
            public void encode(RegistryFriendlyByteBuf b, Preview p) { b.writeUtf(p.reference, 4096); b.writeNbt(p.data); b.writeUtf(p.error, 128); }
        };
        @Override public Type<Preview> type() { return TYPE; }
    }
}
