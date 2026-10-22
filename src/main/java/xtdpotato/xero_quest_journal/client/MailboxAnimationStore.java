package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local, editable mailbox animation sources and their safe native rendering values. */
public final class MailboxAnimationStore {
    public static final List<String> EVENTS = List.of(
            "favorite", "unfavorite", "hover", "press", "row_hover", "row_selected",
            "claim_all_hover", "claim_all_click", "delete_read_hover", "delete_read_click",
            "claim_hover", "claim_click", "delete_hover", "delete_click",
            "close_hover", "close_click", "edit_hover", "edit_click",
            "compose_hover", "compose_click",
            "cancel_hover", "cancel_click", "confirm_hover", "confirm_click", "notice", "new_badge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern PROPERTY = Pattern.compile("--([a-zA-Z0-9_-]+)\\s*:\\s*([^;}]*)");
    private static final Pattern STAR_SCALE = Pattern.compile("star\\.scale\\s*\\(\\s*([0-9.]+)");
    private static final Pattern RIPPLE_SCALE = Pattern.compile("ripple\\s*\\(\\s*([0-9.]+)");
    private static final Pattern DOT_COUNT = Pattern.compile("dots\\s*\\(\\s*([0-9]+)");
    private static final Pattern ROW_SCALE = Pattern.compile("row\\.scale\\s*\\(\\s*([0-9.]+)");
    private static final Pattern DURATION = Pattern.compile("(?:duration|animation\\.duration)\\s*\\(\\s*([0-9]+)");
    private static final String LEGACY_FAVORITE_HTML = """
            <button class="mail-favorite" data-state="empty">
              <span class="star"></span>
              <span class="ripple"></span>
              <span class="dots"></span>
            </button>
            """;
    private static final String LEGACY_FAVORITE_CSS = """
            .mail-favorite {
              --button-color:#ffffff;
              --button-hover-color:#fff1da;
              --button-pressed-color:#e7d9c5;
              --button-border-color:#ffffff;
              --star-color:#ffd900;
              --star-empty-color:#505a70;
              --ripple-color:#f3cc59;
              --dot-color:#fff4c7;
              --star-scale:1.42;
              --ripple-scale:1.85;
              --dot-count:8;
              --duration:520ms;
              --hover-scale:1.05;
            }
            """;
    private static final String LEGACY_FAVORITE_JS = """
            function onFavorite(event) {
              star.scale(1.42);
              ripple(1.85);
              dots(8);
              duration(520);
            }
            """;
    private static final String LEGACY_UNFAVORITE_JS = """
            function onUnfavorite(event) {
              star.scale(1.28);
              ripple(1.65);
              dots(6);
              duration(460);
            }
            """;
    private static Config current;
    private static Config preview;
    private static final Map<String, Style> styleCache = new HashMap<>();
    private static final Map<String, Boolean> partCache = new HashMap<>();

    private MailboxAnimationStore() {}

    public static Config get() {
        if (current == null) {
            try {
                current = Files.exists(file()) ? GSON.fromJson(Files.readString(file()), Config.class) : null;
            } catch (Exception exception) {
                XeroQuestJournal.LOGGER.warn("Unable to load mailbox animation config", exception);
            }
            if (current == null) current = defaults();
            current.normalize();
        }
        return current;
    }

    public static Animation animation(String event) {
        Config config = preview == null ? get() : preview;
        Animation result = config.events.get(event);
        return result == null ? config.events.get("favorite") : result;
    }

    public static void preview(Config draft) {
        preview = draft;
        styleCache.clear();
        partCache.clear();
    }
    public static void clearPreview() {
        preview = null;
        styleCache.clear();
        partCache.clear();
    }

    public static boolean hasPart(String event, String part) {
        if (!List.of("star", "ripple", "dots").contains(part)) return false;
        return partCache.computeIfAbsent(event + ":" + part,
                ignored -> org.jsoup.Jsoup.parseBodyFragment(animation(event).html)
                        .selectFirst("." + part) != null);
    }

