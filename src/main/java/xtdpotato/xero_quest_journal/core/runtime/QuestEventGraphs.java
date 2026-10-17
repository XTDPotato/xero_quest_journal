package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestControlNetwork;
import xtdpotato.xero_quest_journal.core.network.QuestUiThemeNetwork;
import java.util.*;

public final class QuestEventGraphs {
    private record Binding(QuestDefinition quest, QuestPresentation.Control control, TriggerGraph.Node start) {}
    private record Delayed(MinecraftServer server, UUID playerId, Binding binding, String event, String target,
                           Map<String, String> context, List<String> next, long dueTick) {}
    private static long revision = -1;
    private static Map<String, List<Binding>> index = Map.of();
    private static final Map<ServerPlayer, Map<String, Long>> cooldowns = new WeakHashMap<>();
    private static final List<Delayed> delayed = new ArrayList<>();
    private static final int MAX_DELAYED = 4096;
    private static boolean executing;
    private QuestEventGraphs() {}
    private static void refresh() {
        if (revision == QuestRepository.revision()) return;
        Map<String, List<Binding>> next = new HashMap<>();
        for (var quest : QuestRepository.all()) for (var control : xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls())
            for (var node : control.graph().nodes()) if (node.type().equals("event"))
                next.computeIfAbsent(node.get("event", "click"), ignored -> new ArrayList<>()).add(new Binding(quest, control, node));
        index = next;
        revision = QuestRepository.revision();
        cooldowns.clear();
        delayed.clear();
    }
    public static void fire(ServerPlayer player, String event, String target) {
        fire(player, event, target, "", "");
    }
    public static void click(ServerPlayer player, String quest, String control) {
        var definition = QuestRepository.find(quest);
        if (definition == null || !QuestRuntime.unlocked(QuestProgressData.get(player.server), player, definition)
                || !QuestRuntime.triggersEnabled(QuestProgressData.get(player.server), player, definition)
                || !QuestRuntime.allowsCompletedEvents(player, definition))
            return;
        var target = xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(definition).controls().stream()
                .filter(value -> value.id().equals(control)).findFirst().orElse(null);
        if (target == null || !visible(player, definition, target)) return;
        fire(player, "click", "", quest, control);
    }
    public static boolean listens(String event) {
        refresh();
        return !index.getOrDefault(event, List.of()).isEmpty();
    }
    private static void fire(ServerPlayer player, String event, String target, String questId, String controlId) {
        fire(player, event, target, questId, controlId, Map.of());
    }
    public static void control(ServerPlayer player, String questId, String controlId, String event, Map<String, String> values) {
        if (!TriggerGraph.CONTROL_EVENTS.contains(event)) return;
        fire(player, event, "", questId, controlId, values);
    }
    public static void reset(ServerPlayer player, QuestDefinition quest) {
        delayed.removeIf(job -> job.server() == player.server && job.playerId().equals(player.getUUID())
                && job.binding().quest().id().equals(quest.id()));
        Map<String, Long> times = cooldowns.get(player);
        if (times == null) return;
        String prefix = quest.progressKey() + "/";
        times.keySet().removeIf(key -> key.startsWith(prefix));
        if (times.isEmpty()) cooldowns.remove(player);
    }
    private static void fire(ServerPlayer player, String event, String target, String questId, String controlId,
                             Map<String, String> values) {
        if (!player.server.isSameThread() || executing) return;
        QuestConditions.record(player, "event", event, 1);
        if (!target.isBlank()) QuestConditions.record(player, "event", event + ":" + target, 1);
        refresh();
        String focus = QuestFocus.current(player);
        executing = true;
        boolean changed = false;
        try {
            for (Binding binding : index.getOrDefault(event, List.of())) {
                if (!focus.isBlank() && !focus.equals(binding.quest.id())) continue;
                if (!QuestRuntime.allowsCompletedEvents(player, binding.quest)) continue;
                if (!QuestStructures.allowsProgress(player, binding.quest)) continue;
                if (TriggerGraph.CONTROL_EVENTS.contains(event)
                        && (!binding.quest.id().equals(questId) || !binding.control.id().equals(controlId))) continue;
                if (!QuestRuntime.unlocked(QuestProgressData.get(player.server), player, binding.quest)) continue;
                if (!QuestRuntime.triggersEnabled(QuestProgressData.get(player.server), player, binding.quest)) continue;
                if (!binding.start.get("target", "").isBlank() && !binding.start.get("target", "").equals(target)) continue;
                if (TriggerGraph.CONTROL_EVENTS.contains(event) && (!visible(player, binding.quest, binding.control)
                        || !QuestPointerEvents.matches(binding.start, values))) continue;
                String key = binding.quest.progressKey() + "/" + binding.control.id() + "/" + binding.start.id();
                var data = QuestControlData.get(player);
                if (Boolean.parseBoolean(binding.start.get("once", "false")) && data.fired(player, binding.quest, key)) continue;
                var times = cooldowns.computeIfAbsent(player, ignored -> new HashMap<>());
                long now = player.server.getTickCount();
                if (now < times.getOrDefault(key, Long.MIN_VALUE)) continue;
                times.put(key, now + Integer.parseInt(binding.start.get("cooldown", "20")));
                try (var ignored = new QuestGraphContext(values)) {
                    changed |= execute(player, binding, event, target,
                            List.of(binding.start.id()), values);
                    if (Boolean.parseBoolean(binding.start.get("once", "false"))) data.markFired(player, binding.quest, key);
                } catch (Exception invalid) {
                    times.put(key, now + 200);
                    XeroQuestJournal.LOGGER.warn("Control trigger failed: quest={}, control={}, node={}",
                            binding.quest.id(), binding.control.id(), binding.start.id(), invalid);
                }
            }
        } finally { executing = false; }
        if (changed) {
            QuestControlNetwork.send(player);
            QuestControlNetwork.sendVariables(player);
        }
    }
    public static boolean visible(ServerPlayer player, QuestDefinition quest, QuestPresentation.Control control) {
        var controls = xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls();
        for (var current = control; current != null;) {
            String visibility = QuestControlData.get(player).properties(player, quest, current.id())
                    .getOrDefault("visibility", current.properties().getOrDefault("visibility", "visible"));
            if (!visibility.equals("visible")) return false;
            String parent = current.parent();
            current = controls.stream().filter(c -> c.id().equals(parent)).findFirst().orElse(null);
        }
        return true;
    }
    private static boolean execute(ServerPlayer player, Binding binding, String event, String target,
                                    Collection<String> initialNodes, Map<String, String> context) throws Exception {
        var graph = binding.control.graph();
        Map<String, TriggerGraph.Node> nodes = new HashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        var queue = new ArrayDeque<String>();
        Set<String> visited = new HashSet<>();
        queue.addAll(initialNodes);
        boolean changed = false;
        while (!queue.isEmpty()) {
            if (!QuestRuntime.allowsCompletedEvents(player, binding.quest)) break;
            var node = nodes.get(queue.remove());
            if (!visited.add(node.id())) continue;
            String port = "next";
            switch (node.type()) {
                case "locate_structure", "locate_biome", "teleport", "enter_structure" -> {
                    try {
                        port = QuestTravelActions.execute(player, binding.quest, binding.control.id(), node, event, target)
                                ? "true" : "false";
                    } catch (Exception invalidDestination) {
                        port = "false";
                        XeroQuestJournal.LOGGER.debug("Graph destination unavailable: {}", node.id(), invalidDestination);
                    }
                }
                case "mail_send", "mail_check", "mail_delete", "mail_set_property", "mail_lock_task", "mail_unlock_task" ->
                    port = QuestMailActions.execute(player, binding.quest, binding.control.id(), node) ? "true" : "false";
                case "js" -> {
                    String onceKey = binding.quest.progressKey() + "/" + binding.control.id()
                            + "/" + node.id() + "/predicate";
                    var state = QuestControlData.get(player);
                    boolean result = !(Boolean.parseBoolean(node.get("once", "false"))
                            && state.fired(player, binding.quest, onceKey))
                            && (node.get("mode", "script").equals("visual")
                            ? QuestVisualPredicate.evaluate(player, node, event, target, binding.quest.id(), binding.control.id())
                            : Boolean.TRUE.equals(QuestScripts.run(player, node.get("script", "false"), true,
                                    scriptContext(player, binding, event, target))));
                    if (result && Boolean.parseBoolean(node.get("once", "false")))
                        state.markFired(player, binding.quest, onceKey);
                    port = result ? "true" : "false";
                }
                case "variable" -> port = QuestVisualPredicate.variable(player, node, binding.quest.id())
                        ? "true" : "false";
                case "set_variable" -> {
                    String id = node.get("variable", "");
                    if (!QuestVisualPredicate.allowed(id, binding.quest.id()))
                        throw new IllegalArgumentException("Variable outside task scope");
                    var definition = xtdpotato.xero_quest_journal.core.io.QuestVariableStore.find(id);
                    if (definition == null) throw new IllegalArgumentException("Unknown variable");
                    String from = node.get("sourceVariable", "");
                    if (!from.isBlank() && !QuestVisualPredicate.allowed(from, binding.quest.id()))
                        throw new IllegalArgumentException("Source variable outside task scope");
                    String value = from.isBlank() ? node.get("sourceField", "").isBlank() ? node.get("value", "")
                            : QuestVisualPredicate.field(player, node.get("sourceField", ""), event, target,
                            binding.quest.id(), binding.control.id()) : QuestControlData.get(player).variable(player, from);
                    QuestControlData.get(player).setVariable(player, id, value);
                    changed = true;
                    if (definition.teamShared()) QuestControlNetwork.sendTeam(player);
                }
                case "calculate_variable" -> {
                    String id = node.get("variable", "");
                    if (!QuestVisualPredicate.allowed(id, binding.quest.id()))
                        throw new IllegalArgumentException("Variable outside task scope");
                    var definition = xtdpotato.xero_quest_journal.core.io.QuestVariableStore.find(id);
                    if (definition == null || !definition.numeric())
                        throw new IllegalArgumentException("Calculator target must be numeric");
                    String operation = node.get("operation", "add");
                    String operandVariable = node.get("operandVariable", "");
                    if (xtdpotato.xero_quest_journal.core.runtime.QuestVariableMath.unary(operation))
                        operandVariable = "";
                    String operand = operandVariable.isBlank() ? node.get("operand", "0")
                            : QuestControlData.get(player).variable(player, operandVariable);
                    if (!operandVariable.isBlank()) {
                        var other = xtdpotato.xero_quest_journal.core.io.QuestVariableStore.find(operandVariable);
                        if (!QuestVisualPredicate.allowed(operandVariable, binding.quest.id())
                                || other == null || !other.numeric())
                            throw new IllegalArgumentException("Calculator operand must be numeric");
                    }
                    String result = QuestVariableMath.evaluate(operation,
                            QuestControlData.get(player).variable(player, id), operand);
                    QuestControlData.get(player).setVariable(player, id, result);
                    changed = true;
                    if (definition.teamShared()) QuestControlNetwork.sendTeam(player);
                }
                case "activate" -> {
                    String id = node.get("target", "").isBlank() ? binding.quest.id() : node.get("target", "");
                    String result = QuestRuntime.activate(player, id);
                    port = List.of("completed", "already_complete").contains(result) ? "true" : "false";
                }
                case "complete", "unlock", "lock" -> {
                    String id = node.get("target", "").isBlank() ? binding.quest.id() : node.get("target", "");
                    String result = QuestRuntime.trigger(player, id, node.type(), false);
                    if (!List.of("completed", "unlocked", "locked", "already_complete").contains(result))
                        throw new IllegalArgumentException("Task action failed: " + result);
                }
                case "advance_step" -> {
                    String id = node.get("target", "").isBlank() ? binding.quest.id() : node.get("target", "");
                    String result = QuestRuntime.trigger(player, id, "advance_step", false);
                    if (!List.of("step_completed", "completed", "already_complete").contains(result))
                        throw new IllegalArgumentException("Step action failed: " + result);
                }
                case "set_task_property" -> {
                    String id = node.get("target", "").isBlank() ? binding.quest.id() : node.get("target", "");
                    if (!QuestRuntime.setTaskProperty(player, id, node.get("property", "hidden"),
                            Boolean.parseBoolean(node.get("value", "true"))))
                        throw new IllegalArgumentException("Missing target task or invalid task property");
                    changed = true;
                }
                case "discover_anecdote" -> {
                    if (!AnecdoteRuntime.discover(player, node.get("target", "")))
                        throw new IllegalArgumentException("Anecdote target is missing or already discovered");
                }
                case "complete_anecdote" -> {
                    if (!AnecdoteRuntime.completeRecord(player, node.get("entry", ""), node.get("record", "")))
                        throw new IllegalArgumentException("Anecdote record is missing or not discovered");
                }
                case "command" -> QuestCommands.execute(player, node.get("command", ""));
                case "delay" -> {
                    List<String> next = graph.edges().stream()
                            .filter(edge -> edge.from().equals(node.id()) && edge.port().equals("next"))
                            .map(TriggerGraph.Edge::to).toList();
                    enqueueDelay(player, binding, event, target, context, next,
                            Integer.parseInt(node.get("ticks", "20")));
                    continue;
                }
                case "set_context" -> setGameContext(player, node);
                case "set_property", "reset_property" -> {
                    String property = node.get("property", "visibility"), value = node.get("value", "visible");
                    String valueVariable = node.get("valueVariable", "");
                    if (node.type().equals("set_property") && !valueVariable.isBlank()) {
                        if (!QuestVisualPredicate.allowed(valueVariable, binding.quest.id()))
                            throw new IllegalArgumentException("Property variable outside task scope");
                        value = QuestControlData.get(player).variable(player, valueVariable);
                    }
                    if (node.type().equals("set_property")) validateProperty(property, value);
                    var targets = QuestControlTarget.of(node.data(), binding.quest.id(), binding.control.id());
                    var definitions = new ArrayList<QuestDefinition>();
                    // Validate the entire selection before applying any mutations.
                    for (var destination : targets) {
                        var quest = destination.quest().isBlank() ? binding.quest : QuestRepository.find(destination.quest());
                        var control = quest == null ? null
                                : xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls().stream()
                                .filter(c -> c.id().equals(destination.control())).findFirst().orElse(null);
                        if (control == null) throw new IllegalArgumentException("Missing target task/control");
                        if (!ControlProperties.applicable(control.kind(), property))
                            throw new IllegalArgumentException("Property not available for control");
                        definitions.add(quest);
                    }
                    for (int i = 0; i < targets.size(); i++) {
                        if (node.type().equals("reset_property"))
                            QuestControlData.get(player).reset(player, definitions.get(i), targets.get(i).control(),
                                    property, Boolean.parseBoolean(node.get("all", "false")));
                        else QuestControlData.get(player).set(player, definitions.get(i), targets.get(i).control(),
                                property, value);
                    }
                    changed = true;
                }
                case "set_overview_background" -> {
                    var state = QuestControlData.get(player);
                    String key = binding.quest.progressKey() + "/" + binding.control.id()
                            + "/" + node.id() + "/action";
                    boolean once = Boolean.parseBoolean(node.get("once", "true"));
                    if (once && state.fired(player, binding.quest, key)) break;
                    String source = node.get("source", "");
                    if (node.get("scope", "player").equals("server")) {
                        var current = xtdpotato.xero_quest_journal.core.QuestConfig.defaults().uiTheme;
                        if (!QuestUiThemeNetwork.apply(player, current.withOverviewImage(source)))
                            throw new IllegalArgumentException("Unable to save overview background");
                    } else {
                        state.overviewImage(player, source);
                        QuestUiThemeNetwork.send(player);
                    }
                    if (once) state.markFired(player, binding.quest, key);
                }
                case "set_hot_event_background" -> {
                    var state = QuestControlData.get(player);
                    String key = binding.quest.progressKey() + "/" + binding.control.id()
                            + "/" + node.id() + "/hot-event-background";
                    boolean once = Boolean.parseBoolean(node.get("once", "true"));
                    if (once && state.fired(player, binding.quest, key)) break;
                    String source = node.get("source", "").strip();
                    String type = node.get("type", "image");
                    String mode = node.get("mode", "cover");
                    double scale = Double.parseDouble(node.get("scale", "1"));
                    int offsetX = Integer.parseInt(node.get("offsetX", "0"));
                    int offsetY = Integer.parseInt(node.get("offsetY", "0"));
                    String music = node.get("music", "").strip();
                    if (!xtdpotato.xero_quest_journal.core.HotEventStore.applyBackground(
                            player, source, type, mode, scale, offsetX, offsetY, music))
                        throw new IllegalArgumentException("Unable to save hot-event background");
                    if (once) state.markFired(player, binding.quest, key);
                }
                case "set_theme" -> {
                    var state = QuestControlData.get(player);
                    String keyOnce = binding.quest.progressKey() + "/" + binding.control.id()
                            + "/" + node.id() + "/theme";
                    boolean once = Boolean.parseBoolean(node.get("once", "true"));
                    if (once && state.fired(player, binding.quest, keyOnce)) break;
                    String key = node.get("key", "accent");
                    String value = node.get("value", "");
                    QuestUiTheme current = xtdpotato.xero_quest_journal.core.QuestConfig.defaults().uiTheme;
                    if (!QuestUiThemeNetwork.apply(player, current.with(key, value))) {
                        throw new IllegalArgumentException("Unable to save UI theme");
                    }
                    if (once) state.markFired(player, binding.quest, keyOnce);
                }
                case "reward" -> {
                    QuestRewards.custom(player, binding.quest, binding.control.id() + "/" + node.id(),
                            rewards(node), Boolean.parseBoolean(node.get("once", "true")));
                }
                case "kubejs_call", "kubejs_script" -> {
                    var state = QuestControlData.get(player);
                    String key = binding.quest.progressKey() + "/" + binding.control.id() + "/" + node.id() + "/action";
                    boolean once = Boolean.parseBoolean(node.get("once", "true"));
                    if (once && state.fired(player, binding.quest, key)) break;
                    Map<String, String> scriptValues = new LinkedHashMap<>();
                    for (String field : TriggerGraph.FIELDS) scriptValues.put(field, QuestVisualPredicate.field(
                            player, field, event, target, binding.quest.id(), binding.control.id()));
                    if (node.type().equals("kubejs_call")) QuestKubeJs.call(player, node.get("function", ""), scriptValues);
                    else QuestKubeJs.run(player, node.get("script", ""), scriptValues);
                    if (once) state.markFired(player, binding.quest, key);
                }
                default -> { }
            }
            String output = port;
            graph.edges().stream().filter(edge -> edge.from().equals(node.id()) && edge.port().equals(output))
                    .forEach(edge -> queue.add(edge.to()));
        }
        return changed;
    }
    private static void enqueueDelay(ServerPlayer player, Binding binding, String event, String target,
                                    Map<String, String> context, List<String> next, int ticks) {
        if (next.isEmpty() || delayed.size() >= MAX_DELAYED) return;
        delayed.add(new Delayed(player.server, player.getUUID(), binding, event, target,
                Map.copyOf(context), next, player.server.getTickCount() + ticks));
    }
    public static void tick(MinecraftServer server) {
        if (!server.isSameThread() || executing || delayed.isEmpty()) return;
        refresh();
        long now = server.getTickCount();
        var ready = delayed.stream().filter(job -> job.server() == server && job.dueTick() <= now).toList();
        if (ready.isEmpty()) return;
        delayed.removeAll(ready);
        for (Delayed job : ready) {
            var player = server.getPlayerList().getPlayer(job.playerId());
            if (player == null) continue;
            var quest = job.binding().quest();
            String focus = QuestFocus.current(player);
            if (!focus.isBlank() && !focus.equals(quest.id())) {
                delayed.add(job);
                continue;
            }
            if (!QuestRuntime.unlocked(QuestProgressData.get(server), player, quest)
                    || !QuestRuntime.triggersEnabled(QuestProgressData.get(server), player, quest)
                    || !QuestRuntime.allowsCompletedEvents(player, quest)
                    || !QuestStructures.allowsProgress(player, quest)) continue;
            executing = true;
            try (var ignored = new QuestGraphContext(job.context())) {
                boolean changed = execute(player, job.binding(), job.event(), job.target(),
                        job.next(), job.context());
                if (changed) {
                    QuestControlNetwork.send(player);
                    QuestControlNetwork.sendVariables(player);
                }
            } catch (Exception invalid) {
                XeroQuestJournal.LOGGER.warn("Delayed control action failed: quest={}, control={}",
                        job.binding().quest().id(), job.binding().control().id(), invalid);
            } finally {
                executing = false;
            }
        }
    }
    private static void setGameContext(ServerPlayer trigger, TriggerGraph.Node node) throws Exception {
        String scope = node.get("scope", "player");
        String field = node.get("field", "experienceLevel");
        String value = node.get("value", "");
        if (scope.equals("player")) {
            var selector = net.minecraft.commands.arguments.EntityArgument.players()
                    .parse(new com.mojang.brigadier.StringReader(node.get("player", "@s")));
            for (var target : selector.findPlayers(trigger.createCommandSourceStack().withPermission(2))) {
                switch (field) {
                    case "experienceLevel" -> target.giveExperienceLevels(
                            Integer.parseInt(value) - target.experienceLevel);
                    case "health" -> target.setHealth(Math.min(Float.parseFloat(value), target.getMaxHealth()));
                    case "maxHealth" -> {
                        var attribute = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                        if (attribute == null) throw new IllegalArgumentException("Player health attribute is missing");
                        attribute.setBaseValue(Double.parseDouble(value));
                        target.setHealth(Math.min(target.getHealth(), target.getMaxHealth()));
                    }
                    case "foodLevel" -> target.getFoodData().setFoodLevel(Integer.parseInt(value));
                    case "x", "y", "z" -> target.teleportTo(target.serverLevel(),
                            field.equals("x") ? Double.parseDouble(value) : target.getX(),
                            field.equals("y") ? Double.parseDouble(value) : target.getY(),
                            field.equals("z") ? Double.parseDouble(value) : target.getZ(), target.getYRot(), target.getXRot());
                    case "sneaking" -> target.setShiftKeyDown(Boolean.parseBoolean(value));
                    case "sprinting" -> target.setSprinting(Boolean.parseBoolean(value));
                    default -> throw new IllegalArgumentException("Unsupported player variable");
                }
            }
            return;
        }
        net.minecraft.server.level.ServerLevel level = trigger.serverLevel();
        String dimension = node.get("dimension", "");
        if (!dimension.isBlank()) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(dimension);
            if (id == null) throw new IllegalArgumentException("Invalid world dimension");
            level = trigger.server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, id));
            if (level == null) throw new IllegalArgumentException("World dimension is not loaded");
        }
        switch (field) {
            case "dayTime" -> level.setDayTime(Long.parseLong(value));
            case "raining" -> {
                boolean rain = Boolean.parseBoolean(value);
                level.setWeatherParameters(rain ? 0 : 12000, rain ? 12000 : 0, rain,
                        rain && level.isThundering());
            }
            case "thundering" -> {
                boolean thunder = Boolean.parseBoolean(value);
                level.setWeatherParameters(0, 12000, thunder || level.isRaining(), thunder);
            }
            default -> throw new IllegalArgumentException("Unsupported world variable");
        }
    }
    public static void validateCommand(String command) {
        if (command == null || command.isBlank() || command.length() > 4096
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0
                || command.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid command");
    }
    public static List<QuestDefinition.Reward> rewards(TriggerGraph.Node node) {
        var values = new com.google.gson.Gson().fromJson(node.get("rewards", "[]"), QuestDefinition.Reward[].class);
        if (values == null || values.length > 128) throw new IllegalArgumentException("Invalid custom rewards");
        return List.of(values);
    }
    private static Map<String, String> scriptContext(ServerPlayer player, Binding binding, String event, String target) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : TriggerGraph.FIELDS)
            values.put(field, QuestVisualPredicate.field(player, field, event, target, binding.quest.id(), binding.control.id()));
        return values;
    }
    public static void validateProperty(String property, String value) {
        if (!TriggerGraph.PROPERTIES.contains(property) || value == null || value.length() > 4096)
            throw new IllegalArgumentException("Invalid property");
        switch (property) {
            case "visibility" -> { if (!List.of("visible", "invisible", "gone").contains(value)) throw new IllegalArgumentException("Visibility"); }
            case "x", "y", "width", "height" -> {
                int number = Integer.parseInt(value);
                if (number < (property.equals("width") || property.equals("height") ? -2 : 0)
                        || number > 100000) throw new IllegalArgumentException("Size");
            }
            case "alpha", "textSize", "volume" -> {
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number) || number < (property.equals("textSize") ? .25 : 0)
                        || number > (property.equals("textSize") ? 8 : 1)) throw new IllegalArgumentException("Style range");
            }
            case "textColor", "textColorHover", "textColorPressed", "borderColor", "shadowColor", "borderColorHover", "borderColorPressed" -> {
                if (!value.equals("transparent") && !value.equals("inherit")
                        && !value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
                    throw new IllegalArgumentException("Color");
            }
            case "background", "backgroundHover", "backgroundPressed" -> {
                if (value.isBlank() || value.contains("\n")) throw new IllegalArgumentException("Background");
            }
            case "borderEnabled", "autoplay", "loop", "muted", "showControls",
                    "borderEnabledHover", "borderEnabledPressed" -> {
                if (!List.of("true", "false", "inherit").contains(value)) throw new IllegalArgumentException("Boolean");
            }
            case "shape", "shapeHover", "shapePressed" -> {
                if (!List.of("inherit", "rectangle", "rounded", "pill", "ellipse").contains(value))
                    throw new IllegalArgumentException("Shape");
            }
            case "textStyle" -> {
                if (!List.of("normal", "bold", "italic", "bold_italic").contains(value))
                    throw new IllegalArgumentException("Text style");
            }
            case "orientation" -> {
                if (!List.of("vertical", "horizontal").contains(value)) throw new IllegalArgumentException("Orientation");
            }
            case "scaleType" -> {
                if (!List.of("fit_xy", "fit_center", "center").contains(value)) throw new IllegalArgumentException("Scale");
            }
            case "gravity", "layout_gravity" -> {
                if (!value.matches("(inherit|left|right|top|bottom|center|center_vertical|center_horizontal|[a-z_]+\\|[a-z_]+)"))
                    throw new IllegalArgumentException("Gravity");
            }
            case "entryIcon" -> {
                if (value.isBlank() || value.contains("\n")) throw new IllegalArgumentException("Icon");
            }
            case "buttonImage", "buttonImageHover", "buttonImagePressed" -> {
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0)
                    throw new IllegalArgumentException("Button image");
            }
            case "buttonImagePosition" -> {
                if (!List.of("left", "right", "top", "bottom", "only").contains(value))
                    throw new IllegalArgumentException("Button image position");
            }
            case "buttonImageWidth", "buttonImageHeight", "buttonImageGap" -> {
                int number = Integer.parseInt(value);
                int minimum = property.equals("buttonImageGap") ? 0 : 1;
                int maximum = property.equals("buttonImageGap") ? 128 : 256;
                if (number < minimum || number > maximum) throw new IllegalArgumentException("Button image size");
            }
            case "value", "displayText", "entryProgress" -> { }
            default -> {
                if (!value.isBlank() || !property.endsWith("Hover") && !property.endsWith("Pressed")) {
                    double number = Double.parseDouble(value);
                    double limit = switch (property) {
                        case "borderWidth", "borderWidthHover", "borderWidthPressed", "shadowRadius" -> 32;
                        case "maxLines" -> 256;
                        case "spacing", "cornerRadius", "cornerRadiusHover", "cornerRadiusPressed",
                                "layout_marginLeft", "layout_marginTop", "layout_marginRight", "layout_marginBottom",
                                "paddingLeft", "paddingTop", "paddingRight", "paddingBottom" -> 128;
                        default -> 100000;
                    };
                    if (!Double.isFinite(number) || Math.abs(number) > limit
                            || (!List.of("shadowDx", "shadowDy").contains(property) && number < 0))
                        throw new IllegalArgumentException("Numeric property");
                }
            }
        }
    }
}
