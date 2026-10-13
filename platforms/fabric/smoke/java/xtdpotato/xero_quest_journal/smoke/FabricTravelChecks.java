package xtdpotato.xero_quest_journal.smoke;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructures;

/** Calls vanilla entry points so callback-only tests cannot hide missing mixins. */
final class FabricTravelChecks {
    static void blockedTravel(ServerPlayer player) {
        boolean inside = QuestStructures.active(player);
        var target = player.server.getLevel(inside ? Level.OVERWORLD : QuestStructures.DIMENSION);
        if (target == null) throw new AssertionError("Scene dimension is missing");
        var from = player.level().dimension();
        var before = player.position();
        if (player.changeDimension(new DimensionTransition(target, player, DimensionTransition.DO_NOTHING)) != null)
            throw new AssertionError("Vanilla dimension travel bypassed scene guard");
        if (player.teleportTo(target, before.x, before.y, before.z, Set.of(), 0, 0))
            throw new AssertionError("Command dimension travel bypassed scene guard");
        player.teleportTo(target, before.x, before.y, before.z, 0, 0);
        if (!player.level().dimension().equals(from) || !player.position().equals(before))
            throw new AssertionError("Rejected dimension travel moved the player");
        if (!inside) {
            var entity = EntityType.PIG.create(player.serverLevel());
            if (entity == null) throw new AssertionError("Could not create dimension guard fixture");
            try {
                if (entity.changeDimension(new DimensionTransition(target, entity, DimensionTransition.DO_NOTHING)) != null)
                    throw new AssertionError("Non-player entity entered scene dimension");
            } finally {
                entity.discard();
            }
        }
    }
}
