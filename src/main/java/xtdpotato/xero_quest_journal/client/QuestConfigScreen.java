package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.QuestConfig;

/** Full-screen personal settings; server-owned controls retain their own permission checks. */
public final class QuestConfigScreen extends AnimatedQuestScreen {
    private static final List<String> TABS = List.of("general", "motion", "notifications", "appearance", "tasks", "tools");
    private final Screen parent;
    private final SmoothScroll scroll = new SmoothScroll();
    private final List<AbstractWidget> bodyWidgets = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final UiCompositor tabSurface = new UiCompositor();
    private UiMotion tabMotion = new UiMotion(true, System.nanoTime());
    private record Row(Component label, int y) {}
    private record Area(int x, int y, int width) {}
    private ButtonStyleSheet styles;
    private String tab = "general", status = "";
    private boolean saveFailed;
    private CompletableFuture<String> configPending;
    private int lastScroll;

    public QuestConfigScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.config.title"));
        this.parent = parent;
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.config." + key, args);
    }
    private int left() { return Math.max(16, Math.min(48, width / 24)); }
    private int right() { return width - left(); }
    private int bodyTop() { return 106; }
    private int bodyBottom() { return height - 44; }
    private boolean narrow() { return width < 600; }
    private int rowHeight() { return narrow() ? 64 : 48; }
    private int tabMillis() { return popupAnimationMillis() == 0 ? 0 : 100; }
    private boolean changingTab() { return !tabMotion.settled(); }

    @Override protected void init() {
        styles = ButtonStyleSheet.load();
        clearWidgets();
        bodyWidgets.clear();
        rows.clear();
        int tabWidth = (width - 32) / TABS.size();
        for (int i = 0; i < TABS.size(); i++) {
            String value = TABS.get(i);
            var button = new StyledButton(16 + i * tabWidth, 45, tabWidth - 3, 24,
                    text("tab." + value), ignored -> selectTab(value), styles,
                    "config-tab-" + value, "toolbar-button", "config-nav");
            button.extraCss(".config-nav { background-color:#252a31; border-width:0px; color:#bcc9cd; }\n"
                    + ".config-nav:hover { background-color:#353e43; }\n"
                    + ".config-nav:selected { background-color:#344844; color:#dff5ed; }");
            button.setSelectionHighlighted(tab.equals(value));
            addRenderableWidget(button);
        }
        int count = switch (tab) {
            case "general" -> 5;
            case "motion" -> canAuthor() ? 4 : 3;
            case "notifications" -> 4;
            case "appearance" -> 5;
            case "tools" -> QuestReiShortcuts.available() ? 4 : 3;
            case "tasks" -> 4 + (ClientPreferences.get().fixedQuestContextMenuPosition ? 2 : 0);
            default -> 2;
        };
        scroll.limit(count * rowHeight() - Math.max(1, bodyBottom() - bodyTop()));
        lastScroll = scroll.pixels();
        var config = ClientPreferences.get();
        switch (tab) {
            case "general" -> {
                var area = row("default_view");
                body(new StyledChoiceButton<>(area.x(), area.y(), area.width(), 24, text("default_view"),
                        List.of("journal", "overview"), config.defaultView, value -> text("view." + value),
                        value -> { config.defaultView = value; saveLocal(); }, styles, "default-view"));
                toggle("inventory", config.inventoryButton, value -> config.inventoryButton = value);
                toggle("mail_inventory", config.mailboxInventoryButton, value -> config.mailboxInventoryButton = value);
                toggle("hud", config.trackingHud, value -> config.trackingHud = value);
                action("inventory_placement", "inventory-placement", () ->
                        minecraft.setScreen(new InventoryPlacementScreen(this)));
            }
            case "motion" -> {
                toggle("reduced_motion", config.reducedMotion, value -> config.reducedMotion = value);
                number("popup_duration", config.popupAnimationMillis, 50, 100, 2000,
                        value -> config.popupAnimationMillis = value, "popup-duration");
                action("animation.edit", "popup-animation-editor", () ->
                        minecraft.setScreen(new PopupAnimationEditorScreen(this)));
                if (canAuthor()) action("mailbox_animation.edit", "mailbox-animation-editor", () ->
                        minecraft.setScreen(new MailboxAnimationEditorScreen(this)));
            }
            case "notifications" -> {
                number("task_toast_icon_scale_label", config.taskToastIconScalePercent, 5, 10, 200,
                        value -> config.taskToastIconScalePercent = value, "task-toast-icon-scale");
                toggle("hud_auto_avoid_minimap", config.hudAutoAvoidMinimap,
                        value -> config.hudAutoAvoidMinimap = value);
                var corner = row("minimap_corner");
                var cornerChoice = body(new StyledChoiceButton<>(corner.x(), corner.y(), corner.width(), 24,
                        text("minimap_corner"), List.of("top_left", "top_right", "bottom_left", "bottom_right"),
                        config.minimapCorner, value -> text("minimap_corner." + value), value -> {
                            config.minimapCorner = value;
                            saveLocal();
                        }, styles, "hud-minimap-corner"));
                cornerChoice.setTooltip(Tooltip.create(text("minimap_corner_hint")));
                action("notification_layout", "notification-layout", () ->
                        minecraft.setScreen(new HudNotificationLayoutScreen(this)));
            }
            case "appearance" -> {
                number("journey_tab_underline", config.journeyTabUnderlineHeight, 1, 1, 5,
                        value -> config.journeyTabUnderlineHeight = value, "journey-tab-underline");
                number("tracking_marker_scale", config.trackingMarkerScalePercent, 5, 25, 200,
                        value -> config.trackingMarkerScalePercent = value, "tracking-marker-scale");
                bodyWidgets.stream().filter(widget -> widget instanceof StyledButton button
                                && button.styleId().equals("tracking-marker-scale-value"))
                        .map(StyledButton.class::cast).findFirst()
                        .ifPresent(button -> button.setTooltip(Tooltip.create(text("tracking_marker_scale_hint"))));
                var theme = action("theme", "theme", () -> minecraft.setScreen(new QuestUiThemeScreen(this)));
                theme.active = canAuthor();
                theme.setTooltip(Tooltip.create(text("author_required")));
                action("legendary_background", "legendary-background", () ->
                        minecraft.setScreen(new LegendaryBackgroundScreen(this)));
                action("legendary_background_reset", "legendary-background-reset", () -> {
                    config.legendaryBackground = "";
                    config.legendaryBackgroundType = "image";
                    config.legendaryBackgroundMode = "cover";
                    config.legendaryBackgroundMusic = "";
                    config.legendaryBackgroundScale = 1;
                    config.legendaryBackgroundOffsetX = config.legendaryBackgroundOffsetY = 0;
                    saveLocal();
                }).setMessage(text("reset"));
            }
            case "tasks" -> {
                var area = row("task_pagination");
                var pagination = body(new StyledChoiceButton<>(area.x(), area.y(), area.width(), 24,
                        text("task_pagination"), List.of(false, true), ClientQuestProgress.taskPagination(),
                        QuestConfigScreen::toggleValue, value -> {
                            if (configPending == null && canAuthor()) {
                                configPending = ClientQuestProgress.setTaskPagination(value);
                                init();
                            }
                        }, styles, "task-pagination"));
                pagination.active = canAuthor() && configPending == null;
                pagination.setTooltip(Tooltip.create(text("task_pagination_hint")));
                pageSizeControl();
                toggle("quest_reward_sources", config.questRewardSources, value -> config.questRewardSources = value);
                var menuArea = row("context_menu_position");
                var menuPosition = body(new StyledChoiceButton<>(menuArea.x(), menuArea.y(), menuArea.width(), 24,
                        text("context_menu_position"), List.of("cursor", "fixed"),
                        config.fixedQuestContextMenuPosition ? "fixed" : "cursor",
                        value -> text("context_menu_position." + value), value -> {
                            config.fixedQuestContextMenuPosition = value.equals("fixed");
                            saveLocal();
                        }, styles, "quest-context-menu-position"));
                menuPosition.setTooltip(Tooltip.create(text("context_menu_position_hint")));
                if (config.fixedQuestContextMenuPosition) {
                    number("context_menu_x", config.questContextMenuXPercent, 5, 0, 100,
                            value -> config.questContextMenuXPercent = value, "quest-context-menu-x");
                    number("context_menu_y", config.questContextMenuYPercent, 5, 0, 100,
                            value -> config.questContextMenuYPercent = value, "quest-context-menu-y");
                    bodyWidgets.stream().filter(widget -> widget instanceof StyledButton button
                                    && (button.styleId().equals("quest-context-menu-x-value")
                                    || button.styleId().equals("quest-context-menu-y-value")))
                            .map(StyledButton.class::cast).forEach(button -> button.setTooltip(Tooltip.create(
                                    text(button.styleId().endsWith("-x-value")
                                            ? "context_menu_x_hint" : "context_menu_y_hint"))));
                }
            }
            case "tools" -> {
                action("keys", "keys", () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)));
                action("folder", "folder", () -> Util.getPlatform().openFile(QuestConfig.root().toFile()));
                action("collapsed_reset", "collapsed-reset", () -> {
                    config.collapsedJournalGroups = List.of();
                    saveLocal();
                }).setMessage(text("reset"));
                if (QuestReiShortcuts.available())
                    action("rei_shortcuts", "rei-shortcuts", () ->
                            minecraft.setScreen(new QuestReiShortcutScreen(this)));
            }
            default -> throw new IllegalStateException("Unknown settings tab " + tab);
        }
        if (scroll.maximum() > 0)
            addRenderableWidget(new ScrollRail(width - 16, bodyTop(), bodyBottom() - bodyTop(), scroll));
        addRenderableWidget(new StyledButton(width - 112, height - 32, 96, 23,
                Component.translatable("gui.done"), ignored -> onClose(), styles, "done"));
    }

    private boolean canAuthor() {
        return ClientEditMode.enabled() && minecraft != null && minecraft.player != null
                && minecraft.player.hasPermissions(2);
    }
    private void selectTab(String value) {
        if (tab.equals(value)) return;
        tab = value;
        scroll.jump(0);
        setFocused(null);
        setDragging(false);
        tabMotion = new UiMotion(false, System.nanoTime());
        tabMotion.target(true, System.nanoTime(), tabMillis());
        init();
    }
    private Area row(String label) {
        int y = bodyTop() + rows.size() * rowHeight() - lastScroll;
        rows.add(new Row(text(label), y));
        int x = narrow() ? left() : width / 2 + 16;
        return new Area(x, y + (narrow() ? 28 : 12), right() - 14 - x);
    }
    private <T extends AbstractWidget> T body(T widget) {
        bodyWidgets.add(widget);
        return addRenderableWidget(widget);
    }
    private static Component toggleValue(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }
    private void toggle(String key, boolean value, Consumer<Boolean> update) {
        var area = row(key);
        body(new StyledChoiceButton<>(area.x(), area.y(), area.width(), 24, text(key), List.of(false, true),
                value, QuestConfigScreen::toggleValue, next -> { update.accept(next); saveLocal(); },
                styles, "config-" + key));
    }
    private void number(String key, int value, int step, int minimum, int maximum, IntConsumer update, String id) {
        var area = row(key);
        int[] current = {value};
        body(new StyledButton(area.x(), area.y(), 32, 24, Component.literal("-"), ignored -> {
            update.accept(current[0] = Math.max(minimum, current[0] - step));
            saveLocal();
        }, styles, id + "-less")).setTooltip(Tooltip.create(text("decrease", text(key))));
        EditBox input = new EditBox(font, area.x() + 38, area.y() + 2, area.width() - 76, 20,
                text(key));
        input.setValue(Integer.toString(value));
        input.setMaxLength(4);
        input.setFilter(raw -> raw.isEmpty() || raw.matches("\\d{1,4}"));
        input.setResponder(raw -> {
            if (raw.isBlank()) return;
            try {
                int next = Math.clamp(Integer.parseInt(raw), minimum, maximum);
                current[0] = next;
                update.accept(next);
                ClientPreferences.get().save();
            } catch (NumberFormatException ignored) {
            }
        });
        body(input);
        body(new StyledButton(area.x() + area.width() - 32, area.y(), 32, 24, Component.literal("+"), ignored -> {
            update.accept(current[0] = Math.min(maximum, current[0] + step));
            saveLocal();
        }, styles, id + "-more")).setTooltip(Tooltip.create(text("increase", text(key))));
    }
    private StyledButton action(String key, String id, Runnable action) {
        var area = row(key);
        return body(new StyledButton(area.x(), area.y(), area.width(), 24, text("open"),
                ignored -> action.run(), styles, id));
    }
    private void pageSizeControl() {
        var area = row("tasks_per_page");
        var input = new EditBox(font, area.x() + 34, area.y() + 2, Math.max(24, area.width() - 100), 20,
                text("tasks_per_page"));
        input.setMaxLength(4);
        input.setFilter(raw -> raw.isEmpty() || raw.matches("\\d{1,4}"));
        input.setValue(Integer.toString(ClientQuestProgress.tasksPerPage()));
        input.setEditable(canAuthor() && configPending == null);
        input.setTooltip(Tooltip.create(text("tasks_per_page_hint")));
        body(input);
        Runnable save = () -> {
            try {
                int value = Integer.parseInt(input.getValue());
                if (value < 10 || value > 1000) throw new NumberFormatException();
                if (canAuthor() && configPending == null) configPending = ClientQuestProgress.setTasksPerPage(value);
            } catch (NumberFormatException invalid) {
                input.setTextColor(0xFFFF9B8F); status = text("tasks_per_page_hint").getString();
            }
        };
        var less = body(new StyledButton(area.x(), area.y(), 28, 24, Component.literal("-"), ignored -> {
            input.setValue(Integer.toString(Math.max(10, ClientQuestProgress.tasksPerPage() - 10))); save.run();
        }, styles, "task-page-size-less"));
        var more = body(new StyledButton(area.x() + area.width() - 60, area.y(), 28, 24, Component.literal("+"), ignored -> {
            input.setValue(Integer.toString(Math.min(1000, ClientQuestProgress.tasksPerPage() + 10))); save.run();
        }, styles, "task-page-size-more"));
        var apply = body(new EditorIconButton(area.x() + area.width() - 24, area.y(), text("tasks_per_page_apply"),
                save, styles, "task-page-size-save", "save").dark());
        less.active = more.active = apply.active = canAuthor() && configPending == null;
    }
    private void saveLocal() {
        saveFailed = !ClientPreferences.get().save();
        status = text(saveFailed ? "local_save_failed" : "local_saved").getString();
        init();
    }
    @Override public void tick() {
        if (styles != null) styles.refreshIfChanged();
        if (configPending != null && configPending.isDone()) {
            String result;
            try { result = configPending.join(); } catch (RuntimeException failure) { result = "config_save_failed"; }
            configPending = null;
            saveFailed = !result.equals("config_saved");
            status = text(saveFailed ? "server_save_failed" : "server_saved").getString();
            init();
        }
    }

    private void renderBody(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, bodyTop() - 4, width, bodyBottom() + 1, 0xFF1B1E23);
        var bounds = viewport(left(), bodyTop(), right(), bodyBottom());
        g.enableScissor(left(), bodyTop(), right(), bodyBottom());
        for (var row : rows) {
            int labelWidth = narrow() ? right() - left() - 16 : width / 2 - left() - 28;
            var lines = font.split(row.label(), labelWidth);
            for (int i = 0; i < Math.min(2, lines.size()); i++)
                g.drawString(font, lines.get(i), left(), row.y() + (narrow() ? 9 : 15) + i * font.lineHeight,
                        0xFFE1E7E5, false);
            g.fill(left(), row.y() + rowHeight() - 1, right() - 14, row.y() + rowHeight(), 0xFF30353A);
        }
        for (var widget : bodyWidgets) {
            if (!bounds.intersects(widget)) continue;
            boolean hover = bounds.contains(mouseX, mouseY) && !changingTab();
            widget.render(g, hover ? mouseX : -10000, hover ? mouseY : -10000, delta);
        }
        g.disableScissor();
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        backdrop(g, 0xFF1B1E23);
        scroll.advance();
        int change = scroll.pixels() - lastScroll;
        if (change != 0) {
            bodyWidgets.forEach(widget -> widget.setY(widget.getY() - change));
            rows.replaceAll(row -> new Row(row.label(), row.y() - change));
        }
        lastScroll = scroll.pixels();
        tabMotion.advance(System.nanoTime(), tabMillis());
        if (getFocused() instanceof AbstractWidget widget && bodyWidgets.contains(widget)
                && !viewport(left(), bodyTop(), right(), bodyBottom()).intersects(widget)) {
            setFocused(null);
            setDragging(false);
        }
        g.fill(0, 0, width, 78, 0xFF22262C);
        g.drawString(font, title, 18, 15, 0xFFF0D49A, false);
        g.fill(0, 77, width, 78, 0xFF4A5754);
        g.drawString(font, text("tab." + tab), left(), 88, 0xFF9BC4B9, false);
        int tabWidth = (width - 32) / TABS.size();
        int selectedX = 16 + TABS.indexOf(tab) * tabWidth;
        g.fill(selectedX + 5, 70, selectedX + tabWidth - 8, 72, 0xFF9BD3C1);
        if (changingTab()) {
            tabSurface.capture(g, () -> renderBody(g, -10000, -10000, delta));
            tabSurface.transformed(g, width, height, 0, bodyTop(), width, bodyBottom(), 1,
                    12 * (1 - tabMotion.eased()), 0, 0, tabMotion.eased());
        } else {
            tabSurface.close();
            renderBody(g, mouseX, mouseY, delta);
        }
        g.fill(0, height - 39, width, height, 0xFF22262C);
        g.drawString(font, font.plainSubstrByWidth(status.isEmpty() ? text("personal").getString() : status,
                Math.max(40, width - 140)), 18, height - 23, saveFailed ? 0xFFFF9B8F : 0xFF99B5AA, false);
        for (var child : children()) if (child instanceof AbstractWidget widget && !bodyWidgets.contains(widget))
            widget.render(g, mouseX, mouseY, delta);
    }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return bodyWidgets.contains(widget) ? viewport(left(), bodyTop(), right(), bodyBottom()) : null;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (changingTab() && y >= bodyTop() && y < bodyBottom()) return true;
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inputBlocked() || changingTab()) return true;
        if (scrollChoicePopup(x, y, h, v)) return true;
        if (x >= left() && x < width - 8 && y >= bodyTop() && y < bodyBottom()) {
            scroll.wheel(v * 28);
            return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override public void removed() {
        tabSurface.close();
        super.removed();
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
