package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;
import xtdpotato.xero_quest_journal.core.runtime.QuestHoldingCapacity;

import java.util.ArrayList;
import java.util.List;

/** Click-opened journey card details. Hovering a card never shows this content. */
final class JourneyQuestPopupScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 32;
    private static final int ITEM_ROW_HEIGHT = 44;
    private static final int EXPANDED_HEIGHT = 34;
    private static final int FOOTER_AND_PADDING_HEIGHT = JournalDetailRenderer.FOOTER_HEIGHT + 12;
    private record ConditionRow(int index, int depth, QuestDefinition.Trigger trigger) {}
    private record ConditionHit(ConditionRow row, int top, int bottom, int cardLeft, int cardRight) {}

    private final Screen parent;
    private final String questId;
    private final QuestDefinition quest;
    private ButtonStyleSheet styles;
    private final SmoothScroll scroll = new SmoothScroll();
    private int expandedCondition = -1;
    private List<ConditionHit> conditionHits = List.of();

    JourneyQuestPopupScreen(Screen parent, String questId) {
        super(Component.translatable("screen.xero_quest_journal.journey.quest_popup"));
        this.parent = parent;
        this.questId = questId;
        this.quest = ClientQuestDefinitions.find(questId);
    }

    private int panelWidth() { return Math.min(Math.round(width * .75F), width - 24); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelHeight() { return Math.min(Math.round(height * .75F), height - 24); }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int footerTop() { return panelTop() + panelHeight() - FOOTER_AND_PADDING_HEIGHT; }
    private int bodyTop() { return panelTop() + 58; }
    private int bodyBottom() { return footerTop() - 6; }
    private List<QuestDefinition.Reward> displayedRewards() {
        if (quest == null) return List.of();
        var view = ClientQuestProgress.view(quest.id());
        return view == null ? quest.rewards() : view.rewards();
    }
    private List<ConditionRow> conditionRows() {
        if (quest == null) return List.of();
        var rows = new ArrayList<ConditionRow>();
        for (var condition : QuestText.activeConditions(quest)) appendCondition(rows, condition, 0);
        return List.copyOf(rows);
    }
    private static void appendCondition(List<ConditionRow> rows, QuestDefinition.Trigger trigger, int depth) {
        if (rows.size() >= 128) return;
        rows.add(new ConditionRow(rows.size(), depth, trigger));
        for (var child : trigger.children()) appendCondition(rows, child, Math.min(12, depth + 1));
    }
    private boolean itemCondition(QuestDefinition.Trigger trigger) {
        return List.of("item_get", "item_hold", "item_submit", "item_acquire").contains(trigger.type());
    }
    private String conditionIcon(QuestDefinition.Trigger trigger) {
        if (itemCondition(trigger)) return trigger.item();
        return switch (trigger.type()) {
            case "kill" -> "minecraft:iron_sword";
            case "block_break", "block_interact" -> "minecraft:stone";
            case "dimension", "region", "location" -> "minecraft:compass";
            case "advancement" -> "minecraft:knowledge_book";
            case "script", "event" -> "minecraft:book";
            case "stage" -> "minecraft:clock";
            case "quest", "unlock", "lock" -> "minecraft:paper";
            case "all", "any", "count", "ratio" -> "minecraft:chain";
            default -> "minecraft:map";
        };
    }
    private int rowHeight(ConditionRow row) {
        var hint = minecraft == null ? null : QuestHoldingCapacity.hint(minecraft.player, row.trigger());
        return (itemCondition(row.trigger()) ? ITEM_ROW_HEIGHT : ROW_HEIGHT)
                + (expandedCondition == row.index() ? EXPANDED_HEIGHT : 0)
                + (hint == null ? 0 : 4 + font.split(hint.message(), Math.max(1, panelWidth() - 60)).size() * 11);
    }

    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() {
        return viewport(panelLeft() - 2, panelTop() - 2,
                panelLeft() + panelWidth() + 2, panelTop() + panelHeight() + 2);
    }
    @Override protected void init() {
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int x = panelLeft(), y = panelTop(), w = panelWidth();
        addRenderableWidget(new EditorIconButton(x + w - 34, y + 10,
                Component.translatable("screen.xero_quest_journal.close"), this::onClose, styles,
                "journey-quest-popup-close", "close"));
    }
    @Override protected void renderModalBackground(GuiGraphics graphics, float delta) {
        if (parent != null) parent.render(graphics, -10000, -10000, delta);
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = panelLeft(), y = panelTop(), w = panelWidth();
        int h = panelHeight();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFFE0C99A);
        g.fill(x, y, x + w, y + h, 0xFF27343D);
        if (quest == null) {
            g.drawString(font, Component.translatable("screen.xero_quest_journal.journey.quest_missing"),
                    x + 18, y + 24, 0xFFFFA6A6);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(QuestText.title(quest).getString(), Math.max(1, w - 64)),
                x + 18, y + 20, 0xFFF5D99F, true);
        g.drawString(font, QuestText.type(quest), x + 18, y + 39, 0xFFB8C5D0, false);
        var view = ClientQuestProgress.view(quest.id());
        var description = font.split(QuestText.description(quest), Math.max(1, w - 42));
        List<ConditionRow> rows = conditionRows();
        int contentHeight = 37 + description.size() * (font.lineHeight + 3) + 22;
        for (var row : rows) contentHeight += rowHeight(row) + 3;
        int viewportHeight = Math.max(1, bodyBottom() - bodyTop());
        scroll.limit(Math.max(0, contentHeight - viewportHeight));
        scroll.advance();
        int left = x + 18, right = x + w - 18;
        int lineY = bodyTop() - scroll.pixels();
        conditionHits = new ArrayList<>();
        g.enableScissor(x + 14, bodyTop(), x + w - 14, bodyBottom());
        g.drawString(font, Component.translatable("screen.xero_quest_journal.tooltip.status")
                .append(": ").append(ClientQuestProgress.status(quest.id())),
                left, lineY, 0xFFFFE6AE, false);
        lineY += font.lineHeight + 2;
        int current = view == null ? 0 : view.current();
        int required = view == null ? 1 : Math.max(1, view.required());
        g.drawString(font, Component.translatable("screen.xero_quest_journal.tooltip.progress")
                .append(": ").append(current + " / " + required), left, lineY, 0xFF65E9E7, false);
        lineY += font.lineHeight + 5;
        for (var line : description) {
            g.drawString(font, line, left, lineY, 0xFFADB7C0, false);
            lineY += font.lineHeight + 2;
        }
        lineY += 4;
        g.drawString(font, Component.translatable("screen.xero_quest_journal.conditions.edit"),
                left, lineY, 0xFFF2CF83, false);
        lineY += font.lineHeight + 4;
        int rowLeft = x + 14, rowWidth = w - 28;
        for (var row : rows) {
            int height = rowHeight(row);
            int rowY = lineY;
            if (rowY + height > bodyTop() && rowY < bodyBottom()) {
                int indent = Math.min(row.depth() * 10, Math.max(0, rowWidth - 60));
                int itemX = rowLeft + 4 + indent;
                int cardRight = itemX;
                g.fill(rowLeft + indent, rowY, x + w - 14, rowY + (itemCondition(row.trigger()) ? ITEM_ROW_HEIGHT : ROW_HEIGHT),
                        row.index() == expandedCondition ? 0x66394751 : 0x4437434E);
                if (itemCondition(row.trigger())) {
                    boolean itemHovered = mouseX >= itemX && mouseX < itemX + RewardCardRenderer.WIDTH
                            && mouseY >= rowY + 3 && mouseY < rowY + 3 + RewardCardRenderer.HEIGHT;
                    RewardCardRenderer.render(g, font, row.trigger().item(), row.trigger().count(),
                            itemX, rowY + 3, 1, 1, false, itemHovered);
                    ItemStack stack = QuestIcons.stack(row.trigger().item());
                    if (itemHovered && !stack.isEmpty()) g.renderTooltip(font, stack, mouseX, mouseY);
                    cardRight = itemX + RewardCardRenderer.WIDTH;
                } else {
                    int iconSize = 22;
                    g.fill(itemX, rowY + 5, itemX + iconSize, rowY + 5 + iconSize, 0x55303943);
                    QuestIcons.render(g, conditionIcon(row.trigger()), itemX + 3, rowY + 8, 16, 16, 1);
                    cardRight = itemX + iconSize;
                }
                String label = QuestText.describe(row.trigger()).getString();
                g.drawString(font, font.plainSubstrByWidth(label,
                                Math.max(1, right - cardRight - 12)),
                        cardRight + 8, rowY + 4, 0xFFE8EDF0, false);
                if (expandedCondition == row.index())
                    renderConditionDetails(g, row.trigger(), rowY + (itemCondition(row.trigger())
                            ? ITEM_ROW_HEIGHT : ROW_HEIGHT), left + indent + 8, right);
                var capacity = QuestHoldingCapacity.hint(minecraft.player, row.trigger());
                if (capacity != null) {
                    int hintY = rowY + (itemCondition(row.trigger()) ? ITEM_ROW_HEIGHT : ROW_HEIGHT) + 4
                            + (expandedCondition == row.index() ? EXPANDED_HEIGHT : 0);
                    for (var line : font.split(capacity.message(), Math.max(1, panelWidth() - 60))) {
                        g.drawString(font, line, rowLeft + 8, hintY,
                                capacity.impossible() ? 0xFFFF8F83 : 0xFFE8C774, false);
                        hintY += 11;
                    }
                }
                conditionHits.add(new ConditionHit(row, rowY, rowY + height, itemX, cardRight));
            }
            lineY += height + 3;
        }
        g.disableScissor();
        if (scroll.maximum() > 0) {
            int trackX = x + w - 7;
            g.fill(trackX, bodyTop(), trackX + 2, bodyBottom(), 0x55303842);
            int thumb = Math.max(12, viewportHeight * viewportHeight / Math.max(1, contentHeight));
            int thumbY = bodyTop() + (int) Math.round(scroll.position() / scroll.maximum()
                    * Math.max(0, viewportHeight - thumb));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, 0xAA9EB0C1);
        }
        int footerX = x + 14, footerRight = x + w - 14;
        JournalDetailRenderer.renderFooter(g, font, quest, displayedRewards(), footerX, footerTop(),
                footerRight, mouseX, mouseY, false, false);
        var hit = JournalDetailRenderer.cardAt(font, quest, displayedRewards(), footerX, footerTop(),
                footerRight, mouseX, mouseY);
        if (hit != null) {
            var stack = QuestIcons.stack(hit.reward().item());
            if (!stack.isEmpty()) g.renderTooltip(font, stack, mouseX, mouseY);
        }
        super.renderContents(g, mouseX, mouseY, delta);
    }
    private void renderConditionDetails(GuiGraphics g, QuestDefinition.Trigger trigger, int y, int left, int right) {
        int line = font.lineHeight + 2;
        var location = trigger.type().equals("location") ? QuestLocation.parse(trigger.item()) : null;
        if (location != null) {
            String summary = QuestText.placeName(location).getString();
            g.drawString(font, font.plainSubstrByWidth(summary, Math.max(1, right - left)),
                    left, y, 0xFFB8C5D0, false);
            if (location.kind().equals("structure"))
                g.drawString(font, font.plainSubstrByWidth(location.target(), Math.max(1, right - left)),
                        left, y + line, 0xFF8FA3B5, false);
            else if (location.kind().equals("coords"))
                g.drawString(font, font.plainSubstrByWidth(location.summary(), Math.max(1, right - left)),
                        left, y + line, 0xFF8FA3B5, false);
            return;
        }
        if (!trigger.type().equals("script") && !trigger.item().isBlank())
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(
                            "screen.xero_quest_journal.journey.condition_target", trigger.item()).getString(),
                    Math.max(1, right - left)), left, y, 0xFFB8C5D0, false);
        if (trigger.count() > 1)
            g.drawString(font, Component.translatable("screen.xero_quest_journal.journey.condition_count",
                    trigger.count()), left, y + line, 0xFF8FA3B5, false);
        else if (trigger.children().isEmpty() && trigger.item().isBlank())
            g.drawString(font, Component.translatable("screen.xero_quest_journal.journey.condition_no_target"),
                    left, y, 0xFF8FA3B5, false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int x = panelLeft(), w = panelWidth();
        int footerX = x + 14, footerRight = x + w - 14;
        var reward = JournalDetailRenderer.cardAt(font, quest, displayedRewards(), footerX, footerTop(),
                footerRight, mouseX, mouseY);
        if (reward != null) {
            minecraft.setScreen(LootTablePopupScreen.inspect(this, reward.reward()));
            return true;
        }
        for (var hit : conditionHits) {
            if (mouseX < x + 14 || mouseX >= x + w - 14 || mouseY < hit.top() || mouseY >= hit.bottom())
                continue;
            var trigger = hit.row().trigger();
            if (itemCondition(trigger) && mouseX >= hit.cardLeft() && mouseX < hit.cardRight()) {
                ItemStack stack = QuestIcons.stack(trigger.item());
                if (!stack.isEmpty()) {
                    minecraft.setScreen(new QuestRewardPopupScreen(this,
                            new QuestDefinition.Reward("item", trigger.item(), trigger.count())));
                    return true;
                }
            }
            minecraft.setScreen(new ConditionDetailScreen(this, trigger));
            return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inputBlocked()) return true;
        if (x >= panelLeft() && x < panelLeft() + panelWidth()
                && y >= bodyTop() && y < bodyBottom()) {
            scroll.wheel(v * 24);
            return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
