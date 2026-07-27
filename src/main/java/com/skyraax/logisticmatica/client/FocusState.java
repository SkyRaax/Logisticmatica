package com.skyraax.logisticmatica.client;

import java.util.UUID;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/**
 * Remembers the local schematic or authoritative server project Logisticmatica currently follows.
 * The project id is also the canonical scope for shared container snapshots.
 */
public final class FocusState {
	@Nullable private static LitematicaSchematic schematic;
	@Nullable private static SchematicPlacement placement;
	@Nullable private static String schematicKey;
	@Nullable private static UUID projectId;

	private FocusState() {
	}

	public static void setSchematic(@Nullable LitematicaSchematic schematic) {
		FocusState.schematic = schematic;
		FocusState.placement = null;
		FocusState.schematicKey = schematic != null ? SchematicKey.of(schematic) : null;
		FocusState.projectId = null;
	}

	public static void setPlacement(@Nullable SchematicPlacement placement) {
		FocusState.placement = placement;
		FocusState.schematic = placement != null ? placement.getSchematic() : null;
		FocusState.schematicKey = FocusState.schematic != null ? SchematicKey.of(FocusState.schematic) : null;
		FocusState.projectId = null;
	}

	public static void setSharedPlacement(SchematicPlacement placement, UUID projectId) {
		FocusState.placement = placement;
		FocusState.schematic = placement.getSchematic();
		FocusState.schematicKey = ContainerTracker.projectKey(projectId);
		FocusState.projectId = projectId;
	}

	public static void clear() {
		FocusState.schematic = null;
		FocusState.placement = null;
		FocusState.schematicKey = null;
		FocusState.projectId = null;
	}

	@Nullable
	public static LitematicaSchematic getSchematic() {
		return schematic;
	}

	public static boolean isFocusedSchematicKey(@Nullable String schematicKey) {
		return schematicKey != null && schematicKey.equals(FocusState.schematicKey);
	}

	@Nullable
	public static UUID getProjectId() {
		return projectId;
	}

	@Nullable
	public static SchematicPlacement getPlacement() {
		return placement;
	}
}
