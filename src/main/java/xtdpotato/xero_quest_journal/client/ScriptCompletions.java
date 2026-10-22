package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.*;
import xtdpotato.xero_quest_journal.core.model.QuestScriptReference;
import xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind;

/** Static, bounded completion analysis. Never evaluates source or loads optional KubeJS classes. */
public final class ScriptCompletions {
    private static final String MARKER = "__xero_completion_cursor__";
    public record Candidate(String name, String type, String signature) {}
    public record Result(int start, int end, List<Candidate> candidates) {}
    private record Value(String type, Map<String, Value> properties) {
        Value(String type) { this(type, Map.of()); }
    }
    private record Binding(Value value, AstNode initializer) {}
    private static final Value UNKNOWN = new Value("unknown");
    private static final Map<String, String> MATH = Map.ofEntries(
            Map.entry("abs", "abs(x)"), Map.entry("acos", "acos(x)"), Map.entry("acosh", "acosh(x)"),
            Map.entry("asin", "asin(x)"), Map.entry("asinh", "asinh(x)"), Map.entry("atan", "atan(x)"),
            Map.entry("atanh", "atanh(x)"), Map.entry("atan2", "atan2(y, x)"),
            Map.entry("cbrt", "cbrt(x)"), Map.entry("ceil", "ceil(x)"), Map.entry("clz32", "clz32(x)"),
            Map.entry("cos", "cos(x)"), Map.entry("cosh", "cosh(x)"), Map.entry("exp", "exp(x)"),
            Map.entry("expm1", "expm1(x)"), Map.entry("floor", "floor(x)"), Map.entry("fround", "fround(x)"),
            Map.entry("hypot", "hypot(...values)"), Map.entry("imul", "imul(a, b)"),
            Map.entry("log", "log(x)"), Map.entry("log10", "log10(x)"), Map.entry("log1p", "log1p(x)"),
            Map.entry("log2", "log2(x)"), Map.entry("max", "max(...values)"), Map.entry("min", "min(...values)"),
            Map.entry("pow", "pow(base, exponent)"), Map.entry("random", "random()"),
            Map.entry("round", "round(x)"), Map.entry("sign", "sign(x)"), Map.entry("sin", "sin(x)"),
            Map.entry("sinh", "sinh(x)"), Map.entry("sqrt", "sqrt(x)"), Map.entry("tan", "tan(x)"),
            Map.entry("tanh", "tanh(x)"), Map.entry("trunc", "trunc(x)"));

    private ScriptCompletions() {}

