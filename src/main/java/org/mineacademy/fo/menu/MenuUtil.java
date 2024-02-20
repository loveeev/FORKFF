package org.mineacademy.fo.menu;

import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompMaterial;

/**
 * Utility class containing some useful features for menus.
 */
public class MenuUtil {

    public static ItemStack defaultPreviousPageButtonItem = ItemCreator.of(CompMaterial.SPECTRAL_ARROW, "&7Previous page").make();
    public static ItemStack defaultNextPageButtonItem = ItemCreator.of(CompMaterial.TIPPED_ARROW, "&7Next page").make();
    public static ItemStack defaultBackItem = ItemCreator.of(CompMaterial.OAK_TRAPDOOR, "&7Go back").make();
    public static ItemStack defaultRefreshItem = ItemCreator.of(CompMaterial.REDSTONE, "&7Refresh menu").make();
    public static ItemStack defaultInfoItem = ItemCreator.of(CompMaterial.BOOK).make();
    public static ItemStack defaultMenuItem = ItemCreator.of(CompMaterial.APPLE).make();
    public static CompMaterial defaultWrapperMaterial = CompMaterial.GRAY_STAINED_GLASS_PANE;
    public static ItemStack defaultWrapperItem = ItemCreator.of(defaultWrapperMaterial).name(" ").make();

}