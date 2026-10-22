package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class ClaimReceiptRegression {
    private static final String ID = "claim_receipt_" + System.currentTimeMillis();
    private static int stage;
    private static int stageWait;
    private static QuestScreen journal;
    private static CompletableFuture<Void> setup;

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> setup = CompletableFuture.runAsync(() -> {
                var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                for (int i = 0; i < 2; i++) {
                    require(QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                            ID + i, "Bulk claim " + i, "", "", "", "guide", "", 1, "", "",
                            i == 0 ? "minecraft:diamond" : "minecraft:emerald", i == 0 ? 3 : 128,
                            "manual")).successful(), "Cannot create bulk fixture");
                    QuestRuntime.activate(player, ID + i);
                }
                for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                }
                player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 61));
                player.containerMenu.broadcastChanges();
            }, game.getSingleplayerServer());
            case 1 -> {
                if (!setup.isDone() || ClientQuestProgress.view(ID + 1) == null) return false;
                setup.join();
                ClientPreferences.get().reducedMotion = true;
                if (!game.options.languageCode.equals("zh_cn")) {
                    game.getLanguageManager().setSelected("zh_cn");
                    game.options.languageCode = "zh_cn";
                    setup = game.reloadResourcePacks().thenApply(ignored -> null);
                    return false;
                }
                journal = QuestScreen.task(null, ID + 0);
                game.setScreen(journal);
                invoke("runClaimAll", new Class<?>[0]);
            }
            case 2 -> {
                if (!(game.screen instanceof QuestRewardPopupScreen)) {
                    if (++stageWait > 180) {
                        require(false, "Bulk receipt not opened; screen="
                                + (game.screen == null ? "null" : game.screen.getClass().getSimpleName())
                                + ", action=" + field(journal, "actionMessage")
                                + ", pending=" + field(journal, "actionPending")
                                + ", first=" + ClientQuestProgress.view(ID + 0).status()
                                + ", second=" + ClientQuestProgress.view(ID + 1).status());
                    }
                    return false;
                }
                stageWait = 0;
                require(ClientQuestProgress.view(ID + 0).status().equals("claimed"), "Bulk claim was rejected");
                require(ClientQuestProgress.view(ID + 1).status().equals("complete"), "Full inventory claim succeeded");
                require("partially_claimed".equals(field(journal, "actionMessage")), "Partial result not reported");
                @SuppressWarnings("unchecked") var rewards = (List<Reward>) field(game.screen, "rewards");
                require(rewards.contains(new Reward("item", "minecraft:diamond", 3)),
                        "Delivered bulk reward absent from receipt");
                require(!rewards.contains(new Reward("item", "minecraft:emerald", 128)),
                        "Failed bulk reward included in receipt");
                require(game.player.getInventory().countItem(Items.DIAMOND) == 64, "Bulk delivery count");
                game.screen.onClose();
            }
            case 3 -> {
                require(game.screen == journal, "Bulk receipt did not close");
                invoke("runAction", new Class<?>[]{String.class, boolean.class}, ID + 1, true);
            }
            case 4 -> {
                if (game.screen == journal) return false;
                require(game.screen.getClass().getSimpleName().equals("QuestConfirmScreen"),
                        "Inventory failure displayed receipt instead of confirmation");
                require(button(game, "overflow-confirm").getMessage().getString().equals("领取奖励"),
                        "Overflow confirmation lost its reward-specific label");
                button(game, "overflow-cancel").onPress();
            }
            case 5 -> {
                require(game.screen == journal, "Canceling overflow revealed receipt");
                require(field(journal, "pendingClaim") == null, "Canceled overflow kept a receipt request");
                invoke("runAction", new Class<?>[]{String.class, boolean.class}, ID + 1, true);
            }
            case 6 -> {
                if (game.screen == journal) return false;
                button(game, "overflow-confirm").onPress();
            }
            case 7 -> {
                if (!(game.screen instanceof QuestRewardPopupScreen)) return false;
                require(ClientQuestProgress.view(ID + 1).status().equals("claimed"), "Overflow claim not delivered");
                require(field(game.screen, "rewards").equals(List.of(new Reward("item", "minecraft:emerald", 128))),
                        "Overflow receipt lost reward data");
                game.screen.onClose();
            }
            case 8 -> {
                require(game.screen == journal, "Overflow receipt did not close");
                journal.tick();
                require(game.screen == journal && field(journal, "pendingClaim") == null,
                        "Overflow receipt replayed");
                game.setScreen(null);
                return true;
            }
            default -> throw new AssertionError("Unexpected claim stage");
        }
        stage++;
        return false;
    }

    private static StyledButton button(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(String name, Class<?>[] parameters, Object... args) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        method.invoke(journal, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (claim receipt stage " + stage + ")");
    }
}
