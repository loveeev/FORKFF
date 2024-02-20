package org.mineacademy.fo.enchant;

import lombok.Getter;
import org.bukkit.enchantments.Enchantment;
import org.mineacademy.fo.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An easy way to represent checks results on enchanting.
 *
 * @since 6.2.5.4
 * @author Rubix327
 */
public class EnchantStatus {

    /**
     * The enchantment has passed all checks and can be applied to the item.
     */
    public static EnchantStatus ALLOWED = new EnchantStatus(0, true);
    /**
     * The enchantment is conflicting with some other enchantments on the item.
     * @see SimpleEnchantment#conflictsWith(Enchantment)
     */
    public static EnchantStatus CONFLICT = new EnchantStatus(1, true);
    /**
     * The enchantment is not suitable for this type of item.
     * @see SimpleEnchantment#getCustomItemTarget()
     * @see SimpleEnchantmentTarget
     */
    public static EnchantStatus NOT_IN_ITEM_TARGET = new EnchantStatus(2, true);
    /**
     * The enchantment is not suitable for this item material.
     * @see SimpleEnchantment#enchantMaterial()
     */
    public static EnchantStatus NOT_IN_MATERIAL = new EnchantStatus(3, true);
    /**
     * The enchantment is not suitable for this item material.
     * @see SimpleEnchantment#enchantMaterials()
     */
    public static EnchantStatus NOT_IN_MATERIALS = new EnchantStatus(4, true);
    /**
     * The given level of the enchantment is higher than the maximum level.
     * @see SimpleEnchantment#getMaxLevel()
     */
    public static EnchantStatus LEVEL_TOO_HIGH = new EnchantStatus(5, true);
    /**
     * The item already has the same enchantment with the same level.
     */
    public static EnchantStatus ALREADY_ENCHANTED = new EnchantStatus(6, true);

    /**
     * The codes used by the user.<br>
     * Does not contain system's used codes.
     */
    public static List<Integer> usedCodes = new ArrayList<>();

    @Getter
    private final int code;
    @Getter
    private final boolean builtin;

    /**
     * The constructor with the automatic code generation.
     */
    public EnchantStatus() {
        this(usedCodes.isEmpty() ? 10 : Collections.max(usedCodes) + 1);
    }

    /**
     * @param code the unique code of the status. Must be greater than 9. Must not be repeated.
     */
    public EnchantStatus(int code) {
        if (code >= 0 && code <= 9){
            Logger.printErrors("EnchantStatus codes from 0 to 9 are reserved for the system.",
                    "Please choose a code greater than 9 or use no-args constructor.");
            throw new RuntimeException("EnchantStatus codes from 0 to 9 are reserved for the system.");
        }
        if (usedCodes.contains(code)){
            Logger.printErrors("EnchantStatus code " + code + " is already taken by another status.",
                    "Please choose another code for your EnchantStatus.");
            throw new RuntimeException("EnchantStatus code " + code + " is already taken by another status.");
        }

        usedCodes.add(code);
        this.code = code;
        this.builtin = false;
    }

    private EnchantStatus(int code, boolean builtin){
        this.code = code;
        this.builtin = builtin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnchantStatus that = (EnchantStatus) o;

        return code == that.code;
    }

    @Override
    public int hashCode() {
        return code;
    }
}
