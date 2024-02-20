package org.mineacademy.fo.boss;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.annotation.AutoSerialize;
import org.mineacademy.fo.model.AutoSerializable;
import org.mineacademy.fo.model.ItemStackSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a container of items that a boss can drop on death.<br>
 * Every item in a table has its own chance to drop and the table itself
 * has the chance to be picked when a boss dies.
 *
 * @author Rubix327
 */
@Getter
@AutoSerialize
public final class SimpleDropTable implements AutoSerializable {

    private List<ItemStackSerializer> table = new ArrayList<>();
    private double chance = 50;

    public SimpleDropTable(){
    }

    public SimpleDropTable(List<ItemStackSerializer> table, double chance) {
        this.table = table;
        this.chance = chance;
    }

    public static Builder builder(){
        return new SimpleDropTable().new Builder();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public class Builder{
        public Builder add(ItemStack item){
            return add(item, 100);
        }

        public Builder add(ItemStack item, float chance){
            SimpleDropTable.this.table.add(new ItemStackSerializer(item, chance));

            return this;
        }

        public Builder add(Material mat){
            return add(mat, 100);
        }

        public Builder add(Material mat, float chance){
            SimpleDropTable.this.table.add(new ItemStackSerializer(new ItemStack(mat), chance));

            return this;
        }

        public Builder setChance(double chance){
            SimpleDropTable.this.chance = chance;

            return this;
        }

        public SimpleDropTable build(){
            return SimpleDropTable.this;
        }
    }

    @Override
    public String toString() {
        return "DropTable{" +
                "items=" + table +
                ", chance=" + chance +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleDropTable dropTable = (SimpleDropTable) o;

        if (Double.compare(dropTable.chance, chance) != 0) return false;
        return table.equals(dropTable.table);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = table.hashCode();
        temp = Double.doubleToLongBits(chance);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
