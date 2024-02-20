package org.mineacademy.fo.boss;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.model.StringSerializable;

/**
 * Potion effect wrapper that can be easily serialized to a full-properties string.
 * Contains a new 'equals' check, so now the same effects are equal to each other.
 *
 * @author Rubix327
 */
@Getter
@Setter
@AllArgsConstructor
public final class SimplePotionEffect implements StringSerializable {

    /**
     * The type of the potion.
     */
    private final PotionEffectType type;
    /**
     * The duration (in ticks) that this effect will run for when applied to a LivingEntity.
     */
    private final int duration;
    /**
     * The amplifier of this effect. A higher amplifier means the potion effect
     * happens more often over its duration and in some cases has more effect on its target.
     */
    private final int amplifier;
    /**
     * Makes potion effect produce more, translucent, particles.
     */
    private final boolean ambient;
    /**
     * Whether this effect has particles or not.
     */
    private final boolean particles;
    /**
     * Whether this effect has an icon or not.
     */
    private final boolean icon;

    public SimplePotionEffect(PotionEffect effect) {
        this.type = effect.getType();
        this.duration = effect.getDuration();
        this.amplifier = effect.getAmplifier();
        this.ambient = effect.isAmbient();
        this.particles = effect.hasParticles();
        this.icon = effect.hasIcon();
    }

    /**
     * Make a new potion effect out of this instance.
     * @return the new potion effect
     */
    public PotionEffect toPotionEffect(){
        return new PotionEffect(type, duration, amplifier, ambient, particles, icon);
    }

    @Override
    @NotNull
    public String serialize(){
        return type.getName() + " " + duration + " " + amplifier + " " + ambient + " " + particles + " " + icon;
    }

    public static SimplePotionEffect deserialize(String s){
        String[] w = s.split(" ");
        return new SimplePotionEffect(
                PotionEffectType.getByName(w[0]), Integer.parseInt(w[1]),
                Integer.parseInt(w[2]), Boolean.parseBoolean(w[3]),
                Boolean.parseBoolean(w[4]), Boolean.parseBoolean(w[5]));
    }

    @Override
    public String toString() {
        return "SimplePotionEffect{" +
                "type=" + type +
                ", duration=" + duration +
                ", amplifier=" + amplifier +
                ", ambient=" + ambient +
                ", particles=" + particles +
                ", icon=" + icon +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimplePotionEffect that = (SimplePotionEffect) o;

        if (duration != that.duration) return false;
        if (amplifier != that.amplifier) return false;
        if (ambient != that.ambient) return false;
        if (particles != that.particles) return false;
        if (icon != that.icon) return false;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + duration;
        result = 31 * result + amplifier;
        result = 31 * result + (ambient ? 1 : 0);
        result = 31 * result + (particles ? 1 : 0);
        result = 31 * result + (icon ? 1 : 0);
        return result;
    }
}
