package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.mailbox.MailClipboardCodec;
import xtdpotato.xero_quest_journal.core.mailbox.MailDraft;
import xtdpotato.xero_quest_journal.core.mailbox.MailBannerTiming;
import xtdpotato.xero_quest_journal.core.model.MailNodeSettings;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Mail drafts share the task reward editor; delivery remains server-authoritative. */
public final class MailComposeScreen extends AnimatedQuestScreen {
    private final MailboxScreen parent;
    private String id = "mail_" + UUID.randomUUID().toString().replace("-", "");
    private EditBox recipient, sender, subject, icon, expiry;
    private MultiLineEditBox body;
    private String recipientValue = "", senderValue = "Xero", subjectValue = "", bodyValue = "",
            iconValue = "", bannerValue = "", targetTaskId = "";
    private boolean treasured;
    private boolean gameTime;
    private String expiryValue = "30";
    private List<MailboxRuntime.Attachment> rewards = List.of();
    private Component feedback;
    private CompletableFuture<String> pending;
    private AbstractWidget invalidWidget;
    private boolean confirmClose;
    private int pressedCloseAction = -1;
    private final Deque<String> bodyUndo = new ArrayDeque<>(), bodyRedo = new ArrayDeque<>();
    private boolean restoringBody;
    private EditorIconButton bodyUndoButton, bodyRedoButton, bodyEditorButton;
    private MailboxRuntime.View editingMail;
    private MailboxRuntime.View resendSource;
    private String initialExpiryValue;
    private MailBannerTiming bannerTiming = MailBannerTiming.NONE;

    public record DraftState(String id, String recipient, String sender, String subject, String body,
                             String icon, boolean treasured, List<MailboxRuntime.Attachment> rewards,
                             String expiryDays, boolean gameTime, String banner, String targetTaskId,
                             MailBannerTiming bannerTiming) {}

    MailComposeScreen(MailboxScreen parent) {
        super(text("compose"));
        this.parent = parent;
        var draft = ClientPreferences.get().mailDraft;
        if (draft != null) {
            if (draft.id() != null && draft.id().matches("[A-Za-z0-9._-]{1,128}")) id = draft.id();
            recipientValue = draft.recipient() == null ? "" : draft.recipient();
            senderValue = draft.sender() == null ? "Xero" : draft.sender();
            subjectValue = draft.subject() == null ? "" : draft.subject();
            bodyValue = draft.body() == null ? "" : draft.body();
            iconValue = draft.icon() == null ? "" : draft.icon();
            bannerValue = draft.banner() == null ? "" : draft.banner();
            targetTaskId = draft.targetTaskId() == null ? "" : draft.targetTaskId();
            bannerTiming = draft.bannerTiming() == null ? MailBannerTiming.NONE : draft.bannerTiming();
            treasured = draft.treasured();
            gameTime = draft.gameTime();
            expiryValue = draft.expiryDays() == null ? "30" : draft.expiryDays();
            rewards = draft.rewards() == null ? List.of() : List.copyOf(draft.rewards());
        }
    }

    MailComposeScreen(MailboxScreen parent, MailClipboardCodec.ImportedMail imported) {
        this(parent);
        applyDraft(imported.draft());
        recipientValue = imported.recipient();
        id = newMailId();
    }

    MailComposeScreen(MailboxScreen parent, MailboxRuntime.View mail, boolean resend) {
        this(parent);
        editingMail = resend ? null : mail;
        resendSource = resend ? mail : null;
        id = resend ? newMailId() : mail.id();
        recipientValue = mail.recipient();
        senderValue = mail.sender();
        subjectValue = mail.subject();
        bodyValue = mail.body();
        iconValue = mail.icon();
        bannerValue = mail.banner();
        targetTaskId = mail.targetTaskId();
        bannerTiming = mail.bannerTiming();
        treasured = mail.treasured();
        gameTime = mail.gameTime();
        long seconds = ClientMailbox.remainingSeconds(mail);
        expiryValue = seconds == Long.MAX_VALUE ? "0" : Long.toString(Math.max(1,
                (seconds + (gameTime ? 1200 : 86400) - 1) / (gameTime ? 1200 : 86400)));
        initialExpiryValue = expiryValue;
        rewards = mail.attachments().stream().map(a -> new MailboxRuntime.Attachment(
                a.type(), a.item(), a.count(), false, a.hidden(), a.hidePopup())).toList();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.mailbox." + key, args);
    }

