package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.data.RivalGuilds;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.menu.MarketLayout;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import com.akf.tradeexpansion.test.client.TextRecorder.Fill;
import com.akf.tradeexpansion.test.client.TextRecorder.Text;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the buy / sell list (checklist chapters 3, 19, 23 and 24): the market meter at the right end of a
 * row, the price graph with some days of history, the tooltips of goods moved by a rival guild or in fashion, where
 * the balance goes ({@code client.balancePosition}), and the size of the screen at each GUI scale.
 */
final class ClientListCases {
	private ClientListCases() {
	}

	private static final int COLOR_RED = 0xFFFF5555;
	private static final int COLOR_GREEN = 0xFF55FF55;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_GOLD = 0xFFFFD700;
	private static final int COLOR_DARK = 0xFF555555;
	/** Background of the price graph (MarketScreen.extractGraph) and its size. */
	private static final int COLOR_GRAPH_BG = 0xF0100010;
	private static final int GRAPH_W = 120;
	private static final int GRAPH_H = 64;
	/** Width of the meter (MarketScreen.extractRow): half of it each way from the center line. */
	private static final int METER_W = 22;
	/** Right edges of right-aligned texts differ by a few pixels in the recorded areas (as in ClientDisplayCases). */
	private static final int COLUMN_SLACK = 4;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String prefix(String key) {
		return Language.getInstance().getOrDefault(key).split("%s", -1)[0];
	}

	private static void buyTab(ClientGameTestContext context) {
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
	}

	private static void client(ClientGameTestContext context, Consumer<TradeExpansionConfig.Client> change) {
		context.runOnClient(c -> {
			TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
			change.accept(live.client);
			TradeExpansionConfig.set(live);
		});
		context.waitTicks(1);
	}

	private static GoodState state(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		return context.computeOnClient(c -> (GoodState) call(screen, "currentState", view(context, screen, good)));
	}

	private static int imageWidth(MarketScreen screen) {
		return (Integer) get(screen, AbstractContainerScreen.class, "imageWidth");
	}

	private static int imageHeight(MarketScreen screen) {
		return (Integer) get(screen, AbstractContainerScreen.class, "imageHeight");
	}

	// ---- the price graph

	/** The background of the price graph in the last captured frame (x0, y0, x1, y1), or {@code null}. */
	private static int[] graphBox() {
		return TextRecorder.fills().stream().filter(f -> f.color() == COLOR_GRAPH_BG && f.width() == GRAPH_W && f.height() == GRAPH_H)
				.findFirst().map(f -> new int[] {f.x0(), f.y0(), f.x1(), f.y1()}).orElse(null);
	}

	/** The texts the price graph draws (its title, "112%", "10c - 12c", "3日"), told apart from a tooltip's by what they say. */
	private static Predicate<Text> graphText() {
		String buy = text("screen.ak_trade_expansion.graph.buy");
		String sell = text("screen.ak_trade_expansion.graph.sell");
		String[] days = Language.getInstance().getOrDefault("screen.ak_trade_expansion.graph.days").split("%s", -1);
		Pattern daysPattern = Pattern.compile(Pattern.quote(days[0]) + "\\d+" + Pattern.quote(days.length > 1 ? days[1] : ""));
		return t -> t.text().equals(buy) || t.text().equals(sell) || t.text().matches("\\d+%") || t.text().matches(".+c - .+c")
				|| daysPattern.matcher(t.text()).matches();
	}

	/**
	 * The graph next to a row's tooltip: left of the cursor when there is room for it, otherwise above or below the
	 * tooltip; inside the screen and on none of the tooltip's lines. Returns the tooltip lines.
	 */
	private static List<Text> checkGraphBesideTooltip(ClientGameTestContext context, List<Text> frame, int mouseX, String what) {
		int[] g = graphBox();
		check(g != null, "%s: no price graph next to the cursor", what);
		List<Text> tip = ClientUtil.tooltip(frame).stream().filter(graphText().negate()).toList();
		check(!tip.isEmpty(), "%s: no tooltip", what);
		int[] size = context.computeOnClient(c -> new int[] {c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight()});
		int tipTop = tip.stream().mapToInt(Text::y0).min().orElse(0);
		int tipBottom = tip.stream().mapToInt(Text::y1).max().orElse(0);
		if (mouseX - 10 - GRAPH_W >= 4) {
			check(g[2] <= mouseX, "%s: the price graph (x %d-%d) is not left of the cursor (x %d)", what, g[0], g[2], mouseX);
		} else {
			check(g[1] >= tipBottom || g[3] <= tipTop, "%s: with no room on the left, the price graph (y %d-%d) is not above or below the tooltip (y %d-%d)",
					what, g[1], g[3], tipTop, tipBottom);
		}
		check(g[0] >= 0 && g[1] >= 0 && g[2] <= size[0] && g[3] <= size[1], "%s: the price graph %d,%d-%d,%d runs off the %dx%d screen",
				what, g[0], g[1], g[2], g[3], size[0], size[1]);
		List<Text> covered = tip.stream().filter(t -> t.inside(g[0], g[1], g[2], g[3])).toList();
		check(covered.isEmpty(), "%s: the price graph covers tooltip lines: %s", what, covered);
		check(tip.stream().allMatch(t -> t.x0() >= 0 && t.y0() >= 0 && t.x1() <= size[0] && t.y1() <= size[1]), "%s: tooltip lines run off the screen: %s", what, tip);
		return tip;
	}

