package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;

import java.util.ArrayList;
import java.util.List;

/** Two-page chronicle: a smooth card index on the left and independently readable records on the right. */
public final class AnecdoteScreen extends AnimatedQuestScreen {
    private record Rect(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private record RecordRow(AnecdoteDefinition.Record record, Rect bounds, int bodyBottom) {}

    private final Screen parent;
    private final SmoothScroll cardScroll = new SmoothScroll();
    private final SmoothScroll recordScroll = new SmoothScroll();
    private List<AnecdoteDefinition> visibleEntries = List.of();
    private List<Rect> cardBounds = List.of();
    private List<RecordRow> recordRows = List.of();
    private String selectedId = "";
    private String pressedCard = "";
    private String pressedRecord = "";
    private int pressedButton = -1;
    private double pressX, pressY, dragY;
    private boolean dragCandidate, dragging;
    private long frameNanos;
    private final UiMotion pageOpen = new UiMotion(false, System.nanoTime());
    private boolean editActionsVisible;
    private Rect closeBounds, rewardBounds, addBounds, editBounds, recordAddBounds;
    private int bookLeft, bookTop, bookWidth, bookHeight, leftWidth, rightLeft, rightWidth;
    private int gridTop, gridBottom, cardWidth, cardHeight, cardColumns;
    private int recordsTop, recordsBottom;

    public AnecdoteScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.anecdote.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        AnecdoteClientState.request();
        layout();
    }

    private void layout() {
        bookWidth = Math.min(width - 32, Math.max(360, Math.round(width * .86F)));
        bookHeight = Math.min(height - 44, Math.max(240, Math.round(height * .77F)));
        bookLeft = (width - bookWidth) / 2;
        bookTop = (height - bookHeight) / 2 + 8;
        leftWidth = Math.round(bookWidth * .49F);
        rightLeft = bookLeft + leftWidth + 8;
        rightWidth = bookWidth - leftWidth - 8;
        int pageInset = Math.max(12, Math.min(24, bookWidth / 40));
        gridTop = bookTop + 28;
        gridBottom = bookTop + bookHeight - 18;
        cardColumns = width < 500 ? 2 : 4;
        int gridWidth = Math.max(1, leftWidth - pageInset * 2 - (cardColumns - 1) * 7);
        cardWidth = Math.max(36, gridWidth / cardColumns);
        cardHeight = Math.round(cardWidth * 1.25F);
        recordsTop = bookTop + 68;
        recordsBottom = bookTop + bookHeight - 22;
        closeBounds = new Rect(width - 48, 10, 36, 32);
        rewardBounds = new Rect(width - 188, 12, 128, 28);
        addBounds = new Rect(bookLeft + pageInset, bookTop + 8, 24, 18);
        editBounds = new Rect(width - 220, 46, 82, 22);
        recordAddBounds = new Rect(rightLeft + rightWidth - 38, bookTop + 43, 24, 22);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (width != lastWidth || height != lastHeight) layout();
        lastWidth = width;
        lastHeight = height;
        long now = System.nanoTime();
        double seconds = frameNanos == 0 ? 1 / 60.0 : Math.min(.1, (now - frameNanos) / 1e9);
        frameNanos = now;
        pageOpen.target(true, now, ClientPreferences.get().reducedMotion ? 0 : 260);
        pageOpen.advance(now, ClientPreferences.get().reducedMotion ? 0 : 260);
        cardScroll.advance(seconds);
        recordScroll.advance(seconds);
        refreshDefinitions();
        drawBackdrop(graphics);
        drawHeader(graphics, mouseX, mouseY);
        float open = pageOpen.eased();
        graphics.pose().pushPose();
        graphics.pose().translate(bookLeft + bookWidth / 2F, 0, 0);
        graphics.pose().scale(.06F + .94F * open, 1, 1);
        graphics.pose().translate(-(bookLeft + bookWidth / 2F), 0, 0);
        drawBook(graphics);
        drawCards(graphics, mouseX, mouseY);
        drawRecords(graphics, mouseX, mouseY);
        drawNewEntryMarkers(graphics);
        graphics.pose().popPose();
        super.renderContents(graphics, mouseX, mouseY, delta);
    }

    private int lastWidth = -1;
    private int lastHeight = -1;

