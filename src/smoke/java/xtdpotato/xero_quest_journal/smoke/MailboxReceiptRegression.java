package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxData;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MailboxReceiptRegression {
    private static int stage, wait;
    private static boolean reducedMotion;
    private static CompletableFuture<Void> serverWork;
    private static List<MailboxRuntime.Attachment> edited;
    private static final List<ItemStack> inventory = new ArrayList<>();
    private static MailboxScreen parent;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                reducedMotion = ClientPreferences.get().reducedMotion;
                ClientPreferences.get().reducedMotion = true;
                parent = new MailboxScreen();
                game.setScreen(QuestRewardsScreen.mail(parent, List.of(
                        new MailboxRuntime.Attachment("item", "minecraft:diamond", 1, false,
                                false, false, "rare", "#327A65"),
                        new MailboxRuntime.Attachment("command", "tag @s add hidden_mail_ui", 1, false, true, false)),
                        values -> edited = values));
            }
            case 1 -> {
                var boxes = game.screen.children().stream().filter(Checkbox.class::isInstance)
                        .map(Checkbox.class::cast).toList();
                require(boxes.size() == 4 && boxes.get(0).selected() && boxes.get(1).selected()
                                && !boxes.get(2).selected() && !boxes.get(3).active,
                        "Mail editor did not load visibility/popup defaults");
                boxes.get(1).onPress();
                choice(game, "rewards-quality-0").select("mythic");
                capture(game, "mailbox-reward-presentation-editor");
                button(game, "rewards-save");
                require(edited != null && edited.getFirst().hidePopup() && !edited.getFirst().hidden()
                                && edited.get(1).hidden() && edited.getFirst().quality().equals("mythic")
                                && edited.getFirst().background().isBlank(),
                        "Mail editor did not apply quality or clear the conflicting custom background");
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    var styled = new MailboxRuntime.Attachment("item", "minecraft:diamond", 1, false,
                            false, false, "mythic", "");
                    var customBackground = new MailboxRuntime.Attachment("item", "minecraft:emerald", 2, false,
                            false, true, "", "#327A65");
                    MailboxRuntime.sendIfAbsent(player, "receipt_mixed", "Xero", "Item receipt", "Mixed attachments", 0,
                            List.of(styled,
                                    customBackground,
                                    new MailboxRuntime.Attachment("item", "minecraft:gold_ingot", 3, false, true, false),
                                    new MailboxRuntime.Attachment("command", "tag @s add hidden_mail_ui", 1, false, true, false),
                                    new MailboxRuntime.Attachment("experience", "", 2, false)));
                    var saved = MailboxData.get(server).save(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
                    var restored = MailboxData.load(saved, player.registryAccess()).messages(player.getUUID()).stream()
                            .filter(mail -> mail.id().equals("receipt_mixed")).findFirst().orElseThrow();
                    require(restored.attachments().getFirst().equals(styled),
                            "Mail item quality did not survive NBT save/load");
                    require(restored.attachments().get(1).equals(customBackground),
                            "Mail custom background did not survive NBT save/load");
                    MailboxRuntime.sendIfAbsent(player, "receipt_silent", "Xero", "Silent item", "No popup", 0,
                            List.of(new MailboxRuntime.Attachment("item", "minecraft:paper", 1, false, false, true)));
                    MailboxRuntime.sendIfAbsent(player, "receipt_command", "Xero", "Hidden command", "No visible attachment", 0,
                            List.of(new MailboxRuntime.Attachment("command", "tag @s add command_mail_ui", 1, false, true, false)));
                }, server);
            }
            case 2 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                ClientMailbox.request();
            }
            case 3 -> {
                if (ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals("receipt_mixed"))) return false;
                var synced = ClientMailbox.messages().stream()
                        .filter(mail -> mail.id().equals("receipt_mixed")).findFirst().orElseThrow();
                require(synced.attachments().getFirst().quality().equals("mythic")
                                && synced.attachments().get(1).background().equals("#327A65"),
                        "Mail item quality/background were lost over the mailbox snapshot network codec");
                game.setScreen(new MailboxScreen(null, "receipt_mixed"));
            }
            case 4 -> {
                var selected = (MailboxRuntime.View) field(game.screen, "selected");
                var visible = MailboxScreen.class.getDeclaredMethod("visibleAttachments", MailboxRuntime.View.class);
                visible.setAccessible(true);
                require(((List<?>) visible.invoke(null, selected)).size() == 3, "Hidden attachments left visible cards");
                verifyFooter(game, true);
                capture(game, "mailbox-hidden-rewards-grid");
                button(game, "mail-claim");
            }
            case 5 -> {
                if (!(game.screen instanceof QuestRewardPopupScreen)) return false;
                require(field(game.screen, "selectedReward") == null
                                && field(game.screen, "rewards").equals(List.of(
                                new QuestDefinition.Reward("item", "minecraft:diamond", 1, "mythic", ""))),
                        "Mail receipt included silent, hidden or non-item rewards");
                capture(game, "mailbox-mixed-item-receipt");
                game.setScreen((Screen) field(game.screen, "parent"));
                verifyFooter(game, false);
                capture(game, "mailbox-claimed-delete-footer");
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    require(player.getTags().contains("hidden_mail_ui")
                                    && MailboxRuntime.check(player, "receipt_mixed", "claimed"),
                            "Hidden command was not executed");
                }, server);
            }
            case 6 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                game.setScreen(new MailboxScreen(null, "receipt_silent"));
                button(game, "mail-claim");
            }
            case 7 -> {
                if (!claimed("receipt_silent")) return false;
                require(game.screen instanceof MailboxScreen && field(game.screen, "pending") == null,
                        "Disabled item popup still opened");
                game.setScreen(new MailboxScreen(null, "receipt_command"));
                button(game, "mail-claim");
            }
            case 8 -> {
                if (!claimed("receipt_command")) return false;
                require(game.screen instanceof MailboxScreen && field(game.screen, "pending") == null,
                        "Command-only claim opened a popup");
                verifyFooter(game, false);
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    require(player.getTags().contains("command_mail_ui"), "Hidden-only mail did not execute");
                    for (var stack : player.getInventory().items) inventory.add(stack.copy());
                    for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                        player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                    MailboxRuntime.sendIfAbsent(player, "receipt_full", "Xero", "Inventory full", "Do not show success", 0,
                            List.of(new MailboxRuntime.Attachment("item", "minecraft:diamond", 1, false)));
                }, server);
            }
            case 9 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                ClientMailbox.request();
            }
            case 10 -> {
                if (ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals("receipt_full"))) return false;
                game.setScreen(new MailboxScreen(null, "receipt_full"));
                button(game, "mail-claim");
            }
            case 11 -> {
                require(game.screen instanceof MailboxScreen, "Failed claim opened a success receipt");
                if (field(game.screen, "pending") != null) return false;
                require("inventory_full".equals(field(game.screen, "actionMessage")) && !claimed("receipt_full"),
                        "Full inventory did not preserve pending rewards");
                verifyFooter(game, true);
                var server = game.getSingleplayerServer();
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    for (int slot = 0; slot < inventory.size(); slot++) player.getInventory().setItem(slot, inventory.get(slot));
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    player.removeTag("hidden_mail_ui");
                    player.removeTag("command_mail_ui");
                    var data = MailboxData.get(server);
                    for (var mail : data.messages(player.getUUID()))
                        if (mail.id().startsWith("receipt_")) data.update(player.getUUID(), mail.id(), ignored -> null);
                }, server);
            }
            case 12 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                ClientPreferences.get().reducedMotion = reducedMotion;
                game.setScreen(parent);
                return true;
            }
            default -> throw new AssertionError("Unexpected receipt test stage");
        }
        stage++;
        wait = 12;
        return false;
    }

    private static boolean claimed(String id) {
        return ClientMailbox.messages().stream().anyMatch(mail -> mail.id().equals(id) && !mail.claimable());
    }
    private static void verifyFooter(Minecraft game, boolean claimable) {
        var buttons = game.screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).toList();
        require(buttons.stream().noneMatch(b -> b.styleId().equals(claimable ? "mail-delete" : "mail-claim")),
                "Claim and delete were displayed for the same mail");
        var action = buttons.stream().filter(b -> b.styleId().equals(claimable ? "mail-claim" : "mail-delete"))
                .findFirst().orElseThrow();
        var star = buttons.stream().filter(b -> b.styleId().equals("mail-star")).findFirst().orElseThrow();
        require(action.getRight() == game.screen.width - 24 && action.getY() == game.screen.height - 32,
                "Mail action is not anchored to the bottom right");
        require(star.getRight() < action.getX() && star.getY() >= action.getY()
                        && star.getBottom() <= action.getBottom(),
                "Favorite button overlaps the mail action");
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static void button(Minecraft game, String id) {
        game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow().onPress();
    }
    @SuppressWarnings("unchecked")
    private static StyledChoiceButton<String> choice(Minecraft game, String id) {
        return (StyledChoiceButton<String>) game.screen.children().stream()
                .filter(StyledChoiceButton.class::isInstance).map(StyledChoiceButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (mail receipt stage " + stage + ")");
    }
}
