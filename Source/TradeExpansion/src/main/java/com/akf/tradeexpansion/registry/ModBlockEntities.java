package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<MarketStallBlockEntity> MARKET_STALL = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, TradeExpansion.id("market_stall"),
			FabricBlockEntityTypeBuilder.create(MarketStallBlockEntity::new, ModBlocks.MARKET_STALL).build());

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
