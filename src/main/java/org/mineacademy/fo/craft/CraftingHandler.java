package org.mineacademy.fo.craft;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.plugin.SimplePlugin;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import static org.mineacademy.fo.MinecraftVersion.atLeast;

/**
 * The manager of custom crafting recipes.
 *
 * @author Rubix327
 */
public final class CraftingHandler {

    /**
     * The list of all registered custom crafts.<br>
     * Changing this list will not bring anything - it is needed only for informative purposes
     * (so a coder can get the list of the registered crafts).
     */
    @Getter
    private static final List<SimpleCraft<? extends Recipe>> crafts = new ArrayList<>();

    /**
     * Register a custom craft.
     * If not forced, this will only register the craft if it is enabled.
     * @param craft the craft
     * @param force if true - the craft will be registered regardless of {@link SimpleCraft#isEnabled()}
     * @return true if the craft was added, false if it wasn't for some reason
     */
    public static <T extends Recipe> boolean register(SimpleCraft<T> craft, boolean force){
        if (!force && !craft.isEnabled()) return false;
        if (craft.isRegistered()) return false;

        T recipe;

        // If this craft is not supported on the current server version then skip it.
        try{
            recipe = createRecipe(craft);
        } catch (IllegalArgumentException e){
            Bukkit.getLogger().warning("[" + SimplePlugin.getNamed() + "] Recipe type from custom craft '" +
                        craft.getKey().getKey() + "' is not supported on your server version.");
            return false;
        }

        crafts.add(craft);
        craft.recipe = recipe;
        craft.modifyRecipe();

        return Bukkit.addRecipe(craft.getRecipe());
    }

    /**
     * Register a custom craft. Only registers a craft if it is enabled.
     * @param craft the craft
     * @return true if the craft was added, false if it wasn't for some reason
     */
    public static <T extends Recipe> boolean register(SimpleCraft<T> craft){
        return register(craft, false);
    }

    /**
     * Remove a custom craft (recipe) from the server.
     * <br>
     * <b>Note that removing a recipe may cause permanent loss of data
     * associated with that recipe (eg whether it has been discovered by
     * players).</b>
     *
     * @param craft the craft to remove.
     * @return true if the craft was removed
     */
    public static <T extends Recipe> boolean unregister(SimpleCraft<T> craft){
        crafts.remove(craft);
        return Bukkit.removeRecipe(craft.getKey());
    }

    /**
     * Discover a craft for this player such that it has not already been discovered.
     * This method will add the key's associated craft to the player's recipe book.
     * @param craft the craft
     * @param player the player
     * @return whether the craft was newly discovered
     */
    public static <T extends Recipe> boolean discover(SimpleCraft<T> craft, Player player){
        return player.discoverRecipe(craft.getKey());
    }

    /**
     * Undiscover a craft for this player such that it has already been discovered.
     * This method will remove the key's associated recipe from the player's recipe book.
     * @param craft the craft
     * @param player the player
     * @return whether the recipe was successfully undiscovered
     */
    public static <T extends Recipe> boolean undiscover(SimpleCraft<T> craft, Player player){
        return player.undiscoverRecipe(craft.getKey());
    }

    /**
     * Check whether this player has discovered the craft.
     * @param craft the craft
     * @param player the player
     * @return true if discovered, false otherwise
     */
    public static <T extends Recipe> boolean isDiscovered(SimpleCraft<T> craft, Player player){
        return player.hasDiscoveredRecipe(craft.getKey());
    }

