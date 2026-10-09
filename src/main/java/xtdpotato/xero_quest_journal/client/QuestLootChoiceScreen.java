package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Selects one frozen candidate for every choice-based loot reward. */
public final class QuestLootChoiceScreen extends AnimatedQuestScreen {
    private static final int HEADER = 58;
    private static final int ROW_HEIGHT = 30;
    private static final int GROUP_GAP = 18;

    private final Screen parent;
    private final String questId;
    private final String questTitle;
    private final boolean showReceipt;
    private final String errorPrefix;
    private final List<Group> groups;
    private final List<QuestDefinition.Reward> rewards;
    private final java.util.function.BiFunction<Map<Integer, Integer>, Boolean,
            java.util.concurrent.CompletableFuture<xtdpotato.xero_quest_journal.core.network.QuestNetwork.MailboxActionResultPayload>> claimAction;
    private final Map<Integer, Integer> selected = new LinkedHashMap<>();
    private final List<Placement> placements = new ArrayList<>();
    private final SmoothScroll motion = new SmoothScroll();
    private final java.util.Set<AbstractWidget> choiceWidgets = new java.util.HashSet<>();
    private ButtonStyleSheet styles;
    private java.util.concurrent.CompletableFuture<xtdpotato.xero_quest_journal.core.network.QuestNetwork.MailboxActionResultPayload> pending;
    private String error;
    private boolean allowDrop;
    private int scroll;
    private boolean listDragging;
    private boolean listMoved;
    private double dragStartY;
    private double dragStartScroll;

    private record Group(int rewardIndex, List<QuestDefinition.Reward> choices, String title) {}
    private record Placement(StyledButton button, int y) {}

    public QuestLootChoiceScreen(Screen parent, String questId, String questTitle,
                                 List<QuestDefinition.Reward> rewards) {
        this(parent, questId, questTitle, rewards, true);
    }

    public QuestLootChoiceScreen(Screen parent, String questId, String questTitle,
                                 List<QuestDefinition.Reward> rewards, boolean showReceipt) {
        this(parent, questId, questTitle, rewards, showReceipt, null);
    }

    private QuestLootChoiceScreen(Screen parent, String questId, String questTitle,
            List<QuestDefinition.Reward> rewards, boolean showReceipt,
            java.util.function.BiFunction<Map<Integer, Integer>, Boolean,
                    java.util.concurrent.CompletableFuture<xtdpotato.xero_quest_journal.core.network.QuestNetwork.MailboxActionResultPayload>> claimAction) {
        super(text("loot.choose"));
        this.parent = parent;
        this.questId = questId == null ? "" : questId;
        this.questTitle = questTitle == null ? "" : questTitle;
        this.showReceipt = showReceipt;
        this.errorPrefix = claimAction == null ? "action." : "mailbox.action.";
        this.rewards = rewards == null ? List.of() : List.copyOf(rewards);
        this.claimAction = claimAction != null ? claimAction : (selections, drop) ->
                ClientQuestProgress.claimChoices(this.questId, selections, drop).thenApply(result ->
                        new xtdpotato.xero_quest_journal.core.network.QuestNetwork.MailboxActionResultPayload(0,
                                result, result.equals("claimed")
                                ? QuestRewardPopupScreen.receiptRewards(selectedRewards()) : List.of()));
        List<Group> values = new ArrayList<>();
        if (rewards != null) {
            for (int index = 0; index < rewards.size(); index++) {
                QuestDefinition.Reward reward = rewards.get(index);
                if (reward != null && reward.type().equals("loot_choice")) {
                    List<QuestDefinition.Reward> choices = QuestRewards.choices(reward);
                    if (!choices.isEmpty()) values.add(new Group(index, choices,
                            QuestRewards.choiceTitle(reward)));
                }
            }
        }
        groups = List.copyOf(values);
    }

