package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.network.NotebookRequestC2S;
import com.akf.tradeexpansion.network.NotebookS2C;
import com.akf.tradeexpansion.network.NotebookS2C.DayView;
import com.akf.tradeexpansion.network.NotebookS2C.GoodsView;
import com.akf.tradeexpansion.network.NotebookS2C.NoteView;
import com.akf.tradeexpansion.network.NotebookS2C.PriceView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The merchant's notebook (design T, v0.81): three pages from the server's {@link NotebookS2C}.
 * <ul>
 * <li>Today: takings, purchases, profit, request rewards and the staff's earnings of today, the most profitable goods, and
 * the switch of the morning report</li>
 * <li>Days: one line per day of the last days (the most profitable goods in the tooltip) and their total</li>
 * <li>Prices: the goods on the price notes; for the chosen one, its prices at each market noted, how old the note is and
 * where the market is, the cheapest place to buy and the best place to sell marked</li>
 * </ul>
 */
public class NotebookScreen extends Screen {
	private enum Tab { TODAY, DAYS, PRICES }

	private static final int COLOR_PAGE = 0xFFEDE3C8;
	private static final int COLOR_EDGE = 0xFF6B4423;
	private static final int COLOR_LINE = 0xFFC9BC98;
	private static final int COLOR_HOVER = 0xFFE0D2AC;
	private static final int COLOR_SELECTED = 0xFFD6C391;
	private static final int COLOR_TEXT = 0xFF3A2E1E;
	private static final int COLOR_MUTED = 0xFF7A6A50;
	private static final int COLOR_TITLE = 0xFF5A3A10;
	private static final int COLOR_PROFIT = 0xFF2E7D32;
	private static final int COLOR_LOSS = 0xFFB71C1C;
	private static final int COLOR_BEST = 0xFF1565C0;
	private static final int ROW_H = 18;
	/** A market's row on the price notes: its name, and how old the note is and where the market is below. */
	private static final int PRICE_ROW_H = 21;
	private static final int TOP = 26;
	private static final int BOTTOM = 26;
	private static final int GOODS_LIST_W = 112;
	private static final float MIN_TEXT_SCALE = 0.6f;

	private NotebookS2C data;
	private Tab tab = Tab.TODAY;
	private int left;
	private int top;
	private int panelW;
	private int panelH;
	private int scroll;
	private int goodsScroll;
	private Identifier selectedGoods;
	private Button reportButton;

	public NotebookScreen(NotebookS2C data) {
		super(Component.translatable("item.ak_trade_expansion.merchant_notebook"));
		this.data = data;
	}

	/** A fresh notebook from the server (the report switch was changed). */
	public void update(NotebookS2C fresh) {
		this.data = fresh;
		rebuildWidgets();
	}

