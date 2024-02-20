package org.mineacademy.fo.boss;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.annotation.AutoConfig;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.*;
import org.mineacademy.fo.settings.YamlConfig;

import java.io.File;
import java.util.*;

import static org.mineacademy.fo.boss.SimpleBossEquipment.Part.*;

/**
 * Simple wrapper class for custom mobs and bosses.<br>
 * Here you can:
 * <ul>
 *     <li>Set the name, health, damage, drop tables, equipment, etc. in {@link #setup()} method</li>
 *     <li>Set the rest of the characteristics in {@link #onSpawn(LivingEntity)}</li>
 *     <li>Spawn entity using {@link #spawn(Location)}</li>
 *     <li>Easily add extra behavior on spawn, attack, get damage, death, etc.</li>
 *     <li>Add timed tasks by overriding {@link #runTask(LivingEntity)}, {@link #getTaskPeriod()}, {@link #getTaskDelay()}</li>
 *     <li>Save and load to the file using YamlConfig</li>
 * </ul>
 *
 * @author Rubix327
 */
@Getter
@AutoConfig
@SuppressWarnings({"unchecked", "unused", "deprecation"})
public class SimpleBoss extends YamlConfig implements Listener {

    /**
     * Loaded singleton instances of the bosses.
     * Only used in internal purposes (to correctly load bosses after restart).
     * Do not use at your own.
     */
    private static final Map<String, SimpleBoss> instances = new HashMap<>();
    private static boolean SAVE_TO_FILES = false;
    /**
     * The metadata key under which all bosses store their IDs.
     */
    private static @NotNull String META_KEY = "Fo_Boss";
    /**
     * The metadata key which all boss spawners have.
     */
    private static @NotNull String SPAWNER_KEY = "Fo_Boss_Spawner";
    /**
     * The path where all boss' files are located.
     */
    private static @NotNull String PATH = "bosses/";

    protected final @NotNull String id;
    protected @NotNull EntityType type;
    protected String name = "Unnamed";
    protected Double health;
    protected Double damage;
    protected SimpleBossEquipment equipment = new SimpleBossEquipment();
    @AutoConfig(autoLoad = false)
    protected LinkedHashSet<SimpleDropTable> dropTables = new LinkedHashSet<>();
    @AutoConfig(autoLoad = false)
    protected LinkedHashSet<SimplePotionEffect> potionEffects = new LinkedHashSet<>();
    @AutoConfig(false)
    protected LinkedHashMap<String, SimpleBossSkill> skills = new LinkedHashMap<>();
    protected boolean hasVanillaDrops = true;
    protected boolean hasEquipmentDrops = true;
    protected Integer experienceDrop = 0;
    protected String passenger;
    protected boolean isBaby = false;
    protected boolean isRemovedWhenFarAway = true;
    protected boolean isBurnOnSunlight = true;
    protected boolean isGlowing = false;
    protected CompColor glowColor = CompColor.WHITE;
    protected int taskDelay = 0;
    protected int taskPeriod = -1;
    protected SpawnerData spawnerData;

    public SimpleBoss(@NotNull String id, @NotNull EntityType type) {
        String fullPath = getSavePath() + id + ".yml";

        this.id = id;
        this.type = type;
        this.spawnerData = SpawnerData.of(getType()).build();
        register(this);

        // Call class type to check if the getEntityType is not null and is alive
        getClassType();

        // Set a file to get no errors when saving is disabled
        this.setFile(new File(fullPath));

        // Load fields from the file
        if (SAVE_TO_FILES) {
            loadConfiguration(getDefaultFile(), fullPath);
        }

        // Setup from the child class
        setup();
        // Save to the file
        save();
    }

    protected String getDefaultFile(){
        return null;
    }

    protected void setup(){}

    /*
     * Setters for every field
     */

    public final void setType(@NotNull EntityType type) {
        this.type = type;
        this.save();
    }

    public final void setName(String name) {
        this.name = name;
        this.save();
    }

    public final void setHealth(Double health) {
        this.health = health;
        this.save();
    }

    public void setDamage(Double damage) {
        this.damage = damage;
        this.save();
    }

