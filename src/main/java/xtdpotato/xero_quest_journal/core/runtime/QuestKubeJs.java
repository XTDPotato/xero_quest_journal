package xtdpotato.xero_quest_journal.core.runtime;

import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Optional KubeJS 2101 bridge, using its server script manager and Rhino context.
 * Author-provided KubeJS is trusted server code, NOT the isolated QuestScripts sandbox.
 * No optional classes are loaded until the integration is actually invoked.
 */
public final class QuestKubeJs {
    private static final Gson GSON = new Gson();
    private QuestKubeJs() {}
    public static boolean available() { return PlatformServices.isModLoaded("kubejs"); }
    public static void run(ServerPlayer player, String source, Map<String, String> event) {
        if (player == null || !player.server.isSameThread() || !available())
            throw new IllegalStateException("KubeJS is not available on the server");
        if (source == null || source.length() > 16384) throw new IllegalArgumentException("Script exceeds limit");
        try {
            var managers = player.server.getServerResources().managers();
            Class<?> bridge = Class.forName("dev.latvian.mods.kubejs.core.ReloadableServerResourcesKJS");
            Object manager = bridge.getMethod("kjs$getServerScriptManager").invoke(managers);
            Object factory = manager.getClass().getField("contextFactory").get(manager);
            if (factory == null) throw new IllegalStateException("KubeJS server scripts not loaded");
            Object context = factory.getClass().getMethod("enter").invoke(factory);
            Object scope = context.getClass().getField("topLevelScope").get(context);
            Class<?> scriptable = Class.forName("dev.latvian.mods.rhino.Scriptable");
            // The context is a JSON object local to this invocation; never overwrite KubeJS globals.
            String code = "(function(event) {\n" + source + "\n})(" + GSON.toJson(event) + ");";
            context.getClass().getMethod("evaluateString", scriptable, String.class, String.class, int.class, Object.class)
                    .invoke(context, scope, code, "xero-trigger", 1, null);
        } catch (InvocationTargetException failure) {
            throw new IllegalArgumentException("KubeJS trigger failed", failure.getCause());
        } catch (ReflectiveOperationException incompatible) {
            throw new IllegalStateException("Unsupported KubeJS server API", incompatible);
        }
    }
    public static void call(ServerPlayer player, String function, Map<String, String> event) {
        if (function == null || !function.matches("global\\.[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
            throw new IllegalArgumentException("Use global.functionName");
        run(player, "if (typeof " + function + " !== 'function') throw new Error('Function not found');\n"
                + function + "(event);", event);
    }
}
