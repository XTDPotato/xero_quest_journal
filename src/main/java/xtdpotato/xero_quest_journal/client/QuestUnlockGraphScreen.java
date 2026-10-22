package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestUnlockLayout;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Visual editor for the task prerequisite DAG; links are saved into task definitions. */
public final class QuestUnlockGraphScreen extends AnimatedQuestScreen {
    private static final int CARD_W = 190, CARD_H = 92;
    private record Link(String from, String to) {}
    private record Draft(Map<String, QuestUnlockLayout.Position> positions, Set<Link> links, Map<String, String> modes) {}

    private final Screen parent;
    private final List<QuestDefinition> tasks = ClientQuestDefinitions.all();
    private final Map<String, QuestDefinition> byId = new LinkedHashMap<>();
    private final Map<String, QuestUnlockLayout.Position> positions = new LinkedHashMap<>();
    private final Map<String, QuestUnlockLayout.Position> defaultPositions;
    private final Set<Link> links = new LinkedHashSet<>();
    private ViewportIndex<QuestDefinition> nodeIndex = new ViewportIndex<>(List.of());
    private ViewportIndex<Link> linkIndex = new ViewportIndex<>(List.of());
    private Map<String, List<Link>> incidentLinks = Map.of();
    private List<QuestDefinition> drawerTaskCache = List.of();
    private final AsyncQuestSearch searchWorker = new AsyncQuestSearch();
    private List<AsyncQuestSearch.Document> searchDocuments;
    private String submittedSearch;
    private long searchChangedAt;
    private boolean searchPending;
    private String searchWorkerThread = "";
    private final List<xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group> drawerGroups = ClientQuestDefinitions.groups();
    private final Map<String, xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group> drawerGroupIndex = new HashMap<>();
    private final Map<String, String> taskMembership = new HashMap<>();
    private JournalListModel drawerModel;
    private List<String> drawerCollapsedSource;
    private final Map<String, JournalListButton> drawerButtons = new HashMap<>();
    private final Map<String, String> expectedHashes = new HashMap<>();
    private final Deque<Draft> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final long expectedRevision = ClientQuestDefinitions.revision();
    private final Draft initial;
    private CompletableFuture<QuestAuthoring.Result> pending;
    private String error = "", searchText = "", selected = "", wireFrom = "";
    private final String focusTask;
    private boolean focusApplied;
    private boolean viewInitialized;
    private long focusUntilNanos;
    private Link selectedLink;
    private EditBox search;
    private double zoom = 1, panX, panY, dragX, dragY, originX, originY;
    private String draggingNode = "";
    private String hoveredNode = "";
    private String contextTask = "", propertiesTask = "";
    private QuestScreen propertyPreview;
    private String propertyPreviewId = "", lastReleasedNode = "";
    private long lastNodeRelease;
    private final SmoothScroll propertyScroll = new SmoothScroll();
    private int contextX, contextY;
    private int pressedContextAction = -1, pressedPropertiesCopy = -1, pressedExitAction = -1;
    private boolean movingCanvas, dragRemembered, confirmExit, wireDragged;
    private boolean taskDrawer, multiSelect;
    private double drawerProgress;
    private final UiMotion drawerMotion = new UiMotion(false, System.nanoTime());
    private final SmoothScroll drawerScroll = new SmoothScroll();
    private ScrollRail drawerRail;
    private EditorIconButton drawerButton, multipleButton, undoButton, redoButton;
    private CompletableFuture<String> lockPending;
    private String lockResult = "";
    private final Set<String> selectedTasks = new LinkedHashSet<>();
    private EditBox coordinateX, coordinateY;
    private final Map<String, String> modes = new LinkedHashMap<>(), groupLabels = new HashMap<>(),
            chapterLabels = new HashMap<>(), taskTitles = new HashMap<>();
    private Set<String> matchingTasks = Set.of();
    private boolean minimapDragging;
    private boolean minimapOverview;
    private EditorIconButton minimapOverviewButton;
    private record MapProjection(double scale, double originX, double originY) {
        double x(double world) { return originX + world * scale; }
        double y(double world) { return originY + world * scale; }
    }
    private MapProjection minimapDragProjection;
    private MapProjection minimapNavigationProjection;
    private int navigationWidth, navigationHeight;
    private int navigationCanvasLeft;
    private double navigationZoom, minimapPointerX, minimapPointerY;
    private long minimapFrame;
    private boolean minimapTooltipVisible;
    private double minWorldX, minWorldY, maxWorldX, maxWorldY;
    private enum TaskState {
        AVAILABLE(0xFF7DD9A4, 0xFF62C48C, 1), COMPLETE(0xFFF2C75C, 0xFFE0AF42, 2), LOCKED(0xFFF27F6E, 0xFFDE6553, 4);
        final int color, bottom, mask;
        TaskState(int color, int bottom, int mask) { this.color = color; this.bottom = bottom; this.mask = mask; }
    }
    private record MinimapDot(String id, int x, int y, TaskState state) {}
    private List<MinimapDot> minimapDots = List.of();
    private long minimapRevision = Long.MIN_VALUE;
    private final int[] minimapCounts = new int[3];
    private MapProjection minimapRasterProjection;
    private int minimapRasterWidth, minimapRasterHeight;
    private int[] minimapRasterMasks = new int[0];
    private final int[] minimapOffscreen = new int[4];
    private final int[] minimapOffscreenStates = new int[4];
    private List<int[]> minimapRuns = List.of();

    public QuestUnlockGraphScreen(Screen parent) {
        this(parent, "");
    }

