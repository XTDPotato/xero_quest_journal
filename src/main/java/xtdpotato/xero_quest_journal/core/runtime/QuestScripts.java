package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.mozilla.javascript.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import java.nio.file.Files;
import java.nio.file.Path;

/** Author-only JavaScript: no Java objects, IO or class access are exposed to the script. */
public final class QuestScripts {
    private static final int MAX_SOURCE = 65536;
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> BUDGET = new ThreadLocal<>();
    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(1000);
            cx.setMaximumInterpreterStackDepth(128);
            cx.setClassShutter(name -> false);
            return cx;
        }
        @Override protected void observeInstructionCount(Context cx, int count) {
            int remaining = BUDGET.get() - count;
            BUDGET.set(remaining);
            if (remaining < 0 || System.nanoTime() > DEADLINE.get()) throw new ScriptLimit();
        }
    };

    private QuestScripts() {}
    private static final class ScriptLimit extends Error {}

    public static String source(String reference) throws Exception {
        Path root = QuestConfig.root().resolve("scripts").toAbsolutePath().normalize();
        Path path = root.resolve(reference).normalize();
        if (!path.startsWith(root) || !reference.endsWith(".js")) throw new IllegalArgumentException("Invalid script path");
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw new IllegalArgumentException("Symlinked script");
        }
        if (Files.size(path) > MAX_SOURCE) throw new IllegalArgumentException("Script exceeds 64 KiB");
        String source = Files.readString(path);
        // NBT uses modified UTF-8 (supplementary characters occupy six bytes, not four).
        int encoded = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            encoded += c >= 1 && c <= 127 ? 1 : c <= 2047 ? 2 : 3;
            if (encoded > 65535) throw new IllegalArgumentException("Script exceeds NBT string limit");
        }
        return source;
    }

    public static boolean editSource(ServerPlayer author, String reference, String expectedHash, String updated) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2) || updated == null || expectedHash == null
                || updated.length() > MAX_SOURCE) return false;
        try {
            String previous = source(reference);
            if (!QuestRepository.fingerprint(previous).equals(expectedHash)) return false;
            int encoded = 0;
            for (int i = 0; i < updated.length(); i++) {
                char c = updated.charAt(i);
                encoded += c >= 1 && c <= 127 ? 1 : c <= 2047 ? 2 : 3;
                if (encoded > 65535) return false;
            }
            FACTORY.call(cx -> { cx.compileString(updated, reference, 1, null); return null; });
            AtomicFiles.write(QuestConfig.root().resolve("scripts").resolve(reference).normalize(), updated, true);
            return true;
        } catch (Exception invalid) {
            return false;
        }
    }

    public static Object run(ServerPlayer player, String source, boolean condition) {
        return run(player, source, condition, java.util.Map.of());
    }

    public static Object run(ServerPlayer player, String source, boolean condition, java.util.Map<String, String> context) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalArgumentException("Scripts require the server thread");
        if (source.length() > MAX_SOURCE || DEADLINE.get() != null) throw new IllegalArgumentException("Script limit");
        DEADLINE.set(System.nanoTime() + 50_000_000L);
        BUDGET.set(100_000);
        try {
            return FACTORY.call(cx -> {
                ScriptableObject scope = cx.initSafeStandardObjects();
                ScriptableObject.putProperty(scope, "playerName", player.getGameProfile().getName());
                ScriptableObject.putProperty(scope, "playerId", player.getUUID().toString());
                ScriptableObject.putProperty(scope, "dimension", player.level().dimension().location().toString());
                ScriptableObject.putProperty(scope, "x", player.getX());
                ScriptableObject.putProperty(scope, "y", player.getY());
                ScriptableObject.putProperty(scope, "z", player.getZ());
                ScriptableObject.putProperty(scope, "experienceLevel", player.experienceLevel);
                context.forEach((key, value) -> ScriptableObject.putProperty(scope, key, value));
                if (!condition) {
                    function(scope, "message", args -> {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(argument(args, 0)));
                        return true;
                    });
                    function(scope, "command", args -> QuestRewards.command(player, argument(args, 0)));
                }
                Object result = cx.evaluateString(scope, source, "quest-script", 1, null);
                if (result instanceof Boolean || result instanceof Number || result instanceof String) return result;
                return null;
            });
        } catch (ScriptLimit limit) {
            throw new IllegalArgumentException("Script instruction/time budget exceeded");
        } finally {
            DEADLINE.remove();
            BUDGET.remove();
        }
    }

    private static String argument(Object[] args, int index) {
        if (args.length <= index) throw new IllegalArgumentException("Missing script argument");
        String value = Context.toString(args[index]);
        if (value.length() > 4096) throw new IllegalArgumentException("Script argument too long");
        return value;
    }
    private static void function(ScriptableObject scope, String name, java.util.function.Function<Object[], Object> action) {
        BaseFunction function = new BaseFunction() {
            @Override public Object call(Context cx, Scriptable sc, Scriptable self, Object[] args) { return action.apply(args); }
        };
        function.setParentScope(scope);
        function.setPrototype(ScriptableObject.getFunctionPrototype(scope));
        ScriptableObject.putProperty(scope, name, function);
    }
}
