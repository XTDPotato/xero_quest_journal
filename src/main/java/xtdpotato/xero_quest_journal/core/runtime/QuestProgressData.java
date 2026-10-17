package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.*;

/** World-scoped progress. Rewards are frozen at completion, not recalculated at claim time. */
public final class QuestProgressData extends SavedData {
    public static final String FILE = "xero_quest_progress";
    private final Map<UUID, Map<String, Entry>> players = new HashMap<>();
    private final Map<UUID, List<String>> tracked = new HashMap<>();
    private final Map<UUID, String> focused = new HashMap<>();
    private final Map<UUID, String> focusGroups = new HashMap<>();

    public String focused(UUID player) { return focused.getOrDefault(player, ""); }
    public String focusGroup(UUID player) { return focusGroups.getOrDefault(player, ""); }

    public void focused(UUID player, String questId) {
        String previous = focused(player);
        if (questId == null || questId.isBlank()) focused.remove(player);
        else focused.put(player, questId);
        if (!previous.equals(focused(player))) setDirty();
    }

    public void focusGroup(UUID player, String groupId) {
        String previous = focusGroup(player);
        if (groupId == null || groupId.isBlank()) focusGroups.remove(player);
        else focusGroups.put(player, groupId);
        if (!previous.equals(focusGroup(player))) setDirty();
    }
    private final Map<UUID, Set<String>> resetProgress = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> counters = new HashMap<>();
    private final Map<UUID, Map<String, String>> receipts = new HashMap<>();
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();
    private final Map<UUID, Set<String>> locked = new HashMap<>();
    private final Map<UUID, Map<String, Boolean>> taskProperties = new HashMap<>();
    private final Map<UUID, Daily> daily = new HashMap<>();
    private final Map<UUID, Set<String>> knownTasks = new HashMap<>();
    private final Map<UUID, Set<String>> newTasks = new HashMap<>();
    private final Map<UUID, Set<String>> discoveredAnecdotes = new HashMap<>();
    private final Map<UUID, Set<String>> newAnecdotes = new HashMap<>();
    private final Map<UUID, Set<String>> knownAnecdotes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> completedAnecdotes = new HashMap<>();
    private final Map<UUID, Set<String>> newAnecdoteCompletions = new HashMap<>();
    private final Map<UUID, Long> observedCatalogRevisions = new HashMap<>();

    public boolean isNewTask(UUID player, String questId) {
        return newTasks.getOrDefault(player, Set.of()).contains(questId);
    }

    /** The first catalog seeds existing content; later additions become unread for this player. */
    public void observeTaskCatalog(UUID player, Collection<String> questIds) {
        Set<String> current = new HashSet<>(questIds);
        Set<String> known = knownTasks.get(player);
        if (known == null) {
            knownTasks.put(player, current);
            setDirty();
            return;
        }
        boolean changed = known.retainAll(current);
        Set<String> unread = newTasks.computeIfAbsent(player, ignored -> new HashSet<>());
        changed |= unread.retainAll(current);
        for (String id : current) {
            if (known.add(id)) {
                unread.add(id);
                changed = true;
            }
        }
        if (unread.isEmpty()) newTasks.remove(player);
        if (changed) setDirty();
    }

    public void observeTaskCatalog(UUID player, long revision) {
        if (Objects.equals(observedCatalogRevisions.get(player), revision)) return;
        observeTaskCatalog(player, xtdpotato.xero_quest_journal.core.io.QuestRepository.all().stream()
                .map(QuestDefinition::id).toList());
        observedCatalogRevisions.put(player, revision);
    }

    public boolean markTaskRead(UUID player, String questId) {
        Set<String> unread = newTasks.get(player);
        if (unread == null || !unread.remove(questId)) return false;
        if (unread.isEmpty()) newTasks.remove(player);
        setDirty();
        return true;
    }

    public boolean markAllTasksRead(UUID player) {
        Set<String> unread = newTasks.remove(player);
        if (unread == null || unread.isEmpty()) return false;
        setDirty();
        return true;
    }

    public boolean anecdoteDiscovered(UUID player, String id) {
        return discoveredAnecdotes.getOrDefault(player, Set.of()).contains(id);
    }

    public boolean anecdoteNew(UUID player, String id) {
        return newAnecdotes.getOrDefault(player, Set.of()).contains(id);
    }

