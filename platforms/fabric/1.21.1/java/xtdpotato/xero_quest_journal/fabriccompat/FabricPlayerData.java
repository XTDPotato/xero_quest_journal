package xtdpotato.xero_quest_journal.fabriccompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public interface FabricPlayerData {
    CompoundTag xero$persistentData();
    static CompoundTag get(Entity entity) { return ((FabricPlayerData) entity).xero$persistentData(); }
}
