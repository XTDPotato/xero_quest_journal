package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

@EventBusSubscriber(modid = XeroQuestJournal.MOD_ID)
public final class QuestConditionEvents {
    private QuestConditionEvents() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void killed(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player)
            QuestConditions.record(player, "kill", BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString(), 1);
    }
    @SubscribeEvent
    public static void pickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        int count = event.getOriginalStack().getCount() - event.getCurrentStack().getCount();
        acquired(player, event.getOriginalStack(), count);
    }
    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) acquired(player, event.getCrafting(), event.getCrafting().getCount());
    }
    @SubscribeEvent
    public static void smelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) acquired(player, event.getSmelting(), event.getSmelting().getCount());
    }
    private static void acquired(ServerPlayer player, ItemStack stack, int count) {
        if (count > 0) {
            QuestConditions.recordItemAcquired(player, stack, count);
            notifyTrackedPickup(player, stack, count);
        }
    }

    private static void notifyTrackedPickup(ServerPlayer player, ItemStack stack, int count) {
        if (count <= 0 || stack.isEmpty()) return;
        var data = QuestProgressData.get(player.getServer());
        for (String id : QuestRuntime.tracked(player)) {
            QuestDefinition quest = QuestRepository.find(id);
            if (quest == null || data.entry(player.getUUID(), quest) != null) continue;
            QuestDefinition active = QuestSteps.current(data, player.getUUID(), quest);
            QuestDefinition.Trigger matched = matchingItemCondition(player, active, stack);
            if (matched == null) continue;
            QuestConditions.Progress progress = QuestConditions.progress(player, active, matched);
            PacketDistributor.sendToPlayer(player, new QuestNetwork.ItemPickupNoticePayload(
                    stack.copyWithCount(1), count, progress.current(), progress.required(), quest.id(), "acquire"));
            return;
        }
    }

    private static QuestDefinition.Trigger matchingItemCondition(ServerPlayer player, QuestDefinition quest,
                                                                  ItemStack picked) {
        for (QuestDefinition.Trigger trigger : quest.triggers()) {
            var match = matchingItemCondition(player, trigger, picked);
            if (match != null) return match;
        }
        return null;
    }

    private static QuestDefinition.Trigger matchingItemCondition(ServerPlayer player,
                                                                  QuestDefinition.Trigger trigger,
                                                                  ItemStack picked) {
        if (java.util.List.of("item_get", "item_hold", "item_acquire", "item_submit").contains(trigger.type())
                && QuestConditions.matches(picked, trigger.item(), player.registryAccess())) return trigger;
        for (QuestDefinition.Trigger child : trigger.children()) {
            var match = matchingItemCondition(player, child, picked);
            if (match != null) return match;
        }
        return null;
    }
    public static java.util.List<ItemStack> consumedItems(ServerPlayer player, java.util.List<ItemStack> remaining) {
        var consumed = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < remaining.size(); slot++) {
            var before = player.getInventory().getItem(slot);
            int count = before.getCount() - remaining.get(slot).getCount();
            if (before.isEmpty() || count <= 0) continue;
            var same = consumed.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, before)).findFirst().orElse(null);
            if (same == null) consumed.add(before.copyWithCount(count)); else same.grow(count);
        }
        return consumed;
    }
    public static void notifySubmission(ServerPlayer player, QuestDefinition active, java.util.List<ItemStack> consumed) {
        for (var stack : consumed) {
            var matched = matchingSubmissionCondition(player, active.triggers(), stack);
            if (matched == null) continue;
            PacketDistributor.sendToPlayer(player, new QuestNetwork.ItemPickupNoticePayload(stack.copyWithCount(1),
                    stack.getCount(), matched.count(), matched.count(), active.id(), "submit"));
        }
    }
    private static QuestDefinition.Trigger matchingSubmissionCondition(ServerPlayer player,
            java.util.List<QuestDefinition.Trigger> conditions, ItemStack stack) {
        for(var condition : conditions) {
            if(condition.type().equals("item_submit") && QuestConditions.matches(stack, condition.item(), player.registryAccess())) return condition;
            var nested = matchingSubmissionCondition(player, condition.children(), stack);
            if(nested != null) return nested;
        }
        return null;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player)
            QuestConditions.record(player, "block_break", BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString(), 1);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interacted(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND)
            QuestConditions.record(player, "block_interact",
                    BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).toString(), 1);
    }
}
