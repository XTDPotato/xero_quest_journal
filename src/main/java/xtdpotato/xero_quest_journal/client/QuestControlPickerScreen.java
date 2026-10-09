package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestControlTarget;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestPresentation;
import java.util.*;
import java.util.function.Consumer;

/** Read-only task canvases; confirmation is the only operation that publishes a selection. */
public final class QuestControlPickerScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final Consumer<List<QuestControlTarget>> saved;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final Set<QuestControlTarget> selected = new LinkedHashSet<>();
    private final Map<AbstractWidget, Integer> rows = new LinkedHashMap<>();
    private final SmoothScroll listMotion = new SmoothScroll(), canvasMotion = new SmoothScroll();
    private String taskId, query = "", pressed = "";
    private QuestScreen preview;
    private StyledButton confirm;

    public QuestControlPickerScreen(Screen parent, String taskId, List<QuestControlTarget> targets,
                                    Consumer<List<QuestControlTarget>> saved) {
        super(text("picker.title"));
        this.parent = parent; this.taskId = taskId; this.saved = saved;
        selected.addAll(targets);
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.graph." + key);
    }
    private int split() { return Math.min(240, width / 3); }
    private Viewport listViewport() { return viewport(16, 68, split() - 12, height - 44); }
    private Viewport canvasViewport() { return viewport(split() + 12, 60, width - 20, height - 48); }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return rows.containsKey(widget) ? listViewport() : null;
    }
    @Override protected void init() {
        clearWidgets(); rows.clear();
        var search = new EditBox(font, 16, 38, split() - 32, 20, text("picker.search"));
        search.setHint(text("picker.search")); search.setValue(query);
        search.setResponder(value -> { query = value; listMotion.jump(0); rebuildRows(); });
        addRenderableWidget(search);
        confirm = addRenderableWidget(new JournalNavButton(width - 156, height - 32, 140, 24,
                text("picker.confirm"), ignored -> {
            saved.accept(List.copyOf(selected)); minecraft.setScreen(parent);
        }, styles, "control-picker-confirm", JournalNavButton.Kind.ACTION, "choose"));
        addRenderableWidget(new JournalNavButton(width - 40, 8, 24, 24, Component.translatable("gui.cancel"),
                ignored -> onClose(), styles, "control-picker-close", JournalNavButton.Kind.ICON, "close"));
        addRenderableWidget(new JournalNavButton(16, height - 32, 24, 24, text("picker.clear"),
                ignored -> { selected.clear(); confirm.active = false; },
                styles, "control-picker-clear", JournalNavButton.Kind.ICON, "delete"));
        rebuildRows();
        addRenderableWidget(new ScrollRail(split() - 9, 68, height - 112, listMotion));
        addRenderableWidget(new ScrollRail(width - 12, 60, height - 108, canvasMotion));
    }
    private void rebuildRows() {
        rows.keySet().forEach(this::removeWidget); rows.clear();
        var tasks = ClientQuestDefinitions.all().stream().filter(task ->
                (task.id() + "\n" + QuestText.title(task).getString()).toLowerCase(Locale.ROOT)
                        .contains(query.strip().toLowerCase(Locale.ROOT))).toList();
        if (ClientQuestDefinitions.find(taskId) == null) taskId = tasks.isEmpty() ? "" : tasks.getFirst().id();
        int index = 0;
        for (var task : tasks) {
            int y = 68 + index++ * 26;
            var row = new JournalListButton(16, y, split() - 32, 24, QuestText.title(task),
                    ignored -> { taskId = task.id(); canvasMotion.jump(0); updatePreview(); rebuildRows(); },
                    styles, "control-picker-task-" + task.id(), task.leadingIcon(), task.trailingIcon(),
                    task.type(), false, false, false, false, "", null);
            row.setSelectionHighlighted(task.id().equals(taskId));
            row.clipTo(16, 68, split() - 12, height - 44);
            rows.put(addRenderableWidget(row), y);
        }
        listMotion.limit(index * 26 - (height - 112));
        updatePreview(); confirm.active = !selected.isEmpty();
    }
    private void updatePreview() {
        var quest = ClientQuestDefinitions.find(taskId);
        if (quest == null) { preview = null; return; }
        if (preview != null) preview.removed();
        preview = QuestScreen.taskControlPreview(quest);
        preview.init(minecraft, width, height);
        canvasMotion.limit(preview.templatePreviewMaxScroll(canvasViewport()));
    }
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        listMotion.advance(); canvasMotion.advance();
        rows.forEach((row, y) -> { row.setY(y - listMotion.pixels()); row.visible = listViewport().intersects(row); });
        super.renderContents(g, mx, my, delta);
        g.drawString(font, title, 16, 14, 0xFFF2D29A);
        var task = ClientQuestDefinitions.find(taskId);
        if (preview != null && task != null) {
            g.drawString(font, font.plainSubstrByWidth(QuestText.title(task).getString(),
                    width - split() - 60), split() + 16, 40, 0xFFF2D29A);
            preview.renderTemplatePreview(g, canvasViewport(), canvasMotion.position(), mx, my, "");
            var ids = new HashSet<String>();
            selected.stream().filter(t -> t.quest().equals(taskId)).forEach(t -> ids.add(t.control()));
            preview.renderControlSelection(g, ids);
        }
        g.drawString(font, Component.translatable("screen.xero_quest_journal.graph.picker.count",
                selected.size()), 52, height - 24, 0xFFD5DEE8);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && preview != null && canvasViewport().contains(x, y)) {
            pressed = preview.templatePreviewHit(x, y); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && !pressed.isBlank()) {
            String id = pressed; pressed = "";
            if (preview != null && id.equals(preview.templatePreviewHit(x, y))) {
                var target = new QuestControlTarget(taskId, id);
                if (!selected.remove(target) && selected.size() < 128) selected.add(target);
                confirm.active = !selected.isEmpty();
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (listViewport().contains(x, y)) { listMotion.wheel(vertical * 36); return true; }
        if (canvasViewport().contains(x, y)) { canvasMotion.wheel(vertical * 36); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        super.renderBackground(g, x, y, delta); backdrop(g, JOURNAL_BACKDROP);
        g.fill(0, 0, width, 32, JOURNAL_HEADER);
        g.fill(split(), 34, split() + 1, height - 42, 0x408FA5AA);
        MailDecor.divider(g, 16, width - 16, height - 40, 0x408FA5AA);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public void removed() {
        if (preview != null) preview.removed();
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
