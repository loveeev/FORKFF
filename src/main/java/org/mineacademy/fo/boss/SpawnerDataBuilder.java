package org.mineacademy.fo.boss;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The convenient builder for {@link SpawnerData}.
 *
 * @author Rubix327
 */
@SuppressWarnings("unused")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpawnerDataBuilder {
    private @Nullable SimpleBoss boss;
    private final @NotNull EntityType type;
    private String itemName = "%name% Spawner";
    private int delay = 10;
    private int minSpawnDelay = 200;
    private int maxSpawnDelay = 800;
    private int spawnCount = 4;
    private int maxNearbyEntities = 16;
    private int requiredPlayerRange = 16;
    private int spawnRange = 4;

    /**
     * Create new SpawnerDataBuilder with the specified EntityType.
     * @param type the type
     * @return the new SpawnerDataBuilder
     */
    public static SpawnerDataBuilder of(EntityType type){
        return new SpawnerDataBuilder(type);
    }

    /**
     * Set the SimpleBoss related to this SpawnerData.
     * Used for saving boss' data automatically on any change.
     */
    public SpawnerDataBuilder setBoss(SimpleBoss boss){
        this.boss = boss;
        return this;
    }

    /**
     * Set the name of the item in {@link SimpleBoss#getSpawner()}
     */
    public SpawnerDataBuilder setItemName(String name){
        this.itemName = name;
        return this;
    }

    /**
     * See {@link CreatureSpawner#setDelay(int)}
     */
    public SpawnerDataBuilder setDelay(int delay) {
        this.delay = delay;
        return this;
    }

    /**
     * See {@link CreatureSpawner#setMinSpawnDelay(int)}
     */
    public SpawnerDataBuilder setMinSpawnDelay(int minSpawnDelay) {
        this.minSpawnDelay = minSpawnDelay;
        return this;
    }

    /**
     * See {@link CreatureSpawner#setMaxSpawnDelay(int)}
     */
    public SpawnerDataBuilder setMaxSpawnDelay(int maxSpawnDelay) {
        this.maxSpawnDelay = maxSpawnDelay;
        return this;
    }

    /**
     * See {@link CreatureSpawner#setSpawnCount(int)}
     */
    public SpawnerDataBuilder setSpawnCount(int spawnCount) {
        this.spawnCount = spawnCount;
        return this;
    }

    /**
     * See {@link CreatureSpawner#setMaxNearbyEntities(int)}
     */
    public SpawnerDataBuilder setMaxNearbyEntities(int maxNearbyEntities) {
        this.maxNearbyEntities = maxNearbyEntities;
        return this;
    }

    /**
     * See {@link CreatureSpawner#setRequiredPlayerRange(int)}
     */
    public SpawnerDataBuilder setRequiredPlayerRange(int requiredPlayerRange) {
        this.requiredPlayerRange = requiredPlayerRange;
        return this;
    }

    /**
     * See {@link CreatureSpawner#setSpawnRange(int)}
     */
    public SpawnerDataBuilder setSpawnRange(int spawnRange) {
        this.spawnRange = spawnRange;
        return this;
    }

    public SpawnerData build() {
        return new SpawnerData(type, itemName, delay, minSpawnDelay, maxSpawnDelay, spawnCount, maxNearbyEntities, requiredPlayerRange, spawnRange);
    }
}