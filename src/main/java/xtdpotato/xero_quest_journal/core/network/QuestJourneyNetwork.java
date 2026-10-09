package xtdpotato.xero_quest_journal.core.network;

import com.google.gson.Gson;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;
import xtdpotato.xero_quest_journal.core.model.JourneyTimeline;

import java.util.List;

public final class QuestJourneyNetwork {
    private static final Gson GSON = new Gson();
    private QuestJourneyNetwork() {}

    public record Draft(List<String> quests, JourneyTimeline timeline, List<JourneyBackdrop> backgrounds) {
        public Draft {
            quests = List.copyOf(quests);
            backgrounds = List.copyOf(backgrounds);
            if (quests.size() > 4096 || quests.stream().distinct().count() != quests.size()
                    || backgrounds.size() > 128 || timeline == null)
                throw new IllegalArgumentException("Invalid journey draft");
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Update.TYPE, Update.CODEC, (payload, context) -> {
            String result;
            try {
                result = context.player() instanceof ServerPlayer player
                        ? QuestAuthoring.editJourney(player, payload.chapter(), payload.expectedHash(),
                                GSON.fromJson(payload.json(), Draft.class)) : "permission";
            } catch (RuntimeException invalid) { result = "invalid_config"; }
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), result));
        });
    }

    public record Update(long requestId, String chapter, String expectedHash, String json)
            implements CustomPacketPayload {
        public Update {
            if (chapter.isBlank() || chapter.length() > 128 || expectedHash.length() != 64
                    || json.length() > 200000) throw new IllegalArgumentException("Invalid journey update");
        }
        public static final Type<Update> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "journey_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = new StreamCodec<>() {
            public Update decode(RegistryFriendlyByteBuf b) {
                return new Update(b.readVarLong(), b.readUtf(128), b.readUtf(64), b.readUtf(200000));
            }
            public void encode(RegistryFriendlyByteBuf b, Update v) {
                b.writeVarLong(v.requestId()); b.writeUtf(v.chapter(), 128);
                b.writeUtf(v.expectedHash(), 64); b.writeUtf(v.json(), 200000);
            }
        };
        @Override public Type<Update> type() { return TYPE; }
    }
}
