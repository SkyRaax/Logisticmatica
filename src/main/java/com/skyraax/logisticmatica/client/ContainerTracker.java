package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.ItemType;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import com.skyraax.logisticmatica.Logisticmatica;

/**
 * Tracks which containers are marked, <em>bound to a specific schematic</em>, and caches their
 * contents.
 *
 * <p>Marking is per-schematic: a container belongs to one schematic (keyed by {@link SchematicKey}),
 * so the world can colour-code containers per schematic and only show those whose schematic is
 * loaded. Both the bindings <em>and</em> the contents are persisted per world (grouped by schematic),
 * so after a rejoin a container's contents are known without re-opening it — which matters on servers,
 * where a container can only be read while its screen is open.
 *
 * <p>The content map doubles as an "everything I ever looked into" cache so marking takes effect
 * immediately; it is an insertion-ordered LRU capped at {@link #MAX_CACHED_CONTAINERS}, from which
 * marked containers are never evicted. Positions are always the canonical block (see
 * {@link ContainerBlocks#canonical}).
 */
public class ContainerTracker {
	private static final ContainerTracker INSTANCE = new ContainerTracker();
	private static final int MAX_CACHED_CONTAINERS = 2000;

	/** schematic key -> the canonical positions bound to it. */
	private final Map<String, LinkedHashSet<BlockPos>> markedBySchematic = new LinkedHashMap<>();
	/** reverse lookup: position -> its schematic key. */
	private final Map<BlockPos, String> keyByPos = new HashMap<>();
	/** Insertion-ordered content cache; marked positions are never evicted. */
	private final Map<BlockPos, Object2IntOpenHashMap<ItemType>> contents = new LinkedHashMap<>();

	private ContainerTracker() {
	}

	public static ContainerTracker getInstance() {
		return INSTANCE;
	}

	public boolean isMarked(BlockPos pos) {
		return this.keyByPos.containsKey(pos.immutable());
	}

	public boolean isEmpty() {
		return this.keyByPos.isEmpty();
	}

	/** The schematic key a container is bound to, or null if it is not marked. */
	@Nullable
	public String schematicKeyOf(BlockPos pos) {
		return this.keyByPos.get(pos.immutable());
	}

	/** Every marked (canonical) position, across all schematics. */
	public Set<BlockPos> allMarked() {
		return this.keyByPos.keySet();
	}

	/** The bindings grouped by schematic key (for per-schematic rendering). */
	public Map<String, LinkedHashSet<BlockPos>> markedBySchematic() {
		return this.markedBySchematic;
	}

	/**
	 * Toggles the marked state of a container. If already marked (under any schematic) it is unmarked;
	 * otherwise it is bound to {@code schematicKey}. @return true if now marked.
	 */
	public boolean toggle(String schematicKey, BlockPos pos) {
		BlockPos immutable = pos.immutable();
		String existing = this.keyByPos.remove(immutable);

		if (existing != null) {
			LinkedHashSet<BlockPos> set = this.markedBySchematic.get(existing);
			if (set != null) {
				set.remove(immutable);
				if (set.isEmpty()) {
					this.markedBySchematic.remove(existing);
				}
			}
			return false;
		}

		this.markedBySchematic.computeIfAbsent(schematicKey, k -> new LinkedHashSet<>()).add(immutable);
		this.keyByPos.put(immutable, schematicKey);
		return true;
	}

	public void clear() {
		this.markedBySchematic.clear();
		this.keyByPos.clear();
		this.contents.clear();
	}

	/** Caches a container's contents, whether or not it is currently marked. */
	public void setContents(BlockPos canonical, Object2IntOpenHashMap<ItemType> counts) {
		this.contents.put(canonical.immutable(), counts);
		this.evictUntilWithinCap();
	}

	/** The cached contents of a container, or null if we have never looked inside it. */
	@Nullable
	public Object2IntOpenHashMap<ItemType> getContents(BlockPos canonical) {
		return this.contents.get(canonical.immutable());
	}

	/** Sum of the contents of one schematic's marked containers whose contents we know. */
	public Object2IntOpenHashMap<ItemType> totalContentsFor(String schematicKey) {
		Object2IntOpenHashMap<ItemType> total = new Object2IntOpenHashMap<>();
		LinkedHashSet<BlockPos> positions = this.markedBySchematic.get(schematicKey);

		if (positions != null) {
			for (BlockPos pos : positions) {
				Object2IntOpenHashMap<ItemType> snapshot = this.contents.get(pos);

				if (snapshot != null) {
					for (Object2IntMap.Entry<ItemType> entry : snapshot.object2IntEntrySet()) {
						total.addTo(entry.getKey(), entry.getIntValue());
					}
				}
			}
		}

		return total;
	}