    public static QuestLootChoiceScreen mail(Screen parent,
            List<xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime.View> messages) {
        var rewards = new ArrayList<QuestDefinition.Reward>();
        record AttachmentIndex(String mailId, int index) {}
        var indices = new LinkedHashMap<Integer, AttachmentIndex>();
        for (var mail : messages) for (int index = 0; index < mail.attachments().size(); index++) {
            var attachment = mail.attachments().get(index);
            if (attachment.claimed() || attachment.count() <= 0) continue;
            indices.put(rewards.size(), new AttachmentIndex(mail.id(), index));
            rewards.add(new QuestDefinition.Reward(attachment.type(), attachment.item(), attachment.count()));
        }
        return new QuestLootChoiceScreen(parent, "", messages.size() == 1 ? messages.getFirst().subject()
                : Component.translatable("screen.xero_quest_journal.mailbox.claim_all").getString(), rewards, true,
                (selected, drop) -> {
                    var mapped = new LinkedHashMap<String, Map<Integer, Integer>>();
                    selected.forEach((flat, choice) -> {
                        var index = indices.get(flat);
                        mapped.computeIfAbsent(index.mailId(), ignored -> new LinkedHashMap<>()).put(index.index(), choice);
                    });
                    return ClientMailbox.claimChoices(messages.stream().map(mail ->
                            new xtdpotato.xero_quest_journal.core.network.QuestNetwork.MailboxClaimEntry(
                                    mail.id(), xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime.fingerprint(mail),
                                    mapped.getOrDefault(mail.id(), Map.of()))).toList(), drop);
                });
    }

    public String questId() {
        return questId;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal." + key, args);
    }

