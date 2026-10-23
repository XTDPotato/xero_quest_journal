package xtdpotato.xero_quest_journal.core.runtime;

import com.google.gson.Gson;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import xtdpotato.xero_quest_journal.core.io.QuestGenerator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** World-scoped cursor for deterministic, daily generated anecdote chains. */
public final class AnecdoteGenerationData extends SavedData {
    public static final String FILE = "xero_quest_anecdote_generation";
    private static final Gson GSON = new Gson();
    private boolean enabled;
    private Set<String> namespaces = Set.of();
    private String optionsJson = "";
    private long lastProcessedDay = Long.MIN_VALUE;
    private List<String> pendingFollowups = List.of();

    private AnecdoteGenerationData() {}

    public static AnecdoteGenerationData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Anecdote generation state requires server thread");
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(AnecdoteGenerationData::new, AnecdoteGenerationData::load), FILE);
    }

    public boolean enabled() { return enabled; }
    public Set<String> namespaces() { return namespaces; }
    public List<String> pendingFollowups() { return pendingFollowups; }
    public long lastProcessedDay() { return lastProcessedDay; }

    public QuestGenerator.GenerationOptions options() {
        try {
            var value = GSON.fromJson(optionsJson, QuestGenerator.GenerationOptions.class);
            return value == null ? QuestGenerator.GenerationOptions.defaults() : value;
        } catch (RuntimeException invalid) {
            return QuestGenerator.GenerationOptions.defaults();
        }
    }

    public void configure(Set<String> namespaces, QuestGenerator.GenerationOptions options, long currentDay) {
        this.enabled = true;
        this.namespaces = Set.copyOf(namespaces == null ? Set.of() : namespaces);
        this.optionsJson = GSON.toJson(options == null
                ? QuestGenerator.GenerationOptions.defaults() : options);
        if (lastProcessedDay == Long.MIN_VALUE) lastProcessedDay = currentDay - 1;
        setDirty();
    }

    public boolean commitDay(long day, List<String> nextFollowups) {
        if (day <= lastProcessedDay) return false;
        lastProcessedDay = day;
        pendingFollowups = nextFollowups == null ? List.of() : nextFollowups.stream().distinct().limit(128).toList();
        setDirty();
        return true;
    }

    public static AnecdoteGenerationData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new AnecdoteGenerationData();
        data.enabled = tag.getBoolean("enabled");
        data.optionsJson = tag.getString("options");
        data.lastProcessedDay = tag.contains("lastProcessedDay", Tag.TAG_LONG)
                ? tag.getLong("lastProcessedDay") : Long.MIN_VALUE;
        ListTag namespaces = tag.getList("namespaces", Tag.TAG_STRING);
        var namespaceSet = new LinkedHashSet<String>();
        for (int i = 0; i < namespaces.size(); i++) namespaceSet.add(namespaces.getString(i));
        data.namespaces = Set.copyOf(namespaceSet);
        ListTag pending = tag.getList("pendingFollowups", Tag.TAG_STRING);
        var followups = new ArrayList<String>();
        for (int i = 0; i < pending.size() && i < 128; i++) followups.add(pending.getString(i));
        data.pendingFollowups = List.copyOf(followups);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("enabled", enabled);
        tag.putString("options", optionsJson);
        tag.putLong("lastProcessedDay", lastProcessedDay);
        ListTag namespaceTags = new ListTag();
        namespaces.stream().sorted().forEach(value ->
                namespaceTags.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("namespaces", namespaceTags);
        ListTag pendingTags = new ListTag();
        pendingFollowups.forEach(value -> pendingTags.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("pendingFollowups", pendingTags);
        return tag;
    }
}
