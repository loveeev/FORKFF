package org.mineacademy.fo.plugin;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.mineacademy.fo.*;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.boss.SimpleBossSkill;
import org.mineacademy.fo.bungee.BungeeListener;
import org.mineacademy.fo.command.SimpleCommand;
import org.mineacademy.fo.command.SimpleCommandGroup;
import org.mineacademy.fo.craft.CraftingHandler;
import org.mineacademy.fo.craft.SimpleCraft;
import org.mineacademy.fo.enchant.FoundationEnchantmentListener;
import org.mineacademy.fo.enchant.SimpleEnchantment;
import org.mineacademy.fo.event.SimpleListener;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.menu.tool.Tool;
import org.mineacademy.fo.model.*;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.SimpleLocalization;
import org.mineacademy.fo.settings.SimpleSettings;
import org.mineacademy.fo.settings.YamlConfig;
import org.mineacademy.fo.settings.YamlStaticConfig;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Utilizes \@AutoRegister annotation to add auto registration support for commands, events and much more.
 */
final class AutoRegisterScanner {

	/**
	 * Prevent duplicating registering of our {@link EnchantmentPacketListener}
	 */
	private static boolean enchantListenersRegistered = false;

	/**
	 * Prevents overriding {@link BungeeListener} in case of having multiple
	 */
	private static boolean bungeeListenerRegistered = false;

	/**
	 * Automatically register the main command group if there is only one in the code
	 */
	private static List<SimpleCommandGroup> registeredCommandGroups = new ArrayList<>();

	/**
	 * Scans your plugin and if your {@link Tool} or {@link SimpleEnchantment} class implements {@link Listener}
	 * and has "instance" method to be a singleton, your events are registered there automatically
	 * <p>
	 * If not, we only call the instance constructor in case there is any underlying registration going on
	 */
	public static void scanAndRegister() {

		// Reset
		enchantListenersRegistered = false;
		bungeeListenerRegistered = false;
		registeredCommandGroups.clear();

		// Find all plugin classes that can be autoregistered
		final List<Class<?>> classes = findValidClasses();

		// Register settings early to be used later
		registerSettings(classes);

		for (final Class<?> clazz : classes)
			try {

				// Prevent beginner programmer mistake of forgetting to implement listener
				try {
					for (final Method method : clazz.getMethods())
						if (method.isAnnotationPresent(EventHandler.class))
							Valid.checkBoolean(Listener.class.isAssignableFrom(clazz), "Detected @EventHandler in " + clazz + ", make this class 'implements Listener' before using events there");

				} catch (final Error err) {
					// Ignore, likely caused by missing plugins
				}

				// Handled above
				if (YamlStaticConfig.class.isAssignableFrom(clazz))
					continue;

				// Auto register classes
				final AutoRegister autoRegister = clazz.getAnnotation(AutoRegister.class);

				// Classes that should be auto registered without the annotation
				boolean noAnnotationRequired = false;
				List<Class<?>> registeredWithNoAnnotation = Arrays.asList(
						Tool.class, SimpleEnchantment.class,
						BungeeListener.class, SimpleExpansion.class,
						PacketListener.class, DiscordListener.class
				);
				for (Class<?> cl : registeredWithNoAnnotation){
					if (cl.isAssignableFrom(clazz)){
						noAnnotationRequired = true;
					}
				}

				// Require our annotation to be used, or support legacy classes from Foundation 5
				if (autoRegister != null || noAnnotationRequired) {

					if (!Modifier.isFinal(clazz.getModifiers())){
						Logger.error(new FoException("Non final class is attempted to be auto-registered!"),
								"Please make " + clazz + " final for it to be registered automatically (or via @AutoRegister)");
						continue;
					}

					try {
						autoRegister(clazz, (autoRegister == null || !autoRegister.hideIncompatibilityWarnings()) && !SimpleSettings.HIDE_INCOMPATIBILITY_WARNINGS);

					} catch (final NoClassDefFoundError | NoSuchFieldError ex) {
						final String error = Common.getOrEmpty(ex.getMessage());
						if (ex instanceof NoClassDefFoundError) {
							if (error.contains("org/bukkit/entity")){
								Bukkit.getLogger().warning("**** WARNING ****");

								if (error.contains("DragonFireball"))
									Bukkit.getLogger().warning("Your Minecraft version does not have DragonFireball class, we suggest replacing it with a Fireball instead in: " + clazz);
								else
									Bukkit.getLogger().warning("Your Minecraft version does not have " + error + " class you call in: " + clazz);
							}
							else if (error.equals("org/bukkit/inventory/RecipeChoice")){
								Bukkit.getLogger().warning("[" + SimplePlugin.getNamed() + "]" +
										" Recipe from custom craft " + clazz.getSimpleName() +
										" is not supported on your server version because is made with Foundation" +
										" (Spigot API 1.13.1+). Please ask the developer to update the plugin for" +
										" your specific version.");
								continue;
							}
						}
						Bukkit.getLogger().warning("Failed to auto register " + clazz + " due to it requesting missing fields/classes: " + ex.getMessage());

					} catch (final Throwable t) {
						Common.error(t, "Failed to auto register class " + clazz);
					}
				}

			} catch (final Throwable t) {

				// Ignore exception in other class we loaded
				if (t instanceof VerifyError)
					continue;

				Common.error(t, "Failed to scan class '" + clazz + "' using Foundation!");
			}

		// Register command groups later
		registerCommandGroups();
	}

