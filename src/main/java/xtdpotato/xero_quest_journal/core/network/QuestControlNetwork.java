package xtdpotato.xero_quest_journal.core.network;

import com.google.gson.Gson;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.util.Map;
import java.util.WeakHashMap;

public final class QuestControlNetwork {
    private static final Gson GSON = new Gson();
    private static volatile String incoming;
    private static volatile String incomingVariables;
    private static StringBuilder assembling;
    private static StringBuilder assemblingVariables;
    private static int expected, total;
    private static int expectedVariables, totalVariables;
    private static final Map<ServerPlayer, Long> lastClick = new WeakHashMap<>();
    private static final Map<ServerPlayer, String> lastState = new WeakHashMap<>();
    private static final Map<ServerPlayer, String> lastVariables = new WeakHashMap<>();
    private QuestControlNetwork() {}
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Pointer.TYPE, Pointer.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                QuestPointerEvents.input(player, payload.quest, payload.control, payload.revision, payload.event,
                        payload.x, payload.y, payload.width, payload.height);
        });
        registrar.playToServer(Click.TYPE, Click.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            long now = player.server.getTickCount();
            Long previous = lastClick.put(player, now);
            if (previous != null && now - previous < 2 || payload.revision != QuestRepository.revision()) return;
            QuestEventGraphs.click(player, payload.quest, payload.control);
        });
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> {
            if (payload.index == 0) { assembling = new StringBuilder(); expected = 0; total = payload.count; }
            if (assembling == null || expected != payload.index || total != payload.count) { assembling = null; return; }
            assembling.append(payload.json);
            if (assembling.length() > QuestCatalog.MAX_CATALOG_CHARACTERS) { assembling = null; return; }
            if (++expected == total) { incoming = assembling.toString(); assembling = null; }
        });
        registrar.playToClient(VariableState.TYPE, VariableState.CODEC, (payload, context) -> {
            if (payload.index == 0) {
                assemblingVariables = new StringBuilder();
                expectedVariables = 0;
                totalVariables = payload.count;
            }
            if (assemblingVariables == null || expectedVariables != payload.index
                    || totalVariables != payload.count) {
                assemblingVariables = null;
                return;
            }
            assemblingVariables.append(payload.json);
            if (assemblingVariables.length() > QuestCatalog.MAX_CATALOG_CHARACTERS) {
                assemblingVariables = null;
                return;
            }
            if (++expectedVariables == totalVariables) {
                incomingVariables = assemblingVariables.toString();
                assemblingVariables = null;
            }
        });
    }
    public static void send(ServerPlayer player) {
        String json = GSON.toJson(QuestControlData.get(player).snapshot(player));
        if (json.equals(lastState.get(player))) return;
        if (json.length() > QuestCatalog.MAX_CATALOG_CHARACTERS)
            throw new IllegalStateException("Control state exceeds synchronization budget");
        // Avoid splitting a UTF-16 surrogate pair between independently encoded packets.
        var parts = new java.util.ArrayList<String>();
        for (int start = 0; start < json.length();) {
            int end = Math.min(json.length(), start + 65536);
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) end--;
            parts.add(json.substring(start, end));
            start = end;
        }
        for (int i = 0; i < parts.size(); i++) PacketDistributor.sendToPlayer(player, new State(i, parts.size(), parts.get(i)));
        lastState.put(player, json);
    }
    public static void sendVariables(ServerPlayer player) {
        String json = GSON.toJson(QuestControlData.get(player).variableSnapshot(player));
        if (json.equals(lastVariables.get(player))) return;
        if (json.length() > QuestCatalog.MAX_CATALOG_CHARACTERS)
            throw new IllegalStateException("Variable state exceeds synchronization budget");
        var parts = new java.util.ArrayList<String>();
        for (int start = 0; start < json.length();) {
            int end = Math.min(json.length(), start + 65536);
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) end--;
            parts.add(json.substring(start, end));
            start = end;
        }
        for (int i = 0; i < parts.size(); i++)
            PacketDistributor.sendToPlayer(player, new VariableState(i, parts.size(), parts.get(i)));
        lastVariables.put(player, json);
    }
    public static void sendTeam(ServerPlayer player) {
        String team = xtdpotato.xero_quest_journal.core.runtime.QuestControlData.teamKey(player);
        for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
            if (xtdpotato.xero_quest_journal.core.runtime.QuestControlData.teamKey(member).equals(team)) {
                send(member);
                sendVariables(member);
            }
        }
    }
    public static String take() { String value = incoming; incoming = null; return value; }
    public static String takeVariables() { String value = incomingVariables; incomingVariables = null; return value; }
    public static void clearInbox() {
        incoming = null;
        incomingVariables = null;
        assembling = null;
        assemblingVariables = null;
        expected = total = expectedVariables = totalVariables = 0;
    }
    public record Click(String quest, String control, long revision) implements CustomPacketPayload {
        public static final Type<Click> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "control_click"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Click> CODEC = new StreamCodec<>() {
            public Click decode(RegistryFriendlyByteBuf b) { return new Click(b.readUtf(256), b.readUtf(256), b.readLong()); }
            public void encode(RegistryFriendlyByteBuf b, Click p) { b.writeUtf(p.quest, 256); b.writeUtf(p.control, 256); b.writeLong(p.revision); }
        };
        @Override public Type<Click> type() { return TYPE; }
    }
    public record Pointer(String quest, String control, long revision, String event,
                          double x, double y, double width, double height) implements CustomPacketPayload {
        public static final Type<Pointer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                "xero_quest_journal", "control_pointer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pointer> CODEC = new StreamCodec<>() {
            public Pointer decode(RegistryFriendlyByteBuf b) {
                return new Pointer(b.readUtf(256), b.readUtf(256), b.readLong(), b.readUtf(16),
                        b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble());
            }
            public void encode(RegistryFriendlyByteBuf b, Pointer p) {
                b.writeUtf(p.quest, 256); b.writeUtf(p.control, 256); b.writeLong(p.revision); b.writeUtf(p.event, 16);
                b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.width); b.writeDouble(p.height);
            }
        };
        @Override public Type<Pointer> type() { return TYPE; }
    }
    public record State(int index, int count, String json) implements CustomPacketPayload {
        public State {
            if (index < 0 || count < 1 || count > 257 || index >= count || json == null || json.length() > 65536)
                throw new IllegalArgumentException("Invalid control state chunk");
        }
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "control_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            public State decode(RegistryFriendlyByteBuf b) { return new State(b.readVarInt(), b.readVarInt(), b.readUtf(65536)); }
            public void encode(RegistryFriendlyByteBuf b, State p) { b.writeVarInt(p.index); b.writeVarInt(p.count); b.writeUtf(p.json, 65536); }
        };
        @Override public Type<State> type() { return TYPE; }
    }
    public record VariableState(int index, int count, String json) implements CustomPacketPayload {
        public VariableState {
            if (index < 0 || count < 1 || count > 257 || index >= count || json == null || json.length() > 65536)
                throw new IllegalArgumentException("Invalid variable state chunk");
        }
        public static final Type<VariableState> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "control_variables"));
        public static final StreamCodec<RegistryFriendlyByteBuf, VariableState> CODEC = new StreamCodec<>() {
            public VariableState decode(RegistryFriendlyByteBuf b) {
                return new VariableState(b.readVarInt(), b.readVarInt(), b.readUtf(65536));
            }
            public void encode(RegistryFriendlyByteBuf b, VariableState p) {
                b.writeVarInt(p.index);
                b.writeVarInt(p.count);
                b.writeUtf(p.json, 65536);
            }
        };
        @Override public Type<VariableState> type() { return TYPE; }
    }
}
