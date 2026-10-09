package xtdpotato.xero_quest_journal.client;

/** Server-controlled authoring switch. Editing controls stay hidden until explicitly enabled. */
public final class ClientEditMode {
    private static boolean enabled;
    private ClientEditMode() {}
    public static boolean enabled() { return enabled; }
    public static void set(boolean value) { enabled = value; }
    public static void reset() { enabled = false; }
}
