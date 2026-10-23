package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private final SmoothScroll contentScroll = new SmoothScroll();
    private final Set<AbstractWidget> contentWidgets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AbstractWidget, Integer> contentBaseY = new IdentityHashMap<>();
    private String mode = "progress", status = "";
    private final LinkedHashSet<String> selectedNamespaces;
    private boolean recipes = true;
    private boolean advanced;
    private boolean bonusExperience = true, itemRewards = true;
    private String experienceUnit = "auto";
    private boolean advancementTasks = true, itemTasks = true, combatTasks = true, structureTasks = true;
    private String taskMultiplier = "1", rewardMultiplier = "1", experienceMultiplier = "1";
    private EditBox taskMultiplierField, rewardMultiplierField, experienceMultiplierField;
    private List<xtdpotato.xero_quest_journal.core.io.QuestGenerator.TierItemReward> tierItemRewards =
            xtdpotato.xero_quest_journal.core.io.QuestGenerator.GenerationOptions.defaults().tierItemRewards();
    private CompletableFuture<String> pending;
    private int contentTop, contentBottom, contentHeight;
    private boolean listDragging, listMoved;
    private double listStartX, listStartY, listStartScroll;
    public QuestGenerateScreen(Screen parent, QuestGenerationNetwork.Offer offer) {
        super(text("title"));
        this.parent = parent;
        this.offer = offer;
        selectedNamespaces = new LinkedHashSet<>(offer.namespaces());
    }
    private static Component text(String key) { return Component.translatable("screen.xero_quest_journal.generate." + key); }
    protected void init() {
        clearWidgets();
        contentWidgets.clear();
        contentBaseY.clear();
        int w = Math.min(560, width - 36), x = (width - w) / 2;
        contentTop = 46;
        contentBottom = Math.max(contentTop + 56, height - 64);
        int top = contentTop;
        var modes = new StyledChoiceButton<>(x, top + 24, w - 12, 24, text("mode"),
                List.of("progress", "structure", "both", "catalog"), mode, QuestGenerateScreen::text,
                value -> mode = value, styles, "generate-mode");
        modes.active = pending == null; addContentWidget(modes);
        String scopeLabel = Component.translatable("screen.xero_quest_journal.generate.scope.selected",
                selectedNamespaces.size(), offer.namespaces().size()).getString();
        var scope = new StyledButton(x, top + 68, w - 12, 24, Component.literal(scopeLabel), ignored ->
                minecraft.setScreen(new QuestGenerateScopeScreen(this, offer.namespaces(), selectedNamespaces,
                        values -> {
                            selectedNamespaces.clear();
                            selectedNamespaces.addAll(values);
                            init();
                        })), styles, "generate-scope", "toolbar-button");
        scope.active = pending == null;
        scope.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("scope.open_hint")));
        addContentWidget(scope);
        var check = Checkbox.builder(text("recipes"), font).pos(x, top + 102).maxWidth(w).selected(recipes)
                .onValueChange((box, value) -> recipes = value).build();
        check.active = pending == null; addContentWidget(check);
        addContentWidget(new StyledButton(x, top + 130, w - 12, 20, text(advanced ? "advanced_hide" : "advanced_show"),
                ignored -> {
                    saveMultiplierFields();
                    advanced = !advanced;
                    contentScroll.jump(0);
                    init();
                }, styles, "generate-advanced"));
        if (advanced) initAdvanced(x, top, w);
        contentHeight = advanced ? 424 : 220;
        contentScroll.limit(Math.max(0, contentHeight - (contentBottom - contentTop)));
        addRenderableWidget(new ScrollRail(x + w - 8, contentTop,
                contentBottom - contentTop, contentScroll));
        moveContent();
        int actionY = height - 38;
        var generate = new StyledButton(x, actionY, (w - 8) / 2, 24, text("start"), ignored -> {
            saveMultiplierFields();
            pending = ClientQuestProgress.chooseGenerationMode(mode, selectedNamespaces, recipes, options()); init();
        }, styles, "generate-start");
        generate.active = offer.allowed() && pending == null && !selectedNamespaces.isEmpty()
                && !status.equals("generation_started"); addRenderableWidget(generate);
        addRenderableWidget(new StyledButton(x + (w + 8) / 2, actionY, (w - 8) / 2, 24,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "generate-cancel"));
        addRenderableWidget(new EditorIconButton(width - 36, 8, Component.translatable("gui.cancel"), this::onClose, styles, "generate-close", "close"));
    }

    private void initAdvanced(int x, int top, int width) {
        int fieldX = x + width - 62;
        taskMultiplierField = multiplierField(fieldX, top + 154, taskMultiplier, "task_multiplier");
        taskMultiplierField.setResponder(value -> taskMultiplier = value);
        addContentWidget(taskMultiplierField);
        rewardMultiplierField = multiplierField(fieldX, top + 183, rewardMultiplier, "reward_multiplier");
        rewardMultiplierField.setResponder(value -> rewardMultiplier = value);
        addContentWidget(rewardMultiplierField);
        experienceMultiplierField = multiplierField(fieldX, top + 212, experienceMultiplier,
                "experience_multiplier");
        experienceMultiplierField.setResponder(value -> experienceMultiplier = value);
        addContentWidget(experienceMultiplierField);
        var unit = new StyledChoiceButton<>(x + width - 250, top + 212, 180, 20,
                text("experience_unit"), List.of("auto", "points", "levels"), experienceUnit,
                value -> text("experience_unit." + value), value -> experienceUnit = value,
                styles, "generate-experience-unit");
        unit.active = pending == null;
        unit.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("experience_unit_hint")));
        addContentWidget(unit);
        checkbox("type_advancements", x, top + 242, advancementTasks,
                value -> advancementTasks = value, "generate-type-advancements", top);
        checkbox("type_items", x + width / 2, top + 242, itemTasks,
                value -> itemTasks = value, "generate-type-items", top);
        checkbox("type_combat", x, top + 268, combatTasks,
                value -> combatTasks = value, "generate-type-combat", top);
        checkbox("type_structures", x + width / 2, top + 268, structureTasks,
                value -> structureTasks = value, "generate-type-structures", top);
        checkbox("experience_rewards", x, top + 295, bonusExperience,
                value -> bonusExperience = value, "generate-experience-rewards", top);
        checkbox("item_rewards", x + width / 2, top + 295, itemRewards,
                value -> itemRewards = value, "generate-item-rewards", top);
        addContentWidget(new StyledButton(x, top + 324, width - 12, 22,
                text("edit_tier_item_rewards"), ignored ->
                minecraft.setScreen(new QuestGenerationRewardTiersScreen(this, tierItemRewards, values -> {
                    tierItemRewards = List.copyOf(values);
                    init();
                })), styles, "generate-tier-item-rewards", "toolbar-button"));
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
                          java.util.function.Consumer<Boolean> changed, String id, int top) {
        var box = Checkbox.builder(text(label), font).pos(x, y).maxWidth(245).selected(selected)
                .onValueChange((widget, value) -> changed.accept(value)).build();
        box.active = pending == null;
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text(label + "_hint")));
        addContentWidget(box);
    }

    private <T extends AbstractWidget> T addContentWidget(T widget) {
        addRenderableWidget(widget);
        contentWidgets.add(widget);
        contentBaseY.put(widget, widget.getY());
        return widget;
    }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return contentWidgets.contains(widget)
                ? viewport(Math.max(0, (width - Math.min(560, width - 36)) / 2), contentTop,
                Math.min(width, (width + Math.min(560, width - 36)) / 2), contentBottom) : null;
    }

    private void moveContent() {
        for (AbstractWidget widget : contentWidgets)
            widget.setY(contentBaseY.getOrDefault(widget, widget.getY()) - contentScroll.pixels());
    }

    private void saveMultiplierFields() {
        if (taskMultiplierField != null) taskMultiplier = taskMultiplierField.getValue();
        if (rewardMultiplierField != null) rewardMultiplier = rewardMultiplierField.getValue();
        if (experienceMultiplierField != null) experienceMultiplier = experienceMultiplierField.getValue();
    }

    private xtdpotato.xero_quest_journal.core.io.QuestGenerator.GenerationOptions options() {
        return new xtdpotato.xero_quest_journal.core.io.QuestGenerator.GenerationOptions(
                parseMultiplier(taskMultiplier), parseMultiplier(rewardMultiplier),
                parseMultiplier(experienceMultiplier), experienceUnit, bonusExperience,
                itemRewards, advancementTasks, itemTasks, combatTasks, structureTasks, tierItemRewards);
    }

    private static int parseMultiplier(String value) {
        try { return Math.clamp(Integer.parseInt(value), 1, 64); }
        catch (NumberFormatException invalid) { return 1; }
    }
    public void tick() {
        contentScroll.advance();
        moveContent();
        if (pending != null && pending.isDone()) {
            try { status = pending.join(); } catch (Exception failure) { status = "remote_unavailable"; }
            pending = null; init();
        }
    }
    protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, JOURNAL_BACKDROP); super.renderContents(g, x, y, delta);
        int w = Math.min(560, width - 36), left = (width - w) / 2;
        int top = contentTop - contentScroll.pixels();
        g.drawCenteredString(font, title, width / 2, 16, 0xFFF2D29A);
        g.enableScissor(left, contentTop, left + w, contentBottom);
        g.drawString(font, text("mode"), left, top + 10, 0xFFE2D6BD);
        g.drawString(font, text("scope.label"), left, top + 54, 0xFFE2D6BD);
        if (advanced) {
            g.drawString(font, text("task_multiplier"), left, top + 159, 0xFFE2D6BD);
            g.drawString(font, text("item_reward_multiplier"), left, top + 188, 0xFFE2D6BD);
            g.drawString(font, text("experience_multiplier"), left, top + 217, 0xFFE2D6BD);
            g.drawWordWrap(font, text("advanced_hint"), left, top + 352, w, 0xFFB9C2CF);
        } else {
            g.drawWordWrap(font, text(!offer.allowed() ? "permission" : mode + "_description"),
                    left, top + 164, w, 0xFFE5E8ED);
        }
        g.disableScissor();
        if (!status.isEmpty()) g.drawWordWrap(font,
                Component.translatable("screen.xero_quest_journal.action." + status),
                left, height - 58, w, 0xFFF2D29A);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= contentTop && mouseY < contentBottom) {
            contentScroll.wheel(vertical * 34);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        int railX = (width - Math.min(560, width - 36)) / 2
                + Math.min(560, width - 36) - 8;
        if (hasChoicePopup()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && mouseY >= contentTop && mouseY < contentBottom && mouseX >= railX)
            return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && mouseY >= contentTop && mouseY < contentBottom) {
            listDragging = true;
            listMoved = false;
            listStartX = mouseX;
            listStartY = mouseY;
            listStartScroll = contentScroll.target();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (hasChoicePopup()) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (button == 0 && listDragging) {
            if (!listMoved && Math.hypot(mouseX - listStartX, mouseY - listStartY) > 4) listMoved = true;
            if (listMoved) contentScroll.to(listStartScroll + listStartY - mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (hasChoicePopup()) return super.mouseReleased(mouseX, mouseY, button);
        if (button == 0 && listDragging) {
            listDragging = false;
            boolean clicked = !listMoved && mouseY >= contentTop && mouseY < contentBottom;
            if (clicked) {
                super.mouseClicked(listStartX, listStartY, button);
                super.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    public void onClose() {
        ClientQuestProgress.chooseGenerationMode("dismiss", List.of(), false, options());
        closeTo(parent);
    }
    public boolean isPauseScreen() { return false; }
}