    /** Opens the graph and optionally focuses a task requested by a task context menu. */
    public QuestUnlockGraphScreen(Screen parent, String focusTask) {
        super(text("title"));
        this.parent = parent;
        drawerGroups.forEach(group -> drawerGroupIndex.put(group.id(), group));
        for (var chapter : ClientQuestDefinitions.chapters()) for (String id : chapter.quests()) chapterLabels.putIfAbsent(id, chapter.name());
        tasks.forEach(task -> {
            byId.put(task.id(), task);
            modes.put(task.id(), task.completionPolicy().prerequisiteMode());
            taskTitles.put(task.id(), QuestText.title(task).getString());
            String group = ClientQuestDefinitions.groupOf(task.id()); taskMembership.put(task.id(), group);
            groupLabels.put(task.id(), drawerGroupIndex.containsKey(group) ? drawerGroupIndex.get(group).name() : group.isBlank() ? "-" : group);
            chapterLabels.putIfAbsent(task.id(), "-");
        });
        var documents = new ArrayList<AsyncQuestSearch.Document>();
        tasks.forEach(task -> documents.add(new AsyncQuestSearch.Document("task:" + task.id(), List.of(task.id(), taskTitles.get(task.id()),
                task.description(), groupLabels.get(task.id()), chapterLabels.get(task.id())))));
        drawerGroups.forEach(group -> documents.add(new AsyncQuestSearch.Document("group:" + group.id(), List.of(group.id(), group.name(), group.description()))));
        searchDocuments = List.copyOf(documents); drawerTaskCache = tasks;
        this.focusTask = byId.containsKey(focusTask) ? focusTask : "";
        defaultPositions = QuestUnlockGraphLayout.initial(tasks);
        positions.putAll(defaultPositions);
        for (QuestDefinition task : tasks) {
            task.parents().forEach(parentId -> links.add(new Link(parentId, task.id())));
        }
        positions.putAll(ClientQuestDefinitions.unlockLayout().positions());
        positions.keySet().retainAll(byId.keySet());
        rebuildViewportIndexes();
        initial = snapshot();
        for (QuestDefinition task : tasks) {
            try { expectedHashes.put(task.id(), QuestRepository.fingerprint(QuestRepository.source(task.id()))); }
            catch (Exception ignored) { expectedHashes.put(task.id(), ""); }
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.unlock_graph." + key, args);
    }
    private Draft snapshot() { return new Draft(Map.copyOf(positions), Set.copyOf(links), Map.copyOf(modes)); }
    private void remember() {
        undo.push(snapshot());
        if (undo.size() > 64) undo.removeLast();
        redo.clear();
    }
    private void restore(Deque<Draft> source, Deque<Draft> destination) {
        if (source.isEmpty()) return;
        destination.push(snapshot());
        Draft state = source.pop();
        positions.clear(); positions.putAll(state.positions());
        links.clear(); links.addAll(state.links());
        modes.clear(); modes.putAll(state.modes());
        rebuildViewportIndexes();
        selectedLink = null; wireFrom = "";
        init();
    }
    private boolean dirty() { return !snapshot().equals(initial); }
    private int top() { return 70; }
    private int bottom() { return height - 48; }
    private int drawerWidth() { return Math.min(224, Math.max(100, width / 3)); }
    private int drawerExtent() { return (int) Math.round(drawerWidth() * drawerProgress); }
    private int canvasLeft() { return Math.max(12, drawerExtent() + 12); }
    private double sx(double x) { return drawerExtent() + panX + x * zoom; }
    private double sy(double y) { return panY + y * zoom; }
    private double wx(double x) { return (x - drawerExtent() - panX) / zoom; }
    private double wy(double y) { return (y - panY) / zoom; }

    private void fit() {
        minimapNavigationProjection = null;
        if (positions.isEmpty()) { zoom = 1; panX = width / 2.; panY = height / 2.; return; }
        int minX = positions.values().stream().mapToInt(QuestUnlockLayout.Position::x).min().orElse(0);
        int minY = positions.values().stream().mapToInt(QuestUnlockLayout.Position::y).min().orElse(0);
        int maxX = positions.values().stream().mapToInt(p -> p.x() + CARD_W).max().orElse(CARD_W);
        int maxY = positions.values().stream().mapToInt(p -> p.y() + CARD_H).max().orElse(CARD_H);
        double availableWidth = width - drawerExtent();
        double needed = Math.min((availableWidth - 56.) / Math.max(1, maxX - minX),
                (bottom() - top() - 24.) / Math.max(1, maxY - minY));
        zoom = Math.clamp(needed, .45, 1.6);
        panX = needed < .45 ? availableWidth - 24 - maxX * zoom : availableWidth / 2. - (minX + maxX) * zoom / 2.;
        panY = needed < .45 ? top() + 18 - minY * zoom
                : (top() + bottom()) / 2. - (minY + maxY) * zoom / 2.;
    }

    private void applyFocusTarget() {
        if (focusApplied || focusTask.isBlank()) return;
        var position = positions.get(focusTask);
        if (position == null) {
            focusApplied = true;
            return;
        }
        selected = focusTask;
        selectedTasks.clear();
        selectedTasks.add(focusTask);
        panX = width / 2. - drawerExtent() - (position.x() + CARD_W / 2.) * zoom;
        panY = (top() + bottom()) / 2. - (position.y() + CARD_H / 2.) * zoom;
        focusUntilNanos = System.nanoTime() + 3_000_000_000L;
        focusApplied = true;
    }

    @Override protected void init() {
        clearWidgets();
        if (!viewInitialized) {
            if (panX == 0 && panY == 0) fit();
            viewInitialized = true;
        }
        applyFocusTarget();
        addRenderableWidget(new EditorIconButton(12, 8, text("fit"), this::fit, styles,
                "unlock-fit", "fit"));
        undoButton = addRenderableWidget(new EditorIconButton(42, 8, text("undo"),
                () -> restore(undo, redo), styles, "unlock-undo", "undo"));
        undoButton.active = !undo.isEmpty();
        redoButton = addRenderableWidget(new EditorIconButton(72, 8, text("redo"),
                () -> restore(redo, undo), styles, "unlock-redo", "redo"));
        redoButton.active = !redo.isEmpty();
        search = new EditBox(font, 106, 10, Math.max(40, width - 190), 20, text("search"));
        search.setHint(text("search"));
        search.setValue(searchText);
        search.setResponder(this::searchChanged);
        addRenderableWidget(search);
        drawerButton = addRenderableWidget(new EditorIconButton(12, 42, text("task_list"),
                this::toggleDrawer, styles, "unlock-task-list", "overview"));
        drawerButton.setSelectionHighlighted(taskDrawer);
        multipleButton = addRenderableWidget(new EditorIconButton(42, 42, text("multi_select"),
                this::toggleMultiple, styles, "unlock-multi-select", "select_tasks"));
        multipleButton.setSelectionHighlighted(multiSelect);
        var arrange = new EditorIconButton(72, 42, text("arrange_groups"), () -> {
            remember(); positions.clear(); positions.putAll(defaultPositions); rebuildViewportIndexes(); fit(); init();
        }, styles, "unlock-arrange", "layers");
        addRenderableWidget(arrange);
        if (!selected.isBlank()) {
            addRenderableWidget(new StyledChoiceButton<>(106, 42, Math.min(230, Math.max(100, width - 180)), 22,
                    text("prerequisite_mode"), List.of("all", "any"), modes.getOrDefault(selected, "all"),
                    value -> text("prerequisite_" + value), value -> {
                        remember();
                        if (multiSelect && !selectedTasks.isEmpty()) selectedTasks.forEach(id -> modes.put(id, value));
                        else modes.put(selected, value);
                    }, styles, "unlock-prerequisite-mode"));
        }
        drawerRail = addRenderableWidget(new ScrollRail(drawerExtent() - 10, top(),
                Math.max(8, bottom() - top()), drawerScroll));
        drawerRail.visible = drawerMotion.visible() && drawerMotion.settled();
        minimapOverviewButton = addRenderableWidget(new EditorIconButton(mapX() + mapWidth() - 24, mapY() - 26,
                text("minimap_overview"), () -> {
                    minimapOverview = !minimapOverview;
                    minimapNavigationProjection = null;
                    minimapOverviewButton.setSelectionHighlighted(minimapOverview);
                }, styles, "unlock-minimap-overview", "fit").dark());
        minimapOverviewButton.setSelectionHighlighted(minimapOverview);
        coordinateX = coordinateY = null;
        if (!selected.isBlank()) {
            var position = positions.get(selected);
            if (position != null) {
                coordinateX = new EditBox(font, 30, height - 28, 66, 20, text("x"));
                coordinateY = new EditBox(font, 118, height - 28, 66, 20, text("y"));
                coordinateX.setMaxLength(7);
                coordinateY.setMaxLength(7);
                coordinateX.setValue(Integer.toString(position.x()));
                coordinateY.setValue(Integer.toString(position.y()));
                coordinateX.setResponder(value -> updateCoordinate(value, true));
                coordinateY.setResponder(value -> updateCoordinate(value, false));
                addRenderableWidget(coordinateX);
                addRenderableWidget(coordinateY);
            }
        }
        var save = addRenderableWidget(new EditorIconButton(width - 72, 8, text("save"),
                this::save, styles, "unlock-save", "save"));
        save.active = pending == null && minecraft.getSingleplayerServer() != null;
        addRenderableWidget(new EditorIconButton(width - 42, 8, text("close"),
                this::onClose, styles, "unlock-close", "close"));
        if (confirmExit) {
            addRenderableWidget(new StyledButton(width / 2 - 106, height / 2 + 22, 100, 22,
                    text("discard"), ignored -> closeTo(parent), styles, "unlock-discard", "toolbar-button"));
            addRenderableWidget(new StyledButton(width / 2 + 6, height / 2 + 22, 100, 22,
                    text("continue"), ignored -> { confirmExit = false; init(); }, styles,
                    "unlock-continue", "toolbar-button"));
        }
    }

    @Override public void tick() {
        advanceSearch();
        boolean editable = pending == null && lockPending == null && ClientEditMode.enabled();
        children().forEach(child -> {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                widget.active = editable;
        });
        undoButton.active = editable && !undo.isEmpty();
        redoButton.active = editable && !redo.isEmpty();
        if (lockPending != null && lockPending.isDone()) {
            try {
                String result = lockPending.join();
                if (result.equals("locked") || result.equals("unlocked")) {
                    lockResult = text(result.equals("locked") ? "tasks_locked" : "tasks_unlocked").getString();
                    error = "";
                } else error = Component.translatable("screen.xero_quest_journal.action." + result).getString();
            } catch (RuntimeException failure) {
                error = text("save_failed").getString();
            }
            lockPending = null;
        }
        if (pending == null || !pending.isDone()) return;
        QuestAuthoring.Result result;
        try { result = pending.join(); }
        catch (Exception failure) {
            pending = null;
            error = text("save_failed").getString();
            init();
            return;
        }
        pending = null;
        if (result.successful()) closeTo(parent);
        else { error = Component.translatable(result.error()).getString(); init(); }
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        // The editor draws its own backdrop before the cards and widgets.
    }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        advanceSearch();
        if (minimapDragging) advanceMinimapDrag();
        int previousDrawerExtent = drawerExtent();
        drawerMotion.advance(System.nanoTime(), popupAnimationMillis());
        drawerProgress = drawerMotion.eased();
        int drawerDelta = drawerExtent() - previousDrawerExtent;
        panX -= drawerDelta;
        if (movingCanvas) originX -= drawerDelta;
        var drawerList = drawerRows();
        drawerScroll.limit(drawerList.contentHeight() - Math.max(1, bottom() - top()));
        drawerScroll.advance();
        drawerRail.setX(drawerExtent() - 10);
        minimapOverviewButton.setX(mapX() + mapWidth() - 24);
        minimapOverviewButton.setY(mapY() - 26);
        drawerRail.visible = drawerMotion.visible() && drawerMotion.settled()
                && drawerScroll.maximum() > 0;
        backdrop(g, 0xF01E1E22);
        g.fill(0, 0, width, 38, 0xFF19191D);
        g.fill(0, 38, width, 39, TriggerNodeStyle.PANEL_BORDER);
        g.fill(0, height - 44, width, height, 0xFF19191D);
        g.fill(0, height - 45, width, height - 44, TriggerNodeStyle.PANEL_BORDER);
        if (selected.isBlank()) g.drawString(font, title, 106, 47, 0xFFF1D8A3, false);
        if (!selected.isBlank()) g.drawString(font, font.plainSubstrByWidth(text("selection_info",
                taskTitles.getOrDefault(selected, selected), groupLabels.getOrDefault(selected,"-"), chapterLabels.getOrDefault(selected,"-")).getString(), width-24),
                12, height-39, 0xFFE0E7E3, false);
        if (coordinateX != null) {
            g.drawString(font, text("x"), 12, height - 22, 0xFFE0E7E3);
            g.drawString(font, text("y"), 104, height - 22, 0xFFE0E7E3);
        }
        String footer = !error.isBlank() ? error : !lockResult.isBlank() ? lockResult
                : multiSelect ? text("selected_count", selectedTasks.size()).getString() : searchPending ? text("searching").getString() : "";
        g.drawString(font, font.plainSubstrByWidth(footer, Math.max(1, width - 204)),
                196, height - 22, !error.isBlank() ? 0xFFFF9797 : 0xFFE0E7E3, false);
        g.enableScissor(canvasLeft(), top(), width - 12, bottom());
        g.drawManaged(() -> renderGrid(g));
        Set<Link> movingLinks = draggingNode.isBlank() ? Set.of()
                : new HashSet<>(incidentLinks.getOrDefault(draggingNode, List.of()));
        g.drawManaged(() -> {
            for (Link link : visibleLinks()) {
                if (!movingLinks.contains(link)) renderLink(g, link, selectedLink != null && selectedLink.equals(link));
            }
            for (Link link : movingLinks)
                renderLink(g, link, selectedLink != null && selectedLink.equals(link));
        });
        if (!wireFrom.isBlank()) {
            var source = positions.get(wireFrom);
            if (source != null) {
                double x = sx(source.x()), y = sy(source.y() + CARD_H / 2.0);
                TriggerNodeStyle.wire(g, x, y, mx, my, 0xFFE9C576, 2, true, canvasLeft(), top(), width - 12, bottom());
            }
        }
        hoveredNode = nodeAt(mx, my);
        for (QuestDefinition task : visibleTasks()) renderTask(g, task, mx, my);
        g.disableScissor();
        renderMinimap(g);
        renderTaskDrawer(g, mx, my);
        var exitButtons = children().stream().filter(child -> child instanceof StyledButton button
                && (button.styleId().equals("unlock-discard") || button.styleId().equals("unlock-continue")))
                .map(child -> (StyledButton) child).toList();
        exitButtons.forEach(button -> button.visible = false);
        try {
            super.renderContents(g, minimapDragging ? -10000 : mx, minimapDragging ? -10000 : my, delta);
        } finally {
            exitButtons.forEach(button -> button.visible = true);
        }
        renderAnimatedMenu(g, "confirm", confirmExit ? "confirm" : null,
                viewport(width / 2 - 122, height / 2 - 44, width / 2 + 122, height / 2 + 56),
                0xB8000000, () -> {
            int x = width / 2 - 122, y = height / 2 - 44;
            g.fill(x, y, x + 244, y + 100, 0xFF29323C);
            g.renderOutline(x, y, 244, 100, 0xFF9CB0BD);
            g.drawCenteredString(font, text("unsaved"), width / 2, y + 16, 0xFFE8EDEB);
            exitButtons.forEach(button -> button.render(g, mx, my, delta));
        });
        renderAnimatedMenu(g, "properties", !confirmExit && !propertiesTask.isBlank() ? propertiesTask : null,
                viewport(propertyX(), propertyY(), propertyX() + propertyWidth(), propertyY() + propertyHeight()),
                0xB8000000, () -> renderProperties(g));
        renderAnimatedMenu(g, "context", contextTask.isBlank() ? null : contextTask,
                viewport(contextX, contextY, contextX + contextWidth(),
                        contextY + contextItems().length * 22), () -> {
            // Same menu treatment as the trigger editor: dark rounded panel, gold bullets, hover rows.
            String[] items = contextItems();
            int w = contextWidth(), h = items.length * 22;
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 1000);
            g.fill(contextX + 2, contextY + 3, contextX + w + 2, contextY + h + 3, 0x50000000);
            ControlAppearance.draw(g, contextX, contextY, w, h, "rounded", 5, TriggerNodeStyle.PANEL_RAISED,
                    TriggerNodeStyle.PANEL_BORDER, 1);
            for (int i = 0; i < items.length; i++) {
                int rowY = contextY + i * 22;
                boolean destructive = items[i].equals("disconnect");
                boolean enabled = !destructive || hasLinksForContext();
                boolean hovered = enabled && mx >= contextX && mx < contextX + w && my >= rowY && my < rowY + 22;
                if (hovered) ControlAppearance.draw(g, contextX + 3, rowY + 2, w - 6, 18, "rounded", 4,
                        destructive ? 0x40FF8A73 : 0x26FFFFFF, 0, 0);
                if (i == 2 || i == 4 || i == 6) g.fill(contextX + 8, rowY, contextX + w - 8, rowY + 1, 0x30FFFFFF);
                MailDecor.diamond(g, contextX + 10, rowY + 11, 2, !enabled ? 0xFF5A5A62
                        : destructive ? 0xFFFF9B86 : TriggerNodeStyle.GOLD);
                g.drawString(font, font.plainSubstrByWidth(text(items[i]).getString(), w - 26),
                        contextX + 18, rowY + 7, !enabled ? 0xFF6E6E76 : destructive ? 0xFFFFC7B5 : 0xFFE8E8EC, false);
            }
            g.flush();
            g.pose().popPose();
        });
        minimapTooltipVisible = false;
        if (!minimapDragging && !movingCanvas && draggingNode.isBlank() && !confirmExit && contextTask.isBlank() && propertiesTask.isBlank()
                && mx >= mapX() && mx < mapX() + mapWidth() && my >= mapY() && my < mapY() + mapHeight()) {
            minimapTooltipVisible = true;
            g.renderComponentTooltip(font, List.of(text("minimap_tasks", minimapDots.size()),
                    text("available").copy().append(": " + minimapCounts[0]).withStyle(net.minecraft.ChatFormatting.GREEN),
                    text("complete").copy().append(": " + minimapCounts[1]).withStyle(net.minecraft.ChatFormatting.GOLD),
                    text("locked").copy().append(": " + minimapCounts[2]).withStyle(net.minecraft.ChatFormatting.RED),
                    text("minimap_outside", minimapOffscreen[0], minimapOffscreen[1], minimapOffscreen[2], minimapOffscreen[3])), mx, my);
        }
    }
    private void renderTaskDrawer(GuiGraphics g, int mx, int my) {
        if (drawerExtent() <= 0) return;
        int w = drawerWidth(), x = drawerExtent() - w;
        g.enableScissor(0, top(), drawerExtent(), bottom());
        g.fill(x, top(), x + w, bottom(), 0xFF2A323B);
        g.fill(x + w - 2, top(), x + w, bottom(), 0xFF9CB0BD);
        var rows = drawerRows().visibleRows(drawerScroll.position()-56,drawerScroll.position()+bottom()-top()+56);
        var visible = new HashSet<String>();
        for (var row : rows) {
            int y = top()+row.offset()-drawerScroll.pixels();
            if (row.section()) {
                var type=ClientQuestDefinitions.types().stream().filter(t->t.id().equals(row.category())).findFirst().orElse(null);
                if(type!=null) { QuestIcons.render(g,type.icon(),x+10,y+6,12,12,1);g.drawString(font,type.name(),x+28,y+8,0xFFE0D5B5,false); }
                continue;
            }
            visible.add(row.key());
            var button=drawerButtons.get(row.key());
            boolean group=row.group()!=null;
            if(button==null) {
                var task=row.quest(); var heading=row.group();
                button=new JournalListButton(x+6+row.indent(),y,w-18-row.indent(),row.height(),
                        Component.literal(group?heading.name():taskTitles.get(task.id())),ignored->{},styles,"unlock-drawer-"+row.key(),
                        group?heading.leadingIcon():task.leadingIcon(),group?heading.trailingIcon():task.trailingIcon(),
                        group?heading.description():"",group,group&&!ClientPreferences.get().collapsedJournalGroups.contains(heading.id()),
                        false,false,"",null);
                if(!group) button.extraCss(task.buttonCss());
                drawerButtons.put(row.key(),button);
            }
            button.setPosition(x+6+row.indent(),y); button.setWidth(w-18-row.indent());
            button.setSelectionHighlighted(!group && (selectedTasks.contains(row.quest().id()) || row.quest().id().equals(selected)));
            if(!group) button.locked(!isUnlocked(row.quest()));
            button.clipTo(0,top(),drawerExtent()-8,bottom());button.render(g,mx,my,0);
        }
        drawerButtons.keySet().retainAll(visible);
        g.disableScissor();
    }
    private boolean isUnlocked(QuestDefinition task) {
        var progress = ClientQuestProgress.view(task.id());
        return progress == null ? task.defaultUnlocked() : progress.unlocked();
    }
    private TaskState taskState(QuestDefinition task) {
        var progress = ClientQuestProgress.view(task.id());
        return progress != null && progress.completed() ? TaskState.COMPLETE
                : isUnlocked(task) ? TaskState.AVAILABLE : TaskState.LOCKED;
    }
    private static void drawLockStatus(GuiGraphics g, boolean unlocked, int x, int y, int size) {
        drawLockStatus(g, unlocked, x, y, size, 0);
    }
    /** {@code tint} 0 keeps the status colours (drawer rows); otherwise one colour for bright bands. */
    private static void drawLockStatus(GuiGraphics g, boolean unlocked, int x, int y, int size, int tint) {
        if (!unlocked) {
            JournalNavButton.drawIcon(g, "lock", x, y, size, tint != 0 ? tint : 0xFFFF9A9A);
            return;
        }
        // Open shackle, using the same compact padlock silhouette as the locked icon.
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16F, size / 16F, 1);
        int color = tint != 0 ? tint : 0xFF9BE0B0;
        g.fill(3, 7, 13, 15, color);
        g.fill(5, 3, 7, 8, color);
        g.fill(7, 1, 12, 3, color);
        g.fill(12, 3, 14, 5, color);
        g.fill(7, 10, 9, 13, tint != 0 ? 0xFF7DD9A4 : 0xFF315A43);
        g.pose().popPose();
    }
    private List<QuestDefinition> drawerTasks() {
        return drawerTaskCache;
    }
    private void searchChanged(String value) {
        searchText=value; searchChangedAt=System.nanoTime();submittedSearch=null;searchWorker.cancel();drawerScroll.jump(0);
        searchPending=!value.isBlank();
        if(value.isBlank()) { drawerTaskCache=tasks;matchingTasks=Set.of();drawerModel=null;drawerButtons.clear(); }
    }
    private void advanceSearch() {
        String query=AsyncQuestSearch.normalize(searchText);
        if(query.isBlank())return;
        if(!query.equals(submittedSearch) && System.nanoTime()-searchChangedAt>=120_000_000L) {
            submittedSearch=query;searchWorker.request(query,searchDocuments);
        }
        var result=searchWorker.takeResult();
        if(result==null || !result.query().equals(query))return;
        searchWorkerThread=result.workerThread();
        drawerTaskCache=tasks.stream().filter(task->result.ids().contains("task:"+task.id())
                || result.ids().contains("group:"+taskMembership.get(task.id()))).toList();
        matchingTasks=Set.copyOf(drawerTaskCache.stream().map(QuestDefinition::id).toList());
        searchPending=false;drawerModel=null;drawerButtons.clear();
    }
    private JournalListModel drawerRows() {
        var collapsed=ClientPreferences.get().collapsedJournalGroups;
        if(drawerModel==null || drawerCollapsedSource!=collapsed) {
            drawerCollapsedSource=collapsed; drawerButtons.clear();
            drawerModel=JournalListModel.categorized(drawerTasks(),drawerGroups,
                    id->taskMembership.getOrDefault(id,""),searchText.isBlank()?new HashSet<>(collapsed):Set.of(),
                    searchText.isBlank(),ClientQuestDefinitions.types().stream().map(t->t.id()).toList());
        }
        return drawerModel;
    }
    private void toggleDrawerGroup(String id) {
        var collapsed=new LinkedHashSet<>(ClientPreferences.get().collapsedJournalGroups);
        if(!collapsed.remove(id))collapsed.add(id);
        ClientPreferences.get().collapsedJournalGroups=List.copyOf(collapsed);ClientPreferences.get().save();
        drawerModel=null;drawerButtons.clear();
    }
    private void toggleDrawer() {
        taskDrawer = !taskDrawer;
        drawerMotion.target(taskDrawer, System.nanoTime(), popupAnimationMillis());
        drawerProgress = drawerMotion.eased();
        drawerButton.setSelectionHighlighted(taskDrawer);
        wireFrom = draggingNode = "";
    }
    private void toggleMultiple() {
        multiSelect = !multiSelect;
        if (!multiSelect) selectedTasks.clear();
        else if (!selected.isBlank()) selectedTasks.add(selected);
        multipleButton.setSelectionHighlighted(multiSelect);
        wireFrom = "";
        init();
    }
    private void selectTask(String id) {
        if (!byId.containsKey(id)) return;
        if (multiSelect) {
            if (!selectedTasks.add(id)) selectedTasks.remove(id);
        } else {
            selectedTasks.clear();
            selectedTasks.add(id);
        }
        selected = id;
        selectedLink = null;
        lockResult = "";
        init();
    }
    private void selectAll() {
        multiSelect = true;
        selectedTasks.clear();
        selectedTasks.addAll(byId.keySet());
        if (selected.isBlank()) selected = selectedTasks.stream().findFirst().orElse("");
        init();
    }
    private void invertSelection() {
        multiSelect = true;
        var remaining = new LinkedHashSet<>(byId.keySet());
        remaining.removeAll(selectedTasks);
        selectedTasks.clear();
        selectedTasks.addAll(remaining);
        init();
    }
    private String[] contextItems() {
        return new String[]{"properties", "copy_properties", "select_all", "invert_selection",
                "lock_selected", "unlock_selected", "disconnect", "open_task"};
    }

    private void openTask(String id) {
        if (!byId.containsKey(id)) return;
        wireFrom = draggingNode = ""; movingCanvas = minimapDragging = false;
        lastReleasedNode = "";
        minecraft.setScreen(QuestScreen.graphTask(this, id));
    }

    /** Tasks the context menu acts on: the multi-selection, otherwise the clicked task. */
    private Set<String> contextTargets() {
        Set<String> targets = new LinkedHashSet<>();
        if (multiSelect) targets.addAll(selectedTasks);
        if (byId.containsKey(contextTask) && (!multiSelect || targets.isEmpty())) targets.add(contextTask);
        return targets;
    }

    private boolean hasLinksForContext() {
        Set<String> targets = contextTargets();
        return links.stream().anyMatch(link -> targets.contains(link.from()) || targets.contains(link.to()));
    }

    /** Removes every prerequisite link touching the targeted task(s), as one undo step. */
    private void disconnectContext() {
        Set<String> targets = contextTargets();
        if (links.stream().noneMatch(link -> targets.contains(link.from()) || targets.contains(link.to()))) return;
        remember();
        links.removeIf(link -> targets.contains(link.from()) || targets.contains(link.to()));
        rebuildViewportIndexes();
        selectedLink = null;
        wireFrom = "";
        error = "";
        init();
    }
    private int contextWidth() { return Math.min(180, width - 16); }
    private void openContext(String id, double x, double y) {
        if (!multiSelect && !id.isBlank()) {
            selectedTasks.clear();
            selectedTasks.add(id);
            selected = id;
            init();
        } else if (selectedTasks.isEmpty() && !id.isBlank()) selectedTasks.add(id);
        contextTask = id.isBlank() ? "selection" : id;
        contextX = Math.clamp((int) x, 8, Math.max(8, width - contextWidth() - 8));
        contextY = Math.clamp((int) y, 8, Math.max(8, height - contextItems().length * 22 - 8));
    }
    private void setSelectedLocked(boolean locked) {
        if (pending != null || lockPending != null || selectedTasks.isEmpty()) return;
        lockResult = "";
        lockPending = ClientQuestProgress.setLockedMany(selectedTasks, locked);
    }
    private void updateCoordinate(String value, boolean xAxis) {
        try {
            int number = Integer.parseInt(value.strip());
            if (Math.abs((long) number) > 100000) throw new NumberFormatException("Out of bounds");
            var current = positions.get(selected);
            if (current == null) return;
            var next = xAxis ? new QuestUnlockLayout.Position(number, current.y())
                    : new QuestUnlockLayout.Position(current.x(), number);
            if (!next.equals(current)) {
                remember();
                positions.put(selected, next);
                rebuildViewportIndexes();
            }
            (xAxis ? coordinateX : coordinateY).setTextColor(0xFFFFFFFF);
        } catch (NumberFormatException ignored) {
            (xAxis ? coordinateX : coordinateY).setTextColor(0xFFFF7777);
        }
    }
    private String groupName(QuestDefinition task) {
        String id = ClientQuestDefinitions.groupOf(task.id());
        return ClientQuestDefinitions.groups().stream().filter(group -> group.id().equals(id))
                .map(group -> group.name()).findFirst().orElse(id.isBlank() ? "-" : id);
    }
    private String chapterName(QuestDefinition task) {
        return ClientQuestDefinitions.chapters().stream().filter(chapter -> chapter.quests().contains(task.id()))
                .map(chapter -> chapter.name()).findFirst().orElse("-");
    }
    private void rebuildViewportIndexes() {
        var nodes = new ArrayList<ViewportIndex.Entry<QuestDefinition>>(tasks.size());
        for (int index = 0; index < tasks.size(); index++) {
            QuestDefinition task = tasks.get(index);
            var position = positions.get(task.id());
            if (position == null) continue;
            nodes.add(new ViewportIndex.Entry<>(task, position.x() - 6, position.y() - 6,
                    position.x() + CARD_W + 6, position.y() + CARD_H + 6, index));
        }
        nodeIndex = new ViewportIndex<>(nodes);

        var edges = new ArrayList<ViewportIndex.Entry<Link>>(links.size());
        var adjacent = new LinkedHashMap<String, List<Link>>();
        int order = 0;
        for (Link link : links) {
            var from = positions.get(link.from());
            var to = positions.get(link.to());
            if (from != null && to != null) {
                double x1 = from.x() > to.x() ? from.x() - 4 : from.x() + CARD_W + 4;
                double x2 = from.x() > to.x() ? to.x() + CARD_W + 4 : to.x() - 4;
                double y1 = from.y() + CARD_H / 2.0, y2 = to.y() + CARD_H / 2.0;
                edges.add(new ViewportIndex.Entry<>(link, Math.min(x1, x2) - 24,
                        Math.min(y1, y2) - 3, Math.max(x1, x2) + 24, Math.max(y1, y2) + 3, order++));
            }
            adjacent.computeIfAbsent(link.from(), ignored -> new ArrayList<>()).add(link);
            adjacent.computeIfAbsent(link.to(), ignored -> new ArrayList<>()).add(link);
        }
        linkIndex = new ViewportIndex<>(edges);
        adjacent.replaceAll((id, value) -> List.copyOf(value));
        incidentLinks = Map.copyOf(adjacent);
        rebuildMinimap();
    }
    private List<QuestDefinition> visibleTasks() {
        double left = wx(canvasLeft() - 8), right = wx(width - 4);
        double top = wy(top() - 8), bottom = wy(bottom() + 8);
        var visible = new ArrayList<>(nodeIndex.query(left, top, right, bottom));
        if (!draggingNode.isBlank()) {
            visible.removeIf(task -> task.id().equals(draggingNode));
            var position = positions.get(draggingNode);
            QuestDefinition dragged = byId.get(draggingNode);
            if (position != null && dragged != null && position.x() + CARD_W >= left && position.x() <= right
                    && position.y() + CARD_H >= top && position.y() <= bottom) visible.add(dragged);
        }
        return visible;
    }
    private List<Link> visibleLinks() {
        double padding = 10 / zoom;
        return linkIndex.query(wx(canvasLeft() - padding), wy(top() - padding),
                wx(width - 12 + padding), wy(bottom() + padding));
    }
    private int propertyCanvasWidth() {
        return propertyPreview == null ? LayoutEditorState.DEFAULT_TASK_WIDTH : propertyPreview.previewCanvasWidth();
    }
    private int propertyCanvasHeight() {
        return propertyPreview == null ? LayoutEditorState.DEFAULT_TASK_HEIGHT : propertyPreview.previewCanvasHeight();
    }
    private int propertyWidth() {
        int available = Math.max(180, width - 32);
        int preferred = Math.max(360, propertyCanvasWidth() + 110);
        return Math.min(available, Math.min(preferred, Math.max(360, width * 84 / 100)));
    }
    private int propertyHeight() {
        int available = Math.max(140, height - 32);
        int preferred = Math.max(280, propertyCanvasHeight() + 130);
        return Math.min(available, Math.min(preferred, Math.max(280, height * 84 / 100)));
    }
    private int propertyX() { return (width - propertyWidth()) / 2; }
    private int propertyY() { return (height - propertyHeight()) / 2; }
    private Viewport propertyViewport() {
        return viewport(propertyX() + 10, propertyY() + 30,
                propertyX() + propertyWidth() - 14, propertyY() + propertyHeight() - 36);
    }
    private void renderProperties(GuiGraphics g) {
        QuestDefinition task = byId.get(propertiesTask);
        if (task == null) return;
        int w = propertyWidth(), h = propertyHeight(), x = propertyX(), y = propertyY();
        g.fill(x, y, x + w, y + h, 0xFF29323C); g.renderOutline(x, y, w, h, 0xFF9CB0BD);
        g.drawString(font, font.plainSubstrByWidth(text("property_id", task.id()).getString(), w - 44), x + 10, y + 10, 0xFFF0D59B, false);
        JournalNavButton.drawIcon(g, "close", x + w - 23, y + 7, 16, 0xFFE4EAEF);
        if (propertyPreview == null || !propertyPreviewId.equals(task.id())) {
            propertyPreviewId = task.id(); propertyScroll.jump(0);
            propertyPreview = QuestScreen.taskControlPreview(task);
            propertyPreview.init(minecraft, width, height);
        }
        var view = propertyViewport();
        boolean footer = propertyPreview.previewControls().stream().noneMatch(control -> control.kind().equals("quest_rewards"))
                && !task.rewards().isEmpty();
        var body = footer ? viewport(view.left(), view.top(), view.right(), Math.max(view.top() + 24, view.bottom() - 64)) : view;
        propertyScroll.limit(propertyPreview.templatePreviewMaxScroll(body)); propertyScroll.advance();
        propertyPreview.renderTemplatePreview(g, body, propertyScroll.position(), -1000, -1000, "");
        if (footer) JournalDetailRenderer.renderFooter(g, font, task, task.rewards(), view.left(), body.bottom(), view.right(), -1000, -1000, false, false);
        if (propertyScroll.maximum() > 0) {
            g.fill(x + w - 9, view.top(), x + w - 6, view.bottom(), 0xFF465460);
            int thumb = Math.max(12, (int) ((view.bottom() - view.top()) / (1 + propertyScroll.maximum() / (view.bottom() - view.top()))));
            int top = view.top() + (int) ((view.bottom() - view.top() - thumb) * propertyScroll.position() / propertyScroll.maximum());
            g.fill(x + w - 9, top, x + w - 6, top + thumb, 0xFFB7C6CF);
        }
        g.drawString(font, text("copy_properties"), x + 12, y + h - 23, 0xFFF0D59B, false);
        g.drawString(font, text("open_task"), x + w - font.width(text("open_task")) - 12, y + h - 23, 0xFFF0D59B, false);
    }
    private void copyProperties(String id) {
        QuestDefinition task = byId.get(id);
        if (task == null) return;
        var data = new com.google.gson.JsonObject();
        data.addProperty("id", task.id());
        data.addProperty("title", QuestText.title(task).getString());
        data.addProperty("type", task.type());
        data.addProperty("group", ClientQuestDefinitions.groupOf(id));
        data.addProperty("chapter", ClientQuestDefinitions.chapters().stream()
                .filter(chapter -> chapter.quests().contains(id)).map(chapter -> chapter.id()).findFirst().orElse(""));
        data.add("parents", new com.google.gson.Gson().toJsonTree(task.parents()));
        var position = positions.get(id);
        if (position != null) {
            data.addProperty("x", position.x());
            data.addProperty("y", position.y());
        }
        minecraft.keyboardHandler.setClipboard(data.toString());
        error = text("properties_copied").getString();
    }
    private void renderGrid(GuiGraphics g) {
        TriggerNodeStyle.canvas(g, canvasLeft(), top(), width - 12, bottom(),
                drawerExtent() + panX, panY, zoom * 40 / 24.0, true);
    }
    private void renderTask(GuiGraphics g, QuestDefinition task, int mx, int my) {
        var p = positions.get(task.id());
        if (p == null) return;
        int x = (int) sx(p.x()), y = (int) sy(p.y()), w = (int) (CARD_W * zoom), h = (int) (CARD_H * zoom);
        if (x + w < 12 || x > width - 12 || y + h < top() || y > bottom()) return;
        var state = taskState(task);
        boolean complete = state == TaskState.COMPLETE;
        boolean locked = state == TaskState.LOCKED;
        boolean highlighted = !searchText.isBlank() && matchingTasks.contains(task.id());
        boolean chosen = task.id().equals(selected) || selectedTasks.contains(task.id());
        boolean focusHighlighted = task.id().equals(focusTask)
                && System.nanoTime() < focusUntilNanos;
        // Bright state band (complete gold, locked coral, available green) with a dark title.
        int top = state.color;
        int bottom = state.bottom;
        int header = Math.max(font.lineHeight + 5, (int) Math.round(18 * zoom));
        // Keep scanline-shaped highlights in one GUI batch, before text and item rendering.
        g.drawManaged(() -> {
            g.fill(x + 2, y + 4, x + w + 3, y + h + 5, 0x38000000);
            g.fill(x + 1, y + 2, x + w + 2, y + h + 3, 0x30000000);
            if (chosen || highlighted) {
                ControlAppearance.draw(g, x - 3, y - 3, w + 6, h + 6, "rounded", 8, 0, 0x38FFD27A, 1);
                ControlAppearance.draw(g, x - 2, y - 2, w + 4, h + 4, "rounded", 7, 0, 0xA0FFD27A, 1);
            }
            if (focusHighlighted) {
                float pulse = ClientPreferences.get().reducedMotion ? 1F
                        : (float) ((Math.sin(System.nanoTime() / 90_000_000.0) + 1) * .5);
                int alpha = 0x70 + Math.round(pulse * 0x70);
                ControlAppearance.draw(g, x - 7, y - 7, w + 14, h + 14, "rounded", 10,
                        0, (alpha << 24) | 0x00F4D37B, 2);
                ControlAppearance.draw(g, x - 4, y - 4, w + 8, h + 8, "rounded", 8,
                        0, (0xB0 << 24) | 0x00FFF1B0, 1);
            }
            int border = focusHighlighted ? 0xFFFFE08A
                    : chosen || highlighted ? TriggerNodeStyle.GOLD : hoveredNode.equals(task.id()) ? 0xFF5C5C66
                    : TriggerNodeStyle.BODY_BORDER;
            g.fill(x, y, x + w, y + h, TriggerNodeStyle.BODY);
            g.renderOutline(x, y, w, h, border);
            g.fillGradient(x + 1, y + 2, x + w - 1, y + header, top, bottom);
            g.fill(x + 4, y + 1, x + w - 4, y + 2, top);
            g.fill(x + 5, y + 1, x + w - 5, y + 2, 0x50FFFFFF);
            g.fill(x + 1, y + header, x + w - 1, y + header + 1, 0x60000000);
        });
        if (zoom < .65) {
            int clippedWidth = Math.max(1, w - 22);
            g.drawManaged(() -> {
                g.drawString(font, font.plainSubstrByWidth(taskTitles.get(task.id()), clippedWidth),
                        x + 5, y + 3, 0xFF17171B, false);
                if (h > 26) g.drawString(font, font.plainSubstrByWidth(groupLabels.get(task.id()), clippedWidth),
                        x + 5, y + 17, 0xFFB5C8CF, false);
                drawLockStatus(g, !locked, x + w - 14, y + 2, 10, 0xFF17171B);
            });
            renderTaskPorts(g, task, x, y, w, h, mx, my);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) zoom, (float) zoom, 1);
        QuestIcons.render(g, task.icon().isBlank() ? "minecraft:book" : task.icon(), 8, 24, 22);
        g.drawManaged(() -> drawLockStatus(g, !locked, CARD_W - 20, 2, 13, 0xFF17171B));
        g.drawManaged(() -> {
            String title = taskTitles.get(task.id());
            g.drawString(font, font.plainSubstrByWidth(title, CARD_W - 34), 8, 5, 0xFF17171B, false);
            g.drawString(font, font.plainSubstrByWidth(task.id(), CARD_W - 44), 36, 25,
                    0xFFAFC2C5, false);
            g.drawString(font, font.plainSubstrByWidth(text("property_group", groupLabels.get(task.id())).getString(), CARD_W - 44),
                    36, 37, 0xFFB5C8CF, false);
            g.drawString(font, font.plainSubstrByWidth(text("property_chapter", chapterLabels.get(task.id())).getString(), CARD_W - 16),
                    8, 54, 0xFFB5C8CF, false);
            int statusColor = complete ? 0xFFF2CF83 : locked ? 0xFFFF9A9A : 0xFF9BE0B0;
            MailDecor.diamond(g, 11, 76, 2, statusColor);
            g.drawString(font, text(complete ? "complete" : locked ? "locked" : "available"),
                    17, 72, statusColor, false);
        });
        g.pose().popPose();
        renderTaskPorts(g, task, x, y, w, h, mx, my);
    }
    private void renderTaskPorts(GuiGraphics g, QuestDefinition task, int x, int y, int w, int h, int mx, int my) {
        g.drawManaged(() -> {
            TriggerNodeStyle.port(g, x, y + h / 2.0, TriggerNodeStyle.PORT_NEXT, hasOutgoing(task.id()),
                    Math.abs(mx - x) < 8 && Math.abs(my - y - h / 2.0) < 8);
            TriggerNodeStyle.port(g, x + w, y + h / 2.0, TriggerNodeStyle.PORT_INPUT, hasIncoming(task.id()), false);
        });
    }
    private boolean hasOutgoing(String id) {
        return incidentLinks.getOrDefault(id, List.of()).stream().anyMatch(link -> link.from().equals(id));
    }
    private boolean hasIncoming(String id) {
        return incidentLinks.getOrDefault(id, List.of()).stream().anyMatch(link -> link.to().equals(id));
    }
    private void renderLink(GuiGraphics g, Link link, boolean selectedEdge) {
        var from = positions.get(link.from());
        var to = positions.get(link.to());
        if (from == null || to == null) return;
        boolean reversed = from.x() > to.x();
        double x1 = sx(reversed ? from.x() : from.x() + CARD_W) + (reversed ? -4 : 4), y1 = sy(from.y() + CARD_H / 2.0);
        double x2 = sx(reversed ? to.x() + CARD_W : to.x()) + (reversed ? 4 : -4), y2 = sy(to.y() + CARD_H / 2.0);
        int color = selectedEdge ? TriggerNodeStyle.GOLD : completed(link.from()) ? TriggerNodeStyle.PORT_TRUE : 0xFFD9D9DE;
        // Three orthogonal spans replace dozens of transformed curve segments per edge.
        double middle = (x1 + x2) / 2;
        clippedLine(g, x1, y1, middle, y1, color, selectedEdge ? 3 : 1);
        clippedLine(g, middle, y1, middle, y2, color, selectedEdge ? 3 : 1);
        clippedLine(g, middle, y2, x2, y2, color, selectedEdge ? 3 : 1);
        if (!ClientPreferences.get().reducedMotion && selectedEdge) {
            double t = (System.nanoTime() / 1_400_000_000.) % 1;
            double px = t < .33 ? x1 + (middle - x1) * t * 3 : t < .66 ? middle : middle + (x2 - middle) * (t - .66) * 3;
            double py = t < .33 ? y1 : t < .66 ? y1 + (y2 - y1) * (t - .33) * 3 : y2;
            MailDecor.diamond(g, (int) px, (int) py, 3, TriggerNodeStyle.GOLD);
        }
    }
    private void clippedLine(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int thickness) {
        double[] line = ViewportIndex.clipSegment(x1, y1, x2, y2, canvasLeft(), top(), width - 12, bottom());
        if (line == null) return;
        g.fill((int) Math.floor(Math.min(line[0], line[2])), (int) Math.floor(Math.min(line[1], line[3])),
                (int) Math.ceil(Math.max(line[0], line[2])) + thickness,
                (int) Math.ceil(Math.max(line[1], line[3])) + thickness, color);
    }
    private boolean completed(String id) {
        var progress = ClientQuestProgress.view(id);
        return progress != null && progress.completed();
    }
    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) g.vLine(x1, Math.min(y1, y2), Math.max(y1, y2), color);
        else if (y1 == y2) g.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
    }
    private String nodeAt(double x, double y) {
        if (x < canvasLeft() || x >= width - 12 || y < top() || y >= bottom()) return "";
        double px = wx(x), py = wy(y);
        if (!draggingNode.isBlank()) {
            var dragged = positions.get(draggingNode);
            if (dragged != null && px >= dragged.x() && px < dragged.x() + CARD_W
                    && py >= dragged.y() && py < dragged.y() + CARD_H) return draggingNode;
        }
        var candidates = nodeIndex.query(px, py, px, py);
        for (int index = candidates.size() - 1; index >= 0; index--) {
            var task = candidates.get(index);
            var position = positions.get(task.id());
            if (position != null && px >= position.x() && px < position.x() + CARD_W
                    && py >= position.y() && py < position.y() + CARD_H) return task.id();
        }
        return "";
    }
    private Link linkAt(double x, double y) {
        for (Link link : linkIndex.query(wx(x - 6), wy(y - 6), wx(x + 6), wy(y + 6))) {
            var a = positions.get(link.from());
            var b = positions.get(link.to());
            if (a == null || b == null) continue;
            boolean reversed = a.x() > b.x();
            double x1 = sx(reversed ? a.x() : a.x() + CARD_W) + (reversed ? -4 : 4), y1 = sy(a.y() + CARD_H / 2.0);
            double x2 = sx(reversed ? b.x() + CARD_W : b.x()) + (reversed ? 4 : -4), y2 = sy(b.y() + CARD_H / 2.0);
            double middle = (x1 + x2) / 2;
            if (near(x, y, x1, y1, middle, y1) || near(x, y, middle, y1, middle, y2)
                    || near(x, y, middle, y2, x2, y2)) return link;
        }
        return null;
    }
    private static boolean near(double x, double y, double x1, double y1, double x2, double y2) {
        return x >= Math.min(x1, x2) - 4 && x <= Math.max(x1, x2) + 4
                && y >= Math.min(y1, y2) - 4 && y <= Math.max(y1, y2) + 4;
    }
    private void connect(String from, String to) {
        if (from.equals(to) || !byId.containsKey(from) || !byId.containsKey(to)) return;
        Link edge = new Link(from, to);
        if (links.contains(edge)) return;
        if (path(to, from, new HashSet<>())) { error = text("cycle").getString(); return; }
        remember();
        links.add(edge);
        rebuildViewportIndexes();
        error = "";
        init();
    }
    private boolean path(String from, String to, Set<String> seen) {
        if (from.equals(to)) return true;
        if (!seen.add(from)) return false;
        return links.stream().filter(link -> link.from().equals(from))
                .anyMatch(link -> path(link.to(), to, seen));
    }
    private void removeLink(Link link) {
        if (link == null) return;
        remember();
        links.remove(link);
        rebuildViewportIndexes();
        selectedLink = null;
        init();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked() || pending != null || lockPending != null) return true;
        if (button == 0 && !confirmExit && contextTask.isBlank() && propertiesTask.isBlank() && minimapAt(x, y)) {
            minimapDragProjection = minimapProjection();
            minimapDragging = true; minimapFrame=System.nanoTime();moveMinimap(x, y); return true;
        }
        if (!propertiesTask.isBlank()) {
            if (button == 0) {
                pressedPropertiesCopy = x >= propertyX() && x < propertyX() + propertyWidth()
                        && y >= propertyY() + propertyHeight() - 32 && y < propertyY() + propertyHeight()
                        ? x < width / 2. ? 1 : 2 : propertyViewport().contains(x, y) ? 3 : 0;
            }
            return true;
        }
        if (!contextTask.isBlank()) {
            if (button == 0) {
                pressedContextAction = x >= contextX && x < contextX + contextWidth()
                        && y >= contextY && y < contextY + contextItems().length * 22
                        ? (int) ((y - contextY) / 22) : -2;
            }
            return true;
        }
        if (confirmExit) {
            if (button == 0 && y >= height / 2 + 22 && y < height / 2 + 44) {
                pressedExitAction = x >= width / 2 - 106 && x < width / 2 - 6 ? 0
                        : x >= width / 2 + 6 && x < width / 2 + 106 ? 1 : -1;
            }
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (y >= top() && y < bottom() && x >= 0 && x < drawerExtent()) {
            if (!drawerMotion.settled()) return true;
            var row=drawerRows().visibleRows(y-top()+drawerScroll.position(),y-top()+drawerScroll.position()+.01).stream().findFirst().orElse(null);
            if(row==null || row.section())return true;
            if(row.group()!=null) { if(button==0)toggleDrawerGroup(row.group().id()); return true; }
            String id=row.quest().id();
            if (button == 1) {
                openContext(id, x, y);
            } else if (button == 0 && !id.isBlank()) {
                selectTask(id);
                if (!multiSelect) {
                    var p = positions.get(id);
                    panX = (width - drawerExtent()) / 2. - (p.x() + CARD_W / 2.) * zoom;
                    panY = (top() + bottom()) / 2. - (p.y() + CARD_H / 2.) * zoom;
                }
            }
            return true;
        }
        if (x < canvasLeft() || x >= width - 12) return false;
        if (y < top() || y >= bottom()) return false;
        setFocused(null);
        if (button == 2) { minimapNavigationProjection=null;movingCanvas = true; dragX = x; dragY = y; originX = panX; originY = panY; return true; }
        if (button == 1) {
            String hit = nodeAt(x, y);
            if (!hit.isBlank() || multiSelect) openContext(hit, x, y);
            else removeLink(linkAt(x, y));
            return true;
        }
        if (button != 0) return false;
        String hit = nodeAt(x, y);
        if (multiSelect) {
            if (!hit.isBlank()) selectTask(hit);
            return true;
        }
        if (!wireFrom.isBlank() && !hit.isBlank() && !hit.equals(wireFrom)) {
            String source = wireFrom;
            wireFrom = "";
            connect(source, hit);
            return true;
        }
        if (!hit.isBlank()) {
            var p = positions.get(hit);
            if (Math.abs(x - sx(p.x())) <= 8 || Math.abs(x - sx(p.x() + CARD_W)) <= 8) {
                wireFrom = hit;
                wireDragged = false;
            } else {
                selected = hit;
                selectedTasks.clear();
                selectedTasks.add(hit);
                draggingNode = hit;
                dragX = x; dragY = y; originX = p.x(); originY = p.y();
                dragRemembered = false;
            }
            selectedLink = null;
            return true;
        }
        wireFrom = "";
        selectedLink = linkAt(x, y);
        selected = "";
        selectedTasks.clear();
        init();
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && minimapDragging) { moveMinimap(x, y); return true; }
        if (pending != null || lockPending != null || confirmExit || !contextTask.isBlank()
                || !propertiesTask.isBlank() || inputBlocked()) return true;
        if (button == 2 && movingCanvas) {
            panX = originX + x - dragX; panY = originY + y - dragY; return true;
        }
        if (button == 0 && !draggingNode.isBlank()) {
            if (!dragRemembered && Math.hypot(x - dragX, y - dragY) > 3) {
                remember(); dragRemembered = true;
            }
            if (dragRemembered) positions.put(draggingNode, new QuestUnlockLayout.Position(
                    (int) Math.clamp(Math.round(originX + (x - dragX) / zoom), -100000, 100000),
                    (int) Math.clamp(Math.round(originY + (y - dragY) / zoom), -100000, 100000)));
            return true;
        }
        if (button == 0 && !wireFrom.isBlank()) {
            wireDragged = true;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && minimapDragging) {
            minimapDragging=false;minimapNavigationProjection=minimapDragProjection;minimapDragProjection=null;
            navigationWidth=width;navigationHeight=height;navigationZoom=zoom;navigationCanvasLeft=canvasLeft();
            return true;
        }
        if (button == 0 && pressedPropertiesCopy >= 0) {
            int action = pressedPropertiesCopy;
            pressedPropertiesCopy = -1;
            boolean footer = y >= propertyY() + propertyHeight() - 32 && y < propertyY() + propertyHeight()
                    && x >= propertyX() && x < propertyX() + propertyWidth();
            if (action == 1 && footer && x < width / 2.) copyProperties(propertiesTask);
            else if (action == 2 && footer && x >= width / 2.) { String id = propertiesTask; propertiesTask = ""; openTask(id); }
            else if (action == 0) propertiesTask = "";
            return true;
        }
        if (button == 0 && pressedContextAction >= 0) {
            int action = pressedContextAction;
            pressedContextAction = -1;
            if (!contextTask.isBlank() && x >= contextX && x < contextX + contextWidth()
                    && y >= contextY && y < contextY + contextItems().length * 22
                    && action == (int) ((y - contextY) / 22)) {
                switch (action) {
                    case 0 -> { if (byId.containsKey(contextTask)) propertiesTask = contextTask; }
                    case 1 -> copyProperties(contextTask);
                    case 2 -> selectAll();
                    case 3 -> invertSelection();
                    case 4 -> setSelectedLocked(true);
                    case 5 -> setSelectedLocked(false);
                    case 6 -> disconnectContext();
                    case 7 -> openTask(contextTask);
                    default -> { }
                }
            }
            contextTask = "";
            return true;
        }
        if (button == 0 && pressedContextAction == -2) {
            pressedContextAction = -1;
            contextTask = "";
            return true;
        }
        if (button == 0 && pressedExitAction >= 0) {
            int action = pressedExitAction;
            pressedExitAction = -1;
            if (y >= height / 2 + 22 && y < height / 2 + 44) {
                if (action == 0 && x >= width / 2 - 106 && x < width / 2 - 6) closeTo(parent);
                else if (action == 1 && x >= width / 2 + 6 && x < width / 2 + 106) {
                    confirmExit = false;
                    init();
                }
            }
            return true;
        }
        if (button == 2) { movingCanvas = false; return true; }
        if (button == 0) {
            if (wireDragged && !wireFrom.isBlank()) {
                String source = wireFrom;
                wireFrom = "";
                wireDragged = false;
                String target = nodeAt(x, y);
                if (!target.isBlank() && !target.equals(source)) connect(source, target);
                return true;
            }
            if (!draggingNode.isBlank()) {
                String released = draggingNode;
                boolean clicked = !dragRemembered && released.equals(nodeAt(x, y));
                draggingNode = "";
                long now = System.nanoTime();
                if (clicked && released.equals(lastReleasedNode) && now - lastNodeRelease < 350_000_000L) {
                    lastReleasedNode = ""; openTask(released); return true;
                }
                lastReleasedNode = clicked ? released : ""; lastNodeRelease = now;
                rebuildViewportIndexes();
                init();
                return true;
            }
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!propertiesTask.isBlank() && !inputBlocked()) { propertyScroll.wheel(vertical * 30); return true; }
        if (pending != null || lockPending != null || confirmExit || !contextTask.isBlank()
                || !propertiesTask.isBlank() || inputBlocked()) return true;
        if (x < drawerExtent() && y >= top() && y < bottom()) {
            drawerScroll.wheel(vertical * 56);
            return true;
        }
        if (x < canvasLeft() || x >= width - 12) return false;
        if (y < top() || y >= bottom()) return super.mouseScrolled(x, y, horizontal, vertical);
        minimapOverview = false;
        minimapNavigationProjection=null;
        minimapOverviewButton.setSelectionHighlighted(false);
        if (minimapAt(x, y)) {
            x = (canvasLeft() + width - 12) / 2.; y = (top() + bottom()) / 2.;
        }
        double old = zoom;
        zoom = Math.clamp(zoom * Math.pow(1.12, vertical), .4, 2);
        panX = x - drawerExtent() - (x - drawerExtent() - panX) * zoom / old;
        panY = y - (y - panY) * zoom / old;
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (pending != null || lockPending != null || inputBlocked()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!propertiesTask.isBlank() || !contextTask.isBlank()) {
                propertiesTask = contextTask = "";
                return true;
            }
            if (!wireFrom.isBlank()) { wireFrom = ""; return true; }
            if (taskDrawer && !confirmExit) { toggleDrawer(); return true; }
            onClose(); return true;
        }
        if (confirmExit || !propertiesTask.isBlank() || !contextTask.isBlank()) return true;
        if (getFocused() instanceof EditBox) return super.keyPressed(key, scan, modifiers);
        if (key == GLFW.GLFW_KEY_DELETE && selectedLink != null) { removeLink(selectedLink); return true; }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Z) { restore(undo, redo); return true; }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Y) { restore(redo, undo); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    private void save() {
        if (pending != null || lockPending != null || minecraft.player == null
                || !ClientEditMode.enabled() || !minecraft.player.hasPermissions(2)
                || minecraft.getSingleplayerServer() == null) return;
        if (coordinateX != null) {
            try {
                new QuestUnlockLayout.Position(Integer.parseInt(coordinateX.getValue().strip()),
                        Integer.parseInt(coordinateY.getValue().strip()));
            } catch (IllegalArgumentException invalid) {
                error = text("invalid_position").getString();
                return;
            }
        }
        if (tasks.stream().anyMatch(task -> QuestRepository.fileOf(task.id()) == null)) {
            error = text("no_source").getString();
            return;
        }
        Map<String, List<String>> parents = new LinkedHashMap<>();
        for (QuestDefinition task : tasks) parents.put(task.id(), links.stream()
                .filter(link -> link.to().equals(task.id())).map(Link::from).sorted().toList());
        QuestUnlockLayout layout;
        try { layout = new QuestUnlockLayout(QuestUnlockGraphLayout.persisted(positions, defaultPositions)); }
        catch (IllegalArgumentException oversized) {
            error = text("too_many_custom_positions").getString();
            return;
        }
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        pending = CompletableFuture.supplyAsync(() -> QuestAuthoring.editUnlockGraph(
                server.getPlayerList().getPlayer(playerId), expectedRevision, parents, expectedHashes, layout,
                Map.copyOf(modes)), server);
        init();
    }
    @Override public void onClose() {
        if (pending != null || lockPending != null) return;
        if (dirty() && !confirmExit) { confirmExit = true; init(); }
        else if (!confirmExit) closeTo(parent);
    }
    private int mapWidth() { return Math.min(164, Math.max(96, width / 4)); }
    private int mapHeight() { return Math.min(88, Math.max(48, (bottom() - top()) / 4)); }
    private int mapX() { return width - 18 - mapWidth(); }
    private int mapY() { return bottom() - mapHeight() - 6; }
    private int mapContentHeight() { return mapHeight() - 18; }
    private boolean minimapAt(double x, double y) {
        return x >= mapX() && x < mapX() + mapWidth() && y >= mapY() && y < mapY() + mapContentHeight();
    }
    private void rebuildMinimap() {
        minWorldX = positions.values().stream().mapToInt(QuestUnlockLayout.Position::x).min().orElse(0) - 10.;
        minWorldY = positions.values().stream().mapToInt(QuestUnlockLayout.Position::y).min().orElse(0) - 10.;
        maxWorldX = positions.values().stream().mapToInt(p -> p.x() + CARD_W).max().orElse(CARD_W) + 10.;
        maxWorldY = positions.values().stream().mapToInt(p -> p.y() + CARD_H).max().orElse(CARD_H) + 10.;
        minimapRevision = Long.MIN_VALUE;
        refreshMinimapStates();
    }
    private void refreshMinimapStates() {
        long revision = ClientQuestProgress.listRevision();
        if (revision == minimapRevision) return;
        Arrays.fill(minimapCounts, 0);
        var dots = new ArrayList<MinimapDot>(tasks.size());
        for (var task : tasks) {
            var position = positions.get(task.id());
            if (position == null) continue;
            var state = taskState(task);
            dots.add(new MinimapDot(task.id(), position.x(), position.y(), state));
            minimapCounts[state.ordinal()]++;
        }
        minimapDots = List.copyOf(dots);
        minimapRevision = revision;
        minimapRasterProjection = null;
    }
    private void rasterizeMinimap(MapProjection map) {
        int w = mapWidth(), h = mapContentHeight();
        if (map.equals(minimapRasterProjection) && w == minimapRasterWidth && h == minimapRasterHeight) return;
        int[] masks = new int[w * h];
        Arrays.fill(minimapOffscreen, 0); Arrays.fill(minimapOffscreenStates, 0);
        for (var dot : minimapDots) {
            int rawLeft = (int) Math.floor(map.x(dot.x())) - mapX(), rawTop = (int) Math.floor(map.y(dot.y())) - mapY();
            int rawRight = (int) Math.ceil(map.x(dot.x() + CARD_W)) - mapX();
            int rawBottom = (int) Math.ceil(map.y(dot.y() + CARD_H)) - mapY();
            if (rawRight <= 1 || rawLeft >= w - 1 || rawBottom <= 1 || rawTop >= h - 1) {
                int direction = rawRight <= 1 ? 0 : rawLeft >= w - 1 ? 1 : rawBottom <= 1 ? 2 : 3;
                minimapOffscreen[direction]++; minimapOffscreenStates[direction] |= dot.state().mask;
                continue;
            }
            int left = Math.clamp(rawLeft, 1, w - 2), top = Math.clamp(rawTop, 1, h - 2);
            int right = Math.clamp(rawRight, left + 1, w - 1);
            int bottom = Math.clamp(rawBottom, top + 1, h - 1);
            for (int py = top; py < bottom; py++)
                for (int px = left; px < right; px++) masks[py * w + px] |= dot.state().mask;
        }
        var runs = new ArrayList<int[]>();
        for (int py = 1; py < h - 1; py++) {
            int start = 1, previous = 0;
            for (int px = 1; px <= w - 1; px++) {
                int color = px == w - 1 ? 0 : minimapPixelColor(masks[py * w + px], px, py);
                if (color == previous) continue;
                if (previous != 0) runs.add(new int[]{start, py, px, previous});
                start = px; previous = color;
            }
        }
        minimapRasterMasks = masks;
        minimapRuns = List.copyOf(runs);
        minimapRasterProjection = map; minimapRasterWidth = w; minimapRasterHeight = h;
    }
    private static int minimapPixelColor(int mask, int x, int y) {
        if (mask == 0) return 0;
        int index = (x + y) % Integer.bitCount(mask);
        for (var state : TaskState.values())
            if ((mask & state.mask) != 0 && index-- == 0) return state.color;
        return 0;
    }
    private MapProjection minimapProjection() {
        if (minimapDragging && minimapDragProjection != null) return minimapDragProjection;
        if(minimapNavigationProjection!=null && navigationWidth==width && navigationHeight==height
                && navigationZoom==zoom && navigationCanvasLeft==canvasLeft())return minimapNavigationProjection;
        if (minimapOverview) {
            double left = Math.min(minWorldX, wx(canvasLeft())), right = Math.max(maxWorldX, wx(width - 12));
            double top = Math.min(minWorldY, wy(top())), bottom = Math.max(maxWorldY, wy(bottom()));
            double scale = Math.min((mapWidth() - 8) / Math.max(1, right - left),
                    (mapContentHeight() - 8) / Math.max(1, bottom - top));
            return new MapProjection(scale, mapX() + mapWidth() / 2. - (left + right) / 2 * scale,
                    mapY() + mapContentHeight() / 2. - (top + bottom) / 2 * scale);
        }
        double scale = zoom * Math.min((mapWidth() - 16.) / Math.max(1, width - 12 - canvasLeft()),
                (mapContentHeight() - 16.) / Math.max(1, bottom() - top())) * .6;
        // The minimap mirrors canvas zoom; its central viewport has matching aspect and context around it.
        double centerX = wx((canvasLeft() + width - 12) / 2.), centerY = wy((top() + bottom()) / 2.);
        return new MapProjection(scale, mapX() + mapWidth() / 2. - centerX * scale,
                mapY() + mapContentHeight() / 2. - centerY * scale);
    }
    private double[] minimapViewport() {
        var map = minimapProjection();
        return new double[]{map.x(wx(canvasLeft())), map.y(wy(top())),
                (width - 12 - canvasLeft()) / zoom * map.scale(),
                (bottom() - top()) / zoom * map.scale()};
    }
    private void renderMinimap(GuiGraphics g) {
        int x = mapX(), y = mapY(), w = mapWidth(), h = mapHeight();
        g.fill(x, y, x + w, y + h, 0xE81D252D);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        refreshMinimapStates();
        var map = minimapProjection();
        rasterizeMinimap(map);
        g.drawManaged(() -> {
            for (int[] run : minimapRuns) g.fill(x + run[0], y + run[1], x + run[2], y + run[1] + 1, run[3]);
        });
        double[] viewport = minimapViewport();
        int viewX = (int) Math.floor(viewport[0]), viewY = (int) Math.floor(viewport[1]);
        int viewW = Math.max(1, (int) Math.ceil(viewport[0] + viewport[2]) - viewX);
        int viewH = Math.max(1, (int) Math.ceil(viewport[1] + viewport[3]) - viewY);
        g.renderOutline(viewX, viewY, viewW, viewH, 0xFFFFDF98);
        for (int direction = 0; direction < 4; direction++) {
            if (minimapOffscreen[direction] == 0) continue;
            int arrowX = direction == 0 ? x + 3 : direction == 1 ? x + w - 13 : x + w / 2 - 5;
            int arrowY = direction == 2 ? y + 3 : direction == 3 ? y + mapContentHeight() - 13 : y + mapContentHeight() / 2 - 5;
            String icon = direction == 0 ? "back" : direction == 1 ? "forward" : direction == 2 ? "sort_up" : "sort_down";
            JournalNavButton.drawIcon(g, icon, arrowX, arrowY, 10, 0xFFF0E3C8);
            for (var state : TaskState.values()) {
                if ((minimapOffscreenStates[direction] & state.mask) != 0)
                    g.fill(arrowX + state.ordinal() * 3, arrowY + 10, arrowX + state.ordinal() * 3 + 2, arrowY + 12, state.color);
            }
        }
        g.disableScissor();
        int segment = (w - 12) / 3;
        for (var state : TaskState.values()) {
            int left = x + 6 + state.ordinal() * segment;
            g.fill(left, y + h - 11, left + 5, y + h - 6, state.color);
            int count = minimapCounts[state.ordinal()];
            String label = Integer.toString(count);
            if (font.width(label) > segment - 10 && count >= 1000) label = count / 1000 + "k";
            g.drawString(font, font.plainSubstrByWidth(label, segment - 10),
                    left + 8, y + h - 13, state.color, false);
        }
        g.renderOutline(x, y, w, h, minimapDragging ? 0xFFFFDF98 : 0xFF718894);
    }
    private void moveMinimap(double x, double y) {
        minimapPointerX=x;minimapPointerY=y;
        var map = minimapProjection();
        double targetX = (x - map.originX()) / map.scale();
        double targetY = (y - map.originY()) / map.scale();
        panX = (canvasLeft() + width - 12) / 2. - drawerExtent() - targetX * zoom;
        panY = (top() + bottom()) / 2. - targetY * zoom;
    }
    private static double edgeMotion(double point,double low,double high) {
        if(point<low+12)return -Math.clamp((low+12-point)/12,0,3);
        if(point>high-12)return Math.clamp((point-high+12)/12,0,3);
        return 0;
    }
    private void advanceMinimapDrag() {
        long now=System.nanoTime(); double dt=Math.min(.05,(now-minimapFrame)/1_000_000_000.);minimapFrame=now;
        if(minimapDragProjection==null)return;
        double dx=edgeMotion(minimapPointerX,mapX(),mapX()+mapWidth())*dt*70;
        double dy=edgeMotion(minimapPointerY,mapY(),mapY()+mapContentHeight())*dt*70;
        if(dx==0 && dy==0)return;
        minimapDragProjection=new MapProjection(minimapDragProjection.scale(),minimapDragProjection.originX()-dx,minimapDragProjection.originY()-dy);
        moveMinimap(minimapPointerX,minimapPointerY);
    }
    @Override public void removed() {
        searchWorker.cancel();submittedSearch=null;searchChangedAt=0;
        minimapDragging=false;minimapDragProjection=null;
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
