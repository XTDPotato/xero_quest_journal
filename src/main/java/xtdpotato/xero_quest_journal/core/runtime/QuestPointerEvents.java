package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Pointer coordinates are untrusted UI input; task visibility and actions stay server-authoritative. */
public final class QuestPointerEvents {
    public static final List<String> REGIONS = List.of("any", "top_left", "top_right", "bottom_left", "bottom_right", "center", "point");
    private record Press(String quest, String control, long revision) {}
    private static final Map<ServerPlayer, Press> PRESSED = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> HOVER_TIME = new WeakHashMap<>();
    private QuestPointerEvents() {}

    public static void input(ServerPlayer player, String questId, String controlId, long revision, String event,
                             double x, double y, double width, double height) {
        if (!player.server.isSameThread() || revision != QuestRepository.revision()
                || !List.of("hover", "press", "release").contains(event)
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                || Math.abs(x) > 1000000 || Math.abs(y) > 1000000
                || width < 1 || height < 1 || width > 100000 || height > 100000) return;
        var quest = QuestRepository.find(questId);
        var control = quest == null ? null : xtdpotato.xero_quest_journal.core.QuestTemplateStore.presentation(quest).controls().stream()
                .filter(c -> c.id().equals(controlId)).findFirst().orElse(null);
        if (control == null || !QuestRuntime.unlocked(QuestProgressData.get(player.server), player, quest)
                || !QuestRuntime.allowsCompletedEvents(player, quest)
                || !QuestEventGraphs.visible(player, quest, control)) {
            PRESSED.remove(player);
            return;
        }
        boolean inside = x >= 0 && y >= 0 && x < width && y < height;
        long now = player.server.getTickCount();
        var press = PRESSED.get(player);
        boolean held = press != null && press.quest.equals(questId) && press.control.equals(controlId)
                && press.revision == revision;
        if (event.equals("press")) {
            if (!inside) return;
            PRESSED.put(player, new Press(questId, controlId, revision));
            held = true;
        } else if (event.equals("release")) {
            PRESSED.remove(player);
            if (!held) return;
        } else {
            if (!inside || now < HOVER_TIME.getOrDefault(player, Long.MIN_VALUE)) return;
            HOVER_TIME.put(player, now + 4);
        }
        Map<String, String> values = Map.of("mouseX", Double.toString(x), "mouseY", Double.toString(y),
                "mouseWidth", Double.toString(width), "mouseHeight", Double.toString(height),
                "mouseHeld", Boolean.toString(held && !event.equals("release")), "mouseInside", Boolean.toString(inside));
        QuestEventGraphs.control(player, questId, controlId, event, values);
        if (event.equals("hover") && held) QuestEventGraphs.control(player, questId, controlId, "hold", values);
        if (event.equals("release") && inside) QuestEventGraphs.control(player, questId, controlId, "click", values);
    }

    public static boolean matches(TriggerGraph.Node node, Map<String, String> values) {
        String region = node.get("pointerRegion", "any");
        if (region.equals("any")) return true;
        if (!REGIONS.contains(region) || !values.getOrDefault("mouseInside", "false").equals("true")) return false;
        try {
            double x = Double.parseDouble(values.get("mouseX")), y = Double.parseDouble(values.get("mouseY"));
            double width = Double.parseDouble(values.get("mouseWidth")), height = Double.parseDouble(values.get("mouseHeight"));
            double cx = switch (region) {
                case "center" -> width / 2;
                case "top_right", "bottom_right" -> width;
                case "point" -> Double.parseDouble(node.get("regionX", "0"));
                default -> 0;
            };
            double cy = switch (region) {
                case "center" -> height / 2;
                case "bottom_left", "bottom_right" -> height;
                case "point" -> Double.parseDouble(node.get("regionY", "0"));
                default -> 0;
            };
            double radius = Double.parseDouble(node.get("radius", "5"));
            return Double.isFinite(radius) && radius >= 0 && radius <= 100000 && Math.hypot(x - cx, y - cy) <= radius;
        } catch (RuntimeException invalid) { return false; }
    }
}
