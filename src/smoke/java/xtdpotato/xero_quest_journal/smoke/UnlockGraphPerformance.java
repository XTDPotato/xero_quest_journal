package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.QuestUnlockGraphScreen;
import xtdpotato.xero_quest_journal.client.ClientPreferences;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** End-to-end frame intervals in the real client; excluded from production artifacts. */
final class UnlockGraphPerformance {
    private static final long WARMUP = 5_000_000_000L, SAMPLE = 10_000_000_000L;
    private static final long[] intervals = new long[65536];
    private static final String[] SCENES = {"stationary", "panning", "zooming"};
    private static final List<Map<String, Object>> results = new ArrayList<>();
    private static QuestUnlockGraphScreen target;
    private static Field panX, panY, zoom;
    private static double originalPanX, originalPanY, originalZoom;
    private static double lastPanX, lastPanY, lastZoom;
    private static Object savedLink;
    private static int phase, sampleCount, savedLimit;
    private static boolean savedVsync, savedPause, savedReducedMotion, finished;
    private static long phaseStart, previousFrame;
    private static jdk.jfr.Recording recording;

    static boolean tick(Minecraft game, QuestUnlockGraphScreen graph) throws Exception {
        if (finished) return true;
        long now = System.nanoTime();
        if (target == null) {
            target = graph;
            panX = field("panX"); panY = field("panY"); zoom = field("zoom");
            originalPanX = panX.getDouble(graph); originalPanY = panY.getDouble(graph); originalZoom = zoom.getDouble(graph);
            lastPanX = originalPanX; lastPanY = originalPanY; lastZoom = originalZoom;
            savedLink = field("selectedLink").get(graph);
            field("selectedLink").set(graph, ((Set<?>) field("links").get(graph)).stream().findFirst().orElse(null));
            savedReducedMotion = ClientPreferences.get().reducedMotion; ClientPreferences.get().reducedMotion = false;
            savedLimit = game.options.framerateLimit().get(); savedVsync = game.options.enableVsync().get();
            savedPause = game.options.pauseOnLostFocus;
            game.options.framerateLimit().set(260); game.options.enableVsync().set(false);
            game.options.pauseOnLostFocus = false; game.getWindow().updateVsync(false);
            if (Boolean.getBoolean("xero.unlockGraphProfile")) {
                recording = new jdk.jfr.Recording();
                recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(2)).withStackTrace();
                recording.enable("jdk.NativeMethodSample").withPeriod(java.time.Duration.ofMillis(2)).withStackTrace();
                recording.start();
            }
            phaseStart = now;
        }
        game.setWindowActive(true);
        if (now - phaseStart < WARMUP + SAMPLE) return false;
        if (sampleCount < 60) throw new AssertionError("Insufficient real rendered frames for performance measurement");
        long[] sorted = Arrays.copyOf(intervals, sampleCount); Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElseThrow();
        int slowCount = Math.max(1, (int) Math.ceil(sampleCount * .01));
        double slowMean = Arrays.stream(sorted, sampleCount - slowCount, sampleCount).average().orElseThrow();
        var row = new LinkedHashMap<String, Object>();
        row.put("scene", SCENES[phase]); row.put("frames", sampleCount);
        row.put("average_fps", 1_000_000_000. / mean);
        row.put("p50_ms", sorted[sampleCount / 2] / 1_000_000.);
        row.put("p95_ms", sorted[(int) Math.ceil(sampleCount * .95) - 1] / 1_000_000.);
        row.put("p99_ms", sorted[(int) Math.ceil(sampleCount * .99) - 1] / 1_000_000.);
        row.put("one_percent_low_fps", 1_000_000_000. / slowMean);
        row.put("meets_120fps_target", 1_000_000_000. / mean > 120
                && sorted[(int) Math.ceil(sampleCount * .99) - 1] < 1_000_000_000. / 120);
        results.add(row);
        if (++phase < SCENES.length) { phaseStart = now; previousFrame = 0; sampleCount = 0; return false; }
        panX.setDouble(graph, originalPanX); panY.setDouble(graph, originalPanY); zoom.setDouble(graph, originalZoom);
        field("selectedLink").set(graph, savedLink); ClientPreferences.get().reducedMotion = savedReducedMotion;
        var report = new LinkedHashMap<String, Object>();
        report.put("timestamp", java.time.OffsetDateTime.now().toString());
        report.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
        report.put("cpu", System.getenv("PROCESSOR_IDENTIFIER"));
        report.put("resolution", List.of(game.getWindow().getWidth(), game.getWindow().getHeight()));
        report.put("gui_scale", game.getWindow().getGuiScale()); report.put("vsync", false); report.put("fps_limit", 260);
        report.put("animations", "Enabled, with one selected animated link");
        report.put("minecraft", "1.21.1 NeoForge development client");
        report.put("task_count", ((List<?>) field("tasks").get(graph)).size());
        report.put("profiler_enabled", recording != null);
        report.put("measurement", "RenderFrameEvent.Post intervals, 5s warmup + 10s sample per scene; world rendering included; focus flag active");
        report.put("results", results);
        Path output = Path.of("..", "build", "verification", "unlock-graph-performance.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (recording != null) { recording.stop(); recording.dump(output.resolveSibling("unlock-graph-performance.jfr")); recording.close(); }
        game.options.framerateLimit().set(savedLimit); game.options.enableVsync().set(savedVsync);
        game.options.pauseOnLostFocus = savedPause; game.getWindow().updateVsync(savedVsync);
        finished = true; target = null;
        return true;
    }

