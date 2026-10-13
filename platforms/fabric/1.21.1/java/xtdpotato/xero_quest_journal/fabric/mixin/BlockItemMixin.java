package xtdpotato.xero_quest_journal.fabric.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructureEvents;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.BlockEvent;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void xero$allowPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callback) {
        var player = context.getPlayer();
        if (player == null || context.getLevel().isClientSide) return;
        var event = new BlockEvent.EntityPlaceEvent(player, context.getClickedPos(),
                context.getLevel().getBlockState(context.getClickedPos()));
        QuestStructureEvents.placeBlock(event);
        if (event.isCanceled()) callback.setReturnValue(InteractionResult.FAIL);
    }
}
