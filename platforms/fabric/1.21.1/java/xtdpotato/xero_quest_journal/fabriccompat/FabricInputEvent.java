package xtdpotato.xero_quest_journal.fabriccompat;

public final class FabricInputEvent {
    public static class MouseButton {
        public static class Pre extends FabricEvents.Cancelable {
            private final int button, action;
            public Pre(int button, int action) { this.button = button; this.action = action; }
            public int getButton() { return button; }
            public int getAction() { return action; }
        }
    }
}