    public void ensureAnecdoteDefaults(UUID player,
                                       java.util.Collection<xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition> entries) {
        Set<String> discovered = discoveredAnecdotes.computeIfAbsent(player, ignored -> new HashSet<>());
        boolean changed = false;
        for (var entry : entries) if (entry.defaultDiscovered()) changed |= discovered.add(entry.id());
        if (changed) setDirty();
    }

    public void observeAnecdoteCatalog(UUID player,
                                       java.util.Collection<xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition> entries) {
        Set<String> current = new HashSet<>();
        for (var entry : entries) current.add(entry.id());
        Set<String> known = knownAnecdotes.get(player);
        if (known == null) {
            known = new HashSet<>(current);
            knownAnecdotes.put(player, known);
            ensureAnecdoteDefaults(player, entries);
            setDirty();
            return;
        }
        boolean changed = known.retainAll(current);
        for (var entry : entries) {
            if (known.add(entry.id())) {
                newAnecdotes.computeIfAbsent(player, ignored -> new HashSet<>()).add(entry.id());
                changed = true;
            }
            if (entry.defaultDiscovered())
                changed |= discoveredAnecdotes.computeIfAbsent(player, ignored -> new HashSet<>()).add(entry.id());
        }
        if (changed) setDirty();
    }

    public boolean discoverAnecdote(UUID player, String id) {
        boolean changed = discoveredAnecdotes.computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
        if (changed) {
            newAnecdotes.computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
            setDirty();
        }
        return changed;
    }

    public boolean markAnecdoteRead(UUID player, String id) {
        Set<String> unread = newAnecdotes.get(player);
        if (unread == null || !unread.remove(id)) return false;
        if (unread.isEmpty()) newAnecdotes.remove(player);
        setDirty();
        return true;
    }

    private static String anecdoteKey(String entryId, String recordId) { return entryId + "/" + recordId; }

    public boolean anecdoteCompleted(UUID player, String entryId, String recordId) {
        return completedAnecdotes.getOrDefault(player, Map.of()).containsKey(anecdoteKey(entryId, recordId));
    }

    public boolean completeAnecdoteRecord(UUID player, String entryId, String recordId) {
        String key = anecdoteKey(entryId, recordId);
        var completed = completedAnecdotes.computeIfAbsent(player, ignored -> new HashMap<>());
        if (completed.containsKey(key)) return false;
        completed.put(key, System.currentTimeMillis());
        newAnecdoteCompletions.computeIfAbsent(player, ignored -> new HashSet<>()).add(key);
        setDirty();
        return true;
    }

    public List<xtdpotato.xero_quest_journal.core.model.AnecdoteSnapshot.Completed> completedAnecdotes(UUID player) {
        return completedAnecdotes.getOrDefault(player, Map.of()).entrySet().stream().map(entry -> {
            int split = entry.getKey().indexOf('/');
            if (split <= 0) return null;
            return new xtdpotato.xero_quest_journal.core.model.AnecdoteSnapshot.Completed(
                    entry.getKey().substring(0, split), entry.getKey().substring(split + 1), entry.getValue());
        }).filter(java.util.Objects::nonNull).sorted(java.util.Comparator
                .comparingLong(xtdpotato.xero_quest_journal.core.model.AnecdoteSnapshot.Completed::completedAt)
                .reversed()).toList();
    }

    public List<String> newAnecdoteCompletions(UUID player) {
        return newAnecdoteCompletions.getOrDefault(player, Set.of()).stream().toList();
    }

    public boolean markAnecdoteCompletionsRead(UUID player, java.util.Collection<String> keys) {
        Set<String> unread = newAnecdoteCompletions.get(player);
        if (unread == null || !unread.removeAll(keys)) return false;
        if (unread.isEmpty()) newAnecdoteCompletions.remove(player);
        setDirty();
        return true;
    }

    public void forgetAnecdote(String id) {
        boolean changed = false;
        for (var values : discoveredAnecdotes.values()) changed |= values.remove(id);
        for (var values : newAnecdotes.values()) changed |= values.remove(id);
        for (var values : knownAnecdotes.values()) changed |= values.remove(id);
        String prefix = id + "/";
        for (var values : completedAnecdotes.values()) changed |= values.keySet().removeIf(key -> key.startsWith(prefix));
        for (var values : newAnecdoteCompletions.values()) changed |= values.removeIf(key -> key.startsWith(prefix));
        discoveredAnecdotes.values().removeIf(Set::isEmpty);
        newAnecdotes.values().removeIf(Set::isEmpty);
        knownAnecdotes.values().removeIf(Set::isEmpty);
        completedAnecdotes.values().removeIf(Map::isEmpty);
        newAnecdoteCompletions.values().removeIf(Set::isEmpty);
        if (changed) setDirty();
    }

