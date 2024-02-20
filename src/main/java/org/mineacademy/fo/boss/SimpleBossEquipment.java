package org.mineacademy.fo.boss;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.model.ItemStackSerializer;
import org.mineacademy.fo.remain.CompMaterial;

/**
 * Represents equipment of a boss.<br>
 * Here you can set every item of armor, items in hands and their chances to drop.<br>
 * If you use no-args constructor, all items would be empty and the chances are 10% by default.
 *
 * @author Rubix327
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public final class SimpleBossEquipment implements ConfigSerializable {
    private ItemStack helmet = (new ItemStack(Material.AIR));
    private ItemStack chestplate = (new ItemStack(Material.AIR));
    private ItemStack leggings = (new ItemStack(Material.AIR));
    private ItemStack boots = (new ItemStack(Material.AIR));
    private ItemStack mainHand = (new ItemStack(Material.AIR));
    private ItemStack offHand = (new ItemStack(Material.AIR));
    private Float helmetDropChance;
    private Float chestplateDropChance;
    private Float leggingsDropChance;
    private Float bootsDropChance;
    private Float mainHandDropChance;
    private Float offHandDropChance;

    public SimpleBossEquipment(ItemStack[] items) {
        if (items.length != 6){
            throw new IllegalArgumentException("Simple Boss Equipment must consist of 6 items.");
        }

        this.helmet = new ItemStack(items[0]);
        this.chestplate = new ItemStack(items[1]);
        this.leggings = new ItemStack(items[2]);
        this.boots = new ItemStack(items[3]);
        this.mainHand = new ItemStack(items[4]);
        this.offHand = new ItemStack(items[5]);
    }

    public ItemStack[] getArmorContents(){
        return new ItemStack[]{
                boots,
                leggings,
                chestplate,
                helmet
        };
    }

    /**
     * Get the chance if it is set. If not, get the default Minecraft drop chance - 8.5%.
     * @param part the body part
     * @return chance or 0.085f
     */
    public float getChanceOrDefault(Part part){
        Float val = getUnsafeChance(part);
        return val == null ? 0.085f : val / 100;
    }

    /**
     * Get the drop chance of the given body part.<br>
     * May return null if not set. Use {@link #getChanceOrDefault(Part)} to get safe value.
     * @param part the body part
     * @return chance or null
     */
    public Float getUnsafeChance(Part part){
        switch (part){
            case HELMET: return getHelmetDropChance();
            case CHESTPLATE: return getChestplateDropChance();
            case LEGGINGS: return getLeggingsDropChance();
            case BOOTS: return getBootsDropChance();
            case MAIN_HAND: return getMainHandDropChance();
            case OFF_HAND: return getOffHandDropChance();
            default: return null;
        }
    }

    @Override
    public SerializedMap serialize() {
        SerializedMap map = new SerializedMap();

        if (!CompMaterial.isAir(helmet)){
            map.put("helmet", new ItemStackSerializer(helmet, helmetDropChance));
        }
        if (!CompMaterial.isAir(chestplate)){
            map.put("chestplate", new ItemStackSerializer(chestplate));
        }
        if (!CompMaterial.isAir(leggings)){
            map.put("leggings", new ItemStackSerializer(leggings));
        }
        if (!CompMaterial.isAir(boots)){
            map.put("boots", new ItemStackSerializer(boots));
        }
        if (!CompMaterial.isAir(mainHand)){
            map.put("main_hand", new ItemStackSerializer(mainHand));
        }
        if (!CompMaterial.isAir(offHand)){
            map.put("off_hand", new ItemStackSerializer(offHand));
        }

        return map;
    }

    public static SimpleBossEquipment deserialize(SerializedMap map){
        ItemStackSerializer helmet = getItem(map, "helmet");
        ItemStackSerializer chestplate = getItem(map, "chestplate");
        ItemStackSerializer leggings = getItem(map, "leggings");
        ItemStackSerializer boots = getItem(map, "boots");
        ItemStackSerializer mainHand = getItem(map, "main_hand");
        ItemStackSerializer offHand = getItem(map, "off_hand");

        Float helmetC = getChance(helmet);
        Float chestplateC = getChance(chestplate);
        Float leggingsC = getChance(leggings);
        Float bootsC = getChance(boots);
        Float mainHandC = getChance(mainHand);
        Float offHandC = getChance(offHand);
        return new SimpleBossEquipment(helmet.toItemStack(), chestplate.toItemStack(), leggings.toItemStack(),
                boots.toItemStack(), mainHand.toItemStack(), offHand.toItemStack(),
                helmetC, chestplateC, leggingsC, bootsC, mainHandC, offHandC);
    }

    private static ItemStackSerializer getItem(SerializedMap map, String path){
        return map.get(path, ItemStackSerializer.class, ItemStackSerializer.empty());
    }

    private static Float getChance(ItemStackSerializer item){
        return item == null ? null : item.getDropChance();
    }

    @Override
    public String toString() {
        return "SimpleBossEquipment{" +
                "helmet=" + helmet +
                ", chestplate=" + chestplate +
                ", leggings=" + leggings +
                ", boots=" + boots +
                ", mainHand=" + mainHand +
                ", offHand=" + offHand +
                ", helmetChance=" + helmetDropChance +
                ", chestplateChance=" + chestplateDropChance +
                ", leggingsChance=" + leggingsDropChance +
                ", bootsChance=" + bootsDropChance +
                ", mainHandChance=" + mainHandDropChance +
                ", offHandChance=" + offHandDropChance +
                '}';
    }

    public enum Part{
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        MAIN_HAND,
        OFF_HAND
    }
}
