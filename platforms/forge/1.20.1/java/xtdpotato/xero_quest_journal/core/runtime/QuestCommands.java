package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;

final class QuestCommands {
    private QuestCommands() {
    }

    static int execute(ServerPlayer player, String command) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()
                || command == null || command.isBlank() || command.length() > 4096
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid reward command");
        }
        String value = command.startsWith("/") ? command.substring(1) : command;
        var dispatcher = player.getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var event = new net.minecraftforge.event.CommandEvent(dispatcher.parse(value, source));
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
            throw new IllegalArgumentException("Reward command canceled", event.getException());
        }
        try {
            return dispatcher.execute(event.getParseResults());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalArgumentException("Reward command failed", failure);
        }
    }
}
