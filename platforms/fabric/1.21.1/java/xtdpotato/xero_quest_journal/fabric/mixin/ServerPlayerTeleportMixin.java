package xtdpotato.xero_quest_journal.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructureEvents;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.*;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    @Unique
    private boolean xero$blocked(ServerLevel target) {
        var player = (ServerPlayer) (Object) this;
        if (player.level().dimension().equals(target.dimension())) return false;
        var event = new EntityTravelToDimensionEvent(player, target.dimension());
        QuestStructureEvents.travel(event);
        return event.isCanceled();
    }

    @WrapMethod(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z")
    private boolean xero$commandTeleport(ServerLevel target, double x, double y, double z,
                                         Set<RelativeMovement> relative, float yaw, float pitch,
                                         Operation<Boolean> original) {
        return !xero$blocked(target) && original.call(target, x, y, z, relative, yaw, pitch);
    }

    @WrapMethod(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V")
    private void xero$teleport(ServerLevel target, double x, double y, double z,
                               float yaw, float pitch, Operation<Void> original) {
        if (xero$blocked(target)) return;
        var player = (ServerPlayer) (Object) this;
        var from = player.level().dimension();
        original.call(target, x, y, z, yaw, pitch);
        // The command overload delegates cross-dimension travel to this overload.
        if (!from.equals(player.level().dimension()))
            QuestGraphEvents.dimension(new PlayerEvent.PlayerChangedDimensionEvent(player, from, player.level().dimension()));
    }
}
