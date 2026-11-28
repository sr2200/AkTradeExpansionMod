package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.Directions;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.data.MarketEvents;
import com.akf.tradeexpansion.event.EventService;
import com.akf.tradeexpansion.event.MarketEvent;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of market events (design V, checklist 47-2 / 47-3): a running event centered on the stall's market and
 * a forecast one (starting tomorrow) centered on the market 400 blocks east. The board's news rows, their colors and
 * tooltips, a click on the forecast opening its center market, the goods row's tooltip on the trade screen, and the
 * board with both rows at every size in Japanese and English (no texts on top of each other or outside the screen).
 */
final class ClientEventCases {
	private ClientEventCases() {
	}

	private static final String NS = "ak_trade_expansion";
	/** Colors of the headline of a news row (MarketScreen.extractEventNews) and of the tooltip lines. */
	private static final int RGB_HEADLINE_UP = 0xFF8080;
	private static final int RGB_HEADLINE_DOWN = 0x80FF80;
	private static final int RGB_RED = 0xFF5555;
	private static final int RGB_GREEN = 0x55FF55;
	/** Window sizes and GUI scales (0 = auto) of the layout check: the small window, and GUI 1 to 4 on the large one. */
	private static final int[][] SIZES = {{854, 480, 0}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}};

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String daysLeft(int days) {
		return days <= 0 ? text("screen." + NS + ".rival.today") : text("screen." + NS + ".rival.days_left", days);
	}

	private static void config(Session s, Consumer<TradeExpansionConfig> change) {
		s.world.getServer().runOnServer(server -> {
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			change.accept(config);
			ConfigManager.apply(config);
		});
	}

	/**
	 * Starts the first bundled event (lowering prices first when {@code down}) that can be centered on {@code center}
	 * today, moving a good it deals in. {@code null} when none fits.
	 */
	private static MarketEvent start(MinecraftServer server, Market center, boolean down) {
		ServerLevel level = server.overworld();
		MarketRegistry registry = MarketRegistry.get(server);
		long now = MarketUpkeep.touch(level, center);
		List<Identifier> ids = new ArrayList<>(MarketEvents.all().keySet());
		ids.sort((a, b) -> Boolean.compare(MarketEvents.get(a).raises() == down, MarketEvents.get(b).raises() == down));
		for (Identifier id : ids) {
			MarketEvent e = EventService.startNow(level, registry, id, center, now);
			if (e == null) continue;
			if (e.goods().stream().anyMatch(g -> center.good(g) != null)) return e;
			registry.events().remove(e);
		}
		return null;
	}

	private static BoardS2C.EventNews news(BoardS2C board, MarketEvent e) {
		return board.events().stream().filter(n -> n.marketId() == e.market() && n.event().equals(e.event())).findFirst()
				.orElseThrow(() -> new AssertionError("the board has no news of the event " + e.event() + ": " + board.events()));
	}

	private static MarketScreen openBoard(Session s) {
		MarketScreen screen = ClientDisplayCases.reopen(s);
		s.context.clickScreenButton("screen." + NS + ".tab.board");
		s.context.waitTicks(2);
		return screen;
	}

	/** The headline of a news row, as MarketScreen writes it before fitting it to the row. */
	private static String headline(BoardS2C.EventNews n) {
		String key = "screen." + NS + ".board.event." + (n.forecast() ? "forecast." : "") + (n.raises() ? "up" : "down");
		return text(key, MarketEvents.name(n.event()), n.goods());
	}

	private static String where(BoardS2C.EventNews n) {
		return n.dx() == 0 && n.dz() == 0 ? text("screen." + NS + ".board.news.here")
				: text("screen." + NS + ".board.where", Directions.of(n.dx(), n.dz()), (int) Math.round(Math.sqrt((double) n.dx() * n.dx() + (double) n.dz() * n.dz())));
	}

	/** The news row {@code index} (the list scrolled to the top): its headline in its color, where and when at the right. */
	private static void checkRow(List<TextRecorder.Text> texts, int rowY, BoardS2C.EventNews n, String what) {
		List<TextRecorder.Text> row = rowTexts(texts, rowY);
		String full = headline(n);
		int rgb = n.raises() ? RGB_HEADLINE_UP : RGB_HEADLINE_DOWN;
		TextRecorder.Text head = row.stream().filter(t -> !t.text().isEmpty() && (t.text().equals(full) || full.startsWith(t.text().replace("…", ""))))
				.findFirst().orElseThrow(() -> new AssertionError(what + ": the news row does not say \"" + full + "\": " + strings(row)));
		check((head.color() & 0xFFFFFF) == rgb, "%s: the headline is %06X, not %s", what, head.color() & 0xFFFFFF, n.raises() ? "red" : "green");
		String right = where(n) + " " + (n.forecast() ? text("screen." + NS + ".board.event.tomorrow") : daysLeft(n.daysLeft()));
		TextRecorder.Text r = row.stream().filter(t -> t.text().equals(right)).findFirst()
				.orElseThrow(() -> new AssertionError(what + ": the news row has no \"" + right + "\": " + strings(row)));
		check(head.x1() <= r.x0(), "%s: the headline (to x %d) runs into \"%s\" (from x %d)", what, head.x1(), right, r.x0());
	}

	/** The tooltip of a news row: the name in its color, the description, the multiplier, the ramp, felt here and the click. */
	private static void checkTooltip(List<TextRecorder.Text> tip, BoardS2C.EventNews n, boolean listed, String what) {
		String name = MarketEvents.name(n.event()).getString();
		int rgb = n.raises() ? RGB_RED : RGB_GREEN;
		check(tip.stream().anyMatch(t -> t.text().equals(name) && t.styleColor() == rgb), "%s: the tooltip has no %s \"%s\": %s", what,
				n.raises() ? "red" : "green", name, strings(tip));
		String desc = text(MarketEvents.key(n.event()) + (n.forecast() ? ".forecast" : ".desc"), n.market());
		String multiplier = n.forecast() ? text("screen." + NS + ".board.event.tooltip.forecast", n.daysLeft(), ClientDisplayCases.multiplier(n.multiplier()))
				: text("screen." + NS + ".board.event.tooltip.multiplier", ClientDisplayCases.multiplier(n.multiplier()), daysLeft(n.daysLeft()));
		List<String> lines = new ArrayList<>(List.of(desc, multiplier, text("screen." + NS + ".board.event.tooltip.ramp")));
		if (n.felt()) lines.add(text("screen." + NS + ".board.event." + (n.forecast() ? "felt_forecast" : "felt")));
		if (listed) lines.add(text("screen." + NS + ".board.news.open"));
		// Long lines are wrapped: every piece of the line is in the tooltip, in order.
		String all = String.join("", strings(tip));
		for (String line : lines) {
			check(all.contains(line) || joined(tip).contains(line), "%s: the tooltip has no \"%s\": %s", what, line, strings(tip));
		}
	}

	/** The tooltip's lines joined with a space (a wrapped line breaks at a space in English). */
	private static String joined(List<TextRecorder.Text> tip) {
		return String.join(" ", strings(tip));
	}

	/**
	 * 47-2 / 47-3: an event running at the stall's market (lowering prices if one can) and a forecast one at the market
	 * 400 blocks east (raising prices if one can). The board lists both first, nearest first: the headline in green /
	 * red, "この市場 あと N 日" and "東 400m 明日から" at the right; the tooltips explain them and the forecast's says a
	 * click opens its market, which it does. The trade screen's goods row of the running event says "豊作：基準価格
	 * ×0.91（あと N 日）" in its color. Then the board with both rows and the tooltip of each at every size, in Japanese
	 * and English.
	 */
	static void board(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		List<Runnable> restore = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		boolean[] settings = s.world.getServer().computeOnServer(server -> {
			TradeExpansionConfig.Events e = TradeExpansionConfig.get().events;
			return new boolean[] {e.enabled, e.forecast};
		});
		try {
			config(s, c -> {
				c.events.enabled = true;
				c.events.forecast = true;
			});
			MarketEvent[] events = s.world.getServer().computeOnServer(server -> {
				MarketRegistry registry = MarketRegistry.get(server);
				List<MarketEvent> saved = new ArrayList<>(registry.events());
				restore.add(() -> {
					registry.events().clear();
					registry.events().addAll(saved);
					for (Market m : registry.all()) m.eventMultipliers().clear();
				});
				registry.events().clear();
				Market home = market(server, stall);
				Market east = ClientDisplayCases.other(server, home);
				MarketEvent here = start(server, home, true);
				check(here != null, "no market event can start at the stall's market");
				MarketEvent later = start(server, east, false);
				check(later != null, "no market event can start at the market 400 blocks east");
				// The second one starts tomorrow: a forecast.
				long today = EventService.day(MarketClock.now(server.overworld()));
				MarketEvent forecast = new MarketEvent(later.id(), later.event(), later.market(), later.goods(), today + 1, today + 1 + later.days(), later.multiplier());
				registry.events().set(registry.events().indexOf(later), forecast);
				MarketUpkeep.touch(server.overworld(), home);
				MarketUpkeep.touch(server.overworld(), east);
				registry.setDirty();
				return new MarketEvent[] {here, forecast};
			});
			MarketEvent here = events[0], forecast = events[1];

			// 47-2: the trade screen's goods row of the running event.
			MarketScreen trade = ClientDisplayCases.reopen(s);
			MarketSnapshotS2C snapshot = snapshot(context, trade);
			MarketSnapshotS2C.GoodView good = snapshot.goods().stream()
					.filter(v -> v.event().isPresent() && v.event().get().event().equals(here.event()) && (v.buy() || v.sell())).findFirst()
					.orElseThrow(() -> new AssertionError("no goods row of the trade screen has the event " + here.event()));
			MarketSnapshotS2C.EventView ev = good.event().get();
			check(here.raises() ? ev.multiplier() > 1 : ev.multiplier() < 1, "the event multiplier of %s is %.3f (the event %.3f)", good.goods(), ev.multiplier(), here.multiplier());
			context.clickScreenButton("screen." + NS + ".tab." + (good.buy() ? "buy" : "sell"));
			context.waitTicks(2);
			int rowY = showRow(context, trade, good.goods());
			moveTo(context, listX(context, trade) + 60, rowY + 8);
			context.waitTicks(2);
			List<TextRecorder.Text> rowTip = ClientDisplayCases.tip(context);
			String line = text("screen." + NS + ".event.tooltip", MarketEvents.name(ev.event()), ClientDisplayCases.multiplier(ev.multiplier()), daysLeft(ev.daysLeft()));
			int rgb = ev.multiplier() > 1 ? RGB_RED : RGB_GREEN;
			check(rowTip.stream().anyMatch(t -> t.text().equals(line) && t.styleColor() == rgb), "the tooltip of %s has no %s \"%s\": %s", good.goods(),
					rgb == RGB_RED ? "red" : "green", line, strings(rowTip));
			screenshot(context, result, "47-2_event_row_tooltip");
			mouseAway(context);

			// 47-3: the board.
			MarketScreen screen = openBoard(s);
			BoardS2C board = ClientDisplayCases.board(context, screen);
			BoardS2C.EventNews nHere = news(board, here), nLater = news(board, forecast);
			check(board.events().indexOf(nHere) == 0 && board.events().indexOf(nLater) == 1, "the board lists the events as %s, not this market's first", board.events());
			check(!nHere.forecast() && nLater.forecast(), "forecast flags: here %s, east %s", nHere.forecast(), nLater.forecast());
			check(nHere.felt(), "the event at this market is not felt here");
			check(nHere.dx() == 0 && nHere.dz() == 0, "the event at this market is %d / %d away", nHere.dx(), nHere.dz());
			check(nLater.daysLeft() == forecast.days(), "the forecast says %d days, the event lasts %d", nLater.daysLeft(), forecast.days());
			check(!nHere.items().isEmpty() && !nLater.items().isEmpty(), "a news row has no item for its icon");
			boolean listed = board.markets().stream().anyMatch(e -> e.id() == nLater.marketId());
			check(listed, "the market 400 blocks east is not on the board");

			int listY = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
			int rowH = constant(MarketScreen.class, "ROW_H");
			int x0 = listX(context, screen);
			context.runOnClient(c -> set(screen, "scroll", 0));
			mouseAway(context);
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			checkRow(texts, listY, nHere, "running");
			checkRow(texts, listY + rowH, nLater, "forecast");
			moveTo(context, x0 + 60, listY + 8);
			context.waitTicks(2);
			checkTooltip(ClientDisplayCases.tip(context), nHere, board.markets().stream().anyMatch(e -> e.id() == nHere.marketId()), "running");
			screenshot(context, result, "47-3_board_event_running");
			moveTo(context, x0 + 60, listY + rowH + 8);
			context.waitTicks(2);
			checkTooltip(ClientDisplayCases.tip(context), nLater, true, "forecast");
			screenshot(context, result, "47-3_board_event_forecast");
			clickAt(context, x0 + 60, listY + rowH + 8);
			mouseAway(context);
			long opened = context.computeOnClient(c -> (Long) get(screen, "boardMarket"));
			check(opened == nLater.marketId(), "a click on the forecast opened market %d, not its center %d", opened, nLater.marketId());
			screenshot(context, result, "47-3_event_opens_market");
			context.setScreen(() -> null);

			// The layout of the board with both rows, and their tooltips inside the screen, at every size.
			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				MarketScreen sized = openBoard(s);
				ClientDisplayCases.board(context, sized);
				for (int[] size : SIZES) {
					context.getInput().resizeWindow(size[0], size[1]);
					context.runOnClient(client -> {
						client.options.guiScale().set(size[2]);
						client.resizeGui();
					});
					context.waitTicks(3);
					context.runOnClient(c -> set(sized, "scroll", 0));
					String where = String.format("%s %dx%d GUI %s board(events)", language, size[0], size[1], size[2] == 0 ? "auto" : size[2]);
					ClientChecklistTests.checkLayout(context, result, where, false, problems, null);
					int y = context.computeOnClient(c -> topPos(sized)) + constant(MarketScreen.class, "LIST_Y");
					int x = listX(context, sized);
					for (int row = 0; row < 2; row++) {
						moveTo(context, x + 60, y + row * rowH + 8);
						context.waitTicks(2);
						try {
							ClientDisplayCases.tip(context);
						} catch (AssertionError e) {
							problems.add(where + " row " + row + ": " + e.getMessage());
						}
					}
					if (language.equals("en_us") && size == SIZES[0]) screenshot(context, result, "47-3_board_events_en");
					mouseAway(context);
				}
				context.setScreen(() -> null);
			}
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.resizeGui();
			});
			ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
			mouseAway(context);
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				restore.forEach(Runnable::run);
				MarketRegistry.get(server).setDirty();
			});
			config(s, c -> {
				c.events.enabled = settings[0];
				c.events.forecast = settings[1];
			});
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}
}
