package xtdpotato.xero_quest_journal.core.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Side-neutral mailbox populated by client-bound payload handlers.
 * Keeping Minecraft client classes out of the network registration makes the
 * same payload table safe to load on a dedicated server.
 */
public final class QuestClientInbox {
    public record CatalogData(long revision, String json) {}

    private static QuestNetwork.ProgressSnapshotPayload snapshot;
    private static List<QuestNetwork.ProgressSnapshotPayload> snapshotChunks;
    private static CatalogData catalog;
    private static long catalogAssemblyRevision = -1;
    private static String[] catalogChunks;
    private static QuestNetwork.MailboxSnapshotPayload mailbox;
    private static QuestNetwork.MailboxTemplateCatalogPayload mailTemplates;
    private static final java.util.ArrayDeque<QuestNetwork.MailboxNoticePayload> mailboxNotices =
            new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<QuestNetwork.ToastPayload> toasts =
            new java.util.ArrayDeque<>();
    private static QuestNetwork.OpenScreenPayload openScreen;
    private static QuestUiThemeNetwork.State uiTheme;
    private static QuestDailyNetwork.State daily;
    private static final java.util.ArrayDeque<QuestNetwork.AutomaticClaimPayload> automaticClaims =
            new java.util.ArrayDeque<>();
    private static final Map<Long, String> actionResults = new LinkedHashMap<>();
    private static final Map<Long, QuestNetwork.MailboxActionResultPayload> mailboxActionResults = new LinkedHashMap<>();

    private QuestClientInbox() {
    }

    public static synchronized void accept(QuestNetwork.ProgressSnapshotPayload payload) {
        if (payload.chunkCount() == 1) {
            snapshot = payload;
            snapshotChunks = null;
            return;
        }
        if (payload.chunkIndex() == 0 || snapshotChunks == null
                || snapshotChunks.size() != payload.chunkCount()) {
            snapshotChunks = new java.util.ArrayList<>(
                    java.util.Collections.nCopies(payload.chunkCount(), null));
        }
        snapshotChunks.set(payload.chunkIndex(), payload);
        if (snapshotChunks.stream().anyMatch(java.util.Objects::isNull)) return;
        Map<String, xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.View> views = new LinkedHashMap<>();
        for (var chunk : snapshotChunks) views.putAll(chunk.views());
        snapshot = new QuestNetwork.ProgressSnapshotPayload(payload.taskPagination(), views, payload.tracked(),
                payload.focusGroup());
        snapshotChunks = null;
    }

    public static synchronized void accept(QuestNetwork.ActionResultPayload payload) {
        actionResults.put(payload.requestId(), payload.result());
        while (actionResults.size() > 64) actionResults.remove(actionResults.keySet().iterator().next());
    }

    public static synchronized void accept(QuestNetwork.AutomaticClaimPayload payload) {
        if (!payload.questId().isBlank() && automaticClaims.stream()
                .anyMatch(existing -> existing.questId().equals(payload.questId()))) {
            return;
        }
        if (automaticClaims.size() >= 64) automaticClaims.removeFirst();
        automaticClaims.addLast(payload);
    }

    public static synchronized QuestNetwork.AutomaticClaimPayload takeAutomaticClaim() {
        return automaticClaims.pollFirst();
    }

    public static synchronized void discardAutomaticClaims(String questId) {
        if (questId == null || questId.isBlank()) return;
        automaticClaims.removeIf(payload -> payload.questId().equals(questId));
    }

    public static synchronized void accept(QuestNetwork.CatalogPayload payload) {
        if (payload.chunkCount() == 1) {
            catalog = new CatalogData(payload.revision(), payload.json());
            catalogAssemblyRevision = -1;
            catalogChunks = null;
            return;
        }
        if (catalogAssemblyRevision != payload.revision()
                || catalogChunks == null || catalogChunks.length != payload.chunkCount()) {
            catalogAssemblyRevision = payload.revision();
            catalogChunks = new String[payload.chunkCount()];
        }
        catalogChunks[payload.chunkIndex()] = payload.json();
        for (String chunk : catalogChunks) {
            if (chunk == null) return;
        }
        StringBuilder json = new StringBuilder();
        for (String chunk : catalogChunks) json.append(chunk);
        catalog = new CatalogData(payload.revision(), json.toString());
        catalogAssemblyRevision = -1;
        catalogChunks = null;
    }