	/*
	 * Registers settings and localization classes, either automatically if
	 * a class is detected, or forced if settings/localization files are found
	 */
	private static void registerSettings(List<Class<?>> classes) {
		final List<Class<?>> staticSettingsFound = new ArrayList<>();
		final List<Class<?>> staticLocalizations = new ArrayList<>();
		final List<Class<?>> staticCustom = new ArrayList<>();

		for (final Class<?> clazz : classes) {
			boolean load = false;

			if (clazz == SimpleLocalization.class || clazz == SimpleSettings.class || clazz == YamlStaticConfig.class)
				continue;

			if (SimpleSettings.class.isAssignableFrom(clazz)) {
				staticSettingsFound.add(clazz);

				load = true;
			}

			if (SimpleLocalization.class.isAssignableFrom(clazz)) {
				staticLocalizations.add(clazz);

				load = true;
			}

			if (load || YamlStaticConfig.class.isAssignableFrom(clazz))
				staticCustom.add(clazz);
		}

		boolean staticSettingsFileExist = false;
		boolean staticLocalizationFileExist = false;

		try (final JarFile jarFile = new JarFile(SimplePlugin.getSource())) {
			for (final Enumeration<JarEntry> it = jarFile.entries(); it.hasMoreElements();) {
				final JarEntry type = it.nextElement();
				final String name = type.getName();

				if (name.matches("settings\\.yml"))
					staticSettingsFileExist = true;

				else if (name.matches("localization\\/messages\\_(.*)\\.yml"))
					staticLocalizationFileExist = true;
			}
		} catch (final IOException ex) {
		}

		Valid.checkBoolean(staticSettingsFound.size() < 2, "Cannot have more than one class extend SimpleSettings: " + staticSettingsFound);
		Valid.checkBoolean(staticLocalizations.size() < 2, "Cannot have more than one class extend SimpleLocalization: " + staticLocalizations);

		if (staticSettingsFound.isEmpty() && staticSettingsFileExist)
			YamlStaticConfig.load(SimpleSettings.class);

		if (staticLocalizations.isEmpty() && staticLocalizationFileExist)
			YamlStaticConfig.load(SimpleLocalization.class);

		// A dirty solution to prioritize loading settings and then localization
		final List<Class<?>> delayedLoading = new ArrayList<>();

		for (final Class<?> customSettings : staticCustom)
			if (SimpleSettings.class.isAssignableFrom(customSettings)) {
				YamlStaticConfig.load((Class<? extends YamlStaticConfig>) customSettings);
			}
			else {
				delayedLoading.add(customSettings);
			}

		for (final Class<?> delayedSettings : delayedLoading) {
			YamlStaticConfig.load((Class<? extends YamlStaticConfig>) delayedSettings);
		}
	}

	/*
	 * Registers command groups, automatically assuming the main command group from the main command label
	 */
	private static void registerCommandGroups() {
		boolean mainCommandGroupFound = false;

		for (final SimpleCommandGroup group : registeredCommandGroups) {

			// Register if main command or there is only one command group, then assume main
			if (group.getLabel().equals(SimpleSettings.MAIN_COMMAND_ALIASES.first()) || registeredCommandGroups.size() == 1) {
				Valid.checkBoolean(!mainCommandGroupFound, "Found 2 or more command groups that do not specify label in their constructor."
						+ " (We can only automatically use one of such groups as the main one using Command_Aliases as command label(s)"
						+ " from settings.yml but not more.");

				SimplePlugin.getInstance().setMainCommand(group);
				mainCommandGroupFound = true;
			}

			SimplePlugin.getInstance().registerCommands(group);
		}
	}

