package xtdpotato.xero_quest_journal.fabric.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtdpotato.xero_quest_journal.core.runtime.*;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.PlayerEvent;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
    @Shadow private Player player;
    @Shadow private int removeCount;
    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void xero$smelted(ItemStack stack, CallbackInfo callback) {
        if (player.level().isClientSide || removeCount <= 0) return;
        var event = new PlayerEvent.ItemSmeltedEvent(player, stack.copyWithCount(removeCount));
        QuestConditionEvents.smelted(event);
        QuestGraphEvents.smelt(event);
    }
}
