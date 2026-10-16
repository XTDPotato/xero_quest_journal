package xtdpotato.xero_quest_journal.client;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.platform.PlatformServices;

/** Optional REI favorite editing through the public API; no linkage when REI is absent. */
public final class QuestReiShortcuts {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "inventory_shortcut");
    private QuestReiShortcuts() {}
    public static boolean available() { return PlatformServices.isModLoaded("roughlyenoughitems"); }
    public static boolean contains(String kind) {
        if (!available()) return false;
        try {
            Object entry = createEntry(kind);
            return favoriteEntries().stream().anyMatch(entry::equals);
        }
        catch (ReflectiveOperationException failure) { return false; }
    }
    public static void set(String kind, boolean enabled) {
        if (!available() || !List.of("task", "mail").contains(kind)) return;
        try {
            var entries = favoriteEntries();
            Object entry = createEntry(kind);
            boolean exists = entries.stream().anyMatch(entry::equals);
            if (enabled && !exists) ((java.util.List<Object>) entries).add(entry);
            if (!enabled && exists) entries.remove(entry);
            Object manager = Class.forName("me.shedaniel.rei.api.client.config.ConfigManager")
                    .getMethod("getInstance").invoke(null);
            manager.getClass().getMethod("saveConfig").invoke(manager);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not update REI shortcuts", failure);
        }
    }
    private static java.util.List<?> favoriteEntries() throws ReflectiveOperationException {
        Object manager = Class.forName("me.shedaniel.rei.api.client.config.ConfigManager").getMethod("getInstance").invoke(null);
        Object config = manager.getClass().getMethod("getConfig").invoke(manager);
        return (java.util.List<?>) config.getClass().getMethod("getFavoriteEntries").invoke(config);
    }
    private static Object registry() throws ReflectiveOperationException {
        return Class.forName("me.shedaniel.rei.api.client.favorites.FavoriteEntryType")
                .getMethod("registry").invoke(null);
    }
    private static Object createEntry(String kind) throws ReflectiveOperationException {
        Object type = registry().getClass().getMethod("get", ResourceLocation.class).invoke(registry(), ID);
        Object result = type.getClass().getMethod("fromArgs", Object[].class)
                .invoke(type, (Object) new Object[]{kind});
        return result.getClass().getMethod("orElseThrow").invoke(result);
    }
}