	@Override
	protected void init() {
		panelW = Math.min(360, width - 16);
		panelH = Math.min(230, height - 16);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		Tab[] tabs = Tab.values();
		int tabW = Math.min(80, (panelW - 16) / tabs.length);
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			Button b = FittedButton.create(Component.translatable("screen.ak_trade_expansion.notebook.tab." + t.name().toLowerCase(java.util.Locale.ROOT)),
					btn -> {
						tab = t;
						scroll = 0;
						rebuildWidgets();
					}, left + 6 + i * (tabW + 2), top + 4, tabW, 18);
			b.active = tab != t;
			addRenderableWidget(b);
		}
		addRenderableWidget(FittedButton.create(Component.translatable("gui.done"), b -> onClose(), left + panelW - 66, top + panelH - 22, 60, 18));
		reportButton = null;
		if (tab == Tab.TODAY) {
			Component label = Component.translatable(data.reportOn() ? "screen.ak_trade_expansion.notebook.report_on" : "screen.ak_trade_expansion.notebook.report_off");
			reportButton = FittedButton.create(label, b -> ClientPlayNetworking.send(new NotebookRequestC2S(
					data.reportOn() ? NotebookRequestC2S.REPORT_OFF : NotebookRequestC2S.REPORT_ON)), left + 6, top + panelH - 22, Math.min(170, panelW - 80), 18);
			reportButton.active = data.reportEnabled();
			reportButton.setTooltip(Tooltip.create(Component.translatable(data.reportEnabled()
					? "screen.ak_trade_expansion.notebook.report.tooltip" : "screen.ak_trade_expansion.notebook.report.disabled")));
			addRenderableWidget(reportButton);
		}
		if (selectedGoods == null) {
			List<GoodsRow> rows = goodsRows();
			if (!rows.isEmpty()) selectedGoods = rows.getFirst().goods();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- data helpers ----

	private Optional<DayView> day(long day) {
		return data.days().stream().filter(d -> d.day() == day).findFirst();
	}

	private Component dayLabel(long day) {
		long ago = data.today() - day;
		if (ago == 0) return Component.translatable("screen.ak_trade_expansion.notebook.today_label", day + 1);
		if (ago == 1) return Component.translatable("screen.ak_trade_expansion.notebook.yesterday_label", day + 1);
		return Component.translatable("screen.ak_trade_expansion.notebook.day_label", day + 1);
	}

	private Component age(long day) {
		long ago = Math.max(0, data.today() - day);
		if (ago == 0) return Component.translatable("screen.ak_trade_expansion.notebook.age.today");
		if (ago == 1) return Component.translatable("screen.ak_trade_expansion.notebook.age.yesterday");
		return Component.translatable("screen.ak_trade_expansion.notebook.age.days", ago);
	}

	private static String signed(long amount) {
		return (amount >= 0 ? "+" : "-") + CoinItem.formatCopper(Math.abs(amount));
	}

	private static int profitColor(long amount) {
		return amount > 0 ? COLOR_PROFIT : amount < 0 ? COLOR_LOSS : COLOR_TEXT;
	}

	/** A goods on the price notes: its item, and the best spread between the notes (lowest buy → highest sell). */
	private record GoodsRow(Identifier goods, NotebookS2C.GoodsItem item, int unit, long spread, int markets) {
	}

	/** A market's price of the chosen goods. */
	private record PriceRow(NoteView note, PriceView price) {
	}

	private List<GoodsRow> goodsRows() {
		Map<Identifier, List<PriceView>> byGoods = new LinkedHashMap<>();
		for (NoteView note : data.notes()) {
			for (PriceView p : note.prices()) byGoods.computeIfAbsent(p.goods(), k -> new ArrayList<>()).add(p);
		}
		List<GoodsRow> rows = new ArrayList<>();
		byGoods.forEach((goods, prices) -> {
			long minBuy = prices.stream().mapToLong(PriceView::buy).filter(v -> v >= 0).min().orElse(-1);
			long maxSell = prices.stream().mapToLong(PriceView::sell).filter(v -> v >= 0).max().orElse(-1);
			long spread = minBuy >= 0 && maxSell >= 0 ? maxSell - minBuy : Long.MIN_VALUE;
			rows.add(new GoodsRow(goods, prices.getFirst().item(), prices.getFirst().unit(), spread, prices.size()));
		});
		// Goods with the best spread first (worth carrying), then by name.
		rows.sort(Comparator.comparingLong(GoodsRow::spread).reversed()
				.thenComparing(r -> r.item().stack().getHoverName().getString()));
		return rows;
	}

	private List<PriceRow> priceRows(Identifier goods) {
		List<PriceRow> rows = new ArrayList<>();
		for (NoteView note : data.notes()) {
			for (PriceView p : note.prices()) {
				if (p.goods().equals(goods)) rows.add(new PriceRow(note, p));
			}
		}
		rows.sort(Comparator.comparingLong((PriceRow r) -> r.price().sell()).reversed());
		return rows;
	}

	// ---- input ----

	private int listTop() {
		return top + TOP;
	}

	private int listBottom() {
		return top + panelH - BOTTOM;
	}

	private int visibleRows(int headerLines) {
		return Math.max(1, (listBottom() - listTop() - headerLines * 12) / ROW_H);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int step = (int) -Math.signum(scrollY);
		if (tab == Tab.PRICES && mouseX < left + GOODS_LIST_W) {
			int max = Math.max(0, goodsRows().size() - visibleRows(0));
			goodsScroll = Math.clamp(goodsScroll + step, 0, max);
			return true;
		}
		int total = switch (tab) {
			case TODAY -> todayGoods().size();
			case DAYS -> data.days().size();
			case PRICES -> selectedGoods == null ? 0 : priceRows(selectedGoods).size();
		};
		int max = Math.max(0, total - visibleRows(tab == Tab.DAYS ? 1 : 3));
		scroll = Math.clamp(scroll + step, 0, max);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (tab == Tab.PRICES && event.x() >= left + 4 && event.x() < left + GOODS_LIST_W && event.y() >= listTop() && event.y() < listBottom()) {
			int index = goodsScroll + (int) ((event.y() - listTop()) / ROW_H);
			List<GoodsRow> rows = goodsRows();
			if (index < rows.size() && index - goodsScroll < visibleRows(0)) {
				selectedGoods = rows.get(index).goods();
				scroll = 0;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---- rendering ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, COLOR_EDGE);
		g.fill(left, top, left + panelW, top + panelH, COLOR_PAGE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		switch (tab) {
			case TODAY -> extractToday(g, mouseX, mouseY);
			case DAYS -> extractDays(g, mouseX, mouseY);
			case PRICES -> extractPrices(g, mouseX, mouseY);
		}
	}

	private List<GoodsView> todayGoods() {
		return day(data.today()).map(DayView::top).orElse(List.of());
	}

	private void extractToday(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = left + 8;
		int y = listTop();
		int w = panelW - 16;
		DayView today = day(data.today()).orElse(new DayView(data.today(), 0, 0, 0, 0, 0, 0, List.of()));
		fitText(g, dayLabel(data.today()).getString(), x, y, w, COLOR_TITLE);
		y += 12;
		// Two columns of figures.
		int col = w / 2;
		figure(g, x, y, col - 4, Component.translatable("screen.ak_trade_expansion.notebook.sales"), CoinItem.formatCopper(today.sales()), COLOR_TEXT);
		figure(g, x + col, y, col - 4, Component.translatable("screen.ak_trade_expansion.notebook.purchases"), CoinItem.formatCopper(today.purchases()), COLOR_TEXT);
		y += 11;
		figure(g, x, y, col - 4, Component.translatable("screen.ak_trade_expansion.notebook.quests"), CoinItem.formatCopper(today.quests()), COLOR_TEXT);
		figure(g, x + col, y, col - 4, Component.translatable("screen.ak_trade_expansion.notebook.staff"), signed(today.staff()), profitColor(today.staff()));
		y += 11;
		figure(g, x, y, col - 4, Component.translatable("screen.ak_trade_expansion.notebook.profit"), signed(today.totalProfit()), profitColor(today.totalProfit()));
		boolean hoverProfit = mouseX >= x && mouseX < x + col - 4 && mouseY >= y && mouseY < y + 10;
		y += 14;
		g.fill(x, y - 3, x + w, y - 2, COLOR_LINE);
		fitText(g, Component.translatable("screen.ak_trade_expansion.notebook.best").getString(), x, y, w, COLOR_TITLE);
		y += 11;
		List<GoodsView> goods = todayGoods();
		if (goods.isEmpty()) {
			fitText(g, Component.translatable("screen.ak_trade_expansion.notebook.best.none").getString(), x, y + 4, w, COLOR_MUTED);
		}
		int rows = Math.max(1, (listBottom() - y) / ROW_H);
		scroll = Math.clamp(scroll, 0, Math.max(0, goods.size() - rows));
		ItemStack hovered = null;
		for (int i = 0; i < rows && scroll + i < goods.size(); i++) {
			GoodsView gv = goods.get(scroll + i);
			int ry = y + i * ROW_H;
			ItemStack stack = gv.item().stack();
			g.item(stack, x, ry);
			if (mouseX >= x && mouseX < x + 16 && mouseY >= ry && mouseY < ry + 16) hovered = stack;
			String right = Component.translatable("screen.ak_trade_expansion.notebook.best.line", signed(gv.profit()), gv.items()).getString();
			int rw = font.width(right);
			g.text(font, right, x + w - rw, ry + 4, profitColor(gv.profit()), false);
			fitText(g, stack.getHoverName().getString(), x + 20, ry + 4, w - rw - 26, COLOR_TEXT);
		}
		if (hovered != null) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
		else if (hoverProfit) {
			g.setTooltipForNextFrame(font, MarketScreen.wrapTooltip(font, width, List.of(
					Component.translatable("screen.ak_trade_expansion.notebook.profit.tooltip"))), Optional.empty(), mouseX, mouseY);
		}
	}

	/** "label  value" with the value on the right of {@code w}. */
	private void figure(GuiGraphicsExtractor g, int x, int y, int w, Component label, String value, int valueColor) {
		int vw = font.width(value);
		g.text(font, value, x + w - vw, y, valueColor, false);
		fitText(g, label.getString(), x, y, w - vw - 4, COLOR_MUTED);
	}

	private void extractDays(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = left + 8;
		int w = panelW - 16;
		int y = listTop();
		// Columns: day | sales | purchases | staff | profit (right-aligned numbers)
		int dayW = Math.max(60, w / 4);
		int numW = (w - dayW) / 4;
		String[] heads = {"sales", "purchases", "col.staff", "profit"};
		fitText(g, Component.translatable("screen.ak_trade_expansion.notebook.col.day").getString(), x, y, dayW - 4, COLOR_TITLE);
		for (int c = 0; c < heads.length; c++) {
			String h = Component.translatable("screen.ak_trade_expansion.notebook." + heads[c]).getString();
			rightText(g, h, x + dayW + (c + 1) * numW, y, numW - 4, COLOR_TITLE);
		}
		y += 12;
		g.fill(x, y - 2, x + w, y - 1, COLOR_LINE);
		List<DayView> days = new ArrayList<>(data.days());
		days.sort(Comparator.comparingLong(DayView::day).reversed()); // today first
		if (days.isEmpty()) {
			fitText(g, Component.translatable("screen.ak_trade_expansion.notebook.days.none").getString(), x, y + 4, w, COLOR_MUTED);
			return;
		}
		int rows = Math.max(1, (listBottom() - 14 - y) / ROW_H);
		scroll = Math.clamp(scroll, 0, Math.max(0, days.size() - rows));
		DayView hovered = null;
		for (int i = 0; i < rows && scroll + i < days.size(); i++) {
			DayView d = days.get(scroll + i);
			int ry = y + i * ROW_H;
			if (mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H) {
				g.fill(x, ry, x + w, ry + ROW_H, COLOR_HOVER);
				hovered = d;
			}
			fitText(g, dayLabel(d.day()).getString(), x + 2, ry + 5, dayW - 6, COLOR_TEXT);
			rightText(g, CoinItem.formatCopper(d.sales()), x + dayW + numW, ry + 5, numW - 4, COLOR_TEXT);
			rightText(g, CoinItem.formatCopper(d.purchases()), x + dayW + 2 * numW, ry + 5, numW - 4, COLOR_TEXT);
			rightText(g, d.staff() == 0 ? "-" : signed(d.staff()), x + dayW + 3 * numW, ry + 5, numW - 4, profitColor(d.staff()));
			rightText(g, signed(d.totalProfit()), x + dayW + 4 * numW, ry + 5, numW - 4, profitColor(d.totalProfit()));
		}
		// Total of the days kept.
		long sales = days.stream().mapToLong(DayView::sales).sum();
		long purchases = days.stream().mapToLong(DayView::purchases).sum();
		long staff = days.stream().mapToLong(DayView::staff).sum();
		long profit = days.stream().mapToLong(DayView::totalProfit).sum();
		int ty = listBottom() - 10;
		g.fill(x, ty - 3, x + w, ty - 2, COLOR_LINE);
		fitText(g, Component.translatable("screen.ak_trade_expansion.notebook.total", days.size()).getString(), x + 2, ty, dayW - 6, COLOR_TITLE);
		rightText(g, CoinItem.formatCopper(sales), x + dayW + numW, ty, numW - 4, COLOR_TEXT);
		rightText(g, CoinItem.formatCopper(purchases), x + dayW + 2 * numW, ty, numW - 4, COLOR_TEXT);
		rightText(g, staff == 0 ? "-" : signed(staff), x + dayW + 3 * numW, ty, numW - 4, profitColor(staff));
		rightText(g, signed(profit), x + dayW + 4 * numW, ty, numW - 4, profitColor(profit));
		if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(dayLabel(hovered.day()));
			lines.add(Component.translatable("screen.ak_trade_expansion.notebook.days.tooltip.trades", hovered.trades()));
			if (hovered.quests() != 0) {
				lines.add(Component.translatable("screen.ak_trade_expansion.notebook.days.tooltip.quests", CoinItem.formatCopper(hovered.quests())));
			}
			if (!hovered.top().isEmpty()) lines.add(Component.translatable("screen.ak_trade_expansion.notebook.best"));
			for (GoodsView gv : hovered.top()) {
				lines.add(Component.translatable("screen.ak_trade_expansion.notebook.days.tooltip.goods", gv.item().stack().getHoverName(),
						signed(gv.profit()), gv.items()));
			}
			g.setTooltipForNextFrame(font, MarketScreen.wrapTooltip(font, width, lines), Optional.empty(), mouseX, mouseY);
		}
	}

	private void extractPrices(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = left + 4;
		int y = listTop();
		List<GoodsRow> goods = goodsRows();
		if (goods.isEmpty()) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.notebook.prices.none"), left + 8, y + 4, panelW - 16, COLOR_MUTED, false);
			return;
		}
		if (selectedGoods == null || goods.stream().noneMatch(r -> r.goods().equals(selectedGoods))) selectedGoods = goods.getFirst().goods();
		// Left: the goods noted.
		int listW = GOODS_LIST_W - 6;
		int rows = visibleRows(0);
		goodsScroll = Math.clamp(goodsScroll, 0, Math.max(0, goods.size() - rows));
		ItemStack hoveredStack = null;
		for (int i = 0; i < rows && goodsScroll + i < goods.size(); i++) {
			GoodsRow r = goods.get(goodsScroll + i);
			int ry = y + i * ROW_H;
			boolean hover = mouseX >= x && mouseX < x + listW && mouseY >= ry && mouseY < ry + ROW_H;
			if (r.goods().equals(selectedGoods)) g.fill(x, ry, x + listW, ry + ROW_H, COLOR_SELECTED);
			else if (hover) g.fill(x, ry, x + listW, ry + ROW_H, COLOR_HOVER);
			ItemStack stack = r.item().stack();
			g.item(stack, x + 1, ry + 1);
			if (hover && mouseX < x + 17) hoveredStack = stack;
			String spread = r.spread() > 0 ? "+" + CoinItem.formatCopper(r.spread()) : "";
			int sw = font.width(spread);
			if (!spread.isEmpty()) g.text(font, spread, x + listW - sw - 1, ry + 5, COLOR_PROFIT, false);
			fitText(g, stack.getHoverName().getString(), x + 19, ry + 5, listW - 22 - (sw > 0 ? sw + 2 : 0), COLOR_TEXT);
		}
		g.fill(left + GOODS_LIST_W, y, left + GOODS_LIST_W + 1, listBottom(), COLOR_LINE);

		// Right: the chosen goods at each market.
		int px = left + GOODS_LIST_W + 6;
		int pw = left + panelW - 6 - px;
		GoodsRow chosen = goods.stream().filter(r -> r.goods().equals(selectedGoods)).findFirst().orElseThrow();
		List<PriceRow> prices = priceRows(selectedGoods);
		long minBuy = prices.stream().mapToLong(r -> r.price().buy()).filter(v -> v >= 0).min().orElse(-1);
		long maxSell = prices.stream().mapToLong(r -> r.price().sell()).filter(v -> v >= 0).max().orElse(-1);
		String unit = chosen.unit() > 1 ? Component.translatable("screen.ak_trade_expansion.notebook.prices.unit", chosen.unit()).getString() : "";
		fitText(g, chosen.item().stack().getHoverName().getString() + unit, px, y, pw, COLOR_TITLE);
		int hy = y + 12;
		// Summary: where to buy, where to sell.
		Component summary = chosen.spread() > 0
				? Component.translatable("screen.ak_trade_expansion.notebook.prices.spread", signed(chosen.spread()))
				: Component.translatable(chosen.markets() < 2 ? "screen.ak_trade_expansion.notebook.prices.one" : "screen.ak_trade_expansion.notebook.prices.no_spread");
		fitText(g, summary.getString(), px, hy, pw, chosen.spread() > 0 ? COLOR_PROFIT : COLOR_MUTED);
		hy += 12;
		int buyX = px + pw - 52;
		int sellX = px + pw;
		rightText(g, Component.translatable("screen.ak_trade_expansion.notebook.prices.buy").getString(), buyX, hy, 48, COLOR_MUTED);
		rightText(g, Component.translatable("screen.ak_trade_expansion.notebook.prices.sell").getString(), sellX, hy, 48, COLOR_MUTED);
		hy += 11;
		g.fill(px, hy - 2, px + pw, hy - 1, COLOR_LINE);
		int priceRows = Math.max(1, (listBottom() - hy) / PRICE_ROW_H);
		boolean compare = prices.size() > 1; // the cheapest / dearest are marked only among several markets
		scroll = Math.clamp(scroll, 0, Math.max(0, prices.size() - priceRows));
		PriceRow hovered = null;
		for (int i = 0; i < priceRows && scroll + i < prices.size(); i++) {
			PriceRow r = prices.get(scroll + i);
			int ry = hy + i * PRICE_ROW_H;
			if (mouseX >= px && mouseX < px + pw && mouseY >= ry && mouseY < ry + PRICE_ROW_H) {
				g.fill(px, ry, px + pw, ry + PRICE_ROW_H, COLOR_HOVER);
				hovered = r;
			}
			long buy = r.price().buy();
			long sell = r.price().sell();
			rightText(g, buy < 0 ? "-" : CoinItem.formatCopper(buy), buyX, ry + 1, 48, compare && buy >= 0 && buy == minBuy ? COLOR_BEST : COLOR_TEXT);
			rightText(g, sell < 0 ? "-" : CoinItem.formatCopper(sell), sellX, ry + 1, 48, compare && sell >= 0 && sell == maxSell ? COLOR_PROFIT : COLOR_TEXT);
			int nameW = buyX - 52 - px;
			fitText(g, r.note().title().getString(), px + 1, ry + 1, nameW, COLOR_TEXT);
			fitText(g, age(r.note().day()).getString() + "  " + where(r.note()), px + 1, ry + 11, nameW, COLOR_MUTED);
		}
		if (hoveredStack != null) {
			g.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
		} else if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(hovered.note().title());
			lines.add(Component.translatable("screen.ak_trade_expansion.notebook.prices.tooltip.age", age(hovered.note().day())));
			lines.add(Component.literal(where(hovered.note())));
			if (hovered.price().buy() >= 0 && hovered.price().buy() == minBuy && prices.size() > 1) {
				lines.add(Component.translatable("screen.ak_trade_expansion.notebook.prices.tooltip.cheapest"));
			}
			if (hovered.price().sell() >= 0 && hovered.price().sell() == maxSell && prices.size() > 1) {
				lines.add(Component.translatable("screen.ak_trade_expansion.notebook.prices.tooltip.dearest"));
			}
			lines.add(Component.translatable("screen.ak_trade_expansion.notebook.prices.tooltip.old"));
			g.setTooltipForNextFrame(font, MarketScreen.wrapTooltip(font, width, lines), Optional.empty(), mouseX, mouseY);
		}
	}

