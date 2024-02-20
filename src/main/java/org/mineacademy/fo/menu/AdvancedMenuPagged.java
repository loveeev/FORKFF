package org.mineacademy.fo.menu;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.SoundUtil;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompSound;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Menu with pages.<br>
 * Supports previous and next buttons.<br><br>
 * To get started, override {@link #setup} method and customize your menu inside it.
 *
 * @author Rubix327
 */
public abstract class AdvancedMenuPagged<T> extends AdvancedMenu {
    /**
     * Slots and their raw {@link #getElements()}.
     */
    private final TreeMap<Integer, T> elementsSlots = new TreeMap<>();
    @Getter(AccessLevel.PRIVATE)
    private Integer previousButtonSlot;
    @Getter(AccessLevel.PRIVATE)
    private Integer nextButtonSlot;
    /**
     * Position at which the item setter is now.
     */
    private int currentSlot = 0;
    /**
     * Current page opened in the player's menu.
     */
    private int currentPage = 1;
    /**
     * Defines if the previous and next buttons are displayed even if the menu has only one page.
     * False by default.
     */
    private boolean pageButtonsAlwaysEnabled = false;
    /**
     * The ItemStack that the previous button should have.<br>
     * Default: {@link MenuUtil#defaultPreviousPageButtonItem}.
     */
    @Setter
    private ItemStack previousButtonItem = MenuUtil.defaultPreviousPageButtonItem;
    /**
     * The ItemStack that the next button should have.<br>
     * Default: {@link MenuUtil#defaultNextPageButtonItem}.
     */
    @Setter
    private ItemStack nextButtonItem = MenuUtil.defaultNextPageButtonItem;

    public AdvancedMenuPagged(Player player){
        super(player);
    }

    public final AdvancedMenuPagged<T> build(){
        setup();
        initButtonsSlots();
        updateElements();
        return this;
    }

    /**
     * Get elements and their correspondent slots.
     */
    protected final TreeMap<Integer, T> getElementsSlots(){
        return elementsSlots;
    }

    /**
     * Update paged menu elements.<br>
     * It automatically runs when the menu is opened.
     */
    private void updateElements(){
        addPageButtons(getPreviousButtonSlot(), getNextButtonSlot());
        setElementsSlots();
    }

    protected final void setPageButtonsAlwaysEnabled(boolean isEnabled){
        pageButtonsAlwaysEnabled = isEnabled;
    }

    protected final void initButtonsSlots(){
        if (previousButtonSlot == null){
            this.previousButtonSlot = getSize() - 9;
        }
        if (nextButtonSlot == null){
            this.nextButtonSlot = getSize() - 1;
        }
    }

    protected final void setPreviousButtonSlot(int slot){
        this.previousButtonSlot = slot;
    }

    protected final void setNextButtonSlot(int slot){
        this.nextButtonSlot = slot;
    }

    protected final ItemStack getPreviousButtonItem(){
        return previousButtonItem;
    }

    protected final ItemStack getNextButtonItem(){
        return nextButtonItem;
    }

    /**
     * Add previous and next buttons with specified slots.<br>
     * Default slots are left bottom corner and right bottom corner for previous and next buttons correspondingly.<br>
     * By default, buttons are only displayed when there is more than one page
     * or {@link #pageButtonsAlwaysEnabled} is set to true.
     */
    private void addPageButtons(int prevSlot, int nextSlot){
        if (getMaxPage() > 1 || pageButtonsAlwaysEnabled){
            addButton(prevSlot, formPrevNextButton(getPreviousButtonItem(), false));
            addButton(nextSlot, formPrevNextButton(getNextButtonItem(), true));
        }
    }

    private Button formPrevNextButton(ItemStack item, boolean isNextButton){
        return new Button() {
            @Override
            public void onClickedInMenu(Player player, AdvancedMenu menu, ClickType click) {
                if (isNextButton){
                    if (currentPage >= getMaxPage()) {
                        CompSound.VILLAGER_NO.play(getViewer());
                        return;
                    }
                    currentPage += 1;
                    SoundUtil.Play.CLICK_HIGH(player);
                }
                else{
                    if (currentPage <= 1) {
                        CompSound.VILLAGER_NO.play(getViewer());
                        return;
                    }
                    currentPage -= 1;
                    SoundUtil.Play.CLICK_LOW(player);
                }
                redraw();
            }

            @Override
            public ItemStack getItem() {
                return item;
            }
        };
    }

    /**
     * Get the amount of unlocked slots.
     */
    private int getAvailableSlotsSize(){
        return getSize() - getLockedSlots().size() - getButtons().size() - getItems().size();
    }

    /**
     * Get the number of pages that can be in the menu considering amount of elements and available slots.
     */
    public final int getMaxPage(){
        float a = (float) this.getElements().size() / getAvailableSlotsSize();
        return (this.getElements().size() % 2 != 0 || (int)a == 0 ? (int)a + 1 : (int)a);
    }

    /**
     * Set the slots for the elements.<br>
     * If the slot is already taken by a custom item or another button,
     * or it is locked then this slot will not be used.
     */
    private void setElementsSlots(){
        currentSlot = 0;
        elementsSlots.clear();
        for (T element : getElements()){
            putElementOnFreeSlot(element);
        }
    }

    /**
     * Put the element on the first found free slot.
     */
    private void putElementOnFreeSlot(T element){
        int slot = currentSlot % getSize();
        for (int i = 0; i < 1; i++){
            if (getButtons().containsKey(slot)) continue;
            if (getItems().containsKey(slot)) continue;
            if (getLockedSlots().contains(slot)) continue;
            if (getPreviousButtonSlot() == slot) continue;
            if (getNextButtonSlot() == slot) continue;
            if (getElementsSlots().containsKey(this.currentSlot)) continue;

            elementsSlots.put(currentSlot, element);
            currentSlot++;
            return;
        }

        currentSlot++;
        putElementOnFreeSlot(element);
    }

    /**
     * Redraw the menu without moving the cursor to the center.
     */
    @Override
    protected final void refreshMenu(){
        updateElements();
        redraw();
    }

    protected final boolean isElement(int slot){
        return elementsSlots.containsKey(slot);
    }

    /**
     * Display items on their slots.
     * This method already has a good working implementation, so try not to override it.
     */
    @Override
    public ItemStack getItemAt(int slot){
        if (super.getItemAt(slot) != null){
            return super.getItemAt(slot);
        }

        slot = slot + (currentPage - 1) * getSize();
        if (elementsSlots.containsKey(slot)){
            return convertToItemStack(elementsSlots.get(slot));
        }
        return null;
    }

    @Override
    protected void onMenuClick(Player player, int slot, InventoryAction action, ClickType click, ItemStack cursor, ItemStack clicked, boolean cancelled) {
        if (clicked == null) return;
        if (CompMaterial.isAir(clicked.getType())) return;
        int pagedSlot = slot + (currentPage - 1) * getSize();
        if (isElement(pagedSlot)) {
            onElementClick(player, elementsSlots.get(pagedSlot), slot, click);
        }
        if (isButton(slot)){
            getButtons().get(slot).onClickedInMenu(player, this, click);
        }
    }

    /**
     * Actions that should be executed when player clicks on list element.
     * It works only on list elements.
     */
    protected void onElementClick(Player player, T object, int slot, ClickType clickType) {}

    /**
     * Convert the elements to itemStacks that should be displayed in the menu.
     */
    private List<ItemStack> convertToItemStacks(List<T> elements){
        return elements.stream().map(this::convertToItemStack).collect(Collectors.toList());
    }

    /**
     * Get elements that should be converted to itemStacks.
     */
    protected abstract List<T> getElements();

    /**
     * Convert each element to itemStack which should be displayed in the menu.
     */
    protected abstract ItemStack convertToItemStack(T element);
}
