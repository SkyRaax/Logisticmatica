package com.skyraax.logisticmatica.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.skyraax.logisticmatica.client.FocusState;

/**
 * Lets the player choose which schematic Logisticmatica focuses on — its material list is what the
 * HUD and the list screen show — or clear the focus entirely.
 *
 * <p>Litematica sets the "active" material list implicitly, only when the player opens one from a
 * schematic's own menu, which leaves no way to switch or clear it afterwards. This screen makes that
 * choice explicit and offers both sources:
 *
 * <ul>
 *   <li><b>Placements</b> — a placement's material list compares the schematic against the blocks
 *       actually in the world, so it shows what is still <em>missing</em>. This is what you want
 *       while building.</li>
 *   <li><b>Loaded schematics</b> — a bare schematic only knows its <em>total</em> requirements.
 *       Useful for gathering materials before the schematic is placed anywhere.</li>
 * </ul>
 *
 * <p>This is also the client-side seed of the "focused build" that a shared, server-side project
 * will grow from.
 */
public class GuiFocusPicker extends GuiBase {
	private static final int ENTRY_HEIGHT = 22;
	private static final int SECTION_COLOR = 0xFFFFAA00;

	public GuiFocusPicker() {
		this.title = StringUtils.translate("logisticmatica.gui.title.focus");
	}

	@Override
	public void initGui() {
		super.initGui();

		NavBar.add(this, NavBar.Tab.FOCUS);

		final int x = 12;
		final int buttonWidth = Math.min(320, this.getScreenWidth() - 24);
		int y = 46;

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
		this.addButton(clear, new ButtonListener(null, null, this));
		y += 26;

		List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
		Collection<LitematicaSchematic> schematics = SchematicHolder.getInstance().getAllSchematics();

		if (placements.isEmpty() && schematics.isEmpty()) {
			String empty = StringUtils.translate("logisticmatica.gui.label.focus.nothing_loaded");
			this.addLabel(x, y, this.getStringWidth(empty), 12, 0xFFAAAAAA, empty);
			return;
		}

		if (!placements.isEmpty()) {
			y = this.addSectionLabel(x, y, "logisticmatica.gui.label.focus.placements");

			for (SchematicPlacement placement : placements) {
				if (this.isOutOfRoom(y)) {
					return;
				}

				this.addFocusButton(x, y, buttonWidth, placement.getName(), placement.getSchematic(),
						placement::getMaterialList);
				y += ENTRY_HEIGHT;
			}

			y += 6;
		}

		if (!schematics.isEmpty()) {
			y = this.addSectionLabel(x, y, "logisticmatica.gui.label.focus.schematics");

			for (LitematicaSchematic schematic : schematics) {
				if (this.isOutOfRoom(y)) {
					return;
				}

				this.addFocusButton(x, y, buttonWidth, schematic.getMetadata().getName(), schematic,
						() -> new MaterialListSchematic(schematic, false));
				y += ENTRY_HEIGHT;
			}
		}
	}

	private void addFocusButton(int x, int y, int width, String name, LitematicaSchematic schematic,
			Supplier<MaterialListBase> factory) {
		this.addButton(new ButtonGeneric(x, y, width, 20, name), new ButtonListener(schematic, factory, this));
	}

	private int addSectionLabel(int x, int y, String translationKey) {
		String text = StringUtils.translate(translationKey);
		this.addLabel(x, y, this.getStringWidth(text), 12, SECTION_COLOR, text);

		return y + 14;
	}

	private boolean isOutOfRoom(int y) {
		return y + ENTRY_HEIGHT > this.getScreenHeight() - 8;
	}

	/** A null factory clears the focus; otherwise the created list becomes the focused one. The
	 * schematic behind it is remembered in {@link FocusState} so substitution can find it later. */
	private void focus(@Nullable LitematicaSchematic schematic, @Nullable Supplier<MaterialListBase> factory) {
		FocusState.setSchematic(schematic);

		if (factory == null) {
			DataManager.setMaterialList(null);
			return;
		}

		MaterialListBase materialList = factory.get();
		materialList.reCreateMaterialList(); // kicks off the (async) block count
		DataManager.setMaterialList(materialList);
	}

	private record ButtonListener(@Nullable LitematicaSchematic schematic,
			@Nullable Supplier<MaterialListBase> factory, GuiFocusPicker parent)
			implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			this.parent.focus(this.schematic, this.factory);
			this.parent.initGui(); // refresh the label and the enabled state
		}
	}
}
