package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.network.CargoDeliverC2S;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.network.SellAllC2S;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.quest.Cargo;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.staff.StaffService;
import com.akf.tradeexpansion.trade.SellAllService;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * Server cases of the chapters where money and saved data change: 4 (prices), 7 (wallet, bank, card), 18 (sell all),
 * 9 and 21 (requests, transport requests) and 26 (staff). Time-dependent cases switch the market clock to the day clock
 * ({@code recovery.clock = DAY_TIME}) before the markets exist and move it with {@code /time add}.
 */
final class ServerChapterCases {
	private ServerChapterCases() {
	}

	private static final long DAY = PriceModel.TICKS_PER_DAY;

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		chapter4(cases);
		chapter7(cases);
		chapter18(cases);
		chapter9(cases);
		chapter21(cases);
		chapter26(cases);
		return cases;
	}

	// ---- 4. prices over time

	private static void chapter4(List<ServerCase> cases) {
		cases.add(full("propagation_by_distance", List.of("4-2"), ctx -> markets(ctx, new int[] {0, 300, 900}, (c, player, m) -> {
			Market a = m.get(0), near = m.get(1), far = m.get(2);
			Identifier good = shared(c, a, 0, 32, near, far);
			double nearX = near.good(good).pressure(), farX = far.good(good).pressure();
			ServerCases.giveItems(player, c.item(good), 32);
			c.trade(player, a, good, false, 32);
			equal(0, c.count(player, c.item(good)), "items left after selling 32 at A");
			MarketUpkeep.touch(c.level(), near);
			MarketUpkeep.touch(c.level(), far);
			double dNear = near.good(good).pressure() - nearX, dFar = far.good(good).pressure() - farX;
			TradeExpansionConfig.Distance d = TradeExpansionConfig.get().distance;
			double link = GoodsCatalog.get(good).link();
			double wNear = DistanceModel.weight(MarketRegistry.linkedDistance(a, near), d.nearRadius, d.farRadius, d.falloffExponent);
			double wFar = DistanceModel.weight(MarketRegistry.linkedDistance(a, far), d.nearRadius, d.farRadius, d.falloffExponent);
			that(Math.abs(dNear - 32 * wNear * link) < 0.5, "300 blocks away X moved %.2f, expected %.2f", dNear, 32 * wNear * link);
			that(Math.abs(dFar - 32 * wFar * link) < 0.5, "900 blocks away X moved %.2f, expected %.2f", dFar, 32 * wFar * link);
			that(dNear > dFar && dFar > 0, "the farther market did not move less (300: %.2f, 900: %.2f)", dNear, dFar);
			that(c.sellPrice(player, near, good) <= PriceModel.sellPrice(near.params(good), nearX), "the price 300 blocks away did not fall");
		})));
		cases.add(full("consumables_come_back", List.of("4-4"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			Identifier good = c.tradableGood(market, 0, 1);
			// Make the chosen good a consumable for this case (B-3), whatever this market happens to sell.
			String item = GoodsCatalog.get(good).item().toString();
			c.config(cfg -> cfg.consumables.add.add(item));
			that(GoodsCatalog.get(good).consumable(TradeExpansionConfig.get()), "%s did not become a consumable", good);
			long now = MarketUpkeep.touch(c.level(), market);
			int capacity = PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), good, now), market.good(good));
			that(capacity > 0 && capacity <= 2000, "stock room of %s: %d", good, capacity);
			ServerCases.giveItems(player, c.item(good), capacity + 1);
			var r = c.tradeRaw(player, market.id(), good, false, capacity, c.previewTotal(player, market, good, false, capacity), false, true);
			that(r == TradeResult.SUCCESS, "selling up to the stock limit gave %s", r);
			var full = c.tradeRaw(player, market.id(), good, false, 1, c.previewTotal(player, market, good, false, 1), false, false);
			that(full == TradeResult.STOCK_FULL, "selling at the stock limit gave %s", full);
			c.command(player, "aktradeex market advance 1");
			now = MarketUpkeep.touch(c.level(), market);
			that(PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), good, now), market.good(good)) >= 1, "no room after a day");
			var again = c.tradeRaw(player, market.id(), good, false, 1, c.previewTotal(player, market, good, false, 1), false, false);
			that(again == TradeResult.SUCCESS, "selling the next day gave %s", again);
		})));
		cases.add(full("goods_set_base_price", List.of("4-5"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			Identifier good = c.tradableGood(market, 1, 0);
			double before = market.params(good).basePrice();
			double data = GoodsCatalog.get(good).basePrice();
			try {
				c.command(player, "aktradeex goods set " + good + " base_price " + (long) Math.ceil(data * 3));
				c.forget(player);
				c.openStall(player, stall);
				double raised = ServerCases.view(c.lastSnapshot(player), good).params().basePrice();
				that(raised > before * 2, "base price after \"goods set\": %.1f -> %.1f", before, raised);
			} finally {
				c.command(player, "aktradeex goods reset " + good); // the override is saved in the config file
			}
			c.forget(player);
			c.openStall(player, stall);
			double back = ServerCases.view(c.lastSnapshot(player), good).params().basePrice();
			that(Math.abs(back - before) < 1e-6, "base price after \"goods reset\": %.1f (was %.1f)", back, before);
		})));
	}

	// ---- 7. wallet, bank, card

	private static void chapter7(List<ServerCase> cases) {
		cases.add(partial("wallet_picks_up_coins", List.of("7-2"), "財布を持って拾ったコインが財布に入り、インベントリに出ないこと（拾う音・表示は見ていない）", ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			ctx.emptyInventory(player);
			player.getInventory().setItem(0, new ItemStack(ModItems.WALLET));
			ItemEntity coins = new ItemEntity(ctx.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(ModItems.IRON_COIN, 5));
			coins.setNoPickUpDelay();
			ctx.level().addFreshEntity(coins);
			coins.playerTouch(player);
			equal(0, ctx.count(player, ModItems.IRON_COIN), "iron coins in the inventory");
			equal(50, WalletItem.balance(player.getInventory().getItem(0)), "wallet after picking up 5 iron coins");
		}));
		cases.add(full("wallet_capacity", List.of("7-5"), ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			ctx.emptyInventory(player);
			player.getInventory().setItem(0, new ItemStack(ModItems.WALLET));
			long capacity = WalletItem.capacity();
			ctx.giveMoney(player, capacity + 5_000);
			equal(capacity, WalletItem.balance(player.getInventory().getItem(0)), "wallet balance");
			equal(capacity + 5_000, ctx.balance(player), "money (wallet + coins)");
			// A full wallet leaves picked-up coins in the inventory.
			ItemEntity coins = new ItemEntity(ctx.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(ModItems.COPPER_COIN, 3));
			coins.setNoPickUpDelay();
			ctx.level().addFreshEntity(coins);
			coins.playerTouch(player);
			equal(capacity, WalletItem.balance(player.getInventory().getItem(0)), "full wallet after picking up coins");
		}));
		cases.add(partial("bank_deposit_withdraw_death", List.of("7-6", "7-7"), "預け入れ・引き出しで残高が増減し、死ぬと財布ごと落ちて銀行の残高は減らないこと（画面の表示は見ていない）",
				ctx -> {
					ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
					ServerCases.withMarket(ctx, (c, player, market, stall) -> {
						c.openStall(player, stall);
						c.emptyInventory(player);
						player.getInventory().setItem(0, new ItemStack(ModItems.WALLET));
						c.giveMoney(player, 5_000);
						BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 3_000));
						equal(3_000, bank(player), "bank after depositing 3,000c");
						equal(2_000, c.balance(player), "money after depositing 3,000c");
						BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.WITHDRAW, 1_000));
						equal(2_000, bank(player), "bank after withdrawing 1,000c");
						equal(3_000, c.balance(player), "money after withdrawing 1,000c");
						player.closeContainer();
						clientLoaded(player); // a test player never says so, and is invulnerable until then
						player.kill(c.level());
						that(player.isDeadOrDying(), "the player did not die");
						c.later(2, cc -> {
							long wallets = cc.level().getEntitiesOfClass(ItemEntity.class, new AABB(stall).inflate(8), e -> e.getItem().is(ModItems.WALLET)).size();
							that(wallets == 1, "wallets dropped on death: %d (dead %s, keepInventory %s, wallets anywhere %d, items left %d)", wallets, player.isDeadOrDying(),
									cc.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY),
									cc.level().getEntitiesOfClass(ItemEntity.class, new AABB(stall).inflate(200), e -> e.getItem().is(ModItems.WALLET)).size(),
									cc.count(player, ModItems.WALLET));
							equal(2_000, bank(player), "bank after dying");
						});
					});
				}));
		cases.add(full("card_issue_and_pay", List.of("7-8", "7-9"), ctx -> {
			ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				c.giveMoney(player, 20_000);
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 10_000));
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
				equal(10_000 - TradeExpansionConfig.get().bank.cardFee, bank(player), "bank after the card fee");
				equal(1, c.count(player, ModItems.BANK_CARD), "cards");
				that(BankService.hasValidCard(player), "the new card is not valid");
				// Buy by card: the bank pays (with the card fee), the wallet does not.
				Identifier good = c.tradableGood(market, 1, 0);
				long price = c.previewTotal(player, market, good, true, 1);
				long money = c.balance(player), before = bank(player);
				var r = c.tradeRaw(player, market.id(), good, true, 1, price, true, false);
				that(r == TradeResult.SUCCESS, "buying by card gave %s", r);
				equal(money, c.balance(player), "money after buying by card");
				equal(before - BankService.cardCharge(market, price), bank(player), "bank after buying by card");
				// Sell by card: the proceeds go to the account.
				long sale = c.previewTotal(player, market, good, false, 1);
				before = bank(player);
				var s = c.tradeRaw(player, market.id(), good, false, 1, sale, true, false);
				that(s == TradeResult.SUCCESS, "selling by card gave %s", s);
				equal(before + sale, bank(player), "bank after selling by card");
				equal(money, c.balance(player), "money after selling by card");
			});
		}));
		cases.add(full("card_reissue_and_other_player", List.of("7-10"), ctx -> {
			ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				c.giveMoney(player, 1_000);
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 1_000));
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
				ItemStack first = findCard(player);
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
				equal(2, c.count(player, ModItems.BANK_CARD), "cards after reissuing");
				// Only the old card: not valid.
				player.getInventory().clearContent();
				player.getInventory().setItem(0, first);
				that(!BankService.hasValidCard(player), "the old card is still valid after reissuing");
				// Another player with this player's card: not valid either.
				ServerPlayer other = c.player(stall.south(3));
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
				ItemStack newest = findCard(player);
				that(BankService.hasValidCard(player), "the newest card is not valid");
				other.getInventory().setItem(0, newest.copy());
				that(!BankService.hasValidCard(other), "another player's card is valid");
			});
		}));
	}

	private static long bank(ServerPlayer player) {
		return BankService.accounts(player).balance(player.getUUID());
	}

	private static ItemStack findCard(ServerPlayer player) {
		for (ItemStack s : player.getInventory()) if (s.is(ModItems.BANK_CARD)) return s.copy();
		throw new AutotestCheck.Failed("no card in the inventory");
	}

	// ---- 18. sell all

	private static void chapter18(List<ServerCase> cases) {
		cases.add(partial("sell_all_leaves_hotbar", List.of("18-3"), "表示どおりの合計を受け取り、ホットバーの品は残ること（確認の枠・除外の操作は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.emptyInventory(player);
					List<Identifier> goods = market.regularGoods().stream().filter(g -> sellable(c, player, market, g, 4)).limit(2).toList();
					that(goods.size() == 2, "the market has fewer than 2 goods to sell");
					for (Identifier g : goods) ServerCases.giveItemsOutsideHotbar(player, c.item(g), 4);
					player.getInventory().setItem(0, new ItemStack(c.item(goods.get(0)), 3)); // in the hotbar: not sold
					long expected = 0;
					List<SellAllC2S.Line> lines = new ArrayList<>();
					for (Identifier g : goods) {
						expected += c.previewTotal(player, market, g, false, 4);
						lines.add(new SellAllC2S.Line(g, 4));
					}
					long money = c.balance(player);
					c.forget(player);
					SellAllService.handle(player, new SellAllC2S(market.id(), lines, expected, false));
					that(c.lastTradeResult(player) == TradeResult.SUCCESS, "sell all gave %s", c.lastTradeResult(player));
					equal(money + expected, c.balance(player), "money after selling all");
					equal(3, c.count(player, c.item(goods.get(0))), "items left (the hotbar ones)");
					equal(0, c.count(player, c.item(goods.get(1))), "items of the second good left");
				})));
		cases.add(partial("sell_all_stock_limit", List.of("18-4"), "上限を超える数では全体が中止され、売れる分だけなら売れること（「40個（64個中）」の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Identifier good = c.tradableGood(market, 0, 1);
					long now = MarketUpkeep.touch(c.level(), market);
					int capacity = PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), good, now), market.good(good));
					that(capacity <= 2000, "stock room too large to test (%d)", capacity);
					ServerCases.giveItemsOutsideHotbar(player, c.item(good), Math.min(capacity + 1, 27 * 64));
					String before = c.fingerprint(player);
					c.forget(player);
					SellAllService.handle(player, new SellAllC2S(market.id(), List.of(new SellAllC2S.Line(good, capacity + 1)), 1, false));
					that(c.lastTradeResult(player) == TradeResult.STOCK_FULL, "selling above the stock limit gave %s", c.lastTradeResult(player));
					that(before.equals(c.fingerprint(player)), "a refused sell all changed something");
					long expected = c.previewTotal(player, market, good, false, capacity);
					c.forget(player);
					SellAllService.handle(player, new SellAllC2S(market.id(), List.of(new SellAllC2S.Line(good, capacity)), expected, false));
					that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling what the market takes gave %s", c.lastTradeResult(player));
					equal(1, c.count(player, c.item(good)), "items left");
					// Now the good is "not buying": a line of it is refused.
					c.forget(player);
					SellAllService.handle(player, new SellAllC2S(market.id(), List.of(new SellAllC2S.Line(good, 1)), 1, false));
					that(c.lastTradeResult(player) == TradeResult.STOCK_FULL, "selling a good at its stock limit gave %s", c.lastTradeResult(player));
				})));
		cases.add(partial("sell_all_price_changed", List.of("18-5"), "ほかのプレイヤーが先に売ると「価格が変わった」で中止され、新しい相場が届くこと（画面の更新は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Identifier good = c.tradableGood(market, 0, 8);
					ServerCases.giveItemsOutsideHotbar(player, c.item(good), 4);
					long seen = c.previewTotal(player, market, good, false, 4); // what the confirm box shows
					ServerPlayer other = c.player(stall.south(3));
					c.openStall(other, stall);
					// The other player sells until the total of the 4 has changed (cheap goods move by less than 1c).
					for (int i = 0; i < 16 && c.previewTotal(player, market, good, false, 4) == seen; i++) {
						ServerCases.giveItems(other, c.item(good), 4);
						c.trade(other, market, good, false, 4);
					}
					that(c.previewTotal(player, market, good, false, 4) != seen, "the other player's sales did not change the total");
					String before = c.fingerprint(player);
					c.forget(player);
					SellAllService.handle(player, new SellAllC2S(market.id(), List.of(new SellAllC2S.Line(good, 4)), seen, false));
					that(c.lastTradeResult(player) == TradeResult.PRICE_CHANGED, "a stale total gave %s", c.lastTradeResult(player));
					that(before.equals(c.fingerprint(player)), "a stale total changed something");
					that(c.lastSnapshot(player) != null && c.lastSnapshot(player).marketId() == market.id(), "no new prices were sent");
				})));
	}

	private static boolean sellable(ServerCtx c, ServerPlayer player, Market market, Identifier g, int units) {
		GoodsDefinition def = GoodsCatalog.get(g);
		if (def == null || !def.sell() || def.unit() != 1 || def.components().isPresent()) return false;
		long now = MarketUpkeep.touch(c.level(), market);
		return PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), g, now), market.good(g)) >= units;
	}

	// ---- 9. requests and shortages

	private static void chapter9(List<ServerCase> cases) {
		cases.add(partial("quest_deliver", List.of("9-2"), "品を持って納品すると品が減り、報酬が入ること（一覧の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Request request = market.requests().stream().filter(r -> !r.isTransport()).min(Comparator.comparingInt(Request::difficulty))
							.orElseThrow(() -> new AutotestCheck.Failed("the market has no request"));
					c.command(player, "aktradeex reputation set 1000"); // 3-star requests need a reputation
					Item item = c.item(request.goods());
					ServerCases.giveItems(player, item, request.quantity() + 2);
					long money = c.balance(player);
					long now = MarketUpkeep.touch(c.level(), market);
					long reward = Reputation.reward(market, player.getUUID(), request, now);
					c.forget(player);
					QuestService.deliver(player, new QuestDeliverC2S(market.id(), request.id(), false));
					var result = c.payloads(player, QuestResultS2C.class);
					that(!result.isEmpty() && result.getLast().result() == TradeResult.SUCCESS, "delivering gave %s", result);
					equal(2, c.count(player, item), "items left after delivering");
					equal(money + reward, c.balance(player), "money after delivering");
					that(market.requests().stream().noneMatch(r -> r.id() == request.id()), "the request is still listed");
				})));
		cases.add(full("quests_expire_and_refill", List.of("9-3"), ctx -> {
			dayClock(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				List<Integer> before = market.requests().stream().filter(r -> !r.isTransport()).map(Request::id).toList();
				that(!before.isEmpty(), "the market has no request");
				long deadline = market.requests().stream().filter(r -> !r.isTransport()).mapToLong(Request::deadline).max().orElseThrow();
				long now = c.now();
				that(deadline - now <= Math.round(TradeExpansionConfig.get().quest.durationDays * DAY) + 1, "a request lasts longer than %s days",
						TradeExpansionConfig.get().quest.durationDays);
				addDays(c, player, 3.05);
				that(c.now() > deadline, "the market clock did not move (%d, deadline %d)", c.now(), deadline);
				MarketUpkeep.touch(c.level(), market);
				List<Integer> after = market.requests().stream().filter(r -> !r.isTransport()).map(Request::id).toList();
				that(after.stream().noneMatch(before::contains), "old requests are still listed after 3 days: %s / %s", before, after);
				that(!after.isEmpty(), "no new requests after 3 days");
			});
		}));
		cases.add(full("shortages_pay_more_and_rotate", List.of("9-4"), ctx -> {
			dayClock(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				MarketUpkeep.touch(c.level(), market);
				that(!market.shortages().isEmpty(), "the market has no shortage");
				Identifier good = market.shortages().keySet().iterator().next();
				double k = market.shortages().get(good);
				that(k > 1, "shortage multiplier %.2f", k);
				GoodState state = market.good(good);
				double base = GoodsCatalog.get(good).basePrice() * state.priceFactor();
				that(market.params(good).basePrice() > base * 1.1, "a shortage good's base price %.1f is not above %.1f", market.params(good).basePrice(), base);
				var first = java.util.Set.copyOf(market.shortages().keySet());
				// After 3 days the shortages are rolled again (they may by chance be the same goods: try a few periods).
				boolean changed = false;
				for (int i = 0; i < 4 && !changed; i++) {
					addDays(c, player, TradeExpansionConfig.get().demand.durationDays);
					MarketUpkeep.touch(c.level(), market);
					changed = !first.equals(market.shortages().keySet());
				}
				that(changed, "the shortages never changed over 12 days: %s", first);
			});
		}));
	}

	// ---- 21. transport requests

	private static void chapter21(List<ServerCase> cases) {
		cases.add(partial("transport_take_and_deliver", List.of("21-1", "21-2", "21-5", "21-6", "21-7"),
				"輸送依頼が1件あり、受け取ると保証金が引かれ荷箱が手に入り、出した市場では届けられず、届け先で報酬＋保証金を受け取って両方の評判が上がること（一覧・荷箱の説明の表示は見ていない）",
				ctx -> markets(ctx, new int[] {0, 600}, (c, player, m) -> {
					Market a = m.get(0), b = m.get(1);
					Request transport = transportOf(a, b);
					c.command(player, "aktradeex reputation set 1000"); // 3-star requests need a reputation
					long deposit = transport.transport().orElseThrow().deposit();
					c.giveMoney(player, deposit + 1_000);
					long money = c.balance(player);
					c.forget(player);
					QuestService.accept(player, new QuestAcceptC2S(a.id(), transport.id(), false));
					that(questResult(c, player) == TradeResult.SUCCESS, "taking the crate gave %s", questResult(c, player));
					equal(money - deposit, c.balance(player), "money after taking the crate");
					equal(1, c.count(player, ModItems.CARGO_CRATE), "crates");
					Cargo cargo = Cargo.of(findItem(player, ModItems.CARGO_CRATE));
					that(cargo != null && cargo.destination() == b.id() && cargo.source() == a.id(), "the crate's data: %s", cargo);
					that(a.requests().stream().anyMatch(r -> r.id() == transport.id() && r.taken()), "the request is not marked as taken");
					// At A (not the destination): nothing to deliver.
					c.forget(player);
					QuestService.deliverCargo(player, new CargoDeliverC2S(a.id(), false));
					that(questResult(c, player) == TradeResult.NO_CARGO, "delivering at the source gave %s", questResult(c, player));
					// At B.
					long now = c.now();
					double repA = Reputation.points(a, player.getUUID(), now), repB = Reputation.points(b, player.getUUID(), now);
					openAt(c, player, m, 1);
					money = c.balance(player);
					c.forget(player);
					QuestService.deliverCargo(player, new CargoDeliverC2S(b.id(), false));
					that(questResult(c, player) == TradeResult.SUCCESS, "delivering at the destination gave %s", questResult(c, player));
					equal(money + cargo.reward() + cargo.deposit(), c.balance(player), "money after delivering");
					equal(0, c.count(player, ModItems.CARGO_CRATE), "crates after delivering");
					that(a.requests().stream().noneMatch(r -> r.id() == transport.id()), "the request is still listed at A");
					now = c.now();
					that(Reputation.points(a, player.getUUID(), now) > repA, "reputation at A did not rise");
					that(Reputation.points(b, player.getUUID(), now) > repB, "reputation at B did not rise");
				})));
		cases.add(partial("transport_deposit_funds_and_card", List.of("21-3"), "所持金が保証金より少ないと受け取れず、カード払いなら口座から引かれること",
				ctx -> {
					ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						Request transport = transportOf(a, b);
						c.command(player, "aktradeex reputation set 1000"); // 3-star requests need a reputation
						long deposit = transport.transport().orElseThrow().deposit();
						c.emptyInventory(player);
						c.giveMoney(player, deposit - 1);
						String before = c.fingerprint(player);
						c.forget(player);
						QuestService.accept(player, new QuestAcceptC2S(a.id(), transport.id(), false));
						that(questResult(c, player) == TradeResult.INSUFFICIENT_FUNDS, "too little money gave %s", questResult(c, player));
						that(before.equals(c.fingerprint(player)), "a refused crate changed something");
						c.giveMoney(player, 2 * deposit + 1_000);
						BankService.handle(player, new BankRequestC2S(a.id(), BankRequestC2S.Action.DEPOSIT, 2 * deposit + 500));
						BankService.handle(player, new BankRequestC2S(a.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
						long bankBefore = bank(player), money = c.balance(player);
						c.forget(player);
						QuestService.accept(player, new QuestAcceptC2S(a.id(), transport.id(), true));
						that(questResult(c, player) == TradeResult.SUCCESS, "taking the crate by card gave %s", questResult(c, player));
						equal(bankBefore - BankService.cardCharge(a, deposit), bank(player), "bank after taking the crate by card");
						equal(money, c.balance(player), "money after taking the crate by card");
					});
				}));
		cases.add(partial("transport_expires", List.of("21-8"), "期限を過ぎると届け先で「期限が切れています」になり、出した市場の評判が下がること（荷箱の説明の表示は見ていない）",
				ctx -> {
					dayClock(ctx);
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						Request transport = transportOf(a, b);
						c.command(player, "aktradeex reputation set 1000"); // 3-star requests need a reputation
						c.giveMoney(player, transport.transport().orElseThrow().deposit() + 1_000);
						QuestService.accept(player, new QuestAcceptC2S(a.id(), transport.id(), false));
						equal(1, c.count(player, ModItems.CARGO_CRATE), "crates");
						double rep = Reputation.points(a, player.getUUID(), c.now());
						addDays(c, player, TradeExpansionConfig.get().quest.durationDays + 0.1);
						MarketUpkeep.touch(c.level(), a);
						that(Reputation.points(a, player.getUUID(), c.now()) < rep - 1, "reputation at A did not fall (%.1f -> %.1f)", rep,
								Reputation.points(a, player.getUUID(), c.now()));
						openAt(c, player, m, 1);
						c.forget(player);
						QuestService.deliverCargo(player, new CargoDeliverC2S(b.id(), false));
						that(questResult(c, player) == TradeResult.CARGO_EXPIRED, "delivering an expired crate gave %s", questResult(c, player));
						equal(1, c.count(player, ModItems.CARGO_CRATE), "the expired crate is kept");
					});
				}));
		cases.add(full("transport_per_market_zero", List.of("21-9"), ctx -> {
			ctx.config(cfg -> cfg.quest.transportPerMarket = 0);
			markets(ctx, new int[] {0, 600}, (c, player, m) -> {
				c.command(player, "aktradeex market reroll " + m.get(0).id());
				that(m.get(0).requests().stream().noneMatch(Request::isTransport), "a transport request with transportPerMarket 0");
				that(m.get(0).requests().stream().anyMatch(r -> !r.isTransport()), "no ordinary requests either");
			});
		}));
		cases.add(full("transport_needs_another_market", List.of("21-10"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.command(player, "aktradeex market reroll " + market.id());
			that(market.requests().stream().noneMatch(Request::isTransport), "a transport request without another market");
			that(market.requests().stream().anyMatch(r -> !r.isTransport()), "no ordinary requests either");
		})));
	}

	private static Request transportOf(Market a, Market b) {
		return a.requests().stream().filter(r -> r.isTransport() && r.transport().get().destination() == b.id()).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("market A has no transport request to B: " + a.requests()));
	}

	private static TradeResult questResult(ServerCtx c, ServerPlayer player) {
		var list = c.payloads(player, QuestResultS2C.class);
		return list.isEmpty() ? null : list.getLast().result();
	}

	private static ItemStack findItem(ServerPlayer player, Item item) {
		for (ItemStack s : player.getInventory()) if (s.is(item)) return s;
		throw new AutotestCheck.Failed("no " + item + " in the inventory");
	}

	// ---- 26. staff

	private static void chapter26(List<ServerCase> cases) {
		cases.add(partial("staff_needs_reputation", List.of("26-2"), "評判「よそ者」では雇えないこと（ボタンと説明の表示は見ていない）",
				ctx -> markets(ctx, new int[] {0, 600}, (c, player, m) -> {
					c.giveMoney(player, 1_000);
					String before = c.fingerprint(player);
					var r = staff(c, player, m.get(0), StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, m.get(1).id(), 64, 0, 0, 0, false);
					that(r == TradeResult.REPUTATION_TOO_LOW, "hiring as a stranger gave %s", r);
					that(before.equals(c.fingerprint(player)), "a refused hire changed something");
				})));
		cases.add(partial("carrier_round_trip", List.of("26-5", "26-6", "26-7", "26-14", "26-22", "26-30"),
				"おまかせを雇い、資金不足 → 所持金を入れると仕入れて出かけ、行き先で売って戻り、歩合が利益の2割になること。出かけている間は解雇できず、休ませると戻ってから止まること。仕入れで相場が1個 0.25 動くこと。帰り荷 OFF では帰り荷を仕入れないこと（画面の表示は見ていない）",
				ctx -> {
					ctx.config(cfg -> {
						cfg.staff.accidentBase = 0;
						cfg.staff.carrierBackhaul = false;
					});
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						c.command(player, "aktradeex reputation set 150");
						c.giveMoney(player, 20_000);
						Identifier good = shared(c, a, 8, 0, b);
						a.good(good).setPressure(a.params(good).maxPressure() * 0.8); // cheap at A
						b.good(good).setPressure(b.params(good).minPressure() * 0.8); // dear at B: worth carrying
						long fee = TradeExpansionConfig.get().staff.carrierHireFee;
						long money = c.balance(player);
						var hired = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
						that(hired == TradeResult.SUCCESS, "hiring gave %s", hired);
						equal(money - fee, c.balance(player), "money after the hire fee");
						StaffS2C.View e = only(c, player, a);
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						that(e.status() == Employee.Status.WAIT_FUNDS.ordinal(), "status with an empty purse: %s (%s: A buys at %d, B pays %d)",
								Employee.Status.values()[e.status()], good,
								PriceModel.buyPrice(com.akf.tradeexpansion.core.StaffMath.withSpread(a.params(good), 0.5), a.good(good).pressure()),
								PriceModel.sellPrice(com.akf.tradeexpansion.core.StaffMath.withSpread(b.params(good), 0.5), b.good(good).pressure()));
						that(staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 5_000, false) == TradeResult.SUCCESS,
								"depositing 5,000c failed");
						double x0 = a.good(good).pressure(), s0 = a.good(good).stock();
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(Employee.Phase.OUTBOUND.ordinal(), e.phase(), "phase after buying");
						that(e.stock() > 0 && e.purse() < 5_000, "nothing was bought (stock %d, purse %d)", e.stock(), e.purse());
						double bought = s0 - a.good(good).stock();
						that(bought >= 1, "the dear good was not bought");
						that(Math.abs((x0 - a.good(good).pressure()) - bought * TradeExpansionConfig.get().staff.carrierPriceImpact) < 0.05,
								"buying %.0f moved X by %.2f (expected %.2f)", bought, x0 - a.good(good).pressure(), bought * 0.25);
						that(staff(c, player, a, StaffRequestC2S.Action.DISMISS, e.id(), Employee.AUTO, 0, 0, 0, 0, 0, false) == TradeResult.STAFF_AWAY,
								"a carrier on the road could be dismissed");
						that(staff(c, player, a, StaffRequestC2S.Action.PAUSE, e.id(), Employee.AUTO, 0, 0, 0, 0, 0, false) == TradeResult.SUCCESS, "pausing failed");
						c.command(player, "aktradeex staff hurry"); // arrives, sells, starts back
						e = only(c, player, a);
						equal(Employee.Phase.RETURNING.ordinal(), e.phase(), "phase after selling at B");
						equal(0, e.returnItems(), "return cargo with carrierBackhaul off");
						c.command(player, "aktradeex staff hurry"); // home; paused, so it stays
						e = only(c, player, a);
						equal(Employee.Phase.HOME.ordinal(), e.phase(), "phase after coming back");
						equal(1, e.trips(), "round trips");
						equal(0, e.stock(), "goods left");
						long profit = e.earned() - e.spent();
						that(profit > 0, "no profit (earned %d, spent %d)", e.earned(), e.spent());
						equal((long) Math.floor(profit * TradeExpansionConfig.get().staff.carrierCommission), e.wages(), "commission of the round trip");
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(1, e.trips(), "round trips of a paused carrier");
						equal(Employee.Status.PAUSED.ordinal(), e.status(), "status of a paused carrier at home");
					});
				}));
		cases.add(partial("carrier_return_cargo", List.of("26-27", "26-29"),
				"行きに儲からなくても帰り荷の見込みで空荷で出かけ、行き先で帰り荷を仕入れ、戻って売ると歩合に帰り荷の利益も入ること（ログの文と右パネルは見ていない）",
				ctx -> {
					ctx.config(cfg -> cfg.staff.accidentBase = 0);
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						c.command(player, "aktradeex reputation set 150");
						c.giveMoney(player, 20_000);
						shared(c, a, 0, 8, b); // a good both deal in (added to B if they share none)
						// Every good is dear at A: nothing pays on the way out, everything on the way back.
						for (Identifier g : a.goods().keySet()) a.good(g).setPressure(a.params(g).minPressure() * 0.8);
						var hired = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
						that(hired == TradeResult.SUCCESS, "hiring gave %s", hired);
						StaffS2C.View e = only(c, player, a);
						staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 10_000, false);
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						that(e.phase() == Employee.Phase.OUTBOUND.ordinal(), "phase %s / status %s (an empty trip for the return cargo)",
								Employee.Phase.values()[e.phase()], Employee.Status.values()[e.status()]);
						equal(0, e.stock(), "goods taken out");
						staff(c, player, a, StaffRequestC2S.Action.PAUSE, e.id(), Employee.AUTO, 0, 0, 0, 0, 0, false);
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(Employee.Phase.RETURNING.ordinal(), e.phase(), "phase after B");
						that(e.returnItems() > 0, "no return cargo was bought at B");
						long spent = e.spent();
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(Employee.Phase.HOME.ordinal(), e.phase(), "phase after coming back");
						equal(0, e.returnItems(), "return cargo left after selling at home");
						long profit = e.earned() - spent;
						that(profit > 0, "the return cargo made no profit (earned %d, spent %d)", e.earned(), spent);
						equal((long) Math.floor(profit * TradeExpansionConfig.get().staff.carrierCommission), e.wages(), "commission with the return cargo");
					});
				}));
		cases.add(partial("carrier_backhaul_off_no_empty_trip", List.of("26-30"), "帰り荷 OFF では、行きに儲からない時に空荷で出かけないこと（見込みの表示は見ていない）",
				ctx -> {
					ctx.config(cfg -> cfg.staff.carrierBackhaul = false);
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						c.command(player, "aktradeex reputation set 150");
						c.giveMoney(player, 20_000);
						for (Identifier g : a.goods().keySet()) a.good(g).setPressure(a.params(g).minPressure() * 0.8);
						staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
						StaffS2C.View e = only(c, player, a);
						staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 10_000, false);
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(Employee.Phase.HOME.ordinal(), e.phase(), "phase with nothing worth carrying");
						equal(Employee.Status.NO_MARGIN.ordinal(), e.status(), "status with nothing worth carrying");
					});
				}));
		cases.add(partial("clerk_sells_and_sold_out", List.of("26-11", "26-12"), "番頭を雇うと品が預けられ、売れて「売り切れ」になり、預けると再開し、引取で品が戻ること（一覧の見込みの表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.command(player, "aktradeex reputation set 150");
					c.giveMoney(player, 5_000);
					Identifier good = c.tradableGood(market, 0, 20);
					Item item = c.item(good);
					ServerCases.giveItems(player, item, 10);
					var hired = staff(c, player, market, StaffRequestC2S.Action.HIRE_CLERK, 0, good, 0, 4, 0, 1, 0, false);
					that(hired == TradeResult.SUCCESS, "hiring a clerk gave %s", hired);
					equal(6, c.count(player, item), "items left after leaving 4 with the clerk");
					c.command(player, "aktradeex staff hurry");
					StaffS2C.View e = only(c, player, market);
					equal(0, e.stock(), "goods left with the clerk");
					that(e.earned() > 0 && e.purse() == e.earned(), "the sales did not go to the purse (earned %d, purse %d)", e.earned(), e.purse());
					equal(Employee.Status.SOLD_OUT.ordinal(), e.status(), "status after selling everything");
					var more = staff(c, player, market, StaffRequestC2S.Action.DEPOSIT_ITEMS, e.id(), good, 0, 0, 0, 0, 0, false);
					that(more == TradeResult.SUCCESS, "leaving more goods gave %s", more);
					equal(0, c.count(player, item), "items left after leaving the rest");
					that(staff(c, player, market, StaffRequestC2S.Action.PAUSE, e.id(), good, 0, 0, 0, 0, 0, false) == TradeResult.SUCCESS, "pausing failed");
					c.command(player, "aktradeex staff hurry");
					e = only(c, player, market);
					equal(6, e.stock(), "goods with a paused clerk");
					var back = staff(c, player, market, StaffRequestC2S.Action.WITHDRAW_ITEMS, e.id(), good, 0, 0, 0, 0, 0, false);
					that(back == TradeResult.SUCCESS, "taking the goods back gave %s", back);
					equal(6, c.count(player, item), "items after taking them back");
				})));
		cases.add(partial("staff_dismiss_and_limit", List.of("26-16", "26-17"), "解雇で所持金と品が戻って一覧から消えること、3人まで雇えて4人目は断られること",
				ctx -> markets(ctx, new int[] {0, 600}, (c, player, m) -> {
					Market a = m.get(0), b = m.get(1);
					c.command(player, "aktradeex reputation set 150");
					c.giveMoney(player, 20_000);
					Identifier good = c.tradableGood(a, 0, 8);
					ServerCases.giveItems(player, c.item(good), 4);
					staff(c, player, a, StaffRequestC2S.Action.HIRE_CLERK, 0, good, 0, 4, 0, 1_000_000, 0, false); // too dear: keeps the goods
					for (int i = 0; i < 2; i++) {
						var r = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
						that(r == TradeResult.SUCCESS, "hiring carrier %d gave %s", i + 1, r);
					}
					var fourth = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
					that(fourth == TradeResult.STAFF_LIMIT, "a 4th employee gave %s", fourth);
					StaffS2C.View clerk = list(c, player, a).stream().filter(v -> v.role() == Employee.Role.CLERK.ordinal()).findFirst().orElseThrow();
					staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, clerk.id(), good, 0, 0, 0, 0, 700, false);
					long money = c.balance(player);
					var r = staff(c, player, a, StaffRequestC2S.Action.DISMISS, clerk.id(), good, 0, 0, 0, 0, 0, false);
					that(r == TradeResult.SUCCESS, "dismissing gave %s", r);
					equal(money + 700, c.balance(player), "money after dismissing");
					equal(4, c.count(player, c.item(good)), "goods after dismissing");
					equal(2, list(c, player, a).size(), "employees after dismissing one");
				})));
		cases.add(partial("staff_pay_by_card", List.of("26-19"), "カード払いで雇う・所持金を入れると口座から引かれ（手数料つき）、カードで出金すると口座に入ること",
				ctx -> {
					ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						c.command(player, "aktradeex reputation set 150");
						c.giveMoney(player, 10_000);
						BankService.handle(player, new BankRequestC2S(a.id(), BankRequestC2S.Action.DEPOSIT, 9_000));
						BankService.handle(player, new BankRequestC2S(a.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
						long bank = bank(player), money = c.balance(player);
						long fee = TradeExpansionConfig.get().staff.carrierHireFee;
						var r = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, true);
						that(r == TradeResult.SUCCESS, "hiring by card gave %s", r);
						equal(bank - BankService.cardCharge(a, fee), bank(player), "bank after hiring by card");
						equal(money, c.balance(player), "money after hiring by card");
						int id = only(c, player, a).id();
						bank = bank(player);
						staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, id, Employee.AUTO, 0, 0, 0, 0, 1_000, true);
						equal(bank - BankService.cardCharge(a, 1_000), bank(player), "bank after funding by card");
						bank = bank(player);
						staff(c, player, a, StaffRequestC2S.Action.WITHDRAW_MONEY, id, Employee.AUTO, 0, 0, 0, 0, 400, true);
						equal(bank + 400, bank(player), "bank after withdrawing to the card");
					});
				}));
		cases.add(full("staff_home_market_deleted", List.of("26-21"), ctx -> markets(ctx, new int[] {0, 600}, (c, player, m) -> {
			Market a = m.get(0), b = m.get(1);
			c.command(player, "aktradeex reputation set 150");
			c.giveMoney(player, 5_000);
			staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
			int id = only(c, player, a).id();
			staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, id, Employee.AUTO, 0, 0, 0, 0, 800, false);
			staff(c, player, a, StaffRequestC2S.Action.PAUSE, id, Employee.AUTO, 0, 0, 0, 0, 0, false); // keep the purse as it is
			player.closeContainer();
			c.command(player, "aktradeex market delete " + a.id());
			c.command(player, "aktradeex staff hurry");
			openAt(c, player, m, 1);
			StaffS2C.View e = list(c, player, b).stream().filter(v -> v.id() == id).findFirst()
					.orElseThrow(() -> new AutotestCheck.Failed("the employee is not listed at another market"));
			equal(Employee.Status.HOME_GONE.ordinal(), e.status(), "status after the home market was deleted");
			long money = c.balance(player);
			var r = staff(c, player, b, StaffRequestC2S.Action.WITHDRAW_MONEY, id, Employee.AUTO, 0, 0, 0, 0, 0, false);
			that(r == TradeResult.SUCCESS, "taking the purse at another market gave %s", r);
			equal(money + 800, c.balance(player), "money after taking the purse");
		})));
	}

	/** Sends a staff request; returns its result (null when the server sent none). */
	private static TradeResult staff(ServerCtx c, ServerPlayer player, Market market, StaffRequestC2S.Action action, int employee, Identifier goods,
			long destination, int quantity, long buyLimit, long sellLimit, long amount, boolean card) {
		c.forget(player);
		StaffService.handle(player, new StaffRequestC2S(market.id(), action, employee, goods, destination, quantity, buyLimit, sellLimit, amount, card));
		List<StaffS2C> answers = c.payloads(player, StaffS2C.class);
		return answers.isEmpty() ? null : answers.getLast().result().orElse(null);
	}

	/** The player's employees as the staff tab lists them (opened at {@code market}). */
	private static List<StaffS2C.View> list(ServerCtx c, ServerPlayer player, Market market) {
		c.forget(player);
		StaffService.handle(player, new StaffRequestC2S(market.id(), StaffRequestC2S.Action.LIST, 0, Employee.AUTO, 0, 0, 0, 0, 0, false));
		List<StaffS2C> answers = c.payloads(player, StaffS2C.class);
		that(!answers.isEmpty(), "no staff list was sent");
		return answers.getLast().staff();
	}

	private static StaffS2C.View only(ServerCtx c, ServerPlayer player, Market market) {
		List<StaffS2C.View> views = list(c, player, market);
		equal(1, views.size(), "employees");
		return views.getFirst();
	}

	// ---- helpers

	@FunctionalInterface
	interface MarketsBody {
		void run(ServerCtx ctx, ServerPlayer player, List<Market> markets) throws Exception;
	}

	private static final java.util.Map<ServerCtx, List<BlockPos>> STALLS = new java.util.WeakHashMap<>();

	/**
	 * Markets east of the test area at the given offsets (the first is the test area itself), founded from the last to
	 * the first so that the first market sees the others (transport requests). The player stands at the first with its
	 * trade screen open.
	 */
	static void markets(ServerCtx ctx, int[] offsets, MarketsBody body) {
		List<BlockPos> stalls = new ArrayList<>();
		for (int off : offsets) {
			BlockPos p = ctx.center().east(off);
			if (off != 0) ctx.floor(p);
			stalls.add(p);
		}
		// Far away the forced chunks load a little later the first time: villagers spawned before their entities are
		// loaded are lost (or show up in a later case). Wait for them, then build the villages.
		ctx.later(1, c -> whenLoaded(c, stalls, 6000, () -> {
			stalls.forEach(p -> c.village(p, 3, 3));
			c.later(20, cc -> found(cc, offsets, stalls, body));
		}));
	}

	/** Runs {@code then} once the entities around every position are loaded (checking every tick, at most {@code ticks}). */
	private static void whenLoaded(ServerCtx c, List<BlockPos> positions, int ticks, Runnable then) {
		// The village reaches into the neighbouring chunks: check the corners of each floor too.
		int r = ServerCtx.FLOOR;
		boolean loaded = positions.stream().allMatch(p -> java.util.stream.Stream.of(p, p.offset(-r, 0, -r), p.offset(r, 0, -r), p.offset(-r, 0, r), p.offset(r, 0, r))
				.allMatch(q -> c.level().isPositionEntityTicking(q)));
		if (loaded) {
			then.run();
			return;
		}
		that(ticks > 0, "the chunks of the test markets did not load in time");
		c.later(1, cc -> whenLoaded(cc, positions, ticks - 1, then));
	}

	private static void found(ServerCtx c, int[] offsets, List<BlockPos> stalls, MarketsBody body) throws Exception {
		List<Market> markets = new ArrayList<>();
		for (int i = stalls.size() - 1; i >= 0; i--) {
			ServerPlayer founder = c.player(stalls.get(i).south(2));
			c.placeStall(founder, stalls.get(i));
			Market m = c.marketAt(stalls.get(i));
			that(m != null, "the market at +%d was not founded: %s (villagers there %d)", offsets[i], c.sent(founder).stream()
					.filter(x -> x instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket).map(x -> ((net.minecraft.network.protocol.game.ClientboundSystemChatPacket) x).content().getString()).toList(),
					c.level().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, new AABB(stalls.get(i)).inflate(16)).size());
			markets.addFirst(m);
		}
		ServerPlayer player = c.player(stalls.get(0).south(2));
		STALLS.put(c, stalls);
		c.openStall(player, stalls.get(0));
		body.run(c, player, markets);
	}

	/** Moves the player to the {@code index}-th market of {@link #markets} and opens its stall. */
	private static void openAt(ServerCtx c, ServerPlayer player, List<Market> markets, int index) {
		BlockPos stall = STALLS.get(c).get(index);
		player.closeContainer();
		player.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		c.openStall(player, stall);
	}

	/** A good every given market deals in (unit 1, plain item) that {@code first} can sell {@code buyQty} of and take {@code sellQty} of. */
	private static Identifier shared(ServerCtx c, Market first, int buyQty, int sellQty, Market... others) {
		long now = MarketUpkeep.touch(c.level(), first);
		for (Market o : others) MarketUpkeep.touch(c.level(), o);
		Optional<Identifier> best = first.regularGoods().stream().filter(id -> {
			GoodsDefinition def = GoodsCatalog.get(id);
			if (def == null || !def.buy() || !def.sell() || def.unit() != 1 || def.components().isPresent()) return false;
			for (Market o : others) if (o.good(id) == null || !o.regularGoods().contains(id)) return false;
			GoodState s = first.good(id);
			return s.stock() >= buyQty + 1 && PriceModel.sellCapacity(Reputation.params(first, java.util.UUID.randomUUID(), id, now), s) >= sellQty;
		}).max(Comparator.comparingDouble(id -> GoodsCatalog.get(id).basePrice()));
		if (best.isPresent()) return best.get();
		// Markets of different kinds may share no good: give the others one of first's goods (a test set-up only).
		Identifier id = first.regularGoods().stream().filter(g -> {
			GoodsDefinition def = GoodsCatalog.get(g);
			GoodState s = first.good(g);
			return def != null && def.buy() && def.sell() && def.unit() == 1 && def.components().isEmpty() && s.stock() >= buyQty + 1
					&& PriceModel.sellCapacity(first.params(g), s) >= sellQty;
		}).findFirst().orElseThrow(() -> new AutotestCheck.Failed("market #" + first.id() + " has no good to test with"));
		for (Market o : others) {
			if (o.good(id) == null) o.addGood(id, new GoodState(1.0, 0, GoodsCatalog.get(id).targetStock(), now));
		}
		return id;
	}

	/** Market clock = the overworld day clock, so that {@link #addDays} can move it (set before the markets exist). */
	private static void dayClock(ServerCtx ctx) {
		ctx.config(cfg -> cfg.recovery.clock = TradeExpansionConfig.RecoveryClock.DAY_TIME);
	}

	private static void addDays(ServerCtx c, ServerPlayer player, double days) {
		long before = c.now();
		c.command(player, "time add " + Math.round(days * DAY));
		that(c.now() - before >= Math.round(days * DAY) - 1, "\"time add\" moved the market clock by %d ticks, not %d", c.now() - before,
				Math.round(days * DAY));
	}

	/** What a real client reports once its world is shown (ServerboundPlayerLoadedPacket); until then a player cannot be hurt. */
	private static void clientLoaded(ServerPlayer player) throws ReflectiveOperationException {
		var method = player.connection.getClass().getDeclaredMethod("markClientLoaded");
		method.setAccessible(true);
		method.invoke(player.connection);
	}

	static long walletBalance(ServerPlayer player) {
		return WalletService.balance(player);
	}
}
