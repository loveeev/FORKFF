package org.mineacademy.fo.craft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.plugin.SimplePlugin;

/**
 * SimpleCraft encapsulates a new custom recipe that can be added to a Bukkit server.
 * <br><br>
 * Where to start:
 * <ol>
 * <li>Create a new class extending SimpleCraft</li>
 * <li>Implement the required methods. You can override {@link #isEnabled()}
 * to enable the craft only under certain condition or get the value from the config.</li>
 * <li>Make this class a singleton:
 * <ul>
 *     <li>Make a private no-arguments constructor</li>
 *     <li>Make a static field equal to an instance of the class</li>
 * </ul>
 * </li>
 * <li>Register your custom craft with {@link #register()}
 * or @{@link AutoRegister} (the class must be final)
 * </li>
 * </ol>
 * @author Rubix327
 */
public abstract class SimpleCraft<T extends Recipe> {

    /**
     * The recipe related to this craft.
     */
    protected T recipe;

    /**
     * Get the recipe related to this craft.
     * @return the recipe
     */
    public final T getRecipe(){
        return this.recipe;
    }

    /**
     * Shows if this craft is registered on the server.
     * @return is the craft registered
     */
    public final boolean isRegistered(){
        return Bukkit.getRecipe(getKey()) != null;
    }

    /**
     * The key under which your custom recipe will be stored on the server.
     * You can leave it default, and we will handle it automatically.
     * @return the key
     */
    @NotNull
    public NamespacedKey getKey(){
        return new NamespacedKey(SimplePlugin.getInstance(), this.getClass().getSimpleName());
    }

    /**
     * Shows if this craft is enabled.<br>
     * If you register your custom craft with {@link CraftingHandler#register(SimpleCraft)}
     * without forcing then only enabled crafts will be registered.
     * @return is craft enabled
     */
    public boolean isEnabled(){
        return true;
    }

    /**
     * The resulting ItemStack of this craft.
     * @return the result stack
     */
    public abstract ItemStack getResult();

    /**
     * Here you can modify the recipe for this custom craft.
     */
    protected abstract void modifyRecipe();

    /**
     * Should we auto discover this recipe
     * (put it into the player's recipe book)
     * for all players when they join?
     * @return if true - the recipe is auto-discovered
     */
    public boolean isAutoDiscoverEnabled(){
        return false;
    }

    /**
     * Get the new MaterialChoice.<br>
     * This is the short version of {@link RecipeChoice.MaterialChoice}.
     * @param materials Materials that are suitable for this craft
     * @return new MaterialChoice
     */
    protected final RecipeChoice.MaterialChoice choice(Material... materials){
        return new RecipeChoice.MaterialChoice(materials);
    }

    /**
     * Get the new ExactChoice.<br>
     * This is the short version of {@link RecipeChoice.ExactChoice}.
     * @param itemStacks ItemStacks that are suitable for this craft
     * @return new MaterialChoice
     */
    protected final RecipeChoice.ExactChoice choice(ItemStack... itemStacks){
        return new RecipeChoice.ExactChoice(itemStacks);
    }

    @Override
    public String toString() {
        return "SimpleCraft{" +
                "recipe={" +
                    "class=" + recipe.getClass().getSimpleName() +
                    ", result=" + recipe.getResult().getType() +
                "}, isRegistered=" + isRegistered() +
                '}';
    }

    /* ****************************************************
     * Helper methods to get fast access to CraftingHandler
     * ****************************************************/

    /**
     * See {@link CraftingHandler#register(SimpleCraft)}
     */
    public final boolean register(){
        return CraftingHandler.register(this);
    }

    /**
     * See {@link CraftingHandler#unregister(SimpleCraft)}
     */
    public final boolean unregister(){
        return CraftingHandler.unregister(this);
    }

    /**
     * See {@link CraftingHandler#discover(SimpleCraft, Player)}
     */
    public final boolean discover(Player player){
        return CraftingHandler.discover(this, player);
    }

    /**
     * See {@link CraftingHandler#undiscover(SimpleCraft, Player)}
     */
    public final boolean undiscover(Player player) {
        return CraftingHandler.undiscover(this, player);
    }

    /**
     * See {@link CraftingHandler#isDiscovered(SimpleCraft, Player)}
     */
    public final boolean isDiscovered(Player player){
        return CraftingHandler.isDiscovered(this, player);
    }

}
