package xtdpotato.xero_quest_journal.fabric.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xtdpotato.xero_quest_journal.client.StyledButton;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerInputMixin {
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void xero$releaseEntry(double x, double y, int button, CallbackInfoReturnable<Boolean> callback) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        for (var child : screen.children()) {
            if (child instanceof StyledButton entry
                    && (entry.styleId().equals("inventory-entry") || entry.styleId().equals("mailbox-inventory-entry"))
                    && entry.mouseReleased(x, y, button)) {
                callback.setReturnValue(true);
                return;
            }
        }
    }
}
