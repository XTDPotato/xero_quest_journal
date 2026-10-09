package xtdpotato.xero_quest_journal.client;

import net.neoforged.fml.loading.FMLPaths;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a deliberately small CSS dialect for native Minecraft widgets.
 * Unsupported declarations are ignored, so users can keep visual CSS in the
 * same file and progressively enhance it later.
 */
public final class ButtonStyleSheet {
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}", Pattern.DOTALL);
    private static final String DEFAULT_CSS = """
            /* Native journal CSS. Supported states: hover, active, disabled, focus. */
            button {
                background-color: #46556f;
                border-color: #9db4d2;
                border-width: 1px;
                color: #ffffff;
                opacity: 0.96;
                padding: 2px;
                text-shadow: false;
            }
            button:hover {
                background-color: #58749c;
                border-color: #f3d48b;
            }
            button:active {
                background-color: #31415b;
                border-color: #ffffff;
            }
            button:disabled {
                background-color: #27303d;
                border-color: #596474;
                color: #9da6b3;
                opacity: 0.68;
            }
            button:focus {
                border-color: #f3d48b;
            }
            #close {
                background-color: #fffffe;
                border-width: 0px;
                color: #50586d;
            }
            #close:hover {
                background-color: #f7ece0;
            }
            """;
    private static ButtonStyleSheet shared;
    private List<Rule> rules;
    private ButtonStyleSheet defaultThemeRules;
    private ButtonStyleSheet namedThemeRules;
    private xtdpotato.xero_quest_journal.core.model.QuestUiTheme theme;
    private Path file;
    private long modified;
    private long nextCheck;
    private int revision;

    private ButtonStyleSheet(List<Rule> rules) {
        this.rules = rules;
    }

    public static synchronized ButtonStyleSheet load() {
        if (shared != null) return shared;
        Path file = FMLPaths.CONFIGDIR.get().resolve("xero_quest").resolve("ui").resolve("journal.css");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.writeString(file, DEFAULT_CSS, StandardCharsets.UTF_8);
            }
            ButtonStyleSheet sheet = journalStyles(Files.readString(file, StandardCharsets.UTF_8));
            sheet.file = file;
            sheet.modified = Files.getLastModifiedTime(file).toMillis();
            return shared = sheet;
        } catch (IOException | IllegalArgumentException exception) {
            XeroQuestJournal.LOGGER.warn("Unable to load journal CSS {}; using defaults", file, exception);
            return shared = journalStyles(DEFAULT_CSS);
        }
    }

    public int revision() {
        return revision;
    }

    public void refreshIfChanged() {
        refreshTheme();
        long now = System.nanoTime();
        if (file == null || now < nextCheck) {
            return;
        }
        nextCheck = now + 1_000_000_000L;
        try {
            long timestamp = Files.getLastModifiedTime(file).toMillis();
            if (timestamp != modified && Files.size(file) <= 131072) {
                rules = journalStyles(Files.readString(file, StandardCharsets.UTF_8)).rules;
                modified = timestamp;
                revision++;
            }
        } catch (IOException | IllegalArgumentException exception) {
            XeroQuestJournal.LOGGER.warn("Unable to refresh CSS {}; keeping last style", file, exception);
        }
    }

    private static ButtonStyleSheet journalStyles(String source) {
        var sheet = parse(source);
        var rules = new ArrayList<>(parse(JournalListButton.DEFAULT_CSS).rules);
        rules.addAll(sheet.rules);
        sheet.rules = rules;
        return sheet;
    }

    private void refreshTheme() {
        var next = ClientQuestProgress.uiTheme();
        if (next.equals(theme)) return;
        StringBuilder defaults = new StringBuilder();
        StringBuilder named = new StringBuilder();
        for (String button : xtdpotato.xero_quest_journal.core.model.QuestUiTheme.BUTTONS) {
            String selector = button.equals("button") ? "button"
                    : button.equals("claim") ? "#claim,#claim-all" : "#" + button;
            StringBuilder css = button.equals("button") ? defaults : named;
            for (var entry : java.util.Map.of("Background", "background-color", "Border", "border-color",
                    "Text", "color").entrySet()) {
                String value = next.get(button + entry.getKey());
                if (!value.isBlank()) for (String part : selector.split(","))
                    css.append(part).append('{').append(entry.getValue()).append(':').append(value).append(";}");
            }
            for (String state : List.of("Hover", "Pressed")) {
                String value = next.get(button + state);
                if (!value.isBlank()) for (String part : selector.split(","))
                    css.append(part).append(state.equals("Hover") ? ":hover" : ":active")
                            .append("{background-color:").append(value).append(";}");
            }
        }
        defaultThemeRules = parse(defaults.toString());
        namedThemeRules = parse(named.toString());
        theme = next;
        revision++;
    }

    public static ButtonStyleSheet parse(String css) {
        if (css.length() > 131072) {
            throw new IllegalArgumentException("CSS exceeds 128 KiB");
        }
        List<Rule> rules = new ArrayList<>();
        String source = css.replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher matcher = RULE.matcher(source);
        if (source.chars().filter(c -> c == '{').count() != source.chars().filter(c -> c == '}').count()) {
            throw new IllegalArgumentException("Unbalanced CSS braces");
        }
        while (matcher.find()) {
            Map<String, String> declarations = new LinkedHashMap<>();
            for (String declaration : matcher.group(2).split(";")) {
                int colon = declaration.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                declarations.put(declaration.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT),
                        declaration.substring(colon + 1).trim());
            }
            for (String selector : matcher.group(1).split(",")) {
                String normalized = selector.trim();
                if (!normalized.isBlank()) {
                    rules.add(new Rule(normalized, declarations, specificity(normalized), rules.size()));
                }
            }
        }
        return new ButtonStyleSheet(List.copyOf(rules));
    }

    public ButtonStyle resolve(String id, Set<String> classes, boolean hovered, boolean pressed,
                               boolean disabled, boolean focused) {
        return resolveFrom(ButtonStyle.defaults(), id, classes, hovered, pressed, disabled, focused);
    }

    public ButtonStyle resolveFrom(ButtonStyle base, String id, Set<String> classes, boolean hovered, boolean pressed,
                                  boolean disabled, boolean focused) {
        return resolveRules(base, id, classes, hovered, pressed, disabled, focused, false, null);
    }

    public ButtonStyle resolveWithDefaults(ButtonStyleSheet defaults, String id, Set<String> classes,
                                           boolean hovered, boolean pressed, boolean disabled, boolean focused) {
        return resolveRules(ButtonStyle.defaults(), id, classes, hovered, pressed, disabled, focused, false, defaults);
    }

    /** Selection overrides normal/hover styling without rewriting an existing journal.css. */
    public ButtonStyle resolveSelectedFrom(ButtonStyle base, String id, Set<String> classes, boolean hovered,
                                          boolean pressed, boolean disabled, boolean focused) {
        return resolveRules(base, id, classes, hovered, pressed, disabled, focused, true, null);
    }

    public ButtonStyle resolveSelectedFrom(ButtonStyle base, ButtonStyleSheet defaults, String id, Set<String> classes,
                                          boolean hovered, boolean pressed, boolean disabled, boolean focused) {
        return resolveRules(base, id, classes, hovered, pressed, disabled, focused, true, defaults);
    }

    private ButtonStyle resolveRules(ButtonStyle base, String id, Set<String> classes, boolean hovered,
                                    boolean pressed, boolean disabled, boolean focused, boolean selected,
                                    ButtonStyleSheet defaults) {
        ButtonStyle resolved = base;
        if (this == shared) refreshTheme();
        if (defaults != null) resolved = defaults.applyMatching(resolved, id, classes,
                hovered, pressed, disabled, focused, selected);
        resolved = applyMatching(resolved, id, classes, hovered, pressed, disabled, focused, selected);
        if (this == shared && !disabled) {
            if (defaultThemeRules != null && classes.stream().noneMatch(value -> value.startsWith("journal-")))
                resolved = defaultThemeRules.applyMatching(resolved, id, classes,
                        hovered, pressed, false, focused, selected);
            if (namedThemeRules != null)
                resolved = namedThemeRules.applyMatching(resolved, id, classes,
                        hovered, pressed, false, focused, selected);
        }
        return resolved;
    }

    private ButtonStyle applyMatching(ButtonStyle base, String id, Set<String> classes,
                                      boolean hovered, boolean pressed, boolean disabled, boolean focused,
                                      boolean selected) {
        ButtonStyle result = base;
        for (Rule rule : rules.stream().filter(rule -> !selected || rule.selector().contains(":selected"))
                .filter(rule -> rule.matches(id, classes, hovered, pressed, disabled, focused, selected))
                .sorted(java.util.Comparator.comparingInt(Rule::specificity))
                .toList()) {
            for (var entry : rule.declarations.entrySet()) result = result.merge(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static int specificity(String selector) {
        int score = 0;
        for (int i = 0; i < selector.length(); i++) {
            if (selector.charAt(i) == '#') {
                score += 100;
            } else if (selector.charAt(i) == '.') {
                score += 10;
            } else if (selector.charAt(i) == ':') {
                score += 10;
            } else if (i == 0 && selector.charAt(i) != '*') {
                score += 1;
            }
        }
        return score;
    }

    private record Rule(String selector, java.util.Map<String, String> declarations, int specificity, int order) {
        private boolean matches(String id, Set<String> classes, boolean hovered, boolean pressed,
                                boolean disabled, boolean focused, boolean selected) {
            String selectorWithoutState = selector;
            for (String pseudo : List.of(":hover", ":active", ":disabled", ":focus", ":selected")) {
                if (selector.contains(pseudo)) {
                    boolean expected = switch (pseudo) {
                        case ":hover" -> hovered;
                        case ":active" -> pressed;
                        case ":disabled" -> disabled;
                        case ":focus" -> focused;
                        case ":selected" -> selected;
                        default -> false;
                    };
                    if (!expected) {
                        return false;
                    }
                    selectorWithoutState = selectorWithoutState.replace(pseudo, "");
                }
            }
            if (selectorWithoutState.equals("*") || selectorWithoutState.equals("button")) {
                return true;
            }
            if (selectorWithoutState.startsWith("#")) {
                return selectorWithoutState.substring(1).equals(id);
            }
            if (selectorWithoutState.startsWith(".")) {
                return classes.contains(selectorWithoutState.substring(1));
            }
            if (selectorWithoutState.startsWith("button.")) {
                return classes.contains(selectorWithoutState.substring("button.".length()));
            }
            if (selectorWithoutState.startsWith("button#")) {
                return selectorWithoutState.substring("button#".length()).equals(id);
            }
            return false;
        }
    }
}
