package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.QuestConfig;

import java.util.List;
import java.util.Optional;

public abstract class AnimatedQuestScreen extends Screen {
    protected static final int JOURNAL_BACKDROP = 0x88424A5C;
    protected static final int JOURNAL_HEADER = 0xA325293A;
    private final UiMotion motion = new UiMotion(false, System.nanoTime());
    private final UiCompositor compositor = new UiCompositor();
    private final UiCompositor popupCompositor = new UiCompositor();
    private final UiMotion popupMotion = new UiMotion(false, System.nanoTime());
    private final java.util.Map<String, MenuTransition> menuTransitions = new java.util.LinkedHashMap<>();
    private final java.util.Set<String> renderedMenuSlots = new java.util.HashSet<>();
    private Object lastPopup;
    private Viewport popupTransitionBounds;
    private int popupBackdrop;
    private boolean popupSurfaceIsolated;
    private boolean entered;
    private boolean closing;
    private boolean closingBook;
    private boolean capturing;
    private Screen destination;
    private int backdrop = 0xE51D2025;

    protected AnimatedQuestScreen(Component title) { super(title); }

    public static int animationMillis() {
        return ClientPreferences.get().reducedMotion ? 0
                : Math.max(0, Math.min(2000, QuestConfig.defaults().uiAnimationMillis));
    }

    public static int popupAnimationMillis() {
        return animationMillis() == 0 ? 0 : ClientPreferences.get().popupAnimationMillis;
    }

    private int screenAnimationMillis() {
        return isModalScreen() || popupKey() != null ? popupAnimationMillis() : animationMillis();
    }

    public final boolean isClosing() { return closing; }
    protected final boolean isClosingBook() { return closingBook; }
    public final boolean inputBlocked() {
        return closing || !entered && screenAnimationMillis() > 0 || entered && !motion.settled()
                || !popupMotion.settled() && (popupMotion.visible() || blocksInputWhilePopupClosing())
                || popupKey() != lastPopup && popupAnimationMillis() > 0
                || popupAnimationMillis() > 0 && menuTransitions.values().stream().anyMatch(MenuTransition::transitioning);
    }
    public final double animationPosition() { return motion.position(); }
    public final double popupAnimationPosition() { return popupMotion.position(); }
    public final double menuAnimationPosition(String slot) {
        var menu = menuTransitions.get(slot);
        return menu == null ? 0 : menu.position();
    }

    protected final void renderAnimatedMenu(GuiGraphics graphics, String slot, Object key,
                                            Viewport bounds, Runnable draw) {
        renderAnimatedMenu(graphics, slot, key, bounds, 0, draw);
    }

    protected final void closeAnimatedMenu(String slot) {
        MenuTransition transition = menuTransitions.get(slot);
        if (transition != null) transition.close();
        renderedMenuSlots.remove(slot);
    }

    protected final void renderAnimatedMenu(GuiGraphics graphics, String slot, Object key,
                                            Viewport bounds, int scrim, Runnable draw) {
        renderedMenuSlots.add(slot);
        menuTransitions.computeIfAbsent(slot, ignored -> new MenuTransition())
                .render(graphics, width, height, key, bounds, scrim, draw);
    }

    public final void reopen() {
        closing = false;
        destination = null;
        resetInteraction();
        motion.target(true, System.nanoTime(), screenAnimationMillis());
    }

    protected final void closeTo(Screen next) {
        if (closing) return;
        closingBook = false;
        if (screenAnimationMillis() == 0 || !entered) {
            minecraft.setScreen(next);
            return;
        }
        closing = true;
        destination = next;
        motion.target(false, System.nanoTime(), screenAnimationMillis());
        resetInteraction();
    }

    /** Reusable horizontal page-fold transition for book-like full-screen surfaces. */
    protected final void closeBookTo(Screen next) {
        if (closing) return;
        if (screenAnimationMillis() == 0 || !entered) {
            minecraft.setScreen(next);
            return;
        }
        closing = true;
        closingBook = true;
        destination = next;
        motion.target(false, System.nanoTime(), screenAnimationMillis());
        resetInteraction();
    }

    private void resetInteraction() {
        clearFocus();
        children().forEach(child -> child.setFocused(false));
        setDragging(false);
    }

    public final void advanceTransition() {
        if (!entered) return;
        if (closing) resetInteraction();
        motion.advance(System.nanoTime(), screenAnimationMillis());
        if (closing && motion.settled() && minecraft.screen == this) {
            Screen next = destination;
            closing = false;
            minecraft.setScreen(next);
        }
    }

