package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;
import xtdpotato.xero_quest_journal.core.model.JourneyTimeline;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.util.*;

/** Chapter timeline in stable design coordinates; viewport scaling never edits authored data. */
public final class JourneyScreen extends AnimatedQuestScreen {
    private static final int CARD_WIDTH = 168, CARD_HEIGHT = 112, STEP = 230;
    private static final double MIN_TIMELINE_ZOOM = 0.5, MAX_TIMELINE_ZOOM = 1.5;
    private static final int BACKGROUND = 0xFF303541;
    private static final ResourceLocation PROGRESS_MARKER = ResourceLocation.fromNamespaceAndPath(
            "xero_quest_journal", "textures/gui/journey_progress_marker.png");
    private static final int PROGRESS_MARKER_WIDTH = 6;
    private static final int PROGRESS_MARKER_HEIGHT = 12;
    private static final int PROGRESS_ALPHA = 0x80;
    private static final String UNASSIGNED_CHAPTER = "__journey_unassigned";
    private final Screen parent;
    private final String mediaOwner = "journey-" + UUID.randomUUID();
    private final JourneyScroll timelineX = new JourneyScroll(), timelineY = new JourneyScroll();
    private final JourneyScroll tabs = new JourneyScroll();
    private final Map<String, UiMotion> tabMotions = new HashMap<>();
    private List<QuestCatalog.Chapter> chapters = List.of();
    private List<QuestDefinition> quests = List.of();
    private List<QuestSlot> slots = List.of();
    private List<Section> sections = List.of();
    private List<Map<String, QuestSlot>> chapterSlots = List.of();
    private List<List<JourneyTimeline.Connection>> chapterConnections = List.of();
    private ViewportIndex<QuestSlot> slotIndex = new ViewportIndex<>(List.of());
    private ViewportIndex<Path> pathIndex = new ViewportIndex<>(List.of());
    private Map<QuestSlot, List<Path>> incidentPaths = Map.of();
    private List<QuestSlot> visibleSlots = List.of();
    private ViewportIndex<QuestSlot> queriedSlotIndex;
    private List<QuestSlot> queriedSlots = List.of();
    private double queryLeft, queryTop, queryRight, queryBottom;
    private int queryLineY;
    private String queryDrag = "";
    private JourneyTimeline.Position queryPreview;
    private double indexedMinY, indexedMaxY;
    private double limitScale = -1;
    private int chapterIndex, columns, branchRows;
    private int navigationChapter = -1;
    private String pendingChapterEvent = "";
    private long frameTime;
    private double dragX, dragY, dragScrollX, dragScrollY;
    private double timelineZoom = 1;
    private boolean dragging, draggingTabs, draggingTimelineProgress, draggingScrollbar,
            draggingVerticalScrollbar, moved, closeHeld;
    private int pressedTab = -1, pressedArrow;
    private String pressedQuest = "";
    private String backdropKey = "";
    private JourneyBackdrop backdrop;
    private long backdropChanged;
    private UiCompositor backdropFrame = new UiCompositor(), backdropSnapshot = new UiCompositor();
    private final UiCompositor backdropIncoming = new UiCompositor();
    private boolean backdropFrameReady;
    private long catalogRevision = -1, progressRevision = -1;
    private Set<String> hiddenTasks = Set.of();
    private boolean appearanceHeld;
    private boolean editing;
    private String focusQuest = "", selectedQuest = "", nodeDrag = "", wireFrom = "";
    private int nodeChapter;
    private double nodeGrabX, nodeGrabY, pointerX, pointerY;
    private JourneyTimeline.Position previewPosition;
    private final List<StyledButton> menu = new ArrayList<>();
    private int menuX, menuY;
    private java.util.concurrent.CompletableFuture<String> pending;
    private java.util.concurrent.CompletableFuture<String> historyPending;
    private String saveError = "";
    private int initializedWidth, initializedHeight;

    private record QuestSlot(QuestDefinition quest, int column, int branch, int chapter,
                             boolean categoryColumn, String category) {}
    private record Section(int start, int width, int columns, int branches, List<QuestDefinition> quests) {}
    private record Path(QuestSlot from, QuestSlot to, int fromX, int fromY, int toX, int toY) {}

