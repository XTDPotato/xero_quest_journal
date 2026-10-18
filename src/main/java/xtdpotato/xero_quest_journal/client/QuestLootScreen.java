package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestLootStore;
import xtdpotato.xero_quest_journal.core.model.LootDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** Authoring screen for reusable weighted loot tables. */
public final class QuestLootScreen extends AnimatedQuestScreen {
    private static final int TABLE_ROW_HEIGHT = 30;
    private final Screen parent;
    private final Consumer<String> selectedCallback;
    private final List<LootDefinition> tables = new ArrayList<>();
    private final List<Draft> entries = new ArrayList<>();
    private final Set<String> selectedTables = new LinkedHashSet<>();
    private final Set<String> rangeTables = new LinkedHashSet<>();
    private final Set<AbstractWidget> tableWidgets = new LinkedHashSet<>();
    private final Set<AbstractWidget> entryWidgets = new LinkedHashSet<>();
    private final Set<AbstractWidget> metadataWidgets = new LinkedHashSet<>();
    private final Map<Integer, LootWeightSlider> weightSliders = new LinkedHashMap<>();
    private final Map<Integer, EditBox> weightBoxes = new LinkedHashMap<>();
    private final Deque<EditorSnapshot> undo = new ArrayDeque<>();
    private final Deque<EditorSnapshot> redo = new ArrayDeque<>();
    private final SmoothScroll tableMotion = new SmoothScroll();
    private final SmoothScroll entryMotion = new SmoothScroll();
    private ButtonStyleSheet styles;
    private String selectedId = "";
    private String rangeAnchor = "";
    private String id = "";
    private String name = "";
    private String error;
    private LootDefinition.Mode mode = LootDefinition.Mode.RANDOM;
    private String rolls = "1";
    private String choiceTitle = "";
    private String displayIcon = LootDefinition.DEFAULT_DISPLAY_ICON;
    private String entrySearch = "";
    private String status;
    private long statusUntil;
    private boolean syncingWeightFields;
    private boolean multiSelect;
    private int contextIndex = -1;
    private int pressedContextAction = -1;
    private int pressedTableIndex = -1;
    private int contextX;
    private int contextY;
    private SuggestedEditBox idBox;
    private SuggestedEditBox nameBox;
    private int lastTableScroll;
    private int lastEntryScroll;
    private int renderedTableStart;
    private int renderedEntryStart;

    private record EditorSnapshot(String id, String name, LootDefinition.Mode mode, String rolls, String choiceTitle,
                                  String displayIcon, List<Draft> entries) {}

    public QuestLootScreen(Screen parent) {
        this(parent, "", null);
    }

    public QuestLootScreen(Screen parent, String initialId, Consumer<String> selectedCallback) {
        super(text("loot.title"));
        this.parent = parent;
        this.selectedCallback = selectedCallback;
        reloadTables();
        String initial = initialId == null ? "" : initialId;
        if (!initial.isBlank() && tables.stream().anyMatch(table -> table.id().equals(initial))) {
            select(initial);
        } else if (!tables.isEmpty()) {
            select(tables.getFirst().id());
        } else {
            createDraft();
        }
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }

    private void reloadTables() {
        try {
            QuestLootStore.reload();
            tables.clear();
            tables.addAll(QuestLootStore.all());
        } catch (RuntimeException failure) {
            error = "loot.load_error";
        }
    }

    private void select(String tableId) {
        LootDefinition table = QuestLootStore.find(tableId);
        if (table == null) return;
        selectedId = table.id();
        selectedTables.clear();
        selectedTables.add(selectedId);
        rangeTables.clear();
        rangeAnchor = selectedId;
        loadDraft(table);
    }

    private void loadDraft(LootDefinition table) {
        undo.clear();
        redo.clear();
        id = table.id();
        name = table.name();
        mode = table.mode();
        rolls = Integer.toString(table.rolls());
        choiceTitle = table.choiceTitle();
        displayIcon = table.displayIcon();
        entries.clear();
        table.entries().forEach(entry -> entries.add(new Draft(entry)));
        entryMotion.jump(0);
    }

    private void createDraft() {
        undo.clear();
        redo.clear();
        selectedId = "";
        selectedTables.clear();
        rangeTables.clear();
        id = QuestAuthoring.newId();
        name = id;
        mode = LootDefinition.Mode.RANDOM;
        rolls = "1";
        choiceTitle = "";
        displayIcon = LootDefinition.DEFAULT_DISPLAY_ICON;
        entries.clear();
        entries.add(new Draft(new LootDefinition.Entry("entry-1", "item",
                "minecraft:diamond", 1, 100, "always", true)));
        entryMotion.jump(0);
    }

    private int leftPanel() {
        return Math.max(176, Math.min(250, width / 4));
    }

    private int editorLeft() {
        return leftPanel() + 18;
    }

    private int editorWidth() {
        return Math.max(220, width - editorLeft() - 24);
    }

