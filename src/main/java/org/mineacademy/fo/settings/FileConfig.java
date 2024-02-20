package org.mineacademy.fo.settings;

import com.google.common.base.CaseFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.*;
import org.mineacademy.fo.annotation.AutoConfig;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictList;
import org.mineacademy.fo.command.SimpleCommand;
import org.mineacademy.fo.command.SimpleCommandGroup;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.*;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;

/**
 * Represents any configuration that can be stored in a file
 */
public abstract class FileConfig {

	/**
	 * Used to synchronize loading/saving and forces multiple instances that all
	 * use the same file use the same content to set/save it.
	 */
	private static final Map<String, ConfigSection> loadedSections = new HashMap<>();

	/**
	 * Represents "null" which you can use as convenience shortcut in loading config
	 * that has no internal from path.
	 */
	protected static final String NO_DEFAULT = null;

	/**
	 * Switches how you'd like to store the content of the settings file. Defaults to YAML.
	 *
	 * TODO Implement JSON settings storage
	 */
	final SerializeUtil.Mode mode = SerializeUtil.Mode.YAML;

	/*
	 * The file that is being used
	 */
	@Nullable
	@Setter(value = AccessLevel.PROTECTED)
	File file;

	/*
	 * The main config section, overridden in load(File)
	 */
	ConfigSection section = new ConfigSection();

	/*
	 * Optional defaults section to copy values from
	 */
	@Nullable
	ConfigSection defaults;

	/*
	 * Defaults path in your JAR file, if any
	 */
	@Nullable
	String defaultsPath;

	/**
	 * Optional config header
	 */
	@Nullable
	private String header;

	/**
	 * Path prefix to automatically append when calling any getX method
	 * to save your time.
	 */
	private String pathPrefix = null;

	/**
	 * Should we always reload the file even if it was loaded previously when calling {@link #load(File)}?
	 *
	 * Defaults to true
	 */
	@Setter(value = AccessLevel.PROTECTED)
	private boolean alwaysLoad = true;

	/*
	 * Internal flag to only save once during loading and save automatically
	 * after loading if any changes were made.
	 */
	private boolean shouldSave = false;

	/*
	 * Internal flag to avoid duplicate save calls during loading
	 */
	private boolean loading = false;

	/*
	 * Internal flag to avoid race condition when calling save() in onSave().
	 */
	private boolean saving = false;

	protected FileConfig() {
	}

	// ------------------------------------------------------------------------------------
	// Getting fields
	// ------------------------------------------------------------------------------------

	/**
	 * Return all keys in the current section.
	 *
	 * @param deep
	 * @return
	 */
	@NonNull
	protected final Set<String> getKeys(boolean deep) {
		return this.section.getKeys(deep);
	}

	/**
	 * Return all values in the current section. Values can be another {@link ConfigSection}
	 *
	 * @param deep
	 * @return
	 *
	 * @deprecated it is recommended that you use getMap("") instead or for loop in getKeys(deep) and getMap(key) for each
	 * 			   key and print out the results to console to understand the differences
	 */
	@Deprecated
	protected final Map<String, Object> getValues(boolean deep) {
		return this.section.getValues(deep);
	}

	/**
	 * Returns a value at the given path. Path prefix is added automatically, see {@link #setPathPrefix(String)}.
	 * If default config exists within your JAR, we copy the value and save the file if it does not exist.
	 * Specify the type to automatically convert the value into. If you are getting a value that is a custom class,
	 * and your deserialize method has custom parameters in it, pass it here. Example: your custom class
	 * has deserialize(SerializedMap, Player) method, then pass the player instance in deserializeParams
	 *
	 *
	 * @param <T>
	 * @param path
	 * @param type
	 * @param deserializeParams
	 * @return
	 */
	protected final <T> T get(final String path, final Class<T> type, Object... deserializeParams) {
		return this.get(path, type, null, deserializeParams);
	}

	/**
	 * Returns a value at the given path. Path prefix is added automatically, see {@link #setPathPrefix(String)}.
	 * If default config exists within your JAR, we copy the value and save the file if it does not exist.
	 * Specify the type to automatically convert the value into. If you are getting a value that is a custom class,
	 * and your deserialize method has custom parameters in it, pass it here. Example: your custom class
	 * has deserialize(SerializedMap, Player) method, then pass the player instance in deserializeParams
	 *
	 * The def value you specify here is NOT copied/saved if key does not exist, we try to copy it from the
	 * default file from your JAR instead. It is only returned if nor JAR file or the key exist.
	 *
	 * @param <T>
	 * @param path
	 * @param type
	 * @param def
	 * @param deserializeParams
	 * @return
	 */
	protected final <T> T get(@NonNull String path, Class<T> type, T def, Object... deserializeParams) {

		path = this.buildPathPrefix(path);

		// Copy defaults if not set and log about this change
		this.copyDefault(path, type);

		Object raw = this.section.retrieve(path);

		if (this.defaults != null && def == null){
			Valid.checkNotNull(raw, "Failed to set '" + path + "' to " + type.getSimpleName() + " from default config's value: " + this.defaults.retrieve(path));
		}

		if (raw != null) {

			// Workaround for empty lists
			if (raw.equals("[]") && type == List.class){
				raw = new ArrayList<>();
			}

			// Retype manually
			if (type == Long.class && raw instanceof Integer){
				raw = ((Integer) raw).longValue();
			}

			raw = SerializeUtil.deserialize(this.mode, type, raw, deserializeParams);
			this.checkAssignable(path, raw, type);

			return (T) raw;
		}

		return def;
	}

	/*
	 * Attempts to copy a key at the given path from inbuilt JAR to the disk.
	 */
	private void copyDefault(final String path, final Class<?> type) {
		if (this.defaults != null && !this.section.isStored(path)) {
			final Object object = this.defaults.retrieve(path);
			Valid.checkNotNull(object, "Inbuilt config " + this.getFileName() + " lacks " + (object == null ? "key" : object.getClass().getSimpleName()) + " at \"" + path + "\". Is it outdated?");

			Common.log("&7Updating " + this.getFileName() + " at &b'&f" + path + "&b' &7-> " + "&b'&f" + object.toString().replace("\n", ", ") + "&b'" + "&r");
			this.section.store(path, object);
			this.shouldSave = true;
		}
	}

