package com.skyraax.logisticmatica.client.gui;

import java.util.List;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/**
 * Lets the player choose which schematic placement Logisticmatica focuses on — its material list is
 * what the HUD and the list screen show — or clear the focus entirely.
 *
 * <p>Litematica sets the "active" material list implicitly whenever a material list is opened, which
 * leaves the player with no way to switch or clear it. This screen makes that choice explicit, and
 * is the client-side seed of the "focused build" that a shared, server-side project will grow from.
 */
public class GuiFocusPicker extends GuiBase {
	public GuiFocusPicker() {
		this.title = StringUtils.translate("logisticmatica.gui.title.focus");
	}

	@Override
	public void initGui() {
		super.initGui();

		final int x = 12;
		final int buttonWidth = Math.min(320, this.getScreenWidth() - 24);
		int y = 32;

		MaterialListBase active = DataManager.getMaterialList();
		String activeName = active != null
				? active.getName()
				: StringUtils.translate("logisticmatica.gui.label.focus.none");
		String activeLabel = StringUtils.translate("logisticmatica.gui.label.focus.active", activeName);

		this.addLabel(x, y, this.getStringWidth(activeLabel), 12, 0xFFFFFFFF, activeLabel);
		y += 20;

		ButtonGeneric clear = new ButtonGeneric(x, y, buttonWidth, 20,
				StringUtils.translate("logisticmatica.gui.button.focus.clear"));
		clear.setEnabled(active != null);
		this.addButton(clear, new ButtonListener(null, this));
		y += 26;

		List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();

		if (placements.isEmpty()) {
			String empty = StringUtils.translate("logisticmatica.gui.label.focus.no_placements");
			this.addLabel(x, y, this.getStringWidth(empty), 12, 0xFFAAAAAA, empty);
			return;
		}

		for (SchematicPlacement placement : placements) {
			this.addButton(new ButtonGeneric(x, y, buttonWidth, 20, placement.getName()),
					new ButtonListener(placement, this));
			y += 22;
		}
	}

	private void focus(@Nullable SchematicPlacement placement) {
		if (placement == null) {
			DataManager.setMaterialList(null);
			return;
		}

		// Lazily creates the placement's material list and kicks off its (async) block count.
		MaterialListBase materialList = placement.getMaterialList();
		materialList.reCreateMaterialList();
		DataManager.setMaterialList(materialList);
	}

	private record ButtonListener(@Nullable SchematicPlacement placement, GuiFocusPicker parent)
			implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			this.parent.focus(this.placement);
			this.parent.initGui(); // refresh the label and the enabled state
		}
	}
}
