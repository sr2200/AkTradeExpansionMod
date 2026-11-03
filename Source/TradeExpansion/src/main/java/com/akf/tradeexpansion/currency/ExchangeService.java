package com.akf.tradeexpansion.currency;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.ExchangeRequestC2S;
import com.akf.tradeexpansion.network.ExchangeResultS2C;
import com.akf.tradeexpansion.trade.TradeResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Exchange tab (design E-3): one tier up or down between loose coins, with {@code currency.exchangeFee}.
 * Down: {@code count} coins → floor(count × ratio × (1 − fee)) lower coins.
 * Up: ceil(count × ratio × (1 + fee)) lower coins → {@code count} higher coins.
 */
public final class ExchangeService {
	private ExchangeService() {
	}

	public static void handle(ServerPlayer player, ExchangeRequestC2S request) {
		ServerPlayNetworking.send(player, new ExchangeResultS2C(process(player, request)));
	}

	private static TradeResult process(ServerPlayer player, ExchangeRequestC2S request) {
		CoinType[] types = CoinType.values();
		int from = request.coin();
		int to = request.up() ? from + 1 : from - 1;
		if (MarketRegistry.get(player.level().getServer()).get(request.marketId()) == null
				|| !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != request.marketId()
				|| from < 0 || from >= types.length || to < 0 || to >= types.length || request.count() <= 0 || request.count() > 640) {
			return TradeResult.INVALID_REQUEST;
		}
		if (!menu.stillValid(player)) return TradeResult.TOO_FAR;
		if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) return TradeResult.CREATIVE_NOT_ALLOWED;

		double fee = TradeExpansionConfig.get().currency.exchangeFee;
		long[] give = new long[types.length];
		long take;
		if (request.up()) {
			int ratio = types[to].ratioToLower();
			take = (long) Math.ceil(request.count() * (double) ratio * (1 + fee) - 1e-9);
			give[to] = request.count();
		} else {
			int ratio = types[from].ratioToLower();
			take = request.count();
			give[to] = (long) Math.floor(request.count() * (double) ratio * (1 - fee) + 1e-9);
		}
		if (count(player, types[from]) < take) return TradeResult.INSUFFICIENT_FUNDS;
		remove(player, types[from], take);
		WalletService.giveCoins(player, give);
		return TradeResult.SUCCESS;
	}

	private static long count(ServerPlayer player, CoinType type) {
		return WalletService.coinCounts(player)[type.ordinal()];
	}

	private static void remove(ServerPlayer player, CoinType type, long amount) {
		Inventory inventory = player.getInventory();
		long rest = amount;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && rest > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof CoinItem coin && coin.type() == type) {
				int n = (int) Math.min(rest, stack.getCount());
				stack.shrink(n);
				rest -= n;
			}
		}
		inventory.setChanged();
	}
}
