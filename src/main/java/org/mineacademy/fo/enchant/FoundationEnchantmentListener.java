package org.mineacademy.fo.enchant;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.mineacademy.fo.EntityUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.remain.Remain;

import java.util.Map.Entry;
import java.util.function.BiConsumer;

/**
 * Listens and executes events for {@link SimpleEnchantment}
 * <p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FoundationEnchantmentListener implements Listener {

	@Getter
	private static final FoundationEnchantmentListener instance = new FoundationEnchantmentListener();

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageByEntityEvent event) {
		final Entity damager = event.getDamager();

		if (damager instanceof LivingEntity)
			this.execute((LivingEntity) damager, (enchant, level) -> enchant.onDamage(level, (LivingEntity) damager, event));
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void onInteract(PlayerInteractEvent event) {
		if (!Remain.isInteractEventPrimaryHand(event))
			return;

		this.execute(event.getPlayer(), (enchant, level) -> enchant.onInteract(level, event));
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBreakBlock(BlockBreakEvent event) {
		this.execute(event.getPlayer(), (enchant, level) -> enchant.onBreakBlock(level, event));
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onShoot(ProjectileLaunchEvent event) {
		try {
			final ProjectileSource projectileSource = event.getEntity().getShooter();

			if (projectileSource instanceof LivingEntity) {
				final LivingEntity shooter = (LivingEntity) projectileSource;

				this.execute(shooter, (enchant, level) -> enchant.onShoot(level, shooter, event));
				EntityUtil.trackHit(event.getEntity(), hitEvent -> this.execute(shooter, (enchant, level) -> enchant.onHit(level, shooter, hitEvent)));
			}
		} catch (final NoSuchMethodError ex) {
			if (MinecraftVersion.atLeast(V.v1_4))
				ex.printStackTrace();
		}
	}

	/**
	 * Executes the given executor function on the item
	 * held in the main hand of the given living entity
	 * for all its custom enchantments.
	 * @param entity the living entity
	 * @param executer the function to execute
	 */
	@SuppressWarnings("deprecation")
	public void execute(LivingEntity entity, BiConsumer<SimpleEnchantment, Integer> executer) {
		try {
			boolean isPlayer = entity instanceof Player;
			final ItemStack hand;
			if (isPlayer){
				hand = ((Player)entity).getItemInHand();
			} else {
				if (entity.getEquipment() == null) return;
				hand = entity.getEquipment().getItemInHand();
			}

			this.execute(hand, executer);
		} catch (final NoSuchMethodError ex) {
			if (Remain.hasItemMeta()){
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Executes the given executor function on the given item for all its custom enchantments.
	 * @param item the item
	 * @param executer the function to execute
	 */
	public void execute(ItemStack item, BiConsumer<SimpleEnchantment, Integer> executer){
		try{
			for (final Entry<SimpleEnchantment, Integer> e : SimpleEnchantment.findEnchantments(item).entrySet()) {
				executer.accept(e.getKey(), e.getValue());
			}
		} catch (final NoSuchMethodError ex) {
			if (Remain.hasItemMeta()){
				ex.printStackTrace();
			}
		}
	}
}
