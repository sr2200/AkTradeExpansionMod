package com.akf.tradeexpansion.config;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.BulkDiscount;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings of design chapter G, stored in {@code config/trade_expansion.json} as one JSON object per category
 * ({@code {"currency": {"ratios": [10, 10, 10, 10], ...}, ...}}). Missing keys take the defaults below.
 * <p>
 * Instances are mutable so that the config screen can edit a copy; the live instance is replaced as a whole
 * ({@link #set}), never modified in place.
 */
public final class TradeExpansionConfig {
	public Currency currency;
	public Price price;
	public Recovery recovery;
	public Distance distance;
	public Market market;
	public Wallet wallet;
	public Bank bank;
	public Trade trade;
	public Worldgen worldgen;
	public Log log;
	public Command command;
	public Vanilla vanilla;
	public Client client;
	// Sections added after the 16-field limit of RecordCodecBuilder: decoded by EXTRA_CODEC and merged in.
	public Board board = new Board();
	public Quest quest = new Quest();
	public Demand demand = new Demand();
	public Trader trader = new Trader();
	public Villager villager = new Villager();
	public Consumables consumables = new Consumables();
	/** D-2: per-goods values replacing the goods definition (goods id → values). */
	public Map<String, GoodsOverride> goods = new TreeMap<>();

	public TradeExpansionConfig(Currency currency, Price price, Recovery recovery, Distance distance, Market market, Wallet wallet,
			Bank bank, Trade trade, Worldgen worldgen, Log log, Command command, Vanilla vanilla, Client client) {
		this.currency = currency;
		this.price = price;
		this.recovery = recovery;
		this.distance = distance;
		this.market = market;
		this.wallet = wallet;
		this.bank = bank;
		this.trade = trade;
		this.worldgen = worldgen;
		this.log = log;
		this.command = command;
		this.vanilla = vanilla;
		this.client = client;
	}

	public static TradeExpansionConfig defaults() {
		return new TradeExpansionConfig(new Currency(), new Price(), new Recovery(), new Distance(), new Market(), new Wallet(),
				new Bank(), new Trade(), new Worldgen(), new Log(), new Command(), new Vanilla(), new Client());
	}

	private static <T> MapCodec<T> section(String name, Codec<T> codec, T defaults) {
		// fieldOf + orElse (not optionalFieldOf): missing keys take the default, but every value is always written,
		// so the generated file lists all settings.
		return codec.fieldOf(name).orElse(defaults);
	}

	private static final MapCodec<TradeExpansionConfig> MAIN_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			section("currency", Currency.CODEC, new Currency()).forGetter(c -> c.currency),
			section("price", Price.CODEC, new Price()).forGetter(c -> c.price),
			section("recovery", Recovery.CODEC, new Recovery()).forGetter(c -> c.recovery),
			section("distance", Distance.CODEC, new Distance()).forGetter(c -> c.distance),
			section("market", Market.CODEC, new Market()).forGetter(c -> c.market),
			section("wallet", Wallet.CODEC, new Wallet()).forGetter(c -> c.wallet),
			section("bank", Bank.CODEC, new Bank()).forGetter(c -> c.bank),
			section("trade", Trade.CODEC, new Trade()).forGetter(c -> c.trade),
			section("worldgen", Worldgen.CODEC, new Worldgen()).forGetter(c -> c.worldgen),
			section("log", Log.CODEC, new Log()).forGetter(c -> c.log),
			section("command", Command.CODEC, new Command()).forGetter(c -> c.command),
			section("vanilla", Vanilla.CODEC, new Vanilla()).forGetter(c -> c.vanilla),
			section("client", Client.CODEC, new Client()).forGetter(c -> c.client)
	).apply(i, TradeExpansionConfig::new));

	/** Sections of stages 10 / 13 / 14 (design A-5, I, J, K, H). */
	private record Extra(Board board, Quest quest, Demand demand, Trader trader, Villager villager, Consumables consumables,
			Map<String, GoodsOverride> goods) {
		static final MapCodec<Extra> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				section("board", Board.CODEC, new Board()).forGetter(Extra::board),
				section("quest", Quest.CODEC, new Quest()).forGetter(Extra::quest),
				section("demand", Demand.CODEC, new Demand()).forGetter(Extra::demand),
				section("trader", Trader.CODEC, new Trader()).forGetter(Extra::trader),
				section("villager", Villager.CODEC, new Villager()).forGetter(Extra::villager),
				section("consumables", Consumables.CODEC, new Consumables()).forGetter(Extra::consumables),
				section("goods", Codec.unboundedMap(Codec.STRING, GoodsOverride.CODEC), Map.<String, GoodsOverride>of()).forGetter(Extra::goods)
		).apply(i, Extra::new));
	}

	public static final Codec<TradeExpansionConfig> CODEC = Codec.mapPair(MAIN_CODEC, Extra.CODEC).xmap(pair -> {
		TradeExpansionConfig c = pair.getFirst();
		Extra e = pair.getSecond();
		c.board = e.board();
		c.quest = e.quest();
		c.demand = e.demand();
		c.trader = e.trader();
		c.villager = e.villager();
		c.consumables = e.consumables();
		c.goods = new TreeMap<>(e.goods());
		return c;
	}, c -> Pair.of(c, new Extra(c.board, c.quest, c.demand, c.trader, c.villager, c.consumables, c.goods))).codec();

	private static volatile TradeExpansionConfig current = defaults();

	public static TradeExpansionConfig get() {
		return current;
	}

	public static void set(TradeExpansionConfig config) {
		current = config;
	}

	// ---- categories ----

	public static final class Currency {
		public List<Integer> ratios = new ArrayList<>(List.of(10, 10, 10, 10));
		public boolean ratioLocked = true;
		public boolean allowMinting = true;
		public double exchangeFee = 0.0;
		public long startingMoney = 0;
		public boolean mobDropCoins = true;
		public boolean sneakUseExchange = true;
		public int emeraldCoinsPerBlock = 1;
		/** E-2 (v0.27.3): iron block → iron coins; off by default because iron golem farms give unlimited iron. */
		public boolean allowIronMinting = false;

		public Currency() {
		}

		Currency(List<Integer> ratios, boolean ratioLocked, boolean allowMinting, double exchangeFee, long startingMoney,
				boolean mobDropCoins, boolean sneakUseExchange, int emeraldCoinsPerBlock, boolean allowIronMinting) {
			this.ratios = new ArrayList<>(ratios);
			this.ratioLocked = ratioLocked;
			this.allowMinting = allowMinting;
			this.exchangeFee = exchangeFee;
			this.startingMoney = startingMoney;
			this.mobDropCoins = mobDropCoins;
			this.sneakUseExchange = sneakUseExchange;
			this.emeraldCoinsPerBlock = emeraldCoinsPerBlock;
			this.allowIronMinting = allowIronMinting;
		}

		static final Codec<Currency> CODEC = RecordCodecBuilder.create(i -> {
			Currency d = new Currency();
			return i.group(
					Codec.INT.listOf().fieldOf("ratios").orElse(d.ratios).forGetter(c -> c.ratios),
					Codec.BOOL.fieldOf("ratioLocked").orElse(d.ratioLocked).forGetter(c -> c.ratioLocked),
					Codec.BOOL.fieldOf("allowMinting").orElse(d.allowMinting).forGetter(c -> c.allowMinting),
					Codec.DOUBLE.fieldOf("exchangeFee").orElse(d.exchangeFee).forGetter(c -> c.exchangeFee),
					Codec.LONG.fieldOf("startingMoney").orElse(d.startingMoney).forGetter(c -> c.startingMoney),
					Codec.BOOL.fieldOf("mobDropCoins").orElse(d.mobDropCoins).forGetter(c -> c.mobDropCoins),
					Codec.BOOL.fieldOf("sneakUseExchange").orElse(d.sneakUseExchange).forGetter(c -> c.sneakUseExchange),
					Codec.INT.fieldOf("emeraldCoinsPerBlock").orElse(d.emeraldCoinsPerBlock).forGetter(c -> c.emeraldCoinsPerBlock),
					Codec.BOOL.fieldOf("allowIronMinting").orElse(d.allowIronMinting).forGetter(c -> c.allowIronMinting)
			).apply(i, Currency::new);
		});

		public int[] ratioArray() {
			return ratios.stream().mapToInt(Integer::intValue).toArray();
		}
	}

	public static final class Price {
		public double defaultSpread = 0.4;
		public double defaultElasticity = 32;
		public double minMultiplier = 0.25;
		public double maxMultiplier = 3.0;
		public double jitter = 0.15;
		public double defaultMaxStockMultiplier = 2.0;
		public double globalMultiplier = 1.0;

		public Price() {
		}

		Price(double defaultSpread, double defaultElasticity, double minMultiplier, double maxMultiplier, double jitter,
				double defaultMaxStockMultiplier, double globalMultiplier) {
			this.defaultSpread = defaultSpread;
			this.defaultElasticity = defaultElasticity;
			this.minMultiplier = minMultiplier;
			this.maxMultiplier = maxMultiplier;
			this.jitter = jitter;
			this.defaultMaxStockMultiplier = defaultMaxStockMultiplier;
			this.globalMultiplier = globalMultiplier;
		}

		static final Codec<Price> CODEC = RecordCodecBuilder.create(i -> {
			Price d = new Price();
			return i.group(
					Codec.DOUBLE.fieldOf("defaultSpread").orElse(d.defaultSpread).forGetter(c -> c.defaultSpread),
					Codec.DOUBLE.fieldOf("defaultElasticity").orElse(d.defaultElasticity).forGetter(c -> c.defaultElasticity),
					Codec.DOUBLE.fieldOf("minMultiplier").orElse(d.minMultiplier).forGetter(c -> c.minMultiplier),
					Codec.DOUBLE.fieldOf("maxMultiplier").orElse(d.maxMultiplier).forGetter(c -> c.maxMultiplier),
					Codec.DOUBLE.fieldOf("jitter").orElse(d.jitter).forGetter(c -> c.jitter),
					Codec.DOUBLE.fieldOf("defaultMaxStockMultiplier").orElse(d.defaultMaxStockMultiplier).forGetter(c -> c.defaultMaxStockMultiplier),
					Codec.DOUBLE.fieldOf("globalMultiplier").orElse(d.globalMultiplier).forGetter(c -> c.globalMultiplier)
			).apply(i, Price::new);
		});
	}

	/** B-3: which clock drives lazy recovery. */
	public enum RecoveryClock implements StringRepresentable {
		GAME_TIME, DAY_TIME;

		public static final Codec<RecoveryClock> CODEC = StringRepresentable.fromEnum(RecoveryClock::values);

		@Override
		public String getSerializedName() {
			return name();
		}
	}

	public static final class Recovery {
		public double halfLifeDays = 2.0;
		public double restockMultiplier = 1.0;
		public RecoveryClock clock = RecoveryClock.GAME_TIME;
		/** B-3: consumables (#trade_expansion:consumables) consume T × this per day above the target stock. */
		public double consumableMultiplier = 1.0;
		/** B-3: half-life of X for consumables. */
		public double consumableHalfLifeDays = 1.0;

		public Recovery() {
		}

		Recovery(double halfLifeDays, double restockMultiplier, RecoveryClock clock, double consumableMultiplier, double consumableHalfLifeDays) {
			this.halfLifeDays = halfLifeDays;
			this.restockMultiplier = restockMultiplier;
			this.clock = clock;
			this.consumableMultiplier = consumableMultiplier;
			this.consumableHalfLifeDays = consumableHalfLifeDays;
		}

		static final Codec<Recovery> CODEC = RecordCodecBuilder.create(i -> {
			Recovery d = new Recovery();
			return i.group(
					Codec.DOUBLE.fieldOf("halfLifeDays").orElse(d.halfLifeDays).forGetter(c -> c.halfLifeDays),
					Codec.DOUBLE.fieldOf("restockMultiplier").orElse(d.restockMultiplier).forGetter(c -> c.restockMultiplier),
					RecoveryClock.CODEC.fieldOf("clock").orElse(d.clock).forGetter(c -> c.clock),
					Codec.DOUBLE.fieldOf("consumableMultiplier").orElse(d.consumableMultiplier).forGetter(c -> c.consumableMultiplier),
					Codec.DOUBLE.fieldOf("consumableHalfLifeDays").orElse(d.consumableHalfLifeDays).forGetter(c -> c.consumableHalfLifeDays)
			).apply(i, Recovery::new);
		});
	}

	public static final class Distance {
		public double mergeRadius = 64;
		public double nearRadius = 128;
		public double farRadius = 1536;
		public double falloffExponent = 2.0;
		public boolean linkNetherToOverworld = false;
		public double minNewMarketDistance = 256;

		public Distance() {
		}

		Distance(double mergeRadius, double nearRadius, double farRadius, double falloffExponent, boolean linkNetherToOverworld,
				double minNewMarketDistance) {
			this.mergeRadius = mergeRadius;
			this.nearRadius = nearRadius;
			this.farRadius = farRadius;
			this.falloffExponent = falloffExponent;
			this.linkNetherToOverworld = linkNetherToOverworld;
			this.minNewMarketDistance = minNewMarketDistance;
		}

		static final Codec<Distance> CODEC = RecordCodecBuilder.create(i -> {
			Distance d = new Distance();
			return i.group(
					Codec.DOUBLE.fieldOf("mergeRadius").orElse(d.mergeRadius).forGetter(c -> c.mergeRadius),
					Codec.DOUBLE.fieldOf("nearRadius").orElse(d.nearRadius).forGetter(c -> c.nearRadius),
					Codec.DOUBLE.fieldOf("farRadius").orElse(d.farRadius).forGetter(c -> c.farRadius),
					Codec.DOUBLE.fieldOf("falloffExponent").orElse(d.falloffExponent).forGetter(c -> c.falloffExponent),
					Codec.BOOL.fieldOf("linkNetherToOverworld").orElse(d.linkNetherToOverworld).forGetter(c -> c.linkNetherToOverworld),
					Codec.DOUBLE.fieldOf("minNewMarketDistance").orElse(d.minNewMarketDistance).forGetter(c -> c.minNewMarketDistance)
			).apply(i, Distance::new);
		});
	}

	public static final class Market {
		public int minGoods = 10;
		public int maxGoods = 20;
		public List<Integer> levelThresholds = new ArrayList<>(List.of(5, 10, 20, 40));
		public List<Integer> villagerThresholds = new ArrayList<>(List.of(3, 6, 12, 24));
		public int levelRadius = 64;
		public double levelCheckIntervalDays = 1.0;
		/** C-4: who may rename a market. */
		public RenamePermission renamePermission = RenamePermission.NAMER_OR_OP;
		/** C-1: where a crafted stall may found a new market. */
		public NewMarketRequires newMarketRequires = NewMarketRequires.BEDS_AND_VILLAGERS;
		public int newMarketMinBeds = 3;
		public int newMarketMinVillagers = 3;
		/** C-1 (v0.27.1): with BEDS_AND_VILLAGERS, this many beds allow more villagers than beds (0 = beds must always be at least the villagers). */
		public int newMarketEnoughBeds = 5;
		/** D-4 (v0.27, Q21): the market loses goods when its level goes down. */
		public boolean shrinkOnLevelDown = true;
		/** D-5: number of today's specials for levels 1..5 (0 = none). */
		public List<Integer> specialGoods = new ArrayList<>(List.of(1, 1, 2, 2, 2));
		public double specialPriceMultiplier = 1.2;
		/** D-5: a special starts with this share of its target stock (at least 1). */
		public double specialStockRatio = 0.5;

		public Market() {
		}

		Market(int minGoods, int maxGoods, List<Integer> levelThresholds, List<Integer> villagerThresholds, int levelRadius,
				double levelCheckIntervalDays, RenamePermission renamePermission, NewMarketRequires newMarketRequires,
				int newMarketMinBeds, int newMarketMinVillagers, int newMarketEnoughBeds, boolean shrinkOnLevelDown, List<Integer> specialGoods,
				double specialPriceMultiplier, double specialStockRatio) {
			this.minGoods = minGoods;
			this.maxGoods = maxGoods;
			this.levelThresholds = new ArrayList<>(levelThresholds);
			this.villagerThresholds = new ArrayList<>(villagerThresholds);
			this.levelRadius = levelRadius;
			this.levelCheckIntervalDays = levelCheckIntervalDays;
			this.renamePermission = renamePermission;
			this.newMarketRequires = newMarketRequires;
			this.newMarketMinBeds = newMarketMinBeds;
			this.newMarketMinVillagers = newMarketMinVillagers;
			this.newMarketEnoughBeds = newMarketEnoughBeds;
			this.shrinkOnLevelDown = shrinkOnLevelDown;
			this.specialGoods = new ArrayList<>(specialGoods);
			this.specialPriceMultiplier = specialPriceMultiplier;
			this.specialStockRatio = specialStockRatio;
		}

		static final Codec<Market> CODEC = RecordCodecBuilder.create(i -> {
			Market d = new Market();
			return i.group(
					Codec.INT.fieldOf("minGoods").orElse(d.minGoods).forGetter(c -> c.minGoods),
					Codec.INT.fieldOf("maxGoods").orElse(d.maxGoods).forGetter(c -> c.maxGoods),
					Codec.INT.listOf().fieldOf("levelThresholds").orElse(d.levelThresholds).forGetter(c -> c.levelThresholds),
					Codec.INT.listOf().fieldOf("villagerThresholds").orElse(d.villagerThresholds).forGetter(c -> c.villagerThresholds),
					Codec.INT.fieldOf("levelRadius").orElse(d.levelRadius).forGetter(c -> c.levelRadius),
					Codec.DOUBLE.fieldOf("levelCheckIntervalDays").orElse(d.levelCheckIntervalDays).forGetter(c -> c.levelCheckIntervalDays),
					RenamePermission.CODEC.fieldOf("renamePermission").orElse(d.renamePermission).forGetter(c -> c.renamePermission),
					NewMarketRequires.CODEC.fieldOf("newMarketRequires").orElse(d.newMarketRequires).forGetter(c -> c.newMarketRequires),
					Codec.INT.fieldOf("newMarketMinBeds").orElse(d.newMarketMinBeds).forGetter(c -> c.newMarketMinBeds),
					Codec.INT.fieldOf("newMarketMinVillagers").orElse(d.newMarketMinVillagers).forGetter(c -> c.newMarketMinVillagers),
					Codec.INT.fieldOf("newMarketEnoughBeds").orElse(d.newMarketEnoughBeds).forGetter(c -> c.newMarketEnoughBeds),
					Codec.BOOL.fieldOf("shrinkOnLevelDown").orElse(d.shrinkOnLevelDown).forGetter(c -> c.shrinkOnLevelDown),
					Codec.INT.listOf().fieldOf("specialGoods").orElse(d.specialGoods).forGetter(c -> c.specialGoods),
					Codec.DOUBLE.fieldOf("specialPriceMultiplier").orElse(d.specialPriceMultiplier).forGetter(c -> c.specialPriceMultiplier),
					Codec.DOUBLE.fieldOf("specialStockRatio").orElse(d.specialStockRatio).forGetter(c -> c.specialStockRatio)
			).apply(i, Market::new);
		});

		public int[] levelThresholdArray() {
			return levelThresholds.stream().mapToInt(Integer::intValue).toArray();
		}

		public int[] villagerThresholdArray() {
			return villagerThresholds.stream().mapToInt(Integer::intValue).toArray();
		}

		/** D-5: number of specials at {@code level} (the last entry repeats for higher levels). */
		public int specialGoodsAt(int level) {
			if (specialGoods.isEmpty()) return 0;
			return Math.max(0, specialGoods.get(Math.clamp(level - 1, 0, specialGoods.size() - 1)));
		}
	}

	/** E-6 (stage 12). */
	public static final class Wallet {
		public long capacity = 20_000;
		public boolean autoCollect = true;

		public Wallet() {
		}

		Wallet(long capacity, boolean autoCollect) {
			this.capacity = capacity;
			this.autoCollect = autoCollect;
		}

		static final Codec<Wallet> CODEC = RecordCodecBuilder.create(i -> {
			Wallet d = new Wallet();
			return i.group(
					Codec.LONG.fieldOf("capacity").orElse(d.capacity).forGetter(c -> c.capacity),
					Codec.BOOL.fieldOf("autoCollect").orElse(d.autoCollect).forGetter(c -> c.autoCollect)
			).apply(i, Wallet::new);
		});
	}

	/** E-7 / E-8 (stage 12). */
	public static final class Bank {
		public int minMarketLevel = 3;
		public double interestRatePerDay = 0.0;
		public long cardFee = 100;
		public double cardTransactionFee = 0.0;

		public Bank() {
		}

		Bank(int minMarketLevel, double interestRatePerDay, long cardFee, double cardTransactionFee) {
			this.minMarketLevel = minMarketLevel;
			this.interestRatePerDay = interestRatePerDay;
			this.cardFee = cardFee;
			this.cardTransactionFee = cardTransactionFee;
		}

		static final Codec<Bank> CODEC = RecordCodecBuilder.create(i -> {
			Bank d = new Bank();
			return i.group(
					Codec.INT.fieldOf("minMarketLevel").orElse(d.minMarketLevel).forGetter(c -> c.minMarketLevel),
					Codec.DOUBLE.fieldOf("interestRatePerDay").orElse(d.interestRatePerDay).forGetter(c -> c.interestRatePerDay),
					Codec.LONG.fieldOf("cardFee").orElse(d.cardFee).forGetter(c -> c.cardFee),
					Codec.DOUBLE.fieldOf("cardTransactionFee").orElse(d.cardTransactionFee).forGetter(c -> c.cardTransactionFee)
			).apply(i, Bank::new);
		});
	}

	public static final class Trade {
		public double maxDistance = 8;
		public boolean acceptDamaged = false;
		public double priceTolerance = 0.0;
		public int maxPerTransaction = 64;
		public boolean allowShiftAll = true;
		public boolean allowCreative = false;
		/** C-1: action bar hint while holding a stall. */
		public boolean showPlacementHint = true;
		/** B-5 (v0.27): discount on buying at least minQuantity items in one trade. */
		public List<BulkTier> bulkDiscounts = new ArrayList<>(List.of(new BulkTier(16, 0.05), new BulkTier(64, 0.10)));

		public Trade() {
		}

		Trade(double maxDistance, boolean acceptDamaged, double priceTolerance, int maxPerTransaction, boolean allowShiftAll,
				boolean allowCreative, boolean showPlacementHint, List<BulkTier> bulkDiscounts) {
			this.maxDistance = maxDistance;
			this.acceptDamaged = acceptDamaged;
			this.priceTolerance = priceTolerance;
			this.maxPerTransaction = maxPerTransaction;
			this.allowShiftAll = allowShiftAll;
			this.allowCreative = allowCreative;
			this.showPlacementHint = showPlacementHint;
			this.bulkDiscounts = new ArrayList<>(bulkDiscounts);
		}

		static final Codec<Trade> CODEC = RecordCodecBuilder.create(i -> {
			Trade d = new Trade();
			return i.group(
					Codec.DOUBLE.fieldOf("maxDistance").orElse(d.maxDistance).forGetter(c -> c.maxDistance),
					Codec.BOOL.fieldOf("acceptDamaged").orElse(d.acceptDamaged).forGetter(c -> c.acceptDamaged),
					Codec.DOUBLE.fieldOf("priceTolerance").orElse(d.priceTolerance).forGetter(c -> c.priceTolerance),
					Codec.INT.fieldOf("maxPerTransaction").orElse(d.maxPerTransaction).forGetter(c -> c.maxPerTransaction),
					Codec.BOOL.fieldOf("allowShiftAll").orElse(d.allowShiftAll).forGetter(c -> c.allowShiftAll),
					Codec.BOOL.fieldOf("allowCreative").orElse(d.allowCreative).forGetter(c -> c.allowCreative),
					Codec.BOOL.fieldOf("showPlacementHint").orElse(d.showPlacementHint).forGetter(c -> c.showPlacementHint),
					BulkTier.CODEC.listOf().fieldOf("bulkDiscounts").orElse(d.bulkDiscounts).forGetter(c -> c.bulkDiscounts)
			).apply(i, Trade::new);
		});

		public BulkDiscount bulkDiscount() {
			return new BulkDiscount(bulkDiscounts.stream().map(t -> new BulkDiscount.Tier(t.minQuantity(), t.rate())).toList());
		}
	}

	/** One step of {@code trade.bulkDiscounts}: {@code {"minQuantity": 64, "rate": 0.1}}. */
	public record BulkTier(int minQuantity, double rate) {
		static final Codec<BulkTier> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("minQuantity").forGetter(BulkTier::minQuantity),
				Codec.DOUBLE.fieldOf("rate").forGetter(BulkTier::rate)
		).apply(i, BulkTier::new));
	}

	public static final class Worldgen {
		public boolean spawnStallsInVillages = true;
		public boolean protectNaturalStalls = false;
		public boolean spawnTraderNpc = true;

		public Worldgen() {
		}

		Worldgen(boolean spawnStallsInVillages, boolean protectNaturalStalls, boolean spawnTraderNpc) {
			this.spawnStallsInVillages = spawnStallsInVillages;
			this.protectNaturalStalls = protectNaturalStalls;
			this.spawnTraderNpc = spawnTraderNpc;
		}

		static final Codec<Worldgen> CODEC = RecordCodecBuilder.create(i -> {
			Worldgen d = new Worldgen();
			return i.group(
					Codec.BOOL.fieldOf("spawnStallsInVillages").orElse(d.spawnStallsInVillages).forGetter(c -> c.spawnStallsInVillages),
					Codec.BOOL.fieldOf("protectNaturalStalls").orElse(d.protectNaturalStalls).forGetter(c -> c.protectNaturalStalls),
					Codec.BOOL.fieldOf("spawnTraderNpc").orElse(d.spawnTraderNpc).forGetter(c -> c.spawnTraderNpc)
			).apply(i, Worldgen::new);
		});
	}

	public static final class Log {
		public boolean trades = true;

		public Log() {
		}

		Log(boolean trades) {
			this.trades = trades;
		}

		static final Codec<Log> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("trades").orElse(true).forGetter(c -> c.trades)
		).apply(i, Log::new));
	}

	public static final class Command {
		public int permissionLevel = 2;

		public Command() {
		}

		Command(int permissionLevel) {
			this.permissionLevel = permissionLevel;
		}

		static final Codec<Command> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("permissionLevel").orElse(2).forGetter(c -> c.permissionLevel)
		).apply(i, Command::new));
	}

	public static final class Vanilla {
		public boolean disableVillagerTrades = false;

		public Vanilla() {
		}

		Vanilla(boolean disableVillagerTrades) {
			this.disableVillagerTrades = disableVillagerTrades;
		}

		static final Codec<Vanilla> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("disableVillagerTrades").orElse(false).forGetter(c -> c.disableVillagerTrades)
		).apply(i, Vanilla::new));
	}

	/**
	 * C-1: where a crafted stall may found a new market. 1 VILLAGE = inside a village structure, 2 BEDS = enough beds,
	 * 3 VILLAGERS = enough villagers, 4 BEDS_AND_VILLAGERS = enough villagers and at least as many beds as villagers
	 * (or at least {@code newMarketEnoughBeds} beds).
	 */
	public enum NewMarketRequires implements StringRepresentable {
		VILLAGE, BEDS, VILLAGERS, BEDS_AND_VILLAGERS;

		public static final Codec<NewMarketRequires> CODEC = StringRepresentable.fromEnum(NewMarketRequires::values);

		@Override
		public String getSerializedName() {
			return name();
		}
	}

	/** C-4: who may rename a market. */
	public enum RenamePermission implements StringRepresentable {
		ANYONE, NAMER_OR_OP, OP_ONLY;

		public static final Codec<RenamePermission> CODEC = StringRepresentable.fromEnum(RenamePermission::values);

		@Override
		public String getSerializedName() {
			return name();
		}
	}

	/** A-4: where the trade screen shows the cash / bank balance (the top-right corner is often used by other mods' buttons). */
	public enum BalancePosition implements StringRepresentable {
		INVENTORY_RIGHT, BELOW_SELL_SLOT, TOP_RIGHT;

		public static final Codec<BalancePosition> CODEC = StringRepresentable.fromEnum(BalancePosition::values);

		@Override
		public String getSerializedName() {
			return name();
		}
	}

	/** Client-only settings: read from each client's own file, never synced. */
	public static final class Client {
		public boolean showDebugTooltip = false;
		public BalancePosition balancePosition = BalancePosition.INVENTORY_RIGHT;

		public Client() {
		}

		Client(boolean showDebugTooltip, BalancePosition balancePosition) {
			this.showDebugTooltip = showDebugTooltip;
			this.balancePosition = balancePosition;
		}

		static final Codec<Client> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("showDebugTooltip").orElse(false).forGetter(c -> c.showDebugTooltip),
				BalancePosition.CODEC.fieldOf("balancePosition").orElse(BalancePosition.INVENTORY_RIGHT).forGetter(c -> c.balancePosition)
		).apply(i, Client::new));
	}

	/** I: bulletin board. */
	public static final class Board {
		public int radius = 512;
		public int maxMarkets = 20;
		/** I (v0.24): a price this much better than at the current market counts as a deal. */
		public double dealThreshold = 0.1;

		public Board() {
		}

		Board(int radius, int maxMarkets, double dealThreshold) {
			this.radius = radius;
			this.maxMarkets = maxMarkets;
			this.dealThreshold = dealThreshold;
		}

		static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("radius").orElse(512).forGetter(c -> c.radius),
				Codec.INT.fieldOf("maxMarkets").orElse(20).forGetter(c -> c.maxMarkets),
				Codec.DOUBLE.fieldOf("dealThreshold").orElse(0.1).forGetter(c -> c.dealThreshold)
		).apply(i, Board::new));
	}

	/** J: delivery requests. */
	public static final class Quest {
		public int maxPerMarket = 3;
		public double refreshDays = 1.0;
		public double durationDays = 3.0;
		public List<Double> rewardMultipliers = new ArrayList<>(List.of(1.2, 1.5, 2.0));
		public List<Integer> difficultyThresholds = new ArrayList<>(List.of(200, 1000));

		public Quest() {
		}

		Quest(int maxPerMarket, double refreshDays, double durationDays, List<Double> rewardMultipliers, List<Integer> difficultyThresholds) {
			this.maxPerMarket = maxPerMarket;
			this.refreshDays = refreshDays;
			this.durationDays = durationDays;
			this.rewardMultipliers = new ArrayList<>(rewardMultipliers);
			this.difficultyThresholds = new ArrayList<>(difficultyThresholds);
		}

		static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> {
			Quest d = new Quest();
			return i.group(
					Codec.INT.fieldOf("maxPerMarket").orElse(d.maxPerMarket).forGetter(c -> c.maxPerMarket),
					Codec.DOUBLE.fieldOf("refreshDays").orElse(d.refreshDays).forGetter(c -> c.refreshDays),
					Codec.DOUBLE.fieldOf("durationDays").orElse(d.durationDays).forGetter(c -> c.durationDays),
					Codec.DOUBLE.listOf().fieldOf("rewardMultipliers").orElse(d.rewardMultipliers).forGetter(c -> c.rewardMultipliers),
					Codec.INT.listOf().fieldOf("difficultyThresholds").orElse(d.difficultyThresholds).forGetter(c -> c.difficultyThresholds)
			).apply(i, Quest::new);
		});
	}

	/** K: shortages. */
	public static final class Demand {
		public int maxShortages = 2;
		public double durationDays = 3.0;
		public double minMultiplier = 1.5;
		public double maxMultiplier = 2.0;

		public Demand() {
		}

		Demand(int maxShortages, double durationDays, double minMultiplier, double maxMultiplier) {
			this.maxShortages = maxShortages;
			this.durationDays = durationDays;
			this.minMultiplier = minMultiplier;
			this.maxMultiplier = maxMultiplier;
		}

		static final Codec<Demand> CODEC = RecordCodecBuilder.create(i -> {
			Demand d = new Demand();
			return i.group(
					Codec.INT.fieldOf("maxShortages").orElse(d.maxShortages).forGetter(c -> c.maxShortages),
					Codec.DOUBLE.fieldOf("durationDays").orElse(d.durationDays).forGetter(c -> c.durationDays),
					Codec.DOUBLE.fieldOf("minMultiplier").orElse(d.minMultiplier).forGetter(c -> c.minMultiplier),
					Codec.DOUBLE.fieldOf("maxMultiplier").orElse(d.maxMultiplier).forGetter(c -> c.maxMultiplier)
			).apply(i, Demand::new);
		});
	}

	/** A-5 (stage 10). */
	public static final class Trader {
		public boolean spawnOnOpen = true;

		public Trader() {
		}

		Trader(boolean spawnOnOpen) {
			this.spawnOnOpen = spawnOnOpen;
		}

		static final Codec<Trader> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("spawnOnOpen").orElse(true).forGetter(c -> c.spawnOnOpen)
		).apply(i, Trader::new));
	}

	/** H (stage 14). */
	public static final class Villager {
		public boolean coinTrades = true;
		public int emeraldPrice = 30;
		/** H-4 14b: the wandering trader also takes coins (only while coinTrades is on). */
		public boolean wanderingTraderCoins = true;
		/** H-2 14c: villagers never sell goods below base_price × this (0 = off). */
		public double minGoodsPriceMultiplier = 1.4;

		public Villager() {
		}

		Villager(boolean coinTrades, int emeraldPrice, boolean wanderingTraderCoins, double minGoodsPriceMultiplier) {
			this.coinTrades = coinTrades;
			this.emeraldPrice = emeraldPrice;
			this.wanderingTraderCoins = wanderingTraderCoins;
			this.minGoodsPriceMultiplier = minGoodsPriceMultiplier;
		}

		static final Codec<Villager> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("coinTrades").orElse(true).forGetter(c -> c.coinTrades),
				Codec.INT.fieldOf("emeraldPrice").orElse(30).forGetter(c -> c.emeraldPrice),
				Codec.BOOL.fieldOf("wanderingTraderCoins").orElse(true).forGetter(c -> c.wanderingTraderCoins),
				Codec.DOUBLE.fieldOf("minGoodsPriceMultiplier").orElse(1.4).forGetter(c -> c.minGoodsPriceMultiplier)
		).apply(i, Villager::new));
	}

	/**
	 * B-3: changes to the consumables tag ({@code #trade_expansion:consumables}) without a datapack, as item ids.
	 * {@code remove} wins over {@code add} and the tag. Edited by {@code /tradeex consumable} and the config screen.
	 */
	public static final class Consumables {
		public List<String> add = new ArrayList<>();
		public List<String> remove = new ArrayList<>();

		public Consumables() {
		}

		Consumables(List<String> add, List<String> remove) {
			this.add = new ArrayList<>(add);
			this.remove = new ArrayList<>(remove);
		}

		static final Codec<Consumables> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().fieldOf("add").orElse(List.of()).forGetter(c -> c.add),
				Codec.STRING.listOf().fieldOf("remove").orElse(List.of()).forGetter(c -> c.remove)
		).apply(i, Consumables::new));
	}

	/** Logs under the mod prefix; used by {@link ConfigValidator}. */
	static void warn(String message, Object... args) {
		TradeExpansion.LOGGER.warn("config: " + message, args);
	}
}
