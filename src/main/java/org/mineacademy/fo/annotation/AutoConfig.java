package org.mineacademy.fo.annotation;

import com.google.common.base.CaseFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation automatically saves and loads fields between a file and your custom
 * class extending {@link org.mineacademy.fo.settings.YamlConfig}.<br><br>
 * <b>On class</b>:
 * <ul>
 * <li>Saves and loads all non-static class fields</li>
 * <li>Saves and loads those static fields that have separately specified this annotation above themselves</li>
 * <li>But skips fields that have disabled this feature by <i>@AutoConfig(false)</i></li>
 * </ul>
 * When using on class, if you want to prevent one specific field from auto-loading and auto-saving,
 * use <i>@AutoConfig(false)</i> on that field.<br>
 * <br>
 * <b>On field</b>:
 * <ul>
 * <li>Saves and loads field if annotation is in enabled state</li>
 * <li>Skips field saving if <i>@AutoConfig(autoSave = false)</i></li>
 * <li>Skips field loading if <i>@AutoConfig(autoLoad = false)</i></li>
 * </ul>
 * @author Rubix327
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoConfig {

    /**
     * When false, automatic loading and saving does not work for class or field above which is set.
     */
    boolean value() default true;

    /**
     * When false, automatic loading does not work for class or field above which is set.<br>
     * You may manually get the disabled fields in <i>onLoad</i> method if you want.
     */
    boolean autoLoad() default true;

    /**
     * When false, automatic saving does not work for class or field above which is set.<br>
     * You may manually set the disabled fields in <i>onSave</i> method if you want.
     */
    boolean autoSave() default true;

    /**
     * If enabled, we scan a superclass of this class (if the superclass is not a YamlConfig)
     * and save & load its enabled, non-static fields to a file.<br><br>
     *
     * Example:
     * <ul>
     * <li>@AutoConfig(deep = <b>true</b>) <b><i>A</i></b> extends <b><i>B</i></b>;</li>
     * <li>@AutoConfig(deep = <b>true</b>) <b><i>B</i></b> extends <b><i>C;</li>
     * <li>@AutoConfig(deep = false) <b><i>C</i></b> extends <b><i>D</i></b>;</li>
     * <li>@AutoConfig(deep = false) <b><i>D</i></b> extends <b><i>E</i></b>;</li>
     * <li>@AutoConfig(deep = false) <b><i>E</i></b> extends <b><i>YamlConfig</i></b>.</li>
     * </ul>
     * If <b><i>loadConfiguration(...)</i></b> is started in class <b><i>A</i></b>,
     * this would scan <i><b>A, B, C</b></i> classes and save their fields.<br>
     * Class <b><i>D</i></b> would not be scanned because <b><i>C</i></b> has <b><i>deep</i></b> on <b><i>false</i></b>.<br>
     * Class <b><i>E</i></b> would not be scanned because <b><i>D</i></b> has <b><i>deep</i></b> on <b><i>false</i></b>.<br>
     * Next classes would not be scanned because <b><i>YamlConfig</i></b> and its superclasses are not scanned at all.
     *
     * @return is deep scanning enabled
     */
    boolean deep() default false;

    /**
     * In what format should we convert your fields to the file.<br>
     * Only usable if set on class. You can only set one format for one class.<br>
     * Default: lower_underscore.
     */
    CaseFormat format() default CaseFormat.LOWER_UNDERSCORE;

}