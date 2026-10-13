package xtdpotato.xero_quest_journal.modern;

import com.mojang.serialization.Codec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Keeps the shared NBT schema while using 26.x registry-aware SavedData codecs. */
public abstract class ModernSavedData extends SavedData {
    public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);

    public static <T extends ModernSavedData> SavedDataType<T> type(
            String name, Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader, HolderLookup.Provider registries) {
        Codec<T> codec = CompoundTag.CODEC.xmap(
                tag -> loader.apply(tag, registries),
                data -> data.save(new CompoundTag(), registries));
        return new SavedDataType<>(Identifier.fromNamespaceAndPath("xero_quest_journal", name),
                constructor, codec, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    }

    public static <T extends ModernSavedData> T get(
            MinecraftServer server, String name, Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader) {
        if (!server.isSameThread()) throw new IllegalStateException("Saved data requires the server thread");
        return get(server.overworld().getDataStorage(),
                server.getWorldPath(LevelResource.ROOT).resolve("data"), name, constructor, loader,
                server.registryAccess());
    }

    public static <T extends ModernSavedData> T get(
            SavedDataStorage storage, Path directory, String name, Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader, HolderLookup.Provider registries) {
        var type = type(name, constructor, loader, registries);
        T existing = storage.get(type);
        if (existing != null) return existing;
        Path current = type.id().withSuffix(".dat").resolveAgainst(directory);
        // Vanilla logs codec/read failures and returns null. Never replace such a file with empty claims.
        if (Files.exists(current)) throw new IllegalStateException("Could not read saved data: " + current);
        Path legacy = directory.resolve(name + ".dat");
        if (!Files.exists(legacy)) return storage.computeIfAbsent(type);
        try {
            CompoundTag envelope = NbtIo.readCompressed(legacy, NbtAccounter.unlimitedHeap());
            CompoundTag tag = envelope.getCompound("data").orElseThrow(
                    () -> new IOException("Missing data compound in " + legacy));
            T restored = loader.apply(tag, registries);
            restored.setDirty();
            storage.set(type, restored);
            return restored;
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not import saved data: " + legacy, failure);
        }
    }
}
