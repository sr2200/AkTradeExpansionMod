package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.block.ConstructionMarkerBlock;
import com.akf.tradeexpansion.block.FacilitySignBlock;
import com.akf.tradeexpansion.block.MarketStallBlock;
import net.minecraft.world.level.material.PushReaction;
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
					.sound(SoundType.WOOD)
					.noOcclusion()));

	/** N-4: core of a facility building / construction sign. Not obtainable as an item; cannot be pushed; drops nothing. */
	public static final FacilitySignBlock FACILITY_SIGN = registerBlock("facility_sign",
			new FacilitySignBlock(BlockBehaviour.Properties.of()
					.setId(blockKey("facility_sign"))
					.mapColor(MapColor.WOOD)
					.strength(2.0f, 6.0f)
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.IMMOVEABLE)
					.noOcclusion()
					.noLootTable()));

	/** N-5: building site stake, given on the development tab. */
	public static final ConstructionMarkerBlock CONSTRUCTION_MARKER = register("construction_marker",
			new ConstructionMarkerBlock(BlockBehaviour.Properties.of()
					.setId(blockKey("construction_marker"))
					.mapColor(MapColor.WOOD)
					.instabreak()
					.sound(SoundType.WOOD)
					.noOcclusion()
					.pushReaction(PushReaction.POPPED)));

	private ModBlocks() {
	}

	private static <T extends Block> T registerBlock(String name, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, blockKey(name), block);
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
