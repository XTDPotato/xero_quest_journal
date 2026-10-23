package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.*;

@EventBusSubscriber(modid = XeroQuestJournal.MOD_ID)
public final class QuestRuntime {
    /**
     * Player-visible task state. {@code step} is the zero-based current step of a multi-step
     * task ({@code stepCount} once complete); {@code stepCount} is 0 for ordinary tasks.
     */
    public record View(String status, int current, int required, List<QuestDefinition.Reward> rewards,
                       boolean hidden, boolean triggersEnabled, boolean focused, int step, int stepCount,
                       boolean newTask) {
        public View {
            rewards = List.copyOf(rewards);
            stepCount = Math.max(0, stepCount);
            step = Math.max(0, Math.min(step, stepCount));
        }
        public View(String status, int current, int required, List<QuestDefinition.Reward> rewards,
                    boolean hidden, boolean triggersEnabled, boolean focused, int step, int stepCount) {
            this(status, current, required, rewards, hidden, triggersEnabled, focused, step, stepCount, false);
        }
        public View(String status, int current, int required, List<QuestDefinition.Reward> rewards,
                    boolean hidden, boolean triggersEnabled, boolean focused) {
            this(status, current, required, rewards, hidden, triggersEnabled, focused, 0, 0, false);
        }
        public View(String status, int current, int required, List<QuestDefinition.Reward> rewards,
                    boolean hidden, boolean triggersEnabled) {
            this(status, current, required, rewards, hidden, triggersEnabled, false);
        }
        public View(String status, int current, int required, List<QuestDefinition.Reward> rewards) {
            this(status, current, required, rewards, false, true);
        }
        public boolean completed() { return status.equals("complete") || status.equals("claimed"); }
        public boolean unlocked() { return !status.equals("locked") && !status.equals("unavailable"); }
    }
    private QuestRuntime() {}
    public static boolean completed(ServerPlayer player, QuestDefinition quest) {
        return QuestProgressData.get(player.server).entry(player.getUUID(), quest) != null;
    }
    public static boolean allowsCompletedEvents(ServerPlayer player, QuestDefinition quest) {
        return !completed(player, quest) || quest.completionPolicy().events();
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        QuestStructureGuidance.tick(event.getServer());
        if (event.getServer().getTickCount() % 200 == 0)
            xtdpotato.xero_quest_journal.core.io.QuestGenerator.tickDailyAnecdotes(event.getServer());
        if (event.getServer().getTickCount() % 20 == 0)
            xtdpotato.xero_quest_journal.core.QuestTemplateStore.refresh();
        if (event.getServer().getTickCount() % 10 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) evaluate(player);
    }
    public static void evaluate(ServerPlayer player) {
        requireServer(player);
        QuestDaily.refresh(player);
        var data = QuestProgressData.get(player.getServer());
        String focusGroup = QuestFocus.group(player);
        String focus = QuestFocus.current(player);
        if (!focusGroup.isBlank() && focus.isBlank()) return;
        for (var quest : QuestRepository.all()) {
            if (!focusGroup.isBlank() && !QuestRepository.groupOf(quest.id()).equals(focusGroup)) continue;
            if (!focus.isBlank() && !focus.equals(quest.id())) continue;
            if (!QuestStructures.allowsProgress(player, quest)) continue;
            if (data.entry(player.getUUID(), quest) == null && triggersEnabled(data, player, quest)
                    && unlocked(data, player, quest)
                    && QuestConditions.plan(player, QuestSteps.current(data, player.getUUID(), quest), false)
                    .satisfied()) advanceOrComplete(data, player, quest, null);
            var entry = data.entry(player.getUUID(), quest);
            if (entry != null && !entry.claimed()
                    && QuestRewards.hasAutomaticPending(player, quest, entry.rewards())) {
                String claimResult = QuestRewards.claimAutomatic(player, quest);
                var automaticRewards = QuestRewards.automaticPopupRewards(quest,
                        data.entry(player.getUUID(), quest).rewards());
                if (!automaticRewards.isEmpty() && claimResult.equals("claimed")
                        && quest.completionPolicy().automaticRewardPopup()) {
                    PacketDistributor.sendToPlayer(player,
                            new xtdpotato.xero_quest_journal.core.network.QuestNetwork.AutomaticClaimPayload(
                            quest.id(), quest.title(), automaticRewards));
                }
            }
        }
    }
    public static Map<String, View> snapshot(ServerPlayer player) {
        requireServer(player);
        QuestDaily.refresh(player);
        var data = QuestProgressData.get(player.getServer());
        data.observeTaskCatalog(player.getUUID(), QuestRepository.revision());
        Map<String, View> views = new LinkedHashMap<>();
        String focus = QuestFocus.current(player);
        for (var quest : QuestRepository.all()) {
            var entry = data.entry(player.getUUID(), quest);
            String status = entry != null ? entry.claimed() ? "claimed" : "complete"
                    : !QuestSteps.valid(quest) ? "unavailable"
                    : unlocked(data, player, quest) ? "active" : "locked";
            var progress = QuestConditions.progress(player, QuestSteps.current(data, player.getUUID(), quest));
            int steps = quest.steps().size();
            views.put(quest.id(), new View(status, entry != null ? progress.required() : progress.current(),
                    progress.required(), (entry == null ? quest.rewards() : entry.rewards()).stream()
                    .map(QuestDefinition.Reward::display).toList(), hidden(data, player, quest),
                    triggersEnabled(data, player, quest), focus.equals(quest.id()),
                    entry != null ? steps : QuestSteps.index(data, player.getUUID(), quest), steps,
                    data.isNewTask(player.getUUID(), quest.id())));
        }
        return Map.copyOf(views);
    }
    public static String markTaskRead(ServerPlayer player, String id) {
        requireServer(player);
        if (QuestRepository.find(id) == null) return "missing";
        return QuestProgressData.get(player.getServer()).markTaskRead(player.getUUID(), id)
                ? "read" : "already_read";
    }
    public static String markAllTasksRead(ServerPlayer player) {
        requireServer(player);
        var data = QuestProgressData.get(player.getServer());
        data.observeTaskCatalog(player.getUUID(), QuestRepository.revision());
        return data.markAllTasksRead(player.getUUID()) ? "all_read" : "already_read";
    }
    public static List<String> tracked(ServerPlayer player) {
        requireServer(player);
        var data = QuestProgressData.get(player.getServer());
        if (!QuestFocus.group(player).isBlank()) {
            String focus = QuestFocus.current(player);
            return focus.isBlank() ? List.of() : List.of(focus);
        }
        return data.tracked(player.getUUID()).stream().filter(id -> {
            var quest = QuestRepository.find(id);
            return quest != null && data.entry(player.getUUID(), quest) == null
                    && unlocked(data, player, quest)
                    && !hidden(data, player, quest);
        }).limit(QuestConfig.defaults().maxTrackedQuests).toList();
    }
    public static String toggleTracking(ServerPlayer player, String id) {
        requireServer(player);
        var quest = QuestRepository.find(id);
        if (quest == null) return "missing";
        if (!QuestFocus.group(player).isBlank()) return QuestFocus.select(player, quest);
        var data = QuestProgressData.get(player.getServer());
        boolean tracked = data.tracked(player.getUUID()).contains(id);
        if (!tracked && data.entry(player.getUUID(), quest) != null) return "already_complete";
        if (!tracked && !quest.trackable()) return "unavailable";
        if (!tracked && (!QuestSteps.valid(quest) || !unlocked(data, player, quest)
                || hidden(data, player, quest))) return "locked";
        if (!tracked && tracked(player).size() >= QuestConfig.defaults().maxTrackedQuests) {
            data.trackOnly(player.getUUID(), id);
            QuestStructureGuidance.request(player, id);
            return "tracked";
        }
        boolean enabled = data.toggleTracked(player.getUUID(), id, QuestConfig.defaults().maxTrackedQuests);
        if (enabled) QuestStructureGuidance.request(player, id);
        return enabled ? "tracked" : "untracked";
    }
    public static String activate(ServerPlayer player, String id) {
        requireServer(player);
        var quest = QuestRepository.find(id);
        if (quest == null) return "missing";
        if (!QuestFocus.allows(player, quest)) return "focus_active";
        var data = QuestProgressData.get(player.getServer());
        if (data.entry(player.getUUID(), quest) != null) return "already_complete";
        if (!QuestStructures.allowsProgress(player, quest)) return "scene_active";
        if (!QuestSteps.valid(quest)) return "unavailable";
        if (!unlocked(data, player, quest)) return "locked";
        if (!triggersEnabled(data, player, quest)) return "triggers_disabled";
        var active = QuestSteps.current(data, player.getUUID(), quest);
        if (active.triggers().isEmpty()) return "no_condition";
        var plan = QuestConditions.plan(player, active, true);
        if (!plan.satisfied()) return "insufficient";
        boolean finalStep = QuestSteps.last(data, player.getUUID(), quest);
        // Resolve scripts before consuming anything; invalid reward definitions must not eat submissions.
        List<QuestDefinition.Reward> rewards = null;
        if (finalStep) {
            try { rewards = QuestRewards.freeze(player, quest.rewards()); }
            catch (Exception invalid) { return "unavailable"; }
        }
        var submitted = QuestConditionEvents.consumedItems(player, plan.inventory());
        for (int slot = 0; slot < plan.inventory().size(); slot++)
            player.getInventory().setItem(slot, plan.inventory().get(slot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (advanceOrComplete(data, player, quest, rewards)) QuestConditionEvents.notifySubmission(player, active, submitted);
        evaluate(player);
        return finalStep ? "completed" : "step_completed";
    }
    public static String claim(ServerPlayer player, String id) { return claim(player, id, false); }
    public static String claim(ServerPlayer player, String id, boolean allowDrop) {
        requireServer(player);
        var quest = QuestRepository.find(id);
        return quest == null ? "missing" : QuestRewards.claim(player, quest, allowDrop);
    }
    public static String claimAll(ServerPlayer player) {
        requireServer(player);
        int claimed = 0;
        String failure = "nothing_to_claim";
        var data = QuestProgressData.get(player.getServer());
        for (var quest : QuestRepository.all()) {
            var entry = data.entry(player.getUUID(), quest);
            if (entry == null || entry.claimed()) continue;
            String result;
            if (entry != null && !entry.claimed()
                    && entry.rewards().stream().anyMatch(reward -> reward.type().equals("loot_choice"))) {
                Map<Integer, Integer> selections = new LinkedHashMap<>();
                for (int index = 0; index < entry.rewards().size(); index++) {
                    if (entry.rewards().get(index).type().equals("loot_choice")) selections.put(index, 0);
                }
                result = QuestRewards.claimChoices(player, quest, selections);
            } else {
                result = claim(player, quest.id());
            }
            if (result.equals("claimed")) claimed++;
            else if (!List.of("incomplete", "already_claimed").contains(result)) failure = result;
        }
        return claimed > 0 ? (failure.equals("nothing_to_claim") ? "claimed" : "partially_claimed") : failure;
    }
    /** Trusted server integrations only. Network packets never expose this completion bypass. */
    public static String trigger(ServerPlayer player, String id, String action, boolean checkPermission) {
        requireServer(player);
        if (checkPermission && !player.hasPermissions(2)) return "permission";
        var quest = QuestRepository.find(id);
        var data = QuestProgressData.get(player.getServer());
        if (quest == null) {
            if (!List.of("unlock", "lock").contains(action)) return "missing";
            String groupId = id.startsWith("group:") ? id.substring("group:".length()) : id;
            if (QuestRepository.groups().stream().noneMatch(group -> group.id().equals(groupId))) return "missing";
            if (action.equals("unlock")) data.unlock(player.getUUID(), "group:" + groupId);
            else {
                data.lock(player.getUUID(), "group:" + groupId);
                QuestRepository.all().stream()
                        .filter(task -> QuestRepository.groupOf(task.id()).equals(groupId))
                        .forEach(task -> data.removeTracked(player.getUUID(), task.id()));
            }
            return action.equals("unlock") ? "unlocked" : "locked";
        }
        if (id.startsWith("group:")) return "missing";
        if (List.of("unlock", "lock").contains(action)) {
            if (action.equals("unlock")) {
                data.unlock(player.getUUID(), quest);
                if (!QuestFocus.group(player).isBlank()) QuestFocus.advance(player, "");
                else autoTrackIfRelated(player, quest);
            }
            else {
                data.lock(player.getUUID(), quest.progressKey());
                data.removeTracked(player.getUUID(), quest.id());
            }
            return action.equals("unlock") ? "unlocked" : "locked";
        }
        if (!QuestStructures.allowsProgress(player, quest)) return "scene_active";
        if (!QuestFocus.allows(player, quest)) return "focus_active";
        if (data.entry(player.getUUID(), quest) != null) return "already_complete";
        if (action.equals("complete")) return complete(data, player, quest) ? "completed" : "unavailable";
        if (action.equals("evaluate")) {
            if (!unlocked(data, player, quest)) return "locked";
            boolean finalStep = QuestSteps.last(data, player.getUUID(), quest);
            return QuestConditions.plan(player, QuestSteps.current(data, player.getUUID(), quest), false).satisfied()
                    && advanceOrComplete(data, player, quest, null)
                    ? finalStep ? "completed" : "step_completed" : "insufficient";
        }
        // Story-driven progression: skip the current step's conditions (the final step completes the task).
        if (action.equals("advance_step")) {
            if (!unlocked(data, player, quest)) return "locked";
            boolean finalStep = QuestSteps.last(data, player.getUUID(), quest);
            return advanceOrComplete(data, player, quest, null)
                    ? finalStep ? "completed" : "step_completed" : "unavailable";
        }
        return "unavailable";
    }

    /**
     * Completes the current step of a multi-step task, applying that step's unlock/lock actions;
     * the final step (or a task without steps) completes the task. {@code rewards} may be null
     * to freeze the task rewards on demand.
     */
    private static boolean advanceOrComplete(QuestProgressData data, ServerPlayer player, QuestDefinition quest,
                                             List<QuestDefinition.Reward> rewards) {
        var step = QuestSteps.currentStep(data, player.getUUID(), quest);
        if (step != null && QuestSteps.advance(data, player, quest)) {
            applyCompletionActions(player, step.conditions());
            return true;
        }
        boolean completed = rewards == null ? complete(data, player, quest) : complete(data, player, quest, rewards);
        if (completed && step != null) applyCompletionActions(player, step.conditions());
        return completed;
    }

    /** Completion emitted by a scene's own completion trigger is independent of item submission. */
    public static String completeFromStructure(ServerPlayer player, String id) {
        requireServer(player);
        var quest = QuestRepository.find(id);
        if (quest == null || !QuestStructures.active(player) || QuestStructures.editing(player)
                || !QuestStructures.task(player).equals(id) || !QuestStructures.current(player))
            return "unavailable";
        if (!QuestFocus.allows(player, quest)) return "focus_active";
        var data = QuestProgressData.get(player.getServer());
        if (data.entry(player.getUUID(), quest) != null) return "already_complete";
        try {
            return complete(data, player, quest, QuestRewards.freeze(player, quest.rewards()), true)
                    ? "completed" : "unavailable";
        } catch (Exception failure) {
            return "unavailable";
        }
    }
    public static String reset(ServerPlayer player, String id) {
        requireServer(player);
        if (!player.hasPermissions(2)) return "permission";
        return resetTrusted(player, id);
    }
    public static String resetTrusted(ServerPlayer player, String id) {
        requireServer(player);
        var quest = QuestRepository.find(id);
        if (quest == null) return "missing";
        var progress = QuestProgressData.get(player.getServer());
        boolean changed = progress.reset(player.getUUID(), quest);
        changed |= QuestControlData.get(player).resetTask(player, quest);
        QuestEventGraphs.reset(player, quest);
        if (changed) player.sendSystemMessage(Component.translatable("quest.xero_quest_journal.reset", quest.title()));
        return changed ? "reset" : "not_started";
    }
    public static String resetMany(ServerPlayer player, Collection<String> ids) {
        requireServer(player);
        if (!player.hasPermissions(2)) return "permission";
        int reset = 0;
        for (String id : ids) if (resetTrusted(player, id).equals("reset")) reset++;
        return "reset_batch:" + reset;
    }
    private static boolean complete(QuestProgressData data, ServerPlayer player, QuestDefinition quest) {
        try { return complete(data, player, quest, QuestRewards.freeze(player, quest.rewards())); }
        catch (Exception failure) { return false; }
    }
    private static boolean complete(QuestProgressData data, ServerPlayer player, QuestDefinition quest,
                                    List<QuestDefinition.Reward> rewards) {
        return complete(data, player, quest, rewards, false);
    }

    private static boolean complete(QuestProgressData data, ServerPlayer player, QuestDefinition quest,
                                    List<QuestDefinition.Reward> rewards, boolean sceneCompletion) {
        if (!sceneCompletion && !QuestStructures.allowsProgress(player, quest)) return false;
        if (!QuestFocus.allows(player, quest)) return false;
        boolean focusMode = !QuestFocus.group(player).isBlank();
        boolean wasTracked = data.tracked(player.getUUID()).contains(quest.id());
        if (!data.complete(player.getUUID(), quest, rewards)) return false;
        AnecdoteRuntime.completeGeneratedQuest(player, quest.id());
        data.removeTracked(player.getUUID(), quest.id());
        applyCompletionActions(player, quest.triggers());
        if (focusMode) {
            QuestFocus.advance(player, quest.id());
        } else if (wasTracked) {
            autoTrackRelatedSuccessor(player, quest);
        }
        player.sendSystemMessage(completionMessage(quest, player.registryAccess()));
        return true;
    }

    public static Component completionMessage(QuestDefinition quest, net.minecraft.core.HolderLookup.Provider registries) {
        var open = Component.translatable("quest.xero_quest_journal.open_task")
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GOLD)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/xero_quest open task " + quest.id()))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                xtdpotato.xero_quest_journal.core.model.QuestTitleText.title(quest, registries))));
        return Component.translatable("quest.xero_quest_journal.completed",
                xtdpotato.xero_quest_journal.core.model.QuestTitleText.title(quest, registries))
                .append("\n").append(open);
    }

    private static void autoTrackRelatedSuccessor(ServerPlayer player, QuestDefinition completed) {
        if (!QuestFocus.group(player).isBlank()) return;
        QuestDefinition next = QuestRepository.all().stream()
                .filter(quest -> quest.parents().contains(completed.id()))
                .filter(quest -> quest.trackable() && !completed(player, quest)
                        && unlocked(QuestProgressData.get(player.getServer()), player, quest)
                        && !hidden(QuestProgressData.get(player.getServer()), player, quest))
                .findFirst().orElse(null);
        if (next != null) {
            QuestProgressData.get(player.getServer()).trackOnly(player.getUUID(), next.id());
            QuestStructureGuidance.request(player, next.id());
        }
    }

    private static void autoTrackIfRelated(ServerPlayer player, QuestDefinition unlocked) {
        var data = QuestProgressData.get(player.getServer());
        if (data.tracked(player.getUUID()).stream().anyMatch(unlocked.parents()::contains)
                && unlocked.trackable() && !completed(player, unlocked)
                && unlocked(data, player, unlocked) && !hidden(data, player, unlocked)) {
            data.trackOnly(player.getUUID(), unlocked.id());
            QuestStructureGuidance.request(player, unlocked.id());
        }
    }
    private static void applyCompletionActions(ServerPlayer player, List<QuestDefinition.Trigger> triggers) {
        for (var trigger : triggers) {
            if (trigger.type().equals("unlock") || trigger.type().equals("lock")) {
                for (String target : trigger.item().split("\\s*,\\s*")) {
                    String result = trigger(player, target, trigger.type(), false);
                    if (!List.of("unlocked", "locked").contains(result))
                        XeroQuestJournal.LOGGER.warn("Completion action {} failed for {}", trigger.type(), target);
                }
            }
        }
    }
    public static boolean unlocked(QuestProgressData data, ServerPlayer player, QuestDefinition quest) {
        return baseUnlocked(data, player, quest) && QuestDaily.available(data, player.getUUID(), quest);
    }

    /** Prerequisite/lock state without the daily-commission roll. */
    static boolean baseUnlocked(QuestProgressData data, ServerPlayer player, QuestDefinition quest) {
        UUID id = player.getUUID();
        String groupId = QuestRepository.groupOf(quest.id());
        if (!groupId.isBlank()) {
            var group = QuestRepository.groups().stream().filter(value -> value.id().equals(groupId)).findFirst().orElse(null);
            String key = "group:" + groupId;
            if (data.explicitlyLocked(id, key)
                    || group != null && !group.defaultUnlocked() && !data.explicitlyUnlocked(id, key)) return false;
        }
        if (data.explicitlyLocked(id, quest.progressKey())) return false;
        if (data.unlocked(id, quest)) return true;
        if (!quest.defaultUnlocked() && quest.parents().isEmpty()) return false;
        java.util.function.Predicate<String> completedParent = parentId -> {
            var parent = QuestRepository.find(parentId);
            return parent != null && data.entry(id, parent) != null;
        };
        if (quest.parents().isEmpty()) return true;
        return quest.completionPolicy().prerequisiteMode().equals("any")
                ? quest.parents().stream().anyMatch(completedParent) : quest.parents().stream().allMatch(completedParent);
    }
    public static boolean triggersEnabled(QuestProgressData data, ServerPlayer player, QuestDefinition quest) {
        return data.taskProperty(player.getUUID(), quest, "triggersEnabled", quest.triggersEnabled());
    }

    public static boolean hidden(QuestProgressData data, ServerPlayer player, QuestDefinition quest) {
        return !QuestDaily.available(data, player.getUUID(), quest)
                || data.taskProperty(player.getUUID(), quest, "hidden", quest.hidden())
                || data.taskProperty(player.getUUID(), quest, "hideWhenLocked", quest.hideWhenLocked())
                && !unlocked(data, player, quest);
    }

    /** Trusted graph action; overrides belong to this player, not to the shared quest file. */
    public static boolean setTaskProperty(ServerPlayer player, String id, String property, boolean value) {
        requireServer(player);
        if (!List.of("unlocked", "hideWhenLocked", "hidden", "triggersEnabled").contains(property)) return false;
        var quest = QuestRepository.find(id);
        if (quest == null) return false;
        if (property.equals("unlocked"))
            return trigger(player, id, value ? "unlock" : "lock", false)
                    .equals(value ? "unlocked" : "locked");
        var data = QuestProgressData.get(player.getServer());
        data.setTaskProperty(player.getUUID(), quest, property, value);
        if (hidden(data, player, quest)) data.removeTracked(player.getUUID(), id);
        return true;
    }
    private static void requireServer(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("Quest actions require a connected server player and server thread");
    }
}
