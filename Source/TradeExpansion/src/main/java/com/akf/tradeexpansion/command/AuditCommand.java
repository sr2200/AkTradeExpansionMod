package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.crafting.MintingRecipe;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /aktradeex audit} (design B-6, 5-2): recipes that turn goods bought in a market into goods that sell there for
 * more (buy every ingredient at the current price, craft, sell the result), and minting recipes whose coins are worth
 * more than the bought materials (E-2). Shortages (K) are reported too, since they are an accepted exception.
 */
final class AuditCommand {
	private AuditCommand() {
	}

	record Finding(Identifier recipe, String result, long cost, long sell) {
		long profit() {
			return sell - cost;
		}
	}

	/** A recipe with its first displayed result. */
	private record Crafted(RecipeHolder<?> holder, List<Ingredient> ingredients, ItemStack result) {
	}

	static List<Finding> audit(ServerLevel level, Market market) {
		// Plain single-unit goods of this market, by item.
		Map<Item, Identifier> goodsByItem = new HashMap<>();
		market.goods().keySet().forEach(goodsId -> {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (def != null && def.unit() == 1 && def.components().isEmpty()) {
				goodsByItem.put(BuiltInRegistries.ITEM.getValue(def.item()), goodsId);
			}
		});
		ContextMap context = SlotDisplayContext.fromLevel(level);
		List<Crafted> recipes = new ArrayList<>();
		for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
			Recipe<?> recipe = holder.value();
			List<Ingredient> ingredients = recipe.placementInfo().ingredients();
			if (ingredients.isEmpty() || recipe.display().isEmpty()) continue;
			ItemStack result = recipe.display().getFirst().result().resolveForFirstStack(context);
			if (!result.isEmpty()) recipes.add(new Crafted(holder, ingredients, result));
		}

		List<Finding> findings = new ArrayList<>();
		for (Crafted c : recipes) {
			if (c.holder().value() instanceof MintingRecipe) {
				// E-2: materials may come from a recipe one step away (9 ingots -> block).
				long cost = cost(c.ingredients(), item -> unitCost(market, goodsByItem, recipes, item));
				long coins = c.result().getItem() instanceof CoinItem coin ? coin.type().value() * c.result().getCount() : 0;
				if (cost >= 0 && coins > cost) {
					findings.add(new Finding(c.holder().id().identifier(), c.result().getCount() + "x " + BuiltInRegistries.ITEM.getKey(c.result().getItem()), cost, coins));
				}
				continue;
			}
			Identifier resultGoods = goodsByItem.get(c.result().getItem());
			if (resultGoods == null || !GoodsCatalog.get(resultGoods).sell()) continue;
			long cost = cost(c.ingredients(), item -> buyPrice(market, goodsByItem.get(item)));
			if (cost < 0) continue;
			GoodParams p = market.params(resultGoods);
			GoodState state = market.good(resultGoods);
			long sell = PriceModel.sellPrice(p, state.pressure()) * c.result().getCount();
			if (sell > cost) findings.add(new Finding(c.holder().id().identifier(), c.result().getCount() + "x " + resultGoods, cost, sell));
		}
		findings.sort(Comparator.comparingLong(Finding::profit).reversed());
		return findings;
	}

	/** Sum of the cheapest choice of every ingredient; {@code -1} if one cannot be bought. */
	@SuppressWarnings("deprecation") // Ingredient.items() is the only way to list the choices on the server
	private static long cost(List<Ingredient> ingredients, java.util.function.ToLongFunction<Item> price) {
		long cost = 0;
		for (Ingredient ingredient : ingredients) {
			long cheapest = ingredient.items().map(Holder::value)
					.mapToLong(price)
					.filter(p -> p > 0)
					.min().orElse(-1);
			if (cheapest < 0) return -1;
			cost += cheapest;
		}
		return cost;
	}

	/** Cheapest way to get one {@code item}: buy it, or craft it from goods bought here (one recipe step). */
	private static long unitCost(Market market, Map<Item, Identifier> goodsByItem, List<Crafted> recipes, Item item) {
		long best = buyPrice(market, goodsByItem.get(item));
		for (Crafted c : recipes) {
			if (c.result().getItem() != item || c.holder().value() instanceof MintingRecipe) continue;
			long cost = cost(c.ingredients(), i -> buyPrice(market, goodsByItem.get(i)));
			if (cost < 0) continue;
			long perItem = Math.ceilDiv(cost, c.result().getCount());
			if (best < 0 || perItem < best) best = perItem;
		}
		return best;
	}

	private static long buyPrice(Market market, Identifier goodsId) {
		if (goodsId == null || !GoodsCatalog.get(goodsId).buy()) return -1;
		GoodParams p = market.params(goodsId);
		GoodState state = market.good(goodsId);
		return p == null || !PriceModel.canBuy(state) ? -1 : PriceModel.buyPrice(p, state.pressure());
	}
}
