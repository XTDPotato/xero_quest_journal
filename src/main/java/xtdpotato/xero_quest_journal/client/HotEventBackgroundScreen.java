package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;

import java.util.List;
import java.util.function.Consumer;

/** Draft editor for the full-screen hot-event backdrop. */
final class HotEventBackgroundScreen extends AnimatedQuestScreen {
    private final HotEventsScreen parent;
    private final HotEventCanvas original;
    private final Consumer<HotEventCanvas> saved;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String type;
    private String source;
    private String scale;
    private String offsetX;
    private String offsetY;
    private String error = "";

    HotEventBackgroundScreen(HotEventsScreen parent, HotEventCanvas canvas, Consumer<HotEventCanvas> saved) {
        super(text("menu.background"));
        this.parent = parent;
        this.original = canvas;
        this.saved = saved;
        type = canvas.backgroundType();
        source = canvas.backgroundImage();
        scale = Double.toString(canvas.backgroundScale());
        offsetX = Integer.toString(canvas.backgroundOffsetX());
        offsetY = Integer.toString(canvas.backgroundOffsetY());
    }

    private static Component text(String name) {
        return Component.translatable("screen.xero_quest_journal.hot_events." + name);
    }

    private int panelWidth() { return Math.min(420, width - 24); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return Math.max(4, (height - 220) / 2); }

    @Override protected int animationHeaderBottom() { return panelTop(); }
    @Override protected int animationFooterTop() { return Math.min(height, panelTop() + 220); }
    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() {
        return viewport(panelLeft() - 2, panelTop() - 2,
                panelLeft() + panelWidth() + 2, panelTop() + 216);
    }
    @Override protected void renderModalBackground(GuiGraphics graphics, float delta) {
        parent.renderContents(graphics, -10000, -10000, delta);
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = panelLeft(), y = panelTop(), w = panelWidth();
        addRenderableWidget(new StyledChoiceButton<>(x + 138, y + 36, w - 154, 20,
                text("background_type"), List.of("image", "video"), type,
                value -> text("background_type." + value), value -> {
                    type = value;
                    source = "";
                    init();
                }, styles, "hot-background-type"));
        EditBox address = new EditBox(font, x + 138, y + 66, w - 190, 20, text("background_source"));
        address.setMaxLength(4096);
        address.setValue(source);
        address.setResponder(value -> source = value);
        addRenderableWidget(address);
        StyledButton pick = new StyledButton(x + w - 46, y + 66, 30, 20, Component.literal("..."),
                ignored -> {
                    if (type.equals("video")) {
                        QuestMediaPicker.choose(this, true, value -> {
                            source = value;
                            init();
                        });
                    } else minecraft.setScreen(QuestScreen.pickResource(this, source, false, value -> source = value));
                }, styles, "hot-background-choose", "toolbar-button");
        pick.setTooltip(Tooltip.create(text("choose_background")));
        addRenderableWidget(pick);
        field("background_scale", scale, x + 138, y + 96, w - 154, value -> scale = value);
        field("background_offset_x", offsetX, x + 138, y + 126, (w - 162) / 2, value -> offsetX = value);
        field("background_offset_y", offsetY, x + 142 + (w - 162) / 2, y + 126,
                (w - 162) / 2, value -> offsetY = value);
        addRenderableWidget(new StyledButton(x + 16, y + 180, (w - 38) / 2, 22,
                Component.translatable("gui.done"), ignored -> save(), styles, "hot-background-save"));
        addRenderableWidget(new StyledButton(x + 22 + (w - 38) / 2, y + 180,
                (w - 38) / 2, 22, Component.translatable("gui.cancel"),
                ignored -> onClose(), styles, "hot-background-cancel"));
    }

    private void field(String key, String value, int x, int y, int w, Consumer<String> changed) {
        EditBox box = new EditBox(font, x, y, Math.max(22, w), 20, text(key));
        box.setMaxLength(16);
        box.setValue(value);
        box.setResponder(changed);
        addRenderableWidget(box);
    }

    private void save() {
        try {
            String path = source.strip();
            if (!path.isEmpty() && (type.equals("video")
                    ? !ClientMediaManager.validVideoSource(path)
                    : !ClientMediaManager.validImageSource(path) && !QuestIcons.valid(path)))
                throw new IllegalArgumentException("Invalid source");
            var next = new HotEventCanvas(path, original.startId(), original.connections(),
                    true, original.positions(), type, Double.parseDouble(scale.strip()),
                    Integer.parseInt(offsetX.strip()), Integer.parseInt(offsetY.strip()));
            saved.accept(next);
            minecraft.setScreen(parent);
        } catch (IllegalArgumentException invalid) {
            error = text("invalid_background").getString();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, 0xE8181B21);
        int x = panelLeft(), y = panelTop(), w = panelWidth();
        graphics.fill(x - 2, y - 2, x + w + 2, y + 216, 0xFFD9C18F);
        graphics.fill(x, y, x + w, y + 214, 0xFF292E36);
        graphics.drawString(font, text("menu.background"), x + 16, y + 12, 0xFFF3DEB6);
        graphics.drawString(font, text("background_type"), x + 16, y + 42, 0xFFEAF0F6);
        graphics.drawString(font, text("background_source"), x + 16, y + 72, 0xFFEAF0F6);
        graphics.drawString(font, text("background_scale"), x + 16, y + 102, 0xFFEAF0F6);
        graphics.drawString(font, text("background_offset_x"), x + 16, y + 132, 0xFFEAF0F6);
        if (!error.isBlank()) graphics.drawString(font, error, x + 16, y + 160, 0xFFFF7777);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
