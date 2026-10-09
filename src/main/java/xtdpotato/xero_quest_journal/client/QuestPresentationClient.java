package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Coalesced, fingerprint-checked publication. Runtime overrides never enter author source. */
final class QuestPresentationClient {
    private static final Gson GSON = new Gson();
    private static final Set<QuestPresentationClient> ACTIVE = new HashSet<>();
    private final Map<String, QuestPresentation> seen = new HashMap<>();
    private final Map<String, QuestPresentation> queued = new LinkedHashMap<>();
    private CompletableFuture<QuestAuthoring.Result> pending;
    private String pendingId;
    private QuestPresentation pendingValue;
    private String error;
    static String sourceId(LayoutEditorState.Element element) {
        return element.attribute("journal_source_id", element.id());
    }
    private static String layoutId(String quest, String control) {
        return "visual:" + UUID.nameUUIDFromBytes((quest + "\n" + control)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    boolean busy() { return pending != null || !queued.isEmpty(); }
    String takeError() { String result = error; error = null; return result; }
    static void tickAll() {
        for (var writer : List.copyOf(ACTIVE)) {
            writer.tick();
            if (!writer.busy()) ACTIVE.remove(writer);
        }
    }
    void load(QuestDefinition quest, LayoutEditorState layout) {
        if (quest == null || queued.containsKey(quest.id()) || quest.id().equals(pendingId)) return;
        var presentation = quest.presentation();
        var previous = seen.put(quest.id(), presentation);
        if (presentation.equals(previous) || !presentation.authored() && (previous == null || !previous.authored())) return;
        Map<String, String> localIds = new HashMap<>();
        for (var control : presentation.controls()) {
            String existing = layout.elements().values().stream()
                    .filter(e -> e.quest().equals(quest.id()) && sourceId(e).equals(control.id()))
                    .map(LayoutEditorState.Element::id).findFirst().orElse(null);
            String id = existing != null ? existing : !layout.elements().containsKey(control.id())
                    ? control.id() : layoutId(quest.id(), control.id());
            // A reserved-looking author ID must not collide with another control's internal key.
            while (localIds.containsValue(id) || layout.elements().containsKey(id)
                    && !layout.elements().get(id).quest().equals(quest.id()))
                id = layoutId(quest.id(), id);
            localIds.put(control.id(), id);
        }
        var removed = layout.elements().values().stream().filter(e -> e.quest().equals(quest.id()))
                .map(LayoutEditorState.Element::id).toList();
        removed.forEach(id -> { layout.elements().remove(id); layout.positions().remove(id); layout.parents().remove(id); });
        if (!presentation.authored()) return;
        layout.setTaskCanvas(quest.id(), presentation.width(), presentation.height());
        for (var control : presentation.controls()) {
            var properties = new LinkedHashMap<>(control.properties());
            properties.put("journal_source_id", control.id());
            if (!control.graph().nodes().isEmpty()) properties.put("eventGraph", GSON.toJson(control.graph()));
            String id = localIds.get(control.id());
            layout.elements().put(id, new LayoutEditorState.Element(id, quest.id(), control.kind(),
                    control.value(), control.width(), control.height(), properties));
            layout.setPosition(id, control.x(), control.y());
        }
        for (var control : presentation.controls()) if (!control.parent().isBlank())
            layout.setParent(localIds.get(control.id()), localIds.get(control.parent()));
    }
    static QuestPresentation snapshot(String quest, LayoutEditorState layout) {
        var size = layout.taskCanvas(quest);
        List<QuestPresentation.Control> controls = new ArrayList<>();
        for (var element : layout.elements().values()) {
            if (!element.quest().equals(quest)) continue;
            var position = layout.position(element.id(), 0, 0);
            var properties = new LinkedHashMap<>(element.attributes());
            String graph = properties.remove("eventGraph");
            properties.remove("journal_source_id");
            var parent = layout.elements().get(layout.parentOf(element.id()));
            controls.add(new QuestPresentation.Control(sourceId(element), element.kind(), element.value(),
                    position.x(), position.y(), element.width(), element.height(),
                    parent == null ? "" : sourceId(parent), properties,
                    graph == null ? TriggerGraph.empty() : GSON.fromJson(graph, TriggerGraph.class)));
        }
        var task = QuestRepository.find(quest);
        return new QuestPresentation(true, size.width(), size.height(), controls,
                task == null ? List.of() : task.presentation().variables());
    }
    static void renameReferences(LayoutEditorState layout, String quest, String oldId, String newId) {
        if (oldId.equals(newId)) return;
        for (var entry : layout.elements().entrySet()) {
            var element = entry.getValue();
            if (!element.quest().equals(quest) || !element.attributes().containsKey("eventGraph")) continue;
            var graph = GSON.fromJson(element.attribute("eventGraph", ""), TriggerGraph.class);
            var nodes = graph.nodes().stream().map(node -> {
                if (!List.of("set_property", "reset_property").contains(node.type())
                        || !node.get("control", "").equals(oldId)) return node;
                var data = new LinkedHashMap<>(node.data());
                data.put("control", newId);
                return new TriggerGraph.Node(node.id(), node.type(), node.x(), node.y(), data);
            }).toList();
            var attrs = new LinkedHashMap<>(element.attributes());
            attrs.put("eventGraph", GSON.toJson(new TriggerGraph(nodes, graph.edges())));
            entry.setValue(new LayoutEditorState.Element(element.id(), element.quest(), element.kind(), element.value(),
                    element.width(), element.height(), attrs));
        }
    }
    void queue(String quest, LayoutEditorState layout) {
        if (quest == null || quest.isBlank()) return;
        QuestPresentation value = snapshot(quest, layout);
        if (!value.equals(seen.get(quest)) || quest.equals(pendingId)) queued.put(quest, value);
        if (!queued.isEmpty()) ACTIVE.add(this);
    }
    void variablesChanged(String quest, String oldId, String newId, LayoutEditorState layout) {
        var current = QuestRepository.find(quest);
        var previous = seen.get(quest);
        if (current == null || previous == null || busy()) return;
        var renamed = previous.renameVariable(oldId, newId);
        var rebased = new QuestPresentation(renamed.authored(), renamed.width(), renamed.height(),
                renamed.controls(), current.presentation().variables());
        // Accept only the variable transaction; unrelated source edits still conflict.
        if (!rebased.equals(current.presentation())) return;
        seen.put(quest, current.presentation());
        var draft = snapshot(quest, layout).renameVariable(oldId, newId);
        for (var control : draft.controls()) {
            for (var entry : layout.elements().entrySet()) {
                var element = entry.getValue();
                if (!element.quest().equals(quest) || !sourceId(element).equals(control.id())) continue;
                var attributes = new LinkedHashMap<>(element.attributes());
                if (!control.graph().nodes().isEmpty())
                    attributes.put("eventGraph", GSON.toJson(control.graph()));
                entry.setValue(new LayoutEditorState.Element(element.id(), element.quest(), element.kind(),
                        element.value(), element.width(), element.height(), attributes));
                break;
            }
        }
    }
    void tick() {
        if (pending != null && pending.isDone()) {
            try {
                var result = pending.join();
                if (result.successful()) seen.put(pendingId, pendingValue);
                else { error = result.error(); queued.remove(pendingId); }
            } catch (Exception failure) {
                error = "screen.xero_quest_journal.create.error.save";
                queued.remove(pendingId);
            }
            pending = null;
            pendingId = null;
        }
        if (pending != null || queued.isEmpty()) return;
        var game = Minecraft.getInstance();
        var server = game.getSingleplayerServer();
        if (server == null || game.player == null) { queued.clear(); return; }
        var next = queued.entrySet().iterator().next();
        String id = next.getKey();
        var value = next.getValue();
        queued.remove(id);
        try {
            String source = QuestRepository.source(id);
            var current = QuestRepository.find(id);
            if (current == null || seen.containsKey(id) && !current.presentation().equals(seen.get(id))) {
                error = "screen.xero_quest_journal.create.error.conflict";
                return;
            }
            if (value.equals(current.presentation())) { seen.put(id, value); return; }
            String updated = QuestPresentationDocuments.write(source, value);
            String fingerprint = QuestRepository.fingerprint(source);
            UUID player = game.player.getUUID();
            pendingId = id;
            pendingValue = value;
            pending = CompletableFuture.supplyAsync(() -> QuestAuthoring.editSource(
                    server.getPlayerList().getPlayer(player), id, fingerprint, updated), server);
        } catch (Exception failure) {
            error = "screen.xero_quest_journal.create.error.save";
        }
    }
}
