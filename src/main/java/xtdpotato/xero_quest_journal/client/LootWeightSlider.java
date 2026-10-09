package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Compact integer slider used by loot rows; the screen owns weight redistribution. */
final class LootWeightSlider extends AbstractWidget {
    private final IntConsumer changed;
    private int value;
    private boolean dragging;
    private double grabOffset;

    LootWeightSlider(int x, int y, int width, int height, int value, IntConsumer changed) {
        super(x, y, width, height, Component.translatable("screen.xero_quest_journal.loot.weight"));
        this.changed = changed;
        setValue(value);
    }

    int value() { return value; }

    void setValue(int value) {
        this.value = Math.clamp(value, 0, 100);
    }

    private void setFromMouse(double x) {
        int next = Math.clamp((int) Math.round((x - getX() - 6 - grabOffset)
                * 100.0 / Math.max(1, width - 12)), 0, 100);
        if (next == value) return;
        value = next;
        changed.accept(value);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int center = getY() + height / 2;
        int start = getX() + 6;
        int end = getRight() - 6;
        int thumb = start + (int) Math.round((end - start) * value / 100.0);
        graphics.fill(start, center - 2, end, center + 2, 0xFF343B43);
        graphics.fill(start, center - 2, thumb, center + 2, 0xFF8FA9C8);
        graphics.fill(thumb - 3, center - 6, thumb + 4, center + 7,
                dragging || isHoveredOrFocused() ? 0xFFFFD071 : 0xFFD5DFE8);
        graphics.renderOutline(getX(), getY(), width, height, isHoveredOrFocused() ? 0xFFB9CAE0 : 0xFF727D89);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, value + "%",
                getRight() - 30, getY() + 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        int thumb = getX() + 6 + (int) Math.round((width - 12) * value / 100.0);
        grabOffset = Math.abs(mouseX - thumb) <= 5 ? mouseX - thumb : 0;
        dragging = true;
        setFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging || button != 0) return false;
        setFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !dragging) return false;
        dragging = false;
        grabOffset = 0;
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                Component.translatable("screen.xero_quest_journal.loot.weight_value", value));
    }
}
