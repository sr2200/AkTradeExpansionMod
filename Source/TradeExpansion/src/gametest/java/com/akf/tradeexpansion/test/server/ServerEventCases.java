package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.EventMath;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.MarketEvents;
import com.akf.tradeexpansion.event.EventService;
import com.akf.tradeexpansion.event.MarketEvent;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.quest.DemandService;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/** Checklist 47: market events (design V). */
final class ServerEventCases {
	private static final Identifier HARVEST = Identifier.parse("ak_trade_expansion:bumper_harvest");

	private ServerEventCases() {
	}

	private static void near(double expected, double actual, String what) {
		that(Math.abs(expected - actual) < 1e-9, "%s: %s, expected %s", what, actual, expected);
	}

	/** A good of the bumper harvest that {@code a} deals in (added to {@code a} and {@code b} when missing). */
	private static Identifier harvestGood(Market a, Market b, long now) {
		Identifier pick = null;
		for (Identifier id : MarketEvents.get(HARVEST).allGoods()) {
			if (a.good(id) != null && !a.isSpecial(id) && !b.isSpecial(id)) {
				pick = id;
				break;
			}
		}
		if (pick == null) pick = MarketEvents.get(HARVEST).allGoods().stream().filter(id -> !a.isSpecial(id) && !b.isSpecial(id)).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("every good of the bumper harvest is a special here"));
		for (Market m : List.of(a, b)) {
			if (m.good(pick) == null) m.addGood(pick, new GoodState(1.0, 0, GoodsCatalog.get(pick).targetStock(), now));
		}
		return pick;
	}

	private static MarketSnapshotS2C.GoodView view(MarketSnapshotS2C snapshot, Identifier good) {
		return snapshot.goods().stream().filter(v -> v.goods().equals(good)).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("the snapshot has no " + good));
	}

	private static MarketEvent with(MarketEvent e, long start, long end) {
		return new MarketEvent(e.id(), e.event(), e.market(), e.goods(), start, end, e.multiplier());
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("market_event", List.of("47-1", "47-2", "47-3", "47-4", "47-5"),
				"豊作のイベントを中心の市場で始めると、その品の基準価格が1日目は倍率のずれの 1/3（3日目から全部）だけ下がり（スナップショットの倍率と B）、300 ブロック先の市場には距離で弱めた分だけ効き、"
						+ "終わった日・events.enabled が OFF の時は元に戻ること。日が変わると次の日のイベントが予報として選ばれ、中心の近くのプレイヤーに予報と始まりがチャットで届き、掲示板のニュースの一覧に入ること。"
						+ "イベントの品は品不足と商会の動きに選ばれないこと（掲示板の画面の表示・ツールチップは見ていない）",
				ctx -> ServerChapterCases.markets(ctx, new int[] {0, 300}, (c, player, m) -> {
					Market a = m.get(0), b = m.get(1);
					MarketRegistry registry = MarketRegistry.get(c.level().getServer());
					List<MarketEvent> savedEvents = new ArrayList<>(registry.events());
					List<Campaign> savedCampaigns = new ArrayList<>(registry.campaigns());
					try {
						registry.events().clear();
						registry.campaigns().clear();
						long now = c.now();
						long today = EventService.day(now);
						Identifier good = harvestGood(a, b, now);
						for (Market x : List.of(a, b)) {
							x.clearShortages();
							x.trends().clear();
							MarketUpkeep.touch(c.levelOf(x), x);
						}
						ServerChapterCases.openAt(c, player, m, 0);
						double base = view(c.lastSnapshot(player), good).params().basePrice();

						// 47-1: today (the first day) a third of the deviation.
						MarketEvent e = EventService.startNow(c.level(), registry, HARVEST, a, now);
						that(e != null, "the bumper harvest could not start at A");
						that(e.goods().contains(good), "the event does not move %s: %s", good, e.goods());
						that(e.multiplier() >= 0.65 - 1e-9 && e.multiplier() <= 0.8 + 1e-9, "multiplier %s outside 0.65..0.80", e.multiplier());
						double first = EventMath.multiplier(e.multiplier(), 1, 1, 1.0 / 3);
						ServerChapterCases.openAt(c, player, m, 0);
						MarketSnapshotS2C.GoodView v = view(c.lastSnapshot(player), good);
						that(v.event().isPresent(), "the goods row has no event");
						that(HARVEST.equals(v.event().get().event()), "event on the goods row: %s", v.event().get().event());
						near(first, v.event().get().multiplier(), "event multiplier on the first day");
						that(Math.abs(v.params().basePrice() - base * first) < 1e-6, "B on the first day %.4f, expected %.4f", v.params().basePrice(), base * first);
						// From the third day the whole multiplier.
						registry.events().set(registry.events().indexOf(e), e = with(e, today - 2, today + 2));
						MarketUpkeep.touch(c.level(), a);
						near(e.multiplier(), a.eventMultiplierFor(good), "event multiplier on the third day");
						// 47-2: 300 blocks away, weaker by the distance (× the good's link).
						MarketUpkeep.touch(c.levelOf(b), b);
						TradeExpansionConfig cfg = TradeExpansionConfig.get();
						double w = DistanceModel.weight(a.horizontalDistanceTo(b.center()), cfg.events.nearRadius, cfg.events.farRadius, cfg.distance.falloffExponent)
								* GoodsCatalog.get(good).link();
						near(EventMath.multiplier(e.multiplier(), 1, w, 1), b.eventMultiplierFor(good), "event multiplier 300 blocks away");
						that(b.eventMultiplierFor(good) > e.multiplier() && b.eventMultiplierFor(good) < 1, "B is not weaker than the center");
						// 47-5: no shortage and no rival campaign of the good while the event runs.
						for (long period = 1; period <= 30; period++) {
							DemandService.roll(c.level(), a, period);
							that(!a.shortages().containsKey(good), "%s became short during the event (period %d)", good, period);
						}
						a.clearShortages();
						for (int i = 0; i < 20; i++) {
							Campaign camp = RivalService.startNow(c.level(), registry, a, now + i);
							if (camp == null) continue;
							registry.campaigns().remove(camp);
							that(!camp.goods().equals(good), "a rival campaign moved %s during the event", good);
						}
						// 47-4: events.enabled off, and the day the event is over: the old price.
						c.config(x -> x.events.enabled = false);
						MarketUpkeep.touch(c.level(), a);
						near(1.0, a.eventMultiplierFor(good), "event multiplier with events.enabled off");
						c.config(x -> x.events.enabled = true);
						registry.events().set(registry.events().indexOf(e), e = with(e, today - 3, today));
						MarketUpkeep.touch(c.level(), a);
						near(1.0, a.eventMultiplierFor(good), "event multiplier the day after the event");

						// 47-3: a new day rolls tomorrow's event (the forecast) and tells the players near its center.
						registry.events().clear();
						c.config(x -> {
							x.events.chancePerDay = 1;
							x.board.radius = 64; // only A is near a market visited lately, and the player stands there
						});
						for (Market other : registry.all()) if (other != a) other.setLastVisit(Long.MIN_VALUE);
						c.forget(player);
						registry.setEventDay(today - 1);
						EventService.advance(c.level(), registry, now);
						MarketEvent forecast = registry.events().stream().filter(x -> x.startDay() == today + 1).findFirst()
								.orElseThrow(() -> new AutotestCheck.Failed("no event was forecast for tomorrow: " + registry.events()));
						equal(a.id(), forecast.market(), "center of the forecast event");
						that(c.gotMessage(player, "message.ak_trade_expansion.event.forecast"), "no forecast in the chat: %s", c.messages(player));
						that(EventService.news(registry, a, 512, now).contains(forecast), "the forecast is not in the board news");
						MarketUpkeep.touch(c.level(), a);
						near(1.0, a.eventMultiplierFor(forecast.goods().getFirst()), "a forecast event already moves the price");
						// The next day it starts and is told again.
						c.config(x -> x.events.chancePerDay = 0);
						registry.events().set(registry.events().indexOf(forecast), with(forecast, today, today + forecast.days()));
						registry.setEventDay(today - 1);
						c.forget(player);
						EventService.advance(c.level(), registry, now);
						that(c.gotMessage(player, "message.ak_trade_expansion.event.start"), "no message when the event started: %s", c.messages(player));
						// The command lists it.
						c.forget(player);
						c.commandShown(player, "aktradeex events");
						that(c.gotMessage(player, "command.ak_trade_expansion.events.header"), "events command: %s", c.messages(player));
					} finally {
						registry.events().clear();
						registry.events().addAll(savedEvents);
						registry.campaigns().clear();
						registry.campaigns().addAll(savedCampaigns);
						for (Market x : registry.all()) x.eventMultipliers().clear();
						registry.setDirty();
					}
				})));
		return cases;
	}
}
