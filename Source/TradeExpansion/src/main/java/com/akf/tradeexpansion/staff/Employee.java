package com.akf.tradeexpansion.staff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * One hired employee (design Q, v0.48): a carrier that buys a good at its home market, takes it to a destination market
 * and sells it there, or a clerk that sells goods left with it at its home market while the price is high enough.
 * Employees are data only (no entity); {@link StaffService} moves them on with the market clock. Stored in
 * {@code ak_trade_expansion:markets}. Mutable; callers mark the registry dirty.
 */
public final class Employee {
	public enum Role { CARRIER, CLERK }

	/**
	 * HOME: at the home market (a carrier buying, a clerk selling). OUTBOUND / RETURNING: a carrier on the way.
	 * AWAY: a carrier at the destination, selling. QUIT: left the job (purse and goods wait at the home market).
	 */
	public enum Phase { HOME, OUTBOUND, AWAY, RETURNING, QUIT }

	/** What the employee is doing or waiting for (screen and log). */
	public enum Status {
		WORKING, WAIT_PRICE, WAIT_STOCK, WAIT_FUNDS, WAIT_SELL, NOT_DEALT, UNPAID, PAUSED, SOLD_OUT, RESTING, QUIT, HOME_GONE
	}

	/**
	 * A log line, made into text when shown (item and market names in the reader's language).
	 *
	 * @param values numbers of the line; which are coins depends on the key (see {@code StaffService.describe})
	 */
	public record LogEntry(long time, String key, Optional<Identifier> item, long market, List<Long> values) {
		static final Codec<LogEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("time").forGetter(LogEntry::time),
				Codec.STRING.fieldOf("key").forGetter(LogEntry::key),
				Identifier.CODEC.optionalFieldOf("item").forGetter(LogEntry::item),
				Codec.LONG.optionalFieldOf("market", -1L).forGetter(LogEntry::market),
				Codec.LONG.listOf().optionalFieldOf("values", List.of()).forGetter(LogEntry::values)
		).apply(i, LogEntry::new));
	}

	/** Lines kept in the log, newest last. */
	public static final int LOG_SIZE = 10;

	private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, E fallback) {
		Function<String, E> parse = s -> {
			try {
				return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return fallback;
			}
		};
		return Codec.STRING.xmap(parse, e -> e.name().toLowerCase(Locale.ROOT));
	}

	private record Settings(Role role, long home, Identifier goods, long destination, int quantity, long buyLimit, long sellLimit) {
		static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
				enumCodec(Role.class, Role.CLERK).fieldOf("role").forGetter(Settings::role),
				Codec.LONG.fieldOf("home").forGetter(Settings::home),
				Identifier.CODEC.fieldOf("goods").forGetter(Settings::goods),
				Codec.LONG.optionalFieldOf("destination", -1L).forGetter(Settings::destination),
				Codec.INT.optionalFieldOf("quantity", 0).forGetter(Settings::quantity),
				Codec.LONG.optionalFieldOf("buy_limit", 0L).forGetter(Settings::buyLimit),
				Codec.LONG.optionalFieldOf("sell_limit", 1L).forGetter(Settings::sellLimit)
		).apply(i, Settings::new));
	}

	private record State(long purse, int stock, long cargoCost, long tripCost, long tripRevenue, Phase phase, boolean paused, Status status,
			long nextTime, long phaseStart, long wageDue) {
		static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("purse", 0L).forGetter(State::purse),
				Codec.INT.optionalFieldOf("stock", 0).forGetter(State::stock),
				Codec.LONG.optionalFieldOf("cargo_cost", 0L).forGetter(State::cargoCost),
				Codec.LONG.optionalFieldOf("trip_cost", 0L).forGetter(State::tripCost),
				Codec.LONG.optionalFieldOf("trip_revenue", 0L).forGetter(State::tripRevenue),
				enumCodec(Phase.class, Phase.HOME).optionalFieldOf("phase", Phase.HOME).forGetter(State::phase),
				Codec.BOOL.optionalFieldOf("paused", false).forGetter(State::paused),
				enumCodec(Status.class, Status.WORKING).optionalFieldOf("status", Status.WORKING).forGetter(State::status),
				Codec.LONG.optionalFieldOf("next_time", 0L).forGetter(State::nextTime),
				Codec.LONG.optionalFieldOf("phase_start", 0L).forGetter(State::phaseStart),
				Codec.LONG.optionalFieldOf("wage_due", 0L).forGetter(State::wageDue)
		).apply(i, State::new));
	}

	private record Stats(long earned, long spent, long wages, long lost, int trips, int sold, long hired) {
		static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("earned", 0L).forGetter(Stats::earned),
				Codec.LONG.optionalFieldOf("spent", 0L).forGetter(Stats::spent),
				Codec.LONG.optionalFieldOf("wages", 0L).forGetter(Stats::wages),
				Codec.LONG.optionalFieldOf("lost", 0L).forGetter(Stats::lost),
				Codec.INT.optionalFieldOf("trips", 0).forGetter(Stats::trips),
				Codec.INT.optionalFieldOf("sold", 0).forGetter(Stats::sold),
				Codec.LONG.optionalFieldOf("hired", 0L).forGetter(Stats::hired)
		).apply(i, Stats::new));
	}

	public static final Codec<Employee> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(e -> e.id),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(e -> e.owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(e -> e.ownerName),
			Settings.CODEC.fieldOf("settings").forGetter(e -> new Settings(e.role, e.home, e.goods, e.destination, e.quantity, e.buyLimit, e.sellLimit)),
			State.CODEC.fieldOf("state").forGetter(e -> new State(e.purse, e.stock, e.cargoCost, e.tripCost, e.tripRevenue, e.phase, e.paused,
					e.status, e.nextTime, e.phaseStart, e.wageDue)),
			Stats.CODEC.optionalFieldOf("stats", new Stats(0, 0, 0, 0, 0, 0, 0)).forGetter(e -> new Stats(e.earned, e.spent, e.wages, e.lost,
					e.trips, e.sold, e.hired)),
			LogEntry.CODEC.listOf().optionalFieldOf("log", List.of()).forGetter(e -> List.copyOf(e.log))
	).apply(i, Employee::new));

	final int id;
	final UUID owner;
	String ownerName;
	final Role role;
	final long home;
	final Identifier goods;
	/** Carrier: the market it sells at; clerk: -1. */
	final long destination;
	/** Carrier: trading units taken on one trip. */
	int quantity;
	/** Carrier: highest unit price it pays at home. */
	long buyLimit;
	/** Lowest unit price it sells at (carrier: at the destination; clerk: at home). */
	long sellLimit;
	/** Coins of the employee: buys goods, pays the wages, takes the proceeds. */
	long purse;
	/** Trading units carried (carrier) or left for sale (clerk). */
	int stock;
	/** What the units in {@link #stock} cost (carrier), for the profit shown in the log. */
	long cargoCost;
	/** Bought / sold during the current round trip (carrier). */
	long tripCost;
	long tripRevenue;
	Phase phase;
	boolean paused;
	Status status;
	/** Market-clock time of the next step (arrival, or the next try). */
	long nextTime;
	/** When the current phase began (travel progress; how long a carrier has waited at the destination). */
	long phaseStart;
	/** Market-clock time the next day's wage is due. */
	long wageDue;
	long earned;
	long spent;
	long wages;
	long lost;
	int trips;
	int sold;
	long hired;
	final List<LogEntry> log = new ArrayList<>();

	private Employee(int id, UUID owner, String ownerName, Settings settings, State state, Stats stats, List<LogEntry> log) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
		this.role = settings.role();
		this.home = settings.home();
		this.goods = settings.goods();
		this.destination = settings.destination();
		this.quantity = settings.quantity();
		this.buyLimit = settings.buyLimit();
		this.sellLimit = settings.sellLimit();
		this.purse = state.purse();
		this.stock = state.stock();
		this.cargoCost = state.cargoCost();
		this.tripCost = state.tripCost();
		this.tripRevenue = state.tripRevenue();
		this.phase = state.phase();
		this.paused = state.paused();
		this.status = state.status();
		this.nextTime = state.nextTime();
		this.phaseStart = state.phaseStart();
		this.wageDue = state.wageDue();
		this.earned = stats.earned();
		this.spent = stats.spent();
		this.wages = stats.wages();
		this.lost = stats.lost();
		this.trips = stats.trips();
		this.sold = stats.sold();
		this.hired = stats.hired();
		this.log.addAll(log);
	}

	/** A new employee at {@code now}; the first day's wage is part of the hire fee. */
	static Employee hire(int id, UUID owner, String ownerName, Role role, long home, Identifier goods, long destination, int quantity,
			long buyLimit, long sellLimit, long now, long day) {
		Employee e = new Employee(id, owner, ownerName, new Settings(role, home, goods, destination, quantity, buyLimit, sellLimit),
				new State(0, 0, 0, 0, 0, Phase.HOME, false, Status.WORKING, now, now, now + day), new Stats(0, 0, 0, 0, 0, 0, now), List.of());
		return e;
	}

	public int id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public Role role() {
		return role;
	}

	public long home() {
		return home;
	}

	public Identifier goods() {
		return goods;
	}

	public long destination() {
		return destination;
	}

	public Phase phase() {
		return phase;
	}

	public Status status() {
		return status;
	}

	public long purse() {
		return purse;
	}

	public int stock() {
		return stock;
	}

	public boolean quit() {
		return phase == Phase.QUIT;
	}

	/** Makes the next step happen now (test command). */
	public void hurry(long now) {
		if (nextTime > now) nextTime = now;
	}

	/** Adds a log line, dropping the oldest beyond {@link #LOG_SIZE}. */
	void addLog(LogEntry entry) {
		log.add(entry);
		while (log.size() > LOG_SIZE) log.removeFirst();
	}
}