    private int panelWidth() {
        return Math.min(560, width - 24);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int viewportTop() {
        return 54;
    }

    private int viewportBottom() {
        return Math.max(viewportTop() + 1, height - 44);
    }

    private int contentHeight() {
        int value = HEADER;
        for (Group group : groups) value += 22 + group.choices().size() * ROW_HEIGHT + GROUP_GAP;
        return value;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewportBottom() - viewportTop()));
    }

    private void select(int rewardIndex, int choiceIndex) {
        if (pending != null) return;
        selected.put(rewardIndex, choiceIndex);
        error = null;
        init();
    }

    @Override
    protected Viewport widgetViewport(AbstractWidget widget) {
        return choiceWidgets.contains(widget) ? viewport(left() + 8, viewportTop(), left() + panelWidth() - 8,
                viewportBottom()) : null;
    }

    @Override
    protected void init() {
        clearWidgets();
        placements.clear();
        choiceWidgets.clear();
        styles = ButtonStyleSheet.load();
        motion.limit(maxScroll());
        motion.jump(scroll);
        scroll = motion.pixels();
        int x = left();
        int y = HEADER;
        for (Group group : groups) {
            y += 22;
            for (int choiceIndex = 0; choiceIndex < group.choices().size(); choiceIndex++) {
                int index = choiceIndex;
                QuestDefinition.Reward reward = group.choices().get(index);
                boolean highlighted = selected.getOrDefault(group.rewardIndex(), -1) == index;
                StyledButton button = new StyledButton(x + 18, y - scroll, panelWidth() - 36, 24,
                        rewardLabel(reward), ignored -> select(group.rewardIndex(), index),
                        styles, "loot-choice-option", "toolbar-button");
                button.leadingIcon(QuestRewardsScreen.icon(reward.type(), reward.item()));
                button.defaultCss(".toolbar-button:selected { background-color:#65705c; border-color:#f3d48b; border-width:2px; }");
                button.setSelectionHighlighted(highlighted);
                addRenderableWidget(button);
                choiceWidgets.add(button);
                placements.add(new Placement(button, y));
                y += ROW_HEIGHT;
            }
            y += GROUP_GAP;
        }
        moveChoices();
        StyledButton cancel = new StyledButton(x, height - 32, (panelWidth() - 6) / 2, 22,
                text("cancel"), ignored -> onClose(), styles, "loot-choice-cancel");
        cancel.active = pending == null;
        addRenderableWidget(cancel);
        StyledButton claim = new StyledButton(x + (panelWidth() + 6) / 2, height - 32,
                (panelWidth() - 6) / 2, 22, text(pending == null ? "loot.claim" : "saving"),
                ignored -> claim(), styles, "loot-choice-claim");
        claim.active = pending == null && selected.size() == groups.size();
        addRenderableWidget(claim);
    }

    private Component rewardLabel(QuestDefinition.Reward reward) {
        if (reward.type().equals("item")) {
            ItemStack stack = stack(reward);
            if (!stack.isEmpty()) return stack.getHoverName();
        }
        return text("reward_type." + reward.type());
    }

    private ItemStack stack(QuestDefinition.Reward reward) {
        if (minecraft == null || minecraft.player == null || reward == null
                || !reward.type().equals("item") || reward.item().isBlank()) return ItemStack.EMPTY;
        if (QuestItemStackValue.encoded(reward.item())) {
            return QuestItemStackValue.decode(reward.item(), minecraft.player.registryAccess())
                    .orElse(ItemStack.EMPTY);
        }
        ResourceLocation id = ResourceLocation.tryParse(reward.item());
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id), Math.min(64, reward.count()))
                : ItemStack.EMPTY;
    }

    private List<QuestDefinition.Reward> selectedRewards() {
        List<QuestDefinition.Reward> result = new ArrayList<>();
        for (int index = 0; index < rewards.size(); index++) {
            QuestDefinition.Reward reward = rewards.get(index);
            if (reward.type().equals("loot_choice")) {
                List<QuestDefinition.Reward> choices = QuestRewards.choices(reward);
                Integer choice = selected.get(index);
                if (choice == null || choice < 0 || choice >= choices.size()) return List.of();
                result.add(choices.get(choice));
            } else {
                result.add(reward);
            }
        }
        return List.copyOf(result);
    }

    private void claim() {
        if (pending != null || selected.size() != groups.size()) return;
        pending = claimAction.apply(Map.copyOf(selected), allowDrop);
        error = null;
        init();
    }

    private void moveChoices() {
        int next = motion.pixels();
        for (Placement placement : placements) placement.button().setY(placement.y() - next);
        scroll = next;
    }

    @Override
    public void tick() {
        super.tick();
        if (pending == null || !pending.isDone()) {
            motion.advance();
            moveChoices();
            return;
        }
        String result;
        List<QuestDefinition.Reward> received = List.of();
        try {
            var response = pending.join();
            result = response.result();
            received = response.items();
        } catch (Exception failure) {
            result = "save";
        }
        pending = null;
        if (result.equals("claimed")) {
            xtdpotato.xero_quest_journal.core.network.QuestClientInbox.discardAutomaticClaims(questId);
            minecraft.setScreen(showReceipt && !received.isEmpty()
                    ? new QuestRewardPopupScreen(parent, questTitle, received) : parent);
        } else if (result.equals("inventory_full") && !allowDrop) {
            minecraft.setScreen(new QuestConfirmScreen(this, text("rewards.overflow"), text("claim"), () -> {
                allowDrop = true;
                claim();
            }));
        } else {
            allowDrop = false;
            error = errorPrefix + result;
            init();
        }
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderContents(g, mouseX, mouseY, delta);
        int x = left();
        String customTitle = groups.stream().map(Group::title).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
        g.drawCenteredString(font, customTitle.isBlank() ? text("loot.choose") : Component.literal(customTitle),
                width / 2, 12, 0xFFFFFFFF);
        if (!questTitle.isBlank()) {
            g.drawCenteredString(font, questTitle, width / 2, 28, 0xFFE7D19A);
        }
        g.enableScissor(x + 8, viewportTop(), x + panelWidth() - 8, viewportBottom());
        int y = HEADER - scroll;
        for (Group group : groups) {
            g.drawString(font, text("loot.group", group.rewardIndex() + 1), x + 18, y,
                    0xFFF5D99A, false);
            y += 22 + group.choices().size() * ROW_HEIGHT + GROUP_GAP;
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = viewportBottom() - viewportTop();
            int thumb = Math.max(14, track * track / Math.max(track, contentHeight()));
            int top = viewportTop() + (track - thumb) * scroll / maxScroll();
            g.fill(x + panelWidth() + 2, viewportTop(), x + panelWidth() + 5, viewportBottom(), 0xFF303741);
            g.fill(x + panelWidth() + 2, top, x + panelWidth() + 5, top + thumb, ButtonStyle.FRAME_COLOR);
        }
        if (error != null) {
            Component message = text(error);
            g.drawString(font, font.plainSubstrByWidth(message.getString(), panelWidth()),
                    x, height - 47, 0xFFFF7777);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (viewport(left() + 8, viewportTop(), left() + panelWidth() - 8, viewportBottom()).contains(x, y)
                && pending == null) {
            motion.wheel(vertical * ROW_HEIGHT);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && pending == null && x >= left() && x <= left() + panelWidth()
                && y >= viewportTop() && y <= viewportBottom()) {
            if (getChildAt(x, y).isPresent()) return super.mouseClicked(x, y, button);
            listDragging = true;
            listMoved = false;
            dragStartY = y;
            dragStartScroll = motion.position();
            motion.freeze();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && listDragging) {
            listMoved |= Math.abs(y - dragStartY) >= 3;
            if (listMoved) {
                motion.jump(dragStartScroll + dragStartY - y);
                moveChoices();
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && listDragging) {
            listDragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float tick) {
        backdrop(g, 0xF01C2026);
    }

    @Override
    public void onClose() {
        if (pending == null) closeTo(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
