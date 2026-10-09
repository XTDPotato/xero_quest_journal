package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestUiTheme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Authoring surface for the server's persistent theme defaults. */
public final class QuestUiThemeScreen extends AnimatedQuestScreen {
    private static final List<String> KEYS = QuestUiTheme.KEYS;
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final Map<String, String> draft = new LinkedHashMap<>();
    private final List<EditBox> fields = new ArrayList<>();
    private int page;
    private CompletableFuture<String> pending;
    private String message = "";

    public QuestUiThemeScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.theme.title"));
        this.parent = parent;
        draft.putAll(ClientQuestProgress.uiTheme().colors());
    }

    private int perPage() { return Math.max(2, Math.min(9, (height - 118) / 27)); }
    private int pageCount() { return (KEYS.size() + perPage() - 1) / perPage(); }
    private int panelWidth() { return Math.min(370, width - 24); }
    private int panelX() { return (width - panelWidth()) / 2; }

    @Override protected void init() {
        clearWidgets();
        fields.clear();
        page = Math.clamp(page, 0, pageCount() - 1);
        int x = panelX(), w = panelWidth();
        for (int i = page * perPage(); i < Math.min(KEYS.size(), (page + 1) * perPage()); i++) {
            String key = KEYS.get(i);
            int y = 48 + (i % perPage()) * 27;
            var field = new EditBox(font, x + w - 108, y, 86, 19, label(key));
            field.setMaxLength(7);
            field.setValue(draft.getOrDefault(key, ""));
            field.setHint(Component.literal("#RRGGBB"));
            field.setResponder(value -> draft.put(key, value.strip()));
            fields.add(field);
            addRenderableWidget(field);
        }
        int footer = height - 45;
        addRenderableWidget(new EditorIconButton(x, footer, Component.translatable("gui.back"),
                () -> { if (page > 0) { page--; init(); } }, styles, "theme-prev", "back"));
        addRenderableWidget(new EditorIconButton(x + 27, footer, Component.translatable("gui.next"),
                () -> { if (page + 1 < pageCount()) { page++; init(); } }, styles, "theme-next", "forward"));
        addRenderableWidget(new StyledButton(x + w - 158, footer, 75, 22,
                Component.translatable("screen.xero_quest_journal.theme.save"),
                b -> save(), styles, "theme-save"));
        addRenderableWidget(new StyledButton(x + w - 78, footer, 78, 22,
                Component.translatable("gui.cancel"), b -> onClose(), styles, "theme-cancel"));
    }

    private Component label(String key) {
        return Component.translatable("screen.xero_quest_journal.theme." + key);
    }

    private void save() {
        if (pending != null) return;
        try {
            Map<String, String> colors = new LinkedHashMap<>();
            for (String key : KEYS) {
                String value = draft.getOrDefault(key, "").strip();
                QuestUiTheme.validate(key, value);
                if (!value.isBlank()) colors.put(key, value);
            }
            var current = ClientQuestProgress.uiTheme();
            pending = ClientQuestProgress.setUiTheme(new QuestUiTheme(colors, current.overviewImage(),
                    current.journeyAppearance()));
            message = "";
        } catch (IllegalArgumentException invalid) {
            message = Component.translatable("screen.xero_quest_journal.theme.invalid").getString();
        }
    }

    @Override public void tick() {
        if (pending != null && pending.isDone()) {
            String result = pending.join();
            pending = null;
            message = Component.translatable("screen.xero_quest_journal.theme." + result).getString();
        }
    }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        backdrop(g, 0xEE1C2026);
        int x = panelX(), w = panelWidth();
        g.drawString(font, title, x, 17, 0xFFF2D29A);
        for (int i = page * perPage(); i < Math.min(KEYS.size(), (page + 1) * perPage()); i++) {
            String key = KEYS.get(i);
            int y = 48 + (i % perPage()) * 27;
            g.drawString(font, font.plainSubstrByWidth(label(key).getString(), w - 155),
                    x + 1, y + 5, 0xFFE4EAF2);
            String color = draft.getOrDefault(key, "");
            int sample = ButtonStyle.parseColor(color, 0xFF454B55);
            g.fill(x + w - 132, y + 3, x + w - 120, y + 16, sample);
            g.renderOutline(x + w - 133, y + 2, 14, 15, 0xFFAAB3BC);
        }
        g.drawString(font, (page + 1) + " / " + pageCount(), x + 60, height - 38, 0xFFB8C7D9);
        if (!message.isBlank()) g.drawCenteredString(font, message, width / 2, height - 15, 0xFFF2CF83);
        super.renderContents(g, mx, my, delta);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
