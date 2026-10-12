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
        String value = command.startsWith("/") ? command : "/" + command;
        return player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(2).withSuppressedOutput(), value);
    }
}
