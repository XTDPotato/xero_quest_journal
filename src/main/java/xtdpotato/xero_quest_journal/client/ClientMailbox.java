package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.mailbox.MailBannerTiming;
import xtdpotato.xero_quest_journal.core.network.QuestClientInbox;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Client mailbox cache. All changes still go through the server. */
public final class ClientMailbox {
    private static List<MailboxRuntime.View> messages = List.of();
    private static List<QuestNetwork.MailTemplateEntry> templates = List.of();
    private static final Map<Long, CompletableFuture<QuestNetwork.MailboxActionResultPayload>> pending = new LinkedHashMap<>();
    private static final Map<Long, Long> deadlines = new LinkedHashMap<>();
    private static Object connection;
    private static long nextRequestId;
    private static long revision;
    private static int pollTicks;
    private static long snapshotNanos, snapshotGameTicks;

    public static long remainingSeconds(MailboxRuntime.View mail) {
        if (mail.treasured() || mail.expiresAt() <= 0) return Long.MAX_VALUE;
        long elapsed;
        if (mail.gameTime()) {
            var level = Minecraft.getInstance().level;
            elapsed = level == null ? 0 : Math.max(0, level.getGameTime() - snapshotGameTicks);
        } else elapsed = Math.max(0, (System.nanoTime() - snapshotNanos) / 1_000_000L);
        long unit = mail.gameTime() ? 20 : 1000;
        long remaining = Math.max(0, mail.expiresAt() - mail.clock() - elapsed);
        return (remaining + unit - 1) / unit;
    }

    public static long bannerRemainingSeconds(MailboxRuntime.View mail) {
        var timing = mail.bannerTiming();
        if (timing.mode().equals("mail")) return remainingSeconds(mail);
        long seconds = mail.bannerRemainingSeconds();
        if (seconds < 0 || !timing.mode().equals("countdown")) return seconds;
        long elapsed = timing.gameTime() && Minecraft.getInstance().level != null
                ? Math.max(0, Minecraft.getInstance().level.getGameTime() - snapshotGameTicks) / 20
                : timing.gameTime() ? 0 : Math.max(0, (System.nanoTime() - snapshotNanos) / 1_000_000_000L);
        return Math.max(0, seconds - elapsed);
    }

    private ClientMailbox() {
    }

    public static List<MailboxRuntime.View> messages() {
        return messages;
    }

    public static long revision() {
        return revision;
    }

    public static void tick() {
        Minecraft game = Minecraft.getInstance();
        if (connection != game.getConnection()) {
            XeroTips.clear();
            connection = game.getConnection();
            messages = List.of();
            templates = List.of();
            pending.values().forEach(future -> future.complete(
                    new QuestNetwork.MailboxActionResultPayload(0, "remote_unavailable")));
            pending.clear();
            deadlines.clear();
            MailArrivalToast.clear();
            XeroToast.clear();
            AnecdoteToast.clear();
            pollTicks = 0;
            revision++;
        }
        if (game.getConnection() != null && game.player != null && ++pollTicks >= 200) {
            pollTicks = 0;
            request();
        }
        QuestNetwork.MailboxNoticePayload notice;
        while ((notice = QuestClientInbox.takeMailboxNotice()) != null)
            MailArrivalToast.enqueue(notice);
        var snapshot = QuestClientInbox.takeMailbox();
        if (snapshot != null) {
            snapshotNanos = System.nanoTime();
            snapshotGameTicks = game.level == null ? 0 : game.level.getGameTime();
            if (!messages.equals(snapshot.messages())) {
                messages = snapshot.messages();
                revision++;
            }
        }
        var templateCatalog = QuestClientInbox.takeMailTemplates();
        if (templateCatalog != null) {
            templates = templateCatalog.templates();
            if (game.screen instanceof QuestValuePickerScreen picker) picker.refreshOptions();
        }
        QuestClientInbox.takeMailboxActionResults().forEach((id, result) -> {
            var future = pending.remove(id);
            deadlines.remove(id);
            if (future != null) {
                future.complete(result);
            }
        });
        for (Long id : List.copyOf(deadlines.keySet())) {
            if (System.nanoTime() >= deadlines.get(id)) {
                var future = pending.remove(id);
                deadlines.remove(id);
                if (future != null) {
                    future.complete(new QuestNetwork.MailboxActionResultPayload(id, "timeout"));
                }
            }
        }
    }

