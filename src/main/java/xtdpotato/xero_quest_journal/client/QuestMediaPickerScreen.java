package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** In-game browser for local media; selected files are copied into portable quest assets. */
public final class QuestMediaPickerScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 21;
    private final Screen parent;
    private final boolean video;
    private final Consumer<String> chosen;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final List<Path> roots = List.of(FMLPaths.GAMEDIR.get().toAbsolutePath().normalize(),
            FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize(), QuestIcons.localRoot());
    private int rootIndex = 2;
    private Path directory = roots.get(rootIndex);
    private Path selected;
    private List<Path> entries = List.of();
    private int scroll;
    private double smoothScroll, targetScroll, thumbOffset, dragStartY, dragStartScroll;
    private boolean draggingScrollbar, draggingList;
    private String query = "";
    private String error = "";
    private boolean importing;

    public QuestMediaPickerScreen(Screen parent, boolean video, Consumer<String> chosen) {
        super(text("title"));
        this.parent = parent;
        this.video = video;
        this.chosen = chosen;
        refresh();
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.media_picker." + key);
    }

    private int left() { return (width - Math.min(560, width - 24)) / 2; }
    private int panelWidth() { return Math.min(560, width - 24); }
    private int listTop() { return 102; }
    private int listBottom() { return Math.max(listTop() + 20, height - 43); }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT); }
    private int maxScroll() { return Math.max(0, entries.size() - visibleRows()); }
    private int trackX() { return left() + panelWidth() + 3; }
    private int thumbHeight() {
        return Math.max(12, (listBottom() - listTop()) * visibleRows() / Math.max(1, entries.size()));
    }
    private double thumbY() {
        return listTop() + (listBottom() - listTop() - thumbHeight()) * smoothScroll / Math.max(1, maxScroll());
    }

    private void refresh() {
        Path root = roots.get(rootIndex);
        if (!directory.startsWith(root) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            directory = root;
        }
        try (var paths = Files.list(directory)) {
            entries = paths.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && QuestMediaPicker.supported(path, video))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .contains(query.strip().toLowerCase(Locale.ROOT)))
                    .limit(4096)
                    .sorted(Comparator.comparing((Path path) ->
                            !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                            .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            error = "";
        } catch (IOException failure) {
            entries = List.of();
            error = text("read_error").getString();
            XeroQuestJournal.LOGGER.warn("Unable to list quest media directory {}", directory, failure);
        }
        targetScroll = smoothScroll = Math.clamp(targetScroll, 0, maxScroll());
        scroll = (int) smoothScroll;
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = left(), w = panelWidth();
        for (int i = 0; i < roots.size(); i++) {
            final int index = i;
            var button = new StyledButton(x + i * (w / 3), 34, i == 2 ? w - 2 * (w / 3) : w / 3 - 2,
                    20, text("root." + i), ignored -> {
                rootIndex = index;
                directory = roots.get(index);
                selected = null;
                scroll = 0;
                smoothScroll = targetScroll = 0;
                query = "";
                refresh();
                init();
            }, styles, "media-root-" + i, "toolbar-button");
            button.defaultCss("button:selected { background-color:#d9c18f; color:#20252c; }");
            button.setSelectionHighlighted(i == rootIndex);
            addRenderableWidget(button);
        }
        var up = new StyledButton(x, 58, 24, 20, Component.literal("\u2190"), ignored -> {
            if (!directory.equals(roots.get(rootIndex))) {
                directory = directory.getParent();
                selected = null;
                scroll = 0;
                smoothScroll = targetScroll = 0;
                refresh();
                init();
            }
        }, styles, "media-up", "toolbar-button");
        up.active = !directory.equals(roots.get(rootIndex));
        up.setTooltip(Tooltip.create(text("up")));
        addRenderableWidget(up);
        EditBox search = new EditBox(font, x + 28, 58, w - 28, 20, text("search"));
        search.setMaxLength(120);
        search.setValue(query);
        search.setHint(text("search"));
        search.setResponder(value -> {
            query = value;
            scroll = 0;
            smoothScroll = targetScroll = 0;
            refresh();
        });
        addRenderableWidget(search);
        var confirm = new StyledButton(x, height - 30, (w - 6) / 2, 20,
                text("import"), ignored -> importSelected(), styles, "media-import", "toolbar-button");
        confirm.active = selected != null && !importing;
        addRenderableWidget(confirm);
        addRenderableWidget(new StyledButton(x + (w - 6) / 2 + 6, height - 30,
                (w - 6) / 2, 20, Component.translatable("gui.cancel"),
                ignored -> onClose(), styles, "media-cancel", "toolbar-button"));
    }

    private void importSelected() {
        Path path = selected;
        if (importing || path == null || !entries.contains(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return;
        importing = true;
        init();
        Thread.ofVirtual().name("xero-media-import").start(() -> {
            try {
                String value = QuestMediaPicker.importFile(path, video);
                minecraft.execute(() -> {
                    if (minecraft.screen != this) return;
                    minecraft.setScreen(parent);
                    chosen.accept(value);
                });
            } catch (IOException failure) {
                XeroQuestJournal.LOGGER.warn("Unable to import quest media {}", path, failure);
                minecraft.execute(() -> {
                    importing = false;
                    error = text("import_error").getString() + ": " + failure.getMessage();
                    if (minecraft.screen == this) init();
                });
            }
        });
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        super.renderContents(graphics, mouseX, mouseY, tick);
        int x = left(), right = x + panelWidth(), top = listTop(), bottom = listBottom();
        if (!draggingScrollbar && !draggingList) {
            smoothScroll += (targetScroll - smoothScroll) * 0.35;
            if (Math.abs(targetScroll - smoothScroll) < 0.01) smoothScroll = targetScroll;
        }
        scroll = (int) smoothScroll;
        int offset = (int) Math.round((smoothScroll - scroll) * ROW_HEIGHT);
        graphics.drawCenteredString(font, text(video ? "video" : "audio"), width / 2, 12, 0xFFF4E0BD);
        graphics.drawString(font, font.plainSubstrByWidth(
                directory.toString().replace('\\', '/'), panelWidth() - 8),
                x + 4, 83, 0xFFBAC8D4);
        graphics.fill(x, top, right, bottom, 0xE021252C);
        graphics.enableScissor(x, top, right, bottom);
        for (int row = 0; row <= visibleRows() && row + scroll < entries.size(); row++) {
            Path path = entries.get(row + scroll);
            int y = top + row * ROW_HEIGHT - offset;
            boolean folder = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
            boolean hovered = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + 20;
            graphics.fill(x + 1, y, right - 1, y + 20,
                    path.equals(selected) ? 0xFF49617A : hovered ? 0xFF394450 : 0xFF292E35);
            graphics.drawString(font, folder ? "\u25b8" : "\u266b", x + 6, y + 6, 0xFFD8BD86);
            graphics.drawString(font, font.plainSubstrByWidth(path.getFileName().toString(),
                    panelWidth() - 31), x + 22, y + 6, 0xFFE9EDF1);
        }
        graphics.disableScissor();
        if (entries.size() > visibleRows()) {
            graphics.fill(trackX(), top, trackX() + 5, bottom, 0xFF303741);
            graphics.fill(trackX(), (int) thumbY(), trackX() + 5,
                    (int) thumbY() + thumbHeight(), 0xFF9DB4D2);
        }
        if (entries.isEmpty()) graphics.drawCenteredString(font, text("empty"), width / 2, top + 10, 0xFFAAB7C4);
        if (!error.isBlank()) graphics.drawString(font,
                font.plainSubstrByWidth(error, panelWidth()), x, height - 42, 0xFFFF8888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && maxScroll() > 0 && mouseX >= trackX() - 2 && mouseX < trackX() + 9
                && mouseY >= listTop() && mouseY < listBottom()) {
            draggingScrollbar = true;
            thumbOffset = mouseY >= thumbY() && mouseY < thumbY() + thumbHeight()
                    ? mouseY - thumbY() : thumbHeight() / 2.0;
            dragThumb(mouseY);
            return true;
        }
        if (button == 0 && mouseX >= left() && mouseX < left() + panelWidth()
                && mouseY >= listTop() && mouseY < listBottom()) {
            int index = (int) Math.floor(smoothScroll + (mouseY - listTop()) / ROW_HEIGHT);
            if (index < entries.size()) {
                draggingList = true;
                dragStartY = mouseY;
                dragStartScroll = smoothScroll;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragThumb(double y) {
        targetScroll = smoothScroll = Math.clamp((y - listTop() - thumbOffset) * maxScroll()
                / Math.max(1, listBottom() - listTop() - thumbHeight()), 0, maxScroll());
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScrollbar) { dragThumb(y); return true; }
        if (button == 0 && draggingList) {
            targetScroll = smoothScroll = Math.clamp(dragStartScroll + (dragStartY - y) / ROW_HEIGHT, 0, maxScroll());
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        if (button == 0 && draggingList) {
            draggingList = false;
            if (Math.abs(y - dragStartY) < 4 && x >= left() && x < left() + panelWidth()
                    && y >= listTop() && y < listBottom()) {
                int index = (int) Math.floor(smoothScroll + (y - listTop()) / ROW_HEIGHT);
                if (index >= 0 && index < entries.size()) {
                    Path path = entries.get(index);
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        directory = path;
                        selected = null;
                        query = "";
                        scroll = 0;
                        smoothScroll = targetScroll = 0;
                        refresh();
                    } else selected = path;
                    init();
                }
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= left() && mouseX < left() + panelWidth() + 9
                && mouseY >= listTop() && mouseY < listBottom() && vertical != 0) {
            targetScroll = Math.clamp(targetScroll - vertical * 2.5, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float tick) {
        backdrop(graphics, 0xF01B2026);
    }
}
