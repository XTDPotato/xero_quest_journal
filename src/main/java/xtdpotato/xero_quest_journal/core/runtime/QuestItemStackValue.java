package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/** Compact, registry-aware ItemStack identity used for component-specific quest items. */
public final class QuestItemStackValue {
    private static final String PREFIX = "xqj-stack:";
    private static final String MODEL_PREFIX = "xqj-model:";
    private QuestItemStackValue() {}

    public static String encode(ItemStack input, HolderLookup.Provider registries) {
        if (input == null || input.isEmpty()) return "";
        try {
            String serialized = input.copyWithCount(1).save(registries).toString();
            if (serialized.getBytes(StandardCharsets.UTF_8).length > 4096) return "";
            return PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(serialized.getBytes(StandardCharsets.UTF_8));
        } catch (Exception invalid) {
            return "";
        }
    }

    public static Optional<ItemStack> decode(String value, HolderLookup.Provider registries) {
        if (!encoded(value) || value.length() > 8192) return Optional.empty();
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value.substring(prefix(value).length()));
            if (bytes.length > 4096) return Optional.empty();
            String serialized = new String(bytes, StandardCharsets.UTF_8);
            return ItemStack.parse(registries, TagParser.parseTag(serialized))
                    .filter(stack -> !stack.isEmpty());
        } catch (Exception invalid) {
            return Optional.empty();
        }
    }

    public static boolean matches(ItemStack stack, String value, HolderLookup.Provider registries) {
        ItemStack expected = decode(value, registries).orElse(ItemStack.EMPTY);
        if (model(value)) {
            String identity = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(expected);
            return !expected.isEmpty() && ItemStack.isSameItem(stack, expected) && !identity.isBlank()
                    && identity.equals(xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(stack));
        }
        return !stack.isEmpty() && !expected.isEmpty() && ItemStack.isSameItemSameComponents(stack, expected);
    }

    public static String encodeModel(ItemStack stack, HolderLookup.Provider registries) {
        if (xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(stack).isBlank()) return "";
        String value = encode(stack, registries);
        return value.isBlank() ? "" : MODEL_PREFIX + value.substring(PREFIX.length());
    }

    public static boolean model(String value) {
        return value != null && value.startsWith(MODEL_PREFIX);
    }

    public static String modelIdentity(String value, HolderLookup.Provider registries) {
        if (!model(value)) return "";
        return xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat.identity(
                decode(value, registries).orElse(ItemStack.EMPTY));
    }

    private static String prefix(String value) { return model(value) ? MODEL_PREFIX : PREFIX; }

    public static boolean encoded(String value) {
        return value != null && (value.startsWith(PREFIX) || value.startsWith(MODEL_PREFIX));
    }

    public static String itemId(String value) {
        if (!encoded(value) || value.length() > 8192) return "";
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value.substring(prefix(value).length()));
            if (bytes.length > 4096) return "";
            var tag = TagParser.parseTag(new String(bytes, StandardCharsets.UTF_8));
            return tag instanceof CompoundTag compound ? compound.getString("id") : "";
        } catch (Exception invalid) {
            return "";
        }
    }

    public static boolean structurallyValid(String value) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId(value));
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                || new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)).isEmpty()) return false;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null || decode(value, server.registryAccess()).isPresent();
    }
}
