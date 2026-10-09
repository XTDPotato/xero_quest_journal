package xtdpotato.xero_quest_journal.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.io.QuestJournalOrderStore;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

public final class QuestJournalOrderNetwork {
    private QuestJournalOrderNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(TaskBlockMove.TYPE, TaskBlockMove.CODEC, (payload, context) -> {
            String result = context.player() instanceof ServerPlayer player
                    ? QuestJournalOrderStore.moveTasks(player, payload.revision(), payload.taskIds(),
                            payload.neighbor(), payload.after()) : "permission";
            if (result.equals("journal_order_saved") || result.equals("definition_changed")) {
                QuestNetwork.replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
            }
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), result));
        });
        registrar.playToServer(Move.TYPE, Move.CODEC, (payload, context) -> {
            String result = context.player() instanceof ServerPlayer player
                    ? QuestJournalOrderStore.move(player, payload.revision(), payload.kind(),
                            payload.id(), payload.neighbor(), payload.direction()) : "permission";
            if (result.equals("journal_order_saved") || result.equals("definition_changed")) {
                QuestNetwork.replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
            }
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), result));
        });
    }

    public record Move(long requestId, long revision, String kind, String id, String neighbor, int direction)
            implements CustomPacketPayload {
        public Move {
            if (!java.util.List.of("task", "tab").contains(kind) || id == null || neighbor == null
                    || id.isBlank() || neighbor.isBlank() || id.length() > 128 || neighbor.length() > 128
                    || id.equals(neighbor) || Math.abs(direction) != 1)
                throw new IllegalArgumentException("Invalid journal order move");
        }
        public static final Type<Move> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "journal_order_move"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Move> CODEC = new StreamCodec<>() {
            @Override public Move decode(RegistryFriendlyByteBuf b) {
                return new Move(b.readVarLong(), b.readVarLong(), b.readUtf(4), b.readUtf(128),
                        b.readUtf(128), b.readByte());
            }
            @Override public void encode(RegistryFriendlyByteBuf b, Move value) {
                b.writeVarLong(value.requestId()); b.writeVarLong(value.revision()); b.writeUtf(value.kind(), 4);
                b.writeUtf(value.id(), 128); b.writeUtf(value.neighbor(), 128); b.writeByte(value.direction());
            }
        };
        @Override public Type<Move> type() { return TYPE; }
    }

    public record TaskBlockMove(long requestId, long revision, java.util.List<String> taskIds,
                                String neighbor, boolean after) implements CustomPacketPayload {
        private static final int MAX_TASKS = 512;
        public TaskBlockMove {
            taskIds = taskIds == null ? java.util.List.of() : java.util.List.copyOf(taskIds);
            if (taskIds.isEmpty() || taskIds.size() > MAX_TASKS
                    || new java.util.HashSet<>(taskIds).size() != taskIds.size()
                    || taskIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 128)
                    || neighbor == null || neighbor.isBlank() || neighbor.length() > 128
                    || taskIds.contains(neighbor))
                throw new IllegalArgumentException("Invalid journal task block move");
        }
        public static final Type<TaskBlockMove> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "journal_order_move_tasks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TaskBlockMove> CODEC = new StreamCodec<>() {
            @Override public TaskBlockMove decode(RegistryFriendlyByteBuf buffer) {
                long requestId = buffer.readVarLong(), revision = buffer.readVarLong();
                int size = buffer.readVarInt();
                if (size < 1 || size > MAX_TASKS) throw new IllegalArgumentException("Invalid task block size");
                java.util.List<String> ids = new java.util.ArrayList<>(size);
                for (int index = 0; index < size; index++) ids.add(buffer.readUtf(128));
                return new TaskBlockMove(requestId, revision, ids, buffer.readUtf(128), buffer.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, TaskBlockMove payload) {
                buffer.writeVarLong(payload.requestId());
                buffer.writeVarLong(payload.revision());
                buffer.writeVarInt(payload.taskIds().size());
                payload.taskIds().forEach(id -> buffer.writeUtf(id, 128));
                buffer.writeUtf(payload.neighbor(), 128);
                buffer.writeBoolean(payload.after());
            }
        };
        @Override public Type<TaskBlockMove> type() { return TYPE; }
    }
}