    private int tableViewportTop() { return 66; }
    private int tableViewportBottom() { return Math.max(tableViewportTop() + 44, height - 12); }
    private boolean scrollWholeEditor() { return height < 440; }
    private int metadataTopOffset() { return scrollWholeEditor() ? 14 : 0; }
    private int rowsViewportTop() { return scrollWholeEditor() ? 40 : 220; }
    private int entryContentTop() { return 248 + metadataTopOffset(); }
    private int rowsViewportBottom() { return Math.max(rowsViewportTop() + 20, height - 62); }
    private boolean stackedEntries() { return editorWidth() < 560; }
    private int entryHeight() { return stackedEntries() ? 166 : 128; }
    private int tableMaxScroll() {
        return Math.max(0, tables.size() * TABLE_ROW_HEIGHT - (tableViewportBottom() - tableViewportTop()));
    }
    private int entryMaxScroll() {
        return Math.max(0, entryContentTop() + filteredEntries().size() * entryHeight() - rowsViewportBottom());
    }
    private int firstVisibleEntry() {
        return Math.max(0, (entryMotion.pixels() - (entryContentTop() - rowsViewportTop())) / entryHeight());
    }

    @Override
    protected Viewport widgetViewport(AbstractWidget widget) {
        if (tableWidgets.contains(widget)) {
            return new Viewport(8, tableViewportTop(), leftPanel() - 10, tableViewportBottom());
        }
        if (entryWidgets.contains(widget) || scrollWholeEditor() && metadataWidgets.contains(widget)) {
            return new Viewport(editorLeft(), rowsViewportTop(), width - 20, rowsViewportBottom());
        }
        return null;
    }

    @Override
    protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets();
        tableWidgets.clear();
        entryWidgets.clear();
        metadataWidgets.clear();
        weightSliders.clear();
        weightBoxes.clear();
        styles = ButtonStyleSheet.load();
        int divider = leftPanel();
        int listWidth = divider - 20;
        addRenderableWidget(new StyledButton(10, 12, Math.max(90, listWidth - 30), 22, text("loot.new"),
                ignored -> {
                    createDraft();
                    init();
                }, styles, "loot-new"));
        StyledButton multi = new StyledButton(divider - 34, 12, 24, 22,
                Component.literal(multiSelect ? "✓" : "☷"), ignored -> {
            multiSelect = !multiSelect;
            if (!multiSelect && selectedTables.size() > 1) {
                selectedTables.clear();
                if (!selectedId.isBlank()) selectedTables.add(selectedId);
            }
            init();
        }, styles, "loot-multiselect");
        multi.setTooltip(Tooltip.create(text("loot.multiselect")));
        addRenderableWidget(multi);

        tableMotion.limit(tableMaxScroll());
        int firstTable = Math.max(0, tableMotion.pixels() / TABLE_ROW_HEIGHT);
        renderedTableStart = firstTable;
        int tableRows = (tableViewportBottom() - tableViewportTop()) / TABLE_ROW_HEIGHT + 2;
        for (int index = firstTable; index < Math.min(tables.size(), firstTable + tableRows); index++) {
            int row = index;
            LootDefinition table = tables.get(index);
            TableButton button = new TableButton(10,
                    tableViewportTop() + index * TABLE_ROW_HEIGHT - tableMotion.pixels(),
                    Math.max(80, listWidth - 8), 26, Component.literal(table.name()),
                    ignored -> selectTable(row, false), styles, "loot-table-" + index);
            button.setSelectionColor(selectionColor(table.id()));
            button.leadingIcon(table.displayIcon());
            button.setTooltip(Tooltip.create(Component.literal(table.id())));
            addRenderableWidget(button);
            tableWidgets.add(button);
        }
        addRenderableWidget(new ScrollRail(divider - 14, tableViewportTop(),
                tableViewportBottom() - tableViewportTop(), tableMotion));

        int x = editorLeft();
        int w = editorWidth();
        idBox = new SuggestedEditBox(this, font, x, 44, Math.max(80, w / 2 - 12), 20,
                text("loot.id"), "loot.id");
        idBox.withoutMenu();
        idBox.setMaxLength(128);
        idBox.setValue(id);
        idBox.setResponder(value -> { if (!id.equals(value)) pushUndo(); id = value; });
        addRenderableWidget(idBox);
        nameBox = new SuggestedEditBox(this, font, x + w / 2 + 4, 44, Math.max(80, w / 2 - 12), 20,
                text("loot.name"), "loot.name");
        nameBox.withoutMenu();
        nameBox.setMaxLength(256);
        nameBox.setValue(name);
        nameBox.setResponder(value -> { if (!name.equals(value)) pushUndo(); name = value; });
        addRenderableWidget(nameBox);
        SuggestedEditBox choiceTitleBox = new SuggestedEditBox(this, font, x, 106, w, 20,
                text("loot.choice_title"), "loot.choice_title");
        choiceTitleBox.withoutMenu();
        choiceTitleBox.setMaxLength(256);
        choiceTitleBox.setValue(choiceTitle);
        choiceTitleBox.setHint(text("loot.choice_title"));
        choiceTitleBox.setResponder(value -> { if (!choiceTitle.equals(value)) pushUndo(); choiceTitle = value; });
        addRenderableWidget(choiceTitleBox);
        StyledButton iconPicker = new StyledButton(x, 136, Math.max(90, w - 34), 22,
                text("loot.display_icon"), ignored -> minecraft.setScreen(QuestScreen.pickResource(
                this, displayIcon, false, value -> {
                    String next = LootDefinition.normalizeDisplayIcon(value);
                    if (!displayIcon.equals(next)) pushUndo();
                    displayIcon = next;
                })), styles, "loot-display-icon");
        iconPicker.leadingIcon(displayIcon);
        iconPicker.setTooltip(Tooltip.create(text("loot.display_icon.tooltip")));
        addRenderableWidget(iconPicker);
        StyledButton resetIcon = new EditorIconButton(x + w - 26, 136, text("loot.display_icon.reset"),
                () -> {
                    pushUndo();
                    displayIcon = LootDefinition.DEFAULT_DISPLAY_ICON;
                    init();
                }, styles, "loot-display-icon-reset", "undo");
        resetIcon.active = !displayIcon.equals(LootDefinition.DEFAULT_DISPLAY_ICON);
        resetIcon.setTooltip(Tooltip.create(text("loot.display_icon.reset")));
        addRenderableWidget(resetIcon);
        addRenderableWidget(new EditorIconButton(width - 34, 10, text("close"),
                this::onClose, styles, "loot-close", "close"));
        StyledButton delete = new EditorIconButton(x + 4, height - 34, text("loot.delete"),
                this::confirmDelete, styles, "loot-delete", "delete");
        delete.setTooltip(Tooltip.create(text("loot.delete")));
        delete.active = !selectedId.isBlank();
        addRenderableWidget(delete);

