package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.GoodsSelector;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import com.akf.tradeexpansion.entity.TraderEntity;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.PlacementGuide;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModEntities;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.villager.VillagerCoins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * Server cases of the chapters about the world around the markets: 5 (assortment and market size), 6 (traders and
 * village stalls), 7-3 (coins onto the wallet), 8 (getting coins), 10 (market names), 11 (where a stall may go) and
 * 12 (villager trades in coins, through the real merchant screen).
 */
final class ServerWorldCases {
	private ServerWorldCases() {
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		chapter5(cases);
		chapter6(cases);
		chapter7(cases);
		chapter8(cases);
		chapter10(cases);
		chapter11(cases);
		chapter12(cases);
		return cases;
	}

	// ---- 5. assortment and market size

	private static final Identifier EMERALD_COIN = Identifier.parse("ak_trade_expansion:emerald_coin");

	private static void chapter5(List<ServerCase> cases) {
		cases.add(partial("profiles_differ_by_region", List.of("5-1"), "同梱の市場プロファイルで、砂漠の村に砂・サボテン、沼地にヒスイランがあり、平原の村には無いこと（実際の村の地形での抽選は見ていない）", ctx -> {
			Set<String> desert = profileGoods("desert_village").keySet(), swamp = profileGoods("swamp").keySet(), plains = profileGoods("plains_village").keySet();
			for (String g : List.of("sand", "cactus")) that(desert.contains("ak_trade_expansion:" + g), "the desert village has no %s", g);
			that(swamp.contains("ak_trade_expansion:blue_orchid"), "the swamp has no blue orchid");
			for (String g : List.of("sand", "cactus", "blue_orchid")) that(!plains.contains("ak_trade_expansion:" + g), "the plains village has %s", g);
		}));
		cases.add(partial("raw_meat_and_fish", List.of("5-1b"), "同梱の市場プロファイルに生の肉（牛・豚・鶏）と生の魚（鱈・鮭）が並び、焼いた肉・魚の商品が無いこと（並んだ画面は見ていない）", ctx -> {
			Set<String> all = new HashSet<>();
			for (Identifier id : MarketProfiles.all().keySet()) all.addAll(profileGoods(id.getPath()).keySet());
			for (String g : List.of("beef", "porkchop", "chicken", "cod", "salmon")) that(all.contains("ak_trade_expansion:" + g), "no market lists %s", g);
			for (Identifier id : GoodsCatalog.all().keySet()) {
				String item = GoodsCatalog.get(id).item().getPath();
				that(!item.startsWith("cooked_"), "%s is a good (cooked food)", id);
			}
		}));
		cases.add(partial("emerald_coin_always_sells", List.of("5-2"), "村の市場の売却タブにエメラルド貨があり、10枚で 4c のまま70回続けて売れること（ネザー・エンド・前のバージョンの市場は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					that(market.good(EMERALD_COIN) != null, "the market has no emerald coin");
					equal(10, GoodsCatalog.get(EMERALD_COIN).unit(), "emerald coins per unit");
					c.emptyInventory(player);
					ServerCases.giveItems(player, ModItems.EMERALD_COIN, 700);
					for (int i = 0; i < 70; i++) {
						long total = c.previewTotal(player, market, EMERALD_COIN, false, 1);
						equal(4, total, "price of 10 emerald coins (sale " + (i + 1) + ")");
						var r = c.tradeRaw(player, market.id(), EMERALD_COIN, false, 1, total, false, false);
						that(r == TradeResult.SUCCESS, "sale %d of 10 emerald coins gave %s", i + 1, r);
					}
					equal(0, c.count(player, ModItems.EMERALD_COIN), "emerald coins left");
					equal(280, c.balance(player), "money after selling 700 emerald coins");
				})));
		cases.add(partial("market_grows_and_shrinks", List.of("5-3", "5-3b", "5-4", "5-6"),
				"ベッドだけ増やすと村人の数で止まり、村人も増やして1日たつと「成長しました」と出て商品が増え（レベル3以上で銀行が使える。レベル2では使えない）、ベッドを減らすと「縮小しました」と出て減り、戻すと同じ商品が同じ状態で戻ること（銀行タブの表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					BlockPos center = stall;
					c.openStall(player, stall);
					equal(1, market.level(), "level with 3 beds and 3 villagers");
					c.emptyInventory(player);
					c.giveMoney(player, 1_000);
					BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 100));
					equal(0, bank(player), "bank after depositing at a level 1 market");
					for (int i = 3; i < 24; i++) c.bed(bedPos(center, i));
					c.later(ServerCases.SETTLE, c2 -> {
						equal(24, MarketFactory.countBeds(c2.level(), center), "beds");
						reopenNextDay(c2, player, stall);
						// 24 beds = level 4, 3 villagers = level 2: the lower wins (5-3b).
						equal(2, market.level(), "level with 24 beds and 3 villagers");
						BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 100));
						equal(0, bank(player), "bank after depositing at a level 2 market");
						for (int i = 3; i < 13; i++) villager(c2, center.offset(-6 + 2 * (i % 7), 0, 6 + i / 7));
						c2.later(ServerCases.SETTLE, c3 -> {
							int before = market.regularGoods().size();
							reopenNextDay(c3, player, stall);
							that(c3.gotMessage(player, "message.ak_trade_expansion.market_grew"), "no \"grew\" message: %s (level %d, beds %d, villagers %d)", c3.messages(player),
									market.level(), MarketFactory.countBeds(c3.level(), center), MarketFactory.countVillagers(c3.level(), center));
							equal(4, market.level(), "level with 24 beds and 13 villagers");
							equal(MarketFactory.goodsTarget(market, 4), market.regularGoods().size(), "goods at level 4");
							that(market.regularGoods().size() > before, "the goods did not increase (%d -> %d)", before, market.regularGoods().size());
							equal(TradeExpansionConfig.get().market.specialGoodsAt(4), market.specials().size() - market.bonusSpecials().size(), "specials at level 4");
							BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 100));
							equal(100, bank(player), "bank after depositing at a level 4 market");
							Map<Identifier, Double> factors = new HashMap<>();
							for (Identifier g : market.regularGoods()) factors.put(g, market.good(g).priceFactor());
							for (int i = 3; i < 24; i++) removeBed(c3, bedPos(center, i));
							c3.later(ServerCases.SETTLE, c4 -> {
								reopenNextDay(c4, player, stall);
								that(c4.gotMessage(player, "message.ak_trade_expansion.market_shrank"), "no \"shrank\" message: %s", c4.messages(player));
								equal(1, market.level(), "level after removing the beds");
								equal(MarketFactory.goodsTarget(market, 1), market.regularGoods().size(), "goods after shrinking");
								for (int i = 3; i < 24; i++) c4.bed(bedPos(center, i));
								c4.later(ServerCases.SETTLE, c5 -> {
									reopenNextDay(c5, player, stall);
									that(c5.gotMessage(player, "message.ak_trade_expansion.market_grew"), "no \"grew\" message after the beds came back");
									equal(4, market.level(), "level after the beds came back");
									that(new HashSet<>(market.regularGoods()).equals(factors.keySet()), "other goods came back: %s / %s", market.regularGoods(), factors.keySet());
									for (Identifier g : market.regularGoods()) {
										that(market.good(g).priceFactor() == factors.get(g), "%s came back with another price factor", g);
									}
								});
							});
						});
					});
				})));
		cases.add(partial("specials_rotate_daily", List.of("5-7"), "レベル1の市場に本日の掘り出し物が1品あり、日が変わると入れ替わること（レベル3以上で2品は market_grows_and_shrinks で確かめる。★と水色の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					List<Set<Identifier>> seen = new ArrayList<>();
					for (int day = 0; day < 4; day++) {
						if (day > 0) c.command(player, "aktradeex clock advance 1");
						MarketUpkeep.touch(c.level(), market);
						Set<Identifier> regular = new HashSet<>(market.specials());
						regular.removeAll(market.bonusSpecials());
						equal(TradeExpansionConfig.get().market.specialGoodsAt(1), regular.size(), "specials of a level 1 market on day " + day);
						for (Identifier g : regular) that(market.regularGoods().stream().noneMatch(g::equals), "special %s is a regular good", g);
						seen.add(regular);
					}
					that(new HashSet<>(seen).size() >= 2, "the specials never changed in 4 days: %s", seen);
				})));
		cases.add(partial("diamond_gear_levels", List.of("5-8"), "同梱のプロファイルでダイヤの道具はレベル4以上・防具はレベル5でだけ並び、エンチャント付きのダイヤのツルハシは売れず、無しなら売れること（並んだ画面は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					for (Identifier id : MarketProfiles.all().keySet()) {
						profileGoods(id.getPath()).forEach((g, minLevel) -> {
							if (g.contains("diamond_") && !g.endsWith(":diamond")) {
								boolean armor = g.endsWith("_helmet") || g.endsWith("_chestplate") || g.endsWith("_leggings") || g.endsWith("_boots");
								that(minLevel >= (armor ? 5 : 4), "%s lists %s from level %d", id, g, minLevel);
							}
						});
					}
					c.openStall(player, stall);
					Identifier pick = Identifier.parse("ak_trade_expansion:diamond_pickaxe");
					long now = MarketUpkeep.touch(c.level(), market);
					if (market.good(pick) == null) market.addGood(pick, new GoodState(1.0, 0, 0, now));
					c.emptyInventory(player);
					ItemStack enchanted = new ItemStack(Items.DIAMOND_PICKAXE);
					enchanted.enchant(enchantment(c, Enchantments.EFFICIENCY), 3);
					player.getInventory().setItem(9, enchanted);
					String before = c.fingerprint(player);
					var r = c.tradeRaw(player, market.id(), pick, false, 1, c.previewTotal(player, market, pick, false, 1), false, false);
					that(r == TradeResult.INSUFFICIENT_ITEMS, "selling an enchanted diamond pickaxe gave %s", r);
					that(before.equals(c.fingerprint(player)), "the refused sale changed something");
					player.getInventory().setItem(10, new ItemStack(Items.DIAMOND_PICKAXE));
					that(PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), pick, now), market.good(pick)) >= 1, "no room to sell a pickaxe");
					var ok = c.tradeRaw(player, market.id(), pick, false, 1, c.previewTotal(player, market, pick, false, 1), false, false);
					that(ok == TradeResult.SUCCESS, "selling a plain diamond pickaxe gave %s", ok);
					that(player.getInventory().getItem(9).isEnchanted(), "the enchanted pickaxe was sold");
				})));
		cases.add(partial("plains_logs", List.of("5-9"), "同梱の平原の村・開拓地（自分で置いた交易所）のプロファイルにシラカバ・ダークオーク・サクラ・ペールオーク・ポプラの原木があること（並んだ画面は見ていない）", ctx -> {
			for (String profile : List.of("plains_village", "crafted_stall")) {
				Set<String> goods = profileGoods(profile).keySet();
				for (String log : List.of("birch_log", "dark_oak_log", "cherry_log", "pale_oak_log", "poplar_log")) {
					that(goods.contains("ak_trade_expansion:" + log), "%s has no %s", profile, log);
				}
			}
		}));
	}

	/** Goods of a bundled profile with the lowest level they can be listed at (always-goods: level 1). */
	private static Map<String, Integer> profileGoods(String name) {
		MarketProfile profile = MarketProfiles.get(Identifier.parse("ak_trade_expansion:" + name));
		that(profile != null, "no market profile %s", name);
		Map<String, Integer> out = new HashMap<>();
		profile.alwaysGoods().forEach(g -> out.put(g, 1));
		for (GoodsSelector.Pool pool : profile.selectorPools()) {
			for (GoodsSelector.Candidate cand : pool.candidates()) out.merge(cand.goods(), Math.max(1, cand.minLevel()), Math::min);
		}
		return out;
	}

	private static BlockPos bedPos(BlockPos center, int i) {
		return center.offset(-8 + 2 * (i % 8), 0, -9 + 3 * (i / 8));
	}

	private static void removeBed(ServerCtx c, BlockPos head) {
		c.level().setBlock(head, Blocks.AIR.defaultBlockState(), 3);
		c.level().setBlock(head.south(), Blocks.AIR.defaultBlockState(), 3);
	}

	/** A villager that stays where it is put (counted for the market level and the founding rule). */
	private static Villager villager(ServerCtx c, BlockPos pos) {
		Villager v = EntityTypes.VILLAGER.spawn(c.level(), pos, EntitySpawnReason.COMMAND);
		that(v != null, "no villager was spawned");
		v.setNoAi(true);
		return v;
	}

	/** Moves the market clock a day on and opens the stall again (the level is measured once a day, D-4). */
	private static void reopenNextDay(ServerCtx c, ServerPlayer player, BlockPos stall) {
		player.closeContainer(); // closed first: an open screen would bring the market (specials) up to date at the old level
		c.command(player, "aktradeex clock advance 1.01");
		c.forget(player);
		c.openStall(player, stall);
	}

	private static long bank(ServerPlayer player) {
		return BankService.accounts(player).balance(player.getUUID());
	}

	private static net.minecraft.core.Holder<Enchantment> enchantment(ServerCtx c, ResourceKey<Enchantment> key) {
		return c.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	// ---- 6. traders and village stalls

	private static List<TraderEntity> traders(ServerCtx c, BlockPos around) {
		return c.level().getEntitiesOfClass(TraderEntity.class, new AABB(around).inflate(6), TraderEntity::isAlive);
	}

	private static void chapter6(List<ServerCase> cases) {
		cases.add(full("natural_stall_protected", List.of("6-4"), ctx -> {
			ctx.config(cfg -> cfg.worldgen.protectNaturalStalls = true);
			ServerCases.withMarket(ctx, (c, player, market, crafted) -> {
				BlockPos natural = crafted.east(4);
				c.level().setBlock(natural, ModBlocks.MARKET_STALL.defaultBlockState(), 3); // as a village structure places it
				c.forget(player);
				boolean broken = player.gameMode.destroyBlock(natural);
				that(!broken && c.isStall(natural), "the village stall was broken");
				that(c.gotMessage(player, "message.ak_trade_expansion.stall_protected"), "no \"cannot break\" message: %s", c.messages(player));
				that(player.gameMode.destroyBlock(crafted) && !c.isStall(crafted), "a crafted stall could not be broken");
			});
		}));
		cases.add(partial("trader_spawns_once_and_stays", List.of("6-5", "6-6", "6-7"), "開くと正面に商人が1体だけ出て、攻撃・溶岩で死なず、押されず、遠くでも消えない扱いで、交易所を壊すと消えること（時間がたつ様子は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					List<TraderEntity> first = traders(c, stall);
					equal(1, first.size(), "traders after opening the stall");
					TraderEntity trader = first.getFirst();
					BlockPos front = stall.relative(c.level().getBlockState(stall).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
					that(trader.blockPosition().equals(front), "the trader stands at %s, not in front of the stall (%s)", trader.blockPosition(), front);
					player.closeContainer();
					c.openStall(player, stall);
					equal(1, traders(c, stall).size(), "traders after opening the stall again");
					trader.hurtServer(c.level(), c.level().damageSources().playerAttack(player), 1000);
					trader.hurtServer(c.level(), c.level().damageSources().lava(), 1000);
					trader.hurtServer(c.level(), c.level().damageSources().inFire(), 1000);
					that(trader.isAlive() && !trader.isRemoved(), "the trader died");
					that(!trader.isPushable(), "the trader can be pushed");
					that(!trader.removeWhenFarAway(10_000) && trader.requiresCustomPersistence(), "the trader may despawn");
					player.closeContainer();
					that(player.gameMode.destroyBlock(stall), "the stall could not be broken");
					that(trader.isRemoved(), "the trader stayed after its stall was broken");
				})));
		cases.add(full("summoned_trader_opens_market", List.of("6-8", "6-11"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			BlockPos at = stall.east(4);
			c.command(player, "summon ak_trade_expansion:trader " + at.getX() + " " + at.getY() + " " + at.getZ());
			List<TraderEntity> found = traders(c, at).stream().filter(t -> t.stallPos() == null).toList();
			equal(1, found.size(), "summoned traders");
			c.emptyHand(player);
			player.interactOn(found.getFirst(), InteractionHand.MAIN_HAND, Vec3.ZERO);
			that(player.containerMenu instanceof MarketMenu m && m.marketId() == market.id(), "right-clicking the trader did not open market #%d", market.id());
			player.closeContainer();
			c.command(player, "kill @e[type=ak_trade_expansion:trader]");
			that(found.getFirst().isRemoved() || !found.getFirst().isAlive(), "/kill did not remove the trader");
		})));
		cases.add(full("trader_without_market", List.of("6-9"), ctx -> {
			ServerPlayer player = ctx.player(ctx.center().south(2));
			TraderEntity trader = ModEntities.TRADER.spawn(ctx.level(), ctx.center(), EntitySpawnReason.SPAWN_ITEM_USE);
			that(trader != null, "no trader was spawned");
			ctx.emptyHand(player);
			player.interactOn(trader, InteractionHand.MAIN_HAND, Vec3.ZERO);
			that(!(player.containerMenu instanceof MarketMenu), "a trade screen opened without a market");
			that(ctx.gotMessage(player, "message.ak_trade_expansion.trader_no_market"), "no \"no market nearby\" message: %s", ctx.messages(player));
		}));
	}

	// ---- 7-3. coins onto the wallet

	private static void chapter7(List<ServerCase> cases) {
		cases.add(full("coins_stacked_onto_wallet", List.of("7-3"), ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			ctx.emptyInventory(player);
			var menu = player.inventoryMenu;
			player.getInventory().setItem(9, new ItemStack(ModItems.WALLET)); // menu slot 9 = inventory slot 9
			menu.setCarried(new ItemStack(ModItems.COPPER_COIN, 20));
			menu.clicked(9, 1, ContainerInput.PICKUP, player); // right-click the wallet with coins on the cursor
			that(menu.getCarried().isEmpty(), "coins left on the cursor: %s", menu.getCarried());
			equal(20, WalletItem.balance(player.getInventory().getItem(9)), "wallet after right-clicking it with 20 copper coins");
			// The other way: the wallet on the cursor, right-clicked onto coins.
			ItemStack wallet = player.getInventory().getItem(9);
			player.getInventory().setItem(9, ItemStack.EMPTY);
			player.getInventory().setItem(10, new ItemStack(ModItems.IRON_COIN, 3));
			menu.setCarried(wallet);
			menu.clicked(10, 1, ContainerInput.PICKUP, player);
			that(player.getInventory().getItem(10).isEmpty(), "iron coins left in the slot: %s", player.getInventory().getItem(10));
			equal(50, WalletItem.balance(menu.getCarried()), "wallet after right-clicking 3 iron coins with it");
		}));
	}

	// ---- 8. getting coins

	private static void chapter8(List<ServerCase> cases) {
		cases.add(partial("mobs_drop_coins_to_players", List.of("8-1", "8-2"), "プレイヤーが倒したゾンビは銅貨を落とし（ドロップ増加 III で増える）、プレイヤー以外に倒されたゾンビは落とさず、オーバーワールドでは金貨を落とさないこと（鉄貨のまれな確率は見ていない）",
				ctx -> {
					ServerPlayer player = ctx.player(ctx.center().south(3));
					int plain = killZombies(ctx, player, 60, true);
					ItemStack sword = new ItemStack(Items.IRON_SWORD);
					sword.enchant(enchantment(ctx, Enchantments.LOOTING), 3);
					player.setItemInHand(InteractionHand.MAIN_HAND, sword);
					int looting = killZombies(ctx, player, 60, true);
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					int other = killZombies(ctx, player, 40, false);
					that(plain > 20, "60 zombies killed by the player dropped %d copper coins", plain);
					that(looting > plain + 20, "Looting III did not add coins (%d without, %d with)", plain, looting);
					equal(0, other, "copper coins from zombies not killed by a player");
				}));
		cases.add(full("iron_minting_switch", List.of("8-3b"), ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			Item[] nine = {Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK};
			that(ServerCases.craft(ctx, nine).isEmpty(), "iron blocks mint coins before the switch is on");
			ctx.config(cfg -> cfg.currency.allowIronMinting = true);
			CompletableFuture<Void> on = reload(ctx);
			ctx.later(1, c -> waitFor(c, on, 1200, () -> {
				ItemStack out = ServerCases.craft(c, nine);
				that(out.is(ModItems.IRON_COIN) && out.getCount() == 64, "9 iron blocks gave %s after the switch and /reload", out);
				// Back to the default for the later cases.
				c.config(cfg -> cfg.currency.allowIronMinting = false);
				CompletableFuture<Void> off = reload(c);
				c.later(1, c2 -> waitFor(c2, off, 1200, () -> that(ServerCases.craft(c2, nine).isEmpty(), "iron minting stayed on after switching it off")));
			}));
		}));
		cases.add(full("sneak_use_breaks_coin", List.of("8-5"), ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			ctx.emptyInventory(player);
			player.getInventory().setItem(0, new ItemStack(ModItems.IRON_COIN, 2));
			player.getInventory().setSelectedSlot(0);
			player.setShiftKeyDown(true);
			player.gameMode.useItem(player, ctx.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
			player.setShiftKeyDown(false);
			equal(1, ctx.count(player, ModItems.IRON_COIN), "iron coins after sneak + right-click");
			equal(CoinType.IRON.ratioToLower(), ctx.count(player, ModItems.COPPER_COIN), "copper coins after breaking one iron coin");
			that(((CoinItem) ModItems.IRON_COIN).type().lower() == CoinType.COPPER, "iron does not break into copper");
		}));
	}

	/** Kills {@code n} zombies (by the player or by plain damage) and returns the copper coins they dropped. */
	private static int killZombies(ServerCtx c, ServerPlayer player, int n, boolean byPlayer) {
		BlockPos at = c.center().north(3);
		AABB box = new AABB(at).inflate(4);
		for (ItemEntity e : c.level().getEntitiesOfClass(ItemEntity.class, box)) e.discard();
		for (int i = 0; i < n; i++) {
			Zombie z = EntityTypes.ZOMBIE.spawn(c.level(), at, EntitySpawnReason.COMMAND);
			that(z != null, "no zombie was spawned");
			z.setNoAi(true);
			z.hurtServer(c.level(), byPlayer ? c.level().damageSources().playerAttack(player) : c.level().damageSources().generic(), 1000);
			that(z.isDeadOrDying(), "the zombie did not die");
		}
		int copper = 0;
		for (ItemEntity e : c.level().getEntitiesOfClass(ItemEntity.class, box)) {
			that(!e.getItem().is(ModItems.GOLD_COIN), "a zombie in the overworld dropped a gold coin");
			if (e.getItem().is(ModItems.COPPER_COIN)) copper += e.getItem().getCount();
			e.discard();
		}
		for (Zombie z : c.level().getEntitiesOfClass(Zombie.class, box)) z.discard();
		return copper;
	}

	private static CompletableFuture<Void> reload(ServerCtx c) {
		return c.server().reloadResources(c.server().getPackRepository().getSelectedIds());
	}

	private static void waitFor(ServerCtx c, CompletableFuture<?> future, int ticks, Runnable then) {
		if (future.isDone()) {
			that(!future.isCompletedExceptionally(), "/reload failed");
			then.run();
			return;
		}
		that(ticks > 0, "/reload did not finish in time");
		c.later(1, cc -> waitFor(cc, future, ticks - 1, then));
	}

	// ---- 10. market names

	private static ItemStack named(Item item, String name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	private static void chapter10(List<ServerCase> cases) {
		cases.add(partial("name_tag_names_market", List.of("10-1", "10-3", "10-4"),
				"名付け親が名札を使うと名前が変わって名札が1枚減り、画面の題に名前が入り、ほかの人は変えられず（理由が出て名札は減らない）、コマンドで自動の名前に戻ること（掲示板の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					String autoName = MarketNames.name(market).getString();
					c.emptyHand(player);
					player.setItemInHand(InteractionHand.MAIN_HAND, named(Items.NAME_TAG, "麦の里"));
					c.forget(player);
					c.click(player, stall, Direction.SOUTH);
					equal(0, c.count(player, Items.NAME_TAG), "name tags after renaming");
					that("麦の里".equals(market.name()), "the market name is %s", market.name());
					that(c.gotMessage(player, "message.ak_trade_expansion.renamed"), "no \"renamed\" message: %s", c.messages(player));
					that(MarketNames.title(market).getString().contains("麦の里"), "the screen title does not show the name");
					// Another player (not the namer, not an OP) may not rename it.
					ServerPlayer other = c.player(stall.south(3));
					c.emptyHand(other);
					other.setItemInHand(InteractionHand.MAIN_HAND, named(Items.NAME_TAG, "よその名前"));
					c.click(other, stall, Direction.SOUTH);
					that("麦の里".equals(market.name()), "another player renamed the market to %s", market.name());
					equal(1, c.count(other, Items.NAME_TAG), "the other player's name tags");
					that(c.gotMessage(other, "message.ak_trade_expansion.rename_denied"), "no reason was shown to the other player: %s", c.messages(other));
					c.command(player, "aktradeex market name " + market.id() + " reset");
					that(market.name() == null, "the name was not reset (%s)", market.name());
					equal(0, autoName.compareTo(MarketNames.name(market).getString()), "auto name after reset");
				})));
		cases.add(full("named_stall_names_new_market", List.of("10-2"), ctx -> {
			BlockPos stall = ctx.center();
			ctx.village(stall, 3, 3);
			ctx.later(ServerCases.SETTLE, c -> {
				ServerPlayer player = c.player(stall.south(2));
				c.emptyHand(player);
				player.setItemInHand(InteractionHand.MAIN_HAND, named(ModBlocks.MARKET_STALL.asItem(), "川辺の市"));
				c.click(player, stall.below(), Direction.UP);
				Market market = c.marketAt(stall);
				that(market != null, "no market was founded");
				that("川辺の市".equals(market.name()), "the new market is named %s", market.name());
			});
		}));
	}

	// ---- 11. where a stall may go

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	/** Places a stall at {@code stall}; returns the market or {@code null} (and then the refusal key in {@code reason}). */
	private static Market tryFound(ServerCtx c, ServerPlayer player, BlockPos stall, List<String> reason) {
		c.forget(player);
		c.placeStall(player, stall);
		Market m = c.marketAt(stall);
		reason.clear();
		c.messages(player).stream().filter(k -> k.startsWith("message.ak_trade_expansion.new_market.")).forEach(reason::add);
		return m;
	}

	private static void removeMarket(ServerCtx c, Market m, BlockPos stall) {
		c.level().setBlock(stall, Blocks.AIR.defaultBlockState(), 3);
		c.registry().remove(m.id());
		for (ItemEntity e : c.level().getEntitiesOfClass(ItemEntity.class, new AABB(stall).inflate(4))) e.discard();
	}

	private static void chapter11(List<ServerCase> cases) {
		cases.add(full("enough_beds_allow_more_villagers", List.of("11-0"), ctx -> {
			BlockPos stall = ctx.center();
			for (int i = 0; i < 4; i++) ctx.bed(bedPos(stall, i));
			for (int i = 0; i < 6; i++) villager(ctx, stall.offset(-6 + 2 * i, 0, 6));
			ctx.later(ServerCases.SETTLE, c -> {
				ServerPlayer player = c.player(stall.south(2));
				List<String> reason = new ArrayList<>();
				that(tryFound(c, player, stall, reason) == null, "a market was founded with 4 beds and 6 villagers");
				that(reason.contains("message.ak_trade_expansion.new_market.beds_and_villagers.enough"), "the reason did not mention enough beds: %s", reason);
				c.bed(bedPos(stall, 4));
				c.later(ServerCases.SETTLE, c2 -> {
					for (ItemEntity e : c2.level().getEntitiesOfClass(ItemEntity.class, new AABB(stall).inflate(4))) e.discard();
					that(tryFound(c2, player, stall, reason) != null, "no market with 5 beds and 6 villagers: %s", reason);
				});
			});
		}));
		cases.add(partial("where_describes_spot", List.of("11-1"), "村の範囲・一番近い市場・ベッドと村人の数・ここに作った場合の規模・置いた時の結果の行が出て、数と結果が正しいこと（チャットの見た目は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					List<Component> lines = PlacementGuide.describe(player);
					List<String> keys = lines.stream().map(ServerWorldCases::key).toList();
					for (String k : List.of("header", "no_village", "market", "count", "level", "result")) {
						that(keys.contains("message.ak_trade_expansion.where." + k), "no \"%s\" line: %s", k, keys);
					}
					Component count = lines.get(keys.indexOf("message.ak_trade_expansion.where.count"));
					Object[] args = ((TranslatableContents) count.getContents()).getArgs();
					equal(3, ((Number) args[1]).longValue(), "beds in the \"where\" line");
					equal(3, ((Number) args[2]).longValue(), "villagers in the \"where\" line");
					Component result = lines.get(keys.indexOf("message.ak_trade_expansion.where.result"));
					Object inner = ((TranslatableContents) result.getContents()).getArgs()[0];
					that(inner instanceof Component ic && key(ic).equals("message.ak_trade_expansion.where.join"), "the result is not \"joins\": %s", inner);
				})));
		cases.add(full("new_market_requires_modes", List.of("11-3"), ctx -> {
			BlockPos stall = ctx.center();
			for (int i = 0; i < 3; i++) ctx.bed(bedPos(stall, i));
			ctx.later(ServerCases.SETTLE, c -> {
				ServerPlayer player = c.player(stall.south(2));
				List<String> reason = new ArrayList<>();
				c.config(cfg -> cfg.market.newMarketRequires = TradeExpansionConfig.NewMarketRequires.VILLAGE);
				that(tryFound(c, player, stall, reason) == null, "VILLAGE: founded outside a village");
				that(reason.contains("message.ak_trade_expansion.new_market.village"), "VILLAGE: reason %s", reason);
				c.config(cfg -> cfg.market.newMarketRequires = TradeExpansionConfig.NewMarketRequires.VILLAGERS);
				that(tryFound(c, player, stall, reason) == null, "VILLAGERS: founded with 3 beds and no villagers");
				that(reason.contains("message.ak_trade_expansion.new_market.villagers"), "VILLAGERS: reason %s", reason);
				c.config(cfg -> cfg.market.newMarketRequires = TradeExpansionConfig.NewMarketRequires.BEDS);
				Market byBeds = tryFound(c, player, stall, reason);
				that(byBeds != null, "BEDS: no market with 3 beds: %s", reason);
				removeMarket(c, byBeds, stall);
				for (int i = 0; i < 3; i++) removeBed(c, bedPos(stall, i));
				for (int i = 0; i < 3; i++) villager(c, stall.offset(-4 + 2 * i, 0, 6));
				c.later(ServerCases.SETTLE, c2 -> {
					that(tryFound(c2, player, stall, reason) == null, "BEDS: founded with villagers and no beds");
					that(reason.contains("message.ak_trade_expansion.new_market.beds"), "BEDS: reason %s", reason);
					c2.config(cfg -> cfg.market.newMarketRequires = TradeExpansionConfig.NewMarketRequires.VILLAGERS);
					that(tryFound(c2, player, stall, reason) != null, "VILLAGERS: no market with 3 villagers: %s", reason);
				});
			});
		}));
	}

	// ---- 12. villager trades in coins

	static final int BREAD = 0, WHEAT = 1, BOOK = 2, BELL = 3;

	/** A farmer with four offers: emerald → 6 bread, 20 wheat → emerald, 5 emeralds + book → bell, 10 emeralds → bell. */
	private static MerchantOffers offers() {
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f));
		offers.add(new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.of(new ItemCost(Items.BOOK)), new ItemStack(Items.BELL), 12, 1, 0.05f));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BELL), 12, 1, 0.05f));
		return offers;
	}

	static Villager farmer(ServerCtx c, BlockPos pos) {
		Villager v = villager(c, pos);
		v.setVillagerData(v.getVillagerData().withProfession(c.level().registryAccess(), VillagerProfession.FARMER).withLevel(2));
		v.setOffers(offers());
		return v;
	}

	/** Right-clicks the merchant as a player does; returns the merchant screen. */
	static MerchantMenu openMerchant(ServerCtx c, ServerPlayer player, net.minecraft.world.entity.Entity merchant) {
		player.closeContainer();
		c.emptyHand(player);
		player.interactOn(merchant, InteractionHand.MAIN_HAND, Vec3.ZERO);
		that(player.containerMenu instanceof MerchantMenu, "the merchant screen did not open");
		return (MerchantMenu) player.containerMenu;
	}

	/** Chooses an offer in the list (what ServerboundSelectTradePacket does). */
	static ItemStack select(MerchantMenu menu, int index) {
		menu.setSelectionHint(index);
		menu.tryMoveItems(index);
		return menu.getSlot(2).getItem();
	}

	private static void chapter12(List<ServerCase> cases) {
		cases.add(partial("villager_coin_trades", List.of("12-2", "12-3", "12-4", "12-5"),
				"取引を選ぶと支払い欄が空のまま結果欄に品が出て、取ると所持金が減り、Shift でお金が尽きるまで買え、足りないと品が出ず、支払い欄のエメラルドは使われないこと（「代金（所持）」の赤い表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					Villager v = farmer(c, stall.east(4));
					c.emptyInventory(player);
					MerchantMenu menu = openMerchant(c, player, v);
					that(VillagerCoins.active(player), "no coin prices were sent");
					long price = VillagerCoins.price(player, BREAD);
					that(price >= TradeExpansionConfig.get().villager.emeraldPrice, "bread price %d", price);
					// No money: nothing in the result slot, and emeralds in the payment slot do not pay (12-4, 12-5).
					that(select(menu, BREAD).isEmpty(), "the result shows without money: %s", menu.getSlot(2).getItem());
					menu.getSlot(0).set(new ItemStack(Items.EMERALD, 10));
					that(menu.getSlot(2).getItem().isEmpty(), "emeralds in the payment slot bought the coin trade");
					menu.clicked(2, 0, ContainerInput.PICKUP, player);
					that(menu.getCarried().isEmpty(), "the result could be taken without money");
					equal(10, menu.getSlot(0).getItem().getCount(), "emeralds in the payment slot");
					player.getInventory().add(menu.getSlot(0).getItem().copy());
					menu.getSlot(0).set(ItemStack.EMPTY);
					player.getInventory().clearContent();
					// With money: the bread shows with empty payment slots; taking it charges the price (12-2).
					c.giveMoney(player, price * 3 + price / 2);
					long money = c.balance(player);
					ItemStack shown = select(menu, BREAD);
					that(shown.is(Items.BREAD) && shown.getCount() == 6, "the result slot shows %s", shown);
					that(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "something was moved into the payment slots");
					menu.clicked(2, 0, ContainerInput.PICKUP, player);
					that(menu.getCarried().is(Items.BREAD) && menu.getCarried().getCount() == 6, "taken: %s", menu.getCarried());
					equal(money - price, c.balance(player), "money after one trade");
					player.getInventory().add(menu.getCarried().copy());
					menu.setCarried(ItemStack.EMPTY);
					// Shift-click: buys until the money runs out (12-3).
					select(menu, BREAD);
					menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
					equal(18, c.count(player, Items.BREAD), "bread after shift-clicking");
					that(c.balance(player) < price, "money left after shift-clicking: %d (price %d)", c.balance(player), price);
				})));
		cases.add(full("villager_other_costs", List.of("12-6", "12-7"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			Villager v = farmer(c, stall.east(4));
			c.emptyInventory(player);
			player.getInventory().add(new ItemStack(Items.WHEAT, 20));
			player.getInventory().add(new ItemStack(Items.BOOK, 1));
			MerchantMenu menu = openMerchant(c, player, v);
			// Selling crops stays an emerald trade (12-7).
			equal(-1, VillagerCoins.price(player, WHEAT), "coin price of selling wheat");
			ItemStack emerald = select(menu, WHEAT);
			that(emerald.is(Items.EMERALD), "selling 20 wheat shows %s", emerald);
			menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
			equal(1, c.count(player, Items.EMERALD), "emeralds after selling wheat");
			equal(0, c.count(player, Items.WHEAT), "wheat left");
			// Emeralds + book: the book goes into the payment slot, the emeralds are paid in coins (12-6).
			long price = VillagerCoins.price(player, BOOK);
			equal(5L * TradeExpansionConfig.get().villager.emeraldPrice, price, "coin price of 5 emeralds + book");
			c.giveMoney(player, price);
			long money = c.balance(player);
			ItemStack bell = select(menu, BOOK);
			that(bell.is(Items.BELL), "with a book the result shows %s", bell);
			that(menu.getSlot(0).getItem().is(Items.BOOK) || menu.getSlot(1).getItem().is(Items.BOOK), "the book was not moved into a payment slot");
			menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
			equal(1, c.count(player, Items.BELL), "bells bought");
			equal(money - price, c.balance(player), "money after buying with a book");
			that(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "the book was not used up");
			equal(1, c.count(player, Items.EMERALD), "emeralds (not used for the coin trade)");
		})));
		cases.add(partial("villager_bread_above_market", List.of("12-8"), "パン6個の代金が、市場の売値と基準価格×1.4 より安くないこと（農民の本物の取引表は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					Identifier bread = Identifier.parse("ak_trade_expansion:bread");
					long now = MarketUpkeep.touch(c.level(), market);
					if (market.good(bread) == null) market.addGood(bread, new GoodState(1.0, 0, GoodsCatalog.get(bread).targetStock(), now));
					Villager v = farmer(c, stall.east(4));
					openMerchant(c, player, v);
					long price = VillagerCoins.price(player, BREAD);
					long floor = (long) Math.ceil(GoodsCatalog.get(bread).basePrice() * TradeExpansionConfig.get().villager.minGoodsPriceMultiplier - 1e-9) * 6;
					that(price >= floor, "6 bread cost %dc, below base price x multiplier (%dc)", price, floor);
					long marketSell = VillagerCoins.bestMarketSell(c.level(), player.blockPosition(), bread, 6);
					that(marketSell > 0 && price > marketSell, "6 bread cost %dc, the market pays %dc for them", price, marketSell);
				})));
		cases.add(full("coin_trade_switches", List.of("12-10", "12-11"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			Villager v = farmer(c, stall.east(4));
			WanderingTrader w = EntityTypes.WANDERING_TRADER.spawn(c.level(), stall.west(4), EntitySpawnReason.COMMAND);
			that(w != null, "no wandering trader was spawned");
			w.setNoAi(true);
			w.getOffers().clear();
			w.getOffers().addAll(offers());
			openMerchant(c, player, w);
			that(VillagerCoins.active(player) && VillagerCoins.price(player, BELL) > 0, "the wandering trader does not take coins");
			c.config(cfg -> cfg.villager.wanderingTraderCoins = false);
			openMerchant(c, player, w);
			that(!VillagerCoins.active(player), "the wandering trader still takes coins with wanderingTraderCoins off");
			openMerchant(c, player, v);
			that(VillagerCoins.active(player), "villagers stopped taking coins with wanderingTraderCoins off");
			c.config(cfg -> cfg.villager.coinTrades = false);
			c.emptyInventory(player);
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			MerchantMenu menu = openMerchant(c, player, v);
			that(!VillagerCoins.active(player), "villagers still take coins with coinTrades off");
			ItemStack shown = select(menu, BREAD);
			that(shown.is(Items.BREAD), "with coinTrades off an emerald does not buy bread: %s", shown);
		})));
		cases.add(partial("villager_discount_lowers_coins", List.of("12-12"), "村の英雄の効果で村人が値引きすると、コインの代金も安くなること（評判を上げる・ゾンビ村人を治すは見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					Villager v = farmer(c, stall.east(4));
					openMerchant(c, player, v);
					long normal = VillagerCoins.price(player, BELL);
					equal(10L * TradeExpansionConfig.get().villager.emeraldPrice, normal, "coin price of 10 emeralds");
					player.closeContainer();
					player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 2_000, 0));
					openMerchant(c, player, v);
					long discounted = VillagerCoins.price(player, BELL);
					that(discounted < normal, "the hero of the village pays %dc (normally %dc)", discounted, normal);
					equal((long) v.getOffers().get(BELL).getCostA().getCount() * TradeExpansionConfig.get().villager.emeraldPrice, discounted, "coin price after the discount");
				})));
	}
}
