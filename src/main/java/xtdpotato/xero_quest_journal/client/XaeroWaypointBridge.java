package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional Xaero integration. Reflection keeps the journal usable when either
 * Xaero map is absent or its API changes between Minecraft releases.
 */
final class XaeroWaypointBridge {
    private static final String ORIGIN = "xero_quest_journal";
    private static final int WORLD_MAP_GLOBAL = 3;
    private static String lastSignature = "";
    private static boolean unavailable;

    private XaeroWaypointBridge() {}

    static void refresh() {
        lastSignature = "";
        tick();
    }

    static void tick() {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || game.level == null) {
            clearIfPossible();
            return;
        }
        String signature = game.level.dimension().location() + "|" + ClientQuestProgress.revision()
                + "|" + String.join(",", ClientQuestProgress.trackedIds());
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        sync(game);
    }

    private static void sync(Minecraft game) {
        try {
            Class<?> managerType = Class.forName("xaero.common.minimap.waypoints.WaypointsManager");
            Method custom = managerType.getMethod("getCustomWaypoints", String.class);
            @SuppressWarnings("unchecked")
            Hashtable<Integer, Object> waypoints = (Hashtable<Integer, Object>) custom.invoke(null, ORIGIN);
            waypoints.clear();
            Class<?> waypointType = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Constructor<?> constructor = waypointType.getConstructor(int.class, int.class, int.class,
                    String.class, String.class, int.class);
            Map<Integer, QuestDefinition> desired = new LinkedHashMap<>();
            for (String id : ClientQuestProgress.trackedIds()) {
                QuestDefinition quest = QuestScreen.questById(id);
                QuestLocation location = quest == null ? null : QuestHudRenderer.location(quest);
                if (quest == null || location == null || !location.resolved()
                        || !sameDimension(game, location)) continue;
                desired.put(id.hashCode(), quest);
            }
            for (var entry : desired.entrySet()) {
                QuestDefinition quest = entry.getValue();
                QuestLocation location = QuestHudRenderer.location(quest);
                String title = QuestText.title(quest).getString();
                String symbol = symbol(quest.trailingIcon().image(true), quest.trailingIcon().trackingStyle());
                int color = Math.floorMod(quest.trailingIcon().trackingStyle().hashCode(), 22);
                Object waypoint = constructor.newInstance((int) Math.round(location.x()),
                        (int) Math.round(location.y()), (int) Math.round(location.z()),
                        title, symbol, color);
                invokeInt(waypoint, "setVisibilityType", WORLD_MAP_GLOBAL);
                invokeBoolean(waypoint, "setYIncluded", true);
                invoke(waypoint, "setThirdPartyOrigin",
                        ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "quest"));
                waypoints.put(entry.getKey(), waypoint);
            }
            unavailable = false;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            if (!unavailable) {
                unavailable = true;
                XeroQuestJournal.LOGGER.debug("Xaero waypoint integration unavailable", ignored);
            }
        }
    }

    private static boolean sameDimension(Minecraft game, QuestLocation location) {
        return location.dimension().isBlank()
                || location.dimension().equals(game.level.dimension().location().toString());
    }

    private static String symbol(String image, String style) {
        String source = image == null || image.isBlank() ? style : image;
        int slash = Math.max(source.lastIndexOf(':'), source.lastIndexOf('/'));
        String tail = slash >= 0 ? source.substring(slash + 1) : source;
        return tail.isBlank() ? "?" : tail.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    private static void clearIfPossible() {
        if (lastSignature.isEmpty()) return;
        lastSignature = "";
        try {
            Class<?> managerType = Class.forName("xaero.common.minimap.waypoints.WaypointsManager");
            @SuppressWarnings("unchecked")
            Hashtable<Integer, Object> waypoints = (Hashtable<Integer, Object>) managerType
                    .getMethod("getCustomWaypoints", String.class).invoke(null, ORIGIN);
            waypoints.clear();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional dependency is allowed to be absent.
        }
    }

    private static void invokeInt(Object target, String name, int value) throws ReflectiveOperationException {
        target.getClass().getMethod(name, int.class).invoke(target, value);
    }

    private static void invokeBoolean(Object target, String name, boolean value) throws ReflectiveOperationException {
        target.getClass().getMethod(name, boolean.class).invoke(target, value);
    }

    private static void invoke(Object target, String name, Object value) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) {
                method.invoke(target, value);
                return;
            }
        }
    }
}
