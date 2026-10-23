package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

/** Click-opened completion-condition details. */
final class ConditionDetailScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final QuestDefinition.Trigger trigger;
    private final String title;

    ConditionDetailScreen(Screen parent, QuestDefinition.Trigger trigger) {
        super(Component.translatable("screen.xero_quest_journal.conditions.edit"));
        this.parent = parent;
        this.trigger = trigger;
        this.title = trigger.type();
    }

    private int panelWidth() { return Math.min(520, width - 32); }
    private int panelHeight() { return Math.min(330, height - 32); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int conditionTop() { return top() + 78; }
    private boolean itemCondition() {
        return trigger.type().startsWith("item") && !trigger.item().isBlank();
    }
    private int cardWidth() { return Math.round(RewardCardRenderer.WIDTH * 1.5F); }
    private int cardHeight() { return RewardCardRenderer.scaledHeight(1.5F); }

    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() {
        return viewport(left() - 2, top() - 2, left() + panelWidth() + 2, top() + panelHeight() + 2);
    }
    @Override protected void renderModalBackground(GuiGraphics g, float delta) {
        if (parent != null) parent.render(g, -10000, -10000, delta);
    }
    @Override protected void init() {
        clearWidgets();
        addRenderableWidget(new EditorIconButton(left() + panelWidth() - 30, top() + 8,
                Component.translatable("screen.xero_quest_journal.close"), this::onClose,
                ButtonStyleSheet.load(), "condition-detail-close", "close"));
    }
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF9BAFCB);
        g.fill(x, y, x + w, y + h, 0xFF2B303A);
        g.fill(x, y, x + w, y + 52, 0xFF39465C);
        QuestIcons.render(g, icon(), x + 18, y + 13, 26);
        g.drawString(font, Component.translatable("screen.xero_quest_journal.conditions.detail." + title),
                x + 56, y + 17, 0xFFF5D99A, false);
        int line = conditionTop();
        if (itemCondition()) {
            ItemStack stack = item();
            if (!stack.isEmpty()) {
                boolean hovered = mx >= x + 18 && mx < x + 18 + cardWidth()
                        && my >= line && my < line + cardHeight();
                RewardCardRenderer.render(g, font, trigger.item(), trigger.count(), x + 18, line,
                        1.5F, 1, false, hovered);
                g.drawString(font, stack.getHoverName(), x + 92, line + 12, 0xFFF0EEE8, false);
                g.drawString(font, Component.translatable("screen.xero_quest_journal.journey.condition_count",
                        trigger.count()), x + 92, line + 34, 0xFFB8C3D0, false);
            }
        } else {
            g.drawWordWrap(font, Component.translatable("screen.xero_quest_journal.journey.condition_target",
                    trigger.item().isBlank() ? Component.translatable(
                            "screen.xero_quest_journal.journey.condition_no_target") : trigger.item()),
                    x + 18, line, w - 36, 0xFFE3E8ED);
            if (trigger.count() > 1)
                g.drawString(font, Component.translatable("screen.xero_quest_journal.journey.condition_count",
                        trigger.count()), x + 18, line + 42, 0xFFB8C3D0, false);
        }
        g.drawString(font, Component.translatable("screen.xero_quest_journal.conditions.detail.click_hint"),
                x + 18, y + h - 28, 0xFF9DA9B7, false);
        var capacity = xtdpotato.xero_quest_journal.core.runtime.QuestHoldingCapacity.hint(minecraft.player, trigger);
        if (capacity != null)
            g.drawWordWrap(font, capacity.message(), x + 18, line + 82, w - 36,
                    capacity.impossible() ? 0xFFFF8F83 : 0xFFE8C774);
        super.renderContents(g, mx, my, delta);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || !itemCondition()) return false;
        if (mouseX < left() + 18 || mouseX >= left() + 18 + cardWidth()
                || mouseY < conditionTop() || mouseY >= conditionTop() + cardHeight()) return false;
        if (trigger.item().startsWith("#")) {
            var tagged = QuestIcons.tagStacks(trigger.item());
            if (!tagged.isEmpty()) {
                minecraft.setScreen(new QuestTagItemsScreen(this, trigger.item(), tagged));
                return true;
            }
        }
        ItemStack stack = item();
        if (stack.isEmpty()) return false;
        minecraft.setScreen(new QuestRewardPopupScreen(this,
                new QuestDefinition.Reward("item", trigger.item(), trigger.count())));
        return true;
    }

    private String icon() {
        if (trigger.type().startsWith("item")) return trigger.item();
        return switch (trigger.type()) {
            case "kill" -> "minecraft:iron_sword";
            case "location", "dimension", "region" -> "minecraft:compass";
            case "advancement" -> "minecraft:knowledge_book";
            case "structure" -> "minecraft:structure_block";
            default -> "minecraft:book";
        };
    }
    private ItemStack item() {
        ResourceLocation id = ResourceLocation.tryParse(trigger.item());
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id), Math.min(64, trigger.count()))
                : ItemStack.EMPTY;
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
