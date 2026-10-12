package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.util.List;

/** Optional integrations stay unlinked when their recipe viewer is not installed. */
final class RecipeViewerBridge {
    private RecipeViewerBridge() {}

    static String availableViewer() {
        if (PlatformServices.isModLoaded("emi")) return "EMI";
        if (PlatformServices.isModLoaded("roughlyenoughitems")) return "REI";
        if (PlatformServices.isModLoaded("jei")) return "JEI";
        return "";
    }

    static boolean openRecipes(ItemStack ingredient) {
        if (ingredient.isEmpty()) return false;
        var game = Minecraft.getInstance();
        var previous = game.screen;
        for (String viewer : List.of("emi", "roughlyenoughitems", "jei")) {
            if (!PlatformServices.isModLoaded(viewer)) continue;
            try {
                ItemStack stack = ingredient.copyWithCount(1);
                switch (viewer) {
                    case "emi" -> {
                        var emiStack = Class.forName("dev.emi.emi.api.stack.EmiStack");
                        Object entry = emiStack.getMethod("of", ItemStack.class).invoke(null, stack);
                        Class.forName("dev.emi.emi.api.EmiApi")
                                .getMethod("displayRecipes", Class.forName("dev.emi.emi.api.stack.EmiIngredient"))
                                .invoke(null, entry);
                    }
                    case "roughlyenoughitems" -> {
                        Object entry = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks")
                                .getMethod("of", ItemStack.class).invoke(null, stack);
                        var builderType = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
                        Object builder = builderType.getMethod("builder").invoke(null);
                        builderType.getMethod("addRecipesFor",
                                Class.forName("me.shedaniel.rei.api.common.entry.EntryStack")).invoke(builder, entry);
                        builderType.getMethod("open").invoke(builder);
                    }
                    case "jei" -> {
                        // JEI's runtime accessor is isolated here; all operations use its public API interfaces.
                        Object runtime = Class.forName("mezz.jei.common.Internal")
                                .getMethod("getJeiRuntime").invoke(null);
                        var runtimeType = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
                        Object helpers = runtimeType.getMethod("getJeiHelpers").invoke(runtime);
                        Object factory = Class.forName("mezz.jei.api.helpers.IJeiHelpers")
                                .getMethod("getFocusFactory").invoke(helpers);
                        var roleType = Class.forName("mezz.jei.api.recipe.RecipeIngredientRole");
                        Object output = roleType.getField("OUTPUT").get(null);
                        Object itemType = Class.forName("mezz.jei.api.constants.VanillaTypes")
                                .getField("ITEM_STACK").get(null);
                        Object focus = Class.forName("mezz.jei.api.recipe.IFocusFactory")
                                .getMethod("createFocus", roleType,
                                        Class.forName("mezz.jei.api.ingredients.IIngredientType"), Object.class)
                                .invoke(factory, output, itemType, stack);
                        Object gui = runtimeType.getMethod("getRecipesGui").invoke(runtime);
                        Class.forName("mezz.jei.api.runtime.IRecipesGui")
                                .getMethod("show", List.class).invoke(gui, List.of(focus));
                    }
                    default -> throw new IllegalStateException(viewer);
                }
                if (game.screen != previous) return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                XeroQuestJournal.LOGGER.warn("Could not open {} recipes for {}", viewer, ingredient, failure);
                if (game.screen != previous) game.setScreen(previous);
            }
        }
        return false;
    }
}