    public static Result complete(String source, int cursor, Kind kind, boolean explicit) {
        cursor = Math.max(0, Math.min(cursor, source.length()));
        int start = cursor, end = cursor;
        while (start > 0 && identifier(source.charAt(start - 1))) start--;
        while (end < source.length() && identifier(source.charAt(end))) end++;
        String prefix = source.substring(start, cursor);
        var empty = new Result(start, end, List.of());
        int before = start;
        while (before > 0 && Character.isWhitespace(source.charAt(before - 1))) before--;
        boolean member = before > 0 && source.charAt(before - 1) == '.';
        if (!explicit && prefix.isEmpty() && !member || source.length() > 65535) return empty;
        try {
            var env = CompilerEnvirons.ideEnvirons();
            env.setLanguageVersion(Context.VERSION_ES6);
            env.setXmlAvailable(false);
            // Replace the entire word, including its suffix, so accepting in the middle cannot duplicate it.
            AstRoot root = new Parser(env).parse(
                    source.substring(0, start) + MARKER + source.substring(end), "completion", 1);
            Name[] marker = {null};
            int tokenStart = start;
            root.visit(node -> {
                if (node instanceof Name name && name.getAbsolutePosition() == tokenStart
                        && name.getIdentifier().equals(MARKER)) marker[0] = name;
                return true;
            });
            // In strings, comments, templates and regular expressions the marker is not an identifier.
            if (marker[0] == null) return empty;
            var bindings = bindings(root, marker[0], kind);
            var values = new LinkedHashMap<String, Candidate>();
            if (member) {
                if (!(marker[0].getParent() instanceof PropertyGet property)
                        || property.getProperty() != marker[0]) return empty;
                Value target = infer(property.getTarget(), bindings, 0);
                members(target).forEach((name, value) -> values.put(name, candidate(name, value)));
            } else {
                bindings.forEach((name, value) -> values.put(name, candidate(name, value.value())));
                for (String keyword : List.of("const", "let", "var", "function", "return", "if", "else",
                        "for", "while", "break", "continue", "switch", "case", "default", "throw", "try",
                        "catch", "finally", "typeof", "instanceof", "new", "true", "false", "null",
                        "undefined", "this", "delete", "void"))
                    values.putIfAbsent(keyword, new Candidate(keyword, "keyword", keyword));
            }
            String folded = prefix.toLowerCase(Locale.ROOT);
            List<Candidate> matches = values.values().stream()
                    .filter(value -> value.name().toLowerCase(Locale.ROOT).startsWith(folded))
                    .filter(value -> explicit || !value.name().equals(prefix))
                    .sorted(Comparator.<Candidate>comparingInt(value -> value.name().startsWith(prefix) ? 0 : 1)
                            .thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER))
                    .limit(128).toList();
            return new Result(start, end, matches);
        } catch (RuntimeException | StackOverflowError invalidDraft) {
            return empty;
        }
    }

    private static boolean identifier(char value) {
        return value == '$' || value == '_' || Character.isUnicodeIdentifierPart(value);
    }

    private static Candidate candidate(String name, Value value) {
        return new Candidate(name, value.type().contains("(") ? "function" : value.type(),
                value.type().contains("(") ? value.type() : name + ": " + value.type());
    }

    private static Map<String, Binding> bindings(AstRoot root, Name marker, Kind kind) {
        var values = new LinkedHashMap<String, Binding>();
        // Nearest lexical declarations win, including parameters. Never leak a sibling function's locals.
        for (Scope scope = marker.getEnclosingScope(); scope != null; scope = scope.getParentScope()) {
            if (scope.getSymbolTable() == null) continue;
            for (String name : scope.getSymbolTable().keySet()) {
                if (!name.equals(MARKER)) values.putIfAbsent(name, new Binding(UNKNOWN, null));
            }
        }
        root.visit(node -> {
            if (node instanceof VariableInitializer variable && variable.getTarget() instanceof Name name
                    && visible(name, marker)) {
                values.put(name.getIdentifier(), new Binding(UNKNOWN, variable.getInitializer()));
            } else if (node instanceof FunctionNode function && function.getFunctionName() != null
                    && visible(function.getFunctionName(), marker)) {
                values.put(function.getName(), new Binding(new Value(function.getName() + "("
                        + String.join(", ", function.getParams().stream().map(AstNode::toSource).toList()) + ")"), null));
            }
            return true;
        });
        for (var entry : new ArrayList<>(values.entrySet())) {
            if (entry.getValue().initializer() != null)
                values.put(entry.getKey(), new Binding(infer(entry.getValue().initializer(), values, 0),
                        entry.getValue().initializer()));
        }
        var globals = new LinkedHashMap<String, Value>();
        var math = new LinkedHashMap<String, Value>();
        MATH.forEach((name, signature) -> math.put(name, new Value(signature)));
        for (String constant : List.of("PI", "E", "LN2", "LN10", "LOG2E", "LOG10E", "SQRT1_2", "SQRT2"))
            math.put(constant, new Value("number"));
        globals.put("Math", new Value("object", math));
        globals.put("JSON", new Value("object", Map.of("parse", new Value("parse(text)"),
                "stringify", new Value("stringify(value)"))));
        for (String function : List.of("Number", "String", "Boolean", "parseInt", "parseFloat",
                "isNaN", "isFinite", "encodeURIComponent", "decodeURIComponent"))
            globals.put(function, new Value(function + "(value)"));
        globals.put("Array", new Value("object", Map.of("isArray", new Value("isArray(value)"),
                "from", new Value("from(value)"), "of", new Value("of(...values)"))));
        globals.put("Object", new Value("object", Map.of("keys", new Value("keys(object)"),
                "values", new Value("values(object)"), "assign", new Value("assign(target, ...sources)"))));
        globals.put("Error", new Value("Error(message)"));
        if (kind == Kind.KUBEJS) {
            var event = new LinkedHashMap<String, Value>();
            for (var field : QuestScriptReference.fields(kind))
                event.put(field.name().substring("event.".length()), new Value(field.type()));
            globals.put("event", new Value("object", event));
            var global = new LinkedHashMap<String, Value>();
            global.put("onXeroQuestAction", new Value("onXeroQuestAction(event)"));
            root.visit(node -> {
                if (node instanceof Assignment assignment && assignment.getLeft() instanceof PropertyGet property
                        && property.getTarget() instanceof Name name && name.getIdentifier().equals("global"))
                    global.put(property.getProperty().getIdentifier(), infer(assignment.getRight(), values, 0));
                return true;
            });
            globals.put("global", new Value("object", global));
            globals.put("console", new Value("object", Map.of("log", new Value("log(message)"),
                    "info", new Value("info(message)"), "warn", new Value("warn(message)"),
                    "error", new Value("error(message)"))));
        } else {
            for (var field : QuestScriptReference.fields(kind)) globals.put(field.name(), new Value(field.type()));
            if (kind == Kind.REWARD_FILE) {
                globals.put("message", new Value("message(text)"));
                globals.put("command", new Value("command(text)"));
            }
        }
        globals.forEach((name, value) -> values.putIfAbsent(name, new Binding(value, null)));
        return values;
    }

    private static boolean visible(Name name, Name marker) {
        Scope defining = name.getDefiningScope();
        return defining != null && marker.getEnclosingScope() != null
                && defining == marker.getEnclosingScope().getDefiningScope(name.getIdentifier());
    }

    private static Value infer(AstNode node, Map<String, Binding> bindings, int depth) {
        if (node == null || depth > 8) return UNKNOWN;
        if (node instanceof StringLiteral || node instanceof TemplateLiteral) return new Value("string");
        if (node instanceof NumberLiteral) return new Value("number");
        if (node instanceof ArrayLiteral) return new Value("array");
        if (node instanceof FunctionNode function) return new Value("function("
                + String.join(", ", function.getParams().stream().map(AstNode::toSource).toList()) + ")");
        if (node instanceof ParenthesizedExpression expression) return infer(expression.getExpression(), bindings, depth + 1);
        if (node instanceof Name name) {
            Binding binding = bindings.get(name.getIdentifier());
            if (binding == null) return UNKNOWN;
            return binding.initializer() == null ? binding.value() : infer(binding.initializer(), bindings, depth + 1);
        }
        if (node instanceof PropertyGet property)
            return members(infer(property.getTarget(), bindings, depth + 1))
                    .getOrDefault(property.getProperty().getIdentifier(), UNKNOWN);
        if (node instanceof ObjectLiteral object) {
            var fields = new LinkedHashMap<String, Value>();
            for (ObjectProperty property : object.getElements()) {
                String key = property.getLeft() instanceof Name name ? name.getIdentifier()
                        : property.getLeft() instanceof StringLiteral text ? text.getValue() : "";
                if (!key.isBlank()) fields.put(key, infer(property.getRight(), bindings, depth + 1));
            }
            return new Value("object", fields);
        }
        return UNKNOWN;
    }

    private static Map<String, Value> members(Value value) {
        if (!value.properties().isEmpty()) return value.properties();
        var fields = new LinkedHashMap<String, Value>();
        if (value.type().equals("string") || value.type().equals("array")) fields.put("length", new Value("number"));
        List<String> signatures = switch (value.type()) {
            case "string" -> List.of("includes(search)", "startsWith(search)", "endsWith(search)",
                    "substring(start, end)", "slice(start, end)", "split(separator)", "trim()",
                    "toLowerCase()", "toUpperCase()", "replace(search, replacement)",
                    "indexOf(search)", "charAt(index)", "concat(text)", "match(pattern)");
            case "array" -> List.of("push(value)", "pop()", "shift()", "unshift(value)", "map(callback)",
                    "filter(callback)", "find(callback)", "forEach(callback)", "some(callback)",
                    "every(callback)", "reduce(callback, initial)", "join(separator)", "slice(start, end)",
                    "splice(start, count)", "indexOf(value)", "sort(compare)", "reverse()");
            case "number" -> List.of("toFixed(digits)", "toPrecision(digits)", "toString(radix)");
            default -> List.of();
        };
        for (String signature : signatures)
            fields.put(signature.substring(0, signature.indexOf('(')), new Value(signature));
        return fields;
    }
}
