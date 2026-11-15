package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.board.BoardService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.ReputationMath;
import com.akf.tradeexpansion.core.TrendMath;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.data.RivalGuilds;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.network.BoardRequestC2S;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.addDays;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.dayClock;

/**
 * Server cases of reputation (16), rival guilds (23) and village trends (24). Campaigns of the cases are started by
 * hand ({@link #campaign}) with {@code rivals.chancePerDay = 0}, so that no random campaign moves the same prices.
 */
final class ServerReputationCases {
	private ServerReputationCases() {
	}

	private static final long DAY = PriceModel.TICKS_PER_DAY;
	private static final Identifier EMERALD_COIN = Identifier.parse("ak_trade_expansion:emerald_coin");

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		chapter16(cases);
		chapter23(cases);
		chapter24(cases);
		return cases;
	}

	// ---- 16. reputation

	private static void chapter16(List<ServerCase> cases) {
		cases.add(full("reputation_decays", List.of("16-1b"), ctx -> {
			dayClock(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.command(player, "aktradeex reputation set 400");
				addDays(c, player, 30);
				c.forget(player);
				c.commandShown(player, "aktradeex reputation");
				that(c.gotMessage(player, "command.ak_trade_expansion.reputation"), "\"reputation\" said %s", c.messages(player));
				double points = points(c, market, player);
				that(Math.abs(points - 200) < 1, "points 30 days after 400: %.1f (half-life %s days)", points, TradeExpansionConfig.get().reputation.halfLifeDays);
			});
		}));
		cases.add(full("reputation_from_trades", List.of("16-2"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			TradeExpansionConfig.Reputation rc = TradeExpansionConfig.get().reputation;
			Identifier good = c.tradableGood(market, 4, 0);
			double before = points(c, market, player);
			long total = c.trade(player, market, good, true, 4);
			that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying 4 gave %s", c.lastTradeResult(player));
			double gained = points(c, market, player) - before;
			that(Math.abs(gained - total * rc.pointsPerCoin) < 1e-6, "buying for %dc gave %.4f points, expected %.4f", total, gained, total * rc.pointsPerCoin);
			// Selling a good the market is short of counts double.
			MarketUpkeep.touch(c.level(), market);
			Identifier shortGood = market.shortages().keySet().stream().filter(g -> sellable(c, player, market, g)).findFirst()
					.orElseThrow(() -> new AutotestCheck.Failed("no shortage good can be sold: " + market.shortages().keySet()));
			ServerCases.giveItems(player, c.item(shortGood), 2);
			before = points(c, market, player);
			long sold = c.trade(player, market, shortGood, false, 2);
			that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling the shortage good gave %s", c.lastTradeResult(player));
			gained = points(c, market, player) - before;
			double expected = sold * rc.pointsPerCoin * rc.shortageMultiplier;
			that(Math.abs(gained - expected) < 1e-6, "selling a shortage good for %dc gave %.4f points, expected %.4f", sold, gained, expected);
		})));
		cases.add(partial("hard_request_needs_reputation", List.of("16-4"), "評判が足りないと★3の依頼が「納品不可」で送られ、納品が断られ、レベル3で納品できること（灰色の★と右の欄の文は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					MarketUpkeep.touch(c.level(), market); // the requests are made when the market is first touched
					Request hard = makeHard(market);
					c.forget(player);
					c.openStall(player, stall);
					that(requestView(c, player, hard.id()).locked(), "the 3-star request is not locked at level 0");
					ServerCases.giveItemsOutsideHotbar(player, c.item(hard.goods()), hard.quantity()); // the hotbar stays free to open the stall
					c.forget(player);
					QuestService.deliver(player, new QuestDeliverC2S(market.id(), hard.id(), false));
					that(questResult(c, player) == TradeResult.REPUTATION_TOO_LOW, "delivering at level 0 gave %s", questResult(c, player));
					equal(hard.quantity(), c.count(player, c.item(hard.goods())), "items after the refused delivery");
					c.command(player, "aktradeex reputation set " + threshold(TradeExpansionConfig.get().reputation.hardQuestLevel));
					c.forget(player);
					c.openStall(player, stall);
					that(!requestView(c, player, hard.id()).locked(), "the 3-star request is still locked at level %d", TradeExpansionConfig.get().reputation.hardQuestLevel);
					c.forget(player);
					QuestService.deliver(player, new QuestDeliverC2S(market.id(), hard.id(), false));
					that(questResult(c, player) == TradeResult.SUCCESS, "delivering at level 3 gave %s", questResult(c, player));
				})));
		cases.add(partial("accepted_request_more_points", List.of("16-5"), "依頼を受けると受付済みとして送られ、受けた依頼の納品で受けていない依頼の倍率分だけ多く評判が増えること（一覧の ✔ は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.command(player, "aktradeex reputation set 2000"); // any request can be delivered
					List<Request> plain = market.requests().stream().filter(r -> !r.isTransport()).limit(2).toList();
					that(plain.size() == 2, "the market has fewer than 2 requests");
					Request accepted = plain.get(0), other = plain.get(1);
					QuestService.accept(player, new QuestAcceptC2S(market.id(), accepted.id(), false));
					that(market.requests().stream().anyMatch(r -> r.id() == accepted.id() && r.accepted().contains(player.getUUID())), "the request was not accepted");
					c.forget(player);
					c.openStall(player, stall);
					that(requestView(c, player, accepted.id()).accepted(), "the snapshot does not show the request as accepted");
					that(!requestView(c, player, other.id()).accepted(), "the other request shows as accepted");
					TradeExpansionConfig.Reputation rc = TradeExpansionConfig.get().reputation;
					double gainedAccepted = deliverPoints(c, player, market, accepted);
					double gainedOther = deliverPoints(c, player, market, other);
					double expectA = rc.questPoints.get(accepted.difficulty() - 1) * rc.acceptedQuestMultiplier;
					double expectO = rc.questPoints.get(other.difficulty() - 1);
					that(Math.abs(gainedAccepted - expectA) < 1e-6, "an accepted %d-star request gave %.2f points, expected %.2f", accepted.difficulty(), gainedAccepted, expectA);
					that(Math.abs(gainedOther - expectO) < 1e-6, "a %d-star request not accepted gave %.2f points, expected %.2f", other.difficulty(), gainedOther, expectO);
				})));
		cases.add(full("expired_request_costs_points", List.of("16-6"), ctx -> {
			dayClock(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				c.command(player, "aktradeex reputation set 400");
				Request r = market.requests().stream().filter(q -> !q.isTransport()).findFirst()
						.orElseThrow(() -> new AutotestCheck.Failed("the market has no request"));
				QuestService.accept(player, new QuestAcceptC2S(market.id(), r.id(), false));
				long start = c.now();
				double days = (r.deadline() - start) / (double) DAY + 0.05;
				addDays(c, player, days);
				player.closeContainer();
				MarketUpkeep.touch(c.level(), market);
				that(market.requests().stream().noneMatch(q -> q.id() == r.id()), "the request did not expire");
				double elapsed = (c.now() - start) / (double) DAY;
				double expected = ReputationMath.decay(400, elapsed, TradeExpansionConfig.get().reputation.halfLifeDays)
						- TradeExpansionConfig.get().reputation.questFailPenalty * r.difficulty();
				double points = points(c, market, player);
				that(Math.abs(points - expected) < 0.5, "points after the accepted %d-star request expired: %.2f, expected %.2f", r.difficulty(), points, expected);
			});
		}));
		cases.add(partial("reward_grows_with_reputation", List.of("16-7"), "交易画面に送る依頼の報酬が、レベル0とレベル3で報酬の倍率どおりに違うこと（画面の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.forget(player);
					c.openStall(player, stall);
					Request r = market.requests().stream().filter(q -> !q.isTransport()).findFirst()
							.orElseThrow(() -> new AutotestCheck.Failed("the market has no request"));
					long low = requestView(c, player, r.id()).request().reward();
					equal(r.reward(), low, "reward at level 0");
					c.command(player, "aktradeex reputation set " + threshold(3));
					c.forget(player);
					c.openStall(player, stall);
					long high = requestView(c, player, r.id()).request().reward();
					long expected = Math.round(r.reward() * (1 + 3 * TradeExpansionConfig.get().reputation.rewardBonusPerLevel));
					equal(expected, high, "reward at level 3");
					that(high > low, "the reward did not grow (%d -> %d)", low, high);
				})));
		cases.add(full("villagers_and_reputation", List.of("16-8"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			TradeExpansionConfig.Reputation rc = TradeExpansionConfig.get().reputation;
			c.command(player, "aktradeex reputation set 400");
			Villager v = ServerWorldCases.farmer(c, stall.east(4));
			c.emptyInventory(player);
			player.getInventory().add(new ItemStack(Items.WHEAT, 20));
			MerchantMenu menu = ServerWorldCases.openMerchant(c, player, v);
			double p0 = points(c, market, player);
			that(ServerWorldCases.select(menu, ServerWorldCases.WHEAT).is(Items.EMERALD), "selling wheat shows no emerald");
			menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
			equal(1, c.count(player, Items.EMERALD), "emeralds after trading with the villager");
			player.closeContainer();
			double p1 = points(c, market, player);
			that(Math.abs(p1 - p0 - rc.villagerTradePoints) < 1e-6, "a villager trade gave %.2f points", p1 - p0);
			v.hurtServer(c.level(), c.level().damageSources().playerAttack(player), 1.0f);
			double p2 = points(c, market, player);
			that(Math.abs(p1 - p2 - rc.attackPenalty) < 1e-6, "hitting the villager took %.2f points", p1 - p2);
			v.hurtServer(c.level(), c.level().damageSources().playerAttack(player), 1000.0f);
			that(v.isDeadOrDying(), "the villager did not die");
			double p3 = points(c, market, player);
			// The killing blow itself is not counted as a hit (Fabric's AFTER_DAMAGE skips deaths); allow it either way.
			double lost = p2 - p3;
			that(Math.abs(lost - rc.killPenalty) < 1e-6 || Math.abs(lost - rc.killPenalty - rc.attackPenalty) < 1e-6,
					"killing the villager took %.2f points, expected %.0f", lost, rc.killPenalty);
		})));
		cases.add(partial("reputation_per_player", List.of("16-9", "16-10"),
				"評判レベル4の人とレベル0の人に、それぞれの評判で交易画面のデータ（追加の掘り出し物・値段・評判）が送られること（本物の2人のクライアントでの表示は見ていない）",
				// Dearer prices, as in 16-3: in a market of cheap goods the bonus may move no price by 1c.
				ctx -> ServerCases.withMarket(ServerCases.withConfig(ctx, cfg -> cfg.price.globalMultiplier = 20), (c, player, market, stall) -> {
					ServerPlayer other = c.player(stall.south(3));
					int bonusLevel = TradeExpansionConfig.get().reputation.specialBonusLevel;
					c.command(player, "aktradeex reputation set " + threshold(bonusLevel));
					MarketUpkeep.touch(c.level(), market);
					that(!market.bonusSpecials().isEmpty(), "the market has no bonus special (specials %s)", market.specials());
					c.forget(player);
					c.forget(other);
					c.openStall(player, stall);
					c.openStall(other, stall);
					MarketSnapshotS2C high = c.lastSnapshot(player), low = c.lastSnapshot(other);
					equal(bonusLevel, high.reputation().level(), "reputation level sent to the first player");
					equal(0, low.reputation().level(), "reputation level sent to the second player");
					for (Identifier bonus : market.bonusSpecials()) {
						that(high.goods().stream().anyMatch(v -> v.goods().equals(bonus)), "level %d does not see the bonus special %s", bonusLevel, bonus);
						that(low.goods().stream().noneMatch(v -> v.goods().equals(bonus)), "level 0 sees the bonus special %s", bonus);
					}
					equal(high.goods().size() - market.bonusSpecials().size(), low.goods().size(), "goods seen at level 0");
					Identifier good = ServerCases.bonusGood(c, market, Reputation.priceBonus(bonusLevel));
					var h = ServerCases.view(high, good);
					var l = ServerCases.view(low, good);
					that(PriceModel.buyPrice(h.params(), h.pressure()) < PriceModel.buyPrice(l.params(), l.pressure()), "the buy price of %s is not lower for level %d", good, bonusLevel);
					that(PriceModel.sellPrice(h.params(), h.pressure()) > PriceModel.sellPrice(l.params(), l.pressure()), "the sell price of %s is not higher for level %d", good, bonusLevel);
				})));
		cases.add(full("change_owner_only", List.of("16-12"), ctx -> {
			ctx.config(cfg -> cfg.currency.changeOwnerOnly = true);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				ServerPlayer other = c.player(stall.south(2));
				c.emptyInventory(other);
				c.openStall(player, stall);
				Identifier good = ServerCases.cheapGood(c, player, market, 500);
				long total = c.previewTotal(player, market, good, true, 1);
				CoinType coin = null;
				for (CoinType t : CoinType.values()) {
					if (t.value() > total) {
						coin = t;
						break;
					}
				}
				that(coin != null, "no coin is worth more than %dc", total);
				// A full inventory: the one coin pays, the good takes its slot and the change has no room.
				c.emptyInventory(player);
				player.getInventory().setItem(0, new ItemStack(ModItems.coin(coin)));
				for (int slot = 1; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
				var result = c.tradeRaw(player, market.id(), good, true, 1, total, false, false);
				that(result == TradeResult.SUCCESS, "buying with a full inventory gave %s", result);
				List<ItemEntity> change = c.level().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4),
						e -> e.getItem().getItem() instanceof CoinItem);
				that(!change.isEmpty(), "no change was dropped");
				for (ItemEntity e : change) {
					e.setNoPickUpDelay();
					e.playerTouch(other);
				}
				equal(0, c.balance(other), "money the other player picked up");
				player.getInventory().setItem(35, ItemStack.EMPTY);
				player.getInventory().setItem(34, ItemStack.EMPTY);
				player.getInventory().setItem(33, ItemStack.EMPTY);
				for (ItemEntity e : change) if (e.isAlive()) e.playerTouch(player);
				equal(coin.value() - total, c.balance(player), "change the buyer picked up");
			});
		}));
		cases.add(partial("bank_interest_over_days", List.of("16-13"), "10日で 10,000c が約 10,030c になり、market advance では利息が付かないこと（銀行タブの「利息」の表示は見ていない）", ctx -> {
			dayClock(ctx);
			ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				c.emptyInventory(player);
				player.getInventory().setItem(0, new ItemStack(ModItems.WALLET));
				c.giveMoney(player, 10_000);
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 10_000));
				equal(10_000, bank(player), "bank after depositing 10,000c");
				long start = c.now();
				c.command(player, "aktradeex market advance 10");
				equal(10_000, bank(player), "bank after \"market advance 10\"");
				addDays(c, player, 10);
				double days = (c.now() - start) / (double) DAY;
				long expected = Math.round(10_000 * (Math.pow(1 + TradeExpansionConfig.get().bank.interestRatePerDay, days) - 1));
				long interest = bank(player) - 10_000;
				that(Math.abs(interest - expected) <= 1, "interest after %.2f days: %dc, expected about %dc", days, interest, expected);
				that(interest >= 29 && interest <= 31, "interest after 10 days: %dc (about 30c expected)", interest);
			});
		}));
	}

	private static double points(ServerCtx c, Market market, ServerPlayer player) {
		return Reputation.points(market, player.getUUID(), c.now());
	}

	/** Points of the first reputation level at or above {@code level}. */
	private static int threshold(int level) {
		int[] t = TradeExpansionConfig.get().reputation.levelThresholdArray();
		return level <= 0 ? 0 : t[Math.min(level, t.length) - 1];
	}

	private static long bank(ServerPlayer player) {
		return BankService.accounts(player).balance(player.getUUID());
	}

	private static boolean sellable(ServerCtx c, ServerPlayer player, Market market, Identifier g) {
		GoodsDefinition def = GoodsCatalog.get(g);
		GoodState s = market.good(g);
		if (def == null || s == null || !def.sell() || def.unit() != 1 || def.components().isPresent() || c.item(g) == Items.AIR) return false;
		return PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), g, c.now()), s) >= 2;
	}

	/** Makes the market's first plain request a 3-star one (a test set-up: the stars are rolled). */
	private static Request makeHard(Market market) {
		List<Request> list = market.requests();
		for (int i = 0; i < list.size(); i++) {
			Request r = list.get(i);
			if (r.isTransport()) continue;
			Request hard = new Request(r.id(), r.goods(), r.quantity(), r.reward(), r.deadline(), 3, r.accepted(), r.transport());
			list.set(i, hard);
			return hard;
		}
		throw new AutotestCheck.Failed("the market has no request");
	}

	private static MarketSnapshotS2C.RequestView requestView(ServerCtx c, ServerPlayer player, int id) {
		MarketSnapshotS2C snap = c.lastSnapshot(player);
		that(snap != null, "no market snapshot was sent");
		return snap.requests().stream().filter(v -> v.request().id() == id).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("the snapshot has no request #" + id));
	}

	private static TradeResult questResult(ServerCtx c, ServerPlayer player) {
		List<QuestResultS2C> list = c.payloads(player, QuestResultS2C.class);
		return list.isEmpty() ? null : list.getLast().result();
	}

	/** Delivers {@code r} with the items given for it; returns the reputation points it gave. */
	private static double deliverPoints(ServerCtx c, ServerPlayer player, Market market, Request r) {
		ServerCases.giveItemsOutsideHotbar(player, c.item(r.goods()), r.quantity());
		double before = points(c, market, player);
		c.forget(player);
		QuestService.deliver(player, new QuestDeliverC2S(market.id(), r.id(), false));
		that(questResult(c, player) == TradeResult.SUCCESS, "delivering request #%d gave %s", r.id(), questResult(c, player));
		return points(c, market, player) - before;
	}

	// ---- 23. rival guilds

	private static void chapter23(List<ServerCase> cases) {
		cases.add(partial("rivals_start_command", List.of("23-1"), "rivals start で中心の市場に動きが1件始まり、始まった知らせと rivals の一覧の行がチャットに出ること（文の中身は見ていない）", ctx -> {
			noRandomRivals(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.forget(player);
				c.commandShown(player, "aktradeex rivals start");
				that(c.gotMessage(player, "command.ak_trade_expansion.rivals.started"), "\"rivals start\" said %s", c.messages(player));
				equal(1, c.registry().campaigns().size(), "campaigns after \"rivals start\"");
				Campaign camp = c.registry().campaigns().getFirst();
				equal(market.id(), camp.market(), "center market of the campaign");
				var cfg = TradeExpansionConfig.get().rivals;
				that(camp.impact() >= cfg.minImpact && camp.impact() <= cfg.maxImpact, "impact %.2f", camp.impact());
				that(camp.days() >= cfg.minDays && camp.days() <= cfg.maxDays, "%d days", camp.days());
				c.forget(player);
				c.commandShown(player, "aktradeex rivals");
				List<String> said = c.messages(player);
				that(said.contains("command.ak_trade_expansion.rivals.header"), "\"rivals\" said %s", said);
				that(said.contains(camp.buying() ? "command.ak_trade_expansion.rivals.line.buying" : "command.ak_trade_expansion.rivals.line.selling"),
						"\"rivals\" does not list the campaign: %s", said);
			});
		}));
		cases.add(partial("rivals_move_prices", List.of("23-2"), "買い集めで値段が上がり、売りさばきで下がり、交易画面に送る商品に商会の動き（向き・あと何日）が付くこと（ツールチップとグラフは見ていない）", ctx -> {
			noRandomRivals(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				List<Identifier> goods = RivalService.eligibleGoods(market);
				that(goods.size() >= 2, "the market has %d goods a guild can move", goods.size());
				Identifier up = goods.get(0), down = goods.get(1);
				long buy0 = c.buyPrice(player, market, up);
				long sell0 = c.sellPrice(player, market, down);
				double xUp = market.good(up).pressure(), xDown = market.good(down).pressure();
				campaign(c, market, up, true, 0.3, 3);
				campaign(c, market, down, false, 0.3, 3);
				that(market.good(up).pressure() < xUp, "buying up did not lower X (%.3f -> %.3f)", xUp, market.good(up).pressure());
				that(market.good(down).pressure() > xDown, "selling off did not raise X (%.3f -> %.3f)", xDown, market.good(down).pressure());
				that(c.buyPrice(player, market, up) >= buy0, "the buy price fell while bought up (%d -> %d)", buy0, c.buyPrice(player, market, up));
				that(c.sellPrice(player, market, down) <= sell0, "the sell price rose while sold off (%d -> %d)", sell0, c.sellPrice(player, market, down));
				c.forget(player);
				c.openStall(player, stall);
				MarketSnapshotS2C snap = c.lastSnapshot(player);
				var vu = ServerCases.view(snap, up).rival();
				var vd = ServerCases.view(snap, down).rival();
				that(vu.isPresent() && vu.get().buying() && vu.get().daysLeft() == 2, "the bought-up good shows %s", vu);
				that(vd.isPresent() && !vd.get().buying() && vd.get().daysLeft() == 2, "the sold-off good shows %s", vd);
			});
		}));
		cases.add(full("rivals_reach", List.of("23-5"), ctx -> {
			noRandomRivals(ctx);
			ctx.config(cfg -> {
				cfg.distance.minNewMarketDistance = 100; // a second market within rivals.nearRadius (128)
				cfg.market.newMarketEnoughBeds = 3; // a villager left by an earlier case may wander in
			});
			ServerChapterCases.markets(ctx, new int[] {0, 120, 800}, (c, player, m) -> {
				Market center = m.get(0), near = m.get(1), far = m.get(2);
				Identifier good = ServerChapterCases.shared(c, center, 0, 0, near, far);
				for (Market x : m) MarketUpkeep.touch(c.level(), x);
				double x0 = center.good(good).pressure(), x1 = near.good(good).pressure(), x2 = far.good(good).pressure();
				campaign(c, center, good, true, 0.3, 3);
				double d0 = x0 - center.good(good).pressure(), d1 = x1 - near.good(good).pressure(), d2 = x2 - far.good(good).pressure();
				double link = GoodsCatalog.get(good).link();
				that(d0 > 0, "the center market did not move (%.4f)", d0);
				that(Math.abs(d1 - d0 * link) < 1e-6 + d0 * 0.01, "120 blocks away X moved %.4f, the center %.4f (link %.2f)", d1, d0, link);
				that(Math.abs(d2) < 1e-9, "800 blocks away X moved %.4f", d2);
			});
		}));
		cases.add(full("rivals_grow_then_fade", List.of("23-6"), ctx -> {
			noRandomRivals(ctx);
			dayClock(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				Identifier good = RivalService.eligibleGoods(market).getFirst();
				double x0 = market.good(good).pressure();
				campaign(c, market, good, true, 0.3, 3);
				List<Double> shift = new ArrayList<>();
				shift.add(x0 - market.good(good).pressure());
				for (int day = 1; day <= 5; day++) {
					addDays(c, player, 1);
					MarketUpkeep.touch(c.level(), market);
					shift.add(x0 - market.good(good).pressure());
				}
				// Days 0..2 run the campaign; it ends before day 3.
				that(shift.get(1) > shift.get(0) && shift.get(2) > shift.get(1), "the shift did not grow while running: %s", shift);
				that(shift.get(4) < shift.get(3) && shift.get(5) < shift.get(4), "the shift did not fade after the end: %s", shift);
				that(shift.get(5) > 0, "the shift was gone at once: %s", shift);
			});
		}));
		cases.add(full("rivals_start_by_themselves", List.of("23-7"), ctx -> {
			dayClock(ctx);
			ctx.config(cfg -> cfg.rivals.chancePerDay = 1.0);
			ctx.registry().setRivalDay(Long.MIN_VALUE); // roll from today on (the day may be counted on another clock)
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				int max = TradeExpansionConfig.get().rivals.maxActive;
				int most = 0;
				Set<Integer> seen = new HashSet<>();
				for (int day = 0; day < 8; day++) {
					if (day > 0) addDays(c, player, 1);
					MarketUpkeep.touch(c.level(), market);
					List<Campaign> active = RivalService.active(c.registry(), RivalService.day(c.now()));
					that(active.size() <= max, "%d campaigns at once (at most %d)", active.size(), max);
					most = Math.max(most, active.size());
					active.forEach(a -> seen.add(a.id()));
				}
				equal(max, most, "most campaigns at once");
				that(seen.size() > max, "only %d campaigns started in 8 days", seen.size());
			});
		}));
		cases.add(partial("rivals_and_trends_saved", List.of("23-8", "24-8"), "保存データ（markets）を書き出して読み直すと、商会の動き・値段のずれ・流行が同じであること（実際にワールドを開き直してはいない）", ctx -> {
			noRandomRivals(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				Identifier good = RivalService.eligibleGoods(market).getFirst();
				campaign(c, market, good, true, 0.3, 3);
				that(!market.trends().isEmpty(), "the market has no trend");
				RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, c.level().registryAccess());
				Tag saved = MarketRegistry.CODEC.encodeStart(ops, c.registry()).getOrThrow();
				MarketRegistry loaded = MarketRegistry.CODEC.parse(ops, saved).getOrThrow();
				that(loaded.campaigns().equals(c.registry().campaigns()), "campaigns after loading: %s / %s", loaded.campaigns(), c.registry().campaigns());
				Market back = loaded.get(market.id());
				that(back != null, "the market was not loaded");
				that(back.good(good).pressure() == market.good(good).pressure(), "X after loading: %s / %s", back.good(good).pressure(), market.good(good).pressure());
				equal(market.rivalDay(), back.rivalDay(), "rival day after loading");
				that(back.trends().equals(market.trends()), "trends after loading: %s / %s", back.trends(), market.trends());
				equal(market.trendPeriod(), back.trendPeriod(), "trend period after loading");
				double b1 = market.params(good).basePrice(), b2 = back.params(good).basePrice();
				that(Math.abs(b1 - b2) < 1e-9, "base price after loading: %s / %s", b2, b1);
			});
		}));
		cases.add(full("rivals_off", List.of("23-9"), ctx -> {
			noRandomRivals(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				Identifier good = RivalService.eligibleGoods(market).getFirst();
				campaign(c, market, good, true, 0.3, 3);
				that(!board(c, player, market).news().isEmpty(), "the board shows no news while the guild moves");
				c.config(cfg -> {
					cfg.rivals.enabled = false;
					cfg.rivals.chancePerDay = 1.0;
				});
				that(board(c, player, market).news().isEmpty(), "the board still shows news with rivals off");
				c.forget(player);
				c.openStall(player, stall);
				that(ServerCases.view(c.lastSnapshot(player), good).rival().isEmpty(), "the good still shows the guild with rivals off");
				int before = c.registry().campaigns().size();
				c.registry().setRivalDay(Long.MIN_VALUE);
				MarketUpkeep.touch(c.level(), market);
				c.command(player, "aktradeex clock advance 3");
				MarketUpkeep.touch(c.level(), market);
				equal(before, c.registry().campaigns().size(), "campaigns after 3 days with rivals off");
			});
		}));
		cases.add(partial("price_changed_across_day", List.of("23-10"), "開いた時の合計額で日をまたいでから買うと PRICE_CHANGED が返り、品もお金も動かないこと（メッセージの文は見ていない）", ctx -> {
			noRandomRivals(ctx);
			dayClock(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.giveMoney(player, 100_000);
				Identifier good = RivalService.eligibleGoods(market).stream().filter(g -> market.good(g).stock() >= 3).findFirst()
						.orElseThrow(() -> new AutotestCheck.Failed("no good to buy"));
				campaign(c, market, good, true, 0.35, 3);
				c.forget(player);
				c.openStall(player, stall);
				MarketSnapshotS2C snap = c.lastSnapshot(player);
				var v = ServerCases.view(snap, good);
				long shown = PriceModel.sumBuy(v.params(), v.pressure(), 1, v.unit(), snap.bulkDiscount());
				addDays(c, player, 1);
				long now = c.previewTotal(player, market, good, true, 1);
				that(now != shown, "the price did not change over the day (%dc)", shown);
				long money = c.balance(player);
				int items = c.count(player, c.item(good));
				var result = c.tradeRaw(player, market.id(), good, true, 1, shown, false, false);
				that(result == TradeResult.PRICE_CHANGED, "buying for yesterday's %dc (now %dc) gave %s", shown, now, result);
				equal(money, c.balance(player), "money after the refused trade");
				equal(items, c.count(player, c.item(good)), "items after the refused trade");
			});
		}));
		cases.add(partial("shortage_multiplier_sent", List.of("23-11"), "品不足の商品に、市場の品不足の倍率と、その倍率を掛けた基準価格が送られること（ツールチップの文は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.forget(player);
					c.openStall(player, stall);
					that(!market.shortages().isEmpty(), "the market has no shortage");
					MarketSnapshotS2C snap = c.lastSnapshot(player);
					for (Map.Entry<Identifier, Double> e : Map.copyOf(market.shortages()).entrySet()) {
						var v = ServerCases.view(snap, e.getKey());
						that(v.shortage(), "%s is not sent as short", e.getKey());
						that(Math.abs(v.shortageMultiplier() - e.getValue()) < 1e-9, "%s: multiplier sent %.3f, market %.3f", e.getKey(), v.shortageMultiplier(), e.getValue());
						double with = market.params(e.getKey()).basePrice();
						market.shortages().remove(e.getKey());
						double without = market.params(e.getKey()).basePrice();
						market.shortages().put(e.getKey(), e.getValue());
						that(Math.abs(with / without - e.getValue()) < 1e-6, "%s: base price %.2f / %.2f is not x%.3f", e.getKey(), with, without, e.getValue());
					}
				})));
	}

	/** No campaign starts by chance during the case. */
	private static void noRandomRivals(ServerCtx ctx) {
		ctx.config(cfg -> cfg.rivals.chancePerDay = 0);
	}

	/**
	 * Starts a campaign on {@code good} centered on {@code center} today, as {@code rivals start} does (today's push
	 * goes to every market at once). A guild that deals in the good is used when there is one.
	 */
	private static Campaign campaign(ServerCtx c, Market center, Identifier good, boolean buying, double impact, int days) {
		MarketRegistry registry = c.registry();
		long now = MarketUpkeep.touch(c.level(), center);
		long today = RivalService.day(now);
		Identifier guild = RivalGuilds.all().entrySet().stream().filter(e -> e.getValue().dealsIn(good)).map(Map.Entry::getKey).findFirst()
				.orElseGet(() -> RivalGuilds.all().keySet().iterator().next());
		Campaign camp = new Campaign(registry.takeCampaignId(), guild, good, center.id(), buying, today, today + days, impact);
		for (Market m : registry.all()) MarketUpkeep.touch(c.level(), m); // today's pushes of the others first
		registry.campaigns().add(camp);
		for (Market m : registry.all()) RivalService.push(registry, m, camp, today, today, now);
		registry.setDirty();
		return camp;
	}

	private static BoardS2C board(ServerCtx c, ServerPlayer player, Market market) {
		c.forget(player);
		BoardService.handle(player, new BoardRequestC2S(market.id()));
		List<BoardS2C> list = c.payloads(player, BoardS2C.class);
		that(!list.isEmpty(), "no board was sent");
		return list.getLast();
	}

	// ---- 24. village trends

	private static void chapter24(List<ServerCase> cases) {
		cases.add(partial("trend_raises_base_price", List.of("24-2"), "人気の分野の商品に流行と倍率が送られ、基準価格がその倍率だけ上がっていること（ツールチップと market info の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.forget(player);
					c.openStall(player, stall);
					Map.Entry<Identifier, Double> popular = market.trends().entrySet().stream().filter(e -> e.getValue() > 1).findFirst()
							.orElseThrow(() -> new AutotestCheck.Failed("the market has no trend in fashion: " + market.trends()));
					Identifier good = market.regularGoods().stream().filter(g -> GoodsGroups.groupsOf(g).contains(popular.getKey())).findFirst()
							.orElseThrow(() -> new AutotestCheck.Failed("no good of the group " + popular.getKey()));
					var v = ServerCases.view(c.lastSnapshot(player), good);
					that(v.trends().stream().anyMatch(t -> t.group().equals(popular.getKey()) && Math.abs(t.multiplier() - popular.getValue()) < 1e-9),
							"%s is sent with trends %s, not %s x%.3f", good, v.trends(), popular.getKey(), popular.getValue());
					double sent = v.trends().stream().mapToDouble(MarketSnapshotS2C.TrendView::multiplier).reduce(1, (a, b) -> a * b);
					double with = market.params(good).basePrice();
					Map<Identifier, Double> saved = new LinkedHashMap<>(market.trends());
					market.trends().clear();
					double without = market.params(good).basePrice();
					long buyWithout = PriceModel.buyPrice(market.params(good), market.good(good).pressure());
					market.trends().putAll(saved);
					that(Math.abs(with / without - sent) < 1e-6, "base price %.2f / %.2f is not x%.3f", with, without, sent);
					that(c.buyPrice(player, market, good) >= buyWithout, "the buy price is not higher with the trend");
				})));
		cases.add(full("no_trend_on_specials", List.of("24-3"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			MarketUpkeep.touch(c.level(), market);
			List<Identifier> untouched = new ArrayList<>(market.specials());
			if (market.good(EMERALD_COIN) != null) untouched.add(EMERALD_COIN);
			that(!market.specials().isEmpty(), "the market has no special today");
			// Every group of these goods in fashion: they still get no trend.
			for (Identifier g : untouched) for (Identifier group : GoodsGroups.groupsOf(g)) market.trends().put(group, 1.3);
			for (Identifier g : untouched) that(market.trendMultiplier(g) == 1.0, "%s has trend x%.3f", g, market.trendMultiplier(g));
			c.forget(player);
			c.openStall(player, stall);
			MarketSnapshotS2C snap = c.lastSnapshot(player);
			for (Identifier g : untouched) {
				snap.goods().stream().filter(v -> v.goods().equals(g)).findFirst()
						.ifPresent(v -> that(v.trends().isEmpty(), "%s is sent with trends %s", g, v.trends()));
			}
			// The same groups do apply to the regular goods in them.
			market.regularGoods().stream().filter(g -> !g.equals(EMERALD_COIN) && GoodsGroups.groupsOf(g).stream().anyMatch(gr -> market.trends().getOrDefault(gr, 1.0) == 1.3))
					.findFirst().ifPresent(g -> that(market.trendMultiplier(g) > 1.0, "the regular good %s gets no trend either", g));
		})));
		cases.add(full("trends_change_with_period", List.of("24-5"), ctx -> {
			dayClock(ctx);
			ctx.config(cfg -> cfg.market.newMarketEnoughBeds = 3); // a villager left by an earlier case may wander in
			ServerChapterCases.markets(ctx, new int[] {0, 300}, (c, player, m) -> {
				Market a = m.get(0), b = m.get(1);
				double length = TradeExpansionConfig.get().trend.durationDays;
				long offA = TrendMath.offset(MarketFactory.seed(c.level(), a.center()), length);
				long offB = TrendMath.offset(MarketFactory.seed(c.level(), b.center()), length);
				that(offA != offB, "both markets change their trends at the same time (offset %d)", offA);
				MarketUpkeep.touch(c.level(), a);
				Map<Identifier, Double> first = Map.copyOf(a.trends());
				long p0 = a.trendPeriod();
				long next = (p0 + 1) * TrendMath.length(length) - offA; // market clock time of the next period
				double untilNext = (next - c.now()) / (double) DAY;
				if (untilNext > 0.2) {
					addDays(c, player, untilNext - 0.1);
					MarketUpkeep.touch(c.level(), a);
					equal(p0, a.trendPeriod(), "trend period just before its end");
					that(a.trends().equals(first), "the trends changed before the period ended: %s -> %s", first, a.trends());
				}
				addDays(c, player, Math.max(0.1, (next - c.now()) / (double) DAY + 0.05));
				MarketUpkeep.touch(c.level(), a);
				equal(p0 + 1, a.trendPeriod(), "trend period after its end");
				// The new period may by chance roll the same trends: look at a few periods.
				boolean changed = !a.trends().equals(first);
				for (int i = 0; i < 3 && !changed; i++) {
					addDays(c, player, length);
					MarketUpkeep.touch(c.level(), a);
					changed = !a.trends().equals(first);
				}
				that(changed, "the trends never changed over 4 periods: %s", first);
			});
		}));
		cases.add(full("reroll_rerolls_trends", List.of("24-6"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			MarketUpkeep.touch(c.level(), market);
			Identifier fake = Identifier.parse("ak_trade_expansion:autotest_fake_group");
			market.trends().clear();
			market.trends().put(fake, 1.5);
			c.command(player, "aktradeex market reroll");
			that(!market.trends().containsKey(fake), "the old trends stayed after \"market reroll\": %s", market.trends());
			that(!market.trends().isEmpty(), "no trends after \"market reroll\"");
			int min = TradeExpansionConfig.get().trend.minGoods;
			for (Identifier group : market.trends().keySet()) {
				long n = market.regularGoods().stream().filter(g -> GoodsGroups.groupsOf(g).contains(group)).count();
				that(n >= min, "the trend %s has %d goods of the market (at least %d)", group, n, min);
			}
		})));
		cases.add(partial("trends_off", List.of("24-7"), "流行の数を両方 0 にすると次の期間から流行が無くなり、交易画面に送る流行も空になること（設定はファイルの再読み込みでなくその場で変えた。画面の表示は見ていない）", ctx -> {
			dayClock(ctx);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				MarketUpkeep.touch(c.level(), market);
				that(!market.trends().isEmpty(), "the market has no trend to begin with");
				c.config(cfg -> {
					cfg.trend.popularCount = 0;
					cfg.trend.unpopularCount = 0;
				});
				addDays(c, player, TradeExpansionConfig.get().trend.durationDays + 0.1);
				c.forget(player);
				c.openStall(player, stall);
				that(market.trends().isEmpty(), "trends in the next period: %s", market.trends());
				MarketSnapshotS2C snap = c.lastSnapshot(player);
				that(snap.trends().isEmpty(), "trends sent: %s", snap.trends());
				that(snap.goods().stream().allMatch(v -> v.trends().isEmpty()), "a good is sent with a trend");
			});
		}));
	}
}
