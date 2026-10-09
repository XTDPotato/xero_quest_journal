package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.model.HotEventData;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestUnlockLayout;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;

import java.nio.file.*;
import java.util.*;

public final class QuestRepository {
    private static final Gson GSON = new Gson();
    private static List<QuestDefinition> quests = List.of();
    /** ID lookup; large generated packs make the per-tick prerequisite checks call {@link #find} often. */
    private static Map<String, QuestDefinition> questIndex = Map.of();
    private static List<Group> groups = List.of();
    private static List<Chapter> chapters = List.of();
    private static Map<String, Path> filesById = Map.of();
    private static Map<String, String> questGroups = Map.of();
    private static List<String> errors = List.of();
    private static long revision;
    private static QuestUnlockLayout unlockLayout = QuestUnlockLayout.empty();
    private static xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder =
            xtdpotato.xero_quest_journal.core.model.QuestJournalOrder.empty();

    public record Group(String id, String name, String description, String icon, String type,
                        ListIconConfig leadingIcon, ListIconConfig trailingIcon, Path directory,
                        HotEventData hotEvent, boolean defaultUnlocked, boolean recommended,
                        boolean limitedReward, String listAccent, boolean focusAvailable) {
        public Group {
            icon = icon == null ? "" : icon;
            type = type == null || type.isBlank() ? "active" : type;
            leadingIcon = leadingIcon == null ? ListIconConfig.leading(icon) : leadingIcon;
            trailingIcon = trailingIcon == null ? ListIconConfig.trailing() : trailingIcon;
            hotEvent = hotEvent == null ? HotEventData.empty() : hotEvent;
            listAccent = xtdpotato.xero_quest_journal.core.model.QuestListAccent.normalize(listAccent);
        }

        public Group(String id, String name, String description, String icon, String type,
                     ListIconConfig leadingIcon, ListIconConfig trailingIcon, Path directory,
                     HotEventData hotEvent, boolean defaultUnlocked, boolean recommended, boolean limitedReward) {
            this(id, name, description, icon, type, leadingIcon, trailingIcon, directory, hotEvent,
                    defaultUnlocked, recommended, limitedReward, "auto", false);
        }

        public Group(String id, String name, String description, String icon, String type,
                     ListIconConfig leadingIcon, ListIconConfig trailingIcon, Path directory,
                     HotEventData hotEvent, boolean defaultUnlocked, boolean recommended, boolean limitedReward,
                     String listAccent) {
            this(id, name, description, icon, type, leadingIcon, trailingIcon, directory, hotEvent,
                    defaultUnlocked, recommended, limitedReward, listAccent, false);
        }

        public Group(String id, String name, String description, String icon, String type,
                     ListIconConfig leadingIcon, ListIconConfig trailingIcon, Path directory) {
            this(id, name, description, icon, type, leadingIcon, trailingIcon, directory, null, true, false, false,
                    "auto", false);
        }
    }

    public record Chapter(String id, String name, String description, String icon, String groupId,
                          List<String> quests, Path file, List<JourneyBackdrop> backgrounds,
                          JourneyAppearance journeyAppearance,
                          xtdpotato.xero_quest_journal.core.model.JourneyTimeline timeline) {
        public Chapter(String id, String name, String description, String icon, String groupId,
                       List<String> quests, Path file, List<JourneyBackdrop> backgrounds,
                       JourneyAppearance journeyAppearance) {
            this(id, name, description, icon, groupId, quests, file, backgrounds, journeyAppearance, null);
        }
        public Chapter {
            quests = List.copyOf(quests);
            backgrounds = backgrounds == null ? List.of() : List.copyOf(backgrounds);
            journeyAppearance = journeyAppearance == null ? JourneyAppearance.empty() : journeyAppearance;
            timeline = timeline == null ? xtdpotato.xero_quest_journal.core.model.JourneyTimeline.empty() : timeline;
        }

        public Chapter(String id, String name, String description, String icon, String groupId,
                       List<String> quests, Path file, List<JourneyBackdrop> backgrounds) {
            this(id, name, description, icon, groupId, quests, file, backgrounds, JourneyAppearance.empty());
        }

        public Chapter(String id, String name, String description, String icon, String groupId,
                       List<String> quests, Path file) {
            this(id, name, description, icon, groupId, quests, file, List.of());
        }
    }