    /** Today's commission roll for one player: the reset day, chosen task IDs and the bonus receipt. */
    public record Daily(long day, List<String> quests, boolean bonusClaimed) {
        public Daily {
            quests = quests == null ? List.of() : List.copyOf(quests);
        }
    }

    public Daily daily(UUID player) { return daily.get(player); }

    public void daily(UUID player, Daily value) {
        Daily previous = value == null ? daily.remove(player) : daily.put(player, value);
        if (!Objects.equals(previous, value)) setDirty();
    }

    public boolean taskProperty(UUID player, QuestDefinition quest, String property, boolean fallback) {
        return taskProperties.getOrDefault(player, Map.of())
                .getOrDefault(quest.progressKey() + "/" + property, fallback);
    }

    public void setTaskProperty(UUID player, QuestDefinition quest, String property, boolean value) {
        String key = quest.progressKey() + "/" + property;
        Boolean previous = taskProperties.computeIfAbsent(player, ignored -> new HashMap<>()).put(key, value);
        if (!Objects.equals(previous, value)) setDirty();
    }

    public int counter(UUID player, String key) { return counters.getOrDefault(player, Map.of()).getOrDefault(key, 0); }
    public void setCounter(UUID player, String key, int value) {
        int bounded = Math.clamp(value, 0, 1_000_000);
        Integer previous = counters.computeIfAbsent(player, ignored -> new HashMap<>()).put(key, bounded);
        if (previous == null || previous != bounded) setDirty();
    }
    public void increment(UUID player, String key, int amount) {
        var values = counters.computeIfAbsent(player, ignored -> new HashMap<>());
        values.put(key, (int) Math.clamp((long) values.getOrDefault(key, 0) + amount, 0, 1_000_000));
        setDirty();
    }
    public String receipt(UUID player, QuestDefinition quest, int index) {
        return receipts.getOrDefault(player, Map.of()).getOrDefault(quest.progressKey() + "/" + index, "");
    }
    public void receipt(UUID player, QuestDefinition quest, int index, String state) {
        receipts.computeIfAbsent(player, ignored -> new HashMap<>()).put(quest.progressKey() + "/" + index, state);
        setDirty();
    }
    public boolean unlocked(UUID player, QuestDefinition quest) {
        return explicitlyUnlocked(player, quest.progressKey());
    }
    public void unlock(UUID player, QuestDefinition quest) {
        unlock(player, quest.progressKey());
    }
    public boolean explicitlyUnlocked(UUID player, String key) {
        return unlocked.getOrDefault(player, Set.of()).contains(key);
    }
    public boolean explicitlyLocked(UUID player, String key) {
        return locked.getOrDefault(player, Set.of()).contains(key);
    }
    public void unlock(UUID player, String key) {
        Set<String> lockedKeys = locked.get(player);
        boolean changed = lockedKeys != null && lockedKeys.remove(key);
        if (lockedKeys != null && lockedKeys.isEmpty()) locked.remove(player);
        changed |= unlocked.computeIfAbsent(player, ignored -> new HashSet<>()).add(key);
        if (changed) setDirty();
    }
    public void lock(UUID player, String key) {
        Set<String> unlockedKeys = unlocked.get(player);
        boolean changed = unlockedKeys != null && unlockedKeys.remove(key);
        if (unlockedKeys != null && unlockedKeys.isEmpty()) unlocked.remove(player);
        changed |= locked.computeIfAbsent(player, ignored -> new HashSet<>()).add(key);
        if (changed) setDirty();
    }

    public record Entry(boolean claimed, List<QuestDefinition.Reward> rewards, long completedAt) {
        public Entry {
            rewards = List.copyOf(rewards);
        }
    }

