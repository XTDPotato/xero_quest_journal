package xtdpotato.xero_quest_journal.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructureLibrary;
import xtdpotato.xero_quest_journal.core.runtime.QuestStructures;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Local authoring library: imported files and templates available to the current world. */
public final class QuestStructurePickerScreen extends AnimatedQuestScreen {
    private record Copies(List<String> imported, int failed) {}
    private record Preview(String reference, CompoundTag data) {}
    private final Screen parent;
    private final Consumer<String> selected;
    private final Path root = QuestConfig.root().resolve("structures").toAbsolutePath().normalize();
    private final Set<String> marked = new LinkedHashSet<>();
    private List<String> local = List.of(), world = List.of(), visible = List.of();
    private boolean worldLoaded;
    private boolean worldTab;
    private int scroll;
    private final SmoothScroll motion = new SmoothScroll();
    private final List<net.minecraft.client.gui.components.AbstractWidget> rowWidgets = new ArrayList<>();
    private boolean listDragging, listMoved;
    private double startX, startY, startScroll;
    private String query = "", message = "";
    private CompletableFuture<?> pending;
    private ButtonStyleSheet styles;
    private String worldTarget;
    private boolean poolTarget;

    public static QuestStructurePickerScreen inspectTarget(Screen parent, String target) {
        var screen = new QuestStructurePickerScreen(parent, ignored -> {});
        screen.worldTarget = target;
        screen.worldTab = true;
        return screen;
    }

    public static QuestStructurePickerScreen inspectPool(Screen parent, String pool) {
        var screen = inspectTarget(parent, pool);
        screen.poolTarget = true;
        return screen;
    }

    public QuestStructurePickerScreen(Screen parent, Consumer<String> selected) {
        super(Component.translatable("screen.xero_quest_journal.structure.choose"));
        this.parent = parent;
        this.selected = selected;
    }

