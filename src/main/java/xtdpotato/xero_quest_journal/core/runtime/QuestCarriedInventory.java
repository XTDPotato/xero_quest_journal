package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.compat.CarriedInventoryCompat;

import java.util.*;

/** One bounded, read-only carried-storage snapshot per player/tick, shared by holding conditions. */
public final class QuestCarriedInventory {
    private record Cached(long tick, int changes, List<ItemStack> items, Map<String, Integer> counts) {}
    private static final Map<ServerPlayer, Cached> CACHE = new WeakHashMap<>();
    private QuestCarriedInventory() {}

    public static void invalidate(ServerPlayer player) { CACHE.remove(player); }

    public static int count(ServerPlayer player, String target) {
        Cached cached = CACHE.get(player);
        long tick = player.level().getGameTime();
        int changes = player.getInventory().getTimesChanged();
        if (cached == null || cached.tick() != tick || cached.changes() != changes) {
            List<ItemStack> roots = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                roots.add(player.getInventory().getItem(slot));
            roots.addAll(CarriedInventoryCompat.equipped(player));
            List<ItemStack> contents = new ArrayList<>();
            Set<ItemStack> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<Object> storageIdentities = new HashSet<>();
            int[] remaining = {32768};
            for (var stack : roots) collect(stack, 0, contents, stacks, storageIdentities, remaining);
            cached = new Cached(tick, changes, List.copyOf(contents), new HashMap<>());
            CACHE.put(player, cached);
        }
        Cached snapshot = cached;
        return cached.counts().computeIfAbsent(target, ignored -> {
            long result = 0;
            for (var stack : snapshot.items())
                if (QuestConditions.matches(stack, target, player.registryAccess())) result += stack.getCount();
            return (int) Math.min(1_000_000, result);
        });
    }

    private static void collect(ItemStack stack, int depth, List<ItemStack> result, Set<ItemStack> stacks,
                                Set<Object> storages, int[] remaining) {
        if (stack.isEmpty() || remaining[0]-- <= 0 || !stacks.add(stack)) return;
        result.add(stack.copy());
        if (depth >= 8 || !CarriedInventoryCompat.isBackpack(stack)) return;
        var contents = CarriedInventoryCompat.contents(stack);
        if (contents == null || !storages.add(contents.identity())) return;
        for (var item : contents.items()) collect(item, depth + 1, result, stacks, storages, remaining);
    }
}
