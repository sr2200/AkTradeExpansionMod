package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.DevelopmentResultS2C;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Development tab actions (design N-6). Runs on the server thread; checks the open screen, the distance to the stall,
 * the dimension and the amounts before changing anything.
 */
public final class DevelopmentService {
	/** Largest single coin donation (1e9 copper). */
	public static final long MAX_DONATION = 1_000_000_000L;

	private DevelopmentService() {
	}

	public static void handle(ServerPlayer player, DevelopmentRequestC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		long now = MarketClock.now(player.level());
		DevelopmentResultS2C result;
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()) {
			result = fail(TradeResult.INVALID_REQUEST, request);
		} else if (!menu.stillValid(player)) {
			result = fail(TradeResult.TOO_FAR, request);
		} else if (!Development.enabled(market)) {
			result = fail(TradeResult.NOT_AVAILABLE, request);
		} else if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) {
			result = fail(TradeResult.CREATIVE_NOT_ALLOWED, request);
		} else {
			result = switch (request.action()) {
				case DONATE_COINS -> donateCoins(player, market, request, now);
				default -> fail(TradeResult.NOT_AVAILABLE, request);
			};
		}
		if (result.result() == TradeResult.SUCCESS) {
			registry.setDirty();
			TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
		}
		ServerPlayNetworking.send(player, result);
		BankService.sendAccount(player);
	}

	private static DevelopmentResultS2C fail(TradeResult result, DevelopmentRequestC2S request) {
		return new DevelopmentResultS2C(result, request.action(), 0, 0);
	}

	/** N-2: coins (from the wallets and loose coins, or from the account with a card) become development points. */
	private static DevelopmentResultS2C donateCoins(ServerPlayer player, Market market, DevelopmentRequestC2S request, long now) {
		long amount = request.amount();
		if (amount <= 0 || amount > MAX_DONATION) return fail(TradeResult.INVALID_REQUEST, request);
		boolean card = request.useCard();
		if (card && !BankService.hasValidCard(player)) return fail(TradeResult.CARD_INVALID, request);
		if (card) {
			BankAccounts bank = BankService.accounts(player);
			if (bank.balance(player.getUUID()) < amount) return fail(TradeResult.INSUFFICIENT_FUNDS, request);
			bank.add(player.getUUID(), -amount); // no card fee: the money goes to the village (N-2)
		} else if (!WalletService.pay(player, amount)) {
			return fail(TradeResult.INSUFFICIENT_FUNDS, request);
		}
		double points = amount * Development.config().pointsPerCoin;
		Development.add(market, player, points, now);
		Reputation.add(market, player.getUUID(), points * Development.config().reputationPerPoint, now);
		if (TradeExpansionConfig.get().log.trades) {
			TradeExpansion.LOGGER.info("{} DONATE {} @market#{}{}", player.getPlainTextName(), CoinItem.formatCopper(amount), market.id(),
					card ? " (card)" : "");
		}
		return new DevelopmentResultS2C(TradeResult.SUCCESS, request.action(), amount, (long) Math.floor(points));
	}
}
