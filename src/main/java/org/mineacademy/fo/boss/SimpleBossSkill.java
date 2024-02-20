package org.mineacademy.fo.boss;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.Logger;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.menu.AdvancedMenu;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.CompMaterial;

import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a SimpleBoss' skill container.<br>
 * You can listen to any events here and handle them as you wish.<br>
 * To add a skill to a boss, use {@link SimpleBoss#addSkill(SimpleBossSkill)}.<br><br>
 * Don't forget to register the skill using <i>register</i> method or <i>@AutoRegister(priority=1)</i>.
 *
 * @author Rubix327
 */
@Getter
@Setter
public abstract class SimpleBossSkill implements Listener, ConfigSerializable {
    @Getter
    protected final static Map<String, Class<? extends SimpleBossSkill>> classes = new HashMap<>();

    /**
     * The name of the skill that is shown in the game menu
     */
    protected final String name;
    /**
     * The description of the skill that is shown in the game menu
     */
    protected String description = "";

    public SimpleBossSkill(String name) {
        this.name = name;
    }

    public SimpleBossSkill(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * The id of the skill, used for saving purposes.<br>
     * Not related to the skill name and not shown in the game.
     */
    @NotNull
    public String getId(){
        return this.getClass().getSimpleName();
    }

    /**
     * Register the skill and all listeners inside.
     */
    public final void register(){
        Bukkit.getPluginManager().registerEvents(this, SimplePlugin.getInstance());
        classes.put(getId(), this.getClass());
    }

    /**
     * Check if the given entity is a boss and has this skill with any values.<br>
     * You can override it to make a more specific checks.
     * @param entity the entity
     * @return has skill (no properties are taken into account)
     */
    public boolean hasSkill(Entity entity){
        if (!(entity instanceof LivingEntity)) return false;
        SpawnedBoss boss = SpawnedBoss.get((LivingEntity) entity);
        return boss != null && boss.getBoss().getSkills().containsValue(this);
    }

    /**
     * Get the skill item which is shown in the menu.<br>
     * Default is Enchanted Book with the name of the skill.
     * @return the item
     */
    @NotNull
    public ItemStack getMenuItem(){
        return ItemCreator.of(CompMaterial.ENCHANTED_BOOK).name(getName()).make();
    }

    /**
     * Get the menu which should be opened when a player clicks on the skill button.<br>
     * Default is null.
     * @param player the player who clicked the button
     * @param boss the boss who has this skill
     * @return the menu to be opened
     */
    public AdvancedMenu getSettingsMenu(Player player, SimpleBoss boss){
        return null;
    }

    /**
     * Get buttons for each property (field) which value you can set in the menu.
     * The key is supposed to be used as a slot for the button.<br>
     * Default is empty HashMap.
     * @return the map of the buttons: <i>slot:button</i>
     */
    @NotNull
    public Map<Integer, Button> getSettingsButtons(){
        return new HashMap<>();
    }

    /**
     * Serialize 'id' parameter to the map.
     * <br><br>
     * Don't forget to add the <b><i>id</i></b> to the map
     * OR use <i>super.serialize()</i> to get the map
     * OR use <i>@AutoRegister(deep=true)</i>!
     * @return the map
     */
    @Override
    public SerializedMap serialize() {
        SerializedMap map = new SerializedMap();
        map.put("id", getId());
        return map;
    }

    /**
     * Deserialize the 'id' parameter and call child class' deserialization
     * based on the id of the child class to deserialize other properties.
     * @param map the map from the file
     * @return a new instance of specific SimpleBossSkill
     */
    public static SimpleBossSkill deserialize(SerializedMap map){
        String id = map.getString("id");
        if (id == null){
            Logger.printErrors(
                    "Parameter 'id' is missing in the Skill config section. ",
                    "For all of your skills, please: ",
                    "    use 'map.put(\"id\", getId())'",
                    " OR use 'map = super.serialize()' to get the map",
                    " OR use '@AutoSerialize(deep=true)' above the class!"
            );
            throw new NullPointerException("Parameter 'id' is missing in the Skill config section. " +
                    "Please add it to the map or use @AutoSerialize(deep=true).");
        }

        return deserialize(id, map);
    }

    public static SimpleBossSkill deserialize(String id, SerializedMap map){
        Class<? extends SimpleBossSkill> clazz = classes.get(id);
        if (clazz == null) {
            Logger.printErrors(
                    id + ": Skill with that name does not exist, or its class is not registered yet. ",
                    "Skills must be registered before any class where is used (e.g. SimpleBoss).",
                    "If you use @AutoRegister, please set the 'priority' parameter to higher value " +
                            "to load the skill before the boss class."
            );
            throw new NullPointerException("Skill does not exist or is not registered.");
        }

        return SerializeUtil.deserialize(SerializeUtil.Mode.YAML, clazz, map);
    }

    @Override
    public String toString() {
        return "SimpleBossSkill{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleBossSkill skill = (SimpleBossSkill) o;

        return getId().equals(skill.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
