package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

/** The menu remains within the field bounds, including clipped/scrolling editor rows. */
public final class SuggestedEditBox extends EditBox {
    private final Screen parent;
    private final Font font;
    private final String context;
    private Supplier<List<QuestValuePickerScreen.Option>> options;
    private boolean editable = true;
    private boolean menu = true;

    public SuggestedEditBox(Screen parent, Font font, int x, int y, int width, int height,
                            Component label, String context) {
        super(font, x, y, width, height, label);
        this.parent = parent;
        this.font = font;
        this.context = context.isBlank() ? "field:" + label.getString() : context;
        options = () -> QuestSuggestions.options(context, getValue());
    }

    public SuggestedEditBox options(Supplier<List<QuestValuePickerScreen.Option>> options) {
        this.options = options;
        return this;
    }

    public void withoutMenu() { menu = false; }
    private int menuWidth() { return menu && width >= 40 ? 18 : 0; }
    @Override public int getInnerWidth() { return Math.max(1, super.getInnerWidth() - menuWidth()); }
    @Override public void setEditable(boolean editable) { super.setEditable(editable); this.editable = editable; }

    private void open() {
        if (!active || !visible || !editable || menuWidth() == 0) return;
        Minecraft.getInstance().setScreen(new QuestValuePickerScreen(parent, getMessage().getString(),
                context, options, getValue(), this::setValue));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && menuWidth() > 0 && x >= getRight() - menuWidth() && x < getRight()
                && y >= getY() && y < getBottom() && active && visible && editable) {
            open();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (isFocused() && key == GLFW.GLFW_KEY_DOWN && (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            open(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        boolean item = !isFocused() && xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue.encoded(getValue());
        if (item) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF000000);
            graphics.renderOutline(getX(), getY(), width, height, 0xFFA0A0A0);
            graphics.drawString(font, font.plainSubstrByWidth(QuestIcons.displayName(getValue()), getInnerWidth()),
                    getX() + 4, getY() + (height - 8) / 2, active ? 0xFFE0E0E0 : 0xFF707070);
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(QuestIcons.tooltip(getValue())));
        } else super.renderWidget(graphics, mouseX, mouseY, tick);
        if (menuWidth() == 0) return;
        int left = getRight() - menuWidth();
        boolean hover = mouseX >= left && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom();
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(hover ? Component.translatable(
                "screen.xero_quest_journal.value_picker.open") : item ? QuestIcons.tooltip(getValue()) : getMessage()));
        graphics.fill(left, getY() + 1, getRight() - 1, getBottom() - 1,
                active && editable && hover ? 0xFF44505C : 0xFF252B32);
        graphics.drawCenteredString(font, "\u25be", left + menuWidth() / 2, getY() + (height - 8) / 2,
                active && editable ? 0xFFDEE6EA : 0xFF72787C);
    }
}
