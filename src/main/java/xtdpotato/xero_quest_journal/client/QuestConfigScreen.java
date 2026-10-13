package xtdpotato.xero_quest_journal.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.QuestConfig;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/** Tabbed settings hub for personal controls and server-owned journal options. */
public final class QuestConfigScreen extends AnimatedQuestScreen {
    private static final List<String> TABS = List.of("general", "motion", "appearance", "advanced");
    private final Screen parent;
    private ButtonStyleSheet styles;
    private String tab = "general";
    private boolean saveFailed;
    private CompletableFuture<String> configPending;
    private String configMessage = "";
    private final SmoothScroll scroll = new SmoothScroll();
    private final List<AbstractWidget> bodyWidgets = new ArrayList<>();
    private int lastScroll;

    public QuestConfigScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.config.title"));
        this.parent = parent;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.config." + key, args);
    }
    private int panelWidth() { return Math.min(650, width - 24); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return height < 300 ? 12 : 39; }
    private int panelBottom() { return height - (height < 300 ? 12 : 38); }
    private int contentTop() { return panelTop() + 82; }
    private int footerY() { return panelBottom() - 31; }
    private int bodyBottom() { return footerY() - 8; }
    private int rowStep() { return Math.clamp((panelBottom() - contentTop() - 40) / 7, 32, 44); }
    private int innerLeft() { return panelLeft() + 22; }
    private int innerWidth() { return panelWidth() - 44 - (scroll.maximum() > 0 ? 12 : 0); }

    @Override
    protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        styles = ButtonStyleSheet.load();
        clearWidgets();
        bodyWidgets.clear();
        var config = ClientPreferences.get();
        int left = panelLeft(), w = panelWidth();
        int tabY = panelTop() + 34, tabGap = 4;
        int tabWidth = (w - 24 - (TABS.size() - 1) * tabGap) / TABS.size();
        for (int i = 0; i < TABS.size(); i++) {
            String value = TABS.get(i);
            var button = new StyledButton(left + 12 + i * (tabWidth + tabGap), tabY, tabWidth, 24,
                    text("tab." + value), ignored -> { tab = value; scroll.jump(0); init(); },
                    styles, "config-tab-" + value, "toolbar-button");
            button.setSelectionHighlighted(tab.equals(value));
            addRenderableWidget(button);
        }
        boolean stackGeneral = panelWidth() < 500;
        int rows = switch (tab) {
            case "general" -> stackGeneral ? 5 : 4;
            case "appearance" -> 5;
            case "advanced" -> 3;
            default -> 4;
        };
        scroll.limit((rows - 1) * rowStep() + 26 - Math.max(1, bodyBottom() - contentTop()));
        lastScroll = scroll.pixels();
        int bodyStart = children().size();
        int x = innerLeft(), half = (innerWidth() - 10) / 2, y = contentTop() - lastScroll, step = rowStep();
        switch (tab) {
            case "general" -> {
                toggle("inventory", x, y, stackGeneral ? innerWidth() : half, config.inventoryButton,
                        value -> { config.inventoryButton = value; saveLocal(); });
                toggle("hud", stackGeneral ? x : x + half + 10, stackGeneral ? y + step : y,
                        stackGeneral ? innerWidth() : half, config.trackingHud,
                        value -> { config.trackingHud = value; saveLocal(); });
                int remainingY = y + step * (stackGeneral ? 2 : 1);
                addRenderableWidget(new StyledChoiceButton<>(x, remainingY, innerWidth(), 24,
                        text("default_view"), List.of("journal", "overview"), config.defaultView,
                        value -> text("view." + value), value -> {
                            config.defaultView = value;
                            saveLocal();
                        }, styles, "default-view"));
                toggle("quest_reward_sources", x, remainingY + step, innerWidth(),
                        config.questRewardSources, value -> {
                            config.questRewardSources = value;
                            saveLocal();
                        });
                addRenderableWidget(new StyledButton(x, remainingY + step * 2, innerWidth(), 24, text("keys"),
                        ignored -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)),
                        styles, "keys"));
            }
            case "motion" -> {
                toggle("reduced_motion", x, y, innerWidth(), config.reducedMotion, value -> {
                    config.reducedMotion = value;
                    saveLocal();
                });
                addRenderableWidget(new StyledButton(x, y + step, 86, 24, Component.literal("- 50"),
                        ignored -> setPopupDuration(config.popupAnimationMillis - 50),
                        styles, "popup-duration-less"));
                addRenderableWidget(new StyledButton(x + innerWidth() - 86, y + step, 86, 24,
                        Component.literal("+ 50"), ignored -> setPopupDuration(config.popupAnimationMillis + 50),
                        styles, "popup-duration-more"));
                addRenderableWidget(new StyledButton(x + 94, y + step, innerWidth() - 188, 24,
                        text("animation.duration", config.popupAnimationMillis),
                        ignored -> {}, styles, "popup-duration-value"));
                addRenderableWidget(new StyledButton(x, y + step * 2, innerWidth(), 26,
                        text("animation.edit"), ignored -> minecraft.setScreen(new PopupAnimationEditorScreen(this)),
                        styles, "popup-animation-editor"));
                gappedHint("animation.preview_hint", x, y + step * 3);
            }
            case "appearance" -> {
                addRenderableWidget(new StyledButton(x, y, innerWidth(), 26, text("theme"),
                        ignored -> minecraft.setScreen(new QuestUiThemeScreen(this)), styles, "theme"));
                scaleSetting("task_toast_scale", x, y + step, innerWidth(),
                        config.taskToastScalePercent, value -> config.taskToastScalePercent = value,
                        "task-toast-scale");
                scaleSetting("task_toast_icon_scale", x, y + step * 2, innerWidth(),
                        config.taskToastIconScalePercent, value -> config.taskToastIconScalePercent = value,
                        "task-toast-icon-scale");
                addRenderableWidget(new StyledButton(x, y + step * 3, innerWidth(), 26, text("folder"),
                        ignored -> Util.getPlatform().openFile(QuestConfig.root().toFile()),
                        styles, "folder"));
                addRenderableWidget(new StyledButton(x, y + step * 4, innerWidth(), 26, text("reload"),
                        ignored -> styles.refreshIfChanged(),
                        styles, "reload"));
            }
            case "advanced" -> {
                StyledChoiceButton<Boolean> pagination = new StyledChoiceButton<>(x, y, innerWidth(), 24,
                        text("task_pagination"), List.of(false, true),
                        ClientQuestProgress.taskPagination(), value -> toggleLabel("task_pagination", value),
                        value -> {
                            if (configPending != null) return;
                            configPending = ClientQuestProgress.setTaskPagination(value);
                            configPending.thenAccept(result -> minecraft.execute(() -> {
                                configMessage = result;
                                saveFailed = !result.equals("config_saved");
                                configPending = null;
                                init();
                            }));
                        }, styles, "task-pagination");
                pagination.active = configPending == null && minecraft.player != null
                        && minecraft.player.hasPermissions(2);
                pagination.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("task_pagination_hint")));
                addRenderableWidget(pagination);
                addRenderableWidget(new StyledButton(x, y + step, innerWidth(), 24,
                        text("keys"), ignored -> minecraft.setScreen(
                        new KeyBindsScreen(this, minecraft.options)), styles, "keys"));
                addRenderableWidget(new StyledButton(x, y + step * 2, innerWidth(), 24,
                        text("folder"), ignored -> Util.getPlatform().openFile(QuestConfig.root().toFile()),
                        styles, "folder"));
            }
            default -> throw new IllegalStateException("Unknown settings tab " + tab);
        }
        bodyWidgets.addAll(children().subList(bodyStart, children().size()).stream()
                .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList());
        if (scroll.maximum() > 0)
            addRenderableWidget(new ScrollRail(left + w - 26, contentTop(), bodyBottom() - contentTop(), scroll));
        addRenderableWidget(new StyledButton(left + w - 112, footerY(), 96, 23,
                Component.translatable("gui.done"), ignored -> onClose(), styles, "done"));
    }

    private void toggle(String key, int x, int y, int w, boolean value,
                        java.util.function.Consumer<Boolean> update) {
        addRenderableWidget(new StyledChoiceButton<>(x, y, w, 24, text(key), List.of(false, true),
                value, enabled -> toggleLabel(key, enabled), enabled -> {
                    update.accept(enabled);
                    init();
                }, styles, "config-" + key));
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.literal(enabled ? "\u2611 " : "\u2610 ")
                .append(text(key)).append(": ")
                .append(Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private void setPopupDuration(int value) {
        var config = ClientPreferences.get();
        config.popupAnimationMillis = Math.clamp(value, 100, 2000);
        saveLocal();
        init();
    }

    private void scaleSetting(String key, int x, int y, int w, int value,
                              java.util.function.IntConsumer update, String id) {
        int side = 64;
        addRenderableWidget(new StyledButton(x, y, side, 24, Component.literal("- 5"),
                ignored -> {
                    update.accept(Math.max(10, value - 5));
                    saveLocal();
                    init();
                }, styles, id + "-less"));
        addRenderableWidget(new StyledButton(x + side + 8, y, w - side * 2 - 16, 24,
                text(key, value), ignored -> {}, styles, id + "-value"));
        addRenderableWidget(new StyledButton(x + w - side, y, side, 24, Component.literal("+ 5"),
                ignored -> {
                    update.accept(Math.min(200, value + 5));
                    saveLocal();
                    init();
                }, styles, id + "-more"));
    }

    private void saveLocal() { saveFailed = !ClientPreferences.get().save(); }

    private void gappedHint(String key, int x, int y) {
        addRenderableWidget(new StyledButton(x, y, innerWidth(), 24, text(key),
                ignored -> minecraft.setScreen(new PopupAnimationEditorScreen(this)),
                styles, "popup-animation-preview-hint", "toolbar-button"));
    }

    @Override public void tick() {
        if (styles != null) styles.refreshIfChanged();
        if (configPending != null && configPending.isDone()) {
            configMessage = configPending.join();
            saveFailed = !configMessage.equals("config_saved");
            configPending = null;
            init();
        }
    }

    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        backdrop(g, 0xE51C222B);
        scroll.advance();
        int change = scroll.pixels() - lastScroll;
        if (change != 0) bodyWidgets.forEach(widget -> widget.setY(widget.getY() - change));
        lastScroll = scroll.pixels();
        int x = panelLeft(), y = panelTop(), w = panelWidth(), bottom = panelBottom();
        g.fill(x, y, x + w, bottom, 0xEF222832);
        g.fill(x, y, x + w, y + 30, 0xFF313B49);
        g.fill(x, y + 30, x + w, y + 31, 0xFF8F9EB1);
        g.drawString(font, title, x + 18, y + 10, 0xFFF3D48B, false);
        g.drawString(font, text("section." + tab), x + 22, y + 65, 0xFFB8C7D9, false);
        g.fill(x + 22, y + 80, x + w - 22, y + 81, 0x665D6978);
        if (saveFailed)
            g.drawString(font, font.plainSubstrByWidth(text("server_save_failed").getString(), w - 144),
                    x + 18, bottom - 18, 0xFFFF8C83, false);
        else if (!configMessage.isBlank())
            g.drawString(font, font.plainSubstrByWidth(text(configMessage).getString(), w - 144),
                    x + 18, bottom - 18, 0xFF9DD7BC, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return bodyWidgets.contains(widget) ? viewport(innerLeft(), contentTop(), innerLeft() + innerWidth(), bodyBottom()) : null;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (x >= innerLeft() && x < panelLeft() + panelWidth() - 16 && y >= contentTop() && y < bodyBottom()) {
            scroll.wheel(vertical * 28);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override protected int animationHeaderBottom() { return 34; }
    @Override protected int animationFooterTop() { return panelBottom(); }
    @Override public boolean isPauseScreen() { return false; }
}
