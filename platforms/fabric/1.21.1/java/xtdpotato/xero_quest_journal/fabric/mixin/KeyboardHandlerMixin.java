package xtdpotato.xero_quest_journal.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xtdpotato.xero_quest_journal.client.XeroQuestClient;
import xtdpotato.xero_quest_journal.fabriccompat.FabricScreenEvent;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @WrapOperation(method = "method_1454", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(III)Z"))
    private static boolean xero$press(Screen screen, int key, int scan, int modifiers, Operation<Boolean> original) {
        var event = new FabricScreenEvent.KeyPressed.Pre(screen, key, scan, modifiers);
        XeroQuestClient.keyDown(event);
        return event.isCanceled() || original.call(screen, key, scan, modifiers);
    }
    @WrapOperation(method = "method_1454", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/screens/Screen;keyReleased(III)Z"))
    private static boolean xero$release(Screen screen, int key, int scan, int modifiers, Operation<Boolean> original) {
        var event = new FabricScreenEvent.KeyReleased.Pre(screen, key, scan, modifiers);
        XeroQuestClient.keyUp(event);
        return event.isCanceled() || original.call(screen, key, scan, modifiers);
    }
    @WrapOperation(method = {"method_1473", "method_1458"}, remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/components/events/GuiEventListener;charTyped(CI)Z"))
    private static boolean xero$char(net.minecraft.client.gui.components.events.GuiEventListener listener,
                                    char character, int modifiers, Operation<Boolean> original) {
        if (!(listener instanceof Screen screen)) return original.call(listener, character, modifiers);
        var event = new FabricScreenEvent.CharacterTyped.Pre(screen);
        XeroQuestClient.character(event);
        return event.isCanceled() || original.call(listener, character, modifiers);
    }
}
