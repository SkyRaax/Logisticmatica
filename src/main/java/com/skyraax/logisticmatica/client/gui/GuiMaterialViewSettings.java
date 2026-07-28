package com.skyraax.logisticmatica.client.gui;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.config.Configs;

/** Shared material-list/HUD presentation settings with immediate persisted updates. */
public final class GuiMaterialViewSettings extends GuiBase {
	public GuiMaterialViewSettings() {
		this.useTitleHierarchy = false;
		this.title = StringUtils.translate("logisticmatica.gui.title.material_view_settings");
	}

	@Override
	public void initGui() {
		super.initGui();
		int y = 30;
		y = this.option(y, "sort", Configs.Hud.MATERIAL_SORT.getOptionListValue().getDisplayName(), Action.SORT);
		y = this.option(y, "order", StringUtils.translate(Configs.Hud.MATERIAL_SORT_DESCENDING.getBooleanValue()
				? "logisticmatica.gui.material.order.descending"
				: "logisticmatica.gui.material.order.ascending"), Action.ORDER);
		y = this.option(y, "amount", Configs.Hud.MATERIAL_AMOUNT.getOptionListValue().getDisplayName(), Action.AMOUNT);
		y = this.toggle(y, "hide_built", Configs.Hud.ONLY_MISSING.getBooleanValue(), Action.HIDE_BUILT);
		y = this.toggle(y, "hide_supplied", Configs.Hud.HIDE_COMPLETE.getBooleanValue(), Action.HIDE_SUPPLIED);
		y = this.toggle(y, "icons", Configs.Hud.SHOW_ITEM_ICONS.getBooleanValue(), Action.ICONS);
		y = this.toggle(y, "header", Configs.Hud.SHOW_HEADER.getBooleanValue(), Action.HEADER);
		y = this.toggle(y, "background", Configs.Hud.SHOW_BACKGROUND.getBooleanValue(), Action.BACKGROUND);
		this.toggle(y, "in_guis", Configs.Hud.RENDER_IN_GUIS.getBooleanValue(), Action.IN_GUIS);

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		this.addButton(new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back),
				new Listener(Action.BACK, this));
	}

	private int option(int y, String key, String value, Action action) {
		String label = StringUtils.translate("logisticmatica.gui.material.setting." + key, value);
		ButtonGeneric button = new ButtonGeneric(12, y, 250, 20, label);
		button.setHoverStrings("logisticmatica.gui.material.setting." + key + ".description");
		this.addButton(button, new Listener(action, this));
		return y + 24;
	}

	private int toggle(int y, String key, boolean enabled, Action action) {
		String state = StringUtils.translate(enabled
				? "logisticmatica.gui.notifications.enabled"
				: "logisticmatica.gui.notifications.disabled");
		return this.option(y, key, state, action);
	}

	private enum Action {
		SORT, ORDER, AMOUNT, HIDE_BUILT, HIDE_SUPPLIED, ICONS, HEADER, BACKGROUND, IN_GUIS, BACK
	}

	private record Listener(Action action, GuiMaterialViewSettings gui) implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.action) {
				case SORT -> cycle(Configs.Hud.MATERIAL_SORT, mouseButton == 0);
				case ORDER -> Configs.Hud.MATERIAL_SORT_DESCENDING.setBooleanValue(
						!Configs.Hud.MATERIAL_SORT_DESCENDING.getBooleanValue());
				case AMOUNT -> cycle(Configs.Hud.MATERIAL_AMOUNT, mouseButton == 0);
				case HIDE_BUILT -> Configs.Hud.ONLY_MISSING.setBooleanValue(
						!Configs.Hud.ONLY_MISSING.getBooleanValue());
				case HIDE_SUPPLIED -> Configs.Hud.HIDE_COMPLETE.setBooleanValue(
						!Configs.Hud.HIDE_COMPLETE.getBooleanValue());
				case ICONS -> Configs.Hud.SHOW_ITEM_ICONS.setBooleanValue(
						!Configs.Hud.SHOW_ITEM_ICONS.getBooleanValue());
				case HEADER -> Configs.Hud.SHOW_HEADER.setBooleanValue(
						!Configs.Hud.SHOW_HEADER.getBooleanValue());
				case BACKGROUND -> Configs.Hud.SHOW_BACKGROUND.setBooleanValue(
						!Configs.Hud.SHOW_BACKGROUND.getBooleanValue());
				case IN_GUIS -> Configs.Hud.RENDER_IN_GUIS.setBooleanValue(
						!Configs.Hud.RENDER_IN_GUIS.getBooleanValue());
				case BACK -> { GuiBase.openGui(this.gui.getParent()); return; }
			}
			Configs.saveToFile();
			this.gui.initGui();
		}

		private static void cycle(fi.dy.masa.malilib.config.options.ConfigOptionList option, boolean forward) {
			IConfigOptionListEntry current = option.getOptionListValue();
			option.setOptionListValue(current.cycle(forward));
		}
	}
}
