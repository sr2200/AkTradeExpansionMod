package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.block.FacilitySignBlock;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.client.Directions;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.TrendMath;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the board that the first ones left out (checklist 9-1, 10-1, 20-5, 29-11 and 35-2): the market list
 * nearest first with a third market (founded 700 blocks south for the case and deleted at the end), a renamed market's
 * name, a theater village's trends on the board and in the trade screen, and the board with no market in reach
 * ({@code board.radius}, doubled by a tavern) still offering "この市場の座標".
 */
final class ClientBoardCases {
	private ClientBoardCases() {
	}

	private static final String NS = "ak_trade_expansion";

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static void config(Session s, Consumer<TradeExpansionConfig> change) {
		s.world.getServer().runOnServer(server -> {
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			change.accept(config);
			ConfigManager.apply(config);
		});
	}

	/** A completed building of {@code market} without its blocks; its sign at {@code core} keeps it from counting as destroyed. */
	private static void building(MinecraftServer server, Market market, FacilityKind kind, BlockPos core) {
		ServerLevel level = server.overworld();
		long now = MarketClock.now(level);
		Identifier id = Identifier.fromNamespaceAndPath(NS, kind.getSerializedName());
		Facility f = new Facility(id, kind);
		f.start(core, Rotation.NONE, core, new BoundingBox(core), "plains", now, now + 2 * PriceModel.TICKS_PER_DAY);
		level.setBlock(core, ModBlocks.FACILITY_SIGN.defaultBlockState().setValue(FacilitySignBlock.KIND, kind).setValue(FacilitySignBlock.BUILT, true), 2);
		f.complete(core);
		market.facilities().put(id, f);
		MarketRegistry.get(server).setDirty();
	}

	private static void removeBuilding(MinecraftServer server, Market market, FacilityKind kind, BlockPos core) {
		market.facilities().remove(Identifier.fromNamespaceAndPath(NS, kind.getSerializedName()));
		server.overworld().setBlock(core, Blocks.AIR.defaultBlockState(), 2);
		MarketRegistry.get(server).setDirty();
	}

	/** One group in fashion (×1.3) and one out of fashion (×0.8) from the market's goods; the old trends go back with {@code restore}. */
	private static Identifier[] setTrends(Market market, List<Runnable> restore) {
		Map<Identifier, Double> old = new HashMap<>(market.trends());
		restore.add(() -> {
			market.trends().clear();
			market.trends().putAll(old);
		});
		List<Identifier> groups = market.regularGoods().stream().flatMap(g -> GoodsGroups.groupsOf(g).stream()).distinct().toList();
		check(groups.size() >= 2, "the market's goods have fewer than 2 groups: %s", groups);
		market.trends().clear();
		market.trends().put(groups.get(0), 1.3);
		market.trends().put(groups.get(1), 0.8);
		return new Identifier[] {groups.get(0), groups.get(1)};
	}

	private static String trendLine(Identifier group, double m) {
		return text("screen." + NS + ".trend." + (m >= 1 ? "popular" : "unpopular"), GoodsGroups.name(group), ClientDisplayCases.multiplier(m));
	}

	/** The trends of two groups as the server has them now (before the theater). */
	private static double[] rawTrends(Session s, java.util.function.BiFunction<MinecraftServer, Market, Market> which, BlockPos stall, Identifier[] groups) {
		return s.world.getServer().computeOnServer(server -> {
			Market m = which.apply(server, market(server, stall));
			return new double[] {m.trends().getOrDefault(groups[0], 1.0), m.trends().getOrDefault(groups[1], 1.0)};
		});
	}

