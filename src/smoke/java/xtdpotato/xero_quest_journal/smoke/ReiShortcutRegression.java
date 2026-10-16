package xtdpotato.xero_quest_journal.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.client.MailboxScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.platform.PlatformServices;

/** Reflection keeps the ordinary regression classpath free of the optional REI API. */
final class ReiShortcutRegression {
    private static int phase, wait, attempts;
    private static Object task, mail;
    private static Class<?> favorite;
    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (phase) {
            case 0 -> {
                var typeClass = Class.forName("me.shedaniel.rei.api.client.favorites.FavoriteEntryType");
                var registryClass = Class.forName("me.shedaniel.rei.api.client.favorites.FavoriteEntryType$Registry");
                Object registry = typeClass.getMethod("registry").invoke(null);
                Object type = registryClass.getMethod("get", ResourceLocation.class).invoke(registry,
                        ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "inventory_shortcut"));
                if (type == null) {
                    require(++attempts < 200, "REI did not discover the Xero favorite plugin");
                    return false;
                }
                task = ((com.mojang.serialization.DataResult<?>) typeClass.getMethod("fromArgs", Object[].class)
                        .invoke(type, (Object) new Object[]{"task"})).result().orElseThrow();
                mail = ((com.mojang.serialization.DataResult<?>) typeClass.getMethod("fromArgs", Object[].class)
                        .invoke(type, (Object) new Object[]{"mail"})).result().orElseThrow();
                favorite = Class.forName("me.shedaniel.rei.api.client.favorites.FavoriteEntry");
                require(!(boolean) favorite.getMethod("isInvalid").invoke(task), "Task favorite is invalid");
                require(favorite.getMethod("getRenderer", boolean.class).invoke(task, false) != null, "Favorite icon renderer missing");
                var tag = (CompoundTag) favorite.getMethod("save", CompoundTag.class).invoke(task, new CompoundTag());
                Object restored = ((com.mojang.serialization.DataResult<?>) favorite.getMethod("readResult", CompoundTag.class)
                        .invoke(null, tag)).result().orElseThrow();
                require(restored.equals(task) && !restored.equals(mail), "Favorite type/identity did not survive NBT round-trip");
                var managerType = Class.forName("me.shedaniel.rei.impl.client.gui.widget.favorites.FavoritesEntriesManager");
                var manager = managerType.getField("INSTANCE").get(null);
                var shortcuts = Class.forName("xtdpotato.xero_quest_journal.client.QuestReiShortcuts");
                shortcuts.getMethod("set", String.class, boolean.class).invoke(null, "task", false);
                require(!(boolean) shortcuts.getMethod("contains", String.class).invoke(null, "task"),
                        "REI settings could not remove the task favorite");
                shortcuts.getMethod("set", String.class, boolean.class).invoke(null, "task", true);
                require((boolean) shortcuts.getMethod("contains", String.class).invoke(null, "task"),
                        "REI settings could not add the task favorite");
                managerType.getMethod("remove", favorite).invoke(manager, mail);
                require(!((List<?>) managerType.getMethod("getFavorites").invoke(manager)).contains(mail),
                        "REI's native remove operation did not hide the mail shortcut");
                managerType.getMethod("add", favorite).invoke(manager, mail);
                require(((List<?>) managerType.getMethod("getFavorites").invoke(manager)).contains(task)
                                && ((List<?>) managerType.getMethod("getFavorites").invoke(manager)).contains(mail),
                        "REI shortcut add/remove did not restore both entries");
                Path reiConfig = PlatformServices.configDir().resolve("roughlyenoughitems/favorites.json5");
                require(Files.isRegularFile(reiConfig) && Files.readString(reiConfig).contains("inventory_shortcut"),
                        "REI did not persist the shortcut configuration");
                game.setScreen(new InventoryScreen(game.player));
            }
            case 1 -> {
                require(game.screen instanceof InventoryScreen, "REI fixture lost the inventory parent");
                require(game.screen.children().stream().filter(StyledButton.class::isInstance)
                                .map(StyledButton.class::cast)
                                .noneMatch(button -> List.of("inventory-entry", "mailbox-inventory-entry")
                                        .contains(button.styleId())),
                        "Legacy book/paper inventory buttons overlap the REI favorite entries");
                capture(game, "rei-inventory-shortcuts-zh-1920");
                require((boolean) favorite.getMethod("doAction", int.class).invoke(task, 0) && game.screen instanceof QuestScreen,
                        "Task favorite did not open the journal");
            }
            case 2 -> {
                require((boolean) favorite.getMethod("doAction", int.class).invoke(mail, 0) && game.screen instanceof MailboxScreen,
                        "Mail favorite did not open the mailbox");
                Files.writeString(Path.of("..", "build", "verification", "rei-shortcut-result.txt"),
                        "PASS: actual REI plugin discovery, item-like renderers, favorite NBT identity, native removal/re-add without duplicates, persisted REI configuration, task and mail click actions.");
                return true;
            }
            default -> throw new AssertionError("Unknown REI shortcut phase " + phase);
        }
        phase++;
        wait = 12;
        return false;
    }
    private static void capture(Minecraft game, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            require(image.getWidth() == 1920 && image.getHeight() == 1080, "REI screenshot is not full HD");
            image.writeToFile(Path.of("..", "build", "verification", name + ".png"));
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
