package org.mineacademy.fo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can disable fields that you do not want to serialize/deserialize.<br><br>
 * <b>On class</b>:
 * <ul>
 * <li>Does nothing. Can only be used to indicate that this class is AutoSerialized.</li>
 * </ul>
 * <br>
 * <b>On field</b>:
 * <ul>
 * <li>Serializes and deserializes field if annotation is in enabled state</li>
 * <li>Disables auto-serializing and deserializing of the field if @AutoSerialize(value = false)</li>
 * </ul>
 * If you want to prevent one specific field from auto-serializing and auto-deserializing,
 * use <i>@AutoSerialize(false)</i> on that field.<br><br>
 * @author Rubix327
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoSerialize {

    /**
     * When false, automatic serializing and deserializing does not work for the field above which is set.
     */
    boolean value() default true;

}
