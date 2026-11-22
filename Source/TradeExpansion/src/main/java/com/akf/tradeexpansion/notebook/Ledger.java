package com.akf.tradeexpansion.notebook;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.LedgerMath;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The merchant's ledgers of every player, one per server (design T, v0.81: {@code ak_trade_expansion:ledger}): what each
 * player bought and has not sold yet (for the profit of a sale), the takings of the last days, the morning report's
 * progress and the price notes of the markets they visited. Days are days of the overworld clock ({@link LedgerService#today}).
 */
public final class Ledger extends SavedData {
	/** A market's prices as one player saw them on {@code day}: per goods the buy and sell price of a trading unit (-1 = none). */
	public record PriceNote(long market, long day, List<NotePrice> prices) {
		static final Codec<PriceNote> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("market").forGetter(PriceNote::market),
				Codec.LONG.fieldOf("day").forGetter(PriceNote::day),
				NotePrice.CODEC.listOf().optionalFieldOf("prices", List.of()).forGetter(PriceNote::prices)
		).apply(i, PriceNote::new));
	}

	public record NotePrice(Identifier goods, long buy, long sell) {
		static final Codec<NotePrice> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("goods").forGetter(NotePrice::goods),
				Codec.LONG.optionalFieldOf("buy", -1L).forGetter(NotePrice::buy),
				Codec.LONG.optionalFieldOf("sell", -1L).forGetter(NotePrice::sell)
		).apply(i, NotePrice::new));
	}

	/** One player's ledger. Mutable; changes go through {@link Ledger} so it is saved. */
	public static final class Book {
		final Map<Identifier, LedgerMath.Basis> basis = new HashMap<>();
		final TreeMap<Long, LedgerMath.Day> days = new TreeMap<>();
		/** The day the last morning report was made on (it covered the days before); MIN_VALUE: none yet. */
		long reportedDay = Long.MIN_VALUE;
		/** The player turned the morning report off in the notebook. */
		boolean reportOff;
		/** Newest first. */
		final List<PriceNote> notes = new ArrayList<>();

		public LedgerMath.Basis basis(Identifier goods) {
			return basis.computeIfAbsent(goods, k -> new LedgerMath.Basis(0, 0));
		}

		/** Today's page; days older than {@code keepDays} before today (or after it, the clock was set back) are dropped. */
		public LedgerMath.Day day(long today, int keepDays) {
			days.headMap(today - keepDays).clear();
			days.tailMap(today, false).clear();
			return days.computeIfAbsent(today, LedgerMath.Day::new);
		}

		public List<LedgerMath.Day> days() {
			return List.copyOf(days.values());
		}

		public List<PriceNote> notes() {
			return List.copyOf(notes);
		}

		public boolean reportOff() {
			return reportOff;
		}
	}

	// ---- codecs (snake_case keys; every field may be missing) ----

	private record BasisEntry(Identifier goods, long items, long cost) {
		static final Codec<BasisEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("goods").forGetter(BasisEntry::goods),
				Codec.LONG.fieldOf("items").forGetter(BasisEntry::items),
				Codec.LONG.fieldOf("cost").forGetter(BasisEntry::cost)
		).apply(i, BasisEntry::new));
	}

	private static final Codec<LedgerMath.GoodsLine> GOODS_LINE_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("goods").forGetter(LedgerMath.GoodsLine::goods),
			Codec.LONG.optionalFieldOf("items", 0L).forGetter(LedgerMath.GoodsLine::items),
			Codec.LONG.optionalFieldOf("sales", 0L).forGetter(LedgerMath.GoodsLine::sales),
			Codec.LONG.optionalFieldOf("profit", 0L).forGetter(LedgerMath.GoodsLine::profit)
	).apply(i, LedgerMath.GoodsLine::new));

	private static final Codec<LedgerMath.Day> DAY_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(LedgerMath.Day::day),
			Codec.LONG.optionalFieldOf("sales", 0L).forGetter(LedgerMath.Day::sales),
			Codec.LONG.optionalFieldOf("purchases", 0L).forGetter(LedgerMath.Day::purchases),
			Codec.LONG.optionalFieldOf("profit", 0L).forGetter(LedgerMath.Day::profit),
			Codec.LONG.optionalFieldOf("quests", 0L).forGetter(LedgerMath.Day::quests),
			Codec.LONG.optionalFieldOf("staff", 0L).forGetter(LedgerMath.Day::staff),
			Codec.INT.optionalFieldOf("trades", 0).forGetter(LedgerMath.Day::trades),
			GOODS_LINE_CODEC.listOf().optionalFieldOf("goods", List.of()).forGetter(LedgerMath.Day::goods)
	).apply(i, LedgerMath.Day::new));

	private record Entry(UUID uuid, List<BasisEntry> basis, List<LedgerMath.Day> days, long reportedDay, boolean reportOff,
			List<PriceNote> notes) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(Entry::uuid),
				BasisEntry.CODEC.listOf().optionalFieldOf("basis", List.of()).forGetter(Entry::basis),
				DAY_CODEC.listOf().optionalFieldOf("days", List.of()).forGetter(Entry::days),
				Codec.LONG.optionalFieldOf("reported_day", Long.MIN_VALUE).forGetter(Entry::reportedDay),
				Codec.BOOL.optionalFieldOf("report_off", false).forGetter(Entry::reportOff),
				PriceNote.CODEC.listOf().optionalFieldOf("price_notes", List.of()).forGetter(Entry::notes)
		).apply(i, Entry::new));
	}

	public static final Codec<Ledger> CODEC = Entry.CODEC.listOf().optionalFieldOf("players", List.of()).codec()
			.xmap(Ledger::new, Ledger::entries);

	public static final SavedDataType<Ledger> TYPE = new SavedDataType<>(TradeExpansion.id("ledger"), Ledger::new, CODEC, null);

	private final Map<UUID, Book> books = new LinkedHashMap<>();

	private Ledger() {
	}

	private Ledger(List<Entry> entries) {
		for (Entry e : entries) {
			Book book = new Book();
			e.basis().forEach(b -> book.basis.put(b.goods(), new LedgerMath.Basis(b.items(), b.cost())));
			e.days().forEach(d -> book.days.put(d.day(), d));
			book.reportedDay = e.reportedDay();
			book.reportOff = e.reportOff();
			book.notes.addAll(e.notes());
			books.put(e.uuid(), book);
		}
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		books.forEach((uuid, book) -> {
			List<BasisEntry> basis = new ArrayList<>();
			book.basis.forEach((goods, b) -> {
				if (!b.isEmpty()) basis.add(new BasisEntry(goods, b.items(), b.cost()));
			});
			list.add(new Entry(uuid, basis, book.days(), book.reportedDay, book.reportOff, book.notes()));
		});
		return list;
	}

	public static Ledger get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The player's ledger (made when missing). Call {@link #setDirty()} after changing it. */
	public Book book(UUID player) {
		return books.computeIfAbsent(player, k -> new Book());
	}

	/** The player's ledger, or {@code null} when they have none yet. */
	public Book find(UUID player) {
		return books.get(player);
	}
}
