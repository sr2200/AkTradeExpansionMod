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
 * Settings of design chapter G, stored in {@code config/ak_trade_expansion.json} as one JSON object per category
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
	public Reputation reputation = new Reputation();
	/** N (v0.36): village development. */
	public Development development = new Development();
	/** O (v0.46): rival guilds. */
	public Rivals rivals = new Rivals();
	/** P (v0.46): village trends. */
	public Trend trend = new Trend();
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
			Map<String, GoodsOverride> goods, Reputation reputation, Development development, Rivals rivals, Trend trend) {
		static final MapCodec<Extra> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				section("board", Board.CODEC, new Board()).forGetter(Extra::board),
				section("quest", Quest.CODEC, new Quest()).forGetter(Extra::quest),
				section("demand", Demand.CODEC, new Demand()).forGetter(Extra::demand),
				section("trader", Trader.CODEC, new Trader()).forGetter(Extra::trader),
				section("villager", Villager.CODEC, new Villager()).forGetter(Extra::villager),
				section("consumables", Consumables.CODEC, new Consumables()).forGetter(Extra::consumables),
				section("goods", Codec.unboundedMap(Codec.STRING, GoodsOverride.CODEC), Map.<String, GoodsOverride>of()).forGetter(Extra::goods),
				section("reputation", Reputation.CODEC, new Reputation()).forGetter(Extra::reputation),
				section("development", Development.CODEC, new Development()).forGetter(Extra::development),
				section("rivals", Rivals.CODEC, new Rivals()).forGetter(Extra::rivals),
				section("trend", Trend.CODEC, new Trend()).forGetter(Extra::trend)
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
		c.reputation = e.reputation();
		c.development = e.development();
		c.rivals = e.rivals();
		c.trend = e.trend();
		return c;
	}, c -> Pair.of(c, new Extra(c.board, c.quest, c.demand, c.trader, c.villager, c.consumables, c.goods, c.reputation, c.development, c.rivals, c.trend))).codec();

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
		/** E-2 (v0.31): given on a player's first join; default = 10 loaves of bread at the normal buy price (24c). */
		public long startingMoney = 240;
		public boolean mobDropCoins = true;
		public boolean sneakUseExchange = true;
		public int emeraldCoinsPerBlock = 1;
		/** E-2 (v0.27.3): iron block → iron coins; off by default because iron golem farms give unlimited iron. */
		public boolean allowIronMinting = false;
		/** E-3 / Q16: change dropped at the player's feet can only be picked up by that player. */
		public boolean changeOwnerOnly = false;
		/** E-5 (v0.30): the emerald coin sells at a fixed price (selling does not lower it; same in every market). */
		public boolean emeraldCoinFixedPrice = true;
		/** E-5 (v0.30): markets take any number of emerald coins (no stock cap, never "not buying"). */
		public boolean emeraldCoinUnlimited = true;

		public Currency() {
		}

		Currency(List<Integer> ratios, boolean ratioLocked, boolean allowMinting, double exchangeFee, long startingMoney,
				boolean mobDropCoins, boolean sneakUseExchange, int emeraldCoinsPerBlock, boolean allowIronMinting, boolean changeOwnerOnly,
				boolean emeraldCoinFixedPrice, boolean emeraldCoinUnlimited) {
			this.ratios = new ArrayList<>(ratios);
			this.ratioLocked = ratioLocked;
			this.allowMinting = allowMinting;
			this.exchangeFee = exchangeFee;
			this.startingMoney = startingMoney;
			this.mobDropCoins = mobDropCoins;
			this.sneakUseExchange = sneakUseExchange;
			this.emeraldCoinsPerBlock = emeraldCoinsPerBlock;
			this.allowIronMinting = allowIronMinting;
			this.changeOwnerOnly = changeOwnerOnly;
			this.emeraldCoinFixedPrice = emeraldCoinFixedPrice;
			this.emeraldCoinUnlimited = emeraldCoinUnlimited;
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
					Codec.BOOL.fieldOf("allowIronMinting").orElse(d.allowIronMinting).forGetter(c -> c.allowIronMinting),
					Codec.BOOL.fieldOf("changeOwnerOnly").orElse(d.changeOwnerOnly).forGetter(c -> c.changeOwnerOnly),
					Codec.BOOL.fieldOf("emeraldCoinFixedPrice").orElse(d.emeraldCoinFixedPrice).forGetter(c -> c.emeraldCoinFixedPrice),
					Codec.BOOL.fieldOf("emeraldCoinUnlimited").orElse(d.emeraldCoinUnlimited).forGetter(c -> c.emeraldCoinUnlimited)
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
		/** B-3: consumables (#ak_trade_expansion:consumables) consume T × this per day above the target stock. */
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
		/** C-2 (v0.31): Nether markets share prices with Overworld markets at ×8 coordinates. */
		public boolean linkNetherToOverworld = true;
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
		/** D-4 (v0.27.5): in the Overworld the level also needs villagers (villagerThresholds); the lower of the two levels wins. */
		public boolean levelUsesVillagers = true;
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
				int newMarketMinBeds, int newMarketMinVillagers, int newMarketEnoughBeds, boolean levelUsesVillagers, boolean shrinkOnLevelDown, List<Integer> specialGoods,
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
			this.levelUsesVillagers = levelUsesVillagers;
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
					Codec.BOOL.fieldOf("levelUsesVillagers").orElse(d.levelUsesVillagers).forGetter(c -> c.levelUsesVillagers),
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
		/** E-7 (v0.29.1, C案): interest per in-game day, compounded (0.0003 = 0.03 %, about 0.3 % in 10 days). */
		public double interestRatePerDay = 0.0003;
		public long cardFee = 100;
		public double cardTransactionFee = 0.0;
		/** E-7 (v0.29.1): interest is paid on the balance up to this (0 = no cap). */
		public long interestMaxBalance = 100_000;

		public Bank() {
		}

		Bank(int minMarketLevel, double interestRatePerDay, long cardFee, double cardTransactionFee, long interestMaxBalance) {
			this.minMarketLevel = minMarketLevel;
			this.interestRatePerDay = interestRatePerDay;
			this.cardFee = cardFee;
			this.cardTransactionFee = cardTransactionFee;
			this.interestMaxBalance = interestMaxBalance;
		}

		static final Codec<Bank> CODEC = RecordCodecBuilder.create(i -> {
			Bank d = new Bank();
			return i.group(
					Codec.INT.fieldOf("minMarketLevel").orElse(d.minMarketLevel).forGetter(c -> c.minMarketLevel),
					Codec.DOUBLE.fieldOf("interestRatePerDay").orElse(d.interestRatePerDay).forGetter(c -> c.interestRatePerDay),
					Codec.LONG.fieldOf("cardFee").orElse(d.cardFee).forGetter(c -> c.cardFee),
					Codec.DOUBLE.fieldOf("cardTransactionFee").orElse(d.cardTransactionFee).forGetter(c -> c.cardTransactionFee),
					Codec.LONG.fieldOf("interestMaxBalance").orElse(d.interestMaxBalance).forGetter(c -> c.interestMaxBalance)
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
		/** A-4 "sell all" (v0.32): item ids never sold by it. */
		public List<String> sellAllExclude = new ArrayList<>();
		/** A-4 "sell all": tools / armour (excluded by default) that it may sell anyway. */
		public List<String> sellAllInclude = new ArrayList<>();

		public Client() {
		}

		Client(boolean showDebugTooltip, BalancePosition balancePosition, List<String> sellAllExclude, List<String> sellAllInclude) {
			this.showDebugTooltip = showDebugTooltip;
			this.balancePosition = balancePosition;
			this.sellAllExclude = new ArrayList<>(sellAllExclude);
			this.sellAllInclude = new ArrayList<>(sellAllInclude);
		}

		static final Codec<Client> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("showDebugTooltip").orElse(false).forGetter(c -> c.showDebugTooltip),
				BalancePosition.CODEC.fieldOf("balancePosition").orElse(BalancePosition.INVENTORY_RIGHT).forGetter(c -> c.balancePosition),
				Codec.STRING.listOf().fieldOf("sellAllExclude").orElse(List.of()).forGetter(c -> c.sellAllExclude),
				Codec.STRING.listOf().fieldOf("sellAllInclude").orElse(List.of()).forGetter(c -> c.sellAllInclude)
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
		/** J-2 (v0.35): transport requests per market, on top of maxPerMarket (0 = none). */
		public int transportPerMarket = 1;
		/** J-2: the destination is another market this far away (blocks, Nether ×8 when linked). */
		public double transportMinDistance = 300;
		public double transportMaxDistance = 2000;
		/** J-2: reward = goods' value × min(max, base + perThousandBlocks × distance / 1000); the deposit comes back too. */
		public double transportBaseMultiplier = 0.2;
		public double transportPerThousandBlocks = 0.8;
		public double transportMaxMultiplier = 2.0;

		public Quest() {
		}

		Quest(int maxPerMarket, double refreshDays, double durationDays, List<Double> rewardMultipliers, List<Integer> difficultyThresholds,
				int transportPerMarket, double transportMinDistance, double transportMaxDistance, double transportBaseMultiplier,
				double transportPerThousandBlocks, double transportMaxMultiplier) {
			this.maxPerMarket = maxPerMarket;
			this.refreshDays = refreshDays;
			this.durationDays = durationDays;
			this.rewardMultipliers = new ArrayList<>(rewardMultipliers);
			this.difficultyThresholds = new ArrayList<>(difficultyThresholds);
			this.transportPerMarket = transportPerMarket;
			this.transportMinDistance = transportMinDistance;
			this.transportMaxDistance = transportMaxDistance;
			this.transportBaseMultiplier = transportBaseMultiplier;
			this.transportPerThousandBlocks = transportPerThousandBlocks;
			this.transportMaxMultiplier = transportMaxMultiplier;
		}

		static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> {
			Quest d = new Quest();
			return i.group(
					Codec.INT.fieldOf("maxPerMarket").orElse(d.maxPerMarket).forGetter(c -> c.maxPerMarket),
					Codec.DOUBLE.fieldOf("refreshDays").orElse(d.refreshDays).forGetter(c -> c.refreshDays),
					Codec.DOUBLE.fieldOf("durationDays").orElse(d.durationDays).forGetter(c -> c.durationDays),
					Codec.DOUBLE.listOf().fieldOf("rewardMultipliers").orElse(d.rewardMultipliers).forGetter(c -> c.rewardMultipliers),
					Codec.INT.listOf().fieldOf("difficultyThresholds").orElse(d.difficultyThresholds).forGetter(c -> c.difficultyThresholds),
					Codec.INT.fieldOf("transportPerMarket").orElse(d.transportPerMarket).forGetter(c -> c.transportPerMarket),
					Codec.DOUBLE.fieldOf("transportMinDistance").orElse(d.transportMinDistance).forGetter(c -> c.transportMinDistance),
					Codec.DOUBLE.fieldOf("transportMaxDistance").orElse(d.transportMaxDistance).forGetter(c -> c.transportMaxDistance),
					Codec.DOUBLE.fieldOf("transportBaseMultiplier").orElse(d.transportBaseMultiplier).forGetter(c -> c.transportBaseMultiplier),
					Codec.DOUBLE.fieldOf("transportPerThousandBlocks").orElse(d.transportPerThousandBlocks).forGetter(c -> c.transportPerThousandBlocks),
					Codec.DOUBLE.fieldOf("transportMaxMultiplier").orElse(d.transportMaxMultiplier).forGetter(c -> c.transportMaxMultiplier)
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

	/** O (v0.46): rival guilds moving the prices of a region for a few days. */
	public static final class Rivals {
		public boolean enabled = true;
		public double chancePerDay = 0.5;
		public int maxActive = 3;
		public int minDays = 2;
		public int maxDays = 4;
		public double minImpact = 0.15;
		public double maxImpact = 0.35;
		public double nearRadius = 128;
		public double farRadius = 640;

		public Rivals() {
		}

		Rivals(boolean enabled, double chancePerDay, int maxActive, int minDays, int maxDays, double minImpact, double maxImpact,
				double nearRadius, double farRadius) {
			this.enabled = enabled;
			this.chancePerDay = chancePerDay;
			this.maxActive = maxActive;
			this.minDays = minDays;
			this.maxDays = maxDays;
			this.minImpact = minImpact;
			this.maxImpact = maxImpact;
			this.nearRadius = nearRadius;
			this.farRadius = farRadius;
		}

		static final Codec<Rivals> CODEC = RecordCodecBuilder.create(i -> {
			Rivals d = new Rivals();
			return i.group(
					Codec.BOOL.fieldOf("enabled").orElse(d.enabled).forGetter(c -> c.enabled),
					Codec.DOUBLE.fieldOf("chancePerDay").orElse(d.chancePerDay).forGetter(c -> c.chancePerDay),
					Codec.INT.fieldOf("maxActive").orElse(d.maxActive).forGetter(c -> c.maxActive),
					Codec.INT.fieldOf("minDays").orElse(d.minDays).forGetter(c -> c.minDays),
					Codec.INT.fieldOf("maxDays").orElse(d.maxDays).forGetter(c -> c.maxDays),
					Codec.DOUBLE.fieldOf("minImpact").orElse(d.minImpact).forGetter(c -> c.minImpact),
					Codec.DOUBLE.fieldOf("maxImpact").orElse(d.maxImpact).forGetter(c -> c.maxImpact),
					Codec.DOUBLE.fieldOf("nearRadius").orElse(d.nearRadius).forGetter(c -> c.nearRadius),
					Codec.DOUBLE.fieldOf("farRadius").orElse(d.farRadius).forGetter(c -> c.farRadius)
			).apply(i, Rivals::new);
		});
	}

	/** P (v0.46): popular / unpopular goods groups of each market. */
	public static final class Trend {
		public double durationDays = 5.0;
		public int popularCount = 1;
		public int unpopularCount = 1;
		public double popularMin = 1.15;
		public double popularMax = 1.30;
		public double unpopularMin = 0.80;
		public double unpopularMax = 0.90;
		public int minGoods = 2;

		public Trend() {
		}

		Trend(double durationDays, int popularCount, int unpopularCount, double popularMin, double popularMax, double unpopularMin,
				double unpopularMax, int minGoods) {
			this.durationDays = durationDays;
			this.popularCount = popularCount;
			this.unpopularCount = unpopularCount;
			this.popularMin = popularMin;
			this.popularMax = popularMax;
			this.unpopularMin = unpopularMin;
			this.unpopularMax = unpopularMax;
			this.minGoods = minGoods;
		}

		static final Codec<Trend> CODEC = RecordCodecBuilder.create(i -> {
			Trend d = new Trend();
			return i.group(
					Codec.DOUBLE.fieldOf("durationDays").orElse(d.durationDays).forGetter(c -> c.durationDays),
					Codec.INT.fieldOf("popularCount").orElse(d.popularCount).forGetter(c -> c.popularCount),
					Codec.INT.fieldOf("unpopularCount").orElse(d.unpopularCount).forGetter(c -> c.unpopularCount),
					Codec.DOUBLE.fieldOf("popularMin").orElse(d.popularMin).forGetter(c -> c.popularMin),
					Codec.DOUBLE.fieldOf("popularMax").orElse(d.popularMax).forGetter(c -> c.popularMax),
					Codec.DOUBLE.fieldOf("unpopularMin").orElse(d.unpopularMin).forGetter(c -> c.unpopularMin),
					Codec.DOUBLE.fieldOf("unpopularMax").orElse(d.unpopularMax).forGetter(c -> c.unpopularMax),
					Codec.INT.fieldOf("minGoods").orElse(d.minGoods).forGetter(c -> c.minGoods)
			).apply(i, Trend::new);
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
	 * B-3: changes to the consumables tag ({@code #ak_trade_expansion:consumables}) without a datapack, as item ids.
	 * {@code remove} wins over {@code add} and the tag. Edited by {@code /aktradeex consumable} and the config screen.
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

/** L-2 / M (Q39): reputation of each player with each market. */
	public static final class Reputation {
		/** Reputation on / off (off: everyone is at level 0, nothing changes it). */
		public boolean enabled = true;
		/** Points for levels 1..5. */
		public List<Integer> levelThresholds = new ArrayList<>(List.of(50, 150, 400, 1000, 2500));
		/** Points halve over this many in-game days (0 = no decay). */
		public double halfLifeDays = 30.0;
		/** Points per copper of a trade at the market (100c = 1 point). */
		public double pointsPerCoin = 0.01;
		/** Trade points for selling a good that is short (K) are multiplied by this. */
		public double shortageMultiplier = 2.0;
		/** Points for delivering a request of difficulty 1..3. */
		public List<Integer> questPoints = new ArrayList<>(List.of(10, 25, 50));
		/** Delivering a request you accepted multiplies its points by this. */
		public double acceptedQuestMultiplier = 1.5;
		/** Points per trade with a villager near the market. */
		public double villagerTradePoints = 1.0;
		/** Points lost per hit on a villager or trader near the market. */
		public double attackPenalty = 20.0;
		/** Points lost for killing a villager or trader near the market. */
		public double killPenalty = 100.0;
		/** Points lost per star when an accepted request expires undelivered. */
		public double questFailPenalty = 10.0;
		/** Buy prices cheaper / sell prices higher by this per level (capped per good, B-6). */
		public double priceBonusPerLevel = 0.01;
		/** Request rewards higher by this per level. */
		public double rewardBonusPerLevel = 0.04;
		/** Level needed to deliver 3-star requests (0 = anyone). */
		public int hardQuestLevel = 3;
		/** Level from which the extra specials of the market are shown. */
		public int specialBonusLevel = 4;
		/** Extra specials (D-5) shown only to players at specialBonusLevel or above. */
		public int specialBonusCount = 1;

		public Reputation() {
		}

		Reputation(boolean enabled, List<Integer> levelThresholds, double halfLifeDays, double pointsPerCoin, double shortageMultiplier, List<Integer> questPoints, double acceptedQuestMultiplier, double villagerTradePoints, double attackPenalty, double killPenalty, double questFailPenalty, double priceBonusPerLevel, double rewardBonusPerLevel, int hardQuestLevel, int specialBonusLevel, int specialBonusCount) {
			this.enabled = enabled;
			this.levelThresholds = new ArrayList<>(levelThresholds);
			this.halfLifeDays = halfLifeDays;
			this.pointsPerCoin = pointsPerCoin;
			this.shortageMultiplier = shortageMultiplier;
			this.questPoints = new ArrayList<>(questPoints);
			this.acceptedQuestMultiplier = acceptedQuestMultiplier;
			this.villagerTradePoints = villagerTradePoints;
			this.attackPenalty = attackPenalty;
			this.killPenalty = killPenalty;
			this.questFailPenalty = questFailPenalty;
			this.priceBonusPerLevel = priceBonusPerLevel;
			this.rewardBonusPerLevel = rewardBonusPerLevel;
			this.hardQuestLevel = hardQuestLevel;
			this.specialBonusLevel = specialBonusLevel;
			this.specialBonusCount = specialBonusCount;
		}

		static final Codec<Reputation> CODEC = RecordCodecBuilder.create(i -> {
			Reputation d = new Reputation();
			return i.group(
					Codec.BOOL.fieldOf("enabled").orElse(d.enabled).forGetter(c -> c.enabled),
					Codec.INT.listOf().fieldOf("levelThresholds").orElse(d.levelThresholds).forGetter(c -> c.levelThresholds),
					Codec.DOUBLE.fieldOf("halfLifeDays").orElse(d.halfLifeDays).forGetter(c -> c.halfLifeDays),
					Codec.DOUBLE.fieldOf("pointsPerCoin").orElse(d.pointsPerCoin).forGetter(c -> c.pointsPerCoin),
					Codec.DOUBLE.fieldOf("shortageMultiplier").orElse(d.shortageMultiplier).forGetter(c -> c.shortageMultiplier),
					Codec.INT.listOf().fieldOf("questPoints").orElse(d.questPoints).forGetter(c -> c.questPoints),
					Codec.DOUBLE.fieldOf("acceptedQuestMultiplier").orElse(d.acceptedQuestMultiplier).forGetter(c -> c.acceptedQuestMultiplier),
					Codec.DOUBLE.fieldOf("villagerTradePoints").orElse(d.villagerTradePoints).forGetter(c -> c.villagerTradePoints),
					Codec.DOUBLE.fieldOf("attackPenalty").orElse(d.attackPenalty).forGetter(c -> c.attackPenalty),
					Codec.DOUBLE.fieldOf("killPenalty").orElse(d.killPenalty).forGetter(c -> c.killPenalty),
					Codec.DOUBLE.fieldOf("questFailPenalty").orElse(d.questFailPenalty).forGetter(c -> c.questFailPenalty),
					Codec.DOUBLE.fieldOf("priceBonusPerLevel").orElse(d.priceBonusPerLevel).forGetter(c -> c.priceBonusPerLevel),
					Codec.DOUBLE.fieldOf("rewardBonusPerLevel").orElse(d.rewardBonusPerLevel).forGetter(c -> c.rewardBonusPerLevel),
					Codec.INT.fieldOf("hardQuestLevel").orElse(d.hardQuestLevel).forGetter(c -> c.hardQuestLevel),
					Codec.INT.fieldOf("specialBonusLevel").orElse(d.specialBonusLevel).forGetter(c -> c.specialBonusLevel),
					Codec.INT.fieldOf("specialBonusCount").orElse(d.specialBonusCount).forGetter(c -> c.specialBonusCount)
			).apply(i, Reputation::new);
		});

		public int[] levelThresholdArray() {
			return levelThresholds.stream().mapToInt(Integer::intValue).toArray();
		}
	}

	/** N (Q56 / Q57): village development points, facilities and their effects. */
	public static final class Development {
		/** Development on / off (off: no tab, nothing is counted; facilities already built keep their effects). */
		public boolean enabled = true;
		/** Dimensions whose markets can develop. */
		public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));
		/** Development points per copper donated. */
		public double pointsPerCoin = 1.0;
		/** Donated materials give base price B × count × this. */
		public double materialMultiplier = 1.5;
		/** Share of a request's reward added as development points (transport: to both markets). */
		public double questRewardRatio = 0.5;
		/** Development points per copper traded at the market. */
		public double tradePointsPerCoin = 0.02;
		/** Points halve over this many in-game days (0 = no decay). */
		public double halfLifeDays = 0.0;
		/** Completed facilities needed for level bonus +1, +2, ... (ascending). */
		public List<Integer> levelBonusThresholds = new ArrayList<>(List.of(2, 4));
		/** Reputation level needed to choose a facility to build. */
		public int buildReputationLevel = 2;
		/** Facilities that may be under construction at the same time per market. */
		public int maxConcurrentBuilds = 1;
		/** In-game days a facility takes to build. */
		public double buildDays = 2.0;
		/** Horizontal radius around the market center searched for a site (and for markers). */
		public int siteRadius = 32;
		/** Positions tried by one site search. */
		public int siteSearchLimit = 256;
		/** Share of the development cost paid to rebuild a destroyed facility. */
		public double rebuildCostRatio = 0.5;
		/** Completed facilities cannot be broken in survival or by explosions. */
		public boolean protectFacilities = false;
		/** Warehouse: multiplier of the stock cap Scap. */
		public double warehouseStockMultiplier = 1.5;
		/** Warehouse: multiplier of the restock speed r. */
		public double warehouseRestockMultiplier = 1.5;
		/** Request hall: extra request slots. */
		public int questHallExtraQuests = 2;
		/** Request hall: extra transport request slots. */
		public int questHallExtraTransport = 1;
		/** Inn: villagers arriving when it is completed for the first time. */
		public int innVillagers = 2;
		/** Bank: interest cap multiplier for accounts registered with the bank building. */
		public double bankInterestMaxMultiplier = 2.0;
		/** Reputation points per development point donated (M). */
		public double reputationPerPoint = 0.03;
		/** v0.42: a facility can only be built once all its wanted materials are donated. */
		public boolean requireMaterials = true;
		/** v0.42: wanted counts of the facility data are multiplied by this. */
		public double materialCountMultiplier = 4.0;
		/** v0.44: sites under construction show an outline, wood chips and a quiet hammering sound. */
		public boolean constructionEffects = true;

		public Development() {
		}

		/** Second half of the fields (RecordCodecBuilder takes 16 at most). */
		private record Facilities(int siteSearchLimit, double rebuildCostRatio, boolean protectFacilities, double warehouseStockMultiplier,
				double warehouseRestockMultiplier, int questHallExtraQuests, int questHallExtraTransport, int innVillagers,
				double bankInterestMaxMultiplier, double reputationPerPoint, boolean requireMaterials, double materialCountMultiplier,
				boolean constructionEffects) {
			static final MapCodec<Facilities> CODEC = RecordCodecBuilder.mapCodec(i -> {
				Development d = new Development();
				return i.group(
						Codec.INT.fieldOf("siteSearchLimit").orElse(d.siteSearchLimit).forGetter(Facilities::siteSearchLimit),
						Codec.DOUBLE.fieldOf("rebuildCostRatio").orElse(d.rebuildCostRatio).forGetter(Facilities::rebuildCostRatio),
						Codec.BOOL.fieldOf("protectFacilities").orElse(d.protectFacilities).forGetter(Facilities::protectFacilities),
						Codec.DOUBLE.fieldOf("warehouseStockMultiplier").orElse(d.warehouseStockMultiplier).forGetter(Facilities::warehouseStockMultiplier),
						Codec.DOUBLE.fieldOf("warehouseRestockMultiplier").orElse(d.warehouseRestockMultiplier).forGetter(Facilities::warehouseRestockMultiplier),
						Codec.INT.fieldOf("questHallExtraQuests").orElse(d.questHallExtraQuests).forGetter(Facilities::questHallExtraQuests),
						Codec.INT.fieldOf("questHallExtraTransport").orElse(d.questHallExtraTransport).forGetter(Facilities::questHallExtraTransport),
						Codec.INT.fieldOf("innVillagers").orElse(d.innVillagers).forGetter(Facilities::innVillagers),
						Codec.DOUBLE.fieldOf("bankInterestMaxMultiplier").orElse(d.bankInterestMaxMultiplier).forGetter(Facilities::bankInterestMaxMultiplier),
						Codec.DOUBLE.fieldOf("reputationPerPoint").orElse(d.reputationPerPoint).forGetter(Facilities::reputationPerPoint),
						Codec.BOOL.fieldOf("requireMaterials").orElse(d.requireMaterials).forGetter(Facilities::requireMaterials),
						Codec.DOUBLE.fieldOf("materialCountMultiplier").orElse(d.materialCountMultiplier).forGetter(Facilities::materialCountMultiplier),
						Codec.BOOL.fieldOf("constructionEffects").orElse(d.constructionEffects).forGetter(Facilities::constructionEffects)
				).apply(i, Facilities::new);
			});
		}

		private static final MapCodec<Development> POINTS_CODEC = RecordCodecBuilder.mapCodec(i -> {
			Development d = new Development();
			return i.group(
					Codec.BOOL.fieldOf("enabled").orElse(d.enabled).forGetter(c -> c.enabled),
					Codec.STRING.listOf().fieldOf("dimensions").orElse(d.dimensions).forGetter(c -> c.dimensions),
					Codec.DOUBLE.fieldOf("pointsPerCoin").orElse(d.pointsPerCoin).forGetter(c -> c.pointsPerCoin),
					Codec.DOUBLE.fieldOf("materialMultiplier").orElse(d.materialMultiplier).forGetter(c -> c.materialMultiplier),
					Codec.DOUBLE.fieldOf("questRewardRatio").orElse(d.questRewardRatio).forGetter(c -> c.questRewardRatio),
					Codec.DOUBLE.fieldOf("tradePointsPerCoin").orElse(d.tradePointsPerCoin).forGetter(c -> c.tradePointsPerCoin),
					Codec.DOUBLE.fieldOf("halfLifeDays").orElse(d.halfLifeDays).forGetter(c -> c.halfLifeDays),
					Codec.INT.listOf().fieldOf("levelBonusThresholds").orElse(d.levelBonusThresholds).forGetter(c -> c.levelBonusThresholds),
					Codec.INT.fieldOf("buildReputationLevel").orElse(d.buildReputationLevel).forGetter(c -> c.buildReputationLevel),
					Codec.INT.fieldOf("maxConcurrentBuilds").orElse(d.maxConcurrentBuilds).forGetter(c -> c.maxConcurrentBuilds),
					Codec.DOUBLE.fieldOf("buildDays").orElse(d.buildDays).forGetter(c -> c.buildDays),
					Codec.INT.fieldOf("siteRadius").orElse(d.siteRadius).forGetter(c -> c.siteRadius)
			).apply(i, (enabled, dimensions, pointsPerCoin, materialMultiplier, questRewardRatio, tradePointsPerCoin, halfLifeDays,
					levelBonusThresholds, buildReputationLevel, maxConcurrentBuilds, buildDays, siteRadius) -> {
				Development c = new Development();
				c.enabled = enabled;
				c.dimensions = new ArrayList<>(dimensions);
				c.pointsPerCoin = pointsPerCoin;
				c.materialMultiplier = materialMultiplier;
				c.questRewardRatio = questRewardRatio;
				c.tradePointsPerCoin = tradePointsPerCoin;
				c.halfLifeDays = halfLifeDays;
				c.levelBonusThresholds = new ArrayList<>(levelBonusThresholds);
				c.buildReputationLevel = buildReputationLevel;
				c.maxConcurrentBuilds = maxConcurrentBuilds;
				c.buildDays = buildDays;
				c.siteRadius = siteRadius;
				return c;
			});
		});

		static final Codec<Development> CODEC = Codec.mapPair(POINTS_CODEC, Facilities.CODEC).xmap(pair -> {
			Development c = pair.getFirst();
			Facilities f = pair.getSecond();
			c.siteSearchLimit = f.siteSearchLimit();
			c.rebuildCostRatio = f.rebuildCostRatio();
			c.protectFacilities = f.protectFacilities();
			c.warehouseStockMultiplier = f.warehouseStockMultiplier();
			c.warehouseRestockMultiplier = f.warehouseRestockMultiplier();
			c.questHallExtraQuests = f.questHallExtraQuests();
			c.questHallExtraTransport = f.questHallExtraTransport();
			c.innVillagers = f.innVillagers();
			c.bankInterestMaxMultiplier = f.bankInterestMaxMultiplier();
			c.reputationPerPoint = f.reputationPerPoint();
			c.requireMaterials = f.requireMaterials();
			c.materialCountMultiplier = f.materialCountMultiplier();
			c.constructionEffects = f.constructionEffects();
			return c;
		}, c -> Pair.of(c, new Facilities(c.siteSearchLimit, c.rebuildCostRatio, c.protectFacilities, c.warehouseStockMultiplier,
				c.warehouseRestockMultiplier, c.questHallExtraQuests, c.questHallExtraTransport, c.innVillagers, c.bankInterestMaxMultiplier,
				c.reputationPerPoint, c.requireMaterials, c.materialCountMultiplier, c.constructionEffects))).codec();

		public int[] levelBonusThresholdArray() {
			return levelBonusThresholds.stream().mapToInt(Integer::intValue).toArray();
		}
	}

	/** Logs under the mod prefix; used by {@link ConfigValidator}. */
	static void warn(String message, Object... args) {
		TradeExpansion.LOGGER.warn("config: " + message, args);
	}
}
