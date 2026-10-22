package xtdpotato.xero_quest_journal.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.AnecdoteCardLayout;
import xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout;
import xtdpotato.xero_quest_journal.core.model.AnecdoteCardLayout.Part;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

/** One entry draft sharing card-relative geometry with the live book. */
final class AnecdoteEntryEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final boolean creating;
    private final String id;
    private final int order;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String titleValue, labelValue, iconValue, headingValue;
    private List<AnecdoteDefinition.Record> records;
    private List<QuestDefinition.Reward> rewards;
    private final AnecdoteRewardLayout rewardLayout;
    private boolean defaultDiscovered, hideWhenLocked, syncing;
    private AnecdoteCardLayout layout;
    private String tab = "content", selected = "image", dragMode = "";
    private final SmoothScroll scroll = new SmoothScroll();
    private final Map<AbstractWidget, Integer> formPositions = new IdentityHashMap<>();
    private final List<Caption> captions = new ArrayList<>();
    private final Map<String, EditBox> numbers = new LinkedHashMap<>();
    private final Deque<Draft> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Draft dragBefore;
    private double dragX, dragY;
    private int formLeft, formWidth, previewLeft, previewWidth;
    private record Caption(String key, int x, int y) {}
    private record Draft(String title, String label, String icon, String heading,
                         List<AnecdoteDefinition.Record> records, List<QuestDefinition.Reward> rewards,
                         AnecdoteRewardLayout rewardLayout, boolean discovered,
                         boolean hidden, AnecdoteCardLayout layout) {}

    static String nextRecordId(AnecdoteDefinition entry) { return nextRecordId(entry, entry.records()); }
    static String nextRecordId(AnecdoteDefinition entry, List<AnecdoteDefinition.Record> records) {
        for (int index = 1; index <= AnecdoteDefinition.MAX_RECORDS; index++) {
            String next = entry.id() + "_record_" + index;
            if (records.stream().noneMatch(record -> record.id().equals(next))) return next;
        }
        throw new IllegalStateException("Anecdote record limit reached");
    }
    AnecdoteEntryEditorScreen(Screen parent, AnecdoteDefinition entry, boolean creating) {
        super(Component.translatable("screen.xero_quest_journal.anecdote." + (creating ? "create_entry" : "edit_entry")));
        this.parent = parent; this.creating = creating;
        id = entry.id(); order = entry.order(); titleValue = entry.title(); labelValue = entry.cardText();
        iconValue = entry.icon(); headingValue = entry.heading(); records = entry.records(); rewards = entry.rewards();
        defaultDiscovered = entry.defaultDiscovered(); hideWhenLocked = entry.hideWhenLocked();
        layout = entry.cardLayout(); rewardLayout = entry.rewardLayout();
    }
    private Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.anecdote." + key, args);
    }
    private Draft snapshot() {
        return new Draft(titleValue, labelValue, iconValue, headingValue, records, rewards, rewardLayout,
                defaultDiscovered, hideWhenLocked, layout);
    }
    private void edit(Runnable action) {
        Draft previous = snapshot(); action.run();
        if (!previous.equals(snapshot())) { undo.push(previous); trimHistory(); redo.clear(); }
    }
    private void trimHistory() { while (undo.size() > 100) undo.removeLast(); }
    private void restore(Deque<Draft> from, Deque<Draft> to) {
        if (from.isEmpty()) return;
        to.push(snapshot()); Draft value = from.pop();
        titleValue = value.title(); labelValue = value.label(); iconValue = value.icon(); headingValue = value.heading();
        records = value.records(); rewards = value.rewards();
        defaultDiscovered = value.discovered(); hideWhenLocked = value.hidden(); layout = value.layout();
        init();
    }
    private int bodyTop() { return 76; }
    private int bodyBottom() { return height - 44; }

    @Override protected void init() {
        if (!ClientEditMode.enabled() || minecraft.player == null || !minecraft.player.hasPermissions(2)) { onClose(); return; }
        clearWidgets(); formPositions.clear(); captions.clear(); numbers.clear();
        previewLeft = 12; previewWidth = Math.max(110, width * 52 / 100 - 24);
        formLeft = previewLeft + previewWidth + 18; formWidth = Math.max(120, width - formLeft - 24);
        int half = (formWidth - 6) / 2;
        for (int i = 0; i < 2; i++) {
            String next = i == 0 ? "content" : "appearance";
            var button = new StyledButton(formLeft + i * (half + 6), 42, half, 24, text("layout." + next),
                    ignored -> { tab = next; scroll.jump(0); init(); }, styles, "anecdote-tab-" + next);
            button.setSelectionHighlighted(tab.equals(next)); addRenderableWidget(button);
        }
        int y = 0;
        if (tab.equals("content")) {
            input("title_label", titleValue, 128, y, formWidth, value -> edit(() -> titleValue = value)); y += 44;
            input("card_text_label", labelValue, 128, y, formWidth - 30, value -> edit(() -> labelValue = value));
            form(new EditorIconButton(formLeft + formWidth - 24, bodyTop() + y + 13, text("card_text_label"),
                    () -> minecraft.setScreen(new QuestTextEditorScreen(this, labelValue,
                            value -> edit(() -> labelValue = value), 128)), styles, "anecdote-label-edit", "edit")); y += 44;
            input("icon_label", iconValue, 2048, y, formWidth - 30, value -> edit(() -> iconValue = value));
            form(new EditorIconButton(formLeft + formWidth - 24, bodyTop() + y + 13, text("icon_label"),
                    () -> minecraft.setScreen(QuestScreen.pickResource(this, iconValue, false,
                            value -> edit(() -> iconValue = value))), styles, "anecdote-icon-pick", "item")); y += 44;
            input("heading_label", headingValue, 256, y, formWidth, value -> edit(() -> headingValue = value)); y += 48;
            form(Checkbox.builder(text("default_discovered"), font).pos(formLeft, bodyTop() + y)
                    .selected(defaultDiscovered).onValueChange((box, value) -> edit(() -> defaultDiscovered = value)).build()); y += 30;
            form(Checkbox.builder(text("hide_locked"), font).pos(formLeft, bodyTop() + y)
                    .selected(hideWhenLocked).onValueChange((box, value) -> edit(() -> hideWhenLocked = value)).build()); y += 34;
            form(new StyledButton(formLeft, bodyTop() + y, formWidth, 24, text("records", records.size()),
                    ignored -> minecraft.setScreen(new AnecdoteRecordsEditorScreen(this, draft(),
                            next -> edit(() -> records = List.copyOf(next)))), styles, "anecdote-records")); y += 32;
            form(new StyledButton(formLeft, bodyTop() + y, formWidth, 24, text("entry_rewards", rewards.size()),
                    ignored -> minecraft.setScreen(new QuestRewardsScreen(this, rewards, "manual",
                            (next, mode) -> {
                                edit(() -> rewards = List.copyOf(next));
                                init();
                            }, true)), styles, "anecdote-rewards")); y += 32;
        } else {
            form(new StyledChoiceButton<>(formLeft, bodyTop() + y, formWidth, 24, text("layout.element"),
                    List.of("background", "image", "text"), selected, key -> text("layout." + key),
                    key -> { selected = key; init(); }, styles, "anecdote-element")); y += 35;
            Part part = layout.part(selected);
            number("x", part.x(), y, 0, half); number("y", part.y(), y, half + 6, half); y += 44;
            number("width", part.width(), y, 0, half); number("height", part.height(), y, half + 6, half); y += 44;
            number("scale", part.scale() * 100, y, 0, formWidth); y += 44;
            if (selected.equals("text")) {
                number("font_size", layout.fontSize(), y, 0, formWidth); y += 44;
                input("layout.text_color", layout.textColor(), 9, y, formWidth,
                        value -> edit(() -> appearance(null, null, value, layout.fontSize()))); y += 44;
            }
            if (selected.equals("background")) {
                input("layout.background_image", layout.backgroundImage(), 2048, y, formWidth - 30,
                        value -> edit(() -> appearance(value, null, null, layout.fontSize())));
                form(new EditorIconButton(formLeft + formWidth - 24, bodyTop() + y + 13, text("layout.background_image"),
                        () -> minecraft.setScreen(QuestScreen.pickResource(this, layout.backgroundImage(), false,
                                value -> edit(() -> appearance(value, null, null, layout.fontSize())))),
                        styles, "anecdote-background-pick", "item")); y += 44;
                input("layout.background_color", layout.backgroundColor(), 9, y, formWidth,
                        value -> edit(() -> appearance(null, value, null, layout.fontSize()))); y += 44;
            }
            form(new StyledButton(formLeft, bodyTop() + y, formWidth, 24, text("layout.reset"),
                    ignored -> { edit(() -> layout = layout.withPart(selected, AnecdoteCardLayout.defaults().part(selected))); init(); },
                    styles, "anecdote-layout-reset")); y += 32;
        }
        scroll.limit(y - (bodyBottom() - bodyTop()));
        addRenderableWidget(new ScrollRail(width - 16, bodyTop(), bodyBottom() - bodyTop(), scroll));
        addRenderableWidget(new EditorIconButton(12, 10, Component.translatable("screen.xero_quest_journal.layout.rich_undo"),
                () -> restore(undo, redo), styles, "anecdote-undo", "undo").dark());
        addRenderableWidget(new EditorIconButton(42, 10, Component.translatable("screen.xero_quest_journal.layout.rich_redo"),
                () -> restore(redo, undo), styles, "anecdote-redo", "redo").dark());
        addRenderableWidget(new EditorIconButton(width - 36, 10, Component.translatable("screen.xero_quest_journal.close"),
                this::onClose, styles, "anecdote-entry-close", "close").dark());
        addRenderableWidget(new StyledButton(width - 158, height - 32, 68, 24, text("save"), ignored -> save(), styles, "anecdote-save"));
        addRenderableWidget(new StyledButton(width - 80, height - 32, 68, 24, text("cancel"), ignored -> onClose(), styles, "anecdote-cancel"));
        if (!creating) addRenderableWidget(new EditorIconButton(12, height - 32, text("delete"),
                () -> minecraft.setScreen(new QuestConfirmScreen(this, text("delete_confirm"), () -> {
                    AnecdoteClientState.delete(id); minecraft.setScreen(parent);
                })), styles, "anecdote-delete", "delete").dark());
        positionForm();
    }
    private void appearance(String image, String background, String color, double size) {
        layout = new AnecdoteCardLayout(layout.background(), layout.image(), layout.text(),
                image == null ? layout.backgroundImage() : image, background == null ? layout.backgroundColor() : background,
                color == null ? layout.textColor() : color, size);
    }
    private void form(AbstractWidget widget) {
        formPositions.put(widget, widget.getY()); addRenderableWidget(widget);
    }
    private void input(String key, String initial, int max, int y, int w, Consumer<String> changed) {
        captions.add(new Caption(key, formLeft, bodyTop() + y));
        EditBox field = new EditBox(font, formLeft, bodyTop() + y + 13, w, 22, text(key));
        field.setMaxLength(max); field.setValue(initial); field.setResponder(changed); form(field);
    }
    private static String numeric(double value) { return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", ""); }
    private void number(String key, double initial, int y, int offset, int w) {
        captions.add(new Caption("layout." + key, formLeft + offset, bodyTop() + y));
        EditBox field = new EditBox(font, formLeft + offset, bodyTop() + y + 13, w, 22, text("layout." + key));
        field.setMaxLength(12); field.setValue(numeric(initial));
        field.setFilter(value -> value.matches("-?\\d*(\\.\\d*)?"));
        field.setResponder(value -> {
            if (syncing) return;
            try { double parsed = Double.parseDouble(value); edit(() -> setNumber(key, parsed)); }
            catch (NumberFormatException ignored) {}
        });
        numbers.put(key, field); form(field);
    }
    private void setNumber(String key, double value) {
        Part p = layout.part(selected);
        if (key.equals("font_size")) appearance(null, null, null, value);
        else layout = layout.withPart(selected, new Part(key.equals("x") ? value : p.x(), key.equals("y") ? value : p.y(),
                key.equals("width") ? value : p.width(), key.equals("height") ? value : p.height(),
                key.equals("scale") ? value / 100 : p.scale()));
    }
    private void syncNumbers() {
        syncing = true;
        try {
            Part p = layout.part(selected);
            Map<String, Double> values = Map.of("x", p.x(), "y", p.y(), "width", p.width(), "height", p.height(),
                    "scale", p.scale() * 100, "font_size", layout.fontSize());
            numbers.forEach((key, field) -> field.setValue(numeric(values.get(key))));
        } finally { syncing = false; }
    }
    private void positionForm() {
        formPositions.forEach((widget, y) -> widget.setY(y - scroll.pixels()));
    }
    private AnecdoteDefinition draft() {
        return new AnecdoteDefinition(id, titleValue, labelValue, iconValue, headingValue, records,
                defaultDiscovered, hideWhenLocked, order, rewards, layout, rewardLayout);
    }
    private void save() {
        try { AnecdoteClientState.clearResult(); AnecdoteClientState.save(draft()); closeTo(parent); }
        catch (RuntimeException invalid) { XeroTips.show(text("save_error")); }
    }
    private double previewScale() { return Math.max(.1, Math.min((previewWidth - 32) / 100., (height - 118) / 125.)); }
    private double previewX() { return previewLeft + (previewWidth - 100 * previewScale()) / 2; }
    private double previewY() { return 66 + (height - 118 - 125 * previewScale()) / 2; }
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        scroll.advance(); positionForm();
        backdrop(g, 0xFF20262D);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(40, width - 135)), 78, 18, 0xFFF0DCA9, false);
        g.fill(previewLeft, 42, previewLeft + previewWidth, height - 44, 0xFF303941);
        double scale = previewScale();
        AnecdoteCardRenderer.render(g, iconValue, labelValue, layout, (int) previewX(), (int) previewY(),
                (int) Math.round(100 * scale), (int) Math.round(125 * scale), true);
        g.enableScissor(previewLeft, 42, previewLeft + previewWidth, height - 44);
        var box = AnecdoteCardRenderer.bounds(layout.part(selected));
        int x = (int) Math.round(previewX() + box.x() * scale), y = (int) Math.round(previewY() + box.y() * scale);
        int w = Math.max(1, (int) Math.round(box.width() * scale)), h = Math.max(1, (int) Math.round(box.height() * scale));
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        g.renderOutline(x, y, w, h, 0xFF76D7B8);
        for (int[] point : List.of(new int[]{x + w, y + h / 2}, new int[]{x + w / 2, y + h}, new int[]{x + w, y + h}))
            g.fill(point[0] - 3, point[1] - 3, point[0] + 4, point[1] + 4, 0xFFE7CC8C);
        g.pose().popPose(); g.disableScissor();
        g.enableScissor(formLeft, bodyTop(), width - 24, bodyBottom());
        for (Caption caption : captions) g.drawString(font, text(caption.key()), caption.x(), caption.y() - scroll.pixels(), 0xFFB9CDD2, false);
        g.disableScissor();
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast).forEach(button -> {
            if (button.styleId().equals("anecdote-undo")) button.active = !undo.isEmpty();
            if (button.styleId().equals("anecdote-redo")) button.active = !redo.isEmpty();
        });
        super.renderContents(g, mx, my, delta);
    }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return formPositions.containsKey(widget) ? viewport(formLeft, bodyTop(), width - 24, bodyBottom()) : null;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (x >= previewLeft && x < previewLeft + previewWidth && y >= 42 && y < height - 44 && button == 0) {
            double px = (x - previewX()) / previewScale(), py = (y - previewY()) / previewScale();
            var selectedBox = AnecdoteCardRenderer.bounds(layout.part(selected));
            double tolerance = 6 / previewScale();
            boolean right = Math.abs(px - selectedBox.right()) <= tolerance;
            boolean bottom = Math.abs(py - selectedBox.bottom()) <= tolerance;
            if ((right && py >= selectedBox.y() && py <= selectedBox.bottom() + tolerance)
                    || (bottom && px >= selectedBox.x() && px <= selectedBox.right() + tolerance))
                dragMode = right && bottom ? "scale" : right ? "width" : "height";
            else {
                String hit = List.of("text", "image", "background").stream()
                        .filter(slot -> AnecdoteCardRenderer.bounds(layout.part(slot)).contains(px, py)).findFirst().orElse("");
                if (hit.isBlank()) return true;
                selected = hit; dragMode = "move";
            }
            dragBefore = snapshot(); dragX = px; dragY = py;
            if (!tab.equals("appearance")) { tab = "appearance"; scroll.jump(0); }
            init(); setFocused(null); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragBefore != null && !dragMode.isBlank()) {
            Part p = dragBefore.layout().part(selected);
            var box = AnecdoteCardRenderer.bounds(p);
            double deltaX = (x - previewX()) / previewScale() - dragX;
            double deltaY = (y - previewY()) / previewScale() - dragY;
            Part next;
            if (dragMode.equals("move")) next = new Part(p.x() + deltaX, p.y() + deltaY / 1.25, p.width(), p.height(), p.scale());
            else if (dragMode.equals("scale")) {
                double factor = Math.max(.1, ((box.width() + deltaX) * box.width()
                        + (box.height() + deltaY) * box.height()) / (box.width() * box.width() + box.height() * box.height()));
                next = new Part(p.x(), p.y(), p.width(), p.height(), p.scale() * factor);
            } else {
                double nw = dragMode.equals("width") ? Math.max(1, p.width() + deltaX / p.scale()) : p.width();
                double nh = dragMode.equals("height") ? Math.max(1, p.height() + deltaY / 1.25 / p.scale()) : p.height();
                next = new Part(p.x() - (nw - p.width()) * (1 - p.scale()) / 2,
                        p.y() - (nh - p.height()) * (1 - p.scale()) / 2, nw, nh, p.scale());
            }
            layout = layout.withPart(selected, next); syncNumbers(); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragBefore != null) {
            if (!dragBefore.equals(snapshot())) { undo.push(dragBefore); trimHistory(); redo.clear(); }
            dragBefore = null; dragMode = ""; return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inputBlocked()) return true;
        if (scrollChoicePopup(x, y, h, v)) return true;
        if (x >= formLeft && y >= bodyTop() && y < bodyBottom()) { scroll.wheel(v * 28); return true; }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_Z) { restore(undo, redo); return true; }
            if (key == GLFW.GLFW_KEY_Y) { restore(redo, undo); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
