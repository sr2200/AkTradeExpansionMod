package com.akf.tradeexpansion.trade;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankAccounts;
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
import com.akf.tradeexpansion.market.Propagation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.network.TradeResultS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Buy / sell processing (design 4-3). Runs on the server thread only.
 */
public final class TradeService {
	private TradeService() {
	}

	public static void handle(ServerPlayer player, TradeRequestC2S request) {
		MinecraftServer server = player.level().getServer();
		MarketRegistry registry = MarketRegistry.get(server);
		Market market = registry.get(request.marketId());
		long now = MarketClock.now(player.level());

		TradeResultS2C result = process(player, registry, market, request, now);
		ServerPlayNetworking.send(player, result);
		BankService.sendAccount(player); // bank balance / card validity may have changed (E-7 / E-8)
		if (result.result() != TradeResult.SUCCESS && market != null) {
			// Show the latest prices / stock after a refused trade.
			market.refresh(now);
			ServerPlayNetworking.send(player, MarketSnapshotS2C.of(market, now));
		}
	}

	private static TradeResultS2C process(ServerPlayer player, MarketRegistry registry, Market market, TradeRequestC2S request, long now) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		boolean buy = request.buy();
		int qty = request.quantity();

		// 1. Validation
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()
				|| qty <= 0 || qty > config.trade.maxPerTransaction) {
			return fail(TradeResult.INVALID_REQUEST, request);
		}
		if (!menu.stillValid(player)) return fail(TradeResult.TOO_FAR, request);
		if (player.isCreative() && !config.trade.allowCreative) return fail(TradeResult.CREATIVE_NOT_ALLOWED, request);
		boolean card = request.useCard();
		if (card && !BankService.hasValidCard(player)) return fail(TradeResult.CARD_INVALID, request);

		Identifier goodsId = request.goods();
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		GoodState state = market.good(goodsId);
		Item item = def == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(def.item());
		if (def == null || state == null || item == Items.AIR || (buy ? !def.buy() : !def.sell())) {
			return fail(TradeResult.NOT_AVAILABLE, request);
		}

		// 2. Lazy recovery
		market.refresh(now);
		GoodParams params = market.params(goodsId);
		int unit = def.unit();
		int itemCount = qty * unit; // qty counts trading units (B-5)

		// 3. Price + checks (all-or-nothing, B-5)
		long total;
		if (buy) {
			if (qty > Math.floor(state.stock())) return fail(TradeResult.INSUFFICIENT_STOCK, request);
			total = PriceModel.sumBuy(params, state.pressure(), qty);
		} else {
			if (qty > PriceModel.sellCapacity(params, state)) return fail(TradeResult.STOCK_FULL, request);
			total = PriceModel.sumSell(params, state.pressure(), qty);
		}

		// 4. Compare with the previewed amount (A-4)
		if (Math.abs(total - request.expectedTotal()) > request.expectedTotal() * config.trade.priceTolerance) {
			return new TradeResultS2C(TradeResult.PRICE_CHANGED, buy, qty, total);
		}

		// 5. Move items and money: card = bank account (E-8, no splitting with cash, Q34); cash = wallets then coins (E-6)
		BankAccounts bank = BankAccounts.get(player.level().getServer());
		if (buy) {
			long charge = card ? BankService.cardCharge(total) : total;
			boolean affordable = card ? bank.balance(player.getUUID()) >= charge : WalletService.canPay(player, total);
			if (!affordable) return fail(TradeResult.INSUFFICIENT_FUNDS, request);
			// Coin slots count as free only when the coins are collected (cash payment, E-3).
			if (InventoryUtil.freeSpaceFor(player, item, !card) < itemCount) return fail(TradeResult.INVENTORY_FULL, request);

			if (card) {
				bank.add(player.getUUID(), -charge);
				InventoryUtil.give(player, item, itemCount);
			} else {
				long change = WalletService.take(player, total);
				InventoryUtil.give(player, item, itemCount);
				WalletService.give(player, change); // into the wallet; coins that do not fit are dropped (E-3)
			}
			PriceModel.applyBuy(params, state, qty);
		} else {
			if (InventoryUtil.countPlain(player, item) < itemCount) return fail(TradeResult.INSUFFICIENT_ITEMS, request);
			InventoryUtil.removePlain(player, item, itemCount);
			if (card) {
				bank.add(player.getUUID(), total); // proceeds go to the account when paying by card (E-8)
			} else {
				WalletService.give(player, total);
			}
			PriceModel.applySell(params, state, qty);
		}
		state.setLastTradeGameTime(now);

		// 6-7. Propagate X to nearby markets (C-2)
		double deltaX = buy ? -qty : qty;
		List<Market> affected = Propagation.apply(registry, market, goodsId, deltaX, now);

		// 8. Save + log
		registry.setDirty();
		if (config.log.trades) {
			TradeExpansion.LOGGER.info("{} {} {} x{} @market#{} total={}{}", player.getPlainTextName(), buy ? "BUY" : "SELL",
					def.item(), itemCount, market.id(), CoinItem.formatCopper(total), card ? " (card)" : "");
		}

		// 9. Sync everyone looking at a changed market
		Set<Long> changed = new HashSet<>();
		changed.add(market.id());
		affected.forEach(m -> changed.add(m.id()));
		broadcast(player.level().getServer(), registry, changed, now);

		return new TradeResultS2C(TradeResult.SUCCESS, buy, qty, total);
	}

	private static TradeResultS2C fail(TradeResult result, TradeRequestC2S request) {
		return new TradeResultS2C(result, request.buy(), request.quantity(), 0);
	}

	/** Sends a fresh snapshot to every player whose open market screen shows one of {@code marketIds}. */
	public static void broadcast(MinecraftServer server, MarketRegistry registry, Set<Long> marketIds, long now) {
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (viewer.containerMenu instanceof MarketMenu menu && marketIds.contains(menu.marketId())) {
				Market m = registry.get(menu.marketId());
				if (m == null) continue;
				m.refresh(now);
				ServerPlayNetworking.send(viewer, MarketSnapshotS2C.of(m, now));
			}
		}
	}
}
