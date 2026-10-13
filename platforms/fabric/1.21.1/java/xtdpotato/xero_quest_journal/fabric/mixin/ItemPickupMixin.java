package xtdpotato.xero_quest_journal.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import xtdpotato.xero_quest_journal.core.runtime.*;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.*;

@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin {
    @WrapMethod(method = "playerTouch")
    private void xero$pickup(Player player, Operation<Void> original) {
        var entity = (ItemEntity) (Object) this;
        if (entity.level().isClientSide) { original.call(player); return; }
        var pre = new ItemEntityPickupEvent.Pre(player, entity);
        QuestStructureEvents.pickup(pre);
        if (pre.canPickup() == TriState.FALSE) return;
        var before = entity.getItem().copy();
        original.call(player);
        var after = entity.isRemoved() ? net.minecraft.world.item.ItemStack.EMPTY : entity.getItem().copy();
        var post = new ItemEntityPickupEvent.Post(player, before, after);
        QuestConditionEvents.pickup(post);
        QuestGraphEvents.pickup(post);
    }
}
