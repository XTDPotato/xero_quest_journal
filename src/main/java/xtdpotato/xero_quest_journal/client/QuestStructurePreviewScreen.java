package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.components.Tooltip;
import xtdpotato.xero_quest_journal.core.model.QuestJigsawInfo;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuestStructurePreviewScreen extends AnimatedQuestScreen {
    private static final long RESET_DOUBLE_CLICK_NANOS = 350_000_000L;
    private final Screen parent;
    private final String reference;
    private float angle = 35, zoom = 1;
    private float panX, panY;
    private double lastMiddleX, lastMiddleY;
    private long lastMiddleClick;
    private boolean middlePanning, middleMoved, rotating;
    private boolean inspector, guide, canvasMoved, readingDrag;
    private double pressX, pressY, readingStart;
    private List<QuestJigsawInfo> points = List.of();
    private int selected;
    private Object loadedModel;
    private final SmoothScroll reading = new SmoothScroll();
    private final List<TextLine> lines = new ArrayList<>();
    private record TextLine(int y, FormattedCharSequence text, int color) {}
    public QuestStructurePreviewScreen(Screen parent, String reference) {
        super(Component.translatable("screen.xero_quest_journal.structure.preview"));
        this.parent = parent; this.reference = reference;
    }
    @Override protected void init() {
        clearWidgets();
        var model = ClientStructures.model(reference);
        loadedModel = model;
        points = QuestJigsawInfo.read(model);
        selected = Math.min(selected, Math.max(0, points.size() - 1));
        var styles = ButtonStyleSheet.load();
        addRenderableWidget(new EditorIconButton(width - 36, 6, Component.translatable("gui.back"),
                this::onClose, styles, "structure-close", "close").dark());
        addRenderableWidget(new EditorIconButton(width - 64, 6, text("reset"),
                this::resetView, styles, "structure-reset", "fit").dark());
        var inspect = addRenderableWidget(new StyledButton(width - 202, 8, 132, 20,
                Component.translatable("screen.xero_quest_journal.structure.jigsaw.title", points.size()),
                button -> { inspector = !inspector; init(); }, styles, "structure-jigsaws"));
        inspect.active = !points.isEmpty();
        inspect.setTooltip(Tooltip.create(text(points.isEmpty() ? "none" : "inspect")));
        if (!inspector || points.isEmpty()) return;
        int px = panelX(), py = panelY(), pw = panelWidth();
        int tabWidth = (pw - 6) / 2;
        addRenderableWidget(new StyledButton(px, py, tabWidth, 20, text("properties"),
                button -> { guide = false; reading.jump(0); init(); }, styles, "jigsaw-properties")).active = guide;
        addRenderableWidget(new StyledButton(px + tabWidth + 6, py, pw - tabWidth - 6, 20, text("guide"),
                button -> { guide = true; reading.jump(0); init(); }, styles, "jigsaw-guide")).active = !guide;
        addRenderableWidget(new StyledButton(px, py + 26, pw, 22,
                Component.translatable("screen.xero_quest_journal.structure.jigsaw.point",
                        selected + 1, points.size(), current().coordinates()),
                button -> minecraft.setScreen(new QuestValuePickerScreen(this, text("choose").getString(),
                        "", () -> java.util.stream.IntStream.range(0, points.size())
                        .mapToObj(index -> new QuestValuePickerScreen.Option(Integer.toString(index),
                                (index + 1) + "  [" + points.get(index).coordinates() + "]  " + points.get(index).name()))
                        .toList(), Integer.toString(selected), value -> {
                            selected = Integer.parseInt(value); reading.jump(0);
                        })), styles, "jigsaw-choose"));
        var pool = addRenderableWidget(new StyledButton(px, height - 34, pw, 22, text("open_pool"),
                button -> minecraft.setScreen(QuestStructurePickerScreen.inspectPool(this, current().pool())),
                styles, "jigsaw-pool"));
        pool.active = minecraft.getSingleplayerServer() != null
                && net.minecraft.resources.ResourceLocation.tryParse(current().pool()) != null
                && !current().pool().equals("minecraft:empty");
        pool.setTooltip(Tooltip.create(text(minecraft.getSingleplayerServer() == null
                ? "local_only" : "pool_preview_hint")));
        buildLines();
        addRenderableWidget(new ScrollRail(px + pw - 8, contentTop(),
                Math.max(8, contentBottom() - contentTop()), reading));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.structure.jigsaw." + key, args);
    }
    private QuestJigsawInfo current() { return points.get(selected); }
    private boolean split() { return width >= 600; }
    private boolean inspecting() { return inspector && !points.isEmpty(); }
    private int panelWidth() { return split() ? Math.min(340, width * 2 / 5) : width - 24; }
    private int panelX() { return split() ? width - panelWidth() - 12 : 12; }
    private int panelY() { return split() ? 40 : Math.max(112, height * 42 / 100); }
    private int previewWidth() { return inspecting() && split() ? panelX() - 28 : width - 32; }
    private int previewHeight() { return inspecting() && !split() ? panelY() - 48 : height - 56; }
    private int contentTop() { return panelY() + 56; }
    private int contentBottom() { return height - 42; }

    private void buildLines() {
        lines.clear();
        var point = current();
        if (guide) {
            paragraph(text("diagram"), 0xFFFFD77F);
            paragraph(Component.literal("A.target  =  B.name"), 0xFF74DDDE);
            for (int step = 1; step <= 6; step++) {
                paragraph(text("step" + step + "_title"), 0xFFFFD77F);
                paragraph(text("step" + step), 0xFFCCD5DE);
            }
            paragraph(text("limits"), 0xFFE2B3A0);
        } else {
            if (!point.complete()) paragraph(text("incomplete"), 0xFFE2B3A0);
            field("position", point.coordinates());
            field("front", direction(point.front()));
            field("top", direction(point.top()));
            field("name", point.name());
            field("target", point.target());
            field("pool", point.pool());
            field("joint", text(point.joint()).getString() + " (" + point.joint() + ")");
            field("final_state", point.finalState());
            if (point.priorities()) {
                field("selection_priority", Integer.toString(point.selectionPriority()));
                field("placement_priority", Integer.toString(point.placementPriority()));
            }
        }
        int total = lines.isEmpty() ? 0 : lines.getLast().y() + font.lineHeight + 8;
        reading.limit(total - Math.max(1, contentBottom() - contentTop()));
    }

    private String direction(net.minecraft.core.Direction direction) {
        return text("direction." + direction.getName()).getString() + " (" + direction.getName() + ")";
    }
    private void field(String key, String value) {
        paragraph(text("field." + key), 0xFFFFD77F);
        paragraph(Component.literal(value.isEmpty() ? text("missing").getString() : value), 0xFFF0F3F5);
        paragraph(text("hint." + key), 0xFFAEBFC9);
    }
    private void paragraph(Component value, int color) {
        int y = lines.isEmpty() ? 0 : lines.getLast().y() + font.lineHeight + 5;
        for (var line : font.split(value, Math.max(40, panelWidth() - 24))) {
            lines.add(new TextLine(y, line, color));
            y += font.lineHeight + 2;
        }
    }

    @Override public void tick() {
        if (ClientStructures.model(reference) != loadedModel) init();
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, 0xFF20272B);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(20, width - 224)),
                12, 12, 0xFFE7D6AC, false);
        StructurePreviewRenderer.render(g, reference, 16, 40, previewWidth(), previewHeight(),
                angle, zoom, panX, panY, inspecting() ? current().position() : null);
        if (inspecting()) {
            reading.advance();
            if (split()) g.fill(panelX() - 8, panelY(), panelX() - 7, height - 12, 0xFF4E626B);
            else g.fill(panelX(), panelY() - 5, width - 12, panelY() - 4, 0xFF4E626B);
            g.enableScissor(panelX(), contentTop(), panelX() + panelWidth() - 12, contentBottom());
            for (var line : lines) {
                int ly = contentTop() + line.y() - reading.pixels();
                if (ly + font.lineHeight >= contentTop() && ly < contentBottom())
                    g.drawString(font, line.text(), panelX() + 2, ly, line.color(), false);
            }
            g.disableScissor();
        }
        super.renderContents(g, x, y, delta);
    }

    private boolean inPreview(double x, double y) {
        return x >= 16 && x < 16 + previewWidth() && y >= 40 && y < 40 + previewHeight();
    }
    private boolean inReading(double x, double y) {
        return inspecting() && x >= panelX() && x < panelX() + panelWidth() - 10
                && y >= contentTop() && y < contentBottom();
    }

    private void resetView() {
        angle = 35;
        zoom = 1;
        panX = panY = 0;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && inReading(x, y)) {
            readingDrag = true;
            pressY = y;
            readingStart = reading.position();
            reading.freeze();
            return true;
        }
        if (inPreview(x, y) && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            long now = System.nanoTime();
            boolean doubleClick = lastMiddleClick != 0 && now - lastMiddleClick <= RESET_DOUBLE_CLICK_NANOS
                    && Math.abs(x - lastMiddleX) <= 4 && Math.abs(y - lastMiddleY) <= 4;
            if (doubleClick) {
                resetView();
                lastMiddleClick = 0;
                middlePanning = false;
                middleMoved = false;
            } else {
                middlePanning = true;
                middleMoved = false;
                lastMiddleClick = now;
                lastMiddleX = x;
                lastMiddleY = y;
            }
            return true;
        }
        if (inPreview(x, y) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            rotating = true;
            canvasMoved = false;
            pressX = x; pressY = y;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (inputBlocked()) return true;
        if (button == 0 && readingDrag) {
            reading.jump(readingStart + pressY - y);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && middlePanning) {
            if (Math.abs(x - lastMiddleX) >= 4 || Math.abs(y - lastMiddleY) >= 4) {
                middleMoved = true;
                lastMiddleClick = 0;
            }
            panX = Math.clamp(panX + (float) dx, -(width - 32) * .75F, (width - 32) * .75F);
            panY = Math.clamp(panY + (float) dy, -(height - 56) * .75F, (height - 56) * .75F);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && rotating) {
            canvasMoved |= Math.abs(x - pressX) > 4 || Math.abs(y - pressY) > 4;
            if (canvasMoved) angle += (float) dx;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && readingDrag) { readingDrag = false; return true; }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && middlePanning) {
            middlePanning = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && rotating) {
            rotating = false;
            if (inspecting() && !canvasMoved && inPreview(x, y)) {
                StructurePreviewRenderer.jigsawMarkers(reference, 16, 40, previewWidth(), previewHeight(),
                        angle, zoom, panX, panY).stream()
                        .filter(marker -> Math.hypot(marker.x() - x, marker.y() - y) <= 12)
                        .min(Comparator.comparingDouble((StructurePreviewRenderer.JigsawMarker marker) ->
                                Math.hypot(marker.x() - x, marker.y() - y)).thenComparing(
                                        Comparator.comparingDouble(StructurePreviewRenderer.JigsawMarker::depth).reversed()))
                        .ifPresent(marker -> {
                            selected = points.indexOf(marker.info());
                            reading.jump(0); init();
                        });
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (inReading(x, y)) { reading.wheel(vertical * 36); return true; }
        if (!inPreview(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        zoom = Math.clamp(zoom + (float) vertical * .1f, .25f, 3);
        return true;
    }
    @Override public void onClose() { closeTo(parent); }
}
