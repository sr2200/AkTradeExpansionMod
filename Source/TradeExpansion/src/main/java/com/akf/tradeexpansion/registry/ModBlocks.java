package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.block.MarketStallBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final MarketStallBlock MARKET_STALL = register("market_stall",
			new MarketStallBlock(BlockBehaviour.Properties.of()
					.setId(blockKey("market_stall"))
					.mapColor(MapColor.WOOD)
					.strength(2.5f)
					.sound(SoundType.WOOD)));

	private ModBlocks() {
	}

	private static ResourceKey<Block> blockKey(String name) {
		return ResourceKey.create(Registries.BLOCK, TradeExpansion.id(name));
	}

	private static <T extends Block> T register(String name, T block) {
		Registry.register(BuiltInRegistries.BLOCK, blockKey(name), block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TradeExpansion.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
	}
}
