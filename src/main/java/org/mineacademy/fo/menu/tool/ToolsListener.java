package org.mineacademy.fo.menu.tool;

import lombok.Data;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.ItemUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.event.RocketExplosionEvent;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.SimpleLocalization;

import java.util.*;

/**
 * The event listener class responsible for firing events in tools
 */
public final class ToolsListener implements Listener {

	private static final HashMap<UUID, List<ItemStack>> droppedTools = new HashMap<>();
	/**
	 * Stores rockets that were shot
	 */
	private final Map<UUID, ShotRocket> shotRockets = new HashMap<>();

	/**
	 * Handles clicking tools and shooting rocket
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void onClick(final PlayerInteractEvent event) {
		if (!Remain.isInteractEventPrimaryHand(event))
			return;

		final Player player = event.getPlayer();
		final Tool tool = Tool.getTool(player.getInventory().getItemInHand());
		int initialAmount = 0;
		int finalAmount = 0;
		if (tool != null)
			try {
				if ((event.isCancelled() && tool.ignoreCancelled())) return;
				if (tool instanceof Rocket) {
					final Rocket rocket = (Rocket) tool;

					if (rocket.canLaunch(player, player.getEyeLocation()))
						player.launchProjectile(rocket.getProjectile(), player.getEyeLocation().getDirection().multiply(rocket.getFlightSpeed()));
					else
						event.setCancelled(true);

				} else {
					initialAmount = ItemUtil.getAmount(player, tool.getItem());
					tool.onToolClick(event);
					finalAmount = ItemUtil.getAmount(player, tool.getItem());
				}

				if (tool.autoCancel()) event.setCancelled(true);

			} catch (final Throwable t) {
				if (initialAmount < finalAmount){
					player.getInventory().addItem(tool.getItem());
				}
				event.setCancelled(true);

				Common.tell(player, SimpleLocalization.Tool.ERROR);
				Common.error(t,
						"Failed to handle " + event.getAction() + " using tool: " + tool.getClass());
			}
	}

	/**
	 * Handles block placing
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onToolPlaceBlock(final BlockPlaceEvent event) {

		final Player player = event.getPlayer();
		final Tool tool = Tool.getTool(player.getItemInHand());
		int initialAmount = 0;
		int finalAmount = 0;

		if (tool != null)
			try {
				if (event.isCancelled() && tool.ignoreCancelled())
					return;

				initialAmount = ItemUtil.getAmount(player, tool.getItem());
				tool.onToolPlace(event);
				finalAmount = ItemUtil.getAmount(player, tool.getItem());

				if (tool.autoCancel())
					event.setCancelled(true);

			} catch (final Throwable t) {
				if (initialAmount < finalAmount){
					for (int i = initialAmount; i < finalAmount; i++){
						player.getInventory().addItem(tool.getItem());
					}
				}
				event.setCancelled(true);

				Common.tell(player, SimpleLocalization.Tool.ERROR);
				Common.error(t,
						"Failed to handle placing " + event.getBlock() + " using tool: " + tool.getClass());
			}
	}

	/**
	 * Handles hotbar focus/defocus for tools
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onHeltItem(final PlayerItemHeldEvent event) {
		final Player player = event.getPlayer();

		final Tool current = Tool.getTool(player.getInventory().getItem(event.getNewSlot()));
		final Tool previous = Tool.getTool(player.getInventory().getItem(event.getPreviousSlot()));

		// Player has attained focus
		if (current != null) {

			if (previous != null) {

				// Not really
				if (previous.equals(current))
					return;

				previous.onHotbarDefocused(player);
			}

			current.onHotbarFocused(player);
		}
		// Player lost focus
		else if (previous != null)
			previous.onHotbarDefocused(player);
	}

	// -------------------------------------------------------------------------------------------
	// Rockets
	// -------------------------------------------------------------------------------------------

	/**
	 * Handles launching a rocket
	 *
	 * @param event the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onRocketShoot(final ProjectileLaunchEvent event) {
		final Projectile shot = event.getEntity();
		final Object /* 1.6.4 Comp */ shooter;

		try {
			shooter = shot.getShooter();
		} catch (final NoSuchMethodError ex) {
			if (MinecraftVersion.atLeast(V.v1_4))
				ex.printStackTrace();

			return;
		}

		if (!(shooter instanceof Player))
			return;

		if (this.shotRockets.containsKey(shot.getUniqueId()))
			return;

		final Player player = (Player) shooter;
		final Tool tool = Tool.getTool(player.getInventory().getItemInHand());