    public static void request() {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        try {
            PacketDistributor.sendToServer(new QuestNetwork.MailboxRequestPayload());
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to request mailbox", exception);
        }
    }

    public static List<QuestNetwork.MailTemplateEntry> templates() {
        return templates;
    }

    public static void requestTemplates() {
        if (Minecraft.getInstance().getConnection() == null) return;
        try {
            PacketDistributor.sendToServer(new QuestNetwork.MailboxTemplateCatalogRequestPayload());
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to request mail template catalog", exception);
        }
    }

    public static CompletableFuture<String> action(String mailId, int action) {
        return actionWithReceipt(mailId, action).thenApply(QuestNetwork.MailboxActionResultPayload::result);
    }

    public static CompletableFuture<QuestNetwork.MailboxActionResultPayload> actionWithReceipt(String mailId, int action) {
        if (Minecraft.getInstance().getConnection() == null || pending.size() >= 8) {
            return CompletableFuture.completedFuture(
                    new QuestNetwork.MailboxActionResultPayload(0,
                            Minecraft.getInstance().getConnection() == null ? "remote_unavailable" : "busy"));
        }
        long requestId = ++nextRequestId;
        CompletableFuture<QuestNetwork.MailboxActionResultPayload> result = new CompletableFuture<>();
        pending.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new QuestNetwork.MailboxActionPayload(
                    requestId, mailId == null ? "" : mailId, action));
        } catch (Exception exception) {
            pending.remove(requestId);
            deadlines.remove(requestId);
            XeroQuestJournal.LOGGER.warn("Unable to send mailbox action", exception);
            result.complete(new QuestNetwork.MailboxActionResultPayload(requestId, "remote_unavailable"));
        }
        return result;
    }

    public static CompletableFuture<String> send(String recipient, String id, String sender,
                                                   String subject, String body, String icon, boolean treasured,
                                                   List<MailboxRuntime.Attachment> attachments) {
        return send(recipient, id, sender, subject, body, icon, treasured, 0, false, attachments);
    }

    public static CompletableFuture<String> send(String recipient, String id, String sender,
                                                 String subject, String body, String icon, boolean treasured,
                                                 long expiresIn, boolean gameTime,
                                                 List<MailboxRuntime.Attachment> attachments) {
        return send(recipient, id, sender, subject, body, icon, treasured, expiresIn, gameTime,
                attachments, "", "");
    }

    public static CompletableFuture<String> send(String recipient, String id, String sender,
                                                 String subject, String body, String icon, boolean treasured,
                                                 long expiresIn, boolean gameTime,
                                                 List<MailboxRuntime.Attachment> attachments,
                                                 String banner, String targetTaskId) {
        return send(recipient, id, sender, subject, body, icon, treasured, expiresIn, gameTime, attachments,
                banner, targetTaskId, MailBannerTiming.NONE);
    }

    public static CompletableFuture<String> send(String recipient, String id, String sender,
                                                 String subject, String body, String icon, boolean treasured,
                                                 long expiresIn, boolean gameTime, List<MailboxRuntime.Attachment> attachments,
                                                 String banner, String targetTaskId, MailBannerTiming timing) {
        if (Minecraft.getInstance().getConnection() == null || pending.size() >= 8)
            return CompletableFuture.completedFuture(
                    Minecraft.getInstance().getConnection() == null ? "remote_unavailable" : "busy");
        long requestId = ++nextRequestId;
        CompletableFuture<QuestNetwork.MailboxActionResultPayload> result = new CompletableFuture<>();
        pending.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new QuestNetwork.MailboxSendPayload(requestId,
                    recipient, id, sender, subject, body, icon, treasured, expiresIn, gameTime, attachments,
                    banner, targetTaskId, timing));
        } catch (Exception exception) {
            pending.remove(requestId);
            deadlines.remove(requestId);
            XeroQuestJournal.LOGGER.warn("Unable to send mail", exception);
            result.complete(new QuestNetwork.MailboxActionResultPayload(requestId, "remote_unavailable"));
        }
        return result.thenApply(QuestNetwork.MailboxActionResultPayload::result);
    }

    public static CompletableFuture<String> saveTemplate(String id, String sender, String subject,
                                                          String body, String icon, boolean treasured,
                                                          List<MailboxRuntime.Attachment> attachments) {
        return saveTemplate(id, sender, subject, body, icon, treasured, 0, false, attachments);
    }

    public static CompletableFuture<String> saveTemplate(String id, String sender, String subject,
                                                         String body, String icon, boolean treasured,
                                                         long expiresIn, boolean gameTime,
                                                         List<MailboxRuntime.Attachment> attachments) {
        return saveTemplate(id, sender, subject, body, icon, treasured, expiresIn, gameTime,
                attachments, "", "");
    }

    public static CompletableFuture<String> saveTemplate(String id, String sender, String subject,
                                                         String body, String icon, boolean treasured,
                                                         long expiresIn, boolean gameTime,
                                                         List<MailboxRuntime.Attachment> attachments,
                                                         String banner, String targetTaskId) {
        return saveTemplate(id, sender, subject, body, icon, treasured, expiresIn, gameTime, attachments,
                banner, targetTaskId, MailBannerTiming.NONE);
    }

    public static CompletableFuture<String> saveTemplate(String id, String sender, String subject,
                                                         String body, String icon, boolean treasured,
                                                         long expiresIn, boolean gameTime,
                                                         List<MailboxRuntime.Attachment> attachments,
                                                         String banner, String targetTaskId, MailBannerTiming timing) {
        if (Minecraft.getInstance().getConnection() == null || pending.size() >= 8)
            return CompletableFuture.completedFuture(
                    Minecraft.getInstance().getConnection() == null ? "remote_unavailable" : "busy");
        long requestId = ++nextRequestId;
        CompletableFuture<QuestNetwork.MailboxActionResultPayload> result = new CompletableFuture<>();
        pending.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            var mail = new QuestNetwork.MailboxSendPayload(requestId, "", id, sender, subject, body,
                    icon, treasured, expiresIn, gameTime, attachments, banner, targetTaskId, timing);
            PacketDistributor.sendToServer(new QuestNetwork.MailboxTemplateSavePayload(mail));
        } catch (Exception exception) {
            pending.remove(requestId);
            deadlines.remove(requestId);
            XeroQuestJournal.LOGGER.warn("Unable to save mail template", exception);
            result.complete(new QuestNetwork.MailboxActionResultPayload(requestId, "remote_unavailable"));
        }
        return result.thenApply(QuestNetwork.MailboxActionResultPayload::result);
    }

    private static CompletableFuture<QuestNetwork.MailboxActionResultPayload> requestAction(
            java.util.function.LongFunction<net.minecraft.network.protocol.common.custom.CustomPacketPayload> payload) {
        if (Minecraft.getInstance().getConnection() == null || pending.size() >= 8)
            return CompletableFuture.completedFuture(new QuestNetwork.MailboxActionResultPayload(0,
                    Minecraft.getInstance().getConnection() == null ? "remote_unavailable" : "busy"));
        long id = ++nextRequestId;
        var result = new CompletableFuture<QuestNetwork.MailboxActionResultPayload>();
        pending.put(id, result);
        deadlines.put(id, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(payload.apply(id));
        } catch (Exception failure) {
            pending.remove(id);
            deadlines.remove(id);
            result.complete(new QuestNetwork.MailboxActionResultPayload(id, "remote_unavailable"));
            XeroQuestJournal.LOGGER.warn("Unable to send mailbox request", failure);
        }
        return result;
    }

    public static CompletableFuture<QuestNetwork.MailboxActionResultPayload> claimChoices(
            List<QuestNetwork.MailboxClaimEntry> entries, boolean allowDrop) {
        return requestAction(id -> new QuestNetwork.MailboxClaimPayload(id, entries, allowDrop));
    }

    public static CompletableFuture<QuestNetwork.MailboxActionResultPayload> deleteMany(Map<String, String> hashes) {
        return requestAction(id -> new QuestNetwork.MailboxDeletePayload(id, hashes));
    }

    public static CompletableFuture<String> edit(MailboxRuntime.View original, String sender, String subject,
            String body, String icon, boolean treasured, long expiresIn, boolean gameTime,
            List<MailboxRuntime.Attachment> attachments, boolean preserveExpiry) {
        return edit(original, sender, subject, body, icon, treasured, expiresIn, gameTime,
                attachments, preserveExpiry, original.banner(), original.targetTaskId());
    }

    public static CompletableFuture<String> edit(MailboxRuntime.View original, String sender, String subject,
            String body, String icon, boolean treasured, long expiresIn, boolean gameTime,
            List<MailboxRuntime.Attachment> attachments, boolean preserveExpiry,
            String banner, String targetTaskId) {
        return edit(original, sender, subject, body, icon, treasured, expiresIn, gameTime, attachments,
                preserveExpiry, banner, targetTaskId, original.bannerTiming());
    }

    public static CompletableFuture<String> edit(MailboxRuntime.View original, String sender, String subject,
            String body, String icon, boolean treasured, long expiresIn, boolean gameTime,
            List<MailboxRuntime.Attachment> attachments, boolean preserveExpiry,
            String banner, String targetTaskId, MailBannerTiming timing) {
        return requestAction(id -> new QuestNetwork.MailboxEditPayload(new QuestNetwork.MailboxSendPayload(
                id, original.recipient(), original.id(), sender, subject, body, icon, treasured, expiresIn, gameTime,
                attachments, banner, targetTaskId, timing), MailboxRuntime.fingerprint(original), preserveExpiry))
                .thenApply(QuestNetwork.MailboxActionResultPayload::result);
    }

    public static CompletableFuture<String> resend(MailboxRuntime.View original, String recipient,
            String mailId, String sender, String subject, String body, String icon, boolean treasured,
            long expiresIn, boolean gameTime, List<MailboxRuntime.Attachment> attachments) {
        return resend(original, recipient, mailId, sender, subject, body, icon, treasured,
                expiresIn, gameTime, attachments, original.banner(), original.targetTaskId());
    }

    public static CompletableFuture<String> resend(MailboxRuntime.View original, String recipient,
            String mailId, String sender, String subject, String body, String icon, boolean treasured,
            long expiresIn, boolean gameTime, List<MailboxRuntime.Attachment> attachments,
            String banner, String targetTaskId) {
        return resend(original, recipient, mailId, sender, subject, body, icon, treasured, expiresIn, gameTime,
                attachments, banner, targetTaskId, original.bannerTiming());
    }

    public static CompletableFuture<String> resend(MailboxRuntime.View original, String recipient,
            String mailId, String sender, String subject, String body, String icon, boolean treasured,
            long expiresIn, boolean gameTime, List<MailboxRuntime.Attachment> attachments,
            String banner, String targetTaskId, MailBannerTiming timing) {
        return requestAction(id -> new QuestNetwork.MailboxResendPayload(new QuestNetwork.MailboxSendPayload(
                id, recipient, mailId, sender, subject, body, icon, treasured, expiresIn, gameTime, attachments,
                banner, targetTaskId, timing),
                original.id(), MailboxRuntime.fingerprint(original)))
                .thenApply(QuestNetwork.MailboxActionResultPayload::result);
    }
}
