package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.world.level.block.Block;

import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.share.ClientShareManager;

/**
 * Owns the material substitutions the player has defined and pushes them onto the schematic
 * containers as a non-destructive overlay (see {@link ISubstitutableContainer}). Applying a
 * substitution marks the schematic's placements for rebuild so the ghost render, verifier, Easy-Place
 * and material list all pick up the swapped block; the schematic file is never modified.
 *
 * <p>Substitutions are keyed by schematic (its file path, or its name for unsaved ones) and persisted
 * globally, then re-applied when a schematic loads or the world is (re)joined — so a swap survives
 * restarts without ever touching the {@code .litematic}.
 */
public class SubstitutionManager {
	private static final SubstitutionManager INSTANCE = new SubstitutionManager();

	/** schematic key -> (original block -> substitute block). */
	private final Map<String, Map<Block, Block>> substitutions = new HashMap<>();

	private SubstitutionManager() {
	}

	public static SubstitutionManager getInstance() {
		return INSTANCE;
	}

	private static String key(LitematicaSchematic schematic) {
		Path file = schematic.getFile();
		return file != null ? file.toString() : "name:" + schematic.getMetadata().getName();
	}

	/** The substitute for {@code from} in this schematic, or null if it is not substituted. */
	@Nullable
	public Block getSubstitute(LitematicaSchematic schematic, Block from) {
		Map<Block, Block> map = this.substitutions.get(key(schematic));
		return map != null ? map.get(from) : null;
	}

	/** True if this schematic has any substitutions. */
	public boolean hasAny(LitematicaSchematic schematic) {
		Map<Block, Block> map = this.substitutions.get(key(schematic));
		return map != null && !map.isEmpty();
	}

	/** A defensive snapshot used by the sharing protocol. */
	public Map<Block, Block> getAll(LitematicaSchematic schematic) {
		Map<Block, Block> map = this.substitutions.get(key(schematic));
		return map == null ? Map.of() : Map.copyOf(map);
	}

	/** Replaces the complete map, primarily when applying authoritative server state. */
	public void replaceAll(LitematicaSchematic schematic, Map<Block, Block> replacements) {
		Map<Block, Block> current = this.substitutions.get(key(schematic));
		if (replacements.equals(current != null ? current : Map.of())) return;
		if (replacements.isEmpty()) {
			this.substitutions.remove(key(schematic));
		} else {
			this.substitutions.put(key(schematic), new LinkedHashMap<>(replacements));
		}
		this.apply(schematic);
		this.save();
	}

	/** Removes every substitution for this schematic. */
	public void clearAll(LitematicaSchematic schematic) {
		if (this.substitutions.remove(key(schematic)) != null) {
			this.apply(schematic);
			this.save();
			ClientShareManager.getInstance().onLocalSubstitutionsChanged(schematic);
		}
	}

	/** Substitutes {@code from} with {@code to} (or removes the substitution when {@code to} is null or equal). */
	public void setSubstitute(LitematicaSchematic schematic, Block from, @Nullable Block to) {
		String key = key(schematic);
		Map<Block, Block> map = this.substitutions.computeIfAbsent(key, k -> new LinkedHashMap<>());

		if (to == null || to == from) {
			map.remove(from);
		} else {
			map.put(from, to);
		}

		if (map.isEmpty()) {
			this.substitutions.remove(key);
		}

		this.apply(schematic);
		this.save();
		ClientShareManager.getInstance().onLocalSubstitutionsChanged(schematic);
	}

	/** Pushes the current substitution map onto the schematic's containers and refreshes everything downstream. */
	public void apply(LitematicaSchematic schematic) {
		Map<Block, Block> map = this.substitutions.get(key(schematic));
		Map<Block, Block> snapshot = (map == null || map.isEmpty()) ? null : new HashMap<>(map);

		for (String region : schematic.getAreas().keySet()) {
			LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);

			if (container instanceof ISubstitutableContainer substitutable) {
				substitutable.logisticmatica$setSubstitutions(snapshot);
			}
		}

		// Rebuild the placed copies (render/verifier/Easy-Place) and rebuild the focused material list.
		DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);

		MaterialListBase materialList = DataManager.getMaterialList();
		if (materialList != null) {
			materialList.reCreateMaterialList();
		}
	}

	/** Re-applies stored substitutions to a freshly loaded schematic. */
	public void reapply(LitematicaSchematic schematic) {
		if (this.substitutions.containsKey(key(schematic))) {
			this.apply(schematic);
		}
	}

	/** Re-applies substitutions to every currently loaded schematic (e.g. after a world (re)join). */
	public void reapplyAll() {
		for (LitematicaSchematic schematic : SchematicHolder.getInstance().getAllSchematics()) {
			this.reapply(schematic);
		}
	}

	private static Path getStorageFile() {
		return FileUtils.getConfigDirectory().resolve(Logisticmatica.MOD_ID).resolve("substitutions.json");
	}

	public void save() {
		JsonObject root = new JsonObject();

		for (Map.Entry<String, Map<Block, Block>> schematic : this.substitutions.entrySet()) {
			JsonObject pairs = new JsonObject();

			for (Map.Entry<Block, Block> pair : schematic.getValue().entrySet()) {
				pairs.addProperty(Substitutions.idOf(pair.getKey()), Substitutions.idOf(pair.getValue()));
			}

			root.add(schematic.getKey(), pairs);
		}

		Path file = getStorageFile();
		FileUtils.createDirectoriesIfMissing(file.getParent());
		JsonUtils.writeJsonToFile(root, file);
	}

	public void load() {
		this.substitutions.clear();

		JsonElement element = JsonUtils.parseJsonFile(getStorageFile());

		if (element == null || !element.isJsonObject()) {
			return;
		}

		for (Map.Entry<String, JsonElement> schematic : element.getAsJsonObject().entrySet()) {
			if (!schematic.getValue().isJsonObject()) {
				continue;
			}

			Map<Block, Block> map = new LinkedHashMap<>();

			for (Map.Entry<String, JsonElement> pair : schematic.getValue().getAsJsonObject().entrySet()) {
				Block from = Substitutions.blockOf(pair.getKey());
				Block to = Substitutions.blockOf(pair.getValue().getAsString());

				if (from != null && to != null) {
					map.put(from, to);
				}
			}

			if (!map.isEmpty()) {
				this.substitutions.put(schematic.getKey(), map);
			}
		}

		Logisticmatica.LOGGER.debug("[{}] Loaded substitutions for {} schematic(s).",
				Logisticmatica.MOD_NAME, this.substitutions.size());
	}
}
