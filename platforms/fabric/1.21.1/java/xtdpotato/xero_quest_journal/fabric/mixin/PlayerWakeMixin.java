package xtdpotato.xero_quest_journal.fabric.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.PlayerWakeUpEvent;

@Mixin(Player.class)
public abstract class PlayerWakeMixin {
    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void xero$wake(boolean immediately, boolean updateLevel, CallbackInfo callback) {
        var player = (Player) (Object) this;
        if (player.isSleeping()) QuestGraphEvents.wake(new PlayerWakeUpEvent(player));
    }
}
