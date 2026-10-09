package xtdpotato.xero_quest_journal.core.mailbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import xtdpotato.xero_quest_journal.core.io.MailTemplateStore;
import xtdpotato.xero_quest_journal.core.model.MailNodeSettings;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Portable JSON mail payloads and their command-line representation. */
public final class MailClipboardCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_JSON_BYTES = 24_000;
    private static final int MAX_COMMAND_CHARS = 32_000;

    public record ImportedMail(String recipient, MailDraft draft) {}

    private MailClipboardCodec() {}

    public static String json(MailDraft draft) {
        validate(draft);
        String json = GSON.toJson(draft);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Mail JSON exceeds clipboard limit");
        }
        return json;
    }

    public static String command(String recipient, MailDraft draft) {
        String target = recipient == null || recipient.isBlank() ? "@s" : recipient.strip();
        MailNodeSettings.validatePlayer(target);
        if (target.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Command target cannot contain whitespace");
        }
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json(draft).getBytes(StandardCharsets.UTF_8));
        String command = "/xero_quest mail send_bundle " + target + " " + encoded;
        if (command.length() > MAX_COMMAND_CHARS) {
            throw new IllegalArgumentException("Mail command exceeds command length limit");
        }
        return command;
    }

    public static ImportedMail parse(String clipboard) {
        if (clipboard == null || clipboard.isBlank() || clipboard.length() > MAX_COMMAND_CHARS) {
            throw new IllegalArgumentException("Clipboard is empty or too large");
        }
        String value = clipboard.strip();
        if (value.startsWith("{")) return new ImportedMail("@s", parseJson(value));

        String command = value.startsWith("/") ? value.substring(1) : value;
        String[] parts = command.split("\\s+", 6);
        if (parts.length != 5 || !parts[0].equals("xero_quest")
                || !parts[1].equals("mail") || !parts[2].equals("send_bundle")) {
            throw new IllegalArgumentException("Clipboard is not a supported mail bundle");
        }
        String recipient = parts[3];
        MailNodeSettings.validatePlayer(recipient);
        byte[] json;
        try {
            json = Base64.getUrlDecoder().decode(parts[4]);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Mail bundle encoding is invalid", malformed);
        }
        if (json.length > MAX_JSON_BYTES) throw new IllegalArgumentException("Mail bundle exceeds size limit");
        return new ImportedMail(recipient, parseJson(new String(json, StandardCharsets.UTF_8)));
    }

    private static MailDraft parseJson(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Mail JSON exceeds size limit");
        }
        try {
            if (!JsonParser.parseString(json).isJsonObject()) throw new IllegalArgumentException("Mail JSON must be an object");
            MailDraft draft = GSON.fromJson(json, MailDraft.class);
            validate(draft);
            return draft;
        } catch (RuntimeException malformed) {
            if (malformed instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Mail JSON is invalid", malformed);
        }
    }

    private static void validate(MailDraft draft) {
        MailTemplateStore.validate(draft);
        if (draft.attachments().size() > 128) throw new IllegalArgumentException("Too many mail attachments");
    }
}
