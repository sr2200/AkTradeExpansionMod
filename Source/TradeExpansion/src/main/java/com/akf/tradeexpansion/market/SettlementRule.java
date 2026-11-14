package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.SettlementMath;
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

	/** The counting rule of {@code market.newMarketRequires} (not VILLAGE); the formula is {@link SettlementMath}. */
	public static SettlementMath.Requirement requirement(TradeExpansionConfig.Market config) {
		SettlementMath.Mode mode = switch (config.newMarketRequires) {
			case BEDS -> SettlementMath.Mode.BEDS;
			case VILLAGERS -> SettlementMath.Mode.VILLAGERS;
			case BEDS_AND_VILLAGERS, VILLAGE -> SettlementMath.Mode.BEDS_AND_VILLAGERS;
		};
		return new SettlementMath.Requirement(mode, config.newMarketMinBeds, config.newMarketMinVillagers, config.newMarketEnoughBeds);
	}

	/** {@code null} if a new market may be founded at {@code pos}, otherwise the reason shown to the player. */
	public static @Nullable Component check(ServerLevel level, BlockPos pos) {
		TradeExpansionConfig.Market config = TradeExpansionConfig.get().market;
		TradeExpansionConfig.NewMarketRequires mode = config.newMarketRequires;
		if (mode == TradeExpansionConfig.NewMarketRequires.VILLAGE) {
			return villageAt(level, pos) != null ? null : Component.translatable("message.ak_trade_expansion.new_market.village");
		}
		SettlementMath.Requirement requirement = requirement(config);
		boolean noBeds = MarketFactory.usesVillagers(level);
		long beds = noBeds ? 0 : MarketFactory.countBeds(level, pos);
		long villagers = SettlementMath.needsVillagers(requirement.mode(), noBeds) ? MarketFactory.countVillagers(level, pos) : 0;
		if (SettlementMath.allows(requirement, noBeds, beds, villagers)) return null;
		int radius = config.levelRadius;
		return switch (mode) {
			case BEDS -> noBeds
					? Component.translatable("message.ak_trade_expansion.new_market.villagers", radius, config.newMarketMinVillagers, villagers)
					: Component.translatable("message.ak_trade_expansion.new_market.beds", radius, config.newMarketMinBeds, beds);
			case VILLAGERS -> Component.translatable("message.ak_trade_expansion.new_market.villagers", radius, config.newMarketMinVillagers, villagers);
			default -> noBeds
					? Component.translatable("message.ak_trade_expansion.new_market.villagers", radius, config.newMarketMinVillagers, villagers)
					: config.newMarketEnoughBeds > 0
					? Component.translatable("message.ak_trade_expansion.new_market.beds_and_villagers.enough", radius, config.newMarketMinBeds,
					config.newMarketMinVillagers, Math.max(config.newMarketMinBeds, config.newMarketEnoughBeds), beds, villagers)
					: Component.translatable("message.ak_trade_expansion.new_market.beds_and_villagers", radius, config.newMarketMinBeds,
					config.newMarketMinVillagers, beds, villagers);
		};
	}
}
