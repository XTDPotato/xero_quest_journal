package xtdpotato.xero_quest_journal.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestDaily;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Per-player daily commission state; sent only when it changes. */
public final class QuestDailyNetwork {
    private static final Map<ServerPlayer, String> lastSent = new WeakHashMap<>();
    private QuestDailyNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> QuestClientInbox.accept(payload));
    }

    public static void send(ServerPlayer player) {
        var data = QuestProgressData.get(player.getServer());
        var roll = data.daily(player.getUUID());
        boolean enabled = QuestDaily.enabled();
        List<String> quests = enabled && roll != null ? roll.quests() : List.of();
        boolean claimed = roll != null && roll.bonusClaimed();
        List<QuestDefinition.Reward> bonus = enabled ? QuestConfig.defaults().dailyBonusRewards.stream()
                .map(QuestDefinition.Reward::display).limit(32).toList() : List.of();
        long now = System.currentTimeMillis();
        long reset = QuestDaily.nextReset(now);
        String key = enabled + "|" + (roll == null ? "" : roll.day()) + "|" + quests + "|" + claimed + "|" + bonus
                + "|" + reset;
        if (key.equals(lastSent.get(player))) return;
        PacketDistributor.sendToPlayer(player, new State(enabled, quests, claimed, bonus, reset, now));
        lastSent.put(player, key);
    }

    /** {@code resetAt} and {@code serverNow} are epoch milliseconds measured on the server clock. */
    public record State(boolean enabled, List<String> quests, boolean bonusClaimed,
                        List<QuestDefinition.Reward> bonus, long resetAt, long serverNow) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "daily_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            @Override
            public State decode(RegistryFriendlyByteBuf buffer) {
                boolean enabled = buffer.readBoolean();
                int size = buffer.readVarInt();
                if (size < 0 || size > 64) throw new IllegalArgumentException("Invalid daily commission count");
                List<String> quests = new ArrayList<>(size);
                for (int i = 0; i < size; i++) quests.add(buffer.readUtf(256));
                boolean claimed = buffer.readBoolean();
                int rewards = buffer.readVarInt();
                if (rewards < 0 || rewards > 32) throw new IllegalArgumentException("Invalid daily bonus size");
                List<QuestDefinition.Reward> bonus = new ArrayList<>(rewards);
                for (int i = 0; i < rewards; i++)
                    bonus.add(new QuestDefinition.Reward(buffer.readUtf(32), buffer.readUtf(8192),
                            buffer.readVarInt(), buffer.readUtf(16), buffer.readUtf(7)));
                return new State(enabled, quests, claimed, bonus, buffer.readLong(), buffer.readLong());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, State value) {
                buffer.writeBoolean(value.enabled());
                buffer.writeVarInt(value.quests().size());
                value.quests().forEach(id -> buffer.writeUtf(id, 256));
                buffer.writeBoolean(value.bonusClaimed());
                buffer.writeVarInt(value.bonus().size());
                for (var reward : value.bonus()) {
                    buffer.writeUtf(reward.type(), 32);
                    buffer.writeUtf(reward.item(), 8192);
                    buffer.writeVarInt(reward.count());
                    buffer.writeUtf(reward.quality(), 16);
                    buffer.writeUtf(reward.background(), 7);
                }
                buffer.writeLong(value.resetAt());
                buffer.writeLong(value.serverNow());
            }
        };

        public State {
            quests = quests == null ? List.of() : List.copyOf(quests);
            bonus = bonus == null ? List.of() : List.copyOf(bonus);
            if (quests.size() > 64 || bonus.size() > 32) throw new IllegalArgumentException("Daily state too large");
        }

        public static State empty() {
            return new State(false, List.of(), false, List.of(), 0, 0);
        }

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }
}
