package xtdpotato.xero_quest_journal.forgecompat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.HolderLookup;

/**
 * Compatibility name used by the 1.21 network code. Forge 1.20.1 payloads
 * use the same FriendlyByteBuf operations, without registry lookup helpers.
 */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    public RegistryFriendlyByteBuf(ByteBuf source) {
        super(source);
    }

    public RegistryFriendlyByteBuf(ByteBuf source, HolderLookup.Provider ignoredRegistryAccess) {
        super(source);
    }
}