    public JourneyScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.journey.title"));
        this.parent = parent;
    }
    public JourneyScreen(Screen parent, String questId) {
        this(parent);
        focusQuest = questId == null ? "" : questId;
        selectedQuest = focusQuest;
    }

    public int chapterCount() { return chapters.size(); }
    public int visibleQuestCount() { return quests.size(); }
    public static boolean containsQuest(String id) {
        QuestDefinition quest = ClientQuestDefinitions.find(id);
        var progress = ClientQuestProgress.view(id);
        if (quest == null || !quest.journeyVisible() || quest.hidden() || progress != null && progress.hidden())
            return false;
        String group = ClientQuestDefinitions.groupOf(id);
        Set<String> explicitlyAssigned = new HashSet<>();
        for (var chapter : ClientQuestDefinitions.chapters()) {
            if (chapter.quests().contains(id)) return true;
            if (!chapter.quests().isEmpty() || chapter.timeline().explicitTasks())
                explicitlyAssigned.addAll(chapter.quests());
            else if (chapter.groupId().isBlank() || chapter.groupId().equals(group)) return true;
        }
        return !explicitlyAssigned.contains(id);
    }
    private JourneyAppearance appearance() {
        return appearance(chapterIndex);
    }
    private JourneyAppearance appearance(int chapter) {
        JourneyAppearance defaults = ClientQuestProgress.uiTheme().journeyAppearance();
        return chapters.isEmpty() ? defaults : chapters.get(chapter).journeyAppearance().over(defaults);
    }
    private JourneyAppearance.Element style(String slot) { return appearance().get(slot); }
    private void icon(GuiGraphics g, String slot, String fallback, int x, int y, int size, int color) {
        JourneyAppearanceRenderer.icon(g, style(slot), fallback, x, y, size, size, color, false);
    }
    private void text(GuiGraphics g, String slot, String value, int x, int y, int width, int color, boolean centered) {
        JourneyAppearanceRenderer.text(g, font, style(slot), value, x, y, width, color, centered);
    }
    private boolean canStyle() { return ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2); }
    @Override protected void init() {
        boolean resized = initializedWidth != 0 && (initializedWidth != width || initializedHeight != height);
        cancelPointer();
        refresh();
        if (resized && !selectedQuest.isBlank()) focusQuest = selectedQuest;
        focusRequestedQuest();
        ensureSelectedTab();
        initTools();
        initializedWidth = width; initializedHeight = height;
    }
    @Override protected int animationHeaderBottom() { return top(); }
    @Override protected int animationFooterTop() { return bottom(); }

    private void refresh() {
        String id = chapters.isEmpty() ? "" : chapters.get(chapterIndex).id();
        double localX = sections.isEmpty() ? timelineX.position()
                : timelineX.position() - sections.get(chapterIndex).start();
        chapters = journeyChapters();
        chapterIndex = 0;
        for (int i = 0; i < chapters.size(); i++)
            if (chapters.get(i).id().equals(id)) chapterIndex = i;
        Set<String> ids = new HashSet<>();
        long now = System.nanoTime();
        for (int i = 0; i < chapters.size(); i++) {
            String key = chapters.get(i).id();
            ids.add(key);
            tabMotions.putIfAbsent(key, new UiMotion(i == chapterIndex, now));
            UiMotion motion = tabMotions.get(key);
            if (motion.visible() != (i == chapterIndex)) motion.target(i == chapterIndex, now, motionMillis());
        }
        tabMotions.keySet().retainAll(ids);
        loadQuests();
        if (!sections.isEmpty()) timelineX.shift(sections.get(chapterIndex).start() + localX - timelineX.position());
        updateBackdrop();
        catalogRevision = ClientQuestDefinitions.revision();
        progressRevision = ClientQuestProgress.listRevision();
        hiddenTasks = progressHiddenTasks();
    }

    private Set<String> progressHiddenTasks() {
        var hidden = new HashSet<String>();
        for (var quest : ClientQuestDefinitions.all()) {
            var view = ClientQuestProgress.view(quest.id());
            if (view != null && view.hidden()) hidden.add(quest.id());
        }
        return hidden;
    }

    private void focusRequestedQuest() {
        if (focusQuest.isBlank()) return;
        var slot = slots.stream().filter(value -> value.quest().id().equals(focusQuest)).findFirst().orElse(null);
        if (slot == null) return;
        activateChapter(slot.chapter());
        navigationChapter = slot.chapter();
        timelineX.jump(slotX(slot) + CARD_WIDTH / 2.0 - viewWidth() / 2);
        timelineY.jump(slotY(slot) + slotHeight(slot) / 2.0 - viewHeight() / 2);
        focusQuest = "";
    }

    private void initTools() {
        clearWidgets();
        if (!canStyle()) { editing = false; return; }
        var styles = ButtonStyleSheet.load();
        var toggle = addRenderableWidget(new EditorIconButton(width - 62, 10,
                label(editing ? "finish_edit_layout" : "edit_layout"),
                () -> { editing = !editing; wireFrom = ""; menu.clear(); initTools(); },
                styles, "journey-edit-layout", editing ? "edit_exit" : "edit"));
        toggle.active = pending == null;
        if (editing) {
            var undo = addRenderableWidget(new EditorIconButton(width - 212, 10, label("undo"),
                    () -> runHistory(false), styles, "journey-undo", "undo"));
            var redo = addRenderableWidget(new EditorIconButton(width - 182, 10, label("redo"),
                    () -> runHistory(true), styles, "journey-redo", "redo"));
            undo.setTooltip(Tooltip.create(Component.translatable("screen.xero_quest_journal.layout.rich_undo")));
            redo.setTooltip(Tooltip.create(Component.translatable("screen.xero_quest_journal.layout.rich_redo")));
            undo.active = redo.active = historyPending == null && pending == null;
        }
        if (editing) addRenderableWidget(new EditorIconButton(width - 92, 10, label("appearance"),
                this::editAppearance, styles, "journey-appearance", "grid")).active = pending == null;
        if (editing && canEditChapter(chapterIndex)) {
            addRenderableWidget(new EditorIconButton(width - 122, 10, label("edit_backgrounds"),
                    this::editBackgrounds, styles, "journey-edit-backgrounds", "minecraft:painting")).active = pending == null;
            addRenderableWidget(new EditorIconButton(width - 152, 10, label("tasks"),
                    this::chooseTasks, styles, "journey-tasks", "select_tasks")).active = pending == null;
        }
    }
    private static Component label(String key) {
        return Component.translatable("screen.xero_quest_journal.journey." + key);
    }
    private List<QuestCatalog.Chapter> journeyChapters() {
        var values = new ArrayList<>(ClientQuestDefinitions.chapters());
        var assigned = new HashSet<String>();
        for (var chapter : values) {
            if (!chapter.quests().isEmpty() || chapter.timeline().explicitTasks()) assigned.addAll(chapter.quests());
            else ClientQuestDefinitions.all().stream().filter(q -> chapter.groupId().isBlank()
                    || chapter.groupId().equals(ClientQuestDefinitions.groupOf(q.id()))).map(QuestDefinition::id)
                    .forEach(assigned::add);
        }
        var unassigned = ClientQuestDefinitions.all().stream().filter(q -> !assigned.contains(q.id())
                && q.journeyVisible() && !q.hidden()).map(QuestDefinition::id).toList();
        if (!unassigned.isEmpty()) values.add(new QuestCatalog.Chapter(UNASSIGNED_CHAPTER,
                label("unassigned").getString(), "", "minecraft:map", "", unassigned, List.of(),
                JourneyAppearance.empty(), new JourneyTimeline(Map.of(), List.of(), false, true), ""));
        return List.copyOf(values);
    }
    private boolean canEditChapter(int chapter) {
        return chapter >= 0 && chapter < chapters.size() && !chapters.get(chapter).fingerprint().isBlank();
    }

    private List<String> memberIds(int chapter) {
        var value = chapters.get(chapter);
        if (!value.quests().isEmpty() || value.timeline().explicitTasks()) return value.quests();
        return ClientQuestDefinitions.all().stream()
                .filter(q -> value.groupId().isBlank() || value.groupId().equals(ClientQuestDefinitions.groupOf(q.id())))
                .map(QuestDefinition::id).toList();
    }

    private void saveChapter(int chapter, List<String> ids, JourneyTimeline timeline, List<JourneyBackdrop> backgrounds) {
        if (!canStyle() || !canEditChapter(chapter) || pending != null) return;
        var original = chapters.get(chapter);
        var members = new HashSet<>(ids);
        var positions = new LinkedHashMap<>(timeline.positions());
        positions.keySet().retainAll(members);
        var edges = timeline.connections().stream().filter(edge ->
                (members.contains(edge.from()) || JourneyTimeline.chapterPoint(edge.from()))
                        && (members.contains(edge.to()) || JourneyTimeline.chapterPoint(edge.to()))).toList();
        var value = new JourneyTimeline(positions, edges, timeline.customConnections(), true);
        pending = ClientQuestProgress.saveJourney(original,
                new xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork.Draft(ids, value, backgrounds));
        saveError = "";
        initTools();
    }

    private void chooseTasks() {
        if (!canEditChapter(chapterIndex)) return;
        int chapter = chapterIndex;
        var value = chapters.get(chapter);
        minecraft.setScreen(new QuestTaskPickerScreen(this, String.join(",", memberIds(chapter)), "", ids ->
                saveChapter(chapter, ids, value.timeline(), value.backgrounds())).multipleSelection(true));
    }
    private void editBackgrounds() {
        if (!canEditChapter(chapterIndex)) return;
        int chapter = chapterIndex;
        var value = chapters.get(chapter);
        minecraft.setScreen(new JourneyBackgroundScreen(this, value.backgrounds(), backgrounds ->
                saveChapter(chapter, memberIds(chapter), value.timeline(), backgrounds)));
    }

    private void editAppearance() {
        if (!canEditChapter(chapterIndex) || pending != null) return;
        int index = chapterIndex;
        var chapter = chapters.get(index);
        var editor = new JourneyAppearanceScreen(this, chapter.journeyAppearance(), value -> {
            pending = ClientQuestProgress.saveJourney(chapter,
                    new xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork.Draft(
                            memberIds(index), chapter.timeline(), chapter.backgrounds(), null, null, value));
            saveError = "";
            initTools();
        }).chapterContext(chapter, sections.get(index).quests());
        minecraft.setScreen(editor);
    }

    private void editChapter(String component) {
        if (!canEditChapter(chapterIndex) || pending != null) return;
        int index = chapterIndex;
        var chapter = chapters.get(index);
        minecraft.setScreen(new JourneyChapterEditorScreen(this, chapter, memberIds(index), component,
                draft -> { pending = ClientQuestProgress.saveJourney(chapter, draft); initTools(); }));
    }

    private void runHistory(boolean redo) {
        if (historyPending != null || pending != null || !canStyle()) return;
        historyPending = ClientQuestProgress.journeyHistory(redo);
        initTools();
    }

    private void loadQuests() {
        List<Section> nextSections = new ArrayList<>();
        List<QuestSlot> allSlots = new ArrayList<>();
        int start = 0;
        for (int chapter = 0; chapter < chapters.size(); chapter++) {
            List<QuestDefinition> next = new ArrayList<>();
            List<String> chapterIds = memberIds(chapter);
            for (String id : chapterIds) {
                QuestDefinition quest = ClientQuestDefinitions.find(id);
                var progress = ClientQuestProgress.view(id);
                if (quest != null && quest.journeyVisible() && !quest.hidden()
                        && (progress == null || !progress.hidden())
                        && (editing || !quest.automaticallyGenerated() || progress == null || progress.unlocked()))
                    next.add(quest);
            }
            List<QuestSlot> layout = new ArrayList<>();
            var categoryCells = JourneyTaskColumns.layout(chapters.get(chapter).groupId(), next);
            if (!categoryCells.isEmpty()) {
                int categoryCount = 0, rowCount = 0;
                for (var cell : categoryCells) {
                    layout.add(new QuestSlot(cell.quest(), cell.column(), cell.row(), chapter,
                            true, cell.category()));
                    categoryCount = Math.max(categoryCount, cell.column() + 1);
                    rowCount = Math.max(rowCount, cell.row());
                }
                int lastX = layout.stream().mapToInt(slot -> {
                    var position = chapters.get(slot.chapter()).timeline().positions().get(slot.quest().id());
                    return position == null ? 192 + slot.column() * STEP : position.x();
                }).max().orElse(220);
                int extent = Math.max(420, lastX + CARD_WIDTH + 100);
                nextSections.add(new Section(start, extent, categoryCount, rowCount, List.copyOf(next)));
                allSlots.addAll(layout);
                start += extent;
                continue;
            }
            Map<String, Integer> anchors = new HashMap<>();
            List<QuestDefinition> remaining = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            next.forEach(q -> ids.add(q.id()));
            int columns = 0, branchRows = 0;
            for (QuestDefinition quest : next) {
                // Only actual side quests with an in-chapter parent become branch strips.
                if (!quest.type().equals("main") && quest.parents().stream().anyMatch(ids::contains))
                    remaining.add(quest);
                else {
                    anchors.put(quest.id(), columns);
                    layout.add(new QuestSlot(quest, columns++, 0, chapter, false, ""));
                }
            }
            Map<Integer, Integer> rows = new HashMap<>();
            while (!remaining.isEmpty()) {
                boolean resolved = false;
                for (var iterator = remaining.iterator(); iterator.hasNext();) {
                    QuestDefinition quest = iterator.next();
                    Integer column = quest.parents().stream().map(anchors::get).filter(Objects::nonNull)
                            .findFirst().orElse(null);
                    if (column == null) continue;
                    int row = rows.merge(column, 1, Integer::sum);
                    layout.add(new QuestSlot(quest, column, row, chapter, false, ""));
                    anchors.put(quest.id(), column);
                    branchRows = Math.max(branchRows, row);
                    iterator.remove();
                    resolved = true;
                }
                if (!resolved) {
                    // Cyclic or orphaned branches stay accessible rather than disappearing.
                    QuestDefinition quest = remaining.removeFirst();
                    anchors.put(quest.id(), columns);
                    layout.add(new QuestSlot(quest, columns++, 0, chapter, false, ""));
                }
            }
            int lastX = layout.stream().mapToInt(slot -> {
                var position = chapters.get(slot.chapter()).timeline().positions().get(slot.quest().id());
                return position == null ? 192 + slot.column() * STEP : position.x();
            }).max().orElse(220);
            int extent = Math.max(420, lastX + CARD_WIDTH + 100);
            nextSections.add(new Section(start, extent, columns, branchRows, List.copyOf(next)));
            allSlots.addAll(layout);
            start += extent;
        }
        sections = List.copyOf(nextSections);
        slots = List.copyOf(allSlots);
        rebuildRenderIndex();
        activeSection();
        updateLimits();
        updateBackdrop();
    }

    private void rebuildRenderIndex() {
        var byChapter = new ArrayList<Map<String, QuestSlot>>();
        var connections = new ArrayList<List<JourneyTimeline.Connection>>();
        for (int chapter = 0; chapter < chapters.size(); chapter++) byChapter.add(new LinkedHashMap<>());
        var nodes = new ArrayList<ViewportIndex.Entry<QuestSlot>>();
        indexedMinY = Double.POSITIVE_INFINITY;
        indexedMaxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            byChapter.get(slot.chapter()).put(slot.quest().id(), slot);
            int x = slotX(slot), y = slotY(slot) - lineY();
            indexedMinY = Math.min(indexedMinY, y);
            indexedMaxY = Math.max(indexedMaxY, y + slotHeight(slot));
            nodes.add(new ViewportIndex.Entry<>(slot, x - 24, y - 16,
                    x + CARD_WIDTH + 24, y + slotHeight(slot) + 48, i));
        }
        chapterSlots = List.copyOf(byChapter);
        chapterConnections = List.of();
        var paths = new ArrayList<Path>();
        for (int chapter = 0; chapter < chapters.size(); chapter++) {
            var edges = effectiveConnections(chapter);
            connections.add(edges);
            var main = chapterSlots.get(chapter).values().stream().filter(s -> s.branch() == 0).toList();
            if (!chapters.get(chapter).timeline().customConnections()) {
                int start = sections.get(chapter).start() + 100;
                if (!main.isEmpty()) paths.add(new Path(null, main.getFirst(), start, 0, 0, 0));
                if (chapter + 1 < sections.size() && !chapters.get(chapter + 1).timeline().customConnections())
                    paths.add(new Path(main.isEmpty() ? null : main.getLast(), null, start, 0,
                            sections.get(chapter + 1).start() + 100, 0));
            }
            for (var edge : edges) {
                boolean fromChapter = JourneyTimeline.chapterPoint(edge.from());
                boolean toChapter = JourneyTimeline.chapterPoint(edge.to());
                var from = fromChapter ? null : slot(chapter, edge.from());
                var to = toChapter ? null : slot(chapter, edge.to());
                if ((fromChapter || from != null) && (toChapter || to != null))
                    paths.add(new Path(from, to, fromChapter ? sections.get(chapter).start() + 100 : 0,
                            0, toChapter ? sections.get(chapter).start() + 100 : -4, 0));
            }
        }
        chapterConnections = List.copyOf(connections);
        var lines = new ArrayList<ViewportIndex.Entry<Path>>();
        var incident = new HashMap<QuestSlot, List<Path>>();
        for (int i = 0; i < paths.size(); i++) {
            var path = paths.get(i);
            if (path.from() != null) incident.computeIfAbsent(path.from(), ignored -> new ArrayList<>()).add(path);
            if (path.to() != null) incident.computeIfAbsent(path.to(), ignored -> new ArrayList<>()).add(path);
            int ax = renderedPathFromX(path), ay = renderedPathFromY(path);
            int bx = renderedPathToX(path), by = renderedPathToY(path);
            lines.add(new ViewportIndex.Entry<>(path, Math.min(ax, bx), Math.min(ay, by) - lineY(),
                    Math.max(ax, bx) + 1, Math.max(ay, by) - lineY() + 1, i));
        }
        slotIndex = new ViewportIndex<>(nodes);
        pathIndex = new ViewportIndex<>(lines);
        incidentPaths = Map.copyOf(incident);
        limitScale = -1;
    }

    private int pathFromX(Path path) { return path.from() == null ? path.fromX() : slotX(path.from()) + CARD_WIDTH; }
    private int pathFromY(Path path) { return path.from() == null ? lineY() + path.fromY() : anchorY(path.from()); }
    private int pathToX(Path path) { return path.to() == null ? path.toX() : slotX(path.to()) + path.toX(); }
    private int pathToY(Path path) { return path.to() == null ? lineY() + path.toY() : anchorY(path.to()); }
    private boolean verticalCategoryPath(Path path) {
        return path.from() != null && path.to() != null
                && path.from().categoryColumn() && path.to().categoryColumn()
                && path.from().column() == path.to().column();
    }
    private int renderedPathFromX(Path path) {
        return verticalCategoryPath(path) ? slotX(path.from()) + CARD_WIDTH / 2 : pathFromX(path);
    }
    private int renderedPathFromY(Path path) {
        return verticalCategoryPath(path) ? slotY(path.from()) + CARD_HEIGHT : pathFromY(path);
    }
    private int renderedPathToX(Path path) {
        return verticalCategoryPath(path) ? slotX(path.to()) + CARD_WIDTH / 2 : pathToX(path);
    }
    private int renderedPathToY(Path path) {
        return verticalCategoryPath(path) ? slotY(path.to()) : pathToY(path);
    }

    private List<QuestSlot> visibleSlots(double left, double top, double right, double bottom) {
        if (queriedSlotIndex == slotIndex && queryLeft == left && queryTop == top && queryRight == right
                && queryBottom == bottom && queryLineY == lineY() && queryDrag.equals(nodeDrag)
                && queryPreview == previewPosition) return queriedSlots;
        queriedSlotIndex = slotIndex;
        queryLeft = left; queryTop = top; queryRight = right; queryBottom = bottom; queryLineY = lineY();
        queryDrag = nodeDrag; queryPreview = previewPosition;
        var result = slotIndex.query(left, top - lineY(), right, bottom - lineY());
        if (nodeDrag.isBlank()) return queriedSlots = result;
        var moving = slot(nodeChapter, nodeDrag);
        if (moving == null || result.contains(moving)) return queriedSlots = result;
        var combined = new ArrayList<>(result);
        combined.add(moving);
        return queriedSlots = List.copyOf(combined);
    }

    private int chapterAt(double x) {
        int low = 0, high = sections.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sections.get(middle).start() <= x) low = middle + 1;
            else high = middle;
        }
        return Math.max(0, low - 1);
    }

    private void activeSection() {
        quests = sections.isEmpty() ? List.of() : sections.get(chapterIndex).quests();
        columns = sections.isEmpty() ? 0 : sections.get(chapterIndex).columns();
        branchRows = sections.isEmpty() ? 0 : sections.get(chapterIndex).branches();
    }

    private double chapterPosition(int index) {
        return Math.max(0, sections.get(index).start() + 64 - viewWidth() * .30);
    }

    private void followTimeline() {
        if (sections.isEmpty()) return;
        if (navigationChapter >= 0) {
            if (Math.abs(timelineX.position() - timelineX.target()) > .5) return;
            navigationChapter = -1;
            finishChapterEvent();
        }
        double probe = timelineX.position() + viewWidth() * .30;
        activateChapter(chapterAt(probe + .5 - 64));
    }

    private void activateChapter(int index) {
        if (index == chapterIndex) return;
        if (navigationChapter < 0) sendChapterEvent(index, false);
        long now = System.nanoTime();
        tabMotions.get(chapters.get(chapterIndex).id()).target(false, now, motionMillis());
        chapterIndex = index;
        tabMotions.get(chapters.get(index).id()).target(true, now, motionMillis());
        activeSection();
        ensureSelectedTab();
        updateBackdrop();
        initTools();
        if (navigationChapter < 0) sendChapterEvent(index, true);
    }

    private int motionMillis() { return animationMillis() == 0 ? 0 : 240; }
    private void selectChapter(int index) {
        if (index < 0 || index >= chapters.size() || index == chapterIndex) return;
        cancelPointer();
        sendChapterEvent(index, false);
        pendingChapterEvent = chapters.get(index).id();
        navigationChapter = index;
        timelineX.to(chapterPosition(index));
        if (animationMillis() == 0) timelineX.jump(timelineX.target());
        activateChapter(index);
        followTimeline();
    }
    private void sendChapterEvent(int index, boolean after) {
        String id = chapters.get(index).id();
        if (UNASSIGNED_CHAPTER.equals(id) || minecraft.getConnection() == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork.ChapterSwitch(id, after));
    }
    private void finishChapterEvent() {
        if (pendingChapterEvent.isBlank()) return;
        if (chapters.get(chapterIndex).id().equals(pendingChapterEvent)) sendChapterEvent(chapterIndex, true);
        pendingChapterEvent = "";
    }

    private double scale() {
        double fit = Math.max(.5, Math.min(1.5, Math.min(width / 960., height / 540.)));
        return Math.clamp(fit * timelineZoom, .5, 2.25);
    }
    private double viewWidth() { return width / scale(); }
    private double viewHeight() { return (bottom() - top()) / scale(); }
    private int top() { return width < 420 ? 98 : 60; }
    private int bottom() { return height - 23; }
    private int lineY() { return (int) Math.max(CARD_HEIGHT + 28, viewHeight() * .58); }
    private double chapterProgressMinimum() {
        return sections.isEmpty() ? 0 : sections.get(chapterIndex).start();
    }
    private double chapterProgressMaximum() {
        if (sections.isEmpty()) return 0;
        Section section = sections.get(chapterIndex);
        return Math.max(section.start(), section.start() + section.width() - viewWidth());
    }
    private double timelineProgress() {
        double minimum = chapterProgressMinimum();
        double range = chapterProgressMaximum() - minimum;
        return range <= 0 ? 0 : Math.clamp((timelineX.position() - minimum) / range, 0, 1);
    }
    private JourneyTimeline.Position position(QuestSlot slot) {
        if (slot.quest().id().equals(nodeDrag) && slot.chapter() == nodeChapter && previewPosition != null)
            return previewPosition;
        return chapters.get(slot.chapter()).timeline().positions().get(slot.quest().id());
    }
    private int slotX(QuestSlot slot) {
        var position = position(slot);
        return sections.get(slot.chapter()).start() + (position == null ? 192 + slot.column() * STEP : position.x());
    }
    private int slotY(QuestSlot slot) {
        var position = position(slot);
        if (position != null) return lineY() + position.y();
        if (slot.categoryColumn())
            return lineY() - CARD_HEIGHT + 13 + slot.branch() * (CARD_HEIGHT + 26);
        return slot.branch() == 0 ? lineY() - CARD_HEIGHT + 13 : lineY() + 60 + (slot.branch() - 1) * 64;
    }
    private int slotHeight(QuestSlot slot) {
        return slot.categoryColumn() || slot.branch() == 0 ? CARD_HEIGHT : 24;
    }
    private void updateLimits() {
        double currentScale = scale();
        if (limitScale == currentScale && previewPosition == null) return;
        limitScale = currentScale;
        int extent = sections.isEmpty() ? 0 : sections.getLast().start() + sections.getLast().width();
        double lastChapter = sections.isEmpty() ? 0 : chapterPosition(sections.size() - 1);
        timelineX.limit(Math.max(lastChapter, extent - viewWidth()));
        int contentTop = slots.isEmpty() ? 0 : lineY() + (int) indexedMinY;
        int contentBottom = slots.isEmpty() ? lineY() + 32 : lineY() + (int) indexedMaxY + 20;
        var moving = nodeDrag.isBlank() ? null : slot(nodeChapter, nodeDrag);
        if (moving != null) {
            contentTop = Math.min(contentTop, slotY(moving));
            contentBottom = Math.max(contentBottom, slotY(moving) + slotHeight(moving) + 20);
        }
        timelineY.range(sections.isEmpty() ? 0 : Math.min(-64, contentTop - 32),
                sections.isEmpty() ? 0 : Math.max(64, contentBottom + 48 - viewHeight()));
        tabs.limit(chapters.size() * tabWidth() - (tabsRight() - tabsLeft()));
    }

    private void advanceMotion() {
        long now = System.nanoTime();
        double dt = frameTime == 0 ? 0 : Math.clamp((now - frameTime) / 1e9, 0, .1);
        frameTime = now;
        updateLimits();
        timelineX.advance(dt, animationMillis() == 0);
        timelineY.advance(dt, animationMillis() == 0);
        tabs.advance(dt, animationMillis() == 0);
        followTimeline();
        tabMotions.values().forEach(motion -> motion.advance(now, motionMillis()));
        updateBackdrop();
    }

    @Override public void tick() {
        if (!canStyle() && editing) { editing = false; nodeDrag = wireFrom = ""; previewPosition = null; menu.clear(); initTools(); }
        if (historyPending != null && historyPending.isDone()) {
            String result;
            try { result = historyPending.join(); }
            catch (RuntimeException failure) { result = "journey_history_save"; }
            historyPending = null;
            if (result.equals("journey_history_undone") || result.equals("journey_history_redone")) {
                saveError = "";
                refresh();
            } else saveError = result;
            initTools();
        }
        if (pending != null && pending.isDone()) {
            String result;
            try { result = pending.join(); } catch (RuntimeException failure) { result = "config_save_failed"; }
            pending = null;
            previewPosition = null; nodeDrag = "";
            if (!result.equals("config_saved")) saveError = result;
            refresh(); initTools();
        }
        if (dragging || draggingTabs || draggingScrollbar
                || closeHeld || appearanceHeld || pressedArrow != 0) return;
        boolean definitionsChanged = catalogRevision != ClientQuestDefinitions.revision();
        boolean progressChanged = progressRevision != ClientQuestProgress.listRevision();
        if (definitionsChanged || progressChanged && !hiddenTasks.equals(progressHiddenTasks())) {
            refresh();
            focusRequestedQuest();
        } else if (progressChanged) progressRevision = ClientQuestProgress.listRevision();
    }

    private void updateBackdrop() {
        JourneyBackdrop chosen = chapters.isEmpty() ? null : chapters.get(chapterIndex).backgrounds().stream()
                .filter(value -> value.startX() <= Math.max(0, (navigationChapter >= 0
                        ? timelineX.target() : timelineX.position())
                        - sections.get(chapterIndex).start())
                        && (!value.source().isBlank() || !value.music().isBlank()))
                .max(Comparator.comparingInt(JourneyBackdrop::startX)).orElse(null);
        String key = chapters.isEmpty() ? "" : chapters.get(chapterIndex).id() + ":" + chosen;
        if (key.equals(backdropKey)) return;
        // Freeze the last displayed composite, including a partially completed fade.
        // Rapid tab switches therefore start from what the user actually saw.
        if (backdropFrameReady) {
            UiCompositor swap = backdropSnapshot;
            backdropSnapshot = backdropFrame;
            backdropFrame = swap;
            backdropFrameReady = false;
        }
        ClientMediaManager.release(mediaOwner);
        backdrop = chosen;
        backdropKey = key;
        backdropChanged = System.nanoTime();
    }

    private float backdropAlpha() {
        double value = animationMillis() == 0 ? 1
                : Math.clamp((System.nanoTime() - backdropChanged) / 360_000_000., 0, 1);
        return (float) (value * value * (3 - 2 * value));
    }
    private void drawBackdrop(GuiGraphics graphics) {
        float alpha = backdropAlpha();
        backdropIncoming.capture(graphics, () -> {
            graphics.fill(0, 0, width, height, BACKGROUND);
            if (backdrop != null) paintBackdrop(graphics, backdrop, 1);
        });
        backdropFrame.capture(graphics, () -> {
            graphics.fill(0, 0, width, height, BACKGROUND);
            if (alpha < 1) backdropSnapshot.band(graphics, width, height, 0, height, 0, 1);
            backdropIncoming.band(graphics, width, height, 0, height, 0, alpha);
        });
        backdropFrame.band(graphics, width, height, 0, height, 0, 1);
        backdropFrameReady = true;
        if (alpha >= 1) backdropSnapshot.close();
    }
    private void paintBackdrop(GuiGraphics graphics, JourneyBackdrop value, float alpha) {
        int w = Math.max(1, (int) Math.round(width * value.scale()));
        int h = Math.max(1, (int) Math.round(height * value.scale()));
        int x = (width - w) / 2 + value.offsetX(), y = (height - h) / 2 + value.offsetY();
        if (!value.music().isBlank()) ClientMediaManager.renderAudio(graphics, mediaOwner, "music", value.music(),
                0, 0, 1, 1, 1, true, true, false, 1, false, "");
        if (value.source().isBlank()) return;
        if (value.type().equals("video")) ClientMediaManager.renderVideo(graphics, mediaOwner, backdropKey,
                value.source(), x, y, w, h, alpha, true, true, true, 0, value.mode(), "");
        else ClientMediaManager.renderImageScaled(graphics, value.source(), x, y, w, h, alpha, value.mode());
    }

    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        advanceMotion();
        backdrop(graphics, BACKGROUND);
        drawBackdrop(graphics);
        graphics.fillGradient(0, 0, width, top() + 18, 0xBF292532, 0x00292532);
        graphics.fillGradient(0, bottom() - 22, width, height, 0x00202530, 0x88202530);
        icon(graphics, "header", "guide", 13, 12, 20, 0xFFF1D9A8);
        text(graphics, "header", title.getString(), 38, 18,
                width < 420 ? Math.max(20, width - (canStyle() ? editing ? 195 : 135 : 115))
                        : tabsLeft() - 62, 0xFFF5E8CF, false);
        drawTabs(graphics);
        drawTimelineProgress(graphics, mouseX, mouseY);
        icon(graphics, "close", "close", width - 31, 12, 21,
                closeHeld ? 0xFFE3BE75 : 0xFFF5E8CF);
        if (chapters.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.xero_quest_journal.journey.empty"),
                    width / 2, height / 2, 0xFFF1E8D8);
            return;
        }
        graphics.enableScissor(0, top(), width, bottom());
        graphics.pose().pushPose();
        graphics.pose().translate(0, top(), 0);
        graphics.pose().scale((float) scale(), (float) scale(), 1);
        graphics.pose().translate(-timelineX.position(), -timelineY.position(), 0);
        visibleSlots = visibleSlots(timelineX.position(), timelineY.position(),
                timelineX.position() + viewWidth(), timelineY.position() + viewHeight());
        drawTimeline(graphics);
        String hovered = dragging || inputBlocked() ? "" : questAt(mouseX, mouseY);
        for (QuestSlot slot : visibleSlots) drawQuest(graphics, slot, hovered.equals(slot.quest().id()));
        graphics.pose().popPose();
        graphics.disableScissor();
        drawZoomSlider(graphics);
        super.renderContents(graphics, mouseX, mouseY, delta);
        renderAnimatedMenu(graphics, "journey-menu", menu.isEmpty() ? null : menu.getFirst(),
                menu.isEmpty() ? viewport(0, 0, 0, 0)
                        : viewport(menuX - 2, menuY - 2, menu.getFirst().getRight() + 2, menu.getLast().getBottom() + 2),
                () -> {
                    graphics.fill(menuX - 2, menuY - 2, menu.getFirst().getRight() + 2,
                            menu.getLast().getBottom() + 2, 0xFF20262F);
                    menu.forEach(button -> button.render(graphics, mouseX, mouseY, delta));
                });
        if (!saveError.isBlank()) graphics.drawString(font,
                Component.translatable("screen.xero_quest_journal.action." + saveError), 12, height - 24, 0xFFFF8888);
        if (menu.isEmpty() && children().stream().anyMatch(child ->
                child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                        && widget.isHoveredOrFocused() && widget.getTooltip() != null)) {
            // Screen renders the queued widget tooltip once, after this pass; keep it above scene depth.
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                    net.minecraft.client.Minecraft.ON_OSX);
        }
    }

    private int tabsLeft() { return width < 420 ? 30 : Math.max(146, width / 2 - Math.min(200, width / 4)); }
    private int tabsRight() { return width < 420 ? width - 30
            : Math.min(width - (canStyle() ? editing ? 162 : 102 : 56), width / 2 + Math.min(200, width / 4)); }
    private int tabsTop() { return width < 420 ? 41 : 7; }
    private int tabWidth() {
        int available = Math.max(1, tabsRight() - tabsLeft());
        return Math.max(68, Math.min(124, available / Math.max(1, Math.min(3, chapters.size()))));
    }
    private void ensureSelectedTab() {
        updateLimits();
        double left = chapterIndex * tabWidth(), right = left + tabWidth();
        int viewport = tabsRight() - tabsLeft();
        if (left < tabs.position()) tabs.to(left);
        else if (right > tabs.position() + viewport) tabs.to(right - viewport);
    }
    private void drawTabs(GuiGraphics graphics) {
        if (chapters.isEmpty()) return;
        int left = tabsLeft(), right = tabsRight(), y = tabsTop(), w = tabWidth();
        graphics.enableScissor(left, y, right, y + 34);
        int first = Math.max(0, (int) Math.floor(tabs.position() / w));
        int end = Math.min(chapters.size(), (int) Math.ceil((tabs.position() + right - left) / w) + 1);
        for (int i = first; i < end; i++) {
            int x = left + i * w - (int) Math.round(tabs.position());
            if (x >= right || x + w <= left) continue;
            float amount = tabMotions.get(chapters.get(i).id()).eased();
            if (amount > 0) graphics.fillGradient(x + 3, y, x + w - 3, y + 27,
                    0x00FFE6AE, Math.round(65 * amount) << 24 | 0xFFE6AE);
            var tabStyle = appearance(i).get("tab");
            int iconSpace = tabStyle.iconVisible() ? 18 : 0;
            JourneyAppearanceRenderer.icon(graphics, tabStyle, chapterIcon(i), x + 5, y + 8, 14, 14, 0xFFF5E8CF, false);
            JourneyAppearanceRenderer.text(graphics, font, tabStyle, chapters.get(i).name(), x + (w + iconSpace) / 2, y + 10,
                    w - 14 - iconSpace, i == chapterIndex ? 0xFFFFE6AE : 0xFFD5D1DA, true);
            graphics.fill(x + 3, y + 30 - (i == chapterIndex ? 5 : 1), x + w - 3, y + 30,
                    i == chapterIndex ? 0xFFF2D29A : PROGRESS_ALPHA << 24 | 0x7C8792);
        }
        graphics.disableScissor();
        icon(graphics, "previous", "back", left - 18, y + 10, 10,
                tabs.position() > .5 ? 0xFFF5E8CF : 0x668B8495);
        icon(graphics, "next", "@next", right + 8, y + 10, 10,
                tabs.position() < tabs.maximum() - .5 ? 0xFFF5E8CF : 0x668B8495);
    }

    private boolean timelineProgressHit(double x, double y) {
        if (chapters.isEmpty()) return false;
        int[] bounds = timelineProgressBounds();
        int left = Math.max(bounds[0], tabsLeft()), right = Math.min(bounds[2], tabsRight());
        return x >= left && x < right && y >= bounds[1] - 6 && y < bounds[3] + 7;
    }

    private void dragTimelineProgress(double x) {
        int[] bounds = timelineProgressBounds();
        int left = Math.max(bounds[0], tabsLeft()), right = Math.min(bounds[2], tabsRight());
        if (right <= left) return;
        double fraction = Math.clamp((x - left) / (right - left), 0, 1);
        double minimum = chapterProgressMinimum();
        timelineX.jump(minimum + fraction * (chapterProgressMaximum() - minimum));
        followTimeline();
    }

    private void drawTimelineProgress(GuiGraphics graphics, int mouseX, int mouseY) {
        if (chapters.isEmpty() || chapterIndex < 0 || chapterIndex >= chapters.size()) return;
        int[] bounds = timelineProgressBounds();
        int left = bounds[0], top = bounds[1], right = bounds[2], bottom = bounds[3];
        int visibleLeft = Math.max(left, tabsLeft());
        int visibleRight = Math.min(right, tabsRight());
        if (visibleRight <= visibleLeft) return;
        int markerX = left + (int) Math.round((right - left) * timelineProgress());
        if (draggingTimelineProgress || timelineProgressHit(mouseX, mouseY))
            graphics.renderOutline(visibleLeft, top - 4, visibleRight - visibleLeft, 10, 0xFFFFE6AE);
        graphics.enableScissor(tabsLeft(), top + 1 - PROGRESS_MARKER_HEIGHT / 2,
                tabsRight(), top + 1 + PROGRESS_MARKER_HEIGHT / 2);
        graphics.fill(visibleLeft, top, Math.clamp(markerX, visibleLeft, visibleRight), bottom,
                0xFFFFE6AE);
        float alpha = PROGRESS_ALPHA / 255.0F;
        graphics.setColor(1, 1, 1, alpha);
        graphics.blit(PROGRESS_MARKER, markerX - PROGRESS_MARKER_WIDTH / 2,
                top + 1 - PROGRESS_MARKER_HEIGHT / 2,
                PROGRESS_MARKER_WIDTH, PROGRESS_MARKER_HEIGHT,
                0, 0, 32, 64, 32, 64);
        graphics.setColor(1, 1, 1, 1);
        graphics.disableScissor();
    }

    private int[] timelineProgressBounds() {
        int width = tabWidth();
        int left = tabsLeft() + chapterIndex * width - (int) Math.round(tabs.position()) + 3;
        int top = tabsTop() + 29;
        return new int[]{left, top, left + width - 6, top + 1};
    }

    private String chapterIcon(int index) {
        String icon = chapters.get(index).icon();
        if (!icon.isBlank()) return icon;
        String[] defaults = {"minecraft:chest", "minecraft:book", "minecraft:compass",
                "minecraft:ender_eye", "minecraft:amethyst_shard", "minecraft:clock", "minecraft:map"};
        return defaults[index % defaults.length];
    }
    private List<JourneyTimeline.Connection> effectiveConnections(int chapter) {
        if (chapter < chapterConnections.size()) return chapterConnections.get(chapter);
        var timeline = chapters.get(chapter).timeline();
        if (timeline.customConnections()) return timeline.connections();
        var result = new ArrayList<JourneyTimeline.Connection>();
        var members = chapterSlots.get(chapter);
        if (members.values().stream().anyMatch(QuestSlot::categoryColumn)) {
            var lanes = new LinkedHashMap<String, List<QuestSlot>>();
            members.values().stream().filter(QuestSlot::categoryColumn)
                    .forEach(slot -> lanes.computeIfAbsent(slot.category(), ignored -> new ArrayList<>()).add(slot));
            for (var lane : lanes.values()) {
                lane.sort(Comparator.comparingInt(QuestSlot::branch));
                for (int i = 1; i < lane.size(); i++)
                    result.add(new JourneyTimeline.Connection(lane.get(i - 1).quest().id(),
                            lane.get(i).quest().id()));
            }
            return List.copyOf(result);
        }
        var main = members.values().stream().filter(slot -> slot.branch() == 0).toList();
        for (int i = 1; i < main.size(); i++)
            result.add(new JourneyTimeline.Connection(main.get(i - 1).quest().id(), main.get(i).quest().id()));
        for (var slot : members.values()) if (slot.branch() > 0)
            slot.quest().parents().stream().filter(members::containsKey).findFirst()
                    .ifPresent(id -> result.add(new JourneyTimeline.Connection(id, slot.quest().id())));
        return List.copyOf(result);
    }
    private QuestSlot slot(int chapter, String id) {
        return chapter < 0 || chapter >= chapterSlots.size() ? null : chapterSlots.get(chapter).get(id);
    }
    private int anchorY(QuestSlot slot) {
        return slotY(slot) + (slot.categoryColumn() || slot.branch() == 0 ? 100 : 12);
    }
    private int chapterMarkerAt(double x, double y) {
        if (!inContent(x, y)) return -1;
        double worldX = x / scale() + timelineX.position();
        double worldY = (y - top()) / scale() + timelineY.position();
        for (int chapter = 0; chapter < sections.size(); chapter++)
            if (Math.abs(worldX - sections.get(chapter).start() - 100) <= 14
                    && Math.abs(worldY - lineY()) <= 14) return chapter;
        return -1;
    }
    private void connection(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
        int middle = (ax + bx) / 2;
        g.fill(Math.min(ax, middle), ay, Math.max(ax, middle) + 1, ay + 1, color);
        g.fill(middle, Math.min(ay, by), middle + 1, Math.max(ay, by) + 1, color);
        g.fill(Math.min(middle, bx), by, Math.max(middle, bx) + 1, by + 1, color);
    }
    private void drawTimeline(GuiGraphics graphics) {
        int y = lineY();
        var visiblePaths = pathIndex.query(timelineX.position(), timelineY.position() - y,
                timelineX.position() + viewWidth(), timelineY.position() + viewHeight() - y);
        var moving = nodeDrag.isBlank() ? null : slot(nodeChapter, nodeDrag);
        for (var path : visiblePaths) {
            if (moving != null && (path.from() == moving || path.to() == moving)) continue;
            connection(graphics, renderedPathFromX(path), renderedPathFromY(path),
                    renderedPathToX(path), renderedPathToY(path), 0xBCE9E3D5);
        }
        // Moving endpoints are overlaid without rebuilding the static hierarchy each frame.
        if (moving != null) for (var path : incidentPaths.getOrDefault(moving, List.of()))
            connection(graphics, renderedPathFromX(path), renderedPathFromY(path),
                    renderedPathToX(path), renderedPathToY(path), 0xBCE9E3D5);
        if (editing && !wireFrom.isBlank()) {
            int fromX, fromY;
            if (JourneyTimeline.chapterPoint(wireFrom)) {
                fromX = sections.get(nodeChapter).start() + 100;
                fromY = y;
            } else {
                var from = slot(nodeChapter, wireFrom);
                if (from == null) return;
                fromX = slotX(from) + CARD_WIDTH;
                fromY = anchorY(from);
            }
            connection(graphics, fromX, fromY,
                    (int) (pointerX / scale() + timelineX.position()),
                    (int) ((pointerY - top()) / scale() + timelineY.position()), 0xFFFFDE8F);
        }
        int firstChapter = chapterAt(timelineX.position() - 144);
        int endChapter = Math.min(sections.size(), chapterAt(timelineX.position() + viewWidth()) + 1);
        for (int i = firstChapter; i < endChapter; i++) {
            int sx = sections.get(i).start();
            if (sx + 144 < timelineX.position() || sx > timelineX.position() + viewWidth()) continue;
            var chapter = chapters.get(i);
            var chapterStyle = appearance(i);
            JourneyAppearanceRenderer.icon(graphics, chapterStyle.get("chapter"),
                    chapterIcon(i), sx + 78, y - 90, 44, 44, 0xFFF5E8CF, false);
            JourneyAppearanceRenderer.text(graphics, font, chapterStyle.get("chapter"), chapter.name(),
                    sx + 100, y - 28, 138, 0xFFF5E8CF, true);
            JourneyAppearanceRenderer.icon(graphics, chapterStyle.get("marker"), "@marker",
                    sx + 89, y - 11, 22, 22,
                    editing && i == nodeChapter && JourneyTimeline.chapterPoint(wireFrom)
                            ? 0xFFFFDE8F : 0xFFFFFFFF, false);
            if (sections.get(i).quests().isEmpty())
                graphics.drawString(font, label("no_quests"), sx + 140, y - 4, 0xFFF1E8D8, false);
        }
        for (QuestSlot slot : visibleSlots) {
            if (!slot.categoryColumn() || slot.branch() != 0) continue;
            int x = slotX(slot), categoryY = slotY(slot);
            if (x + CARD_WIDTH < timelineX.position() || x > timelineX.position() + viewWidth()) continue;
            graphics.drawString(font, label("category." + slot.category()), x, categoryY - 14, 0xFFF1D9A8, false);
        }
        for (QuestSlot slot : visibleSlots) {
            int x = slotX(slot);
            if (x + CARD_WIDTH < timelineX.position() || x - 24 > timelineX.position() + viewWidth()) continue;
            var marker = slot.quest().journeyAppearance().over(appearance(slot.chapter())).get("marker");
            if (slot.branch() > 0 && !slot.categoryColumn()) {
                int sy = slotY(slot) + 12;
                JourneyAppearanceRenderer.icon(graphics, marker, "@marker", x - 7, sy - 2, 4, 4, 0xFFF5E8CF, false);
            } else JourneyAppearanceRenderer.icon(graphics, marker, "@marker", x - 8, anchorY(slot) - 3, 6, 6, 0xFFF6E5BD, false);
        }
    }

    private void drawQuest(GuiGraphics graphics, QuestSlot slot, boolean hover) {
        int x = slotX(slot), y = slotY(slot), h = slotHeight(slot);
        if (x + CARD_WIDTH < timelineX.position() || x > timelineX.position() + viewWidth()
                || y + h + 24 < timelineY.position() || y > timelineY.position() + viewHeight()) return;
        var questAppearance = slot.quest().journeyAppearance().over(appearance(slot.chapter()));
        var state = ClientQuestProgress.view(slot.quest().id());
        boolean complete = state != null && state.completed();
        graphics.fill(x + 2, y + 3, x + CARD_WIDTH + 3, y + h + 3, 0x44202632);
        boolean focused = slot.quest().id().equals(selectedQuest);
        boolean fullCard = slot.categoryColumn() || slot.branch() == 0;
        if (hover || focused || fullCard)
            graphics.renderOutline(x - 2, y - 2, CARD_WIDTH + 4, h + 4, hover || focused ? 0xFFFFDF9C : 0x99F3EADD);
        int labelY = y;
        if (fullCard) {
            graphics.fill(x, y, x + CARD_WIDTH, y + 84, 0xFF303C46);
            String image = slot.quest().hotEvent().image().isBlank() ? slot.quest().icon() : slot.quest().hotEvent().image();
            JourneyAppearanceRenderer.icon(graphics, questAppearance.get("card"), image, x + 1, y + 1,
                    CARD_WIDTH - 2, 82, 0xFFFFFFFF, true);
            graphics.renderOutline(x, y, CARD_WIDTH, 84, 0xFFF5F0E8);
            for (int cornerX : new int[]{x + 3, x + CARD_WIDTH - 4})
                    for (int cornerY : new int[]{y + 3, y + 80})
                        graphics.fill(cornerX - 1, cornerY - 1, cornerX + 2, cornerY + 2, 0xFFF5F0E8);
            labelY = y + 88;
        }
        graphics.fill(x, labelY, x + CARD_WIDTH, labelY + 24, hover ? 0xFFFFF5DD : 0xFFF5F3EE);
        String element = fullCard ? "card" : "branch";
        var elementStyle = questAppearance.get(element);
        if (!elementStyle.background().isBlank())
            ClientMediaManager.renderImageCover(graphics, elementStyle.background(), x, labelY, CARD_WIDTH, 24, 1);
        graphics.fill(x, labelY, x + 3, labelY + 24,
                slot.quest().type().equals("legendary") ? 0xFFFFAB57 : 0xFFEBC548);
        int iconSpace = !fullCard && elementStyle.iconVisible()
                && (!elementStyle.icon().isBlank() || !slot.quest().icon().isBlank()) ? 24 : 0;
        if (iconSpace > 0) JourneyAppearanceRenderer.icon(graphics, elementStyle, slot.quest().icon(),
                x + 7, labelY - 3, 24, 27, 0xFFFFFFFF, false);
        JourneyAppearanceRenderer.text(graphics, font, elementStyle, QuestText.title(slot.quest()).getString(), x + 8 + iconSpace, labelY + 8,
                CARD_WIDTH - (complete ? 33 : 16) - iconSpace,
                elementStyle.background().isBlank() ? 0xFF394452 : 0xFFFFFFFF, false);
        if (complete) JourneyAppearanceRenderer.icon(graphics, questAppearance.get("complete"), "@complete",
                x + CARD_WIDTH - 24, labelY + 3, 18, 18, 0xFFFFFFFF, false);
        JourneyAppearanceRenderer.text(graphics, font, questAppearance.get("subtitle"), slot.quest().legendary().subtitle(),
                x + 7, labelY + 31, CARD_WIDTH - 14, 0xFFF5E8CF, false);
        if (editing && canEditChapter(slot.chapter())) {
            MailDecor.circle(graphics, x - 13, anchorY(slot) - 4, 8, 0xFFFFDE8F);
            MailDecor.circle(graphics, x + CARD_WIDTH + 5, anchorY(slot) - 4, 8, 0xFFFFDE8F);
        }
    }

    private int zoomSliderWidth() { return Math.min(150, width / 3); }
    private int zoomSliderLeft() { return (width - zoomSliderWidth()) / 2; }
    private int zoomSliderThumbX() {
        double progress = (timelineZoom - MIN_TIMELINE_ZOOM) / (MAX_TIMELINE_ZOOM - MIN_TIMELINE_ZOOM);
        return zoomSliderLeft() + 3 + (int) Math.round(progress * (zoomSliderWidth() - 6));
    }
    private void drawZoomSlider(GuiGraphics graphics) {
        int left = zoomSliderLeft(), track = zoomSliderWidth(), y = height - 12;
        graphics.fill(left, y, left + track, y + 2, 0x806B6572);
        icon(graphics, "scroll_thumb", "@marker", zoomSliderThumbX() - 4, y - 3, 8, 0xFFF8E3AD);
    }
    private void setTimelineZoom(double value, double anchorX, double anchorY) {
        double oldScale = scale();
        double worldX = timelineX.position() + anchorX / oldScale;
        double worldY = timelineY.position() + (anchorY - top()) / oldScale;
        timelineZoom = Math.clamp(value, MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM);
        updateLimits();
        double nextScale = scale();
        timelineX.jump(worldX - anchorX / nextScale);
        timelineY.jump(worldY - (anchorY - top()) / nextScale);
        followTimeline();
    }
    private boolean inContent(double x, double y) { return x >= 0 && x < width && y >= top() && y < bottom(); }
    private boolean inTabs(double x, double y) {
        return x >= tabsLeft() && x < tabsRight() && y >= tabsTop() && y < tabsTop() + 34;
    }
    private int tabAt(double x, double y) {
        if (!inTabs(x, y)) return -1;
        int index = (int) ((x - tabsLeft() + tabs.position()) / tabWidth());
        return index < chapters.size() ? index : -1;
    }
    private String questAt(double x, double y) {
        if (!inContent(x, y)) return "";
        double localX = x / scale() + timelineX.position(), localY = (y - top()) / scale() + timelineY.position();
        for (QuestSlot slot : visibleSlots(localX, localY, localX, localY))
            if (localX >= slotX(slot) && localX < slotX(slot) + CARD_WIDTH
                    && localY >= slotY(slot) && localY < slotY(slot) + slotHeight(slot)) return slot.quest().id();
        return "";
    }
    private boolean atClose(double x, double y) {
        return JourneyAppearanceRenderer.hit(style("close"), x, y, width - 31, 12, 21);
    }
    private boolean atAppearance(double x, double y) {
        return canStyle() && x >= width - 65 && x < width - 39 && y >= 9 && y < 36;
    }
    private boolean atArrow(double x, double y, int direction) {
        return JourneyAppearanceRenderer.hit(style(direction < 0 ? "previous" : "next"), x, y,
                direction < 0 ? tabsLeft() - 18 : tabsRight() + 8, tabsTop() + 10, 10);
    }
    private void cancelPointer() {
        pendingChapterEvent = "";
        if (navigationChapter >= 0) timelineX.to(timelineX.position());
        navigationChapter = -1;
        dragging = draggingTabs = draggingTimelineProgress = draggingScrollbar = draggingVerticalScrollbar
                = closeHeld = appearanceHeld = moved = false;
        pressedQuest = "";
        pressedTab = -1;
        pressedArrow = 0;
        timelineX.release(false);
        timelineY.release(false);
        tabs.release(false);
    }

    private QuestSlot portAt(double x, double y, boolean output) {
        double wx = x / scale() + timelineX.position(), wy = (y - top()) / scale() + timelineY.position();
        return visibleSlots(wx - 9, wy - 9, wx + 9, wy + 9).stream()
                .filter(slot -> Math.abs(wx - (slotX(slot) + (output ? CARD_WIDTH + 9 : -9))) < 9
                && Math.abs(wy - anchorY(slot)) < 9).findFirst().orElse(null);
    }
    private void saveConnection(String target) {
        boolean targetChapter = JourneyTimeline.chapterPoint(target);
        var targetSlot = targetChapter ? null : slot(nodeChapter, target);
        if (target == null || target.isBlank()
                || JourneyTimeline.chapterPoint(wireFrom) && targetChapter
                || target.equals(wireFrom) || !targetChapter && targetSlot == null) {
            wireFrom = "";
            return;
        }
        var value = chapters.get(nodeChapter);
        var connections = new ArrayList<>(effectiveConnections(nodeChapter));
        var edge = new JourneyTimeline.Connection(wireFrom, target);
        if (!connections.contains(edge)) connections.add(edge);
        wireFrom = "";
        saveChapter(nodeChapter, memberIds(nodeChapter),
                new JourneyTimeline(value.timeline().positions(), connections, true), value.backgrounds());
    }
    private void openMenu(double x, double y) {
        menu.clear();
        var entries = new ArrayList<Map.Entry<String, Runnable>>();
        String id = questAt(x, y);
        var selected = slots.stream().filter(slot -> slot.quest().id().equals(id)).findFirst().orElse(null);
        if (selected != null) {
            selectedQuest = id;
            int chapter = selected.chapter();
            entries.add(Map.entry("properties", () -> minecraft.setScreen(QuestScreen.hotEventProperties(this, "task", id))));
            if (canEditChapter(chapter)) {
                entries.add(Map.entry("connect", () -> { wireFrom = id; nodeChapter = chapter; }));
                entries.add(Map.entry("disconnect", () -> {
                    var value = chapters.get(chapter);
                    saveChapter(chapter, memberIds(chapter), new JourneyTimeline(value.timeline().positions(),
                            effectiveConnections(chapter).stream().filter(edge ->
                                    !edge.from().equals(id) && !edge.to().equals(id)).toList(), true), value.backgrounds());
                }));
                entries.add(Map.entry("remove_task", () -> {
                    var value = chapters.get(chapter);
                    saveChapter(chapter, memberIds(chapter).stream().filter(task -> !task.equals(id)).toList(),
                            value.timeline(), value.backgrounds());
                }));
            }
        }
        if (canEditChapter(chapterIndex)) {
            entries.add(Map.entry("edit_chapter_icon", () -> editChapter("icon")));
            entries.add(Map.entry("edit_chapter_name", () -> editChapter("text")));
            entries.add(Map.entry("appearance", this::editAppearance));
            entries.add(Map.entry("tasks", this::chooseTasks));
            entries.add(Map.entry("edit_backgrounds", this::editBackgrounds));
        }
        int w = Math.min(180, width - 16);
        menuX = Math.clamp((int) x, 8, Math.max(8, width - w - 8));
        menuY = Math.clamp((int) y, 8, Math.max(8, height - entries.size() * 22 - 8));
        var styles = ButtonStyleSheet.load();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            menu.add(new StyledButton(menuX, menuY + i * 22, w, 22, label(entry.getKey()), ignored -> {
                menu.clear();
                if (canStyle() && pending == null) entry.getValue().run();
            }, styles, "journey-menu-" + entry.getKey(), "context") {
                @Override protected void renderContent(GuiGraphics g, ButtonStyle style, int color) {
                    String key = entry.getKey();
                    String icon = switch (key) {
                        case "edit_chapter_icon", "edit_backgrounds" -> "item";
                        case "edit_chapter_name", "properties", "appearance" -> "edit";
                        case "connect" -> "visual";
                        case "disconnect" -> "unlink";
                        case "remove_task" -> "delete";
                        default -> "add";
                    };
                    g.fill(getX() + 2, getY() + 3, getX() + 4, getBottom() - 3,
                            key.equals("remove_task") ? 0xFFE58D80 : 0xFFB9CDAB);
                    JournalNavButton.drawIcon(g, icon, getX() + 8, getY() + 4, 14, 0xFFDFCD9D);
                    g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - 34)),
                            getX() + 28, getY() + (getHeight() - font.lineHeight) / 2, color, false);
                }
            });
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (!menu.isEmpty()) {
            for (var item : List.copyOf(menu)) if (item.mouseClicked(x, y, button)) return true;
            menu.clear();
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (editing && (pending != null || historyPending != null)) return true;
        if (editing && button == 1 && (inContent(x, y) || inTabs(x, y))) {
            int tab = tabAt(x, y);
            if (tab >= 0 && tab < chapters.size()) activateChapter(tab);
            else if (inContent(x, y) && questAt(x, y).isBlank())
                activateChapter(chapterAt(x / scale() + timelineX.position()));
            openMenu(x, y); return true;
        }
        if (button != 0) return false;
        cancelPointer();
        if (atAppearance(x, y)) { appearanceHeld = true; return true; }
        if (atClose(x, y)) { closeHeld = true; return true; }
        dragX = x; dragY = y;
        if (timelineProgressHit(x, y)) {
            draggingTimelineProgress = true;
            dragTimelineProgress(x);
            return true;
        }
        if (!chapters.isEmpty()) {
            if (atArrow(x, y, -1)) pressedArrow = -1;
            if (atArrow(x, y, 1)) pressedArrow = 1;
            if (pressedArrow != 0) return true;
        }
        if (!chapters.isEmpty() && y >= tabsTop() && y < tabsTop() + 34) {
            if (inTabs(x, y)) {
                pressedTab = tabAt(x, y);
                draggingTabs = true;
                dragScrollX = tabs.position();
                tabs.begin();
                return true;
            }
        }
        if (y >= height - 20 && y < height
                && x >= zoomSliderLeft() - 8 && x < zoomSliderLeft() + zoomSliderWidth() + 8) {
            draggingScrollbar = true;
            dragZoomSlider(x);
            return true;
        }
        if (!inContent(x, y)) return false;
        if (editing) {
            if (!wireFrom.isBlank()) {
                var target = portAt(x, y, false);
                int markerChapter = chapterMarkerAt(x, y);
                if (target != null) saveConnection(target.chapter() == nodeChapter
                        ? target.quest().id() : "");
                else if (markerChapter == nodeChapter) saveConnection(JourneyTimeline.CHAPTER_POINT);
                else {
                    String id = questAt(x, y);
                    var task = slots.stream().filter(slot -> slot.quest().id().equals(id)
                            && slot.chapter() == nodeChapter).findFirst().orElse(null);
                    saveConnection(task == null ? "" : task.quest().id());
                }
                return true;
            }
            int markerChapter = chapterMarkerAt(x, y);
            if (markerChapter >= 0 && canEditChapter(markerChapter)) {
                nodeChapter = markerChapter;
                wireFrom = JourneyTimeline.CHAPTER_POINT;
                return true;
            }
            var output = portAt(x, y, true);
            if (output != null && canEditChapter(output.chapter())) {
                nodeChapter = output.chapter(); wireFrom = output.quest().id(); return true;
            }
            String id = questAt(x, y);
            var slot = slots.stream().filter(value -> value.quest().id().equals(id)).findFirst().orElse(null);
            if (slot != null && canEditChapter(slot.chapter())) {
                selectedQuest = nodeDrag = id; nodeChapter = slot.chapter();
                nodeGrabX = x / scale() + timelineX.position() - slotX(slot);
                nodeGrabY = (y - top()) / scale() + timelineY.position() - slotY(slot);
                previewPosition = null;
                return true;
            }
        }
        if (x >= width - 12 && timelineY.maximum() > timelineY.minimum()) {
            draggingVerticalScrollbar = true;
            dragVerticalScrollbar(y);
            return true;
        }
        dragging = true;
        dragScrollX = timelineX.position(); dragScrollY = timelineY.position();
        pressedQuest = questAt(x, y);
        timelineX.begin(); timelineY.begin();
        return true;
    }
    private void dragZoomSlider(double x) {
        double progress = Math.clamp((x - zoomSliderLeft() - 3) / Math.max(1, zoomSliderWidth() - 6), 0, 1);
        setTimelineZoom(MIN_TIMELINE_ZOOM + progress * (MAX_TIMELINE_ZOOM - MIN_TIMELINE_ZOOM),
                width / 2.0, top() + (bottom() - top()) / 2.0);
    }
    private void dragVerticalScrollbar(double y) {
        timelineY.jump(timelineY.minimum() + (y - top() - 16)
                / Math.max(1, bottom() - top() - 32)
                * (timelineY.maximum() - timelineY.minimum()));
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || inputBlocked()) return false;
        pointerX = x; pointerY = y;
        if (draggingTimelineProgress) {
            dragTimelineProgress(x);
            return true;
        }
        if (!wireFrom.isBlank()) return true;
        if (!nodeDrag.isBlank() && pending == null) {
            moved |= Math.hypot(x - dragX, y - dragY) > 4;
            if (moved) previewPosition = new JourneyTimeline.Position(
                    Math.clamp((int) Math.round(x / scale() + timelineX.position() - nodeGrabX
                            - sections.get(nodeChapter).start()), 100, 100000),
                    Math.clamp((int) Math.round((y - top()) / scale() + timelineY.position()
                            - nodeGrabY - lineY()), -100000, 100000));
            return true;
        }
        if (draggingScrollbar) { dragZoomSlider(x); return true; }
        if (draggingVerticalScrollbar) { dragVerticalScrollbar(y); return true; }
        if (!dragging && !draggingTabs) return false;
        moved |= Math.hypot(x - dragX, y - dragY) > 4;
        if (!moved) return true;
        if (draggingTabs) tabs.drag(dragScrollX + dragX - x);
        else {
            timelineX.drag(dragScrollX + (dragX - x) / scale());
            timelineY.drag(dragScrollY + (dragY - y) / scale());
            followTimeline();
        }
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (!menu.isEmpty()) {
            List.copyOf(menu).forEach(item -> item.mouseReleased(x, y, button));
            return true;
        }
        if (super.mouseReleased(x, y, button)) return true;
        if (button != 0) return false;
        if (inputBlocked()) { cancelPointer(); return true; }
        if (draggingTimelineProgress) {
            draggingTimelineProgress = false;
            return true;
        }
        if (!wireFrom.isBlank()) {
            var target = portAt(x, y, false);
            int markerChapter = chapterMarkerAt(x, y);
            if (target != null && target.chapter() == nodeChapter) saveConnection(target.quest().id());
            else if (markerChapter == nodeChapter) saveConnection(JourneyTimeline.CHAPTER_POINT);
            else if (target != null) saveConnection("");
            return true;
        }
        if (!nodeDrag.isBlank()) {
            if (previewPosition != null && moved && pending == null) {
                var value = chapters.get(nodeChapter);
                var positions = new LinkedHashMap<>(value.timeline().positions());
                positions.put(nodeDrag, previewPosition);
                saveChapter(nodeChapter, memberIds(nodeChapter),
                        new JourneyTimeline(positions, value.timeline().connections(),
                                value.timeline().customConnections()), value.backgrounds());
            } else { nodeDrag = ""; previewPosition = null; }
            moved = false;
            return true;
        }
        if (appearanceHeld) {
            boolean open = atAppearance(x, y);
            cancelPointer();
            if (open) {
                editing = !editing;
                wireFrom = ""; menu.clear(); initTools();
            }
            return true;
        }
        if (closeHeld) { boolean close = atClose(x, y); cancelPointer(); if (close) onClose(); return true; }
        if (pressedArrow != 0) {
            int direction = pressedArrow;
            boolean inside = atArrow(x, y, direction);
            cancelPointer();
            if (inside) tabs.by(direction * tabWidth() * 2);
            return true;
        }
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        if (draggingVerticalScrollbar) { draggingVerticalScrollbar = false; return true; }
        boolean dragged = moved || Math.hypot(x - dragX, y - dragY) > 4;
        if (draggingTabs) {
            int selected = !dragged && tabAt(x, y) == pressedTab ? pressedTab : -1;
            draggingTabs = false; pressedTab = -1;
            tabs.release(dragged);
            selectChapter(selected);
            return true;
        }
        if (!dragging) return false;
        dragging = false;
        timelineX.release(dragged); timelineY.release(dragged);
        String clicked = questAt(x, y);
        boolean open = !dragged && !clicked.isBlank() && clicked.equals(pressedQuest);
        pressedQuest = "";
        if (open) {
            selectedQuest = clicked;
            minecraft.setScreen(new JourneyQuestPopupScreen(this, clicked));
        }
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (dragging || draggingTabs || draggingTimelineProgress || draggingScrollbar
                || draggingVerticalScrollbar) return true;
        double amount = -(horizontal != 0 ? horizontal : vertical);
        if (inTabs(x, y)) { tabs.by(amount * 64); return true; }
        if (!inContent(x, y)) return false;
        double wheel = horizontal != 0 ? horizontal : vertical;
        setTimelineZoom(timelineZoom * Math.pow(1.12, wheel), x, y);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && (!menu.isEmpty() || !wireFrom.isBlank() || editing)) {
            if (!menu.isEmpty()) menu.clear();
            else if (!wireFrom.isBlank()) wireFrom = "";
            else { editing = false; initTools(); }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            if (hasControlDown()) selectChapter(chapterIndex + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1));
            else timelineX.by(key == GLFW.GLFW_KEY_RIGHT ? 72 : -72);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            timelineY.by(key == GLFW.GLFW_KEY_DOWN ? 52 : -52);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { cancelPointer(); closeTo(parent); }
    @Override public void mouseMoved(double x, double y) { pointerX = x; pointerY = y; super.mouseMoved(x, y); }
    @Override public void removed() {
        if (navigationChapter >= 0) {
            timelineX.jump(timelineX.target());
            navigationChapter = -1;
            finishChapterEvent();
        }
        cancelPointer();
        ClientMediaManager.release(mediaOwner);
        backdropFrame.close(); backdropSnapshot.close(); backdropIncoming.close();
        backdropFrameReady = false;
        frameTime = 0;
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float delta) {}
}
