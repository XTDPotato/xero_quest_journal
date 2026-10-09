package xtdpotato.xero_quest_journal.core;

import net.minecraft.nbt.*;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.runtime.QuestSceneSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Small editable scene; no imported assets or native libraries are bundled. */
final class QuestTemplateSceneDefaults {
    private QuestTemplateSceneDefaults() {}

    static void ensure() throws IOException {
        var root = QuestConfig.root().resolve("structures");
        Files.createDirectories(root);
        var file = root.resolve("template_challenge.nbt");
        if (!Files.exists(file)) {
            var data = new CompoundTag();
            data.put("size", ints(7, 3, 7));
            data.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            var palette = new ListTag();
            for (String name : List.of("minecraft:stone_bricks", "minecraft:polished_andesite")) {
                var state = new CompoundTag(); state.putString("Name", name); palette.add(state);
            }
            data.put("palette", palette);
            var blocks = new ListTag();
            for (int z = 0; z < 7; z++) for (int x = 0; x < 7; x++) {
                var block = new CompoundTag(); block.put("pos", ints(x, 0, z));
                block.putInt("state", x == 3 && z == 3 ? 1 : 0); blocks.add(block);
            }
            data.put("blocks", blocks); data.put("entities", new ListTag());
            NbtIo.writeCompressed(data, file);
        }
        var settings = root.resolve("template_challenge.nbt.scene.json");
        if (!Files.exists(settings)) {
            var scene = new QuestSceneSettings("task", "build", "return",
                    new QuestSceneSettings.Spawn(3.5, 1, .5, 0, 0),
                    List.of(new QuestSceneSettings.Slot(0, "minecraft:gold_block", 1)), List.of(),
                    List.of(new QuestSceneSettings.Trigger("gold_block", "block", 3, 1, 3, 0,
                            "minecraft:gold_block", 1, "current", 0, 4, true, "complete", "")));
            AtomicFiles.write(settings, QuestSceneSettings.JSON.toJson(scene), false);
        }
    }

    private static ListTag ints(int... values) {
        var tag = new ListTag();
        for (int value : values) tag.add(IntTag.valueOf(value));
        return tag;
    }
}