    private QuestRepository() {
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve("xero_quest/groups").toAbsolutePath().normalize();
    }

    /**
     * Everything read from the definition folder. {@link #scan()} can run on a worker thread;
     * {@link #apply(Scan)} swaps the snapshot in quickly on the server thread.
     */
    public record Scan(List<QuestDefinition> quests, List<Group> groups, List<Chapter> chapters,
                       Map<String, Path> files, Map<String, String> membership, QuestUnlockLayout unlockLayout,
                       xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder,
                       List<String> problems, long revisionAtStart) {}

    public static synchronized void reload() {
        QuestLootStore.reload();
        QuestVariableStore.refresh();
        xtdpotato.xero_quest_journal.core.QuestTemplateStore.refresh();
        xtdpotato.xero_quest_journal.core.QuestTypeStore.refresh();
        apply(scan());
    }

    /** Applies a scan; returns false (keeping the previous snapshot) when it reported problems. */
    public static synchronized boolean apply(Scan scan) {
        errors = List.copyOf(scan.problems());
        if (!scan.problems().isEmpty()) {
            scan.problems().forEach(problem -> XeroQuestJournal.LOGGER.warn("Quest definition error: {}", problem));
            return false; // Broken reloads do not replace a previously valid snapshot.
        }
        List<QuestDefinition> nextQuests = new ArrayList<>(scan.quests());
        if (nextQuests.isEmpty()) {
            nextQuests.add(new QuestDefinition("example_intro", "example_intro", "", "normal", "",
                    true, false, 0, null, "quest.xero_quest_journal.welcome",
                    "quest.xero_quest_journal.welcome_description", ""));
        }
        QuestVariableStore.setTasks(nextQuests);
        journalOrder = scan.journalOrder();
        quests = List.copyOf(nextQuests);
        Map<String, QuestDefinition> nextIndex = new HashMap<>();
        for (var quest : quests) nextIndex.putIfAbsent(quest.id(), quest);
        questIndex = Map.copyOf(nextIndex);
        groups = List.copyOf(scan.groups());
        chapters = List.copyOf(scan.chapters());
        filesById = Map.copyOf(scan.files());
        questGroups = Map.copyOf(scan.membership());
        unlockLayout = scan.unlockLayout();
        revision++;
        return true;
    }

