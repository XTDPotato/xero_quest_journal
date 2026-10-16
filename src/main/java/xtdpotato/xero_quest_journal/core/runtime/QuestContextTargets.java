package xtdpotato.xero_quest_journal.core.runtime;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** Read references select one player; write actions may intentionally select several. */
public final class QuestContextTargets {
    private QuestContextTargets() {}
    public static void validateRead(String selector) {
        if (selector == null || selector.isBlank()) return;
        if (selector.length() > 256) throw new IllegalArgumentException("Player reference exceeds limit");
        try {
            var reader = new StringReader(selector);
            EntityArgument.player().parse(reader);
            if (reader.canRead()) throw new IllegalArgumentException("Invalid player reference");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException("A read reference must select one player", invalid);
        }
    }
    public static ServerPlayer resolve(ServerPlayer trigger, String selector) {
        if (selector == null || selector.isBlank() || selector.equals("@s")) return trigger;
        validateRead(selector);
        try {
            return EntityArgument.player().parse(new StringReader(selector))
                    .findSinglePlayer(trigger.createCommandSourceStack().withPermission(2));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException("Player reference did not resolve", invalid);
        }
    }
}
