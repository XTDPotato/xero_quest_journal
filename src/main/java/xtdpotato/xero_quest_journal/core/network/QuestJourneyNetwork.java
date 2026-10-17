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
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;

import java.util.List;

public final class QuestJourneyNetwork {
    private static final Gson GSON = new Gson();
    private QuestJourneyNetwork() {}

    public record Draft(List<String> quests, JourneyTimeline timeline, List<JourneyBackdrop> backgrounds,
                        String name, String icon, JourneyAppearance appearance) {
        public Draft(List<String> quests, JourneyTimeline timeline, List<JourneyBackdrop> backgrounds) {
            this(quests, timeline, backgrounds, null, null, null);
        }
        public Draft {
            quests = List.copyOf(quests);
            backgrounds = List.copyOf(backgrounds);
            if (quests.size() > 4096 || quests.stream().distinct().count() != quests.size()
                    || backgrounds.size() > 128 || timeline == null
                    || name != null && (name.isBlank() || name.length() > 128)
                    || icon != null && (icon.length() > 2048 || !icon.isBlank()
                    && (ResourceLocation.tryParse(icon) == null || icon.contains(".."))))
                throw new IllegalArgumentException("Invalid journey draft");
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(History.TYPE, History.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var result = payload.redo()
                    ? xtdpotato.xero_quest_journal.core.io.QuestAuthoringHistory.redo(player)
                    : xtdpotato.xero_quest_journal.core.io.QuestAuthoringHistory.undo(player);
            if (result.successful() || result.error().equals("conflict")) {
                try { QuestNetwork.replyCatalog(context, xtdpotato.xero_quest_journal.core.io.QuestRepository.revision(),
                        QuestCatalog.serverJson()); }
                catch (IllegalArgumentException ignored) { }
            }
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(),
                    result.successful() ? payload.redo() ? "journey_history_redone" : "journey_history_undone"
                            : "journey_history_" + result.error()));
        });
        registrar.playToServer(Update.TYPE, Update.CODEC, (payload, context) -> {
            String result;
            try {
                result = context.player() instanceof ServerPlayer player
                        ? QuestAuthoring.editJourney(player, payload.chapter(), payload.expectedHash(),
                                GSON.fromJson(payload.json(), Draft.class)) : "permission";
            } catch (RuntimeException invalid) { result = "invalid_config"; }
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), result));
        });
        registrar.playToServer(ChapterSwitch.TYPE, ChapterSwitch.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean exists = xtdpotato.xero_quest_journal.core.io.QuestRepository.chapters().stream()
                    .anyMatch(chapter -> chapter.id().equals(payload.chapter()));
            if (!exists) return;
            var state = SWITCHES.computeIfAbsent(player, ignored -> new SwitchState());
            long tick = player.server.getTickCount();
            if (!payload.after()) {
                if (tick - state.started < 2 || payload.chapter().equals(state.current)) return;
                state.pending = payload.chapter(); state.started = tick;
            } else {
                if (!payload.chapter().equals(state.pending)) return;
                state.current = payload.chapter(); state.pending = "";
            }
            xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs.fire(player,
                    payload.after() ? "chapter_switch_after" : "chapter_switch_before", payload.chapter());
        });
    }

    public record History(long requestId, boolean redo) implements CustomPacketPayload {
        public static final Type<History> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "journey_history"));
        public static final StreamCodec<RegistryFriendlyByteBuf, History> CODEC = new StreamCodec<>() {
            @Override public History decode(RegistryFriendlyByteBuf buffer) {
                return new History(buffer.readVarLong(), buffer.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, History value) {
                buffer.writeVarLong(value.requestId());
                buffer.writeBoolean(value.redo());
            }
        };
        @Override public Type<History> type() { return TYPE; }
    }
    private static final java.util.Map<ServerPlayer, SwitchState> SWITCHES = new java.util.WeakHashMap<>();
    private static final class SwitchState {
        String current = "", pending = "";
        long started = -100;
    }
    public record ChapterSwitch(String chapter, boolean after) implements CustomPacketPayload {
        public ChapterSwitch {
            if (chapter == null || chapter.isBlank() || chapter.length() > 128)
                throw new IllegalArgumentException("Invalid journey chapter event");
        }
        public static final Type<ChapterSwitch> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "journey_chapter_switch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChapterSwitch> CODEC = new StreamCodec<>() {
            public ChapterSwitch decode(RegistryFriendlyByteBuf b) { return new ChapterSwitch(b.readUtf(128), b.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf b, ChapterSwitch v) { b.writeUtf(v.chapter(), 128); b.writeBoolean(v.after()); }
        };
        @Override public Type<ChapterSwitch> type() { return TYPE; }
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
