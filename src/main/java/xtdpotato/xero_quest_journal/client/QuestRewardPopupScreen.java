package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;

/** A receipt opens only after a claim; item inspection is a separate child modal. */
public final class QuestRewardPopupScreen extends AnimatedQuestScreen {
    private static final int CARD_WIDTH = 64;
    private static final int CARD_GAP = 16;
    private static final int DETAIL_TOP = 94;
    private static final int SOURCE_GAP = 40;
    private final Screen parent;
    private final String questTitle;
    private final List<QuestDefinition.Reward> rewards;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final QuestDefinition.Reward selectedReward;
    private List<Component> tooltip = List.of();
    private List<Source> sources = List.of();
    private ItemStack inspectedStack = ItemStack.EMPTY;
    private StyledButton recipeButton;
    private String recipeViewer = "";
    private boolean recipeOpenFailed;
    private int detailScroll;
    private int detailWidth = 194;
    private int detailHeight;
    private List<FormattedCharSequence> tooltipLines = List.of();
    private List<SourceRow> sourceRows = List.of();
    private record Source(Component label, String questId) {}
    private record SourceRow(List<FormattedCharSequence> lines, int height, String questId) {}
    private boolean detailsLoaded;
    private double scroll;
    private double targetScroll;
    private boolean dragging;
    private boolean scrollbarDragging;
    private int pressedReward = -1;
    private double pressX;
    private double pressScroll;
    private long lastRenderNanos;

    public QuestRewardPopupScreen(Screen parent, String questTitle, List<QuestDefinition.Reward> rewards) {
        this(parent, questTitle, rewards, null);
    }

    private QuestRewardPopupScreen(Screen parent, String questTitle, List<QuestDefinition.Reward> rewards,
                                   QuestDefinition.Reward selectedReward) {
        super(Component.translatable("screen.xero_quest_journal.rewards."
                + (selectedReward == null ? "received" : "source")));
        this.parent = parent;
        this.questTitle = questTitle == null ? "" : questTitle;
        this.rewards = List.copyOf(rewards == null ? List.of() : rewards);
        this.selectedReward = selectedReward;
    }

    public QuestRewardPopupScreen(Screen parent, QuestDefinition.Reward reward) {
        this(parent, "", List.of(), java.util.Objects.requireNonNull(reward));
    }

    static List<QuestDefinition.Reward> receiptRewards(List<QuestDefinition.Reward> rewards) {
        return rewards.stream().filter(reward -> switch (reward.type()) {
            case "item", "experience", "levels", "effect" -> true;
            default -> false;
        }).toList();
    }

    private int panelWidth() {
        if (selectedReward == null) {
            return Math.min(480, width - 32);
        }
        return detailWidth;
    }
    private int panelHeight() {
        return Math.min(selectedReward == null ? 228 : DETAIL_TOP + detailContentHeight() + 12, height - 32);
    }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int viewportLeft() { return left() + 30; }
    private int viewportRight() { return left() + panelWidth() - 30; }
    private int viewportWidth() { return Math.max(1, viewportRight() - viewportLeft()); }
    private int contentWidth() { return rewards.size() * CARD_WIDTH + Math.max(0, rewards.size() - 1) * CARD_GAP; }
    private double maxScroll() { return Math.max(0, contentWidth() - viewportWidth()); }
    private double contentLeft() {
        return viewportLeft() + Math.max(0, (viewportWidth() - contentWidth()) / 2.0) - scroll;
    }
    private int cardTop() { return top() + 74; }
    private int trackY() { return top() + panelHeight() - 44; }
    private int thumbWidth() {
        return Math.max(16, (int) (viewportWidth() * viewportWidth() / (double) Math.max(1, contentWidth())));
    }
    private int rewardAt(double x, double y) {
        int cardHeight = RewardCardRenderer.scaledHeight(CARD_WIDTH / (float) RewardCardRenderer.WIDTH);
        if (x < viewportLeft() || x >= viewportRight() || y < cardTop() || y >= cardTop() + cardHeight) return -1;
        double local = x - contentLeft();
        int index = (int) Math.floor(local / (CARD_WIDTH + CARD_GAP));
        return index >= 0 && index < rewards.size() && local % (CARD_WIDTH + CARD_GAP) < CARD_WIDTH ? index : -1;
    }