        addRenderableWidget(new StyledChoiceButton<>(x, 78, Math.max(90, w / 2 - 8), 22,
                text("loot.mode"), List.of(LootDefinition.Mode.RANDOM, LootDefinition.Mode.CHOICE), mode,
                value -> text("loot.mode." + value.name().toLowerCase(Locale.ROOT)),
                value -> { if (mode != value) pushUndo(); mode = value; init(); }, styles, "loot-mode"));
        SuggestedEditBox rollBox = new SuggestedEditBox(this, font, x + w / 2 + 4, 78,
                Math.max(80, w / 2 - 4), 22, text("loot.rolls"), "loot.rolls");
        rollBox.withoutMenu();
        rollBox.setMaxLength(3);
        rollBox.setValue(rolls);
        rollBox.setResponder(value -> { if (!rolls.equals(value)) pushUndo(); rolls = value; });
        addRenderableWidget(rollBox);

        entryMotion.limit(entryMaxScroll());
        List<Integer> filtered = filteredEntries();
        int firstEntry = firstVisibleEntry();
        renderedEntryStart = firstEntry;
        int visibleEntries = (rowsViewportBottom() - rowsViewportTop()) / entryHeight() + 2;
        for (int row = firstEntry; row < Math.min(filtered.size(), firstEntry + visibleEntries); row++) {
            int index = filtered.get(row);
            addRow(index, entries.get(index), x, entryContentTop() + row * entryHeight() - entryMotion.pixels(), w);
        }
        addRenderableWidget(new ScrollRail(width - 16, rowsViewportTop(),
                rowsViewportBottom() - rowsViewportTop(), entryMotion));

