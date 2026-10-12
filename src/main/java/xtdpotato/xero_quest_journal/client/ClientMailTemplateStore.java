package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.core.io.MailTemplateStore;
import xtdpotato.xero_quest_journal.core.mailbox.MailDraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Client-side index of templates saved by this editor for quick reuse. */
final class ClientMailTemplateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = PlatformServices.configDir().resolve("xero_quest/mail/client")
            .toAbsolutePath().normalize();

    private ClientMailTemplateStore() {}

    static List<MailDraft> all() {
        try {
            if (!Files.isDirectory(ROOT)) return List.of();
            try (var files = Files.list(ROOT)) {
                return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(ClientMailTemplateStore::read).filter(java.util.Objects::nonNull).toList();
            }
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static void save(MailDraft draft) {
        MailTemplateStore.validate(draft);
        try {
            Files.createDirectories(ROOT);
            Path file = ROOT.resolve(draft.id() + ".json").normalize();
            if (!file.startsWith(ROOT)) throw new IllegalArgumentException("Invalid mail template path");
            String json = GSON.toJson(draft);
            Files.writeString(file, json);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Unable to save local mail template", failure);
        }
    }

    private static MailDraft read(Path file) {
        try {
            if (Files.size(file) > 131_072) return null;
            MailDraft draft = GSON.fromJson(JsonParser.parseString(Files.readString(file)), MailDraft.class);
            MailTemplateStore.validate(draft);
            return draft;
        } catch (Exception ignored) {
            return null;
        }
    }
}
