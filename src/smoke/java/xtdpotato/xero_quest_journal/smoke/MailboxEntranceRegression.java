package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

final class MailboxEntranceRegression {
    private static int stage;
    private static int originalDuration;
    private static boolean originalReduced;
    private static Screen originalScreen;
    private static MailboxScreen mailbox;
    private static Map<String, Boolean> readState;
    private static long entranceStart;
    private static CompletableFuture<Void> seedWork;
    private static boolean seedStarted;
    private static int snapshotWait;

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> {
                if (!seedStarted) {
                    seedStarted = true;
                    var server = game.getSingleplayerServer();
                    var playerId = game.player.getUUID();
                    seedWork = CompletableFuture.runAsync(() -> {
                        var player = server.getPlayerList().getPlayer(playerId);
                        for (int i = 0; i < 5; i++) MailboxRuntime.sendIfAbsent(player,
                                "entrance_regression_" + i, "Xero", "Entrance " + i, "Entrance animation fixture",
                                0, List.of());
                    }, server);
                    return false;
                }
                if (seedWork != null) {
                    if (!seedWork.isDone()) return false;
                    seedWork.join();
                    seedWork = null;
                    ClientMailbox.request();
                    return false;
                }
                if (ClientMailbox.messages().stream().filter(mail -> !mail.archived()).count() < 4) {
                    if (++snapshotWait % 20 == 0) ClientMailbox.request();
                    return false;
                }
                originalScreen = game.screen;
                originalDuration = QuestConfig.defaults().uiAnimationMillis;
                originalReduced = ClientPreferences.get().reducedMotion;
                QuestConfig.defaults().uiAnimationMillis = 800;
                ClientPreferences.get().reducedMotion = false;
                game.getToasts().clear();
                readState = ClientMailbox.messages().stream()
                        .collect(Collectors.toMap(MailboxRuntime.View::id, MailboxRuntime.View::read));
                mailbox = new MailboxScreen();
                game.setScreen(mailbox);
            }
            case 1, 4 -> {
                if (!(boolean) field("rowEntranceStarted")) return false;
                entranceStart = (long) field("rowEntranceStart");
                var rows = rows();
                require(rows.size() > 3, "Not enough rows to verify stagger");
                long halfway = entranceStart + 400_000_000L;
                require(opacity(rows.get(0), halfway) > opacity(rows.get(1), halfway)
                                && opacity(rows.get(1), halfway) > opacity(rows.get(2), halfway),
                        "Mail rows did not fade in from top to bottom");
                require(opacity(rows.get(0), entranceStart) == 0
                                && opacity(rows.get(2), entranceStart + 50_000_000L) == 0,
                        "Delayed mail rows appeared immediately");
                capture(game, stage == 1 ? "mailbox-list-enter-start" : "keepsake-list-enter-start");
            }
            case 2, 5 -> {
                if (System.nanoTime() - entranceStart < 400_000_000L) return false;
                capture(game, stage == 2 ? "mailbox-list-enter-stagger" : "keepsake-list-enter-stagger");
                var init = MailboxScreen.class.getDeclaredMethod("init");
                init.setAccessible(true);
                init.invoke(mailbox);
                require((long) field("rowEntranceStart") == entranceStart,
                        "Snapshot/widget refresh restarted the list entrance");
            }
            case 3 -> {
                if (!settled()) return false;
                capture(game, "mailbox-list-enter-complete");
                mailbox = new MailboxScreen(mailbox, "", true);
                game.setScreen(mailbox);
            }
            case 6 -> {
                if (!settled()) return false;
                capture(game, "keepsake-list-enter-complete");
                ClientPreferences.get().reducedMotion = true;
                mailbox = new MailboxScreen();
                game.setScreen(mailbox);
            }
            case 7 -> {
                if (!(boolean) field("rowEntranceStarted")) return false;
                require((int) field("rowEntranceDuration") == 0 && settled(),
                        "Reduced-motion preference still delayed mail rows");
                require(ClientMailbox.messages().stream()
                                .allMatch(mail -> readState.get(mail.id()) == null
                                        || readState.get(mail.id()) == mail.read()),
                        "List entrance marked messages as read");
                ClientPreferences.get().reducedMotion = originalReduced;
                QuestConfig.defaults().uiAnimationMillis = originalDuration;
                game.setScreen(originalScreen);
                return true;
            }
            default -> throw new AssertionError("Unexpected mailbox entrance stage");
        }
        stage++;
        return false;
    }

    private static boolean settled() throws Exception {
        long now = System.nanoTime();
        for (var row : rows()) if (opacity(row, now) < 1) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<MailRowButton> rows() throws Exception {
        return (List<MailRowButton>) field("rows");
    }

    private static float opacity(MailRowButton row, long now) throws Exception {
        var method = MailboxScreen.class.getDeclaredMethod("rowOpacity", MailRowButton.class, long.class);
        method.setAccessible(true);
        return (float) method.invoke(mailbox, row, now);
    }

    private static Object field(String name) throws Exception {
        var field = MailboxScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(mailbox);
    }

    private static void capture(Minecraft game, String name) throws Exception {
        var output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (mail entrance stage " + stage + ")");
    }
}
