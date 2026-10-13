package xtdpotato.xero_quest_journal.fabric;

import java.lang.reflect.InvocationTargetException;
import xtdpotato.xero_quest_journal.fabriccompat.*;

/** Reflection keeps optional smoke sources out of production builds. */
public final class FabricSmokeHooks {
    private FabricSmokeHooks() {}
    private static boolean active() { return Boolean.getBoolean("xero.fabricSmoke"); }
    public static void tick() {
        if (active()) invoke("ClientSmokeTest", "tick", FabricEvents.ClientTickEvent.Post.class,
                new FabricEvents.ClientTickEvent.Post());
    }
    public static void frame() {
        if (active()) invoke("ClientSmokeTest", "frame", FabricEvents.RenderFrameEvent.Post.class,
                new FabricEvents.RenderFrameEvent.Post());
    }
    public static void rendered(FabricScreenEvent.Render.Post event) {
        if (!active()) return;
        for (String suite : new String[]{"JournalScaleRegression", "RewardQualityRegression", "SpotlightUiRegression"})
            invoke(suite, "rendered", FabricScreenEvent.Render.Post.class, event);
    }
    private static void invoke(String suite, String method, Class<?> eventType, Object event) {
        try {
            var hook = Class.forName("xtdpotato.xero_quest_journal.smoke." + suite).getDeclaredMethod(method, eventType);
            hook.setAccessible(true);
            hook.invoke(null, event);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Fabric smoke hook failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Fabric smoke source missing", failure);
        }
    }
}
