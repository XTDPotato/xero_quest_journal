package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestPresentationDocuments;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestUiThemeNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class OverviewRegression {
    private static final String QUEST = "overview_background_regression";
    private static final String IMAGE = "minecraft:textures/gui/title/background/panorama_1.png";
    private static final String DEFAULT = "minecraft:textures/gui/title/background/panorama_2.png";
    private static int stage, wait;
    private static CompletableFuture<Void> work;
    private static QuestUiTheme originalTheme;
    private static boolean originalMotion;
    private static QuestScreen screen;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                originalMotion = ClientPreferences.get().reducedMotion;
                ClientPreferences.get().reducedMotion = true;
                game.options.pauseOnLostFocus = false;
                game.options.guiScale().set(2);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1440, 900);
                game.resizeDisplay();
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    try { verifyServer(server.getPlayerList().getPlayer(game.player.getUUID())); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }, server);
            }
            case 1 -> {
                if (!work.isDone()) return false;
                work.join();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new xtdpotato.xero_quest_journal.core.network.QuestNetwork.ProgressRequestPayload(-1));
            }
            case 2 -> {
                if (!ClientQuestProgress.overviewImage().equals(IMAGE)
                        || ClientQuestDefinitions.find(QUEST) == null) return false;
                screen = QuestScreen.overview(null);
                game.setScreen(screen);
            }
            case 3 -> {
                checkLayout(game);
                capture(game, "overview-background-player");
                button(game, "overview-background");
                require(game.screen instanceof OverviewBackgroundScreen, "Background editor unavailable");
            }
            case 4 -> {
                capture(game, "overview-background-editor");
                button(game, "overview-background-pick");
                require(game.screen instanceof QuestScreen, "Background picker did not open");
                var fields = (Map<?, ?>) field(game.screen, "formFields");
                ((EditBox) fields.get("icon_picker.manual")).setValue(DEFAULT);
                button(game, "icon-apply");
            }
            case 5 -> {
                require(game.screen instanceof OverviewBackgroundScreen && field(game.screen, "source").equals(DEFAULT),
                        "Image picker lost background draft");
                button(game, "overview-background-save");
            }
            case 6 -> {
                if (!(game.screen instanceof QuestScreen)) return false;
                require(ClientQuestProgress.uiTheme().overviewImage().equals(DEFAULT)
                                && ClientQuestProgress.overviewImage().equals(IMAGE),
                        "Saving default replaced player's independent background");
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    QuestEventGraphs.click(player, QUEST, "reset");
                    require(QuestControlData.get(player).overviewImage(player).isEmpty(), "Reset node did not clear override");
                }, server);
            }
            case 7 -> {
                if (!work.isDone()) return false;
                work.join();
                if (!ClientQuestProgress.overviewImage().equals(DEFAULT)) return false;
                require(game.screen == screen, "Live background update replaced overview screen");
                capture(game, "overview-background-default");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 480);
                game.resizeDisplay();
            }
            case 8 -> {
                checkLayout(game);
                capture(game, "overview-background-small");
                button(game, "overview-anecdotes");
                require(game.screen instanceof AnecdoteScreen,
                        "Anecdote Chronicle category button did not open the chronicle");
                game.setScreen(screen);
                button(game, "overview-journal");
                require(game.screen instanceof QuestScreen && !(boolean) field(game.screen, "overviewMode"),
                        "Guide notes navigation failed");
                game.player.setPermissionLevel(0);
                game.setScreen(QuestScreen.overview(null));
                require(game.screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).noneMatch(b -> b.styleId().equals("overview-background")),
                        "Background editor leaked to non-author");
                var server = game.getSingleplayerServer();
                work = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestUiThemeNetwork.apply(player, originalTheme), "Unable to restore original theme");
                    QuestControlData.get(player).overviewImage(player, "");
                    QuestUiThemeNetwork.send(player);
                }, server);
            }
            case 9 -> {
                if (!work.isDone()) return false;
                work.join();
                ClientPreferences.get().reducedMotion = originalMotion;
                return true;
            }
            default -> throw new AssertionError("Unknown overview stage");
        }
        stage++;
        wait = 15;
        return false;
    }

    private static void verifyServer(ServerPlayer player) throws Exception {
        originalTheme = QuestConfig.defaults().uiTheme;
        var gson = new Gson();
        require(gson.fromJson("{\"colors\":{}}", QuestUiTheme.class).overviewImage().isEmpty(),
                "Legacy theme configuration failed");
        require(TriggerNodeLibrary.entries("set_overview_background").getFirst().data().get("scope").equals("player"),
                "Background node not available in palette");
        for (String category : List.of("set_property", "set_task_property", "mail_set_property"))
            require(!TriggerNodeLibrary.entries(category).isEmpty(), "Existing property category lost");
        var controls = List.of(
                control("player", "player", IMAGE, true),
                control("server", "server", DEFAULT, false),
                control("reset", "player", "", false));
        var presentation = new QuestPresentation(true, 395, 275, controls);
        String source = QuestPresentationDocuments.write("""
                {"id":"overview_background_regression","title":"A Journey Begins","type":"guide",
                 "recommended":true,"icon":"minecraft:textures/gui/title/background/panorama_2.png"}
                """, presentation);
        Files.writeString(QuestRepository.root().resolve(QUEST + ".json"), source);
        QuestRepository.reload();
        var quest = QuestRepository.find(QUEST);
        require(quest != null && quest.presentation().equals(presentation), "Background graph failed source round-trip");
        var data = QuestControlData.get(player);
        QuestEventGraphs.click(player, QUEST, "player");
        require(data.overviewImage(player).equals(IMAGE), "Player background node did not execute");
        var stranger = new ServerPlayer(player.server, player.server.overworld(),
                new GameProfile(UUID.randomUUID(), "OverviewOther"), ClientInformation.createDefault());
        require(data.overviewImage(stranger).isEmpty(), "Background override crossed player boundary");
        CompoundTag saved = data.save(new CompoundTag(), player.registryAccess());
        require(saved.getCompound("players").getCompound(player.getUUID().toString())
                .getString("overviewImage").equals(IMAGE), "Player background was not persisted");
        data.overviewImage(player, DEFAULT);
        QuestEventGraphs.reset(player, quest);
        QuestEventGraphs.click(player, QUEST, "player");
        require(data.overviewImage(player).equals(DEFAULT), "Once-only background node ran twice");
        QuestEventGraphs.click(player, QUEST, "server");
        require(QuestConfig.defaults().uiTheme.overviewImage().equals(DEFAULT)
                        && QuestConfig.reload() && QuestConfig.defaults().uiTheme.overviewImage().equals(DEFAULT),
                "Server default background did not save/reload");
        require(QuestConfig.defaults().uiTheme.with("accent", "#ffffff").overviewImage().equals(DEFAULT),
                "Theme color change discarded background");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            var state = new QuestUiThemeNetwork.State(gson.toJson(QuestConfig.defaults().uiTheme), IMAGE);
            QuestUiThemeNetwork.State.CODEC.encode(buffer, state);
            require(QuestUiThemeNetwork.State.CODEC.decode(buffer).equals(state), "Theme state codec lost image");
        } finally { buffer.release(); }
        boolean rejected = false;
        try { QuestUiTheme.validateOverviewImage("../secrets.png"); } catch (IllegalArgumentException invalid) { rejected = true; }
        require(rejected, "Unsafe image source accepted");
        data.overviewImage(player, IMAGE);
        QuestUiThemeNetwork.send(player);
    }

    private static QuestPresentation.Control control(String id, String scope, String image, boolean once) {
        return new QuestPresentation.Control(id, "button", id, 10, 10, 100, 24, "", Map.of(),
                new TriggerGraph(List.of(
                        new TriggerGraph.Node("event", "event", 0, 0, Map.of("event", "click", "cooldown", "1")),
                        new TriggerGraph.Node("image", "set_overview_background", 160, 0,
                                Map.of("scope", scope, "source", image, "once", Boolean.toString(once)))),
                        List.of(new TriggerGraph.Edge("event", "image", "next"))));
    }

    private static void checkLayout(Minecraft game) throws Exception {
        for (var child : game.screen.children()) if (child instanceof AbstractWidget widget) {
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= game.screen.width
                    && widget.getBottom() <= game.screen.height, "Overview widget outside viewport");
        }
        var feature = find(game, "overview-feature");
        var first = find(game, "overview-main");
        var last = find(game, "overview-world");
        require(first.getY() == feature.getY() && last.getBottom() == feature.getBottom()
                        && first.getX() > feature.getRight(), "Overview columns do not align");
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            double sx = image.getWidth() / (double) game.screen.width;
            double sy = image.getHeight() / (double) game.screen.height;
            int pixels = 0;
            int knob = Math.max(8, Math.min(14, last.getHeight() - 10));
            int chevronX = last.getRight() - knob - 8 + knob / 2 - 1;
            for (int y = (int) ((last.getY() + last.getHeight() / 2.0 - 4) * sy);
                 y < (last.getY() + last.getHeight() / 2.0 + 4) * sy; y++)
                for (int x = (int) ((chevronX - 1) * sx); x < (chevronX + 5) * sx; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if ((pixel & 255) > 210 && (pixel >>> 8 & 255) > 170 && (pixel >>> 16 & 255) > 120)
                        pixels++;
                }
            require(pixels >= 4, "Overview forward arrow did not render");
        }
    }
    private static StyledButton find(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void button(Minecraft game, String id) { find(game, id).onPress(); }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(Path.of("..", "build", "verification"));
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (overview stage " + stage + ")");
    }
}
