package com.skyraax.logisticmatica.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.profiling.ProfilerFiller;

import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.GuiUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;

import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Independent, fully configurable material HUD.
 *
 * <p>Reads Litematica's currently active material list ({@link DataManager#getMaterialList()})
 * and draws it via MaLiLib's {@link GuiContext}, honouring our own alignment / scale / colour /
 * column configuration. Unlike Litematica's built-in HUD it has no hard 10-line cap
 * ({@code hudMaxLines = 0} shows the whole list) and shows a have/need column per item.
 *
 * <p>The list is auto-fitted to the screen height so it can never run off the bottom; anything that
 * does not fit spills onto further pages, which the "Cycle HUD Page" hotkey steps through.
 */
public class MaterialHudRenderer implements IRenderer {
	/** Which page of the auto-fitted HUD to show; advanced by {@link #cycleHudPage()}. */
	private static int hudPage;

	private long lastAvailUpdate;

	/** Steps the material HUD to the next page (wraps around). Bound to a hotkey. */
	public static void cycleHudPage() {
		hudPage++;
	}

	@Override
	public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
		if (!Configs.Hud.ENABLED.getBooleanValue()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}

		// A material HUD is a gameplay overlay: don't draw it over open screens/menus
		// (e.g. Litematica's own material-list GUI) unless the user explicitly allows it.
		if (GuiUtils.getCurrentScreen() != null && !Configs.Hud.RENDER_IN_GUIS.getBooleanValue()) {
			return;
		}

		MaterialListBase materialList = DataManager.getMaterialList();
		if (materialList == null) {
			return;
		}

		// Refresh "available" counts from the player inventory at most once per second.
		long now = System.currentTimeMillis();
		if (now - this.lastAvailUpdate > 1000L) {
			MaterialListUtils.updateAvailableCounts(materialList.getMaterialsAll(), mc.player);
			this.lastAvailUpdate = now;
		}

		List<MaterialListEntry> list = this.buildDisplayList(materialList);
		if (list.isEmpty()) {
			return;
		}

