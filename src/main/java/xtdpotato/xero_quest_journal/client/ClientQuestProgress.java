package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.network.QuestClientInbox;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Client cache backed by NeoForge payloads for both local and remote servers. */
public final class ClientQuestProgress {
    private static Map<String, QuestRuntime.View> views = Map.of();
    private static List<String> tracked = List.of();
    private static String focusGroup = "";
    private static boolean taskPagination;
    private static int tasksPerPage = 50;
    private static boolean receivedSnapshot;
    private static boolean unreadTasks;
    private static final Map<Long, CompletableFuture<String>> pendingActions = new LinkedHashMap<>();
    private static Object connection;
    private static int ticks;
    private static long revision;
    private static long listRevision;
    private static long nextRequestId;
    private static QuestCatalog catalog = QuestCatalog.empty();
    private static long catalogRevision = -1;
    private static String syncError;
    private static xtdpotato.xero_quest_journal.core.model.QuestUiTheme uiTheme =
            xtdpotato.xero_quest_journal.core.model.QuestUiTheme.empty();
    private static long themeRevision;
    private static String overviewImage = "";
    public static String overviewImage() {
        String source = overviewImage.isBlank() ? uiTheme().overviewImage() : overviewImage;
        return source.isBlank()
                ? xtdpotato.xero_quest_journal.core.model.QuestUiTheme.DEFAULT_OVERVIEW_IMAGE : source;
    }
    private static final Map<Long, Long> deadlines = new LinkedHashMap<>();
    private static final Map<String, CompletionNotice> completionNotices = new LinkedHashMap<>();
    private static final Map<String, Long> stepNotices = new LinkedHashMap<>();
    private static xtdpotato.xero_quest_journal.core.network.QuestDailyNetwork.State daily =
            xtdpotato.xero_quest_journal.core.network.QuestDailyNetwork.State.empty();
    private static long dailyClockOffset;

    public record CompletionNotice(String questId, long startedAt, long expiresAt) {
    }

    /** Today's commission roll as last reported by the server. */
    public static xtdpotato.xero_quest_journal.core.network.QuestDailyNetwork.State daily() {
        return daily;
    }

    public static boolean isDailyToday(String questId) {
        return daily.enabled() && daily.quests().contains(questId);
    }

    public static int dailyCompleted() {
        int done = 0;
        for (String id : daily.quests()) {
            var view = views.get(id);
            if (view != null && view.completed()) done++;
        }
        return done;
    }

    public static boolean dailyBonusReady() {
        return daily.enabled() && !daily.quests().isEmpty() && !daily.bonusClaimed()
                && dailyCompleted() >= daily.quests().size();
    }

    /** Milliseconds until the next commission reset, corrected for client/server clock skew. */
    public static long dailyRemainingMillis() {
        if (daily.resetAt() <= 0) return 0;
        return Math.max(0, daily.resetAt() - (System.currentTimeMillis() + dailyClockOffset));
    }

    public static CompletableFuture<String> claimDailyBonus() {
        return sendAction("daily", QuestNetwork.ActionPayload.DAILY_BONUS);
    }

    /** Nano time at which this task last advanced to a new step, or 0. */
    public static long stepNoticeStarted(String questId) {
        return stepNotices.getOrDefault(questId, 0L);
    }

    private ClientQuestProgress() {
    }

    public static QuestRuntime.View view(String id) {
        return views.get(id);
    }

    public static long revision() {
        return revision;
    }

    /** Count-only progress updates repaint details without rebuilding thousands of rows. */
    public static long listRevision() { return listRevision; }

    private static boolean sameListState(QuestRuntime.View before, QuestRuntime.View after) {
        return before != null && before.status().equals(after.status())
                && before.hidden() == after.hidden() && before.triggersEnabled() == after.triggersEnabled()
                && before.focused() == after.focused() && before.step() == after.step()
                && before.stepCount() == after.stepCount() && before.rewards().equals(after.rewards());
    }

    public static List<String> trackedIds() {
        return tracked;
    }

    public static boolean hasSnapshot() { return receivedSnapshot; }
    public static boolean hasUnreadTasks() { return unreadTasks; }

    public static String focusedId() {
        return views.entrySet().stream().filter(entry -> entry.getValue().focused())
                .map(Map.Entry::getKey).findFirst().orElse("");
    }

    public static String focusGroupId() {
        return focusGroup;
    }

    public static boolean focusCanSelect(String questId) {
        String group = focusGroupId();
        return group.isBlank() || ClientQuestDefinitions.groupOf(questId).equals(group);
    }