    public static float rowHoverScale() {
        Animation source = animation("row_hover");
        Matcher matcher = PROPERTY.matcher(source.css);
        float fallback = 1.05F;
        while (matcher.find()) {
            if (matcher.group(1).equals("row-scale")) {
                try { fallback = Math.clamp(Float.parseFloat(matcher.group(2).trim()), 1F, 1.2F); }
                catch (NumberFormatException ignored) { }
            }
        }
        return number(source.js, ROW_SCALE, fallback);
    }

    public static String rowCss() {
        return animation("row_hover").css + "\n" + animation("row_selected").css;
    }

    public static String property(String event, String key, String fallback) {
        Matcher matcher = PROPERTY.matcher(animation(event).css);
        String result = fallback;
        while (matcher.find()) if (matcher.group(1).equals(key)) result = matcher.group(2).strip();
        return result;
    }

    public static int color(String event, String key, int fallback) {
        return ButtonStyle.parseColor(property(event, key, ""), fallback);
    }

    public static float scalar(String event, String key, float fallback, float minimum, float maximum) {
        try {
            float value = Float.parseFloat(property(event, key, Float.toString(fallback)).replace("px", ""));
            return Float.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
        } catch (NumberFormatException invalid) { return fallback; }
    }

    public static String attribute(String event, String key, String fallback) {
        var element = org.jsoup.Jsoup.parseBodyFragment(animation(event).html).selectFirst("[data-" + key + "]");
        return element == null ? fallback : element.attr("data-" + key);
    }

