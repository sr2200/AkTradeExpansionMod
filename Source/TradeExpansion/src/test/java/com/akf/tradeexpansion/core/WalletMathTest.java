package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WalletMathTest {
	private static final long[] VALUES = CoinMath.values(new int[]{10, 10, 10, 10});
	private static final long[] NO_COINS = new long[5];

	@Test
	void walletOnly() {
		WalletMath.Payment p = WalletMath.pay(new long[]{500}, NO_COINS, 120, VALUES);
		assertArrayEquals(new long[]{380}, p.wallets());
		assertFalse(p.collectCoins());
		assertEquals(0, p.change());
	}

	@Test
	void walletsInOrder() {
		WalletMath.Payment p = WalletMath.pay(new long[]{100, 300}, NO_COINS, 250, VALUES);
		assertArrayEquals(new long[]{0, 150}, p.wallets());
	}

	@Test
	void coinsOnly() {
		// Two diamond coins pay 1,080c: change 920c (design E-3 example).
		WalletMath.Payment p = WalletMath.pay(new long[0], new long[]{0, 0, 0, 2, 0}, 1080, VALUES);
		assertTrue(p.collectCoins());
		assertEquals(920, p.change());
	}

	@Test
	void walletThenCoins() {
		WalletMath.Payment p = WalletMath.pay(new long[]{50}, new long[]{0, 0, 1, 0, 0}, 120, VALUES);
		assertArrayEquals(new long[]{0}, p.wallets());
		assertTrue(p.collectCoins());
		assertEquals(30, p.change()); // 50 from the wallet, 100 in coins for the remaining 70
	}

	@Test
	void insufficient() {
		assertNull(WalletMath.pay(new long[]{50}, new long[]{9, 0, 0, 0, 0}, 60, VALUES));
	}

	@Test
	void depositRespectsCapacity() {
		WalletMath.Deposit d = WalletMath.deposit(new long[]{90_000, 0}, 25_000, 100_000);
		assertArrayEquals(new long[]{100_000, 15_000}, d.wallets());
		assertEquals(0, d.leftover());

		WalletMath.Deposit full = WalletMath.deposit(new long[]{99_990}, 25, 100_000);
		assertArrayEquals(new long[]{100_000}, full.wallets());
		assertEquals(15, full.leftover());

		assertEquals(25, WalletMath.deposit(new long[0], 25, 100_000).leftover());
	}

	@Test
	void coinsThatFit() {
		assertEquals(3, WalletMath.coinsThatFit(350, 100, 10));
		assertEquals(10, WalletMath.coinsThatFit(5000, 100, 10));
		assertEquals(0, WalletMath.coinsThatFit(99, 100, 10));
	}

	@Test
	void randomPaymentsConserveValue() {
		Random random = new Random(7);
		for (int i = 0; i < 10_000; i++) {
			long[] wallets = {random.nextInt(5000), random.nextInt(5000)};
			long[] coins = new long[5];
			for (int t = 0; t < 5; t++) coins[t] = random.nextInt(20);
			long before = WalletMath.funds(wallets, coins, VALUES);
			long price = random.nextLong(before + 1);
			WalletMath.Payment p = WalletMath.pay(wallets, coins, price, VALUES);
			assertNotNull(p);
			long[] coinsAfter = p.collectCoins() ? new long[5] : coins;
			WalletMath.Deposit d = WalletMath.deposit(p.wallets(), p.change(), 100_000);
			long after = WalletMath.funds(d.wallets(), coinsAfter, VALUES) + d.leftover();
			assertEquals(price, before - after);
		}
	}
}
