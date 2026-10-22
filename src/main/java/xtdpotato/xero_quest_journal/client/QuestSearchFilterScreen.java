package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestConditions;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Search scope and multi-select filters in a shared vertical scrolling panel. */
public class QuestSearchFilterScreen extends AnimatedQuestScreen {
    private static final String PREFIX = "screen.xero_quest_journal.search_filter.";
    private static final int HEADER_HEIGHT = 24;
    private static final int OPTION_HEIGHT = 27;

    private enum Kind { HEADER, TOGGLE, PICK_ITEMS, PICK_TARGET, REMOVE_ITEM, REMOVE_TARGET }
    private record Row(Kind kind, String section, String filter, String value, String label) {}

    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final String originalSearch;
    private Map<String, Set<String>> draft = QuestSearchFilters.sanitize(
            ClientPreferences.get().questSearchFilters);
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> collapsedSections = new LinkedHashSet<>();
    private EditBox search;
    private String query = "";
    private String message = "";
    private int contentHeight;
    private final SmoothScroll scroll = new SmoothScroll();
    private boolean scrollbarDragging;
    private double scrollbarOffset;
    private final QuestScreen journal;
    private final Set<String> scopes = new LinkedHashSet<>(ClientPreferences.get().questSearchScopes);
    private final List<net.minecraft.client.gui.components.Checkbox> scopeBoxes = new ArrayList<>();
    private EditBox taskSearch;
    private EditorIconButton collapseAllButton;
    private static final Set<String> SECTION_KEYS = Set.of("type", "group", "status", "template",
            "reward_type", "reward_item", "condition_type", "condition_target");
    private int rowsTop() { return listTop() + (journal == null ? 0 : 124); }

    public QuestSearchFilterScreen(Screen parent) {
        this(parent, null);
    }
    protected QuestSearchFilterScreen(Screen parent, QuestScreen journal) {
        super(text("title"));
        this.parent = parent;
        this.journal = journal;
        this.originalSearch = journal == null ? "" : journal.searchText();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }
    private int panelLeft() { return (width - Math.min(640, width - 24)) / 2; }
    private int panelRight() { return width - panelLeft(); }
    private int panelWidth() { return panelRight() - panelLeft(); }
    private int listTop() { return journal == null ? 82 : height < 320 ? 104 : 136; }
    private int listBottom() { return height - 48; }
    private int listRight() { return panelRight() - (scroll.maximum() > 0 ? 10 : 0); }
    private int trackX() { return panelRight() - 6; }

