package org.mineacademy.fo.boss;

import lombok.*;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.annotation.AutoSerialize;
import org.mineacademy.fo.model.AutoSerializable;

/**
 * This class encapsulates data for spawners.
 * To create SpawnerData, use the all-arguments constructor or
 * the convenient builder - {@link SpawnerData#of(EntityType)}.
 *
 * @author Rubix327
 */
@Getter
@Setter
@AutoSerialize
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpawnerData implements AutoSerializable {
    /**
     * The type of the entity which is spinning in the spawner.<br>
     * Default: EntityType.PIG
     */
    private @NotNull EntityType type = EntityType.PIG;
    /**
     * The item name which is given to the player.<br>
     * Used in {@link SimpleBoss#getSpawner()}.
     */
    private String itemName;
    /**
     * The starting delay of the spawner which is decreasing every tick.<br>
     * When the value reaches -1, the spawn delay is reset to a random value between
     * {@link #getMinSpawnDelay} and {@link #getMaxSpawnDelay()}.<br>
     * See {@link CreatureSpawner#getDelay()}
     */
    private int startDelay;
    /**
     * The minimum time (in ticks) that must elapse after mob spawn to spawn a new mob.<br>
     * See {@link CreatureSpawner#getMinSpawnDelay()}
     */
    private int minSpawnDelay;
    /**
     * The minimum time (in ticks) that must elapse after mob spawn to spawn a new mob.<br>
     * See {@link CreatureSpawner#getMaxSpawnDelay()}
     */
    private int maxSpawnDelay;
    /**
     * See {@link CreatureSpawner#getSpawnCount()}
     */
    private int spawnCount;
    /**
     * See {@link CreatureSpawner#getMaxNearbyEntities()}
     */
    private int maxNearbyEntities;
    /**
     * See {@link CreatureSpawner#getRequiredPlayerRange()}
     */
    private int requiredPlayerRange;
    /**
     * See {@link CreatureSpawner#getSpawnRange()}
     */
    private int spawnRange;

    /**
     * Create a new builder for the SpawnedData and set the given type to the spawner.
     * @param type the spinning type
     * @return builder SpawnerDataBuilder
     */
    public static SpawnerDataBuilder of(EntityType type){
        return SpawnerDataBuilder.of(type);
    }

    @Override
    public String toString() {
        return "SpawnerData{" +
                "type=" + type +
                ", itemName='" + itemName + '\'' +
                ", startDelay=" + startDelay +
                ", minSpawnDelay=" + minSpawnDelay +
                ", maxSpawnDelay=" + maxSpawnDelay +
                ", spawnCount=" + spawnCount +
                ", maxNearbyEntities=" + maxNearbyEntities +
                ", requiredPlayerRange=" + requiredPlayerRange +
                ", spawnRange=" + spawnRange +
                '}';
    }
}