    private EditBox field(int x, int y, int width, int max, String label, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, text(label));
        box.setMaxLength(max);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private void remember() {
        if (recipient == null) return;
        recipientValue = recipient.getValue();
        senderValue = sender.getValue();
        subjectValue = subject.getValue();
        iconValue = icon.getValue();
        bodyValue = body.getValue();
        if (expiry != null) expiryValue = expiry.getValue();
    }

    @Override protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        remember();
        clearWidgets();
        invalidWidget = null;
        var styles = ButtonStyleSheet.load();
        int left = 20, gap = 18, contentWidth = Math.max(280, width - left * 2);
        int sideWidth = Math.min(390, Math.max(140, contentWidth / 3));
        int mainWidth = Math.max(100, contentWidth - sideWidth - gap);
        int sideX = left + mainWidth + gap;
        boolean shortScreen = height < 300;
        int recipientY = shortScreen ? 68 : 78;
        int subjectY = shortScreen ? 102 : 112, bodyY = shortScreen ? 154 : 164;
        int templateY = shortScreen ? 24 : 42;

        StyledButton templates = new StyledButton(left, templateY, mainWidth, 22, text("template_picker"),
                ignored -> chooseTemplate(), styles, "mail-compose-templates");
        addRenderableWidget(templates);
        StyledButton saveTemplate = new StyledButton(sideX, templateY, sideWidth, 22, text("save_template"),
                ignored -> saveTemplate(false), styles, "mail-compose-save-template");
        saveTemplate.active = pending == null;
        addRenderableWidget(saveTemplate);

        int recipientWidth = (mainWidth - 10) / 2;
        recipient = field(left, recipientY, Math.max(80, recipientWidth - 26), 256,
                "recipient_label", recipientValue);
        recipient.setHint(text("recipient_self"));
        recipient.setEditable(editingMail == null);
        var pickRecipient = addRenderableWidget(new StyledButton(left + recipientWidth - 22, recipientY, 22, 20,
                Component.literal("..."), ignored -> pickRecipient(), styles, "mail-compose-pick-recipient"));
        pickRecipient.active = editingMail == null;
        sender = field(left + recipientWidth + 10, recipientY, mainWidth - recipientWidth - 10,
                256, "sender_label", senderValue);
        subject = field(left, subjectY, mainWidth, 512, "subject_label", subjectValue);

        int bodyHeight = Math.max(24, height - bodyY - 60);
        body = new SmoothTextBox(font, left, bodyY, mainWidth, bodyHeight,
                text("body_label"), text("body_label"));
        body.setCharacterLimit(8192);
        body.setValue(bodyValue);
        body.setValueListener(this::bodyChanged);
        addRenderableWidget(body);
        bodyUndoButton = addRenderableWidget(new EditorIconButton(left + mainWidth - 80, bodyY - 28,
                Component.translatable("screen.xero_quest_journal.layout.rich_undo"),
                () -> restoreBody(bodyUndo, bodyRedo), styles, "mail-body-undo", "undo"));
        bodyRedoButton = addRenderableWidget(new EditorIconButton(left + mainWidth - 52, bodyY - 28,
                Component.translatable("screen.xero_quest_journal.layout.rich_redo"),
                () -> restoreBody(bodyRedo, bodyUndo), styles, "mail-body-redo", "redo"));
        bodyEditorButton = addRenderableWidget(new EditorIconButton(left + mainWidth - 24, bodyY - 28,
                Component.translatable("screen.xero_quest_journal.layout.rich_editor"),
                this::openBodyEditor, styles, "mail-body-editor", "expand"));
        updateBodyTools();

