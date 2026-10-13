package xtdpotato.xero_quest_journal.fabric.mixin;

import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xtdpotato.xero_quest_journal.client.XeroQuestClient;
import xtdpotato.xero_quest_journal.fabriccompat.FabricScreenEvent;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen xero$scenePause(Screen screen) {
        var event = new FabricScreenEvent.Opening(((Minecraft) (Object) this).screen, screen);
        XeroQuestClient.openScenePause(event);
        return event.getNewScreen();
    }
}