	private static MarketScreen openBoard(Session s) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = ClientDisplayCases.reopen(s);
		context.clickScreenButton("screen." + NS + ".tab.board");
		context.waitTicks(2);
		return screen;
	}

	// ---- 9-1 / 10-1 the market list

	/**
	 * 9-1 / 10-1: with board.radius 1,000 and a third market 700 blocks south, the board lists the market 400 blocks
	 * east first and the new one after it, each row with its name and "東 400m" as worked out from where it is. A click
	 * on the east row selects it: its goods have the header in line with the columns, both prices explained, and scroll
	 * to the last. 10-1 (recorded apart): the east market, renamed as a name tag does, shows its new name in the row
	 * and in the selected market's title.
	 */
	static void marketList(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		int radius0 = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().board.radius);
		List<Runnable> restore = new ArrayList<>();
		List<String> nameProblems = new ArrayList<>();
		long[] third = {-1};
		BlockPos[] thirdStall = new BlockPos[1];
		String name = "テスト港";
		try {
			config(s, c -> c.board.radius = 1000);
			// The third market: a village 700 blocks south (its chunks kept loaded, or the villagers are not counted).
			BlockPos feet = s.world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos f = ground(server, stall.getX(), stall.getZ() + 700);
				for (int cx = (f.getX() >> 4) - 2; cx <= (f.getX() >> 4) + 2; cx++) {
					for (int cz = (f.getZ() >> 4) - 2; cz <= (f.getZ() >> 4) + 2; cz++) {
						if (level.setChunkForced(cx, cz, true)) {
							int x = cx, z = cz;
							restore.add(() -> level.setChunkForced(x, z, false));
						}
					}
				}
				village(level, f);
				village(level, f.west(12)); // 6 beds: enough for however many villagers are counted
				return f;
			});
			context.waitTicks(20);
			long eastId = s.world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Market home = market(server, stall);
				Market east = ClientDisplayCases.other(server, home);
				BlockPos at = ground(server, feet.getX(), feet.getZ() - 2);
				level.setBlock(at, ModBlocks.MARKET_STALL.defaultBlockState(), 3);
				thirdStall[0] = at;
				MarketRegistry.Assignment a = ((MarketStallBlockEntity) level.getBlockEntity(at)).assign(level, MarketOrigin.CRAFTED);
				check(a.market() != null && a.created(), "the stall 700 blocks south founded no market (%s)", a.reason() == null ? a.rejection() : a.reason().getString());
				third[0] = a.market().id();
				// 10-1: the east market renamed.
				String oldName = east.name();
				restore.add(() -> east.setName(oldName));
				MarketNames.rename(east, name, null);
				MarketRegistry.get(server).setDirty();
				return east.id();
			});

			MarketScreen screen = openBoard(s);
			BoardS2C board = ClientDisplayCases.board(context, screen);
			check(board.radius() == 1000, "the board's radius is %d, not board.radius 1000", board.radius());
			List<Long> order = board.markets().stream().map(BoardS2C.Entry::id).toList();
			check(order.indexOf(eastId) >= 0 && order.indexOf(third[0]) > order.indexOf(eastId), "the board lists %s, not the market 400 east (%d) before the one 700 south (%d)",
					order, eastId, third[0]);
			List<Double> distances = board.markets().stream().map(e -> Math.sqrt((double) e.dx() * e.dx() + (double) e.dz() * e.dz())).toList();
			for (int i = 1; i < distances.size(); i++) check(distances.get(i - 1) <= distances.get(i), "the board is not nearest first: %s", distances);

			int listY = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
			int rowH = constant(MarketScreen.class, "ROW_H");
			int x0 = listX(context, screen);
			context.runOnClient(c -> set(screen, "scroll", 0));
			mouseAway(context);
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			int news = board.news().size();
			int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
			check(news + order.indexOf(third[0]) < rows, "the third market's row is below the list (%d news)", news);
			for (int i = 0; i < board.markets().size() && news + i < rows; i++) {
				BoardS2C.Entry e = board.markets().get(i);
				List<TextRecorder.Text> row = rowTexts(texts, listY + (news + i) * rowH);
				String where = text("screen." + NS + ".board.where", Directions.of(e.dx(), e.dz()), (int) Math.round(Math.sqrt((double) e.dx() * e.dx() + (double) e.dz() * e.dz())));
				check(row.stream().anyMatch(t -> t.text().equals(where)), "row %d (%s) does not say \"%s\": %s", news + i, e.title().getString(), where, strings(row));
				String title = e.title().getString();
				check(row.stream().anyMatch(t -> !t.text().isEmpty() && title.startsWith(t.text())), "row %d does not show \"%s\": %s", news + i, title, strings(row));
			}
			BoardS2C.Entry east = board.markets().get(order.indexOf(eastId));
			int eastY = listY + (news + order.indexOf(eastId)) * rowH;
			if (!east.title().getString().startsWith(name)) nameProblems.add("the renamed market is \"" + east.title().getString() + "\" on the board");
			if (rowTexts(texts, eastY).stream().noneMatch(t -> t.text().startsWith(name))) {
				nameProblems.add("the row of the renamed market does not start with \"" + name + "\": " + strings(rowTexts(texts, eastY)));
			}
			screenshot(context, result, "9-1_board_nearest_first");

			// A click on the row selects it: the name in the title, then the goods.
			clickAt(context, x0 + 40, eastY + 8);
			mouseAway(context);
			check(context.computeOnClient(c -> (Long) get(screen, "boardMarket")) == eastId, "a click on the row did not select the market");
			int top = context.computeOnClient(c -> topPos(screen));
			List<TextRecorder.Text> panel = TextRecorder.capture(context).stream().filter(t -> t.y0() >= top + 36 && t.y0() < top + 60).toList();
			if (panel.stream().noneMatch(t -> t.text().startsWith(name))) nameProblems.add("the selected market's title does not have \"" + name + "\": " + strings(panel));
			screenshot(context, also, "10-1_board_renamed");
			ClientDisplayCases.boardGoods(context, screen, east, result, "9-1_board_selected_scrolled");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				restore.forEach(Runnable::run);
				if (thirdStall[0] != null) server.overworld().setBlock(thirdStall[0], Blocks.AIR.defaultBlockState(), 3);
				if (third[0] >= 0) MarketRegistry.get(server).remove(third[0]);
				MarketRegistry.get(server).setDirty();
			});
			config(s, c -> c.board.radius = radius0);
		}
		if (!nameProblems.isEmpty()) throw new ClientChecklistTests.AlsoFailed(new AssertionError(String.join("; ", nameProblems)));
	}

	// ---- 29-11 a theater's trends

	/**
	 * 29-11: the market 400 blocks east with a theater and a trend in fashion (×1.3) and one out of fashion (×0.8):
	 * the board sends the theater's multipliers (1+(m−1)×k, 1−(1−m)÷k) and the mouse over its row shows them. The
	 * stall's own village with a theater: the snapshot has them and the trade screen's "流行" tooltip shows them.
	 */
	static void theaterTrends(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		List<Runnable> restore = new ArrayList<>();
		try {
			Object[] set = s.world.getServer().computeOnServer(server -> {
				Market east = ClientDisplayCases.other(server, market(server, stall));
				BlockPos core = east.center().above(30);
				building(server, east, FacilityKind.THEATER, core);
				restore.add(() -> removeBuilding(server, east, FacilityKind.THEATER, core));
				return new Object[] {east.id(), setTrends(east, restore)};
			});
			long eastId = (Long) set[0];
			Identifier[] groups = (Identifier[]) set[1];
			double k = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().development.theaterTrendMultiplier);

			MarketScreen screen = openBoard(s);
			BoardS2C board = ClientDisplayCases.board(context, screen);
			BoardS2C.Entry east = board.markets().stream().filter(e -> e.id() == eastId).findFirst()
					.orElseThrow(() -> new AssertionError("the market 400 blocks east is not on the board"));
			// The trends drift within their period when the market is touched: expected from the server's own values now.
			double[] eastRaw = rawTrends(s, ClientDisplayCases::other, stall, groups);
			double up = TrendMath.theater(eastRaw[0], k), down = TrendMath.theater(eastRaw[1], k);
			check(eastRaw[0] > 1 && up > eastRaw[0] && eastRaw[1] < 1 && down > eastRaw[1] && down < 1, "theater(%s) gives %.3f -> %.3f / %.3f -> %.3f", k,
					eastRaw[0], up, eastRaw[1], down);
			Map<Identifier, Double> sent = new HashMap<>();
			east.trends().forEach(t -> sent.put(t.group(), t.multiplier()));
			check(Math.abs(sent.getOrDefault(groups[0], 0.0) - up) < 1e-6 && Math.abs(sent.getOrDefault(groups[1], 0.0) - down) < 1e-6,
					"the board sends the theater village's trends as %s, not %.4f / %.4f (from %.4f / %.4f)", sent, up, down, eastRaw[0], eastRaw[1]);
			int listY = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y");
			int rowH = constant(MarketScreen.class, "ROW_H");
			int index = board.news().size() + board.markets().indexOf(east);
			int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
			int scroll = Math.max(0, index - rows + 1);
			context.runOnClient(c -> set(screen, "scroll", scroll));
			moveTo(context, listX(context, screen) + 40, listY + (index - scroll) * rowH + 8);
			context.waitTicks(2);
			List<TextRecorder.Text> tip = ClientDisplayCases.tip(context);
			for (String line : List.of(trendLine(groups[0], up), trendLine(groups[1], down))) {
				check(tip.stream().anyMatch(t -> t.text().equals(line)), "the theater village's row has no \"%s\": %s", line, strings(tip));
			}
			screenshot(context, result, "29-11_board_theater_trends");

			// The stall's own village with a theater: the trade screen's trends.
			Identifier[] homeGroups = s.world.getServer().computeOnServer(server -> {
				Market home = market(server, stall);
				BlockPos core = stall.above(9);
				building(server, home, FacilityKind.THEATER, core);
				restore.add(() -> removeBuilding(server, home, FacilityKind.THEATER, core));
				return setTrends(home, restore);
			});
			MarketScreen trade = ClientDisplayCases.reopen(s);
			MarketSnapshotS2C snapshot = snapshot(context, trade);
			double[] homeRaw = rawTrends(s, (server, home) -> home, stall, homeGroups);
			double homeUp = TrendMath.theater(homeRaw[0], k), homeDown = TrendMath.theater(homeRaw[1], k);
			Map<Identifier, Double> mine = new HashMap<>();
			snapshot.trends().forEach(t -> mine.put(t.group(), t.multiplier()));
			check(Math.abs(mine.getOrDefault(homeGroups[0], 0.0) - homeUp) < 1e-6 && Math.abs(mine.getOrDefault(homeGroups[1], 0.0) - homeDown) < 1e-6,
					"the trade screen gets the theater village's trends as %s, not %.4f / %.4f", mine, homeUp, homeDown);
			mouseAway(context);
			int top = context.computeOnClient(c -> topPos(trade));
			List<TextRecorder.Text> titleRow = ClientDisplayCases.band(TextRecorder.capture(context), top + 2, top + 14);
			TextRecorder.Text label = ClientDisplayCases.find(titleRow, text("screen." + NS + ".trend.label"), "trends");
			List<TextRecorder.Text> trendTip = ClientDisplayCases.hover(context, label);
			for (String line : List.of(trendLine(homeGroups[0], homeUp), trendLine(homeGroups[1], homeDown))) {
				check(trendTip.stream().anyMatch(t -> t.text().equals(line)), "the trade screen's trends tooltip has no \"%s\": %s", line, strings(trendTip));
			}
			screenshot(context, result, "29-11_screen_theater_trends");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				restore.forEach(Runnable::run);
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	// ---- 20-5 / 35-2 no market in reach

	/**
	 * 20-5 / 35-2: board.radius 150 (the market 400 east out of reach, the rival news of the area put aside): the board
	 * says "半径 150 ブロック以内にほかの市場はありません" and still has "この市場の座標", which writes this market's name and
	 * coordinates to the chat. With a tavern in the village the number is the radius the server used, 150 × the tavern's
	 * multiplier.
	 */
	static void noMarkets(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		int radius0 = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().board.radius);
		List<Runnable> restore = new ArrayList<>();
		try {
			config(s, c -> c.board.radius = 150);
			s.world.getServer().runOnServer(server -> {
				MarketRegistry registry = MarketRegistry.get(server);
				List<Campaign> campaigns = new ArrayList<>(registry.campaigns());
				registry.campaigns().clear();
				restore.add(() -> {
					registry.campaigns().clear();
					registry.campaigns().addAll(campaigns);
				});
			});
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			MarketScreen screen = openBoard(s);
			BoardS2C board = ClientDisplayCases.board(context, screen);
			check(board.markets().isEmpty() && board.news().isEmpty(), "with board.radius 150 the board still lists %d markets and %d news", board.markets().size(), board.news().size());
			mouseAway(context);
			String none = text("screen." + NS + ".board.none", 150);
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			check(texts.stream().anyMatch(t -> none.startsWith(t.text()) && t.text().contains("150")), "the board does not say \"%s\": %s", none, strings(texts));
			String here = "screen." + NS + ".board.map.here";
			check(ClientScreenCases.visible(context, here), "with no market in reach the board has no \"%s\"", text(here));
			screenshot(context, result, "35-2_board_no_markets");
			MarketSnapshotS2C snapshot = snapshot(context, screen);
			Component title = context.computeOnClient(c -> screen.getTitle());
			context.clickScreenButton(here);
			mouseAway(context);
			ClientScreenCases.checkCoordsInChat(context, title, snapshot.center(), "this market (no market in reach)");
			screenshot(context, result, "20-5_no_markets_coords");

			// 35-2: a tavern doubles the reach; the number is the server's.
			double reach = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().development.tavernBoardMultiplier);
			s.world.getServer().runOnServer(server -> {
				Market home = market(server, stall);
				BlockPos core = stall.above(9);
				building(server, home, FacilityKind.TAVERN, core);
				restore.add(() -> removeBuilding(server, home, FacilityKind.TAVERN, core));
			});
			int expected = (int) Math.round(150 * Math.max(1.0, reach));
			MarketScreen tavern = openBoard(s);
			BoardS2C wide = ClientDisplayCases.board(context, tavern);
			if (wide.radius() != expected) throw new ClientChecklistTests.AlsoFailed(new AssertionError("with a tavern the board's radius is " + wide.radius() + ", not " + expected));
			if (wide.markets().isEmpty() && wide.news().isEmpty()) {
				mouseAway(context);
				String wider = text("screen." + NS + ".board.none", expected);
				List<TextRecorder.Text> now = TextRecorder.capture(context);
				if (now.stream().noneMatch(t -> wider.startsWith(t.text()) && t.text().contains(String.valueOf(expected)))) {
					throw new ClientChecklistTests.AlsoFailed(new AssertionError("with a tavern the board does not say \"" + wider + "\": " + strings(now)));
				}
				screenshot(context, also, "35-2_board_tavern");
			}
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				restore.forEach(Runnable::run);
				MarketRegistry.get(server).setDirty();
			});
			config(s, c -> c.board.radius = radius0);
		}
	}
}