    public static synchronized void accept(QuestNetwork.MailboxSnapshotPayload payload) {
        mailbox = payload;
    }

    public static synchronized void accept(QuestNetwork.MailboxTemplateCatalogPayload payload) {
        mailTemplates = payload;
    }

    public static synchronized void accept(QuestNetwork.MailboxNoticePayload payload) {
        if (mailboxNotices.size() >= 16) mailboxNotices.removeFirst();
        mailboxNotices.addLast(payload);
    }

    public static synchronized QuestNetwork.MailboxNoticePayload takeMailboxNotice() {
        return mailboxNotices.pollFirst();
    }

    public static synchronized void accept(QuestNetwork.ToastPayload payload) {
        if (toasts.size() >= 16) toasts.removeFirst();
        toasts.addLast(payload);
    }

    public static synchronized QuestNetwork.ToastPayload takeToast() {
        return toasts.pollFirst();
    }

    public static synchronized void accept(QuestNetwork.MailboxActionResultPayload payload) {
        mailboxActionResults.put(payload.requestId(), payload);
        while (mailboxActionResults.size() > 64) {
            mailboxActionResults.remove(mailboxActionResults.keySet().iterator().next());
        }
    }

    public static synchronized void accept(QuestNetwork.OpenScreenPayload payload) {
        openScreen = payload;
    }

    public static synchronized void accept(QuestDailyNetwork.State payload) { daily = payload; }
    public static synchronized QuestDailyNetwork.State takeDaily() {
        var next = daily;
        daily = null;
        return next;
    }

    public static synchronized void accept(QuestUiThemeNetwork.State payload) { uiTheme = payload; }
    public static synchronized QuestUiThemeNetwork.State takeUiTheme() {
        var next = uiTheme;
        uiTheme = null;
        return next;
    }

    public static synchronized CatalogData takeCatalog() {
        var next = catalog;
        catalog = null;
        return next;
    }
    public static synchronized QuestNetwork.ProgressSnapshotPayload takeSnapshot() {
        QuestNetwork.ProgressSnapshotPayload next = snapshot;
        snapshot = null;
        return next;
    }

    public static synchronized QuestNetwork.MailboxSnapshotPayload takeMailbox() {
        var next = mailbox;
        mailbox = null;
        mailboxNotices.clear();
        return next;
    }

    public static synchronized QuestNetwork.MailboxTemplateCatalogPayload takeMailTemplates() {
        var next = mailTemplates;
        mailTemplates = null;
        return next;
    }

    public static synchronized Map<Long, String> takeActionResults() {
        Map<Long, String> next = Map.copyOf(actionResults);
        actionResults.clear();
        return next;
    }

    public static synchronized Map<Long, QuestNetwork.MailboxActionResultPayload> takeMailboxActionResults() {
        Map<Long, QuestNetwork.MailboxActionResultPayload> next = Map.copyOf(mailboxActionResults);
        mailboxActionResults.clear();
        return next;
    }

    public static synchronized QuestNetwork.OpenScreenPayload takeOpenScreen() {
        var next = openScreen;
        openScreen = null;
        return next;
    }

    public static synchronized void clear() {
        uiTheme = null;
        daily = null;
        snapshot = null;
        snapshotChunks = null;
        catalog = null;
        catalogAssemblyRevision = -1;
        catalogChunks = null;
        mailbox = null;
        mailTemplates = null;
        openScreen = null;
        toasts.clear();
        automaticClaims.clear();
        actionResults.clear();
        mailboxActionResults.clear();
    }
}
