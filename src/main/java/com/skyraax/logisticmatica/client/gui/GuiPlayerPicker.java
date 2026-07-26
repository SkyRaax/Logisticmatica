package com.skyraax.logisticmatica.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedMemberView;
import com.skyraax.logisticmatica.share.SharedPlayerView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Searchable online-player picker with an explicit role chosen before sending an invitation. */
public class GuiPlayerPicker extends GuiListBase<SharedPlayerView, GuiPlayerPicker.Entry,
		GuiPlayerPicker.PlayerList> implements SharingRefreshable {
	private static final int[] ROLES = {
		SharePermission.VIEWER, SharePermission.BUILDER, SharePermission.EDITOR, SharePermission.MANAGER
	};

	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private final UUID projectId;
	private int permissions;
	private String filterText = "";

	public GuiPlayerPicker(UUID projectId, int permissions) {
		super(10, 68);
		this.projectId = projectId;
		this.permissions = permissions;
		this.useTitleHierarchy = false;
		this.title = StringUtils.translate("logisticmatica.gui.title.player_picker");
		this.sharing.refreshPlayers();
	}

	@Override protected int getBrowserWidth() { return this.getScreenWidth() - 20; }
	@Override protected int getBrowserHeight() { return this.getScreenHeight() - 104; }
	@Override protected PlayerList createListWidget(int x, int y) {
		PlayerList list = new PlayerList(x, y, this.getBrowserWidth(), this.getBrowserHeight(), this);
		list.setFilterText(this.filterText);
		return list;
	}

	@Override
	public void initGui() {
		super.initGui();
		GuiTextFieldGeneric search = new GuiTextFieldGeneric(12, 46, 220, 16, this.font);
		search.setValueWrapper(this.filterText);
		this.addTextField(search, new SearchListener(this), TextFieldType.STRING);
		String role = WidgetSharedProjectEntry.role(this.permissions);
		ButtonGeneric roleButton = new ButtonGeneric(238, 44, -1, 20,
				StringUtils.translate("logisticmatica.gui.share.invite_as", role));
		roleButton.setHoverStrings(roleDescriptionKey(this.permissions));
		this.addButton(roleButton, (button, mouseButton) -> {
			this.permissions = nextRole(this.permissions);
			this.initGui();
		});

		ButtonGeneric help = new ButtonGeneric(244 + roleButton.getWidth(), 44, -1, 20,
				StringUtils.translate("logisticmatica.gui.share.explain_roles"));
		this.addButton(help, (button, mouseButton) -> {
			GuiSharingHelp screen = new GuiSharingHelp();
			screen.setParent(this);
			GuiBase.openGui(screen);
		});

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		this.addButton(new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back),
				(button, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	@Override public void refreshSharing() {
		PlayerList list = this.getListWidget();
		if (list != null) list.refreshEntries();
	}

	private void choose(SharedPlayerView player) {
		this.sharing.invite(this.projectId, player, this.permissions);
		GuiBase.openGui(this.getParent());
	}

	private static int nextRole(int current) {
		for (int i = 0; i < ROLES.length; i++) if (ROLES[i] == current) return ROLES[(i + 1) % ROLES.length];
		return SharePermission.VIEWER;
	}

	static String roleDescriptionKey(int permissions) {
		String role = permissions == SharePermission.MANAGER ? "manager"
				: permissions == SharePermission.EDITOR ? "editor"
				: permissions == SharePermission.BUILDER ? "builder" : "viewer";
		return "logisticmatica.gui.share.role." + role + ".description";
	}

	private record SearchListener(GuiPlayerPicker gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override public boolean onTextChange(GuiTextFieldGeneric field) {
			this.gui.filterText = field.getValueWrapper();
			PlayerList list = this.gui.getListWidget();
			if (list != null) {
				list.setFilterText(this.gui.filterText);
				list.refreshEntries();
				list.resetScrollbarPosition();
			}
			return true;
		}
	}

	static final class PlayerList extends WidgetListBase<SharedPlayerView, Entry> {
		private final GuiPlayerPicker gui;
		private String filterText = "";

		PlayerList(int x, int y, int width, int height, GuiPlayerPicker gui) {
			super(x, y, width, height, null);
			this.gui = gui;
			this.browserEntryHeight = 26;
		}
		void setFilterText(String value) { this.filterText = value != null ? value : ""; }
		@Override protected boolean hasFilter() { return !this.filterText.isBlank(); }
		@Override protected String getFilterText() { return this.filterText.toLowerCase(Locale.ROOT); }
		@Override protected Collection<SharedPlayerView> getAllEntries() {
			SharedProjectView project = this.gui.sharing.project(this.gui.projectId);
			Set<UUID> excluded = new HashSet<>();
			if (Minecraft.getInstance().player != null) excluded.add(Minecraft.getInstance().player.getUUID());
			if (project != null) {
				excluded.add(project.ownerId());
				for (SharedMemberView member : project.members()) excluded.add(member.playerId());
			}
			List<SharedPlayerView> result = new ArrayList<>();
			for (SharedPlayerView player : this.gui.sharing.players()) {
				if (!excluded.contains(player.playerId())) result.add(player);
			}
			return result;
		}
		@Override protected List<String> getEntryStringsForFilter(SharedPlayerView player) {
			return List.of(player.playerName());
		}
		@Override protected boolean onEntryClicked(@Nullable SharedPlayerView player, int index) {
			if (player != null) this.gui.choose(player);
			return true;
		}
		@Override protected Entry createListEntryWidget(int x, int y, int index, boolean odd,
				@Nullable SharedPlayerView player) {
			return new Entry(x, y, this.browserEntryWidth, this.browserEntryHeight, odd, player, index);
		}
	}

	static final class Entry extends WidgetListEntryBase<SharedPlayerView> {
		@Nullable private final SharedPlayerView player;
		private final boolean odd;
		Entry(int x, int y, int width, int height, boolean odd, @Nullable SharedPlayerView player, int index) {
			super(x, y, width, height, player, index);
			this.player = player;
			this.odd = odd;
		}
		@Override public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height,
					selected || this.isMouseOver(mouseX, mouseY) ? 0xA0707070
							: this.odd ? 0xA0101010 : 0xA0303030);
			if (this.player != null) {
				this.drawString(ctx, this.x + 7, this.y + 8, 0xFFFFFFFF, this.player.playerName());
				String action = StringUtils.translate("logisticmatica.gui.share.click_to_invite");
				this.drawString(ctx, this.x + this.width - this.getStringWidth(action) - 8,
						this.y + 8, 0xFF55FF55, action);
			}
			super.render(ctx, mouseX, mouseY, selected);
		}
	}
}