    private void loadDetails(QuestDefinition.Reward reward) {
        tooltip = List.of();
        sources = List.of();
        detailsLoaded = true;
        ItemStack stack = stack(reward);
        inspectedStack = stack;
        recipeViewer = RecipeViewerBridge.availableViewer();
        if (!stack.isEmpty()) {
            tooltip = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level),
                    minecraft == null ? null : minecraft.player, TooltipFlag.NORMAL);
            sources = findSources(stack);
        }
    }

    private ItemStack stack(QuestDefinition.Reward reward) {
        if (reward == null || !reward.type().equals("item") || reward.item().isBlank()
                || minecraft == null) return ItemStack.EMPTY;
        if (QuestItemStackValue.encoded(reward.item())) {
            return minecraft.player == null ? ItemStack.EMPTY : QuestItemStackValue
                    .decode(reward.item(), minecraft.player.registryAccess()).orElse(ItemStack.EMPTY);
        }
        ResourceLocation id = ResourceLocation.tryParse(reward.item());
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id), Math.min(64, reward.count()))
                : ItemStack.EMPTY;
    }

    private List<Source> findSources(ItemStack stack) {
        if (minecraft == null || minecraft.level == null || stack.isEmpty()) return List.of();
        List<Source> matches = new ArrayList<>();
        var workstations = new LinkedHashSet<String>();
        for (var holder : minecraft.level.getRecipeManager().getRecipes()) {
            try {
                ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
                if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, stack)) {
                    Component name = holder.value().getToastSymbol().getHoverName();
                    if (workstations.add(name.getString())) {
                        matches.add(new Source(text("source_workstation", name), ""));
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (ClientPreferences.get().questRewardSources) {
            for (var quest : ClientQuestDefinitions.all()) {
                var view = ClientQuestProgress.view(quest.id());
                if (view != null && view.completed()) continue;
                if (quest.rewards().stream().anyMatch(reward -> {
                    ItemStack result = stack(reward);
                    return !result.isEmpty() && ItemStack.isSameItemSameComponents(result, stack);
                })) {
                    matches.add(new Source(text("source_quest", QuestText.title(quest)), quest.id()));
                }
            }
        }
        return List.copyOf(matches);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.rewards." + key, args);
    }

    private int tooltipHeight() {
        return tooltipLines.size() * (font.lineHeight + 2);
    }

    private int detailContentHeight() {
        return detailHeight;
    }

    private void layoutDetails() {
        int desired = 194;
        for (Component line : tooltip) desired = Math.max(desired, font.width(line) + 28);
        for (Source source : sources) desired = Math.max(desired, font.width(source.label()) + 42);
        detailWidth = Math.max(64, Math.min(Math.min(250, desired), width - 32));
        var lines = new ArrayList<FormattedCharSequence>();
        int first = !tooltip.isEmpty() && font.split(tooltip.getFirst(), Math.max(1, detailWidth - 76)).size() > 4 ? 0 : 1;
        for (int i = first; i < tooltip.size(); i++) lines.addAll(font.split(tooltip.get(i), detailWidth - 32));
        tooltipLines = List.copyOf(lines);
        var rows = new ArrayList<SourceRow>();
        int result = tooltipHeight() + SOURCE_GAP;
        if (recipeOpenFailed) result += font.split(text("source_unavailable"), panelWidth() - 32).size() * 11 + 6;
        for (Source source : sources) {
            var wrapped = font.split(source.label(), detailWidth - (source.questId().isEmpty() ? 44 : 54));
            int height = Math.max(22, wrapped.size() * 11 + 8);
            rows.add(new SourceRow(wrapped, height, source.questId()));
            result += height + 4;
        }
        if (sources.isEmpty()) result += Math.max(22, font.split(text("no_source"), detailWidth - 32).size() * 11 + 10);
        sourceRows = List.copyOf(rows);
        detailHeight = result;
    }

    private Viewport detailViewport() {
        return viewport(left() + 10, top() + DETAIL_TOP, left() + panelWidth() - 10, top() + panelHeight() - 10);
    }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return widget == recipeButton ? detailViewport() : null;
    }

    private void positionRecipeButton() {
        if (recipeButton == null) return;
        recipeButton.setY(top() + DETAIL_TOP + tooltipHeight() + 15 - detailScroll);
        Viewport viewport = detailViewport();
        recipeButton.clipTo(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
    }

    @Override
    protected void init() {
        clearWidgets();
        recipeButton = null;
        if (selectedReward != null && !detailsLoaded) loadDetails(selectedReward);
        if (selectedReward != null) layoutDetails();
        detailScroll = Math.clamp(detailScroll, 0,
                Math.max(0, detailContentHeight() - Math.max(1, panelHeight() - DETAIL_TOP - 10)));
        int x = left(), y = top(), w = panelWidth();
        targetScroll = Math.clamp(targetScroll, 0, maxScroll());
        scroll = Math.clamp(scroll, 0, maxScroll());
        pressedReward = -1;
        dragging = scrollbarDragging = false;
        if (selectedReward != null) {
            addRenderableWidget(new StyledButton(x + w - 22, y + 6, 16, 16, Component.literal("\u00d7"),
                    ignored -> onClose(), styles, "reward-popup-close", "toolbar-button"));
            recipeButton = addRenderableWidget(new StyledButton(x + 16, y, w - 32, 22,
                    recipeViewer.isEmpty() ? text("source_no_viewer") : text("source_open", recipeViewer),
                    ignored -> {
                        recipeOpenFailed = !RecipeViewerBridge.openRecipes(inspectedStack);
                        if (recipeOpenFailed) init();
                    }, styles, "reward-source-recipes", "toolbar-button") {
                @Override protected void renderContent(GuiGraphics graphics, ButtonStyle style, int color) {
                    graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), getWidth() - 30),
                            getX() + 8, getY() + (height - font.lineHeight) / 2, color, false);
                    if (active) graphics.drawString(font, "\u203a", getRight() - 13,
                            getY() + (height - font.lineHeight) / 2, color, false);
                }
            });
            recipeButton.active = !recipeViewer.isEmpty() && !inspectedStack.isEmpty();
            recipeButton.setTooltip(Tooltip.create(recipeViewer.isEmpty()
                    ? text("source_no_viewer") : text("source_open", recipeViewer)));
            positionRecipeButton();
        } else if (contentWidth() > viewportWidth()) {
            addRenderableWidget(new StyledButton(x + 8, cardTop() + 18, 18, 28, Component.literal("\u2039"),
                    ignored -> targetScroll = Math.max(0, targetScroll - CARD_WIDTH - CARD_GAP),
                    styles, "reward-popup-left", "toolbar-button"));
            addRenderableWidget(new StyledButton(x + w - 26, cardTop() + 18, 18, 28, Component.literal("\u203a"),
                    ignored -> targetScroll = Math.min(maxScroll(), targetScroll + CARD_WIDTH + CARD_GAP),
                    styles, "reward-popup-right", "toolbar-button"));
        }
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (selectedReward == null) {
            renderReceipt(g, mouseX, mouseY);
            super.renderContents(g, mouseX, mouseY, delta);
            return;
        }
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, ButtonStyle.FRAME_COLOR);
        g.fill(x, y, x + w, y + h, 0xFF2A2E38);
        g.fill(x, y, x + w, y + 28, 0xFF38445A);
        centered(g, text("source"),
                width / 2, y + 9, 0xFFF5D99A);
        renderItemDetails(g, x, y, w, h, mouseX, mouseY);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    private Component rewardName(QuestDefinition.Reward reward) {
        ItemStack item = stack(reward);
        return item.isEmpty() ? Component.translatable("screen.xero_quest_journal.reward_type." + reward.type())
                : item.getHoverName();
    }

    private void renderReceipt(GuiGraphics g, int mouseX, int mouseY) {
        long now = System.nanoTime();
        double elapsed = lastRenderNanos == 0 ? 0 : Math.min(.1, (now - lastRenderNanos) / 1_000_000_000.0);
        lastRenderNanos = now;
        scroll += (targetScroll - scroll) * (1 - Math.exp(-16 * elapsed));
        if (Math.abs(targetScroll - scroll) < .05) scroll = targetScroll;
        int y = top(), w = panelWidth();
        // The modal transition already fades the whole window. Keep the receipt surface
        // transparent so the blurred parent remains visible behind the reward content.
        g.pose().pushPose();
        g.pose().translate(width / 2f, y + 12, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        centered(g, text("received"), 0, 0, 0xFFF5D99A);
        g.pose().popPose();
        int titleHalf = Math.round(font.width(text("received")) * 1.5F / 2) + 10;
        int reach = Math.min(90, Math.max(20, w / 2 - titleHalf - 12));
        for (int side = -1; side <= 1; side += 2) {
            int inner = width / 2 + side * titleHalf;
            int outer = width / 2 + side * (titleHalf + reach);
            g.fill(Math.min(inner, outer), y + 18, Math.max(inner, outer), y + 19, 0x99D9C38E);
            MailDecor.diamond(g, outer, y + 18, 2, 0xCCD9C38E);
            MailDecor.diamond(g, inner + side * 4, y + 18, 1, 0xFFF5D99A);
        }
        g.fill(left() + 12, y + 36, left() + w - 12, y + 37, 0x887E7668);
        g.fill(left() + 12, y + panelHeight() - 30, left() + w - 12, y + panelHeight() - 29, 0x887E7668);
        if (rewards.size() == 1) {
            centered(g, Component.literal(font.plainSubstrByWidth(
                    rewardName(rewards.getFirst()).getString(), viewportWidth())), width / 2, y + 50, 0xFFF0EEE8);
        }
        if (rewards.isEmpty()) centered(g, text("empty"), width / 2, y + 104, 0xFFABB8C3);
        float cardScale = CARD_WIDTH / (float) RewardCardRenderer.WIDTH;
        int renderedCardHeight = RewardCardRenderer.scaledHeight(cardScale);
        g.enableScissor(viewportLeft(), cardTop() - 1, viewportRight(), cardTop() + renderedCardHeight + 24);
        for (int index = 0; index < rewards.size(); index++) {
            int cardX = (int) Math.round(contentLeft() + index * (CARD_WIDTH + CARD_GAP));
            int cardY = cardTop();
            if (cardX + CARD_WIDTH <= viewportLeft() || cardX >= viewportRight()) continue;
            var reward = rewards.get(index);
            RewardCardRenderer.render(g, font, QuestRewardsScreen.icon(reward.type(), reward.item()),
                    reward.count(), reward.quality(), reward.background(), cardX, cardY, cardScale,
                    1, false, rewardAt(mouseX, mouseY) == index);
            var lines = font.split(rewardName(reward), CARD_WIDTH);
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                var label = lines.get(line);
                g.drawString(font, label, cardX + (CARD_WIDTH - font.width(label)) / 2,
                        cardY + renderedCardHeight + 3 + line * 9, 0xFFF0EEE8, false);
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            g.fill(viewportLeft(), trackY(), viewportRight(), trackY() + 3, 0x66424750);
            int thumbX = viewportLeft() + (int) ((viewportWidth() - thumbWidth()) * scroll / maxScroll());
            g.fill(thumbX, trackY(), thumbX + thumbWidth(), trackY() + 3, 0xFFD9C99E);
        }
        centered(g, text("popup_continue"), width / 2, y + panelHeight() - 15, 0xFFBCBCC0);
    }

    private void centered(GuiGraphics graphics, Component text, int center, int y, int color) {
        graphics.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    @Override protected boolean isModalScreen() { return true; }

    @Override
    protected void renderModalBackground(GuiGraphics g, float delta) {
        if (parent != null) parent.render(g, -10000, -10000, delta);
        if (selectedReward == null) {
            g.flush();
            // Blur the completed parent on the main target, never the modal's offscreen surface.
            renderBlurredBackground(delta);
        }
    }

    @Override
    protected Viewport popupBounds() {
        return viewport(left() - 2, top() - 2, left() + panelWidth() + 2, top() + panelHeight() + 2);
    }

    private void renderItemDetails(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        ItemStack stack = inspectedStack;
        g.fill(x, y + 28, x + w, y + 86, 0xFF343B47);
        if (!stack.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + w - 52, y + 36, 0);
            g.pose().scale(2.5F, 2.5F, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            int nameY = y + 38;
            for (var line : font.split(stack.getHoverName(), w - 76)) {
                if (nameY > y + 72) break;
                g.drawString(font, line, x + 13, nameY, 0xFFFFFFFF, false);
                nameY += font.lineHeight + 2;
            }
        } else {
            g.drawWordWrap(font, Component.translatable("screen.xero_quest_journal.reward_type."
                    + selectedReward.type()), x + 13, y + 42, w - 26, 0xFFB8C7D9);
        }
        var viewport = detailViewport();
        g.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        int lineY = y + DETAIL_TOP - detailScroll;
        for (var wrapped : tooltipLines) {
            g.drawString(font, wrapped, x + 16, lineY, 0xFFE5EAF1, false);
            lineY += font.lineHeight + 2;
        }
        g.drawString(font, text("source"), x + 16, lineY + 3, 0xFFF5D99A, false);
        lineY += SOURCE_GAP;
        if (recipeOpenFailed) {
            for (var line : font.split(text("source_unavailable"), w - 32)) {
                g.drawString(font, line, x + 16, lineY, 0xFFFFAA88, false);
                lineY += 11;
            }
            lineY += 6;
        }
        for (SourceRow row : sourceRows) {
            if (lineY >= viewport.bottom()) break;
            var lines = row.lines();
            int rowHeight = row.height();
            if (lineY + rowHeight < viewport.top()) {
                lineY += rowHeight + 4;
                continue;
            }
            boolean hovered = viewport.contains(mouseX, mouseY) && mouseX >= x + 16
                    && mouseX < x + w - 16 && mouseY >= lineY && mouseY < lineY + rowHeight;
            g.renderOutline(x + 16, lineY, w - 32, rowHeight,
                    hovered ? ButtonStyle.FRAME_COLOR : 0xFF626C7B);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), x + 22, lineY + 4 + i * 11, 0xFFD4DAE3, false);
            }
            if (!row.questId().isEmpty()) g.drawString(font, "\u203a", x + w - 27,
                    lineY + (rowHeight - font.lineHeight) / 2, 0xFFE5EAF1, false);
            lineY += rowHeight + 4;
        }
        if (sources.isEmpty()) g.drawWordWrap(font, text("no_source"), x + 16, lineY + 5, w - 32, 0xFFABB8C3);
        g.disableScissor();
        int viewHeight = viewport.bottom() - viewport.top();
        if (detailContentHeight() > viewHeight) {
            int thumb = Math.max(12, viewHeight * viewHeight / detailContentHeight());
            int thumbY = viewport.top() + (viewHeight - thumb) * detailScroll / (detailContentHeight() - viewHeight);
            g.fill(x + w - 7, viewport.top(), x + w - 4, viewport.bottom(), 0xFF20242C);
            g.fill(x + w - 7, thumbY, x + w - 4, thumbY + thumb, ButtonStyle.FRAME_COLOR);
        }
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) { }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (!popupBounds().contains(mouseX, mouseY)) {
            if (button == 0) onClose();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && selectedReward != null && detailViewport().contains(mouseX, mouseY)
                && mouseX >= left() + 16 && mouseX < left() + panelWidth() - 16) {
            int rowY = top() + DETAIL_TOP + tooltipHeight() + SOURCE_GAP - detailScroll;
            if (recipeOpenFailed) rowY += font.split(text("source_unavailable"), panelWidth() - 32).size() * 11 + 6;
            for (SourceRow row : sourceRows) {
                if (mouseY >= rowY && mouseY < rowY + row.height() && !row.questId().isEmpty()) {
                    var view = ClientQuestProgress.view(row.questId());
                    if (view == null || !view.completed()) {
                        minecraft.setScreen(QuestScreen.task(null, row.questId()));
                        return true;
                    }
                }
                rowY += row.height() + 4;
            }
        }
        if (button == 0 && selectedReward == null && maxScroll() > 0
                && mouseY >= trackY() - 3 && mouseY <= trackY() + 6
                && mouseX >= viewportLeft() && mouseX < viewportRight()) {
            scrollToTrack(mouseX);
            scrollbarDragging = true;
            return true;
        }
        if (button == 0 && selectedReward == null) {
            pressedReward = rewardAt(mouseX, mouseY);
            if (pressedReward >= 0) {
                pressX = mouseX;
                pressScroll = scroll;
                targetScroll = scroll;
                return true;
            }
            onClose();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (selectedReward != null && popupBounds().contains(x, y)) {
            var viewport = detailViewport();
            detailScroll = Math.clamp(detailScroll - (int) (vertical * 22), 0,
                    Math.max(0, detailContentHeight() - (viewport.bottom() - viewport.top())));
            positionRecipeButton();
            return true;
        }
        if (selectedReward == null && (vertical != 0 || horizontal != 0) && contentWidth() > viewportWidth()
                && popupBounds().contains(x, y)) {
            double direction = horizontal != 0 ? horizontal : -vertical;
            targetScroll = Math.clamp(targetScroll + direction * (CARD_WIDTH + CARD_GAP) * 0.7,
                    0, maxScroll());
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (inputBlocked()) return true;
        if (button == 0 && scrollbarDragging) {
            scrollToTrack(x);
            return true;
        }
        if (button == 0 && pressedReward >= 0 && maxScroll() > 0) {
            if (Math.abs(x - pressX) > 3) dragging = true;
            if (!dragging) return true;
            targetScroll = Math.clamp(pressScroll + pressX - x, 0, maxScroll());
            scroll = targetScroll;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) {
            int selected = pressedReward;
            boolean open = !inputBlocked() && !dragging && !scrollbarDragging
                    && selected >= 0 && rewardAt(x, y) == selected;
            pressedReward = -1;
            dragging = scrollbarDragging = false;
            if (open) {
                minecraft.setScreen(LootTablePopupScreen.inspect(this, rewards.get(selected)));
                return true;
            }
        }
        return super.mouseReleased(x, y, button);
    }

    private void scrollToTrack(double x) {
        targetScroll = Math.clamp((x - viewportLeft() - thumbWidth() / 2.0)
                / Math.max(1, viewportWidth() - thumbWidth()), 0, 1) * maxScroll();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) {
            onClose();
            return true;
        }
        if (selectedReward == null && !inputBlocked() && (key == 262 || key == 263)) {
            targetScroll = Math.clamp(targetScroll + (key == 262 ? 1 : -1) * (CARD_WIDTH + CARD_GAP), 0, maxScroll());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
