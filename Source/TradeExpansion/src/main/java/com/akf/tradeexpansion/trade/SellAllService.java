package com.akf.tradeexpansion.trade;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Propagation;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.SellAllC2S;
import com.akf.tradeexpansion.network.TradeResultS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Sell all" (A-4, v0.32): sells several goods at once from the main inventory (not the hotbar) and the sell slot. Each
 * good is priced exactly like a single sale (sequential prices, damaged items, reputation, shortages). All or nothing:
 * if any line cannot be sold as shown, or the total differs from the one the player saw, nothing is sold.
 */
public final class SellAllService {
	private SellAllService() {
	}

	private record Planned(Identifier goods, GoodsDefinition def, GoodParams params, GoodState state, InventoryUtil.Match match, int units,
			long total) {
	}

	public static void handle(ServerPlayer player, SellAllC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		long now = MarketClock.now(player.level());
		TradeResultS2C result = process(player, registry, market, request, now);
		ServerPlayNetworking.send(player, result);
		BankService.sendAccount(player);
		if (result.result() != TradeResult.SUCCESS && TradeService.viewing(player, market)) { // only the open screen's market
			now = MarketUpkeep.touch(player.level(), market);
			ServerPlayNetworking.send(player, MarketSnapshotS2C.of(market, player, now));
		}
	}

	private static TradeResultS2C process(ServerPlayer player, MarketRegistry registry, Market market, SellAllC2S request, long now) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id() || request.lines().isEmpty()) {
			return fail(TradeResult.INVALID_REQUEST);
		}
		if (!menu.stillValid(player)) return fail(TradeResult.TOO_FAR);
		if (player.isCreative() && !config.trade.allowCreative) return fail(TradeResult.CREATIVE_NOT_ALLOWED);
		boolean card = request.useCard();
		if (card && !BankService.hasValidCard(player)) return fail(TradeResult.CARD_INVALID);
		MarketUpkeep.touch(player.level(), market);
		Container sellSlot = menu.sellSlot();

		// 1. Plan every line exactly like a single sale; any problem cancels the whole sale.
		List<Planned> plan = new ArrayList<>();
		Set<Identifier> seen = new HashSet<>();
		long total = 0;
		int items = 0;
		for (SellAllC2S.Line line : request.lines()) {
			Identifier goodsId = line.goods();
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			GoodState state = market.good(goodsId);
			if (!seen.add(goodsId) || line.units() <= 0 || def == null || state == null || !def.sell()
					|| !Reputation.canSee(market, player.getUUID(), goodsId, now)) {
				return fail(TradeResult.NOT_AVAILABLE);
			}
			GoodParams params = Reputation.params(market, player.getUUID(), goodsId, now);
			InventoryUtil.Match match = GoodsCatalog.match(goodsId);
			int units = line.units();
			if (units > PriceModel.sellCapacity(params, state)) return fail(TradeResult.STOCK_FULL);
			int itemCount = units * def.unit();
			if (InventoryUtil.count(player, match, sellSlot, true) < itemCount) return fail(TradeResult.INSUFFICIENT_ITEMS);
			double[] ratios = def.unit() == 1 ? InventoryUtil.sellRatios(player, match, sellSlot, units, true) : null;
			long lineTotal = ratios != null ? PriceModel.sumSell(params, state.pressure(), ratios) : PriceModel.sumSell(params, state.pressure(), units);
			plan.add(new Planned(goodsId, def, params, state, match, units, lineTotal));
			total += lineTotal;
			items += itemCount;
		}

		// 2. The total the player saw (A-4)
		if (Math.abs(total - request.expectedTotal()) > request.expectedTotal() * config.trade.priceTolerance) {
			return new TradeResultS2C(TradeResult.PRICE_CHANGED, false, items, total);
		}

		// 3. Sell: items out, market state, propagation, reputation (as single sales), then the money once.
		Set<Long> changed = new HashSet<>();
		changed.add(market.id());
		for (Planned p : plan) {
			InventoryUtil.remove(player, p.match(), sellSlot, p.units() * p.def().unit(), true);
			PriceModel.applySell(p.params(), p.state(), p.units());
			p.state().setLastTradeGameTime(now);
			Propagation.apply(registry, market, p.goods(), p.units(), now).forEach(m -> changed.add(m.id()));
			Reputation.onTrade(market, player.getUUID(), p.total(), market.shortages().containsKey(p.goods()), now);
			Development.onTrade(market, player, p.total(), now); // N-2
			if (config.log.trades) {
				TradeExpansion.LOGGER.info("{} SELL {} x{} @market#{} total={}{} (sell all)", player.getPlainTextName(), p.def().item(),
						p.units() * p.def().unit(), market.id(), CoinItem.formatCopper(p.total()), card ? " (card)" : "");
			}
		}
		if (card) {
			BankService.accounts(player).add(player.getUUID(), total); // proceeds go to the account when paying by card (E-8)
		} else {
			WalletService.give(player, total);
		}
		registry.setDirty();
		for (Planned p : plan) { // S: API event, one per goods
			com.akf.tradeexpansion.api.TradeExpansionEvents.TRADE.invoker().onTrade(new com.akf.tradeexpansion.api.TradeExpansionEvents.Trade(
					player, market.id(), p.goods(), p.def().item(), false, p.units(), p.units() * p.def().unit(), p.total(), card,
					com.akf.tradeexpansion.api.TradeExpansionEvents.Source.SELL_ALL));
		}
		TradeService.broadcast(player.level().getServer(), registry, changed, now);
		// R: advancements (the whole sale counts as one trade)
		for (Planned p : plan) {
			com.akf.tradeexpansion.advancement.TradeAdvancements.afterSale(player, registry, market, p.goods(),
					PriceModel.sellCapacity(p.params(), p.state()) < 1, now);
		}
		com.akf.tradeexpansion.advancement.TradeAdvancements.afterTrade(player, market, false, items, total, now);
		return new TradeResultS2C(TradeResult.SUCCESS, false, items, total);
	}

	private static TradeResultS2C fail(TradeResult result) {
		return new TradeResultS2C(result, false, 0, 0);
	}
}
