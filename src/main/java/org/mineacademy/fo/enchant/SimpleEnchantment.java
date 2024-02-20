package org.mineacademy.fo.enchant;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mineacademy.fo.*;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.remain.Remain;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static org.mineacademy.fo.enchant.EnchantStatus.*;

/**
 * Represents a simple way of getting your own enchantments into Minecraft
 * <br><br>
 * <b>DISCLAIMER:</b> Minecraft is not built for your custom enchants. Removing this enchantment
 * from the item later will still preserve the lore saying the item has it.
 * <br><br>
 * TIP: If you want to register for custom events you just make this class <code>final</code> and
 * <code>implements Listener</code> and we register it automatically!<br>
 * However, you must make sure that the event actually happened when the item was used.
 * Use {@link ItemStack#containsEnchantment(Enchantment)} to check.
 *
 * @author kangarko
 * @author Rubix327
 */
@SuppressWarnings("unused")
public abstract class SimpleEnchantment extends Enchantment {

	/**
	 * Pattern for matching namespaces
	 */
	private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9._-]+");
	/**
	 * The map containing all custom enchantments.
	 *
	 * @since 6.2.5.4
	 */
	private static final Map<String, SimpleEnchantment> customEnchants = new HashMap<>();

	/**
	 * The name of this enchant
	 */
	private final String name;

	/**
	 * The maximum level of this enchant
	 */
	private final int maxLevel;

	/**
	 * Create a new enchantment with the given name.
	 */
	protected SimpleEnchantment(String name, int maxLevel) {
		super(toKey(name));

		this.name = name;
		this.maxLevel = maxLevel;

		Remain.registerEnchantment(this);
		customEnchants.put(name.replace(" ", "_").toLowerCase(), this);
	}

	/**
	 * Convert the name of the enchantment to a key.
	 */
	private static NamespacedKey toKey(@NonNull String name) {
		if (!MinecraftVersion.atLeast(V.v1_13))
			throw new RuntimeException("SimpleEnchantment requires Minecraft 1.13.2 or greater. Cannot make " + name);

		name = name.toLowerCase().replace(" ", "_");
		name = ChatUtil.replaceDiacritic(name);

		Valid.checkBoolean(VALID_NAMESPACE.matcher(name).matches(), "Enchant name must only contain English alphabet names: " + name);
		return NamespacedKey.minecraft(name);
	}

	/**
	 * Try to find an enchantment by its name defined in constructor.
	 * @param name the name of the enchantment
	 * @return instance of SimpleEnchantment
	 * @since 6.2.5.4
	 * @author Rubix327
	 */
	@Nullable
	public static SimpleEnchantment findByName(@NonNull String name){
		return customEnchants.get(name.toLowerCase());
	}

	// ------------------------------------------------------------------------------------------
	// Events
	// ------------------------------------------------------------------------------------------

	/**
	 * Triggered automatically when the attacker has this enchantment
	 */
	protected void onDamage(int level, LivingEntity damager, EntityDamageByEntityEvent event) {
	}

	/**
	 * Triggered automatically when the player clicks block/air with the given enchant
	 */
	protected void onInteract(int level, PlayerInteractEvent event) {
	}

	/**
	 * Triggered automatically when the player breaks block having hand item with this enchantment
	 */
	protected void onBreakBlock(int level, BlockBreakEvent event) {
	}

	/**
	 * Triggered automatically when the projectile was shot from a living entity
	 * having this item at their hand
	 */
	protected void onShoot(int level, LivingEntity shooter, ProjectileLaunchEvent event) {
	}

	/**
	 * Triggered automatically when the projectile hits something and the shooter
	 * is a living entity having the hand item having this enchant
	 */
	protected void onHit(int level, LivingEntity shooter, ProjectileHitEvent event) {
	}

	// ------------------------------------------------------------------------------------------
	// Convenience methods
	// ------------------------------------------------------------------------------------------

	/**
	 * Gives this enchantment to the given item at a certain level taking into account
	 * all restrictions (for full list of restrictions see {@link #isEnchantmentAllowed(ItemStack, int)}).
	 *
	 * @param item the item
	 * @param level the level
	 * @return true if the enchantment was successfully applied, false otherwise
	 */
	public boolean applyTo(ItemStack item, int level) {
		return applyTo(item, level, false);
	}

	/**
	 * Gives this enchantment to the given item at a certain level, either ignoring restrictions
	 * or not (for full list of restrictions see {@link #isEnchantmentAllowed(ItemStack, int)}).
	 *
	 * @param item the item
	 * @param level the level
	 * @param ignoreRestrictions true = ignore all restrictions, false = do not ignore
	 * @return true if the enchantment was successfully applied, false if one of the
	 * restrictions has prevented enchanting
	 * @since 6.2.5.4
	 * @author Rubix327
	 */
	public boolean applyTo(ItemStack item, int level, boolean ignoreRestrictions){
		final ItemMeta meta = item.getItemMeta();
		assert meta != null;

		if (!ignoreRestrictions && isEnchantmentAllowed(item, level) != ALLOWED){
			return false;
		}

		applyLore(meta, level);

		meta.addEnchant(this, level, true);
		item.setItemMeta(meta);
		return true;
	}

	/**
	 * Check if the item can be enchanted taking into account
	 * all the restrictions:
	 * <ul>
	 *     <li>{@link #conflictsWith(Enchantment)}</li>
	 *     <li>{@link #getCustomItemTarget()}</li>
	 *     <li>{@link #enchantMaterial()}</li>
	 *     <li>{@link #enchantMaterials()}</li>
	 *     <li>{@link #canEnchantItem(ItemStack item)}</li>
	 *     <li>{@link #getMaxLevel()} < given level</li>
	 *     <li>check if the item already has this enchantment with this level</li>
	 * </ul>
	 *
	 * @param item the item
	 * @param level the level of the enchantment
	 * @return the {@link EnchantStatus}
	 * @since 6.2.5.4
	 * @author Rubix327
	 */
	public final EnchantStatus isEnchantmentAllowed(ItemStack item, int level){
		final ItemMeta meta = item.getItemMeta();
		assert meta != null;

		for (Enchantment ench : item.getEnchantments().keySet()){
			if (conflictsWith(ench)) return CONFLICT;
		}
		if (getCustomItemTarget() != null && !getCustomItemTarget().includes(item)) return NOT_IN_ITEM_TARGET;
		if (enchantMaterial() != null && item.getType() != enchantMaterial()) return NOT_IN_MATERIAL;
		if (enchantMaterial() != null && !enchantMaterials().contains(item.getType())){
			return NOT_IN_MATERIALS;
		}

		EnchantStatus status = canEnchantItem(item, level);
		if (status != ALLOWED) return status;

		if (level > getMaxLevel()) return LEVEL_TOO_HIGH;
		if (meta.hasEnchant(this) && meta.getEnchantLevel(this) == level) return ALREADY_ENCHANTED;

		return ALLOWED;
	}

	/**
	 * Apply the lore defined in {@link #getLore(int)} to the item if
	 * it does not have {@link ItemFlag#HIDE_ENCHANTS} applied.
	 *
	 * @param meta the ItemMeta of the item
	 * @param level the level of the enchantment
	 * @since 6.2.5.4
	 * @author Rubix327
	 */
	public void applyLore(ItemMeta meta, int level){
		if (!meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS) && getLore(level) != null){
			List<String> newLore;
			String newEnchant = getColoredLore(level);

			if (meta.hasLore()){
				assert meta.getLore() != null;
				newLore = new ArrayList<>(meta.getLore());

				if (meta.hasEnchant(this)){
					// If the item already have this enchantment, replace its level with a new one
					for (int i = 0; i < newLore.size(); i++){
						if (Common.stripColors(newLore.get(i)).startsWith(getName())){
							newLore.set(i, newEnchant);
						}
					}
				} else {
					// If the item does not have this enchantment yet, add it to the end of the lore
					newLore.add(newEnchant);
				}
			} else {
				// If the item does not have any enchantments, create a new list with only this enchantment
				newLore = new ArrayList<>(Collections.singleton(newEnchant));
			}
			meta.setLore(newLore);
		}
	}

	/**
	 * Is item enchanted with this enchantment
	 * @param item the item
	 * @return true if enchanted
	 */
	public final boolean isItemEnchanted(ItemStack item){
		return item.getItemMeta() != null && item.getItemMeta().hasEnchant(this);
	}

	/**
	 * Get the current level of this enchantment on the given item.
	 * @param item the item
	 * @return the level, or 0 if there's no such enchantment on the item
	 */
	public final int getCurrentLevel(ItemStack item){
		if (item.hasItemMeta()){
			assert item.getItemMeta() != null;
			return item.getItemMeta().getEnchantLevel(this);
		}
		return 0;
	}

	// ------------------------------------------------------------------------------------------
	// Our own methods
	// ------------------------------------------------------------------------------------------

	/**
	 * Get the lore shown on items having this enchant.
	 * Return null to hide the lore.
	 * <p>
	 * We have to add item lore manually since Minecraft does not really support custom
	 * enchantments
	 *
	 * @param level the level of the enchantment
	 * @return the compiled lore
	 */
	@Nullable
	public String getLore(int level) {
		return this.name + " " + MathUtil.toRoman(level);
	}

	/**
	 * Get the colored lore shown on items having this enchant.
	 *
	 * @param level the level of the enchantment
	 * @return the compiled colored lore or null if {@link #getLore(int)} is null
	 * @since 6.2.5.4
	 * @author Rubix327
	 */
	@Nullable
	public final String getColoredLore(int level){
		if (getLore(level) == null) return null;
		return Common.colorize("&r&7" + getLore(level));
	}

	/**
	 * Get type of items this enchantment can be applied to.<br>
	 * Default: {@link SimpleEnchantmentTarget#BREAKABLE}
	 */
	public SimpleEnchantmentTarget getCustomItemTarget() {
		return SimpleEnchantmentTarget.BREAKABLE;
	}

	/**
	 * Get material this enchantment can be applied to.
	 * @return the material
	 */
	public Material enchantMaterial() {
		return null;
	}

	/**
	 * Get materials set this enchantment can be applied to.
	 * @return the set of materials
	 */
	public Set<Material> enchantMaterials() {
		return new HashSet<>();
	}

	/**
	 * What items can be enchanted? Default: all <code>ALLOWED</code>.
	 * <br>
	 * Here you can check any very specific characteristics of the item.<br><br>
	 * <b>Example:</b> to prevent the item from being enchanted if it has a display name,
	 * use this check:
	 * <pre>
	 * {@code
	 * if (item.getItemMeta().hasDisplayName()){
	 *     return false;
	 * }
	 * }</pre>
	 *
	 * @param item the item being enchanted
	 * @return the {@link EnchantStatus}. <code>ALLOWED</code> = the item will be enchanted.
	 * @since 6.2.5.4
	 * @author Rubix327
	 */
	@NotNull
	public EnchantStatus canEnchantItem(@NotNull ItemStack item, int level){
		return ALLOWED;
	}

	// ------------------------------------------------------------------------------------------
	// Bukkit methods
	// ------------------------------------------------------------------------------------------

	/**
	 * Get type of items this enchantment can be applied to.<br>
	 * Default: {@link EnchantmentTarget#BREAKABLE}.
	 *
	 * @deprecated This method is no longer used in built-in checks.<br>
	 * Use {@link #getCustomItemTarget()}.
	 */
	@Override
	@Deprecated
	public final @NotNull EnchantmentTarget getItemTarget() {
		return EnchantmentTarget.BREAKABLE;
	}

	/**
	 * Get other enchantments this one conflicts with.<br>
	 * Default: <code>false</code> for all (no conflicts).
	 * <br><br>
	 * <b>Example:</b> to prevent your item from being enchanted if it has {@link #LOOT_BONUS_BLOCKS}
	 * enchantment, use this check:
	 * <pre>
	 * {@code
	 * if (other.equals(Enchantment.LOOT_BONUS_BLOCKS)){
	 *     return false;
	 * }
	 * }</pre>
	 *
	 * @param other the enchantment that is already on the item
	 * @return <code>true</code> if your enchantment conflicts with the given one
	 */
	@Override
	public boolean conflictsWith(@NotNull Enchantment other) {
		return false;
	}

	/**
	 * @deprecated Use {@link #canEnchantItem(ItemStack, int)}.
	 */
	@Deprecated
	@Override
	public final boolean canEnchantItem(@NotNull ItemStack item) {
		return true;
	}

	/**
	 * Get the start level. Default: <code>1</code>
	 */
	@Override
	public int getStartLevel() {
		return 1;
	}

	/**
	 * Is the enchantment a treasure? Default: <code>false</code>
	 */
	@Override
	public boolean isTreasure() {
		return false;
	}

	/**
	 * Is the enchantment cursed? Default: <code>false</code>
	 */
	@Override
	public boolean isCursed() {
		return false;
	}

	/**
	 * Get the max level of this enchantment.
	 */
	@Override
	public final int getMaxLevel() {
		return this.maxLevel;
	}

	/**
	 * Get the name of this enchantment.
	 */
	@Override
	@NotNull
	public final String getName() {
		return this.name;
	}

	// ------------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------------

	/**
	 * Get a map of enchantments with their levels on the given item
	 *
	 * @param item the item
	 * @return the map of enchantments and levels
	 */
	public static Map<SimpleEnchantment, Integer> findEnchantments(ItemStack item) {
		final Map<SimpleEnchantment, Integer> map = new HashMap<>();

		if (item == null)
			return map;

		final Map<Enchantment, Integer> vanilla;

		try {
			vanilla = item.getItemMeta() != null ? item.getItemMeta().getEnchants() : new HashMap<>();
		} catch (final NoSuchMethodError err) {
			if (Remain.hasItemMeta())
				err.printStackTrace();

			return map;

		} catch (final NullPointerException ex) {
			// Caused if any associated enchant is null, probably by a third party plugin
			return map;
		}

		for (final Entry<Enchantment, Integer> e : vanilla.entrySet()) {
			final Enchantment enchantment = e.getKey();
			final int level = e.getValue();

			if (enchantment instanceof SimpleEnchantment)
				map.put((SimpleEnchantment) enchantment, level);
		}

		return map;
	}
}