	/*
	 * Attempts to force a certain class type for the given object, used to prevent mistakes
	 * such as putting "Enabled: truee" (which is a String) instead of "Enabled: true" (which is a Boolean)
	 */
	private void checkAssignable(final String path, final Object object, final Class<?> type) {
		if (!type.isAssignableFrom(object.getClass()) && !type.getSimpleName().equals(object.getClass().getSimpleName())) {

			// Exceptions
			if (ConfigSerializable.class.isAssignableFrom(type) && object instanceof ConfigSection) {
				return;
			}
			if (Set.class.isAssignableFrom(type) && object instanceof List){
				return;
			}

			throw new FoException("Malformed configuration! Key '" + path + "' in " + this.getFileName() + " must be " + type.getSimpleName() + " but got " + object.getClass().getSimpleName() + ": '" + object + "'");
		}
	}

	// ------------------------------------------------------------------------------------
	// Getting values helpers
	// ------------------------------------------------------------------------------------

	/**
	 * Return a String value from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * This will work even if the key is a list that only has one value, or a number or boolean.
	 *
	 * @param path
	 * @return
	 */
	protected final String getString(final String path) {
		return this.getString(path, null);
	}

	/**
	 * Return a String value from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * This will work even if the key is a list that only has one value, or a number or boolean.
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final String getString(final String path, final String def) {
		final Object object = this.getObject(path, def);

		if (object == null)
			return null;

		else if (object instanceof List)
			return Common.join((List<?>) object, "\n");

		else if (object instanceof String[])
			return Common.join(Arrays.asList((String[]) object), "\n");

		else if (object.getClass().isArray())
			return Common.join((Object[]) object);

		else if (object instanceof Boolean
				|| object instanceof Integer
				|| object instanceof Long
				|| object instanceof Double
				|| object instanceof Float)
			return Objects.toString(object);

		else if (object instanceof Number)
			return object.toString();

		else if (object instanceof String)
			return (String) object;

		throw new FoException("Excepted string at '" + path + "' in " + this.getFileName() + ", got (" + object.getClass() + "): " + object);
	}

	/**
	 * Return a String value from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final Boolean getBoolean(final String path) {
		return this.getBoolean(path, null);
	}

	/**
	 * Return a Boolean value from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final Boolean getBoolean(final String path, final Boolean def) {
		return this.get(path, Boolean.class, def);
	}

	/**
	 * Return an Integer value from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final Integer getInteger(final String path) {
		return this.getInteger(path, null);
	}

	/**
	 * Return a Integer value from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final Integer getInteger(final String path, final Integer def) {
		return this.get(path, Integer.class, def);
	}

	/**
	 * Return a Long value from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final Long getLong(final String path) {
		return this.getLong(path, null);
	}

	/**
	 * Return a Long value from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final Long getLong(final String path, final Long def) {
		return this.get(path, Long.class, def);
	}

	/**
	 * Return a Double value from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final Double getDouble(final String path) {
		return this.getDouble(path, null);
	}

	/**
	 * Return a Double value from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final Double getDouble(final String path, final Double def) {
		final Object raw = this.getObject(path, def);

		if (raw != null)
			Valid.checkBoolean(raw instanceof Number, "Expected a number at '" + path + "', got " + raw.getClass().getSimpleName() + ": " + raw);

		return raw != null ? ((Number) raw).doubleValue() : null;
	}

	/**
	 * Return a Location from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * We use a custom method to store location on one line, this won't work
	 * when using Bukkit's getLocation since Bukkit stores in using multiple keys.
	 *
	 * @param path
	 * @return
	 */
	protected final Location getLocation(final String path) {
		return this.getLocation(path, null);
	}

	/**
	 * Return a Location from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * We use a custom method to store location on one line, this won't work
	 * when using Bukkit's getLocation since Bukkit stores in using multiple keys.
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final Location getLocation(final String path, final Location def) {
		return this.get(path, Location.class, def);
	}

	/**
	 * Return a OfflinePlayer from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final OfflinePlayer getOfflinePlayer(final String path) {
		return this.getOfflinePlayer(path, null);
	}

	/**
	 * Return a OfflinePlayer from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final OfflinePlayer getOfflinePlayer(final String path, final OfflinePlayer def) {
		return this.get(path, OfflinePlayer.class, def);
	}

	/**
	 * Return a sound from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final SimpleSound getSound(final String path) {
		return this.getSound(path, null);
	}

	/**
	 * Return a sound from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final SimpleSound getSound(final String path, final SimpleSound def) {
		return this.get(path, SimpleSound.class, def);
	}

	/**
	 * Return a "case" from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * See {@link AccusativeHelper} header docs for what this is.
	 *
	 * @param path
	 * @return
	 */
	protected final AccusativeHelper getAccusativePeriod(final String path) {
		return this.getAccusativePeriod(path, null);
	}

	/**
	 * Return a "case" from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * See {@link AccusativeHelper} header docs for what this is.
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final AccusativeHelper getAccusativePeriod(final String path, final String def) {
		final String rawLine = this.getString(path, def);

		return rawLine != null ? new AccusativeHelper(rawLine) : null;
	}

	/**
	 * Return a title from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final TitleHelper getTitle(final String path) {
		return this.getTitle(path, null, null);
	}

	/**
	 * Return a title from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param defTitle
	 * @param defSubtitle
	 * @return
	 */
	protected final TitleHelper getTitle(final String path, final String defTitle, final String defSubtitle) {
		final String title = this.getString(path + ".Title", defTitle);
		final String subtitle = this.getString(path + ".Subtitle", defSubtitle);

		return title != null ? new TitleHelper(title, subtitle) : null;
	}

	/**
	 * Return a time from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final SimpleTime getTime(final String path) {
		return this.getTime(path, null);
	}

	/**
	 * Return a time from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final SimpleTime getTime(final String path, final SimpleTime def) {
		return this.get(path, SimpleTime.class, def);
	}

	/**
	 * Return a double percentage from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * This is stored as a string such as 85%
	 *
	 * @param path
	 * @return
	 */
	protected final Double getPercentage(String path) {
		return this.getPercentage(path, null);
	}

