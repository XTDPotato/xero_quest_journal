package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.io.QuestVariableStore;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import java.util.*;

public final class QuestControlData extends SavedData {
    private CompoundTag players = new CompoundTag();
    private CompoundTag teams = new CompoundTag();
    public static QuestControlData get(ServerPlayer player) {
        return player.server.overworld().getDataStorage().computeIfAbsent(new Factory<>(QuestControlData::new,
                (tag, registries) -> {
                    var data = new QuestControlData();
                    data.players = tag.getCompound("players");
                    data.teams = tag.getCompound("teams");
                    return data;
                }),
                "xero_control_state");
    }
    private CompoundTag player(ServerPlayer player) {
        String id = player.getUUID().toString();
        if (!players.contains(id)) players.put(id, new CompoundTag());
        return players.getCompound(id);
    }
    public String overviewImage(ServerPlayer player) {
        return players.getCompound(player.getUUID().toString()).getString("overviewImage");
    }
    public void overviewImage(ServerPlayer player, String source) {
        xtdpotato.xero_quest_journal.core.model.QuestUiTheme.validateOverviewImage(source);
        if (source.isBlank()) player(player).remove("overviewImage");
        else player(player).putString("overviewImage", source);
        setDirty();
    }
    private CompoundTag quest(ServerPlayer player, QuestDefinition quest) {
        CompoundTag person = player(player);
        if (!person.contains(quest.progressKey())) {
            if (person.size() >= 4096) throw new IllegalStateException("Too many control states");
            person.put(quest.progressKey(), new CompoundTag());
        }
        return person.getCompound(quest.progressKey());
    }
    public boolean fired(ServerPlayer player, QuestDefinition quest, String key) {
        return players.getCompound(player.getUUID().toString()).getCompound(quest.progressKey()).getCompound("once").getBoolean(key);
    }
    public void markFired(ServerPlayer player, QuestDefinition quest, String key) {
        CompoundTag task = quest(player, quest);
        if (!task.contains("once")) task.put("once", new CompoundTag());
        var live = new HashSet<String>();
        for (var control : xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls()) for (var node : control.graph().nodes())
            {
                String prefix = quest.progressKey() + "/" + control.id() + "/" + node.id();
                live.add(prefix);
                live.add(prefix + "/predicate");
                live.add(prefix + "/action");
                live.add(prefix + "/theme");
            }
        for (String old : Set.copyOf(task.getCompound("once").getAllKeys()))
            if (!live.contains(old)) task.getCompound("once").remove(old);
        task.getCompound("once").putBoolean(key, true);
        setDirty();
    }
    public Map<String, String> properties(ServerPlayer player, QuestDefinition quest, String control) {
        CompoundTag tag = players.getCompound(player.getUUID().toString()).getCompound(quest.progressKey())
                .getCompound("controls").getCompound(control);
        Map<String, String> result = new LinkedHashMap<>();
        tag.getAllKeys().forEach(key -> result.put(key, tag.getString(key)));
        return result;
    }
    public void set(ServerPlayer player, QuestDefinition quest, String control, String property, String value) {
        CompoundTag task = quest(player, quest);
        if (!task.contains("controls")) task.put("controls", new CompoundTag());
        CompoundTag controls = task.getCompound("controls");
        if (!controls.contains(control)) {
            if (controls.size() >= 256) throw new IllegalStateException("Too many control overrides");
            controls.put(control, new CompoundTag());
        }
        controls.getCompound(control).putString(property, value);
        setDirty();
    }
    public void reset(ServerPlayer player, QuestDefinition quest, String control, String property, boolean all) {
        CompoundTag controls = quest(player, quest).getCompound("controls");
        if (all) controls.remove(control);
        else {
            controls.getCompound(control).remove(property);
            if (controls.getCompound(control).isEmpty()) controls.remove(control);
        }
        setDirty();
    }
    /**
     * Clears all player-owned runtime state for one task while preserving global
     * variables and the authored task definition.
     */
    public boolean resetTask(ServerPlayer player, QuestDefinition quest) {
        CompoundTag person = players.getCompound(player.getUUID().toString());
        boolean changed = false;
        if (person.contains(quest.progressKey())) {
            person.remove(quest.progressKey());
            changed = true;
        }
        CompoundTag variables = person.getCompound("variables");
        String prefix = quest.id() + ".";
        for (String id : Set.copyOf(variables.getAllKeys())) {
            if (id.startsWith(prefix)) {
                variables.remove(id);
                changed = true;
            }
        }
        if (variables.isEmpty() && person.contains("variables")) person.remove("variables");
        if (person.isEmpty()) players.remove(player.getUUID().toString());
        if (changed) setDirty();
        return changed;
    }
    public String actionReceipt(ServerPlayer player, QuestDefinition quest, String key) {
        return quest(player, quest).getCompound("actions").getString(key);
    }
    public void actionReceipt(ServerPlayer player, QuestDefinition quest, String key, String value) {
        CompoundTag task = quest(player, quest);
        if (!task.contains("actions")) task.put("actions", new CompoundTag());
        CompoundTag actions = task.getCompound("actions");
        if (!actions.contains(key) && actions.size() >= 32768)
            throw new IllegalStateException("Too many trigger action receipts");
        actions.putString(key, value);
        setDirty();
    }
    public String variable(ServerPlayer player, String id) {
        var definition = QuestVariableStore.find(id);
        if (definition == null) throw new IllegalArgumentException("Unknown variable");
        if (definition.readOnly()) return progressVariable(player, definition);
        if (definition.teamShared()) {
            String team = teamKey(player);
            return teams.getCompound(team).contains(id)
                    ? teams.getCompound(team).getString(id) : definition.initial();
        }
        return players.getCompound(player.getUUID().toString()).getCompound("variables")
                .contains(id) ? players.getCompound(player.getUUID().toString()).getCompound("variables")
                .getString(id) : definition.initial();
    }
    public Map<String, String> variableSnapshot(ServerPlayer player) {
        Map<String, String> values = new LinkedHashMap<>();
        for (var definition : QuestVariableStore.all())
            values.put(definition.id(), variable(player, definition.id()));
        return Map.copyOf(values);
    }
    public void setVariable(ServerPlayer player, String id, String value) {
        var definition = QuestVariableStore.find(id);
        if (definition == null || definition.readOnly() || value == null || value.length() > 4096
                || definition.numeric() && !finiteNumber(value))
            throw new IllegalArgumentException("Invalid variable value");
        if (definition.teamShared()) {
            String team = teamKey(player);
            if (!teams.contains(team)) teams.put(team, new CompoundTag());
            CompoundTag values = teams.getCompound(team);
            if (!values.contains(id) && values.size() >= 1024)
                throw new IllegalStateException("Too many team variable values");
            values.putString(id, value);
            setDirty();
            return;
        }
        CompoundTag person = player(player);
        if (!person.contains("variables")) person.put("variables", new CompoundTag());
        CompoundTag values = person.getCompound("variables");
        if (!values.contains(id) && values.size() >= 1024) throw new IllegalStateException("Too many variable values");
        values.putString(id, value);
        setDirty();
    }
    public void renameVariable(String oldId, String newId) {
        if (oldId.equals(newId)) return;
        boolean changed = false;
        for (String playerId : players.getAllKeys()) {
            CompoundTag values = players.getCompound(playerId).getCompound("variables");
            if (!values.contains(oldId)) continue;
            values.putString(newId, values.getString(oldId));
            values.remove(oldId);
            changed = true;
        }
        for (String team : teams.getAllKeys()) {
            CompoundTag values = teams.getCompound(team);
            if (!values.contains(oldId)) continue;
            values.putString(newId, values.getString(oldId));
            values.remove(oldId);
            changed = true;
        }
        if (changed) setDirty();
    }
    public static String teamKey(ServerPlayer player) {
        var team = player.getTeam();
        return team == null ? "solo:" + player.getUUID() : team.getName();
    }
    private static boolean finiteNumber(String value) {
        try { return Double.isFinite(Double.parseDouble(value)); }
        catch (RuntimeException invalid) { return false; }
    }
    private static String progressVariable(ServerPlayer player,
                                           xtdpotato.xero_quest_journal.core.model.QuestVariable variable) {
        String id = variable.id();
        String suffix = id.substring(id.lastIndexOf('.') + 1);
        String taskId = variable.scope();
        QuestDefinition quest = QuestRepository.find(taskId);
        if (quest == null) return "0";
        QuestProgressData data = QuestProgressData.get(player.server);
        QuestConditions.Progress progress = QuestConditions.progress(player,
                QuestSteps.current(data, player.getUUID(), quest));
        int current = data.entry(player.getUUID(), quest) == null ? progress.current() : progress.required();
        int required = progress.required();
        return switch (suffix) {
            case "progress_current" -> Integer.toString(current);
            case "progress_required" -> Integer.toString(required);
            case "progress_ratio" -> required <= 0 ? "0" : Double.toString(current / (double) required);
            default -> "0";
        };
    }
    public Map<String, Map<String, Map<String, String>>> snapshot(ServerPlayer player) {
        Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<>();
        for (var quest : QuestRepository.all()) {
            if (!QuestRuntime.unlocked(QuestProgressData.get(player.server), player, quest)) continue;
            Map<String, Map<String, String>> controls = new LinkedHashMap<>();
            for (var control : xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls()) {
                var values = properties(player, quest, control.id());
                if (!values.isEmpty()) controls.put(control.id(), values);
            }
            if (!controls.isEmpty()) result.put(quest.id(), controls);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("players", players.copy());
        tag.put("teams", teams.copy());
        return tag;
    }
}