    /**
     * Create a recipe instance depending on the craft's generic type.
     * @param craft the craft
     * @return the new instance of the recipe type
     * @throws IllegalArgumentException if the recipe type is not supported on the current server version,
     * or it is not supported within Foundation
     */
    @SuppressWarnings("unchecked")
    private static <T> T createRecipe(SimpleCraft<? extends Recipe> craft){
        Class<T> clazz = ((Class<T>) ((ParameterizedType) craft.getClass().getGenericSuperclass()).getActualTypeArguments()[0]);

        NamespacedKey namespacedKey = craft.getKey();
        ItemStack result = craft.getResult();
        Material source = Material.STONE;
        Valid.checkNotNull(result, "Crafting ItemStack result in " + craft.getClass() + " cannot be null.");

        if (clazz == ShapedRecipe.class){
            return (T) new ShapedRecipe(namespacedKey, result);
        }
        if (clazz == ShapelessRecipe.class){
            return (T) new ShapelessRecipe(namespacedKey, result);
        }
        if (clazz == FurnaceRecipe.class){
            return (T) new FurnaceRecipe(namespacedKey, result, source, 0, 200);
        }
        if (clazz == MerchantRecipe.class && atLeast(V.v1_9)){
            return (T) new MerchantRecipe(result, 100);
        }
        if (atLeast(V.v1_14)){
            if (clazz == BlastingRecipe.class){
                return (T) new BlastingRecipe(namespacedKey, result, source, 0f, 200);
            }
            else if (clazz == CampfireRecipe.class){
                return (T) new CampfireRecipe(namespacedKey, result, source, 0f, 200);
            }
            else if (clazz == SmokingRecipe.class){
                return (T) new SmokingRecipe(namespacedKey, result, source, 0f, 200);
            }
            else if (clazz == StonecuttingRecipe.class){
                return (T) new StonecuttingRecipe(namespacedKey, result, source);
            }
        }
        if (clazz == SmithingRecipe.class && atLeast(V.v1_16)){
            RecipeChoice choice = new RecipeChoice.MaterialChoice(Material.DIAMOND_SWORD);
            return (T) new SmithingRecipe(namespacedKey, result, choice, choice);
        }
        throw new IllegalArgumentException("Recipe type is not supported.");
    }

    /**
     * Check if list contains recipe.<br>
     * For all recipes it checks for equality of the result item.<br>
     * Additionally, for the keyed recipes it checks for equality of their NamespacedKeys.<br>
     * Additionally, for MerchantRecipe it checks for the ingredients to be the same.
     * @param recipes the list containing recipes
     * @param recipe the recipe to be checked for belonging to the list
     * @return true if the recipe is in the list
     * @throws IllegalArgumentException if recipes list is null or recipe is null
     */
    public static <T extends Recipe> boolean listContainsRecipe(@NotNull List<T> recipes, @NotNull T recipe){
        Valid.checkNotNull(recipes, "Recipes list must not be null.");
        Valid.checkNotNull(recipe, "Recipe must not be null.");

        for (Recipe rec : recipes){
            if (!rec.getResult().isSimilar(recipe.getResult())) return false;

            if (recipe instanceof Keyed){
                if (!(rec instanceof Keyed)) continue;
                if (((Keyed) recipe).getKey().equals(((Keyed) rec).getKey())) return true;
            }

            if (recipe instanceof MerchantRecipe){
                if (!(rec instanceof MerchantRecipe)) continue;

                MerchantRecipe r1 = (MerchantRecipe) recipe;
                MerchantRecipe r2 = (MerchantRecipe) rec;

                if (r1.getIngredients().size() != r2.getIngredients().size()) return false;

                int size = r1.getIngredients().size();
                for (int i = 0; i < size; i++) {
                    if (!r1.getIngredients().get(i).isSimilar(r2.getIngredients().get(i))) return false;
                }
                return true;
            }
        }
        return false;
    }

    public static class Merchant{

        /**
         * Check if the villager has the given recipe.
         * @param villager the villager
         * @param recipe the recipe
         * @return true if the villager has the recipe
         * @throws IllegalArgumentException if villager is null or recipe is null
         */
        public static boolean hasRecipe(Villager villager, MerchantRecipe recipe){
            Valid.checkNotNull(villager, "Villager must not be null.");

            return listContainsRecipe(villager.getRecipes(), recipe);
        }

        /**
         * Add a new recipe to the villager.
         * @param villager the villager
         * @param recipe the recipe
         * @throws IllegalArgumentException if villager is null or recipe is null
         */
        public static void addRecipe(Villager villager, MerchantRecipe recipe){
            Valid.checkNotNull(villager, "Villager must not be null.");
            Valid.checkNotNull(recipe, "Recipe must not be null.");

            List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
            recipes.add(recipe);
            villager.setRecipes(recipes);
        }
    }

}
