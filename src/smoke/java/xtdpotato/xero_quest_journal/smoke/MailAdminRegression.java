package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxData;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class MailAdminRegression {
    private static final String PREFIX = "mail_admin_" + System.currentTimeMillis();
    private static final String A = PREFIX + "_a", B = PREFIX + "_b", C = PREFIX + "_c", D = PREFIX + "_d";
    private static final String TASK = PREFIX + "_task";
    private static int stage, wait;
    private static CompletableFuture<Void> serverWork;
    private static CompletableFuture<String> denied;
    private static MailboxScreen mailbox;
    private static String resent;
    private static long createdAt, expiresAt;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        wait = 6;
        switch (stage) {
            case 0 -> {
                ClientPreferences.get().reducedMotion = true;
                ClientPreferences.get().mailDraft = null;
                game.options.guiScale().set(2);
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = player(game);
                    try {
                        player.server.getCommands().getDispatcher().execute(
                                "xero_quest edit_mode " + player.getGameProfile().getName() + " true",
                                player.server.createCommandSourceStack());
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                    player.getInventory().clearContent();
                    var data = MailboxData.get(player.server);
                    for (var old : data.messages(player.getUUID()))
                        data.update(player.getUUID(), old.id(), ignored -> null);
                    data.markInitialized(player.getUUID());
                    var choice = choice("Pick your reward", "minecraft:diamond", 2, "minecraft:gold_ingot", 3);
                    MailboxRuntime.sendIfAbsent(player, A, "Xero", "Choose A", "<b>Original body</b>", 86400,
                            List.of(new MailboxRuntime.Attachment("item", "minecraft:paper", 1, false), choice));
                    var view = serverView(player, A);
                    createdAt = view.createdAt(); expiresAt = view.expiresAt();
                    require(MailboxRuntime.claim(player, A).equals("choice_required"), "Legacy claim bypassed choice");
                    require(data.receipt(player.getUUID(), A).isEmpty() && count(player, Items.PAPER) == 0,
                            "Choice preflight poisoned receipt or delivered ordinary items");
                    var bad = new QuestNetwork.MailboxClaimEntry(A, MailboxRuntime.fingerprint(view), Map.of(1, 90));
                    require(MailboxRuntime.claimChoices(player, List.of(bad), false).result().equals("invalid_choice"),
                            "Invalid choice accepted");
                    var extra = new QuestNetwork.MailboxClaimEntry(A, MailboxRuntime.fingerprint(view), Map.of(1, 0, 0, 0));
                    require(MailboxRuntime.claimChoices(player, List.of(extra), false).result().equals("invalid_choice"),
                            "Selection for ordinary attachment accepted");
                    for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                        player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
                    var valid = new QuestNetwork.MailboxClaimEntry(A, MailboxRuntime.fingerprint(view), Map.of(1, 0));
                    require(MailboxRuntime.claimChoices(player, List.of(valid), false).result().equals("inventory_full"),
                            "Full inventory was ignored");
                    require(data.receipt(player.getUUID(), A).isEmpty(), "Full inventory reserved a receipt");
                    player.getInventory().clearContent();
                    require(MailboxRuntime.claimChoices(player, List.of(new QuestNetwork.MailboxClaimEntry(
                            A, "0".repeat(64), Map.of(1, 0))), false).result().equals("conflict"),
                            "Stale choice was accepted");
                    MailboxRuntime.sendIfAbsent(player, B, "Xero", "Choose B", "Bulk B", 0,
                            List.of(choice("Bulk selection", "minecraft:emerald", 3, "minecraft:iron_ingot", 4)));
                    MailboxRuntime.sendIfAbsent(player, C, "Xero", "Normal C", "Bulk C", 0,
                            List.of(new MailboxRuntime.Attachment("item", "minecraft:paper", 4, false)));
                    MailboxRuntime.sendIfAbsent(player, D, "Xero", "Choose D", "Bulk D", 0, List.of(choice));
                    String failed = PREFIX + "_failed";
                    MailboxRuntime.sendIfAbsent(player, failed, "Xero", "Failed", "Legacy failure", 0, List.of(choice));
                    data.receipt(player.getUUID(), failed, "failed");
                    var failureEntry = new QuestNetwork.MailboxClaimEntry(failed,
                            MailboxRuntime.fingerprint(serverView(player, failed)), Map.of(0, 0));
                    require(MailboxRuntime.claimChoices(player, List.of(failureEntry), false).result().equals("reward_failed"),
                            "Uncertain legacy receipt was retried");
                    require(MailboxData.load(data.save(new net.minecraft.nbt.CompoundTag(), player.registryAccess()),
                            player.registryAccess()).receipt(player.getUUID(), failed).equals("failed"),
                            "NBT round-trip lost uncertain receipt");
                    data.update(player.getUUID(), failed, ignored -> null);
                    require(MailboxRuntime.deleteMany(player, Map.of(A, MailboxRuntime.fingerprint(view),
                            "missing", "0".repeat(64))).equals("missing")
                            && MailboxRuntime.check(player, A, "exists"), "Deletion was not atomic");
                    checkLegacyAndScripts(player);
                    player.getInventory().setChanged();
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!serverWork.isDone() || mail(A) == null || !ClientEditMode.enabled()) return false;
                serverWork.join();
                checkCodecs(game);
                mailbox = new MailboxScreen(null, A);
                game.setScreen(mailbox);
                button("mail-claim").onPress();
                require(game.screen instanceof QuestLootChoiceScreen, "Mail claim did not open shared choice screen");
                button("loot-choice-cancel").onPress();
                require(game.screen == mailbox && mail(A).claimable(), "Cancel consumed mail reward");
                button("mail-claim").onPress();
                selectChoice(1);
            }
            case 2 -> {
                capture(game, "mail-choice-selected");
                button("loot-choice-claim").onPress();
            }
            case 3 -> {
                if (!(game.screen instanceof QuestRewardPopupScreen) || mail(A).claimable()) return false;
                @SuppressWarnings("unchecked") var received = (List<QuestDefinition.Reward>) field(game.screen, "rewards");
                require(received.equals(List.of(new QuestDefinition.Reward("item", "minecraft:paper", 1),
                        new QuestDefinition.Reward("item", "minecraft:gold_ingot", 3))), "Mail receipt lost chosen item");
                capture(game, "mail-choice-receipt");
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = player(game);
                    require(count(player, Items.GOLD_INGOT) == 3 && count(player, Items.DIAMOND) == 0
                            && count(player, Items.PAPER) == 1, "Wrong mail choice delivered");
                    require(MailboxRuntime.claim(player, A).equals("no_claimable"), "Mail could be claimed twice");
                }, game.getSingleplayerServer());
                game.setScreen(mailbox);
                openContext(game, A);
            }
            case 4 -> {
                serverWork.join();
                capture(game, "mail-admin-context");
                button("mail-context-edit_mail").onPress();
                require(game.screen instanceof MailComposeScreen, "Mail editing did not open composer");
                ((EditBox) field(game.screen, "subject")).setValue("Edited A");
                ((MultiLineEditBox) field(game.screen, "body")).setValue("<b>Edited body</b>");
                require(!button("mail-compose-rewards").active, "Claimed rewards remained editable");
                button("mail-compose-send").onPress();
            }
            case 5 -> {
                if (!(game.screen instanceof MailboxScreen) || !mail(A).subject().equals("Edited A")) return false;
                require(mail(A).createdAt() == createdAt && mail(A).expiresAt() == expiresAt
                        && !mail(A).claimable(), "Editing reset identity, expiry or claimed state");
                openContext(game, A);
                button("mail-context-resend_mail").onPress();
                require(game.screen instanceof MailComposeScreen, "Resend did not open editor");
                resent = (String) field(game.screen, "id");
                require(!resent.equals(A), "Resend reused original mail ID");
                @SuppressWarnings("unchecked") var attachments = (List<MailboxRuntime.Attachment>) field(game.screen, "rewards");
                require(attachments.stream().noneMatch(MailboxRuntime.Attachment::claimed),
                        "Resent draft retained claimed flags");
                ((EditBox) field(game.screen, "subject")).setValue("Resent A");
                button("mail-compose-send").onPress();
            }
            case 6 -> {
                if (!(game.screen instanceof MailboxScreen) || mail(resent) == null) return false;
                require(mail(resent).claimable() && !mail(A).claimable(), "Resend replaced or reset the original");
                button("mail-multi-select").onPress();
                row(A).onPress(); row(resent).onPress(); row(B).onPress();
                require(selection().containsAll(Set.of(A, resent, B)), "Multi-select cleared previous choices");
                row(B).onPress();
                require(selection().equals(Set.of(A, resent)), "Multi-select could not toggle a row");
                mailbox.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
                require(selection().size() == ClientMailbox.messages().size(), "Ctrl+A did not select all");
                invoke(mailbox, "invertMailSelection");
                require(selection().isEmpty(), "Invert selection did not clear all");
                row(A).onPress(); row(resent).onPress();
            }
            case 7 -> {
                capture(game, "mail-admin-multi-select");
                button("mail-delete-selected").onPress();
                require(game.screen.getClass().getSimpleName().equals("QuestConfirmScreen"),
                        "Batch deletion did not confirm");
                button("overflow-cancel").onPress();
                require(game.screen == mailbox && selection().equals(Set.of(A, resent)), "Delete cancel lost selection");
                button("mail-delete-selected").onPress();
                button("overflow-confirm").onPress();
            }
            case 8 -> {
                if (mail(A) != null || mail(resent) != null || field(mailbox, "pending") != null) return false;
                require(mail(B) != null && mail(C) != null && mail(D) != null && selection().isEmpty(),
                        "Batch deletion affected unselected messages");
                button("mail-multi-select").onPress();
                button("mail-claim_all").onPress();
                require(game.screen.getClass().getSimpleName().equals("QuestConfirmScreen"),
                        "Bulk claim did not use a single confirmation");
                button("overflow-confirm").onPress();
                require(game.screen instanceof QuestLootChoiceScreen, "Bulk claim did not open one choice screen");
                @SuppressWarnings("unchecked") var groups = (List<Object>) field(game.screen, "groups");
                require(groups.size() == 2, "Bulk choice window did not combine all groups");
                // The snapshots sort newest first: D then B; choose gold for D and emerald for B.
                var source = ((List<QuestDefinition.Reward>) field(game.screen, "rewards"));
                int optionIndex = 0;
                for (var reward : source) if (reward.type().equals("loot_choice")) {
                    var candidates = xtdpotato.xero_quest_journal.core.runtime.QuestRewards.choices(reward);
                    int desired = candidates.getFirst().item().equals("minecraft:diamond") ? 1 : 0;
                    selectChoice(optionIndex + desired);
                    optionIndex += candidates.size();
                }
            }
            case 9 -> {
                capture(game, "mail-bulk-choice");
                button("loot-choice-claim").onPress();
            }
            case 10 -> {
                if (mail(B).claimable() || mail(C).claimable() || mail(D).claimable()) return false;
                require(game.screen instanceof QuestRewardPopupScreen, "Bulk choice did not open receipt");
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = player(game);
                    require(count(player, Items.GOLD_INGOT) == 6 && count(player, Items.EMERALD) == 3
                            && count(player, Items.PAPER) == 5, "Bulk choice delivered wrong rewards");
                    var b = serverView(player, B);
                    var attachments = b.attachments().stream().map(a -> new MailboxRuntime.Attachment(
                            a.type(), a.item(), a.count(), false, a.hidden(), a.hidePopup())).toList();
                    var edit = new QuestNetwork.MailboxSendPayload(1, b.recipient(), b.id(), b.sender(),
                            b.subject(), "Changed", b.icon(), b.treasured(), 0, false, attachments);
                    var changedRewards = new QuestNetwork.MailboxSendPayload(1, b.recipient(), b.id(), b.sender(),
                            b.subject(), b.body(), b.icon(), b.treasured(), 0, false,
                            List.of(new MailboxRuntime.Attachment("item", "minecraft:diamond", 99, false)));
                    require(MailboxRuntime.edit(player, changedRewards, MailboxRuntime.fingerprint(b), true)
                            .equals("reward_edit_locked"), "Editing reset a claimed reward");
                    MailboxRuntime.setProperty(player, B, "body", "Concurrent edit");
                    require(MailboxRuntime.edit(player, edit, MailboxRuntime.fingerprint(b), true).equals("conflict"),
                            "Stale mail edit overwrote server data");
                    require(MailboxRuntime.deleteMany(player, Map.of(B, MailboxRuntime.fingerprint(b),
                            C, MailboxRuntime.fingerprint(serverView(player, C)))).equals("conflict")
                            && MailboxRuntime.check(player, C, "exists"), "Stale deletion removed another mail");
                    try {
                        player.server.getCommands().getDispatcher().execute(
                                "xero_quest edit_mode " + player.getGameProfile().getName() + " false",
                                player.server.createCommandSourceStack());
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                    require(!MailboxRuntime.canEdit(player), "Edit mode false did not disable server editing");
                }, game.getSingleplayerServer());
                game.setScreen(mailbox);
            }
            case 11 -> {
                if (!serverWork.isDone() || ClientEditMode.enabled()) return false;
                serverWork.join();
                invoke(mailbox, "init");
                require(game.screen.children().stream().noneMatch(child -> child instanceof StyledButton b
                        && Set.of("mail-multi-select", "mail-compose", "mail-edit-animations").contains(b.styleId())),
                        "Editor controls remained visible with edit mode false");
                openContext(game, B);
                require(((List<?>) field(mailbox, "contextMenu")).isEmpty(), "Non-editor opened author menu");
                denied = ClientMailbox.deleteMany(Map.of(B, MailboxRuntime.fingerprint(mail(B))))
                        .thenApply(QuestNetwork.MailboxActionResultPayload::result);
            }
            case 12 -> {
                if (!denied.isDone()) return false;
                require(denied.join().equals("permission") && mail(B) != null, "Forged deletion bypassed server gate");
                denied = ClientMailbox.edit(mail(B), "Xero", "Forged", "Forged body", "", false, 0, false,
                        mail(B).attachments().stream().map(a -> new MailboxRuntime.Attachment(
                                a.type(), a.item(), a.count(), false, a.hidden(), a.hidePopup())).toList(), true);
            }
            case 13 -> {
                if (!denied.isDone()) return false;
                require(denied.join().equals("permission"), "Forged edit bypassed server gate");
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = player(game);
                    var request = new xtdpotato.xero_quest_journal.core.io.QuestAuthoring.Request(
                            xtdpotato.xero_quest_journal.core.io.QuestAuthoring.Kind.TASK,
                            TASK, "Task choice regression", "", "", "", "guide", "", 1);
                    require(xtdpotato.xero_quest_journal.core.io.QuestAuthoring.create(player, request).successful(),
                            "Task fixture creation failed");
                    String source;
                    try { source = xtdpotato.xero_quest_journal.core.io.QuestRepository.source(TASK); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    var reward = choice("Task choice", "minecraft:diamond", 2, "minecraft:gold_ingot", 3);
                    require(xtdpotato.xero_quest_journal.core.io.QuestAuthoring.editRewards(player, TASK,
                            xtdpotato.xero_quest_journal.core.io.QuestRepository.fingerprint(source),
                            List.of(new QuestDefinition.Reward(reward.type(), reward.item(), reward.count())),
                            "manual").successful(), "Task choice fixture failed");
                    require(xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.trigger(player, TASK,
                            "complete", true).equals("completed"), "Task choice completion failed");
                    player.getInventory().clearContent();
                }, game.getSingleplayerServer());
            }
            case 14 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                if (ClientQuestProgress.catalogRevision()
                        != xtdpotato.xero_quest_journal.core.io.QuestRepository.revision()) return false;
                var rewards = List.of(new QuestDefinition.Reward("loot_choice",
                        choice("Task choice", "minecraft:diamond", 2, "minecraft:gold_ingot", 3).item(), 1));
                game.setScreen(new QuestLootChoiceScreen(mailbox, TASK, "Task choice regression", rewards));
                button("loot-choice-cancel").onPress();
                require(game.screen == mailbox, "Task choice cancellation failed");
                game.setScreen(new QuestLootChoiceScreen(mailbox, TASK, "Task choice regression", rewards));
                selectChoice(0);
                button("loot-choice-claim").onPress();
            }
            case 15 -> {
                if (game.screen instanceof QuestLootChoiceScreen)
                    require(field(game.screen, "error") == null, "Task choice failed: " + field(game.screen, "error"));
                if (!(game.screen instanceof QuestRewardPopupScreen)) return false;
                @SuppressWarnings("unchecked") var receipt = (List<QuestDefinition.Reward>) field(game.screen, "rewards");
                require(receipt.equals(List.of(new QuestDefinition.Reward("item", "minecraft:diamond", 2))),
                        "Shared choice screen changed task receipt");
                serverWork = CompletableFuture.runAsync(() -> {
                    var player = player(game);
                    require(count(player, Items.DIAMOND) == 2 && count(player, Items.GOLD_INGOT) == 0,
                            "Task choice delivered wrong item");
                    require(xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.claim(player, TASK)
                            .equals("already_claimed"), "Task choice could be claimed twice");
                }, game.getSingleplayerServer());
            }
            case 16 -> {
                if (!serverWork.isDone()) return false;
                serverWork.join();
                return true;
            }
            default -> throw new AssertionError("Unknown mail admin stage " + stage);
        }
        stage++;
        return false;
    }

    private static MailboxRuntime.Attachment choice(String title, String first, int firstCount, String second, int secondCount) {
        var encoded = new com.google.gson.JsonObject();
        encoded.addProperty("title", title);
        encoded.add("choices", new com.google.gson.Gson().toJsonTree(List.of(
                new QuestDefinition.Reward("item", first, firstCount), new QuestDefinition.Reward("item", second, secondCount))));
        return new MailboxRuntime.Attachment("loot_choice", encoded.toString(), 1, false);
    }
    private static ServerPlayer player(Minecraft game) {
        return game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
    }
    private static MailboxRuntime.View serverView(ServerPlayer player, String id) {
        return MailboxRuntime.snapshot(player).stream().filter(mail -> mail.id().equals(id)).findFirst().orElseThrow();
    }
    private static MailboxRuntime.View mail(String id) {
        return ClientMailbox.messages().stream().filter(mail -> mail.id().equals(id)).findFirst().orElse(null);
    }
    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void checkLegacyAndScripts(ServerPlayer player) {
        var data = MailboxData.get(player.server);
        String legacyId = PREFIX + "_legacy";
        data.add(player.getUUID(), new MailboxRuntime.Mail(legacyId, "Xero", "Legacy", "Old content",
                System.currentTimeMillis(), 0, false, false,
                List.of(new MailboxRuntime.Attachment("item", "minecraft:stick", 1, false))));
        var legacy = serverView(player, legacyId);
        var entry = new QuestNetwork.MailboxClaimEntry(legacyId, MailboxRuntime.fingerprint(legacy), Map.of());
        require(MailboxRuntime.claimChoices(player, List.of(entry), false).result().equals("claimed"),
                "Legacy mail with missing recipient failed fingerprint validation");
        legacy = serverView(player, legacyId);
        require(MailboxRuntime.deleteMany(player, Map.of(legacyId, MailboxRuntime.fingerprint(legacy))).equals("deleted"),
                "Legacy mail could not be deleted");
        String scriptId = PREFIX + "_script", copyId = scriptId + "_copy";
        var script = new MailboxRuntime.Attachment("script", "message('frozen reward');", 1, false);
        MailboxRuntime.send(player, scriptId, "Xero", "Script", "Frozen script body", 0, List.of(script));
        var source = serverView(player, scriptId);
        var draft = new QuestNetwork.MailboxSendPayload(1, source.recipient(), copyId, source.sender(),
                source.subject(), source.body(), source.icon(), source.treasured(), List.of(script));
        require(MailboxRuntime.resend(player, player, new QuestNetwork.MailboxResendPayload(
                draft, scriptId, MailboxRuntime.fingerprint(source))), "Frozen script could not be resent");
        require(serverView(player, copyId).attachments().equals(List.of(script)), "Resend refroze script source");
        var edit = new QuestNetwork.MailboxSendPayload(2, source.recipient(), scriptId, source.sender(),
                source.subject(), "Edited script mail", source.icon(), source.treasured(),
                List.of(script, new MailboxRuntime.Attachment("item", "minecraft:paper", 2, false)));
        require(MailboxRuntime.edit(player, edit, MailboxRuntime.fingerprint(source), true).equals("edited"),
                "Editing other attachments lost unchanged frozen script");
        data.update(player.getUUID(), scriptId, ignored -> null);
        data.update(player.getUUID(), copyId, ignored -> null);
        player.getInventory().clearContent();
    }
    private static StyledButton button(String id) {
        if (id.startsWith("mail-context-")) {
            try {
                @SuppressWarnings("unchecked") var menu = (List<StyledButton>) field(mailbox, "contextMenu");
                return menu.stream().filter(button -> button.styleId().equals(id)).findFirst()
                        .orElseThrow(() -> new AssertionError("Missing menu button " + id + " at stage " + stage));
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
        return Minecraft.getInstance().screen.children().stream().filter(child -> child instanceof StyledButton b
                        && b.styleId().equals(id)).map(child -> (StyledButton) child).findFirst()
                .orElseThrow(() -> new AssertionError("Missing button " + id + " at stage " + stage));
    }
    private static StyledButton row(String id) { return button("mail-" + id); }
    private static void selectChoice(int index) {
        var options = Minecraft.getInstance().screen.children().stream().filter(child -> child instanceof StyledButton b
                && b.styleId().equals("loot-choice-option")).map(child -> (StyledButton) child).toList();
        options.get(index).onPress();
    }
    @SuppressWarnings("unchecked")
    private static Set<String> selection() throws Exception { return (Set<String>) field(mailbox, "selectedMailIds"); }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static void openContext(Minecraft game, String id) {
        var row = row(id);
        mailbox.mouseClicked(row.getX() + 15, row.getY() + 10, 1);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        game.getToasts().clear();
        Path output = Path.of("..", "build", "verification", name + ".png");
        Files.createDirectories(output.getParent());
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(output); }
    }
    private static void checkCodecs(Minecraft game) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                game.player.registryAccess());
        try {
            var entry = new QuestNetwork.MailboxClaimEntry(A, MailboxRuntime.fingerprint(mail(A)), Map.of(1, 1));
            var payload = new QuestNetwork.MailboxClaimPayload(9, List.of(entry), false);
            QuestNetwork.MailboxClaimPayload.STREAM_CODEC.encode(buffer, payload);
            require(QuestNetwork.MailboxClaimPayload.STREAM_CODEC.decode(buffer).equals(payload), "Mail claim codec");
            var deletion = new QuestNetwork.MailboxDeletePayload(10, Map.of(A, entry.expectedHash()));
            QuestNetwork.MailboxDeletePayload.STREAM_CODEC.encode(buffer, deletion);
            require(QuestNetwork.MailboxDeletePayload.STREAM_CODEC.decode(buffer).equals(deletion), "Mail delete codec");
            var draft = new QuestNetwork.MailboxSendPayload(11, "Dev", A, "Xero", "Subject", "Body", "", false, List.of());
            var edit = new QuestNetwork.MailboxEditPayload(draft, entry.expectedHash(), true);
            QuestNetwork.MailboxEditPayload.STREAM_CODEC.encode(buffer, edit);
            require(QuestNetwork.MailboxEditPayload.STREAM_CODEC.decode(buffer).equals(edit), "Mail edit codec");
            var resend = new QuestNetwork.MailboxResendPayload(draft, A, entry.expectedHash());
            QuestNetwork.MailboxResendPayload.STREAM_CODEC.encode(buffer, resend);
            require(QuestNetwork.MailboxResendPayload.STREAM_CODEC.decode(buffer).equals(resend), "Mail resend codec");
            try {
                new QuestNetwork.MailboxClaimPayload(12, List.of(entry, entry), false);
                throw new AssertionError("Duplicate mail claims accepted");
            } catch (IllegalArgumentException expected) {}
        } finally { buffer.release(); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (mail admin stage " + stage + ")");
    }
}
