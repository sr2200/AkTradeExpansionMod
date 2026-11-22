package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.WalletScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.data.RivalGuilds;
import com.akf.tradeexpansion.entity.TraderEntity;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.FacilityView;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of what the trade screen shows beyond buying and selling (checklist chapters 6–9, 16, 18, 22–24, 28,
 * 29, 31 and 34): the title row (reputation, trends and where the trends go), the debug tooltip, the board with its
 * news and trends, "sell all", the order of the lists, the development tab, the wallet and the exchange tab.
 */
final class ClientDisplayCases {
	private ClientDisplayCases() {
	}

	private static final int RGB_RED = 0xFF5555;
	private static final int RGB_GREEN = 0x55FF55;
	/** Width MarketScreen keeps free at the right end of the title row (TITLE_RIGHT_RESERVE). */
	private static final int TITLE_RIGHT_RESERVE = 24;
	/**
	 * Right edges of texts right-aligned to the same x differ by a few pixels in the recorded areas (Japanese glyphs and
	 * digits are measured differently); a column out of line is off by much more.
	 */
	private static final int COLUMN_SLACK = 4;
	/** Every building of the development tab (the bundled facility data). */
	private static final List<String> FACILITIES = List.of("quest_hall", "warehouse", "inn", "bank", "fountain", "park", "clock_tower",
			"watchtower", "tavern", "market_hall", "harbor", "theater", "hot_spring", "festival", "mint");

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** The part of a translation before its first %s. */
	private static String prefix(String key) {
		return Language.getInstance().getOrDefault(key).split("%s", -1)[0];
	}

	private static Component reputationName(int level) {
		return Component.translatable("screen.ak_trade_expansion.reputation.level." + Math.clamp(level, 0, 5));
	}

	private static int imageWidth(MarketScreen screen) {
		return (Integer) get(screen, AbstractContainerScreen.class, "imageWidth");
	}

	/** Calls an accessor of a record object (the screen's private records). */
	private static Object field(Object record, String accessor) {
		try {
			Method m = record.getClass().getDeclaredMethod(accessor);
			m.setAccessible(true);
			return m.invoke(record);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read " + record.getClass().getSimpleName() + "." + accessor, e);
		}
	}

	static TextRecorder.Text find(List<TextRecorder.Text> texts, String text, String what) {
		return texts.stream().filter(t -> t.text().equals(text)).findFirst()
				.orElseThrow(() -> new AssertionError("no \"" + text + "\" (" + what + ") on the screen: " + strings(texts)));
	}

	/** Texts of the screen's own layer whose top is between y0 and y1. */
	static List<TextRecorder.Text> band(List<TextRecorder.Text> texts, int y0, int y1) {
		int base = TextRecorder.baseStratum(texts);
		return texts.stream().filter(t -> t.stratum() == base && t.y0() >= y0 && t.y0() < y1).toList();
	}

	/**
	 * Texts of any layer whose top is between y0 and y1 (with the mouse away there is no tooltip): rows with item icons
	 * draw their texts in the layers after the icons.
	 */
	static List<TextRecorder.Text> area(List<TextRecorder.Text> texts, int y0, int y1) {
		return texts.stream().filter(t -> t.y0() >= y0 && t.y0() < y1).toList();
	}

	/** Changes the client settings (shared with the integrated server in singleplayer, as the config screen does). */
	private static void client(ClientGameTestContext context, Consumer<TradeExpansionConfig.Client> change) {
		context.runOnClient(c -> {
			TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
			change.accept(live.client);
			TradeExpansionConfig.set(live);
		});
		context.waitTicks(1);
	}

	/** Closes the screen and opens the stall again: a new snapshot with what the server changed. */
	static MarketScreen reopen(Session s) {
		s.context.setScreen(() -> null);
		s.context.waitTicks(2);
		return ClientChecklistTests.openStall(s);
	}

