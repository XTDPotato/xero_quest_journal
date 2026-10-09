package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JournalFocusRegression {
    private static final String A = "journal_focus_main", B = "journal_focus_world";
    private static int stage, wait;
    private static CompletableFuture<Void> work;
    private static boolean motion;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                motion = ClientPreferences.get().reducedMotion;
                ClientPreferences.get().reducedMotion = true;
                game.options.pauseOnLostFocus = false;
                game.options.guiScale().set(2);
                resize(game, 1920, 1080);
                var layout = LayoutStore.load("journal");
                layout.elements().entrySet().removeIf(entry -> List.of(A, B).contains(entry.getValue().quest()));
                LayoutStore.save("journal", layout);
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    try { serverChecks(server.getPlayerList().getPlayer(game.player.getUUID())); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }, server);
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new QuestNetwork.ProgressRequestPayload(-1));
            }
            case 2 -> {
                if (ClientQuestDefinitions.find(A) == null || !ClientQuestProgress.focusedId().equals(A)) return false;
                if (!ActiveListRegression.tick(game)) return false;
                open(game, A);
            }
            case 3 -> {
                require(find(game, "focus").active, "Focus switch disabled");
                require(row(game, A).accent().equals("gold"), "Main group accent missing");
                require(row(game, A).trackingStyle().equals("main"), "Main tracking style missing");
                require((double) invoke(game.screen, "canvasContentScale") > 1.0,
                        "Task canvas did not grow to fit the desktop viewport");
                checkSpringSettle();
                checkArrow(game, row(game, A));
                capture(game, "journal-focus-desktop");
                find(game, "focus").onPress();
            }
            case 4 -> {
                if (!ClientQuestProgress.focusedId().isEmpty()) return false;
                require(ClientQuestProgress.isTracked(B), "Focus exit did not restore tracking");
                find(game, "focus").onPress();
            }
            case 5 -> {
                if (!ClientQuestProgress.focusedId().equals(A)) return false;
                resize(game, 640, 480);
            }
            case 6 -> {
                var focus = find(game, "focus");
                require(focus.getX() >= 0 && focus.getRight() <= game.screen.width
                        && focus.getBottom() <= game.screen.height, "Small-window focus outside viewport");
                capture(game, "journal-focus-small");
                resize(game, 1920, 1080);
                open(game, B);
            }
            case 7 -> {
                require(find(game, "focus").active, "Cannot exit focus while browsing another task");
                require(!find(game, "track").active, "Other task tracking not disabled");
                capture(game, "journal-focus-other-task");
                find(game, "focus").onPress();
            }
            case 8 -> {
                if (!ClientQuestProgress.focusedId().isEmpty()) return false;
                open(game, B);
                StyledButton track = find(game, "track");
                require(track.active, "Tracking action unexpectedly disabled");
                boolean handled = game.screen.mouseClicked(track.getX() + track.getWidth() / 2.0,
                        track.getY() + track.getHeight() / 2.0, 0);
                require(handled, "Mouse press did not reach the tracking button");
                game.screen.mouseReleased(track.getX() + track.getWidth() / 2.0,
                        track.getY() + track.getHeight() / 2.0, 0);
                require(field(game.screen, "actionPending") != null,
                        "Mouse release did not dispatch the tracking action");
                open(game, A);
                set(game.screen, "editMode", true);
                var method = QuestScreen.class.getDeclaredMethod("editProperties", String.class, String.class);
                method.setAccessible(true);
                method.invoke(game.screen, "group", "journal_focus_group");
                Object form = field(game.screen, "form");
                require((boolean) field(form, "focusAvailable"), "Group focus setting not loaded");
                require(field(form, "listAccent").equals("auto"), "Accent setting not loaded");
                set(form, "propertyPage", "basic");
                game.screen.init(game, game.screen.width, game.screen.height);
                require(find(game, "focus") != null, "Group focus setting control absent");
                set(form, "scroll", 150.0);
                var update = QuestScreen.class.getDeclaredMethod("updateFormViewport");
                update.setAccessible(true);
                update.invoke(game.screen);
            }
            case 9 -> {
                capture(game, "journal-tracking-settings");
                ClientPreferences.get().reducedMotion = false;
                game.setScreen(new Atlas(0));
            }
            case 10 -> {
                checkAtlas(game);
                capture(game, "journal-tracking-wave-start");
                game.setScreen(new Atlas(650_000_000L));
            }
            case 11 -> {
                capture(game, "journal-tracking-two-waves");
                game.setScreen(new Atlas(1_400_000_000L));
            }
            case 12 -> {
                capture(game, "journal-tracking-wave-fade");
                ClientPreferences.get().reducedMotion = motion;
                return true;
            }
            default -> throw new AssertionError("Unknown journal stage");
        }
        stage++;
        wait = 15;
        return false;
    }

    private static void serverChecks(ServerPlayer player) throws Exception {
        ActiveListRegression.fixtures();
        var preferences = ClientPreferences.get();
        int originalDuration = preferences.popupAnimationMillis;
        preferences.popupAnimationMillis = 100;
        try {
            require(preferences.popupAnimationMillis == 100
                            && new ClientPreferences().popupAnimationMillis == 100
                            && new ClientPreferences().taskToastScalePercent == 33
                            && new ClientPreferences().taskToastIconScalePercent == 33,
                    "Quest notice default settings changed");
            require(QuestCompletionToast.opacity(0) == 0
                            && Math.abs(QuestCompletionToast.opacity(50_000_000L) - .5F) < .01F
                            && QuestCompletionToast.opacity(100_000_000L) == 1
                            && Math.abs(QuestCompletionToast.opacity(4_150_000_000L) - .5F) < .01F
                            && QuestCompletionToast.opacity(4_200_000_000L) == 0,
                    "Completion toast did not use the configured 100ms animation");
            require(QuestUnlockToast.opacity(0) == 0
                            && Math.abs(QuestUnlockToast.opacity(50_000_000L) - .5F) < .01F
                            && QuestUnlockToast.opacity(100_000_000L) == 1
                            && Math.abs(QuestUnlockToast.opacity(4_950_000_000L) - .5F) < .01F
                            && QuestUnlockToast.opacity(5_000_000_000L) == 0,
                    "New task toast did not use the configured 100ms animation");
        } finally {
            preferences.popupAnimationMillis = originalDuration;
        }
        var gson = new Gson();
        Path group = QuestRepository.root().resolve("journal_focus");
        Files.createDirectories(group);
        Files.writeString(group.resolve("group_manifest.json"),
                "{\"id\":\"journal_focus_group\",\"name\":\"A New Journey\",\"type\":\"main\",\"focusAvailable\":true}");
        Files.writeString(group.resolve(A + ".json"), """
                {"id":"journal_focus_main","title":"The Journey Begins","type":"main",
                 "description":"Continue the journey through the mountains.","trackable":true,"unlocked":true,
                 "triggers":[{"type":"event","item":"focus_test","count":3}],"rewardMode":"manual"}
                """);
        Files.writeString(group.resolve("journal_focus_sibling.json"), """
                {"id":"journal_focus_sibling","title":"Beyond the Mountains","type":"commission","trackable":true,"unlocked":true,
                 "triggers":[{"type":"confirm","item":"","count":1}]}
                """);
        Files.writeString(QuestRepository.root().resolve(B + ".json"), """
                {"id":"journal_focus_world","title":"A Quiet Encounter","type":"world","trackable":true,
                 "triggers":[{"type":"event","item":"focus_test","count":3}],"rewardMode":"manual"}
                """);
        Files.writeString(QuestRepository.root().resolve("journal_focus_followup.json"), """
                {"id":"journal_focus_followup","title":"Follow the Trail","type":"world","trackable":true,
                 "defaultUnlocked":false,"parents":["journal_focus_world"],
                 "triggers":[{"type":"confirm","item":"","count":1}]}
                """);
        for (String id : List.of(A, B)) {
            Path path = id.equals(A) ? group.resolve(A + ".json") : QuestRepository.root().resolve(B + ".json");
            var graph = new TriggerGraph(List.of(
                    new TriggerGraph.Node("event", "event", 0, 0,
                            Map.of("event", "jump", "target", "focus_probe", "cooldown", "1")),
                    new TriggerGraph.Node("action", "command", 160, 0, Map.of("command", "tag @s add " + id))),
                    List.of(new TriggerGraph.Edge("event", "action", "next")));
            var control = new QuestPresentation.Control("probe", "button", "Probe", 10, 10, 100, 24, "",
                    Map.of(), graph);
            Files.writeString(path, QuestPresentationDocuments.write(Files.readString(path),
                    new QuestPresentation(false, 395, 275, List.of(control))));
        }
        QuestRepository.reload();
        require(QuestRepository.errors().isEmpty(), "Fixture parse failed: " + QuestRepository.errors());
        var a = QuestRepository.find(A);
        var b = QuestRepository.find(B);
        var data = QuestProgressData.get(player.server);
        data.trackOnly(player.getUUID(), B);
        String result = QuestFocus.toggle(player, "journal_focus_group");
        require(result.equals("focus_on"), "Focus activation failed: " + result);
        require(QuestRuntime.tracked(player).equals(List.of(A)), "Focus HUD not isolated");
        require(data.tracked(player.getUUID()).equals(List.of(B)), "Focus destroyed previous tracking");
        QuestEventGraphs.fire(player, "jump", "focus_probe");
        require(player.getTags().contains(A) && !player.getTags().contains(B), "Other event graph executed");
        QuestConditions.record(player, "event", "focus_test", 1);
        require(QuestConditions.progress(player, a).current() == 1
                && QuestConditions.progress(player, b).current() == 0, "Other counter advanced");
        require(QuestRuntime.activate(player, B).equals("focus_active"), "Other activation was allowed");
        require(QuestRuntime.trigger(player, B, "complete", false).equals("focus_active"), "Other completion allowed");
        require(QuestRuntime.toggleTracking(player, B).equals("focus_active"), "Other tracking allowed");
        require(QuestRuntime.toggleTracking(player, "journal_focus_sibling").equals("tracked")
                        && QuestFocus.current(player).equals("journal_focus_sibling"),
                "Manual selection within a focused group failed");
        require(QuestRuntime.toggleTracking(player, A).equals("tracked")
                        && QuestFocus.current(player).equals(A),
                "Could not switch focus back to the first group task");
        var restored = QuestProgressData.load(data.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
        require(restored.focused(player.getUUID()).equals(A)
                        && restored.focusGroup(player.getUUID()).equals("journal_focus_group")
                        && restored.tracked(player.getUUID()).equals(List.of(B)),
                "Focus/tracking persistence failed");
        require(restored.focused(UUID.randomUUID()).isEmpty(), "Focus leaked between players");
        var other = new ServerPlayer(player.server, player.server.overworld(),
                new GameProfile(UUID.randomUUID(), "FocusOther"), ClientInformation.createDefault());
        require(QuestFocus.current(other).isEmpty() && QuestFocus.allows(other, b), "Player focus isolation failed");
        var snapshot = new QuestNetwork.ProgressSnapshotPayload(0, 1, false, QuestRuntime.snapshot(player),
                QuestRuntime.tracked(player), QuestFocus.group(player));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            require(QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.decode(buffer).equals(snapshot),
                    "Focused state codec failed");
        } finally { buffer.release(); }
        require(QuestFocus.toggle(player, "journal_focus_group").equals("focus_off"), "Focus exit failed");
        require(QuestRuntime.tracked(player).equals(List.of(B)), "Tracking was not restored");
        QuestEventGraphs.fire(player, "jump", "focus_probe");
        require(player.getTags().contains(B), "Other graph did not resume");
        QuestConditions.record(player, "event", "focus_test", 1);
        require(QuestConditions.progress(player, b).current() == 1, "Counter did not resume");
        require(QuestRuntime.trigger(player, B, "complete", false).equals("completed")
                        && QuestRuntime.tracked(player).equals(List.of("journal_focus_followup")),
                "Ordinary tracking did not advance to its newly unlocked related task");
        QuestRuntime.resetTrusted(player, B);
        data.trackOnly(player.getUUID(), B);
        require(QuestFocus.toggle(player, "journal_focus_group").equals("focus_on"), "Focus reactivation failed");
        QuestConditions.record(player, "event", "focus_test", 2);
        QuestRuntime.evaluate(player);
        require(QuestRuntime.completed(player, a) && !QuestRuntime.completed(player, b)
                        && QuestFocus.current(player).equals("journal_focus_sibling")
                        && QuestRuntime.tracked(player).equals(List.of("journal_focus_sibling")),
                "Focused group did not advance to the next available quest: group=" + QuestFocus.group(player)
                        + ", current=" + QuestFocus.current(player) + ", tracked=" + QuestRuntime.tracked(player)
                        + ", sibling unlocked=" + QuestRuntime.unlocked(QuestProgressData.get(player.server), player,
                        QuestRepository.find("journal_focus_sibling")));
        QuestRuntime.resetTrusted(player, A);
        require(QuestFocus.group(player).equals("journal_focus_group")
                        && QuestFocus.current(player).equals("journal_focus_sibling"),
                "Resetting a group task unexpectedly lost the active group target");
        require(QuestFocus.toggle(player, "journal_focus_group").equals("focus_off"),
                "Group focus could not be disabled");

        var icon = new ListIconConfig("", "", true, false, 0.9F, 16, 16, 1, 0, "legendary");
        require(gson.fromJson(gson.toJson(icon), ListIconConfig.class).equals(icon), "Icon JSON round-trip failed");
        require(new ListIconConfig("", "", true, false, 1, 14, 14, 0, 0).trackingStyle().equals("auto"),
                "Legacy icon constructor changed");
        String markup = QuestDocuments.metadata("""
                <quest id="journal_style_edit" title="Style" type="world"><description>Test</description>
                <script>const keep = "a < b";</script></quest>
                """, "Style", "", "Test", true, ListIconConfig.leading(""), icon);
        markup = QuestDocuments.taskFlags(markup, false, false, true, true);
        markup = QuestDocuments.listAccent(markup, "blue");
        var parsed = QuestRepository.parseDefinition(Path.of("test.xml"), markup);
        require(parsed.trailingIcon().equals(icon) && parsed.listAccent().equals("blue")
                        && markup.contains("const keep = \"a < b\";"), "Markup settings/script preservation failed");
        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, "journal_style_" + System.currentTimeMillis(), "Style", "Test",
                "", "", "world", "", 1, "", "", "", 1, "manual", "",
                ListIconConfig.leading(""), icon, List.of(), List.of(), HotEventData.empty(),
                CompletionPolicy.defaults(), true, false, false, false, true, false,
                List.of(), JourneyAppearance.empty(), LegendaryData.empty(), true, "blue");
        var created = QuestAuthoring.create(player, request);
        require(created.successful(), "Authoring create failed: " + created);
        var authored = QuestRepository.find(request.id());
        require(!authored.focusAvailable() && authored.listAccent().equals("blue")
                && authored.trailingIcon().trackingStyle().equals("legendary"), "Authoring settings were not saved");
        var draftJson = gson.toJsonTree(request).getAsJsonObject();
        draftJson.addProperty("focusAvailable", false);
        draftJson.addProperty("listAccent", "none");
        draftJson.getAsJsonObject("trailingIcon").addProperty("trackingStyle", "main");
        var edit = gson.fromJson(draftJson, QuestAuthoring.Request.class);
        var edited = QuestAuthoring.edit(player, edit,
                QuestRepository.fingerprint(Files.readString(QuestRepository.fileOf(request.id()))), false, false, false);
        require(edited.successful(), "Authoring edit failed: " + edited);
        var reloaded = QuestRepository.find(request.id());
        require(!reloaded.focusAvailable() && reloaded.listAccent().equals("none")
                && reloaded.trailingIcon().trackingStyle().equals("main"), "Edited settings did not persist");
        var catalog = xtdpotato.xero_quest_journal.core.network.QuestCatalog.decode(
                xtdpotato.xero_quest_journal.core.network.QuestCatalog.serverJson());
        var publicQuest = catalog.quests().stream().filter(q -> q.id().equals(request.id())).findFirst().orElseThrow();
        require(publicQuest.trailingIcon().equals(reloaded.trailingIcon())
                && publicQuest.listAccent().equals(reloaded.listAccent())
                && catalog.groups().stream().anyMatch(groupDef -> groupDef.id().equals("journal_focus_group")
                && groupDef.focusAvailable()),
                "Catalog lost appearance/group focus settings");
        require(QuestListAccent.resolve("auto", "auto", "main", true).equals("gold")
                && QuestListAccent.resolve("auto", "auto", "legendary", true).equals("blue")
                && QuestListAccent.resolve("auto", "auto", "world", true).equals("none")
                && QuestListAccent.resolve("none", "gold", "main", true).equals("none")
                && QuestListAccent.resolve("auto", "blue", "main", true).equals("blue"),
                "List accent inheritance failed");
        for (String type : List.of("main", "legendary", "world", "commission"))
            require(QuestTrackingStyle.resolve("auto", type).equals(type)
                    && QuestTrackingStyle.resolve("world", type).equals("world"), "Tracking override failed");
        require(QuestTrackingIcons.waveProgress(200_000_000L, 0) > 0
                && QuestTrackingIcons.waveProgress(200_000_000L, 1) < 0
                && QuestTrackingIcons.waveProgress(700_000_000L, 0) > 0
                && QuestTrackingIcons.waveProgress(700_000_000L, 1) > 0
                && QuestTrackingIcons.waveProgress(2_000_000_000L, 0) < 0
                && QuestTrackingIcons.waveProgress(2_000_000_000L, 1) < 0
                && QuestTrackingIcons.waveAlpha(0.8) < QuestTrackingIcons.waveAlpha(0.3),
                "Two-wave animation timing/fade failed");
        for (long t = 0; t <= 2_800_000_000L; t += 100_000_000L)
            require(JournalListButton.arrowOffset(t, false) >= 0 && JournalListButton.arrowOffset(t, false) <= 4
                    && JournalListButton.arrowOffset(t, true) == 0, "Arrow boundary/reduced-motion failed");
        QuestFocus.toggle(player, "journal_focus_group");
    }

    private static void open(Minecraft game, String id) throws Exception {
        game.setScreen(new QuestScreen());
        set(game.screen, "selectedId", id);
        set(game.screen, "groupId", ClientQuestDefinitions.groupOf(id));
        game.screen.init(game, game.screen.width, game.screen.height);
        EditorRegression.reveal(game, id);
    }
    private static void resize(Minecraft game, int width, int height) {
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), width, height);
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), width - 20, height - 20);
        game.resizeDisplay();
    }
    private static StyledButton find(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing " + id));
    }
    private static JournalListButton row(Minecraft game, String id) { return (JournalListButton) find(game, "quest-" + id); }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static Object invoke(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static void checkArrow(Minecraft game, JournalListButton row) throws Exception {
        int white = 0;
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            for (int x = row.getX() - 7; x < row.getX() - 1; x++)
                for (int y = row.getY() + 7; y < row.getBottom() - 7; y++) {
                    int color = image.getPixelRGBA((int) ((x + 0.5) * image.getWidth() / game.screen.width),
                            (int) ((y + 0.5) * image.getHeight() / game.screen.height));
                    if ((color & 0xFFFFFF) == 0xFFFFFF) white++;
                }
        }
        require(white >= 4, "Selection arrow did not render outside row");
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(Path.of("..", "build", "verification"));
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void checkAtlas(Minecraft game) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            int[] expected = {0xFFEB30, 0x9265D6, 0x9DF2FF, 0x9DF2FF};
            for (int i = 0; i < expected.length; i++) {
                int abgr = image.getPixelRGBA((int) ((87.5 + i * 150) * image.getWidth() / game.screen.width),
                        (int) (88.5 * image.getHeight() / game.screen.height));
                int rgb = (abgr & 255) << 16 | abgr & 0xFF00 | abgr >>> 16 & 255;
                require(rgb == expected[i], "Tracking emblem core missing: " + i + ", rgb=" + Integer.toHexString(rgb));
            }
        }
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message + " (journal stage " + stage + ")");
    }

    private static void checkSpringSettle() throws Exception {
        Class<?> spring = Class.forName("xtdpotato.xero_quest_journal.client.DampedSpring");
        var advance = spring.getDeclaredMethod("advance", double.class, double.class,
                double.class, double.class, double.class);
        advance.setAccessible(true);
        Object state = null;
        double previous = -40;
        double position = previous;
        double velocity = 0;
        Class<?> stateType = spring.getDeclaredClasses()[0];
        var readPosition = stateType.getDeclaredMethod("position");
        var readVelocity = stateType.getDeclaredMethod("velocity");
        readPosition.setAccessible(true);
        readVelocity.setAccessible(true);
        for (int frame = 0; frame < 144; frame++) {
            state = advance.invoke(null, position, velocity, 0.0, 1.0 / 144, 15.0);
            position = (double) readPosition.invoke(state);
            velocity = (double) readVelocity.invoke(state);
            require(position <= previous + 2.0 && position >= previous,
                    "Boundary spring was not continuous/monotonic at frame " + frame);
            previous = position;
        }
        require(Math.abs(position) < 0.01 && Math.abs(velocity) < 0.1,
                "Boundary spring did not settle smoothly");
    }

    private static final class Atlas extends Screen {
        private final long time;
        Atlas(long time) { super(Component.literal("Tracking verification")); this.time = time; }
        @Override public void render(GuiGraphics g, int mx, int my, float tick) {
            g.fill(0, 0, width, height, 0xFF414C5D);
            String[] types = {"main", "commission", "legendary", "world"};
            for (int i = 0; i < types.length; i++) {
                int x = 35 + i * 150;
                g.drawString(font, types[i], x, 18, 0xFFECE6DA, false);
                for (int selected = 0; selected < 2; selected++) {
                    int y = 50 + selected * 155;
                    g.fill(x - 10, y - 10, x + 115, y + 120, selected == 0 ? 0xFF414C5D : 0xFFECE6DA);
                    g.enableScissor(x - 10, y - 10, x + 115, y + 120);
                    QuestTrackingIcons.renderAt(g, types[i], true, x + 24, y + 10, 56, 1, false, time);
                    QuestTrackingIcons.renderAt(g, types[i], false, x + 14, y + 85, 24, 1, false, time);
                    QuestTrackingIcons.renderAt(g, types[i], true, x + 63, y + 90, 14, 1, true, time);
                    g.disableScissor();
                }
            }
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
