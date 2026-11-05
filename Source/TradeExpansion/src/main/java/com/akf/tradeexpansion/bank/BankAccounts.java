package com.akf.tradeexpansion.bank;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.InterestMath;
import com.akf.tradeexpansion.core.PriceModel;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bank accounts of every player, one per server (design E-7 / F-1: {@code ak_trade_expansion:bank}).
 */
public final class BankAccounts extends SavedData {
	/**
	 * Mutable account: balance in copper, the number of the only card that works (E-8), and for the interest (E-7,
	 * v0.29.1) the market-clock time it was last paid up to and the fraction of a copper carried over.
	 */
	public static final class Account {
		private long balance;
		private int cardNumber;
		private long interestSince;
		private double interestFraction;

		Account(long balance, int cardNumber, long interestSince, double interestFraction) {
			this.balance = balance;
			this.cardNumber = cardNumber;
			this.interestSince = interestSince;
			this.interestFraction = interestFraction;
		}

		public long balance() {
			return balance;
		}

		public int cardNumber() {
			return cardNumber;
		}
	}

	private record Entry(UUID uuid, long balance, int cardNumber, long interestSince, double interestFraction) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(Entry::uuid),
				Codec.LONG.fieldOf("balance").forGetter(Entry::balance),
				Codec.INT.optionalFieldOf("card_number", 0).forGetter(Entry::cardNumber),
				Codec.LONG.optionalFieldOf("interest_since", Long.MIN_VALUE).forGetter(Entry::interestSince),
				Codec.DOUBLE.optionalFieldOf("interest_fraction", 0.0).forGetter(Entry::interestFraction)
		).apply(i, Entry::new));
	}

	public static final Codec<BankAccounts> CODEC = Entry.CODEC.listOf().fieldOf("accounts").codec()
			.xmap(BankAccounts::new, BankAccounts::entries);

	public static final SavedDataType<BankAccounts> TYPE = new SavedDataType<>(
			TradeExpansion.id("bank"), BankAccounts::new, CODEC, null);

	private final Map<UUID, Account> accounts = new LinkedHashMap<>();

	private BankAccounts() {
	}

	private BankAccounts(List<Entry> entries) {
		entries.forEach(e -> accounts.put(e.uuid(), new Account(e.balance(), e.cardNumber(), e.interestSince(), e.interestFraction())));
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		accounts.forEach((uuid, a) -> list.add(new Entry(uuid, a.balance, a.cardNumber, a.interestSince, a.interestFraction)));
		return list;
	}

	public static BankAccounts get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public long balance(UUID player) {
		Account a = accounts.get(player);
		return a == null ? 0 : a.balance;
	}

	public int cardNumber(UUID player) {
		Account a = accounts.get(player);
		return a == null ? 0 : a.cardNumber;
	}

	public void add(UUID player, long amount) {
		Account a = accounts.computeIfAbsent(player, k -> new Account(0, 0, Long.MIN_VALUE, 0));
		a.balance = Math.max(0, Math.addExact(a.balance, amount));
		setDirty();
	}

	public void set(UUID player, long balance) {
		accounts.computeIfAbsent(player, k -> new Account(0, 0, Long.MIN_VALUE, 0)).balance = Math.max(0, balance);
		setDirty();
	}

	/**
	 * E-7 (v0.29.1): pays the interest from the last time up to {@code now} (market clock), compounded daily on the balance
	 * up to {@code cap}; fractions of a copper are carried over. Call before reading or changing the balance.
	 */
	public void accrue(UUID player, long now, double ratePerDay, long cap) {
		Account a = accounts.get(player);
		if (a == null) return;
		if (a.interestSince == Long.MIN_VALUE || now < a.interestSince) {
			a.interestSince = now;
			setDirty();
			return;
		}
		double days = (now - a.interestSince) / (double) PriceModel.TICKS_PER_DAY;
		if (days <= 0) return;
		double total = InterestMath.accrue(a.balance + a.interestFraction, days, ratePerDay, cap);
		long whole = (long) Math.floor(total);
		a.interestFraction = total - whole;
		a.balance = whole;
		a.interestSince = now;
		setDirty();
	}

	/** Re-issue: the account's card number goes up, so every older card stops working. */
	public int nextCardNumber(UUID player) {
		Account a = accounts.computeIfAbsent(player, k -> new Account(0, 0, Long.MIN_VALUE, 0));
		a.cardNumber++;
		setDirty();
		return a.cardNumber;
	}
}
