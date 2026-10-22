package xtdpotato.xero_quest_journal.core.network;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.io.MailTemplateStore;
import xtdpotato.xero_quest_journal.core.mailbox.MailDraft;
import xtdpotato.xero_quest_journal.core.mailbox.MailBannerTiming;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;
import xtdpotato.xero_quest_journal.core.runtime.QuestFocus;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.HotEventStore;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestNetwork {
    private static final int MAX_QUESTS = 65536;
    private static final int MAX_REWARDS = 128;
    private static final int MAX_CATALOG_CHUNKS = 128;
    private static final int MAX_PROGRESS_CHUNKS = 256;
    private static final int PROGRESS_CHUNK_BYTES = 240000;
    private static final Map<Object, Long> lastPoll = new java.util.WeakHashMap<>();
    private static final Map<Object, Long> lastAction = new java.util.WeakHashMap<>();
    private static final Map<Object, Long> lastRead = new java.util.WeakHashMap<>();

    private QuestNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // 13: task-linked acquisition/submission notices; generation and shared template library actions.
        var registrar = event.registrar("13");
        AnecdoteNetwork.register(registrar);
        QuestControlNetwork.register(registrar);
        QuestUiThemeNetwork.register(registrar);
        QuestDailyNetwork.register(registrar);
        QuestJourneyNetwork.register(registrar);
        QuestJournalOrderNetwork.register(registrar);
        TemplateLibraryNetwork.register(registrar);
        QuestGenerationNetwork.register(registrar);
        QuestStructureNetwork.register(registrar);
        registrar.playToServer(ProgressRequestPayload.TYPE, ProgressRequestPayload.STREAM_CODEC,
                QuestNetwork::handleProgressRequest);
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, QuestNetwork::handleAction);
        registrar.playToServer(ResetBatchPayload.TYPE, ResetBatchPayload.STREAM_CODEC, QuestNetwork::handleResetBatch);
        registrar.playToServer(LockBatchPayload.TYPE, LockBatchPayload.STREAM_CODEC, QuestNetwork::handleLockBatch);
        registrar.playToServer(ClaimChoicePayload.TYPE, ClaimChoicePayload.STREAM_CODEC,
                QuestNetwork::handleClaimChoice);
        registrar.playToServer(ConfigPayload.TYPE, ConfigPayload.STREAM_CODEC, QuestNetwork::handleConfig);
        registrar.playToServer(PageSizePayload.TYPE, PageSizePayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                context.reply(new ActionResultPayload(payload.requestId(), "permission"));
                return;
            }
            int previous = QuestConfig.defaults().tasksPerPage;
            QuestConfig.defaults().tasksPerPage = payload.value();
            boolean saved = QuestConfig.save();
            if (!saved) QuestConfig.defaults().tasksPerPage = previous;
            replySnapshot(player, context);
            context.reply(new ActionResultPayload(payload.requestId(), saved ? "config_saved" : "config_save_failed"));
        });
        registrar.playToServer(HotEventCanvasPayload.TYPE, HotEventCanvasPayload.STREAM_CODEC,
                QuestNetwork::handleHotEventCanvas);
        registrar.playToClient(ProgressSnapshotPayload.TYPE, ProgressSnapshotPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(ActionResultPayload.TYPE, ActionResultPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(AutomaticClaimPayload.TYPE, AutomaticClaimPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(CatalogPayload.TYPE, CatalogPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToServer(MailboxRequestPayload.TYPE, MailboxRequestPayload.STREAM_CODEC,
                QuestNetwork::handleMailboxRequest);
        registrar.playToServer(MailboxActionPayload.TYPE, MailboxActionPayload.STREAM_CODEC,
                QuestNetwork::handleMailboxAction);
        registrar.playToServer(MailboxSendPayload.TYPE, MailboxSendPayload.STREAM_CODEC,
                QuestNetwork::handleMailboxSend);
        registrar.playToServer(MailboxResendPayload.TYPE, MailboxResendPayload.STREAM_CODEC,
                (payload, context) -> handleMailboxSend(payload.mail(), context, payload));
        registrar.playToServer(MailboxClaimPayload.TYPE, MailboxClaimPayload.STREAM_CODEC,
                QuestNetwork::handleMailboxClaim);
        registrar.playToServer(MailboxEditPayload.TYPE, MailboxEditPayload.STREAM_CODEC,
                QuestNetwork::handleMailboxEdit);
        registrar.playToServer(MailboxDeletePayload.TYPE, MailboxDeletePayload.STREAM_CODEC,
                QuestNetwork::handleMailboxDelete);
        registrar.playToServer(MailboxTemplateSavePayload.TYPE, MailboxTemplateSavePayload.STREAM_CODEC,
                QuestNetwork::handleMailboxTemplateSave);
        registrar.playToServer(MailboxTemplateCatalogRequestPayload.TYPE,
                MailboxTemplateCatalogRequestPayload.STREAM_CODEC,
                QuestNetwork::handleMailboxTemplateCatalogRequest);
        registrar.playToClient(MailboxSnapshotPayload.TYPE, MailboxSnapshotPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(MailboxTemplateCatalogPayload.TYPE,
                MailboxTemplateCatalogPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(MailboxActionResultPayload.TYPE, MailboxActionResultPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(MailboxNoticePayload.TYPE, MailboxNoticePayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(ToastPayload.TYPE, ToastPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(ItemPickupNoticePayload.TYPE, ItemPickupNoticePayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(AnecdotePayload.TYPE, AnecdotePayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(OpenScreenPayload.TYPE, OpenScreenPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(LocatedQuestPayload.TYPE, LocatedQuestPayload.STREAM_CODEC,
                (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToClient(EditModePayload.TYPE, EditModePayload.STREAM_CODEC,
                (payload, context) -> xtdpotato.xero_quest_journal.client.ClientEditMode.set(payload.enabled()));
    }

    private static void handleProgressRequest(ProgressRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            long now = player.getServer().getTickCount();
            Long previous = lastPoll.put(player.connection, now);
            if (previous != null && now - previous < 5) return;
            if (payload.catalogRevision() != QuestRepository.revision()) {
                try {
                    replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
                } catch (IllegalArgumentException tooLarge) {
                    context.reply(new ActionResultPayload(0, "catalog_unavailable"));
                    return;
                }
            }
            replySnapshot(player, context);
            QuestGenerationNetwork.offerFirst(player);
        }
    }

    private static void handleAction(ActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.getServer().getTickCount();
        Map<Object, Long> throttle = payload.action() == ActionPayload.MARK_READ
                || payload.action() == ActionPayload.MARK_ALL_READ ? lastRead : lastAction;
        Long previous = throttle.put(player.connection, now);
        if (previous != null && now - previous < 2) {
            context.reply(new ActionResultPayload(payload.requestId(), "busy"));
            replySnapshot(player, context);
            return;
        }
        if (payload.catalogRevision() != QuestRepository.revision()) {
            context.reply(new ActionResultPayload(payload.requestId(), "definition_changed"));
            try {
                replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
            } catch (IllegalArgumentException tooLarge) {
                context.reply(new ActionResultPayload(0, "catalog_unavailable"));
            }
            replySnapshot(player, context);
            return;
        }
        String result = switch (payload.action()) {
            case ActionPayload.ACTIVATE -> QuestRuntime.activate(player, payload.questId());
            case ActionPayload.CLAIM -> QuestRuntime.claim(player, payload.questId());
            case ActionPayload.TRACK -> QuestRuntime.toggleTracking(player, payload.questId());
            case ActionPayload.FOCUS -> xtdpotato.xero_quest_journal.core.runtime.QuestFocus.toggle(player, payload.questId());
            case ActionPayload.RESET -> QuestRuntime.reset(player, payload.questId());
            case ActionPayload.CLAIM_DROP -> QuestRuntime.claim(player, payload.questId(), true);
            case ActionPayload.CLAIM_ALL -> QuestRuntime.claimAll(player);
            case ActionPayload.DAILY_BONUS -> xtdpotato.xero_quest_journal.core.runtime.QuestDaily.claimBonus(player);
            case ActionPayload.MARK_READ -> QuestRuntime.markTaskRead(player, payload.questId());
            case ActionPayload.MARK_ALL_READ -> QuestRuntime.markAllTasksRead(player);
            default -> "invalid_action";
        };
        replySnapshot(player, context);
        // Receipt consumers must see the post-claim state before the request completes.
        context.reply(new ActionResultPayload(payload.requestId(), result));
    }

    private static void handleResetBatch(ResetBatchPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        long now = player.getServer().getTickCount();
        Long previous = lastAction.put(player.connection, now);
        if (previous != null && now - previous < 2) {
            context.reply(new ActionResultPayload(payload.requestId(), "busy"));
            return;
        }
        if (payload.catalogRevision() != QuestRepository.revision()) {
            context.reply(new ActionResultPayload(payload.requestId(), "definition_changed"));
            try {
                replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
            } catch (IllegalArgumentException tooLarge) {
                context.reply(new ActionResultPayload(0, "catalog_unavailable"));
            }
            replySnapshot(player, context);
            return;
        }
        String result = QuestRuntime.resetMany(player, payload.questIds());
        replySnapshot(player, context);
        context.reply(new ActionResultPayload(payload.requestId(), result));
    }

    private static void handleLockBatch(LockBatchPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) {
            context.reply(new ActionResultPayload(payload.requestId(), "permission"));
            return;
        }
        long now = player.getServer().getTickCount();
        Long previous = lastAction.put(player.connection, now);
        if (previous != null && now - previous < 2) {
            context.reply(new ActionResultPayload(payload.requestId(), "busy"));
            return;
        }
        if (payload.catalogRevision() != QuestRepository.revision()) {
            context.reply(new ActionResultPayload(payload.requestId(), "definition_changed"));
            replySnapshot(player, context);
            return;
        }
        // Validate the complete batch before changing any player state.
        if (payload.questIds().stream().anyMatch(id -> QuestRepository.find(id) == null)) {
            context.reply(new ActionResultPayload(payload.requestId(), "missing"));
            return;
        }
        String action = payload.locked() ? "lock" : "unlock";
        for (String id : payload.questIds()) QuestRuntime.trigger(player, id, action, true);
        replySnapshot(player, context);
        context.reply(new ActionResultPayload(payload.requestId(), payload.locked() ? "locked" : "unlocked"));
    }

    private static void handleClaimChoice(ClaimChoicePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.getServer().getTickCount();
        Long previous = lastAction.put(player.connection, now);
        if (previous != null && now - previous < 2) {
            context.reply(new ActionResultPayload(payload.requestId(), "busy"));
            replySnapshot(player, context);
            return;
        }
        if (payload.catalogRevision() != QuestRepository.revision()) {
            context.reply(new ActionResultPayload(payload.requestId(), "definition_changed"));
            try {
                replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
            } catch (IllegalArgumentException tooLarge) {
                context.reply(new ActionResultPayload(0, "catalog_unavailable"));
            }
            replySnapshot(player, context);
            return;
        }
        QuestDefinition quest = QuestRepository.find(payload.questId());
        String result = quest == null
                ? "missing"
                : QuestRewards.claimChoices(player, quest, payload.selections(), payload.allowDrop());
        replySnapshot(player, context);
        context.reply(new ActionResultPayload(payload.requestId(), result));
    }

    private static void handleConfig(ConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            context.reply(new ActionResultPayload(payload.requestId(), "permission"));
            return;
        }
        if (!"task_pagination".equals(payload.key())) {
            context.reply(new ActionResultPayload(payload.requestId(), "invalid_config"));
            return;
        }
        QuestConfig.defaults().taskPagination = payload.value();
        String result = QuestConfig.save() ? "config_saved" : "config_save_failed";
        context.reply(new ActionResultPayload(payload.requestId(), result));
        replySnapshot(player, context);
    }

    private static void handleHotEventCanvas(HotEventCanvasPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            context.reply(new ActionResultPayload(payload.requestId(), "permission"));
            return;
        }
        String result = "invalid_canvas";
        try {
            if (payload.json().length() <= 262144) {
                HotEventCanvas canvas = new com.google.gson.Gson().fromJson(payload.json(), HotEventCanvas.class);
                if (HotEventStore.update(player, canvas)) {
                    result = "saved";
                    replyCatalog(context, QuestRepository.revision(), QuestCatalog.serverJson());
                }
            }
        } catch (Exception invalid) {
            XeroQuestJournal.LOGGER.warn("Rejected hot-event canvas update from {}", player.getGameProfile().getName(),
                    invalid);
        }
        context.reply(new ActionResultPayload(payload.requestId(), result));
    }

    private static void replySnapshot(ServerPlayer player, IPayloadContext context) {
        try {
            sendSnapshotChunks(player, context);
            QuestControlNetwork.send(player);
            QuestControlNetwork.sendVariables(player);
            QuestUiThemeNetwork.send(player);
            QuestDailyNetwork.send(player);
            xtdpotato.xero_quest_journal.core.runtime.QuestStructureGuidance.sync(player);
        } catch (IllegalArgumentException tooLarge) {
            context.reply(new ActionResultPayload(0, "progress_unavailable"));
        }
    }

    private static void handleMailboxRequest(MailboxRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.reply(mailboxSnapshot(player));
        }
    }

    private static void handleMailboxAction(MailboxActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var receipt = payload.action() == MailboxActionPayload.CLAIM || payload.action() == MailboxActionPayload.CLAIM_ALL
                ? MailboxRuntime.receiptItems(player, payload.mailId(), payload.action() == MailboxActionPayload.CLAIM_ALL)
                : List.<QuestDefinition.Reward>of();
        String result = switch (payload.action()) {
            case MailboxActionPayload.READ -> MailboxRuntime.markRead(player, payload.mailId());
            case MailboxActionPayload.CLAIM -> MailboxRuntime.claim(player, payload.mailId());
            case MailboxActionPayload.CLAIM_ALL -> MailboxRuntime.claimAll(player);
            case MailboxActionPayload.DELETE_READ -> MailboxRuntime.deleteRead(player);
            case MailboxActionPayload.STAR -> MailboxRuntime.toggleStarred(player, payload.mailId());
            case MailboxActionPayload.DELETE -> MailboxRuntime.delete(player, payload.mailId());
            case MailboxActionPayload.OPEN_TREASURE -> MailboxRuntime.openTreasure(player, payload.mailId());
            case MailboxActionPayload.SEEN_TREASURE_BOX -> MailboxRuntime.acknowledgeTreasureBox(player);
            default -> "invalid_action";
        };
        context.reply(new MailboxActionResultPayload(payload.requestId(), result,
                result.equals("claimed") ? receipt : List.of()));
        context.reply(mailboxSnapshot(player));
    }

    private static void handleMailboxSend(MailboxSendPayload payload, IPayloadContext context) {
        handleMailboxSend(payload, context, null);
    }

    private static void handleMailboxSend(MailboxSendPayload payload, IPayloadContext context,
                                          MailboxResendPayload resend) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String result = "invalid_mail";
        if (!MailboxRuntime.canEdit(player)) {
            result = "permission";
        } else if (!payload.id().isBlank()
                && !payload.subject().isBlank() && !payload.body().isBlank()) {
            try {
                String recipient = payload.recipient().isBlank() ? "@s" : payload.recipient();
                xtdpotato.xero_quest_journal.core.model.MailNodeSettings.validatePlayer(recipient);
                var targets = EntityArgument.players().parse(new StringReader(recipient))
                        .findPlayers(player.createCommandSourceStack().withPermission(2));
                if (!targets.isEmpty() && targets.size() <= 32) {
                    boolean sent = true;
                    for (ServerPlayer target : targets) {
                        sent &= resend != null ? MailboxRuntime.resend(player, target, resend)
                                : MailboxRuntime.sendIfAbsent(target, payload.id(), payload.sender(),
                                payload.subject(), payload.body(), payload.expiresIn(), payload.gameTime(),
                                payload.icon(), payload.treasured(), payload.attachments(),
                                payload.banner(), payload.targetTaskId(), payload.bannerTiming());
                    }
                    result = sent ? "sent" : "mailbox_full";
                } else result = "invalid_recipient";
            } catch (Exception invalid) {
                result = "invalid_mail";
            }
        }
        context.reply(new MailboxActionResultPayload(payload.requestId(), result));
        context.reply(mailboxSnapshot(player));
    }

    private static void handleMailboxClaim(MailboxClaimPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var result = MailboxRuntime.claimChoices(player, payload.entries(), payload.allowDrop());
        context.reply(new MailboxActionResultPayload(payload.requestId(), result.result(), result.items()));
        context.reply(mailboxSnapshot(player));
    }

    private static void handleMailboxEdit(MailboxEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String result = MailboxRuntime.edit(player, payload.mail(), payload.expectedHash(), payload.preserveExpiry());
        context.reply(new MailboxActionResultPayload(payload.mail().requestId(), result));
        context.reply(mailboxSnapshot(player));
    }

    private static void handleMailboxDelete(MailboxDeletePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String result = MailboxRuntime.deleteMany(player, payload.expectedHashes());
        context.reply(new MailboxActionResultPayload(payload.requestId(), result));
        context.reply(mailboxSnapshot(player));
    }

    private static void handleMailboxTemplateSave(MailboxTemplateSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String result = "template_save_failed";
        if (!MailboxRuntime.canEdit(player)) {
            result = "permission";
        } else {
            try {
                var mail = payload.mail();
                MailTemplateStore.save(player, new MailDraft(mail.id(), mail.sender(), mail.subject(),
                        mail.body(), mail.icon(), mail.treasured(), List.of(), mail.attachments(),
                        mail.expiresIn(), mail.gameTime(), mail.banner(), mail.targetTaskId(), mail.bannerTiming()));
                result = "template_saved";
            } catch (Exception invalid) {
                XeroQuestJournal.LOGGER.warn("Rejected mail template from {}", player.getGameProfile().getName(),
                        invalid);
            }
        }
        context.reply(new MailboxActionResultPayload(payload.mail().requestId(), result));
    }

    private static void handleMailboxTemplateCatalogRequest(MailboxTemplateCatalogRequestPayload payload,
                                                             IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        List<MailTemplateEntry> templates = player.hasPermissions(2)
                ? MailTemplateStore.all().stream()
                .map(draft -> new MailTemplateEntry(draft.id(), draft.subject()))
                .toList()
                : List.of();
        context.reply(new MailboxTemplateCatalogPayload(templates));
    }

    private static MailboxSnapshotPayload mailboxSnapshot(ServerPlayer player) {
        List<MailboxRuntime.View> messages = MailboxRuntime.snapshot(player);
        return new MailboxSnapshotPayload(messages);
    }

    private static void sendSnapshotChunks(ServerPlayer player, IPayloadContext context) {
        Map<String, QuestRuntime.View> views = QuestRuntime.snapshot(player);
        List<Map<String, QuestRuntime.View>> chunks = new ArrayList<>();
        Map<String, QuestRuntime.View> current = new LinkedHashMap<>();
        int bytes = 16;
        for (var entry : views.entrySet()) {
            int entryBytes = progressEntryBytes(entry.getKey(), entry.getValue());
            if (!current.isEmpty() && bytes + entryBytes > PROGRESS_CHUNK_BYTES) {
                chunks.add(Map.copyOf(current));
                current.clear();
                bytes = 16;
            }
            current.put(entry.getKey(), entry.getValue());
            bytes += entryBytes;
        }
        if (!current.isEmpty() || chunks.isEmpty()) chunks.add(Map.copyOf(current));
        if (chunks.size() > MAX_PROGRESS_CHUNKS) {
            throw new IllegalArgumentException("Quest progress has too many chunks");
        }
        List<String> tracked = QuestRuntime.tracked(player);
        for (int index = 0; index < chunks.size(); index++) {
            context.reply(new ProgressSnapshotPayload(index, chunks.size(), QuestConfig.defaults().taskPagination,
                    chunks.get(index), tracked, QuestFocus.group(player), QuestConfig.defaults().tasksPerPage));
        }
    }

    private static int progressEntryBytes(String id, QuestRuntime.View view) {
        int bytes = id.length() * 3 + view.status().length() * 3 + 129;
        for (var reward : view.rewards()) {
            bytes += (reward.type().length() + reward.item().length()
                    + reward.quality().length() + reward.background().length()
                    + reward.claimMode().length()) * 3 + 26;
        }
        return bytes;
    }

    static void replyCatalog(IPayloadContext context, long revision, String json) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < json.length();) {
            int end = Math.min(json.length(), start + QuestCatalog.MAX_CHARACTERS);
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) end--;
            chunks.add(json.substring(start, end));
            start = end;
        }
        if (chunks.isEmpty()) chunks.add("");
        if (chunks.size() > MAX_CATALOG_CHUNKS) {
            throw new IllegalArgumentException("Catalog too large");
        }
        for (int index = 0; index < chunks.size(); index++) {
            context.reply(new CatalogPayload(revision, index, chunks.size(), chunks.get(index)));
        }
    }

    public record ProgressRequestPayload(long catalogRevision) implements CustomPacketPayload {
        public static final Type<ProgressRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "progress_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProgressRequestPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ProgressRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new ProgressRequestPayload(buffer.readVarLong());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, ProgressRequestPayload payload) {
                        buffer.writeVarLong(payload.catalogRevision());
                    }
                };

        @Override
        public Type<ProgressRequestPayload> type() {
            return TYPE;
        }
    }

    public record ActionPayload(long requestId, String questId, int action, long catalogRevision) implements CustomPacketPayload {
        public static final int ACTIVATE = 0;
        public static final int CLAIM = 1;
        public static final int TRACK = 2;
        public static final int RESET = 3;
        public static final int CLAIM_DROP = 4;
        public static final int CLAIM_ALL = 5;
        public static final int FOCUS = 6;
        public static final int DAILY_BONUS = 7;
        public static final int MARK_READ = 8;
        public static final int MARK_ALL_READ = 9;
        public static final Type<ActionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "quest_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ActionPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new ActionPayload(buffer.readVarLong(), buffer.readUtf(256), buffer.readVarInt(), buffer.readVarLong());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, ActionPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeUtf(payload.questId(), 256);
                        buffer.writeVarInt(payload.action());
                        buffer.writeVarLong(payload.catalogRevision());
                    }
                };

        public ActionPayload {
            questId = questId == null ? "" : questId;
        }

        @Override
        public Type<ActionPayload> type() {
            return TYPE;
        }
    }

    public record ResetBatchPayload(long requestId, long catalogRevision,
                                    List<String> questIds) implements CustomPacketPayload {
        public static final int MAX_RESET_IDS = 512;
        public static final Type<ResetBatchPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "quest_reset_batch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetBatchPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ResetBatchPayload decode(RegistryFriendlyByteBuf buffer) {
                        long requestId = buffer.readVarLong();
                        long revision = buffer.readVarLong();
                        int size = checkedSize(buffer.readVarInt(), MAX_RESET_IDS, "quest reset");
                        List<String> ids = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) ids.add(buffer.readUtf(256));
                        return new ResetBatchPayload(requestId, revision, ids);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, ResetBatchPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeVarLong(payload.catalogRevision());
                        buffer.writeVarInt(payload.questIds().size());
                        payload.questIds().forEach(id -> buffer.writeUtf(id, 256));
                    }
                };

        public ResetBatchPayload {
            questIds = questIds == null ? List.of() : List.copyOf(questIds);
            if (questIds.isEmpty() || questIds.size() > MAX_RESET_IDS
                    || questIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 256)
                    || new java.util.HashSet<>(questIds).size() != questIds.size())
                throw new IllegalArgumentException("Invalid batch reset payload");
        }

        @Override
        public Type<ResetBatchPayload> type() { return TYPE; }
    }

    public record LockBatchPayload(long requestId, long catalogRevision, List<String> questIds,
                                   boolean locked) implements CustomPacketPayload {
        public static final int MAX_IDS = 4096;
        public static final Type<LockBatchPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "quest_lock_batch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LockBatchPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public LockBatchPayload decode(RegistryFriendlyByteBuf buffer) {
                        long requestId = buffer.readVarLong();
                        long revision = buffer.readVarLong();
                        boolean locked = buffer.readBoolean();
                        int size = checkedSize(buffer.readVarInt(), MAX_IDS, "quest lock");
                        List<String> ids = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) ids.add(buffer.readUtf(128));
                        return new LockBatchPayload(requestId, revision, ids, locked);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, LockBatchPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeVarLong(payload.catalogRevision());
                        buffer.writeBoolean(payload.locked());
                        buffer.writeVarInt(payload.questIds().size());
                        payload.questIds().forEach(id -> buffer.writeUtf(id, 128));
                    }
                };

        public LockBatchPayload {
            questIds = questIds == null ? List.of() : List.copyOf(questIds);
            if (questIds.isEmpty() || questIds.size() > MAX_IDS
                    || questIds.stream().anyMatch(id -> id.isBlank() || id.length() > 128)
                    || new java.util.HashSet<>(questIds).size() != questIds.size())
                throw new IllegalArgumentException("Invalid batch lock payload");
        }

        @Override public Type<LockBatchPayload> type() { return TYPE; }
    }

    public record ClaimChoicePayload(long requestId, String questId, long catalogRevision,
                                     Map<Integer, Integer> selections, boolean allowDrop) implements CustomPacketPayload {
        private static final int MAX_SELECTIONS = 128;
        public static final Type<ClaimChoicePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "quest_claim_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimChoicePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ClaimChoicePayload decode(RegistryFriendlyByteBuf buffer) {
                        long requestId = buffer.readVarLong();
                        String questId = buffer.readUtf(256);
                        long catalogRevision = buffer.readVarLong();
                        boolean allowDrop = buffer.readBoolean();
                        int size = checkedSize(buffer.readVarInt(), MAX_SELECTIONS, "loot choice");
                        Map<Integer, Integer> selections = new LinkedHashMap<>();
                        for (int i = 0; i < size; i++) {
                            int rewardIndex = buffer.readVarInt();
                            int choiceIndex = buffer.readVarInt();
                            if (selections.put(rewardIndex, choiceIndex) != null) {
                                throw new IllegalArgumentException("Duplicate loot choice index");
                            }
                        }
                        return new ClaimChoicePayload(requestId, questId, catalogRevision, selections, allowDrop);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, ClaimChoicePayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeUtf(payload.questId(), 256);
                        buffer.writeVarLong(payload.catalogRevision());
                        buffer.writeBoolean(payload.allowDrop());
                        buffer.writeVarInt(payload.selections().size());
                        payload.selections().forEach((rewardIndex, choiceIndex) -> {
                            buffer.writeVarInt(rewardIndex);
                            buffer.writeVarInt(choiceIndex);
                        });
                    }
                };

        public ClaimChoicePayload {
            questId = questId == null ? "" : questId;
            selections = selections == null ? Map.of() : Map.copyOf(selections);
            if (questId.length() > 256 || selections.size() > MAX_SELECTIONS
                    || selections.keySet().stream().anyMatch(index -> index == null || index < 0 || index > 128)
                    || selections.values().stream().anyMatch(index -> index == null || index < 0 || index > 256)) {
                throw new IllegalArgumentException("Invalid loot choice payload");
            }
        }

        @Override
        public Type<ClaimChoicePayload> type() {
            return TYPE;
        }
    }

    public record PageSizePayload(long requestId, int value) implements CustomPacketPayload {
        public static final Type<PageSizePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "task_page_size"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PageSizePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public PageSizePayload decode(RegistryFriendlyByteBuf buffer) {
                return new PageSizePayload(buffer.readVarLong(), buffer.readVarInt());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, PageSizePayload payload) {
                buffer.writeVarLong(payload.requestId()); buffer.writeVarInt(payload.value());
            }
        };
        public PageSizePayload {
            if (value < 10 || value > 1000) throw new IllegalArgumentException("Invalid task page size");
        }
        @Override public Type<PageSizePayload> type() { return TYPE; }
    }

    public record ConfigPayload(long requestId, String key, boolean value) implements CustomPacketPayload {
        public static final Type<ConfigPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ConfigPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new ConfigPayload(buffer.readVarLong(), buffer.readUtf(64), buffer.readBoolean());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, ConfigPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeUtf(payload.key(), 64);
                        buffer.writeBoolean(payload.value());
                    }
                };

        public ConfigPayload {
            key = key == null ? "" : key;
        }

        @Override
        public Type<ConfigPayload> type() {
            return TYPE;
        }
    }

    public record HotEventCanvasPayload(long requestId, String json) implements CustomPacketPayload {
        public static final Type<HotEventCanvasPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "hot_event_canvas"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HotEventCanvasPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public HotEventCanvasPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new HotEventCanvasPayload(buffer.readVarLong(), buffer.readUtf(262144));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, HotEventCanvasPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeUtf(payload.json(), 262144);
                    }
                };

        public HotEventCanvasPayload {
            json = json == null ? "" : json;
            if (json.length() > 262144) throw new IllegalArgumentException("Canvas payload too large");
        }

        @Override
        public Type<HotEventCanvasPayload> type() { return TYPE; }
    }

    public record CatalogPayload(long revision, int chunkIndex, int chunkCount, String json) implements CustomPacketPayload {
        public static final Type<CatalogPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CatalogPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public CatalogPayload decode(RegistryFriendlyByteBuf buffer) {
                return new CatalogPayload(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readUtf(QuestCatalog.MAX_CHARACTERS));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, CatalogPayload payload) {
                buffer.writeVarLong(payload.revision());
                buffer.writeVarInt(payload.chunkIndex());
                buffer.writeVarInt(payload.chunkCount());
                buffer.writeUtf(payload.json(), QuestCatalog.MAX_CHARACTERS);
            }
        };

        public CatalogPayload(long revision, String json) {
            this(revision, 0, 1, json);
        }

        public CatalogPayload {
            if (chunkIndex < 0 || chunkCount < 1 || chunkIndex >= chunkCount
                    || chunkCount > MAX_CATALOG_CHUNKS || json == null
                    || json.length() > QuestCatalog.MAX_CHARACTERS) {
                throw new IllegalArgumentException("Invalid catalog chunk");
            }
        }

        @Override
        public Type<CatalogPayload> type() { return TYPE; }
    }

    public record ActionResultPayload(long requestId, String result) implements CustomPacketPayload {
        public static final Type<ActionResultPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "action_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionResultPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ActionResultPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new ActionResultPayload(buffer.readVarLong(), buffer.readUtf(64));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, ActionResultPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeUtf(payload.result(), 64);
                    }
                };

        public ActionResultPayload {
            result = result == null ? "save" : result;
        }

        @Override
        public Type<ActionResultPayload> type() {
            return TYPE;
        }
    }

    public record AutomaticClaimPayload(String questId, String title,
                                        List<QuestDefinition.Reward> rewards) implements CustomPacketPayload {
        public static final Type<AutomaticClaimPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "automatic_claim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AutomaticClaimPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public AutomaticClaimPayload decode(RegistryFriendlyByteBuf buffer) {
                        String id = buffer.readUtf(256);
                        String title = buffer.readUtf(256);
                        int size = checkedSize(buffer.readVarInt(), MAX_REWARDS, "reward");
                        List<QuestDefinition.Reward> rewards = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) rewards.add(readReward(buffer));
                        return new AutomaticClaimPayload(id, title, rewards);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, AutomaticClaimPayload payload) {
                        buffer.writeUtf(payload.questId(), 256);
                        buffer.writeUtf(payload.title(), 256);
                        buffer.writeVarInt(payload.rewards().size());
                        payload.rewards().forEach(reward -> writeReward(buffer, reward));
                    }
                };

        public AutomaticClaimPayload {
            rewards = List.copyOf(rewards);
            if (questId.length() > 256 || title.length() > 256 || rewards.size() > MAX_REWARDS)
                throw new IllegalArgumentException("Automatic claim receipt exceeds protocol limits");
        }

        @Override public Type<AutomaticClaimPayload> type() { return TYPE; }
    }

    public record MailboxRequestPayload() implements CustomPacketPayload {
        public static final Type<MailboxRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxRequestPayload> STREAM_CODEC =
                StreamCodec.unit(new MailboxRequestPayload());

        @Override
        public Type<MailboxRequestPayload> type() {
            return TYPE;
        }
    }

    public record MailboxActionPayload(long requestId, String mailId, int action) implements CustomPacketPayload {
        public static final int READ = 0;
        public static final int CLAIM = 1;
        public static final int CLAIM_ALL = 2;
        public static final int DELETE_READ = 3;
        public static final int STAR = 4;
        public static final int DELETE = 5;
        public static final int OPEN_TREASURE = 6;
        public static final int SEEN_TREASURE_BOX = 7;
        public static final Type<MailboxActionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxActionPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public MailboxActionPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new MailboxActionPayload(buffer.readVarLong(), buffer.readUtf(256), buffer.readVarInt());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, MailboxActionPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeUtf(payload.mailId() == null ? "" : payload.mailId(), 256);
                        buffer.writeVarInt(payload.action());
                    }
                };

        @Override
        public Type<MailboxActionPayload> type() {
            return TYPE;
        }
    }

    public record MailboxSnapshotPayload(List<MailboxRuntime.View> messages) implements CustomPacketPayload {
        private static final int MAX_MESSAGES = 1000;
        private static final int MAX_ATTACHMENTS = 128;
        public static final Type<MailboxSnapshotPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxSnapshotPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public MailboxSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
                        int size = checkedSize(buffer.readVarInt(), MAX_MESSAGES, "mail");
                        List<MailboxRuntime.View> messages = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            String id = buffer.readUtf(256);
                            String sender = buffer.readUtf(256);
                            String recipient = buffer.readUtf(256);
                            String subject = buffer.readUtf(512);
                            String body = buffer.readUtf(8192);
                            long createdAt = buffer.readLong();
                            long expiresAt = buffer.readLong();
                            boolean read = buffer.readBoolean();
                            boolean starred = buffer.readBoolean();
                            String icon = buffer.readUtf(8192);
                            boolean treasured = buffer.readBoolean();
                            boolean archived = buffer.readBoolean();
                            boolean treasureNew = buffer.readBoolean();
                            boolean treasureUnseen = buffer.readBoolean();
                            boolean gameTime = buffer.readBoolean();
                            long clock = buffer.readLong();
                            String banner = buffer.readUtf(8192);
                            String targetTaskId = buffer.readUtf(256);
                            MailBannerTiming timing = readBannerTiming(buffer);
                            long remaining = buffer.readVarLong();
                            int attachments = checkedSize(buffer.readVarInt(), MAX_ATTACHMENTS, "attachment");
                            List<MailboxRuntime.Attachment> values = new ArrayList<>(attachments);
                            for (int j = 0; j < attachments; j++) {
                                values.add(new MailboxRuntime.Attachment(buffer.readUtf(32), buffer.readUtf(8192),
                                        buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                                        buffer.readUtf(16), buffer.readUtf(7)));
                            }
                            messages.add(new MailboxRuntime.View(id, sender, recipient, subject, body, createdAt, expiresAt,
                                    read, starred, icon, treasured, archived, treasureNew, treasureUnseen,
                                    gameTime, clock, values, banner, targetTaskId, timing, remaining));
                        }
                        return new MailboxSnapshotPayload(messages);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, MailboxSnapshotPayload payload) {
                        buffer.writeVarInt(payload.messages().size());
                        for (MailboxRuntime.View message : payload.messages()) {
                            buffer.writeUtf(message.id(), 256);
                            buffer.writeUtf(message.sender(), 256);
                            buffer.writeUtf(message.recipient(), 256);
                            buffer.writeUtf(message.subject(), 512);
                            buffer.writeUtf(message.body(), 8192);
                            buffer.writeLong(message.createdAt());
                            buffer.writeLong(message.expiresAt());
                            buffer.writeBoolean(message.read());
                            buffer.writeBoolean(message.starred());
                            buffer.writeUtf(message.icon(), 8192);
                            buffer.writeBoolean(message.treasured());
                            buffer.writeBoolean(message.archived());
                            buffer.writeBoolean(message.treasureNew());
                            buffer.writeBoolean(message.treasureUnseen());
                            buffer.writeBoolean(message.gameTime());
                            buffer.writeLong(message.clock());
                            buffer.writeUtf(message.banner(), 8192);
                            buffer.writeUtf(message.targetTaskId(), 256);
                            writeBannerTiming(buffer, message.bannerTiming());
                            buffer.writeVarLong(message.bannerRemainingSeconds());
                            buffer.writeVarInt(message.attachments().size());
                            for (MailboxRuntime.Attachment attachment : message.attachments()) {
                                buffer.writeUtf(attachment.type(), 32);
                                buffer.writeUtf(attachment.item(), 8192);
                                buffer.writeVarInt(attachment.count());
                                buffer.writeBoolean(attachment.claimed());
                                buffer.writeBoolean(attachment.hidden());
                                buffer.writeBoolean(attachment.hidePopup());
                                buffer.writeUtf(attachment.quality(), 16);
                                buffer.writeUtf(attachment.background(), 7);
                            }
                        }
                    }
                };

        public MailboxSnapshotPayload {
            messages = List.copyOf(messages);
            if (messages.size() > MAX_MESSAGES) {
                throw new IllegalArgumentException("Mailbox exceeds protocol limit");
            }
        }

        @Override
        public Type<MailboxSnapshotPayload> type() {
            return TYPE;
        }
    }

    public record MailboxNoticePayload(String id, String sender, String subject) implements CustomPacketPayload {
        public static final Type<MailboxNoticePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxNoticePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public MailboxNoticePayload decode(RegistryFriendlyByteBuf buffer) {
                        return new MailboxNoticePayload(buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(512));
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer, MailboxNoticePayload value) {
                        buffer.writeUtf(value.id(), 256);
                        buffer.writeUtf(value.sender(), 256);
                        buffer.writeUtf(value.subject(), 512);
                    }
                };
        @Override public Type<MailboxNoticePayload> type() { return TYPE; }
    }

    public record AnecdotePayload(double offsetX, double offsetY, double scale, String position)
            implements CustomPacketPayload {
        public AnecdotePayload() { this(0, 0, .7, "top"); }
        public static final Type<AnecdotePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "anecdote"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AnecdotePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public AnecdotePayload decode(RegistryFriendlyByteBuf buffer) {
                        return new AnecdotePayload(buffer.readDouble(), buffer.readDouble(),
                                buffer.readDouble(), buffer.readUtf(64));
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer, AnecdotePayload value) {
                        buffer.writeDouble(value.offsetX());
                        buffer.writeDouble(value.offsetY());
                        buffer.writeDouble(value.scale());
                        buffer.writeUtf(value.position(), 64);
                    }
                };

        public AnecdotePayload {
            if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)
                    || Math.abs(offsetX) > 8192 || Math.abs(offsetY) > 8192)
                throw new IllegalArgumentException("Anecdote position offsets must be finite and within 8192 pixels");
            if (!Double.isFinite(scale) || scale < .1 || scale > 2)
                throw new IllegalArgumentException("Anecdote scale must be between 0.1 and 2");
            position = normalizePosition(position);
        }

        public static String normalizePosition(String value) {
            if (value == null || value.isBlank()) return "top";
            var anchors = new java.util.LinkedHashSet<String>();
            for (String part : value.toLowerCase(java.util.Locale.ROOT).split(",")) {
                String anchor = switch (part.strip()) {
                    case "start" -> "left";
                    case "end" -> "right";
                    case "top", "bottom", "left", "right", "center" -> part.strip();
                    default -> throw new IllegalArgumentException("Unknown anecdote anchor: " + part.strip());
                };
                anchors.add(anchor);
            }
            if (anchors.contains("top") && anchors.contains("bottom"))
                throw new IllegalArgumentException("Choose top or bottom, not both");
            if (anchors.contains("left") && anchors.contains("right"))
                throw new IllegalArgumentException("Choose left/start or right/end, not both");
            return String.join(",", anchors);
        }

        @Override public Type<AnecdotePayload> type() { return TYPE; }
    }

    public record ToastPayload(String content) implements CustomPacketPayload {
        public static final Type<ToastPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "toast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToastPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public ToastPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new ToastPayload(buffer.readUtf(2048));
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer, ToastPayload value) {
                        buffer.writeUtf(value.content(), 2048);
                    }
                };

        public ToastPayload {
            content = content == null ? "" : content;
            if (content.length() > 2048) throw new IllegalArgumentException("Toast is too long");
        }

        @Override public Type<ToastPayload> type() { return TYPE; }
    }

    public record ItemPickupNoticePayload(net.minecraft.world.item.ItemStack stack, int pickedCount,
                                          int current, int required, String questId, String action) implements CustomPacketPayload {
        public ItemPickupNoticePayload(net.minecraft.world.item.ItemStack stack, int pickedCount, int current, int required) {
            this(stack, pickedCount, current, required, "", "acquire");
        }
        public static final Type<ItemPickupNoticePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "item_pickup_notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemPickupNoticePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public ItemPickupNoticePayload decode(RegistryFriendlyByteBuf buffer) {
                        return new ItemPickupNoticePayload(net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buffer),
                                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(128), buffer.readUtf(8));
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer, ItemPickupNoticePayload value) {
                        net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buffer, value.stack());
                        buffer.writeVarInt(value.pickedCount());
                        buffer.writeVarInt(value.current());
                        buffer.writeVarInt(value.required());
                        buffer.writeUtf(value.questId(), 128);
                        buffer.writeUtf(value.action(), 8);
                    }
                };

        public ItemPickupNoticePayload {
            stack = stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack.copy();
            if (stack.isEmpty() || pickedCount < 1 || pickedCount > 1_000_000
                    || current < 0 || current > 1_000_000 || required < 1 || required > 1_000_000
                    || questId == null || questId.length() > 128 || !List.of("acquire", "submit").contains(action))
                throw new IllegalArgumentException("Invalid tracked item pickup notice");
        }

        @Override public Type<ItemPickupNoticePayload> type() { return TYPE; }
    }

    public record MailboxSendPayload(long requestId, String recipient, String id, String sender,
                                     String subject, String body, String icon, boolean treasured,
                                     long expiresIn, boolean gameTime,
                                     List<MailboxRuntime.Attachment> attachments,
                                     String banner, String targetTaskId, MailBannerTiming bannerTiming) implements CustomPacketPayload {
        public MailboxSendPayload(long requestId, String recipient, String id, String sender,
                                  String subject, String body, String icon, boolean treasured,
                                  long expiresIn, boolean gameTime, List<MailboxRuntime.Attachment> attachments,
                                  String banner, String targetTaskId) {
            this(requestId, recipient, id, sender, subject, body, icon, treasured, expiresIn, gameTime,
                    attachments, banner, targetTaskId, MailBannerTiming.NONE);
        }
        public MailboxSendPayload(long requestId, String recipient, String id, String sender,
                                  String subject, String body, String icon, boolean treasured,
                                  long expiresIn, boolean gameTime,
                                  List<MailboxRuntime.Attachment> attachments) {
            this(requestId, recipient, id, sender, subject, body, icon, treasured, expiresIn, gameTime,
                    attachments, "", "");
        }
        public MailboxSendPayload(long requestId, String recipient, String id, String sender,
                                  String subject, String body, String icon, boolean treasured,
                                  List<MailboxRuntime.Attachment> attachments) {
            this(requestId, recipient, id, sender, subject, body, icon, treasured, 0, false, attachments);
        }
        public MailboxSendPayload {
            xtdpotato.xero_quest_journal.core.model.MailNodeSettings.validateValue("expiresIn", Long.toString(expiresIn));
            attachments = List.copyOf(attachments);
            banner = banner == null ? "" : banner;
            targetTaskId = targetTaskId == null ? "" : targetTaskId;
            bannerTiming = bannerTiming == null ? MailBannerTiming.NONE : bannerTiming;
            if (banner.length() > 8192 || targetTaskId.length() > 256)
                throw new IllegalArgumentException("Mail banner or task reference is too long");
            if (attachments.size() > 128) throw new IllegalArgumentException("Too many mail rewards");
        }
        public static final Type<MailboxSendPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_send"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxSendPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public MailboxSendPayload decode(RegistryFriendlyByteBuf buffer) {
                        long request = buffer.readVarLong();
                        String recipient = buffer.readUtf(256), id = buffer.readUtf(256), sender = buffer.readUtf(256);
                        String subject = buffer.readUtf(512), body = buffer.readUtf(8192), icon = buffer.readUtf(8192);
                        boolean treasured = buffer.readBoolean();
                        long expiresIn = buffer.readVarLong();
                        boolean gameTime = buffer.readBoolean();
                        String banner = buffer.readUtf(8192);
                        String targetTaskId = buffer.readUtf(256);
                        MailBannerTiming timing = readBannerTiming(buffer);
                        int size = checkedSize(buffer.readVarInt(), 128, "mail reward");
                        List<MailboxRuntime.Attachment> attachments = new ArrayList<>();
                        for (int i = 0; i < size; i++)
                            attachments.add(new MailboxRuntime.Attachment(buffer.readUtf(32), buffer.readUtf(8192),
                                    buffer.readVarInt(), false, buffer.readBoolean(), buffer.readBoolean(),
                                    buffer.readUtf(16), buffer.readUtf(7)));
                        return new MailboxSendPayload(request, recipient, id, sender, subject, body, icon, treasured,
                                expiresIn, gameTime, attachments, banner, targetTaskId, timing);
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer, MailboxSendPayload value) {
                        buffer.writeVarLong(value.requestId());
                        buffer.writeUtf(value.recipient(), 256);
                        buffer.writeUtf(value.id(), 256);
                        buffer.writeUtf(value.sender(), 256);
                        buffer.writeUtf(value.subject(), 512);
                        buffer.writeUtf(value.body(), 8192);
                        buffer.writeUtf(value.icon(), 8192);
                        buffer.writeBoolean(value.treasured());
                        buffer.writeVarLong(value.expiresIn());
                        buffer.writeBoolean(value.gameTime());
                        buffer.writeUtf(value.banner(), 8192);
                        buffer.writeUtf(value.targetTaskId(), 256);
                        writeBannerTiming(buffer, value.bannerTiming());
                        buffer.writeVarInt(value.attachments().size());
                        for (var attachment : value.attachments()) {
                            buffer.writeUtf(attachment.type(), 32);
                            buffer.writeUtf(attachment.item(), 8192);
                            buffer.writeVarInt(attachment.count());
                            buffer.writeBoolean(attachment.hidden());
                            buffer.writeBoolean(attachment.hidePopup());
                            buffer.writeUtf(attachment.quality(), 16);
                            buffer.writeUtf(attachment.background(), 7);
                        }
                    }
                };
        @Override public Type<MailboxSendPayload> type() { return TYPE; }
    }

    public record MailboxResendPayload(MailboxSendPayload mail, String sourceId,
                                       String expectedHash) implements CustomPacketPayload {
        public MailboxResendPayload {
            if (sourceId.isBlank() || sourceId.length() > 256 || expectedHash.length() != 64)
                throw new IllegalArgumentException("Invalid resend source");
        }
        public static final Type<MailboxResendPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_resend"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxResendPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public MailboxResendPayload decode(RegistryFriendlyByteBuf buffer) {
                return new MailboxResendPayload(MailboxSendPayload.STREAM_CODEC.decode(buffer),
                        buffer.readUtf(256), buffer.readUtf(64));
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, MailboxResendPayload payload) {
                MailboxSendPayload.STREAM_CODEC.encode(buffer, payload.mail());
                buffer.writeUtf(payload.sourceId(), 256);
                buffer.writeUtf(payload.expectedHash(), 64);
            }
        };
        @Override public Type<MailboxResendPayload> type() { return TYPE; }
    }

    private static MailBannerTiming readBannerTiming(RegistryFriendlyByteBuf buffer) {
        return new MailBannerTiming(buffer.readUtf(16), buffer.readVarLong(),
                buffer.readBoolean(), buffer.readUtf(129));
    }

    private static void writeBannerTiming(RegistryFriendlyByteBuf buffer, MailBannerTiming timing) {
        buffer.writeUtf(timing.mode(), 16);
        buffer.writeVarLong(timing.seconds());
        buffer.writeBoolean(timing.gameTime());
        buffer.writeUtf(timing.variable(), 129);
    }

    public record MailboxTemplateSavePayload(MailboxSendPayload mail) implements CustomPacketPayload {
        public static final Type<MailboxTemplateSavePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_template_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxTemplateSavePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public MailboxTemplateSavePayload decode(RegistryFriendlyByteBuf buffer) {
                        return new MailboxTemplateSavePayload(MailboxSendPayload.STREAM_CODEC.decode(buffer));
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer, MailboxTemplateSavePayload value) {
                        MailboxSendPayload.STREAM_CODEC.encode(buffer, value.mail());
                    }
                };
        @Override public Type<MailboxTemplateSavePayload> type() { return TYPE; }
    }

    public record MailboxClaimEntry(String mailId, String expectedHash, Map<Integer, Integer> selections) {
        public MailboxClaimEntry {
            selections = Map.copyOf(selections);
            if (mailId.isBlank() || mailId.length() > 256 || expectedHash.length() != 64
                    || selections.size() > 128 || selections.entrySet().stream().anyMatch(e ->
                    e.getKey() < 0 || e.getKey() >= 128 || e.getValue() < 0 || e.getValue() >= 256))
                throw new IllegalArgumentException("Invalid mail choice");
        }
    }

    public record MailboxClaimPayload(long requestId, List<MailboxClaimEntry> entries,
                                       boolean allowDrop) implements CustomPacketPayload {
        public MailboxClaimPayload {
            entries = List.copyOf(entries);
            if (entries.isEmpty() || entries.size() > 1000
                    || entries.stream().map(MailboxClaimEntry::mailId).distinct().count() != entries.size()
                    || entries.stream().mapToInt(e -> e.selections().size()).sum() > 4096)
                throw new IllegalArgumentException("Invalid mail claim batch");
        }
        public static final Type<MailboxClaimPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_claim_choices"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxClaimPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public MailboxClaimPayload decode(RegistryFriendlyByteBuf buffer) {
                long request = buffer.readVarLong();
                boolean drop = buffer.readBoolean();
                int size = checkedSize(buffer.readVarInt(), 1000, "mail claim");
                var entries = new ArrayList<MailboxClaimEntry>();
                for (int i = 0; i < size; i++) {
                    String id = buffer.readUtf(256), hash = buffer.readUtf(64);
                    int count = checkedSize(buffer.readVarInt(), 128, "mail choices");
                    var choices = new LinkedHashMap<Integer, Integer>();
                    for (int j = 0; j < count; j++)
                        if (choices.put(buffer.readVarInt(), buffer.readVarInt()) != null)
                            throw new IllegalArgumentException("Duplicate mail choice");
                    entries.add(new MailboxClaimEntry(id, hash, choices));
                }
                return new MailboxClaimPayload(request, entries, drop);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, MailboxClaimPayload payload) {
                buffer.writeVarLong(payload.requestId());
                buffer.writeBoolean(payload.allowDrop());
                buffer.writeVarInt(payload.entries().size());
                for (var entry : payload.entries()) {
                    buffer.writeUtf(entry.mailId(), 256);
                    buffer.writeUtf(entry.expectedHash(), 64);
                    buffer.writeVarInt(entry.selections().size());
                    entry.selections().forEach((index, choice) -> {
                        buffer.writeVarInt(index);
                        buffer.writeVarInt(choice);
                    });
                }
            }
        };
        @Override public Type<MailboxClaimPayload> type() { return TYPE; }
    }

    public record MailboxEditPayload(MailboxSendPayload mail, String expectedHash,
                                      boolean preserveExpiry) implements CustomPacketPayload {
        public MailboxEditPayload {
            if (expectedHash.length() != 64) throw new IllegalArgumentException("Invalid mail fingerprint");
        }
        public static final Type<MailboxEditPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxEditPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public MailboxEditPayload decode(RegistryFriendlyByteBuf buffer) {
                return new MailboxEditPayload(MailboxSendPayload.STREAM_CODEC.decode(buffer),
                        buffer.readUtf(64), buffer.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, MailboxEditPayload payload) {
                MailboxSendPayload.STREAM_CODEC.encode(buffer, payload.mail());
                buffer.writeUtf(payload.expectedHash(), 64);
                buffer.writeBoolean(payload.preserveExpiry());
            }
        };
        @Override public Type<MailboxEditPayload> type() { return TYPE; }
    }

    public record MailboxDeletePayload(long requestId, Map<String, String> expectedHashes) implements CustomPacketPayload {
        public MailboxDeletePayload {
            expectedHashes = Map.copyOf(expectedHashes);
            if (expectedHashes.isEmpty() || expectedHashes.size() > 1000
                    || expectedHashes.entrySet().stream().anyMatch(e ->
                    e.getKey().isBlank() || e.getKey().length() > 256 || e.getValue().length() != 64))
                throw new IllegalArgumentException("Invalid mail deletion");
        }
        public static final Type<MailboxDeletePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_delete_batch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxDeletePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public MailboxDeletePayload decode(RegistryFriendlyByteBuf buffer) {
                long request = buffer.readVarLong();
                int count = checkedSize(buffer.readVarInt(), 1000, "mail deletion");
                var hashes = new LinkedHashMap<String, String>();
                for (int i = 0; i < count; i++)
                    if (hashes.put(buffer.readUtf(256), buffer.readUtf(64)) != null)
                        throw new IllegalArgumentException("Duplicate mail deletion");
                return new MailboxDeletePayload(request, hashes);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, MailboxDeletePayload payload) {
                buffer.writeVarLong(payload.requestId());
                buffer.writeVarInt(payload.expectedHashes().size());
                payload.expectedHashes().forEach((id, hash) -> {
                    buffer.writeUtf(id, 256);
                    buffer.writeUtf(hash, 64);
                });
            }
        };
        @Override public Type<MailboxDeletePayload> type() { return TYPE; }
    }

    public record MailboxTemplateCatalogRequestPayload() implements CustomPacketPayload {
        public static final Type<MailboxTemplateCatalogRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_template_catalog_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxTemplateCatalogRequestPayload> STREAM_CODEC =
                StreamCodec.unit(new MailboxTemplateCatalogRequestPayload());
        @Override public Type<MailboxTemplateCatalogRequestPayload> type() { return TYPE; }
    }

    public record MailTemplateEntry(String id, String subject) {
        public MailTemplateEntry {
            if (id == null || subject == null || id.length() > 128 || subject.length() > 512)
                throw new IllegalArgumentException("Invalid mail template entry");
        }
    }

    public record MailboxTemplateCatalogPayload(List<MailTemplateEntry> templates)
            implements CustomPacketPayload {
        private static final int MAX_TEMPLATES = 1024;
        public static final Type<MailboxTemplateCatalogPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_template_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxTemplateCatalogPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public MailboxTemplateCatalogPayload decode(RegistryFriendlyByteBuf buffer) {
                        int size = checkedSize(buffer.readVarInt(), MAX_TEMPLATES, "mail template");
                        List<MailTemplateEntry> values = new ArrayList<>(size);
                        for (int i = 0; i < size; i++)
                            values.add(new MailTemplateEntry(buffer.readUtf(128), buffer.readUtf(512)));
                        return new MailboxTemplateCatalogPayload(values);
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer,
                                                  MailboxTemplateCatalogPayload payload) {
                        buffer.writeVarInt(payload.templates().size());
                        for (var template : payload.templates()) {
                            buffer.writeUtf(template.id(), 128);
                            buffer.writeUtf(template.subject(), 512);
                        }
                    }
                };

        public MailboxTemplateCatalogPayload {
            templates = List.copyOf(templates);
            if (templates.size() > MAX_TEMPLATES) throw new IllegalArgumentException("Too many mail templates");
        }

        @Override public Type<MailboxTemplateCatalogPayload> type() { return TYPE; }
    }

    public record MailboxActionResultPayload(long requestId, String result,
                                            List<QuestDefinition.Reward> items) implements CustomPacketPayload {
        public MailboxActionResultPayload(long requestId, String result) {
            this(requestId, result, List.of());
        }
        public MailboxActionResultPayload {
            items = List.copyOf(items);
        }
        public static final Type<MailboxActionResultPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "mailbox_action_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MailboxActionResultPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public MailboxActionResultPayload decode(RegistryFriendlyByteBuf buffer) {
                        long request = buffer.readVarLong();
                        String result = buffer.readUtf(64);
                        int size = checkedSize(buffer.readVarInt(), 128000, "mail receipt");
                        List<QuestDefinition.Reward> items = new ArrayList<>(size);
                        for (int i = 0; i < size; i++)
                            items.add(new QuestDefinition.Reward("item", buffer.readUtf(8192), buffer.readVarInt(),
                                    buffer.readUtf(16), buffer.readUtf(7)));
                        return new MailboxActionResultPayload(request, result, items);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, MailboxActionResultPayload payload) {
                        buffer.writeVarLong(payload.requestId());
                        buffer.writeUtf(payload.result(), 64);
                        buffer.writeVarInt(payload.items().size());
                        for (var item : payload.items()) {
                            buffer.writeUtf(item.item(), 8192);
                            buffer.writeVarInt(item.count());
                            buffer.writeUtf(item.quality(), 16);
                            buffer.writeUtf(item.background(), 7);
                        }
                    }
                };

        @Override
        public Type<MailboxActionResultPayload> type() {
            return TYPE;
        }
    }

    public record OpenScreenPayload(String screen, String targetId) implements CustomPacketPayload {
        public static final Type<OpenScreenPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "open_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public OpenScreenPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new OpenScreenPayload(buffer.readUtf(32), buffer.readUtf(256));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, OpenScreenPayload payload) {
                        buffer.writeUtf(payload.screen(), 32);
                        buffer.writeUtf(payload.targetId(), 256);
                    }
                };

        public OpenScreenPayload {
            screen = screen == null ? "" : screen;
            targetId = targetId == null ? "" : targetId;
        }

        @Override
        public Type<OpenScreenPayload> type() {
            return TYPE;
        }
    }

    public record LocatedQuestPayload(String questId, String location) implements CustomPacketPayload {
        public static final Type<LocatedQuestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "located_quest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LocatedQuestPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public LocatedQuestPayload decode(RegistryFriendlyByteBuf buffer) {
                        return new LocatedQuestPayload(buffer.readUtf(256), buffer.readUtf(512));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, LocatedQuestPayload payload) {
                        buffer.writeUtf(payload.questId(), 256);
                        buffer.writeUtf(payload.location(), 512);
                    }
                };

        public LocatedQuestPayload {
            questId = questId == null ? "" : questId;
            location = location == null ? "" : location;
            if (questId.isBlank() || questId.length() > 256 || location.length() > 512)
                throw new IllegalArgumentException("Invalid located quest payload");
        }

        @Override
        public Type<LocatedQuestPayload> type() {
            return TYPE;
        }
    }

    public record ProgressSnapshotPayload(int chunkIndex, int chunkCount, boolean taskPagination,
                                          Map<String, QuestRuntime.View> views,
                                          List<String> tracked, String focusGroup, int tasksPerPage) implements CustomPacketPayload {
        public static final Type<ProgressSnapshotPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "progress_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSnapshotPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public ProgressSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
                        int chunkIndex = buffer.readVarInt();
                        int chunkCount = checkedSize(buffer.readVarInt(), MAX_PROGRESS_CHUNKS, "progress chunk");
                        boolean taskPagination = buffer.readBoolean();
                        int tasksPerPage = buffer.readVarInt();
                        String focusGroup = buffer.readUtf(256);
                        int size = checkedSize(buffer.readVarInt(), MAX_QUESTS, "quest");
                        Map<String, QuestRuntime.View> views = new LinkedHashMap<>();
                        for (int i = 0; i < size; i++) {
                            String id = buffer.readUtf(256);
                            String status = buffer.readUtf(32);
                            int current = buffer.readVarInt();
                            int required = buffer.readVarInt();
                            int rewardCount = checkedSize(buffer.readVarInt(), MAX_REWARDS, "reward");
                            List<QuestDefinition.Reward> rewards = new ArrayList<>(rewardCount);
                            for (int reward = 0; reward < rewardCount; reward++) rewards.add(readReward(buffer));
                            boolean hidden = buffer.readBoolean();
                            boolean triggersEnabled = buffer.readBoolean();
                            boolean focused = buffer.readBoolean();
                            int step = buffer.readVarInt();
                            int stepCount = checkedSize(buffer.readVarInt(), QuestDefinition.MAX_STEPS, "step");
                            boolean newTask = buffer.readBoolean();
                            views.put(id, new QuestRuntime.View(status, current, required, rewards,
                                    hidden, triggersEnabled, focused, step, stepCount, newTask));
                        }
                        int trackedSize = checkedSize(buffer.readVarInt(), 3, "tracked quest");
                        List<String> tracked = new ArrayList<>(trackedSize);
                        for (int i = 0; i < trackedSize; i++) {
                            tracked.add(buffer.readUtf(256));
                        }
                        return new ProgressSnapshotPayload(chunkIndex, chunkCount, taskPagination, views, tracked,
                                focusGroup, tasksPerPage);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, ProgressSnapshotPayload payload) {
                        buffer.writeVarInt(payload.chunkIndex());
                        buffer.writeVarInt(payload.chunkCount());
                        buffer.writeBoolean(payload.taskPagination());
                        buffer.writeVarInt(payload.tasksPerPage());
                        buffer.writeUtf(payload.focusGroup(), 256);
                        buffer.writeVarInt(payload.views().size());
                        payload.views().forEach((id, view) -> {
                            buffer.writeUtf(id, 256);
                            buffer.writeUtf(view.status(), 32);
                            buffer.writeVarInt(view.current());
                            buffer.writeVarInt(view.required());
                            buffer.writeVarInt(view.rewards().size());
                            view.rewards().forEach(reward -> writeReward(buffer, reward));
                            buffer.writeBoolean(view.hidden());
                            buffer.writeBoolean(view.triggersEnabled());
                            buffer.writeBoolean(view.focused());
                            buffer.writeVarInt(view.step());
                            buffer.writeVarInt(view.stepCount());
                            buffer.writeBoolean(view.newTask());
                        });
                        buffer.writeVarInt(payload.tracked().size());
                        payload.tracked().forEach(id -> buffer.writeUtf(id, 256));
                    }
                };

        public ProgressSnapshotPayload(Map<String, QuestRuntime.View> views, List<String> tracked) {
            this(0, 1, false, views, tracked, "");
        }

        public ProgressSnapshotPayload(int chunkIndex, int chunkCount, boolean taskPagination,
                                       Map<String, QuestRuntime.View> views, List<String> tracked, String focusGroup) {
            this(chunkIndex, chunkCount, taskPagination, views, tracked, focusGroup, 50);
        }

        public ProgressSnapshotPayload(boolean taskPagination, Map<String, QuestRuntime.View> views,
                                       List<String> tracked) {
            this(0, 1, taskPagination, views, tracked, "");
        }

        public ProgressSnapshotPayload(boolean taskPagination, Map<String, QuestRuntime.View> views,
                                       List<String> tracked, String focusGroup) {
            this(0, 1, taskPagination, views, tracked, focusGroup);
        }

        public ProgressSnapshotPayload(int chunkIndex, int chunkCount, boolean taskPagination,
                                       Map<String, QuestRuntime.View> views, List<String> tracked) {
            this(chunkIndex, chunkCount, taskPagination, views, tracked, "");
        }

        public ProgressSnapshotPayload {
            views = Map.copyOf(views);
            tracked = List.copyOf(tracked);
            focusGroup = focusGroup == null ? "" : focusGroup;
            if (chunkIndex < 0 || chunkCount < 1 || chunkIndex >= chunkCount
                    || chunkCount > MAX_PROGRESS_CHUNKS || views.size() > MAX_QUESTS || tracked.size() > 3
                    || focusGroup.length() > 256 || tasksPerPage < 10 || tasksPerPage > 1000) {
                throw new IllegalArgumentException("Quest snapshot exceeds protocol limits");
            }
            for (var entry : views.entrySet()) {
                var view = entry.getValue();
                if (entry.getKey().length() > 256 || view.status().length() > 32 || view.rewards().size() > MAX_REWARDS) {
                    throw new IllegalArgumentException("Quest snapshot entry exceeds protocol limits");
                }
                for (var reward : view.rewards()) {
                    if (reward.type().length() > 32 || reward.item().length() > 8192
                            || reward.quality().length() > 16 || reward.background().length() > 7
                            || reward.claimMode().length() > 16) {
                        throw new IllegalArgumentException("Reward exceeds protocol limits");
                    }
                }
            }
        }

        @Override
        public Type<ProgressSnapshotPayload> type() {
            return TYPE;
        }
    }

    private static int checkedSize(int size, int maximum, String kind) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + kind + " count: " + size);
        }
        return size;
    }

    private static QuestDefinition.Reward readReward(RegistryFriendlyByteBuf buffer) {
        return new QuestDefinition.Reward(buffer.readUtf(32), buffer.readUtf(8192), buffer.readVarInt(),
                buffer.readUtf(16), buffer.readUtf(7), buffer.readBoolean(), buffer.readUtf(16));
    }

    private static void writeReward(RegistryFriendlyByteBuf buffer, QuestDefinition.Reward reward) {
        buffer.writeUtf(reward.type(), 32);
        buffer.writeUtf(reward.item(), 8192);
        buffer.writeVarInt(reward.count());
        buffer.writeUtf(reward.quality(), 16);
        buffer.writeUtf(reward.background(), 7);
        buffer.writeBoolean(reward.displayed());
        buffer.writeUtf(reward.claimMode(), 16);
    }

    public record EditModePayload(boolean enabled) implements CustomPacketPayload {
        public static final Type<EditModePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID, "edit_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EditModePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override public EditModePayload decode(RegistryFriendlyByteBuf buffer) {
                        return new EditModePayload(buffer.readBoolean());
                    }
                    @Override public void encode(RegistryFriendlyByteBuf buffer, EditModePayload value) {
                        buffer.writeBoolean(value.enabled());
                    }
                };
        @Override public Type<EditModePayload> type() { return TYPE; }
    }
}
