package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.LootDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;

import java.util.List;
import java.util.Locale;

/** Read-only contents view for a reusable loot table and frozen choice rewards. */
public final class LootTablePopupScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 52;
    private static final int PANEL_WIDTH = 520;
    private final Screen parent;
    private final String title;
    private final String displayIcon;
    private final LootDefinition.Mode mode;
    private final int rolls;
    private final List<LootDefinition.DisplayEntry> entries;
    private final SmoothScroll scroll = new SmoothScroll();
    private int hover = -1;
    private String query = "";
    private List<LootDefinition.DisplayEntry> filtered;

    private LootTablePopupScreen(Screen parent, String title, String icon, LootDefinition.Mode mode,
                                 int rolls, List<LootDefinition.DisplayEntry> entries) {
        super(Component.translatable("screen.xero_quest_journal.loot.contents"));
        this.parent = parent;
        this.title = title == null ? "" : title;
        this.displayIcon = LootDefinition.normalizeDisplayIcon(icon);
        this.mode = mode == null ? LootDefinition.Mode.RANDOM : mode;
        this.rolls = Math.max(1, rolls);
        this.entries = List.copyOf(entries == null ? List.of() : entries);
        this.filtered = this.entries;
    }

    public static Screen inspect(Screen parent, QuestDefinition.Reward reward) {
        if (reward == null) return parent;
        if (reward.type().equals("loot")) {
            var table = QuestRewards.lootTable(reward.item());
            if (table != null)
                return new LootTablePopupScreen(parent, table.name(), table.displayIcon(), table.mode(),
                        table.rolls(), table.display().entries());
        }
        if (reward.type().equals("loot_choice")) {
            var rewards = QuestRewards.choices(reward);
            List<LootDefinition.DisplayEntry> choices = java.util.stream.IntStream.range(0, rewards.size())
                    .mapToObj(index -> {
                        var value = rewards.get(index);
                        return new LootDefinition.DisplayEntry("choice", value.type(), value.item(),
                                value.count(), 0, true, value.quality(), value.background(), QuestRewards.choiceDetail(reward, index));
                    }).toList();
            return new LootTablePopupScreen(parent, QuestRewards.choiceTitle(reward),
                    QuestRewards.choiceDisplayIcon(reward.item()), LootDefinition.Mode.CHOICE, 1, choices);
        }
        return new QuestRewardPopupScreen(parent, reward);
    }

    private int panelWidth() { return Math.min(PANEL_WIDTH, width - 32); }
    private int panelHeight() { return Math.min(height - 32, Math.max(176, 126 + entries.size() * ROW_HEIGHT)); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private boolean searchable() { return entries.size() > 10; }
    private int rowsTop() { return top() + (searchable() ? 122 : 94); }
    private int rowsBottom() { return top() + panelHeight() - 16; }
    private int rowAt(double x, double y) {
        if (x < left() + 18 || x >= left() + panelWidth() - 18
                || y < rowsTop() || y >= rowsBottom()) return -1;
        int index = (int) ((y - rowsTop() + scroll.pixels()) / ROW_HEIGHT);
        return index >= 0 && index < filtered.size() ? index : -1;
    }
    private double maximumScroll() {
        return Math.max(0, filtered.size() * ROW_HEIGHT - (rowsBottom() - rowsTop()));
    }

    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() {
        return viewport(left() - 2, top() - 2, left() + panelWidth() + 2, top() + panelHeight() + 2);
    }
    @Override protected void init() {
        clearWidgets();
        addRenderableWidget(new EditorIconButton(left() + panelWidth() - 30, top() + 8,
                Component.translatable("screen.xero_quest_journal.close"), this::onClose,
                ButtonStyleSheet.load(), "loot-contents-close", "close"));
        if (searchable()) {
            var search = new EditBox(font, left() + 18, top() + 92, panelWidth() - 36, 20,
                    Component.translatable("screen.xero_quest_journal.loot.search_rewards"));
            search.setMaxLength(128); search.setValue(query);
            search.setHint(Component.translatable("screen.xero_quest_journal.loot.search_rewards"));
            search.setResponder(value -> {
                query = value;
                String term = value.strip().toLowerCase(Locale.ROOT);
                filtered = entries.stream().filter(entry -> term.isEmpty()
                        || QuestIcons.searchIndex(entry.item(), entryName(entry) + " " + entry.type()
                                + " " + entry.detailText()).contains(term)).toList();
                scroll.limit(maximumScroll()); scroll.jump(0);
            });
            addRenderableWidget(search);
        }
    }
    private String entryName(LootDefinition.DisplayEntry entry) {
        var stack = entry.type().equals("item") ? QuestIcons.stack(entry.item()) : ItemStack.EMPTY;
        return !stack.isEmpty() ? stack.getHoverName().getString() : entry.type().equals("item") ? entry.item()
                : Component.translatable("screen.xero_quest_journal.loot.contents.type." + entry.type()).getString();
    }
    @Override protected void renderModalBackground(GuiGraphics graphics, float delta) {
        if (parent != null) parent.render(graphics, -10000, -10000, delta);
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        scroll.limit(maximumScroll());
        scroll.advance();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF9BAFCB);
        g.fill(x, y, x + w, y + h, 0xFF2B303A);
        g.fill(x, y, x + w, y + 66, 0xFF39465C);
        QuestIcons.render(g, displayIcon, x + 18, y + 17, 30);
        g.drawString(font, font.plainSubstrByWidth(title, Math.max(1, w - 104)),
                x + 60, y + 15, 0xFFF5D99A, false);
        Component modeLabel = mode == LootDefinition.Mode.CHOICE
                ? Component.translatable("screen.xero_quest_journal.loot.mode.choice")
                : Component.translatable("screen.xero_quest_journal.loot.contents.random", rolls);
        g.drawString(font, modeLabel, x + 60, y + 36, 0xFFC5D0DC, false);
        g.drawString(font, Component.translatable("screen.xero_quest_journal.loot.contents.entries",
                filtered.size()), x + 18, y + 73, 0xFFE7EAF0, false);

        int totalWeight = entries.stream().filter(entry -> entry.enabled() && entry.weight() > 0)
                .mapToInt(LootDefinition.DisplayEntry::weight).sum();
        hover = rowAt(mouseX, mouseY);
        g.enableScissor(x + 12, rowsTop(), x + w - 12, rowsBottom());
        for (int index = 0; index < filtered.size(); index++) {
            int rowY = rowsTop() + index * ROW_HEIGHT - scroll.pixels();
            if (rowY + ROW_HEIGHT < rowsTop() || rowY > rowsBottom()) continue;
            var entry = filtered.get(index);
            int rowColor = index == hover ? 0xFF3E4959 : 0xFF323945;
            g.fill(x + 14, rowY + 2, x + w - 14, rowY + ROW_HEIGHT - 3, rowColor);
            if (entry.type().equals("item") && !entry.item().isBlank()) {
                RewardCardRenderer.render(g, font, entry.item(), entry.count(), entry.quality(),
                        entry.background(), x + 21, rowY + 7, 1, 1, false, index == hover);
            } else {
                QuestIcons.render(g, displayIconFor(entry.type()), x + 22, rowY + 13, 24);
                g.drawString(font, Integer.toString(entry.count()), x + 27, rowY + 38, 0xFFE8EDF2, false);
            }
            int textX = x + 66;
            ItemStack stack = entry.type().equals("item") ? QuestIcons.stack(entry.item()) : ItemStack.EMPTY;
            String name = !stack.isEmpty() ? stack.getHoverName().getString()
                    : entry.type().equals("item")
                    ? entry.item()
                    : Component.translatable("screen.xero_quest_journal.loot.contents.type." + entry.type()).getString();
            g.drawString(font, font.plainSubstrByWidth(name, Math.max(1, w - 180)),
                    textX, rowY + 9, entry.enabled() ? 0xFFF0EEE8 : 0xFF89919C, false);
            int chanceColor = entry.enabled() ? 0xFFE8C987 : 0xFF89919C;
            if (mode == LootDefinition.Mode.RANDOM)
                g.drawString(font, Component.translatable("screen.xero_quest_journal.loot.weight_value",
                        entry.weight()), textX, rowY + 28, 0xFFB8C3D0, false);
            if (!entry.detailText().isBlank() || mode == LootDefinition.Mode.CHOICE) {
                Component detail = entry.detailText().isBlank()
                        ? Component.translatable("screen.xero_quest_journal.loot.contents.choice")
                        : Component.literal(entry.detailText());
                int detailX = textX + (mode == LootDefinition.Mode.RANDOM ? 100 : 0);
                g.drawString(font, font.plainSubstrByWidth(detail.getString(), Math.max(1, x + w - 22 - detailX)),
                        detailX, rowY + 28, 0xFFB8C3D0, false);
            }
            if (!entry.enabled())
                g.drawString(font, Component.translatable("screen.xero_quest_journal.loot.contents.disabled"),
                        x + w - 112, rowY + 9, 0xFFEB8983, false);
            else if (mode == LootDefinition.Mode.RANDOM && totalWeight > 0)
                g.drawString(font, percent(entry.weight(), totalWeight),
                        x + w - 74, rowY + 9, chanceColor, false);
            if (index == hover) {
                Component hint = Component.literal(name);
                if (!entry.detailText().isBlank()) hint = hint.copy().append("\n").append(entry.detailText());
                setTooltipForNextRenderPass(hint);
            }
        }
        g.disableScissor();
        if (filtered.isEmpty()) g.drawString(font, Component.translatable("screen.xero_quest_journal.loot.no_matches"),
                x + 18, rowsTop() + 12, 0xFFB8C3D0, false);
        if (scroll.maximum() > 0) {
            int railX = x + w - 7, track = rowsBottom() - rowsTop();
            int thumb = Math.max(16, (int) (track * track / (track + scroll.maximum())));
            int thumbY = rowsTop() + (int) ((track - thumb) * scroll.position() / scroll.maximum());
            g.fill(railX, rowsTop(), railX + 2, rowsBottom(), 0x55303842);
            g.fill(railX, thumbY, railX + 2, thumbY + thumb, 0xFF9EB0C1);
        }
        super.renderContents(g, mouseX, mouseY, delta);
    }

    private static String percent(int weight, int total) {
        return String.format(Locale.ROOT, "%.1f%%", total <= 0 ? 0 : weight * 100.0 / total);
    }
    private static String displayIconFor(String type) {
        return switch (type) {
            case "experience" -> "minecraft:experience_bottle";
            case "levels" -> "minecraft:experience_bottle";
            case "command" -> "minecraft:command_block";
            default -> "minecraft:paper";
        };
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (rowsTop() <= y && y < rowsBottom()) {
            scroll.wheel(vertical * 36);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0) {
            int index = rowAt(x, y);
            if (index >= 0) {
                var entry = filtered.get(index);
                minecraft.setScreen(inspect(this, new QuestDefinition.Reward(entry.type(), entry.item(),
                        entry.count(), entry.quality(), entry.background())));
                return true;
            }
        }
        return false;
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
