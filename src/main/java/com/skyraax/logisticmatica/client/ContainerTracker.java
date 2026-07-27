package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.Logisticmatica;

/**
 * Tracks which containers are marked, <em>bound to a specific schematic</em>, and caches their
 * contents.
 *
 * <p>Marking is per-schematic: a container belongs to one schematic (keyed by {@link SchematicKey}),
 * so the world can colour-code containers per schematic and only show those of the focused
 * schematic. Both bindings <em>and</em> contents are persisted per world (grouped by schematic),
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
	/** Authoritative bindings injected from the sharing server; never persisted in the client file. */
	private final Map<UUID, LinkedHashSet<BlockPos>> serverBindings = new LinkedHashMap<>();
	/** Reverse lookup for removing one project's live snapshot without disturbing another. */
	private final Map<BlockPos, UUID> serverProjectByPos = new HashMap<>();
	/** Local bindings temporarily hidden by an active server project. */
	private final Map<UUID, Map<BlockPos, DisplacedLocalBinding>> displacedLocalBindings = new HashMap<>();
	private record DisplacedLocalBinding(String schematicKey,
			@Nullable Object2IntOpenHashMap<ItemType> contents) {}


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

	/** Defensive snapshot of the containers currently owned by one local schematic. */
	public Set<BlockPos> markedFor(String schematicKey) {
		Set<BlockPos> positions = this.markedBySchematic.get(schematicKey);
		return positions != null ? Set.copyOf(positions) : Set.of();
	}

	/** Migrates bindings written with an older relative/unnormalized path to the current key. */
	public void reconcileSchematic(LitematicaSchematic schematic) {
		if (this.reconcileSchematicKey(schematic)) this.save();
	}

	private boolean reconcileSchematicKey(LitematicaSchematic schematic) {
		String current = SchematicKey.of(schematic);
		LinkedHashSet<BlockPos> migrated = new LinkedHashSet<>();
		boolean changed = false;

		for (String stored : Set.copyOf(this.markedBySchematic.keySet())) {
			if (stored.equals(current) || !SchematicKey.refersTo(stored, schematic)) continue;
			LinkedHashSet<BlockPos> positions = this.markedBySchematic.remove(stored);
			if (positions == null) continue;
			migrated.addAll(positions);
			for (BlockPos pos : positions) this.keyByPos.replace(pos, stored, current);
			changed = true;
		}

		if (!migrated.isEmpty()) {
			this.markedBySchematic.computeIfAbsent(current, ignored -> new LinkedHashSet<>()).addAll(migrated);
		}
		return changed;
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
		this.serverBindings.clear();
		this.serverProjectByPos.clear();
		this.displacedLocalBindings.clear();
	}

	/** Removes the previous server snapshot before applying a fresh one. */
	public void clearServerBindings() {
		for (UUID projectId : Set.copyOf(this.serverBindings.keySet())) {
			this.clearServerBindings(projectId);
		}
	}

	public void clearServerBindings(UUID projectId) {
		Set<BlockPos> positions = this.serverBindings.remove(projectId);
		if (positions == null) return;
		String projectKey = projectKey(projectId);
		for (BlockPos pos : positions) {
			if (!projectId.equals(this.serverProjectByPos.remove(pos))) continue;
			this.keyByPos.remove(pos, projectKey);
			LinkedHashSet<BlockPos> marked = this.markedBySchematic.get(projectKey);
			if (marked != null) {
				marked.remove(pos);
				if (marked.isEmpty()) this.markedBySchematic.remove(projectKey);
			}
			this.contents.remove(pos);
			this.restoreDisplaced(projectId, pos);
		}
	}

	/** Moves a removed owner's authoritative container bindings back into the local project. */
	public void transferServerBindingsToLocal(UUID projectId, String schematicKey) {
		Set<BlockPos> positions = this.serverBindings.remove(projectId);
		if (positions == null) return;
		String projectKey = projectKey(projectId);
		LinkedHashSet<BlockPos> projectPositions = this.markedBySchematic.get(projectKey);
		Map<BlockPos, DisplacedLocalBinding> displaced = this.displacedLocalBindings.remove(projectId);
		LinkedHashSet<BlockPos> local = this.markedBySchematic.computeIfAbsent(
				schematicKey, ignored -> new LinkedHashSet<>());
		for (BlockPos pos : positions) {
			if (!projectId.equals(this.serverProjectByPos.remove(pos))) continue;
			this.keyByPos.remove(pos, projectKey);
			if (projectPositions != null) projectPositions.remove(pos);
			if (displaced != null) displaced.remove(pos);
			local.add(pos);
			this.keyByPos.put(pos, schematicKey);
		}
		if (projectPositions != null && projectPositions.isEmpty()) this.markedBySchematic.remove(projectKey);
		this.save();
	}

	/**
	 * Adds one server-owned binding and its vanilla item-id snapshot.
	 *
	 * @return true when a matching local binding was promoted to server ownership
	 */
	public boolean setServerBinding(UUID projectId, BlockPos pos, Map<String, Integer> items) {
		return this.setServerBinding(projectId, pos, items, false);
	}

	public boolean setServerBinding(UUID projectId, BlockPos pos, Map<String, Integer> items, boolean promoteLocal) {
		BlockPos immutable = pos.immutable();
		String key = projectKey(projectId);
		String existing = this.keyByPos.get(immutable);
		boolean promoted = false;
		UUID existingProject = this.serverProjectByPos.get(immutable);
		if (existingProject != null && !existingProject.equals(projectId)) {
			this.removeServerBinding(existingProject, immutable);
			existing = this.keyByPos.get(immutable);
			existingProject = this.serverProjectByPos.get(immutable);
		}
		if (existing != null && existingProject == null) {
			LinkedHashSet<BlockPos> local = this.markedBySchematic.get(existing);
			Object2IntOpenHashMap<ItemType> current = this.contents.get(immutable);
			Object2IntOpenHashMap<ItemType> saved = null;
			if (current != null) {
				saved = new Object2IntOpenHashMap<>();
				saved.putAll(current);
			}
			this.displacedLocalBindings.computeIfAbsent(projectId, ignored -> new HashMap<>())
					.putIfAbsent(immutable, new DisplacedLocalBinding(existing, saved));
			if (local != null) {
				local.remove(immutable);
				if (local.isEmpty()) this.markedBySchematic.remove(existing);
			}
			promoted = promoteLocal;
		}
		this.markedBySchematic.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(immutable);
		this.keyByPos.put(immutable, key);
		this.serverBindings.computeIfAbsent(projectId, ignored -> new LinkedHashSet<>()).add(immutable);
		this.serverProjectByPos.put(immutable, projectId);

		Object2IntOpenHashMap<ItemType> snapshot = new Object2IntOpenHashMap<>();
		for (Map.Entry<String, Integer> item : items.entrySet()) {
			ItemType type = itemTypeOf(item.getKey());
			if (type != null && item.getValue() > 0) snapshot.addTo(type, item.getValue());
		}
		this.contents.put(immutable, snapshot);
		return promoted;
	}

	/** Moves every local binding from an old schematic identity to its replacement. */
	public void migrateLocalBindings(String fromKey, String toKey) {
		if (fromKey.equals(toKey)) return;
		LinkedHashSet<BlockPos> positions = this.markedBySchematic.remove(fromKey);
		if (positions == null) return;
		LinkedHashSet<BlockPos> target = this.markedBySchematic.computeIfAbsent(
				toKey, ignored -> new LinkedHashSet<>());
		for (BlockPos pos : positions) {
			if (fromKey.equals(this.keyByPos.get(pos))) {
				this.keyByPos.put(pos, toKey);
				target.add(pos);
			}
		}
		this.save();
	}

	/** Caches a container's contents, whether or not it is currently marked. */
	public void setContents(BlockPos canonical, Object2IntOpenHashMap<ItemType> counts) {
		this.contents.put(canonical.immutable(), counts);
		this.evictUntilWithinCap();
	}
	public void removeServerBinding(UUID projectId, BlockPos pos) {
		BlockPos immutable = pos.immutable();
		if (!projectId.equals(this.serverProjectByPos.get(immutable))) return;
		this.serverProjectByPos.remove(immutable);
		Set<BlockPos> projectPositions = this.serverBindings.get(projectId);
		if (projectPositions != null) {
			projectPositions.remove(immutable);
			if (projectPositions.isEmpty()) this.serverBindings.remove(projectId);
		}
		String key = projectKey(projectId);
		this.keyByPos.remove(immutable, key);
		LinkedHashSet<BlockPos> marked = this.markedBySchematic.get(key);
		if (marked != null) {
			marked.remove(immutable);
			if (marked.isEmpty()) this.markedBySchematic.remove(key);
		}
		this.contents.remove(immutable);
		this.restoreDisplaced(projectId, immutable);
	}

	private void restoreDisplaced(UUID projectId, BlockPos pos) {
		Map<BlockPos, DisplacedLocalBinding> byPosition = this.displacedLocalBindings.get(projectId);
		if (byPosition == null) return;
		DisplacedLocalBinding local = byPosition.remove(pos);
		if (byPosition.isEmpty()) this.displacedLocalBindings.remove(projectId);
		if (local == null || this.keyByPos.containsKey(pos)) return;
		this.markedBySchematic.computeIfAbsent(local.schematicKey(), ignored -> new LinkedHashSet<>()).add(pos);
		this.keyByPos.put(pos, local.schematicKey());
		if (local.contents() != null) {
			this.contents.put(pos, local.contents());
		} else {
			this.contents.remove(pos);
		}
	}


	public static String projectKey(UUID projectId) {
		return "project:" + projectId;
	}

	private boolean isServerBinding(BlockPos pos) {
		return this.serverProjectByPos.containsKey(pos);
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
				if (this.isServerBinding(pos)) continue;
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

			if (!array.isEmpty()) root.add(schematic.getKey(), array);
		}

		for (Map<BlockPos, DisplacedLocalBinding> displaced : this.displacedLocalBindings.values()) {
			for (Map.Entry<BlockPos, DisplacedLocalBinding> binding : displaced.entrySet()) {
				DisplacedLocalBinding local = binding.getValue();
				JsonArray array = root.has(local.schematicKey())
						? root.getAsJsonArray(local.schematicKey()) : new JsonArray();
				JsonObject entry = new JsonObject();
				entry.add("pos", JsonUtils.blockPosToJson(binding.getKey()));
				Object2IntOpenHashMap<ItemType> snapshot = local.contents();
				if (snapshot != null && !snapshot.isEmpty()) {
					JsonObject items = new JsonObject();
					for (Object2IntMap.Entry<ItemType> item : snapshot.object2IntEntrySet()) {
						items.addProperty(idOf(item.getKey()), item.getIntValue());
					}
					entry.add("items", items);
				}
				array.add(entry);
				root.add(local.schematicKey(), array);
			}
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

		boolean migrated = false;
		for (LitematicaSchematic schematic : SchematicKey.loadedByKey().values()) {
			migrated |= this.reconcileSchematicKey(schematic);
		}
		if (migrated) this.save();

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
