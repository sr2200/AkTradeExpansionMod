package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * C-1: where a crafted stall may found a new market ({@code market.newMarketRequires}). Joining an existing market and
 * naturally generated stalls are not affected. Beds and villagers are counted like the market level (D-4): within
 * {@code market.levelRadius}. In the Nether and the End, where beds cannot be used, the bed conditions are left out and
 * only villagers count.
 */
public final class SettlementRule {
	private static final TagKey<Structure> VILLAGE = TagKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("village"));

	private SettlementRule() {
	}

	/** Bounding box of the village structure containing {@code pos}, or {@code null} (what {@code VILLAGE} checks). */
	public static @Nullable BoundingBox villageAt(ServerLevel level, BlockPos pos) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(VILLAGE)
				.map(set -> level.structureManager().getStructureAt(pos, set))
				.filter(start -> start.isValid())
				.map(start -> start.getBoundingBox())
				.orElse(null);
	}

	/** v0.27.1: villages often have more villagers than beds; this many beds are enough whatever the villager count. */
	private static boolean enoughBeds(long beds, TradeExpansionConfig.Market config) {
		return config.newMarketEnoughBeds > 0 && beds >= config.newMarketEnoughBeds;
	}

	/** {@code null} if a new market may be founded at {@code pos}, otherwise the reason shown to the player. */
	public static @Nullable Component check(ServerLevel level, BlockPos pos) {
		TradeExpansionConfig.Market config = TradeExpansionConfig.get().market;
		TradeExpansionConfig.NewMarketRequires mode = config.newMarketRequires;
		if (mode == TradeExpansionConfig.NewMarketRequires.VILLAGE) {
			return villageAt(level, pos) != null ? null : Component.translatable("message.trade_expansion.new_market.village");
		}
		boolean noBeds = MarketFactory.usesVillagers(level);
		long beds = noBeds ? 0 : MarketFactory.countBeds(level, pos);
		long villagers = mode == TradeExpansionConfig.NewMarketRequires.BEDS ? 0 : MarketFactory.countVillagers(level, pos);
		boolean ok = switch (mode) {
			case BEDS -> noBeds ? MarketFactory.countVillagers(level, pos) >= config.newMarketMinVillagers : beds >= config.newMarketMinBeds;
			case VILLAGERS -> villagers >= config.newMarketMinVillagers;
			case BEDS_AND_VILLAGERS -> villagers >= config.newMarketMinVillagers
					&& (noBeds || (beds >= config.newMarketMinBeds && (beds >= villagers || enoughBeds(beds, config))));
			case VILLAGE -> true;
		};
		if (ok) return null;
		int radius = config.levelRadius;
		return switch (mode) {
			case BEDS -> noBeds
					? Component.translatable("message.trade_expansion.new_market.villagers", radius, config.newMarketMinVillagers, MarketFactory.countVillagers(level, pos))
					: Component.translatable("message.trade_expansion.new_market.beds", radius, config.newMarketMinBeds, beds);
			case VILLAGERS -> Component.translatable("message.trade_expansion.new_market.villagers", radius, config.newMarketMinVillagers, villagers);
			default -> noBeds
					? Component.translatable("message.trade_expansion.new_market.villagers", radius, config.newMarketMinVillagers, villagers)
					: config.newMarketEnoughBeds > 0
					? Component.translatable("message.trade_expansion.new_market.beds_and_villagers.enough", radius, config.newMarketMinBeds,
					config.newMarketMinVillagers, Math.max(config.newMarketMinBeds, config.newMarketEnoughBeds), beds, villagers)
					: Component.translatable("message.trade_expansion.new_market.beds_and_villagers", radius, config.newMarketMinBeds,
					config.newMarketMinVillagers, beds, villagers);
		};
	}
}