	/** "north-east 340m", or the dimension of a market elsewhere. */
	private String where(NoteView note) {
		if (minecraft == null || minecraft.player == null || minecraft.level == null) return "";
		if (!minecraft.level.dimension().identifier().equals(note.dimension())) {
			return Component.translatable("screen.ak_trade_expansion.notebook.prices.other_dimension").getString();
		}
		int dx = note.x() - minecraft.player.getBlockX();
		int dz = note.z() - minecraft.player.getBlockZ();
		int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
		return Component.translatable("screen.ak_trade_expansion.notebook.prices.where", Directions.of(dx, dz), distance).getString();
	}

	// ---- text ----

	private void rightText(GuiGraphicsExtractor g, String text, int right, int y, int maxWidth, int color) {
		int w = Math.min(font.width(text), maxWidth);
		fitText(g, text, right - w, y, maxWidth, color);
	}

	/** Draws {@code text} within {@code maxWidth}: shrunk (down to {@link #MIN_TEXT_SCALE}) or cut with "…" when too wide. */
	private void fitText(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color) {
		int width = font.width(text);
		if (width <= maxWidth) {
			g.text(font, text, x, y, color, false);
			return;
		}
		float scale = Math.max(MIN_TEXT_SCALE, maxWidth / (float) width);
		String shown = text;
		if (width * scale > maxWidth) {
			int room = (int) (maxWidth / scale) - font.width("…");
			shown = font.plainSubstrByWidth(text, Math.max(0, room)) + "…";
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y + font.lineHeight * (1 - scale) / 2);
		g.pose().scale(scale, scale);
		g.text(font, shown, 0, 0, color, false);
		g.pose().popMatrix();
	}
}
