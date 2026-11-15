package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import com.akf.tradeexpansion.market.MarketUpkeep;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * Server cases of the goods data (checklist chapters 30, 32 and 33): consumables, the eggs, the village price
 * modifiers that keep crafting from paying, and today's specials of a small market.
 */
final class ServerGoodsCases {
	private ServerGoodsCases() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ak_trade_expansion", path);
	}

	private static GoodsDefinition def(String goods) {
		GoodsDefinition def = GoodsCatalog.get(id(goods));
		that(def != null, "no goods %s", goods);
		return def;
	}

	private static MarketProfile profile(String name) {
		MarketProfile profile = MarketProfiles.get(id(name));
		that(profile != null, "no market profile %s", name);
		return profile;
	}

	/** The params of a good in a market of {@code profile}, without the market's own price factor (±15%). */
	private static GoodParams params(String profile, String goods) {
		GoodsDefinition def = def(goods);
		return def.params(1.0, profile(profile).multiplier(id(goods), def.item()), TradeExpansionConfig.get());
	}

	private static boolean offers(String profile, String goods) {
		MarketProfile p = profile(profile);
		return p.always().contains(id(goods)) || p.pools().stream().anyMatch(pool -> pool.entries().stream().anyMatch(e -> e.goods().equals(id(goods))));
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("consumable_goods", List.of("30-7", "32-2"),
				"真紅の幹・歪んだ幹・ハチミツ入りの瓶・サトウキビ・矢・茶色の卵・青い卵が消耗品で、相場の戻り（半減期）と在庫の減りが消耗品の速さになること（画面の表示は見ていない）",
				ctx -> {
					TradeExpansionConfig config = TradeExpansionConfig.get();
					for (String goods : List.of("crimson_stem", "warped_stem", "honey_bottle", "sugar_cane", "arrow", "brown_egg", "blue_egg")) {
						GoodsDefinition def = def(goods);
						that(def.consumable(config), "%s is not a consumable", goods);
						GoodParams p = def.params(1.0, 1.0, config);
						that(p.halfLifeDays() <= config.recovery.consumableHalfLifeDays + 1e-9, "%s: the price gap halves in %.2f days, not %.2f",
								goods, p.halfLifeDays(), config.recovery.consumableHalfLifeDays);
						that(p.consumePerDay() >= p.targetStock() * config.recovery.consumableMultiplier - 1e-9, "%s: %.1f of the stock is used up a day",
								goods, p.consumePerDay());
					}
				}));
		cases.add(partial("consumables_review", List.of("36-1"),
				"砂利・粘土玉・砂岩・氷・サボテン・スイレンの葉・ネザーウォート・ソウルサンド・ブラックストーン・玄武岩・エンドストーンが消耗品で、トーチフラワー・ウツボカズラ・ウィザーローズ・金のニンジン・腐った肉は消耗品でないこと（コマンドの表示は見ていない）",
				ctx -> {
					TradeExpansionConfig config = TradeExpansionConfig.get();
					for (String goods : List.of("gravel", "clay_ball", "sandstone", "ice", "cactus", "lily_pad", "nether_wart", "soul_sand", "blackstone",
							"basalt", "end_stone")) {
						that(def(goods).consumable(config), "%s is not a consumable", goods);
					}
					for (String goods : List.of("torchflower", "pitcher_plant", "wither_rose", "golden_carrot", "rotten_flesh")) {
						GoodsDefinition def = def(goods);
						that(!def.consumable(config), "%s is a consumable", goods);
						GoodParams p = def.params(1.0, 1.0, config);
						that(Math.abs(p.halfLifeDays() - config.recovery.halfLifeDays) < 1e-9, "%s: the price gap halves in %.2f days, not the usual %.2f",
								goods, p.halfLifeDays(), config.recovery.halfLifeDays);
					}
				}));
		cases.add(partial("brown_and_blue_eggs", List.of("32-1"),
				"茶色の卵・青い卵の値段が卵と同じ 6c／4c で、茶色の卵は砂漠・サバンナの村とジャングル、青い卵は雪原・タイガの村の品揃えの候補にあること（実際の村での並び方とアイコンは見ていない）",
				ctx -> {
					GoodParams egg = params("plains_village", "egg");
					for (String goods : List.of("brown_egg", "blue_egg")) {
						GoodParams p = def(goods).params(1.0, 1.0, TradeExpansionConfig.get());
						equal(PriceModel.buyPrice(egg, 0), PriceModel.buyPrice(p, 0), goods + " buy price (the egg's)");
						equal(PriceModel.sellPrice(egg, 0), PriceModel.sellPrice(p, 0), goods + " sell price (the egg's)");
					}
					equal(6, PriceModel.buyPrice(egg, 0), "buy price of an egg");
					equal(4, PriceModel.sellPrice(egg, 0), "sell price of an egg");
					for (String profile : List.of("desert_village", "savanna_village", "jungle")) {
						that(offers(profile, "brown_egg"), "%s does not offer brown eggs", profile);
					}
					for (String profile : List.of("snowy_village", "taiga_village")) {
						that(offers(profile, "blue_egg"), "%s does not offer blue eggs", profile);
					}
				}));
		cases.add(partial("snowy_ice_prices", List.of("30-5"),
				"雪原の村で氷と氷塊の両方に ×0.7 が掛かり、氷塊1個の買値が氷9個の買値より安いこと（市場ごとのばらつき ±15% の両端でも）（実際の村では見ていない）",
				ctx -> {
					GoodParams ice = params("snowy_village", "ice");
					GoodParams packed = params("snowy_village", "packed_ice");
					that(Math.abs(ice.basePrice() - def("ice").basePrice() * 0.7) < 1e-6, "ice in a snowy village: %.2fc", ice.basePrice());
					that(Math.abs(packed.basePrice() - def("packed_ice").basePrice() * 0.7) < 1e-6, "packed ice in a snowy village: %.2fc", packed.basePrice());
					that(PriceModel.buyPrice(packed, 0) < 9 * PriceModel.buyPrice(ice, 0), "packed ice %dc, 9 ice %dc", PriceModel.buyPrice(packed, 0),
							9 * PriceModel.buyPrice(ice, 0));
					// Crafting 9 bought ice into packed ice and selling it does not pay, even with the market spread at its ends.
					GoodParams cheapIce = def("ice").params(0.85, profile("snowy_village").multiplier(id("ice"), def("ice").item()), TradeExpansionConfig.get());
					GoodParams dearPacked = def("packed_ice").params(1.15, profile("snowy_village").multiplier(id("packed_ice"), def("packed_ice").item()),
							TradeExpansionConfig.get());
					that(PriceModel.sellPrice(dearPacked, 0) < 9 * PriceModel.buyPrice(cheapIce, 0), "selling packed ice (%dc) pays more than 9 ice cost (%dc)",
							PriceModel.sellPrice(dearPacked, 0), 9 * PriceModel.buyPrice(cheapIce, 0));
				}));
		cases.add(partial("savanna_saddle_price", List.of("30-6"),
				"サバンナの村の鞍の基準価格が 117c（×0.9）で、革3つと鉄インゴットを買って作って売っても儲からないこと（実際の村では見ていない）",
				ctx -> {
					GoodParams saddle = params("savanna_village", "saddle");
					that(Math.abs(saddle.basePrice() - 117) < 1e-6, "a saddle in a savanna village: %.2fc", saddle.basePrice());
					long cost = 3 * PriceModel.buyPrice(params("savanna_village", "leather"), 0) + PriceModel.buyPrice(params("savanna_village", "iron_ingot"), 0);
					that(PriceModel.sellPrice(saddle, 0) < cost, "the saddle sells for %dc, its leather and iron cost %dc", PriceModel.sellPrice(saddle, 0), cost);
				}));
		cases.add(full("specials_of_a_small_market", List.of("33-1"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			int level = market.level();
			that(level <= 2, "the test market is level %d, not 1-2", level);
			for (String expensive : List.of("diamond_sword", "diamond_chestplate", "elytra", "trident")) {
				that(def(expensive).grade() > 2, "%s has grade %d: a level 1-2 market may get it", expensive, def(expensive).grade());
			}
			Set<Identifier> seen = new HashSet<>();
			for (int day = 0; day < 40; day++) {
				c.command(player, "aktradeex clock advance 1");
				MarketUpkeep.touch(c.level(), market);
				for (Identifier special : market.specials()) {
					GoodsDefinition def = GoodsCatalog.get(special);
					that(def != null && def.grade() <= level, "day %d: %s (grade %d) is a special of a level %d market", day, special,
							def == null ? -1 : def.grade(), level);
					seen.add(special);
				}
			}
			that(seen.size() >= 10, "only %d different specials in 40 days: %s", seen.size(), seen);
		})));
		return cases;
	}
}
