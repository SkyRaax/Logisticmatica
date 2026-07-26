package com.skyraax.logisticmatica.client.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;

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

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.skyraax.logisticmatica.client.share.ClientShareManager;

/** Searchable picker used both for creating a share and replacing a project's schematic file. */
public class GuiPlacementPicker extends GuiListBase<SchematicPlacement, GuiPlacementPicker.Entry,
		GuiPlacementPicker.PlacementList> {
	public enum Mode { SHARE, REPLACE }

	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private final Mode mode;
	@Nullable private final UUID projectId;
	private String filterText = "";

	public GuiPlacementPicker(Mode mode, @Nullable UUID projectId) {
		super(10, 68);
		this.mode = mode;
		this.projectId = projectId;
		this.useTitleHierarchy = false;
		this.title = StringUtils.translate(mode == Mode.SHARE
				? "logisticmatica.gui.title.share_placement"
				: "logisticmatica.gui.title.replace_placement");
	}

	@Override protected int getBrowserWidth() { return this.getScreenWidth() - 20; }
	@Override protected int getBrowserHeight() { return this.getScreenHeight() - 104; }

	@Override
	protected PlacementList createListWidget(int listX, int listY) {
		PlacementList list = new PlacementList(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
		list.setFilterText(this.filterText);
		return list;
	}

	@Override
	public void initGui() {
		super.initGui();
		GuiTextFieldGeneric search = new GuiTextFieldGeneric(12, 46, 220, 16, this.font);
		search.setValueWrapper(this.filterText);
		this.addTextField(search, new SearchListener(this), TextFieldType.STRING);

		SchematicPlacement selected = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
		ButtonGeneric useSelected = new ButtonGeneric(238, 44, -1, 20,
				StringUtils.translate("logisticmatica.gui.share.use_selected"));
		useSelected.setEnabled(selected != null);
		useSelected.setHoverStrings("logisticmatica.gui.share.use_selected.hover");
		this.addButton(useSelected, (button, mouseButton) -> {
			if (selected != null) this.choose(selected);
		});

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		this.addButton(new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back),
				(button, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	private void choose(SchematicPlacement placement) {
		if (this.mode == Mode.SHARE) this.sharing.sharePlacement(placement);
		else if (this.projectId != null) this.sharing.replaceSchematic(this.projectId, placement);
		GuiBase.openGui(this.getParent());
	}

	private record SearchListener(GuiPlacementPicker gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override public boolean onTextChange(GuiTextFieldGeneric field) {
			this.gui.filterText = field.getValueWrapper();
			PlacementList list = this.gui.getListWidget();
			if (list != null) {
				list.setFilterText(this.gui.filterText);
				list.refreshEntries();
				list.resetScrollbarPosition();
			}
			return true;
		}
	}

	static final class PlacementList extends WidgetListBase<SchematicPlacement, Entry> {
		private final GuiPlacementPicker gui;
		private String filterText = "";

		PlacementList(int x, int y, int width, int height, GuiPlacementPicker gui) {
			super(x, y, width, height, null);
			this.gui = gui;
			this.browserEntryHeight = 34;
		}

		void setFilterText(String value) { this.filterText = value != null ? value : ""; }
		@Override protected boolean hasFilter() { return !this.filterText.isBlank(); }
		@Override protected String getFilterText() { return this.filterText.toLowerCase(Locale.ROOT); }
		@Override protected Collection<SchematicPlacement> getAllEntries() {
			SchematicPlacement selected = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
			List<SchematicPlacement> entries = new ArrayList<>(this.gui.sharing.availablePlacements());
			entries.sort(Comparator.comparing((SchematicPlacement p) -> p != selected)
					.thenComparing(SchematicPlacement::getName, String.CASE_INSENSITIVE_ORDER));
			return entries;
		}
		@Override protected List<String> getEntryStringsForFilter(SchematicPlacement placement) {
			Path file = placement.getSchematicFile();
			return List.of(placement.getName(), String.valueOf(file), placement.getOrigin().toShortString());
		}
		@Override protected boolean onEntryClicked(@Nullable SchematicPlacement placement, int index) {
			if (placement != null) this.gui.choose(placement);
			return true;
		}
		@Override protected Entry createListEntryWidget(int x, int y, int index, boolean odd,
				@Nullable SchematicPlacement placement) {
			return new Entry(x, y, this.browserEntryWidth, this.browserEntryHeight, odd, placement, index);
		}
	}

	static final class Entry extends WidgetListEntryBase<SchematicPlacement> {
		@Nullable private final SchematicPlacement placement;
		private final boolean odd;

		Entry(int x, int y, int width, int height, boolean odd,
				@Nullable SchematicPlacement placement, int index) {
			super(x, y, width, height, placement, index);
			this.placement = placement;
			this.odd = odd;
		}

		@Override public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
			int background = selected || this.isMouseOver(mouseX, mouseY) ? 0xA0707070
					: this.odd ? 0xA0101010 : 0xA0303030;
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, background);
			if (this.placement != null) {
				boolean active = this.placement == DataManager.getSchematicPlacementManager()
						.getSelectedSchematicPlacement();
				String name = (active ? "★ " : "") + this.placement.getName();
				this.drawString(ctx, this.x + 6, this.y + 5, active ? 0xFF55FF55 : 0xFFFFFFFF, name);
				Path file = this.placement.getSchematicFile();
				String detail = this.placement.getOrigin().toShortString() + "  ·  "
						+ (file != null ? file.getFileName() : StringUtils.translate("logisticmatica.gui.share.unsaved"));
				this.drawString(ctx, this.x + 6, this.y + 19, 0xFFAAAAAA, detail);
			}
			super.render(ctx, mouseX, mouseY, selected);
		}
	}
}