    public final void setEquipment(EntityEquipment equipment) {
        ItemStack offHand = new ItemStack(Material.AIR);
        if (MinecraftVersion.atLeast(MinecraftVersion.V.v1_9)){
            offHand = equipment.getItemInOffHand();
        }
        ItemStack[] items = new ItemStack[]{
                equipment.getHelmet(),
                equipment.getChestplate(),
                equipment.getLeggings(),
                equipment.getBoots(),
                equipment.getItemInHand(),
                offHand
        };
        this.equipment = new SimpleBossEquipment(items);
        this.save();
    }

    public final void setEquipment(SimpleBossEquipment equipment){
        this.equipment = equipment;
        this.save();
    }

    public final void setDropTables(LinkedHashSet<SimpleDropTable> dropTables) {
        this.dropTables = dropTables;
        this.save();
    }

    public final void setDropTables(SimpleDropTable... tables){
        this.dropTables = new LinkedHashSet<>(Arrays.asList(tables));
        this.save();
    }

    public final void addDropTable(SimpleDropTable table){
        this.dropTables.add(table);
        this.save();
    }

    public final void removeDropTable(SimpleDropTable table){
        this.dropTables.remove(table);
        this.save();
    }

    public final void setSkills(LinkedHashMap<String, SimpleBossSkill> skills){
        this.skills = skills;
        this.save();
    }

    public final void addSkill(SimpleBossSkill skill){
        this.skills.put(skill.getId(), skill);
        this.save();
    }

    public final void removeSkill(SimpleBossSkill skill){
        this.skills.remove(skill.getId());
        this.save();
    }

    public final void setPotionEffects(LinkedHashSet<SimplePotionEffect> potionEffects) {
        this.potionEffects = potionEffects;
        this.save();
    }

    public final void addPotionEffect(PotionEffect effect){
        this.potionEffects.add(new SimplePotionEffect(effect));
        this.save();
    }

    public final void addPotionEffect(SimplePotionEffect effect){
        this.potionEffects.add(effect);
        this.save();
    }

    public final void removePotionEffect(SimplePotionEffect effect){
        this.potionEffects.remove(effect);
        this.save();
    }

    public final void setHasVanillaDrops(boolean hasVanillaDrops) {
        this.hasVanillaDrops = hasVanillaDrops;
        this.save();
    }

    public final void setHasEquipmentDrops(boolean hasEquipmentDrops) {
        this.hasEquipmentDrops = hasEquipmentDrops;
        this.save();
    }

    public final void setExperienceDrop(Integer experienceDrop) {
        this.experienceDrop = experienceDrop;
        this.save();
    }

    @Nullable
    public final SimpleBoss getPassenger(){
        return SimpleBoss.get(this.passenger);
    }

    public final void setPassenger(SimpleBoss passenger) {
        this.passenger = passenger.getId();
        this.save();
    }

    public final void setBaby(boolean baby) {
        isBaby = baby;
        this.save();
    }

    public final void setRemoveWhenFarAway(boolean removedWhenFarAway) {
        isRemovedWhenFarAway = removedWhenFarAway;
        this.save();
    }

    public final void setBurnOnSunlight(boolean burnOnSunlight) {
        isBurnOnSunlight = burnOnSunlight;
        this.save();
    }

    public final void setGlowing(boolean glowing) {
        isGlowing = glowing;
        this.save();
    }

    public final void setGlowColor(CompColor glowColor) {
        this.glowColor = glowColor;
        this.save();
    }

    public final void setTaskDelay(int taskDelay) {
        this.taskDelay = taskDelay;
        this.save();
    }

    public final void setTaskPeriod(int taskPeriod) {
        this.taskPeriod = taskPeriod;
        this.save();
    }

    public final void setSpawnerData(SpawnerData spawnerData) {
        this.spawnerData = spawnerData;
        this.save();
    }

    /**
     * Get the boss attack damage.
     * By default, it would be the same as in vanilla Minecraft with the boss' weapon taken into account
     * @param event EntityDamageEvent
     * @return the attack damage
     */
    public double getDamage(EntityDamageByEntityEvent event){
        if (this.damage != null) return damage;
        return Remain.getFinalDamage(event);
    }

