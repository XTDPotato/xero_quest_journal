package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Persistent search-scope panel. It can only be dismissed with its bottom close action. */
public final class QuestSearchSettingsScreen extends AnimatedQuestScreen {
    private static final String PREFIX = "screen.xero_quest_journal.search_settings.";
    private static final String[] SCOPES = {"title", "description", "subtitle", "internal"};
    private final QuestScreen journal;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final Set<String> scopes = new LinkedHashSet<>(ClientPreferences.get().questSearchScopes);
    private String error = "";

    public QuestSearchSettingsScreen(QuestScreen journal) {
        super(text("title"));
        this.journal = journal;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }
    private int panelWidth() { return Math.min(460, width - 24); }
    private int panelLeft() { return (width - panelWidth()) / 2; }

    @Override
    protected void init() {
        clearWidgets();
        int x = panelLeft() + 24;
        int w = panelWidth() - 48;
        int rowY = 104;
        for (int index = 0; index < SCOPES.length; index++) {
            String scope = SCOPES[index];
            var checkbox = Checkbox.builder(text("scope." + scope), font)
                    .pos(x, rowY + index * 34)
                    .selected(scopes.contains(scope))
                    .onValueChange((box, selected) -> setScope(scope, selected))
                    .build();
            checkbox.setTooltip(Tooltip.create(text("scope_hint." + scope)));
            addRenderableWidget(checkbox);
        }
        var filter = new JournalNavButton(x, 268, w, 28, text("open_filters"),
                ignored -> minecraft.setScreen(new QuestSearchFilterScreen(this)), styles,
                "search-settings-filters", JournalNavButton.Kind.ACTION, "choose");
        filter.setTooltip(Tooltip.create(text("filter_hint")));
        addRenderableWidget(filter);

        var close = new StyledButton(x, height - 48, w, 28, text("close"),
                ignored -> closeSettings(), styles, "search-settings-close", "toolbar-button");
        close.leadingIcon("xero_quest_journal:textures/gui/navigation/close.png");
        close.setTooltip(Tooltip.create(text("close_hint")));
        addRenderableWidget(close);
    }

    private void setScope(String scope, boolean enabled) {
        if (enabled) scopes.add(scope);
        else scopes.remove(scope);
        var preferences = ClientPreferences.get();
        preferences.questSearchScopes = Set.copyOf(scopes);
        if (!preferences.save()) error = "save_failed";
        else {
            error = "";
            journal.searchConfigurationChanged();
        }
    }

    boolean applyFilters(java.util.Map<String, Set<String>> filters) {
        var preferences = ClientPreferences.get();
        preferences.questSearchFilters = QuestSearchFilters.sanitize(filters);
        if (!preferences.save()) {
            error = "save_failed";
            return false;
        }
        else {
            error = "";
            journal.searchConfigurationChanged();
            init();
            return true;
        }
    }

    private void closeSettings() {
        journal.searchConfigurationChanged();
        closeTo(journal);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        backdrop(g, 0xB8161B22);
        int left = panelLeft(), top = 16, w = panelWidth(), bottom = height - 16;
        g.fill(left, top, left + w, bottom, 0xFF222831);
        g.fill(left, top, left + w, top + 42, 0xFF2B333E);
        g.renderOutline(left, top, w, bottom - top, 0xFF79899C);
        g.drawString(font, title, left + 20, top + 14, 0xFFF1D49A, false);
        g.drawWordWrap(font, text("hint"), left + 24, 68, w - 48, 0xFFB9C4D0);
        g.fill(left + 20, 96, left + w - 20, 97, 0xFF414A55);
        int checked = scopes.size();
        g.drawString(font, text("selected_count", checked), left + 24, 86, 0xFF9EC5B7, false);
        g.fill(left + 20, 248, left + w - 20, 249, 0xFF414A55);
        g.drawString(font, text("filter_count", QuestSearchFilters.count(
                ClientPreferences.get().questSearchFilters)), left + 24, 306, 0xFF9EC5B7, false);
        if (!error.isBlank())
            g.drawString(font, text(error), left + 24, height - 66, 0xFFFF9B8F, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (inputBlocked()) return true;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() {
        // Deliberately persistent; the bottom action is the explicit close affordance.
    }

    @Override public boolean isPauseScreen() { return false; }
}
