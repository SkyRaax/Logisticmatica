package com.skyraax.logisticmatica.client;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/**
 * Remembers which schematic Logisticmatica currently follows. Litematica's material list does not
 * expose the schematic behind it, so we record it ourselves when the focus picker sets the focus.
 * Material substitution needs this: a substitution is edited from the material list but applies to
 * the whole schematic.
 */
public final class FocusState {
	@Nullable private static LitematicaSchematic schematic;
	@Nullable private static SchematicPlacement placement;

	private FocusState() {
	}

	public static void setSchematic(@Nullable LitematicaSchematic schematic) {
		FocusState.schematic = schematic;
		FocusState.placement = null;
	}

	public static void setPlacement(@Nullable SchematicPlacement placement) {
		FocusState.placement = placement;
		FocusState.schematic = placement != null ? placement.getSchematic() : null;
	}

	public static void clear() {
		FocusState.schematic = null;
		FocusState.placement = null;
	}

	@Nullable
	public static LitematicaSchematic getSchematic() {
		return schematic;
	}

	@Nullable
	public static SchematicPlacement getPlacement() {
		return placement;
	}
}
