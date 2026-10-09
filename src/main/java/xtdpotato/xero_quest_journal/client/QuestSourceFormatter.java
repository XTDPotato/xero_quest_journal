package xtdpotato.xero_quest_journal.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Reindent structural markup without rewriting attributes, scripts or mixed text. */
public final class QuestSourceFormatter {
    private static final Set<String> VERBATIM = Set.of("script", "style", "content", "pre", "code", "textarea");
    public record Diagnostic(int line, int column, String reason, String repair) {}

    private QuestSourceFormatter() {}

    public static Diagnostic diagnose(String source) {
        try {
            format(source);
            return null;
        } catch (RuntimeException invalid) {
            String stripped = source.stripLeading();
            if (stripped.startsWith("{") || stripped.startsWith("[")) {
                var matcher = Pattern.compile("line (\\d+) column (\\d+)", Pattern.CASE_INSENSITIVE)
                        .matcher(String.valueOf(invalid.getMessage()));
                boolean positioned = matcher.find();
                int line = positioned ? Integer.parseInt(matcher.group(1)) : 1;
                int column = positioned ? Integer.parseInt(matcher.group(2)) : 1;
                return new Diagnostic(line, column, invalid.getMessage(), null);
            }
            if (!stripped.startsWith("<")) return new Diagnostic(1, 1, invalid.getMessage(), null);
            var root = Pattern.compile("^\\s*<([A-Za-z][A-Za-z0-9_:-]*)\\b").matcher(source);
            if (root.find() && source.stripTrailing().endsWith(">")
                    && !source.contains("</" + root.group(1) + ">")) {
                String repaired = source.stripTrailing() + "</" + root.group(1) + ">\n";
                try {
                    format(repaired);
                    return at(source, root.start(1), "Missing </" + root.group(1) + ">", repaired);
                } catch (RuntimeException ignored) {}
            }
            Parser parser = Parser.xmlParser().setTrackPosition(true).setTrackErrors(64);
            var document = parser.parseInput(source, "");
            var unclosed = document.getAllElements().stream().filter(element ->
                    element.sourceRange().isTracked()
                            && (!element.endSourceRange().isTracked() || element.endSourceRange().isImplicit())
                            && !source.substring(element.sourceRange().start().pos(),
                            element.sourceRange().end().pos()).endsWith("/>")).toList();
            if (unclosed.size() == 1) {
                var element = unclosed.getFirst();
                String repair = source.stripTrailing().endsWith(">")
                        ? source.stripTrailing() + "</" + element.tagName() + ">\n" : null;
                if (repair != null && !repair.equals(source)) {
                    try {
                        format(repair);
                        return at(source, element.sourceRange().start().pos(),
                                "Missing </" + element.tagName() + ">", repair);
                    } catch (RuntimeException ignored) {}
                }
            }
            for (var error : parser.getErrors()) {
                int pos = Math.clamp(error.getPosition(), 0, source.length());
                boolean verbatim = document.getAllElements().stream().anyMatch(element ->
                        VERBATIM.contains(element.normalName()) && element.endSourceRange().isTracked()
                                && pos >= element.sourceRange().endPos()
                                && pos <= element.endSourceRange().startPos());
                if (!verbatim) return at(source, pos, error.getErrorMessage(), null);
            }
            if (!unclosed.isEmpty()) {
                var element = unclosed.getFirst();
                return at(source, element.sourceRange().start().pos(),
                        "Missing </" + element.tagName() + ">", null);
            }
            return new Diagnostic(1, 1, invalid.getMessage(), null);
        }
    }

    private static Diagnostic at(String source, int position, String reason, String repair) {
        int line = 1, column = 1;
        for (int i = 0; i < position; i++) {
            if (source.charAt(i) == '\n') { line++; column = 1; }
            else column++;
        }
        return new Diagnostic(line, column, reason, repair);
    }

    public static String format(String source) {
        String stripped = source.stripLeading();
        if (stripped.startsWith("{") || stripped.startsWith("[")) {
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(JsonParser.parseString(source)) + "\n";
        }
        if (!stripped.startsWith("<")) throw new IllegalArgumentException("Expected JSON or markup");
        Parser parser = Parser.xmlParser().setTrackPosition(true).setTrackErrors(64);
        var document = parser.parseInput(source, "");
        boolean invalid = parser.getErrors().stream().anyMatch(error -> document.getAllElements().stream()
                .noneMatch(element -> VERBATIM.contains(element.normalName())
                        && element.endSourceRange().isTracked()
                        && error.getPosition() >= element.sourceRange().endPos()
                        && error.getPosition() <= element.endSourceRange().startPos()));
        if (invalid || document.children().isEmpty()) {
            throw new IllegalArgumentException("Invalid markup");
        }
        StringBuilder result = new StringBuilder(source.length());
        for (Node node : document.childNodes()) append(node, source, result, 0);
        return result.toString();
    }

    private static void append(Node node, String source, StringBuilder out, int depth) {
        if (depth > 256) throw new IllegalArgumentException("Markup nesting is too deep");
        if (node instanceof TextNode text && text.isBlank()) return;
        var range = node.sourceRange();
        if (!range.isTracked()) throw new IllegalArgumentException("Untracked markup");
        int start = range.start().pos();
        int end = range.end().pos();
        out.append("  ".repeat(depth));
        if (!(node instanceof Element element)) {
            out.append(source, start, end).append('\n');
            return;
        }
        var closing = element.endSourceRange();
        if (!closing.isTracked() || closing.isImplicit()) {
            // Only genuinely self-closing tags may lack an end tag.
            String opening = source.substring(start, end);
            if (!opening.endsWith("/>")) throw new IllegalArgumentException("Unclosed element");
            out.append(opening).append('\n');
            return;
        }
        boolean mixed = VERBATIM.contains(element.normalName().toLowerCase(Locale.ROOT))
                || element.childNodes().stream().anyMatch(child ->
                !(child instanceof Element) && !(child instanceof TextNode text && text.isBlank()));
        if (mixed || element.children().isEmpty()) {
            out.append(source, start, closing.end().pos()).append('\n');
            return;
        }
        out.append(source, start, end).append('\n');
        for (Node child : element.childNodes()) append(child, source, out, depth + 1);
        out.append("  ".repeat(depth)).append(source, closing.start().pos(), closing.end().pos()).append('\n');
    }
}
