package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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

    static boolean openTag(String tag, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return false;
        var game = Minecraft.getInstance();
        var previous = game.screen;
        for (String viewer : List.of("emi", "roughlyenoughitems", "jei")) {
            if (!PlatformServices.isModLoaded(viewer)) continue;
            try {
                switch (viewer) {
                    case "emi" -> openEmiTag(items);
                    case "roughlyenoughitems" -> openReiTag(items);
                    case "jei" -> openJeiTag(items);
                    default -> throw new IllegalStateException(viewer);
                }
                if (game.screen != previous) return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                XeroQuestJournal.LOGGER.warn("Could not open tag {} in {}", tag, viewer, failure);
                if (game.screen != previous) game.setScreen(previous);
            }
        }
        return false;
    }

    private static void openEmiTag(List<ItemStack> items) throws ReflectiveOperationException {
        Class<?> emiStack = Class.forName("dev.emi.emi.api.stack.EmiStack");
        var entries = new ArrayList<Object>(items.size());
        for (ItemStack stack : items) entries.add(emiStack.getMethod("of", ItemStack.class)
                .invoke(null, stack.copyWithCount(1)));
        Class<?> ingredientType = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
        Object ingredient = collectionFactory(ingredientType, entries);
        Class.forName("dev.emi.emi.api.EmiApi")
                .getMethod("displayRecipes", ingredientType).invoke(null, ingredient);
    }

    private static void openReiTag(List<ItemStack> items) throws ReflectiveOperationException {
        Class<?> entryStacks = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
        var entries = new ArrayList<Object>(items.size());
        for (ItemStack stack : items) entries.add(entryStacks.getMethod("of", ItemStack.class)
                .invoke(null, stack.copyWithCount(1)));
        Class<?> ingredientType = Class.forName("me.shedaniel.rei.api.common.entry.EntryIngredient");
        Object ingredient = collectionFactory(ingredientType, entries);
        Class<?> builderType = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
        Object builder = builderType.getMethod("builder").invoke(null);
        Method add = java.util.Arrays.stream(builderType.getMethods())
                .filter(method -> method.getName().equals("addRecipesFor")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(ingredient))
                .findFirst().orElseThrow(NoSuchMethodException::new);
        add.invoke(builder, ingredient);
        builderType.getMethod("open").invoke(builder);
    }

    private static void openJeiTag(List<ItemStack> items) throws ReflectiveOperationException {
        Object runtime = Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
        var runtimeType = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
        Object helpers = runtimeType.getMethod("getJeiHelpers").invoke(runtime);
        Object factory = Class.forName("mezz.jei.api.helpers.IJeiHelpers")
                .getMethod("getFocusFactory").invoke(helpers);
        var roleType = Class.forName("mezz.jei.api.recipe.RecipeIngredientRole");
        Object input = roleType.getField("INPUT").get(null);
        Object itemType = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
        var focusFactory = Class.forName("mezz.jei.api.recipe.IFocusFactory")
                .getMethod("createFocus", roleType,
                        Class.forName("mezz.jei.api.ingredients.IIngredientType"), Object.class);
        var focuses = new ArrayList<Object>(items.size());
        for (ItemStack stack : items)
            focuses.add(focusFactory.invoke(factory, input, itemType, stack.copyWithCount(1)));
        Object gui = runtimeType.getMethod("getRecipesGui").invoke(runtime);
        Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("show", List.class).invoke(gui, focuses);
    }

    private static Object collectionFactory(Class<?> type, List<?> values) throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !method.getName().equals("of")
                    || method.getParameterCount() != 1) continue;
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter.isInstance(values) || parameter.isAssignableFrom(List.class))
                return method.invoke(null, values);
        }
        for (Method method : type.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !method.getName().equals("of")
                    || !method.isVarArgs() || method.getParameterCount() != 1) continue;
            Class<?> component = method.getParameterTypes()[0].getComponentType();
            Object array = java.lang.reflect.Array.newInstance(component, values.size());
            for (int index = 0; index < values.size(); index++)
                java.lang.reflect.Array.set(array, index, values.get(index));
            return method.invoke(null, array);
        }
        throw new NoSuchMethodException(type.getName() + ".of(Collection)");
    }
}
