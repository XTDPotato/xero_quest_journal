package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxData;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MailboxRegression {
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static int stage;
    private static int wait = 30;
    private static CompletableFuture<Void> serverWork;
    private static int diamondBefore;
    private static int paperBefore;
    private static String hoverId;
    private static String noticeId;
    private static int hoverWait;
    private static boolean starBefore;
    private static int starWait;
    private static String starMailId;
    private static String composedId;
    private static int observedStage = -1;
    private static long stageStarted;

    private MailboxRegression() {
    }

    static boolean tick(Minecraft game) throws Exception {
        if (stage != observedStage) {
            observedStage = stage;
            stageStarted = System.nanoTime();
        } else if (stage > 1 && System.nanoTime() - stageStarted > 30_000_000_000L) {
            capture(game, "mailbox-stalled");
            String detail = game.screen instanceof MailComposeScreen
                    ? " feedback=" + field(game.screen, "feedback") + " pending=" + field(game.screen, "pending") : "";
            throw new IllegalStateException("Mailbox stage " + stage + " stalled: screen="
                    + (game.screen == null ? "none" : game.screen.getClass().getSimpleName()) + detail);
        }
        if (stage >= 48) MailArrivalToast.clear();
        // The test window can be unfocused while the user continues working.
        // Keep the test pointer stable for the attachment-hover screenshot.
        if (stage == 42 && game.screen instanceof MailboxScreen screen) {
            int left = ((Integer) call(screen, "split")) + 18;
            var top = MailboxScreen.class.getDeclaredMethod("attachmentTop", MailboxRuntime.View.class);
            top.setAccessible(true);
            int y = (int) top.invoke(screen, field(screen, "selected"));
            cursor(game, left + 8, y + 8);
            screen.mouseMoved(left + 8, y + 8);
        }
        if (--wait > 0) {
            return false;
        }
        switch (stage) {
            case 0 -> {
                // Synchronous action checks must not click through screen/menu transitions.
                // MailboxEntranceRegression enables motion again for its animation checks.
                ClientPreferences.get().reducedMotion = true;
                verifyFavoritePalette();
                verifyMailRowPresentation(game);
                game.options.pauseOnLostFocus = false;
                GLFW.glfwRestoreWindow(game.getWindow().getWindow());
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1920, 1080);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                game.setScreen(new TitleScreen());
                game.createWorldOpenFlows().createFreshLevel(
                        "Xero-Mailbox-" + System.currentTimeMillis(),
                        new LevelSettings("Xero Mailbox Test", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(47L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        new TitleScreen());
            }
            case 1 -> {
                if (game.player == null || game.level == null || game.screen != null) {
                    return false;
                }
                diamondBefore = count(game, Items.DIAMOND);
                paperBefore = count(game, Items.PAPER);
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    MailboxRuntime.ensureWelcome(player);
                    var dispatcher = server.getCommands().getDispatcher();
                    var source = server.createCommandSourceStack();
                    try {
                        MailboxNodeRegression.run(player);
                        player.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, true);
                        require(dispatcher.execute(
                                "xero_quest mail send_item Dev mailbox_smoke_reward \"P.A.I.M.O.N\" "
                                        + "\"Mailbox reward\" 0 minecraft:diamond 2 Claim the attached diamonds.",
                                source) == 1, "Mail send command failed");
                        require(dispatcher.execute(
                                "xero_quest mail send Dev mailbox_smoke_read Xero "
                                        + "\"Read notice\" 0 This read mail has no attachment.",
                                source) == 1, "No-attachment mail command failed");
                        require(dispatcher.execute("xero_quest mail read Dev mailbox_smoke_read", source) == 1,
                                "Mail read command failed");
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                    var data = MailboxData.get(server);
                    var saved = data.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
                    var restored = MailboxData.load(saved, server.registryAccess());
                    require(restored.messages(player.getUUID()).size() == 3, "Mailbox NBT round-trip failed");
                    require(restored.messages(player.getUUID()).stream().allMatch(mail ->
                            mail.recipient().equals(player.getGameProfile().getName())),
                            "Mailbox NBT lost recipient");
                }, server);
            }
            case 2 -> {
                if (!serverWork.isDone()) {
                    return false;
                }
                serverWork.join();
                require(QuestKeyMappings.OPEN_MAILBOX.getDefaultKey().getValue() == GLFW.GLFW_KEY_F8,
                        "Mailbox key default is not F8");
                KeyMapping.click(QuestKeyMappings.OPEN_MAILBOX.getKey());
            }
            case 3 -> {
                if (!(game.screen instanceof MailboxScreen) || ClientMailbox.messages().size() != 3) {
                    return false;
                }
                assertComposeToolbar(game);
                int listTop = (int) call(game.screen, "listTop");
                int listBottom = (int) call(game.screen, "listBottom");
                require(listBottom - listTop == game.screen.height - 42 - listTop,
                        "Mailbox list does not fill available height above its footer");
                capture(game, "33-mailbox");
                click(game, "mail-delete_read");
            }
            case 4 -> {
                capture(game, "34-mailbox-delete-confirm");
                click(game, "mail-confirm");
            }
            case 5 -> {
                if (ClientMailbox.messages().size() != 2) {
                    return false;
                }
                require(ClientMailbox.messages().stream().anyMatch(mail -> mail.id().equals("xero_welcome")
                                && mail.claimable()),
                        "Delete-read removed unclaimed welcome attachment");
                click(game, "mail-claim_all");
            }
            case 6 -> {
                if (ClientMailbox.messages().stream().anyMatch(MailboxRuntime.View::claimable)
                        || count(game, Items.DIAMOND) != diamondBefore + 2
                        || count(game, Items.PAPER) != paperBefore + 1) {
                    return false;
                }
                if (!(game.screen instanceof QuestRewardPopupScreen)) return false;
                require(((List<?>) field(game.screen, "rewards")).size() == 2,
                        "Bulk mailbox receipt lost delivered items");
                capture(game, "mailbox-claim-all-receipt");
                game.setScreen((net.minecraft.client.gui.screens.Screen) field(game.screen, "parent"));
                click(game, "mail-claim_all");
            }
            case 7 -> {
                Component tip = XeroTips.currentMessage();
                if (tip == null) {
                    return false;
                }
                require(tip.getString().equals(
                        Component.translatable("screen.xero_quest_journal.mailbox.tips.all_claimed").getString()),
                        "No-claimable tip text mismatch");
                capture(game, "35-mailbox-all-claimed-tip");
                game.screen.onClose();
                game.setScreen(new InventoryScreen(game.player));
            }
            case 8 -> {
                capture(game, "36-mailbox-inventory-entry");
                click(game, "mailbox-inventory-entry");
                require(game.screen instanceof MailboxScreen, "Inventory mailbox entry failed");
                game.screen.onClose();
                game.setScreen(null);
                game.player.connection.sendCommand("xero_quest open mailbox");
            }
            case 9 -> {
                if (!(game.screen instanceof MailboxScreen)) return false;
                game.screen.onClose();
                game.setScreen(null);
                String questId = QuestRepository.all().stream().map(quest -> quest.id()).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No quest available for command-open test"));
                game.player.connection.sendCommand("xero_quest open task " + questId);
            }
            case 10 -> {
                if (!(game.screen instanceof QuestScreen)) return false;
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    for (int i = 0; i < 40; i++) {
                        MailboxRuntime.send(player, "mail_ui_" + i, "Quest team",
                                "Mail " + i, "Scrollable message\n".repeat(45), 0,
                                i % 4 == 0 ? List.of(new MailboxRuntime.Attachment("item", "minecraft:emerald", 1, false)) : List.of());
                        if (i % 4 == 1) MailboxRuntime.markRead(player, "mail_ui_" + i);
                    }
                }, server);
            }
            case 11 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                game.setScreen(new MailboxScreen());
                ClientMailbox.request();
            }
            case 12 -> {
                if (ClientMailbox.messages().size() < 40) return false;
                var screen = (MailboxScreen) game.screen;
                var motion = field(screen, "rowMotion");
                double before = (double) field(motion, "position");
                screen.mouseScrolled(30, 85, 0, -3);
                require((double) field(motion, "position") == before, "Wheel jumped instead of animating");
            }
            case 13 -> {
                var screen = (MailboxScreen) game.screen;
                var motion = field(screen, "rowMotion");
                require((double) field(motion, "position") > 0, "Mail list did not scroll");
                String selected = (String) field(screen, "selectedId");
                int listBottom = (int) call(screen, "listBottom");
                screen.mouseClicked(35, listBottom - 12, 0);
                screen.mouseDragged(35, listBottom - 72, 0, 0, -60);
                screen.mouseReleased(35, listBottom - 72, 0);
                require(field(screen, "selectedId").equals(selected), "List drag selected mail");
                int split = (int) call(screen, "split");
                screen.mouseClicked(split - 4, listBottom - 1, 0);
                screen.mouseReleased(split - 4, listBottom - 1, 0);
                require((double) field(motion, "position") > 1200, "Mail scrollbar cannot reach bottom");
                screen.mouseClicked(split - 4, 51, 0);
                screen.mouseReleased(split - 4, 51, 0);
            }
            case 14 -> {
                capture(game, "mailbox-states");
                var screen = (MailboxScreen) game.screen;
                int listBottom = (int) call(screen, "listBottom");
                var row = screen.children().stream().filter(MailRowButton.class::isInstance).map(MailRowButton.class::cast)
                        .filter(button -> button.visible && button.getY() > 55
                                && button.getBottom() < listBottom)
                        .findFirst().orElseThrow();
                hoverId = row.styleId();
                cursor(game, row.getX() + 50, row.getY() + 20);
            }
            case 15 -> {
                var row = find(game, hoverId);
                cursor(game, row.getX() + 50, row.getY() + 20);
                if ((float) field(row, "hoverProgress") < .9f && hoverWait++ < 60) return false;
                require((float) field(row, "hoverProgress") > .9f && (float) field(row, "liftProgress") > .9f,
                        "Mail hover animation did not reach 5% enlargement: hover=" + field(row, "hoverProgress")
                                + ", lift=" + field(row, "liftProgress") + ", mouse=" + game.mouseHandler.xpos()
                                + "," + game.mouseHandler.ypos() + ", row=" + row.getX() + "," + row.getY());
                capture(game, "mailbox-hover");
                cursor(game, game.screen.width - 5, 45);
            }
            case 16 -> {
                require((float) field(find(game, hoverId), "hoverProgress") < .1f, "Hover border failed to fade out");
                var screen = (MailboxScreen) game.screen;
                int listBottom = (int) call(screen, "listBottom");
                var row = screen.children().stream().filter(MailRowButton.class::isInstance).map(MailRowButton.class::cast)
                        .filter(button -> button.visible && button.getY() >= 50
                                && button.getBottom() < listBottom)
                        .filter(button -> {
                            var id = button.styleId().substring(5);
                            return ClientMailbox.messages().stream().anyMatch(mail -> mail.id().equals(id)
                                    && !mail.read() && mail.attachments().isEmpty());
                        }).findFirst().orElseThrow();
                noticeId = row.styleId().substring(5);
                click(game, row.styleId());
            }
            case 17 -> {
                if (ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals(noticeId) && mail.read())) return false;
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 854, 480);
                game.resizeDisplay();
            }
            case 18 -> {
                capture(game, "mailbox-small");
                click(game, "mail-delete_read");
            }
            case 19 -> {
                capture(game, "mailbox-delete-small");
                for (var child : game.screen.children()) if (child instanceof MailRowButton row)
                    require(!row.visible && !row.active, "Mail row leaked into modal");
                click(game, "mail-cancel");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1280, 800);
                game.resizeDisplay();
                var editor = new QuestTriggerGraphScreen(game.screen, MailboxNodeRegression.graph, ignored -> {});
                game.setScreen(editor);
                var selected = editor.getClass().getDeclaredField("selected");
                selected.setAccessible(true);
                selected.set(editor, "send");
                call(editor, "init");
                find(game, "graph-fit").onPress();
            }
            case 20 -> {
                capture(game, "mailbox-node-editor");
                require(game.screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.EditBox
                                edit && edit.getValue().equals(MailboxNodeRegression.MAIL)),
                        "Mail ID is missing from inspector");
                find(game, "graph-save").onPress();
                require(game.screen instanceof MailboxScreen, "Mail graph save failed");
                var screen = (MailboxScreen) game.screen;
                String selectedId = (String) field(screen, "selectedId");
                var selected = ClientMailbox.messages().stream().filter(mail -> mail.id().equals(selectedId))
                        .findFirst().orElseThrow();
                starMailId = selected.id();
                starBefore = selected.starred();
                click(game, "mail-star");
                // A mail action rebuilds the widgets; inspect the live control, not its predecessor.
                var favorite = find(game, "mail-star");
                require(!favorite.active
                                && !game.screen.mouseClicked(favorite.getX() + favorite.getWidth() / 2.0,
                                favorite.getY() + favorite.getHeight() / 2.0, 0),
                        "Favorite button accepted another click during its animation");
            }
            case 21 -> {
                var selected = ClientMailbox.messages().stream().filter(mail -> mail.id().equals(starMailId))
                        .findFirst().orElseThrow();
                if (selected.starred() == starBefore) {
                    if (++starWait < 60) return false;
                    throw new IllegalStateException("Favorite state did not synchronize");
                }
                capture(game, "mailbox-favorite");
                click(game, "mail-favorite-box");
            }
            case 22 -> {
                var screen = (MailboxScreen) game.screen;
                require((boolean) field(screen, "favoriteOnly"), "Favorite box did not open");
                var selected = ClientMailbox.messages().stream().filter(mail -> mail.id().equals(starMailId))
                        .findFirst().orElseThrow();
                require(selected.starred() && !selected.treasured(), "Marking unexpectedly set the keepsake property");
                require(((List<?>) call(screen, "visibleMessages")).isEmpty(), "Ordinary starred mail leaked into keepsake box");
                click(game, "mail-back");
            }
            case 23 -> {
                if (game.screen instanceof MailboxScreen) {
                    click(game, "mail-edit-animations");
                    return false;
                }
                require(game.screen instanceof MailboxAnimationEditorScreen, "Mailbox animation editor did not open");
                var editor = (MailboxAnimationEditorScreen) game.screen;
                var source = (net.minecraft.client.gui.components.MultiLineEditBox) field(editor, "source");
                require(source.getValue().contains("--star-scale"), "Favorite CSS source missing");
                source.setValue(source.getValue().replace("--star-scale:1.42", "--star-scale:1.37"));
                find(game, "mail-animation-save").onPress();
            }
            case 24 -> {
                require(game.screen instanceof MailboxScreen, "Mailbox animation editor did not save");
                require(MailboxAnimationStore.animation("favorite").css.contains("--star-scale:1.37"),
                        "Saved favorite animation source was not applied");
                click(game, "mail-edit-animations");
            }
            case 25 -> {
                if (game.screen instanceof MailboxAnimationEditorScreen editor) {
                    var source = (net.minecraft.client.gui.components.MultiLineEditBox) field(editor, "source");
                    require(source.getValue().contains("--star-scale:1.37"),
                            "Saved favorite animation source was not persisted");
                    if (!editor.isClosing()) {
                        find(game, "mail-animation-cancel").onPress();
                        wait = 1;
                    }
                    return false;
                }
                require(game.screen instanceof MailboxScreen, "Mailbox animation editor cancel failed");
            }
            case 26 -> {
                MailArrivalToast.clear();
                openCompose(game);
                require(game.screen instanceof MailComposeScreen, "Compose screen did not open");
                var editor = game.screen;
                composedId = (String) field(editor, "id");
                ((net.minecraft.client.gui.components.EditBox) field(editor, "recipient"))
                        .setValue(game.player.getGameProfile().getName());
                ((net.minecraft.client.gui.components.EditBox) field(editor, "subject")).setValue("Command test");
                ((net.minecraft.client.gui.components.MultiLineEditBox) field(editor, "body"))
                        .setValue("First line\nSecond line");
                click(game, "mail-compose-copy");
                serverWork = null;
            }
            case 27 -> {
                if (field(game.screen, "pending") != null) return false;
                String command = game.keyboardHandler.getClipboard();
                require(command.startsWith("/xero_quest mail send_bundle "),
                        "Copied mail is not a portable JSON bundle command");
                var imported = xtdpotato.xero_quest_journal.core.mailbox.MailClipboardCodec.parse(command);
                require(imported.draft().body().equals("First line\nSecond line")
                                && imported.draft().id().equals(composedId)
                                && imported.draft().attachments().isEmpty(),
                        "Copied command did not preserve the complete mail JSON");
                if (serverWork == null) {
                    var server = game.getSingleplayerServer();
                    serverWork = CompletableFuture.runAsync(() -> {
                        try {
                            require(server.getCommands().getDispatcher().execute(command.substring(1),
                                    server.createCommandSourceStack()) == 1, "Copied mail command failed");
                            var player = server.getPlayerList().getPlayer(game.player.getUUID());
                            var mail = MailboxData.get(server).messages(player.getUUID()).stream()
                                    .filter(entry -> entry.id().equals(composedId)).findFirst().orElseThrow();
                            require(mail.body().equals("First line\nSecond line"), "Copied command lost line breaks");
                        } catch (Exception failure) { throw new IllegalStateException(failure); }
                    }, server);
                    return false;
                }
                if (!serverWork.isDone()) return false;
                serverWork.join();
                capture(game, "mailbox-compose");
                game.screen.onClose();
                require((Boolean) field(game.screen, "confirmClose"), "Composer did not ask to save a draft");
                var composer = game.screen;
                int dialogWidth = Math.max(120, composer.width / 2);
                int dialogHeight = Math.max(112, composer.height / 2);
                int part = (dialogWidth - 24) / 3;
                double cx = (composer.width - dialogWidth) / 2.0 + 8 + part / 2.0;
                double cy = (composer.height - dialogHeight) / 2.0 + dialogHeight - 22;
                composer.mouseClicked(cx, cy, 0);
                require((boolean) field(composer, "confirmClose") && game.screen == composer,
                        "Composer close confirmation acted on press");
                composer.mouseReleased(cx, cy, 0);
                require(ClientPreferences.get().mailDraft != null && game.screen instanceof MailboxScreen,
                        "Composer did not persist its draft on matching release");
            }
            case 28 -> {
                if (!(game.screen instanceof MailboxScreen)) return false;
                openCompose(game);
                var editor = game.screen;
                // Stage 27 delivered the copied bundle. Test a fresh UI delivery, not an idempotent resend.
                var idField = editor.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                composedId = "mail_ui_" + java.util.UUID.randomUUID().toString().replace("-", "");
                idField.set(editor, composedId);
                ((net.minecraft.client.gui.components.EditBox) field(editor, "recipient"))
                        .setValue("");
                ((net.minecraft.client.gui.components.EditBox) field(editor, "subject")).setValue("UI test");
                ((net.minecraft.client.gui.components.MultiLineEditBox) field(editor, "body"))
                        .setValue("Delivered from composer");
                MailArrivalToast.clear();
                click(game, "mail-compose-send");
            }
            case 29 -> {
                if (!(game.screen instanceof MailboxScreen)) return false;
                if (ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals(composedId))
                        || !MailArrivalToast.visible()) return false;
                double x = game.screen.width / 2.0, y = 20;
                require(MailArrivalToast.click(x, y), "Mail notification press was not handled");
                require(game.screen instanceof MailboxScreen && MailArrivalToast.visible(),
                        "Mail notification opened on press");
                require(MailArrivalToast.release(x, y), "Mail notification release was not handled");
                require(game.screen instanceof MailboxScreen, "Mail notification did not open mailbox");
            }
            case 30 -> {
                if (!field(game.screen, "selectedId").equals(composedId)) return false;
                require(ClientMailbox.messages().stream().anyMatch(mail -> mail.id().equals(composedId)
                        && mail.recipient().equals(game.player.getGameProfile().getName())),
                        "Composed mail lost recipient");
                capture(game, "mailbox-notice-open");
                MailArrivalToast.clear();
                game.setScreen(null);
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() ->
                        MailboxRuntime.send(server.getPlayerList().getPlayer(game.player.getUUID()),
                                "mail_f7_test", "Quest team", "F7 test", "Open with F7", 0, List.of()), server);
            }
            case 31 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                if (!MailArrivalToast.visible()) return false;
                assertIcon(game, "mail");
                capture(game, "mailbox-new-mail-icon");
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F7));
            }
            case 32 -> {
                if (!(game.screen instanceof MailboxScreen)) return false;
                if (!field(game.screen, "selectedId").equals("mail_f7_test")) return false;
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 854, 480);
                game.resizeDisplay();
            }
            case 33 -> {
                assertComposeToolbar(game);
                capture(game, "mailbox-header-compact");
                click(game, "mail-compose");
                clickComposeMenu(game, 1);
                require(game.screen instanceof MailComposeScreen, "Clipboard import did not open composer");
                require(((String) field(game.screen, "bodyValue")).equals("First line\nSecond line"),
                        "Clipboard-imported mail content was not applied");
            }
            case 34 -> {
                require(game.screen instanceof MailComposeScreen, "Small-window composer did not open");
                var body = (net.minecraft.client.gui.components.MultiLineEditBox) field(game.screen, "body");
                require(body.getBottom() + game.font.lineHeight < game.screen.height - 34,
                        "Composer text counter overlaps footer at small window size");
                capture(game, "mailbox-compose-small");
                click(game, "mail-compose-pick-recipient");
            }
            case 35 -> {
                require(game.screen instanceof QuestValuePickerScreen, "Recipient list did not open");
                require(game.screen.mouseClicked(game.screen.width / 2.0, 89, 0),
                        "Recipient option click was ignored");
                game.screen.mouseReleased(game.screen.width / 2.0, 89, 0);
            }
            case 36 -> {
                if (!(game.screen instanceof MailComposeScreen)) return false;
                require(((net.minecraft.client.gui.components.EditBox) field(game.screen, "recipient"))
                        .getValue().equals("@s"), "Recipient selection was not retained");
                click(game, "mail-compose-pick-icon");
            }
            case 37 -> {
                require(game.screen instanceof QuestScreen, "Attachment item picker did not open");
                var fields = (java.util.Map<?, ?>) field(game.screen, "formFields");
                ((net.minecraft.client.gui.components.EditBox) fields.get("icon_picker.manual"))
                        .setValue("minecraft:diamond");
                find(game, "icon-apply").onPress();
            }
            case 38 -> {
                require(game.screen instanceof MailComposeScreen, "Item picker did not return to composer");
                var composer = game.screen;
                require(((net.minecraft.client.gui.components.EditBox) field(composer, "icon"))
                        .getValue().equals("minecraft:diamond"), "Icon selection was not retained");
                var stack = new ItemStack(Items.DIAMOND);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Mail test gem"));
                String encoded = xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue
                        .encode(stack, game.player.registryAccess());
                require(!encoded.isBlank(), "Component attachment failed to encode");
                setField(composer, "rewards", List.of(
                        new MailboxRuntime.Attachment("item", encoded, 1, false),
                        new MailboxRuntime.Attachment("experience", "", 3, false)));
                setField(composer, "treasured", true);
                ((net.minecraft.client.gui.components.EditBox) field(composer, "subject"))
                        .setValue("Component attachment");
                ((net.minecraft.client.gui.components.MultiLineEditBox) field(composer, "body"))
                        .setValue("Inspect this item.");
                click(game, "mail-compose-copy");
                require(game.keyboardHandler.getClipboard().startsWith("/xero_quest mail send_bundle "),
                        "Component rewards were not included in the portable JSON bundle");
                var bundle = xtdpotato.xero_quest_journal.core.mailbox.MailClipboardCodec.parse(
                        game.keyboardHandler.getClipboard());
                require(bundle.draft().attachments().size() == 2 && bundle.draft().treasured()
                                && bundle.draft().icon().equals("minecraft:diamond"),
                        "Copied JSON bundle lost attachments or keepsake metadata");
                composedId = (String) field(composer, "id");
                click(game, "mail-compose-send");
            }
            case 39 -> {
                if (!(game.screen instanceof MailboxScreen)
                        || ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals(composedId)))
                    return false;
                game.setScreen(new MailboxScreen(null, composedId));
            }
            case 40 -> {
                var screen = (MailboxScreen) game.screen;
                require(field(screen, "selectedId").equals(composedId), "Sent mail was not selected");
                var mail = (MailboxRuntime.View) field(screen, "selected");
                require(mail.attachments().size() == 2 && mail.treasured() && mail.icon().equals("minecraft:diamond"),
                        "Multiple rewards, icon or keepsake metadata was not delivered");
                var method = MailboxScreen.class.getDeclaredMethod(
                        "attachmentStack", MailboxRuntime.Attachment.class);
                method.setAccessible(true);
                var stack = (ItemStack) method.invoke(screen, mail.attachments().getFirst());
                require(!stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
                        "Component attachment lost its item data");
            }
            case 41 -> {
                var screen = (MailboxScreen) game.screen;
                int left = ((Integer) call(screen, "split")) + 18;
                var top = MailboxScreen.class.getDeclaredMethod("attachmentTop", MailboxRuntime.View.class);
                top.setAccessible(true);
                int y = (int) top.invoke(screen, field(screen, "selected"));
                cursor(game, left + 8, y + 8);
                screen.mouseMoved(left + 8, y + 8);
                require((int) field(screen, "hoveredAttachment") == 0, "Attachment hover missed its card");
            }
            case 42 -> {
                var screen = (MailboxScreen) game.screen;
                int left = ((Integer) call(screen, "split")) + 18;
                var top = MailboxScreen.class.getDeclaredMethod("attachmentTop", MailboxRuntime.View.class);
                top.setAccessible(true);
                int y = (int) top.invoke(screen, field(screen, "selected"));
                require((int) field(screen, "hoveredAttachment") == 0, "Attachment hover did not persist");
                capture(game, "mailbox-attachment-hover");
                screen.mouseClicked(left + 8, y + 8, 0);
                screen.mouseReleased(left + 8, y + 8, 0);
                require(game.screen instanceof QuestRewardPopupScreen, "Attachment did not open item source");
            }
            case 43 -> {
                require(field(game.screen, "selectedReward") != null, "Attachment opened receipt instead of source");
                capture(game, "mailbox-attachment-source");
                game.screen.onClose();
            }
            case 44 -> {
                if (!(game.screen instanceof MailboxScreen)) return false;
                MailArrivalToast.clear();
                XeroTips.clear();
                ClientPreferences.get().reducedMotion = true;
                game.setScreen(null);
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() ->
                        server.getPlayerList().getPlayer(game.player.getUUID())
                                .setGameMode(GameType.SURVIVAL), server);
            }
            case 45 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                XeroTips.show(Component.translatable(
                        "screen.xero_quest_journal.mailbox.tips.all_claimed"));
            }
            case 46 -> {
                require(game.screen == null && game.gameMode.getPlayerMode() == GameType.SURVIVAL,
                        "Action-bar notification was not tested above the survival HUD");
                capture(game, "mailbox-tip-actionbar-hud");
                game.setScreen(new InventoryScreen(game.player));
            }
            case 47 -> {
                require(XeroTips.currentMessage() != null, "Notification vanished on another screen");
                capture(game, "mailbox-tip-over-inventory");
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 1440, 900);
                game.options.guiScale().set(3);
                game.options.languageCode = "zh_cn";
                game.getLanguageManager().setSelected("zh_cn");
                game.reloadResourcePacks();
                game.resizeDisplay();
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    player.setGameMode(GameType.CREATIVE);
                    for (int i = 0; i < 40; i++)
                        MailboxRuntime.send(player, "keepsake_ui_" + i, i % 2 == 0 ? "任务团队" : "管理员",
                                i == 39 ? "读书的收获" : "珍藏邮件 " + i,
                                "亲爱的旅行者：\n感谢你的陪伴与支持。\n这封邮件将同时保留在邮箱和邮件珍藏盒中。\n愿新的旅程为你带来更多收获。"
                                        + (i == 39 ? "\n旅程仍在继续。".repeat(24) : ""),
                                0, i % 2 == 0 ? "minecraft:diamond" : "minecraft:emerald", true,
                                i == 39 ? java.util.stream.IntStream.range(0, 16).mapToObj(index ->
                                        new MailboxRuntime.Attachment("item",
                                                index == 1 ? "minecraft:chest" : "minecraft:diamond",
                                                index == 1 ? 3 : 10, true)).toList()
                                        : List.of(new MailboxRuntime.Attachment("item", "minecraft:diamond", 10, true),
                                                new MailboxRuntime.Attachment("item", "minecraft:chest", 3, true)));
                    MailboxRuntime.toggleStarred(player, "keepsake_ui_39");
                }, server);
            }
            case 48 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                game.setScreen(new MailboxScreen(null, "keepsake_ui_39"));
                ClientMailbox.request();
            }
            case 49 -> {
                if (ClientMailbox.messages().stream().filter(MailboxRuntime.View::treasured).count() < 40) return false;
                var screen = game.screen;
                require(((List<?>) call(screen, "visibleMessages")).size() == ClientMailbox.messages().size(),
                        "Main mailbox excludes keepsake or starred mail");
                for (String icon : List.of("mail_help", "mail_treasure", "mail_close", "mail_star")) assertIcon(game, icon);
                require(find(game, "mail-help").getWidth() == 16 && find(game, "mail-help").getHeight() == 16,
                        "Help button is not 16x16");
                require(find(game, "mail-favorite-box").getWidth() == 16
                                && find(game, "mail-favorite-box").getHeight() == 16,
                        "Keepsake button is not 16x16");
                require(find(game, "mail-close").getWidth() == 20
                                && find(game, "mail-close").getHeight() == 20,
                        "Close button is not 20x20");
                require(find(game, "mail-help").getRight() + 4 == find(game, "mail-close").getX(),
                        "Help and close controls are too far apart");
                capture(game, "mailbox-reference-main");
                var favorite = find(game, "mail-star");
                require(favorite.getWidth() == 14 && favorite.getHeight() == 14,
                        "Favorite button is not 14x14");
                RewardCardAssertions.opaquePixel(game, screen,
                        favorite.getX() + favorite.getWidth() / 2, favorite.getY() + 2, 0xFFFFFF);
                RewardCardAssertions.opaquePixel(game, screen,
                        favorite.getX() + favorite.getWidth() / 2,
                        favorite.getY() + favorite.getHeight() / 2, 0xFFD900);
                var mailbox = (MailboxScreen) screen;
                var attachmentTop = MailboxScreen.class.getDeclaredMethod("attachmentTop", MailboxRuntime.View.class);
                attachmentTop.setAccessible(true);
                int rewardY = (int) attachmentTop.invoke(mailbox, field(mailbox, "selected"));
                int rewardX = (int) call(mailbox, "split") + 18;
                RewardCardAssertions.claimedBadge(game, mailbox, rewardX, rewardY, 1,
                        "mailbox-claimed-diamond");
                RewardCardAssertions.claimedBadge(game, mailbox, rewardX + 38, rewardY, 1,
                        "mailbox-claimed-chest");
                var hit = MailboxScreen.class.getDeclaredMethod("attachmentAt", double.class, double.class);
                hit.setAccessible(true);
                require((int) hit.invoke(mailbox, rewardX + 33.0, rewardY + 33.0) == 0
                                && (int) hit.invoke(mailbox, rewardX + 12.0, rewardY + 34.0) == -1
                                && (int) hit.invoke(mailbox, rewardX + 34.0, rewardY + 12.0) == -1
                                && (int) hit.invoke(mailbox, rewardX + 38.0, rewardY + 12.0) == 1,
                        "Square reward card bounds or spacing are incorrect");
                require((double) call(mailbox, "maxAttachmentScroll") > 0,
                        "Reward strip does not overflow horizontally");
                var detailMotion = field(mailbox, "detailMotion");
                mailbox.mouseScrolled(rewardX + 8, rewardY + 8, 0, -5);
                require((double) field(field(mailbox, "attachmentMotion"), "position") == 0,
                        "Reward wheel jumped instead of easing");
                require((double) field(detailMotion, "position") == 0,
                        "Reward wheel scrolled the message body");
                mailbox.mouseClicked(rewardX + 8, rewardY - 30, 0);
                mailbox.mouseDragged(rewardX + 8, rewardY - 70, 0, 0, -40);
                mailbox.mouseReleased(rewardX + 8, rewardY - 70, 0);
                require((double) field(detailMotion, "position") > 0,
                        "Long message body did not drag independently");
                require((int) hit.invoke(mailbox, rewardX + 8.0, rewardY + 8.0) == 0,
                        "Body scroll shifted the pinned reward strip");
                mailbox.mouseClicked(rewardX + 8, rewardY + 8, 0);
                mailbox.mouseDragged(rewardX - 90, rewardY + 8, 0, -98, 0);
                mailbox.mouseReleased(rewardX - 90, rewardY + 8, 0);
                require((double) field(field(mailbox, "attachmentMotion"), "position") > 0,
                        "Reward strip did not drag horizontally");
                Object strip = field(mailbox, "attachmentMotion");
                var jump = strip.getClass().getDeclaredMethod("jump", double.class);
                var advance = strip.getClass().getDeclaredMethod("advance", double.class, boolean.class);
                jump.setAccessible(true);
                advance.setAccessible(true);
                jump.invoke(strip, 0D);
                mailbox.mouseClicked(rewardX + 8, rewardY + 8, 0);
                mailbox.mouseDragged(rewardX + 88, rewardY + 8, 0, 80, 0);
                mailbox.mouseReleased(rewardX + 88, rewardY + 8, 0);
                require((double) field(strip, "position") < 0, "Reward strip cannot overshoot its edge");
                for (int frame = 0; frame < 120; frame++) advance.invoke(strip, .025, false);
                require(Math.abs((double) field(strip, "position")) < 1,
                        "Reward strip did not rebound to its edge");
                click(game, "mail-help");
            }
            case 50 -> {
                capture(game, "mailbox-reference-help");
                assertDialogCoversRewards(game, 0x424A5C);
                game.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                click(game, "mail-favorite-box");
            }
            case 51 -> {
                capture(game, "mailbox-reference-keepsake");
                var search = (net.minecraft.client.gui.components.EditBox) field(game.screen, "search");
                search.setValue("读书");
                require(((List<?>) call(game.screen, "visibleMessages")).size() == 1, "Keepsake subject search failed");
                search = (net.minecraft.client.gui.components.EditBox) field(game.screen, "search");
                search.setValue("管理员");
                require(((List<?>) call(game.screen, "visibleMessages")).size() == 20, "Keepsake sender search failed");
                ((net.minecraft.client.gui.components.EditBox) field(game.screen, "search")).setValue("");
                game.screen.mouseScrolled(90, 150, 0, -4);
            }
            case 52 -> {
                require((double) field(field(game.screen, "rowMotion"), "position") > 0, "Keepsake list did not smoothly scroll");
                int split = (int) call(game.screen, "split");
                game.screen.mouseClicked(split - 4, 90, 0);
                game.screen.mouseDragged(split - 4, game.screen.height - 21, 0, 0, 300);
                game.screen.mouseReleased(split - 4, game.screen.height - 21, 0);
                require((double) field(field(game.screen, "rowMotion"), "position") > 1000, "Keepsake scrollbar is not draggable");
                capture(game, "mailbox-reference-keepsake-scrolled");
                ((net.minecraft.client.gui.components.EditBox) field(game.screen, "search")).setValue("读书");
                click(game, "mail-delete");
            }
            case 53 -> {
                capture(game, "mailbox-reference-keepsake-delete");
                assertDialogCoversRewards(game, 0xFFFFFC);
                var confirm = find(game, "mail-confirm");
                RewardCardAssertions.circularConfirm(game, game.screen,
                        confirm.getX(), confirm.getY(), confirm.getHeight());
                click(game, "mail-cancel");
                require(ClientMailbox.messages().stream().anyMatch(mail -> mail.id().equals("keepsake_ui_39")),
                        "Cancel deleted the keepsake");
                click(game, "mail-delete");
                click(game, "mail-confirm");
            }
            case 54 -> {
                if (ClientMailbox.messages().stream().anyMatch(mail -> mail.id().equals("keepsake_ui_39"))) return false;
                require(((List<?>) call(game.screen, "visibleMessages")).isEmpty(), "Deleted mail remains in keepsake list");
                click(game, "mail-back");
                openCompose(game);
            }
            case 55 -> {
                click(game, "mail-compose-rewards");
                require(game.screen instanceof QuestRewardsScreen, "Mail did not reuse task reward editor");
                for (int i = 0; i < 15; i++) find(game, "rewards-add").onPress();
                capture(game, "mailbox-reference-rewards");
                click(game, "rewards-save");
            }
            case 56 -> {
                require(game.screen instanceof MailComposeScreen && ((List<?>) field(game.screen, "rewards")).size() == 15,
                        "Multiple reward drafts did not survive returning to composer");
                capture(game, "mailbox-reference-compose");
            }
            case 57 -> { if (!MailboxEntranceRegression.tick(game)) return false; }
            case 58 -> { return MailboxReceiptRegression.tick(game); }
            default -> throw new IllegalStateException("Unknown mailbox test stage");
        }
        stage++;
        wait = 20;
        return false;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void verifyFavoritePalette() {
        var defaults = MailboxAnimationStore.copy(new MailboxAnimationStore.Config());
        MailboxAnimationStore.preview(defaults);
        var favoriteStyle = MailboxAnimationStore.style("favorite");
        var unfavoriteStyle = MailboxAnimationStore.style("unfavorite");
        MailboxAnimationStore.clearPreview();
        require(favoriteStyle.dotCount() == 3 && favoriteStyle.durationMillis() == 900
                        && unfavoriteStyle.dotCount() == 3 && unfavoriteStyle.durationMillis() == 820,
                "Favorite loading animation is not a three-dot sequence");
        require((float) invokeStatic(MailFavoriteButton.class, "loadingDotProgress",
                        new Class<?>[]{float.class, int.class}, .12F, 0) > 0
                        && (float) invokeStatic(MailFavoriteButton.class, "loadingDotProgress",
                        new Class<?>[]{float.class, int.class}, .12F, 1) == 0
                        && (float) invokeStatic(MailFavoriteButton.class, "loadingDotProgress",
                        new Class<?>[]{float.class, int.class}, .4F, 1) > 0
                        && (float) invokeStatic(MailFavoriteButton.class, "loadingDotProgress",
                        new Class<?>[]{float.class, int.class}, .4F, 2) == 0
                        && (float) invokeStatic(MailFavoriteButton.class, "transitionProgress",
                        new Class<?>[]{float.class}, .6F) == 0
                        && (float) invokeStatic(MailFavoriteButton.class, "transitionProgress",
                        new Class<?>[]{float.class}, .85F) > 0
                        && (boolean) invokeStatic(MailFavoriteButton.class, "rippleActive",
                        new Class<?>[]{float.class, boolean.class}, .85F, true)
                        && !(boolean) invokeStatic(MailFavoriteButton.class, "rippleActive",
                        new Class<?>[]{float.class, boolean.class}, .85F, false),
                "Favorite animation phases are out of order");
        var legacy = new MailboxAnimationStore.Config();
        legacy.events.put("favorite", new MailboxAnimationStore.Animation("<span class=\"star\"></span>",
                ".mail-favorite { --button-color:#313b49; --star-color:#f3cc59; --star-scale:1.37; }",
                "function onFavorite() { star.scale(1.37); }"));
        legacy.events.put("unfavorite", new MailboxAnimationStore.Animation("",
                ".mail-favorite { --button-color:#123456; }", ""));
        var upgraded = MailboxAnimationStore.copy(legacy);
        require(upgraded.paletteVersion == 1
                        && upgraded.events.get("favorite").css.contains("--button-color:#ffffff")
                        && upgraded.events.get("favorite").css.contains("--star-color:#ffd900")
                        && upgraded.events.get("favorite").css.contains("--star-scale:1.37")
                        && upgraded.events.get("favorite").js.contains("star.scale(1.37)")
                        && upgraded.events.get("unfavorite").css.contains("#123456"),
                "Favorite palette migration lost custom animation or color");
        upgraded.events.get("favorite").css = "--button-color:#313b49;";
        require(MailboxAnimationStore.copy(upgraded).events.get("favorite").css.contains("#313b49"),
                "Favorite palette migration repeated after a deliberate color edit");
    }

    private static void verifyMailRowPresentation(Minecraft game) {
        var font = game.font;
        String shortSubject = "Short subject";
        Object shortFit = invokeStatic(MailRowButton.class, "fit",
                new Class<?>[]{net.minecraft.client.gui.Font.class, String.class, int.class, float.class, float.class},
                font, shortSubject, 180, 1.3F, .85F);
        String shortValue = (String) invoke(shortFit, "value");
        float shortScale = (float) invoke(shortFit, "scale");
        require(shortValue.equals(shortSubject) && shortScale == 1.3F,
                "Short mail subject lost its preferred emphasis");
        Object narrowFit = invokeStatic(MailRowButton.class, "fit",
                new Class<?>[]{net.minecraft.client.gui.Font.class, String.class, int.class, float.class, float.class},
                font, "A very long mail title that must fit in a narrow message row", 92, 1.3F, .85F);
        String narrowValue = (String) invoke(narrowFit, "value");
        float narrowScale = (float) invoke(narrowFit, "scale");
        require(font.width(narrowValue) * narrowScale <= 93
                        && (narrowScale < 1.3F || narrowValue.endsWith("...")),
                "Long mail subject neither scaled nor ellipsized inside its row");
        var unread = new MailboxRuntime.View("ordinary", "", "", "Unread", "", 0, 0,
                false, false, "", false, false, false, false, List.of());
        var treasure = new MailboxRuntime.View("treasure", "", "", "New keepsake", "", 0, 0,
                false, false, "", true, true, true, true, List.of());
        require(!new MailRowButton(0, 0, 120, 42, unread, false, ignored -> {},
                        ButtonStyleSheet.load()).isNew()
                        && new MailRowButton(0, 0, 120, 42, treasure, true, ignored -> {},
                        ButtonStyleSheet.load()).isNew(),
                "New badge is not limited to the keepsake box");
    }

    private static Object invoke(Object target, String name) {
        try {
            var method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Object invokeStatic(Class<?> type, String name, Class<?>[] parameters, Object... args) {
        try {
            var method = type.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void assertDialogCoversRewards(Minecraft game, int background) throws Exception {
        var screen = (MailboxScreen) game.screen;
        var attachmentTop = MailboxScreen.class.getDeclaredMethod("attachmentTop", MailboxRuntime.View.class);
        attachmentTop.setAccessible(true);
        int y = (int) attachmentTop.invoke(screen, field(screen, "selected"));
        int x = (int) call(screen, "split") + 18;
        require(countColor(game, screen, x, y, 34, 48, background) >= 8,
                "Dialog does not cover the first reward card");
        require(countColor(game, screen, x + 38, y, 34, 48, background) >= 8,
                "Dialog does not cover the second reward card");
    }

    private static int countColor(Minecraft game, net.minecraft.client.gui.screens.Screen screen,
                                  int left, int top, int width, int height, int expected) {
        int matches = 0;
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            double sx = image.getWidth() / (double) screen.width, sy = image.getHeight() / (double) screen.height;
            for (int y = top; y < top + height; y++) for (int x = left; x < left + width; x++) {
                int pixel = image.getPixelRGBA((int) ((x + .5) * sx), (int) ((y + .5) * sy));
                int rgb = (pixel & 255) << 16 | pixel & 0xFF00 | pixel >>> 16 & 255;
                if (rgb == expected) matches++;
            }
        }
        return matches;
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void click(Minecraft game, String id) {
        StyledButton button = find(game, id);
        require(button.active, "Disabled button: " + id);
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        require(game.screen.mouseClicked(x, y, 0), "Button click not handled: " + id);
        game.screen.mouseReleased(x, y, 0);
    }

    private static void openCompose(Minecraft game) throws Exception {
        click(game, "mail-compose");
        clickComposeMenu(game, 0);
    }

    private static void clickComposeMenu(Minecraft game, int item) throws Exception {
        int x = (int) call(game.screen, "composeMenuLeft") + 30;
        int y = (int) call(game.screen, "composeMenuTop") + item * 24 + 10;
        require(game.screen.mouseClicked(x, y, 0), "Compose menu item click was ignored");
        game.screen.mouseReleased(x, y, 0);
    }

    private static StyledButton find(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(candidate -> candidate.styleId().equals(id))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing button: " + id));
    }
    private static void assertComposeToolbar(Minecraft game) throws Exception {
        var compose = find(game, "mail-compose");
        var edit = find(game, "mail-edit-animations");
        require(compose.active && compose.visible && compose.getRight() < edit.getX(),
                "Compose mail must be visible to the left of edit");
        require("send".equals(field(compose, "glyph")), "Compose mail has no dedicated send icon");
        require((int) call(game.screen, "headerActionsLeft") == compose.getX(),
                "Mail count does not reserve the compose button area");
        assertIcon(game, "send");
    }
    private static void assertIcon(Minecraft game, String name) throws Exception {
        var icon = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "xero_quest_journal", "textures/gui/navigation/" + name + ".png");
        try (var input = game.getResourceManager().getResourceOrThrow(icon).open();
             var image = com.mojang.blaze3d.platform.NativeImage.read(input)) {
            require(image.getWidth() == 32 && image.getHeight() == 32, name + " icon must be 32x32");
        }
    }
    private static void cursor(Minecraft game, int x, int y) throws Exception {
        double windowX = (double) x * game.getWindow().getScreenWidth() / game.screen.width;
        double windowY = (double) y * game.getWindow().getScreenHeight() / game.screen.height;
        GLFW.glfwSetCursorPos(game.getWindow().getWindow(), windowX, windowY);
        // The test window may be unfocused; GLFW then omits the callback.
        for (var coordinate : java.util.Map.of("xpos", windowX, "ypos", windowY).entrySet()) {
            var field = game.mouseHandler.getClass().getDeclaredField(coordinate.getKey());
            field.setAccessible(true);
            field.setDouble(game.mouseHandler, coordinate.getValue());
        }
    }

    private static int count(Minecraft game, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < game.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = game.player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
