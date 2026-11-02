package com.akf.tradeexpansion.bank;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.BankCardItem;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.AccountS2C;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.registry.ModDataComponents;
import com.akf.tradeexpansion.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Bank tab operations and card payments (design E-7 / E-8). Server side only.
 */
public final class BankService {
	private BankService() {
	}

	/** Result codes of the bank tab, shown as {@code bank.trade_expansion.result.<name>}. */
	public enum Result {
		SUCCESS, INVALID_REQUEST, TOO_FAR, CREATIVE_NOT_ALLOWED, NO_BANK, INSUFFICIENT_FUNDS
	}

	/** E-8: a card in the main inventory that belongs to the player and carries the account's current number. */
	public static boolean hasValidCard(ServerPlayer player) {
		int number = BankAccounts.get(player.level().getServer()).cardNumber(player.getUUID());
		if (number == 0) return false;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof BankCardItem) {
				ModDataComponents.CardOwner owner = stack.get(ModDataComponents.CARD_OWNER);
				if (owner != null && owner.uuid().equals(player.getUUID()) && owner.number() == number) return true;
			}
		}
		return false;
	}

	/** Card payment: price plus {@code bank.cardTransactionFee}. */
	public static long cardCharge(long price) {
		return price + (long) Math.ceil(price * TradeExpansionConfig.get().bank.cardTransactionFee);
	}

	public static void sendAccount(ServerPlayer player) {
		sendAccount(player, null);
	}

	/** @param result shown on the bank tab, or {@code null} */
	public static void sendAccount(ServerPlayer player, Result result) {
		long balance = BankAccounts.get(player.level().getServer()).balance(player.getUUID());
		ServerPlayNetworking.send(player, new AccountS2C(balance, hasValidCard(player), result == null ? -1 : result.ordinal()));
	}

	public static void handle(ServerPlayer player, BankRequestC2S request) {
		sendAccount(player, process(player, request));
	}

	private static Result process(ServerPlayer player, BankRequestC2S request) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		Market market = MarketRegistry.get(player.level().getServer()).get(request.marketId());
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()
				|| (request.action() != BankRequestC2S.Action.ISSUE_CARD && request.amount() <= 0)) {
			return Result.INVALID_REQUEST;
		}
		if (!menu.stillValid(player)) return Result.TOO_FAR;
		if (player.isCreative() && !config.trade.allowCreative) return Result.CREATIVE_NOT_ALLOWED;
		if (market.level() < config.bank.minMarketLevel) return Result.NO_BANK;

		BankAccounts bank = BankAccounts.get(player.level().getServer());
		long amount = request.amount();
		switch (request.action()) {
			case DEPOSIT -> {
				if (!WalletService.pay(player, amount)) return Result.INSUFFICIENT_FUNDS;
				bank.add(player.getUUID(), amount);
				log(player, "DEPOSIT", amount, market);
			}
			case WITHDRAW -> {
				if (bank.balance(player.getUUID()) < amount) return Result.INSUFFICIENT_FUNDS;
				bank.add(player.getUUID(), -amount);
				WalletService.give(player, amount);
				log(player, "WITHDRAW", amount, market);
			}
			case ISSUE_CARD -> {
				// The fee is paid from the account (E-8, Q33).
				long fee = config.bank.cardFee;
				if (bank.balance(player.getUUID()) < fee) return Result.INSUFFICIENT_FUNDS;
				bank.add(player.getUUID(), -fee);
				int number = bank.nextCardNumber(player.getUUID());
				player.getInventory().placeItemBackInInventory(card(player, number), Prediction.SERVER_ONLY);
				log(player, "ISSUE_CARD#" + number, fee, market);
			}
		}
		return Result.SUCCESS;
	}

	private static ItemStack card(Player player, int number) {
		ItemStack stack = new ItemStack(ModItems.BANK_CARD);
		stack.set(ModDataComponents.CARD_OWNER, new ModDataComponents.CardOwner(player.getUUID(), player.getPlainTextName(), number));
		return stack;
	}

	private static void log(ServerPlayer player, String action, long amount, Market market) {
		if (TradeExpansionConfig.get().log.trades) {
			TradeExpansion.LOGGER.info("{} {} {} @market#{}", player.getPlainTextName(), action, CoinItem.formatCopper(amount), market.id());
		}
	}
}
