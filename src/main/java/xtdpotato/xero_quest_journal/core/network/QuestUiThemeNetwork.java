package xtdpotato.xero_quest_journal.core.network;

import com.google.gson.Gson;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.model.QuestUiTheme;

import java.util.Map;
import java.util.WeakHashMap;

/** Bounded, server-owned synchronization for persistent journal defaults. */
public final class QuestUiThemeNetwork {
    private static final Gson GSON = new Gson();
    private static final Map<ServerPlayer, String> lastSent = new WeakHashMap<>();
    private QuestUiThemeNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Update.TYPE, Update.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), "permission"));
                return;
            }
            try {
                QuestUiTheme parsed = GSON.fromJson(payload.json(), QuestUiTheme.class);
                QuestUiTheme theme = new QuestUiTheme(parsed.colors(), parsed.overviewImage(), parsed.journeyAppearance());
                context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(),
                        apply(player, theme) ? "config_saved" : "config_save_failed"));
            } catch (RuntimeException invalid) {
                context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), "invalid_config"));
            }
        });
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> QuestClientInbox.accept(payload));
    }

    public static boolean apply(ServerPlayer author, QuestUiTheme theme) {
        if (!author.server.isSameThread()) return false;
        QuestUiTheme old = QuestConfig.defaults().uiTheme;
        if (old.equals(theme)) return true;
        QuestConfig.defaults().uiTheme = theme;
        if (!QuestConfig.save()) {
            QuestConfig.defaults().uiTheme = old;
            return false;
        }
        author.server.getPlayerList().getPlayers().forEach(QuestUiThemeNetwork::send);
        return true;
    }

    public static void send(ServerPlayer player) {
        String json = GSON.toJson(QuestConfig.defaults().uiTheme);
        String image = xtdpotato.xero_quest_journal.core.runtime.QuestControlData.get(player).overviewImage(player);
        String key = json + "\n" + image;
        if (key.equals(lastSent.get(player))) return;
        PacketDistributor.sendToPlayer(player, new State(json, image));
        lastSent.put(player, key);
    }

    public record Update(long requestId, String json) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "ui_theme_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = new StreamCodec<>() {
            public Update decode(RegistryFriendlyByteBuf b) { return new Update(b.readVarLong(), b.readUtf(32768)); }
            public void encode(RegistryFriendlyByteBuf b, Update value) {
                b.writeVarLong(value.requestId()); b.writeUtf(value.json(), 32768);
            }
        };
        @Override public Type<Update> type() { return TYPE; }
    }

    public record State(String json, String overviewImage) implements CustomPacketPayload {
        public State(String json) { this(json, ""); }
        public static final Type<State> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "ui_theme_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            public State decode(RegistryFriendlyByteBuf b) { return new State(b.readUtf(32768), b.readUtf(2048)); }
            public void encode(RegistryFriendlyByteBuf b, State value) {
                b.writeUtf(value.json(), 32768); b.writeUtf(value.overviewImage(), 2048);
            }
        };
        @Override public Type<State> type() { return TYPE; }
    }
}