    @Override
    protected void init() {
        clearWidgets();
        scopeBoxes.clear();
        styles.refreshIfChanged();
        int left = panelLeft() + 12, w = panelWidth() - 24;
        int filterY = journal == null ? 46 : height < 320 ? 72 : 95;
        if (journal != null) {
            taskSearch = new EditBox(font, left, height < 320 ? 36 : 46, w, 22,
                    Component.translatable("screen.xero_quest_journal.quest_search"));
            taskSearch.setMaxLength(256); taskSearch.setValue(journal.searchText());
            taskSearch.setHint(Component.translatable("screen.xero_quest_journal.quest_search"));
            taskSearch.setResponder(journal::searchText);
            addRenderableWidget(taskSearch);
            String[] names = {"title", "description", "subtitle", "internal"};
            for (int i = 0; i < names.length; i++) {
                String scope = names[i];
                var checkbox = Checkbox.builder(Component.translatable(
                        "screen.xero_quest_journal.search_settings.scope." + scope), font)
                        .pos(left, listTop() + 21 + i * 25).selected(scopes.contains(scope))
                        .onValueChange((box, value) -> {
                            if (value) scopes.add(scope); else scopes.remove(scope);
                        }).build();
                checkbox.setTooltip(Tooltip.create(Component.translatable(
                        "screen.xero_quest_journal.search_settings.scope_hint." + scope)));
                scopeBoxes.add(checkbox); addRenderableWidget(checkbox);
            }
        } else {
            taskSearch = null;
        }
        search = new EditBox(font, left, filterY, w, 22, text("search"));
        search.setMaxLength(128);
        search.setValue(query);
        search.setHint(text("search"));
        search.setResponder(value -> {
            query = value;
            rebuildRows();
        });
        addRenderableWidget(search);
        addRenderableWidget(new EditorIconButton(left, height - 36, text("reset"),
                () -> {
                    draft = Map.of();
                    message = "";
                    rebuildRows();
                }, styles, "search-filter-reset", "filter_clear").dark());
        collapseAllButton = addRenderableWidget(new EditorIconButton(panelRight() - 34, 7,
                text(allSectionsCollapsed() ? "expand_all" : "collapse_all"),
                this::toggleAllSections, styles, "search-filter-collapse-all",
                allSectionsCollapsed() ? "layers" : "layers_hide").dark());
        int applyWidth = Math.min(88, Math.max(76, w / 5));
        int cancelX = left + w - applyWidth - 34;
        var cancel = new EditorIconButton(cancelX, height - 36, text("cancel"),
                this::cancel, styles, "search-filter-cancel", "close").dark();
        cancel.setTooltip(Tooltip.create(text("cancel_hint")));
        addRenderableWidget(cancel);
        var apply = new StyledButton(left + w - applyWidth, height - 36, applyWidth, 24,
                text("apply"), ignored -> apply(), styles, "search-filter-apply", "toolbar-button");
        apply.setTooltip(Tooltip.create(text("apply_hint")));
        addRenderableWidget(apply);
        rebuildRows();
    }

    private void cancel() {
        if (journal != null && !journal.searchText().equals(originalSearch))
            journal.searchText(originalSearch);
        closeTo(parent);
    }

    private boolean allSectionsCollapsed() {
        return collapsedSections.containsAll(SECTION_KEYS);
    }

    private void toggleAllSections() {
        if (allSectionsCollapsed()) collapsedSections.clear();
        else collapsedSections.addAll(SECTION_KEYS);
        updateCollapseAllButton();
        rebuildRows();
    }

    private void updateCollapseAllButton() {
        if (collapseAllButton == null) return;
        boolean collapsed = allSectionsCollapsed();
        collapseAllButton.setMessage(text(collapsed ? "expand_all" : "collapse_all"));
        collapseAllButton.setTooltip(Tooltip.create(text(collapsed ? "expand_all" : "collapse_all")));
        collapseAllButton.icon(collapsed ? "layers" : "layers_hide");
    }

    private void toggleSection(String section) {
        if (!SECTION_KEYS.contains(section)) return;
        if (!collapsedSections.add(section)) collapsedSections.remove(section);
        updateCollapseAllButton();
        rebuildRows();
    }

    private void apply() {
        {
            var preferences = ClientPreferences.get();
            var previousFilters = preferences.questSearchFilters;
            var previousScopes = preferences.questSearchScopes;
            preferences.questSearchFilters = QuestSearchFilters.sanitize(draft);
            if (journal != null) preferences.questSearchScopes = Set.copyOf(scopes);
            if (!preferences.save()) {
                preferences.questSearchFilters = previousFilters; preferences.questSearchScopes = previousScopes;
                message = "save_failed";
                return;
            }
        }
        if (journal != null) journal.searchConfigurationChanged();
        closeTo(parent);
    }

