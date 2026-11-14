package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * The server-side checks of docs/release/ingame-checklist.md, in checklist order. Each case names the checklist items
 * it covers; {@code partial} cases say (in Japanese, shown in the checklist) what part of the item they check. Cases
 * with no checklist item (failures, invalid requests) only appear in the report.
 */
public final class ServerCases {
	private ServerCases() {
	}

	/** Ticks for the POI of new beds and new entities to be seen. */
	static final int SETTLE = 3;

	public static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();

		// ---- 2. stall and basic trades
		cases.add(full("stall_recipe", List.of("2-1"), ctx -> {
			Item P = Items.OAK_PLANKS;
			ItemStack out = craft(ctx, Items.CARPET.white(), Items.CARPET.white(), Items.CARPET.white(), P, Items.EMERALD, P, P, Items.BARREL, P);
			that(out.is(ModBlocks.MARKET_STALL.asItem()), "the stall recipe gave %s", out);
		}));
		cases.add(full("stall_in_village", List.of("2-3"), ctx -> {
			BlockPos stall = ctx.center();
			ctx.village(stall, 3, 3);
			ctx.later(SETTLE, c -> {
				ServerPlayer player = c.player(stall.south(2));
				c.placeStall(player, stall);
				that(c.isStall(stall), "the stall was not placed");
				that(c.marketAt(stall) != null, "no market was founded");
				that(c.gotMessage(player, "message.ak_trade_expansion.stall_joined"), "no \"joined\" message: %s", c.messages(player));
			});
		}));
		cases.add(full("stall_outside_village", List.of("2-4"), ctx -> {
			BlockPos stall = ctx.center();
			ServerPlayer player = ctx.player(stall.south(2));
			ctx.placeStall(player, stall);
			that(!ctx.isStall(stall), "the stall stayed without beds or villagers");
			that(ctx.registry().all().isEmpty(), "a market was founded");
			that(ctx.messages(player).stream().anyMatch(k -> k.startsWith("message.ak_trade_expansion.new_market.")),
					"no reason (beds / villagers) was shown: %s", ctx.messages(player));
			ctx.later(1, c -> that(!c.level().getEntitiesOfClass(ItemEntity.class, new AABB(stall).inflate(3),
					e -> e.getItem().is(ModBlocks.MARKET_STALL.asItem())).isEmpty(), "the stall item did not drop"));
		}));
		cases.add(full("stall_too_close", List.of("2-5"), ctx -> {
			BlockPos first = ctx.center();
			ctx.village(first, 3, 3);
			BlockPos second = first.east(100);
			ctx.floor(second);
			ctx.village(second, 3, 3);
			ctx.later(SETTLE, c -> {
				ServerPlayer player = c.player(first.south(2));
				c.placeStall(player, first);
				that(c.marketAt(first) != null, "the first market was not founded");
				ServerPlayer other = c.player(second.south(2));
				c.placeStall(other, second);
				that(!c.isStall(second), "a stall 100 blocks away was placed");
				equal(1, c.registry().all().size(), "markets");
				that(c.gotMessage(other, "message.ak_trade_expansion.stall_too_close"), "no distance reason: %s", c.messages(other));
			});
		}));
		cases.add(partial("stall_opens_menu", List.of("2-8"), "サーバー側で交易画面が開くことのみ", ctx -> withMarket(ctx, (c, player, market, stall) -> {
			var menu = c.openStall(player, stall);
			equal(market.id(), menu.marketId(), "market of the opened screen");
		})));
		cases.add(partial("buy_pays_total", List.of("2-10"), "品が手に入り、所持金が合計額どおり減ることのみ（財布へのお釣りは見ていない）", ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			Identifier good = c.tradableGood(market, 3, 0);
			Item item = c.item(good);
			int before = c.count(player, item);
			long money = c.balance(player);
			long total = c.trade(player, market, good, true, 3);
			equal(before + 3, c.count(player, item), "items after buying 3");
			equal(money - total, c.balance(player), "money after buying");
		})));
		cases.add(partial("buy_raises_price_until_sold_out", List.of("2-11"), "買うたびに値段が上がり、在庫が0になると買えないこと（「売り切れ」「入荷まで」の表示は見ていない）",
				ctx -> withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.giveMoney(player, 1_000_000);
					Identifier good = c.tradableGood(market, 4, 0);
					long p0 = c.buyPrice(player, market, good);
					c.trade(player, market, good, true, 2);
					long p1 = c.buyPrice(player, market, good);
					c.trade(player, market, good, true, 2);
					long p2 = c.buyPrice(player, market, good);
					that(p0 < p1 && p1 < p2, "buy price did not rise: %d -> %d -> %d", p0, p1, p2);
					// Buy the rest of the stock, then one more.
					int rest = (int) Math.floor(market.good(good).stock());
					if (rest > 0) {
						long expected = c.previewTotal(player, market, good, true, rest);
						equal(TradeResult.SUCCESS.ordinal(), c.tradeRaw(player, market.id(), good, true, rest, expected, false, true).ordinal(), "buying the rest of the stock");
					}
					that(!PriceModel.canBuy(market.good(good)), "the good can still be bought with stock %.2f", market.good(good).stock());
					String before = c.fingerprint(player);
					var result = c.tradeRaw(player, market.id(), good, true, 1, c.previewTotal(player, market, good, true, 1), false, false);
					that(result == TradeResult.INSUFFICIENT_STOCK, "buying from an empty stock gave %s", result);
					that(before.equals(c.fingerprint(player)), "buying from an empty stock changed something");
				})));
		cases.add(partial("sell_pays_and_lowers_price_until_full", List.of("2-12"), "所持金が増えて値段が下がり、在庫が上限で売れないこと（「買取停止」の表示は見ていない）",
				ctx -> withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Identifier good = c.tradableGood(market, 0, 8);
					Item item = c.item(good);
					player.getInventory().add(new ItemStack(item, 8));
					long money = c.balance(player);
					long p0 = c.sellPrice(player, market, good);
					long total = c.trade(player, market, good, false, 8);
					equal(money + total, c.balance(player), "money after selling 8");
					equal(0, c.count(player, item), "items left after selling all 8");
					long p1 = c.sellPrice(player, market, good);
					that(p1 < p0, "sell price did not fall: %d -> %d", p0, p1);
					// Sell up to the stock limit, then one more.
					long now = MarketUpkeep.touch(c.level(), market);
					int capacity = PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), good, now), market.good(good));
					that(capacity <= 2000, "the stock limit of %s is too large to test (%d)", good, capacity);
					giveItems(player, item, capacity + 1);
					if (capacity > 0) {
						long expected = c.previewTotal(player, market, good, false, capacity);
						var r = c.tradeRaw(player, market.id(), good, false, capacity, expected, false, true);
						that(r == TradeResult.SUCCESS, "selling up to the stock limit (%d) gave %s", capacity, r);
					}
					String before = c.fingerprint(player);
					var result = c.tradeRaw(player, market.id(), good, false, 1, c.previewTotal(player, market, good, false, 1), false, false);
					that(result == TradeResult.STOCK_FULL, "selling above the stock limit gave %s", result);
					that(before.equals(c.fingerprint(player)), "selling above the stock limit changed something");
				})));
		cases.add(partial("shift_all_beyond_limit", List.of("2-14"), "上限（64）を超える数量の Shift 取引をサーバーが受け付けることのみ（最大数は画面側で計算）",
				ctx -> withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.giveMoney(player, 1_000_000);
					int limit = TradeExpansionConfig.get().trade.maxPerTransaction;
					Identifier good = c.tradableGood(market, limit + 1, 0);
					long expected = c.previewTotal(player, market, good, true, limit + 1);
					var plain = c.tradeRaw(player, market.id(), good, true, limit + 1, expected, false, false);
					that(plain == TradeResult.INVALID_REQUEST, "%d without Shift gave %s", limit + 1, plain);
					var all = c.tradeRaw(player, market.id(), good, true, limit + 1, expected, false, true);
					that(all == TradeResult.SUCCESS, "%d with Shift gave %s", limit + 1, all);
					equal(limit + 1, c.count(player, c.item(good)), "items after the Shift trade");
				})));
		cases.add(full("change_drops_when_full", List.of("2-15"), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			Identifier good = cheapGood(c, player, market, 900);
			long price = c.previewTotal(player, market, good, true, 1);
			// One diamond coin (1,000c) and a full inventory: the change cannot fit and drops at the feet.
			c.emptyInventory(player);
			player.getInventory().setItem(0, new ItemStack(ModItems.DIAMOND_COIN));
			for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			var result = c.tradeRaw(player, market.id(), good, true, 1, price, false, false);
			that(result == TradeResult.SUCCESS, "buying with a full inventory gave %s", result);
			equal(1, c.count(player, c.item(good)), "bought item in the freed coin slot");
			c.later(1, cc -> {
				long dropped = cc.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4), e -> coinValue(e.getItem()) > 0)
						.stream().mapToLong(e -> coinValue(e.getItem()) * e.getItem().getCount()).sum();
				equal(1000 - price, dropped + cc.balance(player), "change (dropped coins + money left)");
				that(dropped > 0, "no change dropped at the feet");
			});
		})));
		cases.add(full("creative_cannot_trade", List.of("2-17"), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			Identifier good = c.tradableGood(market, 1, 0);
			String before = c.fingerprint(player);
			var result = c.tradeRaw(player, market.id(), good, true, 1, c.previewTotal(player, market, good, true, 1), false, false);
			that(result == TradeResult.CREATIVE_NOT_ALLOWED, "a creative trade gave %s", result);
			that(net.minecraft.locale.Language.getInstance().has(result.translationKey()), "no text for %s", result.translationKey());
			that(before.equals(c.fingerprint(player)), "a creative trade changed something");
		})));

		// ---- failures that have no checklist item: nothing may change
		cases.add(full("buy_without_money", List.of(), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.emptyInventory(player);
			Identifier good = c.tradableGood(market, 1, 0);
			String before = c.fingerprint(player);
			var result = c.tradeRaw(player, market.id(), good, true, 1, c.previewTotal(player, market, good, true, 1), false, false);
			that(result == TradeResult.INSUFFICIENT_FUNDS, "buying without money gave %s", result);
			that(before.equals(c.fingerprint(player)), "buying without money changed something");
		})));
		cases.add(full("sell_without_items", List.of(), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			Identifier good = c.tradableGood(market, 0, 1);
			String before = c.fingerprint(player);
			var result = c.tradeRaw(player, market.id(), good, false, 1, c.previewTotal(player, market, good, false, 1), false, false);
			that(result == TradeResult.INSUFFICIENT_ITEMS, "selling without the item gave %s", result);
			that(before.equals(c.fingerprint(player)), "selling without the item changed something");
		})));
		cases.add(full("price_changed_is_refused", List.of(), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			Identifier good = c.tradableGood(market, 1, 0);
			long right = c.previewTotal(player, market, good, true, 1);
			String before = c.fingerprint(player);
			for (long wrong : new long[] {right + 1, right - 1, 0}) {
				var result = c.tradeRaw(player, market.id(), good, true, 1, wrong, false, false);
				that(result == TradeResult.PRICE_CHANGED, "a total of %d instead of %d gave %s", wrong, right, result);
			}
			that(before.equals(c.fingerprint(player)), "a wrong total changed something");
		})));
		cases.add(full("card_without_card", List.of(), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			Identifier good = c.tradableGood(market, 1, 0);
			String before = c.fingerprint(player);
			var result = c.tradeRaw(player, market.id(), good, true, 1, c.previewTotal(player, market, good, true, 1), true, false);
			that(result == TradeResult.CARD_INVALID, "paying by card without a card gave %s", result);
			that(before.equals(c.fingerprint(player)), "paying by card without a card changed something");
		})));
		cases.add(full("unknown_or_missing_good", List.of(), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			String before = c.fingerprint(player);
			var unknown = c.tradeRaw(player, market.id(), Identifier.fromNamespaceAndPath("ak_trade_expansion", "no_such_good"), true, 1, 1, false, false);
			that(unknown == TradeResult.NOT_AVAILABLE, "an unknown good gave %s", unknown);
			// A known good this market does not have.
			Identifier missing = GoodsCatalog.all().keySet().stream().filter(id -> market.good(id) == null).findFirst().orElseThrow();
			var notHere = c.tradeRaw(player, market.id(), missing, true, 1, 1, false, false);
			that(notHere == TradeResult.NOT_AVAILABLE, "a good the market does not have gave %s", notHere);
			that(before.equals(c.fingerprint(player)), "an unavailable good changed something");
		})));

		// ---- 3, 16. the screen's preview = the server's total (A-4; trade.priceTolerance is 0)
		cases.add(partial("snapshot_totals_match", List.of("3-10"), "画面と同じ計算（スナップショットの値）で 1・16・64 個とも確定できること（割引の表示は見ていない）",
				ctx -> withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.giveMoney(player, 1_000_000);
					Identifier good = c.tradableGood(market, 1 + 16 + 64, 0);
					for (int qty : new int[] {1, 16, 64}) buyAsScreen(c, player, market, good, qty);
					int sellQty = 16;
					giveItems(player, c.item(good), sellQty);
					sellAsScreen(c, player, market, good, sellQty);
				})));
		cases.add(partial("reputation_prices_match", List.of("16-3"), "評判「お得意様」で買値が下がり売値が上がり、表示どおりに確定できること（評判の名前の表示は見ていない）",
				ctx -> withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.giveMoney(player, 1_000_000);
					Identifier good = c.tradableGood(market, 2, 0);
					var plain = view(c.lastSnapshot(player), good);
					c.command(player, "aktradeex reputation set 400");
					c.forget(player);
					c.openStall(player, stall);
					MarketSnapshotS2C snap = c.lastSnapshot(player);
					var better = view(snap, good);
					that(snap.reputation().level() >= 3, "reputation level after \"set 400\": %d", snap.reputation().level());
					that(PriceModel.buyPrice(better.params(), better.pressure()) < PriceModel.buyPrice(plain.params(), plain.pressure()), "the buy price did not fall");
					that(PriceModel.sellPrice(better.params(), better.pressure()) > PriceModel.sellPrice(plain.params(), plain.pressure()), "the sell price did not rise");
					buyAsScreen(c, player, market, good, 2);
				})));

		// ---- 4. prices over time
		cases.add(full("advance_recovers_half", List.of("4-1"), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			Identifier good = c.tradableGood(market, 0, 16);
			long base = c.sellPrice(player, market, good);
			int qty = 16;
			player.getInventory().add(new ItemStack(c.item(good), qty));
			c.trade(player, market, good, false, qty);
			long low = c.sellPrice(player, market, good);
			that(low < base, "selling %d did not lower the price (%d -> %d)", qty, base, low);
			double x0 = market.good(good).pressure();
			c.command(player, "aktradeex market advance 2");
			long after = c.sellPrice(player, market, good);
			double x1 = market.good(good).pressure();
			that(low < after && after <= base, "the price did not come back: %d -> %d -> %d", base, low, after);
			// The market pressure X halves every half-life H (B-3; consumables recover faster); the price follows X but
			// not linearly.
			double halfLife = market.params(good).halfLifeDays();
			double expected = Math.pow(0.5, 2 / halfLife);
			that(Math.abs(x1 / x0 - expected) < 0.05, "after 2 days the pressure went %.3f -> %.3f (%.0f%% left), expected %.0f%% (half-life %.1f days)",
					x0, x1, 100 * x1 / x0, 100 * expected, halfLife);
		})));

		// ---- 7. wallet
		cases.add(full("wallet_recipe", List.of("7-1"), ctx -> {
			Item L = Items.LEATHER, S = Items.STRING;
			ItemStack out = craft(ctx, L, S, L, L, S, L, Items.AIR, L, Items.AIR);
			that(out.is(ModItems.WALLET), "the wallet recipe gave %s", out);
		}));

		// ---- 8. coins
		cases.add(full("minting_copper_not_iron", List.of("8-3"), ctx -> {
			Item c = Items.COPPER_BLOCK.weathering().unaffected(), i = Items.IRON_BLOCK;
			ItemStack copper = craft(ctx, c, c, c, c, c, c, c, c, c);
			that(copper.is(ModItems.COPPER_COIN) && copper.getCount() == 64, "9 copper blocks gave %s", copper);
			ItemStack iron = craft(ctx, i, i, i, i, i, i, i, i, i);
			that(iron.isEmpty(), "9 iron blocks gave %s (iron minting is off by default)", iron);
		}));
		cases.add(full("emerald_coin_stonecutter", List.of("8-4"), ctx -> {
			var recipe = ctx.server().getRecipeManager().getRecipeFor(RecipeType.STONECUTTING, new SingleRecipeInput(new ItemStack(Items.EMERALD_BLOCK)), ctx.level());
			that(recipe.isPresent(), "no stonecutter recipe for the emerald block");
			ItemStack out = recipe.get().value().assemble(new SingleRecipeInput(new ItemStack(Items.EMERALD_BLOCK)));
			that(out.is(ModItems.EMERALD_COIN) && out.getCount() == 1, "the stonecutter gave %s", out);
		}));

		// ---- 17. starting money
		cases.add(partial("starting_money", List.of("17-1"), "初めて入った時に 240c もらえることのみ（入り直しは見ていない）", ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			equal(TradeExpansionConfig.get().currency.startingMoney, ctx.balance(player), "money of a new player");
		}));

		cases.addAll(ServerRequestCases.all());
		return cases;
	}

	// ---- helpers

	@FunctionalInterface
	interface MarketBody {
		void run(ServerCtx ctx, ServerPlayer player, Market market, BlockPos stall) throws Exception;
	}

	/** A village with a crafted stall and its market at the center, then {@code body} with a player next to it. */
	static void withMarket(ServerCtx ctx, MarketBody body) {
		BlockPos stall = ctx.center();
		ctx.village(stall, 3, 3);
		ctx.later(SETTLE, c -> {
			ServerPlayer player = c.player(stall.south(2));
			c.placeStall(player, stall);
			Market market = c.marketAt(stall);
			that(market != null, "no market was founded for the case");
			body.run(c, player, market, stall);
		});
	}

	static void giveItems(ServerPlayer player, Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = count; left > 0; left -= max) player.getInventory().add(new ItemStack(item, Math.min(max, left)));
	}

	/** Puts {@code count} items outside the hotbar ("sell all" leaves the hotbar alone, A-4). */
	static void giveItemsOutsideHotbar(ServerPlayer player, Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		int left = count;
		for (int slot = 9; slot < 36 && left > 0; slot++) {
			if (!player.getInventory().getItem(slot).isEmpty()) continue;
			int n = Math.min(max, left);
			player.getInventory().setItem(slot, new ItemStack(item, n));
			left -= n;
		}
		that(left == 0, "no room for %d %s outside the hotbar", count, item);
	}

	/** A good of {@code market} that costs at most {@code maxPrice} for one (unit 1, no components). */
	private static Identifier cheapGood(ServerCtx c, ServerPlayer player, Market market, long maxPrice) {
		for (Identifier id : market.regularGoods()) {
			GoodsDefinition def = GoodsCatalog.get(id);
			GoodState state = market.good(id);
			if (def == null || state == null || !def.buy() || def.unit() != 1 || def.components().isPresent() || state.stock() < 2) continue;
			if (BuiltInRegistries.ITEM.getValue(def.item()) == Items.AIR) continue;
			if (c.buyPrice(player, market, id) <= maxPrice) return id;
		}
		throw new AutotestCheck.Failed("market #" + market.id() + " has no good below " + maxPrice + "c");
	}

	private static final Map<Item, CoinType> COINS = Map.of(ModItems.COPPER_COIN, CoinType.COPPER, ModItems.IRON_COIN, CoinType.IRON,
			ModItems.GOLD_COIN, CoinType.GOLD, ModItems.DIAMOND_COIN, CoinType.DIAMOND, ModItems.NETHERITE_COIN, CoinType.NETHERITE);

	private static long coinValue(ItemStack stack) {
		CoinType type = COINS.get(stack.getItem());
		return type == null ? 0 : type.value();
	}

	static MarketSnapshotS2C.GoodView view(MarketSnapshotS2C snapshot, Identifier good) {
		that(snapshot != null, "no market snapshot was sent");
		return snapshot.goods().stream().filter(v -> v.goods().equals(good)).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("the snapshot has no " + good));
	}

	/** Buys as the trade screen does: the total from the last snapshot the player got (MarketScreen.previewTotal). */
	private static void buyAsScreen(ServerCtx c, ServerPlayer player, Market market, Identifier good, int qty) {
		MarketSnapshotS2C snap = c.lastSnapshot(player);
		var v = view(snap, good);
		long expected = PriceModel.sumBuy(v.params(), v.pressure(), qty, v.unit(), snap.bulkDiscount());
		long money = c.balance(player);
		var result = c.tradeRaw(player, market.id(), good, true, qty, expected, false, false);
		that(result == TradeResult.SUCCESS, "buying %d for the shown %dc gave %s", qty, expected, result);
		equal(money - expected, c.balance(player), "money after buying " + qty + " for the shown total");
	}

	private static void sellAsScreen(ServerCtx c, ServerPlayer player, Market market, Identifier good, int qty) {
		MarketSnapshotS2C snap = c.lastSnapshot(player);
		var v = view(snap, good);
		long expected = PriceModel.sumSell(v.params(), v.pressure(), qty);
		long money = c.balance(player);
		var result = c.tradeRaw(player, market.id(), good, false, qty, expected, false, false);
		that(result == TradeResult.SUCCESS, "selling %d for the shown %dc gave %s", qty, expected, result);
		equal(money + expected, c.balance(player), "money after selling " + qty + " for the shown total");
	}

	/** Result of a 3x3 crafting grid (row by row; AIR = empty), or EMPTY. */
	private static ItemStack craft(ServerCtx ctx, Item... grid) {
		List<ItemStack> stacks = Arrays.stream(grid).map(i -> i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i)).toList();
		CraftingInput input = CraftingInput.of(3, 3, stacks);
		return ctx.server().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, ctx.level())
				.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
	}
}