		this.renderList(ctx, mc, materialList, list);
	}

	/** Applies our config-driven filtering + sorting to the raw entries. */
	private List<MaterialListEntry> buildDisplayList(MaterialListBase materialList) {
		boolean onlyMissing = Configs.Hud.ONLY_MISSING.getBooleanValue();
		boolean hideComplete = Configs.Hud.HIDE_COMPLETE.getBooleanValue();

		List<MaterialListEntry> list = new ArrayList<>();
		for (MaterialListEntry e : materialList.getMaterialsAll()) {
			boolean complete = e.getCountAvailable() >= e.getCountTotal();
			if (onlyMissing && e.getCountMissing() <= 0) {
				continue;
			}
			if (hideComplete && complete) {
				continue;
			}
			list.add(e);
		}

		// Incomplete items first (largest shortfall on top), then alphabetical.
		list.sort((a, b) -> {
			int shortfallA = Math.max(0, a.getCountTotal() - a.getCountAvailable());
			int shortfallB = Math.max(0, b.getCountTotal() - b.getCountAvailable());
			if (shortfallA != shortfallB) {
				return Integer.compare(shortfallB, shortfallA);
			}
			return a.getStack().getHoverName().getString()
					.compareToIgnoreCase(b.getStack().getHoverName().getString());
		});
		return list;
	}

	private void renderList(GuiContext ctx, Minecraft mc, MaterialListBase materialList, List<MaterialListEntry> list) {
		Font font = mc.font;

		final double scale = Math.max(0.1, Configs.Hud.SCALE.getDoubleValue());
		final HudAlignment alignment = (HudAlignment) Configs.Hud.ALIGNMENT.getOptionListValue();

		final boolean showHeader = Configs.Hud.SHOW_HEADER.getBooleanValue();
		final boolean showBg = Configs.Hud.SHOW_BACKGROUND.getBooleanValue();
		final boolean showIcons = Configs.Hud.SHOW_ITEM_ICONS.getBooleanValue();
		final boolean shadow = true;

		final int colText = Configs.Colors.TEXT.getIntegerValue();
		final int colHeader = Configs.Colors.HEADER.getIntegerValue();
		final int colHave = Configs.Colors.HAVE.getIntegerValue();
		final int colMissing = Configs.Colors.MISSING.getIntegerValue();
		final int colBg = Configs.Colors.BACKGROUND.getIntegerValue();

		final int pad = 3;
		final int columnGap = 10;
		final int lineHeight = showIcons ? 18 : (font.lineHeight + 2);
		final int iconGap = showIcons ? 20 : 0;
		final int headerHeight = showHeader ? font.lineHeight + 4 : 0;

		final int offX = Configs.Hud.OFFSET_X.getIntegerValue();
		final int offY = Configs.Hud.OFFSET_Y.getIntegerValue();
		final int screenW = (int) (GuiUtils.getScaledWindowWidth() / scale);
		final int screenH = (int) (GuiUtils.getScaledWindowHeight() / scale);

		// Auto-fit: cap the rows to what fits in the available height, and page through the rest.
		final int availableH = Math.max(lineHeight, screenH - offY - 8);
		final int rowsThatFit = Math.max(1, (availableH - headerHeight - pad * 2) / lineHeight);

		final int configMax = Configs.Hud.MAX_LINES.getIntegerValue();
		int perPage = configMax > 0 ? Math.min(configMax, rowsThatFit) : rowsThatFit;
		perPage = Math.max(1, Math.min(perPage, list.size()));

		final int pageCount = (list.size() + perPage - 1) / perPage;
		final int page = ((hudPage % pageCount) + pageCount) % pageCount;
		final int startIdx = page * perPage;
		final int rows = Math.min(perPage, list.size() - startIdx);

		// Pre-compute count strings + column widths for this page.
		final String[] counts = new String[rows];
		final int[] countColors = new int[rows];
		int nameW = 0;
		int countW = 0;
		for (int i = 0; i < rows; ++i) {
			MaterialListEntry e = list.get(startIdx + i);
			nameW = Math.max(nameW, font.width(e.getStack().getHoverName().getString()));
			String c = e.getCountAvailable() + " / " + e.getCountTotal();
			counts[i] = c;
			countColors[i] = e.getCountAvailable() >= e.getCountTotal() ? colHave : colMissing;
			countW = Math.max(countW, font.width(c));
		}

		String header = null;
		if (showHeader) {
			long total = materialList.getCountTotal();
			long missing = materialList.getCountMissing();
			String name = materialList.getName();
			header = (name != null && !name.isEmpty() ? name : "Materials") + "  " + (total - missing) + " / " + total;
			if (pageCount > 1) {
				header = header + "  [" + (page + 1) + "/" + pageCount + "]";
			}
		}
		final int headerW = header != null ? font.width(header) : 0;

		final int contentW = Math.max(iconGap + nameW + columnGap + countW, headerW);
		final int boxW = contentW + pad * 2;
		final int contentH = headerHeight + rows * lineHeight;
		final int boxH = contentH + pad * 2;

		int x;
		int y;
		switch (alignment) {
			case TOP_RIGHT -> {
				x = screenW - boxW - offX;
				y = offY;
			}
			case BOTTOM_LEFT -> {
				x = offX;
				y = screenH - boxH - offY;
			}
			case BOTTOM_RIGHT -> {
				x = screenW - boxW - offX;
				y = screenH - boxH - offY;
			}
			case CENTER -> {
				x = (screenW - boxW) / 2;
				y = (screenH - boxH) / 2;
			}
			default -> {
				x = offX;
				y = offY;
			}
		}

		if (scale != 1.0) {
			ctx.pose().pushMatrix();
			ctx.pose().scale((float) scale, (float) scale);
		}

		if (showBg) {
			ctx.fill(x, y, x + boxW, y + boxH, colBg);
		}

		final int textX = x + pad;
		int rowY = y + pad;

		if (showHeader) {
			ctx.drawString(font, header, textX, rowY, colHeader, shadow);
			rowY += headerHeight;
		}

		for (int i = 0; i < rows; ++i) {
			MaterialListEntry e = list.get(startIdx + i);
			if (showIcons) {
				ctx.renderItem(e.getStack(), textX, rowY + (lineHeight - 16) / 2);
			}
			int textY = rowY + (lineHeight - font.lineHeight) / 2;
			ctx.drawString(font, e.getStack().getHoverName().getString(), textX + iconGap, textY, colText, shadow);
			String c = counts[i];
			ctx.drawString(font, c, x + boxW - pad - font.width(c), textY, countColors[i], shadow);
			rowY += lineHeight;
		}

		if (scale != 1.0) {
			ctx.pose().popMatrix();
		}
	}
}
