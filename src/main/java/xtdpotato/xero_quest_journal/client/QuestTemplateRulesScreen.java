package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestConditions;

import java.util.ArrayList;
import java.util.List;

/** Edits the reusable condition and reward lists owned by a quest template. */
final class QuestTemplateRulesScreen extends AnimatedQuestScreen {
    private final QuestTemplateEditorScreen parent;
    private final boolean conditionsPage;
    private final List<QuestDefinition.Trigger> initialConditions;
    private final List<QuestDefinition.Reward> initialRewards;
    private final List<ConditionDraft> conditions = new ArrayList<>();
    private final List<RewardDraft> rewards = new ArrayList<>();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private int rowScroll;
    private final SmoothScroll motion = new SmoothScroll();
    private boolean draggingList, moved;
    private double startX, startY, startScroll;
    private String error = "";

    private static final class ConditionDraft {
        String type;
        String target;
        String count;
        ConditionDraft(QuestDefinition.Trigger trigger) {
            type = trigger.type();
            target = trigger.item();
            count = Integer.toString(trigger.count());
        }
        QuestDefinition.Trigger value() {
            return new QuestDefinition.Trigger(type, target.strip(), Integer.parseInt(count.strip()));
        }
    }

    private static final class RewardDraft {
        String type;
        String item;
        String count;
        String quality;
        String background;
        RewardDraft(QuestDefinition.Reward reward) {
            type = reward.type();
            item = reward.item();
            count = Integer.toString(reward.count());
            quality = reward.quality();
            background = reward.background();
        }
        QuestDefinition.Reward value() {
            return new QuestDefinition.Reward(type, item.strip(), Integer.parseInt(count.strip()), quality, background);
        }
    }

    QuestTemplateRulesScreen(QuestTemplateEditorScreen parent, boolean conditionsPage,
                             List<QuestDefinition.Trigger> conditions,
                             List<QuestDefinition.Reward> rewards) {
        super(text(conditionsPage ? "default_conditions" : "default_rewards"));
        this.parent = parent;
        this.conditionsPage = conditionsPage;
        initialConditions = List.copyOf(conditions);
        initialRewards = List.copyOf(rewards);
        conditions.forEach(value -> this.conditions.add(new ConditionDraft(value)));
        rewards.forEach(value -> this.rewards.add(new RewardDraft(value)));
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.template." + key);
    }

