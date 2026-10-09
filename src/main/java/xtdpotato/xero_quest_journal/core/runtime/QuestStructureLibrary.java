package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.QuestConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Local authoring operations; call on the integrated server thread only. */
public final class QuestStructureLibrary {
    private QuestStructureLibrary() {}

    public static void authorize(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2) || !player.server.isSameThread())
            throw new IllegalArgumentException("structure.permission");
    }

    private static Path root() {
        return QuestConfig.root().resolve("structures").toAbsolutePath().normalize();
    }

    public static List<String> worldTemplates(ServerPlayer player) {
        authorize(player);
        return player.server.getStructureManager().listTemplates()
                .map(ResourceLocation::toString).sorted().limit(2048).toList();
    }

    public static CompoundTag previewTemplate(ServerPlayer player, String id) {
        authorize(player);
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) throw new IllegalArgumentException("structure.invalid");
        return QuestStructures.preview(QuestStructures.validate(player.server.getStructureManager().get(key)
                .orElseThrow(() -> new IllegalArgumentException("structure.missing"))
                .save(new CompoundTag())));
    }

    public static String importTemplate(ServerPlayer player, String id) throws Exception {
        authorize(player);
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) throw new IllegalArgumentException("structure.invalid");
        var template = player.server.getStructureManager().get(key)
                .orElseThrow(() -> new IllegalArgumentException("structure.missing"));
        CompoundTag data = QuestStructures.validate(template.save(new CompoundTag()));
        String reference = key.getNamespace() + "/" + key.getPath() + ".nbt";
        return write(reference, data);
    }

    /** Save a new, independent file, never replace the source of an active instance. */
    static String write(String reference, CompoundTag data) throws Exception {
        Path root = root(), target = QuestStructures.path(reference);
        if (!target.startsWith(root) || Files.exists(target))
            throw new IllegalArgumentException("structure.exists");
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".structure-", ".nbt");
        try {
            NbtIo.writeCompressed(data, temp);
            if (Files.size(temp) > 2_097_152) throw new IllegalArgumentException("structure.large");
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return reference;
    }

    public static String saveEdit(ServerPlayer author, boolean draft) throws Exception {
        authorize(author);
        if (!QuestStructures.editing(author)) throw new IllegalArgumentException("structure.not_editing");
        var state = QuestStructures.snapshot(author);
        var size = state.getList("size", net.minecraft.nbt.Tag.TAG_INT);
        var template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
        template.fillFromWorld(author.serverLevel(), QuestStructures.origin(author),
                new net.minecraft.core.Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)), true, null);
        CompoundTag data = QuestStructures.validate(template.save(new CompoundTag()));
        String original = state.getString("reference");
        String reference = (draft ? "drafts/" : "versions/")
                + original.substring(0, original.length() - 4) + "-"
                + java.time.Instant.now().toEpochMilli() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".nbt";
        write(reference, data);
        try {
            Files.writeString(QuestStructures.path(reference + ".scene.json"), state.getString("settings"),
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (Exception failure) {
            Files.deleteIfExists(QuestStructures.path(reference));
            throw failure;
        }
        return reference;
    }

    public static int delete(ServerPlayer player, List<String> references) throws Exception {
        authorize(player);
        int count = 0;
        for (String reference : references) {
            Path file = QuestStructures.path(reference);
            if (!reference.endsWith(".nbt"))
                throw new IllegalArgumentException("structure.invalid");
            Path settings = QuestStructures.path(reference + ".scene.json");
            if (Files.isRegularFile(file) && Files.deleteIfExists(file)) {
                Files.deleteIfExists(settings);
                count++;
            }
        }
        return count;
    }
}
