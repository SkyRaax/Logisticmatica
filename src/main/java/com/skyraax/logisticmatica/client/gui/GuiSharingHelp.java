package com.skyraax.logisticmatica.client.gui;

import java.util.Locale;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.share.SharePermission;

/** In-game explanation of file actions, public modes, role presets and every capability. */
public class GuiSharingHelp extends GuiBase {
	public GuiSharingHelp() {
		this.useTitleHierarchy = false;
		this.title = StringUtils.translate("logisticmatica.gui.title.sharing_help");
	}

	@Override
	public void initGui() {
		super.initGui();
		int left = 12;
		int right = Math.max(430, this.getScreenWidth() / 2 + 8);
		int y = 30;
		y = this.section(left, y, "logisticmatica.gui.share.help.file_actions");
		y = this.explanation(left, y, "logisticmatica.gui.share.download",
				"logisticmatica.gui.share.download.description");
		y = this.explanation(left, y, "logisticmatica.gui.share.replace",
				"logisticmatica.gui.share.replace.description");
		y += 4;
		y = this.section(left, y, "logisticmatica.gui.share.help.roles");
		for (String role : new String[] { "viewer", "builder", "editor", "manager", "owner" }) {
			y = this.explanation(left, y, "logisticmatica.gui.share.role." + role,
					"logisticmatica.gui.share.role." + role + ".description");
		}

		int permissionY = 30;
		permissionY = this.section(right, permissionY, "logisticmatica.gui.share.help.permissions");
		for (SharePermission permission : SharePermission.values()) {
			String id = permission.name().toLowerCase(Locale.ROOT);
			permissionY = this.explanation(right, permissionY,
					"logisticmatica.gui.share.permission." + id,
					"logisticmatica.gui.share.permission." + id + ".description");
		}
		permissionY += 4;
		permissionY = this.section(right, permissionY, "logisticmatica.gui.share.help.public_access");
		for (String access : new String[] { "request_only", "public_viewer", "public_supplier", "public_editor" }) {
			permissionY = this.explanation(right, permissionY,
					"logisticmatica.gui.share.access." + access,
					"logisticmatica.gui.share.access." + access + ".description");
		}
		permissionY += 4;
		this.explanation(right, permissionY,
				"logisticmatica.gui.share.help.world_container_access",
				"logisticmatica.gui.share.help.world_container_access.description");

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		this.addButton(new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back),
				(button, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	private int section(int x, int y, String key) {
		String text = StringUtils.translate(key);
		this.addLabel(x, y, this.getStringWidth(text), 12, 0xFFFFAA00, text);
		return y + 16;
	}

	private int explanation(int x, int y, String titleKey, String descriptionKey) {
		String title = StringUtils.translate(titleKey);
		String description = StringUtils.translate(descriptionKey);
		this.addLabel(x, y, this.getStringWidth(title), 12, 0xFFFFFFFF, title);
		this.addLabel(x + 8, y + 12, this.getStringWidth(description), 12, 0xFFAAAAAA, description);
		return y + 28;
	}
}
