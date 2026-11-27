package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.core.PriceEstimate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Suggested base price for an item to add as a good (the config screen's goods manager, design G v0.92): from the
 * world's recipes and the prices of the goods it is made of ({@link PriceEstimate#fromRecipe}), else from the item's
 * traits ({@link PriceEstimate#fromTraits}). Materials that are not goods are priced from their own recipes, a few
 * steps deep.
 */
public final class PriceSuggester {
	/** How many steps of recipes are followed for materials that are not goods. */
	private static final int MAX_DEPTH = 4;

	/** The suggested price (per item), and whether it came from a recipe. */
	public record Suggestion(double price, boolean fromRecipe) {
	}

	private record Made(Recipe<?> recipe, int count) {
	}

	private final Map<Item, List<Made>> recipes = new HashMap<>();
	private final Map<Item, Double> known = new HashMap<>();
	private final Map<Item, Double> memo = new HashMap<>();

	private PriceSuggester(MinecraftServer server, Item target) {
		for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
			GoodsDefinition def = e.getValue();
			if (def.components().isPresent()) continue; // a potion, a book…: not the plain item
			Item item = BuiltInRegistries.ITEM.getOptional(def.item()).orElse(null);
			if (item == null || item == target) continue;
			known.merge(item, def.basePrice() / Math.max(1, def.unit()), Math::min);
		}
		ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			Recipe<?> recipe = holder.value();
			if (recipe.isSpecial()) continue;
			for (RecipeDisplay display : recipe.display()) {
				ItemStack result = display.result().resolveForFirstStack(context);
				if (result.isEmpty()) continue;
				recipes.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(new Made(recipe, result.getCount()));
			}
		}
	}

	/** Suggests a base price for {@code item} (per item; the screen adds goods with unit 1). */
	public static Suggestion suggest(MinecraftServer server, Item item) {
		PriceSuggester s = new PriceSuggester(server, item);
		OptionalDouble fromRecipe = s.recipeCost(item, 0, new HashSet<>());
		if (fromRecipe.isPresent()) return new Suggestion(PriceEstimate.round(fromRecipe.getAsDouble()), true);
		ItemStack stack = new ItemStack(item);
		FoodProperties food = stack.get(DataComponents.FOOD);
		double guess = PriceEstimate.fromTraits(stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 64), stack.getRarity().ordinal(),
				food == null ? 0 : food.nutrition(), food == null ? 0 : food.saturation(), stack.getMaxDamage());
		return new Suggestion(PriceEstimate.round(guess), false);
	}

	/** Price of a material: a good's price, else from its recipes. */
	private OptionalDouble cost(Item item, int depth, Set<Item> visiting) {
		Double price = known.get(item);
		if (price != null) return OptionalDouble.of(price);
		return recipeCost(item, depth, visiting);
	}

	/** The cheapest of the item's recipes whose materials all have a price. */
	@SuppressWarnings("deprecation") // Ingredient.items(): the only way to list what an ingredient takes
	private OptionalDouble recipeCost(Item item, int depth, Set<Item> visiting) {
		Double memoized = memo.get(item);
		if (memoized != null) return OptionalDouble.of(memoized);
		List<Made> made = recipes.get(item);
		if (made == null || depth > MAX_DEPTH || !visiting.add(item)) return OptionalDouble.empty();
		double best = -1;
		try {
			for (Made m : made) {
				PlacementInfo placement = m.recipe().placementInfo();
				if (placement.isImpossibleToPlace()) continue;
				// One cost per kind of material (the same ingredient in several slots adds up).
				Map<Ingredient, Double> kinds = new LinkedHashMap<>();
				boolean priced = true;
				for (Ingredient ingredient : placement.ingredients()) {
					double cheapest = -1;
					for (Holder<Item> option : (Iterable<Holder<Item>>) ingredient.items()::iterator) {
						OptionalDouble c = cost(option.value(), depth + 1, visiting);
						if (c.isPresent() && (cheapest < 0 || c.getAsDouble() < cheapest)) cheapest = c.getAsDouble();
					}
					if (cheapest < 0) {
						priced = false;
						break;
					}
					kinds.merge(ingredient, cheapest, Double::sum);
				}
				if (!priced) continue;
				double price = PriceEstimate.fromRecipe(new ArrayList<>(kinds.values()), m.count());
				if (price > 0 && (best < 0 || price < best)) best = price;
			}
		} finally {
			visiting.remove(item);
		}
		if (best < 0) return OptionalDouble.empty();
		memo.put(item, best);
		return OptionalDouble.of(best);
	}
}
