package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.CoinType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
			Market.CODEC.listOf().fieldOf("markets").forGetter(r -> List.copyOf(r.markets.values()))
	).apply(i, MarketRegistry::new));

	/** No vanilla data fixer applies to mod data; Fabric API skips fixing when the type is null. */
	public static final SavedDataType<MarketRegistry> TYPE = new SavedDataType<>(
			TradeExpansion.id("markets"), MarketRegistry::new, CODEC, null);

	/** Why a stall could not get a market. */
	public enum Rejection {
		/** C-1: a crafted stall may only found a new market far enough from existing ones. */
		TOO_CLOSE_TO_MARKET
	}

	/** @param created a new market was founded for the stall */
	public record Assignment(Market market, Rejection rejection, double nearestDistance, boolean created) {
	}

	private final List<Integer> currencyRatios;
	private long nextId;
	private final Map<Long, Market> markets = new LinkedHashMap<>();

	private MarketRegistry() {
		this(SCHEMA_VERSION, toList(CoinType.ratios()), 1, List.of());
	}

	private MarketRegistry(int schemaVersion, List<Integer> currencyRatios, long nextId, List<Market> markets) {
		this.currencyRatios = new ArrayList<>(currencyRatios);
		this.nextId = nextId;
		markets.forEach(m -> this.markets.put(m.id(), m));
		if (schemaVersion < 2) {
			// Built-in goods of schema 1 were keyed by item id (minecraft:bread → trade_expansion:bread).
			this.markets.values().forEach(m -> m.remapGoods(id -> TradeExpansion.id(id.getPath())));
			setDirty();
		}
	}

	private static List<Integer> toList(int[] values) {
		List<Integer> list = new ArrayList<>(values.length);
		for (int v : values) list.add(v);
		return list;
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

	public List<Market> inDimension(ResourceKey<Level> dimension) {
		return markets.values().stream().filter(m -> m.dimension().equals(dimension)).toList();
	}

	/**
	 * Finds or creates the market a stall at {@code pos} belongs to (C-1):
	 * the market with the nearest center within {@code mergeRadius} (ties: smaller id), otherwise a new one.
	 * Crafted stalls may found a new market only {@code minNewMarketDistance} away from every other market.
	 */
	public Assignment assignStall(ServerLevel level, BlockPos pos, MarketOrigin origin) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		List<Market> sameDimension = inDimension(level.dimension());

		Market nearest = sameDimension.stream()
				.min(Comparator.comparingDouble((Market m) -> m.horizontalDistanceTo(pos)).thenComparingLong(Market::id))
				.orElse(null);
		double nearestDistance = nearest == null ? Double.POSITIVE_INFINITY : nearest.horizontalDistanceTo(pos);

		if (nearest != null && nearestDistance <= config.distance.mergeRadius) {
			nearest.stalls().add(pos.immutable());
			setDirty();
			return new Assignment(nearest, null, nearestDistance, false);
		}
		if (origin == MarketOrigin.CRAFTED && nearestDistance < config.distance.minNewMarketDistance) {
			return new Assignment(null, Rejection.TOO_CLOSE_TO_MARKET, nearestDistance, false);
		}

		Market market = createMarket(level, pos, origin, sameDimension);
		market.stalls().add(pos.immutable());
		setDirty();
		return new Assignment(market, null, nearestDistance, true);
	}

	/** Nearest market of the dimension within {@code radius} (horizontal), or {@code null}; never creates one (A-5). */
	public Market nearestWithin(ResourceKey<Level> dimension, BlockPos pos, double radius) {
		return inDimension(dimension).stream()
				.filter(m -> m.horizontalDistanceTo(pos) <= radius)
				.min(Comparator.comparingDouble((Market m) -> m.horizontalDistanceTo(pos)).thenComparingLong(Market::id))
				.orElse(null);
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
		TradeExpansion.LOGGER.info("Created market #{} ({}, {}, level {}, {} goods) at {} in {}", market.id(), origin.getSerializedName(),
				market.profileId(), market.level(), market.goods().size(), center.toShortString(), level.dimension().identifier());
		return market;
	}

	/** Deletes a market (admin command). Stalls pointing at it get a new market on next use. */
	public boolean remove(long id) {
		boolean removed = markets.remove(id) != null;
		if (removed) setDirty();
		return removed;
	}
}
