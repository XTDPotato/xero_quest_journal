package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.mailbox.*;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MailReferenceRegression {
    private static int stage, wait;
    private static int closeWait;
    private static CompletableFuture<Void> serverWork;
    private static double scrollTarget;
    private static MailboxScreen inbox;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                game.getLanguageManager().setSelected("zh_cn");
                game.reloadResourcePacks();
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1920, 1080);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    var data = MailboxData.get(server);
                    var reward = List.of(new MailboxRuntime.Attachment("item", "minecraft:paper", 1, false));
                    for (int i = 0; i < 25; i++) {
                        MailboxRuntime.sendIfAbsent(player, "normal_" + i, "邮件运营团队", "默认邮件 " + i,
                                "邮件正文。", (i + 2) * 86400L, "", false, reward);
                        if (i % 2 == 0) MailboxRuntime.markRead(player, "normal_" + i);
                    }
                    MailboxRuntime.sendIfAbsent(player, "game_clock", "任务团队", "游戏时间邮件", "正文",
                            2400, true, "minecraft:clock", false, reward);
                    for (String id : List.of("treasure_read", "treasure_unread")) {
                        MailboxRuntime.sendIfAbsent(player, id, "任务团队", id, "珍藏正文", 86400,
                                "minecraft:diamond", true, reward);
                        if (id.equals("treasure_read")) MailboxRuntime.markRead(player, id);
                        require(MailboxRuntime.claim(player, id).equals("claimed"), "Treasure claim failed");
                        var mail = data.messages(player.getUUID()).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
                        require(mail.archived() && mail.treasureNew() && mail.treasureUnseen(), "Claim did not create independent archive state");
                        require(mail.read() == id.equals("treasure_read"), "Claim changed treasure read state");
                        require(mail.expiresAt() == 0, "Keepsake received a deadline");
                    }
                    CompoundTag saved = data.save(new CompoundTag(), server.registryAccess());
                    var restored = MailboxData.load(saved, server.registryAccess());
                    require(restored.messages(player.getUUID()).equals(data.messages(player.getUUID())), "Mail NBT lost fields");
                    require(restored.messages(java.util.UUID.randomUUID()).isEmpty(), "Mailbox leaked between players");
                    long now = System.currentTimeMillis(), ticks = server.overworld().getGameTime();
                    restored.add(player.getUUID(), new MailboxRuntime.Mail("real_expired", "", "", "", "", now, now - 1,
                            false, false, "", false, false, false, false, false, List.of()));
                    restored.add(player.getUUID(), new MailboxRuntime.Mail("game_expired", "", "", "", "", now, ticks + 20,
                            false, false, "", false, false, false, false, true, List.of()));
                    require(restored.purgeExpired(player.getUUID(), now, ticks) == 1, "Real expiry used wrong clock");
                    require(restored.purgeExpired(player.getUUID(), now, ticks + 20) == 1, "Game expiry used wrong clock");
                    var snapshot = new QuestNetwork.MailboxSnapshotPayload(MailboxRuntime.snapshot(player));
                    var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
                    try {
                        QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
                        require(QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.decode(buffer).equals(snapshot), "Mailbox codec lost archive/clock fields");
                    } finally { buffer.release(); }
                }, server);
            }
            case 1 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                MailArrivalToast.clear();
                ClientMailbox.request();
                inbox = new MailboxScreen();
                game.setScreen(inbox);
            }
            case 2 -> {
                if (ClientMailbox.messages().size() < 29) return false;
                if (rows(inbox).isEmpty()) return false;
                require(((List<?>) call(inbox, "visibleMessages")).size() == ClientMailbox.messages().size(),
                        "Keepsake mail disappeared from the inbox");
                require(ClientMailbox.messages().stream().filter(MailboxRuntime.View::treasureUnseen).count() == 2, "Missing arrival marks");
                capture(game, "mail-reference-inbox.png");
                Object motion = field(inbox, "rowMotion");
                invoke(motion, "jump", new Class<?>[]{double.class}, 480D);
                call(inbox, "moveContent");
                var visible = rows(inbox);
                visible.getLast().onPress();
                scrollTarget = (double) field(motion, "target");
                require(scrollTarget == 528, "Bottom-three selection did not shift one row");
                require((double) field(motion, "position") == 480, "Edge selection jumped");
                String id = (String) field(inbox, "selectedId");
                rows(inbox).stream().filter(r -> r.styleId().equals("mail-" + id)).findFirst().orElseThrow().onPress();
                require((double) field(motion, "target") == scrollTarget, "Repeated selection scrolled");
                wait = 30;
            }
            case 3 -> {
                Object motion = field(inbox, "rowMotion");
                require(Math.abs((double) field(motion, "position") - scrollTarget) < 1, "Selection scroll did not settle");
                rows(inbox).getFirst().onPress();
                require((double) field(motion, "target") == 480, "Top-three selection did not reverse one row");
                capture(game, "mail-reference-selected.png");
                find(inbox, "mail-favorite-box").onPress();
                wait = 20;
            }
            case 4 -> {
                if (ClientMailbox.messages().stream().anyMatch(MailboxRuntime.View::treasureUnseen)) return false;
                require(ClientMailbox.messages().stream().filter(MailboxRuntime.View::treasureNew).count() == 2,
                        "Opening box cleared per-mail new badge");
                capture(game, "mail-reference-treasure-new.png");
                find(game.screen, "mail-treasure_read").onPress();
                wait = 15;
            }
            case 5 -> {
                if (ClientMailbox.messages().stream().anyMatch(m -> m.id().equals("treasure_read") && m.treasureNew())) return false;
                require(ClientMailbox.messages().stream().anyMatch(m -> m.id().equals("treasure_unread") && m.treasureNew()), "Opening one treasure cleared another");
                find(game.screen, "mail-delete").onPress();
                require((int) call(game.screen, "confirmationWidth") == game.screen.width / 2, "Dialog not half width");
                require((int) call(game.screen, "confirmationHeight") == game.screen.height / 2, "Dialog not half height");
                require(!(boolean) call(game.screen, "isModalScreen"),
                        "Nested delete dialog used the full-screen modal crop");
                require((int) call(game.screen, "popupScrimColor") == 0x80000000,
                        "Delete dialog lost its 50% black full-screen scrim");
                wait = 20;
            }
            case 6 -> {
                require(((AnimatedQuestScreen) game.screen).popupAnimationPosition() > .99,
                        "Favorite delete dialog did not finish its popup animation");
                capture(game, "mail-reference-confirm.png");
                var cancel = find(game.screen, "mail-cancel");
                double scale = game.getWindow().getWidth() / (double) game.screen.width;
                GLFW.glfwSetCursorPos(game.getWindow().getWindow(),
                        (cancel.getX() + cancel.getWidth() / 2.0) * scale,
                        (cancel.getY() + cancel.getHeight() / 2.0) * scale);
                wait = 2;
            }
            case 7 -> {
                var cancel = find(game.screen, "mail-cancel");
                double x = cancel.getX() + cancel.getWidth() / 2.0;
                double y = cancel.getY() + cancel.getHeight() / 2.0;
                require(cancel.isMouseOver(x, y), "Delete dialog button hitbox does not match its visible bounds");
                require(game.screen.mouseClicked(x, y, 0), "Delete dialog cancel button did not receive press");
                require((boolean) field(game.screen, "confirmDelete"), "Cancel acted on press instead of release");
                game.screen.mouseReleased(x, y, 0);
                require(!(boolean) field(game.screen, "confirmDelete"), "Cancel release did not close delete dialog");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 960, 540);
                game.resizeDisplay();
                wait = 20;
            }
            case 8 -> {
                var page = (AnimatedQuestScreen) game.screen;
                if (page.popupAnimationPosition() != 0 || page.inputBlocked()) {
                    if (++closeWait < 100) {
                        wait = 1;
                        return false;
                    }
                    throw new AssertionError("Favorite delete popup stuck: confirm="
                            + field(game.screen, "confirmDelete") + ", popup="
                            + page.popupAnimationPosition() + ", blocked=" + page.inputBlocked()
                            + ", closing=" + page.isClosing());
                }
                require(!(boolean) field(game.screen, "confirmDelete"),
                        "Favorite delete popup state did not clear");
                var firstRow = rows((MailboxScreen) game.screen).getFirst();
                double rowX = firstRow.getX() + firstRow.getWidth() / 2.0;
                double rowY = firstRow.getY() + firstRow.getHeight() / 2.0;
                require(game.screen.mouseClicked(rowX, rowY, 0),
                        "Mailbox row could not be clicked after delete popup closed");
                game.screen.mouseReleased(rowX, rowY, 0);
                find(game.screen, "mail-delete").onPress();
                require((int) call(game.screen, "confirmationWidth") <= game.screen.width - 16,
                        "Compact dialog exceeds horizontal viewport");
                require((int) call(game.screen, "confirmationHeight") <= game.screen.height - 16,
                        "Compact dialog exceeds vertical viewport");
                wait = 20;
            }
            case 9 -> {
                capture(game, "mail-reference-confirm-small.png");
                find(game.screen, "mail-cancel").onPress();
                MailArrivalToast.enqueue(new QuestNetwork.MailboxNoticePayload("normal_1", "PRIVATE SENDER", "PRIVATE SUBJECT"));
                wait = 20;
            }
            case 10 -> {
                require(MailArrivalToast.visible(), "Notification not visible");
                capture(game, "mail-reference-notice.png");
                var cls = MailArrivalToast.class;
                int left = staticInt(cls, "left"), right = staticInt(cls, "right");
                int top = staticInt(cls, "top"), bottom = staticInt(cls, "bottom");
                require(right - left <= game.getWindow().getGuiScaledWidth() / 3
                                && bottom - top <= 34,
                        "Default new-mail notification does not fit the reference banner size");
                var prior = game.screen;
                double x = (left + right) / 2.0, y = (top + bottom) / 2.0;
                require(MailArrivalToast.click(x, y), "Notification press not handled");
                require(game.screen == prior && MailArrivalToast.visible(), "Notification opened on press");
                require(MailArrivalToast.release(x, top - 2), "Notification outside release not handled");
                require(game.screen == prior && MailArrivalToast.visible(), "Notification opened after outside release");
            }
            case 11 -> {
                var cls = MailArrivalToast.class;
                double x = (staticInt(cls, "left") + staticInt(cls, "right")) / 2.0;
                double y = (staticInt(cls, "top") + staticInt(cls, "bottom")) / 2.0;
                require(MailArrivalToast.click(x, y), "Banner body press not handled");
                require(game.screen instanceof MailboxScreen && MailArrivalToast.visible(),
                        "Banner opened before release");
                require(MailArrivalToast.release(x, y), "Banner body release not handled");
                require(game.screen instanceof MailboxScreen, "Banner click did not open mailbox");
                wait = 20;
            }
            case 12 -> {
                require(ClientMailbox.messages().stream().anyMatch(m -> m.id().equals("normal_1") && m.read()), "Opened ordinary mail not marked read");
                var original = MailboxAnimationStore.copy(MailboxAnimationStore.get());
                var draft = MailboxAnimationStore.copy(original);
                draft.events.get("notice").html = "<div data-text=\"Custom notice\" data-icon=\"mail\"></div>";
                draft.events.get("notice").css = ".mail-notice { --text:#123456; --width:333; }";
                MailboxAnimationStore.preview(draft);
                require(MailboxAnimationStore.attribute("notice", "text", "").equals("Custom notice")
                        && MailboxAnimationStore.color("notice", "text", 0) == 0xFF123456, "Notice custom style ignored");
                MailboxAnimationStore.clearPreview();
                find(game.screen, "mail-compose").onPress();
                int menuLeft = (int) call(game.screen, "composeMenuLeft");
                int menuTop = (int) call(game.screen, "composeMenuTop");
                require(game.screen.mouseClicked(menuLeft + 12, menuTop + 12, 0),
                        "New mail menu item did not receive click");
                game.screen.mouseReleased(menuLeft + 12, menuTop + 12, 0);
                wait = 15;
            }
            case 13 -> {
                require(find(game.screen, "mail-compose-clock") != null, "Missing expiry clock choice");
                capture(game, "mail-reference-compose-clock.png");
                return true;
            }
        }
        stage++;
        return false;
    }

    private static List<MailRowButton> rows(MailboxScreen screen) throws Exception {
        int top = (int) call(screen, "listTop"), bottom = (int) call(screen, "listBottom");
        return screen.children().stream().filter(MailRowButton.class::isInstance).map(MailRowButton.class::cast)
                .filter(r -> r.getBottom() > top && r.getY() < bottom).toList();
    }
    private static StyledButton find(net.minecraft.client.gui.screens.Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(object);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
    private static int staticInt(Class<?> cls, String name) throws Exception {
        var f = cls.getDeclaredField(name); f.setAccessible(true); return f.getInt(null);
    }
    private static Object call(Object object, String name) throws Exception { return invoke(object, name, new Class<?>[0]); }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        var m = object.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(object, args);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("..", "build", "verification"); Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(output.resolve(name)); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError("Mail reference stage " + stage + ": " + message); }
}
