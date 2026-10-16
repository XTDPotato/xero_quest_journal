package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xtdpotato.xero_quest_journal.core.io.QuestPresentationDocuments;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxData;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class MailboxNodeRegression {
    static final String QUEST = "mail_graph_regression", MAIL = "mail_graph_attachment";
    static TriggerGraph graph;
    private static final Gson GSON = new Gson();

    static void run(ServerPlayer player) throws Exception {
        var inventory = new ArrayList<ItemStack>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            inventory.add(player.getInventory().getItem(i).copy());
        var data = MailboxData.get(player.server);
        try {
            ItemStack custom = new ItemStack(Items.IRON_SWORD);
            custom.set(DataComponents.CUSTOM_NAME, Component.literal("Mailbox component-preserving reward ".repeat(10)));
            custom.setDamageValue(17);
            String encoded = QuestItemStackValue.encode(custom, player.registryAccess());
            require(encoded.length() > 256, "Fixture must exceed legacy mailbox item limit");
            var send = node("send", "mail_send", Map.of("mailId", MAIL, "player", "@s", "sender", "Quest team",
                    "subject", "A new assignment", "body", "Your equipment is attached.",
                    "expiresIn", "0", "once", "true", "rewards",
                    GSON.toJson(List.of(new QuestDefinition.Reward("item", encoded, 1)))));
            graph = new TriggerGraph(List.of(
                    node("event", "event", Map.of("event", "click")),
                    send,
                    node("check", "mail_check", Map.of("mailId", MAIL, "state", "claimable")),
                    node("property", "mail_set_property", Map.of("mailId", MAIL, "property", "starred", "value", "true")),
                    node("delete", "mail_delete", Map.of("mailId", MAIL)),
                    node("protected", "set_property", Map.of("control", "mail", "property", "value", "value", "Attachment protected"))),
                    List.of(new TriggerGraph.Edge("event", "send", "next"),
                            new TriggerGraph.Edge("send", "check", "true"),
                            new TriggerGraph.Edge("check", "property", "true"),
                            new TriggerGraph.Edge("property", "delete", "true"),
                            new TriggerGraph.Edge("delete", "protected", "false")));
            var presentation = new QuestPresentation(true, 395, 275, List.of(
                    new QuestPresentation.Control("mail", "button", "Mail", 10, 10, 100, 24, "", Map.of(), graph)));
            String source = QuestPresentationDocuments.write(
                    "{\"id\":\"" + QUEST + "\",\"title\":\"Mailbox graph\",\"type\":\"guide\"}", presentation);
            Files.createDirectories(QuestRepository.root());
            Files.writeString(QuestRepository.root().resolve(QUEST + ".json"), source);
            QuestRepository.reload();
            require(QuestRepository.errors().isEmpty(), "Mail graph rejected: " + QuestRepository.errors());
            var quest = QuestRepository.find(QUEST);
            require(quest.presentation().equals(presentation), "Mail graph source round-trip failed");
            require(QuestRepository.parseDefinition(Path.of("mail.html"),
                    QuestPresentationDocuments.write("<quest id=\"mail_html\"/>", presentation))
                    .presentation().equals(presentation), "Mail graph HTML round-trip failed");
            QuestEventGraphs.click(player, QUEST, "mail");
            require(MailboxRuntime.check(player, MAIL, "claimable") && MailboxRuntime.check(player, MAIL, "starred"),
                    "Send/check/property graph chain did not execute");
            var implicitPlayer = node("implicit", "mail_send", Map.of(
                    "mailId", "mail_graph_implicit", "subject", "To self", "body", "Current player"));
            require(QuestMailActions.execute(player, quest, "mail", implicitPlayer)
                            && MailboxRuntime.check(player, "mail_graph_implicit", "exists"),
                    "Blank mail player selector did not target triggering player");
            var missingPlayer = node("missing_player", "mail_check", Map.of(
                    "mailId", MAIL, "player", "NoSuchPlayerEver"));
            require(!QuestMailActions.execute(player, quest, "mail", missingPlayer),
                    "Offline player selector unexpectedly targeted triggering player");
            require(QuestControlData.get(player).properties(player, quest, "mail").get("value")
                    .equals("Attachment protected"), "Delete failure branch did not execute");
            var stranger = new ServerPlayer(player.server, player.server.overworld(),
                    new GameProfile(UUID.randomUUID(), "MailboxOther"), ClientInformation.createDefault());
            require(!MailboxRuntime.check(stranger, MAIL, "exists")
                    && !MailboxRuntime.setProperty(stranger, MAIL, "body", "wrong player")
                    && MailboxRuntime.delete(stranger, MAIL, true).equals("missing"),
                    "Mail operations crossed player boundaries");
            var snapshot = new QuestNetwork.MailboxSnapshotPayload(MailboxRuntime.snapshot(player));
            require(snapshot.messages().stream().allMatch(mail ->
                    mail.recipient().equals(player.getGameProfile().getName())),
                    "Mailbox snapshot lost recipient");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            try {
                QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
                require(QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.decode(buffer).equals(snapshot),
                        "Component-rich attachments failed network round-trip");
            } finally { buffer.release(); }
            var noticeBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            try {
                var notice = new QuestNetwork.MailboxNoticePayload(MAIL, "Quest team", "A new assignment");
                QuestNetwork.MailboxNoticePayload.STREAM_CODEC.encode(noticeBuffer, notice);
                require(QuestNetwork.MailboxNoticePayload.STREAM_CODEC.decode(noticeBuffer).equals(notice),
                        "Mail notification failed network round-trip");
            } finally { noticeBuffer.release(); }
            require(MailboxRuntime.claim(player, MAIL).equals("claimed"), "Attachment claim failed");
            require(player.getInventory().items.stream().anyMatch(stack ->
                    ItemStack.isSameItemSameComponents(stack, custom)), "Claim lost item components");
            require(MailboxRuntime.check(player, MAIL, "claimed") && MailboxRuntime.check(player, MAIL, "read"),
                    "Claim did not mark read/claimed");
            int after = count(player, Items.IRON_SWORD);
            require(QuestMailActions.execute(player, quest, "mail", send), "Repeated send should be idempotent success");
            require(MailboxRuntime.claim(player, MAIL).equals("no_claimable")
                    && count(player, Items.IRON_SWORD) == after, "Repeated send duplicated attachments");
            for (var entry : Map.of("sender", "New sender", "subject", "Revised subject",
                    "body", "Revised body", "expiresIn", "60", "read", "false", "starred", "false").entrySet())
                require(MailboxRuntime.setProperty(player, MAIL, entry.getKey(), entry.getValue()), "Mail property failed");
            var saved = MailboxData.load(data.save(new net.minecraft.nbt.CompoundTag(), player.registryAccess()),
                    player.registryAccess()).messages(player.getUUID()).stream().filter(mail -> mail.id().equals(MAIL)).findFirst().orElseThrow();
            require(saved.subject().equals("Revised subject") && saved.body().equals("Revised body")
                    && saved.recipient().equals(player.getGameProfile().getName())
                    && !saved.read() && !saved.starred() && saved.expiresAt() > System.currentTimeMillis()
                    && saved.attachments().getFirst().claimed(), "Updated mail did not persist correctly");
            require(MailboxRuntime.delete(player, MAIL).equals("deleted"), "Claimed mail deletion failed");
            QuestMailActions.execute(player, quest, "mail", send);
            require(!MailboxRuntime.check(player, MAIL, "exists"), "Once-send recreated deleted mail");
            MailboxRuntime.send(player, "mail_graph_expired", "Xero", "Expired", "",
                    System.currentTimeMillis() - 1, List.of(new MailboxRuntime.Attachment("item", "minecraft:diamond", 2, false)));
            require(MailboxRuntime.claim(player, "mail_graph_expired").equals("missing"),
                    "Expired mail was claimable before requesting snapshot");
            require(MailboxRuntime.check(player, "mail_graph_expired", "missing"), "Expired detection failed");
            require(MailboxRuntime.sendIfAbsent(player, "mail_graph_notice", "", "Notice", "", 0, List.of()),
                    "Plain notice send failed");
            require(MailboxRuntime.check(player, "mail_graph_notice", "unread"), "Notice not unread");
            MailboxRuntime.markRead(player, "mail_graph_notice");
            require(MailboxRuntime.check(player, "mail_graph_notice", "read")
                    && !MailboxRuntime.check(player, "mail_graph_notice", "claimed"), "Plain notice state incorrect");
            verifyKeepsakes(player);
            verifyPresentation(player);
            for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            MailboxRuntime.sendIfAbsent(player, "mail_graph_full", "", "Full inventory", "", 0,
                    List.of(new MailboxRuntime.Attachment("item", "minecraft:diamond", 1, false)));
            require(MailboxRuntime.claim(player, "mail_graph_full").equals("inventory_full")
                    && MailboxRuntime.check(player, "mail_graph_full", "claimable"),
                    "Full inventory claim lost attachments");
            require(MailboxRuntime.delete(player, "mail_graph_full", true).equals("deleted"), "Explicit discard failed");
            reject(() -> MailboxRuntime.setProperty(player, "mail_graph_notice", "claimed", "false"),
                    "Property action can reset rewards");
            reject(() -> node("bad", "mail_send", Map.of("expiresIn", "-1")), "Negative expiry accepted");
            reject(() -> node("bad", "mail_send", Map.of("player", "@!")), "Invalid player selector accepted");
            reject(() -> new TriggerGraph(graph.nodes(), List.of(new TriggerGraph.Edge("send", "check", "next"))),
                    "Mail node accepted wrong output port");
        } finally {
            for (var mail : data.messages(player.getUUID())) if (mail.id().startsWith("mail_graph_"))
                data.update(player.getUUID(), mail.id(), ignored -> null);
            for (int slot = 0; slot < inventory.size(); slot++) player.getInventory().setItem(slot, inventory.get(slot));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void verifyKeepsakes(ServerPlayer player) {
        var data = MailboxData.get(player.server);
        String id = "mail_graph_keepsake";
        var attachments = List.of(new MailboxRuntime.Attachment("item", "minecraft:emerald", 3, false),
                new MailboxRuntime.Attachment("experience", "", 7, false),
                new MailboxRuntime.Attachment("levels", "", 1, false));
        int experience = player.totalExperience, levels = player.experienceLevel;
        float progress = player.experienceProgress;
        int emeralds = count(player, Items.EMERALD);
        try {
            require(MailboxRuntime.sendIfAbsent(player, id, "Sender", "Keepsake", "Both lists", 1,
                    "minecraft:diamond", true, attachments), "Keepsake send failed");
            MailboxRuntime.toggleStarred(player, id);
            MailboxRuntime.markRead(player, id);
            require(MailboxRuntime.snapshot(player).stream().anyMatch(mail ->
                            mail.id().equals(id) && mail.treasured() && mail.starred()
                                    && mail.icon().equals("minecraft:diamond")), "Mail metadata was lost");
            var copy = MailboxData.load(data.save(new net.minecraft.nbt.CompoundTag(), player.registryAccess()),
                    player.registryAccess());
            var saved = copy.messages(player.getUUID()).stream().filter(mail -> mail.id().equals(id)).findFirst().orElseThrow();
            require(saved.treasured() && saved.icon().equals("minecraft:diamond"), "Metadata NBT round-trip failed");
            copy.purgeExpired(player.getUUID(), System.currentTimeMillis() + 120_000);
            require(copy.messages(player.getUUID()).stream().anyMatch(mail -> mail.id().equals(id)), "Keepsake expired");
            var wire = new QuestNetwork.MailboxSendPayload(5, "@s", id, "Sender", "Subject", "Body",
                    "minecraft:diamond", true, attachments);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            try {
                QuestNetwork.MailboxSendPayload.STREAM_CODEC.encode(buffer, wire);
                require(QuestNetwork.MailboxSendPayload.STREAM_CODEC.decode(buffer).equals(wire), "Mail draft codec lost rewards");
            } finally { buffer.release(); }
            require(MailboxRuntime.claim(player, id).equals("claimed"), "Mixed rewards could not be claimed");
            require(count(player, Items.EMERALD) == emeralds + 3 && player.totalExperience == experience + 7
                    && player.experienceLevel >= levels + 1, "Mixed rewards were not delivered");
            require(MailboxRuntime.claim(player, id).equals("no_claimable"), "Mixed rewards can be claimed twice");
            MailboxRuntime.deleteRead(player);
            require(MailboxRuntime.check(player, id, "exists"), "Quick deletion removed keepsake");
            MailboxRuntime.toggleStarred(player, id);
            MailboxRuntime.deleteRead(player);
            require(MailboxRuntime.check(player, id, "exists"), "Unmarked keepsake was quick-deleted");
            require(MailboxRuntime.delete(player, id).equals("deleted"), "Explicit keepsake deletion failed");
            String marked = "mail_graph_marked";
            MailboxRuntime.send(player, marked, "Sender", "Marked", "Ordinary", System.currentTimeMillis() + 60000, List.of());
            MailboxRuntime.toggleStarred(player, marked);
            MailboxRuntime.markRead(player, marked);
            MailboxRuntime.deleteRead(player);
            require(MailboxRuntime.snapshot(player).stream().anyMatch(mail -> mail.id().equals(marked)
                    && mail.starred() && !mail.treasured()), "Marking changed keepsake membership or quick delete protection");
            copy = MailboxData.load(data.save(new net.minecraft.nbt.CompoundTag(), player.registryAccess()), player.registryAccess());
            copy.purgeExpired(player.getUUID(), System.currentTimeMillis() + 120000);
            require(copy.messages(player.getUUID()).stream().noneMatch(mail -> mail.id().equals(marked)), "Ordinary marked mail never expires");
        } finally {
            player.totalExperience = experience;
            player.experienceLevel = levels;
            player.experienceProgress = progress;
        }
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void verifyPresentation(ServerPlayer player) throws Exception {
        String id = "mail_graph_presentation";
        var attachments = List.of(
                new MailboxRuntime.Attachment("item", "minecraft:diamond", 1, false,
                        false, false, "mythic", "#327A65"),
                new MailboxRuntime.Attachment("item", "minecraft:emerald", 2, false,
                        false, true, "rare", ""),
                new MailboxRuntime.Attachment("item", "minecraft:gold_ingot", 3, false,
                        true, false, "epic", ""),
                new MailboxRuntime.Attachment("command", "tag @s add mail_hidden_reward", 1, false, true, true));
        var node = node("presentation", "mail_send", Map.of("rewards", GSON.toJson(attachments)));
        require(QuestMailActions.attachments(node).equals(attachments), "Mail node discarded presentation flags");
        var draft = new xtdpotato.xero_quest_journal.core.mailbox.MailDraft(id, "Sender", "Subject", "Body", "",
                false, List.of(), attachments);
        require(GSON.fromJson(GSON.toJson(draft), draft.getClass()).equals(draft), "Mail bundle lost flags");
        var legacy = GSON.fromJson("""
                {"id":"old","sender":"Xero","subject":"Legacy","body":"","icon":"","treasured":false,
                 "rewards":[{"type":"item","item":"minecraft:diamond","count":1}]}
                """, xtdpotato.xero_quest_journal.core.mailbox.MailDraft.class);
        require(legacy.attachments().equals(List.of(
                        new MailboxRuntime.Attachment("item", "minecraft:diamond", 1, false))),
                "Legacy bundles no longer default missing reward quality");
        var wire = new QuestNetwork.MailboxSendPayload(7, "@s", id, "Sender", "Subject", "Body", "", false, attachments);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            QuestNetwork.MailboxSendPayload.STREAM_CODEC.encode(buffer, wire);
            require(QuestNetwork.MailboxSendPayload.STREAM_CODEC.decode(buffer).equals(wire), "Send codec lost flags");
            require(MailboxRuntime.sendIfAbsent(player, id, "Sender", "Subject", "Body", 0, attachments),
                    "Presentation mail failed to send");
            var snapshot = new QuestNetwork.MailboxSnapshotPayload(MailboxRuntime.snapshot(player));
            QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            require(QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.decode(buffer).equals(snapshot), "Snapshot codec lost flags");
            var data = MailboxData.get(player.server);
            var saved = data.save(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
            var restored = MailboxData.load(saved, player.registryAccess());
            require(restored.messages(player.getUUID()).stream().filter(mail -> mail.id().equals(id))
                    .findFirst().orElseThrow().attachments().equals(attachments), "NBT lost flags");
            var receipt = MailboxRuntime.receiptItems(player, id, false);
            require(receipt.equals(List.of(new QuestDefinition.Reward(
                            "item", "minecraft:diamond", 1, "mythic", "#327A65"))),
                    "Receipt includes hidden, silent or non-item rewards");
            var result = new QuestNetwork.MailboxActionResultPayload(8, "claimed", receipt);
            QuestNetwork.MailboxActionResultPayload.STREAM_CODEC.encode(buffer, result);
            require(QuestNetwork.MailboxActionResultPayload.STREAM_CODEC.decode(buffer).equals(result), "Receipt codec mismatch");
            int diamonds = count(player, Items.DIAMOND), emeralds = count(player, Items.EMERALD), gold = count(player, Items.GOLD_INGOT);
            require(MailboxRuntime.claim(player, id).equals("claimed"), "Presentation mail claim failed");
            require(count(player, Items.DIAMOND) == diamonds + 1 && count(player, Items.EMERALD) == emeralds + 2
                    && count(player, Items.GOLD_INGOT) == gold + 3 && player.getTags().contains("mail_hidden_reward"),
                    "Hidden or silent rewards did not execute");
            require(MailboxRuntime.receiptItems(player, id, false).isEmpty(), "Claimed rewards appeared in another receipt");
            require(data.messages(player.getUUID()).stream().filter(mail -> mail.id().equals(id)).findFirst().orElseThrow()
                    .attachments().equals(attachments.stream().map(MailboxRuntime.Attachment::asClaimed).toList()),
                    "Claim discarded presentation flags");
        } finally {
            buffer.release();
            player.removeTag("mail_hidden_reward");
        }
    }
    private static TriggerGraph.Node node(String id, String type, Map<String, String> data) {
        int index = List.of("event", "send", "check", "property", "delete", "protected").indexOf(id);
        return new TriggerGraph.Node(id, type, Math.max(0, index) * 200, 0, data);
    }
    private static void reject(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new IllegalStateException(message);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
