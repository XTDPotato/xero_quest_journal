package xtdpotato.xero_quest_journal.core.network;

import com.google.gson.Gson;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.model.HotEventData;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;
import xtdpotato.xero_quest_journal.core.model.QuestType;
import xtdpotato.xero_quest_journal.core.model.QuestVariable;
import xtdpotato.xero_quest_journal.core.model.QuestUnlockLayout;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;
import xtdpotato.xero_quest_journal.core.io.QuestVariableStore;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.QuestTypeStore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Public display definitions only: no server filesystem paths or executable source. */
public record QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                           Map<String, String> membership, List<QuestTemplate> templates,
                           List<QuestType> types, HotEventCanvas hotEventCanvas, List<QuestVariable> variables,
                           QuestUnlockLayout unlockLayout,
                           xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder,
                           Map<String, String> lootDisplayIcons,
                           Map<String, xtdpotato.xero_quest_journal.core.model.LootDefinition.Display> lootTables,
                           xtdpotato.xero_quest_journal.core.TemplateLibraryStore.Library templateLibrary) {
    private static final Gson GSON = new Gson();
    /** Maximum size of one network chunk. The complete catalog is assembled client-side. */
    public static final int MAX_CHARACTERS = 262144;
    /** Keep an extreme malformed catalog from exhausting client memory. */
    public static final int MAX_CATALOG_CHARACTERS = 16 * 1024 * 1024;
    private static long cachedRevision = -1;
    private static long cachedLootRevision = -1;
    private static String cachedJson = "";

    public record Group(String id, String name, String description, String icon, String type,
                        ListIconConfig leadingIcon, ListIconConfig trailingIcon, HotEventData hotEvent,
                        boolean defaultUnlocked, boolean recommended, boolean limitedReward, String listAccent,
                        boolean focusAvailable) {
        public Group {
            icon = icon == null ? "" : icon;
            type = type == null || type.isBlank() ? "active" : type;
            leadingIcon = leadingIcon == null ? ListIconConfig.leading(icon) : leadingIcon;
            trailingIcon = trailingIcon == null ? ListIconConfig.trailing() : trailingIcon;
            hotEvent = hotEvent == null ? HotEventData.empty() : hotEvent;
            listAccent = xtdpotato.xero_quest_journal.core.model.QuestListAccent.normalize(listAccent);
        }

        public Group(String id, String name, String description, String icon, String type,
                     ListIconConfig leadingIcon, ListIconConfig trailingIcon, HotEventData hotEvent,
                     boolean defaultUnlocked, boolean recommended, boolean limitedReward) {
            this(id, name, description, icon, type, leadingIcon, trailingIcon, hotEvent,
                    defaultUnlocked, recommended, limitedReward, "auto", false);
        }

        public Group(String id, String name, String description, String icon, String type,
                     ListIconConfig leadingIcon, ListIconConfig trailingIcon, HotEventData hotEvent,
                     boolean defaultUnlocked, boolean recommended, boolean limitedReward, String listAccent) {
            this(id, name, description, icon, type, leadingIcon, trailingIcon, hotEvent,
                    defaultUnlocked, recommended, limitedReward, listAccent, false);
        }

        public Group(String id, String name, String description, String icon, String type,
                     ListIconConfig leadingIcon, ListIconConfig trailingIcon) {
            this(id, name, description, icon, type, leadingIcon, trailingIcon, null, true, false, false,
                    "auto", false);
        }

        public Group(String id, String name, String description, String icon) {
            this(id, name, description, icon, "active", null, null, null, true, false, false, "auto", false);
        }
    }
    public record Chapter(String id, String name, String description, String icon, String groupId,
                          List<String> quests, List<JourneyBackdrop> backgrounds, JourneyAppearance journeyAppearance,
                          xtdpotato.xero_quest_journal.core.model.JourneyTimeline timeline, String fingerprint) {
        public Chapter(String id, String name, String description, String icon, String groupId,
                       List<String> quests, List<JourneyBackdrop> backgrounds, JourneyAppearance journeyAppearance) {
            this(id, name, description, icon, groupId, quests, backgrounds, journeyAppearance, null, "");
        }
        public Chapter {
            quests = List.copyOf(quests);
            backgrounds = backgrounds == null ? List.of() : List.copyOf(backgrounds);
            journeyAppearance = journeyAppearance == null ? JourneyAppearance.empty() : journeyAppearance;
            timeline = timeline == null ? xtdpotato.xero_quest_journal.core.model.JourneyTimeline.empty() : timeline;
            fingerprint = fingerprint == null ? "" : fingerprint;
        }

        public Chapter(String id, String name, String description, String icon, String groupId,
                       List<String> quests, List<JourneyBackdrop> backgrounds) {
            this(id, name, description, icon, groupId, quests, backgrounds, JourneyAppearance.empty());
        }

        public Chapter(String id, String name, String description, String icon, String groupId,
                       List<String> quests) {
            this(id, name, description, icon, groupId, quests, List.of());
        }
    }

    public QuestCatalog {
        quests = List.copyOf(quests);
        groups = List.copyOf(groups);
        chapters = List.copyOf(chapters);
        membership = Map.copyOf(membership);
        templates = templates == null ? List.of() : List.copyOf(templates);
        types = types == null ? List.of() : List.copyOf(types);
        hotEventCanvas = hotEventCanvas == null ? HotEventCanvas.empty() : hotEventCanvas;
        variables = variables == null ? List.of() : List.copyOf(variables);
        unlockLayout = unlockLayout == null ? QuestUnlockLayout.empty() : unlockLayout;
        journalOrder = journalOrder == null
                ? xtdpotato.xero_quest_journal.core.model.QuestJournalOrder.empty() : journalOrder;
        lootDisplayIcons = lootDisplayIcons == null ? Map.of() : Map.copyOf(lootDisplayIcons);
        lootTables = lootTables == null ? Map.of() : Map.copyOf(lootTables);
        templateLibrary = templateLibrary == null ? xtdpotato.xero_quest_journal.core.TemplateLibraryStore.Library.empty() : templateLibrary;
    }
    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates, List<QuestType> types,
                        HotEventCanvas hotEventCanvas, List<QuestVariable> variables, QuestUnlockLayout unlockLayout,
                        xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder,
                        Map<String, String> lootDisplayIcons,
                        Map<String, xtdpotato.xero_quest_journal.core.model.LootDefinition.Display> lootTables) {
        this(quests, groups, chapters, membership, templates, types, hotEventCanvas, variables, unlockLayout,
                journalOrder, lootDisplayIcons, lootTables, null);
    }
    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates,
                        List<QuestType> types, HotEventCanvas hotEventCanvas, List<QuestVariable> variables,
                        QuestUnlockLayout unlockLayout,
                        xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder) {
        this(quests, groups, chapters, membership, templates, types, hotEventCanvas, variables,
                unlockLayout, journalOrder, Map.of(), Map.of());
    }
    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates,
                        List<QuestType> types, HotEventCanvas hotEventCanvas, List<QuestVariable> variables,
                        QuestUnlockLayout unlockLayout,
                        xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder,
                        Map<String, String> lootDisplayIcons) {
        this(quests, groups, chapters, membership, templates, types, hotEventCanvas, variables,
                unlockLayout, journalOrder, lootDisplayIcons, Map.of());
    }
    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates,
                        List<QuestType> types, HotEventCanvas hotEventCanvas, List<QuestVariable> variables,
                        QuestUnlockLayout unlockLayout) {
        this(quests, groups, chapters, membership, templates, types, hotEventCanvas, variables, unlockLayout, null);
    }
    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates,
                        List<QuestType> types, HotEventCanvas hotEventCanvas, List<QuestVariable> variables) {
        this(quests, groups, chapters, membership, templates, types, hotEventCanvas, variables,
                QuestUnlockLayout.empty());
    }
    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates,
                        List<QuestType> types, HotEventCanvas hotEventCanvas) {
        this(quests, groups, chapters, membership, templates, types, hotEventCanvas, List.of());
    }

    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates, List<QuestType> types) {
        this(quests, groups, chapters, membership, templates, types, null);
    }

    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership) {
        this(quests, groups, chapters, membership, List.of(), List.of());
    }

    public QuestCatalog(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                        Map<String, String> membership, List<QuestTemplate> templates) {
        this(quests, groups, chapters, membership, templates, List.of());
    }
    public static QuestCatalog empty() {
        return new QuestCatalog(List.of(), List.of(), List.of(), Map.of());
    }

    public static QuestCatalog decode(String json) {
        if (json.length() > MAX_CATALOG_CHARACTERS) throw new IllegalArgumentException("Catalog too large");
        QuestCatalog catalog = GSON.fromJson(json, QuestCatalog.class);
        if (catalog == null) throw new IllegalArgumentException("Missing catalog");
        return catalog;
    }

    public static String serverJson() {
        synchronized (QuestRepository.class) {
            if (cachedRevision != QuestRepository.revision()
                    || cachedLootRevision != xtdpotato.xero_quest_journal.core.io.QuestLootStore.revision()) {
                var data = new QuestCatalog(QuestRepository.all().stream().map(QuestDefinition::display).toList(),
                        QuestRepository.groups().stream().map(g -> new Group(g.id(), g.name(), g.description(), g.icon(),
                                g.type(), g.leadingIcon(), g.trailingIcon(), g.hotEvent(), g.defaultUnlocked(),
                                g.recommended(), g.limitedReward(), g.listAccent(), g.focusAvailable())).toList(),
                        QuestRepository.chapters().stream().map(c -> new Chapter(c.id(), c.name(), c.description(),
                                c.icon(), c.groupId(), c.quests(), c.backgrounds(), c.journeyAppearance(),
                                c.timeline(), chapterFingerprint(c.file()))).toList(),
                        QuestRepository.all().stream().collect(Collectors.toMap(QuestDefinition::id,
                                q -> QuestRepository.groupOf(q.id()))), QuestTemplateStore.all(), QuestTypeStore.all(),
                        xtdpotato.xero_quest_journal.core.HotEventStore.get(), QuestVariableStore.all(),
                        QuestRepository.unlockLayout(), QuestRepository.journalOrder(),
                        xtdpotato.xero_quest_journal.core.io.QuestLootStore.all().stream().collect(
                                Collectors.toMap(xtdpotato.xero_quest_journal.core.model.LootDefinition::id,
                                        xtdpotato.xero_quest_journal.core.model.LootDefinition::displayIcon)),
                        xtdpotato.xero_quest_journal.core.io.QuestLootStore.all().stream().collect(
                                Collectors.toMap(xtdpotato.xero_quest_journal.core.model.LootDefinition::id,
                                        xtdpotato.xero_quest_journal.core.model.LootDefinition::display)),
                        xtdpotato.xero_quest_journal.core.TemplateLibraryStore.get());
                String json = GSON.toJson(data);
                if (json.length() > MAX_CATALOG_CHARACTERS) throw new IllegalArgumentException("Catalog too large");
                cachedJson = json;
                cachedRevision = QuestRepository.revision();
                cachedLootRevision = xtdpotato.xero_quest_journal.core.io.QuestLootStore.revision();
            }
            return cachedJson;
        }
    }

    public static String chapterFingerprint(java.nio.file.Path file) {
        try { return QuestRepository.fingerprint(java.nio.file.Files.readString(file)); }
        catch (java.io.IOException failure) { return ""; }
    }
}
