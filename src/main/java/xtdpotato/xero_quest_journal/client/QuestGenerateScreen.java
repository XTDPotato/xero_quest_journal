package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.network.QuestGenerationNetwork;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Generation is an explicit server-authorized choice; opening this screen writes no tasks. */
public final class QuestGenerateScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final QuestGenerationNetwork.Offer offer;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String mode = "progress", namespace = "all", status = "";
    private boolean recipes = true;
    private boolean advanced;
    private boolean bonusExperience = true, itemRewards = true;
    private boolean advancementTasks = true, itemTasks = true, combatTasks = true, structureTasks = true;
    private String taskMultiplier = "1", rewardMultiplier = "1";
    private EditBox taskMultiplierField, rewardMultiplierField;
    private CompletableFuture<String> pending;
    public QuestGenerateScreen(Screen parent, QuestGenerationNetwork.Offer offer) {
        super(text("title")); this.parent = parent; this.offer = offer;
    }
    private static Component text(String key) { return Component.translatable("screen.xero_quest_journal.generate." + key); }
    protected void init() {
        clearWidgets();
        int w = Math.min(560, width - 36), x = (width - w) / 2;
        int panelHeight = advanced ? 390 : 250;
        int top = Math.max(30, (height - panelHeight) / 2);
        var modes = new StyledChoiceButton<>(x, top + 24, w, 24, text("mode"),
                List.of("progress", "structure", "both", "catalog"), mode, QuestGenerateScreen::text,
                value -> mode = value, styles, "generate-mode");
        modes.active = pending == null; addRenderableWidget(modes);
        var namespaces = new ArrayList<String>(); namespaces.add("all"); namespaces.addAll(offer.namespaces());
        var target = new StyledChoiceButton<>(x, top + 68, w, 24, text("namespace"), namespaces, namespace,
                value -> value.equals("all") ? text("all") : Component.literal(value), value -> namespace = value, styles, "generate-namespace");
        target.active = pending == null; addRenderableWidget(target);
        var check = Checkbox.builder(text("recipes"), font).pos(x, top + 102).maxWidth(w).selected(recipes)
                .onValueChange((box, value) -> recipes = value).build();
        check.active = pending == null; addRenderableWidget(check);
        addRenderableWidget(new StyledButton(x, top + 130, w, 20, text(advanced ? "advanced_hide" : "advanced_show"),
                ignored -> {
                    saveMultiplierFields();
                    advanced = !advanced;
                    init();
                }, styles, "generate-advanced"));
        if (advanced) initAdvanced(x, top, w);
        int actionY = top + panelHeight - 36;
        var generate = new StyledButton(x, actionY, (w - 8) / 2, 24, text("start"), ignored -> {
            saveMultiplierFields();
            pending = ClientQuestProgress.chooseGenerationMode(mode, namespace, recipes, options()); init();
        }, styles, "generate-start");
        generate.active = offer.allowed() && pending == null && !status.equals("generation_started"); addRenderableWidget(generate);
        addRenderableWidget(new StyledButton(x + (w + 8) / 2, actionY, (w - 8) / 2, 24,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "generate-cancel"));
        addRenderableWidget(new EditorIconButton(width - 36, 8, Component.translatable("gui.cancel"), this::onClose, styles, "generate-close", "close"));
    }

    private void initAdvanced(int x, int top, int width) {
        int fieldX = x + width - 62;
        taskMultiplierField = multiplierField(fieldX, top + 154, taskMultiplier, "task_multiplier");
        taskMultiplierField.setResponder(value -> taskMultiplier = value);
        addRenderableWidget(taskMultiplierField);
        rewardMultiplierField = multiplierField(fieldX, top + 183, rewardMultiplier, "reward_multiplier");
        rewardMultiplierField.setResponder(value -> rewardMultiplier = value);
        addRenderableWidget(rewardMultiplierField);
        checkbox("type_advancements", x, top + 213, advancementTasks,
                value -> advancementTasks = value, "generate-type-advancements");
        checkbox("type_items", x + width / 2, top + 213, itemTasks,
                value -> itemTasks = value, "generate-type-items");
        checkbox("type_combat", x, top + 239, combatTasks,
                value -> combatTasks = value, "generate-type-combat");
        checkbox("type_structures", x + width / 2, top + 239, structureTasks,
                value -> structureTasks = value, "generate-type-structures");
        checkbox("bonus_experience", x, top + 265, bonusExperience,
                value -> bonusExperience = value, "generate-bonus-experience");
        checkbox("item_rewards", x + width / 2, top + 265, itemRewards,
                value -> itemRewards = value, "generate-item-rewards");
    }

    private EditBox multiplierField(int x, int y, String value, String key) {
        var field = new EditBox(font, x, y, 56, 20, text(key));
        field.setValue(value);
        field.setMaxLength(2);
        field.setFilter(candidate -> candidate.matches("\\d{0,2}"));
        field.active = pending == null;
        return field;
    }

    private void checkbox(String label, int x, int y, boolean selected,
                          java.util.function.Consumer<Boolean> changed, String id) {
        var box = Checkbox.builder(text(label), font).pos(x, y).maxWidth(245).selected(selected)
                .onValueChange((widget, value) -> changed.accept(value)).build();
        box.active = pending == null;
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text(label + "_hint")));
        addRenderableWidget(box);
    }

    private void saveMultiplierFields() {
        if (taskMultiplierField != null) taskMultiplier = taskMultiplierField.getValue();
        if (rewardMultiplierField != null) rewardMultiplier = rewardMultiplierField.getValue();
    }

    private xtdpotato.xero_quest_journal.core.io.QuestGenerator.GenerationOptions options() {
        return new xtdpotato.xero_quest_journal.core.io.QuestGenerator.GenerationOptions(
                parseMultiplier(taskMultiplier), parseMultiplier(rewardMultiplier), bonusExperience,
                itemRewards, advancementTasks, itemTasks, combatTasks, structureTasks);
    }

    private static int parseMultiplier(String value) {
        try { return Math.clamp(Integer.parseInt(value), 1, 64); }
        catch (NumberFormatException invalid) { return 1; }
    }
    public void tick() {
        if (pending != null && pending.isDone()) {
            try { status = pending.join(); } catch (Exception failure) { status = "remote_unavailable"; }
            pending = null; init();
        }
    }
    protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, JOURNAL_BACKDROP); super.renderContents(g, x, y, delta);
        int w = Math.min(560, width - 36), left = (width - w) / 2;
        int panelHeight = advanced ? 390 : 250;
        int top = Math.max(30, (height - panelHeight) / 2);
        g.drawCenteredString(font, title, width / 2, 16, 0xFFF2D29A);
        g.drawString(font, text("mode"), left, top + 10, 0xFFE2D6BD);
        g.drawString(font, text("namespace"), left, top + 54, 0xFFE2D6BD);
        if (advanced) {
            g.drawString(font, text("task_multiplier"), left, top + 159, 0xFFE2D6BD);
            g.drawString(font, text("reward_multiplier"), left, top + 188, 0xFFE2D6BD);
            g.drawWordWrap(font, text("advanced_hint"), left, top + 294, w, 0xFFB9C2CF);
        } else {
            g.drawWordWrap(font, text(!offer.allowed() ? "permission" : mode + "_description"),
                    left, top + 164, w, 0xFFE5E8ED);
        }
        if (!status.isEmpty()) g.drawWordWrap(font,
                Component.translatable("screen.xero_quest_journal.action." + status),
                left, top + panelHeight - 57, w, 0xFFF2D29A);
    }
    public void onClose() { ClientQuestProgress.chooseGenerationMode("dismiss", "all", false); closeTo(parent); }
    public boolean isPauseScreen() { return false; }
}
