package com.skyraax.logisticmatica.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharedProjectView;

/**
 * A stable string key identifying a schematic across sessions — its file path, or its name for
 * unsaved ones. Used to bind tracked containers (and substitutions) to a specific schematic.
 */
public final class SchematicKey {
	private SchematicKey() {
	}

	public static String of(LitematicaSchematic schematic) {
		Path file = schematic.getFile();
		return file != null ? of(file) : "name:" + schematic.getMetadata().getName();
	}

	/** Stable key for a schematic file that may not currently be loaded. */
	public static String of(Path file) {
		return normalize(file);
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

	/** Human-readable schematic or server-project name for container overview rows and filters. */
	public static String displayName(String key) {
		UUID projectId = projectId(key);
		if (projectId != null) {
			SharedProjectView project = ClientShareManager.getInstance().project(projectId);
			if (project != null) return project.name();
		}

		LitematicaSchematic loaded = null;
		for (LitematicaSchematic schematic : SchematicHolder.getInstance().getAllSchematics()) {
			if (of(schematic).equals(key)) { loaded = schematic; break; }
		}
		if (loaded != null) return loaded.getMetadata().getName();
		if (key.startsWith("name:")) return key.substring("name:".length());
		try {
			Path file = Path.of(key).getFileName();
			return file != null ? file.toString() : key;
		} catch (RuntimeException ignored) {
			return key;
		}
	}

	/** Extracts the authoritative project UUID from an internal shared-container key. */
	@Nullable
	public static UUID projectId(@Nullable String key) {
		if (key == null || !key.startsWith("project:")) return null;
		try {
			return UUID.fromString(key.substring("project:".length()));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
