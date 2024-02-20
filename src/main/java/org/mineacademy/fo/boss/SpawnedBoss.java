package org.mineacademy.fo.boss;

import lombok.Getter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;
import org.mineacademy.fo.Common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The wrapper of the boss that has been spawned to a world.
 * The instance of this class stores the living entity, the SimpleBoss
 * class to which he belongs and the BukkitTask that runs once at a certain time.
 *
 * @author Rubix327
 */
@Getter
public final class SpawnedBoss {

    /**
     * All found loaded bosses in the worlds.<br>
     * This map is filled automatically when a boss spawns
     * or someone loads a chunk with a boss inside.
     */
    private static final Map<LivingEntity, SpawnedBoss> bosses = new HashMap<>();
    /**
     * The period between each removal of invalid bosses.
     */
    @Getter
    private static int removeInvalidPeriod = 200;

    /**
     * The SimpleBoss belonging to this SpawnedBoss.
     */
    private final SimpleBoss boss;
    /**
     * The real entity of this boss.<br>
     */
    private final LivingEntity entity;
    /**
     * The task belonging to this boss. This task is ran when the boss is spawned.<br>
     * Also, you can manually run this task by {@link #runTaskTimers()}.
     */
    private BukkitTask task;

    private SpawnedBoss(SimpleBoss boss, LivingEntity entity) {
        this.boss = boss;
        this.entity = entity;
        bosses.put(entity, this);
    }

    /**
     * Remove the entity from the world.
     */
    public void remove(){
        unload(entity);
        entity.remove();
    }

    /**
     * Run task timers to execute them in the specified repeat rate.<br>
     * If the task already exist, it will not be run anymore.<br><br>
     * These timers are already run automatically when the entity is spawned or loaded
     * and generally should not be used externally. <b>Use it on your own risk!</b>
     */
    public void runTaskTimers(){
        if (task == null && entity.isValid()){
            // If the task should only be run once
            if (boss.getTaskPeriod() < 0){
                task = Common.runLater(boss.getTaskDelay(), this::runTaskOnce);
                Common.runLater(boss.getTaskDelay() + 1, this::cancelTask);
            }
            // If the task is repeatable
            else{
                task = Common.runTimer(boss.getTaskDelay(), boss.getTaskPeriod(), this::runTaskOnce);
            }
        }
    }

    /**
     * Run the task if the entity is still alive. Otherwise - cancel it.<br><br>
     *
     * This method is already called automatically and generally should
     * not be used externally. <b>Use it on your own risk!</b>
     */
    public void runTaskOnce(){
        if (entity.isValid()){
            boss.runTask(entity);
        }
        else{
            cancelTask();
        }
    }

    /**
     * Cancel the task for this boss if it exists.
     */
    public void cancelTask(){
        if (task != null){
            task.cancel();
            task = null;
        }
    }

    /**
     * Check is the entity is loaded to the plugin as a SpawnedBoss.
     * @param entity the entity
     * @return true if it's loaded
     */
    public static boolean isLoaded(Entity entity){
        if (!SimpleBoss.isBoss(entity)) return false;
        return bosses.get((LivingEntity) entity) != null;
    }

    /**
     * Get the SpawnedBoss of the entity.
     * @param entity the entity
     * @return the SpawnedBoss or null if this entity is not a boss or not loaded ({@link #isLoaded(Entity)})
     */
    public static SpawnedBoss get(LivingEntity entity){
        return bosses.get(entity);
    }

    /**
     * Get all loaded SpawnBosses on the server.
     * @return collection of spawned bosses.
     */
    public static Collection<SpawnedBoss> getAll(){
        return bosses.values();
    }

    /**
     * Load the entity to the plugin.
     * @param entity the entity
     * @return the SpawnedBoss or null if this entity is not a boss
     */
    public static SpawnedBoss load(LivingEntity entity){
        if (SimpleBoss.isBoss(entity)){
            SpawnedBoss boss = new SpawnedBoss(SimpleBoss.get(entity), entity);
            boss.runTaskTimers();
            removeInvalidBosses();
            return boss;
        }
        return null;
    }

    /**
     * Unload the entity from the plugin.
     * From this moment it is no longer a SpawnedBoss.
     * @param entity the entity
     */
    public static void unload(LivingEntity entity){
        if (SimpleBoss.isBoss(entity)){
            bosses.get(entity).cancelTask();
            bosses.remove(entity);
        }
    }

    /**
     * Set the period between each removal of invalid bosses.<br>
     * Decreasing the value may reduce performance.<br>
     * Increasing the value will mean that the bosses can stay longer
     * in the container ({@link #getAll()}) and take up server memory.<br>
     * Default value is 200 ticks (10 seconds).
     * @param period the period, in ticks
     */
    @SuppressWarnings("unused")
    public static void setRemoveInvalidPeriod(int period) {
        removeInvalidPeriod = period;
    }

    /**
     * Loop all the SpawnedBosses and remove invalid ones.<br>
     * Bosses can become invalid when the server removes them due to the player being too far away.
     */
    private static void removeInvalidBosses(){
        Common.runTimer(removeInvalidPeriod, () -> {
            Collection<Map.Entry<LivingEntity, SpawnedBoss>> dead = new ArrayList<>();
            for (Map.Entry<LivingEntity, SpawnedBoss> entry : bosses.entrySet()){
                if (!entry.getKey().isValid()){
                    dead.add(entry);
                }
            }
            dead.forEach(b -> {
                bosses.remove(b.getKey());
                b.getValue().getBoss().onDespawn(b.getKey());
            });
            dead.clear();
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpawnedBoss that = (SpawnedBoss) o;

        return entity.getUniqueId().equals(that.entity.getUniqueId());
    }

    @Override
    public int hashCode() {
        return entity.hashCode();
    }

    @Override
    public String toString() {
        return "SpawnedBoss{" +
                "entityUUID=" + entity.getUniqueId() +
                ", boss=" + boss +
                '}';
    }
}
