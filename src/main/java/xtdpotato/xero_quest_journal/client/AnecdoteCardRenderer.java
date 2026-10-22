package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import xtdpotato.xero_quest_journal.core.model.AnecdoteCardLayout;

/** Shared card drawing and geometry for the live book and the visual authoring preview. */
final class AnecdoteCardRenderer {
    static final double WIDTH = 100, HEIGHT = 125;
    record Box(double x, double y, double width, double height) {
        double right() { return x + width; }
        double bottom() { return y + height; }
        boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }
    private AnecdoteCardRenderer() {}

    static Box bounds(AnecdoteCardLayout.Part part) {
        double width = WIDTH * part.width() / 100, height = HEIGHT * part.height() / 100;
        return new Box(WIDTH * part.x() / 100 + width * (1 - part.scale()) / 2,
                HEIGHT * part.y() / 100 + height * (1 - part.scale()) / 2,
                width * part.scale(), height * part.scale());
    }

    static void render(GuiGraphics g, String icon, String caption, AnecdoteCardLayout layout,
                       int x, int y, int width, int height, boolean discovered) {
        if (width <= 0 || height <= 0) return;
        var font = Minecraft.getInstance().font;
        g.enableScissor(x, y, x + width, y + height);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) (width / WIDTH), (float) (height / HEIGHT), 1);
        Box background = bounds(layout.background()), image = bounds(layout.image()), text = bounds(layout.text());
        int backgroundColor = discovered ? ButtonStyle.parseColor(layout.backgroundColor(), 0xFFF8F0DF) : 0xFFD9D0BE;
        g.fill((int) background.x(), (int) background.y(), (int) Math.ceil(background.right()),
                (int) Math.ceil(background.bottom()), backgroundColor);
        if (!layout.backgroundImage().isBlank()) drawImage(g, layout.backgroundImage(), background, discovered ? 1 : .45F);
        if (!icon.isBlank()) drawImage(g, icon, image, discovered ? 1 : .45F);
        else {
            g.pose().pushPose();
            g.pose().translate(image.x(), image.y(), 0);
            g.pose().scale((float) (image.width() / 16), (float) (image.height() / 16), 1);
            AnecdoteBookArt.mapScroll(g, 0, 0, 16, discovered ? 0xFFD2AD69 : 0xFFACA28D);
            g.pose().popPose();
        }
        // Flush item depth before drawing the caption and author selection handles.
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        var label = QuestRichText.parse(caption, Style.EMPTY);
        float factor = (float) Math.min(layout.fontSize() / font.lineHeight * layout.text().scale(),
                Math.min(text.width() / Math.max(1, font.width(label)), text.height() / font.lineHeight));
        g.fill((int) text.x(), (int) text.y() - 3, (int) text.right(), (int) text.y() - 2, 0x55B9A77E);
        g.pose().translate(text.x() + text.width() / 2, text.y() + text.height() / 2, 0);
        g.pose().scale(factor, factor, 1);
        g.drawString(font, label, -font.width(label) / 2, -font.lineHeight / 2,
                discovered ? ButtonStyle.parseColor(layout.textColor(), 0xFF4F5664) : 0xFF827D70, false);
        g.pose().popPose();
        g.pose().popPose();
        g.disableScissor();
    }

    private static void drawImage(GuiGraphics g, String source, Box box, float alpha) {
        QuestIcons.render(g, source, (int) Math.round(box.x()), (int) Math.round(box.y()),
                Math.max(1, (int) Math.round(box.width())), Math.max(1, (int) Math.round(box.height())), alpha);
    }
}
