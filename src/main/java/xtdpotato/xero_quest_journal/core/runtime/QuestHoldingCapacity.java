package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import xtdpotato.xero_quest_journal.core.compat.CarriedInventoryCompat;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger;

import java.util.List;

/** Authoring feasibility checks; existing task definitions remain loadable for repair. */
public final class QuestHoldingCapacity {
    public record Hint(int required, int ordinaryCapacity, boolean impossible, boolean submission) {
        public Component message() {
            return Component.translatable("screen.xero_quest_journal.conditions.capacity."
                    + (impossible ? submission ? "submit" : "impossible" : "backpack"), required, ordinaryCapacity);
        }
    }
    private QuestHoldingCapacity() {}

    public static Hint hint(Player player, Trigger trigger) {
        if (player == null || !List.of("item_get", "item_hold", "item_submit").contains(trigger.type())) return null;
        int maximum = capacity(player, trigger.item());
        if (maximum <= 0 || trigger.count() <= maximum) return null;
        boolean submission = trigger.type().equals("item_submit");
        return new Hint(trigger.count(), maximum, submission || !CarriedInventoryCompat.installed(), submission);
    }

    private static int capacity(Player player, String target) {
        List<ItemStack> candidates;
        if (QuestItemStackValue.encoded(target)) {
            var stack = QuestItemStackValue.decode(target, player.registryAccess()).orElse(ItemStack.EMPTY);
            candidates = List.of(stack);
        } else if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            candidates = id == null ? List.of() : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                    .map(tag -> tag.stream().map(holder -> new ItemStack(holder.value())).toList()).orElse(List.of());
        } else {
            ResourceLocation id = ResourceLocation.tryParse(target);
            candidates = id != null && BuiltInRegistries.ITEM.containsKey(id)
                    ? List.of(new ItemStack(BuiltInRegistries.ITEM.get(id))) : List.of();
        }
        int maximum = 0, slots = player.getInventory().items.size() + player.getInventory().offhand.size();
        for (var stack : candidates) {
            if (stack.isEmpty()) continue;
            int equipment = stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ElytraItem ? 1 : 0;
            maximum = Math.max(maximum, Math.min(player.getInventory().getMaxStackSize(), stack.getMaxStackSize())
                    * (slots + equipment));
        }
        return maximum;
    }

    public static boolean impossible(Player player, List<Trigger> triggers) {
        return triggers.stream().anyMatch(trigger -> impossible(player, trigger));
    }

    private static boolean impossible(Player player, Trigger trigger) {
        if (!trigger.children().isEmpty()) {
            int possible = (int) trigger.children().stream().filter(child -> !impossible(player, child)).count();
            int needed = switch (trigger.type()) {
                case "any" -> 1;
                case "count" -> trigger.count();
                case "ratio" -> (trigger.children().size() * trigger.count() + 99) / 100;
                default -> trigger.children().size();
            };
            return possible < needed;
        }
        Hint hint = hint(player, trigger);
        return hint != null && hint.impossible();
    }
}
