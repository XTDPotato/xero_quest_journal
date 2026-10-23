package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.io.QuestGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Edit the generator's cumulative, difficulty-tiered item reward catalog. */
public final class QuestGenerationRewardTiersScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 28;
    private static final int GROUP_GAP = 8;
    private final Screen parent;
    private final Consumer<List<QuestGenerator.TierItemReward>> applied;
    private final List<RewardDraft> rewards = new ArrayList<>();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final SmoothScroll scroll = new SmoothScroll();
    private final Set<AbstractWidget> contentWidgets =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AbstractWidget, Integer> baseY = new IdentityHashMap<>();
    private int listTop, listBottom, contentHeight;
    private boolean pointerDown, dragged;
    private double downX, downY, startScroll;

    public QuestGenerationRewardTiersScreen(Screen parent, List<QuestGenerator.TierItemReward> initial,
                                            Consumer<List<QuestGenerator.TierItemReward>> applied) {
        super(text("title"));
        this.parent = parent;
        this.applied = applied;
        initial.forEach(reward -> rewards.add(new RewardDraft(reward)));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.generate.reward_tiers." + key, args);
    }

    private int panelWidth() { return Math.min(720, width - 28); }
    private int left() { return (width - panelWidth()) / 2; }

    @Override
    protected void init() {
        clearWidgets();
        contentWidgets.clear();
        baseY.clear();
        int x = left(), w = panelWidth();
        listTop = 58;
        listBottom = Math.max(listTop + 48, height - 62);
        int cursor = listTop + 8;
        for (int tier = 1; tier <= 6; tier++) {
            int selectedTier = tier;
            cursor += 16;
            for (int index = 0; index < rewards.size(); index++) {
                RewardDraft draft = rewards.get(index);
                if (draft.tier != tier) continue;
                addRewardRow(x, w, cursor, draft);
                cursor += ROW_HEIGHT;
            }
            addContent(new StyledButton(x + 8, cursor, Math.max(100, w - 32), 20,
                    text("add_item"), ignored -> {
                        rewards.add(new RewardDraft(selectedTier, "minecraft:iron_ingot", 1, 1));
                        init();
                    }, styles, "generation-reward-add-" + tier, "toolbar-button"));
            cursor += ROW_HEIGHT + GROUP_GAP;
        }
        contentHeight = cursor - listTop;
        scroll.limit(Math.max(0, contentHeight - (listBottom - listTop)));
        moveWidgets();
        var rail = new ScrollRail(x + w - 8, listTop, listBottom - listTop, scroll);
        rail.visible = scroll.maximum() > 0;
        addRenderableWidget(rail);
        addRenderableWidget(new StyledButton(x, height - 30, (w - 6) / 2, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles,
                "generation-rewards-cancel"));
        addRenderableWidget(new StyledButton(x + (w + 6) / 2, height - 30, (w - 6) / 2, 22,
                text("apply"), ignored -> {
                    applied.accept(rewards.stream().map(RewardDraft::value).toList());
                    closeTo(parent);
                }, styles, "generation-rewards-apply"));
        addRenderableWidget(new EditorIconButton(width - 36, 8,
                Component.translatable("gui.cancel"), this::onClose, styles,
                "generation-rewards-close", "close"));
    }

    private void addRewardRow(int x, int width, int y, RewardDraft draft) {
        var choose = new StyledButton(x + 8, y, Math.max(48, width - 176), 24,
                Component.literal(QuestIcons.displayName(draft.item)),
                ignored -> minecraft.setScreen(QuestScreen.pickResource(this, draft.item, true,
                        value -> {
                            draft.item = value;
                            init();
                        })), styles, "generation-reward-item", "toolbar-button");
        choose.leadingIcon(draft.item);
        choose.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(draft.item)));
        addContent(choose);
        var count = new EditBox(font, x + width - 164, y + 2, 56, 20, text("count"));
        count.setValue(Integer.toString(draft.count));
        count.setMaxLength(2);
        count.setFilter(value -> value.matches("\\d{0,2}"));
        count.setResponder(value -> {
            try { draft.count = Math.clamp(Integer.parseInt(value), 1, 64); }
            catch (NumberFormatException ignored) { draft.count = 1; }
        });
        addContent(count);
        var weight = new EditBox(font, x + width - 98, y + 2, 56, 20, text("weight"));
        weight.setValue(Integer.toString(draft.weight));
        weight.setMaxLength(7);
        weight.setFilter(value -> value.matches("\\d{0,7}"));
        weight.setResponder(value -> {
            try { draft.weight = Math.clamp(Integer.parseInt(value), 1, 1_000_000); }
            catch (NumberFormatException ignored) { draft.weight = 1; }
        });
        weight.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("weight_hint")));
        addContent(weight);
        addContent(new EditorIconButton(x + width - 34, y, text("remove"),
                () -> {
                    rewards.remove(draft);
                    init();
                }, styles, "generation-reward-remove", "delete"));
    }

    private <T extends AbstractWidget> T addContent(T widget) {
        addRenderableWidget(widget);
        contentWidgets.add(widget);
        baseY.put(widget, widget.getY());
        return widget;
    }

    private void moveWidgets() {
        for (AbstractWidget widget : contentWidgets)
            widget.setY(baseY.getOrDefault(widget, widget.getY()) - scroll.pixels());
    }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return contentWidgets.contains(widget)
                ? viewport(left(), listTop, left() + panelWidth(), listBottom) : null;
    }

    @Override public void tick() {
        scroll.advance();
        moveWidgets();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, 0xF0181D23);
        int x = left(), w = panelWidth();
        RewardEditorTheme.panel(graphics, x, 4, w, 42, RewardEditorTheme.GOLD);
        graphics.fill(x, listTop, x + w, listBottom, 0xE520242B);
        graphics.enableScissor(x, listTop, x + w, listBottom);
        int cursor = listTop + 8;
        for (int tier = 1; tier <= 6; tier++) {
            int headerY = cursor - scroll.pixels();
            graphics.drawString(font, text("tier", tier), x + 10, headerY,
                    tier >= 4 ? 0xFFF0C878 : 0xFFD7DFE8);
            cursor += 16;
            int row = 0;
            for (RewardDraft reward : rewards) if (reward.tier == tier) {
                int rowY = cursor + row * ROW_HEIGHT - scroll.pixels();
                graphics.fill(x + 6, rowY, x + w - 14, rowY + 24, 0xFF29313B);
                graphics.renderOutline(x + 6, rowY, w - 20, 24, 0xFF596879);
                row++;
            }
            cursor += row * ROW_HEIGHT + ROW_HEIGHT + GROUP_GAP;
        }
        graphics.disableScissor();
        super.renderContents(graphics, mouseX, mouseY, delta);
        graphics.drawString(font, text("title"), x + 12, 12, RewardEditorTheme.TEXT);
        cursor = listTop + 8;
        graphics.enableScissor(x, listTop, x + w, listBottom);
        for (int tier = 1; tier <= 6; tier++) {
            int headerY = cursor - scroll.pixels();
            graphics.drawString(font, text("tier", tier), x + 10, headerY,
                    tier >= 4 ? 0xFFF0C878 : 0xFFD7DFE8);
            int row = 0;
            for (RewardDraft reward : rewards) if (reward.tier == tier) row++;
            cursor += 16 + row * ROW_HEIGHT + ROW_HEIGHT + GROUP_GAP;
        }
        graphics.disableScissor();
        if (scroll.maximum() > 0) {
            int track = listBottom - listTop;
            int thumb = Math.max(18, track * track / (track + (int) scroll.maximum()));
            int y = listTop + (int) ((track - thumb) * scroll.position() / scroll.maximum());
            graphics.fill(x + w - 6, listTop, x + w - 3, listBottom, 0xFF343B45);
            graphics.fill(x + w - 6, y, x + w - 3, y + thumb, 0xFF9DB4D2);
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left() && x < left() + panelWidth() && y >= listTop && y < listBottom) {
            scroll.wheel(vertical * ROW_HEIGHT * 2);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && y >= listTop && y < listBottom
                && x >= left() && x < left() + panelWidth() - 8) {
            pointerDown = true; dragged = false; downX = x; downY = y; startScroll = scroll.target();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && pointerDown) {
            if (!dragged && Math.hypot(x - downX, y - downY) > 4) dragged = true;
            if (dragged) scroll.to(startScroll + downY - y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && pointerDown) {
            pointerDown = false;
            if (!dragged && y >= listTop && y < listBottom) {
                super.mouseClicked(downX, downY, button);
                super.mouseReleased(x, y, button);
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public void onClose() { closeTo(parent); }

    private static final class RewardDraft {
        private final int tier;
        private String item;
        private int count;
        private int weight;
        private RewardDraft(QuestGenerator.TierItemReward reward) {
            this(reward.tier(), reward.item(), reward.count(), reward.weight());
        }
        private RewardDraft(int tier, String item, int count, int weight) {
            this.tier = Math.clamp(tier, 1, 6);
            this.item = item;
            this.count = Math.clamp(count, 1, 64);
            this.weight = Math.clamp(weight, 1, 1_000_000);
        }
        private QuestGenerator.TierItemReward value() {
            return new QuestGenerator.TierItemReward(tier, item, count, weight);
        }
    }
}
