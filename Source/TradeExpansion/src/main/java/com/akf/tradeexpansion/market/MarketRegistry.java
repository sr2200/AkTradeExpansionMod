package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.SpatialHash;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.staff.Employee;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every market of every dimension, stored once per world (design F-1).
 */
public final class MarketRegistry extends SavedData {
	/** 2: goods keyed by goods id, markets have profile / level (stage 7). */
	public static final int SCHEMA_VERSION = 2;

	public static final Codec<MarketRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("schema_version").forGetter(r -> SCHEMA_VERSION),
			Codec.INT.listOf().fieldOf("currency_ratios").forGetter(r -> r.currencyRatios),
			Codec.LONG.fieldOf("next_id").forGetter(r -> r.nextId),
			Market.CODEC.listOf().fieldOf("markets").forGetter(r -> List.copyOf(r.markets.values())),
			// O-6 (v0.46): rival guild campaigns
			Codec.LONG.optionalFieldOf("rival_day", Long.MIN_VALUE).forGetter(r -> r.rivalDay),
			Codec.INT.optionalFieldOf("next_campaign_id", 1).forGetter(r -> r.nextCampaignId),
			Campaign.CODEC.listOf().optionalFieldOf("campaigns", List.of()).forGetter(r -> List.copyOf(r.campaigns)),
			// Q (v0.48): hired staff
			Codec.INT.optionalFieldOf("next_employee_id", 1).forGetter(r -> r.nextEmployeeId),
			Employee.CODEC.listOf().optionalFieldOf("employees", List.of()).forGetter(r -> List.copyOf(r.employees)),
			// G: market clock offset of /aktradeex clock advance (ticks)
			Codec.LONG.optionalFieldOf("clock_offset", 0L).forGetter(r -> r.clockOffset)
	).apply(i, MarketRegistry::new));

	/** No vanilla data fixer applies to mod data; Fabric API skips fixing when the type is null. */
	public static final SavedDataType<MarketRegistry> TYPE = new SavedDataType<>(
			TradeExpansion.id("markets"), MarketRegistry::new, CODEC, null);

	/** Why a stall could not get a market. */
	public enum Rejection {
		/** C-1: a crafted stall may only found a new market far enough from existing ones. */
		TOO_CLOSE_TO_MARKET,
		/** C-1: the place does not meet {@code market.newMarketRequires} (village / beds / villagers). */
		NOT_A_SETTLEMENT
	}

	/** @param created a new market was founded for the stall */
	public record Assignment(Market market, Rejection rejection, double nearestDistance, boolean created, Component reason) {
		public Assignment(Market market, Rejection rejection, double nearestDistance, boolean created) {
			this(market, rejection, nearestDistance, created, null);
		}

	}

	private final List<Integer> currencyRatios;
	private long nextId;
	private final Map<Long, Market> markets = new LinkedHashMap<>();
	/** C-2 MarketIndex: markets of each dimension by position (centers never move). Not saved; rebuilt on load. */
	private final Map<ResourceKey<Level>, SpatialHash<Market>> index = new HashMap<>();
	/** Cell size of {@link #index} (design C-2: 512 blocks). */
	static final double INDEX_CELL = 512;
	/** O-4: the last market-clock day campaigns were rolled for, and the campaigns (oldest first). */
	private long rivalDay = Long.MIN_VALUE;
	private int nextCampaignId = 1;
	private final List<Campaign> campaigns = new ArrayList<>();
	/** Q: hired staff of every player, oldest first. */
	private int nextEmployeeId = 1;
	private final List<Employee> employees = new ArrayList<>();
	/** G: ticks the market clock is ahead of the game clock ({@link MarketClock}). */
	private long clockOffset;

	private MarketRegistry() {
		this(SCHEMA_VERSION, toList(CoinType.ratios()), 1, List.of(), Long.MIN_VALUE, 1, List.of(), 1, List.of(), 0L);
	}

	private MarketRegistry(int schemaVersion, List<Integer> currencyRatios, long nextId, List<Market> markets, long rivalDay,
			int nextCampaignId, List<Campaign> campaigns, int nextEmployeeId, List<Employee> employees, long clockOffset) {
		this.clockOffset = Math.max(0, clockOffset);
		this.nextEmployeeId = nextEmployeeId;
		this.employees.addAll(employees);
		this.rivalDay = rivalDay;
		this.nextCampaignId = nextCampaignId;
		this.campaigns.addAll(campaigns);
		this.currencyRatios = new ArrayList<>(currencyRatios);
		this.nextId = nextId;
		markets.forEach(m -> this.markets.put(m.id(), m));
		this.markets.values().forEach(this::indexAdd);
		if (schemaVersion < 2) {
			// Built-in goods of schema 1 were keyed by item id (minecraft:bread → ak_trade_expansion:bread).
			this.markets.values().forEach(m -> m.remapGoods(id -> TradeExpansion.id(id.getPath())));
			setDirty();
		}
	}

	private static List<Integer> toList(int[] values) {
		List<Integer> list = new ArrayList<>(values.length);
		for (int v : values) list.add(v);
		return list;
	}

	/** O-4: last day campaigns were rolled for. */
	public long rivalDay() {
		return rivalDay;
	}

	public void setRivalDay(long day) {
		this.rivalDay = day;
	}

	/** O-3: campaigns, oldest first (mutable; callers mark the registry dirty). */
	public List<Campaign> campaigns() {
		return campaigns;
	}

	public int takeCampaignId() {
		return nextCampaignId++;
	}

	/** Q: hired staff, oldest first (mutable; callers mark the registry dirty). */
	public List<Employee> employees() {
		return employees;
	}

	public int takeEmployeeId() {
		return nextEmployeeId++;
	}

	/** G: ticks the market clock is ahead of the game clock (never negative, so time never runs backwards). */
	public long clockOffset() {
		return clockOffset;
	}

	public void addClockOffset(long ticks) {
		if (ticks <= 0) return;
		clockOffset += ticks;
		setDirty();
	}

	/** Coin ratios this world was created with (E-4). */
	public int[] currencyRatios() {
		return currencyRatios.stream().mapToInt(Integer::intValue).toArray();
	}

	public void setCurrencyRatios(int[] ratios) {
		currencyRatios.clear();
		for (int r : ratios) currencyRatios.add(r);
		setDirty();
	}

	public static MarketRegistry get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Market get(long id) {
		return markets.get(id);
	}

	public Collection<Market> all() {
		return markets.values();
	}

	private void indexAdd(Market market) {
		index.computeIfAbsent(market.dimension(), k -> new SpatialHash<>(INDEX_CELL)).add(market, market.center().getX(), market.center().getZ());
	}

	/**
	 * Markets of the dimension whose center is within {@code radius} (horizontal, inclusive) of {@code pos}, by id
	 * (C-2 MarketIndex: only nearby cells are looked at).
	 */
	public List<Market> within(ResourceKey<Level> dimension, BlockPos pos, double radius) {
		return within(dimension, pos.getX(), pos.getZ(), radius);
	}

	private List<Market> within(ResourceKey<Level> dimension, double x, double z, double radius) {
		SpatialHash<Market> hash = index.get(dimension);
		if (hash == null) return List.of();
		List<Market> list = new ArrayList<>(hash.within(x, z, radius));
		list.sort(Comparator.comparingLong(Market::id));
		return list;
	}

	/**
	 * Markets linked with {@code source} ({@link #linkedTo}) within {@code radius} by {@link #linkedDistance}, by id;
	 * includes {@code source} itself.
	 */
	public List<Market> linkedWithin(Market source, double radius) {
		List<Market> list = new ArrayList<>(within(source.dimension(), source.center(), radius));
		ResourceKey<Level> other = linkedDimension(source.dimension());
		if (other != null) {
			// Nether coordinates count ×8; look a little wider and filter with the exact distance.
			double scale = source.dimension().equals(Level.NETHER) ? 8 : 1.0 / 8;
			for (Market m : within(other, source.center().getX() * scale, source.center().getZ() * scale, radius * Math.min(scale, 1) + 8)) {
				if (linkedDistance(source, m) <= radius) list.add(m);
			}
			list.sort(Comparator.comparingLong(Market::id));
		}
		return list;
	}

	public List<Market> inDimension(ResourceKey<Level> dimension) {
		return markets.values().stream().filter(m -> m.dimension().equals(dimension)).toList();
	}

	/**
	 * Markets whose prices are linked with {@code dimension} (C-2): the same dimension, plus the Overworld for the Nether
	 * and the other way round when {@code distance.linkNetherToOverworld} is on. Distances between them: {@link #linkedDistance}.
	 */
	public List<Market> linkedTo(ResourceKey<Level> dimension) {
		ResourceKey<Level> other = linkedDimension(dimension);
		if (other == null) return inDimension(dimension);
		return markets.values().stream().filter(m -> m.dimension().equals(dimension) || m.dimension().equals(other)).toList();
	}

	/** The dimension linked with {@code dimension} (Nether ↔ Overworld), or {@code null}. */
	public static ResourceKey<Level> linkedDimension(ResourceKey<Level> dimension) {
		if (!TradeExpansionConfig.get().distance.linkNetherToOverworld) return null;
		if (dimension.equals(Level.OVERWORLD)) return Level.NETHER;
		if (dimension.equals(Level.NETHER)) return Level.OVERWORLD;
		return null;
	}

	/** Horizontal distance in Overworld blocks: Nether coordinates count ×8 (C-2, distance.linkNetherToOverworld). */
	public static double linkedDistance(Market a, Market b) {
		double sa = a.dimension().equals(Level.NETHER) ? 8 : 1;
		double sb = b.dimension().equals(Level.NETHER) ? 8 : 1;
		if (a.dimension().equals(b.dimension())) sa = sb = 1; // same dimension: plain distance (as before)
		return DistanceModel.horizontalDistance(a.center().getX() * sa, a.center().getZ() * sa, b.center().getX() * sb, b.center().getZ() * sb);
	}

	/**
	 * Finds or creates the market a stall at {@code pos} belongs to (C-1):
	 * the market with the nearest center within {@code mergeRadius} (ties: smaller id), otherwise a new one.
	 * Crafted stalls may found a new market only {@code minNewMarketDistance} away from every other market.
	 */
	public Assignment assignStall(ServerLevel level, BlockPos pos, MarketOrigin origin) {
		Preview preview = preview(level, pos, origin);
		if (preview.kind() == Preview.Kind.JOIN) {
			preview.market().stalls().add(pos.immutable());
			setDirty();
			return new Assignment(preview.market(), null, preview.nearestDistance(), false);
		}
		if (preview.kind() == Preview.Kind.REFUSED) {
			return new Assignment(null, preview.rejection(), preview.nearestDistance(), false, preview.reason());
		}
		Market market = createMarket(level, pos, origin, linkedTo(level.dimension()));
		double nearestDistance = preview.nearestDistance();
		market.stalls().add(pos.immutable());
		setDirty();
		return new Assignment(market, null, nearestDistance, true);
	}

	/**
	 * What placing a stall at {@code pos} would do, without changing anything (C-1): join the nearest market within
	 * {@code mergeRadius}, found a new one, or be refused (too close to a market, or {@code market.newMarketRequires}).
	 */
	public record Preview(Kind kind, Market market, double nearestDistance, Rejection rejection, Component reason) {
		public enum Kind { JOIN, NEW, REFUSED }
	}

	public Preview preview(ServerLevel level, BlockPos pos, MarketOrigin origin) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		Market nearest = nearestWithin(level.dimension(), pos, Double.MAX_VALUE);
		double nearestDistance = nearest == null ? Double.POSITIVE_INFINITY : nearest.horizontalDistanceTo(pos);
		if (nearest != null && nearestDistance <= config.distance.mergeRadius) return new Preview(Preview.Kind.JOIN, nearest, nearestDistance, null, null);
		if (origin == MarketOrigin.CRAFTED && nearestDistance < config.distance.minNewMarketDistance) {
			Component reason = Component.translatable("message.ak_trade_expansion.stall_too_close",
					(int) nearestDistance, (int) config.distance.mergeRadius, (int) config.distance.minNewMarketDistance);
			return new Preview(Preview.Kind.REFUSED, null, nearestDistance, Rejection.TOO_CLOSE_TO_MARKET, reason);
		}
		if (origin == MarketOrigin.CRAFTED) {
			Component reason = SettlementRule.check(level, pos);
			if (reason != null) return new Preview(Preview.Kind.REFUSED, null, nearestDistance, Rejection.NOT_A_SETTLEMENT, reason);
		}
		return new Preview(Preview.Kind.NEW, null, nearestDistance, null, null);
	}

	/** Nearest market of the dimension within {@code radius} (horizontal), or {@code null}; never creates one (A-5). */
	public Market nearestWithin(ResourceKey<Level> dimension, BlockPos pos, double radius) {
		SpatialHash<Market> hash = index.get(dimension);
		return hash == null ? null : hash.nearest(pos.getX(), pos.getZ(), radius, Comparator.comparingLong(Market::id));
	}

	public void removeStall(ResourceKey<Level> dimension, BlockPos pos, long marketId) {
		Market market = markets.get(marketId);
		// The market itself is kept even without stalls (C-1).
		if (market != null && market.dimension().equals(dimension) && market.stalls().remove(pos)) {
			setDirty();
		}
	}

	private Market createMarket(ServerLevel level, BlockPos center, MarketOrigin origin, List<Market> sameDimension) {
		Market market = new Market(nextId++, level.dimension(), center.immutable(), origin, MarketClock.now(level));
		MarketFactory.populate(level, market, sameDimension);
		markets.put(market.id(), market);
		indexAdd(market);
		TradeExpansion.LOGGER.info("Created market #{} ({}, {}, level {}, {} goods) at {} in {}", market.id(), origin.getSerializedName(),
				market.profileId(), market.level(), market.goods().size(), center.toShortString(), level.dimension().identifier());
		return market;
	}

	/** Deletes a market (admin command). Stalls pointing at it get a new market on next use. */
	public boolean remove(long id) {
		Market market = markets.remove(id);
		if (market == null) return false;
		SpatialHash<Market> hash = index.get(market.dimension());
		if (hash != null) hash.remove(market);
		setDirty();
		return true;
	}
}
