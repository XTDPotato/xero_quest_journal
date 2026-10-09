package xtdpotato.xero_quest_journal.core.mailbox;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** World-scoped mailbox state. Mail data is authoritative on the server. */
public final class MailboxData extends SavedData {
    public static final String FILE = "xero_mailbox";
    private final Map<UUID, List<MailboxRuntime.Mail>> mailboxes = new HashMap<>();
    private final Set<UUID> initialized = new HashSet<>();
    private final Map<UUID, Map<String, String>> receipts = new HashMap<>();

    public String receipt(UUID player, String mail) {
        return receipts.getOrDefault(player, Map.of()).getOrDefault(mail, "");
    }

    public void receipt(UUID player, String mail, String value) {
        receipts.computeIfAbsent(player, ignored -> new HashMap<>()).put(mail, value);
        setDirty();
    }

    public static MailboxData get(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Mailbox data must be accessed on the server thread");
        }
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(MailboxData::new, MailboxData::load), FILE);
    }

    public boolean initialized(UUID player) {
        return initialized.contains(player);
    }

    public void markInitialized(UUID player) {
        if (initialized.add(player)) {
            setDirty();
        }
    }

    public List<MailboxRuntime.Mail> messages(UUID player) {
        return List.copyOf(mailboxes.getOrDefault(player, List.of()));
    }

    public void add(UUID player, MailboxRuntime.Mail message) {
        message = message.archiveIfReady();
        String messageId = message.id();
        List<MailboxRuntime.Mail> messages = mailboxes.computeIfAbsent(player, ignored -> new ArrayList<>());
        messages.removeIf(existing -> existing.id().equals(messageId));
        messages.add(message);
        messages.sort(Comparator.comparingLong(MailboxRuntime.Mail::createdAt).reversed());
        while (messages.size() > MailboxRuntime.MAX_MESSAGES) {
            int removable = -1;
            for (int i = messages.size() - 1; i >= 0; i--) {
                var mail = messages.get(i);
                if (!mail.treasured() && !mail.starred() && !mail.claimable()) {
                    removable = i;
                    break;
                }
            }
            if (removable < 0) {
                messages.remove(message);
                throw new IllegalStateException("Mailbox full");
            }
            messages.remove(removable);
        }
        receipts.computeIfAbsent(player, ignored -> new HashMap<>()).remove(message.id());
        setDirty();
    }

    public boolean update(UUID player, String id,
                          java.util.function.UnaryOperator<MailboxRuntime.Mail> updater) {
        List<MailboxRuntime.Mail> messages = mailboxes.get(player);
        if (messages == null) {
            return false;
        }
        for (int index = 0; index < messages.size(); index++) {
            MailboxRuntime.Mail current = messages.get(index);
            if (!current.id().equals(id)) {
                continue;
            }
            MailboxRuntime.Mail next = updater.apply(current);
            if (next == null) {
                messages.remove(index);
            } else {
                messages.set(index, next.archiveIfReady());
            }
            setDirty();
            return true;
        }
        return false;
    }

    public int deleteRead(UUID player) {
        List<MailboxRuntime.Mail> messages = mailboxes.get(player);
        if (messages == null) {
            return 0;
        }
        int before = messages.size();
        messages.removeIf(message -> !message.starred() && !message.treasured() && message.read() && message.attachments().stream()
                .allMatch(MailboxRuntime.Attachment::claimed));
        int deleted = before - messages.size();
        if (deleted > 0) {
            setDirty();
        }
        return deleted;
    }

    public int purgeExpired(UUID player, long now) {
        return purgeExpired(player, now, 0);
    }

    public int purgeExpired(UUID player, long now, long gameTicks) {
        List<MailboxRuntime.Mail> messages = mailboxes.get(player);
        if (messages == null) return 0;
        int before = messages.size();
        messages.removeIf(message -> !message.treasured() && message.expiresAt() > 0
                && message.expiresAt() <= (message.gameTime() ? gameTicks : now));
        int removed = before - messages.size();
        if (removed > 0) setDirty();
        return removed;
    }

    public static MailboxData load(CompoundTag root, HolderLookup.Provider registries) {
        MailboxData result = new MailboxData();
        ListTag people = root.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < people.size(); i++) {
            CompoundTag person = people.getCompound(i);
            UUID player = person.getUUID("id");
            if (person.getBoolean("initialized")) {
                result.initialized.add(player);
            }
            List<MailboxRuntime.Mail> messages = new ArrayList<>();
            ListTag mail = person.getList("mail", Tag.TAG_COMPOUND);
            for (int j = 0; j < mail.size(); j++) {
                CompoundTag entry = mail.getCompound(j);
                if (!entry.getString("receipt").isEmpty())
                    result.receipt(player, entry.getString("id"), entry.getString("receipt"));
                List<MailboxRuntime.Attachment> attachments = new ArrayList<>();
                ListTag items = entry.getList("attachments", Tag.TAG_COMPOUND);
                for (int k = 0; k < items.size(); k++) {
                    CompoundTag item = items.getCompound(k);
                    attachments.add(new MailboxRuntime.Attachment(item.getString("type"),
                            item.getString("item"), item.getInt("count"), item.getBoolean("claimed"),
                            item.getBoolean("hidden"), item.getBoolean("hidePopup")));
                }
                MailboxRuntime.Mail loaded = new MailboxRuntime.Mail(
                        entry.getString("id"),
                        entry.getString("sender"),
                        entry.getString("recipient"),
                        entry.getString("subject"),
                        entry.getString("body"),
                        entry.getLong("createdAt"),
                        entry.getLong("expiresAt"),
                        entry.getBoolean("read"),
                        entry.getBoolean("starred"),
                        entry.getString("icon"),
                        entry.getBoolean("treasured"),
                        entry.getBoolean("archived"),
                        entry.getBoolean("treasureNew"),
                        entry.getBoolean("treasureUnseen"),
                        entry.getBoolean("gameTime"),
                        attachments, entry.getString("banner"), entry.getString("targetTaskId"),
                        readTiming(entry.getCompound("bannerTiming")), entry.getLong("bannerDeadline"));
                if (!entry.contains("archived")) {
                    loaded = loaded.archiveIfReady();
                    if (loaded.read()) loaded = loaded.openTreasure().acknowledgeTreasure();
                }
                messages.add(loaded);
            }
            messages.sort(Comparator.comparingLong(MailboxRuntime.Mail::createdAt).reversed());
            if (!messages.isEmpty()) {
                result.mailboxes.put(player, messages);
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag people = new ListTag();
        Set<UUID> players = new HashSet<>(mailboxes.keySet());
        players.addAll(initialized);
        for (UUID player : players) {
            CompoundTag person = new CompoundTag();
            person.putUUID("id", player);
            person.putBoolean("initialized", initialized.contains(player));
            ListTag mail = new ListTag();
            for (MailboxRuntime.Mail message : mailboxes.getOrDefault(player, List.of())) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", message.id());
                entry.putString("receipt", receipt(player, message.id()));
                entry.putString("sender", message.sender());
                entry.putString("recipient", message.recipient());
                entry.putString("subject", message.subject());
                entry.putString("body", message.body());
                entry.putLong("createdAt", message.createdAt());
                entry.putLong("expiresAt", message.expiresAt());
                entry.putBoolean("read", message.read());
                entry.putBoolean("starred", message.starred());
                entry.putString("icon", message.icon());
                entry.putString("banner", message.banner());
                entry.putString("targetTaskId", message.targetTaskId());
                var timing = new CompoundTag();
                timing.putString("mode", message.bannerTiming().mode());
                timing.putLong("seconds", message.bannerTiming().seconds());
                timing.putBoolean("gameTime", message.bannerTiming().gameTime());
                timing.putString("variable", message.bannerTiming().variable());
                entry.put("bannerTiming", timing);
                entry.putLong("bannerDeadline", message.bannerDeadline());
                entry.putBoolean("treasured", message.treasured());
                entry.putBoolean("archived", message.archived());
                entry.putBoolean("treasureNew", message.treasureNew());
                entry.putBoolean("treasureUnseen", message.treasureUnseen());
                entry.putBoolean("gameTime", message.gameTime());
                ListTag attachments = new ListTag();
                for (MailboxRuntime.Attachment attachment : message.attachments()) {
                    CompoundTag item = new CompoundTag();
                    item.putString("type", attachment.type());
                    item.putString("item", attachment.item());
                    item.putInt("count", attachment.count());
                    item.putBoolean("claimed", attachment.claimed());
                    item.putBoolean("hidden", attachment.hidden());
                    item.putBoolean("hidePopup", attachment.hidePopup());
                    attachments.add(item);
                }
                entry.put("attachments", attachments);
                mail.add(entry);
            }
            person.put("mail", mail);
            people.add(person);
        }
        root.putInt("schema", 5);
        root.put("players", people);
        return root;
    }

    private static MailBannerTiming readTiming(CompoundTag tag) {
        if (tag.isEmpty()) return MailBannerTiming.NONE;
        try {
            return new MailBannerTiming(tag.getString("mode"), tag.getLong("seconds"),
                    tag.getBoolean("gameTime"), tag.getString("variable"));
        } catch (IllegalArgumentException invalid) { return MailBannerTiming.NONE; }
    }
}
