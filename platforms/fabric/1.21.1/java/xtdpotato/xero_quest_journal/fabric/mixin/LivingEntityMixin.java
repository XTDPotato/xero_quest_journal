package xtdpotato.xero_quest_journal.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.LivingEvent;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void xero$jump(CallbackInfo callback) {
        QuestGraphEvents.jump(new LivingEvent.LivingJumpEvent((LivingEntity) (Object) this));
    }
}
