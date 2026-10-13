package xtdpotato.xero_quest_journal.fabriccompat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class FabricDeferredRegister<T> {
    private final Registry<T> registry;
    private final String namespace;
    private final List<Runnable> registrations = new ArrayList<>();
    private FabricDeferredRegister(Registry<T> registry, String namespace) {
        this.registry = registry; this.namespace = namespace;
    }
    public static <T> FabricDeferredRegister<T> create(Registry<T> registry, String namespace) {
        return new FabricDeferredRegister<>(registry, namespace);
    }
    public <V extends T> Supplier<V> register(String id, Supplier<V> factory) {
        final class Entry implements Supplier<V> {
            private V value;
            public V get() {
                if (value == null) throw new IllegalStateException("Unregistered entry " + id);
                return value;
            }
        }
        var entry = new Entry();
        registrations.add(() -> {
            entry.value = factory.get();
            Registry.register(registry, ResourceLocation.fromNamespaceAndPath(namespace, id), entry.value);
        });
        return entry;
    }
    public void register(FabricEventBus ignored) { registrations.forEach(Runnable::run); }
}
