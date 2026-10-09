package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local background override for the legendary gallery. */
final class LegendaryBackgroundScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String source;
    private String error;
    private EditBox field;

    LegendaryBackgroundScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.legendary.background"));
        this.parent = parent;
        source = ClientPreferences.get().legendaryBackground;
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.legendary." + key);
    }

    private int panelWidth() { return Math.min(460, width - 32); }
    private int left() { return (width - panelWidth()) / 2; }

    @Override protected void renderBeforeBlur(GuiGraphics graphics, float delta) {
        String background = source.isBlank() ? ClientQuestProgress.overviewImage() : source;
        if (!background.isBlank())
            ClientMediaManager.renderImageCover(graphics, background, 0, 0, width, height, 1);
    }

    @Override protected void init() {
        clearWidgets();
        int x = left(), w = panelWidth();
        field = new EditBox(font, x, height - 88, w - 30, 20, text("background.source"));
        field.setMaxLength(4096);
        field.setValue(source);
        field.setResponder(value -> source = value.strip());
        addRenderableWidget(field);
        addRenderableWidget(new EditorIconButton(x + w - 24, height - 90,
                text("background.choose"), () ->
                minecraft.setScreen(QuestScreen.pickResource(this, source, false, value -> {
                    source = value;
                    field.setValue(value);
                })), styles, "legendary-background-pick", "minecraft:painting"));
        int part = (w - 12) / 3;
        addRenderableWidget(new StyledButton(x, height - 42, part, 22, text("background.reset"),
                ignored -> { source = ""; field.setValue(""); error = null; },
                styles, "legendary-background-reset"));
        addRenderableWidget(new StyledButton(x + part + 6, height - 42, part, 22,
                Component.translatable("gui.done"), ignored -> save(), styles, "legendary-background-save"));
        addRenderableWidget(new StyledButton(x + (part + 6) * 2, height - 42, part, 22,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "legendary-background-cancel"));
    }

    private void save() {
        if (!source.isBlank() && !QuestIcons.valid(source) && !ClientMediaManager.validImageSource(source)) {
            error = "background.invalid";
            return;
        }
        ClientPreferences.get().legendaryBackground = source;
        if (ClientPreferences.get().save()) closeTo(parent);
        else error = "background.save_failed";
    }

    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, 0x55181B23);
        graphics.fill(0, height - 116, width, height, 0xE521252D);
        int x = left(), w = panelWidth();
        graphics.drawString(font, title, x, height - 103, 0xFFF4DCB1, false);
        if (error != null)
            graphics.drawString(font, text(error), x, height - 61, 0xFFFFA6A6);
        super.renderContents(graphics, mouseX, mouseY, delta);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
