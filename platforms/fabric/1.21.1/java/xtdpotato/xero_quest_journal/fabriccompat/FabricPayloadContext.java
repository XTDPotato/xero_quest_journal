package xtdpotato.xero_quest_journal.fabriccompat;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import java.util.function.Consumer;

public record FabricPayloadContext(Player player, Consumer<CustomPacketPayload> replies) {
    public void reply(CustomPacketPayload payload) { replies.accept(payload); }
}
