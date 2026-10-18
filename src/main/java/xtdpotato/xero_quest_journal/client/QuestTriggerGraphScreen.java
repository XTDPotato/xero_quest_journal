package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.core.model.ControlProperties;
import xtdpotato.xero_quest_journal.core.model.QuestPresentation;
import xtdpotato.xero_quest_journal.core.model.QuestVariable;
import xtdpotato.xero_quest_journal.core.model.QuestUiTheme;
import xtdpotato.xero_quest_journal.core.model.TriggerNodeLibrary;
import java.util.*;
import java.util.function.Consumer;

public final class QuestTriggerGraphScreen extends AnimatedQuestScreen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<TriggerGraph.Node, TriggerNodeLayout.Layout> measuredNodes = new HashMap<>();
    private TriggerNodeLayout.Layout nodeLayout(TriggerGraph.Node node) {
        return measuredNodes.computeIfAbsent(node, value -> TriggerNodeLayout.measure(value, font));
    }
    private int nodeWidth(TriggerGraph.Node node) { return nodeLayout(node).width(); }
    private int nodeHeight(TriggerGraph.Node node) { return nodeLayout(node).height(); }
    private final Screen parent;
    private final TriggerGraph originalGraph;
    private final Consumer<TriggerGraph> saved;
    private final List<TriggerGraph.Node> nodes = new ArrayList<>();
    private final List<TriggerGraph.Edge> edges = new ArrayList<>();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<InspectorHint> inspectorHints = new ArrayList<>();
    private record Label(Component text, int x, int y) {}
    private record InspectorHint(Component title, Component detail, int x, int y, int width, boolean valid) {}
    private record GraphIssue(String nodeId, String property, Component detail) {}
    private record GraphDraft(List<TriggerGraph.Node> nodes, List<TriggerGraph.Edge> edges) {}
    private String selected = "", wireFrom = "", wirePort = "", message = "";
    private GraphIssue graphIssue;
    private String invalidLinkTarget = "";
    private TriggerGraph.Node validatingNode;
    private String validatingProperty = "";
    private TriggerGraph.Edge selectedEdge, contextEdge;
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Map<String, TriggerGraph.Node> dragOrigins = new HashMap<>();
    private List<TriggerGraph.Node> copiedNodes = List.of();
    private List<TriggerGraph.Edge> copiedEdges = List.of();
    private String contextNode = "";
    private int contextX, contextY;
    private int pressedContextAction = -1;
    private MultiLineEditBox source;
    private final Deque<String> sourceUndo = new ArrayDeque<>(), sourceRedo = new ArrayDeque<>();
    private String sourceDraft = "";
    private boolean restoringSource;
    private QuestSourceFormatter.Diagnostic sourceDiagnostic;
    private StyledButton sourceRepairButton, sourceCopyButton;
    private boolean sourceMode, dragging, panning;
    private boolean wiring;
    private boolean wireMoved;
    private boolean dragRemembered;
    private boolean showGrid = true;
    private boolean visualSecond;
    private boolean variablesOpen;
    private int inspectorScroll, inspectorMaxScroll;
    private double inspectorPosition, inspectorTarget;
    private long inspectorFrame;
    private final List<AbstractWidget> inspectorWidgets = new ArrayList<>();
    private EditBox nodeX, nodeY, nodeWidthField, nodeHeightField;
    private static final int RESIZE_LEFT = 1, RESIZE_RIGHT = 2, RESIZE_TOP = 4, RESIZE_BOTTOM = 8;
    private TriggerGraph.Node resizeOrigin;
    private int resizeEdges, resizeWidth, resizeHeight;
    private double resizeX, resizeY;
    private boolean resizeRemembered;
    private boolean syncingPosition, inspectorDragging, inspectorThumbDragging;
    private double inspectorDragY, inspectorDragStart, inspectorThumbOffset;
    private String inspectorNode = "";
    private double panX = 18, panY = 18, zoom = 1;
    private double grabX, grabY;
    private int serial;
    /** Rubber-band selection on the empty canvas (left drag). */
    private boolean marquee, minimapDragging;
    private double marqueeX, marqueeY, marqueeEndX, marqueeEndY;
    private final Set<String> marqueeBase = new LinkedHashSet<>();
    /** World position and dangling wire for the next node chosen from the library. */
    private double[] pendingPlacement;
    private String pendingWireFrom = "", pendingWirePort = "";
    private long lastCanvasClick;
    private double lastCanvasClickX, lastCanvasClickY;
    private long lastValueClick;
    private String lastValueNode = "", lastValueProperty = "";
    private double lastValueX, lastValueY;
    private record NodeOption(String key, String value, String suffix, boolean left) {}
    private NodeOption pressedNodeOption;
    private String pressedOptionNode = "";
    private double optionPressX, optionPressY;
    private long optionPressedAt;
    private boolean optionMoved;
    private StyledChoiceButton<String> inlineChoice;
    /** Floating node search (Ctrl+F): matches get gold brackets, the rest of the graph is dimmed. */
    private boolean searchOpen, helpOpen;
    private String searchQuery = "";
    private int searchIndex = -1;
    private EditBox searchBox;
    /** Canvas menu opened by a right click that did not turn into a pan. */
    private boolean contextCanvas, rightPanMoved;
    private double rightPressX, rightPressY, canvasMenuX, canvasMenuY;
    /** World-space alignment guides shown while dragging nodes. */
    private final Set<Integer> guideXs = new HashSet<>(), guideYs = new HashSet<>();
    /** Wire lifted off a connected input while it is being re-routed. */
    private TriggerGraph.Edge detachedEdge;
    private int detachedIndex;
    private boolean reconnecting;
    /** Recently placed nodes play a short expanding ring. */
    private final Map<String, Long> spawned = new HashMap<>();
    private static final long SPAWN_NANOS = 320_000_000L;
    private final List<String> controlIds;
    private final String currentControl;
    private final String taskId;
    private final Map<String, QuestPresentation.Control> controls;

    public QuestTriggerGraphScreen(Screen parent, TriggerGraph graph, Consumer<TriggerGraph> saved) {
        this(parent, graph, List.of(), saved);
    }
    public QuestTriggerGraphScreen(Screen parent, TriggerGraph graph, List<String> controlIds, Consumer<TriggerGraph> saved) {
        this(parent, graph, "", Collections.emptyMap(), controlIds, saved);
    }
    public QuestTriggerGraphScreen(Screen parent, TriggerGraph graph, String currentControl,
                                   Map<String, QuestPresentation.Control> controls, Consumer<TriggerGraph> saved) {
        this(parent, graph, currentControl, controls, List.copyOf(controls.keySet()), saved);
    }
    private QuestTriggerGraphScreen(Screen parent, TriggerGraph graph, String currentControl,
                                    Map<String, QuestPresentation.Control> controls, List<String> controlIds,
                                    Consumer<TriggerGraph> saved) {
        this(parent, graph, "", currentControl, controls, controlIds, saved);
    }
    public QuestTriggerGraphScreen(Screen parent, TriggerGraph graph, String taskId, String currentControl,
                                   Map<String, QuestPresentation.Control> controls, Consumer<TriggerGraph> saved) {
        this(parent, graph, taskId, currentControl, controls, List.copyOf(controls.keySet()), saved);
    }
    private QuestTriggerGraphScreen(Screen parent, TriggerGraph graph, String taskId, String currentControl,
                                    Map<String, QuestPresentation.Control> controls, List<String> controlIds,
                                    Consumer<TriggerGraph> saved) {
        super(text("title"));
        this.parent = parent;
        this.originalGraph = graph;
        this.saved = saved;
        this.controlIds = List.copyOf(controlIds);
        this.currentControl = currentControl;
        this.taskId = taskId;
        this.controls = Map.copyOf(controls);
        nodes.addAll(graph.nodes());
        edges.addAll(graph.edges());
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + (key.startsWith("hot_events.") ? "" : "graph.") + key);
    }
    private TriggerGraph graph() { return new TriggerGraph(nodes, edges); }
    private int side() { return Math.min(190, width / 2); }
    private int canvasRight() { return width - side() - 8; }
    private TriggerGraph.Node selected() { return nodes.stream().filter(n -> n.id().equals(selected)).findFirst().orElse(null); }
    private void remember() {
        undo.push(GSON.toJson(graph()));
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
    }
    private void restore(Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return;
        to.push(GSON.toJson(graph()));
        load(GSON.fromJson(from.pop(), TriggerGraph.class));
        message = "";
        init();
    }
    private void load(TriggerGraph graph) {
        pressedNodeOption = null;
        resizeOrigin = null;
        dragging = panning = wiring = marquee = minimapDragging = false;
        contextCanvas = false;
        detachedEdge = null;
        reconnecting = false;
        guideXs.clear();
        guideYs.clear();
        nodes.clear(); nodes.addAll(graph.nodes());
        edges.clear(); edges.addAll(graph.edges());
        selected = wireFrom = "";
        selectedIds.clear();
        selectedEdge = contextEdge = null;
    }
    @Override protected void init() {
        String draft = source == null ? GSON.toJson(new GraphDraft(nodes, edges)) : source.getValue();
        clearWidgets();
        inlineChoice = null;
        measuredNodes.clear();
        labels.clear();
        inspectorHints.clear();
        inspectorWidgets.clear();
        nodeX = nodeY = nodeWidthField = nodeHeightField = null;
        source = null;
        searchBox = null;
        sourceRepairButton = sourceCopyButton = null;
        int toolsY = 22;
        if (!sourceMode) {
            button(8, toolsY, 24, null, "add", this::openNodeLibrary);
            button(36, toolsY, 24, null, "undo", () -> restore(undo, redo));
            button(64, toolsY, 24, null, "redo", () -> restore(redo, undo));
            button(92, toolsY, 24, null, "fit", this::fitGraph);
            button(120, toolsY, 24, null, "variables", () -> { variablesOpen = !variablesOpen; init(); });
            button(148, toolsY, 24, null, "grid", () -> showGrid = !showGrid);
            button(176, toolsY, 24, null, "arrange", this::arrangeGraph);
            // Optional tools only when they cannot collide with the right-hand group.
            if (204 + 24 <= width - 96) button(204, toolsY, 24, null, "search", this::toggleSearch);
            if (232 + 24 <= width - 96) button(232, toolsY, 24, null, "shortcuts", () -> helpOpen = !helpOpen);
        }
        button(width - 92, toolsY, 24, null, sourceMode ? "visual" : "source", this::toggleSource);
        button(width - 64, toolsY, 24, null, "cancel", this::onClose);
        button(width - 36, toolsY, 24, null, "save", this::save);
        if (sourceMode) {
            button(8, 52, 24, null, "undo", () -> restoreSource(sourceUndo, sourceRedo));
            button(36, 52, 24, null, "redo", () -> restoreSource(sourceRedo, sourceUndo));
            button(64, 52, 24, null, "format", this::formatSource);
            sourceRepairButton = addRenderableWidget(new EditorIconButton(92, 52, text("repair"),
                    this::repairSource, styles, "graph-repair", "refresh").dark());
            sourceCopyButton = addRenderableWidget(new EditorIconButton(120, 52, text("copy_problem"),
                    this::copySourceProblem, styles, "graph-copy_problem", "choose").dark());
            sourceRepairButton.active = sourceDiagnostic != null && sourceDiagnostic.repair() != null;
            sourceCopyButton.active = sourceDiagnostic != null;
            source = new SourceBox(font, 48, 82, width - 56, Math.max(32, height - 110),
                    Component.empty(), text("source"));
            source.setCharacterLimit(1048576);
            source.setValue(draft);
            sourceDraft = draft;
            source.setValueListener(this::sourceChanged);
            addRenderableWidget(source);
            return;
        }
        initInspector();
        if (variablesOpen) {
            int x = Math.min(120, width - 144);
            button(x, 52, 136, null, "variable.insert", () -> {
                variablesOpen = false;
                openVariables(this::insertVariableNode);
            });
            button(x, 74, 136, null, "variable.add_global", () -> {
                variablesOpen = false;
                openVariableEdit("globe");
            });
            button(x, 96, 136, null, "variable.add_task", () -> {
                variablesOpen = false;
                openVariableEdit(taskId);
            });
            button(x, 118, 136, null, "variable.list", () -> {
                variablesOpen = false;
                openVariables(this::insertVariableNode);
            });
        }
        if (searchOpen) {
            int boxWidth = Math.max(60, Math.min(160, canvasRight() - 90));
            searchBox = new EditBox(font, 16, 64, boxWidth, 16, text("search"));
            searchBox.setMaxLength(64);
            searchBox.setHint(text("search_nodes"));
            searchBox.setValue(searchQuery);
            searchBox.setResponder(value -> {
                if (!value.equals(searchQuery)) searchIndex = -1;
                searchQuery = value;
            });
            addRenderableWidget(searchBox);
        }
    }
    private void button(int x, int y, int width, String glyph, String key, Runnable action) {
        if (!List.of("variable.insert", "variable.add_global", "variable.add_task", "variable.list").contains(key)) {
            String icon = switch (key) {
                case "kind", "add" -> "add";
                case "edit_script" -> "source";
                case "expand_value" -> "expand";
                case "auto_size" -> "fit";
                case "first_clause", "second_clause" -> "clause";
                case "custom_rewards", "mail.attachments" -> "gift";
                case "mail.pick_player" -> "choose";
                case "hot_events.background_music" -> "choose";
                case "hot_events.visual_background" -> "visual";
                case "variable.source" -> "variables";
                case "variable.bind" -> "\u00d7".equals(glyph) ? "unlink" : "variables";
                case "cancel" -> "close";
                case "format" -> "fit";
                case "arrange" -> "refresh";
                case "search" -> "mail_search";
                case "shortcuts" -> "mail_help";
                default -> key;
            };
            // Every tool of this editor sits on a dark plate or the dark inspector.
            addRenderableWidget(new EditorIconButton(x, y, text(key), action, styles, "graph-" + key, icon).dark());
            return;
        }
        var button = addRenderableWidget(new StyledButton(x, y, width, 20,
                glyph == null ? text(key) : Component.literal(glyph), ignored -> action.run(), styles, "graph-" + key, "toolbar-button"));
        button.setTooltip(Tooltip.create(text(key)));
    }
    private void openNodeLibrary() {
        if (sourceMode) return;
        pendingPlacement = null;
        pendingWireFrom = pendingWirePort = "";
        minecraft.setScreen(new QuestNodeLibraryScreen(this, this::addNode));
    }

    /** Library opened from the canvas: place the new node there and attach a dragged wire. */
    private void openNodeLibraryAt(double worldX, double worldY, String wireSource, String port) {
        if (sourceMode) return;
        pendingPlacement = new double[]{worldX, worldY};
        pendingWireFrom = wireSource == null ? "" : wireSource;
        pendingWirePort = port == null ? "" : port;
        minecraft.setScreen(new QuestNodeLibraryScreen(this, this::addNode));
    }
    private void addNode(TriggerNodeLibrary.Entry entry) {
        if (!QuestNodeLibraryScreen.available(entry.category())) return;
        String nodeType = entry.type();
        if (nodes.size() >= 128) { message = "invalid"; return; }
        if (nodeType.equals("variable") && variables().isEmpty()) {
            message = "variable.missing"; return;
        }
        if (nodeType.equals("set_variable") && variables().stream().noneMatch(v -> !v.readOnly()))
            nodeType = "set_context";
        if (nodeType.equals("calculate_variable") && variables().stream()
                .noneMatch(v -> v.numeric() && !v.readOnly())) {
            message = "variable.numeric_missing"; return;
        }
        remember();
        String id;
        do { id = "node_" + ++serial; } while (contains(id));
        Map<String, String> data = new LinkedHashMap<>(entry.data());
        if (nodeType.equals("mail_send")) data.put("mailId", defaultMailId(id));
        if (List.of("variable", "set_variable").contains(nodeType)) {
            boolean readOnlyAllowed = nodeType.equals("variable");
            data.put("variable", variables().stream().filter(v -> readOnlyAllowed || !v.readOnly())
                    .findFirst().orElseThrow().id());
        }
        if (nodeType.equals("calculate_variable"))
            data.put("variable", variables().stream().filter(v -> v.numeric() && !v.readOnly())
                    .findFirst().orElseThrow().id());
        if (nodeType.equals("variable")) { data.put("operator", "eq"); data.put("expected", ""); }
        if (nodeType.equals("set_variable")) data.put("value", "");
        if (nodeType.equals("set_context")) {
            data.put("scope", "player"); data.put("field", "experienceLevel");
            data.put("player", "@s"); data.put("value", "0");
        }
        double placeX = pendingPlacement != null ? pendingPlacement[0] : (canvasRight() / 2.0 - panX) / zoom;
        double placeY = pendingPlacement != null ? pendingPlacement[1] : (height / 2.0 - 56 - panY) / zoom;
        pendingPlacement = null;
        nodes.add(new TriggerGraph.Node(id, nodeType, (int) Math.clamp(placeX, -100000, 100000),
                (int) Math.clamp(placeY, -100000, 100000), data));
        spawned.put(id, System.nanoTime());
        if (!pendingWireFrom.isBlank() && contains(pendingWireFrom)) {
            var edge = new TriggerGraph.Edge(pendingWireFrom, id, pendingWirePort.isBlank() ? "next" : pendingWirePort);
            try {
                var next = new ArrayList<>(edges);
                next.add(edge);
                new TriggerGraph(nodes, next);
                edges.add(edge);
            } catch (IllegalArgumentException incompatible) {
                // The chosen node cannot receive this wire (e.g. an event); keep it unconnected.
            }
        }
        pendingWireFrom = pendingWirePort = "";
        selected = id;
        selectedIds.clear();
        selectedIds.add(id);
        message = "";
        init();
        if (nodeType.equals("kubejs_script"))
            minecraft.setScreen(new QuestScriptEditorScreen(this, nodeTitle(nodes.getLast()),
                    data.getOrDefault("script", ""), value -> update("script", value),
                    xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind.KUBEJS));
    }
    static Component nodeTitle(TriggerGraph.Node node) {
        if (node.type().equals("event") && !node.get("category", "").isBlank())
            return text("category." + node.get("category", ""));
        if (node.type().equals("context")) return text("category." + node.get("category", "player_value"));
        if (node.type().equals("js")) return text("category." +
                (node.get("mode", "script").equals("visual") ? "condition" : "script_condition"));
        return text("node." + node.type());
    }
    private boolean contains(String id) { return nodes.stream().anyMatch(node -> node.id().equals(id)); }
    private List<QuestVariable> variables() {
        var result = new ArrayList<>(ClientQuestDefinitions.variables().stream().filter(v ->
                v.scope().equals("globe") || v.scope().equals(taskId)).toList());
        if (taskId.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}"))
            for (String name : List.of("progress_current", "progress_required", "progress_ratio"))
                result.add(new QuestVariable(taskId + "." + name, "0", 0, "number", "player"));
        return result.stream().distinct().toList();
    }
    private void openVariables(Consumer<String> chosen) {
        minecraft.setScreen(new QuestVariableListScreen(this, taskId, chosen, this::renameVariable));
    }
    private void openVariableEdit(String scope) {
        minecraft.setScreen(new QuestVariableEditScreen(this, taskId, scope, null, this::renameVariable));
    }
    private void renameVariable(String oldId, String id) {
        if (parent instanceof QuestScreen journal) journal.variablesChanged(taskId, oldId, id);
        if (oldId.isBlank() || oldId.equals(id)) return;
        remember();
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            var data = new LinkedHashMap<>(node.data());
            for (String key : List.of("field", "field2", "expectedVar", "expectedVar2",
                    "variable", "sourceVariable")) {
                if (oldId.equals(data.get(key))) data.put(key, id);
                if (("field".equals(key) || "field2".equals(key))
                        && ("var:" + oldId).equals(data.get(key))) data.put(key, "var:" + id);
            }
            if (!data.equals(node.data()))
                nodes.set(i, new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data));
        }
        init();
    }
    private void insertVariableNode(String id) {
        if (nodes.size() >= 128 || variables().stream().noneMatch(v -> v.id().equals(id))) return;
        remember();
        String nodeId;
        do { nodeId = "node_" + ++serial; } while (contains(nodeId));
        nodes.add(new TriggerGraph.Node(nodeId, "variable", (int) ((canvasRight() / 2.0 - panX) / zoom),
                (int) ((height / 2.0 - 56 - panY) / zoom),
                Map.of("variable", id, "operator", "eq", "expected", "")));
        spawned.put(nodeId, System.nanoTime());
        selected = nodeId;
        selectedIds.clear(); selectedIds.add(nodeId);
        init();
    }
    private void fitGraph() {
        if (nodes.isEmpty()) { panX = panY = 18; zoom = 1; return; }
        int left = nodes.stream().mapToInt(TriggerGraph.Node::x).min().orElse(0);
        int top = nodes.stream().mapToInt(TriggerGraph.Node::y).min().orElse(0);
        int right = nodes.stream().mapToInt(node -> node.x() + nodeWidth(node)).max().orElse(0);
        int bottom = nodes.stream().mapToInt(node -> node.y() + nodeHeight(node)).max().orElse(0);
        zoom = Math.clamp(Math.min((canvasRight() - 32.0) / (right - left),
                (height - 108.0) / (bottom - top)), .25, 1);
        panX = 12 - left * zoom; panY = 12 - top * zoom;
    }
    /** Layered auto-layout: events on the left, each step of the flow one column to the right. */
    private void arrangeGraph() {
        if (sourceMode || nodes.isEmpty()) return;
        Map<String, Integer> layer = new HashMap<>();
        nodes.forEach(node -> layer.put(node.id(), 0));
        for (int pass = 0; pass < nodes.size(); pass++) {
            boolean changed = false;
            for (var edge : edges) {
                int next = layer.getOrDefault(edge.from(), 0) + 1;
                if (next > layer.getOrDefault(edge.to(), 0)) {
                    layer.put(edge.to(), next);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        // Unwired value nodes sit next to the first predicate that could use them.
        int maxLayer = layer.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<String, Double> order = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) order.put(nodes.get(i).id(), (double) nodes.get(i).y() + i * 1e-3);
        Map<String, TriggerGraph.Node> placed = new LinkedHashMap<>();
        int x = 0;
        for (int column = 0; column <= maxLayer; column++) {
            final int current = column;
            var members = new ArrayList<>(nodes.stream().filter(node -> layer.get(node.id()) == current).toList());
            if (members.isEmpty()) continue;
            if (column > 0) {
                for (var node : members) {
                    double sum = 0;
                    int count = 0;
                    for (var edge : edges) if (edge.to().equals(node.id()) && order.containsKey(edge.from())) {
                        sum += order.get(edge.from());
                        count++;
                    }
                    if (count > 0) order.put(node.id(), sum / count);
                }
            }
            members.sort(Comparator.comparingDouble(node -> order.get(node.id())));
            int y = 0;
            int columnWidth = 0;
            for (var node : members) {
                placed.put(node.id(), new TriggerGraph.Node(node.id(), node.type(), x, y, node.data()));
                order.put(node.id(), (double) y);
                y += (int) Math.ceil((nodeHeight(node) + 24) / 24.0) * 24;
                columnWidth = Math.max(columnWidth, nodeWidth(node));
            }
            x += (int) Math.ceil((columnWidth + 72) / 24.0) * 24;
        }
        remember();
        for (int i = 0; i < nodes.size(); i++) {
            var replacement = placed.get(nodes.get(i).id());
            if (replacement != null) nodes.set(i, replacement);
        }
        message = "";
        fitGraph();
        init();
    }

    private void alignSelected(String mode) {
        var chosen = nodes.stream().filter(node -> selectedIds.contains(node.id())).toList();
        if (chosen.size() < 2) return;
        remember();
        int left = chosen.stream().mapToInt(TriggerGraph.Node::x).min().orElse(0);
        int top = chosen.stream().mapToInt(TriggerGraph.Node::y).min().orElse(0);
        var sorted = new ArrayList<>(chosen);
        java.util.function.ToIntFunction<TriggerGraph.Node> axis = mode.equals("distribute_v")
                ? TriggerGraph.Node::y : TriggerGraph.Node::x;
        sorted.sort(Comparator.comparingInt(axis));
        int first = mode.equals("distribute_v") ? sorted.getFirst().y() : sorted.getFirst().x();
        int last = mode.equals("distribute_v") ? sorted.getLast().y() : sorted.getLast().x();
        for (int i = 0; i < sorted.size(); i++) {
            var node = sorted.get(i);
            int position = first + (int) Math.round((last - first) * (double) i / (sorted.size() - 1));
            var moved = switch (mode) {
                case "align_left" -> new TriggerGraph.Node(node.id(), node.type(), left, node.y(), node.data());
                case "align_top" -> new TriggerGraph.Node(node.id(), node.type(), node.x(), top, node.data());
                case "distribute_h" -> new TriggerGraph.Node(node.id(), node.type(), position, node.y(), node.data());
                default -> new TriggerGraph.Node(node.id(), node.type(), node.x(), position, node.data());
            };
            nodes.set(nodes.indexOf(node), moved);
        }
        message = "";
        init();
    }

    private void selectAll() {
        selectedIds.clear();
        nodes.forEach(node -> selectedIds.add(node.id()));
        selected = selectedIds.isEmpty() ? "" : selectedIds.iterator().next();
        selectedEdge = null;
        init();
    }

    private void resetZoom() {
        double centerX = (8 + canvasRight()) / 2.0, centerY = (56 + height - 28) / 2.0;
        double worldX = (centerX - 8 - panX) / zoom, worldY = (centerY - 56 - panY) / zoom;
        zoom = 1;
        panX = centerX - 8 - worldX;
        panY = centerY - 56 - worldY;
    }

    /** Centres (and if needed zooms out to) the given nodes inside the canvas. */
    private void focusNodes(List<TriggerGraph.Node> chosen) {
        if (chosen.isEmpty()) return;
        int left = chosen.stream().mapToInt(TriggerGraph.Node::x).min().orElse(0);
        int top = chosen.stream().mapToInt(TriggerGraph.Node::y).min().orElse(0);
        int right = chosen.stream().mapToInt(node -> node.x() + nodeWidth(node)).max().orElse(0);
        int bottom = chosen.stream().mapToInt(node -> node.y() + nodeHeight(node)).max().orElse(0);
        double viewWidth = canvasRight() - 8.0, viewHeight = height - 84.0;
        zoom = Math.clamp(Math.min((viewWidth - 48) / Math.max(1, right - left),
                (viewHeight - 48) / Math.max(1, bottom - top)), .4, 1);
        panX = viewWidth / 2 - (left + right) / 2.0 * zoom;
        panY = viewHeight / 2 - (top + bottom) / 2.0 * zoom;
    }

    private void focusSelection() {
        focusNodes(nodes.stream().filter(node -> selectedIds.contains(node.id())).toList());
    }

    private void toggleSearch() {
        if (searchOpen) closeSearch();
        else {
            searchOpen = true;
            helpOpen = false;
            init();
            if (searchBox != null) setFocused(searchBox);
        }
    }

    private void closeSearch() {
        searchOpen = false;
        searchQuery = "";
        searchIndex = -1;
        init();
    }

    /** Text a node can be found by: id, type, title, body lines and slot values. */
    private String searchText(TriggerGraph.Node node) {
        var text = new StringBuilder(node.id()).append('\n').append(node.type()).append('\n')
                .append(nodeTitle(node).getString());
        var layout = nodeLayout(node);
        for (var line : layout.texts()) text.append('\n').append(line.value());
        for (var slot : layout.slots()) text.append('\n').append(slot.value());
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private List<TriggerGraph.Node> searchMatches() {
        String query = searchQuery.strip().toLowerCase(Locale.ROOT);
        if (!searchOpen || query.isEmpty()) return List.of();
        return nodes.stream().filter(node -> searchText(node).contains(query)).toList();
    }

    /** Steps through the matches, selecting and centring each one. */
    private void nextSearchMatch(int direction) {
        var matches = searchMatches();
        if (matches.isEmpty()) return;
        searchIndex = Math.floorMod(searchIndex + direction, matches.size());
        var node = matches.get(searchIndex);
        selected = node.id();
        selectedIds.clear();
        selectedIds.add(node.id());
        selectedEdge = null;
        focusNodes(List.of(node));
        init();
        if (searchBox != null) setFocused(searchBox);
    }

    /** Guides where a dragged node's left/right edge or top row lines up with another node. */
    private void updateGuides() {
        guideXs.clear();
        guideYs.clear();
        var moving = nodes.stream().filter(node -> selectedIds.contains(node.id())).toList();
        if (moving.isEmpty() || moving.size() > 8) return;
        for (var node : moving) {
            int right = node.x() + nodeWidth(node);
            for (var other : nodes) {
                if (selectedIds.contains(other.id())) continue;
                if (other.x() == node.x()) guideXs.add(node.x());
                if (other.x() + nodeWidth(other) == right) guideXs.add(right);
                if (other.y() == node.y()) guideYs.add(node.y());
            }
        }
    }

    private boolean contextOpen() {
        return !contextNode.isBlank() || contextEdge != null || contextCanvas;
    }

    private void closeContext() {
        contextNode = "";
        contextEdge = null;
        contextCanvas = false;
    }

    private void openCanvasMenu(double x, double y) {
        closeContext();
        contextCanvas = true;
        canvasMenuX = (x - 8 - panX) / zoom;
        canvasMenuY = (y - 56 - panY) / zoom;
        contextX = Math.max(8, Math.min((int) x, canvasRight() - contextWidth() - 2));
        contextY = Math.max(56, Math.min((int) y, height - contextHeight() - 30));
    }

    private String[] helpLines() {
        return text("shortcuts.list").getString().split("\n");
    }

    /** Shortcut panel in the canvas' upper-right corner: {x, y, width, height}. */
    private int[] helpBounds() {
        String[] lines = helpLines();
        int widest = font.width(text("shortcuts"));
        for (String line : lines) widest = Math.max(widest, font.width(line));
        int w = Math.max(60, Math.min(widest + 20, canvasRight() - 24)), h = 26 + lines.length * 11;
        return new int[]{canvasRight() - w - 10, 64, w, h};
    }

    private void renderShortcutHelp(GuiGraphics g) {
        String[] lines = helpLines();
        int[] bounds = helpBounds();
        int x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x50000000);
        ControlAppearance.draw(g, x, y, w, h, "rounded", 6, TriggerNodeStyle.PANEL_RAISED,
                TriggerNodeStyle.PANEL_BORDER, 1);
        MailDecor.diamond(g, x + 10, y + 11, 2, TriggerNodeStyle.GOLD);
        g.drawString(font, text("shortcuts"), x + 16, y + 7, TriggerNodeStyle.GOLD, false);
        g.fill(x + 8, y + 19, x + w - 8, y + 20, 0x30FFFFFF);
        for (int i = 0; i < lines.length; i++)
            g.drawString(font, font.plainSubstrByWidth(lines[i], w - 16), x + 8, y + 24 + i * 11, 0xFFD4D7DE, false);
    }

    private TriggerGraph.Node nodeAt(double x, double y) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            var node = nodes.get(i);
            if (x >= sx(node) && x < sx(node) + nodeWidth(node) * zoom
                    && y >= sy(node) && y < sy(node) + nodeHeight(node) * zoom) return node;
        }
        return null;
    }

    private void updateMarqueeSelection() {
        double left = Math.min(marqueeX, marqueeEndX), right = Math.max(marqueeX, marqueeEndX);
        double top = Math.min(marqueeY, marqueeEndY), bottom = Math.max(marqueeY, marqueeEndY);
        if (right - left < 3 && bottom - top < 3) return;
        selectedIds.clear();
        selectedIds.addAll(marqueeBase);
        for (var node : nodes) {
            double nx = sx(node), ny = sy(node);
            if (nx < right && nx + nodeWidth(node) * zoom > left && ny < bottom && ny + nodeHeight(node) * zoom > top)
                selectedIds.add(node.id());
        }
        selected = selectedIds.isEmpty() ? "" : selectedIds.iterator().next();
    }

    /** Minimap rectangle in the canvas' lower-right corner, or null when there is no room. */
    private int[] minimapBounds() {
        int w = Math.min(150, (canvasRight() - 8) / 4), h = Math.min(96, (height - 84) / 4);
        if (nodes.size() < 2 || w < 60 || h < 36) return null;
        int right = canvasRight() - 6, bottom = height - 34;
        return new int[]{right - w, bottom - h, right, bottom};
    }

    /** World bounds shown by the minimap: every node plus the visible viewport. */
    private double[] minimapWorld() {
        double left = (0 - panX) / zoom, top = (0 - panY) / zoom;
        double right = (canvasRight() - 8 - panX) / zoom, bottom = (height - 84 - panY) / zoom;
        for (var node : nodes) {
            left = Math.min(left, node.x());
            top = Math.min(top, node.y());
            right = Math.max(right, node.x() + nodeWidth(node));
            bottom = Math.max(bottom, node.y() + nodeHeight(node));
        }
        return new double[]{left - 24, top - 24, right + 24, bottom + 24};
    }

    private void renderMinimap(GuiGraphics g) {
        int[] box = minimapBounds();
        if (box == null) return;
        double[] world = minimapWorld();
        double scale = Math.min((box[2] - box[0] - 6) / (world[2] - world[0]), (box[3] - box[1] - 6) / (world[3] - world[1]));
        ControlAppearance.draw(g, box[0], box[1], box[2] - box[0], box[3] - box[1], "rounded", 3,
                0xD0121720, 0xFF3B4555, 1);
        int originX = box[0] + 3, originY = box[1] + 3;
        for (var node : nodes) {
            int x = originX + (int) Math.round((node.x() - world[0]) * scale);
            int y = originY + (int) Math.round((node.y() - world[1]) * scale);
            int w = Math.max(2, (int) Math.round(nodeWidth(node) * scale));
            int h = Math.max(2, (int) Math.round(nodeHeight(node) * scale));
            g.fill(x, y, x + w, y + h, selectedIds.contains(node.id()) ? TriggerNodeStyle.GOLD
                    : TriggerNodeStyle.kind(node).top);
        }
        int vx = originX + (int) Math.round(((0 - panX) / zoom - world[0]) * scale);
        int vy = originY + (int) Math.round(((0 - panY) / zoom - world[1]) * scale);
        int vw = (int) Math.round((canvasRight() - 8) / zoom * scale);
        int vh = (int) Math.round((height - 84) / zoom * scale);
        g.enableScissor(box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1);
        g.renderOutline(vx, vy, Math.max(2, vw), Math.max(2, vh), 0xC0F2D29A);
        g.disableScissor();
    }

    private void jumpMinimap(double mouseX, double mouseY) {
        int[] box = minimapBounds();
        if (box == null) return;
        double[] world = minimapWorld();
        double scale = Math.min((box[2] - box[0] - 6) / (world[2] - world[0]), (box[3] - box[1] - 6) / (world[3] - world[1]));
        double worldX = world[0] + (mouseX - box[0] - 3) / scale;
        double worldY = world[1] + (mouseY - box[1] - 3) / scale;
        panX = (canvasRight() - 8) / 2.0 - worldX * zoom;
        panY = (height - 84) / 2.0 - worldY * zoom;
    }

    private void update(String key, String value) {
        var node = selected();
        if (node == null || Objects.equals(node.data().get(key), value)) return;
        var data = new LinkedHashMap<>(node.data());
        data.put(key, value);
        replaceData(node, key, data);
    }
    private void replaceData(TriggerGraph.Node node, String property, Map<String, String> data) {
        try {
            var replacement = new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
            remember();
            nodes.set(nodes.indexOf(node), replacement);
            message = "";
            graphIssue = null;
            invalidLinkTarget = "";
        } catch (IllegalArgumentException invalid) {
            graphIssue = new GraphIssue(node.id(), property, Component.literal(
                    invalid.getMessage() == null ? text("invalid").getString() : invalid.getMessage()));
            message = "invalid";
        }
    }
    private void position(String axis, String value) {
        if (syncingPosition) return;
        var node = selected();
        if (node == null) return;
        try {
            int coordinate = Integer.parseInt(value.strip());
            if (coordinate == (axis.equals("x") ? node.x() : node.y())) return;
            var replacement = new TriggerGraph.Node(node.id(), node.type(),
                    axis.equals("x") ? coordinate : node.x(), axis.equals("y") ? coordinate : node.y(), node.data());
            remember();
            nodes.set(nodes.indexOf(node), replacement);
            message = "";
        } catch (IllegalArgumentException invalid) { message = "invalid"; }
    }
    private void dimension(String key, String value) {
        if (syncingPosition) return;
        try {
            int size = Integer.parseInt(value.strip());
            if (size < 0 || size > TriggerGraph.MAX_NODE_SIZE) throw new IllegalArgumentException();
            update(key, Integer.toString(size));
        } catch (IllegalArgumentException invalid) { message = "invalid"; }
    }
    private void resetNodeSize() {
        var node = selected();
        if (node == null || !node.data().containsKey("layoutWidth") && !node.data().containsKey("layoutHeight")) return;
        var data = new LinkedHashMap<>(node.data());
        data.remove("layoutWidth");
        data.remove("layoutHeight");
        remember();
        nodes.set(nodes.indexOf(node), new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data));
        syncPositionFields();
        message = "";
    }
    private void rename(String id) {
        var node = selected();
        if (node == null || node.id().equals(id)) return;
        if (contains(id)) { message = "duplicate:" + id; return; }
        try {
            var replacement = new TriggerGraph.Node(id, node.type(), node.x(), node.y(), node.data());
            var next = new ArrayList<>(nodes);
            next.set(nodes.indexOf(node), replacement);
            var connections = edges.stream().map(edge -> new TriggerGraph.Edge(
                    edge.from().equals(node.id()) ? id : edge.from(),
                    edge.to().equals(node.id()) ? id : edge.to(), edge.port())).toList();
            new TriggerGraph(next, connections);
            remember();
            nodes.set(nodes.indexOf(node), replacement);
            edges.clear();
            edges.addAll(connections);
            selectedIds.remove(node.id());
            selectedIds.add(id);
            selected = id;
            message = "";
        } catch (RuntimeException invalid) { message = "invalid"; }
    }
    private void field(String label, String key, String initial, int y) {
        field(label, key, initial, y, 4096);
    }
    private void field(String label, String key, String initial, int y, int maxLength) {
        int x = canvasRight() + 16, w = side() - 24;
        labels.add(new Label(text(label), x, y));
        String context = key;
        if (key.equals("target") && selected() != null && selected().type().equals("event"))
            context = selected().get("event", "click");
        if (key.equals("target") && selected() != null && selected().type().startsWith("locate_"))
            context = selected().type();
        if (key.equals("structure")) context = "structure_file";
        var input = new SuggestedEditBox(this, font, x, y + 12, w, 18, text(label), context);
        input.setMaxLength(maxLength);
        input.setValue(initial);
        input.setResponder(value -> update(key, value));
        addRenderableWidget(input);
    }
    private QuestPresentation.Control targetControl(TriggerGraph.Node node) {
        var multiple = xtdpotato.xero_quest_journal.core.model.QuestControlTarget.decode(node.get("controls", ""));
        String quest = multiple.isEmpty() ? node.get("controlQuest", taskId) : multiple.getFirst().quest();
        String id = multiple.isEmpty() ? node.get("control", "").isBlank() ? currentControl : node.get("control", "")
                : multiple.getFirst().control();
        return lookupControl(quest, id);
    }
    private QuestPresentation.Control lookupControl(String quest, String id) {
        if (quest.isBlank() || quest.equals(taskId)) {
            var local = controls.get(id);
            if (local != null) return local;
        }
        var definition = ClientQuestDefinitions.find(quest);
        if (definition == null) return null;
        var presentation = definition.presentation().authored() ? definition.presentation()
                : ClientQuestDefinitions.templates().stream().filter(t -> t.id().equals(definition.templateId()))
                .map(t -> t.presentation(definition.id())).findFirst().orElse(definition.presentation());
        return presentation.controls().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
    private List<String> propertiesFor(TriggerGraph.Node node) {
        var target = targetControl(node);
        var available = new ArrayList<>(target == null ? TriggerGraph.PROPERTIES : ControlProperties.forKind(target.kind()));
        for (var selected : xtdpotato.xero_quest_journal.core.model.QuestControlTarget.decode(node.get("controls", ""))) {
            var control = lookupControl(selected.quest(), selected.control());
            if (control != null) available.removeIf(key -> !ControlProperties.applicable(control.kind(), key));
        }
        String chosen = node.get("property", "visibility");
        if (available.contains(chosen)) return available;
        var result = new ArrayList<>(available);
        result.add(chosen);
        return result;
    }
    private static Component propertyName(String key) {
        return Component.translatable("screen.xero_quest_journal.graph.property." + key);
    }
    private String configuredValue(TriggerGraph.Node node, String key) {
        var target = targetControl(node);
        if (target == null) return ControlProperties.defaultValue(key);
        return switch (key) {
            case "value" -> target.value();
            case "x" -> Integer.toString(target.x());
            case "y" -> Integer.toString(target.y());
            case "width" -> Integer.toString(target.width());
            case "height" -> Integer.toString(target.height());
            default -> target.properties().getOrDefault(key, ControlProperties.defaultValue(key));
        };
    }
    private void changeProperty(String key) {
        var node = selected();
        if (node == null || key.equals(node.get("property", "visibility"))) return;
        var data = new LinkedHashMap<>(node.data());
        data.put("property", key);
        data.put("value", configuredValue(node, key));
        remember();
        nodes.set(nodes.indexOf(node), new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data));
        init();
    }
    private void changeControl(String id) {
        var node = selected();
        if (node == null || id.equals(node.get("control", ""))) return;
        var target = controls.get(id.isBlank() ? currentControl : id);
        String property = node.get("property", "visibility");
        if (target != null && !ControlProperties.applicable(target.kind(), property))
            property = "visibility";
        var data = new LinkedHashMap<>(node.data());
        data.put("control", id);
        data.remove("controls");
        data.remove("controlQuest");
        data.put("property", property);
        var candidate = new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
        data.put("value", configuredValue(candidate, property));
        remember();
        nodes.set(nodes.indexOf(node), new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data));
        init();
    }
    private void pickControls() {
        var node = selected();
        if (node == null) return;
        var initial = xtdpotato.xero_quest_journal.core.model.QuestControlTarget.decode(node.get("controls", ""));
        if (initial.isEmpty() && !currentControl.isBlank() && !taskId.isBlank())
            initial = xtdpotato.xero_quest_journal.core.model.QuestControlTarget.of(node.data(), taskId, currentControl);
        minecraft.setScreen(new QuestControlPickerScreen(this, taskId, initial, targets -> {
            if (!node.id().equals(selected) || targets.isEmpty()) return;
            var data = new LinkedHashMap<>(node.data());
            data.put("controls", xtdpotato.xero_quest_journal.core.model.QuestControlTarget.encode(targets));
            data.put("controlQuest", targets.getFirst().quest());
            data.put("control", targets.getFirst().control());
            var candidate = new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
            if (!propertiesFor(candidate).contains(data.getOrDefault("property", "visibility"))) {
                data.put("property", "visibility"); data.put("value", "visible");
            }
            remember();
            nodes.set(nodes.indexOf(node), new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data));
            init();
        }));
    }
    private void initInspector() {
        var node = selected();
        if (node == null) return;
        if (!inspectorNode.equals(node.id())) {
            inspectorNode = node.id();
            inspectorScroll = 0;
            inspectorPosition = inspectorTarget = 0;
        }
        int firstWidget = children().size();
        int x = canvasRight() + 16, w = side() - 24, y = 60 - inspectorScroll;
        var id = new EditBox(font, x, y, w, 18, text("id"));
        id.setMaxLength(64);
        id.setValue(node.id());
        id.setResponder(this::rename);
        addRenderableWidget(id);
        y += 18;
        labels.add(new Label(text("position"), x, y));
        var xField = new EditBox(font, x, y + 12, (w - 4) / 2, 18, text("x"));
        nodeX = xField;
        xField.setMaxLength(7);
        xField.setValue(Integer.toString(node.x()));
        xField.setResponder(value -> position("x", value));
        addRenderableWidget(xField);
        var yField = new EditBox(font, x + w / 2 + 2, y + 12, (w - 4) / 2, 18, text("y"));
        nodeY = yField;
        yField.setMaxLength(7);
        yField.setValue(Integer.toString(node.y()));
        yField.setResponder(value -> position("y", value));
        addRenderableWidget(yField);
        y += 42;
        labels.add(new Label(text("size"), x, y));
        int dimensionWidth = (w - 32) / 2;
        nodeWidthField = new EditBox(font, x, y + 12, dimensionWidth, 18, text("width"));
        nodeWidthField.setMaxLength(4);
        nodeWidthField.setValue(Integer.toString(nodeWidth(node)));
        nodeWidthField.setTooltip(Tooltip.create(text("width")));
        nodeWidthField.setResponder(value -> dimension("layoutWidth", value));
        addRenderableWidget(nodeWidthField);
        nodeHeightField = new EditBox(font, x + dimensionWidth + 4, y + 12, dimensionWidth, 18, text("height"));
        nodeHeightField.setMaxLength(6);
        nodeHeightField.setValue(Integer.toString(nodeHeight(node)));
        nodeHeightField.setTooltip(Tooltip.create(text("height")));
        nodeHeightField.setResponder(value -> dimension("layoutHeight", value));
        addRenderableWidget(nodeHeightField);
        button(x + w - 24, y + 9, 24, null, "auto_size", this::resetNodeSize);
        y += 42;
        addRenderableWidget(Checkbox.builder(text("show_all"), font).pos(x, y)
                .selected(Boolean.parseBoolean(node.get("showAll", "true")))
                .onValueChange((box, value) -> { update("showAll", Boolean.toString(value)); init(); }).build());
        y += 30;
        field("note", "note", node.get("note", ""), y);
        y += 42;
        if (node.type().equals("event")) {
            addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("event"),
                    TriggerNodeLibrary.events(node.get("category", "")),
                    node.get("event", "click"), key -> text("event." + key), key -> { update("event", key); init(); }, styles, "graph-event"));
            if (node.get("event", "click").equals("dimension") && minecraft.level != null) {
                var dimensions = new ArrayList<String>();
                dimensions.add("");
                minecraft.getConnection().levels().stream().map(key -> key.location().toString()).sorted().forEach(dimensions::add);
                if (!dimensions.contains(node.get("target", ""))) dimensions.add(node.get("target", ""));
                labels.add(new Label(text("target"), x, y + 26));
                addRenderableWidget(new StyledChoiceButton<>(x, y + 38, w, 18, text("target"), dimensions,
                        node.get("target", ""), key -> key.isEmpty() ? text("any") : Component.literal(key),
                        key -> update("target", key), styles, "graph-dimension"));
            } else field("target", "target", node.get("target", ""), y + 26);
            field("cooldown", "cooldown", node.get("cooldown", "20"), y + 62);
            addRenderableWidget(new StyledChoiceButton<>(x, y + 98, w, 20, text("once"),
                    List.of(false, true), Boolean.parseBoolean(node.get("once", "false")),
                    value -> text(value ? "once" : "repeat"), value -> update("once", value.toString()), styles, "graph-once"));
            if (TriggerGraph.CONTROL_EVENTS.contains(node.get("event", "click"))) {
                addRenderableWidget(new StyledChoiceButton<>(x, y + 126, w, 20, text("pointer.region"),
                        xtdpotato.xero_quest_journal.core.runtime.QuestPointerEvents.REGIONS, node.get("pointerRegion", "any"),
                        value -> text("pointer.region." + value), value -> { update("pointerRegion", value); init(); },
                        styles, "graph-pointer-region"));
                if (!node.get("pointerRegion", "any").equals("any"))
                    field("pointer.radius", "radius", node.get("radius", "5"), y + 156);
                if (node.get("pointerRegion", "any").equals("point")) {
                    field("pointer.x", "regionX", node.get("regionX", "0"), y + 198);
                    field("pointer.y", "regionY", node.get("regionY", "0"), y + 240);
                }
            }
        } else if (node.type().equals("context")) {
            String valueField = node.get("field", "experienceLevel");
            boolean mutable = TriggerGraph.SETTABLE_PLAYER_FIELDS.contains(valueField)
                    || TriggerGraph.SETTABLE_WORLD_FIELDS.contains(valueField);
            labels.add(new Label(text(mutable ? "context.mutable" : "readonly_context"), x, y));
            addRenderableWidget(new StyledChoiceButton<>(x, y + 18, w, 20, text("field"),
                    TriggerNodeLibrary.fields(node.get("category", "")), node.get("field", "eventType"),
                    key -> text("field." + key), key -> update("field", key), styles, "graph-context-field"));
            if (TriggerNodeLibrary.PLAYER_VALUES.contains(valueField)
                    && !List.of("eventType", "eventTarget").contains(valueField))
                field("context.player", "player", node.get("player", "@s"), y + 48);
        } else if (node.type().equals("js")) {
            addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("mode"),
                    List.of("visual", "script"), node.get("mode", "script"),
                    value -> text("mode." + value), value -> { update("mode", value); init(); },
                    styles, "graph-mode"));
            if (node.get("mode", "script").equals("visual")) {
                boolean secondary = visualSecond && List.of("and", "or").contains(node.get("logic", "single"));
                visualField(node, secondary ? "field2" : "field",
                        secondary ? "eventTarget" : "eventType", y + 26);
                visualOperator(node, secondary ? "operator2" : "operator", y + 52);
                variableExpected(node, secondary ? "2" : "", y + 78);
                addRenderableWidget(new StyledChoiceButton<>(x, y + 116, w, 20, text("logic"),
                        TriggerGraph.LOGICS, node.get("logic", "single"),
                        value -> text("logic." + value), value -> { update("logic", value); init(); },
                        styles, "graph-logic"));
                if (List.of("and", "or").contains(node.get("logic", "single"))) {
                    button(x, y + 142, w, null, secondary ? "first_clause" : "second_clause",
                            () -> { visualSecond = !visualSecond; init(); });
                }
            } else {
                labels.add(new Label(text("node.js"), x, y + 27));
                labels.add(new Label(Component.literal(font.plainSubstrByWidth(
                        node.get("script", "false").replace('\n', ' '), w)), x, y + 42));
                button(x, y + 60, w, null, "edit_script", () ->
                        minecraft.setScreen(new QuestScriptEditorScreen(this, node.get("script", "false"),
                                value -> update("script", value))));
            }
            addRenderableWidget(new StyledChoiceButton<>(x, y + (node.get("mode", "script").equals("visual")
                    ? 182 : 98), w, 20, text("once"),
                    List.of(false, true), Boolean.parseBoolean(node.get("once", "false")),
                    value -> text(value ? "once" : "repeat"), value -> update("once", value.toString()),
                    styles, "graph-js-once"));
            if (node.get("mode", "script").equals("visual")) {
                String suffix = visualSecond && List.of("and", "or").contains(node.get("logic", "single")) ? "2" : "";
                String left = node.get("field" + suffix, "eventType"), right = node.get("expectedField" + suffix, "");
                if (TriggerNodeLibrary.PLAYER_VALUES.contains(left) && !List.of("eventType", "eventTarget").contains(left))
                    field("context.read_player", "fieldPlayer" + suffix, node.get("fieldPlayer" + suffix, "@s"), y + 212);
                if (TriggerNodeLibrary.PLAYER_VALUES.contains(right) && !List.of("eventType", "eventTarget").contains(right))
                    field("context.expected_player", "expectedPlayer" + suffix, node.get("expectedPlayer" + suffix, "@s"), y + 254);
            }
        } else if (List.of("complete", "advance_step", "activate", "unlock", "lock").contains(node.type())) {
            field("quest", "target", node.get("target", ""), y);
            button(x, y + 38, w, null, "choose", () -> minecraft.setScreen(new QuestTaskPickerScreen(this,
                    node.get("target", ""), "", true,
                    ids -> { if (!ids.isEmpty()) update("target", ids.getFirst()); })));
        } else if (node.type().equals("set_task_property")) {
            field("quest", "target", node.get("target", ""), y);
            button(x, y + 38, w, null, "choose", () -> minecraft.setScreen(new QuestTaskPickerScreen(this,
                    node.get("target", ""), "", true,
                    ids -> { if (!ids.isEmpty()) { update("target", ids.getFirst()); init(); } })));
            String property = node.get("property", "hidden");
            addRenderableWidget(new StyledChoiceButton<>(x, y + 66, w, 20, text("property"),
                    List.of("unlocked", "hideWhenLocked", "hidden", "triggersEnabled"), property,
                    key -> text("task_property." + key),
                    key -> { update("property", key); init(); }, styles, "graph-task-property"));
            addRenderableWidget(new StyledChoiceButton<>(x, y + 92, w, 20, text("value"),
                    List.of("true", "false"), node.get("value", "true"),
                    key -> text("task_property.value." + key), key -> update("value", key),
                    styles, "graph-task-property-value"));
            String targetId = node.get("target", "").isBlank() ? taskId : node.get("target", "");
            var task = ClientQuestDefinitions.find(targetId);
            if (task != null) {
                boolean defaultValue = switch (property) {
                    case "unlocked" -> task.defaultUnlocked();
                    case "hideWhenLocked" -> task.hideWhenLocked();
                    case "hidden" -> task.hidden();
                    default -> task.triggersEnabled();
                };
                labels.add(new Label(text("default").copy().append(": ")
                        .append(text("task_property.value." + defaultValue)), x, y + 120));
            }
        } else if (node.type().equals("discover_anecdote")) {
            field("anecdote.target", "target", node.get("target", ""), y);
            var anecdotes = xtdpotato.xero_quest_journal.client.AnecdoteClientState.snapshot().entries();
            if (!anecdotes.isEmpty())
                addRenderableWidget(new StyledChoiceButton<>(x, y + 38, w, 20, text("anecdote.target"),
                        anecdotes.stream().map(xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition::id).toList(),
                        node.get("target", anecdotes.getFirst().id()), Component::literal,
                        value -> { update("target", value); init(); }, styles, "graph-anecdote-target"));
        } else if (node.type().equals("complete_anecdote")) {
            var anecdotes = xtdpotato.xero_quest_journal.client.AnecdoteClientState.snapshot().entries();
            var ids = anecdotes.stream()
                    .map(xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition::id).toList();
            if (!ids.isEmpty()) {
                String entry = node.get("entry", ids.getFirst());
                addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("anecdote.target"),
                        ids, entry, Component::literal, value -> { update("entry", value); update("record",
                        anecdotes.stream().filter(item -> item.id().equals(value)).findFirst()
                                .flatMap(item -> item.records().stream().findFirst())
                                .map(xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition.Record::id).orElse("")); init(); },
                        styles, "graph-anecdote-entry"));
                var selected = anecdotes.stream().filter(item -> item.id().equals(entry)).findFirst().orElse(null);
                if (selected != null && !selected.records().isEmpty())
                    addRenderableWidget(new StyledChoiceButton<>(x, y + 28, w, 20, text("anecdote.record"),
                            selected.records().stream()
                                    .map(xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition.Record::id).toList(),
                            node.get("record", selected.records().getFirst().id()), Component::literal,
                            value -> update("record", value), styles, "graph-anecdote-record"));
            }
        } else if (node.type().equals("variable") || node.type().equals("set_variable")) {
            variableChoice(node, "variable", y, node.type().equals("set_variable")
                    ? variable -> !variable.readOnly() : variable -> true);
            if (node.type().equals("variable")) {
                visualOperator(node, "operator", y + 28);
                variableExpected(node, "", y + 55);
            } else {
                field("value", "value", node.get("value", ""), y + 28);
                button(x, y + 70, w, null, "variable.source", () ->
                        openVariables(chosen -> { update("sourceVariable", chosen); init(); }));
                String sourceId = node.get("sourceVariable", "");
                if (!sourceId.isEmpty()) labels.add(new Label(Component.literal(
                        font.plainSubstrByWidth(sourceId, w)), x, y + 110));
                var fields = new ArrayList<String>();
                fields.add(""); fields.addAll(TriggerGraph.FIELDS);
                addRenderableWidget(new StyledChoiceButton<>(x, y + 134, w, 20, text("travel.source"), fields,
                        node.get("sourceField", ""), value -> value.isEmpty() ? text("travel.literal") : text("field." + value),
                        value -> { update("sourceField", value); update("sourceVariable", ""); },
                        styles, "graph-source-field"));
            }
        } else if (node.type().equals("calculate_variable")) {
            variableChoice(node, "variable", y, variable -> variable.numeric() && !variable.readOnly());
            var operations = xtdpotato.xero_quest_journal.core.runtime.QuestVariableMath.OPERATIONS;
            addRenderableWidget(new StyledChoiceButton<>(x, y + 28, w, 20, text("math.operation"),
                    operations, node.get("operation", "add"), value -> text("math." + value),
                    value -> { update("operation", value); init(); }, styles, "graph-math-operation"));
            String operation = node.get("operation", "add");
            boolean unary = xtdpotato.xero_quest_journal.core.runtime.QuestVariableMath.unary(operation);
            int previewY;
            if (!unary) {
                boolean variableMode = !node.get("operandVariable", "").isBlank();
                int gap = 4, firstWidth = (w - gap) / 2;
                var literal = new StyledButton(x, y + 56, firstWidth, 20, text("math.literal"),
                        ignored -> {
                            update("operandVariable", "");
                            if (node.get("operand", "").isBlank()) update("operand", "1");
                            init();
                        }, styles, "graph-math-literal");
                literal.setSelectionHighlighted(!variableMode);
                addRenderableWidget(literal);
                var variable = new StyledButton(x + firstWidth + gap, y + 56, w - firstWidth - gap, 20,
                        text("math.variable"), ignored -> {
                            String current = node.get("operandVariable", "");
                            String first = variables().stream().filter(v -> v.numeric() && !v.readOnly())
                                    .map(QuestVariable::id).findFirst().orElse("");
                            if (current.isBlank() && !first.isBlank()) {
                                update("operandVariable", first);
                                update("operand", "");
                            }
                            init();
                        }, styles, "graph-math-variable");
                variable.setSelectionHighlighted(variableMode);
                variable.active &= variableMode || variables().stream().anyMatch(v -> v.numeric() && !v.readOnly());
                addRenderableWidget(variable);
                if (variableMode) {
                    var operands = new ArrayList<>(variables().stream().filter(v -> v.numeric())
                            .map(QuestVariable::id).distinct().toList());
                    if (!operands.contains(node.get("operandVariable", "")))
                        operands.add(node.get("operandVariable", ""));
                    if (operands.isEmpty()) operands.add("");
                    addRenderableWidget(new StyledChoiceButton<>(x, y + 82, w, 20, text("math.operand_variable"),
                            operands, node.get("operandVariable", operands.getFirst()),
                            value -> value.isBlank() ? text("variable.missing") : Component.literal(value),
                            value -> update("operandVariable", value),
                            styles, "graph-math-operand"));
                } else {
                    field("math.operand", "operand", node.get("operand", "1"), y + 82, 256);
                }
                previewY = y + 108;
            } else {
                previewY = y + 56;
            }
            addCalculatorPreview(node, x, previewY, w);
        } else if (node.type().equals("delay")) {
            field("delay.ticks", "ticks", node.get("ticks", "20"), y);
            labels.add(new Label(text("delay.note"), x, y + 38));
        } else if (node.type().equals("set_context")) {
            String scope = node.get("scope", "player");
            addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("context.scope"),
                    List.of("player", "world"), scope, value -> text("context.scope." + value),
                    value -> changeGameScope(value), styles, "graph-context-scope"));
            List<String> fields = scope.equals("world") ? TriggerGraph.SETTABLE_WORLD_FIELDS
                    : TriggerGraph.SETTABLE_PLAYER_FIELDS;
            String field = node.get("field", fields.getFirst());
            if (!fields.contains(field)) field = fields.getFirst();
            addRenderableWidget(new StyledChoiceButton<>(x, y + 28, w, 20, text("context.field"),
                    fields, field, value -> text("field." + value),
                    this::changeGameField, styles, "graph-context-field"));
            if (scope.equals("player")) field("context.player", "player", node.get("player", "@s"), y + 56);
            else field("context.dimension", "dimension", node.get("dimension", ""), y + 56);
            if (List.of("raining", "thundering", "sneaking", "sprinting").contains(field)) {
                labels.add(new Label(text("context.value"), x, y + 98));
                addRenderableWidget(new StyledChoiceButton<>(x, y + 110, w, 20, text("context.value"),
                        List.of("false", "true"), node.get("value", "false"),
                        value -> text("task_property.value." + value), value -> update("value", value),
                        styles, "graph-context-value"));
            } else field("context.value", "value", node.get("value", "0"), y + 98);
            labels.add(new Label(text("context.drop_hint"), x, y + 138));
        } else if (List.of("teleport", "enter_structure", "locate_structure", "locate_biome").contains(node.type())) {
            if (node.type().equals("enter_structure")) {
                field("travel.structure", "structure", node.get("structure", ""), y);
                button(x, y + 38, w, null, "choose", () -> minecraft.setScreen(
                        new QuestStructurePickerScreen(this, value -> { update("structure", value); init(); })));
                y += 70;
            } else {
                field("travel.dimension", "dimension", node.get("dimension", ""), y);
                coordinateSource(node, "dimension", y + 36);
                y += 66;
            }
            if (node.type().startsWith("locate_")) {
                field("travel." + node.type(), "target", node.get("target", ""), y);
                field(node.type().equals("locate_biome") ? "travel.block_radius" : "travel.chunk_radius",
                        "radius", node.get("radius", node.type().equals("locate_biome") ? "256" : "16"), y + 42);
                var sharedStrings = variables().stream()
                        .filter(variable -> !variable.numeric() && variable.teamShared())
                        .map(QuestVariable::id).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                String stored = node.get("storeVariable", "");
                if (!stored.isBlank() && !sharedStrings.contains(stored)) sharedStrings.add(stored);
                if (sharedStrings.isEmpty()) sharedStrings.add("");
                addRenderableWidget(new StyledChoiceButton<>(x, y + 82, w, 20,
                        text("travel.store_result"), sharedStrings,
                        stored.isBlank() ? sharedStrings.getFirst() : stored,
                        value -> value.isBlank() ? text("variable.missing") : Component.literal(value),
                        value -> update("storeVariable", value), styles, "graph-locate-store-variable"));
                addInspectorHint(text("travel.store_result"),
                        text("travel.store_result_hint"), x, y + 108, w, !stored.isBlank());
            } else {
                for (String axis : List.of("x", "y", "z")) {
                    field("travel." + axis, axis, node.get(axis, ""), y);
                    coordinateSource(node, axis, y + 36);
                    y += 66;
                }
                if (node.type().equals("teleport")) button(x, y, w, null, "travel.use_located", () -> {
                    for (String axis : List.of("x", "y", "z"))
                        update(axis + "Field", "located" + axis.toUpperCase(Locale.ROOT));
                    update("dimensionField", "locatedDimension");
                    init();
                });
            }
        } else if (xtdpotato.xero_quest_journal.core.model.MailNodeSettings.TYPES.contains(node.type())) {
            boolean taskAction = xtdpotato.xero_quest_journal.core.model.MailNodeSettings.TASK_TYPES
                    .contains(node.type());
            if (taskAction) {
                field("quest", "target", node.get("target", ""), y);
                button(x, y + 38, w, null, "choose", () -> minecraft.setScreen(new QuestTaskPickerScreen(this,
                        node.get("target", ""), "", true,
                        ids -> { if (!ids.isEmpty()) { update("target", ids.getFirst()); init(); } })));
                y += 28;
            } else {
                field("mail.id", "mailId", node.get("mailId", ""), y);
            }
            int pickerX = canvasRight() + 16, pickerW = side() - 24;
            labels.add(new Label(text("mail.player"), pickerX, y + 42));
            var playerField = new SuggestedEditBox(this, font, pickerX, y + 54,
                    pickerW - 28, 18, text("mail.player"), "mail_player");
            playerField.setMaxLength(256);
            playerField.setValue(node.get("player", ""));
            playerField.setResponder(value -> update("player", value));
            addRenderableWidget(playerField);
            button(pickerX + pickerW - 24, y + 52, 24, null, "mail.pick_player", () -> {
                var choices = new ArrayList<QuestValuePickerScreen.Option>();
                choices.add(new QuestValuePickerScreen.Option("", text("mail.current_player").getString()));
                choices.add(new QuestValuePickerScreen.Option("@s", "@s"));
                choices.add(new QuestValuePickerScreen.Option("@p", "@p"));
                choices.add(new QuestValuePickerScreen.Option("@a", "@a"));
                if (minecraft.getConnection() != null) minecraft.getConnection().getOnlinePlayers()
                        .forEach(info -> choices.add(new QuestValuePickerScreen.Option(
                                info.getProfile().getName(), info.getProfile().getName())));
                minecraft.setScreen(new QuestValuePickerScreen(this, text("mail.player").getString(), "",
                        () -> choices, node.get("player", ""), value -> { update("player", value); init(); }));
            });
            y += 84;
            if (node.type().equals("mail_send")) {
                field("mail.sender", "sender", node.get("sender", ""), y);
                field("mail.subject", "subject", node.get("subject", ""), y + 42);
                field("mail.body", "body", node.get("body", ""), y + 84);
                button(x, y + 120, w, null, "expand_value", () ->
                        minecraft.setScreen(new QuestPropertyValueScreen(this, text("mail.body"), node.get("body", ""),
                                value -> { update("body", value); init(); })));
                field("mail.expiresIn", "expiresIn", node.get("expiresIn", "0"), y + 152);
                button(x, y + 194, w, null, "mail.attachments", () ->
                        minecraft.setScreen(QuestRewardsScreen.mail(this,
                                xtdpotato.xero_quest_journal.core.runtime.QuestMailActions.attachments(node),
                                rewards -> { update("rewards", GSON.toJson(rewards)); init(); })));
                field("mail.icon", "icon", node.get("icon", ""), y + 226);
                addRenderableWidget(new StyledChoiceButton<>(x, y + 268, w, 20, text("mail.treasured"),
                        List.of(false, true), Boolean.parseBoolean(node.get("treasured", "false")),
                        value -> Component.translatable(value ? "gui.yes" : "gui.no"),
                        value -> update("treasured", value.toString()), styles, "graph-mail-treasured"));
                addRenderableWidget(new StyledChoiceButton<>(x, y + 300, w, 20, text("mail.clock"),
                        List.of(false, true), Boolean.parseBoolean(node.get("gameTime", "false")),
                        value -> Component.translatable("screen.xero_quest_journal.mailbox.clock." + (value ? "game" : "real")),
                        value -> update("gameTime", value.toString()), styles, "graph-mail-clock"));
                actionOnce(node, x, y + 330, w);
            } else if (node.type().equals("mail_check")) {
                addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("mail.state"),
                        xtdpotato.xero_quest_journal.core.model.MailNodeSettings.STATES, node.get("state", "exists"),
                        value -> text("mail.state." + value), value -> update("state", value), styles, "graph-mail-state"));
            } else if (node.type().equals("mail_delete")) {
                addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("mail.delete_mode"),
                        List.of(false, true), Boolean.parseBoolean(node.get("discardPending", "false")),
                        value -> text(value ? "mail.discard" : "mail.protect"),
                        value -> update("discardPending", value.toString()), styles, "graph-mail-delete-mode"));
            } else {
                String property = node.get("property", "subject");
                addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("property"),
                        xtdpotato.xero_quest_journal.core.model.MailNodeSettings.PROPERTIES, property,
                        value -> text("mail." + value), value -> {
                            remember();
                            var data = new LinkedHashMap<>(selected().data());
                            data.put("property", value);
                            data.put("value", List.of("read", "starred", "treasured").contains(value) ? "false"
                                    : value.equals("expiresIn") ? "0" : "");
                            var current = selected();
                            nodes.set(nodes.indexOf(current), new TriggerGraph.Node(current.id(), current.type(),
                                    current.x(), current.y(), data));
                            init();
                        }, styles, "graph-mail-property"));
                if (List.of("read", "starred", "treasured").contains(property)) {
                    addRenderableWidget(new StyledChoiceButton<>(x, y + 28, w, 20, text("value"),
                            List.of(false, true), Boolean.parseBoolean(node.get("value", "false")),
                            value -> Component.translatable(value ? "gui.yes" : "gui.no"),
                            value -> update("value", value.toString()), styles, "graph-mail-value"));
                } else {
                    field("value", "value", node.get("value", ""), y + 28);
                    button(x, y + 70, w, null, "expand_value", () ->
                            minecraft.setScreen(new QuestPropertyValueScreen(this, text("mail." + property),
                                    node.get("value", ""), value -> { update("value", value); init(); })));
                }
            }
        } else if (node.type().equals("reward")) {
            button(x, y, w, null, "custom_rewards", () -> minecraft.setScreen(new QuestRewardsScreen(this,
                    xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs.rewards(node), "automatic",
                    (rewards, mode) -> { update("rewards", GSON.toJson(rewards)); init(); }, true)));
            labels.add(new Label(text("independent_rewards"), x, y + 38));
            actionOnce(node, x, y + 56, w);
        } else if (node.type().equals("command")) {
            field("command", "command", node.get("command", ""), y);
            button(x, y + 40, w, null, "expand_value", () ->
                    minecraft.setScreen(new QuestPropertyValueScreen(this, text("command"),
                            node.get("command", ""), value -> { update("command", value); init(); })));
        } else if (node.type().equals("set_overview_background")) {
            field("overview.source", "source", node.get("source", ""), y);
            button(x, y + 38, w, null, "choose", () -> minecraft.setScreen(
                    QuestScreen.pickResource(this, node.get("source", ""), false,
                            value -> { update("source", value); init(); })));
            addRenderableWidget(new StyledChoiceButton<>(x, y + 66, w, 20, text("overview.scope"),
                    List.of("player", "server"), node.get("scope", "player"),
                    value -> text("overview.scope." + value), value -> update("scope", value),
                    styles, "graph-overview-scope"));
            button(x, y + 94, w, null, "overview.reset", () -> { update("source", ""); init(); });
            actionOnce(node, x, y + 122, w);
        } else if (node.type().equals("set_hot_event_background")) {
            field("hot_events.background_source", "source", node.get("source", ""), y);
            button(x, y + 38, w, null, "choose", () -> {
                if (node.get("type", "image").equals("video")) QuestMediaPicker.choose(this, true,
                        value -> { update("source", value); init(); });
                else minecraft.setScreen(QuestScreen.pickResource(this, node.get("source", ""), false,
                        value -> { update("source", value); init(); }));
            });
            addRenderableWidget(new StyledChoiceButton<>(x, y + 66, w, 20, text("hot_events.background_type"),
                    List.of("image", "video"), node.get("type", "image"),
                    value -> text("hot_events.background_type." + value), value -> { update("type", value); init(); },
                    styles, "graph-hot-background-type"));
            addRenderableWidget(new StyledChoiceButton<>(x, y + 94, w, 20, text("hot_events.background_mode"),
                    List.of("cover", "contain", "stretch", "adaptive"), node.get("mode", "cover"),
                    value -> text("hot_events.background_mode." + value), value -> { update("mode", value); init(); },
                    styles, "graph-hot-background-mode"));
            field("hot_events.background_scale", "scale", node.get("scale", "1"), y + 122);
            field("hot_events.background_offset_x", "offsetX", node.get("offsetX", "0"), y + 150);
            field("hot_events.background_offset_y", "offsetY", node.get("offsetY", "0"), y + 178);
            field("hot_events.background_music", "music", node.get("music", ""), y + 206);
            button(x, y + 244, w, null, "hot_events.background_music", () -> QuestMediaPicker.choose(
                    this, false, value -> { update("music", value); init(); }));
            actionOnce(node, x, y + 272, w);
            button(x, y + 300, w, null, "hot_events.visual_background", () -> {
                try {
                    var draft = new xtdpotato.xero_quest_journal.core.model.HotEventCanvas(
                            node.get("source", ""), "", List.of(), true, Map.of(), node.get("type", "image"),
                            Double.parseDouble(node.get("scale", "1")), Integer.parseInt(node.get("offsetX", "0")),
                            Integer.parseInt(node.get("offsetY", "0")), node.get("mode", "cover"), node.get("music", ""));
                    minecraft.setScreen(new HotEventBackgroundScreen(this, draft, value -> {
                        if (!node.id().equals(selected)) return;
                        var data = new LinkedHashMap<>(selected().data());
                        data.put("source", value.backgroundImage()); data.put("type", value.backgroundType());
                        data.put("mode", value.backgroundMode()); data.put("scale", Double.toString(value.backgroundScale()));
                        data.put("offsetX", Integer.toString(value.backgroundOffsetX()));
                        data.put("offsetY", Integer.toString(value.backgroundOffsetY()));
                        data.put("music", value.backgroundMusic());
                        replaceData(selected(), "source", data);
                    }));
                } catch (IllegalArgumentException invalid) { message = "invalid"; }
            });
        } else if (node.type().equals("set_theme")) {
            addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("theme.key"),
                    QuestUiTheme.KEYS, node.get("key", "accent"),
                    QuestTriggerGraphScreen::themeKeyName, value -> { update("key", value); init(); },
                    styles, "graph-theme-key"));
            field("theme.value", "value", node.get("value", ""), y + 28);
            button(x, y + 70, w, null, "expand_value", () ->
                    minecraft.setScreen(new QuestPropertyValueScreen(this, text("theme.value"),
                            node.get("value", ""), value -> { update("value", value); init(); })));
            actionOnce(node, x, y + 112, w);
        } else if (node.type().startsWith("kubejs_")) {
            boolean enabled = QuestNodeLibraryScreen.available(node.type());
            if (!enabled) labels.add(new Label(Component.translatable(
                    "screen.xero_quest_journal.graph.requires_mod", "KubeJS"), x, y));
            y += 22;
            if (node.type().equals("kubejs_call")) {
                field("kubejs_function", "function", node.get("function", ""), y);
                labels.add(new Label(Component.literal("global.functionName(event)"), x, y + 37));
            } else {
                button(x, y, w, null, "edit_script", () -> {
                    if (enabled) minecraft.setScreen(new QuestScriptEditorScreen(this, nodeTitle(node),
                            node.get("script", ""), value -> update("script", value),
                            xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind.KUBEJS));
                });
                var edit = (AbstractWidget) children().getLast();
                edit.active = enabled;
                if (!enabled) edit.setTooltip(Tooltip.create(Component.translatable(
                        "screen.xero_quest_journal.graph.requires_mod", "KubeJS")));
            }
            labels.add(new Label(text("kubejs_trusted"), x, y + 57));
            actionOnce(node, x, y + 78, w);
        } else if (node.type().equals("set_property") || node.type().equals("reset_property")) {
            var targets = new ArrayList<String>();
            targets.add(""); targets.addAll(controlIds);
            if (!targets.contains(node.get("control", ""))) targets.add(node.get("control", ""));
            labels.add(new Label(text("control"), x, y));
            addRenderableWidget(new StyledChoiceButton<>(x, y + 12, w, 18, text("control"), targets,
                    node.get("control", ""), key -> key.isEmpty() ? text("current") : Component.literal(key),
                    this::changeControl, styles, "graph-control"));
            button(x, y + 36, w, null, "picker.open", this::pickControls);
            int count = xtdpotato.xero_quest_journal.core.model.QuestControlTarget.decode(node.get("controls", "")).size();
            labels.add(new Label(Component.translatable("screen.xero_quest_journal.graph.picker.count", count),
                    x, y + 60));
            y += 42;
            String property = node.get("property", "visibility");
            addRenderableWidget(new StyledChoiceButton<>(x, y + 38, w, 20, text("property"), propertiesFor(node),
                    property, QuestTriggerGraphScreen::propertyName, this::changeProperty, styles, "graph-property"));
            labels.add(new Label(text("configured").copy().append(": "
                    + font.plainSubstrByWidth(configuredValue(node, property), w - 20)), x, y + 63));
            labels.add(new Label(text("default").copy().append(": "
                    + font.plainSubstrByWidth(ControlProperties.defaultValue(property), w - 20)), x, y + 75));
            if (node.type().equals("reset_property")) {
                addRenderableWidget(new StyledChoiceButton<>(x, y + 92, w, 20, text("reset_scope"),
                        List.of(false, true), Boolean.parseBoolean(node.get("all", "false")),
                        all -> text(all ? "reset_all" : "reset_one"),
                        all -> update("all", all.toString()), styles, "graph-reset-scope"));
            } else {
                if (node.get("valueVariable", "").isBlank())
                    field("value", "value", node.get("value", "visible"), y + 92);
                else labels.add(new Label(Component.literal(node.get("valueVariable", "")), x, y + 99));
                button(x, y + 132, w, null, "expand_value", () -> {
                String nodeId = node.id();
                minecraft.setScreen(new QuestPropertyValueScreen(this, propertyName(property), node.get("value", ""),
                        value -> {
                            if (!nodeId.equals(selected)) return;
                            update("value", value);
                            init();
                        }));
                });
                if (property.equals("displayText"))
                    button(x, y + 168, w, node.get("valueVariable", "").isBlank() ? "V" : "\u00d7",
                            "variable.bind", () -> {
                                if (!node.get("valueVariable", "").isBlank()) {
                                    update("valueVariable", "");
                                    init();
                                } else openVariables(chosenId -> {
                                    update("valueVariable", chosenId);
                                    update("value", "");
                                    init();
                                });
                            });
                if (property.equals("displayText"))
                    addInspectorHint(text("property.displayText"),
                            text("property.displayText_hint"), x, y + 194, w, true);
            }
        }
        var widgets = new ArrayList<>(children().subList(firstWidget, children().size()));
        int widgetBottom = widgets.stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).mapToInt(widget -> widget.getBottom() + inspectorScroll)
                .max().orElse(60);
        int labelBottom = labels.stream().mapToInt(label -> label.y() + font.lineHeight).max().orElse(60);
        int hintBottom = inspectorHints.stream().mapToInt(hint -> hint.y() + 54).max().orElse(60);
        inspectorMaxScroll = Math.max(widgetBottom, Math.max(labelBottom, hintBottom)) - (height - 42);
        inspectorMaxScroll = Math.max(0, inspectorMaxScroll);
        for (var widget : widgets) if (widget instanceof AbstractWidget control) {
            inspectorWidgets.add(control);
            if (control instanceof StyledChoiceButton<?> choice) choice.searchable();
        }
        button(x, height - 32, w, null, "delete", this::deleteSelected);
    }
    private void changeGameScope(String scope) {
        if (!List.of("player", "world").contains(scope)) return;
        var node = selected();
        if (node == null) return;
        var data = new LinkedHashMap<>(node.data());
        data.put("scope", scope);
        data.put("field", scope.equals("player") ? "experienceLevel" : "dayTime");
        data.put("value", "0");
        if (scope.equals("player")) data.put("player", "@s");
        else data.put("dimension", "");
        replaceData(node, "scope", data);
        init();
    }
    private void changeGameField(String field) {
        var node = selected();
        if (node == null) return;
        var data = new LinkedHashMap<>(node.data());
        data.put("field", field);
        data.put("value", defaultGameValue(field));
        replaceData(node, "context.field", data);
        init();
    }
    private static String defaultGameValue(String field) {
        return List.of("raining", "thundering", "sneaking", "sprinting").contains(field) ? "false"
                : field.equals("maxHealth") ? "20" : "0";
    }
    private static List<String> outputPorts(TriggerGraph.Node node) {
        if (TriggerGraph.branching(node.type())) return List.of("true", "false");
        return hasNextOutput(node.type()) ? List.of("next") : List.of();
    }
    private static boolean hasNextOutput(String type) {
        return type.equals("event") || type.equals("delay") || type.equals("calculate_variable");
    }
    private void actionOnce(TriggerGraph.Node node, int x, int y, int w) {
        addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("once"),
                List.of(true, false), Boolean.parseBoolean(node.get("once", "true")),
                value -> text(value ? "once" : "repeat"), value -> update("once", value.toString()),
                styles, "graph-action-once"));
    }
    private static Component themeKeyName(String key) {
        return Component.translatable("screen.xero_quest_journal.theme." + key);
    }
    private void coordinateSource(TriggerGraph.Node node, String axis, int y) {
        int x = canvasRight() + 16, w = side() - 24;
        var sources = new ArrayList<String>();
        sources.add("");
        sources.addAll(TriggerGraph.FIELDS);
        variables().forEach(value -> sources.add("var:" + value.id()));
        addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("travel.source"), sources,
                node.get(axis + "Field", ""), value -> value.isBlank() ? text("travel.literal")
                        : value.startsWith("var:") ? Component.literal(value.substring(4)) : text("field." + value),
                value -> update(axis + "Field", value), styles, "graph-" + axis + "-source"));
    }
    private void variableChoice(TriggerGraph.Node node, String key, int y) {
        variableChoice(node, key, y, variable -> true);
    }
    private void addCalculatorPreview(TriggerGraph.Node node, int x, int y, int width) {
        String id = node.get("variable", "");
        QuestVariable target = variables().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (target == null) {
            addInspectorHint(text("math.preview"), text("math.target_missing"), x, y, width, false);
            return;
        }
        String operation = node.get("operation", "add");
        String left = previewVariableValue(target);
        String right = "";
        boolean unary = xtdpotato.xero_quest_journal.core.runtime.QuestVariableMath.unary(operation);
        if (!unary) {
            String operandVariable = node.get("operandVariable", "");
            QuestVariable operand = variables().stream().filter(value -> value.id().equals(operandVariable))
                    .findFirst().orElse(null);
            right = operand == null ? node.get("operand", "1") : previewVariableValue(operand);
        }
        String expression = unary
                ? Component.translatable("screen.xero_quest_journal.graph.math.expression_unary",
                        text("math." + operation), left).getString()
                : Component.translatable("screen.xero_quest_journal.graph.math.expression_binary",
                        left, text("math." + operation), right).getString();
        try {
            String result = xtdpotato.xero_quest_journal.core.runtime.QuestVariableMath.evaluate(
                    operation, left, right);
            String sourceHint = operation.matches("sin|cos|tan|asin|acos|atan|atan2")
                    ? text("math.radians").getString() : text("math.preview_source").getString();
            addInspectorHint(text("math.preview"),
                    Component.literal(expression + "\n")
                            .append(Component.literal(text("math.result").getString() + ": " + result))
                            .append("\n").append(sourceHint),
                    x, y, width, true);
        } catch (RuntimeException invalid) {
            addInspectorHint(text("math.preview"),
                    Component.literal(expression + "\n").append(text("math.invalid_result")),
                    x, y, width, false);
        }
    }

    private String previewVariableValue(QuestVariable variable) {
        String live = ClientControlState.variable(variable.id());
        if (live != null) return live;
        if (variable.readOnly()) {
            var progress = ClientQuestProgress.view(variable.scope());
            if (progress != null) return switch (variable.name()) {
                case "progress_current" -> Integer.toString(progress.current());
                case "progress_required" -> Integer.toString(progress.required());
                case "progress_ratio" -> progress.required() <= 0 ? "0"
                        : Double.toString(progress.current() / (double) progress.required());
                default -> variable.initial();
            };
        }
        return variable.initial();
    }

    private void addInspectorHint(Component title, Component detail, int x, int y, int width, boolean valid) {
        inspectorHints.add(new InspectorHint(title, detail, x, y, width, valid));
    }

    private void variableChoice(TriggerGraph.Node node, String key, int y,
                                java.util.function.Predicate<QuestVariable> filter) {
        int x = canvasRight() + 16, w = side() - 24;
        var ids = new ArrayList<String>();
        variables().stream().filter(filter).forEach(v -> ids.add(v.id()));
        if (!ids.contains(node.get(key, ""))) ids.add(node.get(key, ""));
        if (ids.isEmpty()) ids.add("");
        addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text("variable.target"),
                ids, node.get(key, ""), id -> id.isEmpty() ? text("variable.missing") : Component.literal(id),
                id -> update(key, id), styles, "graph-" + key));
    }
    private void variableExpected(TriggerGraph.Node node, String suffix, int y) {
        int x = canvasRight() + 16, w = side() - 24;
        String key = "expected" + suffix, binding = "expectedVar" + suffix;
        String contextBinding = "expectedField" + suffix;
        boolean bound = !node.get(binding, "").isBlank() || !node.get(contextBinding, "").isBlank();
        labels.add(new Label(text("expected"), x, y));
        var input = new EditBox(font, x, y + 12, w - 28, 18, text("expected"));
        input.setMaxLength(4096);
        input.setValue(!node.get(binding, "").isBlank() ? node.get(binding, "") :
                !node.get(contextBinding, "").isBlank() ? text("field." + node.get(contextBinding, "")).getString()
                        : node.get(key, ""));
        input.setEditable(!bound);
        input.setResponder(value -> update(key, value));
        addRenderableWidget(input);
        button(x + w - 24, y + 9, 24, !bound ? "V" : "\u00d7",
                "variable.bind", () -> {
                    if (bound) { update(binding, ""); update(contextBinding, ""); init(); }
                    else openVariables(id -> { update(binding, id); init(); });
                });
    }
    private void visualField(TriggerGraph.Node node, String key, String fallback, int y) {
        int x = canvasRight() + 16, w = side() - 24;
        var fields = new ArrayList<>(TriggerGraph.FIELDS);
        variables().forEach(v -> fields.add("var:" + v.id()));
        if (!fields.contains(node.get(key, fallback))) fields.add(node.get(key, fallback));
        addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text(key), fields,
                node.get(key, fallback), value -> value.startsWith("var:")
                        ? Component.literal(value.substring(4)) : text("field." + value),
                value -> update(key, value), styles, "graph-" + key));
    }
    private void visualOperator(TriggerGraph.Node node, String key, int y) {
        int x = canvasRight() + 16, w = side() - 24;
        addRenderableWidget(new StyledChoiceButton<>(x, y, w, 20, text(key), TriggerGraph.OPERATORS,
                node.get(key, "eq"), value -> text("operator." + value),
                value -> update(key, value), styles, "graph-" + key));
    }
    private void deleteSelected() {
        if (selectedEdge != null) {
            remember();
            edges.remove(selectedEdge);
            selectedEdge = contextEdge = null;
            message = "";
            init();
            return;
        }
        if (selectedIds.isEmpty()) return;
        remember();
        edges.removeIf(edge -> selectedIds.contains(edge.from()) || selectedIds.contains(edge.to()));
        nodes.removeIf(node -> selectedIds.contains(node.id()));
        selectedIds.clear();
        selected = wireFrom = "";
        message = "";
        init();
    }
    private void toggleSource() {
        try {
            if (sourceMode) {
                var parsed = parseSource();
                remember();
                load(parsed);
                sourceDiagnostic = null;
                graphIssue = null;
                invalidLinkTarget = "";
            }
            sourceMode = !sourceMode;
            source = null;
            message = "";
            init();
        } catch (RuntimeException invalid) { showSourceProblem(invalid); }
    }
    private String defaultMailId(String nodeId) {
        String id = (taskId.isBlank() ? "task" : taskId) + "/"
                + (currentControl.isBlank() ? "control" : currentControl) + "/" + nodeId;
        return id.length() <= 256 ? id : "quest/" + java.util.UUID.nameUUIDFromBytes(
                id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private TriggerGraph withMailIds(TriggerGraph graph) {
        return new TriggerGraph(graph.nodes().stream().map(node -> {
            if (!node.type().equals("mail_send") || !node.get("mailId", "").isBlank()) return node;
            var data = new LinkedHashMap<>(node.data());
            data.put("mailId", defaultMailId(node.id()));
            return new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
        }).toList(), graph.edges());
    }
    private TriggerGraph parseSource() {
        var parsed = GSON.fromJson(source.getValue(), TriggerGraph.class);
        if (parsed == null) throw new IllegalArgumentException("Expected a trigger graph");
        return new TriggerGraph(parsed.nodes(), parsed.edges());
    }
    private void sourceChanged(String value) {
        if (!restoringSource && !value.equals(sourceDraft)) {
            sourceUndo.push(sourceDraft);
            while (sourceUndo.size() > 128) sourceUndo.removeLast();
            sourceRedo.clear();
        }
        sourceDraft = value;
        sourceDiagnostic = null;
        if (sourceRepairButton != null) sourceRepairButton.active = false;
        if (sourceCopyButton != null) sourceCopyButton.active = false;
        message = "";
    }
    private void restoreSource(Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return;
        to.push(source.getValue());
        restoringSource = true;
        try { source.setValue(from.pop()); }
        finally { restoringSource = false; }
        setFocused(source);
    }
    private void showSourceProblem(RuntimeException invalid) {
        sourceDiagnostic = QuestSourceFormatter.diagnose(source.getValue());
        if (sourceDiagnostic == null) sourceDiagnostic = new QuestSourceFormatter.Diagnostic(
                1, 1, String.valueOf(invalid.getMessage()), null);
        message = "invalid";
        sourceRepairButton.active = sourceDiagnostic.repair() != null;
        sourceCopyButton.active = true;
        int lines = Math.max(0, sourceDiagnostic.line() - 1);
        ((SourceBox) source).restoreScroll(lines * font.lineHeight);
        setFocused(source);
    }
    private void formatSource() {
        try {
            String formatted = QuestSourceFormatter.format(source.getValue());
            var parsed = GSON.fromJson(formatted, TriggerGraph.class);
            if (parsed == null) throw new IllegalArgumentException("Expected a trigger graph");
            new TriggerGraph(parsed.nodes(), parsed.edges());
            if (!formatted.equals(source.getValue())) source.setValue(formatted);
            sourceDiagnostic = null;
            message = "";
        } catch (RuntimeException invalid) { showSourceProblem(invalid); }
    }
    private void repairSource() {
        if (sourceDiagnostic == null || sourceDiagnostic.repair() == null) return;
        source.setValue(sourceDiagnostic.repair());
        formatSource();
    }
    private void copySourceProblem() {
        if (sourceDiagnostic == null) return;
        minecraft.keyboardHandler.setClipboard(Component.translatable("screen.xero_quest_journal.source.diagnostic",
                sourceDiagnostic.line(), sourceDiagnostic.column(), sourceDiagnostic.reason()).getString());
    }
    private void save() {
        validatingNode = null;
        validatingProperty = "";
        try {
            TriggerGraph graph = withMailIds(sourceMode ? parseSource() : graph());
            if (graph == null || !message.isEmpty()) throw new IllegalArgumentException();
            for (var node : graph.nodes()) {
                validatingNode = node;
                validatingProperty = "value";
                if (node.type().equals("set_property"))
                    xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs.validateProperty(
                            node.get("property", "visibility"), node.get("value", "visible"));
                validatingProperty = "mail.id";
                if (xtdpotato.xero_quest_journal.core.model.MailNodeSettings.TYPES.contains(node.type())
                        && node.get("mailId", "").isBlank()) throw new IllegalArgumentException("Mail ID is required");
                if (node.type().startsWith("mail_")) {
                    validatingProperty = "mail.player";
                    xtdpotato.xero_quest_journal.core.model.MailNodeSettings.validatePlayer(node.get("player", ""));
                }
                if (node.type().equals("mail_send")) {
                    validatingProperty = "mail.subject";
                    if (node.get("subject", "").isBlank())
                        throw new IllegalArgumentException(text("mail.subject_required").getString());
                    validatingProperty = "mail.body";
                    if (node.get("body", "").isBlank())
                        throw new IllegalArgumentException(text("mail.body_required").getString());
                }
                validatingProperty = "mail.attachments";
                if (node.type().equals("mail_send")
                        && xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs.rewards(node).stream()
                        .anyMatch(reward -> !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(reward)))
                    throw new IllegalArgumentException("Invalid mail attachments");
                validatingProperty = "custom_rewards";
                if (node.type().equals("reward")
                        && xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs.rewards(node).stream()
                        .anyMatch(reward -> !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(reward)))
                    throw new IllegalArgumentException("Invalid custom rewards");
                validatingProperty = "theme.value";
                if (node.type().equals("set_theme"))
                    QuestUiTheme.validate(node.get("key", "accent"), node.get("value", ""));
                if (node.type().equals("set_overview_background"))
                    QuestUiTheme.validateOverviewImage(node.get("source", ""));
                validatingProperty = "command";
                if (node.type().equals("command"))
                    xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs.validateCommand(
                            node.get("command", ""));
                validatingProperty = "kubejs_function";
                if (node.type().equals("kubejs_call") && !node.get("function", "")
                        .matches("global\\.[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
                    throw new IllegalArgumentException("Use global.functionName");
                for (String key : List.of("variable", "sourceVariable", "expectedVar", "expectedVar2",
                        "valueVariable", "operandVariable", "storeVariable")) {
                    validatingProperty = "variable.target";
                    String id = node.get(key, "");
                    if (!id.isBlank() && variables().stream().noneMatch(v -> v.id().equals(id)))
                        throw new IllegalArgumentException("Unknown variable");
                }
                validatingProperty = "variable.target";
                if (node.type().equals("calculate_variable")) {
                    var targetVariable = variables().stream().filter(v -> v.id().equals(node.get("variable", "")))
                            .findFirst().orElseThrow();
                    if (!targetVariable.numeric() || targetVariable.readOnly())
                        throw new IllegalArgumentException("Calculator target must be numeric and writable");
                    String operand = node.get("operandVariable", "");
                    if (!operand.isBlank()) {
                        var operandVariable = variables().stream().filter(v -> v.id().equals(operand))
                                .findFirst().orElseThrow();
                        if (!operandVariable.numeric())
                            throw new IllegalArgumentException("Calculator operand must be numeric");
                    }
                }
                if (node.type().equals("set_variable")) {
                    var targetVariable = variables().stream().filter(v -> v.id().equals(node.get("variable", "")))
                            .findFirst().orElseThrow();
                    if (targetVariable.readOnly())
                        throw new IllegalArgumentException("Read-only variable cannot be assigned");
                }
                if (List.of("locate_structure", "locate_biome").contains(node.type())
                        && !node.get("storeVariable", "").isBlank()) {
                    var destination = variables().stream()
                            .filter(v -> v.id().equals(node.get("storeVariable", ""))).findFirst().orElseThrow();
                    if (destination.numeric() || !destination.teamShared())
                        throw new IllegalArgumentException("Locate result requires a team-shared string variable");
                }
                for (String key : List.of("field", "field2", "xField", "yField", "zField", "dimensionField")) {
                    validatingProperty = "field";
                    String field = node.get(key, "");
                    if (field.startsWith("var:") && variables().stream().noneMatch(v -> v.id().equals(field.substring(4))))
                        throw new IllegalArgumentException("Unknown variable field");
                }
            }
            validatingNode = null;
            graphIssue = null;
            invalidLinkTarget = "";
            saved.accept(graph);
            minecraft.setScreen(parent);
        } catch (RuntimeException invalid) {
            if (sourceMode) showSourceProblem(invalid);
            else if (validatingNode != null) showGraphIssue(validatingNode, validatingProperty, invalid);
            else showGraphValidationIssue(invalid);
        }
    }
    private void showGraphValidationIssue(RuntimeException invalid) {
        var seen = new HashSet<String>();
        for (var node : nodes) {
            if (!seen.add(node.id())) {
                showGraphIssue(node, "id", invalid);
                return;
            }
        }
        var checked = new ArrayList<TriggerGraph.Edge>();
        for (var edge : edges) {
            checked.add(edge);
            try {
                new TriggerGraph(nodes, checked);
            } catch (RuntimeException connectionError) {
                var from = nodes.stream().filter(node -> node.id().equals(edge.from())).findFirst().orElse(null);
                var to = nodes.stream().filter(node -> node.id().equals(edge.to())).findFirst().orElse(null);
                if (from != null || to != null) {
                    showGraphIssue(from != null ? from : to, "connection",
                            new IllegalArgumentException(connectionReason(edge, connectionError, false)));
                    invalidLinkTarget = to == null ? "" : to.id();
                    return;
                }
                break;
            }
        }
        message = "invalid";
    }
    private String connectionReason(TriggerGraph.Edge edge, RuntimeException failure, boolean attempted) {
        var from = nodes.stream().filter(node -> node.id().equals(edge.from())).findFirst().orElse(null);
        var to = nodes.stream().filter(node -> node.id().equals(edge.to())).findFirst().orElse(null);
        long copies = edges.stream().filter(edge::equals).count();
        String reason = from == null || to == null ? "missing"
                : List.of("event", "context").contains(to.type()) ? "target"
                : TriggerGraph.ACTIONS.contains(from.type()) ? "action"
                : copies > (attempted ? 0 : 1) ? "duplicate"
                : Objects.equals(failure.getMessage(), "Cyclic trigger graph") ? "cycle"
                : "port";
        return text("connection." + reason).getString();
    }
    private void showGraphIssue(TriggerGraph.Node node, String property, RuntimeException invalid) {
        String detail = invalid.getMessage();
        if (detail == null || detail.isBlank()) detail = text("invalid").getString();
        graphIssue = new GraphIssue(node.id(), property, Component.literal(detail));
        invalidLinkTarget = "";
        selected = node.id();
        selectedIds.clear();
        selectedIds.add(node.id());
        message = "invalid";
        init();
        var visible = selected();
        if (visible != null) {
            zoom = Math.min(zoom, Math.min((canvasRight() - 32.0) / nodeWidth(visible),
                    (height - 112.0) / nodeHeight(visible)));
            zoom = Math.max(.25, zoom);
            double centerX = sx(visible) + nodeWidth(visible) * zoom / 2;
            double centerY = sy(visible) + nodeHeight(visible) * zoom / 2;
            panX += (canvasRight() + 8) / 2.0 - centerX;
            panY += (height + 12) / 2.0 - centerY;
        }
        var label = labels.stream().filter(item -> item.text().getString().equals(text(property).getString()))
                .findFirst().orElse(null);
        if (label != null && inspectorMaxScroll > 0) {
            inspectorScroll = (int) Math.clamp(label.y() - 86L, 0, inspectorMaxScroll);
            inspectorPosition = inspectorTarget = inspectorScroll;
            init();
        }
    }
    private Component graphProblem() {
        return Component.translatable("screen.xero_quest_journal.graph.problem", graphIssue.nodeId(),
                text(graphIssue.property()), graphIssue.detail());
    }
    private double sx(TriggerGraph.Node node) { return 8 + panX + node.x() * zoom; }
    private double sy(TriggerGraph.Node node) { return 56 + panY + node.y() * zoom; }
    private double portY(TriggerGraph.Node node, String port) {
        return sy(node) + (port.equals("false") ? 40 : port.equals("true") ? 24 : 32) * zoom;
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        measuredNodes.clear();
        syncPositionFields();
        advanceInspector();
        backdrop(g, TriggerNodeStyle.PANEL);
        renderChrome(g);
        if (!sourceMode) {
            int canvasBottom = height - 28;
            boolean popup = hasChoicePopup() || variablesOpen || contextOpen();
            g.enableScissor(8, 56, canvasRight(), canvasBottom);
            TriggerNodeStyle.canvas(g, 8, 56, canvasRight(), canvasBottom, 8 + panX, 56 + panY, zoom, showGrid);
            renderWatermark(g, canvasBottom);
            long now = System.nanoTime();
            boolean reduced = ClientPreferences.get().reducedMotion;
            boolean busy = popup || dragging || marquee || panning || resizeOrigin != null || minimapDragging;
            boolean inCanvas = mouseX >= 8 && mouseX <= canvasRight() && mouseY >= 56 && mouseY <= canvasBottom;
            TriggerGraph.Node hoveredNode = busy || !inCanvas ? null : nodeAt(mouseX, mouseY);
            TriggerGraph.Edge hoveredEdge = busy || !inCanvas || hoveredNode != null || !wireFrom.isEmpty()
                    ? null : edgeAt(mouseX, mouseY);
            var matches = searchMatches();
            Set<String> matchIds = new HashSet<>();
            matches.forEach(node -> matchIds.add(node.id()));
            boolean searching = searchOpen && !searchQuery.isBlank();
            Map<String, TriggerGraph.Node> nodesById = new HashMap<>();
            for (var node : nodes) nodesById.putIfAbsent(node.id(), node);
            Set<String> connected = new HashSet<>();
            for (var edge : edges) {
                connected.add(edge.from() + "/" + edge.port());
                connected.add(edge.to() + "/in");
            }
            Set<String> snapTargets = new HashSet<>();
            if (dragging && selectedIds.size() == 1) {
                var source = nodesById.get(selectedIds.iterator().next());
                if (source != null && List.of("variable", "context").contains(source.type()))
                    for (var candidate : nodes)
                        if (acceptsVariable(candidate, source)) snapTargets.add(candidate.id());
            }
            for (var edge : edges) {
                var from = nodesById.get(edge.from());
                var to = nodesById.get(edge.to());
                if (from == null || to == null) continue;
                double x1 = sx(from) + nodeWidth(from) * zoom, y1 = portY(from, edge.port());
                double x2 = sx(to), y2 = sy(to) + 32 * zoom;
                boolean chosen = selectedEdge != null && selectedEdge.equals(edge);
                boolean lit = chosen || edge.equals(hoveredEdge) || hoveredNode != null
                        && (edge.from().equals(hoveredNode.id()) || edge.to().equals(hoveredNode.id()));
                int color = chosen ? TriggerNodeStyle.GOLD : TriggerNodeStyle.portColor(edge.port());
                if (lit) TriggerNodeStyle.halo(g, x1, y1, x2, y2, color, 3, 8, 56, canvasRight(), canvasBottom);
                TriggerNodeStyle.wire(g, x1, y1, x2, y2, lit && !chosen ? TriggerNodeStyle.brighten(color) : color,
                        lit ? 3 : 2, false, 8, 56, canvasRight(), canvasBottom);
                if (!reduced && (lit || selectedIds.contains(from.id()) || selectedIds.contains(to.id())))
                    TriggerNodeStyle.flow(g, x1, y1, x2, y2, color, now);
            }
            spawned.values().removeIf(started -> now - started > SPAWN_NANOS);
            Component portTip = null;
            for (var node : nodes) {
                int x = (int) sx(node), y = (int) sy(node), w = (int) (nodeWidth(node) * zoom), h = (int) (nodeHeight(node) * zoom);
                if (x > canvasRight() + 8 || y > canvasBottom + 8 || x + w < 0 || y + h < 48) continue;
                boolean snapTarget = snapTargets.contains(node.id())
                        && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
                boolean hovered = hoveredNode != null && hoveredNode.id().equals(node.id());
                boolean error = (graphIssue != null && graphIssue.nodeId().equals(node.id()))
                        || invalidLinkTarget.equals(node.id());
                var layout = nodeLayout(node);
                Long born = spawned.get(node.id());
                if (born != null && !reduced)
                    TriggerNodeStyle.spawnRing(g, node, x, y, w, h, (now - born) / (float) SPAWN_NANOS);
                TriggerNodeStyle.frame(g, node, x, y, w, h, (int) Math.round(layout.headerHeight() * zoom),
                        selectedIds.contains(node.id()), hovered, error, snapTarget);
                g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale((float) zoom, (float) zoom, 1);
                TriggerNodeStyle.glyph(g, node, 6, (layout.headerHeight() - TriggerNodeLayout.GLYPH_SIZE) / 2,
                        TriggerNodeLayout.GLYPH_SIZE);
                layout.render(g, font, (int) Math.floor((56 - y) / zoom),
                        (int) Math.ceil((canvasBottom - y) / zoom));
                g.pose().popPose();
                if (searching) {
                    if (matchIds.contains(node.id())) TriggerNodeStyle.ornaments(g, x, y, w, h, 0xFFFFE08A);
                    else TriggerNodeStyle.dim(g, x, y, w, h);
                }
                if (selectedIds.contains(node.id())) renderResizeHandles(g, node, mouseX, mouseY);
                if (!List.of("event", "context").contains(node.type())) {
                    double py = sy(node) + 32 * zoom;
                    if (!wireFrom.isEmpty() && !node.id().equals(wireFrom))
                        TriggerNodeStyle.portHalo(g, x, py, now, reduced);
                    TriggerNodeStyle.port(g, x, py, TriggerNodeStyle.PORT_INPUT, connected.contains(node.id() + "/in"),
                            !wireFrom.isEmpty() && Math.abs(mouseX - x) < 8 && Math.abs(mouseY - py) < 8);
                }
                for (String output : outputPorts(node)) {
                    double py = portY(node, output);
                    boolean portHovered = !popup && Math.abs(mouseX - x - w) < 8 && Math.abs(mouseY - py) < 8;
                    TriggerNodeStyle.port(g, x + w, py, TriggerNodeStyle.portColor(output),
                            connected.contains(node.id() + "/" + output), portHovered);
                    if (portHovered && TriggerGraph.branching(node.type())) portTip = text(output);
                }
                // Branch outputs are labelled while the node is in focus.
                if ((hovered || selectedIds.contains(node.id())) && TriggerGraph.branching(node.type())) {
                    for (String output : outputPorts(node)) {
                        String label = text("port." + output).getString();
                        int labelX = x + w + 9, labelY = (int) Math.round(portY(node, output)) - 4;
                        ControlAppearance.draw(g, labelX - 3, labelY - 2, font.width(label) + 6, font.lineHeight + 3,
                                "rounded", 3, 0xE61B1B1F, 0, 0);
                        g.drawString(font, label, labelX, labelY, TriggerNodeStyle.portColor(output), false);
                    }
                }
            }
            if (!wireFrom.isEmpty()) {
                var from = nodesById.get(wireFrom);
                if (from != null) TriggerNodeStyle.wire(g, sx(from) + nodeWidth(from) * zoom, portY(from, wirePort),
                        mouseX, mouseY, TriggerNodeStyle.GOLD, 2, true, 8, 56, canvasRight(), canvasBottom);
            }
            if (dragging) {
                for (int guide : guideXs)
                    TriggerNodeStyle.guide(g, (int) Math.round(8 + panX + guide * zoom), 56, canvasBottom, true);
                for (int guide : guideYs)
                    TriggerNodeStyle.guide(g, (int) Math.round(56 + panY + guide * zoom), 8, canvasRight(), false);
            }
            if (marquee) {
                int left = (int) Math.min(marqueeX, marqueeEndX), top = (int) Math.min(marqueeY, marqueeEndY);
                int right = (int) Math.max(marqueeX, marqueeEndX), bottom = (int) Math.max(marqueeY, marqueeEndY);
                g.fill(left, top, right, bottom, 0x22FFD27A);
                g.renderOutline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), 0xB0FFD27A);
            }
            renderMinimap(g);
            if (searchBox != null) {
                String count = searchQuery.isBlank() ? ""
                        : matches.isEmpty() ? text("search.none").getString()
                        : searchIndex < 0 ? Component.translatable("screen.xero_quest_journal.graph.search.found",
                                matches.size()).getString()
                        : (searchIndex + 1) + "/" + matches.size();
                int panelRight = searchBox.getX() + searchBox.getWidth() + 6 + (count.isEmpty() ? 0 : font.width(count) + 6);
                g.fill(searchBox.getX() - 3, searchBox.getY() - 1, panelRight + 3, searchBox.getY() + searchBox.getHeight() + 7,
                        0x40000000);
                ControlAppearance.draw(g, searchBox.getX() - 6, searchBox.getY() - 5, panelRight - searchBox.getX() + 6,
                        searchBox.getHeight() + 10, "rounded", 6, TriggerNodeStyle.PANEL_RAISED,
                        TriggerNodeStyle.PANEL_BORDER, 1);
                if (!count.isEmpty()) g.drawString(font, count, searchBox.getX() + searchBox.getWidth() + 6,
                        searchBox.getY() + (searchBox.getHeight() - font.lineHeight) / 2 + 1,
                        matches.isEmpty() ? 0xFFFF9B86 : TriggerNodeStyle.GOLD, false);
            }
            if (helpOpen) renderShortcutHelp(g);
            String zoomLabel = Math.round(zoom * 100) + "%";
            ControlAppearance.draw(g, 10, canvasBottom - 18, font.width(zoomLabel) + 12, 15, "rounded", 5,
                    TriggerNodeStyle.PANEL_RAISED, TriggerNodeStyle.PANEL_BORDER, 1);
            g.drawString(font, zoomLabel, 16, canvasBottom - 14, 0xFFE8E8EC, false);
            g.disableScissor();
            if (portTip != null) g.renderTooltip(font, portTip, mouseX, mouseY);
            if (message.isEmpty()) {
                String status = Component.translatable("screen.xero_quest_journal.graph.status",
                        nodes.size(), edges.size()).getString();
                g.drawString(font, font.plainSubstrByWidth(status, Math.max(1, canvasRight() - 16)),
                        10, height - 20, 0xFF8B8B95, false);
            }
            int panelLeft = canvasRight() + 8;
            g.fill(panelLeft + 1, 52, width, height, 0xFF222226);
            g.fill(panelLeft, 52, panelLeft + 1, height, TriggerNodeStyle.PANEL_BORDER);
            var current = selected();
            if (current != null) {
                var kind = TriggerNodeStyle.kind(current);
                g.fillGradient(panelLeft + 1, 52, width, 55, kind.top, kind.bottom);
            }
            g.enableScissor(canvasRight() + 8, 56, width - 8, height - 42);
            labels.forEach(label -> {
                boolean invalid = graphIssue != null && graphIssue.nodeId().equals(selected)
                        && label.text().getString().equals(text(graphIssue.property()).getString());
                g.drawString(font, font.plainSubstrByWidth(label.text.getString(), side() - 24),
                        label.x, label.y, invalid ? 0xFFFF6666 : 0xFFB9BDC7, false);
            });
            inspectorHints.forEach(hint -> renderInspectorHint(g, hint));
            g.disableScissor();
            if (inspectorMaxScroll > 0) {
                g.fill(width - 7, 56, width - 3, height - 42, 0xFF303037);
                int thumbY = (int) inspectorThumbY();
                g.fill(width - 7, thumbY, width - 3, thumbY + inspectorThumbHeight(), 0xFF8A8A94);
            }
        }
        var variableControls = children().stream().filter(child -> child instanceof StyledButton b
                && b.styleId().startsWith("graph-variable.")).map(child -> (StyledButton) child).toList();
        variableControls.forEach(control -> control.visible = false);
        super.renderContents(g, mouseX, mouseY, delta);
        variableControls.forEach(control -> control.visible = true);
        if (!sourceMode && graphIssue != null && graphIssue.nodeId().equals(selected)) {
            g.enableScissor(canvasRight() + 8, 56, width - 8, height - 42);
            labels.stream().filter(label -> label.text().getString().equals(text(graphIssue.property()).getString()))
                    .forEach(label -> g.renderOutline(label.x() - 1, label.y() + 11,
                            side() - 22, 20, 0xFFFF6666));
            if (graphIssue.property().equals("mail.attachments"))
                inspectorWidgets.stream().filter(widget -> widget instanceof StyledButton b
                        && b.styleId().equals("graph-mail.attachments"))
                        .forEach(widget -> g.renderOutline(widget.getX() - 1, widget.getY() - 1,
                                widget.getWidth() + 2, widget.getHeight() + 2, 0xFFFF6666));
            g.disableScissor();
        }
        renderAnimatedMenu(g, "variables", variablesOpen ? "variables" : null,
                viewport(Math.min(120, width - 144) - 2, 50,
                        Math.min(120, width - 144) + 138, 140), () -> {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 1000);
            int x = Math.min(120, width - 144);
            g.fill(x, 53, x + 140, 143, 0x50000000);
            ControlAppearance.draw(g, x - 2, 50, 140, 90, "rounded", 5, TriggerNodeStyle.PANEL_RAISED,
                    TriggerNodeStyle.PANEL_BORDER, 1);
            for (var child : children()) if (child instanceof StyledButton control
                    && control.styleId().startsWith("graph-variable."))
                control.render(g, mouseX, mouseY, delta);
            g.flush();
            g.pose().popPose();
        });
        renderAnimatedMenu(g, "context", contextEdge != null ? contextEdge
                        : contextCanvas ? "canvas" : contextNode.isBlank() ? null : contextNode,
                viewport(contextX, contextY, contextX + contextWidth(), contextY + contextHeight()),
                () -> renderContext(g, mouseX, mouseY));
        if (!message.isEmpty()) {
            Component problem = !sourceMode && graphIssue != null ? graphProblem()
                    : sourceMode && sourceDiagnostic != null
                        ? Component.translatable("screen.xero_quest_journal.source.diagnostic",
                        sourceDiagnostic.line(), sourceDiagnostic.column(), sourceDiagnostic.reason())
                        : message.startsWith("duplicate:") ? text("duplicate").copy().append(message.substring(10))
                        : text(message);
            g.fill(0, height - 44, width, height, TriggerNodeStyle.PANEL);
            g.fill(0, height - 44, width, height - 43, 0xFF6D4545);
            var lines = font.split(problem, Math.max(1, width - 16));
            for (int line = 0; line < Math.min(2, lines.size()); line++)
                g.drawString(font, lines.get(line), 8, height - 36 + line * 12, 0xFFFF7777, false);
        }
    }
    private void renderInspectorHint(GuiGraphics g, InspectorHint hint) {
        int height = 54;
        int background = hint.valid() ? 0xFF26362F : 0xFF3B2C30;
        int border = hint.valid() ? 0xFF527F66 : 0xFF8D5658;
        g.fill(hint.x(), hint.y(), hint.x() + hint.width(), hint.y() + height, background);
        g.renderOutline(hint.x(), hint.y(), hint.width(), height, border);
        g.drawString(font, font.plainSubstrByWidth(hint.title().getString(), hint.width() - 12),
                hint.x() + 6, hint.y() + 5, hint.valid() ? 0xFF9ED8B1 : 0xFFFFA29A, false);
        int lineY = hint.y() + 18;
        for (var line : font.split(hint.detail(), Math.max(1, hint.width() - 12))) {
            if (lineY + font.lineHeight > hint.y() + height - 3) break;
            g.drawString(font, line, hint.x() + 6, lineY, 0xFFD7DEE5, false);
            lineY += font.lineHeight + 1;
        }
    }
    private static final int CONTEXT_ROW = 20;

    /**
     * Context entries: edge menus only delete; node menus add alignment for multi-selection; the
     * empty-canvas menu places, pastes and organises nodes.
     */
    private List<String> contextActions() {
        if (contextEdge != null) return List.of("delete_edge");
        if (contextCanvas) {
            var actions = new ArrayList<>(List.of("add_here"));
            if (!copiedNodes.isEmpty()) actions.add("paste");
            actions.addAll(List.of("select_all", "arrange", "fit"));
            return actions;
        }
        var actions = new ArrayList<>(List.of("copy", "copy_json"));
        if (selectedIds.size() > 1) actions.addAll(List.of("align_left", "align_top", "distribute_h", "distribute_v"));
        actions.add("focus");
        if (edges.stream().anyMatch(edge -> selectedIds.contains(edge.from()) || selectedIds.contains(edge.to())))
            actions.add("disconnect");
        actions.add("delete");
        return actions;
    }

    /** Removes every wire attached to the selected node(s) as one undo step. */
    private void disconnectSelected() {
        if (edges.stream().noneMatch(edge -> selectedIds.contains(edge.from()) || selectedIds.contains(edge.to())))
            return;
        remember();
        edges.removeIf(edge -> selectedIds.contains(edge.from()) || selectedIds.contains(edge.to()));
        selectedEdge = null;
        graphIssue = null;
        invalidLinkTarget = "";
        message = "";
        init();
    }
    private void runContextAction(String action) {
        switch (action) {
            case "copy" -> { copySelected(); pasteSelected(); }
            case "copy_json" -> copySelectedJson();
            case "align_left", "align_top", "distribute_h", "distribute_v" -> alignSelected(action);
            case "focus" -> focusSelection();
            case "disconnect" -> disconnectSelected();
            case "add_here" -> openNodeLibraryAt(canvasMenuX, canvasMenuY, "", "");
            case "paste" -> pasteAt(canvasMenuX, canvasMenuY);
            case "select_all" -> selectAll();
            case "arrange" -> arrangeGraph();
            case "fit" -> fitGraph();
            case "delete", "delete_edge" -> deleteSelected();
            default -> { }
        }
    }
    private void renderContext(GuiGraphics g, int mouseX, int mouseY) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 1000);
        int h = contextHeight(), w = contextWidth();
        g.fill(contextX + 2, contextY + 3, contextX + w + 2, contextY + h + 3, 0x50000000);
        ControlAppearance.draw(g, contextX, contextY, w, h, "rounded", 5, TriggerNodeStyle.PANEL_RAISED,
                TriggerNodeStyle.PANEL_BORDER, 1);
        var actions = contextActions();
        for (int i = 0; i < actions.size(); i++) {
            String action = actions.get(i);
            int rowY = contextY + 1 + i * CONTEXT_ROW;
            boolean destructive = destructiveAction(action);
            boolean hovered = mouseX >= contextX && mouseX < contextX + w && mouseY >= rowY && mouseY < rowY + CONTEXT_ROW;
            if (hovered) ControlAppearance.draw(g, contextX + 3, rowY + 1, w - 6, CONTEXT_ROW - 2, "rounded", 4,
                    destructive ? 0x40FF8A73 : 0x26FFFFFF, 0, 0);
            // One divider before each group: alignment, canvas tools and the destructive tail.
            boolean groupStart = destructive ? !destructiveAction(actions.get(i - (i > 0 ? 1 : 0)))
                    : action.equals("align_left") || action.equals("select_all");
            if (i > 0 && groupStart)
                g.fill(contextX + 8, rowY, contextX + w - 8, rowY + 1, 0x30FFFFFF);
            MailDecor.diamond(g, contextX + 10, rowY + CONTEXT_ROW / 2, 2,
                    destructive ? 0xFFFF9B86 : TriggerNodeStyle.GOLD);
            g.drawString(font, text(action), contextX + 18, rowY + (CONTEXT_ROW - font.lineHeight) / 2 + 1,
                    destructive ? 0xFFFFC7B5 : 0xFFE8E8EC, false);
        }
        g.flush();
        g.pose().popPose();
    }

    /** Title tab and dark tool plates along the top, echoing the reference editor's chrome. */
    private void renderChrome(GuiGraphics g) {
        g.fill(0, 0, width, 51, 0xFF1A1A1E);
        g.fill(0, 51, width, 52, TriggerNodeStyle.PANEL_BORDER);
        var current = selected();
        int accent = current == null ? TriggerNodeStyle.GOLD : TriggerNodeStyle.kind(current).top;
        int tabWidth = Math.max(40, Math.min(width - 104, font.width(title) + 34));
        ControlAppearance.draw(g, 4, 2, tabWidth, 16, "rounded", 5, 0xFF2B2B30, 0, 0);
        g.fill(9, 2, 4 + tabWidth - 5, 3, accent);
        MailDecor.diamond(g, 13, 10, 3, accent);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), tabWidth - 28), 21, 6, 0xFFE8E8EC, false);
        if (sourceMode) {
            TriggerNodeStyle.toolbar(g, 4, 49, 148, 79);
        } else {
            int toolsRight = 200;
            if (204 + 24 <= width - 96) toolsRight = 228;
            if (232 + 24 <= width - 96) toolsRight = 256;
            TriggerNodeStyle.toolbar(g, 4, 19, toolsRight + 4, 49);
        }
        TriggerNodeStyle.toolbar(g, width - 96, 19, width - 8, 49);
    }

    /** Large faded editor name in the canvas corner, as on the reference editor. */
    private void renderWatermark(GuiGraphics g, int canvasBottom) {
        String mark = title.getString();
        float scale = 3;
        int markWidth = Math.round(font.width(mark) * scale);
        int[] map = minimapBounds();
        int right = (map == null ? canvasRight() : map[0]) - 14;
        if (right - markWidth < 24 || canvasBottom - 56 < 80) return;
        g.pose().pushPose();
        g.pose().translate(right - markWidth, canvasBottom - 12 - font.lineHeight * scale, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, mark, 0, 0, 0x12FFFFFF, false);
        g.pose().popPose();
    }
    private static boolean destructiveAction(String action) {
        return action.startsWith("delete") || action.equals("disconnect");
    }
    private int contextHeight() { return contextActions().size() * CONTEXT_ROW + 2; }
    private int contextWidth() {
        int widest = 0;
        for (String action : contextActions()) widest = Math.max(widest, font.width(text(action)));
        return Math.max(90, widest + 26);
    }
    private void copySelected() {
        copiedNodes = nodes.stream().filter(node -> selectedIds.contains(node.id())).toList();
        copiedEdges = edges.stream().filter(edge ->
                selectedIds.contains(edge.from()) && selectedIds.contains(edge.to())).toList();
    }
    private void copySelectedJson() {
        copySelected();
        if (!copiedNodes.isEmpty())
            minecraft.keyboardHandler.setClipboard(GSON.toJson(new GraphDraft(copiedNodes, copiedEdges)));
    }
    private void pasteSelected() { pasteSelected(24, 24); }
    /** Pastes the copied group so that its top-left corner lands on the given (snapped) world point. */
    private void pasteAt(double worldX, double worldY) {
        if (copiedNodes.isEmpty()) return;
        int left = copiedNodes.stream().mapToInt(TriggerGraph.Node::x).min().orElse(0);
        int top = copiedNodes.stream().mapToInt(TriggerGraph.Node::y).min().orElse(0);
        pasteSelected((int) Math.round(worldX / 24.0) * 24 - left, (int) Math.round(worldY / 24.0) * 24 - top);
    }
    private void pasteSelected(int offsetX, int offsetY) {
        if (copiedNodes.isEmpty() || nodes.size() + copiedNodes.size() > 128
                || edges.size() + copiedEdges.size() > 256) return;
        Map<String, String> ids = new LinkedHashMap<>();
        for (var node : copiedNodes) {
            String candidate = node.id() + "_copy";
            int suffix = 2;
            while (contains(candidate) || ids.containsValue(candidate)) candidate = node.id() + "_copy" + suffix++;
            if (candidate.length() > 64) { message = "invalid"; return; }
            ids.put(node.id(), candidate);
        }
        var next = new ArrayList<>(nodes);
        copiedNodes.forEach(node -> next.add(new TriggerGraph.Node(ids.get(node.id()), node.type(),
                (int) Math.clamp((long) node.x() + offsetX, -100000, 100000),
                (int) Math.clamp((long) node.y() + offsetY, -100000, 100000), node.data())));
        var links = new ArrayList<>(edges);
        copiedEdges.forEach(edge -> links.add(new TriggerGraph.Edge(
                ids.get(edge.from()), ids.get(edge.to()), edge.port())));
        try {
            new TriggerGraph(next, links);
            remember();
            nodes.clear(); nodes.addAll(next);
            edges.clear(); edges.addAll(links);
            selectedIds.clear(); selectedIds.addAll(ids.values());
            selected = ids.values().iterator().next();
            long now = System.nanoTime();
            ids.values().forEach(id -> spawned.put(id, now));
            init();
        } catch (RuntimeException invalid) { message = "invalid"; }
    }
    /** Hit test against the drawn Bezier wires (a little wider than the 2px stroke). */
    private TriggerGraph.Edge edgeAt(double x, double y) {
        if (x < 8 || x > canvasRight() || y < 56 || y > height - 28) return null;
        for (int i = edges.size() - 1; i >= 0; i--) {
            var edge = edges.get(i);
            var from = nodes.stream().filter(n -> n.id().equals(edge.from())).findFirst().orElse(null);
            var to = nodes.stream().filter(n -> n.id().equals(edge.to())).findFirst().orElse(null);
            if (from == null || to == null) continue;
            double ax = sx(from) + nodeWidth(from) * zoom, ay = portY(from, edge.port());
            double bx = sx(to), by = sy(to) + 32 * zoom;
            if (TriggerNodeStyle.distance(ax, ay, bx, by, x, y) <= 6) return edge;
        }
        return null;
    }
    private int resizeAt(TriggerGraph.Node node, double x, double y) {
        double left = sx(node), top = sy(node);
        double right = left + nodeWidth(node) * zoom, bottom = top + nodeHeight(node) * zoom;
        if (x < left - 5 || x > right + 5 || y < top - 5 || y > bottom + 5 || inputAt(node, x, y)) return 0;
        for (String port : outputPorts(node))
            if (Math.abs(x - right) < 8 && Math.abs(y - portY(node, port)) < 8) return 0;
        int edges = Math.abs(x - left) <= 5 ? RESIZE_LEFT : Math.abs(x - right) <= 5 ? RESIZE_RIGHT : 0;
        if (Math.abs(y - top) <= 5) edges |= RESIZE_TOP;
        else if (Math.abs(y - bottom) <= 5) edges |= RESIZE_BOTTOM;
        return edges;
    }
    private void renderResizeHandles(GuiGraphics g, TriggerGraph.Node node, double mouseX, double mouseY) {
        double x = sx(node), y = sy(node), w = nodeWidth(node) * zoom, h = nodeHeight(node) * zoom;
        int active = resizeOrigin != null && resizeOrigin.id().equals(node.id()) ? resizeEdges
                : hasChoicePopup() || variablesOpen || contextOpen() ? 0
                : resizeAt(node, mouseX, mouseY);
        if ((active & RESIZE_LEFT) != 0) g.fill((int) x, (int) y, (int) x + 2, (int) (y + h), -1);
        if ((active & RESIZE_RIGHT) != 0) g.fill((int) (x + w) - 2, (int) y, (int) (x + w), (int) (y + h), -1);
        if ((active & RESIZE_TOP) != 0) g.fill((int) x, (int) y, (int) (x + w), (int) y + 2, -1);
        if ((active & RESIZE_BOTTOM) != 0) g.fill((int) x, (int) (y + h) - 2, (int) (x + w), (int) (y + h), -1);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            if (column == 1 && row == 1) continue;
            double hx = x + column * w / 2, hy = y + row * h / 2;
            if (resizeAt(node, hx, hy) == 0) continue;
            int sides = (column == 0 ? RESIZE_LEFT : column == 2 ? RESIZE_RIGHT : 0)
                    | (row == 0 ? RESIZE_TOP : row == 2 ? RESIZE_BOTTOM : 0);
            g.fill((int) hx - 3, (int) hy - 3, (int) hx + 4, (int) hy + 4,
                    active != 0 && (active & sides) == sides ? 0xFFFFFFFF : TriggerNodeStyle.GOLD);
            g.fill((int) hx - 1, (int) hy - 1, (int) hx + 2, (int) hy + 2, 0xFF1E1E22);
        }
    }
    private void resizeNode(double x, double y) {
        int dx = (int) Math.round((x - resizeX) / zoom), dy = (int) Math.round((y - resizeY) / zoom);
        var data = new LinkedHashMap<>(resizeOrigin.data());
        if ((resizeEdges & (RESIZE_LEFT | RESIZE_RIGHT)) != 0 && dx != 0)
            data.put("layoutWidth", Integer.toString(Math.clamp(
                    resizeWidth + ((resizeEdges & RESIZE_LEFT) != 0 ? -dx : dx),
                    TriggerNodeLayout.MIN_WIDTH, TriggerGraph.MAX_NODE_SIZE)));
        if ((resizeEdges & (RESIZE_TOP | RESIZE_BOTTOM)) != 0 && dy != 0)
            data.put("layoutHeight", Integer.toString(Math.clamp(
                    resizeHeight + ((resizeEdges & RESIZE_TOP) != 0 ? -dy : dy),
                    TriggerNodeLayout.MIN_HEIGHT, TriggerGraph.MAX_NODE_SIZE)));
        try {
            var candidate = new TriggerGraph.Node(resizeOrigin.id(), resizeOrigin.type(), resizeOrigin.x(), resizeOrigin.y(), data);
            // Opposite borders stay fixed, including when wrapped text imposes a minimum height.
            int nx = resizeOrigin.x() + ((resizeEdges & RESIZE_LEFT) != 0 ? resizeWidth - nodeWidth(candidate) : 0);
            int ny = resizeOrigin.y() + ((resizeEdges & RESIZE_TOP) != 0 ? resizeHeight - nodeHeight(candidate) : 0);
            candidate = new TriggerGraph.Node(candidate.id(), candidate.type(), nx, ny, data);
            var current = selected();
            if (current == null || candidate.equals(current)) return;
            if (!resizeRemembered) {
                remember();
                resizeRemembered = true;
            }
            nodes.set(nodes.indexOf(current), candidate);
            syncPositionFields();
            message = "";
        } catch (IllegalArgumentException invalid) { message = "invalid"; }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && graphIssue != null && !sourceMode && y >= height - 44 && y < height
                && x >= 8 && x < width - 8) {
            minecraft.keyboardHandler.setClipboard(graphProblem().getString());
            return true;
        }
        if (clickChoicePopup(x, y, button)) return true;
        if (variablesOpen) {
            if (x < Math.min(120, width - 144) || x >= Math.min(120, width - 144) + 136
                    || y < 52 || y >= 138) {
                variablesOpen = false;
                init();
                return true;
            }
            for (var child : List.copyOf(children())) if (child instanceof StyledButton control
                    && control.styleId().startsWith("graph-variable.") && control.mouseClicked(x, y, button))
                return true;
            return true;
        }
        if (contextOpen()) {
            if (button == 0 && x >= contextX && x < contextX + contextWidth()
                    && y >= contextY && y < contextY + contextHeight()) {
                var actions = contextActions();
                int index = Math.max(0, Math.min(actions.size() - 1, (int) ((y - contextY - 1) / CONTEXT_ROW)));
                if (contextEdge != null) {
                    // Single-entry connection menu acts on press (the release used to drop the action).
                    runContextAction(actions.get(index));
                    closeContext();
                    return true;
                }
                pressedContextAction = index;
                return true;
            }
            closeContext();
            return true;
        }
        if (!sourceMode && button == 0 && x >= width - 9 && y >= 56 && y < height - 42
                && inspectorMaxScroll > 0) {
            inspectorDragging = inspectorThumbDragging = true;
            inspectorThumbOffset = y >= inspectorThumbY() && y < inspectorThumbY() + inspectorThumbHeight()
                    ? y - inspectorThumbY() : inspectorThumbHeight() / 2.;
            dragInspectorThumb(y);
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (!sourceMode && button == 0 && x > canvasRight() && y >= 56 && y < height - 42) {
            inspectorDragging = true;
            inspectorThumbDragging = false;
            inspectorDragY = y;
            inspectorDragStart = inspectorPosition;
            return true;
        }
        if (sourceMode || x < 8 || x > canvasRight() || y < 56 || y > height - 28) return false;
        setFocused(null);
        int[] help = helpOpen ? helpBounds() : null;
        if (help != null && x >= help[0] && x < help[0] + help[2] && y >= help[1] && y < help[1] + help[3]) {
            helpOpen = false;
            return true;
        }
        int[] minimap = minimapBounds();
        if (button == 0 && minimap != null && x >= minimap[0] && x < minimap[2] && y >= minimap[1] && y < minimap[3]) {
            minimapDragging = true;
            jumpMinimap(x, y);
            return true;
        }
        String zoomLabel = Math.round(zoom * 100) + "%";
        if (button == 0 && x >= 10 && x < 22 + font.width(zoomLabel) && y >= height - 46 && y < height - 31) {
            resetZoom();
            return true;
        }
        if (button == 1) {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                var node = nodes.get(i);
                if (x >= sx(node) && x < sx(node) + nodeWidth(node) * zoom
                        && y >= sy(node) && y < sy(node) + nodeHeight(node) * zoom) {
                    var option = nodeOptionAt(node, x, y);
                    if (option != null && !hasControlDown() && !hasShiftDown()) {
                        selectedIds.clear();
                        selectedIds.add(node.id());
                        selected = node.id();
                        selectedEdge = null;
                        init();
                        openNodeFieldPicker(node, option);
                        return true;
                    }
                    if (!selectedIds.contains(node.id())) {
                        selectedIds.clear(); selectedIds.add(node.id());
                        selected = node.id(); selectedEdge = null; init();
                    }
                    closeContext();
                    contextNode = node.id();
                    contextX = Math.max(8, Math.min((int) x, canvasRight() - contextWidth() - 2));
                    contextY = Math.max(56, Math.min((int) y, height - contextHeight() - 30));
                    return true;
                }
            }
            var edge = edgeAt(x, y);
            if (edge != null) {
                selectedEdge = contextEdge = edge;
                selected = ""; selectedIds.clear();
                contextX = Math.max(8, Math.min((int) x, canvasRight() - contextWidth() - 2));
                contextY = Math.min((int) y, height - 52);
                init();
                return true;
            }
        }
        if (button == 1 || button == 2) {
            // Right click without movement opens the canvas menu on release; dragging pans.
            wireFrom = "";
            panning = true;
            rightPanMoved = button != 1;
            rightPressX = x;
            rightPressY = y;
            return true;
        }
        if (button != 0) return false;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            var node = nodes.get(i);
            if (inputAt(node, x, y) && !wireFrom.isEmpty()) { connectTo(node); return true; }
            if (inputAt(node, x, y)) {
                // Dragging off a connected input picks its wire up again so it can be re-routed.
                TriggerGraph.Edge incoming = null;
                for (var edge : edges) if (edge.to().equals(node.id())) incoming = edge;
                if (incoming != null) {
                    remember();
                    detachedIndex = edges.indexOf(incoming);
                    edges.remove(incoming);
                    detachedEdge = incoming;
                    selectedEdge = null;
                    wireFrom = incoming.from(); wirePort = incoming.port(); wiring = true; wireMoved = false;
                    return true;
                }
            }
            for (String port : outputPorts(node)) {
                if (Math.abs(x - sx(node) - nodeWidth(node) * zoom) < 8 && Math.abs(y - portY(node, port)) < 8) {
                    wireFrom = node.id(); wirePort = port; wiring = true; wireMoved = false; detachedEdge = null;
                    return true;
                }
            }
            int sides = resizeAt(node, x, y);
            if (sides != 0 && wireFrom.isEmpty() && !hasControlDown() && !hasShiftDown()) {
                selected = node.id();
                selectedIds.clear();
                selectedIds.add(selected);
                selectedEdge = null;
                resizeOrigin = node;
                resizeEdges = sides;
                resizeWidth = nodeWidth(node);
                resizeHeight = nodeHeight(node);
                resizeX = x;
                resizeY = y;
                resizeRemembered = false;
                dragging = panning = false;
                init();
                return true;
            }
            if (x >= sx(node) && x < sx(node) + nodeWidth(node) * zoom && y >= sy(node) && y < sy(node) + nodeHeight(node) * zoom) {
                var option = nodeOptionAt(node, x, y);
                if (option != null && wireFrom.isEmpty() && !hasControlDown() && !hasShiftDown()) {
                    selected = node.id();
                    selectedIds.clear(); selectedIds.add(selected);
                    selectedEdge = null;
                    dragging = false;
                    init();
                    pressedNodeOption = option;
                    pressedOptionNode = node.id();
                    optionPressX = x; optionPressY = y;
                    optionPressedAt = System.nanoTime();
                    optionMoved = false;
                    lastValueClick = 0;
                    return true;
                }
                String property = editableAt(node, x, y);
                if (!property.isEmpty() && !hasControlDown() && !hasShiftDown()) {
                    long now = System.nanoTime();
                    boolean twice = node.id().equals(lastValueNode) && property.equals(lastValueProperty)
                            && now - lastValueClick < 350_000_000L
                            && Math.abs(x - lastValueX) < 4 && Math.abs(y - lastValueY) < 4;
                    lastValueClick = twice ? 0 : now;
                    lastValueNode = node.id();
                    lastValueProperty = property;
                    lastValueX = x; lastValueY = y;
                    if (twice) {
                        selected = node.id();
                        selectedIds.clear(); selectedIds.add(selected);
                        dragging = false;
                        init();
                        openNodeValue(node, property);
                        return true;
                    }
                } else lastValueClick = 0;
                selectedEdge = null;
                if (hasControlDown() || hasShiftDown()) {
                    if (!selectedIds.add(node.id())) selectedIds.remove(node.id());
                    selected = selectedIds.isEmpty() ? "" : selectedIds.iterator().next();
                    init();
                    return true;
                }
                if (!selectedIds.contains(node.id())) {
                    selectedIds.clear();
                    selectedIds.add(node.id());
                }
                selected = node.id();
                dragging = true;
                dragRemembered = false;
                grabX = x; grabY = y;
                dragOrigins.clear();
                nodes.stream().filter(n -> selectedIds.contains(n.id())).forEach(n -> dragOrigins.put(n.id(), n));
                init(); return true;
            }
        }
        var edge = edgeAt(x, y);
        if (edge != null) {
            selectedEdge = edge;
            selectedIds.clear(); selected = wireFrom = "";
            init();
            return true;
        }
        long now = System.nanoTime();
        boolean doubleClick = now - lastCanvasClick < 350_000_000L
                && Math.abs(x - lastCanvasClickX) < 4 && Math.abs(y - lastCanvasClickY) < 4;
        lastCanvasClick = doubleClick ? 0 : now;
        lastCanvasClickX = x;
        lastCanvasClickY = y;
        if (doubleClick && wireFrom.isEmpty()) {
            openNodeLibraryAt((x - 8 - panX) / zoom, (y - 56 - panY) / zoom, "", "");
            return true;
        }
        // Empty canvas: left drag draws a selection box (Shift/Ctrl extends); right/middle drag pans.
        marquee = true;
        marqueeX = marqueeEndX = x;
        marqueeY = marqueeEndY = y;
        marqueeBase.clear();
        if (hasShiftDown() || hasControlDown()) marqueeBase.addAll(selectedIds);
        else {
            selected = "";
            selectedIds.clear();
        }
        wireFrom = "";
        selectedEdge = null;
        init();
        return true;
    }
    private boolean inputAt(TriggerGraph.Node node, double x, double y) {
        return !List.of("event", "context").contains(node.type()) && Math.abs(x - sx(node)) < 8
                && Math.abs(y - sy(node) - 32 * zoom) < 8;
    }
    private String editableAt(TriggerGraph.Node node, double x, double y) {
        double localX = (x - sx(node)) / zoom, localY = (y - sy(node)) / zoom;
        for (var block : nodeLayout(node).texts()) {
            if (block.property().isEmpty() || block.property().equals("logic") || block.property().startsWith("operator"))
                continue;
            if (localX >= block.x() - 3 && localX < block.x() + block.width() + 3
                    && localY >= block.y() - 3 && localY < block.y() + block.height(font) + 3)
                return block.property();
        }
        return "";
    }
    private NodeOption nodeOptionAt(TriggerGraph.Node node, double x, double y) {
        double localX = (x - sx(node)) / zoom, localY = (y - sy(node)) / zoom;
        for (var slot : nodeLayout(node).slots()) {
            if (localX < slot.x() || localX >= slot.x() + slot.width()
                    || localY < slot.y() || localY >= slot.y() + slot.height()) continue;
            String value = slot.left() ? node.get("field" + slot.suffix(), "eventType")
                    : !node.get("expectedVar" + slot.suffix(), "").isBlank()
                    ? "var:" + node.get("expectedVar" + slot.suffix(), "")
                    : node.get("expectedField" + slot.suffix(), "");
            return new NodeOption(slot.left() ? "field" : "expected", value, slot.suffix(), slot.left());
        }
        for (var block : nodeLayout(node).texts()) {
            String property = block.property();
            if (!property.equals("logic") && !property.startsWith("operator")
                    && !(node.type().equals("context") && property.equals("field"))) continue;
            if (localX >= block.x() - 3 && localX < block.x() + block.width() + 3
                    && localY >= block.y() - 3 && localY < block.y() + block.height(font) + 3)
                return new NodeOption(property, node.get(property, ""), "", true);
        }
        return null;
    }
    private void openNodeOption(TriggerGraph.Node node, NodeOption option, double x, double y) {
        var choices = new ArrayList<String>();
        String initial = option.value();
        if (option.key().equals("field")) {
            choices.addAll(TriggerGraph.FIELDS);
            if (!node.type().equals("context")) variables().forEach(value -> choices.add("var:" + value.id()));
        } else if (option.key().equals("expected")) {
            choices.add("");
            TriggerGraph.FIELDS.forEach(value -> choices.add("field:" + value));
            variables().forEach(value -> choices.add("var:" + value.id()));
            if (!initial.isBlank() && !initial.startsWith("var:")) initial = "field:" + initial;
        } else choices.addAll(option.key().equals("logic") ? TriggerGraph.LOGICS : TriggerGraph.OPERATORS);
        if (!choices.contains(initial)) initial = choices.getFirst();
        String key = option.key();
        closeOpenNodeChoices();
        inlineChoice = new StyledChoiceButton<>((int) x, (int) y, 140, 20, text(key),
                choices, initial, value -> {
                    if (value.isBlank()) return text("travel.literal");
                    if (value.startsWith("var:")) return Component.literal(value.substring(4));
                    String field = value.startsWith("field:") ? value.substring(6) : value;
                    return text(TriggerGraph.FIELDS.contains(field) ? "field." + field
                            : key.equals("logic") ? "logic." + field : "operator." + field);
                }, value -> {
                    var current = selected();
                    if (current == null || !current.id().equals(node.id())) return;
                    if (key.equals("expected") && value.isEmpty()) {
                        openNodeValue(current, "expected" + option.suffix());
                        return;
                    }
                    var data = new LinkedHashMap<>(current.data());
                    if (key.equals("expected")) {
                        data.remove("expectedVar" + option.suffix());
                        data.remove("expectedField" + option.suffix());
                        data.put((value.startsWith("var:") ? "expectedVar" : "expectedField") + option.suffix(),
                                value.substring(value.indexOf(':') + 1));
                    } else data.put(key + option.suffix(), value);
                    replaceData(current, key, data);
                    init();
                }, styles, "graph-inline-choice").searchable();
        addWidget(inlineChoice);
        setFocused(inlineChoice);
        inlineChoice.onPress();
        inlineChoice.restoreSearchFocus();
    }
    private void closeOpenNodeChoices() {
        children().stream().filter(StyledChoiceButton.class::isInstance)
                .map(StyledChoiceButton.class::cast).forEach(StyledChoiceButton::closeMenu);
    }
    private void openNodeFieldPicker(TriggerGraph.Node node, NodeOption option) {
        var options = new ArrayList<QuestValuePickerScreen.Option>();
        if (option.key().equals("expected")) {
            options.add(new QuestValuePickerScreen.Option("literal:", text("travel.literal").getString()));
            TriggerGraph.FIELDS.forEach(field -> options.add(new QuestValuePickerScreen.Option(
                    "field:" + field, text("field." + field).getString())));
            variables().forEach(variable -> options.add(new QuestValuePickerScreen.Option(
                    "var:" + variable.id(), variable.id())));
        } else {
            TriggerGraph.FIELDS.forEach(field -> options.add(new QuestValuePickerScreen.Option(
                    field, text("field." + field).getString())));
            if (!node.type().equals("context"))
                variables().forEach(variable -> options.add(new QuestValuePickerScreen.Option(
                        "var:" + variable.id(), variable.id())));
        }
        String currentValue = option.value();
        if (option.key().equals("expected") && !currentValue.isBlank()
                && !currentValue.startsWith("var:")) currentValue = "field:" + currentValue;
        minecraft.setScreen(new QuestValuePickerScreen(this, text("field_picker").getString(), "",
                () -> options, currentValue, value -> {
            var activeNode = selected();
            if (activeNode == null || !activeNode.id().equals(node.id())) return;
            if (value.equals("literal:")) {
                minecraft.setScreen(new QuestPropertyValueScreen(this, text("expected"),
                        activeNode.get("expected" + option.suffix(), ""), literal -> {
                    var currentNode = selected();
                    if (currentNode == null || !currentNode.id().equals(node.id())) return;
                    var data = new LinkedHashMap<>(currentNode.data());
                    data.remove("expectedVar" + option.suffix());
                    data.remove("expectedField" + option.suffix());
                    data.put("expected" + option.suffix(), literal);
                    replaceData(currentNode, "expected" + option.suffix(), data);
                    init();
                }));
                return;
            }
            var data = new LinkedHashMap<>(activeNode.data());
            if (option.key().equals("expected")) {
                data.remove("expectedVar" + option.suffix());
                data.remove("expectedField" + option.suffix());
                data.put((value.startsWith("var:") ? "expectedVar" : "expectedField") + option.suffix(),
                        value.startsWith("var:") ? value.substring(4)
                                : value.startsWith("field:") ? value.substring(6) : value);
                data.put("expectedPlayer" + option.suffix(),
                        activeNode.get("expectedPlayer" + option.suffix(), "@s"));
            } else {
                data.put("field" + option.suffix(), value);
                if (TriggerNodeLibrary.PLAYER_VALUES.contains(value)
                        && !List.of("eventType", "eventTarget").contains(value))
                    data.put("fieldPlayer" + option.suffix(), activeNode.get("fieldPlayer" + option.suffix(), "@s"));
            }
            replaceData(activeNode, option.key(), data);
            init();
        }));
    }
    private void extractNodeOption(double x, double y) {
        var option = pressedNodeOption;
        var source = nodes.stream().filter(node -> node.id().equals(pressedOptionNode)).findFirst().orElse(null);
        if (source == null || option == null || option.value().isBlank()
                || !List.of("field", "expected").contains(option.key()) || nodes.size() >= 128) return;
        boolean custom = option.value().startsWith("var:");
        if (!custom && !TriggerGraph.FIELDS.contains(option.value())) return;
        String id;
        do { id = "node_" + ++serial; } while (contains(id));
        var data = new LinkedHashMap<String, String>();
        if (custom) {
            data.put("variable", option.value().substring(4));
            data.put("operator", "eq"); data.put("expected", "");
        } else {
            data.put("field", option.value());
            data.put("player", source.get((option.left() ? "fieldPlayer" : "expectedPlayer") + option.suffix(),
                    source.type().equals("context") ? source.get("player", "@s") : "@s"));
        }
        var node = new TriggerGraph.Node(id, custom ? "variable" : "context",
                (int) Math.clamp((x - 8 - panX) / zoom - 20, -100000, 100000),
                (int) Math.clamp((y - 56 - panY) / zoom - 20, -100000, 100000), data);
        remember();
        nodes.add(node);
        selected = node.id(); selectedIds.clear(); selectedIds.add(selected);
        dragOrigins.clear(); dragOrigins.put(selected, node);
        grabX = x; grabY = y;
        pressedNodeOption = null;
        dragging = dragRemembered = true;
        init();
    }
    private void openNodeValue(TriggerGraph.Node node, String property) {
        Consumer<String> save = value -> {
            if (!selected.equals(node.id())) return;
            if (property.startsWith("expected")) {
                var current = selected();
                var data = new LinkedHashMap<>(current.data());
                String suffix = property.substring("expected".length());
                data.remove("expectedVar" + suffix);
                data.remove("expectedField" + suffix);
                data.put(property, value);
                replaceData(current, property, data);
            } else update(property, value);
            init();
        };
        if (property.equals("script")) {
            if (node.type().equals("kubejs_script") && !QuestNodeLibraryScreen.available(node.type())) return;
            minecraft.setScreen(new QuestScriptEditorScreen(this, nodeTitle(node), node.get(property, ""), save,
                    node.type().equals("kubejs_script")
                            ? xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind.KUBEJS
                            : xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind.GRAPH_PREDICATE));
        } else {
            minecraft.setScreen(new QuestPropertyValueScreen(this,
                    text(property.startsWith("expected") ? "expected" : property), node.get(property, ""), save,
                    property.equals("body") || node.type().equals("mail_set_property") ? 8192 : 4096));
        }
    }
    private void connectTo(TriggerGraph.Node node) {
        try {
            var edge = new TriggerGraph.Edge(wireFrom, node.id(), wirePort);
            var next = new ArrayList<>(edges);
            next.add(edge);
            new TriggerGraph(nodes, next);
            // A re-routed wire already recorded its removal; keep the move as one undo step.
            if (reconnecting) reconnecting = false;
            else remember();
            edges.add(edge);
            message = "";
            graphIssue = null;
            invalidLinkTarget = "";
        } catch (IllegalArgumentException invalid) {
            var start = nodes.stream().filter(candidate -> candidate.id().equals(wireFrom)).findFirst().orElse(null);
            if (start == null) message = "invalid";
            else {
                showGraphIssue(start, "connection", new IllegalArgumentException(
                        text("connection_invalid").getString() + " " + wireFrom + " -> " + node.id()
                                + ": " + connectionReason(new TriggerGraph.Edge(wireFrom, node.id(), wirePort), invalid, true)));
                invalidLinkTarget = node.id();
            }
        }
        wireFrom = "";
        wiring = false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && pressedNodeOption != null) {
            optionMoved |= Math.abs(x - optionPressX) + Math.abs(y - optionPressY) >= 4;
            if (optionMoved && System.nanoTime() - optionPressedAt >= 300_000_000L) extractNodeOption(x, y);
            return true;
        }
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (resizeOrigin != null && button == 0) { resizeNode(x, y); return true; }
        if (inspectorDragging && button == 0) {
            if (inspectorThumbDragging) dragInspectorThumb(y);
            else inspectorPosition = inspectorTarget = Math.clamp(inspectorDragStart + inspectorDragY - y, 0, inspectorMaxScroll);
            return true;
        }
        if (wiring && button == 0) {
            wireMoved = true;
            return true;
        }
        if (minimapDragging && button == 0) {
            jumpMinimap(x, y);
            return true;
        }
        if (marquee && button == 0) {
            marqueeEndX = Math.clamp(x, 8, canvasRight());
            marqueeEndY = Math.clamp(y, 56, height - 28);
            updateMarqueeSelection();
            return true;
        }
        if (dragging && button == 0) {
            int offsetX = (int) Math.round((x - grabX) / zoom);
            int offsetY = (int) Math.round((y - grabY) / zoom);
            if ((offsetX != 0 || offsetY != 0) && !dragRemembered) {
                lastValueClick = 0;
                remember();
                dragRemembered = true;
            }
            if (offsetX == 0 && offsetY == 0 && !dragRemembered) return true;
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                if (selectedIds.contains(node.id())) {
                    var origin = dragOrigins.get(node.id());
                    if (origin != null) nodes.set(i, new TriggerGraph.Node(node.id(), node.type(),
                    (int) Math.clamp(hasShiftDown() ? (long) origin.x() + offsetX
                            : Math.round((origin.x() + offsetX) / 24.0) * 24, -100000, 100000),
                    (int) Math.clamp(hasShiftDown() ? (long) origin.y() + offsetY
                            : Math.round((origin.y() + offsetY) / 24.0) * 24, -100000, 100000), node.data()));
                }
            }
            updateGuides();
            syncPositionFields();
            return true;
        }
        if (panning) {
            if (Math.abs(x - rightPressX) + Math.abs(y - rightPressY) > 3) rightPanMoved = true;
            panX += dx;
            panY += dy;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && pressedNodeOption != null) {
            var option = pressedNodeOption;
            pressedNodeOption = null;
            var node = nodes.stream().filter(value -> value.id().equals(pressedOptionNode)).findFirst().orElse(null);
            if (!optionMoved && node != null) openNodeOption(node, option, x, y);
            return true;
        }
        if (releaseChoicePopup(x, y, button)) return true;
        if (button == 0 && pressedContextAction >= 0) {
            int action = pressedContextAction;
            pressedContextAction = -1;
            var actions = contextActions();
            boolean inside = contextOpen() && x >= contextX && x < contextX + contextWidth()
                    && y >= contextY && y < contextY + contextHeight()
                    && action == (int) ((y - contextY - 1) / CONTEXT_ROW) && action < actions.size();
            // Close first: some actions open another screen or rebuild the widgets.
            closeContext();
            if (inside) runContextAction(actions.get(action));
            return true;
        }
        if (button == 1 && panning && !rightPanMoved) {
            panning = false;
            openCanvasMenu(x, y);
            return true;
        }
        if (button == 0 && minimapDragging) {
            minimapDragging = false;
            return true;
        }
        if (button == 0 && marquee) {
            marquee = false;
            marqueeEndX = Math.clamp(x, 8, canvasRight());
            marqueeEndY = Math.clamp(y, 56, height - 28);
            updateMarqueeSelection();
            init();
            return true;
        }
        if (button == 0 && resizeOrigin != null) {
            resizeNode(x, y);
            resizeOrigin = null;
            return true;
        }
        if (button == 0 && inspectorDragging) { inspectorDragging = inspectorThumbDragging = false; return true; }
        if (button == 0 && wiring) {
            wiring = false;
            var detached = detachedEdge;
            detachedEdge = null;
            if (detached != null && !wireMoved) {
                // A plain click on a connected input leaves its wire exactly where it was.
                edges.add(Math.min(Math.max(0, detachedIndex), edges.size()), detached);
                if (!undo.isEmpty()) undo.pop();
                wireFrom = "";
                return true;
            }
            reconnecting = detached != null;
            for (int i = nodes.size() - 1; i >= 0; i--) {
                var node = nodes.get(i);
                if (inputAt(node, x, y)) { connectTo(node); reconnecting = false; return true; }
            }
            if (wireMoved) {
                // Dropping on a node body connects to it; dropping on empty canvas offers a new node
                // (a re-routed wire dropped on empty canvas is simply removed).
                var target = nodeAt(x, y);
                if (target != null && !target.id().equals(wireFrom)
                        && !List.of("event", "context").contains(target.type())) {
                    connectTo(target);
                    reconnecting = false;
                    return true;
                }
                String source = wireFrom, port = wirePort;
                wireFrom = "";
                if (detached == null && target == null && x >= 8 && x <= canvasRight() && y >= 56 && y <= height - 28)
                    openNodeLibraryAt((x - 8 - panX) / zoom, (y - 56 - panY) / zoom - 32, source, port);
            }
            reconnecting = false;
            return true;
        }
        if (button == 0 && dragging && dragRemembered && selectedIds.size() == 1) {
            var variable = selected();
            if (variable != null && List.of("variable", "context").contains(variable.type())) {
                var target = nodes.stream().filter(node -> acceptsVariable(node, variable)
                        && x >= sx(node) && x < sx(node) + nodeWidth(node) * zoom
                        && y >= sy(node) && y < sy(node) + nodeHeight(node) * zoom)
                        .findFirst().orElse(null);
                if (target != null) {
                    if (target.type().equals("set_context") || target.type().equals("set_variable")) {
                        var data = new LinkedHashMap<>(target.data());
                        String type;
                        if (variable.type().equals("context")) {
                            type = "set_context";
                            String field = variable.get("field", "");
                            String scope = TriggerGraph.SETTABLE_PLAYER_FIELDS.contains(field) ? "player" : "world";
                            data.put("scope", scope);
                            data.put("field", field);
                            data.put("value", defaultGameValue(field));
                            data.remove("variable"); data.remove("operator");
                            data.remove("sourceVariable"); data.remove("sourceField");
                            if (scope.equals("player")) data.put("player", variable.get("player", "@s"));
                            else data.putIfAbsent("dimension", "");
                        } else {
                            type = "set_variable";
                            data.put("variable", variable.get("variable", ""));
                            data.remove("scope"); data.remove("field"); data.remove("player"); data.remove("dimension");
                        }
                        nodes.set(nodes.indexOf(target),
                                new TriggerGraph.Node(target.id(), type, target.x(), target.y(), data));
                        finishVariableDrop(variable, target);
                        return true;
                    }
                    String suffix = visualSecond && List.of("and", "or").contains(target.get("logic", "single")) ? "2" : "";
                    boolean leftSlot = x < sx(target) + nodeWidth(target) * zoom / 2;
                    for (var slot : nodeLayout(target).slots()) {
                        double localX = (x - sx(target)) / zoom, localY = (y - sy(target)) / zoom;
                        if (localX >= slot.x() && localX < slot.x() + slot.width()
                                && localY >= slot.y() && localY < slot.y() + slot.height()) {
                            suffix = slot.suffix();
                            leftSlot = slot.left();
                            break;
                        }
                    }
                    String slot = (leftSlot ? "field" : "expectedVar") + suffix;
                    var data = new LinkedHashMap<>(target.data());
                    if (variable.type().equals("context")) {
                        data.put(leftSlot ? slot : "expectedField" + suffix, variable.get("field", ""));
                        data.put((leftSlot ? "fieldPlayer" : "expectedPlayer") + suffix, variable.get("player", "@s"));
                        if (!leftSlot) data.remove("expectedVar" + suffix);
                    } else {
                        data.put(slot, leftSlot ? "var:" + variable.get("variable", "")
                                : variable.get("variable", ""));
                        if (!leftSlot) data.remove("expectedField" + suffix);
                    }
                    nodes.set(nodes.indexOf(target),
                            new TriggerGraph.Node(target.id(), target.type(), target.x(), target.y(), data));
                    finishVariableDrop(variable, target);
                    return true;
                }
            }
        }
        dragging = panning = false;
        guideXs.clear();
        guideYs.clear();
        return super.mouseReleased(x, y, button);
    }
    private static boolean acceptsVariable(TriggerGraph.Node target, TriggerGraph.Node variable) {
        if (target.id().equals(variable.id())) return false;
        if (target.type().equals("js") && target.get("mode", "script").equals("visual")) return true;
        return List.of("set_context", "set_variable").contains(target.type())
                && (variable.type().equals("variable") || variable.type().equals("context")
                && (TriggerGraph.SETTABLE_PLAYER_FIELDS.contains(variable.get("field", ""))
                || TriggerGraph.SETTABLE_WORLD_FIELDS.contains(variable.get("field", ""))));
    }
    private void finishVariableDrop(TriggerGraph.Node variable, TriggerGraph.Node target) {
        boolean linked = edges.stream().anyMatch(edge ->
                edge.from().equals(variable.id()) || edge.to().equals(variable.id()));
        if (!linked) nodes.removeIf(node -> node.id().equals(variable.id()));
        else {
            var origin = dragOrigins.get(variable.id());
            if (origin != null) nodes.set(nodes.indexOf(variable), origin);
        }
        selected = target.id();
        selectedIds.clear();
        selectedIds.add(selected);
        dragging = false;
        guideXs.clear();
        guideYs.clear();
        message = "";
        init();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (variablesOpen || contextOpen()) return true;
        if (resizeOrigin != null || dragging || wiring || panning) return true;
        if (!sourceMode && x > canvasRight()) {
            inspectorTarget = Math.clamp(inspectorTarget - vertical * 32, 0, inspectorMaxScroll);
            return true;
        }
        if (sourceMode || x >= canvasRight()) return super.mouseScrolled(x, y, horizontal, vertical);
        double old = zoom;
        zoom = Math.clamp(zoom * Math.pow(1.12, vertical), .4, 2);
        panX = x - 8 - (x - 8 - panX) / old * zoom;
        panY = y - 56 - (y - 56 - panY) / old * zoom;
        return true;
    }
    private void syncPositionFields() {
        var node = selected();
        if (node == null || nodeX == null || nodeY == null) return;
        syncingPosition = true;
        try {
            if (getFocused() != nodeX) nodeX.setValue(Integer.toString(node.x()));
            if (getFocused() != nodeY) nodeY.setValue(Integer.toString(node.y()));
            if (nodeWidthField != null && getFocused() != nodeWidthField)
                nodeWidthField.setValue(Integer.toString(nodeWidth(node)));
            if (nodeHeightField != null && getFocused() != nodeHeightField)
                nodeHeightField.setValue(Integer.toString(nodeHeight(node)));
        } finally { syncingPosition = false; }
    }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return inspectorWidgets.contains(widget) ? viewport(canvasRight() + 8, 56, width - 8, height - 42) : null;
    }

    private int inspectorThumbHeight() {
        int available = Math.max(1, height - 98);
        return Math.max(12, available * available / Math.max(1, available + inspectorMaxScroll));
    }
    private double inspectorThumbY() {
        return 56 + (height - 98 - inspectorThumbHeight()) * inspectorPosition / Math.max(1, inspectorMaxScroll);
    }
    private void dragInspectorThumb(double y) {
        inspectorPosition = inspectorTarget = Math.clamp((y - 56 - inspectorThumbOffset) * inspectorMaxScroll
                / Math.max(1, height - 98 - inspectorThumbHeight()), 0, inspectorMaxScroll);
    }
    private void advanceInspector() {
        long now = System.nanoTime();
        double blend = inspectorFrame == 0 ? 1 : 1 - Math.exp(-18 * Math.min(.1, (now - inspectorFrame) / 1e9));
        inspectorFrame = now;
        if (hasChoicePopup()) {
            inspectorTarget = inspectorPosition;
            return;
        }
        inspectorTarget = Math.clamp(inspectorTarget, 0, inspectorMaxScroll);
        inspectorPosition += (inspectorTarget - inspectorPosition) * blend;
        int next = (int) Math.round(inspectorPosition);
        int offset = inspectorScroll - next;
        if (offset == 0) return;
        inspectorScroll = next;
        inspectorWidgets.forEach(widget -> widget.setY(widget.getY() + offset));
        labels.replaceAll(label -> new Label(label.text(), label.x(), label.y() + offset));
        inspectorHints.replaceAll(hint -> new InspectorHint(hint.title(), hint.detail(),
                hint.x(), hint.y() + offset, hint.width(), hint.valid()));
    }
    @Override public void onClose() {
        boolean unchanged;
        try {
            unchanged = graph().equals(originalGraph)
                    && (!sourceMode || source.getValue().equals(GSON.toJson(graph())));
        } catch (RuntimeException invalidDraft) {
            unchanged = false;
        }
        if (unchanged) {
            closeTo(parent);
            return;
        }
        minecraft.setScreen(new QuestConfirmScreen(this, text("discard_changes"), text("discard"),
                () -> minecraft.setScreen(parent)));
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (hasChoicePopup()) return super.keyPressed(key, scan, modifiers);
        if (sourceMode && hasControlDown() && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            boolean forward = key == GLFW.GLFW_KEY_Y || (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            restoreSource(forward ? sourceRedo : sourceUndo, forward ? sourceUndo : sourceRedo);
            return true;
        }
        if (hasChoicePopup()) return super.keyPressed(key, scan, modifiers);
        if (variablesOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { variablesOpen = false; init(); }
            return true;
        }
        if (searchOpen && searchBox != null && getFocused() == searchBox) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_DOWN) {
                nextSearchMatch((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) { nextSearchMatch(-1); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { closeSearch(); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && contextOpen()) {
            closeContext(); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !wireFrom.isEmpty()) {
            wireFrom = "";
            wiring = false;
            detachedEdge = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && helpOpen) { helpOpen = false; return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && searchOpen) { closeSearch(); return true; }
        if (!sourceMode && !(getFocused() instanceof EditBox) && !(getFocused() instanceof MultiLineEditBox)) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) {
                toggleSearch(); return true;
            }
            if (modifiers == 0 && key == GLFW.GLFW_KEY_F && !selectedIds.isEmpty()) {
                focusSelection(); return true;
            }
            if (modifiers == 0 && key == GLFW.GLFW_KEY_HOME) {
                fitGraph(); return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_C) {
                copySelected(); return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_V) {
                pasteSelected(); return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_A) {
                selectAll(); return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_D && !selectedIds.isEmpty()) {
                copySelected(); pasteSelected(); return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_0) {
                resetZoom(); return true;
            }
            int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            if ((dx != 0 || dy != 0) && !selectedIds.isEmpty()) {
                remember();
                for (int i = 0; i < nodes.size(); i++) {
                    var node = nodes.get(i);
                    if (selectedIds.contains(node.id())) nodes.set(i, new TriggerGraph.Node(
                            node.id(), node.type(), (int) Math.clamp((long) node.x() + dx, -100000, 100000),
                            (int) Math.clamp((long) node.y() + dy, -100000, 100000), node.data()));
                }
                init();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) { deleteSelected(); return true; }
            if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) { restore(undo, redo); return true; }
            if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) { restore(redo, undo); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
    private static final class SourceBox extends SmoothTextBox {
        private SourceBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height,
                          Component message, Component placeholder) {
            super(font, x, y, width, height, message, placeholder);
        }
        private void restoreScroll(double value) { setScrollAmount(value); }
    }
}
