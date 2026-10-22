package xtdpotato.xero_quest_journal.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.TemplateLibraryStore;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

public final class TemplateLibraryNetwork {
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Change.TYPE, Change.CODEC, (payload, context) -> {
            String result = context.player() instanceof ServerPlayer player
                    ? payload.revision() != QuestRepository.revision() ? "definition_changed"
                    : TemplateLibraryStore.change(player, payload.kind(), payload.id(), payload.target(), payload.after())
                    : "permission";
            if (result.isEmpty() || result.equals("definition_changed"))
                QuestNetwork.replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), result));
        });
    }
    public record Change(long requestId, long revision, String kind, String id, String target, boolean after)
            implements CustomPacketPayload {
        public Change {
            if (!java.util.List.of("add_group", "group", "template", "into_group").contains(kind)
                    || id == null || id.isBlank() || id.length() > 128 || target == null || target.length() > 128)
                throw new IllegalArgumentException("Invalid template library action");
        }
        public static final Type<Change> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "template_library"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Change> CODEC = new StreamCodec<>() {
            public Change decode(RegistryFriendlyByteBuf b) { return new Change(b.readVarLong(), b.readVarLong(), b.readUtf(16), b.readUtf(128), b.readUtf(128), b.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf b, Change p) { b.writeVarLong(p.requestId()); b.writeVarLong(p.revision()); b.writeUtf(p.kind(), 16); b.writeUtf(p.id(),128); b.writeUtf(p.target(),128); b.writeBoolean(p.after()); }
        };
        public Type<Change> type() { return TYPE; }
    }
}
