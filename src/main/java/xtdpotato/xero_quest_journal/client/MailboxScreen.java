package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MailboxScreen extends AnimatedQuestScreen {
    private static final int ATTACHMENT_WIDTH = RewardCardRenderer.WIDTH;
    private static final int ATTACHMENT_HEIGHT = RewardCardRenderer.HEIGHT;
    private static final int ATTACHMENT_COLUMN_STEP = RewardCardRenderer.COLUMN_STEP;
    private static final int MAIL_ROW_STEP = 48;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final Screen parent;
    private String noticeId;
    private final boolean favoriteOnly;
    private ButtonStyleSheet styles;
    private String selectedId = "";
    private MailboxRuntime.View selected;
    private double scroll;
    private double detailScroll;
    private final ElasticScroll rowMotion = new ElasticScroll(), detailMotion = new ElasticScroll(),
            attachmentMotion = new ElasticScroll();
    private final java.util.ArrayList<MailRowButton> rows = new java.util.ArrayList<>();
    private int dragPane = -1;
    private boolean dragMoved;
    private double dragX, dragY, dragStart;
    private boolean draggingDetails;
    private double detailThumbOffset;
    private boolean draggingRows;
    private double rowThumbOffset;
    private int hoveredAttachment = -1;
    private long mailboxRevision = -1;
    private int requestTicks;
    private boolean confirmDelete;
    private String deleteId = "";
    private CompletableFuture<QuestNetwork.MailboxActionResultPayload> pending;
    private String actionMessage;
    private String timedActionMessage;
    private long actionMessageStartedAt;
    void showActionMessage(String result) { actionMessage = result; }
    private JournalNavButton confirmationCancel;
    private JournalNavButton confirmationConfirm;
    private MailFavoriteButton favoriteButton;
    private String favoriteMailId = "";
    private boolean authoringAllowed;
    private boolean composeMenuOpen;
    private int pressedComposeOption = -1;
    private java.util.Map<String, MailboxActionButton> previousActionButtons = java.util.Map.of();
    private boolean showHelp;
    private SmoothScroll helpMotion = new SmoothScroll();
    private boolean draggingHelp;
    private double helpDragOffset;
    private MailboxActionButton helpClose;
    private EditBox search;
    private String searchValue = "";
    private List<AbstractWidget> baseWidgets = List.of();
    private boolean helpScrollbar;
    private final UiCompositor rowCompositor = new UiCompositor();
    private final UiCompositor detailCompositor = new UiCompositor();
    private UiMotion detailReveal = new UiMotion(false, System.nanoTime());
    private final java.util.Map<String, Integer> rowEntranceOrder = new java.util.LinkedHashMap<>();
    private long rowEntranceStart;
    private int rowEntranceDuration;
    private boolean rowEntranceStarted;
    private boolean treasureAcknowledged;
    private boolean multiSelect;
    private final java.util.Set<String> selectedMailIds = new java.util.LinkedHashSet<>();
    private final java.util.List<StyledButton> contextMenu = new java.util.ArrayList<>();
    private int contextX, contextY;
    private String claimMailId = "";

    public MailboxScreen() {
        this(null, "", false);
    }

    public MailboxScreen(Screen parent) {
        this(parent, "", false);
    }

    public MailboxScreen(Screen parent, String mailId) {
        this(parent, mailId, false);
    }

    public MailboxScreen(Screen parent, String mailId, boolean favoriteOnly) {
        super(text("title"));
        this.parent = parent;
        this.noticeId = mailId == null ? "" : mailId;
        this.selectedId = this.noticeId;
        this.favoriteOnly = favoriteOnly;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.mailbox." + key, args);
    }

    @Override
    protected void init() {
        if (favoriteOnly && !treasureAcknowledged) {
            treasureAcknowledged = true;
            ClientMailbox.action("", QuestNetwork.MailboxActionPayload.SEEN_TREASURE_BOX);
        }
        var previousRows = new java.util.HashMap<String, MailRowButton>();
        rows.forEach(row -> previousRows.put(row.styleId(), row));
        MailFavoriteButton previousFavorite = favoriteButton;
        String previousFavoriteId = favoriteMailId;
        var previousActions = new java.util.HashMap<String, MailboxActionButton>();
        children().stream().filter(MailboxActionButton.class::isInstance)
                .map(MailboxActionButton.class::cast)
                .forEach(button -> previousActions.put(button.styleId(), button));
        previousActionButtons = previousActions;
        clearWidgets();
        rows.clear();
        favoriteButton = null;
        favoriteMailId = "";
        confirmationCancel = null;
        confirmationConfirm = null;
        helpClose = null;
        styles = ButtonStyleSheet.load();
        authoringAllowed = ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2);
        if (!authoringAllowed) {
            multiSelect = false;
            selectedMailIds.clear();
            contextMenu.clear();
        }
        selectedMailIds.retainAll(ClientMailbox.messages().stream().map(MailboxRuntime.View::id).toList());
        List<MailboxRuntime.View> messages = visibleMessages();
        selected = messages.stream().filter(mail -> mail.id().equals(selectedId)).findFirst()
                .orElse(noticeId.isEmpty() && !messages.isEmpty() ? messages.getFirst() : null);
        String nextId = selected == null ? "" : selected.id();
        if (!nextId.equals(selectedId)) {
            detailScroll = 0;
            detailMotion.jump(0);
            attachmentMotion.jump(0);
        }
        selectedId = nextId;
        if (!noticeId.isEmpty()) selectedId = noticeId;
        detailScroll = Math.clamp(detailScroll, 0, maxDetailScroll());
        clampScroll(messages.size());
        rowMotion.limit(Math.max(0, messages.size() * MAIL_ROW_STEP - (listBottom() - listTop())));
        detailMotion.limit(maxDetailScroll());
        attachmentMotion.limit(maxAttachmentScroll());
        helpMotion.limit(maxHelpScroll());

        int split = split();
        int listWidth = split - 40;
        int listTop = listTop();
        int listBottom = listBottom();
        if (favoriteOnly) {
            search = new EditBox(font, 42, 58, listWidth - 26, 12, text("search"));
            search.setBordered(false);
            search.setMaxLength(256);
            search.setValue(searchValue);
            search.setHint(text("search"));
            search.setResponder(value -> {
                searchValue = value;
                rowMotion.jump(0);
                scroll = 0;
                init();
                setFocused(search);
            });
            addRenderableWidget(search);
        }
        for (int index = 0; index < messages.size(); index++) {
            MailboxRuntime.View mail = messages.get(index);
            int y = listTop + index * MAIL_ROW_STEP - (int) scroll;
            MailRowButton row = new MailRowButton(20, y, listWidth, 42, mail, favoriteOnly,
                    ignored -> select(mail), styles);
            row.copyMotionFrom(previousRows.get(row.styleId()));
            row.clipTo(12, listTop, split - 5, listBottom);
            row.visible = row.getBottom() > listTop && row.getY() < listBottom;
            row.setSelectionHighlighted(multiSelect ? selectedMailIds.contains(mail.id()) : mail.id().equals(selectedId));
            row.multiSelection(multiSelect, selectedMailIds.contains(mail.id()));
            row.setTooltip(Tooltip.create(Component.literal(mail.subject())));
            rows.add(addRenderableWidget(row));
        }

        int buttonY = height - 32;
        if (!favoriteOnly) {
            JournalNavButton claimAll = action(20, buttonY, Math.min(126, listWidth / 2 - 4),
                    "claim_all", "mail_claim", () -> runAction("", QuestNetwork.MailboxActionPayload.CLAIM_ALL));
            claimAll.active = pending == null;
            JournalNavButton deleteRead = action(28 + claimAll.getWidth(), buttonY,
                    Math.min(126, listWidth - claimAll.getWidth() - 8),
                    "delete_read", "delete", () -> {
                    confirmDelete = true;
                    deleteId = "";
                    init();
                });
            deleteRead.active = pending == null;
        }

        int right = width - 12;
        MailboxActionButton close = new MailboxActionButton(right - 20, 12, 20, 20, text("close"),
                ignored -> onClose(), styles, favoriteOnly ? "mail-back" : "mail-close",
                JournalNavButton.Kind.ICON, "mail_close", "close");
        close.setTooltip(Tooltip.create(text("close")));
        close.hoverMotion();
        addRenderableWidget(close);
        if (!favoriteOnly && authoringAllowed) {
            var send = new MailboxActionButton(authorToolsLeft(), 10, 24, 24, text("compose"),
                    ignored -> composeMenuOpen = !composeMenuOpen, styles,
                    "mail-compose", JournalNavButton.Kind.ICON, "send", "compose");
            send.active = pending == null;
            send.setTooltip(Tooltip.create(text("compose")));
            addRenderableWidget(send);
        }
        if (authoringAllowed) {
            int toolX = favoriteOnly ? 24 : authorToolsLeft() + 30;
            var multiple = addRenderableWidget(new EditorIconButton(toolX, 10, text("multi_select"), () -> {
                multiSelect = !multiSelect;
                selectedMailIds.clear();
                contextMenu.clear();
                init();
            }, styles, "mail-multi-select", multiSelect ? "choose" : "all"));
            multiple.setSelectionHighlighted(multiSelect);
            multiple.active = pending == null;
            if (multiSelect) {
                var deleteMany = addRenderableWidget(new EditorIconButton(toolX + 30, 10, text("delete_selected"),
                        () -> confirmAuthorDelete(selectedMailIds), styles, "mail-delete-selected", "delete"));
                deleteMany.active = pending == null && !selectedMailIds.isEmpty();
            }
        }
        if (!favoriteOnly) {
            MailboxActionButton favorites = new MailboxActionButton(treasureIconX() + 6, 14, 16, 16,
                    text("favorite_box"), ignored -> minecraft.setScreen(new MailboxScreen(this, "", true)),
                    styles, "mail-favorite-box", JournalNavButton.Kind.ICON, "mail_treasure", "favorite_box");
            favorites.setTooltip(Tooltip.create(text("favorite_box")));
            addRenderableWidget(favorites);
        }
        if (!favoriteOnly) {
        MailboxActionButton help = new MailboxActionButton(right - 40, 14, 16, 16, text("help"),
                ignored -> {
                    showHelp = true;
                    helpMotion.jump(0);
                    init();
                }, styles, "mail-help", JournalNavButton.Kind.ICON, "mail_help", "help");
        help.setTooltip(Tooltip.create(text("help")));
        addRenderableWidget(help);
        }

        int detailActionWidth = Math.min(112, Math.max(1, width - 24 - (split + 18) - 30));
        int detailActionX = width - 24 - detailActionWidth;
        if (selected != null && selected.claimable()) {
            JournalNavButton claim = action(detailActionX, buttonY, detailActionWidth,
                    "claim", "mail_claim_single", () -> runAction(selected.id(), QuestNetwork.MailboxActionPayload.CLAIM));
            claim.active = pending == null;
        }
        if (selected != null) {
            MailFavoriteButton star = new MailFavoriteButton(detailActionX - 22, buttonY + 5, 14,
                    selected.starred(),
                    ignored -> {
                        if (pending != null) return;
                        starClicked();
                    }, styles);
            if (selected.id().equals(previousFavoriteId)) {
                star.copyAnimationFrom(previousFavorite);
                if (pending == null && star.targetStarred() != selected.starred())
                    star.trigger(selected.starred());
            }
            star.setTooltip(Tooltip.create(text("star")));
            star.active = pending == null && !star.animationPlaying();
            favoriteButton = star;
            if (!favoriteOnly) addRenderableWidget(star);
            favoriteMailId = selected.id();
            if (!selected.claimable()) {
                JournalNavButton delete = favoriteOnly
                        ? addRenderableWidget(new MailboxActionButton(right - 30, buttonY - 2, 26, 26, text("delete"),
                        ignored -> {
                            deleteId = selected.id();
                            confirmDelete = true;
                            init();
                        }, styles, "mail-delete", JournalNavButton.Kind.ICON, "delete", "delete"))
                        : action(detailActionX, buttonY, detailActionWidth,
                        "delete", "delete", () -> {
                            deleteId = selected.id();
                            confirmDelete = true;
                            init();
                        });
                delete.active = pending == null;
            }
        }

        baseWidgets = children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        if (confirmDelete || showHelp) {
            baseWidgets.stream()
                    .forEach(button -> {
                        button.active = false;
                        button.visible = false;
                    });
        }
        if (showHelp) {
            Viewport bounds = helpBounds();
            helpClose = new MailboxActionButton(bounds.right() - 38, bounds.top() + 12, 24, 24,
                    text("close"), ignored -> {
                        showHelp = false;
                        init();
                    }, styles, "mail-help-close", JournalNavButton.Kind.ICON, "mail_close", "help_close");
            helpClose.setTooltip(Tooltip.create(text("close")));
            helpClose.visible = true;
            helpClose.active = true;
            addRenderableWidget(helpClose);
        }
        if (confirmDelete) {
            int dialogWidth = confirmationWidth();
            int dialogX = (width - dialogWidth) / 2;
            int dialogY = (height - confirmationHeight()) / 2;
            int confirmButtonY = dialogY + confirmationHeight() - 34;
            confirmationCancel = action(dialogX + 12, confirmButtonY,
                    (dialogWidth - 32) / 2, "cancel", "cancel_choice", () -> {
                        confirmDelete = false;
                        deleteId = "";
                        init();
                    });
            confirmationCancel.active = true;
            confirmationConfirm = action(dialogX + 20 + confirmationCancel.getWidth(), confirmButtonY,
                    (dialogWidth - 32) / 2, "confirm", "confirm_choice", () -> {
                        confirmDelete = false;
                        String id = deleteId;
                        deleteId = "";
                        runAction(id, id.isEmpty() ? QuestNetwork.MailboxActionPayload.DELETE_READ
                                : QuestNetwork.MailboxActionPayload.DELETE);
                    });
            confirmationConfirm.active = true;
            confirmationCancel.extraCss("""
                    .journal-action { background-color:#505a70; border-color:#eee1ce; border-width:1px; color:#ffffff; }
                    .journal-action:hover { background-color:#646e82; }
                    """);
            confirmationConfirm.extraCss("""
                    .journal-action { background-color:#505a70; border-color:#eee1ce; border-width:1px; color:#ffffff; }
                    .journal-action:hover { background-color:#646e82; }
                    """);
            confirmationCancel.setHeight(24);
            confirmationConfirm.setHeight(24);
        }
    }

    private void starClicked() {
        if (selected == null || favoriteButton == null) return;
        favoriteButton.trigger(!selected.starred());
        runAction(selected.id(), QuestNetwork.MailboxActionPayload.STAR);
    }

    private MailboxActionButton action(int x, int y, int width, String key, String icon, Runnable command) {
        MailboxActionButton button = new MailboxActionButton(x, y, width, 24, text(key),
                ignored -> command.run(), styles, "mail-" + key, JournalNavButton.Kind.ACTION, icon, key);
        button.copyAnimationFrom(previousActionButtons.get(button.styleId()));
        button.compactContent();
        button.hoverMotion();
        addRenderableWidget(button);
        return button;
    }

    private List<MailboxRuntime.View> visibleMessages() {
        return ClientMailbox.messages().stream()
                .filter(mail -> !favoriteOnly || mail.archived())
                .filter(mail -> !favoriteOnly || searchValue.isBlank()
                        || QuestIcons.matchesSearch(searchValue, mail.subject(), mail.sender()))
                .toList();
    }

    private void select(MailboxRuntime.View mail) {
        if (multiSelect && authoringAllowed) {
            if (!selectedMailIds.add(mail.id())) selectedMailIds.remove(mail.id());
        }
        if (!selectedId.equals(mail.id())) {
            var visibleRows = rows.stream().filter(row -> row.getBottom() > listTop()
                    && row.getY() < listBottom()).toList();
            int index = -1;
            for (int i = 0; i < visibleRows.size(); i++)
                if (visibleRows.get(i).styleId().equals("mail-" + mail.id())) index = i;
            if (index >= 0) {
                int fromEnd = visibleRows.size() - 1 - index;
                if (index < 3 && index <= fromEnd) rowMotion.by(-MAIL_ROW_STEP);
                else if (fromEnd < 3) rowMotion.by(MAIL_ROW_STEP);
            }
            detailScroll = 0;
            detailMotion.jump(0);
            attachmentMotion.jump(0);
            revealDetails();
        }
        selectedId = mail.id();
        noticeId = "";
        selected = mail;
        actionMessage = null;
        if (!mail.read() || favoriteOnly && mail.treasureNew()) {
            ClientMailbox.action(mail.id(), favoriteOnly ? QuestNetwork.MailboxActionPayload.OPEN_TREASURE
                    : QuestNetwork.MailboxActionPayload.READ);
        }
        init();
    }

    private void runAction(String id, int action) {
        if (pending != null) {
            return;
        }
        actionMessage = null;
        if (action == QuestNetwork.MailboxActionPayload.CLAIM
                || action == QuestNetwork.MailboxActionPayload.CLAIM_ALL) {
            var messages = ClientMailbox.messages().stream()
                    .filter(mail -> mail.claimable() && (action == QuestNetwork.MailboxActionPayload.CLAIM_ALL
                            || mail.id().equals(id))).toList();
            boolean choice = messages.stream().flatMap(mail -> mail.attachments().stream())
                    .anyMatch(a -> !a.claimed() && a.type().equals("loot_choice"));
            if (choice) {
                Runnable choose = () -> minecraft.setScreen(QuestLootChoiceScreen.mail(this, messages));
                if (action == QuestNetwork.MailboxActionPayload.CLAIM_ALL)
                    minecraft.setScreen(new QuestConfirmScreen(this, text("claim_all_choices"), text("claim_all"), choose));
                else choose.run();
                return;
            }
            claimMailId = id;
        }
        pending = ClientMailbox.actionWithReceipt(id, action);
        init();
    }

    @Override
    public void tick() {
        updateActionMessageTimeout();
        if (++requestTicks >= 20) {
            requestTicks = 0;
            ClientMailbox.request();
        }
        if (mailboxRevision != ClientMailbox.revision()
                || authoringAllowed != (ClientEditMode.enabled()
                && minecraft.player != null && minecraft.player.hasPermissions(2))) {
            mailboxRevision = ClientMailbox.revision();
            if (!noticeId.isEmpty() && visibleMessages().stream().anyMatch(mail -> mail.id().equals(noticeId))) {
                String id = noticeId;
                noticeId = "";
                ClientMailbox.action(id, QuestNetwork.MailboxActionPayload.READ);
            }
            init();
        }
        if (pending != null && pending.isDone()) {
            var response = pending.join();
            String result = response.result();
            pending = null;
            if (!result.equals("starred") && favoriteButton != null && selected != null)
                favoriteButton.setStarred(selected.starred());
            if (result.equals("no_claimable")) {
                XeroTips.show(text("tips.all_claimed"));
            } else if (!result.equals("claimed") && !result.equals("deleted")
                    && !result.equals("read") && !result.equals("starred")) {
                actionMessage = result;
            }
            init();
            if (result.equals("choice_required")) {
                var messages = ClientMailbox.messages().stream().filter(mail -> mail.claimable()
                        && (claimMailId.isBlank() || mail.id().equals(claimMailId))).toList();
                if (!messages.isEmpty()) minecraft.setScreen(QuestLootChoiceScreen.mail(this, messages));
            }
            if (result.equals("claimed") && !response.items().isEmpty())
                minecraft.setScreen(new QuestRewardPopupScreen(this, text("title").getString(), response.items()));
        }
        if (favoriteButton != null)
            favoriteButton.active = pending == null && !favoriteButton.animationPlaying();
    }

    private void openMailMenu(double x, double y, MailboxRuntime.View mail) {
        if (!authoringAllowed || pending != null) return;
        contextMenu.clear();
        var entries = new java.util.ArrayList<java.util.Map.Entry<String, Runnable>>();
        if (mail != null) {
            if (multiSelect && !selectedMailIds.contains(mail.id())) selectedMailIds.add(mail.id());
            if (!multiSelect || selectedMailIds.size() <= 1) {
                entries.add(java.util.Map.entry("edit_mail", () -> minecraft.setScreen(new MailComposeScreen(this, mail, false))));
                entries.add(java.util.Map.entry("resend_mail", () -> minecraft.setScreen(new MailComposeScreen(this, mail, true))));
            }
            var ids = multiSelect ? java.util.Set.copyOf(selectedMailIds) : java.util.Set.of(mail.id());
            entries.add(java.util.Map.entry("delete_selected", () -> confirmAuthorDelete(ids)));
        } else if (multiSelect && !selectedMailIds.isEmpty()) {
            entries.add(java.util.Map.entry("delete_selected", () -> confirmAuthorDelete(selectedMailIds)));
        }
        entries.add(java.util.Map.entry("multi_select", () -> {
            multiSelect = !multiSelect;
            if (!multiSelect) selectedMailIds.clear();
            init();
        }));
        entries.add(java.util.Map.entry("select_all", this::selectAllMails));
        entries.add(java.util.Map.entry("invert_selection", this::invertMailSelection));
        int w = Math.min(180, width - 16);
        contextX = Math.clamp((int) x, 8, Math.max(8, width - w - 8));
        contextY = Math.clamp((int) y, 8, Math.max(8, height - entries.size() * 22 - 8));
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            contextMenu.add(new StyledButton(contextX, contextY + i * 22, w, 22, text(entry.getKey()), ignored -> {
                contextMenu.clear();
                if (authoringAllowed && ClientEditMode.enabled()) entry.getValue().run();
            }, styles, "mail-context-" + entry.getKey(), "context"));
        }
        rows.forEach(row -> row.setSelectionHighlighted(multiSelect
                ? selectedMailIds.contains(row.styleId().substring(5)) : row.styleId().equals("mail-" + selectedId)));
        rows.forEach(row -> row.multiSelection(multiSelect, selectedMailIds.contains(row.styleId().substring(5))));
    }

    private void selectAllMails() {
        if (!authoringAllowed) return;
        multiSelect = true;
        visibleMessages().forEach(mail -> selectedMailIds.add(mail.id()));
        init();
    }

    private void invertMailSelection() {
        if (!authoringAllowed) return;
        multiSelect = true;
        for (var mail : visibleMessages())
            if (!selectedMailIds.add(mail.id())) selectedMailIds.remove(mail.id());
        init();
    }

    private void confirmAuthorDelete(java.util.Set<String> ids) {
        if (!authoringAllowed || pending != null || ids.isEmpty()) return;
        var hashes = new java.util.LinkedHashMap<String, String>();
        ClientMailbox.messages().stream().filter(mail -> ids.contains(mail.id()))
                .forEach(mail -> hashes.put(mail.id(), MailboxRuntime.fingerprint(mail)));
        if (hashes.isEmpty()) return;
        minecraft.setScreen(new QuestConfirmScreen(this, text("delete_selected_question", hashes.size()),
                text("delete"), () -> {
            actionMessage = null;
            pending = ClientMailbox.deleteMany(hashes);
            contextMenu.clear();
        }));
    }

    private void updateActionMessageTimeout() {
        if (!java.util.Objects.equals(actionMessage, timedActionMessage)) {
            timedActionMessage = actionMessage;
            actionMessageStartedAt = System.nanoTime();
        }
        if (actionMessage != null && actionMessageStartedAt != 0
                && System.nanoTime() - actionMessageStartedAt >= 3_000_000_000L) {
            actionMessage = null;
            timedActionMessage = null;
            actionMessageStartedAt = 0;
        }
    }

    @Override protected boolean blurWorldBackground() { return !favoriteOnly; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        backdrop(graphics, JOURNAL_BACKDROP);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderMailboxBase(graphics, mouseX, mouseY, partialTick);
        if (confirmDelete || showHelp) renderInlinePopup(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMailboxBase(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (confirmDelete || showHelp) {
            rowMotion.freeze();
            detailMotion.freeze();
            attachmentMotion.freeze();
        }
        if (showHelp) helpMotion.advance();
        rowMotion.advance(); detailMotion.advance(); attachmentMotion.advance();
        moveContent();
        hoveredAttachment = confirmDelete || showHelp ? -1 : attachmentAt(mouseX, mouseY);
        renderMailbox(graphics, confirmDelete || showHelp ? -10000 : mouseX,
                confirmDelete || showHelp ? -10000 : mouseY, partialTick);
    }

    private void renderInlinePopup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (confirmDelete || showHelp) {
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                    net.minecraft.client.Minecraft.ON_OSX);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1000);
            try {
                if (confirmDelete) {
                    renderConfirmation(graphics);
                    confirmationCancel.render(graphics, mouseX, mouseY, partialTick);
                    confirmationConfirm.render(graphics, mouseX, mouseY, partialTick);
                } else {
                    renderHelp(graphics);
                    if (helpClose != null) helpClose.render(graphics, mouseX, mouseY, partialTick);
                }
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    @Override
    protected boolean isolatePopupSurface(Object key) {
        return "help".equals(key) || "delete_read".equals(key) || "delete_one".equals(key);
    }

    @Override
    protected void renderPopupBase(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderMailboxBase(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderPopupSurface(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderInlinePopup(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMailbox(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (favoriteOnly) {
            MailDecor.frame(graphics, 8, 8, width - 16, height - 16, false);
            MailDecor.centered(graphics, font, text("favorite_title").getString(), width / 2, 16, 1.5F, 0xFFF3D38B);
            MailDecor.centered(graphics, font, ClientMailbox.messages().stream().filter(MailboxRuntime.View::archived).count()
                    + "/" + MailboxRuntime.MAX_MESSAGES, width / 2, 32, 1, 0xFFE5CEA0);
            MailDecor.divider(graphics, 24, width - 24, 44, 0xFF626775);
            graphics.renderOutline(20, 50, split() - 40, 24, 0xFFA3A6AA);
            JournalNavButton.drawIcon(graphics, "mail_search", 25, 54, 15, 0xFFB6BAC3);
        } else {
            graphics.fill(0, 0, width, 42, JOURNAL_HEADER);
            JournalNavButton.drawIcon(graphics, "mail_envelope", 14, 8, 28, 0xFFF3D38B);
            graphics.drawString(font, text("title"), 50, 17, 0xFFF3D38B, false);
            int labelRight = treasureIconX() - 8;
            if (labelRight - font.width(text("favorite_title")) > authorToolsLeft() + (authoringAllowed ? 118 : 0))
                graphics.drawString(font, text("favorite_title"), labelRight - font.width(text("favorite_title")),
                        17, 0xFFF3D38B, false);
            graphics.drawString(font, text("count", ClientMailbox.messages().size(), MailboxRuntime.MAX_MESSAGES),
                    countX(), 17, 0xFFF0DCB6, false);
        }
        renderMailRows(graphics, mouseX, mouseY, partialTick);
        for (AbstractWidget widget : baseWidgets) {
            if (widget instanceof MailRowButton) continue;
            boolean visible = widget.visible;
            widget.visible = true;
            widget.render(graphics, mouseX, mouseY, partialTick);
            widget.visible = visible;
        }
        if (!favoriteOnly && ClientMailbox.messages().stream().anyMatch(MailboxRuntime.View::treasureUnseen)) {
            MailDecor.circle(graphics, treasureIconX() + 13, 13, 9, 0xFFEA4964);
            MailDecor.centered(graphics, font, "!", treasureIconX() + 17, 13, .8F, 0xFFFFE8D8);
        }

        if (visibleMessages().isEmpty()) {
            MailDecor.centered(graphics, font, text("empty").getString(), split() / 2, height / 2, 1, 0xFFB9C3D1);
        }
        if (selected != null) {
            renderFadingDetails(graphics, mouseX, mouseY);
        }
        if (maxRowScroll() > 0) {
            graphics.fill(split() - 5, listTop(), split() - 2, listBottom(), 0xFF383F50);
            graphics.fill(split() - 5, rowThumbTop(), split() - 2, rowThumbTop() + rowThumbHeight(), 0xFFE6DFD1);
        }
        if (actionMessage != null) {
            var layout = StatusToastRenderer.layout(font, text("action." + actionMessage),
                    width - 24, height - 84);
            StatusToastRenderer.drawAnimated(graphics, font, layout, width / 2,
                    height - 54 - layout.height(),
                    StatusToastRenderer.lifetimeProgress(actionMessageStartedAt, 3000));
        }
        renderAnimatedMenu(graphics, "compose", composeMenuOpen && !favoriteOnly ? "compose" : null,
                viewport(composeMenuLeft(), composeMenuTop(), composeMenuLeft() + 176,
                        composeMenuTop() + 48), () -> renderComposeMenu(graphics, mouseX, mouseY));
        renderAnimatedMenu(graphics, "context", contextMenu.isEmpty() ? null : contextMenu.getFirst(),
                contextMenu.isEmpty() ? viewport(0, 0, 0, 0) : viewport(contextX - 2, contextY - 2,
                        contextMenu.getFirst().getRight() + 2, contextMenu.getLast().getBottom() + 2), () -> {
                    graphics.fill(contextX - 2, contextY - 2, contextMenu.getFirst().getRight() + 2,
                            contextMenu.getLast().getBottom() + 2, 0xFF1C222B);
                    contextMenu.forEach(button -> button.render(graphics, mouseX, mouseY, partialTick));
                });
        if (hoveredAttachment >= 0 && selected != null && hoveredAttachment < visibleAttachments(selected).size()) {
            ItemStack stack = attachmentStack(visibleAttachments(selected).get(hoveredAttachment));
            if (!stack.isEmpty())
                graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    private void renderMailRows(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var visibleRows = rows.stream()
                .filter(row -> row.getBottom() > listTop() && row.getY() < listBottom()).toList();
        long now = System.nanoTime();
        if (!rowEntranceStarted && !visibleRows.isEmpty()) {
            rowEntranceStarted = true;
            rowEntranceStart = now;
            rowEntranceDuration = animationMillis();
            for (int index = 0; index < visibleRows.size(); index++)
                rowEntranceOrder.put(visibleRows.get(index).styleId(), index);
        }
        for (var row : visibleRows) row.active = !confirmDelete && !showHelp && rowOpacity(row, now) > .05F;
        Runnable draw = () -> {
            for (var row : visibleRows) {
                boolean visible = row.visible;
                row.visible = true;
                row.render(graphics, row.active ? mouseX : -10000, row.active ? mouseY : -10000, delta);
                row.visible = visible;
            }
        };
        if (visibleRows.stream().allMatch(row -> rowOpacity(row, now) >= 1)) {
            rowCompositor.close();
            draw.run();
            drawSelectedMailArrow(graphics, visibleRows, now);
            return;
        }
        // Fade complete rows as one image so item models and hard-coded text colors fade together.
        rowCompositor.capture(graphics, draw);
        for (var row : visibleRows) {
            rowCompositor.band(graphics, width, height, Math.max(listTop(), row.getY()),
                    Math.min(listBottom(), row.getBottom()), 0, rowOpacity(row, now));
        }
        drawSelectedMailArrow(graphics, visibleRows, now);
    }

    private void drawSelectedMailArrow(GuiGraphics graphics, List<MailRowButton> visibleRows, long now) {
        if (confirmDelete || showHelp) return;
        graphics.enableScissor(favoriteOnly ? 12 : 8, listTop(), split() - 5, listBottom());
        try {
            visibleRows.stream().filter(row -> row.styleId().equals("mail-" + selectedId))
                    .findFirst().ifPresent(row -> row.renderSelectionArrow(
                            graphics, now, rowOpacity(row, now), favoriteOnly ? 3 : 0));
        } finally {
            graphics.disableScissor();
        }
    }

    private float rowOpacity(MailRowButton row, long now) {
        Integer order = rowEntranceOrder.get(row.styleId());
        if (rowEntranceDuration <= 0 || animationMillis() == 0 || order == null) return 1;
        int staggerMillis = Math.min(60, Math.min(rowEntranceDuration / 3,
                360 / Math.max(1, rowEntranceOrder.size() - 1)));
        long delay = order * staggerMillis * 1_000_000L;
        float progress = Math.clamp((now - rowEntranceStart - delay)
                / (rowEntranceDuration * 1_000_000F), 0F, 1F);
        return progress * progress * (3 - 2 * progress);
    }

    private int headerActionsLeft() {
        return authorToolsLeft();
    }
    private int composeMenuLeft() { return Math.clamp(authorToolsLeft() - 150, 8, Math.max(8, width - 184)); }
    private int composeMenuTop() { return 38; }
    private void renderComposeMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = composeMenuLeft(), top = composeMenuTop(), menuWidth = 176, rowHeight = 24;
        graphics.fill(left, top, left + menuWidth, top + rowHeight * 2, 0xF01C2430);
        graphics.renderOutline(left, top, menuWidth, rowHeight * 2, 0xFFB78A4C);
        String[] labels = {"new_mail", "import_command_mail"};
        for (int i = 0; i < labels.length; i++) {
            int y = top + i * rowHeight;
            boolean hovered = mouseX >= left && mouseX < left + menuWidth
                    && mouseY >= y && mouseY < y + rowHeight;
            if (hovered) graphics.fill(left + 1, y + 1, left + menuWidth - 1, y + rowHeight,
                    0x664CB4D8);
            graphics.drawString(font, text(labels[i]), left + 9, y + 8, 0xFFE6E9ED, false);
        }
    }
    private void importMailFromClipboard() {
        composeMenuOpen = false;
        try {
            var imported = xtdpotato.xero_quest_journal.core.mailbox.MailClipboardCodec.parse(
                    minecraft.keyboardHandler.getClipboard());
            minecraft.setScreen(new MailComposeScreen(this, imported));
        } catch (IllegalArgumentException invalid) {
            actionMessage = "import_invalid";
        }
    }
    private int countX() { return width - 100 - font.width(text("count", ClientMailbox.messages().size(), MailboxRuntime.MAX_MESSAGES)); }
    private int treasureIconX() { return countX() - 40; }
    private int authorToolsLeft() { return Math.max(84, treasureIconX() - 154); }
    private int listTop() { return favoriteOnly ? 82 : 52; }
    private int listBottom() {
        return Math.max(listTop() + 1, favoriteOnly ? height - 20 : height - 42);
    }

    private void renderDetails(GuiGraphics graphics, MailboxRuntime.View mail, int mouseX, int mouseY) {
        Viewport viewport = detailViewport();
        int left = viewport.left();
        int right = viewport.right();
        int available = Math.max(40, right - left);
        int offset = (int) detailScroll;
        int bannerHeight = bannerHeight(mail);
        int contentShift = bannerHeight == 0 ? 0 : bannerHeight + 12;
        graphics.enableScissor(left, viewport.top(), right, viewport.bottom());
        if (bannerHeight > 0) {
            int bannerY = 54 - offset;
            MailBannerRenderer.draw(graphics, font, mail.banner(), !mail.targetTaskId().isBlank(),
                    MailBannerRenderer.remainingLabel(mail), left, bannerY, available, bannerHeight,
                    detailViewport().contains(mouseX, mouseY) && bannerContains(mail, mouseX, mouseY));
        }
        MailDecor.text(graphics, font, font.plainSubstrByWidth(mail.subject(), (int) (available / 1.35F)),
                left, 54 + contentShift - offset, 1.35F, 0xFFF4F6FA);
        MailDecor.divider(graphics, left, right, 72 + contentShift - offset, 0x66788CA6);
        graphics.drawString(font, font.plainSubstrByWidth(text("sender", mail.sender()).getString(), available),
                left, 82 + contentShift - offset, 0xFFB7C2D1, false);
        graphics.drawString(font, text("time", TIME.format(Instant.ofEpochMilli(mail.createdAt()))),
                left, 96 + contentShift - offset, 0xFF9BA9BB, false);
        MailDecor.divider(graphics, left, right, 110 + contentShift - offset, 0x445E718B);
        int bodyY = 122 + contentShift - offset;
        for (var line : font.split(QuestRichText.parse(mail.body(), net.minecraft.network.chat.Style.EMPTY),
                (int) (available / 1.2F))) {
            graphics.pose().pushPose();
            graphics.pose().translate(left, bodyY, 0);
            graphics.pose().scale(1.2F, 1.2F, 1);
            graphics.drawString(font, line, 0, 0, 0xFFE4E9F1, false);
            graphics.pose().popPose();
            bodyY += bodyLineHeight();
        }
        graphics.disableScissor();
        if (maxDetailScroll() > 0) {
            graphics.fill(right + 4, viewport.top(), right + 7, viewport.bottom(), 0xFF303741);
            graphics.fill(right + 4, detailThumbTop(), right + 7, detailThumbTop() + detailThumbHeight(), 0xFF9DB4D2);
        }
        var attachments = visibleAttachments(mail);
        if (!attachments.isEmpty()) {
            Viewport rewards = attachmentViewport();
            graphics.fill(rewards.left(), rewards.top() - 4, rewards.right(), rewards.top() - 3, 0x445E718B);
            graphics.enableScissor(rewards.left(), rewards.top(), rewards.right(), rewards.bottom());
            for (int index = 0; index < attachments.size(); index++) {
                MailboxRuntime.Attachment attachment = attachments.get(index);
                RewardCardRenderer.render(graphics, font,
                        QuestRewardsScreen.icon(attachment.type(), attachment.item()), attachment.count(),
                        attachment.quality(), attachment.background(),
                        rewards.left() + index * ATTACHMENT_COLUMN_STEP - attachmentMotion.pixels(),
                        attachmentTop(mail), 1, 1, attachment.claimed(), index == hoveredAttachment);
            }
            graphics.disableScissor();
        }
    }

    private void renderFadingDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        detailReveal.advance(System.nanoTime(), popupAnimationMillis());
        Viewport detail = detailViewport();
        if (detailReveal.settled()) {
            detailCompositor.close();
            renderDetails(graphics, selected, mouseX, mouseY);
        } else {
            detailCompositor.capture(graphics, () -> renderDetails(graphics, selected, mouseX, mouseY));
            detailCompositor.centered(graphics, width, height, detail.left(), detail.top(), width - 12,
                    !visibleAttachments(selected).isEmpty() ? height - 44 : detail.bottom(),
                    1, detailReveal.eased());
        }
    }

    private void revealDetails() {
        long now = System.nanoTime();
        detailReveal = new UiMotion(false, now);
        detailReveal.target(true, now, popupAnimationMillis());
    }

    private Viewport detailViewport() {
        return new Viewport(split() + 18, 50, width - 24,
                selected != null && !visibleAttachments(selected).isEmpty() ? height - 106 : height - 56);
    }
    private int bannerHeight(MailboxRuntime.View mail) {
        return mail.banner().isBlank() ? 0 : Math.clamp(
                (detailViewport().right() - detailViewport().left()) / 7, 48, 104);
    }
    private boolean bannerContains(MailboxRuntime.View mail, double x, double y) {
        int height = bannerHeight(mail);
        if (height == 0 || mail.targetTaskId().isBlank()) return false;
        Viewport viewport = detailViewport();
        int top = 54 - (int) detailScroll;
        return x >= viewport.left() && x < viewport.right() && y >= top && y < top + height;
    }
    private void openBannerTask(MailboxRuntime.View mail) {
        if (mail == null || mail.targetTaskId().isBlank()) return;
        if (QuestScreen.questById(mail.targetTaskId()) == null) {
            actionMessage = "task_missing";
            return;
        }
        minecraft.setScreen(QuestScreen.task(this, mail.targetTaskId()));
    }
    private Viewport attachmentViewport() {
        return new Viewport(split() + 18, height - 100, width - 24, height - 48);
    }
    private int bodyBottom(MailboxRuntime.View mail) {
        return 122 + (bannerHeight(mail) == 0 ? 0 : bannerHeight(mail) + 12)
                + font.split(QuestRichText.parse(mail.body(), net.minecraft.network.chat.Style.EMPTY),
                Math.max(40, (int) ((detailViewport().right() - detailViewport().left()) / 1.2F))).size() * bodyLineHeight();
    }
    private int bodyLineHeight() { return (int) Math.ceil(font.lineHeight * 1.2F) + 2; }
    private static List<MailboxRuntime.Attachment> visibleAttachments(MailboxRuntime.View mail) {
        return mail.attachments().stream().filter(attachment -> !attachment.hidden()).toList();
    }
    private int attachmentTop(MailboxRuntime.View mail) { return attachmentViewport().top() + 8; }
    private double maxAttachmentScroll() {
        if (selected == null) return 0;
        int count = visibleAttachments(selected).size();
        if (count == 0) return 0;
        Viewport viewport = attachmentViewport();
        return Math.max(0, (count - 1) * ATTACHMENT_COLUMN_STEP + ATTACHMENT_WIDTH
                - (viewport.right() - viewport.left()));
    }
    private int attachmentAt(double mouseX, double mouseY) {
        if (selected == null || visibleAttachments(selected).isEmpty()
                || !attachmentViewport().contains(mouseX, mouseY)) return -1;
        int localX = (int) mouseX - attachmentViewport().left() + attachmentMotion.pixels();
        int localY = (int) mouseY - attachmentTop(selected);
        if (localX < 0 || localX % ATTACHMENT_COLUMN_STEP >= ATTACHMENT_WIDTH
                || localY < 0 || localY >= ATTACHMENT_HEIGHT) return -1;
        int index = localX / ATTACHMENT_COLUMN_STEP;
        return index < visibleAttachments(selected).size() ? index : -1;
    }
    private void inspectAttachment(int index) {
        if (selected == null || index < 0 || index >= visibleAttachments(selected).size()) return;
        MailboxRuntime.Attachment attachment = visibleAttachments(selected).get(index);
        if (attachment.type().equals("loot_choice")) {
            minecraft.setScreen(LootTablePopupScreen.inspect(this,
                    new QuestDefinition.Reward("loot_choice", attachment.item(), 1)));
        } else if (attachment.type().equals("item") && !attachment.item().isBlank())
            minecraft.setScreen(new QuestRewardPopupScreen(this,
                    new QuestDefinition.Reward("item", attachment.item(), attachment.count(),
                            attachment.quality(), attachment.background())));
    }
    private ItemStack attachmentStack(MailboxRuntime.Attachment attachment) {
        if (!attachment.type().equals("item") || attachment.item().isBlank() || minecraft.player == null)
            return ItemStack.EMPTY;
        if (QuestItemStackValue.encoded(attachment.item()))
            return QuestItemStackValue.decode(attachment.item(), minecraft.player.registryAccess())
                    .map(stack -> {
                        ItemStack copy = stack.copy();
                        copy.setCount(Math.min(64, attachment.count()));
                        return copy;
                    }).orElse(ItemStack.EMPTY);
        ResourceLocation id = ResourceLocation.tryParse(attachment.item());
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id), Math.min(64, attachment.count()))
                : ItemStack.EMPTY;
    }
    private double maxDetailScroll() {
        if (selected == null) return 0;
        return Math.max(0, bodyBottom(selected) + 4 - detailViewport().bottom());
    }
    private int detailThumbHeight() {
        int h = detailViewport().bottom() - detailViewport().top();
        return Math.max(12, (int) (h * h / (h + maxDetailScroll())));
    }
    private int detailThumbTop() {
        Viewport viewport = detailViewport();
        return viewport.top() + (int) ((viewport.bottom() - viewport.top() - detailThumbHeight())
                * Math.clamp(detailScroll, 0, maxDetailScroll()) / Math.max(1, maxDetailScroll()));
    }
    private void scrollDetailsTo(double y) {
        Viewport viewport = detailViewport();
        detailScroll = Math.clamp((y - viewport.top() - detailThumbOffset) * maxDetailScroll()
                / Math.max(1, viewport.bottom() - viewport.top() - detailThumbHeight()), 0, maxDetailScroll());
        detailMotion.jump(detailScroll);
    }
    private double maxRowScroll() { return Math.max(0, rows.size() * MAIL_ROW_STEP - (listBottom() - listTop())); }
    private int rowThumbHeight() {
        int h = Math.max(1, listBottom() - listTop());
        return Math.max(12, (int) (h * h / (h + maxRowScroll())));
    }
    private int rowThumbTop() {
        return listTop() + (int) ((listBottom() - listTop() - rowThumbHeight())
                * Math.clamp(rowMotion.position(), 0, maxRowScroll()) / Math.max(1, maxRowScroll()));
    }
    private void scrollRowsTo(double y) {
        int trackTop = listTop();
        int trackBottom = listBottom();
        int thumbTravel = Math.max(1, trackBottom - trackTop - rowThumbHeight());
        double thumbTop = y - rowThumbOffset;
        if (y <= trackTop + 6) {
            thumbTop = trackTop;
        } else if (y >= trackBottom - 6) {
            thumbTop = trackTop + thumbTravel;
        }
        rowMotion.jump((thumbTop - trackTop) * maxRowScroll() / thumbTravel);
        moveContent();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (!contextMenu.isEmpty()) {
            for (var entry : List.copyOf(contextMenu)) if (entry.mouseClicked(x, y, button)) return true;
            contextMenu.clear();
            return true;
        }
        if (authoringAllowed && !confirmDelete && !showHelp && button == 1
                && x >= 12 && x < split() - 12 && y >= listTop() && y < listBottom()) {
            var mail = visibleMessages().stream().filter(value -> rows.stream().anyMatch(row ->
                    row.styleId().equals("mail-" + value.id()) && row.visible && row.isMouseOver(x, y)))
                    .findFirst().orElse(null);
            openMailMenu(x, y, mail);
            return true;
        }
        if (composeMenuOpen) {
            if (button == 0 && x >= composeMenuLeft() && x < composeMenuLeft() + 176
                    && y >= composeMenuTop() && y < composeMenuTop() + 48) {
                pressedComposeOption = (int) ((y - composeMenuTop()) / 24);
                return true;
            }
            composeMenuOpen = false;
            if (button == 0 && x >= authorToolsLeft() && x < authorToolsLeft() + 26 && y >= 8 && y < 36)
                return true;
        }
        if (showHelp) {
            if (!helpBounds().contains(x, y)) {
                showHelp = false;
                init();
                return true;
            }
            if (helpClose != null && helpClose.mouseClicked(x, y, button)) return true;
            if (button == 0 && maxHelpScroll() > 0 && x >= helpBounds().right() - 20
                    && x < helpBounds().right() - 6 && y >= helpViewport().top() && y < helpViewport().bottom()) {
                helpScrollbar = true;
                scrollHelpTo(y);
                return true;
            }
            if (button == 0 && helpViewport().contains(x, y)) {
                draggingHelp = true;
                helpDragOffset = y + helpMotion.position();
            }
            return true;
        }
        if (!confirmDelete && button == 0 && maxRowScroll() > 0 && x >= split() - 10
                && x < split() && y >= listTop() && y < listBottom()) {
            draggingRows = true;
            rowThumbOffset = y >= rowThumbTop() && y < rowThumbTop() + rowThumbHeight()
                    ? y - rowThumbTop() : rowThumbHeight() / 2.0;
            scrollRowsTo(y);
            return true;
        }
        Viewport viewport = detailViewport();
        if (!confirmDelete && button == 0 && maxDetailScroll() > 0 && x >= viewport.right()
                && x < viewport.right() + 10 && y >= viewport.top() && y < viewport.bottom()) {
            draggingDetails = true;
            detailThumbOffset = y >= detailThumbTop() && y < detailThumbTop() + detailThumbHeight()
                    ? y - detailThumbTop() : detailThumbHeight() / 2.0;
            scrollDetailsTo(y);
            return true;
        }
        if (!confirmDelete && button == 0 && (viewport.contains(x, y)
                || selected != null && !visibleAttachments(selected).isEmpty()
                && attachmentViewport().contains(x, y)
                || x >= 12 && x < split() - 12 && y >= listTop() && y < listBottom())) {
            dragPane = viewport.contains(x, y) ? 1
                    : selected != null && !visibleAttachments(selected).isEmpty()
                    && attachmentViewport().contains(x, y) ? 2 : 0;
            dragMoved = false; dragX = x; dragY = y;
            var motion = dragPane == 0 ? rowMotion : dragPane == 1 ? detailMotion : attachmentMotion;
            dragStart = motion.position();
            motion.begin();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (showHelp) {
            if (button == 0 && helpScrollbar) scrollHelpTo(y);
            else if (button == 0 && draggingHelp) {
                helpMotion.jump(helpDragOffset - y);
            }
            return true;
        }
        if (!confirmDelete && button == 0 && draggingRows) { scrollRowsTo(y); return true; }
        if (!confirmDelete && button == 0 && dragPane >= 0) {
            dragMoved |= Math.abs(y - dragY) >= 4 || Math.abs(x - dragX) >= 4;
            if (dragMoved) {
                (dragPane == 0 ? rowMotion : dragPane == 1 ? detailMotion : attachmentMotion)
                        .drag(dragStart + (dragPane == 2 ? dragX - x : dragY - y));
                moveContent();
            }
            return true;
        }
        if (!confirmDelete && button == 0 && draggingDetails) { scrollDetailsTo(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (!contextMenu.isEmpty()) {
            List.copyOf(contextMenu).forEach(entry -> entry.mouseReleased(x, y, button));
            return true;
        }
        if (button == 0 && pressedComposeOption >= 0) {
            int option = pressedComposeOption; pressedComposeOption = -1;
            if (composeMenuOpen && x >= composeMenuLeft() && x < composeMenuLeft() + 176
                    && y >= composeMenuTop() && y < composeMenuTop() + 48
                    && option == (int) ((y - composeMenuTop()) / 24)) {
                if (option == 0) { composeMenuOpen = false; minecraft.setScreen(new MailComposeScreen(this)); }
                else importMailFromClipboard();
            }
            return true;
        }
        if (showHelp) {
            if (helpClose != null && helpClose.mouseReleased(x, y, button)) return true;
            if (button == 0) { draggingHelp = false; helpScrollbar = false; }
            return true;
        }
        if (button == 0 && dragPane >= 0) {
            int pane = dragPane; dragPane = -1;
            (pane == 0 ? rowMotion : pane == 1 ? detailMotion : attachmentMotion).release(dragMoved);
            if (pane == 2 && !dragMoved && Math.abs(x - dragX) < 4
                    && Math.abs(y - dragY) < 4) {
                int index = attachmentAt(x, y);
                if (index >= 0) inspectAttachment(index);
            }
            if (pane == 1 && !dragMoved && selected != null
                    && Math.abs(x - dragX) < 4 && Math.abs(y - dragY) < 4
                    && bannerContains(selected, x, y)) {
                openBannerTask(selected);
                return true;
            }
            if (pane == 0 && !dragMoved && Math.abs(x - dragX) < 4 && y >= listTop() && y < listBottom()) {
                super.mouseClicked(dragX, dragY, button); super.mouseReleased(x, y, button);
            }
            return true;
        }
        if (button == 0) { draggingDetails = false; draggingRows = false; }
        return super.mouseReleased(x, y, button);
    }

    private void renderConfirmation(GuiGraphics graphics) {
        int dialogWidth = confirmationWidth();
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - confirmationHeight()) / 2;
        MailDecor.frame(graphics, dialogX, dialogY, dialogWidth, confirmationHeight(), true);
        String label = text(deleteId.isEmpty() ? "delete_read" : favoriteOnly ? "delete_treasure" : "delete").getString();
        MailDecor.centered(graphics, font, label, width / 2, dialogY + 12,
                Math.min(1.5F, (dialogWidth - 32F) / Math.max(1, font.width(label))), 0xFF505A70);
        MailDecor.divider(graphics, dialogX + 12, dialogX + dialogWidth - 12, dialogY + 30, 0xFFE0D6C6);
        MailDecor.divider(graphics, dialogX + 20, dialogX + dialogWidth - 20,
                dialogY + confirmationHeight() - 42, 0xFFE0D6C6);
        List<String> keys = favoriteOnly && !deleteId.isEmpty() ? List.of("delete_treasure_warning", "delete_treasure_question")
                : List.of(deleteId.isEmpty() ? "delete_question" : "delete_one_question",
                deleteId.isEmpty() ? "delete_warning" : "delete_one_warning");
        HalfScreenDialog.drawBody(graphics, font, keys.stream().map(key -> text(key)).toList(), width / 2,
                dialogY + 34, dialogWidth - 32, confirmationHeight() - 78, 0xFF505A70);
    }

    private int confirmationHeight() { return HalfScreenDialog.height(height); }
    private int confirmationWidth() { return HalfScreenDialog.width(width); }

    private Viewport helpBounds() {
        int dialogWidth = Math.min(620, width - 32);
        int dialogHeight = Math.min(430, height - 32);
        int left = (width - dialogWidth) / 2;
        int top = (height - dialogHeight) / 2;
        return new Viewport(left, top, left + dialogWidth, top + dialogHeight);
    }

    private Viewport helpViewport() {
        Viewport bounds = helpBounds();
        return new Viewport(bounds.left() + 24, bounds.top() + 52,
                bounds.right() - 24, bounds.bottom() - 22);
    }

    private List<net.minecraft.util.FormattedCharSequence> helpLines() {
        Viewport viewport = helpViewport();
        List<net.minecraft.util.FormattedCharSequence> result = new java.util.ArrayList<>();
        for (String paragraph : text("help_body").getString().split("\\n", -1)) {
            result.addAll(font.split(Component.literal(paragraph).withStyle(style ->
                            style.withColor(paragraph.startsWith("【") || paragraph.startsWith("[") ? 0xF3D38B : 0xFFFFFF)),
                    Math.max(40, (int) ((viewport.right() - viewport.left() - 8) / 1.25F))));
        }
        return result;
    }

    private double maxHelpScroll() {
        Viewport viewport = helpViewport();
        return Math.max(0, helpLines().size() * helpLineHeight() - (viewport.bottom() - viewport.top()));
    }
    private int helpLineHeight() { return Math.round(font.lineHeight * 1.25F) + 4; }

    private void renderHelp(GuiGraphics graphics) {
        Viewport bounds = helpBounds();
        Viewport viewport = helpViewport();
        MailDecor.frame(graphics, bounds.left(), bounds.top(), bounds.right() - bounds.left(),
                bounds.bottom() - bounds.top(), false);
        MailDecor.centered(graphics, font, text("help_title").getString(), width / 2, bounds.top() + 16, 1.7F, 0xFFF3D38B);
        MailDecor.divider(graphics, bounds.left() + 20, bounds.right() - 20, bounds.top() + 42, 0xFF636978);
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        int y = viewport.top() - (int) helpMotion.position();
        for (var line : helpLines()) {
            graphics.pose().pushPose();
            graphics.pose().translate(viewport.left(), y, 0);
            graphics.pose().scale(1.25F, 1.25F, 1);
            graphics.drawString(font, line, 0, 0, 0xFFFFFFFF, false);
            graphics.pose().popPose();
            y += helpLineHeight();
        }
        graphics.disableScissor();
        if (maxHelpScroll() > 0) {
            int trackX = bounds.right() - 14;
            int trackTop = viewport.top();
            int trackHeight = viewport.bottom() - viewport.top();
            int thumbHeight = Math.max(18, (int) (trackHeight * trackHeight
                    / (trackHeight + maxHelpScroll())));
            int thumbTop = trackTop + (int) ((trackHeight - thumbHeight)
                    * helpMotion.position() / Math.max(1, maxHelpScroll()));
            graphics.fill(trackX, trackTop, trackX + 3, viewport.bottom(), 0x22303848);
            graphics.fill(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, 0xFF9CA7B5);
        }
    }

    private void scrollHelpTo(double y) {
        Viewport view = helpViewport();
        helpMotion.jump((y - view.top()) / Math.max(1, view.bottom() - view.top()) * maxHelpScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (confirmDelete) return true;
        if (showHelp) {
            if (helpViewport().contains(mouseX, mouseY)) helpMotion.wheel(vertical * 32);
            return true;
        }
        if (mouseX >= 12 && mouseX < split() - 12 && mouseY >= listTop() && mouseY < listBottom()) {
            rowMotion.wheel(vertical * 36);
            return true;
        }
        if (selected != null && !visibleAttachments(selected).isEmpty()
                && attachmentViewport().contains(mouseX, mouseY)) {
            attachmentMotion.wheel((vertical != 0 ? vertical : horizontal) * 36);
            return true;
        }
        if (detailViewport().contains(mouseX, mouseY)) {
            detailMotion.wheel(vertical * 36);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    private void moveContent() {
        scroll = rowMotion.position(); detailScroll = detailMotion.position();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            row.setY(listTop() + i * MAIL_ROW_STEP - rowMotion.pixels());
            row.visible = !confirmDelete && !showHelp && row.getBottom() > listTop() && row.getY() < listBottom();
        }
    }

    @Override public void mouseMoved(double x, double y) {
        super.mouseMoved(x, y);
        hoveredAttachment = confirmDelete || showHelp ? -1 : attachmentAt(x, y);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if (!contextMenu.isEmpty()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) contextMenu.clear();
            return true;
        }
        if (authoringAllowed && !confirmDelete && !showHelp && !(getFocused() instanceof EditBox)) {
            if (key == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                selectAllMails();
                return true;
            }
            if (multiSelect && key == GLFW.GLFW_KEY_DELETE && !selectedMailIds.isEmpty()) {
                confirmAuthorDelete(selectedMailIds);
                return true;
            }
        }
        if (composeMenuOpen && key == GLFW.GLFW_KEY_ESCAPE) {
            composeMenuOpen = false;
            return true;
        }
        if (favoriteOnly && getFocused() instanceof EditBox && key != GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(key, scan, modifiers);
        if (confirmDelete && key == GLFW.GLFW_KEY_ESCAPE) {
            confirmDelete = false;
            deleteId = "";
            init();
            return true;
        }
        if (showHelp && key == GLFW.GLFW_KEY_ESCAPE) {
            showHelp = false;
            init();
            return true;
        }
        if (!confirmDelete && !showHelp && !(getFocused() instanceof EditBox)
                && (key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_S)) {
            List<MailboxRuntime.View> messages = visibleMessages();
            int current = selected == null ? -1
                    : messages.indexOf(messages.stream().filter(mail -> mail.id().equals(selected.id())).findFirst().orElse(null));
            int next = key == GLFW.GLFW_KEY_W ? current - 1 : current + 1;
            if (current < 0) next = key == GLFW.GLFW_KEY_W ? -1 : 0;
            if (next >= 0 && next < messages.size()) {
                select(messages.get(next));
                return true;
            }
            return true;
        }
        if (QuestKeyMappings.OPEN_MAILBOX.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        detailCompositor.close();
        rowCompositor.close();
        super.removed();
    }

    @Override
    public void onClose() {
        if (!contextMenu.isEmpty()) { contextMenu.clear(); return; }
        if (multiSelect) { multiSelect = false; selectedMailIds.clear(); init(); return; }
        closeTo(parent);
    }

    @Override protected int animationHeaderBottom() { return 42; }
    @Override protected int animationFooterTop() { return height - 42; }
    @Override protected Object popupKey() {
        if (confirmDelete) return deleteId.isEmpty() ? "delete_read" : "delete_one";
        return showHelp ? "help" : null;
    }
    @Override protected int popupScrimColor() { return 0x80000000; }
    @Override protected Viewport popupBounds() {
        if (favoriteOnly && !confirmDelete && !showHelp) return new Viewport(0, 0, width, height);
        if (showHelp) {
            Viewport help = helpBounds();
            return new Viewport(Math.max(0, help.left() - 4), Math.max(0, help.top() - 4),
                    Math.min(width, help.right() + 4), Math.min(height, help.bottom() + 4));
        }
        if (!confirmDelete) return favoriteOnly ? new Viewport(8, 8, width - 8, height - 8) : super.popupBounds();
        int dialogWidth = confirmationWidth();
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - confirmationHeight()) / 2;
        return new Viewport(Math.max(0, dialogX - 14), Math.max(0, dialogY - 14),
                Math.min(width, dialogX + dialogWidth + 14),
                Math.min(height, dialogY + confirmationHeight() + 14));
    }

    private int split() {
        return Math.max(180, Math.min(width - 210, width * 47 / 100));
    }

    private void clampScroll(int count) {
        double max = Math.max(0, count * MAIL_ROW_STEP - Math.max(1, listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override protected boolean isModalScreen() {
        return favoriteOnly && !confirmDelete && !showHelp && !popupTransitionActive();
    }
    @Override protected void renderModalBackground(GuiGraphics graphics, float delta) {
        if (parent != null) parent.render(graphics, -10000, -10000, delta);
    }
}
