package xtdpotato.xero_quest_journal.core.runtime;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;

public final class QuestMailActions {
    private QuestMailActions() {}

    public static java.util.List<MailboxRuntime.Attachment> attachments(TriggerGraph.Node node) {
        var values = new com.google.gson.Gson().fromJson(node.get("rewards", "[]"), MailboxRuntime.Attachment[].class);
        if (values == null || values.length > 128) throw new IllegalArgumentException("Invalid mail rewards");
        return java.util.List.of(values);
    }

    public static boolean execute(ServerPlayer player, QuestDefinition quest, String control, TriggerGraph.Node node) {
        boolean taskAction = xtdpotato.xero_quest_journal.core.model.MailNodeSettings.TASK_TYPES.contains(node.type());
        String id = node.get(taskAction ? "target" : "mailId", "");
        if (id.isBlank()) throw new IllegalArgumentException(taskAction ? "Task ID is required" : "Mail ID is required");
        String selector = node.get("player", "");
        if (selector.isBlank()) return executeFor(player, player, quest, control, node, id);
        xtdpotato.xero_quest_journal.core.model.MailNodeSettings.validatePlayer(selector);
        try {
            var targets = EntityArgument.players().parse(new StringReader(selector))
                    .findPlayers(player.createCommandSourceStack().withPermission(2));
            if (targets.isEmpty()) return false;
            boolean success = true;
            for (ServerPlayer target : targets) success &= executeFor(player, target, quest, control, node, id);
            return success;
        } catch (CommandSyntaxException invalid) {
            throw new IllegalArgumentException("Mail player selector found no valid players", invalid);
        }
    }

    private static boolean executeFor(ServerPlayer trigger, ServerPlayer target, QuestDefinition quest,
                                      String control, TriggerGraph.Node node, String id) {
        return switch (node.type()) {
            case "mail_lock_task" -> QuestRuntime.trigger(target, id, "lock", false).equals("locked");
            case "mail_unlock_task" -> QuestRuntime.trigger(target, id, "unlock", false).equals("unlocked");
            case "mail_check" -> MailboxRuntime.check(target, id, node.get("state", "exists"));
            case "mail_delete" -> MailboxRuntime.delete(target, id,
                    Boolean.parseBoolean(node.get("discardPending", "false"))).equals("deleted");
            case "mail_set_property" -> MailboxRuntime.setProperty(target, id,
                    node.get("property", "subject"), node.get("value", ""));
            case "mail_send" -> {
                String key = quest.progressKey() + "/" + control + "/" + node.id() + "/action";
                var state = QuestControlData.get(trigger);
                boolean once = Boolean.parseBoolean(node.get("once", "true"));
                if (once && state.fired(target, quest, key)) yield true;
                boolean sent = MailboxRuntime.sendIfAbsent(target, id, node.get("sender", ""),
                        node.get("subject", ""), node.get("body", ""),
                        Long.parseLong(node.get("expiresIn", "0")),
                        Boolean.parseBoolean(node.get("gameTime", "false")), node.get("icon", ""),
                        Boolean.parseBoolean(node.get("treasured", "false")), attachments(node));
                if (sent && once) state.markFired(target, quest, key);
                yield sent;
            }
            default -> throw new IllegalArgumentException("Unknown mail node");
        };
    }
}
