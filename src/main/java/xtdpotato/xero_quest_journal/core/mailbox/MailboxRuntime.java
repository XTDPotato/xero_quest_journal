package xtdpotato.xero_quest_journal.core.mailbox;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.model.MailNodeSettings;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.util.*;

/** Server-side mailbox operations and the public delivery entry point. */
public final class MailboxRuntime {
    public static final int MAX_MESSAGES = 1000;
    public static final String EDIT_MODE_KEY = "xero_quest_edit_mode";
    private static final Set<UUID> DELIVERING = new HashSet<>();

    private MailboxRuntime() {
    }

    public record Attachment(String type, String item, int count, boolean claimed,
                             boolean hidden, boolean hidePopup) {
        public Attachment(String type, String item, int count, boolean claimed) {
            this(type, item, count, claimed, false, false);
        }

        public Attachment {
            type = type == null || type.isBlank() ? "item" : type;
            item = item == null ? "" : item;
            count = Math.max(0, count);
        }

        public Attachment asClaimed() {
            return new Attachment(type, item, count, true, hidden, hidePopup);
        }

        public boolean showsReceipt() {
            return type.equals("item") && count > 0 && !claimed && !hidden && !hidePopup;
        }
    }

    public record Mail(String id, String sender, String recipient, String subject, String body, long createdAt,
                       long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                       boolean archived, boolean treasureNew, boolean treasureUnseen,
                       boolean gameTime,
                       List<Attachment> attachments, String banner, String targetTaskId,
                       MailBannerTiming bannerTiming, long bannerDeadline) {
        public Mail(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    boolean archived, boolean treasureNew, boolean treasureUnseen, boolean gameTime,
                    List<Attachment> attachments, String banner, String targetTaskId) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, icon, treasured,
                    archived, treasureNew, treasureUnseen, gameTime, attachments, banner, targetTaskId,
                    MailBannerTiming.NONE, 0);
        }
        public Mail(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    boolean archived, boolean treasureNew, boolean treasureUnseen, boolean gameTime,
                    List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, icon, treasured,
                    archived, treasureNew, treasureUnseen, gameTime, attachments, "", "");
        }
        public Mail {
            id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
            sender = sender == null ? "" : sender;
            recipient = recipient == null ? "" : recipient;
            subject = subject == null ? "" : subject;
            body = body == null ? "" : body;
            icon = icon == null ? "" : icon;
            banner = banner == null ? "" : banner;
            targetTaskId = targetTaskId == null ? "" : targetTaskId;
            bannerTiming = bannerTiming == null ? MailBannerTiming.NONE : bannerTiming;
            if (banner.length() > 8192 || targetTaskId.length() > 256)
                throw new IllegalArgumentException("Mail banner or destination exceeds limit");
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
            archived = treasured && archived;
            treasureNew = archived && treasureNew;
            treasureUnseen = archived && treasureUnseen;
        }

        public Mail(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    boolean archived, boolean treasureNew, boolean treasureUnseen, List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, icon, treasured,
                    archived, treasureNew, treasureUnseen, false, attachments);
        }

        public Mail(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred,
                    icon, treasured, false, false, false, attachments);
        }

        public Mail(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, "", false, attachments);
        }

        public Mail(String id, String sender, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, List<Attachment> attachments) {
            this(id, sender, "", subject, body, createdAt, expiresAt, read, starred, attachments);
        }

        public Mail markRead() {
            return new Mail(id, sender, recipient, subject, body, createdAt, expiresAt, true, starred,
                    icon, treasured, archived, treasureNew, treasureUnseen, gameTime, attachments, banner, targetTaskId,
                    bannerTiming, bannerDeadline);
        }

        public Mail withAttachments(List<Attachment> next) {
            return new Mail(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred,
                    icon, treasured, archived, treasureNew, treasureUnseen, gameTime, next, banner, targetTaskId,
                    bannerTiming, bannerDeadline);
        }

        public Mail toggleStarred() {
            return new Mail(id, sender, recipient, subject, body, createdAt, expiresAt, read, !starred,
                    icon, treasured, archived, treasureNew, treasureUnseen, gameTime, attachments, banner, targetTaskId,
                    bannerTiming, bannerDeadline);
        }

        public Mail archiveIfReady() {
            // Rewardless keepsakes enter on opening; claiming rewards never requires reading first.
            if (!treasured || archived || claimable()
                    || !read && attachments.stream().noneMatch(attachment -> attachment.count() > 0)) return this;
            return new Mail(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred,
                    icon, true, true, true, true, gameTime, attachments, banner, targetTaskId,
                    bannerTiming, bannerDeadline);
        }

        public Mail openTreasure() {
            if (!archived) return this;
            return new Mail(id, sender, recipient, subject, body, createdAt, expiresAt, true, starred,
                    icon, treasured, true, false, treasureUnseen, gameTime, attachments, banner, targetTaskId,
                    bannerTiming, bannerDeadline);
        }

        public Mail acknowledgeTreasure() {
            return new Mail(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred,
                    icon, treasured, archived, treasureNew, false, gameTime, attachments, banner, targetTaskId,
                    bannerTiming, bannerDeadline);
        }

        public Mail claimRewards() {
            return new Mail(id, sender, recipient, subject, body, createdAt, expiresAt, read || !treasured,
                    starred, icon, treasured, archived, treasureNew, treasureUnseen, gameTime,
                    attachments.stream().map(Attachment::asClaimed).toList(), banner, targetTaskId,
                    bannerTiming, bannerDeadline);
        }

        public boolean claimable() {
            return attachments.stream().anyMatch(attachment -> !attachment.claimed() && attachment.count() > 0);
        }
    }

    public record View(String id, String sender, String recipient, String subject, String body, long createdAt,
                       long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                       boolean archived, boolean treasureNew, boolean treasureUnseen,
                       boolean gameTime, long clock,
                       List<Attachment> attachments, String banner, String targetTaskId,
                       MailBannerTiming bannerTiming, long bannerRemainingSeconds) {
        public View(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    boolean archived, boolean treasureNew, boolean treasureUnseen,
                    boolean gameTime, long clock, List<Attachment> attachments, String banner, String targetTaskId) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, icon, treasured,
                    archived, treasureNew, treasureUnseen, gameTime, clock, attachments, banner, targetTaskId,
                    MailBannerTiming.NONE, -1);
        }
        public View(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    boolean archived, boolean treasureNew, boolean treasureUnseen,
                    boolean gameTime, long clock, List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, icon, treasured,
                    archived, treasureNew, treasureUnseen, gameTime, clock, attachments, "", "");
        }
        public View {
            icon = icon == null ? "" : icon;
            banner = banner == null ? "" : banner;
            targetTaskId = targetTaskId == null ? "" : targetTaskId;
            bannerTiming = bannerTiming == null ? MailBannerTiming.NONE : bannerTiming;
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }

        public View(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    boolean archived, boolean treasureNew, boolean treasureUnseen, List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, icon, treasured,
                    archived, treasureNew, treasureUnseen, false, System.currentTimeMillis(), attachments);
        }

        public View(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, String icon, boolean treasured,
                    List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred,
                    icon, treasured, false, false, false, attachments);
        }

        public View(String id, String sender, String recipient, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, List<Attachment> attachments) {
            this(id, sender, recipient, subject, body, createdAt, expiresAt, read, starred, "", false, attachments);
        }

        public View(String id, String sender, String subject, String body, long createdAt,
                    long expiresAt, boolean read, boolean starred, List<Attachment> attachments) {
            this(id, sender, "", subject, body, createdAt, expiresAt, read, starred, attachments);
        }

        public boolean claimable() {
            return attachments.stream().anyMatch(attachment -> !attachment.claimed() && attachment.count() > 0);
        }
    }

    public static void ensureWelcome(ServerPlayer player) {
        requireServer(player);
        MailboxData data = MailboxData.get(player.getServer());
        if (data.initialized(player.getUUID())) {
            return;
        }
        data.markInitialized(player.getUUID());
        send(player,
                "xero_welcome",
                "Xero",
                "欢迎使用 Xero 邮箱",
                "这里可以接收任务奖励和系统通知。请在邮件详情中查看附件，并使用底部按钮领取。",
                0L,
                List.of(new Attachment("item", "minecraft:paper", 1, false)));
    }

    /** Public server-side API for quest packs and other Xero systems. */
    public static void send(ServerPlayer player, String id, String sender, String subject, String body,
                            long expiresAt, List<Attachment> attachments) {
        send(player, id, sender, subject, body, expiresAt, "", false, attachments);
    }

    public static void send(ServerPlayer player, String id, String sender, String subject, String body,
                            long expiresAt, String icon, boolean treasured, List<Attachment> attachments) {
        requireServer(player);
        MailboxData.get(player.getServer()).add(player.getUUID(),
                new Mail(id, sender, player.getGameProfile().getName(), subject, body,
                        System.currentTimeMillis(), expiresAt, false, false, icon, treasured, attachments));
        PacketDistributor.sendToPlayer(player, new QuestNetwork.MailboxNoticePayload(id, sender, subject));
    }

    public static List<View> snapshot(ServerPlayer player) {
        requireServer(player);
        ensureWelcome(player);
        MailboxData.get(player.getServer()).purgeExpired(player.getUUID(), System.currentTimeMillis(),
                player.server.overworld().getGameTime());
        return MailboxData.get(player.getServer()).messages(player.getUUID()).stream()
                .sorted(Comparator.comparing(Mail::starred).reversed()
                        .thenComparing(Comparator.comparingLong(Mail::createdAt).reversed()))
                .map(mail -> view(mail, player)).toList();
    }

    public static String markRead(ServerPlayer player, String id) {
        requireServer(player);
        boolean changed = MailboxData.get(player.getServer()).update(player.getUUID(), id, Mail::markRead);
        return changed ? "read" : "missing";
    }

    public static String openTreasure(ServerPlayer player, String id) {
        boolean changed = liveData(player).update(player.getUUID(), id, Mail::openTreasure);
        return changed ? "read" : "missing";
    }

    public static String acknowledgeTreasureBox(ServerPlayer player) {
        MailboxData data = liveData(player);
        for (Mail mail : data.messages(player.getUUID())) {
            if (mail.treasureUnseen()) data.update(player.getUUID(), mail.id(), Mail::acknowledgeTreasure);
        }
        return "treasure_seen";
    }

    public static String deleteRead(ServerPlayer player) {
        requireServer(player);
        int deleted = MailboxData.get(player.getServer()).deleteRead(player.getUUID());
        return deleted > 0 ? "deleted" : "nothing_deleted";
    }

    public static String toggleStarred(ServerPlayer player, String id) {
        requireServer(player);
        boolean changed = MailboxData.get(player.getServer()).update(player.getUUID(), id, Mail::toggleStarred);
        return changed ? "starred" : "missing";
    }

    public static String delete(ServerPlayer player, String id) {
        return delete(player, id, false);
    }

    public static String delete(ServerPlayer player, String id, boolean discardPending) {
        requireServer(player);
        MailboxData data = liveData(player);
        Mail message = data.messages(player.getUUID()).stream()
                .filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
        if (message == null) return "missing";
        if (message.claimable() && !discardPending) return "attachment_pending";
        data.update(player.getUUID(), id, ignored -> null);
        return "deleted";
    }

    public static String claim(ServerPlayer player, String id) {
        requireServer(player);
        MailboxData data = liveData(player);
        Mail message = data.messages(player.getUUID()).stream()
                .filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
        if (message == null) {
            return "missing";
        }
        if (!message.claimable()) {
            return "no_claimable";
        }
        return deliver(player, List.of(message));
    }

    /** Capture on the server before delivery; only return these items after a successful claim. */
    public static List<QuestDefinition.Reward> receiptItems(ServerPlayer player, String id, boolean all) {
        return liveData(player).messages(player.getUUID()).stream()
                .filter(mail -> all || mail.id().equals(id))
                .flatMap(mail -> mail.attachments().stream())
                .filter(Attachment::showsReceipt)
                .map(a -> new QuestDefinition.Reward("item", a.item(), a.count())).toList();
    }

    public static String claimAll(ServerPlayer player) {
        requireServer(player);
        MailboxData data = liveData(player);
        List<Mail> messages = data.messages(player.getUUID()).stream().filter(Mail::claimable).toList();
        if (messages.isEmpty()) {
            return "no_claimable";
        }
        return deliver(player, messages);
    }

    public record ClaimResult(String result, List<QuestDefinition.Reward> items) {
        public ClaimResult(String result) { this(result, List.of()); }
    }

    /** Choices are resolved against the stored mail before any inventory or receipt mutation. */
    public static ClaimResult claimChoices(ServerPlayer player, List<QuestNetwork.MailboxClaimEntry> entries,
                                           boolean allowDrop) {
        MailboxData data = liveData(player);
        if (entries.isEmpty() || entries.size() > MAX_MESSAGES
                || entries.stream().map(QuestNetwork.MailboxClaimEntry::mailId).distinct().count() != entries.size())
            return new ClaimResult("invalid_choice");
        List<Mail> resolved = new ArrayList<>();
        for (var entry : entries) {
            Mail mail = data.messages(player.getUUID()).stream()
                    .filter(value -> value.id().equals(entry.mailId())).findFirst().orElse(null);
            if (mail == null) return new ClaimResult("missing");
            if (!fingerprint(view(mail, player)).equals(entry.expectedHash())) return new ClaimResult("conflict");
            if (!mail.claimable()) return new ClaimResult("no_claimable");
            var attachments = new ArrayList<>(mail.attachments());
            Set<Integer> choiceIndices = new HashSet<>();
            for (int index = 0; index < attachments.size(); index++) {
                var attachment = attachments.get(index);
                if (attachment.claimed() || attachment.count() <= 0 || !attachment.type().equals("loot_choice")) continue;
                choiceIndices.add(index);
                var candidates = QuestRewards.choices(new QuestDefinition.Reward("loot_choice", attachment.item(), 1));
                Integer selected = entry.selections().get(index);
                if (selected == null) return new ClaimResult("choice_required");
                if (selected < 0 || selected >= candidates.size()) return new ClaimResult("invalid_choice");
                var reward = candidates.get(selected);
                attachments.set(index, new Attachment(reward.type(), reward.item(), reward.count(),
                        false, attachment.hidden(), attachment.hidePopup()));
            }
            if (!choiceIndices.equals(entry.selections().keySet())) return new ClaimResult("invalid_choice");
            resolved.add(mail.withAttachments(attachments));
        }
        var receipt = resolved.stream().flatMap(mail -> mail.attachments().stream())
                .filter(Attachment::showsReceipt)
                .map(a -> new QuestDefinition.Reward("item", a.item(), a.count())).toList();
        String result = deliver(player, resolved, allowDrop);
        return new ClaimResult(result, result.equals("claimed") ? receipt : List.of());
    }

    private record MailContent(String id, String sender, String recipient, String subject, String body,
                               long createdAt, long expiresAt, String icon, boolean treasured,
                               boolean gameTime, List<Attachment> attachments, String banner, String targetTaskId,
                               MailBannerTiming bannerTiming) {}

    public static String fingerprint(View mail) {
        return contentHash(new MailContent(mail.id(), mail.sender(), mail.recipient(), mail.subject(), mail.body(),
                mail.createdAt(), mail.expiresAt(), mail.icon(), mail.treasured(), mail.gameTime(), mail.attachments(),
                mail.banner(), mail.targetTaskId(), mail.bannerTiming()));
    }

    private static String contentHash(MailContent content) {
        return xtdpotato.xero_quest_journal.core.io.QuestRepository.fingerprint(new com.google.gson.Gson().toJson(content));
    }

    public static boolean canEdit(ServerPlayer player) {
        return player != null && player.hasPermissions(2) && player.getPersistentData().getBoolean(EDIT_MODE_KEY);
    }

    public static String edit(ServerPlayer player, QuestNetwork.MailboxSendPayload draft,
                              String expectedHash, boolean preserveExpiry) {
        MailboxData data = liveData(player);
        if (!canEdit(player)) return "permission";
        Mail mail = data.messages(player.getUUID()).stream()
                .filter(value -> value.id().equals(draft.id())).findFirst().orElse(null);
        if (mail == null) return "missing";
        if (!fingerprint(view(mail, player)).equals(expectedHash)) return "conflict";
        try {
            MailNodeSettings.validate("mail_send", Map.of("mailId", draft.id(), "sender", draft.sender(),
                    "subject", draft.subject(), "body", draft.body(), "icon", draft.icon(),
                    "expiresIn", Long.toString(draft.expiresIn())));
            if (draft.subject().isBlank() || draft.body().isBlank()) return "invalid_mail";
            var oldRewards = mail.attachments().stream().map(a ->
                    new Attachment(a.type(), a.item(), a.count(), false, a.hidden(), a.hidePopup())).toList();
            List<Attachment> attachments = mail.attachments();
            if (!oldRewards.equals(draft.attachments())) {
                if (!data.receipt(player.getUUID(), mail.id()).isEmpty()
                        || mail.attachments().stream().anyMatch(Attachment::claimed)) return "reward_edit_locked";
                attachments = freezeAttachments(player, draft.attachments(), oldRewards);
            }
            long expiresAt = preserveExpiry ? mail.expiresAt() : deadline(player, draft.expiresIn(), draft.gameTime());
            var changed = new Mail(mail.id(), draft.sender(), mail.recipient(), draft.subject(), draft.body(),
                    mail.createdAt(), draft.treasured() ? 0 : expiresAt, mail.read(), mail.starred(), draft.icon(),
                    draft.treasured(), mail.archived(), mail.treasureNew(), mail.treasureUnseen(),
                    preserveExpiry ? mail.gameTime() : draft.gameTime(), attachments, draft.banner(), draft.targetTaskId(),
                    draft.bannerTiming(), draft.bannerTiming().equals(mail.bannerTiming())
                    ? mail.bannerDeadline() : bannerDeadline(player, draft.bannerTiming()));
            data.update(player.getUUID(), mail.id(), ignored -> changed);
            player.server.overworld().getDataStorage().save();
            return "edited";
        } catch (Exception invalid) {
            return "invalid_mail";
        }
    }

    public static String deleteMany(ServerPlayer player, Map<String, String> expectedHashes) {
        MailboxData data = liveData(player);
        if (!canEdit(player)) return "permission";
        if (expectedHashes.isEmpty() || expectedHashes.size() > MAX_MESSAGES) return "missing";
        for (var entry : expectedHashes.entrySet()) {
            var mail = data.messages(player.getUUID()).stream().filter(value -> value.id().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (mail == null) return "missing";
            if (!fingerprint(view(mail, player)).equals(entry.getValue())) return "conflict";
        }
        expectedHashes.keySet().forEach(id -> data.update(player.getUUID(), id, ignored -> null));
        player.server.overworld().getDataStorage().save();
        return "deleted";
    }

    private static String deliver(ServerPlayer player, List<Mail> messages) {
        return deliver(player, messages, false);
    }

    private static String deliver(ServerPlayer player, List<Mail> messages, boolean allowDrop) {
        if (DELIVERING.contains(player.getUUID())) return "busy";
        MailboxData data = liveData(player);
        if (messages.stream().anyMatch(mail -> !data.receipt(player.getUUID(), mail.id()).isEmpty()))
            return "reward_failed";
        if (xtdpotato.xero_quest_journal.core.runtime.QuestStructures.active(player)) return "scene_active";
        if (messages.stream().flatMap(mail -> mail.attachments().stream())
                .anyMatch(a -> !a.claimed() && a.count() > 0 && a.type().equals("loot_choice")))
            return "choice_required";
        List<ItemStack> planned = player.getInventory().items.stream().map(ItemStack::copy).toList();
        List<ItemStack> mutable = new ArrayList<>(planned);
        List<ItemStack> drops = new ArrayList<>();
        for (Mail message : messages) {
            for (Attachment attachment : message.attachments()) {
                if (attachment.claimed() || attachment.count() <= 0) {
                    continue;
                }
                if (!attachment.type().equals("item")) {
                    if (!attachment.type().equals("script") && !validAttachment(player, attachment))
                        return "unavailable";
                    continue;
                }
                ItemStack prototype = attachmentStack(player, attachment);
                if (prototype.isEmpty()) {
                    return "unavailable";
                }
                int remaining = attachment.count();
                for (int slot = 0; slot < mutable.size() && remaining > 0; slot++) {
                    ItemStack stack = mutable.get(slot);
                    if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, prototype)) {
                        int amount = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                        stack.grow(amount);
                        remaining -= amount;
                    }
                }
                for (int slot = 0; slot < mutable.size() && remaining > 0; slot++) {
                    if (mutable.get(slot).isEmpty()) {
                        int amount = Math.min(remaining, prototype.getMaxStackSize());
                        mutable.set(slot, prototype.copyWithCount(amount));
                        remaining -= amount;
                    }
                }
                if (remaining > 0 && !allowDrop) return "inventory_full";
                while (remaining > 0) {
                    if (drops.size() >= 256) return "unavailable";
                    int amount = Math.min(remaining, prototype.getMaxStackSize());
                    drops.add(prototype.copyWithCount(amount));
                    remaining -= amount;
                }
            }
        }
        DELIVERING.add(player.getUUID());
        try {
            for (Mail mail : messages) data.receipt(player.getUUID(), mail.id(), "running");
            player.server.overworld().getDataStorage().save();
            for (int slot = 0; slot < mutable.size(); slot++) {
                player.getInventory().setItem(slot, mutable.get(slot));
            }
            for (ItemStack drop : drops)
                if (player.drop(drop, false) == null) throw new IllegalStateException("Mail reward drop canceled");
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            for (Mail mail : messages) for (Attachment attachment : mail.attachments()) {
                if (!attachment.claimed() && attachment.count() > 0 && !attachment.type().equals("item"))
                    QuestRewards.execute(player, new QuestDefinition.Reward(
                            attachment.type(), attachment.item(), attachment.count()));
            }
            player.server.getPlayerList().saveAll();
            for (Mail mail : messages) {
                data.update(player.getUUID(), mail.id(), Mail::claimRewards);
                data.receipt(player.getUUID(), mail.id(), "done");
            }
            return "claimed";
        } catch (Exception failure) {
            for (Mail mail : messages) data.receipt(player.getUUID(), mail.id(), "failed");
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.error("Mail reward delivery needs review", failure);
            return "reward_failed";
        } finally {
            player.server.overworld().getDataStorage().save();
            DELIVERING.remove(player.getUUID());
        }
    }

    private static MailboxData liveData(ServerPlayer player) {
        requireServer(player);
        MailboxData data = MailboxData.get(player.getServer());
        data.purgeExpired(player.getUUID(), System.currentTimeMillis(), player.server.overworld().getGameTime());
        return data;
    }

    private static ItemStack attachmentStack(ServerPlayer player, Attachment attachment) {
        if (QuestItemStackValue.encoded(attachment.item()))
            return QuestItemStackValue.decode(attachment.item(), player.registryAccess()).orElse(ItemStack.EMPTY);
        ResourceLocation id = ResourceLocation.tryParse(attachment.item());
        return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    /** Repeated graph evaluation must never replace mail or reset claimed attachments. */
    public static boolean sendIfAbsent(ServerPlayer player, String id, String sender, String subject, String body,
                                       long expiresIn, List<Attachment> attachments) {
        return sendIfAbsent(player, id, sender, subject, body, expiresIn, "", false, attachments);
    }

    public static boolean sendIfAbsent(ServerPlayer player, String id, String sender, String subject, String body,
                                       long expiresIn, String icon, boolean treasured, List<Attachment> attachments) {
        return sendIfAbsent(player, id, sender, subject, body, expiresIn, false, icon, treasured, attachments);
    }

    public static boolean sendIfAbsent(ServerPlayer player, String id, String sender, String subject, String body,
                                       long expiresIn, boolean gameTime, String icon, boolean treasured,
                                       List<Attachment> attachments) {
        return sendIfAbsent(player, id, sender, subject, body, expiresIn, gameTime, icon, treasured,
                attachments, "", "");
    }

    public static boolean sendIfAbsent(ServerPlayer player, String id, String sender, String subject, String body,
                                       long expiresIn, boolean gameTime, String icon, boolean treasured,
                                       List<Attachment> attachments, String banner, String targetTaskId) {
        return sendIfAbsent(player, id, sender, subject, body, expiresIn, gameTime, icon, treasured,
                attachments, banner, targetTaskId, MailBannerTiming.NONE);
    }

    public static boolean sendIfAbsent(ServerPlayer player, String id, String sender, String subject, String body,
                                       long expiresIn, boolean gameTime, String icon, boolean treasured,
                                       List<Attachment> attachments, String banner, String targetTaskId,
                                       MailBannerTiming bannerTiming) {
        return sendIfAbsent(player, id, sender, subject, body, expiresIn, gameTime, icon, treasured,
                attachments, List.of(), banner, targetTaskId, bannerTiming);
    }

    public static boolean resend(ServerPlayer author, ServerPlayer target, QuestNetwork.MailboxResendPayload payload) {
        if (!canEdit(author)) throw new IllegalArgumentException("Mail editing disabled");
        var source = liveData(author).messages(author.getUUID()).stream()
                .filter(mail -> mail.id().equals(payload.sourceId())).findFirst().orElse(null);
        if (source == null || !fingerprint(view(source, author)).equals(payload.expectedHash()))
            throw new IllegalArgumentException("Mail changed");
        var trusted = source.attachments().stream().map(a ->
                new Attachment(a.type(), a.item(), a.count(), false, a.hidden(), a.hidePopup())).toList();
        var draft = payload.mail();
        if (draft.id().equals(source.id())) throw new IllegalArgumentException("Resend requires a new mail ID");
        return sendIfAbsent(target, draft.id(), draft.sender(), draft.subject(), draft.body(), draft.expiresIn(),
                draft.gameTime(), draft.icon(), draft.treasured(), draft.attachments(), trusted,
                draft.banner(), draft.targetTaskId(), draft.bannerTiming());
    }

    private static boolean sendIfAbsent(ServerPlayer player, String id, String sender, String subject, String body,
                                       long expiresIn, boolean gameTime, String icon, boolean treasured,
                                       List<Attachment> attachments, List<Attachment> trusted,
                                       String banner, String targetTaskId, MailBannerTiming bannerTiming) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Mail ID is required");
        MailNodeSettings.validate("mail_send", Map.of("mailId", id, "sender", sender,
                "subject", subject, "body", body, "expiresIn", Long.toString(expiresIn), "icon", icon));
        MailboxData data = liveData(player);
        if (data.messages(player.getUUID()).stream().anyMatch(mail -> mail.id().equals(id))) return true;
        if (data.messages(player.getUUID()).size() >= MAX_MESSAGES
                && data.messages(player.getUUID()).stream().noneMatch(mail ->
                !mail.starred() && !mail.treasured() && !mail.claimable())) return false;
        var frozen = freezeAttachments(player, attachments, trusted);
        long deadline = treasured ? 0 : deadline(player, expiresIn, gameTime);
        data.add(player.getUUID(), new Mail(id, sender, player.getGameProfile().getName(), subject, body,
                System.currentTimeMillis(), deadline, false, false, icon, treasured,
                false, false, false, gameTime, frozen, banner, targetTaskId, bannerTiming,
                bannerDeadline(player, bannerTiming)));
        PacketDistributor.sendToPlayer(player, new QuestNetwork.MailboxNoticePayload(id, sender, subject));
        return true;
    }

    private static long deadline(ServerPlayer player, long expiresIn, boolean gameTime) {
        return expiresIn == 0 ? 0 : gameTime ? player.server.overworld().getGameTime() + expiresIn * 20
                : System.currentTimeMillis() + expiresIn * 1000;
    }

    private static long bannerDeadline(ServerPlayer player, MailBannerTiming timing) {
        if (!timing.mode().equals("countdown")) return 0;
        return (timing.gameTime() ? player.server.overworld().getGameTime() : System.currentTimeMillis())
                + timing.seconds() * (timing.gameTime() ? 20 : 1000);
    }

    private static long bannerSeconds(ServerPlayer player, Mail mail) {
        var timing = mail.bannerTiming();
        if (timing.mode().equals("variable")) {
            if (!xtdpotato.xero_quest_journal.core.runtime.QuestVisualPredicate.allowed(
                    timing.variable(), mail.targetTaskId())) return -1;
            try {
                String value = xtdpotato.xero_quest_journal.core.runtime.QuestControlData.get(player)
                        .variable(player, timing.variable());
                double number = Double.parseDouble(value);
                return Double.isFinite(number) ? (long) Math.clamp(Math.ceil(number), 0, 315360000) : -1;
            } catch (IllegalArgumentException invalid) { return -1; }
        }
        if (!timing.mode().equals("countdown")) return -1;
        long unit = timing.gameTime() ? 20 : 1000;
        long now = timing.gameTime() ? player.server.overworld().getGameTime() : System.currentTimeMillis();
        return (Math.max(0, mail.bannerDeadline() - now) + unit - 1) / unit;
    }

    private static List<Attachment> freezeAttachments(ServerPlayer player, List<Attachment> attachments) {
        return freezeAttachments(player, attachments, List.of());
    }

    private static List<Attachment> freezeAttachments(ServerPlayer player, List<Attachment> attachments,
                                                     List<Attachment> trusted) {
        if (attachments.size() > 128 || attachments.stream().anyMatch(attachment ->
                attachment.count() < 1 || attachment.count() > 1000000 || attachment.claimed()
                        || !(attachment.type().equals("script") && trusted.contains(attachment))
                        && !validAttachment(player, attachment)))
            throw new IllegalArgumentException("Invalid mail attachments");
        // Freeze each source separately so expanded loot retains its presentation settings.
        var frozen = attachments.stream().flatMap(a -> a.type().equals("script") && trusted.contains(a)
                ? java.util.stream.Stream.of(a) : QuestRewards.freeze(player,
                        List.of(new QuestDefinition.Reward(a.type(), a.item(), a.count()))).stream()
                .map(r -> new Attachment(r.type(), r.item(), r.count(), false, a.hidden(), a.hidePopup()))).toList();
        if (frozen.size() > 128 || frozen.stream().anyMatch(reward -> reward.item().length() > 8192))
            throw new IllegalArgumentException("Invalid frozen mail rewards");
        return frozen;
    }

    private static boolean validAttachment(ServerPlayer player, Attachment attachment) {
        if (attachment == null || attachment.type().equals("mail")) return false;
        if (attachment.type().equals("loot_choice")) {
            return !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.choices(
                    new QuestDefinition.Reward("loot_choice", attachment.item(), 1)).isEmpty();
        }
        try {
            return QuestRewards.valid(new QuestDefinition.Reward(
                    attachment.type(), attachment.item(), attachment.count()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static boolean check(ServerPlayer player, String id, String state) {
        if (!MailNodeSettings.STATES.contains(state)) throw new IllegalArgumentException("Invalid mail state");
        Mail mail = liveData(player).messages(player.getUUID()).stream()
                .filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
        if (state.equals("missing")) return mail == null;
        if (mail == null) return false;
        return switch (state) {
            case "exists" -> true;
            case "read" -> mail.read();
            case "unread" -> !mail.read();
            case "claimable" -> mail.claimable();
            case "claimed" -> !mail.attachments().isEmpty() && !mail.claimable();
            case "starred" -> mail.starred();
            default -> false;
        };
    }

    public static boolean setProperty(ServerPlayer player, String id, String property, String value) {
        if (!MailNodeSettings.PROPERTIES.contains(property)) throw new IllegalArgumentException("Invalid mail property");
        MailNodeSettings.validateValue(property, value);
        return liveData(player).update(player.getUUID(), id, mail -> new Mail(mail.id(),
                property.equals("sender") ? value : mail.sender(),
                mail.recipient(),
                property.equals("subject") ? value : mail.subject(),
                property.equals("body") ? value : mail.body(), mail.createdAt(),
                property.equals("expiresIn") ? (value.equals("0") ? 0
                        : (mail.gameTime() ? player.server.overworld().getGameTime() : System.currentTimeMillis())
                        + Long.parseLong(value) * (mail.gameTime() ? 20 : 1000)) : mail.expiresAt(),
                property.equals("read") ? Boolean.parseBoolean(value) : mail.read(),
                property.equals("starred") ? Boolean.parseBoolean(value) : mail.starred(),
                property.equals("icon") ? value : mail.icon(),
                property.equals("treasured") ? Boolean.parseBoolean(value) : mail.treasured(),
                mail.archived(), mail.treasureNew(), mail.treasureUnseen(),
                mail.gameTime(),
                mail.attachments(), mail.banner(), mail.targetTaskId(), mail.bannerTiming(), mail.bannerDeadline()));
    }

    private static View view(Mail message, ServerPlayer player) {
        return new View(message.id(), message.sender(),
                message.recipient().isBlank() ? player.getGameProfile().getName() : message.recipient(),
                message.subject(), message.body(),
                message.createdAt(), message.expiresAt(), message.read(), message.starred(),
                message.icon(), message.treasured(), message.archived(), message.treasureNew(),
                message.treasureUnseen(), message.gameTime(),
                message.gameTime() ? player.server.overworld().getGameTime() : System.currentTimeMillis(),
                message.attachments(), message.banner(), message.targetTaskId(), message.bannerTiming(),
                bannerSeconds(player, message));
    }

    private static void requireServer(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) {
            throw new IllegalStateException("Mailbox actions require a connected server player and server thread");
        }
    }
}