    public static boolean focusAllows(String id) {
        String focus = focusedId();
        return focus.isBlank() || focus.equals(id);
    }

    public static boolean focusRewardAllows(String id) {
        String group = focusGroupId();
        return group.isBlank() || ClientQuestDefinitions.groupOf(id).equals(group);
    }

    public static CompletableFuture<String> toggleFocus(String id) {
        return sendAction(id, QuestNetwork.ActionPayload.FOCUS);
    }

    public static boolean isTracked(String id) {
        return tracked.contains(id);
    }

    public static boolean taskPagination() {
        return Minecraft.getInstance().getSingleplayerServer() != null
                ? QuestConfig.defaults().taskPagination : taskPagination;
    }
    public static int tasksPerPage() {
        return Math.clamp(Minecraft.getInstance().getSingleplayerServer() != null
                ? QuestConfig.defaults().tasksPerPage : tasksPerPage, 10, 1000);
    }

    public static QuestCatalog catalog() { return catalog; }
    public static long catalogRevision() { return catalogRevision; }
    public static String syncError() { return syncError; }
    public static xtdpotato.xero_quest_journal.core.model.QuestUiTheme uiTheme() {
        return Minecraft.getInstance().getSingleplayerServer() != null ? QuestConfig.defaults().uiTheme : uiTheme;
    }
    public static long themeRevision() { return themeRevision; }
    public static List<CompletionNotice> completionNotices() {
        return List.copyOf(completionNotices.values());
    }

    public static void tick() {
        Minecraft game = Minecraft.getInstance();
        if (connection != game.getConnection()) {
            connection = game.getConnection();
            QuestCompletionToast.clear();
            QuestHudRenderer.clearCoordinateGuidance();
            QuestUnlockToast.clear();
            ItemPickupToast.clear();
            ClientEditMode.reset();
            views = Map.of();
            tracked = List.of();
            focusGroup = "";
            taskPagination = false;
            tasksPerPage = 50;
            receivedSnapshot = false;
            unreadTasks = false;
            pendingActions.values().forEach(future -> future.complete("remote_unavailable"));
            pendingActions.clear();
            deadlines.clear();
            catalog = QuestCatalog.empty();
            catalogRevision = -1;
            uiTheme = xtdpotato.xero_quest_journal.core.model.QuestUiTheme.empty();
            overviewImage = "";
            themeRevision++;
            syncError = null;
            completionNotices.clear();
            stepNotices.clear();
            daily = xtdpotato.xero_quest_journal.core.network.QuestDailyNetwork.State.empty();
            dailyClockOffset = 0;
            QuestClientInbox.clear();
            revision++;
            listRevision++;
            ticks = 0;
        }
        if (connection == null || game.player == null) {
            return;
        }
        var themeState = QuestClientInbox.takeUiTheme();
        if (themeState != null) {
            try {
                var parsed = new com.google.gson.Gson().fromJson(themeState.json(),
                        xtdpotato.xero_quest_journal.core.model.QuestUiTheme.class);
                var next = new xtdpotato.xero_quest_journal.core.model.QuestUiTheme(parsed.colors(), parsed.overviewImage(),
                        parsed.journeyAppearance());
                xtdpotato.xero_quest_journal.core.model.QuestUiTheme.validateOverviewImage(themeState.overviewImage());
                uiTheme = next;
                overviewImage = themeState.overviewImage();
                themeRevision++;
                listRevision++;
            } catch (RuntimeException invalid) {
                XeroQuestJournal.LOGGER.warn("Invalid server UI theme", invalid);
            }
        }
        var dailyState = QuestClientInbox.takeDaily();
        if (dailyState != null) {
            daily = dailyState;
            dailyClockOffset = dailyState.serverNow() - System.currentTimeMillis();
            revision++;
            listRevision++;
        }
        var definitions = QuestClientInbox.takeCatalog();
        if (definitions != null) {
            try {
                catalog = QuestCatalog.decode(definitions.json());
                catalogRevision = definitions.revision();
                syncError = null;
                revision++;
                listRevision++;
            } catch (Exception invalid) {
                syncError = "unavailable";
                XeroQuestJournal.LOGGER.warn("Invalid server quest catalog", invalid);
            }
        }
        var snapshot = QuestClientInbox.takeSnapshot();
        if (snapshot != null && (!receivedSnapshot || !views.equals(snapshot.views()) || !tracked.equals(snapshot.tracked())
                || taskPagination != snapshot.taskPagination() || tasksPerPage != snapshot.tasksPerPage()
                || !focusGroup.equals(snapshot.focusGroup()))) {
            boolean hadSnapshot = receivedSnapshot;
            boolean structuralChange = !hadSnapshot || !tracked.equals(snapshot.tracked())
                    || taskPagination != snapshot.taskPagination() || tasksPerPage != snapshot.tasksPerPage()
                    || !focusGroup.equals(snapshot.focusGroup())
                    || !views.keySet().equals(snapshot.views().keySet())
                    || snapshot.views().entrySet().stream()
                    .anyMatch(entry -> !sameListState(views.get(entry.getKey()), entry.getValue()));
            receivedSnapshot = true;
            if (hadSnapshot) {
                long now = System.nanoTime();
                snapshot.views().forEach((id, after) -> {
                    QuestRuntime.View before = views.get(id);
                    if (before != null && !before.completed() && after.completed()) {
                        completionNotices.put(id, new CompletionNotice(id, now, now + 4_200_000_000L));
                        QuestCompletionToast.enqueue(id);
                    } else if (before != null && !after.completed() && after.stepCount() > 0
                            && after.step() > before.step()) {
                        stepNotices.put(id, now);
                    }
                    if ((before == null || !before.unlocked()) && after.unlocked()
                            && !after.completed() && !after.hidden()) {
                        QuestUnlockToast.enqueue(id);
                    }
                });
            }
            views = snapshot.views();
            unreadTasks = views.values().stream().anyMatch(view -> !view.hidden()
                    && !view.status().equals("claimed") && (view.newTask() || view.status().equals("complete")));
            tracked = snapshot.tracked();
            taskPagination = snapshot.taskPagination();
            tasksPerPage = snapshot.tasksPerPage();
            focusGroup = snapshot.focusGroup();
            revision++;
            if (structuralChange) listRevision++;
        }
        completionNotices.values().removeIf(notice -> System.nanoTime() >= notice.expiresAt());
        stepNotices.values().removeIf(started -> System.nanoTime() - started >= 4_000_000_000L);
        QuestClientInbox.takeActionResults().forEach((id, result) -> {
            if (id == 0) syncError = result;
            CompletableFuture<String> pending = pendingActions.remove(id);
            deadlines.remove(id);
            if (pending != null) {
                pending.complete(result);
            }
        });
        for (Long id : List.copyOf(deadlines.keySet())) {
            if (System.nanoTime() >= deadlines.get(id)) {
                var pending = pendingActions.remove(id);
                deadlines.remove(id);
                if (pending != null) pending.complete("timeout");
            }
        }
        if (ticks++ % 10 == 0) {
            try {
                PacketDistributor.sendToServer(new QuestNetwork.ProgressRequestPayload(catalogRevision));
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to request task progress", exception);
            }
        }
    }

