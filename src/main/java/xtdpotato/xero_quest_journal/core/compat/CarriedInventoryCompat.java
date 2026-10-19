package xtdpotato.xero_quest_journal.core.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.platform.PlatformServices;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/** Read-only optional storage APIs. No container is opened or consumed during holding checks. */
public final class CarriedInventoryCompat {
    public record Contents(Object identity, List<ItemStack> items) {}
    private static final Set<String> WARNED = new HashSet<>();
    private static final String SOPHISTICATED = "net.p3pp3rf1y.sophisticatedbackpacks.";
    private static final String[] BACKPACK_MODS = {"sophisticatedbackpacks", "simplybackpacks",
            "travelersbackpack"};

    private CarriedInventoryCompat() {}

    public static boolean installed() {
        return Arrays.stream(BACKPACK_MODS).anyMatch(PlatformServices::isModLoaded);
    }

    public static boolean isBackpack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String namespace = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return PlatformServices.isModLoaded(namespace) && Arrays.asList(BACKPACK_MODS).contains(namespace);
    }

    public static List<ItemStack> equipped(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        if (PlatformServices.isModLoaded("sophisticatedbackpacks")) {
            try {
                Class<?> providerType = Class.forName(SOPHISTICATED + "util.PlayerInventoryProvider");
                Class<?> callback = Class.forName(SOPHISTICATED + "util.PlayerInventoryProvider$BackpackInventorySlotConsumer");
                Object consumer = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                        (proxy, method, args) -> {
                            if (method.getName().equals("accept")) {
                                if (args[0] instanceof ItemStack stack) result.add(stack);
                                return false;
                            }
                            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                            if (method.getName().equals("equals")) return proxy == args[0];
                            return "Xero carried inventory reader";
                        });
                providerType.getMethod("runOnBackpacks", Player.class, callback)
                        .invoke(providerType.getMethod("get").invoke(null), player, consumer);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                warn("Sophisticated equipped backpacks", failure);
            }
        }
        if (PlatformServices.isModLoaded("curios")) {
            try {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                Object optional;
                try {
                    optional = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, player);
                } catch (NoSuchMethodException legacy) {
                    Object helper = api.getMethod("getCuriosHelper").invoke(null);
                    optional = method(helper.getClass(), "getCuriosHandler", LivingEntity.class).invoke(helper, player);
                }
                Object inventory = optional(optional);
                if (inventory != null) {
                    for (Object handler : ((Map<?, ?>) call(inventory, "getCurios")).values())
                        result.addAll(handlerItems(call(handler, "getStacks")));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                warn("Curios carried items", failure);
            }
        }
        return List.copyOf(result);
    }

    public static Contents contents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (PlatformServices.isModLoaded("sophisticatedbackpacks")) {
            try {
                Class<?> wrapperType = Class.forName(SOPHISTICATED + "backpack.wrapper.BackpackWrapper");
                Object wrapper = wrapperType.getMethod("fromStack", ItemStack.class).invoke(null, stack);
                Object uuid = optional(call(wrapper, "getContentsUuid"));
                if (uuid != null) return new Contents("sophisticatedbackpacks:" + uuid,
                        handlerItems(call(wrapper, "getInventoryHandler")));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                warn("Sophisticated backpack contents", failure);
            }
        }
        try {
            Class<?> capability = Class.forName("net.neoforged.neoforge.capabilities.ItemCapability");
            Object key = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler")
                    .getField("ITEM").get(null);
            Object handler = ItemStack.class.getMethod("getCapability", capability).invoke(stack, key);
            if (handler != null) return new Contents(handler, handlerItems(handler));
        } catch (ClassNotFoundException ignored) {
            // The other loaders provide their own optional public storage API below.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            warn("NeoForge item storage", failure);
        }
        try {
            Class<?> capability = Class.forName("net.minecraftforge.common.capabilities.Capability");
            Object key = Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities")
                    .getField("ITEM_HANDLER").get(null);
            Object handler = optional(ItemStack.class.getMethod("getCapability", capability).invoke(stack, key));
            if (handler != null) return new Contents(handler, handlerItems(handler));
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            warn("Forge item storage", failure);
        }
        try {
            Class<?> contextType = Class.forName("net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext");
            Object context = contextType.getMethod("withConstant", ItemStack.class).invoke(null, stack);
            Object lookup = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemStorage").getField("ITEM").get(null);
            Class<?> lookupType = Class.forName("net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup");
            Object storage = contextType.getMethod("find", lookupType).invoke(context, lookup);
            if (storage instanceof Iterable<?> iterable) {
                List<ItemStack> items = new ArrayList<>();
                Class<?> viewType = Class.forName("net.fabricmc.fabric.api.transfer.v1.storage.StorageView");
                Class<?> variantType = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemVariant");
                for (Object view : iterable) {
                    if (items.size() >= 4096) break;
                    long amount = ((Number) viewType.getMethod("getAmount").invoke(view)).longValue();
                    if (amount <= 0) continue;
                    Object variant = viewType.getMethod("getResource").invoke(view);
                    ItemStack item = (ItemStack) variantType.getMethod("toStack", int.class)
                            .invoke(variant, (int) Math.min(1_000_000, amount));
                    if (!item.isEmpty()) items.add(item);
                }
                return new Contents(storage, List.copyOf(items));
            }
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            warn("Fabric item storage", failure);
        }
        return null;
    }

    private static List<ItemStack> handlerItems(Object handler) throws ReflectiveOperationException {
        int slots = Math.min(4096, ((Number) call(handler, "getSlots")).intValue());
        List<ItemStack> items = new ArrayList<>();
        Method getter = method(handler.getClass(), "getStackInSlot", int.class);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack item = (ItemStack) getter.invoke(handler, slot);
            if (!item.isEmpty()) items.add(item.copy());
        }
        return List.copyOf(items);
    }

    private static Object optional(Object value) throws ReflectiveOperationException {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        Object resolved = call(value, "resolve");
        return resolved instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return method(target.getClass(), name).invoke(target);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameters);
        if (!java.lang.reflect.Modifier.isPublic(method.getDeclaringClass().getModifiers()))
            method.trySetAccessible();
        return method;
    }

    private static void warn(String integration, Throwable failure) {
        if (WARNED.add(integration)) XeroQuestJournal.LOGGER.warn("Unable to read {}: {}", integration,
                failure.toString());
    }
}
