package xtdpotato.xero_quest_journal.core.runtime;

import java.util.List;
import java.math.BigDecimal;

/** Bounded, deterministic numeric operations for visual quest-graph nodes. */
public final class QuestVariableMath {
    public static final List<String> OPERATIONS = List.of("add", "subtract", "multiply", "divide", "modulo", "power",
            "sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "abs", "floor", "ceil", "round",
            "log", "log10", "exp", "min", "max", "atan2", "cbrt", "sign", "to_degrees", "to_radians");
    private static final List<String> UNARY = List.of("sin", "cos", "tan", "asin", "acos", "atan", "sqrt",
            "abs", "floor", "ceil", "round", "log", "log10", "exp", "cbrt", "sign",
            "to_degrees", "to_radians");

    private QuestVariableMath() {}

    public static boolean unary(String operation) { return UNARY.contains(operation); }

    public static String evaluate(String operation, String leftValue, String rightValue) {
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Unknown numeric operation");
        double left = number(leftValue);
        double right = unary(operation) ? 0 : number(rightValue);
        double result = switch (operation) {
            case "add" -> left + right;
            case "subtract" -> left - right;
            case "multiply" -> left * right;
            case "divide" -> {
                if (right == 0) throw new IllegalArgumentException("Division by zero");
                yield left / right;
            }
            case "modulo" -> {
                if (right == 0) throw new IllegalArgumentException("Modulo by zero");
                yield left % right;
            }
            case "power" -> Math.pow(left, right);
            case "sin" -> Math.sin(left);
            case "cos" -> Math.cos(left);
            case "tan" -> Math.tan(left);
            case "asin" -> Math.asin(left);
            case "acos" -> Math.acos(left);
            case "atan" -> Math.atan(left);
            case "sqrt" -> {
                if (left < 0) throw new IllegalArgumentException("Square root requires a non-negative value");
                yield Math.sqrt(left);
            }
            case "abs" -> Math.abs(left);
            case "floor" -> Math.floor(left);
            case "ceil" -> Math.ceil(left);
            case "round" -> Math.round(left);
            case "log" -> Math.log(left);
            case "log10" -> Math.log10(left);
            case "exp" -> Math.exp(left);
            case "min" -> Math.min(left, right);
            case "max" -> Math.max(left, right);
            case "atan2" -> Math.atan2(left, right);
            case "cbrt" -> Math.cbrt(left);
            case "sign" -> Math.signum(left);
            case "to_degrees" -> Math.toDegrees(left);
            case "to_radians" -> Math.toRadians(left);
            default -> throw new IllegalArgumentException("Unknown numeric operation");
        };
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Numeric result is not finite");
        return BigDecimal.valueOf(result).stripTrailingZeros().toPlainString();
    }

    private static double number(String value) {
        try {
            double result = Double.parseDouble(value);
            if (Double.isFinite(result)) return result;
        } catch (RuntimeException ignored) {}
        throw new IllegalArgumentException("Expected a finite number");
    }
}
