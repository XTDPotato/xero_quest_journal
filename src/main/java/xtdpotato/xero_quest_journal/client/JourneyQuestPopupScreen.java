package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

/** Click-opened journey card details. Hovering a card never shows this content. */
final class JourneyQuestPopupScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final String questId;
    private final QuestDefinition quest;
    private ButtonStyleSheet styles;
    private final SmoothScroll scroll = new SmoothScroll();

    JourneyQuestPopupScreen(Screen parent, String questId) {
        super(Component.translatable("screen.xero_quest_journal.journey.quest_popup"));
        this.parent = parent;
        this.questId = questId;
        this.quest = ClientQuestDefinitions.find(questId);
    }

    private int panelWidth() { return Math.min(560, width - 28); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelHeight() { return Math.min(340, height - 28); }
    private int panelTop() { return (height - panelHeight()) / 2; }

    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() {
        return viewport(panelLeft() - 2, panelTop() - 2,
                panelLeft() + panelWidth() + 2, panelTop() + panelHeight() + 2);
    }
    @Override protected void init() {
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int x = panelLeft(), y = panelTop(), w = panelWidth();
        addRenderableWidget(new EditorIconButton(x + w - 34, y + 10,
                Component.translatable("screen.xero_quest_journal.close"), this::onClose, styles,
                "journey-quest-popup-close", "close"));
        addRenderableWidget(new JournalNavButton(x + 18, y + panelHeight() - 38, w - 36, 24,
                Component.translatable("screen.xero_quest_journal.journey.go_to_task"),
                ignored -> closeTo(QuestScreen.task(parent, questId)), styles,
                "journey-quest-popup-open", JournalNavButton.Kind.ACTION, "overview")).active = quest != null;
    }
    @Override protected void renderModalBackground(GuiGraphics graphics, float delta) {
        if (parent instanceof JourneyScreen journey) journey.render(graphics, -10000, -10000, delta);
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = panelLeft(), y = panelTop(), w = panelWidth();
        int h = panelHeight();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFFE0C99A);
        g.fill(x, y, x + w, y + h, 0xFF27343D);
        if (quest == null) {
            g.drawString(font, Component.translatable("screen.xero_quest_journal.journey.quest_missing"),
                    x + 18, y + 24, 0xFFFFA6A6);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(QuestText.title(quest).getString(), Math.max(1, w - 64)),
                x + 18, y + 20, 0xFFF5D99F, true);
        g.drawString(font, QuestText.type(quest), x + 18, y + 39, 0xFFB8C5D0, false);
        var content = Component.empty();
        QuestText.tooltip(quest).getSiblings().forEach(content::append);
        var lines = font.split(content, Math.max(1, w - 42));
        scroll.limit(lines.size() * (font.lineHeight + 3) - Math.max(1, h - 120));
        scroll.advance();
        int lineY = y + 64 - scroll.pixels();
        g.enableScissor(x + 14, y + 60, x + w - 14, y + h - 50);
        for (var line : lines) {
            if (lineY >= y + 50 && lineY < y + h - 50)
                g.drawString(font, line, x + 18, lineY, 0xFFE8EDF0, false);
            lineY += font.lineHeight + 3;
        }
        g.disableScissor();
        super.renderContents(g, mouseX, mouseY, delta);
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inputBlocked()) return true;
        scroll.wheel(v * 24); return true;
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
