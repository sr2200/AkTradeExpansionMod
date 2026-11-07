package com.akf.tradeexpansion.bank;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.item.BankCardItem;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
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

	/** Result codes of the bank tab, shown as {@code bank.ak_trade_expansion.result.<name>}. */
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

	/** The bank with {@code player}'s interest paid up to now (E-7): use this before reading or changing the balance. */
	public static BankAccounts accounts(ServerPlayer player) {
		BankAccounts bank = BankAccounts.get(player.level().getServer());
		TradeExpansionConfig.Bank config = TradeExpansionConfig.get().bank;
		bank.accrue(player.getUUID(), MarketClock.now(player.level()), config.interestRatePerDay, interestCap(player, bank));
		return bank;
	}

	/**
	 * E-7 / N-4: balance up to which interest is paid. An account registered with a bank building that still stands gets
	 * {@code development.bankInterestMaxMultiplier} times the cap; a registration whose building is gone is dropped.
	 */
	public static long interestCap(ServerPlayer player, BankAccounts bank) {
		long cap = TradeExpansionConfig.get().bank.interestMaxBalance;
		Market market = registeredBank(player, bank);
		if (market == null || cap <= 0) return cap;
		double raised = cap * TradeExpansionConfig.get().development.bankInterestMaxMultiplier;
		return raised >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) raised;
	}

	/** The market whose standing bank building the account is registered with, or {@code null} (stale entries are cleared). */
	private static Market registeredBank(ServerPlayer player, BankAccounts bank) {
		long id = bank.bankFacility(player.getUUID());
		if (id < 0) return null;
		Market market = MarketRegistry.get(player.level().getServer()).get(id);
		if (market != null && market.hasFacility(FacilityKind.BANK)) return market;
		bank.setBankFacility(player.getUUID(), -1);
		return null;
	}

	/** Card payment: price plus {@code bank.cardTransactionFee}. */
	public static long cardCharge(long price) {
		return price + (long) Math.ceil(price * TradeExpansionConfig.get().bank.cardTransactionFee);
	}

	/** N-4: no card fee at a market with a bank building. */
	public static long cardCharge(Market market, long price) {
		return market != null && market.hasFacility(FacilityKind.BANK) ? price : cardCharge(price);
	}

	/** E-7 / N-4: the bank tab shows from {@code bank.minMarketLevel}, or at any level with a bank building. */
	public static boolean hasBank(Market market) {
		return market.level() >= TradeExpansionConfig.get().bank.minMarketLevel || market.hasFacility(FacilityKind.BANK);
	}

	public static void sendAccount(ServerPlayer player) {
		sendAccount(player, null);
	}

	/** @param result shown on the bank tab, or {@code null} */
	public static void sendAccount(ServerPlayer player, Result result) {
		BankAccounts bank = accounts(player);
		long balance = bank.balance(player.getUUID());
		Market registered = registeredBank(player, bank);
		ServerPlayNetworking.send(player, new AccountS2C(balance, hasValidCard(player), result == null ? -1 : result.ordinal(),
				interestCap(player, bank), registered == null ? "" : MarketNames.name(registered).getString()));
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
		if (!hasBank(market)) return Result.NO_BANK;

		BankAccounts bank = accounts(player);
		// N-4: using the bank of a market with a bank building registers the account there (higher interest cap).
		if (market.hasFacility(FacilityKind.BANK) && request.action() != BankRequestC2S.Action.ISSUE_CARD) {
			bank.setBankFacility(player.getUUID(), market.id());
		}
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
