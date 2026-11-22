package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.WeatherMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.RainGoods;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.CargoDeliverC2S;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
import com.akf.tradeexpansion.quest.Cargo;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.weather.WeatherService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/** Checklist 23-12 (rival campaigns start near the players, v0.82) and 41: the weather moves the market (design U). */
final class ServerWeatherCases {
	private static final int DAY = (int) PriceModel.TICKS_PER_DAY;

	private ServerWeatherCases() {
	}

	/** Runs {@code body} with the rain record replaced by {@code rain}, and puts the old record back afterwards. */
	@FunctionalInterface
	private interface Body {
		void run() throws Exception;
	}

	/** Sets the overworld's weather: thundering (with rain) or clear. */
	private static void storm(ServerLevel overworld, boolean on) {
		var weather = overworld.getWeatherData();
		weather.setClearWeatherTime(0);
		weather.setRaining(on);
		weather.setRainTime(on ? 6000 : 0);
		weather.setThundering(on);
		weather.setThunderTime(on ? 6000 : 0);
		overworld.setRainLevel(on ? 1 : 0);
		overworld.setThunderLevel(on ? 1 : 0);
	}

	private static void withRain(MinecraftServer server, Map<Long, Integer> rain, Body body) throws Exception {
		Map<Long, Integer> record = MarketRegistry.get(server).rain();
		Map<Long, Integer> saved = new TreeMap<>(record);
		try {
			record.clear();
			record.putAll(rain);
			body.run();
		} finally {
			record.clear();
			record.putAll(saved);
		}
	}

	private static void near(double expected, double actual, String what) {
		that(Math.abs(expected - actual) < 1e-9, "%s: %s, expected %s", what, actual, expected);
	}

	private static long today(MinecraftServer server) {
		return WeatherMath.day(server.overworld().getGameTime());
	}

	/** A good of the rain list in {@code market} (added for the test when the market has none). */
	private static Identifier rainGood(Market market, long now) {
		for (Identifier id : RainGoods.all()) {
			if (market.good(id) != null && !market.isSpecial(id)) return id;
		}
		Identifier id = RainGoods.all().iterator().next();
		market.addGood(id, new GoodState(1.0, 0, GoodsCatalog.get(id).targetStock(), now));
		return id;
	}

