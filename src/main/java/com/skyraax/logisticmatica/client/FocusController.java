package com.skyraax.logisticmatica.client;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/** Applies one explicit Logisticmatica focus to Litematica's active material list. */
public final class FocusController {
	private FocusController() {
	}

	public static void clear() {
		FocusState.clear();
		DataManager.setMaterialList(null);
	}

	public static void focusPlacement(SchematicPlacement placement) {
		FocusState.setPlacement(placement);
		apply(placement.getMaterialList());
	}

	public static void focusSchematic(LitematicaSchematic schematic) {
		FocusState.setSchematic(schematic);
		apply(new MaterialListSchematic(schematic, false));
	}

	public static boolean isFocused(SchematicPlacement placement) {
		return FocusState.getPlacement() == placement;
	}

	private static void apply(MaterialListBase materialList) {
		materialList.reCreateMaterialList();
		DataManager.setMaterialList(materialList);
	}
}
