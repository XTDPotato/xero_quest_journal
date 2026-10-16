package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;
import xtdpotato.xero_quest_journal.core.model.HotEventData;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full-width, presentation-only view of quests and groups marked as hot events. */
public final class HotEventsScreen extends AnimatedQuestScreen {
    private static final int CARD_WIDTH = 184;
    private static final int CARD_HEIGHT = 110;
    private static final int LABEL_HEIGHT = 16;
    private static final int CARD_GAP = 44;
    private static final int ZOOM_MIN = 45;
    private static final int ZOOM_MAX = 160;
    private final Screen parent;
    private final String mediaOwner = "hot-events-" + java.util.UUID.randomUUID();
    private final Map<String, HotEventCanvas.Position> positions = new LinkedHashMap<>();
    private HotEventCanvas canvas;
    private List<Card> cards = List.of();
    private Map<String, Card> cardLookup = Map.of();
    private ViewportIndex<Card> cardIndex = new ViewportIndex<>(List.of());
    private ViewportIndex<HotEventCanvas.Connection> connectionIndex = new ViewportIndex<>(List.of());
    private List<Card> visibleCards = List.of();
    private int contentLeft, contentRight;
    private long catalogRevision = -1;
    private long progressRevision = -1;
    private long listRevision = -1;
    private double zoom = 1.0;
    private double scrollX;
    private double targetScrollX;
    private double canvasCenterY;
    private String contextId;
    private HotEventCanvas.Connection contextConnection;
    private int contextX;
    private int contextY;
    private String wireFrom = "";
    private double wireX;
    private double wireY;
    private String selectedId = "";
    private String message = "";
    private boolean editing;
    private boolean draggingCanvas;
    private boolean draggingCard;
    private boolean draggingZoom;
    private double dragOffsetX;
    private double dragOffsetY;
    private double dragStartX;
    private double dragStartScroll;
    private double dragStartY;
    private String lastClickId = "";
    private long scrollFrame;
    private boolean draggingScrollRail;
    private double railGrab;

    private record Card(String id, boolean group, String title, String subtitle,
                        String image, int imageWidth, int imageHeight,
                        int x, int y, boolean completed, String countText, boolean hideImage) {}
    private record ScrollBounds(double minX, double maxX) {}

