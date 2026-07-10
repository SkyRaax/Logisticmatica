package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.ItemType;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import com.skyraax.logisticmatica.Logisticmatica;

/**
 * Two things: the set of containers the player has <em>marked</em> as tracked, and a cache of the
 * contents of <em>every</em> container the player has looked into.
 *
 * <p>Caching every opened container (not just the marked ones) means marking and unmarking takes
 * effect immediately — no need to re-open a chest after marking it. The cache is also what a
 * "where is item X" overview can be built on. It is cheap: one small item-to-count map per container.
 *
 * <p>Marked <em>positions</em> are persisted per world/dimension; the content cache is runtime-only,
 * refilled from the world (single-player) or when a container is opened (server). Positions are
 * always the canonical block (see {@link ContainerBlocks#canonical}).
 */
public class ContainerTracker {
	private static final ContainerTracker INSTANCE = new ContainerTracker();

	private final Set<BlockPos> marked = new LinkedHashSet<>();
	private final Map<BlockPos, Object2IntOpenHashMap<ItemType>> contents = new HashMap<>();

	private ContainerTracker() {
	}

	public static ContainerTracker getInstance() {
		return INSTANCE;
	}

	public boolean isMarked(BlockPos pos) {
		return this.marked.contains(pos.immutable());
	}

	public Set<BlockPos> getMarked() {
		return this.marked;
	}

	public boolean isEmpty() {
		return this.marked.isEmpty();
	}

	/** Toggles the marked state of a (canonical) container. @return true if now marked, false if unmarked. */
	public boolean toggle(BlockPos pos) {
		BlockPos immutable = pos.immutable();

		if (this.marked.remove(immutable)) {
			// Deliberately keep the cached contents, so re-marking counts again instantly.
			return false;
		}

		this.marked.add(immutable);
		return true;
	}

	public void clear() {
		this.marked.clear();
		this.contents.clear();
	}

	/** Caches a container's contents, whether or not it is currently marked. */
	public void setContents(BlockPos canonical, Object2IntOpenHashMap<ItemType> counts) {
		this.contents.put(canonical.immutable(), counts);
	}

	/** The cached contents of a container, or null if we have never looked inside it. */
	public Object2IntOpenHashMap<ItemType> getContents(BlockPos canonical) {
		return this.contents.get(canonical.immutable());
	}

	/** Sum of the contents of all marked containers whose contents we know. */
	public Object2IntOpenHashMap<ItemType> getTotalContents() {
		Object2IntOpenHashMap<ItemType> total = new Object2IntOpenHashMap<>();

		for (BlockPos pos : this.marked) {
			Object2IntOpenHashMap<ItemType> snapshot = this.contents.get(pos);

			if (snapshot != null) {
				for (Object2IntMap.Entry<ItemType> entry : snapshot.object2IntEntrySet()) {
					total.addTo(entry.getKey(), entry.getIntValue());
				}
			}
		}

		return total;
	}

	private static Path getStorageFile() {
		return FileUtils.getConfigDirectory()
				.resolve(Logisticmatica.MOD_ID)
				.resolve(StringUtils.getStorageFileName(false, "containers_", ".json", "default"));
	}

	public void save() {
		JsonArray array = new JsonArray();

		for (BlockPos pos : this.marked) {
			array.add(JsonUtils.blockPosToJson(pos));
		}

		JsonObject root = new JsonObject();
		root.add("marked", array);

		Path file = getStorageFile();
		FileUtils.createDirectoriesIfMissing(file.getParent());
		JsonUtils.writeJsonToFile(root, file);
	}

	public void load() {
		this.marked.clear();
		this.contents.clear();

		JsonElement element = JsonUtils.parseJsonFile(getStorageFile());

		if (element != null && element.isJsonObject()) {
			JsonObject root = element.getAsJsonObject();

			if (root.has("marked") && root.get("marked").isJsonArray()) {
				for (JsonElement entry : root.getAsJsonArray("marked")) {
					if (entry.isJsonArray()) {
						JsonArray coords = entry.getAsJsonArray();

						if (coords.size() == 3) {
							this.marked.add(new BlockPos(coords.get(0).getAsInt(),
									coords.get(1).getAsInt(), coords.get(2).getAsInt()));
						}
					}
				}
			}

			Logisticmatica.LOGGER.debug("[{}] Loaded {} tracked containers.", Logisticmatica.MOD_NAME, this.marked.size());
		}
	}
}
