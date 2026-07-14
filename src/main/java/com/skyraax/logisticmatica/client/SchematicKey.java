package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/**
 * A stable string key identifying a schematic across sessions — its file path, or its name for
 * unsaved ones. Used to bind tracked containers (and substitutions) to a specific schematic.
 */
public final class SchematicKey {
	private SchematicKey() {
	}

	public static String of(LitematicaSchematic schematic) {
		Path file = schematic.getFile();
		return file != null ? file.toString() : "name:" + schematic.getMetadata().getName();
	}

	/** All currently loaded schematics, keyed for lookup — used to show only loaded schematics' containers. */
	public static Map<String, LitematicaSchematic> loadedByKey() {
		Map<String, LitematicaSchematic> map = new LinkedHashMap<>();

		for (LitematicaSchematic schematic : SchematicHolder.getInstance().getAllSchematics()) {
			map.put(of(schematic), schematic);
		}

		return map;
	}
}