    private void rebuildRows() {
        List<Row> all = allRows();
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        var filtered = new ArrayList<Row>();
        if (normalized.isBlank()) filtered.addAll(all);
        else {
            Row sectionHeader = null;
            boolean sectionAdded = false;
            for (Row row : all) {
                if (row.kind() == Kind.HEADER) {
                    sectionHeader = row;
                    sectionAdded = false;
                } else if (row.label().toLowerCase(Locale.ROOT).contains(normalized)
                        || row.value().toLowerCase(Locale.ROOT).contains(normalized)
                        || QuestIcons.searchIndex(row.label(), row.value()).contains(normalized)) {
                    if (!sectionAdded) {
                        if (sectionHeader != null) filtered.add(sectionHeader);
                        sectionAdded = true;
                    }
                    filtered.add(row);
                }
            }
        }
        rows.clear();
        String activeSection = "";
        for (Row row : filtered) {
            if (row.kind() == Kind.HEADER) {
                activeSection = row.section();
                rows.add(row);
            } else if (!collapsedSections.contains(activeSection)) {
                rows.add(row);
            }
        }
        contentHeight = rowsTop() - listTop() + rows.stream().mapToInt(row -> row.kind() == Kind.HEADER
                ? HEADER_HEIGHT : OPTION_HEIGHT).sum();
        scroll.limit(Math.max(0, contentHeight - Math.max(1, listBottom() - listTop())));
    }

    private List<Row> allRows() {
        var result = new ArrayList<Row>();
        var typeLabels = new LinkedHashMap<String, String>();
        ClientQuestDefinitions.types().forEach(type -> typeLabels.put(type.id(), type.name()));
        ClientQuestDefinitions.all().forEach(quest -> typeLabels.putIfAbsent(quest.type(),
                QuestText.prettyId(quest.type())));
        addSection(result, "type", typeLabels);

        var groups = new LinkedHashMap<String, String>();
        ClientQuestDefinitions.groups().forEach(group -> groups.put(group.id(), group.name()));
        addSection(result, "group", groups);

        var statuses = new LinkedHashMap<String, String>();
        for (String status : List.of("active", "completed", "claimed", "locked", "unlocked",
                "tracked", "untracked", "unread"))
            statuses.put(status, text("status." + status).getString());
        addSection(result, "status", statuses);

        var templates = new LinkedHashMap<String, String>();
        templates.put("none", text("template.none").getString());
        ClientQuestDefinitions.templates().forEach(template -> templates.put(template.id(), template.name()));
        ClientQuestDefinitions.all().stream().map(QuestDefinition::templateId).filter(value -> !value.isBlank())
                .forEach(value -> templates.putIfAbsent(value, value));
        addSection(result, "template", templates);

        var rewardTypes = new LinkedHashMap<String, String>();
        QuestRewards.TYPES.forEach(type -> rewardTypes.put(type,
                Component.translatable("screen.xero_quest_journal.reward_type." + type).getString()));
        ClientQuestDefinitions.all().stream().flatMap(quest -> quest.rewards().stream())
                .map(QuestDefinition.Reward::type).forEach(type -> rewardTypes.putIfAbsent(type, QuestText.prettyId(type)));
        addSection(result, "reward_type", rewardTypes);

        result.add(new Row(Kind.HEADER, "reward_item", "", "", text("section.reward_item").getString()));
        result.add(new Row(Kind.PICK_ITEMS, "reward_item", "reward_item", "",
                text("choose_items", QuestSearchFilters.selected(draft, "reward_item").size()).getString()));
        QuestSearchFilters.sorted(QuestSearchFilters.selected(draft, "reward_item")).forEach(item ->
                result.add(new Row(Kind.REMOVE_ITEM, "reward_item", "reward_item", item,
                        QuestIcons.displayName(item) + "  (" + item + ")")));

        var conditionTypes = new LinkedHashMap<String, String>();
        QuestConditions.TYPES.forEach(type -> conditionTypes.put(type,
                Component.translatable("screen.xero_quest_journal.condition_type." + type).getString()));
        ClientQuestDefinitions.all().forEach(quest -> {
            quest.triggers().forEach(trigger -> collectCustomConditionTypes(trigger, conditionTypes));
            quest.steps().forEach(step -> step.conditions().forEach(
                    trigger -> collectCustomConditionTypes(trigger, conditionTypes)));
        });
        addSection(result, "condition_type", conditionTypes);

        result.add(new Row(Kind.HEADER, "condition_target", "", "", text("section.condition_target").getString()));
        result.add(new Row(Kind.PICK_TARGET, "condition_target", "condition_target", "",
                text("choose_targets", QuestSearchFilters.selected(draft, "condition_target").size()).getString()));
        QuestSearchFilters.sorted(QuestSearchFilters.selected(draft, "condition_target")).forEach(target ->
                result.add(new Row(Kind.REMOVE_TARGET, "condition_target", "condition_target", target, target)));
        return result;
    }

