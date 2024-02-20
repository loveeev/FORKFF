package org.mineacademy.fo.boss;

import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.model.ItemStackSerializer;
import org.mineacademy.fo.remain.CompMetadata;

/**
 * This class listens to events related to SimpleBosses.<br>
 * See {@link SimpleBoss} and {@link SpawnedBoss}.
 *
 * @author Rubix327
 */
@AutoRegister
public final class SimpleBossListener implements Listener {

    @EventHandler
    public void onBossAttack(EntityDamageByEntityEvent event){
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (SimpleBoss.isBoss(damager)){
            SimpleBoss boss = SimpleBoss.get(damager);
            if (boss.getDamage(event) > 0){
                event.setDamage(boss.getDamage(event));
            }
            boss.onAttack(victim, event);
        }
    }

    @EventHandler
    public void onBossGetDamage(EntityDamageByEntityEvent event){
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (SimpleBoss.isBoss(victim)){
            SimpleBoss boss = SimpleBoss.get(victim);

            boss.onGetDamage(damager, event);
            Common.tell(damager, "" + event.getFinalDamage());
        }
    }

    @EventHandler
    public void onBossDie(EntityDeathEvent event){
        LivingEntity entity = event.getEntity();
        if (!SimpleBoss.isBoss(entity)) return;

        SimpleBoss boss = SimpleBoss.get(entity);

        if (boss.getExperienceDrop() != null){
            event.setDroppedExp(boss.getExperienceDrop());
        }

        if (!boss.isHasVanillaDrops()){
            event.getDrops().clear();
        }

        // Adding custom drops
        if (!Valid.isNullOrEmpty(boss.getDropTables())){
            for (SimpleDropTable table : boss.getDropTables()){
                if (!RandomUtil.chance(table.getChance())) continue;

                for (ItemStackSerializer item : table.getTable()){
                    if (RandomUtil.chance(item.getDropChance())){
                        event.getDrops().add(item.toItemStack());
                    }
                }

                break;
            }
        }

        // Call onDie method so the user can customize this event
        boss.onDie(event);

        // Unload the boss from the memory
        SpawnedBoss.unload(entity);
    }

    @EventHandler
    public void onBurnSunlight(EntityCombustEvent event){
        Entity entity = event.getEntity();
        if (SimpleBoss.isBoss(entity)){
            SimpleBoss boss = SimpleBoss.get(entity);
            if (!boss.isBurnOnSunlight()){
                event.setCancelled(true);
                return;
            }
            boss.onBurn(event);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event){
        for (Entity entity : event.getChunk().getEntities()){
            if (entity instanceof LivingEntity){
                LivingEntity ent = ((LivingEntity) entity);
                SpawnedBoss boss = SpawnedBoss.load(ent);
                if (boss != null){
                    boss.getBoss().onChunkLoad(ent);
                }
            }
        }
    }

    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event){
        String meta = CompMetadata.getMetadata(event.getPlayer().getInventory().getItemInMainHand(), SimpleBoss.getSpawnerKey());
        if (SimpleBoss.exists(meta)){
            SimpleBoss boss = SimpleBoss.get(meta);
            SpawnerData data = boss.getSpawnerData();
            CreatureSpawner spawner = (CreatureSpawner) event.getBlock().getState();

            // Setting values from SimpleBoss#getSpawnerData() to the spawner
            spawner.setSpawnedType(data.getType());
            spawner.setDelay(data.getStartDelay());
            spawner.setMinSpawnDelay(data.getMinSpawnDelay());
            spawner.setMaxSpawnDelay(data.getMaxSpawnDelay());
            spawner.setSpawnCount(data.getSpawnCount());
            spawner.setMaxNearbyEntities(data.getMaxNearbyEntities());
            spawner.setRequiredPlayerRange(data.getRequiredPlayerRange());
            spawner.setSpawnRange(data.getSpawnRange());
            spawner.update();

            // Adding metadata to the block after the spawner is updated
            CompMetadata.setMetadata(event.getBlock(), SimpleBoss.getSpawnerKey(), meta);
            boss.onSpawnerPlace(spawner, event);
        }
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event){
        String meta = CompMetadata.getMetadata(event.getSpawner().getBlock(), SimpleBoss.getSpawnerKey());
        if (SimpleBoss.exists(meta)){
            event.setCancelled(true);
            SimpleBoss.get(meta).spawn(event.getLocation());
        }
    }

}
