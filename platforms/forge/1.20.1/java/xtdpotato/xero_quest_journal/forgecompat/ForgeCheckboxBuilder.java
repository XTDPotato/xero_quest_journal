package xtdpotato.xero_quest_journal.forgecompat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

public final class ForgeCheckboxBuilder {
    private final Component label;
    private final Font font;
    private int x;
    private int y;
    private int maxWidth = Integer.MAX_VALUE;
    private boolean selected;
    private BiConsumer<Checkbox, Boolean> onValueChange = (checkbox, value) -> {};

    public ForgeCheckboxBuilder(Component label, Font font) {
        this.label = label;
        this.font = font;
    }

    public ForgeCheckboxBuilder pos(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public ForgeCheckboxBuilder maxWidth(int maxWidth) {
        this.maxWidth = Math.max(20, maxWidth);
        return this;
    }

    public ForgeCheckboxBuilder selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public ForgeCheckboxBuilder onValueChange(BiConsumer<Checkbox, Boolean> callback) {
        this.onValueChange = callback;
        return this;
    }

    public Checkbox build() {
        int width = Math.min(maxWidth, font.width(label) + 24);
        return new Checkbox(x, y, width, 20, label, selected) {
            @Override
            public void onPress() {
                super.onPress();
                onValueChange.accept(this, selected());
            }
        };
    }
}
