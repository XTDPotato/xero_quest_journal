package xtdpotato.xero_quest_journal.core.model;

import java.util.List;

public record QuestDefinition(
        String id,
        String title,
        String description,
        String type,
        String icon,
        boolean trackable,
        boolean recommended,
        int recommendationLevel,
        LayoutNode layout,
        String titleKey,
        String descriptionKey,
        String buttonCss,
        String uid,
        List<String> parents,
        List<Trigger> triggers,
        List<Reward> rewards,
        String rewardMode,
        String templateId,
        ListIconConfig leadingIcon,
        ListIconConfig trailingIcon,
        HotEventData hotEvent,
        QuestPresentation presentation,
        CompletionPolicy completionPolicy,
        boolean defaultUnlocked,
        boolean hideWhenLocked,
        boolean hidden,
        boolean triggersEnabled,
        boolean limitedReward,
        LegendaryData legendary,
        boolean focusAvailable,
        String listAccent,
        JourneyAppearance journeyAppearance,
        Boolean journeyVisible,
        String subtitle,
        String objective,
        String place,
        List<Step> steps,
        boolean daily
) {
    public static final int MAX_STEPS = 32;

    /** Stable ID families reserved by the automatic advancement, item and combat generator. */
    public boolean automaticallyGenerated() {
        return id.startsWith("auto_adv_") || id.startsWith("auto_item_") || id.startsWith("auto_kill_") || id.startsWith("auto_story_");
    }

    /** Pre-step schema; keeps older authoring code and fixtures source compatible. */
    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked, boolean hideWhenLocked, boolean hidden,
                           boolean triggersEnabled, boolean limitedReward, LegendaryData legendary,
                           boolean focusAvailable, String listAccent, JourneyAppearance journeyAppearance,
                           Boolean journeyVisible) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, legendary, focusAvailable, listAccent,
                journeyAppearance, journeyVisible, "", "", "", List.of(), false);
    }
    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked, boolean hideWhenLocked, boolean hidden,
                           boolean triggersEnabled, boolean limitedReward, LegendaryData legendary,
                           boolean focusAvailable, String listAccent, JourneyAppearance journeyAppearance) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, legendary, focusAvailable, listAccent,
                journeyAppearance, Boolean.TRUE);
    }
    public QuestDefinition {
        id = id == null || id.isBlank() ? "unnamed" : id;
        title = title == null || title.isBlank() ? id : title;
        description = description == null ? "" : description;
        String legacyType = type == null || type.isBlank() ? "normal" : type;
        type = normalizeType(legacyType);
        icon = icon == null ? "" : icon;
        layout = layout == null ? LayoutNode.root() : layout;
        buttonCss = buttonCss == null ? "" : buttonCss;
        uid = uid == null ? "" : uid;
        parents = parents == null ? List.of() : List.copyOf(parents);
        triggers = triggers == null || triggers.isEmpty()
                ? legacyType.equals("guide") ? List.of(new Trigger("confirm", "", 1)) : List.of()
                : List.copyOf(triggers);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        if (rewards.size() > 128 || !bounded(triggers, 0, new int[]{256}))
            throw new IllegalArgumentException("Quest exceeds reward or condition limits");
        rewardMode = rewardMode == null || rewardMode.isBlank() ? "default" : rewardMode;
        templateId = templateId == null ? "" : templateId;
        leadingIcon = leadingIcon == null ? ListIconConfig.leading(icon) : leadingIcon;
        trailingIcon = trailingIcon == null ? ListIconConfig.trailing() : trailingIcon;
        hotEvent = hotEvent == null ? HotEventData.empty() : hotEvent;
        presentation = presentation == null ? QuestPresentation.empty() : presentation;
        completionPolicy = completionPolicy == null ? CompletionPolicy.defaults() : completionPolicy;
        legendary = legendary == null ? LegendaryData.empty() : legendary;
        listAccent = QuestListAccent.normalize(listAccent);
        journeyAppearance = journeyAppearance == null ? JourneyAppearance.empty() : journeyAppearance;
        journeyVisible = journeyVisible == null || journeyVisible;
        subtitle = subtitle == null ? "" : subtitle.strip();
        objective = objective == null ? "" : objective.strip();
        place = place == null ? "" : place.strip();
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (subtitle.length() > 256 || objective.length() > 512 || place.length() > 256)
            throw new IllegalArgumentException("Quest subtitle, objective or place is too long");
        if (steps.size() > MAX_STEPS) throw new IllegalArgumentException("Quest exceeds step limit");
        int[] stepBudget = {256};
        for (Step step : steps)
            if (!bounded(step.conditions(), 0, stepBudget))
                throw new IllegalArgumentException("Quest step exceeds condition limits");
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked, boolean hideWhenLocked, boolean hidden,
                           boolean triggersEnabled, boolean limitedReward, LegendaryData legendary,
                           boolean focusAvailable, String listAccent) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, legendary, focusAvailable, listAccent, null);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked, boolean hideWhenLocked, boolean hidden,
                           boolean triggersEnabled, boolean limitedReward, LegendaryData legendary, boolean focusAvailable) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, legendary, focusAvailable, "auto");
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked, boolean hideWhenLocked, boolean hidden,
                           boolean triggersEnabled, boolean limitedReward, LegendaryData legendary) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, legendary, false);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked, boolean hideWhenLocked, boolean hidden,
                           boolean triggersEnabled, boolean limitedReward) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, null);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked, boolean hideWhenLocked, boolean hidden,
                           boolean triggersEnabled) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, false);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation, CompletionPolicy completionPolicy,
                           boolean defaultUnlocked) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy, defaultUnlocked,
                false, false, true, false);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon,
                           HotEventData hotEvent, QuestPresentation presentation) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, presentation, null, true);
    }

    @Override public CompletionPolicy completionPolicy() {
        return completionPolicy == null ? CompletionPolicy.defaults() : completionPolicy;
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon, HotEventData hotEvent) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout, titleKey,
                descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, hotEvent, null, null, true);
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "commission";
        return switch (type) {
            case "guide" -> "main";
            case "normal", "item" -> "commission";
            default -> type;
        };
    }

    private static boolean bounded(List<Trigger> triggers, int depth, int[] budget) {
        if (depth > 16) return false;
        for (Trigger trigger : triggers) {
            if (--budget[0] < 0 || trigger.item().length() > 8192
                    || !bounded(trigger.children(), depth + 1, budget)) return false;
        }
        return true;
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, "", List.of(), List.of(), List.of(), "default", "",
                null, null, null, null, null, true);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, "",
                null, null, null, null, null, true);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                null, null, null, null, null, true);
    }

    public QuestDefinition(String id, String title, String description, String type, String icon, boolean trackable,
                           boolean recommended, int recommendationLevel, LayoutNode layout,
                           String titleKey, String descriptionKey, String buttonCss, String uid,
                           List<String> parents, List<Trigger> triggers, List<Reward> rewards, String rewardMode,
                           String templateId, ListIconConfig leadingIcon, ListIconConfig trailingIcon) {
        this(id, title, description, type, icon, trackable, recommended, recommendationLevel, layout,
                titleKey, descriptionKey, buttonCss, uid, parents, triggers, rewards, rewardMode, templateId,
                leadingIcon, trailingIcon, null, null, null, true);
    }

    public String progressKey() {
        return uid.isBlank() ? "id:" + id : "uid:" + uid;
    }

    public record Trigger(String type, String item, int count, List<Trigger> children,
                          String buttonText, String buttonShape) {
        public Trigger(String type, String item, int count) { this(type, item, count, List.of(), "", "rectangle"); }
        public Trigger(String type, String item, int count, List<Trigger> children) {
            this(type, item, count, children, "", "rectangle");
        }
        public Trigger {
            type = type == null ? "" : type;
            item = item == null ? "" : item;
            children = children == null ? List.of() : List.copyOf(children);
            buttonText = buttonText == null ? "" : buttonText;
            buttonShape = buttonShape == null || buttonShape.isBlank() ? "rectangle" : buttonShape;
            if (count < 1 || count > 1000000) {
                throw new IllegalArgumentException("Trigger count must be 1..1000000");
            }
            if (buttonText.length() > 128 || !validButtonShape(buttonShape)) {
                throw new IllegalArgumentException("Invalid condition button");
            }
        }
        private static boolean validButtonShape(String shape) {
            if (List.of("circle", "rectangle", "diamond", "triangle", "pentagon",
                    "hexagon", "none", "gear", "heart").contains(shape)) return true;
            if (!shape.startsWith("custom:") || shape.length() > 256) return false;
            String[] points = shape.substring("custom:".length()).split(";", -1);
            if (points.length < 3 || points.length > 16) return false;
            for (String point : points) {
                String[] coordinates = point.split(",", -1);
                if (coordinates.length != 2) return false;
                try {
                    int x = Integer.parseInt(coordinates[0]), y = Integer.parseInt(coordinates[1]);
                    if (x < 0 || x > 100 || y < 0 || y > 100) return false;
                } catch (NumberFormatException invalid) { return false; }
            }
            return true;
        }
    }

    public record Reward(String type, String item, int count, String quality, String background,
                         Boolean displayed, String claimMode) {
        public Reward(String type, String item, int count) {
            this(type, item, count, "", "", true, "default");
        }
        public Reward(String type, String item, int count, String quality, String background) {
            this(type, item, count, quality, background, true, "default");
        }

        public Reward {
            type = type == null ? "item" : type;
            item = item == null ? "" : item;
            quality = QuestRewardQuality.normalize(quality);
            background = QuestRewardQuality.normalizeBackground(background);
            displayed = displayed == null || displayed;
            claimMode = normalizeClaimMode(claimMode);
            if (count < 1 || count > 1000000) {
                throw new IllegalArgumentException("Reward count must be 1..1000000");
            }
        }

        public static String normalizeClaimMode(String value) {
            String normalized = value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "manual", "automatic" -> normalized;
                default -> "default";
            };
        }

        public Reward display() {
            return new Reward(type, List.of("command", "script", "function").contains(type) ? "" : item,
                    count, quality, background, displayed, claimMode);
        }
    }

    public QuestDefinition display() {
        return new QuestDefinition(id, title, description, type, icon, trackable, recommended, recommendationLevel,
                layout, titleKey, descriptionKey, buttonCss, uid, parents,
                triggers.stream().map(QuestDefinition::displayTrigger).toList(),
                rewards.stream().map(Reward::display).toList(), rewardMode, templateId, leadingIcon, trailingIcon,
                hotEvent, presentation.display(), completionPolicy(), defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, legendary, focusAvailable, listAccent,
                journeyAppearance, journeyVisible, subtitle, objective, place,
                steps.stream().map(Step::display).toList(), daily);
    }

    /**
     * Runtime view of one step: the step's conditions replace the task's and counters use a
     * step-scoped identity, while title, rewards and every other field stay those of the task.
     */
    public QuestDefinition stepView(List<Trigger> stepTriggers, String scopedUid) {
        return new QuestDefinition(id, title, description, type, icon, trackable, recommended, recommendationLevel,
                layout, titleKey, descriptionKey, buttonCss, scopedUid, parents, stepTriggers, rewards, rewardMode,
                templateId, leadingIcon, trailingIcon, hotEvent, presentation, completionPolicy(), defaultUnlocked,
                hideWhenLocked, hidden, triggersEnabled, limitedReward, legendary, focusAvailable, listAccent,
                journeyAppearance, journeyVisible, subtitle, objective, place, List.of(), daily);
    }

    /** One objective of a multi-step task. Conditions use the same grammar as task conditions. */
    public record Step(String id, String title, String description, List<Trigger> conditions) {
        public Step {
            id = id == null ? "" : id.strip();
            title = title == null ? "" : title.strip();
            description = description == null ? "" : description;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            if (id.length() > 64 || title.length() > 512 || description.length() > 8192 || conditions.size() > 64)
                throw new IllegalArgumentException("Quest step exceeds limits");
        }

        public Step display() {
            return new Step(id, title, description,
                    conditions.stream().map(QuestDefinition::displayTrigger).toList());
        }
    }

    private static Trigger displayTrigger(Trigger trigger) {
        return new Trigger(trigger.type(), trigger.type().equals("script") ? "" : trigger.item(),
                trigger.count(), trigger.children().stream().map(QuestDefinition::displayTrigger).toList());
    }

    public static QuestDefinition fallback(String id, String title, String description) {
        return new QuestDefinition(id, title, description, "commission", "", true, false, 0,
                LayoutNode.root(), null, null, "");
    }

    public record LayoutNode(
            String id,
            String kind,
            String layout,
            int x,
            int y,
            int width,
            int height,
            List<LayoutNode> children
    ) {
        public LayoutNode {
            id = id == null || id.isBlank() ? "root" : id;
            kind = kind == null || kind.isBlank() ? "container" : kind;
            layout = layout == null || layout.isBlank() ? "relative" : layout;
            children = children == null ? List.of() : List.copyOf(children);
        }

        public static LayoutNode root() {
            return new LayoutNode("root", "container", "relative", 0, 0, 0, 0, List.of());
        }
    }
}