	// ---- 3-2 / 3-3 the meter and the price graph

	/**
	 * 3-2: the meter at the right end of a row: a dear good (×2) fills from the center line to the right in red, a cheap
	 * one (×0.5) to the left in green, as far as the log scale says (the ends are ×3 and ÷3). 3-3: with some days of
	 * history the price graph of the dear good is a line through one point per day (and today), each at its price, with
	 * the lowest and highest price and the number of days written under it.
	 */
	static void meterAndGraph(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		MarketScreen probe = ClientDisplayCases.reopen(s);
		buyTab(context);
		List<GoodView> goods = visibleGoods(context, probe).stream()
				.filter(v -> PriceModel.canBuy(v.state()) && !v.special() && v.patch().isEmpty() && v.unit() == 1
						&& v.params().maxMultiplier() >= 2.5 && v.params().minMultiplier() <= 0.4 && v.params().basePrice() >= 8)
				.toList();
		check(goods.size() >= 2, "fewer than two goods whose price can go to ×2.5 and ×0.4");
		GoodView dear = goods.stream().max(Comparator.comparingDouble(v -> v.params().basePrice())).orElseThrow();
		GoodView cheap = goods.stream().filter(v -> v != dear).findFirst().orElseThrow();
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		Object[] saved = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			GoodState d = market.good(dear.goods());
			GoodState c = market.good(cheap.goods());
			Object[] old = {d.pressure(), new ArrayList<>(d.history()), c.pressure()};
			GoodParams dp = market.params(dear.goods());
			GoodParams cp = market.params(cheap.goods());
			double up = dp.clampPressure(-dp.elasticity() * Math.log(2));
			d.setPressure(up);
			// Five days going up and down on the way to ×2: a line with corners.
			d.history().clear();
			for (double f : new double[] {0, 0.3, 0.1, 0.6, 0.4}) d.history().add(up * f);
			c.setPressure(cp.clampPressure(cp.elasticity() * Math.log(2)));
			MarketRegistry.get(server).setDirty();
			return old;
		});
		try {
			MarketScreen screen = ClientDisplayCases.reopen(s);
			buyTab(context);
			mouseAway(context);
			for (GoodView v : List.of(dear, cheap)) {
				boolean up = v == dear;
				int rowY = showRow(context, screen, v.goods());
				mouseAway(context);
				TextRecorder.capture(context);
				List<Fill> fills = TextRecorder.fills();
				double m = PriceModel.multiplier(v.params(), state(context, screen, v.goods()).pressure());
				check(up ? m > 1.5 : m < 0.67, "%s is at ×%.2f, not made %s", v.goods(), m, up ? "dear" : "cheap");
				Fill center = fills.stream().filter(f -> f.width() == 1 && f.height() == 6 && f.y0() == rowY + 6 && f.color() == COLOR_WHITE).findFirst()
						.orElseThrow(() -> new AssertionError("no center line of the meter in the row of " + v.goods() + ": "
								+ fills.stream().filter(f -> f.y0() >= rowY && f.y1() <= rowY + 18).toList()));
				Fill track = fills.stream().filter(f -> f.width() == METER_W && f.height() == 4 && f.y0() == rowY + 7 && f.color() == COLOR_DARK).findFirst()
						.orElseThrow(() -> new AssertionError("no meter track in the row of " + v.goods()));
				check(center.x0() == track.x0() + METER_W / 2, "the center line (x %d) is not in the middle of the meter (x %d-%d)", center.x0(), track.x0(), track.x1());
				Fill bar = fills.stream().filter(f -> f.height() == 4 && f.y0() == rowY + 7 && f.color() == (up ? COLOR_RED : COLOR_GREEN)).findFirst()
						.orElseThrow(() -> new AssertionError("no " + (up ? "red" : "green") + " bar in the meter of " + v.goods() + ": "
								+ fills.stream().filter(f -> f.y0() >= rowY && f.y1() <= rowY + 18).toList()));
				int end = center.x0() + (int) Math.round(Math.clamp(Math.log(m) / Math.log(3.0), -1, 1) * (METER_W / 2.0));
				if (up) {
					check(bar.x0() == center.x0() && bar.x1() > center.x0() + 1, "the red bar %s of the dear good does not go right from the center x %d", bar, center.x0());
					check(Math.abs(bar.x1() - 1 - end) <= 1, "the red bar of ×%.2f ends at x %d, not %d", m, bar.x1() - 1, end);
				} else {
					check(bar.x1() == center.x0() + 1 && bar.x0() < center.x0(), "the green bar %s of the cheap good does not go left from the center x %d", bar, center.x0());
					check(Math.abs(bar.x0() - end) <= 1, "the green bar of ×%.2f starts at x %d, not %d", m, bar.x0(), end);
				}
				check(bar.x0() >= track.x0() && bar.x1() <= track.x1() + 1, "the bar %s leaves the meter %s", bar, track);
			}
			screenshot(context, result, "3-2_meters");

			// 3-3: the graph of the dear good.
			int rowY = showRow(context, screen, dear.goods());
			int mouseX = listX(context, screen) + 60;
			moveTo(context, mouseX, rowY + 8);
			context.waitTicks(2);
			List<Text> frame = TextRecorder.capture(context);
			int[] g = graphBox();
			check(g != null, "no price graph over the row of %s", dear.goods());
			GoodView now = view(context, screen, dear.goods());
			GoodState st = state(context, screen, dear.goods());
			List<Double> xs = new ArrayList<>(now.history());
			check(xs.size() >= 5, "the snapshot has %d days of history, not the 5 put in", xs.size());
			xs.add(st.pressure());
			long[] prices = xs.stream().mapToLong(x -> PriceModel.buyPrice(now.params(), x)).toArray();
			long min = java.util.Arrays.stream(prices).min().orElseThrow();
			long max = java.util.Arrays.stream(prices).max().orElseThrow();
			check(max > min, "the history of %s does not change its price (%s)", dear.goods(), java.util.Arrays.toString(prices));
			List<Fill> inside = TextRecorder.fills().stream().filter(f -> f.x0() >= g[0] && f.x1() <= g[2] && f.y0() >= g[1] && f.y1() <= g[3]).toList();
			List<Fill> points = inside.stream().filter(f -> f.width() == 2 && f.height() == 2 && f.color() == COLOR_WHITE).sorted(Comparator.comparingInt(Fill::x0)).toList();
			check(points.size() == prices.length, "the graph has %d points, not one per day and today (%d)", points.size(), prices.length);
			int px0 = g[0] + 6;
			int py0 = g[1] + 16;
			int pw = GRAPH_W - 12;
			int ph = GRAPH_H - 30;
			for (int i = 0; i < prices.length; i++) {
				int px = px0 + i * pw / (prices.length - 1);
				int py = py0 + ph - (int) Math.round((prices[i] - min) * (double) ph / (max - min));
				Fill p = points.get(i);
				check(Math.abs(p.x0() + 1 - px) <= 1 && Math.abs(p.y0() + 1 - py) <= 2, "point %d (%dc) of the graph is at %d,%d, not %d,%d",
						i, prices[i], p.x0() + 1, p.y0() + 1, px, py);
			}
			List<Fill> line = inside.stream().filter(f -> f.color() == COLOR_GOLD).toList();
			int lineX0 = line.stream().mapToInt(Fill::x0).min().orElse(0);
			int lineX1 = line.stream().mapToInt(Fill::x1).max().orElse(0);
			check(lineX0 <= px0 + 1 && lineX1 >= px0 + pw - 1, "the gold line of the graph goes from x %d to %d, not from the first point (%d) to the last (%d)",
					lineX0, lineX1, px0, px0 + pw);
			int[] ys = line.stream().mapToInt(Fill::y0).distinct().toArray();
			check(ys.length >= 5, "the line of the graph is flat (%d heights)", ys.length);
			String range = CoinItem.formatCopper(min) + " - " + CoinItem.formatCopper(max);
			String days = text("screen.ak_trade_expansion.graph.days", prices.length - 1);
			List<Text> graphTexts = frame.stream().filter(t -> t.inside(g[0], g[1], g[2], g[3])).toList();
			check(graphTexts.stream().anyMatch(t -> t.text().equals(range)), "the graph does not say \"%s\": %s", range, strings(graphTexts));
			check(graphTexts.stream().anyMatch(t -> t.text().equals(days)), "the graph does not say \"%s\": %s", days, strings(graphTexts));
			String pct = Math.round(PriceModel.multiplier(now.params(), st.pressure()) * 100) + "%";
			check(graphTexts.stream().anyMatch(t -> t.text().equals(pct) && t.color() == COLOR_RED), "the graph has no red \"%s\": %s", pct, strings(graphTexts));
			screenshot(context, result, "3-3_price_graph_line");
		} finally {
			mouseAway(context);
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				GoodState d = market.good(dear.goods());
				d.setPressure((Double) saved[0]);
				d.history().clear();
				@SuppressWarnings("unchecked")
				List<Double> history = (List<Double>) saved[1];
				d.history().addAll(history);
				market.good(cheap.goods()).setPressure((Double) saved[2]);
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	// ---- 23-2 / 24-2 the tooltips of a rival campaign and of a trend

	/**
	 * 23-2: a good of a rival campaign at this market: its row's tooltip says "〇〇商会が買い集め中：値上がりしています
	 * （あと N 日）" (red; selling off: green), and the price graph moves aside of the tooltip. 24-2: a good of the group
	 * in fashion: "この村では今「〇〇」が人気（基準価格 ×1.2x）" in red with the multiplier {@code /aktradeex market info}
	 * shows in its trends.
	 */
	static void rivalAndTrendTooltips(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		int campaignId = s.world.getServer().computeOnServer(server -> {
			Market home = market(server, stall);
			ClientDisplayCases.ensureTrend(home);
			MarketRegistry registry = MarketRegistry.get(server);
			Campaign c = RivalService.startNow(server.overworld(), registry, home, MarketUpkeep.touch(server.overworld(), home));
			check(c != null, "no campaign could start at the stall's market");
			return c.id();
		});
		try {
			MarketScreen screen = ClientDisplayCases.reopen(s);
			Campaign campaign = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).campaigns().stream()
					.filter(c -> c.id() == campaignId).findFirst().orElseThrow());
			// 23-2: the campaign's good, on the buy tab or else the sell tab.
			buyTab(context);
			if (visibleGoods(context, screen).stream().noneMatch(v -> v.goods().equals(campaign.goods()))) {
				context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
				context.waitTicks(2);
			}
			GoodView rivalGood = view(context, screen, campaign.goods());
			MarketSnapshotS2C.RivalView rival = rivalGood.rival()
					.orElseThrow(() -> new AssertionError("the snapshot has no rival campaign on " + campaign.goods()));
			check(rival.buying() == campaign.buying(), "the snapshot says %s, the campaign %s", rival.buying() ? "buying" : "selling",
					campaign.buying() ? "buying" : "selling");
			String days = rival.daysLeft() <= 0 ? text("screen.ak_trade_expansion.rival.today") : text("screen.ak_trade_expansion.rival.days_left", rival.daysLeft());
			String rivalLine = text(rival.buying() ? "screen.ak_trade_expansion.rival.buying" : "screen.ak_trade_expansion.rival.selling",
					RivalGuilds.name(rival.guild()), days);
			int rowY = showRow(context, screen, campaign.goods());
			int mouseX = listX(context, screen) + 60;
			moveTo(context, mouseX, rowY + 8);
			context.waitTicks(2);
			List<Text> tip = checkGraphBesideTooltip(context, TextRecorder.capture(context), mouseX, "rival " + campaign.goods());
			int rgb = (rival.buying() ? COLOR_RED : COLOR_GREEN) & 0xFFFFFF;
			check(tip.stream().anyMatch(t -> t.text().equals(rivalLine) && t.styleColor() == rgb), "the tooltip of %s has no %s \"%s\": %s",
					campaign.goods(), rival.buying() ? "red" : "green", rivalLine, strings(tip));
			screenshot(context, result, "23-2_rival_tooltip");
			mouseAway(context);

			// 24-2: a good of the popular group.
			buyTab(context);
			GoodView trendGood = visibleGoods(context, screen).stream().filter(v -> v.trends().stream().anyMatch(t -> t.multiplier() > 1)).findFirst()
					.orElseThrow(() -> new AssertionError("no good of the popular group on the buy tab"));
			MarketSnapshotS2C.TrendView trend = trendGood.trends().stream().filter(t -> t.multiplier() > 1).findFirst().orElseThrow();
			String trendLine = text("screen.ak_trade_expansion.trend.popular.here", GoodsGroups.name(trend.group()), ClientDisplayCases.multiplier(trend.multiplier()));
			rowY = showRow(context, screen, trendGood.goods());
			moveTo(context, mouseX, rowY + 8);
			context.waitTicks(2);
			tip = checkGraphBesideTooltip(context, TextRecorder.capture(context), mouseX, "trend " + trendGood.goods());
			check(tip.stream().anyMatch(t -> t.text().equals(trendLine) && t.styleColor() == (COLOR_RED & 0xFFFFFF)), "the tooltip of %s has no red \"%s\": %s",
					trendGood.goods(), trendLine, strings(tip));
			screenshot(context, result, "24-2_trend_tooltip");
			mouseAway(context);

			// The same multiplier as the trends of /aktradeex market info (run as the player: the lines go to its chat).
			context.setScreen(() -> null);
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			double serverValue = s.world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(player.commandSource()).withEntity(player)
						.withPosition(player.position()).withLevel(player.level()), "aktradeex market info");
				return market(server, stall).trends().get(trend.group());
			});
			String expected = trend.group() + "=" + serverValue;
			check(waitClient(context, c -> chat(c).stream().anyMatch(m -> m.content().getString().contains(expected)), 40),
					"the chat of market info has no trends line with \"%s\": %s", expected,
					context.computeOnClient(c -> chat(c).stream().map(m -> m.content().getString()).toList()));
			check(Math.abs(serverValue - trend.multiplier()) < 0.01, "market info says %s ×%.3f, the tooltip ×%.3f", trend.group(), serverValue, trend.multiplier());
		} finally {
			mouseAway(context);
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				MarketRegistry registry = MarketRegistry.get(server);
				registry.campaigns().removeIf(c -> c.id() == campaignId);
				registry.setDirty();
			});
		}
	}

	private static List<GuiMessage> chat(net.minecraft.client.Minecraft client) {
		return ClientScreenCases.chat(client);
	}

	// ---- 3-5 client.balancePosition

	/**
	 * 3-5: {@code client.balancePosition} BELOW_SELL_SLOT puts "現金／銀行" in the column under the sell slot, left of
	 * the inventory; TOP_RIGHT one line at the right end of the title row, where the title and the reputation stop
	 * before it (a long market name shrinks). No texts on top of each other, the breakdown on hover with both.
	 */
	static void balancePosition(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		TradeExpansionConfig.BalancePosition before = TradeExpansionConfig.get().client.balancePosition;
		if (s.world.getServer().computeOnServer(server -> WalletService.balance(player(server))) < 1000) {
			s.world.getServer().runOnServer(server -> WalletService.give(player(server), 12_345));
		}
		BlockPos stall = s.stall;
		String oldName = s.world.getServer().computeOnServer(server -> market(server, stall).name());
		String cashLabel = text("screen.ak_trade_expansion.cash_label");
		String bankLabel = text("screen.ak_trade_expansion.bank_label");
		try {
			MarketScreen screen = ClientDisplayCases.reopen(s);
			check(waitClient(context, c -> get(screen, "account") != null, 40), "the screen got no bank account");
			MarketLayout layout = context.computeOnClient(c -> screen.getMenu().layout());
			int left = context.computeOnClient(c -> leftPos(screen));
			int top = context.computeOnClient(c -> topPos(screen));
			int width = context.computeOnClient(c -> imageWidth(screen));

			// Below the sell slot.
			client(context, c -> c.balancePosition = TradeExpansionConfig.BalancePosition.BELOW_SELL_SLOT);
			mouseAway(context);
			List<Text> all = TextRecorder.capture(context);
			int colX = left + 8;
			int colRight = left + layout.inventoryX() - 4;
			int colTop = top + layout.sellSlotY() + 22;
			// Below the sell slot only: the bank tab button says "銀行" too.
			List<Text> column = all.stream().filter(t -> t.y0() >= colTop - 2).toList();
			Text cash = ClientDisplayCases.find(column, cashLabel, "cash below the sell slot");
			Text bank = ClientDisplayCases.find(column, bankLabel, "bank below the sell slot");
			for (Text t : List.of(cash, bank)) {
				check(Math.abs(t.x0() - colX) <= 1 && t.x1() <= colRight && t.y0() >= colTop - 1, "BELOW_SELL_SLOT: %s is not in the column under the sell slot (x %d-%d, from y %d)",
						t, colX, colRight, colTop);
			}
			check(bank.y0() > cash.y0(), "BELOW_SELL_SLOT: the bank %s is not below the cash %s", bank, cash);
			List<Text> amounts = all.stream().filter(t -> t.x0() >= colX - 1 && t.x1() <= colRight && t.y0() > cash.y0() && t.text().endsWith("c")).toList();
			check(amounts.size() >= 2, "BELOW_SELL_SLOT: the column has no two amounts: %s", strings(amounts));
			check(all.stream().noneMatch(t -> t.text().equals(cashLabel) && t.x0() >= left + layout.inventoryX() + 9 * 18), "BELOW_SELL_SLOT: the cash is still right of the inventory");
			layoutOk(context, "3-5 BELOW_SELL_SLOT", all);
			breakdownOnHover(context, cash, "BELOW_SELL_SLOT");
			screenshot(context, result, "3-5_below_sell_slot");

			// The right end of the title row.
			client(context, c -> c.balancePosition = TradeExpansionConfig.BalancePosition.TOP_RIGHT);
			for (boolean longName : new boolean[] {false, true}) {
				String where = longName ? "TOP_RIGHT with a long name" : "TOP_RIGHT";
				MarketScreen open = screen(context);
				mouseAway(context);
				String balance = context.computeOnClient(c -> ((Component) call(open, "topRightBalance", c.player)).getString());
				List<Text> frame = TextRecorder.capture(context);
				List<Text> row = ClientDisplayCases.band(frame, top + 2, top + 14);
				Text shown = ClientDisplayCases.find(row, balance, "balance at the right end of the title row");
				check(Math.abs(shown.x1() - (left + width - 8)) <= COLUMN_SLACK, "%s: the balance ends at x %d, not at the right end (%d)", where, shown.x1(), left + width - 8);
				List<Text> before2 = row.stream().filter(t -> t != shown).toList();
				check(!before2.isEmpty(), "%s: the title row has nothing but the balance", where);
				check(before2.stream().allMatch(t -> t.x1() <= shown.x0()), "%s: a text of the title row runs into the balance %s: %s", where, shown, before2);
				MarketSnapshotS2C snapshot = snapshot(context, open);
				if (snapshot.reputation().enabled()) {
					String rep = text("screen.ak_trade_expansion.reputation.label",
							Component.translatable("screen.ak_trade_expansion.reputation.level." + Math.clamp(snapshot.reputation().level(), 0, 5)));
					ClientDisplayCases.find(row, rep, where + ": reputation");
				}
				check(frame.stream().noneMatch(t -> t.text().equals(cashLabel)), "%s: the two-line balance column is still drawn", where);
				if (longName) {
					check(context.computeOnClient(c -> open.getTitle().getString()).contains("とても長い名前"), "the screen does not have the long name");
					Text title = row.stream().min(Comparator.comparingInt(Text::x0)).orElseThrow();
					int full = context.computeOnClient(c -> c.font.width(open.getTitle().getString()));
					check(title.width() < full, "%s: the title %s is not shrunk (whole: %d wide)", where, title, full);
				}
				layoutOk(context, "3-5 " + where, frame);
				breakdownOnHover(context, shown, where);
				screenshot(context, result, longName ? "3-5_top_right_long_name" : "3-5_top_right");
				if (!longName) {
					s.world.getServer().runOnServer(server -> {
						market(server, stall).setName("とても長い名前の市場でタイトルの行には入りきらない名前の例");
						MarketRegistry.get(server).setDirty();
					});
					ClientDisplayCases.reopen(s);
					check(waitClient(context, c -> c.gui.screen() instanceof MarketScreen m && get(m, "account") != null, 40), "the reopened screen got no bank account");
				}
			}
		} finally {
			client(context, c -> c.balancePosition = before);
			mouseAway(context);
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> market(server, stall).setName(oldName));
		}
	}

	private static void layoutOk(ClientGameTestContext context, String where, List<Text> texts) {
		List<String> problems = context.computeOnClient(c -> LayoutCheck.check(where, texts, c.getWindow().getGuiScaledWidth(),
				c.getWindow().getGuiScaledHeight(), ClientChecklistTests.panel(c.gui.screen())));
		check(problems.isEmpty(), "%s", problems);
	}

	/** The mouse over the balance shows what it is made of (coins "×N" and the bank). */
	private static void breakdownOnHover(ClientGameTestContext context, Text at, String where) {
		moveTo(context, at.x0() + 3, (at.y0() + at.y1()) / 2.0);
		context.waitTicks(2);
		List<Text> tip = ClientDisplayCases.tip(context);
		String bankLine = prefix("screen.ak_trade_expansion.balance.bank");
		check(tip.stream().anyMatch(t -> t.text().contains(" ×")), "%s: the balance tooltip shows no coins: %s", where, strings(tip));
		check(tip.stream().anyMatch(t -> t.text().startsWith(bankLine)), "%s: the balance tooltip has no \"%s…\": %s", where, bankLine, strings(tip));
		mouseAway(context);
	}

	// ---- 19-1 / 19-2 the screen at each GUI scale

	/** Window sizes and GUI scales (0 = auto, the largest that fits). */
	private static final int[][] SIZES = {{1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 0}, {854, 480, 0}};

	/**
	 * 19-1: at a large GUI-scaled window the screen grows: the goods list has 7–10 rows (all filled when the market has
	 * that many goods) and a wider name column; the slots of the inventory and the sell slot are where the screen
	 * draws them, and an item moves into the sell slot and back with the mouse. 19-2: at the largest GUI scale the
	 * screen keeps its base height (240, 6 rows) and stays inside the window.
	 */
	static void sizeByGuiScale(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		MarketScreen probe = ClientDisplayCases.reopen(s);
		GoodView sellable = snapshot(context, probe).goods().stream()
				.filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty() && new ItemStack(itemOf(v)).getMaxStackSize() >= 2).findFirst()
				.orElseThrow(() -> new AssertionError("no good to sell"));
		Item item = itemOf(sellable);
		context.setScreen(() -> null);
		int had = s.world.getServer().computeOnServer(server -> count(player(server), item));
		s.world.getServer().runOnServer(server -> giveItems(player(server), item, 2));
		context.waitTicks(3);
		int rowH = constant(MarketScreen.class, "ROW_H");
		boolean seenTen = false;
		boolean seenMax = false;
		try {
			for (int[] size : SIZES) {
				context.getInput().resizeWindow(size[0], size[1]);
				context.runOnClient(c -> {
					c.options.guiScale().set(size[2]);
					c.resizeGui();
				});
				context.waitTicks(3);
				// The layout is decided when the screen opens.
				MarketScreen screen = ClientDisplayCases.reopen(s);
				buyTab(context);
				int[] gui = context.computeOnClient(c -> new int[] {c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight(), c.getWindow().getGuiScale()});
				String where = String.format("%dx%d GUI %d (%dx%d)", size[0], size[1], gui[2], gui[0], gui[1]);
				MarketLayout expected = MarketLayout.fit(gui[0], gui[1]);
				MarketLayout layout = context.computeOnClient(c -> screen.getMenu().layout());
				int[] box = context.computeOnClient(c -> new int[] {leftPos(screen), topPos(screen), imageWidth(screen), imageHeight(screen),
						(Integer) get(screen, "rows"), (Integer) get(screen, "listW")});
				check(layout.equals(expected), "%s: the layout is %s, not %s", where, layout, expected);
				check(box[2] == expected.width() && box[3] == expected.height(), "%s: the screen is %dx%d, not %dx%d", where, box[2], box[3], expected.width(), expected.height());
				check(box[4] == expected.rows(), "%s: the list has %d rows, not %d", where, box[4], expected.rows());
				check(box[5] == 200 + expected.extraWidth(), "%s: the list is %d wide, not %d", where, box[5], 200 + expected.extraWidth());
				check(box[0] >= 0 && box[1] >= 0 && box[0] + box[2] <= gui[0] && box[1] + box[3] <= gui[1], "%s: the screen %d,%d %dx%d leaves the window",
						where, box[0], box[1], box[2], box[3]);
				int maxScale = context.computeOnClient(c -> c.getWindow().calculateScale(0, c.isEnforceUnicode()));
				if (gui[2] == maxScale) {
					check(box[3] == MarketLayout.BASE_HEIGHT && box[4] == MarketLayout.BASE_ROWS, "%s (the largest scale): %d high with %d rows, not 240 with 6", where, box[3], box[4]);
					seenMax = true;
				} else if (size[0] == 1920 && gui[2] <= 3) {
					check(box[4] >= 7 && box[4] <= 10, "%s: %d rows, not 7 to 10", where, box[4]);
					check(box[5] > 200, "%s: the name column is not wider (list %d wide)", where, box[5]);
					seenTen |= box[4] == 10;
				}

				// Every row of the list shows a good (as many as there are).
				int n = visibleGoods(context, screen).size();
				int listY = box[1] + constant(MarketScreen.class, "LIST_Y");
				int listX = box[0] + constant(MarketScreen.class, "LIST_X");
				mouseAway(context);
				List<Text> texts = TextRecorder.capture(context);
				for (int i = 0; i < Math.min(n, box[4]); i++) {
					int y = listY + i * rowH;
					check(texts.stream().anyMatch(t -> t.y0() >= y && t.y0() < y + rowH && t.x0() >= listX && t.x1() <= listX + box[5]),
							"%s: row %d of the list (y %d) is empty with %d goods", where, i + 1, y, n);
				}

				// The slots are where the layout puts them; an item goes into the sell slot and back with the mouse.
				List<int[]> wrong = context.computeOnClient(c -> {
					List<int[]> bad = new ArrayList<>();
					List<Slot> slots = screen.getMenu().slots;
					int sellIndex = (Integer) get(screen.getMenu(), "sellSlotIndex");
					Slot sell = slots.get(sellIndex);
					if (sell.x != expected.sellSlotX() || sell.y != expected.sellSlotY()) bad.add(new int[] {-1, sell.x, sell.y});
					for (Slot slot : slots) {
						if (slot.container != c.player.getInventory()) continue;
						int i = slot.getContainerSlot();
						int ex = expected.inventoryX() + (i % 9) * 18;
						int ey = i < 9 ? expected.inventoryY() + 58 : expected.inventoryY() + (i / 9 - 1) * 18;
						if (i < 36 && (slot.x != ex || slot.y != ey)) bad.add(new int[] {i, slot.x, slot.y});
					}
					return bad;
				});
				check(wrong.isEmpty(), "%s: slots not where the layout puts them (index, x, y): %s", where,
						wrong.stream().map(java.util.Arrays::toString).toList());
				putInSellSlot(context, screen, item);
				context.waitTicks(2);
				Slot sell = context.computeOnClient(c -> screen.getMenu().slots.get((Integer) get(screen.getMenu(), "sellSlotIndex")));
				check(context.computeOnClient(c -> sell.getItem().is(item)), "%s: the item did not go into the sell slot", where);
				check(waitClient(context, c -> sellable.goods().equals(get(screen, "selected")), 20), "%s: the item in the sell slot did not select its good", where);
				Slot empty = context.computeOnClient(c -> screen.getMenu().slots.stream()
						.filter(sl -> sl.container == c.player.getInventory() && sl.getContainerSlot() >= 9 && sl.getContainerSlot() < 36 && !sl.hasItem())
						.findFirst().orElse(null));
				check(empty != null, "%s: no empty inventory slot", where);
				clickAt(context, box[0] + sell.x + 8, box[1] + sell.y + 8);
				clickAt(context, box[0] + empty.x + 8, box[1] + empty.y + 8);
				mouseAway(context);
				check(waitClient(context, c -> !sell.hasItem() && empty.getItem().is(item), 20), "%s: the item did not go back from the sell slot to the inventory", where);
				screenshot(context, result, String.format("19-1_gui_%dx%d_scale_%d", size[0], size[1], gui[2]));
			}
			check(seenTen, "no size had 10 rows");
			check(seenMax, "the largest GUI scale was not checked");
			context.setScreen(() -> null);
			context.waitTicks(3);
			int now = s.world.getServer().computeOnServer(server -> count(player(server), item));
			check(now == had + 2, "the player has %d of the item after moving it around, not %d", now, had + 2);
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(c -> {
				c.options.guiScale().set(0);
				c.resizeGui();
			});
			context.setScreen(() -> null);
			context.waitTicks(2);
			s.world.getServer().runOnServer(server -> removeItems(player(server), item, 2));
		}
	}

	private static void removeItems(ServerPlayer player, Item item, int n) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && n > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.is(item)) continue;
			int take = Math.min(n, stack.getCount());
			stack.shrink(take);
			n -= take;
		}
		player.containerMenu.broadcastChanges();
	}
}
