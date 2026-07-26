package com.skyraax.logisticmatica.client.gui;

import java.util.Locale;
import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.FocusState;

/**
 * A shared navigation tab row placed just under the title of each top-level screen, so the player can
 * jump straight between Materials / Focus / Containers / Substitutions / Settings (and back to the
 * hub) without stepping back each time. The tab for the current screen is disabled. Screens reserve
 * room for it with {@link #Y} + {@link #HEIGHT}; sub-flows (the block picker, a container's contents)
 * deliberately omit it so their Back flow stays simple.
 */
public final class NavBar {
	/** Top of the tab row; screens put their own controls below {@code Y + HEIGHT}. */
	public static final int Y = 22;
	public static final int HEIGHT = 18;

	public enum Tab {
		MENU, MATERIALS, FOCUS, CONTAINERS, SUBSTITUTIONS, SHARING, SETTINGS
	}

	private NavBar() {
	}

	/** Adds the tab row to {@code gui}, disabling the {@code current} tab. */
	public static void add(GuiBase gui, Tab current) {
		int x = 12;
		Screen hub = gui.getParent();

		for (Tab tab : Tab.values()) {
			String label = StringUtils.translate("logisticmatica.gui.tab." + tab.name().toLowerCase(Locale.ROOT));
			int width = gui.getStringWidth(label) + 10;

			ButtonGeneric button = new ButtonGeneric(x, Y, width, HEIGHT, label);
			button.setEnabled(tab != current);
			gui.addButton(button, new TabListener(tab, hub));

			x += width + 2;
		}
	}

	private static void openChild(GuiBase child, @Nullable Screen hub) {
		child.setParent(hub);
		GuiBase.openGui(child);
	}

	/** Opens the screen for a tab, preserving the hub as the parent so Back still returns there. */
	private record TabListener(Tab tab, @Nullable Screen hub) implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.tab) {
				case MENU -> GuiBase.openGui(this.hub instanceof GuiHub ? this.hub : new GuiHub());
				case MATERIALS -> {
					MaterialListBase materialList = DataManager.getMaterialList();
					openChild(materialList != null ? new GuiMaterialListView(materialList) : new GuiFocusPicker(), this.hub);
				}
				case FOCUS -> openChild(new GuiFocusPicker(), this.hub);
				case CONTAINERS -> openChild(new GuiContainerOverview(), this.hub);
				case SUBSTITUTIONS -> {
					LitematicaSchematic schematic = FocusState.getSchematic();

					if (schematic == null) {
						InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "logisticmatica.message.substitutions.no_focus");
						openChild(new GuiFocusPicker(), this.hub);
					} else {
						openChild(new GuiSubstitutions(schematic), this.hub);
					}
				}
				case SETTINGS -> openChild(new GuiConfigs(), this.hub);
				case SHARING -> openChild(new GuiSharing(), this.hub);
			}
		}
	}
}
