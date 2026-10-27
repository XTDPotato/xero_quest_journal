package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.compat.CarriedInventoryCompat;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Uses real optional backpack storage; the no-mod run separately checks impossible authoring. */
final class CarriedInventoryRegression {
    private static final String TASK = "holding_capacity_fixture";
    private static final boolean SIMPLE = Boolean.getBoolean("xero.simpleBackpackTest");
    private static final boolean BACKPACK = Boolean.getBoolean("xero.backpackIntegrationTest") || SIMPLE;
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final QuestDefinition.Trigger HOLD = new QuestDefinition.Trigger("item_hold", "minecraft:stick", 10000);
    private static CompletableFuture<Void> setup, language;
    private static int stage, wait;
    private static boolean saved;

    static boolean tick(Minecraft game) throws Exception {
        MailArrivalToast.clear(); QuestUnlockToast.clear(); XeroToast.clear(); game.getToasts().clear();
        if (--wait > 0) return false;
        wait = 8;
        switch (stage) {
            case 0 -> {
                game.options.guiScale().set(2);
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920); game.getWindow().setHeight(1080); game.resizeDisplay();
                ClientPreferences.get().reducedMotion = true;
                game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                language = game.reloadResourcePacks();
                setup = CompletableFuture.runAsync(() -> {
                    try { verifyServer(game); } catch (Exception failure) { throw new RuntimeException(failure); }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!setup.isDone() || !language.isDone()) return false;
                setup.join(); language.join();
                game.setScreen(new QuestConditionsScreen(null, TASK, List.of(HOLD), ignored -> saved = true));
            }
            case 2 -> {
                if (game.screen instanceof AnimatedQuestScreen screen && screen.inputBlocked()) return false;
                var save = button(game, "conditions-save");
                require(save.active == BACKPACK, "Condition editor save state does not reflect inventory feasibility");
                capture(game, BACKPACK ? "holding-backpack-hint-zh-1920" : "holding-capacity-impossible-zh-1920");
                save.onPress();
                require(saved == BACKPACK, "Condition editor accepted an impossible draft or rejected backpack capacity");
                if (BACKPACK) {
                    writeReport();
                    return true;
                }
                game.setScreen(new QuestConditionsScreen(null, TASK,
                        List.of(new QuestDefinition.Trigger("item_acquire", "minecraft:stick", 10000)),
                        ignored -> saved = true));
            }
            case 3 -> {
                require(button(game, "conditions-save").active, "Cumulative acquisition was incorrectly capacity-limited");
                button(game, "conditions-save").onPress();
                require(saved, "Cumulative acquisition draft could not be saved");
                writeReport();
                return true;
            }
            default -> throw new AssertionError("Unexpected carried-inventory test stage");
        }
        stage++;
        return false;
    }

