package xtdpotato.xero_quest_journal.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

import java.util.List;
import java.util.Locale;

public final class QuestText {
    private QuestText() {
    }

    public static Component title(QuestDefinition quest) {
        if (quest.titleKey() != null && List.of("generate.xero_quest_journal.title.collect", "generate.xero_quest_journal.title.craft",
                "generate.xero_quest_journal.title.defeat").contains(quest.titleKey())
                && !quest.triggers().isEmpty())
            return Component.translatable(quest.titleKey(), targetName(quest.triggers().getFirst()));
        return text(quest.titleKey(), quest.title());
    }

    /** Compact rows keep their full, localized content available on pointer hover. */
    public static Component tooltip(QuestDefinition quest) {
        var result = title(quest).copy().withStyle(net.minecraft.ChatFormatting.GOLD);
        String objective = objective(quest).getString().strip();
        String description = description(quest).getString().strip();
        if (!objective.isBlank() && !objective.equals(result.getString()))
            result.append(Component.literal("\n" + objective).withStyle(net.minecraft.ChatFormatting.WHITE));
        if (!description.isBlank() && !description.equals(objective))
            result.append(Component.literal("\n" + description).withStyle(net.minecraft.ChatFormatting.GRAY));
        return result;
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
        if (quest.id().startsWith("auto_adv_") && quest.triggers().size() == 1
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
                    return Component.literal(prettyId(target));
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
        return key == null || key.isBlank() || !Language.getInstance().has(key)
                ? Component.literal(fallback) : Component.translatable(key);
    }
}
