package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.core.model.TriggerNodeLibrary;
import java.util.*;
import java.util.function.Consumer;

/** Categorized node palette. Missing integrations remain visible, but cannot be selected. */
public final class QuestNodeLibraryScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 40;
    private final Screen parent;
    private final Consumer<TriggerNodeLibrary.Entry> chosen;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String category = "player_event", query = "";
    private final double[] scroll = {0, 0}, target = {0, 0};
    private int dragging = -1;
    private boolean thumbDrag;
    private double startY, startScroll, thumbOffset;
    private long lastFrame;

    public QuestNodeLibraryScreen(Screen parent, Consumer<TriggerNodeLibrary.Entry> chosen) {
        super(text("library"));
        this.parent = parent;
        this.chosen = chosen;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.graph." + key);
    }
    public static boolean available(String category) {
        String mod = TriggerNodeLibrary.requiredMod(category);
        return mod.isBlank() || PlatformServices.isModLoaded(mod);
    }
    private int split() { return Math.min(180, width * 2 / 5); }
    private int left(int side) { return side == 0 ? 12 : split() + 8; }
    private int right(int side) { return side == 0 ? split() : width - 12; }
    private int bottom() { return height - 18; }
    private List<TriggerNodeLibrary.Entry> allEntries() {
        return TriggerNodeLibrary.PALETTE_CATEGORIES.stream()
                .flatMap(category -> TriggerNodeLibrary.paletteEntries(category).stream())
                .distinct().toList();
    }
    private List<TriggerNodeLibrary.Entry> entries() {
        String search = query.strip().toLowerCase(Locale.ROOT);
        var source = search.isEmpty() ? TriggerNodeLibrary.paletteEntries(category) : allEntries();
        return source.stream().filter(e -> {
            String categoryName = text("category." + e.category()).getString();
            String paletteName = text("category." + paletteCategory(e.category())).getString();
            String nodeName = text(e.labelKey()).getString();
            return search.isEmpty() || categoryName.toLowerCase(Locale.ROOT).contains(search)
                    || paletteName.toLowerCase(Locale.ROOT).contains(search)
                    || nodeName.toLowerCase(Locale.ROOT).contains(search)
                    || e.value().toLowerCase(Locale.ROOT).contains(search)
                    || e.type().toLowerCase(Locale.ROOT).contains(search);
        }).toList();
    }
    private Component entryLabel(TriggerNodeLibrary.Entry entry) {
        if (query.strip().isEmpty()) return text(entry.labelKey());
        return Component.literal(text("category." + paletteCategory(entry.category())).getString()
                + " - " + text(entry.labelKey()).getString());
    }
    private static String paletteCategory(String category) {
        return TriggerNodeLibrary.PALETTE_CATEGORIES.stream()
                .filter(group -> TriggerNodeLibrary.paletteEntries(group).stream()
                        .anyMatch(entry -> entry.category().equals(category)))
                .findFirst().orElse(category);
    }
    private int count(int side) { return side == 0 ? TriggerNodeLibrary.PALETTE_CATEGORIES.size() : entries().size(); }
    private int max(int side) { return Math.max(0, count(side) * ROW_HEIGHT - (bottom() - 68)); }
    private int thumbHeight(int side) { int h = bottom() - 68; return Math.max(14, h * h / Math.max(h, count(side) * ROW_HEIGHT)); }
    private double thumbY(int side) { return 68 + (bottom() - 68 - thumbHeight(side)) * scroll[side] / Math.max(1, max(side)); }
    @Override protected void init() {
        clearWidgets();
        var search = new EditBox(font, left(1), 38, right(1) - left(1), 20, text("search_nodes"));
        search.setMaxLength(128);
        search.setHint(text("search_nodes"));
        search.setValue(query);
        search.setResponder(value -> { query = value; scroll[1] = target[1] = 0; });
        addRenderableWidget(search);
        addRenderableWidget(new EditorIconButton(width - 36, 6, Component.translatable("gui.back"),
                this::onClose, styles, "node-library-close", "close"));
        for (int side = 0; side < 2; side++) scroll[side] = target[side] = Math.clamp(target[side], 0, max(side));
    }
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        backdrop(g, TriggerNodeStyle.PANEL);
        g.fill(0, 0, width, 32, 0xFF19191D);
        g.fill(0, 32, width, 33, TriggerNodeStyle.PANEL_BORDER);
        MailDecor.diamond(g, 15, 17, 3, TriggerNodeStyle.GOLD);
        g.drawString(font, title, 24, 13, 0xFFE8E8EC, false);
        g.drawString(font, text("categories"), 12, 43, 0xFFB9BDC7, false);
        long now = System.nanoTime();
        double blend = lastFrame == 0 ? 1 : 1 - Math.exp(-18 * Math.min(.1, (now - lastFrame) / 1e9));
        lastFrame = now;
        Component tooltip = null;
        var entries = entries();
        for (int side = 0; side < 2; side++) {
            scroll[side] += (target[side] - scroll[side]) * blend;
            int l = left(side), r = right(side), first = (int) scroll[side] / ROW_HEIGHT;
            g.fill(l, 68, r, bottom(), 0xFF19191D);
            g.enableScissor(l, 68, r, bottom());
            for (int i = first; i < count(side); i++) {
                int y = 68 + i * ROW_HEIGHT - (int) scroll[side];
                if (y >= bottom()) break;
                String cat = side == 0 ? TriggerNodeLibrary.PALETTE_CATEGORIES.get(i) : entries.get(i).category();
                Component label;
                if (side == 0) {
                    label = text("category." + cat);
                } else {
                    var entry = entries.get(i);
                    label = entryLabel(entry);
                }
                boolean enabled = available(cat), hovered = mx >= l && mx < r - 6 && my >= Math.max(68, y)
                        && my < Math.min(bottom(), y + ROW_HEIGHT);
                boolean chosenCategory = side == 0 && category.equals(cat);
                var kind = TriggerNodeStyle.kindForCategory(cat);
                int background = !enabled ? 0xFF222226 : chosenCategory ? 0xFF2F2F36 : hovered ? 0xFF2B2B31 : 0xFF26262B;
                int border = chosenCategory ? TriggerNodeStyle.GOLD : hovered && enabled ? 0xFF5C5C66
                        : TriggerNodeStyle.VALUE_BORDER;
                ControlAppearance.draw(g, l + 1, y + 1, r - l - 8, ROW_HEIGHT - 3, "rounded", 5, background, border, 1);
                // Category swatch: the node class colour behind the icon, like a node's header band.
                ControlAppearance.draw(g, l + 5, y + 5, 32, ROW_HEIGHT - 11, "rounded", 4,
                        enabled ? kind.top : 0xFF4A4A52, 0, 0);
                JournalNavButton.drawIcon(g, icon(cat), l + 9, y + 7, 24,
                        enabled ? kind.text : 0xFF2A2A30);
                g.drawString(font, font.plainSubstrByWidth(label.getString(), r - l - 52),
                        l + 44, y + (ROW_HEIGHT - font.lineHeight) / 2,
                        enabled ? chosenCategory ? 0xFFFFE6B0 : 0xFFE8E8EC : 0xFF6E6E76, false);
                if (hovered) tooltip = enabled ? label : Component.translatable(
                        "screen.xero_quest_journal.graph.requires_mod", "KubeJS");
            }
            g.disableScissor();
            if (max(side) > 0) {
                g.fill(r - 5, 68, r - 1, bottom(), 0xFF303037);
                g.fill(r - 5, (int) thumbY(side), r - 1, (int) thumbY(side) + thumbHeight(side), 0xFF8A8A94);
            }
        }
        super.renderContents(g, mx, my, delta);
        if (tooltip != null) g.renderTooltip(font, tooltip, mx, my);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 || y < 68 || y >= bottom()) return false;
        for (int side = 0; side < 2; side++) if (x >= left(side) && x < right(side)) {
            dragging = side;
            thumbDrag = x >= right(side) - 7 && max(side) > 0;
            startY = y;
            startScroll = scroll[side];
            if (thumbDrag) {
                thumbOffset = y >= thumbY(side) && y < thumbY(side) + thumbHeight(side)
                        ? y - thumbY(side) : thumbHeight(side) / 2.;
                dragThumb(y);
            }
            return true;
        }
        return false;
    }
    private void dragThumb(double y) {
        scroll[dragging] = target[dragging] = Math.clamp((y - 68 - thumbOffset) * max(dragging)
                / Math.max(1, bottom() - 68 - thumbHeight(dragging)), 0, max(dragging));
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging >= 0) {
            if (thumbDrag) dragThumb(y);
            else scroll[dragging] = target[dragging] = Math.clamp(startScroll + startY - y, 0, max(dragging));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging >= 0) {
            int side = dragging;
            dragging = -1;
            if (!thumbDrag && Math.abs(y - startY) < 4 && y >= 68 && y < bottom()
                    && x >= left(side) && x < right(side) - 6) {
                int index = (int) ((scroll[side] + y - 68) / ROW_HEIGHT);
                if (index >= 0 && index < count(side)) {
                    if (side == 0) {
                        category = TriggerNodeLibrary.PALETTE_CATEGORIES.get(index);
                        scroll[1] = target[1] = 0;
                        query = "";
                        init();
                    } else if (available(entries().get(index).category())) {
                        chosen.accept(entries().get(index));
                        if (minecraft.screen == this) minecraft.setScreen(parent);
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        for (int side = 0; side < 2; side++) if (x >= left(side) && x < right(side) && y >= 68 && y < bottom()) {
            target[side] = Math.clamp(target[side] - vertical * 48, 0, max(side)); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void onClose() { closeTo(parent); }

    private static String icon(String category) {
        return switch (category) {
            case "player_event", "player_value" -> "minecraft:player_head";
            case "world_event", "world_value" -> "minecraft:grass_block";
            case "control_event", "control_value", "control_properties", "set_property", "reset_property" -> "settings";
            case "task_actions", "complete" -> "track";
            case "advance_step" -> "expand";
            case "command" -> "minecraft:command_block";
            case "unlock" -> "expand";
            case "lock", "set_task_property" -> "settings";
            case "set_theme" -> "color";
            case "reward" -> "gift";
            case "logic", "condition" -> "clause";
            case "script_condition" -> "source";
            case "variable", "calculate_variable" -> "variables";
            case "set_variable" -> "edit";
            case "kubejs", "kubejs_call" -> "source";
            case "kubejs_script" -> "edit";
            case "mail", "mail_send" -> "minecraft:paper";
            case "mail_check" -> "choose";
            case "mail_delete" -> "delete";
            case "mail_set_property" -> "settings";
            case "location", "locate_structure", "locate_biome" -> "minecraft:compass";
            case "teleport" -> "expand";
            case "enter_structure" -> "minecraft:structure_block";
            case "end" -> "close";
            default -> "other";
        };
    }
}
