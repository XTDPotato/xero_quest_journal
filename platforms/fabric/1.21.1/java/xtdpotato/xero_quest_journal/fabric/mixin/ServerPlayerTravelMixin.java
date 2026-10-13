package xtdpotato.xero_quest_journal.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import xtdpotato.xero_quest_journal.core.runtime.*;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.*;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTravelMixin {
    @WrapMethod(method = "changeDimension")
    private net.minecraft.world.entity.Entity xero$travel(DimensionTransition transition, Operation<net.minecraft.world.entity.Entity> original) {
        var player = (ServerPlayer) (Object) this;
        var event = new EntityTravelToDimensionEvent(player, transition.newLevel().dimension());
        QuestStructureEvents.travel(event);
        if (event.isCanceled()) return null;
        var from = player.level().dimension();
        var result = original.call(transition);
        if (result instanceof ServerPlayer serverPlayer && !from.equals(serverPlayer.level().dimension()))
            QuestGraphEvents.dimension(new PlayerEvent.PlayerChangedDimensionEvent(serverPlayer, serverPlayer.level().dimension()));
        return result;
    }
}