	/**
	 * Return a double percentage from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * This is stored as a string such as 85%
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final Double getPercentage(String path, Double def) {

		final Object object = this.getObject(path, def);

		if (object != null) {
			final String raw = object.toString();
			Valid.checkBoolean(raw.endsWith("%"), "Your " + path + " key in " + this.getPathPrefix() + "." + path + " must end with %! Got: " + raw);

			final String rawNumber = raw.substring(0, raw.length() - 1);
			Valid.checkInteger(rawNumber, "Your " + path + " key in " + this.getPathPrefix() + "." + path + " must be a whole number! Got: " + raw);

			return Integer.parseInt(rawNumber) / 100D;
		}

		return null;
	}

	/**
	 * Return a message that can be formatted nicely from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final BoxedMessage getBoxedMessage(final String path) {
		return this.getBoxedMessage(path, null);
	}

	/**
	 * Return a message that can be formatted nicely from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final BoxedMessage getBoxedMessage(final String path, final BoxedMessage def) {
		return this.get(path, BoxedMessage.class, def);
	}

	/**
	 * Return material from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 */
	protected final Material getMaterial(final String path){
		return getMaterial(path, null);
	}

	/**
	 * Return material from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 */
	protected final Material getMaterial(final String path, Material def){
		return this.get(path, Material.class, def);
	}

	/**
	 * Return CompMaterial from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 */
	protected final CompMaterial getCompMaterial(final String path) {
		return this.getCompMaterial(path, null);
	}

	/**
	 * Return CompMaterial from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 */
	protected final CompMaterial getCompMaterial(final String path, CompMaterial def) {
		return this.get(path, CompMaterial.class, def);
	}

	/**
	 * Return an item from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final ItemStack getItemStack(@NonNull String path) {
		return this.getItemStack(path, null);
	}

	/**
	 * Return an item from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final ItemStack getItemStack(@NonNull String path, ItemStack def) {
		return this.get(path, ItemStack.class, def);
	}

	/**
	 * Return an tuple from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * This is stored as a map that has two sub-keys, one for the first value, second for the latter
	 *
	 * @param <K>
	 * @param <V>
	 * @param key
	 * @param keyType
	 * @param valueType
	 * @return
	 */
	protected final <K, V> Tuple<K, V> getTuple(final String key, Class<K> keyType, Class<V> valueType) {
		return this.getTuple(key, null, keyType, valueType);
	}

	/**
	 * Return an tuple from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * This is stored as a map that has two sub-keys, one for the first value, second for the latter
	 *
	 * @param <K>
	 * @param <V>
	 * @param key
	 * @param def
	 * @param keyType
	 * @param valueType
	 * @return
	 */
	protected final <K, V> Tuple<K, V> getTuple(final String key, final Tuple<K, V> def, Class<K> keyType, Class<V> valueType) {
		return this.get(key, Tuple.class, def, keyType, valueType);
	}

