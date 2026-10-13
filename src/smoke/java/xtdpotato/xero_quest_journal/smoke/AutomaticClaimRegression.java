package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;
import xtdpotato.xero_quest_journal.client.QuestRewardPopupScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.ClientQuestProgress;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.util.concurrent.CompletableFuture;
import java.util.List;

final class AutomaticClaimRegression {
    private static final String ID = "automatic_claim_" + System.currentTimeMillis();
    private static CompletableFuture<Void> pending;
    private static Screen originalScreen;
    private static QuestScreen journal;
    private static int stage;
    private static int waiting;

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> {
                originalScreen = game.screen;
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    player.getInventory().clearContent();
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            ID, "Silent automatic reward", "", "", "", "guide", "", 1, "", "",
                            "minecraft:diamond", 2, "automatic")).successful(), "Cannot create automatic task");
                    try {
                        String source = QuestRepository.source(ID);
                        String silent = xtdpotato.xero_quest_journal.core.io.QuestDocuments.completionPolicy(source,
                                new xtdpotato.xero_quest_journal.core.model.CompletionPolicy(
                                        false, false, false, false, false));
                        require(QuestAuthoring.editSource(player, ID, QuestRepository.fingerprint(source), silent)
                                .successful(), "Cannot disable automatic reward popup");
                    } catch (java.io.IOException failure) {
                        throw new IllegalStateException(failure);
                    }
                    require(QuestRuntime.activate(player, ID).equals("completed"), "Automatic task did not complete");
                    require(QuestProgressData.get(player.getServer()).entry(player.getUUID(),
                            QuestRepository.find(ID)).claimed(), "Automatic task did not claim on server");
                    require(player.getInventory().countItem(Items.DIAMOND) == 2, "Server did not grant automatic reward");
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone()) return false;
                pending.join();
                require(game.screen == originalScreen, "Automatic delivery opened a receipt");
                if (game.player.getInventory().countItem(Items.DIAMOND) != 2
                        || ClientQuestProgress.view(ID) == null
                        || !ClientQuestProgress.view(ID).status().equals("claimed")) {
                    require(++waiting < 20, "Automatic delivery did not synchronize");
                    return false;
                }
                waiting = 0;
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestRuntime.reset(player, ID).equals("reset"), "Automatic task reset failed");
                    require(QuestProgressData.get(player.getServer()).entry(player.getUUID(),
                            QuestRepository.find(ID)) == null, "Reset kept completed entry");
                    require(QuestRuntime.activate(player, ID).equals("completed"), "Reset task did not complete");
                    require(QuestProgressData.get(player.getServer()).entry(player.getUUID(),
                            QuestRepository.find(ID)).claimed(), "Reset task did not auto claim");
                }, game.getSingleplayerServer());
            }
            case 2 -> {
                if (!pending.isDone()) return false;
                pending.join();
                require(game.screen == originalScreen, "Reset automatic delivery opened a receipt");
                if (game.player.getInventory().countItem(Items.DIAMOND) != 4) {
                    require(++waiting < 20, "Reset automatic reward missing");
                    return false;
                }
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            ID + "_target", "Unlock target", "", "", "", "guide", "", 1)).successful(),
                            "Cannot create unlock target");
                    for (int i = 0; i < 2; i++) {
                        var rewards = new java.util.ArrayList<>(List.of(
                                new Reward("command", "tag @s add silent_claim_test_" + i, 1),
                                new Reward("unlock", ID + "_target", 1)));
                        if (i == 1) rewards.add(new Reward("item", "minecraft:emerald", 1));
                        String id = ID + "_actions_" + i;
                        require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                                id, "Action reward " + i, "", "", "", "guide", "", 1, "", "", "", 1,
                                "manual", "", null, null, List.of(new Trigger("confirm", "", 1)), rewards))
                                .successful(), "Cannot create action reward task");
                        require(QuestRuntime.activate(player, id).equals("completed"),
                                "Action reward task did not complete");
                    }
                }, game.getSingleplayerServer());
            }
            case 3 -> {
                if (!pending.isDone() || ClientQuestProgress.view(ID + "_actions_1") == null) return false;
                pending.join();
                journal = QuestScreen.task(originalScreen, ID + "_actions_0");
                game.setScreen(journal);
                claim(ID + "_actions_0");
            }
            case 4 -> {
                require(game.screen == journal, "Action-only claim opened a receipt");
                if (!ClientQuestProgress.view(ID + "_actions_0").status().equals("claimed")
                        || field(journal, "actionPending") != null) return false;
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    require(player.getTags().contains("silent_claim_test_0"), "Silent command reward was not executed");
                    require(QuestProgressData.get(player.getServer()).unlocked(player.getUUID(),
                            QuestRepository.find(ID + "_target")), "Silent unlock reward was not executed");
                }, game.getSingleplayerServer());
                claim(ID + "_actions_1");
            }
            case 5 -> {
                if (!pending.isDone()) return false;
                pending.join();
                if (!(game.screen instanceof QuestRewardPopupScreen)) {
                    require(++waiting < 20, "Mixed receipt missing: " + field(journal, "actionMessage"));
                    return false;
                }
                require(field(game.screen, "rewards").equals(List.of(new Reward("item", "minecraft:emerald", 1))),
                        "Mixed receipt exposed command/unlock rewards");
                require(game.player.getInventory().countItem(Items.EMERALD) == 1, "Mixed item reward was not delivered");
                game.setScreen(originalScreen);
                return true;
            }
            default -> throw new AssertionError("Unexpected automatic claim stage");
        }
        stage++;
        return false;
    }

    private static void claim(String id) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod("runAction", String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(journal, id, true);
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (automatic claim stage " + stage + ")");
    }
}