    private int left() { return Math.max(8, (width - Math.min(560, width - 24)) / 2); }
    private int panelWidth() { return Math.min(560, width - 24); }
    private int rows() { return Math.max(1, (height - 126) / 48); }
    private int size() { return conditionsPage ? conditions.size() : rewards.size(); }
    private Viewport rowViewport() { return new Viewport(left(), 62, left() + panelWidth(), height - 54); }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return rowWidgets.contains(widget) ? rowViewport() : null;
    }

    @Override
    protected void init() {
        clearWidgets();
        rowWidgets.clear();
        int x = left(), w = panelWidth();
        button("template-rule-add", "+", x + w - 26, 32, 24, () -> {
            if (conditionsPage) conditions.add(new ConditionDraft(new QuestDefinition.Trigger("confirm", "", 1)));
            else rewards.add(new RewardDraft(new QuestDefinition.Reward("item", "minecraft:diamond", 1)));
            rowScroll = Math.max(0, size() * 48 - (height - 116));
            init();
        }).active = size() < 128;

        motion.limit(size() * 48 - (height - 116));
        motion.jump(rowScroll);
        rowScroll = motion.pixels();
        for (int i = 0; i < size(); i++) {
            int first = children().size();
            int y = 62 + i * 48 - rowScroll;
            if (conditionsPage) addConditionRow(i, x, w, y);
            else addRewardRow(i, x, w, y);
            children().subList(first, children().size()).stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(widget -> !rowWidgets.contains(widget)).forEach(rowWidgets::add);
        }
        int half = (w - 8) / 2;
        button("template-rules-save", text("save"), x, height - 30, half, this::save);
        button("template-rules-cancel", Component.translatable("gui.cancel"), x + half + 8,
                height - 30, half, this::onClose);
        addRenderableWidget(new ScrollRail(x + w + 2, 62, height - 116, motion));
    }

    private void addConditionRow(int index, int x, int w, int y) {
        ConditionDraft draft = conditions.get(index);
        var types = QuestConditions.TYPES.stream()
                .filter(value -> !List.of("all", "any", "count", "ratio").contains(value)).toList();
        var type = new StyledChoiceButton<>(x, y, Math.max(120, w - 100), 20, text("default_conditions"),
                types, draft.type, value -> Component.translatable(
                "screen.xero_quest_journal.condition_type." + value), value -> {
            draft.type = value;
            draft.target = value.startsWith("item_") ? "minecraft:diamond" : "";
            init();
        }, styles, "template-condition-type-" + index);
        rowWidgets.add(addRenderableWidget(type));
        button("template-condition-delete-" + index, "\u00d7", x + w - 24, y, 24, () -> {
            conditions.remove(index);
            init();
        });
        var target = new SuggestedEditBox(this, font, x, y + 22, w - 94, 20, text("condition_target"), draft.type);
        if (draft.type.startsWith("item_") || draft.type.equals("location")) {
            target.withoutMenu();
            rowWidgets.add(button("template-condition-pick-" + index, "...", x + w - 90, y + 22, 22, () -> {
                if (draft.type.equals("location")) minecraft.setScreen(
                        new QuestLocationScreen(this, draft.target, value -> draft.target = value));
                else minecraft.setScreen(QuestScreen.pickResource(this, draft.target, true, true, value -> draft.target = value));
            }));
        }
        target.setMaxLength(8192);
        target.setValue(draft.target);
        target.setResponder(value -> draft.target = value);
        target.setEditable(!draft.type.equals("confirm") && !draft.type.matches("all|any|count|ratio"));
        rowWidgets.add(addRenderableWidget(target));
        EditBox count = new SuggestedEditBox(this, font, x + w - 64, y + 22, 64, 20, text("condition_count"), "count");
        count.setMaxLength(7);
        count.setValue(draft.count);
        count.setResponder(value -> draft.count = value);
        rowWidgets.add(addRenderableWidget(count));
    }

    private void addRewardRow(int index, int x, int w, int y) {
        RewardDraft draft = rewards.get(index);
        var type = new StyledChoiceButton<>(x, y, w - 30, 20, text("default_rewards"),
                List.of("item", "command", "function", "script"), draft.type,
                value -> Component.literal(value), value -> { draft.type = value; init(); },
                styles, "template-reward-type-" + index);
        rowWidgets.add(addRenderableWidget(type));
        button("template-reward-delete-" + index, "\u00d7", x + w - 24, y, 24, () -> {
            rewards.remove(index);
            init();
        });
        var item = new SuggestedEditBox(this, font, x, y + 22, w - 94, 20, text("default_reward_value"), draft.type);
        if (draft.type.equals("item")) {
            item.withoutMenu();
            rowWidgets.add(button("template-reward-pick-" + index, "...", x + w - 90, y + 22, 22,
                    () -> minecraft.setScreen(QuestScreen.pickResource(this, draft.item, true, value -> draft.item = value))));
        }
        item.setMaxLength(8192);
        item.setValue(draft.item);
        item.setResponder(value -> draft.item = value);
        rowWidgets.add(addRenderableWidget(item));
        EditBox count = new SuggestedEditBox(this, font, x + w - 64, y + 22, 64, 20, text("default_reward_count"), "count");
        count.setMaxLength(7);
        count.setValue(draft.count);
        count.setResponder(value -> draft.count = value);
        rowWidgets.add(addRenderableWidget(count));
    }

    private StyledButton button(String id, String symbol, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new StyledButton(x, y, w, 20, Component.literal(symbol),
                ignored -> action.run(), styles, id, "toolbar-button"));
    }

    private StyledButton button(String id, Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new StyledButton(x, y, w, 22, label,
                ignored -> action.run(), styles, id, "toolbar-button"));
    }

    private void save() {
        try {
            var conditionValues = conditions.stream().map(ConditionDraft::value).toList();
            var rewardValues = rewards.stream().map(RewardDraft::value).toList();
            if (conditionValues.stream().anyMatch(value -> !QuestConditions.valid(List.of(value)))
                    || rewardValues.stream().anyMatch(value -> !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(value))) {
                throw new IllegalArgumentException("Invalid template rules");
            }
            parent.updateTemplateRules(conditionValues, rewardValues);
            minecraft.setScreen(parent);
            error = "";
        } catch (RuntimeException invalid) {
            error = "invalid";
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (rowViewport().contains(x, y)) {
            motion.wheel(vertical * 48);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        if (hasChoicePopup()) motion.freeze();
        motion.advance();
        moveRows();
        super.renderContents(g, x, y, delta);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        if (size() == 0) g.drawCenteredString(font, text("rules_empty"), width / 2, 80, 0xFFCCD5DE);
        if (!"".equals(error)) g.drawCenteredString(font, text("error.invalid"), width / 2, height - 50, 0xFFFF7777);
    }
    private void moveRows() {
        int next = motion.pixels(), offset = rowScroll - next;
        rowWidgets.forEach(widget -> widget.setY(widget.getY() + offset));
        rowScroll = next;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        if (button == 0 && rowViewport().contains(x, y) && !(getChildAt(x, y).orElse(null) instanceof EditBox)) {
            draggingList = true; moved = false;
            startX = x; startY = y; startScroll = motion.position(); motion.freeze();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (draggingList && button == 0) {
            moved |= Math.abs(y - startY) >= 4;
            if (moved) { motion.jump(startScroll + startY - y); moveRows(); }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (releaseChoicePopup(x, y, button)) return true;
        if (draggingList && button == 0) {
            draggingList = false;
            if (!moved && Math.abs(x - startX) < 4 && rowViewport().contains(x, y)) {
                super.mouseClicked(startX, startY, button);
                super.mouseReleased(x, y, button);
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        super.renderBackground(g, x, y, delta);
        backdrop(g, 0xE51D2025);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
