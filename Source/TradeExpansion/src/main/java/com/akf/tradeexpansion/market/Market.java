package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One market: a group of stalls sharing stock and prices (design C-1 / F-1).
 */
public final class Market {
	private static final Codec<GoodState> GOOD_STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("price_factor").forGetter(GoodState::priceFactor),
			Codec.DOUBLE.fieldOf("pressure").forGetter(GoodState::pressure),
			Codec.DOUBLE.fieldOf("stock").forGetter(GoodState::stock),
			Codec.LONG.fieldOf("last_trade").forGetter(GoodState::lastTradeGameTime)
	).apply(i, GoodState::new));

	private record GoodEntry(Identifier item, GoodState state) {
		static final Codec<GoodEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(GoodEntry::item),
				GOOD_STATE_CODEC.fieldOf("state").forGetter(GoodEntry::state)
		).apply(i, GoodEntry::new));
	}

	public static final Codec<Market> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(Market::id),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Market::dimension),
			BlockPos.CODEC.fieldOf("center").forGetter(Market::center),
			MarketOrigin.CODEC.fieldOf("origin").forGetter(Market::origin),
			BlockPos.CODEC.listOf().fieldOf("stalls").forGetter(m -> List.copyOf(m.stalls)),
			Codec.LONG.fieldOf("last_update").forGetter(Market::lastUpdateGameTime),
			GoodEntry.CODEC.listOf().fieldOf("goods").forGetter(Market::goodEntries)
	).apply(i, Market::new));

	private final long id;
	private final ResourceKey<Level> dimension;
	private final BlockPos center;
	private final MarketOrigin origin;
	private final Set<BlockPos> stalls;
	private long lastUpdateGameTime;
	/** Insertion order = display order. */
	private final Map<Identifier, GoodState> goods;

	private Market(long id, ResourceKey<Level> dimension, BlockPos center, MarketOrigin origin, List<BlockPos> stalls,
			long lastUpdateGameTime, List<GoodEntry> goods) {
		this.id = id;
		this.dimension = dimension;
		this.center = center;
		this.origin = origin;
		this.stalls = new HashSet<>(stalls);
		this.lastUpdateGameTime = lastUpdateGameTime;
		this.goods = new LinkedHashMap<>();
		goods.forEach(e -> this.goods.put(e.item(), e.state()));
	}

	public Market(long id, ResourceKey<Level> dimension, BlockPos center, MarketOrigin origin, long gameTime) {
		this(id, dimension, center, origin, List.of(), gameTime, List.of());
	}

	private List<GoodEntry> goodEntries() {
		List<GoodEntry> list = new ArrayList<>(goods.size());
		goods.forEach((item, state) -> list.add(new GoodEntry(item, state)));
		return list;
	}

	public long id() {
		return id;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public BlockPos center() {
		return center;
	}

	public MarketOrigin origin() {
		return origin;
	}

	public Set<BlockPos> stalls() {
		return stalls;
	}

	public long lastUpdateGameTime() {
		return lastUpdateGameTime;
	}

	/** Every good this market deals in, including ones whose definition was removed (F-1: callers hide those via {@link #params} == null). */
	public Map<Identifier, GoodState> goods() {
		return goods;
	}

	public GoodState good(Identifier item) {
		return goods.get(item);
	}

	public void addGood(Identifier item, GoodState state) {
		goods.put(item, state);
	}

	/** Effective params of one good, or {@code null} if it has no definition any more. */
	public GoodParams params(Identifier item) {
		GoodState state = goods.get(item);
		GoodsDefinition def = GoodsCatalog.get(item);
		if (state == null || def == null) return null;
		return def.params(state.priceFactor(), TradeExpansionConfig.get());
	}

	/** Lazy recovery of every good up to {@code gameTime} (B-3). */
	public void refresh(long gameTime) {
		double days = (gameTime - lastUpdateGameTime) / (double) PriceModel.TICKS_PER_DAY;
		TradeExpansionConfig config = TradeExpansionConfig.get();
		if (days > 0) {
			goods.forEach((item, state) -> {
				GoodParams p = params(item);
				if (p != null) PriceModel.recover(p, state, days, config.halfLifeDays(), config.restockMultiplier());
			});
		}
		lastUpdateGameTime = gameTime;
	}

	/** Applies {@code days} of recovery without waiting (test command, design 5-2). */
	public void elapse(double days) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		goods.forEach((item, state) -> {
			GoodParams p = params(item);
			if (p != null) PriceModel.recover(p, state, days, config.halfLifeDays(), config.restockMultiplier());
		});
	}

	public double horizontalDistanceTo(BlockPos pos) {
		double dx = center.getX() - pos.getX();
		double dz = center.getZ() - pos.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
