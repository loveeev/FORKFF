package org.mineacademy.fo.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.*;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.menu.tool.Tool;
import org.mineacademy.fo.remain.CompMaterial;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Basic advanced menu.
 * Supports adding items, buttons and filling locked slots with wrapper item.
 * Also contains some ready buttons (Menu, ReturnBack, Refresh, etc.).<br><br>
 * To get started, override {@link #setup} method and customize your menu inside it.
 *
 * @author Rubix327
 */
public abstract class AdvancedMenu extends Menu {

    /**
     * The player watching the menu.
     */
    private final Player player;
    /**
     * Contains buttons and their slots.
     */
    private final TreeMap<Integer, Button> buttons = new TreeMap<>();
    /**
     * Custom slots and their itemStacks.<br>
     * You can define them in {@link #addItem} method.
     */
    private final TreeMap<Integer, ItemStack> items = new TreeMap<>();
    /**
     * In AdvancedMenu, locked slots are filled with {@link #getWrapperItem()}.<br>
     * In AdvancedMenuPaginated, <i>elementsItems</i> are not displayed on these slots and slots
     * are filled with {@link #getWrapperItem()}.
     */
    private List<Integer> lockedSlots = new ArrayList<>();
    /**
     * Convenient item that you can use to close some menu slots.<br>
     * By default, displays on empty locked slots in AdvancedMenuPagged.<br>
     * Default item is gray stained-glass.
     * Set this item to whether you want by {@link #setWrapper}.
     * To disable item set it to null.
     */
    private ItemStack wrapperItem = MenuUtil.defaultWrapperItem;

    public AdvancedMenu(Player player){
        this.player = player;
    }

    /**
     * Add buttons and items from {@link #setup()} and update menu elements for paged menus.
     * @return built instance of the menu
     */
    public AdvancedMenu build(){
        setup();
        return this;
    }

    /**
     * Display this menu to the player given in the constructor.
     */
    public final void display(){
        build().displayTo(getPlayer());
    }

    /**
     * Add button to the menu.<br>
     * <b>Use it only inside {@link #setup} method to avoid errors!</b><br>
     * @param slot the slot the button should be displayed on
     * @param btn the button
     */
    protected final void addButton(Integer slot, Button btn){
        // Remove item from the same slot from lockedSlots and items, because buttons have higher priority
        lockedSlots.remove(slot);
        items.remove(slot);

        buttons.put(slot, btn);
    }

    /**
     * Add custom item with no behavior to the menu.<br>
     * <b>Use it only inside {@link #setup} method to avoid errors!</b><br>
     * If you want item to have behavior use {@link #addButton}.
     * @param slot the slot the item should be placed on
     * @param item the item
     */
    protected final void addItem(Integer slot, ItemStack item){
        // Remove the same slot from lockedSlots, because items have higher priority
        lockedSlots.remove(slot);

        items.put(slot, item);
    }

    /**
     * Redraw the menu without moving the cursor to the center.
     */
    protected void refreshMenu(){
        redraw();
    }

    /**
     * Get a player watching the menu.
     */
    public final Player getPlayer(){
        return this.player;
    }

    /**
     * Get the {@link #items}
     */
    protected final TreeMap<Integer, ItemStack> getItems(){
        return items;
    }

    /**
     * Get the {@link #buttons}
     */
    protected final TreeMap<Integer, Button> getButtons(){
        return buttons;
    }

    /**
     * Get the {@link #lockedSlots}
     */
    protected final List<Integer> getLockedSlots(){
        return lockedSlots;
    }

    /**
     * For {@link AdvancedMenu}, set the slots that should be filled with {@link #wrapperItem}.
     * For {@link AdvancedMenuPagged}, set the slots the main elements should NOT be placed on.
     */
    protected final void setLockedSlots(Integer... slots){
        lockedSlots = Arrays.stream(slots).filter(e -> e >= 0).filter(e -> e < getSize()).collect(Collectors.toList());
    }

    /**
     * See {@link #setLockedSlots(Integer...)} for the detailed description.<br><br>
     * Shapes available: {@link MenuSlots.SizedShape}.
     */
    protected final void setLockedSlots(MenuSlots.SizedShape sizedShape){
        setLockedSlots(sizedShape.getSlots());
    }

    /**
     * Set the automated locked slots depending on the shape and the menu size.<br>
     * Shapes available: {@link MenuSlots.Shape}.
     * @param shape the shape you want to use
     */
    protected final void setLockedSlots(MenuSlots.Shape shape){
        setLockedSlots(shape, getSize());
    }

    /**
     * Set the automated locked slots depending on the shape and the given size.<br>
     * Shapes available: {@link MenuSlots.Shape}.
     * @param shape the shape you want to use
     * @param size the size of the menu
     */
    protected final void setLockedSlots(MenuSlots.Shape shape, int size){
        setLockedSlots(MenuSlots.AUTO(shape, size));
    }

    /**
     * For {@link AdvancedMenu}, set the slots that should NOT be filled with {@link #wrapperItem}.<br>
     * For {@link AdvancedMenuPagged}, set the slots the main elements can only be placed on.
     * Note that all unspecified slots are locked.
     */
    @SuppressWarnings("BoxingBoxedValue")
    protected final void setUnlockedSlots(Integer... slots){
        List<Integer> slotsList = Arrays.stream(slots).collect(Collectors.toList());
        setLockedSlots(IntStream.rangeClosed(0, getSize() - 1).filter(i -> !slotsList.contains(i)).boxed().toArray(Integer[]::new));
    }

    /**
     * Get the {@link #wrapperItem}
     */
    protected final ItemStack getWrapperItem(){
        return wrapperItem;
    }

    /**
     * Set the material of {@link #wrapperItem}
     */
    protected final void setWrapper(CompMaterial material){
        wrapperItem = ItemCreator.of(material, "").make();
    }

    /**
     * Set the item of {@link #wrapperItem}
     */
    protected final void setWrapper(ItemStack item){
        wrapperItem = item;
    }

    protected final Button getBackButton(){
        return getBackButton(MenuUtil.defaultBackItem);
    }

    protected final Button getBackButton(ItemStack item){
        return new Button() {
            @Override
            public void onClickedInMenu(Player player, AdvancedMenu menu, ClickType click) {
                AdvancedMenu previous = getPreviousMenu(player);
                if (previous != null){
                    previous.display();
                }
            }

            @Override
            public ItemStack getItem() {
                return item;
            }
        };
    }

    /**
     * Does the same as {@link #getRefreshButton(ItemStack)}.
     * Uses the default button from {@link MenuUtil#defaultRefreshItem}.
     */
    protected final Button getRefreshButton(){
        return getRefreshButton(MenuUtil.defaultRefreshItem);
    }

    /**
     * Get the button that refreshes the menu.<br>
     * If the given item is null, it will get its item from {@link MenuUtil#defaultRefreshItem}.<br>
     * This button does not imply any additional behavior.
     * @return the refresh button
     */
    protected final Button getRefreshButton(@NotNull ItemStack item){
        return new Button() {
            @Override
            public void onClickedInMenu(Player player, AdvancedMenu menu, ClickType click) {
                SoundUtil.Play.POP(player);
                refreshMenu();
            }

            @Override
            public ItemStack getItem() {
                return item;
            }
        };
    }

    /**
     * <b>Attention! Please use {@link #getMenuButton(Class)} if possible because it is much more efficient!
     * Use this method only if your menu constructor is custom or the menu must be created by instance
     * (and not by class).</b><br><br>
     * Does the same as {@link #getMenuButton(AdvancedMenu, ItemStack)}.
     * Uses the default button from {@link MenuUtil#defaultMenuItem}.
     */
    protected final Button getMenuButton(AdvancedMenu to){
        return getMenuButton(to, MenuUtil.defaultMenuItem);
    }

    /**
     * <b>Attention! Please use {@link #getMenuButton(Class, ItemStack)} if possible because it is much more efficient!
     * Use this method only if your menu constructor is custom or the menu must be created by instance
     * (and not by class).</b><br><br>
     * Create a new button which opens a given menu instance.
     * @param item how the button should look like
     * @param to what menu the player should be sent to
     * @return the button
     */
    protected final Button getMenuButton(AdvancedMenu to, ItemStack item){
        return new Button() {
            @Override
            public void onClickedInMenu(Player player, AdvancedMenu menu, ClickType click) {
                to.display();
            }

            @Override
            public ItemStack getItem() {
                return item;
            }
        };
    }

    /**
     * Does the same as {@link #getMenuButton(Class, ItemStack)}.
     * Uses default item from {@link MenuUtil#defaultMenuItem}.
     */
    protected final Button getMenuButton(Class<? extends AdvancedMenu> to){
        return getMenuButton(to, MenuUtil.defaultMenuItem);
    }

    /**
     * Create a new button which opens a new menu instance created from a given class.
     * @param item how the button should look like
     * @param to what menu the player should be sent to
     * @return the button
     */
    protected final Button getMenuButton(Class<? extends AdvancedMenu> to, ItemStack item){
        return new Button() {
            @Override
            public void onClickedInMenu(Player player, AdvancedMenu menu, ClickType click) {
                newInstanceOf(to, player).display();
            }

            @Override
            public ItemStack getItem() {
                return item;
            }
        };
    }

    /**
     * Make the button from the tool. It gives player one piece of this tool.<br>
     * This button gets its additional lore depending on if player has the tool
     * in the inventory from {@link #getAlreadyHaveLore()} and {@link #getClickToGetLore()}
     * so you can override them to set your custom items and messages.<br>
     * @param tool the tool we should give
     * @return the button
     */
    protected final Button getToolButton(Tool tool){
        return new Button() {
            @Override
            public void onClickedInMenu(Player player, AdvancedMenu menu, ClickType click) {
                if (tool.isConsumable()){
                    SoundUtil.Play.POP_HIGH(player);
                    player.getInventory().addItem(tool.getItem());
                }
                else{
                    if (!player.getInventory().containsAtLeast(tool.getItem(), 1)){
                        SoundUtil.Play.POP_HIGH(player);
                        player.getInventory().addItem(tool.getItem());
                    }
                    else{
                        SoundUtil.Play.POP_LOW(player);
                        player.getInventory().removeItem(tool.getItem());
                    }
                }
                refreshMenu();
            }

            @Override
            public ItemStack getItem() {
                List<String> lore;
                boolean isConsumable = tool.isConsumable();
                boolean hasTool = hasItem(player, tool);

                if (isConsumable){
                    lore = getClickToGetLore();
                }
                else{
                    lore = hasTool ? getAlreadyHaveLore() : getClickToGetLore();
                }

                return ItemCreator.of(tool.getItem()).lore(lore).glow(!isConsumable && hasTool).make();
            }
        };
    }

    /**
     * Checks if the player has the specified tool in the inventory.
     */
    private boolean hasItem(Player player, Tool tool){
        return player.getInventory().containsAtLeast(tool.getItem(), 1);
    }

    /**
     * Get the additional item lore of the tool if the player already has this tool in the inventory.
     */
    protected List<String> getAlreadyHaveLore(){
        return Arrays.asList("", "&cYou already have this item!");
    }

    /**
     * Get the additional item lore of the tool if player does not have this tool in the inventory yet.
     */
    protected List<String> getClickToGetLore(){
        return Arrays.asList("", "&2Click to get this item");
    }

    /**
     * Does the same as {@link #getInfoButton(ItemStack)}.
     * Uses the default button from {@link MenuUtil#defaultInfoItem}.
     */
    protected final Button getInfoButton(){
        return getInfoButton(MenuUtil.defaultInfoItem);
    }

    /**
     * Get the button that shows info about the menu.
     * By default, does nothing when clicked, but you can override it and add your behavior.
     * This button gets its info from {@link #getInfoName()} and {@link #getInfoLore()}.
     * So you can override them and set your custom name and lore.
     * @return the button
     */
    protected final Button getInfoButton(ItemStack item){
        return Button.makeDummy(ItemCreator.of(item).name(getInfoName())
                .lore(getInfoLore()).hideTags(true));
    }

    /**
     * Get the name of the button that shows info about the menu.<br>
     * Override it to set your own name.
     * @see #getInfoButton(ItemStack)
     */
    protected String getInfoName(){
        return "&f" + ItemUtil.bountifyCapitalized(getTitle()) + " Menu Information";
    }

    /**
     * Get the lore of the button that shows info about the menu.<br>
     * Override it to set your own lore (info).
     * @see #getInfoButton(ItemStack)
     */
    protected List<String> getInfoLore() {
        return Arrays.asList("",
                "&7Override &fgetInfoName() &7and &fgetInfoLore()",
                "&7in " + getClass().getSimpleName() + " &7to set your own menu description.");
    }

    @Override
    protected void onMenuClick(Player player, int slot, InventoryAction action, ClickType click, ItemStack cursor, ItemStack clicked, boolean cancelled) {
        if (getButtons().containsKey(slot)){
            getButtons().get(slot).onClickedInMenu(player, this, click);
        }
    }

    /**
     * Override this method and customize your menu here.
     * This method is automatically started just before displaying a menu to a player.
     */
    protected abstract void setup();

    protected final boolean isButton(int slot){
        return getButtons().containsKey(slot);
    }

    protected final boolean isItem(int slot){
        return getItems().containsKey(slot);
    }

    protected final boolean isLockedSlot(int slot){
        return getLockedSlots().contains(slot);
    }

    @Override
    public ItemStack getItemAt(int slot) {
        if (isButton(slot)){
            return getButtons().get(slot).getItem();
        }
        if (isItem(slot)){
            return getItems().get(slot);
        }
        if (isLockedSlot(slot)){
            return getWrapperItem();
        }
        return null;
    }

    /**
     * Close the menu for the player who is viewing it.
     */
    public final void closeMenu(){
        if (isViewing(getPlayer())){
            getPlayer().closeInventory();
        }
    }

    @Override
    public AdvancedMenu newInstance() {
        return newInstanceOf(this.getClass(), this.player);
    }

    /**
     * Create a new instance of the menu from the given class.
     */
    public static AdvancedMenu newInstanceOf(Class<? extends AdvancedMenu> menu, Player player){
        try{
            return menu.getDeclaredConstructor(Player.class).newInstance(player);
        }
        catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e){
            Logger.infoFramed("Could not create a new instance of " + menu.getName() + " class." +
                    "\r\n Please create a constructor with only Player argument" +
                    "\r\n or override 'public AdvancedMenu newInstance' method in your class.");
            throw new NullPointerException("Could not create a new instance of " + menu.getName() + " class.");
        }
    }

    /**
     * Returns the current menu for player
     *
     * @param player the player
     * @return the menu, or null if none
     */
    public static AdvancedMenu getMenu(final Player player) {
        return getMenu0(player, FoConstants.NBT.TAG_MENU_CURRENT);
    }

    /**
     * Returns the previous menu for player
     *
     * @param player the player
     * @return the menu, or none
     */
    public static AdvancedMenu getPreviousMenu(final Player player) {
        return getMenu0(player, FoConstants.NBT.TAG_MENU_PREVIOUS);
    }

    /**
     * Returns the last closed menu, null if it exists.
     */
    @Nullable
    public static AdvancedMenu getLastClosedMenu(final Player player) {
        if (player.hasMetadata(FoConstants.NBT.TAG_MENU_LAST_CLOSED)) {
            return (AdvancedMenu) player.getMetadata(FoConstants.NBT.TAG_MENU_LAST_CLOSED).get(0).value();
        }

        return null;
    }

    /**
     * Returns the menu associated with the player's metadata, or null
     */
    private static AdvancedMenu getMenu0(final Player player, final String tag) {
        if (player.hasMetadata(tag)) {
            final AdvancedMenu menu = (AdvancedMenu) player.getMetadata(tag).get(0).value();
            Valid.checkNotNull(menu, "Menu missing from " + player.getName() + "'s metadata '" + tag + "' tag!");

            return menu;
        }

        return null;
    }

    @Override
    protected final void finalize() throws Throwable {
        super.finalize();
    }

    /**
     * Send a message to the {@link #getPlayer()}
     */
    public final void tell(Object... messages) {
        Common.tell(this.player, Arrays.toString(messages).replace("[", "").replace("]", ""));
    }

    /**
     * Send an information message to the {@link #getPlayer()}
     */
    public final void tellInfo(Object message) {
        Messenger.info(this.player, message.toString());
    }

    /**
     * Send a success message to the {@link #getPlayer()}
     */
    public final void tellSuccess(Object message) {
        Messenger.success(this.player, message.toString());
    }

    /**
     * Send a warning message to the {@link #getPlayer()}
     */
    public final void tellWarn(Object message) {
        Messenger.warn(this.player, message.toString());
    }

    /**
     * Send an error message to the {@link #getPlayer()}
     */
    public final void tellError(Object message) {
        Messenger.error(this.player, message.toString());
    }

    /**
     * Send a question message to the {@link #getPlayer()}
     */
    public final void tellQuestion(Object message) {
        Messenger.question(this.player, message.toString());
    }

    /**
     * Send an announcement message to the {@link #getPlayer()}
     */
    public final void tellAnnounce(Object message) {
        Messenger.announce(this.player, message.toString());
    }
}