package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

/** Small, deterministic condition language for reusable loot entries. */
public final class QuestLootConditions {
    private QuestLootConditions() {}

    public static boolean matches(ServerPlayer player, String expression) {
        String value = expression == null || expression.isBlank() ? "always" : expression.strip();
        if (value.equalsIgnoreCase("always") || value.equalsIgnoreCase("true")) return true;
        if (player == null) return false;
        if (value.startsWith("dimension:")) {
            return player.level().dimension().location().toString().equals(value.substring("dimension:".length()).strip());
        }
        if (value.startsWith("has_item:")) {
            String item = value.substring("has_item:".length()).strip();
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null) return false;
            return player.getInventory().items.stream().anyMatch(stack ->
                    !stack.isEmpty() && stack.getItemHolder().is(id));
        }
        if (value.startsWith("quest_complete:")) {
            var quest = QuestRepository.find(value.substring("quest_complete:".length()).strip());
            return quest != null && QuestProgressData.get(player.getServer()).entry(player.getUUID(), quest) != null;
        }
        if (value.startsWith("random:")) {
            try {
                double chance = Double.parseDouble(value.substring("random:".length()).strip());
                return chance >= 1 || chance > 0 && player.getRandom().nextDouble() < chance;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        return false;
    }

    public static boolean valid(String expression) {
        if (expression == null || expression.isBlank() || expression.equalsIgnoreCase("always")
                || expression.equalsIgnoreCase("true")) return true;
        if (expression.startsWith("dimension:")) return ResourceLocation.tryParse(
                expression.substring("dimension:".length()).strip()) != null;
        if (expression.startsWith("has_item:")) return ResourceLocation.tryParse(
                expression.substring("has_item:".length()).strip()) != null;
        if (expression.startsWith("quest_complete:")) return !expression.substring("quest_complete:".length()).strip().isBlank();
        if (expression.startsWith("random:")) {
            try {
                double value = Double.parseDouble(expression.substring("random:".length()).strip());
                return value >= 0 && value <= 1;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        return false;
    }
}