    /**
     * Called when the boss attacks another entity.
     * @param event EntityDeathEvent
     */
    protected void onAttack(Entity target, EntityDamageByEntityEvent event){}

    /**
     * Called when the boss gets damage from another entity.
     * @param event EntityDeathEvent
     */
    protected void onGetDamage(Entity damager, EntityDamageByEntityEvent event){}

    /**
     * Called when the boss dies
     * @param event EntityDeathEvent
     */
    protected void onDie(EntityDeathEvent event){}

    /**
     * Called when the boss is fired by the sun (zombie, skeleton, etc.).
     * @param event EntityCombustEvent
     */
    protected void onBurn(EntityCombustEvent event){}

    /**
     * Called when someone places a spawner of this boss.
     * @param spawner the spawner
     * @param event BlockPlaceEvent
     */
    protected void onSpawnerPlace(CreatureSpawner spawner, BlockPlaceEvent event){}

    /**
     * Called when the boss is spawned.<br>
     * This method is the last opportunity to somehow change the boss entity.
     * @param entity the instance of entity being spawned
     */
    protected void onSpawn(LivingEntity entity){}

    /**
     * Called when the boss is loaded when a chunk is loaded by a player.<br>
     * <b>Not called when the boss is spawned.</b>
     * @param entity the entity of this boss
     */
    protected void onChunkLoad(LivingEntity entity){}

    /**
     * Called when the boss is despawned by the server due to the player is too far away.
     * @param entity the despawned entity
     */
    protected void onDespawn(LivingEntity entity){}

    /**
     * Run the task of this boss on the given entity.<br>
     * When overriding, here you should specify only single execution of the task (not a timer!).
     * @param entity the entity.
     */
    public void runTask(LivingEntity entity){}

    /**
     * Get the class type of specified EntityType.<br>
     * This method includes checks if EntityType is not null and alive.
     * @return the class type
     */
    @SuppressWarnings("ConstantValue")
    final Class<? extends LivingEntity> getClassType(){
        if (getType() == null){
            throw new IllegalStateException("Method getType must not return null. " +
                    "Please override this method manually and return a living EntityType.");
        }
        if (!type.isAlive() || !type.isSpawnable()){
            throw new IllegalArgumentException("Boss entity type must be alive (LivingEntity) and spawnable.");
        }
        return (Class<? extends LivingEntity>) type.getEntityClass();
    }

    /**
     * Spawn the entity in the world at the given location.
     * @param location the location.
     * @return the SpawnedBoss instance
     * @throws IllegalArgumentException if world in the given location is undefined
     */
    public final SpawnedBoss spawn(Location location) throws IllegalArgumentException{
        if (location.getWorld() == null) throw new IllegalArgumentException("Invalid world given (not set in Location?)");
        LivingEntity ent = location.getWorld().spawn(location, getClassType(), entity -> {
            // Add boss meta-tag to the entity
            CompMetadata.setMetadata(entity, META_KEY, this.getId());

            // Let the user customize this entity at his will
            onSpawn(entity);

            // Set the specified display name
            if (this.getName() != null) {
                Remain.setCustomName(entity, this.getName());
            }

            // Set the max health of the entity
            if (getHealth() != null && getHealth() > 0){
                CompAttribute.GENERIC_MAX_HEALTH.set(entity, this.getHealth());
            }

            // Make the entity a baby
            Remain.setBabyOrAdult(entity, isBaby());

            // Remove equipment drops if they are disabled
            // or set chances from SimpleBossEquipment if enabled
            EntityEquipment eq = entity.getEquipment();
            SimpleBossEquipment sbe = getEquipment();
            boolean hasEq = isHasEquipmentDrops();
            if (eq != null){
                eq.setHelmetDropChance(hasEq ? sbe.getChanceOrDefault(HELMET) : 0);
                eq.setChestplateDropChance(hasEq ? sbe.getChanceOrDefault(CHESTPLATE) : 0);
                eq.setLeggingsDropChance(hasEq ? sbe.getChanceOrDefault(LEGGINGS) : 0);
                eq.setBootsDropChance(hasEq ? sbe.getChanceOrDefault(BOOTS) : 0);
                eq.setItemInHandDropChance(hasEq ? sbe.getChanceOrDefault(MAIN_HAND) : 0);
                if (MinecraftVersion.atLeast(MinecraftVersion.V.v1_9)) {
                    eq.setItemInOffHandDropChance(hasEq ? sbe.getChanceOrDefault(OFF_HAND) : 0);
                }
            }

            // Set equipment from the boss to the entity
            entity.getEquipment().setArmorContents(sbe.getArmorContents());
            entity.getEquipment().setItemInHand(sbe.getMainHand());
            if (MinecraftVersion.atLeast(MinecraftVersion.V.v1_9)){
                entity.getEquipment().setItemInOffHand(sbe.getOffHand());
            }

            // Add potion effects
            if (potionEffects != null){
                for (SimplePotionEffect effect : potionEffects){
                    entity.addPotionEffect(effect.toPotionEffect());
                }
            }

            // Remove entity when far away from player
            entity.setRemoveWhenFarAway(isRemovedWhenFarAway());

            // Add glowing effect to the boss
            if (isGlowing()){
                Remain.setGlowing(entity, getGlowColor());
            }

            // Lastly, add passenger
            SimpleBoss pass = getPassenger();
            if (pass != null){
                Remain.addPassenger(entity, pass.spawn(entity.getLocation()).getEntity());
            }
        });

        // Load the boss to the plugin
        return SpawnedBoss.load(ent);
    }

