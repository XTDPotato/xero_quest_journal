package xtdpotato.xero_quest_journal.core.model;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.EntityArgument;
import java.util.List;
import java.util.Map;

/** Shared authoring vocabulary and limits for player-scoped mailbox nodes. */
public final class MailNodeSettings {
    public static final List<String> TYPES = List.of("mail_send", "mail_check", "mail_delete", "mail_set_property",
            "mail_lock_task", "mail_unlock_task");
    public static final List<String> TASK_TYPES = List.of("mail_lock_task", "mail_unlock_task");
    public static final List<String> STATES = List.of("exists", "missing", "unread", "read", "claimable", "claimed", "starred");
    public static final List<String> PROPERTIES = List.of("sender", "subject", "body", "read", "starred", "expiresIn", "icon", "treasured");
    private MailNodeSettings() {}

    public static void validate(String type, Map<String, String> data) {
        if (!TYPES.contains(type)) return;
        validatePlayer(data.getOrDefault("player", ""));
        if (TASK_TYPES.contains(type)) {
            String target = data.getOrDefault("target", "");
            if (target.length() > 256 || target.indexOf('\n') >= 0 || target.indexOf('\0') >= 0
                    || !target.isBlank() && !target.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("Invalid mail task target");
            return;
        }
        String id = data.getOrDefault("mailId", "");
        if (id.length() > 256 || id.indexOf('\n') >= 0 || id.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid mail ID");
        if (type.equals("mail_check") && !STATES.contains(data.getOrDefault("state", "exists")))
            throw new IllegalArgumentException("Invalid mail state");
        if (type.equals("mail_set_property")) {
            String property = data.getOrDefault("property", "subject");
            if (!PROPERTIES.contains(property)) throw new IllegalArgumentException("Invalid mail property");
            validateValue(property, data.getOrDefault("value", ""));
        }
        if (type.equals("mail_send")) {
            for (String key : List.of("sender", "subject", "body"))
                validateValue(key, data.getOrDefault(key, ""));
            validateValue("expiresIn", data.getOrDefault("expiresIn", "0"));
            validateValue("icon", data.getOrDefault("icon", ""));
            validateValue("treasured", data.getOrDefault("treasured", "false"));
            validateValue("gameTime", data.getOrDefault("gameTime", "false"));
        }
    }

    public static void validatePlayer(String value) {
        if (value.isBlank()) return;
        if (value.length() > 256) throw new IllegalArgumentException("Invalid mail player selector");
        var reader = new StringReader(value);
        try {
            EntityArgument.players().parse(reader);
            if (reader.canRead()) throw new IllegalArgumentException("Invalid mail player selector");
        } catch (CommandSyntaxException invalid) {
            throw new IllegalArgumentException("Invalid mail player selector", invalid);
        }
    }

    public static void validateValue(String property, String value) {
        int limit = property.equals("body") || property.equals("icon") ? 8192 : property.equals("subject") ? 512 : 256;
        if (value.length() > limit) throw new IllegalArgumentException("Mail property exceeds limit");
        if (List.of("read", "starred", "treasured", "gameTime").contains(property) && !List.of("true", "false").contains(value))
            throw new IllegalArgumentException("Mail flag must be true or false");
        if (property.equals("expiresIn")) {
            long seconds = Long.parseLong(value);
            if (seconds < 0 || seconds > 315360000L)
                throw new IllegalArgumentException("Mail expiry must be 0..315360000 seconds");
        }
    }
}
