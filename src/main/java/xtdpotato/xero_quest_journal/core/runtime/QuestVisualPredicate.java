package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.core.io.QuestVariableStore;

/** Bounded, data-only predicate used by visual JS nodes. */
public final class QuestVisualPredicate {
    private QuestVisualPredicate() {}

    public static boolean evaluate(ServerPlayer player, TriggerGraph.Node node, String event, String target) {
        return evaluate(player, node, event, target, "");
    }
    public static boolean evaluate(ServerPlayer player, TriggerGraph.Node node, String event, String target,
                                   String taskId) {
        return evaluate(player, node, event, target, taskId, "");
    }
    public static boolean evaluate(ServerPlayer player, TriggerGraph.Node node, String event, String target,
                                   String taskId, String controlId) {
        for (String suffix : new String[]{"", "2"}) {
            String field = node.get("field" + suffix, "");
            String expectedVar = node.get("expectedVar" + suffix, "");
            if (field.startsWith("var:") && !allowed(field.substring(4), taskId)
                    || !expectedVar.isBlank() && !allowed(expectedVar, taskId)) return false;
        }
        boolean first = clause(player, node, "", event, target, taskId, controlId);
        return switch (node.get("logic", "single")) {
            case "not" -> !first;
            case "and" -> first && clause(player, node, "2", event, target, taskId, controlId);
            case "or" -> first || clause(player, node, "2", event, target, taskId, controlId);
            default -> first;
        };
    }
    private static boolean clause(ServerPlayer player, TriggerGraph.Node node, String suffix, String event,
                                  String target, String taskId, String controlId) {
        String field = node.get("field" + suffix, suffix.isEmpty() ? "eventType" : "eventTarget");
        String expectedVar = node.get("expectedVar" + suffix, "");
        if (field.startsWith("var:") && !allowed(field.substring(4), taskId)
                || !expectedVar.isBlank() && !allowed(expectedVar, taskId)) return false;
        String actual = field(player, field, event, target, taskId, controlId);
        String otherField = node.get("expectedField" + suffix, "");
        String expected = !expectedVar.isBlank() ? QuestControlData.get(player).variable(player, expectedVar)
                : !otherField.isBlank() ? field(player, otherField, event, target, taskId, controlId)
                : node.get("expected" + suffix, "");
        return compare(actual, node.get("operator" + suffix, "eq"), expected);
    }
    public static boolean variable(ServerPlayer player, TriggerGraph.Node node, String taskId) {
        String id = node.get("variable", ""), other = node.get("expectedVar", "");
        if (!allowed(id, taskId) || !other.isBlank() && !allowed(other, taskId)) return false;
        var state = QuestControlData.get(player);
        return compare(state.variable(player, id), node.get("operator", "eq"),
                other.isBlank() ? node.get("expected", "") : state.variable(player, other));
    }
    public static boolean allowed(String id, String taskId) {
        var variable = QuestVariableStore.find(id);
        return variable != null && (variable.scope().equals("globe") || variable.scope().equals(taskId));
    }

    public static String field(ServerPlayer player, String name, String event, String target,
                               String taskId, String controlId) {
        String contextual = QuestGraphContext.get(name);
        if (contextual != null) return contextual;
        if (name.startsWith("var:")) return QuestControlData.get(player).variable(player, name.substring(4));
        return switch (name) {
            case "eventType" -> event;
            case "eventTarget" -> target;
            case "dimension" -> player.level().dimension().location().toString();
            case "playerName" -> player.getGameProfile().getName();
            case "playerId" -> player.getUUID().toString();
            case "experienceLevel" -> Integer.toString(player.experienceLevel);
            case "x" -> Double.toString(player.getX());
            case "y" -> Double.toString(player.getY());
            case "z" -> Double.toString(player.getZ());
            case "health" -> Float.toString(player.getHealth());
            case "maxHealth" -> Float.toString(player.getMaxHealth());
            case "foodLevel" -> Integer.toString(player.getFoodData().getFoodLevel());
            case "sneaking" -> Boolean.toString(player.isShiftKeyDown());
            case "sprinting" -> Boolean.toString(player.isSprinting());
            case "dayTime" -> Long.toString(player.level().getDayTime());
            case "gameTime" -> Long.toString(player.level().getGameTime());
            case "raining" -> Boolean.toString(player.level().getLevelData().isRaining());
            case "thundering" -> Boolean.toString(player.level().getLevelData().isThundering());
            case "difficulty" -> player.level().getDifficulty().getKey();
            case "questId" -> taskId;
            case "controlId" -> controlId;
            case "controlKind", "controlValue", "controlX", "controlY", "controlWidth", "controlHeight" -> {
                var quest = xtdpotato.xero_quest_journal.core.io.QuestRepository.find(taskId);
                var control = quest == null ? null : xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls().stream()
                        .filter(c -> c.id().equals(controlId)).findFirst().orElse(null);
                if (control == null) yield "";
                if (name.equals("controlKind")) yield control.kind();
                String property = name.substring(7);
                property = property.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + property.substring(1);
                String fallback = switch (property) {
                    case "value" -> control.value();
                    case "x" -> Integer.toString(control.x());
                    case "y" -> Integer.toString(control.y());
                    case "width" -> Integer.toString(control.width());
                    default -> Integer.toString(control.height());
                };
                yield QuestControlData.get(player).properties(player, quest, controlId).getOrDefault(property, fallback);
            }
            default -> "";
        };
    }

    private static boolean compare(String actual, String operator, String expected) {
        return switch (operator) {
            case "eq" -> actual.equals(expected);
            case "ne" -> !actual.equals(expected);
            case "contains" -> actual.contains(expected);
            case "starts" -> actual.startsWith(expected);
            case "ends" -> actual.endsWith(expected);
            case "gt", "gte", "lt", "lte" -> {
                try {
                    double a = Double.parseDouble(actual), b = Double.parseDouble(expected);
                    yield Double.isFinite(a) && Double.isFinite(b) && switch (operator) {
                        case "gt" -> a > b;
                        case "gte" -> a >= b;
                        case "lt" -> a < b;
                        default -> a <= b;
                    };
                } catch (NumberFormatException invalid) { yield false; }
            }
            default -> false;
        };
    }
}
