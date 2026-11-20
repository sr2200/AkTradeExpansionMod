package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.GoodsOverride;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * Server cases of the commands and the config file (checklist 12-9, 13-3 to 13-5, 13-7), damaged goods (30-2), the
 * coast market (30-3) and today's specials by market level and settings (33-2, 33-3). Cases that edit the config file
 * keep it and write it back afterwards ({@link ServerCtx#keepConfigFile()}).
 */
final class ServerCommandCases {
	private ServerCommandCases() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ak_trade_expansion", path);
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();

		// ---- 12-9 audit villagers
		cases.add(full("audit_villagers", List.of("12-9"), ctx -> {
			// No shortages, trends or rival guilds: the plain prices (a shortage good may pay, the checklist allows it).
			ctx.config(cfg -> {
				cfg.demand.maxShortages = 0;
				cfg.trend.popularCount = 0;
				cfg.trend.unpopularCount = 0;
				cfg.rivals.chancePerDay = 0;
			});
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				that(market.shortages().isEmpty(), "the market has shortages: %s", market.shortages());
				// A villager of every profession with the trades of every level (as one fully levelled up), and a wandering trader.
				List<Villager> villagers = new ArrayList<>();
				int i = 0;
				for (var key : BuiltInRegistries.VILLAGER_PROFESSION.registryKeySet()) {
					if (key.equals(VillagerProfession.NONE) || key.equals(VillagerProfession.NITWIT)) continue;
					BlockPos pos = stall.offset(-10 + 2 * (i % 11), 0, -4 - 2 * (i / 11));
					i++;
					Villager v = EntityTypes.VILLAGER.spawn(c.level(), pos, EntitySpawnReason.COMMAND);
					that(v != null, "no villager was spawned");
					v.setNoAi(true);
					v.setVillagerData(v.getVillagerData().withProfession(c.level().registryAccess(), key).withLevel(1));
					v.getOffers(); // the level 1 trades
					var update = Villager.class.getDeclaredMethod("updateTrades", net.minecraft.server.level.ServerLevel.class);
					update.setAccessible(true);
					for (int level = 2; level <= 5; level++) {
						v.setVillagerData(v.getVillagerData().withLevel(level));
						update.invoke(v, c.level());
					}
					villagers.add(v);
				}
				that(villagers.size() >= 10, "only %d professions", villagers.size());
				WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(c.level(), stall.offset(6, 0, -8), EntitySpawnReason.COMMAND);
				that(trader != null, "no wandering trader was spawned");
				trader.setNoAi(true);
				int offers = villagers.stream().mapToInt(v -> v.getOffers().size()).sum() + trader.getOffers().size();
				that(offers > 50, "only %d offers", offers);
				c.forget(player);
				int found = c.commandResult(player, "aktradeex audit villagers");
				that(c.gotMessage(player, "command.ak_trade_expansion.audit_villagers"), "no audit summary: %s", c.messages(player));
				that(found == 0, "%d coin trades of villagers profit when resold at the market: %s", found, c.messages(player));
			});
		}));

		// ---- 13-3 reload
		cases.add(full("config_file_reload", List.of("13-3"), ctx -> {
			ctx.keepConfigFile();
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				Identifier good = c.tradableGood(market, 1, 0);
				long price = c.buyPrice(player, market, good);
				double multiplier = TradeExpansionConfig.get().price.globalMultiplier;
				// Edit the file as a person would (the live settings stay as they are until the reload).
				TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
				file.price.globalMultiplier = multiplier * 3;
				file.trade.maxPerTransaction = 37;
				ConfigManager.write(ConfigManager.path(), file);
				that(TradeExpansionConfig.get().trade.maxPerTransaction != 37, "the live settings changed before the reload");
				c.forget(player);
				c.commandShown(player, "aktradeex reload");
				that(c.gotMessage(player, "command.ak_trade_expansion.reloaded"), "no reload message: %s", c.messages(player));
				equal(37, TradeExpansionConfig.get().trade.maxPerTransaction, "trade.maxPerTransaction after the reload");
				that(Math.abs(TradeExpansionConfig.get().price.globalMultiplier - multiplier * 3) < 1e-9, "price.globalMultiplier after the reload: %s",
						TradeExpansionConfig.get().price.globalMultiplier);
				long after = c.buyPrice(player, market, good);
				that(after >= price * 2.5, "the buy price of %s went %d -> %d with prices x3", good, price, after);
			});
		}));

		// ---- 13-4 consumable / goods commands
		cases.add(full("consumable_and_goods_commands", List.of("13-4"), ctx -> {
			ctx.keepConfigFile();
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				TradeExpansionConfig config = TradeExpansionConfig.get();
				// A good of the market that is not a consumable.
				Identifier good = market.regularGoods().stream().filter(g -> GoodsCatalog.get(g) != null && !GoodsCatalog.get(g).consumable(config)).findFirst()
						.orElseThrow(() -> new AutotestCheck.Failed("the market has no good that is not a consumable"));
				String item = GoodsCatalog.get(good).item().toString();
				double usual = market.params(good).halfLifeDays();
				int listed = c.commandResult(player, "aktradeex consumable list");
				that(c.gotMessage(player, "command.ak_trade_expansion.consumable.list"), "no consumable list: %s", c.messages(player));

				c.commandShown(player, "aktradeex consumable add " + item);
				that(ConfigManager.read(ConfigManager.path()).consumables.add.contains(item), "consumable add did not write %s to the file", item);
				that(GoodsCatalog.get(good).consumable(TradeExpansionConfig.get()), "%s is no consumable after consumable add", item);
				that(market.params(good).halfLifeDays() < usual, "the half-life of %s stayed %.2f days after consumable add", good, market.params(good).halfLifeDays());
				equal(listed + 1, c.commandResult(player, "aktradeex consumable list"), "consumables listed after consumable add");

				c.commandShown(player, "aktradeex consumable remove " + item);
				TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
				that(file.consumables.remove.contains(item) && !file.consumables.add.contains(item), "consumable remove: add %s, remove %s",
						file.consumables.add, file.consumables.remove);
				that(!GoodsCatalog.get(good).consumable(TradeExpansionConfig.get()), "%s is a consumable after consumable remove", item);
				equal(listed, c.commandResult(player, "aktradeex consumable list"), "consumables listed after consumable remove");

				c.commandShown(player, "aktradeex consumable reset " + item);
				file = ConfigManager.read(ConfigManager.path());
				that(!file.consumables.remove.contains(item) && !file.consumables.add.contains(item), "consumable reset left %s in the file", item);

				// goods info / set / reset
				double base = GoodsCatalog.get(good).basePrice();
				c.forget(player);
				c.commandShown(player, "aktradeex goods info " + good);
				that(c.gotMessage(player, "command.ak_trade_expansion.goods_info"), "no goods info: %s", c.messages(player));
				double changed = Math.max(1, Math.round(base * 2));
				long price = c.buyPrice(player, market, good);
				c.forget(player);
				c.commandShown(player, "aktradeex goods set " + good + " base_price " + changed);
				that(c.gotMessage(player, "command.ak_trade_expansion.goods_set"), "no goods set message: %s", c.messages(player));
				file = ConfigManager.read(ConfigManager.path());
				GoodsOverride o = file.goods.get(good.toString());
				that(o != null && o.basePrice().equals(Optional.of(changed)), "goods set wrote %s to the file", o);
				that(Math.abs(GoodsCatalog.get(good).basePrice() - changed) < 1e-9, "the base price of %s is %.2f after goods set, not %.2f", good,
						GoodsCatalog.get(good).basePrice(), changed);
				long raised = c.buyPrice(player, market, good);
				that(raised > price * 1.5, "the buy price of %s went %d -> %d with the base price %.0f -> %.0f", good, price, raised, base, changed);
				c.forget(player);
				c.commandShown(player, "aktradeex goods reset " + good);
				that(c.gotMessage(player, "command.ak_trade_expansion.goods_reset"), "no goods reset message: %s", c.messages(player));
				that(!ConfigManager.read(ConfigManager.path()).goods.containsKey(good.toString()), "goods reset left %s in the file", good);
				that(Math.abs(GoodsCatalog.get(good).basePrice() - base) < 1e-9, "the base price of %s is %.2f after goods reset, not %.2f", good,
						GoodsCatalog.get(good).basePrice(), base);
			});
		}));

		// ---- 13-5 audit / simulate / market info
		cases.add(full("audit_simulate_market_info", List.of("13-5"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.forget(player);
			c.commandShown(player, "aktradeex audit");
			that(c.gotMessage(player, "command.ak_trade_expansion.audit"), "no audit result: %s", c.messages(player));
			Identifier good = c.tradableGood(market, 10, 0);
			String before = c.fingerprint(player);
			c.forget(player);
			c.commandShown(player, "aktradeex simulate " + good + " buy 10");
			that(c.messages(player).stream().anyMatch(m -> m.startsWith("BUY " + good + " x10 @market#" + market.id())), "no simulate result: %s", c.messages(player));
			that(before.equals(c.fingerprint(player)), "simulate changed the market or the player");
			c.forget(player);
			c.commandShown(player, "aktradeex market info");
			List<String> info = c.messages(player);
			that(info.stream().anyMatch(m -> m.startsWith("Market #" + market.id())), "no market info: %s", info);
			that(info.stream().anyMatch(m -> m.contains(good.toString()) && m.contains("buy=")), "market info has no line for %s: %s", good, info);
		})));

		// ---- 13-7 goods added in the config are today's specials
		cases.add(full("added_goods_as_special", List.of("13-7"), ctx -> {
			Item item = java.util.stream.Stream.of(Items.SPONGE, Items.DRIED_KELP_BLOCK, Items.NAUTILUS_SHELL, Items.HEART_OF_THE_SEA)
					.filter(i -> GoodsCatalog.all().values().stream().noneMatch(d -> d.item().equals(BuiltInRegistries.ITEM.getKey(i)))).findFirst()
					.orElseThrow(() -> new AutotestCheck.Failed("every candidate item is already a good"));
			Identifier added = BuiltInRegistries.ITEM.getKey(item);
			ctx.config(cfg -> {
				// What the config screen writes for an added good: the item and a base price.
				cfg.goods.put(added.toString(), new GoodsOverride(Optional.of(40.0), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(added.toString())));
				cfg.market.specialGoods = new ArrayList<>(List.of(10, 10, 10, 10, 10)); // many specials a day: it comes up soon
				cfg.reputation.specialBonusCount = 0;
			});
			that(GoodsCatalog.get(added) != null, "the good added in the config is not in the catalog");
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				boolean found = false;
				for (int day = 0; day < 200 && !found; day++) {
					c.command(player, "aktradeex clock advance 1");
					MarketUpkeep.touch(c.level(), market);
					found = market.specials().contains(added);
				}
				that(found, "%s was never one of today's specials in 200 days", added);
				c.giveMoney(player, 100_000);
				int before = c.count(player, item);
				c.trade(player, market, added, true, 1);
				that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying the added good gave %s", c.lastTradeResult(player));
				equal(before + 1, c.count(player, item), "added goods after buying one");
				c.trade(player, market, added, false, 1);
				that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling the added good gave %s", c.lastTradeResult(player));
				equal(before, c.count(player, item), "added goods after selling one");
			});
		}));

		// ---- 30-2 damaged tools
		cases.add(full("damaged_goods_price", List.of("30-2"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			MarketMenu menu = c.openStall(player, stall);
			long now = MarketUpkeep.touch(c.level(), market);
			// A tool or armour piece the market buys (added to it when it has none), with no accept_damaged of its own.
			Identifier good = null;
			for (var e : GoodsCatalog.all().entrySet()) {
				GoodsDefinition def = e.getValue();
				ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(def.item()));
				if (!def.sell() || def.unit() != 1 || def.components().isPresent() || def.acceptDamaged().isPresent() || !stack.isDamageableItem()) continue;
				if (good == null || market.good(e.getKey()) != null) good = e.getKey();
				if (market.good(good) != null) break;
			}
			that(good != null, "no damageable good");
			if (market.good(good) == null) market.addGood(good, new GoodState(1.0, 0, 0, now));
			market.good(good).setStock(0);
			Item item = c.item(good);
			ItemStack damaged = new ItemStack(item);
			damaged.setDamageValue(damaged.getMaxDamage() / 2);
			c.emptyHand(player);
			player.getInventory().add(damaged);
			long full = c.previewTotal(player, market, good, false, 1);

			// acceptDamaged off (default): a used tool is not the good.
			that(!TradeExpansionConfig.get().trade.acceptDamaged, "trade.acceptDamaged is on by default");
			String before = c.fingerprint(player);
			var r = c.tradeRaw(player, market.id(), good, false, 1, full, false, false);
			that(r == TradeResult.INSUFFICIENT_ITEMS, "selling a used %s without acceptDamaged gave %s", good, r);
			that(before.equals(c.fingerprint(player)), "the refused sale changed something");

			// acceptDamaged on: bought for less than a new one.
			c.config(cfg -> cfg.trade.acceptDamaged = true);
			r = c.tradeRaw(player, market.id(), good, false, 1, full, false, false);
			that(r == TradeResult.PRICE_CHANGED, "selling a used %s for the price of a new one gave %s", good, r);
			double[] ratios = InventoryUtil.sellRatios(player, GoodsCatalog.match(good), menu.sellSlot(), 1);
			that(ratios != null && ratios.length == 1 && ratios[0] < 0.75, "remaining durability of the used tool: %s", java.util.Arrays.toString(ratios));
			GoodParams params = com.akf.tradeexpansion.market.Reputation.params(market, player.getUUID(), good, c.now());
			long reduced = PriceModel.sumSell(params, market.good(good).pressure(), ratios);
			that(reduced < full, "the used tool sells for %dc, a new one %dc", reduced, full);
			long money = c.balance(player);
			r = c.tradeRaw(player, market.id(), good, false, 1, reduced, false, false);
			that(r == TradeResult.SUCCESS, "selling the used %s for %dc gave %s", good, reduced, r);
			equal(money + reduced, c.balance(player), "money after selling the used tool");
			equal(0, c.count(player, item), "used tools left");
		})));

		// ---- 30-3 the coast market
		cases.add(partial("coast_market", List.of("30-3"),
				"浜辺のバイオーム（/fillbiome で変えた）に交易所を置くと、市場の種類が「海辺の市場」になって生鱈が並ぶこと。村の品揃えの優先度が海辺の市場より高いこと（データ。テストの世界に村の構造物が無いので、浜辺の村そのものは見ていない）",
				ctx -> {
					MarketProfile coast = MarketProfiles.get(id("coast"));
					that(coast != null, "no coast market profile");
					for (String village : List.of("plains_village", "desert_village", "savanna_village", "snowy_village", "taiga_village")) {
						MarketProfile p = MarketProfiles.get(id(village));
						that(p != null && p.priority() > coast.priority(), "%s has priority %s, the coast market %d", village, p == null ? "-" : p.priority(), coast.priority());
					}
					ctx.fillBiome(ctx.center(), 12, "minecraft:beach");
					ServerCases.withMarket(ctx, (c, player, market, stall) -> {
						that(market.profileId().equals(id("coast")), "the market type is %s, not the coast market", market.profileId());
						that(market.good(id("cod")) != null, "the coast market has no cod: %s", market.goods().keySet());
					});
				}));

		// ---- 33-2 specials of a level 5 market
		cases.add(full("specials_of_a_large_market", List.of("33-2"), ctx -> {
			// Level 5 with the small test village.
			ctx.config(cfg -> {
				cfg.market.levelThresholds = new ArrayList<>(List.of(1, 1, 1, 1));
				cfg.market.villagerThresholds = new ArrayList<>(List.of(1, 1, 1, 1));
			});
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				equal(5, market.level(), "market level");
				int cheap = 0, dear = 0;
				Set<Identifier> dearSeen = new HashSet<>();
				for (int day = 0; day < 300; day++) {
					c.command(player, "aktradeex clock advance 1");
					MarketUpkeep.touch(c.level(), market);
					for (Identifier special : market.specials()) {
						double price = GoodsCatalog.get(special).basePrice();
						if (price <= 100) cheap++;
						if (price >= 1000) {
							dear++;
							dearSeen.add(special);
						}
					}
				}
				that(dear > 0, "no special of 1,000c or more in 300 days (cheap ones: %d)", cheap);
				that(cheap >= 10 * dear, "cheap specials %d, expensive ones %d (%s): the cheap ones are not much more common", cheap, dear, dearSeen);
			});
		}));

		// ---- 33-3 the special settings apply from the next day
		cases.add(partial("special_settings_next_day", List.of("33-3"),
				"「掘り出し物を市場の規模に合わせる」を OFF にすると、その日の掘り出し物は変わらず、次の日から市場のレベルより高い品も出ること。「高い掘り出し物の出にくさ」を上げると高い品が出にくくなること（設定画面はクライアントのテストで見ている）",
				ctx -> {
					ctx.config(cfg -> {
						cfg.market.specialGoods = new ArrayList<>(List.of(10, 10, 10, 10, 10));
						cfg.reputation.specialBonusCount = 0;
					});
					ServerCases.withMarket(ctx, (c, player, market, stall) -> {
						int level = market.level();
						that(level <= 2, "the test market is level %d", level);
						for (int day = 0; day < 10; day++) {
							c.command(player, "aktradeex clock advance 1");
							MarketUpkeep.touch(c.level(), market);
							for (Identifier s : market.specials()) {
								that(GoodsCatalog.get(s).grade() <= level, "day %d: %s (grade %d) with specialUseGrade on", day, s, GoodsCatalog.get(s).grade());
							}
						}
						Set<Identifier> today = Set.copyOf(market.specials());
						c.config(cfg -> {
							cfg.market.specialUseGrade = false;
							cfg.market.specialPriceExponent = 0;
						});
						MarketUpkeep.touch(c.level(), market);
						that(today.equals(Set.copyOf(market.specials())), "today's specials changed with the setting: %s -> %s", today, market.specials());
						int higher = 0, dearEqual = 0;
						for (int day = 0; day < 20; day++) {
							c.command(player, "aktradeex clock advance 1");
							MarketUpkeep.touch(c.level(), market);
							for (Identifier s : market.specials()) {
								if (GoodsCatalog.get(s).grade() > level) higher++;
								if (GoodsCatalog.get(s).basePrice() >= 200) dearEqual++;
							}
						}
						that(higher > 0, "no special above the market level in 20 days with specialUseGrade off");
						c.config(cfg -> cfg.market.specialPriceExponent = 2);
						int dearRare = 0;
						for (int day = 0; day < 20; day++) {
							c.command(player, "aktradeex clock advance 1");
							MarketUpkeep.touch(c.level(), market);
							for (Identifier s : market.specials()) if (GoodsCatalog.get(s).basePrice() >= 200) dearRare++;
						}
						that(dearRare * 3 < dearEqual, "specials of 200c or more: %d with specialPriceExponent 0, %d with 2", dearEqual, dearRare);
					});
				}));
		return cases;
	}
}
