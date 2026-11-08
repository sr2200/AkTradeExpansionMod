package com.akf.tradeexpansion.client.compat.jei;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.client.PriceLookupCache;
import com.akf.tradeexpansion.client.compat.recipeviewer.MarketPriceRecipe;
import com.akf.tradeexpansion.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * I (v0.47): JEI plugin. Listed under the {@code "jei_mod_plugin"} entrypoint of {@code fabric.mod.json}, so JEI finds and
 * creates it itself; without JEI nothing ever loads this package. The pages are added at runtime: the server sends the
 * tradable items after joining ({@link PriceLookupCache}), items it no longer trades are hidden.
 */
@JeiPlugin
public final class TradeExpansionJeiPlugin implements IModPlugin {
	private static final Identifier UID = TradeExpansion.id("jei_plugin");

	private static @Nullable IJeiRuntime runtime;
	/** Pages added to the current runtime, and whether each is shown. */
	private static final Map<Identifier, MarketPriceRecipe> ADDED = new HashMap<>();
	private static final Map<Identifier, Boolean> SHOWN = new HashMap<>();
	private static boolean listening;

	@Override
	public Identifier getPluginUid() {
		return UID;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new MarketPriceCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(MarketPriceCategory.TYPE, ModBlocks.MARKET_STALL);
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		ADDED.clear();
		SHOWN.clear();
		if (!listening) {
			listening = true;
			PriceLookupCache.onTradableChanged(TradeExpansionJeiPlugin::sync);
		}
		sync(PriceLookupCache.tradable());
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
		ADDED.clear();
		SHOWN.clear();
	}

	/** Adds pages for new tradable items, shows them again or hides the ones no longer traded. */
	private static void sync(Set<Identifier> tradable) {
		IJeiRuntime current = runtime;
		if (current == null) return;
		try {
			IRecipeManager manager = current.getRecipeManager();
			List<MarketPriceRecipe> add = new ArrayList<>();
			List<MarketPriceRecipe> show = new ArrayList<>();
			List<MarketPriceRecipe> hide = new ArrayList<>();
			for (Identifier item : tradable) {
				MarketPriceRecipe recipe = ADDED.get(item);
				if (recipe == null) {
					recipe = MarketPriceRecipe.of(item);
					if (recipe == null) continue; // an item this client does not know
					ADDED.put(item, recipe);
					add.add(recipe);
				} else if (!SHOWN.get(item)) {
					show.add(recipe);
				}
				SHOWN.put(item, true);
			}
			ADDED.forEach((item, recipe) -> {
				if (!tradable.contains(item) && SHOWN.get(item)) {
					hide.add(recipe);
					SHOWN.put(item, false);
				}
			});
			if (!add.isEmpty()) manager.addRecipes(MarketPriceCategory.TYPE, add);
			if (!show.isEmpty()) manager.unhideRecipes(MarketPriceCategory.TYPE, show);
			if (!hide.isEmpty()) manager.hideRecipes(MarketPriceCategory.TYPE, hide);
		} catch (RuntimeException | LinkageError e) {
			// A JEI version with a different API: no market pages, the game goes on.
			TradeExpansion.LOGGER.warn("Could not add the market pages to JEI", e);
		}
	}
}
