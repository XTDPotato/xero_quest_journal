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
import xtdpotato.xero_quest_journal.XeroQuestJournal;

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
        if (event.getPlayer() instanceof ServerPlayer player)
            acquired(player, event.getOriginalStack(), event.getOriginalStack().getCount() - event.getCurrentStack().getCount());
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
        if (count > 0) QuestConditions.recordItemAcquired(player, stack, count);
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
