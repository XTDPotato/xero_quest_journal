package xtdpotato.xero_quest_journal.client;

public final class ClientEditMode {
    private static volatile boolean enabled;

    private ClientEditMode() {
    }

    public static void set(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void reset() {
        enabled = false;
    }
}
