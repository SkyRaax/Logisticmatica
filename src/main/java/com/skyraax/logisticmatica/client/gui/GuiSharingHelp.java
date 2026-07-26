package com.skyraax.logisticmatica.client.gui;

import java.util.ArrayList;
import java.util.List;
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
		int leftWidth = Math.max(180, right - left - 24);
		int rightWidth = Math.max(180, this.getScreenWidth() - right - 12);
		int y = 30;
		y = this.section(left, y, "logisticmatica.gui.share.help.file_actions");
		y = this.explanation(left, y, leftWidth, "logisticmatica.gui.share.download_focus",
				"logisticmatica.gui.share.download_focus.description");
		y = this.explanation(left, y, leftWidth, "logisticmatica.gui.share.focus",
				"logisticmatica.gui.share.focus.description");
		y = this.explanation(left, y, leftWidth, "logisticmatica.gui.share.download",
				"logisticmatica.gui.share.download.description");
		y = this.explanation(left, y, leftWidth, "logisticmatica.gui.share.replace",
				"logisticmatica.gui.share.replace.description");
		y += 4;
		y = this.section(left, y, "logisticmatica.gui.share.help.roles");
		for (String role : new String[] { "viewer", "builder", "editor", "manager", "owner" }) {
			y = this.explanation(left, y, leftWidth, "logisticmatica.gui.share.role." + role,
					"logisticmatica.gui.share.role." + role + ".description");
		}

		int permissionY = 30;
		permissionY = this.section(right, permissionY, "logisticmatica.gui.share.help.permissions");
		for (SharePermission permission : SharePermission.values()) {
			String id = permission.name().toLowerCase(Locale.ROOT);
			permissionY = this.explanation(right, permissionY, rightWidth,
					"logisticmatica.gui.share.permission." + id,
					"logisticmatica.gui.share.permission." + id + ".description");
		}
		permissionY += 4;
		permissionY = this.section(right, permissionY, "logisticmatica.gui.share.help.public_access");
		for (String access : new String[] { "request_only", "public_viewer", "public_supplier", "public_editor" }) {
			permissionY = this.explanation(right, permissionY, rightWidth,
					"logisticmatica.gui.share.access." + access,
					"logisticmatica.gui.share.access." + access + ".description");
		}
		permissionY += 4;
		this.explanation(right, permissionY, rightWidth,
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

	private int explanation(int x, int y, int width, String titleKey, String descriptionKey) {
		String title = StringUtils.translate(titleKey);
		String description = StringUtils.translate(descriptionKey);
		this.addLabel(x, y, this.getStringWidth(title), 12, 0xFFFFFFFF, title);
		int lineY = y + 12;
		for (String line : this.wrap(description, Math.max(80, width - 8))) {
			this.addLabel(x + 8, lineY, this.getStringWidth(line), 12, 0xFFAAAAAA, line);
			lineY += 11;
		}
		return lineY + 5;
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && this.getStringWidth(candidate) > maxWidth) {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			} else {
				if (!line.isEmpty()) line.append(' ');
				line.append(word);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines.isEmpty() ? List.of("") : lines;
	}
}
