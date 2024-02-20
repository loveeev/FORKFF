package org.mineacademy.fo.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompMaterial;

import java.util.function.Consumer;

/**
 * This menu is intended to be used as an intermediate state when
 * something is being loaded, e.g. data from a database.
 * <br><br>
 * <b>Attention:</b><br>
 * The menu is active indefinitely long until it is closed manually
 * (menu.{@link #closeMenu()}) or another menu is opened.
 * <br><br>
 * To change the default materials, override {@link #getMaterials()}<br>
 * To change the refresh rate, override {@link #getRefreshPeriod()}<br>
 * To change the title and size, use constructor {@link #LoadingMenu(Player, Consumer, String, int)}
 * <br><br>
 * Here is an example of using this menu with a database call:
 * <pre>
 * new DataLoadingMenu(player, loadingMenu ->
 *     DataManager.getDropService().getAll(new SimpleDatabaseManager.Callback<List<DropItem>>() {

 *         public void onSuccess(List<DropItem> items) {
 *             // Here the LoadingMenu is closed automatically because a new menu is opened
 *             new SetupDropsMenu(player, items);
 *         }
 *
 *         public void onFail(Throwable t) {
 *             // Here you should close the menu manually
 *             loadingMenu.closeMenu();
 *             tell("Could not load drop items from the database.");
 *         }
 *     })
 * ).display();
 * </pre>
 */
public class LoadingMenu extends AdvancedMenu {

    private final Consumer<AdvancedMenu> consumer;
    private BukkitTask task;

    public LoadingMenu(Player player, Consumer<AdvancedMenu> consumer) {
        this(player, consumer, "Loading...", 9*3);
    }

    public LoadingMenu(Player player, Consumer<AdvancedMenu> consumer, String title, int size){
        super(player);
        this.consumer = consumer;
        setTitle(title);
        setSize(size);
    }

    @Override
    protected void setup() {
        consumer.accept(this);

        task = Common.runTimer(0, getRefreshPeriod(), () -> {
            for (int i = 0; i < getSize(); i++) {
                addItem(i, ItemCreator.of(RandomUtil.nextItem(getMaterials())).name(getTitle()).make());
            }
            refreshMenu();
        });
    }

    protected int getRefreshPeriod(){
        return 3;
    }

    protected CompMaterial[] getMaterials(){
        return new CompMaterial[]{
                CompMaterial.WHITE_STAINED_GLASS_PANE,
                CompMaterial.ORANGE_STAINED_GLASS_PANE,
                CompMaterial.MAGENTA_STAINED_GLASS_PANE,
                CompMaterial.LIGHT_BLUE_STAINED_GLASS_PANE,
                CompMaterial.YELLOW_STAINED_GLASS_PANE,
                CompMaterial.LIME_STAINED_GLASS_PANE,
                CompMaterial.PINK_STAINED_GLASS_PANE,
                CompMaterial.CYAN_STAINED_GLASS_PANE,
                CompMaterial.PURPLE_STAINED_GLASS_PANE,
                CompMaterial.BLUE_STAINED_GLASS_PANE,
                CompMaterial.BROWN_STAINED_GLASS_PANE,
                CompMaterial.GREEN_STAINED_GLASS_PANE,
                CompMaterial.RED_STAINED_GLASS_PANE
        };
    }

    @Override
    protected void onMenuClose(Player player, Inventory inventory) {
        task.cancel();
    }

}