	/*
	 * Automatically registers the given class, printing console warnings
	 */
	private static void autoRegister(Class<?> clazz, boolean printWarnings) {

		// Special case: Prevent class init error
		if (SimpleEnchantment.class.isAssignableFrom(clazz) && MinecraftVersion.olderThan(V.v1_13)) {
			if (printWarnings) {
				Bukkit.getLogger().warning("**** WARNING ****");
				Bukkit.getLogger().warning("SimpleEnchantment requires Minecraft 1.13.2 or greater. The following class will not be registered: " + clazz.getName()
						+ ". To hide this message, put @AutoRegister(hideIncompatibilityWarnings=true) over the class.");
			}

			return;
		}

		if (DiscordListener.class.isAssignableFrom(clazz) && !HookManager.isDiscordSRVLoaded()) {
			if (printWarnings) {
				Bukkit.getLogger().warning("**** WARNING ****");
				Bukkit.getLogger().warning("The following class requires DiscordSRV and won't be registered: " + clazz.getName()
						+ ". To hide this message, put @AutoRegister(hideIncompatibilityWarnings=true) over the class.");
			}

			return;
		}

		if (PacketListener.class.isAssignableFrom(clazz) && !HookManager.isProtocolLibLoaded()) {
			if (printWarnings) {
				Logger.printErrors("**** WARNING ****",
						"The following class requires ProtocolLib and won't be registered: " + clazz.getName(),
						". To hide this message, put @AutoRegister(hideIncompatibilityWarnings=true) over the class.");
			}

			return;
		}

		final SimplePlugin plugin = SimplePlugin.getInstance();
		final Tuple<InstanceType, Object> tuple = findInstance(clazz);

		final InstanceType mode = tuple.getKey();
		final Object instance = tuple.getValue();

		boolean eventsRegistered = false;

		if (SimpleListener.class.isAssignableFrom(clazz)) {
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

			plugin.registerEvents((SimpleListener<?>) instance);
			eventsRegistered = true;
		}

		else if (BungeeListener.class.isAssignableFrom(clazz)) {
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

			if (!bungeeListenerRegistered) {
				bungeeListenerRegistered = true;

				plugin.setBungeeCord((BungeeListener) instance);
			}

			plugin.registerBungeeCord((BungeeListener) instance);
			eventsRegistered = true;
		}

		else if (SimpleCommand.class.isAssignableFrom(clazz)){
			plugin.registerCommand((SimpleCommand) instance);
		}

		else if (SimpleCommandGroup.class.isAssignableFrom(clazz)) {
			final SimpleCommandGroup group = (SimpleCommandGroup) instance;

			// Special case, do it at the end
			registeredCommandGroups.add(group);
		}

		else if (SimpleExpansion.class.isAssignableFrom(clazz)) {
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

			Variables.addExpansion((SimpleExpansion) instance);
		}

		else if (YamlConfig.class.isAssignableFrom(clazz)) {

			// Automatically called onLoadFinish when getting instance
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

			if (SimplePlugin.isReloading()) {
				((YamlConfig) instance).save();
				((YamlConfig) instance).reload();
			}
		}

		else if (PacketListener.class.isAssignableFrom(clazz)) {

			// Automatically registered by means of adding packet adapters
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

			((PacketListener) instance).onRegister();
		}

		else if (DiscordListener.class.isAssignableFrom(clazz))
			// Automatically registered in its constructor
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

		else if (SimpleEnchantment.class.isAssignableFrom(clazz)) {

			// Automatically registered in its constructor
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

			if (!enchantListenersRegistered) {
				enchantListenersRegistered = true;

				plugin.registerEvents(FoundationEnchantmentListener.getInstance());
			}
		}

		else if (SimpleCraft.class.isAssignableFrom(clazz)){
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);
			CraftingHandler.register((SimpleCraft<? extends Recipe>) instance);
		}

		else if (SimpleBossSkill.class.isAssignableFrom(clazz)){
			enforceModeFor(clazz, mode, InstanceType.NEW_FROM_CONSTRUCTOR);
			((SimpleBossSkill) instance).register();
		}

		else if (Tool.class.isAssignableFrom(clazz))
			// Automatically registered in its constructor
			enforceModeFor(clazz, mode, InstanceType.SINGLETON);

		else if (instance instanceof Listener) {
			// Pass-through to register events later
		}

		else {
			throw new FoException("@AutoRegister cannot be used on " + clazz);
		}