    static void frame() {
        if (target == null || Minecraft.getInstance().screen != target) return;
        long now = System.nanoTime(), elapsed = now - phaseStart;
        try {
            if (manualInput()) { cancelForManualInput(); return; }
            if (phase == 1) panX.setDouble(target, originalPanX + Math.sin(elapsed / 900_000_000.) * 500);
            if (phase == 2) {
                double value = .65 + Math.sin(elapsed / 900_000_000.) * .2;
                zoom.setDouble(target, value);
                panX.setDouble(target, target.width / 2. - (target.width / 2. - originalPanX) / originalZoom * value);
                panY.setDouble(target, target.height / 2. - (target.height / 2. - originalPanY) / originalZoom * value);
            }
            lastPanX = panX.getDouble(target); lastPanY = panY.getDouble(target); lastZoom = zoom.getDouble(target);
        } catch (Exception e) { throw new IllegalStateException(e); }
        if (elapsed >= WARMUP && elapsed < WARMUP + SAMPLE && previousFrame != 0 && sampleCount < intervals.length)
            intervals[sampleCount++] = now - previousFrame;
        previousFrame = now;
    }

    private static boolean manualInput() throws Exception {
        long window = Minecraft.getInstance().getWindow().getWindow();
        for (int button = 0; button <= 2; button++)
            if (GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS) return true;
        return (boolean) field("movingCanvas").get(target) || (boolean) field("minimapDragging").get(target)
                || !((String) field("draggingNode").get(target)).isBlank()
                || Math.abs(panX.getDouble(target) - lastPanX) > .0001
                || Math.abs(panY.getDouble(target) - lastPanY) > .0001
                || Math.abs(zoom.getDouble(target) - lastZoom) > .0001;
    }

    private static void cancelForManualInput() throws Exception {
        var game = Minecraft.getInstance();
        game.options.framerateLimit().set(savedLimit); game.options.enableVsync().set(savedVsync);
        game.options.pauseOnLostFocus = savedPause; game.getWindow().updateVsync(savedVsync);
        ClientPreferences.get().reducedMotion = savedReducedMotion;
        if (recording != null) { recording.stop(); recording.close(); recording = null; }
        finished = true; target = null;
        // Hand control to the human without saving the test draft or continuing automated clicks.
        System.clearProperty("xero.taskWorkflowTest"); System.clearProperty("xero.unlockGraphPerf");
        Files.writeString(Path.of("..", "build", "verification", "unlock-graph-performance-cancelled.txt"),
                "Performance measurement stopped on manual input. Camera and draft retained; no automatic save or clicks.\n");
        game.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal("Performance test stopped"), false);
    }

    private static Field field(String name) throws Exception {
        var field = QuestUnlockGraphScreen.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
