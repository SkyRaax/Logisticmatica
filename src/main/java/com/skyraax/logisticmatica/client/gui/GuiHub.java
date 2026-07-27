package com.skyraax.logisticmatica.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.FocusState;

/**
 * The central hub screen for Logisticmatica — one place that reaches every feature (material list,
 * focus, container overview, substitutions, settings) instead of a scatter of hotkeys. Every screen
 * opened from here is given this hub as its parent, so "Back" always returns here and the navigation
 * stays consistent. The Sharing entry opens the server-authoritative project directory when the
 * server component is available and otherwise explains that local features remain usable.
 */
public class GuiHub extends GuiBase {
	private static final int BUTTON_WIDTH = 220;

	public GuiHub() {
		this.title = StringUtils.translate("logisticmatica.gui.title.menu");
	}

	@Override
	public void initGui() {
		super.initGui();

		int x = 12;
		int y = 32;

		this.addMenuButton(x, y, "material_list", true, this::openMaterialList);
		y += 24;
		this.addMenuButton(x, y, "projects", true, () -> this.open(new GuiProjects()));
		y += 24;
		this.addMenuButton(x, y, "containers", true, () -> this.open(new GuiContainerOverview()));
		y += 24;
		this.addMenuButton(x, y, "substitutions", true, this::openSubstitutions);
		y += 24;
		this.addMenuButton(x, y, "settings", true, () -> this.open(new GuiConfigs()));

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		ButtonGeneric backButton = new ButtonGeneric(x, this.getScreenHeight() - 26,
				this.getStringWidth(back) + 20, 20, back);
		this.addButton(backButton, new Listener(() -> GuiBase.openGui(this.getParent())));
	}

	private void addMenuButton(int x, int y, String id, boolean enabled, Runnable action) {
		ButtonGeneric button = new ButtonGeneric(x, y, BUTTON_WIDTH, 20,
				StringUtils.translate("logisticmatica.gui.button.menu." + id));
		button.setEnabled(enabled);
		button.setHoverStrings("logisticmatica.gui.button.menu." + id + ".hover");
		this.addButton(button, new Listener(action));
	}

	/** Opens a child screen with this hub as its parent, so its "Back" returns here. */
	private void open(GuiBase gui) {
		gui.setParent(this);
		GuiBase.openGui(gui);
	}

	private void openMaterialList() {
		MaterialListBase materialList = DataManager.getMaterialList();
		this.open(materialList != null ? new GuiMaterialListView(materialList) : new GuiProjects());
	}

	private void openSubstitutions() {
		LitematicaSchematic schematic = FocusState.getSchematic();

		if (schematic == null) {
			InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "logisticmatica.message.substitutions.no_focus");
			this.open(new GuiProjects());
			return;
		}

		this.open(new GuiSubstitutions(schematic));
	}

	private record Listener(Runnable action) implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			this.action.run();
		}
	}
}
