package com.akf.tradeexpansion.client.compat.rei;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.client.compat.recipeviewer.MarketPriceRecipe;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * I (v0.47): REI's "nearby markets" page of an item. Made on the client only when it is looked up (no serializer: it is
 * never saved or sent). The item is both an input (it can be sold) and an output (it can be bought).
 */
final class MarketPriceDisplay implements Display {
	static final CategoryIdentifier<MarketPriceDisplay> CATEGORY = CategoryIdentifier.of(TradeExpansion.id("market_prices"));

	private final MarketPriceRecipe recipe;
	private final List<EntryIngredient> entries;

	MarketPriceDisplay(MarketPriceRecipe recipe) {
		this.recipe = recipe;
		this.entries = List.of(EntryIngredients.of(recipe.stack()));
	}

	MarketPriceRecipe recipe() {
		return recipe;
	}

	@Override
	public List<EntryIngredient> getInputEntries() {
		return entries;
	}

	@Override
	public List<EntryIngredient> getOutputEntries() {
		return entries;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CATEGORY;
	}

	@Override
	public Optional<Identifier> getDisplayLocation() {
		return Optional.empty();
	}

	@Override
	public @Nullable DisplaySerializer<? extends Display> getSerializer() {
		return null;
	}
}
