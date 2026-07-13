package com.skyraax.logisticmatica.mixin.litematica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.Screen;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu.ButtonType;
import fi.dy.masa.litematica.selection.SelectionMode;

import com.skyraax.logisticmatica.client.LitematicaMenuButtonListener;

/**
 * Adds a "Logisticmatica" button to Litematica's main menu, in the gap the layout leaves in the
 * right column below "Configuration" — the same slot other add-ons (e.g. Syncmatica) fill. It opens
 * the mod's hub screen.
 *
 * <p>{@code remap = false}: the target is Litematica's own {@code initGui}. Button placement mirrors
 * Litematica's own right-column x by recomputing its button width from the public menu enums, so it
 * lines up with the native buttons regardless of language.
 */
@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu {
	@Inject(method = "initGui", at = @At("RETURN"), remap = false)
	private void logisticmatica$addHubButton(CallbackInfo ci) {
		GuiBase gui = (GuiBase) (Object) this;

		int buttonWidth = 0;
		for (ButtonType type : ButtonType.values()) {
			buttonWidth = Math.max(buttonWidth, gui.getStringWidth(type.getDisplayName()) + 30);
		}
		for (SelectionMode mode : SelectionMode.values()) {
			String label = StringUtils.translate("litematica.gui.button.area_selection_mode", mode.getDisplayName());
			buttonWidth = Math.max(buttonWidth, gui.getStringWidth(label) + 10);
		}

		// Right column (x), in the gap Litematica leaves below the "Configuration" button (y).
		int x = 12 + buttonWidth + 20;
		int y = 52;

		ButtonGeneric button = new ButtonGeneric(x, y, buttonWidth, 20,
				StringUtils.translate("logisticmatica.gui.button.litematica_menu"));
		gui.addButton(button, new LitematicaMenuButtonListener((Screen) (Object) this));
	}
}
