package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import com.skyraax.logisticmatica.Logisticmatica;

/**
 * Holds the set of containers the player has marked as "tracked" for the current world/dimension,
 * and persists it to a per-world JSON file. Marked container contents get folded into the material
 * list's "available" counts, and the positions are highlighted in the world.
 *
 * <p>Client-side only; the set is loaded on world join and saved whenever it changes.
 */
public class ContainerTracker {
	private static final ContainerTracker INSTANCE = new ContainerTracker();

	private final Set<BlockPos> marked = new LinkedHashSet<>();

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

	/** Toggles the marked state of a container. @return true if it is now marked, false if unmarked. */
	public boolean toggle(BlockPos pos) {
		BlockPos immutable = pos.immutable();

		if (this.marked.remove(immutable)) {
			return false;
		}

		this.marked.add(immutable);
		return true;
	}

	public void clear() {
		this.marked.clear();
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
