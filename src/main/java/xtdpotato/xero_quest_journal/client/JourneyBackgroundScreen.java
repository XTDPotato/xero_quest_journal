package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.JourneyBackdrop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Edits a chapter's scroll-positioned backdrops without publishing until the chapter form is saved. */
final class JourneyBackgroundScreen extends AnimatedQuestScreen {
    private final net.minecraft.client.gui.screens.Screen parent;
    private final Consumer<List<JourneyBackdrop>> saved;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final List<Draft> drafts = new ArrayList<>();
    private int selected;
    private String error = "";

    private static final class Draft {
        String start, source, type, scale, x, y, music;

        Draft(JourneyBackdrop value) {
            start = Integer.toString(value.startX());
            source = value.source();
            type = value.type();
            scale = Double.toString(value.scale());
            x = Integer.toString(value.offsetX());
            y = Integer.toString(value.offsetY());
            music = value.music();
        }

        JourneyBackdrop value() {
            return new JourneyBackdrop(Integer.parseInt(start.strip()), source, type,
                    Double.parseDouble(scale.strip()), Integer.parseInt(x.strip()),
                    Integer.parseInt(y.strip()), music);
        }
    }

    JourneyBackgroundScreen(QuestScreen parent, List<JourneyBackdrop> backgrounds,
                            Consumer<List<JourneyBackdrop>> saved) {
        this((net.minecraft.client.gui.screens.Screen) parent, backgrounds, saved);
    }

    JourneyBackgroundScreen(net.minecraft.client.gui.screens.Screen parent, List<JourneyBackdrop> backgrounds,
                            Consumer<List<JourneyBackdrop>> saved) {
        super(text("edit_backgrounds"));
        this.parent = parent;
        this.saved = saved;
        backgrounds.forEach(value -> drafts.add(new Draft(value)));
        if (drafts.isEmpty()) drafts.add(new Draft(new JourneyBackdrop(0, "", "image", 1, 0, 0)));
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.journey." + key);
    }

