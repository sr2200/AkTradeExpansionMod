package com.akf.tradeexpansion.trade;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.CoinMath;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
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
		long now = player.level().getGameTime();

		TradeResultS2C result = process(player, registry, market, request, now);
		ServerPlayNetworking.send(player, result);
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
				|| qty <= 0 || qty > config.maxPerTransaction()) {
			return fail(TradeResult.INVALID_REQUEST, request);
		}
		if (!menu.stillValid(player)) return fail(TradeResult.TOO_FAR, request);
		if (player.isCreative() && !config.allowCreative()) return fail(TradeResult.CREATIVE_NOT_ALLOWED, request);

		Identifier itemId = request.item();
		GoodsDefinition def = GoodsCatalog.get(itemId);
		GoodState state = market.good(itemId);
		Item item = BuiltInRegistries.ITEM.getValue(itemId);
		if (def == null || state == null || item == Items.AIR || (buy ? !def.buy() : !def.sell())) {
			return fail(TradeResult.NOT_AVAILABLE, request);
		}

		// 2. Lazy recovery
		market.refresh(now);
		GoodParams params = market.params(itemId);

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
		if (Math.abs(total - request.expectedTotal()) > request.expectedTotal() * config.priceTolerance()) {
			return new TradeResultS2C(TradeResult.PRICE_CHANGED, buy, qty, total);
		}

		// 5. Move items and coins
		if (buy) {
			long[] held = WalletService.coinCounts(player);
			long[] change = CoinMath.change(held, total, CoinType.valueTable());
			if (change == null) return fail(TradeResult.INSUFFICIENT_FUNDS, request);
			if (InventoryUtil.freeSpaceFor(player, item) < qty) return fail(TradeResult.INVENTORY_FULL, request);

			WalletService.collectAll(player);
			InventoryUtil.give(player, item, qty);
			WalletService.giveCoins(player, change); // change that does not fit is dropped (E-3)
			PriceModel.applyBuy(params, state, qty);
		} else {
			if (InventoryUtil.countPlain(player, item) < qty) return fail(TradeResult.INSUFFICIENT_ITEMS, request);
			InventoryUtil.removePlain(player, item, qty);
			WalletService.give(player, total);
			PriceModel.applySell(params, state, qty);
		}
		state.setLastTradeGameTime(now);

		// 6-7. Propagate X to nearby markets (C-2)
		double deltaX = buy ? -qty : qty;
		List<Market> affected = Propagation.apply(registry, market, itemId, deltaX, now);

		// 8. Save + log
		registry.setDirty();
		if (config.logTrades()) {
			TradeExpansion.LOGGER.info("{} {} {} x{} @market#{} total={}", player.getPlainTextName(), buy ? "BUY" : "SELL",
					itemId, qty, market.id(), CoinItem.formatCopper(total));
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
