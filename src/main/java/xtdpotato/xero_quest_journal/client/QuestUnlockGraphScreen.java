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
    private record Draft(Map<String, QuestUnlockLayout.Position> positions, Set<Link> links) {}

    private final Screen parent;
    private final List<QuestDefinition> tasks = ClientQuestDefinitions.all().stream()
            .sorted(Comparator.comparing(QuestDefinition::id)).toList();
    private final Map<String, QuestDefinition> byId = new LinkedHashMap<>();
    private final Map<String, QuestUnlockLayout.Position> positions = new LinkedHashMap<>();
    private final Map<String, QuestUnlockLayout.Position> defaultPositions;
    private final Set<Link> links = new LinkedHashSet<>();
    private ViewportIndex<QuestDefinition> nodeIndex = new ViewportIndex<>(List.of());
    private ViewportIndex<Link> linkIndex = new ViewportIndex<>(List.of());
    private Map<String, List<Link>> incidentLinks = Map.of();
    private String drawerTaskQuery;
    private List<QuestDefinition> drawerTaskCache = List.of();
    private long unlockCountRevision = Long.MIN_VALUE;
    private int cachedUnlockedCount;
    private final Map<String, String> expectedHashes = new HashMap<>();
    private final Deque<Draft> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final long expectedRevision = ClientQuestDefinitions.revision();
    private final Draft initial;
    private CompletableFuture<QuestAuthoring.Result> pending;
    private String error = "", searchText = "", selected = "", wireFrom = "";
    private Link selectedLink;
    private EditBox search;
    private double zoom = 1, panX, panY, dragX, dragY, originX, originY;
    private String draggingNode = "";
    private String hoveredNode = "";
    private String contextTask = "", propertiesTask = "";
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

    public QuestUnlockGraphScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        tasks.forEach(task -> byId.put(task.id(), task));
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
    private Draft snapshot() { return new Draft(Map.copyOf(positions), Set.copyOf(links)); }
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
        rebuildViewportIndexes();
        selectedLink = null; wireFrom = "";
        init();
    }
    private boolean dirty() { return !snapshot().equals(initial); }
    private int top() { return 70; }
    private int bottom() { return height - 70; }
    private int drawerWidth() { return Math.min(224, Math.max(100, width / 3)); }
    private int drawerExtent() { return (int) Math.round(drawerWidth() * drawerProgress); }
    private int canvasLeft() { return Math.max(12, drawerExtent() + 12); }
    private double sx(double x) { return drawerExtent() + panX + x * zoom; }
    private double sy(double y) { return panY + y * zoom; }
    private double wx(double x) { return (x - drawerExtent() - panX) / zoom; }
    private double wy(double y) { return (y - panY) / zoom; }

    private void fit() {
        if (positions.isEmpty()) { zoom = 1; panX = width / 2.; panY = height / 2.; return; }
        int minX = positions.values().stream().mapToInt(QuestUnlockLayout.Position::x).min().orElse(0);
        int minY = positions.values().stream().mapToInt(QuestUnlockLayout.Position::y).min().orElse(0);
        int maxX = positions.values().stream().mapToInt(p -> p.x() + CARD_W).max().orElse(CARD_W);
        int maxY = positions.values().stream().mapToInt(p -> p.y() + CARD_H).max().orElse(CARD_H);
        double availableWidth = width - drawerExtent();
        double needed = Math.min((availableWidth - 56.) / Math.max(1, maxX - minX),
                (bottom() - top() - 24.) / Math.max(1, maxY - minY));
        zoom = Math.clamp(needed, .45, 1.6);
        panX = needed < .45 ? 24 - minX * zoom : availableWidth / 2. - (minX + maxX) * zoom / 2.;
        panY = needed < .45 ? top() + 18 - minY * zoom
                : (top() + bottom()) / 2. - (minY + maxY) * zoom / 2.;
    }

    @Override protected void init() {
        clearWidgets();
        if (panX == 0 && panY == 0) fit();
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
        search.setResponder(value -> { searchText = value; drawerScroll.jump(0); });
        addRenderableWidget(search);
        drawerButton = addRenderableWidget(new EditorIconButton(12, 42, text("task_list"),
                this::toggleDrawer, styles, "unlock-task-list", "all"));
        drawerButton.setSelectionHighlighted(taskDrawer);
        multipleButton = addRenderableWidget(new EditorIconButton(42, 42, text("multi_select"),
                this::toggleMultiple, styles, "unlock-multi-select", multiSelect ? "choose" : "all"));
        multipleButton.setSelectionHighlighted(multiSelect);
        drawerRail = addRenderableWidget(new ScrollRail(drawerExtent() - 10, top(),
                Math.max(8, bottom() - top()), drawerScroll));
        drawerRail.visible = drawerMotion.visible() && drawerMotion.settled();
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
        drawerMotion.advance(System.nanoTime(), popupAnimationMillis());
        drawerProgress = drawerMotion.eased();
        List<QuestDefinition> drawerTasks = drawerTasks();
        drawerScroll.limit(drawerTasks.size() * 28 - Math.max(1, bottom() - top()));
        drawerScroll.advance();
        drawerRail.setX(drawerExtent() - 10);
        drawerRail.visible = drawerMotion.visible() && drawerMotion.settled()
                && drawerScroll.maximum() > 0;
        backdrop(g, 0xF01E1E22);
        g.fill(0, 0, width, 38, 0xFF19191D);
        g.fill(0, 38, width, 39, TriggerNodeStyle.PANEL_BORDER);
        g.fill(0, height - 64, width, height, 0xFF19191D);
        g.fill(0, height - 65, width, height - 64, TriggerNodeStyle.PANEL_BORDER);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, width - 90)),
                78, 46, 0xFFF1D8A3, false);
        int unlocked = unlockedCount();
        int barLeft = Math.max(12, width / 2 - 130), barRight = Math.min(width - 12, width / 2 + 130);
        g.fill(barLeft, height - 43, barRight, height - 39, 0xFF414B53);
        g.fill(barLeft, height - 43, barLeft + (barRight - barLeft) * unlocked / Math.max(1, tasks.size()),
                height - 39, 0xFF79C994);
        g.drawCenteredString(font, text("progress", unlocked, tasks.size()), width / 2,
                height - 59, 0xFFE0E7E3);
        if (coordinateX != null) {
            g.drawString(font, text("x"), 12, height - 22, 0xFFE0E7E3);
            g.drawString(font, text("y"), 104, height - 22, 0xFFE0E7E3);
        }
        String footer = !error.isBlank() ? error : !lockResult.isBlank() ? lockResult
                : multiSelect ? text("selected_count", selectedTasks.size()).getString() : selected;
        g.drawString(font, font.plainSubstrByWidth(footer, Math.max(1, width - 204)),
                196, height - 22, !error.isBlank() ? 0xFFFF9797 : 0xFFE0E7E3, false);
        g.enableScissor(canvasLeft(), top(), width - 12, bottom());
        renderGrid(g);
        Set<Link> movingLinks = draggingNode.isBlank() ? Set.of()
                : new HashSet<>(incidentLinks.getOrDefault(draggingNode, List.of()));
        for (Link link : visibleLinks()) {
            if (!movingLinks.contains(link)) renderLink(g, link, selectedLink != null && selectedLink.equals(link));
        }
        for (Link link : movingLinks)
            renderLink(g, link, selectedLink != null && selectedLink.equals(link));
        if (!wireFrom.isBlank()) {
            var source = positions.get(wireFrom);
            if (source != null) {
                double x = sx(source.x() + CARD_W), y = sy(source.y() + CARD_H / 2.0);
                TriggerNodeStyle.wire(g, x, y, mx, my, 0xFFE9C576, 2, true, canvasLeft(), top(), width - 12, bottom());
            }
        }
        hoveredNode = nodeAt(mx, my);
        for (QuestDefinition task : visibleTasks()) renderTask(g, task, mx, my);
        g.disableScissor();
        renderTaskDrawer(g, mx, my);
        var exitButtons = children().stream().filter(child -> child instanceof StyledButton button
                && (button.styleId().equals("unlock-discard") || button.styleId().equals("unlock-continue")))
                .map(child -> (StyledButton) child).toList();
        exitButtons.forEach(button -> button.visible = false);
        try {
            super.renderContents(g, mx, my, delta);
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
        int propertyWidth = Math.min(380, width - 32), propertyX = (width - propertyWidth) / 2;
        int propertyY = Math.max(40, (height - 172) / 2);
        renderAnimatedMenu(g, "properties", !confirmExit && !propertiesTask.isBlank() ? propertiesTask : null,
                viewport(propertyX, propertyY, propertyX + propertyWidth, propertyY + 172),
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
    }
    private void renderTaskDrawer(GuiGraphics g, int mx, int my) {
        if (drawerExtent() <= 0) return;
        int w = drawerWidth(), x = drawerExtent() - w;
        g.enableScissor(0, top(), drawerExtent(), bottom());
        g.fill(x, top(), x + w, bottom(), 0xFF2A323B);
        g.fill(x + w - 2, top(), x + w, bottom(), 0xFF9CB0BD);
        var filtered = drawerTasks();
        int scroll = drawerScroll.pixels();
        int first = Math.max(0, (scroll - 26) / 28);
        int end = Math.min(filtered.size(), (scroll + bottom() - top() + 27) / 28 + 1);
        for (int index = first; index < end; index++) {
            int y = top() + index * 28 - scroll;
            QuestDefinition task = filtered.get(index);
            boolean unlocked = isUnlocked(task);
            boolean chosen = selectedTasks.contains(task.id()) || !multiSelect && task.id().equals(selected);
            g.fill(x + 6, y + 1, x + w - 12, y + 26, unlocked ? 0xFF315A43 : 0xFF5A3438);
            if (chosen) g.renderOutline(x + 6, y + 1, w - 18, 25, 0xFFF0CF91);
            drawLockStatus(g, unlocked, x + 10, y + 5, 16);
            g.drawString(font, font.plainSubstrByWidth(QuestText.title(task).getString(), w - 54),
                    x + 30, y + 9, 0xFFE8EDEB, false);
        }
        g.disableScissor();
    }
    private boolean isUnlocked(QuestDefinition task) {
        var progress = ClientQuestProgress.view(task.id());
        return progress == null ? task.defaultUnlocked() : progress.unlocked();
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
        if (Objects.equals(drawerTaskQuery, searchText)) return drawerTaskCache;
        drawerTaskQuery = searchText;
        drawerTaskCache = searchText.isBlank() ? tasks : tasks.stream().filter(task ->
                QuestIcons.matchesSearch(searchText, task.id(), QuestText.title(task).getString())).toList();
        return drawerTaskCache;
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
                "lock_selected", "unlock_selected", "disconnect"};
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
    private int unlockedCount() {
        long revision = ClientQuestProgress.revision();
        if (revision != unlockCountRevision) {
            cachedUnlockedCount = (int) tasks.stream().filter(task -> {
                var progress = ClientQuestProgress.view(task.id());
                return progress != null && progress.unlocked();
            }).count();
            unlockCountRevision = revision;
        }
        return cachedUnlockedCount;
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
                double x1 = from.x() + CARD_W + 4, x2 = to.x() - 4;
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
    private void renderProperties(GuiGraphics g) {
        QuestDefinition task = byId.get(propertiesTask);
        if (task == null) return;
        int w = Math.min(380, width - 32), x = (width - w) / 2, y = Math.max(40, (height - 172) / 2);
        g.fill(x, y, x + w, y + 172, 0xFF29323C);
        g.renderOutline(x, y, w, 172, 0xFF9CB0BD);
        String[] rows = {text("properties").getString(), text("property_id", task.id()).getString(),
                text("property_title", QuestText.title(task).getString()).getString(),
                text("property_group", groupName(task)).getString(),
                text("property_chapter", chapterName(task)).getString(),
                text("property_type", task.type()).getString(),
                text("property_parents", String.join(", ", task.parents())).getString()};
        for (int i = 0; i < rows.length; i++)
            g.drawString(font, font.plainSubstrByWidth(rows[i], w - 20), x + 10, y + 9 + i * 19,
                    i == 0 ? 0xFFF0D59B : 0xFFE4EAEF, false);
        g.drawCenteredString(font, text("copy_properties"), x + w / 2, y + 148, 0xFFF0D59B);
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
        var progress = ClientQuestProgress.view(task.id());
        boolean complete = progress != null && progress.completed();
        boolean locked = !isUnlocked(task);
        boolean highlighted = !searchText.isBlank() && QuestIcons.matchesSearch(searchText,
                task.id(), QuestText.title(task).getString());
        boolean chosen = task.id().equals(selected) || selectedTasks.contains(task.id());
        // Bright state band (complete gold, locked coral, available green) with a dark title.
        int top = complete ? 0xFFF2C75C : locked ? 0xFFF27F6E : 0xFF7DD9A4;
        int bottom = complete ? 0xFFE0AF42 : locked ? 0xFFDE6553 : 0xFF62C48C;
        int header = Math.max(2, (int) Math.round(18 * zoom));
        g.fill(x + 2, y + 4, x + w + 3, y + h + 5, 0x38000000);
        g.fill(x + 1, y + 2, x + w + 2, y + h + 3, 0x30000000);
        if (chosen || highlighted) {
            ControlAppearance.draw(g, x - 3, y - 3, w + 6, h + 6, "rounded", 8, 0, 0x38FFD27A, 1);
            ControlAppearance.draw(g, x - 2, y - 2, w + 4, h + 4, "rounded", 7, 0, 0xA0FFD27A, 1);
        }
        int border = chosen || highlighted ? TriggerNodeStyle.GOLD : hoveredNode.equals(task.id()) ? 0xFF5C5C66
                : TriggerNodeStyle.BODY_BORDER;
        ControlAppearance.draw(g, x, y, w, h, "rounded", 5, TriggerNodeStyle.BODY, border, 1);
        g.fillGradient(x + 1, y + 2, x + w - 1, y + header, top, bottom);
        g.fill(x + 4, y + 1, x + w - 4, y + 2, top);
        g.fill(x + 5, y + 1, x + w - 5, y + 2, 0x50FFFFFF);
        g.fill(x + 1, y + header, x + w - 1, y + header + 1, 0x60000000);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) zoom, (float) zoom, 1);
        String title = QuestText.title(task).getString();
        g.drawString(font, font.plainSubstrByWidth(title, CARD_W - 34), 8, 5, 0xFF17171B, false);
        drawLockStatus(g, !locked, CARD_W - 20, 2, 13, 0xFF17171B);
        QuestIcons.render(g, task.icon().isBlank() ? "minecraft:book" : task.icon(), 8, 24, 22);
        g.drawString(font, font.plainSubstrByWidth(task.id(), CARD_W - 44), 36, 25,
                0xFFAFC2C5, false);
        g.drawString(font, font.plainSubstrByWidth(text("property_group", groupName(task)).getString(), CARD_W - 44),
                36, 37, 0xFFB5C8CF, false);
        g.drawString(font, font.plainSubstrByWidth(text("property_chapter", chapterName(task)).getString(), CARD_W - 16),
                8, 54, 0xFFB5C8CF, false);
        int statusColor = complete ? 0xFFF2CF83 : locked ? 0xFFFF9A9A : 0xFF9BE0B0;
        MailDecor.diamond(g, 11, 76, 2, statusColor);
        g.drawString(font, text(complete ? "complete" : locked ? "locked" : "available"),
                17, 72, statusColor, false);
        g.pose().popPose();
        TriggerNodeStyle.port(g, x + w, y + h / 2.0, TriggerNodeStyle.PORT_NEXT, hasOutgoing(task.id()),
                Math.abs(mx - x - w) < 8 && Math.abs(my - y - h / 2.0) < 8);
        TriggerNodeStyle.port(g, x, y + h / 2.0, TriggerNodeStyle.PORT_INPUT, hasIncoming(task.id()), false);
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
        double x1 = sx(from.x() + CARD_W) + 4, y1 = sy(from.y() + CARD_H / 2.0);
        double x2 = sx(to.x()) - 4, y2 = sy(to.y() + CARD_H / 2.0);
        int color = selectedEdge ? TriggerNodeStyle.GOLD : completed(link.from()) ? TriggerNodeStyle.PORT_TRUE : 0xFFD9D9DE;
        TriggerNodeStyle.wire(g, x1, y1, x2, y2, color, selectedEdge ? 3 : 2, false,
                canvasLeft(), top(), width - 12, bottom());
        if (!ClientPreferences.get().reducedMotion && (selectedEdge || completed(link.from())))
            TriggerNodeStyle.flow(g, x1, y1, x2, y2, color, System.nanoTime());
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
            double x1 = sx(a.x() + CARD_W) + 4, y1 = sy(a.y() + CARD_H / 2.0);
            double x2 = sx(b.x()) - 4, y2 = sy(b.y() + CARD_H / 2.0);
            if (TriggerNodeStyle.distance(x1, y1, x2, y2, x, y) <= 6) return link;
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
        if (!propertiesTask.isBlank()) {
            if (button == 0) {
                int w = Math.min(380, width - 32), px = (width - w) / 2, py = Math.max(40, (height - 172) / 2);
                pressedPropertiesCopy = x >= px && x < px + w && y >= py + 138 && y < py + 172 ? 1 : 0;
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
            var filtered = drawerTasks();
            int index = (int) Math.floor((y - top() + drawerScroll.position()) / 28);
            String id = index >= 0 && index < filtered.size() ? filtered.get(index).id() : "";
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
        if (button == 2) { movingCanvas = true; dragX = x; dragY = y; originX = panX; originY = panY; return true; }
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
            if (x >= sx(p.x() + CARD_W) - 8 && x <= sx(p.x() + CARD_W) + 8) {
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
        if (button == 0 && pressedPropertiesCopy >= 0) {
            int action = pressedPropertiesCopy;
            pressedPropertiesCopy = -1;
            int w = Math.min(380, width - 32), px = (width - w) / 2, py = Math.max(40, (height - 172) / 2);
            if (action == 1 && !propertiesTask.isBlank() && x >= px && x < px + w
                    && y >= py + 138 && y < py + 172) copyProperties(propertiesTask);
            else if (action == 0 || x < px || x >= px + w || y < py || y >= py + 138)
                propertiesTask = "";
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
                draggingNode = "";
                rebuildViewportIndexes();
                init();
                return true;
            }
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (pending != null || lockPending != null || confirmExit || !contextTask.isBlank()
                || !propertiesTask.isBlank() || inputBlocked()) return true;
        if (x < drawerExtent() && y >= top() && y < bottom()) {
            drawerScroll.wheel(vertical * 56);
            return true;
        }
        if (x < canvasLeft() || x >= width - 12) return false;
        if (y < top() || y >= bottom()) return super.mouseScrolled(x, y, horizontal, vertical);
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
                server.getPlayerList().getPlayer(playerId), expectedRevision, parents, expectedHashes, layout), server);
        init();
    }
    @Override public void onClose() {
        if (pending != null || lockPending != null) return;
        if (dirty() && !confirmExit) { confirmExit = true; init(); }
        else if (!confirmExit) closeTo(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
