package xtdpotato.xero_quest_journal.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructureEvents;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.EntityTravelToDimensionEvent;

@Mixin(Entity.class)
public abstract class EntityTravelMixin {
    @WrapMethod(method = "changeDimension")
    private Entity xero$travel(DimensionTransition transition, Operation<Entity> original) {
        var event = new EntityTravelToDimensionEvent((Entity) (Object) this, transition.newLevel().dimension());
        QuestStructureEvents.travel(event);
        return event.isCanceled() ? null : original.call(transition);
    }
}
