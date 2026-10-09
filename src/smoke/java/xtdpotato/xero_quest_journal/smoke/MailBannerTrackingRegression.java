package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.MailTemplateStore;
import xtdpotato.xero_quest_journal.core.mailbox.*;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import xtdpotato.xero_quest_journal.core.io.QuestVariableStore;
import xtdpotato.xero_quest_journal.core.runtime.QuestControlData;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Real-client regression for banner delivery/navigation and shared status tips; camera math uses vanilla axes. */
final class MailBannerTrackingRegression {
    private static final String ID = "banner_regression";
    private static final String TASK = "tracking_hud_world";
    private static final String IMAGE = "minecraft:textures/gui/title/background/panorama_0.png";
    private static final MailBannerTiming TIMING = new MailBannerTiming("countdown", 259200, false, "");
    private static Vec3 destination;
    private static int stage, wait;
    private static CompletableFuture<Void> work;
    private static MailboxScreen inbox;
    private static MailComposeScreen compose;
    private static final Component TIP = Component.translatable("screen.xero_quest_journal.mailbox.tips.all_claimed");

    private static final class TestCamera extends Camera {
        void orient(float yaw, float pitch, float roll) { setRotation(yaw, pitch, roll); }
        void position(Vec3 position) { setPosition(position); }
    }

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                checkProjection();
                game.getLanguageManager().setSelected("zh_cn");
                game.reloadResourcePacks();
                game.options.guiScale().set(2);
                resize(game, 1920, 1080);
                ClientPreferences.get().reducedMotion = true;
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, true);
                    var draft = new MailDraft(ID, "Xero", "Mail banner", "Banner body\nSecond line", "",
                            false, List.of(), List.of(), 86400, false, IMAGE, TASK, TIMING);
                    require(MailClipboardCodec.parse(MailClipboardCodec.command("@s", draft)).draft().equals(draft),
                            "Bundle lost banner/target");
                    var legacy = new Gson().fromJson("{\"id\":\"legacy_banner\",\"sender\":\"Xero\","
                            + "\"subject\":\"Legacy\",\"body\":\"Body\",\"icon\":\"\",\"treasured\":false,\"rewards\":[]}",
                            MailDraft.class);
                    require(legacy.banner().isEmpty() && legacy.targetTaskId().isEmpty(), "Legacy JSON not compatible");
                    MailTemplateStore.save(player, draft);
                    require(draft.equals(MailTemplateStore.find(ID)), "Template lost banner/target");
                    QuestRewards.execute(player, new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward(
                            "mail", ID, 1));
                    var mail = MailboxRuntime.snapshot(player).stream().filter(m -> m.id().equals(ID)).findFirst().orElseThrow();
                    require(mail.banner().equals(IMAGE) && mail.targetTaskId().equals(TASK)
                                    && mail.bannerTiming().equals(TIMING)
                                    && mail.bannerRemainingSeconds() > 259190, "Reward template lost banner/timing");
                    String variableId = "globe.bannerSeconds";
                    String previousVariable = QuestVariableStore.find(variableId) == null ? "" : variableId;
                    var variable = QuestVariableStore.save(player, TASK, previousVariable, variableId, "7200", false);
                    require(variable.successful(), "Variable fixture failed: " + variable.error()
                            + ", task=" + QuestRepository.find(TASK));
                    MailboxRuntime.sendIfAbsent(player, ID + "_variable", "Xero", "Variable time", "Body",
                            0, false, "", true, List.of(), IMAGE, TASK,
                            new MailBannerTiming("variable", 0, false, variableId));
                    require(findView(player, ID + "_variable").bannerRemainingSeconds() == 7200, "Variable initial value lost");
                    QuestControlData.get(player).setVariable(player, variableId, "3600");
                    require(findView(player, ID + "_variable").bannerRemainingSeconds() == 3600, "Variable live value not used");
                    QuestControlData.get(player).setVariable(player, variableId, "invalid");
                    require(findView(player, ID + "_variable").bannerRemainingSeconds() == -1, "Invalid variable caused unsafe time");
                    QuestControlData.get(player).setVariable(player, variableId, "3600");
                    MailboxRuntime.sendIfAbsent(player, ID + "_game", "Xero", "Game time", "Body", 0, false, "", true,
                            List.of(), IMAGE, TASK, new MailBannerTiming("countdown", 3600, true, ""));
                    require(findView(player, ID + "_game").bannerRemainingSeconds() == 3600, "Game countdown uses mail clock");
                    String hash = MailboxRuntime.fingerprint(mail);
                    var edit = new QuestNetwork.MailboxSendPayload(1, mail.recipient(), ID, mail.sender(),
                            mail.subject(), mail.body(), "", false, 86400, false, List.of(), IMAGE, "", TIMING);
                    require(MailboxRuntime.edit(player, edit, hash, true).equals("edited"), "Banner edit failed");
                    var changed = MailboxRuntime.snapshot(player).stream().filter(m -> m.id().equals(ID)).findFirst().orElseThrow();
                    require(!hash.equals(MailboxRuntime.fingerprint(changed)), "Fingerprint ignored task destination");
                    var restore = new QuestNetwork.MailboxSendPayload(2, mail.recipient(), ID, mail.sender(),
                            mail.subject(), mail.body(), "", false, 86400, false, List.of(), IMAGE, TASK, TIMING);
                    require(MailboxRuntime.edit(player, restore, MailboxRuntime.fingerprint(changed), true).equals("edited"),
                            "Destination restore failed");
                    var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
                    try {
                        QuestNetwork.MailboxSendPayload.STREAM_CODEC.encode(buffer, restore);
                        require(restore.equals(QuestNetwork.MailboxSendPayload.STREAM_CODEC.decode(buffer)), "Send codec lost banner");
                        buffer.clear();
                        var snapshot = new QuestNetwork.MailboxSnapshotPayload(MailboxRuntime.snapshot(player));
                        QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
                        require(snapshot.equals(QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.decode(buffer)), "Snapshot lost banner");
                    } finally { buffer.release(); }
                    MailboxRuntime.markRead(player, ID);
                    MailboxRuntime.toggleStarred(player, ID);
                    var data = MailboxData.get(server);
                    var restored = MailboxData.load(data.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
                    require(restored.messages(player.getUUID()).equals(data.messages(player.getUUID())), "NBT lost banner");
                    var stored = data.messages(player.getUUID()).stream().filter(m -> m.id().equals(ID)).findFirst().orElseThrow();
                    require(stored.banner().equals(IMAGE) && stored.targetTaskId().equals(TASK)
                            && stored.bannerTiming().equals(TIMING), "Read/star lost banner");
                    destination = new Vec3(player.getX() + 8, player.getEyeY(), player.getZ() + 40);
                    try {
                        Files.writeString(QuestRepository.root().resolve("banner_location.json"), new Gson().toJson(
                                java.util.Map.of("id", "banner_location", "title", "Direction regression", "type", "world",
                                        "defaultUnlocked", true, "trackable", true, "triggers", List.of(java.util.Map.of(
                                                "type", "location", "item", "coords|minecraft:overworld|" + destination.x
                                                        + "|" + destination.y + "|" + destination.z + "|1", "count", 1)))));
                        QuestRepository.reload();
                    } catch (Exception invalid) { throw new IllegalStateException(invalid); }
                }, server);
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                ClientMailbox.request();
                MailArrivalToast.clear();
            }
            case 2 -> {
                if (ClientMailbox.messages().stream().noneMatch(m -> m.id().equals(ID))) return false;
                inbox = new MailboxScreen(null, ID);
                game.setScreen(inbox);
            }
            case 3 -> {
                capture(game, "mail-banner-desktop");
                int left = (int) invoke(inbox, "split") + 18;
                int x = left + 20, y = 68;
                inbox.mouseClicked(x, y, 0);
                require(game.screen == inbox, "Banner navigated on press");
                inbox.mouseReleased(x, y, 0);
                require(game.screen instanceof QuestScreen && field(game.screen, "selectedId").equals(TASK),
                        "Banner did not open selected task");
            }
            case 4 -> {
                game.screen.onClose();
            }
            case 5 -> {
                require(game.screen == inbox, "Task did not return to mail");
                var constructor = MailComposeScreen.class.getDeclaredConstructor(MailboxScreen.class,
                        MailboxRuntime.View.class, boolean.class);
                constructor.setAccessible(true);
                compose = constructor.newInstance(inbox, ClientMailbox.messages().stream()
                        .filter(m -> m.id().equals(ID)).findFirst().orElseThrow(), false);
                game.setScreen(compose);
            }
            case 6 -> {
                require(field(compose, "bannerValue").equals(IMAGE) && field(compose, "targetTaskId").equals(TASK),
                        "Editor lost metadata");
                button(compose, "mail-compose-banner").onPress();
            }
            case 7 -> {
                require(game.screen instanceof MailBannerScreen, "Banner editor did not open");
                require(field(game.screen, "timingMode").equals("countdown"), "Editor lost timing");
                capture(game, "mail-banner-settings-desktop");
                button(game.screen, "mail-banner-task-clear").onPress();
                button(game.screen, "mail-banner-apply").onPress();
            }
            case 8 -> {
                require(game.screen == compose && field(compose, "targetTaskId").equals(""), "Clear did not persist");
                resize(game, 854, 480);
            }
            case 9 -> {
                for (var child : compose.children()) if (child instanceof AbstractWidget widget)
                    require(widget.getRight() <= compose.width && widget.getBottom() <= compose.height,
                            "Compose control outside compact viewport: " + widget.getMessage().getString());
                capture(game, "mail-banner-compose-compact");
                game.setScreen(inbox);
                XeroTips.clear();
                XeroTips.show(TIP);
            }
            case 10 -> {
                capture(game, "mail-status-toast-compact");
                checkCapsule(game);
                resize(game, 1920, 1080);
                XeroTips.clear();
                XeroTips.show(TIP);
            }
            case 11 -> {
                capture(game, "mail-status-toast-desktop");
                checkCapsule(game);
                XeroTips.clear();
                game.setScreen(QuestScreen.task(inbox, TASK));
                setField(game.screen, "editMode", false);
                setField(game.screen, "actionMessage", "reset");
            }
            case 12 -> {
                capture(game, "task-status-toast-desktop");
                XeroTips.clear();
                game.setScreen(null);
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    QuestProgressData.get(server).trackOnly(player.getUUID(), "banner_location");
                }, server);
            }
            case 13 -> {
                if (!work.isDone()) return false;
                work.join();
                PacketDistributor.sendToServer(new QuestNetwork.ProgressRequestPayload(-1));
                QuestUnlockToast.clear();
                QuestCompletionToast.clear();
                game.player.setYRot(0);
                game.player.setXRot(0);
            }
            case 14 -> {
                if (!ClientQuestProgress.isTracked("banner_location")) return false;
                requireRenderedMarker(game, false);
                capture(game, "tracking-location-left");
                game.player.setYRot(180);
                game.player.setXRot(0);
            }
            case 15 -> {
                requireRenderedMarker(game, true);
                capture(game, "tracking-location-behind-right");
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    for (int i = 0; i < 40; i++)
                        MailboxRuntime.sendIfAbsent(player, "full_height_" + i, "Xero",
                                "Full height " + i, "Mailbox list fixture", 0, List.of());
                }, server);
            }
            case 16 -> {
                if (!work.isDone()) return false;
                work.join();
                ClientMailbox.request();
                game.options.guiScale().set(1);
                resize(game, 2048, 1200);
            }
            case 17 -> {
                if (ClientMailbox.messages().stream().filter(m -> m.id().startsWith("full_height_")).count() < 40)
                    return false;
                MailArrivalToast.clear();
                inbox = new MailboxScreen();
                game.setScreen(inbox);
            }
            case 18 -> {
                require((int) invoke(inbox, "listBottom") == inbox.height - 42, "Mailbox retains fixed eight-row cap");
                var rows = inbox.children().stream().filter(MailRowButton.class::isInstance)
                        .map(MailRowButton.class::cast).filter(row -> row.visible).toList();
                require(rows.size() > 20 && rows.getLast().getY() > inbox.height - 100,
                        "Mailbox did not fill available height at GUI scale 1");
                require((double) invoke(inbox, "maxRowScroll") > 0, "Full-height list lost scroll range");
                capture(game, "mailbox-full-height-gui-scale-1");
                return true;
            }
            default -> throw new AssertionError("Unknown banner stage " + stage);
        }
        stage++;
        wait = 16;
        return false;
    }

    private static MailboxRuntime.View findView(net.minecraft.server.level.ServerPlayer player, String id) {
        return MailboxRuntime.snapshot(player).stream().filter(mail -> mail.id().equals(id)).findFirst().orElseThrow();
    }

    private static void requireRenderedMarker(Minecraft game, boolean behind) {
        int width = game.getWindow().getGuiScaledWidth(), height = game.getWindow().getGuiScaledHeight();
        var projected = QuestMarkerProjection.project(game.gameRenderer.getMainCamera(), destination,
                width, height, game.options.fov().get());
        require(behind ? !projected.onScreen() && projected.x() > width / 2
                        : projected.onScreen() && projected.x() < width / 2,
                "Actual camera left/right differs from projection");
        int matches = 0;
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            double sx = image.getWidth() / (double) width, sy = image.getHeight() / (double) height;
            for (int y = projected.y() - 17; y < projected.y() + 17; y++)
                for (int x = projected.x() - 17; x < projected.x() + 17; x++) {
                    int color = image.getPixelRGBA((int) ((x + .5) * sx), (int) ((y + .5) * sy));
                    int r = color & 255, g = color >>> 8 & 255, b = color >>> 16 & 255;
                    if (Math.abs(r - 157) <= 3 && Math.abs(g - 242) <= 3 && Math.abs(b - 255) <= 3) matches++;
                }
        }
        require(matches >= 3, "Actual tracking marker missing at projected side: " + matches);
    }

    private static void checkProjection() {
        var camera = new TestCamera();
        camera.position(new Vec3(-768, 154, -174));
        for (float yaw : new float[]{0, 90, 180, -90, 315}) {
            for (float pitch : new float[]{0, 45, -45, 89, -89}) {
                camera.orient(yaw, pitch, 0);
                Vec3 ahead = new Vec3(camera.getLookVector()).scale(40);
                Vec3 right = new Vec3(camera.getLeftVector()).scale(-8);
                Vec3 up = new Vec3(camera.getUpVector()).scale(8);
                Vec3 origin = camera.getPosition();
                var front = QuestMarkerProjection.project(camera, origin.add(ahead), 960, 540, 70);
                require(front.onScreen() && Math.abs(front.x() - 480) < 2 && Math.abs(front.y() - 270) < 2,
                        "Forward not centered at yaw/pitch " + yaw + "/" + pitch);
                require(QuestMarkerProjection.project(camera, origin.add(ahead).add(right), 960, 540, 70).x() > 480,
                        "Camera-right mirrored");
                require(QuestMarkerProjection.project(camera, origin.add(ahead).subtract(right), 960, 540, 70).x() < 480,
                        "Camera-left mirrored");
                require(QuestMarkerProjection.project(camera, origin.add(ahead).add(up), 960, 540, 70).y() < 270,
                        "Camera-up inverted");
                var behind = QuestMarkerProjection.project(camera, origin.subtract(ahead).add(right), 960, 540, 70);
                require(!behind.onScreen() && behind.x() > 480, "Behind-right mirrored");
            }
        }
        camera.orient(0, 0, 0);
        require(QuestMarkerProjection.project(camera, camera.getPosition().add(10, 0, 40), 960, 540, 70).x() < 480,
                "East must be left when facing south");
        camera.orient(180, 0, 0);
        require(QuestMarkerProjection.project(camera, camera.getPosition().add(10, 0, -40), 960, 540, 70).x() > 480,
                "East must be right when facing north / reversed third person");
    }

    private static void checkCapsule(Minecraft game) {
        int width = game.getWindow().getGuiScaledWidth(), height = game.getWindow().getGuiScaledHeight();
        var layout = StatusToastRenderer.layout(game.font, TIP, width - 24, height - 84);
        int x = width / 2 - layout.width() / 2, y = height - layout.height() - 54;
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            double sx = image.getWidth() / (double) width, sy = image.getHeight() / (double) height;
            int interior = image.getPixelRGBA((int) ((width / 2 + .5) * sx), (int) ((y + 3.5) * sy)) & 0xFFFFFF;
            int corner = image.getPixelRGBA((int) ((x + .5) * sx), (int) ((y + .5) * sy)) & 0xFFFFFF;
            require((interior & 255) >= 254 && (interior >>> 8 & 255) >= 254
                            && (interior >>> 16 & 255) >= 254
                            && ((corner & 255) < 254 || (corner >>> 8 & 255) < 254 || (corner >>> 16 & 255) < 254),
                    "Status toast is not an opaque white capsule: interior=" + Integer.toHexString(interior)
                            + ", corner=" + Integer.toHexString(corner));
        }
    }

    private static StyledButton button(net.minecraft.client.gui.screens.Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void setField(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static Object invoke(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }
    private static void resize(Minecraft game, int width, int height) {
        GLFW.glfwSetWindowSize(game.getWindow().getWindow(), width, height);
        game.resizeDisplay();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        var path = Path.of("..", "build", "verification", name + ".png");
        Files.createDirectories(path.getParent());
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(path); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
