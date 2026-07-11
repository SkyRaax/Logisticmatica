package com.skyraax.logisticmatica.client;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/**
 * Remembers which schematic Logisticmatica currently follows. Litematica's material list does not
 * expose the schematic behind it, so we record it ourselves when the focus picker sets the focus.
 * Material substitution needs this: a substitution is edited from the material list but applies to
 * the whole schematic.
 */
public final class FocusState {
	@Nullable private static LitematicaSchematic schematic;

	private FocusState() {
	}

	public static void setSchematic(@Nullable LitematicaSchematic schematic) {
		FocusState.schematic = schematic;
	}

	@Nullable
	public static LitematicaSchematic getSchematic() {
		return schematic;
	}
}
