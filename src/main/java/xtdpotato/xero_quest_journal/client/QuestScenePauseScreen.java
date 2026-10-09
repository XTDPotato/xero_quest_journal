package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/** Esc chooses a scene exit or the unmodified vanilla pause menu. */
public final class QuestScenePauseScreen extends AnimatedQuestScreen {
    private final PauseScreen gameMenu;
    private boolean leaving;
    private int waitTicks;
    private String error = "";
    public QuestScenePauseScreen(PauseScreen gameMenu) {
        super(text("pause_title"));
        this.gameMenu = gameMenu;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.structure." + key);
    }
    private int panelWidth() { return Math.min(360, width - 24); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - 134) / 2; }
    @Override protected void init() {
        clearWidgets();
        var styles = ButtonStyleSheet.load();
        int x = left() + 16, y = top() + 60;
        int w = (panelWidth() - 40) / 2;
        var leave = new StyledButton(x, y, w, 26, text(leaving ? "leaving" : "leave"),
                ignored -> {
                    if (leaving) return;
                    leaving = true; waitTicks = 0; error = "";
                    ClientStructures.leave();
                    init();
                }, styles, "scene-pause-leave").hoverMotion();
        leave.active = !leaving;
        addRenderableWidget(leave);
        var menu = new StyledButton(x + w + 8, y, w, 26, text("game_menu"),
                ignored -> minecraft.setScreen(gameMenu), styles, "scene-pause-menu").hoverMotion();
        menu.active = !leaving;
        addRenderableWidget(menu);
        addRenderableWidget(new EditorIconButton(left() + panelWidth() - 32, top() + 8,
                Component.translatable("gui.back"), this::onClose, styles, "scene-pause-close", "close"));
    }
    @Override public void tick() {
        if (!ClientStructures.inScene()) { minecraft.setScreen(null); return; }
        if (leaving && ++waitTicks >= 200) {
            leaving = false; error = "leave_failed"; init();
        }
    }
    @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        g.fill(left(), top(), left() + panelWidth(), top() + 134, 0xFF282C34);
        g.renderOutline(left(), top(), panelWidth(), 134, 0xFF6F7783);
        g.drawString(font, title, left() + 16, top() + 21, 0xFFF3F4F6, false);
        if (!error.isEmpty()) g.drawWordWrap(font, text(error), left() + 16, top() + 98,
                panelWidth() - 32, 0xFFFFACAC);
        super.renderContents(g, x, y, delta);
    }
    @Override protected boolean isModalScreen() { return true; }
    @Override protected Viewport popupBounds() {
        return viewport(left(), top(), left() + panelWidth(), top() + 134);
    }
    @Override public void onClose() { closeTo(null); }
    @Override public boolean isPauseScreen() { return false; }
}
