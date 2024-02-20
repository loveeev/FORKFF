package org.mineacademy.fo.remain;

import lombok.*;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.nbt.NBTCompound;
import org.mineacademy.fo.remain.nbt.NBTItem;
import org.mineacademy.fo.settings.YamlConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for persistent metadata manipulation
 * <p>
 * We apply scoreboard tags to ensure permanent metadata storage
 * if supported, otherwise it is lost on reload
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompMetadata {

	/**
	 * The tag delimiter
	 */
	private final static String DELIMITER = "%-%";

	// ----------------------------------------------------------------------------------------
	// Setting metadata
	// ----------------------------------------------------------------------------------------

	/**
	 * Set persistent tag on item.
	 * @return the item with a tag set
	 */
	public static ItemStack setMetadata(final ItemStack item, final String tag){
		return setMetadata(item, tag, tag);
	}

	/**
	 * Set persistent key-value tag on item.
	 * @return the item with a tag set
	 */
	public static ItemStack setMetadata(final ItemStack item, final String key, final String value) {
		Valid.checkNotNull(item, "Item is null");

		final NBTItem nbt = new NBTItem(item);
		final NBTCompound tag = nbt.addCompound(FoConstants.NBT.TAG);

		tag.setString(key, value);
		return nbt.getItem();
	}

	/**
	 * Set persistent tag on entity.
	 */
	public static void setMetadata(final Entity entity, final String tag) {
		setMetadata(entity, tag, tag);
	}

	/**
	 * Set persistent key-value tag on entity.
	 */
	public static void setMetadata(final Entity entity, final String key, final String value) {
		Valid.checkNotNull(entity);

		if (Remain.hasScoreboardTags()) {
			final String tag = format(key, value);
			if (!entity.getScoreboardTags().contains(tag)){
				entity.addScoreboardTag(tag);
				return;
			}
		}

		entity.setMetadata(key, new FixedMetadataValue(SimplePlugin.getInstance(), value));
		MetadataFile.getInstance().addMetadata(entity, key, value);
	}

	/**
	 * Format the syntax of stored tags
	 */
	private static String format(final String key, final String value) {
		return SimplePlugin.getNamed() + DELIMITER + key + DELIMITER + value;
	}

	/**
	 * Set persistent tag on block.
	 */
	public static void setMetadata(final Block block, final String tag){
		setMetadata(block, tag, tag);
	}

	/**
	 * Set persistent tag on block.<br>
	 * By default, does not take into account the type of the block.
	 */
	public static void setMetadata(final Block block, final String key, final String value){
		setMetadata(block, key, value, false);
	}

	/**
	 * Set persistent key-value tag on block.
	 * If you set <i><b>typed</b></i> to <i><b>true</b></i>, it will take into account the type of block,
	 * so if the type of block changes, the metadata would not work until it has the initial type.
	 */
	public static void setMetadata(final Block block, final String key, final String value, final boolean typed) {
		Valid.checkNotNull(block);
		Valid.checkNotNull(key);
		Valid.checkNotNull(value);

		BlockState state = block.getState();
		if (MinecraftVersion.atLeast(V.v1_14)){
			if (state instanceof TileState){
				setNamespaced((TileState) state, key, value);
				state.update();
				return;
			}
		}
		state.setMetadata(key, new FixedMetadataValue(SimplePlugin.getInstance(), value));
		state.update();

		MetadataFile.getInstance().addMetadata(state, key, value, typed);
	}



	private static void setNamespaced(final TileState tile, final String key, final String value) {
		tile.getPersistentDataContainer().set(new NamespacedKey(SimplePlugin.getInstance(), key), PersistentDataType.STRING, value);
	}

	/**
	 * Set persistent tag on world.
	 */
	public static void setMetadata(final World world, final String tag){
		setMetadata(world, tag, tag);
	}

	/**
	 * Set persistent key-value tag on block.
	 */
	public static void setMetadata(final World world, final String key, final String value) {
		world.getPersistentDataContainer().set(new NamespacedKey(SimplePlugin.getInstance(), key), PersistentDataType.STRING, value);
	}

	// ----------------------------------------------------------------------------------------
	// Getting metadata
	// ----------------------------------------------------------------------------------------

	/**
	 * Get metadata of item by key.
	 */
	public static String getMetadata(final ItemStack item, final String key) {
		Valid.checkNotNull(item, "Item is null");

		if (CompMaterial.isAir(item.getType()))
			return null;

		final String compoundTag = FoConstants.NBT.TAG;
		final NBTItem nbt = new NBTItem(item);

		final String value = nbt.hasKey(compoundTag) ? nbt.getCompound(compoundTag).getString(key) : null;

		return Common.getOrNull(value);
	}

	/**
	 * Get metadata value of entity by key, or null if none.
	 * @return the value or null
	 */
	public static String getMetadata(final Entity entity, final String key) {
		Valid.checkNotNull(entity);

		if (Remain.hasScoreboardTags())
			for (final String line : entity.getScoreboardTags()) {
				final String tag = getTag(line, key);

				if (tag != null && !tag.isEmpty())
					return tag;
			}

		final String value = entity.hasMetadata(key) ? entity.getMetadata(key).get(0).asString() : null;

		return Common.getOrNull(value);
	}

	// Parse the tag and get its value
	private static String getTag(final String raw, final String key) {
		final String[] parts = raw.split(DELIMITER);

		return parts.length == 3 && parts[0].equals(SimplePlugin.getNamed()) && parts[1].equals(key) ? parts[2] : null;
	}

	/**
	 * Get metadata value of block by key, or null if none
	 * @return the value or null
	 */
	public static String getMetadata(final Block block, final String key) {
		Valid.checkNotNull(block);
		Valid.checkNotNull(key);

		BlockState state = block.getState();
		if (MinecraftVersion.atLeast(V.v1_14)) {
			if (state instanceof TileState){
				return getNamespaced((TileState) state, key);
			}
		}

		final String value = state.hasMetadata(key) ? state.getMetadata(key).get(0).asString() : null;
		return Common.getOrNull(value);
	}

	private static String getNamespaced(final TileState tile, final String key) {
		final String value = tile.getPersistentDataContainer().get(new NamespacedKey(SimplePlugin.getInstance(), key), PersistentDataType.STRING);

		return Common.getOrNull(value);
	}

	/**
	 * Get metadata value of world by key, or null if none
	 * @return the value or null
	 */
	public static String getMetadata(final World world, final String key){
		final String value = world.getPersistentDataContainer().get(new NamespacedKey(SimplePlugin.getInstance(), key), PersistentDataType.STRING);

		return Common.getOrNull(value);
	}

	// ----------------------------------------------------------------------------------------
	// Checking for metadata
	// ----------------------------------------------------------------------------------------

	/**
	 * Return true if the given ItemStack has the given key stored at its compound
	 * tag {@link org.mineacademy.fo.constants.FoConstants.NBT#TAG}
	 */
	public static boolean hasMetadata(final ItemStack item, final String key) {
		Valid.checkBoolean(MinecraftVersion.atLeast(V.v1_7), "NBT ItemStack tags only support MC 1.7.10+");
		Valid.checkNotNull(item, "Item is null");

		if (CompMaterial.isAir(item.getType()))
			return false;

		final NBTItem nbt = new NBTItem(item);
		final NBTCompound tag = nbt.getCompound(FoConstants.NBT.TAG);

		return tag != null && tag.hasTag(key);
	}

	/**
	 * Returns true if the entity has the given metadata key.<br>
	 * First checks scoreboard tags, and then bukkit metadata.
	 */
	public static boolean hasMetadata(final Entity entity, final String key) {
		Valid.checkNotNull(entity);

		if (Remain.hasScoreboardTags())
			for (final String line : entity.getScoreboardTags())
				if (hasTag(line, key))
					return true;

		return entity.hasMetadata(key);
	}

	/**
	 * Returns true if the block has the given metadata key
	 */
	public static boolean hasMetadata(final Block block, final String key) {
		Valid.checkNotNull(block);
		Valid.checkNotNull(key);

		BlockState state = block.getState();
		if (MinecraftVersion.atLeast(V.v1_14)) {
			if (state instanceof TileState){
				return hasNamespaced((TileState) state, key);
			}
		}
		return state.hasMetadata(key);
	}

	private static boolean hasNamespaced(final PersistentDataHolder object, final String key) {
		return object.getPersistentDataContainer().has(new NamespacedKey(SimplePlugin.getInstance(), key), PersistentDataType.STRING);
	}

	// Parse the tag and get its value
	private static boolean hasTag(final String raw, final String tag) {
		final String[] parts = raw.split(DELIMITER);

		return parts.length == 3 && parts[0].equals(SimplePlugin.getNamed()) && parts[1].equals(tag);
	}

	/**
	 * Returns true if the world has the given metadata key
	 */
	public static boolean hasMetadata(World world, String key){
		return hasNamespaced(world, key);
	}

	// ----------------------------------------------------------------------------------------
	// Removing metadata
	// ----------------------------------------------------------------------------------------

	/**
	 * Remove metadata with the given key from the item
	 */
	public static ItemStack removeMetadata(final ItemStack item, final String key){
		Valid.checkBoolean(MinecraftVersion.atLeast(V.v1_7), "NBT ItemStack tags only support MC 1.7.10+");
		Valid.checkNotNull(item, "Item is null");

		if (CompMaterial.isAir(item.getType())) return null;

		final NBTItem nbt = new NBTItem(item);
		final NBTCompound tag = nbt.getCompound(FoConstants.NBT.TAG);

		tag.removeKey(key);
		return nbt.getItem();
	}

	/**
	 * Remove metadata with the given key from the entity
	 */
	public static void removeMetadata(final Entity entity, final String key){
		Valid.checkNotNull(entity);

		if (Remain.hasScoreboardTags()){
			for (final String line : entity.getScoreboardTags()){
				if (hasTag(line, key)){
					entity.removeScoreboardTag(line);
					return;
				}
			}
		}
		entity.removeMetadata(key, SimplePlugin.getInstance());
		MetadataFile.getInstance().removeMetadata(entity, key);
	}

	/**
	 * Remove metadata with the given key from the block
	 */
	public static void removeMetadata(final Block block, final String key){
		Valid.checkNotNull(block);
		Valid.checkNotNull(key);

		BlockState state = block.getState();
		if (MinecraftVersion.atLeast(V.v1_14)){
			if (state instanceof TileState){
				removeNamespaced((TileState) state, key);
				state.update();
				return;
			}
		}
		state.removeMetadata(key, SimplePlugin.getInstance());
		state.update();

		MetadataFile.getInstance().removeMetadata(state, key);
	}

	/**
	 * Remove metadata of the tile with the given key from persistent data container
	 */
	private static void removeNamespaced(final PersistentDataHolder object, final String key){
		if (hasNamespaced(object, key)){
			NamespacedKey namespacedKey = NamespacedKey.fromString(key, SimplePlugin.getInstance());
			if (namespacedKey != null){
				object.getPersistentDataContainer().remove(namespacedKey);
			}
		}
	}

	/**
	 * Remove metadata with the given key from the block
	 */
	public static void removeMetadata(final World world, final String key){
		removeNamespaced(world, key);
	}

	// ----------------------------------------------------------------------------------------
	// Managing temporary metadata
	// ----------------------------------------------------------------------------------------

	/**
	 * Set a temporary tag to the object. The object must implement {@link Metadatable}.
	 * This can be a block, a world or an entity.<br>
	 * If no value specified, it would be the same as the tag.
	 */
	public static void setTempMetadata(final Metadatable object, final String tag){
		setTempMetadata(object, tag, tag);
	}

	/**
	 * Set a temporary metadata to the object. The object must implement {@link Metadatable}.
	 * This can be a block, a world or an entity.
	 */
	public static void setTempMetadata(final Metadatable object, final String key, final Object value){
		object.setMetadata(key, new FixedMetadataValue(SimplePlugin.getInstance(), value));
	}

	/**
	 * Returns true if the object has the given temporary metadata
	 */
	public static boolean hasTempMetadata(final Metadatable object, final String key){
		return object.hasMetadata(key);
	}

	/**
	 * Return object metadata value or null if it has none
	 * <p>
	 * Only usable if you set it using the {@link #setTempMetadata(Metadatable, String, Object)} with the key parameter
	 * because otherwise the tag is the same as the value
	 */
	public static MetadataValue getTempMetadata(final Metadatable object, final String key){
		return hasTempMetadata(object, key) ? object.getMetadata(key).get(0) : null;
	}

	/**
	 * Remove temporary metadata from object
	 */
	public static void removeTempMetadata(final Metadatable object, final String key){
		if (hasTempMetadata(object, key)){
			object.removeMetadata(key, SimplePlugin.getInstance());
		}
	}

	/**
	 * Due to lack of persistent metadata implementation until Minecraft 1.14.x,
	 * we simply store them in a file during server restart and then apply
	 * as a temporary metadata for the Bukkit entities.
	 * <p>
	 * <b>Internal use only.</b>
	 */
	@AutoRegister
	public static final class MetadataFile extends YamlConfig {

		private static final Object LOCK = new Object();

		@Getter
		private static final MetadataFile instance = new MetadataFile();

		private final StrictMap<UUID, List<String>> entityMetadataMap = new StrictMap<>();
		private final StrictMap<Location, BlockCache> blockMetadataMap = new StrictMap<>();

		private MetadataFile() {
			this.setPathPrefix("Metadata");
			this.setSaveEmptyValues(false);

			this.loadConfiguration(NO_DEFAULT, FoConstants.File.DATA);
		}

		@Override
		protected void onLoad() {
			synchronized (LOCK) {
				this.loadEntities();

				this.loadBlockStates();
			}
		}

		@Override
		protected void onSave() {
			this.set("Entity", this.entityMetadataMap);
			this.set("Block", this.blockMetadataMap);
		}

		private void loadEntities() {
			synchronized (LOCK) {
				this.entityMetadataMap.clear();

				for (final String uuidName : this.getMap("Entity").keySet()) {
					final UUID uuid = UUID.fromString(uuidName);

					// Remove broken key
					if (!(this.getObject("Entity." + uuidName) instanceof List)) {
						this.set("Entity." + uuidName, null);

						continue;
					}

					final List<String> metadata = this.getStringList("Entity." + uuidName);
					final Entity entity = Remain.getEntity(uuid);

					// Check if the entity is still real
					if (!metadata.isEmpty() && entity != null && entity.isValid() && !entity.isDead()) {
						this.entityMetadataMap.put(uuid, metadata);

						this.applySavedMetadata(metadata, entity);
					}
				}

				this.set("Entity", this.entityMetadataMap);
			}
		}

		private void loadBlockStates() {
			synchronized (LOCK) {
				this.blockMetadataMap.clear();

				for (final String locationRaw : this.getMap("Block").keySet()) {
					final Location location = SerializeUtil.deserializeLoc(locationRaw);
					final BlockCache blockCache = this.get("Block." + locationRaw, BlockCache.class);

					final Block block = location.getBlock();

					// Check if the block remained the same
					if (blockCache.getType() == null || !CompMaterial.isAir(block) &&
							CompMaterial.fromBlock(block) == blockCache.getType()){
						this.blockMetadataMap.put(location, blockCache);
						this.applySavedMetadata(blockCache.getMetadata(), block);
					}
				}

				this.set("Block", this.blockMetadataMap);
			}
		}

		private void applySavedMetadata(final List<String> metadata, final Metadatable entity) {
			synchronized (LOCK) {
				for (final String metadataLine : metadata) {
					if (metadataLine.isEmpty())
						continue;

					final String[] lines = metadataLine.split(DELIMITER);
					Valid.checkBoolean(lines.length == 3, "Malformed metadata line for " + entity + ". Length 3 != " + lines.length + ". Data: " + metadataLine);

					final String key = lines[1];
					final String value = lines[2];

					entity.setMetadata(key, new FixedMetadataValue(SimplePlugin.getInstance(), value));
				}
			}
		}

		private void addMetadata(final Entity entity, @NonNull final String key, final String value) {
			synchronized (LOCK) {
				final List<String> metadata = this.entityMetadataMap.getOrPut(entity.getUniqueId(), new ArrayList<>());

				// Remove if value with this key already exists
				metadata.removeIf(meta -> getTag(meta, key) != null);

				if (value != null && !value.isEmpty()) {
					final String formatted = format(key, value);

					metadata.add(formatted);
				}

				this.save("Entity", this.entityMetadataMap);
			}
		}

		private void addMetadata(final BlockState blockState, final String key, final String value, boolean typed) {
			synchronized (LOCK) {
				final BlockCache blockCache = this.blockMetadataMap.getOrPut(blockState.getLocation(),
						new BlockCache(typed ? CompMaterial.fromBlock(blockState.getBlock()) : null, new ArrayList<>()));

				blockCache.getMetadata().removeIf(meta -> getTag(meta, key) != null);

				if (value != null && !value.isEmpty()) {
					final String formatted = format(key, value);

					blockCache.getMetadata().add(formatted);
				}

				// Save
				for (final Map.Entry<Location, BlockCache> entry : this.blockMetadataMap.entrySet()){
					this.set("Block." + SerializeUtil.serializeLoc(entry.getKey()), entry.getValue().serialize());
				}
				this.save("Block", this.blockMetadataMap);
			}
		}

		private void removeMetadata(final Entity entity, final String key){
			UUID uuid = entity.getUniqueId();
			List<String> tags = this.entityMetadataMap.get(uuid);
			if (tags == null) return;

			// Remove all tags with the given key
			tags.removeIf(meta -> getTag(meta, key) != null);

			// Remove this entity from the file completely if it has no more metadata
			if (tags.isEmpty()){
				this.entityMetadataMap.remove(uuid);
			}

			// Save
			this.save("Entity", this.entityMetadataMap);
		}

		private void removeMetadata(final BlockState state, final String key){
			Location location = state.getLocation();
			BlockCache cache = this.blockMetadataMap.get(location);
			if (cache == null) return;

			// Remove all tags with the given key
			cache.getMetadata().removeIf(meta -> getTag(meta, key) != null);

			// Remove this block from the file completely if it has no more metadata
			if (cache.getMetadata().isEmpty()){
				this.blockMetadataMap.remove(location);
			}

			// Save
			this.save("Block", this.blockMetadataMap);
		}

		@Getter
		@RequiredArgsConstructor
		public static final class BlockCache implements ConfigSerializable {
			private final CompMaterial type;
			private final List<String> metadata;

			public static BlockCache deserialize(final SerializedMap map) {
				final CompMaterial type = map.getCompMaterial("Type");
				final List<String> metadata = map.getStringList("Metadata");

				return new BlockCache(type, metadata);
			}

			@Override
			public SerializedMap serialize() {
				final SerializedMap map = new SerializedMap();

				if (type != null){
					map.put("Type", this.type.toString());
				}
				map.put("Metadata", this.metadata);

				return map;
			}
		}
	}
}