    protected int animationHeaderBottom() { return 32; }
    protected int animationFooterTop() { return Math.max(animationHeaderBottom(), height - 68); }
    protected Object popupKey() { return null; }
    protected boolean blocksInputWhilePopupClosing() { return true; }
    protected int popupScrimColor() { return 0xB8181B23; }
    protected boolean isolatePopupSurface(Object key) { return false; }
    protected void renderPopupBase(GuiGraphics graphics, int x, int y, float delta) {}
    protected void renderPopupSurface(GuiGraphics graphics, int x, int y, float delta) {
        renderWithChoicePopup(graphics, x, y, delta);
    }
    protected final boolean popupTransitionActive() {
        return lastPopup != null || popupKey() != null || !popupMotion.settled();
    }
    protected Viewport popupBounds() { return new Viewport(0, 0, width, height); }
    protected boolean isModalScreen() { return false; }
    protected boolean blurWorldBackground() { return false; }
    protected void renderBeforeBlur(GuiGraphics graphics, float delta) {}
    protected void renderModalBackground(GuiGraphics graphics, float delta) {}
    protected Viewport modalScrimBounds() { return viewport(0, 0, width, height); }
    protected boolean preserveTrackingHudBehind() { return false; }
    public final boolean showsTrackingHudBehind() { return preserveTrackingHudBehind(); }
    protected final Viewport viewport(int left, int top, int right, int bottom) {
        return new Viewport(left, top, right, bottom);
    }

    protected final void backdrop(GuiGraphics graphics, int color) {
        String configured = ClientQuestProgress.uiTheme().get("background");
        if (!configured.isBlank()) color = (color & 0xFF000000)
                | (ButtonStyle.parseColor(configured, color) & 0x00FFFFFF);
        backdrop = color;
        if (!capturing) graphics.fill(0, 0, width, height, color);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int x, int y, float delta) {
        // World blur runs before capture in render(), never inside an offscreen UI target.
    }

    @Override
    public final void render(GuiGraphics graphics, int x, int y, float delta) {
        if (!entered) {
            entered = true;
            motion.target(true, System.nanoTime(), screenAnimationMillis());
        }
        motion.advance(System.nanoTime(), screenAnimationMillis());
        if (isModalScreen()) {
            renderModal(graphics, x, y, delta);
            return;
        }
        renderBeforeBlur(graphics, delta);
        if (blurWorldBackground()) {
            graphics.flush();
            renderBlurredBackground(delta);
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        }
        if (motion.settled() && !closing) {
            compositor.close();
            renderPopupLayer(graphics, x, y, delta);
            return;
        }
        capturing = true;
        try {
            compositor.capture(graphics, () -> renderWithChoicePopup(graphics, -10000, -10000, delta));
        } finally {
            capturing = false;
        }
        float alpha = motion.eased();
        if (closingBook) {
            int color = (backdrop & 0xFFFFFF) | Math.round((backdrop >>> 24) * alpha) << 24;
            graphics.fill(0, 0, width, height, color);
            compositor.bookClose(graphics, width, height, 1 - alpha);
            return;
        }
        int color = (backdrop & 0xFFFFFF) | Math.round((backdrop >>> 24) * alpha) << 24;
        graphics.fill(0, 0, width, height, color);
        if (popupKey() != null) {
            compositor.full(graphics, width, height, alpha);
            lastPopup = popupKey();
            popupMotion.target(true, System.nanoTime(), 0);
        } else {
            compositor.full(graphics, width, height, alpha);
        }
    }

    public static float controlEntranceOffset(int screenHeight, double controlCenterY, float progress) {
        if (screenHeight <= 0) return 0;
        float normalizedY = (float) (controlCenterY / screenHeight * 2 - 1);
        float travel = Math.max(24, Math.min(40, screenHeight * 0.06F));
        return normalizedY * travel * (1 - Math.clamp(progress, 0, 1));
    }

