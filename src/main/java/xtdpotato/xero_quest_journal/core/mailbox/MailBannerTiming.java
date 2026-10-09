package xtdpotato.xero_quest_journal.core.mailbox;

import java.util.List;

/** Banner activity time is independent of attachment/mail expiry. Variable values are remaining seconds. */
public record MailBannerTiming(String mode, long seconds, boolean gameTime, String variable) {
    public static final List<String> MODES = List.of("none", "mail", "countdown", "variable");
    public static final MailBannerTiming NONE = new MailBannerTiming("none", 0, false, "");

    public MailBannerTiming {
        mode = mode == null ? "none" : mode;
        variable = variable == null ? "" : variable;
        if (!MODES.contains(mode) || seconds < 0 || seconds > 315360000L || variable.length() > 129
                || !variable.isBlank() && !variable.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}\\.[A-Za-z_][A-Za-z0-9_]{0,63}")
                || mode.equals("variable") && variable.isBlank())
            throw new IllegalArgumentException("Invalid mail banner timing");
    }
}
