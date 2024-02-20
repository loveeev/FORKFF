package org.mineacademy.fo.annotation;

import com.google.common.base.CaseFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation automatically saves and loads fields between a file and your custom
 * class extending {@link org.mineacademy.fo.settings.YamlStaticConfig}.<br><br>
 * <b>On class</b>:
 * <ul>
 * <li>Saves and loads all static class fields</li>
 * <li>But skips fields that have disabled this feature by <i>@AutoStaticConfig(false)</i></li>
 * </ul>
 * When using on class, if you want to prevent one specific field from auto-loading and auto-saving,
 * use <i>@AutoStaticConfig(false)</i> on that field.<br><br>
 *
 * This annotation only works if is set above class. But you can disable fields separately.<br><br>
 *
 * <b>About Format:</b><br>
 * <ul>
 * <li>By default, format of fields is Capital_Underscore which corresponds to Foundation naming convention.</li>
 * <li>You can change format to your desired, but system fields (Version, Locale, etc.) will stay the same.</li>
 * </ul>
 * @author Rubix327
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoStaticConfig {

    /**
     * When false, automatic loading and saving does not work for class or field above which is set.
     */
    boolean value() default true;

    /**
     * In what format should we convert your fields to the file.<br>
     * Only usable if set on class. You can only set one format for one class.<br>
     * System fields (Version, Locale, etc.) will stay the same regardless of this value.<br>
     * Default: Capital_Underscore.
     */
    CaseFormat[] format() default {};

}