    private void renderModal(GuiGraphics graphics, int x, int y, float delta) {
        renderModalBackground(graphics, delta);
        graphics.flush();
        // The parent has finished painting; its item/tooltip depth must not occlude the modal scrim.
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                net.minecraft.client.Minecraft.ON_OSX);
        float alpha = motion.eased();
        // Keep the scrim above parent item depth, then composite an isolated panel over it.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        Viewport scrim = modalScrimBounds();
        int scrimColor = popupScrimColor();
        graphics.fill(scrim.left(), scrim.top(), scrim.right(), scrim.bottom(),
                Math.round((scrimColor >>> 24) * alpha) << 24 | scrimColor & 0x00FFFFFF);
        graphics.flush();
        graphics.pose().popPose();
        capturing = true;
        try {
            compositor.capture(graphics, () -> renderWithChoicePopup(graphics,
                    inputBlocked() ? -10000 : x, inputBlocked() ? -10000 : y, delta));
        } finally {
            capturing = false;
        }
        if (closingBook) {
            compositor.bookClose(graphics, width, height, 1 - alpha);
            return;
        }
        Viewport bounds = popupBounds();
        compositor.centered(graphics, width, height, bounds.left(), bounds.top(),
                bounds.right(), bounds.bottom(), 0.88F + 0.12F * alpha, alpha);
    }

    private void renderPopupLayer(GuiGraphics graphics, int x, int y, float delta) {
        Object currentPopup = popupKey();
        long now = System.nanoTime();
        if (currentPopup != lastPopup) {
            resetInteraction();
            if (currentPopup != null && lastPopup != null) popupMotion.target(false, now, 0);
            popupMotion.target(currentPopup != null, now, popupAnimationMillis());
            lastPopup = currentPopup;
            if (currentPopup != null) popupSurfaceIsolated = isolatePopupSurface(currentPopup);
        }
        popupMotion.advance(now, popupAnimationMillis());
        if (popupSurfaceIsolated) renderPopupBase(graphics, x, y, delta);
        if (currentPopup == null) {
            if (!popupSurfaceIsolated) renderWithChoicePopup(graphics, x, y, delta);
            if (popupMotion.position() == 0) {
                popupCompositor.close();
                popupTransitionBounds = null;
                popupSurfaceIsolated = false;
                return;
            }
        } else {
            capturing = true;
            try {
                popupCompositor.capture(graphics, () -> {
                    int drawX = popupMotion.settled() ? x : -10000;
                    int drawY = popupMotion.settled() ? y : -10000;
                    if (popupSurfaceIsolated) renderPopupSurface(graphics, drawX, drawY, delta);
                    else renderWithChoicePopup(graphics, drawX, drawY, delta);
                });
                popupBackdrop = backdrop;
            } finally {
                capturing = false;
            }
            popupTransitionBounds = popupBounds();
        }
        float alpha = popupMotion.eased();
        graphics.flush();
        // Parent item models may write depth beyond ordinary widget layers.
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                net.minecraft.client.Minecraft.ON_OSX);
        int scrim = popupScrimColor();
        graphics.fill(0, 0, width, height,
                (scrim & 0xFFFFFF) | Math.round((scrim >>> 24) * alpha) << 24);
        float eased = popupMotion.eased();
        Viewport bounds = currentPopup != null || popupTransitionBounds == null
                ? popupBounds() : popupTransitionBounds;
        popupCompositor.centered(graphics, width, height, bounds.left(), bounds.top(),
                bounds.right(), bounds.bottom(), 0.88F + 0.12F * eased, alpha);
    }

    private void renderWithChoicePopup(GuiGraphics graphics, int x, int y, float delta) {
        renderedMenuSlots.clear();
        renderContents(graphics, x, y, delta);
        StyledChoiceButton<?> choice = openChoice();
        renderAnimatedMenu(graphics, "choice", choice, choice == null ? viewport(0, 0, 0, 0)
                : choice.popupBounds(), () -> choice.renderPopup(graphics, x, y));
        // A screen may return early when a menu action opens a property form.
        // Still advance and draw the previous menu's exit in that case.
        for (var entry : menuTransitions.entrySet()) if (!renderedMenuSlots.contains(entry.getKey()))
            entry.getValue().render(graphics, width, height, null, viewport(0, 0, 0, 0), 0, () -> {});
    }

    protected record Viewport(int left, int top, int right, int bottom) {
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        public boolean intersects(AbstractWidget widget) {
            return widget.getRight() > left && widget.getX() < right
                    && widget.getBottom() > top && widget.getY() < bottom;
        }
    }

    protected Viewport widgetViewport(AbstractWidget widget) { return null; }

    private StyledChoiceButton<?> openChoice() {
        return children().stream().filter(StyledChoiceButton.class::isInstance)
                .map(StyledChoiceButton.class::cast).filter(StyledChoiceButton::isOpen)
                .findFirst().orElse(null);
    }
    protected final boolean hasChoicePopup() { return openChoice() != null; }

    protected final boolean scrollChoicePopup(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        StyledChoiceButton<?> choice = openChoice();
        return choice != null && choice.mouseScrolled(x, y, horizontal, vertical);
    }

    protected final boolean clickChoicePopup(double x, double y, int button) {
        if (inputBlocked()) return true;
        StyledChoiceButton<?> choice = openChoice();
        if (choice == null) return false;
        choice.mouseClicked(x, y, button);
        if (children().contains(choice)) {
            if (getFocused() != choice) {
                setFocused(choice);
            }
            if (button == 0) setDragging(true);
        }
        return true;
    }

    protected final boolean dragChoicePopup(double x, double y, int button, double dx, double dy) {
        if (inputBlocked()) return true;
        var choice = openChoice();
        return choice != null && choice.mouseDragged(x, y, button, dx, dy);
    }

    protected final boolean releaseChoicePopup(double x, double y, int button) {
        if (inputBlocked()) return true;
        var choice = openChoice();
        return choice != null && choice.mouseReleased(x, y, button);
    }

    private boolean acceptsPointer(GuiEventListener child, double x, double y) {
        if (!(child instanceof AbstractWidget widget)) return true;
        Viewport viewport = widgetViewport(widget);
        return widget.visible && (viewport == null || viewport.contains(x, y) && viewport.intersects(widget));
    }

    private void discardClippedFocus() {
        if (getFocused() instanceof AbstractWidget widget) {
            Viewport viewport = widgetViewport(widget);
            if (!widget.visible || viewport != null && !viewport.intersects(widget)) {
                setFocused(null);
                setDragging(false);
            }
        }
    }

    protected void renderContents(GuiGraphics graphics, int x, int y, float delta) {
        discardClippedFocus();
        renderBackground(graphics, x, y, delta);
        // Include vanilla input backgrounds, cursors and selection in the viewport.
        for (var child : children()) {
            if (!(child instanceof net.minecraft.client.gui.components.Renderable renderable)) continue;
            if (!(child instanceof AbstractWidget widget)) {
                renderable.render(graphics, x, y, delta);
                continue;
            }
            Viewport viewport = widgetViewport(widget);
            if (viewport != null && !viewport.intersects(widget)) continue;
            float offsetY = motion.settled() ? 0
                    : controlEntranceOffset(height, widget.getY() + widget.getHeight() / 2.0, motion.eased());
            graphics.pose().pushPose();
            graphics.pose().translate(0, offsetY, 0);
            try {
                if (viewport != null)
                    graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
                try {
                    renderable.render(graphics, viewport == null || viewport.contains(x, y) ? x : -10000,
                            viewport == null || viewport.contains(x, y) ? y : -10000, delta);
                } finally {
                    if (viewport != null) graphics.disableScissor();
                }
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    @Override
    public Optional<GuiEventListener> getChildAt(double x, double y) {
        StyledChoiceButton<?> choice = openChoice();
        if (choice != null && choice.popupContains(x, y)) {
            return Optional.of(choice);
        }
        return children().stream().filter(child -> acceptsPointer(child, x, y) && child.isMouseOver(x, y))
                .map(GuiEventListener.class::cast).findFirst();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        StyledChoiceButton<?> choice = openChoice();
        for (var child : List.copyOf(children())) {
            if (child == choice) continue;
            if (acceptsPointer(child, x, y) && child.mouseClicked(x, y, button)) {
                if (child instanceof StyledChoiceButton<?> nextChoice) {
                    children().stream().filter(StyledChoiceButton.class::isInstance)
                            .map(StyledChoiceButton.class::cast).filter(other -> other != nextChoice)
                            .forEach(StyledChoiceButton::closeMenu);
                }
                if (children().contains(child)) {
                    if (getFocused() != child) {
                        setFocused(child);
                    }
                    if (button == 0) setDragging(true);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollChoicePopup(x, y, horizontal, vertical)
                || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (getFocused() != null && !acceptsPointer(getFocused(), x, y)) return false;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (releaseChoicePopup(x, y, button)) return true;
        boolean handled = false;
        // Some screens route presses directly; release held widgets even outside their bounds.
        for (var child : List.copyOf(children())) {
            if (child instanceof StyledButton widget) {
                boolean insideViewport = acceptsPointer(widget, x, y);
                handled |= widget.mouseReleased(insideViewport ? x : -100000,
                        insideViewport ? y : -100000, button);
            }
        }
        setDragging(false);
        return handled || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        discardClippedFocus();
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (inputBlocked()) return true;
        discardClippedFocus();
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        menuTransitions.values().forEach(MenuTransition::close);
        menuTransitions.clear();
        compositor.close();
        popupCompositor.close();
        popupMotion.target(false, System.nanoTime(), 0);
        lastPopup = null;
        popupTransitionBounds = null;
        popupSurfaceIsolated = false;
        // Returning from a child restores the same page, not a second entrance animation.
        if (entered) {
            closing = false;
            closingBook = false;
            destination = null;
            motion.target(true, System.nanoTime(), 0);
        }
        super.removed();
    }
}
