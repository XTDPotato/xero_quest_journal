package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.util.*;

/** Captures localized text in bounded client-thread slices and sends immutable strings to a worker. */
final class JournalSearch {
    private static final long CAPTURE_BUDGET = 2_000_000L;
    private static final long DEBOUNCE = 120_000_000L;
    private final AsyncQuestSearch worker = new AsyncQuestSearch();
    private List<QuestDefinition> source = List.of();
    private List<QuestCatalog.Group> groupSource = List.of();
    private List<QuestCatalog.Chapter> chapterSource = List.of();
    private Object language, connection;
    private final Map<String, String> groupText = new HashMap<>();
    private final Map<String, String> chapterText = new HashMap<>();
    private final Map<String, String> itemNames = new HashMap<>();
    private final List<AsyncQuestSearch.Document> captured = new ArrayList<>();
    private List<AsyncQuestSearch.Document> documents;
    private int cursor;
    private String query = "", submittedQuery;
    private long changedAt;
    private AsyncQuestSearch.Result applied;

    void queryChanged(String next) {
        next = AsyncQuestSearch.normalize(next);
        if (query.equals(next)) return;
        query = next;
        changedAt = System.nanoTime();
        submittedQuery = null;
        worker.cancel();
        if (next.isBlank()) applied = null;
    }

    boolean synchronize(List<QuestDefinition> tasks, List<QuestCatalog.Group> groups,
                        List<QuestCatalog.Chapter> chapters) {
        var game = Minecraft.getInstance();
        Object currentLanguage = Language.getInstance(), currentConnection = game.getConnection();
        if (source == tasks && groupSource == groups && chapterSource == chapters
                && language == currentLanguage && connection == currentConnection) return false;
        source = tasks;
        groupSource = groups;
        chapterSource = chapters;
        language = currentLanguage;
        connection = currentConnection;
        worker.cancel();
        submittedQuery = null;
        documents = null;
        applied = null;
        cursor = 0;
        captured.clear();
        itemNames.clear();
        groupText.clear();
        chapterText.clear();
        for (var group : groups)
            groupText.put(group.id(), group.id() + "\n" + group.name() + "\n" + group.description());
        var chapterFields = new HashMap<String, StringBuilder>();
        for (var chapter : chapters) {
            String text = chapter.id() + "\n" + chapter.name() + "\n" + chapter.description();
            for (String id : chapter.quests())
                chapterFields.computeIfAbsent(id, ignored -> new StringBuilder()).append(text).append('\n');
        }
        chapterFields.forEach((id, text) -> chapterText.put(id, text.toString()));
        for (var group : groups)
            captured.add(new AsyncQuestSearch.Document("group:" + group.id(), List.of(groupText.get(group.id()))));
        return true;
    }

    /** Return true only when a new, still-current result can be applied to the list. */
    boolean advance() {
        if (query.isBlank()) return false;
        long deadline = System.nanoTime() + CAPTURE_BUDGET;
        int count = 0;
        while (documents == null && cursor < source.size() && count++ < 64 && System.nanoTime() < deadline) {
            captured.add(document(source.get(cursor++)));
        }
        if (documents == null && cursor == source.size()) documents = List.copyOf(captured);
        if (documents != null && !query.equals(submittedQuery) && System.nanoTime() - changedAt >= DEBOUNCE) {
            submittedQuery = query;
            worker.request(query, documents);
        }
        var result = worker.takeResult();
        if (result == null || !result.query().equals(query)) return false;
        applied = result;
        return true;
    }

    boolean pending() { return !query.isBlank() && (applied == null || !applied.query().equals(query)); }
    boolean matchesTask(String id) { return applied == null || applied.ids().contains("task:" + id); }
    boolean matchesGroup(String id) { return applied == null || applied.ids().contains("group:" + id); }
    void suspend() { worker.cancel(); submittedQuery = null; }

    private AsyncQuestSearch.Document document(QuestDefinition quest) {
        var values = new ArrayList<String>();
        Collections.addAll(values, quest.id(), quest.title(), QuestText.title(quest).getString(),
                quest.description(), QuestText.description(quest).getString(), quest.type(),
                QuestText.type(quest).getString(), QuestText.objective(quest).getString(),
                quest.titleKey(), quest.descriptionKey(), quest.templateId(), quest.subtitle(), quest.objective(), quest.place(),
                groupText.get(ClientQuestDefinitions.groupOf(quest.id())), chapterText.get(quest.id()));
        quest.triggers().forEach(trigger -> trigger(values, trigger));
        quest.steps().forEach(step -> {
            values.add(step.title());
            values.add(step.description());
            step.conditions().forEach(trigger -> trigger(values, trigger));
        });
        quest.rewards().forEach(reward -> {
            values.add(reward.type());
            values.add(reward.item());
            values.add(itemName(reward.item()));
        });
        return new AsyncQuestSearch.Document("task:" + quest.id(), values);
    }

    private void trigger(List<String> values, QuestDefinition.Trigger trigger) {
        values.add(trigger.type());
        values.add(trigger.item());
        values.add(itemName(trigger.item()));
        trigger.children().forEach(child -> trigger(values, child));
    }

    private String itemName(String target) {
        if (target == null || target.isBlank()) return "";
        return itemNames.computeIfAbsent(target, QuestIcons::displayName);
    }
}
