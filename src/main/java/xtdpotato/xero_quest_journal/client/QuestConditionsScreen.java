package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger;
import xtdpotato.xero_quest_journal.core.runtime.QuestConditions;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Tree authoring changes conditions only, preserving rewards, identity and task layout. */
public final class QuestConditionsScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final String questId;
    private final String expectedHash;
    private final Consumer<List<Trigger>> draftSaved;
    private final List<Draft> roots = new ArrayList<>();
    private final List<Row> visible = new ArrayList<>();
    private int scroll;
    private final SmoothScroll motion = new SmoothScroll();
    private boolean listDragging, listMoved;
    private double listStartX, listStartY, listStartScroll;
    private boolean dragging;
    private double dragOffset;
    private String error;
    private ButtonStyleSheet styles;
    private CompletableFuture<QuestAuthoring.Result> pending;
    private final Set<AbstractWidget> rowWidgets = new HashSet<>();
    private record Row(Draft draft, List<Draft> siblings, int depth) {}
    private static final class Draft {
        String type, target, count;
        final String buttonText, buttonShape;
        final List<Draft> children = new ArrayList<>();
        Draft(Trigger t) {
            type = t.type().equals("item_get") ? "item_hold" : t.type();
            target = t.item();
            count = Integer.toString(t.count());
            buttonText = t.buttonText();
            buttonShape = t.buttonShape();
            t.children().forEach(c -> children.add(new Draft(c)));
        }
        Trigger value() { return new Trigger(type, target.strip(), Integer.parseInt(count.strip()),
                children.stream().map(Draft::value).toList(), buttonText, buttonShape); }
        boolean group() { return List.of("all", "any", "count", "ratio").contains(type); }
    }
    public QuestConditionsScreen(Screen parent, String questId) throws Exception {
        super(text("conditions.edit"));
        this.parent = parent;
        this.questId = questId;
        this.draftSaved = null;
        String source = QuestRepository.source(questId);
        expectedHash = QuestRepository.fingerprint(source);
        QuestRepository.parseDefinition(QuestRepository.fileOf(questId), source).triggers()
                .forEach(t -> roots.add(new Draft(t)));
    }
    public QuestConditionsScreen(Screen parent, String questId, List<Trigger> conditions,
                                 Consumer<List<Trigger>> draftSaved) {
        super(text("conditions.edit"));
        this.parent = parent;
        this.questId = questId;
        this.expectedHash = "";
        this.draftSaved = Objects.requireNonNull(draftSaved);
        conditions.forEach(t -> roots.add(new Draft(t)));
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }
    private int panelWidth() { return Math.min(520, width - 24); }
    private int left() { return (width - panelWidth()) / 2; }
    private int rows() { return Math.max(1, (height - 126) / 48); }
    private Viewport rowsViewport() { return new Viewport(left(), 68, left() + panelWidth(), 68 + rows() * 48); }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return rowWidgets.contains(widget) ? rowsViewport() : null;
    }
    private int thumbHeight() { return Math.max(10, rows() * 48 * rows() / Math.max(1, visible.size())); }
    private int thumbTop() { return 68 + (rows() * 48 - thumbHeight()) * scroll / Math.max(1, (visible.size() - rows()) * 48); }
    private void flatten(List<Draft> list, int depth) {
        for (Draft draft : list) {
            visible.add(new Row(draft, list, depth));
            flatten(draft.children, depth + 1);
        }
    }
    @Override protected void init() {
        clearWidgets();
        rowWidgets.clear();
        styles = ButtonStyleSheet.load();
        visible.clear();
        flatten(roots, 0);
        motion.limit((visible.size() - rows()) * 48);
        motion.jump(scroll);
        scroll = motion.pixels();
        int x = left(), w = panelWidth();
        button("conditions-add", "+", "conditions.add", x + w - 24, 34, 24, () -> {
            roots.add(new Draft(new Trigger("confirm", "", 1)));
            scroll = Math.max(0, visible.size() + 1 - rows()) * 48;
            init();
        }).active &= visible.size() < 128;
        for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
            int index = rowIndex;
            Row row = visible.get(index);
            Draft draft = row.draft();
            int indent = Math.min(row.depth() * 10, Math.max(0, w - 240));
            int lx = x + indent, rw = w - indent, y = 68 + index * 48 - scroll;
            int firstWidget = children().size();
            List<List<String>> conditionGroups = List.of(
                    List.of("confirm"),
                    List.of("item_hold", "item_submit", "item_acquire"),
                    List.of("kill"),
                    List.of("block_break", "block_interact", "dimension", "region", "location"),
                    List.of("advancement", "stage", "quest", "unlock", "lock"),
                    List.of("event", "script"),
                    List.of("all", "any", "count", "ratio"));
            List<String> typeOptions = conditionGroups.stream().flatMap(List::stream).toList();
            Map<String, String> conditionOptionGroups = new LinkedHashMap<>();
            for (int group = 0; group < conditionGroups.size(); group++)
                for (String type : conditionGroups.get(group))
                    conditionOptionGroups.put(type, List.of("manual", "inventory", "combat", "world",
                            "quest", "events", "logic").get(group));
            List<String> specialGroups = conditionGroups.getLast();
            addRenderableWidget(new StyledChoiceButton<>(lx, y, rw - 86, 20, text("conditions.type"),
                    typeOptions, draft.type, value -> text("condition_type." + value),
                    value -> {
                        draft.type = value;
                        if (!draft.group()) draft.children.clear();
                        draft.target = value.startsWith("item_") ? "minecraft:diamond"
                                : value.equals("dimension") ? "minecraft:overworld" : "";
                        draft.count = "1";
                        init();
                    }, styles, "condition-type-" + index)
                    .groupedOptions(conditionOptionGroups).distinctOptions(specialGroups)).active = pending == null;
            button("condition-child-" + index, "+", "conditions.child", x + w - 80, y, 24, () -> {
                draft.children.add(new Draft(new Trigger("confirm", "", 1)));
                init();
            }).active &= draft.group() && row.depth() < 15 && visible.size() < 128;
            button("condition-pick-" + index, draft.type.equals("script") ? "\u270e" : "...",
                    draft.type.equals("script") ? "graph.edit_script" : "icon_picker.title",
                    x + w - 52, y, 24, () ->
                    {
                        if (draft.type.equals("quest") || draft.type.equals("unlock") || draft.type.equals("lock")) {
                            minecraft.setScreen(new QuestTaskPickerScreen(this, draft.target,
                                    draft.type.equals("quest") ? questId : "",
                                    !draft.type.equals("quest"),
                                    ids -> {
                                        draft.target = String.join(", ", ids);
                                        init();
                                    }));
                        } else if (draft.type.equals("location")) {
                            minecraft.setScreen(new QuestLocationScreen(this, draft.target, value -> draft.target = value));
                        } else if (draft.type.startsWith("item_")) {
                            minecraft.setScreen(QuestScreen.pickResource(this, draft.target, true, true,
                                    value -> draft.target = value));
                        } else if (draft.type.equals("event")) {
                            minecraft.setScreen(new QuestValuePickerScreen(this, text("condition_type.event").getString(),
                                    "event", () -> QuestSuggestions.options("event", draft.target),
                                    draft.target, value -> { draft.target = value; init(); }));
                        } else if (draft.type.equals("script")) {
                            try { minecraft.setScreen(new QuestScriptFileScreen(this, draft.target)); }
                            catch (Exception invalid) { error = "graph.script_open_failed"; }
                        }
                    }).active &= draft.type.equals("quest") || draft.type.equals("unlock")
                    || draft.type.equals("lock") || draft.type.equals("location")
                    || draft.type.startsWith("item_") || draft.type.equals("event") || draft.type.equals("script");
            button("condition-delete-" + index, "\u00d7", "delete", x + w - 24, y, 24, () -> {
                row.siblings().remove(draft);
                init();
            });
            int targetWidth = rw - 70;
            var target = new SuggestedEditBox(this, font, lx, y + 22, Math.max(32, targetWidth), 20,
                    text("conditions.target"), draft.type);
            if (draft.type.startsWith("item_") || draft.type.equals("quest")
                    || draft.type.equals("unlock") || draft.type.equals("lock") || draft.type.equals("location")
                    || draft.type.equals("event"))
                target.withoutMenu();
            target.setMaxLength(8192);
            target.setValue(draft.type.equals("location") ? locationSummary(draft.target) : draft.target);
            if (!draft.type.equals("location")) target.setResponder(value -> draft.target = value);
            target.setEditable(pending == null && !draft.group() && !draft.type.equals("confirm")
                    && !draft.type.equals("location"));
            addRenderableWidget(target);
            if (!draft.type.equals("script")) {
                EditBox count = new SuggestedEditBox(this, font, x + w - 64, y + 22, 64, 20, text("conditions.count"), "count");
                count.setMaxLength(7);
                count.setValue(draft.count);
                count.setResponder(value -> draft.count = value);
                count.setEditable(pending == null);
                addRenderableWidget(count);
            }
            children().subList(firstWidget, children().size()).stream()
                    .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).forEach(rowWidgets::add);
        }
        var save = new StyledButton(x, height - 30, (w - 6) / 2, 22, text(pending == null ? "save" : "saving"),
                ignored -> save(), styles, "conditions-save");
        save.active = pending == null;
        addRenderableWidget(save);
        var cancel = new StyledButton(x + (w + 6) / 2, height - 30, (w - 6) / 2, 22, text("cancel"),
                ignored -> onClose(), styles, "conditions-cancel");
        cancel.active = pending == null;
        addRenderableWidget(cancel);
    }
    private StyledButton button(String id, String symbol, String tooltip, int x, int y, int w, Runnable action) {
        var button = new StyledButton(x, y, w, 20, Component.literal(symbol), ignored -> action.run(), styles, id);
        button.setTooltip(Tooltip.create(text(tooltip)));
        button.active = pending == null;
        return addRenderableWidget(button);
    }

    private String locationSummary(String encoded) {
        var value = xtdpotato.xero_quest_journal.core.runtime.QuestLocation.parse(encoded);
        return value == null ? text("location.choose_target").getString() : value.summary();
    }
    private void save() {
        if (pending != null) return;
        if (minecraft.player == null || !minecraft.player.hasPermissions(2) || minecraft.getSingleplayerServer() == null) {
            error = "create.error.permission";
            return;
        }
        try {
            var values = roots.stream().map(Draft::value).toList();
            if (!QuestConditions.valid(values)) {
                error = "create.error.condition";
                return;
            }
            if (draftSaved != null) {
                draftSaved.accept(values);
                minecraft.setScreen(parent);
                return;
            }
            var server = minecraft.getSingleplayerServer();
            var id = minecraft.player.getUUID();
            pending = CompletableFuture.supplyAsync(() -> QuestAuthoring.editConditions(
                    server.getPlayerList().getPlayer(id), questId, expectedHash, values), server);
            error = null;
            init();
        } catch (IllegalArgumentException failure) { error = "create.error.condition"; }
    }
    @Override public void tick() {
        if (pending == null || !pending.isDone()) return;
        try {
            var result = pending.join();
            pending = null;
            if (result.successful()) minecraft.setScreen(parent);
            else { error = result.error().replace("screen.xero_quest_journal.", ""); init(); }
        } catch (Exception failure) { pending = null; error = "create.error.save"; init(); }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (pending == null && rowsViewport().contains(x, y)) {
            motion.wheel(vertical * 48);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    private void scrollTo(double y) {
        motion.jump((y - 68 - dragOffset) * Math.max(0, (visible.size() - rows()) * 48)
                / Math.max(1, rows() * 48 - thumbHeight()));
        moveRows();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        if (button == 0 && pending == null && visible.size() > rows() && x >= left() + panelWidth()
                && x < left() + panelWidth() + 9 && y >= 68 && y < 68 + rows() * 48) {
            dragging = true;
            dragOffset = y >= thumbTop() && y < thumbTop() + thumbHeight() ? y - thumbTop() : thumbHeight() / 2.0;
            scrollTo(y);
            return true;
        }
        if (button == 0 && pending == null && rowsViewport().contains(x, y)
                && !(getChildAt(x, y).orElse(null) instanceof EditBox)) {
            listDragging = true;
            listMoved = false;
            listStartX = x; listStartY = y; listStartScroll = motion.position();
            motion.freeze();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (button == 0 && listDragging) {
            listMoved |= Math.abs(y - listStartY) >= 4;
            if (listMoved) { motion.jump(listStartScroll + listStartY - y); moveRows(); }
            return true;
        }
        if (button == 0 && dragging) { scrollTo(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (releaseChoicePopup(x, y, button)) return true;
        if (button == 0 && listDragging) {
            listDragging = false;
            if (!listMoved && rowsViewport().contains(x, y) && Math.abs(x - listStartX) < 4) {
                super.mouseClicked(listStartX, listStartY, button);
                super.mouseReleased(x, y, button);
            }
            return true;
        }
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float tick) {
        if (hasChoicePopup()) motion.freeze();
        motion.advance();
        moveRows();
        super.renderContents(g, x, y, tick);
        g.drawCenteredString(font, title, width / 2, 12, -1);
        g.drawString(font, text("conditions.target"), left(), 43, 0xFFCCD5DE);
        if (visible.isEmpty()) g.drawString(font, text("conditions.empty"), left(), 80, 0xFFCCD5DE);
        if (visible.size() > rows()) {
            int right = left() + panelWidth() + 3;
            g.fill(right, 68, right + 3, 68 + rows() * 48, 0xFF303741);
            g.fill(right, thumbTop(), right + 3, thumbTop() + thumbHeight(), 0xFF9DB4D2);
        }
        if (error != null) g.drawString(font, font.plainSubstrByWidth(text(error).getString(), panelWidth()),
                left(), height - 46, 0xFFFF7777);
    }
    private void moveRows() {
        int next = motion.pixels(), delta = scroll - next;
        rowWidgets.forEach(widget -> widget.setY(widget.getY() + delta));
        scroll = next;
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) { backdrop(g, 0xF01C2026); }
    @Override public void onClose() { if (pending == null) closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected int animationHeaderBottom() { return 60; }
    @Override protected int animationFooterTop() { return height - 52; }
}
