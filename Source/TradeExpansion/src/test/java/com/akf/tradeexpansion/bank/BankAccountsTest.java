package com.akf.tradeexpansion.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akf.tradeexpansion.core.PriceModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Bank accounts (design E-7 / E-8 / F-1, {@code ak_trade_expansion:bank}): saved form and interest. */
class BankAccountsTest {
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final double RATE = 0.0003;
	private static final long DAY = PriceModel.TICKS_PER_DAY;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static BankAccounts empty() {
		return BankAccounts.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"accounts\": []}")).getOrThrow();
	}

	private static BankAccounts roundTrip(BankAccounts bank) {
		return BankAccounts.CODEC.parse(JsonOps.INSTANCE, BankAccounts.CODEC.encodeStart(JsonOps.INSTANCE, bank).getOrThrow()).getOrThrow();
	}

	@Test
	void roundTripKeepsBalanceCardAndBank() {
		BankAccounts bank = empty();
		bank.set(A, 12_345);
		bank.nextCardNumber(A);
		bank.nextCardNumber(A);
		bank.setBankFacility(A, 7);
		bank.accrue(A, 0, RATE, 100_000);
		bank.accrue(A, DAY / 3, RATE, 100_000); // leaves a fraction of a copper
		bank.add(B, 50);
		BankAccounts back = roundTrip(bank);
		assertEquals(12_346, back.balance(A)); // 12,345 × 0.03 % × 1/3 day = 1.23c
		assertEquals(2, back.cardNumber(A));
		assertEquals(7, back.bankFacility(A));
		assertEquals(50, back.balance(B));
		assertEquals(-1, back.bankFacility(B));
		JsonObject entry = BankAccounts.CODEC.encodeStart(JsonOps.INSTANCE, bank).getOrThrow().getAsJsonObject()
				.getAsJsonArray("accounts").get(0).getAsJsonObject();
		for (String key : new String[] {"card_number", "interest_since", "interest_fraction", "bank_facility"}) assertTrue(entry.has(key), key);
	}

	/** An account saved before v0.29.1 / v0.38: only uuid and balance. */
	@Test
	void oldSaveTakesDefaults() {
		JsonElement json = JsonParser.parseString("{\"accounts\": [{\"uuid\": [0, 0, 0, 10], \"balance\": 500}]}");
		BankAccounts bank = BankAccounts.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(500, bank.balance(A));
		assertEquals(0, bank.cardNumber(A));
		assertEquals(-1, bank.bankFacility(A));
		// No interest time yet: the first accrue only starts the clock.
		bank.accrue(A, 10 * DAY, RATE, 100_000);
		assertEquals(500, bank.balance(A));
	}

	@Test
	void unknownPlayersHaveNothing() {
		BankAccounts bank = empty();
		assertEquals(0, bank.balance(A));
		assertEquals(0, bank.cardNumber(A));
		assertEquals(-1, bank.bankFacility(A));
		bank.accrue(A, DAY, RATE, 100_000); // no account is created
		assertEquals(0, roundTrip(bank).balance(A));
	}

	@Test
	void balanceNeverGoesNegative() {
		BankAccounts bank = empty();
		bank.add(A, 100);
		bank.add(A, -300);
		assertEquals(0, bank.balance(A));
		bank.set(A, -5);
		assertEquals(0, bank.balance(A));
	}

	/** E-7: fractions of a copper are carried over, so many short periods pay the same as one long one. */
	@Test
	void interestFractionsAreCarriedOver() {
		BankAccounts often = empty();
		BankAccounts once = empty();
		often.set(A, 10_000);
		once.set(A, 10_000);
		often.accrue(A, 0, RATE, 100_000);
		once.accrue(A, 0, RATE, 100_000);
		for (int i = 1; i <= 100; i++) {
			often.accrue(A, i * DAY / 10, RATE, 100_000);
			often = roundTrip(often); // the fraction survives a save
		}
		once.accrue(A, 10 * DAY, RATE, 100_000);
		assertEquals(once.balance(A), often.balance(A));
		assertEquals(10_030, once.balance(A));
	}

	/** The clock going back (DAY_TIME clock, /time set) pays nothing and restarts from the new time. */
	@Test
	void clockGoingBackPaysNothing() {
		BankAccounts bank = empty();
		bank.set(A, 10_000);
		bank.accrue(A, 100 * DAY, RATE, 100_000);
		bank.accrue(A, 5 * DAY, RATE, 100_000);
		assertEquals(10_000, bank.balance(A));
		bank.accrue(A, 15 * DAY, RATE, 100_000);
		assertEquals(10_030, bank.balance(A));
	}
}
