package com.akf.tradeexpansion.currency;

import com.akf.tradeexpansion.core.CoinMath;
import com.akf.tradeexpansion.core.WalletMath;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.registry.ModItems;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Cash of a player: coins and wallets in the main inventory (hotbar + 27 slots; design E-3 / E-6).
 * Reading ({@link #coinCounts}, {@link #balance}) also runs on the client for the trade screen preview;
 * everything that changes the inventory is server side only.
 */
public final class WalletService {
	private WalletService() {
	}

	/** Coin counts in the main inventory, indexed by {@link CoinType#ordinal()}. */
	public static long[] coinCounts(Player player) {
		Inventory inventory = player.getInventory();
		long[] counts = new long[CoinType.values().length];
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof CoinItem coin) {
				counts[coin.type().ordinal()] += stack.getCount();
			}
		}
		return counts;
	}

	/** Wallets in the main inventory, in slot order (the first one is used for receiving, E-6). */
	public static List<ItemStack> wallets(Player player) {
		Inventory inventory = player.getInventory();
		List<ItemStack> list = new ArrayList<>();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof WalletItem) list.add(stack);
		}
		return list;
	}

	private static long[] walletBalances(List<ItemStack> wallets) {
		return wallets.stream().mapToLong(WalletItem::balance).toArray();
	}

	private static void setWalletBalances(List<ItemStack> wallets, long[] balances) {
		for (int i = 0; i < wallets.size(); i++) WalletItem.setBalance(wallets.get(i), balances[i]);
	}

	/** Coins plus every wallet (E-6). */
	public static long balance(Player player) {
		return WalletMath.funds(walletBalances(wallets(player)), coinCounts(player), CoinType.valueTable());
	}

	public static boolean canPay(Player player, long price) {
		return balance(player) >= price;
	}

	/**
	 * Takes {@code price}: wallets first, then all loose coins. The returned change must be handed back with
	 * {@link #give} (after the goods, so the goods can use the freed coin slots; E-3).
	 *
	 * @return the change, or {@code -1} (and nothing changes) if the player cannot afford it
	 */
	public static long take(Player player, long price) {
		List<ItemStack> wallets = wallets(player);
		WalletMath.Payment payment = WalletMath.pay(walletBalances(wallets), coinCounts(player), price, CoinType.valueTable());
		if (payment == null) return -1;
		setWalletBalances(wallets, payment.wallets());
		if (payment.collectCoins()) collectAll(player);
		player.getInventory().setChanged();
		return payment.change();
	}

	/** Takes {@code price} and returns the change at once. */
	public static boolean pay(Player player, long price) {
		long change = take(player, price);
		if (change < 0) return false;
		give(player, change);
		return true;
	}

	/** Removes every coin from the main inventory and returns the removed counts. */
	public static long[] collectAll(Player player) {
		long[] counts = coinCounts(player);
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (inventory.getItem(slot).getItem() instanceof CoinItem) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		return counts;
	}

	/** Gives {@code amount} copper: into the wallets up to their capacity, the rest as the fewest coins (E-6). */
	public static void give(Player player, long amount) {
		if (amount <= 0) return;
		List<ItemStack> wallets = wallets(player);
		WalletMath.Deposit deposit = WalletMath.deposit(walletBalances(wallets), amount, WalletItem.capacity());
		setWalletBalances(wallets, deposit.wallets());
		giveCoins(player, CoinMath.decompose(deposit.leftover(), CoinType.valueTable()));
	}

	/** Puts coins into the inventory, largest first; whatever does not fit is dropped at the player's feet. */
	public static void giveCoins(Player player, long[] counts) {
		Inventory inventory = player.getInventory();
		for (int i = counts.length - 1; i >= 0; i--) {
			long remaining = counts[i];
			CoinItem item = ModItems.coin(CoinType.values()[i]);
			int maxStack = item.getDefaultMaxStackSize();
			while (remaining > 0) {
				int n = (int) Math.min(remaining, maxStack);
				inventory.placeItemBackInInventory(new ItemStack(item, n), Prediction.SERVER_ONLY);
				remaining -= n;
			}
		}
		inventory.setChanged();
	}

	/**
	 * Takes {@code count} coins of one tier out of the wallet held in a hand (wallet screen, E-6).
	 * Silently ignores requests that do not fit the held wallet.
	 */
	public static void withdraw(Player player, boolean offhand, int coin, int count) {
		ItemStack wallet = player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (!(wallet.getItem() instanceof WalletItem) || coin < 0 || coin >= CoinType.values().length || count <= 0 || count > 2304) return;
		long value = CoinType.values()[coin].value();
		long amount = value * count;
		if (WalletItem.balance(wallet) < amount) return;
		WalletItem.setBalance(wallet, WalletItem.balance(wallet) - amount);
		long[] counts = new long[CoinType.values().length];
		counts[coin] = count;
		giveCoins(player, counts);
	}

	/**
	 * Picked-up coins go straight into a wallet when {@code wallet.autoCollect} is on (E-6).
	 *
	 * @return number of coins stored
	 */
	public static int collectPickup(Player player, ItemStack coins) {
		int stored = 0;
		for (ItemStack wallet : wallets(player)) {
			stored += WalletItem.insertCoins(wallet, coins);
			if (coins.isEmpty()) break;
		}
		if (stored > 0) player.getInventory().setChanged();
		return stored;
	}
}