	private void evictUntilWithinCap() {
		if (this.contents.size() <= MAX_CACHED_CONTAINERS) {
			return;
		}

		Iterator<BlockPos> iterator = this.contents.keySet().iterator();

		while (iterator.hasNext() && this.contents.size() > MAX_CACHED_CONTAINERS) {
			if (!this.keyByPos.containsKey(iterator.next())) {
				iterator.remove();
			}
		}
	}

	// --- Persistence (per world, grouped by schematic, contents included) ---

	private static Path getStorageFile() {
		return FileUtils.getConfigDirectory()
				.resolve(Logisticmatica.MOD_ID)
				.resolve(StringUtils.getStorageFileName(false, "containers_", ".json", "default"));
	}

	public void save() {
		JsonObject root = new JsonObject();

		for (Map.Entry<String, LinkedHashSet<BlockPos>> schematic : this.markedBySchematic.entrySet()) {
			JsonArray array = new JsonArray();

			for (BlockPos pos : schematic.getValue()) {
				JsonObject entry = new JsonObject();
				entry.add("pos", JsonUtils.blockPosToJson(pos));

				Object2IntOpenHashMap<ItemType> snapshot = this.contents.get(pos);
				if (snapshot != null && !snapshot.isEmpty()) {
					JsonObject items = new JsonObject();
					for (Object2IntMap.Entry<ItemType> item : snapshot.object2IntEntrySet()) {
						items.addProperty(idOf(item.getKey()), item.getIntValue());
					}
					entry.add("items", items);
				}

				array.add(entry);
			}

			root.add(schematic.getKey(), array);
		}

		Path file = getStorageFile();
		FileUtils.createDirectoriesIfMissing(file.getParent());
		JsonUtils.writeJsonToFile(root, file);
	}

	public void load() {
		this.clear();

		JsonElement element = JsonUtils.parseJsonFile(getStorageFile());
		if (element == null || !element.isJsonObject()) {
			return;
		}

		for (Map.Entry<String, JsonElement> schematic : element.getAsJsonObject().entrySet()) {
			if (!schematic.getValue().isJsonArray()) {
				continue;
			}

			String key = schematic.getKey();

			for (JsonElement raw : schematic.getValue().getAsJsonArray()) {
				if (!raw.isJsonObject()) {
					continue;
				}

				JsonObject entry = raw.getAsJsonObject();
				BlockPos pos = posFromJson(entry.get("pos"));
				if (pos == null) {
					continue;
				}

				this.markedBySchematic.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(pos);
				this.keyByPos.put(pos, key);

				if (entry.has("items") && entry.get("items").isJsonObject()) {
					Object2IntOpenHashMap<ItemType> snapshot = new Object2IntOpenHashMap<>();

					for (Map.Entry<String, JsonElement> item : entry.getAsJsonObject("items").entrySet()) {
						ItemType type = itemTypeOf(item.getKey());
						if (type != null) {
							snapshot.addTo(type, item.getValue().getAsInt());
						}
					}

					if (!snapshot.isEmpty()) {
						this.contents.put(pos, snapshot);
					}
				}
			}
		}

		Logisticmatica.LOGGER.debug("[{}] Loaded tracked containers for {} schematic(s).",
				Logisticmatica.MOD_NAME, this.markedBySchematic.size());
	}

	private static String idOf(ItemType type) {
		return BuiltInRegistries.ITEM.getKey(type.getStack().getItem()).toString();
	}

	@Nullable
	private static ItemType itemTypeOf(String id) {
		Identifier location = Identifier.tryParse(id);
		if (location == null) {
			return null;
		}

		Item item = BuiltInRegistries.ITEM.getValue(location);
		if (item == Items.AIR) {
			return null;
		}

		return new ItemType(new ItemStack(item), false);
	}

	@Nullable
	private static BlockPos posFromJson(@Nullable JsonElement element) {
		if (element != null && element.isJsonArray()) {
			JsonArray coords = element.getAsJsonArray();
			if (coords.size() == 3) {
				return new BlockPos(coords.get(0).getAsInt(), coords.get(1).getAsInt(), coords.get(2).getAsInt());
			}
		}

		return null;
	}
}
