package org.mineacademy.fo.annotation;

import org.mineacademy.fo.boss.SimpleBoss;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Place this annotation over any of the following classes to make Foundation
 * automatically register it when the plugin starts, and properly reload it.<br><br>
 * All classes must be final to be auto-registered.<br>
 * Some classes must have a public no-args constructor or to be a singleton to be auto-registered.<br>
 * <br>
 * To set the correct order of registering, use {@link #priority} parameter.<br>
 * For example, SimpleBossSkills are required to be registered before SimpleBosses!<br><br>
 * Supported classes:
 * <ul>
 * <li><b>SimpleListener</b></li>
 * - registers all events in the class<br>
 * - ex.: <i>registerEvents(MySimpleListener.getInstance())</i>
 * <li><b>PacketListener</b></li>
 * - automatically calls <i>onRegister</i> method for your packet listener<br>
 * - ex.: <i>MyPacketListener.getInstance().onRegister()</i>
 * <li><b>BungeeListener</b></li>
 * - registers a simple bungee class as a custom BungeeCord listener<br>
 * - ex.: <i>registerBungeeCord(MyBungeeCordListener.getInstance())</i>
 * <li><b>DiscordListener</b></li>
 * - automatically registered in its constructor, requires to be a singleton
 * <li><b>SimpleCommand</b></li>
 * - registers a command<br>
 * - ex.: <i>registerCommand(cmd)</i>
 * <li><b>SimpleCommandGroup</b></li>
 * - registers a command group<br>
 * - ex.: <i>registerCommands(group)</i>
 * <li><b>SimpleExpansion</b></li>
 * - registers a simple expansion<br>
 * - ex.: <i>Variables.addExpansion(MyExpansion.getInstance())</i>
 * <li><b>SimpleCraft</b></li>
 * - adds a new recipe to the server<br>
 * - ex.: <i>MyCraft.getInstance().register()</i>
 * <li><b>SimpleBoss</b></li>
 * - saves a boss to a file if {@link SimpleBoss#isSaveEnabled()} is true<br>
 * - bosses themselves are registered automatically in the constructor<br>
 * - ex.: <i>MyBoss.getInstance().save()</i>
 * <li><b>SimpleBossSkill</b></li>
 * - registers a skill<br>
 * - ex.: <i>mySkill.register()</i><br>
 * - <b>[!]</b> required to be loaded before SimpleBosses, use 'priority' parameter
 * <li><b>YamlConfig</b></li>
 * - loads a config when the plugin starts, and reloads it properly<br>
 * - ex.: <i>MyConfig.getInstance().save()</i>
 * <li><b>any class that 'implements Listener'</b></li>
 * - registers the listener class<br>
 * - ex.: <i>registerEvents(new MyListener())</i>
 * </ul>
 *
 * In addition, the following classes will self-register automatically<br>
 * regardless if you place this annotation on them or not:
 * <ul>
 * <li><b>Tool</b> (and its derivatives such as Rocket)</li>
 * <li><b>SimpleEnchantment</b></li>
 * </ul>
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface AutoRegister {

	/**
	 * When false, we won't print console warnings such as that registration failed
	 * because the server runs outdated MC version (example: SimpleEnchantment) or lacks
	 * necessary plugins to be hooked into (example: DiscordListener, PacketListener)
	 *
	 * @return default = false
	 */
	boolean hideIncompatibilityWarnings() default false;

	/**
	 * The higher the priority, the earlier the class will be loaded and registered.<br>
	 * Use it to determine which classes must be registered first.<br><br>
	 * For example, SimpleBossSkills classes must be registered before SimpleBosses,
	 * that's why SimpleBossSkills' priorities must be higher.
	 * @return default = 0
	 * @author Rubix327
	 */
	int priority() default 0;
}