    private Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.structure." + key);
    }

    private int rowCount() { return Math.max(1, (height - 192) / 26); }

    private void refreshLocal() {
        try {
            Files.createDirectories(root);
            try (var paths = Files.walk(root, 8)) {
                local = paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                                && p.toString().endsWith(".nbt"))
                        .limit(1024).map(root::relativize).map(Path::toString).sorted().toList();
            }
        } catch (Exception failure) {
            local = List.of();
            message = text("invalid").getString();
        }
        filter();
    }

    private void refreshWorld() {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null || pending != null) return;
        UUID player = minecraft.player.getUUID();
        pending = CompletableFuture.supplyAsync(() ->
                worldTarget == null ? QuestStructureLibrary.worldTemplates(server.getPlayerList().getPlayer(player))
                        : poolTarget ? QuestStructureLibrary.poolTemplates(server.getPlayerList().getPlayer(player), worldTarget)
                        : QuestStructureLibrary.targetTemplates(server.getPlayerList().getPlayer(player), worldTarget), server);
    }

    private void filter() {
        String term = query.strip().toLowerCase(Locale.ROOT);
        visible = (worldTab ? world : local).stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(term)).toList();
        motion.limit(Math.max(0, visible.size() - rowCount()) * 26);
        motion.jump(scroll);
        scroll = motion.pixels();
    }

    @Override protected void init() {
        clearWidgets();
        styles = ButtonStyleSheet.load();
        if (worldTarget == null && local.isEmpty()) refreshLocal();
        filter();
        if (worldTab && !worldLoaded && pending == null) refreshWorld();
        int x = 16, w = Math.max(60, width - 32);
        if (worldTarget == null) {
            addRenderableWidget(new StyledButton(x, 34, (w - 4) / 2, 20, text("local"),
                b -> switchTab(false), styles, "structure-local"));
            addRenderableWidget(new StyledButton(x + (w + 4) / 2, 34, (w - 4) / 2, 20, text("world"),
                b -> switchTab(true), styles, "structure-world"));
        }
        var search = new EditBox(font, x, 60, w, 20, text("search"));
        search.setMaxLength(128);
        search.setValue(query);
        search.setHint(text("search"));
        search.setResponder(value -> { query = value; scroll = 0; filter(); rebuildRows(); });
        addRenderableWidget(search);
        rebuildRows();
        if (worldTarget != null) {
            addRenderableWidget(new EditorIconButton(width - 42, 8, Component.translatable("gui.back"),
                    this::onClose, styles, "structure-close", "close"));
            return;
        }
        int actionWidth = Math.max(45, (w - 12) / 3);
        addRenderableWidget(new StyledButton(x, height - 58, actionWidth, 22, text("folder"),
                b -> Util.getPlatform().openFile(root.toFile()), styles, "structure-folder"));
        var copy = new StyledButton(x + actionWidth + 6, height - 58, actionWidth, 22, text("copy"),
                b -> copyWorld(), styles, "structure-copy");
        copy.active = worldTab && pending == null && !marked.isEmpty();
        addRenderableWidget(copy);
        var delete = new StyledButton(x + 2 * (actionWidth + 6), height - 58,
                actionWidth, 22, text("delete"),
                b -> confirmDelete(), styles, "structure-delete");
        delete.active = !worldTab && pending == null && !marked.isEmpty();
        addRenderableWidget(delete);
        var choose = new StyledButton(width - 110, height - 32, 94, 22, text("use"),
                b -> {
                    selected.accept(marked.iterator().next());
                    minecraft.setScreen(parent);
                }, styles, "structure-use");
        choose.active = !worldTab && marked.size() == 1;
        addRenderableWidget(choose);
        var edit = new StyledButton(16, height - 84, Math.max(60, (w - 6) / 2), 20, text("edit"),
                b -> editCopy(), styles, "structure-edit-copy");
        edit.active = !worldTab && marked.size() == 1 && pending == null && minecraft.getSingleplayerServer() != null;
        addRenderableWidget(edit);
        var settings = new StyledButton(16 + (w + 6) / 2, height - 84, Math.max(60, (w - 6) / 2), 20, text("settings"),
                b -> minecraft.setScreen(new QuestSceneSettingsScreen(this, marked.iterator().next(), false)),
                styles, "structure-settings");
        settings.active = edit.active;
        addRenderableWidget(settings);
        addRenderableWidget(new StyledButton(width - 46, 8, 30, 20, Component.literal("\u00d7"),
                b -> onClose(), styles, "structure-close"));
    }

    private void switchTab(boolean worldTab) {
        this.worldTab = worldTab;
        marked.clear();
        scroll = 0;
        query = "";
        filter();
        init();
    }

    private void rebuildRows() {
        rowWidgets.clear();
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().startsWith("structure-row-")
                        || button.styleId().startsWith("structure-preview-"))
                .toList().forEach(this::removeWidget);
        for (int i = 0; i < visible.size(); i++) {
            String name = visible.get(i);
            int y = 86 + i * 26 - scroll;
            var row = new StyledButton(16, y, Math.max(36, width - 80), 22,
                    Component.literal((marked.contains(name) ? "[x] " : "[ ] ") + name),
                    b -> {
                        if (!marked.add(name)) marked.remove(name);
                        init();
                    }, styles, "structure-row-" + i);
            row.setTooltip(Tooltip.create(Component.literal(name)));
            rowWidgets.add(addRenderableWidget(row));
            rowWidgets.add(addRenderableWidget(new EditorIconButton(width - 42, y, text("preview"),
                    () -> {
                        if (!worldTab) {
                            ClientStructures.request("", "", name);
                            minecraft.setScreen(new QuestStructurePreviewScreen(this, name));
                        } else previewWorld(name);
                    }, styles, "structure-preview-" + i, "visual")));
        }
    }

    private void previewWorld(String name) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null || pending != null) return;
        UUID player = minecraft.player.getUUID();
        pending = CompletableFuture.supplyAsync(() -> new Preview("world:" + name,
                QuestStructureLibrary.previewTemplate(server.getPlayerList().getPlayer(player), name)), server);
        message = text("loading").getString();
    }

    private void copyWorld() {
        if (marked.isEmpty() || pending != null) return;
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return;
        UUID player = minecraft.player.getUUID();
        List<String> names = List.copyOf(marked);
        pending = CompletableFuture.supplyAsync(() -> {
            List<String> imported = new ArrayList<>();
            int failed = 0;
            for (String name : names) {
                try {
                    imported.add(QuestStructureLibrary.importTemplate(
                            server.getPlayerList().getPlayer(player), name));
                } catch (Exception invalid) {
                    failed++;
                }
            }
            return new Copies(imported, failed);
        }, server);
        message = text("copying").getString();
        init();
    }
    private void editCopy() {
        if (pending != null || marked.size() != 1 || minecraft.player == null) return;
        var server = minecraft.getSingleplayerServer();
        if (server == null) return;
        UUID id = minecraft.player.getUUID();
        String reference = marked.iterator().next();
        pending = CompletableFuture.supplyAsync(() -> {
            try { QuestStructures.edit(server.getPlayerList().getPlayer(id), reference); return Boolean.TRUE; }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, server);
    }

    private void confirmDelete() {
        if (marked.isEmpty()) return;
        List<String> names = List.copyOf(marked);
        minecraft.setScreen(new QuestConfirmScreen(this,
                Component.translatable("screen.xero_quest_journal.structure.delete_confirm", names.size()),
                Component.translatable("screen.xero_quest_journal.dialog.confirm"),
                () -> {
                    var server = minecraft.getSingleplayerServer();
                    if (server == null || minecraft.player == null) return;
                    UUID player = minecraft.player.getUUID();
                    pending = CompletableFuture.supplyAsync(() -> {
                        try {
                            return QuestStructureLibrary.delete(server.getPlayerList().getPlayer(player), names);
                        } catch (Exception failure) {
                            throw new java.util.concurrent.CompletionException(failure);
                        }
                    }, server);
                }));
    }

    @Override public void tick() {
        if (pending == null || !pending.isDone()) return;
        try {
            Object value = pending.join();
            if (Boolean.TRUE.equals(value)) { pending = null; minecraft.setScreen(null); return; }
            if (value instanceof List<?> names) {
                world = names.stream().map(Object::toString).toList();
                worldLoaded = true;
            } else if (value instanceof Preview preview) {
                ClientStructures.cachePreview(preview.reference(), preview.data());
                message = "";
                pending = null;
                minecraft.setScreen(new QuestStructurePreviewScreen(this, preview.reference()));
                return;
            }
            else if (value instanceof Copies result) {
                marked.clear();
                worldTab = false;
                query = "";
                message = Component.translatable("screen.xero_quest_journal.structure.copied",
                        result.imported().size(), result.failed()).getString();
                refreshLocal();
            } else if (value instanceof Integer count) {
                marked.clear();
                message = Component.translatable("screen.xero_quest_journal.structure.deleted", count).getString();
                refreshLocal();
            }
        } catch (Exception failure) {
            if (worldTarget != null) worldLoaded = true;
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            String key = cause.getMessage();
            message = key != null && key.startsWith("structure.")
                    ? Component.translatable("screen.xero_quest_journal." + key).getString()
                    : text("invalid").getString();
        }
        pending = null;
        filter();
        init();
    }

    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        motion.advance();
        moveRows();
        backdrop(g, 0xFF22282C);
        g.drawString(font, title, 16, 13, 0xFFF0D59B, false);
        if (visible.isEmpty()) g.drawString(font, text(pending != null ? "loading" : "missing"),
                16, 90, 0xFFC9D1D6, false);
        if (worldTarget != null) {
            g.drawString(font, font.plainSubstrByWidth(worldTarget, width - 32), 16, 38, 0xFFCCD5DE, false);
            if (worldLoaded && visible.isEmpty() && query.isBlank())
                g.drawWordWrap(font, text(poolTarget ? "jigsaw.no_pool_preview" : "no_target_preview"),
                        16, 116, width - 32, 0xFFCCD5DE);
        }
        if (!message.isBlank()) g.drawString(font,
                font.plainSubstrByWidth(message, Math.max(1, width - 255)),
                234, height - 25, 0xFFF2CF83, false);
        super.renderContents(g, x, y, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (scrollChoicePopup(x, y, h, v)) return true;
        if (!rowsViewport().contains(x, y)) return super.mouseScrolled(x, y, h, v);
        motion.wheel(v * 48);
        return true;
    }
    private Viewport rowsViewport() { return viewport(16, 86, width - 16, 86 + rowCount() * 26); }
    @Override protected Viewport widgetViewport(net.minecraft.client.gui.components.AbstractWidget widget) {
        return rowWidgets.contains(widget) ? rowsViewport() : null;
    }
    private void moveRows() {
        int next = motion.pixels(), delta = scroll - next;
        rowWidgets.forEach(widget -> widget.setY(widget.getY() + delta));
        scroll = next;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        if (button == 0 && rowsViewport().contains(x, y)) {
            listDragging = true; listMoved = false;
            startX = x; startY = y; startScroll = motion.position(); motion.freeze();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && listDragging) {
            listMoved |= Math.abs(y - startY) >= 4;
            if (listMoved) { motion.jump(startScroll + startY - y); moveRows(); }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && listDragging) {
            listDragging = false;
            if (!listMoved && Math.abs(x - startX) < 4 && rowsViewport().contains(x, y)) {
                super.mouseClicked(startX, startY, button);
                super.mouseReleased(x, y, button);
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public void onClose() { closeTo(parent); }
}
