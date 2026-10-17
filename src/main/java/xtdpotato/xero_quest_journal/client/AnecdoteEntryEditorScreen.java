package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.List;

/** Edits one independently stored chronicle card; record paragraphs have their own editor. */
final class AnecdoteEntryEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final boolean creating;
    private String id, titleValue, labelValue, iconValue, headingValue;
    private List<AnecdoteDefinition.Record> records;
    private List<QuestDefinition.Reward> rewards;
    private boolean defaultDiscovered, hideWhenLocked;
    private EditBox title, label, icon, heading;
    private ButtonStyleSheet styles;
    private int formLeft, formWidth, previewLeft, previewWidth;

    static String nextRecordId(AnecdoteDefinition entry) {
        return nextRecordId(entry, entry.records());
    }

    static String nextRecordId(AnecdoteDefinition entry, List<AnecdoteDefinition.Record> records) {
        for (int index = 1; index <= AnecdoteDefinition.MAX_RECORDS; index++) {
            String id = entry.id() + "_record_" + index;
            if (records.stream().noneMatch(record -> record.id().equals(id))) return id;
        }
        throw new IllegalStateException("Anecdote record limit reached");
    }

    AnecdoteEntryEditorScreen(Screen parent, AnecdoteDefinition entry, boolean creating) {
        super(Component.translatable("screen.xero_quest_journal.anecdote.edit_entry"));
        this.parent = parent;
        this.creating = creating;
        id = entry.id();
        titleValue = entry.title();
        labelValue = entry.cardText();
        iconValue = entry.icon();
        headingValue = entry.heading();
        records = entry.records();
        rewards = entry.rewards();
        defaultDiscovered = entry.defaultDiscovered();
        hideWhenLocked = entry.hideWhenLocked();
    }

    private Component text(String key, Object... arguments) {
        return Component.translatable("screen.xero_quest_journal.anecdote." + key, arguments);
    }

    @Override
    protected void init() {
        if (!ClientEditMode.enabled() || minecraft.player == null || !minecraft.player.hasPermissions(2)) {
            onClose();
            return;
        }
        remember();
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int margin = Math.max(16, width / 32);
        previewWidth = Math.min(300, Math.max(180, width / 3));
        previewLeft = margin;
        formLeft = previewLeft + previewWidth + margin;
        formWidth = Math.max(180, width - formLeft - margin);
        int y = Math.max(52, height / 8);
        addRenderableWidget(field(formLeft, y, formWidth, 128, "title_label", titleValue, value -> title = value));
        addRenderableWidget(field(formLeft, y + 31, formWidth - 30, 128, "card_text_label", labelValue,
                value -> label = value));
        addRenderableWidget(new StyledButton(formLeft + formWidth - 24, y + 31, 24, 20, Component.literal("..."),
                ignored -> minecraft.setScreen(new QuestTextEditorScreen(this, labelValue,
                        value -> label.setValue(value), 128)),
                styles, "anecdote-label-edit", "toolbar-button"));
        addRenderableWidget(field(formLeft, y + 62, formWidth - 30, 2048, "icon_label", iconValue,
                value -> icon = value));
        addRenderableWidget(new StyledButton(formLeft + formWidth - 24, y + 62, 24, 20, Component.literal("..."),
                ignored -> minecraft.setScreen(QuestScreen.pickResource(this, iconValue, false,
                        value -> { iconValue = value; icon.setValue(value); })),
                styles, "anecdote-icon-pick", "toolbar-button"));
        addRenderableWidget(field(formLeft, y + 93, formWidth, 256, "heading_label", headingValue,
                value -> heading = value));

        int optionsY = y + 126;
        addRenderableWidget(new StyledButton(formLeft, optionsY, formWidth, 22,
                text("default_discovered").copy().append(": ").append(bool(defaultDiscovered)),
                ignored -> { defaultDiscovered = !defaultDiscovered; init(); },
                styles, "anecdote-default-discovered", "toolbar-button"));
        addRenderableWidget(new StyledButton(formLeft, optionsY + 28, formWidth, 22,
                text("hide_locked").copy().append(": ").append(bool(hideWhenLocked)),
                ignored -> { hideWhenLocked = !hideWhenLocked; init(); },
                styles, "anecdote-hide-locked", "toolbar-button"));
        addRenderableWidget(new StyledButton(formLeft, optionsY + 62, formWidth, 24,
                text("records", records.size()),
                ignored -> { remember(); minecraft.setScreen(new AnecdoteRecordsEditorScreen(
                        this, draft(), next -> { records = List.copyOf(next); init(); })); },
                styles, "anecdote-records", "toolbar-button"));

        int bottom = height - 34;
        addRenderableWidget(new StyledButton(width - 230, bottom, 78, 24, text("save"),
                ignored -> save(), styles, "anecdote-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(width - 144, bottom, 60, 24, text("cancel"),
                ignored -> onClose(), styles, "anecdote-cancel", "toolbar-button"));
        if (!creating) addRenderableWidget(new StyledButton(margin, bottom, 86, 24, text("delete"),
                ignored -> confirmDelete(), styles, "anecdote-delete", "toolbar-button"));
    }

    private EditBox field(int x, int y, int w, int max, String labelKey, String value,
                          java.util.function.Consumer<EditBox> capture) {
        EditBox box = new EditBox(font, x, y, w, 20, text(labelKey));
        box.setMaxLength(max);
        box.setValue(value);
        capture.accept(box);
        return box;
    }

    private Component bool(boolean value) { return Component.translatable(value ? "gui.yes" : "gui.no"); }

    private void remember() {
        if (title != null) titleValue = title.getValue();
        if (label != null) labelValue = label.getValue();
        if (icon != null) iconValue = icon.getValue();
        if (heading != null) headingValue = heading.getValue();
    }

    private AnecdoteDefinition draft() {
        remember();
        return new AnecdoteDefinition(id, titleValue, labelValue, iconValue, headingValue, records,
                defaultDiscovered, hideWhenLocked, creating ? AnecdoteClientState.snapshot().entries().size()
                : AnecdoteClientState.snapshot().entries().stream().filter(entry -> entry.id().equals(id))
                .mapToInt(AnecdoteDefinition::order).findFirst().orElse(0), rewards);
    }

    private void save() {
        try {
            AnecdoteClientState.save(draft());
            AnecdoteClientState.clearResult();
            closeTo(parent);
        } catch (RuntimeException invalid) {
            XeroTips.show(Component.literal(invalid.getMessage() == null ? "Invalid anecdote" : invalid.getMessage()));
        }
    }

    private void confirmDelete() {
        minecraft.setScreen(new QuestConfirmScreen(this, text("delete_confirm"), () -> {
            AnecdoteClientState.delete(id);
            minecraft.setScreen(parent);
        }));
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int margin = Math.max(16, width / 32);
        g.fill(0, 0, width, height, 0xFF202A3B);
        g.fill(margin - 4, 34, margin + previewWidth + 4, height - 24, 0xFFF4EBD8);
        g.renderOutline(margin - 4, 34, previewWidth + 8, height - 58, 0xFFC5AE83);
        drawPreview(g);
        g.drawString(font, text(creating ? "create_entry" : "edit_entry"), formLeft, 30, 0xFFF0DCA9, false);
        if (!AnecdoteClientState.lastResult().isBlank()
                && !List.of("saved", "deleted").contains(AnecdoteClientState.lastResult()))
            g.drawString(font, text("save_error"), formLeft, height - 55, 0xFFE28B72, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    private void drawPreview(GuiGraphics g) {
        int x = previewLeft + 20, w = previewWidth - 40;
        int y = Math.max(66, height / 5);
        int cardHeight = Math.min(height - y - 80, Math.round(w * 1.25F));
        g.fill(x, y, x + w, y + cardHeight, 0xFFFFF7E8);
        g.renderOutline(x, y, w, cardHeight, 0xFFCCB78E);
        int imageSize = Math.max(24, Math.min(w - 18, cardHeight - 42));
        if (!iconValue.isBlank())
            QuestIcons.render(g, iconValue, x + (w - imageSize) / 2, y + 8, imageSize, imageSize, 1);
        int footer = y + cardHeight - 32;
        g.fill(x + 5, footer - 4, x + w - 5, footer - 3, 0x66C3AE86);
        String value = labelValue.isBlank() ? "逸闻" : labelValue;
        float scale = Math.min(1, (w - 10F) / Math.max(1, font.width(value)));
        MailDecor.centered(g, font, value, x + w / 2, footer + (24 - font.lineHeight) / 2,
                scale, 0xFF53596A);
        if (defaultDiscovered) drawNewBadge(g, x + w - 22, y - 5);
    }

    private void drawNewBadge(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 22, y + 20, 0xFFE8AA33);
        g.drawString(font, "新", x + 3, y + 3, 0xFFFFFFFF, false);
    }

    @Override
    public void onClose() {
        remember();
        closeTo(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