    private void addSection(List<Row> rows, String key, Map<String, String> options) {
        rows.add(new Row(Kind.HEADER, key, "", "", text("section." + key).getString()));
        options.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> rows.add(new Row(Kind.TOGGLE, key, key, entry.getKey(), entry.getValue())));
    }

    private static void collectCustomConditionTypes(QuestDefinition.Trigger trigger, Map<String, String> types) {
        types.putIfAbsent(trigger.type(), QuestText.prettyId(trigger.type()));
        trigger.children().forEach(child -> collectCustomConditionTypes(child, types));
    }

    private List<QuestValuePickerScreen.Option> itemOptions() {
        return BuiltInRegistries.ITEM.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString))
                .map(id -> {
                    String value = id.toString();
                    String label = QuestIcons.displayName(value);
                    return new QuestValuePickerScreen.Option(value, label, value + "\n" + label);
                })
                .toList();
    }

    private List<QuestValuePickerScreen.Option> conditionTargetOptions() {
        var labels = new LinkedHashMap<String, String>();
        ClientQuestDefinitions.all().forEach(quest -> {
            quest.triggers().forEach(trigger -> collectConditionTargets(trigger, labels));
            quest.steps().forEach(step -> step.conditions().forEach(trigger -> collectConditionTargets(trigger, labels)));
        });
        return labels.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new QuestValuePickerScreen.Option(entry.getKey(), entry.getValue(),
                        entry.getKey() + "\n" + entry.getValue()))
                .toList();
    }

    private static void collectConditionTargets(QuestDefinition.Trigger trigger, Map<String, String> targets) {
        if (!trigger.item().isBlank()) {
            String label = switch (trigger.type()) {
                case "item_get", "item_hold", "item_submit", "item_acquire" ->
                        QuestIcons.displayName(trigger.item());
                case "quest", "unlock", "lock" -> {
                    var task = ClientQuestDefinitions.find(trigger.item());
                    yield task == null ? QuestText.prettyId(trigger.item()) : QuestText.title(task).getString();
                }
                default -> trigger.item();
            };
            targets.putIfAbsent(trigger.item(), label);
        }
        trigger.children().forEach(child -> collectConditionTargets(child, targets));
    }

    private void toggle(String key, String value) {
        draft = QuestSearchFilters.toggle(draft, key, value);
        message = "";
        rebuildRows();
    }

    private void openItemPicker() {
        minecraft.setScreen(new QuestValuePickerScreen(this, text("picker.reward_item").getString(), "",
                this::itemOptions, "", value -> {
            if (!value.isBlank()) toggle("reward_item", value);
        }));
    }

    private void openConditionTargetPicker() {
        minecraft.setScreen(new QuestValuePickerScreen(this, text("picker.condition_target").getString(), "",
                this::conditionTargetOptions, "", value -> {
            if (!value.isBlank()) toggle("condition_target", value);
        }));
    }

    private int scrollbarHeight() {
        int available = Math.max(1, listBottom() - listTop());
        return Math.max(18, available * available / Math.max(1, available + (int) scroll.maximum()));
    }

    private int scrollbarTop() {
        int travel = Math.max(0, listBottom() - listTop() - scrollbarHeight());
        return listTop() + (int) Math.round(travel * scroll.pixels() / Math.max(1, scroll.maximum()));
    }

    private void dragScrollbar(double mouseY) {
        int travel = Math.max(1, listBottom() - listTop() - scrollbarHeight());
        scroll.jump((mouseY - listTop() - scrollbarOffset) * scroll.maximum() / travel);
    }

    private int rowIndexAt(double y) {
        int point = (int) Math.floor(y - rowsTop() + scroll.position());
        int offset = 0;
        for (int index = 0; index < rows.size(); index++) {
            int rowHeight = rows.get(index).kind() == Kind.HEADER ? HEADER_HEIGHT : OPTION_HEIGHT;
            if (point >= offset && point < offset + rowHeight) return index;
            offset += rowHeight;
        }
        return -1;
    }

    private void activate(Row row) {
        switch (row.kind()) {
            case TOGGLE -> toggle(row.filter(), row.value());
            case PICK_ITEMS -> openItemPicker();
            case PICK_TARGET -> openConditionTargetPicker();
            case REMOVE_ITEM, REMOVE_TARGET -> {
                draft = QuestSearchFilters.without(draft, row.filter(), row.value());
                rebuildRows();
            }
            case HEADER -> toggleSection(row.section());
        }
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        scroll.advance();
        for (int i = 0; i < scopeBoxes.size(); i++)
            scopeBoxes.get(i).setY(listTop() + 21 + i * 25 - scroll.pixels());
        backdrop(g, 0xF0181C22);
        int left = panelLeft(), right = panelRight(), top = listTop(), bottom = listBottom();
        g.fill(left, 0, right, height, 0xFF20262E);
        g.fill(left, 0, right, height < 320 ? 28 : 38, 0xFF2B333E);
        JournalNavButton.drawIcon(g, "search_filter", left + 12, height < 320 ? 7 : 11, 16, 0xFFF1D49A);
        g.drawString(font, journal == null ? title : Component.translatable(
                "screen.xero_quest_journal.search_settings.title"), left + 36, height < 320 ? 10 : 14, 0xFFF1D49A, false);
        if (journal != null) g.drawString(font, text("filter_options"), left + 12,
                height < 320 ? 61 : 79, 0xFFB8C2CE, false);
        g.fill(left, top, listRight(), bottom, 0xFF181D23);
        g.enableScissor(left, top, listRight(), bottom);
        if (journal != null) {
            JournalNavButton.drawIcon(g, "mail_search", left + 10, top + 2 - scroll.pixels(), 14, 0xFFF0D49A);
            g.drawString(font, text("scope"), left + 30, top + 3 - scroll.pixels(), 0xFFF0D49A, false);
        }
        int y = rowsTop() - scroll.pixels();
        for (Row row : rows) {
            int rowHeight = row.kind() == Kind.HEADER ? HEADER_HEIGHT : OPTION_HEIGHT;
            if (y + rowHeight > top && y < bottom) renderRow(g, row, left, listRight(), y, rowHeight,
                    mouseX >= left && mouseX < listRight() && mouseY >= y && mouseY < y + rowHeight);
            y += rowHeight;
        }
        g.disableScissor();
        if (scroll.maximum() > 0) {
            g.fill(trackX(), top, trackX() + 4, bottom, 0xFF343B45);
            g.fill(trackX(), scrollbarTop(), trackX() + 4, scrollbarTop() + scrollbarHeight(), 0xFF9DB4D2);
        }
        g.fill(left, height - 44, right, height, 0xFF252C34);
        g.drawString(font, text("active_count", QuestSearchFilters.count(draft)), left + 46,
                height - 25, 0xFF9FC8BA, false);
        if (!message.isBlank()) g.drawString(font, text(message), left + 150, height - 25, 0xFFFF9B8F, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    private void renderRow(GuiGraphics g, Row row, int left, int right, int y, int h, boolean hovered) {
        if (row.kind() == Kind.HEADER) {
            if (hovered) g.fill(left, y, right, y + h - 1, 0xFF303A46);
            g.fill(left + 8, y + h - 1, right - 8, y + h, 0xFF45515D);
            String icon = switch (row.section()) {
                case "type" -> "all";
                case "group" -> "layers";
                case "status" -> "mail_check";
                case "template" -> "source";
                case "reward_type" -> "gift";
                case "reward_item" -> "item";
                case "condition_type" -> "clause";
                default -> "guide";
            };
            JournalNavButton.drawIcon(g, icon, left + 10, y + (h - 14) / 2, 14, 0xFFF0D49A);
            g.drawString(font, font.plainSubstrByWidth(row.label(), right - left - 62),
                    left + 30, y + (h - font.lineHeight) / 2, 0xFFF0D49A, false);
            JournalNavButton.drawIcon(g, collapsedSections.contains(row.section()) ? "sort_down" : "sort_up",
                    right - 25, y + (h - 14) / 2, 14, 0xFFB8C2CE);
            return;
        }
        if (hovered) g.fill(left, y, right, y + h - 1, 0xFF303A46);
        boolean selected = row.kind() == Kind.TOGGLE
                && QuestSearchFilters.selected(draft, row.filter()).contains(row.value());
        int checkX = left + 12, checkY = y + (h - 14) / 2;
        if (row.kind() == Kind.TOGGLE) {
            g.fill(checkX, checkY, checkX + 14, checkY + 14, 0xFF151A20);
            g.renderOutline(checkX, checkY, 14, 14, selected ? 0xFF9FC8BA : 0xFF7E8B98);
            if (selected) QuestStatusIcons.renderCheckMark(g, checkX, checkY, 14, 0xFF9FC8BA);
        } else {
            JournalNavButton.drawIcon(g, row.kind() == Kind.PICK_ITEMS ? "item"
                    : row.kind() == Kind.PICK_TARGET ? "choose" : "close",
                    checkX, checkY, 14, row.kind() == Kind.REMOVE_ITEM || row.kind() == Kind.REMOVE_TARGET
                            ? 0xFFE8897C : 0xFF9FC8BA);
        }
        String label = row.label();
        int maxWidth = right - (left + 34) - 12;
        if (row.kind() == Kind.PICK_ITEMS || row.kind() == Kind.PICK_TARGET) maxWidth -= 64;
        g.drawString(font, font.plainSubstrByWidth(label, Math.max(1, maxWidth)),
                left + 34, y + (h - font.lineHeight) / 2, 0xFFE2E7EC, false);
        if (row.kind() == Kind.PICK_ITEMS || row.kind() == Kind.PICK_TARGET)
            g.drawString(font, text("choose").getString(), right - 56,
                    y + (h - font.lineHeight) / 2, 0xFFB7C4D0, false);
        else if (row.kind() == Kind.REMOVE_ITEM || row.kind() == Kind.REMOVE_TARGET)
            g.drawString(font, "\u00d7", right - 17, y + (h - font.lineHeight) / 2, 0xFFFF9B8F, false);
        g.fill(left + 10, y + h - 1, right - 10, y + h, 0xFF343C46);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0) return false;
        if (x >= trackX() - 2 && x < rightEdge() && y >= listTop() && y < listBottom()
                && scroll.maximum() > 0) {
            scrollbarDragging = true;
            scrollbarOffset = y >= scrollbarTop() && y < scrollbarTop() + scrollbarHeight()
                    ? y - scrollbarTop() : scrollbarHeight() / 2.0;
            dragScrollbar(y);
            return true;
        }
        if (x >= panelLeft() && x < listRight() && y >= listTop() && y < listBottom()) {
            int index = rowIndexAt(y);
            if (index >= 0 && index < rows.size()) activate(rows.get(index));
            return true;
        }
        return true;
    }

    private int rightEdge() { return panelRight(); }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollbarDragging) {
            dragScrollbar(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (x >= panelLeft() && x < panelRight() && y >= listTop() && y < listBottom()) {
            scroll.wheel(vertical * 30);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (inputBlocked()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) { scroll.to(0); return true; }
        if (key == GLFW.GLFW_KEY_END) { scroll.to(scroll.maximum()); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override protected Viewport widgetViewport(net.minecraft.client.gui.components.AbstractWidget widget) {
        return scopeBoxes.contains(widget) ? viewport(panelLeft(), listTop(), listRight(), listBottom()) : null;
    }
    @Override public void onClose() { cancel(); }
    @Override public boolean isPauseScreen() { return false; }
}
