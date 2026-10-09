package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestVariable;
import xtdpotato.xero_quest_journal.core.io.QuestVariableStore;
import xtdpotato.xero_quest_journal.core.model.QuestType;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;
import xtdpotato.xero_quest_journal.core.model.QuestUnlockLayout;

import java.util.List;
import xtdpotato.xero_quest_journal.core.model.QuestJournalOrder;

/** Remote sessions never display the player's unrelated local task files. */
public final class ClientQuestDefinitions {
    private static List<QuestDefinition> orderSource = List.of(), orderedTasks = List.of();
    private static QuestJournalOrder appliedOrder;
    private static java.util.Map<String, QuestDefinition> taskIndex = java.util.Map.of();
    private static Object groupSource, chapterSource;
    private static QuestJournalOrder groupOrder;
    private static List<QuestCatalog.Group> cachedGroups = List.of();
    private static List<QuestCatalog.Chapter> cachedChapters = List.of();
    private ClientQuestDefinitions() {}

    private static boolean local() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    public static List<QuestDefinition> all() {
        var source = local() ? QuestRepository.all() : ClientQuestProgress.catalog().quests();
        var order = journalOrder();
        if (source == orderSource && order == appliedOrder) return orderedTasks;
        if (order.tasks().isEmpty()) orderedTasks = source;
        else {
            var index = new java.util.LinkedHashMap<String, QuestDefinition>();
            source.forEach(quest -> index.put(quest.id(), quest));
            orderedTasks = QuestJournalOrder.apply(source.stream().map(QuestDefinition::id).toList(),
                    order.tasks()).stream().map(index::get).toList();
        }
        orderSource = source;
        appliedOrder = order;
        var lookup = new java.util.HashMap<String, QuestDefinition>();
        orderedTasks.forEach(quest -> lookup.put(quest.id(), quest));
        taskIndex = java.util.Map.copyOf(lookup);
        return orderedTasks;
    }
    public static List<QuestVariable> variables() {
        return local() ? QuestVariableStore.all() : ClientQuestProgress.catalog().variables();
    }

    public static QuestDefinition find(String id) {
        if (id == null) return null;
        all();
        return taskIndex.get(id);
    }

    public static List<xtdpotato.xero_quest_journal.core.model.QuestTemplate> templates() {
        return local() ? xtdpotato.xero_quest_journal.core.QuestTemplateStore.all()
                : ClientQuestProgress.catalog().templates();
    }

    public static List<QuestType> types() {
        return local() ? xtdpotato.xero_quest_journal.core.QuestTypeStore.all()
                : ClientQuestProgress.catalog().types();
    }

    public static List<QuestCatalog.Group> groups() {
        Object identity = local() ? QuestRepository.groups() : ClientQuestProgress.catalog();
        var order = journalOrder();
        if (identity == groupSource && order == groupOrder) return cachedGroups;
        var source = local() ? QuestRepository.groups().stream()
                .map(g -> new QuestCatalog.Group(g.id(), g.name(), g.description(), g.icon(),
                        g.type(), g.leadingIcon(), g.trailingIcon(), g.hotEvent(), g.defaultUnlocked(),
                        g.recommended(), g.limitedReward(), g.listAccent(), g.focusAvailable())).toList()
                : ClientQuestProgress.catalog().groups();
        groupSource = identity;
        groupOrder = order;
        if (order.groups().isEmpty()) return cachedGroups = source;
        var index = new java.util.LinkedHashMap<String, QuestCatalog.Group>();
        source.forEach(group -> index.put(group.id(), group));
        return cachedGroups = QuestJournalOrder.apply(source.stream().map(QuestCatalog.Group::id).toList(),
                order.groups()).stream().map(index::get).toList();
    }

    public static HotEventCanvas hotEventCanvas() {
        return local() ? xtdpotato.xero_quest_journal.core.HotEventStore.get()
                : ClientQuestProgress.catalog().hotEventCanvas();
    }

    public static QuestUnlockLayout unlockLayout() {
        return local() ? QuestRepository.unlockLayout() : ClientQuestProgress.catalog().unlockLayout();
    }

    public static xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder() {
        return local() ? QuestRepository.journalOrder() : ClientQuestProgress.catalog().journalOrder();
    }

    public static List<QuestCatalog.Chapter> chapters() {
        Object identity = local() ? QuestRepository.chapters() : ClientQuestProgress.catalog();
        if (identity == chapterSource) return cachedChapters;
        chapterSource = identity;
        return cachedChapters = local() ? QuestRepository.chapters().stream()
                .map(c -> new QuestCatalog.Chapter(c.id(), c.name(), c.description(), c.icon(),
                        c.groupId(), c.quests(), c.backgrounds(), c.journeyAppearance(), c.timeline(),
                        QuestCatalog.chapterFingerprint(c.file()))).toList()
                : ClientQuestProgress.catalog().chapters();
    }

    public static String groupOf(String id) {
        return local() ? QuestRepository.groupOf(id) : ClientQuestProgress.catalog().membership().getOrDefault(id, "");
    }

    public static long revision() {
        return local() ? QuestRepository.revision() : ClientQuestProgress.catalogRevision();
    }

    public static List<String> errors() {
        return local() ? QuestRepository.errors() : List.of();
    }
}