    public static CompletableFuture<String> action(String id, boolean claim) {
        return sendAction(id, claim ? QuestNetwork.ActionPayload.CLAIM : QuestNetwork.ActionPayload.ACTIVATE);
    }

    public static CompletableFuture<String> claimOverflow(String id) {
        return sendAction(id, QuestNetwork.ActionPayload.CLAIM_DROP);
    }

    public static CompletableFuture<String> claimAll() {
        return sendAction("", QuestNetwork.ActionPayload.CLAIM_ALL);
    }

    public static CompletableFuture<String> claimChoices(String questId, Map<Integer, Integer> selections) {
        return claimChoices(questId, selections, false);
    }

    public static CompletableFuture<String> claimChoices(String questId, Map<Integer, Integer> selections,
                                                        boolean allowDrop) {
        if (Minecraft.getInstance().getConnection() == null || questId == null || questId.isBlank()) {
            return CompletableFuture.completedFuture("remote_unavailable");
        }
        if (catalogRevision < 0) return CompletableFuture.completedFuture("definition_changed");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        CompletableFuture<String> result = new CompletableFuture<>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new QuestNetwork.ClaimChoicePayload(requestId, questId,
                    catalogRevision, selections, allowDrop));
        } catch (Exception exception) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            XeroQuestJournal.LOGGER.warn("Unable to send loot choice", exception);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> toggleTracking(String id) {
        return sendAction(id, QuestNetwork.ActionPayload.TRACK);
    }

    public static CompletableFuture<String> markTaskRead(String id) {
        return sendAction(id, QuestNetwork.ActionPayload.MARK_READ);
    }

    public static CompletableFuture<String> markAllTasksRead() {
        return sendAction("", QuestNetwork.ActionPayload.MARK_ALL_READ);
    }

    public static CompletableFuture<String> setTaskPagination(boolean enabled) {
        if (Minecraft.getInstance().getSingleplayerServer() != null) {
            QuestConfig.defaults().taskPagination = enabled;
            return CompletableFuture.completedFuture(QuestConfig.save() ? "config_saved" : "config_save_failed");
        }
        if (Minecraft.getInstance().getConnection() == null) {
            return CompletableFuture.completedFuture("remote_unavailable");
        }
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        CompletableFuture<String> result = new CompletableFuture<>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new QuestNetwork.ConfigPayload(requestId, "task_pagination", enabled));
        } catch (Exception exception) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> setTasksPerPage(int value) {
        var game = Minecraft.getInstance();
        if (value < 10 || value > 1000) return CompletableFuture.completedFuture("invalid_config");
        if (game.getConnection() == null) return CompletableFuture.completedFuture("remote_unavailable");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        var result = new CompletableFuture<String>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try { PacketDistributor.sendToServer(new QuestNetwork.PageSizePayload(requestId, value)); }
        catch (RuntimeException failure) {
            pendingActions.remove(requestId); deadlines.remove(requestId); result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> setUiTheme(
            xtdpotato.xero_quest_journal.core.model.QuestUiTheme theme) {
        if (Minecraft.getInstance().getConnection() == null) return CompletableFuture.completedFuture("remote_unavailable");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        CompletableFuture<String> result = new CompletableFuture<>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new xtdpotato.xero_quest_journal.core.network.QuestUiThemeNetwork.Update(
                    requestId, new com.google.gson.Gson().toJson(theme)));
        } catch (RuntimeException failure) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> saveJourney(QuestCatalog.Chapter chapter,
            xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork.Draft draft) {
        if (Minecraft.getInstance().getConnection() == null) return CompletableFuture.completedFuture("remote_unavailable");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long id = ++nextRequestId;
        var result = new CompletableFuture<String>();
        pendingActions.put(id, result);
        deadlines.put(id, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork.Update(
                    id, chapter.id(), chapter.fingerprint(), new com.google.gson.Gson().toJson(draft)));
        } catch (RuntimeException failure) {
            pendingActions.remove(id); deadlines.remove(id); result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> journeyHistory(boolean redo) {
        if (Minecraft.getInstance().getConnection() == null)
            return CompletableFuture.completedFuture("remote_unavailable");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long id = ++nextRequestId;
        var result = new CompletableFuture<String>();
        pendingActions.put(id, result);
        deadlines.put(id, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork.History(
                    id, redo));
        } catch (RuntimeException failure) {
            pendingActions.remove(id);
            deadlines.remove(id);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> reset(String id) {
        return sendAction(id, QuestNetwork.ActionPayload.RESET);
    }

    public static CompletableFuture<String> resetMany(java.util.Collection<String> ids) {
        var questIds = List.copyOf(new java.util.LinkedHashSet<>(ids));
        if (Minecraft.getInstance().getConnection() == null || questIds.isEmpty()
                || questIds.size() > QuestNetwork.ResetBatchPayload.MAX_RESET_IDS)
            return CompletableFuture.completedFuture("remote_unavailable");
        if (catalogRevision < 0) return CompletableFuture.completedFuture("definition_changed");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        CompletableFuture<String> result = new CompletableFuture<>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new QuestNetwork.ResetBatchPayload(requestId, catalogRevision, questIds));
        } catch (Exception exception) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            XeroQuestJournal.LOGGER.warn("Unable to send batch task reset", exception);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> moveJournalEntry(String kind, String id, String neighbor, int direction) {
        var game = Minecraft.getInstance();
        if (!ClientEditMode.enabled() || game.player == null || !game.player.hasPermissions(2))
            return CompletableFuture.completedFuture("permission");
        if (game.getConnection() == null) return CompletableFuture.completedFuture("remote_unavailable");
        if (catalogRevision < 0) return CompletableFuture.completedFuture("definition_changed");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        var result = new CompletableFuture<String>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(
                    new xtdpotato.xero_quest_journal.core.network.QuestJournalOrderNetwork.Move(
                            requestId, ClientQuestDefinitions.revision(), kind, id, neighbor, direction));
        } catch (RuntimeException failure) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> changeTemplateLibrary(String kind, String id, String target, boolean after) {
        if (Minecraft.getInstance().getConnection() == null) return CompletableFuture.completedFuture("remote_unavailable");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long request = ++nextRequestId;
        var result = new CompletableFuture<String>();
        pendingActions.put(request, result);
        deadlines.put(request, System.nanoTime() + 15_000_000_000L);
        try { PacketDistributor.sendToServer(new xtdpotato.xero_quest_journal.core.network.TemplateLibraryNetwork.Change(
                request, ClientQuestDefinitions.revision(), kind, id, target, after)); }
        catch (RuntimeException failure) { pendingActions.remove(request); deadlines.remove(request); result.complete("remote_unavailable"); }
        return result;
    }
    public static CompletableFuture<String> chooseGenerationMode(String mode, String namespace, boolean recipes) {
        if (Minecraft.getInstance().getConnection() == null) return CompletableFuture.completedFuture("remote_unavailable");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long request = ++nextRequestId;
        var result = new CompletableFuture<String>(); pendingActions.put(request, result);
        deadlines.put(request, System.nanoTime() + 15_000_000_000L);
        try { PacketDistributor.sendToServer(new xtdpotato.xero_quest_journal.core.network.QuestGenerationNetwork.Choose(request, mode, namespace, recipes)); }
        catch (RuntimeException failure) { pendingActions.remove(request); deadlines.remove(request); result.complete("remote_unavailable"); }
        return result;
    }

    public static CompletableFuture<String> moveJournalTasks(java.util.Collection<String> ids,
                                                             String neighbor, boolean after) {
        var taskIds = java.util.List.copyOf(new java.util.LinkedHashSet<>(ids));
        var game = Minecraft.getInstance();
        if (!ClientEditMode.enabled() || game.player == null || !game.player.hasPermissions(2))
            return CompletableFuture.completedFuture("permission");
        if (game.getConnection() == null) return CompletableFuture.completedFuture("remote_unavailable");
        if (catalogRevision < 0) return CompletableFuture.completedFuture("definition_changed");
        if (taskIds.isEmpty() || taskIds.size() > 512 || pendingActions.size() >= 8)
            return CompletableFuture.completedFuture(taskIds.isEmpty() ? "invalid_action" : "busy");
        long requestId = ++nextRequestId;
        var result = new CompletableFuture<String>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new xtdpotato.xero_quest_journal.core.network.QuestJournalOrderNetwork
                    .TaskBlockMove(requestId, ClientQuestDefinitions.revision(), taskIds, neighbor, after));
        } catch (RuntimeException failure) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static CompletableFuture<String> setLockedMany(java.util.Collection<String> ids, boolean locked) {
        var questIds = List.copyOf(new java.util.LinkedHashSet<>(ids));
        var game = Minecraft.getInstance();
        if (game.getConnection() == null || questIds.isEmpty()
                || questIds.size() > QuestNetwork.LockBatchPayload.MAX_IDS)
            return CompletableFuture.completedFuture("unavailable");
        if (!ClientEditMode.enabled() || game.player == null || !game.player.hasPermissions(2))
            return CompletableFuture.completedFuture("permission");
        if (catalogRevision < 0) return CompletableFuture.completedFuture("definition_changed");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        CompletableFuture<String> result = new CompletableFuture<>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new QuestNetwork.LockBatchPayload(
                    requestId, catalogRevision, questIds, locked));
        } catch (Exception exception) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            result.complete("remote_unavailable");
        }
        return result;
    }

    private static CompletableFuture<String> sendAction(String id, int action) {
        if (Minecraft.getInstance().getConnection() == null || id == null
                || id.isBlank() && action != QuestNetwork.ActionPayload.CLAIM_ALL
                && action != QuestNetwork.ActionPayload.MARK_ALL_READ) {
            return CompletableFuture.completedFuture("remote_unavailable");
        }
        if (catalogRevision < 0) return CompletableFuture.completedFuture("definition_changed");
        if (pendingActions.size() >= 8) return CompletableFuture.completedFuture("busy");
        long requestId = ++nextRequestId;
        CompletableFuture<String> result = new CompletableFuture<>();
        pendingActions.put(requestId, result);
        deadlines.put(requestId, System.nanoTime() + 15_000_000_000L);
        try {
            PacketDistributor.sendToServer(new QuestNetwork.ActionPayload(requestId, id, action, catalogRevision));
        } catch (Exception exception) {
            pendingActions.remove(requestId);
            deadlines.remove(requestId);
            XeroQuestJournal.LOGGER.warn("Unable to send task action", exception);
            result.complete("remote_unavailable");
        }
        return result;
    }

    public static Component status(String id) {
        var view = view(id);
        return Component.translatable("quest.xero_quest_journal.status." + (view == null ? "loading" : view.status()));
    }
}