        var rewardEditor = addRenderableWidget(new StyledButton(sideX, recipientY, sideWidth, 22, text("rewards", rewards.size()),
                ignored -> {
                    remember();
                    minecraft.setScreen(QuestRewardsScreen.mail(this, rewards,
                            values -> rewards = List.copyOf(values)));
                }, styles, "mail-compose-rewards"));
        rewardEditor.active = editingMail == null || editingMail.attachments().stream().noneMatch(
                MailboxRuntime.Attachment::claimed);
        int iconY = recipientY + (shortScreen ? 34 : 32);
        icon = field(sideX, iconY, sideWidth - 30, 8192, "icon_label", iconValue);
        icon.setHint(text("icon_default"));
        addRenderableWidget(new StyledButton(sideX + sideWidth - 24, iconY, 24, 20,
                Component.literal("..."), ignored -> {
                    remember();
                    minecraft.setScreen(QuestScreen.pickResource(this, iconValue, false, icon::setValue));
                }, styles, "mail-compose-pick-icon"));

        int optionsY = iconY + (shortScreen ? 26 : 32);
        addRenderableWidget(Checkbox.builder(text("treasured"), font).pos(sideX, optionsY)
                .selected(treasured).onValueChange((box, value) -> {
                    treasured = value;
                    if (expiry != null) expiry.active = !value;
                }).build());
        int expiryY = optionsY + (shortScreen ? 34 : 32);
        int expiryWidth = (sideWidth - 10) / 2;
        expiry = field(sideX, expiryY, expiryWidth, 6, "expiry_days", expiryValue);
        expiry.active = !treasured;
        addRenderableWidget(new StyledChoiceButton<>(sideX + expiryWidth + 10, expiryY,
                sideWidth - expiryWidth - 10, 20,
                text("clock"), List.of(false, true), gameTime,
                value -> text("clock." + (value ? "game" : "real")),
                value -> gameTime = value, styles, "mail-compose-clock"));
        int optionWidth = (sideWidth - 6) / 2;
        int extraY = expiryY + (shortScreen ? 28 : 32);
        addRenderableWidget(new StyledButton(sideX, extraY, optionWidth, 22, text("add_to_rewards"),
                ignored -> addToRewards(), styles, "mail-compose-add-to-rewards"));
        addRenderableWidget(new StyledButton(sideX + optionWidth + 6, extraY,
                sideWidth - optionWidth - 6, 22, text("banner_settings"), ignored -> {
            remember();
            minecraft.setScreen(new MailBannerScreen(this, bannerValue, targetTaskId, bannerTiming, value -> {
                bannerValue = value.image();
                targetTaskId = value.task();
                bannerTiming = value.timing();
            }));
        }, styles, "mail-compose-banner"));

