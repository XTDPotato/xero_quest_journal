package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Virtualized item browser for all entries in an item tag. */
final class QuestTagItemsScreen extends AnimatedQuestScreen {
    private static final int TILE = 52;
    private static final int GAP = 8;
    private final Screen parent;
    private final String tag;
    private final List<ItemStack> items;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private double scroll;
    private double targetScroll;

    QuestTagItemsScreen(Screen parent, String tag, List<ItemStack> items) {
        super(Component.literal(tag));
        this.parent = parent;
        this.tag = tag;
        this.items = items.stream().map(ItemStack::copy).toList();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.tag_items." + key, args);
    }

    private int columns() {
        return Math.max(1, (width - 64 + GAP) / (TILE + GAP));
    }

    private int listLeft() { return (width - (columns() * TILE + (columns() - 1) * GAP)) / 2; }
    private int listTop() { return 54; }
    private int listBottom() { return Math.max(listTop() + TILE, height - 48); }
    private int contentHeight() {
        int rows = (items.size() + columns() - 1) / columns();
        return Math.max(0, rows * (TILE + GAP) - GAP);
    }
    private double maxScroll() { return Math.max(0, contentHeight() - (listBottom() - listTop())); }
    private int tileX(int column) { return listLeft() + column * (TILE + GAP); }
    private int tileY(int row) { return listTop() + row * (TILE + GAP) - (int) scroll; }

    @Override
    protected void init() {
        clearWidgets();
        var close = new EditorIconButton(width - 36, 8, Component.translatable("gui.cancel"),
                this::onClose, styles, "tag-items-close", "close");
        addRenderableWidget(close);
        var viewer = RecipeViewerBridge.availableViewer();
        var open = new StyledButton(16, height - 30, Math.min(220, width - 32), 20,
                viewer.isBlank() ? text("viewer_unavailable") : text("viewer_open", viewer),
                ignored -> {
                    if (!viewer.isBlank() && !RecipeViewerBridge.openTag(tag, items))
                        minecraft.setScreen(new QuestTagItemsScreen(parent, tag, items));
                }, styles, "tag-items-viewer", "toolbar-button");
        open.active = !viewer.isBlank() && !items.isEmpty();
        open.setTooltip(net.minecraft.client.gui.components.Tooltip.create(viewer.isBlank()
                ? text("viewer_unavailable_hint") : text("viewer_open_hint", viewer)));
        addRenderableWidget(open);
        if (items.isEmpty()) {
            addRenderableWidget(new StyledButton(width - 16 - Math.min(120, width - 32), height - 30,
                    Math.min(120, width - 32), 20, Component.translatable("gui.done"),
                    ignored -> onClose(), styles, "tag-items-done", "toolbar-button"));
        }
    }

    @Override
    public void tick() {
        scroll += (targetScroll - scroll) * .35;
        if (Math.abs(targetScroll - scroll) < .05) scroll = targetScroll;
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, 0xF01B2026);
        super.renderContents(graphics, mouseX, mouseY, delta);
        int left = listLeft(), right = width - left, top = listTop(), bottom = listBottom();
        graphics.drawString(font, tag, left, 30, 0xFFF2D29A, false);
        graphics.drawString(font, text("count", items.size()), right - font.width(text("count", items.size())),
                30, 0xFFBFC8D3, false);
        graphics.fill(left - 4, top - 4, right + 4, bottom + 4, 0xE021252C);
        graphics.enableScissor(left - 4, top - 4, right + 4, bottom + 4);
        int columns = columns();
        int firstRow = Math.max(0, (int) (scroll / (TILE + GAP)) - 1);
        int lastRow = Math.min((items.size() + columns - 1) / columns,
                (int) ((scroll + bottom - top) / (TILE + GAP)) + 2);
        int hovered = -1;
        for (int row = firstRow; row < lastRow; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= items.size()) break;
                int x = tileX(column), y = tileY(row);
                boolean hover = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
                if (hover) hovered = index;
                graphics.fill(x, y, x + TILE, y + TILE,
                        hover ? 0xFF536E8C : 0xFF354252);
                graphics.renderOutline(x, y, TILE, TILE,
                        hover ? 0xFFF1D8A0 : 0xFF92A7BD);
                graphics.renderItem(items.get(index), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
            }
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int trackX = right + 7;
            int trackHeight = bottom - top;
            int thumbHeight = Math.max(18, trackHeight * trackHeight / Math.max(trackHeight, contentHeight()));
            int thumbY = top + (int) Math.round((trackHeight - thumbHeight) * scroll / maxScroll());
            graphics.fill(trackX, top, trackX + 4, bottom, 0xFF303741);
            graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFFE6DFD1);
        }
        if (items.isEmpty())
            graphics.drawCenteredString(font, text("empty"), width / 2, (top + bottom) / 2, 0xFFBAC4D0);
        if (hovered >= 0) {
            ItemStack stack = items.get(hovered);
            graphics.renderTooltip(font, net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, stack),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && x >= listLeft() && x < width - listLeft()
                && y >= listTop() && y < listBottom()) {
            int column = ((int) x - listLeft()) / (TILE + GAP);
            int row = (int) ((y - listTop() + scroll) / (TILE + GAP));
            int index = row * columns() + column;
            int localX = ((int) x - listLeft()) % (TILE + GAP);
            int localY = (int) (y - listTop() + scroll) % (TILE + GAP);
            if (column < columns() && localX < TILE && localY < TILE && index >= 0 && index < items.size()) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items.get(index).getItem()).toString();
                minecraft.setScreen(new QuestRewardPopupScreen(this,
                        new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward("item", id, 1)));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= listLeft() - 8 && x < width - listLeft() + 12
                && y >= listTop() && y < listBottom() && vertical != 0) {
            targetScroll = Math.clamp(targetScroll - vertical * 48, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