		if (tool instanceof Rocket)
			try {
				final Rocket rocket = (Rocket) tool;

				if (event.isCancelled() && tool.ignoreCancelled())
					return;

				if (!rocket.canLaunch(player, shot.getLocation())) {
					event.setCancelled(true);

					return;
				}

				if (tool.autoCancel() || shot instanceof EnderPearl) {
					final World world = shot.getWorld();
					final Location loc = shot.getLocation();

					Common.runLater(shot::remove);

					Common.runLater(1, () -> {
						Valid.checkNotNull(shot, "shot = null");
						Valid.checkNotNull(world, "shot.world = null");
						Valid.checkNotNull(loc, "shot.location = null");

						final Location directedLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().setY(0).normalize().multiply(1.05)).add(0, 0.2, 0);

						final Projectile copy = world.spawn(directedLoc, shot.getClass());
						copy.setVelocity(shot.getVelocity());

						this.shotRockets.put(copy.getUniqueId(), new ShotRocket(player, rocket));
						rocket.onLaunch(copy, player);

						Common.runTimer(1, new BukkitRunnable() {

							private long elapsedTicks = 0;

							@Override
							public void run() {
								if (!copy.isValid() || copy.isOnGround() || this.elapsedTicks++ > 20 * 30 /*Remove after 30 seconds to reduce server strain*/)
									this.cancel();

								else
									rocket.onFlyTick(copy, player);
							}
						});
					});

				} else {
					this.shotRockets.put(shot.getUniqueId(), new ShotRocket(player, rocket));
					rocket.onLaunch(shot, player);
				}

			} catch (final Throwable t) {
				event.setCancelled(true);

				Common.tell(player, SimpleLocalization.Tool.ERROR);
				Common.error(t,
						"Failed to shoot rocket " + tool.getClass());
			}
	}

	@EventHandler
	public void onToolDrop(PlayerDropItemEvent event){
		final Tool tool = Tool.getTool(event.getItemDrop().getItemStack());

		if (tool != null){
			preventDrop(event, tool);
			tool.onToolDrop(event);
		}
	}

	/**
	 * Prevent the tool from dropping if its drop is disabled.<br>
	 * @param event PlayerDropItemEvent
	 * @author Rubix327
	 */
	public void preventDrop(PlayerDropItemEvent event, Tool tool){
		if (tool != null && tool.isDropForbidden()){
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onToolMove(InventoryClickEvent event){
		final Tool tool = Tool.getTool(event.getCurrentItem());

		if (tool != null){
			preventToolMove(event, tool);
			tool.onToolMove(event);
		}
	}

	/**
	 * Prevent the tool from moving into unsafe inventories if its drop is disabled.
	 * @param event InventoryClickEvent
	 * @author Rubix327
	 */
	public void preventToolMove(InventoryClickEvent event, Tool tool){
		List<InventoryType> safeTypes = new ArrayList<>(Arrays.asList(InventoryType.PLAYER, InventoryType.CREATIVE,
				InventoryType.CRAFTING, InventoryType.ENDER_CHEST, InventoryType.MERCHANT, InventoryType.ENCHANTING,
				InventoryType.WORKBENCH));
		if (safeTypes.contains(event.getInventory().getType())) return;

		if (tool != null){
			if (tool.isDropForbidden()){
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onToolWasteDeath(PlayerDeathEvent event){
		if (event.getDrops().isEmpty()) return;

		for (Tool tool : Tool.getTools()){
			for (ItemStack drop : event.getDrops()){
				if (tool.getItem().isSimilar(drop)) {
					saveWastedTool(event.getEntity().getUniqueId(), tool, drop);
					tool.onToolWaste(event);
				}
			}
		}
	}

	/**
	 * Save the death-wasted tool into the map to give them back to the player on revival.
	 * @param player the player uuid
	 * @param tool the tool
	 * @param drop the real dropped ItemStack
	 * @author Rubix327
	 */
	public void saveWastedTool(UUID player, Tool tool, ItemStack drop){
		if (tool == null || !tool.isDropForbidden()) return;
		if (droppedTools.containsKey(player)){
			droppedTools.get(player).add(drop.clone());
		}
		else{
			droppedTools.put(player, new ArrayList<>(Collections.singletonList(drop.clone())));
		}
		drop.setType(Material.AIR);
	}

	/**
	 * Give the wasted tools back to the player after his death.
	 * @param event PlayerRespawnEvent
	 * @author Rubix327
	 */
	@EventHandler
	public void onToolWasteRespawn(PlayerRespawnEvent event){
		if (droppedTools.isEmpty()) return;
		Player player = event.getPlayer();
		if (droppedTools.containsKey(player.getUniqueId())){
			for (ItemStack item : droppedTools.get(player.getUniqueId())){
				player.getInventory().addItem(item);
			}
		}
		droppedTools.get(player.getUniqueId()).clear();
	}

	// -------------------------------------------------------------------------------------------
	// Rockets
	// -------------------------------------------------------------------------------------------

	/**
	 * Handles rockets on impacts
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onRocketHit(final ProjectileHitEvent event) {
		final Projectile projectile = event.getEntity();
		final ShotRocket shot = this.shotRockets.remove(projectile.getUniqueId());

		if (shot != null) {
			final Rocket rocket = shot.getRocket();
			final Player shooter = shot.getShooter();

			try {
				if (rocket.canExplode(projectile, shooter)) {
					final RocketExplosionEvent rocketEvent = new RocketExplosionEvent(rocket, projectile, rocket.getExplosionPower(), rocket.isBreakBlocks());

					if (Common.callEvent(rocketEvent)) {
						final Location location = projectile.getLocation();

						shot.getRocket().onExplode(projectile, shot.getShooter());
						projectile.getWorld().createExplosion(location.getX(), location.getY(), location.getZ(), rocketEvent.getPower(), false, rocketEvent.isBreakBlocks());
					}

				} else
					projectile.remove();
			} catch (final Throwable t) {
				Common.tell(shooter, SimpleLocalization.Tool.ERROR);
				Common.error(t,
						"Failed to handle impact by rocket " + shot.getRocket().getClass());
			}
		}
	}
	/**
	 * Represents a shot rocket with the shooter
	 */
	@Data
	private final class ShotRocket {
		private final Player shooter;
		private final Rocket rocket;
	}
}
