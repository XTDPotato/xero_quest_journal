package xtdpotato.xero_quest_journal.core.model;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.util.List;

/** Common title components keep localization parameters intact in both server chat and client UI. */
public final class QuestTitleText {
    private QuestTitleText() {}

    public static boolean generated(QuestDefinition quest) {
        return quest.titleKey() != null && List.of("generate.xero_quest_journal.title.collect", "generate.xero_quest_journal.title.craft",
                "generate.xero_quest_journal.title.defeat").contains(quest.titleKey());
    }

    public static Component title(QuestDefinition quest, HolderLookup.Provider registries) {
        if (quest.titleKey() == null || quest.titleKey().isBlank()) return Component.literal(quest.title());
        if (!generated(quest)) return Component.translatableWithFallback(quest.titleKey(), quest.title());
        if (quest.triggers().isEmpty()) return Component.literal(quest.title());
        var trigger = quest.triggers().getFirst();
        Component target = Component.literal(trigger.item());
        var id = ResourceLocation.tryParse(trigger.item());
        if (trigger.type().equals("kill")) {
            if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id))
                target = BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        } else if (QuestItemStackValue.encoded(trigger.item()) && registries != null) {
            var stack = QuestItemStackValue.decode(trigger.item(), registries).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) target = stack.getHoverName();
        } else if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            target = BuiltInRegistries.ITEM.get(id).getDescription();
        }
        return Component.translatableWithFallback(quest.titleKey(), "%s", target);
    }
}
