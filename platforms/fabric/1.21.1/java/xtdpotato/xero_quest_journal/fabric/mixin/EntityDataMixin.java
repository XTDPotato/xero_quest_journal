package xtdpotato.xero_quest_journal.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xtdpotato.xero_quest_journal.fabriccompat.FabricPlayerData;

@Mixin(Entity.class)
public abstract class EntityDataMixin implements FabricPlayerData {
    @Unique private CompoundTag xero$data = new CompoundTag();
    @Override public CompoundTag xero$persistentData() { return xero$data; }
    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void xero$save(CompoundTag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<CompoundTag> callback) {
        if (!xero$data.isEmpty()) tag.put("xero_quest_journal", xero$data.copy());
    }
    @Inject(method = "load", at = @At("TAIL"))
    private void xero$load(CompoundTag tag, CallbackInfo callback) {
        xero$data = tag.getCompound("xero_quest_journal").copy();
    }
}
