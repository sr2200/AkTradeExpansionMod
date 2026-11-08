package com.akf.tradeexpansion.client.compat.rei;

import com.akf.tradeexpansion.client.PriceLookupCache;
import com.akf.tradeexpansion.client.compat.recipeviewer.MarketPriceRecipe;
import com.akf.tradeexpansion.registry.ModBlocks;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * I (v0.47): REI client plugin. Listed under the {@code "rei_client"} entrypoint of {@code fabric.mod.json}, so REI finds
 * and creates it itself; without REI nothing ever loads this package. The pages are made when an item is looked up
 * ("recipes" or "uses"), for the items the server trades ({@link PriceLookupCache}).
 */
public final class TradeExpansionReiPlugin implements REIClientPlugin {
	@Override
	public void registerCategories(CategoryRegistry registry) {
		registry.add(new MarketPriceDisplayCategory());
		registry.addWorkstations(MarketPriceDisplay.CATEGORY, EntryStacks.of(ModBlocks.MARKET_STALL));
	}

	@Override
	public void registerDisplays(DisplayRegistry registry) {
		registry.registerDisplayGenerator(MarketPriceDisplay.CATEGORY, new DynamicDisplayGenerator<MarketPriceDisplay>() {
			@Override
			public Optional<List<MarketPriceDisplay>> getRecipeFor(EntryStack<?> entry) {
				return page(entry);
			}

			@Override
			public Optional<List<MarketPriceDisplay>> getUsageFor(EntryStack<?> entry) {
				return page(entry);
			}
		});
	}

	private static Optional<List<MarketPriceDisplay>> page(EntryStack<?> entry) {
		if (entry.getType() != VanillaEntryTypes.ITEM) return Optional.empty();
		ItemStack stack = entry.castValue();
		if (stack.isEmpty()) return Optional.empty();
		Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!PriceLookupCache.tradable().contains(item)) return Optional.empty();
		MarketPriceRecipe recipe = MarketPriceRecipe.of(item);
		return recipe == null ? Optional.empty() : Optional.of(List.of(new MarketPriceDisplay(recipe)));
	}
}
