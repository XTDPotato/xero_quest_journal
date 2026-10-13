package xtdpotato.xero_quest_journal.fabric.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.AdvancementEvent;

@Mixin(PlayerAdvancements.class)
public abstract class AdvancementMixin {
    @Shadow private ServerPlayer player;
    @Shadow public abstract net.minecraft.advancements.AdvancementProgress getOrStartProgress(AdvancementHolder holder);
    @Inject(method = "award", at = @At("RETURN"))
    private void xero$earned(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && getOrStartProgress(holder).isDone())
            QuestGraphEvents.advancement(new AdvancementEvent.AdvancementEarnEvent(player, holder));
    }
}
