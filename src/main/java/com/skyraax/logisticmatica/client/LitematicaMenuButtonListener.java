package com.skyraax.logisticmatica.client;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;

import com.skyraax.logisticmatica.client.gui.GuiHub;

/**
 * Opens the Logisticmatica hub from the button injected into Litematica's main menu, keeping
 * Litematica's menu as the parent so "Back" returns there. Lives outside the mixin package: a helper
 * referenced by a mixin must not be loaded from within a mixin-owned package.
 */
public record LitematicaMenuButtonListener(@Nullable Screen parent) implements IButtonActionListener {
	@Override
	public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
		GuiHub hub = new GuiHub();
		hub.setParent(this.parent);
		GuiBase.openGui(hub);
	}
}
