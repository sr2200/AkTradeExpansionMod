package com.akf.tradeexpansion.currency;

import com.akf.tradeexpansion.core.CoinMath;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.registry.ModItems;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and changes the coins in a player's main inventory (design E-3). Server side only.
 */
public final class WalletService {
	private WalletService() {
	}

	/** Coin counts in the main inventory (hotbar + 27 slots), indexed by {@link CoinType#ordinal()}. */
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

	/** Total copper value of the player's coins. */
	public static long balance(Player player) {
		return CoinMath.total(coinCounts(player), CoinType.valueTable());
	}

	/**
	 * Takes {@code price} copper from the player: removes every coin, then returns the change greedily.
	 *
	 * @return {@code false} (and nothing changes) if the player cannot afford it
	 */
	public static boolean pay(Player player, long price) {
		long[] change = CoinMath.change(coinCounts(player), price, CoinType.valueTable());
		if (change == null) return false;

		collectAll(player);
		giveCoins(player, change);
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

	/** Gives {@code amount} copper to the player as the fewest coins. */
	public static void give(Player player, long amount) {
		giveCoins(player, CoinMath.decompose(amount, CoinType.valueTable()));
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
}