		// Register events if needed
		if (!eventsRegistered && instance instanceof Listener) {
			plugin.registerEvents((Listener) instance);
		}
	}

	/*
	 * Compiles valid classes from our plugin that can be autoregistered
	 */
	private static List<Class<?>> findValidClasses() {
		final List<Class<?>> classes = new ArrayList<>();
		final HashMap<Class<?>, Integer> priorities = new HashMap<>();

		// Ignore anonymous inner classes
		final Pattern anonymousClassPattern = Pattern.compile("\\w+\\$[0-9]$");

		try (final JarFile file = new JarFile(SimplePlugin.getSource())) {
			for (final Enumeration<JarEntry> entry = file.entries(); entry.hasMoreElements();) {
				final JarEntry jar = entry.nextElement();
				final String name = jar.getName().replace("/", ".");

				// Ignore files such as settings.yml
				if (!name.endsWith(".class"))
					continue;

				final String className = name.substring(0, name.length() - 6);
				Class<?> clazz = null;

				// Look up the Java class, silently ignore if failing
				try {
					clazz = SimplePlugin.class.getClassLoader().loadClass(className);

				} catch (final ClassFormatError | VerifyError | NoClassDefFoundError | ClassNotFoundException | IncompatibleClassChangeError error) {
					continue;
				}

				// Ignore abstract or anonymous classes
				if (Modifier.isAbstract(clazz.getModifiers()) || anonymousClassPattern.matcher(className).find()) continue;

				AutoRegister ann = clazz.getAnnotation(AutoRegister.class);
				int priority = 0;
				if (ann != null){
					priority = ann.priority();
				}
				priorities.put(clazz, priority);
			}

		} catch (final Throwable t) {
			Remain.sneaky(t);
		}

		// Sort the list according to the priorities
		List<Map.Entry<Class<?>, Integer>> list = new ArrayList<>(priorities.entrySet());
		list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
		for (Map.Entry<Class<?>, Integer> entry : list) {
			classes.add(entry.getKey());
		}

		return classes;
	}

	/*
	 * Tries to return instance of the given class, either by returning its singleton
	 * or creating a new instance from a constructor
	 */
	private static Tuple<InstanceType, Object> findInstance(Class<?> clazz) {
		final Constructor<?> constructor;

		Object instance = null;
		InstanceType mode = null;

		try{
			constructor = clazz.getDeclaredConstructor();
			final int modifiers = constructor.getModifiers();

			// Case 1: Public constructor
			if (Modifier.isPublic(modifiers)) {
				instance = ReflectionUtil.instantiate(constructor);
				mode = InstanceType.NEW_FROM_CONSTRUCTOR;
			}

			// Case 2: Singleton
			// Requires private constructor and 'private static final Class instance' field
			else if (Modifier.isPrivate(modifiers)) {
				List<Field> suitable = new ArrayList<>();
				Field instanceField = null;

				for (final Field field : clazz.getDeclaredFields()) {
					final int fieldMods = field.getModifiers();

					if (Modifier.isPrivate(fieldMods) && Modifier.isStatic(fieldMods) && (Modifier.isFinal(fieldMods)
							|| Modifier.isVolatile(fieldMods))){
						suitable.add(field);
					}
				}

				if (suitable.size() == 1){
					instanceField = suitable.get(0);
				} else {
					for (Field field : suitable){
						if (field.getName().equals("instance")){
							instanceField = field;
						}
					}
					if (instanceField == null && suitable.size() != 0){
						Logger.printErrors(
								"PROBLEM:",
								"Your " + clazz + " (using @AutoRegister) contains several",
								"'private static final' fields that all suits to be a singleton instance.",
								"",
								"SOLUTION:",
								"Please make one field called 'instance' like this:",
								"'private static final " + clazz.getSimpleName() + " instance = new " + clazz.getSimpleName() + "();'");
						throw new FoException("Found multiple fields that claim to be a singleton. Make only one OR call one of them 'instance'.");
					}
				}

				if (instanceField != null) {
					instance = ReflectionUtil.getFieldContent(instanceField, (Object) null);
					mode = InstanceType.SINGLETON;
				}
			}

		} catch (NoSuchMethodException e){
			Logger.printErrors("Your " + clazz + " using @AutoRegister must EITHER have ",
					"1) one public no arguments constructor OR ",
					"2) one private no arguments constructor and a 'private static final "
					+ clazz.getSimpleName() + " instance' field.");
			throw new FoException("Class " + clazz + " using @AutoRegister does not have any no-arguments constructor.");
		}

		return new Tuple<>(mode, instance);
	}

	/*
	 * Checks if the way the given class can be made a new instance of, correspond with the required way
	 */
	private static void enforceModeFor(Class<?> clazz, InstanceType actual, InstanceType required) {
		Valid.checkBoolean(required == actual, clazz + " using @AutoRegister must have " + (required == InstanceType.NEW_FROM_CONSTRUCTOR ? "a single public no args constructor"
				: "one private no args constructor and a 'private static final " + clazz.getSimpleName() + " instance' field to be a singleton"));
	}

	/*
	 * How a new instance can be made to autoregister
	 */
	enum InstanceType {
		NEW_FROM_CONSTRUCTOR,
		SINGLETON
	}
}
