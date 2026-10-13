package xtdpotato.xero_quest_journal.fabriccompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.util.function.Consumer;

public class FabricScreenEvent extends FabricEvents.Cancelable {
    private final Screen screen;
    public FabricScreenEvent(Screen screen) { this.screen = screen; }
    public Screen getScreen() { return screen; }
    public static class Render {
        public static class Post extends FabricScreenEvent {
            private final GuiGraphics graphics;
            public Post(Screen screen, GuiGraphics graphics) { super(screen); this.graphics = graphics; }
            public GuiGraphics getGuiGraphics() { return graphics; }
        }
    }
    public static class Init {
        public static class Post extends FabricScreenEvent {
            private final Consumer<GuiEventListener> listeners;
            public Post(Screen screen, Consumer<GuiEventListener> listeners) { super(screen); this.listeners = listeners; }
            public void addListener(GuiEventListener listener) { listeners.accept(listener); }
        }
    }
    public static class Opening {
        private final Screen current;
        private Screen next;
        public Opening(Screen current, Screen next) { this.current = current; this.next = next; }
        public Screen getCurrentScreen() { return current; }
        public Screen getNewScreen() { return next; }
        public void setNewScreen(Screen value) { next = value; }
    }
    public static class Pointer extends FabricScreenEvent {
        private final double x, y;
        private final int button;
        public Pointer(Screen screen, double x, double y, int button) { super(screen); this.x = x; this.y = y; this.button = button; }
        public double getMouseX() { return x; }
        public double getMouseY() { return y; }
        public int getButton() { return button; }
        public int getMouseButton() { return button; }
    }
    public static class MouseButtonPressed { public static class Pre extends Pointer {
        public Pre(Screen s, double x, double y, int button) { super(s, x, y, button); }
    } }
    public static class MouseButtonReleased { public static class Pre extends Pointer {
        public Pre(Screen s, double x, double y, int button) { super(s, x, y, button); }
    } }
    public static class MouseDragged { public static class Pre extends Pointer {
        private final double dx, dy;
        public Pre(Screen s, double x, double y, int b, double dx, double dy) { super(s, x, y, b); this.dx = dx; this.dy = dy; }
        public double getDragX() { return dx; }
        public double getDragY() { return dy; }
    } }
    public static class MouseScrolled { public static class Pre extends Pointer {
        private final double horizontal, vertical;
        public Pre(Screen s, double x, double y, double h, double v) { super(s, x, y, 0); horizontal = h; vertical = v; }
        public double getScrollDeltaX() { return horizontal; }
        public double getScrollDeltaY() { return vertical; }
    } }
    public static class Key extends FabricScreenEvent {
        private final int key, scan, modifiers;
        public Key(Screen screen, int key, int scan, int modifiers) { super(screen); this.key = key; this.scan = scan; this.modifiers = modifiers; }
        public int getKeyCode() { return key; }
        public int getScanCode() { return scan; }
        public int getModifiers() { return modifiers; }
    }
    public static class KeyPressed { public static class Pre extends Key {
        public Pre(Screen s, int key, int scan, int modifiers) { super(s, key, scan, modifiers); }
    } }
    public static class KeyReleased { public static class Pre extends Key {
        public Pre(Screen s, int key, int scan, int modifiers) { super(s, key, scan, modifiers); }
    } }
    public static class CharacterTyped { public static class Pre extends FabricScreenEvent {
        public Pre(Screen s) { super(s); }
    } }
}