    public static Style style(String event) {
        Style cached = styleCache.get(event);
        if (cached != null) return cached;
        Animation source = animation(event);
        Map<String, String> properties = new LinkedHashMap<>();
        for (String text : List.of(source.css, source.js)) {
            Matcher matcher = PROPERTY.matcher(text == null ? "" : text);
            while (matcher.find()) properties.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).trim());
        }
        Style base = Style.defaults();
        base = base.withColor("button-color", properties.get("button-color"))
                .withColor("button-hover-color", properties.get("button-hover-color"))
                .withColor("button-pressed-color", properties.get("button-pressed-color"))
                .withColor("button-border-color", properties.get("button-border-color"))
                .withColor("star-color", properties.get("star-color"))
                .withColor("star-empty-color", properties.get("star-empty-color"))
                .withColor("ripple-color", properties.get("ripple-color"))
                .withColor("dot-color", properties.get("dot-color"))
                .withFloat("star-scale", properties.get("star-scale"))
                .withFloat("ripple-scale", properties.get("ripple-scale"))
                .withFloat("hover-scale", properties.get("hover-scale"))
                .withInt("dot-count", properties.get("dot-count"))
                .withInt("duration", properties.get("duration"));
        Style result = new Style(
                base.buttonColor,
                base.buttonHoverColor,
                base.buttonPressedColor,
                base.buttonBorderColor,
                base.starColor,
                base.starEmptyColor,
                base.rippleColor,
                base.dotColor,
                number(source.js, STAR_SCALE, base.starScale),
                number(source.js, RIPPLE_SCALE, base.rippleScale),
                number(source.js, DOT_COUNT, base.dotCount),
                number(source.js, DURATION, base.durationMillis),
                base.hoverScale);
        styleCache.put(event, result);
        return result;
    }

    public static boolean save(Config value) {
        try {
            value.normalize();
            AtomicFiles.write(file(), GSON.toJson(value), true);
            current = value;
            preview = null;
            styleCache.clear();
            partCache.clear();
            return true;
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to save mailbox animation config", exception);
            return false;
        }
    }

    public static Config copy(Config source) {
        Config result = new Config();
        source.normalize();
        result.paletteVersion = source.paletteVersion;
        result.noticeVersion = source.noticeVersion;
        result.favoriteAnimationVersion = source.favoriteAnimationVersion;
        result.rowAnimationVersion = source.rowAnimationVersion;
        source.events.forEach((key, value) -> result.events.put(key, new Animation(value.html, value.css, value.js)));
        return result;
    }

    private static int number(String source, Pattern pattern, int fallback) {
        Matcher matcher = pattern.matcher(source == null ? "" : source);
        if (!matcher.find()) return fallback;
        try { return Math.max(1, Math.min(2000, Integer.parseInt(matcher.group(1)))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static float number(String source, Pattern pattern, float fallback) {
        Matcher matcher = pattern.matcher(source == null ? "" : source);
        if (!matcher.find()) return fallback;
        try {
            float value = Float.parseFloat(matcher.group(1));
            return Float.isFinite(value) ? Math.clamp(value, .1F, 4.0F) : fallback;
        } catch (NumberFormatException ignored) { return fallback; }
    }

    private static Config defaults() {
        Config result = new Config();
        result.paletteVersion = 1;
        result.noticeVersion = 4;
        result.favoriteAnimationVersion = 1;
        result.rowAnimationVersion = 1;
        for (String event : EVENTS) result.events.put(event, defaultAnimation(event));
        return result;
    }

    private static Animation defaultAnimation(String event) {
        if (event.equals("notice")) return new Animation(
                "<div class=\"mail-notice\" data-icon=\"mail_envelope\" data-text=\"\" data-background=\"\" data-close=\"false\"></div>",
                ".mail-notice { --background:#1d202680; --border:#0a0c1080; --inner-border:#80858d80; --text:#f4f5f7;"
                        + " --icon:#c8c8c880; --width:250; --height:30; --text-scale:1.6; --duration:6000; }", "");
        if (event.equals("new_badge")) return new Animation(
                "<span class=\"mail-new\" data-text=\"\" data-frame=\"mail_new_frame\" data-background=\"mail_new_bg\"></span>",
                ".mail-new { --background:#c88008; --border:#f0c038; --text:#f8f070; }", "");
        String html = LEGACY_FAVORITE_HTML;
        String css = """
                .mail-favorite {
                  --button-color:#ffffff;
                  --button-hover-color:#fff1da;
                  --button-pressed-color:#e7d9c5;
                  --button-border-color:#ffffff;
                  --star-color:#ffd900;
                  --star-empty-color:#505a70;
                  --ripple-color:#f3cc59;
                  --dot-color:#fff4c7;
                  --star-scale:1.42;
                  --ripple-scale:1.85;
                  --dot-count:3;
                  --duration:900ms;
                  --hover-scale:1.05;
                }
                """;
        String js = switch (event) {
            case "favorite" -> """
                    function onFavorite(event) {
                      star.scale(1.42);
                      ripple(1.85);
                      dots(3);
                      duration(900);
                    }
                    """;
            case "unfavorite" -> """
                    function onUnfavorite(event) {
                      star.scale(1.28);
                      ripple(1.65);
                      dots(3);
                      duration(820);
                    }
                    """;
            case "hover" -> "function onHover(event) { star.scale(1.08); }";
            case "press" -> "function onPress(event) { star.scale(0.92); }";
            case "row_hover" -> "function onRowHover(event) { row.scale(1.05); }";
            case "row_selected" -> "function onRowSelected(event) { row.fade(1); }";
            default -> "";
        };
        if (event.equals("row_hover")) {
            html = "<button class=\"mail-row\"></button>";
            css = ".mail-row:hover { border-color:#ffffff; background-color:#394656; }\n"
                    + ".mail-row { --row-scale:1.05; }";
        } else if (event.endsWith("_hover") || event.endsWith("_click")) {
            String key = event.substring(0, event.lastIndexOf('_'));
            String id = key.equals("edit") ? "mail-edit-animations" : "mail-" + key;
            html = "<button id=\"" + id + "\"></button>";
            if (event.endsWith("_hover")) {
                css = "button#" + id + ":hover { --hover-scale:1.05; }";
                js = "function onHover(event) { button.scale(1.05); }";
            } else {
                css = "button#" + id + ":active { --ripple-color:#f3cc59; --ripple-scale:1.8; --duration:320ms; }";
                js = "function onClick(event) { ripple(1.8); duration(320); }";
            }
        } else if (event.equals("row_selected")) {
            html = "<button class=\"mail-row\" data-selected=\"true\"></button>";
            css = ".mail-row:selected { background-color:#e7e5df; border-color:#ffffff; color:#50545d; }";
        } else if (event.equals("hover")) {
            css = ".mail-favorite:hover { --button-hover-color:#fff1da; --hover-scale:1.05; }";
        } else if (event.equals("press")) {
            css = ".mail-favorite:active { --button-pressed-color:#e7d9c5; }";
        }
        return new Animation(html, css, js);
    }

    private static Path file() {
        return PlatformServices.configDir().resolve("xero_quest/ui/mailbox-animations.json");
    }

    public static final class Config {
        public int paletteVersion;
        public int noticeVersion;
        public int favoriteAnimationVersion;
        public int rowAnimationVersion;
        public Map<String, Animation> events = new LinkedHashMap<>();

        private void normalize() {
            if (events == null) events = new LinkedHashMap<>();
            Config fallback = defaults();
            for (String event : EVENTS) {
                Animation value = events.get(event);
                if (value == null) events.put(event, fallback.events.get(event));
                else value.normalize();
            }
            events.keySet().removeIf(key -> !EVENTS.contains(key));
            if (noticeVersion < 1) {
                Animation old = events.get("notice");
                String legacyHtml = "<div class=\"mail-notice\" data-icon=\"mail_envelope\" data-text=\"\" data-background=\"\"></div>";
                String legacyCss = ".mail-notice { --background:#cc161a20; --border:#64666b; --text:#e9e7e2;"
                        + " --width:500; --height:58; --text-scale:1.6; --duration:6000; }";
                if (old.html.equals(legacyHtml) && old.css.equals(legacyCss) && old.js.isBlank())
                    events.put("notice", defaultAnimation("notice"));
                noticeVersion = 1;
            }
            if (noticeVersion < 2) {
                Animation notice = events.get("notice");
                String oldDefaults = ".mail-notice { --background:#cc161a20; --border:#64666b; --text:#e9e7e2;"
                        + " --width:168; --height:20; --text-scale:1; --duration:6000; }";
                if (notice.css.equals(oldDefaults)) {
                    notice.css = fallback.events.get("notice").css;
                } else {
                    notice.css = notice.css.replace("--background:#cc161a20", "--background:#80121820")
                            .replace("--border:#64666b", "--border:#800a0c10")
                            .replace("--text:#e9e7e2", "--text:#f4f5f7");
                    if (!notice.css.contains("--icon:"))
                        notice.css = notice.css.replace("}", " --icon:#80c8d0da; }");
                }
                noticeVersion = 2;
            }
            if (noticeVersion < 3) {
                Animation notice = events.get("notice");
                String shippedV2 = notice.css.replaceAll("\\s+", "");
                if (shippedV2.contains("--background:#80121820;")
                        && shippedV2.contains("--border:#800a0c10;")
                        && shippedV2.contains("--text:#f4f5f7;")
                        && shippedV2.contains("--icon:#80c8d0da;")
                        && shippedV2.contains("--width:500;")
                        && shippedV2.contains("--height:58;")
                        && shippedV2.contains("--text-scale:1.6;")
                        && shippedV2.contains("--duration:6000;"))
                    notice.css = fallback.events.get("notice").css;
                noticeVersion = 3;
            }
            if (noticeVersion < 4) {
                Animation notice = events.get("notice");
                // Repair shipped ARGB literals in CSS without changing custom colors or geometry.
                notice.css = PROPERTY.matcher(notice.css).replaceAll(match -> {
                    String replacement = switch (match.group(1).toLowerCase(Locale.ROOT) + ":"
                            + match.group(2).strip().toLowerCase(Locale.ROOT)) {
                        case "background:#80121820", "background:#cc161a20" -> "#1d202680";
                        case "border:#800a0c10" -> "#0a0c1080";
                        case "icon:#80c8d0da" -> "#c8c8c880";
                        default -> null;
                    };
                    return Matcher.quoteReplacement(replacement == null ? match.group()
                            : match.group().substring(0, match.start(2) - match.start()) + replacement);
                });
                noticeVersion = 4;
            }
            if (favoriteAnimationVersion < 1) {
                migrateFavoriteDefault("favorite", LEGACY_FAVORITE_JS);
                migrateFavoriteDefault("unfavorite", LEGACY_UNFAVORITE_JS);
                favoriteAnimationVersion = 1;
            }
            if (rowAnimationVersion < 1) {
                Animation rowHover = events.get("row_hover");
                if (rowHover.html.strip().equals("<button id=\"mail-row\"></button>")
                        && rowHover.css.strip().equals("button#mail-row:hover { --hover-scale:1.05; }")
                        && rowHover.js.strip().equals("function onHover(event) { button.scale(1.05); }"))
                    events.put("row_hover", MailboxAnimationStore.defaultAnimation("row_hover"));
                rowAnimationVersion = 1;
            }
            if (paletteVersion < 1) {
                // Upgrade only shipped colors; keep authored animation and custom palettes intact.
                for (String event : List.of("favorite", "unfavorite", "hover", "press")) {
                    Animation value = events.get(event);
                    value.css = upgradePalette(value.css);
                    value.js = upgradePalette(value.js);
                }
                paletteVersion = 1;
            }
        }

        private void migrateFavoriteDefault(String event, String legacyJs) {
            Animation value = events.get(event);
            if (value.html.strip().equals(LEGACY_FAVORITE_HTML.strip())
                    && value.css.strip().equals(LEGACY_FAVORITE_CSS.strip())
                    && value.js.strip().equals(legacyJs.strip()))
                events.put(event, MailboxAnimationStore.defaultAnimation(event));
        }
    }

    private static String upgradePalette(String source) {
        return PROPERTY.matcher(source).replaceAll(match -> {
            String replacement = switch (match.group(1).toLowerCase(Locale.ROOT) + ":"
                    + match.group(2).strip().toLowerCase(Locale.ROOT)) {
                case "button-color:#313b49", "button-border-color:#78889c" -> "#ffffff";
                case "button-hover-color:#46566b" -> "#fff1da";
                case "button-pressed-color:#26303d" -> "#e7d9c5";
                case "star-color:#f3cc59" -> "#ffd900";
                case "star-empty-color:#e6edf6" -> "#505a70";
                default -> null;
            };
            return Matcher.quoteReplacement(replacement == null ? match.group()
                    : match.group().substring(0, match.start(2) - match.start()) + replacement);
        });
    }

    public static final class Animation {
        public String html;
        public String css;
        public String js;

        public Animation() {
            this("", "", "");
        }

        public Animation(String html, String css, String js) {
            this.html = html == null ? "" : html;
            this.css = css == null ? "" : css;
            this.js = js == null ? "" : js;
        }

        private void normalize() {
            html = limit(html, 16384);
            css = limit(css, 16384);
            js = limit(js, 16384);
        }

        private static String limit(String value, int limit) {
            return value == null ? "" : value.length() > limit ? value.substring(0, limit) : value;
        }
    }

    public record Style(int buttonColor, int buttonHoverColor, int buttonPressedColor, int buttonBorderColor,
                        int starColor, int starEmptyColor, int rippleColor, int dotColor,
                        float starScale, float rippleScale, int dotCount, int durationMillis, float hoverScale) {
        public static Style defaults() {
            return new Style(0xFFFFFFFF, 0xFFFFF1DA, 0xFFE7D9C5, 0xFFFFFFFF,
                    0xFFFFD900, 0xFF505A70, 0xFFF3CC59, 0xFFFFF4C7,
                    1.42F, 1.85F, 8, 520, 1.05F);
        }

        private Style withColor(String key, String value) {
            if (value == null || value.isBlank()) return this;
            return switch (key) {
                case "button-color" -> new Style(ButtonStyle.parseColor(value, buttonColor), buttonHoverColor,
                        buttonPressedColor, buttonBorderColor, starColor, starEmptyColor, rippleColor, dotColor,
                        starScale, rippleScale, dotCount, durationMillis, hoverScale);
                case "button-hover-color" -> new Style(buttonColor, ButtonStyle.parseColor(value, buttonHoverColor),
                        buttonPressedColor, buttonBorderColor, starColor, starEmptyColor, rippleColor, dotColor,
                        starScale, rippleScale, dotCount, durationMillis, hoverScale);
                case "button-pressed-color" -> new Style(buttonColor, buttonHoverColor,
                        ButtonStyle.parseColor(value, buttonPressedColor), buttonBorderColor, starColor,
                        starEmptyColor, rippleColor, dotColor, starScale, rippleScale, dotCount,
                        durationMillis, hoverScale);
                case "button-border-color" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor,
                        ButtonStyle.parseColor(value, buttonBorderColor), starColor, starEmptyColor, rippleColor,
                        dotColor, starScale, rippleScale, dotCount, durationMillis, hoverScale);
                case "star-color" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor, buttonBorderColor,
                        ButtonStyle.parseColor(value, starColor), starEmptyColor, rippleColor, dotColor,
                        starScale, rippleScale, dotCount, durationMillis, hoverScale);
                case "star-empty-color" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor,
                        buttonBorderColor, starColor, ButtonStyle.parseColor(value, starEmptyColor), rippleColor,
                        dotColor, starScale, rippleScale, dotCount, durationMillis, hoverScale);
                case "ripple-color" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor, buttonBorderColor,
                        starColor, starEmptyColor, ButtonStyle.parseColor(value, rippleColor), dotColor,
                        starScale, rippleScale, dotCount, durationMillis, hoverScale);
                case "dot-color" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor, buttonBorderColor,
                        starColor, starEmptyColor, rippleColor, ButtonStyle.parseColor(value, dotColor),
                        starScale, rippleScale, dotCount, durationMillis, hoverScale);
                default -> this;
            };
        }

        private Style withFloat(String key, String value) {
            if (value == null || value.isBlank()) return this;
            try {
                float parsed = Float.parseFloat(value.replace("px", "").trim());
                if (!Float.isFinite(parsed)) return this;
                parsed = Math.clamp(parsed, .1F, 4.0F);
                return switch (key) {
                    case "star-scale" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor,
                            buttonBorderColor, starColor, starEmptyColor, rippleColor, dotColor, parsed,
                            rippleScale, dotCount, durationMillis, hoverScale);
                    case "ripple-scale" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor,
                            buttonBorderColor, starColor, starEmptyColor, rippleColor, dotColor, starScale,
                            parsed, dotCount, durationMillis, hoverScale);
                    case "hover-scale" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor,
                            buttonBorderColor, starColor, starEmptyColor, rippleColor, dotColor, starScale,
                            rippleScale, dotCount, durationMillis, parsed);
                    default -> this;
                };
            } catch (NumberFormatException ignored) {
                return this;
            }
        }

        private Style withInt(String key, String value) {
            if (value == null || value.isBlank()) return this;
            try {
                int parsed = Integer.parseInt(value.replace("ms", "").trim());
                return switch (key) {
                    case "dot-count" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor,
                            buttonBorderColor, starColor, starEmptyColor, rippleColor, dotColor, starScale,
                            rippleScale, Math.clamp(parsed, 0, 24), durationMillis, hoverScale);
                    case "duration" -> new Style(buttonColor, buttonHoverColor, buttonPressedColor,
                            buttonBorderColor, starColor, starEmptyColor, rippleColor, dotColor, starScale,
                            rippleScale, dotCount, Math.clamp(parsed, 60, 2000), hoverScale);
                    default -> this;
                };
            } catch (NumberFormatException ignored) {
                return this;
            }
        }
    }
}