    public static QuestProgressData get(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Progress must be accessed on the server thread");
        }
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(QuestProgressData::new, QuestProgressData::load), FILE);
    }

    public Entry entry(UUID player, QuestDefinition quest) {
        if (resetProgress.getOrDefault(player, Set.of()).contains(quest.progressKey())) return null;
        return players.getOrDefault(player, Map.of()).get(quest.progressKey());
    }

    public boolean complete(UUID player, QuestDefinition quest) {
        return complete(player, quest, quest.rewards());
    }

    public boolean complete(UUID player, QuestDefinition quest, List<QuestDefinition.Reward> frozenRewards) {
        var records = players.computeIfAbsent(player, ignored -> new HashMap<>());
        Set<String> reset = resetProgress.get(player);
        if (reset != null && reset.remove(quest.progressKey())) {
            if (reset.isEmpty()) resetProgress.remove(player);
            records.remove(quest.progressKey());
            clearReceipts(player, quest);
            records.put(quest.progressKey(), new Entry(frozenRewards.isEmpty(), frozenRewards, System.currentTimeMillis()));
            setDirty();
            return true;
        }
        if (records.containsKey(quest.progressKey())) return false;
        records.put(quest.progressKey(), new Entry(frozenRewards.isEmpty(), frozenRewards, System.currentTimeMillis()));
        setDirty();
        return true;
    }

    public void claimed(UUID player, QuestDefinition quest) {
        Entry previous = entry(player, quest);
        if (previous == null) throw new IllegalStateException("No unclaimed completion");
        claimed(player, quest, previous.rewards());
    }

    public void claimed(UUID player, QuestDefinition quest, List<QuestDefinition.Reward> rewards) {
        Entry previous = entry(player, quest);
        if (previous == null || previous.claimed()) throw new IllegalStateException("No unclaimed completion");
        players.get(player).put(quest.progressKey(), new Entry(true, rewards, previous.completedAt()));
        setDirty();
    }

    public boolean reset(UUID player, QuestDefinition quest) {
        Map<String, Entry> records = players.get(player);
        boolean changed = records != null && records.remove(quest.progressKey()) != null;
        Set<String> legacyResets = resetProgress.get(player);
        if (legacyResets != null) {
            boolean removed = legacyResets.remove(quest.progressKey());
            changed |= removed;
            if (legacyResets.isEmpty()) resetProgress.remove(player);
        }
        changed |= removeTracked(player, quest.id());
        var counts = counters.get(player);
        String stepScope = QuestSteps.scopePrefix(quest);
        if (counts != null) changed |= counts.keySet().removeIf(key -> key.startsWith(quest.progressKey() + "/")
                || key.startsWith(stepScope));
        var issued = receipts.get(player);
        if (issued != null) {
            changed |= issued.keySet().removeIf(key -> key.startsWith(quest.progressKey() + "/"));
            if (issued.isEmpty()) receipts.remove(player);
        }
        var flags = taskProperties.get(player);
        if (flags != null) {
            changed |= flags.keySet().removeIf(key -> key.startsWith(quest.progressKey() + "/"));
            if (flags.isEmpty()) taskProperties.remove(player);
        }
        Set<String> unlockedKeys = unlocked.get(player);
        if (unlockedKeys != null) {
            changed |= unlockedKeys.remove(quest.progressKey());
            if (unlockedKeys.isEmpty()) unlocked.remove(player);
        }
        Set<String> lockedKeys = locked.get(player);
        if (lockedKeys != null) {
            changed |= lockedKeys.remove(quest.progressKey());
            if (lockedKeys.isEmpty()) locked.remove(player);
        }
        if (focused(player).equals(quest.id())) {
            focused(player, "");
            changed = true;
        }
        if (records != null && records.isEmpty()) players.remove(player);
        if (counts != null && counts.isEmpty()) counters.remove(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private void clearReceipts(UUID player, QuestDefinition quest) {
        var values = receipts.get(player);
        if (values != null) {
            values.keySet().removeIf(key -> key.startsWith(quest.progressKey() + "/"));
            if (values.isEmpty()) receipts.remove(player);
        }
    }

    public List<String> tracked(UUID player) {
        return List.copyOf(tracked.getOrDefault(player, List.of()));
    }

    public boolean toggleTracked(UUID player, String questId, int maximum) {
        List<String> quests = tracked.computeIfAbsent(player, ignored -> new ArrayList<>());
        if (quests.remove(questId)) {
            if (quests.isEmpty()) {
                tracked.remove(player);
            }
            setDirty();
            return false;
        }
        while (quests.size() >= maximum) {
            quests.removeFirst();
        }
        quests.add(questId);
        setDirty();
        return true;
    }

    public void trackOnly(UUID player, String questId) {
        if (tracked.getOrDefault(player, List.of()).equals(List.of(questId))) return;
        tracked.put(player, new ArrayList<>(List.of(questId)));
        setDirty();
    }

    public boolean removeTracked(UUID player, String questId) {
        boolean wasFocused = focused(player).equals(questId);
        if (wasFocused) focused(player, "");
        List<String> quests = tracked.get(player);
        if (quests == null || !quests.remove(questId)) {
            return wasFocused;
        }
        if (quests.isEmpty()) {
            tracked.remove(player);
        }
        setDirty();
        return true;
    }

    public static QuestProgressData load(CompoundTag root, HolderLookup.Provider registries) {
        var result = new QuestProgressData();
        ListTag people = root.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < people.size(); i++) {
            CompoundTag person = people.getCompound(i);
            UUID player = person.getUUID("id");
            if (!person.getString("focused").isBlank())
                result.focused.put(player, person.getString("focused"));
            if (!person.getString("focusGroup").isBlank())
                result.focusGroups.put(player, person.getString("focusGroup"));
            ListTag known = person.getList("knownTasks", Tag.TAG_STRING);
            Set<String> knownValues = new HashSet<>();
            for (int j = 0; j < known.size(); j++) knownValues.add(known.getString(j));
            if (person.contains("knownTasks")) result.knownTasks.put(player, knownValues);
            ListTag newTasks = person.getList("newTasks", Tag.TAG_STRING);
            Set<String> newValues = new HashSet<>();
            for (int j = 0; j < newTasks.size(); j++) newValues.add(newTasks.getString(j));
            if (!newValues.isEmpty()) result.newTasks.put(player, newValues);
            ListTag discoveredAnecdotes = person.getList("discoveredAnecdotes", Tag.TAG_STRING);
            Set<String> discoveredValues = new HashSet<>();
            for (int j = 0; j < discoveredAnecdotes.size(); j++)
                discoveredValues.add(discoveredAnecdotes.getString(j));
            if (!discoveredValues.isEmpty()) result.discoveredAnecdotes.put(player, discoveredValues);
            ListTag newAnecdotes = person.getList("newAnecdotes", Tag.TAG_STRING);
            Set<String> newAnecdoteValues = new HashSet<>();
            for (int j = 0; j < newAnecdotes.size(); j++) newAnecdoteValues.add(newAnecdotes.getString(j));
            if (!newAnecdoteValues.isEmpty()) result.newAnecdotes.put(player, newAnecdoteValues);
            ListTag knownAnecdotes = person.getList("knownAnecdotes", Tag.TAG_STRING);
            Set<String> knownAnecdoteValues = new HashSet<>();
            for (int j = 0; j < knownAnecdotes.size(); j++) knownAnecdoteValues.add(knownAnecdotes.getString(j));
            if (person.contains("knownAnecdotes")) result.knownAnecdotes.put(player, knownAnecdoteValues);
            CompoundTag completed = person.getCompound("completedAnecdotes");
            Map<String, Long> completedValues = new HashMap<>();
            completed.getAllKeys().forEach(key -> completedValues.put(key, completed.getLong(key)));
            if (!completedValues.isEmpty()) result.completedAnecdotes.put(player, completedValues);
            ListTag newCompletions = person.getList("newAnecdoteCompletions", Tag.TAG_STRING);
            Set<String> newCompletionValues = new HashSet<>();
            for (int j = 0; j < newCompletions.size(); j++) newCompletionValues.add(newCompletions.getString(j));
            if (!newCompletionValues.isEmpty()) result.newAnecdoteCompletions.put(player, newCompletionValues);
            Map<String, Entry> records = new HashMap<>();
            ListTag quests = person.getList("quests", Tag.TAG_COMPOUND);
            for (int j = 0; j < quests.size(); j++) {
                CompoundTag quest = quests.getCompound(j);
                List<QuestDefinition.Reward> rewards = new ArrayList<>();
                ListTag items = quest.getList("rewards", Tag.TAG_COMPOUND);
                for (int k = 0; k < items.size(); k++) {
                    CompoundTag reward = items.getCompound(k);
                    rewards.add(new QuestDefinition.Reward(reward.getString("type"),
                            reward.getString("item"), reward.getInt("count"),
                            reward.getString("quality"), reward.getString("background")));
                }
                records.put(quest.getString("key"), new Entry(quest.getBoolean("claimed"), rewards, quest.getLong("time")));
            }
            result.players.put(player, records);
            CompoundTag counts = person.getCompound("counters");
            Map<String, Integer> countValues = new HashMap<>();
            counts.getAllKeys().forEach(key -> countValues.put(key, counts.getInt(key)));
            if (!countValues.isEmpty()) result.counters.put(player, countValues);
            CompoundTag receipts = person.getCompound("receipts");
            Map<String, String> receiptValues = new HashMap<>();
            receipts.getAllKeys().forEach(key -> receiptValues.put(key, receipts.getString(key)));
            if (!receiptValues.isEmpty()) result.receipts.put(player, receiptValues);
            ListTag unlocks = person.getList("unlocked", Tag.TAG_STRING);
            Set<String> unlockValues = new HashSet<>();
            for (int j = 0; j < unlocks.size(); j++) unlockValues.add(unlocks.getString(j));
            if (!unlockValues.isEmpty()) result.unlocked.put(player, unlockValues);
            ListTag locks = person.getList("locked", Tag.TAG_STRING);
            Set<String> lockValues = new HashSet<>();
            for (int j = 0; j < locks.size(); j++) lockValues.add(locks.getString(j));
            if (!lockValues.isEmpty()) result.locked.put(player, lockValues);
            CompoundTag flags = person.getCompound("taskProperties");
            Map<String, Boolean> flagValues = new HashMap<>();
            flags.getAllKeys().forEach(key -> flagValues.put(key, flags.getBoolean(key)));
            if (!flagValues.isEmpty()) result.taskProperties.put(player, flagValues);
            ListTag resets = person.getList("resetProgress", Tag.TAG_STRING);
            Set<String> resetKeys = new HashSet<>();
            for (int j = 0; j < resets.size(); j++) {
                if (records.containsKey(resets.getString(j))) resetKeys.add(resets.getString(j));
            }
            if (!resetKeys.isEmpty()) result.resetProgress.put(player, resetKeys);
            ListTag tracked = person.getList("tracked", Tag.TAG_STRING);
            List<String> trackedIds = new ArrayList<>();
            for (int j = 0; j < tracked.size() && trackedIds.size() < 3; j++) {
                String id = tracked.getString(j);
                if (!id.isBlank() && !trackedIds.contains(id)) {
                    trackedIds.add(id);
                }
            }
            if (!trackedIds.isEmpty()) {
                result.tracked.put(player, trackedIds);
            }
            if (person.contains("dailyDay")) {
                ListTag dailyQuests = person.getList("dailyQuests", Tag.TAG_STRING);
                List<String> dailyIds = new ArrayList<>();
                for (int j = 0; j < dailyQuests.size() && dailyIds.size() < 64; j++) {
                    String id = dailyQuests.getString(j);
                    if (!id.isBlank() && !dailyIds.contains(id)) dailyIds.add(id);
                }
                result.daily.put(player, new Daily(person.getLong("dailyDay"), dailyIds,
                        person.getBoolean("dailyBonus")));
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag people = new ListTag();
        Set<UUID> allPlayers = new HashSet<>(players.keySet());
        allPlayers.addAll(tracked.keySet());
        allPlayers.addAll(focused.keySet());
        allPlayers.addAll(focusGroups.keySet());
        allPlayers.addAll(counters.keySet());
        allPlayers.addAll(receipts.keySet());
        allPlayers.addAll(unlocked.keySet());
        allPlayers.addAll(locked.keySet());
        allPlayers.addAll(taskProperties.keySet());
        allPlayers.addAll(daily.keySet());
        allPlayers.addAll(knownTasks.keySet());
        allPlayers.addAll(newTasks.keySet());
        allPlayers.addAll(discoveredAnecdotes.keySet());
        allPlayers.addAll(newAnecdotes.keySet());
        allPlayers.addAll(knownAnecdotes.keySet());
        allPlayers.addAll(completedAnecdotes.keySet());
        allPlayers.addAll(newAnecdoteCompletions.keySet());
        allPlayers.forEach(id -> {
            Map<String, Entry> records = players.getOrDefault(id, Map.of());
            CompoundTag person = new CompoundTag();
            person.putUUID("id", id);
            person.putString("focused", focused(id));
            person.putString("focusGroup", focusGroup(id));
            ListTag known = new ListTag();
            knownTasks.getOrDefault(id, Set.of()).forEach(key ->
                    known.add(net.minecraft.nbt.StringTag.valueOf(key)));
            if (knownTasks.containsKey(id)) person.put("knownTasks", known);
            ListTag newTaskIds = new ListTag();
            newTasks.getOrDefault(id, Set.of()).forEach(key ->
                    newTaskIds.add(net.minecraft.nbt.StringTag.valueOf(key)));
            person.put("newTasks", newTaskIds);
            ListTag discoveredAnecdoteIds = new ListTag();
            discoveredAnecdotes.getOrDefault(id, Set.of()).forEach(key ->
                    discoveredAnecdoteIds.add(net.minecraft.nbt.StringTag.valueOf(key)));
            person.put("discoveredAnecdotes", discoveredAnecdoteIds);
            ListTag newAnecdoteIds = new ListTag();
            newAnecdotes.getOrDefault(id, Set.of()).forEach(key ->
                    newAnecdoteIds.add(net.minecraft.nbt.StringTag.valueOf(key)));
            person.put("newAnecdotes", newAnecdoteIds);
            ListTag knownAnecdoteIds = new ListTag();
            knownAnecdotes.getOrDefault(id, Set.of()).forEach(key ->
                    knownAnecdoteIds.add(net.minecraft.nbt.StringTag.valueOf(key)));
            if (knownAnecdotes.containsKey(id)) person.put("knownAnecdotes", knownAnecdoteIds);
            CompoundTag completedAnecdoteTimes = new CompoundTag();
            completedAnecdotes.getOrDefault(id, Map.of()).forEach(completedAnecdoteTimes::putLong);
            person.put("completedAnecdotes", completedAnecdoteTimes);
            ListTag newCompletionIds = new ListTag();
            newAnecdoteCompletions.getOrDefault(id, Set.of()).forEach(key ->
                    newCompletionIds.add(net.minecraft.nbt.StringTag.valueOf(key)));
            person.put("newAnecdoteCompletions", newCompletionIds);
            CompoundTag counts = new CompoundTag();
            counters.getOrDefault(id, Map.of()).forEach(counts::putInt);
            person.put("counters", counts);
            CompoundTag issued = new CompoundTag();
            receipts.getOrDefault(id, Map.of()).forEach(issued::putString);
            person.put("receipts", issued);
            ListTag unlocks = new ListTag();
            unlocked.getOrDefault(id, Set.of()).forEach(key -> unlocks.add(net.minecraft.nbt.StringTag.valueOf(key)));
            person.put("unlocked", unlocks);
            ListTag locks = new ListTag();
            locked.getOrDefault(id, Set.of()).forEach(key -> locks.add(net.minecraft.nbt.StringTag.valueOf(key)));
            person.put("locked", locks);
            CompoundTag flags = new CompoundTag();
            taskProperties.getOrDefault(id, Map.of()).forEach(flags::putBoolean);
            person.put("taskProperties", flags);
            ListTag quests = new ListTag();
            records.forEach((key, entry) -> {
                CompoundTag quest = new CompoundTag();
                quest.putString("key", key);
                quest.putBoolean("claimed", entry.claimed());
                quest.putLong("time", entry.completedAt());
                ListTag rewards = new ListTag();
                entry.rewards().forEach(value -> {
                    CompoundTag reward = new CompoundTag();
                    reward.putString("type", value.type());
                    reward.putString("item", value.item());
                    reward.putInt("count", value.count());
                    reward.putString("quality", value.quality());
                    reward.putString("background", value.background());
                    rewards.add(reward);
                });
                quest.put("rewards", rewards);
                quests.add(quest);
            });
            person.put("quests", quests);
            ListTag resets = new ListTag();
            resetProgress.getOrDefault(id, Set.of()).forEach(key ->
                    resets.add(net.minecraft.nbt.StringTag.valueOf(key)));
            person.put("resetProgress", resets);
            ListTag trackedQuests = new ListTag();
            for (String trackedId : tracked.getOrDefault(id, List.of())) {
                trackedQuests.add(net.minecraft.nbt.StringTag.valueOf(trackedId));
            }
            person.put("tracked", trackedQuests);
            Daily roll = daily.get(id);
            if (roll != null) {
                person.putLong("dailyDay", roll.day());
                ListTag dailyQuests = new ListTag();
                roll.quests().forEach(questId -> dailyQuests.add(net.minecraft.nbt.StringTag.valueOf(questId)));
                person.put("dailyQuests", dailyQuests);
                person.putBoolean("dailyBonus", roll.bonusClaimed());
            }
            people.add(person);
        });
        root.putInt("schema", 8);
        root.put("players", people);
        return root;
    }
}
