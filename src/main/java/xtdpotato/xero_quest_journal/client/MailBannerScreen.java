package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.List;
import xtdpotato.xero_quest_journal.core.mailbox.MailBannerTiming;

/** Optional banner and destination, edited separately to keep the compose form compact. */
public final class MailBannerScreen extends AnimatedQuestScreen {
    private final Screen parent;
    public record Settings(String image, String task, MailBannerTiming timing) {}
    private final Consumer<Settings> saved;
    private String source, taskId;
    private String timingMode, timingValue;
    private boolean gameTime;
    private EditBox image;
    private Component error;

    public MailBannerScreen(Screen parent, String source, String taskId, MailBannerTiming timing,
                            Consumer<Settings> saved) {
        super(text("banner_label"));
        this.parent = parent;
        this.source = source;
        this.taskId = taskId;
        this.saved = saved;
        timingMode = timing.mode();
        timingValue = timingMode.equals("variable") ? timing.variable() : Long.toString(timing.seconds());
        gameTime = timing.gameTime();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.mailbox." + key, args);
    }

    @Override protected void init() {
        clearWidgets();
        var styles = ButtonStyleSheet.load();
        int w = Math.min(520, width - 32), x = (width - w) / 2;
        image = new EditBox(font, x, 60, w - 60, 20, text("banner_label"));
        image.setMaxLength(8192);
        image.setValue(source);
        image.setHint(text("banner_hint"));
        image.setResponder(value -> source = value);
        addRenderableWidget(image);
        addRenderableWidget(new EditorIconButton(x + w - 54, 58, text("banner_pick"), () ->
                minecraft.setScreen(QuestScreen.pickResource(this, source, false, value -> {
                    source = value;
                    image.setValue(value);
                })), styles, "mail-banner-pick", "grid").dark());
        addRenderableWidget(new EditorIconButton(x + w - 26, 58, text("banner_clear"), () -> image.setValue(""),
                styles, "mail-banner-clear", "delete").dark());
        var task = QuestScreen.questById(taskId);
        Component label = taskId.isBlank() ? text("target_task_none")
                : text("target_task_selected", task == null ? taskId : QuestText.title(task).getString());
        addRenderableWidget(new StyledButton(x, 96, w - 32, 22, label, ignored ->
                minecraft.setScreen(new QuestTaskPickerScreen(this, taskId, "", ids ->
                        { taskId = ids.isEmpty() ? "" : ids.getFirst(); init(); }).singleSelectionOnly()), styles, "mail-banner-task"));
        addRenderableWidget(new EditorIconButton(x + w - 26, 94, text("target_task_clear"), () -> {
            taskId = "";
            init();
        }, styles, "mail-banner-task-clear", "delete").dark());
        addRenderableWidget(new StyledChoiceButton<>(x, 128, w, 22, text("banner_time"),
                MailBannerTiming.MODES, timingMode, mode -> text("banner_time." + mode), mode -> {
            timingMode = mode;
            timingValue = mode.equals("variable") ? "" : "259200";
            error = null;
            init();
        }, styles, "mail-banner-time-mode"));
        if (timingMode.equals("countdown") || timingMode.equals("variable")) {
            EditBox value = new EditBox(font, x, 170, w - 134, 20,
                    text(timingMode.equals("variable") ? "banner_time_variable" : "banner_time_seconds"));
            value.setMaxLength(timingMode.equals("variable") ? 129 : 9);
            value.setValue(timingValue);
            value.setResponder(next -> timingValue = next);
            addRenderableWidget(value);
            if (timingMode.equals("variable")) {
                addRenderableWidget(new EditorIconButton(x + w - 26, 168, text("banner_time_variable"),
                        this::pickVariable, styles, "mail-banner-time-variable", "variables").dark());
            } else {
                addRenderableWidget(new StyledChoiceButton<>(x + w - 128, 170, 128, 20, text("clock"),
                        List.of(false, true), gameTime, next -> text("clock." + (next ? "game" : "real")),
                        next -> gameTime = next, styles, "mail-banner-time-clock"));
            }
        }
        addRenderableWidget(new StyledButton(x, height - 30, (w - 8) / 2, 22,
                Component.translatable("gui.done"), ignored -> apply(), styles, "mail-banner-apply"));
        addRenderableWidget(new StyledButton(x + (w + 8) / 2, height - 30, (w - 8) / 2, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "mail-banner-cancel"));
    }

    private void apply() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        if (!source.isBlank() && !ClientMediaManager.validImageSource(source) && !QuestIcons.valid(source)) {
            error = text("invalid_banner");
            return;
        }
        if (!taskId.isBlank() && QuestScreen.questById(taskId) == null) {
            error = text("invalid_target_task");
            return;
        }
        MailBannerTiming timing;
        try {
            timing = new MailBannerTiming(timingMode,
                    timingMode.equals("countdown") ? Long.parseLong(timingValue) : 0,
                    gameTime, timingMode.equals("variable") ? timingValue : "");
            if (timingMode.equals("variable") && !validVariable(timing.variable()))
                throw new IllegalArgumentException("Unknown or out-of-scope variable");
        } catch (IllegalArgumentException invalid) {
            error = text("invalid_banner_time");
            return;
        }
        saved.accept(new Settings(source, taskId, timing));
        minecraft.setScreen(parent);
    }

    private boolean validVariable(String id) {
        return ClientQuestDefinitions.variables().stream().anyMatch(variable -> variable.id().equals(id)
                && (variable.scope().equals("globe") || variable.scope().equals(taskId)));
    }

    private void pickVariable() {
        minecraft.setScreen(new QuestValuePickerScreen(this, text("banner_time_variable").getString(), "",
                () -> ClientQuestDefinitions.variables().stream()
                        .filter(variable -> variable.scope().equals("globe") || variable.scope().equals(taskId))
                        .map(variable -> new QuestValuePickerScreen.Option(variable.id(), variable.id())).toList(),
                timingValue, value -> { timingValue = value; init(); }));
    }

    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, 0xF01C2430);
        int w = Math.min(520, width - 32), x = (width - w) / 2;
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFF3D38B);
        graphics.drawString(font, text("banner_label"), x, 44, 0xFFBCC8D6, false);
        if (timingMode.equals("countdown") || timingMode.equals("variable"))
            graphics.drawString(font, text(timingMode.equals("variable") ? "banner_time_variable" : "banner_time_seconds"),
                    x, 156, 0xFFBCC8D6, false);
        int previewHeight = Math.min(w / 7, height - 256);
        String remaining = "";
        if (timingMode.equals("mail")) remaining = text("banner_time_preview").getString();
        else if (timingMode.equals("variable")) remaining = text("banner_time_unknown").getString();
        else if (timingMode.equals("countdown")) {
            try { remaining = MailBannerRenderer.formatRemaining(Math.max(0, Long.parseLong(timingValue)), gameTime); }
            catch (NumberFormatException invalid) { remaining = text("banner_time_unknown").getString(); }
        }
        if (!source.isBlank() && previewHeight >= 36)
            MailBannerRenderer.draw(graphics, font, source, !taskId.isBlank(),
                    remaining,
                    x, 204, w, previewHeight, false);
        if (error != null) graphics.drawString(font,
                font.plainSubstrByWidth(error.getString(), w), x, height - 44, 0xFFFFB4A8, false);
        super.renderContents(graphics, mouseX, mouseY, delta);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
