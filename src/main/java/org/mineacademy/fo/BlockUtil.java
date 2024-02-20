package org.mineacademy.fo;

import com.google.common.collect.Sets;
import lombok.*;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility class for block manipulation.
 */
@SuppressWarnings({"unused", "DuplicatedCode"})
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockUtil {

	/**
	 * Matches all DOUBLE or STEP block names
	 */
	private static final Pattern SLAB_PATTERN = Pattern.compile("(?!DOUBLE).*STEP");

	/**
	 * The block faces we use while searching for all parts of the given
	 * tree upwards
	 */
	private static final BlockFace[] TREE_TRUNK_FACES = {
			BlockFace.UP, BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH
	};

	/**
	 * A list of safe blocks upon which a tree naturally grows
	 */
	private final static Set<String> TREE_GROUND_BLOCKS = Sets.newHashSet("GRASS_BLOCK", "COARSE_DIRT", "DIRT", "MYCELIUM", "PODZOL");

	/**
	 * The vertical gaps when creating locations for a bounding box,
	 * see {@link #getBoundingBox(Location, Location)}
	 */
	public static double BOUNDING_VERTICAL_GAP = 1;

	/**
	 * The horizontal gaps when creating locations for a bounding box,
	 * see {@link #getBoundingBox(Location, Location)}
	 */
	public static double BOUNDING_HORIZONTAL_GAP = 1;

	// ------------------------------------------------------------------------------------------------------------
	// Cuboid region manipulation
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Returns true if the given location is within the two vector cuboid bounds
	 */
	public static boolean isWithinCuboid(final Location location, final Location primary, final Location secondary) {
		final double locX = location.getX();
		final double locY = location.getY();
		final double locZ = location.getZ();

		final int x = primary.getBlockX();
		final int y = primary.getBlockY();
		final int z = primary.getBlockZ();

		final int x1 = secondary.getBlockX();
		final int y1 = secondary.getBlockY();
		final int z1 = secondary.getBlockZ();

		if (locX >= x && locX <= x1 || locX <= x && locX >= x1)
			if (locZ >= z && locZ <= z1 || locZ <= z && locZ >= z1)
				return locY >= y && locY <= y1 || locY <= y && locY >= y1;

		return false;
	}

	/**
	 * Return locations representing the bounding box (walls) of a chunk,
	 * used when rendering particle effects for example.
	 */
	public static Set<Location> getBoundingBox(@NonNull Chunk chunk) {
		final int minX = chunk.getX() << 4;
		final int minY = 0;
		final int minZ = chunk.getZ() << 4;

		final int maxX = minX | 15;
		final int maxY = chunk.getWorld().getMaxHeight();
		final int maxZ = minZ | 15;

		final Location primary = new Location(chunk.getWorld(), minX, minY, minZ);
		final Location secondary = new Location(chunk.getWorld(), maxX, maxY, maxZ);

		return getBoundingBox(primary, secondary);
	}

	/**
	 * Return locations representing the bounding box (walls) of a cuboid region,
	 * used when rendering particle effects
	 */
	public static Set<Location> getBoundingBox(final Location primary, final Location secondary) {
		final List<VectorHelper> shape = new ArrayList<>();

		final VectorHelper min = getMinimumPoint(primary, secondary);
		final VectorHelper max = getMaximumPoint(primary, secondary).add(1, 0, 1);

		final int height = getHeight(primary, secondary);

		final List<VectorHelper> bottomCorners = new ArrayList<>();

		bottomCorners.add(new VectorHelper(min.getX(), min.getY(), min.getZ()));
		bottomCorners.add(new VectorHelper(max.getX(), min.getY(), min.getZ()));
		bottomCorners.add(new VectorHelper(max.getX(), min.getY(), max.getZ()));
		bottomCorners.add(new VectorHelper(min.getX(), min.getY(), max.getZ()));

		for (int i = 0; i < bottomCorners.size(); i++) {
			final VectorHelper p1 = bottomCorners.get(i);
			final VectorHelper p2 = i + 1 < bottomCorners.size() ? bottomCorners.get(i + 1) : bottomCorners.get(0);

			final VectorHelper p3 = p1.add(0, height, 0);
			final VectorHelper p4 = p2.add(0, height, 0);
			shape.addAll(plotLine(p1, p2));
			shape.addAll(plotLine(p3, p4));
			shape.addAll(plotLine(p1, p3));

			for (double offset = BOUNDING_VERTICAL_GAP; offset < height; offset += BOUNDING_VERTICAL_GAP) {
				final VectorHelper p5 = p1.add(0.0D, offset, 0.0D);
				final VectorHelper p6 = p2.add(0.0D, offset, 0.0D);
				shape.addAll(plotLine(p5, p6));
			}
		}

		final Set<Location> locations = new HashSet<>();

		for (final VectorHelper vector : shape)
			locations.add(new Location(primary.getWorld(), vector.getX(), vector.getY(), vector.getZ()));

		return locations;
	}

	/**
	 * Get a set of locations that form a 2D frame (in X-Z dimension).<br>
	 * If locations Y coordinates are not equal, the
	 * {@link #get3DFrame(Location, Location, boolean)} method is used instead.
	 * @param first the first location
	 * @param second the second location
	 * @param reversed if true, returns the reversed set of blocks
	 * @return the set of locations
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static Set<Location> get2DFrame(Location first, Location second, boolean reversed){
		Valid.checkBoolean(first.getWorld() != null, "The first location's world is null");
		Valid.checkBoolean(second.getWorld() != null, "The second location's world is null");
		Valid.checkBoolean(first.getWorld().equals(second.getWorld()), "The worlds do not equal");

		if (first.getY() != second.getY()){
			Logger.printErrors(
					"Warning: BlockUtil#get2DFrame: the given first location's Y coordinate is not equal to the second location's Y.",
					"Using the BlockUtil#get3DFrame method instead."
			);
			return get3DFrame(first, second, reversed);
		}

		World world = first.getWorld();
		int y = first.getBlockY();

		Set<Location> set = new HashSet<>();
		Set<Location> reversedSet = new HashSet<>();

		int minX = Math.min(first.getBlockX(), second.getBlockX());
		int maxX = Math.max(first.getBlockX(), second.getBlockX());
		int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
		int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

		for (int x = minX; x <= maxX; x++){
			for (int z = minZ; z <= maxZ; z++) {
				Location loc = new Location(world, x, y, z);

				// Check if the block is on the border
				if (x == minX || x == maxX || z == minZ || z == maxZ){
					set.add(loc);
				} else {
					reversedSet.add(loc);
				}

			}
		}

		return reversed ? reversedSet : set;
	}

	/**
	 * Get a set of locations that form a 3D frame.<br>
	 * If locations Y coordinates are equal, the {@link #get2DFrame(Location, Location, boolean)}
	 * method is used instead for better performance.
	 * @param first the first location
	 * @param second the second location
	 * @param reversed if true, returns the reversed set of blocks
	 * @return the set of locations
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static Set<Location> get3DFrame(Location first, Location second, boolean reversed){
		Valid.checkBoolean(first.getWorld() != null, "The first location's world is null");
		Valid.checkBoolean(second.getWorld() != null, "The second location's world is null");
		Valid.checkBoolean(first.getWorld().equals(second.getWorld()), "The worlds do not equal");

		if (first.getY() == second.getY()){
			return get2DFrame(first, second, reversed);
		}

		World world = first.getWorld();
		Set<Location> set = new HashSet<>();
		Set<Location> reversedSet = new HashSet<>();

		int minX = Math.min(first.getBlockX(), second.getBlockX());
		int maxX = Math.max(first.getBlockX(), second.getBlockX());
		int minY = Math.min(first.getBlockY(), second.getBlockY());
		int maxY = Math.max(first.getBlockY(), second.getBlockY());
		int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
		int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

		for (int x = minX; x <= maxX; x++){
			for (int y = minY; y <= maxY; y++){
				for (int z = minZ; z <= maxZ; z++) {
					Location loc = new Location(world, x, y, z);

					// Check if the block is on the border
					if (y == maxY && (z == minZ || z == maxZ) || y == maxY && (x == minX || x == maxX) ||
							y == minY && (z == minZ || z == maxZ) || y == minY && (x == minX || x == maxX) ||
							x == minX && (z == minZ || z == maxZ) || x == maxX && (z == minZ || z == maxZ)
					){
						set.add(loc);
					} else {
						reversedSet.add(loc);
					}

				}
			}
		}

		return reversed ? reversedSet : set;
	}

	private static List<VectorHelper> plotLine(final VectorHelper p1, final VectorHelper p2) {
		final List<VectorHelper> ShapeVectors = new ArrayList<>();

		final int points = (int) (p1.distance(p2) / BOUNDING_HORIZONTAL_GAP) + 1;
		final double length = p1.distance(p2);
		final double gap = length / (points - 1);

		final VectorHelper gapShapeVector = p2.subtract(p1).normalize().multiply(gap);

		for (int i = 0; i < points; i++) {
			final VectorHelper currentPoint = p1.add(gapShapeVector.multiply(i));

			ShapeVectors.add(currentPoint);
		}

		return ShapeVectors;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Spherical manipulation
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Get all locations within the given 3D spherical radius, hollow or not
	 * <p>
	 * NOTE: Calling this operation causes performance penaulty (>100ms for 30 radius!), be careful.
	 */
	public static Set<Location> getSphere(final Location location, final int radius, final boolean hollow) {
		final Set<Location> blocks = new HashSet<>();
		final World world = location.getWorld();
		final int X = location.getBlockX();
		final int Y = location.getBlockY();
		final int Z = location.getBlockZ();
		final int radiusSquared = radius * radius;

		if (hollow) {
			for (int x = X - radius; x <= X + radius; x++)
				for (int y = Y - radius; y <= Y + radius; y++)
					for (int z = Z - radius; z <= Z + radius; z++)
						if ((X - x) * (X - x) + (Y - y) * (Y - y) + (Z - z) * (Z - z) <= radiusSquared)
							blocks.add(new Location(world, x, y, z));

			return makeHollow(blocks, true);
		}

		for (int x = X - radius; x <= X + radius; x++)
			for (int y = Y - radius; y <= Y + radius; y++)
				for (int z = Z - radius; z <= Z + radius; z++)
					if ((X - x) * (X - x) + (Y - y) * (Y - y) + (Z - z) * (Z - z) <= radiusSquared)
						blocks.add(new Location(world, x, y, z));

		return blocks;
	}

	/**
	 * Get all locations within the given 2D circle radius, hollow or full circle
	 * <p>
	 * NOTE: Calling this operation causes performance penaulty (>100ms for 30 radius!), be careful.
	 */
	public static Set<Location> getCircle(final Location location, final int radius, final boolean hollow) {
		final Set<Location> blocks = new HashSet<>();
		final World world = location.getWorld();

		final int initialX = location.getBlockX();
		final int initialY = location.getBlockY();
		final int initialZ = location.getBlockZ();
		final int radiusSquared = radius * radius;

		if (hollow) {
			for (int x = initialX - radius; x <= initialX + radius; x++)
				for (int z = initialZ - radius; z <= initialZ + radius; z++)
					if ((initialX - x) * (initialX - x) + (initialZ - z) * (initialZ - z) <= radiusSquared)
						blocks.add(new Location(world, x, initialY, z));

			return makeHollow(blocks, false);
		}

		for (int x = initialX - radius; x <= initialX + radius; x++)
			for (int z = initialZ - radius; z <= initialZ + radius; z++)
				if ((initialX - x) * (initialX - x) + (initialZ - z) * (initialZ - z) <= radiusSquared)
					blocks.add(new Location(world, x, initialY, z));

		return blocks;
	}

	/**
	 * Creates a new list of outer location points from all given points
	 */
	private static Set<Location> makeHollow(final Set<Location> blocks, final boolean sphere) {
		final Set<Location> edge = new HashSet<>();

		if (!sphere) {
			for (final Location location : blocks) {
				final World world = location.getWorld();
				final int x = location.getBlockX();
				final int y = location.getBlockY();
				final int z = location.getBlockZ();

				final Location front = new Location(world, x + 1, y, z);
				final Location back = new Location(world, x - 1, y, z);
				final Location left = new Location(world, x, y, z + 1);
				final Location right = new Location(world, x, y, z - 1);

				if (!(blocks.contains(front) && blocks.contains(back) && blocks.contains(left) && blocks.contains(right)))
					edge.add(location);

			}
			return edge;
		}

		for (final Location location : blocks) {
			final World world = location.getWorld();

			final int x = location.getBlockX();
			final int y = location.getBlockY();
			final int z = location.getBlockZ();

			final Location front = new Location(world, x + 1, y, z);
			final Location back = new Location(world, x - 1, y, z);
			final Location left = new Location(world, x, y, z + 1);
			final Location right = new Location(world, x, y, z - 1);
			final Location top = new Location(world, x, y + 1, z);
			final Location bottom = new Location(world, x, y - 1, z);

			if (!(blocks.contains(front) && blocks.contains(back) && blocks.contains(left) && blocks.contains(right) && blocks.contains(top) && blocks.contains(bottom)))
				edge.add(location);
		}

		return edge;

	}

	// ------------------------------------------------------------------------------------------------------------
	// Getting blocks within a cuboid
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Get all blocks within the two cuboid bounds (may take a while)
	 *
	 * @param primary the first position
	 * @param secondary the seconds position
	 * @return the list of blocks
	 */
	public static List<Block> getBlocks(final Location primary, final Location secondary) {
		Valid.checkNotNull(primary, "Primary region point must be set!");
		Valid.checkNotNull(secondary, "Secondary region point must be set!");
		Valid.checkNotNull(primary.getWorld());

		final List<Block> blocks = new ArrayList<>();

		final int topBlockX = Math.max(primary.getBlockX(), secondary.getBlockX());
		final int bottomBlockX = Math.min(primary.getBlockX(), secondary.getBlockX());

		final int topBlockY = Math.max(primary.getBlockY(), secondary.getBlockY());
		final int bottomBlockY = Math.min(primary.getBlockY(), secondary.getBlockY());

		final int topBlockZ = Math.max(primary.getBlockZ(), secondary.getBlockZ());
		final int bottomBlockZ = Math.min(primary.getBlockZ(), secondary.getBlockZ());

		for (int x = bottomBlockX; x <= topBlockX; x++)
			for (int z = bottomBlockZ; z <= topBlockZ; z++)
				for (int y = bottomBlockY; y <= topBlockY; y++) {
					final Block block = primary.getWorld().getBlockAt(x, y, z);

					blocks.add(block);
				}

		return blocks;
	}

	/**
	 * Return all blocks in the given chunk
	 */
	public static List<Block> getBlocks(@NonNull Chunk chunk) {
		final List<Block> blocks = new ArrayList<>();

		final int minX = chunk.getX() << 4;
		final int minZ = chunk.getZ() << 4;

		final int maxX = minX | 15;
		final int maxY = chunk.getWorld().getMaxHeight();
		final int maxZ = minZ | 15;

		for (int x = minX; x <= maxX; ++x)
			for (int y = 0; y <= maxY; ++y)
				for (int z = minZ; z <= maxZ; ++z)
					blocks.add(chunk.getBlock(x, y, z));

		return blocks;
	}

	/**
	 * Get all the blocks in a specific area centered around the Location passed in
	 */
	public static List<Block> getBlocks(final Location loc, final int height, final int radius) {
		final List<Block> blocks = new ArrayList<>();

		for (int y = 0; y < height; y++)
			for (int x = -radius; x <= radius; x++)
				for (int z = -radius; z <= radius; z++) {
					final Block checkBlock = loc.getBlock().getRelative(x, y, z);

					if (checkBlock.getType() != Material.AIR)
						blocks.add(checkBlock);
				}
		return blocks;
	}

	/**
	 * Get locations from two cuboid bounds.
	 *
	 * @param first the first bound
	 * @param second the second bound
	 * @param toCenter if true, the location will be centered to the center of the block
	 * @return the list of locations within bounds
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static List<Location> getLocations(@NotNull Location first, @NotNull Location second, boolean toCenter){
		Valid.checkNotNull(first.getWorld(), "First location world is null");
		Valid.checkNotNull(second.getWorld(), "Second location world is null");
		Valid.checkBoolean(first.getWorld().equals(second.getWorld()),
				"First location world (" + first.getWorld().getName() +
						") differs from the second location world (" +
						second.getWorld().getName());

		World world = first.getWorld();
		List<Location> locations = new ArrayList<>();
		int minX = Math.min(first.getBlockX(), second.getBlockX());
		int maxX = Math.max(first.getBlockX(), second.getBlockX());
		int minY = Math.min(first.getBlockY(), second.getBlockY());
		int maxY = Math.max(first.getBlockY(), second.getBlockY());
		int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
		int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());
		for (int x = minX; x <= maxX; x++){
			for (int y = minY; y <= maxY; y++){
				for (int z = minZ; z <= maxZ; z++) {
					Location location = new Location(world, x, y, z);
					locations.add(toCenter ? getBlockCenter(location) : location);
				}
			}
		}
		return locations;
	}

	/**
	 * Center the location to the center of the block
	 * @param location the location
	 * @return the centered location
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static Location getBlockCenter(@NonNull Location location){
		Location exactLocation = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
		exactLocation.add(0.5, 0, 0.5);
		return exactLocation;
	}

	/**
	 * Return chunks around the given location
	 */
	public static List<Chunk> getChunks(final Location location, final int radius) {
		final HashSet<Chunk> addedChunks = new HashSet<>();
		final World world = location.getWorld();
		Valid.checkNotNull(world);

		final int chunkX = location.getBlockX() >> 4;
		final int chunkZ = location.getBlockZ() >> 4;

		for (int x = chunkX - radius; x <= chunkX + radius; ++x)
			for (int z = chunkZ - radius; z <= chunkZ + radius; ++z)
				if (world.isChunkLoaded(x, z))
					addedChunks.add(world.getChunkAt(x, z));

		return new ArrayList<>(addedChunks);
	}

	/**
	 * Return all x-z locations within a chunk
	 */
	public static List<Location> getXZLocations(Chunk chunk) {
		final List<Location> found = new ArrayList<>();

		final int chunkX = chunk.getX() << 4;
		final int chunkZ = chunk.getZ() << 4;

		for (int x = chunkX; x < chunkX + 16; x++)
			for (int z = chunkZ; z < chunkZ + 16; z++)
				found.add(new Location(chunk.getWorld(), x, 0, z));

		return found;
	}

	/**
	 * Return all leaves/logs upwards connected to that given tree block.
	 * Parts are sorted according to their Y coordinate from lowest to highest.<br>
	 * Does not catch corner blocks that are not directly adjacent.<br>
	 * For more accuracy use {@link #getTreePartsUp(Block, Block, CompMaterial)}
	 * @param treeBase the root of the tree
	 * @return the blocks
	 */
	public static List<Block> getTreePartsUp(final Block treeBase) {
		final Material baseMaterial = treeBase.getState().getType();

		final String logType = MinecraftVersion.atLeast(V.v1_13) ? baseMaterial.toString() : "LOG";
		final String leaveType = MinecraftVersion.atLeast(V.v1_13) ? logType.replace("_LOG", "") + "_LEAVES" : "LEAVES";

		final Set<Block> treeParts = new HashSet<>();
		final Set<Block> toSearch = new HashSet<>();
		final Set<Block> searched = new HashSet<>();

		toSearch.add(treeBase.getRelative(BlockFace.UP));
		searched.add(treeBase);

		int cycle;

		for (cycle = 0; cycle < 1000 && !toSearch.isEmpty(); cycle++) {
			final Block block = toSearch.iterator().next();

			toSearch.remove(block);
			searched.add(block);

			if (block.getType().toString().equals(logType) || block.getType().toString().equals(leaveType)) {
				treeParts.add(block);

				for (final BlockFace face : TREE_TRUNK_FACES) {
					final Block relative = block.getRelative(face);

					if (!searched.contains(relative))
						toSearch.add(relative);

				}

			} else if (!block.getType().isTransparent())
				return new ArrayList<>();
		}

		return new ArrayList<>(treeParts);
	}

	/**
	 * Return all leaves/logs upwards connected to that given tree block.<br>
	 * Catches all corner blocks that are not directly adjacent.
	 *
	 * @param treeBase the tree root
	 * @param treeTrunkTop the top of the tree's trunk (use {@link #getTopTreeTrunkBlock(Block)} to calculate it)
	 * @param logMaterial the material of the tree log
	 * @return the blocks
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static List<Block> getTreePartsUp(Block treeBase, Block treeTrunkTop, CompMaterial logMaterial) {
		String logType = MinecraftVersion.atLeast(MinecraftVersion.V.v1_13) ? logMaterial.getMaterial().toString() : "LOG";
		String leaveType = MinecraftVersion.atLeast(MinecraftVersion.V.v1_13) ? logType.replace("_LOG", "") + "_LEAVES" : "LEAVES";
		Set<Block> toSearch = new HashSet<>(getBlocks(treeBase.getLocation(), treeTrunkTop.getLocation()));
		Set<Block> initialBlocks = new HashSet<>(toSearch);
		Set<Block> searched = new HashSet<>();
		Set<Block> treeParts = new HashSet<>();

		for(int cycle = 0; cycle < 1000 && !toSearch.isEmpty(); ++cycle) {
			Block block = toSearch.iterator().next();
			toSearch.remove(block);
			searched.add(block);

			if (initialBlocks.contains(block) || block.getType().toString().equals(logType) || block.getType().toString().equals(leaveType)){
				treeParts.add(block);

				for (Block bl : getNeighbourBlocksUp(block)){
					if (bl.getType().toString().equals(logType) || bl.getType().toString().equals(leaveType)){
						if (!searched.contains(bl)) {
							toSearch.add(bl);
						}
					}
				}
			}
		}

		return new ArrayList<>(treeParts);
	}

	/**
	 * Get neighbour block from the anchor up.
	 * @param anchor the anchor block
	 * @return the list of neighbours up
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static List<Block> getNeighbourBlocksUp(Block anchor){
		Location loc = anchor.getLocation();
		Set<Block> blocks = new HashSet<>();

		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					blocks.add(loc.clone().add(x, y, z).getBlock());
					blocks.remove(anchor);
				}
			}
		}

		return new ArrayList<>(blocks);
	}

	/**
	 * Get the top block of the tree trunk.<br>
	 * Works both for 1x1 and 2x2 trunks.
	 * @param treeBase the root of the tree (the place where the sapling was)
	 * @return the top trunk block
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static Block getTopTreeTrunkBlock(Block treeBase){
		World world = treeBase.getWorld();
		Material baseMaterial = treeBase.getState().getType();
		String logType = MinecraftVersion.atLeast(MinecraftVersion.V.v1_13) ? baseMaterial.toString() : "LOG";

		int treeBaseSide = -1;
		Block baseNW = null, baseNE = null, baseSW = null, baseSE = null;

		if (treeBase.getRelative(BlockFace.NORTH).getType().toString().equals(logType)){
			if (treeBase.getRelative(BlockFace.WEST).getType().toString().equals(logType)){
				baseNW = treeBase.getLocation().clone().add(-1, 0, -1).getBlock();
				if (baseNW.getType().toString().equals(logType)){
					treeBaseSide = 4;
					baseSE = treeBase;
					baseNE = treeBase.getRelative(BlockFace.NORTH);
					baseSW = treeBase.getRelative(BlockFace.WEST);
				}
			} else if (treeBase.getRelative(BlockFace.EAST).getType().toString().equals(logType)){
				baseNE = treeBase.getLocation().clone().add(1, 0, -1).getBlock();
				if (baseNE.getType().toString().equals(logType)){
					treeBaseSide = 3;
					baseSW = treeBase;
					baseNW = treeBase.getRelative(BlockFace.NORTH);
					baseSE = treeBase.getRelative(BlockFace.EAST);
				}
			}
		} else if (treeBase.getRelative(BlockFace.SOUTH).getType().toString().equals(logType)){
			if (treeBase.getRelative(BlockFace.WEST).getType().toString().equals(logType)){
				baseSW = treeBase.getLocation().clone().add(-1, 0, 1).getBlock();
				if (baseSW.getType().toString().equals(logType)){
					treeBaseSide = 2;
					baseNE = treeBase;
					baseSE = treeBase.getRelative(BlockFace.SOUTH);
					baseNW = treeBase.getRelative(BlockFace.WEST);
				}
			} else if (treeBase.getRelative(BlockFace.EAST).getType().toString().equals(logType)){
				baseSE = treeBase.getLocation().clone().add(1, 0, 1).getBlock();
				if (baseSE.getType().toString().equals(logType)){
					treeBaseSide = 1;
					baseNW = treeBase;
					baseNE = treeBase.getRelative(BlockFace.EAST);
					baseSW = treeBase.getRelative(BlockFace.SOUTH);
				}
			}
		}

		// The tree is 2x2 (2 blocks wide and 2 blocks long)
		if (treeBaseSide != -1){
			Block topNW = getHighestLogBlock(baseNW);
			Block topNE = getHighestLogBlock(baseNE);
			Block topSW = getHighestLogBlock(baseSW);
			Block topSE = getHighestLogBlock(baseSE);

			int maxMinY = Math.min(Math.min(topNW.getY(), topNE.getY()), Math.min(topSW.getY(), topSE.getY()));
			if (treeBaseSide == 1){
				return world.getBlockAt(topSE.getX(), maxMinY, topSE.getZ());
			}
			else if (treeBaseSide == 2){
				return world.getBlockAt(topSW.getX(), maxMinY, topSW.getZ());
			}
			else if (treeBaseSide == 3){
				return world.getBlockAt(topNE.getX(), maxMinY, topNE.getZ());
			}
			else {
				return world.getBlockAt(topNW.getX(), maxMinY, topNW.getZ());
			}
		}
		else {
			return getHighestLogBlock(treeBase);
		}
	}

	/**
	 * Get the highest log block using the binary search algorithm
	 * @param baseBlock the base block to search up from
	 * @return the highest block in that Y-line
	 * @author Rubix327
	 * @since 6.2.5.11
	 */
	public static Block getHighestLogBlock(Block baseBlock){
		World world = baseBlock.getWorld();
		Material baseMaterial = baseBlock.getState().getType();
		String logType = MinecraftVersion.atLeast(MinecraftVersion.V.v1_13) ? baseMaterial.toString() : "LOG";

		int x = baseBlock.getX();
		int z = baseBlock.getZ();
		int low = baseBlock.getY();
		int high = baseBlock.getY() + 32;
		int mid;

		while (low <= high) {
			mid = low + (high - low) / 2;
			if (world.getBlockAt(x, mid, z).getType().toString().equals(logType)) {
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}

		return world.getBlockAt(x, high, z);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Block type checkers
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Returns true whether the given block is a "LOG" type and we perform a search
	 * down to the bottom most connected block to find if that stands on tree ground blocks.
	 */
	public static boolean isLogOnGround(Block treeBaseBlock) {
		// Validates the block passed in is actually a log
		if (!(CompMaterial.isLog(treeBaseBlock.getType())))
			return false;

		// Reach for the bottom most tree-like block
		while (CompMaterial.isLog(treeBaseBlock.getType()))
			treeBaseBlock = treeBaseBlock.getRelative(BlockFace.DOWN);

		return TREE_GROUND_BLOCKS.contains(CompMaterial.fromMaterial(treeBaseBlock.getType()).toString());
	}

	/**
	 * Will a FallingBlock which lands on this Material break and drop to the
	 * ground?
	 *
	 * @param material to check
	 * @return boolean
	 */
	public static boolean isBreakingFallingBlock(final Material material) {
		return material.isTransparent() &&
				material != CompMaterial.NETHER_PORTAL.getMaterial() &&
				material != CompMaterial.END_PORTAL.getMaterial() ||
				material == CompMaterial.COBWEB.getMaterial() ||
				material == Material.DAYLIGHT_DETECTOR ||
				CompMaterial.isTrapDoor(material) ||
				material == CompMaterial.OAK_SIGN.getMaterial() ||
				CompMaterial.isWallSign(material) ||
				// Match all slabs besides double slab
				SLAB_PATTERN.matcher(material.name()).matches();
	}

	/**
	 * Return true when the given material is a tool, e.g. doesn't stack
	 */
	public static boolean isTool(final Material material) {
		return material.name().endsWith("AXE") // axe & pickaxe
				|| material.name().endsWith("SPADE")
				|| material.name().endsWith("SWORD")
				|| material.name().endsWith("HOE")
				|| material.name().endsWith("BUCKET") // water, milk, lava,..
				|| material == CompMaterial.BOW.getMaterial()
				|| material == CompMaterial.FISHING_ROD.getMaterial()
				|| material == CompMaterial.CLOCK.getMaterial()
				|| material == CompMaterial.COMPASS.getMaterial()
				|| material == CompMaterial.FLINT_AND_STEEL.getMaterial();
	}

	/**
	 * Return true if the material is an armor
	 */
	public static boolean isArmor(final Material material) {
		return material.name().endsWith("HELMET")
				|| material.name().endsWith("CHESTPLATE")
				|| material.name().endsWith("LEGGINGS")
				|| material.name().endsWith("BOOTS");
	}

	/**
	 * Returns true if block is safe to select
	 */
	public static boolean isForBlockSelection(final Material material) {
		if (!material.isBlock() || material == Material.AIR) {
			return false;
		}

		try {
			if (material.isInteractable()) // Ignore chests etc.
				return false;

		} catch (final Throwable ignored) {}

		try {
			if (material.hasGravity()) // Ignore falling blocks
				return false;
		} catch (final Throwable ignored) {}

		return material.isSolid();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Finding blocks and locations
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Scan the location from top to bottom to find the highest Y coordinate that is not air and not snow.
	 * This will return the free coordinate above the snow layer.
	 *
	 * @return the y coordinate, or -1 if not found
	 */
	public static int findHighestBlockNoSnow(final Location location) {
		Valid.checkNotNull(location.getWorld());
		return findHighestBlockNoSnow(location.getWorld(), location.getBlockX(), location.getBlockZ());
	}

	/**
	 * Scan the location from top to bottom to find the highest Y coordinate that is not air and not snow.
	 * This will return the free coordinate above the snow layer.
	 *
	 * @return the y coordinate, or -1 if not found
	 */
	public static int findHighestBlockNoSnow(final World world, final int x, final int z) {
		for (int y = world.getMaxHeight() - 1; y > 0; y--) {
			final Block block = world.getBlockAt(x, y, z);

			if (!CompMaterial.isAir(block)) {

				if (block.getType() == CompMaterial.SNOW_BLOCK.getMaterial())
					return -1;

				if (block.getType() == CompMaterial.SNOW.getMaterial())
					continue;

				return y + 1;
			}
		}

		return -1;
	}

	/**
	 * Scans the location from top to bottom to find the highest Y non-air coordinate that matches
	 * the given predicate.
	 */
	public static int findHighestBlock(final Location location, final Predicate<Material> predicate) {
		Valid.checkNotNull(location.getWorld());
		return findHighestBlock(location.getWorld(), location.getBlockX(), location.getBlockZ(), predicate);
	}

	/**
	 * Scans the location from top to bottom to find the highest Y non-air coordinate that matches
	 * the given predicate. For nether worlds, we recommend you see {@link #findHighestNetherAirBlock(World, int, int)}
	 *
	 * @return the y coordinate, or -1 if not found
	 */
	public static int findHighestBlock(final World world, final int x, final int z, final Predicate<Material> predicate) {
		final int minHeight = MinecraftVersion.atLeast(V.v1_18) ? world.getMinHeight() : 0;

		for (int y = world.getMaxHeight() - 1; y > minHeight; y--) {
			final Block block = world.getBlockAt(x, y, z);

			if (!CompMaterial.isAir(block) && predicate.test(block.getType()))
				return y + 1;
		}

		return -1;
	}

	/**
	 * Scans the coordinates to find the highest Y non-air coordinate that matches
	 * the given predicate. For nether worlds, we recommend you see {@link #findHighestNetherAirBlock(World, int, int)}
	 */
	public static int findAirBlock(final Location location, final boolean topDown, final Predicate<Material> predicate) {
		return findAirBlock(location.getWorld(), location.getBlockX(), location.getBlockZ(), topDown, predicate);
	}

	/**
	 * Scans the coordinates to find the highest Y non-air coordinate that matches
	 * the given predicate. For nether worlds, we recommend you see {@link #findHighestNetherAirBlock(World, int, int)}
	 */
	public static int findAirBlock(final World world, final int x, final int z, final boolean topDown, final Predicate<Material> predicate) {
		final int minHeight = (MinecraftVersion.atLeast(V.v1_18) ? world.getMinHeight() : 0) + 1;

		if (topDown)
			for (int y = world.getMaxHeight() - 1; y > minHeight; y--) {
				final Block block = world.getBlockAt(x, y, z);

				if (!CompMaterial.isAir(block) && predicate.test(block.getType()))
					return y + 1;
			}
		else
			for (int y = minHeight; y < world.getMaxHeight() - 1; y++) {
				final Block block = world.getBlockAt(x, y, z);
				final Block blockAbove = block.getRelative(BlockFace.UP);
				final Block blockTwoAbove = blockAbove.getRelative(BlockFace.UP);

				if (CompMaterial.isAir(blockAbove) && CompMaterial.isAir(blockTwoAbove) && predicate.test(block.getType()))
					return y + 1;
			}

		return -1;
	}

	/**
	 * @see #findHighestNetherAirBlock(World, int, int)
	 */
	public static int findHighestNetherAirBlock(@NonNull Location location) {
		Valid.checkNotNull(location.getWorld());
		return findHighestNetherAirBlock(location.getWorld(), location.getBlockX(), location.getBlockZ());
	}

	/**
	 * Returns the first air block that has air block above it and a solid block below. Useful for finding
	 * nether location from the bottom up to spawn mobs (not spawning them on the top bedrock as per {@link #findHighestBlock(Location, Predicate)}).
	 */
	public static int findHighestNetherAirBlock(@NonNull World world, int x, int z) {
		Valid.checkBoolean(world.getEnvironment() == Environment.NETHER, "findHighestNetherAirBlock must be called in nether worlds, " + world.getName() + " is of type " + world.getEnvironment());

		for (int y = 0; y < world.getMaxHeight(); y++) {
			final Block block = world.getBlockAt(x, y, z);
			final Block above = block.getRelative(BlockFace.UP);
			final Block below = block.getRelative(BlockFace.DOWN);

			if (CompMaterial.isAir(block) && CompMaterial.isAir(above) && !CompMaterial.isAir(below) && below.getType().isSolid())
				return y;
		}

		return -1;
	}

	/**
	 * Returns the closest location to the given one of the given locations
	 */
	public static Location findClosestLocation(Location location, List<Location> locations) {
		locations = new ArrayList<>(locations);
		final Location playerLocation = location;

		locations.sort(Comparator.comparingDouble(f -> f.distance(playerLocation)));
		return locations.get(0);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Shooting blocks
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Shoot the given block to the sky with the given velocity (maybe your arrow velocity?)
	 * and can even make the block burn on impact. The shot block is set to air
	 */
	public static FallingBlock shootBlock(final Block block, final Vector velocity) {
		return shootBlock(block, velocity, 0D);
	}

	/**
	 * Shoot the given block to the sky with the given velocity (maybe your arrow velocity?)
	 * and can even make the block burn on impact. The shot block is set to air
	 * <p>
	 * We adjust velocity a bit using random to add a bit for more realism, if you do not
	 * want this, use {@link #spawnFallingBlock(Block, Vector)}
	 *
	 * @param burnOnFallChance from 0.0 to 1.0
	 */
	public static FallingBlock shootBlock(final Block block, final Vector velocity, final double burnOnFallChance) {
		if (!canShootBlock(block))
			return null;

		final FallingBlock falling = Remain.spawnFallingBlock(block.getLocation().clone().add(0.5, 0, 0.5), block.getType());

		{ // Set velocity to reflect the given velocity but change a bit for more realism
			final double x = MathUtil.range(velocity.getX(), -2, 2) * 0.5D;
			final double y = Math.random();
			final double z = MathUtil.range(velocity.getZ(), -2, 2) * 0.5D;

			falling.setVelocity(new Vector(x, y, z));
		}

		if (RandomUtil.chanceD(burnOnFallChance) && block.getType().isBurnable())
			scheduleBurnOnFall(falling);

		// Prevent drop
		falling.setDropItem(false);

		// Remove the block
		block.setType(Material.AIR);

		return falling;
	}

	/**
	 * Just spawns the falling block without adjusting its velocity
	 */
	public static FallingBlock spawnFallingBlock(final Block block, final Vector velocity) {
		final FallingBlock falling = Remain.spawnFallingBlock(block.getLocation().clone().add(0.5, 0, 0.5), block.getType());

		// Apply velocity
		falling.setVelocity(velocity);

		// Prevent drop
		falling.setDropItem(false);

		// Remove the block
		block.setType(Material.AIR);

		return falling;
	}

	/**
	 * Return the allowed material types to shoot this block
	 */
	private static boolean canShootBlock(final Block block) {
		final Material material = block.getType();

		return !CompMaterial.isAir(material) && (material.toString().contains("STEP") || material.toString().contains("SLAB") || BlockUtil.isForBlockSelection(material));
	}

	/**
	 * Schedule to set the flying block on fire upon impact
	 */
	private static void scheduleBurnOnFall(final FallingBlock block) {
		EntityUtil.trackFalling(block, () -> {
			final Block upperBlock = block.getLocation().getBlock().getRelative(BlockFace.UP);

			if (upperBlock.getType() == Material.AIR)
				upperBlock.setType(Material.FIRE);
		});
	}

	// ------------------------------------------------------------------------------------------------------------
	// Helper classes
	// ------------------------------------------------------------------------------------------------------------

	private static VectorHelper getMinimumPoint(final Location pos1, final Location pos2) {
		return new VectorHelper(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
	}

	private static VectorHelper getMaximumPoint(final Location pos1, final Location pos2) {
		return new VectorHelper(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
	}

	private static int getHeight(final Location pos1, final Location pos2) {
		final VectorHelper min = getMinimumPoint(pos1, pos2);
		final VectorHelper max = getMaximumPoint(pos1, pos2);

		return (int) (max.getY() - min.getY() + 1.0D);
	}

	@RequiredArgsConstructor
	private final static class VectorHelper {

		@Getter
		private final double x, y, z;

		public VectorHelper add(final VectorHelper other) {
			return this.add(other.x, other.y, other.z);
		}

		public VectorHelper add(final double x, final double y, final double z) {
			return new VectorHelper(this.x + x, this.y + y, this.z + z);
		}

		public VectorHelper subtract(final VectorHelper other) {
			return this.subtract(other.x, other.y, other.z);
		}

		public VectorHelper subtract(final double x, final double y, final double z) {
			return new VectorHelper(this.x - x, this.y - y, this.z - z);
		}

		public VectorHelper multiply(final double n) {
			return new VectorHelper(this.x * n, this.y * n, this.z * n);
		}

		public VectorHelper divide(final double n) {
			return new VectorHelper(this.x / n, this.y / n, this.z / n);
		}

		public double length() {
			return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
		}

		public double distance(final VectorHelper other) {
			return Math.sqrt(Math.pow(other.x - this.x, 2) +
					Math.pow(other.y - this.y, 2) +
					Math.pow(other.z - this.z, 2));
		}

		public VectorHelper normalize() {
			return this.divide(this.length());
		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof VectorHelper))
				return false;

			final VectorHelper other = (VectorHelper) obj;
			return other.x == this.x && other.y == this.y && other.z == this.z;
		}

		@Override
		public String toString() {
			return "(" + this.x + ", " + this.y + ", " + this.z + ")";
		}
	}
}
