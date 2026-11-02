package com.akf.tradeexpansion.core;

/**
 * Money arithmetic with wallets (design E-6). Minecraft-independent.
 * Wallets hold copper amounts; loose coins are counted per tier as in {@link CoinMath}.
 */
public final class WalletMath {
	private WalletMath() {
	}

	/**
	 * @param wallets new balance of every wallet, in inventory order
	 * @param collectCoins whether every loose coin is removed (wallets were not enough)
	 * @param change copper to give back afterwards (wallet first, see {@link #deposit})
	 */
	public record Payment(long[] wallets, boolean collectCoins, long change) {
	}

	/** Total of wallets and loose coins. */
	public static long funds(long[] wallets, long[] coinCounts, long[] values) {
		long sum = CoinMath.total(coinCounts, values);
		for (long w : wallets) sum = Math.addExact(sum, w);
		return sum;
	}

	/**
	 * Pays {@code price}: wallets first (in order), then every loose coin is collected and the change returned.
	 *
	 * @return {@code null} if the funds are insufficient
	 */
	public static Payment pay(long[] wallets, long[] coinCounts, long price, long[] values) {
		if (price < 0) throw new IllegalArgumentException("price must be >= 0");
		if (funds(wallets, coinCounts, values) < price) return null;
		long[] after = wallets.clone();
		long rest = price;
		for (int i = 0; i < after.length && rest > 0; i++) {
			long take = Math.min(after[i], rest);
			after[i] -= take;
			rest -= take;
		}
		if (rest == 0) return new Payment(after, false, 0);
		long coins = CoinMath.total(coinCounts, values);
		return new Payment(after, true, coins - rest);
	}

	/**
	 * @param wallets new balance of every wallet
	 * @param leftover copper that did not fit into any wallet (given as coins)
	 */
	public record Deposit(long[] wallets, long leftover) {
	}

	/** Puts {@code amount} into the wallets in order, each up to {@code capacity}. */
	public static Deposit deposit(long[] wallets, long amount, long capacity) {
		if (amount < 0) throw new IllegalArgumentException("amount must be >= 0");
		long[] after = wallets.clone();
		long rest = amount;
		for (int i = 0; i < after.length && rest > 0; i++) {
			long room = Math.max(0, capacity - after[i]);
			long put = Math.min(room, rest);
			after[i] += put;
			rest -= put;
		}
		return new Deposit(after, rest);
	}

	/** How many coins of {@code value} fit into a wallet with {@code room} copper of space. */
	public static long coinsThatFit(long room, long value, long count) {
		if (room <= 0 || value <= 0) return 0;
		return Math.min(count, room / value);
	}
}