	/** The market has a trend in fashion (P), so the screen shows "│ 流行". */
	private static void ensureTrend(Session s) {
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> ensureTrend(market(server, stall)));
	}

	static void ensureTrend(Market market) {
		if (market.trends().values().stream().anyMatch(m -> m > 1)) return;
		Identifier group = market.regularGoods().stream().flatMap(g -> GoodsGroups.groupsOf(g).stream()).findFirst()
				.orElseThrow(() -> new AssertionError("no goods group in the market"));
		market.trends().put(group, 1.2);
	}

	static String multiplier(double m) {
		return String.format(Locale.ROOT, "×%.2f", m);
	}

	/** The tooltip of the trends: the title, one line per trend in red (popular) or green (plentiful). */
	static void checkTrendTooltip(List<TextRecorder.Text> tip, MarketSnapshotS2C snapshot) {
		String title = text("screen.ak_trade_expansion.trend.title");
		check(tip.stream().anyMatch(t -> t.text().equals(title)), "the trends tooltip has no \"%s\": %s", title, strings(tip));
		for (MarketSnapshotS2C.TrendView trend : snapshot.trends()) {
			boolean popular = trend.multiplier() >= 1;
			String line = text("screen.ak_trade_expansion.trend." + (popular ? "popular" : "unpopular"), GoodsGroups.name(trend.group()),
					multiplier(trend.multiplier()));
			TextRecorder.Text shown = tip.stream().filter(t -> t.text().equals(line)).findFirst()
					.orElseThrow(() -> new AssertionError("the trends tooltip has no \"" + line + "\": " + strings(tip)));
			check(shown.styleColor() == (popular ? RGB_RED : RGB_GREEN), "\"%s\" is not %s (color %06X)", line, popular ? "red" : "green", shown.styleColor());
		}
	}

	static List<TextRecorder.Text> hover(ClientGameTestContext context, TextRecorder.Text t) {
		moveTo(context, (t.x0() + t.x1()) / 2.0, (t.y0() + t.y1()) / 2.0);
		context.waitTicks(2);
		return tip(context);
	}

	/** The tooltip shown now; every line of it inside the screen (long lines are wrapped, none runs off an edge). */
	static List<TextRecorder.Text> tip(ClientGameTestContext context) {
		List<TextRecorder.Text> tip = tooltip(TextRecorder.capture(context));
		int[] size = context.computeOnClient(c -> new int[] {c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight()});
		List<TextRecorder.Text> outside = tip.stream().filter(t -> t.x0() < 0 || t.y0() < 0 || t.x1() > size[0] || t.y1() > size[1]).toList();
		check(outside.isEmpty(), "tooltip lines run off the %dx%d screen: %s", size[0], size[1], outside);
		return tip;
	}

	// ---- 16-1 / 24-1 the title row

	/**
	 * 16-1 / 24-1: "市場名 │ 評判：よそ者 │ 流行" in the title row, the right end left free; the mouse over the reputation
	 * shows its details, over the trends this village's trends in red / green. With a very long market name the name
	 * shrinks and both still show.
	 */
	static void titleRow(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		ensureTrend(s);
		MarketScreen screen = reopen(s);
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		check(snapshot.reputation().enabled(), "the reputation is off");
		check(!snapshot.trends().isEmpty(), "the snapshot has no trends");
		String rep = text("screen.ak_trade_expansion.reputation.label", reputationName(snapshot.reputation().level()));
		String trend = text("screen.ak_trade_expansion.trend.label");
		mouseAway(context);
		int[] box = context.computeOnClient(c -> new int[] {leftPos(screen), topPos(screen), imageWidth(screen)});
		int free = box[0] + box[2] - 8 - TITLE_RIGHT_RESERVE;
		List<TextRecorder.Text> row = band(TextRecorder.capture(context), box[1] + 2, box[1] + 14);
		TextRecorder.Text repText = find(row, rep, "reputation");
		TextRecorder.Text trendText = find(row, trend, "trends");
		TextRecorder.Text title = row.stream().min(Comparator.comparingInt(TextRecorder.Text::x0)).orElseThrow();
		check(title != repText && title.x1() <= repText.x0(), "the reputation %s is not right of the title %s", repText, title);
		check(repText.x1() <= trendText.x0() && trendText.x0() - repText.x1() <= 20, "the trends %s do not follow the reputation %s", trendText, repText);
		check(trendText.x1() <= free + 1, "the trends %s run into the free right end (from x %d)", trendText, free);
		screenshot(context, result, "24-1_title_row");

		List<TextRecorder.Text> repTip = hover(context, repText);
		MarketSnapshotS2C.ReputationView r = snapshot.reputation();
		String repTitle = text("screen.ak_trade_expansion.reputation.title", reputationName(r.level()), r.level());
		String points = r.next() < 0 ? prefix("screen.ak_trade_expansion.reputation.points_max") : prefix("screen.ak_trade_expansion.reputation.points");
		check(repTip.stream().anyMatch(t -> t.text().equals(repTitle)), "the reputation tooltip has no \"%s\": %s", repTitle, strings(repTip));
		check(repTip.stream().anyMatch(t -> t.text().startsWith(points)), "the reputation tooltip has no points \"%s…\": %s", points, strings(repTip));
		String how = prefix("screen.ak_trade_expansion.reputation.how").substring(0, 6);
		check(repTip.stream().anyMatch(t -> t.text().startsWith(how)), "the reputation tooltip does not say how it goes up: %s", strings(repTip));
		screenshot(context, result, "16-1_reputation_tooltip");
		List<TextRecorder.Text> trendTip = hover(context, trendText);
		checkTrendTooltip(trendTip, snapshot);
		screenshot(context, result, "24-1_trends_tooltip");
		mouseAway(context);

		// A very long name: the name shrinks, the reputation and the trends stay whole.
		BlockPos stall = s.stall;
		String old = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			String name = market.name();
			market.setName("とても長い名前の市場でタイトルの行には入りきらない名前の例");
			MarketRegistry.get(server).setDirty();
			return name;
		});
		try {
			MarketScreen named = reopen(s);
			mouseAway(context);
			List<TextRecorder.Text> longRow = band(TextRecorder.capture(context), box[1] + 2, box[1] + 14);
			TextRecorder.Text rep2 = find(longRow, rep, "reputation with a long name");
			TextRecorder.Text trend2 = find(longRow, trend, "trends with a long name");
			TextRecorder.Text title2 = longRow.stream().min(Comparator.comparingInt(TextRecorder.Text::x0)).orElseThrow();
			check(title2.x1() <= rep2.x0(), "the long title %s runs into the reputation %s", title2, rep2);
			check(trend2.x1() <= free + 1, "with a long title the trends %s run into the free right end", trend2);
			check(context.computeOnClient(c -> named.getTitle().getString()).contains("とても長い名前"), "the screen does not have the new name");
			screenshot(context, result, "16-1_long_name");
		} finally {
			s.world.getServer().runOnServer(server -> market(server, stall).setName(old));
			context.setScreen(() -> null);
		}
	}

	// ---- 24-1b client.trendPosition

	/**
	 * 24-1b: {@code client.trendPosition} TITLE_RIGHT puts "│ 流行" at the right end of the title row (the reserve kept
	 * free), STATUS_ROW at the right end of the status row below the list, the status text cut before it. The tooltip
	 * comes with both.
	 */
	static void trendPosition(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		ensureTrend(s);
		TradeExpansionConfig.TrendPosition before = TradeExpansionConfig.get().client.trendPosition;
		String trend = text("screen.ak_trade_expansion.trend.label");
		try {
			MarketScreen screen = reopen(s);
			MarketSnapshotS2C snapshot = snapshot(context, screen);
			int[] box = context.computeOnClient(c -> new int[] {leftPos(screen), topPos(screen), imageWidth(screen), (Integer) get(screen, "rows")});
			client(context, c -> c.trendPosition = TradeExpansionConfig.TrendPosition.TITLE_RIGHT);
			mouseAway(context);
			List<TextRecorder.Text> row = band(TextRecorder.capture(context), box[1] + 2, box[1] + 14);
			TextRecorder.Text right = find(row, trend, "trends at the right end");
			int end = box[0] + box[2] - 8 - TITLE_RIGHT_RESERVE;
			check(Math.abs(right.x1() - end) <= 1, "TITLE_RIGHT: the trends end at x %d, not at the right end of the title row (%d)", right.x1(), end);
			check(row.stream().filter(t -> t != right).allMatch(t -> t.x1() <= right.x0()), "TITLE_RIGHT: a text of the title row runs into the trends: %s", strings(row));
			checkTrendTooltip(hover(context, right), snapshot);
			screenshot(context, result, "24-1b_title_right");

			client(context, c -> c.trendPosition = TradeExpansionConfig.TrendPosition.STATUS_ROW);
			// A long status line: it is cut before the trends.
			context.runOnClient(c -> call(screen, "setStatus",
					Component.literal("取引の結果などを出す状態表示の行です。長い文は右端の「流行」の手前で切られます。".repeat(2)), true));
			mouseAway(context);
			int statusY = box[1] + 36 + box[3] * constant(MarketScreen.class, "ROW_H") + 3;
			List<TextRecorder.Text> all = TextRecorder.capture(context);
			check(band(all, box[1] + 2, box[1] + 14).stream().noneMatch(t -> t.text().equals(trend)), "STATUS_ROW: the trends are still in the title row");
			List<TextRecorder.Text> status = band(all, statusY - 2, statusY + 3);
			TextRecorder.Text bottom = find(status, trend, "trends in the status row");
			check(Math.abs(bottom.x1() - (box[0] + box[2] - 8)) <= 1, "STATUS_ROW: the trends end at x %d, not at the right end (%d)", bottom.x1(), box[0] + box[2] - 8);
			TextRecorder.Text line = status.stream().filter(t -> t != bottom).findFirst()
					.orElseThrow(() -> new AssertionError("STATUS_ROW: no status text left of the trends: " + strings(status)));
			check(line.x1() <= bottom.x0(), "STATUS_ROW: the status %s runs into the trends %s", line, bottom);
			List<String> problems = context.computeOnClient(c -> ClientChecklistTests.layoutProblems(c, "24-1b status row", all, null));
			check(problems.isEmpty(), "STATUS_ROW: %s", problems);
			checkTrendTooltip(hover(context, bottom), snapshot);
			screenshot(context, result, "24-1b_status_row");
		} finally {
			client(context, c -> c.trendPosition = before);
			context.setScreen(() -> null);
		}
	}

	// ---- 16-11 the debug tooltip

	/**
	 * 16-11: with {@code client.showDebugTooltip} the tooltip of a goods row adds the base price, the multiplier, the
	 * prices, the stock and how long the price and the stock take to recover (a good made dear with half its stock);
	 * without it they are not there. The price graph is left of the cursor (above or below the tooltip when there is no
	 * room on the left), inside the screen, on no tooltip line.
	 */
	static void debugTooltip(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		boolean before = TradeExpansionConfig.get().client.showDebugTooltip;
		try {
			client(context, c -> c.showDebugTooltip = true);
			MarketScreen probe = reopen(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
			context.waitTicks(2);
			GoodView picked = visibleGoods(context, probe).stream().filter(v -> PriceModel.canBuy(v.state()) && v.patch().isEmpty() && v.params().targetStock() >= 8)
					.findFirst().orElseThrow(() -> new AssertionError("no good to buy"));
			// Dearer than normal and half the stock: the tooltip forecasts both recoveries.
			BlockPos stall = s.stall;
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				GoodState st = market.good(picked.goods());
				st.setPressure(market.params(picked.goods()).minPressure() * 0.5);
				st.setStock(Math.floor(market.params(picked.goods()).targetStock() / 2));
				MarketRegistry.get(server).setDirty();
			});
			MarketScreen screen = reopen(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
			context.waitTicks(2);
			GoodView good = view(context, screen, picked.goods());
			int rowY = showRow(context, screen, good.goods());
			int mouseX = listX(context, screen) + 60;
			moveTo(context, mouseX, rowY + 8);
			context.waitTicks(2);
			List<TextRecorder.Text> frame = TextRecorder.capture(context);
			// The price graph (120 x 64, its title 4 / 3 pixels in) is drawn over the screen too: its texts (the title,
			// "112%", "10c - 12c", "3日") are told apart from the tooltip's by what they say.
			String graphTitle = text("screen.ak_trade_expansion.graph.buy");
			String[] days = Language.getInstance().getOrDefault("screen.ak_trade_expansion.graph.days").split("%s", -1);
			TextRecorder.Text graph = frame.stream().filter(t -> t.text().equals(graphTitle)).findFirst()
					.orElseThrow(() -> new AssertionError("no price graph (\"" + graphTitle + "\") next to the cursor"));
			int[] gbox = {graph.x0() - 4, graph.y0() - 3, graph.x0() - 4 + 120, graph.y0() - 3 + 64};
			java.util.function.Predicate<TextRecorder.Text> graphText = t -> t.text().equals(graphTitle) || t.text().matches("\\d+%")
					|| t.text().matches(".+c - .+c") || t.text().matches(java.util.regex.Pattern.quote(days[0]) + "\\d+" + java.util.regex.Pattern.quote(days[1]));
			List<TextRecorder.Text> tip = tooltip(frame).stream().filter(graphText.negate()).toList();
			int[] size = context.computeOnClient(c -> new int[] {c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight()});
			check(tip.stream().allMatch(t -> t.x0() >= 0 && t.y0() >= 0 && t.x1() <= size[0] && t.y1() <= size[1]), "tooltip lines run off the screen: %s", tip);
			String base = text("screen.ak_trade_expansion.debug.base", CoinItem.formatCopper(Math.round(good.params().basePrice())));
			check(tip.stream().anyMatch(t -> t.text().equals(base)), "the tooltip has no \"%s\": %s", base, strings(tip));
			for (String key : List.of("debug.prices", "debug.stock", "debug.multiplier.high", "debug.price_recovery", "debug.restock")) {
				String p = prefix("screen.ak_trade_expansion." + key);
				check(tip.stream().anyMatch(t -> t.text().startsWith(p)), "the tooltip has no \"%s…\": %s", p, strings(tip));
			}
			GoodState state = context.computeOnClient(c -> (GoodState) call(screen, "currentState", view(context, screen, good.goods())));
			String stock = String.valueOf((long) Math.floor(state.stock()));
			check(tip.stream().anyMatch(t -> t.text().startsWith(prefix("screen.ak_trade_expansion.debug.stock")) && t.text().contains(stock)),
					"the stock line does not say %s: %s", stock, strings(tip));
			String v = String.format("%.2f", PriceModel.multiplier(good.params(), state.pressure()));
			check(tip.stream().anyMatch(t -> t.text().equals(text("screen.ak_trade_expansion.debug.multiplier.high", v))),
					"the multiplier line does not say ×%s: %s", v, strings(tip));
			// The graph: left of the cursor (the tooltip is on the right) when there is room for it, otherwise above or
			// below the tooltip; inside the screen, on none of the tooltip's lines.
			int tipTop = tip.stream().mapToInt(TextRecorder.Text::y0).min().orElse(0);
			int tipBottom = tip.stream().mapToInt(TextRecorder.Text::y1).max().orElse(0);
			if (mouseX - 10 - 120 >= 4) {
				check(gbox[2] <= mouseX, "the price graph (x %d-%d) is not left of the cursor (x %d)", gbox[0], gbox[2], mouseX);
			} else {
				check(gbox[1] >= tipBottom || gbox[3] <= tipTop, "with no room on the left, the price graph (y %d-%d) is not above or below the tooltip (y %d-%d)",
						gbox[1], gbox[3], tipTop, tipBottom);
			}
			check(gbox[0] >= 0 && gbox[3] <= size[1], "the price graph (%d,%d-%d,%d) runs off the %dx%d screen", gbox[0], gbox[1], gbox[2], gbox[3], size[0], size[1]);
			List<TextRecorder.Text> covered = tip.stream().filter(t -> t.inside(gbox[0], gbox[1], gbox[2], gbox[3])).toList();
			check(covered.isEmpty(), "the price graph covers tooltip lines: %s", covered);
			screenshot(context, result, "16-11_debug_tooltip");

			client(context, c -> c.showDebugTooltip = false);
			moveTo(context, listX(context, screen) + 61, rowY + 8);
			context.waitTicks(2);
			List<TextRecorder.Text> plain = tip(context);
			check(plain.stream().noneMatch(t -> t.text().equals(base)), "the base price is still in the tooltip with the setting off: %s", strings(plain));
		} finally {
			client(context, c -> c.showDebugTooltip = before);
			mouseAway(context);
			context.setScreen(() -> null);
		}
	}

	// ---- 9-1 / 23-3 / 23-4 / 24-4 the board

	/** The market of the shared world other than the stall's (the case placement_hint founds one 400 blocks east). */
	static Market other(MinecraftServer server, Market home) {
		return MarketRegistry.get(server).all().stream().filter(m -> m.id() != home.id() && m.dimension().equals(home.dimension()))
				.min(Comparator.comparingDouble(m -> m.center().distSqr(home.center())))
				.orElseThrow(() -> new AssertionError("no other market (the case placement_hint founds one)"));
	}

	static BoardS2C board(ClientGameTestContext context, MarketScreen screen) {
		check(waitClient(context, c -> get(screen, "board") != null, 60), "the board did not arrive");
		return context.computeOnClient(c -> (BoardS2C) get(screen, "board"));
	}

	/**
	 * 23-3 / 23-4 / 24-4 / 9-1: a rival campaign centered on the other market. The board lists its news first (the
	 * good, the guild, where and the days left; the tooltip says what happens and that a click opens the market), the
	 * market row shows where it is and its trends on hover, a click on the news opens the center market's goods with the
	 * header "商品／買値／売値" in line with the columns, and the list scrolls to its last good.
	 */
	static void board(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		long[] ids = s.world.getServer().computeOnServer(server -> {
			Market home = market(server, stall);
			Market center = other(server, home);
			ensureTrend(center);
			MarketRegistry registry = MarketRegistry.get(server);
			Campaign campaign = RivalService.startNow(server.overworld(), registry, center, MarketUpkeep.touch(server.overworld(), center));
			check(campaign != null, "no campaign could start at the other market");
			return new long[] {center.id(), campaign.id()};
		});
		try {
			MarketScreen screen = reopen(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.board");
			context.waitTicks(2);
			BoardS2C board = board(context, screen);
			BoardS2C.News news = board.news().stream().filter(n -> n.marketId() == ids[0]).findFirst()
					.orElseThrow(() -> new AssertionError("the board has no news of the campaign: " + board.news()));
			int index = board.news().indexOf(news);
			int listY = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
			int rowH = constant(MarketScreen.class, "ROW_H");
			int x0 = listX(context, screen);
			context.runOnClient(c -> set(screen, "scroll", 0));
			mouseAway(context);
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			List<TextRecorder.Text> newsRow = rowTexts(texts, listY + index * rowH);
			String item = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(news.item())).getHoverName().getString();
			String headline = text(news.buying() ? "screen.ak_trade_expansion.board.news.buying" : "screen.ak_trade_expansion.board.news.selling",
					RivalGuilds.name(news.guild()), item);
			String days = news.daysLeft() <= 0 ? text("screen.ak_trade_expansion.rival.today") : text("screen.ak_trade_expansion.rival.days_left", news.daysLeft());
			check(newsRow.stream().anyMatch(t -> t.text().equals(headline) || headline.startsWith(t.text().replace("…", ""))),
					"the news row does not say \"%s\": %s", headline, strings(newsRow));
			check(newsRow.stream().anyMatch(t -> t.text().endsWith("m " + days)), "the news row has no \"… 340m %s\": %s", days, strings(newsRow));
			moveTo(context, x0 + 60, listY + index * rowH + 8);
			context.waitTicks(2);
			List<TextRecorder.Text> tip = tip(context);
			String about = prefix(news.buying() ? "screen.ak_trade_expansion.board.news.tooltip.buying" : "screen.ak_trade_expansion.board.news.tooltip.selling");
			check(tip.stream().anyMatch(t -> t.text().startsWith(about.isEmpty() ? RivalGuilds.name(news.guild()).getString() : about)),
					"the news tooltip does not explain the campaign: %s", strings(tip));
			String open = text("screen.ak_trade_expansion.board.news.open");
			check(tip.stream().anyMatch(t -> t.text().equals(open)), "the news tooltip has no \"%s\": %s", open, strings(tip));
			screenshot(context, result, "23-3_board_news");

			// 24-4 / 9-1: the market row: its name, where it is, and its trends on hover.
			BoardS2C.Entry entry = board.markets().stream().filter(e -> e.id() == ids[0]).findFirst()
					.orElseThrow(() -> new AssertionError("the other market is not on the board"));
			int marketIndex = board.news().size() + board.markets().indexOf(entry);
			int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
			int scroll = Math.max(0, marketIndex - rows + 1);
			context.runOnClient(c -> set(screen, "scroll", scroll));
			int marketY = listY + (marketIndex - scroll) * rowH;
			mouseAway(context);
			List<TextRecorder.Text> marketRow = rowTexts(TextRecorder.capture(context), marketY);
			check(marketRow.stream().anyMatch(t -> t.text().matches(".+ [0-9,]+m")), "the market row does not say where it is (\"北東 340m\"): %s", strings(marketRow));
			String name = entry.title().getString();
			check(marketRow.stream().anyMatch(t -> name.startsWith(t.text()) && t.text().length() >= Math.min(3, name.length())),
					"the market row does not show \"%s\": %s", name, strings(marketRow));
			moveTo(context, x0 + 40, marketY + 8);
			context.waitTicks(2);
			List<TextRecorder.Text> marketTip = tip(context);
			check(!entry.trends().isEmpty(), "the board sends no trends of the other market");
			for (MarketSnapshotS2C.TrendView t : entry.trends()) {
				String line = text("screen.ak_trade_expansion.trend." + (t.multiplier() >= 1 ? "popular" : "unpopular"), GoodsGroups.name(t.group()), multiplier(t.multiplier()));
				check(marketTip.stream().anyMatch(x -> x.text().equals(line)), "the market's tooltip has no \"%s\": %s", line, strings(marketTip));
			}
			screenshot(context, result, "24-4_board_market_trends");

			// 23-4: a click on the news opens the center market.
			context.runOnClient(c -> set(screen, "scroll", 0));
			clickAt(context, x0 + 60, listY + index * rowH + 8);
			mouseAway(context);
			long opened = context.computeOnClient(c -> (Long) get(screen, "boardMarket"));
			check(opened == ids[0], "a click on the news opened market %d, not the center %d", opened, ids[0]);
			screenshot(context, result, "23-4_news_opens_market");

			boardGoods(context, screen, entry, result, "9-1_board_market_scrolled");
		} finally {
			s.world.getServer().runOnServer(server -> {
				MarketRegistry registry = MarketRegistry.get(server);
				registry.campaigns().removeIf(c -> c.id() == ids[1]);
				registry.setDirty();
			});
			context.setScreen(() -> null);
		}
	}

	/**
	 * 9-1: the goods of the market selected on the board: the header "商品／買値／売値" in line with the price columns,
	 * the mouse over it explains both prices, and the list scrolls with the wheel to its last good.
	 */
	static void boardGoods(ClientGameTestContext context, MarketScreen screen, BoardS2C.Entry entry, CaseResult.Builder result, String shot) {
		int listY = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
		int rowH = constant(MarketScreen.class, "ROW_H");
		int x0 = listX(context, screen);
		int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
		mouseAway(context);
		List<TextRecorder.Text> goodsTexts = TextRecorder.capture(context);
		int headerY = listY + 1;
		List<TextRecorder.Text> header = area(goodsTexts, headerY - 1, headerY + 3);
		TextRecorder.Text buyHeader = find(header, text("screen.ak_trade_expansion.board.header.buy"), "buy price header");
		TextRecorder.Text sellHeader = find(header, text("screen.ak_trade_expansion.board.header.sell"), "sell price header");
		find(header, text("screen.ak_trade_expansion.board.header.goods"), "goods header");
		int headerH = 10;
		List<TextRecorder.Text> firstRow = area(goodsTexts, listY + headerH + 3, listY + headerH + rowH);
		BoardS2C.Good firstGood = entry.goods().getFirst();
		if (firstGood.buy() >= 0) {
			String price = (firstGood.buyDeal() > 0 ? "↓" : "") + CoinItem.formatCopper(firstGood.buy());
			TextRecorder.Text p = find(firstRow, price, "buy price of the first good");
			check(Math.abs(p.x1() - buyHeader.x1()) <= COLUMN_SLACK, "the buy price %s is not under its header %s", p, buyHeader);
		}
		if (firstGood.sell() >= 0) {
			String price = (firstGood.sellDeal() > 0 ? "↑" : "") + CoinItem.formatCopper(firstGood.sell());
			TextRecorder.Text p = find(firstRow, price, "sell price of the first good");
			check(Math.abs(p.x1() - sellHeader.x1()) <= COLUMN_SLACK, "the sell price %s is not under its header %s", p, sellHeader);
		}
		List<TextRecorder.Text> headerTip = hover(context, buyHeader);
		String buyTip = text("screen.ak_trade_expansion.board.header.buy.tooltip");
		check(headerTip.stream().anyMatch(t -> t.text().equals(buyTip)), "the header tooltip has no \"%s\": %s", buyTip, strings(headerTip));
		String sellTip = text("screen.ak_trade_expansion.board.header.sell.tooltip");
		check(headerTip.stream().anyMatch(t -> t.text().equals(sellTip)), "the header tooltip has no \"%s\": %s", sellTip, strings(headerTip));
		// Scrolled to the end with the wheel, the last good shows.
		moveTo(context, x0 + 40, listY + 30);
		for (int i = 0; i < entry.goods().size() + 2; i++) {
			context.getInput().scroll(-1);
			context.waitTick();
		}
		mouseAway(context);
		BoardS2C.Good last = entry.goods().getLast();
		String lastPrice = last.sell() >= 0 ? (last.sellDeal() > 0 ? "↑" : "") + CoinItem.formatCopper(last.sell())
				: (last.buyDeal() > 0 ? "↓" : "") + CoinItem.formatCopper(last.buy());
		int lastRowY = listY + headerH + ((rows * rowH - headerH) / rowH - 1) * rowH;
		List<TextRecorder.Text> lastRow = area(TextRecorder.capture(context), lastRowY + 3, lastRowY + rowH);
		check(lastRow.stream().anyMatch(t -> t.text().equals(lastPrice)), "scrolled to the end, the last row is not the last good (%s): %s", lastPrice, strings(lastRow));
		screenshot(context, result, shot);
	}

	// ---- 9-1b deals on the board

	private static final int COLOR_DEAL_BUY = 0xFF55FFFF;
	private static final int COLOR_DEAL_SELL = 0xFFFF88FF;

	/**
	 * 9-1b: one good of the other market made cheap (to buy) and one made dear (to sell) against the stall's market.
	 * The market row shows "安n" in cyan and "高n" in pink (n: the deals the board sent); the mouse over it shows the best
	 * deals with their % in the right panel. With the market selected, the cheap good's buy price is cyan with "↓" and
	 * the dear good's sell price pink with "↑"; the mouse over them says how much. No texts overlap.
	 */
	static void boardDeals(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		// {other market id, cheap good, dear good} and the pressures to put back.
		List<Runnable> restore = new ArrayList<>();
		Object[] picked = s.world.getServer().computeOnServer(server -> {
			Market home = market(server, stall);
			Market other = other(server, home);
			MarketUpkeep.touch(server.overworld(), other);
			Identifier cheap = other.goods().keySet().stream().filter(g -> {
				var def = com.akf.tradeexpansion.data.GoodsCatalog.get(g);
				return def != null && def.buy() && def.components().isEmpty() && other.good(g).stock() >= 1;
			}).findFirst().orElseThrow(() -> new AssertionError("the other market sells nothing"));
			Identifier dear = other.goods().keySet().stream().filter(g -> {
				var def = com.akf.tradeexpansion.data.GoodsCatalog.get(g);
				return def != null && def.sell() && def.components().isEmpty() && !g.equals(cheap) && PriceModel.canSell(other.params(g), other.good(g));
			}).findFirst().orElseThrow(() -> new AssertionError("the other market buys nothing"));
			for (Market m : List.of(home, other)) {
				for (Identifier g : List.of(cheap, dear)) {
					GoodState st = m.good(g);
					if (st == null) continue;
					double x = st.pressure();
					restore.add(() -> st.setPressure(x));
				}
			}
			for (Identifier g : List.of(cheap, dear)) if (home.good(g) != null) home.good(g).setPressure(0);
			other.good(cheap).setPressure(other.params(cheap).maxPressure() * 0.8);
			other.good(dear).setPressure(other.params(dear).minPressure() * 0.8);
			MarketRegistry.get(server).setDirty();
			return new Object[] {other.id(), cheap, dear};
		});
		long otherId = (Long) picked[0];
		Item cheapItem = item((Identifier) picked[1]);
		Item dearItem = item((Identifier) picked[2]);
		try {
			MarketScreen screen = reopen(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.board");
			context.waitTicks(2);
			BoardS2C board = board(context, screen);
			BoardS2C.Entry entry = board.markets().stream().filter(e -> e.id() == otherId).findFirst()
					.orElseThrow(() -> new AssertionError("the other market is not on the board"));
			BoardS2C.Good cheapGood = entry.goods().stream().filter(g -> BuiltInRegistries.ITEM.getValue(g.item()) == cheapItem).findFirst()
					.orElseThrow(() -> new AssertionError("the board has no " + cheapItem + " of the other market"));
			BoardS2C.Good dearGood = entry.goods().stream().filter(g -> BuiltInRegistries.ITEM.getValue(g.item()) == dearItem).findFirst()
					.orElseThrow(() -> new AssertionError("the board has no " + dearItem + " of the other market"));
			check(cheapGood.buyDeal() > 0, "the cheap good %s is no deal to buy: %s", cheapItem, cheapGood);
			check(dearGood.sellDeal() > 0, "the dear good %s is no deal to sell: %s", dearItem, dearGood);
			long buyDeals = entry.goods().stream().filter(g -> g.buyDeal() > 0).count();
			long sellDeals = entry.goods().stream().filter(g -> g.sellDeal() > 0).count();
			int bestBuy = entry.goods().stream().mapToInt(BoardS2C.Good::buyDeal).max().orElseThrow();
			int bestSell = entry.goods().stream().mapToInt(BoardS2C.Good::sellDeal).max().orElseThrow();

			// The market row: "安n" in cyan, "高n" in pink.
			int listY = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
			int rowH = constant(MarketScreen.class, "ROW_H");
			int x0 = listX(context, screen);
			int index = board.news().size() + board.markets().indexOf(entry);
			int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
			int scroll = Math.max(0, index - rows + 1);
			context.runOnClient(c -> set(screen, "scroll", scroll));
			int rowY = listY + (index - scroll) * rowH;
			mouseAway(context);
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			List<TextRecorder.Text> row = rowTexts(texts, rowY);
			String buyCount = text("screen.ak_trade_expansion.board.deal_buy_count", buyDeals);
			String sellCount = text("screen.ak_trade_expansion.board.deal_sell_count", sellDeals);
			TextRecorder.Text buyText = find(row, buyCount, "deals to buy");
			TextRecorder.Text sellText = find(row, sellCount, "deals to sell");
			check(buyText.color() == COLOR_DEAL_BUY, "\"%s\" is not cyan (%08X)", buyCount, buyText.color());
			check(sellText.color() == COLOR_DEAL_SELL, "\"%s\" is not pink (%08X)", sellCount, sellText.color());
			layoutOk(context, "9-1b board", texts);

			// The mouse over the row: the best deals in the right panel.
			moveTo(context, x0 + 40, rowY + 8);
			context.waitTicks(2);
			int px = context.computeOnClient(c -> leftPos(screen) + (Integer) get(screen, "panelX"));
			List<TextRecorder.Text> hovered = TextRecorder.capture(context);
			List<TextRecorder.Text> panel = hovered.stream().filter(t -> t.x0() >= px).toList();
			find(panel, text("screen.ak_trade_expansion.board.deal_buy_title"), "title of the deals to buy");
			find(panel, text("screen.ak_trade_expansion.board.deal_sell_title"), "title of the deals to sell");
			find(panel, "-" + bestBuy + "%", "the best deal to buy");
			find(panel, "+" + bestSell + "%", "the best deal to sell");
			layoutOk(context, "9-1b board hovered", hovered);
			screenshot(context, result, "9-1b_board_deals");

			// Select the market: the prices of the deals with their arrows and colors, the % on hover.
			clickAt(context, x0 + 40, rowY + 8);
			mouseAway(context);
			check(context.computeOnClient(c -> (Long) get(screen, "boardMarket")) == otherId, "a click on the market row did not select it");
			int headerH = 10;
			int goodsRows = (rows * rowH - headerH) / rowH;
			for (BoardS2C.Good good : List.of(cheapGood, dearGood)) {
				boolean buy = good == cheapGood;
				int i = entry.goods().indexOf(good);
				int top = Math.max(0, Math.min(i, entry.goods().size() - goodsRows));
				context.runOnClient(c -> set(screen, "scroll", top));
				context.waitTicks(1);
				int gy = listY + headerH + (i - top) * rowH;
				mouseAway(context);
				List<TextRecorder.Text> goods = TextRecorder.capture(context);
				String price = buy ? "↓" + CoinItem.formatCopper(good.buy()) : "↑" + CoinItem.formatCopper(good.sell());
				TextRecorder.Text p = find(area(goods, gy + 3, gy + rowH), price, buy ? "cheap buy price" : "dear sell price");
				check(p.color() == (buy ? COLOR_DEAL_BUY : COLOR_DEAL_SELL), "\"%s\" is not %s (%08X)", price, buy ? "cyan" : "pink", p.color());
				layoutOk(context, "9-1b board market", goods);
				moveTo(context, x0 + 40, gy + 8);
				context.waitTicks(2);
				List<TextRecorder.Text> tip = tip(context);
				String line = buy ? text("screen.ak_trade_expansion.board.deal_buy", good.buyDeal()) : text("screen.ak_trade_expansion.board.deal_sell", good.sellDeal());
				check(tip.stream().anyMatch(t -> t.text().equals(line)), "the tooltip of %s has no \"%s\": %s", buy ? cheapItem : dearItem, line, strings(tip));
				screenshot(context, result, buy ? "9-1b_board_cheap" : "9-1b_board_dear");
			}
		} finally {
			s.world.getServer().runOnServer(server -> {
				restore.forEach(Runnable::run);
				MarketRegistry.get(server).setDirty();
			});
			mouseAway(context);
			context.setScreen(() -> null);
		}
	}

	/** No texts on top of each other, none outside the screen or the panel. */
	private static void layoutOk(ClientGameTestContext context, String where, List<TextRecorder.Text> texts) {
		List<String> problems = context.computeOnClient(c -> ClientChecklistTests.layoutProblems(c, where, texts, null));
		check(problems.isEmpty(), "%s", problems);
	}

	// ---- 18-1 / 18-2 / 31-2 sell all

	private static int freeSlot(ServerPlayer player, int from, int to) {
		Inventory inventory = player.getInventory();
		for (int i = from; i < to; i++) if (inventory.getItem(i).isEmpty()) return i;
		List<String> held = new java.util.ArrayList<>();
		for (int i = from; i < to; i++) held.add(inventory.getItem(i).getCount() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inventory.getItem(i).getItem()));
		throw new AssertionError("no free inventory slot between " + from + " and " + to + ": " + held);
	}

	@SuppressWarnings("unchecked")
	private static List<Object> sellAllLines(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(c -> new ArrayList<>((List<Object>) get(screen, "sellAllLines")));
	}

	private static Object line(List<Object> lines, Identifier good) {
		return lines.stream().filter(l -> ((GoodView) field(l, "view")).goods().equals(good)).findFirst().orElse(null);
	}

	/**
	 * 18-1 / 18-2 / 31-2: "sell all" lists the goods held outside the hotbar (a good only in the hotbar is not there),
	 * cheapest first; a tool or armour the market buys is excluded from the start; a click on a line excludes it (the
	 * total goes down) and the choice is still there after the screen is opened again.
	 */
	static void sellAll(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		MarketScreen first = reopen(s);
		MarketSnapshotS2C snapshot = snapshot(context, first);
		List<GoodView> plain = snapshot.goods().stream()
				.filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty() && !v.displayStack().isDamageableItem()
						&& v.displayStack().getMaxStackSize() >= 4 && PriceModel.sellCapacity(v.params(), v.state()) >= 4
						&& context.computeOnClient(c -> c.player.getInventory().countItem(itemOf(v))) == 0)
				.toList();
		check(plain.size() >= 2, "fewer than 2 goods the market buys that the player has none of");
		GoodView kept = plain.get(0);
		GoodView hotbarOnly = plain.get(1);
		GoodView tool = snapshot.goods().stream().filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty() && v.displayStack().isDamageableItem()
				&& context.computeOnClient(c -> c.player.getInventory().countItem(itemOf(v))) == 0).findFirst().orElse(null);
		context.setScreen(() -> null);
		List<String> exclude0 = List.copyOf(TradeExpansionConfig.get().client.sellAllExclude);
		List<String> include0 = List.copyOf(TradeExpansionConfig.get().client.sellAllInclude);
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().setItem(freeSlot(player, 9, 36), new ItemStack(itemOf(kept), 4));
			player.getInventory().setItem(freeSlot(player, 0, 8), new ItemStack(itemOf(hotbarOnly), 4));
			if (tool != null) player.getInventory().setItem(freeSlot(player, 9, 36), new ItemStack(itemOf(tool)));
		});
		context.waitTicks(3);
		try {
			MarketScreen screen = ClientChecklistTests.openStall(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
			context.clickScreenButton("screen.ak_trade_expansion.sell_all");
			context.waitTicks(2);
			check(context.computeOnClient(c -> (Boolean) get(screen, "sellAllOpen")), "the \"sell all\" box did not open");
			List<Object> lines = sellAllLines(context, screen);
			check(line(lines, kept.goods()) != null, "%s held outside the hotbar is not in \"sell all\"", kept.goods());
			check(line(lines, hotbarOnly.goods()) == null, "%s held only in the hotbar is in \"sell all\"", hotbarOnly.goods());
			List<Double> prices = lines.stream().map(l -> ((GoodView) field(l, "view")).params().basePrice()).toList();
			for (int i = 1; i < prices.size(); i++) {
				check(prices.get(i - 1) <= prices.get(i), "\"sell all\" is not cheapest first: %s", prices);
			}
			if (tool != null) {
				Object t = line(lines, tool.goods());
				check(t != null && (Boolean) field(t, "excluded"), "the tool %s is not excluded from the start", tool.goods());
			}
			mouseAway(context);
			String total0 = text("screen.ak_trade_expansion.sell_all.total", CoinItem.formatCopper((Long) context.computeOnClient(c -> call(screen, "sellAllTotal"))),
					lines.stream().filter(l -> !(Boolean) field(l, "excluded") && (Integer) field(l, "units") > 0).count());
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			check(texts.stream().anyMatch(t -> t.text().equals(total0)), "the box does not show \"%s\": %s", total0, strings(texts));
			screenshot(context, result, "18-1_sell_all");

			// Click the line of the kept good: it is excluded and the total goes down by its amount.
			long before = context.computeOnClient(c -> (Long) call(screen, "sellAllTotal"));
			long amount = (Long) field(line(lines, kept.goods()), "total");
			clickLine(context, screen, lines.indexOf(line(lines, kept.goods())));
			Object after = line(sellAllLines(context, screen), kept.goods());
			check((Boolean) field(after, "excluded"), "a click on the line of %s did not exclude it", kept.goods());
			long total = context.computeOnClient(c -> (Long) call(screen, "sellAllTotal"));
			check(total == before - amount, "excluding %s took the total %d -> %d (its line is %d)", kept.goods(), before, total, amount);
			screenshot(context, result, "18-2_excluded");

			// Opened again: still excluded.
			MarketScreen again = reopen(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
			context.clickScreenButton("screen.ak_trade_expansion.sell_all");
			context.waitTicks(2);
			List<Object> reopened = sellAllLines(context, again);
			check((Boolean) field(line(reopened, kept.goods()), "excluded"), "%s is no longer excluded after the screen was opened again", kept.goods());
			// And a second click includes it again.
			clickLine(context, again, reopened.indexOf(line(reopened, kept.goods())));
			check(!(Boolean) field(line(sellAllLines(context, again), kept.goods()), "excluded"), "a second click did not include %s again", kept.goods());
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(c -> {
				TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
				live.client.sellAllExclude = new ArrayList<>(exclude0);
				live.client.sellAllInclude = new ArrayList<>(include0);
				TradeExpansionConfig.set(live);
				TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
				file.client = live.client;
				ConfigManager.write(ConfigManager.path(), file);
			});
			s.world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				removeItems(player, kept);
				removeItems(player, hotbarOnly);
				if (tool != null) removeItems(player, tool);
			});
		}
	}

	private static void removeItems(ServerPlayer player, GoodView good) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(itemOf(good))) inventory.setItem(i, ItemStack.EMPTY);
	}

	/** Clicks a line of the "sell all" box (scrolling it into view first). */
	private static void clickLine(ClientGameTestContext context, MarketScreen screen, int index) {
		int rows = context.computeOnClient(c -> (Integer) get(screen, "sellAllRows"));
		int scroll = Math.max(0, index - rows + 1);
		context.runOnClient(c -> set(screen, "sellAllScroll", scroll));
		int[] at = context.computeOnClient(c -> new int[] {leftPos(screen) + constant(MarketScreen.class, "SELL_ALL_X"),
				topPos(screen) + constant(MarketScreen.class, "SELL_ALL_Y") + 16});
		clickAt(context, at[0] + 30, at[1] + (index - scroll) * constant(MarketScreen.class, "ROW_H") + 8);
		mouseAway(context);
	}

	// ---- 31-1 the order of the goods

	/**
	 * 31-1: the buy and sell tabs list the goods cheapest (base price) first, in the rows as drawn; buying one does not
	 * move the selected row.
	 */
	static void goodsOrder(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		s.world.getServer().runOnServer(server -> WalletService.give(player(server), 1_000_000));
		MarketScreen screen = reopen(s);
		for (String tab : List.of("buy", "sell")) {
			context.clickScreenButton("screen.ak_trade_expansion.tab." + tab);
			context.runOnClient(c -> set(screen, "scroll", 0));
			mouseAway(context);
			List<GoodView> goods = visibleGoods(context, screen);
			for (int i = 1; i < goods.size(); i++) {
				check(goods.get(i - 1).params().basePrice() <= goods.get(i).params().basePrice(), "the %s tab is not cheapest first at row %d: %s %.1f, %s %.1f",
						tab, i, goods.get(i - 1).goods(), goods.get(i - 1).params().basePrice(), goods.get(i).goods(), goods.get(i).params().basePrice());
			}
			int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
			int listY = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			for (int i = 0; i < Math.min(rows, goods.size()); i++) {
				String name = goods.get(i).displayStack().getHoverName().getString();
				List<TextRecorder.Text> row = rowTexts(texts, listY + i * constant(MarketScreen.class, "ROW_H"));
				check(row.stream().anyMatch(t -> {
					String shown = t.text().replace("★", "").replace("…", "");
					return !shown.isEmpty() && name.startsWith(shown);
				}), "row %d of the %s tab is not %s: %s", i, tab, name, strings(row));
			}
		}
		screenshot(context, result, "31-1_sell_order");
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		List<GoodView> goods = visibleGoods(context, screen);
		GoodView chosen = goods.stream().filter(v -> v.unit() == 1 && v.stock() >= 2 && v.patch().isEmpty()).skip(1).findFirst()
				.orElseThrow(() -> new AssertionError("no good to buy"));
		int index = goods.indexOf(chosen);
		buyWithButtons(s, screen, chosen.goods(), "1");
		List<GoodView> after = visibleGoods(context, screen);
		int now = after.stream().map(GoodView::goods).toList().indexOf(chosen.goods());
		check(now == index, "buying %s moved its row %d -> %d", chosen.goods(), index, now);
		check(chosen.goods().equals(context.computeOnClient(c -> get(screen, "selected"))), "buying %s changed the selected good", chosen.goods());
	}

	// ---- 22-20 / 22-21 / 28-1 / 29-1 / 31-3 / 34-1 / 34-2 the development tab

	/**
	 * The development tab: the points and the market size above, the 15 buildings cheapest first (with the fountain and
	 * the park), the level breakdown and each building's effect, size and materials on hover. A click (also after the
	 * list was scrolled with the wheel) selects the row's building: its name, size (with the full size on hover), cost,
	 * materials and buttons on the right; a second click goes back.
	 */
	static void townTab(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = reopen(s);
		check(context.computeOnClient(c -> ((MarketSnapshotS2C) get(screen, "snapshot")).development().enabled()), "the market has no development tab");
		context.clickScreenButton("screen.ak_trade_expansion.tab.town");
		context.waitTicks(3);
		mouseAway(context);
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		List<FacilityView> facilities = snapshot.development().facilities();
		Set<String> kinds = new TreeSet<>(facilities.stream().map(FacilityView::kind).toList());
		List<String> missing = FACILITIES.stream().filter(k -> !kinds.contains(k)).toList();
		check(missing.isEmpty(), "the development tab lacks %s (it has %s)", missing, kinds);
		for (int i = 1; i < facilities.size(); i++) {
			check(facilities.get(i - 1).cost() <= facilities.get(i).cost(), "the buildings are not cheapest first: %s %d, %s %d",
					facilities.get(i - 1).kind(), facilities.get(i - 1).cost(), facilities.get(i).kind(), facilities.get(i).cost());
		}
		int n = facilities.size();
		int rowH = context.computeOnClient(c -> (Integer) call(screen, "townRowH", n));
		int visible = context.computeOnClient(c -> (Integer) call(screen, "townVisible", n));
		int header = 10;
		int ty = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
		int tx = listX(context, screen);
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		String pointsLabel = prefix("screen.ak_trade_expansion.town.points");
		TextRecorder.Text points = texts.stream().filter(t -> t.text().startsWith(pointsLabel)).findFirst()
				.orElseThrow(() -> new AssertionError("no \"" + pointsLabel + "…\" on the development tab: " + texts));
		List<TextRecorder.Text> top = texts.stream().filter(t -> Math.abs(t.y0() - points.y0()) <= 2).toList();
		check(points.y0() <= ty + header, "the points %s are not above the buildings (list from y %d)", points, ty);
		check(top.size() >= 2, "the header shows no market size: %s", strings(top));
		for (int i = 0; i < visible && i < n; i++) {
			String name = text("facility.ak_trade_expansion." + facilities.get(i).kind());
			int rowTop = ty + header + i * rowH;
			// Any layer: the names are drawn after the icons, which start a new one.
			List<TextRecorder.Text> row = texts.stream().filter(t -> t.y0() >= rowTop && t.y0() < rowTop + rowH && t.x0() < tx + 180).toList();
			check(row.stream().anyMatch(t -> name.startsWith(t.text().replace("…", "")) && !t.text().isEmpty()), "row %d is not %s: %s", i, name, strings(row));
		}
		screenshot(context, result, "22-20_town_tab");
		List<TextRecorder.Text> levelTip = hover(context, points);
		String measured = prefix("screen.ak_trade_expansion.town.level.measured");
		check(levelTip.stream().anyMatch(t -> t.text().startsWith(measured)), "the header tooltip has no \"%s…\": %s", measured, strings(levelTip));

		// A row's tooltip: name, effect, size, materials, "クリックで選ぶ".
		FacilityView firstFacility = facilities.getFirst();
		moveTo(context, tx + 40, ty + header + rowH / 2.0);
		context.waitTicks(2);
		List<TextRecorder.Text> rowTip = tip(context);
		String facilityName = text("facility.ak_trade_expansion." + firstFacility.kind());
		check(rowTip.stream().anyMatch(t -> t.text().equals(facilityName)), "the row's tooltip has no \"%s\": %s", facilityName, strings(rowTip));
		String effect = text("facility.ak_trade_expansion." + firstFacility.kind() + ".effect");
		check(rowTip.stream().anyMatch(t -> effect.startsWith(t.text()) && t.text().length() >= 4), "the row's tooltip has no effect \"%s\": %s", effect, strings(rowTip));
		if (firstFacility.size().getX() > 0) {
			String size = text("screen.ak_trade_expansion.town.size", firstFacility.size().getX(), firstFacility.size().getZ(), firstFacility.size().getY());
			check(rowTip.stream().anyMatch(t -> t.text().equals(size)), "the row's tooltip has no \"%s\": %s", size, strings(rowTip));
		}
		String wanted = prefix("screen.ak_trade_expansion.town.wanted.line");
		check(firstFacility.wanted().isEmpty() || rowTip.stream().anyMatch(t -> t.text().startsWith(wanted)), "the row's tooltip lists no materials: %s", strings(rowTip));
		String hint = text("screen.ak_trade_expansion.town.select_hint");
		check(rowTip.stream().anyMatch(t -> t.text().equals(hint)), "the row's tooltip has no \"%s\": %s", hint, strings(rowTip));

		// Scroll with the wheel when not all rows fit (the smallest screen), then click a row.
		int scrolled = 0;
		if (visible < n) {
			moveTo(context, tx + 40, ty + header + rowH * 1.5);
			context.getInput().scroll(-1);
			context.waitTicks(2);
			scrolled = context.computeOnClient(c -> (Integer) get(screen, "townScroll"));
			check(scrolled == 1, "the wheel scrolled the buildings to %d, not 1", scrolled);
			screenshot(context, result, "29-1_town_scrolled");
		}
		int warehouse = facilities.stream().map(FacilityView::kind).toList().indexOf("warehouse");
		int scroll = Math.clamp(warehouse, 0, Math.max(0, n - visible));
		if (warehouse < scrolled || warehouse >= scrolled + visible) {
			context.runOnClient(c -> set(screen, "townScroll", scroll));
		} else {
			context.runOnClient(c -> set(screen, "townScroll", (Integer) get(screen, "townScroll")));
		}
		int at = context.computeOnClient(c -> (Integer) get(screen, "townScroll"));
		clickAt(context, tx + 40, ty + header + (warehouse - at) * rowH + rowH / 2.0);
		mouseAway(context);
		String selected = context.computeOnClient(c -> (String) get(screen, "selectedFacility"));
		check(facilities.get(warehouse).id().equals(selected), "a click on the warehouse row (list scrolled to %d) selected %s", at, selected);
		FacilityView w = facilities.get(warehouse);
		int px = context.computeOnClient(c -> leftPos(screen) + (Integer) get(screen, "panelX"));
		List<TextRecorder.Text> panel = TextRecorder.capture(context).stream().filter(t -> t.x0() >= px).toList();
		String warehouseName = text("facility.ak_trade_expansion.warehouse");
		check(panel.stream().anyMatch(t -> warehouseName.startsWith(t.text().replace("…", "")) && !t.text().isEmpty()), "the panel does not show \"%s\": %s",
				warehouseName, strings(panel));
		if (w.size().getX() > 0) {
			String shortSize = text("screen.ak_trade_expansion.town.size.short", w.size().getX(), w.size().getZ(), w.size().getY());
			TextRecorder.Text size = find(panel, shortSize, "size of the warehouse");
			TextRecorder.Text name = panel.stream().filter(t -> warehouseName.startsWith(t.text().replace("…", "")) && !t.text().isEmpty()).findFirst().orElseThrow();
			check(name.x1() <= size.x0() && Math.abs(name.y0() - size.y0()) <= 2, "the size %s is not right of the name %s", size, name);
			List<TextRecorder.Text> sizeTip = hover(context, size);
			String full = text("screen.ak_trade_expansion.town.size", w.size().getX(), w.size().getZ(), w.size().getY());
			check(sizeTip.stream().anyMatch(t -> t.text().equals(full)), "the size's tooltip has no \"%s\": %s", full, strings(sizeTip));
			mouseAway(context);
		}
		if (w.state() == FacilityView.NONE || w.state() == FacilityView.DESTROYED) {
			String cost = prefix("screen.ak_trade_expansion.town.cost");
			check(panel.stream().anyMatch(t -> t.text().startsWith(cost)), "the panel has no \"%s…\": %s", cost, strings(panel));
			check(widgetVisible(context, "screen.ak_trade_expansion.town.build"), "no \"建てる\" button for the warehouse");
			check(widgetVisible(context, "screen.ak_trade_expansion.town.donate_items"), "no \"資材を寄付\" button for the warehouse");
		}
		screenshot(context, result, "22-21_warehouse_selected");
		// A second click goes back to the donations.
		int at2 = context.computeOnClient(c -> (Integer) get(screen, "townScroll"));
		clickAt(context, tx + 40, ty + header + (warehouse - at2) * rowH + rowH / 2.0);
		mouseAway(context);
		check(context.computeOnClient(c -> get(screen, "selectedFacility")) == null, "a second click on the warehouse did not go back");
	}

	private static boolean widgetVisible(ClientGameTestContext context, String key) {
		return context.computeOnClient(c -> c.gui.screen().children().stream().anyMatch(ch -> ch instanceof AbstractWidget w && w.visible
				&& w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)));
	}

	// ---- 8-6 the exchange tab

	/** 8-6: "↓1" breaks an iron coin into copper, "↑1" makes one from copper; the counts on the tab follow. */
	static void exchange(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		s.world.getServer().runOnServer(server -> WalletService.giveCoins(player(server), new long[] {40, 3, 0, 0, 0}));
		context.waitTicks(3);
		reopen(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.exchange");
		mouseAway(context);
		long[] before = context.computeOnClient(c -> WalletService.coinCounts(c.player));
		checkCounts(context, before);
		clickWidget(context, "↓1"); // the first row: iron ↔ copper
		check(waitClient(context, c -> WalletService.coinCounts(c.player)[1] == before[1] - 1, 40), "\"↓1\" did not break an iron coin");
		long[] down = context.computeOnClient(c -> WalletService.coinCounts(c.player));
		check(down[0] > before[0], "breaking an iron coin gave no copper (%d -> %d)", before[0], down[0]);
		mouseAway(context);
		checkCounts(context, down);
		screenshot(context, result, "8-6_exchange_down");
		clickWidget(context, "↑1");
		check(waitClient(context, c -> WalletService.coinCounts(c.player)[1] == down[1] + 1, 40), "\"↑1\" did not make an iron coin");
		long[] up = context.computeOnClient(c -> WalletService.coinCounts(c.player));
		check(down[0] - up[0] >= CoinType.IRON.value() / CoinType.COPPER.value(), "making an iron coin took %d copper", down[0] - up[0]);
		mouseAway(context);
		checkCounts(context, up);
	}

	/** The tab shows "×n" of the iron and the copper coins. */
	private static void checkCounts(ClientGameTestContext context, long[] counts) {
		List<String> texts = strings(TextRecorder.capture(context));
		check(texts.contains("×" + counts[1]) && texts.contains("×" + counts[0]), "the exchange tab does not show ×%d iron and ×%d copper: %s", counts[1], counts[0], texts);
	}

	// ---- 7-4 the wallet

	/** 7-4: the use key with a wallet opens its screen; "×10" of the copper row takes 10 copper coins out. */
	static void wallet(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ItemStack wallet = new ItemStack(ModItems.WALLET);
			WalletItem.setBalance(wallet, 1234);
			player.getInventory().setItem(8, wallet);
		});
		float[] rot = context.computeOnClient(c -> new float[] {c.player.getYRot(), c.player.getXRot()});
		try {
			context.waitTicks(3);
			// Select the slot on the client too (the server's choice is not sent to it).
			context.getInput().pressKey(options -> options.keyHotbarSlots[8]);
			context.waitTicks(2);
			check(context.computeOnClient(c -> c.player.getMainHandItem().getItem() instanceof WalletItem), "the wallet is not in the hand");
			context.getInput().lookAt(rot[0], -90); // the sky: no block in reach
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(WalletScreen.class);
			context.waitTicks(3);
			long copper0 = s.world.getServer().computeOnServer(server -> WalletService.coinCounts(player(server))[0]);
			long[] layout = context.computeOnClient(c -> new long[] {c.gui.screen().width, c.gui.screen().height});
			int left = (int) layout[0] / 2 - 100;
			int top = (int) layout[1] / 2 - 60;
			// Rows largest coin first: copper is the last; buttons ×1, ×10, ×64.
			int copperRow = CoinType.values().length - 1;
			mouseAway(context);
			List<String> texts = strings(TextRecorder.capture(context));
			String balance = text("screen.ak_trade_expansion.wallet.balance", CoinItem.formatCopper(1234), CoinItem.formatCopper(WalletItem.capacity()));
			check(texts.contains(balance), "the wallet screen does not show \"%s\": %s", balance, texts);
			screenshot(context, result, "7-4_wallet_screen");
			clickAt(context, left + 110 + 31 + 14, top + 24 + copperRow * 22 + 9);
			boolean taken = waitClient(context, c -> WalletItem.balance(c.player.getMainHandItem()) == 1224, 40);
			check(taken, "\"×10\" of the copper row did not take 10c out of the wallet (balance %d)",
					(long) context.computeOnClient(c -> WalletItem.balance(c.player.getMainHandItem())));
			long copper1 = s.world.getServer().computeOnServer(server -> WalletService.coinCounts(player(server))[0]);
			check(copper1 - copper0 == 10, "taking 10c out gave %d copper coins", copper1 - copper0);
		} finally {
			context.setScreen(() -> null);
			context.getInput().lookAt(rot[0], rot[1]);
			s.world.getServer().runOnServer(server -> player(server).getInventory().setItem(8, ItemStack.EMPTY));
			context.waitTicks(2);
		}
	}

	// ---- 6-3 the trader opens the market

	/** 6-3: the trader standing at the stall (it comes when the stall is first opened) opens the trade screen. */
	static void traderOpens(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.openStall(s);
		context.setScreen(() -> null);
		context.waitTicks(5);
		BlockPos stall = s.stall;
		BlockPos trader = s.world.getServer().computeOnServer(server -> server.overworld()
				.getEntitiesOfClass(TraderEntity.class, new AABB(stall).inflate(4)).stream().findFirst().map(TraderEntity::blockPosition).orElse(null));
		check(trader != null, "no trader at the stall after opening it");
		context.getInput().lookAt(trader);
		context.waitTicks(3);
		boolean aimed = context.computeOnClient(c -> c.hitResult instanceof EntityHitResult e && e.getEntity() instanceof TraderEntity);
		check(aimed, "the crosshair is not on the trader at %s", trader);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(MarketScreen.class);
		context.waitTicks(5);
		mouseAway(context);
		screenshot(context, result, "6-3_trader_opens");
	}

}
