package com.skyraax.logisticmatica.client.gui;

import java.util.UUID;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.share.ProjectNotificationCategory;
import com.skyraax.logisticmatica.client.share.ProjectNotificationSettings;

/** Concrete per-project controls for shared activity messages. */
public final class GuiProjectNotifications extends GuiBase {
	private final UUID projectId;

	public GuiProjectNotifications(UUID projectId, String projectName) {
		this.projectId = projectId;
		this.title = StringUtils.translate("logisticmatica.gui.title.project_notifications", projectName);
		this.useTitleHierarchy = false;
	}

	@Override
	public void initGui() {
		super.initGui();
		ProjectNotificationSettings settings = ProjectNotificationSettings.getInstance();
		int y = 30;
		String hint = StringUtils.translate("logisticmatica.gui.notifications.hint");
		this.addLabel(12, y, this.getStringWidth(hint), 12, 0xFFAAAAAA, hint);
		y += 20;
		for (ProjectNotificationCategory category : ProjectNotificationCategory.values()) {
			boolean enabled = settings.isEnabled(this.projectId, category);
			String state = StringUtils.translate(enabled
					? "logisticmatica.gui.notifications.enabled"
					: "logisticmatica.gui.notifications.disabled");
			String label = StringUtils.translate(category.translationKey()) + ": " + state;
			ButtonGeneric button = new ButtonGeneric(12, y, -1, 20, label);
			button.setHoverStrings(category.translationKey() + ".description");
			this.addButton(button, new ToggleListener(this, category));
			y += 24;
		}
		String back = StringUtils.translate("logisticmatica.gui.button.back");
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back);
		this.addButton(backButton, new BackListener(this));
	}

	private record ToggleListener(GuiProjectNotifications gui, ProjectNotificationCategory category)
			implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			ProjectNotificationSettings.getInstance().toggle(this.gui.projectId, this.category);
			this.gui.initGui();
		}
	}

	private record BackListener(GuiProjectNotifications gui) implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			GuiBase.openGui(this.gui.getParent());
		}
	}
}