    private int panelWidth() { return Math.min(470, width - 16); }
    private int panelHeight() { return Math.min(266, height - 12); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int listWidth() { return Math.min(112, Math.max(72, panelWidth() / 4)); }
    private Draft current() { return drafts.get(selected); }

    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() {
        return viewport(left() - 2, top() - 2, left() + panelWidth() + 2, top() + panelHeight() + 2);
    }
    @Override protected void renderModalBackground(GuiGraphics graphics, float delta) {
        parent.render(graphics, -10000, -10000, delta);
    }

    @Override
    protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets();
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        int listW = listWidth(), fieldX = x + listW + 18, fieldW = w - listW - 32;
        int visible = Math.min(drafts.size(), Math.max(1, (h - 86) / 22));
        int first = Math.clamp(selected - visible + 1, 0, drafts.size() - visible);
        for (int i = first; i < first + visible; i++) {
            int index = i;
            var row = new StyledButton(x + 8, y + 30 + (i - first) * 22, listW - 4, 20,
                    text("segment").copy().append(" " + (i + 1)),
                    ignored -> { selected = index; error = ""; init(); }, styles,
                    "journey-segment-" + i, "toolbar-button");
            row.active = i != selected;
            addRenderableWidget(row);
        }
        int buttonY = y + h - 49;
        addRenderableWidget(new StyledButton(x + 8, buttonY, (listW - 8) / 2, 19,
                text("add"), ignored -> {
                    int start = drafts.stream().mapToInt(value -> {
                        try { return Integer.parseInt(value.start.strip()); }
                        catch (NumberFormatException invalid) { return 0; }
                    }).max().orElse(0);
                    drafts.add(new Draft(new JourneyBackdrop(Math.min(100000, start + 400),
                            "", "image", 1, 0, 0)));
                    selected = drafts.size() - 1;
                    init();
                }, styles, "journey-add", "toolbar-button"));
        addRenderableWidget(new StyledButton(x + 12 + (listW - 8) / 2, buttonY, (listW - 8) / 2, 19,
                text("delete"), ignored -> {
                    drafts.remove(selected);
                    if (drafts.isEmpty()) drafts.add(new Draft(new JourneyBackdrop(0, "", "image", 1, 0, 0)));
                    selected = Math.min(selected, drafts.size() - 1);
                    init();
                }, styles, "journey-delete", "toolbar-button"));

        Draft draft = current();
        int row = 30;
        field(fieldX, y + row, fieldW, "start", draft.start, 8, value -> draft.start = value);
        row += 24;
        addRenderableWidget(new StyledChoiceButton<>(fieldX, y + row, fieldW, 20,
                text("type"), List.of("image", "video"), draft.type,
                value -> text("type." + value), value -> {
                    draft.type = value;
                    draft.source = "";
                    init();
                }, styles, "journey-type"));
        row += 24;
        field(fieldX, y + row, fieldW - 27, "source", draft.source, 4096, value -> draft.source = value);
        var picker = new StyledButton(fieldX + fieldW - 24, y + row, 24, 20,
                Component.literal("..."), ignored -> {
                    if (draft.type.equals("video")) QuestMediaPicker.choose(this, true, value -> {
                        draft.source = value;
                        init();
                    });
                    else minecraft.setScreen(QuestScreen.pickResource(this, draft.source, false, value -> {
                        draft.source = value;
                        init();
                    }));
                }, styles, "journey-source-pick", "toolbar-button");
        picker.setTooltip(Tooltip.create(text("choose_source")));
        addRenderableWidget(picker);
        row += 24;
        field(fieldX, y + row, fieldW, "scale", draft.scale, 12, value -> draft.scale = value);
        row += 24;
        field(fieldX, y + row, fieldW / 2 - 2, "offset_x", draft.x, 8, value -> draft.x = value);
        field(fieldX + fieldW / 2 + 2, y + row, fieldW / 2 - 2,
                "offset_y", draft.y, 8, value -> draft.y = value);
        row += 24;
        field(fieldX, y + row, fieldW - 27, "music", draft.music, 4096, value -> draft.music = value);
        addRenderableWidget(new EditorIconButton(fieldX + fieldW - 24, y + row - 2, text("music"),
                () -> QuestMediaPicker.choose(this, false, value -> { draft.music = value; init(); }),
                styles, "journey-music-pick", "choose"));
        int half = (fieldW - 4) / 2;
        addRenderableWidget(new StyledButton(fieldX, buttonY, half, 19, text("save"),
                ignored -> save(), styles, "journey-save", "toolbar-button"));
        addRenderableWidget(new StyledButton(fieldX + half + 4, buttonY, half, 19,
                Component.translatable("gui.cancel"), ignored -> onClose(),
                styles, "journey-cancel", "toolbar-button"));
    }

    private void field(int x, int y, int w, String key, String value, int max,
                       Consumer<String> changed) {
        EditBox box = new EditBox(font, x, y, Math.max(20, w), 20, text(key));
        box.setMaxLength(max);
        box.setValue(value);
        box.setHint(text(key));
        box.setTooltip(Tooltip.create(text(key)));
        box.setResponder(changed);
        addRenderableWidget(box);
    }

    private void save() {
        try {
            List<JourneyBackdrop> values = new ArrayList<>();
            Set<Integer> starts = new HashSet<>();
            for (Draft draft : drafts) {
                if (draft.source.isBlank() && draft.music.isBlank()) continue;
                JourneyBackdrop value = draft.value();
                boolean validSource = value.source().isBlank() || (value.type().equals("video")
                        ? ClientMediaManager.validVideoSource(value.source())
                        : ClientMediaManager.validImageSource(value.source())
                        || QuestIcons.valid(value.source()));
                if (!starts.add(value.startX()) || !validSource
                        || !value.music().isBlank() && !ClientMediaManager.validAudioSource(value.music()))
                    throw new IllegalArgumentException("Invalid backdrop");
                values.add(value);
            }
            values.sort(Comparator.comparingInt(JourneyBackdrop::startX));
            saved.accept(List.copyOf(values));
            minecraft.setScreen(parent);
        } catch (IllegalArgumentException invalid) {
            error = text("invalid").getString();
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFE2CCA1);
        graphics.fill(x, y, x + w, y + h, 0xFF29343B);
        graphics.drawString(font, text("edit_backgrounds"), x + 10, y + 9, 0xFFF3DFAE);
        if (!error.isBlank()) graphics.drawString(font, error, x + 10, y + h - 70, 0xFFFF8888);
        super.renderContents(graphics, mouseX, mouseY, delta);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
