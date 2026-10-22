package xtdpotato.xero_quest_journal.client;

/** Render-state GUI extraction cannot capture and replay an immediate-mode framebuffer. */
final class UiCompositor implements AutoCloseable {
    void capture(ModernGuiGraphics graphics, Runnable draw) { draw.run(); }
    void band(ModernGuiGraphics graphics, int width, int height, int top, int bottom,
              float offset, float alpha) {}
    void full(ModernGuiGraphics graphics, int width, int height, float alpha) {}
    void centered(ModernGuiGraphics graphics, int width, int height, int left, int top,
                  int right, int bottom, float scale, float alpha) {}
    void transformed(ModernGuiGraphics graphics, int width, int height, int left, int top,
                     int right, int bottom, double scale, double offsetX, double offsetY,
                     double rotation, double alpha) {}
    void bookClose(ModernGuiGraphics graphics, int width, int height, float progress) {}
    @Override public void close() {}
}
