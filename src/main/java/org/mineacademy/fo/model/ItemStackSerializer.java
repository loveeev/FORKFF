package org.mineacademy.fo.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.mineacademy.fo.annotation.AutoSerialize;
import org.mineacademy.fo.boss.SimpleBossEquipment;
import org.mineacademy.fo.boss.SimpleDropTable;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompMetadata;
import org.mineacademy.fo.remain.nbt.NBTCompound;
import org.mineacademy.fo.remain.nbt.NBTItem;

import java.util.*;

/**
 * This class represents an ItemStack wrapper for beautiful serialization to a file.
 * @author Rubix327
 */
@Getter
@AutoSerialize
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemStackSerializer implements AutoSerializable{

    private Material type = Material.AIR;
    private int amount = 0;
    private String displayName;
    private String localizedName;
    private List<String> lore;
    private Integer customModelData;
    private Boolean unbreakable;
    private Map<Enchantment, Integer> enchants;
    private Set<ItemFlag> flags;
    private List<String> attributes;
    /**
     * This only stores metadata from Foundation.
     */
    private Map<String, String> metadata;
    /**
     * Additional parameter - chance of dropping the item for events like EntityDeathEvent.<br>
     * Left on <code>null</code> if you don't want to use it.<br>
     * Used in Foundation in {@link SimpleBossEquipment} and {@link SimpleDropTable}.<br><br>
     * <b>This parameter is not saved on the item after converting it into an ItemStack!</b>
     */
    private Float dropChance;

    public ItemStackSerializer(ItemStack item){
        this(item, null);
    }

    public ItemStackSerializer(ItemStack item, Float dropChance){
        this.dropChance = dropChance;

        // Basic parameters
        this.type = item.getType();
        this.amount = item.getAmount();

        // Item Meta
        ItemMeta meta = item.getItemMeta();
        if (item.hasItemMeta() && meta != null){
            if (meta.hasDisplayName()){
                this.displayName = meta.getDisplayName();
            }
            if (meta.hasLocalizedName()){
                this.localizedName = meta.getLocalizedName();
            }
            if (meta.hasLore()){
                this.lore = meta.getLore();
            }
            if (meta.hasCustomModelData()){
                this.customModelData = meta.getCustomModelData();
            }
            if (meta.getEnchants().size() > 0){
                this.enchants = meta.getEnchants();
            }
            if (meta.getItemFlags().size() > 0){
                this.flags = meta.getItemFlags();
            }
            this.unbreakable = meta.isUnbreakable() ? true : null;
            if (meta.getAttributeModifiers() != null && meta.getAttributeModifiers().size() > 0){
                if (attributes == null){
                    attributes = new ArrayList<>();
                }
                for (Map.Entry<Attribute, AttributeModifier> attr : meta.getAttributeModifiers().entries()){
                    attributes.add(serializeAttributeModifier(attr.getKey(), attr.getValue()));
                }
            }
        }

        // Metadata by Foundation
        if (!CompMaterial.isAir(item.getType())){
            final String compoundTag = FoConstants.NBT.TAG;
            final NBTItem nbt = new NBTItem(item);
            NBTCompound compound = nbt.getCompound(compoundTag);
            if (compound != null){
                Set<String> keys = compound.getKeys();
                if (keys.size() != 0 && this.metadata == null){
                    this.metadata = new HashMap<>();
                }
                for (String key : keys){
                    this.metadata.put(key, compound.getString(key));
                }
            }
        }
    }

    public ItemStack toItemStack(){
        // Basic parameters
        ItemStack item = new ItemStack(this.type, this.amount);

        // Item meta
        ItemMeta meta = item.getItemMeta();
        if (meta != null){
            meta.setDisplayName(this.displayName);
            meta.setLocalizedName(this.localizedName);
            meta.setLore(this.lore);
            meta.setCustomModelData(this.customModelData);
            meta.setUnbreakable(this.unbreakable != null && this.unbreakable);
            // Enchantments
            if (this.enchants != null){
                for (Map.Entry<Enchantment, Integer> ench : this.enchants.entrySet()){
                    meta.addEnchant(ench.getKey(), ench.getValue(), true);
                }
            }
            // ItemFlags
            if (this.flags != null){
                for (ItemFlag flag : this.flags){
                    meta.addItemFlags(flag);
                }
            }
            // Attributes
            if (this.attributes != null){
                for (String line : attributes){
                    meta.addAttributeModifier(deserializeAttribute(line), deserializeModifier(line));
                }
            }
            // Metadata by Foundation
            if (this.metadata != null){
                for (Map.Entry<String, String> data : this.metadata.entrySet()){
                    CompMetadata.setMetadata(item, data.getKey(), data.getValue());
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    public static String serializeAttributeModifier(Attribute attribute, AttributeModifier mod){
        return attribute + " " + mod.getAmount() + " "
                + mod.getOperation() + (mod.getSlot() == null ? "" : mod.getSlot().toString());
    }

    public static Attribute deserializeAttribute(String line){
        int index = line.indexOf(" ");
        return Attribute.valueOf(line.substring(0, index));
    }

    public static AttributeModifier deserializeModifier(String line){
        String[] arr = line.split(" ");
        UUID uuid = UUID.randomUUID();
        double amount = Double.parseDouble(arr[1]);
        AttributeModifier.Operation op = AttributeModifier.Operation.valueOf(arr[2]);
        EquipmentSlot slot = arr.length == 4 ? EquipmentSlot.valueOf(arr[3]) : null;
        if (slot == null){
            return new AttributeModifier(uuid, "", amount, op);
        } else {
            return new AttributeModifier(uuid, "", amount, op, slot);
        }
    }

    @Contract(pure = true)
    public static ItemStackSerializer empty(){
        return new ItemStackSerializer(new ItemStack(Material.AIR), null);
    }

    @Override
    public boolean loadEmptyCollections() {
        return false;
    }

    @Override
    public String toString() {
        return "ItemStackSerializer{" +
                "type=" + type +
                ", amount=" + amount +
                ", displayName='" + displayName + '\'' +
                ", localizedName='" + localizedName + '\'' +
                ", lore=" + lore +
                ", customModelData=" + customModelData +
                ", enchants=" + enchants +
                ", flags=" + flags +
                ", unbreakable=" + unbreakable +
                ", attributes=" + attributes +
                ", metadata=" + metadata +
                ", dropChance=" + dropChance +
                '}';
    }
}
