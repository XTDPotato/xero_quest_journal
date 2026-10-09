package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class QuestTemplateEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final QuestTemplate original;
    private final String expectedHash;
    private final Map<String, String> values = new LinkedHashMap<>();
    private List<QuestTemplate.Element> elements;
    private List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger> defaultConditions;
    private List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward> defaultRewards;
    private final Map<AbstractWidget, Integer> fields = new LinkedHashMap<>();
    private final Set<AbstractWidget> unlabeled = new HashSet<>();
    private CompletableFuture<String> pending;
    private String error = "";
    private int scroll;
    private boolean draggingScrollbar;
    private double scrollbarGrab;

    public QuestTemplateEditorScreen(Screen parent, QuestTemplate original) {
        super(text("edit"));
        this.parent = parent;
        this.original = original;
        expectedHash = original == null ? "" : QuestTemplateStore.fingerprint(original.id());
        elements = original == null ? QuestTemplateStore.defaultElements() : original.elements();
        defaultConditions = original == null ? List.of() : original.defaultConditions();
        defaultRewards = original == null ? List.of() : original.defaultRewards();
        values.put("id", original == null ? xtdpotato.xero_quest_journal.core.io.QuestAuthoring.newId() : original.id());
        values.put("name", original == null ? "" : original.name());
        values.put("description", original == null ? "" : original.description());
        values.put("type", original == null ? "commission" : original.type());
        values.put("group", original == null ? "commission" : original.group());
        values.put("icon", original == null ? "" : original.icon());
        values.put("reward_mode", original == null ? "default" : original.rewardMode());
        values.put("default_conditions", "");
        values.put("default_rewards", "");
        values.put("css", original == null ? "" : original.css());
    }

    static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.template." + key);
    }

    private int left() { return (width - formWidth()) / 2; }
    private int formWidth() { return Math.min(480, width - 32); }
    private int bottom() { return height - 68; }
    private Viewport fieldViewport() { return new Viewport(left() - 4, 32, left() + formWidth() + 4, bottom()); }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return fields.containsKey(widget) ? fieldViewport() : null;
    }
    private int maxScroll() { return Math.max(0, 50 + values.size() * 38 - bottom()); }
    private int scrollbarX() { return left() + formWidth() + 6; }
    private int thumbHeight() {
        int viewport = Math.max(1, bottom() - 32);
        return Math.max(12, viewport * viewport / (viewport + maxScroll()));
    }
    private int thumbY() {
        return 32 + (int) ((bottom() - 32 - thumbHeight()) * (double) scroll / Math.max(1, maxScroll()));
    }

    @Override
    protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets();
        fields.clear();
        unlabeled.clear();
        int index = 0;
        for (var entry : values.entrySet()) {
            int y = 50 + index++ * 38;
            String key = entry.getKey();
            if (key.equals("default_conditions") || key.equals("default_rewards")) {
                var button = new StyledButton(left(), y, formWidth(), 20,
                        text(key), ignored -> minecraft.setScreen(new QuestTemplateRulesScreen(
                        this, key.equals("default_conditions"), defaultConditions, defaultRewards)),
                        styles, "template-" + key, "toolbar-button");
                button.setMessage(Component.translatable(
                        "screen.xero_quest_journal.template." + key,
                        key.equals("default_conditions") ? defaultConditions.size() : defaultRewards.size()));
                button.active = pending == null;
                fields.put(addRenderableWidget(button), y);
                continue;
            }
            if (key.equals("type") || key.equals("reward_mode")) {
                var options = key.equals("type") ? ClientQuestDefinitions.types().stream()
                        .map(xtdpotato.xero_quest_journal.core.model.QuestType::id).toList()
                        : List.of("default", "manual", "automatic");
                if (options.isEmpty()) options = List.of("commission");
                var choice = new StyledChoiceButton<>(left(), y, formWidth(), 20, text(key), options,
                        entry.getValue(), value -> key.equals("type")
                        ? Component.literal(ClientQuestDefinitions.types().stream()
                        .filter(type -> type.id().equals(value)).map(xtdpotato.xero_quest_journal.core.model.QuestType::name)
                        .findFirst().orElse(value))
                        : Component.translatable("screen.xero_quest_journal.reward_mode." + value),
                        value -> values.put(key, value), styles, "template-" + key);
                choice.active = pending == null;
                fields.put(addRenderableWidget(choice), y);
                continue;
            }
            boolean resource = List.of("icon").contains(key);
            var box = new SuggestedEditBox(this, font, left(), y, formWidth() - (resource ? 28 : 0), 20, text(key), key);
            if (resource) box.withoutMenu();
            box.setMaxLength(key.equals("css") ? 32768 : key.equals("description") ? 4096 : resource ? 8192 : 128);
            box.setValue(entry.getValue());
            box.setResponder(value -> values.put(entry.getKey(), value));
            box.setEditable(pending == null && !(entry.getKey().equals("id") && original != null));
            fields.put(addRenderableWidget(box), y);
            if (resource) {
                var pick = new StyledButton(left() + formWidth() - 24, y, 24, 20, Component.literal("..."),
                        ignored -> minecraft.setScreen(QuestScreen.pickResource(this, values.get(key), !key.equals("icon"),
                                value -> values.put(key, value))), styles, "template-pick-" + key, "toolbar-button");
                pick.leadingIcon(entry.getValue());
                pick.setTooltip(Tooltip.create(Component.translatable("screen.xero_quest_journal.icon_picker.choose")));
                pick.active = pending == null;
                fields.put(addRenderableWidget(pick), y);
                unlabeled.add(pick);
            }
        }
        button("controls", left(), height - 56, formWidth(), () ->
                minecraft.setScreen(new QuestTemplateControlsScreen(this, elements)));
        button("save", left(), height - 28, (formWidth() - 8) / 2, this::save);
        button("cancel", left() + (formWidth() + 8) / 2, height - 28, (formWidth() - 8) / 2, this::onClose);
        updateScroll();
    }

    private void button(String key, int x, int y, int w, Runnable action) {
        var button = new StyledButton(x, y, w, 22, text(key),
                ignored -> action.run(), styles, "template-" + key, "toolbar-button");
        button.active = pending == null;
        addRenderableWidget(button);
    }

    void updateTemplateElements(List<QuestTemplate.Element> updated) {
        elements = List.copyOf(updated);
    }

    QuestTemplate previewTemplate(List<QuestTemplate.Element> controls) {
        return new QuestTemplate(values.get("id"), values.get("name"), values.get("description"),
                values.get("type"), values.get("icon"), original == null ? "minecraft:oak_log" : original.item(),
                original == null ? 1 : original.count(), "", 1, values.get("reward_mode"), values.get("css"),
                controls, defaultConditions, defaultRewards, values.get("group"), false);
    }

    void updateTemplateRules(
            List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger> conditions,
            List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward> rewards) {
        defaultConditions = List.copyOf(conditions);
        defaultRewards = List.copyOf(rewards);
        init();
    }

    private void save() {
        if (pending != null) return;
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null || !minecraft.player.hasPermissions(2)) {
            error = "permission";
            return;
        }
        try {
            String id = values.get("id").strip();
            if (values.get("name").isBlank()) throw new IllegalArgumentException("Missing name");
            QuestTemplate next = new QuestTemplate(id, values.get("name"), values.get("description"),
                    values.get("type").strip(), values.get("icon").strip(),
                    original == null ? "minecraft:oak_log" : original.item(),
                    original == null ? 1 : original.count(),
                    original == null ? "" : original.rewardItem(),
                    original == null ? 1 : original.rewardCount(),
                    values.get("reward_mode"), values.get("css"), elements, defaultConditions, defaultRewards,
                    values.get("group"), false);
            UUID playerId = minecraft.player.getUUID();
            pending = CompletableFuture.supplyAsync(() ->
                    QuestTemplateStore.save(server.getPlayerList().getPlayer(playerId), next, expectedHash), server);
            error = "";
            init();
        } catch (IllegalArgumentException invalid) {
            error = "invalid";
        }
    }

    @Override
    public void tick() {
        if (pending == null || !pending.isDone()) return;
        try { error = pending.join(); } catch (RuntimeException failure) { error = "save"; }
        pending = null;
        if (error.isEmpty()) minecraft.setScreen(parent);
        else init();
    }

    private void updateScroll() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        fields.forEach((box, y) -> {
            box.setY(y - scroll);
            box.visible = fieldViewport().intersects(box);
            if (!box.visible && getFocused() == box) setFocused(null);
        });
    }

    private final SmoothScroll scrollMotion = new SmoothScroll();
    private boolean draggingContent, contentMoved;
    private double contentStartY, contentStartX, contentStartScroll;

    private void dragScrollbar(double y) {
        scroll = (int) Math.round((y - 32 - scrollbarGrab) * maxScroll()
                / Math.max(1, bottom() - 32 - thumbHeight()));
        updateScroll();
        scrollMotion.limit(maxScroll());
        scrollMotion.jump(scroll);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        if (button == 0 && maxScroll() > 0 && x >= scrollbarX() - 2 && x < scrollbarX() + 6
                && y >= 32 && y < bottom()) {
            draggingScrollbar = true;
            scrollbarGrab = y >= thumbY() && y < thumbY() + thumbHeight() ? y - thumbY() : thumbHeight() / 2.0;
            dragScrollbar(y);
            return true;
        }
        if (button == 0 && fieldViewport().contains(x, y) && !(getChildAt(x, y).orElse(null) instanceof EditBox)) {
            draggingContent = true; contentMoved = false;
            contentStartX = x; contentStartY = y; contentStartScroll = scroll;
            scrollMotion.limit(maxScroll()); scrollMotion.jump(scroll);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (button == 0 && draggingContent) {
            contentMoved |= Math.abs(y - contentStartY) >= 4;
            if (contentMoved) {
                scrollMotion.jump(contentStartScroll + contentStartY - y);
                scroll = scrollMotion.pixels(); updateScroll();
            }
            return true;
        }
        if (button == 0 && draggingScrollbar) { dragScrollbar(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (releaseChoicePopup(x, y, button)) return true;
        if (button == 0 && draggingContent) {
            draggingContent = false;
            if (!contentMoved && Math.abs(x - contentStartX) < 4) {
                super.mouseClicked(contentStartX, contentStartY, button); super.mouseReleased(x, y, button);
            }
            return true;
        }
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (fieldViewport().contains(x, y)) {
            scrollMotion.limit(maxScroll());
            scrollMotion.wheel(vertical * 30);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        scrollMotion.limit(maxScroll());
        if (hasChoicePopup()) scrollMotion.freeze();
        scrollMotion.advance();
        scroll = scrollMotion.pixels();
        updateScroll();
        super.renderContents(g, x, y, delta);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        g.enableScissor(left() - 4, 32, left() + formWidth() + 4, bottom());
        fields.forEach((box, unused) -> {
            if (box.visible && !unlabeled.contains(box)) {
                Component caption = box instanceof StyledChoiceButton<?>
                        ? text(((StyledButton) box).styleId().substring("template-".length())) : box.getMessage();
                g.drawString(font, caption, box.getX(), box.getY() - 12, 0xFFE4EAF2);
            }
        });
        g.disableScissor();
        if (maxScroll() > 0) {
            g.fill(scrollbarX(), 32, scrollbarX() + 4, bottom(), 0xFF555B64);
            g.fill(scrollbarX(), thumbY(), scrollbarX() + 4, thumbY() + thumbHeight(), 0xFFE4E7E8);
        }
        if (!error.isBlank()) g.drawCenteredString(font, text("error." + error), width / 2, height - 67, 0xFFFF7777);
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        super.renderBackground(g, x, y, delta);
        backdrop(g, 0xE51D2025);
    }

    @Override public void onClose() { if (pending == null) closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
