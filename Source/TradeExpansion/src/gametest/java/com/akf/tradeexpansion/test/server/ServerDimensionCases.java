package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;

/**
 * Server cases in the Nether and the End (checklist 5-5, 8-2b, 17-2 to 17-4, 27-4). The markets there stand in a
 * closed stone room ({@link ServerCtx#floor(ServerLevel, BlockPos)}) with villagers (no beds: they explode there).
 * The Nether market stands at 1/8 of the test area's coordinates, so that it is linked with a market in the test area.
 */
final class ServerDimensionCases {
	private ServerDimensionCases() {
	}

	/** Height of the rooms in the Nether and the End (above the lava sea, below the bedrock roof). */
	private static final int ROOM_Y = 80;

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(full("nether_market_goods", List.of("5-5", "17-4"), ctx -> {
			ServerLevel nether = ctx.dimension(Level.NETHER);
			BlockPos stall = netherPos(ctx);
			room(ctx, nether, stall, 3, c -> {
				Market market = found(c, nether, stall);
				that(market.dimension().equals(Level.NETHER), "the market is in %s", market.dimension().identifier());
				that(market.origin() == MarketOrigin.CRAFTED, "the market of a crafted stall has the origin %s", market.origin());
				that(TradeExpansion.id("default_the_nether").equals(market.profileId()), "the Nether market has the profile %s", market.profileId());
				Set<Identifier> nethers = goodsOf("default_the_nether");
				Set<Identifier> crafted = goodsOf("crafted_stall");
				List<Identifier> goods = market.regularGoods();
				that(goods.size() >= 5, "a Nether market with only %d goods: %s", goods.size(), goods);
				for (Identifier id : goods) {
					GoodsDefinition def = GoodsCatalog.get(id);
					that(nethers.contains(id) || def != null && def.autoAdd().isPresent(), "%s is not a Nether good (goods %s)", id, goods);
				}
				that(goods.contains(TradeExpansion.id("gold_nugget")), "the Nether market does not deal in gold nuggets: %s", goods);
				// Goods of the settlement (crafted stall) list that the Nether list does not have.
				for (Identifier id : crafted) {
					that(nethers.contains(id) || !goods.contains(id), "the Nether market deals in %s of the settlement list", id);
				}
				that(goods.stream().anyMatch(id -> !crafted.contains(id)), "every good is one of the settlement list: %s", goods);
			});
		}));
		cases.add(full("nether_prices_follow_overworld", List.of("17-2", "17-3"), ctx -> {
			ServerLevel nether = ctx.dimension(Level.NETHER);
			BlockPos home = ctx.center();
			BlockPos far = netherPos(ctx);
			ctx.village(home, 3, 3);
			room(ctx, nether, far, 3, c -> {
				Market overworld = found(c, c.level(), home);
				Market linked = found(c, nether, far);
				double d = MarketRegistry.linkedDistance(overworld, linked);
				that(d < com.akf.tradeexpansion.config.TradeExpansionConfig.get().distance.nearRadius, "the markets are %.0f blocks apart (Nether x8)", d);
				ServerPlayer player = c.player(home.south(2));
				c.openStall(player, home);
				Identifier good = ServerChapterCases.shared(c, overworld, 0, 20, linked);

				// Linked (the default): selling in the Overworld lowers the Nether price as well.
				double[] moved = sell(c, player, overworld, linked, good, 10);
				// Selling raises X (the oversupply), which lowers the price.
				that(moved[0] > 0, "selling did not lower the Overworld price (X %+.3f)", moved[0]);
				that(moved[1] >= moved[0] * 0.5, "the Nether market followed by X %+.3f only (the Overworld one %+.3f)", moved[1], moved[0]);
				that(moved[3] <= moved[2], "the Nether sell price rose from %.0fc to %.0fc", moved[2], moved[3]);

				// Not linked: the Nether market stays where it is.
				c.config(cfg -> cfg.distance.linkNetherToOverworld = false);
				moved = sell(c, player, overworld, linked, good, 10);
				that(moved[0] > 0, "selling did not lower the Overworld price (X %+.3f)", moved[0]);
				that(Math.abs(moved[1]) < 1e-9, "the Nether market moved by X %+.5f with linkNetherToOverworld off", moved[1]);
			});
		}));
		cases.add(full("advancements_nether_and_end", List.of("27-4"), ctx -> {
			ServerLevel nether = ctx.dimension(Level.NETHER);
			ServerLevel end = ctx.dimension(Level.END);
			BlockPos home = ctx.center();
			BlockPos inNether = netherPos(ctx);
			// Between the main island and the outer islands (no dragon close by).
			BlockPos inEnd = new BlockPos(300, ROOM_Y, 300);
			ctx.village(home, 3, 3);
			room(ctx, nether, inNether, 3, c1 -> room(c1, end, inEnd, 3, c -> {
				Market[] markets = {found(c, c.level(), home), found(c, nether, inNether), found(c, end, inEnd)};
				BlockPos[] stalls = {home, inNether, inEnd};
				ServerLevel[] levels = {c.level(), nether, end};
				String[] advancements = {null, "nether_market", "end_market"};
				for (int i = 0; i < 3; i++) {
					// The same player in all three: the first one travels (as through a portal).
					ServerPlayer player = i == 0 ? c.player(home.south(2)) : traveller(c, levels[i], stalls[i]);
					c.openStall(player, stalls[i]);
					c.giveMoney(player, 100_000);
					if (advancements[i] != null) ServerAdvancementCases.notYet(c, player, advancements[i]);
					ServerAdvancementCases.notYet(c, player, "peddler");
					Identifier good = c.tradableGood(markets[i], 1, 0);
					c.trade(player, markets[i], good, true, 1);
					that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying in %s gave %s", levels[i].dimension().identifier(), c.lastTradeResult(player));
					if (advancements[i] != null) ServerAdvancementCases.done(c, player, advancements[i], "after trading in " + levels[i].dimension().identifier());
					if (i == 0) TRAVELLER.put(c, player);
				}
				ServerAdvancementCases.done(c, TRAVELLER.get(c), "peddler", "after trading at markets of 3 dimensions");
			}));
		}));
		cases.add(ServerCase.partial("level5_nether_and_end_goods", List.of("30-4"),
				"村人24人（レベル5）のネザー・エンドの市場で、並ぶ商品が候補の一部だけで（すべての候補が並ぶことはない）、market reroll の後も同じ数の候補の一部であること。同じ市場で reroll を繰り返すと品揃えが変わり（reroll の回数を乱数に混ぜる）、ネザーの別の場所（400 ブロック離れた所）の村人24人の市場とも品揃えが違うこと",
				ctx -> {
					ServerLevel nether = ctx.dimension(Level.NETHER);
					ServerLevel end = ctx.dimension(Level.END);
					BlockPos inNether = netherPos(ctx);
					BlockPos inNether2 = inNether.east(400);
					BlockPos inEnd = new BlockPos(300, ROOM_Y, 300);
					room(ctx, nether, inNether, 24, c1 -> room(c1, end, inEnd, 24, c0 -> room(c0, nether, inNether2, 24, c -> {
						Map<String, List<Identifier>> first = new HashMap<>();
						for (Object[] where : new Object[][] {{nether, inNether, "default_the_nether"}, {end, inEnd, "default_the_end"}, {nether, inNether2, "default_the_nether"}}) {
							ServerLevel level = (ServerLevel) where[0];
							BlockPos at = (BlockPos) where[1];
							Market market = found(c, level, at);
							equal(5, market.level(), "level of a market with 24 villagers in " + level.dimension().identifier() + " at " + at.toShortString());
							Set<Identifier> candidates = goodsOf((String) where[2]);
							Set<List<Identifier>> draws = new HashSet<>();
							int rerolls = market.rerolls();
							for (int roll = 0; roll < 3; roll++) {
								List<Identifier> picked = market.regularGoods().stream().filter(candidates::contains).sorted().toList();
								draws.add(picked);
								that(picked.size() >= 10, "%s: only %d goods at level 5: %s", level.dimension().identifier(), picked.size(), picked);
								that(picked.size() < candidates.size(), "%s: all %d candidates are picked at level 5", level.dimension().identifier(), candidates.size());
								first.putIfAbsent(level.dimension().identifier() + "@" + at.toShortString(), picked);
								c.command(c.player(level, at.south(3)), "aktradeex market reroll " + market.id());
							}
							equal(rerolls + 3, market.rerolls(), "rerolls counted in " + level.dimension().identifier());
							that(draws.size() >= 2, "%s at %s: 2 rerolls drew the same goods every time: %s", level.dimension().identifier(), at.toShortString(), draws);
						}
						List<Identifier> a = first.get(nether.dimension().identifier() + "@" + inNether.toShortString());
						List<Identifier> b = first.get(nether.dimension().identifier() + "@" + inNether2.toShortString());
						that(!a.equals(b), "two level 5 Nether markets 400 blocks apart picked the same goods: %s", a);
					})));
				}));
		cases.add(full("nether_mobs_drop_gold_coins", List.of("8-2b"), ctx -> {
			ServerLevel nether = ctx.dimension(Level.NETHER);
			BlockPos at = netherPos(ctx);
			room(ctx, nether, at, 0, c -> {
				ServerPlayer inNether = c.player(nether, at.south(3));
				ServerPlayer inOverworld = c.player(c.center().south(3));
				int[] gold = new int[2];
				int[] copper = new int[2];
				// 2,000 blazes in the Nether (0.5% each: about 10 gold coins), 1,000 in the Overworld (none), in parts.
				for (int part = 0; part < 12; part++) {
					boolean inTheNether = part < 8;
					c.later(1, cc -> {
						int k = inTheNether ? 0 : 1;
						int[] got = killBlazes(cc, inTheNether ? nether : cc.level(), inTheNether ? inNether : inOverworld,
								(inTheNether ? at : cc.center()).north(3), 250);
						gold[k] += got[0];
						copper[k] += got[1];
					});
				}
				c.later(1, cc -> {
					that(copper[0] > 500 && copper[1] > 250, "blazes killed by a player dropped few copper coins (Nether %d, Overworld %d)", copper[0], copper[1]);
					that(gold[0] > 0, "2,000 blazes killed in the Nether dropped no gold coin");
					that(gold[0] <= 40, "2,000 blazes killed in the Nether dropped %d gold coins (about 10 expected)", gold[0]);
					equal(0, gold[1], "gold coins from 1,000 blazes killed in the Overworld");
				});
			});
		}));
		return cases;
	}

	// ---- helpers

	private static final java.util.Map<ServerCtx, ServerPlayer> TRAVELLER = new java.util.WeakHashMap<>();

	/** The player of the first market moves to {@code level} next to {@code stall} (as through a portal). */
	private static ServerPlayer traveller(ServerCtx c, ServerLevel level, BlockPos stall) {
		ServerPlayer player = TRAVELLER.get(c);
		player.closeContainer();
		player.teleportTo(level, stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5, Set.of(), 0, 0, true);
		return player;
	}

	/** The Nether position linked with the test area (1/8 of its coordinates). */
	private static BlockPos netherPos(ServerCtx ctx) {
		BlockPos o = ctx.center();
		return new BlockPos(Math.floorDiv(o.getX(), 8), ROOM_Y, Math.floorDiv(o.getZ(), 8));
	}

	/** A stone room at {@code pos} in {@code level} with {@code villagers} villagers; runs {@code then} once they stand. */
	private static void room(ServerCtx ctx, ServerLevel level, BlockPos pos, int villagers, ServerCase.Body then) {
		ctx.floor(level, pos);
		ctx.later(1, c -> whenLoaded(c, level, pos, 6000, cc -> {
			cc.villagers(level, pos, villagers);
			cc.later(20, then);
		}));
	}

	/** Runs {@code then} once the entities around {@code pos} are loaded (checking every tick, at most {@code ticks}). */
	private static void whenLoaded(ServerCtx c, ServerLevel level, BlockPos pos, int ticks, ServerCase.Body then) throws Exception {
		int r = ServerCtx.FLOOR;
		boolean loaded = java.util.stream.Stream.of(pos, pos.offset(-r, 0, -r), pos.offset(r, 0, -r), pos.offset(-r, 0, r), pos.offset(r, 0, r))
				.allMatch(level::isPositionEntityTicking);
		if (loaded) {
			then.run(c);
			return;
		}
		that(ticks > 0, "the chunks of %s did not load in time", level.dimension().identifier());
		c.later(1, cc -> whenLoaded(cc, level, pos, ticks - 1, then));
	}

	/** A player places a stall at {@code stall} in {@code level}; returns the market it founded. */
	private static Market found(ServerCtx c, ServerLevel level, BlockPos stall) {
		ServerPlayer founder = c.player(level, stall.south(2));
		c.placeStall(founder, stall);
		Market market = c.marketAt(level, stall);
		that(market != null, "no market was founded in %s: %s (villagers %d)", level.dimension().identifier(), c.messages(founder),
				com.akf.tradeexpansion.market.MarketFactory.countVillagers(level, stall));
		return market;
	}

	/** Every good of a market profile (always and in the pools). */
	private static Set<Identifier> goodsOf(String profile) {
		MarketProfile p = MarketProfiles.get(TradeExpansion.id(profile));
		that(p != null, "no market profile %s", profile);
		Set<Identifier> out = new HashSet<>(p.always());
		p.pools().forEach(pool -> pool.entries().forEach(e -> out.add(e.goods())));
		return out;
	}

	/**
	 * Sells {@code qty} of {@code good} at {@code market}; returns how far X moved there and at {@code other}, and the
	 * sell price at {@code other} before and after.
	 */
	private static double[] sell(ServerCtx c, ServerPlayer player, Market market, Market other, Identifier good, int qty) {
		MarketUpkeep.touch(c.levelOf(market), market);
		MarketUpkeep.touch(c.levelOf(other), other);
		double x0 = market.good(good).pressure();
		double y0 = other.good(good).pressure();
		long p0 = c.sellPrice(player, other, good);
		player.getInventory().add(new ItemStack(c.item(good), qty));
		c.forget(player);
		c.trade(player, market, good, false, qty);
		that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling %d %s gave %s", qty, good, c.lastTradeResult(player));
		MarketUpkeep.touch(c.levelOf(other), other);
		return new double[] {market.good(good).pressure() - x0, other.good(good).pressure() - y0, p0, c.sellPrice(player, other, good)};
	}

	/** Kills {@code n} blazes at {@code at} with {@code player}'s attack; returns the gold and copper coins they dropped. */
	private static int[] killBlazes(ServerCtx c, ServerLevel level, ServerPlayer player, BlockPos at, int n) {
		AABB box = new AABB(at).inflate(4);
		for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
		for (int i = 0; i < n; i++) {
			Blaze blaze = EntityTypes.BLAZE.spawn(level, at, EntitySpawnReason.COMMAND);
			that(blaze != null, "no blaze was spawned");
			blaze.setNoAi(true);
			blaze.hurtServer(level, level.damageSources().playerAttack(player), 1000);
			that(blaze.isDeadOrDying(), "the blaze did not die");
		}
		int gold = 0;
		int copper = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
			if (e.getItem().is(ModItems.GOLD_COIN)) gold += e.getItem().getCount();
			if (e.getItem().is(ModItems.COPPER_COIN)) copper += e.getItem().getCount();
		}
		for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
		return new int[] {gold, copper};
	}
}