    private static void verifyServer(Minecraft game) throws Exception {
        var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
        player.getInventory().clearContent();
        player.getInventory().setItem(40, new ItemStack(Items.STICK, 16));
        player.getInventory().setChanged();
        var hint = QuestHoldingCapacity.hint(player, HOLD);
        require(hint != null && hint.ordinaryCapacity() == 2368 && hint.impossible() != BACKPACK,
                "Holding capacity failed to use 36 main slots, offhand and the actual stack size");
        var unstackable = QuestHoldingCapacity.hint(player,
                new QuestDefinition.Trigger("item_hold", "minecraft:iron_sword", 38));
        require(unstackable != null && unstackable.ordinaryCapacity() == 37, "Unstackable item capacity is incorrect");
        player.getInventory().setItem(0, new ItemStack(Items.STICK, 16));
        var ordinaryHold = new QuestDefinition("ordinary_hold_fixture", "普通背包持有测试", "", "world", "", true,
                false, 0, null, "", "", "", "ordinary-hold-fixture", List.of(),
                List.of(new QuestDefinition.Trigger("item_hold", "minecraft:stick", 32)), List.of(), "manual");
        require(QuestCarriedInventory.count(player, "minecraft:stick") == 32
                        && QuestConditions.progress(player, ordinaryHold).current() == 32
                        && QuestConditions.plan(player, ordinaryHold, true).satisfied(),
                "Ordinary inventory plus offhand failed to satisfy a hold objective");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        require(!QuestHoldingCapacity.impossible(player, List.of(new QuestDefinition.Trigger("any", "", 1,
                        List.of(HOLD, new QuestDefinition.Trigger("confirm", "", 1))))),
                "An impossible optional branch blocked a completable condition");
        require(QuestHoldingCapacity.impossible(player, List.of(
                        new QuestDefinition.Trigger("item_submit", "minecraft:stick", 10000))),
                "Submission falsely promises to consume inside backpack storage");
        var submission = new QuestDefinition("submission_fixture", "提交测试", "", "world", "", true,
                false, 0, null, "", "", "", "submission-fixture", List.of(),
                List.of(new QuestDefinition.Trigger("item_submit", "minecraft:stick", 10000)),
                List.of(), "manual");
        require(!QuestConditions.plan(player, submission, true).satisfied(),
                "Backpack contents incorrectly satisfied a submission condition");
        Files.writeString(QuestRepository.root().resolve(TASK + ".json"), new Gson().toJson(Map.of(
                "id", TASK, "title", "背包容量检查", "type", "world", "triggers", List.of(HOLD),
                "rewards", List.of(), "rewardMode", "manual")));
        QuestRepository.reload();
        var task = QuestRepository.find(TASK);
        require(task != null, "Legacy impossible definition could not load for repair");
        if (!BACKPACK) {
            require(!CarriedInventoryCompat.installed(), "No-mod test unexpectedly has a backpack provider");
            var create = QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                    "holding_impossible_new", "Impossible", "", "", "", "world", "minecraft:stick", 10000));
            require(!create.successful() && create.error().endsWith(".capacity"),
                    "Server allowed creating an impossible holding requirement");
            var edit = QuestAuthoring.editConditions(player, TASK,
                    QuestRepository.fingerprint(QuestRepository.source(TASK)), List.of(HOLD));
            require(!edit.successful() && edit.error().endsWith(".capacity"),
                    "Server allowed editing a task to an impossible holding requirement");
            require(QuestCarriedInventory.count(player, "minecraft:stick") == 16,
                    "Vanilla offhand holding count is incorrect");
            return;
        }
        require(xtdpotato.xero_quest_journal.platform.PlatformServices.isModLoaded(
                        SIMPLE ? "simplybackpacks" : "sophisticatedbackpacks"),
                "Backpack integration run did not load its real backpack mod");
        ItemStack first = backpack(100), second = backpack(57);
        player.getInventory().setItem(0, first);
        player.getInventory().setItem(1, second);
        player.getInventory().setItem(2, first.copy());
        player.getInventory().setChanged();
        QuestCarriedInventory.invalidate(player);
        require(QuestCarriedInventory.count(player, "minecraft:stick") == 10064,
                "Real backpack contents were missing or a duplicate storage UUID was counted twice");
        require(QuestConditions.progress(player, task).current() == 10000 && QuestConditions.plan(player, task, true).satisfied(),
                "Real holding condition did not complete above ordinary inventory capacity");
        require(!QuestConditions.plan(player, submission, true).satisfied(),
                "Backpack items were included in the submission consumption plan");
        require(QuestCarriedInventory.count(player, "minecraft:stick") == 10064,
                "Holding check consumed backpack contents");
        player.getInventory().setItem(0, ItemStack.EMPTY); player.getInventory().setItem(1, ItemStack.EMPTY);
        player.getInventory().setItem(2, ItemStack.EMPTY); player.getInventory().setChanged();
        require(!QuestConditions.plan(player, task, true).satisfied()
                        && QuestCarriedInventory.count(player, "minecraft:stick") == 16,
                "Removed backpacks remained counted as held items");
        player.getInventory().setItem(0, first);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, second);
        player.getInventory().setChanged();
        require(QuestConditions.plan(player, task, true).satisfied(),
                "Equipped backpack contents were not counted");
        require(QuestConditions.progress(player, task).current() == 10000,
                "Configured backpack contents did not flow into the quest progress snapshot");
        for (int hit = 0; hit < 10; hit++) CarriedInventoryCompat.equipped(player);
        require(QuestCarriedInventory.count(player, "minecraft:stick") == 10064,
                "Repeated holding checks changed backpack storage contents");
    }

    private static ItemStack backpack(int filledSlots) throws Exception {
        if (SIMPLE) {
            var id = ResourceLocation.fromNamespaceAndPath("simplybackpacks", "ultimatebackpack");
            require(BuiltInRegistries.ITEM.containsKey(id), "Real Simply Backpacks item is missing");
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            var type = Class.forName("com.flanks255.simplybackpacks.items.BackpackItem");
            Object data = type.getMethod("getData", ItemStack.class).invoke(null, stack);
            Object handler = data.getClass().getMethod("getHandler").invoke(data);
            var setter = handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
            for (int slot = 0; slot < filledSlots; slot++) setter.invoke(handler, slot, new ItemStack(Items.STICK, 64));
            return stack;
        }
        var id = ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "netherite_backpack");
        require(BuiltInRegistries.ITEM.containsKey(id), "Real netherite backpack item is missing");
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        var type = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
        Object wrapper = type.getMethod("fromStack", ItemStack.class).invoke(null, stack);
        wrapper.getClass().getMethod("setSlotNumbers", int.class, int.class).invoke(wrapper, 144, 0);
        wrapper.getClass().getMethod("setContentsUuid", UUID.class).invoke(wrapper, UUID.randomUUID());
        Object handler = wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
        var setter = handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
        for (int slot = 0; slot < filledSlots; slot++) setter.invoke(handler, slot, new ItemStack(Items.STICK, 64));
        return stack;
    }

    private static StyledButton button(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }
    private static void writeReport() throws Exception {
        String text = BACKPACK
                ? "PASS: real Sophisticated Backpacks contents above vanilla capacity; duplicate UUID counted once; offhand and equipped storage; holding never consumes; removed bags stop counting; authoring backpack hint.\n"
                : "PASS: no-mod capacity calculation; unstackable limit; impossible create/edit blocked; optional branches remain completable; editor save blocked; acquisition remains valid.\n";
        if (SIMPLE) text = text.replace("Sophisticated Backpacks", "Simply Backpacks");
        Files.writeString(OUTPUT.resolve(SIMPLE ? "carried-simple-backpack-result.txt"
                : BACKPACK ? "carried-backpack-result.txt" : "carried-capacity-result.txt"), text);
        Files.writeString(OUTPUT.resolve("result.txt"), text);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
