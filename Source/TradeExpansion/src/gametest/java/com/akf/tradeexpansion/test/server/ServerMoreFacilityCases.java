package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.QuestMath;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.market.TradeRoutes;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.quest.DemandService;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.staff.StaffService;
import com.akf.tradeexpansion.villager.VillagerCoins;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.buildAndComplete;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.builder;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.town;

/** Checklist 39: the eight buildings of v0.79 (station, appraiser, wholesaler, guild hall, chapel, town hall, workshop, school). */
final class ServerMoreFacilityCases {
	private static final Identifier STATION = TradeExpansion.id("station");
	private static final Identifier APPRAISER = TradeExpansion.id("appraiser");
	private static final Identifier WHOLESALER = TradeExpansion.id("wholesaler");
	private static final Identifier GUILD_HALL = TradeExpansion.id("guild_hall");
	private static final Identifier CHAPEL = TradeExpansion.id("chapel");
	private static final Identifier TOWN_HALL = TradeExpansion.id("town_hall");
	private static final Identifier WORKSHOP = TradeExpansion.id("workshop");
	private static final Identifier SCHOOL = TradeExpansion.id("school");
	private static final Identifier WAREHOUSE = TradeExpansion.id("warehouse");

	private ServerMoreFacilityCases() {
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("station_routes", List.of("39-2"),
				"2つの市場（600ブロック、遠い方の端を 400 にした設定）の間に道標を並べた交易路で、Aで売った相場の変化がBに 50% 伝わり、Aに駅舎が建つと 75% 伝わり、"
						+ "Aの交易路の上限が 4 + 2 = 6 本になること（道標は保存データに登録した。5本目より多い交易路を実際につないではいない）",
				ctx -> {
					ctx.config(cfg -> cfg.distance.farRadius = 400);
					ServerFacilityCases.townMarkets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						builder(c, player, a);
						MarketRegistry registry = c.registry();
						Identifier good = ServerChapterCases.shared(c, a, 0, 64, b);
						for (int x = 40; x <= 580; x += 60) TradeRoutes.onPlaced(c.level(), a.center().east(x), null);
						that(TradeRoutes.partners(registry, a).equals(List.of(b)), "partners of A: %s", TradeRoutes.partners(registry, a));
						double link = GoodsCatalog.get(good).link();
						ServerChapterCases.openAt(c, player, m, 0);
						double moved = ServerRouteCases.sellAndMeasure(c, player, a, b, good, 16);
						that(Math.abs(moved - 16 * 0.5 * link) < 0.5, "without a station B's X moved %.2f, expected %.2f", moved, 16 * 0.5 * link);
						equal(4, TradeRoutes.maxRoutes(a), "routes of A without a station");

						buildAndComplete(c, player, a, STATION);
						double w = Development.config().stationRoadWeight;
						that(Math.abs(TradeRoutes.roadWeight(a, b) - w) < 1e-9, "road weight with a station: %.3f", TradeRoutes.roadWeight(a, b));
						equal(4 + Development.config().stationExtraRoutes, TradeRoutes.maxRoutes(a), "routes of A with a station");
						equal(4, TradeRoutes.maxRoutes(b), "routes of B (no station)");
						that(TradeRoutes.partners(registry, a).equals(List.of(b)), "partners of A after the station: %s", TradeRoutes.partners(registry, a));
						ServerChapterCases.openAt(c, player, m, 0);
						moved = ServerRouteCases.sellAndMeasure(c, player, a, b, good, 16);
						that(Math.abs(moved - 16 * w * link) < 0.5, "with a station B's X moved %.2f, expected %.2f", moved, 16 * w * link);
						for (int x = 40; x <= 580; x += 60) TradeRoutes.onRemoved(c.level(), a.center().east(x));
					});
				}));
		cases.add(partial("appraiser_specials", List.of("39-3"),
				"目利きの店が建つと、日の入れ替えで本日の掘り出し物が1つ増え、掘り出し物の基準価格の倍率が「掘り出し物の価格倍率」（1.2）ではなく目利きの店の倍率（1.0）になり、"
						+ "その倍率を 2 にすると掘り出し物の基準価格が2倍になること（画面の★の表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					long now = MarketUpkeep.touch(c.level(), market);
					market.setSpecialDay(Long.MIN_VALUE);
					MarketFactory.refreshSpecials(c.level(), market, now);
					int before = market.specials().size();
					that(Math.abs(market.specialPriceMultiplier() - TradeExpansionConfig.get().market.specialPriceMultiplier) < 1e-9,
							"special multiplier without an appraiser: %s", market.specialPriceMultiplier());
					buildAndComplete(c, player, market, APPRAISER);
					now = MarketUpkeep.touch(c.level(), market);
					market.setSpecialDay(Long.MIN_VALUE);
					MarketFactory.refreshSpecials(c.level(), market, now);
					equal(before + Development.config().appraiserExtraSpecials, market.specials().size(), "specials with an appraiser");
					that(Math.abs(market.specialPriceMultiplier() - Development.config().appraiserSpecialPriceMultiplier) < 1e-9,
							"special multiplier with an appraiser: %s", market.specialPriceMultiplier());
					Identifier special = market.specials().iterator().next();
					double b1 = market.params(special).basePrice();
					c.config(cfg -> cfg.development.appraiserSpecialPriceMultiplier = 2.0);
					double b2 = market.params(special).basePrice();
					that(Math.abs(b2 / b1 - 2.0) < 1e-6, "B of the special %s: %.3f with 1.0, %.3f with 2.0", special, b1, b2);
				})));
		cases.add(partial("wholesaler_shortages", List.of("39-4"),
				"問屋が建つと、品不足の入れ替えで品不足になる品が1つ増えること（品不足を満たした時の発展度の倍率は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					long now = MarketUpkeep.touch(c.level(), market);
					long period = QuestMath.period(now, TradeExpansionConfig.get().demand.durationDays);
					DemandService.roll(c.level(), market, period);
					int before = market.shortages().size();
					equal(TradeExpansionConfig.get().demand.maxShortages, before, "shortages without a wholesaler");
					buildAndComplete(c, player, market, WHOLESALER);
					DemandService.roll(c.level(), market, period);
					equal(before + Development.config().wholesalerExtraShortages, market.shortages().size(), "shortages with a wholesaler");
				})));
		cases.add(partial("guild_hall_clerks", List.of("39-5"),
				"商人組合の会館が建つと、その市場の番頭の1日の給金が 0.75 倍（雇い人タブの説明にも同じ額）になり、番頭の売り1個が相場を動かす量が 1 ÷ 1.5 になること"
						+ "（番頭を雇って実際に売れた数は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					long base = TradeExpansionConfig.get().staff.clerkWagePerDay;
					equal(base, StaffService.clerkWage(market), "clerk wage without a guild hall");
					that(StaffService.clerkImpact(market) == 1.0, "clerk impact without a guild hall: %s", StaffService.clerkImpact(market));
					buildAndComplete(c, player, market, GUILD_HALL);
					long wage = Math.round(base * Development.config().guildHallClerkWageMultiplier);
					equal(wage, StaffService.clerkWage(market), "clerk wage with a guild hall");
					that(Math.abs(StaffService.clerkImpact(market) - 1 / Development.config().guildHallClerkSpeedMultiplier) < 1e-9,
							"clerk impact with a guild hall: %s", StaffService.clerkImpact(market));
					ServerDevelopmentCases.ensureOpen(c, player, market);
					c.forget(player);
					StaffService.handle(player, new StaffRequestC2S(market.id(), StaffRequestC2S.Action.LIST, 0, Employee.AUTO, 0, 0, 0, 0, 0, false));
					List<StaffS2C> answers = c.payloads(player, StaffS2C.class);
					that(!answers.isEmpty(), "no staff list was sent");
					equal(wage, answers.getLast().info().clerkWage(), "clerk wage shown on the staff tab");
				})));
		cases.add(full("chapel_reputation", List.of("39-6"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			double h = TradeExpansionConfig.get().reputation.halfLifeDays;
			that(h > 0, "reputation does not fade (halfLifeDays %s)", h);
			buildAndComplete(c, player, market, CHAPEL);
			double k = Development.config().chapelReputationDecayMultiplier;
			that(Math.abs(Reputation.halfLifeDays(market) - h / k) < 1e-9, "half-life with a chapel: %s", Reputation.halfLifeDays(market));
			UUID id = player.getUUID();
			long now = c.now();
			market.setStanding(new Market.Standing(id, 1000, now));
			double after = Reputation.points(market, id, now + Math.round(h * PriceModel.TICKS_PER_DAY));
			double expected = 1000 * Math.pow(2, -k);
			that(Math.abs(after - expected) < 1.0, "after %.1f days with a chapel: %.1f points, expected %.1f", h, after, expected);
		})));
		cases.add(full("town_hall_materials", List.of("39-7"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			FacilityDefinition def = FacilityCatalog.get(WAREHOUSE);
			FacilityDefinition.Wanted wanted = def.wanted().getFirst();
			double multiplier = Development.config().materialCountMultiplier;
			equal((int) DevelopmentMath.materialCount(wanted.count(), multiplier), FacilityBuilder.required(market, WAREHOUSE, wanted), "materials without a town hall");
			buildAndComplete(c, player, market, TOWN_HALL);
			int expected = (int) DevelopmentMath.materialCount(wanted.count(), multiplier * Development.config().townHallMaterialMultiplier);
			equal(expected, FacilityBuilder.required(market, WAREHOUSE, wanted), "materials of the warehouse with a town hall");
			that(expected < DevelopmentMath.materialCount(wanted.count(), multiplier), "the town hall did not lower the count");
		})));
		cases.add(full("workshop_villagers", List.of("39-8"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BELL), 12, 1, 0.05f);
			TradeExpansionConfig cfg = TradeExpansionConfig.get();
			long before = VillagerCoins.price(player, offer, cfg);
			that(before > 0, "the bell trade is not paid in coins: %s", before);
			buildAndComplete(c, player, market, WORKSHOP);
			player.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
			long after = VillagerCoins.price(player, offer, cfg);
			long expected = Math.max(1, (long) Math.ceil(before * Development.config().workshopVillagerPriceMultiplier - 1e-9));
			equal(expected, after, "coin price of 10 emeralds near a workshop");
			BlockPos far = stall.east(200);
			c.floor(far);
			player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			equal(before, VillagerCoins.price(player, offer, cfg), "coin price far from the workshop");
			player.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		})));
		cases.add(partial("school_outlook", List.of("39-9"),
				"学舎が建つと、交易画面のスナップショットに品不足・流行の残り日数（1日以上、それぞれの入れ替えの日数以下）が入り、雇い人タブの行き先（品を決めた運び屋）に、"
						+ "行き先の品不足の倍率と残り日数が入ること。学舎が無い時はどちらも入らない（画面の文の表示は見ていない）",
				ctx -> ServerFacilityCases.townMarkets(ctx, new int[] {0, 300}, (c, player, m) -> {
					Market a = m.get(0), b = m.get(1);
					builder(c, player, a);
					c.command(player, "aktradeex reputation set 150");
					Identifier good = ServerChapterCases.shared(c, a, 16, 0, b);
					b.shortages().put(good, 1.8);
					ServerChapterCases.openAt(c, player, m, 0);
					that(!c.lastSnapshot(player).outlook().school(), "the snapshot shows a school before it was built");
					StaffS2C.Destination d0 = destination(c, player, a, b, good);
					that(d0.outlook().isEmpty(), "an outlook without a school: %s", d0.outlook());

					buildAndComplete(c, player, a, SCHOOL);
					player.closeContainer();
					ServerChapterCases.openAt(c, player, m, 0);
					MarketSnapshotS2C.OutlookView o = c.lastSnapshot(player).outlook();
					that(o.school(), "no school in the snapshot");
					int shortageMax = (int) Math.ceil(TradeExpansionConfig.get().demand.durationDays);
					int trendMax = (int) Math.ceil(TradeExpansionConfig.get().trend.durationDays);
					that(o.shortageDays() >= 1 && o.shortageDays() <= shortageMax, "shortage days: %s", o.shortageDays());
					that(o.trendDays() >= 1 && o.trendDays() <= trendMax, "trend days: %s", o.trendDays());
					StaffS2C.Destination d1 = destination(c, player, a, b, good);
					that(d1.outlook().isPresent(), "no outlook with a school");
					that(Math.abs(d1.outlook().get().shortage() - 1.8) < 1e-9, "shortage in the outlook: %s", d1.outlook().get());
					that(d1.outlook().get().shortageDays() >= 1, "shortage days in the outlook: %s", d1.outlook().get());
				})));
		return cases;
	}

	/** The staff tab's destination {@code b} of a carrier of {@code good} hired at {@code a}. */
	private static StaffS2C.Destination destination(ServerCtx c, ServerPlayer player, Market a, Market b, Identifier good) {
		c.forget(player);
		StaffService.handle(player, new StaffRequestC2S(a.id(), StaffRequestC2S.Action.DESTINATIONS, 0, good, 0, 0, 0, 0, 0, false));
		List<StaffS2C> answers = c.payloads(player, StaffS2C.class);
		that(!answers.isEmpty(), "no staff answer to DESTINATIONS");
		return answers.getLast().destinations().stream().filter(d -> d.id() == b.id()).findFirst()
				.orElseThrow(() -> new AssertionError("B is not a destination of " + good + ": " + answers.getLast().destinations()));
	}
}
