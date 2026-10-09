package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.*;

/** Scrollable cover gallery; filter options are derived from the authored quest facets. */
public final class LegendaryScreen extends AnimatedQuestScreen {
    private static final int CARD_W = 154, CARD_H = 278, GAP = 18;
    private final Screen parent;
    private final ElasticScroll cards = new ElasticScroll(), filters = new ElasticScroll();
    private final UiMotion panel = new UiMotion(false, System.nanoTime());
    private final Map<String, UiMotion> lifts = new HashMap<>();
    private final Map<String, String> selected = new LinkedHashMap<>();
    private List<QuestDefinition> quests = List.of(), visible = List.of();
    private Map<String, List<String>> options = Map.of();
    private String status = "";
    private boolean panelOpen, held, panelDrag, moved;
    private String pressedQuest = "", pendingQuest = "";
    private long pendingAt, frameTime, catalogRevision = -1, progressRevision = -1;
    private double startX, startY, startScroll;
    private int pressedControl;
    private ElasticScrollRail filterRail;

    public LegendaryScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.overview.category.legendary"));
        this.parent = parent;
    }

    public int visibleQuestCount() { return visible.size(); }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.legendary." + key);
    }
    private double scale() { return Math.max(.4, Math.min(1.3, Math.min(width / 900., height / 510.))); }
    private int top() { return 51; }
    private int bottom() { return height - 39; }
    private int cardY() { return (int) Math.max(15, ((bottom() - top()) / scale() - CARD_H) / 2); }
    private int cardX(int index) { return 24 + index * (CARD_W + GAP); }
    private int panelWidth() { return Math.min(275, Math.max(148, width * 2 / 5)); }
    private int panelX() { return (int) Math.round(-panelWidth() * (1 - panel.eased())); }
    private int filterBottom() { return height - 46; }
    private boolean admin() { return ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2)
            && minecraft.getSingleplayerServer() != null; }

    @Override protected void init() {
        clearWidgets();
        refresh();
        filterRail = addRenderableWidget(new ElasticScrollRail(panelWidth() - 8, 42,
                Math.max(8, filterBottom() - 42), true, filters, filterBottom() - 42));
        if (admin()) addRenderableWidget(new EditorIconButton(width - 66, 11, text("background.edit"),
                () -> minecraft.setScreen(new LegendaryBackgroundScreen(this)), ButtonStyleSheet.load(),
                "legendary-background-edit", "edit"));
    }
    @Override protected int animationHeaderBottom() { return top(); }
    @Override protected int animationFooterTop() { return bottom(); }

    private void refresh() {
        quests = ClientQuestDefinitions.all().stream().filter(q -> q.type().equals("legendary"))
                .filter(q -> admin() || ClientQuestProgress.view(q.id()) == null
                        || !ClientQuestProgress.view(q.id()).hidden()).toList();
        Map<String, LinkedHashSet<String>> groups = new LinkedHashMap<>();
        for (QuestDefinition quest : quests) quest.legendary().filters().forEach((group, values) ->
                groups.computeIfAbsent(group, ignored -> new LinkedHashSet<>()).addAll(values));
        Map<String, List<String>> next = new LinkedHashMap<>();
        groups.forEach((group, values) -> next.put(group, List.copyOf(values)));
        options = next;
        selected.entrySet().removeIf(entry -> !options.containsKey(entry.getKey())
                || !options.get(entry.getKey()).contains(entry.getValue()));
        applyFilter();
        catalogRevision = ClientQuestDefinitions.revision();
        progressRevision = ClientQuestProgress.revision();
    }
    private void applyFilter() {
        visible = quests.stream().filter(q -> selected.entrySet().stream().allMatch(entry ->
                q.legendary().filters().getOrDefault(entry.getKey(), List.of()).contains(entry.getValue())))
                .filter(q -> {
                    var progress = ClientQuestProgress.view(q.id());
                    return status.isBlank() || progress != null
                            && (status.equals("active") ? progress.unlocked() : progress.completed());
                }).toList();
        cards.limit(Math.max(0, (24 + visible.size() * (CARD_W + GAP) - GAP + 24)
                - width / scale()));
        cards.jump(0);
        lifts.keySet().retainAll(visible.stream().map(QuestDefinition::id).toList());
    }

    private int optionHeight() {
        int rows = 0;
        for (List<String> values : options.values()) rows += 1 + (values.size() + 1) / 2;
        return 61 + rows * 29 + 75;
    }
    private void updateLimits() {
        cards.limit(Math.max(0, 24 + visible.size() * (CARD_W + GAP) + 6 - width / scale()));
        filters.limit(Math.max(0, optionHeight() - filterBottom()));
    }
    private void advance() {
        long now = System.nanoTime();
        double dt = frameTime == 0 ? 0 : Math.clamp((now - frameTime) / 1e9, 0, .05);
        frameTime = now;
        updateLimits();
        cards.advance(dt, animationMillis() == 0);
        filters.advance(dt, animationMillis() == 0);
        panel.advance(now, animationMillis() == 0 ? 0 : 220);
        lifts.values().forEach(motion -> motion.advance(now, animationMillis() == 0 ? 0 : 140));
        if (!pendingQuest.isBlank() && now >= pendingAt && !inputBlocked()) {
            String id = pendingQuest;
            pendingQuest = "";
            closeTo(QuestScreen.task(this, id));
        }
    }
    @Override public void tick() {
        if (catalogRevision != ClientQuestDefinitions.revision()
                || progressRevision != ClientQuestProgress.revision()) refresh();
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        advance();
        backdrop(g, 0xFF34414A);
        String customBackground = ClientPreferences.get().legendaryBackground;
        ClientMediaManager.renderImageCover(g, customBackground.isBlank()
                ? ClientQuestProgress.overviewImage() : customBackground, 0, 0, width, height, 1);
        g.fill(0, 0, width, height, 0x5A202532);
        g.fill(0, 0, width, top(), 0xB51D2332);
        g.fill(0, bottom(), width, height, 0x54202732);
        JournalNavButton.drawIcon(g, "guide", 15, 12, 22, 0xFFF4D99D);
        g.drawString(font, title, 44, 18, 0xFFF4D99D, true);
        JournalNavButton.drawIcon(g, "close", width - 32, 12, 22, 0xFFF5EFE8);
        g.enableScissor(0, top(), width, bottom());
        g.pose().pushPose();
        g.pose().translate(0, top(), 0);
        g.pose().scale((float) scale(), (float) scale(), 1);
        g.pose().translate(-cards.position(), 0, 0);
        for (int i = 0; i < visible.size(); i++)
            drawCard(g, visible.get(i), i, mouseX, mouseY);
        g.pose().popPose();
        g.disableScissor();
        if (visible.isEmpty()) g.drawCenteredString(font,
                Component.translatable("screen.xero_quest_journal.legendary.empty"), width / 2, height / 2,
                0xFFF5EFE8);
        g.fill(14, height - 31, 101, height - 8, 0xFFF7F5ED);
        JournalNavButton.drawIcon(g, "choose", 20, height - 27, 16, 0xFF344357);
        g.drawString(font, Component.translatable("screen.xero_quest_journal.legendary.filter"),
                43, height - 23, 0xFF344357, false);
        if (panel.eased() > 0) {
            g.fill(0, 0, width, height, Math.round(128 * panel.eased()) << 24);
            drawPanel(g);
        }
        filterRail.setX(panelX() + panelWidth() - 8);
        filterRail.visible = panelOpen;
        super.renderContents(g, mouseX, mouseY, delta);
    }
    private void drawCard(GuiGraphics g, QuestDefinition quest, int index, int mouseX, int mouseY) {
        int x = cardX(index), y = cardY();
        if (x + CARD_W < cards.position() - 10 || x > cards.position() + width / scale() + 10) return;
        boolean hover = !held && !panelOpen && pendingQuest.isBlank()
                && quest.id().equals(cardAt(mouseX, mouseY));
        UiMotion lift = lifts.computeIfAbsent(quest.id(), ignored -> new UiMotion(false, System.nanoTime()));
        lift.target(hover, System.nanoTime(), animationMillis() == 0 ? 0 : 140);
        float amount = lift.eased();
        boolean pressed = held && quest.id().equals(pressedQuest) && !moved;
        float zoom = pressed ? .97F : 1 + amount * .035F;
        g.pose().pushPose();
        g.pose().translate(x + CARD_W / 2F, y + CARD_H / 2F - amount * 5, 0);
        g.pose().scale(zoom, zoom, 1);
        g.pose().translate(-x - CARD_W / 2F, -y - CARD_H / 2F, 0);
        g.fill(x + 3, y + 6, x + CARD_W + 4, y + CARD_H + 7, 0x78202631);
        g.fill(x, y, x + CARD_W, y + CARD_H, 0xFFF9F8F1);
        g.renderOutline(x, y, CARD_W, CARD_H, pressed ? 0xFFDBB16E : hover ? 0xFFFFFFFF : 0xFFD9D5C3);
        g.renderOutline(x + 2, y + 2, CARD_W - 4, CARD_H - 4, 0x99EEE1BB);
        String image = quest.legendary().image();
        if (image.isBlank()) image = quest.hotEvent().image();
        if (image.isBlank()) image = quest.icon();
        g.enableScissor((int) ((x - cards.position()) * scale()),
                top() + (int) (y * scale()),
                (int) ((x - cards.position() + CARD_W) * scale()),
                top() + (int) ((y + CARD_H - 32) * scale()));
        g.fill(x + 3, y + 3, x + CARD_W - 3, y + CARD_H - 32, 0xFF327D81);
        ClientMediaManager.renderImageCover(g, image, x + 3, y + 3, CARD_W - 6, CARD_H - 35, 1);
        g.disableScissor();
        g.fillGradient(x + 3, y + 3, x + CARD_W - 3, y + 50, 0xA3273440, 0x00273440);
        String heading = quest.legendary().subtitle();
        if (!heading.isBlank()) g.drawCenteredString(font,
                font.plainSubstrByWidth(heading, CARD_W - 22), x + CARD_W / 2, y + 16, 0xFFFFFFFF);
        g.fill(x + 3, y + CARD_H - 31, x + CARD_W - 3, y + CARD_H - 3, 0xFFFCFBF6);
        g.drawCenteredString(font, font.plainSubstrByWidth(QuestText.title(quest).getString(), CARD_W - 14),
                x + CARD_W / 2, y + CARD_H - 22, 0xFF887050);
        var progress = ClientQuestProgress.view(quest.id());
        if (progress != null && progress.completed())
            JournalNavButton.drawIcon(g, "mail_check", x + CARD_W - 28, y + CARD_H - 65, 24, 0xFF7CBF58);
        g.pose().popPose();
    }
    private void drawPanel(GuiGraphics g) {
        int x = panelX(), w = panelWidth();
        g.fill(x, 0, x + w, height, 0xF329394B);
        g.renderOutline(x, 0, w, height, 0x74465B6C);
        g.drawString(font, Component.translatable("screen.xero_quest_journal.legendary.filter"),
                x + 17, 18, 0xFFF6E0B1);
        g.enableScissor(Math.max(0, x), 42, Math.max(0, x + w), filterBottom());
        int y = 53 - (int) filters.position();
        for (var group : options.entrySet()) {
            g.drawString(font, font.plainSubstrByWidth(group.getKey(), w - 32),
                    x + 16, y, 0xFFE5E9ED);
            y += 22;
            List<String> values = group.getValue();
            for (int i = 0; i < values.size(); i++) {
                int bx = x + 16 + i % 2 * ((w - 38) / 2 + 5), by = y + i / 2 * 29;
                int bw = (w - 38) / 2;
                g.fill(bx, by, bx + bw, by + 23, 0x66344150);
                g.renderOutline(bx, by, bw, 23, 0x667F90A2);
                String value = values.get(i);
                drawFilterChoice(g, bx + 4, by + 5, selected.getOrDefault(group.getKey(), "").equals(value));
                g.drawString(font, font.plainSubstrByWidth(value, bw - 29),
                        bx + 21, by + 7, 0xFFF1F1F0, false);
            }
            y += (values.size() + 1) / 2 * 29 + 8;
        }
        g.drawString(font, Component.translatable("screen.xero_quest_journal.legendary.status"),
                x + 16, y, 0xFFE5E9ED);
        y += 22;
        for (int i = 0; i < 2; i++) {
            int bx = x + 16 + i * ((w - 38) / 2 + 5), bw = (w - 38) / 2;
            String value = i == 0 ? "active" : "completed";
            g.fill(bx, y, bx + bw, y + 23, 0x66344150);
            g.renderOutline(bx, y, bw, 23, 0x667F90A2);
            drawFilterChoice(g, bx + 4, y + 5, status.equals(value));
            g.drawString(font, Component.translatable("screen.xero_quest_journal.legendary." + value),
                    bx + 21, y + 7, 0xFFF1F1F0, false);
        }
        g.disableScissor();
        g.fill(x + 12, height - 32, x + w - 12, height - 7, 0xFFF5F4F0);
        g.drawCenteredString(font, Component.translatable("screen.xero_quest_journal.legendary.confirm"),
                x + w / 2, height - 24, 0xFF354255);
    }
    private static void drawFilterChoice(GuiGraphics graphics, int x, int y, boolean selected) {
        if (selected) {
            JournalNavButton.drawIcon(graphics, "mail_check", x, y, 13, 0xFFE6CB85);
        } else {
            graphics.renderOutline(x + 1, y + 1, 11, 11, 0xFFB7C0CC);
        }
    }
    private boolean panelHit(double x, double y) { return panelOpen && x >= panelX() && x < panelX() + panelWidth(); }
    private String cardAt(double x, double y) {
        if (panelHit(x, y) || y < top() || y >= bottom()) return "";
        double localX = x / scale() + cards.position(), localY = (y - top()) / scale();
        if (localY < cardY() || localY >= cardY() + CARD_H) return "";
        int index = (int) Math.floor((localX - 24) / (CARD_W + GAP));
        return index >= 0 && index < visible.size()
                && localX < cardX(index) + CARD_W ? visible.get(index).id() : "";
    }
    private void filterAt(double x, double y) {
        int cy = 53 - (int) filters.position();
        for (var group : options.entrySet()) {
            cy += 22;
            var values = group.getValue();
            for (int i = 0; i < values.size(); i++) {
                int bx = panelX() + 16 + i % 2 * ((panelWidth() - 38) / 2 + 5);
                int by = cy + i / 2 * 29;
                if (x >= bx && x < bx + (panelWidth() - 38) / 2 && y >= by && y < by + 23) {
                    String value = values.get(i);
                    if (value.equals(selected.get(group.getKey()))) selected.remove(group.getKey());
                    else selected.put(group.getKey(), value);
                    return;
                }
            }
            cy += (values.size() + 1) / 2 * 29 + 8;
        }
        cy += 22;
        for (int i = 0; i < 2; i++) {
            int bx = panelX() + 16 + i * ((panelWidth() - 38) / 2 + 5);
            if (x >= bx && x < bx + (panelWidth() - 38) / 2 && y >= cy && y < cy + 23) {
                String next = i == 0 ? "active" : "completed";
                status = status.equals(next) ? "" : next;
                return;
            }
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked() || !pendingQuest.isBlank()) return true;
        if ((!panelHit(x, y) || filterRail.isMouseOver(x, y)) && super.mouseClicked(x, y, button)) return true;
        if (button == 1 && admin()) {
            String id = cardAt(x, y);
            if (!id.isBlank()) { minecraft.setScreen(QuestScreen.legendaryProperties(this, id)); return true; }
        }
        if (button != 0) return false;
        held = true; moved = false; startX = x; startY = y; pressedQuest = "";
        if (x >= width - 38 && y < 43) { pressedControl = 1; return true; }
        if (y >= height - 34 && x < 110 && !panelOpen) { pressedControl = 2; return true; }
        if (panelHit(x, y)) {
            pressedControl = y >= height - 34 ? 3 : 4;
            panelDrag = pressedControl == 4;
            startScroll = filters.position();
            if (panelDrag) filters.begin();
            return true;
        }
        if (panelOpen) { pressedControl = 5; return true; }
        pressedControl = 0;
        pressedQuest = cardAt(x, y);
        startScroll = cards.position();
        cards.begin();
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (super.mouseDragged(x, y, button, dx, dy)) return true;
        if (button != 0 || !held || inputBlocked()) return false;
        moved |= Math.hypot(x - startX, y - startY) > 4;
        if (moved && panelDrag) filters.drag(startScroll + startY - y);
        else if (moved && pressedControl == 0) cards.drag(startScroll + (startX - x) / scale());
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (super.mouseReleased(x, y, button)) return true;
        if (button != 0 || !held) return false;
        held = false;
        boolean dragged = moved || Math.hypot(x - startX, y - startY) > 4;
        cards.release(dragged);
        filters.release(dragged);
        if (!dragged && !inputBlocked()) {
            if (pressedControl == 1 && x >= width - 38 && y < 43) onClose();
            else if (pressedControl == 2 && y >= height - 34 && x < 110) togglePanel();
            else if (pressedControl == 3 && panelHit(x, y) && y >= height - 34) {
                applyFilter(); togglePanel();
            } else if (pressedControl == 4 && panelHit(x, y) && y >= 42 && y < filterBottom())
                filterAt(x, y);
            else if (pressedControl == 5 && !panelHit(x, y)) togglePanel();
            else if (pressedControl == 0 && !pressedQuest.isBlank()
                    && pressedQuest.equals(cardAt(x, y))) {
                pendingQuest = pressedQuest;
                pendingAt = System.nanoTime() + (animationMillis() == 0 ? 0 : 150_000_000L);
            }
        }
        pressedQuest = "";
        panelDrag = false;
        return true;
    }
    private void togglePanel() {
        panelOpen = !panelOpen;
        panel.target(panelOpen, System.nanoTime(), animationMillis() == 0 ? 0 : 220);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (panelHit(x, y)) filters.by(-(horizontal != 0 ? horizontal : vertical) * 46);
        else cards.by(-(horizontal != 0 ? horizontal : vertical) * 80);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            cards.by(key == GLFW.GLFW_KEY_LEFT ? -110 : 110);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public void removed() { held = panelDrag = false; frameTime = 0; super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
