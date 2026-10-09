package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import xtdpotato.xero_quest_journal.core.mailbox.MailDraft;
import xtdpotato.xero_quest_journal.core.model.MailNodeSettings;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Shared JSON mail templates used by commands and quest rewards. */
public final class MailTemplateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FMLPaths.CONFIGDIR.get().resolve("xero_quest/mail")
            .toAbsolutePath().normalize();

    private MailTemplateStore() {}

    public static MailDraft find(String id) {
        try {
            Path path = path(id);
            if (!Files.isRegularFile(path) || Files.size(path) > 131072) return null;
            MailDraft draft = GSON.fromJson(Files.readString(path), MailDraft.class);
            validate(draft);
            return draft.id().equals(id) ? draft : null;
        } catch (Exception invalid) {
            return null;
        }
    }

    public static List<MailDraft> all() {
        try {
            if (!Files.isDirectory(ROOT)) return List.of();
            try (var files = Files.list(ROOT)) {
                return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                        .map(file -> file.getFileName().toString().replaceFirst("\\.json$", ""))
                        .map(MailTemplateStore::find).filter(java.util.Objects::nonNull).toList();
            }
        } catch (Exception invalid) {
            return List.of();
        }
    }

    public static void save(ServerPlayer author, MailDraft draft) {
        if (author == null || !author.hasPermissions(2)) throw new IllegalArgumentException("Permission required");
        validate(draft);
        try {
            String json = GSON.toJson(draft);
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 131072)
                throw new IllegalArgumentException("Mail template exceeds JSON size limit");
            AtomicFiles.write(path(draft.id()), json, true);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Unable to save mail template", failure);
        }
    }

    public static void validate(MailDraft draft) {
        if (draft == null || draft.id() == null || draft.sender() == null || draft.subject() == null
                || draft.body() == null || draft.icon() == null || draft.attachments() == null
                || draft.subject().isBlank() || draft.body().isBlank()) throw new IllegalArgumentException("Invalid mail template");
        path(draft.id());
        MailNodeSettings.validate("mail_send", Map.of("mailId", draft.id(), "sender", draft.sender(),
                "subject", draft.subject(), "body", draft.body(), "icon", draft.icon()));
        if (draft.attachments().size() > 128 || draft.attachments().stream().anyMatch(a -> a == null
                || a.type().equals("mail") || a.count() < 1
                || !QuestRewards.valid(new QuestDefinition.Reward(a.type(), a.item(), a.count()))))
            throw new IllegalArgumentException("Invalid mail attachments");
    }

    private static Path path(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,128}"))
            throw new IllegalArgumentException("Invalid mail template ID");
        Path file = ROOT.resolve(id + ".json").normalize();
        if (!file.startsWith(ROOT)) throw new IllegalArgumentException("Invalid mail template path");
        return file;
    }
}