    /**
     * Spawn boss with the given ID on the given location.
     * If there is no boss with that ID, it will throw NPE.
     * @param id the id of the boss
     * @param location the location
     * @return new SpawnedBoss instance
     * @throws NullPointerException if no boss with that ID found
     */
    public static SpawnedBoss spawn(String id, Location location) throws NullPointerException{
        SimpleBoss boss = instances.get(id);
        if (boss == null){
            throw new NullPointerException("No boss found with the given name.");
        }
        return boss.spawn(location);
    }

    /**
     * Get the spawner item for this boss.
     * @return the spawner
     */
    public final ItemStack getSpawner(){
        String name = getName() + " Spawner";
        if (getSpawnerData().getItemName() != null && !getSpawnerData().getItemName().isEmpty()){
            name = getSpawnerData().getItemName().replace("%name%", getName());
        }
        return ItemCreator.of(CompMaterial.SPAWNER).name(name).metadata(SPAWNER_KEY, getId()).make();
    }

    /**
     * Kill all bosses within a radius of 200 blocks from the given location.
     * @param location the location
     * @return amount of mobs killed
     */
    public static int killAll(Location location){
        return killAll(location, 200, null);
    }

    /**
     * Kill specific bosses within a given radius from the given location.
     * @param location the location
     * @param radius radius
     * @param boss the boss, specify null to ignore boss' class
     * @return amount of mobs killed
     */
    public static int killAll(Location location, int radius, SimpleBoss boss){
        World world = location.getWorld();
        if (world == null) return 0;
        int counter = 0;
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius,
                e -> e instanceof LivingEntity && !(e instanceof Player))){
            SpawnedBoss spawned = SpawnedBoss.get((LivingEntity) entity);
            if (spawned != null){
                if (boss != null && !boss.equals(spawned.getBoss())) continue;
                spawned.remove();
                counter++;
            }
        }
        return counter;
    }

    /**
     * Check if the entity is a boss of this specific class.
     * @param entity the entity
     * @return is specific boss
     */
    public final boolean is(Entity entity){
        return isBoss(entity) && getId().equals(getMeta(entity));
    }

    /**
     * Check if the entity is a SimpleBoss.<br>
     * To check if the entity is spawned and loaded use {@link SpawnedBoss#isLoaded(Entity)}.
     * @param entity the entity
     * @return true if the entity has {@link #META_KEY} and is a LivingEntity
     */
    public static boolean isBoss(Entity entity){
        if (!(entity instanceof LivingEntity)) return false;
        return exists(getMeta(entity));
    }

    public static boolean exists(String id){
        return id != null && getIDs().contains(id);
    }

    /**
     * Try to get the SimpleBoss instance of this entity.
     * @param entity the entity
     * @return the SimpleBoss or null if the entity is not a boss ({@link #isBoss})
     */
    public static SimpleBoss get(Entity entity){
        if (!(entity instanceof LivingEntity)) return null;
        return get(getMeta(entity));
    }

    /**
     * Get metadata from the given entity by the {@link #META_KEY} key.
     * @param entity the entity
     * @return the id of the boss or null if it is not a boss
     */
    public static String getMeta(Entity entity){
        return CompMetadata.getMetadata(entity, META_KEY);
    }

    /**
     * Try to get the SimpleBoss instance from the ID.
     * @param simpleBossID ID of the SimpleBoss
     * @return the SimpleBoss or null if this ID does not belong to any SimpleBoss
     */
    public static SimpleBoss get(String simpleBossID){
        return instances.get(simpleBossID);
    }

    /**
     * Get all bosses' ids.
     * @return the list of IDs
     */
    public static List<String> getIDs(){
        return new ArrayList<>(instances.keySet());
    }

    /**
     * Get all instances of SimpleBosses.
     * @return the list of instances.
     */
    public static List<SimpleBoss> getInstances(){
        return new ArrayList<>(instances.values());
    }

    /**
     * Register the given boss.
     * @param boss the boss
     */
    private static void register(SimpleBoss boss){
        instances.put(boss.id, boss);
    }

    /**
     * Get the metadata key under which all bosses store their IDs.
     * @return the key
     */
    @NotNull
    public static String getMetaKey(){
        return META_KEY;
    }

    /**
     * Set the metadata key under which all bosses store their IDs.
     * @param newTag the new key
     */
    public static void setMetaKey(@NotNull String newTag){
        META_KEY = newTag;
    }

    /**
     * Get the metadata key which all boss spawners have.
     * @return the key
     */
    @NotNull
    public static String getSpawnerKey(){
        return SPAWNER_KEY;
    }

    /**
     * Set the metadata key which all boss spawners have.
     * @param newKey the new key
     */
    public static void setSpawnerKey(@NotNull String newKey){
        SPAWNER_KEY = newKey;
    }


    /**
     * Get the path where all boss' files are located.
     * @return the path
     */
    @NotNull
    public static String getSavePath() {
        return PATH;
    }

    /**
     * Set the path where all boss' files are located.
     * @param path the new path
     */
    public static void setSavePath(@NotNull String path) {
        SimpleBoss.PATH = path;
    }

    public static void setSaveEnabled(boolean isEnabled){
        SAVE_TO_FILES = isEnabled;
    }

    public static boolean isSaveEnabled(){
        return SAVE_TO_FILES;
    }

    @Override
    protected void onSave() {
        this.set("skills", this.skills.values());
    }

    @Override
    protected void onLoad() {
        this.dropTables = getSet("drop_tables", SimpleDropTable.class, new LinkedHashSet<>());
        this.potionEffects = getSet("potion_effects", SimplePotionEffect.class, new LinkedHashSet<>());
        List<SimpleBossSkill> skills = getList("skills", SimpleBossSkill.class);
        for (SimpleBossSkill sk : skills){
            this.skills.put(sk.getId(), sk);
        }
    }

    @Override
    public String toString() {
        return "SimpleBoss{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", health=" + health +
                ", equipment=" + equipment +
                ", dropTables=" + dropTables +
                ", potionEffects=" + potionEffects +
                ", skills=" + skills +
                ", hasVanillaDrops=" + hasVanillaDrops +
                ", hasEquipmentDrops=" + hasEquipmentDrops +
                ", experienceDrop=" + experienceDrop +
                ", passenger='" + passenger + '\'' +
                ", isBaby=" + isBaby +
                ", isRemovedWhenFarAway=" + isRemovedWhenFarAway +
                ", isBurnOnSunlight=" + isBurnOnSunlight +
                ", isGlowing=" + isGlowing +
                ", glowColor=" + glowColor +
                ", taskDelay=" + taskDelay +
                ", taskPeriod=" + taskPeriod +
                ", spawnerData=" + spawnerData +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj)
                && obj instanceof SimpleBoss
                && ((SimpleBoss) obj).getId().equals(this.getId());
    }

    @Override
    public int hashCode() {
        return this.getId().hashCode();
    }
}