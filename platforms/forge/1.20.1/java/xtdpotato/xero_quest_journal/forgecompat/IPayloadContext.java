package xtdpotato.xero_quest_journal.forgecompat;

import xtdpotato.xero_quest_journal.forgecompat.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public interface IPayloadContext {
    Player player();

    void reply(CustomPacketPayload payload);
}
