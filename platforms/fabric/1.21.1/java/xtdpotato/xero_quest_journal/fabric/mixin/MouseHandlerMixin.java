package xtdpotato.xero_quest_journal.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtdpotato.xero_quest_journal.client.XeroQuestClient;
import xtdpotato.xero_quest_journal.fabriccompat.*;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void xero$hud(long window, int button, int action, int modifiers, CallbackInfo callback) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        var event = new FabricInputEvent.MouseButton.Pre(button, action);
        XeroQuestClient.clickMailInGame(event);
        if (event.isCanceled()) callback.cancel();
    }
    @WrapOperation(method = "method_1611", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(DDI)Z"))
    private static boolean xero$click(Screen screen, double x, double y, int button, Operation<Boolean> original) {
        var event = new FabricScreenEvent.MouseButtonPressed.Pre(screen, x, y, button);
        XeroQuestClient.clickMailOnScreen(event);
        if (!event.isCanceled()) XeroQuestClient.mouseDown(event);
        return event.isCanceled() || original.call(screen, x, y, button);
    }
    @WrapOperation(method = "method_1605", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(DDI)Z"))
    private static boolean xero$release(Screen screen, double x, double y, int button, Operation<Boolean> original) {
        var event = new FabricScreenEvent.MouseButtonReleased.Pre(screen, x, y, button);
        XeroQuestClient.releaseMailOnScreen(event);
        if (!event.isCanceled()) XeroQuestClient.mouseUp(event);
        return event.isCanceled() || original.call(screen, x, y, button);
    }
    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
    private boolean xero$scroll(Screen screen, double x, double y, double h, double v, Operation<Boolean> original) {
        var event = new FabricScreenEvent.MouseScrolled.Pre(screen, x, y, h, v);
        XeroQuestClient.scroll(event);
        return event.isCanceled() || original.call(screen, x, y, h, v);
    }
    @WrapOperation(method = "method_55795", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(DDIDD)Z"))
    private boolean xero$drag(Screen screen, double x, double y, int button, double dx, double dy, Operation<Boolean> original) {
        var event = new FabricScreenEvent.MouseDragged.Pre(screen, x, y, button, dx, dy);
        XeroQuestClient.mouseDrag(event);
        return event.isCanceled() || original.call(screen, x, y, button, dx, dy);
    }
}