        int buttonY = height - (shortScreen ? 26 : 30), buttonW = (contentWidth - 12) / 3;
        addRenderableWidget(new StyledButton(left, buttonY, buttonW, 22,
                editingMail == null ? text("send") : Component.translatable("screen.xero_quest_journal.save"),
                ignored -> { if (editingMail == null) send(); else saveMail(); }, styles, "mail-compose-send"));
        addRenderableWidget(new StyledButton(left + buttonW + 6, buttonY, buttonW, 22, text("copy_command"),
                ignored -> copyCommand(), styles, "mail-compose-copy"));
        addRenderableWidget(new StyledButton(left + (buttonW + 6) * 2, buttonY, buttonW, 22, text("cancel"),
                ignored -> onClose(), styles, "mail-compose-cancel"));
        addRenderableWidget(new StyledButton(width - 28, 8, 20, 20, Component.literal("\u00d7"),
                ignored -> onClose(), styles, "mail-compose-close"));
        if (pending != null) children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> !(widget instanceof StyledButton button
                        && (button.styleId().equals("mail-compose-close") || button.styleId().equals("mail-compose-cancel"))))
                .forEach(widget -> widget.active = false);
        setInitialFocus(recipient);
    }

    private void bodyChanged(String value) {
        if (!restoringBody && !value.equals(bodyValue)) {
            bodyUndo.push(bodyValue);
            while (bodyUndo.size() > 128) bodyUndo.removeLast();
            bodyRedo.clear();
        }
        bodyValue = value;
        updateBodyTools();
    }

    private void updateBodyTools() {
        boolean editable = pending == null && !confirmClose && ClientEditMode.enabled();
        if (bodyUndoButton != null) bodyUndoButton.active = editable && !bodyUndo.isEmpty();
        if (bodyRedoButton != null) bodyRedoButton.active = editable && !bodyRedo.isEmpty();
        if (bodyEditorButton != null) bodyEditorButton.active = editable;
    }

    private void restoreBody(Deque<String> from, Deque<String> to) {
        if (pending != null || confirmClose || !ClientEditMode.enabled() || from.isEmpty()) return;
        to.push(body.getValue());
        restoringBody = true;
        try { body.setValue(from.pop()); }
        finally { restoringBody = false; }
        setFocused(body);
        updateBodyTools();
    }

    private void setBodyValue(String value) {
        if (body == null) bodyValue = value;
        else body.setValue(value);
    }

    private void openBodyEditor() {
        if (pending != null || confirmClose || !ClientEditMode.enabled()) return;
        remember();
        minecraft.setScreen(new QuestTextEditorScreen(this, bodyValue, value -> {
            if (pending == null && ClientEditMode.enabled()) setBodyValue(value);
        }, 8192));
    }

    private void chooseTemplate() {
        remember();
        var options = new ArrayList<QuestValuePickerScreen.Option>();
        for (String key : List.of("notice", "reward", "keepsake")) {
            options.add(new QuestValuePickerScreen.Option("builtin:" + key,
                    text("preset." + key).getString()));
        }
        ClientMailTemplateStore.all().forEach(draft -> options.add(new QuestValuePickerScreen.Option(
                "saved:" + draft.id(), text("saved_template", draft.subject()).getString())));
        minecraft.setScreen(new QuestValuePickerScreen(this, text("template_picker").getString(),
                "mail_templates", () -> options, "", value -> {
            if (value.startsWith("builtin:")) applyPreset(value.substring("builtin:".length()));
            else if (value.startsWith("saved:")) ClientMailTemplateStore.all().stream()
                    .filter(draft -> draft.id().equals(value.substring("saved:".length())))
                    .findFirst().ifPresent(this::applyDraft);
        }));
    }

    private void applyPreset(String preset) {
        bannerValue = "";
        targetTaskId = "";
        bannerTiming = MailBannerTiming.NONE;
        switch (preset) {
            case "notice" -> {
                senderValue = "Xero";
                subjectValue = text("preset.notice_subject").getString();
                setBodyValue(text("preset.notice_body").getString());
                iconValue = "";
                treasured = false;
                expiryValue = "30";
                gameTime = false;
                rewards = List.of();
            }
            case "reward" -> {
                senderValue = "Xero";
                subjectValue = text("preset.reward_subject").getString();
                setBodyValue(text("preset.reward_body").getString());
                iconValue = "minecraft:chest";
                treasured = false;
                expiryValue = "30";
                gameTime = false;
                rewards = List.of(new MailboxRuntime.Attachment("item", "minecraft:diamond", 3, false));
            }
            case "keepsake" -> {
                senderValue = "Xero";
                subjectValue = text("preset.keepsake_subject").getString();
                setBodyValue(text("preset.keepsake_body").getString());
                iconValue = "minecraft:book";
                treasured = true;
                expiryValue = "0";
                gameTime = false;
                rewards = List.of();
            }
            default -> { return; }
        }
        if (icon != null) icon.setValue(iconValue);
        if (sender != null) {
            sender.setValue(senderValue);
            subject.setValue(subjectValue);
            expiry.setValue(expiryValue);
        }
        feedback = null;
    }

    private void applyDraft(MailDraft draft) {
        senderValue = draft.sender();
        subjectValue = draft.subject();
        setBodyValue(draft.body());
        iconValue = draft.icon();
        bannerValue = draft.banner();
        targetTaskId = draft.targetTaskId();
        bannerTiming = draft.bannerTiming();
        treasured = draft.treasured();
        gameTime = draft.gameTime();
        expiryValue = draft.expiresIn() == 0 ? "0" : Long.toString(
                Math.max(1, draft.expiresIn() / (draft.gameTime() ? 1200L : 86400L)));
        rewards = draft.attachments();
        if (sender != null) {
            sender.setValue(senderValue);
            subject.setValue(subjectValue);
            icon.setValue(iconValue);
            expiry.setValue(expiryValue);
        }
        feedback = null;
    }

    private static String newMailId() {
        return "mail_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void pickRecipient() {
        remember();
        var choices = new ArrayList<QuestValuePickerScreen.Option>();
        for (String selector : List.of("@s", "@p", "@a"))
            choices.add(new QuestValuePickerScreen.Option(selector, selector));
        if (minecraft.getConnection() != null) minecraft.getConnection().getOnlinePlayers().forEach(info ->
                choices.add(new QuestValuePickerScreen.Option(info.getProfile().getName(), info.getProfile().getName())));
        minecraft.setScreen(new QuestValuePickerScreen(this, text("recipient_picker").getString(),
                "", () -> choices, recipientValue, recipient::setValue));
    }

    private boolean valid() {
        remember();
        invalidWidget = null;
        if (recipientValue.length() > 256 || !validRecipient()) return invalid(recipient, "invalid_recipient");
        if (senderValue.length() > 256) return invalid(sender, "invalid_sender");
        if (subjectValue.isBlank() || subjectValue.length() > 512) return invalid(subject, "invalid_subject");
        if (bodyValue.isBlank() || bodyValue.length() > 8192) return invalid(body, "invalid_body");
        if (!QuestIcons.valid(iconValue)) return invalid(icon, "invalid_icon");
        if (!ClientMediaManager.validImageSource(bannerValue) && !QuestIcons.valid(bannerValue))
            return invalid(null, "invalid_banner");
        if (!targetTaskId.isBlank() && QuestScreen.questById(targetTaskId) == null)
            return invalid(null, "invalid_target_task");
        try { expirySeconds(); } catch (IllegalArgumentException invalid) { return invalid(expiry, "invalid_expiry"); }
        try {
            if (rewards.size() > 128 || rewards.stream().anyMatch(reward -> reward.type().equals("mail")
                    || !unchangedStoredReward(reward)
                    && !QuestRewards.valid(new QuestDefinition.Reward(reward.type(), reward.item(), reward.count()))))
                return invalid(null, "invalid_rewards");
        } catch (IllegalArgumentException invalid) {
            return invalid(null, "invalid_rewards");
        }
        feedback = null;
        return true;
    }

    private boolean unchangedStoredReward(MailboxRuntime.Attachment reward) {
        var source = editingMail != null ? editingMail : resendSource;
        return source != null && source.attachments().stream().anyMatch(a -> reward.equals(
                new MailboxRuntime.Attachment(a.type(), a.item(), a.count(), false, a.hidden(), a.hidePopup())));
    }

    private boolean validRecipient() {
        try {
            MailNodeSettings.validatePlayer(recipientValue);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private boolean invalid(AbstractWidget widget, String key) {
        invalidWidget = widget;
        feedback = text(key);
        if (widget != null) setFocused(widget);
        return false;
    }

    private void saveTemplate(boolean addToQuestRewards) {
        if (pending != null || !valid()) return;
        if (!ClientEditMode.enabled() || minecraft.player == null || !minecraft.player.hasPermissions(2)) {
            invalid(null, "template_permission");
            return;
        }
        String savedId = id;
        pending = ClientMailbox.saveTemplate(id, senderValue, subjectValue, bodyValue, iconValue,
                treasured, expirySeconds(), gameTime, rewards, bannerValue, targetTaskId, bannerTiming);
        init();
        pending.thenAccept(result -> minecraft.execute(() -> {
            pending = null;
            if (!result.equals("template_saved")) {
                feedback = text(result.equals("permission") ? "template_permission" : "template_save_failed");
            } else {
                MailDraft draft = new MailDraft(savedId, senderValue, subjectValue, bodyValue, iconValue,
                        treasured, List.of(), rewards, expirySeconds(), gameTime, bannerValue, targetTaskId, bannerTiming);
                try {
                    ClientMailTemplateStore.save(draft);
                    feedback = addToQuestRewards ? text("template_added", savedId) : text("template_saved");
                } catch (RuntimeException failure) {
                    feedback = text("template_local_save_failed");
                }
            }
            if (minecraft.screen == this) init();
        }));
    }

    private void addToRewards() {
        saveTemplate(true);
    }

    private void send() {
        if (pending != null || !ClientEditMode.enabled() || !valid()) return;
        pending = resendSource == null
                ? ClientMailbox.send(recipientValue, id, senderValue, subjectValue, bodyValue, iconValue,
                        treasured, expirySeconds(), gameTime, rewards, bannerValue, targetTaskId, bannerTiming)
                : ClientMailbox.resend(resendSource, recipientValue, id, senderValue, subjectValue, bodyValue,
                        iconValue, treasured, expirySeconds(), gameTime, rewards, bannerValue, targetTaskId, bannerTiming);
        saveDraft();
        init();
        pending.thenAccept(result -> minecraft.execute(() -> {
            pending = null;
            if (result.equals("sent")) {
                ClientPreferences.get().mailDraft = null;
                ClientPreferences.get().save();
                ClientMailbox.request();
                if (minecraft.screen == this) closeTo(parent);
            } else {
                feedback = text("action." + result);
                if (minecraft.screen == this) init();
            }
        }));
    }

    private void saveMail() {
        if (pending != null || !ClientEditMode.enabled() || !valid()) return;
        boolean preserveExpiry = expiryValue.equals(initialExpiryValue)
                && gameTime == editingMail.gameTime() && treasured == editingMail.treasured();
        pending = ClientMailbox.edit(editingMail, senderValue, subjectValue, bodyValue, iconValue, treasured,
                expirySeconds(), gameTime, rewards, preserveExpiry, bannerValue, targetTaskId, bannerTiming);
        init();
        pending.thenAccept(result -> minecraft.execute(() -> {
            pending = null;
            if (result.equals("edited")) {
                ClientMailbox.request();
                parent.showActionMessage(result);
                if (minecraft.screen == this) closeTo(parent);
            } else {
                feedback = text("action." + result);
                if (minecraft.screen == this) init();
            }
        }));
    }

    private void copyCommand() {
        if (pending != null || !valid()) return;
        try {
            MailDraft draft = new MailDraft(editingMail == null ? id : newMailId(), senderValue, subjectValue, bodyValue, iconValue,
                    treasured, List.of(), rewards, expirySeconds(), gameTime, bannerValue, targetTaskId, bannerTiming);
            minecraft.keyboardHandler.setClipboard(MailClipboardCodec.command(recipientValue, draft));
            feedback = text("command_copied");
        } catch (IllegalArgumentException invalid) {
            feedback = text("command_too_long");
        }
    }

    private void saveDraft() {
        remember();
        ClientPreferences.get().mailDraft = new DraftState(editingMail == null ? id : newMailId(), recipientValue, senderValue,
                subjectValue, bodyValue, iconValue, treasured, rewards, expiryValue, gameTime,
                bannerValue, targetTaskId, bannerTiming);
        ClientPreferences.get().save();
    }

    private boolean hasContent() {
        return !recipientValue.isBlank() || !senderValue.equals("Xero") || !subjectValue.isBlank()
                || !bodyValue.isBlank() || !iconValue.isBlank() || treasured || !rewards.isEmpty()
                || !bannerValue.isBlank() || !targetTaskId.isBlank() || !bannerTiming.equals(MailBannerTiming.NONE)
                || !expiryValue.equals("30") || gameTime;
    }

    private long expirySeconds() {
        if (treasured) return 0;
        long days = Long.parseLong(expiryValue);
        long seconds = Math.multiplyExact(days, gameTime ? 1200L : 86400L);
        MailNodeSettings.validateValue("expiresIn", Long.toString(seconds));
        return seconds;
    }

    @Override protected void renderContents(GuiGraphics graphics, int x, int y, float delta) {
        updateBodyTools();
        backdrop(graphics, 0xF01C2430);
        graphics.drawString(font, editingMail == null ? title : text("edit_mail"), 18, 12, 0xFFF3D38B, false);
        for (var entry : java.util.Map.of(recipient, "recipient_short", sender, "sender_label",
                subject, "subject_label", icon, "icon_label", expiry, "expiry_days").entrySet())
            graphics.drawString(font, text(entry.getValue()), entry.getKey().getX(),
                    entry.getKey().getY() - 12, 0xFFBCC8D6, false);
        graphics.drawString(font, text("body_label"), body.getX(), body.getY() - 12, 0xFFBCC8D6, false);
        if (feedback != null) graphics.drawString(font,
                font.plainSubstrByWidth(feedback.getString(), height < 300 ? body.getWidth() : width - 36),
                18, height - 45, 0xFFFFB4A8, false);
        super.renderContents(graphics, x, y, delta);
        if (invalidWidget != null) {
            graphics.renderOutline(invalidWidget.getX() - 1, invalidWidget.getY() - 1,
                    invalidWidget.getWidth() + 2, invalidWidget.getHeight() + 2, 0xFFFF7676);
        }
        int dialogW = HalfScreenDialog.width(width), dialogH = HalfScreenDialog.height(height);
        int dialogLeft = (width - dialogW) / 2, dialogTop = (height - dialogH) / 2;
        renderAnimatedMenu(graphics, "confirm", confirmClose ? "confirm" : null,
                viewport(Math.max(0, dialogLeft - 14), Math.max(0, dialogTop - 14),
                        Math.min(width, dialogLeft + dialogW + 14), Math.min(height, dialogTop + dialogH + 14)),
                0xCC111720, () -> {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1000);
            int w = HalfScreenDialog.width(width), h = HalfScreenDialog.height(height);
            int left = (width - w) / 2, top = (height - h) / 2, part = (w - 24) / 3;
            MailDecor.frame(graphics, left, top, w, h, true);
            var lines = font.split(text("save_draft_question"), Math.max(1, w - 24));
            for (int i = 0; i < Math.min(3, lines.size()); i++)
                graphics.drawString(font, lines.get(i), left + 12, top + 16 + i * font.lineHeight, 0xFF505A6B, false);
            String[] keys = {"save_draft_yes", "save_draft_no", "cancel"};
            for (int i = 0; i < 3; i++) {
                int bx = left + 8 + i * (part + 4), by = top + h - 34;
                graphics.fill(bx, by, bx + part, by + 24, 0xFF303C50);
                String label = text(keys[i]).getString();
                float scale = Math.min(1, (part - 8F) / Math.max(1, font.width(label)));
                graphics.pose().pushPose();
                graphics.pose().translate(bx + part / 2F, by + 12F, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(font, label, -font.width(label) / 2, -font.lineHeight / 2, 0xFFFFFFFF, false);
                graphics.pose().popPose();
            }
            graphics.pose().popPose();
        });
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (confirmClose) {
            if (button != 0) return true;
            int w = HalfScreenDialog.width(width), h = HalfScreenDialog.height(height);
            int left = (width - w) / 2, top = (height - h) / 2, part = (w - 24) / 3;
            if (y >= top + h - 34 && y < top + h - 10 && x >= left + 8 && x < left + w - 8) {
                int column = (int) ((x - left - 8) / (part + 4));
                int withinColumn = (int) ((x - left - 8) % (part + 4));
                if (column >= 0 && column < 3 && withinColumn < part) pressedCloseAction = column;
            } else pressedCloseAction = -2;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (inputBlocked()) { pressedCloseAction = -1; return true; }
        if (button == 0 && pressedCloseAction != -1) {
            int action = pressedCloseAction;
            pressedCloseAction = -1;
            int w = HalfScreenDialog.width(width), h = HalfScreenDialog.height(height);
            int left = (width - w) / 2, top = (height - h) / 2, part = (w - 24) / 3;
            if (action == -2) {
                if (x < left || x >= left + w || y < top || y >= top + h) confirmClose = false;
                return true;
            }
            if (y < top + h - 34 || y >= top + h - 10
                    || x < left + 8 || x >= left + w - 8) return true;
            int column = (int) ((x - left - 8) / (part + 4));
            int withinColumn = (int) ((x - left - 8) % (part + 4));
            if (column != action || withinColumn >= part) return true;
            if (action == 0) {
                saveDraft();
                confirmClose = false;
                closeTo(parent);
            } else if (action == 1) {
                ClientPreferences.get().mailDraft = null;
                ClientPreferences.get().save();
                confirmClose = false;
                closeTo(parent);
            } else confirmClose = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if (confirmClose) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) confirmClose = false;
            return true;
        }
        if (getFocused() == body && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            boolean redo = key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z
                    && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
            if (redo || key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
                restoreBody(redo ? bodyRedo : bodyUndo, redo ? bodyUndo : bodyRedo);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        return confirmClose || super.charTyped(codePoint, modifiers);
    }

    @Override public void onClose() {
        if (confirmClose) { confirmClose = false; return; }
        if (pending != null) { closeTo(parent); return; }
        remember();
        if (hasContent()) confirmClose = true;
        else closeTo(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
