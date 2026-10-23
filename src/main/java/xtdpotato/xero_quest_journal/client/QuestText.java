package xtdpotato.xero_quest_journal.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.core.model.LootDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

import java.util.List;
import java.util.Locale;

public final class QuestText {
    private QuestText() {
    }

    public static Component rewardTooltip(QuestDefinition.Reward reward) {
        if (reward == null) return Component.empty();
        if (reward.type().equals("experience") || reward.type().equals("levels")) {
            return Component.translatable("screen.xero_quest_journal.reward_type." + reward.type())
                    .append(" ×" + reward.count())
                    .withStyle(net.minecraft.ChatFormatting.GOLD);
        }
        if (reward.type().equals("item")) {
            return Component.literal(localizedItemName(reward.item())
                            + (reward.count() > 1 ? " ×" + reward.count() : ""))
                    .withStyle(net.minecraft.ChatFormatting.GOLD);
        }
        return Component.translatable("screen.xero_quest_journal.reward_type." + reward.type())
                .withStyle(net.minecraft.ChatFormatting.GOLD);
    }

    public static Component title(QuestDefinition quest) {
        if (quest.titleKey() != null && xtdpotato.xero_quest_journal.core.model.QuestTitleText.generated(quest)) {
            var game = net.minecraft.client.Minecraft.getInstance();
            return xtdpotato.xero_quest_journal.core.model.QuestTitleText.title(quest,
                    game.player == null ? null : game.player.registryAccess());
        }
        return text(quest.titleKey(), quest.title());
    }

