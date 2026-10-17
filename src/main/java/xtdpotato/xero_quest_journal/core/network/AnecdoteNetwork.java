package xtdpotato.xero_quest_journal.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.AnecdoteStore;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.core.runtime.AnecdoteRuntime;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;

/** World definitions are server-owned; only editors with permission level two may change them. */
public final class AnecdoteNetwork {
    private static final int MAX_JSON = 1_000_000;
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private AnecdoteNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE, Request.CODEC, AnecdoteNetwork::request);
        registrar.playToServer(Save.TYPE, Save.CODEC, AnecdoteNetwork::save);
        registrar.playToServer(Delete.TYPE, Delete.CODEC, AnecdoteNetwork::delete);
        registrar.playToServer(MarkRead.TYPE, MarkRead.CODEC, AnecdoteNetwork::markRead);
        registrar.playToServer(MarkRewardsRead.TYPE, MarkRewardsRead.CODEC, AnecdoteNetwork::markRewardsRead);
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(Result.TYPE, Result.CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
    }

    public static void request(Request payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) sendSnapshot(player);
    }

    private static void save(Save payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) {
            context.reply(new Result("permission"));
            return;
        }
        try {
            AnecdoteDefinition entry = GSON.fromJson(payload.json(), AnecdoteDefinition.class);
            AnecdoteStore.save(entry);
            context.reply(new Result("saved"));
            sendSnapshot(player);
        } catch (Exception invalid) {
            XeroQuestJournal.LOGGER.warn("Unable to save anecdote entry", invalid);
            context.reply(new Result("invalid"));
        }
    }

    private static void delete(Delete payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) {
            context.reply(new Result("permission"));
            return;
        }
        try {
            boolean removed = AnecdoteStore.delete(payload.id());
            if (removed) QuestProgressData.get(player.getServer()).forgetAnecdote(payload.id());
            context.reply(new Result(removed ? "deleted" : "missing"));
            sendSnapshot(player);
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.warn("Unable to delete anecdote {}", payload.id(), failure);
            context.reply(new Result("invalid"));
        }
    }

    private static void markRead(MarkRead payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && AnecdoteStore.find(payload.id()) != null)
            AnecdoteRuntime.markRead(player, payload.id());
    }

    private static void markRewardsRead(MarkRewardsRead payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) AnecdoteRuntime.markRewardsRead(player);
    }

    public static void sendSnapshot(ServerPlayer player) {
        try {
            String json = GSON.toJson(AnecdoteRuntime.snapshot(player));
            PacketDistributor.sendToPlayer(player, new Snapshot(json));
        } catch (RuntimeException invalid) {
            XeroQuestJournal.LOGGER.warn("Unable to send anecdote snapshot", invalid);
            PacketDistributor.sendToPlayer(player, new Result("snapshot_error"));
        }
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = AnecdoteNetwork.payloadType("anecdote_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = unit(Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Save(String json) implements CustomPacketPayload {
        public static final Type<Save> TYPE = AnecdoteNetwork.payloadType("anecdote_save");
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC = new StreamCodec<>() {
            @Override public Save decode(RegistryFriendlyByteBuf buffer) { return new Save(buffer.readUtf(MAX_JSON)); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Save value) { buffer.writeUtf(value.json(), MAX_JSON); }
        };
        public Save {
            json = json == null ? "" : json;
            if (json.length() > MAX_JSON) throw new IllegalArgumentException("Anecdote edit is too large");
        }
        @Override public Type<Save> type() { return TYPE; }
    }

    public record Delete(String id) implements CustomPacketPayload {
        public static final Type<Delete> TYPE = AnecdoteNetwork.payloadType("anecdote_delete");
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> CODEC = new StreamCodec<>() {
            @Override public Delete decode(RegistryFriendlyByteBuf buffer) { return new Delete(buffer.readUtf(64)); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Delete value) { buffer.writeUtf(value.id(), 64); }
        };
        public Delete {
            id = id == null ? "" : id;
            if (id.length() > 64) throw new IllegalArgumentException("Invalid anecdote ID");
        }
        @Override public Type<Delete> type() { return TYPE; }
    }

    public record MarkRead(String id) implements CustomPacketPayload {
        public static final Type<MarkRead> TYPE = AnecdoteNetwork.payloadType("anecdote_read");
        public static final StreamCodec<RegistryFriendlyByteBuf, MarkRead> CODEC = new StreamCodec<>() {
            @Override public MarkRead decode(RegistryFriendlyByteBuf buffer) { return new MarkRead(buffer.readUtf(64)); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, MarkRead value) { buffer.writeUtf(value.id(), 64); }
        };
        @Override public Type<MarkRead> type() { return TYPE; }
    }

    public record MarkRewardsRead() implements CustomPacketPayload {
        public static final Type<MarkRewardsRead> TYPE = AnecdoteNetwork.payloadType("anecdote_rewards_read");
        public static final StreamCodec<RegistryFriendlyByteBuf, MarkRewardsRead> CODEC =
                AnecdoteNetwork.unit(MarkRewardsRead::new);
        @Override public Type<MarkRewardsRead> type() { return TYPE; }
    }

    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = AnecdoteNetwork.payloadType("anecdote_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            @Override public Snapshot decode(RegistryFriendlyByteBuf buffer) { return new Snapshot(buffer.readUtf(MAX_JSON)); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Snapshot value) { buffer.writeUtf(value.json(), MAX_JSON); }
        };
        public Snapshot {
            json = json == null ? "" : json;
            if (json.length() > MAX_JSON) throw new IllegalArgumentException("Anecdote snapshot is too large");
        }
        @Override public Type<Snapshot> type() { return TYPE; }
    }

    public record Result(String value) implements CustomPacketPayload {
        public static final Type<Result> TYPE = AnecdoteNetwork.payloadType("anecdote_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = new StreamCodec<>() {
            @Override public Result decode(RegistryFriendlyByteBuf buffer) { return new Result(buffer.readUtf(64)); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Result value) { buffer.writeUtf(value.value(), 64); }
        };
        public Result { value = value == null ? "" : value; }
        @Override public Type<Result> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, path);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(id(path));
    }

    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> unit(
            java.util.function.Supplier<T> factory) {
        return new StreamCodec<>() {
            @Override public T decode(RegistryFriendlyByteBuf buffer) { return factory.get(); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, T value) {}
        };
    }
}
