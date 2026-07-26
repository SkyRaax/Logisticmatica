package com.skyraax.logisticmatica.client;

import java.nio.file.Files;
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
		return file != null ? normalize(file) : "name:" + schematic.getMetadata().getName();
	}

	/**
	 * Returns whether a persisted key points at this schematic. Older Logisticmatica versions stored
	 * Litematica's path verbatim, which could switch between relative and absolute forms across a
	 * restart. Existing bindings are migrated to {@link #of} as soon as the schematic is loaded.
	 */
	public static boolean refersTo(String storedKey, LitematicaSchematic schematic) {
		String current = of(schematic);
		if (storedKey.equals(current)) return true;
		if (storedKey.startsWith("name:") || schematic.getFile() == null) return false;

		try {
			Path stored = Path.of(storedKey);
			Path actual = schematic.getFile();
			if (normalize(stored).equals(normalize(actual))) return true;
			return Files.exists(stored) && Files.exists(actual) && Files.isSameFile(stored, actual);
		} catch (RuntimeException | java.io.IOException ignored) {
			return false;
		}
	}

	private static String normalize(Path file) {
		try {
			return file.toAbsolutePath().normalize().toString();
		} catch (RuntimeException ignored) {
			return file.normalize().toString();
		}
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