    /** Compact rows keep their full, localized content available on pointer hover. */
    public static Component tooltip(QuestDefinition quest) {
        var result = title(quest).copy().withStyle(net.minecraft.ChatFormatting.GOLD);
        var view = ClientQuestProgress.view(quest.id());
        if (view != null) {
            result.append(Component.literal("\n")
                    .append(Component.translatable("screen.xero_quest_journal.tooltip.status"))
                    .append(": ").append(ClientQuestProgress.status(quest.id()))
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
            result.append(Component.literal("\n")
                    .append(Component.translatable("screen.xero_quest_journal.tooltip.progress"))
                    .append(": " + view.current() + " / " + view.required())
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            if (view.stepCount() > 0) {
                int step = view.completed() ? view.stepCount() : Math.min(view.step() + 1, view.stepCount());
                result.append(Component.literal("\n")
                        .append(Component.translatable("screen.xero_quest_journal.tooltip.step", step, view.stepCount()))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        String objective = objective(quest).getString().strip();
        String description = description(quest).getString().strip();
        if (!objective.isBlank() && !objective.equals(title(quest).getString()))
            result.append(Component.literal("\n" + objective).withStyle(net.minecraft.ChatFormatting.WHITE));
        if (!description.isBlank() && !description.equals(objective))
            result.append(Component.literal("\n" + description).withStyle(net.minecraft.ChatFormatting.GRAY));
        List<QuestDefinition.Reward> rewards = view != null && view.completed()
                && !view.rewards().isEmpty() ? view.rewards() : quest.rewards();
        rewards = rewards.stream().filter(QuestDefinition.Reward::displayed).toList();
        if (!rewards.isEmpty()) {
            result.append(Component.literal("\n")
                    .append(Component.translatable("screen.xero_quest_journal.tooltip.rewards"))
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            int shown = Math.min(6, rewards.size());
            for (int i = 0; i < shown; i++) {
                var reward = rewards.get(i);
                String quality = reward.quality().isBlank() || reward.quality().equals("auto") ? ""
                        : " · " + Component.translatable(
                                "screen.xero_quest_journal.rewards.quality." + reward.quality()).getString();
                if (reward.type().equals("loot")) {
                    appendLootReward(result, reward);
                } else if (reward.type().equals("loot_choice")) {
                    appendChoiceReward(result, reward);
                } else {
                    boolean item = reward.type().equals("item");
                    String name = item ? localizedItemName(reward.item())
                            : Component.translatable("screen.xero_quest_journal.reward_type."
                                    + reward.type()).getString();
                    if (!item && !reward.item().isBlank()) name += ": " + reward.item();
                    String count = item && reward.count() > 1 ? " ×" + reward.count() : "";
                    result.append(Component.literal("\n- ")
                            .append(Component.literal(name).withStyle(net.minecraft.ChatFormatting.GOLD))
                            .append(Component.literal(count + quality).withStyle(net.minecraft.ChatFormatting.WHITE)));
                }
            }
            if (rewards.size() > shown)
                result.append(Component.literal("\n")
                        .append(Component.translatable("screen.xero_quest_journal.tooltip.more_rewards",
                                rewards.size() - shown))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return result;
    }

    private static void appendLootReward(MutableComponent tooltip, QuestDefinition.Reward reward) {
        LootDefinition definition = null;
        LootDefinition.Display display = null;
        String target = reward.item();
        if (target.startsWith("inline:")) {
            try {
                definition = new com.google.gson.Gson().fromJson(
                        target.substring("inline:".length()), LootDefinition.class);
            } catch (RuntimeException ignored) {
                // Invalid legacy inline data falls back to a generic loot label.
            }
        } else {
            display = ClientQuestDefinitions.lootTable(target);
        }
        List<? extends LootTooltipEntry> entries = definition != null
                ? definition.entries().stream().map(entry -> (LootTooltipEntry) new LootTooltipEntry(
                        entry.type(), entry.item(), entry.count(), entry.enabled())).toList()
                : display == null ? List.of() : display.entries().stream()
                .map(entry -> (LootTooltipEntry) new LootTooltipEntry(
                        entry.type(), entry.item(), entry.count(), entry.enabled())).toList();
        String tableName = definition != null ? definition.name()
                : display == null ? "" : display.name();
        tooltip.append(Component.literal("\n- ")
                .append(Component.literal(tableName.isBlank()
                                ? Component.translatable("screen.xero_quest_journal.reward_type.loot").getString()
                                : tableName)
                        .withStyle(net.minecraft.ChatFormatting.GOLD)));
        int shown = 0;
        for (LootTooltipEntry entry : entries) {
            if (!entry.enabled() || shown >= 6) continue;
            shown++;
            long amount = (long) entry.count() * reward.count()
                    * (definition == null ? display.rolls() : definition.rolls());
            String name = entry.type().equals("item") ? localizedItemName(entry.item())
                    : Component.translatable("screen.xero_quest_journal.loot.contents.type."
                            + entry.type()).getString();
            tooltip.append(Component.literal("\n  ")
                    .append(Component.literal(name).withStyle(net.minecraft.ChatFormatting.GOLD))
                    .append(Component.literal(amount > 1 ? " ×" + amount : "")
                            .withStyle(net.minecraft.ChatFormatting.WHITE)));
        }
        if (entries.stream().filter(LootTooltipEntry::enabled).count() > shown) {
            tooltip.append(Component.literal("\n  ")
                    .append(Component.translatable("screen.xero_quest_journal.tooltip.more_rewards",
                                    entries.stream().filter(LootTooltipEntry::enabled).count() - shown)
                            .withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    private record LootTooltipEntry(String type, String item, int count, boolean enabled) {}

    private static void appendChoiceReward(MutableComponent tooltip, QuestDefinition.Reward reward) {
        List<QuestDefinition.Reward> choices =
                xtdpotato.xero_quest_journal.core.runtime.QuestRewards.choices(reward);
        String title = xtdpotato.xero_quest_journal.core.runtime.QuestRewards.choiceTitle(reward);
        tooltip.append(Component.literal("\n- ")
                .append(Component.literal(title.isBlank()
                                ? Component.translatable("screen.xero_quest_journal.reward_type.loot_choice").getString()
                                : title)
                        .withStyle(net.minecraft.ChatFormatting.GOLD)));
        for (var choice : choices.stream().limit(6).toList()) {
            String name = choice.type().equals("item") ? localizedItemName(choice.item())
                    : Component.translatable("screen.xero_quest_journal.reward_type." + choice.type()).getString();
            tooltip.append(Component.literal("\n  ")
                    .append(Component.literal(name).withStyle(net.minecraft.ChatFormatting.GOLD))
                    .append(Component.literal(choice.count() > 1 ? " ×" + choice.count() : "")
                            .withStyle(net.minecraft.ChatFormatting.WHITE)));
        }
    }

    private static String localizedItemName(String item) {
        String name = QuestIcons.localizedName(item);
        if (!name.isBlank()) return name;
        return QuestIcons.displayName(item).replaceAll(" \\([^)]*\\)$", "");
    }

    public static Component description(QuestDefinition quest) {
        var step = currentStep(quest);
        if (step != null && !step.description().isBlank()) return Component.literal(step.description());
        return text(quest.descriptionKey(), quest.description());
    }

    public static Component type(QuestDefinition quest) {
        return ClientQuestDefinitions.types().stream().filter(type -> type.id().equals(quest.type()))
                .map(type -> (Component) Component.literal(type.name())).findFirst()
                .orElseGet(() -> Component.literal(quest.type()));
    }

    /** Zero-based current step for this player, or -1 for tasks without steps. */
    public static int stepIndex(QuestDefinition quest) {
        if (quest.steps().isEmpty()) return -1;
        var view = ClientQuestProgress.view(quest.id());
        return view == null ? 0 : Math.min(view.step(), quest.steps().size() - 1);
    }

    public static QuestDefinition.Step currentStep(QuestDefinition quest) {
        int index = stepIndex(quest);
        return index < 0 ? null : quest.steps().get(index);
    }

    /** Conditions the player is currently working on: the current step's, or the task's own. */
    public static List<QuestDefinition.Trigger> activeConditions(QuestDefinition quest) {
        var step = currentStep(quest);
        return step == null ? quest.triggers() : step.conditions();
    }

    /**
     * The objective line shown in the journal and HUD: authored step title, authored objective,
     * then a readable sentence generated from the active conditions.
     */
    public static Component objective(QuestDefinition quest) {
        var step = currentStep(quest);
        if (step != null) return stepTitle(step, title(quest));
        if (!quest.objective().isBlank()) return Component.literal(quest.objective());
        if (quest.automaticallyGenerated() && quest.triggers().size() == 1
                && quest.triggers().getFirst().type().equals("advancement"))
            return Component.translatable("screen.xero_quest_journal.objective.advancement", title(quest));
        return describe(quest.triggers(), title(quest));
    }

    public static Component stepTitle(QuestDefinition.Step step, Component fallback) {
        return step.title().isBlank() ? describe(step.conditions(), fallback) : Component.literal(step.title());
    }

    public static Component describe(List<QuestDefinition.Trigger> triggers, Component fallback) {
        var effective = triggers.stream()
                .filter(t -> !t.type().equals("unlock") && !t.type().equals("lock")).toList();
        if (effective.isEmpty()) return fallback;
        if (effective.size() > 1)
            return Component.translatable("screen.xero_quest_journal.objective.multiple", effective.size());
        return describe(effective.getFirst());
    }

    public static Component describe(QuestDefinition.Trigger trigger) {
        String templateObjective = templateObjectiveKey(trigger);
        if (templateObjective != null) return Component.translatable(templateObjective);
        String type = trigger.type().equals("item_get") ? "item_hold" : trigger.type();
        String key = "screen.xero_quest_journal.objective." + type;
        if (!Language.getInstance().has(key)) {
            return Component.translatable("screen.xero_quest_journal.condition_type." + type);
        }
        return switch (type) {
            case "confirm", "script", "region" -> Component.translatable(key);
            case "all", "count", "ratio" -> Component.translatable(key,
                    type.equals("all") ? trigger.children().size() : trigger.count());
            case "any" -> Component.translatable(key);
            default -> {
                Component sentence = Component.translatable(key, targetName(trigger));
                yield trigger.count() > 1 && countable(type)
                        ? sentence.copy().append(" ×" + trigger.count()) : sentence;
            }
        };
    }

    private static String templateObjectiveKey(QuestDefinition.Trigger trigger) {
        String templateId = trigger.item();
        if (trigger.type().equals("event") || trigger.type().startsWith("template_")) {
            if (trigger.type().startsWith("template_")) templateId = trigger.type();
            templateId = templateId.strip();
            return switch (templateId) {
                case "template_graph_only" -> "screen.xero_quest_journal.objective.template_graph_only";
                case "template_scene_only" -> "screen.xero_quest_journal.objective.template_scene_only";
                default -> null;
            };
        }
        return null;
    }

    private static boolean countable(String type) {
        return switch (type) {
            case "item_hold", "item_submit", "item_acquire", "kill", "block_break", "block_interact",
                 "event", "stage" -> true;
            default -> false;
        };
    }

    /** Localized name of a condition target (item, entity, block, place, task...). */
    public static Component targetName(QuestDefinition.Trigger trigger) {
        String target = trigger.item();
        try {
            switch (trigger.type()) {
                case "item_get", "item_hold", "item_submit", "item_acquire" -> {
                    if (target.startsWith("#")) return Component.literal(prettyId(target.substring(1)));
                    return Component.literal(QuestIcons.displayName(target).replaceAll(" \\([^)]*\\)$", ""));
                }
                case "kill" -> {
                    var id = ResourceLocation.tryParse(target);
                    if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id))
                        return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
                }
                case "block_break", "block_interact" -> {
                    var id = ResourceLocation.tryParse(target);
                    if (id != null && BuiltInRegistries.BLOCK.containsKey(id))
                        return BuiltInRegistries.BLOCK.get(id).getName();
                }
                case "dimension" -> {
                    return dimensionName(target);
                }
                case "location" -> {
                    var location = QuestLocation.parse(target);
                    if (location != null) return placeName(location);
                }
                case "advancement" -> {
                    ResourceLocation advancement = ResourceLocation.tryParse(target);
                    if (advancement != null) {
                        String namespace = advancement.getNamespace().equals("minecraft")
                                ? "" : advancement.getNamespace() + ".";
                        String key = "advancements." + namespace
                                + advancement.getPath().replace('/', '.') + ".title";
                        return Component.translatableWithFallback(key, prettyId(target));
                    }
                }
                case "quest" -> {
                    String first = target.split("\\s*,\\s*")[0];
                    var quest = ClientQuestDefinitions.find(first);
                    if (quest != null) return title(quest);
                }
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
            // Malformed author data falls back to the raw target text.
        }
        return Component.literal(target);
    }

    /** "Place" line: authored place, then the active location target, else empty. */
    public static Component place(QuestDefinition quest) {
        if (!quest.place().isBlank()) return Component.literal(quest.place());
        var location = location(quest);
        return location == null ? Component.empty() : placeName(location);
    }

    public static Component placeName(QuestLocation location) {
        Component dimension = location.dimension().isBlank() ? null : dimensionName(location.dimension());
        Component target;
        if (location.kind().equals("coords")) {
            target = Component.literal(Math.round(location.x()) + ", " + Math.round(location.y()) + ", "
                    + Math.round(location.z()));
        } else if (location.kind().equals("biome")) {
            String id = location.target().startsWith("#") ? location.target().substring(1) : location.target();
            String key = "biome." + id.replace(':', '.');
            target = Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(prettyId(id));
        } else {
            String id = location.target().startsWith("#") ? location.target().substring(1) : location.target();
            String key = "structure." + id.replace(':', '.');
            target = Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(prettyId(id));
        }
        return dimension == null ? target : dimension.copy().append("  ").append(target);
    }

    public static Component dimensionName(String id) {
        String key = "dimension." + id.replace(':', '.');
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(prettyId(id));
    }

    /** First location target among the active conditions. */
    public static QuestLocation location(QuestDefinition quest) {
        for (var trigger : activeConditions(quest)) {
            QuestLocation value = location(trigger);
            if (value != null) return value;
        }
        return null;
    }

    private static QuestLocation location(QuestDefinition.Trigger trigger) {
        if (trigger.type().equals("location")) {
            QuestLocation value = QuestLocation.parse(trigger.item());
            if (value != null) return value;
        }
        for (var child : trigger.children()) {
            QuestLocation value = location(child);
            if (value != null) return value;
        }
        return null;
    }

    /** "minecraft:village_plains" -> "Village Plains". */
    static String prettyId(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        path = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        StringBuilder result = new StringBuilder();
        for (String word : path.split("[_\\-]+")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.isEmpty() ? id : result.toString();
    }

    private static Component text(String key, String fallback) {
        if (key != null && !key.isBlank() && Language.getInstance().has(key)) {
            Component translated = Component.translatable(key);
            if (!translated.getString().matches("(?s).*%(?:\\d+\\$)?[sd].*")) return translated;
        }
        if (fallback == null || fallback.isBlank()) return Component.empty();
        if ((key == null || key.isBlank() || fallback.equals(key)) && translationKeyLike(fallback)) {
            if (descriptionKey(key, fallback)) return Component.empty();
            return Component.literal(prettyTranslationKey(fallback));
        }
        return Component.literal(fallback);
    }

    private static boolean translationKeyLike(String value) {
        return value.matches("[a-z0-9_-]+(?:[.:/][a-z0-9_.:/-]+)+");
    }

    private static boolean descriptionKey(String key, String fallback) {
        String value = key == null || key.isBlank() ? fallback : key;
        return value.endsWith(".desc") || value.endsWith(".description")
                || value.endsWith("_desc") || value.endsWith("_description");
    }

    private static String prettyTranslationKey(String key) {
        String[] words = key.split("[.:/_-]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank() || List.of("advancement", "advancements", "title", "desc", "description")
                    .contains(word.toLowerCase(Locale.ROOT))) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(word.substring(1));
        }
        return result.isEmpty() ? prettyId(key) : result.toString();
    }
}
