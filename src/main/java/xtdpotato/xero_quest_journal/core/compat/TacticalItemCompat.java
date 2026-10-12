package xtdpotato.xero_quest_journal.core.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.lang.reflect.Method;
import java.util.*;

/** Optional public APIs, isolated from class linking when the equipment mods are absent. */
public final class TacticalItemCompat {
    public record Model(String kind, ResourceLocation id, ItemStack stack, Component name, int tier) {}
    public record Material(List<ItemStack> alternatives, int count) {}
    public record Recipe(ItemStack result, List<Material> ingredients) {}
    private static final String TACZ = "com.tacz.guns.api.TimelessAPI";
    private static final String LR = "me.xjqsh.lrtactical.api.LrTacticalAPI";
    private static final Set<String> WARNED = new HashSet<>();

    private TacticalItemCompat() {}

    public static List<Model> models(HolderLookup.Provider registries) {
        List<Model> result = new ArrayList<>();
        if (PlatformServices.isModLoaded("tacz")) {
            for (String kind : List.of("Gun", "Ammo", "Attachment", "Block")) {
                try {
                    for (Object value : (Set<?>) callStatic(TACZ, "getAllCommon" + kind + "Index")) {
                        var entry = (Map.Entry<?, ?>) value;
                        var id = (ResourceLocation) entry.getKey();
                        Object index = entry.getValue();
                        Object pojo = call(index, "getPojo");
                        if (kind.equals("Attachment") && Boolean.TRUE.equals(call(pojo, "isHidden"))) continue;
                        String key = (String) call(pojo, "getName");
                        Object builder;
                        if (kind.equals("Block")) {
                            builder = callStatic("com.tacz.guns.api.item.builder.BlockItemBuilder", "create",
                                    net.minecraft.world.level.ItemLike.class, call(index, "getBlock"));
                        } else {
                            builder = callStatic("com.tacz.guns.api.item.builder." + kind + "ItemBuilder", "create");
                        }
                        call(builder, "setId", ResourceLocation.class, id);
                        ItemStack stack;
                        if (kind.equals("Gun")) {
                            stack = (ItemStack) call(builder, "build", HolderLookup.Provider.class, registries);
                        } else stack = (ItemStack) call(builder, "build");
                        if (!stack.isEmpty())
                            result.add(new Model(kind.toLowerCase(Locale.ROOT), id, stack.copyWithCount(1),
                                    Component.translatable(key), kind.equals("Gun") ? 2 : 1));
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                    warn("TaCZ " + kind, failure);
                }
            }
        }
        if (PlatformServices.isModLoaded("lrtactical")) {
            for (String kind : List.of("Melee", "Throwable", "Consumable")) {
                try {
                    Class<?> indexApi = Class.forName("me.xjqsh.lrtactical.api.index.ICustomItemIndex");
                    for (Object index : (Collection<?>) callStatic(LR, "get" + kind + "Indexes")) {
                        ItemStack stack = (ItemStack) indexApi.getMethod("createItemStack").invoke(index);
                        var id = (ResourceLocation) indexApi.getMethod("getId").invoke(index);
                        String key;
                        try { key = (String) call(index, "getName"); }
                        catch (NoSuchMethodException absent) {
                            key = (String) indexApi.getMethod("getDescriptionId").invoke(index);
                        }
                        if (!stack.isEmpty())
                            result.add(new Model(kind.toLowerCase(Locale.ROOT), id, stack.copyWithCount(1),
                                    Component.translatable(key), kind.equals("Melee") ? 2 : 1));
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                    warn("LR " + kind, failure);
                }
            }
        }
        result.sort(Comparator.comparing(Model::kind).thenComparing(m -> m.id().toString()));
        return List.copyOf(result);
    }

    public static List<Recipe> recipes() {
        if (!PlatformServices.isModLoaded("tacz")) return List.of();
        List<Recipe> result = new ArrayList<>();
        try {
            for (Object recipe : ((Map<?, ?>) callStatic(TACZ, "getAllRecipes")).values()) {
                ItemStack output = (ItemStack) call(recipe, "getOutput");
                List<Material> materials = new ArrayList<>();
                for (Object input : (List<?>) call(recipe, "getInputs")) {
                    Ingredient ingredient = (Ingredient) call(input, "getIngredient");
                    int count = ((Number) call(input, "getCount")).intValue();
                    var alternatives = Arrays.stream(ingredient.getItems())
                            .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
                    if (!alternatives.isEmpty() && count > 0) materials.add(new Material(alternatives, count));
                }
                if (!output.isEmpty() && !materials.isEmpty())
                    result.add(new Recipe(output.copy(), List.copyOf(materials)));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            warn("TaCZ recipes", failure);
        }
        return List.copyOf(result);
    }

    public static String identity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        for (String[] type : List.of(
                new String[]{"tacz", "com.tacz.guns.api.item.IGun", "getGunId", "gun"},
                new String[]{"tacz", "com.tacz.guns.api.item.IAmmo", "getAmmoId", "ammo"},
                new String[]{"tacz", "com.tacz.guns.api.item.IAttachment", "getAttachmentId", "attachment"},
                new String[]{"tacz", "com.tacz.guns.api.item.IBlock", "getBlockId", "block"},
                new String[]{"lrtactical", "me.xjqsh.lrtactical.api.item.IMeleeWeapon", "getId", "melee"},
                new String[]{"lrtactical", "me.xjqsh.lrtactical.api.item.IThrowable", "getId", "throwable"},
                new String[]{"lrtactical", "me.xjqsh.lrtactical.api.item.IConsumable", "getId", "consumable"},
                new String[]{"lrtactical", "me.xjqsh.lrtactical.api.item.ICustomItem", "getId", "custom"})) {
            if (!PlatformServices.isModLoaded(type[0])) continue;
            try {
                Class<?> api = Class.forName(type[1]);
                if (!api.isInstance(stack.getItem())) continue;
                Object id = api.getMethod(type[2], ItemStack.class).invoke(stack.getItem(), stack);
                return id instanceof ResourceLocation location ? type[3] + "/" + location : "";
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                warn(type[1], failure);
            }
        }
        return "";
    }

    public static String displayName(String identity) {
        if (identity == null || identity.isBlank()) return "";
        int slash = identity.indexOf('/');
        int colon = identity.indexOf(':', slash + 1);
        if (slash <= 0 || colon < 0) return readable(identity);
        String kind = identity.substring(0, slash);
        ResourceLocation id = ResourceLocation.tryParse(identity.substring(slash + 1));
        if (id == null) return readable(identity);
        try {
            String key = "";
            if (Set.of("gun", "ammo", "attachment").contains(kind) && PlatformServices.isModLoaded("tacz")) {
                String indexKind = switch (kind) {
                    case "gun" -> "Gun";
                    case "ammo" -> "Ammo";
                    default -> "Attachment";
                };
                Object index = callStatic(TACZ, "getClient" + indexKind + "Index", ResourceLocation.class, id);
                if (index instanceof Optional<?> optional) index = optional.orElse(null);
                if (index == null) return readable(id.getPath());
                key = (String) call(index, "getName");
            } else if (Set.of("melee", "throwable", "consumable").contains(kind)
                    && PlatformServices.isModLoaded("lrtactical")) {
                String indexKind = switch (kind) {
                    case "melee" -> "Melee";
                    case "throwable" -> "Throwable";
                    default -> "Consumable";
                };
                for (Object index : (Collection<?>) callStatic(LR, "get" + indexKind + "Indexes")) {
                    if (id.equals(call(index, "getId"))) {
                        try { key = (String) call(index, "getName"); }
                        catch (NoSuchMethodException absent) { key = (String) call(index, "getDescriptionId"); }
                        break;
                    }
                }
            }
            if (!key.isBlank() && Language.getInstance().has(key))
                return stripFormatting(Component.translatable(key).getString());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Dynamic item models are optional and fall back to their stable resource ID.
        }
        return readable(id.getPath());
    }

    public static String translationKey(Component component) {
        return component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents value
                && value.getArgs().length == 0 && component.getSiblings().isEmpty()
                ? value.getKey() : "";
    }

    public static String readableName(ResourceLocation id) {
        return id == null ? "" : readable(id.getPath());
    }

    private static String readable(String path) {
        String value = path.substring(path.lastIndexOf(':') + 1).replace('/', ' ');
        String[] words = value.split("[_\\-.]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.isEmpty() ? path : result.toString();
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    public static boolean dynamicItem(Item item) {
        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        if (!namespace.equals("tacz") && !namespace.equals("lrtactical")) return false;
        String[] interfaces = namespace.equals("tacz")
                ? new String[]{"com.tacz.guns.api.item.IGun", "com.tacz.guns.api.item.IAmmo",
                    "com.tacz.guns.api.item.IAttachment", "com.tacz.guns.api.item.IBlock"}
                : new String[]{"me.xjqsh.lrtactical.api.item.ICustomItem"};
        for (String name : interfaces) {
            try { if (Class.forName(name).isInstance(item)) return true; }
            catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return false;
    }

    public static Object callStatic(String className, String name) throws ReflectiveOperationException {
        return Class.forName(className).getMethod(name).invoke(null);
    }

    public static Object callStatic(String className, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        return Class.forName(className).getMethod(name, parameter).invoke(null, value);
    }

    public static Object call(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }

    public static Object call(Object target, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, parameter);
        return method.invoke(target, value);
    }

    private static synchronized void warn(String feature, Throwable failure) {
        if (WARNED.add(feature)) XeroQuestJournal.LOGGER.warn("Unable to read optional {} API", feature, failure);
    }
}
