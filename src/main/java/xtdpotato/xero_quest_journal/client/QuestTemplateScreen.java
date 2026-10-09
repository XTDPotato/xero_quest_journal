package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.QuestTemplateStore;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class QuestTemplateScreen extends AnimatedQuestScreen {
    @Override protected int animationHeaderBottom() { return 52; }
    @Override protected int animationFooterTop() { return height - 40; }

    private final QuestScreen parent;
    private final String targetGroup;
    private final xtdpotato.xero_quest_journal.core.io.QuestJournalOrderStore.Placement placement;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Set<String> collapsed = new HashSet<>();
    private EditBox search;
    private List<QuestTemplate> templates = List.of(), filtered = List.of();
    private final List<StyledButton> rows = new ArrayList<>(), menu = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private String activeId;
    private double scroll, previewScroll;
    private final SmoothScroll rowMotion = new SmoothScroll(), previewMotion = new SmoothScroll();
    private int dragPane = -1;
    private boolean dragMoved, draggingScroll, draggingPreview;
    private double dragX, dragY, dragStart;
    private StyledButton use, delete;
    private CompletableFuture<String> pendingDelete;
    private List<String> deletingIds = List.of();
    private QuestScreen preview;
    private QuestTemplate previewed;

    private record Entry(String group, QuestTemplate template) {}

    public QuestTemplateScreen(QuestScreen parent, String targetGroup) {
        this(parent, targetGroup, null);
    }

    public QuestTemplateScreen(QuestScreen parent, String targetGroup,
            xtdpotato.xero_quest_journal.core.io.QuestJournalOrderStore.Placement placement) {
        super(QuestTemplateEditorScreen.text("title"));
        this.parent = parent;
        this.targetGroup = targetGroup;
        this.placement = placement;
        activeId = ClientPreferences.get().lastTaskTemplate;
    }

    @Override protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets();
        rows.clear(); menu.clear();
        templates = ClientQuestDefinitions.templates().stream().filter(t -> !t.archived()).toList();
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, 18, 28, Math.max(80, divider() - 34), 20,
                QuestTemplateEditorScreen.text("search"));
        search.setValue(query);
        search.setHint(QuestTemplateEditorScreen.text("search"));
        search.setResponder(value -> { rowMotion.jump(0); scroll = 0; rebuild(); });
        addRenderableWidget(search);
        addRenderableWidget(new JournalNavButton(18, height - 32, Math.max(72, divider() - 60), 24,
                QuestTemplateEditorScreen.text("add"),
                ignored -> minecraft.setScreen(new QuestTemplateEditorScreen(this, null)),
                styles, "template-add", JournalNavButton.Kind.ACTION, "add"));
        delete = addRenderableWidget(new JournalNavButton(divider() - 36, height - 32, 24, 24,
                QuestTemplateEditorScreen.text("delete"),
                ignored -> confirmDelete(new ArrayList<>(selected)), styles,
                "template-delete-selected", JournalNavButton.Kind.ICON, "delete"));
        addRenderableWidget(new JournalNavButton(width - 40, 12, 24, 24,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles,
                "template-close", JournalNavButton.Kind.ICON, "close"));
        use = addRenderableWidget(new JournalNavButton(width - 144, height - 32, 128, 24,
                QuestTemplateEditorScreen.text("use"), ignored -> templates.stream()
                .filter(t -> t.id().equals(activeId)).findFirst().ifPresent(template -> {
                    parent.openTaskTemplate(template, targetGroup, placement);
                    minecraft.setScreen(parent);
                }), styles, "template-use", JournalNavButton.Kind.ACTION, "choose").compactContent());
        previewed = null;
        rebuild();
        int remembered = -1;
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).template() != null && entries.get(i).template().id().equals(activeId)) remembered = i;
        if (remembered >= 0 && (remembered * 26 < rowMotion.position()
                || remembered * 26 + 26 > rowMotion.position() + height - 102)) {
            rowMotion.jump(Math.clamp(remembered * 26., 0, maxScroll()));
            moveRows();
        }
    }

    private void updateDeleteButton() {
        int count = (int) selected.stream().filter(QuestTemplateStore::isDeletableId).count();
        delete.active = pendingDelete == null && parent.canAuthor() && count > 0;
        delete.setTooltip(Tooltip.create(Component.translatable(
                "screen.xero_quest_journal.template.delete_selected", count)));
    }

    private void rebuild() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = templates.stream().filter(template -> query.isBlank()
                || (template.id() + "\n" + template.name() + "\n" + template.description()
                + "\n" + groupLabel(template.group())).toLowerCase(Locale.ROOT).contains(query)).toList();
        rows.forEach(this::removeWidget); rows.clear(); entries.clear();
        if (filtered.stream().noneMatch(t -> t.id().equals(activeId))) {
            activeId = filtered.isEmpty() ? "" : filtered.getFirst().id();
            previewScroll = 0;
        }
        var groups = new LinkedHashMap<String, List<QuestTemplate>>();
        filtered.stream().sorted(Comparator.comparingInt((QuestTemplate t) -> groupOrder(t.group()))
                        .thenComparing(QuestTemplate::group))
                .forEach(t -> groups.computeIfAbsent(t.group(), ignored -> new ArrayList<>()).add(t));
        groups.forEach((group, values) -> {
            entries.add(new Entry(group, null));
            if (!collapsed.contains(group) || !query.isBlank())
                values.forEach(t -> entries.add(new Entry(group, t)));
        });
        use.active = !activeId.isBlank() && parent.canAuthor() && pendingDelete == null;
        updateDeleteButton();
        rowMotion.limit(maxScroll());
        rowMotion.jump(Math.clamp(scroll, 0, maxScroll()));
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            QuestTemplate template = entry.template();
            boolean header = template == null;
            int x = header ? 18 : 24;
            int w = divider() - x - 24;
            JournalListButton row = new JournalListButton(x, 58 + i * 26 - rowMotion.pixels(), w, 24,
                    Component.literal(header ? groupLabel(entry.group()) : template.name()), ignored -> {
                if (header) {
                    if (!collapsed.add(entry.group())) collapsed.remove(entry.group());
                    rebuild();
                } else choose(template);
            }, styles, header ? "template-group-" + entry.group() : "template-" + template.id(),
                    ListIconConfig.leading(header ? groupIcon(entry.group()) : template.icon()),
                    header ? ListIconConfig.trailing() : new ListIconConfig("", "", false, false, 1, 14, 14, 0, 0),
                    "", header, !collapsed.contains(entry.group()), false, false, "", null);
            row.setSelectionHighlighted(!header && (selected.contains(template.id()) || activeId.equals(template.id())));
            if (!header) row.setTooltip(Tooltip.create(Component.literal(template.id() + "\n" + template.description())));
            row.clipTo(18, 58, divider() - 14, height - 44);
            rows.add(addRenderableWidget(row));
        }
        updatePreview();
        moveRows();
    }

    private static int groupOrder(String group) {
        int order = List.of("main", "legendary", "commission", "world").indexOf(group);
        return order < 0 ? 4 : order;
    }

    private static String groupLabel(String group) {
        return ClientQuestDefinitions.types().stream().filter(type -> type.id().equals(group))
                .map(xtdpotato.xero_quest_journal.core.model.QuestType::name).findFirst().orElse(group);
    }

    private static String groupIcon(String group) {
        return switch (group) {
            case "main" -> "minecraft:gold_ingot"; case "legendary" -> "minecraft:amethyst_shard";
            case "commission" -> "minecraft:writable_book"; case "world" -> "minecraft:grass_block";
            default -> "minecraft:bundle";
        };
    }

    private void choose(QuestTemplate template) {
        if (hasControlDown()) {
            if (!selected.add(template.id())) selected.remove(template.id());
        } else {
            if (!activeId.equals(template.id())) { previewScroll = 0; previewMotion.jump(0); }
            activeId = template.id(); selected.clear(); selected.add(template.id());
            ClientPreferences.get().lastTaskTemplate = activeId;
            ClientPreferences.get().save();
        }
        rebuild();
    }

    private void updatePreview() {
        var template = templates.stream().filter(t -> t.id().equals(activeId)).findFirst().orElse(null);
        if (template == null) { preview = null; previewed = null; return; }
        if (!template.equals(previewed)) {
            if (preview != null) preview.removed();
            preview = QuestScreen.templatePreview(template);
            preview.init(minecraft, width, height);
            previewed = template;
        }
        previewMotion.limit(maxPreviewScroll());
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (!menu.isEmpty()) {
            for (StyledButton entry : List.copyOf(menu)) if (entry.mouseClicked(x, y, button)) return true;
            menu.clear(); return true;
        }
        if (button == 0 && maxScroll() > 0 && x >= divider() - 17 && x < divider() - 9
                && y >= 58 && y < height - 44) {
            draggingScroll = true; scrollTo(y); return true;
        }
        if (button == 0 && maxPreviewScroll() > 0 && x >= width - 25 && x < width - 15
                && y >= previewViewport().top() && y < previewViewport().bottom()) {
            draggingPreview = true; scrollPreviewTo(y); return true;
        }
        if (button == 1) for (int i = 0; i < rows.size(); i++)
            if (rows.get(i).visible && rows.get(i).isMouseOver(x, y) && entries.get(i).template() != null) {
                showMenu((int) x, (int) y, entries.get(i).template()); return true;
            }
        if (button == 0 && (x >= 18 && x < divider() - 18 && y >= 58 && y < height - 44
                || previewViewport().contains(x, y))) {
            dragPane = x < divider() ? 0 : 1; dragMoved = false; dragX = x; dragY = y;
            var motion = dragPane == 0 ? rowMotion : previewMotion;
            dragStart = motion.position(); motion.freeze(); return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void showMenu(int x, int y, QuestTemplate template) {
        menu.clear();
        int left = Math.clamp(x, 8, Math.max(8, width - 190));
        int top = Math.clamp(y, 8, Math.max(8, height - 68));
        menu.add(menuButton(left, top, "edit", () -> minecraft.setScreen(new QuestTemplateEditorScreen(this, template))));
        menu.add(menuButton(left, top + 20, "select", () -> {
            if (!selected.add(template.id())) selected.remove(template.id());
            menu.clear(); rebuild();
        }));
        var remove = menuButton(left, top + 40, "delete", () -> confirmDelete(List.of(template.id())));
        remove.active = parent.canAuthor() && pendingDelete == null; menu.add(remove);
    }

    private void confirmDelete(List<String> ids) {
        var deletable = ids.stream().filter(QuestTemplateStore::isDeletableId).distinct().toList();
        if (deletable.isEmpty() || !parent.canAuthor() || pendingDelete != null) return;
        minecraft.setScreen(new QuestConfirmScreen(this, Component.translatable(
                "screen.xero_quest_journal.template.delete_confirm", deletable.size()),
                QuestTemplateEditorScreen.text("delete_confirm_button"), () -> deleteTemplates(deletable)));
    }

    private void deleteTemplates(List<String> ids) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null || !minecraft.player.hasPermissions(2)) return;
        var playerId = minecraft.player.getUUID();
        deletingIds = List.copyOf(ids);
        pendingDelete = CompletableFuture.supplyAsync(() ->
                QuestTemplateStore.delete(server.getPlayerList().getPlayer(playerId), deletingIds), server);
        updateDeleteButton();
    }

    @Override public void tick() {
        if (pendingDelete == null || !pendingDelete.isDone()) return;
        String result;
        try { result = pendingDelete.join(); } catch (RuntimeException failure) { result = "delete"; }
        pendingDelete = null;
        if (result.isEmpty()) {
            templates = ClientQuestDefinitions.templates().stream().filter(t -> !t.archived()).toList();
            selected.removeAll(deletingIds);
            XeroTips.show(Component.translatable("screen.xero_quest_journal.template.deleted", deletingIds.size()));
        } else XeroTips.show(QuestTemplateEditorScreen.text("delete_error"));
        deletingIds = List.of(); rebuild();
    }

    private StyledButton menuButton(int x, int y, String key, Runnable action) {
        return new StyledButton(x, y, 180, 20, QuestTemplateEditorScreen.text(key),
                ignored -> action.run(), styles, "template-menu-" + key, "context");
    }

    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!menu.isEmpty()) { rowMotion.freeze(); previewMotion.freeze(); }
        rowMotion.advance(); previewMotion.advance(); moveRows();
        super.renderContents(g, mouseX, mouseY, partialTick);
        JournalNavButton.drawIcon(g, "other", 18, 7, 18, 0xFFF2D29A);
        g.drawString(font, title, 44, 12, 0xFFF2D29A);
        if (filtered.isEmpty()) g.drawString(font, QuestTemplateEditorScreen.text("empty"), 24, 70, 0xFFB8C7D9);
        if (preview != null) {
            g.drawString(font, QuestTemplateEditorScreen.text("preview"), divider() + 16, 60, 0xFFD5DEE8);
            preview.renderTemplatePreview(g, previewViewport(), previewScroll, mouseX, mouseY, "");
            g.drawString(font, font.plainSubstrByWidth("ID: " + activeId, Math.max(1, width - divider() - 40)),
                    divider() + 16, height - 57, 0xFFB8C7D9);
            drawRail(g, width - 22, previewViewport().top(), previewViewport().bottom(), previewScroll, maxPreviewScroll());
        }
        drawRail(g, divider() - 15, 58, height - 44, scroll, maxScroll());
        renderAnimatedMenu(g, "context", menu.isEmpty() ? null : menu.getFirst(),
                menu.isEmpty() ? viewport(0, 0, 0, 0) : viewport(menu.getFirst().getX(),
                        menu.getFirst().getY(), menu.getFirst().getRight(), menu.getLast().getBottom()),
                () -> {
                    g.pose().pushPose(); g.pose().translate(0, 0, 300);
                    menu.forEach(button -> button.render(g, mouseX, mouseY, partialTick));
                    g.pose().popPose();
                });
    }

    private void drawRail(GuiGraphics g, int x, int top, int bottom, double value, double max) {
        if (max <= 0) return;
        int height = Math.max(1, bottom - top);
        int thumb = Math.max(12, (int) (height * height / (height + max)));
        int y = top + (int) ((height - thumb) * value / max);
        g.fill(x, top, x + 3, bottom, 0xFF555B64);
        g.fill(x, y, x + 3, y + thumb, 0xFFE5D2AC);
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        super.renderBackground(g, x, y, delta);
        backdrop(g, JOURNAL_BACKDROP);
        g.fill(0, 0, width, 52, JOURNAL_HEADER);
        g.fill(divider(), 52, divider() + 1, height - 42, 0x408FA5AA);
        MailDecor.divider(g, 16, width - 16, height - 40, 0x408FA5AA);
    }

    private int divider() { return Math.min(280, width * 2 / 5); }
    private double maxScroll() { return Math.max(0, entries.size() * 26 - (height - 102)); }
    private Viewport previewViewport() { return viewport(divider() + 16, 78, width - 28, Math.max(79, height - 76)); }
    private double maxPreviewScroll() { return preview == null ? 0 : preview.templatePreviewMaxScroll(previewViewport()); }
    private void scrollPreviewTo(double y) {
        var viewport = previewViewport();
        int h = viewport.bottom() - viewport.top();
        double thumb = Math.max(12, h * h / (h + maxPreviewScroll()));
        previewMotion.jump(Math.clamp((y - viewport.top() - thumb / 2) * maxPreviewScroll()
                / Math.max(1, h - thumb), 0, maxPreviewScroll())); moveRows();
    }
    private void scrollTo(double y) {
        int h = height - 102;
        double thumb = Math.max(12, h * h / (h + maxScroll()));
        rowMotion.jump(Math.clamp((y - 58 - thumb / 2) * maxScroll() / Math.max(1, h - thumb), 0, maxScroll()));
        moveRows();
    }
    private void moveRows() {
        scroll = rowMotion.position(); previewScroll = previewMotion.position();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); row.setY(58 + i * 26 - rowMotion.pixels());
            row.visible = row.getBottom() > 58 && row.getY() < height - 44;
        }
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragPane >= 0) {
            dragMoved |= Math.abs(y - dragY) >= 4;
            if (dragMoved) { (dragPane == 0 ? rowMotion : previewMotion).jump(dragStart + dragY - y); moveRows(); }
            return true;
        }
        if (draggingPreview) { scrollPreviewTo(y); return true; }
        if (draggingScroll) { scrollTo(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragPane >= 0) {
            int pane = dragPane; dragPane = -1;
            if (pane == 0 && !dragMoved && Math.abs(x - dragX) < 4 && y >= 58 && y < height - 44) {
                super.mouseClicked(dragX, dragY, button); super.mouseReleased(x, y, button);
            }
            return true;
        }
        draggingScroll = false; draggingPreview = false;
        for (var entry : List.copyOf(menu)) entry.mouseReleased(x, y, button);
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!menu.isEmpty()) return true;
        if (x >= 18 && x < divider() - 10 && y >= 58 && y < height - 44) {
            rowMotion.wheel(vertical * 36); return true;
        }
        if (previewViewport().contains(x, y)) { previewMotion.wheel(vertical * 36); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !menu.isEmpty()) {
            menu.clear();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public void removed() {
        if (preview != null) preview.removed();
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