    public HotEventsScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.hot_events.title"));
        this.parent = parent;
        canvas = ClientQuestDefinitions.hotEventCanvas();
        positions.putAll(canvas.positions());
    }

    public int visibleCardCount() { return cards.size(); }

    private boolean isAdmin() {
        return ClientEditMode.enabled() && minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    @Override
    protected void init() {
        clearWidgets();
        editing &= isAdmin();
        reloadCards();
        clampScroll();
        catalogRevision = ClientQuestDefinitions.revision();
        progressRevision = ClientQuestProgress.revision();
        listRevision = ClientQuestProgress.listRevision();
    }

    private void reloadCards() {
        List<Card> next = new ArrayList<>();
        var membersByGroup = new java.util.HashMap<String, List<xtdpotato.xero_quest_journal.core.model.QuestDefinition>>();
        for (var quest : ClientQuestDefinitions.all()) {
            var progress = ClientQuestProgress.view(quest.id());
            if (editing || (progress == null || !progress.hidden())
                    && (!quest.automaticallyGenerated() || progress == null || progress.unlocked()))
                membersByGroup.computeIfAbsent(ClientQuestDefinitions.groupOf(quest.id()),
                        ignored -> new ArrayList<>()).add(quest);
        }
        var featuredGroups = ClientQuestDefinitions.groups().stream()
                .filter(group -> group.hotEvent().enabled()).map(QuestCatalog.Group::id)
                .collect(java.util.stream.Collectors.toSet());
        for (QuestCatalog.Group group : ClientQuestDefinitions.groups().stream()
                .sorted(Comparator.comparing(QuestCatalog.Group::id)).toList()) {
            HotEventData data = group.hotEvent();
            if (!data.enabled()) continue;
            String id = "group:" + group.id();
            HotEventCanvas.Position position = position(id, data, next.size());
            List<xtdpotato.xero_quest_journal.core.model.QuestDefinition> members =
                    membersByGroup.getOrDefault(group.id(), List.of());
            if (members.isEmpty() && !editing) continue;
            boolean complete = !members.isEmpty() && members.stream().allMatch(quest -> {
                var progress = ClientQuestProgress.view(quest.id());
                return progress != null && progress.completed();
            });
            next.add(new Card(id, true, data.title().isBlank() ? group.name() : data.title(),
                    group.description(), data.image().isBlank() ? group.icon() : data.image(),
                    data.imageWidth(), data.imageHeight(), position.x(), position.y(),
                    complete, Integer.toString(members.size()), data.hideImage()));
        }
        for (var quest : ClientQuestDefinitions.all().stream()
                .sorted(Comparator.comparing(xtdpotato.xero_quest_journal.core.model.QuestDefinition::id)).toList()) {
            HotEventData data = quest.hotEvent();
            if (!data.enabled() || featuredGroups.contains(ClientQuestDefinitions.groupOf(quest.id()))) continue;
            var progress = ClientQuestProgress.view(quest.id());
            if (!editing && (progress != null && progress.hidden()
                    || quest.automaticallyGenerated() && progress != null && !progress.unlocked())) continue;
            String id = "quest:" + quest.id();
            HotEventCanvas.Position position = position(id, data, next.size());
            String count = progress == null || progress.required() <= 0 ? ""
                    : progress.current() + "/" + progress.required();
            next.add(new Card(id, false, data.title().isBlank() ? QuestText.title(quest).getString() : data.title(),
                    QuestText.type(quest).getString(), data.image().isBlank() ? quest.icon() : data.image(),
                    data.imageWidth(), data.imageHeight(), position.x(), position.y(),
                    progress != null && progress.completed(), count, data.hideImage()));
        }
        next.sort(Comparator.comparing(Card::id));
        cards = List.copyOf(next);
        rebuildViewportIndexes();
        // Keep a parallel quest line at screen centre without changing authored coordinates.
        // Freeze this reference during card dragging so inverse coordinates remain stable.
        if (!draggingCard) {
            int top = cards.stream().mapToInt(Card::y).min().orElse(0);
            int bottom = cards.stream().mapToInt(card -> card.y() + CARD_HEIGHT).max().orElse(0);
            canvasCenterY = (top + bottom) / 2.0;
        }
    }

    private void rebuildViewportIndexes() {
        var lookup = new LinkedHashMap<String, Card>();
        var entries = new ArrayList<ViewportIndex.Entry<Card>>();
        contentLeft = contentRight = 0;
        for (int i = 0; i < cards.size(); i++) {
            var card = cards.get(i);
            lookup.put(card.id(), card);
            if (i == 0) { contentLeft = card.x(); contentRight = card.x() + CARD_WIDTH; }
            contentLeft = Math.min(contentLeft, card.x());
            contentRight = Math.max(contentRight, card.x() + CARD_WIDTH);
            double y = card.y() + (CARD_HEIGHT - cardHeight(card)) / 2.0;
            entries.add(new ViewportIndex.Entry<>(card, card.x() - 12, y - 12,
                    card.x() + CARD_WIDTH + 12, y + cardHeight(card) + 12, i));
        }
        cardLookup = Map.copyOf(lookup);
        cardIndex = new ViewportIndex<>(entries);
        var edges = new ArrayList<ViewportIndex.Entry<HotEventCanvas.Connection>>();
        for (int i = 0; i < canvas.connections().size(); i++) {
            var edge = canvas.connections().get(i);
            var from = findCard(edge.from()); var to = findCard(edge.to());
            if (from == null || to == null) continue;
            double x1 = from.x() + CARD_WIDTH, y1 = from.y() + CARD_HEIGHT / 2.0;
            double x2 = to.x(), y2 = to.y() + CARD_HEIGHT / 2.0;
            edges.add(new ViewportIndex.Entry<>(edge, Math.min(x1, x2) - 3, Math.min(y1, y2) - 3,
                    Math.max(x1, x2) + 3, Math.max(y1, y2) + 3, i));
        }
        connectionIndex = new ViewportIndex<>(edges);
    }

    private void refreshCardProgress() {
        var questsByGroup = new java.util.HashMap<String, List<xtdpotato.xero_quest_journal.core.model.QuestDefinition>>();
        for (var quest : ClientQuestDefinitions.all()) {
            var progress = ClientQuestProgress.view(quest.id());
            if (editing || (progress == null || !progress.hidden())
                    && (!quest.automaticallyGenerated() || progress == null || progress.unlocked()))
                questsByGroup.computeIfAbsent(ClientQuestDefinitions.groupOf(quest.id()),
                        ignored -> new ArrayList<>()).add(quest);
        }
        var refreshed = new ArrayList<Card>(cards.size());
        for (var card : cards) {
            if (card.group()) {
                var members = questsByGroup.getOrDefault(card.id().substring("group:".length()), List.of());
                boolean complete = !members.isEmpty() && members.stream().allMatch(quest -> {
                    var progress = ClientQuestProgress.view(quest.id());
                    return progress != null && progress.completed();
                });
                refreshed.add(new Card(card.id(), true, card.title(), card.subtitle(), card.image(),
                        card.imageWidth(), card.imageHeight(), card.x(), card.y(), complete,
                        Integer.toString(members.size()), card.hideImage()));
            } else {
                String id = card.id().substring("quest:".length());
                var progress = ClientQuestProgress.view(id);
                var quest = ClientQuestDefinitions.find(id);
                if (!editing && quest != null && quest.automaticallyGenerated()
                        && progress != null && !progress.unlocked()) continue;
                String count = progress == null || progress.required() <= 0 ? ""
                        : progress.current() + "/" + progress.required();
                refreshed.add(new Card(card.id(), false, card.title(), card.subtitle(), card.image(),
                        card.imageWidth(), card.imageHeight(), card.x(), card.y(),
                        progress != null && progress.completed(), count, card.hideImage()));
            }
        }
        cards = List.copyOf(refreshed);
        rebuildViewportIndexes();
    }

    private List<Card> visibleCards() {
        return cardIndex.query(canvasX(0), canvasY(viewportTop()), canvasX(width), canvasY(viewportBottom()));
    }

    private HotEventCanvas.Position position(String id, HotEventData data, int index) {
        HotEventCanvas.Position explicit = positions.get(id);
        if (explicit != null) return explicit;
        if (data.positioned()) return new HotEventCanvas.Position(data.x(), data.y());
        return new HotEventCanvas.Position(48 + index * (CARD_WIDTH + CARD_GAP), 52);
    }

    private int viewportTop() { return 35; }
    private int viewportBottom() { return Math.max(viewportTop(), height - 34); }
    private int viewportHeight() { return Math.max(1, viewportBottom() - viewportTop()); }
    private ScrollBounds scrollBounds() {
        if (cards.isEmpty()) return new ScrollBounds(0, 0);
        int left = contentLeft, right = contentRight;
        double origin = width / 2.0 * (1 - zoom);
        double minX = origin + left * zoom - CARD_GAP * zoom;
        double maxX = origin + right * zoom - width + CARD_GAP * zoom;
        if (minX > maxX) {
            double centered = origin + (left + right) / 2.0 * zoom - width / 2.0;
            return new ScrollBounds(centered, centered);
        }
        return new ScrollBounds(minX, maxX);
    }
    private int zoomTrackLeft() { return width / 2 - Math.min(90, Math.max(12, (width - 80) / 2)); }
    private int zoomTrackRight() { return width - zoomTrackLeft(); }
    private int zoomTrackY() { return height - 20; }
    private int scrollRailY() { return viewportBottom() - 7; }
    private int scrollThumbWidth() {
        ScrollBounds bounds = scrollBounds();
        return Math.min(width - 24, Math.max(24,
                (int) ((width - 24) * width / (width + bounds.maxX() - bounds.minX()))));
    }
    private int scrollThumbX() {
        ScrollBounds bounds = scrollBounds();
        double progress = Math.clamp((scrollX - bounds.minX()) / Math.max(1, bounds.maxX() - bounds.minX()), 0, 1);
        return 12 + (int) Math.round(progress * (width - 24 - scrollThumbWidth()));
    }
    private double scrollRailValue(double x) {
        ScrollBounds bounds = scrollBounds();
        return Math.clamp(bounds.minX() + (x - 12 - railGrab) / Math.max(1, width - 24 - scrollThumbWidth())
                * (bounds.maxX() - bounds.minX()), bounds.minX(), bounds.maxX());
    }

    @Override
    public void tick() {
        if (!isAdmin() && editing) {
            editing = false;
            draggingCard = false;
            wireFrom = "";
            contextId = null;
            contextConnection = null;
            reloadCards();
        }
        long revision = ClientQuestProgress.revision();
        if (!draggingCard && (ClientQuestDefinitions.revision() != catalogRevision
                || ClientQuestProgress.listRevision() != listRevision)) {
            catalogRevision = ClientQuestDefinitions.revision();
            progressRevision = ClientQuestProgress.revision();
            listRevision = ClientQuestProgress.listRevision();
            HotEventCanvas updated = ClientQuestDefinitions.hotEventCanvas();
            if (!canvas.backgroundMusic().equals(updated.backgroundMusic())
                    || !canvas.backgroundImage().equals(updated.backgroundImage())
                    || !canvas.backgroundType().equals(updated.backgroundType()))
                ClientMediaManager.release(mediaOwner);
            canvas = updated;
            positions.clear();
            positions.putAll(updated.positions());
            reloadCards();
            clampScroll();
        } else if (!draggingCard && revision != progressRevision) {
            refreshCardProgress();
        }
        progressRevision = revision;
    }

    private void clampScroll() {
        clampTargets();
        if (!draggingCanvas && !draggingScrollRail) {
            ScrollBounds bounds = scrollBounds();
            scrollX = Math.clamp(scrollX, bounds.minX(), bounds.maxX());
        }
    }

    private void clampTargets() {
        ScrollBounds bounds = scrollBounds();
        targetScrollX = Math.clamp(targetScrollX, bounds.minX(), bounds.maxX());
    }

    private static double rubberBand(double value, double min, double max, double limit) {
        if (value < min) return min - Math.min(limit, (min - value) * 0.35);
        if (value > max) return max + Math.min(limit, (value - max) * 0.35);
        return value;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        double dt = scrollFrame == 0 ? 1. / 60 : Math.clamp((now - scrollFrame) / 1e9, 0, .05);
        scrollFrame = now;
        if (!draggingCanvas) {
            scrollX += (targetScrollX - scrollX) * -Math.expm1(-16 * dt);
            if (Math.abs(targetScrollX - scrollX) < .05) scrollX = targetScrollX;
        }
        renderCanvasBackground(graphics);
        visibleCards = visibleCards();
        if (editing) renderGrid(graphics);
        graphics.enableScissor(0, viewportTop(), width, viewportBottom());
        renderConnections(graphics);
        renderCards(graphics, mouseX, mouseY);
        graphics.disableScissor();
        JournalNavButton.drawIcon(graphics, "recommended", 12, 6, 23, 0xFFF2D29A);
        graphics.drawString(font, Component.translatable("screen.xero_quest_journal.hot_events.title"),
                42, 13, 0xFFF4D49B, true);
        if (isAdmin()) {
            boolean hover = mouseX >= width - 66 && mouseX < width - 36 && mouseY >= 4 && mouseY < 28;
            ToolIconRenderer.surface(graphics, width - 63, 4, 24, 24, hover || editing, 1);
            ToolIconRenderer.icon(graphics, editing ? "close" : "edit", width - 63, 4, 24, 24, 1);
        }
        ToolIconRenderer.surface(graphics, width - 33, 4, 24, 24,
                mouseX >= width - 36 && mouseY >= 4 && mouseY < 28, 1);
        ToolIconRenderer.icon(graphics, "close", width - 33, 4, 24, 24, 1);
        if (cards.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.xero_quest_journal.hot_events.empty"),
                    width / 2, height / 2 - 4, 0xFFD7DEE8);
        }
        renderFooter(graphics);
        int railY = scrollRailY(), thumbX = scrollThumbX();
        graphics.fill(12, railY + 2, width - 12, railY + 5, 0xFF303741);
        graphics.fill(thumbX, railY + 2, thumbX + scrollThumbWidth(), railY + 5,
                draggingScrollRail ? 0xFFF3D48B : 0xFFABB8C8);
        if (!message.isBlank()) graphics.drawString(font, message, 12, height - 34, 0xFFF2CF83, true);
        int menuHeight = contextOptions().size() * 19 + 8;
        int menuX = Math.clamp(contextX, 4, Math.max(4, width - 136));
        int menuY = Math.clamp(contextY, 4, Math.max(4, height - menuHeight - 4));
        renderAnimatedMenu(graphics, "context", contextId,
                viewport(menuX - 1, menuY - 1, menuX + 133, menuY + menuHeight + 1),
                () -> renderContextMenu(graphics));
        if (!editing && !draggingCanvas && !draggingCard && !draggingZoom && contextId == null
                && !inputBlocked() && mouseY >= viewportTop() && mouseY < viewportBottom()) {
            var hovered = cardAt(mouseX, mouseY);
            if (hovered != null) graphics.renderTooltip(font, cardTooltip(hovered), mouseX, mouseY);
        }
    }

    private void renderCanvasBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xFF1C1D22);
        String source = canvas.backgroundImage();
        if (!canvas.backgroundMusic().isBlank())
            ClientMediaManager.renderAudio(graphics, mediaOwner, "background-music", canvas.backgroundMusic(),
                    0, 0, 1, 1, 1, true, true, false, 1, false, "");
        if (source.isBlank()) return;
        double scale = canvas.backgroundScale();
        int left = contentLeft, right = contentRight + CARD_GAP;
        double canvasLeft = Math.min(0, left);
        double canvasRight = Math.max(width, right);
        double canvasWidth = canvasRight - canvasLeft;
        int w = Math.max(1, (int) Math.round(canvasWidth * scale * zoom));
        int h = Math.max(1, (int) Math.round(height * scale * zoom));
        int x = (int) Math.round(screenX(canvasLeft + (canvasWidth - canvasWidth * scale) / 2
                + canvas.backgroundOffsetX()));
        int y = (int) Math.round(screenY(canvasCenterY - height * scale / 2
                + canvas.backgroundOffsetY()));
        graphics.enableScissor(0, 0, width, height);
        if (canvas.backgroundType().equals("video")) {
            ClientMediaManager.renderVideo(graphics, mediaOwner, "background", source, x, y, w, h, 1,
                    true, true, true, 0, canvas.backgroundMode(), "");
        } else {
            ClientMediaManager.renderImageScaled(graphics, source, x, y, w, h, 1, canvas.backgroundMode());
        }
        graphics.disableScissor();
    }

    private void renderGrid(GuiGraphics graphics) {
        graphics.enableScissor(0, viewportTop(), width, viewportBottom());
        double step = 50 * zoom;
        for (double x = screenX(0) % step; x < width; x += step) {
            int px = (int) Math.round(x);
            graphics.fill(px, viewportTop(), px + 1, viewportBottom(), 0x345F6B78);
        }
        for (double y = screenY(0) % step; y < viewportBottom(); y += step) {
            int py = (int) Math.round(y);
            graphics.fill(0, py, width, py + 1, 0x345F6B78);
        }
        graphics.disableScissor();
    }

    private void renderConnections(GuiGraphics graphics) {
        for (var connection : connectionIndex.query(canvasX(0), canvasY(viewportTop()),
                canvasX(width), canvasY(viewportBottom()))) {
            double[] points = connectionPoints(connection);
            if (points == null) continue;
            for (int step = 1; step < points.length / 2; step++) {
                drawSegment(graphics, points[(step - 1) * 2], points[(step - 1) * 2 + 1],
                        points[step * 2], points[step * 2 + 1], 0xD8E9C681);
            }
        }
        if (editing && !wireFrom.isBlank()) {
            Card source = findCard(wireFrom);
            if (source != null) drawSegment(graphics, screenX(source.x() + CARD_WIDTH),
                    screenY(source.y() + CARD_HEIGHT / 2.0), wireX, wireY, 0xFFF2D29A);
        }
    }

    private double[] connectionPoints(HotEventCanvas.Connection connection) {
        Card from = findCard(connection.from());
        Card to = findCard(connection.to());
        if (from == null || to == null) return null;
        double x1 = screenX(from.x() + CARD_WIDTH);
        double y1 = screenY(from.y() + CARD_HEIGHT / 2.0);
        double x2 = screenX(to.x());
        double y2 = screenY(to.y() + CARD_HEIGHT / 2.0);
        return new double[]{x1, y1, x2, y2};
    }

    private HotEventCanvas.Connection connectionAt(double x, double y) {
        double toleranceSquared = 8 * 8;
        for (HotEventCanvas.Connection connection : connectionIndex.query(canvasX(x - 8), canvasY(y - 8),
                canvasX(x + 8), canvasY(y + 8))) {
            double[] points = connectionPoints(connection);
            if (points == null) continue;
            for (int step = 1; step < points.length / 2; step++) {
                if (distanceToSegmentSquared(x, y, points[(step - 1) * 2], points[(step - 1) * 2 + 1],
                        points[step * 2], points[step * 2 + 1]) <= toleranceSquared) {
                    return connection;
                }
            }
        }
        return null;
    }

    private double distanceToSegmentSquared(double x, double y, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.clamp(((x - x1) * dx + (y - y1) * dy) / lengthSquared, 0, 1);
        double nearestX = x1 + t * dx, nearestY = y1 + t * dy;
        double offsetX = x - nearestX, offsetY = y - nearestY;
        return offsetX * offsetX + offsetY * offsetY;
    }

    private void drawSegment(GuiGraphics graphics, double x1, double y1, double x2, double y2, int color) {
        var clipped = ViewportIndex.clipSegment(x1, y1, x2, y2, -2, viewportTop() - 2, width + 2, viewportBottom() + 2);
        if (clipped == null) return;
        x1 = clipped[0]; y1 = clipped[1]; x2 = clipped[2]; y2 = clipped[3];
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t);
            int y = (int) Math.round(y1 + (y2 - y1) * t);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void renderCards(GuiGraphics graphics, int mouseX, int mouseY) {
        int cardWidth = Math.max(1, (int) Math.round(CARD_WIDTH * zoom));
        int labelHeight = Math.max(1, (int) Math.round(LABEL_HEIGHT * zoom));
        int gap = Math.max(1, (int) Math.round(2 * zoom));
        for (Card card : visibleCards) {
            int cardHeight = Math.max(1, (int) Math.round(cardHeight(card) * zoom));
            int x = (int) Math.round(screenX(card.x()));
            int y = (int) Math.round(screenY(card.y() + (CARD_HEIGHT - cardHeight(card)) / 2.0));
            if (x + cardWidth <= 0 || x >= width || y + cardHeight <= viewportTop()
                    || y >= viewportBottom()) continue;
            int imageHeight = card.hideImage() ? 0 : cardHeight - labelHeight - gap;
            if (!card.hideImage()) {
                graphics.fill(x - 1, y - 1, x + cardWidth + 1, y + imageHeight + 1, 0xFFEDE6D8);
                graphics.fill(x, y, x + cardWidth, y + imageHeight, 0xFF30343B);
                graphics.enableScissor(Math.max(0, x + 1), Math.max(viewportTop(), y + 1),
                        Math.min(width, x + cardWidth - 1), Math.min(viewportBottom(), y + imageHeight - 1));
                if (!card.image().isBlank() && (ClientMediaManager.validImageSource(card.image())
                        || card.image().startsWith(QuestIcons.LOCAL_NAMESPACE + ":")
                        || card.image().contains("textures/"))) {
                    int coverWidth = card.imageWidth() == 0 ? cardWidth : (int) Math.round(card.imageWidth() * zoom);
                    int coverHeight = card.imageHeight() == 0 ? imageHeight
                            : (int) Math.round(card.imageHeight() * zoom);
                    ClientMediaManager.renderImageCover(graphics, card.image(),
                            x + (cardWidth - coverWidth) / 2, y + (imageHeight - coverHeight) / 2,
                            Math.max(1, coverWidth), Math.max(1, coverHeight), 1);
                } else {
                    QuestIcons.render(graphics, card.image(), x + (int) (10 * zoom), y + (int) (10 * zoom),
                            Math.max(1, (int) (36 * zoom)));
                }
                graphics.disableScissor();
            }
            int labelY = y + (card.hideImage() ? 0 : imageHeight + gap);
            graphics.fill(x - 1, labelY - 1, x + cardWidth + 1, y + cardHeight + 1, 0xFFEDE6D8);
            graphics.fill(x, labelY, x + cardWidth, y + cardHeight, 0xFFFDFBF7);
            graphics.fill(x, labelY, x + Math.max(2, (int) Math.round(3 * zoom)),
                    y + cardHeight, 0xFFF3D27D);
            int statusSize = Math.max(1, (int) Math.round(13 * zoom));
            int textWidth = Math.max(1, cardWidth - 16 - (card.completed() ? statusSize + 4 : 0));
            String title = font.plainSubstrByWidth(card.title(), (int) (textWidth / zoom));
            int textX = x + Math.max(8, (cardWidth - (int) Math.round(font.width(title) * zoom)) / 2
                    - (card.completed() ? statusSize / 2 : 0));
            graphics.pose().pushPose();
            graphics.pose().translate(textX, labelY + (labelHeight - font.lineHeight * zoom) / 2, 0);
            graphics.pose().scale((float) zoom, (float) zoom, 1);
            graphics.drawString(font, title, 0, 0, 0xFF475161, false);
            graphics.pose().popPose();
            if (card.completed()) {
                drawCompleted(graphics, x + cardWidth - statusSize - 3,
                        labelY + (labelHeight - statusSize) / 2, statusSize);
            }
            if (editing && card.id().equals(canvas.startId())) {
                graphics.pose().pushPose();
                graphics.pose().translate(x + 5 * zoom, y + 5 * zoom, 0);
                graphics.pose().scale((float) zoom, (float) zoom, 1);
                graphics.drawString(font, "★", 0, 0, 0xFFFFD36B, true);
                graphics.pose().popPose();
            }
            if (editing) {
                drawPort(graphics, x, y + cardHeight / 2, 0xFFAAC5D7);
                drawPort(graphics, x + cardWidth, y + cardHeight / 2,
                        card.id().equals(wireFrom) ? 0xFFFFD36B : 0xFF79B9A5);
            }
            boolean hovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight;
            if (hovered) drawCorners(graphics, x, y, cardWidth, cardHeight);
        }
    }
    private int cardHeight(Card card) { return card.hideImage() ? LABEL_HEIGHT : CARD_HEIGHT; }
    private void drawPort(GuiGraphics graphics, int x, int y, int color) {
        int radius = Math.max(2, (int) Math.round(4 * zoom));
        graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
    }

    private void drawCompleted(GuiGraphics graphics, int x, int y, int size) {
        int color = 0xFFB4FA8B;
        int thickness = Math.max(2, Math.round(size * 0.085F));
        double cx = x + size * 0.48, cy = y + size * 0.52;
        double radius = size * 0.36;
        for (int angle = -65; angle < 260; angle += 4) {
            double a = Math.toRadians(angle), b = Math.toRadians(Math.min(angle + 4, 260));
            drawStroke(graphics, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius,
                    cx + Math.cos(b) * radius, cy + Math.sin(b) * radius, thickness, color);
        }
        drawStroke(graphics, x + size * 0.22, y + size * 0.51, x + size * 0.44,
                y + size * 0.70, thickness, color);
        drawStroke(graphics, x + size * 0.44, y + size * 0.70, x + size * 0.86,
                y + size * 0.19, thickness, color);
    }

    private void drawStroke(GuiGraphics graphics, double x1, double y1, double x2, double y2,
                            int thickness, int color) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1) * 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int px = (int) Math.round(x1 + (x2 - x1) * t);
            int py = (int) Math.round(y1 + (y2 - y1) * t);
            graphics.fill(px - thickness / 2, py - thickness / 2,
                    px - thickness / 2 + thickness, py - thickness / 2 + thickness, color);
        }
    }

    private void drawCorners(GuiGraphics graphics, int x, int y, int width, int height) {
        int length = Math.max(1, (int) Math.round(13 * zoom));
        int line = Math.max(1, (int) Math.round(2 * zoom));
        int inset = Math.max(1, (int) Math.round(7 * zoom));
        int left = x - inset, right = x + width + inset;
        int top = y - inset, bottom = y + height + inset;
        int color = 0xFFFFE6AB;
        graphics.fill(left, top, left + length, top + line, color);
        graphics.fill(left, top, left + line, top + length, color);
        graphics.fill(right - length, top, right, top + line, color);
        graphics.fill(right - line, top, right, top + length, color);
        graphics.fill(left, bottom - line, left + length, bottom, color);
        graphics.fill(left, bottom - length, left + line, bottom, color);
        graphics.fill(right - length, bottom - line, right, bottom, color);
        graphics.fill(right - line, bottom - length, right, bottom, color);
    }

    private void renderFooter(GuiGraphics graphics) {
        int y = zoomTrackY();
        graphics.drawCenteredString(font, "−", zoomTrackLeft() - 13, y - 4, 0xFFE4D8C8);
        graphics.drawCenteredString(font, "+", zoomTrackRight() + 13, y - 4, 0xFFE4D8C8);
        graphics.fill(zoomTrackLeft(), y, zoomTrackRight(), y + 3, 0xBB5C6065);
        int thumb = zoomTrackLeft() + (int) Math.round((zoomTrackRight() - zoomTrackLeft())
                * (zoom * 100 - ZOOM_MIN) / (double) (ZOOM_MAX - ZOOM_MIN));
        graphics.fill(thumb - 3, y - 4, thumb + 4, y + 7, 0xFFF2DFBC);
    }

    private void renderContextMenu(GuiGraphics graphics) {
        if (contextId == null) return;
        List<String> options = contextOptions();
        int width = 132;
        int height = options.size() * 19 + 8;
        int x = Math.clamp(contextX, 4, Math.max(4, this.width - width - 4));
        int y = Math.clamp(contextY, 4, Math.max(4, this.height - height - 4));
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                net.minecraft.client.Minecraft.ON_OSX);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFFD9C18F);
        graphics.fill(x, y, x + width, y + height, 0xF02A303A);
        for (int i = 0; i < options.size(); i++) {
            graphics.drawString(font, Component.translatable("screen.xero_quest_journal.hot_events.menu."
                    + options.get(i)), x + 7, y + 5 + i * 19, 0xFFEAF0F6);
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    private List<String> contextOptions() {
        if (contextConnection != null) return List.of("remove_connection");
        if (contextId == null) return List.of();
        if (contextId.isEmpty()) return List.of("background", "clear_background");
        return List.of("properties", "start", "remove_links");
    }

    private Card findCard(String id) {
        return id == null ? null : cardLookup.get(id);
    }

    private Component cardTooltip(Card card) {
        if (!card.group()) {
            var quest = ClientQuestDefinitions.find(card.id().substring("quest:".length()));
            if (quest != null) {
                var content = QuestText.tooltip(quest);
                return card.title().equals(QuestText.title(quest).getString()) ? content
                        : Component.literal(card.title()).withStyle(net.minecraft.ChatFormatting.GOLD)
                        .append("\n").append(content);
            }
        }
        return Component.literal(card.title()).withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.literal(card.subtitle().isBlank() ? "" : "\n" + card.subtitle())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private double screenX(double canvasX) {
        return width / 2.0 + (canvasX - width / 2.0) * zoom - scrollX;
    }
    private double screenY(double canvasY) { return height / 2.0 + (canvasY - canvasCenterY) * zoom; }
    private double canvasX(double x) { return width / 2.0 + (x - width / 2.0 + scrollX) / zoom; }
    private double canvasY(double y) { return canvasCenterY + (y - height / 2.0) / zoom; }

    private void setZoom(double value) {
        double next = Math.clamp(value, ZOOM_MIN / 100.0, ZOOM_MAX / 100.0);
        // Both the visible position and pending scroll use the same fixed screen-centre pivot.
        double ratio = next / zoom;
        scrollX *= ratio;
        targetScrollX *= ratio;
        zoom = next;
        clampScroll();
    }

    private Card cardAt(double x, double y) {
        return cardIndex.query(canvasX(x), canvasY(y), canvasX(x), canvasY(y)).stream().filter(card -> {
            double left = screenX(card.x());
            double top = screenY(card.y() + (CARD_HEIGHT - cardHeight(card)) / 2.0);
            return x >= left && x < left + CARD_WIDTH * zoom
                    && y >= top && y < top + cardHeight(card) * zoom;
        }).findFirst().orElse(null);
    }

    private void updateCanvas(String background, String start, List<HotEventCanvas.Connection> edges,
                             Map<String, HotEventCanvas.Position> nextPositions) {
        updateCanvas(new HotEventCanvas(background, start, edges, true, nextPositions,
                canvas.backgroundType(), canvas.backgroundScale(),
                canvas.backgroundOffsetX(), canvas.backgroundOffsetY(), canvas.backgroundMode(),
                canvas.backgroundMusic()));
    }

    private void updateCanvas(HotEventCanvas next) {
        if (!isAdmin() || !editing) return;
        if (!canvas.backgroundMusic().equals(next.backgroundMusic())
                || !canvas.backgroundImage().equals(next.backgroundImage())
                || !canvas.backgroundType().equals(next.backgroundType())) ClientMediaManager.release(mediaOwner);
        canvas = next;
        positions.clear();
        positions.putAll(next.positions());
        if (!ClientHotEvents.save(canvas)) {
            message = Component.translatable("screen.xero_quest_journal.hot_events.save_failed").getString();
        } else {
            message = "";
        }
        reloadCards();
        clampScroll();
    }

    private void setStart(String id) {
        updateCanvas(canvas.backgroundImage(), id, canvas.connections(), positions);
    }

    private void connect(String from, String to) {
        if (from.equals(to)) return;
        List<HotEventCanvas.Connection> edges = new ArrayList<>(canvas.connections());
        HotEventCanvas.Connection edge = new HotEventCanvas.Connection(from, to);
        if (!edges.contains(edge)) edges.add(edge);
        updateCanvas(canvas.backgroundImage(), canvas.startId(), edges, positions);
    }
    private Card portAt(double x, double y, boolean output) {
        for (Card card : cardIndex.query(canvasX(x - 10), canvasY(y - 10), canvasX(x + 10), canvasY(y + 10))) {
            double px = screenX(card.x() + (output ? CARD_WIDTH : 0));
            double py = screenY(card.y() + CARD_HEIGHT / 2.0);
            if (Math.abs(x - px) <= 9 && Math.abs(y - py) <= 9) return card;
        }
        return null;
    }

    private void removeLinks(String id) {
        List<HotEventCanvas.Connection> edges = canvas.connections().stream()
                .filter(edge -> !edge.from().equals(id) && !edge.to().equals(id)).toList();
        contextId = null;
        updateCanvas(canvas.backgroundImage(), canvas.startId(), edges, positions);
    }

    private void removeConnection(HotEventCanvas.Connection connection) {
        List<HotEventCanvas.Connection> edges = canvas.connections().stream()
                .filter(edge -> !edge.equals(connection)).toList();
        contextId = null;
        contextConnection = null;
        updateCanvas(canvas.backgroundImage(), canvas.startId(), edges, positions);
    }

    private void openBackgroundEditor() {
        contextId = null;
        contextConnection = null;
        minecraft.setScreen(new HotEventBackgroundScreen(this, canvas, this::updateCanvas));
    }

    private void openCardProperties(Card card) {
        contextId = null;
        contextConnection = null;
        String kind = card.group() ? "group" : "task";
        String id = card.id().substring(card.id().indexOf(':') + 1);
        minecraft.setScreen(QuestScreen.hotEventProperties(this, kind, id));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (!isAdmin()) {
            editing = false;
            contextId = null;
            contextConnection = null;
            wireFrom = "";
            draggingCard = false;
        }
        if (contextId != null && button == 0) {
            handleContextClick(mouseX, mouseY);
            return true;
        }
        if (button == 0 && mouseY < 32) {
            if (mouseX >= width - 36) {
                onClose();
                return true;
            }
            if (isAdmin() && mouseX >= width - 66 && mouseX < width - 36) {
                editing = !editing;
                contextId = null;
                return true;
            }
        }
        if (button == 0 && mouseY >= scrollRailY() && mouseY < viewportBottom()
                && mouseX >= 12 && mouseX < width - 12) {
            draggingScrollRail = mouseX >= scrollThumbX() && mouseX < scrollThumbX() + scrollThumbWidth();
            railGrab = draggingScrollRail ? mouseX - scrollThumbX() : scrollThumbWidth() / 2.;
            if (!draggingScrollRail) targetScrollX = scrollRailValue(mouseX);
            return true;
        }
        if (editing && button == 1 && mouseY >= viewportTop() && mouseY < viewportBottom()) {
            Card card = cardAt(mouseX, mouseY);
            contextConnection = card == null ? connectionAt(mouseX, mouseY) : null;
            contextId = card != null ? card.id() : contextConnection == null ? "" : "connection";
            contextX = (int) mouseX;
            contextY = (int) mouseY;
            return true;
        }
        if (button == 0 && mouseY >= zoomTrackY() - 7 && mouseY <= zoomTrackY() + 10
                && mouseX >= zoomTrackLeft() && mouseX <= zoomTrackRight()) {
            draggingZoom = true;
            updateZoom(mouseX);
            return true;
        }
        if (button != 0 || mouseY < viewportTop() || mouseY >= viewportBottom()) return false;
        if (editing && isAdmin()) {
            Card output = portAt(mouseX, mouseY, true);
            if (output != null) {
                wireFrom = output.id();
                wireX = mouseX;
                wireY = mouseY;
                return true;
            }
        }
        Card card = cardAt(mouseX, mouseY);
        if (card != null) {
            selectedId = card.id();
            if (editing && isAdmin()) {
                draggingCard = true;
                dragOffsetX = canvasX(mouseX) - card.x();
                dragOffsetY = canvasY(mouseY) - card.y();
            } else {
                lastClickId = card.id();
                draggingCanvas = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragStartScroll = scrollX;
            }
        } else {
            lastClickId = "";
            draggingCanvas = true;
            dragStartX = mouseX;
            dragStartY = mouseY;
            dragStartScroll = scrollX;
        }
        return true;
    }

    private void handleContextClick(double mouseX, double mouseY) {
        String id = contextId;
        List<String> options = contextOptions();
        int x = Math.clamp(contextX, 4, Math.max(4, width - 136));
        int y = Math.clamp(contextY, 4, Math.max(4, height - (options.size() * 19 + 12)));
        if (mouseX < x || mouseX >= x + 132 || mouseY < y || mouseY >= y + options.size() * 19 + 8) {
            contextId = null;
            contextConnection = null;
            return;
        }
        int index = (int) ((mouseY - y - 4) / 19);
        if (index < 0 || index >= options.size()) return;
        String choice = options.get(index);
        if (id.isEmpty()) {
            if (choice.equals("background")) openBackgroundEditor();
            else {
                contextId = null;
                contextConnection = null;
                updateCanvas(new HotEventCanvas("", canvas.startId(), canvas.connections(), true,
                        positions, canvas.backgroundType(), canvas.backgroundScale(),
                        canvas.backgroundOffsetX(), canvas.backgroundOffsetY(), canvas.backgroundMode(),
                        canvas.backgroundMusic()));
            }
            return;
        }
        if (contextConnection != null) {
            HotEventCanvas.Connection connection = contextConnection;
            if (choice.equals("remove_connection")) removeConnection(connection);
            else {
                contextId = null;
                contextConnection = null;
            }
            return;
        }
        Card card = findCard(id);
        contextId = null;
        contextConnection = null;
        switch (choice) {
            case "properties" -> { if (card != null) openCardProperties(card); }
            case "start" -> setStart(id);
            case "remove_links" -> removeLinks(id);
            default -> contextId = null;
        }
    }

    private void updateZoom(double mouseX) {
        double progress = Math.clamp((mouseX - zoomTrackLeft()) / (double) (zoomTrackRight() - zoomTrackLeft()), 0, 1);
        setZoom((ZOOM_MIN + progress * (ZOOM_MAX - ZOOM_MIN)) / 100.0);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0) return false;
        if (draggingScrollRail) {
            scrollX = targetScrollX = scrollRailValue(mouseX);
            return true;
        }
        if (!wireFrom.isBlank()) {
            wireX = mouseX;
            wireY = mouseY;
            return true;
        }
        if (draggingZoom) {
            updateZoom(mouseX);
            return true;
        }
        if (draggingCanvas) {
            ScrollBounds bounds = scrollBounds();
            targetScrollX = rubberBand(dragStartScroll + dragStartX - mouseX,
                    bounds.minX(), bounds.maxX(), Math.min(40, width * 0.1));
            scrollX = targetScrollX;
            if (Math.abs(mouseX - dragStartX) > 4 || Math.abs(mouseY - dragStartY) > 4) lastClickId = "";
            return true;
        }
        if (draggingCard && isAdmin()) {
            Card selected = findCard(selectedId);
            if (selected == null) return false;
            int x = (int) Math.round(canvasX(mouseX) - dragOffsetX);
            int y = (int) Math.round(canvasY(mouseY) - dragOffsetY);
            positions.put(selectedId, new HotEventCanvas.Position(x, y));
            reloadCards();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollRail) {
            draggingScrollRail = false;
            return true;
        }
        if (button == 0 && !wireFrom.isBlank()) {
            Card target = portAt(mouseX, mouseY, false);
            if (target != null && isAdmin()) connect(wireFrom, target.id());
            wireFrom = "";
            return true;
        }
        if (button == 0 && draggingCard) {
            draggingCard = false;
            if (isAdmin()) updateCanvas(canvas.backgroundImage(), canvas.startId(),
                    canvas.connections(), positions);
            return true;
        }
        if (button == 0 && (draggingZoom || draggingCanvas)) {
            Card clicked = draggingCanvas && !editing && !lastClickId.isEmpty() ? cardAt(mouseX, mouseY) : null;
            draggingZoom = false;
            draggingCanvas = false;
            clampTargets();
            if (clicked != null && clicked.id().equals(lastClickId) && !clicked.group() && !clicked.completed()
                    && Math.abs(mouseX - dragStartX) < 4 && Math.abs(mouseY - dragStartY) < 4) {
                String taskId = clicked.id().substring(clicked.id().indexOf(':') + 1);
                var progress = ClientQuestProgress.view(taskId);
                if (progress == null || !progress.completed()) minecraft.setScreen(QuestScreen.task(null, taskId));
            }
            lastClickId = "";
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (scrollChoicePopup(mouseX, mouseY, horizontal, vertical)) return true;
        if (contextId != null) return true;
        if (mouseY >= viewportTop() && mouseY < viewportBottom()) {
            if (horizontal != 0 && !Screen.hasControlDown()) targetScrollX -= horizontal * 48;
            else setZoom(zoom + vertical * 0.08);
            clampScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (contextId != null) {
                contextId = null;
                contextConnection = null;
                return true;
            }
            if (!wireFrom.isBlank()) {
                wireFrom = "";
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            targetScrollX += key == GLFW.GLFW_KEY_LEFT ? -24 : 24;
            clampScroll();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        closeTo(parent);
    }

    @Override
    public void removed() {
        ClientMediaManager.release(mediaOwner);
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
