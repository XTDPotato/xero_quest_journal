package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jsoup.nodes.Element;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestTypeStore;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.model.HotEventData;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.CompletionPolicy;
import xtdpotato.xero_quest_journal.core.model.QuestUnlockLayout;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;
import xtdpotato.xero_quest_journal.core.model.LegendaryData;

import java.nio.file.*;
import java.util.*;

public final class QuestAuthoring {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Kind { GROUP, CHAPTER, TASK }

    public record Request(Kind kind, String id, String name, String description, String group,
                          String chapter, String taskType, String item, int count, String icon,
                          String parents, String rewardItem, int rewardCount, String rewardMode,
                          String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                          List<QuestDefinition.Trigger> defaultConditions,
                          List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                          CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                          boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                          boolean limitedReward, List<JourneyBackdrop> journeyBackgrounds,
                          JourneyAppearance journeyAppearance, LegendaryData legendary, Boolean focusAvailable,
                          String listAccent, Boolean journeyVisible, String objective) {
        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                       boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                       boolean limitedReward, List<JourneyBackdrop> journeyBackgrounds,
                       JourneyAppearance journeyAppearance, LegendaryData legendary, Boolean focusAvailable,
                       String listAccent, Boolean journeyVisible) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon, defaultConditions,
                    defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended, hideWhenLocked, hidden,
                    triggersEnabled, limitedReward, journeyBackgrounds, journeyAppearance, legendary,
                    focusAvailable, listAccent, journeyVisible, null);
        }
        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions, List<QuestDefinition.Reward> defaultRewards,
                       HotEventData hotEvent, CompletionPolicy completionPolicy, boolean defaultUnlocked,
                       boolean recommended, boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                       boolean limitedReward, List<JourneyBackdrop> journeyBackgrounds,
                       JourneyAppearance journeyAppearance, LegendaryData legendary, Boolean focusAvailable,
                       String listAccent) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    hideWhenLocked, hidden, triggersEnabled, limitedReward, journeyBackgrounds,
                    journeyAppearance, legendary, focusAvailable, listAccent, null);
        }
        public Request {
            if (kind == Kind.GROUP && (taskType == null || taskType.isBlank() || taskType.equals("normal"))) {
                taskType = "active";
            }
            leadingIcon = leadingIcon == null ? ListIconConfig.leading(icon) : leadingIcon;
            trailingIcon = trailingIcon == null ? ListIconConfig.trailing() : trailingIcon;
            defaultConditions = defaultConditions == null ? List.of() : List.copyOf(defaultConditions);
            defaultRewards = defaultRewards == null ? List.of() : List.copyOf(defaultRewards);
            hotEvent = hotEvent == null ? HotEventData.empty() : hotEvent;
            completionPolicy = completionPolicy == null ? CompletionPolicy.defaults() : completionPolicy;
            journeyBackgrounds = journeyBackgrounds == null ? List.of() : List.copyOf(journeyBackgrounds);
            legendary = legendary == null ? LegendaryData.empty() : legendary;
            if (listAccent != null)
                listAccent = xtdpotato.xero_quest_journal.core.model.QuestListAccent.normalize(listAccent);
            if (objective != null && objective.length() > 512)
                throw new IllegalArgumentException("Task objective exceeds 512 characters");
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                       boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                       boolean limitedReward, List<JourneyBackdrop> journeyBackgrounds,
                       JourneyAppearance journeyAppearance, LegendaryData legendary, Boolean focusAvailable) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    hideWhenLocked, hidden, triggersEnabled, limitedReward, journeyBackgrounds,
                    journeyAppearance, legendary, focusAvailable, null);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                       boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                       boolean limitedReward, List<JourneyBackdrop> journeyBackgrounds,
                       JourneyAppearance journeyAppearance, LegendaryData legendary) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    hideWhenLocked, hidden, triggersEnabled, limitedReward, journeyBackgrounds,
                    journeyAppearance, legendary, null);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                       boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                       boolean limitedReward, List<JourneyBackdrop> journeyBackgrounds,
                       JourneyAppearance journeyAppearance) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    hideWhenLocked, hidden, triggersEnabled, limitedReward, journeyBackgrounds,
                    journeyAppearance, null);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                       boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                       boolean limitedReward, List<JourneyBackdrop> journeyBackgrounds) {
            // Null means an older caller did not edit appearance; preserve it on update.
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    hideWhenLocked, hidden, triggersEnabled, limitedReward, journeyBackgrounds, null);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                       boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                       boolean limitedReward) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    hideWhenLocked, hidden, triggersEnabled, limitedReward, List.of());
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended,
                       boolean hideWhenLocked, boolean hidden, boolean triggersEnabled) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    hideWhenLocked, hidden, triggersEnabled, false);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked, boolean recommended) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, recommended,
                    false, false, true, false);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent,
                       CompletionPolicy completionPolicy, boolean defaultUnlocked) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, completionPolicy, defaultUnlocked, false);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards, HotEventData hotEvent) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, hotEvent, null, true);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    List.of(), List.of(), null, null, true);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                       List<QuestDefinition.Trigger> defaultConditions,
                       List<QuestDefinition.Reward> defaultRewards) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon, parents,
                    rewardItem, rewardCount, rewardMode, templateId, leadingIcon, trailingIcon,
                    defaultConditions, defaultRewards, null, null, true);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode,
                       String templateId) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon,
                    parents, rewardItem, rewardCount, rewardMode, templateId, null, null,
                    List.of(), List.of(), null, null, true);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon,
                       String parents, String rewardItem, int rewardCount, String rewardMode) {
            this(kind, id, name, description, group, chapter, taskType, item, count, icon,
                    parents, rewardItem, rewardCount, rewardMode, "", null, null, List.of(), List.of(), null, null, true);
        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count) {
            this(kind, id, name, description, group, chapter, taskType, item, count,
                    "", "", "", 1, "default", "", null, null, List.of(), List.of(), null, null, true);

        }

        public Request(Kind kind, String id, String name, String description, String group,
                       String chapter, String taskType, String item, int count, String icon) {
            this(kind, id, name, description, group, chapter, taskType, item, count,
                    icon, "", "", 1, "default", "", null, null, List.of(), List.of(), null, null, true);
        }
    }

    public record Result(String id, String error) {
        public boolean successful() {
            return error == null;
        }
    }

    private QuestAuthoring() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static Result create(ServerPlayer author, Request request) {
        return create(author, request, null);
    }

    public static Result create(ServerPlayer author, Request request, QuestJournalOrderStore.Placement placement) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2) || placement != null
                && !xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime.canEdit(author)) {
            return failure("permission");
        }
        synchronized (QuestRepository.class) {
            QuestRepository.reload();
            if (!QuestRepository.errors().isEmpty()) {
                return failure("definitions");
            }
            if (!QuestJournalOrderStore.validPlacement(request, placement)) return failure("group");
            if (request.id() == null || !request.id().matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")) {
                return failure("id");
            }
            if (QuestRepository.idExists(request.id())) {
                return failure("duplicate");
            }
            if (request.name() == null || request.name().isBlank()) {
                return failure("name");
            }
            if (request.name().length() > 128 || request.description() == null || request.description().length() > 4096) {
                return failure("size");
            }
            if (request.kind() == Kind.GROUP && !validGroupType(request.taskType())) {
                return failure("definitions");
            }
            if (!validJourneyBackgrounds(request.journeyBackgrounds())) return failure("definitions");
            QuestRepository.Group group = QuestRepository.groups().stream()
                    .filter(candidate -> candidate.id().equals(request.group())).findFirst().orElse(null);
            if (request.kind() == Kind.CHAPTER && group == null
                    || request.kind() == Kind.TASK && group == null && !request.group().isEmpty()) {
                return failure("group");
            }
            QuestRepository.Chapter chapter = QuestRepository.chapters().stream()
                    .filter(candidate -> candidate.id().equals(request.chapter())).findFirst().orElse(null);
            if (chapter != null && !chapter.groupId().equals(request.group())) {
                return failure("group");
            }
            if (!request.chapter().isEmpty() && chapter == null) {
                return failure("chapter");
            }
            if (request.kind() == Kind.TASK) {
                for (String parent : parseParents(request.parents())) {
                    if (parent.equals(request.id()) || QuestRepository.find(parent) == null) return failure("parent");
                }
                if (!validReward(request.rewardItem(), request.rewardCount())
                        || request.defaultRewards().size() > 128
                        || request.defaultRewards().stream().anyMatch(reward ->
                        !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(reward))
                        || !xtdpotato.xero_quest_journal.core.runtime.QuestConditions.valid(request.defaultConditions())) {
                    return failure("reward");
                }
            }
            Path createdFile = null;
            Path changedChapter = null;
            String chapterBefore = null;
            try {
                Path folder = safe(group == null ? QuestRepository.root() : group.directory());
                JsonObject metadata = new JsonObject();
                metadata.addProperty("id", request.id());
                metadata.addProperty("uid", UUID.randomUUID().toString());
                metadata.addProperty("name", request.name().strip());
                metadata.addProperty("description", request.description());
                metadata.addProperty("unlocked", request.defaultUnlocked());
                if (!request.icon().isBlank()) {
                    metadata.addProperty("icon", request.icon());
                }
                metadata.add("leadingIcon", GSON.toJsonTree(request.leadingIcon()));
                metadata.add("trailingIcon", GSON.toJsonTree(request.trailingIcon()));
                metadata.add("hotEvent", GSON.toJsonTree(request.hotEvent()));
                if (request.kind() == Kind.GROUP) {
                    metadata.addProperty("type", request.taskType());
                    metadata.addProperty("recommended", request.recommended());
                    metadata.addProperty("limitedReward", request.limitedReward());
                    metadata.addProperty("listAccent", request.listAccent() == null ? "auto" : request.listAccent());
                    metadata.addProperty("focusAvailable", Boolean.TRUE.equals(request.focusAvailable()));
                    Path created = safe(folder.resolve("group_" + request.id())).resolve("group_manifest.json");
                    AtomicFiles.write(created, GSON.toJson(metadata), false);
                    createdFile = created;
                } else if (request.kind() == Kind.CHAPTER) {
                    metadata.addProperty("group", request.group());
                    metadata.add("quests", new JsonArray());
                    metadata.add("journeyBackgrounds", GSON.toJsonTree(request.journeyBackgrounds()));
                    if (request.journeyAppearance() != null)
                        metadata.add("journeyAppearance", GSON.toJsonTree(request.journeyAppearance()));
                    Path created = safe(folder.resolve(request.id() + ".chapter.json"));
                    AtomicFiles.write(created, GSON.toJson(metadata), false);
                    createdFile = created;
                } else {
                    Element root = new Element("quest").attr("id", request.id()).attr("name", request.name().strip())
                            .attr("uid", UUID.randomUUID().toString()).attr("type", request.taskType())
                            .attr("reward-mode", request.rewardMode())
                            .attr("completed-events", Boolean.toString(request.completionPolicy().events()))
                            .attr("completed-reenter-structure", Boolean.toString(request.completionPolicy().reenterStructure()))
                            .attr("completed-export-items", Boolean.toString(request.completionPolicy().exportAcquiredItems()))
                            .attr("structure-progress", Boolean.toString(request.completionPolicy().structureProgress()))
                            .attr("automatic-reward-popup",
                                    Boolean.toString(request.completionPolicy().automaticRewardPopup()))
                            .attr("recommended", Boolean.toString(request.recommended()))
                            .attr("limited-reward", Boolean.toString(request.limitedReward()))
                            .attr("unlocked", Boolean.toString(request.defaultUnlocked()))
                            .attr("hide-when-locked", Boolean.toString(request.hideWhenLocked()))
                            .attr("hidden", Boolean.toString(request.hidden()))
                            .attr("triggers-enabled", Boolean.toString(request.triggersEnabled()))
                            .attr("list-accent", request.listAccent() == null ? "auto" : request.listAccent());
                    if (!request.templateId().isBlank()) {
                        root.attr("template", request.templateId());
                    }
                    if (!request.icon().isBlank()) {
                        root.attr("icon", request.icon());
                    }
                    QuestDocuments.iconAttributes(root, request.leadingIcon(), request.trailingIcon());
                    List<String> parents = parseParents(request.parents());
                    if (!parents.isEmpty()) {
                        Element parentList = root.appendElement("parents");
                        parents.forEach(parent -> parentList.appendElement("parent").attr("id", parent));
                    }
                    root.appendElement("content").appendElement("p").text(request.description());
                    if (request.objective() != null && !request.objective().isBlank())
                        root.attr("objective", request.objective().strip());
                    if (!request.rewardItem().isBlank()) {
                        root.appendElement("rewards").appendElement("reward").attr("type", "item")
                                .attr("item", request.rewardItem()).attr("count", Integer.toString(request.rewardCount()));
                    }
                    String taskSource = root.outerHtml();
                    taskSource = QuestDocuments.journeyAppearance(taskSource, request.journeyAppearance());
                    taskSource = QuestDocuments.journeyVisible(taskSource, !Boolean.FALSE.equals(request.journeyVisible()));
                    taskSource = QuestDocuments.hotEvent(taskSource, request.hotEvent());
                    taskSource = QuestDocuments.legendary(taskSource, request.legendary());
                    if (!request.defaultConditions().isEmpty()) {
                        taskSource = QuestDocuments.conditions(taskSource, request.defaultConditions());
                    }
                    if (!request.defaultRewards().isEmpty()) {
                        taskSource = QuestDocuments.rewards(taskSource, request.defaultRewards(), request.rewardMode());
                    }
                    Path created = safe(folder.resolve("quest_" + request.id() + ".html"));
                    AtomicFiles.write(created, taskSource, false);
                    createdFile = created;
                    if (chapter != null && (!chapter.quests().isEmpty() || chapter.timeline().explicitTasks())) {
                        try {
                            Path chapterFile = safe(chapter.file());
                            chapterBefore = Files.readString(chapterFile);
                            changedChapter = chapterFile;
                            JsonObject data = JsonParser.parseString(chapterBefore).getAsJsonObject();
                            if (!data.has("quests")) data.add("quests", new JsonArray());
                            data.getAsJsonArray("quests").add(request.id());
                            AtomicFiles.write(chapterFile, GSON.toJson(data), true);
                        } catch (Exception failure) {
                            Files.delete(created); // Roll back only the file just created by this operation.
                            throw failure;
                        }
                    }
                }
                QuestRepository.reload();
                if (placement != null) QuestJournalOrderStore.placeNew(request.id(), placement);
                return new Result(request.id(), null);
            } catch (FileAlreadyExistsException exception) {
                return failure("duplicate");
            } catch (Exception exception) {
                if (placement != null) {
                    try {
                        if (createdFile != null) Files.deleteIfExists(createdFile);
                        if (changedChapter != null && chapterBefore != null)
                            AtomicFiles.write(changedChapter, chapterBefore, true);
                        QuestRepository.reload();
                    } catch (Exception rollback) { exception.addSuppressed(rollback); }
                }
                XeroQuestJournal.LOGGER.warn("Unable to create {} {}", request.kind(), request.id(), exception);
                return failure("save");
            }
        }
    }

    public static Result edit(ServerPlayer author, Request request, String expectedHash,
                              boolean replaceDescription, boolean replaceRules) {
        return edit(author, request, expectedHash, replaceDescription, replaceRules, false);
    }

    public static Result edit(ServerPlayer author, Request request, String expectedHash,
                              boolean replaceDescription, boolean replaceRules, boolean replaceConditions) {
        return edit(author, request, expectedHash, replaceDescription, replaceRules, replaceConditions, false);
    }

    public static Result edit(ServerPlayer author, Request request, String expectedHash,
                              boolean replaceDescription, boolean replaceRules, boolean replaceConditions,
                              boolean replaceRewards) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) {
            return failure("permission");
        }
        if (replaceConditions && (request.kind() != Kind.TASK
                || !xtdpotato.xero_quest_journal.core.runtime.QuestConditions.valid(request.defaultConditions()))) {
            return failure("condition");
        }
        if (replaceRewards && (request.kind() != Kind.TASK || request.defaultRewards().size() > 128
                || request.defaultRewards().stream().anyMatch(reward ->
                !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(reward)))) return failure("reward");
        synchronized (QuestRepository.class) {
            try {
                Path file = QuestRepository.fileOf(request.id());
                if (file == null || request.name().isBlank() || request.name().length() > 128
                        || request.description().length() > 4096
                        || (!request.icon().isBlank() && ResourceLocation.tryParse(request.icon()) == null
                        && !xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue
                        .decode(request.icon(), author.registryAccess()).isPresent())) {
                    return failure("name");
                }
                if (request.kind() == Kind.GROUP && !validGroupType(request.taskType())) {
                    return failure("definitions");
                }
                if (!validJourneyBackgrounds(request.journeyBackgrounds())) return failure("definitions");
                String source = Files.readString(safe(file));
                if (!QuestRepository.fingerprint(source).equals(expectedHash)) {
                    return failure("conflict");
                }
                String updated;
                if (file.toString().endsWith(".json")) {
                    var data = JsonParser.parseString(source).getAsJsonObject();
                    String nameKey = request.kind() == Kind.TASK ? "title" : "name";
                    String oldName = data.has(nameKey) ? data.get(nameKey).getAsString() : request.id();
                    if (!oldName.equals(request.name())) {
                        data.addProperty(nameKey, request.name());
                        data.remove("titleKey");
                    }
                    if (request.icon().isBlank()) {
                        data.remove("icon");
                    } else {
                        data.addProperty("icon", request.icon());
                    }
                    data.add("leadingIcon", GSON.toJsonTree(request.leadingIcon()));
                    data.add("trailingIcon", GSON.toJsonTree(request.trailingIcon()));
                    data.add("hotEvent", GSON.toJsonTree(request.hotEvent()));
                    if (request.kind() == Kind.TASK && request.journeyAppearance() != null)
                        data.add("journeyAppearance", GSON.toJsonTree(request.journeyAppearance()));
                    if (request.kind() == Kind.TASK && request.journeyVisible() != null)
                        data.addProperty("journeyVisible", request.journeyVisible());
                    if (request.kind() != Kind.CHAPTER && request.listAccent() != null)
                        data.addProperty("listAccent", request.listAccent());
                    if (request.kind() == Kind.GROUP) {
                        data.addProperty("type", request.taskType());
                        data.addProperty("recommended", request.recommended());
                        data.addProperty("limitedReward", request.limitedReward());
                        data.addProperty("focusAvailable", Boolean.TRUE.equals(request.focusAvailable()));
                    }
                    if (request.kind() == Kind.CHAPTER) {
                        data.add("journeyBackgrounds", GSON.toJsonTree(request.journeyBackgrounds()));
                        if (request.journeyAppearance() != null)
                            data.add("journeyAppearance", GSON.toJsonTree(request.journeyAppearance()));
                    }
                    if (request.kind() == Kind.GROUP || request.kind() == Kind.TASK) {
                        data.addProperty("defaultUnlocked", request.defaultUnlocked());
                        data.addProperty("unlocked", request.defaultUnlocked());
                    }
                    if (request.kind() == Kind.TASK && replaceRules) {
                        data.addProperty("type", request.taskType());
                        data.addProperty("rewardMode", request.rewardMode());
                    }
                    if (request.kind() == Kind.TASK)
                        data.add("completionPolicy", GSON.toJsonTree(request.completionPolicy()));
                    if (request.kind() == Kind.TASK && request.objective() != null) {
                        if (request.objective().isBlank()) data.remove("objective");
                        else data.addProperty("objective", request.objective().strip());
                    }
                    if (request.kind() == Kind.TASK)
                        data.add("legendary", GSON.toJsonTree(request.legendary()));
                    if (request.kind() == Kind.TASK)
                        data.addProperty("recommended", request.recommended());
                    if (request.kind() == Kind.TASK)
                        data.addProperty("limitedReward", request.limitedReward());
                    if (request.kind() == Kind.TASK) {
                        data.addProperty("hideWhenLocked", request.hideWhenLocked());
                        data.addProperty("hidden", request.hidden());
                        data.addProperty("triggersEnabled", request.triggersEnabled());
                    }
                    if (request.kind() == Kind.TASK && replaceRewards) {
                        data.add("rewards", GSON.toJsonTree(request.defaultRewards()));
                        data.addProperty("rewardMode", request.rewardMode());
                    }
                    if (replaceDescription) {
                        data.addProperty("description", request.description());
                        data.remove("descriptionKey");
                    }
                    updated = GSON.toJson(data);
                } else {
                    if (!QuestRepository.parseDefinition(file, source).id().equals(request.id())) {
                        return failure("definitions");
                    }
                    updated = QuestDocuments.metadata(source, request.name(), request.icon(), request.description(),
                            replaceDescription, request.leadingIcon(), request.trailingIcon());
                    if (request.kind() == Kind.TASK) {
                        if (request.objective() != null)
                            updated = QuestDocuments.objective(updated, request.objective());
                        updated = QuestDocuments.hotEvent(updated, request.hotEvent());
                        updated = QuestDocuments.journeyAppearance(updated, request.journeyAppearance());
                        if (request.journeyVisible() != null)
                            updated = QuestDocuments.journeyVisible(updated, request.journeyVisible());
                        updated = QuestDocuments.legendary(updated, request.legendary());
                        updated = QuestDocuments.completionPolicy(updated, request.completionPolicy());
                        updated = QuestDocuments.defaultUnlocked(updated, request.defaultUnlocked());
                        updated = QuestDocuments.recommended(updated, request.recommended());
                        updated = QuestDocuments.limitedReward(updated, request.limitedReward());
                        updated = QuestDocuments.taskFlags(updated, request.hideWhenLocked(),
                                request.hidden(), request.triggersEnabled(), request.focusAvailable());
                        if (request.listAccent() != null)
                            updated = QuestDocuments.listAccent(updated, request.listAccent());
                    }
                    if (request.kind() == Kind.TASK && replaceRules) {
                        for (String parent : parseParents(request.parents())) {
                            if (parent.equals(request.id()) || QuestRepository.find(parent) == null) return failure("parent");
                        }
                        var previous = QuestRepository.parseDefinition(file, source);
                        var previousRewards = previous.rewards();
                        updated = QuestDocuments.rules(updated, request.taskType(), request.item(), request.count(),
                                request.parents(), "",
                                request.rewardCount(), request.rewardMode());
                        updated = QuestDocuments.rewards(updated,
                                replaceRewards ? request.defaultRewards() : previousRewards, request.rewardMode());
                    }
                    if (request.kind() == Kind.TASK && replaceRewards && !replaceRules)
                        updated = QuestDocuments.rewards(updated, request.defaultRewards(), request.rewardMode());
                }
                if (replaceConditions) {
                    if (file.toString().endsWith(".json")) {
                        var data = JsonParser.parseString(updated).getAsJsonObject();
                        data.add("triggers", GSON.toJsonTree(request.defaultConditions()));
                        if (request.defaultConditions().isEmpty() && data.has("type")
                                && data.get("type").getAsString().equals("guide")) data.addProperty("type", "main");
                        updated = GSON.toJson(data);
                    } else {
                        updated = QuestDocuments.conditions(updated, request.defaultConditions());
                    }
                }
                AtomicFiles.write(file, updated, true);
                QuestRepository.reload();
                return new Result(request.id(), null);
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to edit {}", request.id(), exception);
                return failure("save");
            }
        }
    }

    public static Result editSource(ServerPlayer author, String id, String expectedHash, String updatedSource) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) {
            return failure("permission");
        }
        synchronized (QuestRepository.class) {
            try {
                Path file = QuestRepository.fileOf(id);
                if (file == null || updatedSource == null
                        || updatedSource.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1048576) {
                    return failure(file == null ? "no_source" : "size");
                }
                file = safe(file);
                String current = Files.readString(file);
                if (!QuestRepository.fingerprint(current).equals(expectedHash)) {
                    return failure("conflict");
                }
                if (!QuestRepository.parseDefinition(file, updatedSource).id().equals(id)) {
                    return failure("definitions");
                }
                AtomicFiles.write(file, updatedSource, true);
                QuestRepository.reload();
                if (!QuestRepository.errors().isEmpty()) {
                    AtomicFiles.write(file, current, true);
                    QuestRepository.reload();
                    return failure("definitions");
                }
                return new Result(id, null);
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to save source for {}", id, exception);
                return failure("save");
            }
        }
    }

    /** Save an entire prerequisite DAG as task sources, rolling back all changed files on failure. */
    public static Result editUnlockGraph(ServerPlayer author, long expectedRevision,
                                         Map<String, List<String>> parentsByTask,
                                         Map<String, String> expectedHashes, QuestUnlockLayout layout) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return failure("permission");
        synchronized (QuestRepository.class) {
            if (QuestRepository.revision() != expectedRevision) return failure("conflict");
            var tasks = QuestRepository.all();
            if (parentsByTask == null || expectedHashes == null || layout == null
                    || tasks.size() > 4096 || parentsByTask.size() != tasks.size())
                return failure("definitions");
            Set<String> ids = new HashSet<>();
            tasks.forEach(task -> ids.add(task.id()));
            if (!parentsByTask.keySet().equals(ids)
                    || !ids.containsAll(layout.positions().keySet())
                    || !validPrerequisites(parentsByTask, ids)) return failure("parent");
            if (tasks.stream().anyMatch(task -> QuestRepository.fileOf(task.id()) == null))
                return failure("no_source");

            Map<Path, String> originals = new LinkedHashMap<>();
            Map<Path, String> updates = new LinkedHashMap<>();
            Path layoutFile = QuestRepository.unlockLayoutFile();
            String oldLayout = null;
            boolean hadLayout = false;
            boolean safeLayout = false;
            try {
                layoutFile = safeUnlockLayout(layoutFile);
                safeLayout = true;
                for (var task : tasks) {
                    Path file = safe(QuestRepository.fileOf(task.id()));
                    String source = Files.readString(file);
                    if (!QuestRepository.fingerprint(source).equals(expectedHashes.get(task.id())))
                        return failure("conflict");
                    List<String> parents = parentsByTask.get(task.id());
                    if (task.parents().equals(parents)) continue;
                    String updated;
                    if (file.toString().endsWith(".json")) {
                        var json = JsonParser.parseString(source).getAsJsonObject();
                        json.add("parents", GSON.toJsonTree(parents));
                        updated = GSON.toJson(json);
                    } else updated = QuestDocuments.parents(source, parents);
                    if (!QuestRepository.parseDefinition(file, updated).id().equals(task.id()))
                        return failure("definitions");
                    originals.put(file, source);
                    updates.put(file, updated);
                }
                hadLayout = Files.exists(layoutFile);
                if (hadLayout) oldLayout = Files.readString(layoutFile);
                for (var update : updates.entrySet()) AtomicFiles.write(update.getKey(), update.getValue(), true);
                AtomicFiles.write(layoutFile, GSON.toJson(layout), true);
                QuestRepository.reload();
                if (!QuestRepository.errors().isEmpty()) throw new IllegalStateException("Invalid unlock graph");
                return new Result("", null);
            } catch (Exception invalid) {
                XeroQuestJournal.LOGGER.warn("Unable to save unlock graph", invalid);
                for (var original : originals.entrySet()) {
                    try { AtomicFiles.write(original.getKey(), original.getValue(), true); }
                    catch (Exception rollback) { XeroQuestJournal.LOGGER.error("Unable to restore {}", original.getKey(), rollback); }
                }
                if (safeLayout) {
                    try {
                        if (hadLayout) AtomicFiles.write(layoutFile, oldLayout, true);
                        else Files.deleteIfExists(layoutFile);
                    } catch (Exception rollback) {
                        XeroQuestJournal.LOGGER.error("Unable to restore unlock layout", rollback);
                    }
                }
                QuestRepository.reload();
                return failure("definitions");
            }
        }
    }

    private static Path safeUnlockLayout(Path file) throws java.io.IOException {
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.equals(QuestRepository.root().getParent().resolve("unlock_layout.json")))
            throw new java.io.IOException("Invalid unlock layout path");
        for (Path cursor = absolute; cursor != null; cursor = cursor.getParent())
            if (Files.isSymbolicLink(cursor)) throw new java.io.IOException("Symlinked unlock layout path");
        return absolute;
    }

    private static boolean validPrerequisites(Map<String, List<String>> parentsByTask, Set<String> ids) {
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (var entry : parentsByTask.entrySet()) {
            List<String> parents = entry.getValue();
            if (parents == null || parents.size() > 128 || new HashSet<>(parents).size() != parents.size()
                    || !ids.containsAll(parents) || parents.contains(entry.getKey())) return false;
            incoming.put(entry.getKey(), parents.size());
            parents.forEach(parent -> children.computeIfAbsent(parent, ignored -> new ArrayList<>())
                    .add(entry.getKey()));
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        incoming.forEach((id, count) -> { if (count == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.remove();
            visited++;
            for (String child : children.getOrDefault(id, List.of()))
                if (incoming.compute(child, (key, count) -> count - 1) == 0) ready.add(child);
        }
        return visited == ids.size();
    }

    private static boolean validGroupType(String type) {
        return "active".equals(type) || QuestTypeStore.all().stream().anyMatch(value -> value.id().equals(type));
    }

    private static boolean validJourneyBackgrounds(List<JourneyBackdrop> values) {
        if (values == null || values.size() > 64) return false;
        Set<Integer> positions = new HashSet<>();
        try {
            for (JourneyBackdrop value : values) {
                if (value == null || !positions.add(value.startX())) return false;
                new JourneyBackdrop(value.startX(), value.source(), value.type(),
                        value.scale(), value.offsetX(), value.offsetY(), value.music(), value.mode());
            }
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        return true;
    }

    public static String editJourney(ServerPlayer author, String id, String expectedHash,
            xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork.Draft draft) {
        if (!xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime.canEdit(author)
                || !author.server.isSameThread()) return "permission";
        synchronized (QuestRepository.class) {
            var chapter = QuestRepository.chapters().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
            if (chapter == null) return "missing";
            if (draft == null || !validJourneyBackgrounds(draft.backgrounds())) return "invalid_config";
            var ids = new HashSet<>(draft.quests());
            if (ids.stream().anyMatch(task -> QuestRepository.find(task) == null)
                    || !ids.containsAll(draft.timeline().positions().keySet())
                    || draft.timeline().connections().stream().anyMatch(edge ->
                    !ids.contains(edge.from()) || !ids.contains(edge.to()))) return "invalid_config";
            try {
                Path file = safe(chapter.file());
                String source = Files.readString(file);
                if (!QuestRepository.fingerprint(source).equals(expectedHash)) return "conflict";
                var json = JsonParser.parseString(source).getAsJsonObject();
                json.add("quests", GSON.toJsonTree(draft.quests()));
                json.add("journeyTimeline", GSON.toJsonTree(draft.timeline()));
                json.add("journeyBackgrounds", GSON.toJsonTree(draft.backgrounds()));
                if (draft.name() != null) json.addProperty("name", draft.name());
                if (draft.icon() != null) json.addProperty("icon", draft.icon());
                if (draft.appearance() != null) json.add("journeyAppearance", GSON.toJsonTree(draft.appearance()));
                AtomicFiles.write(file, GSON.toJson(json), true);
                QuestRepository.reload();
                return "config_saved";
            } catch (Exception failure) {
                XeroQuestJournal.LOGGER.warn("Unable to save journey chapter", failure);
                return "config_save_failed";
            }
        }
    }

    public static Result editRewards(ServerPlayer author, String id, String expectedHash,
                                     List<QuestDefinition.Reward> rewards, String mode) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return failure("permission");
        if (rewards == null || rewards.size() > 128 || mode == null || !List.of("default", "manual", "automatic").contains(mode)
                || rewards.stream().anyMatch(reward ->
                !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(reward))) {
            return failure("reward");
        }
        synchronized (QuestRepository.class) {
            try {
                Path file = QuestRepository.fileOf(id);
                if (file == null) return failure("no_source");
                String source = Files.readString(safe(file));
                if (!QuestRepository.fingerprint(source).equals(expectedHash)) return failure("conflict");
                String updated;
                if (file.toString().endsWith(".json")) {
                    var data = JsonParser.parseString(source).getAsJsonObject();
                    data.add("rewards", GSON.toJsonTree(rewards));
                    data.addProperty("rewardMode", mode);
                    updated = GSON.toJson(data);
                } else {
                    updated = QuestDocuments.rewards(source, rewards, mode);
                }
                return editSource(author, id, expectedHash, updated);
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to edit rewards for {}", id, exception);
                return failure("save");
            }
        }
    }

    public static Result editConditions(ServerPlayer author, String id, String expectedHash,
                                        List<QuestDefinition.Trigger> conditions) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return failure("permission");
        if (conditions == null || !xtdpotato.xero_quest_journal.core.runtime.QuestConditions.valid(conditions))
            return failure("condition");
        synchronized (QuestRepository.class) {
            try {
                Path file = QuestRepository.fileOf(id);
                if (file == null) return failure("no_source");
                String source = Files.readString(safe(file));
                if (!QuestRepository.fingerprint(source).equals(expectedHash)) return failure("conflict");
                String updated;
                if (file.toString().endsWith(".json")) {
                    var data = JsonParser.parseString(source).getAsJsonObject();
                    data.add("triggers", GSON.toJsonTree(conditions));
                    updated = GSON.toJson(data);
                } else updated = QuestDocuments.conditions(source, conditions);
                return editSource(author, id, expectedHash, updated);
            } catch (Exception failure) {
                return failure("save");
            }
        }
    }

    private static Path safe(Path path) throws java.io.IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path root = QuestRepository.root();
        if (!absolute.startsWith(root)) {
            throw new java.io.IOException("Path is outside the quest root");
        }
        for (Path cursor = absolute; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) {
                throw new java.io.IOException("Symlinked authoring path");
            }
        }
        return absolute;
    }

    private static Result failure(String key) {
        return new Result("", "screen.xero_quest_journal.create.error." + key);
    }

    private static List<String> parseParents(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::strip).filter(parent -> !parent.isEmpty()).distinct().toList();
    }

    private static boolean validReward(String item, int count) {
        if (item == null || item.isBlank()) return true;
        if (xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue.encoded(item))
            return xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(
                    new QuestDefinition.Reward("item", item, count));
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && count > 0 && count <= 1000000;
    }
}
