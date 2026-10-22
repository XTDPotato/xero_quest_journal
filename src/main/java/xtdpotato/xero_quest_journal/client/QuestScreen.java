package xtdpotato.xero_quest_journal.client;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoringHistory;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.io.QuestClipboard;
import xtdpotato.xero_quest_journal.core.io.QuestBatchAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestJournalOrderStore;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.QuestTypeStore;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.model.HotEventData;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;
import xtdpotato.xero_quest_journal.core.model.QuestType;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class QuestScreen extends AnimatedQuestScreen {
    private int tasksPerPage() { return ClientQuestProgress.tasksPerPage(); }
    private static final int LIST_OVERSCAN_ROWS = 4;
    private static final int ICON_PICKER_OVERSCAN_ROWS = 4;
    private record ScrollTrack(int x, int y, int width, int height, int thumbX, int thumbY,
                               int thumbWidth, int thumbHeight, double maximum) {}
    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;
    private static final int GUIDE_SNAP_PIXELS = 5;
    private static final List<String> GRAVITY_FLAGS = List.of(
            "left", "right", "top", "bottom", "center", "center_vertical", "center_horizontal");
    private final Screen parent;
    private final boolean overviewMode;
    private final String mediaOwner = UUID.randomUUID().toString();
    private String category = "active";
    private double categoryScroll;
    private final SmoothScroll categoryMotion = new SmoothScroll();
    private int previousCategoryViewportWidth;
    private boolean revealCategory = true;
    private long categoryTransitionStart;
    private String categoryTransitionTarget = "";
    private boolean adminAtInit;
    private boolean authorAtInit;
    private List<QuestDefinition> quests = List.of();
    private int listPage;
    private int listPages = 1;
    private boolean revealSelectedPage;
    private final String requestedInitialTask;
    private boolean initialTaskResolved;
    private String revealTaskRow = "";
    private String revealGroupRow = "";
    private EditBox taskSearch;
    private boolean searchableTasks;
    private String searchQuery = "";
    private boolean searchDirty;
    private final JournalSearch journalSearch = new JournalSearch();
    private String groupId = "";
    private String chapterId = "";
    private String selectedId = "";
    private String pendingCompletedSelectionId = "";
    private final Set<String> readMarkedTasks = new HashSet<>();
    private CompletableFuture<String> readAllPending;
    private QuestDefinition selected;
    private QuestCatalog.Group featuredGroup;
    private boolean editMode;
    private boolean showEditorGrid = true;
    private boolean showEditorAxes = true;
    private boolean showEditorBounds = true;
    private boolean systemLayout;
    private int listWidth;
    private int detailsLeft;
    private double listScroll;
    private double listTargetScroll;
    private long listScrollFrameNanos;
    private boolean listDragCandidate;
    private boolean listDragMoved;
    private boolean listScrollbarDragging;
    private double listDragStartY;
    private double listDragStartX;
    private double listDragStartScroll;
    private double listLastDragY;
    private long listLastDragNanos;
    private double listReleaseVelocity;
    private double detailScroll;
    private double detailTargetScroll;
    private long detailScrollFrameNanos;
    private boolean detailScrollbarDragging;
    private boolean listControlClick;
    private boolean selectionControlDown;
    private boolean claimableRewards;
    private final LinkedHashSet<String> selectedTaskIds = new LinkedHashSet<>();
    private final LinkedHashSet<String> selectedGroupIds = new LinkedHashSet<>();
    private final Set<String> collapsedGroupIds = new HashSet<>(ClientPreferences.get().collapsedJournalGroups);
    private String taskReorderCandidate = "";
    private String pressedClaimId = "";
    private long taskReorderPressedAt;
    private boolean taskReorderDragging;
    private boolean taskReorderCompatible;
    private double taskReorderX;
    private double taskReorderY;
    private String taskDropTarget = "";
    private boolean taskDropAfter;
    private List<String> taskReorderIds = List.of();
    private List<JournalListButton> taskDragPreview = List.of();
    private JournalListModel journalList = new JournalListModel(List.of(), 0);
    private JournalListModel groupSlideSource = journalList;
    private final Map<String, UiMotion> groupSlides = new HashMap<>();
    private final Map<String, SmoothScroll> embeddedScrolls = new HashMap<>();
    private String scrollTrackDragging = "";
    private double scrollTrackGrab;
    private Map<String, JournalGroupSlide.Clip> groupRowClips = Map.of();
    private CompletableFuture<QuestBatchAuthoring.Result> batchPending;
    private LayoutEditorState copiedLayout;
    private long revision;
    private ButtonStyleSheet styles;
    private LayoutEditorState layout = LayoutStore.load("journal");
    private final QuestPresentationClient presentationWriter = new QuestPresentationClient();
    private Runnable afterPublication;
    private final Map<String, StyledButton> controls = new LinkedHashMap<>();
    private List<JournalListModel.Row> viewportRows = List.of();
    private JournalListModel queriedJournal;
    private int queriedScrollPixel, queriedListHeight;
    private List<JournalListModel.Row> queriedRows = List.of(), materializedRows = List.of();
    private final Set<String> materializedRowKeys = new HashSet<>();
    private List<LayoutEditorState.Element> frameElements, frameHitElements;
    private Double frameDetailMaximum;
    private final Map<String, StyledButton> previousButtons = new HashMap<>();
    private final Map<String, ButtonActionAnimation> controlMotions = new HashMap<>();
    private final List<StyledButton> menu = new ArrayList<>();
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private CompletableFuture<QuestAuthoringHistory.Result> historyPending;
    private long historyRevision;
    private boolean historyRedo;
    private String menuTarget;
    private String dragTarget;
    private double dragX;
    private double dragY;
    private double lastClickX;
    private double lastClickY;
    private long lastClickNanos;
    private String lastClickTarget;
    private int startX;
    private int startY;
    private String beforeDrag;
    private boolean moving;
    private Form form;
    private String error;
    private CompletableFuture<QuestAuthoring.Result> pending;
    private int menuX;
    private int menuY;
    private final List<String> menuRowGroups = new ArrayList<>();
    private int menuScroll;
    private final SmoothScroll menuMotion = new SmoothScroll();
    private boolean menuDragging, menuMoved;
    private double menuDragX, menuDragY, menuDragScroll;
    private StyledButton menuPressed;
    private String elementParent;
    private StyledChoiceButton<String> groupControl;
    private StyledChoiceButton<String> chapterControl;
    private long progressRevision;
    private CompletableFuture<String> actionPending;
    private String actionMessage;
    private String timedActionMessage;
    private long actionMessageStartedAt;
    private String copiedIdToast = "";
    private PendingClaim pendingClaim;
    private record PendingClaim(CompletableFuture<String> request, String title, String overflowId,
                                Map<String, List<QuestDefinition.Reward>> rewards) {}
    private IconPicker iconPicker;
    private boolean externalPicker;
    private final List<StyledButton> iconChoiceButtons = new ArrayList<>();
    private StyledButton iconPrevious;
    private StyledButton iconNext;
    private String selectedLayoutId;
    private String pressedControlId;
    private String pressedStructurePreviewId;
    private double structurePreviewPressX, structurePreviewPressY;
    private boolean structurePreviewMoved;
    private long lastControlHover;
    private final LinkedHashSet<String> selectedLayoutIds = new LinkedHashSet<>();
    private final Map<String, StyledButton> hierarchyRows = new LinkedHashMap<>();
    private boolean hierarchyOpen;
    private int hierarchyScroll;
    private final SmoothScroll hierarchyMotion = new SmoothScroll();
    private final List<FormLabel> formLabels = new ArrayList<>();
    private final List<ColorPreview> colorPreviews = new ArrayList<>();
    private double canvasZoom = 1.0;
    private double canvasPanX;
    private double canvasTargetPanX;
    private boolean canvasPanEasing;
    private double canvasPanY;
    private boolean middlePanning;
    private long lastMiddleClickNanos;
    private double lastMiddleClickX;
    private double lastMiddleClickY;
    private EditBox coordinateEditor;
    private String coordinateAxis = "";
    private String coordinateElement = "";
    private String lastCoordinateAxis = "";
    private long lastCoordinateClick;
    private final Map<AbstractWidget, Integer> formWidgets = new LinkedHashMap<>();
    private final Map<String, AbstractWidget> formFields = new LinkedHashMap<>();
    private final Set<String> invalidFormFields = new HashSet<>();
    private boolean formScrollbarDragging;
    private QuestTemplate appliedTemplate;
    private LayoutEditorState templateBaseline;
    private String templateHash = "";
    private CompletableFuture<String> templatePending;
    private CompletableFuture<String> typePending;
    private String pendingTypeDelete = "";
    private String movingTask = "", movingTab = "";
    private CompletableFuture<String> orderPending;
    private String pendingCreationParent = "";
    private String initialPropertyKind = "";
    private String initialPropertyId = "";
    private boolean hotEventPropertyOnly;
    private final UiMotion overviewFeatureHover = new UiMotion(false, System.nanoTime());
    private UiMotion detailReveal = new UiMotion(false, System.nanoTime());
    private final UiCompositor detailCompositor = new UiCompositor();
    private QuestTemplate previewTemplate;
    private QuestDefinition previewQuest;
    private Viewport previewViewport;
    private boolean graphInspection;

    public QuestScreen() {
        this(null);
    }

    public QuestScreen(Screen parent) {
        this(parent, false);
    }

    public static QuestScreen resume(Screen parent) {
        String id = ClientPreferences.get().lastSelectedQuestId;
        QuestScreen screen = new QuestScreen(parent, false, id);
        var quest = ClientQuestDefinitions.find(id);
        String category = ClientPreferences.get().lastSelectedQuestCategory;
        if (quest != null && ClientQuestDefinitions.types().stream().anyMatch(type -> type.id().equals(category))
                || quest != null && category.equals("completed")) {
            screen.category = category;
        }
        return screen;
    }

    @Override
    public void resize(net.minecraft.client.Minecraft game, int width, int height) {
        if (!editMode && form == null && !selectedId.isBlank()) revealSelectedPage = true;
        super.resize(game, width, height);
    }

    private QuestScreen(Screen parent, boolean overviewMode) {
        this(parent, overviewMode, "");
    }

    private QuestScreen(Screen parent, boolean overviewMode, String selectedId) {
        super(text(overviewMode ? "overview" : "title"));
        this.parent = parent;
        this.overviewMode = overviewMode;
        this.selectedId = selectedId == null ? "" : selectedId;
        this.requestedInitialTask = this.selectedId;
        this.revealSelectedPage = !this.selectedId.isEmpty();
        this.category = "active";
    }

    public static QuestScreen overview(Screen parent) {
        return new QuestScreen(parent, true);
    }

    public static QuestScreen task(Screen parent, String id) {
        return new QuestScreen(parent, false, id);
    }

    static QuestScreen graphTask(Screen parent, String id) {
        var screen = task(parent, id); screen.graphInspection = true; return screen;
    }

    static QuestScreen templatePreview(QuestTemplate template) {
        var screen = new QuestScreen(null);
        screen.previewTemplate = template;
        screen.layout = new LayoutEditorState();
        return screen;
    }

    static QuestScreen taskControlPreview(QuestDefinition quest) {
        var template = new QuestTemplate("control_preview", quest.title(), quest.description(), quest.type(),
                quest.icon(), "", 1, "", 1, "default", quest.buttonCss(),
                xtdpotato.xero_quest_journal.core.QuestTemplateStore.defaultElements());
        var screen = templatePreview(template);
        screen.previewQuest = quest;
        return screen;
    }

    private void updateTaskControlPreview() {
        clearFrameElements();
        selected = previewQuest;
        selectedId = selected.id();
        layout = new LayoutEditorState();
        layout.ensureCanvasCoordinates(395, 275, 0, 0);
        if (selected.presentation().authored()) {
            new QuestPresentationClient().load(selected, layout);
        } else if (selected.templateId().isBlank()) {
            ensureBuiltInElements();
        } else {
            var template = ClientQuestDefinitions.templates().stream()
                    .filter(t -> t.id().equals(selected.templateId())).findFirst().orElse(previewTemplate);
            TemplateLayout.apply(layout, selectedId, template, layout.gridSize(), 0);
            layout.setTaskCanvas(selectedId, 395, 275);
        }
        for (var element : List.copyOf(layout.elements().values()))
            if (layout.parentOf(element.id()) == null) arrangeParent(element.id());
    }

    List<xtdpotato.xero_quest_journal.core.model.QuestPresentation.Control> previewControls() {
        return QuestPresentationClient.snapshot(selectedId, layout).controls();
    }

    void renderControlSelection(GuiGraphics g, java.util.Set<String> ids) {
        if (previewViewport == null) return;
        g.enableScissor(previewViewport.left(), previewViewport.top(), previewViewport.right(), previewViewport.bottom());
        for (var element : layout.elements().values()) {
            if (!ids.contains(QuestPresentationClient.sourceId(element)) || !runtimeVisible(element)) continue;
            var position = elementDisplayPosition(element);
            g.renderOutline(position[0], position[1], displayWidth(element), displayHeight(element), 0xFFFFE29D);
        }
        g.disableScissor();
    }

    void updateTemplatePreview(QuestTemplate template) {
        clearFrameElements();
        previewTemplate = template;
        selectedId = "template_preview";
        selected = new QuestDefinition(selectedId, template.name(), template.description(), template.type(),
                template.icon(), true, false, 0, null, "", "", template.css(), "",
                List.of(), template.defaultConditions(), template.defaultRewards(), template.rewardMode(), template.id());
        layout = new LayoutEditorState();
        layout.ensureCanvasCoordinates(395, 275, 0, 0);
        layout.setTaskCanvas(selectedId, 395, 275);
        TemplateLayout.apply(layout, selectedId, template, layout.gridSize(), 0);
        for (var control : template.elements()) if (control.parent().isBlank())
            arrangeParent(TemplateLayout.id(selectedId, template.id(), control.id()));
    }

    double templatePreviewMaxScroll(Viewport viewport) {
        previewViewport = viewport;
        detailsLeft = viewport.left();
        return maxDetailScroll();
    }

    void renderTemplatePreview(GuiGraphics g, Viewport viewport, double scroll, int mouseX, int mouseY,
                               String selectedControl) {
        clearFrameElements();
        previewViewport = viewport;
        detailsLeft = viewport.left();
        detailScroll = scroll;
        g.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        renderElements(g, mouseX, mouseY);
        if (selectedControl != null && !selectedControl.isBlank()) {
            var element = layout.elements().get(TemplateLayout.id(selectedId, previewTemplate.id(), selectedControl));
            if (element != null) {
                var position = elementDisplayPosition(element);
                g.renderOutline(position[0], position[1], displayWidth(element), displayHeight(element), 0xFFF2CF83);
            }
        }
        g.disableScissor();
    }

    String templatePreviewHit(double x, double y) {
        if (previewViewport == null || !previewViewport.contains(x, y)) return "";
        for (var element : orderedElements(true)) {
            if (!runtimeVisible(element)) continue;
            int[] position = elementDisplayPosition(element);
            if (x >= position[0] && x < position[0] + displayWidth(element)
                    && y >= position[1] && y < position[1] + displayHeight(element))
                return previewQuest == null ? element.attribute("template_key", "")
                        : QuestPresentationClient.sourceId(element);
        }
        return "";
    }

    static QuestScreen hotEventProperties(Screen parent, String kind, String id) {
        QuestScreen screen = new QuestScreen(parent, true, id);
        screen.editMode = true;
        screen.initialPropertyKind = kind;
        screen.initialPropertyId = id;
        screen.hotEventPropertyOnly = true;
        return screen;
    }

    static QuestScreen legendaryProperties(Screen parent, String id) {
        return hotEventProperties(parent, "task", id);
    }

    public static QuestScreen pickResource(Screen parent, String current, boolean itemOnly,
                                          java.util.function.Consumer<String> changed) {
        return pickResource(parent, current, itemOnly, false, changed);
    }

    public static QuestScreen pickResource(Screen parent, String current, boolean itemOnly, boolean allowTags,
                                          java.util.function.Consumer<String> changed) {
        var screen = new QuestScreen(parent);
        screen.externalPicker = true;
        screen.iconPicker = new IconPicker(current, value -> {
            changed.accept(value);
            net.minecraft.client.Minecraft.getInstance().setScreen(parent);
        }, itemOnly);
        screen.iconPicker.allowTags = itemOnly && allowTags;
        if (screen.iconPicker.allowTags && current != null && current.startsWith("#"))
            screen.iconPicker.source = IconSource.TAGS;
        return screen;
    }

    public static QuestDefinition questById(String id) {
        return ClientQuestDefinitions.find(id);
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }

    public boolean canAuthor() {
        return ClientEditMode.enabled() && isAdmin() && minecraft.getSingleplayerServer() != null;
    }

    private boolean isAdmin() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    @Override
    protected void init() {
        clearFrameElements();
        var savedCollapsedGroups = new HashSet<>(ClientPreferences.get().collapsedJournalGroups);
        if (!collapsedGroupIds.equals(savedCollapsedGroups)) {
            collapsedGroupIds.clear();
            collapsedGroupIds.addAll(savedCollapsedGroups);
            groupSlides.clear();
        }
        if (!pressedClaimId.isBlank()) {
            var pressed = controls.get("quest:" + pressedClaimId);
            if (pressed instanceof JournalListButton row) row.claimPressed(false);
            pressedClaimId = "";
        }
        if (!ClientEditMode.enabled()) editMode = false;
        if (!editMode) movingTask = movingTab = "";
        if (previewTemplate != null) {
            clearWidgets();
            styles = ButtonStyleSheet.load();
            updateTemplatePreview(previewTemplate);
            if (previewQuest != null) updateTaskControlPreview();
            return;
        }
        previousButtons.clear();
        for (var child : children()) {
            if (child instanceof StyledButton button) previousButtons.put(button.styleId(), button);
        }
        clearWidgets();
        iconChoiceButtons.clear();
        iconPrevious = null;
        iconNext = null;
        controls.clear();
        materializedRowKeys.clear();
        materializedRows = List.of();
        cancelTaskReorder();
        hierarchyRows.clear();
        menu.clear();
        menuTarget = null;
        formLabels.clear();
        colorPreviews.clear();
        formWidgets.clear();
        formFields.clear();
        formScrollbarDragging = false;
        canvasPanEasing = false;
        groupControl = null;
        chapterControl = null;
        styles = ButtonStyleSheet.load();
        adminAtInit = isAdmin();
        authorAtInit = canAuthor();
        revision = ClientQuestDefinitions.revision();
        progressRevision = ClientQuestProgress.listRevision();
        // Moving mode has no undo toolbar, but still observes history changes after each move.
        historyRevision = authoringHistory().revision();
        if (!canAuthor()) {
            movingTask = movingTab = "";
            editMode = false;
            systemLayout = false;
            hierarchyOpen = false;
            if (pending == null) {
                form = null;
                iconPicker = null;
            }
        }
        if (!overviewMode) {
            groupId = "";
            chapterId = "";
        }
        if (!categories().contains(category)) category = "active";
        if (!categories().contains(movingTab)) movingTab = "";
        if (!groupId.isEmpty() && ClientQuestDefinitions.groups().stream().noneMatch(g -> g.id().equals(groupId))) groupId = "";
        if (!chapterId.isEmpty() && ClientQuestDefinitions.chapters().stream().noneMatch(c -> c.id().equals(chapterId))) chapterId = "";
        resolveInitialTask();
        rebuildQuestListData();
        if (!initialPropertyKind.isBlank()) {
            String kind = initialPropertyKind;
            String id = initialPropertyId;
            initialPropertyKind = "";
            initialPropertyId = "";
            editProperties(kind, id);
            return;
        }
        listWidth = Math.max(100, Math.min(420, (width - 46) * 36 / 100));
        detailsLeft = 16 + listWidth + 14;
        if (layout.ensureCanvasCoordinates(LayoutEditorState.DEFAULT_TASK_WIDTH,
                LayoutEditorState.DEFAULT_TASK_HEIGHT, detailsLeft, detailsTop())) {
            LayoutStore.save("journal", layout);
        }
        clearFixedControlLayoutState();
        presentationWriter.load(selected, layout);
        controlMotions.keySet().removeIf(id -> !layout.elements().containsKey(id)
                || !layout.elements().get(id).quest().equals(selectedId));
        if (editMode) controlMotions.clear();
        syncSelectedTemplateLayout();
        selectedLayoutIds.removeIf(id -> !layout.elements().containsKey(id)
                || !layout.elements().get(id).quest().equals(selectedId));
        if (selectedLayoutId != null && !selectedLayoutIds.contains(selectedLayoutId)) {
            selectedLayoutId = selectedLayoutIds.stream().findFirst().orElse(null);
        }
        if (selected != null) {
            ensureBuiltInElements();
        }
        updateBuiltInAnchors();
        clampDetailScroll();
        if (!editMode || form != null || iconPicker != null
                || !coordinateAxis.equals("zoom") && !java.util.Objects.equals(coordinateElement, selectedLayoutId)) {
            finishCoordinateEdit(false);
        }
        if (iconPicker != null) {
            initIconPicker();
            return;
        }
        if (form != null) {
            initForm();
            prepareFormViewport();
            return;
        }

        if (overviewMode && !editMode) {
            initOverviewPresentation();
            if (ClientStructures.inScene()) initSceneActions();
            return;
        }
        initHeader();
        if (ClientStructures.inScene()) initSceneActions();
        if (overviewMode && !editMode) initScopeControls();
        initTaskSearch();
        searchDirty = false;
        clampListScroll();
        int rowWidth = listWidth - (maxListScroll() > 0 ? 8 : 0);
        buildListRows(rowWidth);
        listScrollFrameNanos = System.nanoTime();
        updateListButtons();
        revealTaskRow();
        if (!revealGroupRow.isBlank()) {
            String key = "group:" + revealGroupRow;
            journalList.rows().stream().filter(row -> row.key().equals(key)).findFirst().ifPresent(row -> {
                listScroll = listTargetScroll = Math.clamp(row.offset(), 0, maxListScroll());
            });
            revealGroupRow = "";
        }
        updateListButtons();
        initListPager();
        if (canAuthor()) {
            nav("edit", editMode ? "preview" : "edit", width - 62, headerControlY(), 22, 24, JournalNavButton.Kind.ICON,
                    editMode ? "preview" : "edit", overviewMode ? this::overviewEditMenu : () -> {
                        editMode = !editMode;
                        systemLayout = false;
                        error = null;
                        init();
                    });
        }
        if (ClientEditMode.enabled() && isAdmin()) {
            nav("settings", "config.title", width - 90, headerControlY(), 22, 24, JournalNavButton.Kind.ICON, "settings",
                    () -> minecraft.setScreen(new QuestConfigScreen(this)));
        }
        nav("close", "close", width - 34, headerControlY(), 22, 24, JournalNavButton.Kind.ICON, "close", this::onClose);
        if (hasClaimableTasks()) {
            var claimAll = nav("claim_all", "claim_all", width - 118, headerControlY(), 22, 24,
                    JournalNavButton.Kind.ICON, "gift", this::runClaimAll);
            claimAll.active = actionPending == null;
        }
        if (!editMode && !overviewMode) {
            var readAll = nav("mark-all-read", "task.mark_all_read", width - 146, headerControlY(), 22, 24,
                    JournalNavButton.Kind.ICON, "mail_check", this::markAllTasksRead);
            readAll.active = readAllPending == null && (ClientQuestProgress.hasUnreadTasks() || QuestUnlockToast.isPresenting());
        }
        if (!editMode) {
            nav("overview", overviewMode ? "back" : "overview", 16, height - 34, 136, 24,
                    JournalNavButton.Kind.ACTION, overviewMode ? "back" : "overview",
                    overviewMode ? this::onClose : () -> minecraft.setScreen(new QuestScreen(this, true)))
                    .extraCss(".journal-action { background-color:#f0eadc; color:#50586d; border-width:0px; }");
        }
        StyledButton track = new MailboxActionButton(width - 136, height - 34, 120, 24,
                text(selected != null && ClientQuestProgress.isTracked(selected.id())
                        && ClientQuestProgress.focusGroupId().isBlank() ? "untrack"
                        : selected != null && ClientQuestProgress.isTracked(selected.id()) ? "tracked" : "track"),
                ignored -> toggleTrack(), styles, "track",
                JournalNavButton.Kind.ACTION, "track", "claim_all").compactContent();
        track.active = selected != null && (selected.trackable() || ClientQuestProgress.isTracked(selected.id()));
        var selectedView = selected == null ? null : ClientQuestProgress.view(selected.id());
        track.active &= actionPending == null && selectedView != null
                && ClientQuestProgress.focusCanSelect(selected.id())
                && (!selectedView.completed() && selectedView.unlocked() || ClientQuestProgress.isTracked(selected.id()));
        track.extraCss("""
                .journal-action { background-color:#ece6da; border-width:0px; color:#50586d; opacity:1; }
                .journal-action:hover { background-color:#fff1da; }
                """ + MailboxAnimationStore.animation("claim_all_hover").css + "\n"
                + MailboxAnimationStore.animation("claim_all_click").css);
        initFocusControl();
        if (overviewMode) {
            JournalNavButton hotEvents = nav("hot-events", "hot_events.title", width - 164, height - 34, 24, 24,
                    JournalNavButton.Kind.ICON, "recommended",
                    () -> minecraft.setScreen(new HotEventsScreen(this)));
            hotEvents.setTooltip(Tooltip.create(text("hot_events.title")));
        } else if (editMode || selected != null && (selectedView == null || !selectedView.completed()
                && (!(selectedView.status().equals("active") && manualCondition(QuestText.activeConditions(selected)))
                || dualFooter()))) {
            // Like the reference journal, an empty list shows no "track" action at all; submit/confirm
            // tasks keep "track" at the far right with the action button beside it when there is room.
            register("system:track", track);
        }
        if (!editMode && !overviewMode && selected != null && JourneyScreen.containsQuest(selected.id())) {
            int actionX = width - 136 - (dualFooter() ? 128 : 0);
            var journey = new MailboxActionButton(actionX - 128, height - 34, 120, 24,
                    text("journey.open"), ignored -> minecraft.setScreen(new JourneyScreen(this, selected.id())),
                    styles, "open-journey", JournalNavButton.Kind.ACTION, "guide", "claim_all")
                    .compactContent();
            journey.setTooltip(Tooltip.create(text("journey.open")));
            journey.active = actionPending == null && !editMode;
            register("system:open-journey", journey);
        }
        if (!editMode && !overviewMode && selected != null && selectedView != null && selectedView.status().equals("active")
                && manualCondition(QuestText.activeConditions(selected))) {
            StyledButton complete = new MailboxActionButton(width - 136 - (dualFooter() ? 128 : 0), height - 34, 120, 24,
                    text(containsCondition(QuestText.activeConditions(selected), "item_submit") ? "submit" : "confirm"),
                    ignored -> runAction(selected.id(), false), styles, "complete",
                    JournalNavButton.Kind.ACTION, "track", "claim_all").compactContent();
            complete.active = actionPending == null && selectedView.triggersEnabled()
                    && ClientQuestProgress.focusAllows(selected.id());
            register("system:complete", complete);
        } else if (!editMode && !overviewMode && selected != null && selectedView != null && selectedView.status().equals("complete")) {
            StyledButton claim = new MailboxActionButton(width - 136, height - 34, 120, 24,
                    text("claim"), ignored -> runAction(selected.id(), true), styles, "claim",
                    JournalNavButton.Kind.ACTION, "gift", "claim_all").compactContent();
            claim.active = actionPending == null && ClientQuestProgress.focusRewardAllows(selected.id());
            register("system:claim", claim);
        }
        if (!editMode && !overviewMode && selected != null && ClientQuestProgress.isDailyToday(selected.id())
                && ClientQuestProgress.dailyBonusReady() && rewardFooterHeight() > 0) {
            Component label = text("daily.claim");
            int w = Math.min(Math.max(60, width / 3), font.width(label) + 20);
            StyledButton bonus = new StyledButton(width - 24 - w, detailContentBottom() + 5, w, 14, label,
                    ignored -> claimDailyBonus(), styles, "daily-bonus", "toolbar-button");
            bonus.extraCss("button#daily-bonus { background-color:#f2cf83; color:#3d3322; border-width:0px; }\n"
                    + "button#daily-bonus:hover { background-color:#ffe2a6; color:#2a2318; }");
            bonus.active = actionPending == null;
            register("system:daily-bonus", bonus);
        }
        if (editMode && (!movingTask.isBlank() || !movingTab.isBlank())) {
            initOrderControls();
        } else if (editMode) {
            var history = authoringHistory();
            historyRevision = history.revision();
            var undoButton = action("undo", "", "undo", 16, height - 28, 24, () -> undoAuthoring(false));
            var redoButton = action("redo", "", "redo", 44, height - 28, 24, () -> undoAuthoring(true));
            boolean local = systemLayout || !canAuthor();
            boolean available = historyPending == null && pending == null && batchPending == null
                    && orderPending == null && templatePending == null;
            undoButton.active = available
                    && (local ? !undo.isEmpty() : history.undoCount() > 0 || presentationWriter.busy());
            redoButton.active = available
                    && (local ? !redo.isEmpty() : history.redoCount() > 0);
            if (!local) {
                undoButton.setTooltip(Tooltip.create(historyTooltip(false, history)));
                redoButton.setTooltip(Tooltip.create(historyTooltip(true, history)));
            }
            action("hierarchy", "", hierarchyOpen ? "hierarchy.hide" : "hierarchy.show",
                    72, height - 28, 24, () -> {
                hierarchyOpen = !hierarchyOpen;
                init();
            }).active = selected != null;
            action("source", "", "source", 100, height - 28, 24, this::openSourceEditor).active = selected != null;
            addEditorViewToggle("editor-grid", "grid", 156, showEditorGrid,
                    () -> { showEditorGrid = !showEditorGrid; init(); });
            addEditorViewToggle("editor-axes", "fit", 184, showEditorAxes,
                    () -> { showEditorAxes = !showEditorAxes; init(); });
            addEditorViewToggle("editor-bounds", "expand", 212, showEditorBounds,
                    () -> { showEditorBounds = !showEditorBounds; init(); });
            StyledButton unlockGraph = new EditorIconButton(128, height - 28,
                    text("unlock_graph.title"), () -> minecraft.setScreen(new QuestUnlockGraphScreen(this)),
                    styles, "unlock_graph.title", "visual");
            register("system:unlock_graph.title", unlockGraph);
            unlockGraph.active = isAdmin() && minecraft.getSingleplayerServer() != null;
            if (hierarchyOpen && selected != null) {
                initHierarchyPanel();
            }
        }
    }

    private boolean hasClaimableTasks() {
        return claimableRewards;
    }

    private void refreshClaimableRewardState() {
        claimableRewards = !editMode && !overviewMode && ClientQuestDefinitions.all().stream().anyMatch(quest -> {
            var view = ClientQuestProgress.view(quest.id());
            return view != null && view.status().equals("complete")
                    && ClientQuestProgress.focusRewardAllows(quest.id());
        });
    }

    private void addEditorViewToggle(String id, String icon, int x, boolean enabled, Runnable action) {
        EditorIconButton button = new EditorIconButton(x, height - 28,
                text("editor_view." + icon + (enabled ? ".on" : ".off")), action, styles, id, icon);
        button.active = true;
        register("system:" + id, button);
    }

    private boolean showFocusControl() {
        String focusGroup = ClientQuestProgress.focusGroupId();
        String selectedGroup = groupId.isBlank() && selected != null
                ? ClientQuestDefinitions.groupOf(selected.id()) : groupId;
        return !editMode && !overviewMode && (!focusGroup.isBlank()
                || !selectedGroup.isBlank() && ClientQuestDefinitions.groups().stream().anyMatch(group ->
                group.id().equals(selectedGroup) && group.focusAvailable()));
    }

    private boolean stackedFocusControl() {
        return showFocusControl() && width < 152 + footerActionsWidth() + font.width(text("focus")) + 56;
    }

    /** Submit/confirm sits left of "track" (which stays rightmost) when the footer is wide enough. */
    private boolean dualFooter() {
        if (editMode || overviewMode || selected == null || width < 152 + 8 + 248 + 16) return false;
        var view = ClientQuestProgress.view(selected.id());
        return view != null && view.status().equals("active") && manualCondition(QuestText.activeConditions(selected));
    }

    /** Width taken by the right-hand footer actions (one button, or action + track). */
    private int footerActionsWidth() {
        return dualFooter() ? 248 : 120;
    }

    private void initFocusControl() {
        if (!showFocusControl()) return;
        String focusedGroup = ClientQuestProgress.focusGroupId();
        String selectedGroup = groupId.isBlank() && selected != null
                ? ClientQuestDefinitions.groupOf(selected.id()) : groupId;
        String target = focusedGroup.isBlank() ? selectedGroup : focusedGroup;
        if (target.isBlank()) return;
        int w = Math.min(width - 32, font.width(text("focus")) + 44);
        int x = stackedFocusControl() ? width - 16 - w : width - 24 - footerActionsWidth() - w;
        int y = height - (stackedFocusControl() ? 60 : 34);
        var button = new QuestFocusButton(x, y, w, text("focus"), !focusedGroup.isBlank(), ignored -> {
            if (actionPending != null) return;
            actionMessage = null;
            actionPending = ClientQuestProgress.toggleFocus(target);
            init();
        }, styles);
        boolean available = ClientQuestDefinitions.groups().stream()
                .anyMatch(group -> group.id().equals(target) && group.focusAvailable())
                && ClientQuestDefinitions.all().stream()
                .filter(quest -> ClientQuestDefinitions.groupOf(quest.id()).equals(target))
                .anyMatch(quest -> {
                    var view = ClientQuestProgress.view(quest.id());
                    return quest.trackable() && view != null && view.unlocked() && !view.completed()
                            && !view.hidden() && view.triggersEnabled();
                });
        button.active = actionPending == null && (!focusedGroup.isBlank() || available);
        button.setTooltip(Tooltip.create(text(focusedGroup.isBlank() ? "focus.start_hint" : "focus.stop_hint")));
        register("system:focus", button);
    }

    private void rebuildQuestListData() {
        List<xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group> groups =
                ClientQuestDefinitions.groups();
        List<xtdpotato.xero_quest_journal.core.network.QuestCatalog.Chapter> chapters =
                ClientQuestDefinitions.chapters();
        if (!pendingCompletedSelectionId.isBlank()) {
            var claimed = ClientQuestProgress.view(pendingCompletedSelectionId);
            if (claimed != null && claimed.status().equals("claimed")) {
                selectedId = pendingCompletedSelectionId;
                pendingCompletedSelectionId = "";
                category = "completed";
                groupId = chapterId = searchQuery = "";
                listPage = 0;
                resetListScroll();
                revealSelectedPage = true;
                revealTaskRow = selectedId;
            } else if (claimed == null || !claimed.status().equals("complete")) {
                pendingCompletedSelectionId = "";
            }
        }
        advanceFromCompletedTask();
        var previouslySelectedView = selectedId.isBlank() ? null : ClientQuestProgress.view(selectedId);
        if (!editMode && previouslySelectedView != null && previouslySelectedView.status().equals("claimed")
                && !category.equals("completed")) category = "completed";
        var allTasks = ClientQuestDefinitions.all();
        journalSearch.queryChanged(searchQuery);
        if (!searchQuery.isBlank()) journalSearch.synchronize(allTasks, groups, chapters);
        Set<String> chapterTasks = chapterId.isEmpty() ? Set.of() : chapters.stream()
                .filter(chapter -> chapter.id().equals(chapterId)).findFirst()
                .map(chapter -> Set.copyOf(chapter.quests())).orElse(Set.of());
        List<QuestDefinition> scopedQuests = allTasks.stream()
                .filter(quest -> editMode || graphInspection && quest.id().equals(requestedInitialTask) || !lockedGeneratedTask(quest))
                .filter(quest -> graphInspection && quest.id().equals(requestedInitialTask) || editMode || category.equals("active") || category.equals("completed")
                        || ClientQuestProgress.view(quest.id()) == null
                        || !ClientQuestProgress.view(quest.id()).hidden())
                .filter(quest -> editMode || !category.equals("active")
                        || ClientQuestProgress.view(quest.id()) == null
                        || !ClientQuestProgress.view(quest.id()).status().equals("claimed"))
                .filter(quest -> graphInspection && quest.id().equals(requestedInitialTask) || overviewMode || editMode && category.equals("active")
                        || matchesCategory(quest, category))
                .filter(quest -> !chapterId.isEmpty() || groupId.isEmpty()
                        || ClientQuestDefinitions.groupOf(quest.id()).equals(groupId))
                .filter(quest -> chapterId.isEmpty() || chapterTasks.contains(quest.id()))
                .toList();
        List<QuestDefinition> baseQuests = scopedQuests.stream()
                .filter(quest -> {
                    if (graphInspection && quest.id().equals(requestedInitialTask)) return true;
                    var filters = ClientPreferences.get().questSearchFilters;
                    Set<String> groupsForFilter = QuestSearchFilters.selected(filters, "group");
                    return (groupsForFilter.isEmpty()
                            || groupsForFilter.contains(ClientQuestDefinitions.groupOf(quest.id())))
                            && QuestSearchFilters.matchesContent(quest, filters)
                            && QuestSearchFilters.matchesStatus(quest, filters);
                })
                .toList();
        searchableTasks = !baseQuests.isEmpty();
        if (searchQuery.isBlank()) {
            quests = baseQuests;
        } else {
            quests = baseQuests.stream()
                    .filter(quest -> journalSearch.matchesTask(quest.id()))
                    .toList();
        }
        if (category.equals("active")) {
            // Categorize before pagination so later pages cannot restart the built-in category order.
            quests = JournalListModel.categorized(quests, groups, ClientQuestDefinitions::groupOf,
                    Set.of(), false, categories()).rows().stream().map(JournalListModel.Row::quest)
                    .filter(Objects::nonNull).toList();
        } else {
            quests = JournalListModel.build(quests, groups, ClientQuestDefinitions::groupOf,
                    Set.of(), false).rows().stream().map(JournalListModel.Row::quest)
                    .filter(Objects::nonNull).toList();
        }
        boolean paginationEnabled = ClientQuestProgress.taskPagination();
        listPages = paginationEnabled
                ? Math.max(1, (quests.size() + tasksPerPage() - 1) / tasksPerPage()) : 1;
        if (revealSelectedPage) {
            int selectedIndex = questIndex(selectedId);
            if (selectedIndex >= 0) {
                listPage = listPages > 1 ? selectedIndex / tasksPerPage() : 0;
                if (!requestedInitialTask.isBlank()) expandGroup(ClientQuestDefinitions.groupOf(selectedId));
                if (!requestedInitialTask.isBlank()) revealTaskRow = selectedId;
                revealSelectedPage = false;
            }
        }
        listPage = Math.clamp(listPage, 0, listPages - 1);
        int pageStart = listPages > 1 ? Math.min(quests.size(), listPage * tasksPerPage()) : 0;
        List<QuestDefinition> pageQuests = listPages > 1
                ? quests.subList(pageStart, Math.min(quests.size(), pageStart + tasksPerPage())) : quests;
        Set<String> matchingGroups = new HashSet<>();
        quests.forEach(quest -> matchingGroups.add(ClientQuestDefinitions.groupOf(quest.id())));
        Set<String> groupIds = new HashSet<>();
        groups.forEach(group -> groupIds.add(group.id()));
        List<xtdpotato.xero_quest_journal.core.network.QuestCatalog.Group> visibleGroups = groups.stream()
                .filter(group -> !chapterId.isEmpty() || groupId.isEmpty() || group.id().equals(groupId))
                .filter(group -> editMode && (category.equals("active") || group.type().equals(category))
                        || matchingGroups.contains(group.id()))
                .filter(group -> searchQuery.isBlank()
                        || journalSearch.matchesGroup(group.id()) || matchingGroups.contains(group.id()))
                .toList();
        selectedTaskIds.removeIf(id -> ClientQuestDefinitions.find(id) == null);
        selectedGroupIds.removeIf(id -> !groupIds.contains(id));
        groupSlides.keySet().removeIf(id -> !groupIds.contains(id));
        var visualCollapsed = new HashSet<>(collapsedGroupIds);
        groupSlides.forEach((id, motion) -> {
            if (motion.position() > 0) visualCollapsed.remove(id);
        });
        groupSlideSource = category.equals("active")
                ? JournalListModel.categorized(pageQuests, visibleGroups, ClientQuestDefinitions::groupOf,
                        visualCollapsed, editMode && chapterId.isEmpty() && listPage == 0, categories(),
                        this::questRowHeight)
                : JournalListModel.build(pageQuests, visibleGroups, ClientQuestDefinitions::groupOf,
                        visualCollapsed, editMode && chapterId.isEmpty() && listPage == 0, this::questRowHeight)
                        .withSection(category);
        updateGroupSlideLayout();
        boolean waitingForRequestedTask = !initialTaskResolved && !requestedInitialTask.isBlank();
        selected = pageQuests.stream().filter(quest -> quest.id().equals(selectedId)).findFirst()
                .orElseGet(() -> waitingForRequestedTask ? null : journalList.rows().stream()
                        .map(JournalListModel.Row::quest).filter(Objects::nonNull).findFirst().orElse(null));
        selectedId = selected == null
                ? waitingForRequestedTask ? requestedInitialTask : ""
                : selected.id();
        refreshClaimableRewardState();
        if (!movingTask.isBlank() && quests.stream().noneMatch(q -> q.id().equals(movingTask))) movingTask = "";
    }

    private boolean lockedGeneratedTask(QuestDefinition quest) {
        if (!quest.automaticallyGenerated()) return false;
        var view = ClientQuestProgress.view(quest.id());
        return view != null && !view.unlocked();
    }

    /**
     * Completion snapshots arrive after the server has already advanced tracking.
     * Move the detail pane with that transition instead of leaving the completed
     * task selected for the duration of its completion toast.
     */
    private void advanceFromCompletedTask() {
        if (editMode || overviewMode || selectedId.isBlank()) return;
        // Keep completed selections, including the task just revealed after claiming its reward.
        var selectedView = ClientQuestProgress.view(selectedId);
        if (selectedView != null && selectedView.completed()) return;
        boolean justCompleted = ClientQuestProgress.completionNotices().stream()
                .anyMatch(notice -> notice.questId().equals(selectedId));
        if (!justCompleted) return;

        String completedId = selectedId;
        String next = ClientQuestProgress.trackedIds().stream()
                .filter(id -> {
                    var view = ClientQuestProgress.view(id);
                    var quest = ClientQuestDefinitions.find(id);
                    return quest != null && view != null && !view.completed() && !view.hidden()
                            && view.unlocked() && quest.trackable();
                })
                .findFirst().orElse("");
        if (next.isBlank()) {
            next = ClientQuestDefinitions.all().stream()
                    .filter(quest -> quest.parents().contains(completedId))
                    .filter(this::availableForAutoSelection)
                    .map(QuestDefinition::id)
                    .findFirst().orElse("");
        }
        if (next.isBlank()) {
            next = ClientQuestDefinitions.all().stream()
                    .filter(this::availableForAutoSelection)
                    .map(QuestDefinition::id)
                    .findFirst().orElse("");
        }

        category = "active";
        groupId = "";
        chapterId = "";
        searchQuery = "";
        listPage = 0;
        resetListScroll();
        revealSelectedPage = !next.isBlank();
        revealTaskRow = next;
        selectedTaskIds.clear();
        selectedLayoutIds.clear();
        selectedLayoutId = null;
        selectedId = next;
        selected = next.isBlank() ? null : ClientQuestDefinitions.find(next);
    }

    private int questRowHeight(QuestDefinition quest) {
        return !editMode && !rowSubtitle(quest).isBlank() ? JournalListModel.TALL_ROW : JournalListModel.QUEST_ROW;
    }

    /** Second row line, like Genshin's list: distance, authored subtitle, or the current step. */
    private String rowSubtitle(QuestDefinition quest) {
        var view = ClientQuestProgress.view(quest.id());
        if (view != null && view.completed()) return quest.subtitle();
        String distance = rowDistance(quest);
        String authored = quest.subtitle();
        if (!authored.isBlank()) return distance.isBlank() ? authored : authored + "  ·  " + distance;
        if (!distance.isBlank()) return distance;
        if (ClientQuestProgress.isDailyToday(quest.id())) return text("daily.row").getString();
        if (!quest.steps().isEmpty()) return QuestText.objective(quest).getString();
        return "";
    }

    private String rowDistance(QuestDefinition quest) {
        var location = QuestText.location(quest);
        if (location == null || minecraft == null || minecraft.player == null || minecraft.level == null) return "";
        if (!location.dimension().isBlank()
                && !location.dimension().equals(minecraft.level.dimension().location().toString()))
            return QuestText.dimensionName(location.dimension()).getString();
        if (!location.resolved()) return QuestText.placeName(location).getString();
        double dx = location.x() - minecraft.player.getX();
        double dy = location.y() - minecraft.player.getY();
        double dz = location.z() - minecraft.player.getZ();
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
    }

    private boolean availableForAutoSelection(QuestDefinition quest) {
        var view = ClientQuestProgress.view(quest.id());
        return quest.trackable() && view != null && view.unlocked() && !view.completed()
                && !view.hidden() && view.triggersEnabled();
    }

    private void buildListRows(int rowWidth) {
        viewportRows = visibleJournalRows();
        for (var row : viewportRows) {
            if (row.section()) continue;
            if (controls.containsKey(row.key())) {
                materializedRowKeys.add(row.key());
                continue;
            }
            StyledButton button;
            if (row.group() != null) {
                var group = row.group();
                boolean expanded = !collapsedGroupIds.contains(group.id());
                boolean tracked = ClientQuestProgress.trackedIds().stream()
                        .anyMatch(id -> ClientQuestDefinitions.groupOf(id).equals(group.id()));
                button = new JournalListButton(listLeft(), listRowY(row), rowWidth, row.height(),
                        Component.literal(group.name()), ignored -> toggleGroup(group.id()), styles,
                        "group-" + group.id(), group.leadingIcon(), group.trailingIcon(),
                        group.description(), true, expanded, false, tracked, "",
                        editMode ? () -> groupAddMenu(group.id()) : null)
                        .accent(groupAccent(group));
                Component tooltip = Component.literal(group.name());
                if (!group.description().isBlank()) tooltip = tooltip.copy().append("\n").append(group.description());
                button.setTooltip(Tooltip.create(editMode ? tooltip.copy().append("\n")
                        .append(text(expanded ? "collapse_group" : "expand_group")) : tooltip));
            } else {
                var quest = row.quest();
                var view = ClientQuestProgress.view(quest.id());
                boolean completed = !editMode && view != null && view.status().equals("claimed");
                boolean claimable = !editMode && view != null && view.status().equals("complete");
                boolean tracked = ClientQuestProgress.isTracked(quest.id());
                String status = editMode ? "active" : view == null ? "" : tracked ? "tracked" : view.status();
                button = new JournalListButton(listLeft() + row.indent(), listRowY(row),
                        rowWidth - row.indent(), row.height(), QuestText.title(quest),
                        ignored -> selectQuest(quest), styles,
                        "quest-" + quest.id(), quest.leadingIcon(), quest.trailingIcon(),
                        "", false, false, completed, tracked, status,
                        editMode ? () -> openTaskInGroup(ClientQuestDefinitions.groupOf(quest.id()), quest.id()) : null)
                        .accent(taskAccent(quest))
                        .newTask(!editMode && view != null && view.newTask())
                        .claimable(claimable)
                        .trackingType(quest.type())
                        .locked(editMode && view != null && !view.unlocked())
                        .subtitle(editMode ? null : () -> rowSubtitle(quest))
                        .extraCss(quest.buttonCss());
                Component tooltip = QuestText.tooltip(quest);
                if (claimable) tooltip = tooltip.copy().append("\n").append(text("claim"));
                if (view != null && view.newTask())
                    tooltip = tooltip.copy().append("\n").append(text("task.new"));
                button.setTooltip(Tooltip.create(tooltip));
            }
            button.clipTo(listLeft(), listTop(), listRight(), listBottom());
            register(row.key(), button);
            materializedRowKeys.add(row.key());
        }
        materializedRows = viewportRows;
    }

    private List<JournalListModel.Row> visibleJournalRows() {
        int pixel = (int) Math.floor(listScroll), viewportHeight = listHeight();
        if (queriedJournal == journalList && queriedScrollPixel == pixel && queriedListHeight == viewportHeight)
            return queriedRows;
        queriedJournal = journalList;
        queriedScrollPixel = pixel;
        queriedListHeight = viewportHeight;
        int overscan = (JournalListModel.TALL_ROW + 4) * LIST_OVERSCAN_ROWS;
        double top = pixel - overscan, bottom = pixel + viewportHeight + overscan;
        // Sliding children temporarily overlap their header and cease to be sorted.
        if (groupSlides.values().stream().anyMatch(motion -> !motion.settled()))
            return queriedRows = journalList.rows().stream()
                    .filter(row -> row.offset() + row.height() > top && row.offset() < bottom).toList();
        return queriedRows = journalList.visibleRows(top, bottom);
    }

    private void removeListRows() {
        for (String id : List.copyOf(controls.keySet())) {
            if (isListRow(id) || id.equals("system:list-page-previous")
                    || id.equals("system:list-page-next")) {
                StyledButton button = controls.remove(id);
                if (button != null) removeWidget(button);
            }
        }
        materializedRowKeys.clear();
        materializedRows = List.of();
    }

    private String groupAccent(QuestCatalog.Group group) {
        String type = group.type().equals("active") ? quests.stream()
                .filter(q -> ClientQuestDefinitions.groupOf(q.id()).equals(group.id()))
                .map(QuestDefinition::type).findFirst().orElse("active") : group.type();
        return xtdpotato.xero_quest_journal.core.model.QuestListAccent.resolve(
                group.listAccent(), "auto", type, true);
    }

    private String taskAccent(QuestDefinition quest) {
        String groupId = ClientQuestDefinitions.groupOf(quest.id());
        String groupStyle = ClientQuestDefinitions.groups().stream().filter(g -> g.id().equals(groupId))
                .map(QuestCatalog.Group::listAccent).findFirst().orElse("auto");
        return xtdpotato.xero_quest_journal.core.model.QuestListAccent.resolve(
                quest.listAccent(), groupStyle, quest.type(), !groupId.isBlank());
    }

    private void initTaskSearch() {
        taskSearch = null;
        if (!editMode && !searchableTasks) return;
        int y = searchY();
        int width = Math.max(80, listWidth - (overviewMode && editMode ? 26 : 0));
        int settingsWidth = 17;
        int settingsHeight = 15;
        int settingsX = listLeft() + width - settingsWidth;
        int fieldWidth = Math.max(24, settingsX - 4 - (listLeft() + 24));
        taskSearch = new EditBox(font, listLeft() + 24, y + 6, fieldWidth, 10,
                text("quest_search"));
        taskSearch.setBordered(false);
        taskSearch.setValue(searchQuery);
        taskSearch.setHint(Component.literal(font.plainSubstrByWidth(text("quest_search").getString(),
                Math.max(1, taskSearch.getWidth() - 4))));
        taskSearch.setResponder(value -> {
            searchQuery = value;
            journalSearch.queryChanged(value);
            listPage = 0;
            resetListScroll();
            searchDirty = true;
        });
        addRenderableWidget(taskSearch);
        var settings = new JournalNavButton(settingsX, y + 2, settingsWidth, settingsHeight, text("search_settings"),
                ignored -> minecraft.setScreen(new QuestSearchSettingsScreen(this)), styles,
                "search-settings", JournalNavButton.Kind.ICON, "search_filter").toolIconScale(.75F);
        settings.setTooltip(Tooltip.create(text("search_settings")));
        addRenderableWidget(settings);
        if (overviewMode && editMode) {
            action("add-task", "+", "add_task", listRight() - 22, y, 22,
                    this::globalAddMenu);
        }
    }

    void searchConfigurationChanged() {
        listPage = 0;
        resetListScroll();
        init();
    }

    private int searchY() {
        return overviewMode && !editMode ? 78 : headerHeight() + 2;
    }

    private void refreshSearchResults() {
        if (form != null || iconPicker != null || externalPicker) return;
        journalSearch.queryChanged(searchQuery);
        if (!searchQuery.isBlank())
            journalSearch.synchronize(ClientQuestDefinitions.all(), ClientQuestDefinitions.groups(),
                    ClientQuestDefinitions.chapters());
        boolean ready = journalSearch.advance();
        boolean cleared = searchDirty && searchQuery.isBlank();
        searchDirty = false;
        if (!ready && !cleared) return;
        rebuildQuestListData();
        removeListRows();
        clampListScroll();
        buildListRows(listWidth - (maxListScroll() > 0 ? 8 : 0));
        updateListButtons();
        initListPager();
    }

    private JournalNavButton nav(String id, String label, int x, int y, int w, int h,
                                  JournalNavButton.Kind kind, String icon, Runnable command) {
        var button = kind == JournalNavButton.Kind.ACTION
                ? new MailboxActionButton(x, y, w, h, text(label), ignored -> command.run(), styles, id, kind, icon, "claim_all")
                : new JournalNavButton(x, y, w, h, text(label), ignored -> command.run(), styles, id, kind, icon);
        button.setTooltip(editMode || kind == JournalNavButton.Kind.ICON ? Tooltip.create(text(label)) : null);
        register("system:" + id, button);
        return button;
    }

    private void initScopeControls() {
        int column = (width - 44) / 2;
        List<String> groupOptions = new ArrayList<>(List.of(""));
        ClientQuestDefinitions.groups().forEach(group -> groupOptions.add(group.id()));
        groupControl = new StyledChoiceButton<>(16, 52, column - (editMode ? 26 : 0), 20,
                text("groups"), groupOptions, groupId, id -> Component.literal(id.isEmpty()
                        ? text("all").getString() : ClientQuestDefinitions.groups().stream()
                        .filter(group -> group.id().equals(id)).findFirst().orElseThrow().name()), value -> {
                    groupId = value;
                    chapterId = "";
                    listPage = 0;
                    resetListScroll();
                    init();
                }, styles, "groups");
        register("system:groups", groupControl);
        groupControl.setTooltip(null);
        List<String> chapterOptions = new ArrayList<>(List.of(""));
        ClientQuestDefinitions.chapters().stream().filter(chapter -> groupId.isEmpty() || chapter.groupId().equals(groupId))
                .forEach(chapter -> chapterOptions.add(chapter.id()));
        chapterControl = new StyledChoiceButton<>(28 + column, 52, column - (editMode ? 26 : 0), 20,
                text("chapters"), chapterOptions, chapterId, id -> Component.literal(id.isEmpty()
                        ? text("all").getString() : ClientQuestDefinitions.chapters().stream()
                .filter(chapter -> chapter.id().equals(id)).findFirst().orElseThrow().name()), value -> {
                    chapterId = value;
                    listPage = 0;
                    resetListScroll();
                    init();
                }, styles, "chapters");
        register("system:chapters", chapterControl);
        chapterControl.setTooltip(null);
        if (editMode) {
            action("add-group", "+", "add_group", 16 + column - 22, 52, 22,
                    () -> openForm("group", "normal"));
            action("add-chapter", "+", "add_chapter", 28 + column - 22 + column, 52, 22,
                    () -> openForm("chapter", "normal"));
            action("add-task", "+", "add_task", 16 + listWidth - 22, 78, 22,
                    () -> openTaskInGroup(groupId));
        }
    }

    private void initHeader() {
        if (overviewMode) return;
        List<String> categories = categories();
        int count = categories.size() + (editMode ? 1 : 0);
        clampCategoryScroll(count);
        if (revealCategory || previousCategoryViewportWidth != categoryViewportWidth()) {
            ensureCategoryVisible(categories.indexOf(category), count);
            revealCategory = false;
        }
        previousCategoryViewportWidth = categoryViewportWidth();
        int y = categoryY();
        for (int i = 0; i < categories.size(); i++) {
            String value = categories.get(i);
            Component label = categoryLabel(value);
            var tab = new JournalNavButton(categoryButtonX(i, count), y, categoryTabWidth(), categoryHeight(),
                    label, ignored -> setCategory(value), styles, "category-" + value,
                    JournalNavButton.Kind.TAB, categoryIcon(value));
            tab.setTooltip(editMode ? Tooltip.create(label) : null);
            tab.setSelectionHighlighted(category.equals(value));
            if (category.equals(value) && categoryTransitionTarget.equals(value)) {
                tab.animateSelection(categoryTransitionStart);
            }
            tab.clipTo(categoryViewportLeft(), y, categoryViewportRight(), y + categoryHeight());
            tab.visible = tab.getRight() > categoryViewportLeft() && tab.getX() < categoryViewportRight();
            register("system:category-" + value, tab);
        }
        if (editMode) {
            int x = categoryButtonX(categories.size(), count);
            var add = new JournalNavButton(x, y, categoryTabWidth(), categoryHeight(), text("quest_types.add"),
                    ignored -> minecraft.setScreen(new QuestTypeScreen(this)), styles, "category-add",
                    JournalNavButton.Kind.TAB, "add");
            add.setTooltip(editMode ? Tooltip.create(text("quest_types.add")) : null);
            add.clipTo(categoryViewportLeft(), y, categoryViewportRight(), y + categoryHeight());
            add.visible = add.getRight() > categoryViewportLeft() && add.getX() < categoryViewportRight();
            register("system:category-add", add);
        }
        int keyHeight = (categoryHeight() - 2) / 2;
        int keyY = y + (categoryHeight() - keyHeight) / 2;
        var previous = new JournalNavButton(categoryViewportLeft() - categoryKeyWidth() - 6, keyY,
                categoryKeyWidth(), keyHeight, Component.literal("Q"),
                ignored -> cycleCategory(-1), styles, "category-previous", JournalNavButton.Kind.KEY, "");
        previous.setTooltip(null);
        register("system:category-previous", previous);
        var next = new JournalNavButton(categoryViewportRight() + 6, keyY,
                categoryKeyWidth(), keyHeight, Component.literal("E"),
                ignored -> cycleCategory(1), styles, "category-next", JournalNavButton.Kind.KEY, "");
        next.setTooltip(null);
        register("system:category-next", next);
    }

    private List<String> categories() {
        return ClientQuestDefinitions.journalOrder().tabOrder(ClientQuestDefinitions.types());
    }

    private void startTaskMove(String id) {
        if (!canAuthor() || !editMode || questById(id) == null || orderPending != null) return;
        movingTask = id;
        movingTab = "";
        hierarchyOpen = false;
        selectedId = id;
        selectedTaskIds.clear();
        selectedTaskIds.add(id);
        selectedLayoutId = null;
        selectedLayoutIds.clear();
        revealSelectedPage = true;
        setFocused(null);
        init();
    }

    private void startTabMove(String id) {
        if (!canAuthor() || !editMode || !categories().contains(id) || orderPending != null) return;
        movingTask = "";
        movingTab = id;
        hierarchyOpen = false;
        selectedLayoutId = null;
        selectedLayoutIds.clear();
        revealCategory = true;
        setFocused(null);
        init();
        ensureCategoryVisible(categories().indexOf(id), categories().size() + 1);
        animateCategories();
    }

    private void stopOrderMove() {
        movingTask = movingTab = "";
        menu.clear();
        menuTarget = null;
        closeAnimatedMenu("context");
        setFocused(null);
        setDragging(false);
        init();
    }

    private List<String> orderMovePeers() {
        if (!movingTab.isBlank()) return categories();
        if (movingTask.isBlank()) return List.of();
        String membership = ClientQuestDefinitions.groupOf(movingTask);
        return quests.stream().filter(q -> ClientQuestDefinitions.groupOf(q.id()).equals(membership))
                .map(QuestDefinition::id).toList();
    }

    private boolean canMoveOrder(int direction) {
        var peers = orderMovePeers();
        String id = movingTab.isBlank() ? movingTask : movingTab;
        int index = peers.indexOf(id);
        return canAuthor() && editMode && orderPending == null && index >= 0
                && index + direction >= 0 && index + direction < peers.size();
    }

    private void moveOrder(int direction) {
        if (!canMoveOrder(direction)) return;
        String id = movingTab.isBlank() ? movingTask : movingTab;
        var peers = orderMovePeers();
        orderPending = ClientQuestProgress.moveJournalEntry(movingTab.isBlank() ? "task" : "tab",
                id, peers.get(peers.indexOf(id) + direction), direction);
        error = null;
        updateOrderControls();
    }

    private void initOrderControls() {
        boolean tab = !movingTab.isBlank();
        for (int direction : List.of(-1, 1)) {
            String id = direction < 0 ? "order-previous" : "order-next";
            int angle = tab ? direction < 0 ? 0 : 180 : direction < 0 ? 90 : -90;
            var button = new JournalNavButton(direction < 0 ? 16 : 44, height - 28, 24, 24,
                    text("order." + (tab ? direction < 0 ? "left" : "right" : direction < 0 ? "up" : "down")),
                    ignored -> moveOrder(direction), styles, id, JournalNavButton.Kind.ICON, "back") {
                @Override protected void renderContent(GuiGraphics g, ButtonStyle style, int color) {
                    g.pose().pushPose();
                    g.pose().translate(getX() + getWidth() / 2F, getY() + getHeight() / 2F, 0);
                    g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
                    g.pose().translate(-getX() - getWidth() / 2F, -getY() - getHeight() / 2F, 0);
                    super.renderContent(g, style, color);
                    g.pose().popPose();
                }
            };
            button.setTooltip(Tooltip.create(button.getMessage()));
            register("system:" + id, button);
        }
        register("system:order-done", new EditorIconButton(72, height - 28, text("order.done"),
                this::stopOrderMove, styles, "order-done", "choose"));
        updateOrderControls();
    }

    private void updateOrderControls() {
        StyledButton previous = controls.get("system:order-previous"), next = controls.get("system:order-next");
        if (previous != null) previous.active = canMoveOrder(-1);
        if (next != null) next.active = canMoveOrder(1);
    }

    private Component categoryLabel(String value) {
        return ClientQuestDefinitions.types().stream().filter(type -> type.id().equals(value))
                .map(type -> (Component) Component.literal(type.name())).findFirst()
                .orElseGet(() -> value.equals("active") ? text("category.active")
                        : value.equals("completed") ? text("category.completed") : Component.literal(value));
    }

    private String categoryIcon(String value) {
        return ClientQuestDefinitions.types().stream().filter(type -> type.id().equals(value))
                .map(QuestType::icon).findFirst().orElse(value.equals("active") ? "all"
                        : value.equals("completed") ? "choose" : "other");
    }

    private int categoryTabWidth() { return width < 400 ? 24 : 28; }
    private int categoryGap() { return width < 400 ? 3 : 6; }
    private int categoryKeyWidth() { return width < 400 ? 9 : 10; }
    private int categoryViewportWidth() {
        int available = width < 400 ? width - 72 : width - (editMode ? 260 : 316);
        return Math.max(72, Math.min(164, available));
    }
    private int categoryViewportLeft() {
        int centered = (width - categoryViewportWidth()) / 2;
        return !editMode && !overviewMode && width >= 400
                ? Math.min(centered, width - taskNoticeHeaderInset() - 20 - categoryViewportWidth()) : centered;
    }
    private int taskNoticeHeaderInset() {
        return selected != null && QuestUnlockToast.hasTask(selected.id()) ? 174 : 146;
    }
    private int categoryViewportRight() { return categoryViewportLeft() + categoryViewportWidth(); }
    private int categoryContentWidth(int count) {
        return count <= 0 ? 0 : count * categoryTabWidth() + (count - 1) * categoryGap();
    }
    private int categoryContentInset(int count) {
        return Math.max(0, (categoryViewportWidth() - categoryContentWidth(count)) / 2);
    }
    private double maxCategoryScroll(int count) {
        return Math.max(0, categoryContentWidth(count) - categoryViewportWidth());
    }
    private void clampCategoryScroll(int count) {
        categoryScroll = Math.max(0, Math.min(maxCategoryScroll(count), categoryScroll));
        categoryMotion.limit(maxCategoryScroll(count));
        if (Math.abs(categoryMotion.position() - categoryScroll) > .01) categoryMotion.jump(categoryScroll);
    }
    private void ensureCategoryVisible(int index, int count) {
        if (index < 0) return;
        int rawLeft = categoryContentInset(count)
                + index * (categoryTabWidth() + categoryGap());
        int rawRight = rawLeft + categoryTabWidth();
        if (rawLeft < categoryScroll) categoryScroll = rawLeft;
        if (rawRight > categoryScroll + categoryViewportWidth()) {
            categoryScroll = rawRight - categoryViewportWidth();
        }
        clampCategoryScroll(count);
    }
    private int categoryButtonX(int index, int count) {
        return categoryViewportLeft() + categoryContentInset(count)
                + index * (categoryTabWidth() + categoryGap()) - (int) Math.round(categoryScroll);
    }
    private boolean inCategoryViewport(double x, double y) {
        return !overviewMode && x >= categoryViewportLeft() && x < categoryViewportRight()
                && y >= categoryY() && y < categoryY() + categoryHeight();
    }
    private void scrollCategories(double amount) {
        int count = categories().size() + (editMode ? 1 : 0);
        categoryMotion.limit(maxCategoryScroll(count));
        categoryMotion.wheel(-amount);
    }

    private void animateCategories() {
        int count = categories().size() + (editMode ? 1 : 0);
        categoryMotion.limit(maxCategoryScroll(count));
        categoryMotion.advance();
        categoryScroll = categoryMotion.position();
        List<String> values = categories();
        for (int i = 0; i < values.size(); i++) {
            StyledButton button = controls.get("system:category-" + values.get(i));
            if (button != null) {
                button.setX(categoryButtonX(i, count));
                button.visible = button.getRight() > categoryViewportLeft() && button.getX() < categoryViewportRight();
            }
        }
        StyledButton add = controls.get("system:category-add");
        if (add != null) {
            add.setX(categoryButtonX(values.size(), count));
            add.visible = add.getRight() > categoryViewportLeft() && add.getX() < categoryViewportRight();
        }
    }

    private void setCategory(String value) {
        if (Objects.equals(category, value)) return;
        initialTaskResolved = true;
        selected = null;
        selectedId = "";
        selectedTaskIds.clear();
        selectedLayoutIds.clear();
        selectedLayoutId = null;
        detailScroll = detailTargetScroll = 0;
        revealTaskRow = "";
        revealSelectedPage = false;
        category = value;
        revealDetails();
        revealCategory = true;
        categoryTransitionTarget = value;
        categoryTransitionStart = System.nanoTime();
        listPage = 0;
        resetListScroll();
        init();
    }

    private void cycleCategory(int direction) {
        List<String> values = categories();
        if (values.isEmpty()) return;
        setCategory(values.get(Math.floorMod(values.indexOf(category) + direction, values.size())));
    }

    public static boolean matchesCategory(QuestDefinition quest, String category) {
        var view = ClientQuestProgress.view(quest.id());
        if (category.equals("completed")) return view != null && view.status().equals("claimed");
        if (!category.equals("active")) return quest.type().equals(category)
                && (ClientEditMode.enabled()
                || view == null || !view.status().equals("claimed"));
        return view != null && view.unlocked() && !view.status().equals("claimed");
    }

    private void resolveInitialTask() {
        if (initialTaskResolved || overviewMode || externalPicker) return;
        if (!editMode && !ClientQuestProgress.hasSnapshot()) return;
        initialTaskResolved = true;
        String target = requestedInitialTask;
        if (target.isBlank() && !editMode && category.equals("active") && searchQuery.isBlank()) {
            target = ClientQuestProgress.trackedIds().stream().filter(id -> {
                var quest = ClientQuestDefinitions.find(id);
                var view = ClientQuestProgress.view(id);
                return quest != null && view != null && !view.hidden() && matchesCategory(quest, "active");
            }).findFirst().orElse("");
        }
        if (target.isBlank()) return;
        var quest = ClientQuestDefinitions.find(target);
        if (quest == null) {
            if (!requestedInitialTask.isBlank()) {
                ClientPreferences.get().lastSelectedQuestId = "";
                ClientPreferences.get().save();
            }
            return;
        }
        selectedId = target;
        if (!editMode && viewClaimed(target)) category = "completed";
        else if (!editMode && !matchesCategory(quest, category)) category = quest.type();
        rememberSelectedQuest(target);
        revealSelectedPage = !requestedInitialTask.isBlank();
        if (!requestedInitialTask.isBlank()) expandGroup(ClientQuestDefinitions.groupOf(target));
    }

    private static boolean viewClaimed(String id) {
        var view = ClientQuestProgress.view(id);
        return view != null && view.status().equals("claimed");
    }

    private void revealTaskRow() {
        if (revealTaskRow.isBlank()) return;
        var row = journalList.rows().stream().filter(value -> value.key().equals("quest:" + revealTaskRow))
                .findFirst().orElse(null);
        if (row == null) return;
        // Resolve page/group first, then place the tracked row in the visible list.
        listScroll = listTargetScroll = Math.max(0, row.offset() - (listHeight() - row.height()) / 2.0);
        listReleaseVelocity = 0;
        clampListScroll();
        revealTaskRow = "";
    }

    private void returnToJournal(String id) {
        if (id == null || id.isEmpty()) return;
        QuestScreen journal = overviewJournal();
        journal.selectedId = id;
        journal.rememberSelectedQuest(id);
        journal.initialTaskResolved = true;
        journal.editMode = false;
        var destinationQuest = ClientQuestDefinitions.find(id);
        journal.category = destinationQuest != null && !matchesCategory(destinationQuest, "active")
                ? destinationQuest.type() : "active";
        journal.groupId = journal.chapterId = journal.searchQuery = "";
        journal.revealSelectedPage = true;
        journal.layout = LayoutStore.load("journal");
        journal.expandGroup(ClientQuestDefinitions.groupOf(id));
        minecraft.setScreen(journal);
        journal.journalList.rows().stream().filter(row -> row.key().equals("quest:" + id)).findFirst().ifPresent(row -> {
            journal.listScroll = journal.listTargetScroll = row.offset();
            journal.clampListScroll();
            journal.updateListButtons();
        });
    }

    private void openOverviewTask(String id) {
        if (id == null || id.isBlank()) return;
        closeTo(new JourneyScreen(overviewJournal(), id));
    }

    /** Overview replaces journal navigation; only hot events keep it as their parent. */
    private QuestScreen overviewJournal() {
        Screen destination = parent;
        while (destination instanceof QuestScreen overview && overview.overviewMode)
            destination = overview.parent;
        return destination instanceof QuestScreen journal ? journal : new QuestScreen(destination);
    }

    private void openOverviewCategory(String id) {
        QuestScreen journal = overviewJournal();
        journal.editMode = false;
        journal.initialTaskResolved = true;
        journal.category = id;
        journal.groupId = journal.chapterId = journal.searchQuery = "";
        journal.selectedId = "";
        journal.selected = null;
        journal.revealSelectedPage = false;
        journal.revealTaskRow = "";
        journal.listPage = 0;
        journal.listScroll = journal.listTargetScroll = 0;
        journal.detailScroll = journal.detailTargetScroll = 0;
        journal.layout = LayoutStore.load("journal");
        journal.revealCategory = true;
        closeTo(journal);
    }

    private boolean overviewCardPending() {
        return children().stream().anyMatch(child -> child instanceof OverviewCategoryCard card
                && card.activationPending());
    }

    private boolean overviewCardInteracting() {
        return children().stream().anyMatch(child -> child instanceof OverviewCategoryCard card && card.pressed());
    }

    private int overviewLeft() { return (width - overviewContentWidth()) / 2; }
    private int overviewContentWidth() { return width < 480 ? width - 32 : Math.min(960, width * 3 / 4); }
    private int overviewTop() { return Math.max(50, height / 4); }
    private int overviewTypeRows() { return 4; }
    private int overviewVisibleRows() { return 5; }
    private int overviewColumnGap() { return Math.max(8, overviewContentWidth() / 14); }
    private int overviewFeatureWidth() {
        int available = Math.max(1, overviewContentWidth() - overviewColumnGap());
        int preferred = available * 3 / 5;
        return Math.max(1, Math.min(480, Math.min(preferred, Math.max(1, available - 1))));
    }
    private int overviewRowHeight() {
        int available = Math.max(1, height - overviewTop() - 50);
        return Math.max(18, Math.min(40, (available - 18) / overviewVisibleRows()));
    }
    private int overviewFeatureHeight() {
        int rows = overviewVisibleRows();
        return rows * overviewRowHeight() + (rows - 1) * 4;
    }

    private void initOverviewPresentation() {
        featuredGroup = ClientQuestDefinitions.groups().stream()
                .filter(QuestCatalog.Group::recommended)
                .filter(group -> ClientQuestDefinitions.all().stream().anyMatch(quest ->
                        group.id().equals(ClientQuestDefinitions.groupOf(quest.id()))
                                && availableRecommendation(quest)))
                .findFirst().orElse(null);
        selected = featuredGroup == null ? ClientQuestDefinitions.all().stream()
                .filter(QuestDefinition::recommended).filter(this::availableRecommendation)
                .max(Comparator.comparingInt(QuestDefinition::recommendationLevel)).orElse(null)
                : ClientQuestDefinitions.all().stream()
                    .filter(quest -> featuredGroup.id().equals(ClientQuestDefinitions.groupOf(quest.id()))
                            && availableRecommendation(quest))
                    .findFirst().orElse(null);
        selectedId = selected == null ? "" : selected.id();
        nav("close", "close", width - 34, 8, 24, 24,
                JournalNavButton.Kind.ICON, "close", this::onClose);
        if (canAuthor()) {
            nav("edit", "edit", width - 204, 8, 24, 24, JournalNavButton.Kind.ICON,
                    "edit", this::overviewEditMenu);
            addRenderableWidget(new EditorIconButton(width - 176, 8, text("overview_background.edit"),
                    () -> minecraft.setScreen(new OverviewBackgroundScreen(this)), styles, "overview-background", "minecraft:painting"));
        }
        addRenderableWidget(new JournalNavButton(width - 142, 8, 100, 24, text("overview_journal"),
                ignored -> minecraft.setScreen(overviewJournal()), styles, "overview-journal",
                JournalNavButton.Kind.ACTION, "guide") {
            @Override protected void renderContent(GuiGraphics graphics, ButtonStyle style, int color) {
                JournalNavButton.drawIcon(graphics, "guide", getX() + 2, getY() + 4, 16, color);
                graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), 76),
                        getX() + 22, getY() + 8, color, false);
            }
        });
        int left = overviewLeft(), top = overviewTop(), feature = overviewFeatureWidth();
        int featureHeight = overviewFeatureHeight();
        if (selected != null) {
            StyledButton open = new StyledButton(left, top + 22, feature, featureHeight,
                    Component.empty(), ignored -> openOverviewTask(selectedId),
                    styles, "overview-feature", "toolbar-button") {
                @Override
                public boolean isMouseOver(double mouseX, double mouseY) {
                    double extraX = getWidth() * .025 * overviewFeatureHover.eased();
                    double extraY = getHeight() * .025 * overviewFeatureHover.eased();
                    return visible && mouseX >= getX() - extraX && mouseX < getRight() + extraX
                            && mouseY >= getY() - extraY && mouseY < getBottom() + extraY;
                }
                @Override
                protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                    renderOverviewFeature(graphics, isMouseOver(mouseX, mouseY));
                }
            };
            addRenderableWidget(open);
        }
        else {
            StyledButton empty = new StyledButton(left, top + 22, feature, featureHeight,
                Component.empty(), ignored -> {},
                styles, "overview-feature-empty", "toolbar-button") {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                int x = getX(), y = getY(), w = getWidth(), h = getHeight();
                int frame = Math.max(2, Math.min(8, Math.min(w, h) / 28));
                graphics.fill(x - 5, y + 5, getRight() + 7, getBottom() + 10, 0xFFBFA982);
                graphics.fill(x - 8, y - 5, getRight() + 9, getBottom() + 3, 0xFFC9B28A);
                graphics.fill(x - 2, y - 2, getRight() + 2, getBottom() + 2, 0xFFF8F5EE);
                graphics.fill(x + frame, y + frame, getRight() - frame, getBottom() - frame, 0xFFE9D9B9);
                int captionHeight = Math.max(30, Math.min(56, h / 8));
                int imageBottom = getBottom() - frame - captionHeight;
                graphics.fill(x + frame, y + frame, getRight() - frame, imageBottom, 0xFFE8D7B4);
                graphics.fillGradient(x + frame, y + frame, getRight() - frame, imageBottom,
                        0xFFF1E5CA, 0xFFE4D1AA);
                for (int row = y + frame + 4, index = 0; row < imageBottom; row += 18, index++) {
                    for (int column = x + frame + 5 + (index % 2) * 9;
                         column < getRight() - frame; column += 18) {
                        MailDecor.diamond(graphics, column, row, 3, 0x12A98A5D);
                    }
                }
                int centerX = x + w / 2, centerY = y + frame + (imageBottom - y - frame) / 2;
                JournalNavButton.drawIcon(graphics, "guide", centerX - Math.min(w, h) / 3,
                        centerY - Math.min(w, h) / 3, Math.min(w, h) * 2 / 3, 0x2C8C714C);
                graphics.fill(x + frame, imageBottom, getRight() - frame, getBottom() - frame, 0xFFFCFBF8);
                int captionY = imageBottom + Math.max(4, (getBottom() - frame - imageBottom - font.lineHeight) / 2);
                graphics.drawString(font, text("overview_feature_empty"), x + frame + 10, captionY,
                        0xFF596274, true);
            }
            };
            empty.active = false;
            addRenderableWidget(empty);
        }
        int rowLeft = left + feature + overviewColumnGap();
        int rowTop = top + 22;
        int rowWidth = Math.max(1, overviewContentWidth() - feature - overviewColumnGap());
        int rowGap = 4;
        int rowHeight = overviewRowHeight();
        List<OverviewEntry> entries = List.of(
                new OverviewEntry("main", "overview.category.travel", "category_journey"),
                new OverviewEntry("legendary", "overview.category.legendary", "category_legendary"),
                new OverviewEntry("hot_events", "overview.category.hot_events", "category_hot_events"),
                new OverviewEntry("anecdotes", "overview.category.anecdotes", "category_anecdotes"));
        for (int index = 0; index < entries.size(); index++) {
            OverviewEntry entry = entries.get(index);
            JournalNavButton row = new OverviewCategoryCard(rowLeft, rowTop + index * (rowHeight + rowGap),
                    rowWidth, rowHeight, text(entry.labelKey()),
                    ignored -> {
                        if (entry.id().equals("main")) closeTo(new JourneyScreen(overviewJournal()));
                        else if (entry.id().equals("hot_events")) closeTo(new HotEventsScreen(this));
                        else if (entry.id().equals("legendary")) closeTo(new LegendaryScreen(this));
                        else if (entry.id().equals("anecdotes"))
                            closeTo(new AnecdoteScreen(this));
                        else openOverviewCategory(entry.id());
                    }, styles, "overview-" + entry.id(), entry.glyph());
            row.setLimitedRewardBadge(hasLimitedRewardForOverview(entry.id()));
            row.setTooltip(Tooltip.create(text(entry.labelKey())));
            addRenderableWidget(row);
        }
    }

    private record OverviewEntry(String id, String labelKey, String glyph) {}

    private boolean hasLimitedRewardForOverview(String id) {
        if (id.equals("hot_events")) {
            return ClientQuestDefinitions.groups().stream().anyMatch(group ->
                    group.hotEvent().enabled() && group.limitedReward())
                    || ClientQuestDefinitions.all().stream().anyMatch(quest ->
                    quest.hotEvent().enabled() && quest.limitedReward());
        }
        return ClientQuestDefinitions.all().stream()
                .filter(quest -> quest.type().equals(id))
                .anyMatch(quest -> quest.limitedReward()
                        || Optional.ofNullable(ClientQuestDefinitions.groupOf(quest.id()))
                        .flatMap(groupId -> ClientQuestDefinitions.groups().stream()
                                .filter(group -> group.id().equals(groupId)).findFirst())
                        .map(QuestCatalog.Group::limitedReward).orElse(false));
    }

    private boolean availableRecommendation(QuestDefinition quest) {
        var view = ClientQuestProgress.view(quest.id());
        return view == null || !view.completed() && !view.hidden();
    }

    private void initSceneActions() {
        int buttonWidth = Math.min(110, Math.max(64, (width - 40) / 2));
        int left = (width - 2 * buttonWidth - 8) / 2;
        addRenderableWidget(new StyledButton(left, height - 28, buttonWidth, 20,
                text("structure.leave"), ignored -> {
                    ClientStructures.leave();
                    minecraft.setScreen(null);
                }, styles, "structure-leave", "toolbar-button"));
        addRenderableWidget(new StyledButton(left + buttonWidth + 8, height - 28, buttonWidth, 20,
                text("structure.reset"), ignored -> minecraft.setScreen(new QuestConfirmScreen(this,
                text("structure.reset"), () -> {
                    ClientStructures.reset();
                    minecraft.setScreen(null);
                })), styles, "structure-reset", "toolbar-button"));
    }

    private StyledButton action(String id, String icon, String tooltip, int x, int y, int w, Runnable action) {
        StyledButton button = List.of("undo", "redo", "hierarchy", "source").contains(id)
                ? new EditorIconButton(x, y, text(tooltip), action, styles, id,
                        id.equals("hierarchy") ? hierarchyOpen ? "layers_hide" : "layers" : id)
                : new StyledButton(x, y, w, 20, Component.literal(icon),
                ignored -> action.run(), styles, id, "toolbar-button");
        button.setTooltip(Tooltip.create(id.startsWith("add-")
                ? text(tooltip).copy().append("\n").append(text("add_menu_hint")) : text(tooltip)));
        register("system:" + id, button);
        return button;
    }

    private void register(String id, StyledButton button) {
        controls.put(id, button);
        if (!isListRow(id) && !isFixedSystemControl(id) && !isCategoryControl(id) && !isHeaderControl(id)
                && layout.positions().containsKey(id)) {
            var p = layout.position(id, button.getX(), button.getY());
            button.setPosition(Math.max(0, Math.min(p.x(), width - button.getWidth())),
                    Math.max(0, Math.min(p.y(), height - button.getHeight())));
            p.size(button.getWidth(), button.getHeight());
        }
        addRenderableWidget(button);
    }

    @Override
    protected <T extends net.minecraft.client.gui.components.events.GuiEventListener
            & Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addRenderableWidget(T widget) {
        if (widget instanceof StyledButton button) {
            // Recommended cards already animate their entire image/card separately.
            if (!(button instanceof MailboxActionButton) && !button.styleId().equals("overview-feature"))
                button.actionMotion("claim_all");
            button.copyMotionFrom(previousButtons.get(button.styleId()));
        }
        return super.addRenderableWidget(widget);
    }

    private static boolean isFixedSystemControl(String id) {
        return "system:focus".equals(id) || "system:track".equals(id) || "system:open-task".equals(id)
                || "system:complete".equals(id) || "system:claim".equals(id) || "system:claim-all".equals(id)
                || "system:daily-bonus".equals(id)
                || "system:list-page-previous".equals(id) || "system:list-page-next".equals(id)
                || "system:category-previous".equals(id) || "system:category-next".equals(id)
                || "system:order-previous".equals(id) || "system:order-next".equals(id)
                || "system:order-done".equals(id);
    }

    private static boolean isCategoryControl(String id) {
        return id != null && id.startsWith("system:category-");
    }

    private static boolean isHeaderControl(String id) {
        return "system:edit".equals(id) || "system:settings".equals(id)
                || "system:close".equals(id) || "system:overview".equals(id)
                || "system:claim_all".equals(id);
    }

    private void clearFixedControlLayoutState() {
        boolean changed = false;
        for (String id : List.of("system:focus", "system:track", "system:open-task",
                "system:complete", "system:claim", "system:claim-all")) {
            changed |= layout.positions().remove(id) != null;
            changed |= layout.parents().remove(id) != null;
        }
        for (String id : new ArrayList<>(layout.positions().keySet())) {
            if (isCategoryControl(id) || isHeaderControl(id)) {
                changed |= layout.positions().remove(id) != null;
                changed |= layout.parents().remove(id) != null;
            }
        }
        if (changed) {
            LayoutStore.save("journal", layout);
        }
    }

    private void selectQuest(QuestDefinition quest) {
        initialTaskResolved = true;
        revealSelectedPage = false;
        revealTaskRow = "";
        if (overviewMode && !editMode) {
            returnToJournal(quest.id());
            return;
        }
        selectedTaskIds.clear();
        selectedTaskIds.add(quest.id());
        if (!selectedId.equals(quest.id())) {
            selectedLayoutIds.clear();
            selectedLayoutId = null;
            detailScroll = detailTargetScroll = 0;
            revealDetails();
        }
        selectedId = quest.id();
        rememberSelectedQuest(quest.id());
        init();
    }

    private void navigateTaskSelection(int direction) {
        if (editMode || overviewMode || quests.size() < 2 || selectedId.isBlank()) return;
        int current = questIndex(selectedId);
        if (current < 0) return;
        int targetIndex = Math.clamp(current + direction, 0, quests.size() - 1);
        if (targetIndex == current) return;
        QuestDefinition target = quests.get(targetIndex);
        if (listPages > 1) listPage = targetIndex / tasksPerPage();
        expandGroup(ClientQuestDefinitions.groupOf(target.id()));
        selectedTaskIds.clear();
        selectedTaskIds.add(target.id());
        selectedLayoutIds.clear();
        selectedLayoutId = null;
        selectedId = target.id();
        selected = target;
        revealSelectedPage = false;
        revealTaskRow = target.id();
        detailScroll = detailTargetScroll = 0;
        resetListScroll();
        rememberSelectedQuest(target.id());
        init();
    }

    private void rememberSelectedQuest(String id) {
        if (id == null || id.isBlank() || editMode || overviewMode
                || previewTemplate != null || previewQuest != null) return;
        var preferences = ClientPreferences.get();
        String category = viewClaimed(id) ? "completed" : this.category;
        if (!id.equals(preferences.lastSelectedQuestId)
                || !category.equals(preferences.lastSelectedQuestCategory)) {
            preferences.lastSelectedQuestId = id;
            preferences.lastSelectedQuestCategory = category;
            preferences.save();
        }
    }

    private void markSelectedTaskRead() {
        var view = ClientQuestProgress.view(selectedId);
        if (editMode || externalPicker || form != null || iconPicker != null || previewTemplate != null
                || selected == null || view == null || !view.newTask()
                || !readMarkedTasks.add(selectedId)) return;
        String id = selectedId;
        ClientQuestProgress.markTaskRead(id).thenAccept(result -> {
            if (!List.of("read", "already_read").contains(result)) readMarkedTasks.remove(id);
        });
    }
    String searchText() { return searchQuery; }
    void searchText(String value) { searchQuery = value; searchConfigurationChanged(); }

    private void markAllTasksRead() {
        QuestUnlockToast.clearUnlockNotices();
        if (readAllPending != null || !ClientQuestProgress.hasUnreadTasks()) return;
        readAllPending = ClientQuestProgress.markAllTasksRead();
        var button = controls.get("system:mark-all-read");
        if (button != null) button.active = false;
    }

    private int listLeft() { return 16; }
    private int listRight() { return 16 + listWidth; }
    private int headerHeight() { return overviewMode && !editMode ? 48 : width < 400 ? 51 : 32; }
    private int headerControlY() { return overviewMode && !editMode ? 12 : 4; }
    private int categoryY() { return width < 400 ? 29 : 4; }
    private int categoryHeight() { return width < 400 ? 20 : 24; }
    private int detailsTop() { return previewViewport != null ? previewViewport.top() : overviewMode && !editMode ? 82 : headerHeight() + 6; }
    private int canvasLeft() { return detailsLeft; }
    private int canvasTop() { return detailsTop(); }
    private int canvasRight() { return previewViewport != null ? previewViewport.right() : width - 16; }
    private int canvasBottom() { return previewViewport != null ? previewViewport.bottom() : Math.max(canvasTop() + 1, height - (stackedFocusControl() ? 72 : 52)); }
    private int canvasWidth() { return Math.max(1, canvasRight() - canvasLeft()); }
    private int canvasHeight() { return Math.max(1, canvasBottom() - canvasTop()); }
    private int taskWidth() { return layout.taskCanvas(selectedId).width(); }
    private int taskHeight() { return layout.taskCanvas(selectedId).height(); }
    int previewCanvasWidth() { return taskWidth(); }
    int previewCanvasHeight() { return taskHeight(); }
    private void setCanvasZoom(double value) {
        clearFrameElements();
        double next = Math.round(Math.max(0.1, Math.min(5, value)) * 1000) / 1000.0;
        double ratio = next / canvasZoom;
        canvasPanX *= ratio;
        canvasPanY *= ratio;
        detailScroll *= ratio;
        detailTargetScroll *= ratio;
        canvasZoom = next;
        clampDetailScroll();
    }

    private void zoomCanvasAt(double value, double x, double y) {
        double before = canvasZoom;
        double relativeX = (x - canvasLeft() - canvasPanX) / before;
        double relativeY = (y - canvasTop() - canvasPanY + detailScroll) / before;
        setCanvasZoom(value);
        canvasPanX = x - canvasLeft() - relativeX * canvasZoom;
        canvasPanY = y - canvasTop() + detailScroll - relativeY * canvasZoom;
        clampDetailScroll();
    }

    private void resetCanvasView() {
        clearFrameElements();
        canvasZoom = 1.0;
        canvasPanX = canvasPanY = 0;
        detailScroll = detailTargetScroll = 0;
        middlePanning = false;
    }

    private int coordinateCellWidth() {
        return Math.max(1, (canvasRight() - detailsLeft - 2) / 3);
    }

    private int coordinateLeft(String axis) {
        return detailsLeft + 2 + coordinateCellWidth() * (axis.equals("x") ? 0 : axis.equals("y") ? 1 : 2);
    }

    private String coordinateAt(double x, double y) {
        if (y < height - 49 || y >= height - 34) return null;
        for (String axis : List.of("x", "y", "zoom")) {
            if (x >= coordinateLeft(axis) && x < coordinateLeft(axis) + coordinateCellWidth()
                    && (axis.equals("zoom") || layout.elements().containsKey(selectedLayoutId))) return axis;
        }
        return null;
    }

    private void renderCoordinates(GuiGraphics graphics) {
        if (!editMode || !showEditorAxes) return;
        var position = layout.positions().get(selectedLayoutId);
        for (String axis : List.of("x", "y", "zoom")) {
            if (coordinateEditor != null && coordinateAxis.equals(axis)) continue;
            String label = axis.equals("zoom") ? String.format(java.util.Locale.ROOT, "%.1f%%", canvasZoom * 100)
                    : axis.toUpperCase(java.util.Locale.ROOT) + ": "
                    + (position == null ? "-" : axis.equals("x") ? position.x() : position.y());
            int left = coordinateLeft(axis);
            graphics.enableScissor(left, height - 49, left + coordinateCellWidth() - 2, height - 34);
            graphics.drawString(font, label, left, height - 46, 0xFFE4EAF2, true);
            graphics.disableScissor();
        }
    }

    private void beginCoordinateEdit(String axis) {
        coordinateAxis = axis;
        coordinateElement = selectedLayoutId;
        coordinateEditor = new EditBox(font, coordinateLeft(axis), height - 49,
                Math.max(16, coordinateCellWidth() - 4), 14, Component.literal(axis));
        coordinateEditor.setMaxLength(12);
        var position = axis.equals("zoom") ? null : layout.position(selectedLayoutId, 0, 0);
        coordinateEditor.setValue(axis.equals("zoom")
                ? String.format(java.util.Locale.ROOT, "%.1f", canvasZoom * 100)
                : Integer.toString(axis.equals("x") ? position.x() : position.y()));
        setFocused(null);
        coordinateEditor.setFocused(true);
        coordinateEditor.setHighlightPos(0);
    }

    private boolean finishCoordinateEdit(boolean commit) {
        if (coordinateEditor == null) return true;
        if (commit) {
            try {
                if (coordinateAxis.equals("zoom")) {
                    double value = Double.parseDouble(coordinateEditor.getValue().strip());
                    if (!Double.isFinite(value) || value < 10 || value > 500) throw new IllegalArgumentException();
                    setCanvasZoom(value / 100);
                } else {
                    var element = layout.elements().get(coordinateElement);
                    if (element != null && element.quest().equals(selectedId)) {
                        int value = Integer.parseInt(coordinateEditor.getValue().strip());
                        int limit = coordinateAxis.equals("x") ? taskWidth() : taskHeight();
                        if (value < 0 || value > limit) throw new IllegalArgumentException();
                        var p = layout.position(coordinateElement, 0, 0);
                        pushUndo();
                        layout.moveTree(coordinateElement, coordinateAxis.equals("x") ? value - p.x() : 0,
                                coordinateAxis.equals("y") ? value - p.y() : 0, taskWidth(), taskHeight());
                        saveLayout();
                    }
                }
            } catch (IllegalArgumentException invalid) {
                coordinateEditor.setTextColor(0xFFFF5555);
                return false;
            }
        }
        coordinateEditor = null;
        return true;
    }

    private void openCanvasSize(String questId) {
        var size = layout.taskCanvas(questId);
        form = new Form("canvas", "normal");
        form.id = questId;
        form.editing = true;
        form.layoutWidth = Integer.toString(size.width());
        form.layoutHeight = Integer.toString(size.height());
        error = null;
        menu.clear();
        init();
    }
    private double canvasScaleX() { return canvasContentScale(); }
    private double canvasScaleY() { return canvasContentScale(); }
    private double canvasContentScale() {
        // Preview fits authored canvases; the editor keeps its zoomable, scrollable workspace.
        int referenceWidth = editMode ? LayoutEditorState.DEFAULT_TASK_WIDTH
                : Math.max(LayoutEditorState.DEFAULT_TASK_WIDTH, taskWidth());
        int referenceHeight = editMode ? LayoutEditorState.DEFAULT_TASK_HEIGHT
                : Math.max(LayoutEditorState.DEFAULT_TASK_HEIGHT, taskHeight());
        return Math.max(0.75, Math.min(canvasWidth() / (double) referenceWidth,
                canvasHeight() / (double) referenceHeight));
    }
    private double canvasRenderScale() { return canvasContentScale() * (editMode ? canvasZoom : 1.0); }
    private int canvasPixels(int logicalPixels) {
        return logicalPixels <= 0 ? 0
                : Math.max(1, (int) Math.round(logicalPixels * canvasRenderScale()));
    }
    private int unzoomedCanvasPixels(int logicalPixels) {
        return logicalPixels <= 0 ? 0
                : Math.max(1, (int) Math.round(logicalPixels * canvasContentScale()));
    }
    private int canvasX(int logicalX) {
        return canvasLeft() + (int) Math.round(logicalX * canvasScaleX());
    }
    private int canvasY(int logicalY) {
        return canvasTop() + (int) Math.round(logicalY * canvasScaleY());
    }
    private int logicalX(double screenX) {
        return (int) Math.round((screenX - canvasLeft() - canvasPanX) / canvasZoom / canvasScaleX());
    }
    private int logicalY(double screenY) {
        return (int) Math.round((screenY - canvasTop() - canvasPanY + detailScroll) / canvasZoom / canvasScaleY());
    }
    private int listTop() { return overviewMode && !editMode ? 102 : searchY() + 24 + 4; }
    private int listBottom() {
        return Math.max(listTop() + 1, height - (listPages > 1 ? 88 : 58)
                - (stackedFocusControl() ? 26 : 0));
    }
    private int listHeight() { return listBottom() - listTop(); }
    private double maxListScroll() { return Math.max(0, journalList.contentHeight() - listHeight()); }
    private int listRowY(JournalListModel.Row row) { return listTop() + row.offset() - (int) Math.floor(listScroll); }
    private static boolean isListRow(String id) {
        return id != null && (id.startsWith("quest:") || id.startsWith("group:"));
    }
    private void toggleGroup(String id) {
        int oldOffset = journalList.rows().stream().filter(row -> row.key().equals("group:" + id))
                .mapToInt(JournalListModel.Row::offset).findFirst().orElse(0);
        boolean wasExpanded = !collapsedGroupIds.contains(id);
        long now = System.nanoTime();
        var motion = groupSlides.computeIfAbsent(id, ignored -> new UiMotion(wasExpanded, now));
        if (!collapsedGroupIds.remove(id)) collapsedGroupIds.add(id);
        persistCollapsedGroups();
        motion.target(!collapsedGroupIds.contains(id), now, groupSlideMillis());
        // A header clicked while partly scrolled must not disappear above the viewport.
        listScroll = listTargetScroll = Math.min(listScroll, oldOffset);
        listReleaseVelocity = 0;
        init();
    }

    private void expandGroup(String id) {
        if (id != null && !id.isBlank() && collapsedGroupIds.remove(id)) persistCollapsedGroups();
    }

    private void persistCollapsedGroups() {
        var preferences = ClientPreferences.get();
        preferences.collapsedJournalGroups = collapsedGroupIds.stream().sorted().toList();
        if (!preferences.save())
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn("Unable to save collapsed journal groups");
    }

    private static int groupSlideMillis() {
        return animationMillis() == 0 ? 0 : ClientPreferences.get().popupAnimationMillis;
    }

    private void updateGroupSlideLayout() {
        var progress = new HashMap<String, Double>();
        groupSlides.forEach((id, motion) -> progress.put(id, (double) motion.eased()));
        var slide = JournalGroupSlide.apply(groupSlideSource, progress);
        journalList = slide.model();
        groupRowClips = slide.clips();
    }

    private void animateGroups(long now) {
        boolean removeClosedRows = false;
        boolean changed = false;
        for (var entry : groupSlides.entrySet()) {
            var motion = entry.getValue();
            // Programmatic task reveals may reopen a collapsed group during a refresh.
            boolean expanded = !collapsedGroupIds.contains(entry.getKey());
            if (motion.visible() != expanded) motion.target(expanded, now, groupSlideMillis());
            double before = motion.position();
            motion.advance(now, groupSlideMillis());
            removeClosedRows |= before > 0 && motion.position() == 0;
            changed |= before != motion.position();
        }
        if (removeClosedRows) {
            rebuildQuestListData();
            removeListRows();
            buildListRows(listWidth - (maxListScroll() > 0 ? 8 : 0));
            initListPager();
        } else if (changed) updateGroupSlideLayout();
    }

    private int groupClipTop(String key) {
        var clip = groupRowClips.get(key);
        return clip == null ? listTop()
                : Math.max(listTop(), listTop() + clip.top() - (int) Math.floor(listScroll));
    }

    private int groupClipBottom(String key) {
        var clip = groupRowClips.get(key);
        return clip == null ? listBottom()
                : Math.min(listBottom(), listTop() + clip.bottom() - (int) Math.floor(listScroll));
    }
    private boolean listAvailable() {
        return form == null && iconPicker == null && !(editMode && hierarchyOpen);
    }
    private boolean inList(double x, double y) {
        return listAvailable() && x >= listLeft() && x < listRight() && y >= listTop() && y < listBottom();
    }
    private int thumbHeight() {
        return Math.min(listHeight(), Math.max(20,
                (int) (listHeight() * (double) listHeight() / Math.max(1, journalList.contentHeight()))));
    }
    private int thumbTop() {
        return listTop() + (int) Math.round(Math.clamp(listScroll, 0, maxListScroll())
                / Math.max(1, maxListScroll()) * (listHeight() - thumbHeight()));
    }
    private void resetListScroll() {
        listScroll = listTargetScroll = listReleaseVelocity = 0;
        listDragCandidate = listScrollbarDragging = listDragMoved = false;
        cancelTaskReorder();
    }
    private int questIndex(String id) {
        if (id == null || id.isEmpty()) return -1;
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id().equals(id)) return i;
        }
        return -1;
    }
    private void initListPager() {
        if (listPages <= 1) return;
        int y = listBottom() + 4;
        StyledButton previous = new StyledButton(listLeft(), y, 24, 20, Component.literal("\u2039"),
                ignored -> changeListPage(listPage - 1), styles, "list-page-previous", "toolbar-button");
        previous.active = listPage > 0;
        previous.setTooltip(editMode ? Tooltip.create(text("list_page_previous")) : null);
        register("system:list-page-previous", previous);
        StyledButton next = new StyledButton(listRight() - 24, y, 24, 20, Component.literal("\u203a"),
                ignored -> changeListPage(listPage + 1), styles, "list-page-next", "toolbar-button");
        next.active = listPage + 1 < listPages;
        next.setTooltip(editMode ? Tooltip.create(text("list_page_next")) : null);
        register("system:list-page-next", next);
    }
    private void changeListPage(int page) {
        int next = Math.clamp(page, 0, listPages - 1);
        if (next == listPage) return;
        listPage = next;
        selected = null;
        initialTaskResolved = true;
        revealSelectedPage = false;
        revealTaskRow = "";
        int first = listPage * tasksPerPage();
        selectedId = first < quests.size() ? quests.get(first).id() : "";
        detailScroll = detailTargetScroll = 0;
        resetListScroll();
        init();
    }
    private void clampListScroll() {
        listScroll = Math.max(0, Math.min(maxListScroll(), listScroll));
        listTargetScroll = Math.max(0, Math.min(maxListScroll(), listTargetScroll));
    }
    private void updateListButtons() {
        String activeGroup = selectedId.isEmpty() ? "" : ClientQuestDefinitions.groupOf(selectedId);
        viewportRows = visibleJournalRows();
        if (viewportRows != materializedRows) {
            var keep = new HashSet<String>();
            for (var row : viewportRows) if (!row.section()) keep.add(row.key());
            for (String key : List.copyOf(materializedRowKeys)) {
                if (keep.contains(key)) continue;
                var button = controls.remove(key);
                if (button != null) {
                    if (getFocused() == button) setFocused(null);
                    button.visible = false;
                    removeWidget(button);
                }
                materializedRowKeys.remove(key);
            }
            buildListRows(listWidth - (maxListScroll() > 0 ? 8 : 0));
        }
        for (var row : viewportRows) {
            StyledButton button = controls.get(row.key());
            if (button == null) continue;
            button.setY(listRowY(row));
            button.setSelectionHighlighted(row.group() != null
                    ? row.group().id().equals(activeGroup) : row.quest().id().equals(selectedId));
            int top = groupClipTop(row.key()), bottom = groupClipBottom(row.key());
            button.clipTo(listLeft(), top, listRight(), Math.max(top, bottom));
            int overscan = (JournalListModel.TALL_ROW + 4) * LIST_OVERSCAN_ROWS;
            button.visible = listAvailable() && bottom > top
                    && button.getBottom() > top - overscan && button.getY() < bottom + overscan;
        }
    }
    private void animateList() {
        long now = System.nanoTime();
        animateGroups(now);
        double dt = Math.min(0.05, Math.max(0, (now - listScrollFrameNanos) / 1_000_000_000.0));
        listScrollFrameNanos = now;
        advanceTaskReorder(now, dt);
        if (listAvailable() && menu.isEmpty() && !listDragCandidate && !listScrollbarDragging) {
            double edge = Math.clamp(listScroll, 0, maxListScroll());
            if (listScroll != edge) {
                DampedSpring.State state = DampedSpring.advance(listScroll, listReleaseVelocity, edge, dt, 15);
                listScroll = state.position();
                listReleaseVelocity = state.velocity();
            } else {
                listReleaseVelocity = 0;
                listScroll += (listTargetScroll - listScroll) * -Math.expm1(-16 * dt);
                if (Math.abs(listTargetScroll - listScroll) < 0.02) listScroll = listTargetScroll;
            }
        }
        listTargetScroll = Math.clamp(listTargetScroll, 0, maxListScroll());
        updateListButtons();
    }

    private void advanceTaskReorder(long now, double dt) {
        if (!taskReorderCandidate.isBlank() && !taskReorderDragging && listDragCandidate && !listDragMoved
                && now - taskReorderPressedAt >= 400_000_000L) {
            if (!canAuthor() || !editMode || orderPending != null) {
                cancelTaskReorder();
                return;
            }
            if (!selectedTaskIds.contains(taskReorderCandidate)) {
                selectedTaskIds.clear();
                selectedTaskIds.add(taskReorderCandidate);
            }
            var ordered = ClientQuestDefinitions.all().stream()
                    .filter(q -> selectedTaskIds.contains(q.id())).map(QuestDefinition::id).toList();
            taskReorderIds = ordered;
            String group = ClientQuestDefinitions.groupOf(taskReorderCandidate);
            taskReorderCompatible = ordered.size() <= 512 && ordered.stream()
                    .allMatch(id -> ClientQuestDefinitions.groupOf(id).equals(group));
            taskReorderDragging = true;
            listReleaseVelocity = 0;
            var previews = new ArrayList<JournalListButton>();
            for (String id : ordered.stream().limit(3).toList()) {
                var quest = ClientQuestDefinitions.find(id);
                var preview = new JournalListButton(0, 0, listWidth - 12, JournalListModel.QUEST_ROW,
                        QuestText.title(quest), ignored -> {}, styles, "drag-" + id,
                        quest.leadingIcon(), quest.trailingIcon(), "", false, false, false,
                        false, "", null);
                preview.extraCss(".quest-button { background-color:#414c5d; opacity:1; }");
                previews.add(preview);
            }
            taskDragPreview = List.copyOf(previews);
        }
        if (!taskReorderDragging) return;
        if (!canAuthor() || !editMode) { cancelTaskReorder(); return; }
        if (inList(taskReorderX, taskReorderY)) {
            double edge = taskReorderY < listTop() + 24 ? taskReorderY - listTop() - 24
                    : taskReorderY > listBottom() - 24 ? taskReorderY - listBottom() + 24 : 0;
            listScroll = listTargetScroll = Math.clamp(listScroll + edge * dt * 12, 0, maxListScroll());
        }
        updateTaskDropTarget();
    }

    private void updateTaskDropTarget() {
        taskDropTarget = "";
        if (!taskReorderCompatible || !inList(taskReorderX, taskReorderY)) return;
        String group = ClientQuestDefinitions.groupOf(taskReorderCandidate);
        for (var row : visibleJournalRows()) {
            if (row.quest() == null || selectedTaskIds.contains(row.quest().id())
                    || !ClientQuestDefinitions.groupOf(row.quest().id()).equals(group)) continue;
            int y = listRowY(row);
            if (taskReorderY < y - 2 || taskReorderY >= y + row.height() + 2
                    || taskReorderY < groupClipTop(row.key()) || taskReorderY >= groupClipBottom(row.key())) continue;
            taskDropTarget = row.quest().id();
            taskDropAfter = taskReorderY >= y + row.height() / 2.0;
            break;
        }
    }

    private void cancelTaskReorder() {
        taskReorderCandidate = taskDropTarget = "";
        taskReorderDragging = false;
        taskReorderIds = List.of();
        taskDragPreview = List.of();
    }

    private void renderTaskReorder(GuiGraphics g, float delta) {
        if (!taskReorderDragging) return;
        var target = controls.get("quest:" + taskDropTarget);
        if (target != null) {
            int y = taskDropAfter ? target.getBottom() + 1 : target.getY() - 2;
            g.enableScissor(listLeft(), listTop(), listRight(), listBottom());
            g.fill(target.getX(), y, target.getRight(), y + 2, 0xFF85D6AA);
            g.disableScissor();
        }
        int x = Math.clamp((int) taskReorderX + 12, 0, Math.max(0, width - listWidth));
        int y = Math.clamp((int) taskReorderY + 12, 0, Math.max(0, height - 86));
        for (int i = taskDragPreview.size() - 1; i > 0; i--) {
            var preview = taskDragPreview.get(i);
            g.fill(x + i * 3, y + i * 6, x + i * 3 + preview.getWidth(),
                    y + i * 6 + preview.getHeight(), 0xFF374353);
            g.renderOutline(x + i * 3, y + i * 6, preview.getWidth(), preview.getHeight(), 0xFF68758A);
        }
        if (!taskDragPreview.isEmpty()) {
            var preview = taskDragPreview.getFirst();
            preview.setPosition(x, y);
            preview.render(g, -10000, -10000, delta);
            g.renderOutline(x - 1, y - 1, preview.getWidth() + 2, preview.getHeight() + 2,
                    taskReorderCompatible ? 0xFFF2CF83 : 0xFFFF8888);
            if (taskReorderIds.size() > 1)
                g.drawString(font, Integer.toString(taskReorderIds.size()), x + preview.getWidth() - 18, y - 10,
                        0xFFFFFFFF, true);
        }
    }

    private double maxDetailScroll() {
        if (frameDetailMaximum != null) return frameDetailMaximum;
        if (selected == null) return 0;
        int contentBottom;
        if (!orderedElements(false).isEmpty()) {
            contentBottom = orderedElements(false).stream()
                    .filter(element -> enclosingScroll(element) == null)
                    .mapToInt(element -> canvasTop() + (int) Math.round(layout.position(element.id(), layout.gridSize(), 48).y()
                            * canvasScaleY() * (editMode ? canvasZoom : 1))
                            + displayHeight(element))
                    .max().orElse(canvasTop());
        } else {
            contentBottom = detailsTop() + JournalDetailRenderer.bodyHeight(font, selected,
                    Math.max(1, width - 14 - detailsLeft));
        }
        contentBottom += 12;
        contentBottom = Math.max(contentBottom, canvasTop()
                + (int) Math.round(taskHeight() * canvasScaleY() * (editMode ? canvasZoom : 1)));
        return frameDetailMaximum = (double) Math.max(0, contentBottom - detailContentBottom());
    }

    private void clampDetailScroll() {
        detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll));
        detailTargetScroll = Math.max(0, Math.min(maxDetailScroll(), detailTargetScroll));
        double contentWidth = taskWidth() * canvasScaleX() * (editMode ? canvasZoom : 1);
        double visibleX = Math.min(32, Math.min(canvasWidth(), contentWidth) / 2);
        double minX = editMode ? visibleX - contentWidth : Math.min(0, canvasWidth() - contentWidth);
        double maxX = editMode ? canvasWidth() - visibleX : 0;
        canvasPanX = Math.clamp(canvasPanX, minX, maxX);
        if (canvasPanEasing) canvasTargetPanX = Math.clamp(canvasTargetPanX, minX, maxX);
        if (editMode) {
            double contentHeight = taskHeight() * canvasScaleY() * canvasZoom;
            double visibleY = Math.min(32, Math.min(canvasHeight(), contentHeight) / 2);
            canvasPanY = Math.clamp(canvasPanY, visibleY - contentHeight, canvasHeight() - visibleY);
        }
    }

    private void animateDetails() {
        long now = System.nanoTime();
        if (detailScrollFrameNanos == 0) detailScrollFrameNanos = now;
        double dt = Math.min(0.05, Math.max(0, (now - detailScrollFrameNanos) / 1_000_000_000.0));
        detailScrollFrameNanos = now;
        if (!detailScrollbarDragging) {
            detailScroll += (detailTargetScroll - detailScroll) * -Math.expm1(-16 * dt);
            if (Math.abs(detailTargetScroll - detailScroll) < 0.02) detailScroll = detailTargetScroll;
        }
        if (canvasPanEasing) {
            canvasPanX += (canvasTargetPanX - canvasPanX) * -Math.expm1(-16 * dt);
            if (Math.abs(canvasTargetPanX - canvasPanX) < .02) {
                canvasPanX = canvasTargetPanX;
                canvasPanEasing = false;
            }
        }
        clampDetailScroll();
    }

    private int detailThumbHeight() {
        int viewport = detailContentBottom() - detailsTop();
        return Math.min(viewport, Math.max(20,
                (int) (viewport * (double) viewport / Math.max(1, viewport + maxDetailScroll()))));
    }

    private int detailThumbTop() {
        int viewport = detailContentBottom() - detailsTop();
        return detailsTop() + (int) Math.round(detailScroll / Math.max(1, maxDetailScroll())
                * (viewport - detailThumbHeight()));
    }

    private void ensureBuiltInElements() {
        if (selected != null && selected.presentation().authored()
                && (selected.presentation().controls().isEmpty() || selected.presentation().controls().stream()
                .anyMatch(control -> !control.id().startsWith("builtin:" + selectedId + ":")))) return;
        if (hasTemplateElements()) return;
        // Upgrade the old automatic text-only layout without touching authored task canvases.
        if (!layout.elements().containsKey(builtInId("objective"))) {
            for (String suffix : List.of("title", "type", "status", "progress", "description", "icon")) {
                String id = builtInId(suffix);
                var old = layout.elements().get(id);
                if (old != null && !old.attribute("journal_anchor", "").isBlank()) {
                    layout.elements().remove(id); layout.positions().remove(id); layout.parents().remove(id);
                }
            }
        }
        int textX = 10;
        int textWidth = Math.max(80, taskWidth() - 20);
        boolean changed = false;
        changed |= addBuiltIn("title", "quest_title", textX, 12, textWidth, 22,
                Map.of("textColor", "#ffffff", "textSize", "1.5", "textShadow", "true"));
        changed |= addBuiltIn("type", "quest_type", textX, 34, textWidth, 12,
                Map.of("textColor", "#b9c4d1", "gravity", "top|right"));
        changed |= addBuiltIn("divider", "relative", textX, 48, textWidth, 4,
                Map.of("journal_ornament", "true", "background", "transparent"));
        changed |= addBuiltIn("objective", "quest_objective", textX, 60, textWidth, 24,
                Map.of("background", "#55202836", "borderWidth", "1", "borderColor", "#22ffffff",
                        "paddingLeft", "6", "paddingRight", "6", "textColor", "#f4f1ea"));
        changed |= addBuiltIn("description", "quest_description", textX, 94, textWidth, 72,
                Map.of("layout_height", "-2", "textColor", "#e4eaf2",
                        "maxLines", "0"));
        if (changed && previewTemplate == null) {
            saveLayout(false);
        }
    }

    private boolean addBuiltIn(String suffix, String kind, int x, int y, int w, int h,
                               Map<String, String> attributes) {
        String id = builtInId(suffix);
        if (layout.elements().containsKey(id) || layout.isDeleted(id)) {
            return false;
        }
        Map<String, String> anchored = new LinkedHashMap<>(attributes);
        anchored.put("journal_anchor", suffix.equals("icon") ? "right" : "details");
        anchored.put("journal_auto_x", Integer.toString(x));
        anchored.put("journal_auto_y", Integer.toString(y));
        anchored.put("journal_offset_y", Integer.toString(y));
        layout.elements().put(id, new LayoutEditorState.Element(id, selectedId, kind, "", w, h, anchored));
        layout.position(id, x, y).size(w, h);
        return true;
    }

    private void updateBuiltInAnchors() {
        boolean changed = false;
        for (var element : List.copyOf(layout.elements().values())) {
            String anchor = element.attribute("journal_anchor", "");
            if (!isBuiltIn(element) || anchor.isEmpty()) continue;
            var position = layout.position(element.id(), layout.gridSize(), 48);
            int previousX = parseInteger(element.attribute("journal_auto_x", ""), position.x());
            int nextX = anchor.equals("right") ? Math.max(0, layout.taskCanvas(element.quest()).width() - 28) : 10;
            int previousY = parseInteger(element.attribute("journal_auto_y", ""), position.y());
            int nextY = element.attributes().containsKey("journal_offset_y")
                    ? parseInteger(element.attribute("journal_offset_y", "0"), 0) : position.y();
            Map<String, String> attributes = new LinkedHashMap<>(element.attributes());
            // A manual move or reparenting relinquishes automatic positioning.
            if (position.x() != previousX || position.y() != previousY || layout.parentOf(element.id()) != null) {
                attributes.remove("journal_anchor");
                attributes.remove("journal_auto_x");
                attributes.remove("journal_auto_y");
                attributes.remove("journal_offset_y");
            } else if (position.x() != nextX || position.y() != nextY) {
                layout.setPosition(element.id(), nextX, nextY);
                attributes.put("journal_auto_x", Integer.toString(nextX));
                attributes.put("journal_auto_y", Integer.toString(nextY));
            } else {
                continue;
            }
            layout.elements().put(element.id(), new LayoutEditorState.Element(element.id(), element.quest(),
                    element.kind(), element.value(), element.width(), element.height(), attributes));
            changed = true;
        }
        if (changed) saveLayout(false);
    }

    private String builtInId(String suffix) {
        return "builtin:" + selectedId + ":" + suffix;
    }

    private static boolean isBuiltIn(LayoutEditorState.Element element) {
        return element != null && element.kind().startsWith("quest_");
    }

    private ItemStack itemStack(String value) {
        if (QuestItemStackValue.encoded(value)) {
            return minecraft.player == null ? ItemStack.EMPTY
                    : QuestItemStackValue.decode(value, minecraft.player.registryAccess()).orElse(ItemStack.EMPTY);
        }
        var id = ResourceLocation.tryParse(value == null ? "" : value.strip());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(id);
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void renderItemStack(GuiGraphics graphics, ItemStack original, int x, int y,
                                        int width, int height, float alpha) {
        ItemStack stack = original.copy();
        stack.setCount(1);
        float size = Math.max(1, Math.min(width, height));
        graphics.pose().pushPose();
        graphics.pose().translate(x + (width - size) / 2F, y + (height - size) / 2F, 0);
        graphics.pose().scale(size / 16F, size / 16F, 1);
        graphics.setColor(1, 1, 1, alpha);
        graphics.renderItem(stack, 0, 0);
        graphics.setColor(1, 1, 1, 1);
        graphics.pose().popPose();
    }

    private static boolean isTemplateElement(LayoutEditorState.Element element) {
        return element != null && !element.attribute("template_owner", "").isBlank();
    }

    private boolean hasBuiltInElements() {
        return selected != null && (selected.presentation().authored()
                || layout.elements().containsKey(builtInId("title")) || hasTemplateElements());
    }

    private boolean hasTemplateElements() {
        return selected != null && ClientQuestDefinitions.templates().stream()
                .anyMatch(template -> template.id().equals(selected.templateId()));
    }

    private void syncSelectedTemplateLayout() {
        if (templatePending != null) return;
        appliedTemplate = null;
        templateBaseline = null;
        if (selected != null && selected.presentation().authored()) return;
        if (selected == null || selected.templateId().isBlank()) return;
        QuestTemplate template = ClientQuestDefinitions.templates().stream()
                .filter(value -> value.id().equals(selected.templateId())).findFirst().orElse(null);
        if (template == null) return;
        TemplateLayout.apply(layout, selected.id(), template, layout.gridSize(), 0, true);
        template.elements().stream().filter(e -> e.parent().isBlank())
                .forEach(e -> arrangeParent(TemplateLayout.id(selected.id(), template.id(), e.id())));
        appliedTemplate = template;
        templateBaseline = LayoutStore.restore(LayoutStore.snapshot(layout));
        templateHash = canAuthor() ? QuestTemplateStore.fingerprint(template.id()) : "";
    }

    private void initHierarchyPanel() {
        int x = 16;
        int y = 94;
        int w = listWidth;
        int rowY = y + 48;
        int maxRows = Math.max(1, (height - rowY - 58) / 20);
        addHierarchyTool("hierarchy-back", "\u21e4", x + 4, y + 24,
                () -> changeSelectedLayer(Integer.MIN_VALUE));
        addHierarchyTool("hierarchy-down", "\u2193", x + 28, y + 24,
                () -> changeSelectedLayer(-1));
        addHierarchyTool("hierarchy-up", "\u2191", x + 52, y + 24,
                () -> changeSelectedLayer(1));
        addHierarchyTool("hierarchy-front", "\u21e5", x + 76, y + 24,
                () -> changeSelectedLayer(Integer.MAX_VALUE));
        List<LayoutEditorState.Element> elements = hierarchyElements();
        hierarchyScroll = Math.max(0, Math.min(hierarchyScroll, Math.max(0, elements.size() - maxRows)));
        hierarchyMotion.limit(elements.size() * 20 - (height - 58 - rowY));
        hierarchyMotion.jump(hierarchyScroll * 20);
        for (int index = 0; index < elements.size(); index++) {
            LayoutEditorState.Element element = elements.get(index);
            int depth = layout.depth(element.id());
            String prefix = "  ".repeat(Math.min(6, depth));
            Component label = Component.literal(prefix + (index + 1) + "  ")
                    .append(text("layout." + element.kind()))
                    .append(Component.literal("  " + element.id()));
            StyledButton row = new StyledButton(x + 4, rowY + index * 20 - hierarchyMotion.pixels(), w - 16, 20, label,
                    ignored -> selectLayoutElement(element.id(), Screen.hasControlDown() || Screen.hasShiftDown()),
                    styles, "hierarchy-row-" + index, "hierarchy-row");
            if (selectedLayoutIds.contains(element.id())) {
                row.extraCss("button { border-color:#f2cf83; background-color:#3b4655; }");
            }
            hierarchyRows.put(element.id(), row);
            row.clipTo(x, rowY, x + w, height - 58);
            row.setTooltip(Tooltip.create(label));
            addRenderableWidget(row);
        }
        addRenderableWidget(new ScrollRail(x + w - 8, rowY, height - 58 - rowY, hierarchyMotion));
        animateHierarchy();
    }

    private void animateHierarchy() {
        if (!editMode || !hierarchyOpen || selected == null) return;
        hierarchyMotion.advance();
        hierarchyScroll = hierarchyMotion.pixels() / 20;
        int index = 0;
        for (var row : hierarchyRows.values()) {
            row.setY(142 + index++ * 20 - hierarchyMotion.pixels());
            row.visible = row.getBottom() > 142 && row.getY() < height - 58;
        }
    }

    private void addHierarchyTool(String id, String icon, int x, int y, Runnable action) {
        StyledButton button = new StyledButton(x, y, 20, 18, Component.literal(icon),
                ignored -> action.run(), styles, id, "toolbar-button");
        button.active = !selectedLayoutIds.isEmpty();
        button.setTooltip(Tooltip.create(text(switch (id) {
            case "hierarchy-back" -> "layer_back";
            case "hierarchy-down" -> "layer_down";
            case "hierarchy-up" -> "layer_up";
            default -> "layer_front";
        })));
        addRenderableWidget(button);
    }

    private List<LayoutEditorState.Element> hierarchyElements() {
        List<LayoutEditorState.Element> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (LayoutEditorState.Element element : layout.elements().values()) {
            if (element.quest().equals(selectedId) && layout.parentOf(element.id()) == null) {
                addHierarchyTree(element, result, visited);
            }
        }
        for (LayoutEditorState.Element element : layout.elements().values()) {
            if (element.quest().equals(selectedId) && !visited.contains(element.id())) {
                addHierarchyTree(element, result, visited);
            }
        }
        return result;
    }

    private void addHierarchyTree(LayoutEditorState.Element element, List<LayoutEditorState.Element> result,
                                  Set<String> visited) {
        if (!visited.add(element.id())) {
            return;
        }
        result.add(element);
        for (LayoutEditorState.Element child : layout.elements().values()) {
            if (child.quest().equals(selectedId) && element.id().equals(layout.parentOf(child.id()))) {
                addHierarchyTree(child, result, visited);
            }
        }
    }

    private void selectLayoutElement(String id, boolean additive) {
        if (!additive) {
            selectedLayoutIds.clear();
        }
        if (additive && selectedLayoutIds.contains(id)) {
            selectedLayoutIds.remove(id);
        } else {
            selectedLayoutIds.add(id);
        }
        selectedLayoutId = selectedLayoutIds.contains(id)
                ? id : selectedLayoutIds.stream().reduce((first, second) -> second).orElse(null);
        init();
    }

    private void changeSelectedLayer(int direction) {
        if (selectedLayoutIds.isEmpty()) {
            return;
        }
        pushUndo();
        if (direction == Integer.MIN_VALUE || direction == Integer.MAX_VALUE) {
            layout.moveLayerToEdge(selectedLayoutIds, direction == Integer.MAX_VALUE);
        } else {
            layout.moveLayer(selectedLayoutIds, direction);
        }
        saveLayout();
        init();
    }

    private void openSourceEditor() {
        if (selected == null || !canAuthor()) {
            return;
        }
        if (presentationWriter.busy()) {
            afterPublication = this::openSourceEditor;
            return;
        }
        var current = QuestPresentationClient.snapshot(selected.id(), layout);
        if (!current.equals(selected.presentation())) {
            presentationWriter.queue(selected.id(), layout);
            afterPublication = this::openSourceEditor;
            return;
        }
        try {
            var sourceScreen = new QuestSourceScreen(this, selected.id());
            form = null;
            iconPicker = null;
            minecraft.setScreen(sourceScreen);
        } catch (Exception exception) {
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn(
                    "Unable to open source editor for {}", selected.id(), exception);
            error = "create.error.no_source";
        }
    }

    private void toggleTrack() {
        if (selected != null && actionPending == null) {
            var view = ClientQuestProgress.view(selected.id());
            if (view != null && view.completed() && !ClientQuestProgress.isTracked(selected.id())) {
                actionMessage = "already_complete";
                return;
            }
            actionMessage = null;
            actionPending = ClientQuestProgress.toggleTracking(selected.id());
            init();
        }
    }

    private void openForm(String kind, String type) {
        if (!canAuthor()) {
            error = "create.error.permission";
            return;
        }
        form = new Form(kind, kind.equals("group") ? category : type);
        form.targetGroup = creationGroup(groupId);
        form.targetChapter = chapterForGroup(form.targetGroup);
        if (!form.authoring()) {
            form.x = Integer.toString(Math.max(0, logicalX(menuX)));
            form.y = Integer.toString(Math.max(0, logicalY(menuY)));
            form.propertyPage = "content";
            if (kind.equals("quest_rewards")) {
                form.layoutWidth = "-1";
                form.layoutHeight = "-2";
            } else if (kind.equals("item_display")) {
                form.layoutWidth = "24";
                form.layoutHeight = "24";
                form.description = "minecraft:oak_log";
            } else if (kind.equals("image")) {
                form.layoutWidth = "48";
                form.layoutHeight = "48";
            } else if (kind.equals("video")) {
                form.layoutWidth = "160";
                form.layoutHeight = "90";
            } else if (kind.equals("audio")) {
                form.layoutWidth = "180";
                form.layoutHeight = "24";
            } else if (kind.equals("button") || kind.equals("structure_enter")) {
                form.layoutWidth = "120";
                form.layoutHeight = "24";
                form.background = "#384b50";
                form.borderColor = "#79b9a5";
                form.borderWidth = "1";
                form.paddingLeft = form.paddingTop = "6";
                form.description = kind.equals("button") ? text("layout.button").getString() : "";
            } else if (kind.equals("structure_preview")) {
                form.layoutWidth = "160";
                form.layoutHeight = "120";
            } else if (kind.equals("scroll")) {
                form.layoutWidth = "180";
                form.layoutHeight = "100";
                form.orientation = "vertical";
                form.showControls = "true";
            } else if (kind.equals("text")) {
                form.layoutWidth = "-2";
                form.layoutHeight = "-2";
            } else if (kind.equals("quest_entry_text")) {
                form.layoutWidth = "-1";
                form.layoutHeight = "24";
                form.background = "#80000000";
                form.paddingLeft = form.paddingTop = form.paddingRight = form.paddingBottom = "4";
                form.entryIcon = "minecraft:paper";
                form.entryProgress = "0/1";
            } else if (!kind.equals("text")) {
                form.layoutHeight = "80";
            }
            if (kind.equals("item_display")) {
                int[] belowText = defaultItemDisplayPosition();
                form.x = Integer.toString(belowText[0]);
                form.y = Integer.toString(belowText[1]);
            }
        }
        error = null;
        init();
    }

    private int[] defaultItemDisplayPosition() {
        LayoutEditorState.Element anchor = layout.elements().values().stream()
                .filter(element -> element.quest().equals(selectedId)
                        && (element.kind().equals("quest_description") || isTextElement(element)))
                .max(Comparator.comparingInt(element ->
                        layout.position(element.id(), 0, 0).y() + elementHeight(element)))
                .orElse(null);
        if (anchor == null) return new int[]{layout.gridSize(), Math.max(0, layout.canvasHeight() - 40)};
        var position = layout.position(anchor.id(), layout.gridSize(), 48);
        return new int[]{Math.max(0, position.x()),
                Math.min(Math.max(0, layout.canvasHeight() - 24), position.y() + elementHeight(anchor) + 8)};
    }

    private void editProperties(String kind, String id) {
        try {
            Form next = new Form(kind, "normal");
            next.id = id;
            next.editing = true;
            String source = QuestRepository.source(id);
            if (source.isBlank()) {
                error = "create.error.no_source";
                return;
            }
            next.expectedHash = QuestRepository.fingerprint(source);
            if (kind.equals("group")) {
                var data = com.google.gson.JsonParser.parseString(source).getAsJsonObject();
                next.name = data.has("name") ? data.get("name").getAsString() : id;
                next.description = data.has("description") ? data.get("description").getAsString() : "";
                next.icon = data.has("icon") ? data.get("icon").getAsString() : "";
                var group = QuestRepository.groups().stream().filter(value -> value.id().equals(id))
                        .findFirst().orElseThrow();
                next.type = group.type();
                next.defaultUnlocked = group.defaultUnlocked();
                next.recommended = group.recommended();
                next.limitedReward = group.limitedReward();
                next.focusAvailable = group.focusAvailable();
                next.listAccent = group.listAccent();
                next.leadingIcon.load(group.leadingIcon());
                next.trailingIcon.load(group.trailingIcon());
                next.loadHotEvent(group.hotEvent());
            } else if (kind.equals("chapter")) {
                var data = com.google.gson.JsonParser.parseString(source).getAsJsonObject();
                next.name = data.get("name").getAsString();
                next.description = data.has("description") ? data.get("description").getAsString() : "";
                next.icon = data.has("icon") ? data.get("icon").getAsString() : "";
                next.journeyBackgrounds = new ArrayList<>(ClientQuestDefinitions.chapters().stream()
                        .filter(chapter -> chapter.id().equals(id)).findFirst()
                        .map(QuestCatalog.Chapter::backgrounds).orElse(List.of()));
                next.journeyAppearance = ClientQuestDefinitions.chapters().stream()
                        .filter(chapter -> chapter.id().equals(id)).findFirst()
                        .map(QuestCatalog.Chapter::journeyAppearance)
                        .orElse(xtdpotato.xero_quest_journal.core.model.JourneyAppearance.empty());
            } else {
                var quest = QuestRepository.parseDefinition(QuestRepository.fileOf(id), source);
                next.name = quest.title();
                next.journeyAppearance = quest.journeyAppearance();
                next.journeyVisible = quest.journeyVisible();
                next.description = quest.description();
                next.objective = quest.objective();
                next.originalObjective = quest.objective();
                next.icon = quest.icon();
                next.loadHotEvent(quest.hotEvent());
                next.legendaryImage = quest.legendary().image();
                next.legendarySubtitle = quest.legendary().subtitle();
                next.legendaryFilters = quest.legendary().filters().entrySet().stream()
                        .map(entry -> entry.getKey() + ":" + String.join(",", entry.getValue()))
                        .collect(java.util.stream.Collectors.joining(";"));
                next.leadingIcon.load(quest.leadingIcon());
                next.trailingIcon.load(quest.trailingIcon());
                next.type = quest.type();
                next.targetGroup = ClientQuestDefinitions.groupOf(id);
                next.targetChapter = chapterForGroup(next.targetGroup);
                if (!quest.triggers().isEmpty() && List.of("item_hold", "item_get", "item_submit")
                        .contains(quest.triggers().getFirst().type())) {
                    var trigger = quest.triggers().getFirst();
                    next.item = trigger.item().isBlank() ? next.item : trigger.item();
                    next.count = Integer.toString(trigger.count());
                }
                next.parents = String.join(",", quest.parents());
                next.defaultConditions = quest.triggers();
                next.defaultRewards = quest.rewards();
                if (!quest.rewards().isEmpty() && quest.rewards().getFirst().type().equals("item")) {
                    next.rewardItem = quest.rewards().getFirst().item();
                    next.rewardCount = Integer.toString(quest.rewards().getFirst().count());
                }
                next.rewardMode = quest.rewardMode();
                next.completionEvents = quest.completionPolicy().events();
                next.completionReenter = quest.completionPolicy().reenterStructure();
                next.completionExport = quest.completionPolicy().exportAcquiredItems();
                next.structureProgress = quest.completionPolicy().structureProgress();
                next.automaticRewardPopup = quest.completionPolicy().automaticRewardPopup();
                next.prerequisiteMode = quest.completionPolicy().prerequisiteMode();
                next.defaultUnlocked = quest.defaultUnlocked();
                next.hideWhenLocked = quest.hideWhenLocked();
                next.hidden = quest.hidden();
                next.triggersEnabled = quest.triggersEnabled();
                next.listAccent = quest.listAccent();
                next.recommended = quest.recommended();
                next.limitedReward = quest.limitedReward();
                next.complexConditions = quest.triggers().size() > 1 || quest.triggers().stream()
                        .anyMatch(t -> !t.children().isEmpty()
                                || !List.of("confirm", "item_get", "item_hold").contains(t.type()));
                next.complexRewards = !quest.rewards().isEmpty() && !quest.rewards().getFirst().type().equals("item");
                next.originalRules = next.ruleSignature();
            }
            next.originalDescription = next.description;
            if (next.originalObjective == null) next.originalObjective = next.objective;
            if (next.hasListIconPages()) next.propertyPage = "basic";
            form = next;
            error = null;
            init();
        } catch (Exception exception) {
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn(
                    "Unable to open {} properties for {}", kind, id, exception);
            error = "create.error.save";
        }
    }

    private void editElement(String id) {
        var element = layout.elements().get(id);
        if (element == null) return;
        var position = layout.position(id, 0, 0);
        Form next = new Form(element.kind(), "normal");
        next.editing = true;
        next.elementId = id;
        next.id = controlId(element);
        next.name = text("layout." + element.kind()).getString();
        next.description = elementDisplayValue(element);
        if (Set.of("item_display", "scroll").contains(element.kind())) next.propertyPage = "content";
        next.x = Integer.toString(position.x());
        next.y = Integer.toString(position.y());
        next.layoutWidth = element.attribute("layout_width", Integer.toString(element.width()));
        next.layoutHeight = element.attribute("layout_height", Integer.toString(element.height()));
        next.visibility = element.attribute("visibility", "visible");
        next.alpha = element.attribute("alpha", "1.0");
        next.layoutGravity = element.attributes().containsKey("layout_gravity")
                ? normalizeGravity(element.attribute("layout_gravity", "inherit")) : "inherit";
        next.marginLeft = element.attribute("layout_marginLeft", element.attribute("layout_margin", "0"));
        next.marginTop = element.attribute("layout_marginTop", element.attribute("layout_margin", "0"));
        next.marginRight = element.attribute("layout_marginRight", element.attribute("layout_margin", "0"));
        next.marginBottom = element.attribute("layout_marginBottom", element.attribute("layout_margin", "0"));
        next.gravity = normalizeGravity(element.attribute("gravity", "top|left"));
        next.textColor = element.attribute("textColor", "#ffffff");
        next.textStyle = element.attribute("textStyle", "normal");
        next.textSize = element.attribute("textSize", "1.0");
        next.maxLines = element.attribute("maxLines", "0");
        next.background = element.attribute("background", "transparent");
        next.paddingLeft = element.attribute("paddingLeft", element.attribute("padding", "0"));
        next.paddingTop = element.attribute("paddingTop", element.attribute("padding", "0"));
        next.paddingRight = element.attribute("paddingRight", element.attribute("padding", "0"));
        next.paddingBottom = element.attribute("paddingBottom", element.attribute("padding", "0"));
        next.borderColor = element.attribute("borderColor", "transparent");
        next.borderWidth = element.attribute("borderWidth", "0");
        for (String key : List.of("background", "borderColor", "borderWidth", "borderEnabled", "shape", "cornerRadius")) {
            for (String state : List.of("Hover", "Pressed")) {
                next.appearance.put(key + state, element.attribute(key + state, ""));
            }
        }
        next.appearance.put("borderEnabled", element.attribute("borderEnabled", "true"));
        next.appearance.put("shape", element.attribute("shape", "rectangle"));
        next.appearance.put("cornerRadius", element.attribute("cornerRadius", "0"));
        if (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind())) {
            next.buttonContent.replaceAll((key, value) -> element.attribute(key, value));
        }
        next.shadowColor = element.attribute("shadowColor", "#000000");
        next.shadowRadius = element.attribute("shadowRadius", "0");
        next.shadowDx = element.attribute("shadowDx", "0");
        next.shadowDy = element.attribute("shadowDy", "0");
        next.orientation = element.attribute("orientation", "vertical");
        next.spacing = element.attribute("spacing", "4");
        next.imageScaleType = element.attribute("scaleType", "fit_xy");
        next.autoplay = element.attribute("autoplay", "true");
        next.loop = element.attribute("loop", "false");
        next.muted = element.attribute("muted", "false");
        next.volume = element.attribute("volume", "1.0");
        next.showControls = element.attribute(element.kind().equals("scroll")
                ? "showScrollbar" : "showControls", "true");
        next.mediaDisplayText = element.attribute("displayText", "");
        next.entryIcon = element.attribute("entryIcon", "minecraft:paper");
        next.entryProgress = element.attribute("entryProgress", "0/1");
        form = next;
        error = null;
        init();
    }

    private void initForm() {
        int w = formWidth();
        int x = (width - w) / 2;
        int panelHeight = formPanelHeight();
        int y = Math.max(2, (height - panelHeight) / 2);
        if (form.kind.equals("canvas")) {
            labeledField("layout.task_width", form.layoutWidth, x + 12, y + 32, w - 24, 6,
                    value -> form.layoutWidth = value);
            labeledField("layout.task_height", form.layoutHeight, x + 12, y + 60, w - 24, 6,
                    value -> form.layoutHeight = value);
            addRenderableWidget(new StyledButton(x + 12, y + panelHeight - 28, (w - 30) / 2, 22,
                    text("save"), ignored -> saveForm(), styles, "create"));
            addRenderableWidget(new StyledButton(x + 18 + (w - 30) / 2, y + panelHeight - 28,
                    (w - 30) / 2, 22, Component.translatable("gui.cancel"),
                    ignored -> closeForm(), styles, "cancel"));
            return;
        }
        if (form.hasListIconPages()) {
            initAuthoringPropertyForm(x, y, w);
            return;
        }
        String idLabel = form.authoring() ? "create.id" : "layout.id";
        EditBox id = form.authoring()
                ? field(idLabel, form.id, x + 12, y + 28, w - 52, 64, value -> form.id = value)
                : labeledField(idLabel, form.id, x + 12, y + 28, w - 52, 64, value -> form.id = value);
        copyFieldButton(id, x + w - 36, y + 28, 24, 20, "copy-control-id");
        var editedElement = form.elementId == null ? null : layout.elements().get(form.elementId);
        id.setEditable((!form.editing || !form.authoring())
                && (editedElement == null || !isBuiltIn(editedElement) && !isTemplateElement(editedElement)));
        if (QuestRepository.idExists(form.id) && form.authoring() && !form.editing) {
            id.setTextColor(0xFFFF5555);
        }
        if (!form.authoring()) {
            initElementForm(x, y, w);
            return;
        }
        field("create.name", form.name, x + 12, y + 52, w - 24, 128, value -> form.name = value);
        field(form.authoring() ? "create.description" : "layout.value", form.description,
                x + 12, y + 76, w - 24, 4096, value -> form.description = value);
        if (form.kind.equals("task")) {
            iconField(x + 12, y + 100, w - 24);
        } else if (form.authoring()) {
            iconField(x + 12, y + 100, w - 24);
        }
        if (form.kind.equals("group")) {
            groupTypeChoice(x + 12, y + 124, w - 24);
            labeledChoice("create.default_unlocked", x + 12, y + 152, w - 24,
                    List.of("true", "false"), Boolean.toString(form.defaultUnlocked),
                    value -> text("list_icon.boolean." + value),
                    value -> form.defaultUnlocked = Boolean.parseBoolean(value), "default-unlocked");
            focusAvailabilitySwitch(x + 12, y + 180, w - 24);
            listAccentChoice(x + 12, y + 208, w - 24);
        }
        if (form.kind.equals("chapter")) {
            int half = (w - 30) / 2;
            addRenderableWidget(new StyledButton(x + 12, y + (form.editing ? 124 : 152), half, 20,
                    Component.translatable("screen.xero_quest_journal.journey.edit_backgrounds"),
                    ignored -> {
                        Form draft = form;
                        minecraft.setScreen(new JourneyBackgroundScreen(this, draft.journeyBackgrounds,
                                values -> draft.journeyBackgrounds = new ArrayList<>(values)));
                    }, styles, "journey-backgrounds", "toolbar-button"));
            addRenderableWidget(new StyledButton(x + 18 + half, y + (form.editing ? 124 : 152), half, 20,
                    Component.translatable("screen.xero_quest_journal.journey.appearance"),
                    ignored -> {
                        Form draft = form;
                        minecraft.setScreen(new JourneyAppearanceScreen(this, draft.journeyAppearance,
                                value -> draft.journeyAppearance = value)
                                .chapterContext(new QuestCatalog.Chapter(draft.id, draft.name, draft.description,
                                                draft.icon, draft.targetGroup, List.of(), draft.journeyBackgrounds,
                                                draft.journeyAppearance),
                                        ClientQuestDefinitions.all().stream().filter(quest ->
                                                draft.targetGroup.isBlank() || draft.targetGroup.equals(
                                                        ClientQuestDefinitions.groupOf(quest.id()))).toList()));
                    }, styles, "journey-appearance", "toolbar-button"));
        }
        if (form.kind.equals("task")) {
            field("create.objective", form.objective, x + 12, y + 124, w - 24, 512,
                    value -> form.objective = value);
            conditionDraftButton(x + 12, y + 152, w - 24);
            if (form.complexRewards) {
                var retained = new StyledButton(x + 12, y + 176, w - 80, 20, text("rewards.preserved"),
                        ignored -> {}, styles, "preserved-rewards");
                retained.active = false;
                addRenderableWidget(retained);
            } else {
                resourceField("create.reward", form.rewardItem, x + 12, y + 176, w - 136,
                        value -> form.rewardItem = value, "choose-reward-item", true);
                field("create.reward_count", form.rewardCount, x + w - 118, y + 176, 50, 7,
                        value -> form.rewardCount = value);
            }
            addRenderableWidget(new StyledChoiceButton<>(x + w - 62, y + 176, 50, 20, text("create.reward_mode"),
                    List.of("default", "manual", "automatic"), form.rewardMode,
                    value -> text("reward_mode." + value), value -> form.rewardMode = value, styles, "reward-mode"));
            labeledChoice("create.default_unlocked", x + 12, y + 204, w - 24,
                    List.of("true", "false"), Boolean.toString(form.defaultUnlocked),
                    value -> text("list_icon.boolean." + value),
                    value -> form.defaultUnlocked = Boolean.parseBoolean(value), "default-unlocked");
        }
        if (!form.editing && (form.kind.equals("task") || form.kind.equals("chapter"))) {
        int scopeY = y + (form.kind.equals("task") ? 260 : 124);
            List<String> groups = new ArrayList<>(List.of(""));
            ClientQuestDefinitions.groups().forEach(group -> groups.add(group.id()));
            labeledChoice("create.target_group", x + 12, scopeY, w - 24, groups, form.targetGroup,
                    value -> value.isEmpty() ? text(form.kind.equals("task") ? "create.ungrouped" : "create.select_group")
                            : Component.literal(ClientQuestDefinitions.groups().stream()
                            .filter(group -> group.id().equals(value)).findFirst().orElseThrow().name()),
                    value -> { form.targetGroup = value; form.targetChapter = ""; init(); }, "create-group");
            if (form.kind.equals("task")) {
                List<String> chapters = new ArrayList<>(List.of(""));
                ClientQuestDefinitions.chapters().stream().filter(chapter -> chapter.groupId().equals(form.targetGroup))
                        .forEach(chapter -> chapters.add(chapter.id()));
                labeledChoice("create.target_chapter", x + 12, scopeY + 24, w - 24, chapters, form.targetChapter,
                        value -> value.isEmpty() ? text("create.no_chapter") : Component.literal(ClientQuestDefinitions.chapters()
                                .stream().filter(chapter -> chapter.id().equals(value)).findFirst().orElseThrow().name()),
                        value -> form.targetChapter = value, "create-chapter");
            }
        }
        if (form.kind.equals("task")) {
            listAccentChoice(x + 12, y + 320, w - 24);
        }
        int buttonY = y + formPanelHeight() - 28;
        var save = new StyledButton(x + 12, buttonY, (w - 30) / 2, 22,
                text(pending == null ? form.editing ? "save" : "create.confirm" : "saving"),
                ignored -> saveForm(), styles, "create");
        save.active = pending == null && canAuthor();
        addRenderableWidget(save);
        var cancel = new StyledButton(x + 18 + (w - 30) / 2, buttonY, (w - 30) / 2, 22,
                Component.translatable("gui.cancel"), ignored -> closeForm(), styles, "cancel");
        cancel.active = pending == null;
        addRenderableWidget(cancel);
        if (pending != null) {
            children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).forEach(box -> box.setEditable(false));
        }
    }

    private void initAuthoringPropertyForm(int x, int y, int w) {
        List<String> pages = form.kind.equals("task")
                ? List.of("basic", "leading_icon", "trailing_icon", "hot_event", "legendary", "completed")
                : List.of("basic", "leading_icon", "trailing_icon", "hot_event");
        int tabWidth = (w - 28) / pages.size();
        for (int index = 0; index < pages.size(); index++) {
            String page = pages.get(index);
            StyledButton tab = new StyledButton(x + 12 + index * tabWidth, y + 28,
                    tabWidth - 2, 20, text("list_icon.page." + page), ignored -> {
                form.propertyPage = page;
                form.scroll = 0;
                init();
            }, styles, "authoring-page-" + page, "toolbar-button");
            tab.active = !form.propertyPage.equals(page);
            addRenderableWidget(tab);
        }
        if (form.propertyPage.equals("leading_icon")) {
            initListIconPage(x, y, w, form.leadingIcon, "leading");
        } else if (form.propertyPage.equals("trailing_icon")) {
            initListIconPage(x, y, w, form.trailingIcon, "trailing");
        } else if (form.propertyPage.equals("hot_event")) {
            initHotEventPage(x, y, w);
        } else if (form.propertyPage.equals("legendary")) {
            resourceField("legendary.image", form.legendaryImage, x + 12, y + 60, w - 24,
                    value -> form.legendaryImage = value, "choose-legendary-image", false);
            labeledField("legendary.subtitle", form.legendarySubtitle, x + 12, y + 96, w - 24, 128,
                    value -> form.legendarySubtitle = value);
            labeledField("legendary.filters", form.legendaryFilters, x + 12, y + 132, w - 24, 2048,
                    value -> form.legendaryFilters = value);
        } else if (form.propertyPage.equals("completed")) {
            initCompletedPage(x, y, w);
        } else {
            initAuthoringBasicPage(x, y, w);
        }
        addFormActions(x, y, w);
    }

    private void initCompletedPage(int x, int y, int w) {
        labeledChoice("completed.events", x + 12, y + 60, w - 24,
                List.of("false", "true"), Boolean.toString(form.completionEvents),
                value -> text("list_icon.boolean." + value),
                value -> form.completionEvents = Boolean.parseBoolean(value), "completed-events");
        labeledChoice("completed.reenter", x + 12, y + 100, w - 24,
                List.of("false", "true"), Boolean.toString(form.completionReenter),
                value -> text("list_icon.boolean." + value),
                value -> form.completionReenter = Boolean.parseBoolean(value), "completed-reenter");
        labeledChoice("completed.export", x + 12, y + 140, w - 24,
                List.of("false", "true"), Boolean.toString(form.completionExport),
                value -> text("list_icon.boolean." + value),
                value -> form.completionExport = Boolean.parseBoolean(value), "completed-export");
        labeledChoice("completed.structure_progress", x + 12, y + 180, w - 24,
                List.of("false", "true"), Boolean.toString(form.structureProgress),
                value -> text("list_icon.boolean." + value),
                value -> form.structureProgress = Boolean.parseBoolean(value), "structure-progress");
        labeledChoice("completed.reward_popup", x + 12, y + 220, w - 24,
                List.of("true", "false"), Boolean.toString(form.automaticRewardPopup),
                value -> text("list_icon.boolean." + value),
                value -> form.automaticRewardPopup = Boolean.parseBoolean(value), "automatic-reward-popup");
    }

    private void initHotEventPage(int x, int y, int w) {
        labeledChoice("hot_event.enabled", x + 12, y + 56, w - 24,
                List.of("true", "false"), Boolean.toString(form.hotEventEnabled),
                value -> text("list_icon.boolean." + value),
                value -> form.hotEventEnabled = Boolean.parseBoolean(value), "hot-event-enabled");
        labeledChoice("hot_event.show_image", x + 12, y + 84, w - 24,
                List.of("true", "false"), Boolean.toString(!form.hotEventHideImage),
                value -> text("list_icon.boolean." + value),
                value -> form.hotEventHideImage = !Boolean.parseBoolean(value), "hot-event-show-image");
        resourceField("hot_event.image", form.hotEventImage, x + 12, y + 112, w - 24,
                value -> form.hotEventImage = value, "choose-hot-event-image", false);
        labeledField("hot_event.title", form.hotEventTitle, x + 12, y + 140, w - 24, 128,
                value -> form.hotEventTitle = value);
        int half = (w - 30) / 2;
        labeledField("hot_event.image_width", form.hotEventImageWidth, x + 12, y + 168, half, 8,
                value -> form.hotEventImageWidth = value);
        labeledField("hot_event.image_height", form.hotEventImageHeight, x + 18 + half, y + 168, half, 8,
                value -> form.hotEventImageHeight = value);
        labeledField("hot_event.x", form.hotEventXText, x + 12, y + 196, half, 16,
                value -> form.hotEventXText = value);
        labeledField("hot_event.y", form.hotEventYText, x + 18 + half, y + 196, half, 16,
                value -> form.hotEventYText = value);
        labeledChoice("hot_event.positioned", x + 12, y + 224, w - 24,
                List.of("true", "false"), Boolean.toString(form.hotEventPositioned),
                value -> text("list_icon.boolean." + value),
                value -> form.hotEventPositioned = Boolean.parseBoolean(value), "hot-event-positioned");
    }

    private void initAuthoringBasicPage(int x, int y, int w) {
        EditBox id = field("create.id", form.id, x + 12, y + 56, w - 52, 64, value -> form.id = value);
        id.setEditable(false);
        copyFieldButton(id, x + w - 36, y + 56, 24, 20, "copy-task-id");
        field("create.name", form.name, x + 12, y + 80, w - 24, 128, value -> form.name = value);
        field("create.description", form.description, x + 12, y + 104, w - 24, 4096,
                value -> form.description = value);
        if (form.kind.equals("group")) {
            groupTypeChoice(x + 12, y + 128, w - 24);
            labeledChoice("create.default_unlocked", x + 12, y + 156, w - 24,
                    List.of("true", "false"), Boolean.toString(form.defaultUnlocked),
                    value -> text("list_icon.boolean." + value),
                    value -> form.defaultUnlocked = Boolean.parseBoolean(value), "default-unlocked");
            labeledChoice("create.recommended", x + 12, y + 184, w - 24,
                    List.of("true", "false"), Boolean.toString(form.recommended),
                    value -> text("list_icon.boolean." + value),
                    value -> form.recommended = Boolean.parseBoolean(value), "recommended");
            labeledChoice("create.limited_reward", x + 12, y + 212, w - 24,
                    List.of("true", "false"), Boolean.toString(form.limitedReward),
                    value -> text("list_icon.boolean." + value),
                    value -> form.limitedReward = Boolean.parseBoolean(value), "limited-reward");
            focusAvailabilitySwitch(x + 12, y + 240, w - 24);
            listAccentChoice(x + 12, y + 268, w - 24);
            return;
        }
        if (!form.kind.equals("task")) return;
        taskTypeChoice(x + 12, y + 128, w - 24);
        labeledField("create.objective", form.objective, x + 12, y + 156, w - 24, 512,
                value -> form.objective = value);
        conditionDraftButton(x + 12, y + 184, w - 24);
        rewardDraftButton(x + 12, y + 208, w - 24);
        labeledChoice("create.default_unlocked", x + 12, y + 232, w - 24,
                List.of("true", "false"), Boolean.toString(form.defaultUnlocked),
                value -> text("list_icon.boolean." + value),
                value -> form.defaultUnlocked = Boolean.parseBoolean(value), "default-unlocked");
        labeledChoice("create.recommended", x + 12, y + 260, w - 24,
                List.of("true", "false"), Boolean.toString(form.recommended),
                value -> text("list_icon.boolean." + value),
                value -> form.recommended = Boolean.parseBoolean(value), "recommended");
        labeledChoice("create.limited_reward", x + 12, y + 288, w - 24,
                List.of("true", "false"), Boolean.toString(form.limitedReward),
                value -> text("list_icon.boolean." + value),
                value -> form.limitedReward = Boolean.parseBoolean(value), "limited-reward");
        labeledChoice("create.hide_when_locked", x + 12, y + 316, w - 24,
                List.of("true", "false"), Boolean.toString(form.hideWhenLocked),
                value -> text("list_icon.boolean." + value),
                value -> form.hideWhenLocked = Boolean.parseBoolean(value), "hide-when-locked");
        labeledChoice("create.hidden", x + 12, y + 344, w - 24,
                List.of("true", "false"), Boolean.toString(form.hidden),
                value -> text("list_icon.boolean." + value),
                value -> form.hidden = Boolean.parseBoolean(value), "hidden-task");
        labeledChoice("create.triggers_enabled", x + 12, y + 372, w - 24,
                List.of("true", "false"), Boolean.toString(form.triggersEnabled),
                value -> text("list_icon.boolean." + value),
                value -> form.triggersEnabled = Boolean.parseBoolean(value), "triggers-enabled");
        listAccentChoice(x + 12, y + 436, w - 24);
        labeledChoice("journey.task_visible", x + 12, y + 404, w - 24,
                List.of("true", "false"), Boolean.toString(form.journeyVisible),
                value -> text("list_icon.boolean." + value),
                value -> form.journeyVisible = Boolean.parseBoolean(value), "journey-visible");
        Form draft = form;
        addRenderableWidget(new StyledButton(x + 12, y + 472, w - 24, 20, text("journey.task_appearance"),
                ignored -> minecraft.setScreen(JourneyAppearanceScreen.task(this, draft.journeyAppearance,
                        value -> draft.journeyAppearance = value)
                        .preview("branch", draft.icon, draft.name)
                        .preview("card", draft.hotEventImage.isBlank() ? draft.icon : draft.hotEventImage, draft.name)
                        .preview("subtitle", "", draft.legendarySubtitle)), styles, "journey-task-appearance", "toolbar-button"));
        labeledChoice("unlock_graph.prerequisite_mode", x + 12, y + 504, w - 24,
                List.of("all", "any"), form.prerequisiteMode,
                value -> text("unlock_graph.prerequisite_" + value), value -> form.prerequisiteMode = value,
                "task-prerequisite-mode");
    }

    private void listAccentChoice(int x, int y, int w) {
        labeledChoice("list_accent.title", x, y, w,
                xtdpotato.xero_quest_journal.core.model.QuestListAccent.VALUES, form.listAccent,
                value -> text("list_accent." + value), value -> form.listAccent = value, "list-accent");
    }

    private void focusAvailabilitySwitch(int x, int y, int w) {
        var focus = new QuestFocusButton(x, y, w, text("create.focus_available"),
                form.focusAvailable, ignored -> {
                    form.focusAvailable = !form.focusAvailable;
                    init();
                }, styles);
        addRenderableWidget(focus);
    }

    private void initListIconPage(int x, int y, int w, IconForm icon, String side) {
        labeledIconField("list_icon.image", icon.image, x + 12, y + 56, w - 24,
                value -> icon.image = value, side + "-image");
        labeledIconField("list_icon.tracked_image", icon.trackedImage, x + 12, y + 84, w - 24,
                value -> icon.trackedImage = value, side + "-tracked-image");
        labeledChoice("list_icon.visible", x + 12, y + 112, w - 24,
                List.of("true", "false"), Boolean.toString(icon.visible),
                value -> text("list_icon.boolean." + value),
                value -> icon.visible = Boolean.parseBoolean(value), side + "-visible");
        labeledChoice("list_icon.replace_tracked", x + 12, y + 140, w - 24,
                List.of("true", "false"), Boolean.toString(icon.replaceWhenTracked),
                value -> text("list_icon.boolean." + value),
                value -> icon.replaceWhenTracked = Boolean.parseBoolean(value), side + "-replace");
        labeledField("list_icon.alpha", icon.alpha, x + 12, y + 168, w - 24, 8,
                value -> icon.alpha = value);
        labeledField("list_icon.width", icon.width, x + 12, y + 196, w - 24, 8,
                value -> icon.width = value);
        labeledField("list_icon.height", icon.height, x + 12, y + 224, w - 24, 8,
                value -> icon.height = value);
        labeledField("list_icon.offset_x", icon.offsetX, x + 12, y + 252, w - 24, 8,
                value -> icon.offsetX = value);
        labeledField("list_icon.offset_y", icon.offsetY, x + 12, y + 280, w - 24, 8,
                value -> icon.offsetY = value);
        if (side.equals("trailing")) {
            labeledChoice("tracking_style.title", x + 12, y + 308, w - 24,
                    xtdpotato.xero_quest_journal.core.model.QuestTrackingStyle.VALUES, icon.trackingStyle,
                    value -> text("tracking_style." + value), value -> icon.trackingStyle = value,
                    "tracking-style");
        }
    }

    private void conditionDraftButton(int x, int y, int w) {
        Form draft = form;
        var button = new StyledButton(x, y, w, 20, text("conditions.add"),
                ignored -> minecraft.setScreen(new QuestConditionsScreen(this, draft.id,
                        draft.defaultConditions, conditions -> {
                            draft.defaultConditions = List.copyOf(conditions);
                            draft.conditionsEdited = true;
                        })), styles, "edit-form-conditions", "toolbar-button");
        button.active = pending == null && canAuthor();
        addRenderableWidget(button);
    }

    private void taskTypeChoice(int x, int y, int w) {
        List<String> values = categories().stream().filter(type -> !type.equals("active")).toList();
        if (!values.contains(form.type)) values = new ArrayList<>(values);
        if (!values.contains(form.type)) values.add(form.type);
        labeledChoice("create.type", x, y, w, values, form.type,
                this::categoryLabel, value -> form.type = value, "task-type");
    }

    private void rewardDraftButton(int x, int y, int w) {
        Form draft = form;
        var button = new StyledButton(x, y, w, 20, text("rewards.edit"),
                ignored -> minecraft.setScreen(new QuestRewardsScreen(this, draft.defaultRewards,
                        draft.rewardMode, (rewards, mode) -> {
                    draft.defaultRewards = List.copyOf(rewards);
                    draft.rewardMode = mode;
                    draft.rewardsEdited = true;
                })), styles, "edit-form-rewards", "toolbar-button");
        button.active = pending == null && canAuthor();
        addRenderableWidget(button);
    }

    private void groupTypeChoice(int x, int y, int width) {
        List<String> values = categories();
        if (!values.contains(form.type)) form.type = "active";
        labeledChoice("create.type", x, y, width, values, form.type,
                this::categoryLabel, value -> form.type = value, "group-type");
    }

    private void addFormActions(int x, int y, int w) {
        int buttonY = y + formPanelHeight() - 28;
        var save = new StyledButton(x + 12, buttonY, (w - 30) / 2, 22,
                text(pending == null ? form.editing ? "save" : "create.confirm" : "saving"),
                ignored -> saveForm(), styles, "create");
        save.active = pending == null && canAuthor();
        addRenderableWidget(save);
        var cancel = new StyledButton(x + 18 + (w - 30) / 2, buttonY, (w - 30) / 2, 22,
                Component.translatable("gui.cancel"), ignored -> closeForm(), styles, "cancel");
        cancel.active = pending == null;
        addRenderableWidget(cancel);
        if (pending != null) {
            children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .forEach(box -> box.setEditable(false));
        }
    }

    private int formPanelHeight() {
        if (form != null && form.kind.equals("canvas")) return Math.min(150, height - 10);
        if (form != null && !form.authoring()) {
            return Math.min(352, height - 10);
        }
        if (form != null && form.hasListIconPages()) return Math.min(352, height - 10);
        int size = form != null && form.kind.equals("task") ? (form.editing ? 212 : 344)
                : form != null && form.kind.equals("chapter") && !form.editing ? 198
                : form != null && form.kind.equals("group") ? 260 : 174;
        return Math.min(height - 10, size);
    }

    private int formWidth() {
        return form != null && !form.authoring() ? Math.min(430, width - 24) : Math.min(380, width - 32);
    }

    private void initElementForm(int x, int y, int w) {
        List<String> pages = List.of("layout", "spacing", "appearance", "content", "events");
        int tabWidth = (w - 28) / pages.size();
        for (int index = 0; index < pages.size(); index++) {
            String page = pages.get(index);
            StyledButton tab = new StyledButton(x + 12 + index * tabWidth, y + 52,
                    tabWidth - 2, 20, text("layout.page." + page), ignored -> {
                form.propertyPage = page;
                form.scroll = 0;
                init();
            }, styles, "layout-page-" + page, "toolbar-button");
            tab.active = !form.propertyPage.equals(page);
            addRenderableWidget(tab);
        }
        if (form.propertyPage.equals("events")) {
            addRenderableWidget(new StyledButton(x + 12, y + 80, w - 24, 22, text("graph.settings"),
                    ignored -> openFormTriggerGraph(), styles, "layout-events"));
        } else if (form.propertyPage.equals("layout")) {
            initElementLayoutPage(x, y, w);
        } else if (form.propertyPage.equals("spacing")) {
            initElementSpacingPage(x, y, w);
        } else if (form.propertyPage.equals("appearance")) {
            initElementAppearancePage(x, y, w);
        } else {
            initElementContentPage(x, y, w);
        }
        int buttonY = y + formPanelHeight() - 28;
        var save = new StyledButton(x + 12, buttonY, (w - 30) / 2, 22, text("save"),
                ignored -> saveForm(), styles, "create");
        addRenderableWidget(save);
        addRenderableWidget(new StyledButton(x + 18 + (w - 30) / 2, buttonY, (w - 30) / 2, 22,
                Component.translatable("gui.cancel"), ignored -> closeForm(), styles, "cancel"));
    }

    private int formTop() { return Math.max(2, (height - formPanelHeight()) / 2); }
    private int formBodyTop() {
        if (form.kind.equals("canvas")) return formTop() + 26;
        return formTop() + (form.hasListIconPages() ? 52 : form.authoring() ? 26 : 76);
    }
    private int formBodyBottom() { return formTop() + formPanelHeight() - 42; }
    private boolean inFormBody(double x, double y) {
        return formViewport().contains(x, y);
    }
    private Viewport formViewport() {
        int left = (width - formWidth()) / 2;
        return new Viewport(left + 8, formBodyTop(), left + formWidth() - 8, formBodyBottom());
    }
    @Override
    protected Viewport widgetViewport(AbstractWidget widget) {
        if (iconPicker != null && iconChoiceButtons.contains(widget))
            return viewport(16, iconPicker.gridTop, width - 16, iconPicker.gridBottom);
        return form != null && iconPicker == null && formWidgets.containsKey(widget) ? formViewport() : null;
    }
    private void prepareFormViewport() {
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.getY() >= formBodyTop()
                    && !(widget instanceof StyledButton button && Set.of("create", "cancel").contains(button.styleId()))) {
                formWidgets.put(widget, widget.getY());
            }
        }
        updateFormViewport();
        form.motion.limit(formMaxScroll());
        form.motion.jump(form.scroll);
    }
    private void updateFormViewport() {
        form.scroll = Math.max(0, Math.min(formMaxScroll(), form.scroll));
        form.motion.limit(formMaxScroll());
        if (Math.abs(form.motion.position() - form.scroll) > .01) form.motion.jump(form.scroll);
        for (var entry : formWidgets.entrySet()) {
            var widget = entry.getKey();
            widget.setY(entry.getValue() - (int) Math.round(form.scroll));
            widget.visible = widget.getBottom() > formBodyTop() && widget.getY() < formBodyBottom();
            if (!widget.visible && getFocused() == widget) setFocused(null);
        }
    }
    private int formThumbHeight() {
        int track = formBodyBottom() - formBodyTop();
        return Math.max(18, (int) (track * track / (track + formMaxScroll())));
    }
    private int formThumbTop() {
        return formBodyTop() + (int) ((formBodyBottom() - formBodyTop() - formThumbHeight())
                * form.scroll / Math.max(1, formMaxScroll()));
    }

    private double formMaxScroll() {
        return Math.max(0, formWidgets.entrySet().stream().mapToInt(entry ->
                entry.getValue() + entry.getKey().getHeight() + 4).max().orElse(formBodyBottom()) - formBodyBottom());
    }

    private void initElementLayoutPage(int x, int y, int w) {
        labeledField("layout.x", form.x, x + 12, y + 80, w - 24, 8, value -> form.x = value);
        labeledField("layout.y", form.y, x + 12, y + 108, w - 24, 8, value -> form.y = value);
        labeledField("layout.width", form.layoutWidth, x + 12, y + 136, w - 24, 32,
                value -> form.layoutWidth = value);
        labeledField("layout.height", form.layoutHeight, x + 12, y + 164, w - 24, 32,
                value -> form.layoutHeight = value);
        labeledChoice("layout.visibility", x + 12, y + 192, w - 24,
                List.of("visible", "invisible", "gone"), form.visibility,
                value -> text("layout.visibility." + value), value -> form.visibility = value,
                "layout-visibility");
        labeledField("layout.alpha", form.alpha, x + 12, y + 220, w - 24, 8,
                value -> form.alpha = value);
        gravityFlagsControl("layout.layout_gravity", form.layoutGravity, x + 12, y + 248, w - 24,
                true, value -> form.layoutGravity = value, "layout-layout-gravity");
    }

    private void initElementSpacingPage(int x, int y, int w) {
        labeledField("layout.margin_left", form.marginLeft, x + 12, y + 80, w - 24, 8,
                value -> form.marginLeft = value);
        labeledField("layout.margin_top", form.marginTop, x + 12, y + 108, w - 24, 8,
                value -> form.marginTop = value);
        labeledField("layout.margin_right", form.marginRight, x + 12, y + 136, w - 24, 8,
                value -> form.marginRight = value);
        labeledField("layout.margin_bottom", form.marginBottom, x + 12, y + 164, w - 24, 8,
                value -> form.marginBottom = value);
        labeledField("layout.padding_left", form.paddingLeft, x + 12, y + 192, w - 24, 8,
                value -> form.paddingLeft = value);
        labeledField("layout.padding_top", form.paddingTop, x + 12, y + 220, w - 24, 8,
                value -> form.paddingTop = value);
        labeledField("layout.padding_right", form.paddingRight, x + 12, y + 248, w - 24, 8,
                value -> form.paddingRight = value);
        labeledField("layout.padding_bottom", form.paddingBottom, x + 12, y + 276, w - 24, 8,
                value -> form.paddingBottom = value);
    }

    private void initElementAppearancePage(int x, int y, int w) {
        List<String> states = List.of("normal", "hover", "pressed");
        int stateWidth = (w - 24) / 3;
        for (int i = 0; i < states.size(); i++) {
            String state = states.get(i);
            var tab = new StyledButton(x + 12 + i * stateWidth, y + 80, stateWidth - 2, 20,
                    text("layout.state." + state), ignored -> {
                form.appearanceState = state;
                form.scroll = 0;
                init();
            }, styles, "layout-state-" + state, "toolbar-button");
            tab.active = !form.appearanceState.equals(state);
            addRenderableWidget(tab);
        }
        String suffix = form.appearanceState.equals("normal") ? "" : form.appearanceState.equals("hover") ? "Hover" : "Pressed";
        boolean normal = suffix.isEmpty();
        colorField("layout.background", normal ? form.background : form.appearance.getOrDefault("background" + suffix, "inherit"),
                x + 12, y + 108, w - 24, 512,
                value -> { if (normal) form.background = value; else form.appearance.put("background" + suffix, value); }, true);
        colorField("layout.border_color", normal ? form.borderColor : form.appearance.getOrDefault("borderColor" + suffix, "inherit"),
                x + 12, y + 136, w - 24, 16,
                value -> { if (normal) form.borderColor = value; else form.appearance.put("borderColor" + suffix, value); });
        labeledField("layout.border_width", normal ? form.borderWidth : form.appearance.getOrDefault("borderWidth" + suffix, ""),
                x + 12, y + 164, w - 24, 8,
                value -> { if (normal) form.borderWidth = value; else form.appearance.put("borderWidth" + suffix, value); });
        labeledField("layout.corner_radius", form.appearance.getOrDefault("cornerRadius" + suffix, normal ? "0" : ""),
                x + 12, y + 192, w - 24, 8, value -> form.appearance.put("cornerRadius" + suffix, value));
        labeledChoice("layout.shape", x + 12, y + 220, w - 24,
                List.of("inherit", "rectangle", "rounded", "pill", "ellipse"),
                form.appearance.getOrDefault("shape" + suffix, normal ? "rectangle" : "inherit"),
                value -> text("layout.shape." + value),
                value -> form.appearance.put("shape" + suffix, value), "layout-shape");
        labeledChoice("layout.border_enabled", x + 12, y + 248, w - 24,
                List.of("inherit", "true", "false"),
                form.appearance.getOrDefault("borderEnabled" + suffix, normal ? "true" : "inherit"),
                value -> text("layout.border_enabled." + value),
                value -> form.appearance.put("borderEnabled" + suffix, value), "layout-border-enabled");
        if (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(form.kind)) {
            buttonImageField(suffix, x + 12, y + 276, w - 24);
            colorField("layout.text_color", normal ? form.textColor : form.buttonContent.get("textColor" + suffix),
                    x + 12, y + 304, w - 24, 16,
                    value -> { if (normal) form.textColor = value; else form.buttonContent.put("textColor" + suffix, value); });
        }
    }

    private void buttonImageField(String suffix, int x, int y, int w) {
        String key = "buttonImage" + suffix;
        Form editing = form;
        resourceField("layout.button_image", editing.buttonContent.get(key), x, y,
                suffix.isEmpty() ? w : w - 28,
                value -> editing.buttonContent.put(key, value.isBlank() && !suffix.isEmpty() ? "none" : value),
                "button-image-choose", false);
        if (!suffix.isEmpty()) {
            var inherit = new StyledButton(x + w - 24, y, 24, 20, Component.literal("\u21ba"), ignored -> {
                editing.buttonContent.put(key, "inherit");
                init();
            }, styles, "button-image-inherit", "toolbar-button");
            inherit.setTooltip(Tooltip.create(text("layout.button_image_inherit")));
            addRenderableWidget(inherit);
        }
    }

    private void initElementContentPage(int x, int y, int w) {
        if (form.kind.equals("quest_rewards")) {
            addRenderableWidget(new StyledButton(x + 12, y + 80, w - 24, 20,
                    text("rewards.edit"), ignored -> openRewardsEditor(),
                    styles, "edit-rewards", "toolbar-button"));
            return;
        }
        var edited = form.elementId == null ? null : layout.elements().get(form.elementId);
        if (isBuiltIn(edited)) {
            formLabels.add(new FormLabel(text("layout.dynamic_content"), x + 12, y + 84, w - 24));
            addRenderableWidget(new StyledButton(x + 12, y + 108, w - 24, 20,
                    text("source"), ignored -> openSourceEditor(), styles, "source-from-properties",
                    "toolbar-button"));
            return;
        }
        if (form.kind.equals("item_display")) {
            Form editing = form;
            labeledField("layout.value", form.description, x + 12, y + 80, w - 52, 4096,
                    value -> form.description = value);
            var choose = new StyledButton(x + w - 36, y + 80, 24, 20, Component.literal("..."),
                    ignored -> {
                        iconPicker = new IconPicker(editing.description, value -> {
                            if (form != editing) return;
                            editing.description = value;
                        }, true);
                        init();
                    }, styles, "item-display-choose", "toolbar-button");
            choose.setTooltip(Tooltip.create(text("item_display.choose")));
            addRenderableWidget(choose);
            formLabels.add(new FormLabel(text("item_display.hint"), x + 12, y + 110, w - 24));
            return;
        }
        if (form.kind.equals("scroll")) {
            labeledChoice("layout.orientation", x + 12, y + 80, w - 24,
                    List.of("vertical", "horizontal"), form.orientation,
                    value -> text("layout.orientation." + value), value -> form.orientation = value,
                    "scroll-orientation");
            labeledChoice("layout.scrollbar", x + 12, y + 108, w - 24,
                    List.of("true", "false"), form.showControls,
                    value -> text("layout.boolean." + value), value -> form.showControls = value,
                    "scroll-scrollbar");
            labeledField("layout.spacing", form.spacing, x + 12, y + 136, w - 24, 8,
                    value -> form.spacing = value);
            return;
        }
        if (form.kind.equals("structure_preview") || form.kind.equals("structure_enter")) {
            labeledField("layout.value", form.description, x + 12, y + 80, w - 24, 4096,
                    value -> form.description = value);
            addRenderableWidget(new StyledButton(x + 12, y + 110, w - 24, 20, text("structure.choose"),
                    button -> minecraft.setScreen(new QuestStructurePickerScreen(this, value -> form.description = value)),
                    styles, "structure-choose", "toolbar-button"));
            if (form.kind.equals("structure_preview")) return;
            labeledField("layout.media_display_text", form.mediaDisplayText, x + 12, y + 140, w - 24, 512,
                    value -> form.mediaDisplayText = value);
            y += 36;
        }
        if (form.kind.equals("structure_enter")) {
            // The structure reference above is independent of the button caption.
        } else if (form.kind.equals("image")) {
            resourceField("layout.image_source", form.description, x + 12, y + 80, w - 24,
                    value -> form.description = value, "choose-image", false);
        } else if (form.kind.equals("video") || form.kind.equals("audio")) {
            ((SuggestedEditBox) labeledField("layout.media_display_text", form.mediaDisplayText,
                    x + 12, y + 80, w - 24, 512, value -> form.mediaDisplayText = value)).withoutMenu();
            ((SuggestedEditBox) labeledField("layout.media_source", form.description,
                    x + 12, y + 108, w - 52, 4096, value -> form.description = value)).withoutMenu();
            Form editing = form;
            var pick = new StyledButton(x + w - 36, y + 108, 24, 20, Component.literal("..."),
                    ignored -> QuestMediaPicker.choose(this, editing.kind.equals("video"), value -> {
                        if (form != editing) return;
                        form.description = value;
                        init();
                    }), styles, "media-choose", "toolbar-button");
            pick.setTooltip(Tooltip.create(text("layout.choose_media")));
            addRenderableWidget(pick);
        } else if (form.kind.equals("quest_entry_text")) {
            resourceField("layout.entry_icon", form.entryIcon, x + 12, y + 80, w - 24,
                    value -> form.entryIcon = value, "choose-entry-icon", false);
            labeledField("layout.value", form.description, x + 12, y + 108, w - 24, 4096,
                    value -> form.description = value);
            labeledField("layout.entry_progress", form.entryProgress, x + 12, y + 136, w - 24, 128,
                    value -> form.entryProgress = value);
            return;
        } else if (form.kind.equals("text") || form.kind.equals("button")) {
            labeledField("layout.value", form.description, x + 12, y + 80, w - 52, 16384,
                    value -> form.description = value);
            Form editing = form;
            var expand = new StyledButton(x + w - 36, y + 80, 24, 20, Component.literal("\u2922"),
                    ignored -> minecraft.setScreen(new QuestTextEditorScreen(this, editing.description, value -> {
                        if (form != editing) return;
                        form.description = value;
                    })), styles, "rich-editor", "toolbar-button");
            expand.setTooltip(Tooltip.create(text("layout.rich_editor")));
            addRenderableWidget(expand);
        } else {
            labeledField("layout.value", form.description, x + 12, y + 80, w - 24, 4096,
                    value -> form.description = value);
        }
        if (form.kind.equals("text") || xtdpotato.xero_quest_journal.core.model.ControlProperties.button(form.kind)) {
            labeledChoice("layout.text_style", x + 12, y + 136, w - 24,
                    List.of("normal", "bold", "italic", "bold_italic"), form.textStyle,
                    value -> text("layout.text_style." + value), value -> form.textStyle = value,
                    "layout-text-style");
            labeledField("layout.text_size", form.textSize, x + 12, y + 164, w - 24, 8,
                    value -> form.textSize = value);
            colorField("layout.text_color", form.textColor, x + 12, y + 192, w - 24, 16,
                    value -> form.textColor = value);
            labeledField("layout.max_lines", form.maxLines, x + 12, y + 220, w - 24, 8,
                    value -> form.maxLines = value);
            colorField("layout.shadow_color", form.shadowColor, x + 12, y + 248, w - 24, 16,
                    value -> form.shadowColor = value);
            int third = (w - 32) / 3;
            compactLabeledField("layout.shadow_radius", form.shadowRadius, x + 12, y + 276, third, 8,
                    value -> form.shadowRadius = value);
            compactLabeledField("layout.shadow_dx", form.shadowDx, x + 16 + third, y + 276, third, 8,
                    value -> form.shadowDx = value);
            compactLabeledField("layout.shadow_dy", form.shadowDy, x + 20 + third * 2, y + 276,
                    third, 8, value -> form.shadowDy = value);
            if (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(form.kind)) {
                buttonImageField("", x + 12, y + 312, w - 24);
                labeledChoice("layout.button_image_position", x + 12, y + 340, w - 24,
                        List.of("left", "right", "top", "bottom", "only"), form.buttonContent.get("buttonImagePosition"),
                        value -> text("layout.button_image_position." + value),
                        value -> form.buttonContent.put("buttonImagePosition", value), "button-image-position");
                labeledField("layout.button_image_width", form.buttonContent.get("buttonImageWidth"),
                        x + 12, y + 368, w - 24, 3, value -> form.buttonContent.put("buttonImageWidth", value));
                labeledField("layout.button_image_height", form.buttonContent.get("buttonImageHeight"),
                        x + 12, y + 396, w - 24, 3, value -> form.buttonContent.put("buttonImageHeight", value));
                labeledField("layout.button_image_gap", form.buttonContent.get("buttonImageGap"),
                        x + 12, y + 424, w - 24, 3, value -> form.buttonContent.put("buttonImageGap", value));
            }
        } else if (form.kind.equals("image")) {
            labeledChoice("layout.scale_type", x + 12, y + 108, w - 24,
                    List.of("fit_xy", "fit_center", "center"), form.imageScaleType,
                    value -> text("layout.scale_type." + value), value -> form.imageScaleType = value,
                    "layout-scale-type");
        } else if (form.kind.equals("video")) {
            labeledChoice("layout.scale_type", x + 12, y + 136, w - 24,
                    List.of("fit_xy", "fit_center", "center"), form.imageScaleType,
                    value -> text("layout.scale_type." + value), value -> form.imageScaleType = value,
                    "layout-scale-type");
            mediaPlaybackFields(x, y + 164, w, true);
        } else if (form.kind.equals("audio")) {
            mediaPlaybackFields(x, y + 136, w, false);
        } else {
            labeledChoice("layout.orientation", x + 12, y + 108, w - 24,
                    List.of("vertical", "horizontal"), form.orientation,
                    value -> text("layout.orientation." + value), value -> form.orientation = value,
                    "layout-orientation");
            labeledField("layout.spacing", form.spacing, x + 12, y + 136, w - 24, 8,
                    value -> form.spacing = value);
            if (List.of("linear", "relative", "scroll").contains(form.kind)) {
                gravityFlagsControl("layout.gravity", form.gravity, x + 12, y + 164, w - 24,
                        false, value -> form.gravity = value, "layout-gravity");
            }
        }
    }

    private void mediaPlaybackFields(int x, int y, int w, boolean video) {
        labeledChoice("layout.autoplay", x + 12, y, w - 24,
                List.of("true", "false"), form.autoplay,
                value -> text("layout.boolean." + value), value -> form.autoplay = value,
                "layout-autoplay");
        labeledChoice("layout.loop", x + 12, y + 28, w - 24,
                List.of("true", "false"), form.loop,
                value -> text("layout.boolean." + value), value -> form.loop = value,
                "layout-loop");
        labeledChoice("layout.muted", x + 12, y + 56, w - 24,
                List.of("true", "false"), form.muted,
                value -> text("layout.boolean." + value), value -> form.muted = value,
                "layout-muted");
        labeledField("layout.volume", form.volume, x + 12, y + 84, w - 24, 8,
                value -> form.volume = value);
        if (!video) {
            labeledChoice("layout.show_controls", x + 12, y + 112, w - 24,
                    List.of("true", "false"), form.showControls,
                    value -> text("layout.boolean." + value), value -> form.showControls = value,
                    "layout-show-controls");
        }
    }

    private void gravityFlagsControl(String label, String initial, int x, int y, int w,
                                     boolean inheritable, java.util.function.Consumer<String> changed,
                                     String id) {
        formLabels.add(new FormLabel(text(label), x, y, w));
        int controlsY = y + 24;
        if (inheritable) {
            boolean inherits = initial.equals("inherit");
            StyledButton inherit = new StyledButton(x, controlsY, w, 20,
                    Component.literal((inherits ? "[x] " : "[ ] ")
                            + text("layout.gravity.inherit").getString()),
                    ignored -> {
                        changed.accept("inherit");
                        init();
                    }, styles, id + "-inherit", "toolbar-button");
            inherit.setSelectionHighlighted(inherits);
            addRenderableWidget(inherit);
            controlsY += 24;
        }
        Set<String> selectedFlags = new LinkedHashSet<>(gravityFlags(initial));
        int gap = 4;
        int columnWidth = (w - gap) / 2;
        for (int index = 0; index < GRAVITY_FLAGS.size(); index++) {
            String flag = GRAVITY_FLAGS.get(index);
            boolean selectedFlag = selectedFlags.contains(flag);
            int column = index % 2;
            int row = index / 2;
            StyledButton choice = new StyledButton(x + column * (columnWidth + gap),
                    controlsY + row * 22, columnWidth, 20,
                    Component.literal((selectedFlag ? "[x] " : "[ ] ")
                            + text("layout.gravity." + flag).getString()),
                    ignored -> {
                        String current = initial.equals("inherit") ? inheritedGravity(id) : initial;
                        Set<String> next = new LinkedHashSet<>(gravityFlags(current));
                        if (!next.remove(flag)) next.add(flag);
                        changed.accept(serializeGravity(next));
                        init();
                    }, styles, id + "-" + flag, "toolbar-button");
            choice.setSelectionHighlighted(selectedFlag);
            addRenderableWidget(choice);
        }
    }

    private String inheritedGravity(String controlId) {
        String parentId = null;
        if (controlId.equals("layout-layout-gravity")) {
            parentId = form.elementId == null ? elementParent : layout.parentOf(form.elementId);
        }
        LayoutEditorState.Element parent = parentId == null ? null : layout.elements().get(parentId);
        return parent == null ? "top|left" : parent.attribute("gravity", "top|left");
    }

    private void iconField(int x, int y, int w) {
        int clearWidth = 24;
        Component label = form.icon.isBlank()
                ? text("icon_picker.choose")
                : Component.literal(QuestIcons.displayName(form.icon));
        StyledButton choose = new StyledButton(x, y, Math.max(24, w - clearWidth - 4), 20, label,
                ignored -> {
                    iconPicker = new IconPicker(form.icon, value -> form.icon = value, false);
                    init();
                }, styles, "choose-icon", "toolbar-button");
        choose.leadingIcon(form.icon);
        choose.setTooltip(Tooltip.create(text("icon_picker.choose")));
        addRenderableWidget(choose);
        StyledButton clear = new StyledButton(x + w - clearWidth, y, clearWidth, 20, Component.literal("\u00d7"),
                ignored -> {
                    form.icon = "";
                    init();
                }, styles, "clear-icon", "toolbar-button");
        clear.active = !form.icon.isBlank();
        clear.setTooltip(Tooltip.create(text("icon_picker.clear")));
        addRenderableWidget(clear);
    }

    private void labeledIconField(String label, String value, int x, int y, int w,
                                  java.util.function.Consumer<String> changed, String id) {
        int labelWidth = Math.min(190, Math.max(110, w * 44 / 100));
        int clearWidth = 24;
        formLabels.add(new FormLabel(text(label), x, y, labelWidth - 6));
        int fieldX = x + labelWidth;
        int fieldWidth = Math.max(24, w - labelWidth - clearWidth - 4);
        Component display = value == null || value.isBlank()
                ? text("icon_picker.choose") : Component.literal(QuestIcons.displayName(value));
        StyledButton choose = new StyledButton(fieldX, y, fieldWidth, 20, display,
                ignored -> {
                    iconPicker = new IconPicker(value, changed, false);
                    init();
                }, styles, id, "toolbar-button");
        choose.leadingIcon(value);
        choose.setTooltip(Tooltip.create(text(label)));
        addRenderableWidget(choose);
        StyledButton clear = new StyledButton(x + w - clearWidth, y, clearWidth, 20,
                Component.literal("\u00d7"), ignored -> {
            changed.accept("");
            init();
        }, styles, id + "-clear", "toolbar-button");
        clear.active = value != null && !value.isBlank();
        clear.setTooltip(Tooltip.create(text("icon_picker.clear")));
        addRenderableWidget(clear);
    }

    private void initIconPicker() {
        int w = width;
        int h = height;
        int x = 0, y = 0;
        iconPicker.sourceButtons.clear();
        List<IconSource> sources = iconPicker.itemOnly
                ? iconPicker.allowTags ? List.of(IconSource.INVENTORY, IconSource.ITEMS, IconSource.TAGS)
                    : List.of(IconSource.INVENTORY, IconSource.ITEMS)
                : List.of(IconSource.INVENTORY, IconSource.ITEMS, IconSource.TEXTURES, IconSource.LOCAL);
        int tabWidth = Math.max(50, (w - 32) / sources.size());
        int tabX = x + 16;
        for (IconSource source : sources) {
            StyledButton tab = new StyledButton(tabX, y + 42, tabWidth - 4, 26,
                    text("icon_picker." + source.key), ignored -> {
                        iconPicker.source = source;
                        iconPicker.scroll.jump(0);
                        init();
                    }, styles, "icon-source-" + source.key, "toolbar-button");
            tab.setSelectionHighlighted(iconPicker.source == source);
            tab.active = iconPicker.source != source;
            addRenderableWidget(tab);
            iconPicker.sourceButtons.add(tab);
            tabX += tabWidth;
        }
        int searchY = y + 76;
        EditBox search = field("icon_picker.search", iconPicker.search, x + 16, searchY,
                Math.max(80, w - 198), 128, value -> iconPicker.search = value);
        search.setResponder(value -> {
            iconPicker.search = value;
            iconPicker.scroll.jump(0);
            rebuildIconPickerResults(x, y, w, h);
        });
        JournalNavButton refresh = new JournalNavButton(x + w - 174, searchY, 24, 22, text("icon_picker.refresh"),
                ignored -> {
                    if (iconPicker.refreshPending == null) {
                        iconPicker.refreshPending = CompletableFuture.runAsync(QuestIcons::refreshChoices);
                        iconPicker.cachedChoices.clear();
                        iconPicker.boundValues.clear();
                        iconPicker.sortedInput = null;
                        iconPicker.loadingChoices = null;
                    }
                }, styles, "icon-filter", JournalNavButton.Kind.ICON, "refresh");
        refresh.setTooltip(Tooltip.create(text("icon_picker.refresh")));
        addRenderableWidget(refresh);
        var sort = new StyledChoiceButton<>(x + w - 144, searchY, 100, 22,
                text("icon_picker.sort"), List.of("id", "name", "created", "modified"), iconPicker.sort,
                value -> text("icon_picker.sort." + value), value -> {
                    iconPicker.sort = value;
                    iconPicker.scroll.jump(0);
                    rebuildIconPickerResults(x, y, w, h);
                }, styles, "icon-sort");
        sort.setTooltip(Tooltip.create(text("icon_picker.sort")));
        addRenderableWidget(sort);
        var order = new JournalNavButton(x + w - 40, searchY, 24, 22, text("icon_picker.order"),
                ignored -> {
                    iconPicker.descending = !iconPicker.descending;
                    iconPicker.scroll.jump(0);
                    rebuildIconPickerResults(x, y, w, h);
                    init();
                }, styles, "icon-sort-order", JournalNavButton.Kind.ICON,
                iconPicker.descending ? "sort_down" : "sort_up");
        order.setTooltip(Tooltip.create(text(iconPicker.descending
                ? "icon_picker.descending" : "icon_picker.ascending")));
        addRenderableWidget(order);
        int columns = Math.max(3, (w - 32) / 40);
        int rows = Math.max(1, (h - 212 + 39) / 40);
        iconPicker.columns = columns;
        iconPicker.visibleRows = rows;
        iconPicker.gridTop = y + 112;
        iconPicker.gridBottom = y + h - 100;
        int slotCount = columns * (rows + ICON_PICKER_OVERSCAN_ROWS * 2);
        for (int slot = 0; slot < slotCount; slot++) {
            int index = slot;
            int bx = x + 16 + slot % columns * 40;
            int by = y + 112 + slot / columns * 40;
            StyledButton icon = new StyledButton(bx, by, 36, 36, Component.empty(), ignored -> {
                int choiceIndex = iconPicker.firstRow() * iconPicker.columns + index;
                if (choiceIndex < iconPicker.filteredChoices.size()) {
                    iconPicker.manual = iconPicker.filteredChoices.get(choiceIndex).value();
                    updateIconPickerSelection();
                }
            }, styles, "icon-entry", "toolbar-button");
            icon.visible = false;
            addRenderableWidget(icon);
            iconChoiceButtons.add(icon);
        }
        iconPrevious = null;
        iconNext = null;
        addRenderableWidget(new ScrollRail(w - 14, iconPicker.gridTop,
                iconPicker.gridBottom - iconPicker.gridTop, iconPicker.scroll));
        iconPicker.sourceButtons.forEach(button ->
                button.active = !button.styleId().equals("icon-source-" + iconPicker.source.key));
        rebuildIconPickerResults(x, y, w, h);
        initIconPickerFooter(x, y, w, h);
    }

    private void rebuildIconPickerResults(int x, int y, int w, int h) {
        String normalizedSearch = iconPicker.search.strip().toLowerCase(Locale.ROOT);
        List<QuestIcons.IconChoice> available = readyIconChoices();
        if (available == null) {
            iconPicker.filteredChoices = List.of();
            updateIconPickerViewport();
            return;
        }
        List<QuestIcons.IconChoice> sorted = sortIconChoices(available);
        iconPicker.filteredChoices = normalizedSearch.isEmpty() ? sorted
                : sorted.stream()
                .filter(choice -> choice.matchesNormalized(normalizedSearch)).toList();
        int totalRows = Math.max(1, (iconPicker.filteredChoices.size() + iconPicker.columns - 1)
                / iconPicker.columns);
        iconPicker.scroll.limit(Math.max(0, totalRows * 40 - 4
                - (iconPicker.gridBottom - iconPicker.gridTop)));
        updateIconPickerViewport();
    }

    private List<QuestIcons.IconChoice> readyIconChoices() {
        List<QuestIcons.IconChoice> cached = iconPicker.cachedChoices.get(iconPicker.source);
        if (cached != null) return cached;
        if (iconPicker.source == IconSource.INVENTORY || iconPicker.source == IconSource.LOCAL)
            return iconChoices(iconPicker.source);
        if (iconPicker.loadingChoices == null || iconPicker.loadingSource != iconPicker.source) {
            IconSource source = iconPicker.source;
            iconPicker.loadingSource = source;
            iconPicker.loadingChoices = CompletableFuture.supplyAsync(() -> switch (source) {
                case ITEMS -> QuestIcons.itemChoices();
                case TAGS -> QuestIcons.tagChoices();
                case TEXTURES -> QuestIcons.textureChoices();
                default -> List.of();
            });
            return null;
        }
        if (!iconPicker.loadingChoices.isDone()) return null;
        try {
            List<QuestIcons.IconChoice> result = iconPicker.loadingChoices.join();
            iconPicker.cachedChoices.put(iconPicker.source, result);
            iconPicker.loadingChoices = null;
            return result;
        } catch (RuntimeException failure) {
            iconPicker.loadingChoices = null;
            error = "icon_picker.refresh_failed";
            return List.of();
        }
    }

    private void updateIconPickerViewport() {
        if (iconPicker == null || iconPicker.columns <= 0) return;
        int firstRow = iconPicker.firstRow();
        int offset = (int) Math.round(iconPicker.scroll.position() - iconPicker.firstRow() * 40.0);
        for (int slot = 0; slot < iconChoiceButtons.size(); slot++) {
            StyledButton icon = iconChoiceButtons.get(slot);
            int column = slot % iconPicker.columns;
            int row = slot / iconPicker.columns;
            int index = (firstRow + row) * iconPicker.columns + column;
            icon.setX(16 + column * 40);
            icon.setY(iconPicker.gridTop + row * 40 - offset);
            icon.clipTo(16, iconPicker.gridTop, width - 16, iconPicker.gridBottom);
            boolean inViewport = icon.getY() + 36 >= iconPicker.gridTop
                    && icon.getY() < iconPicker.gridBottom;
            boolean visible = index < iconPicker.filteredChoices.size() && inViewport;
            icon.visible = visible;
            if (visible) {
                QuestIcons.IconChoice choice = iconPicker.filteredChoices.get(index);
                if (!choice.value().equals(iconPicker.boundValues.get(icon))) {
                    icon.leadingIcon(choice.value());
                    icon.setTooltip(Tooltip.create(QuestIcons.metadataTooltip(choice.value())));
                    iconPicker.boundValues.put(icon, choice.value());
                }
                icon.setSelectionHighlighted(choice.value().equals(iconPicker.manual));
            }
        }
    }

    private List<QuestIcons.IconChoice> sortIconChoices(List<QuestIcons.IconChoice> choices) {
        String sortKey = iconPicker.source + ":" + iconPicker.sort + ":" + iconPicker.descending;
        if (iconPicker.sortedInput == choices && sortKey.equals(iconPicker.sortedKey)) return iconPicker.sortedChoices;
        boolean reverse = iconPicker.descending;
        Comparator<QuestIcons.IconChoice> comparator = switch (iconPicker.sort) {
            case "name" -> Comparator.comparing(QuestIcons.IconChoice::displayName,
                    (first, second) -> naturalCompareDirection(first, second, reverse));
            case "created", "modified" -> {
                boolean creation = iconPicker.sort.equals("created");
                Comparator<QuestIcons.IconChoice> time = (first, second) -> {
                    long a = QuestIcons.fileTimestamp(first.value(), creation);
                    long b = QuestIcons.fileTimestamp(second.value(), creation);
                    if (a < 0 || b < 0) {
                        if (a < 0 && b >= 0) return 1;
                        if (b < 0 && a >= 0) return -1;
                    }
                    return Long.compare(a, b);
                };
                yield time.thenComparing(QuestIcons.IconChoice::value, QuestScreen::naturalCompare);
            }
            default -> Comparator.comparing(QuestIcons.IconChoice::value,
                    (first, second) -> naturalCompareDirection(first, second, reverse));
        };
        if (iconPicker.descending && !iconPicker.sort.equals("id") && !iconPicker.sort.equals("name")) {
            comparator = (first, second) -> {
                long a = QuestIcons.fileTimestamp(first.value(), iconPicker.sort.equals("created"));
                long b = QuestIcons.fileTimestamp(second.value(), iconPicker.sort.equals("created"));
                if (a < 0 || b < 0) {
                    if (a < 0 && b >= 0) return 1;
                    if (b < 0 && a >= 0) return -1;
                }
                int time = Long.compare(b, a);
                return time == 0 ? naturalCompare(first.value(), second.value()) : time;
            };
        }
        iconPicker.sortedInput = choices;
        iconPicker.sortedKey = sortKey;
        iconPicker.sortedChoices = choices.stream().sorted(comparator).toList();
        return iconPicker.sortedChoices;
    }

    private static int naturalCompareDirection(String left, String right, boolean descending) {
        int leftClass = leadingSortClass(left), rightClass = leadingSortClass(right);
        if (leftClass != rightClass) return Integer.compare(leftClass, rightClass);
        int compared = naturalCompare(left, right);
        return descending ? -compared : compared;
    }

    private static int leadingSortClass(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isDigit(character)) return 0;
            if (Character.isLetter(character)) return 1;
        }
        return 2;
    }

    private static int naturalCompare(String left, String right) {
        int a = 0, b = 0;
        while (a < left.length() && b < right.length()) {
            char ca = Character.toLowerCase(left.charAt(a));
            char cb = Character.toLowerCase(right.charAt(b));
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int endA = a, endB = b;
                while (endA < left.length() && Character.isDigit(left.charAt(endA))) endA++;
                while (endB < right.length() && Character.isDigit(right.charAt(endB))) endB++;
                String numberA = left.substring(a, endA), numberB = right.substring(b, endB);
                int nonZeroA = 0, nonZeroB = 0;
                while (nonZeroA < numberA.length() - 1 && numberA.charAt(nonZeroA) == '0') nonZeroA++;
                while (nonZeroB < numberB.length() - 1 && numberB.charAt(nonZeroB) == '0') nonZeroB++;
                String significantA = numberA.substring(nonZeroA), significantB = numberB.substring(nonZeroB);
                int numeric = Integer.compare(significantA.length(), significantB.length());
                if (numeric == 0) numeric = significantA.compareTo(significantB);
                if (numeric != 0) return numeric;
                a = endA;
                b = endB;
                continue;
            }
            int classA = Character.isDigit(ca) ? 0 : Character.isLetter(ca) ? 1 : 2;
            int classB = Character.isDigit(cb) ? 0 : Character.isLetter(cb) ? 1 : 2;
            if (classA != classB) return Integer.compare(classA, classB);
            if (ca != cb) return Character.compare(ca, cb);
            a++;
            b++;
        }
        return Integer.compare(left.length() - a, right.length() - b);
    }

    private void updateIconPickerSelection() {
        var manual = formFields.get("icon_picker.manual");
        if (manual instanceof EditBox editBox && !editBox.getValue().equals(iconPicker.manual))
            editBox.setValue(iconPicker.manual);
        for (int slot = 0; slot < iconChoiceButtons.size(); slot++) {
            StyledButton button = iconChoiceButtons.get(slot);
            int choiceIndex = iconPicker.firstRow() * iconPicker.columns + slot;
            button.setSelectionHighlighted(button.visible && choiceIndex < iconPicker.filteredChoices.size()
                    && iconPicker.filteredChoices.get(choiceIndex).value().equals(iconPicker.manual));
        }
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals("icon-preview")).findFirst()
                .ifPresent(button -> button.active = !iconPicker.manual.isBlank());
    }

    private void initIconPickerFooter(int x, int y, int w, int h) {
        int footerY = y + h - 88;
        int blankWidth = Math.max(60, font.width(text("icon_picker.blank")) + 24);
        int folderWidth = iconPicker.source == IconSource.LOCAL
                ? Math.max(96, font.width(text("icon_picker.open_folder")) + 24) : 0;
        int folderX = x + w - 16 - folderWidth;
        int actionX = folderX - (folderWidth > 0 ? 8 : 0) - blankWidth;
        int manualWidth = Math.max(80, actionX - x - 28);
        int buttonWidth = Math.max(72, Math.max(font.width(text("icon_picker.apply")),
                font.width(Component.translatable("gui.cancel"))) + 24);
        int cancelX = x + w - 16 - buttonWidth;
        int applyX = cancelX - 8 - buttonWidth;
        field("icon_picker.manual", iconPicker.manual, x + 16, footerY,
                manualWidth, 8192, value -> {
                    iconPicker.manual = value;
                    children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                            .filter(button -> button.styleId().equals("icon-preview")).findFirst()
                            .ifPresent(button -> button.active = !value.isBlank());
                });
        addRenderableWidget(new StyledButton(applyX, y + h - 42, buttonWidth, 26,
                text("icon_picker.apply"), ignored -> {
                    String value = iconPicker.manual.strip();
                    var id = ResourceLocation.tryParse(value);
                    if (iconPicker.allowTags && validItemTag(value) || (iconPicker.itemOnly ? QuestItemStackValue.encoded(value)
                            ? minecraft.player != null && QuestItemStackValue
                            .decode(value, minecraft.player.registryAccess()).isPresent()
                            : id != null && BuiltInRegistries.ITEM.containsKey(id)
                            && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR
                            : QuestIcons.valid(value))) {
                        applyPickedResource(value);
                    } else {
                        error = "create.error.icon";
                    }
                }, styles, "icon-apply", "toolbar-button"));
        var preview = new JournalNavButton(x + 16, y + h - 42, 26, 26, text("icon_picker.preview"),
                ignored -> {
                    if (!iconPicker.manual.isBlank())
                        minecraft.setScreen(new QuestIconPreviewScreen(this, iconPicker.manual));
                }, styles, "icon-preview", JournalNavButton.Kind.ICON, "fullscreen");
        preview.active = !iconPicker.manual.isBlank();
        preview.setTooltip(Tooltip.create(text("icon_picker.preview")));
        addRenderableWidget(preview);
        if (iconPicker.source == IconSource.LOCAL)
            addRenderableWidget(new StyledButton(folderX, footerY, folderWidth, 22,
                    text("icon_picker.open_folder"), ignored ->
                    Util.getPlatform().openFile(QuestIcons.localRoot().toFile()),
                    styles, "icon-folder", "toolbar-button"));
        addRenderableWidget(new StyledButton(actionX, footerY, blankWidth, 22,
                text("icon_picker.blank"), ignored -> {
                    iconPicker.manual = "";
                    updateIconPickerSelection();
                }, styles, "icon-none", "toolbar-button"));
        addRenderableWidget(new StyledButton(cancelX, y + h - 42, buttonWidth, 26,
                Component.translatable("gui.cancel"), ignored -> {
                    if (externalPicker) {
                        onClose();
                        return;
                    }
                    iconPicker = null;
                    error = null;
                    init();
                }, styles, "icon-cancel", "toolbar-button"));
    }

    private List<QuestIcons.IconChoice> iconChoices(IconSource source) {
        if (source == IconSource.TAGS) return QuestIcons.tagChoices();
        if (source == IconSource.ITEMS) {
            return QuestIcons.itemChoices();
        }
        if (source == IconSource.TEXTURES) {
            return QuestIcons.textureChoices();
        }
        List<QuestIcons.IconChoice> cached = iconPicker.cachedChoices.get(source);
        if (cached != null) {
            return cached;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (source == IconSource.INVENTORY && minecraft.player != null) {
            var inventory = minecraft.player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    String serialized = QuestItemStackValue.encode(stack, minecraft.player.registryAccess());
                    if (!serialized.isBlank()) values.add(serialized);
                }
            }
        } else if (source == IconSource.LOCAL) {
            List<QuestIcons.IconChoice> choices = QuestIcons.localChoices();
            iconPicker.cachedChoices.put(source, choices);
            return choices;
        }
        List<QuestIcons.IconChoice> choices = values.stream().map(QuestIcons::choice).toList();
        iconPicker.cachedChoices.put(source, choices);
        return choices;
    }

    private static boolean validItemTag(String value) {
        if (!value.startsWith("#")) return false;
        var id = ResourceLocation.tryParse(value.substring(1));
        return id != null && BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, id)).isPresent();
    }

    private void applyPickedResource(String value) {
        var changed = iconPicker.changed;
        iconPicker = null;
        changed.accept(value);
        error = null;
        if (!externalPicker) init();
    }

    private void resourceField(String label, String value, int x, int y, int w,
                               java.util.function.Consumer<String> changed, String id, boolean itemOnly) {
        EditBox input = form.authoring() ? field(label, value, x, y, w - 28, 8192, changed)
                : labeledField(label, value, x, y, w - 28, 8192, changed);
        if (input instanceof SuggestedEditBox suggested) suggested.withoutMenu();
        var choose = new StyledButton(x + w - 24, y, 24, 20, Component.literal("..."),
                ignored -> {
                    iconPicker = new IconPicker(input.getValue(), changed, itemOnly);
                    if (!itemOnly) iconPicker.source = IconSource.TEXTURES;
                    init();
                }, styles, id, "toolbar-button");
        choose.setTooltip(Tooltip.create(text(itemOnly ? "item_picker.title" : "icon_picker.choose")));
        addRenderableWidget(choose);
    }

    private EditBox field(String label, String value, int x, int y, int w, int limit, java.util.function.Consumer<String> onChange) {
        var field = new SuggestedEditBox(this, font, x, y, Math.max(24, w), 20, text(label), label);
        if (label.startsWith("icon_picker.")) field.withoutMenu();
        field.setMaxLength(limit);
        field.setValue(value);
        field.setHint(text(label));
        field.setTooltip(Tooltip.create(text(label)));
        field.setResponder(next -> {
            onChange.accept(next);
            invalidFormFields.remove(label);
        });
        formFields.put(label, field);
        return addRenderableWidget(field);
    }

    private void copyFieldButton(EditBox field, int x, int y, int w, int h, String id) {
        StyledButton copy = new StyledButton(x, y, w, h, Component.literal("\u2398"),
                ignored -> {
                    copiedIdToast = field.getValue();
                    minecraft.keyboardHandler.setClipboard(copiedIdToast);
                    actionMessage = "id_copied";
                }, styles, id, "toolbar-button");
        copy.setTooltip(Tooltip.create(text("copy_id")));
        addRenderableWidget(copy);
    }

    private EditBox labeledField(String label, String value, int x, int y, int w, int limit,
                                 java.util.function.Consumer<String> onChange) {
        int labelWidth = Math.min(190, Math.max(110, w * 44 / 100));
        formLabels.add(new FormLabel(text(label), x, y, labelWidth - 6));
        EditBox input = field(label, value, x + labelWidth, y, w - labelWidth, limit, onChange);
        input.setHint(Component.empty());
        input.setTooltip(null);
        return input;
    }

    private EditBox compactLabeledField(String label, String value, int x, int y, int w, int limit,
                                        java.util.function.Consumer<String> onChange) {
        int labelWidth = Math.min(58, Math.max(34, w / 2));
        formLabels.add(new FormLabel(text(label), x, y, labelWidth - 4));
        EditBox input = field(label, value, x + labelWidth, y, w - labelWidth, limit, onChange);
        input.setHint(Component.empty());
        input.setTooltip(null);
        return input;
    }

    private void labeledChoice(String label, int x, int y, int w, List<String> values, String initial,
                               java.util.function.Function<String, Component> display,
                               java.util.function.Consumer<String> changed, String id) {
        int labelWidth = Math.min(190, Math.max(110, w * 44 / 100));
        formLabels.add(new FormLabel(text(label), x, y, labelWidth - 6));
        addRenderableWidget(new StyledChoiceButton<>(x + labelWidth, y, w - labelWidth, 20,
                text(label), values, initial, display, changed, styles, id));
    }

    private void colorField(String label, String value, int x, int y, int w, int limit,
                            java.util.function.Consumer<String> changed) {
        colorField(label, value, x, y, w, limit, changed, false);
    }

    private void colorField(String label, String value, int x, int y, int w, int limit,
                            java.util.function.Consumer<String> changed, boolean imagePicker) {
        int labelWidth = Math.min(130, Math.max(86, w / 3));
        int swatchWidth = 18;
        int clearWidth = Math.min(54, Math.max(42, w / 7));
        int pickerWidth = imagePicker ? 24 : 0;
        formLabels.add(new FormLabel(text(label), x, y, labelWidth - 6));
        int fieldX = x + labelWidth + swatchWidth + 4;
        int fieldWidth = Math.max(36, w - labelWidth - swatchWidth - clearWidth - pickerWidth
                - (imagePicker ? 14 : 10));
        String displayed = value == null || value.isBlank() ? "transparent" : value;
        changed.accept(displayed);
        EditBox input = field(label, displayed, fieldX, y, fieldWidth, limit, changed);
        input.setHint(Component.empty());
        input.setTooltip(null);
        colorPreviews.add(new ColorPreview(x + labelWidth, y + 2, 16, () -> input.getValue()));
        addRenderableWidget(new StyledButton(fieldX + fieldWidth + 4, y, clearWidth, 20,
                text("layout.color.transparent"), ignored -> input.setValue("transparent"),
                styles, "color-transparent", "toolbar-button"));
        if (imagePicker) {
            var choose = new StyledButton(fieldX + fieldWidth + clearWidth + 8, y, pickerWidth, 20,
                    Component.literal("..."), ignored -> {
                iconPicker = new IconPicker(input.getValue(), input::setValue, false);
                iconPicker.source = IconSource.TEXTURES;
                init();
            }, styles, "choose-background", "toolbar-button");
            choose.setTooltip(Tooltip.create(text("icon_picker.choose_texture")));
            addRenderableWidget(choose);
        }
    }

    private void saveForm() {
        if (form == null || pending != null || !canAuthor()) {
            return;
        }
        if (form.kind.equals("task") && xtdpotato.xero_quest_journal.core.runtime.QuestHoldingCapacity
                .impossible(minecraft.player, authoringConditions())) {
            error = "create.error.capacity";
            return;
        }
        if (form.kind.equals("canvas")) {
            try {
                var size = new LayoutEditorState.CanvasSize(Integer.parseInt(form.layoutWidth.strip()),
                        Integer.parseInt(form.layoutHeight.strip()));
                pushUndo();
                layout.setTaskCanvas(form.id, size.width(), size.height());
                saveLayout();
                clampDetailScroll();
                closeForm();
            } catch (IllegalArgumentException invalid) {
                error = "layout.error.canvas";
            }
            return;
        }
        if (!form.authoring()) {
            saveElementProperties();
            return;
        }
        int count;
        int rewardCount;
        try {
            count = Integer.parseInt(form.count);
            rewardCount = Integer.parseInt(form.rewardCount);
        } catch (NumberFormatException invalid) {
            error = "create.error.item";
            return;
        }
        ListIconConfig leadingIcon;
        ListIconConfig trailingIcon;
        try {
            leadingIcon = form.hasListIconPages()
                    ? form.leadingIcon.value() : ListIconConfig.leading(form.icon);
            trailingIcon = form.hasListIconPages()
                    ? form.trailingIcon.value() : ListIconConfig.trailing();
        } catch (IllegalArgumentException invalid) {
            error = "create.error.list_icon";
            return;
        }
        String legacyIcon = form.hasListIconPages() ? leadingIcon.image() : form.icon;
        if (!form.hotEventImage.isBlank() && !ClientMediaManager.validImageSource(form.hotEventImage)
                && !QuestIcons.valid(form.hotEventImage)) {
            error = "hot_event.invalid_image";
            form.propertyPage = "hot_event";
            init();
            return;
        }
        int hotX;
        int hotY;
        try {
            hotX = Integer.parseInt(form.hotEventXText.strip());
            hotY = Integer.parseInt(form.hotEventYText.strip());
        } catch (NumberFormatException invalid) {
            error = "hot_event.invalid_position";
            form.propertyPage = "hot_event";
            init();
            return;
        }
        HotEventData hotEvent;
        try {
            hotEvent = new HotEventData(form.hotEventEnabled, form.hotEventImage.strip(),
                    form.hotEventTitle.strip(), hotX, hotY, form.hotEventPositioned,
                    Integer.parseInt(form.hotEventImageWidth.strip()),
                    Integer.parseInt(form.hotEventImageHeight.strip()), form.hotEventHideImage);
        } catch (IllegalArgumentException invalid) {
            error = "hot_event.invalid_position";
            form.propertyPage = "hot_event";
            init();
            return;
        }
        xtdpotato.xero_quest_journal.core.model.LegendaryData legendary;
        try {
            var filters = new LinkedHashMap<String, List<String>>();
            for (String group : form.legendaryFilters.split(";", -1)) {
                if (group.isBlank()) continue;
                int separator = group.indexOf(':');
                if (separator < 1 || separator == group.length() - 1)
                    throw new IllegalArgumentException("Invalid filter group");
                String name = group.substring(0, separator).strip();
                if (filters.containsKey(name)) throw new IllegalArgumentException("Duplicate filter group");
                filters.put(name, Arrays.stream(group.substring(separator + 1).split(",", -1))
                        .map(String::strip).toList());
            }
            legendary = new xtdpotato.xero_quest_journal.core.model.LegendaryData(
                    form.legendaryImage, form.legendarySubtitle, filters);
            if (!legendary.image().isBlank() && !ClientMediaManager.validImageSource(legendary.image())
                    && !QuestIcons.valid(legendary.image()))
                throw new IllegalArgumentException("Invalid image");
        } catch (IllegalArgumentException invalid) {
            error = "legendary.invalid";
            form.propertyPage = "legendary";
            init();
            return;
        }
        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.valueOf(form.kind.toUpperCase(Locale.ROOT)),
                form.id.strip(), form.name.strip(), form.description, form.targetGroup,
                form.kind.equals("task") ? form.targetChapter : "", form.type, form.item, count, legacyIcon,
                form.parents, form.rewardItem, rewardCount, form.rewardMode, form.templateId,
                leadingIcon, trailingIcon, form.defaultConditions, form.defaultRewards, hotEvent,
                new xtdpotato.xero_quest_journal.core.model.CompletionPolicy(
                        form.completionEvents, form.completionReenter, form.completionExport,
                        form.structureProgress, form.automaticRewardPopup, form.prerequisiteMode),
                form.defaultUnlocked, form.recommended,
                form.hideWhenLocked, form.hidden, form.triggersEnabled, form.limitedReward,
                form.journeyBackgrounds, form.journeyAppearance, legendary,
                form.kind.equals("group") ? form.focusAvailable : null, form.listAccent,
                form.kind.equals("task") ? form.journeyVisible : null,
                form.kind.equals("task") && (!form.editing
                        || !Objects.equals(form.originalObjective, form.objective)) ? form.objective : null);
        if (!QuestIcons.valid(legacyIcon) || !QuestIcons.valid(leadingIcon.image())
                || !QuestIcons.valid(leadingIcon.trackedImage()) || !QuestIcons.valid(trailingIcon.image())
                || !QuestIcons.valid(trailingIcon.trackedImage())) {
            error = "create.error.icon";
            return;
        }
        var server = minecraft.getSingleplayerServer();
        UUID playerId = minecraft.player.getUUID();
        boolean editing = form.editing;
        boolean replaceDescription = !Objects.equals(form.originalDescription, form.description);
        boolean replaceRules = !Objects.equals(form.originalRules, form.ruleSignature());
        String expectedHash = form.expectedHash;
        boolean replaceConditions = form.conditionsEdited;
        boolean replaceRewards = form.rewardsEdited;
        QuestJournalOrderStore.Placement placement = form.placement;
        pending = CompletableFuture.supplyAsync(() -> editing
                ? QuestAuthoring.edit(server.getPlayerList().getPlayer(playerId), request, expectedHash,
                        replaceDescription, replaceRules, replaceConditions, replaceRewards)
                : QuestAuthoring.create(server.getPlayerList().getPlayer(playerId), request, placement), server);
        init();
    }

    private List<QuestDefinition.Trigger> authoringConditions() {
        if (!form.defaultConditions.isEmpty()) return form.defaultConditions;
        if (form.item.isBlank()) return List.of();
        try {
            return List.of(new QuestDefinition.Trigger("item_hold", form.item, Integer.parseInt(form.count)));
        } catch (IllegalArgumentException invalid) {
            return List.of();
        }
    }

    private List<xtdpotato.xero_quest_journal.core.runtime.QuestHoldingCapacity.Hint> authoringCapacityHints() {
        return authoringConditions().stream()
                .map(condition -> xtdpotato.xero_quest_journal.core.runtime.QuestHoldingCapacity
                        .hint(minecraft.player, condition))
                .filter(Objects::nonNull).toList();
    }

    private void saveElementProperties() {
        boolean creating = form.elementId == null;
        var old = creating ? new LayoutEditorState.Element(form.id, selectedId, form.kind, "", 140, 24)
                : layout.elements().get(form.elementId);
        if (old == null) {
            error = "create.error.save";
            return;
        }
        try {
            int x = Integer.parseInt(form.x.strip());
            int y = Integer.parseInt(form.y.strip());
            int width = parseDimension(form.layoutWidth, old.width());
            int height = parseDimension(form.layoutHeight, old.height());
            float textSize = parseTextScale(form.textSize);
            float alpha = Float.parseFloat(form.alpha);
            int marginLeft = nonNegative(form.marginLeft, 128);
            int marginTop = nonNegative(form.marginTop, 128);
            int marginRight = nonNegative(form.marginRight, 128);
            int marginBottom = nonNegative(form.marginBottom, 128);
            int paddingLeft = nonNegative(form.paddingLeft, 128);
            int paddingTop = nonNegative(form.paddingTop, 128);
            int paddingRight = nonNegative(form.paddingRight, 128);
            int paddingBottom = nonNegative(form.paddingBottom, 128);
            int borderWidth = Integer.parseInt(form.borderWidth);
            float shadowRadius = Float.parseFloat(form.shadowRadius);
            float volume = Float.parseFloat(form.volume);
            int shadowDx = Integer.parseInt(form.shadowDx);
            int shadowDy = Integer.parseInt(form.shadowDy);
            int maxLines = nonNegative(form.maxLines, 256);
            int spacing = nonNegative(form.spacing, 128);
            if (!Float.isFinite(textSize) || !Float.isFinite(alpha) || !Float.isFinite(shadowRadius)
                    || !Float.isFinite(volume)
                    || textSize < 0.25F || textSize > 8F || alpha < 0.0F || alpha > 1.0F
                    || volume < 0.0F || volume > 1.0F || borderWidth < 0 || borderWidth > 32
                    || shadowRadius < 0 || shadowRadius > 32) {
                throw new IllegalArgumentException("Layout value out of range");
            }
            parseColor(form.textColor, 0xFFFFFFFF);
            if (!validBackground(form.background)) {
                throw new IllegalArgumentException("Invalid background");
            }
            parseColor(form.borderColor, 0);
            for (String state : List.of("", "Hover", "Pressed")) {
                String radius = form.appearance.getOrDefault("cornerRadius" + state, "");
                String widthValue = form.appearance.getOrDefault("borderWidth" + state, "");
                if (!radius.isBlank() && nonNegative(radius, 128) > 128
                        || !widthValue.isBlank() && nonNegative(widthValue, 32) > 32)
                    throw new IllegalArgumentException("Invalid appearance");
                for (String key : List.of("background", "borderColor")) {
                    String color = form.appearance.getOrDefault(key + state, "");
                    if (!color.isBlank() && !color.equals("inherit")
                            && (key.equals("background") ? !validBackground(color)
                            : safeColor(color, Integer.MIN_VALUE) == Integer.MIN_VALUE))
                        throw new IllegalArgumentException("Invalid appearance color");
                }
            }
            parseColor(form.shadowColor, 0xFF000000);
            if (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(form.kind)) {
                for (var entry : form.buttonContent.entrySet()) {
                    String value = entry.getValue().strip();
                    xtdpotato.xero_quest_journal.core.runtime.QuestEventGraphs.validateProperty(entry.getKey(), value);
                    if (List.of("buttonImage", "buttonImageHover", "buttonImagePressed").contains(entry.getKey())
                            && ButtonControlContent.present(value) && !QuestIcons.valid(value))
                        throw new IllegalArgumentException("Invalid button image");
                }
            }
            if (form.kind.equals("image") && !QuestIcons.valid(form.description)) {
                throw new IllegalArgumentException("Invalid image");
            }
            if (form.kind.equals("item_display") && !validItemDisplayValue(form.description)) {
                throw new IllegalArgumentException("Invalid displayed item");
            }
            if (form.kind.equals("video") && !ClientMediaManager.validVideoSource(form.description)) {
                throw new IllegalArgumentException("Invalid video");
            }
            if (form.kind.equals("audio") && !ClientMediaManager.validAudioSource(form.description)) {
                throw new IllegalArgumentException("Invalid audio");
            }
            if (form.kind.equals("quest_entry_text") && !QuestIcons.valid(form.entryIcon)) {
                throw new IllegalArgumentException("Invalid entry icon");
            }
            boolean sourced = old.attributes().containsKey("journal_source_id");
            boolean fixedInternalId = !creating && (isBuiltIn(old) || isTemplateElement(old) || sourced);
            String id = fixedInternalId ? form.elementId : form.id.strip();
            if (fixedInternalId ? id == null || !layout.elements().containsKey(id)
                    : !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.:-]{0,127}")
                    || (!id.equals(form.elementId) && (layout.elements().containsKey(id)
                    || layout.positions().containsKey(id)))) {
                throw new IllegalArgumentException("Invalid or duplicate element ID");
            }
            pushUndo();
            if (!creating && !id.equals(form.elementId)) {
                layout.renameElement(form.elementId, id);
            }
            selectedLayoutId = id;
            Map<String, String> attributes = new LinkedHashMap<>(old.attributes());
            if (form.eventGraph != null) attributes.put("eventGraph", new com.google.gson.Gson().toJson(form.eventGraph));
            if (sourced) {
                String sourceId = form.id.strip();
                if (!sourceId.matches("[A-Za-z0-9_-][A-Za-z0-9_.:-]{0,127}")
                        || layout.elements().values().stream().anyMatch(e -> e.quest().equals(old.quest())
                        && !e.id().equals(old.id()) && QuestPresentationClient.sourceId(e).equals(sourceId)))
                    throw new IllegalArgumentException("Duplicate source control ID");
                attributes.put("journal_source_id", sourceId);
            }
            attributes.put("layout_width", form.layoutWidth.strip());
            attributes.put("layout_height", form.layoutHeight.strip());
            attributes.put("visibility", form.visibility);
            attributes.put("alpha", Float.toString(alpha));
            if (form.layoutGravity.equals("inherit")) {
                attributes.remove("layout_gravity");
            } else {
                attributes.put("layout_gravity", normalizeGravity(form.layoutGravity));
            }
            attributes.put("layout_marginLeft", Integer.toString(marginLeft));
            attributes.put("layout_marginTop", Integer.toString(marginTop));
            attributes.put("layout_marginRight", Integer.toString(marginRight));
            attributes.put("layout_marginBottom", Integer.toString(marginBottom));
            if (List.of("linear", "relative", "scroll").contains(form.kind)) {
                attributes.put("gravity", normalizeGravity(form.gravity));
            } else {
                attributes.remove("gravity");
            }
            attributes.put("textColor", form.textColor.strip());
            attributes.put("textStyle", form.textStyle);
            attributes.put("textSize", form.textSize.strip());
            attributes.put("maxLines", Integer.toString(maxLines));
            attributes.put("background", form.background.strip());
            attributes.put("paddingLeft", Integer.toString(paddingLeft));
            attributes.put("paddingTop", Integer.toString(paddingTop));
            attributes.put("paddingRight", Integer.toString(paddingRight));
            attributes.put("paddingBottom", Integer.toString(paddingBottom));
            attributes.put("borderColor", form.borderColor.strip());
            attributes.put("borderWidth", Integer.toString(borderWidth));
            for (var entry : form.appearance.entrySet()) {
                if (entry.getValue().isBlank() || entry.getValue().equals("inherit")) attributes.remove(entry.getKey());
                else attributes.put(entry.getKey(), entry.getValue());
            }
            if (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(form.kind)) {
                form.buttonContent.forEach((key, value) -> {
                    if (value.isBlank() || value.equals("inherit")) attributes.remove(key);
                    else attributes.put(key, value.strip());
                });
            }
            attributes.put("shadowColor", form.shadowColor.strip());
            attributes.put("shadowRadius", Float.toString(shadowRadius));
            attributes.put("shadowDx", Integer.toString(shadowDx));
            attributes.put("shadowDy", Integer.toString(shadowDy));
            attributes.put("orientation", form.orientation);
            attributes.put("spacing", Integer.toString(spacing));
            attributes.put("scaleType", form.imageScaleType);
            if (form.kind.equals("scroll")) attributes.put("showScrollbar", form.showControls);
            if (form.kind.equals("structure_enter")) attributes.put("displayText", form.mediaDisplayText);
            if (form.kind.equals("video") || form.kind.equals("audio")) {
                attributes.put("displayText", form.mediaDisplayText);
                attributes.put("autoplay", form.autoplay);
                attributes.put("loop", form.loop);
                attributes.put("muted", form.muted);
                attributes.put("volume", Float.toString(volume));
                attributes.put("showControls", form.showControls);
            }
            if (form.kind.equals("quest_entry_text")) {
                attributes.put("entryIcon", form.entryIcon.strip());
                attributes.put("entryProgress", form.entryProgress);
            }
            String value = isBuiltIn(old) ? old.value() : form.description;
            layout.elements().put(selectedLayoutId, new LayoutEditorState.Element(selectedLayoutId, old.quest(),
                    old.kind(), value, width, height, attributes));
            if (!creating) QuestPresentationClient.renameReferences(layout, old.quest(),
                    QuestPresentationClient.sourceId(old), sourced ? form.id.strip() : id);
            layout.setPosition(selectedLayoutId, x, y);
            if (creating && elementParent != null) {
                layout.setParent(selectedLayoutId, elementParent);
            }
            arrangeParent(selectedLayoutId);
            arrangeParent(layout.parentOf(selectedLayoutId));
            saveLayout();
            closeForm();
        } catch (Exception invalid) {
            error = "layout.error.properties";
        }
    }

    private static int nonNegative(String value, int maximum) {
        int parsed = Integer.parseInt(value.strip());
        if (parsed < 0 || parsed > maximum) {
            throw new IllegalArgumentException("Layout value out of range");
        }
        return parsed;
    }

    private static boolean validBackground(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            parseColor(value, 0);
            return true;
        } catch (Exception ignored) {
            return QuestIcons.valid(value);
        }
    }

    private boolean validItemDisplayValue(String value) {
        if (QuestItemStackValue.encoded(value)) {
            return minecraft.player != null && QuestItemStackValue
                    .decode(value, minecraft.player.registryAccess()).isPresent();
        }
        var id = ResourceLocation.tryParse(value == null ? "" : value.strip());
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR;
    }

    private static int parseDimension(String value, int fallback) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("wrap_content") || normalized.equals("match_parent")
                || normalized.equals("fill_parent") || normalized.equals("-1") || normalized.equals("-2")) {
            return fallback;
        }
        int parsed = dimensionPixels(normalized);
        if (parsed < 1 || parsed > 4096) {
            throw new IllegalArgumentException("Invalid layout dimension");
        }
        return parsed;
    }

    private static int dimensionPixels(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceFirst("(dp|px)$", "");
        double parsed = Double.parseDouble(normalized);
        if (!Double.isFinite(parsed) || parsed < 1 || parsed > 4096) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return (int) Math.round(parsed);
    }

    private static float parseTextScale(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("sp") || normalized.endsWith("px")) {
            return Float.parseFloat(normalized.substring(0, normalized.length() - 2)) / 9.0F;
        }
        return Float.parseFloat(normalized);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("transparent") || normalized.equals("none")) {
            return 0;
        }
        if (normalized.startsWith("#")) {
            String hex = normalized.substring(1);
            long parsed = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                return (int) (0xFF000000L | parsed);
            }
            if (hex.length() == 8) {
                return (int) parsed;
            }
        }
        throw new IllegalArgumentException("Invalid color");
    }

    private int elementWidth(LayoutEditorState.Element element) {
        String value = element.attribute("layout_width", Integer.toString(element.width()));
        if (isMatchParent(value)) {
            String parent = layout.parentOf(element.id());
            if (parent != null && layout.elements().containsKey(parent)) {
                var parentElement = layout.elements().get(parent);
                if (isWrapContent(parentElement.attribute("layout_width", ""))) {
                    return Math.max(1, element.width());
                }
                return Math.max(1, elementWidth(parentElement) - paddingLeft(parentElement)
                        - paddingRight(parentElement) - marginLeft(element) - marginRight(element));
            }
            return Math.max(1, layout.taskCanvas(element.quest()).width() - 8);
        }
        if (isWrapContent(value)) {
            return wrapContentWidth(element);
        }
        return Math.max(1, parseDimensionSafe(value, element.width()));
    }

    private int elementHeight(LayoutEditorState.Element element) {
        String value = element.attribute("layout_height", Integer.toString(element.height()));
        if (isMatchParent(value)) {
            String parent = layout.parentOf(element.id());
            if (parent != null && layout.elements().containsKey(parent)) {
                var parentElement = layout.elements().get(parent);
                if (isWrapContent(parentElement.attribute("layout_height", ""))) {
                    return Math.max(1, element.height());
                }
                return Math.max(1, elementHeight(parentElement) - paddingTop(parentElement)
                        - paddingBottom(parentElement) - marginTop(element) - marginBottom(element));
            }
            return Math.max(1, layout.taskCanvas(element.quest()).height() - 6);
        }
        if (isWrapContent(value)) {
            return wrapContentHeight(element);
        }
        return Math.max(1, parseDimensionSafe(value, element.height()));
    }

    private int wrapContentWidth(LayoutEditorState.Element element) {
        if (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind()) && ButtonControlContent.hasImage(element)) {
            return ButtonControlContent.naturalSize(element, font, buttonLabel(element),
                    buttonTextScale(element), 100000).width() + paddingLeft(element) + paddingRight(element);
        }
        if (isTextElement(element)) {
            float scale = Math.max(0.25F, Math.min(8F,
                    parseTextScaleSafe(element.attribute("textSize", "1"), 1F)));
            int textWidth = Arrays.stream(elementDisplayValue(element).split("\\R", -1))
                    .map(line -> styledText(element, line)).mapToInt(font::width).max().orElse(0);
            return Math.max(1, Math.round(textWidth * scale) + paddingLeft(element) + paddingRight(element));
        }
        if (element.kind().equals("image") || element.kind().equals("item_display")) {
            return 16 + paddingLeft(element) + paddingRight(element);
        }
        if (element.kind().equals("video")) {
            return 160 + paddingLeft(element) + paddingRight(element);
        }
        if (element.kind().equals("audio")) {
            return 180 + paddingLeft(element) + paddingRight(element);
        }
        List<LayoutEditorState.Element> children = layout.elements().values().stream()
                .filter(child -> element.id().equals(layout.parentOf(child.id())))
                .filter(child -> !child.attribute("visibility", "visible").equals("gone")).toList();
        if (children.isEmpty()) {
            return Math.max(1, element.width());
        }
        int spacing = Math.max(0, parseInteger(element.attribute("spacing", "4"), 4));
        boolean horizontal = List.of("linear", "scroll").contains(element.kind())
                && element.attribute("orientation", "vertical").equals("horizontal");
        int content;
        if (horizontal) {
            content = children.stream().mapToInt(child -> widthForWrapParent(child)
                    + marginLeft(child) + marginRight(child)).sum() + spacing * (children.size() - 1);
        } else if (element.kind().equals("relative")) {
            int parentX = layout.position(element.id(), 0, 0).x();
            content = children.stream().mapToInt(child -> layout.position(child.id(), parentX, 0).x() - parentX
                    + widthForWrapParent(child) + marginRight(child)).max().orElse(0);
        } else {
            content = children.stream().mapToInt(child -> widthForWrapParent(child)
                    + marginLeft(child) + marginRight(child)).max().orElse(0);
        }
        return Math.max(1, paddingLeft(element) + content + paddingRight(element));
    }

    private int wrapContentHeight(LayoutEditorState.Element element) {
        if (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind()) && ButtonControlContent.hasImage(element)) {
            return ButtonControlContent.naturalSize(element, font, buttonLabel(element),
                    buttonTextScale(element), Math.max(1, elementWidth(element) - paddingLeft(element) - paddingRight(element)))
                    .height() + paddingTop(element) + paddingBottom(element);
        }
        if (element.kind().equals("quest_rewards")) {
            int columns = Math.max(1, (elementWidth(element) + 4) / RewardCardRenderer.COLUMN_STEP);
            int count = displayedRewards().size();
            return 14 + Math.max(1, (count + columns - 1) / columns) * RewardCardRenderer.ROW_STEP;
        }
        if (isTextElement(element)) {
            float scale = Math.max(0.25F, Math.min(8F,
                    parseTextScaleSafe(element.attribute("textSize", "1"), 1F)));
            int contentWidth = Math.max(1, elementWidth(element) - paddingLeft(element) - paddingRight(element));
            int availableWidth = Math.max(1, Math.round(contentWidth / scale));
            int lines = font.split(styledText(element, elementDisplayValue(element)), availableWidth).size();
            int maxLines = Math.max(0, parseInteger(element.attribute("maxLines", "0"), 0));
            if (maxLines > 0) {
                lines = Math.min(lines, maxLines);
            }
            return Math.max(1, Math.max(1, lines) * Math.round((font.lineHeight + 1) * scale)
                    + paddingTop(element) + paddingBottom(element));
        }
        if (element.kind().equals("image") || element.kind().equals("item_display")) {
            return 16 + paddingTop(element) + paddingBottom(element);
        }
        if (element.kind().equals("video")) {
            return 90 + paddingTop(element) + paddingBottom(element);
        }
        if (element.kind().equals("audio")) {
            return 24 + paddingTop(element) + paddingBottom(element);
        }
        List<LayoutEditorState.Element> children = layout.elements().values().stream()
                .filter(child -> element.id().equals(layout.parentOf(child.id())))
                .filter(child -> !child.attribute("visibility", "visible").equals("gone")).toList();
        if (children.isEmpty()) {
            return Math.max(1, element.height());
        }
        int spacing = Math.max(0, parseInteger(element.attribute("spacing", "4"), 4));
        boolean vertical = List.of("linear", "scroll").contains(element.kind())
                && !element.attribute("orientation", "vertical").equals("horizontal");
        int content;
        if (vertical) {
            content = children.stream().mapToInt(child -> heightForWrapParent(child)
                    + marginTop(child) + marginBottom(child)).sum() + spacing * (children.size() - 1);
        } else if (element.kind().equals("relative")) {
            int parentY = layout.position(element.id(), 0, 0).y();
            content = children.stream().mapToInt(child -> layout.position(child.id(), 0, parentY).y() - parentY
                    + heightForWrapParent(child) + marginBottom(child)).max().orElse(0);
        } else {
            content = children.stream().mapToInt(child -> heightForWrapParent(child)
                    + marginTop(child) + marginBottom(child)).max().orElse(0);
        }
        return Math.max(1, paddingTop(element) + content + paddingBottom(element));
    }

    private int widthForWrapParent(LayoutEditorState.Element child) {
        return isMatchParent(child.attribute("layout_width", "")) ? child.width() : elementWidth(child);
    }

    private int heightForWrapParent(LayoutEditorState.Element child) {
        return isMatchParent(child.attribute("layout_height", "")) ? child.height() : elementHeight(child);
    }

    private static boolean isMatchParent(String value) {
        return value != null && (value.equalsIgnoreCase("match_parent")
                || value.equalsIgnoreCase("fill_parent") || value.strip().equals("-1"));
    }

    private static boolean isWrapContent(String value) {
        return value != null && (value.equalsIgnoreCase("wrap_content") || value.strip().equals("-2"));
    }

    private static int parseDimensionSafe(String value, int fallback) {
        try {
            return dimensionPixels(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int paddingLeft(LayoutEditorState.Element element) {
        return edge(element, "paddingLeft", "padding");
    }

    private static int paddingTop(LayoutEditorState.Element element) {
        return edge(element, "paddingTop", "padding");
    }

    private static int paddingRight(LayoutEditorState.Element element) {
        return edge(element, "paddingRight", "padding");
    }

    private static int paddingBottom(LayoutEditorState.Element element) {
        return edge(element, "paddingBottom", "padding");
    }

    private static int marginLeft(LayoutEditorState.Element element) {
        return edge(element, "layout_marginLeft", "layout_margin");
    }

    private static int marginTop(LayoutEditorState.Element element) {
        return edge(element, "layout_marginTop", "layout_margin");
    }

    private static int marginRight(LayoutEditorState.Element element) {
        return edge(element, "layout_marginRight", "layout_margin");
    }

    private static int marginBottom(LayoutEditorState.Element element) {
        return edge(element, "layout_marginBottom", "layout_margin");
    }

    private static int edge(LayoutEditorState.Element element, String name, String fallbackName) {
        return Math.max(0, parseInteger(element.attribute(name, element.attribute(fallbackName, "0")), 0));
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private void arrangeParent(String parentId) {
        if (parentId == null) {
            return;
        }
        var parent = layout.elements().get(parentId);
        if (parent == null) {
            return;
        }
        var parentPosition = layout.position(parentId, layout.gridSize(), 48);
        int contentX = parentPosition.x() + paddingLeft(parent);
        int contentY = parentPosition.y() + paddingTop(parent);
        int contentWidth = Math.max(1, elementWidth(parent) - paddingLeft(parent) - paddingRight(parent));
        int contentHeight = Math.max(1, elementHeight(parent) - paddingTop(parent) - paddingBottom(parent));
        int spacing = Math.max(0, parseInteger(parent.attribute("spacing", "4"), 4));
        boolean horizontal = parent.attribute("orientation", "vertical").equals("horizontal");
        String parentGravity = normalizeGravity(parent.attribute("gravity", "top|left"));
        List<LayoutEditorState.Element> children = layout.elements().values().stream()
                .filter(child -> parentId.equals(layout.parentOf(child.id())))
                .filter(child -> !child.attribute("visibility", "visible").equals("gone"))
                .toList();
        int stackWidth = horizontal
                ? children.stream().mapToInt(child -> marginLeft(child) + elementWidth(child)
                        + marginRight(child)).sum() + Math.max(0, children.size() - 1) * spacing
                : children.stream().mapToInt(child -> marginLeft(child) + elementWidth(child)
                        + marginRight(child)).max().orElse(0);
        int stackHeight = horizontal
                ? children.stream().mapToInt(child -> marginTop(child) + elementHeight(child)
                        + marginBottom(child)).max().orElse(0)
                : children.stream().mapToInt(child -> marginTop(child) + elementHeight(child)
                        + marginBottom(child)).sum() + Math.max(0, children.size() - 1) * spacing;
        int cursorX = alignedX(parentGravity, contentX, contentWidth, stackWidth, 0, 0);
        int cursorY = alignedY(parentGravity, contentY, contentHeight, stackHeight, 0, 0);
        for (var child : children) {
            int childWidth = elementWidth(child);
            int childHeight = elementHeight(child);
            String gravity = normalizeGravity(child.attributes().containsKey("layout_gravity")
                    ? child.attribute("layout_gravity", "top|left")
                    : parentGravity);
            if (parent.kind().equals("relative")) {
                int childX = alignedX(gravity, contentX, contentWidth, childWidth,
                        marginLeft(child), marginRight(child));
                int childY = alignedY(gravity, contentY, contentHeight, childHeight,
                        marginTop(child), marginBottom(child));
                layout.setPosition(child.id(), childX, childY);
            } else if (horizontal) {
                int childY = alignedY(gravity, contentY, contentHeight, childHeight,
                        marginTop(child), marginBottom(child));
                layout.setPosition(child.id(), cursorX + marginLeft(child), childY);
                cursorX += marginLeft(child) + childWidth + marginRight(child) + spacing;
            } else {
                int childX = alignedX(gravity, contentX, contentWidth, childWidth,
                        marginLeft(child), marginRight(child));
                layout.setPosition(child.id(), childX, cursorY + marginTop(child));
                cursorY += marginTop(child) + childHeight + marginBottom(child) + spacing;
            }
        }
    }

    private static int alignedX(String gravity, int start, int available, int size,
                                int leadingMargin, int trailingMargin) {
        int inner = Math.max(0, available - leadingMargin - trailingMargin);
        Set<String> flags = gravityFlags(gravity);
        if (flags.contains("left")) {
            return start + leadingMargin;
        }
        if (flags.contains("right")) {
            return start + available - trailingMargin - size;
        }
        if (flags.contains("center") || flags.contains("center_horizontal")) {
            return start + leadingMargin + Math.max(0, (inner - size) / 2);
        }
        return start + leadingMargin;
    }

    private static int alignedY(String gravity, int start, int available, int size,
                                int leadingMargin, int trailingMargin) {
        int inner = Math.max(0, available - leadingMargin - trailingMargin);
        Set<String> flags = gravityFlags(gravity);
        if (flags.contains("top")) {
            return start + leadingMargin;
        }
        if (flags.contains("bottom")) {
            return start + available - trailingMargin - size;
        }
        if (flags.contains("center") || flags.contains("center_vertical")) {
            return start + leadingMargin + Math.max(0, (inner - size) / 2);
        }
        return start + leadingMargin;
    }

    private static String normalizeGravity(String gravity) {
        return gravity != null && gravity.equals("inherit") ? "inherit"
                : serializeGravity(gravityFlags(gravity));
    }

    private static Set<String> gravityFlags(String gravity) {
        Set<String> flags = new LinkedHashSet<>();
        if (gravity == null || gravity.isBlank() || gravity.equals("inherit")) return flags;
        for (String value : gravity.toLowerCase(Locale.ROOT).split("[|,+\\s]+")) {
            switch (value) {
                case "left", "right", "top", "bottom", "center",
                        "center_vertical", "center_horizontal" -> flags.add(value);
                case "top_left" -> { flags.add("top"); flags.add("left"); }
                case "top_center" -> { flags.add("top"); flags.add("center_horizontal"); }
                case "top_right" -> { flags.add("top"); flags.add("right"); }
                case "center_left" -> { flags.add("center_vertical"); flags.add("left"); }
                case "center_right" -> { flags.add("center_vertical"); flags.add("right"); }
                case "bottom_left" -> { flags.add("bottom"); flags.add("left"); }
                case "bottom_center" -> { flags.add("bottom"); flags.add("center_horizontal"); }
                case "bottom_right" -> { flags.add("bottom"); flags.add("right"); }
                default -> { }
            }
        }
        return flags;
    }

    private static String serializeGravity(Set<String> flags) {
        return GRAVITY_FLAGS.stream().filter(flags::contains).reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private void closeForm() {
        if (pending == null) {
            iconPicker = null;
            form = null;
            if (hotEventPropertyOnly) closeTo(parent);
            else init();
        }
    }

    @Override
    public void tick() {
        updateActionMessageTimeout();
        if (iconPicker != null) {
            iconPicker.scroll.advance();
            updateIconPickerViewport();
            if (iconPicker.refreshPending != null && iconPicker.refreshPending.isDone()) {
                try {
                    iconPicker.refreshPending.join();
                    error = null;
                } catch (RuntimeException failure) {
                    error = "icon_picker.refresh_failed";
                }
                iconPicker.refreshPending = null;
                iconPicker.loadingChoices = null;
                init();
                return;
            }
            if (iconPicker.refreshPending == null && iconPicker.loadingChoices != null && iconPicker.loadingChoices.isDone()) {
                init();
                return;
            }
        }
        if (historyPending != null) {
            if (!historyPending.isDone()) return;
            try {
                var result = historyPending.join();
                if (result.successful()) {
                    if (ClientQuestDefinitions.find(result.id()) != null) selectedId = result.id();
                    revealSelectedPage = true;
                    selectedLayoutIds.clear(); selectedLayoutId = null;
                    selectedTaskIds.clear(); selectedGroupIds.clear();
                    undo.clear(); redo.clear();
                    error = null;
                    actionMessage = "history_" + (historyRedo ? "redone" : "undone");
                } else error = "history.error." + result.error();
            } catch (RuntimeException failure) { error = "history.error.save"; }
            historyPending = null;
            init();
            return;
        }
        if (orderPending != null && orderPending.isDone()) {
            try {
                String result = orderPending.join();
                if (result.equals("journal_order_saved")) {
                    error = null;
                    actionMessage = result;
                    if (!movingTask.isBlank()) {
                        selectedId = movingTask;
                        revealSelectedPage = true;
                        revealTaskRow = movingTask;
                    }
                } else error = "action." + result;
            } catch (RuntimeException failure) {
                error = "action.journal_order_failed";
            }
            orderPending = null;
            init();
            if (!movingTab.isBlank()) {
                ensureCategoryVisible(categories().indexOf(movingTab), categories().size() + 1);
                animateCategories();
            }
        }
        if (overviewMode && !isClosing()) {
            for (var child : List.copyOf(children()))
                if (child instanceof OverviewCategoryCard card) card.advanceActivation();
            if (isClosing() || minecraft.screen != this) return;
        }
        String publicationError = presentationWriter.takeError();
        if (publicationError != null) {
            error = publicationError.replace("screen.xero_quest_journal.", "");
            afterPublication = null;
        }
        if (afterPublication != null && !presentationWriter.busy()) {
            Runnable next = afterPublication;
            afterPublication = null;
            selected = ClientQuestDefinitions.find(selectedId);
            next.run();
            return;
        }
        styles.refreshIfChanged();
        if (typePending != null && typePending.isDone()) {
            try {
                String failure = typePending.join();
                if (failure.isEmpty()) {
                    if (category.equals(pendingTypeDelete)) category = "active";
                    actionMessage = "quest_type_deleted";
                    error = null;
                } else {
                    error = "quest_types.error." + failure;
                }
            } catch (RuntimeException failure) {
                error = "quest_types.error.save";
            }
            typePending = null;
            pendingTypeDelete = "";
            init();
        } else if (templatePending != null && templatePending.isDone()) {
            try {
                String failure = templatePending.join();
                if (!failure.isEmpty()) error = "template.error." + failure;
            } catch (RuntimeException failure) {
                error = "template.error.save";
            }
            templatePending = null;
            init();
        }
        if (adminAtInit != isAdmin() || authorAtInit != canAuthor()) init();
        if (batchPending != null && batchPending.isDone()) {
            try {
                var result = batchPending.join();
                if (result.successful()) {
                    copyTaskLayouts(result.copies());
                    selectedTaskIds.clear();
                    selectedTaskIds.addAll(result.copies().values());
                    if (!selectedTaskIds.isEmpty()) {
                        selectedId = selectedTaskIds.iterator().next();
                        revealSelectedPage = true;
                    }
                    actionMessage = result.copies().isEmpty() ? "deleted" : "pasted";
                    error = null;
                } else error = result.error();
            } catch (Exception failure) {
                xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn("Batch authoring failed", failure);
                error = "create.error.save";
            }
            batchPending = null;
            copiedLayout = null;
            init();
        } else if (pending != null && pending.isDone()) {
            QuestAuthoring.Result result;
            try {
                result = pending.join();
            } catch (Exception exception) {
                xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.error("Authoring operation failed", exception);
                result = new QuestAuthoring.Result("", "screen.xero_quest_journal.create.error.save");
            }
            pending = null;
            if (result.successful()) {
                if (!form.editing) {
                    switch (form.kind) {
                        case "group" -> {
                            groupId = overviewMode ? "" : result.id();
                            chapterId = "";
                            if (overviewMode) revealGroupRow = result.id();
                        }
                        case "chapter" -> { groupId = form.targetGroup; chapterId = result.id(); }
                        default -> {
                            if (!groupId.isEmpty()) groupId = form.targetGroup;
                            if (!chapterId.isEmpty()) chapterId = form.targetChapter;
                            selectedId = result.id();
                            revealSelectedPage = true;
                        }
                    }
                    if (overviewMode) {
                        category = "active";
                        groupId = chapterId = searchQuery = "";
                    }
                }
                form = null;
                error = null;
                if (hotEventPropertyOnly) {
                    closeTo(parent);
                    return;
                }
            } else {
                error = result.error().replace("screen.xero_quest_journal.", "");
                xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.info("Authoring rejected: {}", result.error());
            }
            init();
        } else if (actionPending != null && actionPending.isDone() && minecraft.screen == this) {
            PendingClaim claim = pendingClaim != null && pendingClaim.request() == actionPending
                    ? pendingClaim : null;
            pendingClaim = null;
            try {
                actionMessage = actionPending.join();
            } catch (Exception exception) {
                actionMessage = "save";
            }
            actionPending = null;
            if (claim != null && claim.overflowId() != null && actionMessage.equals("claimed"))
                pendingCompletedSelectionId = claim.overflowId();
            ClientQuestProgress.tick();
            init();
            if ("choice_required".equals(actionMessage) && claim != null && claim.overflowId() != null) {
                QuestDefinition quest = ClientQuestDefinitions.find(claim.overflowId());
                var view = ClientQuestProgress.view(claim.overflowId());
                if (quest != null && view != null) {
                    minecraft.setScreen(new QuestLootChoiceScreen(this, claim.overflowId(), claim.title(),
                            view.rewards()));
                }
            } else if ("inventory_full".equals(actionMessage) && claim != null && claim.overflowId() != null) {
                minecraft.setScreen(new QuestConfirmScreen(this, text("rewards.overflow"), text("claim"), () -> {
                    actionPending = ClientQuestProgress.claimOverflow(claim.overflowId());
                    pendingClaim = new PendingClaim(actionPending, claim.title(), null, claim.rewards());
                    init();
                }));
            } else if (claim != null && (actionMessage.equals("claimed") || actionMessage.equals("partially_claimed")
                    || actionMessage.equals("daily_claimed"))) {
                // A successful single claim is authoritative even when its progress snapshot arrives later.
                // Partial bulk claims still need the per-task snapshot to exclude undelivered rewards.
                var received = claim.overflowId() != null || actionMessage.equals("claimed")
                        || actionMessage.equals("daily_claimed")
                        ? claim.rewards().values().stream().flatMap(List::stream).toList()
                        : claim.rewards().entrySet().stream()
                        .filter(entry -> {
                            var view = ClientQuestProgress.view(entry.getKey());
                            return view != null && view.status().equals("claimed");
                        })
                        .flatMap(entry -> entry.getValue().stream()).toList();
                received = QuestRewardPopupScreen.receiptRewards(received);
                if (!received.isEmpty()) {
                    minecraft.setScreen(new QuestRewardPopupScreen(this, claim.title(), received));
                }
            }
        } else if (form == null && !moving && !listDragCandidate && !listScrollbarDragging && menu.isEmpty()
                && batchPending == null && !(overviewMode && overviewCardInteracting())
                && (revision != ClientQuestDefinitions.revision()
                || editMode && historyRevision != authoringHistory().revision()
                || progressRevision != ClientQuestProgress.listRevision())) {
            init();
        }
    }

    private void updateActionMessageTimeout() {
        if (!java.util.Objects.equals(actionMessage, timedActionMessage)) {
            timedActionMessage = actionMessage;
            actionMessageStartedAt = System.nanoTime();
        }
        if (!editMode && form == null && actionMessage != null && actionMessageStartedAt != 0
                && System.nanoTime() - actionMessageStartedAt >= 3_000_000_000L) {
            actionMessage = null;
            timedActionMessage = null;
            actionMessageStartedAt = 0;
        }
    }

    @Override protected boolean blurWorldBackground() { return !editMode; }

    @Override protected void renderBeforeBlur(GuiGraphics graphics, float delta) {
        if (overviewMode && !editMode)
            OverviewBackgroundScreen.renderImage(graphics, ClientQuestProgress.overviewImage(), width, height);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (iconPicker != null) {
            backdrop(g, 0xF0181C22);
            g.fill(0, 0, width, 38, 0xFF252C34);
            g.drawString(font, text(iconPicker.itemOnly ? "item_picker.title" : "icon_picker.title"),
                    16, 14, 0xFFF1D49A, false);
            g.drawString(font, Component.translatable(
                            "screen.xero_quest_journal.icon_picker.count", iconPicker.filteredChoices.size()),
                    Math.max(16, width - 170), 15, 0xFFB6C0CD, false);
            g.fill(0, height - 66, width, height, 0xFF252C34);
            if (!iconPicker.manual.isBlank()) {
                QuestIcons.render(g, iconPicker.manual, 16, height - 61, 16);
                g.drawString(font, font.plainSubstrByWidth(QuestIcons.displayName(iconPicker.manual),
                                Math.max(1, (error == null ? width : width / 2) - 64)),
                        40, height - 57, 0xFFE2E7EC, false);
            } else {
                g.drawString(font, text("icon_picker.blank"), 16, height - 57, 0xFF9DAAB8, false);
            }
            if (error != null) g.drawString(font, text(error), width / 2, height - 57, 0xFFFF7777);
            return;
        }
        if (form != null) {
            backdrop(g, 0xF01C2026);
            int w = formWidth();
            int x = (width - w) / 2;
            int panelHeight = formPanelHeight();
            int y = Math.max(2, (height - panelHeight) / 2);
            MailDecor.frame(g, x, y, w, panelHeight, false);
            g.drawString(font, text(form.kind.equals("canvas") ? "layout.task_size"
                    : form.editing ? "properties" : "add_" + form.kind), x + 12, y + 12, 0xFFFFFFFF);
            renderFormLabels(g);
            if (formMaxScroll() > 0) {
                    int trackX = x + w - 8;
                    g.fill(trackX, formBodyTop(), trackX + 4, formBodyBottom(), 0xFF303741);
                    g.fill(trackX, formThumbTop(), trackX + 4, formThumbTop() + formThumbHeight(), 0xFF9DB4D2);
            }
            if (error != null) {
                g.drawString(font, font.plainSubstrByWidth(text(error).getString(), w - 24),
                        x + 12, y + panelHeight - 39, 0xFFFF7777);
            } else if (form.kind.equals("task")) {
                try {
                    var hints = authoringCapacityHints();
                    Component warning = hints.stream().findFirst()
                            .map(xtdpotato.xero_quest_journal.core.runtime.QuestHoldingCapacity.Hint::message)
                            .orElse(null);
                    boolean impossible = xtdpotato.xero_quest_journal.core.runtime.QuestHoldingCapacity
                            .impossible(minecraft.player, authoringConditions());
                    children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                            .filter(button -> button.styleId().equals("create"))
                            .forEach(button -> {
                                button.active = pending == null && canAuthor() && !impossible;
                                button.setTooltip(impossible ? Tooltip.create(text("create.error.capacity")) : null);
                            });
                    if (warning != null) {
                        g.drawString(font, font.plainSubstrByWidth(warning.getString(), w - 24),
                                x + 12, y + panelHeight - 39, impossible ? 0xFFFF8F83 : 0xFFE8C774);
                        if (mouseX >= x + 12 && mouseX < x + w - 12
                                && mouseY >= y + panelHeight - 43 && mouseY < y + panelHeight - 29)
                            setTooltipForNextRenderPass(warning);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            return;
        }
        if (overviewMode && !editMode) {
            renderOverviewPresentation(g);
            return;
        }
        backdrop(g, editMode ? 0xEE1C2026 : JOURNAL_BACKDROP);
        g.fill(0, 0, width, headerHeight(), themeColor("header", editMode ? 0xF02A2D37 : JOURNAL_HEADER));
        JournalNavButton.drawIcon(g, overviewMode ? "overview" : "other", 16, headerControlY() + 1, 22,
                themeColor("accent", 0xFFF2D29A));
        int titleRight = !overviewMode && width >= 400 ? categoryViewportLeft() - 28
                : !overviewMode && !editMode ? width - taskNoticeHeaderInset() - 6
                : width - (hasClaimableTasks() ? 144 : 98);
        Component headerTitle = overviewMode ? title : categoryLabel(category);
        g.drawString(font, font.plainSubstrByWidth(headerTitle.getString(), Math.max(1, titleRight - 44)),
                44, headerControlY() + 8, themeColor("accent", 0xFFF2D29A), false);
        if (overviewMode && !editMode) {
            g.drawString(font, text("groups"), 16, 40, 0xFFCCD5DE);
            g.drawString(font, text("chapters"), 28 + (width - 44) / 2, 40, 0xFFCCD5DE);
            ClientQuestDefinitions.groups().stream().filter(group -> group.id().equals(groupId)).findFirst()
                    .ifPresent(group -> QuestIcons.render(g, group.icon(), 60, 37, 12));
            ClientQuestDefinitions.chapters().stream().filter(chapter -> chapter.id().equals(chapterId)).findFirst()
                    .ifPresent(chapter -> QuestIcons.render(g, chapter.icon(), 80 + (width - 44) / 2, 37, 12));
            g.drawString(font, text("tasks"), 16, searchY() - 12, 0xFFCCD5DE);
        }
        if (taskSearch != null && !(overviewMode && !editMode)) {
            int searchWidth = Math.max(80, listWidth - (overviewMode && editMode ? 26 : 0));
            g.fill(listLeft(), searchY(), listLeft() + searchWidth, searchY() + 20, 0x66202633);
            g.renderOutline(listLeft(), searchY(), searchWidth, 20, taskSearch.isFocused() ? 0xFFF3D38B : 0xFFA3A6AA);
            JournalNavButton.drawIcon(g, journalSearch.pending() ? "refresh" : "mail_search",
                    listLeft() + 5, searchY() + 3, 14,
                    journalSearch.pending() ? 0xFFF2CF83 : 0xFFB6BAC3);
        }
        if (listPages > 1 && listAvailable()) {
            String page = (listPage + 1) + "/" + listPages;
            g.drawString(font, page, listLeft() + (listWidth - font.width(page)) / 2,
                    listBottom() + 10, 0xFFCCD5DE, false);
        }
        int contentTop = detailsTop();
        if (!editMode) MailDecor.divider(g, 16, width - 16,
                height - (stackedFocusControl() ? 68 : 42), 0x408FA5AA);
        renderTaskDetails(g, mouseX, mouseY);
        if (editMode) {
            g.drawString(font, font.plainSubstrByWidth((actionMessage == null
                            ? text(systemLayout ? "system_layout" : "edit_mode") : actionText(actionMessage)).getString(),
                    Math.max(1, detailsLeft - 26)), 16, height - 46, 0xFFF2CF83);
            renderCoordinates(g);
        }
        if (!ClientQuestDefinitions.errors().isEmpty()) {
            g.drawString(font, text("definitions_error"), 16, height - 60, 0xFFFF7777);
        }
        if (ClientQuestProgress.syncError() != null) {
            g.drawString(font, text("action." + ClientQuestProgress.syncError()), 16, height - 60, 0xFFFF7777);
        }
        if (error != null) {
            g.drawString(font, font.plainSubstrByWidth(text(error).getString(), width - 32), 16, height - 60, 0xFFFF7777);
        }
        if (form == null && !editMode && actionMessage != null
                && !Set.of("focus_on", "focus_off").contains(actionMessage)) renderActionToast(g);
        if (editMode && hierarchyOpen && selected != null) {
            int panelX = 16;
            int panelY = 94;
            int panelBottom = height - 54;
            g.fill(panelX - 2, panelY - 2, panelX + listWidth + 2, panelBottom + 2, 0xFF9DB4D2);
            g.fill(panelX, panelY, panelX + listWidth, panelBottom, 0xFA1C2026);
            g.drawString(font, text("hierarchy"), panelX + 6, panelY + 7, 0xFFF2CF83);
            g.drawString(font, Component.translatable("screen.xero_quest_journal.hierarchy.selected",
                    selectedLayoutIds.size()), panelX + listWidth - 64, panelY + 7, 0xFFB8C7D9);
        }
    }

    /** Genshin-style empty state: a large centred line and a soft hint (the editor keeps the plain note). */
    private void renderEmptyJournal(GuiGraphics g, int contentTop) {
        if (editMode) {
            g.drawString(font, text("no_quests"), detailsLeft + 8, contentTop + 16, 0xFFABB8C3);
            return;
        }
        String title = text("empty.title").getString();
        String hint = text("empty.hint").getString();
        float scale = width < 400 ? 1.4F : 2.0F;
        int centerX = width / 2;
        int centerY = (contentTop + canvasBottom()) / 2;
        int titleWidth = Math.round(font.width(title) * scale);
        JournalDetailRenderer.scaled(g, font, title, centerX - titleWidth / 2,
                centerY - Math.round(font.lineHeight * scale) - 4, scale, 0xFFFFFFFF, true);
        g.drawString(font, hint, centerX - font.width(hint) / 2, centerY + 8, 0xFFE6DFCF, true);
    }

    private int themeColor(String key, int fallback) {
        String value = ClientQuestProgress.uiTheme().get(key);
        return value.isBlank() ? fallback : (fallback & 0xFF000000)
                | (ButtonStyle.parseColor(value, fallback) & 0x00FFFFFF);
    }

    /** Server results are translation suffixes; batch resets carry their count after a colon. */
    private static Component actionText(String message) {
        if (message != null && message.startsWith("reset_batch:"))
            return Component.translatable("screen.xero_quest_journal.action.reset_batch",
                    message.substring("reset_batch:".length()));
        return text("action." + message);
    }

    private void renderActionToast(GuiGraphics g) {
        var layout = StatusToastRenderer.layout(font, actionText(actionMessage), width - 24, height - 84);
        int y = Math.max(4, height - (stackedFocusControl() ? 66 : 38) - layout.height());
        StatusToastRenderer.drawAnimated(g, font, layout, width / 2, y,
                StatusToastRenderer.lifetimeProgress(actionMessageStartedAt, 3000));
    }

    private void renderTaskDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        detailReveal.advance(System.nanoTime(), popupAnimationMillis());
        Runnable draw = () -> {
            graphics.fill(detailsLeft, detailsTop(), width - 16, canvasBottom(),
                    themeColor("panel", editMode ? 0xFF292E36 : 0x00292E36));
            if (editMode) renderGrid(graphics);
            if (selected == null) {
                renderEmptyJournal(graphics, detailsTop());
            } else if (!hasBuiltInElements()) {
                graphics.enableScissor(detailsLeft, detailsTop(), width - 16, detailContentBottom());
                JournalDetailRenderer.renderBody(graphics, font, selected, detailsLeft, detailsTop(), width - 14,
                        (int) Math.floor(detailScroll));
                graphics.disableScissor();
            }
            renderElements(graphics, mouseX, mouseY);
            if (selected != null && rewardFooterHeight() > 0) {
                JournalDetailRenderer.renderFooter(graphics, font, selected, displayedRewards(), detailsLeft,
                        detailContentBottom(), width - 14, mouseX, mouseY,
                        controls.containsKey("system:daily-bonus"), editMode);
            }
        };
        if (editMode || selected == null || detailReveal.settled()) {
            detailCompositor.close();
            draw.run();
        } else {
            // Fade the complete content surface; empty pixels retain the world background.
            detailCompositor.capture(graphics, draw);
            detailCompositor.centered(graphics, width, height, detailsLeft, detailsTop(),
                    width - 14, canvasBottom(), 1, detailReveal.eased());
        }
    }

    private void revealDetails() {
        long now = System.nanoTime();
        detailReveal = new UiMotion(false, now);
        detailReveal.target(true, now, popupAnimationMillis());
    }

    private void renderOverviewPresentation(GuiGraphics g) {
        backdrop(g, 0x77171820);
        JournalNavButton.drawIcon(g, "overview", 16, 7, 24, 0xFFF1D5A0);
        g.drawString(font, font.plainSubstrByWidth(title.getString(),
                Math.max(1, width - (canAuthor() ? 214 : 152) - 46)), 46, 16, 0xFFF4DCB1, true);
        int left = overviewLeft(), top = overviewTop(), feature = overviewFeatureWidth();
        // "✦ Recommended" heading with a four-point star and an ornamented rule, as in Genshin.
        fourPointStar(g, left + 4, top + font.lineHeight / 2, 4, 0xFFF4D79C);
        g.drawString(font, text("overview_feature"), left + 12, top, 0xFFF4E0BD, true);
        int rowLeft = left + feature + overviewColumnGap();
        int rowTop = top + 22;
        int typesWidth = font.width(text("overview_types"));
        int typesRight = left + overviewContentWidth();
        int typesX = rowLeft + Math.max(0, (typesRight - rowLeft - typesWidth) / 2);
        g.drawString(font, text("overview_types"), typesX, rowTop - 20, 0xFFF4E0BD, true);
        int lineY = top + font.lineHeight / 2;
        ornamentRule(g, left + Math.min(feature, font.width(text("overview_feature")) + 20), left + feature, lineY);
        ornamentRule(g, rowLeft, Math.max(rowLeft, typesX - 8), lineY);
        ornamentRule(g, Math.min(typesRight, typesX + typesWidth + 8), typesRight, lineY);
        if (ClientQuestProgress.daily().enabled() && !ClientQuestProgress.daily().quests().isEmpty()) {
            String daily = JournalDetailRenderer.dailyHeading().getString() + "  ·  " + Component.translatable(
                    "screen.xero_quest_journal.daily.refresh", JournalDetailRenderer.countdown()).getString();
            int dailyY = rowTop + overviewFeatureHeight() + 8;
            if (dailyY + font.lineHeight < height - 8) {
                MailDecor.diamond(g, left + 3, dailyY + font.lineHeight / 2, 2, 0xFFF2CF83);
                g.drawString(font, font.plainSubstrByWidth(daily, Math.max(1, feature - 10)), left + 10, dailyY,
                        ClientQuestProgress.dailyBonusReady() ? 0xFFF2CF83 : 0xFFE6DDCB, true);
            }
        }
    }

    private static void fourPointStar(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int i = -radius; i <= radius; i++) {
            int span = Math.max(0, (radius - Math.abs(i)) / 2);
            g.fill(cx - span, cy + i, cx + span + 1, cy + i + 1, color);
            g.fill(cx + i, cy - span, cx + i + 1, cy + span + 1, color);
        }
    }

    /** Faint rule with a small diamond near each end and a dotted accent, like Genshin's headings. */
    private static void ornamentRule(GuiGraphics g, int left, int right, int y) {
        if (right - left < 12) return;
        g.fill(left, y, right, y + 1, 0x448E877D);
        MailDecor.diamond(g, left + 3, y, 2, 0x668E877D);
        MailDecor.diamond(g, right - 3, y, 2, 0x668E877D);
        if (right - left > 80) for (int i = 0; i < 5; i++)
            g.fill(right - 30 + i * 4, y - 2, right - 29 + i * 4, y - 1, 0x558E877D);
    }

    private void renderOverviewFeature(GuiGraphics g, boolean hovered) {
        if (selected == null) return;
        overviewFeatureHover.target(hovered, System.nanoTime(), animationMillis());
        float hover = overviewFeatureHover.eased();
        int left = overviewLeft(), top = overviewTop(), feature = overviewFeatureWidth();
        int featureHeight = overviewFeatureHeight();
        String image = featuredGroup == null ? selected.icon() : featuredGroup.icon();
        int imageTop = top + 22;
        int imageHeight = Math.max(1, featureHeight - 20);
        float cardScale = 1 + 0.05F * hover;
        float centerX = left + feature / 2F, centerY = imageTop + featureHeight / 2F;
        int cardLeft = Math.round(centerX + (left - centerX) * cardScale);
        int cardTop = Math.round(centerY + (imageTop - centerY) * cardScale);
        int cardRight = Math.round(centerX + (left + feature - centerX) * cardScale);
        int cardBottom = Math.round(centerY + (imageTop + featureHeight - centerY) * cardScale);
        int imageBottom = Math.round(centerY + (imageTop + imageHeight - centerY) * cardScale);
        int cardWidth = cardRight - cardLeft;
        int imageDrawHeight = imageBottom - cardTop;
        g.fill(cardLeft - 2, cardTop - 2, cardRight + 2, cardBottom + 2, 0xFFE9DDD0);
        g.fill(cardLeft, cardTop, cardRight, cardBottom, 0xFF30343B);
        g.enableScissor(cardLeft, cardTop, cardRight, imageBottom);
        float imageScale = 1 + 0.15F * hover;
        int coverWidth = Math.max(cardWidth, (int) Math.ceil(cardWidth * imageScale));
        int coverHeight = Math.max(imageDrawHeight, (int) Math.ceil(imageDrawHeight * imageScale));
        int coverX = cardLeft + (cardWidth - coverWidth) / 2;
        int coverY = cardTop + (imageDrawHeight - coverHeight) / 2;
        if (image.isBlank()) {
            ClientMediaManager.renderImageCover(g, "minecraft:textures/gui/title/background/panorama_0.png",
                    coverX, coverY, coverWidth, coverHeight, 1);
            g.fill(cardLeft, cardTop, cardRight, imageBottom, 0x8820242C);
            QuestIcons.render(g, "minecraft:book",
                    cardLeft + cardWidth / 2 - 24, cardTop + imageDrawHeight / 2 - 24, 48);
        } else if (image.contains("textures/") || image.startsWith(QuestIcons.LOCAL_NAMESPACE + ":")
                || ClientMediaManager.validImageSource(image)) {
            ClientMediaManager.renderImageCover(g, image, coverX, coverY, coverWidth, coverHeight, 1);
        } else {
            QuestIcons.render(g, image, cardLeft + cardWidth / 2 - 24,
                    cardTop + imageDrawHeight / 2 - 24, 48);
        }
        g.disableScissor();
        g.fill(cardLeft, imageBottom, cardRight, cardBottom, 0xFFF7F5F0);
        int titleLeft = cardLeft + 7;
        int titleRight = cardRight - 23;
        int titleY = imageBottom + (cardBottom - imageBottom - font.lineHeight) / 2;
        String title = featuredGroup == null ? QuestText.title(selected).getString() : featuredGroup.name();
        g.enableScissor(titleLeft, imageBottom, Math.max(titleLeft + 1, titleRight), cardBottom);
        int overflow = Math.max(0, font.width(title) - (titleRight - titleLeft));
        int offset = 0;
        if (overflow > 0) {
            double travel = overflow * 24.0; // 24 ms per pixel, with a pause at each end.
            double phase = (System.nanoTime() / 1_000_000.0) % (travel * 2 + 1600);
            offset = (int) Math.round(phase < 800 ? 0
                    : phase < travel + 800 ? (phase - 800) / 24.0
                    : phase < travel + 1600 ? overflow
                    : overflow - (phase - travel - 1600) / 24.0);
        }
        g.drawString(font, title, titleLeft - offset, titleY, 0xFF293039, false);
        g.disableScissor();
        // Genshin-style round "go" button: dark brown disc with a light chevron.
        int knob = Math.max(9, Math.min(15, cardBottom - imageBottom - 6));
        int knobX = cardRight - knob - 5, knobY = imageBottom + (cardBottom - imageBottom - knob) / 2;
        MailDecor.circle(g, knobX, knobY, knob, hovered ? 0xFF7A5A3C : 0xFF5F4530);
        int chevronX = knobX + knob / 2 - 1, chevronY = knobY + knob / 2;
        for (int row = -3; row <= 3; row++)
            g.fill(chevronX + (3 - Math.abs(row)) - 1, chevronY + row, chevronX + (3 - Math.abs(row)) + 1,
                    chevronY + row + 1, 0xFFF7EBDA);
        if ((featuredGroup != null && featuredGroup.limitedReward())
                || (featuredGroup == null && selected.limitedReward())) {
            JournalNavButton.drawLimitedRewardBadge(g, font, cardLeft + 4, imageBottom + 2,
                    Math.max(54, cardWidth - 8));
        }
        if (hovered)
            g.renderOutline(cardLeft - 2, cardTop - 2, cardWidth + 4, cardBottom - cardTop + 4, 0xFFF4E0BD);
    }

    private void renderFormLabels(GuiGraphics g) {
        for (FormLabel label : formLabels) {
            String visible = font.plainSubstrByWidth(label.text().getString(), label.width());
            boolean scrolls = label.y() >= formBodyTop();
            if (scrolls) g.enableScissor(formViewport().left(), formBodyTop(), formViewport().right(), formBodyBottom());
            g.drawString(font, visible, label.x(), label.y() + 6 - (scrolls ? (int) Math.round(form.scroll) : 0),
                    0xFFD5DEE8, false);
            if (scrolls) g.disableScissor();
        }
        g.enableScissor(formViewport().left(), formBodyTop(), formViewport().right(), formBodyBottom());
        for (ColorPreview preview : colorPreviews) {
            String value = preview.value().get();
            int color = safeColor(value, Integer.MIN_VALUE);
            int x = preview.x();
            int y = preview.y() - (int) Math.round(form.scroll);
            int size = preview.size();
            if (color != Integer.MIN_VALUE && (color >>> 24) != 0) {
                g.fill(x, y, x + size, y + size, color);
            } else if (color == Integer.MIN_VALUE && QuestIcons.valid(value) && !value.isBlank()) {
                QuestIcons.render(g, value, x, y, size);
            } else {
                int half = Math.max(1, size / 2);
                g.fill(x, y, x + size, y + size, 0xFF454B55);
                g.fill(x, y, x + half, y + half, 0xFF8A929E);
                g.fill(x + half, y + half, x + size, y + size, 0xFF8A929E);
            }
            g.renderOutline(x - 1, y - 1, size + 2, size + 2, 0xFF9DB4D2);
        }
        g.disableScissor();
    }

    private void renderGrid(GuiGraphics g) {
        if (!showEditorGrid && !showEditorAxes) return;
        int left = canvasLeft();
        int top = canvasTop();
        int right = canvasRight();
        int bottom = detailContentBottom();
        int grid = Math.max(2, layout.gridSize());
        double stepX = Math.max(2.0, grid * canvasScaleX() * canvasZoom);
        double stepY = Math.max(2.0, grid * canvasScaleY() * canvasZoom);
        double originX = left + canvasPanX;
        double originY = top + canvasPanY - detailScroll;
        g.enableScissor(left, top, right, bottom);
        if (showEditorGrid) for (double x = originX + Math.ceil((left - originX) / stepX) * stepX;
             x <= Math.min(right, originX + taskWidth() * canvasScaleX() * canvasZoom); x += stepX) {
            int px = (int) Math.round(x);
            g.fill(px, top, px + 1, bottom, px == left ? 0x709DB4D2 : 0x243F4A56);
        }
        if (showEditorGrid) for (double y = originY + Math.ceil((top - originY) / stepY) * stepY;
             y <= Math.min(bottom, originY + taskHeight() * canvasScaleY() * canvasZoom); y += stepY) {
            int py = (int) Math.round(y);
            g.fill(left, py, right, py + 1, py == top ? 0x709DB4D2 : 0x243F4A56);
        }
        if (showEditorAxes) {
            int axisX = (int) Math.round(originX);
            int axisY = (int) Math.round(originY);
            if (axisY >= top && axisY < bottom) g.fill(left, axisY, right, axisY + 2, 0x906D9FC0);
            if (axisX >= left && axisX < right) g.fill(axisX, top, axisX + 2, bottom, 0x906D9FC0);
            g.drawString(font, "X", right - 12, Math.clamp(axisY + 4, top + 2, bottom - 12), 0xFFB4CDE4, false);
            g.drawString(font, "Y", Math.clamp(axisX + 4, left + 2, right - 12), top + 3, 0xFFB4CDE4, false);
            g.drawString(font, "0,0", axisX + 3, axisY + 3, 0xFF9DB4D2, false);
        }
        g.disableScissor();
    }

    private void renderElements(GuiGraphics g, int mouseX, int mouseY) {
        var scrollContainers = layout.elements().values().stream()
                .filter(element -> element.quest().equals(selectedId) && element.kind().equals("scroll"))
                .toList();
        Set<String> scrollIds = scrollContainers.stream().map(LayoutEditorState.Element::id)
                .collect(java.util.stream.Collectors.toSet());
        embeddedScrolls.keySet().removeIf(id -> !scrollIds.contains(id));
        for (var container : scrollContainers) {
            var scroll = embeddedScroll(container);
            scroll.limit(embeddedScrollMaximum(container));
            scroll.advance();
        }
        if (previewTemplate == null && !editMode && form == null && iconPicker == null && menu.isEmpty()
                && System.nanoTime() - lastControlHover >= 200_000_000L) {
            String hovered = hit(mouseX, mouseY);
            var element = layout.elements().get(hovered);
            if (element != null && !element.kind().equals("quest_rewards")
                    && element.attributes().containsKey("eventGraph")) {
                lastControlHover = System.nanoTime();
                controlPointer(element, "hover", mouseX, mouseY);
            }
        }
        g.enableScissor(detailsLeft, detailsTop(), canvasRight(), detailContentBottom());
        for (var element : orderedElements(false)) {
            if (!element.quest().equals(selectedId)) {
                continue;
            }
            var p = layout.position(element.id(), layout.gridSize(), 48);
            int elementWidth = displayWidth(element);
            int elementHeight = displayHeight(element);
            int[] display = elementDisplayPosition(element);
            int x = display[0];
            int y = display[1];
            int[] scrollClip = scrollClipBounds(element);
            if (scrollClip[2] <= scrollClip[0] || scrollClip[3] <= scrollClip[1]) continue;
            boolean nestedClip = scrollClip[0] > detailsLeft || scrollClip[1] > detailsTop()
                    || scrollClip[2] < canvasRight() || scrollClip[3] < detailContentBottom();
            if (nestedClip) g.enableScissor(scrollClip[0], scrollClip[1], scrollClip[2], scrollClip[3]);
            p.size(logicalDisplayWidth(element), logicalDisplayHeight(element));
            if (x + elementWidth + 32 < detailsLeft || x - 32 > canvasRight()
                    || y + elementHeight + 32 < detailsTop() || y - 32 > detailContentBottom()) continue;
            String visibility = element.attribute("visibility", "visible");
            boolean drawContent = visibility.equals("visible") && (editMode || runtimeVisible(element));
            if (!drawContent && !editMode) {
                continue;
            }
            float alpha = Math.max(0.0F, Math.min(1.0F,
                    parseFloat(element.attribute("alpha", "1"), 1.0F)));
            boolean hoveredSurface = drawContent && !editMode && menu.isEmpty() && form == null && iconPicker == null
                    && mouseY >= detailsTop() && mouseY < detailContentBottom()
                    && mouseX >= x && mouseX < x + elementWidth && mouseY >= y && mouseY < y + elementHeight;
            boolean pressedSurface = hoveredSurface && (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind())
                    ? element.id().equals(pressedControlId) : org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    minecraft.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS);
            String appearanceState = pressedSurface ? "Pressed" : hoveredSurface ? "Hover" : "";
            String backgroundValue = appearance(element, "background", appearanceState, "transparent");
            int background = safeColor(backgroundValue, Integer.MIN_VALUE);
            int borderColor = applyAlpha(safeColor(appearance(element, "borderColor", appearanceState, "transparent"), 0), alpha);
            int borderWidth = Math.max(0, Math.min(32,
                    parseInteger(appearance(element, "borderWidth", appearanceState, "0"), 0)));
            if (!Boolean.parseBoolean(appearance(element, "borderEnabled", appearanceState, "true"))) borderWidth = 0;
            int displayBorderWidth = canvasPixels(borderWidth);
            String shape = appearance(element, "shape", appearanceState, "rectangle");
            int radius = canvasPixels(Math.max(0, Math.min(128,
                    parseInteger(appearance(element, "cornerRadius", appearanceState, "0"), 0))));
            ButtonActionAnimation motion = null;
            if (drawContent && !editMode
                    && xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind())) {
                motion = controlMotions.computeIfAbsent(element.id(), ignored -> new ButtonActionAnimation());
                motion.update(hoveredSurface, element.id().equals(pressedControlId));
                motion.push(g, x, y, elementWidth, elementHeight, 0F);
            }
            if (drawContent && background != Integer.MIN_VALUE) {
                ControlAppearance.draw(g, x, y, elementWidth, elementHeight, shape, radius,
                        applyAlpha(background, alpha), borderColor, displayBorderWidth);
            } else if (drawContent && QuestIcons.valid(backgroundValue)) {
                QuestIcons.render(g, backgroundValue, x, y, elementWidth, elementHeight, alpha);
                ControlAppearance.draw(g, x, y, elementWidth, elementHeight, shape, radius, 0,
                        borderColor, displayBorderWidth);
            }
            if (motion != null) motion.ripple(g, "claim_all", x, y, elementWidth, elementHeight, shape, radius, alpha);
            if (drawContent && element.kind().equals("quest_icon")) {
                QuestIcons.render(g, selected == null ? "" : selected.icon(), x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.kind().equals("item_display")) {
                ItemStack stack = itemStack(effectiveElement(element).value());
                if (!stack.isEmpty()) renderItemStack(g, stack, x, y, elementWidth, elementHeight, alpha);
                if (hoveredSurface || pressedSurface)
                    g.renderOutline(x - 1, y - 1, elementWidth + 2, elementHeight + 2,
                            applyAlpha(pressedSurface ? 0xFFFFE3A2 : 0xFFF0D28A, alpha));
            } else if (drawContent && element.kind().equals("quest_rewards")) {
                int hoveredReward = !editMode && menu.isEmpty() && element.id().equals(hit(mouseX, mouseY))
                        ? rewardIndexAt(element, mouseX, mouseY) : -1;
                renderTemplateRewards(g, x, y, elementWidth, elementHeight, alpha, hoveredReward);
            } else if (drawContent && element.kind().equals("quest_entry_text")) {
                renderQuestEntryText(g, element, x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.kind().equals("structure_preview")) {
                if (previewTemplate == null) {
                    ClientStructures.request(element.quest(), QuestPresentationClient.sourceId(element), element.value());
                    StructurePreviewRenderer.render(g, element.value(), x, y, elementWidth, elementHeight);
                } else QuestIcons.render(g, "minecraft:structure_block", x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.kind().equals("structure_enter")) {
                renderButtonElement(g, new LayoutEditorState.Element(element.id(), element.quest(), "button",
                        element.attribute("displayText", "").isBlank() ? text("structure.enter").getString()
                                : element.attribute("displayText", ""),
                        element.width(), element.height(), element.attributes()),
                        appearanceState, x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.kind().equals("button")) {
                renderButtonElement(g, elementWithDisplayValue(element), appearanceState, x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.attribute("journal_ornament", "false").equals("true")) {
                JournalDetailRenderer.ornament(g, x, x + elementWidth, y + elementHeight / 2, applyAlpha(0x66D9C9A3, alpha));
            } else if (drawContent && element.kind().equals("quest_objective") && (previewTemplate == null || previewQuest != null)
                    && selected != null && element.quest().equals(selected.id())) {
                renderObjectiveElement(g, element, x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && isTextElement(element)) {
                renderTextElement(g, elementWithDisplayValue(element), x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.kind().equals("image")) {
                renderImageElement(g, element, x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.kind().equals("video")) {
                renderVideoElement(g, element, x, y, elementWidth, elementHeight, alpha);
            } else if (drawContent && element.kind().equals("audio")) {
                renderAudioElement(g, element, x, y, elementWidth, elementHeight, alpha);
            }
            if (motion != null) g.pose().popPose();
            if (editMode && showEditorBounds) {
                g.renderOutline(x, y, elementWidth, elementHeight,
                        selectedLayoutIds.contains(element.id()) ? 0xFFF2CF83 : 0xFF79B9A5);
                if (isLayoutContainerKind(element.kind())) {
                    g.drawString(font, text("layout." + element.kind()), x + 3, y + 3, 0xFF79B9A5);
                }
            }
            if (nestedClip) g.disableScissor();
        }
        for (var container : scrollContainers) renderScrollTrack(g, container, mouseX, mouseY);
        if (!editMode && menu.isEmpty() && form == null && iconPicker == null) {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            for (var element : orderedElements(false)) {
                if (!element.kind().equals("quest_objective") && !parseBoolean(element.attribute("hoverHighlight", "false"), false))
                    continue;
                if (!runtimeVisible(element)) continue;
                int[] location = elementDisplayPosition(element), clip = scrollClipBounds(element);
                int w = displayWidth(element), h = displayHeight(element);
                if (mouseX < location[0] || mouseX >= location[0] + w || mouseY < location[1] || mouseY >= location[1] + h
                        || mouseX < clip[0] || mouseX >= clip[2] || mouseY < clip[1] || mouseY >= clip[3]) continue;
                g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
                g.renderOutline(location[0], location[1], w, h, 0xFFFFFFFF);
                g.disableScissor();
            }
            g.pose().popPose();
        }
        g.disableScissor();
    }

    private SmoothScroll embeddedScroll(LayoutEditorState.Element container) {
        return embeddedScrolls.computeIfAbsent(container.id(), ignored -> new SmoothScroll());
    }

    private boolean horizontalScroll(LayoutEditorState.Element container) {
        return container.attribute("orientation", "vertical").equals("horizontal");
    }

    private double embeddedScrollMaximum(LayoutEditorState.Element container) {
        var position = layout.position(container.id(), 0, 0);
        boolean horizontal = horizontalScroll(container);
        int viewportStart = horizontal ? position.x() + paddingLeft(container)
                : position.y() + paddingTop(container);
        int viewportSize = horizontal
                ? elementWidth(container) - paddingLeft(container) - paddingRight(container)
                : elementHeight(container) - paddingTop(container) - paddingBottom(container);
        int contentEnd = viewportStart + Math.max(1, viewportSize);
        for (var child : layout.elements().values()) {
            if (!container.id().equals(layout.parentOf(child.id()))
                    || child.attribute("visibility", "visible").equals("gone")) continue;
            var childPosition = layout.position(child.id(), 0, 0);
            int end = horizontal ? childPosition.x() + elementWidth(child)
                    : childPosition.y() + elementHeight(child);
            contentEnd = Math.max(contentEnd, end + (horizontal ? marginRight(child) : marginBottom(child)));
        }
        return Math.max(0, contentEnd - viewportStart - Math.max(1, viewportSize));
    }

    private LayoutEditorState.Element enclosingScroll(LayoutEditorState.Element element) {
        for (String parentId = layout.parentOf(element.id()); parentId != null;
             parentId = layout.parentOf(parentId)) {
            var parent = layout.elements().get(parentId);
            if (parent == null || !parent.quest().equals(element.quest())) break;
            if (parent.kind().equals("scroll")) return parent;
        }
        return null;
    }

    private int[] scrollViewportRect(LayoutEditorState.Element container) {
        int[] position = elementDisplayPosition(container);
        int width = displayWidth(container), height = displayHeight(container);
        int left = position[0] + canvasPixels(paddingLeft(container));
        int top = position[1] + canvasPixels(paddingTop(container));
        int right = position[0] + width - canvasPixels(paddingRight(container));
        int bottom = position[1] + height - canvasPixels(paddingBottom(container));
        if (parseBoolean(container.attribute("showScrollbar", "true"), true)
                && embeddedScrollMaximum(container) > 0) {
            if (horizontalScroll(container)) bottom -= Math.max(4, canvasPixels(6));
            else right -= Math.max(4, canvasPixels(6));
        }
        return new int[]{left, top, Math.max(left, right), Math.max(top, bottom)};
    }

    private int[] scrollClipBounds(LayoutEditorState.Element element) {
        int left = detailsLeft, top = detailsTop(), right = canvasRight(), bottom = detailContentBottom();
        for (String parentId = layout.parentOf(element.id()); parentId != null;
             parentId = layout.parentOf(parentId)) {
            var parent = layout.elements().get(parentId);
            if (parent == null || !parent.quest().equals(element.quest())) break;
            if (!parent.kind().equals("scroll")) continue;
            int[] clip = scrollViewportRect(parent);
            left = Math.max(left, clip[0]); top = Math.max(top, clip[1]);
            right = Math.min(right, clip[2]); bottom = Math.min(bottom, clip[3]);
            int[] parentPosition = elementDisplayPosition(parent);
            left = Math.max(left, parentPosition[0]);
            top = Math.max(top, parentPosition[1]);
            right = Math.min(right, parentPosition[0] + displayWidth(parent));
            bottom = Math.min(bottom, parentPosition[1] + displayHeight(parent));
        }
        return new int[]{left, top, Math.max(left, right), Math.max(top, bottom)};
    }

    private ScrollTrack scrollTrack(LayoutEditorState.Element container) {
        if (!parseBoolean(container.attribute("showScrollbar", "true"), true)) return null;
        int[] box = elementDisplayPosition(container);
        int width = displayWidth(container), height = displayHeight(container);
        boolean horizontal = horizontalScroll(container);
        double maximum = embeddedScrollMaximum(container);
        if (maximum <= 0) return null;
        int trackX = horizontal ? box[0] + 2 : box[0] + width - Math.max(4, canvasPixels(5));
        int trackY = horizontal ? box[1] + height - Math.max(4, canvasPixels(5)) : box[1] + 2;
        int trackWidth = horizontal ? Math.max(1, width - 4) : Math.max(3, canvasPixels(3));
        int trackHeight = horizontal ? Math.max(3, canvasPixels(3)) : Math.max(1, height - 4);
        int trackLength = horizontal ? trackWidth : trackHeight;
        double scale = (horizontal ? canvasScaleX() : canvasScaleY()) * (editMode ? canvasZoom : 1);
        int contentLength = trackLength + (int) Math.ceil(maximum * scale);
        int thumbLength = Math.min(trackLength, Math.max(canvasPixels(10),
                trackLength * trackLength / Math.max(1, contentLength)));
        double progress = embeddedScroll(container).position() / maximum;
        int thumbOffset = (int) Math.round(Math.max(0, trackLength - thumbLength)
                * Math.clamp(progress, 0, 1));
        int thumbX = horizontal ? trackX + thumbOffset : trackX;
        int thumbY = horizontal ? trackY : trackY + thumbOffset;
        return new ScrollTrack(trackX, trackY, trackWidth, trackHeight,
                thumbX, thumbY, horizontal ? thumbLength : trackWidth,
                horizontal ? trackHeight : thumbLength, maximum);
    }

    private void renderScrollTrack(GuiGraphics g, LayoutEditorState.Element container,
                                  int mouseX, int mouseY) {
        ScrollTrack track = scrollTrack(container);
        if (track == null) return;
        int[] clip = scrollClipBounds(container);
        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        int background = 0x80555C66, thumb = 0xFFD9DDE3;
        if (mouseX >= track.thumbX() && mouseX < track.thumbX() + track.thumbWidth()
                && mouseY >= track.thumbY() && mouseY < track.thumbY() + track.thumbHeight())
            thumb = 0xFFFFE0A0;
        g.fill(track.x(), track.y(), track.x() + track.width(), track.y() + track.height(), background);
        g.fill(track.thumbX(), track.thumbY(), track.thumbX() + track.thumbWidth(),
                track.thumbY() + track.thumbHeight(), thumb);
        g.disableScissor();
    }

    private LayoutEditorState.Element scrollTrackAt(double x, double y) {
        List<LayoutEditorState.Element> ordered = orderedElements(true);
        for (int index = ordered.size() - 1; index >= 0; index--) {
            var element = ordered.get(index);
            if (!element.quest().equals(selectedId) || !element.kind().equals("scroll")
                    || !editMode && !runtimeVisible(element) || !insideScrollClips(element, x, y)) continue;
            ScrollTrack track = scrollTrack(element);
            if (track != null && x >= track.x() - 2 && x < track.x() + track.width() + 2
                    && y >= track.y() - 2 && y < track.y() + track.height() + 2) return element;
        }
        return null;
    }

    private void moveScrollTrack(LayoutEditorState.Element container, double x, double y) {
        ScrollTrack track = scrollTrack(container);
        if (track == null) return;
        boolean horizontal = horizontalScroll(container);
        double start = horizontal ? track.x() : track.y();
        double length = horizontal ? track.width() : track.height();
        double thumb = horizontal ? track.thumbWidth() : track.thumbHeight();
        double pointer = horizontal ? x : y;
        double value = (pointer - start - scrollTrackGrab) / Math.max(1, length - thumb);
        SmoothScroll scroll = embeddedScroll(container);
        scroll.limit(track.maximum());
        scroll.jump(Math.clamp(value, 0, 1) * track.maximum());
    }

    private int detailContentBottom() {
        return canvasBottom() - rewardFooterHeight();
    }

    /**
     * Height of the Genshin-style reward footer pinned below the scrolling details. Hidden while
     * editing and when the task layout already places its own reward control.
     */
    private int rewardFooterHeight() {
        if (overviewMode || previewViewport != null || selected == null) return 0;
        for (var element : layout.elements().values()) {
            if (element.quest().equals(selectedId) && element.kind().equals("quest_rewards")
                    && (editMode || runtimeVisible(element))) return 0;
        }
        if (!editMode) {
            if (!JournalDetailRenderer.footerApplies(selected, displayedRewards())) return 0;
            for (var element : layout.elements().values()) {
                if (element.quest().equals(selectedId) && element.kind().equals("quest_rewards")
                        && runtimeVisible(element)) return 0;
            }
        }
        int available = canvasBottom() - canvasTop() - 60;
        return available < JournalDetailRenderer.FOOTER_HEIGHT ? 0 : JournalDetailRenderer.FOOTER_HEIGHT;
    }

    private void claimDailyBonus() {
        if (actionPending != null || !ClientQuestProgress.dailyBonusReady()) return;
        actionMessage = null;
        var bonus = ClientQuestProgress.daily().bonus();
        actionPending = ClientQuestProgress.claimDailyBonus();
        pendingClaim = new PendingClaim(actionPending, text("daily.bonus_title").getString(), null,
                Map.of("daily", bonus));
        init();
    }
    private static String appearance(LayoutEditorState.Element element, String key, String state, String fallback) {
        String value = state.isEmpty() ? "" : element.attribute(key + state, "");
        return value.isBlank() || value.equals("inherit") ? element.attribute(key, fallback) : value;
    }

    private int[] elementDisplayPosition(LayoutEditorState.Element element) {
        var position = layout.position(element.id(), layout.gridSize(), 48);
        var runtime = editMode ? Map.<String, String>of() : ClientControlState.properties(element.quest(), QuestPresentationClient.sourceId(element));
        int mappedX = canvasX(parseInteger(runtime.getOrDefault("x", Integer.toString(position.x())), position.x()));
        int mappedY = canvasY(parseInteger(runtime.getOrDefault("y", Integer.toString(position.y())), position.y()));
        int[] display;
        if (editMode) {
            display = new int[]{
                    (int) Math.round(canvasLeft() + (mappedX - canvasLeft()) * canvasZoom + canvasPanX),
                    (int) Math.round(canvasTop() + (mappedY - canvasTop()) * canvasZoom + canvasPanY - detailScroll)};
        } else {
            // Keep saved coordinates intact; move an out-of-bounds root and its children together for preview.
            var root = element;
            String parent;
            while ((parent = layout.parentOf(root.id())) != null) {
                var candidate = layout.elements().get(parent);
                if (candidate == null || !candidate.quest().equals(selectedId)) break;
                root = candidate;
            }
            var origin = layout.position(root.id(), layout.gridSize(), 48);
            int originX = canvasX(origin.x());
            int originY = canvasY(origin.y());
            int left = canvasLeft();
            int top = canvasTop();
            int right = Math.max(left, canvasX(taskWidth()) - displayWidth(root));
            int bottom = Math.max(top, canvasY(taskHeight()) - displayHeight(root));
            int dx = Math.max(left, Math.min(originX, right)) - originX;
            int dy = Math.max(top, Math.min(originY, bottom)) - originY;
            display = new int[]{mappedX + dx + (int) Math.round(canvasPanX),
                    mappedY + dy - (int) Math.floor(detailScroll)};
        }
        var scroll = enclosingScroll(element);
        if (scroll != null) {
            double value = embeddedScroll(scroll).position();
            int offset = (int) Math.round(value * canvasScaleX() * (editMode ? canvasZoom : 1));
            if (horizontalScroll(scroll)) display[0] -= offset;
            else display[1] -= offset;
        }
        return display;
    }

    private int displayWidth(LayoutEditorState.Element element) {
        return Math.max(1, (int) Math.round(elementWidth(element) * canvasRenderScale()));
    }

    private int displayHeight(LayoutEditorState.Element element) {
        return Math.max(1, (int) Math.round(elementHeight(element) * canvasRenderScale()));
    }

    private int logicalDisplayWidth(LayoutEditorState.Element element) {
        return Math.max(1, (int) Math.ceil(elementWidth(element) * canvasContentScale() / canvasScaleX()));
    }

    private int logicalDisplayHeight(LayoutEditorState.Element element) {
        return Math.max(1, (int) Math.ceil(elementHeight(element) * canvasContentScale() / canvasScaleY()));
    }

    private List<LayoutEditorState.Element> orderedElements(boolean hitOrder) {
        if (hitOrder && frameHitElements != null) return frameHitElements;
        if (!hitOrder && frameElements != null) return frameElements;
        Map<String, Integer> layer = new HashMap<>();
        int index = 0;
        for (String id : layout.elements().keySet()) {
            layer.put(id, index++);
        }
        Comparator<LayoutEditorState.Element> comparator = Comparator
                .comparingInt((LayoutEditorState.Element element) -> layout.depth(element.id()))
                .thenComparingInt(element -> layer.getOrDefault(element.id(), 0));
        List<LayoutEditorState.Element> elements = layout.elements().values().stream()
                .filter(element -> element.quest().equals(selectedId)).sorted(comparator).map(this::effectiveElement).toList();
        if (!hitOrder) {
            return frameElements = elements;
        }
        List<LayoutEditorState.Element> reversed = new ArrayList<>(elements);
        Collections.reverse(reversed);
        return frameHitElements = reversed;
    }

    private void clearFrameElements() {
        frameElements = frameHitElements = null;
        frameDetailMaximum = null;
    }
    private boolean runtimeVisible(LayoutEditorState.Element element) {
        for (var current = element; current != null; current = layout.elements().get(layout.parentOf(current.id()))) {
            if (!effectiveElement(current).attribute("visibility", "visible").equals("visible")) return false;
        }
        return true;
    }

    private boolean isTextElement(LayoutEditorState.Element element) {
        return element.kind().equals("text") || element.kind().equals("button") || element.kind().startsWith("quest_")
                && !Set.of("quest_icon", "quest_rewards", "quest_entry_text").contains(element.kind());
    }

    private Component styledText(LayoutEditorState.Element element, String value) {
        var text = Component.literal(value);
        return switch (element.attribute("textStyle", "normal")) {
            case "bold" -> text.withStyle(ChatFormatting.BOLD);
            case "italic" -> text.withStyle(ChatFormatting.ITALIC);
            case "bold_italic" -> text.withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC);
            default -> text;
        };
    }

    private LayoutEditorState.Element elementWithDisplayValue(LayoutEditorState.Element element) {
        return new LayoutEditorState.Element(element.id(), element.quest(), element.kind(),
                elementDisplayValue(element), element.width(), element.height(), element.attributes());
    }

    private String elementDisplayValue(LayoutEditorState.Element element) {
        if (previewTemplate != null) {
            if (element.kind().equals("quest_status")) return Component.translatable("quest.xero_quest_journal.status.active").getString();
            if (element.kind().equals("quest_progress")) return Component.translatable(
                    "screen.xero_quest_journal.progress", 0,
                    Math.max(1, previewTemplate.defaultConditions().stream().mapToInt(QuestDefinition.Trigger::count).sum()))
                    .getString();
        }
        if (!editMode) {
            String overridden = ClientControlState.properties(element.quest(), QuestPresentationClient.sourceId(element)).get("value");
            if (overridden != null) return overridden;
        }
        if (selected == null || !element.quest().equals(selected.id())) {
            return element.value();
        }
        return switch (element.kind()) {
            case "quest_title" -> QuestText.title(selected).getString();
            case "quest_type" -> QuestText.type(selected).getString();
            case "quest_status" -> ClientQuestProgress.status(selected.id()).getString();
            case "quest_description" -> QuestText.description(selected).getString();
            case "quest_progress" -> {
                var view = ClientQuestProgress.view(selected.id());
                if (view == null || view.required() <= 0 || view.completed()) yield "";
                String label = progressDisplayName(element);
                yield label.isBlank() ? Component.translatable("screen.xero_quest_journal.progress",
                        view.current(), view.required()).getString()
                        : Component.literal(label + ": " + view.current() + " / " + view.required()).getString();
            }
            case "quest_objective" -> {
                var view = ClientQuestProgress.view(selected.id());
                String configured = progressDisplayName(element);
                String label = configured.isBlank() ? QuestText.objective(selected).getString() : configured;
                // Leading spaces reserve the objective diamond drawn by renderObjectiveElement.
                yield "    " + label + (view == null || view.required() <= 1 || view.completed() ? ""
                        : "  (" + view.current() + "/" + view.required() + ")");
            }
            case "quest_place" -> QuestText.place(selected).getString();
            default -> element.value();
        };
    }

    private List<QuestDefinition.Reward> displayedRewards() {
        if (selected == null) return List.of();
        var view = ClientQuestProgress.view(selected.id());
        return view == null ? selected.rewards() : view.rewards();
    }

    private void openRewardsEditor() {
        if (selected == null || !canAuthor()) return;
        try {
            minecraft.setScreen(new QuestRewardsScreen(this, selected.id()));
        } catch (Exception exception) {
            error = "create.error.no_source";
        }
    }

    private static boolean manualCondition(List<QuestDefinition.Trigger> triggers) {
        return triggers.stream().anyMatch(t -> t.type().equals("confirm") || t.type().equals("item_submit")
                || manualCondition(t.children()));
    }

    private static boolean containsCondition(List<QuestDefinition.Trigger> triggers, String type) {
        return triggers.stream().anyMatch(t -> t.type().equals(type) || containsCondition(t.children(), type));
    }

    private void openConditionsEditor(String id) {
        if (!canAuthor()) return;
        try { minecraft.setScreen(new QuestConditionsScreen(this, id)); }
        catch (Exception failure) { error = "create.error.no_source"; }
    }

    private void renderTemplateRewards(GuiGraphics g, int x, int y, int w, int h, float alpha, int hoveredReward) {
        if (selected == null) return;
        var view = ClientQuestProgress.view(selected.id());
        var rewards = displayedRewards();
        g.enableScissor(x, y, x + w, y + h);
        String status = rewards.isEmpty() ? "empty" : view == null ? "loading"
                : view.status().equals("claimed") ? "claimed" : view.status().equals("complete")
                ? (!view.rewards().equals(selected.rewards()) ? "historical" : "pending") : "locked";
        int heading = h >= canvasPixels(46) || rewards.isEmpty() ? canvasPixels(14) : 0;
        if (heading > 0) {
            float scale = (float) canvasRenderScale();
            int availableWidth = Math.max(1, (int) Math.floor(w / scale));
            String label = font.plainSubstrByWidth(text("rewards." + status).getString(), availableWidth);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, label, 0, 0, applyAlpha(0xFFF2CF83, alpha), false);
            g.pose().popPose();
        }
        int columnStep = canvasPixels(RewardCardRenderer.COLUMN_STEP);
        int rowStep = canvasPixels(RewardCardRenderer.ROW_STEP);
        int columns = Math.max(1, (w + canvasPixels(4)) / columnStep);
        int index = 0;
        for (var reward : rewards) {
            int left = x + index % columns * columnStep;
            int top = y + heading + index / columns * rowStep;
            index++;
            if (top >= y + h) break;
            boolean claimed = view != null && view.status().equals("claimed");
            RewardCardRenderer.render(g, font, QuestRewardsScreen.icon(reward.type(), reward.item()),
                    reward.count(), reward.quality(), reward.background(), left, top,
                    (float) canvasRenderScale(), alpha, claimed,
                    index - 1 == hoveredReward);
        }
        g.disableScissor();
    }

    private void renderImageElement(GuiGraphics g, LayoutEditorState.Element element,
                                    int x, int y, int elementWidth, int elementHeight, float alpha) {
        int paddingLeft = canvasPixels(paddingLeft(element));
        int paddingTop = canvasPixels(paddingTop(element));
        int paddingRight = canvasPixels(paddingRight(element));
        int paddingBottom = canvasPixels(paddingBottom(element));
        int left = x + paddingLeft;
        int top = y + paddingTop;
        int availableWidth = Math.max(1, elementWidth - paddingLeft - paddingRight);
        int availableHeight = Math.max(1, elementHeight - paddingTop - paddingBottom);
        String scaleType = element.attribute("scaleType", "fit_xy");
        if (scaleType.equals("center")) {
            int size = Math.min(canvasPixels(16), Math.min(availableWidth, availableHeight));
            QuestIcons.render(g, element.value(), left + (availableWidth - size) / 2,
                    top + (availableHeight - size) / 2, size, size, alpha);
        } else if (scaleType.equals("fit_center")) {
            int size = Math.min(availableWidth, availableHeight);
            QuestIcons.render(g, element.value(), left + (availableWidth - size) / 2,
                    top + (availableHeight - size) / 2, size, size, alpha);
        } else {
            QuestIcons.render(g, element.value(), left, top, availableWidth, availableHeight, alpha);
        }
    }

    private void renderVideoElement(GuiGraphics g, LayoutEditorState.Element element,
                                    int x, int y, int elementWidth, int elementHeight, float alpha) {
        int paddingLeft = canvasPixels(paddingLeft(element));
        int paddingTop = canvasPixels(paddingTop(element));
        int paddingRight = canvasPixels(paddingRight(element));
        int paddingBottom = canvasPixels(paddingBottom(element));
        int left = x + paddingLeft;
        int top = y + paddingTop;
        int availableWidth = Math.max(1, elementWidth - paddingLeft - paddingRight);
        int availableHeight = Math.max(1, elementHeight - paddingTop - paddingBottom);
        ClientMediaManager.renderVideo(g, mediaOwner, element.id(), element.value(),
                left, top, availableWidth, availableHeight, alpha,
                previewTemplate == null && parseBoolean(element.attribute("autoplay", "true"), true),
                parseBoolean(element.attribute("loop", "false"), false),
                parseBoolean(element.attribute("muted", "false"), false),
                parseFloat(element.attribute("volume", "1"), 1.0F),
                element.attribute("scaleType", "fit_center"), element.attribute("displayText", ""));
    }

    private void renderAudioElement(GuiGraphics g, LayoutEditorState.Element element,
                                    int x, int y, int elementWidth, int elementHeight, float alpha) {
        int paddingLeft = canvasPixels(paddingLeft(element));
        int paddingTop = canvasPixels(paddingTop(element));
        int paddingRight = canvasPixels(paddingRight(element));
        int paddingBottom = canvasPixels(paddingBottom(element));
        int left = x + paddingLeft;
        int top = y + paddingTop;
        int availableWidth = Math.max(1, elementWidth - paddingLeft - paddingRight);
        int availableHeight = Math.max(1, elementHeight - paddingTop - paddingBottom);
        ClientMediaManager.renderAudio(g, mediaOwner, element.id(), element.value(),
                left, top, availableWidth, availableHeight, alpha,
                previewTemplate == null && parseBoolean(element.attribute("autoplay", "true"), true),
                parseBoolean(element.attribute("loop", "false"), false),
                parseBoolean(element.attribute("muted", "false"), false),
                parseFloat(element.attribute("volume", "1"), 1.0F),
                parseBoolean(element.attribute("showControls", "true"), true),
                element.attribute("displayText", ""));
    }

    private void renderQuestEntryText(GuiGraphics g, LayoutEditorState.Element element,
                                      int x, int y, int elementWidth, int elementHeight, float alpha) {
        int paddingLeft = canvasPixels(paddingLeft(element));
        int paddingTop = canvasPixels(paddingTop(element));
        int paddingRight = canvasPixels(paddingRight(element));
        int paddingBottom = canvasPixels(paddingBottom(element));
        int contentHeight = Math.max(1, elementHeight - paddingTop - paddingBottom);
        int iconSize = Math.min(contentHeight, canvasPixels(16));
        int iconX = x + paddingLeft;
        int iconY = y + paddingTop + (contentHeight - iconSize) / 2;
        QuestIcons.render(g, element.attribute("entryIcon", "minecraft:paper"),
                iconX, iconY, iconSize, iconSize, alpha);

        float scale = Math.max(0.25F, Math.min(8F,
                parseTextScaleSafe(element.attribute("textSize", "1"), 1F)))
                * (float) canvasRenderScale();
        String progress = element.attribute("entryProgress", "0/1");
        int progressWidth = Math.round(font.width(progress) * scale);
        int textLeft = iconX + iconSize + canvasPixels(5);
        int progressX = x + elementWidth - paddingRight - progressWidth;
        int available = Math.max(1, Math.round((progressX - canvasPixels(6) - textLeft) / scale));
        String label = font.plainSubstrByWidth(element.value(), available);
        int textY = y + (elementHeight - Math.round(font.lineHeight * scale)) / 2;
        int color = applyAlpha(safeColor(element.attribute("textColor", "#ffffff"), 0xFFFFFFFF), alpha);
        g.enableScissor(x, y, x + elementWidth, y + elementHeight);
        g.pose().pushPose();
        g.pose().translate(textLeft, textY, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, label, 0, 0, color, false);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(progressX, textY, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, progress, 0, 0, color, false);
        g.pose().popPose();
        g.disableScissor();
    }

    private float buttonTextScale(LayoutEditorState.Element element) {
        return Math.clamp(parseTextScaleSafe(element.attribute("textSize", "1"), 1F), 0.25F, 8F);
    }

    private Component buttonLabel(LayoutEditorState.Element element) {
        if (element.kind().equals("structure_enter"))
            return QuestRichText.parse(element.attribute("displayText", "").isBlank()
                    ? text("structure.enter").getString() : element.attribute("displayText", ""), styledText(element, "").getStyle());
        return QuestRichText.parse(elementDisplayValue(element), styledText(element, "").getStyle());
    }

    private void renderButtonElement(GuiGraphics g, LayoutEditorState.Element element, String state,
                                     int x, int y, int width, int height, float alpha) {
        Map<String, String> attributes = new LinkedHashMap<>(element.attributes());
        attributes.put("textColor", appearance(element, "textColor", state, "#ffffff"));
        if (!ButtonControlContent.hasImage(element)) {
            renderTextElement(g, new LayoutEditorState.Element(element.id(), element.quest(), element.kind(),
                    element.value(), width, height, attributes), x, y, width, height, alpha);
            return;
        }
        int left = Math.min(width, canvasPixels(paddingLeft(element)));
        int top = Math.min(height, canvasPixels(paddingTop(element)));
        int contentWidth = Math.max(0, width - left - canvasPixels(paddingRight(element)));
        int contentHeight = Math.max(0, height - top - canvasPixels(paddingBottom(element)));
        var content = ButtonControlContent.layout(element, font, buttonLabel(element),
                buttonTextScale(element) * (float) canvasRenderScale(), canvasRenderScale(), contentWidth, contentHeight);
        x += left;
        y += top;
        g.enableScissor(x, y, x + contentWidth, y + contentHeight);
        var image = content.image();
        String source = ButtonControlContent.image(element, state);
        if (ButtonControlContent.present(source) && image.width() > 0 && image.height() > 0)
            QuestIcons.render(g, source, x + image.x(), y + image.y(), image.width(), image.height(), alpha);
        var label = content.text();
        if (label.width() > 0 && label.height() > 0) {
            for (String key : List.of("paddingLeft", "paddingTop", "paddingRight", "paddingBottom")) attributes.put(key, "0");
            attributes.put("gravity", "top|center_horizontal");
            renderTextElement(g, new LayoutEditorState.Element(element.id(), element.quest(), element.kind(),
                    element.value(), label.width(), label.height(), attributes),
                    x + label.x(), y + label.y(), label.width(), label.height(), alpha);
        }
        g.disableScissor();
    }

    private void renderTextElement(GuiGraphics g, LayoutEditorState.Element element,
                                   int x, int y, int elementWidth, int elementHeight, float alpha) {
        int paddingLeft = canvasPixels(paddingLeft(element));
        int paddingTop = canvasPixels(paddingTop(element));
        int paddingRight = canvasPixels(paddingRight(element));
        int paddingBottom = canvasPixels(paddingBottom(element));
        float scale = Math.max(0.25F, Math.min(8F,
                parseTextScaleSafe(element.attribute("textSize", "1"), 1F)))
                * (float) canvasRenderScale();
        int color = applyAlpha(safeColor(element.attribute("textColor", "#ffffff"), 0xFFFFFFFF), alpha);
        int shadowColor = applyAlpha(safeColor(element.attribute("shadowColor", "#000000"), 0xFF000000), alpha);
        int shadowRadius = Math.min(4, Math.max(0,
                Math.round(parseFloat(element.attribute("shadowRadius", "0"), 0))));
        int shadowDx = parseInteger(element.attribute("shadowDx", "0"), 0);
        int shadowDy = parseInteger(element.attribute("shadowDy", "0"), 0);
        shadowRadius = canvasPixels(shadowRadius);
        shadowDx = Math.round(shadowDx * (float) canvasRenderScale());
        shadowDy = Math.round(shadowDy * (float) canvasRenderScale());
        var component = (element.kind().equals("text") || element.kind().equals("button"))
                ? QuestRichText.parse(element.value(), styledText(element, "").getStyle())
                : styledText(element, element.value());
        int availableWidth = Math.max(1, Math.round((elementWidth - paddingLeft - paddingRight) / scale));
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(component, availableWidth));
        int maxLines = Math.max(0, parseInteger(element.attribute("maxLines", "0"), 0));
        if (maxLines > 0 && lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
        }
        int lineHeight = Math.max(1, Math.round((font.lineHeight + 1) * scale));
        int blockHeight = lines.size() * lineHeight;
        String gravity = normalizeGravity(element.attribute("gravity", "top_left"));
        int lineY = alignedY(gravity, y + paddingTop,
                Math.max(1, elementHeight - paddingTop - paddingBottom), blockHeight, 0, 0);
        g.enableScissor(x, y, x + elementWidth, y + elementHeight);
        for (var line : lines) {
            int renderedWidth = Math.round(font.width(line) * scale);
            int lineX = alignedX(gravity, x + paddingLeft,
                    Math.max(1, elementWidth - paddingLeft - paddingRight), renderedWidth, 0, 0);
            g.pose().pushPose();
            g.pose().translate(lineX, lineY, 0);
            g.pose().scale(scale, scale, 1);
            if ((shadowDx != 0 || shadowDy != 0) && (shadowColor >>> 24) != 0) {
                int sx = Math.round(shadowDx / scale);
                int sy = Math.round(shadowDy / scale);
                if (shadowRadius == 0) {
                    g.drawString(font, line, sx, sy, shadowColor, false);
                } else {
                    int radius = Math.max(1, Math.round(shadowRadius / scale));
                    for (int ox = -radius; ox <= radius; ox += radius) {
                        for (int oy = -radius; oy <= radius; oy += radius) {
                            if (ox != 0 || oy != 0) {
                                g.drawString(font, line, sx + ox, sy + oy, shadowColor, false);
                            }
                        }
                    }
                }
            }
            g.drawString(font, line, 0, 0, color, false);
            g.pose().popPose();
            lineY += lineHeight;
        }
        g.disableScissor();
    }

    /** Genshin objective bar inside authored layouts: hollow diamond, objective text, right-aligned progress. */
    private void renderObjectiveElement(GuiGraphics g, LayoutEditorState.Element element,
                                        int x, int y, int elementWidth, int elementHeight, float alpha) {
        float renderScale = (float) canvasRenderScale();
        float scale = Math.max(0.25F, Math.min(8F,
                parseTextScaleSafe(element.attribute("textSize", "1"), 1F))) * renderScale;
        int color = applyAlpha(safeColor(element.attribute("textColor", "#ffffff"), 0xFFFFFFFF), alpha);
        int gold = applyAlpha(JournalDetailRenderer.GOLD, alpha);
        String overridden = ClientControlState.properties(element.quest(), QuestPresentationClient.sourceId(element))
                .get("value");
        String configured = progressDisplayName(element);
        String label = overridden != null ? overridden
                : configured.isBlank() ? QuestText.objective(selected).getString() : configured;
        var view = ClientQuestProgress.view(selected.id());
        String progress = view != null && view.required() > 1 && !view.completed()
                ? "(" + view.current() + "/" + view.required() + ")" : "";
        int left = x + canvasPixels(paddingLeft(element));
        int right = x + elementWidth - canvasPixels(paddingRight(element));
        int centerY = y + elementHeight / 2;
        int radius = Math.max(3, Math.round(5 * renderScale));
        g.enableScissor(x, y, x + elementWidth, y + elementHeight);
        JournalDetailRenderer.diamondRing(g, left + radius, centerY, radius, gold);
        MailDecor.diamond(g, left + radius, centerY, Math.max(1, radius - 3), gold);
        int textLeft = left + radius * 2 + Math.round(6 * renderScale);
        int progressWidth = progress.isEmpty() ? 0 : Math.round(font.width(progress) * scale);
        int available = Math.max(1, Math.round((right - textLeft - progressWidth - 6) / scale));
        int textY = centerY - Math.round(font.lineHeight * scale / 2F);
        JournalDetailRenderer.scaled(g, font, font.plainSubstrByWidth(label, available), textLeft, textY,
                scale, color, false);
        if (!progress.isEmpty())
            JournalDetailRenderer.scaled(g, font, progress, right - progressWidth, textY, scale, color, false);
        g.disableScissor();
    }

    private String progressDisplayName(LayoutEditorState.Element element) {
        String value = element.attribute("displayText", "");
        if (editMode) return value;
        return ClientControlState.properties(element.quest(), QuestPresentationClient.sourceId(element))
                .getOrDefault("displayText", value);
    }

    private static float parseTextScaleSafe(String value, float fallback) {
        try {
            return parseTextScale(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int applyAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24;
        int applied = Math.max(0, Math.min(255, Math.round(sourceAlpha * alpha)));
        return (color & 0x00FFFFFF) | applied << 24;
    }

    static int safeColor(String value, int fallback) {
        try {
            return parseColor(value, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    protected void renderContents(GuiGraphics g, int x, int y, float tick) {
        if (iconPicker != null) {
            iconPicker.scroll.advance();
            updateIconPickerViewport();
        }
        clearFrameElements();
        if (form != null && iconPicker == null) {
            if (hasChoicePopup()) form.motion.freeze();
            form.motion.advance();
            form.scroll = form.motion.position();
            updateFormViewport();
        }
        if (overviewMode && !editMode && form == null && iconPicker == null) {
            super.renderContents(g, x, y, tick);
            renderContextMenu(g, x, y, tick);
            return;
        }
        markSelectedTaskRead();
        if (readAllPending != null && readAllPending.isDone()) {
            String result = readAllPending.join();
            readAllPending = null;
            if (!List.of("all_read", "already_read").contains(result)) actionMessage = result;
        }
        var readAllButton = controls.get("system:mark-all-read");
        if (readAllButton != null)
            readAllButton.active = readAllPending == null && (ClientQuestProgress.hasUnreadTasks() || QuestUnlockToast.isPresenting());
        for (var row : viewportRows) {
            if (row.quest() == null) continue;
            var button = controls.get(row.key());
            var progress = ClientQuestProgress.view(row.quest().id());
            if (button instanceof JournalListButton taskButton)
                taskButton.newTask(!editMode && progress != null && progress.newTask()
                        && !readMarkedTasks.contains(row.quest().id()));
        }
        refreshSearchResults();
        animateList();
        animateDetails();
        animateCategories();
        animateHierarchy();
        super.renderContents(g, x, y, tick);
        renderEditorViewIndicators(g);
        if (coordinateEditor != null && form == null && iconPicker == null) {
            coordinateEditor.setX(coordinateLeft(coordinateAxis));
            coordinateEditor.setY(height - 49);
            coordinateEditor.render(g, x, y, tick);
        }
        if (listAvailable()) {
            g.enableScissor(Math.max(0, listLeft() - 12), listTop(), listRight(), listBottom());
            for (var row : viewportRows) {
                if (row.section()) {
                    int rowY = listRowY(row);
                    if (rowY + row.height() > listTop() && rowY < listBottom()) {
                        CategoryIconRenderer.render(g, categoryIcon(row.category()), listLeft() + 4, rowY + 4, 18, 0xFFECE8DF);
                        g.drawString(font, font.plainSubstrByWidth(categoryLabel(row.category()).getString(),
                                listWidth - 32), listLeft() + 28, rowY + 8, 0xFFECE8DF, false);
                    }
                    continue;
                }
                StyledButton button = controls.get(row.key());
                if (button == null || !button.visible) continue;
                if (row.group() != null) {
                    if (selectedGroupIds.contains(row.group().id()) && !button.selectionHighlighted()) {
                        g.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), 0xFFF2CF83);
                    }
                    continue;
                }
                var quest = row.quest();
                g.enableScissor(Math.max(0, listLeft() - 12), groupClipTop(row.key()),
                        listRight(), groupClipBottom(row.key()));
                if (editMode && row.indent() > 0) {
                    g.fill(listLeft() + 5, button.getY() - 4, listLeft() + 6, button.getBottom(), 0xFF8A969F);
                }
                if (!editMode && button instanceof JournalListButton rowButton)
                    rowButton.renderSelectionArrow(g, System.nanoTime());
                if (selectedTaskIds.contains(quest.id()) && !button.selectionHighlighted()) {
                    g.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), 0xFFF2CF83);
                }
                if (quest.id().equals(movingTask)) {
                    g.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), 0xFF85D6AA);
                }
                g.disableScissor();
            }
            g.disableScissor();
            if (maxListScroll() > 0) {
                g.fill(listRight() - 3, listTop(), listRight(), listBottom(), 0xFF383F50);
                g.fill(listRight() - 3, thumbTop(), listRight(), thumbTop() + thumbHeight(),
                        listScrollbarDragging ? 0xFFF3D38B : 0xFFE6DFD1);
            }
        }
        if (form == null && iconPicker == null && maxDetailScroll() > 0) {
            int right = width - 16;
            g.fill(right - 3, detailsTop(), right, detailContentBottom(), 0x22303848);
            g.fill(right - 3, detailThumbTop(), right, detailThumbTop() + detailThumbHeight(),
                    detailScrollbarDragging ? 0xFFF3D38B : 0xFF9CA7B5);
        }
        if (form == null && iconPicker == null && !overviewMode && maxCategoryScroll(categories().size() + (editMode ? 1 : 0)) > 0) {
            int l = categoryViewportLeft(), r = categoryViewportRight(), y0 = categoryY() + categoryHeight() + 1;
            int view = r - l, content = categoryContentWidth(categories().size() + (editMode ? 1 : 0));
            int thumb = Math.max(12, view * view / Math.max(1, content));
            int tx = l + (int) Math.round(categoryScroll / Math.max(1, content - view) * (view - thumb));
            g.fill(l, y0, r, y0 + 1, 0x8059606B);
            g.fill(tx, y0, tx + thumb, y0 + 1, 0xFFE6DFD1);
        }
        if (!movingTab.isBlank() && form == null && iconPicker == null) {
            StyledButton tab = controls.get("system:category-" + movingTab);
            if (tab != null && tab.visible) {
                g.enableScissor(categoryViewportLeft(), categoryY(), categoryViewportRight(),
                        categoryY() + categoryHeight());
                g.fill(tab.getX() + 4, tab.getY(), tab.getRight() - 4, tab.getY() + 2, 0xFF85D6AA);
                g.disableScissor();
            }
        }
        renderContextMenu(g, x, y, tick);
        renderTaskReorder(g, tick);
    }

    private void renderContextMenu(GuiGraphics g, int x, int y, float tick) {
        if (!menu.isEmpty()) {
            g.flush();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                    net.minecraft.client.Minecraft.ON_OSX);
            menuMotion.advance();
            menuScroll = menuMotion.pixels();
            updateMenuViewport();
        }
        renderAnimatedMenu(g, "context", menu.isEmpty() ? null : menu.getFirst(),
                menu.isEmpty() ? viewport(0, 0, 0, 0) : viewport(menu.getFirst().getX() - 3,
                        menuTop() - 3, menu.getFirst().getRight() + 3,
                        menuTop() + Math.min(height - 16, menu.size() * 20) + 3),
                () -> {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    var first = menu.getFirst();
                    int top = menuTop(), visibleHeight = Math.min(height - 16, menu.size() * 20);
                    g.fill(first.getX() - 3, top - 3, first.getRight() + 3, top + visibleHeight + 3, 0xFF171B21);
                    g.renderOutline(first.getX() - 2, top - 2, first.getWidth() + 4, visibleHeight + 4, 0xFF9DB4D2);
                    g.enableScissor(first.getX(), top, first.getRight(), top + visibleHeight);
                    menu.forEach(button -> button.render(g, x, y, tick));
                    renderContextGroupMarkers(g, top);
                    g.disableScissor();
                    g.pose().popPose();
                });
    }

    private void renderContextGroupMarkers(GuiGraphics g, int top) {
        for (int start = 0; start < menu.size() && start < menuRowGroups.size();) {
            int end = start + 1;
            String group = menuRowGroups.get(start);
            while (end < menu.size() && end < menuRowGroups.size()
                    && menuRowGroups.get(end).equals(group)) end++;
            int firstY = top + start * 20 - menuScroll;
            int lastY = top + end * 20 - menuScroll;
            g.fill(menu.getFirst().getX() + 3, firstY + 3, menu.getFirst().getX() + 5,
                    Math.max(firstY + 4, lastY - 3), contextGroupColor(group));
            start = end;
        }
    }

    private static int contextGroupColor(String group) {
        return switch (group) {
            case "edit" -> 0xFF79B8D8;
            case "organize" -> 0xFF9DC58C;
            case "progress" -> 0xFFF0C36D;
            case "danger" -> 0xFFE17C72;
            default -> 0xFFAAB7D2;
        };
    }

    private final class ContextMenuButton extends StyledButton {
        private final String key;
        private final String group;

        private ContextMenuButton(int x, int y, int width, int height, Component label,
                                  String key, String group, OnPress action) {
            super(x, y, width, height, label, action, styles, "context-" + key);
            this.key = key;
            this.group = group;
            setTooltip(Tooltip.create(Component.translatable(
                    "screen.xero_quest_journal.context.group." + group).copy().append("\n").append(label)));
        }

        @Override
        protected void renderContent(GuiGraphics g, ButtonStyle style, int color) {
            int iconColor = contextGroupColor(group);
            drawContextIcon(g, key, getX() + 9, getY() + 5, iconColor);
            var font = QuestScreen.this.font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - 36));
            g.drawString(font, label, getX() + 29, getY() + (getHeight() - font.lineHeight) / 2,
                    color, style.textShadow());
        }
    }

    private static void drawContextIcon(GuiGraphics g, String key, int x, int y, int color) {
        switch (key) {
            case "delete", "delete_tasks", "delete_groups", "delete_quest_type" -> {
                g.fill(x + 2, y + 2, x + 11, y + 4, color);
                g.fill(x + 4, y, x + 9, y + 2, color);
                g.fill(x + 3, y + 4, x + 10, y + 11, color);
                g.fill(x + 5, y + 5, x + 6, y + 10, 0xFF24272D);
                g.fill(x + 8, y + 5, x + 9, y + 10, 0xFF24272D);
            }
            case "copy_id", "copy_tasks", "duplicate_tasks" -> {
                g.fill(x + 1, y + 2, x + 8, y + 3, color);
                g.fill(x + 1, y + 3, x + 2, y + 10, color);
                g.fill(x + 2, y + 10, x + 9, y + 11, color);
                g.fill(x + 8, y + 3, x + 9, y + 10, color);
                g.fill(x + 4, y, x + 11, y + 1, color);
                g.fill(x + 10, y + 1, x + 11, y + 8, color);
                g.fill(x + 4, y + 8, x + 10, y + 9, color);
            }
            case "graph.overview", "graph.settings", "unlock_graph.title" -> {
                g.fill(x + 1, y + 2, x + 5, y + 6, color);
                g.fill(x + 8, y, x + 12, y + 4, color);
                g.fill(x + 8, y + 8, x + 12, y + 12, color);
                g.fill(x + 4, y + 3, x + 9, y + 4, color);
                g.fill(x + 5, y + 3, x + 6, y + 9, color);
                g.fill(x + 5, y + 8, x + 9, y + 9, color);
            }
            case "order.move_task", "layer_up", "layer_down", "layer_front", "layer_back" -> {
                g.fill(x + 5, y, x + 8, y + 2, color);
                g.fill(x + 3, y + 2, x + 10, y + 3, color);
                g.fill(x + 5, y + 3, x + 8, y + 6, color);
                g.fill(x + 5, y + 6, x + 8, y + 9, 0xFFB9C4D1);
                g.fill(x + 3, y + 9, x + 10, y + 10, 0xFFB9C4D1);
                g.fill(x + 5, y + 10, x + 8, y + 12, 0xFFB9C4D1);
            }
            case "paste_tasks", "paste" -> {
                g.fill(x + 2, y + 2, x + 11, y + 12, color);
                g.fill(x + 5, y, x + 9, y + 3, 0xFF24272D);
                g.fill(x + 4, y + 4, x + 9, y + 5, 0xFF24272D);
                g.fill(x + 4, y + 7, x + 9, y + 8, 0xFF24272D);
            }
            case "reset_progress", "reset_selected_progress", "reset_position" -> {
                g.fill(x + 4, y, x + 9, y + 2, color);
                g.fill(x + 1, y + 2, x + 4, y + 4, color);
                g.fill(x, y + 4, x + 2, y + 9, color);
                g.fill(x + 2, y + 9, x + 5, y + 11, color);
                g.fill(x + 5, y + 10, x + 10, y + 12, color);
                g.fill(x + 6, y + 5, x + 11, y + 6, color);
                g.fill(x + 10, y + 5, x + 12, y + 9, color);
                g.fill(x + 8, y + 3, x + 11, y + 6, color);
            }
            case "properties", "conditions.edit", "template.edit", "layout.canvas_settings" -> {
                g.fill(x + 2, y + 2, x + 5, y + 10, color);
                g.fill(x + 5, y + 1, x + 7, y + 9, color);
                g.fill(x + 7, y + 3, x + 9, y + 11, color);
                g.fill(x + 9, y + 2, x + 11, y + 10, color);
            }
            case "add_task", "add_group", "add_chapter" -> {
                g.fill(x + 5, y, x + 8, y + 12, color);
                g.fill(x, y + 5, x + 12, y + 8, color);
            }
            default -> {
                g.fill(x + 2, y + 1, x + 10, y + 2, color);
                g.fill(x + 1, y + 2, x + 2, y + 10, color);
                g.fill(x + 10, y + 2, x + 11, y + 10, color);
                g.fill(x + 2, y + 10, x + 10, y + 11, color);
                g.fill(x + 4, y + 4, x + 8, y + 8, color);
            }
        }
    }

    private void renderEditorViewIndicators(GuiGraphics graphics) {
        if (!editMode) return;
        drawEditorViewIndicator(graphics, "system:editor-grid", showEditorGrid);
        drawEditorViewIndicator(graphics, "system:editor-axes", showEditorAxes);
        drawEditorViewIndicator(graphics, "system:editor-bounds", showEditorBounds);
    }

    private void drawEditorViewIndicator(GuiGraphics graphics, String id, boolean enabled) {
        StyledButton button = controls.get(id);
        if (button == null || !button.visible) return;
        int color = enabled ? 0xFFFFD071 : 0xFF626A76;
        graphics.fill(button.getRight() - 6, button.getBottom() - 6,
                button.getRight() - 2, button.getBottom() - 2, color);
    }

    private void overviewEditMenu() {
        if (!canAuthor()) return;
        var entries = new ArrayList<Map.Entry<String, Runnable>>();
        entries.add(Map.entry("overview_edit.all_tasks", this::enterAllTaskEditor));
        entries.add(Map.entry("overview_background.edit",
                () -> minecraft.setScreen(new OverviewBackgroundScreen(this))));
        if (editMode) entries.add(Map.entry("preview", () -> {
            editMode = false;
            movingTask = movingTab = "";
            init();
        }));
        showAuthorMenu(controls.get("system:edit"), "overview-edit", entries);
    }

    private void enterAllTaskEditor() {
        if (!canAuthor()) return;
        editMode = true;
        systemLayout = hierarchyOpen = false;
        movingTask = movingTab = "";
        category = "active";
        groupId = chapterId = searchQuery = "";
        listPage = 0;
        resetListScroll();
        error = null;
        init();
    }

    private void globalAddMenu() {
        if (!canAuthor() || !editMode) return;
        showAuthorMenu(controls.get("system:add-task"), "global-add", List.of(
                Map.entry("add_group", () -> {
                    openForm("group", "normal");
                    form.targetGroup = form.targetChapter = "";
                    init();
                }),
                Map.entry("add_task", () -> openTaskInGroup(groupId))));
    }

    private void groupAddMenu(String id) {
        if (!canAuthor() || !editMode
                || ClientQuestDefinitions.groups().stream().noneMatch(group -> group.id().equals(id))) return;
        showAuthorMenu(controls.get("group:" + id), "group-add:" + id, List.of(
                Map.entry("group_add.task_bottom", () -> openTaskInGroup(id, "",
                        new QuestJournalOrderStore.Placement("task_bottom", id))),
                Map.entry("group_add.task_top", () -> openTaskInGroup(id, "",
                        new QuestJournalOrderStore.Placement("task_top", id))),
                Map.entry("group_add.group_bottom", () -> openSiblingGroup(id, false)),
                Map.entry("group_add.group_top", () -> openSiblingGroup(id, true))));
    }

    private void openSiblingGroup(String id, boolean before) {
        var anchor = ClientQuestDefinitions.groups().stream().filter(group -> group.id().equals(id))
                .findFirst().orElse(null);
        if (anchor == null || !canAuthor()) return;
        openForm("group", anchor.type());
        form.type = anchor.type();
        form.targetGroup = form.targetChapter = "";
        form.placement = new QuestJournalOrderStore.Placement(before ? "group_before" : "group_after", id);
        init();
    }

    private void showAuthorMenu(StyledButton anchor, String target, List<Map.Entry<String, Runnable>> entries) {
        if (!canAuthor() || anchor == null || pending != null) return;
        menu.clear();
        menuTarget = target;
        menuScroll = 0;
        menuMotion.jump(0);
        int w = Math.min(width - 16, Math.max(180, entries.stream()
                .mapToInt(entry -> font.width(text(entry.getKey())) + 24).max().orElse(180)));
        int x = Math.clamp(anchor.getX(), 8, Math.max(8, width - w - 8));
        menuX = x;
        menuY = Math.clamp(anchor.getBottom() + 3, 8, Math.max(8, height - entries.size() * 20 - 8));
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            menu.add(new StyledButton(x, menuY + index * 20, w, 20, text(entry.getKey()), ignored -> {
                menu.clear();
                menuTarget = null;
                if (canAuthor()) entry.getValue().run();
            }, styles, "context-" + entry.getKey()));
        }
        updateMenuViewport();
    }

    private boolean clickContextMenu(double x, double y, int button) {
        updateMenuViewport();
        var first = menu.getFirst();
        if (button == 0 && x >= first.getX() && x < first.getRight()
                && y >= menuTop() && y < menuTop() + Math.min(height - 16, menu.size() * 20)) {
            menuDragging = true;
            menuMoved = false;
            menuDragX = x;
            menuDragY = y;
            menuDragScroll = menuMotion.position();
            menuMotion.freeze();
            menuPressed = menu.stream().filter(entry -> entry.visible && entry.isMouseOver(x, y))
                    .findFirst().orElse(null);
            return true;
        }
        menu.clear();
        menuTarget = null;
        return true;
    }

    private void contextMenu(int x, int y, String target) {
        if (editMode && "system:add-task".equals(target)) {
            globalAddMenu();
            return;
        }
        menu.clear();
        menuRowGroups.clear();
        menuScroll = 0;
        menuMotion.jump(0);
        if (!editMode) {
            return;
        }
        menuTarget = target;
        menuX = x;
        menuY = y;
        if (isFixedSystemControl(target)) {
            return;
        }
        elementParent = isLayoutContainer(target) ? target : null;
        List<Map.Entry<String, Runnable>> entries = new ArrayList<>();
        if (target != null && target.startsWith("system:category-")) {
            if (!target.equals("system:category-add")
                    && !target.equals("system:category-previous")
                    && !target.equals("system:category-next")) {
                String id = target.substring("system:category-".length());
                entries.add(Map.entry("copy_id", () -> copyId(id)));
                entries.add(Map.entry("properties", () -> minecraft.setScreen(new QuestTypeScreen(this, id))));
                if (!id.equals("active")) {
                    entries.add(Map.entry("delete_quest_type", () -> confirmDeleteQuestType(id)));
                }
            }
        } else if ("group-properties".equals(target) || "chapter-properties".equals(target)
                || target != null && target.startsWith("group:")) {
            boolean group = !"chapter-properties".equals(target);
            String targetGroup = target.startsWith("group:") ? target.substring("group:".length()) : groupId;
            entries.add(Map.entry("properties", () -> editProperties(group ? "group" : "chapter", group ? targetGroup : chapterId)));
            entries.add(Map.entry(group ? "add_group" : "add_chapter", () -> openForm(group ? "group" : "chapter", "normal")));
            if (group) {
                entries.add(Map.entry("select_group_tasks", () -> selectGroupTasks(targetGroup, false)));
                entries.add(Map.entry("invert_group_tasks", () -> selectGroupTasks(targetGroup, true)));
                entries.add(Map.entry("add_task", () -> openTaskInGroup(targetGroup)));
                entries.add(Map.entry("add_chapter", () -> {
                    openForm("chapter", "normal");
                    form.targetGroup = targetGroup;
                    init();
                }));
                entries.add(Map.entry("select_groups", this::groupSelectionMenu));
                entries.add(Map.entry("paste_tasks", () -> pasteClipboard(targetGroup, "")));
                entries.add(Map.entry("delete_groups", () -> confirmDelete(Set.of(),
                        selectedGroupIds.contains(targetGroup) ? Set.copyOf(selectedGroupIds) : Set.of(targetGroup))));
            } else {
                entries.add(Map.entry("paste_tasks", () -> pasteClipboard(groupId, chapterId)));
            }
        } else if (target != null && target.startsWith("quest:")) {
            String id = target.substring("quest:".length());
            if (!selectedTaskIds.contains(id)) {
                selectedTaskIds.clear();
                selectedTaskIds.add(id);
            }
            entries.add(Map.entry("copy_id", () -> copyId(id)));
            if (canAuthor()) entries.add(Map.entry("order.move_task", () -> startTaskMove(id)));
            entries.add(Map.entry("unlock_graph.title",
                    () -> minecraft.setScreen(new QuestUnlockGraphScreen(this, id))));
            entries.add(Map.entry("layout.canvas_settings", () -> openCanvasSize(id)));
            if (canAuthor()) {
                entries.add(Map.entry("graph.overview", () -> openTriggerOverview(id)));
                entries.add(Map.entry("properties", () -> editProperties("task", id)));
                entries.add(Map.entry("conditions.edit", () -> openConditionsEditor(id)));
                var task = questById(id);
                if (task != null) ClientQuestDefinitions.templates().stream()
                        .filter(t -> t.id().equals(task.templateId())).findFirst().ifPresent(template ->
                                entries.add(Map.entry("template.edit", () ->
                                        minecraft.setScreen(new QuestTemplateEditorScreen(this, template)))));
                entries.add(Map.entry("duplicate_tasks", () -> duplicateTasks(Set.copyOf(selectedTaskIds), id)));
                entries.add(Map.entry("copy_tasks", () -> copyTasks(Set.copyOf(selectedTaskIds))));
                entries.add(Map.entry("paste_tasks", () -> pasteClipboard(
                        groupId.isEmpty() ? ClientQuestDefinitions.groupOf(id) : groupId, chapterId)));
                entries.add(Map.entry("delete_tasks", () -> confirmDelete(Set.copyOf(selectedTaskIds), Set.of())));
            }
            Set<String> resetTargets = selectedTaskIds.contains(id) && selectedTaskIds.size() > 1
                    ? Set.copyOf(selectedTaskIds) : Set.of(id);
            entries.add(Map.entry(resetTargets.size() > 1 ? "reset_selected_progress" : "reset_progress",
                    () -> confirmReset(resetTargets)));
        } else if (target != null && target.startsWith("system:") && !target.startsWith("system:add-")) {
            entries.add(Map.entry("system_layout", () -> { systemLayout = !systemLayout; init(); }));
            entries.add(Map.entry("snap", () -> { layout.setSnapToGrid(!layout.snapToGrid()); saveLayout(); }));
            entries.add(Map.entry("reset_position", () -> { pushUndo(); layout.reset(target); saveLayout(); init(); }));
        } else if (target != null && layout.elements().containsKey(target)) {
            if (inDetails(x, y)) entries.add(Map.entry("layout.canvas_settings",
                    () -> openCanvasSize(selectedId)));
            entries.add(Map.entry("copy_id", () -> copyControlId(target)));
            entries.add(Map.entry("properties", () -> editElement(target)));
            entries.add(Map.entry("graph.settings", () -> openTriggerGraph(target)));
            if (layout.elements().get(target).kind().equals("quest_rewards")) {
                entries.add(Map.entry("rewards.edit", this::openRewardsEditor));
            }
            if (isLayoutContainerKind(layout.elements().get(target).kind())) {
                entries.add(Map.entry("put_into_layout", () -> layoutContentsMenu(target)));
                if (inDetails(x, y)) addLayoutEntries(entries);
            }
            entries.add(Map.entry("delete", () -> deleteElements(Set.of(target))));
            entries.add(Map.entry("reset_position", () -> { pushUndo(); layout.reset(target); saveLayout(); init(); }));
        } else if (target != null && target.startsWith("system:add-")
                || listAvailable() && x >= listLeft() && x < listRight()
                && y >= 78 && y < listBottom()) {
            entries.add(Map.entry("add_task", () -> openTaskInGroup(groupId)));
            entries.add(Map.entry("add_group", () -> openForm("group", "normal")));
            entries.add(Map.entry("add_chapter", () -> openForm("chapter", "normal")));
            entries.add(Map.entry("paste_tasks", () -> pasteClipboard(groupId, chapterId)));
        } else if (editMode && selected != null && inDetails(x, y)) {
            entries.add(Map.entry("layout.canvas_settings", () -> openCanvasSize(selectedId)));
            addLayoutEntries(entries);
        }
        int w = Math.min(250, Math.max(210, entries.stream()
                .mapToInt(entry -> font.width(text(entry.getKey())) + 40).max().orElse(210)));
        w = Math.min(Math.max(1, width - 16), w);
        var preferences = ClientPreferences.get();
        int viewportHeight = Math.min(height - 16, entries.size() * 20);
        int requestedX = x, requestedY = y;
        if (target != null && target.startsWith("quest:") && preferences.fixedQuestContextMenuPosition) {
            requestedX = 8 + Math.max(0, width - w - 16) * preferences.questContextMenuXPercent / 100;
            requestedY = 8 + Math.max(0, height - viewportHeight - 16)
                    * preferences.questContextMenuYPercent / 100;
        }
        int left = Math.max(8, Math.min(requestedX, width - w - 8));
        int top = Math.max(8, Math.min(requestedY, height - viewportHeight - 8));
        menuX = left;
        menuY = top;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            String group = contextMenuGroup(target, entry.getKey());
            menuRowGroups.add(group);
            StyledButton button = new ContextMenuButton(left, top + i * 20, w, 20, text(entry.getKey()),
                    entry.getKey(), group, ignored -> {
                menu.clear();
                menuTarget = null;
                entry.getValue().run();
            });
            menu.add(button);
        }
        updateMenuViewport();
    }

    private static String contextMenuGroup(String target, String key) {
        if (target != null && target.startsWith("quest:")) {
            return switch (key) {
                case "copy_id", "order.move_task", "unlock_graph.title", "layout.canvas_settings" -> "task";
                case "graph.overview", "properties", "conditions.edit", "template.edit" -> "edit";
                case "duplicate_tasks", "copy_tasks", "paste_tasks" -> "organize";
                case "reset_progress", "reset_selected_progress" -> "progress";
                case "delete_tasks" -> "danger";
                default -> "task";
            };
        }
        if (key.contains("delete")) return "danger";
        if (key.startsWith("add_") || key.equals("paste_tasks") || key.equals("put_into_layout"))
            return "organize";
        if (key.startsWith("reset_")) return "progress";
        if (key.contains("properties") || key.endsWith(".edit") || key.equals("graph.settings"))
            return "edit";
        return "task";
    }

    private void selectGroupTasks(String targetGroup, boolean invert) {
        var ids = ClientQuestDefinitions.all().stream()
                .filter(quest -> targetGroup.equals(ClientQuestDefinitions.groupOf(quest.id())))
                .map(QuestDefinition::id).toList();
        if (invert) {
            for (String id : ids) {
                if (!selectedTaskIds.remove(id)) selectedTaskIds.add(id);
            }
        } else selectedTaskIds.addAll(ids);
        actionMessage = null;
        init();
    }

    private void addLayoutEntries(List<Map.Entry<String, Runnable>> entries) {
        entries.add(Map.entry("add_button", () -> openForm("button", "normal")));
        entries.add(Map.entry("add_structure_preview", () -> openForm("structure_preview", "normal")));
        entries.add(Map.entry("add_structure_enter", () -> openForm("structure_enter", "normal")));
        entries.add(Map.entry("add_rewards", () -> openForm("quest_rewards", "normal")));
        entries.add(Map.entry("rewards.edit", this::openRewardsEditor));
        entries.add(Map.entry("conditions.edit", () -> openConditionsEditor(selectedId)));
        entries.add(Map.entry("add_text", () -> openForm("text", "normal")));
        entries.add(Map.entry("add_quest_entry_text", () -> openForm("quest_entry_text", "normal")));
        entries.add(Map.entry("add_item_display", () -> openForm("item_display", "normal")));
        entries.add(Map.entry("add_image", () -> openForm("image", "normal")));
        entries.add(Map.entry("add_video", () -> openForm("video", "normal")));
        entries.add(Map.entry("add_audio", () -> openForm("audio", "normal")));
        entries.add(Map.entry("add_linear", () -> openForm("linear", "normal")));
        entries.add(Map.entry("add_relative", () -> openForm("relative", "normal")));
        entries.add(Map.entry("add_scroll", () -> openForm("scroll", "normal")));
    }

    private List<Map.Entry<String, Runnable>> layoutInsertEntries() {
        return List.of(
                Map.entry("add_button", () -> openForm("button", "normal")),
                Map.entry("add_structure_preview", () -> openForm("structure_preview", "normal")),
                Map.entry("add_structure_enter", () -> openForm("structure_enter", "normal")),
                Map.entry("add_text", () -> openForm("text", "normal")),
                Map.entry("add_quest_entry_text", () -> openForm("quest_entry_text", "normal")),
                Map.entry("add_item_display", () -> openForm("item_display", "normal")),
                Map.entry("add_image", () -> openForm("image", "normal")),
                Map.entry("add_video", () -> openForm("video", "normal")),
                Map.entry("add_audio", () -> openForm("audio", "normal")),
                Map.entry("add_linear", () -> openForm("linear", "normal")),
                Map.entry("add_relative", () -> openForm("relative", "normal")),
                Map.entry("add_scroll", () -> openForm("scroll", "normal")),
                Map.entry("add_rewards", () -> openForm("quest_rewards", "normal")));
    }

    private boolean isLayoutContainer(String id) {
        if (id == null) return false;
        var element = layout.elements().get(id);
        return element != null && isLayoutContainerKind(element.kind());
    }

    private static boolean isLayoutContainerKind(String kind) {
        return List.of("linear", "relative", "scroll").contains(kind);
    }

    private void openLayoutInsertMenu() {
        if (!editMode || selected == null) {
            return;
        }
        if (selectedLayoutId != null && isLayoutContainer(selectedLayoutId)) {
            elementParent = selectedLayoutId;
            var position = layout.position(selectedLayoutId, 0, 0);
            menuX = canvasX(position.x());
            menuY = canvasY(position.y());
        } else {
            elementParent = null;
            menuX = lastClickNanos == 0 ? detailsLeft + 12 : (int) Math.round(lastClickX);
            menuY = lastClickNanos == 0 ? detailsTop() + 12 : (int) Math.round(lastClickY);
        }
        showMenu(menuX, menuY, layoutInsertEntries());
    }

    private boolean runContextShortcut(int key) {
        if (key != GLFW.GLFW_KEY_DELETE || menuTarget == null) {
            return false;
        }
        String target = menuTarget;
        menu.clear();
        menuTarget = null;
        if (layout.elements().containsKey(target)) {
            Set<String> ids = selectedLayoutIds.contains(target)
                    ? Set.copyOf(selectedLayoutIds) : Set.of(target);
            deleteElements(ids);
            return true;
        }
        if (target.startsWith("quest:")) {
            String id = target.substring("quest:".length());
            confirmDelete(selectedTaskIds.contains(id) ? Set.copyOf(selectedTaskIds) : Set.of(id), Set.of());
            return true;
        }
        if (target.startsWith("group:")) {
            String id = target.substring("group:".length());
            confirmDelete(Set.of(), selectedGroupIds.contains(id) ? Set.copyOf(selectedGroupIds) : Set.of(id));
            return true;
        }
        if (target.startsWith("system:category-")
                && !target.equals("system:category-active")
                && !target.equals("system:category-add")) {
            confirmDeleteQuestType(target.substring("system:category-".length()));
            return true;
        }
        return false;
    }

    private void openTaskInGroup(String targetGroup) {
        openTaskInGroup(targetGroup, "");
    }

    private void openTaskInGroup(String targetGroup, String parentId) {
        openTaskInGroup(targetGroup, parentId, null);
    }

    private void openTaskInGroup(String targetGroup, String parentId, QuestJournalOrderStore.Placement placement) {
        if (!canAuthor()) return;
        pendingCreationParent = parentId == null ? "" : parentId;
        minecraft.setScreen(new QuestTemplateScreen(this, creationGroup(targetGroup), placement));
    }

    private String creationGroup(String preferred) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return ClientQuestDefinitions.chapters().stream().filter(chapter -> chapter.id().equals(chapterId))
                .map(chapter -> chapter.groupId()).findFirst().orElse("");
    }

    private String chapterForGroup(String targetGroup) {
        return ClientQuestDefinitions.chapters().stream()
                .filter(chapter -> chapter.id().equals(chapterId) && chapter.groupId().equals(targetGroup))
                .map(chapter -> chapter.id()).findFirst().orElse("");
    }

    void openTaskTemplate(QuestTemplate template, String targetGroup) {
        openTaskTemplate(template, targetGroup, null);
    }

    void openTaskTemplate(QuestTemplate template, String targetGroup, QuestJournalOrderStore.Placement placement) {
        if (!canAuthor() || template == null || targetGroup == null) {
            error = "create.error.group";
            return;
        }
        ClientPreferences.get().lastTaskTemplate = template.id();
        ClientPreferences.get().save();
        editMode = true;
        form = new Form("task", template.type());
        form.placement = placement;
        form.targetGroup = targetGroup;
        form.targetChapter = chapterForGroup(targetGroup);
        form.templateId = template.id();
        form.name = template.name();
        form.description = template.description();
        form.item = template.item();
        form.count = Integer.toString(template.count());
        form.icon = template.icon();
        form.rewardItem = template.rewardItem();
        form.rewardCount = Integer.toString(template.rewardCount());
        form.rewardMode = template.rewardMode();
        form.defaultConditions = template.defaultConditions();
        form.defaultRewards = template.defaultRewards();
        form.parents = pendingCreationParent;
        pendingCreationParent = "";
        error = null;
        init();
    }


    private int menuTop() {
        return Math.max(8, Math.min(menuY, height - Math.min(height - 16, menu.size() * 20) - 8));
    }

    private void updateMenuViewport() {
        if (menu.isEmpty()) return;
        int viewportHeight = Math.min(height - 16, menu.size() * 20);
        menuMotion.limit(menu.size() * 20 - viewportHeight);
        menuScroll = Math.max(0, Math.min(menuScroll, menu.size() * 20 - viewportHeight));
        int top = menuTop();
        for (int i = 0; i < menu.size(); i++) {
            StyledButton button = menu.get(i);
            button.actionMotion("claim_all");
            button.setY(top + i * 20 - menuScroll);
            button.clipTo(button.getX(), top, button.getRight(), top + viewportHeight);
            button.visible = button.getBottom() > top && button.getY() < top + viewportHeight;
        }
    }

    private void groupSelectionMenu() {
        List<Map.Entry<String, Runnable>> entries = new ArrayList<>();
        var groups = ClientQuestDefinitions.groups();
        for (var group : groups) {
            entries.add(Map.entry(group.id(), () -> {
                if (!selectedGroupIds.remove(group.id())) selectedGroupIds.add(group.id());
                groupSelectionMenu();
            }));
        }
        entries.add(Map.entry("delete_groups", () -> confirmDelete(Set.of(), Set.copyOf(selectedGroupIds))));
        entries.add(Map.entry("clear_selection", () -> selectedGroupIds.clear()));
        showMenu(menuX, menuY, entries);
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            menu.get(i).setMessage(Component.literal(group.name()).append(" ")
                    .append(text(selectedGroupIds.contains(group.id()) ? "selection_on" : "selection_off")));
            menu.get(i).leadingIcon(group.icon());
        }
    }

    private void copyTasks(Set<String> ids) {
        try {
            minecraft.keyboardHandler.setClipboard(QuestClipboard.encode(QuestClipboard.capture(ids)));
            actionMessage = "copied";
        } catch (Exception failure) {
            error = "create.error.clipboard";
        }
    }

    private void copyId(String id) {
        minecraft.keyboardHandler.setClipboard(id);
        actionMessage = "id_copied";
    }

    private void copyControlId(String internalId) {
        var element = layout.elements().get(internalId);
        copyId(element == null ? internalId : controlId(element));
    }

    private static String controlId(LayoutEditorState.Element element) {
        if (element.attributes().containsKey("journal_source_id")) return QuestPresentationClient.sourceId(element);
        String templateKey = element.attribute("template_key", "");
        if (!templateKey.isBlank()) {
            return templateKey;
        }
        String prefix = "builtin:" + element.quest() + ":";
        if (element.id().startsWith(prefix)) {
            return element.id().substring(prefix.length());
        }
        return element.id();
    }

    private void pasteClipboard(String targetGroup, String targetChapter) {
        try {
            pasteTasks(QuestClipboard.decode(minecraft.keyboardHandler.getClipboard()), targetGroup, targetChapter);
        } catch (Exception invalid) {
            error = "create.error.clipboard";
        }
    }

    private void duplicateTasks(Set<String> ids, String clickedId) {
        try {
            pasteTasks(QuestClipboard.capture(ids),
                    groupId.isEmpty() ? ClientQuestDefinitions.groupOf(clickedId) : groupId, chapterId);
        } catch (Exception invalid) {
            error = "create.error.clipboard";
        }
    }

    private void pasteTasks(List<QuestClipboard.Task> tasks, String targetGroup, String targetChapter) {
        if (!canAuthor() || batchPending != null) return;
        if (targetGroup == null || targetGroup.isBlank()) {
            error = "create.error.group";
            return;
        }
        copiedLayout = LayoutStore.restore(LayoutStore.snapshot(layout));
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        String suffix = text("copy_suffix").getString();
        groupId = targetGroup;
        chapterId = targetChapter;
        batchPending = CompletableFuture.supplyAsync(() -> QuestBatchAuthoring.paste(
                server.getPlayerList().getPlayer(playerId), tasks, targetGroup, targetChapter, suffix), server);
    }

    private void confirmDelete(Set<String> tasks, Set<String> groups) {
        if (tasks.isEmpty() && groups.isEmpty()) return;
        showMenu(menuX, menuY, List.of(
                Map.entry("delete_confirm", () -> {
                    if (!canAuthor() || batchPending != null) return;
                    var server = minecraft.getSingleplayerServer();
                    var playerId = minecraft.player.getUUID();
                    batchPending = CompletableFuture.supplyAsync(() -> QuestBatchAuthoring.delete(
                            server.getPlayerList().getPlayer(playerId), tasks, groups), server);
                }), Map.entry("delete_warning_short", () -> {}),
                Map.entry("delete_backup_short", () -> {}), Map.entry("cancel", () -> {})));
        menu.getFirst().setMessage(Component.translatable("screen.xero_quest_journal.delete_count", tasks.size(), groups.size()));
        menu.getFirst().setTooltip(Tooltip.create(text("delete_warning")));
        menu.get(1).active = menu.get(2).active = false;
    }

    private void confirmDeleteQuestType(String id) {
        if (id == null || id.isBlank()) return;
        showMenu(menuX, menuY, List.of(
                Map.entry("delete_quest_type_confirm", () -> {
                    if (!canAuthor() || typePending != null) return;
                    var server = minecraft.getSingleplayerServer();
                    var playerId = minecraft.player.getUUID();
                    pendingTypeDelete = id;
                    typePending = CompletableFuture.supplyAsync(() -> QuestTypeStore.delete(
                            server.getPlayerList().getPlayer(playerId), id), server);
                }),
                Map.entry("delete_quest_type_warning", () -> {}),
                Map.entry("cancel", () -> {})));
        String name = categoryLabel(id).getString();
        menu.getFirst().setMessage(Component.translatable(
                "screen.xero_quest_journal.delete_quest_type_question", name));
        menu.get(1).active = false;
    }

    private void copyTaskLayouts(Map<String, String> copies) {
        if (copies.isEmpty() || copiedLayout == null) return;
        pushUndo();
        for (var copy : copies.entrySet()) {
            var size = copiedLayout.taskCanvas(copy.getKey());
            layout.setTaskCanvas(copy.getValue(), size.width(), size.height());
            Map<String, String> elements = new LinkedHashMap<>();
            copiedLayout.elements().values().stream().filter(e -> e.quest().equals(copy.getKey())).forEach(e ->
                    elements.put(e.id(), e.id().startsWith("builtin:" + copy.getKey() + ":")
                            ? "builtin:" + copy.getValue() + e.id().substring(("builtin:" + copy.getKey()).length())
                            : QuestAuthoring.newId()));
            for (var entry : elements.entrySet()) {
                var element = copiedLayout.elements().get(entry.getKey());
                var position = copiedLayout.position(entry.getKey(), 0, 0);
                layout.elements().put(entry.getValue(), new LayoutEditorState.Element(entry.getValue(), copy.getValue(),
                        element.kind(), element.value(), element.width(), element.height(), element.attributes()));
                layout.position(entry.getValue(), position.x(), position.y()).size(element.width(), element.height());
                String parentId = copiedLayout.parentOf(entry.getKey());
                if (elements.containsKey(parentId)) layout.setParent(entry.getValue(), elements.get(parentId));
            }
        }
        saveLayout();
    }

    private void confirmReset(String id) {
        confirmReset(Set.of(id));
    }

    private void confirmReset(Set<String> ids) {
        if (ids.isEmpty() || ids.size() > xtdpotato.xero_quest_journal.core.network.QuestNetwork
                .ResetBatchPayload.MAX_RESET_IDS) {
            error = "reset_selection_limit";
            return;
        }
        showMenu(menuX, menuY, List.of(
                Map.entry("reset_confirm", () -> {
                    if (actionPending == null) {
                        actionPending = ids.size() == 1 ? ClientQuestProgress.reset(ids.iterator().next())
                                : ClientQuestProgress.resetMany(ids);
                        actionMessage = null;
                        init();
                    }
                }),
                Map.entry("reset_warning_short", () -> {}),
                Map.entry("cancel", () -> {})));
        menu.get(1).active = false;
        if (ids.size() > 1) menu.getFirst().setMessage(Component.translatable(
                "screen.xero_quest_journal.reset_count", ids.size()));
        menu.getFirst().setTooltip(Tooltip.create(text("reset_warning")));
        menu.getFirst().active = actionPending == null;
    }

    private void showMenu(int x, int y, List<Map.Entry<String, Runnable>> entries) {
        menu.clear();
        menuRowGroups.clear();
        menuScroll = 0;
        menuMotion.jump(0);
        menuTarget = null;
        menuX = x;
        menuY = y;
        int w = Math.min(210, width - 16);
        int left = Math.max(8, Math.min(x, width - w - 8));
        int top = Math.max(8, Math.min(y, height - entries.size() * 20 - 8));
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            menu.add(new StyledButton(left, top + i * 20, w, 20, text(entry.getKey()), ignored -> {
                menu.clear();
                menuTarget = null;
                entry.getValue().run();
            }, styles, "context-" + entry.getKey()));
        }
        updateMenuViewport();
    }

    private void selectionContextMenu(int x, int y, String target) {
        showMenu(x, y, List.of(
                Map.entry("copy_id", () -> copyControlId(target)),
                Map.entry("properties", () -> editElement(target)),
                Map.entry("graph.settings", () -> openTriggerGraph(target)),
                Map.entry("put_into_layout", this::selectedParentMenu),
                Map.entry("layer_up", () -> changeSelectedLayer(1)),
                Map.entry("layer_down", () -> changeSelectedLayer(-1)),
                Map.entry("layer_front", () -> changeSelectedLayer(Integer.MAX_VALUE)),
                Map.entry("layer_back", () -> changeSelectedLayer(Integer.MIN_VALUE)),
                Map.entry("delete", () -> deleteElements(Set.copyOf(selectedLayoutIds)))));
        menuTarget = target;
    }

    private void deleteElements(Set<String> ids) {
        pushUndo();
        Set<String> affected = new HashSet<>(ids);
        layout.elements().keySet().stream().filter(id -> ids.stream().anyMatch(root -> layout.isDescendant(id, root)))
                .forEach(affected::add);
        for (String id : affected) {
            var element = layout.elements().get(id);
            if (element != null && (isBuiltIn(element) || isTemplateElement(element))) {
                layout.markDeleted(id);
            }
        }
        for (String id : ids) {
            layout.removeTree(id);
        }
        selectedLayoutIds.clear();
        selectedLayoutId = null;
        saveLayout();
        init();
    }

    private void selectedParentMenu() {
        var parents = layout.elements().values().stream()
                .filter(element -> element.quest().equals(selectedId) && isLayoutContainerKind(element.kind()))
                .filter(element -> !selectedLayoutIds.contains(element.id())
                        && selectedLayoutIds.stream().noneMatch(id -> layout.isDescendant(element.id(), id))).toList();
        List<Map.Entry<String, Runnable>> entries = new ArrayList<>();
        for (var parent : parents) {
            entries.add(Map.entry(parent.id(), () -> {
                pushUndo();
                for (String id : selectedLayoutIds) layout.setParent(id, parent.id());
                arrangeParent(parent.id());
                saveLayout();
                init();
            }));
        }
        if (entries.isEmpty()) entries.add(Map.entry("layout.no_contents", () -> {}));
        showMenu(menuX, menuY, entries);
        for (int i = 0; i < parents.size(); i++) menu.get(i).setMessage(Component.literal(parents.get(i).id()));
    }

    private void layoutContentsMenu(String parentId) {
        menu.clear();
        menuRowGroups.clear();
        List<LayoutEditorState.Element> choices = layout.elements().values().stream()
                .filter(element -> element.quest().equals(selectedId) && !element.id().equals(parentId))
                .filter(element -> !parentId.equals(layout.parentOf(element.id())))
                .toList();
        int w = Math.min(190, width - 16);
        int left = Math.max(8, Math.min(menuX, width - w - 8));
        int top = Math.max(8, Math.min(menuY, height - Math.max(1, choices.size()) * 20 - 8));
        if (choices.isEmpty()) {
            StyledButton empty = new StyledButton(left, top, w, 20, text("layout.no_contents"),
                    ignored -> menu.clear(), styles, "context");
            empty.active = false;
            menu.add(empty);
            return;
        }
        int shown = Math.min(choices.size(), Math.max(1, (height - 16) / 20));
        for (int i = 0; i < shown; i++) {
            var child = choices.get(i);
            Component label = Component.literal(child.id() + "  ")
                    .append(text("layout." + child.kind()));
            StyledButton button = new StyledButton(left, top + i * 20, w, 20, label, ignored -> {
                try {
                    pushUndo();
                    layout.setParent(child.id(), parentId);
                    arrangeParent(parentId);
                    selectedLayoutId = child.id();
                    saveLayout();
                    init();
                } catch (IllegalArgumentException invalid) {
                    error = "layout.error.parent";
                    menu.clear();
                }
            }, styles, "context");
            menu.add(button);
        }
    }

    private String hit(double x, double y) {
        List<Map.Entry<String, StyledButton>> buttons = new ArrayList<>(controls.entrySet());
        Collections.reverse(buttons);
        for (var entry : buttons) {
            var b = entry.getValue();
            if (b.visible && b.isMouseOver(x, y)) {
                return entry.getKey();
            }
        }
        if (!inDetails(x, y)) return null;
        for (var element : orderedElements(true)) {
            if (!editMode && !runtimeVisible(element)) continue;
            if (!insideScrollClips(element, x, y)) continue;
            int elementWidth = displayWidth(element);
            int elementHeight = displayHeight(element);
            int[] display = elementDisplayPosition(element);
            int displayX = display[0];
            int displayY = display[1];
            if (element.quest().equals(selectedId) && x >= displayX && x < displayX + elementWidth
                    && y >= displayY && y < displayY + elementHeight) {
                return element.id();
            }
        }
        return null;
    }

    private boolean insideScrollClips(LayoutEditorState.Element element, double x, double y) {
        for (String parentId = layout.parentOf(element.id()); parentId != null;
             parentId = layout.parentOf(parentId)) {
            var parent = layout.elements().get(parentId);
            if (parent == null || !parent.quest().equals(element.quest())) break;
            if (parent.kind().equals("scroll")) {
                int[] viewport = scrollViewportRect(parent);
                if (x < viewport[0] || x >= viewport[2] || y < viewport[1] || y >= viewport[3])
                    return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (historyPending != null) return true;
        clearFrameElements();
        if (inputBlocked()) return true;
        if (button == 1 && (!movingTask.isBlank() || !movingTab.isBlank())
                && form == null && iconPicker == null) {
            stopOrderMove();
            return true;
        }
        if (overviewMode && !editMode && form == null && iconPicker == null && !menu.isEmpty()) {
            return clickContextMenu(x, y, button);
        }
        if (overviewMode && !editMode && form == null && iconPicker == null) {
            if (inputBlocked() || overviewCardPending()) return true;
            return super.mouseClicked(x, y, button) || button == 1;
        }
        if (button == 0 && form == null && iconPicker == null) {
            var container = scrollTrackAt(x, y);
            if (container != null) {
                ScrollTrack track = scrollTrack(container);
                boolean horizontal = horizontalScroll(container);
                double pointer = horizontal ? x : y;
                double thumbStart = horizontal ? track.thumbX() : track.thumbY();
                double thumbLength = horizontal ? track.thumbWidth() : track.thumbHeight();
                scrollTrackGrab = pointer >= thumbStart && pointer < thumbStart + thumbLength
                        ? pointer - thumbStart : thumbLength / 2.0;
                scrollTrackDragging = container.id();
                moveScrollTrack(container, x, y);
                setFocused(null);
                return true;
            }
        }
        if (coordinateEditor != null) {
            if (coordinateEditor.isMouseOver(x, y)) return coordinateEditor.mouseClicked(x, y, button);
            if (!finishCoordinateEdit(true)) return true;
        }
        if (button == 0 && editMode && form == null && iconPicker == null && menu.isEmpty()) {
            String axis = coordinateAt(x, y);
            if (axis != null) {
                long now = System.nanoTime();
                if (axis.equals(lastCoordinateAxis) && now - lastCoordinateClick <= DOUBLE_CLICK_NANOS) {
                    beginCoordinateEdit(axis);
                    lastCoordinateAxis = "";
                } else {
                    lastCoordinateAxis = axis;
                    lastCoordinateClick = now;
                }
                return true;
            }
        }
        if (batchPending != null) return true;
        if (clickChoicePopup(x, y, button)) return true;
        if (form != null && iconPicker == null) {
            int bar = (width + formWidth()) / 2 - 8;
            if (button == 0 && formMaxScroll() > 0 && x >= bar - 2 && x < bar + 6
                    && y >= formBodyTop() && y < formBodyBottom()) {
                formScrollbarDragging = y >= formThumbTop() && y < formThumbTop() + formThumbHeight();
                if (formScrollbarDragging) scrollFormTo(y);
                else form.motion.to((y - formBodyTop() - formThumbHeight() / 2.)
                        / Math.max(1, formBodyBottom() - formBodyTop() - formThumbHeight()) * formMaxScroll());
                return true;
            }
            if (!inFormBody(x, y)) {
                for (var widget : formWidgets.keySet()) widget.visible = false;
                boolean handled = super.mouseClicked(x, y, button);
                if (form != null && iconPicker == null) updateFormViewport();
                return handled;
            }
        }
        if (iconPicker != null && button == 0 && x >= 16 && x < width - 16
                && y >= iconPicker.gridTop && y < iconPicker.gridBottom) {
            boolean overWidget = children().stream().anyMatch(child ->
                    child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                            && widget.visible && widget.isMouseOver(x, y));
            if (!overWidget) {
                iconPicker.dragging = true;
                iconPicker.dragY = y;
                return true;
            }
        }
        if (form != null || iconPicker != null) {
            return super.mouseClicked(x, y, button);
        }
        if (button == 2 && editMode && selected != null && inDetails(x, y) && menu.isEmpty()) {
            long now = System.nanoTime();
            if (now - lastMiddleClickNanos <= DOUBLE_CLICK_NANOS
                    && Math.abs(x - lastMiddleClickX) <= 4 && Math.abs(y - lastMiddleClickY) <= 4) {
                resetCanvasView();
                lastMiddleClickNanos = 0;
            } else {
                middlePanning = true;
                lastMiddleClickNanos = now;
                lastMiddleClickX = x;
                lastMiddleClickY = y;
            }
            return true;
        }
        if (button == 1 && !editMode) {
            menu.clear();
            menuTarget = null;
            return true;
        }
        if (button == 0 && maxDetailScroll() > 0
                && x >= width - 23 && x < width - 14
                && y >= detailsTop() && y < detailContentBottom()) {
            detailScrollbarDragging = true;
            detailScroll = detailTargetScroll = (y - detailsTop() - detailThumbHeight() / 2.0)
                    / Math.max(1, detailContentBottom() - detailsTop() - detailThumbHeight()) * maxDetailScroll();
            clampDetailScroll();
            return true;
        }
        if (!menu.isEmpty()) {
            return clickContextMenu(x, y, button);
        }
        if (button == 0) {
            StyledButton track = controls.get("system:track");
            if (track != null && track.visible && track.isMouseOver(x, y)) {
                return track.mouseClicked(x, y, button);
            }
        }
        if (button == 0 && selected != null && rewardFooterHeight() > 0
                && y >= detailContentBottom() && y < canvasBottom()) {
            if (editMode && JournalDetailRenderer.addButtonAt(displayedRewards(), detailsLeft,
                    detailContentBottom(), x, y)) {
                openRewardsEditor();
                return true;
            }
            var hit = JournalDetailRenderer.cardAt(font, selected, displayedRewards(), detailsLeft,
                    detailContentBottom(), width - 14, x, y);
            if (hit != null) {
                minecraft.setScreen(LootTablePopupScreen.inspect(this, hit.reward()));
                return true;
            }
        }
        if (editMode && hierarchyOpen) {
            for (var entry : hierarchyRows.entrySet()) {
                StyledButton row = entry.getValue();
                if (row.isMouseOver(x, y)) {
                    if (button == 1) {
                        if (!selectedLayoutIds.contains(entry.getKey())) {
                            selectedLayoutIds.clear();
                            selectedLayoutIds.add(entry.getKey());
                            selectedLayoutId = entry.getKey();
                        }
                        selectionContextMenu((int) x, (int) y, entry.getKey());
                        return true;
                    }
                    return super.mouseClicked(x, y, button);
                }
            }
            if (x >= 14 && x < 18 + listWidth && y >= 92 && y < height - 52) {
                return super.mouseClicked(x, y, button) || true;
            }
        }
        if (button == 0 && !editMode && selected != null && inDetails(x, y)) {
            if (y < detailContentBottom() && !hasBuiltInElements()
                    && JournalDetailRenderer.objectiveAt(font, selected, detailsLeft, detailsTop(),
                    width - 14, (int) Math.floor(detailScroll), x, y)) {
                minecraft.setScreen(new JourneyQuestPopupScreen(this, selectedId));
                return true;
            }
            for (var element : orderedElements(true)) {
                if (!element.quest().equals(selectedId) || !element.kind().equals("quest_objective")
                        || !runtimeVisible(element)) continue;
                int[] position = elementDisplayPosition(element);
                if (y < detailContentBottom() && x >= position[0] && x < position[0] + displayWidth(element)
                        && y >= position[1] && y < position[1] + displayHeight(element)) {
                    minecraft.setScreen(new JourneyQuestPopupScreen(this, selectedId));
                    return true;
                }
            }
            // Claimed layouts may retain overlapping text/control surfaces; reward cards stay inspectable.
            for (var element : orderedElements(true)) {
                if (!element.quest().equals(selectedId) || !element.kind().equals("quest_rewards")
                        || !runtimeVisible(element)) continue;
                int index = rewardIndexAt(element, x, y);
                var rewards = displayedRewards();
                if (index >= 0 && index < rewards.size()) {
                    minecraft.setScreen(LootTablePopupScreen.inspect(this, rewards.get(index)));
                    return true;
                }
            }
        }
        String target = hit(x, y);
        if (button == 0 && !editMode && target != null && target.startsWith("quest:")) {
            StyledButton row = controls.get(target);
            if (row instanceof JournalListButton questRow && questRow.claimable()
                    && questRow.claimIconContains(x, y)) {
                pressedClaimId = target.substring("quest:".length());
                questRow.claimPressed(true);
                return true;
            }
        }
        if (button == 0 && !editMode && target != null && layout.elements().containsKey(target)) {
            var element = layout.elements().get(target);
            if (element.kind().equals("item_display")) {
                String value = effectiveElement(element).value();
                minecraft.setScreen(new QuestRewardPopupScreen(this,
                        new QuestDefinition.Reward("item", value, 1)));
                return true;
            }
            if (!element.kind().equals("quest_rewards") && (xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind())
                    || element.attributes().containsKey("eventGraph"))) {
                pressedControlId = target;
                controlPointer(element, "press", x, y);
                return true;
            }
            if (element.kind().equals("structure_preview")) {
                pressedStructurePreviewId = target;
                structurePreviewPressX = x;
                structurePreviewPressY = y;
                structurePreviewMoved = false;
                return true;
            }
        }
        if (button == 0 && !editMode && target != null && layout.elements().containsKey(target)
                && layout.elements().get(target).kind().equals("quest_rewards")) {
            int rewardIndex = rewardIndexAt(layout.elements().get(target), x, y);
            List<QuestDefinition.Reward> rewards = displayedRewards();
            if (rewardIndex >= 0 && rewardIndex < rewards.size()) {
                minecraft.setScreen(LootTablePopupScreen.inspect(this, rewards.get(rewardIndex)));
                return true;
            }
        }
        if (button == 0 && !editMode && target != null && layout.elements().containsKey(target)) {
            String kind = layout.elements().get(target).kind();
            if ((kind.equals("video") || kind.equals("audio"))
                    && ClientMediaManager.toggle(mediaOwner, target)) {
                return true;
            }
        }
        long now = System.nanoTime();
        if (button == 0 && editMode && target != null && layout.elements().containsKey(target)) {
            var element = layout.elements().get(target);
            boolean editableText = element.kind().equals("text") || element.kind().equals("quest_entry_text");
            boolean doubleClick = editableText && target.equals(lastClickTarget)
                    && now - lastClickNanos <= DOUBLE_CLICK_NANOS;
            lastClickTarget = target;
            lastClickNanos = now;
            lastClickX = x;
            lastClickY = y;
            if (doubleClick) {
                selectedLayoutIds.clear();
                selectedLayoutIds.add(target);
                selectedLayoutId = target;
                editElement(target);
                return true;
            }
        } else if (button == 0) {
            lastClickTarget = target;
            lastClickNanos = now;
            lastClickX = x;
            lastClickY = y;
        }
        if (button == 0 && isFixedSystemControl(target)) {
            StyledButton fixed = controls.get(target);
            return fixed != null && fixed.mouseClicked(x, y, button);
        }
        if (button == 1 && isFixedSystemControl(target)) {
            return true;
        }
        if (button == 0 && isCategoryControl(target)) {
            if (!movingTab.isBlank() && target.startsWith("system:category-")
                    && categories().contains(target.substring("system:category-".length()))) {
                startTabMove(target.substring("system:category-".length()));
                return true;
            }
            StyledButton categoryButton = controls.get(target);
            return categoryButton != null && categoryButton.mouseClicked(x, y, button);
        }
        if (button == 1 && editMode && canAuthor() && target != null
                && target.startsWith("system:category-")
                && categories().contains(target.substring("system:category-".length()))) {
            startTabMove(target.substring("system:category-".length()));
            return true;
        }
        if (canAuthor() && button == 1 && target != null && target.startsWith("system:add-")) {
            contextMenu((int) x, (int) y, target);
            return true;
        }
        if (canAuthor() && button == 0 && groupControl != null && groupControl.isMouseOver(x, y)
                && (Screen.hasControlDown() || selectionControlDown) && !groupId.isEmpty()) {
            if (!selectedGroupIds.remove(groupId)) selectedGroupIds.add(groupId);
            actionMessage = selectedGroupIds.contains(groupId) ? "group_selected" : "group_unselected";
            return true;
        }
        if (canAuthor() && button == 2 && target != null && target.startsWith("quest:")) {
            String id = target.substring("quest:".length());
            duplicateTasks(selectedTaskIds.contains(id) ? Set.copyOf(selectedTaskIds) : Set.of(id), id);
            return true;
        }
        if (canAuthor() && editMode && button == 1
                && groupControl != null && groupControl.isMouseOver(x, y)) {
            contextMenu((int) x, (int) y, groupId.isEmpty() ? "system:add-group" : "group-properties");
            return true;
        }
        if (canAuthor() && editMode && button == 1 && target != null && target.startsWith("group:")) {
            contextMenu((int) x, (int) y, target);
            return true;
        }
        if (button == 1 && editMode && canAuthor() && target != null && target.startsWith("quest:")) {
            contextMenu((int) x, (int) y, target);
            return true;
        }
        if (button == 0 && inList(x, y)) {
            if (editMode && taskSearch != null && getFocused() == taskSearch) {
                setFocused(null);
                taskSearch.setFocused(false);
            }
            StyledButton clickedRow = target == null ? null : controls.get(target);
            if (!editMode && clickedRow instanceof JournalListButton questRow
                    && questRow.claimable() && questRow.claimIconContains(x, y)) {
                pressedClaimId = target.substring("quest:".length());
                questRow.claimPressed(true);
                return true;
            }
            listTargetScroll = listScroll;
            listReleaseVelocity = 0;
            listDragStartY = listLastDragY = y;
            listDragStartX = x;
            listDragStartScroll = listScroll;
            listLastDragNanos = System.nanoTime();
            if (maxListScroll() > 0 && x >= listRight() - 8) {
                listScrollbarDragging = true;
                if (y < thumbTop() || y >= thumbTop() + thumbHeight()) {
                    listScrollbarDragging = false;
                    listTargetScroll = (y - listTop() - thumbHeight() / 2.0)
                            / Math.max(1, listHeight() - thumbHeight()) * maxListScroll();
                    clampListScroll();
                }
            } else {
                listDragCandidate = true;
                listDragMoved = false;
                listControlClick = canAuthor() && (Screen.hasControlDown() || selectionControlDown);
                dragTarget = isListRow(target) ? target : null;
                cancelTaskReorder();
                if (canAuthor() && editMode && orderPending == null && !listControlClick
                        && target != null && target.startsWith("quest:")
                        && clickedRow != null && x >= clickedRow.getX() + 24) {
                    taskReorderCandidate = target.substring("quest:".length());
                    taskReorderPressedAt = now;
                    taskReorderX = x;
                    taskReorderY = y;
                }
            }
            return true;
        }
        if (button == 1 && editMode && groupControl != null && groupControl.isMouseOver(x, y) && !groupId.isEmpty()) {
            target = "group-properties";
        } else if (button == 1 && editMode && chapterControl != null && chapterControl.isMouseOver(x, y) && !chapterId.isEmpty()) {
            target = "chapter-properties";
        }
        if (button == 1 && canAuthor() && editMode && target != null
                && layout.elements().containsKey(target) && selectedLayoutIds.contains(target)
                && selectedLayoutIds.size() > 1) {
            selectionContextMenu((int) x, (int) y, target);
            return true;
        }
        if (button == 1 && canAuthor() && (editMode || "system:edit".equals(target))) {
            editMode = true;
            contextMenu((int) x, (int) y, target);
            return true;
        }
        if (button == 0 && editMode && target != null && !isFixedSystemControl(target) && !isHeaderControl(target)
                && (!target.startsWith("system:") || systemLayout)) {
            if (layout.elements().containsKey(target)) {
                boolean additive = Screen.hasControlDown() || Screen.hasShiftDown();
                if (!additive && !selectedLayoutIds.contains(target)) {
                    selectedLayoutIds.clear();
                }
                if (additive && selectedLayoutIds.contains(target)) {
                    selectedLayoutIds.remove(target);
                } else {
                    selectedLayoutIds.add(target);
                }
                selectedLayoutId = selectedLayoutIds.contains(target)
                        ? target : selectedLayoutIds.stream().findFirst().orElse(null);
            } else {
                selectedLayoutIds.clear();
                selectedLayoutId = null;
            }
            StyledButton control = controls.get(target);
            if (control != null) {
                layout.position(target, control.getX(), control.getY()).size(control.getWidth(), control.getHeight());
            }
            var p = layout.position(target, 0, 0);
            dragTarget = target;
            startX = p.x();
            startY = p.y();
            dragX = x;
            dragY = y;
            beforeDrag = LayoutStore.snapshot(layout);
            moving = false;
        }
        return super.mouseClicked(x, y, button) || dragTarget != null;
    }

    private int rewardIndexAt(LayoutEditorState.Element element, double mouseX, double mouseY) {
        int[] position = elementDisplayPosition(element);
        int width = displayWidth(element);
        int height = displayHeight(element);
        double localX = mouseX - position[0];
        double localY = mouseY - position[1];
        if (localX < 0 || localY < 0 || localX >= width || localY >= height) return -1;
        List<QuestDefinition.Reward> rewards = displayedRewards();
        int heading = height >= canvasPixels(46) || rewards.isEmpty() ? canvasPixels(14) : 0;
        int columnStep = canvasPixels(RewardCardRenderer.COLUMN_STEP);
        int rowStep = canvasPixels(RewardCardRenderer.ROW_STEP);
        int cardWidth = canvasPixels(RewardCardRenderer.WIDTH);
        int cardHeight = canvasPixels(RewardCardRenderer.HEIGHT);
        int columns = Math.max(1, (width + canvasPixels(4)) / columnStep);
        int column = (int) (localX / columnStep);
        int row = (int) ((localY - heading) / rowStep);
        if (localY < heading || localY - heading < 0 || column >= columns || row < 0) return -1;
        if (localX - column * columnStep >= cardWidth || (localY - heading) % rowStep >= cardHeight) return -1;
        int index = row * columns + column;
        return index < rewards.size() ? index : -1;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (historyPending != null) return true;
        if (button == 0 && !pressedClaimId.isBlank()) {
            var row = controls.get("quest:" + pressedClaimId);
            if (row instanceof JournalListButton claimRow)
                claimRow.claimPressed(claimRow.claimIconContains(x, y));
            return true;
        }
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (button == 0 && !scrollTrackDragging.isBlank()) {
            var container = layout.elements().get(scrollTrackDragging);
            if (container != null && container.kind().equals("scroll")) moveScrollTrack(container, x, y);
            return true;
        }
        if (iconPicker != null && iconPicker.dragging && button == 0) {
            iconPicker.scroll.to(iconPicker.scroll.target() - (y - iconPicker.dragY));
            iconPicker.dragY = y;
            return true;
        }
        if (button == 0 && pressedStructurePreviewId != null) {
            if (Math.abs(x - structurePreviewPressX) >= 4 || Math.abs(y - structurePreviewPressY) >= 4)
                structurePreviewMoved = true;
            return true;
        }
        if (!menu.isEmpty()) {
            if (button == 0 && menuDragging) {
                menuMoved |= Math.abs(y - menuDragY) >= 4;
                if (menuMoved) {
                    menuMotion.jump(menuDragScroll + menuDragY - y);
                    menuScroll = menuMotion.pixels();
                    updateMenuViewport();
                }
            }
            return true;
        }
        if (button == 2 && middlePanning) {
            if (Math.abs(x - lastMiddleClickX) > 4 || Math.abs(y - lastMiddleClickY) > 4)
                lastMiddleClickNanos = 0;
            canvasPanX += dx;
            canvasPanY += dy;
            clampDetailScroll();
            return true;
        }
        if (formScrollbarDragging && form != null && button == 0) {
            scrollFormTo(y);
            return true;
        }
        if (button == 0 && listScrollbarDragging) {
            listScroll = listTargetScroll = listDragStartScroll + (y - listDragStartY)
                    / Math.max(1, listHeight() - thumbHeight()) * maxListScroll();
            clampListScroll();
            updateListButtons();
            return true;
        }
        if (button == 0 && detailScrollbarDragging) {
            detailScroll = detailTargetScroll = (y - detailsTop() - detailThumbHeight() / 2.0)
                    / Math.max(1, detailContentBottom() - detailsTop() - detailThumbHeight()) * maxDetailScroll();
            clampDetailScroll();
            return true;
        }
        if (button == 0 && listDragCandidate) {
            taskReorderX = x;
            taskReorderY = y;
            advanceTaskReorder(System.nanoTime(), 0);
            if (taskReorderDragging) { updateTaskDropTarget(); return true; }
            if (Math.hypot(x - listDragStartX, y - listDragStartY) >= 4) {
                listDragMoved = true;
                cancelTaskReorder();
            }
            if (listDragMoved) {
                long now = System.nanoTime();
                double seconds = Math.max(0.008, (now - listLastDragNanos) / 1_000_000_000.0);
                listReleaseVelocity = Math.max(-1800, Math.min(1800, (listLastDragY - y) / seconds));
                double raw = listDragStartScroll + listDragStartY - y;
                double maximum = maxListScroll();
                listScroll = raw < 0 ? -Math.min(55, -raw * .32)
                        : raw > maximum ? maximum + Math.min(55, (raw - maximum) * .32) : raw;
                listTargetScroll = Math.clamp(listScroll, 0, maximum);
                listLastDragNanos = now;
                listLastDragY = y;
                updateListButtons();
            }
            return true;
        }
        if (dragTarget != null && button == 0) {
            var control = controls.get(dragTarget);
            var element = layout.elements().get(dragTarget);
            if (control == null && element == null) {
                dragTarget = null;
                beforeDrag = null;
                moving = false;
                return false;
            }
            if (Math.abs(x - dragX) + Math.abs(y - dragY) >= 3) {
                moving = true;
                int w = control == null ? elementWidth(element) : control.getWidth();
                int h = control == null ? elementHeight(element) : control.getHeight();
                if (element != null) {
                    int logicalWidth = logicalDisplayWidth(element);
                    int logicalHeight = logicalDisplayHeight(element);
                    int nextX = startX + (int) Math.round((x - dragX) / canvasZoom / canvasScaleX());
                    int nextY = startY + (int) Math.round((y - dragY) / canvasZoom / canvasScaleY());
                    boolean snap = !Screen.hasShiftDown();
                    if (snap) {
                        nextX = layout.snap(nextX);
                        nextY = layout.snap(nextY);
                        int[] snapped = snapElementPosition(dragTarget, nextX, nextY,
                                logicalWidth, logicalHeight);
                        nextX = snapped[0];
                        nextY = snapped[1];
                    }
                    layout.moveWithin(dragTarget,
                            nextX, nextY, logicalWidth, logicalHeight,
                            0, 0, taskWidth(), taskHeight(), false);
                } else {
                    layout.move(dragTarget, startX + (int) ((x - dragX) / canvasZoom),
                            startY + (int) ((y - dragY) / canvasZoom), w, h, width, height);
                }
                controls.forEach((id, widget) -> {
                    if (!isFixedSystemControl(id) && layout.positions().containsKey(id)) {
                        var p = layout.position(id, 0, 0);
                        widget.setPosition(p.x(), p.y());
                    }
                    });
        }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (!pressedClaimId.isBlank() && button == 0) {
            String id = pressedClaimId;
            pressedClaimId = "";
            var row = controls.get("quest:" + id);
            boolean claim = row instanceof JournalListButton claimRow && claimRow.claimable()
                    && claimRow.claimIconContains(x, y) && ("quest:" + id).equals(hit(x, y));
            if (row instanceof JournalListButton claimRow) claimRow.claimPressed(false);
            if (claim) runAction(id, true);
            return true;
        }
        if (button == 0 && !scrollTrackDragging.isBlank()) {
            scrollTrackDragging = "";
            return true;
        }
        if (iconPicker != null && button == 0 && iconPicker.dragging) {
            iconPicker.dragging = false;
            return true;
        }
        if (overviewMode && !editMode && form == null && iconPicker == null && !menuDragging) {
            // Route release to the held card even outside its hit box, so it cannot stick.
            boolean handled = false;
            for (var child : List.copyOf(children()))
                if (child instanceof OverviewCategoryCard card) handled |= card.mouseReleased(x, y, button);
            setDragging(false);
            if (handled) return true;
        }
        if (releaseChoicePopup(x, y, button)) return true;
        if (button == 0 && menuDragging) {
            menuDragging = false;
            var pressed = menuPressed;
            menuPressed = null;
            if (!menuMoved && Math.abs(x - menuDragX) < 4 && pressed != null && menu.contains(pressed)
                    && pressed.isMouseOver(x, y)) {
                pressed.mouseClicked(menuDragX, menuDragY, button);
                pressed.mouseReleased(x, y, button);
            }
            return true;
        }
        if (button == 0 && pressedStructurePreviewId != null) {
            String id = pressedStructurePreviewId;
            pressedStructurePreviewId = null;
            boolean open = !structurePreviewMoved && Math.abs(x - structurePreviewPressX) < 4
                    && Math.abs(y - structurePreviewPressY) < 4 && id.equals(hit(x, y));
            structurePreviewMoved = false;
            var element = layout.elements().get(id);
            if (open && element != null && element.kind().equals("structure_preview")) {
                ClientStructures.request(element.quest(), QuestPresentationClient.sourceId(element), element.value());
                minecraft.setScreen(new QuestStructurePreviewScreen(this, element.value()));
            }
            return true;
        }
        if (button == 0 && pressedControlId != null) {
            String id = pressedControlId;
            pressedControlId = null;
            var element = layout.elements().get(id);
            if (element != null && !editMode && form == null && menu.isEmpty() && iconPicker == null) {
                boolean inside = id.equals(hit(x, y));
                if (inside && xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind()))
                    controlMotions.computeIfAbsent(id, ignored -> new ButtonActionAnimation()).click();
                controlPointer(element, "release", inside ? x : -100000, inside ? y : -100000);
                if (inside && element.kind().equals("structure_enter")
                        && !element.attribute("eventGraph", "").contains("\"enter_structure\""))
                    ClientStructures.enter(element.quest(), QuestPresentationClient.sourceId(element));
            }
            return true;
        }
        if (button == 2 && middlePanning) {
            middlePanning = false;
            return true;
        }
        if (button == 0 && formScrollbarDragging) {
            formScrollbarDragging = false;
            return true;
        }
        if (button == 0 && listScrollbarDragging) {
            listScrollbarDragging = false;
            return true;
        }
        if (button == 0 && detailScrollbarDragging) {
            detailScrollbarDragging = false;
            return true;
        }
        if (button == 0 && listDragCandidate) {
            if (taskReorderDragging) {
                taskReorderX = x; taskReorderY = y;
                updateTaskDropTarget();
                if (!taskDropTarget.isBlank() && canAuthor() && editMode && orderPending == null)
                    orderPending = ClientQuestProgress.moveJournalTasks(taskReorderIds, taskDropTarget, taskDropAfter);
                listDragCandidate = listDragMoved = false;
                dragTarget = null;
                cancelTaskReorder();
                return true;
            }
            cancelTaskReorder();
            listDragCandidate = false;
            String clicked = dragTarget;
            dragTarget = null;
            if (listDragMoved) {
                if (System.nanoTime() - listLastDragNanos < 120_000_000L) {
                    listTargetScroll = Math.clamp(listScroll + listReleaseVelocity * 0.16, 0, maxListScroll());
                }
            } else if (clicked != null && inList(x, y) && clicked.equals(hit(x, y))) {
                StyledButton row = controls.get(clicked);
                if (!movingTask.isBlank() && clicked.startsWith("quest:") && orderPending == null) {
                    startTaskMove(clicked.substring("quest:".length()));
                } else if (listControlClick) {
                    boolean group = clicked.startsWith("group:");
                    String id = clicked.substring(group ? "group:".length() : "quest:".length());
                    var selection = group ? selectedGroupIds : selectedTaskIds;
                    if (!selection.remove(id)) selection.add(id);
                } else if (row != null && row.active) {
                    row.playDownSound(minecraft.getSoundManager());
                    if (!(editMode && row instanceof JournalListButton listRow && listRow.runLeadingAction(x)))
                        row.onPress();
                }
            }
            listDragMoved = false;
            return true;
        }
        if (!menu.isEmpty()) {
            for (var entry : List.copyOf(menu)) {
                entry.mouseReleased(x, y, button);
            }
            return true;
        }
        if (dragTarget != null && button == 0) {
            dragTarget = null;
            if (moving) {
                moving = false;
                if (beforeDrag != null) undo.push(beforeDrag);
                redo.clear();
                saveLayout();
                setFocused(null);
                init();
                return true;
            }
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        clearFrameElements();
        if (taskReorderDragging) return true;
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (!menu.isEmpty()) {
            var first = menu.getFirst();
            if (x >= first.getX() && x < first.getRight() && y >= menuTop()
                    && y < menuTop() + Math.min(height - 16, menu.size() * 20)) {
                menuMotion.wheel(vertical * 40);
            }
            return true;
        }
        if (form == null && iconPicker == null && (horizontal != 0 || vertical != 0)) {
            var scrollContainer = scrollContainerAt(x, y);
            if (scrollContainer != null) {
                SmoothScroll scroll = embeddedScroll(scrollContainer);
                scroll.limit(embeddedScrollMaximum(scrollContainer));
                double delta = horizontalScroll(scrollContainer)
                        ? horizontal != 0 ? horizontal : -vertical : vertical;
                if (delta != 0) scroll.wheel(delta * 32);
                return true;
            }
        }
        if (form != null || iconPicker != null) {
            if (iconPicker == null && vertical != 0 && inFormBody(x, y)) {
                form.motion.limit(formMaxScroll());
                form.motion.wheel(vertical * 24);
                return true;
            }
            if (iconPicker != null && vertical != 0 && x >= 16 && x < width - 16
                    && y >= iconPicker.gridTop && y < iconPicker.gridBottom) {
                iconPicker.scroll.wheel(vertical * 56);
                return true;
            }
            return super.mouseScrolled(x, y, horizontal, vertical);
        }
        if (inCategoryViewport(x, y) && (horizontal != 0 || vertical != 0)) {
            double delta = horizontal != 0 ? horizontal : -vertical;
            scrollCategories(delta * 28);
            return true;
        }
        if (editMode && Screen.hasControlDown() && inDetails(x, y) && vertical != 0) {
            zoomCanvasAt(canvasZoom + vertical * 0.1, x, y);
            return true;
        }
        if (menu.isEmpty() && inDetails(x, y) && (vertical != 0 || horizontal != 0)) {
            if (Screen.hasShiftDown() || horizontal != 0) {
                if (!canvasPanEasing) canvasTargetPanX = canvasPanX;
                canvasTargetPanX += (horizontal != 0 ? horizontal : vertical) * 32;
                canvasPanEasing = true;
                clampDetailScroll();
                return true;
            }
            detailTargetScroll -= vertical * 32;
            clampDetailScroll();
            return true;
        }
        if (form == null && iconPicker == null && editMode && hierarchyOpen
                && x >= 14 && x < listRight() + 2 && y >= 142 && y < height - 52 && vertical != 0) {
            hierarchyMotion.wheel(vertical * 40);
            return true;
        }
        if (menu.isEmpty() && inList(x, y) && vertical != 0) {
            listTargetScroll -= vertical * 32;
            clampListScroll();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private LayoutEditorState.Element scrollContainerAt(double x, double y) {
        List<LayoutEditorState.Element> ordered = orderedElements(true);
        for (int index = ordered.size() - 1; index >= 0; index--) {
            var element = ordered.get(index);
            if (!element.quest().equals(selectedId) || !element.kind().equals("scroll")
                    || !editMode && !runtimeVisible(element) || !insideScrollClips(element, x, y)) continue;
            int[] position = elementDisplayPosition(element);
            if (x >= position[0] && x < position[0] + displayWidth(element)
                    && y >= position[1] && y < position[1] + displayHeight(element)) return element;
        }
        return null;
    }

    private boolean inDetails(double x, double y) {
        return x >= detailsLeft && x < width - 16 && y >= detailsTop() && y < detailContentBottom();
    }

    private int[] snapElementPosition(String id, int x, int y, int width, int height) {
        if (layout.snapToGrid()) return new int[]{layout.snap(x), layout.snap(y)};
        int thresholdX = Math.max(1, (int) Math.ceil(
                GUIDE_SNAP_PIXELS / Math.max(0.01, canvasZoom * canvasScaleX())));
        int thresholdY = Math.max(1, (int) Math.ceil(
                GUIDE_SNAP_PIXELS / Math.max(0.01, canvasZoom * canvasScaleY())));
        List<Integer> guidesX = new ArrayList<>();
        List<Integer> guidesY = new ArrayList<>();
        guidesX.add(taskWidth() / 2);
        guidesY.add(taskHeight() / 2);
        Set<String> movingTree = layoutTree(id);
        for (var other : layout.elements().values()) {
            if (!other.quest().equals(selectedId) || movingTree.contains(other.id())) {
                continue;
            }
            var position = layout.position(other.id(), 0, 0);
            int otherWidth = logicalDisplayWidth(other);
            int otherHeight = logicalDisplayHeight(other);
            guidesX.add(position.x());
            guidesX.add(position.x() + otherWidth / 2);
            guidesX.add(position.x() + otherWidth);
            guidesY.add(position.y());
            guidesY.add(position.y() + otherHeight / 2);
            guidesY.add(position.y() + otherHeight);
        }
        return new int[]{
                snapAxis(x, width, guidesX, thresholdX),
                snapAxis(y, height, guidesY, thresholdY)
        };
    }

    private Set<String> layoutTree(String root) {
        Set<String> result = new HashSet<>();
        for (String id : layout.elements().keySet()) {
            if (id.equals(root) || layout.isDescendant(id, root)) {
                result.add(id);
            }
        }
        return result;
    }

    private static int snapAxis(int start, int size, List<Integer> guides, int threshold) {
        int[] anchors = {start, start + size / 2, start + size};
        int best = start;
        int bestDistance = threshold + 1;
        for (int guide : guides) {
            for (int anchor : anchors) {
                int distance = Math.abs(guide - anchor);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = start + guide - anchor;
                }
            }
        }
        return bestDistance <= threshold ? best : start;
    }

    private void scrollFormTo(double y) {
        form.scroll = (y - formBodyTop() - formThumbHeight() / 2.0)
                / Math.max(1, formBodyBottom() - formBodyTop() - formThumbHeight()) * formMaxScroll();
        updateFormViewport();
    }

    private void pushUndo() {
        undo.push(LayoutStore.snapshot(layout));
        while (undo.size() > 64) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void restoreHistory(Deque<String> from, Deque<String> to) {
        if (!from.isEmpty()) {
            to.push(LayoutStore.snapshot(layout));
            layout = LayoutStore.restore(from.pop());
            saveLayout();
            init();
        }
    }

    private QuestAuthoringHistory.State authoringHistory() {
        return QuestAuthoringHistory.state(minecraft == null || minecraft.player == null
                || minecraft.getSingleplayerServer() == null ? null
                : minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID()));
    }

    private Component historyTooltip(boolean redoAction, QuestAuthoringHistory.State state) {
        String kind = redoAction ? state.redoKind() : state.undoKind();
        return Component.translatable("screen.xero_quest_journal.history." + (redoAction ? "redo" : "undo"),
                Component.translatable("screen.xero_quest_journal.history.kind." + (kind.isEmpty() ? "none" : kind)),
                redoAction ? state.redoCount() : state.undoCount());
    }

    private void undoAuthoring(boolean redoAction) {
        if (historyPending != null || pending != null || batchPending != null || orderPending != null
                || templatePending != null || form != null) return;
        if (systemLayout || !canAuthor()) {
            restoreHistory(redoAction ? redo : undo, redoAction ? undo : redo);
            return;
        }
        if (presentationWriter.busy()) {
            afterPublication = () -> undoAuthoring(redoAction);
            return;
        }
        var server = minecraft.getSingleplayerServer();
        UUID player = minecraft.player.getUUID();
        historyRedo = redoAction;
        historyPending = CompletableFuture.supplyAsync(() -> redoAction
                ? QuestAuthoringHistory.redo(server.getPlayerList().getPlayer(player))
                : QuestAuthoringHistory.undo(server.getPlayerList().getPlayer(player)), server);
        init();
    }

    private void saveLayout() {
        saveLayout(true);
    }

    private void saveLayout(boolean publish) {
        clearFrameElements();
        if (!LayoutStore.save("journal", layout)) {
            error = "create.error.save";
        }
        if (!publish) return;
        // Task edits publish an independent presentation; templates have a separate editor.
        if (editMode && canAuthor() && selected != null && pending == null) {
            try { presentationWriter.queue(selected.id(), layout); }
            catch (IllegalArgumentException invalid) { error = "graph.invalid"; }
        }
    }

    void variablesChanged(String taskId, String oldId, String newId) {
        presentationWriter.variablesChanged(taskId, oldId, newId, layout);
    }

    private void openTriggerGraph(String id) {
        var element = layout.elements().get(id);
        if (element == null || !canAuthor()) return;
        var graph = new com.google.gson.Gson().fromJson(element.attribute("eventGraph", "{\"nodes\":[],\"edges\":[]}"),
                xtdpotato.xero_quest_journal.core.model.TriggerGraph.class);
        if (graph.nodes().isEmpty() && xtdpotato.xero_quest_journal.core.model.ControlProperties.button(element.kind()))
            graph = xtdpotato.xero_quest_journal.core.model.TriggerGraph.buttonEvents();
        var controls = new LinkedHashMap<String, xtdpotato.xero_quest_journal.core.model.QuestPresentation.Control>();
        for (var control : QuestPresentationClient.snapshot(element.quest(), layout).controls())
            controls.put(control.id(), control);
        minecraft.setScreen(new QuestTriggerGraphScreen(this, graph, element.quest(),
                QuestPresentationClient.sourceId(element), controls, value -> {
            pushUndo();
            var attrs = new LinkedHashMap<>(element.attributes());
            attrs.put("eventGraph", new com.google.gson.Gson().toJson(value));
            layout.elements().put(id, new LayoutEditorState.Element(id, element.quest(), element.kind(),
                    element.value(), element.width(), element.height(), attrs));
            saveLayout();
        }));
    }
    private void openFormTriggerGraph() {
        Form editing = form;
        if (editing == null || !canAuthor()) return;
        if (editing.eventGraph == null) {
            var element = layout.elements().get(editing.elementId);
            editing.eventGraph = element == null ? xtdpotato.xero_quest_journal.core.model.TriggerGraph.empty()
                    : new com.google.gson.Gson().fromJson(element.attribute("eventGraph", "{\"nodes\":[],\"edges\":[]}"),
                    xtdpotato.xero_quest_journal.core.model.TriggerGraph.class);
            if (editing.eventGraph.nodes().isEmpty())
                editing.eventGraph = xtdpotato.xero_quest_journal.core.model.TriggerGraph.buttonEvents();
        }
        var controls = new LinkedHashMap<String, xtdpotato.xero_quest_journal.core.model.QuestPresentation.Control>();
        for (var control : QuestPresentationClient.snapshot(selectedId, layout).controls())
            controls.put(control.id(), control);
        minecraft.setScreen(new QuestTriggerGraphScreen(this, editing.eventGraph, selectedId, editing.id, controls,
                value -> editing.eventGraph = value));
    }
    private void controlPointer(LayoutEditorState.Element element, String event, double x, double y) {
        int[] origin = elementDisplayPosition(element);
        double scaleX = Math.max(.001, displayWidth(element) / (double) Math.max(1, logicalDisplayWidth(element)));
        double scaleY = Math.max(.001, displayHeight(element) / (double) Math.max(1, logicalDisplayHeight(element)));
        ClientControlState.pointer(element.quest(), QuestPresentationClient.sourceId(element), event,
                (x - origin[0]) / scaleX, (y - origin[1]) / scaleY,
                Math.max(1, logicalDisplayWidth(element)), Math.max(1, logicalDisplayHeight(element)));
    }
    private void openTriggerOverview(String quest) {
        if (!canAuthor()) return;
        var entries = layout.elements().values().stream()
                .filter(element -> element.quest().equals(quest))
                .sorted(java.util.Comparator.comparing(QuestPresentationClient::sourceId))
                .map(element -> {
                    xtdpotato.xero_quest_journal.core.model.TriggerGraph graph;
                    try {
                        graph = new com.google.gson.Gson().fromJson(
                                element.attribute("eventGraph", "{\"nodes\":[],\"edges\":[]}"),
                                xtdpotato.xero_quest_journal.core.model.TriggerGraph.class);
                    } catch (RuntimeException invalid) {
                        graph = xtdpotato.xero_quest_journal.core.model.TriggerGraph.empty();
                    }
                    return new QuestTriggerOverviewScreen.Entry(element.id(),
                            QuestPresentationClient.sourceId(element), graph);
                }).toList();
        minecraft.setScreen(new QuestTriggerOverviewScreen(this, entries, this::openTriggerGraph));
    }

    private LayoutEditorState.Element effectiveElement(LayoutEditorState.Element element) {
        if (editMode) return element;
        var values = ClientControlState.properties(element.quest(), QuestPresentationClient.sourceId(element));
        if (values.isEmpty()) return element;
        var attrs = new LinkedHashMap<>(element.attributes());
        attrs.putAll(values);
        if (values.containsKey("width")) attrs.put("layout_width", values.get("width"));
        if (values.containsKey("height")) attrs.put("layout_height", values.get("height"));
        return new LayoutEditorState.Element(element.id(), element.quest(), element.kind(),
                values.getOrDefault("value", element.value()),
                parseInteger(values.getOrDefault("width", Integer.toString(element.width())), element.width()),
                parseInteger(values.getOrDefault("height", Integer.toString(element.height())), element.height()), attrs);
    }

    private void publishTemplateChanges() {
        if (!editMode || !canAuthor() || appliedTemplate == null || templateBaseline == null
                || templatePending != null || selected == null) return;
        try {
            List<QuestTemplate.Element> next = new ArrayList<>();
            Map<String, QuestTemplate.Element> sources = new LinkedHashMap<>();
            appliedTemplate.elements().forEach(e -> sources.put(e.id(), e));
            for (var current : layout.elements().values()) {
                if (!current.quest().equals(selectedId)
                        || !current.attribute("template_owner", "").equals(appliedTemplate.id())) continue;
                var source = sources.get(current.attribute("template_key", ""));
                var baseline = templateBaseline.elements().get(current.id());
                if (source == null || baseline == null) continue;
                var attrs = new LinkedHashMap<>(current.attributes());
                attrs.keySet().removeIf(key -> key.startsWith("template_") || key.startsWith("journal_"));
                if (source.attributes().containsKey("eventGraph"))
                    attrs.put("eventGraph", source.attributes().get("eventGraph"));
                var before = templateBaseline.position(current.id(), 0, 0);
                var after = layout.position(current.id(), 0, 0);
                var parentElement = layout.elements().get(layout.parentOf(current.id()));
                String parent = parentElement == null ? "" : parentElement.attribute("template_key", "");
                next.add(new QuestTemplate.Element(source.id(), current.kind(), source.value(),
                        source.x() + after.x() - before.x(), source.y() + after.y() - before.y(),
                        current.width(), current.height(), attrs, parent));
            }
            QuestTemplate updated = appliedTemplate.withElements(next);
            if (updated.equals(appliedTemplate)) return;
            var server = minecraft.getSingleplayerServer();
            UUID playerId = minecraft.player.getUUID();
            String hash = templateHash;
            templatePending = CompletableFuture.supplyAsync(() ->
                    QuestTemplateStore.save(server.getPlayerList().getPlayer(playerId), updated, hash), server);
        } catch (RuntimeException invalid) {
            error = "template.error.invalid";
        }
    }

    private void runAction(String id, boolean claim) {
        if (id == null || id.isBlank() || actionPending != null) return;
        actionMessage = null;
        pendingClaim = null;
        if (claim) {
            var view = ClientQuestProgress.view(id);
            if (view == null || !view.status().equals("complete")) {
                actionMessage = view != null && view.status().equals("claimed") ? "already_claimed" : "incomplete";
                init();
                return;
            }
            QuestDefinition quest = ClientQuestDefinitions.find(id);
            actionPending = ClientQuestProgress.action(id, true);
            pendingClaim = new PendingClaim(actionPending, quest == null ? "" : QuestText.title(quest).getString(),
                    id, Map.of(id, view.rewards()));
        } else {
            actionPending = ClientQuestProgress.action(id, false);
        }
        init();
    }

    private void runClaimAll() {
        if (actionPending != null) return;
        Map<String, List<QuestDefinition.Reward>> rewards = new LinkedHashMap<>();
        boolean hasChoiceRewards = false;
        for (var quest : ClientQuestDefinitions.all()) {
            var view = ClientQuestProgress.view(quest.id());
            if (view != null && view.status().equals("complete")) {
                rewards.put(quest.id(), view.rewards());
                hasChoiceRewards |= view.rewards().stream()
                        .anyMatch(reward -> reward.type().equals("loot_choice"));
            }
        }
        if (hasChoiceRewards) {
            minecraft.setScreen(new QuestConfirmScreen(this,
                    text("rewards.claim_all_choice_confirm"), text("rewards.claim_all_confirm"),
                    () -> runClaimAllConfirmed(rewards)));
            return;
        }
        runClaimAllConfirmed(rewards);
    }

    private void runClaimAllConfirmed(Map<String, List<QuestDefinition.Reward>> rewards) {
        if (actionPending != null) return;
        actionMessage = null;
        actionPending = ClientQuestProgress.claimAll();
        pendingClaim = new PendingClaim(actionPending, text("rewards.claim_all_title").getString(), null, rewards);
        init();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (historyPending != null) return true;
        if (inputBlocked()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && taskReorderDragging) {
            listDragCandidate = listDragMoved = false;
            dragTarget = null;
            cancelTaskReorder();
            return true;
        }
        if (overviewMode && (inputBlocked() || overviewCardPending())) return true;
        if (coordinateEditor != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { finishCoordinateEdit(false); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                finishCoordinateEdit(true);
                return true;
            }
            return coordinateEditor.keyPressed(key, scan, modifiers);
        }
        if (key == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && taskSearch != null && getFocused() == taskSearch && taskSearch.isFocused()) {
            taskSearch.insertText(minecraft.keyboardHandler.getClipboard());
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) selectionControlDown = true;
        if (iconPicker != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (externalPicker) {
                    onClose();
                    return true;
                }
                iconPicker = null;
                error = null;
                init();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        if (getFocused() instanceof EditBox editBox && editBox.isFocused()
                && key != GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(key, scan, modifiers);
        }
        if (form == null && menu.isEmpty() && (!movingTask.isBlank() || !movingTab.isBlank())) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                stopOrderMove();
                return true;
            }
            boolean tab = !movingTab.isBlank();
            if (key == (tab ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_UP)
                    || key == (tab ? GLFW.GLFW_KEY_RIGHT : GLFW.GLFW_KEY_DOWN)) {
                moveOrder(key == (tab ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_UP) ? -1 : 1);
                return true;
            }
        }
        if (!overviewMode && editMode && menu.isEmpty()
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_C) {
                Set<String> ids = selectedTaskIds.isEmpty()
                        ? selected == null ? Set.of() : Set.of(selected.id())
                        : Set.copyOf(selectedTaskIds);
                if (!ids.isEmpty()) copyTasks(ids);
                return !ids.isEmpty();
            }
            if (key == GLFW.GLFW_KEY_V && canAuthor()) {
                String targetGroup = !groupId.isBlank() ? groupId
                        : selected == null ? "" : ClientQuestDefinitions.groupOf(selected.id());
                if (targetGroup.isBlank()) targetGroup = creationGroup("");
                pasteClipboard(targetGroup, chapterId);
                return true;
            }
        }
        if (form != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeForm();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !menu.isEmpty()) {
            menu.clear();
            menuTarget = null;
            return true;
        }
        if (editMode && !menu.isEmpty() && runContextShortcut(key)) {
            return true;
        }
        if (editMode && menu.isEmpty() && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) == 0
                && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD
                || key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT)) {
            double step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 0.001 : 0.01;
            setCanvasZoom(canvasZoom + (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT ? -step : step));
            return true;
        }
        if (QuestKeyMappings.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        if (!overviewMode && menu.isEmpty()
                && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT)) == 0) {
            if (key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_S) {
                navigateTaskSelection(key == GLFW.GLFW_KEY_W ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_E) {
                cycleCategory(key == GLFW.GLFW_KEY_Q ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_F) {
                StyledButton track = controls.get("system:track");
                if (track != null && track.active) track.onPress();
                return true;
            }
        }
        if (editMode && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_Z) {
            undoAuthoring((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (editMode && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_Y) {
            undoAuthoring(true);
            return true;
        }
        if (editMode && menu.isEmpty() && key == GLFW.GLFW_KEY_DELETE && !selectedLayoutIds.isEmpty()) {
            deleteElements(Set.copyOf(selectedLayoutIds));
            return true;
        }
        if (editMode && menu.isEmpty() && key == GLFW.GLFW_KEY_INSERT) {
            openLayoutInsertMenu();
            return true;
        }
        if (editMode && selectedLayoutId != null && layout.elements().containsKey(selectedLayoutId)
                && List.of(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN).contains(key)) {
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 8 : 1;
            int dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
            pushUndo();
            layout.moveTree(selectedLayoutId, dx, dy, taskWidth(), taskHeight());
            arrangeParent(layout.parentOf(selectedLayoutId));
            saveLayout();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return coordinateEditor != null ? coordinateEditor.charTyped(character, modifiers)
                : super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) selectionControlDown = false;
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (pending == null && batchPending == null && historyPending == null) {
            rememberSelectedQuest(selectedId);
            if (overviewMode && parent instanceof QuestScreen journal) journal.layout = LayoutStore.load("journal");
            closeTo(parent);
        }
    }

    @Override
    public void removed() {
        detailCompositor.close();
        journalSearch.suspend();
        cancelTaskReorder();
        listDragCandidate = listDragMoved = false;
        if (pressedControlId != null) {
            var element = layout.elements().get(pressedControlId);
            pressedControlId = null;
            if (element != null && !editMode) ClientControlState.pointer(element.quest(),
                    QuestPresentationClient.sourceId(element), "release", -1, -1,
                    Math.max(1, logicalDisplayWidth(element)), Math.max(1, logicalDisplayHeight(element)));
        }
        pressedStructurePreviewId = null;
        structurePreviewMoved = false;
        ClientMediaManager.release(mediaOwner);
        CategoryIconRenderer.close();
        super.removed();
    }

    @Override protected int animationHeaderBottom() { return detailsTop(); }
    @Override protected int animationFooterTop() { return height - 48; }
    @Override protected Object popupKey() { return iconPicker != null ? iconPicker : form; }
    @Override protected Viewport popupBounds() {
        if (iconPicker != null) {
            return new Viewport(0, 0, width, height);
        }
        if (form != null) {
            int w = formWidth();
            int x = (width - w) / 2;
            int panelHeight = formPanelHeight();
            int y = Math.max(2, (height - panelHeight) / 2);
            return new Viewport(x - 2, y - 2, x + w + 2, y + panelHeight + 2);
        }
        return super.popupBounds();
    }

    private static final class Form {
        String prerequisiteMode = "all";
        final String kind;
        QuestJournalOrderStore.Placement placement;
        String type;
        String id = QuestAuthoring.newId();
        String name = "";
        String description = "";
        String objective = "";
        String originalObjective;
        String item = "minecraft:oak_log";
        String count = "16";
        String icon = "";
        String expectedHash = "";
        String originalDescription = "";
        String parents = "";
        String rewardItem = "";
        boolean complexConditions;
        boolean conditionsEdited;
        boolean rewardsEdited;
        boolean complexRewards;
        boolean completionEvents;
        boolean completionReenter;
        boolean completionExport;
        boolean structureProgress;
        boolean automaticRewardPopup = true;
        boolean defaultUnlocked = true;
        boolean hideWhenLocked;
        boolean hidden;
        boolean triggersEnabled = true;
        boolean focusAvailable;
        String listAccent = "auto";
        boolean recommended;
        boolean limitedReward;
        String rewardCount = "1";
        String rewardMode = "default";
        String originalRules = "";
        String elementId;
        String x = "0";
        String y = "0";
        String layoutWidth = "140";
        String layoutHeight = "24";
        String propertyPage = "layout";
        String visibility = "visible";
        String alpha = "1";
        String layoutGravity = "inherit";
        String marginLeft = "0";
        String marginTop = "0";
        String marginRight = "0";
        String marginBottom = "0";
        String gravity = "top|left";
        String textColor = "#ffffff";
        String textStyle = "normal";
        String textSize = "1.0";
        String maxLines = "0";
        String background = "transparent";
        String paddingLeft = "0";
        String paddingTop = "0";
        String paddingRight = "0";
        String paddingBottom = "0";
        String borderColor = "transparent";
        String borderWidth = "0";
        String appearanceState = "normal";
        final Map<String, String> appearance = new LinkedHashMap<>();
        final Map<String, String> buttonContent = new LinkedHashMap<>(Map.of(
                "buttonImage", "", "buttonImageHover", "inherit", "buttonImagePressed", "inherit",
                "buttonImageWidth", "16", "buttonImageHeight", "16", "buttonImageGap", "4",
                "buttonImagePosition", "left", "textColorHover", "inherit", "textColorPressed", "inherit"));
        String shadowColor = "#000000";
        String shadowRadius = "0";
        String shadowDx = "0";
        String shadowDy = "0";
        String orientation = "vertical";
        String spacing = "4";
        String imageScaleType = "fit_xy";
        String autoplay = "true";
        String loop = "false";
        String muted = "false";
        String volume = "1.0";
        String showControls = "true";
        String mediaDisplayText = "";
        xtdpotato.xero_quest_journal.core.model.TriggerGraph eventGraph;
        String entryIcon = "minecraft:paper";
        String entryProgress = "0/1";
        boolean hotEventEnabled;
        boolean hotEventHideImage;
        String hotEventImage = "";
        String hotEventTitle = "";
        String legendaryImage = "";
        String legendarySubtitle = "";
        String legendaryFilters = "";
        String hotEventImageWidth = "0";
        String hotEventImageHeight = "0";
        int hotEventX;
        int hotEventY;
        String hotEventXText = "0";
        String hotEventYText = "0";
        boolean hotEventPositioned;
        String templateId = "";
        List<QuestDefinition.Trigger> defaultConditions = List.of();
        List<QuestDefinition.Reward> defaultRewards = List.of();
        String targetGroup = "";
        String targetChapter = "";
        List<JourneyBackdrop> journeyBackgrounds = new ArrayList<>();
        boolean journeyVisible = true;
        xtdpotato.xero_quest_journal.core.model.JourneyAppearance journeyAppearance =
                xtdpotato.xero_quest_journal.core.model.JourneyAppearance.empty();
        final IconForm leadingIcon = new IconForm(ListIconConfig.leading(""));
        final IconForm trailingIcon = new IconForm(ListIconConfig.trailing());
        double scroll;
        final SmoothScroll motion = new SmoothScroll();
        boolean editing;

        Form(String kind, String type) {
            this.kind = kind;
            this.type = kind.equals("task") ? QuestDefinition.normalizeType(type) : type;
            if (kind.equals("task") && type.equals("guide")) {
                defaultConditions = List.of(new QuestDefinition.Trigger("confirm", "", 1));
            }
            count = Integer.toString(QuestConfig.defaults().defaultItemCount);
        }

        String ruleSignature() {
            return String.join("\u0000", type, parents, rewardItem, rewardCount, rewardMode,
                    Boolean.toString(completionEvents), Boolean.toString(completionReenter),
                    Boolean.toString(completionExport), Boolean.toString(structureProgress),
                    Boolean.toString(automaticRewardPopup));
        }

        boolean authoring() {
            return List.of("group", "chapter", "task").contains(kind);
        }

        boolean hasListIconPages() {
            return editing && (kind.equals("group") || kind.equals("task"));
        }

        void loadHotEvent(HotEventData value) {
            hotEventEnabled = value.enabled();
            hotEventHideImage = value.hideImage();
            hotEventImage = value.image();
            hotEventTitle = value.title();
            hotEventImageWidth = Integer.toString(value.imageWidth());
            hotEventImageHeight = Integer.toString(value.imageHeight());
            hotEventX = value.x();
            hotEventY = value.y();
            hotEventXText = Integer.toString(value.x());
            hotEventYText = Integer.toString(value.y());
            hotEventPositioned = value.positioned();
        }
    }

    private static final class IconForm {
        String image;
        String trackedImage;
        String trackingStyle;
        boolean visible;
        boolean replaceWhenTracked;
        String alpha;
        String width;
        String height;
        String offsetX;
        String offsetY;

        IconForm(ListIconConfig value) {
            load(value);
        }

        void load(ListIconConfig value) {
            image = value.image();
            trackedImage = value.trackedImage();
            trackingStyle = value.trackingStyle();
            visible = value.visible();
            replaceWhenTracked = value.replaceWhenTracked();
            alpha = Float.toString(value.alpha());
            width = Integer.toString(value.width());
            height = Integer.toString(value.height());
            offsetX = Integer.toString(value.offsetX());
            offsetY = Integer.toString(value.offsetY());
        }

        ListIconConfig value() {
            try {
                return new ListIconConfig(image, trackedImage, visible, replaceWhenTracked,
                        Float.parseFloat(alpha.strip()), Integer.parseInt(width.strip()),
                        Integer.parseInt(height.strip()), Integer.parseInt(offsetX.strip()),
                        Integer.parseInt(offsetY.strip()), trackingStyle);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid list icon number", invalid);
            }
        }
    }

    private enum IconSource {
        INVENTORY("inventory"),
        ITEMS("items"),
        TAGS("tags"),
        TEXTURES("textures"),
        LOCAL("local");

        private final String key;

        IconSource(String key) {
            this.key = key;
        }
    }

    private static final class IconPicker {
        IconSource source = IconSource.INVENTORY;
        String search = "";
        String manual;
        String sort = "id";
        boolean descending;
        int columns = 1;
        int visibleRows = 1;
        int gridTop;
        int gridBottom;
        List<QuestIcons.IconChoice> filteredChoices = List.of();
        final List<StyledButton> sourceButtons = new ArrayList<>();
        final EnumMap<IconSource, List<QuestIcons.IconChoice>> cachedChoices = new EnumMap<>(IconSource.class);
        final SmoothScroll scroll = new SmoothScroll();
        final Map<StyledButton, String> boundValues = new java.util.IdentityHashMap<>();
        List<QuestIcons.IconChoice> sortedInput, sortedChoices;
        String sortedKey = "";
        CompletableFuture<Void> refreshPending;
        CompletableFuture<List<QuestIcons.IconChoice>> loadingChoices;
        IconSource loadingSource;
        final java.util.function.Consumer<String> changed;
        final boolean itemOnly;
        boolean allowTags;
        boolean dragging;
        double dragY;

        IconPicker(String current, java.util.function.Consumer<String> changed, boolean itemOnly) {
            manual = current == null ? "" : current;
            this.changed = changed;
            this.itemOnly = itemOnly;
            source = !itemOnly && manual.endsWith(".png") ? IconSource.TEXTURES : IconSource.ITEMS;
        }

        int firstRow() {
            return Math.max(0, (int) Math.floor(scroll.position() / 40) - ICON_PICKER_OVERSCAN_ROWS);
        }
    }

    private record FormLabel(Component text, int x, int y, int width) {
    }

    private record ColorPreview(int x, int y, int size, java.util.function.Supplier<String> value) {
    }
}