	/**
	 * Return an unspecified object from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final Object getObject(final String path) {
		return this.getObject(path, null);
	}

	/**
	 * Return an unspecified object from the key at the given path, or supply with default
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @param def
	 * @return
	 */
	protected final Object getObject(final String path, final Object def) {
		return this.get(path, Object.class, def);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Getting lists
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Return a special location list from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 * (see {@link LocationList})
	 *
	 * @param path
	 * @return
	 */
	protected final LocationList getLocationList(final String path) {
		return new LocationList(this, this.getList(path, Location.class));
	}

	/**
	 * Return a list of maps\<string, object\> list from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final List<SerializedMap> getMapList(final String path) {
		return this.getList(path, SerializedMap.class);
	}

	/**
	 * Return a special {@link IsInList} list from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * It is a list used to check if a value is in it, it can contain ["*"] to match all.
	 *
	 * @param <T>
	 * @param path
	 * @param type
	 * @return
	 */
	protected final <T> IsInList<T> getIsInList(String path, Class<T> type) {
		final List<String> stringList = this.getStringList(path);

		if (stringList.size() == 1 && "*".equals(stringList.get(0)))
			return IsInList.fromStar();

		return IsInList.fromList(this.getList(path, type));
	}

	/**
	 * Return a list of strings from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final List<String> getStringList(final String path) {
		final Object raw = this.getObject(path);

		if (raw == null)
			return new ArrayList<>();

		if (raw instanceof String) {
			final String output = (String) raw;

			return "'[]'".equals(output) || "[]".equals(output) ? new ArrayList<>() : this.fixYamlBooleansInList((Object[]) output.split("\n"));
		}

		if (raw instanceof List)
			return this.fixYamlBooleansInList(((List<Object>) raw).toArray());

		throw new FoException("Excepted a list at '" + path + "' in " + this.getFileName() + ", got (" + raw.getClass() + "): " + raw);
	}

	/*
	 * Attempts to convert objects into strings, since SnakeYAML parser interprets
	 * "true" and "yes" as boolean types
	 */
	private List<String> fixYamlBooleansInList(@NonNull final Object... list) {
		final List<String> newList = new ArrayList<>();

		for (final Object obj : list)
			if (obj != null)
				newList.add(obj.toString());

		return newList;
	}

	/**
	 * Return a list of strings used as commands from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * This is a basic string list, however we enforce at least one value (the first -> the
	 * main command label) and we remove the initial / from each item.
	 *
	 * Usable in {@link SimpleCommandGroup} or {@link SimpleCommand}
	 *
	 * @param path
	 * @return
	 */
	@Nullable
	protected final StrictList<String> getCommandList(final String path) {
		final List<String> list = this.getStringList(path);
		Valid.checkBoolean(!list.isEmpty(), "Please set at least one command alias in '" + path + "' (" + this.getFileName() + ") for this will be used as your main command!");

		for (int i = 0; i < list.size(); i++) {
			String command = list.get(i);

			command = command.startsWith("/") ? command.substring(1) : command;
			list.set(i, command);
		}

		return new StrictList<>(list);
	}

	/**
	 * Return a list of materials from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 */
	protected final List<Material> getMaterialList(final String path){
		return this.getList(path, Material.class);
	}

	/**
	 * Return a list of CompMaterials from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 */
	protected final List<CompMaterial> getCompMaterialList(final String path) {
		return this.getList(path, CompMaterial.class);
	}

	/**
	 * Get a new HashSet with the values from the given path.<br>
	 * This method returns a new <b>HashSet</b> with values.<br>
	 * To get a set of another specific type use {@link #getSet(String, Class, Set)}.
	 * @param key the path
	 * @param type the type of values
	 * @return a new HashSet with the values
	 */
	protected final <T> Set<T> getSet(final String key, final Class<T> type) {
		return getSet(key, type, new HashSet<>());
	}

	/**
	 * Get the given set filled with the values from the given path.
	 * If you don't specify the <code>to</code> parameter, HashSet will be used as a default.<br><br>
	 * Example:<br>
	 * <code>getSet("tables", Table.class, new LinkedHashSet())</code>.<br><br>
	 * @param key the path
	 * @param type the type of values
	 * @param to the specific set which will be filled with values
	 * @return a set of the given type with the values
	 * @author Rubix327
	 */
	protected final <T, S extends Set<T>> S getSet(final String key, final Class<T> type, final S to){
		to.addAll(this.getList(key, type));
		return to;
	}

	/**
	 * Get a list of tuples with the given key-value
	 *
	 * @param <K>
	 * @param <V>
	 * @param path
	 * @param tupleKey
	 * @param tupleValue
	 * @return
	 */
	protected final <K, V> List<Tuple<K, V>> getTupleList(final String path, final Class<K> tupleKey, final Class<V> tupleValue) {
		final List<Tuple<K, V>> list = new ArrayList<>();

		for (final Object object : this.getList(path))
			if (object == null)
				list.add(null);
			else {
				final Tuple<K, V> tuple = Tuple.deserialize(SerializedMap.of(object), tupleKey, tupleValue);

				list.add(tuple);
			}

		return list;
	}

	/**
	 * Get a new ArrayList with values from the given path.<br>
	 * To get another specific type of List, use {@link #getList(String, Class, List, Object...)}.<br>
	 * @author Rubix327
	 */
	protected final <T> List<T> getList(final String path, final Class<T> type, final Object... deserializeParameters) {
		return getList(path, type, new ArrayList<>(), deserializeParameters);
	}

	/**
	 * Fill the given list with the values from the given path and then return it.<br><br>
	 * Example:<br>
	 * <code>getList("effects", Effect.class, new LinkedList())</code><br><br>
	 * @param path the path
	 * @param type the type of values
	 * @param to the specific list which will be filled with values
	 * @return a specific list with the values
	 */
	protected final <T, L extends List<T>> L getList(final String path, final Class<T> type, final L to, final Object... deserializeParameters) {
		final List<Object> objects = this.getList(path);

		if (type == Map.class && deserializeParameters != null & deserializeParameters.length > 0 && deserializeParameters[0] != String.class)
			throw new FoException("getList('" + path + "') that returns Map must have String.class as key, not " + deserializeParameters[0]);

		for (Object object : objects) {
			object = object != null ? SerializeUtil.deserialize(this.mode, type, object, deserializeParameters) : null;

			if (object != null)
				to.add((T) object);

			else if (!type.isPrimitive() && type != String.class)
				to.add(null);
		}

		return to;
	}

	/**
	 * Return a list (or empty if not set) from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * We allow one values instead of lists, such as
	 * "Apply_On: timed" instead of "Apply_On: [timed]" for convenience
	 *
	 * @param path
	 * @return
	 */
	protected final List<Object> getList(final String path) {
		final Object obj = this.getObject(path);

		if (obj instanceof Collection<?>){
			return new ArrayList<>(((Collection<?>) obj));
		} else if (obj != null){
			return Collections.singletonList(obj);
		} else {
			return new ArrayList<>();
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Getting maps
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Return a map\<string, object\> from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param path
	 * @return
	 */
	protected final SerializedMap getMap(final String path) {
		final LinkedHashMap<?, ?> map = this.getMap(path, Object.class, Object.class);

		return SerializedMap.of(map);
	}

	/**
	 * Return a map of the given key and value types from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param <Key>
	 * @param <Value>
	 * @param path
	 * @param keyType
	 * @param valueType
	 * @param valueDeserializeParams
	 * @return
	 */
	protected final <Key, Value> LinkedHashMap<Key, Value> getMap(@NonNull String path, final Class<Key> keyType, final Class<Value> valueType, Object... valueDeserializeParams) {

		// The map we are creating, preserve order
		final LinkedHashMap<Key, Value> map = new LinkedHashMap<>();
		final boolean exists = this.isSet(path);

		// Add path prefix right away
		path = this.buildPathPrefix(path);

		// Add defaults
		if (this.defaults != null && !exists) {
			Valid.checkBoolean(this.defaults.isStored(path), "Default '" + this.getFileName() + "' lacks a map at " + path);

			for (final String key : this.defaults.retrieveConfigurationSection(path).getKeys(false))
				this.copyDefault(path + "." + key, valueType);
		}

		// Load key-value pairs from config to our map
		if (exists)
			for (final Map.Entry<String, Object> entry : SerializedMap.of(this.section.retrieve(path))) {
				final Key key = SerializeUtil.deserialize(this.mode, keyType, entry.getKey());
				final Value value;

				if (LocationList.class.isAssignableFrom(valueType)) {
					final List<?> list = SerializeUtil.deserialize(this.mode, List.class, entry.getValue());
					final List<Location> copy = new ArrayList<>();

					list.forEach(locationRaw -> copy.add(SerializeUtil.deserializeLoc(locationRaw)));

					value = (Value) new LocationList(this, copy);

				} else
					value = SerializeUtil.deserialize(this.mode, valueType, entry.getValue(), valueDeserializeParams);

				// Ensure the pair values are valid for the given paramenters
				this.checkAssignable(path, key, keyType);
				this.checkAssignable(path, value, valueType);

				map.put(key, value);
			}

		return map;
	}

	/**
	 * Return a list of a map of the given types from the key at the given path
	 * (see {@link #get(String, Class, Object, Object...)}).
	 *
	 * @param <Key>
	 * @param <Value>
	 * @param path
	 * @param keyType
	 * @param setType
	 * @param setDeserializeParameters
	 * @return
	 */
	protected final <Key, Value> LinkedHashMap<Key, List<Value>> getMapList(@NonNull String path, final Class<Key> keyType, final Class<Value> setType, Object... setDeserializeParameters) {

		// The map we are creating, preserve order
		final LinkedHashMap<Key, List<Value>> map = new LinkedHashMap<>();
		final boolean exists = this.isSet(path);

		// Add path prefix right away
		path = this.buildPathPrefix(path);

		// Add defaults
		if (this.defaults != null && !exists) {
			Valid.checkBoolean(this.defaults.isStored(path), "Default '" + this.getFileName() + "' lacks a map at " + path);

			for (final String key : this.defaults.retrieveConfigurationSection(path).getKeys(false))
				this.copyDefault(path + "." + key, setType);
		}

		// Load key-value pairs from config to our map
		if (exists)
			for (final Map.Entry<String, Object> entry : SerializedMap.of(this.section.retrieve(path)).entrySet()) {
				final Key key = SerializeUtil.deserialize(this.mode, keyType, entry.getKey());
				final List<Value> value = SerializeUtil.deserialize(this.mode, List.class, entry.getValue(), setDeserializeParameters);

				// Ensure the pair values are valid for the given parameters
				this.checkAssignable(path, key, keyType);

				if (!value.isEmpty())
					for (final Value item : value)
						this.checkAssignable(path, item, setType);

				map.put(key, value);
			}

		return map;
	}

	// ------------------------------------------------------------------------------------
	// Setting values
	// ------------------------------------------------------------------------------------

	/**
	 * Sets the given value to the given path (set the value to null to remove it)
	 * and then saves the file immediately.
	 *
	 * Path prefix is added automatically, see {@link #getPathPrefix()}
	 * The value is serialized using {@link SerializeUtil}
	 *
	 * @param path
	 * @param value
	 */
	protected final void save(String path, Object value) {
		this.set(path, value);

		this.save();
	}

	/**
	 * Sets the given value to the given path (set the value to null to remove it).
	 *
	 * Path prefix is added automatically, see {@link #getPathPrefix()}
	 * The value is serialized using {@link SerializeUtil}
	 *
	 * @param path
	 * @param value
	 */
	protected final void set(String path, Object value) {
		path = this.buildPathPrefix(path);
		value = SerializeUtil.serialize(this.mode, value);

		this.section.store(path, value);
		this.shouldSave = true;
	}

	/**
	 * Returns true if the given path contains a non-null value
	 *
	 * Path prefix is added automatically, see {@link #getPathPrefix()}
	 *
	 * @param path
	 * @return
	 */
	public final boolean isSet(String path) {
		path = this.buildPathPrefix(path);

		return this.section.isStored(path);
	}

	/**
	 * Returns true if defaults are set and contain a non-null value
	 * at the given path
	 *
	 * Path prefix is added automatically, see {@link #getPathPrefix()}
	 *
	 * @param path
	 * @return
	 */
	protected final boolean isSetDefault(String path) {
		path = this.buildPathPrefix(path);

		return this.defaults != null && this.defaults.isStored(path);
	}

	/**
	 * Attempts to move the given key from the relative path (path prefix is added)
	 * to the absolute new path (path prefix not added)
	 *
	 * @param fromPathRel
	 * @param toPathAbs
	 */
	protected final void move(String fromPathRel, final String toPathAbs) {
		final Object oldObject = this.getObject(fromPathRel);

		// Remove the old object
		this.set(fromPathRel, null);

		// Set it as absolute, do not add path prefix
		this.section.store(toPathAbs, oldObject);

		Common.log("&7Update " + this.getFileName() + ". Move &b\'&f" + this.buildPathPrefix(fromPathRel) + "&b\' &7(was \'" + oldObject + "&7\') to " + "&b\'&f" + toPathAbs + "&b\'" + "&r");
	}

	// ------------------------------------------------------------------------------------
	// File manipulation
	// ------------------------------------------------------------------------------------

	/**
	 * Attempts to load the file configuration, not saving any changes made since last loading it.
	 */
	public final void reload() {
		Valid.checkNotNull(this.file, "Cannot call reload() before loading a file!");

		this.load(this.file);
	}

	/*
	 * Helper to load configuration from a file
	 */
	final void load(@NonNull File file) {
		synchronized (loadedSections) {
			try {
				Valid.checkBoolean(!this.loading, "Called load(" + file + ") on already being loaded configuration!");
				this.loading = true;

				final FileInputStream stream = new FileInputStream(file);
				final String path = file.getAbsolutePath();
				boolean loadedBefore = false;
				ConfigSection section = loadedSections.get(path);

				if (section == null) {
					section = new ConfigSection();

					loadedSections.put(path, section);
				}

				else {
					loadedBefore = true;
				}

				this.section = section;
				this.file = file;

				if (!loadedBefore || this.alwaysLoad){
					this.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
				}

				try {
					this.loadFields();
					this.onLoad();

				} catch (final EventHandledException ex) {
					// Handled successfully in the polymorphism pipeline
				}

				if (this.shouldSave || this.alwaysSaveOnLoad()) {
					this.loading = false;
					this.save();

					this.shouldSave = false;
				}

			} catch (final Exception ex) {
				Common.throwError(ex, "Error loading " + file + ": " + ex);
			} finally {
				this.loading = false;
			}
		}
	}

	/*
	 * Helper to load configuration from a reader
	 */
	private void load(@NonNull Reader reader) {
		try {
			final BufferedReader input = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
			final StringBuilder builder = new StringBuilder();

			try {
				String line;

				while ((line = input.readLine()) != null) {
					builder.append(line);
					builder.append('\n');
				}

			} finally {
				input.close();
			}

			this.loadFromString(builder.toString());

		} catch (final Exception ex) {
			Remain.sneaky(ex);
		}
	}

	/**
	 * Implementation by specific configuration type to load configuration from the given string contents.
	 *
	 * @param contents
	 */
	abstract void loadFromString(@NonNull String contents);

	/**
	 * Called automatically when the file is just created.
	 */
	protected void onFileCreate(){
	}

	/**
	 * Called automatically right before the configuration is loaded.
	 */
	protected void onPreLoad(){
	}

	/**
	 * Called automatically when the configuration has been loaded, used to load your
	 * fields in your class.<br>
	 * If @AutoConfig is enabled, this method is executed after AutoConfig fields have been loaded.
	 * <br>
	 * You can throw {@link EventHandledException} here to indicate to your child class to interrupt loading
	 */
	protected void onLoad() {
	}

	/**
	 * Called automatically after {@link #onLoad()} regardless of configuration loading success.
	 */
	protected void onLoadFinish() {
	}

	/**
	 * Save the configuration to the file immediately (you need to call loadConfiguration(File) first)
	 */
	public final void save() {
		Valid.checkNotNull(this.file, "Cannot call save() for " + this + " when no file was set! Call load first!");

		this.save(this.file);
	}

	/**
	 * Saves the configuration to the given file, updating the file stored in this configuration.
	 *
	 * @param file
	 */
	public final void save(@NonNull File file) {
		synchronized (loadedSections) {

			if (this.saving)
				return;

			try {
				if (this.loading) {
					this.shouldSave = true;

					return;
				}

				this.onPreSave();

				if (this.canSaveFile()) {

					try {
						this.saving = true;
						this.saveFields();
						this.onSave();

					} catch (final EventHandledException ignored) {
						// Ignore, indicated that we exited polymorphism inheritance prematurely by intention

					} finally {
						this.saving = false;
					}

					final File parent = file.getCanonicalFile().getParentFile();

					if (parent != null)
						parent.mkdirs();

					final String data = this.saveToString();

					if (data != null){
						try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
							writer.write(data);

						} catch (final Exception ex) {
							Remain.sneaky(ex);
						}
					}

					// Update file
					this.file = file;
				}

			} catch (final Exception ex) {
				Remain.sneaky(ex);
			}

			this.onSaveFinish();
		}
	}

	/**
	 * Return true if we should always save the file after loading it.
	 *
	 * @return
	 */
	protected boolean alwaysSaveOnLoad() {
		return false;
	}

	/**
	 * Called automatically before {@link #canSaveFile()}
	 */
	protected void onPreSave() {
	}

	/**
	 * Called automatically on saving the configuration, you can call "set(path, value)" methods here
	 * to save your class fields. We automatically save what you have in {@link #saveToMap()} if not null.
	 * <br>
	 * If @AutoConfig is enabled, this method is executed after AutoConfig fields have been saved.
	 * <br>
	 * Called after {@link #canSaveFile()}
	 */
	protected void onSave() {
		final SerializedMap map = this.saveToMap();

		if (map != null)
			for (final Map.Entry<String, Object> entry : map.entrySet())
				this.set(entry.getKey(), entry.getValue());
	}

	/**
	 * Called automatically after {@link #onSave()} (typically when configuration has been saved).
	 */
	protected void onSaveFinish(){
	}

	/**
	 * Get values from file and set them to fields annotated with @AutoConfig
	 * @author Rubix327
	 */
	private void loadFields(){
		for (Field field : getFieldsToAutoLoad(new ArrayList<>(), this.getClass())) {
			field.setAccessible(true);

			try {
				Object value = getBasedOnClass(getFormattedFieldName(field), field);
				if (value != null || loadNullValues()){
					field.set(this, value);
				}
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Get fields for which we should set values from a file.
	 * Skips static fields and grabs all the fields which are annotated or whose class is annotated with @AutoConfig.
	 * @author Rubix327
	 */
	private List<Field> getFieldsToAutoLoad(List<Field> startList, Class<?> clazz) {
		boolean isAboveClass = false;

		// Do nothing if AutoConfig is disabled for the whole class
		AutoConfig ant = clazz.getAnnotation(AutoConfig.class);
		if (ant != null){
			isAboveClass = true;
			if (!ant.value()){
				return startList;
			}
		}

		// Scan all the fields of the given class
		for (Field field : clazz.getDeclaredFields()){
			AutoConfig ann = field.getAnnotation(AutoConfig.class);
			boolean hasAnnotation = ann != null;
			boolean isEnabled = false;
			if (hasAnnotation){
				isEnabled = ann.value() && ann.autoLoad();
			}
			else if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			if (ReflectionUtil.isAnnotationAttached(isAboveClass, hasAnnotation, isEnabled)) startList.add(field);
		}

		// Run once more to get fields of a superclass
		if (ant != null && ant.deep()){
			if (clazz.getSuperclass() != YamlConfig.class){
				startList = getFieldsToAutoLoad(startList, clazz.getSuperclass());
			}
		}

		return startList;
	}

	/**
	 * Set values annotated with @AutoConfig from a YamlConfig and save them to a file.
	 * @author Rubix327
	 */
	private void saveFields() {
		for (Field field : getFieldsToAutoSave(new ArrayList<>(), this.getClass())) {
			field.setAccessible(true);

			try {
				Object value = field.get(this);
				if (value != null || saveNullValues()){
					set(getFormattedFieldName(field), value);
				}
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Get fields which should be saved to a file.
	 * Skips static fields and grabs all the fields which are annotated or whose class is annotated with @AutoConfig.
	 * @author Rubix327
	 */
	private List<Field> getFieldsToAutoSave(List<Field> startList, Class<?> clazz) {
		boolean isAboveClass = false;

		// Do nothing if AutoConfig is disabled for the whole class
		AutoConfig ant = clazz.getAnnotation(AutoConfig.class);
		if (ant != null){
			isAboveClass = true;
			if (!ant.value()){
				return startList;
			}
		}

		// Scan all the fields of the given class
		for (Field field : clazz.getDeclaredFields()){
			AutoConfig ann = field.getAnnotation(AutoConfig.class);
			boolean hasAnnotation = ann != null;
			boolean isEnabled = false;
			if (hasAnnotation){
				isEnabled = ann.value() && ann.autoSave();
			}
			else if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			if (ReflectionUtil.isAnnotationAttached(isAboveClass, hasAnnotation, isEnabled)) startList.add(field);
		}

		// Run once more to get fields of a superclass
		if (ant != null && ant.deep()){
			if (clazz.getSuperclass() != YamlConfig.class){
				startList = getFieldsToAutoSave(startList, clazz.getSuperclass());
			}
		}

		return startList;
	}

	/**
	 * Get the deserialized object from the given path depending on its field class type.
	 * @author Rubix327
	 */
	protected Object getBasedOnClass(String path, Field field){
		Class<?> fieldType = field.getType();

		// ***
		// If field is a set, list, tuple or a map
		// ***

		try{
			if (Set.class.isAssignableFrom(fieldType)){
				Type type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
				return getSet(path, Class.forName(type.getTypeName()));
			}
			else if (List.class.isAssignableFrom(fieldType)){
				Type type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
				return getList(path, Class.forName(type.getTypeName()));
			}
			else if (Map.class.isAssignableFrom(fieldType)){
				Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
				return getMap(path, Class.forName(types[0].getTypeName()), Class.forName(types[1].getTypeName()));
			}
			else if (Tuple.class.isAssignableFrom(fieldType)){
				Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
				return getTuple(path, Class.forName(types[0].getTypeName()), Class.forName(types[1].getTypeName()));
			}
		}
		catch (ClassNotFoundException c){
			c.printStackTrace();
		}

		// ***
		// Getting TypeMethod for those types whose getter methods does not correspond with their names
		// ***

		// Convert field name to UPPER_UNDERSCORE to correspond with TypeMethod enum
		String name = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, fieldType.getSimpleName());
		TypeMethod typeMethod = null;
		try{
			typeMethod = TypeMethod.valueOf(name);
		} catch (IllegalArgumentException ignored){}

		String str = fieldType.getSimpleName();

		// ***
		// If it is a type from TypeMethod
		// ***

		if (typeMethod != null){
			name = typeMethod.getMethod();

		// ***
		// Try to get field value by 'getSomething' method
		// ***

		} else {
			name = str.substring(0, 1).toUpperCase() + str.substring(1);
			if (fieldType == int.class) {
				name = "Integer";
			}
			name = "get" + name;
		}

		try {
			// Trying to get FileConfig class instance to invoke a method from it
			// Note: any other ways doing that is not suitable, except
			// making all 'get' methods public, which is unacceptable
			Class<?> clazz = ReflectionUtil.getParent(this.getClass(), "FileConfig", 10);
			Method method = clazz.getDeclaredMethod(name, String.class);
			return method.invoke(this, path);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
			return get(path, fieldType);
		}
	}

	/**
	 * Types whose getters names do not match their names.
	 * @author Rubix327
	 */
	@Getter
	private enum TypeMethod {
		ACCUSATIVE_HELPER("getAccusativePeriod"),
		SIMPLE_SOUND("getSound"),
		TITLE_HELPER("getTitle"),
		SIMPLE_TIME("getTime");

		final String method;
		TypeMethod(String method){
			this.method = method;
		}
	}

	/**
	 * Get the name under which the value will be saved in the file.
	 * Based on user-defined AutoConfig.format() and is lower_underscore by default.
	 * @author Rubix327
	 */
	private String getFormattedFieldName(Field field) {
		String name = field.getName();
		if (ChatUtil.isAllUpperCase(field.getName())){
			name = field.getName().toLowerCase();
		}
		name = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
		if (this.getClass().isAnnotationPresent(AutoConfig.class)){
			name = CaseFormat.LOWER_CAMEL.to(this.getClass().getAnnotation(AutoConfig.class).format(), field.getName());
		}
		return name;
	}

	/**
	 * Tells @AutoConfig whether save null values to the file or not.<br>
	 * Only works with @AutoConfig saving (not {@link #onSave()} method).
	 */
	protected boolean saveNullValues(){
		return true;
	}

	/**
	 * Tells @AutoConfig whether load null values from the file or not.<br>
	 * Only works with @AutoConfig loading (not {@link #onLoad()} method).
	 */
	protected boolean loadNullValues(){
		return true;
	}

	/**
	 * Return if the file can be saved when calling {@link #save()}
	 *
	 * @return
	 */
	protected boolean canSaveFile() {
		return true;
	}

	/**
	 * Implementation by specific configurations to generate file contents to save.
	 */
	abstract String saveToString();

	/**
	 * Override to implement custom saving mechanism, used automatically in {@link #onSave()}
	 * you can return only the data you actually want to save here.
	 * <br>
	 * Returns null by default!
	 */
	protected SerializedMap saveToMap() {
		return null;
	}

	/**
	 * Removes the loaded file configuration from the disk.
	 */
	protected final void deleteFile() {
		synchronized (loadedSections) {
			Valid.checkNotNull(this.file, "Cannot unregister null file before settings were loaded!");

			if (this.file.exists())
				this.file.delete();

			loadedSections.remove(this.file.getAbsolutePath());
		}
	}

	// ------------------------------------------------------------------------------------
	// Path prefix
	// ------------------------------------------------------------------------------------

	/**
	 * Returns the current path prefix. See {@link #setPathPrefix(String)} for explainer.
	 *
	 * @return
	 */
	protected final String getPathPrefix() {
		return this.pathPrefix;
	}

	/**
	 * Sets the given path prefix, set to null to remove.
	 *
	 * Path prefix is used as convenience to prevent duplicate
	 * section calls such as:
	 *
	 * get("Player.Name")
	 * get("Player.Health")
	 *
	 * You can set the path prefix to "Player" and then simply call get("Name") and get("Health") instead
	 * and we place "Player." before each path call automatically.
	 *
	 * @param pathPrefix
	 */
	protected final void setPathPrefix(final String pathPrefix) {
		if (pathPrefix != null) {
			Valid.checkBoolean(!pathPrefix.endsWith("."), "Path prefix must not end with a dot: " + pathPrefix);
			Valid.checkBoolean(!pathPrefix.endsWith(".yml"), "Path prefix must not end with .yml!");
		}

		this.pathPrefix = pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix : null;
	}

	/*
	 * Helper method to add path prefix
	 */
	private final String buildPathPrefix(@NonNull final String path) {
		final String prefixed = this.pathPrefix != null ? this.pathPrefix + (!path.isEmpty() ? "." + path : "") : path;
		final String newPath = prefixed.endsWith(".") ? prefixed.substring(0, prefixed.length() - 1) : prefixed;

		// Check for a case where there is multiple dots at the end... #somePeople
		Valid.checkBoolean(!newPath.endsWith("."), "Path '" + path + "' must not end with '.' after path prefix '" + this.pathPrefix + "': " + newPath);
		return newPath;
	}

	// ------------------------------------------------------------------------------------
	// Final getters
	// ------------------------------------------------------------------------------------

	/**
	 * Return the comments header of the file, can be null
	 *
	 * @return
	 */
	protected final @Nullable String getHeader() {
		return this.header;
	}

	/**
	 * Set the comments header of the file, set to null to remove.
	 * Only works if defaults are not set, or if saving comments is disabled for supported configurations
	 *
	 * @param values
	 */
	protected final void setHeader(String... values) {
		this.header = values == null ? null : String.join("\n", values);
	}

	/**
	 * Removes all keys in the entire configuration section
	 */
	protected final void clear() {
		this.section.clear();
	}

	/**
	 * Return the name of the file (if any), without file extension
	 */
	protected String getCleanFileName() {
		final String fileName = this.getFileName();

		if (!fileName.equals("null")) {
			final int lastDot = fileName.lastIndexOf(".");

			if (lastDot != -1)
				return fileName.substring(0, lastDot);
		}

		return null;
	}

	/**
	 * Return the file name, if set
	 */
	public final String getFileName() {
		return this.file == null ? "null" : this.file.getName();
	}

	/**
	 * Return if there are any keys set in this configuration
	 */
	protected final boolean isEmpty() {
		return this.section.isEmpty();
	}

	/*
	 * Copy the given files into debug/ folder
	 */
	public static void copyFileToDebug(File file) throws IOException {
		// Get the path in our folder
		final String path = file.getPath().replace("\\", "/").replace("plugins/" + SimplePlugin.getNamed(), "");

		// Create a copy file
		final File copy = FileUtil.createIfNotExists("debug/" + path);

		// Strip sensitive keys from .YML files
		if (file.getName().endsWith(".yml")) {
			final YamlConfig config = YamlConfig.fromFile(file);
			final YamlConfig copyConfig = YamlConfig.fromFile(copy);

			for (final String key : config.getKeys(true)) {
				final Object value = config.getObject(key);

				if (!key.contains("MySQL"))
					copyConfig.set(key, value);
			}

			copyConfig.save(copy);
		} else {
			Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// ------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------

	@Deprecated // internal use only
	public static void clearLoadedSections() {
		synchronized (loadedSections) {
			loadedSections.clear();
		}
	}

	// ------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------

	/**
	 * Language-specific helper to deal with different cases when i.e. counting:
	 *
	 * "Please wait 1 second before your next message."
	 *
	 * In flexible languages such as Slovak, the case is changed three times:
	 * 0 or 5+ seconds = 5 sekúnd
	 * 1 = 1 sekundu
	 * 2-4 = 2 sekundy
	 *
	 * This helper is used to automatically determine and get the right case. We
	 * save all three values on a single line split by a comma.
	 */
	public static final class AccusativeHelper {

		private final String accusativeSingural; // 1 second (Slovak case - sekundu)
		private final String accusativePlural; // 2-4 seconds (Slovak case - sekundy, not in English)
		private final String genitivePlural; // 0 or 5+ seconds (Slovak case - sekund)

		private AccusativeHelper(final String raw) {
			final String[] values = raw.split(", ");

			if (values.length == 2) {
				this.accusativeSingural = values[0];
				this.accusativePlural = values[1];
				this.genitivePlural = this.accusativePlural;

				return;
			}

			if (values.length != 3)
				throw new FoException("Malformed type, use format: 'second, seconds' OR 'sekundu, sekundy, sekund' (if your language has it)");

			this.accusativeSingural = values[0];
			this.accusativePlural = values[1];
			this.genitivePlural = values[2];
		}

		public String getPlural() {
			return this.genitivePlural;
		}

		public String formatWithCount(final long count) {
			return count + " " + this.formatWithoutCount(count);
		}

		public String formatWithoutCount(final long count) {
			if (count == 1)
				return this.accusativeSingural;

			if (count > 1 && count < 5)
				return this.accusativePlural;

			return this.genitivePlural;
		}
	}

	/**
	 * A helper to automatically send titles and subtitles to players.
	 */
	public static final class TitleHelper {

		private final String title, subtitle;

		private TitleHelper(final String title, final String subtitle) {
			this.title = Common.colorize(title);
			this.subtitle = Common.colorize(subtitle);
		}

		public void playLong(final Player player) {
			this.playLong(player, null);
		}

		public void playLong(final Player player, final Function<String, String> replacer) {
			this.play(player, 5, 4 * 20, 15, replacer);
		}

		public void playShort(final Player player) {
			this.playShort(player, null);
		}

		public void playShort(final Player player, final Function<String, String> replacer) {
			this.play(player, 3, 2 * 20, 5, replacer);
		}

		public void play(final Player player, final int fadeIn, final int stay, final int fadeOut) {
			this.play(player, fadeIn, stay, fadeOut, null);
		}

		public void play(final Player player, final int fadeIn, final int stay, final int fadeOut, Function<String, String> replacer) {
			Remain.sendTitle(player, fadeIn, stay, fadeOut, replacer != null ? replacer.apply(this.title) : this.title, replacer != null ? replacer.apply(this.subtitle) : this.subtitle);
		}
	}

	/**
	 * A helper to store a list of location points and be able to remove/add them with
	 * one click, automatically saving your configuration.
	 */
	public static final class LocationList implements Iterable<Location> {

		private final FileConfig settings;
		private final List<Location> points;

		public LocationList(final FileConfig settings) {
			this(settings, new ArrayList<>());
		}

		private LocationList(final FileConfig settings, final List<Location> points) {
			this.settings = settings;
			this.points = points;
		}

		public boolean toggle(final Location location) {
			for (final Location point : this.points)
				if (Valid.locationEquals(point, location)) {
					this.points.remove(point);

					this.settings.save();
					return false;
				}

			this.points.add(location);
			this.settings.save();

			return true;
		}

		public void add(final Location location) {
			Valid.checkBoolean(!this.hasLocation(location), "Location at " + location + " already exists!");

			this.points.add(location);
			this.settings.save();
		}

		public void remove(final Location location) {
			final Location point = this.find(location);
			Valid.checkNotNull(point, "Location at " + location + " does not exist!");

			this.points.remove(point);
			this.settings.save();
		}

		public boolean hasLocation(final Location location) {
			return this.find(location) != null;
		}

		public Location find(final Location location) {
			for (final Location entrance : this.points)
				if (Valid.locationEquals(entrance, location))
					return entrance;

			return null;
		}

		public List<Location> getLocations() {
			return Collections.unmodifiableList(this.points);
		}

		@Override
		public Iterator<Location> iterator() {
			return this.points.iterator();
		}

		public int size() {
			return this.points.size();
		}
	}
}
