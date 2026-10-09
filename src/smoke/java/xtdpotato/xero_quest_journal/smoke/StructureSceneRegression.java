package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class StructureSceneRegression {
    private static final String ID = "scene_" + System.currentTimeMillis();
    private static final String FILE = ID + ".nbt";
    private static int stage, wait;
    private static CompletableFuture<Void> pending;
    private static BlockPos origin;
    private static ItemStack named;
    private static String originalSettings;
    private static long started;
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ServerPlayer player(Minecraft game) { return game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID()); }
    private interface Check { void run(ServerPlayer p) throws Exception; }
    private static void run(Minecraft game, Check check) {
        pending = CompletableFuture.runAsync(() -> {
            try { check.run(player(game)); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        }, game.getSingleplayerServer());
    }
    static boolean tick(Minecraft game) throws Exception {
        if (game.player == null || game.level == null || --wait > 0) return false;
        if (pending != null) {
            if (!pending.isDone()) return false;
            pending.join(); pending = null;
        }
        switch (stage) {
            case 0 -> {
                if (game.screen != null) return false;
                run(game, StructureSceneRegression::setup);
            }
            case 1 -> {
                run(game, p -> {
                    enter(p);
                    require(QuestStructures.active(p) && QuestStructures.task(p).equals(ID), "Missing scene identity");
                    origin = QuestStructures.origin(p);
                    started = p.level().getGameTime();
                    require(ItemStack.isSameItemSameComponents(named, p.getInventory().getItem(4))
                            && p.getInventory().getItem(4).getCount() == 2, "Equipment components/slot lost");
                    require(p.getInventory().countItem(Items.DIAMOND) == 0, "Original inventory not isolated");
                    require(!QuestStructures.allowsProgress(p, QuestRepository.find(ID + "_other")), "Other task progressed");
                    require(QuestRuntime.trigger(p, ID + "_other", "complete", false).equals("scene_active"), "Completion bypass");
                    require(QuestRuntime.activate(p, ID + "_other").equals("scene_active"), "Manual completion bypass");
                    var level = p.serverLevel();
                    var furnacePos = origin.offset(1, 0, 1);
                    level.setBlock(furnacePos, Blocks.FURNACE.defaultBlockState(), 3);
                    var furnace = (FurnaceBlockEntity) level.getBlockEntity(furnacePos);
                    furnace.setItem(0, new ItemStack(Items.IRON_ORE));
                    furnace.setItem(1, new ItemStack(Items.COAL));
                    furnace.setChanged();
                    level.setBlock(origin.offset(2, 0, 2), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
                    level.setBlock(origin.offset(3, 0, 2), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                    require(level.getBlockState(origin.offset(2, 0, 2)).getValue(RedstoneLampBlock.LIT), "Redstone update missing");
                    level.setBlock(origin.offset(3, 0, 2), Blocks.AIR.defaultBlockState(), 3);
                    var interaction = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND,
                            furnacePos, new BlockHitResult(Vec3.atCenterOf(furnacePos), Direction.UP, furnacePos, false));
                    NeoForge.EVENT_BUS.post(interaction);
                    require(interaction.isCanceled(), "View-only container permission bypass");
                    var outside = new PlayerInteractEvent.RightClickItem(p, InteractionHand.MAIN_HAND);
                    NeoForge.EVENT_BUS.post(outside);
                    require(outside.isCanceled(), "View-only item permission bypass");
                });
                wait = 230;
            }
            case 2 -> run(game, p -> {
                require(p.level().getGameTime() - started >= 200, "Real server ticks did not advance");
                var furnace = (FurnaceBlockEntity) p.level().getBlockEntity(origin.offset(1, 0, 1));
                require(furnace.getItem(2).is(Items.IRON_INGOT), "View-only stopped furnace world ticks");
                require(!p.level().getBlockState(origin.offset(2, 0, 2)).getValue(RedstoneLampBlock.LIT),
                        "Scheduled redstone tick did not run");
                p.serverLevel().setBlock(origin.offset(0, 0, 0), QuestSceneBlocks.COMPLETION.get().defaultBlockState(), 3);
                p.serverLevel().setBlock(origin.offset(0, 0, 1), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                wait = 8;
            });
            case 3 -> run(game, p -> {
                require(QuestProgressData.get(p.server).entry(p.getUUID(), QuestRepository.find(ID)) != null,
                        "Redstone completion block did not complete bound task");
                require(QuestRuntime.claim(p, ID).equals("scene_active"), "Reward mixed into temporary inventory");
                QuestStructures.reset(p);
                require(QuestProgressData.get(p.server).entry(p.getUUID(), QuestRepository.find(ID)) != null,
                        "Scene reset reset formal completion");
                require(p.getInventory().getItem(4).getCount() == 2, "Reset did not restore task equipment");
                require(p.serverLevel().getBlockState(origin.offset(1, 0, 1)).isAir(), "Reset did not remove runtime blocks");
                p.getInventory().setItem(10, new ItemStack(Items.NETHERITE_INGOT, 64));
                QuestStructures.leave(p);
                require(p.level().dimension().equals(Level.OVERWORLD), "Return failed");
                require(p.getInventory().countItem(Items.DIAMOND) == 7
                        && p.getInventory().countItem(Items.NETHERITE_INGOT) == 0
                        && p.getInventory().countItem(Items.STICK) == 0, "Scene items escaped/return snapshot lost");
                require(QuestRuntime.claim(p, ID).equals("claimed"), "Reward not delivered after restore");
                require(p.getInventory().countItem(Items.EMERALD) == 3, "Reward delivery failed");
                require(QuestRuntime.claim(p, ID).equals("already_claimed"), "Reward duplicate");
                QuestRuntime.resetTrusted(p, ID);
                enter(p);
                originalSettings = QuestSceneSettings.source(FILE);
                Files.writeString(QuestStructures.path(FILE + ".scene.json"), originalSettings.replace("\"view\"", "\"interact\""));
            });
            case 4 -> run(game, p -> {
                require(!QuestStructures.current(p), "Changed template settings not invalidated");
                require(QuestStructures.complete(p).equals("unavailable"), "Stale instance completed");
                Files.writeString(QuestStructures.path(FILE + ".scene.json"), originalSettings);
                require(!QuestStructures.current(p), "Restoring old bytes removed sticky invalidation");
                boolean resetRejected = false;
                try { QuestStructures.reset(p); } catch (IllegalArgumentException invalid) { resetRejected = true; }
                require(resetRejected, "Reset refreshed stale instance qualification");
                QuestStructures.leave(p);
                require(p.getInventory().countItem(Items.EMERALD) == 3, "Previously valid reward removed");
                var settings = new QuestSceneSettings("player_copy", "regions", "checkpoint", null, List.of(),
                        List.of(new QuestSceneSettings.Region(0, 0, 0, 3, 3, 3, true, false, false)),
                        List.of(new QuestSceneSettings.Trigger("stone", "block", 2, 0, 2, 0, "minecraft:gold_block", 1,
                                "rising", 0, 20, true, "complete", "")));
                Files.writeString(QuestStructures.path(FILE + ".scene.json"), QuestSceneSettings.JSON.toJson(settings));
                enter(p);
                require(p.getInventory().countItem(Items.DIAMOND) == 7, "Player copy mode missing items");
                require(QuestStructures.allows(p, QuestStructures.origin(p).offset(1, 1, 1), "use")
                        && !QuestStructures.allows(p, QuestStructures.origin(p).offset(1, 1, 1), "build")
                        && !QuestStructures.allows(p, QuestStructures.origin(p).offset(8, 1, 1), "use"), "Region permissions wrong");
                QuestStructures.checkpoint(p);
                var checkpoint = p.position();
                p.teleportTo(p.serverLevel(), checkpoint.x + 3, checkpoint.y, checkpoint.z, 0, 0);
                var death = new LivingDeathEvent(p, p.damageSources().generic());
                NeoForge.EVENT_BUS.post(death);
                require(death.isCanceled() && QuestStructures.active(p) && p.position().distanceTo(checkpoint) < .01,
                        "Checkpoint death did not remain in scene");
                QuestSceneTriggers.tick(p);
                p.serverLevel().setBlock(QuestStructures.origin(p).offset(2, 0, 2), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                QuestSceneTriggers.tick(p);
                require(QuestProgressData.get(p.server).entry(p.getUUID(), QuestRepository.find(ID)) != null,
                        "Attached block trigger not bound to local scene");
                p.getInventory().clearContent();
                QuestStructures.leave(p);
                require(p.getInventory().countItem(Items.DIAMOND) == 7, "Player copy mutated original inventory");
                QuestStructures.edit(p, FILE);
                require(QuestStructures.editing(p), "Editor flag missing");
                require(!QuestStructures.allowsProgress(p, QuestRepository.find(ID)), "Editor can grant rewards");
                require(QuestStructures.allows(p, QuestStructures.origin(p).offset(1, 1, 1), "build")
                        && !QuestStructures.allows(p, QuestStructures.origin(p).offset(9, 1, 1), "build"), "Editor bounds");
                p.serverLevel().setBlock(QuestStructures.origin(p).offset(1, 1, 1), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                String copy = QuestStructureLibrary.saveEdit(p, false), draft = QuestStructureLibrary.saveEdit(p, true);
                require(copy.startsWith("versions/") && draft.startsWith("drafts/"), "Draft/version naming");
                require(Files.exists(QuestStructures.path(copy + ".scene.json")), "Version lost settings");
                require(!QuestStructures.read(FILE).toString().contains("minecraft:diamond_block")
                        && QuestStructures.read(copy).toString().contains("minecraft:diamond_block"), "Editor overwrote original");
            });
            case 5 -> {
                game.setScreen(new QuestSceneActionsScreen(null));
                XeroTips.clear();
                ClientStructures.enter(ID, "enter");
                wait = 15;
            }
            case 6 -> {
                require(XeroTips.history().stream().anyMatch(message -> message.getString().equals(
                        Component.translatable("screen.xero_quest_journal.structure.already_inside").getString())),
                        "Re-enter warning was not shown as an on-screen tip");
                require(game.screen.children().stream().anyMatch(w -> w instanceof StyledButton b
                        && b.styleId().equals("scene-draft")), "Draft button absent");
                capture(game, "structure-scene-actions");
                var journal = QuestScreen.task(null, ID);
                game.setScreen(journal);
                require(journal.children().stream().anyMatch(w -> w instanceof StyledButton b
                        && b.styleId().equals("structure-leave")), "Scene exit button absent");
                require(journal.children().stream().anyMatch(w -> w instanceof StyledButton b
                        && b.styleId().equals("structure-reset")), "Scene reset button absent");
                require(journal.children().stream().noneMatch(w -> w instanceof StyledButton b
                        && b.getMessage().getString().equals("场景设置")), "Old scene settings entry remains");
                game.setScreen(new QuestSceneSettingsScreen(game.screen, FILE, true));
                wait = 15;
            }
            case 7 -> {
                require(game.screen.children().stream().anyMatch(w -> w instanceof StyledChoiceButton<?>),
                        "Settings selectors missing");
                require(game.screen.children().stream().anyMatch(w -> w instanceof net.minecraft.client.gui.components.MultiLineEditBox),
                        "Advanced settings editor absent");
                capture(game, "structure-scene-settings");
                game.setScreen(null);
                run(game, p -> {
                    QuestStructures.leave(p); QuestStructures.leave(p);
                    QuestRuntime.resetTrusted(p, ID);
                    String source = QuestRepository.source(ID);
                    Files.writeString(QuestRepository.fileOf(ID), source.replace("reward-mode=\"manual\"", "reward-mode=\"automatic\""));
                    QuestRepository.reload();
                    var original = p.position();
                    var world = p.serverLevel();
                    var obstruct = BlockPos.containing(original);
                    var before = world.getBlockState(obstruct);
                    enter(p);
                    QuestStructures.complete(p);
                    QuestRuntime.evaluate(p);
                    require(!QuestProgressData.get(p.server).entry(p.getUUID(), QuestRepository.find(ID)).claimed(),
                            "Automatic reward claimed before inventory restoration");
                    world.setBlock(obstruct, Blocks.STONE.defaultBlockState(), 3);
                    QuestStructures.leave(p);
                    require(world.noCollision(p.getBoundingBox()), "Returned into an obstructed original position");
                    world.setBlock(obstruct, before, 3);
                    QuestRuntime.evaluate(p);
                    require(QuestProgressData.get(p.server).entry(p.getUUID(), QuestRepository.find(ID)).claimed()
                            && p.getInventory().countItem(Items.EMERALD) == 6, "Automatic reward lost after restore");
                });
            }
            default -> { return true; }
        }
        stage++;
        return false;
    }
    private static void setup(ServerPlayer p) throws Exception {
        Files.createDirectories(QuestConfig.root().resolve("structures"));
        CompoundTag data = new CompoundTag();
        ListTag size = new ListTag(); for (int i = 0; i < 3; i++) size.add(IntTag.valueOf(4)); data.put("size", size);
        ListTag palette = new ListTag(); var stone = new CompoundTag(); stone.putString("Name", "minecraft:stone"); palette.add(stone);
        data.put("palette", palette);
        ListTag blocks = new ListTag(); var block = new CompoundTag(); var pos = new ListTag();
        for (int i = 0; i < 3; i++) pos.add(IntTag.valueOf(0));
        block.put("pos", pos); block.putInt("state", 0); blocks.add(block); data.put("blocks", blocks);
        data.put("entities", new ListTag());
        NbtIo.writeCompressed(data, QuestStructures.path(FILE));
        named = new ItemStack(Items.STICK);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Component-specific equipment"));
        var settings = new QuestSceneSettings("task", "view", "return", null,
                List.of(new QuestSceneSettings.Slot(4, QuestItemStackValue.encode(named, p.registryAccess()), 2)),
                List.of(), List.of());
        Files.writeString(QuestStructures.path(FILE + ".scene.json"), QuestSceneSettings.JSON.toJson(settings));
        var control = new QuestPresentation.Control("enter", "structure_enter", FILE, 0, 0, 100, 20, "", Map.of(), TriggerGraph.empty());
        String source = "<quest id=\"" + ID + "\" name=\"Scene test\" reward-mode=\"manual\">"
                + "<conditions><condition type=\"confirm\"/></conditions>"
                + "<rewards><reward type=\"item\" item=\"minecraft:emerald\" count=\"3\"/></rewards></quest>";
        source = QuestPresentationDocuments.write(source, new QuestPresentation(true, 395, 275, List.of(control)));
        Files.createDirectories(QuestRepository.root());
        Files.writeString(QuestRepository.root().resolve(ID + ".html"), source);
        Files.writeString(QuestRepository.root().resolve(ID + "_other.html"),
                "<quest id=\"" + ID + "_other\" type=\"guide\" name=\"Unrelated\"/>");
        QuestRepository.reload();
        require(QuestRepository.find(ID) != null, "Scene task fixture failed: " + QuestRepository.errors());
        p.getInventory().clearContent(); p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        boolean rejected = false;
        try { QuestSceneSettings.parse("{\"targetTPS\":5}"); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Unsupported engine setting silently accepted");
    }
    private static void enter(ServerPlayer p) throws Exception {
        var quest = QuestRepository.find(ID);
        QuestStructures.enter(p, quest, quest.presentation().controls().getFirst());
    }
    private static void capture(Minecraft game, String name) throws Exception {
        var output = Path.of("..", "build", "verification"); Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(output.resolve(name + ".png")); }
    }
}
