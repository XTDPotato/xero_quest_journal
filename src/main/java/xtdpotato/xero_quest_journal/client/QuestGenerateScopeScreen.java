package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Searchable, virtualized multi-select scope chooser for task generation. */
final class QuestGenerateScopeScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 38;
    private final Screen parent;
    private final List<String> namespaces;
    private final LinkedHashSet<String> selected;
    private final Consumer<Set<String>> applied;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final SmoothScroll scroll = new SmoothScroll();
    private EditBox search;
    private StyledButton allButton;
    private StyledButton noneButton;
    private StyledButton applyButton;
    private List<String> filtered;
    private String query = "";
    private boolean dragging;

    QuestGenerateScopeScreen(Screen parent, List<String> namespaces, Set<String> selected,
                             Consumer<Set<String>> applied) {
        super(text("title"));
        this.parent = parent;
        this.namespaces = namespaces.stream().distinct().sorted((a, b) -> {
            if (a.equals("minecraft")) return b.equals("minecraft") ? 0 : -1;
            if (b.equals("minecraft")) return 1;
            return a.compareToIgnoreCase(b);
        }).toList();
        this.selected = new LinkedHashSet<>(selected);
        this.applied = applied;
        filter();
    }

    private static Component text(String key, Object... values) {
        return Component.translatable("screen.xero_quest_journal.generate.scope." + key, values);
    }

    private int panelWidth() { return Math.max(320, Math.min(700, width - 40)); }
    private int left() { return (width - panelWidth()) / 2; }
    private int visibleRows() { return Math.max(3, Math.min(12, filtered.size())); }
    private int panelHeight() { return 154 + visibleRows() * ROW_HEIGHT; }
    private int panelTop() { return Math.max(16, (height - panelHeight()) / 2); }
    private int listTop() { return panelTop() + 80; }
    private int listBottom() { return listTop() + visibleRows() * ROW_HEIGHT; }
    private int maxScroll() { return Math.max(0, filtered.size() * ROW_HEIGHT - (listBottom() - listTop())); }

    @Override
    protected void init() {
        clearWidgets();
        int x = left(), w = panelWidth();
        int top = panelTop();
        search = new EditBox(font, x + 12, top + 48, w - 24, 22, text("search"));
        search.setMaxLength(128);
        search.setHint(text("search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            filter();
        });
        addRenderableWidget(search);
        addRenderableWidget(new EditorIconButton(x + w - 32, top + 8,
                Component.translatable("gui.cancel"), this::onClose, styles,
                "generate-scope-close", "close"));
        allButton = new StyledButton(x + 12, listBottom() + 8, 112, 22, text("select_all"),
                ignored -> {
                    selected.addAll(namespaces);
                    updateButtons();
                }, styles, "generate-scope-all", "toolbar-button");
        allButton.setTooltip(Tooltip.create(text("select_all_hint")));
        allButton.active = selected.size() < namespaces.size();
        addRenderableWidget(allButton);
        noneButton = new StyledButton(x + 130, listBottom() + 8, 88, 22, text("select_none"),
                ignored -> {
                    selected.clear();
                    updateButtons();
                }, styles, "generate-scope-none", "toolbar-button");
        noneButton.setTooltip(Tooltip.create(text("select_none_hint")));
        noneButton.active = !selected.isEmpty();
        addRenderableWidget(noneButton);
        var rail = new ScrollRail(x + w - 10, listTop(), listBottom() - listTop(), scroll);
        rail.visible = filtered.size() > visibleRows();
        addRenderableWidget(rail);
        var cancel = new StyledButton(x + w - 206, listBottom() + 38, 92, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "generate-scope-cancel");
        addRenderableWidget(cancel);
        applyButton = new StyledButton(x + w - 106, listBottom() + 38, 94, 22, text("apply"),
                ignored -> {
                    if (selected.isEmpty()) return;
                    applied.accept(Set.copyOf(selected));
                    closeTo(parent);
                }, styles, "generate-scope-apply");
        applyButton.active = !selected.isEmpty();
        addRenderableWidget(applyButton);
        filtered = filterNamespaces(query);
        scroll.limit(maxScroll());
    }

    private void updateButtons() {
        if (allButton != null) allButton.active = selected.size() < namespaces.size();
        if (noneButton != null) noneButton.active = !selected.isEmpty();
        if (applyButton != null) applyButton.active = !selected.isEmpty();
    }

    private void filter() {
        filtered = filterNamespaces(query);
        scroll.jump(0);
        scroll.limit(maxScroll());
    }

    private List<String> filterNamespaces(String value) {
        String needle = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return namespaces.stream().filter(namespace -> needle.isEmpty()
                || namespace.toLowerCase(Locale.ROOT).contains(needle)
                || QuestModIconProvider.displayName(namespace).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    @Override
    public void tick() {
        scroll.limit(maxScroll());
        scroll.advance();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, 0xD8121519);
        super.renderContents(graphics, mouseX, mouseY, delta);
        int x = left(), w = panelWidth(), top = panelTop(), bottom = panelTop() + panelHeight();
        graphics.fill(x, top, x + w, bottom, 0xF022252C);
        graphics.renderOutline(x, top, w, bottom - top, 0xFF91856E);
        graphics.drawString(font, text("title"), x + 12, top + 10, 0xFFF2D29A);
        graphics.drawString(font, text("selected", selected.size(), namespaces.size()),
                x + 12, top + 30, 0xFFBFC8D3);
        graphics.fill(x + 8, listTop() - 4, x + w - 18, listBottom() + 2, 0xCC171B20);
        graphics.enableScissor(x + 8, listTop(), x + w - 18, listBottom());
        int first = Math.max(0, (int) (scroll.position() / ROW_HEIGHT) - 1);
        int end = Math.min(filtered.size(),
                (int) Math.ceil((scroll.position() + listBottom() - listTop()) / ROW_HEIGHT) + 1);
        for (int index = first; index < end; index++) {
            String namespace = filtered.get(index);
            int y = listTop() + index * ROW_HEIGHT - scroll.pixels();
            boolean hover = mouseX >= x + 8 && mouseX < x + w - 18 && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            boolean checked = selected.contains(namespace);
            graphics.fill(x + 10, y, x + w - 20, y + ROW_HEIGHT - 2,
                    hover ? 0xFF35404C : 0xFF292F38);
            graphics.renderOutline(x + 10, y, w - 30, ROW_HEIGHT - 2,
                    hover ? 0xFFE3C88F : 0xFF56616E);
            int checkX = x + 18, checkY = y + 10;
            graphics.fill(checkX, checkY, checkX + 16, checkY + 16, checked ? 0xFF64563C : 0xFF1C2026);
            graphics.renderOutline(checkX, checkY, 16, 16, checked ? 0xFFE9C778 : 0xFF8792A0);
            if (checked) JournalNavButton.drawIcon(graphics, "mail_check",
                    checkX + 2, checkY + 2, 12, 0xFFFFE8B0);
            QuestModIconProvider.render(graphics, namespace, x + 44, y + 5, 26);
            graphics.drawString(font, QuestModIconProvider.displayName(namespace),
                    x + 78, y + 5, checked ? 0xFFF1D7A1 : 0xFFE2E5E8);
            graphics.drawString(font, namespace, x + 78, y + 19, 0xFF9AA6B3);
        }
        graphics.disableScissor();
        if (filtered.isEmpty())
            graphics.drawCenteredString(font, text("empty"), x + w / 2, (listTop() + listBottom()) / 2, 0xFFBAC4D0);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= left() + 8 && x < left() + panelWidth() - 18
                && y >= listTop() && y < listBottom() && !filtered.isEmpty()) {
            int index = (int) ((y - listTop() + scroll.position()) / ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                String namespace = filtered.get(index);
                if (!selected.remove(namespace)) selected.add(namespace);
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left() && x < left() + panelWidth() && y >= listTop() && y < listBottom()) {
            scroll.wheel(vertical * ROW_HEIGHT * 2);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && y >= listTop() && y < listBottom()
                && x >= left() + 8 && x < left() + panelWidth() - 18) {
            if (!dragging) {
                dragging = true;
            } else {
                scroll.to(scroll.target() - dy);
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
