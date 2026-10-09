package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Daily commissions: tasks flagged {@code daily} form a pool. Every player receives a stable,
 * per-day selection of {@code dailyCommissionCount} tasks at {@code dailyResetHour} (server local
 * time). Selected tasks start a fresh round each day; completing all of them unlocks one bonus.
 */
public final class QuestDaily {
    private QuestDaily() {}

    public static boolean enabled() {
        return QuestConfig.defaults().dailyCommissionCount > 0;
    }

    public static long today() {
        return day(System.currentTimeMillis());
    }

    static long day(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
                .minusHours(QuestConfig.defaults().dailyResetHour).toLocalDate().toEpochDay();
    }

    /** The next reset instant, in epoch milliseconds. */
    public static long nextReset(long epochMillis) {
        var zone = ZoneId.systemDefault();
        var now = Instant.ofEpochMilli(epochMillis).atZone(zone);
        var reset = now.toLocalDate().atTime(QuestConfig.defaults().dailyResetHour, 0).atZone(zone);
        if (!reset.isAfter(now)) reset = reset.plusDays(1);
        return reset.toInstant().toEpochMilli();
    }

    /**
     * Ordinary tasks are always available. A daily task is available when it belongs to the
     * player's current roll, or while it still holds an unclaimed reward from an earlier day.
     */
    public static boolean available(QuestProgressData data, UUID player, QuestDefinition quest) {
        if (!quest.daily() || !enabled()) return true;
        var roll = data.daily(player);
        if (roll != null && roll.quests().contains(quest.id())) return true;
        var entry = data.entry(player, quest);
        return entry != null && !entry.claimed();
    }

    public static List<String> todays(QuestProgressData data, UUID player) {
        var roll = data.daily(player);
        return roll == null ? List.of() : roll.quests();
    }

    /** Rolls a new day when needed. Returns true when the selection changed. */
    public static boolean refresh(ServerPlayer player) {
        if (!enabled()) return false;
        var data = QuestProgressData.get(player.getServer());
        UUID id = player.getUUID();
        long today = today();
        var roll = data.daily(id);
        if (roll != null && roll.day() == today) return false;
        roll(player, data, roll, today, id.getMostSignificantBits()
                ^ Long.rotateLeft(id.getLeastSignificantBits(), 17) ^ today * 0x9E3779B97F4A7C15L);
        return true;
    }

    /** Administrator re-roll with a fresh seed; the bonus becomes claimable again. */
    public static void reroll(ServerPlayer player) {
        if (!enabled()) return;
        var data = QuestProgressData.get(player.getServer());
        roll(player, data, data.daily(player.getUUID()), today(), System.nanoTime());
    }

    private static void roll(ServerPlayer player, QuestProgressData data, QuestProgressData.Daily roll,
                             long today, long seed) {
        UUID id = player.getUUID();
        List<QuestDefinition> pool = QuestRepository.all().stream().filter(QuestDefinition::daily).toList();
        Set<String> previous = roll == null ? Set.of() : new HashSet<>(roll.quests());
        List<QuestDefinition> candidates = new ArrayList<>(pool.stream()
                .filter(QuestSteps::valid)
                .filter(quest -> QuestRuntime.baseUnlocked(data, player, quest))
                .filter(quest -> !pendingReward(data, id, quest))
                .toList());
        candidates.sort(Comparator.comparing(QuestDefinition::id));
        Collections.shuffle(candidates, new Random(seed));
        List<String> chosen = candidates.stream().limit(QuestConfig.defaults().dailyCommissionCount)
                .map(QuestDefinition::id).toList();
        for (var quest : pool) {
            if (!previous.contains(quest.id()) && !chosen.contains(quest.id())) continue;
            if (pendingReward(data, id, quest)) continue;
            data.reset(id, quest);
            QuestControlData.get(player).resetTask(player, quest);
            QuestEventGraphs.reset(player, quest);
        }
        data.daily(id, new QuestProgressData.Daily(today, chosen, false));
    }

    private static boolean pendingReward(QuestProgressData data, UUID player, QuestDefinition quest) {
        var entry = data.entry(player, quest);
        return entry != null && !entry.claimed();
    }

    public static int completed(QuestProgressData data, UUID player) {
        int done = 0;
        for (String id : todays(data, player)) {
            var quest = QuestRepository.find(id);
            if (quest != null && data.entry(player, quest) != null) done++;
        }
        return done;
    }

    /** Grants the configured bonus once per day after every rolled commission is complete. */
    public static String claimBonus(ServerPlayer player) {
        if (!enabled()) return "daily_unavailable";
        refresh(player);
        var data = QuestProgressData.get(player.getServer());
        var roll = data.daily(player.getUUID());
        if (roll == null || roll.quests().isEmpty()) return "daily_unavailable";
        if (roll.bonusClaimed()) return "daily_already_claimed";
        if (completed(data, player.getUUID()) < roll.quests().size()) return "daily_incomplete";
        if (QuestStructures.active(player)) return "scene_active";
        List<QuestDefinition.Reward> rewards;
        try {
            rewards = QuestRewards.freeze(player, QuestConfig.defaults().dailyBonusRewards);
        } catch (Exception invalid) {
            XeroQuestJournal.LOGGER.warn("Invalid daily commission bonus rewards", invalid);
            return "unavailable";
        }
        if (rewards.stream().anyMatch(reward -> reward.type().equals("loot_choice"))) return "unavailable";
        List<ItemStack> planned = new ArrayList<>(player.getInventory().items.stream().map(ItemStack::copy).toList());
        for (var reward : rewards) {
            if (!reward.type().equals("item")) continue;
            ItemStack prototype = QuestItemStackValue.encoded(reward.item())
                    ? QuestItemStackValue.decode(reward.item(), player.registryAccess()).orElse(ItemStack.EMPTY)
                    : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.item())));
            if (prototype.isEmpty()) return "unavailable";
            int remaining = reward.count();
            for (var stack : planned) {
                if (remaining <= 0) break;
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) continue;
                int amount = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(amount);
                remaining -= amount;
            }
            for (int slot = 0; slot < planned.size() && remaining > 0; slot++) {
                if (!planned.get(slot).isEmpty()) continue;
                int amount = Math.min(remaining, prototype.getMaxStackSize());
                planned.set(slot, prototype.copyWithCount(amount));
                remaining -= amount;
            }
            if (remaining > 0) return "inventory_full";
        }
        // Record the receipt before any side effect so a crash can never grant the bonus twice.
        data.daily(player.getUUID(), new QuestProgressData.Daily(roll.day(), roll.quests(), true));
        player.getServer().overworld().getDataStorage().save();
        for (int slot = 0; slot < planned.size(); slot++) player.getInventory().setItem(slot, planned.get(slot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        for (var reward : rewards) {
            if (reward.type().equals("item")) continue;
            try {
                QuestRewards.execute(player, reward);
            } catch (Exception failure) {
                XeroQuestJournal.LOGGER.error("Daily commission bonus reward {} failed", reward.type(), failure);
            }
        }
        player.getServer().getPlayerList().saveAll();
        return "daily_claimed";
    }
}
