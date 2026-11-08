package com.akf.tradeexpansion.client.compat.recipeviewer;

import com.akf.tradeexpansion.board.PriceLookupService;
import com.akf.tradeexpansion.client.Directions;
import com.akf.tradeexpansion.client.PriceLookupCache;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.network.PriceLookupS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * I (v0.47): the "nearby markets" page of the recipe viewers, drawn the same way for JEI and REI. The item slot sits at
 * the top left ({@link #SLOT_X}, {@link #SLOT_Y}, 18 x 18 with its frame); the viewer draws it. Uses only Minecraft's
 * types, so it loads without any recipe viewer.
 */
public final class MarketPricePanel {
	public static final int WIDTH = 160;
	private static final int HEADER_H = 20;
	private static final int ROW_H = 21;
	public static final int HEIGHT = HEADER_H + PriceLookupService.MAX_MARKETS * ROW_H;
	public static final int SLOT_X = 0;
	public static final int SLOT_Y = 0;

	private static final int COLOR_TEXT = 0xFF303030;
	private static final int COLOR_DIM = 0xFF606060;
	private static final int COLOR_SHORTAGE = 0xFFB04A00;
	private static final int COLOR_BUY = 0xFF1F5F8F;
	private static final int COLOR_SELL = 0xFF7F2F6F;
	private static final int COLOR_LINE = 0x40000000;

	private MarketPricePanel() {
	}

	/** Draws the page with its top left corner at (x, y). */
	public static void draw(GuiGraphicsExtractor g, Identifier item, int x, int y) {
		Font font = Minecraft.getInstance().font;
		PriceLookupS2C result = PriceLookupCache.get(item);
		int textX = x + SLOT_X + 21;
		if (result == null) {
			g.text(font, Component.translatable("recipe_viewer.ak_trade_expansion.loading"), textX, y + 5, COLOR_DIM, false);
			return;
		}
		g.text(font, Component.translatable("recipe_viewer.ak_trade_expansion.header", result.radius()), textX, y, COLOR_DIM, false);
		g.text(font, Component.translatable("recipe_viewer.ak_trade_expansion.hint"), textX, y + 9, COLOR_DIM, false);
		if (result.markets().isEmpty()) {
			int ly = y + HEADER_H + 4;
			for (FormattedCharSequence line : font.split(Component.translatable("recipe_viewer.ak_trade_expansion.none"), WIDTH - 4)) {
				g.text(font, line, x + 2, ly, COLOR_TEXT, false);
				ly += 10;
			}
			return;
		}
		int ry = y + HEADER_H;
		int rows = Math.min(result.markets().size(), PriceLookupService.MAX_MARKETS);
		for (int i = 0; i < rows; i++) {
			PriceLookupS2C.Entry e = result.markets().get(i);
			g.fill(x, ry, x + WIDTH, ry + 1, COLOR_LINE);
			String where = Component.translatable("screen.ak_trade_expansion.board.where", Directions.of(e.dx(), e.dz()), e.distance()).getString();
			int whereW = font.width(where);
			g.text(font, fit(font, e.title().getString(), WIDTH - whereW - 6), x + 2, ry + 2, e.shortage() ? COLOR_SHORTAGE : COLOR_TEXT, false);
			g.text(font, where, x + WIDTH - whereW - 1, ry + 2, COLOR_DIM, false);

			String buy = Component.translatable("recipe_viewer.ak_trade_expansion.buy", price(e.buy(), e.unit())).getString();
			String sell = Component.translatable("recipe_viewer.ak_trade_expansion.sell", price(e.sell(), e.unit())).getString();
			String stock = Component.translatable("recipe_viewer.ak_trade_expansion.stock", e.stock()).getString();
			g.text(font, buy, x + 2, ry + 11, COLOR_BUY, false);
			g.text(font, sell, x + 2 + Math.max(font.width(buy) + 6, 58), ry + 11, COLOR_SELL, false);
			g.text(font, stock, x + WIDTH - font.width(stock) - 1, ry + 11, COLOR_DIM, false);
			ry += ROW_H;
		}
	}

	/** Tooltip for the mouse at (mouseX, mouseY) relative to the page's top left corner, empty when none. */
	public static List<Component> tooltip(Identifier item, double mouseX, double mouseY) {
		if (mouseX < 0 || mouseX >= WIDTH || mouseY < HEADER_H || mouseY >= HEIGHT) return List.of();
		PriceLookupS2C result = PriceLookupCache.get(item);
		if (result == null) return List.of();
		int index = (int) ((mouseY - HEADER_H) / ROW_H);
		if (index >= Math.min(result.markets().size(), PriceLookupService.MAX_MARKETS)) return List.of();
		PriceLookupS2C.Entry e = result.markets().get(index);
		List<Component> lines = new ArrayList<>();
		lines.add(e.title());
		if (e.unit() > 1) lines.add(Component.translatable("recipe_viewer.ak_trade_expansion.unit", e.unit(), e.unit()));
		if (e.shortage()) lines.add(Component.translatable("recipe_viewer.ak_trade_expansion.shortage"));
		if (e.buy() < 0) lines.add(Component.translatable("recipe_viewer.ak_trade_expansion.no_buy"));
		if (e.sell() < 0) lines.add(Component.translatable("recipe_viewer.ak_trade_expansion.no_sell"));
		return lines;
	}

	private static String price(long price, int unit) {
		if (price < 0) return "-";
		return CoinItem.formatCopper(price) + (unit > 1 ? "/" + unit : "");
	}

	private static String fit(Font font, String text, int width) {
		if (font.width(text) <= width) return text;
		String ellipsis = "…";
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))) + ellipsis;
	}
}
