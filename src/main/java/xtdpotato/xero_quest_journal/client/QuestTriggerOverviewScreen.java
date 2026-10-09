package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;

import java.util.List;
import java.util.function.Consumer;

/** Read-only task-wide index; editing always opens the selected control's graph. */
final class QuestTriggerOverviewScreen extends AnimatedQuestScreen {
    record Entry(String elementId, String controlId, TriggerGraph graph) {}

    private final Screen parent;
    private final List<Entry> entries;
    private final Consumer<String> edit;
    private int selected;
    private final SmoothScroll listScroll = new SmoothScroll();
    private boolean listDragging, listMoved;
    private double startY, startScroll;
    private double panX, panY, zoom = 1;
    private long lastClick;
    private int lastRow = -1;
    private boolean panning;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();

    QuestTriggerOverviewScreen(Screen parent, List<Entry> entries, Consumer<String> edit) {
        super(Component.translatable("screen.xero_quest_journal.graph.overview"));
        this.parent = parent;
        this.entries = List.copyOf(entries);
        this.edit = edit;
    }

    private int divider() { return Math.min(180, Math.max(106, width / 3)); }

    @Override protected void init() {
        clearWidgets();
        addRenderableWidget(new EditorIconButton(width - 36, 6,
                Component.translatable("gui.done"), this::onClose, styles, "graph-overview-close", "close"));
        listScroll.limit(entries.size() * 25 - (height - 54));
        addRenderableWidget(new ScrollRail(divider() - 7, 38, height - 50, listScroll));
    }

    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, TriggerNodeStyle.PANEL);
        int divider = divider();
        listScroll.advance();
        graphics.fill(0, 0, width, 33, 0xFF19191D);
        graphics.fill(0, 33, width, 34, TriggerNodeStyle.PANEL_BORDER);
        graphics.fill(8, 36, divider, height - 12, 0xFF222226);
        graphics.fill(divider + 2, 36, width - 8, height - 12, TriggerNodeStyle.CANVAS);
        MailDecor.diamond(graphics, 15, 17, 3, TriggerNodeStyle.GOLD);
        graphics.drawString(font, title, 24, 13, 0xFFE8E8EC, false);
        graphics.enableScissor(8, 38, divider, height - 12);
        for (int i = listScroll.pixels() / 25; i < entries.size(); i++) {
            int y = 42 + i * 25 - listScroll.pixels();
            if (y >= height - 12) break;
            Entry entry = entries.get(i);
            boolean hovered = mouseX >= 12 && mouseX < divider && mouseY >= y && mouseY < y + 22;
            ControlAppearance.draw(graphics, 12, y, divider - 22, 22, "rounded", 4,
                    i == selected ? 0xFF2F2F36 : hovered ? 0xFF2A2A30 : 0xFF26262B,
                    i == selected ? TriggerNodeStyle.GOLD : TriggerNodeStyle.VALUE_BORDER, 1);
            MailDecor.diamond(graphics, 19, y + 11, 2, i == selected ? TriggerNodeStyle.GOLD : 0xFF6E6E76);
            graphics.drawString(font, font.plainSubstrByWidth(entry.controlId(), divider - 64),
                    26, y + 7, 0xFFE8E8EC, false);
            String count = Integer.toString(entry.graph().nodes().size());
            ControlAppearance.draw(graphics, divider - 22 - font.width(count) - 6, y + 5, font.width(count) + 8, 12,
                    "rounded", 4, TriggerNodeStyle.VALUE_BOX, 0, 0);
            graphics.drawString(font, count, divider - 18 - font.width(count) - 2, y + 7, 0xFFD3DC5A, false);
        }
        graphics.disableScissor();
        if (!entries.isEmpty()) {
            TriggerGraph graph = entries.get(selected).graph();
            int left = divider + 3, top = 37, right = width - 9, bottom = height - 13;
            graphics.enableScissor(left, top, right, bottom);
            int originX = divider + 20, originY = 68;
            TriggerNodeStyle.canvas(graphics, left, top, right, bottom, originX + panX, originY + panY, zoom, true);
            for (var edge : graph.edges()) {
                var from = graph.nodes().stream().filter(node -> node.id().equals(edge.from())).findFirst().orElse(null);
                var to = graph.nodes().stream().filter(node -> node.id().equals(edge.to())).findFirst().orElse(null);
                if (from != null && to != null) {
                    double x1 = originX + panX + (from.x() + TriggerNodeLayout.measure(from, font).width()) * zoom;
                    double y1 = originY + panY + (from.y() + TriggerNodeStyle.portOffset(edge.port())) * zoom;
                    double x2 = originX + panX + to.x() * zoom;
                    double y2 = originY + panY + (to.y() + 32) * zoom;
                    TriggerNodeStyle.wire(graphics, x1, y1, x2, y2, TriggerNodeStyle.portColor(edge.port()), 2,
                            false, left, top, right, bottom);
                }
            }
            var linked = new java.util.HashSet<String>();
            for (var edge : graph.edges()) {
                linked.add(edge.from() + "/" + edge.port());
                linked.add(edge.to() + "/in");
            }
            for (var node : graph.nodes()) {
                int x = (int) (originX + panX + node.x() * zoom);
                int y = (int) (originY + panY + node.y() * zoom);
                var layout = TriggerNodeLayout.measure(node, font);
                int w = Math.max(1, (int) (layout.width() * zoom)), h = Math.max(1, (int) (layout.height() * zoom));
                TriggerNodeStyle.frame(graphics, node, x, y, w, h, (int) Math.round(layout.headerHeight() * zoom),
                        false, false, false, false);
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale((float) zoom, (float) zoom, 1);
                TriggerNodeStyle.glyph(graphics, node, 6, (layout.headerHeight() - TriggerNodeLayout.GLYPH_SIZE) / 2,
                        TriggerNodeLayout.GLYPH_SIZE);
                layout.render(graphics, font);
                graphics.pose().popPose();
                // Execution pins, as in the editor (read-only here).
                if (!List.of("event", "context").contains(node.type()))
                    TriggerNodeStyle.port(graphics, x, y + 32 * zoom, TriggerNodeStyle.PORT_INPUT,
                            linked.contains(node.id() + "/in"), false);
                List<String> outputs = TriggerGraph.branching(node.type()) ? List.of("true", "false")
                        : node.type().equals("event") || node.type().equals("delay") ? List.of("next") : List.of();
                for (String output : outputs)
                    TriggerNodeStyle.port(graphics, x + w, y + TriggerNodeStyle.portOffset(output) * zoom,
                            TriggerNodeStyle.portColor(output), linked.contains(node.id() + "/" + output), false);
            }
            graphics.disableScissor();
        }
        super.renderContents(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 || y < 38 || y >= height - 12) return false;
        if (x < divider()) {
            listDragging = true;
            listMoved = false;
            startY = y; startScroll = listScroll.position();
            listScroll.freeze();
            return true;
        }
        panning = true;
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (listDragging && button == 0) {
            listMoved |= Math.abs(y - startY) >= 4;
            if (listMoved) listScroll.jump(startScroll + startY - y);
            return true;
        }
        if (panning && button == 0) { panX += dx; panY += dy; return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && listDragging) {
            listDragging = false;
            int index = (int) ((listScroll.position() + y - 42) / 25);
            if (!listMoved && x >= 8 && x < divider() && y >= 42 && y < height - 12 && index >= 0 && index < entries.size()) {
                long now = System.nanoTime();
                if (lastRow == index && now - lastClick < 400_000_000L) edit.accept(entries.get(index).elementId());
                else {
                    selected = index;
                    lastRow = index; lastClick = now;
                    panX = panY = 0; zoom = 1;
                }
            }
            return true;
        }
        if (button == 0 && panning) { panning = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x < divider()) {
            listScroll.wheel(vertical * 48);
        } else {
            double old = zoom;
            zoom = Math.clamp(zoom * Math.pow(1.12, vertical), .3, 2.5);
            panX = x - divider() - 20 - (x - divider() - 20 - panX) / old * zoom;
            panY = y - 68 - (y - 68 - panY) / old * zoom;
        }
        return true;
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