    /** Reads and validates every definition file without touching the live snapshot. */
    public static Scan scan() {
        long revisionAtStart = revision();
        List<QuestDefinition> nextQuests = new ArrayList<>();
        List<Group> nextGroups = new ArrayList<>();
        List<Chapter> nextChapters = new ArrayList<>();
        Map<String, Path> nextFiles = new LinkedHashMap<>();
        Map<String, String> nextMembership = new HashMap<>();
        List<String> problems = new ArrayList<>();
        try {
            Files.createDirectories(root());
            List<Path> paths;
            try (var walk = Files.walk(root())) {
                paths = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> !path.getFileName().toString().endsWith(".tmp")).sorted().toList();
            }
            for (Path path : paths) {
                if (!path.getFileName().toString().equals("group_manifest.json")) {
                    continue;
                }
                try {
                    var data = JsonParser.parseString(read(path)).getAsJsonObject();
                    String id = data.get("id").getAsString();
                    register(nextFiles, id, path);
                    String icon = data.has("icon") ? data.get("icon").getAsString() : "";
                    nextGroups.add(new Group(id, data.has("name") ? data.get("name").getAsString() : id,
                            data.has("description") ? data.get("description").getAsString() : "",
                            icon, data.has("type") ? data.get("type").getAsString() : "active",
                            iconConfig(data, "leadingIcon", ListIconConfig.leading(icon)),
                            iconConfig(data, "trailingIcon", ListIconConfig.trailing()), path.getParent(),
                            data.has("hotEvent") ? GSON.fromJson(data.get("hotEvent"), HotEventData.class)
                                    : HotEventData.empty(),
                            data.has("unlocked") ? data.get("unlocked").getAsBoolean()
                                    : !data.has("defaultUnlocked") || data.get("defaultUnlocked").getAsBoolean(),
                            data.has("recommended") && data.get("recommended").getAsBoolean(),
                            data.has("limitedReward") && data.get("limitedReward").getAsBoolean(),
                            data.has("listAccent") ? data.get("listAccent").getAsString() : "auto",
                            data.has("focusAvailable") && data.get("focusAvailable").getAsBoolean()));
                } catch (Exception exception) {
                    problems.add(path + ": " + exception.getMessage());
                }
            }
            for (Path path : paths) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.equals("group_manifest.json")) {
                    continue;
                }
                if (!name.endsWith(".html") && !name.endsWith(".htm") && !name.endsWith(".xml") && !name.endsWith(".json")) {
                    continue;
                }
                try {
                    String source = read(path);
                    if (name.endsWith(".chapter.json") || name.equals("chapter_manifest.json")) {
                        var data = JsonParser.parseString(source).getAsJsonObject();
                        String id = data.get("id").getAsString();
                        register(nextFiles, id, path);
                        List<String> refs = new ArrayList<>();
                        if (data.has("quests")) {
                            data.getAsJsonArray("quests").forEach(value -> refs.add(value.getAsString()));
                        }
                        nextChapters.add(new Chapter(id, data.get("name").getAsString(),
                                data.has("description") ? data.get("description").getAsString() : "",
                                data.has("icon") ? data.get("icon").getAsString() : "",
                                data.get("group").getAsString(), List.copyOf(refs), path,
                                data.has("journeyBackgrounds") ? List.of(GSON.fromJson(
                                        data.get("journeyBackgrounds"), JourneyBackdrop[].class)) : List.of(),
                                data.has("journeyAppearance") ? GSON.fromJson(data.get("journeyAppearance"),
                                        JourneyAppearance.class) : JourneyAppearance.empty(),
                                data.has("journeyTimeline") ? GSON.fromJson(data.get("journeyTimeline"),
                                        xtdpotato.xero_quest_journal.core.model.JourneyTimeline.class) : null));
                        continue;
                    }
                    QuestDefinition quest = parseDefinition(path, source);
                    register(nextFiles, quest.id(), path);
                    nextQuests.add(quest);
                    String group = nextGroups.stream().filter(candidate -> path.startsWith(candidate.directory()))
                            .max(Comparator.comparingInt(candidate -> candidate.directory().getNameCount()))
                            .map(Group::id).orElse("");
                    nextMembership.put(quest.id(), group);
                } catch (Exception exception) {
                    problems.add(path + ": " + exception.getMessage());
                }
            }
            validateDependencies(nextQuests, nextGroups, nextChapters, problems);
            QuestUnlockLayout nextUnlockLayout = QuestUnlockLayout.empty();
            Path unlockFile = unlockLayoutFile();
            if (Files.exists(unlockFile)) {
                try {
                    QuestUnlockLayout readLayout = GSON.fromJson(read(unlockFile), QuestUnlockLayout.class);
                    nextUnlockLayout = new QuestUnlockLayout(readLayout.positions());
                } catch (Exception invalid) {
                    problems.add(unlockFile + ": " + invalid.getMessage());
                }
            }
            var nextJournalOrder = journalOrder();
            try { nextJournalOrder = QuestJournalOrderStore.read(); }
            catch (Exception invalid) { problems.add(QuestJournalOrderStore.file() + ": " + invalid.getMessage()); }
            return new Scan(nextQuests, nextGroups, nextChapters, nextFiles, nextMembership, nextUnlockLayout,
                    nextJournalOrder, problems, revisionAtStart);
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.error("Unable to scan definitions; retaining previous snapshot", exception);
            return new Scan(List.of(), List.of(), List.of(), Map.of(), Map.of(), QuestUnlockLayout.empty(),
                    journalOrder(), List.of(exception.toString()), revisionAtStart);
        }
    }

    public static synchronized void templatesChanged() {
        revision++;
    }

    public static synchronized void typesChanged() {
        revision++;
    }

    public static synchronized xtdpotato.xero_quest_journal.core.model.QuestJournalOrder journalOrder() {
        return journalOrder;
    }

    public static synchronized void journalOrderChanged(
            xtdpotato.xero_quest_journal.core.model.QuestJournalOrder order) {
        journalOrder = order;
        revision++;
    }

    public static synchronized void hotEventsChanged() {
        revision++;
    }

    private static String read(Path path) throws java.io.IOException {
        if (Files.size(path) > 1048576) {
            throw new java.io.IOException("Definition exceeds 1 MiB");
        }
        return Files.readString(path);
    }

    private static void register(Map<String, Path> index, String id, Path path) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Missing ID");
        }
        Path previous = index.putIfAbsent(id, path);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate ID " + id + " in " + previous + " and " + path);
        }
    }

    public static QuestDefinition parseMarkup(String source, boolean xml) {
        var document = Jsoup.parse(source, "", xml ? Parser.xmlParser() : Parser.htmlParser());
        Element quest = document.selectFirst("quest");
        if (quest == null || quest.attr("id").isBlank()) {
            throw new IllegalArgumentException("Missing quest element or ID");
        }
        String css = document.select("style").stream().map(element -> element.data().isEmpty() ? element.text() : element.data())
                .collect(java.util.stream.Collectors.joining("\n"));
        Element content = quest.selectFirst("content");
        Element text = (content == null ? quest : content).clone();
        text.select("script,style,parents,trigger,conditions,rewards,visual,hot-event,legendary,steps").remove();
        // XML parsing recognizes self-closing rule tags used by both authoring formats.
        Element rules = Jsoup.parse(source, "", Parser.xmlParser()).selectFirst("quest");
        List<String> parents = rules.select("parents > parent").stream().map(parent -> parent.attr("id")).toList();
        List<QuestDefinition.Trigger> triggers = rules.children().stream()
                .filter(element -> element.normalName().equals("trigger") || element.normalName().equals("conditions"))
                .map(element -> parseTrigger(element, 0)).toList();
        List<QuestDefinition.Reward> rewards = rules.select("rewards > reward").stream()
                .map(reward -> new QuestDefinition.Reward(reward.hasAttr("type") ? reward.attr("type") : "item",
                        reward.hasAttr("target") ? reward.attr("target") : reward.attr("item"),
                        number(reward, "count", 1), reward.attr("quality"), reward.attr("background"))).toList();
        return new QuestDefinition(quest.attr("id"), quest.attr("name"), text.text(), quest.attr("type"),
                quest.attr("icon"), !quest.attr("trackable").equalsIgnoreCase("false"),
                quest.attr("recommended").equalsIgnoreCase("true"), number(quest, "recommendation-level", 0), null,
                quest.attr("title-key"), quest.attr("description-key"), css, quest.attr("uid"),
                parents, triggers, rewards, quest.attr("reward-mode"), quest.attr("template"),
                iconConfig(quest, "leading", ListIconConfig.leading(quest.attr("icon"))),
                iconConfig(quest, "trailing", ListIconConfig.trailing()),
                hotEvent(quest.selectFirst("hot-event")), QuestPresentationDocuments.read(rules.selectFirst("visual")),
                new xtdpotato.xero_quest_journal.core.model.CompletionPolicy(
                        Boolean.parseBoolean(quest.attr("completed-events")),
                        Boolean.parseBoolean(quest.attr("completed-reenter-structure")),
                        Boolean.parseBoolean(quest.attr("completed-export-items")),
                        Boolean.parseBoolean(quest.attr("structure-progress")),
                        !quest.hasAttr("automatic-reward-popup")
                                || Boolean.parseBoolean(quest.attr("automatic-reward-popup"))),
                !quest.hasAttr("unlocked") && !quest.hasAttr("default-unlocked")
                        || !quest.hasAttr("unlocked") && Boolean.parseBoolean(quest.attr("default-unlocked"))
                        || quest.hasAttr("unlocked") && Boolean.parseBoolean(quest.attr("unlocked")),
                Boolean.parseBoolean(quest.attr("hide-when-locked")),
                Boolean.parseBoolean(quest.attr("hidden")),
                !quest.hasAttr("triggers-enabled") || Boolean.parseBoolean(quest.attr("triggers-enabled")),
                Boolean.parseBoolean(quest.attr("limited-reward")),
                legendary(quest.selectFirst("legendary")),
                Boolean.parseBoolean(quest.attr("focus-available")), quest.attr("list-accent"),
                quest.hasAttr("journey-appearance") ? GSON.fromJson(quest.attr("journey-appearance"),
                        JourneyAppearance.class) : null,
                !quest.hasAttr("journey-visible") || Boolean.parseBoolean(quest.attr("journey-visible")),
                quest.attr("subtitle"), quest.attr("objective"), quest.attr("place"),
                steps(rules), Boolean.parseBoolean(quest.attr("daily")));
    }

    /** {@code <steps><step id title description><trigger .../></step></steps>}, in authored order. */
    private static List<QuestDefinition.Step> steps(Element rules) {
        Element container = rules.children().stream().filter(child -> child.normalName().equals("steps"))
                .findFirst().orElse(null);
        if (container == null) return List.of();
        List<QuestDefinition.Step> result = new ArrayList<>();
        for (Element step : container.children()) {
            if (!step.normalName().equals("step")) continue;
            List<QuestDefinition.Trigger> conditions = step.children().stream()
                    .filter(element -> element.normalName().equals("trigger")
                            || element.normalName().equals("conditions"))
                    .map(element -> parseTrigger(element, 0)).toList();
            String description = step.hasAttr("description") ? step.attr("description")
                    : step.children().stream().filter(child -> child.normalName().equals("description"))
                    .map(Element::text).findFirst().orElse("");
            result.add(new QuestDefinition.Step(step.attr("id"), step.attr("title"), description, conditions));
        }
        return List.copyOf(result);
    }

    private static xtdpotato.xero_quest_journal.core.model.LegendaryData legendary(Element element) {
        if (element == null) return xtdpotato.xero_quest_journal.core.model.LegendaryData.empty();
        var filters = new java.util.LinkedHashMap<String, List<String>>();
        for (Element option : element.select("filter[group][name]"))
            filters.computeIfAbsent(option.attr("group"), ignored -> new java.util.ArrayList<>())
                    .add(option.attr("name"));
        return new xtdpotato.xero_quest_journal.core.model.LegendaryData(
                element.attr("image"), element.attr("subtitle"), filters);
    }

    private static HotEventData hotEvent(Element element) {
        if (element == null) return HotEventData.empty();
        return new HotEventData(Boolean.parseBoolean(element.attr("enabled")), element.attr("image"),
                element.attr("title"), number(element, "x", 0), number(element, "y", 0),
                Boolean.parseBoolean(element.attr("positioned")), number(element, "image-width", 0),
                number(element, "image-height", 0),
                element.hasAttr("show-image") && !Boolean.parseBoolean(element.attr("show-image")));
    }

    private static QuestDefinition.Trigger parseTrigger(Element element, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Condition nesting exceeds 16");
        return new QuestDefinition.Trigger(element.hasAttr("type") ? element.attr("type") : "all",
                element.hasAttr("target") ? element.attr("target") : element.attr("item"),
                number(element, "count", 1), element.children().stream()
                .filter(child -> child.normalName().equals("trigger") || child.normalName().equals("conditions"))
                .map(child -> parseTrigger(child, depth + 1)).toList(),
                element.attr("button-text"), element.attr("button-shape"));
    }

    public static QuestDefinition parseDefinition(Path path, String source) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".json")) return parseMarkup(source, name.endsWith(".xml"));
        var data = JsonParser.parseString(source).getAsJsonObject();
        if (!data.has("defaultUnlocked")) {
            data.addProperty("defaultUnlocked", data.has("unlocked")
                    ? data.get("unlocked").getAsBoolean() : true);
        }
        if (!data.has("triggersEnabled")) data.addProperty("triggersEnabled", true);
        return GSON.fromJson(data, QuestDefinition.class);
    }

    private static int number(Element element, String attribute, int fallback) {
        return element.hasAttr(attribute) ? Integer.parseInt(element.attr(attribute)) : fallback;
    }

    private static ListIconConfig iconConfig(com.google.gson.JsonObject data, String name,
                                             ListIconConfig fallback) {
        if (!data.has(name) || !data.get(name).isJsonObject()) return fallback;
        var value = data.getAsJsonObject(name);
        return new ListIconConfig(
                value.has("image") ? value.get("image").getAsString() : fallback.image(),
                value.has("trackedImage") ? value.get("trackedImage").getAsString() : fallback.trackedImage(),
                !value.has("visible") || value.get("visible").getAsBoolean(),
                value.has("replaceWhenTracked") && value.get("replaceWhenTracked").getAsBoolean(),
                value.has("alpha") ? value.get("alpha").getAsFloat() : fallback.alpha(),
                value.has("width") ? value.get("width").getAsInt() : fallback.width(),
                value.has("height") ? value.get("height").getAsInt() : fallback.height(),
                value.has("offsetX") ? value.get("offsetX").getAsInt() : fallback.offsetX(),
                value.has("offsetY") ? value.get("offsetY").getAsInt() : fallback.offsetY(),
                value.has("trackingStyle") ? value.get("trackingStyle").getAsString() : fallback.trackingStyle());
    }

    private static ListIconConfig iconConfig(Element element, String prefix, ListIconConfig fallback) {
        String image = prefix + "-icon";
        String tracked = prefix + "-tracked-icon";
        String visible = prefix + "-visible";
        String replace = prefix + "-replace-when-tracked";
        String alpha = prefix + "-alpha";
        String width = prefix + "-width";
        String height = prefix + "-height";
        String offsetX = prefix + "-x";
        String offsetY = prefix + "-y";
        String trackingStyle = prefix + "-tracking-style";
        boolean configured = element.hasAttr(image) || element.hasAttr(tracked) || element.hasAttr(visible)
                || element.hasAttr(replace) || element.hasAttr(alpha) || element.hasAttr(width)
                || element.hasAttr(height) || element.hasAttr(offsetX) || element.hasAttr(offsetY)
                || element.hasAttr(trackingStyle);
        if (!configured) return fallback;
        return new ListIconConfig(
                element.hasAttr(image) ? element.attr(image) : fallback.image(),
                element.hasAttr(tracked) ? element.attr(tracked) : fallback.trackedImage(),
                !element.hasAttr(visible) || Boolean.parseBoolean(element.attr(visible)),
                element.hasAttr(replace) && Boolean.parseBoolean(element.attr(replace)),
                element.hasAttr(alpha) ? Float.parseFloat(element.attr(alpha)) : fallback.alpha(),
                number(element, width, fallback.width()), number(element, height, fallback.height()),
                number(element, offsetX, fallback.offsetX()), number(element, offsetY, fallback.offsetY()),
                element.hasAttr(trackingStyle) ? element.attr(trackingStyle) : fallback.trackingStyle());
    }

    private static void validateDependencies(List<QuestDefinition> quests, List<Group> groups,
                                             List<Chapter> chapters, List<String> problems) {
        Map<String, QuestDefinition> index = new HashMap<>();
        Set<String> identities = new HashSet<>();
        for (var quest : quests) {
            index.put(quest.id(), quest);
            if (!identities.add(quest.progressKey())) {
                problems.add("Duplicate quest identity: " + quest.progressKey());
            }
        }
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (var quest : quests) {
            incoming.put(quest.id(), quest.parents().size());
            for (String parent : quest.parents()) {
                if (!index.containsKey(parent)) {
                    problems.add("Missing prerequisite " + parent + " in " + quest.id());
                }
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(quest.id());
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        incoming.forEach((id, count) -> { if (count == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.remove();
            visited++;
            for (String child : children.getOrDefault(id, List.of())) {
                if (incoming.compute(child, (key, count) -> count - 1) == 0) {
                    ready.add(child);
                }
            }
        }
        if (visited != quests.size()) {
            problems.add("Cyclic or unresolved quest prerequisites");
        }
        Set<String> groupIds = new HashSet<>();
        groups.forEach(group -> groupIds.add(group.id()));
        for (Chapter chapter : chapters) {
            if (!groupIds.contains(chapter.groupId())) problems.add("Missing group in chapter " + chapter.id());
            for (String ref : chapter.quests()) {
                if (!index.containsKey(ref)) problems.add("Missing quest " + ref + " in chapter " + chapter.id());
            }
        }
    }

    public static synchronized List<QuestDefinition> all() {
        return quests;
    }

    public static synchronized QuestDefinition find(String id) {
        return id == null ? null : questIndex.get(id);
    }

    public static synchronized List<Group> groups() {
        return groups;
    }

    public static synchronized List<Chapter> chapters() {
        return chapters;
    }

    public static synchronized boolean idExists(String id) {
        return filesById.containsKey(id) || find(id) != null;
    }

    public static synchronized String groupOf(String quest) {
        return questGroups.getOrDefault(quest, "");
    }

    public static synchronized List<String> errors() {
        return errors;
    }

    public static synchronized long revision() {
        return revision;
    }

    public static Path unlockLayoutFile() {
        return root().getParent().resolve("unlock_layout.json");
    }

    public static synchronized QuestUnlockLayout unlockLayout() { return unlockLayout; }

    public static synchronized Path fileOf(String id) {
        return filesById.get(id);
    }

    public static String source(String id) throws java.io.IOException {
        Path path = fileOf(id);
        return path == null ? "" : read(path);
    }

    public static String fingerprint(String source) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
