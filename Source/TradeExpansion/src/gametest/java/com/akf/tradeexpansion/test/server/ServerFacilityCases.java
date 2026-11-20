package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.QuestMath;
import com.akf.tradeexpansion.core.TrendMath;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.DevelopmentService;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.development.Festival;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.BoardRequestC2S;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.DevelopmentResultS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.data.RivalGuilds;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.build;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.buildAndComplete;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.builder;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.donate;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.town;

/**
 * Server cases of the leisure facilities (28) and the eight buildings of v0.51 (29): their effects (reputation,
 * development points, recovery, rival guilds, carriers, the board, goods, trends, festivals), the harbor at the water and
 * the tall towers. The look of the buildings (and water that must not spill) is left to the screenshots and to hand.
 */
final class ServerFacilityCases {
	private ServerFacilityCases() {
	}

	private static final Identifier QUEST_HALL = TradeExpansion.id("quest_hall");
	private static final Identifier WAREHOUSE = TradeExpansion.id("warehouse");
	private static final Identifier INN = TradeExpansion.id("inn");
	private static final Identifier BANK = TradeExpansion.id("bank");
	private static final Identifier FOUNTAIN = TradeExpansion.id("fountain");
	private static final Identifier PARK = TradeExpansion.id("park");
	private static final Identifier CLOCK_TOWER = TradeExpansion.id("clock_tower");
	private static final Identifier WATCHTOWER = TradeExpansion.id("watchtower");
	private static final Identifier TAVERN = TradeExpansion.id("tavern");
	private static final Identifier MARKET_HALL = TradeExpansion.id("market_hall");
	private static final Identifier HARBOR = TradeExpansion.id("harbor");
	private static final Identifier THEATER = TradeExpansion.id("theater");
	private static final Identifier HOT_SPRING = TradeExpansion.id("hot_spring");
	private static final Identifier FESTIVAL = TradeExpansion.id("festival");

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		leisure(cases);
		buildings(cases);
		carriers(cases);
		return cases;
	}

	// ---- 28. fountain and park

	private static void leisure(List<ServerCase> cases) {
		cases.add(full("fountain_reputation", List.of("28-7"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			c.giveMoney(player, 200_000);
			double perCoin = TradeExpansionConfig.get().reputation.pointsPerCoin;
			double k = Development.config().fountainReputationMultiplier;
			that(Math.abs(tradeGain(c, player, market) - perCoin) < 1e-9, "reputation per coin without a fountain: %.6f", tradeGain(c, player, market));
			buildAndComplete(c, player, market, FOUNTAIN);
			double with = tradeGain(c, player, market);
			that(Math.abs(with - perCoin * k) < 1e-9, "reputation per coin with a fountain: %.6f, expected %.6f", with, perCoin * k);
			// The sign gone, the effect ends.
			Facility f = market.facilities().get(FOUNTAIN);
			player.closeContainer();
			that(ServerDevelopmentCases.breakBlock(player, f.core()), "the fountain's sign could not be broken");
			that(!market.hasFacility(FacilityKind.FOUNTAIN), "the fountain still works without its sign");
			double after = tradeGain(c, player, market);
			that(Math.abs(after - perCoin) < 1e-9, "reputation per coin after the fountain's sign was broken: %.6f", after);
		})));
		cases.add(partial("park_points", List.of("28-8"),
				"公園がある村で 1,000c 寄付すると発展度が 1,250 増え（結果のパケットの値。チャットの文は見ていない）、評判の増え方は公園が無い時と同じで、噴水広場もあればその分だけ増えること",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					c.giveMoney(player, 10_000);
					double perPoint = Development.config().reputationPerPoint;
					buildAndComplete(c, player, market, PARK);
					double rep = Reputation.points(market, player.getUUID(), c.now());
					DevelopmentResultS2C r = donate(c, player, market, 1_000, false);
					that(r.result() == TradeResult.SUCCESS, "donating 1,000c gave %s", r.result());
					equal((long) Math.floor(1_000 * Development.config().parkPointsMultiplier), r.points(), "development points of 1,000c with a park");
					double gained = Reputation.points(market, player.getUUID(), c.now()) - rep;
					that(Math.abs(gained - 1_000 * perPoint) < 1e-6, "reputation of 1,000c with a park: %.4f, expected %.4f (as without one)", gained,
							1_000 * perPoint);
					buildAndComplete(c, player, market, FOUNTAIN);
					rep = Reputation.points(market, player.getUUID(), c.now());
					donate(c, player, market, 1_000, false);
					gained = Reputation.points(market, player.getUUID(), c.now()) - rep;
					double expected = 1_000 * perPoint * Development.config().fountainReputationMultiplier;
					that(Math.abs(gained - expected) < 1e-6, "reputation of 1,000c with a park and a fountain: %.4f, expected %.4f", gained, expected);
				})));
		cases.add(full("eight_kinds", List.of("28-9", "27-7"), ctx -> town(ctx, (c, player, market, stall) -> {
			// 27-7: donating makes the player a contributor ("investor"); completed buildings count for contributors.
			c.openStall(player, stall);
			c.giveMoney(player, 1_000);
			ServerAdvancementCases.notYet(c, player, "investor");
			DevelopmentService.handle(player, new DevelopmentRequestC2S(market.id(), DevelopmentRequestC2S.Action.DONATE_COINS, 100, "", false));
			ServerAdvancementCases.done(c, player, "investor", "after donating 100c");
			builder(c, player, market);
			List<Identifier> kinds = List.of(QUEST_HALL, WAREHOUSE, INN, BANK, FOUNTAIN, PARK, CLOCK_TOWER, TAVERN);
			for (int i = 0; i < kinds.size(); i++) {
				ServerAdvancementCases.notYet(c, player, "village_revival");
				buildAndComplete(c, player, market, kinds.get(i));
				if (i == 0) ServerAdvancementCases.done(c, player, "builder", "after the first building was completed");
			}
			ServerAdvancementCases.done(c, player, "village_revival", "with 8 kinds of buildings");
			// 28-9: the sign of a leisure building opens the development tab.
			for (Identifier id : List.of(FOUNTAIN, PARK)) {
				Facility f = market.facilities().get(id);
				player.closeContainer();
				c.emptyHand(player);
				c.click(player, f.core(), Direction.UP);
				that(player.containerMenu instanceof MarketMenu menu && menu.marketId() == market.id(), "the sign of %s opened no trade screen", id);
				that("town".equals(((MarketMenu) player.containerMenu).initialTab()), "the sign of %s opened the tab %s", id, ((MarketMenu) player.containerMenu).initialTab());
			}
		})));
	}

	// ---- 29. the eight buildings

	private static void buildings(List<ServerCase> cases) {
		cases.add(partial("watchtower_ladder_and_height", List.of("29-3"),
				"見張り塔（高さ21）と時計塔（高さ22）が建ち、見張り塔には床から見張り台までつながったはしごがあること。杭の上の高い所（20ブロック上）にブロックがあると「空間がありません」で断られること（本物のプレイヤーが上れるかは見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					BlockPos spot = stall.south(14);
					stakeFacing(c, player, spot, Direction.NORTH);
					BlockPos high = spot.south(2).above(20);
					// Leaves: a branch overhead (a floating solid block would count as the ground of its column, "uneven").
					c.level().setBlock(high, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2 | 16);
					try {
						c.forget(player);
						that(build(c, player, market, WATCHTOWER) == TradeResult.MARKER_BLOCKED, "a watchtower was built under leaves 20 high");
						that("message.ak_trade_expansion.facility.site.space".equals(blockedReason(c, player)), "the blocked stake did not say \"no space\": %s",
								blockedReason(c, player));
					} finally {
						c.level().setBlock(high, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
					buildAndComplete(c, player, market, WATCHTOWER);
					Facility tower = market.facilities().get(WATCHTOWER);
					equal(21, tower.box().getYSpan(), "height of the watchtower");
					ladder(c, tower);
					buildAndComplete(c, player, market, CLOCK_TOWER);
					equal(22, market.facilities().get(CLOCK_TOWER).box().getYSpan(), "height of the clock tower");
				})));
		cases.add(partial("harbor_at_the_shore", List.of("29-4"),
				"陸だけの所の杭では「奥側が水の上にありません」で断られ、岸の杭では手前（6列）が陸・奥が水の上に建ち、桟橋の下が水底まで埋まること（自動の場所探しは harbor_effects で見る。形は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					BlockPos spot = stall.south(14);
					stakeFacing(c, player, spot, Direction.NORTH);
					c.forget(player);
					that(build(c, player, market, HARBOR) == TradeResult.MARKER_BLOCKED, "a harbor was built on dry land");
					that("message.ak_trade_expansion.facility.site.water".equals(blockedReason(c, player)), "the stake on land did not say \"not over water\": %s",
							blockedReason(c, player));
					Runnable restore = pool(c, stall, -8, 8, 20, 32);
					try {
						buildAndComplete(c, player, market, HARBOR);
						Facility f = market.facilities().get(HARBOR);
						that(f.front().equals(spot), "the harbor's front is at %s, not at the stake %s", f.front().toShortString(), spot.toShortString());
						BoundingBox box = f.box();
						equal(spot.getZ(), box.minZ(), "the harbor's front row");
						equal(spot.getZ() + 12, box.maxZ(), "the harbor's back row");
						int floorY = stall.getY() - 1;
						// The rows on land stand on the stone floor; under the deck over the water the piers reach the bottom.
						int piers = 0;
						for (int x = box.minX(); x <= box.maxX(); x++) {
							for (int z = spot.getZ() + 6; z <= box.maxZ(); z++) {
								BlockState bottom = c.level().getBlockState(new BlockPos(x, floorY - 1, z));
								if (!bottom.isAir() && bottom.getFluidState().isEmpty()) piers++;
							}
						}
						if (piers == 0) {
							java.util.Map<String, Integer> deck = new java.util.TreeMap<>(), under = new java.util.TreeMap<>();
							for (int x = box.minX(); x <= box.maxX(); x++) {
								for (int z = spot.getZ() + 6; z <= box.maxZ(); z++) {
									deck.merge(c.level().getBlockState(new BlockPos(x, box.minY(), z)).getBlock().toString(), 1, Integer::sum);
									under.merge(c.level().getBlockState(new BlockPos(x, box.minY() - 1, z)).getBlock().toString(), 1, Integer::sum);
								}
							}
							throw new AutotestCheck.Failed("no pier reaches the bottom of the water (box " + box + ", floor " + floorY + "): deck " + deck + ", under " + under);
						}
					} finally {
						restore.run();
					}
				})));
		cases.add(full("clock_tower_recovery", List.of("29-6"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			Identifier good = c.tradableGood(market, 0, 16);
			double h = market.params(good).halfLifeDays();
			buildAndComplete(c, player, market, CLOCK_TOWER);
			double k = Development.config().clockTowerRecoveryMultiplier;
			double h2 = market.params(good).halfLifeDays();
			that(Math.abs(h2 - h * k) < 1e-9, "half-life of %s with a clock tower: %.3f days, expected %.3f (%.3f x %.2f)", good, h2, h * k, h, k);
			ServerDevelopmentCases.ensureOpen(c, player, market);
			ServerCases.giveItems(player, c.item(good), 16);
			c.trade(player, market, good, false, 16);
			that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling gave %s", c.lastTradeResult(player));
			double x0 = market.good(good).pressure();
			that(x0 > 0, "selling did not move the price");
			c.command(player, "aktradeex clock advance " + h2);
			MarketUpkeep.touch(c.level(), market);
			double x1 = market.good(good).pressure();
			that(Math.abs(x1 / x0 - 0.5) < 0.02, "after %.3f days (the shorter half-life) the pressure went %.3f -> %.3f", h2, x0, x1);
			// The screen computes the same price as the server: buying with the previewed total is not refused.
			ServerDevelopmentCases.ensureOpen(c, player, market);
			c.giveMoney(player, 100_000);
			Identifier dear = c.tradableGood(market, 4, 0);
			long expected = c.previewTotal(player, market, dear, true, 4);
			TradeResult r = c.tradeRaw(player, market.id(), dear, true, 4, expected, false, false);
			that(r == TradeResult.SUCCESS, "buying with the previewed %dc at a clock tower market gave %s", expected, r);
		})));
		cases.add(partial("watchtower_effects", List.of("29-7"),
				"見張り塔の村では、商会の動きの割合（重み）が半分になり、そこを行き来する運び屋の事故の倍率が 0.5 になること（事故の回数・値動きの大きさは数えていない。倍率で確かめる）",
				ctx -> townMarkets(ctx, new int[] {0, 600}, (c, player, m) -> {
					Market a = m.get(0), b = m.get(1);
					builder(c, player, a);
					MarketRegistry registry = c.registry();
					Identifier good = ServerChapterCases.shared(c, a, 1, 0, b);
					Campaign camp = campaign(c, a, good);
					double before = RivalService.weight(registry, a, camp);
					that(before == 1.0, "weight of the campaign's own market: %s", before);
					buildAndComplete(c, player, a, WATCHTOWER);
					double k = Development.config().watchtowerRivalMultiplier;
					that(Math.abs(RivalService.weight(registry, a, camp) - k) < 1e-9, "weight with a watchtower: %.3f, expected %.3f",
							RivalService.weight(registry, a, camp), k);
					StaffS2C.View e = hireCarrier(c, player, a, b);
					that(Math.abs(e.accidentMultiplier() - Development.config().watchtowerAccidentMultiplier) < 1e-9, "accident multiplier of a carrier from a watchtower village: %s",
							e.accidentMultiplier());
					that(Math.abs(e.travelMultiplier() - 1.0) < 1e-9, "travel multiplier without a harbor: %s", e.travelMultiplier());
				})));
		cases.add(full("tavern_board", List.of("29-8"), ctx -> townMarkets(ctx, new int[] {0, 700}, (c, player, m) -> {
			Market a = m.get(0), b = m.get(1);
			int radius = TradeExpansionConfig.get().board.radius;
			double distance = a.horizontalDistanceTo(b.center());
			that(distance > radius && distance <= radius * Development.config().tavernBoardMultiplier, "the test markets are %.0f apart (board radius %d)", distance, radius);
			builder(c, player, a);
			that(board(c, player, a).markets().stream().noneMatch(e -> e.id() == b.id()), "the board lists a market %.0f blocks away without a tavern", distance);
			buildAndComplete(c, player, a, TAVERN);
			ServerDevelopmentCases.ensureOpen(c, player, a);
			BoardS2C board = board(c, player, a);
			that(board.markets().stream().anyMatch(e -> e.id() == b.id()), "the board does not list the market %.0f blocks away with a tavern", distance);
		})));
		cases.add(full("market_hall_goods", List.of("29-9"), ctx -> town(ctx, (c, player, market, stall) -> {
			c.config(cfg -> cfg.market.shrinkOnLevelDown = true);
			builder(c, player, market);
			int goods = market.regularGoods().size();
			c.forget(player);
			buildAndComplete(c, player, market, MARKET_HALL);
			that(c.gotMessage(player, "message.ak_trade_expansion.market_grew"), "no \"market grew\" message: %s", c.messages(player));
			int extra = Development.config().marketHallExtraGoods;
			equal(goods + extra, market.regularGoods().size(), "goods with a market hall");
			player.closeContainer();
			c.forget(player);
			that(ServerDevelopmentCases.breakBlock(player, market.facilities().get(MARKET_HALL).core()), "the market hall's sign could not be broken");
			that(c.gotMessage(player, "message.ak_trade_expansion.market_shrank"), "no \"market shrank\" message: %s", c.messages(player));
			equal(goods, market.regularGoods().size(), "goods after the market hall's sign was broken");
		})));
		cases.add(partial("theater_trends", List.of("29-11"),
				"野外劇場の村では、人気の分野の倍率が 1+(m−1)×1.5、余り気味の分野が 1−(1−m)÷1.5 になって商品の値段に効き、交易画面にもその倍率が送られること（掲示板の流行の表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					// Two groups of the market's goods: one in fashion, one out of fashion.
					List<Identifier> groups = market.regularGoods().stream().flatMap(g -> GoodsGroups.groupsOf(g).stream()).distinct().toList();
					that(groups.size() >= 2, "the market's goods have fewer than 2 groups: %s", groups);
					Identifier up = groups.get(0), down = groups.get(1);
					buildAndComplete(c, player, market, THEATER);
					double k = Development.config().theaterTrendMultiplier;
					market.trends().clear(); // set after building: completing touches the market (a new trend period may start)
					market.trends().put(up, 1.3);
					market.trends().put(down, 0.8);
					int checked = 0;
					for (Identifier g : market.regularGoods()) {
						var def = com.akf.tradeexpansion.data.GoodsCatalog.get(g);
						if (def == null || def.autoAdd().isPresent() || market.isSpecial(g)) continue; // no trends (P)
						double expected = 1;
						for (Identifier group : GoodsGroups.groupsOf(g)) {
							Double t = market.trends().get(group);
							if (t != null) expected *= TrendMath.theater(t, k);
						}
						if (expected != 1) checked++;
						that(Math.abs(market.trendMultiplier(g) - expected) < 1e-9, "trend multiplier of %s with a theater: %.4f, expected %.4f", g,
								market.trendMultiplier(g), expected);
					}
					that(checked > 0, "no good of the market has a trend");
					that(TrendMath.theater(1.3, k) > 1.3 && TrendMath.theater(0.8, k) > 0.8 && TrendMath.theater(0.8, k) < 1, "theater(%s): %.3f / %.3f", k,
							TrendMath.theater(1.3, k), TrendMath.theater(0.8, k));
					MarketSnapshotS2C snap = snapshot(c, player, stall);
					Map<Identifier, Double> sent = new LinkedHashMap<>();
					snap.trends().forEach(t -> sent.put(t.group(), t.multiplier()));
					that(Math.abs(sent.getOrDefault(up, 0.0) - TrendMath.theater(1.3, k)) < 1e-9, "trend in fashion sent: %s", sent);
					that(Math.abs(sent.getOrDefault(down, 0.0) - TrendMath.theater(0.8, k)) < 1e-9, "trend out of fashion sent: %s", sent);
				})));
		cases.add(partial("festival_days", List.of("29-13"),
				"祭りの間隔 2日で、祭りの日に交易画面を開くと「今日は〇〇の祭りの日です」が届き、寄付の発展度と評判が2倍（公園があればさらに1.25倍）、発展のタブに「今日は祭り」（0日）、次の日は「祭りまで1日」で普通に戻ること（タブの文は見ていない）",
				ctx -> {
					ctx.config(cfg -> cfg.development.festivalIntervalDays = 2);
					town(ctx, (c, player, market, stall) -> {
						builder(c, player, market);
						c.giveMoney(player, 10_000);
						buildAndComplete(c, player, market, FESTIVAL);
						int days = Festival.daysUntil(market, c.now());
						that(days >= 0 && days < 2, "days until the festival: %d", days);
						if (days > 0) c.command(player, "aktradeex clock advance " + days);
						that(Festival.today(market, c.now()), "no festival after %d days", days);
						player.closeContainer();
						c.forget(player);
						MarketSnapshotS2C snap = snapshot(c, player, stall);
						that(c.gotMessage(player, "message.ak_trade_expansion.festival.today"), "no festival message when the screen opened: %s", c.messages(player));
						equal(0, snap.development().festivalIn(), "days until the festival sent on the festival day");
						double festival = Development.config().festivalMultiplier;
						double perPoint = Development.config().reputationPerPoint;
						double rep = Reputation.points(market, player.getUUID(), c.now());
						DevelopmentResultS2C r = donate(c, player, market, 100, false);
						equal((long) Math.floor(100 * festival), r.points(), "development points of 100c on the festival day");
						double gained = Reputation.points(market, player.getUUID(), c.now()) - rep;
						that(Math.abs(gained - 100 * perPoint * festival) < 1e-6, "reputation of 100c on the festival day: %.4f", gained);
						buildAndComplete(c, player, market, PARK);
						that(Festival.today(market, c.now()), "the festival ended while the park was built");
						r = donate(c, player, market, 100, false);
						equal((long) Math.floor(100 * festival * Development.config().parkPointsMultiplier), r.points(), "development points of 100c on the festival day with a park");
						// The next day is an ordinary day.
						c.command(player, "aktradeex clock advance 1");
						player.closeContainer();
						c.forget(player);
						snap = snapshot(c, player, stall);
						that(!c.gotMessage(player, "message.ak_trade_expansion.festival.today"), "a festival message on the next day");
						equal(1, snap.development().festivalIn(), "days until the festival sent on the next day");
						r = donate(c, player, market, 100, false);
						equal((long) Math.floor(100 * Development.config().parkPointsMultiplier), r.points(), "development points of 100c on an ordinary day with a park");
					});
				}));
	}

	// ---- 29-10, 29-12: harbor and hot spring (carriers and transport requests)

	private static void carriers(List<ServerCase> cases) {
		cases.add(partial("harbor_effects", List.of("29-4", "29-10"),
				"自動の場所探しで岸が見つかって港が建つこと。港のある村の新しい輸送依頼の報酬が 1.25 倍になり、そこを行き来する運び屋の移動時間の倍率が 0.75 になり、実際に出かけた運び屋が、距離の時間の 0.75 倍で着くこと（その200ティック前はまだ運送中、200ティック後には着いている）",
				ctx -> {
					ctx.config(cfg -> cfg.staff.accidentBase = 0);
					townMarkets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						BlockPos center = c.center(); // the first market
						builder(c, player, a);
						Runnable restore = pool(c, center, -8, 8, 20, 32);
						boolean[] restored = {false};
						try {
							checkTransportReward(c, a, b, 1.0);
							buildAndComplete(c, player, a, HARBOR);
							BoundingBox box = a.facilities().get(HARBOR).box();
							boolean overWater = false;
							for (int x = box.minX(); x <= box.maxX() && !overWater; x++) {
								for (int z = box.minZ(); z <= box.maxZ() && !overWater; z++) {
									overWater = c.level().getFluidState(new BlockPos(x, center.getY() - 2, z)).is(FluidTags.WATER);
								}
							}
							that(overWater, "the harbor found by the site search does not stand over the water: %s", box);
							checkTransportReward(c, a, b, Development.config().harborRewardMultiplier);
							StaffS2C.View e = hireCarrier(c, player, a, b);
							that(Math.abs(e.travelMultiplier() - Development.config().harborTravelMultiplier) < 1e-9, "travel multiplier of a carrier from a harbor: %s",
									e.travelMultiplier());
							// The trip itself: the carrier sets out and arrives after 0.75 of the time of the distance.
							c.giveMoney(player, 20_000);
							Identifier good = ServerChapterCases.shared(c, a, 8, 0, b);
							a.good(good).setPressure(a.params(good).maxPressure() * 0.8); // cheap at A
							b.good(good).setPressure(b.params(good).minPressure() * 0.8); // dear at B: worth carrying
							that(ServerChapterCases.staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 5_000, false)
									== TradeResult.SUCCESS, "depositing 5,000c failed");
							c.command(player, "aktradeex staff hurry"); // buys and sets out
							e = ServerChapterCases.only(c, player, a);
							equal(Employee.Phase.OUTBOUND.ordinal(), e.phase(), "phase after setting out from a harbor village");
							long plain = com.akf.tradeexpansion.core.StaffMath.travelTicks(MarketRegistry.linkedDistance(a, b), TradeExpansionConfig.get().staff.carrierBlocksPerDay);
							long expected = Math.round(plain * Development.config().harborTravelMultiplier);
							equal(expected, e.nextTime() - e.phaseStart(), "ticks of the trip from a harbor village (" + plain + " without a harbor)");
							// The carriers move every 20 ticks: 200 ticks before the end it is still on the road, 200 after it arrived.
							c.command(player, String.format(java.util.Locale.ROOT, "aktradeex clock advance %.6f", (expected - 200 - 40) / (double) PriceModel.TICKS_PER_DAY));
							long plainTicks = plain, harborTicks = expected;
							c.later(40, c2 -> {
								equal(Employee.Phase.OUTBOUND.ordinal(), ServerChapterCases.only(c2, player, a).phase(), "phase 200 ticks before the harbor trip ends");
								c2.command(player, String.format(java.util.Locale.ROOT, "aktradeex clock advance %.6f", (400 - 40) / (double) PriceModel.TICKS_PER_DAY));
							});
							c.later(40, c2 -> {
								StaffS2C.View v = ServerChapterCases.only(c2, player, a);
								that(v.phase() != Employee.Phase.OUTBOUND.ordinal(), "the carrier from a harbor village has not arrived 200 ticks after %d ticks (%d without a harbor)",
										harborTicks, plainTicks);
								that(plainTicks - harborTicks > 400, "the harbor saves only %d ticks", plainTicks - harborTicks);
							});
							c.later(1, c2 -> restore.run());
							restored[0] = true;
						} finally {
							if (!restored[0]) restore.run();
						}
					});
				}));
		cases.add(partial("hot_spring_rest", List.of("29-12"),
				"温泉のある村で雇った運び屋が事故に遭うと、療養の時間が半分（0.25日）になること（辞める確率は見ていない。既定では辞めない）",
				ctx -> {
					ctx.config(cfg -> {
						cfg.staff.accidentBase = 1;
						cfg.staff.accidentMax = 1;
						cfg.staff.quitChance = 0;
						cfg.staff.carrierBackhaul = false;
					});
					townMarkets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						builder(c, player, a);
						buildAndComplete(c, player, a, HOT_SPRING);
						ServerDevelopmentCases.ensureOpen(c, player, a);
						c.giveMoney(player, 20_000);
						Identifier good = ServerChapterCases.shared(c, a, 8, 0, b);
						a.good(good).setPressure(a.params(good).maxPressure() * 0.8); // cheap at A
						b.good(good).setPressure(b.params(good).minPressure() * 0.8); // dear at B: worth carrying
						StaffS2C.View e = hireCarrier(c, player, a, b);
						that(ServerChapterCases.staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 5_000, false)
								== TradeResult.SUCCESS, "depositing 5,000c failed");
						c.command(player, "aktradeex staff hurry"); // buys and sets out
						e = ServerChapterCases.only(c, player, a);
						that(e.stock() > 0, "the carrier set out with nothing (status %s)", Employee.Status.values()[e.status()]);
						c.command(player, "aktradeex staff hurry"); // arrives: an accident
						e = ServerChapterCases.only(c, player, a);
						equal(Employee.Status.RESTING.ordinal(), e.status(), "status after an accident (chance 1)");
						long rest = Math.round(TradeExpansionConfig.get().staff.accidentRestDays * Development.config().hotSpringRestMultiplier * PriceModel.TICKS_PER_DAY);
						that(Math.abs(e.nextTime() - c.now() - rest) <= 1, "rest after an accident: %d ticks, expected %d (half)", e.nextTime() - c.now(), rest);
					});
				}));
	}

	// ---- helpers

	/** The reason (translation key) of the last "the stake is blocked" message, or {@code null}. */
	private static String blockedReason(ServerCtx c, ServerPlayer player) {
		String reason = null;
		for (Object msg : c.sent(player)) {
			if (msg instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p
					&& p.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
					&& t.getKey().equals("message.ak_trade_expansion.facility.marker_blocked") && t.getArgs().length > 0
					&& t.getArgs()[0] instanceof net.minecraft.network.chat.Component arg
					&& arg.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents r) {
				reason = r.getKey();
			}
		}
		return reason;
	}

	/** {@link ServerChapterCases#markets} with the large floor of {@link ServerDevelopmentCases#town} around the first market. */
	private static void townMarkets(ServerCtx ctx, int[] offsets, ServerChapterCases.MarketsBody body) {
		for (int dx = -24; dx <= 24; dx += 24) {
			for (int dz = -24; dz <= 24; dz += 24) {
				if (dx != 0 || dz != 0) ctx.floor(ctx.center().offset(dx, 0, dz));
			}
		}
		ctx.config(cfg -> cfg.development.requireMaterials = false);
		ServerChapterCases.markets(ctx, offsets, body);
	}

	/** Reputation gained per coin of one purchase. */
	private static double tradeGain(ServerCtx c, ServerPlayer player, Market market) {
		ServerDevelopmentCases.ensureOpen(c, player, market);
		Identifier good = c.tradableGood(market, 4, 0);
		double before = Reputation.points(market, player.getUUID(), c.now());
		long total = c.trade(player, market, good, true, 4);
		that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying gave %s", c.lastTradeResult(player));
		return (Reputation.points(market, player.getUUID(), c.now()) - before) / total;
	}

	/** A building site stake at {@code pos} whose front faces {@code facing} (the player looks the other way). */
	private static void stakeFacing(ServerCtx c, ServerPlayer player, BlockPos pos, Direction facing) {
		player.closeContainer();
		Direction look = facing.getOpposite();
		player.teleportTo(pos.getX() + 0.5 - look.getStepX() * 2, pos.getY(), pos.getZ() + 0.5 - look.getStepZ() * 2);
		player.setYRot(look.toYRot());
		player.setYHeadRot(look.toYRot());
		ServerDevelopmentCases.placeStake(c, player, pos);
		BlockState state = c.level().getBlockState(pos);
		that(state.is(ModBlocks.CONSTRUCTION_MARKER), "the stake was not placed at %s", pos.toShortString());
		that(state.getValue(HorizontalDirectionalBlock.FACING) == facing, "the stake faces %s, not %s", state.getValue(HorizontalDirectionalBlock.FACING), facing);
	}

	/** A ladder column from the floor of the tower to its top (at least 15 rungs in a row). */
	private static void ladder(ServerCtx c, Facility tower) {
		BoundingBox box = tower.box();
		int best = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int run = 0, start = -1;
				for (int y = box.minY(); y <= box.maxY(); y++) {
					if (c.level().getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) {
						if (run == 0) start = y;
						run++;
						if (run > best && start <= box.minY() + 2) best = run;
					} else {
						run = 0;
					}
				}
			}
		}
		if (best >= 13) return; // rungs 1..13: from the floor up through the platform (y 13)
		// Tell what stands in the column with the most ladders.
		StringBuilder column = new StringBuilder();
		int most = -1;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int n = 0;
				for (int y = box.minY(); y <= box.maxY(); y++) if (c.level().getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) n++;
				if (n <= most) continue;
				most = n;
				column.setLength(0);
				for (int y = box.minY(); y <= box.minY() + 14; y++) {
					column.append(y - box.minY()).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(new BlockPos(x, y, z)).getBlock()).getPath())
							.append(' ');
				}
			}
		}
		throw new AutotestCheck.Failed("the watchtower's longest ladder from the floor has " + best + " rungs: " + column);
	}

	/**
	 * Water two blocks deep in place of the floor at {@code xMin..xMax}, {@code zMin..zMax} from {@code center} (in a
	 * stone basin). @return puts the blocks back (call it before the case ends: the clean-up does not reach below the floor)
	 */
	private static Runnable pool(ServerCtx c, BlockPos center, int xMin, int xMax, int zMin, int zMax) {
		int floorY = center.getY() - 1;
		Map<BlockPos, BlockState> saved = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(center.getX() + xMin - 1, floorY - 2, center.getZ() + zMin - 1, center.getX() + xMax + 1, floorY,
				center.getZ() + zMax + 1)) {
			saved.put(p.immutable(), c.level().getBlockState(p));
		}
		saved.keySet().forEach(p -> {
			boolean inside = p.getX() >= center.getX() + xMin && p.getX() <= center.getX() + xMax && p.getZ() >= center.getZ() + zMin
					&& p.getZ() <= center.getZ() + zMax;
			if (p.getY() == floorY - 2 || !inside) {
				if (p.getY() < floorY || c.level().getBlockState(p).isAir()) c.level().setBlock(p, Blocks.STONE.defaultBlockState(), 2 | 16);
			}
		});
		saved.keySet().forEach(p -> {
			boolean inside = p.getX() >= center.getX() + xMin && p.getX() <= center.getX() + xMax && p.getZ() >= center.getZ() + zMin
					&& p.getZ() <= center.getZ() + zMax;
			if (inside && p.getY() > floorY - 2) c.level().setBlock(p, Blocks.WATER.defaultBlockState(), 2 | 16);
		});
		return () -> {
			// The harbor's piers stand in the water: remove them with it.
			for (BlockPos p : BlockPos.betweenClosed(center.getX() + xMin - 1, floorY - 2, center.getZ() + zMin - 1, center.getX() + xMax + 1, floorY,
					center.getZ() + zMax + 1)) {
				c.level().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			saved.forEach((p, s) -> c.level().setBlock(p, s, 2 | 16));
		};
	}

	/** A new transport request from {@code a} to {@code b} pays the value × the distance multiplier × {@code factor}. */
	private static void checkTransportReward(ServerCtx c, Market a, Market b, double factor) {
		a.requests().removeIf(Request::isTransport);
		a.setLastRequestRefresh(Long.MIN_VALUE);
		MarketUpkeep.touch(c.level(), a);
		Request r = a.requests().stream().filter(q -> q.isTransport() && q.transport().get().destination() == b.id()).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("market A made no transport request to B: " + a.requests()));
		TradeExpansionConfig.Quest q = TradeExpansionConfig.get().quest;
		double value = a.basePriceFor(r.goods()) * r.quantity();
		double multiplier = QuestMath.transportMultiplier(MarketRegistry.linkedDistance(a, b), q.transportBaseMultiplier, q.transportPerThousandBlocks,
				q.transportMaxMultiplier) * factor;
		long expected = Math.max(1, (long) Math.ceil(value * multiplier - 1e-9));
		equal(expected, r.reward(), "reward of a transport request of " + r.quantity() + " " + r.goods() + " (x" + factor + ")");
	}

	/** Hires an automatic carrier at {@code a} for trips to {@code b}; returns it as the staff tab lists it. */
	private static StaffS2C.View hireCarrier(ServerCtx c, ServerPlayer player, Market a, Market b) {
		ServerDevelopmentCases.ensureOpen(c, player, a);
		c.command(player, "aktradeex reputation set 150");
		c.giveMoney(player, TradeExpansionConfig.get().staff.carrierHireFee + 1_000);
		TradeResult r = ServerChapterCases.staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
		that(r == TradeResult.SUCCESS, "hiring a carrier gave %s", r);
		return ServerChapterCases.only(c, player, a);
	}

	/** A rival campaign on {@code good} centered on {@code center} (not pushed: only its weight is looked at). */
	private static Campaign campaign(ServerCtx c, Market center, Identifier good) {
		long today = RivalService.day(MarketUpkeep.touch(c.level(), center));
		Identifier guild = RivalGuilds.all().keySet().iterator().next();
		return new Campaign(c.registry().takeCampaignId(), guild, good, center.id(), true, today, today + 3, 10);
	}

	private static BoardS2C board(ServerCtx c, ServerPlayer player, Market market) {
		c.forget(player);
		com.akf.tradeexpansion.board.BoardService.handle(player, new BoardRequestC2S(market.id()));
		List<BoardS2C> list = c.payloads(player, BoardS2C.class);
		that(!list.isEmpty(), "no board was sent");
		return list.getLast();
	}

	private static MarketSnapshotS2C snapshot(ServerCtx c, ServerPlayer player, BlockPos stall) {
		player.closeContainer();
		player.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		c.openStall(player, stall);
		MarketSnapshotS2C snap = c.lastSnapshot(player);
		that(snap != null, "no snapshot when the screen opened");
		return snap;
	}
}