        EditBox search = new SuggestedEditBox(this, font, x, 176, Math.max(110, w - 88), 20,
                text("loot.search"), "loot.search");
        search.setMaxLength(128);
        search.setValue(entrySearch);
        search.setHint(text("loot.search"));
        search.setResponder(value -> {
            if (entrySearch.equals(value)) return;
            entrySearch = value;
            entryMotion.jump(0);
            init();
            children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .filter(box -> box.getMessage().equals(text("loot.search")))
                    .findFirst().ifPresent(box -> { box.setFocused(true); setFocused(box); });
        });
        addRenderableWidget(search);
        StyledButton addEntry = new StyledButton(x + w - 26, 176, 26, 20, Component.literal("+"),
                ignored -> addEntry(), styles, "loot-add-entry");
        addEntry.setTooltip(Tooltip.create(text("loot.add_entry")));
        addRenderableWidget(addEntry);
        children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getX() >= x && widget.getY() >= 40 && widget.getY() < 200
                        && !entryWidgets.contains(widget) && !(widget instanceof ScrollRail))
                .forEach(metadataWidgets::add);
        if (scrollWholeEditor()) moveWidgets(metadataWidgets, metadataTopOffset() - entryMotion.pixels());
        StyledButton undoButton = new EditorIconButton(width - 94, 10, text("loot.undo"),
                this::undo, styles, "loot-undo", "undo");
        undoButton.active = !undo.isEmpty();
        undoButton.setTooltip(Tooltip.create(text("loot.undo")));
        addRenderableWidget(undoButton);
        StyledButton redoButton = new EditorIconButton(width - 64, 10, text("loot.redo"),
                this::redo, styles, "loot-redo", "redo");
        redoButton.active = !redo.isEmpty();
        redoButton.setTooltip(Tooltip.create(text("loot.redo")));
        addRenderableWidget(redoButton);
        addRenderableWidget(new StyledButton(x + 34, height - 34, 104, 22, text("loot.save"),
                ignored -> save(), styles, "loot-save"));
        if (selectedCallback != null) {
            StyledButton use = new StyledButton(x + 146, height - 34, 104, 22, text("loot.use"),
                    ignored -> useSelected(), styles, "loot-use");
            use.active = !selectedId.isBlank();
            addRenderableWidget(use);
        }
        addRenderableWidget(new StyledButton(width - 122, height - 34, 104, 22, text("cancel"),
                ignored -> onClose(), styles, "loot-cancel"));
        if (status != null && System.currentTimeMillis() > statusUntil) status = null;
        lastTableScroll = tableMotion.pixels();
        lastEntryScroll = entryMotion.pixels();
    }

    private void addRow(int index, Draft draft, int x, int y, int width) {
        int rowWidth = Math.max(180, width - 12);
        int firstWidget = children().size();
        addRenderableWidget(new StyledChoiceButton<>(x + 20, y + 10, 116, 20, text("loot.type"),
                List.of("item", "command", "experience", "levels"), draft.type,
                value -> text("loot.type." + value), value -> {
            pushUndo();
            draft.type = value;
            draft.item = defaultTarget(value);
            draft.quality = "";
            draft.background = "";
            init();
        }, styles, "loot-entry-type-" + index));
        int pickerWidth = draft.type.equals("item") ? 24 : 0;
        int targetX = x + 142;
        int targetWidth = Math.max(70, rowWidth - 152 - pickerWidth);
        EditBox target = new SuggestedEditBox(this, font, targetX, y + 10, targetWidth, 20,
                text("loot.target"), "loot.target");
        target.setMaxLength(8192);
        target.setValue(draft.item);
        target.setResponder(value -> { if (!draft.item.equals(value)) pushUndo(); draft.item = value; });
        addRenderableWidget(target);
        if (draft.type.equals("item")) {
            StyledButton picker = new StyledButton(x + rowWidth - 24, y + 10, 22, 20,
                    Component.literal("..."), ignored -> minecraft.setScreen(QuestScreen.pickResource(
                    this, draft.item, true, true, value -> {
                        pushUndo();
                        draft.item = value;
                        init();
                    })), styles, "loot-entry-pick-" + index);
            picker.setTooltip(Tooltip.create(text("loot.pick_item")));
            addRenderableWidget(picker);
        }

        addField(draft, "count", draft.count, x + 20, y + 48, 68, value -> draft.count = value, index);
        int sliderX = x + 154;
        int sliderWidth = stackedEntries() ? Math.max(60, rowWidth - 164)
                : Math.min(210, Math.max(92, rowWidth / 4));
        if (mode != LootDefinition.Mode.CHOICE) {
            EditBox weightBox = new SuggestedEditBox(this, font, x + 94, y + 48, 54, 20,
                    text("loot.weight"), "loot.weight.value." + index);
            weightBox.setMaxLength(5);
            weightBox.setValue(formatWeight(parseWeight(draft.weight)));
            weightBox.setResponder(value -> applyWeightInput(index, value));
            weightBox.setTooltip(Tooltip.create(text("loot.weight_hint")));
            weightBoxes.put(index, weightBox);
            addRenderableWidget(weightBox);
            LootWeightSlider slider = new LootWeightSlider(sliderX, y + 48, sliderWidth, 20, parseWeight(draft.weight),
                    value -> setWeight(index, value));
            weightSliders.put(index, slider);
            slider.setTooltip(Tooltip.create(text("loot.weight")));
            addRenderableWidget(slider);
        }
        int actionX = x + rowWidth - 54;
        int conditionX = stackedEntries() ? x + 20
                : mode == LootDefinition.Mode.CHOICE ? x + 142 : sliderX + sliderWidth + 12;
        int conditionY = y + (stackedEntries() ? 86 : 48);
        int conditionWidth = Math.max(54, actionX - conditionX - 6);
        EditBox condition = new SuggestedEditBox(this, font, conditionX, conditionY, conditionWidth, 20,
                text("loot.condition"), "loot.condition." + index);
        condition.setMaxLength(512);
        condition.setValue(draft.condition);
        condition.setResponder(value -> { if (!draft.condition.equals(value)) pushUndo(); draft.condition = value; });
        condition.setTooltip(Tooltip.create(text("loot.condition_help")));
        addRenderableWidget(condition);
        StyledButton enabled = new StyledButton(actionX, conditionY, 24, 20,
                Component.literal(draft.enabled ? "✓" : "×"), ignored -> {
            pushUndo();
            draft.enabled = !draft.enabled;
            equalizeEnabledWeights();
            init();
        }, styles, "loot-enabled-" + index);
        enabled.setTooltip(Tooltip.create(text("loot.enabled")));
        addRenderableWidget(enabled);
        StyledButton remove = new StyledButton(actionX + 28, conditionY, 24, 20, Component.literal("×"),
                ignored -> {
                    pushUndo();
                    entries.remove(index);
                    equalizeEnabledWeights();
                    init();
                }, styles, "loot-remove-" + index);
        remove.setTooltip(Tooltip.create(text("loot.remove")));
        addRenderableWidget(remove);

        if (draft.type.equals("item")) {
            int qualityWidth = Math.min(150, Math.max(104, rowWidth / 3));
            int qualityY = y + (stackedEntries() ? 124 : 86);
            addRenderableWidget(new StyledChoiceButton<>(x + 20, qualityY, qualityWidth, 20,
                    text("rewards.quality"), xtdpotato.xero_quest_journal.core.model.QuestRewardQuality.VALUES,
                    draft.quality.isBlank() ? "auto" : draft.quality,
                    value -> text("rewards.quality." + value),
                    value -> { pushUndo(); draft.quality = value.equals("auto") ? "" : value; },
                    styles, "loot-entry-quality-" + index));
            int backgroundX = x + 20 + qualityWidth + 8;
            int backgroundWidth = Math.max(60, x + rowWidth - backgroundX - 8);
            SuggestedEditBox background = new SuggestedEditBox(this, font, backgroundX, qualityY,
                    backgroundWidth, 20, text("rewards.background"), "loot.background." + index);
            background.withoutMenu();
            background.setMaxLength(7);
            background.setHint(text("rewards.background"));
            background.setValue(draft.background);
            background.setTooltip(Tooltip.create(text("rewards.background")));
            background.setResponder(value -> { if (!draft.background.equals(value)) pushUndo(); draft.background = value; });
            addRenderableWidget(background);
        }

        children().subList(firstWidget, children().size()).stream()
                .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).forEach(entryWidgets::add);
    }

    private void addField(Draft draft, String kind, String value, int x, int y, int width,
                          Consumer<String> changed, int index) {
        SuggestedEditBox box = new SuggestedEditBox(this, font, x, y, width, 20,
                text("loot." + kind), "loot." + kind + "." + index);
        box.withoutMenu();
        box.setMaxLength(9);
        box.setValue(value);
        box.setResponder(next -> { if (!value.equals(next)) pushUndo(); changed.accept(next); });
        addRenderableWidget(box);
    }

    private void setWeight(int index, int weight) {
        if (index < 0 || index >= entries.size()) return;
        int[] weights = new int[entries.size()];
        boolean[] enabled = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            weights[i] = parseWeight(entries.get(i).weight);
            enabled[i] = entries.get(i).enabled;
        }
        if (weights[index] == weight) return;
        pushUndo();
        int[] updated = LootWeightDistribution.redistribute(weights, enabled, index, weight);
        for (int i = 0; i < updated.length; i++) entries.get(i).weight = Integer.toString(updated[i]);
        for (var slider : weightSliders.entrySet()) {
            slider.getValue().setValue(updated[slider.getKey()]);
        }
        syncingWeightFields = true;
        try {
            weightBoxes.forEach((row, box) -> box.setValue(formatWeight(updated[row])));
        } finally {
            syncingWeightFields = false;
        }
    }

    private void applyWeightInput(int index, String value) {
        if (syncingWeightFields || mode == LootDefinition.Mode.CHOICE || index < 0 || index >= entries.size()) return;
        try {
            String normalized = value.strip();
            if (!normalized.matches("(?:0\\.[0-9]{2}|1\\.00)")) return;
            double probability = Double.parseDouble(normalized);
            if (Double.isFinite(probability) && probability >= 0 && probability <= 1) {
                setWeight(index, (int) Math.round(probability * 100));
            }
        } catch (NumberFormatException ignored) {
            // Allow an incomplete decimal while the user is typing.
        }
    }

    private static String formatWeight(int value) {
        return String.format(Locale.ROOT, "%.2f", value / 100.0);
    }

    private static int parseWeight(String value) {
        try {
            return Math.clamp(Integer.parseInt(value.strip()), 0, 100);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void equalizeEnabledWeights() {
        int[] weights = new int[entries.size()];
        boolean[] enabled = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            weights[i] = parseWeight(entries.get(i).weight);
            enabled[i] = entries.get(i).enabled;
        }
        int[] updated = LootWeightDistribution.equalize(weights, enabled);
        for (int i = 0; i < entries.size(); i++) entries.get(i).weight = Integer.toString(updated[i]);
    }

    private List<Integer> filteredEntries() {
        String query = entrySearch.strip().toLowerCase(Locale.ROOT);
        List<Integer> filtered = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Draft entry = entries.get(i);
            if (query.isEmpty() || (entry.id + " " + entry.type + " " + entry.item + " " + entry.condition)
                    .toLowerCase(Locale.ROOT).contains(query)) filtered.add(i);
        }
        return filtered;
    }

    private void addEntry() {
        pushUndo();
        entrySearch = "";
        entries.add(new Draft(new LootDefinition.Entry("entry-" + (entries.size() + 1),
                "item", "minecraft:diamond", 1, 1, "always", true)));
        equalizeEnabledWeights();
        entryMotion.limit(entryMaxScroll());
        entryMotion.jump(entryMaxScroll());
        init();
    }

    private EditorSnapshot snapshot() {
        return new EditorSnapshot(id, name, mode, rolls, choiceTitle, displayIcon,
                entries.stream().map(Draft::new).toList());
    }

    private void pushUndo() {
        undo.push(snapshot());
        while (undo.size() > 100) undo.removeLast();
        redo.clear();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
    }

    private void restore(EditorSnapshot snapshot) {
        id = snapshot.id();
        name = snapshot.name();
        mode = snapshot.mode();
        rolls = snapshot.rolls();
        choiceTitle = snapshot.choiceTitle();
        displayIcon = snapshot.displayIcon();
        entries.clear();
        snapshot.entries().forEach(entry -> entries.add(new Draft(entry)));
        error = null;
        init();
    }

    private void selectTable(int index, boolean context) {
        if (index < 0 || index >= tables.size()) return;
        LootDefinition table = tables.get(index);
        if (!context && Screen.hasShiftDown()) {
            int anchor = tableIndex(rangeAnchor);
            if (anchor < 0) anchor = index;
            selectedTables.clear();
            rangeTables.clear();
            for (int i = Math.min(anchor, index); i <= Math.max(anchor, index); i++) {
                selectedTables.add(tables.get(i).id());
                rangeTables.add(tables.get(i).id());
            }
            selectedId = table.id();
        } else if (!context && (Screen.hasControlDown() || multiSelect)) {
            if (!selectedTables.add(table.id())) selectedTables.remove(table.id());
            rangeTables.clear();
            selectedId = table.id();
        } else if (!context) {
            selectedTables.clear();
            selectedTables.add(table.id());
            rangeTables.clear();
            selectedId = table.id();
            rangeAnchor = table.id();
        }
        loadDraft(table);
        init();
    }

    private int tableIndex(String tableId) {
        for (int i = 0; i < tables.size(); i++) if (tables.get(i).id().equals(tableId)) return i;
        return -1;
    }

    private void openContext(int index, int x, int y) {
        if (index < 0 || index >= tables.size()) return;
        if (!selectedTables.contains(tables.get(index).id())) {
            selectedTables.clear();
            selectedTables.add(tables.get(index).id());
            rangeTables.clear();
        }
        selectTable(index, true);
        contextIndex = index;
        contextX = (int) x;
        contextY = (int) y;
    }

    private void confirmDelete() {
        List<String> removing = selectedTables.isEmpty() && !selectedId.isBlank()
                ? List.of(selectedId) : List.copyOf(selectedTables);
        if (removing.isEmpty()) return;
        Component message = Component.translatable("screen.xero_quest_journal.loot.confirm_delete",
                removing.size());
        minecraft.setScreen(new QuestConfirmScreen(this, message, text("delete"), () -> delete(removing)));
    }

    private void delete(List<String> removing) {
        var server = minecraft.getSingleplayerServer();
        var author = server == null || minecraft.player == null ? null
                : server.getPlayerList().getPlayer(minecraft.player.getUUID());
        List<String> failed = new ArrayList<>();
        for (String tableId : removing) {
            try {
                QuestLootStore.delete(author, tableId);
            } catch (RuntimeException failure) {
                failed.add(tableId);
            }
        }
        reloadTables();
        xtdpotato.xero_quest_journal.core.io.QuestRepository.lootPresentationChanged();
        selectedTables.clear();
        for (String tableId : failed) {
            if (tables.stream().anyMatch(table -> table.id().equals(tableId))) selectedTables.add(tableId);
        }
        if (tables.isEmpty()) {
            createDraft();
        } else {
            String next = selectedTables.isEmpty() ? tables.getFirst().id() : selectedTables.iterator().next();
            selectedId = next;
            loadDraft(QuestLootStore.find(next));
        }
        error = failed.isEmpty() ? null : author == null || !author.hasPermissions(2)
                ? "create.error.permission" : "loot.delete_error";
        init();
    }

    private void save() {
        error = null;
        try {
            if (minecraft.player == null || minecraft.getSingleplayerServer() == null
                    || !minecraft.player.hasPermissions(2)) {
                error = "create.error.permission";
                return;
            }
            if (!id.matches("[A-Za-z0-9._-]{1,128}") || name.isBlank()) {
                error = "loot.invalid";
                return;
            }
            String newId = id.strip();
            if (!newId.equals(selectedId) && QuestLootStore.find(newId) != null) {
                error = "loot.id_exists";
                return;
            }
            int parsedRolls = Integer.parseInt(rolls.strip());
            List<LootDefinition.Entry> values = entries.stream().map(Draft::value).toList();
            LootDefinition table = new LootDefinition(id.strip(), name.strip(), mode, parsedRolls,
                    choiceTitle.strip(), values, displayIcon);
            QuestLootStore.validate(table);
            String oldId = selectedId;
            var server = minecraft.getSingleplayerServer();
            var author = server.getPlayerList().getPlayer(minecraft.player.getUUID());
            QuestLootStore.save(author, table);
            if (!oldId.isBlank() && !oldId.equals(table.id())) QuestLootStore.delete(author, oldId);
            reloadTables();
            xtdpotato.xero_quest_journal.core.io.QuestRepository.lootPresentationChanged();
            selectedId = table.id();
            select(table.id());
            status = "loot.saved";
            statusUntil = System.currentTimeMillis() + 2200;
            init();
        } catch (Exception invalid) {
            error = "loot.invalid";
        }
    }

    private void useSelected() {
        if (selectedCallback == null || selectedId.isBlank()) return;
        selectedCallback.accept(selectedId);
        closeTo(parent);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        tableMotion.limit(tableMaxScroll());
        entryMotion.limit(entryMaxScroll());
        tableMotion.advance();
        entryMotion.advance();
        if (renderedTableStart != Math.max(0, tableMotion.pixels() / TABLE_ROW_HEIGHT)
                || renderedEntryStart != firstVisibleEntry()) init();
        int tableScroll = tableMotion.pixels();
        int entryScroll = entryMotion.pixels();
        moveWidgets(tableWidgets, lastTableScroll - tableScroll);
        moveWidgets(entryWidgets, lastEntryScroll - entryScroll);
        if (scrollWholeEditor()) moveWidgets(metadataWidgets, lastEntryScroll - entryScroll);
        lastTableScroll = tableScroll;
        lastEntryScroll = entryScroll;

        int divider = leftPanel();
        int x = editorLeft();
        int w = editorWidth();
        super.renderContents(g, mouseX, mouseY, delta);
        g.drawString(font, text("loot.library"), 14, 52, RewardEditorTheme.TEXT);
        if (width < 640) g.drawString(font,
                font.plainSubstrByWidth(title.getString(), Math.max(1, leftPanel() - 24)), 12, 42, 0xFFFFFFFF, false);
        else g.drawString(font, title, x, 10, RewardEditorTheme.TEXT);
        if (scrollWholeEditor()) g.enableScissor(x, rowsViewportTop(), width - 20, rowsViewportBottom());
        int headerOffset = scrollWholeEditor() ? entryMotion.pixels() : 0;
        int labelY = 31 + metadataTopOffset() - headerOffset;
        g.drawString(font, text("loot.id"), idBox.getX(), labelY, RewardEditorTheme.MUTED);
        g.drawString(font, text("loot.name"), nameBox.getX(), labelY, RewardEditorTheme.MUTED);
        g.drawString(font, text("loot.entry_section"), x, 220 + metadataTopOffset() - headerOffset, RewardEditorTheme.TEXT);
        if (scrollWholeEditor()) g.disableScissor();
        g.enableScissor(x, rowsViewportTop(), width - 20, rowsViewportBottom());
        List<Integer> filtered = filteredEntries();
        for (int row = 0; row < filtered.size(); row++) {
            int index = filtered.get(row);
            Draft draft = entries.get(index);
            int y = entryContentTop() + row * entryHeight() - entryMotion.pixels();
            if (y + entryHeight() < rowsViewportTop() || y > rowsViewportBottom()) continue;
            QuestIcons.render(g, icon(draft.type, draft.item), x + 2, y + 14, 16);
            g.drawString(font, text("loot.count"), x + 20, y + 37, 0xFFB9C4CE);
            if (mode != LootDefinition.Mode.CHOICE) g.drawString(font, text("loot.weight"), x + 94, y + 37, 0xFFB9C4CE);
            int sliderWidth = Math.min(210, Math.max(92, Math.max(180, w - 12) / 4));
            int conditionX = stackedEntries() ? x + 20
                    : mode == LootDefinition.Mode.CHOICE ? x + 142 : x + 154 + sliderWidth + 12;
            g.drawString(font, text("loot.condition"), conditionX, y + (stackedEntries() ? 75 : 37), 0xFFB9C4CE);
            if (draft.type.equals("item")) {
                g.drawString(font, text("rewards.quality"), x + 20, y + (stackedEntries() ? 113 : 75), RewardEditorTheme.MUTED);
                g.drawString(font, text("rewards.background"), x + 28 + Math.min(150, Math.max(104, Math.max(180, w - 12) / 3)),
                        y + (stackedEntries() ? 113 : 75), RewardEditorTheme.MUTED);
            }
        }
        g.disableScissor();
        if (filtered.isEmpty()) g.drawString(font, entries.isEmpty() ? text("loot.empty") : text("loot.no_matches"),
                x + 8, rowsViewportTop() + 12, 0xFFABB8C3);

        int menuX = Math.clamp(contextX, 4, Math.max(4, width - 136));
        int menuY = Math.clamp(contextY, 4, Math.max(4, height - 66));
        renderAnimatedMenu(g, "context", contextIndex < 0 ? null : contextIndex,
                viewport(menuX, menuY, menuX + 132, menuY + 62), () -> renderContextMenu(g));
        if (error != null) {
            g.drawString(font, text(error), editorLeft(), height - 48, 0xFFFF7777);
        } else if (status != null && System.currentTimeMillis() <= statusUntil) {
            g.drawString(font, text(status), editorLeft(), height - 48, 0xFF8FD5A6);
        }
    }

    private void moveWidgets(Set<AbstractWidget> widgets, int delta) {
        if (delta != 0) widgets.forEach(widget -> widget.setY(widget.getY() + delta));
    }

    private int selectionColor(String tableId) {
        if (!selectedTables.contains(tableId)) return 0;
        if (rangeTables.contains(tableId)) return 0xFF31564F;
        return tableId.equals(selectedId) ? 0xFF6B552C : 0xFF344C64;
    }

    private void renderEntrySurfaces(GuiGraphics g, int x) {
        g.enableScissor(x, rowsViewportTop(), width - 20, rowsViewportBottom());
        List<Integer> filtered = filteredEntries();
        for (int row = 0; row < filtered.size(); row++) {
            int index = filtered.get(row);
            int y = entryContentTop() + row * entryHeight() - entryMotion.pixels();
            if (y + entryHeight() < rowsViewportTop() || y > rowsViewportBottom()) continue;
            Draft draft = entries.get(index);
            RewardEditorTheme.entry(g, x, y + 4, Math.max(1, width - x - 22),
                    entryHeight() - 8, draft.type, draft.enabled, false);
        }
        g.disableScissor();
    }

    private void renderContextMenu(GuiGraphics g) {
        if (contextIndex < 0) return;
        int menuWidth = 132;
        int menuHeight = 62;
        int x = Math.clamp(contextX, 4, Math.max(4, width - menuWidth - 4));
        int y = Math.clamp(contextY, 4, Math.max(4, height - menuHeight - 4));
        g.fill(x, y, x + menuWidth, y + menuHeight, 0xF0252B33);
        g.renderOutline(x, y, menuWidth, menuHeight, 0xFF9CB0BD);
        g.drawString(font, text("loot.context.delete"), x + 8, y + 6, 0xFFFFC1B2);
        g.drawString(font, text("loot.context.rename"), x + 8, y + 24, 0xFFEAF0F6);
        g.drawString(font, text("loot.context.id"), x + 8, y + 42, 0xFFEAF0F6);
    }

    private boolean handleContextClick(double mouseX, double mouseY) {
        if (contextIndex < 0) return false;
        int menuWidth = 132, menuHeight = 62;
        int x = Math.clamp(contextX, 4, Math.max(4, width - menuWidth - 4));
        int y = Math.clamp(contextY, 4, Math.max(4, height - menuHeight - 4));
        if (mouseX >= x && mouseX < x + menuWidth && mouseY >= y && mouseY < y + menuHeight) {
            int action = (int) ((mouseY - y) / 20);
            contextIndex = -1;
            if (action == 0) confirmDelete();
            else if (action == 1 && nameBox != null) {
                nameBox.setFocused(true);
                setFocused(nameBox);
            } else if (action == 2 && idBox != null) {
                idBox.setFocused(true);
                setFocused(idBox);
            }
        } else {
            contextIndex = -1;
        }
        return true;
    }

    private int tableAt(double x, double y) {
        if (x < 10 || x >= leftPanel() - 16 || y < tableViewportTop() || y >= tableViewportBottom()) return -1;
        int index = (int) ((y - tableViewportTop() + tableMotion.pixels()) / TABLE_ROW_HEIGHT);
        return index >= 0 && index < tables.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (contextIndex >= 0) return true;
        if (x < leftPanel() && y >= tableViewportTop() && y < tableViewportBottom()) {
            tableMotion.wheel(vertical * TABLE_ROW_HEIGHT);
            return true;
        }
        if (x >= editorLeft() && y >= rowsViewportTop() && y < rowsViewportBottom()) {
            entryMotion.wheel(vertical * entryHeight());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (contextIndex >= 0) {
            if (button == 0) {
                int menuX = Math.clamp(contextX, 4, Math.max(4, width - 136));
                int menuY = Math.clamp(contextY, 4, Math.max(4, height - 66));
                if (x >= menuX && x < menuX + 132 && y >= menuY && y < menuY + 62)
                    pressedContextAction = (int) ((y - menuY) / 20);
                else contextIndex = -1;
            }
            return true;
        }
        int tableIndex = tableAt(x, y);
        if (tableIndex >= 0) {
            if (button == 1) {
                openContext(tableIndex, (int) x, (int) y);
                return true;
            }
            if (button == 0) {
                pressedTableIndex = tableIndex;
                return true;
            }
        }
        if (button == 1 && x >= 8 && x < leftPanel() && y >= tableViewportTop()) {
            contextIndex = -1;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && pressedContextAction >= 0) {
            int action = pressedContextAction;
            pressedContextAction = -1;
            int menuX = Math.clamp(contextX, 4, Math.max(4, width - 136));
            int menuY = Math.clamp(contextY, 4, Math.max(4, height - 66));
            if (contextIndex >= 0 && x >= menuX && x < menuX + 132 && y >= menuY && y < menuY + 62
                    && action == (int) ((y - menuY) / 20))
                handleContextClick(x, y);
            return true;
        }
        if (button == 0 && pressedTableIndex >= 0) {
            int index = pressedTableIndex;
            pressedTableIndex = -1;
            if (index == tableAt(x, y)) selectTable(index, false);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!(getFocused() instanceof EditBox) && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_Z) {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) redo();
                else undo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && contextIndex >= 0) {
            contextIndex = -1;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() { closeTo(parent); }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xF0181D23);
        int divider = leftPanel();
        int editorX = editorLeft();
        RewardEditorTheme.panel(g, 4, 4, divider - 8, height - 8, RewardEditorTheme.GOLD);
        RewardEditorTheme.panel(g, divider + 5, 4, Math.max(1, width - divider - 9),
                scrollWholeEditor() ? height - 62 : 234,
                RewardEditorTheme.GREEN);
        g.fill(divider, 12, divider + 1, height - 12, 0x66566370);
        renderEntrySurfaces(g, editorX);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String defaultTarget(String type) {
        return switch (type) {
            case "item" -> "minecraft:diamond";
            case "command" -> "say loot";
            default -> "1";
        };
    }

    private static String icon(String type, String target) {
        return switch (type) {
            case "item" -> target;
            case "experience", "levels" -> "minecraft:experience_bottle";
            case "command" -> "minecraft:command_block";
            default -> "minecraft:paper";
        };
    }

    private static final class Draft {
        String id;
        String type;
        String item;
        String count;
        String weight;
        String condition;
        boolean enabled;
        String quality;
        String background;

        Draft(LootDefinition.Entry entry) {
            id = entry.id();
            type = entry.type();
            item = entry.item();
            count = Integer.toString(entry.count());
            weight = Integer.toString(entry.weight());
            condition = entry.condition();
            enabled = entry.enabled();
            quality = entry.quality();
            background = entry.background();
        }

        Draft(Draft other) {
            id = other.id;
            type = other.type;
            item = other.item;
            count = other.count;
            weight = other.weight;
            condition = other.condition;
            enabled = other.enabled;
            quality = other.quality;
            background = other.background;
        }

        LootDefinition.Entry value() {
            return new LootDefinition.Entry(id, type, item, Integer.parseInt(count.strip()),
                    Integer.parseInt(weight.strip()), condition, enabled, quality, background);
        }
    }

    private static final class TableButton extends StyledButton {
        private int selectionColor;

        TableButton(int x, int y, int width, int height, Component message, OnPress onPress,
                    ButtonStyleSheet styles, String id) {
            super(x, y, width, height, message, onPress, styles, id, "loot-table");
        }

        void setSelectionColor(int color) { selectionColor = color; }

        @Override
        protected void renderSurface(GuiGraphics graphics, ButtonStyle style, int background, int border) {
            if (selectionColor == 0) {
                super.renderSurface(graphics, style, background, border);
                return;
            }
            int fill = isHoveredOrFocused() ? brighten(selectionColor) : selectionColor;
            graphics.fill(getX(), getY(), getRight(), getBottom(), fill);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(),
                    selectionColor == 0xFF6B552C ? 0xFFFFD071
                            : selectionColor == 0xFF31564F ? 0xFF91D2BB : 0xFF8FC7E8);
        }

        private static int brighten(int color) {
            int red = Math.min(255, ((color >>> 16) & 0xFF) + 14);
            int green = Math.min(255, ((color >>> 8) & 0xFF) + 14);
            int blue = Math.min(255, (color & 0xFF) + 14);
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }
    }
}
