package xtdpotato.xero_quest_journal.modern;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Exercises the actual 26.x disk storage and legacy import, without a client or EULA. */
public final class ModernSavedDataCheck {
    private static final String NAME = "xero_progress_check";
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    private static final UUID PLAYER = UUID.fromString("ad8f5eae-5505-4ff8-9418-2670d65b49a2");

    public static final class Progress extends ModernSavedData {
        CompoundTag data = new CompoundTag();
        static Progress load(CompoundTag tag, HolderLookup.Provider registries) {
            require(registries == REGISTRIES, "Loader lost registry context");
            var result = new Progress();
            result.data = tag.copy();
            return result;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            require(registries == REGISTRIES, "Encoder lost registry context");
            return data.copy();
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            verify();
        } finally {
            net.minecraft.util.Util.shutdownExecutors();
        }
    }

    private static void verify() throws Exception {
        SharedConstants.tryDetectVersion();
        var fixer = net.minecraft.util.datafix.DataFixers.getDataFixer();
        Path root = Files.createTempDirectory(Path.of("build"), "modern-data-check-");
        Path world = root.resolve("world");
        Files.createDirectories(world);
        var fixture = new CompoundTag();
        fixture.store("player", UUIDUtil.CODEC, PLAYER);
        var claim = new CompoundTag();
        claim.putBoolean("completed", true);
        claim.putBoolean("claimed", true);
        claim.putString("receipt", "delivered");
        fixture.put("quest-1", claim);
        var mail = new CompoundTag();
        mail.putBoolean("attachmentClaimed", true);
        fixture.put("mail-1", mail);

        var type = ModernSavedData.type(NAME, Progress::new, Progress::load, REGISTRIES);
        Path current = type.id().withSuffix(".dat").resolveAgainst(world);
        try (var storage = new SavedDataStorage(world, fixer, REGISTRIES)) {
            var data = ModernSavedData.get(storage, world, NAME, Progress::new, Progress::load, REGISTRIES);
            data.data = fixture.copy();
            data.setDirty();
            storage.saveAndJoin();
            require(Files.isRegularFile(current), "Namespaced save file was not written");
        }
        try (var storage = new SavedDataStorage(world, fixer, REGISTRIES)) {
            var restored = ModernSavedData.get(storage, world, NAME, Progress::new, Progress::load, REGISTRIES);
            require(restored.data.equals(fixture), "Disk reload lost progress, mail or claim receipt");
            require(restored.data.read("player", UUIDUtil.CODEC).orElseThrow().equals(PLAYER), "UUID changed");
            require(ModernSavedData.get(storage, world, NAME, Progress::new, Progress::load, REGISTRIES) == restored,
                    "Repeated lookups created distinct progress objects");
        }

        Path legacyWorld = root.resolve("legacy-world");
        Files.createDirectories(legacyWorld);
        var envelope = new CompoundTag();
        envelope.put("data", fixture.copy());
        Path legacyFile = legacyWorld.resolve(NAME + ".dat");
        NbtIo.writeCompressed(envelope, legacyFile);
        try (var storage = new SavedDataStorage(legacyWorld, fixer, REGISTRIES)) {
            var restored = ModernSavedData.get(storage, legacyWorld, NAME, Progress::new, Progress::load, REGISTRIES);
            require(restored.data.equals(fixture), "Legacy import reset progress or rewards");
            require(restored.isDirty(), "Legacy import was not scheduled for native persistence");
            storage.saveAndJoin();
            require(Files.exists(type.id().withSuffix(".dat").resolveAgainst(legacyWorld)), "Imported file not saved");
            require(NbtIo.readCompressed(legacyFile, NbtAccounter.unlimitedHeap()).equals(envelope),
                    "Legacy import changed the original backup");
        }
        Path otherWorld = root.resolve("other-world");
        Files.createDirectories(otherWorld);
        try (var storage = new SavedDataStorage(otherWorld, fixer, REGISTRIES)) {
            require(ModernSavedData.get(storage, otherWorld, NAME, Progress::new, Progress::load, REGISTRIES)
                    .data.isEmpty(), "World data leaked between servers");
        }
        Path corruptWorld = root.resolve("corrupt-world");
        Files.createDirectories(corruptWorld);
        Path corrupt = type.id().withSuffix(".dat").resolveAgainst(corruptWorld);
        Files.createDirectories(corrupt.getParent());
        byte[] invalid = {1, 2, 3, 4};
        Files.write(corrupt, invalid);
        try (var storage = new SavedDataStorage(corruptWorld, fixer, REGISTRIES)) {
            boolean rejected = false;
            try {
                ModernSavedData.get(storage, corruptWorld, NAME, Progress::new, Progress::load, REGISTRIES);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            require(rejected, "Corrupt native save silently reset reward claims");
        }
        require(java.util.Arrays.equals(Files.readAllBytes(corrupt), invalid), "Corrupt file was overwritten");
        System.out.println("PASS: native SavedData disk reload, registry context, UUID, reward/mail claims, "
                + "legacy import and backup, world isolation, corrupt-file rejection");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
