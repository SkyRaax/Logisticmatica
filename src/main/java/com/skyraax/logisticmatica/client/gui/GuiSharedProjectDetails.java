package com.skyraax.logisticmatica.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;

import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.ShareAccess;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedMemberView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Project actions, invitation flow and per-member role administration. */
public class GuiSharedProjectDetails extends GuiBase implements SharingRefreshable {
	private static final int[] ROLES = {
			SharePermission.VIEWER, SharePermission.BUILDER,
			SharePermission.EDITOR, SharePermission.MANAGER
	};
	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private final UUID projectId;
	private int invitePermissions = SharePermission.BUILDER;
	private int requestPermissions = SharePermission.BUILDER;
	private boolean deleteArmed;

	public GuiSharedProjectDetails(UUID projectId) {
		this.projectId = projectId;
		this.useTitleHierarchy = false;
	}

	@Override
	public void initGui() {
		super.initGui();
		SharedProjectView project = this.sharing.project(this.projectId);
		if (project == null) {
			GuiBase.openGui(this.getParent());
			return;
		}
		this.title = StringUtils.translate("logisticmatica.gui.title.shared_project", project.name());
		UUID self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
		boolean owner = self != null && project.ownerId().equals(self);
		int x = 12;
		int y = 28;
		String ownerLabel = StringUtils.translate("logisticmatica.gui.share.owner", project.ownerName());
		this.addLabel(x, y, this.getStringWidth(ownerLabel), 12, 0xFFFFFFFF, ownerLabel);
		y += 14;
		String location = StringUtils.translate("logisticmatica.gui.share.location", project.dimension(),
				project.x(), project.y(), project.z());
		this.addLabel(x, y, this.getStringWidth(location), 12, 0xFFAAAAAA, location);
		y += 18;

		if (project.pendingInvite()) {
			this.addButton(new ButtonGeneric(x, y, -1, 20,
					StringUtils.translate("logisticmatica.gui.share.accept")),
					new Listener(Action.ACCEPT, this, null));
			x += 88;
			this.addButton(new ButtonGeneric(x, y, -1, 20,
					StringUtils.translate("logisticmatica.gui.share.decline")),
					new Listener(Action.DECLINE, this, null));
			y += 28;
		} else if (project.can(SharePermission.VIEW)) {
			boolean loaded = this.sharing.isLoaded(project.id());
			boolean focused = this.sharing.isFocused(project.id());
			boolean placementEditable = project.can(SharePermission.MOVE);
			String loadStatus = StringUtils.translate(focused
					? "logisticmatica.gui.share.status.focused"
					: loaded ? "logisticmatica.gui.share.status.loaded"
					: "logisticmatica.gui.share.status.not_loaded");
			String editStatus = StringUtils.translate(placementEditable
					? "logisticmatica.gui.share.status.placement_editable"
					: "logisticmatica.gui.share.status.placement_read_only");
			String status = StringUtils.translate("logisticmatica.gui.share.local_status", loadStatus, editStatus);
			this.addLabel(12, y, this.getStringWidth(status), 12,
					focused ? 0xFF55FF55 : loaded ? 0xFF55FFFF : 0xFFFFAA00, status);
			y += 17;

			x = 12;
			if (loaded) {
				x = this.addAction(x, y, focused ? "focused" : "focus", Action.FOCUS, !focused);
				x = this.addAction(x, y, "open_placement", Action.OPEN_PLACEMENT, true);
			} else {
				x = this.addAction(x, y, "download_focus", Action.DOWNLOAD_FOCUS, true);
			}
			this.addAction(x, y, "help", Action.HELP, true);
			y += 24;

			x = this.addAction(12, y, "download", Action.DOWNLOAD, true);
			this.addAction(x, y, "replace", Action.REPLACE,
					project.can(SharePermission.UPDATE_SCHEMATIC));
			y += 24;

			int textWidth = Math.max(160, this.getScreenWidth() - 32);
			y = this.addWrappedLabel(16, y, textWidth,
					StringUtils.translate(placementEditable
							? "logisticmatica.gui.share.status.placement_editable.description"
							: "logisticmatica.gui.share.status.placement_read_only.description"));
			y = this.addWrappedLabel(16, y, textWidth,
					StringUtils.translate("logisticmatica.gui.share.download.description"));
			y = this.addWrappedLabel(16, y, textWidth,
					StringUtils.translate("logisticmatica.gui.share.replace.description"));
		} else {
			String unavailable = StringUtils.translate("logisticmatica.gui.share.no_project_access");
			this.addLabel(12, y, this.getStringWidth(unavailable), 12, 0xFFFFAA00, unavailable);
			y += 20;
		}

		if (!project.pendingInvite() && project.can(SharePermission.MANAGE_PERMISSIONS)) {
			String access = StringUtils.translate("logisticmatica.gui.share.server_access");
			this.addLabel(12, y, this.getStringWidth(access), 12, 0xFFFFAA00, access);
			y += 14;
			x = this.addAction(12, y, "access", Action.PUBLIC_ACCESS, true,
					accessName(project.publicAccess()));
			this.addAction(x, y, "help", Action.HELP, true);
			y += 22;
			String accessHelp = StringUtils.translate(accessDescriptionKey(project.publicAccess()));
			this.addLabel(16, y, this.getStringWidth(accessHelp), 12, 0xFFAAAAAA, accessHelp);
			y += 18;
		}

		if (!project.pendingInvite() && !owner && !project.member()) {
			String request = StringUtils.translate(project.accessRequested()
					? "logisticmatica.gui.share.request_pending"
					: "logisticmatica.gui.share.request_access_label");
			this.addLabel(12, y, this.getStringWidth(request), 12, 0xFFFFAA00, request);
			y += 14;
			if (project.accessRequested()) {
				this.addAction(12, y, "cancel_request", Action.CANCEL_REQUEST, true);
				y += 28;
			} else {
				String scale = StringUtils.translate("logisticmatica.gui.share.request_role.scale", ROLES.length);
				this.addLabel(16, y, this.getStringWidth(scale), 12, 0xFFAAAAAA, scale);
				y += 14;
				y = this.addRequestRoleSelector(y);

				String role = WidgetSharedProjectEntry.role(this.requestPermissions);
				String selected = StringUtils.translate("logisticmatica.gui.share.request_role.selected",
						roleLevel(this.requestPermissions), ROLES.length, role);
				this.addLabel(16, y, this.getStringWidth(selected), 12, 0xFF55FFFF, selected);
				y += 14;
				y = this.addWrappedLabel(16, y, Math.max(160, this.getScreenWidth() - 32),
						StringUtils.translate(requestRoleSummaryKey(this.requestPermissions)));

				this.addAction(12, y, "request_access", Action.REQUEST_ACCESS, true,
						StringUtils.translate("logisticmatica.gui.share.request_access_as", role));
				y += 28;
			}
		}

		if (!project.pendingInvite() && project.can(SharePermission.INVITE)) {
			String invite = StringUtils.translate("logisticmatica.gui.share.invite_label");
			this.addLabel(12, y, this.getStringWidth(invite), 12, 0xFFFFAA00, invite);
			y += 14;
			this.addAction(12, y, "choose_player", Action.CHOOSE_PLAYER, true);
			y += 28;
		}

		if (!project.pendingInvite()) {
			String members = StringUtils.translate("logisticmatica.gui.share.members");
			this.addLabel(12, y, this.getStringWidth(members), 12, 0xFFFFAA00, members);
			y += 14;
			for (SharedMemberView member : project.members()) {
				if (y + 22 >= this.getScreenHeight() - 38) break;
				String suffix = member.accessRequested()
						? StringUtils.translate("logisticmatica.gui.share.member.requested")
						: member.accepted() ? "" : StringUtils.translate("logisticmatica.gui.share.member.invited");
				String name = member.playerName() + suffix;
				this.addLabel(16, y + 5, this.getStringWidth(name), 12, 0xFFFFFFFF, name);
				String role = member.playerId().equals(project.ownerId())
						? StringUtils.translate("logisticmatica.gui.share.role.owner")
						: WidgetSharedProjectEntry.role(member.permissions());
				boolean editable = project.can(SharePermission.MANAGE_PERMISSIONS)
						&& !member.playerId().equals(project.ownerId());
				ButtonGeneric roleButton = new ButtonGeneric(180, y, 90, 20, role);
				roleButton.setEnabled(editable);
				roleButton.setHoverStrings(member.playerId().equals(project.ownerId())
						? "logisticmatica.gui.share.role.owner.description"
						: GuiPlayerPicker.roleDescriptionKey(member.permissions()));
				this.addButton(roleButton, new Listener(Action.MEMBER_ROLE, this, member.playerId()));
				if (editable) {
					if (member.accessRequested()) {
						ButtonGeneric approve = new ButtonGeneric(274, y, -1, 20,
								StringUtils.translate("logisticmatica.gui.share.approve"));
						this.addButton(approve, new Listener(Action.APPROVE_ACCESS, this, member.playerId()));
						ButtonGeneric decline = new ButtonGeneric(278 + approve.getWidth(), y, -1, 20,
								StringUtils.translate("logisticmatica.gui.share.decline"));
						this.addButton(decline, new Listener(Action.DECLINE_ACCESS, this, member.playerId()));
					} else {
						ButtonGeneric remove = new ButtonGeneric(274, y, -1, 20,
								StringUtils.translate("logisticmatica.gui.share.remove"));
						this.addButton(remove, new Listener(Action.REMOVE_MEMBER, this, member.playerId()));
					}
				}
				y += 22;
			}
		}

		boolean canDelete = project.can(SharePermission.DELETE);
		if (canDelete || (project.member() && !owner)) {
			String destructive = canDelete
					? StringUtils.translate(this.deleteArmed ? "logisticmatica.gui.share.confirm_delete"
							: "logisticmatica.gui.share.delete")
					: StringUtils.translate("logisticmatica.gui.share.leave");
			ButtonGeneric destructiveButton = new ButtonGeneric(12, this.getScreenHeight() - 34,
					-1, 20, destructive);
			this.addButton(destructiveButton,
					new Listener(canDelete ? Action.DELETE : Action.LEAVE, this, null));
		}

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back);
		this.addButton(backButton, new Listener(Action.BACK, this, null));
	}

	private int addAction(int x, int y, String key, Action action, boolean enabled) {
		return this.addAction(x, y, key, action, enabled,
				StringUtils.translate("logisticmatica.gui.share." + key));
	}

	private int addAction(int x, int y, String key, Action action, boolean enabled, String label) {
		ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, label);
		button.setEnabled(enabled);
		button.setHoverStrings("logisticmatica.gui.share." + key + ".description");
		this.addButton(button, new Listener(action, this, null));
		return x + button.getWidth() + 4;
	}

	private int addWrappedLabel(int x, int y, int width, String text) {
		for (String line : this.wrap(text, width)) {
			this.addLabel(x, y, this.getStringWidth(line), 12, 0xFFAAAAAA, line);
			y += 11;
		}
		return y + 3;
	}

	private int addRequestRoleSelector(int y) {
		int x = 12;
		int right = this.getScreenWidth() - 12;
		for (int i = 0; i < ROLES.length; i++) {
			int permissions = ROLES[i];
			boolean selected = permissions == this.requestPermissions;
			String role = WidgetSharedProjectEntry.role(permissions);
			String label = StringUtils.translate(selected
					? "logisticmatica.gui.share.request_role.selected_option"
					: "logisticmatica.gui.share.request_role.option", i + 1, role);
			ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, label);
			if (x > 12 && x + button.getWidth() > right) {
				x = 12;
				y += 24;
				button = new ButtonGeneric(x, y, -1, 20, label);
			}
			button.setEnabled(!selected);
			button.setHoverStrings(GuiPlayerPicker.roleDescriptionKey(permissions));
			this.addButton(button, new RequestRoleListener(this, permissions));
			x += button.getWidth() + 4;
		}
		return y + 24;
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

	@Override public void refreshSharing() { this.initGui(); }


	private static int roleLevel(int permissions) {
		for (int i = 0; i < ROLES.length; i++) {
			if (ROLES[i] == permissions) return i + 1;
		}
		return 1;
	}

	private static String requestRoleSummaryKey(int permissions) {
		if (permissions == SharePermission.VIEWER) return "logisticmatica.gui.share.request_role.viewer.summary";
		if (permissions == SharePermission.EDITOR) return "logisticmatica.gui.share.request_role.editor.summary";
		if (permissions == SharePermission.MANAGER) return "logisticmatica.gui.share.request_role.manager.summary";
		return "logisticmatica.gui.share.request_role.builder.summary";
	}

	private static ShareAccess nextAccess(ShareAccess current) {
		ShareAccess[] values = ShareAccess.values();
		return values[(current.ordinal() + 1) % values.length];
	}

	private static String accessName(ShareAccess access) {
		return StringUtils.translate("logisticmatica.gui.share.access."
				+ access.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static String accessDescriptionKey(ShareAccess access) {
		return "logisticmatica.gui.share.access."
				+ access.name().toLowerCase(java.util.Locale.ROOT) + ".description";
	}

	private enum Action {
		DOWNLOAD_FOCUS, FOCUS, OPEN_PLACEMENT, DOWNLOAD, REPLACE, HELP, ACCEPT, DECLINE, PUBLIC_ACCESS,
		REQUEST_ACCESS, CANCEL_REQUEST, CHOOSE_PLAYER,
		MEMBER_ROLE, APPROVE_ACCESS, DECLINE_ACCESS, REMOVE_MEMBER, DELETE, LEAVE, BACK
	}

	private record Listener(Action action, GuiSharedProjectDetails gui, @Nullable UUID memberId)
			implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.action) {
				case DOWNLOAD_FOCUS -> this.gui.sharing.downloadAndFocus(this.gui.projectId);
				case FOCUS -> this.gui.sharing.focusProject(this.gui.projectId);
				case OPEN_PLACEMENT -> {
					var placement = this.gui.sharing.placement(this.gui.projectId);
					if (placement != null) {
						GuiPlacementConfiguration placementGui = new GuiPlacementConfiguration(placement);
						placementGui.setParent(this.gui);
						GuiBase.openGui(placementGui);
					}
				}
				case DOWNLOAD -> this.gui.sharing.download(this.gui.projectId);
				case REPLACE -> {
					GuiPlacementPicker picker = new GuiPlacementPicker(
							GuiPlacementPicker.Mode.REPLACE, this.gui.projectId);
					picker.setParent(this.gui);
					GuiBase.openGui(picker);
				}
				case HELP -> {
					GuiSharingHelp help = new GuiSharingHelp();
					help.setParent(this.gui);
					GuiBase.openGui(help);
				}
				case ACCEPT -> this.gui.sharing.respondToInvite(this.gui.projectId, true);
				case DECLINE -> this.gui.sharing.respondToInvite(this.gui.projectId, false);
				case PUBLIC_ACCESS -> {
					SharedProjectView project = this.gui.sharing.project(this.gui.projectId);
					if (project != null) this.gui.sharing.setPublicAccess(
							this.gui.projectId, nextAccess(project.publicAccess()));
				}
				case REQUEST_ACCESS -> this.gui.sharing.requestAccess(
						this.gui.projectId, this.gui.requestPermissions);
				case CANCEL_REQUEST -> this.gui.sharing.leave(this.gui.projectId);
				case CHOOSE_PLAYER -> {
					GuiPlayerPicker picker = new GuiPlayerPicker(
							this.gui.projectId, this.gui.invitePermissions);
					picker.setParent(this.gui);
					GuiBase.openGui(picker);
				}
				case MEMBER_ROLE -> {
					SharedProjectView project = this.gui.sharing.project(this.gui.projectId);
					if (project != null && this.memberId != null) {
						for (SharedMemberView member : project.members()) {
							if (member.playerId().equals(this.memberId)) {
								GuiMemberPermissions permissions = new GuiMemberPermissions(
										project.id(), member.playerId(), member.permissions());
								permissions.setParent(this.gui);
								GuiBase.openGui(permissions);
								break;
							}
						}
					}
				}
				case APPROVE_ACCESS, DECLINE_ACCESS -> {
					SharedProjectView project = this.gui.sharing.project(this.gui.projectId);
					if (project != null && this.memberId != null) {
						for (SharedMemberView member : project.members()) {
							if (member.playerId().equals(this.memberId)) {
								this.gui.sharing.respondToAccessRequest(this.gui.projectId,
										this.memberId, this.action == Action.APPROVE_ACCESS,
										member.permissions());
								break;
							}
						}
					}
				}
				case REMOVE_MEMBER -> {
					if (this.memberId != null) this.gui.sharing.removeMember(this.gui.projectId, this.memberId);
				}
				case DELETE -> {
					if (this.gui.deleteArmed) this.gui.sharing.delete(this.gui.projectId);
					else { this.gui.deleteArmed = true; this.gui.initGui(); }
				}
				case LEAVE -> this.gui.sharing.leave(this.gui.projectId);
				case BACK -> GuiBase.openGui(this.gui.getParent());
			}
		}
	}

	private record RequestRoleListener(GuiSharedProjectDetails gui, int permissions)
			implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			this.gui.requestPermissions = this.permissions;
			this.gui.initGui();
		}
	}
}