    private void refreshDefinitions() {
        var snapshot = AnecdoteClientState.snapshot();
        boolean canEdit = ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2);
        visibleEntries = snapshot.entries().stream()
                .filter(entry -> AnecdoteClientState.discovered(entry.id()) || !entry.hideWhenLocked() || canEdit)
                .toList();
        if (visibleEntries.stream().noneMatch(entry -> entry.id().equals(selectedId))) {
            selectedId = visibleEntries.stream().filter(entry -> AnecdoteClientState.discovered(entry.id()))
                    .findFirst().or(() -> visibleEntries.stream().findFirst())
                    .map(AnecdoteDefinition::id).orElse("");
        }
        int rows = (visibleEntries.size() + cardColumns - 1) / cardColumns;
        cardScroll.limit(Math.max(0, rows * (cardHeight + 8) - (gridBottom - gridTop)));
        var selected = selected();
        recordScroll.limit(selected == null ? 0
                : Math.max(0, selected.records().size() * 72.0 - (recordsBottom - recordsTop)));
    }

    private AnecdoteDefinition selected() {
        return visibleEntries.stream().filter(entry -> entry.id().equals(selectedId)).findFirst().orElse(null);
    }

    private void drawBackdrop(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xB51A2B43);
        g.fill(0, 0, width, Math.max(36, bookTop - 20), 0x18253D5B);
        g.fill(0, height - Math.max(36, bookTop - 20), width, height, 0x18253D5B);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        JournalNavButton.drawIcon(g, "overview", 24, 12, 22, 0xFFE8D099);
        g.drawString(font, text("title"), 52, 18, 0xFFF1D89B, false);
        drawAction(g, rewardBounds, text("rewards"), "gift", mouseX, mouseY, false);
        drawAction(g, closeBounds, Component.empty(), "close", mouseX, mouseY, false);
        if (ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2))
            drawAction(g, addBounds, Component.literal("+"), "", mouseX, mouseY, false);
        var current = selected();
        if (current != null && ClientEditMode.enabled() && minecraft.player != null
                && minecraft.player.hasPermissions(2))
            drawAction(g, editBounds, text("edit"), "edit", mouseX, mouseY, false);
    }

    private void drawBook(GuiGraphics g) {
        int outer = 0xFFB9A77E, pale = 0xFFEEE4D0;
        g.fill(bookLeft - 6, bookTop - 5, bookLeft + bookWidth + 6, bookTop + bookHeight + 8, 0xFF354A68);
        g.fill(bookLeft - 3, bookTop - 3, bookLeft + bookWidth + 3, bookTop + bookHeight + 5, outer);
        g.renderOutline(bookLeft - 3, bookTop - 3, bookWidth + 6, bookHeight + 8, 0xFFD9C795);
        g.fill(bookLeft, bookTop, bookLeft + leftWidth, bookTop + bookHeight, 0xFFF3EAD9);
        g.fill(rightLeft, bookTop, rightLeft + rightWidth, bookTop + bookHeight, 0xFFF5F0E8);
        g.renderOutline(bookLeft + 1, bookTop + 1, leftWidth - 2, bookHeight - 2, pale);
        g.renderOutline(rightLeft + 1, bookTop + 1, rightWidth - 2, bookHeight - 2, pale);
        g.fill(bookLeft + leftWidth - 3, bookTop + 2, bookLeft + leftWidth + 5, bookTop + bookHeight - 2,
                0x33534A39);
        MailDecor.divider(g, rightLeft + 20, rightLeft + rightWidth - 20, bookTop + 48, 0xFFE2D4B7);
        MailDecor.diamond(g, bookLeft + bookWidth / 2, bookTop + bookHeight + 10, 7, 0xFFD8B365);
    }

    private void drawCards(GuiGraphics g, int mouseX, int mouseY) {
        int pageInset = Math.max(12, Math.min(24, bookWidth / 40));
        int contentLeft = bookLeft + pageInset;
        int gap = 7, stepX = cardWidth + gap, stepY = cardHeight + 8;
        var bounds = new ArrayList<Rect>(visibleEntries.size());
        g.enableScissor(bookLeft + 4, gridTop, bookLeft + leftWidth - 4, gridBottom);
        for (int index = 0; index < visibleEntries.size(); index++) {
            int col = index % cardColumns, row = index / cardColumns;
            int x = contentLeft + col * stepX;
            int y = gridTop + row * stepY - cardScroll.pixels();
            Rect rect = new Rect(x, y, cardWidth, cardHeight);
            bounds.add(rect);
            if (y + cardHeight < gridTop || y > gridBottom) continue;
            AnecdoteDefinition entry = visibleEntries.get(index);
            boolean discovered = AnecdoteClientState.discovered(entry.id());
            boolean hovered = rect.contains(mouseX, mouseY);
            boolean held = pressedButton == 0 && entry.id().equals(pressedCard);
            float factor = held ? .95F : hovered ? 1.05F : 1F;
            g.pose().pushPose();
            g.pose().translate(x + cardWidth / 2F, y + cardHeight / 2F, 0);
            g.pose().scale(factor, factor, 1);
            g.pose().translate(-(x + cardWidth / 2F), -(y + cardHeight / 2F), 0);
            drawCard(g, entry, rect, discovered, hovered || entry.id().equals(selectedId));
            g.pose().popPose();
        }
        g.disableScissor();
        cardBounds = List.copyOf(bounds);
        if (visibleEntries.isEmpty()) {
            centered(g, text(ClientEditMode.enabled() ? "empty_author" : "empty"), bookLeft + leftWidth / 2,
                    bookTop + bookHeight / 2, 0xFF81775F);
        }
    }

    private void drawCard(GuiGraphics g, AnecdoteDefinition entry, Rect r, boolean discovered, boolean highlighted) {
        int fill = discovered ? 0xFFF8F0DF : 0xFFD9D0BE;
        g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), fill);
        int outline = entry.id().equals(selectedId) ? 0xFFE9A347 : highlighted ? 0xFFC9934D : 0xFFC4B28D;
        int border = entry.id().equals(selectedId) ? 3 : 1;
        for (int i = 0; i < border; i++)
            g.renderOutline(r.x() + i, r.y() + i, r.width() - i * 2, r.height() - i * 2, outline);
        int footerHeight = Math.max(22, font.lineHeight + 10);
        int iconSize = Math.min(r.width() - 12, r.height() - footerHeight - 8);
        int iconX = r.x() + (r.width() - iconSize) / 2;
        int iconY = r.y() + 5;
        if (!entry.icon().isBlank()) QuestIcons.render(g, entry.icon(), iconX, iconY, iconSize, iconSize,
                discovered ? 1 : .45F);
        if (!discovered) g.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x5531383E);
        int footerTop = r.y() + r.height() - footerHeight;
        g.fill(r.x() + 4, footerTop, r.x() + r.width() - 4, footerTop + 1, 0x55B9A77E);
        String label = entry.cardText();
        float scale = Math.min(1F, (r.width() - 8F) / Math.max(1, font.width(label)));
        MailDecor.centered(g, font, label, r.x() + r.width() / 2,
                footerTop + (footerHeight - Math.round(font.lineHeight * scale)) / 2,
                scale, discovered ? 0xFF4F5664 : 0xFF827D70);
        if (!discovered) {
            drawLock(g, r.x() + r.width() - 14, r.y() + 5);
        } else if (AnecdoteClientState.unread(entry.id())) {
            drawNewBadge(g, r.x() + r.width() - 20, r.y() - 3);
        }
    }

    private void drawRecords(GuiGraphics g, int mouseX, int mouseY) {
        AnecdoteDefinition entry = selected();
        if (entry == null || !AnecdoteClientState.discovered(entry.id())) {
            centered(g, text("undiscovered"), rightLeft + rightWidth / 2, bookTop + bookHeight / 2,
                    0xFF9A907C);
            recordRows = List.of();
            return;
        }
        String heading = entry.heading().isBlank() ? entry.title() : entry.heading();
        centered(g, Component.literal(text("heading_prefix").getString() + heading),
                rightLeft + rightWidth / 2, bookTop + 17, 0xFF50556A);
        int x = rightLeft + 22;
        int rowWidth = rightWidth - 44;
        int y = recordsTop - recordScroll.pixels();
        var rows = new ArrayList<RecordRow>();
        g.enableScissor(rightLeft + 5, recordsTop, rightLeft + rightWidth - 5, recordsBottom);
        for (var record : entry.records()) {
            var titleLines = font.split(Component.literal(record.title()), rowWidth - 32);
            int titleHeight = Math.max(24, titleLines.size() * font.lineHeight + 12);
            var bodyLines = font.split(Component.literal(record.body()), rowWidth - 24);
            int bodyHeight = Math.max(18, bodyLines.size() * (font.lineHeight + 2) + 12);
            int totalHeight = titleHeight + bodyHeight;
            Rect bounds = new Rect(x, y, rowWidth, totalHeight);
            rows.add(new RecordRow(record, bounds, y + titleHeight + bodyHeight));
            boolean hover = bounds.contains(mouseX, mouseY);
            boolean held = pressedButton == 0 && record.id().equals(pressedRecord);
            boolean completed = AnecdoteClientState.snapshot().completed().stream()
                    .anyMatch(value -> value.entryId().equals(entry.id()) && value.recordId().equals(record.id()));
            float factor = held ? .95F : hover ? 1.05F : 1;
            g.pose().pushPose();
            g.pose().translate(x + rowWidth / 2F, y + totalHeight / 2F, 0);
            g.pose().scale(factor, factor, 1);
            g.pose().translate(-(x + rowWidth / 2F), -(y + totalHeight / 2F), 0);
            g.fill(x, y, x + rowWidth, y + totalHeight, 0xFFF8F2E7);
            g.fill(x, y, x + rowWidth, y + titleHeight, hover ? 0xFFFFE6B6 : 0xFFE7D4AF);
            g.renderOutline(x, y, rowWidth, totalHeight, hover ? 0xFFE7A244 : 0xFFD7BF8E);
            for (int line = 0; line < titleLines.size(); line++)
                g.drawString(font, titleLines.get(line), x + 12, y + 6 + line * font.lineHeight,
                        0xFF625B56, false);
            if (completed)
                JournalNavButton.drawIcon(g, "mail_check", x + rowWidth - 22, y + 5, 14, 0xFFE1AD4F);
            for (int line = 0; line < bodyLines.size(); line++)
                g.drawString(font, bodyLines.get(line), x + 12,
                        y + titleHeight + 6 + line * (font.lineHeight + 2), 0xFF555A66, false);
            g.pose().popPose();
            y += totalHeight + 8;
        }
        g.disableScissor();
        recordRows = List.copyOf(rows);
        if (entry.records().isEmpty())
            centered(g, text(ClientEditMode.enabled() ? "records_empty_author" : "records_empty"),
                    rightLeft + rightWidth / 2, recordsTop + 18, 0xFF9A907C);
        if (ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2))
            drawAction(g, recordAddBounds, Component.literal("+"), "", mouseX, mouseY, false);
    }

    private void drawNewEntryMarkers(GuiGraphics g) {
        if (Math.abs(cardScroll.target() - cardScroll.position()) > 1) return;
        double scroll = cardScroll.position();
        boolean above = false, below = false;
        for (int index = 0; index < visibleEntries.size(); index++) {
            if (!AnecdoteClientState.unread(visibleEntries.get(index).id())) continue;
            int row = index / cardColumns;
            double top = gridTop + row * (cardHeight + 8) - scroll;
            if (top + cardHeight < gridTop) above = true;
            else if (top > gridBottom) below = true;
        }
        if (above) drawEdgeMarker(g, bookLeft + leftWidth / 2, gridTop + 2, true);
        if (below) drawEdgeMarker(g, bookLeft + leftWidth / 2, gridBottom - 14, false);
    }

    private void drawEdgeMarker(GuiGraphics g, int cx, int y, boolean upper) {
        int cy = y + 6;
        MailDecor.circle(g, cx - 9, cy - 9, 18, 0xFFF0AC35);
        g.drawString(font, "!", cx - font.width("!") / 2, cy - font.lineHeight / 2, 0xFFFFFFFF, true);
        int tipY = upper ? cy - 13 : cy + 13;
        MailDecor.diamond(g, cx, tipY, 4, 0xFFF6E7C7);
    }

    private static void drawNewBadge(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 21, y + 20, 0xFFE5A92F);
        g.renderOutline(x, y, 21, 20, 0xFFFFE9A9);
        g.drawString(Minecraft.getInstance().font, "新", x + 3, y + 3, 0xFFFFFFFF, false);
    }

    private static void drawLock(GuiGraphics g, int x, int y) {
        MailDecor.circle(g, x - 2, y - 1, 14, 0xD4A67D32);
        g.drawString(Minecraft.getInstance().font, "?", x + 2, y + 2, 0xFFFFFFFF, false);
    }

    private void drawAction(GuiGraphics g, Rect bounds, Component label, String icon,
                            int mouseX, int mouseY, boolean disabled) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int color = disabled ? 0x66695E4E : hovered ? 0xFFE6D4AC : 0xE9F2E9DD;
        g.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), color);
        g.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                hovered ? 0xFFD49A48 : 0xFFBBA77D);
        JournalNavButton.drawIcon(g, icon, bounds.x() + 3, bounds.y() + 3,
                bounds.height() - 6, disabled ? 0xFF8A857A : 0xFF6F6047);
        if (!label.getString().isEmpty()) g.drawString(font, label, bounds.x() + bounds.height(),
                bounds.y() + (bounds.height() - font.lineHeight) / 2, 0xFF665B4B, false);
    }

    private Component text(String key) { return Component.translatable("screen.xero_quest_journal.anecdote." + key); }
    private void centered(GuiGraphics g, Component value, int center, int y, int color) {
        g.drawString(font, value, center - font.width(value) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (closeBounds.contains(mouseX, mouseY) && button == 0) { onClose(); return true; }
        if (rewardBounds.contains(mouseX, mouseY) && button == 0) {
            minecraft.setScreen(new AnecdoteRewardsScreen(this));
            return true;
        }
        boolean canEdit = ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2);
        if (canEdit && addBounds.contains(mouseX, mouseY) && button == 0) {
            var entry = new AnecdoteDefinition(AnecdoteClientState.nextId(), "新逸闻", "新", "", "", List.of(),
                    false, false, AnecdoteClientState.snapshot().entries().size());
            minecraft.setScreen(new AnecdoteEntryEditorScreen(this, entry, true));
            return true;
        }
        if (canEdit && editBounds.contains(mouseX, mouseY) && button == 0 && selected() != null) {
            minecraft.setScreen(new AnecdoteEntryEditorScreen(this, selected(), false));
            return true;
        }
        boolean overGrid = mouseX >= bookLeft && mouseX < bookLeft + leftWidth
                && mouseY >= gridTop && mouseY < gridBottom;
        if (overGrid) {
            for (int i = 0; i < cardBounds.size(); i++) {
                Rect bounds = cardBounds.get(i);
                var entry = visibleEntries.get(i);
                if (!bounds.contains(mouseX, mouseY)) continue;
                if (button == 1 && canEdit) {
                    minecraft.setScreen(new AnecdoteEntryEditorScreen(this, entry, false));
                    return true;
                }
                if (button == 0) {
                    pressedButton = button;
                    pressedCard = entry.id();
                    pressedRecord = "";
                    pressX = mouseX;
                    pressY = dragY = mouseY;
                    dragCandidate = true;
                    dragging = false;
                    return true;
                }
            }
            if (button == 0) {
                pressedButton = button;
                pressedCard = "";
                pressX = mouseX;
                pressY = dragY = mouseY;
                dragCandidate = true;
                return true;
            }
        }
        boolean overRecords = mouseX >= rightLeft && mouseX < rightLeft + rightWidth
                && mouseY >= recordsTop && mouseY < recordsBottom;
        if (overRecords) {
            for (var row : recordRows) {
                if (!row.bounds().contains(mouseX, mouseY)) continue;
                if (button == 1 && canEdit) {
                    var entry = selected();
                    if (entry != null) minecraft.setScreen(new AnecdoteRecordEditorScreen(this, entry, row.record(),
                            false, AnecdoteClientState::save));
                    return true;
                }
                if (button == 0) {
                    pressedButton = button;
                    pressedRecord = row.record().id();
                    pressedCard = "";
                    pressX = mouseX;
                    pressY = dragY = mouseY;
                    dragCandidate = true;
                    dragging = false;
                    return true;
                }
            }
            if (button == 0 && canEdit && recordAddBounds.contains(mouseX, mouseY)) {
                var entry = selected();
                if (entry != null) {
                    var record = new AnecdoteDefinition.Record(AnecdoteEntryEditorScreen.nextRecordId(entry),
                            "新记录", "");
                    minecraft.setScreen(new AnecdoteRecordEditorScreen(this, entry, record, true,
                            AnecdoteClientState::save));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!dragCandidate || button != pressedButton) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        if (Math.abs(mouseY - pressY) > 4) dragging = true;
        if (dragging) {
            if (pressX < bookLeft + leftWidth) cardScroll.wheel(-(mouseY - dragY));
            else recordScroll.wheel(-(mouseY - dragY));
            dragY = mouseY;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != pressedButton || !dragCandidate) return super.mouseReleased(mouseX, mouseY, button);
        String card = pressedCard;
        String record = pressedRecord;
        pressedButton = -1;
        pressedCard = pressedRecord = "";
        dragCandidate = false;
        if (dragging) {
            dragging = false;
            return true;
        }
        if (button == 0 && !card.isBlank()) {
            for (int i = 0; i < cardBounds.size(); i++) {
                if (visibleEntries.get(i).id().equals(card) && cardBounds.get(i).contains(mouseX, mouseY)) {
                    selectedId = card;
                    recordScroll.jump(0);
                    AnecdoteClientState.markRead(card);
                    return true;
                }
            }
        }
        if (button == 0 && !record.isBlank()) {
            for (var row : recordRows)
                if (row.record().id().equals(record) && row.bounds().contains(mouseX, mouseY)) return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= bookLeft && mouseX < bookLeft + leftWidth && mouseY >= gridTop && mouseY < gridBottom) {
            cardScroll.wheel(vertical * 32);
            return true;
        }
        if (mouseX >= rightLeft && mouseX < rightLeft + rightWidth
                && mouseY >= recordsTop && mouseY < recordsBottom) {
            recordScroll.wheel(vertical * 32);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose() { closeBookTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