	private static MarketSnapshotS2C.GoodView view(MarketSnapshotS2C snapshot, Identifier good) {
		return snapshot.goods().stream().filter(v -> v.goods().equals(good)).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("the snapshot has no " + good));
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("rival_center_near_visits", List.of("23-12"),
				"商会の動きの中心が、最近プレイヤーが交易画面を開いた市場の近く（board.radius 以内）から選ばれ、最近開かれた市場が無い時は全部の市場から選ばれること"
						+ "（rivals start と同じ選び方を5回ずつ。日ごとの抽選そのものは見ていない）",
				ctx -> ServerChapterCases.markets(ctx, new int[] {0, 600}, (c, player, m) -> {
					Market a = m.get(0), b = m.get(1);
					MarketRegistry registry = MarketRegistry.get(c.level().getServer());
					c.config(cfg -> cfg.board.radius = 64); // only A itself is near A
					long now = c.now();
					// Only A was visited lately (opening its stall in markets() visited it; forget the visits of other cases).
					for (Market other : registry.all()) if (other != a) other.setLastVisit(Long.MIN_VALUE);
					that(a.lastVisit() != Long.MIN_VALUE, "opening the stall of A did not note a visit");
					for (int i = 0; i < 5; i++) {
						Campaign camp = RivalService.startNow(c.level(), registry, null, now + i);
						that(camp != null, "no campaign could start (%d)", i);
						registry.campaigns().remove(camp);
						equal(a.id(), camp.market(), "center of campaign " + i + " (only A was visited lately)");
					}
					// Nobody visited lately: any market may be the center (B too).
					a.setLastVisit(now - (RivalService.RECENT_DAYS + 1L) * DAY);
					boolean other = false;
					for (int i = 0; i < 40 && !other; i++) {
						Campaign camp = RivalService.startNow(c.level(), registry, null, now + 100 + i);
						that(camp != null, "no campaign could start without visits (%d)", i);
						registry.campaigns().remove(camp);
						other = camp.market() != a.id();
					}
					that(other, "without visits every campaign was still centered on A");
					that(b.lastVisit() == Long.MIN_VALUE, "B was never opened but has a visit");
				})));
		cases.add(partial("weather_rain_crops", List.of("41-1", "41-2", "41-4"),
				"今日と前の2日の雨が合わせて1日分になると、オーバーワールドの市場の作物の基準価格が ×0.85 になり（交易画面のスナップショットの倍率と B）、作物でない品は変わらず、"
						+ "雨続きが始まった・終わった時にチャットで知らせ、雨が足りない時・weather.enabled が OFF の時は元の値段のままなこと（ツールチップの表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					MinecraftServer server = c.level().getServer();
					long day = today(server);
					Identifier crop = rainGood(market, c.now());
					Identifier other = market.regularGoods().stream().filter(id -> !RainGoods.contains(id) && market.params(id) != null).findFirst()
							.orElseThrow(() -> new AutotestCheck.Failed("the market has only rain goods"));
					withRain(server, Map.of(), () -> {
						WeatherService.sample(server, 0); // notes "no rainy spell" (it may rain now: 0 ticks are added)
						c.openStall(player, stall);
						MarketSnapshotS2C dry = c.lastSnapshot(player);
						near(1.0, view(dry, crop).rainMultiplier(), "rain multiplier without rain");
						double crop0 = view(dry, crop).params().basePrice(), other0 = view(dry, other).params().basePrice();
						// 0.9 days of rain: not yet.
						MarketRegistry.get(server).rain().put(day - 1, DAY * 9 / 10);
						c.openStall(player, stall);
						near(1.0, view(c.lastSnapshot(player), crop).rainMultiplier(), "rain multiplier after 0.9 days of rain");
						// 1 day: a rainy spell.
						c.forget(player);
						MarketRegistry.get(server).rain().put(day - 2, DAY / 10);
						WeatherService.sample(server, 0);
						that(c.gotMessage(player, "message.ak_trade_expansion.weather.rain_start"), "no message when the rainy spell began: %s", c.messages(player));
						c.openStall(player, stall);
						MarketSnapshotS2C wet = c.lastSnapshot(player);
						double m = com.akf.tradeexpansion.config.TradeExpansionConfig.get().weather.rainMultiplier;
						near(m, view(wet, crop).rainMultiplier(), "rain multiplier of " + crop);
						that(Math.abs(view(wet, crop).params().basePrice() - crop0 * m) < 1e-6, "B of %s: %.4f, expected %.4f", crop,
								view(wet, crop).params().basePrice(), crop0 * m);
						near(1.0, view(wet, other).rainMultiplier(), "rain multiplier of " + other);
						that(Math.abs(view(wet, other).params().basePrice() - other0) < 1e-6, "B of %s changed in the rain", other);
						// Off: the old prices.
						c.config(cfg -> cfg.weather.enabled = false);
						c.openStall(player, stall);
						near(1.0, view(c.lastSnapshot(player), crop).rainMultiplier(), "rain multiplier with weather.enabled off");
						c.config(cfg -> cfg.weather.enabled = true);
						// The rain is over (the days moved out of the window).
						c.forget(player);
						MarketRegistry.get(server).rain().clear();
						WeatherService.sample(server, 0);
						that(c.gotMessage(player, "message.ak_trade_expansion.weather.rain_end"), "no message when the rainy spell ended: %s", c.messages(player));
						c.openStall(player, stall);
						near(1.0, view(c.lastSnapshot(player), crop).rainMultiplier(), "rain multiplier after the rain");
					});
				})));
		cases.add(partial("weather_storm_cargo", List.of("41-3", "41-5"),
				"雷雨の間に荷箱を届けると、輸送の報酬が ×1.5（切り上げ）になり、保証金はそのまま戻り、チャットに雷雨の配達の行が出ること。"
						+ "雷雨が始まった時に荷箱を持っていると知らせが出ること（雷雨の見え方は見ていない。雷雨は天気の値を直接変えて作った）",
				ctx -> ServerChapterCases.markets(ctx, new int[] {0, 600}, (c, player, m) -> {
					Market a = m.get(0), b = m.get(1);
					MinecraftServer server = c.level().getServer();
					ServerLevel overworld = server.overworld();
					Request transport = ServerChapterCases.transportOf(a, b);
					c.command(player, "aktradeex reputation set 1000"); // 3-star requests need a reputation
					c.giveMoney(player, transport.transport().orElseThrow().deposit() + 1_000);
					QuestService.accept(player, new QuestAcceptC2S(a.id(), transport.id(), false));
					that(ServerChapterCases.questResult(c, player) == TradeResult.SUCCESS, "taking the crate gave %s", ServerChapterCases.questResult(c, player));
					Cargo cargo = null;
					for (int i = 0; i < player.getInventory().getContainerSize() && cargo == null; i++) cargo = Cargo.of(player.getInventory().getItem(i));
					that(cargo != null, "no cargo crate after taking the request");
					try {
						storm(overworld, false);
						WeatherService.sample(server, 0); // notes "no thunderstorm"
						c.forget(player);
						storm(overworld, true);
						that(overworld.isThundering(), "the test could not make a thunderstorm");
						WeatherService.sample(server, 0);
						that(c.gotMessage(player, "message.ak_trade_expansion.weather.storm_start"), "no message for the crate holder when the storm began: %s",
								c.messages(player));
						ServerChapterCases.openAt(c, player, m, 1);
						long money = c.balance(player);
						c.forget(player);
						QuestService.deliverCargo(player, new CargoDeliverC2S(b.id(), false));
						that(ServerChapterCases.questResult(c, player) == TradeResult.SUCCESS, "delivering in the storm gave %s", ServerChapterCases.questResult(c, player));
						long reward = (long) Math.ceil(cargo.reward() * com.akf.tradeexpansion.config.TradeExpansionConfig.get().weather.stormRewardMultiplier - 1e-9);
						equal(money + reward + cargo.deposit(), c.balance(player), "money after delivering in a thunderstorm (reward " + cargo.reward() + ")");
						that(c.gotMessage(player, "message.ak_trade_expansion.weather.storm_bonus"), "no storm bonus line: %s", c.messages(player));
					} finally {
						storm(overworld, false);
					}
				})));
		return cases;
	}
}
